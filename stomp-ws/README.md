# RabbitMQ Stomp over WebSocket client

The purpose of this project is to demonstrate how a Spring Boot application
can send and receive **Stomp** messages to/from RabbitMQ over WebSocket protocol.

Moreover, we also want to demonstrate how to make the application resilient to
connection failures. For instance, start the application with RabbitMQ down or
shutdown RabbitMQ while the application is connected or drop individual connections.

The expectations are the following:
- It is up to the application to retry should it failed to send a message
- However, it is expected that any subscription is restored after the connection is recovered.

## client-stomp-ws

The demonstration code is under [client-stomp-ws](client-stomp-ws) project. It
consists of 2 packages:
- `com.pivotal.rabbitmq.example` It has our Spring Boot demo application. It shows 2 type of use cases
that will see shortly.
- `com.pivotal.rabbitmq.stompws` It provides to the demo application a fully configured and resilient
 **Stomp** client with the aforementioned expectations with regards resiliency.


## Spring WebSocket Stomp Client

Although we are using [Spring WebSocket Stomp Client](https://docs.spring.io/spring/docs/current/spring-framework-reference/web.html#websocket-stomp-client) which gives us all the functionality to send and receive **stomp**
messages to RabbitMQ, we still need to write some logic to make it resilient.

For our demo application to get a stomp client, we first need to build `WebSocketConnectionProvider`. The snippet
of code below is under RabbitStompWebSocketConfiguration.java.
```
@Bean
 public WebSocketConnectionProvider webSocketConnectionProvider() {
     return WebSocketConnectionProvider.builder()
             .connectTo(hostname, port)
             .withStompHeader(headerWithCredentials())
             .withTaskScheduler(taskScheduler)
             .build();
 }
```

It allows us to fully configure the connection details such as address, credentials, etc.

Once we have a `WebSocketConnectionProvider` we can get a stomp client.
```
@Bean(destroyMethod = "stop")
 public RabbitStompWsClient stompForScheduledTask(WebSocketConnectionProvider provider) {
     return provider.newClient("stompForScheduledTask");
 }
```

We can build as many clients as needed. For instance, in our demo application we have 2 use cases.
One use case is a scheduler that sends messages on a regular basis. We want to dedicate a stomp
client for this use case called `stompForScheduledTask`.
```
  @Autowired
	@Qualifier("stompForScheduledTask")
	RabbitStompWsClient rabbit;

	@Bean
	public CommandLineRunner sendMessagesToTest() {
		return (args) -> {
			taskScheduler.scheduleAtFixedRate(()-> {
				rabbit.send("/queue/test", String.valueOf(System.currentTimeMillis()))
						.thenRun(()->{log.info("Sent to test");})
						.exceptionally(throwable -> {log.error("Failed to send", throwable); return null;});
			}, 2000);
		};
	}
```

And another use case is a RestController that also sends messages and we decided
to allocate its own stomp client to it.
```
@Autowired
@Qualifier("stompForRest")
RabbitStompWsClient rabbit;

@PostMapping("/send")
public String sendMessage(@RequestParam String destination, @RequestBody String body) {
    CompletionStage<String> result = rabbit.send(destination, body).thenApply(o ->  "ok")
            .exceptionally(throwable -> "failed");
    try {
        return result.toCompletableFuture().get();
    } catch (Exception e) {
        return "failed";
    }
}
```

### Why separate stomp clients?

It turns out that if we send a message and RabbitMQ rejects it, say because the
exchange does not exist; the connection is dropped. If two services are using the same stomp client then one
service may affect the other. We can think of `RabbitStompWsClient` as a persistent connection to RabbitMQ.
But in fact, under the covers it uses two connections. One for sending and a separate one for subscriptions.



## What do we need to do in order to write our own application

First, create `@Configuration` class that builds the stomp clients you need. e.g.
```
  @Bean(destroyMethod = "stop")
  public RabbitStompWsClient stompForRest(WebSocketConnectionProvider provider) {
      return provider.newClient("stompForRest");
  }

  @Bean(destroyMethod = "stop")
  public RabbitStompWsClient stompForScheduledTask(WebSocketConnectionProvider provider) {
      return provider.newClient("stompForScheduledTask");
  }
```

Second, inject your client to the services that need it to send:

```
@Bean
public CommandLineRunner sendMessagesToTest(RabbitStompWsClient rabbit) {
  return (args) -> {
    taskScheduler.scheduleAtFixedRate(()-> {
      rabbit.send("/queue/test", String.valueOf(System.currentTimeMillis()))
          .thenRun(()->{log.info("Sent to test");})
          .exceptionally(throwable -> {log.error("Failed to send", throwable); return null;});
    }, 2000);
  };
}
```

or that need to subscribe:

```
@Bean
	public CompletionStage<Subscription> queueTestListener() {
		return rabbit.subscribe("/queue/test", String.class,
				(stompHeaders, s) -> {log.info("/queue/test : Received message {}", s);});
	}
```

## How to run this demo

If you have docker installed, you can simply run the following script to launch
RabbitMQ configured with stomp/ws:
```
bin/deploy-rabbitmq
```

Then, build the application and run it
```
cd client-stomp-ws
mvn
./run
```

The application will automatically connect and sends messages to 2 queues and consumes messages from those queues.

Additionally, we can send messages by invoking the rest endpoint.
`curl -X POST localhost:8080/send?destination="/queue/test" -d "hello"`

## Testing resiliency

### Application starts without RabbitMQ

1. Stop RabbitMQ
  `../bin/docker kill rabbitmq-5672`
2. Run application
  `.run`
3. Check that it fails to connect  
4. Start RabbitMQ
  `../bin/deploy-rabbit`


### RabbitMQ is rebooted while application is running

1. Run application
  `.run`
2. Check that it connects
3. Restart RabbitMQ
  `../bin/deploy-rabbit`
4. Check that it looses the connection and retries
5. Eventually, it connects and continues sending and receiving

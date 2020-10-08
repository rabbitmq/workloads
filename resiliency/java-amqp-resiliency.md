# Java RabbitMQ Resiliency workshop

The goal of this workload is to provide guidance to developers on how to write resilient Java applications that uses [RabbitMQ Java Client](https://www.rabbitmq.com/java-client.html).

The type of failures we are going to handle are:
- RabbitMQ node goes down expectedly (e.g. during a rolling upgrade) or unexpectedly
- Entire RabbitMQ cluster goes down
- Connection fails or abruptly closed by an intermediary such as a load balancer
- Channel-level exceptions  
- Fatal consumer errors (e.g. poisonous message)


**Table of Content**


## Patterns for applications that uses RabbitMQ Java client

This time we start with an application which is already resilient. We are going to challenge it with a number of failures and point to the code how it handles those failures.

If you want to run the code follow the steps on the next sections otherwise if you are only interested in the patterns you can go straight to the [Reference Java AMQP application](#Reference-Java-AMQP-application) section.

### Getting the code and building the application

We will need Java 1.8 and at least Maven 3.3.x to build the application.

  ```
  git clone https://github.com/rabbitmq/workloads
  cd workloads/resiliency/resilient-java-rabbitmq
  mvn install
  ```

### To run the application locally

To run the application locally we need to provide the following environment variables:
```
export SPRING_PROFILES_ACTIVE=Cloud
export VCAP_APPLICATION='{"application_name":"demo"}'
export VCAP_SERVICES="$(cat src/main/resources/cluster.json)"
```

To run the application within IntelliJ, copy the file `ResilientApplication.xml` to folder `.idea/runConfigurations`.  

Either `src/main/resources/cluster.json` or `ResilientApplication.xml` have the credentials to a 3 node RabbitMQ cluster running in localhost on ports 5672, 5673 and 5674 respectively.  


### Reference Java AMQP application

This application also uses [Spring Cloud Connectors](https://cloud.spring.io/spring-cloud-connectors/). But this time it only gets the RabbitMQ cluster credentials to build an instance of  `com.rabbitmq.client.ConnectionFactory`.

**TL;DR** This reference application proposes a design to make our applications resilient however by all means it is not the only or the best design.

### Types of failures/situations

#### Application starts up with the entire cluster down

[RabbitMQ Java AMQP](https://www.rabbitmq.com/java-client.html) has a built-in mechanism to recover from network connection problem. But the application has to successfully establish the first connection.

We should delay all AMQP operations, be it publishing, declaring resources or consuming from queue, until we have a connection. We follow the **Hollywood Principle** : *Don't call us, we'll call you*.

An application component that needs to publish a message will have to request a `com.rabbitmq.client.Connection` to an `AMPQConnectionProvider` and only when the connection is available, it gets notified.

```

        [App.Component]---(requestConnnection)--->[AMQPConnectionProviderImpl]
              /\                                    |     |      /\   |
               |                                    |     |       |   |
               |                                    |     |       +---+ mainLoop
               +----------(connectionAvailable)-----+     |
                                                          |
                                                    (newConnection)
                                                          |
                                                          \/
                                        [com.rabbitmq.client.ConnectionFactory]

```

1. An application component requests a `com.rabbitmq.client.Connection` to  an [AMQPConnectionProvider](https://github.com/rabbitmq/workloads/blob/master/resiliency/sample-resilient-java-rabbitmq/src/main/java/com/pivotal/resilient/amqp/AMQPConnectionProvider.java) by calling `requestConnectionFor` method like this:
  ```
    Producer producer = ...
    List<AMQPResource> producerResources =  ...
    amqpConnectionProvider.requestConnectionFor("my producer", producerResources, producer);
  ```
2. An application component that needs a `com.rabbitmq.client.Connection` must implement the interface [AMQPConnectionRequester](https://github.com/rabbitmq/workloads/blob/master/resiliency/sample-resilient-java-rabbitmq/src/main/java/com/pivotal/resilient/amqp/AMQPConnectionRequester.java).
3. [AMQPConnectionProviderImpl](https://github.com/rabbitmq/workloads/blob/master/resiliency/sample-resilient-java-rabbitmq/src/main/java/com/pivotal/resilient/amqp/AMQPConnectionProviderImpl.java), the implementation of [AMQPConnectionProvider](https://github.com/rabbitmq/workloads/blob/master/resiliency/sample-resilient-java-rabbitmq/src/main/java/com/pivotal/resilient/amqp/AMQPConnectionProvider.java),  is constantly making sure that all [AMQPConnectionRequester](https://github.com/rabbitmq/workloads/blob/master/resiliency/sample-resilient-java-rabbitmq/src/main/java/com/pivotal/resilient/amqp/AMQPConnectionRequester.java)(s) are fully resolved via its internal `mainLoop`
3. When `AMQPConnectionProviderImpl` detects that there are unresolved requests and there is no yet any `com.rabbitmq.client.Connection` opened yet, it opens one via the `com.rabbitmq.client.ConnectionFactory`.
4. When `AMQPConnectionProviderImpl` establishes a connection, it passes it to all registered and unresolved yet `AMQPConnectionRequester`(s).
5. Although not shown in the diagram, when an application component requests a `com.rabbitmq.client.Connection` it passes a list of `AMQPResource`(s) that it needs to operate. `AMQPConnectionProviderImpl` will only call `connectionAvailable` on a `AMQPConnectionRequester` (i.e. the application component) only if it succeeded to declare the requested `AMQPResource`(s).


#### Application cannot connect due to authentication failures

The application could encounter this issue right from the start, when it is trying to establish the first connection. Or it could happen while connected.

In the former case, the `AMQPConnectionProvider` logs the failure but keeps trying to establish the connection. It will never crash until we stop the application.

In the latter case, the Java AMPQ library will handle the connection recovery. When we create the `ConnectionFactory`, we explicitly set it to automatically recover connections.

From [RabbitMQConfiguration](https://github.com/rabbitmq/workloads/blob/master/resiliency/sample-resilient-java-rabbitmq/src/main/java/com/pivotal/resilient/RabbitMQConfiguration.java#L47-L60):
```
   ....
   @Bean
   public ConnectionFactory amqpConnectionFactory(Cloud cloud) {
       ConnectionFactory factory = new ConnectionFactory();

       initConnetionFactoryWithAMQPCredentials(cloud, factory);

       factory.setAutomaticRecoveryEnabled(true);
       factory.setTopologyRecoveryEnabled(false);
       factory.setConnectionTimeout(10000);
       factory.setNetworkRecoveryInterval(1000); // how long will automatic recovery wait before attempting to reconnect, in ms; default is 5000

       logger.info("Creating connection factory using username:{}, vhost:{}", factory.getUsername(), factory.getVirtualHost());

       return factory;
   }
   ....
```

#### RabbitMQ node becomes unavailable with applications connected to it

In the [Patterns for applications that uses Spring AMQP client](#patterns-for-applications-that-uses-spring-amqp-client), we covered all the situations that could lead to a node become unavailable.

Java AMQP library will automatically recover the connection(s). It will try indefinitely with a configurable interval (`ConnectionFactory.setNetworkRecoveryInterval`). Java AMQP will also randomly pick one address from the provided list of addresses.

As an application developer, we probably want our application components to react when we lose a connection or when we get it back. For instance, we could stop threads/timers we used to publish messages. Or we could flag our application components as *out of service* so that they can fail-fast, or simply report its status via a `health` endpoint.

[Publisher.SendAtFixedRate](https://github.com/rabbitmq/workloads/blob/master/resiliency/sample-resilient-java-rabbitmq/src/main/java/com/pivotal/resilient/workloads/Producer.java#L207-L268) is an example of a `AMQPConnectionRequester` which will schedule a task to publish a message at a fixed interval when it gets a connection:
```
@Override
public void connectionAvailable(Connection connection) {
    logger.info("{} has received a connection. Scheduling producer timer", getName());
    if (sendMessageAtFixedRate != null) {
        return; // it is already running. This method can be called several times for the same connection
    }

    sendMessageAtFixedRate = scheduler.scheduleAtFixedRate(() -> send(connection), this.fixedRate);
}
```
But it will cancel the scheduled task if it loses the connection:
```
  @Override
   public void connectionLost() {
       logger.info("{} has received a connection. Scheduling producer timer", getName());
       if (sendMessageAtFixedRate != null) {
           sendMessageAtFixedRate.cancel(true);
           sendMessageAtFixedRate = null;
       }
   }
```

#### RabbitMQ node with non-mirrored durable queues becomes unavailable

When an application requires **High Availability**, and more specifically **High Message Availability**,
the options are either to use [Mirrored Queues](https://www.rabbitmq.com/ha.html) or Sharding.
> Sharding is a rather long topic that we will address it in a future guide. But in a nutshell, it is about partitioning a
standard queue into multiple queues and host those queues on different nodes. Messages are then spread onto those partitions
 hence increasing the availability.
However if we lose a node hosting a partition queue, the messages in that node will not be available until the node comes back. In the meantime, messages can flow in and out of the remaining partitions. To shard a queue, we can either partition the queues ourselves and use [Consistent Hash exchange plugin](https://github.com/rabbitmq/rabbitmq-consistent-hash-exchange) to spread the messages. Or use the [Sharding plugin](https://github.com/rabbitmq/rabbitmq-sharding) instead so that RabbitMQ takes care of everything.

Publishers will not detect any problem except if they use the [mandatory](https://www.rabbitmq.com/amqp-0-9-1-reference.html#basic.publish.mandatory) flag. Even if they
used [Publisher Confirms](https://www.rabbitmq.com/confirms.html) they will not notice any errors. However, be aware of the [performance impact](https://www.rabbitmq.com/blog/2012/04/25/rabbitmq-performance-measurements-part-2/) of the [mandatory](https://www.rabbitmq.com/amqp-0-9-1-reference.html#basic.publish.mandatory) flag.

Consumers, in the contrary, will get their subscription shutdown (`com.rabbitmq.client.ShutdownSignalException`) and its channel closed. So, here comes two recommendations:
- the obvious one is to always handle the shutdown signal. Do not subscribe only when we establish a connection. We could get the subscription shutdown for other reasons not just when we lose a connection.
- use one channel per component/consumer, do not share it with other components in your application. The consumer could abruptly get its channel closed due to a failed AMQP operation or it could be closed by others if we shared the channel. So, do not share the channel.

An `AMQPConnectionRequester` must implement the `boolean isHealthy()` method. The `AMQPConnectionProvider` will check the health of each requester at certain interval. If an `AMQPConnectionRequester` is not healthy, and provided there is a connection, the `AMQPConnectionProvider` will call `connectionAvailable` giving the requester the chance to restore its service. A consumer which has no subscription yet is not healthy hence `isHealth()` will return false.

Sample code from [Consumer](https://github.com/rabbitmq/workloads/blob/master/resiliency/sample-resilient-java-rabbitmq/src/main/java/com/pivotal/resilient/workloads/Consumer.java#L112-L131) class where we handle the `ShutdownSignalException`:
```
  ...
  private void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
      logger.warn("{} received shutdownSignal event on consumerTag {}", getName(), consumerTag);
      consumerTag = null;
      closeSubscriptionChannel();
  }

  private AtomicReference<Channel> subscriptionChannel = new AtomicReference<>();

  private void closeSubscriptionChannel() {
      Channel channel = subscriptionChannel.getAndSet(null);
      if (channel == null) {
          return;
      }
      try {
          channel.close();
      } catch (IOException | TimeoutException e) {
          logger.warn("Failed to close channel. Ignore", e);
          throw new RuntimeException(e);
      }
  }
  ...

```

Sample code from [Consumer](https://github.com/rabbitmq/workloads/blob/master/resiliency/sample-resilient-java-rabbitmq/src/main/java/com/pivotal/resilient/workloads/Consumer.java#L80-L83) class that determines its health based on whether it has any subscription.
```
  ...
  @Override
  public boolean isHealthy() {
      return subscriptionChannel.get() != null;
  }
  ...
```

Sample code from [Consumer](https://github.com/rabbitmq/workloads/blob/master/resiliency/sample-resilient-java-rabbitmq/src/main/java/com/pivotal/resilient/workloads/Consumer.java#L56-L63) class where it establishes a subscription when it receives a connection:
```
@Override
public void connectionAvailable(Connection connection) {
    try {
        subscribeForMessages(connection);
    }catch(IOException e) {
        e.printStackTrace();
        logger.error("{} failed to consume messages", getName(), e.getMessage());
    }
}
```

Another recommendation is never to expect that resource declaration will always succeed. The reasons why we may fail to declare a resource are:
- we declare a durable queue which is already declared but the node is not available
- we declare a resource (queue, exchange) which is already declared but with a different configuration. It is unlikely that a queue which was initially configured as durable changes to non-durable or the other way around. But it is more likely that we change some its arguments like `x-message-ttl`. Here comes another recommendation, never programatically configure queues and/or exchanges via arguments; use [policies](https://www.rabbitmq.com/parameters.html) instead. If you do not use policies, you would have to delete the queue/exchange to change its arguments.

Sample code from [AMQPConnectionProviderImpl](https://github.com/rabbitmq/workloads/blob/master/resiliency/sample-resilient-java-rabbitmq/src/main/java/com/pivotal/resilient/amqp/AMQPConnectionProviderImpl.java#L109-L128) class where we handle resource declaration failures. When an `AMQPConnectionRequester` requests a connection to an `AMQPConnectionProvider` it can specify which AMQPResource(s) it requires to operate. The `AMQPConnectionProvider` will make sure that those resources are available before passing a connection to the `AMQPConnectionRequester`. Furthermore, some `AMQPConnectionRequester` could be happily working while others not because `AMQPConnectionProvider` could not satisfy them.
> This is an oversimplification. There could be complex application which may not know which resources they need right from the start. In that case, it is the application developer who should handle these failures.
```
    ...
    private void declareResources(AMQPConnectionRequest request, final Channel channel) {
       request.getRequiredResources().forEach(resource -> {
           try {
               amqpDeclarations.get(resource.getClass()).declare(resource, channel);
           } catch (IOException e) {
               // why could it fail?
               // 1) unexpected connection dropped
               // 2) wrong permissions
               // 3) ??
               logger.warn("Failed to declare resources for {}. Due to {}", request.getName(), e.getCause().getMessage());
               throw new RuntimeException(e);
           } catch (IllegalStateException e) {
               // when we pass invalid configuration
               // tell requester that it has wrong configuration configuration but let the other services to carry on ?
               // or maybe this is a good reason to terminate the app
               logger.warn("Failed to declare resources for {}. Due to wrong configuration: {}", request.getName(), e.getMessage());
               throw e;
           }
       });
   }
   ....
```

#### RabbitMQ node with non-mirrored non-durable queues becomes unavailable

If our application can tolerate message loss, non-durable queues is the best choice to achieve **Highly available queues**.

We could encounter 2 type of failures or situations. One where our consumer application was connected to the node hosting the non-durable queue. If the node goes down, the application looses the connection, Java AMQP library recovers the connection, the consumer detects that its subscription was shutdown and flags itself as *not healthy*. Eventually, the `AMQPConnectionProvider` detects the consumer is not healthy and it passes the connection so that it can subscribe.
The second failure is when our consumer application is connected to a node which does not host the non-durable queue. If the hosting queue node goes down, the application does not loose the connection but the consumer gets its subscription shutdown. Both are handled by the current design.

Be aware that Java RabbitMQ client has a feature called [Topology recovery](https://www.rabbitmq.com/api-guide.html#recovery) which re-declares AMQP resources (including bindings) and consumers. But in our reference application we have decided to disable this feature and always redeclare the resources and consumers.  
> When using topology recovery : you shouldn't close a channel after it has created some resources (queues, exchanges, bindings) or topology recovery for those resources will fail later, as the channel has been closed. Instead, leave creating channels open for the life of the application.


#### RabbitMQ cluster raises an alarm

The advise here is the same one we did for the Spring AMQP application; that is, dedicate a connection to consume messages and another one to publish.

The `RabbitMQConfiguration` class builds not just one `AMQPConnectionProvider` but two; one called *producer* and another *consumer*. And these names will be used to name the connection too so that we can clearly identify them in the management UI. Being the *producer* the default one. See that the *consumer* connection is in `Blocking` mode as opposed to `Blocked` in the *producer* connection.

![named connections](assets/blockedConnection.png)

All we need to do is to inject or wire the right `AMQPConnectionProvider` to the application component.

This producer gets injected the default, or `@Primary`, `AMQPConnectionProvider`.
```
public class ProducerConsumerWithDurableQueue {

    @Bean
    public Producer durableProducer(TaskScheduler taskScheduler, AMQPConnectionProvider amqpConnectionProvider) {
        ....
```   

Whereas, the consumer gets injected the *consumer*:
```
    @Bean
    public Consumer durableConsumer(@Qualifier("consumer") AMQPConnectionProvider amqpConnectionProvider) {
        ...
```


A second advise is to detect when the connection is blocked and use it in the application. For instance, we could implement a back-pressure mechanism. Publisher will not send and block but fail-fast and report upstreams that it is not available. Or record this situation via its health status which we can expose it through the actuator `/health` endpoint.

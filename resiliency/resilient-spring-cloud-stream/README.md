
# Spring Cloud Stream Patterns

The goal is to explore configuration and design patterns in Spring Cloud Stream
to achieve various levels of resiliency and delivery of guarantee.

We start with the most basic SCS app with minimal configuration and we challenge it with a number of failure scenarios. This is done [Scenario 1](#basic-consumer-producer-application-1)



## Getting started

By default, all the sample applications are configured to connect to a 3-node cluster
which we launch it by running:
```
deploy-rabbit-cluster
```
> It will deploy a cluster with nodes listening on 5673, 5674, 5575

To launch the application against a single standalone server, edit application.yml
and remove `cluster` as one of the included Spring profiles. And to deploy a  
standalone server run:
```
deploy-rabbit
```
> It will deploy a standalone server on port 5672


## Application with transient subscriptions

We can find this application under `scenario-1` folder.

It consists of 2 Spring `@Service`(s). One is a producer service (p`ScheduledTradeRequester`) and a second is a consumer service (`TradeLogger`). The consumer service uses transient, a.k.a. non-durable,
subscriptions. This means that the consumer will only receive messages sent while it is listening to
them. It uses non-durable, auto-delete queues.

This application automatically declares the AMQP resources such as exchanges, queues and bindings.
Otherwise we would have to configure (e.g. `missingQueuesFatal`) it to deal with situations where those resources do not exist.


### Verify resiliency

Out of the box, the application is pretty resilient to connection failures as we will
see in the next sections. It lacks of guarantee of delivery which we will address
in the next sections.

#### Failure 1 - RabbitMQ is not available when application starts

**TODO** provide details with regards retry max attempts and/or frequency if there are any
`recoveryInterval` is a property of consumer rabbitmq binder.

1. Stop RabbitMQ cluster
  ```
  ../docker/destroy-rabbit-cluster
  ```
2. Launch application with both roles, producer and consumer
  ```
  SPRING_PROFILES_ACTIVE=cluster ./run.sh --scheduledTradeRequester=true --tradeLogger=true
  ```
3. Check for fail attempts to connect in the logs
4. Start RabbitMQ cluster
  ```
  ../docker/deploy-rabbit-cluster
  ```

#### Failure 2 - Restart a cluster node

Pick the node where application is connected and the queue declared, say it is `rmq0`

1. Restart `rmq0` node:
  ```
  docker-compose -f ../docker/docker-compose.yml start rmq0
  ```
2. Check for reconnect attempts in the logs and how producer and consumer keeps working.
We should expect a sequence of logging statements like these two:
  ```
  Requesting Trade 2 for account 0
  Received [2] Trade 2 (account: 0) done
  ```
  > the first line corresponds to the producer emitting a trade request. Each
  trade has a automatically incrementing sequence number
  > the second line corresponds to the consumer receiving a trade request. The
  number in brackets is the total count of trades received so far.
  > When the number of trades received matches with the trade id it means that
  the consumer has not missed any trade request yet

#### Failure 3 - Rolling restart of cluster nodes

1. Rolling restart:
  ```
  ./rolling-restart
  ```
2. Wait until the script terminate to check how producer and consumer is still working
(i.e. sending and receiving)

#### Failure 4 - Kill producer connection (repeatedly)

**TODO** Investigate: It would be ideal to name connections based on the application name so that
it makes easier to identify who is connected. When we set the `connection-name-prefix`, it
fails with
```
he dependencies of some of the beans in the application context form a cycle:

   binderHealthIndicator defined in org.springframework.cloud.stream.binder.rabbit.config.RabbitServiceAutoConfiguration$RabbitHealthIndicatorConfiguration
      ↓
   rabbitTemplate defined in org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration$RabbitTemplateConfiguration
┌─────┐
|  rabbitConnectionFactory defined in org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration$RabbitConnectionFactoryCreator
↑     ↓
|  org.springframework.cloud.stream.binder.rabbit.config.RabbitMessageChannelBinderConfiguration (field private org.springframework.amqp.rabbit.connection.ConnectionFactory org.springframework.cloud.stream.binder.rabbit.config.RabbitMessageChannelBinderConfiguration.rabbitConnectionFactory)
└─────┘
```


1. Launch producer only
  ```
  ./run.sh --scheduledTradeRequester=true
  ```
2. Kill *producer* connection (via management UI, or by other means) named for instance
`rabbitConnectionFactory.publisher#1554b244:2`
  > Spring Cloud Stream will automatically create 2 connections if we have at least
  one producer channel. The producer connection has the label producer on its name

3. The producer should recover from it. We should get a similar logging sequence to this one:
```
2020-09-16 09:57:03.618  INFO 28370 --- [   scheduling-1] c.p.resilient.ScheduledTradeRequester    : [sent:23] Requesting Trade 24 for account 4
2020-09-16 09:57:08.471 ERROR 28370 --- [ 127.0.0.1:5673] o.s.a.r.c.CachingConnectionFactory       : Channel shutdown: connection error; protocol method: #method<connection.close>(reply-code=320, reply-text=CONNECTION_FORCED - Closed via management plugin, class-id=0, method-id=0)
2020-09-16 09:57:08.620  INFO 28370 --- [   scheduling-1] c.p.resilient.ScheduledTradeRequester    : [sent:24] Requesting Trade 25 for account 7
2020-09-16 09:57:08.621  INFO 28370 --- [   scheduling-1] o.s.a.r.c.CachingConnectionFactory       : Attempting to connect to: [localhost:5673, localhost:5674, localhost:5675]
2020-09-16 09:57:08.638  INFO 28370 --- [   scheduling-1] o.s.a.r.c.CachingConnectionFactory       : Created new connection: rabbitConnectionFactory.publisher#1554b244:2/SimpleConnection@1796c24f [delegate=amqp://guest@127.0.0.1:5673/, localPort= 53480]
```

4. Kill the other connection named for instance `rabbitConnectionFactory#1554b244:2`

5. The producer detects the connection was closed but it does not reopen it again.
  **TODO** explore the implications of this
```
2020-09-16 10:00:50.820 ERROR 28370 --- [ 127.0.0.1:5673] o.s.a.r.c.CachingConnectionFactory       : Channel shutdown: connection error; protocol method: #method<connection.close>(reply-code=320, reply-text=CONNECTION_FORCED - Closed via management plugin, class-id=0, method-id=0)
```


#### Failure 5 - Kill consumer connection (repeatedly)

1. Launch consumer with a processingTime of 5 seconds
  ```
  ./run.sh --tradeLogger=true --processingTime=5s --server.port=8082
  ```
2. Launch producer (which uses `tradeRateMs:1000` , i.e. a trade per second)
  ```
  ./run.sh --scheduledTradeRequester=true
  ```

3. Kill consumer connection (via management UI, or by other means)
  > Pick that connection which has a consumer in one of its channel

4. The consumer should recover from it. We should get a similar logging sequence to this one:
```
2020-09-16 10:34:25.373  INFO 30872 --- [3mb6uTnFmfrJQ-1] com.pivotal.resilient.TradeLogger        : Received [total:7,missed:0] Trade 7 (account: 3) done
2020-09-16 10:34:26.012 ERROR 30872 --- [ 127.0.0.1:5673] o.s.a.r.c.CachingConnectionFactory       : Channel shutdown: connection error; protocol method: #method<connection.close>(reply-code=320, reply-text=CONNECTION_FORCED - Closed via management plugin, class-id=0, method-id=0)
2020-09-16 10:34:30.377  INFO 30872 --- [3mb6uTnFmfrJQ-1] com.pivotal.resilient.TradeLogger        : Processed [total:7,missed:0] Trade 7 (account: 3) done
2020-09-16 10:34:30.381  INFO 30872 --- [3mb6uTnFmfrJQ-1] o.s.a.r.l.SimpleMessageListenerContainer : Restarting Consumer@63fdab07: tags=[[amq.ctag-8ub2RbGR8XG9edPLAN2MMA]], channel=Cached Rabbit Channel: AMQChannel(amqp://guest@127.0.0.1:5673/,1), conn: Proxy@54a67a45 Shared Rabbit Connection: SimpleConnection@2e554a3b [delegate=amqp://guest@127.0.0.1:5673/, localPort= 54676], acknowledgeMode=AUTO local queue size=0
2020-09-16 10:34:30.383  INFO 30872 --- [3mb6uTnFmfrJQ-2] o.s.a.r.c.CachingConnectionFactory       : Attempting to connect to: [localhost:5673, localhost:5674, localhost:5675]
2020-09-16 10:34:30.408  INFO 30872 --- [3mb6uTnFmfrJQ-2] o.s.a.r.c.CachingConnectionFactory       : Created new connection: rabbitConnectionFactory#53f0a4cb:1/SimpleConnection@3cd6ec24 [delegate=amqp://guest@127.0.0.1:5673/, localPort= 54686]
2020-09-16 10:34:30.408  INFO 30872 --- [3mb6uTnFmfrJQ-2] o.s.amqp.rabbit.core.RabbitAdmin         : Auto-declaring a non-durable, auto-delete, or exclusive Queue (trades.anonymous.pCUg5QrzR3mb6uTnFmfrJQ) durable:false, auto-delete:true, exclusive:true. It will be redeclared if the broker stops and is restarted while the connection factory is alive, but all messages will be lost.
2020-09-16 10:34:30.441  INFO 30872 --- [3mb6uTnFmfrJQ-2] com.pivotal.resilient.TradeLogger        : Received [total:8,missed:28] Trade 36 (account: 5) done
```

However, we should notice that our consumer has missed 28 messages. That is due to 2 factors.
The first is that our consumer is slower (processingTime:5s) than the producer (trade) so we
are creating a queue backlog. And second, when the connection is closed, we loose the backlog
because the queue is deleted and recreated it again.


**TODO** Investigate: I noticed that the consumer connection creates 2 channels after it recovers the connection rather than just one. However, it does not keep opening further channels should it
recovered from additional connection failures.


#### Failure 5 - Block producers

We are going to force RabbitMQ to trigger a memory alarm by setting the high water mark to 0.
This should only impact the producer connections and let consumer connections carry on.

1. Launch both roles together in the same application, but the a slower consumer
so that we create a queue backlog
```
./run.sh --tradeLogger=true --scheduledTradeRequester=true --processingTime=5s
```
2. Wait a couple of seconds until we produce a backlog
3. Set high water mark to zero
```
docker-compose -f ../docker/docker-compose.yml exec rmq0 rabbitmqctl set_vm_memory_high_watermark 0
```
4. Watch the queue depth goes to zero, i.e. the consumer is able to consume.
5. Watch messages stop coming to RabbitMQ. However, they are piling up in the tcp buffers.
When we restore the high water mark, we will see all those messages sent to RabbitMQ.
```
docker-compose -f ../docker/docker-compose.yml  exec rmq0 rabbitmqctl set_vm_memory_high_watermark 1.0
```

#### Failure 6 - Pause nodes

We are going to pause a node, which is similar to what happen when a network partition occurs
and the node is on the minority and we are using *pause_minority* cluster partition handling.

We are going to shutdown all nodes (`rmq2`, `rmq3`) except one (`rmq0`) where our applications
are connected to. This will automatically pause the last standing node because it is in minority.

1. Launch both roles together
```
./run.sh --tradeLogger=true --scheduledTradeRequester=true --processingTime=5s
```
2. Wait till we have produced a message backlog
3. Stop `rmq2`, `rmq3`
```
docker-compose -f ../docker/docker-compose.yml  stop rmq1 rmq2
```
4. Notice connection errors in the application logs. Also we have lost connection to the
management UI on `rmq0`.
5. Application keeps trying to publish but it fails
6. Start `rmq2`, `rmq3`
7. Notice application recovers and keeps publishing. The consumer has lost a few messages though.


### Guarantee of delivery

#### Zero guarantee of delivery on the producer

- Producer does not use publisher confirmation. If RabbitMQ fail to deliver a message
to a queue due to an internal issue or simply because the RabbitMQ rejects it, the
producer is not aware of it.
- Producer does not use mandatory flag hence if there are no queues bound to the exchange
associated to the outbound channel, the message is lost. The exchange is not configured with
an alternate exchange either.
- When producer fails to send (i.e. send operation throws an exception) a message,
the producer is pretty basic and it does not retry it.

#### Some Guarantees of delivery on the consumer

- Consumer will only get messages while it is running. Once we stop it, all messages which
were still in the queue are lost and also any message sent afterwards. This is because the
consumer service, TradeLogger, is an anonymous consumer. This means that the queue named
 `<channel_destination_name>.anonymous.<unique_id>` e.g. `q_trade_confirmations.anonymous.XbaJDGmDT7mNEgD6_ru9zw` is:
  - non-durable
  - exclusive
  - *auto-delete*   
- As long as the consumer is running, messages are delivered with all guarantees:
  - consumer only acks messages after it has successfully processed them
  - it processes messages in strict order (prefetch=1)

Do we need to make this type of queue mirrored? Not really, because an *auto-delete*
queue will be automatically deleted as soon as its last consumer has cancelled or when
the connection is lost.

#### Zero guarantees of delivery on the consumer

This time we are launching producer and consumer on separate application/process
and we are going to perform a rolling restart.

1. Start producer
  ```
  SPRING_PROFILES_ACTIVE=cluster ./run.sh --scheduledTradeRequester=true
  ```
2. Start consumer (on a separate terminal)  
  ```
  SPRING_PROFILES_ACTIVE=cluster ./run.sh --tradeLogger=true --server.port=8082
  ```
3. Rolling restart
  ```
  ./rolling-restart
  ```
4. Watch the consumer logs and eventually we will notice a discrepancy. It has
received so far 11 trades however this is the 12th trade sent. We lost one.
  ```
  Received [11] Trade 12 (account: 2) done
  ```

## Application with durable subscriptions

We are using the same application but this time we are using a different
consumer `@Service` called `DurableTradeLogger`. This service uses a *Consumer Group*
called after its name `trade-logger` which creates a durable queue called
`queue.trade-logger`.

We switched to durable subscriptions so that we did not lose messages. However, we need to
ensure that the producer stream uses `deliveryMode: PERSISTENT` which is the default
value though.

#### Failure 2 - Shutdown queue hosting node

Our consumer will not be able to consume while the queue hosting node is down. Furthermore,
if the producer does not use mandatory flag and/or alternate-exchange, those messages are lost too. 

1. Launch durable consumer
```
./run.sh --durableTradeLogger=true
```
2. Stop the hosting node. Most likely the queue will be on the first node, `rmq0`.
```
docker-compose -f ../docker/docker-compose.yml stop rmq0
```
3. We will notice the consumer fails to declare the queue but it keeps indefinitely trying.
```
2020-09-16 16:56:17.050 o.s.a.r.l.BlockingQueueConsumer          Failed to declare queue: trades.trade-logger
2020-09-16 16:56:17.051 o.s.a.r.l.BlockingQueueConsumer          Queue declaration failed; retries left=1

Caused by: com.rabbitmq.client.ShutdownSignalException: channel error; protocol method: #method<channel.close>(reply-code=404, reply-text=NOT_FOUND - home node 'rabbit@rmq0' of durable queue 'trades.trade-logger' in vhost '/' is down or inaccessible, class-id=50, method-id=10)

```
4. Start the hosting node.
```
docker-compose -f ../docker/docker-compose.yml start rmq0
```
5. The consumer is able to declare the queue and subscribe to it.
```
2020-09-16 16:51:47.022 o.s.a.r.l.BlockingQueueConsumer          Queue declaration succeeded after retrying
```

If we want to limit the amount of retries and terminate the application we have to use these [RabbitMQ Binder consumer settings](https://cloud.spring.io/spring-cloud-static/spring-cloud-stream-binder-rabbit/2.2.0.M1/spring-cloud-stream-binder-rabbit.html#_rabbitmq_consumer_properties):
  * `missingQueuesFatal: true`
  * `queueDeclarationRetries: 3`
  * `failedDeclarationRetryInterval: 5000`


## Application with highly available subscriptions

In order to address the issues found in the [previous](#Application-with-durable-subscriptions) scenario
we are going to make the queue mirrored.

**TODO** Investigate how to use QuorumQueues


## Resilient consumer processing

`maxAttempts`, `backOffX`, `defaultRetryable`, `retryableExceptions`
https://cloud.spring.io/spring-cloud-static/spring-cloud-stream/current/reference/html/spring-cloud-stream.html#_consumer_properties

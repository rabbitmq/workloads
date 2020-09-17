
# Spring Cloud Stream Patterns

The goal is to propose various types of Spring Cloud Stream applications. Each type offers
different levels of data loss and/or downtime tolerance.

**Table of content**
<!-- TOC depthFrom:2 depthTo:3 withLinks:1 updateOnSave:1 orderedList:0 -->

- [Application types](#application-types)
	- [Transient consumer](#transient-consumer)
	- [Durable consumer](#durable-consumer)
	- [HA Durable consumer](#ha-durable-consumer)
	- [Resilient consumer processing](#resilient-consumer-processing)
	- [Fire-and-forget producer](#fire-and-forget-producer)
	- [Guarantee Delivery producer](#guarantee-delivery-producer)
- [Testing Applications](#testing-applications)
	- [How to deploy RabbitMQ](#how-to-deploy-rabbitmq)
	- [Verify resiliency of Transient consumer](#verify-resiliency-of-transient-consumer)
	- [Verify Guarantee of delivery of fire-and-forget producer](#verify-guarantee-of-delivery-of-fire-and-forget-producer)
	- [Verify delivery guarantee on the producer - Declare the consumer groups' queues](#verify-delivery-guarantee-on-the-producer-declare-the-consumer-groups-queues)
	- [Verify delivery guarantee on the producer - Ensure messages are successfully sent](#verify-delivery-guarantee-on-the-producer-ensure-messages-are-successfully-sent)

<!-- /TOC -->

## Application types

### Transient consumer

A transient consumer is one where messages are delivered to the consumer application only when that application is running and connected to the broker. Messages sent while the application is
disconnected are lost.

This type of consumer creates a queue named `<channel_destination_name>.anonymous.<unique_id>` e.g. `q_trade_confirmations.anonymous.XbaJDGmDT7mNEgD6_ru9zw` with this attributes:
  - *non-durable*
  - *exclusive*
  - *auto-delete*   

---

We can find an example of this type of consumer in the project [transient-consumer](transient-consumer).

It consists of 2 Spring `@Service`(s). One is a producer service (p`ScheduledTradeRequester`) and a second is a transient consumer service (`TradeLogger`).

**TODO** Maybe include some snippets of SCS configuration and sample code

This application automatically declares the AMQP resources such as exchanges, queues and bindings.

#### What is this consumer useful for

- monitoring/dashboard applications which provide real-time stats;
- audit/logger applications which sends messages to a persistent state such as
ELK;
- keep local-cache up-to-date

#### What about data loss?

As we already know it will not get messages delivered while it is not connected.
Once connected, the consumer uses *Client Auto Acknowledgement* therefore it will not lose
queued messages, as long as it is connected. Once it disconnects, or it looses the connection,
all queued messages are lost.

If we are using this type of consumer to keep a local-cache up-to-date with updates
that come via messages, we need to know when we are processing the first message so that
we clear the cache and prime it.

#### Is this consumer highly available

This consumer is **highly available** as long as the broker has at least one node where to
connect. The consumer will always recreate the queue, therefore the queue it uses is
non-durable, auto-delete and exclusive.

**IMPORTANT**: We should not include this type of queues in HA policies because an *auto-delete*
queue will be automatically deleted as soon as its last consumer is cancelled or when
the connection is lost.

#### Is this consumer resilient to connection failures?

There are different reasons why we may experience connections failure:

- The application cannot establish the first connection because the cluster is not available
- The application is connected to a node and it goes down
- The application cannot establish the connection because the credentials are wrong
- The application is connected to a a node and it is paused (e.g. due to network partition)

#### Can I add more consumer instances to increase throughput

We cannot because the queue is declared as *exclusive*.

**TODO** Check if we can have +1 consumers within a single app instance

#### What other failures this consumer has to deal with

Other failures have to do with AMQP resource availability. By default, this consumer
is configured to declare the exchange and the queue too. Producer and consumer have to
agree on the exchange name (`bindings.<channelName>.destination`) and type (default is
*Topic Exchange*). If we do not change the type of exchange there are fewer chances for
failures. If we used a different exchange type then both applications must use the same
otherwise they will fail to declare it.

In the contrary, we may choose to declare the AMQP exchange externally. If the resources
are not available when the application starts, it will fail. But we can configure it to
keep retrying until the resource is declared. Or give up and terminate after N failed attempts.


### Durable consumer

A durable consumer means that messages it is subscribed to will be delivered even when it is
not running at the particular point of time. Once it reconnects to the broker, all the messages that were posted in the meantime will be delivered immediately.

---

We can find an example f¡of this type of consumer in the project [durable-consumer](durable-consumer).
It consists of one durable consumer called `DurableTradeLogger`. This service uses a *Consumer Group*
called after its name `trade-logger` which creates a durable queue called
`queue.trade-logger`.

We switched to durable subscriptions so that we did not lose messages. However, we need to
ensure that the producer stream uses `deliveryMode: PERSISTENT` which is the default
value though. If the producer did not send messages as persistent, they will be lost
if the queue's hosting node goes down.

#### What about data loss

The consumer uses *Client Auto Acknowledgement* therefore it will not lose
 messages due to failures that may occur while processing the message.

However, queued messages -i.e. messages which are already in the queue- may be lost if
the messages are not sent with persistent flag. By default, Spring Cloud Stream will
send messages as persistent unless we change it. Non-persistent messages are only kept
in memory and if the queue's hosting node goes down, they will be lost.

*IMPORTANT*: We are always taking about queued messages. We are not talking yet about all kind of messages, including those which are about to be sent by the producer.

#### Can I add more consumer instances to increase throughput

**TODO**

#### Is this consumer highly available

By default, this consumer is **not highly available**. Its uptime depends on the
uptime of queue's hosting node goes down.

Is this suitable for my case? That depends on your business case. If the consumer
can tolerate a downtime of less than an hour which is the maximum time any of nodes
can be down then this consumer is suitable. Else, we need to make it HA. Look at the [next](#ha-durable-consumer) type of application.

#### What about strict order processing of messages?

If we need to have strict ordering of processing of messages we need to use `exclusive: true` attribute. If we have more instances, they will fail to subscribe but will retry based on the `recoveryInterval: 5000` attribute.

**TODO** investigate how to set *single-active-consumer* on SCS

### HA Durable consumer

In order to improve the availability of the [durable-consumer](#durable-consumer) application
we are going to configure the queue as mirrored.
There are two ways:
- The queue's name must follow some naming convention, e.g. `ha-*`, because there is a policy
that configures those queues as *Mirror queue*.
- Application puts (using the Management Rest API) a custom policy which configures the queue
as *Mirrored*.


**TODO** Investigate how to use QuorumQueues

### Resilient consumer processing

By default, Spring Cloud Stream will use client acknowledgement (`acknowledgeMode: AUTO)`. It will reject messages if the application failed to process and it will not requeue them. We can change this behaviour though with `requeueRejected: true`. But be careful changing this value because it could produce a storm of poisonous messages unless the application raises an `AmqpRejectAndDontRequeueException`.

**TODO**

`maxAttempts`, `backOffX`, `defaultRetryable`, `retryableExceptions`
https://cloud.spring.io/spring-cloud-static/spring-cloud-stream/current/reference/html/spring-cloud-stream.html#_consumer_properties


### Fire-and-forget producer

This type of producer does not guarantee that the message is delivered to all
bound queues. Instead, it sends the message and forgets about it. If there were
issues with the message, the message would be lost.

---

We can find an example of this type of producer in the project [transient-consumer](transient-consumer). It is the `ScheduledTradeRequester` producer that we have used so far.

#### When is this type of producer useful

- When data is not massively critical and consumers can tolerate message loss.
- Especially interesting when the consumers are of type transient
- When consumers use some eviction strategy in their queues, either max-length or ttl.


### Guarantee Delivery producer

This type of producer guarantee that the message is always delivered to all bound queues.

To do it, the producer uses these mechanisms:
1. Publish messages using *RabbitMQ Publisher Confirms*. A message is said to be delivered only
when we receive a confirmation for it. Without this mechanism, we are doing *fire-and-forget*.
2. Retry failed attempt to publish a message.
3. Retry *Negative Publisher Confirm*.
4. Publish messages as *persistent* otherwise the broker may loose them when the queue is
offline (this is when the queue's hosting node goes down).

When the producer sends critical messages it automatically implies that they will
be consumed by durable consumers. To further improve the delivery guarantees of the producer
we use these additional mechanisms:
1. Declare all destination queues, a.k.a, *consumer groups*, using a new property called `requiredGroups`. A message may need to be delivered to more than one application. Hence, the producer has to be told which those *consumer groups* are.

---

Configure `errorChannelEnabled: true` so that *send failures* are sent to an error channel for the destination. We need this setting so that we can configure the following one. The error channel is called <destinationName>.errors e.g. `destinationName.errors`

Configure the RabbitMQ binder so that we receive confirmations of successfully sent Trades. We need to specify the name of the channel. Unsuccessful confirmations are sent to the *error channel*.

*IMPORTANT*: The connection factory must be configured to enable publisher confirms.

Configure RabbitMQ's binder (`application-cluster.yml`)to use publisher confirms and publisher returns.


## Testing Applications

### How to deploy RabbitMQ

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


### Verify resiliency of Transient consumer

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


#### Failure 6 - Block producers

We are going to force RabbitMQ to trigger a memory alarm by setting the high water mark to 0.
This should only impact the producer connections and let consumer connections carry on.

1. Launch both roles (`tradeLogger` and `scheduledTradeRequester`) together in the same application, but the a slower consumer so that we create a queue backlog
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

#### Failure 7 - Pause nodes

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


### Verify Guarantee of delivery of fire-and-forget producer

- Producer does not use publisher confirmation. If RabbitMQ fail to deliver a message
to a queue due to an internal issue or simply because the RabbitMQ rejects it, the
producer is not aware of it.
- Producer does not use mandatory flag hence if there are no queues bound to the exchange
associated to the outbound channel, the message is lost. The exchange is not configured with
an alternate exchange either.
- When producer fails to send (i.e. send operation throws an exception) a message,
the producer is pretty basic and it does not retry it.

#### Verify Guarantee of delivery on the transient consumer while it is subscribed

As long as the consumer is running, messages are delivered with all guarantees:
  - consumer only acks messages after it has successfully processed them
  - it processes messages in strict order (prefetch=1)

**TODO** simulate processing failure
**TODO** test poison messages and how to deal with them

#### Verify zero guarantee of delivery on the transient consumer when connection drops

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

#### Verify durable consumer - Failure 2 - Shutdown queue hosting node

Our consumer will not be able to consume while the queue's hosting node is down. Furthermore,
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

If we cannot afford to lose messages and/or have downtime of our consumer service then
we should make the queue highly available. Take a look at [Application with highly available subscriptions](#Application-with-highly-available-subscriptions).

### Verify delivery guarantee on the producer - Declare the consumer groups' queues

1. Destroy the cluster and recreate it again so that we start without any queues
```
./destroy-rabbit-cluster
./deploy-rabbit-cluster
```
2. Launch the producer
```
./run.sh --scheduledTradeRequester=true
```
3. Check the queue `trades.trade-logger` exists and it is getting messages even though
the consumer has not started yet.

However, our producer will not guarantee delivery when the queue's hosting node is down.
These are the two scenarios we can encounter:

- The producer starts up and the queue's hosting node is down. In this scenario,
the producer will attempt to declare it and it will fail. It does not terminate.
Any attempt to send a message will succeed but the message will go nowhere, it will be lost.
- The producer starts up and successfully declares the queue. However, later on,
the queue's hosting node goes down. The messages will go no where, they will be lost.

Conclusion: Adding `requiredGroups` setting in the producer, help us in reducing the
amount of message loss but it does not prevent it entirely. It is convenient because we
can start applications, producer or consumer, in any order. However, we are coupling the producer with the consumer. Also, should we added more consumer groups, we would have to reconfigure our producer application.

### Verify delivery guarantee on the producer - Ensure messages are successfully sent

1. Launch the producer. By default, `--scheduledTradeRequester=true` is already set.
```
./run.sh
```
2. Check that messages go to the `trades.trade-logger` queue and also new logging statements that
informs the message was successfully sent.
```
[sent:27] Requesting Trade{accountId=6, asset='null', amount=0, buy=false, timestamp=0}
Received publish confirm => Trade{accountId=6, asset='null', amount=0, buy=false, timestamp=0}
```
3. Put a max-length limit on the queue by invoking the following script that puts a policy.
```
PORT=15673 ./set_limit_on_queue "trade-logger" 5
```
> PORT=15673 allows us to target the first node in the cluster otherwise it would use 15672

4. Notice that after 5 messages, it starts reporting errors.
```
2020-09-17 19:58:07.145 c.p.r.ScheduledTradeRequester            [attempts:2,sent:0] Requesting Trade{accountId=3, asset='null', amount=0, buy=false, timestamp=0}
2020-09-17 19:58:07.151 c.p.r.ScheduledTradeRequester            An error occurred while publishing Trade{accountId=3, asset='null', amount=0, buy=false, timestamp=0}```
```

5. The producer has been programmed so that it does not send more trades but retry trades which
have not been confirmed yet (`pendingTrades`).

6. Remove the max-length limit and we see the producer successfully sends pending trades and continues
with newer ones.
```
PORT=15673 ./unset_limit_on_queue 
```

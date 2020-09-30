
# Spring Cloud Stream Patterns

The goal of this workload is to provide guidance to developers on how to write Spring Cloud Stream applications which are resilient to failures and guarantee message delivery, or put it
other words, that it does not loose messages.

Not all applications requires the same level of resiliency, or message delivery guarantee or
tolerance to downtime. For this reason, we are going to create [different kinds of consumer and producer applications](#application-types), where each type gives us certain level of resiliency and/or guarantee of delivery. And then we are going to [test](#testing-applications) them against various [failure scenarios](#failure-scenarios).


**Table of content**
<!-- TOC depthFrom:2 depthTo:4 withLinks:1 updateOnSave:1 orderedList:0 -->

- [Application types](#application-types)
	- [Transient consumer](#transient-consumer)
		- [What is this consumer useful for](#what-is-this-consumer-useful-for)
		- [What about data loss](#what-about-data-loss)
		- [Is this consumer highly available](#is-this-consumer-highly-available)
		- [Is this consumer resilient to connection failures](#is-this-consumer-resilient-to-connection-failures)
		- [What other failures this consumer has to deal with](#what-other-failures-this-consumer-has-to-deal-with)
	- [Durable consumer](#durable-consumer)
		- [What about data loss](#what-about-data-loss)
		- [Is this consumer highly available](#is-this-consumer-highly-available)
		- [What about strict order processing of messages](#what-about-strict-order-processing-of-messages)
	- [Highly available Durable consumer](#highly-available-durable-consumer)
		- [HA Durable consumer with classical mirrored queues](#ha-durable-consumer-with-classical-mirrored-queues)
		- [HA Durable consumer with quorum queues](#ha-durable-consumer-with-quorum-queues)
	- [Reliable consumer](#reliable-consumer)
		- [Dealing with processing failures](#dealing-with-processing-failures)
		- [Dealing with processing failures without losing messages](#dealing-with-processing-failures-without-losing-messages)
	- [Fire-and-forget producer](#fire-and-forget-producer)
		- [When is this type of producer useful](#when-is-this-type-of-producer-useful)
	- [Guarantee Delivery producer](#guarantee-delivery-producer)
- [Testing Applications](#testing-applications)
	- [Failure scenarios](#failure-scenarios)
	- [Resiliency Matrix](#resiliency-matrix)
	- [How to deploy RabbitMQ](#how-to-deploy-rabbitmq)
	- [<a name="1a"></a> Verify resiliency - 1.a RabbitMQ is not available when application starts](#a-name1aa-verify-resiliency-1a-rabbitmq-is-not-available-when-application-starts)
		- [:white_check_mark: All applications are resilient to this failure](#whitecheckmark-all-applications-are-resilient-to-this-failure)
	- [<a name="1b"></a> Verify resiliency - 1.b Restart a cluster node the application is connected to](#a-name1ba-verify-resiliency-1b-restart-a-cluster-node-the-application-is-connected-to)
	- [<a name="1c"></a> Verify resiliency - 1.c Restart a cluster node hosting the consumer's queue](#a-name1ca-verify-resiliency-1c-restart-a-cluster-node-hosting-the-consumers-queue)
	- [<a name="1d"></a> Verify resiliency - 1.d Rolling restart of cluster nodes](#a-name1da-verify-resiliency-1d-rolling-restart-of-cluster-nodes)
	- [<a name="1e"></a><a name="1ep"></a> Verify resiliency - 1.e Kill producer connection (repeatedly)](#a-name1eaa-name1epa-verify-resiliency-1e-kill-producer-connection-repeatedly)
	- [<a name="1ec"></a> Verify resiliency - 1.e Kill consumer connection (repeatedly)](#a-name1eca-verify-resiliency-1e-kill-consumer-connection-repeatedly)
	- [<a name="1g"></a> Verify guarantee of delivery - 2.g Block producers](#a-name1ga-verify-guarantee-of-delivery-2g-block-producers)
	- [Verify resiliency - 1.e Pause nodes](#verify-resiliency-1e-pause-nodes)
	- [<a name="1f"></a> Verify resiliency - 1.f Unresponsive connections](#a-name1fa-verify-resiliency-1f-unresponsive-connections)
	- [Verify Guarantee of delivery - Connection drops while sending a message](#verify-guarantee-of-delivery-connection-drops-while-sending-a-message)
		- [:x: Fire-and-forget looses the message](#x-fire-and-forget-looses-the-message)
		- [:white_check_mark: Reliable producer retries the failed operation](#whitecheckmark-reliable-producer-retries-the-failed-operation)
	- [Verify Guarantee of delivery - RabbitMQ fails to accept a sent message](#verify-guarantee-of-delivery-rabbitmq-fails-to-accept-a-sent-message)
		- [:x: Fire-and-forget looses a message if RabbitMQ fails to accept it](#x-fire-and-forget-looses-a-message-if-rabbitmq-fails-to-accept-it)
		- [:white_check_mark: Reliable producer knows when RabbitMQ fails to accept a message](#whitecheckmark-reliable-producer-knows-when-rabbitmq-fails-to-accept-a-message)
	- [Verify Guarantee of delivery - RabbitMQ cannot route a message](#verify-guarantee-of-delivery-rabbitmq-cannot-route-a-message)
		- [:x: Fire-and-forget looses a message if RabbitMQ cannot route it](#x-fire-and-forget-looses-a-message-if-rabbitmq-cannot-route-it)
		- [:white_check_mark: Reliable producer knows when RabbitMQ cannot route a message](#whitecheckmark-reliable-producer-knows-when-rabbitmq-cannot-route-a-message)
	- [Verify Guarantee of delivery - Consumer fail to process a message](#verify-guarantee-of-delivery-consumer-fail-to-process-a-message)
		- [:white_check_mark: All consumer types will never lose the message](#whitecheckmark-all-consumer-types-will-never-lose-the-message)
	- [Verify Guarantee of delivery - Connection drops while processing a message](#verify-guarantee-of-delivery-connection-drops-while-processing-a-message)
		- [:x: Fire-and-forget looses all enqueued messages so far](#x-fire-and-forget-looses-all-enqueued-messages-so-far)
	- [Verify durable consumer - Failure 2 - Shutdown queue hosting node](#verify-durable-consumer-failure-2-shutdown-queue-hosting-node)
	- [Verify delivery guarantee on the producer - Ensure the consumer groups' queues exists](#verify-delivery-guarantee-on-the-producer-ensure-the-consumer-groups-queues-exists)
	- [Verify delivery guarantee on the producer - Ensure messages are successfully sent](#verify-delivery-guarantee-on-the-producer-ensure-messages-are-successfully-sent)
	- [Verify delivery guarantee - Consumer fails to process a message](#verify-delivery-guarantee-consumer-fails-to-process-a-message)
	- [Verify delivery guarantee - Consumer gives up after failing to process a message](#verify-delivery-guarantee-consumer-gives-up-after-failing-to-process-a-message)

<!-- /TOC -->

## Application types

### Transient consumer

A **transient consumer** only receives messages which were sent after the consumer has connected to the broker and declared its *non-durable* queue bound to the corresponding *exchange*. If the consumer disconnects from the broker, it looses the queue and all its messages.

This type of consumer creates a queue named `<channel_destination_name>.anonymous.<unique_id>` e.g. `q_trade_confirmations.anonymous.XbaJDGmDT7mNEgD6_ru9zw` with this attributes:
  - *non-durable*
  - *exclusive*
  - *auto-delete*   

We can find an example of this type of consumer in the project [transient-consumer](transient-consumer).

It consists of 2 Spring `@Service`(s):
- `ScheduledTradeRequester` is a producer service
- `TradeLogger` is a transient consumer service

**TODO** Does it help to include some snippets of SCS configuration and sample code?

This application automatically declares the AMQP resources such as exchanges, queues and bindings.

#### What is this consumer useful for

- monitoring/dashboard applications which provide real-time stats;
- audit/logger applications which sends messages to a persistent state such as
ELK;
- keep local-cache up-to-date

#### What about data loss

As we already know, it will not get messages which are delivered while it is not connected.
Once connected, the consumer uses *Client Auto Acknowledgement* therefore it will not lose queued messages, as long as it is connected. Once it disconnects, or it looses the connection, all queued messages are lost.

If we are using this type of consumer to keep a local-cache up-to-date with updates
that come via messages, we need to know when we are processing the first message so that
we clear the cache and prime it.
> You can add a ApplicationListener<ListenerContainerConsumerFailedEvent> and listen for AsyncConsumerStartedEvent(s). More info [here](https://docs.spring.io/spring-amqp/reference/html/#async-consumer).

#### Is this consumer highly available

This consumer is **highly available** as long as the broker has at least one node where to connect. The consumer will always recreate the queue, therefore the queue it uses is non-durable, auto-delete and exclusive.

**IMPORTANT**: We should not include this type of queues in HA policies because an *auto-delete* queue will be automatically deleted as soon as its last consumer is cancelled or when the connection is lost.

#### Is this consumer resilient to connection failures

There are different reasons why we may experience connections failure:

- The application cannot establish the first connection because the cluster is not available
- The application is connected to a node and it goes down
- The application cannot establish the connection because the credentials are wrong
- The application is connected to a a node and it is paused (e.g. due to network partition)

Check out the resiliency of this type of application in the [resiliency matrix](#resiliency-matrix) below.

#### What other failures this consumer has to deal with

Other failures have to do with AMQP resource availability. Let's discuss the
two possible scenarios:

**Application-managed AMQP resources**

By default, this consumer is configured to declare the exchange and the queue. Producers and consumers have to agree on the exchange name (`bindings.<channelName>.destination`) and type (default is
*Topic Exchange*). If we do not change the type of exchange there are fewer chances for
failures. If they used a different exchange type then the first application to declare it
will succeed and the latter will fail.

**Externally-managed AMQP resources**

In the contrary, we may choose to declare the AMQP exchange externally. When the application
starts up, the resources must be available otherwise the application will fail. However, we can configure the application to keep retrying until the resource is declared. Or give up and terminate after N failed attempts.


### Durable consumer

A **durable consumer** receives messages sent after the consumer connected to the broker
and created the *durable queue* bound to the corresponding *exchange*. However, contrary to
the [Transient consumer](#transient-consumer), when **Durable consumer** disconnects from the broker, the queue remains in the broker receiving more messages. Once the **Durable consumer** reconnects to the broker, all the messages that were posted in the meantime will be delivered.

We can find an example of this type of consumer in the project [durable-consumer](durable-consumer).
It consists of one durable consumer called `DurableTradeLogger`. This service uses a *Consumer Group* called after its name `trade-logger` which creates a durable queue called
`queue.trade-logger`.

We switched to durable subscriptions so that we did not lose messages. However, we need to
ensure that the message producer uses `deliveryMode: PERSISTENT` which is the default
value though. If the producer did not send messages as persistent, they will be lost
if the queue's hosting node goes down.

#### What about data loss

The consumer uses *Client Auto Acknowledgement* therefore it will not lose
 messages due to failures that may occur while processing the message.

However, queued messages -i.e. messages which are already in the queue- may be lost if
the messages are not sent with the *persistent flag*. By default, Spring Cloud Stream will
send messages as persistent unless we change it. Non-persistent messages are only kept
in memory and if the queue's hosting node goes down, they will be lost.

*IMPORTANT*: We are always taking about queued messages. We are not talking yet about all kind of messages, including those which are about to be sent by the producer.


#### Is this consumer highly available

By default, this consumer is **not highly available**. Its uptime depends on the
uptime of queue's hosting node goes down.

Is this suitable for my case? That depends on your business case. If the consumer
can tolerate a downtime of less than an hour which is the maximum time any of nodes
can be down then this consumer is suitable. Else, we need to make it HA. Look at the [next](#highly-available-durable-consumer) type of application.

#### What about strict order processing of messages

If we need to have strict ordering of processing of messages we need to use `exclusive: true` attribute. If we have more instances, they will fail to subscribe but will retry based on the `recoveryInterval: 5000` attribute.

**TODO** investigate how to set *single-active-consumer* on SCS

### Highly available Durable consumer

In order to improve the availability of the [durable-consumer](#durable-consumer) application we need to use highly available queues so that if the queue's hosting node goes down, the broker is able to elect a replica/slave node as master node.

#### HA Durable consumer with classical mirrored queues

In order to improve the availability of the [durable-consumer](#durable-consumer) application we are going to configure the queue as mirrored.

There are two ways:
- The queue's name must follow some naming convention, e.g. `ha-*`, because there is a policy
that configures those queues as *Mirror queue*.
- Application puts (using the Management Rest API) a custom policy which configures the queue
as *Mirrored*.

#### HA Durable consumer with quorum queues

It requires [SCS 3.0.0-RELEASE](https://cloud.spring.io/spring-cloud-static/spring-cloud-stream-binder-rabbit/3.0.0.RELEASE/reference/html/spring-cloud-stream-binder-rabbit.html)

We need to configure the RabbitMQ Binder's consumer bindings with `quorum.enabled: true`.

If we want producers to declare the consumer's queue via the `requiredGroups` then we have to
also specify `quorum.enabled: true` in the RabbitMQ Binder's producer bindings.


### Reliable consumer

By default, Spring Cloud Stream uses client acknowledgement (`acknowledgeMode: AUTO)`.
This means that if our listener threw an exception while processing a message, it would not be lost. Instead, the message is nacked and returned back to the queue and delivered again. This retry mechanism is enabled by default on SCS as we will see in the [next](#dealing-with-processing-failures) section.

In order to test consumer's reliability, we need to simulate failures while processing
messages. For this reason, we have created another consumer project called [reliable-consumer](reliable-consumer). It still has the same durable consumer called `DurableTradeLogger`.


#### Dealing with processing failures

If the listener fails to process the message and throws an exception (different to `AmqpRejectAndDontRequeueException`), SCS nacks the message and the broker delivers it again.

However, if the listener keeps failing, SCS will eventually reject it and the message is lost if the queue has not been configured with a *dead-letter-queue* (see [next](#Dealing-with-processing-failures-without-losing-messages) section).

These are the consumer bindings' [settings](https://cloud.spring.io/spring-cloud-static/spring-cloud-stream/current/reference/html/spring-cloud-stream.html#_consumer_properties) that control the retries:
  - `maxAttempts: 3`
  - `backOffInitialInterval: 1000`
  - `backOffMaxInterval: 1000`
  - `defaultRetryable: true`
  - `retryableExceptions`


> We can change this behaviour with `requeueRejected: true`. But be careful changing this value because it could produce a storm of poisonous messages unless the application raises an `AmqpRejectAndDontRequeueException`.

> We should not retry exceptions related to parsing/deserializing messages and/or
business exceptions. Because it will always fail.
> Whereas, we should retry infrastructure related exceptions such as connectivity issues to downstream
services over http, jdbc, etc.


#### Dealing with processing failures without losing messages

Once the consumer has exceeded the maximum of number of retries, we want to move the message to an
error queue so that we do not lose it and it can be handled separately.

SCS RabbitMQ binder allows to configure a queue with a dead-letter-queue. All we need to do is
add a `autoBindDlq: true` to the consumer channel. Check out [application-dlq](reliable-consumer/src/main/resources/application-dlq.yml).


**VERY IMPORTANT**: Once we configure our queue with DLQ or any other features via one of the
SCS settings, we cannot change it otherwise our application fails to declare it. Moreover, if we
also configure the producer -via `requiredGroups`- to declare the queue, we will see failures
happening in both, consumer and producer. Those failures are not fatal but annoying.


### Fire-and-forget producer

This type of producer does not guarantee that the message is delivered to all
bound queues. Instead, it sends the message and forgets about it.

The following circumstances will cause a message to be lost however this producer will
never know it because it does not expect confirmation that it was sent:
- connection drops with the message in transit
- the broker rejects the message (e.g. due to *ttl* or *max-length* policy)
- the broker could not find a destination queue for it
- the broker failed to accept the message due to an internal error

We can find an example of this type of producer in the project [transient-consumer](transient-consumer). It is the `ScheduledTradeRequester` producer that we have used so far.

#### When is this type of producer useful

- When data is not massively critical and consumers can tolerate message loss.
- Especially interesting when the consumers are of type transient
- When consumers use some eviction strategy in their queues, either max-length or ttl.


### Guarantee Delivery producer

First of all, let's clarify what we mean by guarantee of delivery.

There are at least 2 ways to ensure that messages do get delivered (i.e. arrive to the destination queue(s)).

 The interactive way where the sender/caller expects to get a confirmation when the message is eventually delivered or when it could not be. Depending how we implement it, the caller can wait until the message is *confirmed* before continue the business transaction or it can do it asynchronously.

 [TradeRequesterController.execute-async]() method is an example. It calls a TradeService which
 sends the message and returns back a *CompletableFuture*. Once the message is delivered -Successfully
 or not- we will know it. If it is successful, we return the delivered Trade else an error.

 The offline way where the sender/caller does not expect a confirmation, it is like the fire-and-forget.
 However, there is some logic running in the background that ensures that messages get delivered. And
 when they cannot be delivered, it notifies it somehow. Either by logging it, or sending it to an external service (http, jdbc, etc) or maybe sending it to another RabbitMQ cluster. The difference
 lies in that the sender does not need to know when or whether it was sent.

 [TradeRequesterController.execute]() method is an example. It calls a TradeService which
 sends the message and it immediately returns. At this point, we do not know whether the message
 was delivered or not. Under the covers, the `DefaultTradeService` awaits for the confirmation
 in the background, and retries if it fails. At the moment, after 3 failed attempts, it gives up
 and does nothing but logging it.

 By the way, regardless which way we use, the `DefaultTradeService` will retry failed messages.
 However, with the interactive way, the caller knows when it was delivered and can do something
 differently when it was not. It could even abort the entire transaction. Whereas with the
 offline way, we cannot.


Regardless which way we want, we must use the following core RabbitMQ features, which they will
tell us when a message was delivered and when not. By themselves will not prevent message loss.
1. Publish messages using *RabbitMQ Publisher Confirms*. A message is said to be delivered only
when we receive a confirmation for it. Without this mechanism, we are doing *fire-and-forget*.
2. Retry failed attempt to publish a message.
3. Retry *Negative Publisher Confirm* and/or *Returned* messages
4. Publish messages as *persistent* otherwise the broker may loose them when the queue is
offline (this is when the queue's hosting node goes down).

In terms of Spring Cloud Stream, this is what we need to do:
1. Declare all destination queues, a.k.a, *consumer groups*, using a new property called `requiredGroups`. A message may need to be delivered to more than one application. Hence, the producer has to be told which those *consumer groups* are.
> Note 1: If we cannot lose messages the destination queues must be durable, hence we need to use
*consumer group* feature.   
> Note 2: We are tightly coupling the producer and the consumer when we ask the producer to declare the destination queues. It is not bad nor good, it depends on your case.

2. Configure `producer.errorChannelEnabled: true` so that *send failures* are sent to an error channel for the destination. The destination's error channel is called <destinationName>.errors e.g. `destinationName.errors`
3. Configure the RabbitMQ binder so that we receive confirmations of successfully sent Trades (`producer.confirmAckChannel`). We need to specify the name of the channel. Unsuccessful confirmations are sent to the *error channel*.
4. Configure RabbitMQ's binder (`application-cluster.yml`) to use publisher confirms and publisher returns.



## Testing Applications

Out of the 5 applications we have seen, some of them gracefully handle some failures but
not others. And only two applications gracefully handle all kind of failures, or at least,
the failures we are going to test in this workshop.

### Failure scenarios

The type of failures we are going test are:

1. Resiliency related failures:  
  a. RabbitMQ is not available when application starts  
  b. Restart/Shutdown a cluster node the application is connected to  
	c. Restart/Shutdown a cluster node hosting the consumer's queue  
  d. Rolling restart of cluster nodes   
  e. Kill/drop connection -consumer and/or producer  
  f. Pause nodes  
  g. Unresponsive connections  
2. Guarantee of delivery related failures:  
  a. Consumer fails to process a message  
  b. Connection drops while processing a message  
  c. Consumer receives a *Poison message*  
	d. Consumer gives up after failing to process a message (same as c.)  
  e. Producer fails to send a message (due to connection/channel errors)  
  f. Broker nacks a message (i.e. sent message does not get delivered)  
  g. Broker returns a message (i.e. sent message does not get delivered)  
	h. Queue's hosting node down while sending messages to it (same as g.)
  i. Broker blocks producers  

### Resiliency Matrix


|      |  Transient consumer  | Durable consumer  | HA Durable consumer  | Reliable consumer  | Fire-and-forget producer  | Guarantee Delivery producer  |
|------|:-----:|:----:|:----:|:----:|:----:|:----:|
| [`1.a`](#1a)|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|   
|[`1.b`](#1b)|:white_check_mark:|    |    |    |    |    |   
|[`1.c`](#1c)|:white_check_mark:|    |    |    |    |    |   
|[`1.d`](#1d)|:white_check_mark:|    |    |    |    |    |   
|[`1.e`](#1e)|:white_check_mark:|    |     |    |    |    |   
|[`1.f`](#1f)|:white_check_mark:|    |     |    |    |    |   
|[`1.g`](#1g)|     |    |     |    |    |    |   
|[`2.a`](#2a)|:white_check_mark:|    |    |    |    |    |   
|[`2.b`](#2b)|:x:|    |    |    |    |    |   
|[`2.c`](#2c)|     |    |    |    |    |    |   
|[`2.d`](#2d)|     |    |    |    |    |     |   
|[`2.e`](#2e)|     |    |    |    |    |    |   
|[`2.f`](#2f)|     |    |     |    |    |    |   
|[`2.g`](#2g)|     |    |     |    |    |    |   
|[`2.h`](#2h)|     |    |     |    |    |    |   
|[`2.i`](#2i)|     |    |     |    |    |    |   

:white_check_mark: Application is resilient to the failure
:x: Application is not resilient to the failure

### How to deploy RabbitMQ

By default, all the sample applications are configured to connect to a 3-node cluster.
Under `src/main/resources` we can find a `application-cluster.yml` file with
RabbitMQ's binder configuration that looks like this:
```yaml
spring:
  cloud:
    stream:
      binders:
        local_rabbit:
          type: rabbit
          defaultCandidate: true
          environment:
            spring:
              rabbitmq:
                addresses: localhost:5673,localhost:5674,localhost:5675
                username: guest
                password: guest
                virtual-host: /

```
And every application is configured with the `cluster` profile under their `application.yml`,
similar to this configuration:
```yaml
spring:
  application:
    name: transient-consumer
  profiles:
    include:
      - management
      - cluster
```

To launch the corresponding 3-node cluster, we run the script:
```bash
docker/deploy-rabbit-cluster
```

To launch the application against a single standalone server, edit `application.yml`
and remove `cluster` as one of the included Spring profiles. And to deploy a standalone server run:
```bash
docker/deploy-rabbit
```
> It will deploy a standalone server on port 5672


### <a name="1a"></a> Verify resiliency - 1.a RabbitMQ is not available when application starts

**TODO** provide details with regards retry max attempts and/or frequency if there are any
`recoveryInterval` is a property of consumer rabbitmq binder.

#### :white_check_mark: All applications are resilient to this failure

1. Stop RabbitMQ cluster
  ```bash
  ../docker/destroy-rabbit-cluster
  ```
2. Launch application with both roles, producer and consumer
  ```bash
  SPRING_PROFILES_ACTIVE=cluster ./run.sh --scheduledTradeRequester=true --tradeLogger=true
  ```
3. Check for fail attempts to connect in the logs
4. Start RabbitMQ cluster
  ```bash
  ../docker/deploy-rabbit-cluster
  ```

### <a name="1b"></a> Verify resiliency - 1.b Restart a cluster node the application is connected to

Pick the node where application is connected and the queue declared, say it is `rmq0`

1. Restart `rmq0` node:
  ```bash
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

### <a name="1c"></a> Verify resiliency - 1.c Restart a cluster node hosting the consumer's queue

### <a name="1d"></a> Verify resiliency - 1.d Rolling restart of cluster nodes

1. Rolling restart:
  ```bash
  ./rolling-restart
  ```
2. Wait until the script terminate to check how producer and consumer is still working
(i.e. sending and receiving)

### <a name="1e"></a><a name="1ep"></a> Verify resiliency - 1.e Kill producer connection (repeatedly)

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
  ```bash
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

### <a name="1ec"></a> Verify resiliency - 1.e Kill consumer connection (repeatedly)

1. Launch consumer with a processingTime of 5 seconds
  ```bash
  ./run.sh --tradeLogger=true --processingTime=5s --server.port=8082
  ```
2. Launch producer (which uses `tradeRateMs:1000` , i.e. a trade per second)
  ```bash
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

### <a name="1e"></a> Verify resiliency - 1.e Pause nodes

We are going to pause a node, which is similar to what happen when a network partition occurs
and the node is on the minority and we are using *pause_minority* cluster partition handling.

We are going to shutdown all nodes (`rmq2`, `rmq3`) except one (`rmq0`) where our applications
are connected to. This will automatically pause the last standing node because it is in minority.

1. Launch both roles together
```bash
./run.sh --tradeLogger=true --scheduledTradeRequester=true --processingTime=5s
```
2. Wait till we have produced a message backlog
3. Stop `rmq2`, `rmq3`
```bash
docker-compose -f ../docker/docker-compose.yml  stop rmq1 rmq2
```
4. Notice connection errors in the application logs. Also we have lost connection to the
management UI on `rmq0`.
5. Application keeps trying to publish but it fails
6. Start `rmq2`, `rmq3`
7. Notice application recovers and keeps publishing. The consumer has lost a few messages though.

### <a name="1f"></a> Verify resiliency - 1.f Unresponsive connections

We are going to simulate buggy or unresponsive connections.

**Get the environment ready**

1. Launch ToxiProxy
```bash
docker/deploy-toxiproxy
```
2. Get a list of proxies currently installed
```bash
$ docker/toxiproxy-cli list
Name			Listen		Upstream		Enabled		Toxics
======================================================================================
no proxies
```
3. Create an AMQP proxy to simulate buggy connections. We are going to proxy the first node in the cluster, `rmq0`.
```bash
docker/toxiproxy-cli create rabbit --listen 0.0.0.0:25673 -upstream rmq0:5672
```

If we list the proxies again, we should see:
```bash
../docker/toxiproxy-cli list
Name			Listen		Upstream		Enabled		Toxics
======================================================================================
rabbit			[::]:25673	rmq0:5672		enabled		None

Hint: inspect toxics with `toxiproxy-cli inspect <proxyName>`
```

4. Configure our application to connect via `localhost:25673` and launch it.
```
SPRING_PROFILES_ACTIVE=toxi ./run.sh
```
We configured the `proxi` profile here: [src/main/resources/application-toxi.yml](). It
just configure a single amqp address `localhost:25673`.

**Simulate connection drop by disabling the proxy**

1. Disable the proxy
```bash
./toxiproxy-cli toggle rabbit
Proxy rabbit is now disabled
```
The application detects the connection dropped:
```
o.s.a.r.c.CachingConnectionFactory       Channel shutdown: connection error
o.s.a.r.c.CachingConnectionFactory       Channel shutdown: connection error
```
2. Request a trade
```bash
./request-trade
```
It will fail though:
```
c.p.r.DefaultTradeService                Sending trade 2 with correlation 1601019379310 . Attempt #1
o.s.a.r.c.CachingConnectionFactory       Attempting to connect to: [localhost:25673]
o.a.c.c.C.[.[.[.[dispatcherServlet]      Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed; nested exception is org.springframework.messaging.MessageHandlingException: error occurred in message handler [org.springframework.integration.amqp.outbound.AmqpOutboundEndpoint@4578370e]; nested exception is org.springframework.amqp.AmqpIOException: java.io.IOException, failedMessage=GenericMessage [payload=byte[73], headers={resend=true, correlationId=1601019379310, id=da831fec-ea0c-a00b-cc7f-434e3a406a15, contentType=application/json, tradeId=2, account=23, timestamp=1601019379310}]] with root cause
```
3. Enable the proxy
```bash
./toxiproxy-cli toggle rabbit
Proxy rabbit is now enabled
```
4. Request a trade should work this time
```bash
./request-trade
```

**Simulate unresponsive connection**

**TODO**



### <a name="2a"></a> Verify Guarantee of delivery - 2.a Consumer fail to process a message

All consumer types uses client acknowledgment therefore they will only ack a message
after it has successfully processed it.  

#### :white_check_mark: All consumer types will never lose the message

1. Launch the producer.
```
cd reliable-producer
./run.sh
```
2. Launch the consumer. It will fail to process tradeId `3` two times. However, SCS
 retries it 3 times.
```
cd reliable-consumer
./run.sh --chaos.tradeId=3 --chaos.maxFailTimes=2
```
3. Notice in the consumer log how it fails and the message is retried.
4. Kill the consumer right after the first failed attempted and ensure that the
message stays in the queue, i.e. it is not lost

### <a name="2b"></a> Verify Guarantee of delivery - 2.b Connection drops while processing a message

#### :x: Transient consumer looses all enqueued messages so far

This time we are launching producer and consumer on separate application/process
and we are going to perform a rolling restart.

1. Start producer
  ```bash
	cd transient-consumer
  ./run.sh --scheduledTradeRequester=true
  ```
2. Start transient consumer (on a separate terminal) with a message processing time of
5seconds to produce a backlog in the queue
  ```bash
	cd transient-consumer
  ./run.sh --tradeLogger=true --server.port=8082 --processingTime=5s
  ```
3. Stop the producer. Take note of the last Trade id sent
4. Go to the management ui and take note of the messages in the queue.
5. Go to the management ui and Kill the consumer's connection
6. Follow the consumer's log and see that it reconnects but it does not receive any messages.
It has lost them all.

#### :white_check_mark: Durable consumer does not loose the enqueued messages

1. Start producer
  ```bash
	cd transient-consumer
  ./run.sh --scheduledTradeRequester=true
  ```
2. Start durable consumer (on a separate terminal) with a message processing time of
5seconds to produce a backlog in the queue
  ```bash
	cd durable-consumer
  ./run.sh --durableTradeLogger=true --server.port=8082 --processingTime=5s
  ```
3. Stop the producer. Take note of the last Trade id sent
4. Go to the management ui and Kill the consumer's connection
5. Follow the consumer's log and see that it reconnects and it receives all messages the
producer sent.


### <a name="2c"></a> Verify delivery guarantee - 2.c Consumer receives a Poison message

#### :x: All consumers except the reliable does lose the message

After retrying a number of times, the message is rejected and the broker drops it.

#### :white_check_mark: Reliable consumer does not lose the message but it moves it to a DLQ

1. Launch the producer.
```
cd reliable-producer
./run.sh
```
2. Launch the consumer. It will fail to process tradeId `3` two times. However, SCS
 retries it 3 times.
```
cd reliable-consumer
./run.sh --chaos.tradeId=3 --chaos.maxFailTimes=2
```
3. Notice in the consumer log how it fails and the message is retried. We can try to
kill the consumer before it exhausts all the attempts to ensure that the message
stays in the queue, i.e. it is not lost.


### Verify delivery guarantee - Consumer gives up after failing to process a message

If in the previous scenario, we used `--chaos.maxFailTimes=3` or greater than 3, the message
would be rejected and dropped/lost because we did not configure the queue with a dead-letter-queue.

To run the `reliable-consumer` with a dlq we are going to run it like this :
```
cd reliable-consumer
SPRING_PROFILES_ACTIVE=dlq ./run.sh --chaos.tradeId=3 --chaos.maxFailTimes=3
```


### Verify Guarantee of delivery - Connection drops while sending a message

#### :x: Fire-and-forget looses the message

- When producer fails to send (i.e. send operation throws an exception) a message,
the producer is pretty basic and it does not retry it.

#### :white_check_mark: Reliable producer retries the failed operation

### Verify Guarantee of delivery - RabbitMQ fails to accept a sent message

#### :x: Fire-and-forget looses a message if RabbitMQ fails to accept it
- Producer does not use publisher confirmation. If RabbitMQ fail to deliver a message
to a queue due to an internal issue or simply because the RabbitMQ rejects it, the
producer is not aware of it.

#### :white_check_mark: Reliable producer knows when RabbitMQ fails to accept a message

1. Launch the producer.
```bash
cd reliable-producer
./run.sh
```
2. Request a trade (offline way)
```bash
./request-trade
```
3. Check that it sent a message go to the `trades.trade-logger` queue and also new logging statements that informs the message was successfully sent.
```
c.p.r.DefaultTradeService                [attempts:0,sent:0] Requesting Trade{tradeId=1accountId=23asset=nullamount=0buy=falsetimestamp=0}
c.p.r.DefaultTradeService                Sending trade 1 with correlation 1600954559449 . Attempt #1
c.p.r.DefaultTradeService                Sent trade 1
c.p.r.DefaultTradeService                Received publish confirm w/id 1600954559449 => Trade{tradeId=1accountId=23asset=nullamount=0buy=falsetimestamp=0}
c.p.r.DefaultTradeService                Removing 1 completed trades
```
4. Put a max-length limit on the queue by invoking the following script that puts a policy.
```
PORT=15674 ./set_limit_on_queue trade-logger 3
```
> PORT=15674 allows us to target the first node in the cluster otherwise it would use 15672

5. Request a trade (offline way)
```
./request-trade
```

6. Notice that it fails and it retries 3 times. See also that our http request succeeded.
```
```

7. Request a trade (interactive way)
```
./request-trade-async
```

8. Notice that it fails and it retries 3 times. See also that our http request failed.
```
```

9. Remove the max-length limit and we see the producer successfully sends pending trades and continues
with newer ones.
```
PORT=15673 ./unset_limit_on_queue
```

### Verify Guarantee of delivery - RabbitMQ cannot route a message

#### :x: Fire-and-forget looses a message if RabbitMQ cannot route it

- Producer does not use mandatory flag hence if there are no queues bound to the exchange
associated to the outbound channel, the message is lost. The exchange is not configured with
an alternate exchange either.

#### :white_check_mark: Reliable producer knows when RabbitMQ cannot route a message

1. Launch the producer.
```bash
cd reliable-producer
./run.sh
```
2. Request a trade (offline way)
```bash
./request-trade
```
3. Check that it sent a message go to the `trades.trade-logger` queue and also new logging statements that informs the message was successfully sent.
```
c.p.r.DefaultTradeService                [attempts:0,sent:0] Requesting Trade{tradeId=1accountId=23asset=nullamount=0buy=falsetimestamp=0}
c.p.r.DefaultTradeService                Sending trade 1 with correlation 1600954559449 . Attempt #1
c.p.r.DefaultTradeService                Sent trade 1
c.p.r.DefaultTradeService                Received publish confirm w/id 1600954559449 => Trade{tradeId=1accountId=23asset=nullamount=0buy=falsetimestamp=0}
c.p.r.DefaultTradeService                Removing 1 completed trades
```

4. Remove the binding of `trades.trade-logger` queue so that messages do not get to any queue.

5. Request a trade (interactive way)
```
./request-trade-async
```

6. Notice that it fails and it retries 3 times. See also that our http request failed.
```
```


### Verify Guarantee of delivery - Queue's hosting node down while sending messages to it

Our consumer will not be able to consume while the queue's hosting node is down. Furthermore,
if the producer does not use mandatory flag and/or alternate-exchange, those messages are lost too.

1. Launch durable consumer
```bash
./run.sh --durableTradeLogger=true
```
2. Stop the hosting node. Most likely the queue will be on the first node, `rmq0`.
```bash
docker-compose -f ../docker/docker-compose.yml stop rmq0
```
3. We will notice the consumer fails to declare the queue but it keeps indefinitely trying.
```
2020-09-16 16:56:17.050 o.s.a.r.l.BlockingQueueConsumer          Failed to declare queue: trades.trade-logger
2020-09-16 16:56:17.051 o.s.a.r.l.BlockingQueueConsumer          Queue declaration failed; retries left=1

Caused by: com.rabbitmq.client.ShutdownSignalException: channel error; protocol method: #method<channel.close>(reply-code=404, reply-text=NOT_FOUND - home node 'rabbit@rmq0' of durable queue 'trades.trade-logger' in vhost '/' is down or inaccessible, class-id=50, method-id=10)

```
4. Start the hosting node.
```bash
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

### Verify delivery guarantee on the producer - Ensure the consumer groups' queues exists

1. Destroy the cluster and recreate it again so that we start without any queues
```bash
./destroy-rabbit-cluster
./deploy-rabbit-cluster
```
2. Launch the producer
```bash
./run.sh --scheduledTradeRequester=true
```
3. Check the queue `trades.trade-logger` exists and it is getting messages even though
the consumer has not started yet.

However, our producer will not guarantee delivery when the queue's hosting node is down.
These are the two scenarios we can encounter:

- The producer starts up and the queue's hosting node is down. In this scenario,
the producer will attempt to declare it and it will fail. It does not crash though.
Any attempt to send a message will succeed but the message will go nowhere, it will be lost.
- The producer starts up and successfully declares the queue. However, later on,
the queue's hosting node goes down. The messages will go nowhere, they will be lost.

Conclusion: Adding `requiredGroups` setting in the producer, help us in reducing the
amount of message loss but it does not prevent it entirely. It is convenient because we
can start applications, producer or consumer, in any order. However, we are coupling the producer with the consumer. Also, should we added more consumer groups, we would have to reconfigure our producer application.

### Verify delivery guarantee on the producer - Ensure messages are successfully sent




### <a name="2g"></a> Verify guarantee of delivery - 2.g Block producers

We are going to force RabbitMQ to trigger a memory alarm by setting the high water mark to 0.
This should only impact the producer connections and let consumer connections carry on.

1. Launch both roles (`tradeLogger` and `scheduledTradeRequester`) together in the same application, but the a slower consumer so that we create a queue backlog
```bash
./run.sh --tradeLogger=true --scheduledTradeRequester=true --processingTime=5s
```
2. Wait a couple of seconds until we produce a backlog
3. Set high water mark to zero
```bash
docker-compose -f ../docker/docker-compose.yml exec rmq0 rabbitmqctl set_vm_memory_high_watermark 0
```
4. Watch the queue depth goes to zero, i.e. the consumer is able to consume.
5. Watch messages stop coming to RabbitMQ. However, they are piling up in the tcp buffers.
When we restore the high water mark, we will see all those messages sent to RabbitMQ.
```bash
docker-compose -f ../docker/docker-compose.yml  exec rmq0 rabbitmqctl set_vm_memory_high_watermark 1.0
```

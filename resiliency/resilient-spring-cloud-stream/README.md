
# Spring Cloud Stream Resiliency Workshop

The goal of this workshop is to provide guidance to developers on how to write Spring Cloud Stream applications which are resilient to failures and guarantee message delivery, or put it another words, that it does not loose messages.

## What you will learn

This is a self-guided workshop on which you will learn:
- levels of resiliency
- what patterns/techniques/configurations to use to achieve certain level of resiliency
- how to test the resiliency's levels

## Audience

This workshop is intended for developers who are already using Spring Cloud Stream or
planning to use it. This is not a workshop to learn Spring Cloud Stream. Therefore, it requires
some knowledge of Spring Cloud Stream and Spring Boot to follow it.

## Prerequisites

To follow this workshop you need:
- Java 1.8
- Maven 3.6.2 or more recent
- Docker

## How to follow the workshop

First of all, we recommend checking out [Getting started](#getting-started) section so that you get all the content of this workshop which consists of various applications and scripts to launch 3-node RabbitMQ cluster using Docker. You will also build those applications locally so that you can run them later on.

Then you can choose two ways to follow this workshop.

**For those who are fairly familiar with Spring Cloud Stream** and only want to know
how to handle certain failure scenario, go straight to the [resiliency matrix](#resiliency-matrix).
From this matrix you will quickly see which failure is handled by which application.
Once you have identified the failure scenario, you can jump straight (each failure scenario is a hyperlink) to the section where we test it.

**For those who want to learn what reliability options are available with SCS** and do not have
any specific failure in mind we recommend going thru the [Application types](#application-types) section where you will learn why there are various types of applications and what levels of resiliency you can expect from each type.
By the end of this section, you have identified the type of application you need and then you can move onto the last section [Testing Applications](#testing-applications).


**Table of content**
<!-- TOC depthFrom:2 depthTo:3 withLinks:1 updateOnSave:1 orderedList:0 -->

- [What you will learn](#what-you-will-learn)
- [Audience](#audience)
- [Prerequisites](#prerequisites)
- [How to follow the workshop](#how-to-follow-the-workshop)
- [Getting started](#getting-started)
	- [Get the entire workshop](#get-the-entire-workshop)
	- [Building the code](#building-the-code)
	- [How projects are structured](#how-projects-are-structured)
	- [How to deploy RabbitMQ](#how-to-deploy-rabbitmq)
- [Application types](#application-types)
	- [Transient consumer](#transient-consumer)
	- [Durable consumer](#durable-consumer)
	- [Highly available durable consumer](#highly-available-durable-consumer)
	- [Reliable consumer](#reliable-consumer)
	- [Fire-and-forget producer](#fire-and-forget-producer)
	- [Reliable producer](#reliable-producer)
- [Testing Applications](#testing-applications)
	- [Failure scenarios](#failure-scenarios)
	- [Resiliency Matrix](#resiliency-matrix)
- [Verify resiliency-1a RabbitMQ is not available when application starts](#verify-resiliency-1a-rabbitmq-is-not-available-when-application-starts)
	- [:white_check_mark: All applications are resilient to this failure](#whitecheckmark-all-applications-are-resilient-to-this-failure)
- [Verify resiliency-1b Restart a cluster node the application is connected to](#verify-resiliency-1b-restart-a-cluster-node-the-application-is-connected-to)
	- [:white_check_mark: All applications are resilient to this failure](#whitecheckmark-all-applications-are-resilient-to-this-failure)
- [Verify resiliency-1c Restart a cluster node hosting the consumer's queue](#verify-resiliency-1c-restart-a-cluster-node-hosting-the-consumers-queue)
	- [:x: Durable consumers are resilient to this failure but will suffer downtime](#x-durable-consumers-are-resilient-to-this-failure-but-will-suffer-downtime)
	- [:white_check_mark: Transient consumers, HA durable consumers and producers in general are resilient to this failure](#whitecheckmark-transient-consumers-ha-durable-consumers-and-producers-in-general-are-resilient-to-this-failure)
- [Verify resiliency-1d Rolling restart of cluster nodes](#verify-resiliency-1d-rolling-restart-of-cluster-nodes)
	- [:white_check_mark: All applications are resilient to this failure](#whitecheckmark-all-applications-are-resilient-to-this-failure)
- [Verify resiliency-1e Kill producer connection](#verify-resiliency-1e-kill-producer-connection)
	- [:white_check_mark: In general all producer applications are resilient to this failure](#whitecheckmark-in-general-all-producer-applications-are-resilient-to-this-failure)
- [Verify resiliency-1e Kill consumer connection (repeatedly)](#verify-resiliency-1e-kill-consumer-connection-repeatedly)
	- [:white_check_mark: All consumers are resilient to this failure](#whitecheckmark-all-consumers-are-resilient-to-this-failure)
- [Verify resiliency-1f Pause nodes](#verify-resiliency-1f-pause-nodes)
- [Verify resiliency-1g Unresponsive connections](#verify-resiliency-1g-unresponsive-connections)
	- [:white_check_mark: Unresponsive connections are eventually detected and closed](#whitecheckmark-unresponsive-connections-are-eventually-detected-and-closed)
	- [:white_check_mark: Unresponsive connections should not make producers unresponsive](#whitecheckmark-unresponsive-connections-should-not-make-producers-unresponsive)
- [Verify Guarantee of delivery-2a Consumer fail to process a message](#verify-guarantee-of-delivery-2a-consumer-fail-to-process-a-message)
	- [:white_check_mark: All consumer types should retry the message before giving up](#whitecheckmark-all-consumer-types-should-retry-the-message-before-giving-up)
- [Verify Guarantee of delivery - 2.b Consumer terminates while processing a message](#verify-guarantee-of-delivery-2b-consumer-terminates-while-processing-a-message)
	- [:x: Transient consumers will lose the message and all the remaining enqueued messages](#x-transient-consumers-will-lose-the-message-and-all-the-remaining-enqueued-messages)
	- [:white_check_mark: Only durable consumers will never lose the message](#whitecheckmark-only-durable-consumers-will-never-lose-the-message)
- [Verify Guarantee of delivery-2c Connection drops while processing a message](#verify-guarantee-of-delivery-2c-connection-drops-while-processing-a-message)
	- [:x: Transient consumer looses all enqueued messages so far](#x-transient-consumer-looses-all-enqueued-messages-so-far)
	- [:white_check_mark: Durable consumer does not loose the enqueued messages](#whitecheckmark-durable-consumer-does-not-loose-the-enqueued-messages)
- [Verify delivery guarantee-2d Consumer receives a Poison message](#verify-delivery-guarantee-2d-consumer-receives-a-poison-message)
	- [:x: All consumers without a DLQ lose the message](#x-all-consumers-without-a-dlq-lose-the-message)
	- [:white_check_mark: Consumers with queues configured with DLQ do not lose the message](#whitecheckmark-consumers-with-queues-configured-with-dlq-do-not-lose-the-message)
	- [:white_check_mark: Consumers should not retry poison message neither lose it](#whitecheckmark-consumers-should-not-retry-poison-message-neither-lose-it)
- [Verify delivery guarantee-2e Consumer gives up after failing to process a message](#verify-delivery-guarantee-2e-consumer-gives-up-after-failing-to-process-a-message)
	- [:white_check_mark: Transient failures should be delayed and retried without blocking newer messages](#whitecheckmark-transient-failures-should-be-delayed-and-retried-without-blocking-newer-messages)
- [Verify Guarantee of delivery-2f Connection drops while sending a message](#verify-guarantee-of-delivery-2f-connection-drops-while-sending-a-message)
	- [:x: Fire-and-forget is not resilient and fails to send it](#x-fire-and-forget-is-not-resilient-and-fails-to-send-it)
	- [:white_check_mark: Fire-and-forget is now resilient and retries when it fails](#whitecheckmark-fire-and-forget-is-now-resilient-and-retries-when-it-fails)
- [Verify Guarantee of delivery-2g RabbitMQ fails to deliver a message to a queue](#verify-guarantee-of-delivery-2g-rabbitmq-fails-to-deliver-a-message-to-a-queue)
	- [:x: Fire-and-forget looses messages if RabbitMQ fails to accept it](#x-fire-and-forget-looses-messages-if-rabbitmq-fails-to-accept-it)
	- [:white_check_mark: Reliable producer knows when RabbitMQ fails to accept a message](#whitecheckmark-reliable-producer-knows-when-rabbitmq-fails-to-accept-a-message)
- [Verify Guarantee of delivery-2h RabbitMQ cannot route a message](#verify-guarantee-of-delivery-2h-rabbitmq-cannot-route-a-message)
	- [:x: Fire-and-forget looses a message if RabbitMQ cannot route it](#x-fire-and-forget-looses-a-message-if-rabbitmq-cannot-route-it)
	- [:white_check_mark: Reliable producer knows when RabbitMQ cannot route a message](#whitecheckmark-reliable-producer-knows-when-rabbitmq-cannot-route-a-message)
	- [:white_check_mark: Reliable producer ensures the consumer groups' queues exists](#whitecheckmark-reliable-producer-ensures-the-consumer-groups-queues-exists)
- [Verify Guarantee of delivery-2i Queue's hosting node down while sending messages to it](#verify-guarantee-of-delivery-2i-queues-hosting-node-down-while-sending-messages-to-it)
	- [:x: Transient consumers lose all enqueued messages](#x-transient-consumers-lose-all-enqueued-messages)
	- [:question: Durable consumers do not lose any enqueued messages but may lose newer ones](#question-durable-consumers-do-not-lose-any-enqueued-messages-but-may-lose-newer-ones)
	- [:white_check_mark: Highly available consumer will not lose messages](#whitecheckmark-highly-available-consumer-will-not-lose-messages)
- [Verify guarantee of delivery-2j Block producers](#verify-guarantee-of-delivery-2j-block-producers)

<!-- /TOC -->

## Getting started

### Get the entire workshop

```
git clone https://github.com/rabbitmq/workloads
cd workloads/resiliency/resilient-spring-cloud-stream
```

Any sample script, for instance to deploy a rabbitmq cluster,
 assumes we are on the `workloads/resiliency/resilient-spring-cloud-stream` folder.

### Building the code

It is highly recommended to build all projects together by running the following
command from the root of this folder, `resilient-spring-cloud-stream`:
```bash
mvn
```

### How projects are structured

There is a root [pom.xml](pom.xml) that builds all the application types.
All application types such as `fire-and-forget-producer` or `transient-consumer` inherits (maven term)
from a common [parent](parent) project. The `parent` project centralizes dependencies and plugin configuration
required by the children projects.

Common code shared by all application types resides in the [common](common) project. Therefore,
all applications types has `common` as a dependency too.

### How to deploy RabbitMQ

By default, all applications are configured to connect to a 3-node cluster.
Under `src/main/resources` of each application project there is an `application-cluster.yml` file with
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
And every application is configured with the `cluster` profile in their `application.yml`:
```yaml
spring:
  application:
    name: transient-consumer
  profiles:
    include:
      - management
      - cluster
```

To launch the 3-node cluster, we run the script:
```bash
docker/deploy-rabbit-cluster
```

## Application types

Not all applications requires the same level of resiliency, or message delivery guarantee or
tolerance to downtime. For this reason, we are going to create different kinds of consumer and producer applications, where each type gives us certain level of resiliency and/or guarantee of delivery.

### Transient consumer

A **transient consumer** only receives messages which were sent after the consumer has connected to the broker and declared its *non-durable* queue bound to the corresponding *exchange*. If the consumer disconnects from the broker, it looses the queue and all its messages.

This type of consumer creates a queue named `<channel_destination_name>.anonymous.<unique_id>` e.g. `trades.anonymous.3pxLDAVsRBWQE_DmgVHaYg` with this attributes:
  - *non-durable*
  - *exclusive*
  - *auto-delete*   

We can find an example of this type of consumer in the project [transient-consumer](transient-consumer).

It consists of an Spring `@Service`(s) called `TradeLogger`. Here is a snippet of code with the relevant parts.

```Java
@Service
@EnableBinding(TradeLogger.MessagingBridge.class)
@ConditionalOnProperty(name="tradeLogger", matchIfMissing = true)
public class TradeLogger {

  interface MessagingBridge {

        String INPUT = "trade-logger-input";

        @Input(INPUT)
        SubscribableChannel tradeRequests();

    }
    ...

    @StreamListener(MessagingBridge.INPUT)
    public void execute(@Header("account") long account,
                    @Header("tradeId") long tradeId,
                    @Payload Trade trade) {

    }
}
```

And the corresponding SCS configuration:

```yaml
spring:
  cloud:
    stream:
      bindings: # spring cloud stream binding configuration
        trade-logger-input:
          destination: trades

```

This application automatically declares the AMQP resources such as exchanges, queues and bindings.

#### What is this consumer useful for

This type of consumer is useful in use cases like these ones:

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
the [Transient consumer](#transient-consumer), when the **durable consumer** disconnects from the broker, the queue remains in the broker receiving more messages. Once the **durable consumer** reconnects to the broker, it gets all messages, i.e. messages left in the queue when it disconnected, and messages sent meanwhile.

We can find an example of this type of consumer in the project [durable-consumer](durable-consumer).
It consists of a Spring `@Service` durable consumer called `DurableTradeLogger`. This service uses a *Consumer Group* called after its name `trade-logger` which creates a durable queue called
`queue.trade-logger`.

We switched to durable subscriptions so that we did not lose messages. However, we need to
ensure the producer sends messages with `deliveryMode: PERSISTENT` which is the default
value in SCS. If the producer did not send messages as persistent, they will be lost
if the queue's hosting node goes down.

#### What about data loss

The consumer uses *Client Auto Acknowledgement* therefore it will not lose
 messages due to failures that may occur while processing the message including losing the connection.
If the failure is persistent eventually SCS gives up and the message could be lost if we have not
configured the queue with a DLQ.

However, queued messages -i.e. messages which are already in the queue- may be lost if
the messages are not sent with the *persistent flag*. By default, Spring Cloud Stream will
send messages as persistent unless we change it. Non-persistent messages are only kept
in memory and if the queue's hosting node goes down, they will be lost.

*IMPORTANT*: We are always talking about queued messages. That is, messages which are already in a queue.
We are not addressing data loss of messages which are being sent to a queue. We will address this type of
data loss in the producer application types later on.

#### Is this consumer highly available

By default, this consumer is **not highly available**. Its uptime depends on the
uptime of queue's hosting node goes down.

Is this suitable for my case? That depends on your business case. If the consumer
can tolerate a downtime of less than an hour which is the maximum time any of nodes
can be down then this consumer is suitable. Else, we need to make it HA. Look at the [next](#highly-available-durable-consumer) type of application.

#### What about strict order processing of messages

If we need to have strict ordering of processing of messages we need to use `exclusive: true` attribute. If we have more instances, they will fail to subscribe but will retry based on the `recoveryInterval: 5000` attribute.

Another way is by configuring a queue with [singleActiveConsumer](https://github.com/spring-cloud/spring-cloud-stream-binder-rabbit#rabbitmq-consumer-properties) which is simpler. RabbitMQ will ensure
that there is only one active consumer. If there are more than one consumer, one will be active and the
others will be passive.

### Highly available durable consumer

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

By default, Spring Cloud Stream uses client acknowledgement (`acknowledgeMode: AUTO`).
This means that if our listener threw an exception while processing a message, it would not be lost. Instead, the message is retried. This retry mechanism is enabled by default on SCS as we will see in the [next](#dealing-with-processing-failures) section.

In order to test the consumer's reliability, we need to simulate failures. For this reason, we have created another consumer project called [reliable-consumer](reliable-consumer).


#### Dealing with processing failures

If the listener fails to process the message and throws an exception (different to `AmqpRejectAndDontRequeueException`), SCS retries it a configurable number of times and with a delay between attempts.

However, if the listener keeps failing, SCS will eventually reject it and the message is lost if the queue has not been configured with a *dead-letter-queue* (see [next](#Dealing-with-processing-failures-without-losing-messages) section).

> We can change this behaviour with `requeueRejected: true`. But be careful changing this value because it could produce a storm of poisonous messages unless the application raises an `AmqpRejectAndDontRequeueException`.


> We should not retry exceptions related to parsing/deserializing messages and/or
business exceptions. Because it will always fail.
> However, we should retry infrastructure related exceptions such as connectivity issues to downstream
services over http, jdbc, etc.

These are the consumer bindings' [settings](https://cloud.spring.io/spring-cloud-static/spring-cloud-stream/current/reference/html/spring-cloud-stream.html#_consumer_properties) that control the retries:
  - `maxAttempts: 3`
  - `backOffInitialInterval: 1000`
  - `backOffMaxInterval: 1000`
  - `defaultRetryable: true`
  - `retryableExceptions`

[application.yml](reliable-consumer/src/main/resources/application.yml) configures `maxAttempts` for the single input channel, `durable-trade-logger-input`.


#### Dealing with processing failures without losing messages

Once the consumer has exceeded the maximum of number of retries, we want to move the message to an
error queue so that we do not lose it.

SCS RabbitMQ binder allows us to configure a queue with a *dead-letter-queue*. All we need to do is
add a `autoBindDlq: true` to the consumer channel. Check out [application-dlq](reliable-consumer/src/main/resources/application-dlq.yml).

:bangbang: **VERY IMPORTANT**: Once we configure our queue with DLQ or any other features via one of the
SCS settings, we cannot change it otherwise our application fails to declare it. Moreover, if we
also configure the producer -via `requiredGroups`- to declare the queue, we will see failures
happening in both, consumer and producer. Those failures are not fatal but annoying.


### Fire-and-forget producer

This type of producer does not guarantee that the message is delivered. Instead, it sends the message and forgets about it.

The following circumstances produce message loss and this producer will
never know it because it does not expect any confirmation that a message was delivered:
- connection drops while sending the message
- the broker rejects the message due to an internal error
- the broker rejects the message due to *max-length* policy
- the broker could not find a destination queue for it

We can find an example of this type of producer in the project [fire-and-forget-producer](fire-and-forget-producer). It has 2 Spring `@Service`(s):
- `ScheduledTradeRequester` It is a Scheduled task that sends messages every 1sec by default
- `TradeRequesterController` It is a Rest controller that exposes an endpoint that we use to send a message

#### When is this type of producer useful

This type of producer is useful in these use cases:

- When data is not massively critical and consumers can tolerate message loss.
- Especially interesting when the consumers are of type transient
- When consumers use some eviction strategy in their queues, either max-length or ttl.


### Reliable producer

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

| 1 | Resiliency | 2 | Guarantee of delivery |
|:------:|-----|:----:|----|
|a|RabbitMQ is not available when application starts|a|Consumer fails to process a message|
|b|Restart/Shutdown a cluster node the application is connected to|b|Consumer terminates while processing a message|
|c|Restart/Shutdown a cluster node hosting the consumer's queue|c|Connection drops while processing a message|
|d|Rolling restart of cluster nodes|d|Consumer receives a *Poison message*|
|e|Producer looses connection|e|Consumer gives up after failing to process a message (same as c.)|
|f|Consumer looses connection|f|Producer fails to send a message (due to connection/channel errors)|
|g|Pause nodes|g|Broker nacks a message (i.e. sent message does not get delivered)|
|h|Unresponsive connections |h|Broker returns a message (i.e. sent message does not get delivered)|
| | |i|Queue's hosting node down while sending messages to it (same as g.)|
| | |j|Broker blocks producers  |


### Resiliency Matrix

|      |  Transient consumer  | Durable consumer  | HA Durable consumer  | Reliable consumer  | Fire-and-forget producer  | Reliable producer  |
|------|:-----:|:----:|:----:|:----:|:----:|:----:|
|[`1.a`](#user-content-1a)|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|   
|[`1.b`](#user-content-1b)|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|
|[`1.c`](#user-content-1c)|:white_check_mark:|:white_check_mark::question:|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|   
|[`1.d`](#user-content-1d)|:white_check_mark:|:white_check_mark::question:|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|   
|[`1.e`](#user-content-1e)|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|   
|[`1.f`](#user-content-1f)|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|   
|[`1.g`](#user-content-1g)|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|
|[`1.h`](#user-content-1h)|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|
|[`2.a`](#user-content-2a)|:white_check_mark:|:white_check_mark:|:white_check_mark:|:white_check_mark:|:heavy_minus_sign:|:heavy_minus_sign:|   
|[`2.b`](#user-content-2b)|:x:|:white_check_mark:|:white_check_mark:|:white_check_mark:|:heavy_minus_sign:|:heavy_minus_sign:|
|[`2.c`](#user-content-2c)|:x:|:white_check_mark:|:white_check_mark:|:white_check_mark:|:heavy_minus_sign:|:heavy_minus_sign:|
|[`2.d`](#user-content-2d)|:white_check_mark::question:|:white_check_mark::question:|:white_check_mark::question:|:white_check_mark:|:heavy_minus_sign:|:heavy_minus_sign:|   
|[`2.e`](#user-content-2e)|     |    |    |    |    |    |   
|[`2.f`](#user-content-2f)|:heavy_minus_sign:|:heavy_minus_sign:|:heavy_minus_sign:|:heavy_minus_sign:|:white_check_mark::question:|:white_check_mark:|   
|[`2.g`](#user-content-2g)|:heavy_minus_sign:|:heavy_minus_sign:|:heavy_minus_sign:|:heavy_minus_sign:|:x:|:white_check_mark:|   
|[`2.h`](#user-content-2h)|:heavy_minus_sign:|:heavy_minus_sign:|:heavy_minus_sign:|:heavy_minus_sign:|:x:|:white_check_mark:|
|[`2.i`](#user-content-2i)|:x:|:x::question:|:white_check_mark:|:white_check_mark:|:heavy_minus_sign:|:heavy_minus_sign:|   
|[`2.j`](#user-content-2j)|:heavy_minus_sign:|:heavy_minus_sign:|:heavy_minus_sign:|:heavy_minus_sign:|:x:|:white_check_mark:|   

:white_check_mark: Application is resilient to the failure
:x: Application is not resilient to the failure
:question: Application is partially resilient to the failure
:heavy_minus_sign: Application not affected to the failure

<br/>
<br/>
<br/>

<a name="1a"></a>
## Verify resiliency-1a RabbitMQ is not available when application starts

It is important that our applications are able to start even when RabbitMQ is not reachable.
This allows us to separate RabbitMQ's operations from application's deployment.

### :white_check_mark: All applications are resilient to this failure

We are demonstrating that, by default, SCS applications are resilient to failure. To do
so, we start with the simplest applications: `fire-and-forget-producer` and `transient-consumer`.

1. Stop RabbitMQ cluster
  ```bash
  docker/destroy-rabbit-cluster  
  ```
2. Launch the `fire-and-forget-producer` from one terminal
  ```bash
  fire-and-forget-producer/run.sh
  ```
3. Check the logs for connection fail attempts
4. Launch the `transient-consumer` from another terminal
  ```bash
  transient-consumer/run.sh
  ```
5. Check the logs for connection fail attempts
6. Start RabbitMQ cluster and ensure that it starts with all 3-nodes
  ```bash
  docker/deploy-rabbit-cluster
  docker exec -it rmq0 rabbitmqctl cluster_status
  ```
7. Check the logs for both apps to ensure they are connected to RabbitMQ and working as expected.


<br/>
<br/>
<br/>

<a name="1b"></a>
## Verify resiliency-1b Restart a cluster node the application is connected to

For maintenance reasons or due to a node failure, the application looses the
connection with the node it was connected to. The application should be able to reconnect
to another node.

To identify which application is connected we can use two techniques:
- **Each application has its own RabbitMQ user**. Via the management ui/api we can identify who is connected
as shown in the image below.

   The 3-node cluster is automatically [configured](docker/definitions.json) with one user for each type of application.
   In a cloud environments like Tanzu Application Service, applications get granted a unique UUID as username making this method less useful.

   ![identify connections](assets/connections.png)

- **Use the application's name to name the connection** as shown in the previous image (e.g. `fire-and-forget#2.publisher`). SCS RabbitMQ binder
implements a good practice which consists in separating producer connections from non-producer connections.
Producer connections are suffixed with `.publisher`.

  :warning: Due to an [issue](https://github.com/spring-cloud/spring-cloud-stream-binder-rabbit/issues/307) we cannot configure the connection's name via configuration as suggested [here](https://github.com/spring-cloud/spring-cloud-stream-binder-rabbit/blob/master/README.adoc#rabbitmq-binder-properties). We can find
  a [work-around](common/src/main/java/com/pivotal/resilient/ConnectionNameConfiguration.java) under the [common](common) project which configures the connection's name so that it matches the application's name.


### :white_check_mark: All applications are resilient to this failure

Once again, we choose the least resilient producer and consumer applications which yet
they are resilient to this particular failure.

1. Launch the `fire-and-forget-producer` from one terminal
  ```bash
  fire-and-forget-producer/run.sh
  ```
2. Launch the `transient-consumer` from another terminal
  ```bash
  transient-consumer/run.sh
  ```
3. Identify the node the application is connected to. We run the following
convenient script:
  ```bash
  docker/list-conn
  ```
  We should get an output similar to this:
  ```
  172.24.0.1:34078 -> 172.24.0.5:5672	fire-and-forget-producer	rabbit@rmq0
  172.24.0.1:60938 -> 172.24.0.5:5672	fire-and-forget-producer	rabbit@rmq0
  ```
4. Restart the node where either application is connected to. Let's say it is `rmq0`
  ```bash
  docker-compose -f docker/docker-compose.yml restart rmq0
  ```
5. Check in the logs how producer and consumer keeps working. We should expect a sequence of logging statements like these two:
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


:warning: **Watch out**
- It is important that either we configure the binder with a list of
AMQP addresses like we do (e.g. [application-cluster.yml](fire-and-forget-producer/src/main/resources/application-cluster.yml) ) or use an address which is load-balanced (DNS, or LB).

<br/>
<br/>
<br/>

<a name="1c"></a>
## Verify resiliency-1c Restart a cluster node hosting the consumer's queue

In the previous scenario, [1.b](#user-content-1b), the goal was to test resiliency against
connection drops. In this scenario, the goal is to test whether the application resiliency when
the affected node was hosting the application's queue. The application could be a producer sending
messages to the queue or it could be a consumer.

### :x: Durable consumers are resilient to this failure but will suffer downtime

:warning: `durable-consumer` is resilient because it does not crash however it will suffer
downtime as it will stop getting messages

1. Launch the `durable-consumer` from a terminal
  ```bash
  durable-consumer/run.sh
  ```
2. Launch the `fire-and-forget-producer` from another terminal
  ```bash
  fire-and-forget-producer/run.sh
  ```
3. Stop the node hosting the `trades.trade-logger` queue. Let's say it is `rmq0`
  ```bash
  docker-compose -f docker/docker-compose.yml stop rmq0
  ```
4. Check the `durable-consumer` logs that it is able to reconnect but it is not able to
declare the queue.
5. :warning: Notice that `fire-and-forget-producer` is not affected. However, all messages sent after the
node went down are lost.
6. Restart the node we stopped earlier
  ```bash
  docker-compose -f docker/docker-compose.yml restart rmq0
  ```
7. Check the `durable-consumer` logs that it is receiving messages but only messages sent after the
node came back.

**Key configuration settings**:  
- [RabbitMQ Consumer Binding](https://github.com/spring-cloud/spring-cloud-stream-binder-rabbit#rabbitmq-consumer-properties) - `failedDeclarationRetryInterval` The interval (in milliseconds) between attempts to consume from a queue if it is missing (default `5000`)
- [RabbitMQ Consumer Binding](https://github.com/spring-cloud/spring-cloud-stream-binder-rabbit#rabbitmq-consumer-properties) - `missingQueuesFatal` When the queue cannot be found, whether to treat the condition as fatal and stop the listener container. (default `false`)

### :white_check_mark: Transient consumers, HA durable consumers and producers in general are resilient to this failure

`transient-consumer` is resilient and does not suffer downtime because it automatically
reconnects and redeclare the queue. Messages are lost but we are focusing here on resiliency not guarantee
of delivery.

Repeat the same steps as in the previous scenario but using `transient-consumer` instead.

<br/>
<br/>
<br/>

<a name="1d"></a>
## Verify resiliency-1d Rolling restart of cluster nodes

Applications have to be able to deal with RabbitMQ Cluster upgrades. In this scenario,
we are simulating a *rolling upgrade*. If we wanted to simulate a *full-shutdown upgrade*
then all we need to do is run `docker/destroy-rabbit-cluster` script instead.

### :white_check_mark: All applications are resilient to this failure

We can choose any type of consumer and producer application. As an example, we chose
`durable-consumer` and `fire-and-forget-producer`.


1. Launch the `durable-consumer` from a terminal
  ```bash
  durable-consumer/run.sh
  ```
2. Launch the `fire-and-forget-producer` from another terminal
  ```bash
  fire-and-forget-producer/run.sh
  ```
3. Trigger rolling restart:
  ```bash
  docker/rolling-restart
  ```
4. Wait until the script terminates to check how producer and consumer are still working
(i.e. sending and receiving)


<br/>
<br/>
<br/>

<a name="1e"></a>
## Verify resiliency-1e Producer looses connection

Sometimes a single connection is dropped and not necessarily due to a node crash. This could be due to a network infrastructure failure. This can impact some parts of an application -the producers- while others -the consumers- may continue to work.

Producer applications should be able to deal with this failure especially if they occur while sending a message.

### :white_check_mark: In general all producer applications are resilient to this failure

**TODO** See if we can cause the send operation to fail due to a connection error and see what happens


1. Launch `fire-and-forget-producer`
  ```bash
  fire-and-forget-producer/run.sh
  ```
2. Kill *publisher* connection.
  ```bash
  docker/list-conn
  docker/kill-conn-grep fire-and-forget-producer
  ```
  > With the last command we are actually killing the two connections the fire-and-forget-producer has.

3. The producer should recover from it. We should get a similar logging sequence to this one:
```
2020-09-16 09:57:03.618  INFO 28370 --- [   scheduling-1] c.p.resilient.ScheduledTradeRequester    : [sent:23] Requesting Trade 24 for account 4
2020-09-16 09:57:08.471 ERROR 28370 --- [ 127.0.0.1:5673] o.s.a.r.c.CachingConnectionFactory       : Channel shutdown: connection error; protocol method: #method<connection.close>(reply-code=320, reply-text=CONNECTION_FORCED - Closed via management plugin, class-id=0, method-id=0)
2020-09-16 09:57:08.620  INFO 28370 --- [   scheduling-1] c.p.resilient.ScheduledTradeRequester    : [sent:24] Requesting Trade 25 for account 7
2020-09-16 09:57:08.621  INFO 28370 --- [   scheduling-1] o.s.a.r.c.CachingConnectionFactory       : Attempting to connect to: [localhost:5673, localhost:5674, localhost:5675]
2020-09-16 09:57:08.638  INFO 28370 --- [   scheduling-1] o.s.a.r.c.CachingConnectionFactory       : Created new connection: rabbitConnectionFactory.publisher#1554b244:2/SimpleConnection@1796c24f [delegate=amqp://guest@127.0.0.1:5673/, localPort= 53480]
```


:warning: **Watch out**
- We are not testing guarantee of delivery. Therefore, the goal is to ensure the application does not
crash when the connection drops, and not to retry the send operation. The latter is necessary if we want
to ensure guarantee of delivery, i.e. we do not want to lose the message.

<br/>
<br/>
<br/>

<a name="1f"></a>
## Verify resiliency-1f Consumer looses connection

Consumer applications should be able to deal with this failure too. In other words, they should
reconnect and resubscribe.

### :white_check_mark: All consumers are resilient to this failure

We can try any of the 3 consumer applications. But given that `durable-consumer` already showed
a weakness (see scenario [1c](#user-content-1c)), we are going to test it here.

1. Launch `durable-consumer`
  ```bash
  durable-consumer/run.sh
  ```
2. Launch `fire-and-forget-producer`
  ```bash
  fire-and-forget-producer/run.sh
  ```
3. Kill consumer connection
  ```bash
  docker/kill-conn-grep durable-consumer
  ```
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

<br/>
<br/>
<br/>

<a name="1g"></a>
## Verify resiliency-1g Pause nodes

Although it is not a failure scenario but it is an scenario that impacts applications.

RabbitMQ pauses a node when either a network partition occurs and the node is on the minority.
Or the cluster is running with fewer nodes than minimum required in case of using *pause_minority* cluster partition handling.

This scenario may trigger previous failure scenarios such as [1b]#user-content-1b), [1c]#user-content-1c).

### :white_check_mark: All applications are resilient to this failure

We are going to shutdown all nodes (`rmq2`, `rmq3`) except one (`rmq0`) where our applications
are connected to. This will automatically pause the last standing node because it is in minority.

1. Launch a producer
  ```bash
  fire-and-forget-producer/run.sh --processingTime=5s
  ```
2. Launch a consumer
  ```bash
  transient-consumer/run.sh --processingTime=5s
  ```
3. Wait till we have produced a message backlog
4. Stop `rmq2`, `rmq3`.
  ```bash
  docker-compose -f docker/docker-compose.yml  stop rmq1 rmq2
  ```
5. Notice connection errors in the application logs. Also we have lost connection to the
management UI on `rmq0`.
6. Application keeps trying to publish but it fails
7. Start `rmq2`, `rmq3`
8. Notice application recovers and keeps publishing. The consumer has lost a few messages though.

<br/>
<br/>
<br/>

<a name="1h"></a>
## Verify resiliency-1h Unresponsive connections

This failure scenario corresponds to those situations where due to faulty network infrastructure
connections are unresponsive. As far as the application is concerned, the socket is opened and write/read operations succeed. However, send operations eventually fail because RabbitMQ client expects a reply which never arrives and it times out throwing an exception.

### About ToxiProxy

[Toxiproxy](https://github.com/Shopify/toxiproxy) is a framework for simulating network conditions. It consists of 2 parts:
  - **toxiproxy** - server component that allows us to create proxies at runtime
  - **toxiproxy-cli** - client application that allows us to interact with toxiproxy to create proxies.

The diagram below illustrates how it works:

1. First we launch `toxiproxy`. See next section on how to do it
2. Then we create a proxy called `rabbit` that listens on port `25673` and forwards it to
`5673` where our real RabbitMQ node is listening. We use `toxiproxy-cli`.
3. We configure our application to use `localhost:25673` (the proxy) as RabbitMQ address.
4. When our application connects to the proxy (on `localhost:25673`), it forwards the traffic to RabbitMQ on `localhost:5673`.

With this setup in place we can introduce buggy behaviours like dropping connections and/ introduce latency.

```
          (2)
    [toxiproxy-cli]----->8474:[toxiproxy]
                              [---------]
       [application]--->25673:[rabbit   ]---->5673:[real-rabbit]
			 			(3)										(1)									(4)
```

<a name="toxiproxy-ready"></a>
### Get the environment ready

1. Launch ToxiProxy
  ```bash
  docker/deploy-toxiproxy
  ```
2. Get a list of proxies currently installed
  ```bash
  docker/toxiproxy-cli list
  Name			Listen		Upstream		Enabled		Toxics
  ======================================================================================
  no proxies
  ```
3. Create an AMQP proxy to simulate buggy connections. We are going to proxy the first node in the cluster, `rmq0`.
  ```bash
  docker/toxiproxy-cli create rabbit --listen 0.0.0.0:25673 --upstream rmq0:5672
  ```

  If we list the proxies again, we should see:
  ```bash
  docker/toxiproxy-cli list
  Name			Listen		Upstream		Enabled		Toxics
  ======================================================================================
  rabbit			[::]:25673	rmq0:5672		enabled		None

  Hint: inspect toxics with `toxiproxy-cli inspect <proxyName>`
  ```

### :white_check_mark: Unresponsive connections are eventually detected and closed

To detect unresponsive connections we use RabbitMQ [heartbeats](https://www.rabbitmq.com/heartbeats.html).

  > Heartbeat frames are sent about every heartbeat timeout / 2 seconds. This value is sometimes referred to as the heartbeat interval. After two missed heartbeats, the peer is considered to be unreachable.

Spring AMQP does not configure any heartbeat (`spring.rabbitmq.requested-heartbeat`) which means it accepts whatever timeout the RabbitMQ server has [configured](https://www.rabbitmq.com/configure.html). The setting is called `heartbeat`. Our RabbitMQ server uses the default heartbeat which is `60` seconds.

We are going to test unresponsive connections on a consumer but it also applies to producer applications.

1. Configure the proxy to stop all data from getting through. More details [here](https://github.com/Shopify/toxiproxy#timeout)
  ```bash
  docker/toxiproxy-cli toxic add --type timeout --upstream --attribute timeout=0 rabbit
  Added upstream timeout toxic 'timeout_upstream' on proxy 'rabbit'
  ```
  ```bash
  docker/toxiproxy-cli toxic add --type timeout --downstream --attribute timeout=0 rabbit
  Added downstream timeout toxic 'timeout_downstream' on proxy 'rabbit'
  ```
2. Configure consumer to connect via the proxy and launch it.
  ```
  SPRING_PROFILES_ACTIVE=toxi transient-consumer/run.sh
  ```
  We configured the `proxi` profile here: [transient-consumer/src/main/resources/application-toxi.yml](). It overrides the already configured amqp address to be just `localhost:25673`.

3. The application should detect the unresponsive connection and close it after 60 seconds.
  ```
  o.s.a.r.c.CachingConnectionFactory       Channel shutdown: connection error
  o.s.a.r.c.CachingConnectionFactory       Channel shutdown: connection error
  ```
4. Remove the toxics from the proxy
  ```bash
  docker/toxiproxy-cli toxic remove --toxicName timeout_upstream rabbit
  Removed toxic 'timeout_upstream' on proxy 'rabbit'
  ```
  ```bash
  docker/toxiproxy-cli toxic remove --toxicName timeout_downstream rabbit
  Removed toxic 'timeout_downstream' on proxy 'rabbit'
  ```

### :white_check_mark: Unresponsive connections should not make producers unresponsive

This scenario verifies that a send operation should not block forever when the connection is unresponsive.

1. Configure the proxy to stop all data from getting through.
  ```bash
  docker/toxiproxy-cli toxic add --type timeout --upstream --attribute timeout=0 rabbit
  Added upstream timeout toxic 'timeout_upstream' on proxy 'rabbit'
  ```
  ```bash
  docker/toxiproxy-cli toxic add --type timeout --downstream --attribute timeout=0 rabbit
  Added downstream timeout toxic 'timeout_downstream' on proxy 'rabbit'
  ```

2. Launch `fire-and-forget-producer` connected via the proxy
  ```bash
  SPRING_PROFILES_ACTIVE=toxi fire-and-forget-producer/run.sh --scheduledTradeRequester=false
  ```
3. Launch a consumer. We will use it to ensure there is a queue and also that no messages go thru due to
the toxic configured in the proxy.
  ```bash
  transient-consumer/run.sh
  ```
4. Request a trade
  ```bash
  fire-and-forget-producer/request-trade
  ```
  It will fail though:
  ```
  2020-10-07 15:27:26.198 c.p.r.FireAndForgetTradeService          [attempts:0,sent:0] Requesting Trade{ tradeId=1 accountId=23 asset=null amount=0 buy=false timestamp=0}
  2020-10-07 15:27:26.200 c.p.r.FireAndForgetTradeService          Sending trade 1 with correlation 1602077246200
  2020-10-07 15:27:26.226 o.s.a.r.c.CachingConnectionFactory       Attempting to connect to: [localhost:25673]
  2020-10-07 15:27:31.228 c.r.c.i.ForgivingExceptionHandler        An unexpected connection driver error occured (Exception message: Socket closed)
  2020-10-07 15:27:36.235 c.r.c.i.ForgivingExceptionHandler        An unexpected connection driver error occured (Exception message: Socket closed)
  2020-10-07 15:27:36.276 o.a.c.c.C.[.[.[.[dispatcherServlet]      Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed; nested exception is org.springframework.messaging.MessageHandlingException: error occurred in message handler [org.springframework.integration.amqp.outbound.AmqpOutboundEndpoint@677a0e3d]; nested exception is org.springframework.amqp.AmqpTimeoutException: java.util.concurrent.TimeoutException, failedMessage=GenericMessage [payload=byte[73], headers={resend=true, correlationId=1602077246200, id=a7f2859a-fa3e-9f18-2e3f-b4250cd2ea07, contentType=application/json, tradeId=1, account=23, timestamp=1602077246218}]] with root cause
  java.util.concurrent.TimeoutException: null
  ```
5. Check the consumer has not received any message
6. Remove the toxic from the proxy
  ```bash
  docker/toxiproxy-cli toxic remove --toxicName timeout_upstream rabbit
  Removed toxic 'timeout_upstream' on proxy 'rabbit'
  ```
7. Request a trade should work this time
  ```bash
  ./request-trade
  ```
  It should work this time:
  ```
  2020-10-07 15:30:07.558 c.p.r.FireAndForgetTradeService          [attempts:0,sent:0] Requesting Trade{ tradeId=2 accountId=23 asset=null amount=0 buy=false timestamp=0}
  2020-10-07 15:30:07.558 c.p.r.FireAndForgetTradeService          Sending trade 2 with correlation 1602077407558
  2020-10-07 15:30:07.559 o.s.a.r.c.CachingConnectionFactory       Attempting to connect to: [localhost:25673]
  2020-10-07 15:30:07.652 o.s.a.r.c.CachingConnectionFactory       Created new connection: fire-and-forget#3.publisher/SimpleConnection@54bb871d [delegate=amqp://fire-and-forget-producer@127.0.0.1:25673/, localPort= 59102]
  2020-10-07 15:30:07.656 o.s.r.s.RetryTemplate                    Retry: count=0
  2020-10-07 15:30:07.658 o.s.r.s.RetryTemplate                    Retry: count=0
  2020-10-07 15:30:07.658 o.s.a.r.c.CachingConnectionFactory       Attempting to connect to: [localhost:25673]
  2020-10-07 15:30:07.676 o.s.a.r.c.CachingConnectionFactory       Created new connection: fire-and-forget#4/SimpleConnection@591637d1 [delegate=amqp://fire-and-forget-producer@127.0.0.1:25673/, localPort= 59103]
  2020-10-07 15:30:07.676 o.s.r.s.RetryTemplate                    Retry: count=0
  2020-10-07 15:30:07.709 c.p.r.FireAndForgetTradeService          Sent trade 2
  ```
  > Note the two connections created by the producer. This is totally expected.


<br/>
<br/>
<br/>


<a name="2a"></a>
## Verify Guarantee of delivery-2a Consumer fail to process a message

Sometimes the consumer cannot process the message and it fails. It could be a
*transient* failure which means that after a few attempts, the consumer succeeds to process it.
However, there other times when the consumer exhausts all attempts to process it. This could be down
to problems with message itself (e.g. wrong/missing headers, unable to parse body, or
  schema incompatible), or infrastructure failures such as unable to connect to the database.

In this scenario, we are going to verify that in case of a *transient* failure, our consumer
does not lose the message.

**How to simulate processing failures**

`transient-consumer` and `reliable-consumer` allows us to:
  - throw a generic `RuntimeException` when it receives a trade with certain `tradeId` (`--chaos.tradeId`)
  - configure how many times we want to repeatedly fail it (`--chaos.maxFailTimes`)
  - and whether to do nothing after we have retried `maxFailTimes` (`--chaos.actionAfterMaxFailTimes=nothing`) which is the default behaviour or to throw `AmqpRejectAndDontRequeueException` (`--chaos.actionAfterMaxFailTimes=reject`) or to abruptly terminate (`--chaos.actionAfterMaxFailTimes=exit`).

**Retries in Spring Cloud Stream**

By default, SCS will retry a message as many times as indicated by [maxAttempts](https://cloud.spring.io/spring-cloud-static/spring-cloud-stream/current/reference/html/spring-cloud-stream.html#_retry_template_and_retrybackoff), which is by default, 3 times. However, if it is set to 1, SCS will not try it again. SCS relies on Spring's `RetryTemplate` to implement this retry mechanism. Once, `maxAttempts` is reached, the message is rejected.
> We can configure exponential backoff retries via configuration. See the sample configuration file [application-retries.yml](transient-consumer/src/main/resources/application-retries.yml) we use in the transient-consumer.
> If we want to have greater control on the retry logic we can provide a RestTemplate bean annotated with `@StreamRetryTemplate`.

Once SCS has decided to reject a message, there are two ways to do it which is controlled by [requeueRejected](https://github.com/spring-cloud/spring-cloud-stream-binder-rabbit/blob/master/README.adoc#rabbitmq-consumer-properties) consumer's setting.
- when `requeueRejected: false` (default), the message is *rejected* in terms of RabbitMQ, i.e. the message is dropped if the queue does not have a DLQ or instead routed to the DLQ.
- when `requeueRejected: true`, the message is *nacked* in terms of RabbitMQ, i.e. it goes back to the queue to be redelivered again.

:warning: If we use `requeueRejected: true` then our application eventually has to throw  `AmqpRejectAndDontRequeueException` otherwise the message will be bouncing back and forth forever. This could kill our consumer application and/or cause bigger problems.


### :white_check_mark: All consumer types should retry the message before giving up

To guarantee we do not lose messages, consumers must use [client AUTO acknowledgment](https://github.com/spring-cloud/spring-cloud-stream-binder-rabbit#rabbitmq-consumer-properties), which is the default value. This means a consumer will only ack a message after it has successfully processed it. And if an exception occurs -except for `AmqpRejectAndDontRequeueException`, the message is retried before giving up. As we learnt earlier, depending on whether the queue has a DLQ, the message will be lost or not. We will cover this in the [scenario 2.d](#user-content-2d).

We will verify it on the `transient-consumer`.

1. Launch the producer.
  ```bash
  reliable-producer/run.sh
  ```
2. Launch the consumer. It will fail to process tradeId `3` only two times (< `maxAttempts`) and it succeeds
on the first attempt.
  ```bash
  transient-consumer/run.sh --chaos.tradeId=3 --chaos.maxFailTimes=2
  ```
3. Notice in the consumer log how it fails 2 times and on the 3rd attempt it succeeds.
  ```
  Received Trade{ tradeId=3 accountId=6 asset=VMW amount=1000 buy=true timestamp=1601887222068} done
  Simulating failure. Attempts:1
  Failed to processed trade 3 due to ChaosMoney on trade 3 after 1 attempts
  Trade summary after trade 3: total received:3, missed:0, processed:2

  Received Trade{ tradeId=3 accountId=6 asset=VMW amount=1000 buy=true timestamp=1601887222068} done
  Simulating failure. Attempts:2
  Failed to processed trade 3 due to ChaosMoney on trade 3 after 2 attempts
  Trade summary after trade 3: total received:4, missed:0, processed:2

  Received Trade{ tradeId=3 accountId=6 asset=VMW amount=1000 buy=true timestamp=1601887222068} done
  Simulating failure. Attempts:3
  Simulating failure. Has exceeded maxTimes:2. next:nothing
  Successfully Processed trade 3
  ```

We have verified that `trade 3` is not lost.

<br/>
<br/>
<br/>

<a name="2b"></a>
## Verify Guarantee of delivery - 2.b Consumer terminates while processing a message

Here we are simulating a rather severe failure that causes the application to crash
while processing a message.

### :x: Transient consumers will lose the message and all the remaining enqueued messages

This is due to the nature of this consumer. Messages' live depend on the consumer's live.

### :white_check_mark: Only durable consumers will never lose the message

1. Launch the producer.
  ```bash
  reliable-producer/run.sh
  ```
2. Launch the consumer. It will fail to process tradeId `3` only two times (< `maxAttempts`) and then it terminates
  ```bash
  durable-consumer/run.sh --chaos.tradeId=3 --chaos.maxFailTimes=2 --chaos.actionAfterMaxFailTimes=exit
  ```
3. Notice in the consumer log how it fails 2 times and on the 3rd attempt it terminates.
  ```
  ...

  Simulating failure. Attempts:3
  Simulating failure. Has exceeded maxTimes:2. next:exit
  Trying to unbind 'durable-trade-logger-input', but no binding found.
  Removing {logging-channel-adapter:_org.springframework.integration.errorLogger} as a subscriber to the 'errorChannel' channel
  Channel 'durable-consumer.errorChannel' has 0 subscriber(s).
  stopped bean '_org.springframework.integration.errorLogger'

  ...
  ```
4. The message, `trade 3`, is not lost. If we run our consumer again without any `chaos` setting we will
see that the first message is `trade 3`.
  ```bash
  ./run.sh
  ```

  ```
  Received Trade{ tradeId=3 accountId=6 asset=VMW amount=1000 buy=true timestamp=1601887222068} done
  Successfully Processed trade 3
  ```

<br/>
<br/>
<br/>

<a name="2c"></a>
## Verify Guarantee of delivery-2c Connection drops while processing a message

### :x: Transient consumer looses all enqueued messages so far

This time we are launching producer and consumer on separate application/process.

1. Start producer
  ```bash
	fire-and-forget-producer/run.sh
  ```
2. Start transient consumer (on a separate terminal) with a message processing time of
5seconds to produce a backlog in the queue
  ```bash
	transient-consumer/run.sh --processingTime=5s
  ```
3. Wait until we have a few messages in the queue and then stop the producer. You
can use the script below to check the queue depth.
  ```bash
  docker/check-queue-depth trades.trade-logger
  ```
4. Kill the consumer's connection
  ```bash
  docker/kill-conn-grep transient-consumer
  ```

  It should print out the messages in the queue before killing the connection:
  ```
  There are 9 messages in the trades.trade-logger queue
  ```
4. Verify that the consumer reconnects but it has lost all enqueued messages


### :white_check_mark: Durable consumer does not loose the enqueued messages

1. Start producer
  ```bash
	fire-and-forget-producer/run.sh
  ```
2. Start durable consumer (on a separate terminal) with a message processing time of
5seconds to produce a backlog in the queue
  ```bash
	durable-consumer/run.sh --processingTime=5s
  ```
3. Wait until we have a few messages in the queue and then stop the producer. You
can use the script below to check the queue depth.
  ```bash
  docker/check-queue-depth trades.trade-logger
  ```
4. Kill the consumer's connection
  ```bash
  docker/kill-conn-grep durable-consumer
  ```
5. Follow the consumer's log and see that it reconnects and it receives all messages the
producer sent since it started.

The durable consumer has not lost the messages which were in the queue
right before it lost the connection. It has not lost either the messages the producer sent
while it was reconnecting.


<br/>
<br/>
<br/>

<a name="2d"></a>
## Verify delivery guarantee-2d Consumer receives a Poison message

A *Poison message* is a message that the consumer will never be able to process. Common
cases are:
- badly formed message, e.g. missing required header, or missing required body
- unable to parse content, e.g. badly formed json or xml payload
- missing schema and/or schema incompatibility, e.g. avro schema reader cannot parse avro payload
- validation errors, e.g. invalid date format, field value out of range

A consumer should detect *poison message* and reject it immediately rather than retrying it. However, if we do not configure our application to do it at least SCS will limit the number of retries (by default to 3).

:warning: Be careful changing the default SCS's configuration (e.g. `requeueRejected: true`, `maxAttepmts`) otherwise we could crash all consumer instances, or worse, consume lots of network bandwidth and cpu in the broker.


### :x: All consumers without a DLQ lose the message

:warning: After retrying a number of times, the message is rejected and the broker drops it.

1. Launch the producer.
  ```bash
  fire-and-forget-producer/run.sh
  ```
2. Launch the durable consumer that fails tradeId `3` three times. But SCS
 retries at most 3 times before rejecting it.
  ```bash
  durable-consumer/run.sh --chaos.tradeId=3 --chaos.maxFailTimes=3
  ```
3. Notice in the consumer log how it fails and the message is retried three times and then it is
rejected, i.e. it is lost.
  ```
  Received Trade{ tradeId=3 accountId=0 asset=VMW amount=1000 buy=true timestamp=1601890884488} done
  Simulating failure. Attempts:1
  Failed to processed trade 3 due to ChaosMoney on trade 3 after 1 attempts
  Trade summary after trade 3: total received:1, missed:0, processed:0

  Received Trade{ tradeId=3 accountId=0 asset=VMW amount=1000 buy=true timestamp=1601890884488} done
  Simulating failure. Attempts:2
  Failed to processed trade 3 due to ChaosMoney on trade 3 after 2 attempts
  Trade summary after trade 3: total received:2, missed:0, processed:0

  Received Trade{ tradeId=3 accountId=0 asset=VMW amount=1000 buy=true timestamp=1601890884488} done
  Simulating failure. Attempts:3
  Failed to processed trade 3 due to ChaosMoney on trade 3 after 3 attempts
  Trade summary after trade 3: total received:3, missed:0, processed:0

  Exception thrown while invoking DurableTradeLogger#execute[3 args]; nested exception is java.lang.RuntimeException: ChaosMoney on trade 3 after 3 attempts, failedMessage=GenericMessage [payload=byte[87], headers={amqp_receivedDeliveryMode=PERSISTENT, amqp_receivedExchange=trades, amqp_deliveryTag=1, deliveryAttempt=3, amqp_consumerQueue=trades.trade-logger, amqp_redelivered=true, amqp_receivedRoutingKey=trades, amqp_timestamp=Mon Oct 05 11:41:24 CEST 2020, amqp_messageId=4994a7d6-d114-ceb6-0600-3b897c68b48c, id=6345e726-4582-0a7a-c27f-9d1719ae17f7, amqp_consumerTag=amq.ctag-VnRS4Jm24Bxwpl83zhjQbw,
    ...
  Caused by: java.lang.RuntimeException: ChaosMoney on trade 3 after 3 attempts

  'republishToDlq' is true, but the 'DLX' dead letter exchange is not present; disabling 'republishToDlq'

  ```
4. Notice the last logging statement about `republishToDlq`. This is just a warning that we do not have an
alternate route -a.k.a dlq- for our rejected message. We are addressing this issue in the happy scenario below.


### :white_check_mark: Consumers with queues configured with DLQ do not lose the message

In order to demonstrate *dead-letter-queues* we are going to use the [reliable-consumer](reliable-consumer) project.

**Required configuration changes**

 We are going to configure our consumer channel with a *dead-letter-queue* in the [application-dlq.yaml](reliable-consumer/src/main/resources/application-dlq.yaml).
  ```yaml
  spring:
    cloud:
      stream:
        rabbit:
          bindings:
            durable-trade-logger-input:
              consumer:
                autoBindDlq: true
                republishToDlq: false  # default is true

  ```  
  We enable the `dlq` Spring Boot profile:
  ```yaml
  spring:
    application:
      name: reliable-consumer
    profiles:
      include:
        - management
        - cluster
        - dlq   # <-- activate dlq
  ```
  With these two changes, SCS does the following:
  - Creates a *direct* exchange named `DLX`
  - Creates a `trades.trade-logger.dlq` queue bound to the `DLX` with a routing key of `trades.trade-logger`
  - Declares a `trades.trade-logger` queue with `x-dead-letter-exchange: trades.trade-logger.dlq`

:warning: It is very convenient that SCS declares the queue fully configured with the DLQ however it has some important implications. Once we declare a queue we cannot change its configuration. If you have followed the
workshop up to this point, we already have a `trades.trade-logger` queue declared as *durable* without any arguments. Now, our `reliable-consumer` application tries to declare it with a `x-dead-letter-exchange` argument however it will fail with the following error:
```
Channel shutdown: channel error; protocol method: #method<channel.close>(reply-code=406, reply-text=PRECONDITION_FAILED - inequivalent arg 'x-dead-letter-exchange' for queue 'trades.trade-logger' in vhost '/': received the value 'DLX' of type 'longstr' but current is none, class-id=50, method-id=10)
```

**Two DLQ mechanisms available in SCS**

SCS Rabbit binder gives us two DLQ mechanisms. Let's start with the standard
RabbitMQ mechanism and then continue with a more advanced one.

In the **standard RabbitMQ mechanism** -which is disabled by the default- to get a message to a
DLQ we reject it and RabbitMQ does the job. This is the simplest and the most reliable way because
RabbitMQ will ensure the message gets to the DLQ.
The dead-lettering process adds an array to the header of each dead-lettered message named `x-death`. This array contains an entry for each dead lettering event, identified by a pair of `{queue, reason}`. Each such entry is a table that consists of several fields. More details [here](https://www.rabbitmq.com/dlx.html#effects).
Below is a screenshot from the management ui of `trade 3` dead-lettered.
![x-death-trade-3](assets/x-death.png). As we can see, the exact reason why it failed is unknown. All we know is that it was rejected.

To enable this mechanism, we use the configuration at [application-dlq.yaml](reliable-consumer/src/main/resources/application-dlq.yaml#L10) which disables the advanced mechanism.

In the **advanced mechanism provided by SCS RabbitMQ Binder** -which is enabled by default- to get a message to a DLQ we reject it but SCS, under the covers, nacks it and publishes it to the DLQ with a different set of
headers which gives us details such as:
  - `x-exception-message` It carries the exception's message
  - `x-exception-stacktrace` It carries the whole stacktrace. :warning: This could have [important implications](https://github.com/spring-cloud/spring-cloud-stream-binder-rabbit#spring-cloud-stream-rabbit-frame-max-headroom)
  - `x-original-exchange`
  - `x-original-routingKey`

Below is a screenshot from the management ui of `trade 3` dead-lettered.
![x-dql-message](assets/x-dlq-message.png).

To enable this mechanism, we use the configuration at [application-republis-dql.yaml](reliable-consumer/src/main/resources/application-republish-dlq.yaml#L10) which restores its default value.


Let's verify the scenario:

1. Launch the producer.
  ```bash
  fire-and-forget-producer/run.sh
  ```
2. Go to the management ui and delete the `trades.trade-logger` queue
3. Launch the consumer.
  ```bash
  reliable-consumer/run.sh --chaos.tradeId=3 --chaos.maxFailTimes=3
  ```
4. Notice in the consumer log how it fails and the message is retried 3 times and then
it moves onto the next message.
5. However, the message was not lost and it is not in the new dlq queue.
Check the depth of the queue has 1 message.
  ```bash
  docker/check-queue-depth trades.trade-logger.dlq
  ```

<br/>
<br/>
<br/>

### :white_check_mark: Consumers should not retry poison message neither lose it

This scenario builds on top of the previous scenario so that we do not lose poison messages. But
we do not want to retry them because it would be wasteful to do it.

**TODO** Demonstrate techniques that prevents retrying poison message (`retriableExceptions`,
  throwing appropriate Spring AMQP exception)


<a name="2e"></a>
## Verify delivery guarantee-2e Consumer gives up after failing to process a message

This failure is very similar to the previous failure, [2d Consumer receives a Poison message](#user-content-2d). It is more a semantic difference. This time we do not fail to parse or validate a *poison message*, but we fail to process it due to an infrastructure issue (e.g. database server is down).

We know that SCS will retry it and we know that once we exhaust all the attempts, it is redirected to a DLQ.

### :white_check_mark: Transient failures should be delayed and retried without blocking newer messages

This type of failure, alike a *Poison message*, is transient and typically due to an infrastructure problem. Transient failures should be retried however we cannot suspend or delay the listener until the problem is solved. Ideally, we would want to schedule it for another time and carry on processing other messages. Hopefully, newer messages will not suffer the same issue.

If we want to automatically retry messages after a configurable delay there is a mechanism explained [here](https://cloud.spring.io/spring-cloud-stream-binder-rabbit/multi/multi__retry_with_the_rabbitmq_binder.html).


<br/>
<br/>
<br/>

<a name="2f"></a>
## Verify Guarantee of delivery-2f Connection drops while sending a message

Something can go wrong right when we are sending a message. These could be the reasons:
- it is not possible to establish a connection
- the connection drops while sending it
- there is a channel failure (e.g. exchange does not exist, mandatory message could not be routed)

When any of these situations occur we expect the publish operation to retry the message before
giving up and throwing an exception to the caller.

### :x: Fire-and-forget is not resilient and fails to send it

By default, SCS will not retry fail send operations. We are going to use the `fire-and-forget-producer` to test it.

1. Get [toxi proxy ready](#user-content-toxi-proxy-ready) if you have not done it yet.
2. Disable the proxy
  ```bash
  docker/toxiproxy-cli toggle rabbit
  Proxy rabbit is now disabled
  ```
3. Launch fire-and-forget-producer which connects via the proxy
  ```
  SPRING_PROFILES_ACTIVE=toxi fire-and-forget-producer/run.sh
  ```
4. Notice that every send operation throws an exception. They are not retried.
5. Enable the proxy
  ```bash
  docker/toxiproxy-cli toggle rabbit
  Proxy rabbit is now enabled
  ```
6. The producer should be able to send now.


### :white_check_mark: Fire-and-forget is now resilient and retries when it fails

All we need to do is enable *retry mechanism* via configuration as shown below. This is from
[application-retries.yml](fire-and-forget-producer/src/main/resources/application-retries.yml):
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
                template:
                  retry:
                    enabled: true # Whether publishing retries are enabled.
                    initial-interval: 1000ms # Duration between the first and second attempt to deliver a message.
                    max-attempts: 5 # Maximum number of attempts to deliver a message.
                    max-interval: 10000ms # Maximum duration between attempts.
                    multiplier: 1 # Multiplier to apply to the previous retry interval.

```

We are going to test this configuration in the `fire-and-forget-producer`. All we need to do is enable the profile that we will do via the command line. However, the `reliable-producer` does activate the profile automatically via configuration.


1. Get [toxi proxy ready](#user-content-toxi-proxy-ready) if you have not done it yet.
2. Disable the proxy
  ```bash
  docker/toxiproxy-cli toggle rabbit
  Proxy rabbit is now disabled
  ```
3. Launch fire-and-forget-producer which connects via the proxy
  ```
  SPRING_PROFILES_ACTIVE=toxi,retries fire-and-forget-producer/run.sh
  ```
4. Notice that every send operation fails but it is retried 5 times and then it throws an exception.
  ```
  Retry: count=0
  Attempting to connect to: [localhost:25673]
  Checking for rethrow: count=1
  Retry: count=1
  Attempting to connect to: [localhost:25673]
  Checking for rethrow: count=2
  Retry: count=2
  ```
  > The extra logging statements like `Retry:` or `Checking for rethrow:` comes from the
  RetryTemplate class. See logging configuration in [application.yml](fire-and-forget-producer/src/main/resources/application.yml)

5. Enable the proxy
  ```bash
  docker/toxiproxy-cli toggle rabbit
  Proxy rabbit is now enabled
  ```

<br/>
<br/>
<br/>

<a name="2g"></a>
## Verify Guarantee of delivery-2g RabbitMQ fails to deliver a message to a queue

As far as the producer is concerned, the send operation has completed successfully, but has it really succeeded? i.e. has RabbitMQ delivered the message all intended queues? what if the broker dies right before it gets the message? what if RabbitMQ cannot deliver the message to the queue?

In this scenario we are going to test an scenario where RabbitMQ fails to deliver a message to a queue.
There could be 2 main reasons:
- RabbitMQ internal failure
- RabbitMQ reject policy due to exceeding max-length policy

We are going to test the latter.  

### :x: Fire-and-forget looses messages if RabbitMQ fails to accept it

`fire-and-forget-producer` as its name implies does not care what happens afterwards.
Therefore it does not use [publisher confirmation](https://www.rabbitmq.com/confirms.html).

1. Launch the consumer slower than the producer to cause a backlog.
  ```bash
  durable-consumer/run.sh --processingTime=5s
  ./run.sh
  ```
2. Launch the producer.
  ```bash
  fire-and-forget-producer/run.sh
  ./run.sh
  ```
3. Put a [max-length](https://www.rabbitmq.com/maxlength.html) limit on the queue by invoking the following script
  ```bash
  PORT=15673 docker/set_limit_on_queue trade-logger 3
  ```
4. Notice how the consumer does not get all the sent messages. The consumer is 5 times slower so
there will be point when the queue is full and messages are dropped. However, the sender does not know they are being dropped.


### :white_check_mark: Reliable producer knows when RabbitMQ fails to accept a message

To make our producer more resilient we have to use publisher confirmations. But that adds a
complication to our application's design because those notifications arrive asynchronously.

We have implemented a solution in the `reliable-producer` project. This solution gives us the following
advantages:
- We can make the caller wait until the message is sent
- We can make the caller not to wait however should the message failed we invoke a fallback strategy. In order dummy solution, we log it but we could have stored in a persistent store like a db or send it another queue.
- Failed messages are retried as many times as required before giving up


1. Launch the producer.
  ```bash
  reliable-producer/run.sh
  ./run.sh
```
2. Request a trade (without the caller waiting for confirmation, a.k.a. offline)
  ```bash
  reliable-producer/request-trade
  ```
3. Check that it sent a message to the `trades.trade-logger` queue
```
c.p.r.DefaultTradeService        [attempts:0,sent:0] Requesting Trade{tradeId=1accountId=23asset=nullamount=0buy=falsetimestamp=0}
c.p.r.DefaultTradeService        Sending trade 1 with correlation 1600954559449 . Attempt #1
c.p.r.DefaultTradeService        Sent trade 1
c.p.r.DefaultTradeService        Received publish confirm w/id 1600954559449 => Trade{tradeId=1accountId=23asset=nullamount=0buy=falsetimestamp=0}
c.p.r.DefaultTradeService        Removing 1 completed trades
```
4. Put a max-length limit on the queue by invoking the following script that puts a policy.
  ```bash
  PORT=15673 docker/set_limit_on_queue trade-logger 3
  ```
  > PORT=15673 allows us to target the first node in the cluster otherwise it would use 15672

5. Request a trade (offline way)
  ```bash
  reliable-producer/request-trade
  ```

6. Notice that it fails and it retries 3 times. See also that our http request succeeded.
```
```

7. Request a trade (interactive way)
  ```bash
  reliable-producer/request-trade-async
  ```

8. Notice that it fails and it retries 3 times. See also that our http request failed.
```
```

9. Remove the max-length limit and we see the producer successfully sends pending trades and continues
with newer ones.
  ```bash
  PORT=15673 docker/unset_limit_on_queue
  ```

<br/>
<br/>
<br/>

<a name="2h"></a>
## Verify Guarantee of delivery-2h RabbitMQ cannot route a message

Our send operation has completed successfully, but has the message really gone to a queue?
We know that `fire-and-forget-producer` did not care about it. We also learnt
that the `reliable-producer` uses *publisher confirmations* to ensure that the message was
successfully handled by RabbitMQ.
However, if the exchange we are sending to does not have any queue bindigs, the send is considered successful.
Therefore, *publisher confirmations* is not enough to guarantee delivery.

We need to use *publisher returns* too, another type of notification, to ensure that the
message was actually delivered to a queue. It is an asynchronous mechanism like the *publisher confirmations*.

Instead, if we want the send operation to immediately fail we send the message with a *mandatory* flag.
Thus, the caller knows whether the message made to a queue or not.
**However, We have not been able to turn this flag on yet on SCS.**


### :x: Fire-and-forget looses a message if RabbitMQ cannot route it

The `fire-and-forgot-producer` does not use any of the mechanisms mentioned so far. Therefore, it will lose the message if there are no queues where to send it.

To test it, all we need to do is remove the bindings on the durable queue `trades.trade-logger` if it is there and then launch the `fire-and-forget-producer`. You will notice that it reports successful sends however
those messages are going nowhere.


### :white_check_mark: Reliable producer knows when RabbitMQ cannot route a message

We enable *publisher returns* on the `reliable-producer`. We leverage the same solution we implemented
for *publisher confirmations* which allows us to retry messages if they fail to get to a queue.
And also, we opt to wait or not for its completion.

1. Launch the producer.
  ```bash
  reliable-producer/run.sh
  ```
2. Request a trade (offline way)
  ```bash
  reliable-producer/request-trade
  ```
3. Check that it sent a message to the `trades.trade-logger` queue and also new logging statements that informs the message was successfully sent.
```
c.p.r.DefaultTradeService                [attempts:0,sent:0] Requesting Trade{tradeId=1accountId=23asset=nullamount=0buy=falsetimestamp=0}
c.p.r.DefaultTradeService                Sending trade 1 with correlation 1600954559449 . Attempt #1
c.p.r.DefaultTradeService                Sent trade 1
c.p.r.DefaultTradeService                Received publish confirm w/id 1600954559449 => Trade{tradeId=1accountId=23asset=nullamount=0buy=falsetimestamp=0}
c.p.r.DefaultTradeService                Removing 1 completed trades
```

4. Remove the binding of `trades.trade-logger` queue so that messages do not get to any queue.

5. Request a trade (interactive way, i.e. waiting for its completion)
  ```bash
  reliable-producer/request-trade-async
  ```

6. Notice that it fails and it retries 3 times. See also that our http request failed.
```
```

### :white_check_mark: Reliable producer ensures the consumer groups' queues exists

In this scenario we are going to test a mechanism provided by SCS on the producer to ensure
the consumer queues are always there. This is a way to prevent message loss. It works by
listing the consumers' group destination queues. When the producer starts up, it declares those
queues before sending any message, as shown below:

```yaml
spring.cloud:
  stream:
    bindings: # spring cloud stream binding configuration
      outboundTradeRequests:
        destination: trades
        producer:
          errorChannelEnabled: true # send failures are sent to an error channel for the destination
          requiredGroups:
            - trade-logger
```
[application.yaml](reliable-producer/src/main/resources/application.yml)

However, the producer will not guarantee delivery when the queue's hosting node is down.
These are the two scenarios we can encounter:

- The producer starts up and the queue's hosting node is down. In this scenario,
the producer will attempt to declare it and it will fail. It does not crash though.
Any attempt to send a message will succeed but the message will go nowhere, it will be lost.
- The producer starts up and successfully declares the queue. However, later on,
the queue's hosting node goes down. The messages will go nowhere, they will be lost.

Conclusion: Adding `requiredGroups` setting in the producer, help us in reducing the
amount of message loss but it does not prevent it entirely. It is convenient because we
can start applications, producer or consumer, in any order. However, we are coupling the producer with the consumer. Also, should we added more consumer groups, we would have to reconfigure our producer application.


1. Destroy the cluster and recreate it again so that we start without any queues
  ```bash
  ./destroy-rabbit-cluster
  ./deploy-rabbit-cluster
  ```
2. Launch the producer
  ```bash
  reliable-producer/run.sh
  ```
3. Check the queue `trades.trade-logger` exists and it is getting messages even though
the consumer has not started yet.


<br/>
<br/>
<br/>

<a name="2i"></a>
## Verify Guarantee of delivery-2i Queue's hosting node down while sending messages to it

This is a very common scenario that will find in production. We need to shutdown a node
for maintenance or to upgrade it but we have applications sending and consuming messages.

### :x: Transient consumers lose all enqueued messages

If the transient queue is on the affected node, all enqueued messages are lost. This is because the consumer loses the connection.

**TODO** What happens when the queue is on a different node to the node where the consumer is connected?
The consumer is canceled but the connection is not lost. Will SCS automatically declare the queue again?

There is very little down time (in the order of milliseconds). The consumer automatically connects to another node, declares the queue and carries on.

### :question: Durable consumers do not lose any enqueued messages but may lose newer ones

**Assumption**: Messages were sent as *persistent* otherwise all enqueue messages would be lost.

Our durable consumer will not lose all enqueued messages however will not receive them while the queue's hosting node is down. Furthermore, if the producer does not cooperate, it may lose newer messages too.

Our consumer will experience downtime.

1. Launch durable consumer
  ```bash
  durable-consumer/run.sh
  ```
2. Stop the hosting node. Most likely the queue will be on the first node, `rmq0`.
  ```bash
  docker-compose -f docker/docker-compose.yml stop rmq0
  ```
3. We will notice the consumer fails to declare the queue but it keeps indefinitely trying.
```
2020-09-16 16:56:17.050 o.s.a.r.l.BlockingQueueConsumer          Failed to declare queue: trades.trade-logger
2020-09-16 16:56:17.051 o.s.a.r.l.BlockingQueueConsumer          Queue declaration failed; retries left=1

Caused by: com.rabbitmq.client.ShutdownSignalException: channel error; protocol method: #method<channel.close>(reply-code=404, reply-text=NOT_FOUND - home node 'rabbit@rmq0' of durable queue 'trades.trade-logger' in vhost '/' is down or inaccessible, class-id=50, method-id=10)

```
4. Start the hosting node.
  ```bash
  docker-compose -f docker/docker-compose.yml start rmq0
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

### :white_check_mark: Highly available consumer will not lose messages

The queue is highly available therefore consumers can still receive messages even
when the master/leader node goes down. And producers can also continue delivering messages to the
queues.


<br/>
<br/>
<br/>

<a name="2j"></a>
## Verify guarantee of delivery-2j Block producers

This is not strictly speaking a failure scenario. However, it can impact our applications and RabbitMQ too if we do not follow certain best practice. When RabbitMQ memory or disk alarm triggers, it blocks all producer
connections. However it does not block consumers as they can help reducing the excess of memory/disk that
initially triggered the alarm.

It is very important that we use separate connections for sending and consuming. This is a best practice implemented by SCS RabbitMQ binder. To truly test it, we should have both roles, consumer and producer,
in the same application. However, we are not going to do it. Instead we continue using our dedicated consumer
and producer applications, but knowing that producer applications uses a *.producer* connection solely to send
messages.

We are going to force RabbitMQ to trigger a memory alarm by setting the high water mark to 0.
This should only impact the producer connections and let consumer connections carry on.

### :x: Fire-and-forget producers may lose messages

If the application crashes, all messages sitting in the tcp buffers are lost.

### :white_check_mark: Reliable producers will not lose message

1. Launch a slow consumer
  ```bash
  reliable-consumer/run.sh --processingTime=5s
  ```
2. Launch a producer
  ```bash
  reliable-producer/run.sh
  ```
3. Wait a couple of seconds until we produce a backlog
4. Set high water mark to zero
  ```bash
  docker-compose -f docker/docker-compose.yml exec rmq0 rabbitmqctl set_vm_memory_high_watermark 0
  ```
5. Check out the queue depth goes to zero, i.e. the consumer is able to consume.
6. Check out there are no messages coming to the queue. However, they are piling up in the producer's tcp buffers. When we restore the high water mark, we will see all those messages sent to RabbitMQ.
  ```bash
  docker-compose -f docker/docker-compose.yml  exec rmq0 rabbitmqctl set_vm_memory_high_watermark 1.0
  ```

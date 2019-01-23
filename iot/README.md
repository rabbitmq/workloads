# Internet Of Things

The goal of this workload is to learn about IoT workload, get to know use cases, architectural patterns and its requirements.

The term **Internet of Things** was first used by [Kevin Ashton](http://www.rfidjournal.com/article/view/4986) in 2009 for interconnecting physical devices over the internet. The basic idea is very simple: **Physical devices can exchange data between each other or being controlled by others**.

The communication between the huge amount of devices is enabled by IPv6 and lightweight communication protocols like MQTT.

## MQTT - MQ Telemetry Transport

The goals of MQTT were to have a protocol, which is bandwidth-efficient and uses little battery power.

The protocol uses a publish/subscribe architecture in contrast to HTTP with its request/response paradigm. Publish/Subscribe is event-driven and enables messages to be pushed to clients. The central communication point is the MQTT broker, it is in charge of dispatching all messages between the senders and the rightful receivers. Each client that publishes a message to the broker, includes a topic into the message.

The *topic* is the routing information for the broker. A topic is a simple string that can have more hierarchy levels, which are separated by a slash.

By default, every message will be sent to the `amq.topic` exchange using the MQTT *topic* as the routing key.

# MQTT in RabbitMQ

As of RabbitMQ 3.7.x, it supports MQTT 3.1.1 via a [plugin](https://www.rabbitmq.com/mqtt.html). Check [here](https://www.rabbitmq.com/mqtt.html#features) for the list of supported features.

# Learnings on RabbitMQ MQTT

- Duplicate connections (with same *client id*) are not allowed. RabbitMQ will close the duplicate connection attempt and log it as follows:
  ```
   MQTT disconnecting duplicate client id <<"v1">> ("172.17.0.3:53904 -> 172.17.0.2:1883")
  ```
-

## Set up

Before we run any of the experiments we need the following:

[] Docker installed
[] RabbitMQ Docker image. To build it, check out `git@github.com:rabbitmq/rabbitmq.git` and run `cd 3.7/ubuntu/management && docker build --build-arg PGP_KEYSERVER="pgpkeys.co.uk" -t rabbitmq-mgt-ubuntu .`
[] mqtt-client Docker image. To build it run `cd mqtt-client && docker build -t mqtt .`


## Use Cases / Applications

The MQTT protocol is a good choice for wireless networks that experience varying levels of latency due to occasional bandwidth constraints or unreliable connections.


# Use case - Connected Applications

The customer is building applications where the users connect to RabbitMQ from the web pages, mobile, and Windows native apps. The customer would like to use an efficient and ubiquitous messaging protocol that is supported by many brokers, such as MQTT.

**OAuth authentication** - Not supported by RabbitMQ 3.7

RabbitMQ MQTT requires authentication via user id/password or certificate. Storing password or certificate in client source code is a security anti-pattern (it is easily discoverable), so we would like to use a token (e.g. OAuth) for user authentication.

**Topic authorization**

Customer has 2 type of applications, *consumer-to-business* and *business-to-business* apps. In *consumer-to-business* applications, users subscribe to "own" topics, i.e. topics that reflect user's id, user's group, or user's connection id (client id in MQTT terms). Hence, the need for MQTT topic access control based on patterns, so to apply to an infinitely large number of users.

Application subscribes for messages to a topic that follows this naming convention: `client`/`<client_id>`/`<event_type>`. Only an application with `bob` as it is `client_id` can subscribe to any event_type for `bob`, i.e. `client/bob/#`.

Applications sends messages to a topic that follows this naming convention:
`activity`/`<username>`/`<event_type>`. Only an application with `bob` as it is `client_id` can send a message to the topic `activity/bob/turn-on`.


## Experiment 2 - Leveraging RabbitMQ multi-tenancy

If RabbitMQ is used by just one single solution, i.e. single tenancy, we don't need to have multiple *vhosts*. We could simply use the default *vhost* as we did it in the previous experiment.

In the contrary, if RabbitMQ is used or shared by many solutions, i.e. multiple tenants, we need a *vhost* for each solution/tenant to isolate one from the other. The question lies on how to map MQTT users, or more precisely their connections, to a *vhost*.

We have 2 options:
a) We enable in RabbitMQ a port for each solution and map the port to a vhost.
or
b) Embed the *vhost* on the username like this `<vhost>:<username>`. If we use X.509-based authentication, we need map each certificate's subject to a vhost via *Runtime Global parameters* in RabbitMQ. Will this scale if we have thousands of users/devices?

> If we used on-demand RabbitMQ for PCF offering, we don't really need to set up any vhost mapping. The cluster would have just one vhost, the default one. Furthermore, the on-demand instance would be used by a single solution.

Questions: How does real MQTT architectures work?
- Do they use anonymous or authenticated access?
- What is the number of devices/users?
- Do we assign devices an ID which matches a username in the MQTT broker? or in other hand, those IDs are fixed and instead we have to register uses in the broker using those IDs?
- When is MQTTs used?

1. Declare the `mqtt` vhost and enable `guest` user to access it  

we are going to disable anonymous access and only allow declared users (`guest`/`guest`) to access RabbitMQ.
```
mqtt.allow_anonymous = false
```

## Experiment 3 - Quality of Service

The Quality of Service (QoS) level is an agreement between the sender of a message and the receiver of a message that defines the guarantee of delivery for a specific message.

There are 3 QoS levels in MQTT:
- **QoS0** At most once - There is no guarantee of delivery. Fire and forget. Supported by RabbitMQ.
- **QoS1** At least once - Guarantees that a message is delivered at least one time to the receiver. Supported by RabbitMQ.
- **QoS2** Exactly once - publishers/subscribers **downgraded to QoS1** in RabbitMQ.

The client that publishes the message to the broker defines the QoS level of the message when it sends the message to the broker. The broker transmits this message to subscribing clients using the QoS level that each subscribing client defines during the subscription process.

MQTT manages the re-transmission of messages and guarantees delivery (even when the underlying transport is not reliable). This is the reason why duplicates may happen.


## Experiment N - Authenticate users with TLS/X.509 certificates

## Experiment N - Enable multiple vhosts

## Experiment N - Handle large number of connections

The plugin supports TCP listener option configuration. The settings use a common prefix, mqtt.tcp_listen_options, and control things such as TCP buffer sizes, inbound TCP connection queue length, whether TCP keepalives are enabled and so on.

- Open File Handles (including sockets)
  ulimit –n default: 1024    1.5 * (number of connections)  500K+
- TCP Buffer Size
  reduce RAM consumption, e.g. 32KB
  drawback: throughput drop
- Erlang VM I/O Thread Pool
  RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS=“+A 128”
  12+ threads per available core
- Connection backlog
  Increase rabbit.tcp_listen_options.backlog
- TCP Sockets Options
  Low tcp_fin_timeout, tcp_tw_reuse=1


## Experiment N - MQTT with TLS

[TLS Support](https://www.rabbitmq.com/mqtt.html#tls)

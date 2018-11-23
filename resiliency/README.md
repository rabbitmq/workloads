## Resilient Java applications upon AMQP failures

The goal of this workload is to provide guidance to developers on how to write Java applications -that uses
[RabbitMQ Java AMQP](https://www.rabbitmq.com/java-client.html) or [Spring AMQP](https://docs.spring.io/spring-amqp/reference/html/)- which are resilient to failures.

The type of failures we are going to handle are:
- RabbitMQ node goes down expectedly (e.g. during a rolling upgrade) or unexpectedly
- Entire RabbitMQ cluster goes down
- Connection abruptly closed
- Channel failure  
- Fatal consumer errors (e.g. poisonous message)

## Test Environment

(PCF RabbitMQ 1.13.8) RabbitMQ 3.7.7  -> RabbitMQ 3.7.8 (PCF RabbitMQ 1.13.7)

## Patterns for applications that uses RabbitMQ Java client

## Patterns for applications that uses Spring AMQP client

### Set up

1. Build application
  ```
  cd sample-spring-rabbitmq
  mvn install
  ```
2. Create **RabbitMQ** service and deploy the application to *Cloud Foundry*
  ```
  cf create-service p-rabbitmq standard rmq
  cf push
  ```

Currently the PCF RabbitMQ have 2 offerings. In the example above, we are using the pre-provisioned offering.
This will add a vhost to a 3-node cluster with a ha-proxy in front of it. Applications will connect to RabbitMQ via the single ha-proxy.
> This offering will be deprecated soon

The other offering, called On-Demand PCF RabbitMQ, allows us to create a dedicated RabbitMQ cluster without any ha-proxy in front of it. Applications will connect directly to any of the RabbitMQ nodes in the cluster. And they decide to which node they want to connect to. And in the event of a node failure, they should connect to other nodes.

To create an on-demand RabbitMQ instance, we do it like this:
```
 cf create-service p.rabbitmq <plan-name> rmq
```

### Types of failures/situations

# Application starts up with the entire cluster down
It could happen that when we deploy our application, the entire RabbitMQ cluster is down. This could be because we are in the middle of a cluster upgrade which required to bring the entire cluster. Or the cluster is down due for maintenance reasons.

We could opt for the application to crash and let for instance Cloud Foundry to restart it again.

-> how to do it ?

# RabbitMQ node becomes unavailable with applications connected to it
A RabbitMQ node may become unavailable should any of these events occurred:
  - network partition occurs which isolates the application from the node  (reminder: heartbeats 60sec)
  - network partition occurs which pauses the node (in case of pause minority)
  - node crashes
  - operator stops the node
  - operator closes the connection (TODO: Check in mgt console if we can do it with a non-administrator user)
  - operator is performing a rolling upgrade

-> how to do it ?


# RabbitMQ node with non-mirrored durable queues becomes unavailable
For producer applications, they will not experience any connection/AMQP failures. However, published messages may be lost.
-> how do we prevent message loss?

For consumer applications which had consumers before the node became unavailable, all AMQP consumers on the queue are shutdown and their respective channels are closed. Essentially, message consumption is not possible while the node remains unavailable.
-> how do we prevent it? or how do we handle it so that consumers resume once the queue becomes available?

For consumer applications which start up when the node, hosting durable queue, is unavailable will fail to declare the queue and also will fail to subscribe.
-> how do we handle it?

# RabbitMQ cluster raises an alarm
When RabbitMQ cluster raises a memory and/or disk alarm, it blocks all producer connections. If we have consumers and producers sharing the same connection, consumers will not receive more messages until the alarm is cleared. On the other hand, connections with only consumers will still receive messages.
Producers will block indefinitely when they publish to a channel on a **blocked** connection.

-> how do we reduce the impact of this event? Separate consumer from producer connections. Detect when connection is blocked, and fail fast or stop producers rather than blocking.

# Application cannot connect due to authentication failures
It is an application design decision whether our application should crash if it cannot connect due to authentication/acess-control failures.
Situations when this might happen?
- We lost all the users in the RabbitMQ cluster (database corruption). The operator starts the RabbitMQ cluster without the users. The application connects but the users (or their permissions) are not there yet. And finally the operator imports/adds the users and their permissions.
- During blue/green deployment, applications switch to the new/blue cluster before all the data -including users- has been fully restored.   

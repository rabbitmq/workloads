## Resilient Java applications upon AMQP failures

The goal of this workload is to provide guidance to developers on how to write Java applications -that uses
[RabbitMQ Java AMQP](https://www.rabbitmq.com/java-client.html) or [Spring AMQP](https://docs.spring.io/spring-amqp/reference/html/)- which are resilient to failures.

The type of failures we are going to handle are:
- RabbitMQ node goes down expectedly (e.g. during a rolling upgrade) or unexpectedly
- Entire RabbitMQ cluster goes down
- Connection abruptly closed
- Channel failure  
- Fatal consumer errors (e.g. poisonous message)


## Patterns for applications that uses Spring AMQP client

### Set up

**Prerequisites**:
- Java 1.8
- Maven 3.3.x or newer

1. Build application
  ```
  git clone https://github.com/rabbitmq/workloads
  cd workloads/resiliency/resilient-spring-rabbitmq
  mvn install
  ```
2. Create **RabbitMQ** service and deploy the application to *Cloud Foundry*
  ```
  cf create-service p-rabbitmq standard rmq
  cf push
  ```

Currently the [PCF RabbitMQ](https://docs.pivotal.io/rabbitmq-cf/1-14/index.html) have 2 offerings. In the example above, we are using the [pre-provisioned](https://docs.pivotal.io/rabbitmq-cf/1-14/deploying.html) offering.
This will add a **vhost** to a 3-node cluster with a **ha-proxy** in front of it. Applications will connect to RabbitMQ via the single **ha-proxy**.
> This offering will be deprecated soon

The other offering, called [On-Demand PCF RabbitMQ](https://docs.pivotal.io/rabbitmq-cf/1-14/about.html), allows us to create a dedicated RabbitMQ cluster without any **ha-proxy** in front of it. Applications will connect directly to any of the RabbitMQ nodes in the cluster. And they decide to which node they want to connect to. And in the event of a node failure, they should connect to other nodes.

To create an on-demand RabbitMQ instance, we do it like this:
```
 cf create-service p.rabbitmq <plan-name> rmq
```

### Reference application

We are going to build a basic application step by step adding resiliency features as we challenge it with failure situations. The first commit is a plain Spring boot with Spring AMQP library built from the [Spring initializer](https://start.spring.io/).

[f9f8ba2](https://github.com/rabbitmq/workloads/commit/f9f8ba2)

This application as it stands, reads the RabbitMQ credentials from applications properties, which by default, connects to `localhost:5672`. To deploy in Cloud Foundry, we are going to use the Spring Cloud Services library which reads the credentials from VCAP_SERVICES and creates a ConnectionFactory.
[2a2b01f](https://github.com/rabbitmq/workloads/commit/2a2b01f)

We are going to start building the messaging application by adding a basic producer which sends messages every 5sec to a durable exchange and a basic consumer which consumes messages from a durable queue bound to the durable exchange. The application will be responsible for the declaration of the aforementioned AMQP resources.
[e88a705](https://github.com/rabbitmq/workloads/commit/e88a705)

### Types of failures/situations

Now that we have the application ready, we are going to improve its resiliency by challenging it with a number of failures.

# Application starts up with the entire cluster down
It could happen that when we deploy our application, the entire RabbitMQ cluster is down. This could be because we are in the middle of a cluster upgrade which required to bring the entire cluster. Or the cluster is down due for maintenance reasons.

We could have chosen to crash instead and let Cloud Foundry to restart it again. But we prefer the application to start and stay up and running even when it cannot connect to RabbitMQ. To make it even better, the application should report in it health status that it is out of service if it cannot connect to RabbitMQ (best practice).

Our application as it stands (commit_id [e88a705](https://github.com/rabbitmq/workloads/commit/e88a705)) can start without RabbitMQ being available. Spring AMQP will continuously try to connect indefinitely right out of the box.

However, be aware that if we try to use RabbitMQ while Spring is still initializing the application, the application will crash.
[6601573](https://github.com/rabbitmq/workloads/commit/6601573)

# Application cannot connect due to authentication failures
It is an application design decision whether our application should crash if it cannot connect due to authentication or access control failures like this one :
```
Caused by: com.rabbitmq.client.AuthenticationFailureException: ACCESS_REFUSED - Login was refused using authentication mechanism PLAIN. For details see the broker logfile.
```

This situation may happen if:
- We lose all the users in the RabbitMQ cluster (e.g. due to database corruption we had to delete **mnesia** database). The operator starts the RabbitMQ cluster without the users. The application connects to RabbitMQ but the users (or their permissions) are not there yet. And finally the operator imports/adds the users and their permissions.
- During blue/green deployment, applications switch to the new/blue cluster before all the data -including users- has been fully restored.   

The producer will get an exception when it tries to publish but Spring Scheduling (see `@Scheduled` annotation) will keep calling the producer's logic. But the consumer will fail to declare the queue and abort and terminate the application. This is because by default it is instructed to terminate if it encounter an authentication error.
To make the consumer survive this situation we need to configure the consumer accordingly.
[cfa3736](https://github.com/rabbitmq/workloads/commit/cfa3736)

# RabbitMQ node becomes unavailable with applications connected to it
A RabbitMQ node may become unavailable should any of these events occurred:
  - network partition occurs which isolates the application from the node  (reminder: heartbeats 60sec)
  - network partition occurs which pauses the node (in case of pause minority)
  - node crashes
  - operator stops the node
  - operator closes the connection
  - operator is performing a rolling upgrade (e.g. Upgrade *PCF RabbitMQ 1.13.8 / RabbitMQ 3.7.7* to *RabbitMQ 3.7.8 (PCF RabbitMQ 1.13.7*)

Provided we have configured the application with a list of addresses to the RabbitMQ cluster, Spring AMQP will automatically try to connect to the other nodes. This is the case when we use On-Demand PCF RabbitMQ offering with a cluster.
In the case of Pre-Provision PCF RabbitMQ, where we only have one address, the HA-proxy's address, Spring AMQP will keep trying to connect to that single address.

This is as far as the connection recovery is concerned. Now, we are going to address the resiliency of consumers when we lose the connection. We contemplate the following 2 scenarios: one with durable queues and another with non-durable queues, both non-mirrored.

## RabbitMQ node with non-mirrored durable queues becomes unavailable
The producer will not experience any connection/AMQP failures. However, published messages may be lost.
How do we prevent message loss in this case?
- Either we use mandatory flag (be aware of the performance impact this flag may have. Read more [here](https://www.rabbitmq.com/blog/2012/04/25/rabbitmq-performance-measurements-part-2/))
- Use [Alternate exchange](https://www.rabbitmq.com/ae.html)

The consumer will get shutdown and its respective channel closed.
```
Caused by: com.rabbitmq.client.ShutdownSignalException: channel error; protocol method: #method<channel.close>(reply-code=404, reply-text=NOT_FOUND - home node 'rabbit@rabbit1' of durable queue 'durable-q' in vhost '/' is down or inaccessible, class-id=50, method-id=10)
```
And after 3 failed attempts to recover it, the consumer is shutdown forever.
```
2018-11-23 16:24:54.632 ERROR 88230 --- [nDurableQueue-2] o.s.a.r.l.SimpleMessageListenerContainer : Stopping container from aborted consumer
2018-11-23 16:24:54.633  INFO 88230 --- [nDurableQueue-2] o.s.a.r.l.SimpleMessageListenerContainer : Waiting for workers to finish.
2018-11-23 16:24:54.633  INFO 88230 --- [nDurableQueue-2] o.s.a.r.l.SimpleMessageListenerContainer : Successfully waited for workers to finish.
```
Message consumption is not possible while the node remains unavailable and RabbitMQ will not let the application to declare it either. Durable queues can only exist in one node therefore when the node goes down, the cluster makes the queue not only unavailable but also it will prevent any attempts to redeclare it.

To make our consumer resilient to this failure, we need to configure with the flag `MissingQueuesFatal` equal to `false`.

## RabbitMQ node with non-mirrored non-durable queues becomes unavailable
We added a new pair of consumer-producer components to our application but this pair with declare a non-durable exchange and a non-durable queue.
(1d91839)[https://github.com/rabbitmq/workloads/commit/1d91839]

At first we may expect the non-durable consumer will be able to re-declare the non-durable queue in the new connected node. But we encounter an issue. The Spring AMQP consumer (`SimpleMessageListenerContainer`) delegates the resource declaration to default instance of `RabbitAdmin`. This instance is configured such that if it fails to declare one resource, it fails the rest.  

We need to create a custom `RabbitAdmin` with the flag `ignoreDeclarationExceptions` set to `true` and configure the `ConnectionFactory` with this instance.

[2722f1f](https://github.com/rabbitmq/workloads/commit/2722f1f)

# RabbitMQ cluster raises an alarm
When RabbitMQ cluster raises a memory and/or disk alarm, it blocks all producer connections. If we have consumers and producers sharing the same connection, consumers will not receive more messages until the alarm is cleared. On the other hand, connections with only consumers will still receive messages.
Producers will block indefinitely when they publish to a channel on a **blocked** connection.

We should create separate connections for consumption and publishing messages. Spring AMQP actually facilitates the job by exposing a new method in the `ConnectionFactory` called `getPublisherConnectionFactory()`. We need to change our consumers and producers so that they get the correct `ConnectionFactory`. Choose the `producer` `ConnectionFactory` as the `@Primary` or default one.

It would also be great if we could identify which connection is which when we look at the management UI. Spring AMQP allows us to inject our own `ConnectionNamingStrategy` but we can leverage the existing strategy. We can give it a meaningful name to the `@Bean` of each `ConnectionFactory` and Spring AMQP will use it.

[550f6ec](https://github.com/rabbitmq/workloads/commit/550f6ec)


## Patterns for applications that uses RabbitMQ Java client

### Set up

### Reference application

### Types of failures/situations

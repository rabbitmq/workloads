## Resilient Applications in Java: Handle Connection Failures and More

The goal of this workload is to provide guidance to developers on how to write Java applications -that uses
[RabbitMQ Java AMQP](https://www.rabbitmq.com/java-client.html) or [Spring AMQP](https://docs.spring.io/spring-amqp/reference/html/)- which are resilient to failures.

The type of failures we are going to handle are:
- RabbitMQ node goes down expectedly (e.g. during a rolling upgrade) or unexpectedly
- Entire RabbitMQ cluster goes down
- Connection fails or abruptly closed by an intermediary such as a load balancer
- Channel-level exceptions  
- Fatal consumer errors (e.g. poisonous message)

## Skeleton for Spring AMQP client

Before we start talking about resiliency and what it means, first we would like to set the basis of how we are going to bootstrap RabbitMQ ConnectionFactory.

### Cloud Foundry and Spring Cloud

First of all, we are assuming we are running our Spring applications in Cloud Foundry. [Spring Cloud](https://spring.io/projects/spring-cloud) ecosystem offers a very convenient library called [Spring Cloud Connectors](https://cloud.spring.io/spring-cloud-connectors/) which we can use it to either get automatically injected a [ConnectionFactory](https://docs.spring.io/spring-amqp/api/org/springframework/amqp/rabbit/connection/ConnectionFactory.html) to our application or get one manually built by calling the appropriate [method](https://docs.spring.io/spring-cloud/docs/1.2.6.RELEASE/api/org/springframework/cloud/config/java/ServiceConnectionFactory.html#rabbitConnectionFactory()). The latter method allows us to fully configure the ConnectionFactory because by default Spring Cloud Services does not take into account the local Spring configuration.

Spring Cloud Connectors is able to read from `VCAP_SERVICES` the full credentials of a RabbitMQ Service Instance. See a sample below which corresponds to a 3-node cluster.
```
{
  "user-provided": [
    {
      "credentials":  {
        "uri": "amqp://af716b3b-9f10-49f6-8433-22d040f6a6e2:0FUhD8G8ntYpAjGY48hifHmF@10.0.0.41:5672/ee72bc16-3752-4c60-85d4-3c74a015aa57",
       "uris": [
        "amqp://af716b3b-9f10-49f6-8433-22d040f6a6e2:0FUhD8G8ntYpAjGY48hifHmF@10.0.0.42:5672/ee72bc16-3752-4c60-85d4-3c74a015aa57",
        amqp://af716b3b-9f10-49f6-8433-22d040f6a6e2:0FUhD8G8ntYpAjGY48hifHmF@10.0.0.42:5672/ee72bc16-3752-4c60-85d4-3c74a015aa57",
        amqp://af716b3b-9f10-49f6-8433-22d040f6a6e2:0FUhD8G8ntYpAjGY48hifHmF@10.0.0.43:5672/ee72bc16-3752-4c60-85d4-3c74a015aa57",
       ],
      },
      "instance_name": "rmq",
      "label": "rabbitmq",
      "name": "rmq"
    }
  ]
}
```

From the above configuration, RabbitMQ Cloud Connectors takes the `username`, `password` and `vhost` from the `uri` *json attribute*. If the `uris` *json array* is present, it extracts all the hostname/ipadress and port from the array of AMQP addresses. If `uris` *json array* is not present or empty, the hostname and port in the `uri` is used instead.  
Spring Cloud Connectors configures Spring AMQP's ConnectionFactory with the full list of addresses. Spring AMQP will first connect to the first address (`10.0.0.41:5672`). If that fails it attempts to connect to the next one, `10.0.0.42:5673` and so forth.


### Skeleton application

The minimum code that demonstrate RabbitMQ bootstrapping for a Spring Boot application check out the [resilient-skeleton-spring-rabbitmq](resilient-skeleton-spring-rabbitmq) folder.

Before waking thru this skeleton application it is worth mentioning the requirements that shaped the application as it is. These are:
- Our application may potentially require another RabbitMQ service instance
- also our application most likely will need custom RabbitMQ ConnectionFactory settings like `spring.rabbitmq.connection-timeout`

These two requirements tells us that we cannot use [Spring Auto-configuration provided by the Java Buildpack](https://docs.cloudfoundry.org/buildpacks/java/configuring-service-connections/spring-service-bindings.html#auto) to automatically bootstrap RabbitMQ ConnectionFactory for us. Instead we need to ask that mechanism to back off.

### When Auto Configuration is a viable option

In the contrary, if your application does not need any custom RabbitMQ configuration and your application only ever gets bound one RabbitMQ service instance then all we need to do is add the appropriate Spring AMQP dependency. We don't need to include `spring-cloud-spring-service-connector` or `spring-cloud-cloudfoundry-connector` because [Spring Auto-configuration provided by the Java Buildpack](https://docs.cloudfoundry.org/buildpacks/java/configuring-service-connections/spring-service-bindings.html#auto) does it for us.

### When Auto Configuration is not a viable option

Going back to the skeleton application the key classes are:
- [CloudConfig](resilient-skeleton-spring-rabbitmq/src/main/java/com/pivotal/resilient/CloudConfig.java) - This is where we ask Spring Cloud Connectors for an RabbitMQ ConnectionFactory instance and expose it as a `@Bean` so that we can use it from other parts of our Spring application. In fact, we build 2 separate ConnectionFactories, one for publishing and another one for consuming messages. This is a very good practice that we will discuss later on.

- [RabbitMQConfiguration](resilient-skeleton-spring-rabbitmq/src/main/java/com/pivotal/resilient/RabbitMQConfiguration.java) - This is where we build RabbitMQ related objects like a RabbitTemplate that uses the `publisher` connection factory or a rabbitListenerContainerFactory used for methods annotated with `@RabbitListener` configured with the `consumer` connection factory.
  > It is a good practice to separate publisher from consumer connections. We can also easily identify them in the RabbitMQ management ui as shown in the screenshot below
  > ![Connection for publisher annotated ConnectionFactory](assets/skeleton-producer-conn.png)

- [ResilientSpringRabbitmqApplication](resilient-skeleton-spring-rabbitmq/src/main/java/com/pivotal/resilient/ResilientSpringRabbitmqApplication.java) - This is where we use the RabbitTemplate to send a message every 5 seconds to the configured routing-key and exchanged in the RabbitTemplate.

### Get started with this skeleton app

Get the code if you have not done it yet.
```
> git clone https://github.com/rabbitmq/workloads
```

Create a RabbitMQ Service instance with a 3-node cluster (`plan-2`) and wait until it is built:
```
> cf create-service p.rabbitmq plan-2 rmq
> cf services
Getting services in org system / space dev as admin...

name   service      plan     bound apps   last operation
rmq    p.rabbitmq   plan-2                create succeeded
```


We will need Java 1.8 and at least Maven 3.3.x to build the application.
```
> cd resiliency/resilient-skeleton-spring-rabbitmq
> mvn install
```

Push the application using the local `manifest.yml` file which binds the application to the `rmq` service instance we just created.
```
cf push
```

The logs -coming from Spring AMQP- reveal the list of AMQP addresses configured and the address used to establish the connection. The following logs shows the application was configured with 2 addresses (`[localhost:5672, localhost:5673]`) and the connected address `127.0.0.1:5673`.

```
2019-03-20 15:53:38.532  INFO 86633 --- [   scheduling-1] o.s.a.r.c.CachingConnectionFactory       : Attempting to connect to: [localhost:5672, localhost:5673]
2019-03-20 15:53:38.533  INFO 86633 --- [           main] c.p.r.ResilientSpringRabbitmqApplication : Started ResilientSpringRabbitmqApplication in 1.596 seconds (JVM running for 2.094)
2019-03-20 15:53:38.587  INFO 86633 --- [   scheduling-1] o.s.a.r.c.CachingConnectionFactory       : Created new connection: producer#42b64ab8:0/SimpleConnection@73ae06fd [delegate=amqp://guest@127.0.0.1:5673/, localPort= 49402]
```


### To run it locally

To run it locally all you need to do is either use the command `run.sh` or if you want to run it from your preferred IDE declare the following environment variables:
- `SPRING_PROFILES_ACTIVE` with the value `Cloud`
- `VCAP_APPLICATION` with the value `'{"application_name":demo}'`
- `VCAP_SERVICES` with the value copied from either [src/main/resources/cluster.json](resilient-skeleton-spring-rabbitmq/src/main/resources/cluster.json) or  [src/main/resources/singleNode.json](resilient-skeleton-spring-rabbitmq/src/main/resources/singleNode.json)

### RabbitMQ Metrics

Although it is not strictly necessary to talk about metrics at this stage where we only care about the minimum logic to connect to RabbitMQ, we thought that monitoring metrics was such a basic functionality that it deserved to be done now.

In [CloudConfig](resilient-skeleton-spring-rabbitmq/src/main/java/com/pivotal/resilient/CloudConfig.java) we build 2 Spring AMQP connection factories with its own Java RabbitMQ connection factory.
The reason for having a dedicated Java RabbitMQ ConnectionFactory for each Spring AMQP ConnectionFactory is so that we could have separate metrics for each connectionFactory. In the contrary, if both Spring AMQP ConnectionFactory shared the same Java RabbitMQ ConnectionFactory then we would have aggregated metrics which is not very useful.

By default, Spring AMQP auto-configuration groups all the rabbitmq metrics under the prefix `rabbitmq`. For instance, `rabbitmq.connections` metrics gives us all the connections opened by the single Java RabbitMQ ConnectionFactory created by Spring AMQP auto-configuration. This works fine if we do not need to know how many connections are used for consuming and how many for producing. But if we cared then this is what we did in [CloudConfig](resilient-skeleton-spring-rabbitmq/src/main/java/com/pivotal/resilient/CloudConfig.java#L52-L59).

If you run `curl localhost:8080/actuator/metrics | jq . | grep rabbitmq` it returns all the `rabbitmq` metrics:
```
  "rabbitmq.producer.acknowledged_published",
  "rabbitmq.producer.consumed",
  "rabbitmq.producer.acknowledged",
  "rabbitmq.consumer.failed_to_publish",
  "rabbitmq.consumer.channels",
  "rabbitmq.producer.connections",
  "rabbitmq.consumer.acknowledged",
  "rabbitmq.consumer.not_acknowledged_published",
  "rabbitmq.producer.channels",
  "rabbitmq.consumer.unrouted_published",
  "rabbitmq.consumer.rejected",
  "rabbitmq.consumer.acknowledged_published",
  "rabbitmq.consumer.connections",
  "rabbitmq.producer.not_acknowledged_published",
  "rabbitmq.producer.failed_to_publish",
  "rabbitmq.producer.rejected",
  "rabbitmq.producer.unrouted_published",
  "rabbitmq.consumer.published",
  "rabbitmq.consumer.consumed",
  "rabbitmq.producer.published",
```

And to get the connections opened by the `producer` connectionFactory, we run
`curl localhost:8080/actuator/metrics/rabbitmq.producer.connections | jq .` which produces

```
{
  "name": "rabbitmq.client.producer.connections",
  "description": null,
  "baseUnit": null,
  "measurements": [
    {
      "statistic": "VALUE",
      "value": 1
    }
  ],
  "availableTags": [
    {
      "tag": "cf-app-name",
      "values": [
        "demo"
      ]
    },
    {
      "tag": "cf-space-id",
      "values": [
        "flamingo"
      ]
    },
    {
      "tag": "cf-app-id",
      "values": [
        "0001"
      ]
    },
    {
      "tag": "app-name",
      "values": [
        "resilient-skeleton-spring-rabbitmq"
      ]
    }
  ]
}
```

We have tagged the metrics so that we can identify the originator. These are the tags and [here](resilient-skeleton-spring-rabbitmq/src/main/java/com/pivotal/resilient/CloudConfig.java#L59-L65) you can find where we set them up in the code.
- tag `cf-app-name` = `VCAP_APPLICATION.name`
- tag `cf-app-id` = `VCAP_APPLICATION.instance_id`
- tag `cf-space-id` = `VCAP_APPLICATION.space_id`
- tag `app-name` = `spring.application.name`


### Monitoring skeleton application with Datadog

It would be great to be able to present the metrics discussed in the previous section in a dashboard together with RabbitMQ server metrics so that we can reason about our architecture from a single place. In this section, we are going to go thru what we need to do in order to get our application to send metrics to Datadog and how to find those metrics in Datadog. We are not going to build any dashboard with those metrics yet.

To know more about how to monitor RabbitMQ server with Datadog and how even how to install it, check out [Monitoring RabbitMQ for PCF using Datadog](https://github.com/MarcialRosales/rabbitmq-monitoring-guide/tree/master/datadog).

First of all, we need to build the application with a different Maven profile so that it includes `micrometer-registry-datadog` dependency.
  > The pom.xml has a [custom build profile](resilient-skeleton-spring-rabbitmq/pom.xml#L68-L76) which adds `micrometer-registry-datadog` dependency

  ```
  mvn -P datadog clean install
  ```

Second, to run it locally we need to specify the Datadog API_KEY:
  ```
  export DATADOG_API_KEY="my-key"
  ./run.sh --management.metrics.export.datadog.api-key=$DATADOG_API_KEY
  ```
    > Notice that run.sh activates the spring profile `datadog` in all cases. Although it only takes effect when we add the appropriate dependency.

  It should print out the following statement every 20seconds:
  ```
  019-04-15 11:43:19.613 DEBUG 54120 --- [trics-publisher] i.m.datadog.DatadogMeterRegistry         : successfully sent 94 metrics to datadog
  ```

  And we can see the metrics in Datadog Metrics explorer. In the screenshot below, we are observing the number of opened *consumer* connections.
  ![client metrics](assets/client-metrics.png)
    > In the screenshot we are searching for metrics coming from a given application name and running within a Cloud Foundry space.

Third, to deploy it in Cloud Foundry we run the following command:
  ```
  export DATADOG_API_KEY="my-key"
  cf push -f manifest-datadog.yml --var datadog_api_key=$DATADOG_API_KEY
  ```


## Patterns for applications that uses Spring AMQP client

We are going to build a basic application incrementally. On every increment we are going to challenge it with a failure and improve the code so that it can gracefully handle it.

If you want to run the code follow the steps on the next sections otherwise if you are only interested in the patterns you can go straight to the [Reference Spring AMQP application](#Reference-Spring-AMQP-application) section.

### Getting the code and building the application

We will need Java 1.8 and at least Maven 3.3.x to build the application.

  ```
  git clone https://github.com/rabbitmq/workloads
  cd workloads/resiliency/resilient-spring-rabbitmq
  mvn install
  ```

### To run the application locally

We need to provide the following environment variables to run the application locally:
```
export SPRING_PROFILES_ACTIVE=Cloud
export VCAP_APPLICATION='{"application_name":"demo"}'
export VCAP_SERVICES="$(cat src/main/resources/cluster.json)"
```

To run the application within IntelliJ, copy the file `ResilientSpringRabbitmqApplication.xml` to folder `.idea/runConfigurations`(within `workloads/resiliency/resilient-spring-rabbitmq` folder)  

Either `src/main/resources/cluster.json` or `ResilientSpringRabbitmqApplication.xml` have the credentials to a 3 node RabbitMQ cluster running in localhost on ports 5672, 5673 and 5674 respectively.  

#### To run a 3-node cluster locally

Follow these steps to run a 3-node cluster locally. You will need Docker installed in your machine:

```
git clone https://github.com/harbur/docker-rabbitmq-cluster
cd docker-rabbitmq-cluster
docker-compose up -d
```

The application is configured to connect to RabbitMQ cluster with a user called `test` and password `test`. The application will not be able to connect right after we bring the cluster up because it comes with the default user `guest:guest`.

This is on purpose so that we can reproduce situations where we connect to a cluster due to access control failures.
To add the user to the running cluster follow these steps:
```
$ docker exec -it docker-rabbitmq-cluster_rabbit1_1 /bin/bash -c 'rabbitmqctl add_user test test; rabbitmqctl set_permissions test ".*" ".*" ".*"; rabbitmqctl set_user_tags test administrator'
```

### To deploy the application to Cloud Foundry

First we need to provision a RabbitMQ cluster. [RabbitMQ for PCF](https://docs.pivotal.io/rabbitmq-cf/1-14/index.html) have 2 service offerings.

The [pre-provisioned](https://docs.pivotal.io/rabbitmq-cf/1-14/deploying.html) offering which consists of  a multi-tenant RabbitMQ cluster with a **ha-proxy** in front of it. Applications will connect to RabbitMQ via a single address, the **ha-proxy** address.
> This offering will be deprecated soon

To create an pre-provisioned RabbitMQ cluster, we do it like this:
```
 cf create-service p-rabbitmq standard rmq
```
> It will not create a cluster but a vhost and a user assigned to the vhost on a pre-existing RabbitMQ cluster.

The other offering, called [On-Demand PCF RabbitMQ](https://docs.pivotal.io/rabbitmq-cf/1-14/about.html), allows us to create a dedicated RabbitMQ cluster without any **ha-proxy** in front of it. Applications will connect directly to any of the RabbitMQ nodes in the cluster. In the event of a node failure, it is up to the applications to decide which node to connect to.

To create an on-demand RabbitMQ instance, we do it like this.
```
 cf create-service p.rabbitmq <plan-name> rmq
```
> Replace <plan-name> with one of the plans available in your Cloud Foundry foundation

Now we can push the application. It uses the local `manifest.yml` file which references the `rmq` service we just created.
```
cf push
```

### Reference Spring AMQP application

We start with a plain Spring boot application created by [Spring initializer](https://start.spring.io/).

This is the commit_id for this seed application [f9f8ba2](https://github.com/rabbitmq/workloads/commit/f9f8ba2).

This application as it stands, reads the RabbitMQ credentials from applications properties, which by default, connects to `localhost:5672`. To deploy to Cloud Foundry, we are going to use [Spring Cloud Connectors](https://cloud.spring.io/spring-cloud-connectors/) to get an instance of ` org.springframework.amqp.rabbit.connection.ConnectionFactory` already configured with the credentials to connect to the RabbitMQ cluster.

We make the following changes:
- Add these 2 dependencies to the `pom.xml`:
  ```
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-spring-service-connector</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-cloudfoundry-connector</artifactId>
  </dependency>
  ```
- Add [RabbitMQConfiguration](https://github.com/rabbitmq/workloads/blob/2a2b01f/resiliency/resilient-spring-rabbitmq/src/main/java/com/pivotal/resilient/resilientspringrabbitmq/RabbitMQConfiguration.java) which obtains the `org.springframework.amqp.rabbit.connection.ConnectionFactory` from the [Spring Cloud Connectors](https://cloud.spring.io/spring-cloud-connectors/)

[2a2b01f](https://github.com/rabbitmq/workloads/commit/2a2b01f)

Now we are ready to add a basic producer which sends messages every 5sec to a durable exchange and a basic consumer which consumes messages from a durable queue bound to the durable exchange. The application will be responsible for the declaration of the aforementioned AMQP resources too.

[e88a705](https://github.com/rabbitmq/workloads/commit/e88a705)

### Types of failures/situations

Now that we have the application ready, we are going to improve its resiliency by challenging it with a number of failures.

#### Application starts up with the entire cluster down
It could happen that when we deploy our application, the entire RabbitMQ cluster is down. This could be because we are in the middle of a cluster upgrade which required to bring the entire cluster. Or the cluster is down due for maintenance reasons. It may not be easy -and undesirable- to coordinate service (upgrades, stop/start) and application operations (e.g. deploy, stop/start).

We could have chosen to crash the application instead and let Cloud Foundry restart it. But we preferred the application should gracefully start even when it cannot connect to RabbitMQ. To make it even better, the application should report in it health status that it is out of service if it cannot connect to RabbitMQ.

Right now, our application (commit_id [e88a705](https://github.com/rabbitmq/workloads/commit/e88a705)) can start without RabbitMQ cluster down. Spring AMQP will continuously try to connect indefinitely right out of the box.

However, be aware that if we try to use RabbitMQ while Spring is initializing the application, the application will crash.
We introduced this small buggy component to our application to demonstrate it : [8ec0c97](https://github.com/rabbitmq/workloads/commit/8ec0c97)

#### Application cannot connect due to authentication failures
It is an application design decision whether our application should crash if it cannot connect due to access control failures like this one :
```
Caused by: com.rabbitmq.client.AuthenticationFailureException: ACCESS_REFUSED - Login was refused using authentication mechanism PLAIN. For details see the broker logfile.
```

This situation may happen if:
- We lose all the users in the RabbitMQ cluster (e.g. **mnesia** database was corrupted in all nodes of the cluster and we had to delete it in order to restore the RabbitMQ nodes). The operator starts the RabbitMQ cluster without the users. The application connects to RabbitMQ but the users (or their permissions) are not there yet. After the operator imports/adds the users and their permissions, the application is able to connect.
- During blue/green deployment, applications switch to the new/blue cluster before all the data -including users- has been fully restored.   

The producer will get an exception when it tries to publish. However, because it is an scheduled tasks (see `@Scheduled` annotation), it will keep sending. But the consumer will fail to declare the queue and abort and terminate the application. This is because by default it is instructed to terminate if it encounter an authentication error.
To make the consumer survive this situation we need to configure the consumer accordingly.

[cfa3736](https://github.com/rabbitmq/workloads/commit/cfa3736)

**TL;DR**: We have not built any message resiliency yet.  

#### RabbitMQ node becomes unavailable with applications connected to it
A RabbitMQ node may become unavailable should any of these events occurred:
  - network partition occurs which isolates the application from the node. Eventually, and thanks to the heartbeats the Java client sends to the RabbitMQ node (by default once every 30sec), it detects the problem and closes the connection
  - network partition occurs which pauses the node (in case of pause minority)
  - node crashes
  - operator stops the node
  - operator closes the connection
  - operator is performing a rolling upgrade (e.g. Upgrade *PCF RabbitMQ 1.13.8 / RabbitMQ 3.7.7* to *RabbitMQ 3.7.8 (PCF RabbitMQ 1.13.7*)

If we are using **On-Demand RabbitMQ for PCF** offering, our application gets the address of every node in the cluster. Spring AMQP will automatically try to connect to next node in the list.

If we are using **Pre-Provision RabbitMQ for PCF** offering, our application only gets one address, the HA-proxy's address. Spring AMQP will automatically try to connect to that single address and the ha-proxy will connect it to an available node, if any.

**TL;DR** : This is as far as the connection recovery is concerned. Spring AMQP will automatically recover the connection if there is an available node. However, our application may still crash due to buggy logic in the consumer and/or producer.

In the next sections we are going to address the resiliency of consumers when we lose the connection. We contemplate the following 2 scenarios: one with durable queues and another with non-durable queues, both non-mirrored.

#### RabbitMQ node with non-mirrored durable queues becomes unavailable
When this situation occurs, although the RabbitMQ Cluster knows about the queue existence, as far as the applications is concerned the queue does not exist and hence they cannot use it. But even worse, they cannot declare it either.

The producer will not experience any connection/AMQP failures. However, published messages may be lost.
How do we prevent message loss in this case?
- Either we use [mandatory](https://www.rabbitmq.com/amqp-0-9-1-reference.html#basic.publish.mandatory) flag (be aware of the [performance impact](https://www.rabbitmq.com/blog/2012/04/25/rabbitmq-performance-measurements-part-2/) this flag may have. If we use this flag, RabbitMQ will inform the producer if it could not send the message to any queue.
- Use [Alternate exchange](https://www.rabbitmq.com/ae.html) to send messages to an alternate exchange should an exchange cannot find a queue where to send the messages.

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

Message consumption is not possible while the node remains unavailable and RabbitMQ will not let the application to declare it either. To make our consumer resilient to this failure, we need to configure it with the flag `MissingQueuesFatal` equal to `false`.

[352558d](https://github.com/rabbitmq/workloads/commit/352558d)

#### RabbitMQ node with non-mirrored non-durable queues becomes unavailable
We added a new pair of consumer-producer components to our application that declares a non-durable exchange and a non-durable queue.
[1d91839](https://github.com/rabbitmq/workloads/commit/1d91839)

We probably expect the non-durable consumer would be able to re-declare the non-durable queue when it recovers the connection. But we encountered an issue. The Spring AMQP consumer (`SimpleMessageListenerContainer`) delegates the resource declaration to a default instance of `RabbitAdmin`. This instance is configured to fail all declarations if at least one fails. In our case, we are failing to declare the durable queue.

We need to create a custom `RabbitAdmin` with the flag `ignoreDeclarationExceptions` set to `true` and configure the `ConnectionFactory` with this instance.

[2722f1f](https://github.com/rabbitmq/workloads/commit/2722f1f)

#### RabbitMQ cluster raises an alarm
When RabbitMQ cluster raises a memory and/or disk alarm, it blocks all producer connections. If we have consumers and producers sharing the same connection, consumers will not receive more messages until the alarm is cleared. On the other hand, connections with only consumers will still receive messages.
Producers will block indefinitely when they publish to a channel on a **blocked** connection.

We should create separate connections for consumption and publishing messages. Spring AMQP actually facilitates the job by exposing a new method in the `ConnectionFactory` called `getPublisherConnectionFactory()`. We need to change our consumers and producers so that they get the correct `ConnectionFactory`. We should make the `producer` `ConnectionFactory` as the `@Primary` or default one.

[550f6ec](https://github.com/rabbitmq/workloads/commit/550f6ec)

It would also be great if we could identify which connection is which when we look at the management UI. Spring AMQP allows us to inject our own `ConnectionNamingStrategy` but we can leverage the existing strategy. We can give it a meaningful name to the `@Bean` of each `ConnectionFactory` and Spring AMQP will use it.

In the diagram below we can see the two named connections. The first number after the connection label (`consumer`, `producer`) is the `ConnectionFactory` reference and the second number is the number associated to this connection. The value is `0` but a value of `10` would mean that this is the 11th connection created by `ConnectionFactory`.
![named connections](assets/namedConnections.png)

#### Rolling upgrade of RabbitMQ for PCF
Depending on what versions are involved in an upgrade, we have to choose the right upgrade procedure. These are the 2 possible upgrade procedures:
- [Rolling upgrade](https://www.rabbitmq.com/upgrade.html#multiple-nodes-upgrade) - Perform upgrades without cluster downtime. A rolling upgrade is when nodes are stopped, upgraded and restarted one-by-one, with the rest of the cluster still running while each node is being upgraded.
- [Full-Stop upgrade](https://www.rabbitmq.com/upgrade.html#full-stop-upgrades) - If rolling upgrades are not possible, the entire cluster should be stopped, then restarted. This is referred to as a full stop upgrade.

When we do a rolling upgrade on a **Pre-Provisioned RabbitMQ for PCF** cluster, applications will suffer some downtime. For instance, in a 3 node cluster with 1 ha-proxy, it has been observed around 5 minutes downtime. This is because RabbitMQ for PCF upgrades not only RabbitMQ cluster but also the Ha-Proxy server.

When we do a rolling upgrade on a **On-demand RabbitMQ for PCF** cluster, applications will suffer a sub-second downtime. However, applications should be written to handle connection drops so that they can reconnect when the RabbitMQ Cluster is upgraded.

As far as the applications are concerned, they only see nodes going down or the entire cluster going down. And these 2 failure scenarios have already been handled in the previous sections.

### Message resiliency

So far we have covered failures scenarios of RabbitMQ nodes and how we can improve our applications so that we are resilient to them and hence they remain available until the RabbitMQ nodes' service is restored.
But there is another type of failure we need to deal with which is related to message resiliency, and in simple words, prevent message loss.

RabbitMQ provides mechanisms to prevent message loss for publishers and consumers.

**Publishers** shall use:
- [Publisher confirmation](https://www.rabbitmq.com/confirms.html). It could happen that the broker dies just before it received the message.
- [Alternate Exchange](https://www.rabbitmq.com/ae.html) or [Mandatory Flag](https://www.rabbitmq.com/amqp-0-9-1-reference.html#basic.publish.mandatory). It could happen that the producer application sends messages before the consumer application has declared its queue and bound it to the exchange. It could also happens that the hosting non-mirrored durable queue node is not available.

**Consumers** shall use:
- [Client acknowledgement](https://www.rabbitmq.com/confirms.html#acknowledgement-modes)

This is as far as RabbitMQ is concerned and regardless of the RabbitMQ client library we use.

But what happens when our message consumer logic throws an exception while processing the message? or what happens with [poisonous messages](https://docs.microsoft.com/en-us/dotnet/framework/wcf/feature-details/poison-message-handling)? Spring AMQP will automatically reject and requeue all the messages the consumer failed to consume (i.e. threw an exception) except for those poisonous messages which are discarded. [Spring AMQP docs](https://docs.spring.io/spring-amqp/reference/html/_reference.html#exception-handling) explains which failures are treated as poisonous message.
It is recommended that you configure the queue with [dead-letter exchange](https://www.rabbitmq.com/dlx.html) so that they are not entirely lost.

Be aware that a poisonous message originated due to some business exception will be constantly delivered draining lots of resources in the broker, in the network and in the consumer application.
Here we have 2 options:
- Either configure the `SimpleMessageListenerContainer` to always discard messages (`SimpleMessageListenerContainer.DefaultRequeueRejected(false)`).
- Or instead throw `AmqpRejectAndDontRequeueException`
- Or implement your own `ErrorHandler` maybe extending the current implementation `ConditionalRejectingErrorHandler`.

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


A second advise is to detect when the connection is blocked and use it in the application. For instance, we could implement a back-pressure mechanism. Publisher will not send and block but fail-fast and report upstreams that it is not available. Or record this situation via its health status which we can expose it thru the actuator `/health` endpoint.   

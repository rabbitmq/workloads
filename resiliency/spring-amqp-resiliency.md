# Spring AMQP Resiliency workshop

The goal of this workload is to provide guidance to developers on how to write [Spring AMQP](https://docs.spring.io/spring-amqp/reference/html/) applications which are resilient to failures.

The type of failures we are going to handle are:
- RabbitMQ node goes down expectedly (e.g. during a rolling upgrade) or unexpectedly
- Entire RabbitMQ cluster goes down
- Connection fails or abruptly closed by an intermediary such as a load balancer
- Channel-level exceptions  
- Fatal consumer errors (e.g. poisonous message)


**Table of Content**



## Skeleton Spring AMQP client

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

> **Since RabbitMQ for PCF 1.16**, the *hostnames* field and *uris* contains a single hostname, even if there is more than one RabbitMQ node. For more information, check out the [official docs](https://docs.pivotal.io/rabbitmq-cf/1-16/use.html#bosh-dns).

### Skeleton application

[resilient-skeleton-spring-rabbitmq](resilient-skeleton-spring-rabbitmq) folder contains the minimum code that demonstrate RabbitMQ bootstrapping for a Spring Boot application.

Before waking through this skeleton application it is worth mentioning the requirements that shaped the application as it is. These are:
- Our application may potentially require another RabbitMQ service instance
- also our application most likely will need custom RabbitMQ ConnectionFactory settings like `spring.rabbitmq.connection-timeout`

These two requirements tells us that we cannot use [Spring Auto-configuration provided by the Java Buildpack](https://docs.cloudfoundry.org/buildpacks/java/configuring-service-connections/spring-service-bindings.html#auto) to automatically bootstrap RabbitMQ ConnectionFactory for us. Instead we need to ask that mechanism to back off.

### When Auto Configuration is a viable option

In the contrary, if your application does not need any custom RabbitMQ configuration and your application only ever gets bound one RabbitMQ service instance then all we need to do is add the appropriate Spring AMQP dependency. We don't need to include `spring-cloud-spring-service-connector` or `spring-cloud-cloudfoundry-connector` because [Spring Auto-configuration provided by the Java Buildpack](https://docs.cloudfoundry.org/buildpacks/java/configuring-service-connections/spring-service-bindings.html#auto) does it for us.

### When Auto Configuration is not a viable option

Going back to the skeleton application the key classes are:
- [AMQPConnectionFactoryConfig](resilient-skeleton-spring-rabbitmq/src/main/java/com/pivotal/resilient/AMQPConnectionFactoryConfig.java) - This is where we ask Spring Cloud Connectors for an RabbitMQ ConnectionFactory instance and expose it as a `@Bean` so that we can use it from other parts of our Spring application. In fact, we build 2 separate ConnectionFactories, one for publishing and another one for consuming messages. This is a very good practice that we will discuss later on.

- [AMQPResourceConfig](resilient-skeleton-spring-rabbitmq/src/main/java/com/pivotal/resilient/AMQPResourceConfig.java) - This is where we build RabbitMQ related objects like a RabbitTemplate that uses the `publisher` connection factory or a rabbitListenerContainerFactory used for methods annotated with `@RabbitListener` configured with the `consumer` connection factory.
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
- `VCAP_APPLICATION` with the value `='{"instance_id": "0001", "name": "demo", "space_id": "flamingo"}'`
- `VCAP_SERVICES` with the value copied from either [src/main/resources/cluster.json](resilient-skeleton-spring-rabbitmq/src/main/resources/cluster.json) or  [src/main/resources/singleNode.json](resilient-skeleton-spring-rabbitmq/src/main/resources/singleNode.json)

### RabbitMQ Metrics

Although it is not strictly necessary to talk about metrics at this stage where we only care about the minimum logic to connect to RabbitMQ, we thought that monitoring metrics was such a basic functionality that it deserved to be done now.

If you run the command below against the skeleton application running [locally](#To-run-it-locally) it returns all the metrics emitted the Java RabbitMQ client:
```
curl -s localhost:8080/actuator/metrics | jq -r '.names[]' | grep rabbitmq | sort

rabbitmq.acknowledged
rabbitmq.acknowledged_published
rabbitmq.channels
rabbitmq.connections
rabbitmq.consumed
rabbitmq.failed_to_publish
rabbitmq.not_acknowledged_published
rabbitmq.published
rabbitmq.rejected
rabbitmq.unrouted_published

```

If we ran the command below we would get back the value `2`.
```
curl localhost:8080/actuator/metrics/rabbitmq.connections | jq '.measurements[].value'

2
```

And to get the connections opened from the `producer` connectionFactory, we run
`curl localhost:8080/actuator/metrics/rabbitmq.connections?tag=name:producer | jq '.measurements[].value'` which produces the value `1`.


We have tagged the metrics so that we can identify the originator. These are the tags and [here](resilient-skeleton-spring-rabbitmq/src/main/java/com/pivotal/resilient/CloudConfig.java#L59-L65) you can find where we set them up in the code.
- tag `cf-app-name` = `VCAP_APPLICATION.name`
- tag `cf-app-id` = `VCAP_APPLICATION.instance_id`
- tag `cf-space-id` = `VCAP_APPLICATION.space_id`
- tag `app-name` = `spring.application.name`

e.g.
```
{
  "name": "rabbitmq.connections",
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

This is a brief demonstration on how we can monitor RabbitMQ client running within our applications.

### Monitoring skeleton application with Datadog

It would be great to be able to present the metrics discussed in the previous section in a dashboard together with RabbitMQ server metrics so that we can reason about our architecture from a single place. In this section, we are going to go through what we need to do in order to get our application to send metrics to Datadog and how to find those metrics in Datadog. We are not going to build any dashboard with those metrics yet.

To know more about how to monitor RabbitMQ server with Datadog and how even how to install it, check out [Monitoring RabbitMQ for PCF using Datadog](https://github.com/MarcialRosales/rabbitmq-monitoring-guide/tree/master/datadog).

First of all, we need to build the application with a different Maven profile so that it includes `micrometer-registry-datadog` dependency.
  > The pom.xml has a [custom build profile](resilient-skeleton-spring-rabbitmq/pom.xml#L68-L76) which adds `micrometer-registry-datadog` dependency

  ```
  mvn -P datadog clean install
  ```

To run it locally we need to specify the Datadog API_KEY:
  ```
  export DATADOG_API_KEY="my-key"
  ./run.sh --management.metrics.export.datadog.api-key=$DATADOG_API_KEY
  ```

  > Notice that run.sh activates the spring profile `datadog` in all cases. Although it only takes effect when we add the appropriate dependency.

  It should print out the following statement every 20seconds:
  ```
  019-04-15 11:43:19.613 DEBUG 54120 --- [trics-publisher] i.m.datadog.DatadogMeterRegistry         : successfully sent 94 metrics to datadog
  ```

Once we see the the above logging statement, we should be able to see the metrics in *Datadog Metrics explorer* as shown below.
![client metrics](assets/client-metrics.png)
> In the screenshot we are searching for metrics coming from a given application name and running within a Cloud Foundry space.

And finally, to deploy it in Cloud Foundry we run the following command:
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
export VCAP_APPLICATION='{"instance_id": "0001", "name": "demo", "space_id": "flamingo"}'
export VCAP_SERVICES="$(cat src/main/resources/cluster.json)"
```
> In addition to the `name` attribute in `VCAP_APPLICATION` we use the other attributes to
> uniquely identify the metrics emitted by the appication

To run the application within IntelliJ, copy the file `ResilientSpringRabbitmqApplication.xml` to folder `.idea/runConfigurations`(within `workloads/resiliency/resilient-spring-rabbitmq` folder)  

Either `src/main/resources/cluster.json` or `ResilientSpringRabbitmqApplication.xml` have the credentials to a 3 node RabbitMQ cluster running in localhost on ports 5672, 5673 and 5674 respectively.  

### To run a 3-node cluster locally

Follow these steps to run a 3-node cluster locally. You will need Docker installed in your machine:

```
git clone https://github.com/harbur/docker-rabbitmq-cluster
cd docker-rabbitmq-cluster
docker-compose up -d
```

The application is configured to connect to RabbitMQ cluster with a user called `test` and password `test`. The application will not be able to connect right after we bring the cluster up because it comes with the default user `guest:guest`.

This is on purpose so that we can reproduce situations where we cannot connect to a cluster due to access control failures.
To add the user to the running cluster follow these steps:
```
$ docker exec -it docker-rabbitmq-cluster_rabbit1_1 /bin/bash -c 'rabbitmqctl add_user test test; rabbitmqctl set_permissions test ".*" ".*" ".*"; rabbitmqctl set_user_tags test administrator'
```

### To deploy the application to Cloud Foundry

First we need to provision a RabbitMQ cluster. [RabbitMQ for PCF](https://docs.pivotal.io/rabbitmq-cf/1-14/index.html) have 2 service offerings.

The [pre-provisioned](https://docs.pivotal.io/rabbitmq-cf/1-14/deploying.html) offering which consists of  a multi-tenant RabbitMQ cluster with a **ha-proxy** in front of it. Applications will connect to RabbitMQ via a single address, the **ha-proxy** address.

> This offering is not recommended a production use-case.

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
> Replace <plan-name> with one of the plans available in your Cloud Foundry foundation. Use the command `cf marketplace`, to list the available plans.

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

The application's code base has changed since that commit so that we could achieve things like:
- use Spring AMQP configuration (`spring.rabbitmq.*`) from the environment (such as `application.yml`) to customize ConnectionFactories among other objects
- expose RabbitMQ metrics

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
  - operator is performing a rolling upgrade (e.g. Upgrade *PCF RabbitMQ 1.13.8 / RabbitMQ 3.7.7* to *PCF RabbitMQ 1.13.8 / RabbitMQ 3.7.8*)

If we are using **On-Demand RabbitMQ for PCF** offering, our application gets the address of every node in the cluster. Spring AMQP will automatically try to connect to next node in the list.

> **Since RabbitMQ for PCF 1.16**, the application gets a single DNS entry, which resolves each IP of the nodes.

If we are using **Pre-Provision RabbitMQ for PCF** offering, our application only gets one address, the HA-proxy's address. Spring AMQP will automatically try to connect to that single address and the ha-proxy will connect it to an available node, if any.

**TL;DR** : This is as far as the connection recovery is concerned. Spring AMQP will automatically recover the connection if there is an available node. However, our application may still crash due to buggy logic in the consumer and/or producer.

In the next sections we are going to address the resiliency of consumers when we lose the connection. We contemplate the following 2 scenarios: one with durable queues and another with non-durable queues, both non-mirrored.

#### RabbitMQ node with non-mirrored durable queues becomes unavailable
When this situation occurs, although the RabbitMQ Cluster knows about the queue existence, as far as the applications is concerned the queue does not exist and hence they cannot use it. But even worse, they cannot declare it either.

The producer will not experience any connection/AMQP failures. However, published messages may be lost.
How do we prevent message loss in this case?
- Either we use [mandatory](https://www.rabbitmq.com/amqp-0-9-1-reference.html#basic.publish.mandatory) flag. If we use this flag, RabbitMQ will inform the producer if it could not send the message to any queue.
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
Depending on which version we are upgrading to, we have to choose the right upgrade procedure. These are the 2 possible upgrade procedures:
- [Rolling upgrade](https://www.rabbitmq.com/upgrade.html#multiple-nodes-upgrade) - Perform upgrades without entire cluster downtime. A rolling upgrade is when nodes are stopped, upgraded and restarted one-by-one, with the rest of the cluster still running while each node is being upgraded.
- [Full-Stop upgrade](https://www.rabbitmq.com/upgrade.html#full-stop-upgrades) - If rolling upgrades are not possible, the entire cluster should be stopped, then restarted. This is referred to as a full stop upgrade.

When we do a rolling upgrade on a **Pre-Provisioned RabbitMQ for PCF** cluster, applications will suffer some downtime. For instance, in a 3 node cluster with 1 ha-proxy, it has been observed around 5 minutes downtime. This is because RabbitMQ for PCF upgrades not only RabbitMQ cluster but also the Ha-Proxy server.

When we do a rolling upgrade on a **On-demand RabbitMQ for PCF** cluster, applications will suffer a sub-second downtime. However, applications should be written to handle connection drops so that they can reconnect when the RabbitMQ Cluster is upgraded.

As far as the applications are concerned, they only see nodes going down or the entire cluster going down. And these 2 failure scenarios have already been handled in the previous sections.

#### Considerations when performing a rolling upgrade in relation to service availability and message resiliency
Up until now we have made our applications resilient in the event of an upgrade, i.e it does not crash when it happens and when the upgrade completes, it keeps functioning. However there are two new aspects we have to contemplate which are **service availability** and **message resiliency**.

If a queue is not available and our application cannot declare it either then we have lost **service availability**. For as long as the queue is not available, consumer applications will not be able to consume. Furthermore, producer applications -if they use the right configuration- they wont be able to successfully publish messages either.

We can configure RabbitMQ to favour availability over consistency or on the contrary, depending on our requirements. Consistency refers to guaranteed message delivery (i.e. no message loss). The following two configurations shows how we can affect service availability and message resiliency.

  - Manual synchronization (`ha-sync-mode: manual`) and `ha-promote-on-shutdown: always` will lose messages generated during the upgrade process. However with this set up we achieve the shortest service downtime as the queue master is election is quicker.
  - Manual synchronization (`ha-sync-mode: manual`) and `ha-promote-on-shutdown: when-synced` will prevent message loss at the cost of service downtime. The queue will be unavailable until the node, hosting the master queue, is upgraded (i.e. stopped, upgraded and started). The downtime is shorter compared to doing a full synchronization on a very large queue (full synchronization will trigger when RabbitMQ promotes a new master before upgrading the node hosting the master queue).

**TL:DR**
1) After a rolling upgrade using manual synchronization we dot no have redundancy of messages which existed prior the upgrade and/or were sent during the upgrade. We need to asses whether we take the risk or whether we should trigger a manual synchronization.

2) Having large queues and automatic synchronization can cause large service downtime. We can increase the size of the synchronization batch (`ha-sync-batch-size: 4096`) to speed up the synchronization. However synchronizing lots of large messages can be even worse. So we have to be careful when adjusting this setting.

3) With *Pause minority* partition clustering technique we have to use *Automatic synchronization* if we do not want to lose messages. *Ignore* is the only partition clustering technique we can use to prevent message loss.

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


# Spring Cloud Stream Patterns

## How to deploy RabbitMQ

To deploy a standalone server, run:
```
deploy-rabbit
```
> It will deploy a standalone server on port 5672

To deploy a cluster of 3 nodes, run:
```
deploy-rabbit-cluster
```
> It will deploy a cluster with nodes listening on 5673, 5674, 5575


## Scenarios

By default, the application is configured to connect to a cluster (application.yml)
  which is configured on the configuration file application-cluster.yml.

To launch the application against a single standalone server, edit application.yml
and remove `cluster` as one of the included Spring profiles.


### Scenario 1 - Resilient producer/consumer with unreliable producer

In this scenario, we have a producer service (ScheduledTradeRequester) and a
consumer service (TradeLogger).

**Resiliency**

Both services are resilient to connection failures (1, 2, 3) in the
sense that the application does not crash and both producer and consumer recover
from it.

TODO provide details with regards retry max attempts and/or frequency if there are any

**Verify resiliency against failure 1 - RabbitMQ is not available when application starts**

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

**Verify resiliency against failure 2 - Restart a cluster node**

Pick the node where application is connected and the queue declared, say it is `rmq0`

1. Restart `rmq0` node:
  ```
  docker-compose start rmq0
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

**Verify resiliency against failure 3 - Rolling restart of cluster nodes**

1. Rolling restart:
  ```
  ./rolling-restart
  ```
2. Wait until the script terminate to check how producer and consumer is still working
(i.e. sending and receiving)

**Verify resiliency against failure 4 - Kill producer connection (repeatedly)**

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


**Verify resiliency against failure 5 - Kill consumer connection (repeatedly)**

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

> We noticed that the consumer connection creates 2 channels after it recovers the
connection rather than just one. However, it does not keep opening further channels should it
recovered from additional connection failures.

**Verify resiliency against failure 5 - Block producers**

We are going to force RabbitMQ to trigger a memory alarm by setting the high water mark to 0.
This should only impact the producer connections and let consumer connections carry on.



**Zero Guarantee of delivery on the producer**

- Producer does not use publisher confirmation. If RabbitMQ fail to deliver a message
to a queue due to an internal issue or simply because the RabbitMQ rejects it, the
producer is not aware of it.
- Producer does not use mandatory flag hence if there are no queues bound to the exchange
associated to the outbound channel, the message is lost. The exchange is not configured with
an alternate exchange either.
- When producer fails to send (i.e. send operation throws an exception) a message,
the producer is pretty basic and it does not retry it.

**Some Guarantees of delivery on the consumer**

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

**Verify guarantee of delivery failure - Some messages are lost**

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

**Recommendation**
- Ensure your ha policies do not include anonymous queues
- If we cannot afford to a single messages then we cannot use anonymous consumers



## Appendix - How to test failures

All the commands mentioned below assumes you are running from within `docker` folder.

### 1. RabbitMQ is down

To ensure your standalone RabbitMQ server is not running before starting your application.
`destroy-rabbit` or `docker stop rabbitmq-5672`
> These two commands assumes you are running RabbitMQ on its default port

To ensure your cluster is not running, run:
`destroy-rabbitmq-cluster` or `docker-compose stop`


### 2. Restart standalone server or a single cluster node

To restart standalone server, run:
```
deploy-rabbit
```

To restart an individual cluster node such as `rmq0`, run:
```
docker-compose restart rmq0
```

### 3. Rolling restart of all cluster nodes

To perform a rolling restart of the cluster nodes, run:
```
rolling-restart
```

### 4. Kill producer connection (repeatedly)

To be done

### 5. kill consumer connection (repeatedly)

To be done

### 6. Block producers (due to alarm, or high water mark to 0)

To be done

### 7. Pause node (due to network partition or cluster running in minority)

```
rabbitmqctl set high water mark to 0
```

### 8. Unresponsive network connection

TODO Configure toxyproxy so that it drops messages

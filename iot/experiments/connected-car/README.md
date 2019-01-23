# Use case - Connected Car

Vehicles talks to backend over MQTT over TLS. Each vehicle is an MQTT client with a unique ID.
> It’s common practice to use the 36 character Universal Unique Identifier (UUID) or other unique client information as the client ID. For example, the MAC address of the network module or the device serial number

They may also use certificates for authentication.
> If you provision IoT devices, X509 client certificates can be a very good source for authenticating clients on the MQTT broker.

They may also authenticate using username/password although it travels on-clear, the communication is over TLS.
> A common way to confirm if a client can access the MQTT broker is to validate the username/password and the client ID that is correct for that credential combination. It is also possible to ignore the username/password and just authenticate against the client ID; however, this method is not a good security practice.

Each vehicle can publish and subscribe to the vehicle related messages only. Vehicles should not be able to publish/consume messages to/from other vehicles.
Vehicle subscribes for events using its *Client Id* and connects using *Persistent Session (CleanSession = false)* and *QoS1*. This means RabbitMQ keeps events around even when the vehicle is not connected and events are only removed after the vehicle has acked it. The subscription topic follows this pattern `vehicle`/`<client_id>`.
This subscription connection uses LWT (Last Will and Testament) which notifies when the connection drops ungracefully.


Vehicle sends non-critical events using *QoS0* like location information and similar.
Vehicle sends critical events using *QoS1* like alarms.

End-users (e.g. via a mobile app) connect to backend via MQTT too. They authenticate using OAuth 2.0.

Backend connect vehicles with end-users and let them exchange messages eventually.

*Requirements*
- Least service downtime. Minimize impact of RabbitMQ nodes restarts.
- Dynamically scale up/down with sustained message flow and new connection requests
- Access control at the MQTT Topic level
- Exactly-once delivery (how to deal with duplicates?)
- QoS1/2
- Throttling - Limit the total incoming bytes per second and the total outgoing bytes per second independently, globally or per-client basis.

## Configure RabbitMQ

- [Enable MQTT plugin](experiments/connected-car/conf/enabled_plugins)
- Explicitly [configured](experiments/connected-car/conf/rabbitmq.conf) the listening port
  ```
  mqtt.listeners.tcp.1 = 1883
  ```
- Explicitly disable anonymous access
  ```
  mqtt.allow_anonymous = true
  ```
- Explicitly configure the default *vhost* used for all MQTT clients
  ```
  mqtt.vhost = /
  ```
- Explicitly disable `subscription_ttl` option otherwise messages would be lost after 24 hours (Check out https://www.rabbitmq.com/mqtt.html#stickiness for more information)
  ```
  mqtt.subscription_ttl = undefined
  ```

## Start single node RabbitMQ

1. Start RabbitMQ with MQTT and Management plugin enabled
  ```
  ./start-rabbitmq
  ```
2. To check the management UI is available run:
  ```
  curl  -u guest:guest http://localhost:15672/api/overview | jq .
  ```
3. To check mqtt plugin is running in RabbitMQ:
  ```
  docker exec -it rabbitmq-1 rabbitmqctl status
  ```
  We should expect `{rabbitmq_mqtt,"RabbitMQ MQTT Adapter","3.7.8"},` and `{mqtt,1883,"::"},`.

## Simulate connected car use case

1. Create user for `vehicle-1` (password: `guest`).
  ```
  ./put vehicle-1.json
  ```
1. `vehicle-1` subscribes for events on `vehicle-1` *topic*
  ```
  ./start-consumer vehicle-1 -C mqtt \
    -u vehicle-1 -i "v1" -P guest -h rabbitmq -p 1883 \
    -t "vehicle/v1" --qos 1 --no-clean \
    -v
  ```

  We can check the established connection in RabbitMQ via `rabbitmqctl` or *management UI*
  ```
  $ rabbitmqctl list_mqtt_connections
  Listing MQTT connections ...
  v1	172.17.0.3:50368 -> 172.17.0.2:1883
  ```
  ![mqtt connection](assets/subscription-conn.png)

  And we can also check the queue created for the subscription which is *durable* and it is not *auto-delete*:
  ![subscription queue](assets/subscription-queue.png)

5. Backend sends message to `vehicle-1` on `vehicle-1` *topi*
  ```
  ./publisher backend -C mqtt -u backend -p password -h rabbitmq -p 1883 -t "vehicle-1" -m "Your car is connected"
  ./publisher backend -C mqtt -u backend -p password -h rabbitmq -p 1883 -t "vehicle-1" -m "Door is unlocked"
  ```
  Check the consumer logs
  ```
  docker logs vehicle-1
  ```
  It should print out
  ```
  Your car is connected
  Door is unlocked
  ```
6. Stop vehicle-1
  ```
  ./stop-consumer vehicle-1
  ```
  After we stop the consumer, its queue disappears.

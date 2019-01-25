# Disaster Recovery Reference Architecture

Requirements summary:
[X] Messages cannot be lost
[] Messages must be replicated
[] Zero service downtime
[] Messages cannot be delivered more than once
[] Message throughput ?
[] Eventual consistency (i.e. DR site may not have all messages generated from the main site. They are not lost though)

## Set up

1. We are going to deploy a single RabbitMQ server in a Docker container. For this reason we need to have docker installed.

2. We are going to use an Ubuntu docker image which is not yet available in Docker hub (pending to approve PR). Therefore, we are going to build the docker image locally.

  ```
  git clone git@github.com:rabbitmq/rabbitmq.git
  cd rabbitmq
  git checkout compile-openssl-otp
  git pull
  cd 3.7/ubuntu/management
  docker build --build-arg PGP_KEYSERVER="pgpkeys.co.uk" -t rabbitmq-mgt-ubuntu .
  ```

3. Start RabbitMQ server with a local `data` folder created if it does not exist yet. This is so that we don't loose the messages when we stop RabbitMQ server container.
  ```
  ./start-rabbitmq
  ```

4. Start producer configured with all the guarantees to not lose 100 messages. The second command sends 10 messages per second.
  ```
  ./start-producer
  or
  ./start-producer --rate 10
  ```
5. Start consumer configured with all the guarantees to not lose messages. The first command processes messages at maximum rate. The second command processes 5 messages per second.
  ```
  ./start-consumer
  or
  ./start-consumer --consumer-rate 5
  ```
6. To check the current depth of the `transactions` queue, run the following command:
`curl -s -u guest:guest localhost:15672/api/queues/%2F/transactions | jq .messages`

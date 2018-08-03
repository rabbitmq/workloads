# Environment setup

[Intel Optane](https://www.acceleratewithoptane.com/access/) running Ubuntu 16.04 LTS & Linux 4.13.0-41-generic SMP x86_64.

Each host had two [Intel Xeon Gold 6142](https://ark.intel.com/products/120487/Intel-Xeon-Gold-6142-Processor-22M-Cache-2_60-GHz) & 20Gbps Bonded Network (2 × 10Gbps w/ LACP).

We were running the following versions:

1. RabbitMQ v3.7.7 on Erlang/OTP v21.0.3
1. [PerfTest v2.2.0.M1](https://github.com/rabbitmq/rabbitmq-perf-test/releases/download/v2.2.0.M1/rabbitmq-perf-test-2.2.0.M1-bin.tar.gz) on Open JDK v1.8.0_171, RabbitMQ AMQP Java Client v5.3.0

```
apt install apt-transport-https
wget -O - 'https://dl.bintray.com/rabbitmq/Keys/rabbitmq-release-signing-key.asc' | apt-key add -
cat > /etc/apt/sources.list.d/rabbitmq.list <<EOF
deb https://dl.bintray.com/rabbitmq/debian $(lsb_release -c | awk '{ print $2 }') rabbitmq-server-v3.7.x
deb https://dl.bintray.com/rabbitmq/debian $(lsb_release -c | awk '{ print $2 }') erlang-21.x
deb https://dl.bintray.com/rabbitmq/debian $(lsb_release -c | awk '{ print $2 }') elixir
EOF
apt update
apt upgrade -y
apt install vim-nox dstat htop vnstat tmux git-core -y
apt install rabbitmq-server -y
rabbitmqctl add_user admin PASSWORD
rabbitmqctl set_user_tags admin administrator
rabbitmqctl set_permissions admin '.*' '.*' '.*'
rabbitmq-plugins enable rabbitmq_management
```

```
apt update
apt upgrade -y
apt install vim-nox dstat htop vnstat tmux git-core -y
apt install openjdk-8-jre -y
wget https://github.com/rabbitmq/rabbitmq-perf-test/releases/download/v2.2.0.M1/rabbitmq-perf-test-2.2.0.M1-bin.tar.gz
tar zxvf rabbitmq-perf-test-2.2.0.M1-bin.tar.gz
cd rabbitmq-perf-test-2.2.0.M1
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
```

```
mkfs.ext4 -F /dev/nvme1n1
mkdir -p /rabbitmq
mount /dev/nvme1n1 /rabbitmq
chmod a+w /rabbitmq
```

We started with the following configuration:

* single non-durable queue
* 1 producer with 1 connection & 1 channel, no publisher confirms
* 1 consumer with 1 connection & 1 channel, automatic message acknowledgements
* 1000 bytes AMQP message body

```
bin/runjava com.rabbitmq.perf.PerfTest \
--autoack \
--interval 15 \
--size 1000 \
--queue-args 'x-max-length=10000' \
--routing-key perf-test \
--uri 'amqp://admin:pass@10.99.244.3:5672/%2F' \
--metrics-prometheus \
--metrics-tags 'type=publisher,type=consumer,deployment=low-latency' \
--queue-pattern 'perf-test-%d' \
--queue-pattern-from 0 \
--queue-pattern-to 0 \
--consumers 1 \
--producers 1 \
--rate 100
```

## How does publish rate affect message latency?

|      Publish Rate |  Max 99th |  Max 95th |  Max 75th |
|                -: |        -: |        -: |        -: |
|         100 msg/s |   0.52 ms |   0.48 ms |   0.45 ms |
|        1000 msg/s |   0.59 ms |   0.49 ms |   0.41 ms |
|       10000 msg/s |   3.14 ms |   0.85 ms |   0.55 ms |
|       20000 msg/s |   3.53 ms |   1.18 ms |   0.78 ms |
|       30000 msg/s |   4.71 ms |   1.31 ms |   0.98 ms |
|       40000 msg/s |  13.10 ms |   1.57 ms |   1.01 ms |
|       50000 msg/s |  18.87 ms |   1.90 ms |   1.11 ms |
|       60000 msg/s |  25.16 ms |   2.22 ms |   1.37 ms |
| 63000 msg/s (MAX) | 335.54 ms | 318.76 ms | 301.98 ms |

At 63k msg/s the producer channel is in a constant flow state.

We suspect that the flow is artificial, the consumer is able to cope with the message rate, but the queue process is slowing down the producer unnecessarily.

## How does credit flow affect message latency?

Publish rate: 63000 msg/s

|    Credits | Max 99th | Max 95th | Max 75th |
|         -: |       -: |       -: |       -: |
|  {100, 50} | 37.74 ms |  5.76 ms |  1.31 ms |
| {200, 100} | 29.36 ms |  2.09 ms |  1.24 ms |
| {400, 200} | 25.16 ms | 20.97 ms |  1.63 ms |

## How do multiple consumers affect message latency?

Publish rate: 10000 msg/s

| Consumers | Max 99th | Max 95th | Max 75th |
|        -: |       -: |       -: |       -: |
|         1 |  3.93 ms |  0.85 ms |  0.59 ms |
|         2 |  3.01 ms |  0.75 ms |  0.47 ms |
|         5 |  2.75 ms |  0.65 ms |  0.44 ms |
|        10 |  1.90 ms |  0.65 ms |  0.39 ms |
|       100 |  1.24 ms |  0.65 ms |  0.39 ms |
|       500 |  0.98 ms |  0.65 ms |  0.41 ms |

Publish rate: 50000 msg/s ?

## How do multiple producers affect message latency?
## How do multiple queues affect message latency?

## What are the effects of running multiple queues?

Publish rate: 10000 msg/s

We use 1 producer & 1 consumer per queue, so there will be multiple producers, consumers & queues.

Each producer and each consumer use their own connection & channel.

| Queues<br /> Producers<br /> Consumers | Msg/s per queue | Max 99th | Max 95th | Max 75th |
| -:                                     | -:              | -:       | -:       | -:       |
| 1                                      | 10000           | 2.88ms   | 1.56ms   | 0.942ms  |
| 2                                      | 5000            | 1.3ms    | 0.844ms  | 0.68ms   |
| 5                                      | 2000            | 1.11ms   | 0.909ms  | 0.5ms    |
| 10                                     | 1000            | 1.11ms   | 0.844ms  | 0.434ms  |
| 100                                    | 100             | 2.22ms   | 0.549ms  | 0.369ms  |
| 500                                    | 20              | 7.3ms    | 4.4ms    | 0.549ms  |
| 1000                                   | 10              | 10ms     | 4.2ms    | 0.483ms  |

We believe that the reason why 2 producers & 2 consumers have half the latency of 1 producer & consumer is because the Erlang TCP port driver needs to copy data from the VM to the port driver.

## What are the effects of different queue types?

Message body size: 10000 bytes

### Publish rate: 10000 msg/s

|  Queue type | Max 99th | Max 95th | Max 75th |
|          -: |       -: |       -: |       -: |
| non-durable |  4.19 ms |  1.04 ms |  0.88 ms |
|     durable |  1.43 ms |  1.04 ms |  0.91 ms |
|        lazy |  1.83 ms |  1.17 ms |  0.97 ms |

### Publish rate: 20000 msg/s

|  Queue type | Max 99th | Max 95th | Max 75th |
|          -: |       -: |       -: |       -: |
| non-durable |  3.66 ms |  1.37 ms |  0.88 ms |
|     durable |  2.22 ms |  1.30 ms |  0.84 ms |
|        lazy |  3.14 ms |  1.56 ms |  1.11 ms |

### Publish rate: 30000 msg/s

|  Queue type | Max 99th | Max 95th | Max 75th |
|          -: |       -: |       -: |       -: |
| non-durable | 13.62 ms |  2.61 ms |  1.43 ms |
|     durable |  7.86 ms |  2.09 ms |  1.11 ms |
|        lazy |  5.23 ms |  2.09 ms |  1.01 ms |

### Publish rate: 40000 msg/s

|  Queue type | Max 99th | Max 95th | Max 75th |
|          -: |       -: |       -: |       -: |
| non-durable | 33.55 ms |  6.81 ms |  1.96 ms |
|     durable | 39.84 ms | 11.53 ms |  3.79 ms |
|        lazy | 50.32 ms |  8.90 ms |  3.01 ms |

### Publish rate: 50000 msg/s

| non-durable | 113.24 ms | 92.27 ms | 83.88 ms |
|     durable | 109.04 ms | 96.46 ms | 92.27 ms |
|        lazy | 100.66 ms | 96.46 ms | 83.88 ms |

## What are the effects of publisher confirms?

* multi-publisher confirms

## What are the effects of consumer acks?

* multi-acks

## What are the effects of exchange type?

* direct
* topic
* fanout
* headers

## What are the effects of queue mirroring?

### Publish rate: 10000 msg/s

| Queue mirrors | Max 99th | Max 95th | Max 75th |
|            -: |       -: |       -: |       -: |
|             1 |  3.40 ms |  0.95 ms |  0.62 ms |
|             2 |  2.75 ms |  0.75 ms |  0.52 ms |
|             3 |  3.93 ms |  0.88 ms |  0.59 ms |

### Publish rate: 20000 msg/s

| Queue mirrors | Max 99th | Max 95th | Max 75th |
|            -: |       -: |       -: |       -: |
|             1 |  2.75 ms |  1.18 ms |  0.78 ms |
|             2 |  7.34 ms |  1.18 ms |  0.75 ms |
|             3 | 17.82 ms |  1.31 ms |  0.82 ms |

### Publish rate: 30000 msg/s

| Queue mirrors | Max 99th | Max 95th | Max 75th |
|            -: |       -: |       -: |       -: |
|             1 | 11.53 ms |  1.31 ms |  0.88 ms |
|             2 | 19.92 ms |  5.24 ms |  0.98 ms |
|             3 | 39.84 ms | 20.97 ms |  1.24 ms |

## What are the effects of RabbitMQ Management?

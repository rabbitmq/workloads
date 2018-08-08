# Environment setup

[Intel Optane](https://www.acceleratewithoptane.com/access/) running Ubuntu 16.04 LTS & Linux 4.13.0-41-generic SMP x86_64.

Each host had two [Intel Xeon Gold 6142](https://ark.intel.com/products/120487/Intel-Xeon-Gold-6142-Processor-22M-Cache-2_60-GHz) & 20Gbps Bonded Network (2 × 10Gbps w/ LACP).

Network latency measured between the client and the broker:

```
# fping -c 100 PRIVATE_IP

client -> broker : xmt/rcv/%loss = 100/100/0%, min/avg/max = 0.06/0.12/0.17
broker -> client : xmt/rcv/%loss = 100/100/0%, min/avg/max = 0.04/0.07/0.16
```

We were running the following versions:

1. RabbitMQ v3.7.7 on Erlang/OTP v21.0.4
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

## LEARNINGS

Pay close attention to your network latency, since your RabbitMQ messages cannot be faster than your network.
In cloud environments we have observed a large difference in network latency which has a direct impact on message latency.
Using a tool such as fping to monitor network latency between your clients and your broker is highly recommended.

We assume that the RabbitMQ broker has dedicated CPUs. If you must run clients on the same host as the broker, use CPU pinning so that the broker gets dedicated CPUs.

When running clients in containers and running many different containers on the same CPUs, we've observed clients contending for CPU which results in higher message latency.

A fast disk, such as the Intel Optane P4900X, results in lazy queues having the least amount of message latency.
The reason for this is that lazy queues use simpler data structures internally, so if the disk is fast enough, lazy queues outperform the default queue type, regardless whether it's in-memory (non-durable) or durable.

Halving credit flow values reduces message latency in the 95th percentile by a factor of 10, and increases it slightly in the 99th percentile.

As expected, increasing the publish rate increases the message latency (we're trading latency for throughput).
At the same time, increasing the number of publishers and consumers lowers the message latency.

A higher number of producers and consumers generally results in lower message latency, up to a point.
Once the queue gets saturated and back-pressure gets applied, message latency increases.

Every queue mirror that gets added degrades the message latency by factor of 2.
If message latency is important, do not use queue mirroring.

If every consumer and producer have their own queue, as well as their own connection and channel, they perform better.

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

### Publish rate: 10000 msg/s

| Consumers | Max 99th | Max 95th | Max 75th |
|        -: |       -: |       -: |       -: |
|         1 |  4.98 ms |  1.01 ms |  0.65 ms |
|         2 |  4.45 ms |  0.82 ms |  0.52 ms |
|         5 |  2.09 ms |  0.68 ms |  0.44 ms |
|        10 |  3.14 ms |  0.68 ms |  0.41 ms |
|       100 |  1.18 ms |  0.62 ms |  0.41 ms |
|       500 |  1.11 ms |  0.65 ms |  0.42 ms |

### Publish rate: 20000 msg/s

| Consumers | Max 99th | Max 95th | Max 75th |
|        -: |       -: |       -: |       -: |
|         1 |  1.50 ms |  1.24 ms |  0.82 ms |
|         2 |  1.37 ms |  0.95 ms |  0.68 ms |
|         5 |  4.45 ms |  0.82 ms |  0.55 ms |
|        10 |  6.03 ms |  0.75 ms |  0.50 ms |
|       100 | 10.48 ms |  0.82 ms |  0.55 ms |
|       500 |  4.19 ms |  0.78 ms |  0.55 ms |

### Publish rate: 30000 msg/s

| Consumers | Max 99th | Max 95th | Max 75th |
|        -: |       -: |       -: |       -: |
|         1 |  1.83 ms |  1.31 ms |  0.98 ms |
|         2 | 11.53 ms |  1.11 ms |  0.75 ms |
|         5 |  8.12 ms |  0.91 ms |  0.65 ms |
|        10 |  7.07 ms |  0.88 ms |  0.65 ms |
|       100 |  8.91 ms |  1.04 ms |  0.72 ms |
|       500 |  9.43 ms |  0.98 ms |  0.65 ms |

### Publish rate: 40000 msg/s

| Consumers | Max 99th | Max 95th | Max 75th |
|        -: |       -: |       -: |       -: |
|         1 | 13.63 ms |  1.57 ms |  1.11 ms |
|         2 | 13.10 ms |  1.24 ms |  0.91 ms |
|         5 |  1.70 ms |  1.11 ms |  0.78 ms |
|        10 | 16.25 ms |  1.18 ms |  0.82 ms |
|       100 |  1.70 ms |  1.37 ms |  1.01 ms |
|       500 | 22.02 ms |  1.37 ms |  0.85 ms |

### Publish rate: 50000 msg/s

| Consumers | Max 99th | Max 95th | Max 75th |
|        -: |       -: |       -: |       -: |
|         1 | 15.20 ms |  1.77 ms |  1.18 ms |
|         2 | 19.92 ms |  1.77 ms |  1.04 ms |
|         5 | 22.02 ms |  1.63 ms |  1.11 ms |
|        10 | 19.92 ms |  1.50 ms |  1.11 ms |
|       100 | 48.23 ms | 39.84 ms |  6.55 ms |
|       500 | 54.52 ms | 44.04 ms |  8.91 ms |

## How do multiple producers affect message latency?

### Publish rate: 10000 msg/s

| Producers | Max 99th | Max 95th | Max 75th |
|        -: |       -: |       -: |       -: |
|         1 |  2.09 ms |  0.85 ms |  0.62 ms |
|         2 |  4.45 ms |  0.65 ms |  0.47 ms |
|         5 |  1.57 ms |  0.41 ms |  0.29 ms |
|        10 |  2.22 ms |  0.41 ms |  0.23 ms |
|       100 |  1.63 ms |  0.52 ms |  0.34 ms |
|       500 |  1.44 ms |  0.49 ms |  0.29 ms |

### Publish rate: 20000 msg/s

| Producers | Max 99th | Max 95th | Max 75th |
|        -: |       -: |       -: |       -: |
|         1 |  4.19 ms |  1.18 ms |  0.78 ms |
|         2 |  5.76 ms |  0.72 ms |  0.52 ms |
|         5 |  6.29 ms |  0.55 ms |  0.39 ms |
|        10 |  5.50 ms |  0.41 ms |  0.29 ms |
|       100 |  3.01 ms |  0.41 ms |  0.27 ms |
|       500 |  1.63 ms |  0.41 ms |  0.27 ms |

### Publish rate: 30000 msg/s

| Producers | Max 99th | Max 95th | Max 75th |
|        -: |       -: |       -: |       -: |
|         1 |  3.40 ms |  1.31 ms |  0.98 ms |
|         2 |  7.34 ms |  0.98 ms |  0.72 ms |
|         5 |  0.85 ms |  0.65 ms |  0.47 ms |
|        10 | 10.48 ms |  0.44 ms |  0.32 ms |
|       100 |  8.91 ms |  0.32 ms |  0.22 ms |
|       500 | 13.63 ms |  0.32 ms |  0.23 ms |

### Publish rate: 40000 msg/s

| Producers | Max 99th | Max 95th | Max 75th |
|        -: |       -: |       -: |       -: |
|         1 | 18.87 ms |  1.57 ms |  1.18 ms |
|         2 |  1.96 ms |  1.11 ms |  0.78 ms |
|         5 | 18.87 ms |  0.88 ms |  0.50 ms |
|        10 | 20.97 ms |  0.52 ms |  0.37 ms |
|       100 | 19.92 ms |  0.31 ms |  0.22 ms |
|       500 | 19.92 ms |  0.34 ms |  0.24 ms |

### Publish rate: 50000 msg/s

| Producers | Max 99th | Max 95th | Max 75th |
|        -: |       -: |       -: |       -: |
|         1 |  7.34 ms |  1.77 ms |  1.18 ms |
|         2 | 12.58 ms |  1.24 ms |  0.85 ms |
|         5 | 31.45 ms |  0.88 ms |  0.62 ms |
|        10 | 39.84 ms |  0.62 ms |  0.42 ms |
|       100 | 35.65 ms |  0.59 ms |  0.27 ms |
|       500 | 16.77 ms |  0.62 ms |  0.34 ms |

## What are the effects of running multiple queues?

### Publish rate: 50000 msg/s

We use 1 producer & 1 consumer per queue, so there will be multiple producers, consumers & queues in total.

Each producer and each consumer use their own connection & channel.

| Queues<br /> Producers<br /> Consumers | Msg/s per queue | Max 99th | Max 95th | Max 75th |
|                                     -: |              -: |       -: |       -: |       -: |
|                                      1 |           50000 | 16.77 ms |  1.96 ms |  1.18 ms |
|                                      2 |           25000 |  3.01 ms |  1.24 ms |  0.85 ms |
|                                      5 |           10000 |  4.19 ms |  0.65 ms |  0.47 ms |
|                                     10 |            5000 |  1.11 ms |  0.47 ms |  0.34 ms |
|                                    100 |             500 |  0.37 ms |  0.23 ms |  0.19 ms |

## What are the effects of different queue types?

Since we do not want messages to be embedded in the message index, we increase the message body size above the default message index embed threshold (4KB).

Message body size: 10000 bytes

### Publish rate: 10000 msg/s

|  Queue Type | Max 99th | Max 95th | Max 75th |
|          -: |       -: |       -: |       -: |
| non-durable |  4.19 ms |  1.04 ms |  0.88 ms |
|     durable |  1.43 ms |  1.04 ms |  0.91 ms |
|        lazy |  1.83 ms |  1.17 ms |  0.97 ms |

### Publish rate: 20000 msg/s

|  Queue Type | Max 99th | Max 95th | Max 75th |
|          -: |       -: |       -: |       -: |
| non-durable |  3.66 ms |  1.37 ms |  0.88 ms |
|     durable |  2.22 ms |  1.30 ms |  0.84 ms |
|        lazy |  3.14 ms |  1.56 ms |  1.11 ms |

### Publish rate: 30000 msg/s

|  Queue Type | Max 99th | Max 95th | Max 75th |
|          -: |       -: |       -: |       -: |
| non-durable | 13.62 ms |  2.61 ms |  1.43 ms |
|     durable |  7.86 ms |  2.09 ms |  1.11 ms |
|        lazy |  5.23 ms |  2.09 ms |  1.01 ms |

### Publish rate: 40000 msg/s

|  Queue Type | Max 99th | Max 95th | Max 75th |
|          -: |       -: |       -: |       -: |
| non-durable | 33.55 ms |  6.81 ms |  1.96 ms |
|     durable | 39.84 ms | 11.53 ms |  3.79 ms |
|        lazy | 50.32 ms |  8.90 ms |  3.01 ms |

### Publish rate: 50000 msg/s

|  Queue Type | Max 99th | Max 95th | Max 75th |
|          -: |       -: |       -: |       -: |
| non-durable | 113.24 ms | 92.27 ms | 83.88 ms |
|     durable | 109.04 ms | 96.46 ms | 92.27 ms |
|        lazy | 100.66 ms | 96.46 ms | 83.88 ms |

## What are the effects of publisher confirms?

### Publish rate: 50000 msg/s

| Publisher Confirms | Msg/s | Max 99th | Max 95th | Max 75th |
|                 -: |    -: |       -: |       -: |       -: |
|           disabled | 50000 |  8.38 ms |  1.63 ms |  1.18 ms |
|        every 1 msg |  6700 |  0.22 ms |  0.20 ms |  0.18 ms |
|       every 2 msgs |  8800 |  0.32 ms |  0.31 ms |  0.27 ms |
|       every 5 msgs | 18500 |  0.41 ms |  0.36 ms |  0.31 ms |
|      every 10 msgs | 31000 |  0.62 ms |  0.52 ms |  0.39 ms |
|     every 100 msgs | 39000 |  3.27 ms |  2.88 ms |  2.74 ms |
|     every 500 msgs | 35000 | 18.86 ms | 17.81 ms | 15.71 ms |

## What are the effects of consumer ACKs?

Consumer ACKs are set to 1/2 of the prefecth value (a.k.a. QOS)

### Publish rate: 50000 msg/s

|  Consumer ACKs | Msg/s | Max 99th | Max 95th | Max 75th |
|             -: |    -: |       -: |       -: |       -: |
|       disabled | 50000 | 23.06 ms |  1.96 ms |  1.31 ms |
|    every 1 msg |  6600 |  0.21 ms |  0.19 ms |  0.18 ms |
|   every 2 msgs | 11700 |  0.26 ms |  0.23 ms |  0.19 ms |
|   every 5 msgs | 18500 |  0.41 ms |  0.36 ms |  0.31 ms |
|  every 10 msgs | 32000 |  0.62 ms |  0.55 ms |  0.39 ms |
| every 100 msgs | 39000 |  3.14 ms |  2.88 ms |  2.61 ms |
| every 500 msgs | 33500 | 18.87 ms | 17.82 ms | 15.72 ms |

## What are the effects of exchange type?

### Publish rate: 20000 msg/s

| Exchange Type | Msg/s | Max 99th | Max 95th | Max 75th |
|            -: |    -: |       -: |       -: |       -: |
|        direct | 20000 |  7.07 ms |  1.18 ms |  0.78 ms |
|        fanout | 20000 |  6.29 ms |  1.18 ms |  0.78 ms |
|         topic | 20000 | 28.31 ms |  9.96 ms |  1.18 ms |
|       headers | 20000 |  1.44 ms |  1.18 ms |  0.82 ms |

### Publish rate: 30000 msg/s

| Exchange Type | Msg/s |  Max 99th |  Max 95th |  Max 75th |
|            -: |    -: |        -: |        -: |        -: |
|        direct | 30000 |   7.60 ms |   1.50 ms |   1.11 ms |
|        fanout | 30000 |   3.67 ms |   1.24 ms |   0.95 ms |
|         topic | 23000 | 603.96 ms | 570.41 ms | 536.85 ms |
|       headers | 30000 |   4.98 ms |   1.44 ms |   1.11 ms |

### Publish rate: 40000 msg/s

| Exchange Type | Msg/s |  Max 99th |  Max 95th |  Max 75th |
|            -: |    -: |        -: |        -: |        -: |
|        direct | 40000 |   4.71 ms |   1.44 ms |   1.01 ms |
|        fanout | 40000 |   6.81 ms |   1.50 ms |   1.11 ms |
|         topic | 23000 | 209.71 ms | 201.32 ms | 192.93 ms |
|       headers | 40000 |   4.98 ms |   1.57 ms |   1.11 ms |

### Publish rate: 50000 msg/s

| Exchange Type | Msg/s |  Max 99th |  Max 95th |  Max 75th |
|            -: |    -: |        -: |        -: |        -: |
|        direct | 50000 |  20.97 ms |   1.70 ms |   1.18 ms |
|        fanout | 50000 |  28.31 ms |   1.90 ms |   1.18 ms |
|         topic | 23000 | 704.61 ms | 671.06 ms | 637.50 ms |
|       headers | 50000 |  37.74 ms |   4.98 ms |   1.24 ms |

## What are the effects of queue mirroring?

### Publish rate: 10000 msg/s

| Queue Mirrors | Max 99th | Max 95th | Max 75th |
|            -: |       -: |       -: |       -: |
|             1 |  3.40 ms |  0.95 ms |  0.62 ms |
|             2 |  2.75 ms |  0.75 ms |  0.52 ms |
|             3 |  3.93 ms |  0.88 ms |  0.59 ms |

### Publish rate: 20000 msg/s

| Queue Mirrors | Max 99th | Max 95th | Max 75th |
|            -: |       -: |       -: |       -: |
|             1 |  2.75 ms |  1.18 ms |  0.78 ms |
|             2 |  7.34 ms |  1.18 ms |  0.75 ms |
|             3 | 17.82 ms |  1.31 ms |  0.82 ms |

### Publish rate: 30000 msg/s

| Queue Mirrors | Max 99th | Max 95th | Max 75th |
|            -: |       -: |       -: |       -: |
|             1 | 11.53 ms |  1.31 ms |  0.88 ms |
|             2 | 19.92 ms |  5.24 ms |  0.98 ms |
|             3 | 39.84 ms | 20.97 ms |  1.24 ms |

## What are the effects of RabbitMQ Management?

### Publish rate: 50000 msg/s

| Management | Max 99th | Max 95th | Max 75th |
|         -: |       -: |       -: |       -: |
|    enabled | 16.77 ms |  1.77 ms |  1.18 ms |
|   disabled | 14.15 ms |  1.77 ms |  1.18 ms |

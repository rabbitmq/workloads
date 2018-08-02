# Environment setup

(Intel Optane](https://www.acceleratewithoptane.com/access/) running Ubuntu 16.04 LTS & Linux 4.13.0-41-generic SMP x86_64.

Each host had two [Intel Xeon Gold 6142](https://ark.intel.com/products/120487/Intel-Xeon-Gold-6142-Processor-22M-Cache-2_60-GHz) & 20Gbps Bonded Network (2 × 10Gbps w/ LACP).

We were running the following versions:

1. RabbitMQ v3.7.7 on Erlang/OTP v21.0.3
1. [PerfTest v2.2.0.M1](https://github.com/rabbitmq/rabbitmq-perf-test/releases/download/v2.2.0.M1/rabbitmq-perf-test-2.2.0.M1-bin.tar.gz) on Open JDK v1.8.0_171, RabbitMQ AMQP Java Client v5.3.0

```
wget -O - 'https://dl.bintray.com/rabbitmq/Keys/rabbitmq-release-signing-key.asc' | apt-key add -
apt install apt-transport-https
cat > /etc/apt/sources.list.d/rabbitmq.list <<EOF
deb https://dl.bintray.com/rabbitmq/debian $(lsb_release -c | awk '{ print $2 }') rabbitmq-server-v3.7.x
deb https://dl.bintray.com/rabbitmq/debian $(lsb_release -c | awk '{ print $2 }') erlang-21.x
deb https://dl.bintray.com/rabbitmq/debian $(lsb_release -c | awk '{ print $2 }') elixir
EOF
apt update
apt upgrade -y
apt install vim-nox dstat htop vnstat tmux -y
apt install rabbitmq-server -y
rabbitmqctl add_user admin PASSWORD
rabbitmqctl set_user_tags admin administrator
rabbitmqctl set_permissions admin '.*' '.*' '.*'
rabbitmq-plugins enable rabbitmq_management
```

```
apt update
apt upgrade -y
apt install openjdk-8-jre -y
apt install vim-nox dstat htop vnstat tmux -y
wget https://github.com/rabbitmq/rabbitmq-perf-test/releases/download/v2.2.0.M1/rabbitmq-perf-test-2.2.0.M1-bin.tar.gz
tar zxvf rabbitmq-perf-test-2.2.0.M1-bin.tar.gz
cd rabbitmq-perf-test-2.2.0.M1
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/
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

| Publish Rate      | Max 99th  | Max 95th  | Max 75th  |
| -:                | -:        | -:        | -:        |
| 100 msg/s         | 0.80 ms   | 0.67 ms   | 0.64 ms   |
| 1000 msg/s        | 0.47 ms   | 0.39 ms   | 0.31 ms   |
| 10000 msg/s       | 6.29 ms   | 1.18 ms   | 0.72 ms   |
| 20000 msg/s       | 16.25 ms  | 1.31 ms   | 0.78 ms   |
| 30000 msg/s       | 23.06 ms  | 2.36 ms   | 0.98 ms   |
| 50000 msg/s       | 39.84 ms  | 11.01 ms  | 1.83 ms   |
| 70000 msg/s       | 92.27 ms  | 88.08 ms  | 54.52 ms  |
| 73000 msg/s (MAX) | 234.86 ms | 226.48 ms | 209.70 ms |

At 73k msg/s we are hitting ...

## How does credit flow affect message latency (Erlang/OTP 21)?

Publish rate: 80000 msg/s

| Credits    | Max 99th | Max 95th | Max 75th |
| -:         | -:       | -:       | -:       |
| {100, 50}  | 23.1ms   | 19.9ms   | 4.97ms   |
| {200, 100} | 25.2ms   | 14.7ms   | 3.53ms   |
| {400, 200} | 210ms    | 193ms    | 185ms    |

### {200, 100}

Erlang scheduler utilization was highest at `{200, 100}`:

```erlang
recon:scheduler_usage(5000).
[{1,0.6423917112158622},
 {2,0.7163237673642957},
 {3,0.7692058040286527},
 {4,0.6308404592514513},
 {5,5.355899933531696e-5},
 {6,3.345150568985605e-6},
 {7,2.7830244208355204e-6},
 {8,3.135576866444181e-6},
 {9,0.0},
 {10,0.0},
 {11,0.0},
 {12,0.0},
 {13,0.0},
 {14,0.0},
 {15,0.0},
 {16,3.045235457910485e-4}]
```

### {400, 200} (default)

| Credits    | Max 99th | Max 95th | Max 75th |
| -:         | -:       | -:       | -:       |
| {400, 200} | 210ms    | 193ms    | 185ms    |

Erlang scheduler utilization at `{400, 200}`:

```erlang
recon:scheduler_usage(5000).
[{1,0.45632246662108844},
 {2,0.6696720902621559},
 {3,0.5261055287839728},
 {4,0.5886865800022822},
 {5,0.44606700844562114},
 {6,3.2406325893914185e-6},
 {7,4.5946789441100864e-6},
 {8,3.1390420895599905e-6},
 {9,0.0},
 {10,0.0},
 {11,0.0},
 {12,0.0},
 {13,0.0},
 {14,0.0},
 {15,0.0},
 {16,0.0}]
```

Going above the default `{400, 200}` credits resulted in over 200ms latency for 99th, 95th & 75th.
We suspect that this is related to Erlang/OTP 21 which removes the reduction penalty when sending messages to processes with large mailboxes.

## How does credit flow affect message latency (Erlang/OTP 20)?

## How do multiple consumers affect message latency?
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

* non-durable
* durable
* lazy

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

* 1
* 2
* 3

## What are the effects of RabbitMQ Management?

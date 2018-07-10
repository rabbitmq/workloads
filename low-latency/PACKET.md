# Environment setup

Because we didn't want any CPU contention and we wanted a real network, we had 2 identical machines:
[Packet c1.small.x86](https://www.packet.net/bare-metal/servers/c1-small/) running Ubuntu 18.04 LTS & Linux 4.15.0-20-generic SMP x86_64.

Each host had a single [Intel(R) Xeon(R) CPU E3-1240 v5 @ 3.50GHz](https://ark.intel.com/products/88176/Intel-Xeon-Processor-E3-1240-v5-8M-Cache-3_50-GHz) & 2Gbps Bonded Network (2 × 1Gbps w/ LACP).

We were running the following versions:

1. RabbitMQ v3.7.7 on Erlang/OTP v21.0.2
1. PerfTest v2.2.0.M1 on Open JDK v1.8.0_171, RabbitMQ AMQP Java Client v5.3.0

We started with the following configuration:

* single non-durable queue
* 1 producer with 1 connection & 1 channel, no publisher confirms
* 1 consumer with 1 connection & 1 channel, automatic message acknowledgements
* 1000 bytes AMQP message body

We started with the following PerfTest flags:

```
bin/runjava com.rabbitmq.perf.PerfTest \
--autoack \
--interval 15 \
--size 1000 \
--queue-args 'x-max-length=10000' \
--routing-key perf-test \
--uri 'amqp://admin:pass@10.80.163.3:5672/%2F' \
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

| Publish Rate      | Max 99th | Max 95th | Max 75th |
| -:                | -:       | -:       | -:       |
| 100 msg/s         | 1.75ms   | 1.49ms   | 1.47ms   |
| 1000 msg/s        | 0.87ms   | 0.74ms   | 0.67ms   |
| 10000 msg/s       | 3ms      | 1.89ms   | 1ms      |
| 20000 msg/s       | 15.7ms   | 6.5ms    | 0.97ms   |
| 30000 msg/s       | 36ms     | 21ms     | 1.37ms   |
| 50000 msg/s       | 40ms     | 8.1ms    | 1.17ms   |
| 80000 msg/s       | 210ms    | 193ms    | 185ms    |
| 83000 msg/s (MAX) | 302ms    | 302ms    | 285ms    |

### 20k msg/s

| Publish Rate | Max 99th | Max 95th | Max 75th |
| -:           | -:       | -:       | -:       |
| 20000 msg/s  | 15.7ms   | 6.5ms    | 0.97ms   |

At 20k msg/s, the producer channel would intermittently enter flow for a brief period of time.
CPU user was at 20%, CPU system was at 1% & Erlang scheduler utilization was at ~30%:

```erlang

recon:scheduler_usage(5000).
[{1,0.32127064777042974},
 {2,0.19421909177056895},
 {3,0.276789464705123},
 {4,5.61117772151292e-6},
 {5,2.8452664909501565e-6},
 {6,2.872080559732737e-6},
 {7,2.643504629596315e-6},
 {8,2.4995208790033458e-6},
 {9,0.0},
 {10,0.0},
 {11,0.0},
 {12,0.0},
 {13,0.0},
 {14,0.0},
 {15,0.0},
 {16,0.0}]
```

### 50k msg/s

| Publish Rate | Max 99th | Max 95th | Max 75th |
| -:           | -:       | -:       | -:       |
| 50000 msg/s  | 40ms     | 8.1ms    | 1.17ms   |

At 50k msg/s, the producer channel would intermittently enter flow.
CPU user was at 32%, CPU system was at 3% & Erlang scheduler utilization was at ~44%:

```erlang
recon:scheduler_usage(5000).
[{1,0.5141759984360637},
 {2,0.45835134168684916},
 {3,0.4446946843590661},
 {4,0.3624757672451614},
 {5,3.004022702537944e-6},
 {6,2.684283906691537e-6},
 {7,2.8790446178979844e-6},
 {8,2.5593061828957327e-6},
 {9,0.0},
 {10,0.0},
 {11,0.0},
 {12,0.0},
 {13,0.0},
 {14,0.0},
 {15,0.0},
 {16,0.0}]
```

### 80k msg/s & above

| Publish Rate      | Max 99th | Max 95th | Max 75th |
| -:                | -:       | -:       | -:       |
| 80000 msg/s       | 210ms    | 193ms    | 185ms    |
| 83000 msg/s (MAX) | 302ms    | 302ms    | 285ms    |

At 80k msg/s @ 1000 bytes msg body we are saturating the 2Gbps network.
Lowering the msg body to 500 bytes resulted in a peak of 83k msgs/s. Both producer connection & channel were in a permanent flow state.
CPU user was at 40%, CPU system was at 3% & scheduler utilization was at ~54%:

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

We suspect that the flow is artificial, the consumer is able to cope with the message rate, but the queue process is slowing down the producer unnecessarily.

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

# How to measure message latencies in RabbitMQ?

PerfTest is a tool for testing throughput, developed by the RabbitMQ team. It’s based on the RabbitMQ Java client, and can be configured to simulate varying workload levels. Its output can range from plain text output to STDOUT, to static HTML graphs.


PerfTest can be used in conjunction with other tools, such as Prometheus and Grafana, for visual analysis of throughput, message latencies, and many other metrics.

By default, Perftest’s message latencies are displayed on STDOUT, at regular intervals, using the following format:

```
time: 5.000s, received: 999 msg/s, min/median/75th/95th/99th latency: 1/3/3/3 ms
```

From version 2.2.0, PerfTest can also expose metrics, including message latencies, to Prometheus, an open-source systems monitoring and alerting toolkit hosted by the Cloud Native Computing Foundation.

To see a list of the new flags added from 2.2.0 onwards, download the latest RabbitMQ PerfTest source code release from [rabbitmq-perf-test Releases](https://github.com/rabbitmq/rabbitmq-perf-test/releases) locally, unarchive, change to that directory within your terminal, and then run `make run ARGS=-mh`.

Once RabbitMQ is running locally, you'll be able to run PerfTest with Prometheus metrics enabled by using the following command:

```
make run ARGS="--metrics-prometheus --producers 1 --consumers 1 --rate 100 --metrics-tags type=publisher,type=consumer"
```

To confirm that Prometheus metrics are working correctly within PerfTest, you can visit `http://127.0.0.1:8080/metrics` in a browser.

> Since a producer and a consumer are both run in a single PerfTest instance, we need to tag it with both types: `--metrics-tags type=publisher,type=consumer`.

> Make sure that no other process is bound to port 8080, otherwise PerfTest will start, bind to the same port, and traffic will not be routed correctly. Talk to Arnaud about this.

Once you've confirmed that metrics are running locally, you can configure Prometheus to scrape PerfTest on a pre-determined interval. The following static configuration can be used:

```
  scrape_configs:
  - job_name: perf-test
    static_configs:
      - targets: ['127.0.0.1:8080']
```

If everything is set up correctly, you should see a new target in Prometheus by selecting "Status > Targets". You can then view  `perftest_latency_seconds` metrics, alongside other metrics, by going to "Graph" and searching for "perftest". Click "Execute" and then click "Graph" to view a visual performance chart.

Now that we have Prometheus integrated with PerfTest, we can create a dashboard in Grafana to visualise PerfTest metrics. Import the [RabbitMQ perf-test message latencies dashboard](https://grafana.com/dashboards/6566) straight from grafana.com.

## What is the baseline message latency?

* RabbitMQ v3.7.6
* Erlang/OTP v20.3.8
* VM type was n1-highcpu-4 with Intel(R) Xeon(R) CPU @ 2.60GHz (cpu family 6 & model 45)

We used a single PerfTest instance for both publishing and consuming so that there would be no time difference between the hosts when calculating message latencies.
PerfTest was running on a separate host so that JVM wouldn't contend on CPU with Erlang, and so that we would use a real network, not the loopback interface.
PerfTest was deployed to CloudFoundry using [rabbitmq-perf-test-for-cf](https://github.com/rabbitmq/rabbitmq-perf-test-for-cf).

* perf-test v2.2.0
* JDK 1.8.0_172 (java_buildpack v4.12)
* VM type was n1-standard-4 with Intel(R) Xeon(R) CPU @ 2.60GHz (cpu family 6 & model 45)

* 1 publisher, no confirms
* 1 consumer using autoack
* 1 non-durable queue
* 10K msg/s, non-persistent
* 1K msg body

Given a 5 minute window:

* max 99th percentile is 1.56ms
* max 95th percentile is 1.23m
* max 75th percentile is 1.04ms

## What are the effects of publish rates?

| Publish Rate | Max 99th | Max 95th | Max 75th |
| -            | -        | -        | -        |
| 100 msg/s    | 26ms     | 0.97ms   | 0.64ms   |
| 1000 msg/s   | 2.90ms   | 0.58ms   | 0.45ms   |
| 10000 msg/s  | 2.74ms   | 1.36ms   | 1.16ms   |
| 20000 msg/s  | 24ms     | 13.10ms  | 1.63ms   |
| 30000 msg/s  | 243ms    | 243ms    | 235ms    |

We've noticed at 20k msg/s the producer connection & channel are in flow intermittently.
CPU usage was at 50%, and scheduler utilization was at ~36%:

```erlang
recon:scheduler_usage(5000).
[{1,0.2941370489242772},
 {2,0.4844966953303812},
 {3,0.42555099137009667},
 {4,0.2326298245189346},
 {5,0.0},
 {6,0.0},
 {7,0.0},
 {8,0.0}]
```

We've noticed at 30k msg/s the producer connection & channel are in flow constantly.
CPU usage was at 75%, and scheduler utilization was at ~65%:

```erlang
recon:scheduler_usage(5000).
[{1,0.6695429071706646},
 {2,0.5765912199438225},
 {3,0.6885744277903317},
 {4,0.6703701969365036},
 {5,0.0},
 {6,0.0},
 {7,0.0},
 {8,0.0}]
```

We suspect that the flow is artificial, the consumer is able to cope with the message rate, but the queue process is slowing down the producer unnecessarily.

## What are the effects of credit flow credits?

Publish rate: 30000 msg/s

| Credits        | Max 99th | Max 95th | Max 75th |
| -              | -        | -        | -        |
| {400, 200}     | 243ms    | 243ms    | 235ms    |
| {4000, 2000}   | 96ms     | 92ms     | 80ms     |
| {40000, 20000} | 3000ms   | 3000ms   | 3000ms   |

Increasing the credit flow credits 10x makes message latency ~3x lower.

Increasing the credit flow credits 100x makes message latency ~12x higher.

```erlang
recon:scheduler_usage(5000).
[{1,0.5358486521401843},
 {2,0.5963335130712368},
 {3,0.5895582014257715},
 {4,0.5651211425785718},
 {5,0.0},
 {6,0.0},
 {7,0.22630769885254684},
 {8,0.0}]
```

## What are the effects of multiple consumers?

Publish rate: 10000 msg/s

| Consumers | Max 99th | Max 95th | Max 75th |
| -         | -        | -        | -        |
| 1         | 2.74ms   | 1.36ms   | 1.16ms   |
| 2         | 6ms      | 1.24ms   | 0.94ms   |
| 5         | 10.50ms  | 6.00ms   | 0.84ms   |
| 10        | 16.80ms  | 11.50ms  | 5.20ms   |
| 100       | 15.20ms  | 11.00ms  | 6.82ms   |
| 500       | 16.80ms  | 11.50ms  | 6.50ms   |

## What are the effects of multiple producers?

Publish rate: 10000 msg/s

| Producers | Max 99th | Max 95th | Max 75th |
| -         | -        | -        | -        |
| 1         | 2.74ms   | 1.36ms   | 1.16ms   |
| 2         | 3.40ms   | 1.11ms   | 0.81ms   |
| 5         | 10.50ms  | 3.10ms   | 0.61ms   |
| 10        | 16.80ms  | 12.10ms  | 5.00ms   |
| 100       | 31.40ms  | 22.00ms  | 12.10ms  |
| 500       | 35.60ms  | 23.10ms  | 10.50ms  |

## What are the effects of running multiple queues?

Publish rate: 10000 msg/s

We use 1 producer & 1 consumer per queue, so there will be multiple producers, consumers & queues.

Each producer and each consumer use their own connection & channel.

| Queues<br /> Producers<br /> Consumers | Msg/s per queue | Max 99th | Max 95th | Max 75th |
| -:                                     | -:              | -:       | -:       | -:       |
| 1                                      | 10000           | 2.74ms   | 1.36ms   | 1.16ms   |
| 2                                      | 5000            | 2.3ms    | 1.43ms   | 0.99ms   |
| 5                                      | 2000            | 17.8ms   | 13.1ms   | 8.4ms    |
| 10                                     | 1000            | 17.8ms   | 14.1ms   | 8.9ms    |
| 100                                    | 100             | 28ms     | 18.9ms   | 11ms     |

We changed the NIO_THREADS from 10 to 5 and the NIO_THREAD_POOL from 20 to 10 and observed lower latency, especially for the 100 & 10 tests.
Having run this a couple of times later on the in day, we've observed 80% higher latency for the 2 test and stopped.
The cloud is too unpredictable to be confident in latency readings.
Having set up fping between the RabbitMQ node and the Diego cells, we've observed network latencies as high as 2.76ms.

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

## Notes

* Add CF manifest & BOSH manifest
* If producer & consumer instances are running on separate hosts, make sure that they use NTP and are in sync. Check for time drift.

# How to measure message latencies in RabbitMQ?

Perf-test, the RabbitMQ throughput testing tool that is maintained by team RabbitMQ, was able to report message latencies for a long time now. The message latencies would be displayed on STDOUT, using the following format:

```
time: 5.000s, received: 999 msg/s, min/median/75th/95th/99th latency: 1/3/3/3 ms
```

A new feature coming in perf-test v2.2.0 exposes message latencies to Prometheus via `GET /metrics` endpoint. To make use of this feature, take a look at the new flags that have been added by running `make run ARGS=-mh`.

To enable this new feature, you could run the following command:

```
make run ARGS="--metrics-prometheus --use-millis --producers 1 --consumers 1 --rate 100 --metrics-tags type=publisher,type=consumer"
```

You will be able to access the metrics via `http://127.0.0.1:8080/metrics`

Since we run a producer & a consumer in a single perf-test instance, we need to tag it with both types: `--metrics-tags type=publisher,type=consumer`.

> Make sure that no other process is bound to port 8080, otherwise perf-test will start, bind to the same port, and traffic will not be routed correctly. Talk to Arnaud about this.

Once you confirm that Prometheus support has been configured in perf-test, you will need to configure Prometheus to scrape perf-test on a pre-determined interval. We use the following static configuration:

```
  scrape_configs:
  - job_name: perf-test
    static_configs:
      - targets: ['127.0.0.1:8080']
```

If everything is setup correctly, you would expect to see a new target in Prometheus and the `perftest_latency_seconds` metrics, alongside other metrics prefixed with `perftest_`.

Now that we have Prometheus integrated with perf-test, we can create a dashboard in Grafana to visualise perf-test metrics. Import the [RabbitMQ perf-test message latencies dashboard](https://grafana.com/dashboards/6566) straight from grafana.com.

## What is the baseline message latency?

RabbitMQ v3.7.6
Erlang/OTP v20.3.8
VM type was n1-highcpu-4

We used a single perf-test instance for both publishing and consuming so that there would be no time difference between the hosts when calculating message latencies.
perf-test was running on a separate host so that JVM wouldn't contend on CPU with Erlang, and so that we would use a real network, not the loopback interface.
Perf-test was deployed to CloudFoundry using [rabbitmq-perf-test-for-cf](https://github.com/rabbitmq/rabbitmq-perf-test-for-cf).

perf-test v2.2.0
JDK 1.8.0_172 (java_buildpack v4.12)
VM type was n1-standard-2

1 publisher, no confirms
1 consumer using autoack
1 non-durable queue
10K msg/s, non-persistent
1K msg body

The max 99th percentile is 1.56ms.
The max 95th percentile is 1.23ms.
The max 75th percentile is 1.04ms.

## What are the effects of RabbitMQ's metrics collection interval on message latency?

## What are the effects of RabbitMQ Management on message latency?

## What are the effects of message rates on message latency?
100 msg/s
1000 msg/s
10000 msg/s
50000 msg/s

## What are the effects of multiple consumers on message latency?
1
10
100

## What are the effects of multiple producers on message latency?
1
2
5

## What are the effects of different queue types on message latency?
non-durable
durable
lazy

## What are the effects of queue mirroring on message latency?
1
2
3

## What are the effects of publisher confirms on message latency?
multi-publisher confirms

## What are the effects of consumer acks on message latency?
multi-acks

## What are the effects of exchange type on message latency?
direct
topic
fanout
headers

## What are the effects of running multiple queues on message latency?
1
2
5

## Notes

Running separate perf-test instances for producer & consumer
If they are running on separate hosts, make sure that they use NTP and are in sync. Check for time drift.


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

## Notes

Running separate perf-test instances for producer & consumer
If they are running on separate hosts, make sure that they use NTP and are in sync. Check for time drift.
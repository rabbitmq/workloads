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

Once you've confirmed that metrics are running locally, you can configure Prometheus to scrape PerfTest on a pre-determined interval. Create a file named `prometheus.yml`, containing the following:

```
global:
  scrape_interval:     15s
  evaluation_interval: 15s

rule_files: []

scrape_configs:
  - job_name: prometheus
    static_configs:
      - targets: ['localhost:9090']
  - job_name: perf-test
    static_configs:
      - targets: ['localhost:8080']

```

You can then run Prometheus by changing to the directory containing this configuration file, and running the `prometheus` command to start the Prometheus server.

To interact with Prometheus, you can now run a web browser and visit the URL http://localhost:9090. If everything is set up correctly, a Prometheus page will load. You should now see a new target, **perf-test (1/1 up)**, listed in Prometheus by selecting [Status > Targets](http://localhost:9090/targets). You can then view  `perftest_latency_seconds` metrics, alongside other metrics, by selecting [Graph](http://localhost:9090/graph), searching for "perftest", and selecting an item of interest, such as **perftest_latency_seconds**. Click the **Execute** button to see a list of metrics. You can then click the **Graph** tab to see a visual representation of the same metrics as a chart.

Now that we have Prometheus integrated with PerfTest, we can create a dashboard in Grafana to visualise PerfTest metrics. Import the [RabbitMQ perf-test message latencies dashboard](https://grafana.com/dashboards/6566) straight from https://grafana.com.

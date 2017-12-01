## RabbitMQ workloads

All RabbitMQ users should know what to expect from RabbitMQ in terms of performance, and understand the impact of specific requirements on throughput. For example, durable queues with persistent messages that are replicated across 3 RabbitMQ nodes incur a certain throughput penalty that is poorly understood. This is one of the examples which we cover in detail in `dq-3n-confirm-multiack` dir.

Each directory in this repository represents a specific RabbitMQ configuration and contains:

* RabbitMQ configuration description and point-in-time performance observations, as well as links to metrics dashboards and RabbitMQ Management UI
* CloudFoundry manifest for [PerfTest](https://github.com/rabbitmq/rabbitmq-perf-test) that captures every producer and consumer configuration
* BOSH manifest for [rabbitmq-server-boshrelease](https://github.com/rabbitmq/rabbitmq-server-boshrelease) the contains the complete RabbitMQ config. This BOSH manifest requires [variable interpolation](http://bosh.io/docs/cli-int.html) for the credentials, it cannot be used as is.

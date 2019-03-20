## RabbitMQ workloads

We all want the best performance from our messaging technology. We want the lowest possible message latency; message delivery reliability; and the most efficient use of RAM, storage, and CPU.

Rapid messaging workflows involve many complex tradeoffs between speed, data consistency, and reliability. These choices and their ramifications are not always obvious or intuitive.

The contents of this repository provide common examples of RabbitMQ deployments, and explain how message throughput can be impacted and improved by the many different broker configurations available.

Each directory in this repository represents a specific RabbitMQ configuration and contains:

* RabbitMQ configuration description and point-in-time performance observations, as well as links to metrics dashboards and RabbitMQ Management UI
* CloudFoundry manifest for [PerfTest](https://github.com/rabbitmq/rabbitmq-perf-test) that captures every producer and consumer configuration
* BOSH manifest for [rabbitmq-server-boshrelease](https://github.com/rabbitmq/rabbitmq-server-boshrelease) contains the complete RabbitMQ config. This BOSH manifest requires [variable interpolation](https://bosh.io/docs/cli-int.html) for the credentials; it cannot be used as-is.

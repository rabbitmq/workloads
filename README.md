## RabbitMQ configurations optimised for specific use-cases

We want to continuously validate RabbitMQ best-case behaviour in specific use-cases.

Each directory in this repository represents a specific use-case and contains:

* README that describes the use-case, highlights specific RabbitMQ configurations and captures point-in-time observations
* CloudFoundry manifest for [PerfTest](https://github.com/rabbitmq/rabbitmq-perf-test) that describes every producer and consumer configuration
* BOSH manifest for [rabbitmq-server-boshrelease](https://github.com/rabbitmq/rabbitmq-server-boshrelease) the captures the RabbitMQ configuration in detail. The manifest requires [variable interpolation](http://bosh.io/docs/cli-int.html) for some credentials. It cannot be used as is.

## Requirements

### Messages are ordered

In order for message order to be preserved, we must use a single RabbitMQ queue.

### Messages cannot be lost

Messages will be persisted to disk, across multiple nodes.

### Messages must have 3N redundancy

There will be 3 fully redundant copies for every message at all times.
There will be no service impact if 2 out of 3 nodes fail.

### Confirm message persistence and replication

We want the broker to confirm messages once they are persisted to disk across all nodes.

### Consumers regulate message delivery rate

We cannot flood consumers with messages, consumers must acknowledge messages as they get processed.

## Setup

This is a setup that maximizes availability and consistency.
There are 3 nodes across which all operations need to synchronize. This will reflect in message throughput.

We limit the size of messages to 1KB. This is a sensible default that is most likely to exist in real-world scenarios.

Our RabbitMQ node has 8 CPU cores, which translates to 8 Erlang schedulers.
To achieve optimal Erlang scheduler utilization, we have 1 producer and 1 consumer with 1 connection & 1 channel each.
This means that we have 2 connection processes, 2 channel processes, 1 queue process & 1 queue synchronisation process (6 processes in total), spread across 8 Erlang schedulers.
Since our use-case is CPU & network-intensive, we chose an n1-highcpu-8 instance type.

Here is a summary of our configuration:

| PROPERTY             | VALUE          |
| -------------------- | -------------  |
| GCP INSTANCE TYPE    | n1-highcpu-8   |
| QUEUE                | durable        |
| QUEUE MIRRORS        | 3              |
| PUBLISHERS           | 1              |
| PUBLISHER RATE MSG/S | unlimited      |
| PUBLISHER CONFIRMS   | every 200 msgs |
| MSG SIZE bytes       | 1000           |
| CONSUMERS            | 1              |
| CONSUMER RATE MSG/S  | unlimited      |
| QOS (PREFETCH)       | 200            |
| MULTI-ACK            | every 50 msgs  |

## Links

[DataDog dashboard](https://p.datadoghq.com/sb/eac1d6667-75ac04872a)

| RabbitMQ | Metrics                                                                     | Management URL (self-signed SSL cert)                                                   | Username | Password |
| -        | -                                                                           | -                                                                                       | -        | -        |
| v3.6.6   | [Netdata](https://0-netdata-dq-3n-confirm-multiack-3-6-6.gcp.rabbitmq.com)  | [dq-3n-confirm-multiack-3-6-6](https://dq-3n-confirm-multiack-3-6-6.gcp.rabbitmq.com)   | **demo** | **demo** |
| v3.6.14  | [Netdata](https://0-netdata-dq-3n-confirm-multiack-3-6-14.gcp.rabbitmq.com) | [dq-3n-confirm-multiack-3-6-14](https://dq-3n-confirm-multiack-3-6-14.gcp.rabbitmq.com) | **demo** | **demo** |
| v3.7.0   | [Netdata](https://0-netdata-dq-3n-confirm-multiack-3-7-0.gcp.rabbitmq.com)  | [dq-3n-confirm-multiack-3-7-0](https://dq-3n-confirm-multiack-3-7-0.gcp.rabbitmq.com)   | **demo** | **demo** |

## Point-in-time observations

![](dq-3n-confirm-multiack-dashboard.png)
![](dq-3n-confirm-multiack-overview.png)
![](dq-3n-confirm-multiack-master-node.png)

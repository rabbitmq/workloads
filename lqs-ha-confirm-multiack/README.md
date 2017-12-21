## Requirements

### Messages cannot be lost

Publishers will receive confirmations when messages have been persisted to disk by all nodes in the cluster.

Consumers will send acknowledgements to the broker when messages have been processed.

### Messages must have 3N redundancy

There will be 3 fully redundant copies for every message at all times.
There will be no service impact if 2 out of 3 nodes fail.

### Confirm message persistence and replication

We want the broker to confirm messages once they are persisted to disk across all nodes.

### Consumers regulate message delivery rate

We cannot flood consumers with messages, consumers must acknowledge messages as they get processed.

## Setup

## Links

[DataDog dashboard](https://p.datadoghq.com/sb/eac1d6667-75ac04872a)

| RabbitMQ | Metrics                                                                     | Management URL (self-signed SSL cert)                                                   | Username | Password |
| -        | -                                                                           | -                                                                                       | -        | -        |
| v3.7.1   | [Netdata](https://0-netdata-lqs-ha-confirm-multiack-3-7-1.gcp.rabbitmq.com) | [lqs-ha-confirm-multiack-3-7-1](https://lqs-ha-confirm-multiack-3-7-1.gcp.rabbitmq.com) | **demo** | **demo** |

## Point-in-time observations

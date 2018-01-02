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

### New messages are rejected when queues are full

All queues have a maximum capacity defined as `max-length-bytes`.
Once this limit is reached, new messages are rejected with `basic.nack`, as described in [Queue Overflow Behaviour](https://www.rabbitmq.com/maxlength.html#overflow-behaviour).

### Messages in queues exceed the available node memory

Given many queues per node which combined have more messages than available system memory,
RabbitMQ nodes and the cluster as a whole continues to operate as expected during IaaS events such as:

* VM preemption
* 100s of connections terminating abruptly
* 100s of new connections initiating at the same

## Setup

All messages are limited to 1,000 bytes and flushed to disk as soon as they reach the queue process (a.k.a. [lazy queues](https://www.rabbitmq.com/lazy-queues.html)).
The goal is to keep as many messages as possible on disk, and only load them into memory when requested by consumers.
Lazy queues are ideal for very long queues, with many millions of messages.
In our scenario, we have 500 queues across a 3 node cluster.
Since all queues are automatically replicated across all nodes, there are ~166 queue masters and ~334 queue mirrors on every node.
Each queue has the `max-lenght-bytes` set to 150,000,000 bytes, which results in a limit of ~150,000 messages - all messages are 1,000 bytes.

Every queue has 1 producer (or publisher) and 3 consumers.
The producers &amp; consumers are throttled so that we simulate a slow but steady message increase.
Without throttling producers, [consumer bias](https://www.rabbitmq.com/blog/2014/04/10/consumer-bias-in-rabbitmq-3-3/) would result in consumers being prioritised, and we wouldn't be able to produce the desired message backlog.

Since our RabbitMQ node has 8 CPU cores and therefore 8 Erlang schedulers, our workload could easily overwhelm the nodes if not limited.
Given that there are 2,000 connection processes, 2,000 channel processes &amp; 500 fully mirrored queues always workings, we have at least ~2,000 always active Erlang processes requiring wall time on 8 schedulers.

## Links

[DataDog dashboard](https://p.datadoghq.com/sb/eac1d6667-75ac04872a)

| RabbitMQ | Metrics                                                                     | Management URL (self-signed SSL cert)                                                   | Username | Password |
| -        | -                                                                           | -                                                                                       | -        | -        |
| v3.7.2   | [Netdata](https://0-netdata-lqs-ha-confirm-multiack-3-7-2.gcp.rabbitmq.com) | [lqs-ha-confirm-multiack-3-7-2](https://lqs-ha-confirm-multiack-3-7-2.gcp.rabbitmq.com) | **demo** | **demo** |

## Point-in-time observations

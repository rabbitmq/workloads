## Non-durable queue, 1KB messages, consumer auto-ack

![](metrics.png)

```
| GCP INSTANCE TYPE    | n1-highcpu-4  |
| -------------------- | ------------  |
| QUEUE                | non-durable   |
| MAX-LENGTH           | 250,000       |
| -------------------- | ------------  |
| PUBLISHERS           | 1             |
| PUBLISHER RATE MSG/S | ∞             |
| MSG SIZE             | 1000          |
| -------------------- | ------------  |
| CONSUMERS            | 1             |
| CONSUMER RATE MSG/S  | ∞             |
```

### `vm_memory_high_watermark_paging_ratio`

When messages are paged to disk, all other queue operations are blocked, including arriving messages from producers and sending messages to consumers.

We set `vm_memory_high_watermark_paging_ratio` to `100.0` so that messages are never paged to disk, not even under memory pressure.
This is a risky setting and must be used in conjunction with the rest of the settings mentioned in this use-case.

### `vm_memory_high_watermark`

Not paging messages to disk when under memory pressure can exhaust all available system memory and result in RabbitMQ termination.
To ensure this doesn't happen, we set `vm_memory_high_watermark` to `0.5`.
For this setting to work correctly, ensure you are using RabbitMQ 3.6.11 or higher [\[1\]](#3.6.11-mem).

### `x-max-length`

If consumers get restarted or stopped, producers can create a significant message backlog.
Since we have disabled message paging to disk, our message backlog is held entirely in memory and can result in the memory alarm getting triggered.
When the memory alarm gets triggered, all producers will be blocked.

So what happens when there are no consumers and producers are blocked by the memory alarm?

Since RabbitMQ is continuously garbage collecting and defragmenting memory, producers will get blocked and unblocked many times, as the memory alarm gets raised and cleared.
At some point, RabbitMQ will not be able to release enough memory to clear the memory alarm and publishers will remain blocked until consumers start making progress on the message backlog.

Since we do not want our publishers blocked, our only other option is to start dropping messages [\[2\]](#queues-dont-fix-overload).

We limit the number of messages in our queue to 250,000.
Once there are 250,000 messages in the queue, the queue will start dropping messages from the head, meaning the oldest messages will be dropped first.
This will reduce performance since the queue process has more work to do, but it will keep the system as a whole in a stable, non-blocked state.
The producers will not be blocked, as would be the case if the memory alarm got triggered, so messages will continue flowing, even if at a reduced rate.

### Notes

* <a name="3.6.11-mem">\[1\]</a>: [ANN Default node RAM consumption calculation strategy will change in 3.6.11](https://groups.google.com/forum/#!msg/rabbitmq-users/TVZt45O3WzU/jkG4SK_rAQAJ)
* <a name="queues-dont-fix-overload">\[2\]</a>: [Queues Don't Fix Overload](https://ferd.ca/queues-don-t-fix-overload.html)

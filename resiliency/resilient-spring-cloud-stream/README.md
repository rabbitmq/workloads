
## Spring Cloud Stream Patterns

Failures:
1. RabbitMQ down from the start
2. Restart single cluster node
3. Rolling restart of all cluster nodes
4. Kill producer connection (repeatedly)
5. kill consumer connection (repeatedly)
6. Block producers (due to alarm, or high water mark to 0)
7. Pause node (due to network partition or cluster running in minority)
8. Unresponsive network connection (w/toxyproxy which drops messages)

### Scenario 1 - Resilient producer/consumer with unreliable producer

Positive aspects:
- resilient producer/consumer: handle failures 1 to 3
- reliable consumer (acknowledgeMode=AUTO)

Negative aspects to fix:
- unreliable sender (not using publisher confirm)
- message loss if consumer is not live (non-durable, auto-delete, exclusive queue)

Issues:
- 2 connections: one for producer, and another one ... Why?

Things not addressed yet:
- use +1 rabbitmq endpoints
- monitoring
- get credentials from VCAP_SERVICES

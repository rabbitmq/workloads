package com.pivotal.resilient.amqp;

public class BindingDescriptor extends AMQPResource {
    private QueueDescriptor queue;
    private ExchangeDescriptor exchange;
    private String routingKey;

    public BindingDescriptor(QueueDescriptor queue, ExchangeDescriptor exchange, String routingKey) {
        super(String.format("binding:%s,%s,%s", exchange.getName(), queue.getName(), routingKey));
        this.queue = queue;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @Override
    public String toString() {
        return "{" +
                "queue=" + queue +
                ", exchange=" + exchange +
                ", routingKey='" + routingKey + '\'' +
                '}';
    }

    public QueueDescriptor getQueue() {
        return queue;
    }

    public ExchangeDescriptor getExchange() {
        return exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }
}

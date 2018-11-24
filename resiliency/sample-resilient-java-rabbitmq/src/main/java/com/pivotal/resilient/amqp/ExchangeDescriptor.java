package com.pivotal.resilient.amqp;

import com.rabbitmq.client.BuiltinExchangeType;

public class ExchangeDescriptor extends AMQPResource {
    private BuiltinExchangeType type;
    boolean durable;

    public ExchangeDescriptor(String name, BuiltinExchangeType type, boolean durable) {
        super(name);
        this.type = type;
        this.durable = durable;
    }

    public BuiltinExchangeType getType() {
        return type;
    }

    public boolean isDurable() {
        return durable;
    }

    @Override
    public String toString() {
        return "{" +
                " name=" + getName() +
                " type=" + type +
                ", durable=" + durable +
                '}';
    }
}

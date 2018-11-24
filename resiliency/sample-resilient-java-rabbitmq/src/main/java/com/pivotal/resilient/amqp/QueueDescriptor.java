package com.pivotal.resilient.amqp;

public class QueueDescriptor extends AMQPResource {
    boolean durable = false;
    boolean passive = false;
    boolean exclusive = false;


    public QueueDescriptor(String name, boolean durable, boolean passive, boolean exclusive) {
        super(name);
        this.durable = durable;
        this.passive = passive;
        this.exclusive = exclusive;
    }
    public QueueDescriptor(String name, boolean durable) {
        this(name, durable, false, false);
    }

    public boolean isDurable() {
        return durable;
    }

    public boolean isPassive() {
        return passive;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    @Override
    public String toString() {
        return "{" +
                " name=" + getName() +
                " durable=" + durable +
                ", passive=" + passive +
                ", exclusive=" + exclusive +
                '}';
    }
    public BindingDescriptor bindWith(ExchangeDescriptor exchange, String routingKey) {
        return new BindingDescriptor(this, exchange, routingKey);
    }
}

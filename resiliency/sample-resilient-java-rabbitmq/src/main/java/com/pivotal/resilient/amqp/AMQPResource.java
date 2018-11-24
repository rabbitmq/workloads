package com.pivotal.resilient.amqp;

public abstract class AMQPResource {
    private String name;

    public AMQPResource(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}

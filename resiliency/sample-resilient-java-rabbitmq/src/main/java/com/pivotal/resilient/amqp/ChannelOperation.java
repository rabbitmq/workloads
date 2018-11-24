package com.pivotal.resilient.amqp;

import com.rabbitmq.client.Channel;

import java.io.IOException;

@FunctionalInterface
public interface ChannelOperation {
    void executeOn(Channel t) throws IOException;
}

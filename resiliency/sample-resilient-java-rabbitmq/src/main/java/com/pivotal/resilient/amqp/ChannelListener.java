package com.pivotal.resilient.amqp;

import com.rabbitmq.client.Channel;

public interface ChannelListener {
    void createdChannel(Channel channel);
}

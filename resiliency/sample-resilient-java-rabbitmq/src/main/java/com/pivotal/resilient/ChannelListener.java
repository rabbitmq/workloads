package com.pivotal.resilient;

import com.rabbitmq.client.Channel;

public interface ChannelListener {
    void createdChannel(Channel channel);
}

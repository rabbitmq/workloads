package com.pivotal.resilient.chaos;

import com.pivotal.resilient.amqp.ChannelListener;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class RandomChannelCloser extends ChaosBunnyAbstract implements ChannelListener {

    protected AtomicReference<Channel> lastCreatedChannel = new AtomicReference<>();
    private Logger logger = LoggerFactory.getLogger(RandomChannelCloser.class);

    public RandomChannelCloser(TaskScheduler scheduler, long frequency) {
        super(scheduler, frequency);
    }

    @Override
    public void run() {
        Channel channel = lastCreatedChannel.getAndSet(null);
        if (channel != null) {
            try {
                logger.warn("RandomChannelCloser forcing channel {} to close", channel.getChannelNumber());
                channel.queueBind("na", "na" , "na");
            } catch (IOException | RuntimeException e) {
                if (!channel.isOpen()) {
                    logger.info("RandomChannelCloser closed channel {}", channel.getChannelNumber());
                }
            }
        }
    }

    @Override
    public void createdChannel(Channel channel) {
        logger.warn("{} received a channel", getName());
        lastCreatedChannel.set(channel);
    }

    @Override
    public String getName() {
        return "RandomChannelCloser";
    }
}

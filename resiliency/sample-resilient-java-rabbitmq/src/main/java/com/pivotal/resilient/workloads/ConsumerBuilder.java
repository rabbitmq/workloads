package com.pivotal.resilient.workloads;

import com.pivotal.resilient.amqp.*;
import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class ConsumerBuilder {

    private Logger logger = LoggerFactory.getLogger(ConsumerBuilder.class);

    private List<AMQPResource> requiredAMQPResources;
    private QueueDescriptor queue;
    private boolean autoAck;
    private int prefetch;
    private String name;
    private MessageConsumerChain messageConsumer;

    ConsumerBuilder(String name) {
        this.name = name;
        messageConsumer = new MessageConsumerChain();
        autoAck = true;
    }

    public static ConsumerBuilder named(String name) {
        return new ConsumerBuilder(name);
    }
    /** queue bound to default exchange */
    public ConsumerBuilder consumeFrom(QueueDescriptor queue) {
        return consumeFrom(queue, null);
    }
    public ConsumerBuilder consumeFrom(QueueDescriptor queue, BindingDescriptor queueBinding) {
        this.queue = queue;
        this.requiredAMQPResources = queueBinding != null ?
                declareAMQPResources(queue, queueBinding) :
                declareAMQPResources(queue);
        return this;
    }
    public Consumer build() {
        logger.info("Created Consumer {}", queue.getName());
        return new Consumer(name, queue, autoAck, prefetch, requiredAMQPResources, messageConsumer, channelListener);
    }

    public ConsumerBuilder withAutoack() {
        this.autoAck = true;
        return this;
    }
    public ConsumerBuilder withPrefetch(int count) {
        this.prefetch = count;
        this.autoAck = false;
        return this;
    }
    public ConsumerBuilder ackEveryMessage() {
        messageConsumer.addConsumer((String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) -> {
            channel.basicAck(envelope.getDeliveryTag(), false);
        });
        return this;
    }
    public ConsumerBuilder nackEveryMessage(boolean requeue) {
        messageConsumer.addConsumer((String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) -> {
            channel.basicNack(envelope.getDeliveryTag(), false, requeue);
        });
        return this;
    }
    public ConsumerBuilder throwException(String message) {
        messageConsumer.addConsumer((String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) -> {
            throw new RuntimeException(message);
        });
        return this;
    }
    public ConsumerBuilder cancelAfterFirstMessage() {
        messageConsumer.addConsumer(new MessageConsumer() {
            int messageCount;
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) throws IOException {
                if (messageCount++ == 1) {
                    logger.info("{} cancelling subscription {} on channel {}", name, consumerTag, channel.getChannelNumber());
                    channel.basicCancel(consumerTag);
                }
            }
        });
        return this;
    }
    public ConsumerBuilder throwExceptionAfter(String message, final int afterMessageCount) {
        messageConsumer.addConsumer(new MessageConsumer() {
            int count = 1;
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) throws IOException {
                if (count++ >= afterMessageCount)
                    throw new RuntimeException(message);
            }
        });
        return this;
    }

    public ConsumerBuilder withConsumptionDelay(long delayMs) {
        messageConsumer.addConsumer((consumerTag, envelope, properties, body, channel) -> {
            try {
                long deliveryTag = envelope.getDeliveryTag();
                String messageId = properties.getMessageId();
                logger.debug("{} delaying message [{}, {}] consumption with {} msec", name, deliveryTag, messageId, delayMs);
                Thread.sleep(delayMs);
                logger.debug("{} finished delaying message [{}, {}]", name, deliveryTag, messageId);
            } catch (InterruptedException e) {

            }
        });
        return this;
    }

    class MessageConsumerChain implements MessageConsumer {
        List<MessageConsumer> consumers;

        public MessageConsumerChain() {
            this.consumers = new ArrayList<>();
        }
        void addConsumer(MessageConsumer consumer) {
            consumers.add(consumer);
        }
        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) {
            consumers.forEach(c -> {
                try {
                    c.handleDelivery(consumerTag, envelope, properties, body, channel);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
    public ConsumerBuilder handleMessagesWith(MessageConsumer consumer) {
        this.messageConsumer.addConsumer(consumer);
        return this;
    }

    public ConsumerBuilder handleMessagesWith(MessageConsumer forFirstMessage, MessageConsumer forNextMessages) {
        this.messageConsumer.addConsumer(new MessageConsumer() {
            boolean isFirstMessage = true;

            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) throws IOException {
                if (isFirstMessage) {
                    isFirstMessage = false;
                    forFirstMessage.handleDelivery(consumerTag, envelope, properties, body, channel);
                }else {
                    forNextMessages.handleDelivery(consumerTag, envelope, properties, body, channel);
                }
            }
        });
        return this;
    }

    private ChannelListener channelListener;

    public ConsumerBuilder withChannelListener(ChannelListener channelListener) {
        this.channelListener = channelListener;
        return this;
    }


    private List<AMQPResource>  declareAMQPResources(AMQPResource ... resources) {
        return  Collections.unmodifiableList(Arrays.asList(resources));
    }


}


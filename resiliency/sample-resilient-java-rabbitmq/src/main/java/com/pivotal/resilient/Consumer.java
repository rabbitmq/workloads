package com.pivotal.resilient;

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

class Consumer implements AMQPConnectionRequester {

    private Logger logger = LoggerFactory.getLogger(Consumer.class);

    private List<AMQPResource> requiredAMQPResources;
    private QueueDescriptor queue;
    private boolean autoAck = true;
    private int prefetch;
    private String name;
    private MessageConsumerChain messageConsumer;
    private String consumerTag;

    public Consumer(String name, QueueDescriptor queue) {
        this(name, queue, null);
    }
    public Consumer(String name, QueueDescriptor queue, BindingDescriptor queueBinding) {
        this.name = name;
        this.queue = queue;
        this.autoAck = autoAck;
        this.requiredAMQPResources = queueBinding != null ?
                declareAMQPResources(queue, queueBinding) :
                declareAMQPResources(queue);
        logger.info("Created Consumer {}", queue.getName());
        messageConsumer = new MessageConsumerChain();
    }

    public Consumer withAutoack() {
        this.autoAck = true;
        return this;
    }
    public Consumer withPrefetch(int count) {
        this.prefetch = count;
        this.autoAck = false;
        return this;
    }
    public Consumer ackEveryMessage() {
        messageConsumer.addConsumer((Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) -> {
            channel.basicAck(envelope.getDeliveryTag(), false);
        });
        return this;
    }
    public Consumer nackEveryMessage(boolean requeue) {
        messageConsumer.addConsumer((Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) -> {
            channel.basicNack(envelope.getDeliveryTag(), false, requeue);
        });
        return this;
    }
    public Consumer throwException(String message) {
        messageConsumer.addConsumer((Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) -> {
            throw new RuntimeException(message);
        });
        return this;
    }
    public Consumer cancelAfterFirstMessage() {
        messageConsumer.addConsumer(new MessageConsumer() {
            int messageCount;
            @Override
            public void handleDelivery(Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) throws IOException {
                if (messageCount++ == 1) {
                    logger.info("{} cancelling subscription {} on channel {}", getName(), consumerTag, channel.getChannelNumber());
                    channel.basicCancel(consumerTag);
                }
            }
        });
        return this;
    }
    public Consumer throwExceptionAfter(String message, final int afterMessageCount) {
        messageConsumer.addConsumer(new MessageConsumer() {
            int count = 1;
            @Override
            public void handleDelivery(Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) throws IOException {
                if (count++ >= afterMessageCount)
                    throw new RuntimeException(message);
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
        public void handleDelivery(Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) throws IOException {
            consumers.forEach(c -> {
                try {
                    c.handleDelivery(envelope, properties, body, channel);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
    public Consumer handleMessagesWith(MessageConsumer consumer) {
        this.messageConsumer.addConsumer(consumer);
        return this;
    }
    public Consumer handleMessagesWith(MessageConsumer forFirstMessage, MessageConsumer forNextMessages) {
        this.messageConsumer.addConsumer(new MessageConsumer() {
            boolean isFirstMessage = true;

            @Override
            public void handleDelivery(Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) throws IOException {
                if (isFirstMessage) {
                    isFirstMessage = false;
                    forFirstMessage.handleDelivery(envelope, properties, body, channel);
                }else {
                    forNextMessages.handleDelivery(envelope, properties, body, channel);
                }
            }
        });
        return this;
    }

    private ChannelListener channelListener;

    public void setChannelListener(ChannelListener channelListener) {
        this.channelListener = channelListener;
    }

    public List<AMQPResource> getRequiredAMQPResources() {
        return requiredAMQPResources;
    }

    private List<AMQPResource>  declareAMQPResources(AMQPResource ... resources) {
        return  Collections.unmodifiableList(Arrays.asList(resources));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void connectionAvailable(Connection connection) {
        try {
            subscribeForMessages(connection);
        }catch(IOException e) {
            e.printStackTrace();
            logger.error("{} failed to consume messages", getName(), e.getMessage());
        }
    }

    @Override
    public void connectionLost() {
        logger.warn("{} is temporary out of service. AMQP requiredResources are not resolved yet", getName());
    }

    @Override
    public boolean isHealthy() {
        return subscriptionChannel.get() != null;
    }

    private void handleMessage(String consumerTag, Delivery delivery, Channel channel) throws IOException {
        Envelope envelope = delivery.getEnvelope();
        String routingKey = envelope.getRoutingKey();
        long deliveryTag = envelope.getDeliveryTag();
        String correlationId = delivery.getProperties().getCorrelationId();
        logger.debug("{} received message on {} with routingKey {}, deliveryTag {}, correlationId {} -  Thread {}",
                getName(), queue.getName(), routingKey, deliveryTag, correlationId, Thread.currentThread().getId());

        delegateToMessageConsumer(envelope, delivery.getProperties(), delivery.getBody(), channel);

    }
    private void delegateToMessageConsumer(Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) throws IOException {
        if (messageConsumer != null) {
            messageConsumer.handleDelivery(envelope, properties, body, channel);
        }
    }

    private void handleCancel(String consumerTag) {
        logger.warn("{} received cancel event on consumerTag {}", getName(), consumerTag);
        closeSubscriptionChannel();
        consumerTag = null;
    }

    private void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
        logger.warn("{} received shutdownSignal event on consumerTag {}", getName(), consumerTag);
        consumerTag = null;
        closeSubscriptionChannel();
    }

    private AtomicReference<Channel> subscriptionChannel = new AtomicReference<>();

    private void closeSubscriptionChannel() {
        Channel channel = subscriptionChannel.getAndSet(null);
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException | TimeoutException e) {
            logger.warn("Failed to close channel. Ignore", e);
            throw new RuntimeException(e);
        }
    }

    private void subscribeForMessages(Connection amqpConn) throws IOException {
        closeSubscriptionChannel();

        logger.debug("{} placing subscription", getName());
        Channel channel = amqpConn.createChannel();
        subscriptionChannel.set(channel);
        channel.basicQos(prefetch);
        consumerTag = channel.basicConsume(queue.getName(),  autoAck,
                (consumerTag, delivery) -> handleMessage(consumerTag, delivery, channel),
                (consumerTag) -> handleCancel(consumerTag),
                (consumerTag, sig) -> handleShutdownSignal(consumerTag, sig));

        reportChannelListener(channel);
    }

    private void reportChannelListener(Channel channel) {
        if (channelListener !=  null) {
            channelListener.createdChannel(channel);
        }
    }

    public void shutdown() {
        logger.info("{} Shutting down ...", getName());
        Channel channel = subscriptionChannel != null ? subscriptionChannel.get() : null;
        try {
            if (channel !=  null) channel.basicCancel(consumerTag);
        } catch (IOException e) {
            logger.warn("{} Failed to cancel subscription {}", getName(), consumerTag);
        }finally {
            consumerTag = null;
        }
        logger.info("{} Shut down completed", getName());
    }

}
interface MessageConsumer {
    void handleDelivery(Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) throws IOException;
}

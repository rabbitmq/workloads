package com.pivotal.resilient.workloads;

import com.pivotal.resilient.amqp.AMQPConnectionRequester;
import com.pivotal.resilient.amqp.AMQPResource;
import com.pivotal.resilient.amqp.ChannelListener;
import com.pivotal.resilient.amqp.QueueDescriptor;
import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class Consumer implements AMQPConnectionRequester {

    private Logger logger = LoggerFactory.getLogger(Consumer.class);

    private List<AMQPResource> requiredAMQPResources;
    private QueueDescriptor queue;
    private boolean autoAck;
    private int prefetch;
    private String name;
    private MessageConsumer messageConsumer;
    private String consumerTag;
    private ChannelListener channelListener;

    Consumer(String name,
             QueueDescriptor queue,
             boolean autoAck,
             int prefetch,
             List<AMQPResource> requiredAMQPResources,
             MessageConsumer messageConsumer,
             ChannelListener channelListener) {
        this.name = name;
        this.queue = queue;
        this.autoAck = autoAck;
        this.prefetch = prefetch;
        this.requiredAMQPResources = requiredAMQPResources;
        this.messageConsumer = messageConsumer;
        logger.info("Created Consumer {}", queue.getName());

    }

    public List<AMQPResource> getRequiredAMQPResources() {
        return requiredAMQPResources;
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
    public void connectionBlocked(String reason) {

    }

    @Override
    public void connectionUnblocked(Connection connection) {

    }

    @Override
    public boolean isHealthy() {
        return subscriptionChannel.get() != null;
    }

    private void handleMessage(String consumerTag, Delivery delivery, Channel channel) throws IOException {
        Envelope envelope = delivery.getEnvelope();
        String routingKey = envelope.getRoutingKey();
        long deliveryTag = envelope.getDeliveryTag();
        String messageId = delivery.getProperties().getMessageId();
        logger.debug("{} received message on {} with routingKey {}, deliveryTag {}, messageI {}",
                getName(),
                queue.getName(),
                routingKey,
                deliveryTag,
                messageId);

        delegateToMessageConsumer(consumerTag, envelope, delivery.getProperties(), delivery.getBody(), channel);

    }
    private void delegateToMessageConsumer(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) throws IOException {
        if (messageConsumer != null) {
            messageConsumer.handleDelivery(consumerTag, envelope, properties, body, channel);
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
    void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) throws IOException;
}

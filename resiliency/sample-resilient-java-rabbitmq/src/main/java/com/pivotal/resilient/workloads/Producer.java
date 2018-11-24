package com.pivotal.resilient.workloads;

import com.pivotal.resilient.amqp.AMQPConnectionRequester;
import com.pivotal.resilient.amqp.AMQPResource;
import com.pivotal.resilient.amqp.ChannelListener;
import com.pivotal.resilient.amqp.ExchangeDescriptor;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class Producer implements AMQPConnectionRequester {
    private Logger logger = LoggerFactory.getLogger(Producer.class);

    private String name;
    private ChannelHandler channelHandler;
    private AMQPConnectionRequester delegate;
    private List<AMQPResource> requiredAMQPResources;

    Producer(String name, ChannelHandler channelHandler, AMQPConnectionRequester delegate, List<AMQPResource> requiredAMQPResources) {
        this.name = name;
        this.channelHandler = channelHandler;
        this.delegate = delegate;
        this.requiredAMQPResources = requiredAMQPResources;
        logger.info("Created Producer {}", name);
    }

    public List<AMQPResource> getRequiredAMQPResources() {
        return requiredAMQPResources;
    }
    private List<AMQPResource>  declareAMQPResources(AMQPResource ... resources) {
        return  Collections.unmodifiableList(Arrays.asList(resources));
    }

    public static Sender sender(String name, ExchangeDescriptor exchange, String routingKey) {
        return new Sender(name, new ChannelHandler(name), exchange, routingKey);
    }
    public static Sender sender(String name, String routingKey) {
        return new Sender(name, new ChannelHandler(name), null, routingKey);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void connectionAvailable(final Connection connection) {
        delegate.connectionAvailable(connection);
    }

    @Override
    public void connectionLost() {
        delegate.connectionLost();
    }

    @Override
    public void connectionBlocked(String reason) {
       delegate.connectionBlocked(reason);
    }

    @Override
    public void connectionUnblocked(Connection connection) {
        delegate.connectionUnblocked(connection);
    }

    @Override
    public boolean isHealthy() {
        return delegate.isHealthy();
    }

    public void setChannelListener(ChannelListener channelListener) {
        this.channelHandler.setChannelListener(channelListener);
    }


}
class ChannelHandler {
    private Logger logger = LoggerFactory.getLogger(ChannelHandler.class);
    Channel channel;
    String name;

    public ChannelHandler(String name) {
        this.name = name;
    }

    void drainChannel() {
        if (channel != null) {
            try {
                logger.debug("{} Draining channel {}", name, channel.getChannelNumber());
                channel.close();
            }catch(Exception e) {

            }
        }
        channel = null;
    }

    Channel getOrCreateChannel(Connection connection, boolean withPublisherConfirms) throws IOException {
        if (channel == null) {
            channel = connection.createChannel();
            logger.info("{} created channel {}", name, channel.getChannelNumber());

            if (withPublisherConfirms) channel.confirmSelect();
             if (channelListener !=  null) channelListener.createdChannel(channel);

        }

        return channel;
    }

    private ChannelListener channelListener;

    public void setChannelListener(ChannelListener channelListener) {
        this.channelListener = channelListener;
    }


    public void waitForConfirmation() throws InterruptedException {
        if (channel == null) return;
        channel.waitForConfirms();
    }
}

class Sender {
    private String name;
    private Logger logger = LoggerFactory.getLogger(Sender.class);
    private ExchangeDescriptor exchange;
    private String routingKey;
    private byte[] messageBodyBytes = "Hello, world!".getBytes();
    private int messageId;
    private String messageIdPrefix = String.valueOf(System.currentTimeMillis());
    private ChannelHandler channelHandler;

    public Sender(String name, ChannelHandler channelHandler, ExchangeDescriptor exchange, String routingKey) {
        this.name = name;
        this.channelHandler = channelHandler;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public String getName() {
        return name;
    }

    public boolean sendMessage(Connection connection) {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().
                messageId(nextMessageId()).build();
        try {
            String exchangeName = exchange != null ? exchange.getName() : "";
            channelHandler.getOrCreateChannel(connection, usePublisherConfirms).basicPublish(exchangeName, routingKey, properties, messageBodyBytes);
            logger.debug("{} Sent message with messageId {}", name, properties.getMessageId());
            confirmMessageWasSent();
            return true;
        } catch(AlreadyClosedException | IOException e) {
            logger.error("{} Failed to send a message with messageId {} due to {}", name, properties.getMessageId(), e.getMessage());
            channelHandler.drainChannel();
        } catch(InterruptedException e) {
            logger.error("{} Failed to receive a publisher confirmation", name);
        }
        return false;

    }
    private void confirmMessageWasSent() throws InterruptedException {
        if (usePublisherConfirms) channelHandler.waitForConfirmation();
        messageId++;
    }
    private String nextMessageId() {
        return String.format("%s-%d", messageIdPrefix, messageId);
    }

    public Producer atFixedRate(TaskScheduler scheduler, long rateMillis) {
        List<AMQPResource> amqpResources = exchange != null ? Arrays.asList(exchange) : Collections.emptyList();

        return new Producer(name, channelHandler,
                new SendAtFixedRate(this, scheduler, rateMillis),
                amqpResources);
    }

    private boolean usePublisherConfirms;

    public Sender withPublisherConfirmation() {
        this.usePublisherConfirms = true;
        return this;
    }

    public Producer once(int messageCount) {
        List<AMQPResource> amqpResources = exchange != null ? Arrays.asList(exchange) : Collections.emptyList();

        return new Producer(name, channelHandler,
                new SendOnce(this, messageCount),
                amqpResources);
    }

}

class SendAtFixedRate implements AMQPConnectionRequester {
    private Logger logger = LoggerFactory.getLogger(SendAtFixedRate.class);

    private long fixedRate;
    private ScheduledFuture<?> sendMessageAtFixedRate;
    private TaskScheduler scheduler;
    private Sender sender;
    private AtomicBoolean failedToSendLastMessage = new AtomicBoolean(false);

    public SendAtFixedRate(Sender sender, TaskScheduler scheduler, long fixedRate) {
        this.fixedRate = fixedRate;
        this.scheduler = scheduler;
        this.sender = sender;
    }

    @Override
    public String getName() {
        return sender.getName();
    }

    @Override
    public void connectionAvailable(Connection connection) {
        logger.info("{} has received a connection. Scheduling producer timer", getName());
        if (sendMessageAtFixedRate != null) {
            return; // it is already running. This method can be called several times for the same connection
        }

        sendMessageAtFixedRate = scheduler.scheduleAtFixedRate(() -> send(connection), this.fixedRate);
    }

    private void send(Connection connection) {
        try {
            failedToSendLastMessage.set(!sender.sendMessage(connection));
        }catch(RuntimeException e) {
            failedToSendLastMessage.set(true);
        }
    }

    @Override
    public void connectionLost() {
        logger.info("{} has received a connection. Scheduling producer timer", getName());
        if (sendMessageAtFixedRate != null) {
            sendMessageAtFixedRate.cancel(true);
            sendMessageAtFixedRate = null;
        }
    }

    @Override
    public void connectionBlocked(String reason) {
        // probably we want to stop the timer
    }

    @Override
    public void connectionUnblocked(Connection connection) {
        // reschedule the timer when we stopped it upon connectionBlocked event arrival
    }

    @Override
    public boolean isHealthy() {
        return sendMessageAtFixedRate != null && !failedToSendLastMessage.get();
    }
}
class SendOnce implements AMQPConnectionRequester {
    private Logger logger = LoggerFactory.getLogger(SendOnce.class);
    private Sender sender;
    private int messagesToSend;

    public SendOnce(Sender sender, int messagesToSend) {
        this.sender = sender;
        this.messagesToSend = messagesToSend;
    }

    @Override
    public String getName() {
        return sender.getName();
    }

    @Override
    public void connectionAvailable(Connection connection) {
        logger.info("{} has received a connection. Continue sending {} messages ", getName(), messagesToSend);
        while (messagesToSend > 0) {
            sender.sendMessage(connection);
            messagesToSend--;
        }
    }

    @Override
    public void connectionLost() {
        logger.info("{} has received a connection. Scheduling producer timer", getName());
    }

    @Override
    public void connectionBlocked(String reason) {

    }

    @Override
    public void connectionUnblocked(Connection connection) {

    }

    @Override
    public boolean isHealthy() {
        return true;
    }
}
package com.pivotal.resilient.chaos;

import com.pivotal.resilient.amqp.AMQPConnectionRequester;
import com.pivotal.resilient.amqp.ChannelOperation;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.springframework.scheduling.TaskScheduler;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeoutException;

abstract class ChaosBunnyAbstract implements Runnable, AMQPConnectionRequester {

    private TaskScheduler scheduler;
    private Connection connection;
    private ScheduledFuture<?> chaosScheduler;

    private long frequency;

    public ChaosBunnyAbstract(TaskScheduler scheduler, long frequency) {
        this.frequency = frequency;
        this.scheduler = scheduler;
    }

    protected void executeOnChannel(ChannelOperation operation) {
        Channel channel;
        try {
            channel = connection.createChannel();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            operation.executeOn(channel);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                channel.close();
            } catch (IOException | TimeoutException e) {

            }
        }
    }

    @Override
    public void connectionAvailable(Connection connection) {
        this.connection = connection;
        chaosScheduler = scheduler.scheduleAtFixedRate(this, Instant.now().plusMillis(frequency), Duration.ofMillis(frequency));
    }

    @Override
    public void connectionLost() {
        if (chaosScheduler != null) {
            chaosScheduler.cancel(true);
        }
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

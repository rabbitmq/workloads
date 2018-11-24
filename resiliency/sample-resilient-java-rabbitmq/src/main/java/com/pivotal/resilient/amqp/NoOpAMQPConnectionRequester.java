package com.pivotal.resilient.amqp;

import com.rabbitmq.client.Connection;

public class NoOpAMQPConnectionRequester implements AMQPConnectionRequester {

    @Override
    public String getName() {
        return "NoOp";
    }

    @Override
    public void connectionAvailable(Connection channel) {

    }

    @Override
    public void connectionLost() {

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

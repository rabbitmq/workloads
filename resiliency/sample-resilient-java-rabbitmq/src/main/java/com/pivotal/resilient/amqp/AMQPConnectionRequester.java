package com.pivotal.resilient.amqp;

import com.rabbitmq.client.Connection;

public interface AMQPConnectionRequester {
    String getName();
    void connectionAvailable(Connection connection);
    void connectionLost();
    void connectionBlocked(String reason);
    void connectionUnblocked(Connection connection);
    boolean isHealthy();
}

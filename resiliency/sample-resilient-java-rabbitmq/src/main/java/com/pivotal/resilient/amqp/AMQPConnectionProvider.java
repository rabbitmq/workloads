package com.pivotal.resilient.amqp;

import java.util.List;

public interface AMQPConnectionProvider {
    void requestConnectionFor(String name, List<AMQPResource> resources, AMQPConnectionRequester claimer);
    void requestConnectionFor(String name, List<AMQPResource> resources);
    void releaseConnectionsFor(String name);
}

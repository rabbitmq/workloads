package com.pivotal.resilient;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.service.common.AmqpServiceInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class RabbitMQConfiguration {

    private Logger logger = LoggerFactory.getLogger(RabbitMQConfiguration.class);

    @Autowired
    private TaskScheduler executor;

    @Autowired
    ConnectionFactory connectionFactory;

    @Bean
    public CloudFactory cloudFactory() {
        CloudFactory cloudFactory = new CloudFactory();
        return cloudFactory;
    }

    @Bean
    public Cloud cloud(CloudFactory factory) {
        return factory.getCloud();
    }

    @Bean
    public ConnectionFactory amqpConnectionFactory(Cloud cloud) throws NoSuchAlgorithmException, KeyManagementException, URISyntaxException {
        ConnectionFactory factory = new ConnectionFactory();

        initConnetionFactoryWithAMQPCredentials(cloud, factory);

        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(false);
        factory.setConnectionTimeout(10000);
        factory.setNetworkRecoveryInterval(1000); // how long will automatic recovery wait before attempting to reconnect, in ms; default is 5000

        logger.info("Creating connection factory using username:{}, vhost:{}", factory.getUsername(), factory.getVirtualHost());

        return factory;
    }
    private void initConnetionFactoryWithAMQPCredentials(Cloud cloud, ConnectionFactory factory) {
        AmqpServiceInfo amqp = cloud.getSingletonServiceInfoByType(AmqpServiceInfo.class);
        factory.setUsername(amqp.getUserName());
        factory.setPassword(amqp.getPassword());
        factory.setVirtualHost(amqp.getVirtualHost());
    }
    private List<Address> getAmqpAddressesFrom(Cloud cloud) {
        AmqpServiceInfo amqp = cloud.getSingletonServiceInfoByType(AmqpServiceInfo.class);
        List<String> uris = new ArrayList<>();
        if (amqp.getUris() != null) {
            uris.addAll(amqp.getUris());
        }else {
            uris.add(amqp.getUri());
        }
        return uris.stream().map(uri -> {
            try {
                URI amqpURI = new URI(uri);
                return new Address(amqpURI.getHost(), amqpURI.getPort());
            } catch (URISyntaxException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
    }

    @Bean(destroyMethod = "shutdown", name = "consumer")
    public AMQPConnectionProvider consumer(Cloud cloud, ConnectionFactory factory, TaskScheduler scheduler) {
        logger.info("Creating AMQPConnectionProvider:consumer");
        AMQPConnectionProviderImpl provider =  new AMQPConnectionProviderImpl("consumer", factory, getAmqpAddressesFrom(cloud), scheduler);
        provider.start();
        return provider;
    }

    @Bean(destroyMethod = "shutdown")
    @Primary
    public AMQPConnectionProvider producer(Cloud cloud, ConnectionFactory factory, TaskScheduler scheduler) {
        logger.info("Creating AMQPConnectionProvider:producer");
        AMQPConnectionProviderImpl provider =  new AMQPConnectionProviderImpl("producer", factory, getAmqpAddressesFrom(cloud), scheduler);
        provider.start();
        return provider;
    }

}

class NoOpAMQPConnectionRequester implements AMQPConnectionRequester {

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
interface AMQPConnectionRequester {
    String getName();
    void connectionAvailable(Connection connection);
    void connectionLost();
    void connectionBlocked(String reason);
    void connectionUnblocked(Connection connection);
    boolean isHealthy();
}

interface AMQPConnectionProvider {
    void manageConnectionFor(String name, List<AMQPResource> resources, AMQPConnectionRequester claimer);
    void manageConnectionFor(String name, List<AMQPResource> resources);
    void unmanageConnectionsFor(String name);
}

abstract class AMQPResource {
    private String name;

    public AMQPResource(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
class BindingDescriptor extends AMQPResource {
    private QueueDescriptor queue;
    private ExchangeDescriptor exchange;
    private String routingKey;

    public BindingDescriptor(QueueDescriptor queue, ExchangeDescriptor exchange, String routingKey) {
        super(String.format("binding:%s,%s,%s", exchange.getName(), queue.getName(), routingKey));
        this.queue = queue;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @Override
    public String toString() {
        return "{" +
                "queue=" + queue +
                ", exchange=" + exchange +
                ", routingKey='" + routingKey + '\'' +
                '}';
    }

    public QueueDescriptor getQueue() {
        return queue;
    }

    public ExchangeDescriptor getExchange() {
        return exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }
}
class ExchangeDescriptor extends AMQPResource {
    private BuiltinExchangeType type;
    boolean durable;

    public ExchangeDescriptor(String name, BuiltinExchangeType type, boolean durable) {
        super(name);
        this.type = type;
        this.durable = durable;
    }

    public BuiltinExchangeType getType() {
        return type;
    }

    public boolean isDurable() {
        return durable;
    }

    @Override
    public String toString() {
        return "{" +
                " name=" + getName() +
                " type=" + type +
                ", durable=" + durable +
                '}';
    }
}

class QueueDescriptor extends AMQPResource {
    boolean durable = false;
    boolean passive = false;
    boolean exclusive = false;


    public QueueDescriptor(String name, boolean durable, boolean passive, boolean exclusive) {
        super(name);
        this.durable = durable;
        this.passive = passive;
        this.exclusive = exclusive;
    }
    public QueueDescriptor(String name, boolean durable) {
        this(name, durable, false, false);
    }

    public boolean isDurable() {
        return durable;
    }

    public boolean isPassive() {
        return passive;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    @Override
    public String toString() {
        return "{" +
                " name=" + getName() +
                " durable=" + durable +
                ", passive=" + passive +
                ", exclusive=" + exclusive +
                '}';
    }
    public BindingDescriptor bindWith(ExchangeDescriptor exchange, String routingKey) {
        return new BindingDescriptor(this, exchange, routingKey);
    }
}
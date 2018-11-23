package com.pivotal.resilient.samplespringrabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.MicrometerMetricsCollector;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.service.messaging.RabbitConnectionFactoryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;


@Configuration
public class RabbitMQConfiguration {
    private Logger logger = LoggerFactory.getLogger(RabbitMQConfiguration.class);

    @Value("${spring.application.name:}") String applicationName;

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
    public RabbitConnectionFactoryConfig rabbitConnectionFactoryConfig(RabbitProperties rabbitProperties) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("channelCacheSize", rabbitProperties.getCache().getChannel().getSize());
        RabbitConnectionFactoryConfig connectionFactoryConfig = new RabbitConnectionFactoryConfig(properties);
        return connectionFactoryConfig;
    }

    @Bean
    public ChannelChurnMonitor producerChurnMonitor(MeterRegistry meterRegistry,
                                                   @Qualifier("producer") org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {

        CachingConnectionFactory cachedFactory = (CachingConnectionFactory)connectionFactory;
        ChannelChurnMonitor channelChurnMonitor = new ChannelChurnMonitor("producer");
        cachedFactory.addChannelListener(channelChurnMonitor);
        cachedFactory.addConnectionListener(channelChurnMonitor);
        channelChurnMonitor.addMetrics(meterRegistry);

        com.rabbitmq.client.ConnectionFactory rabbitConnectionFactory =  cachedFactory.getRabbitConnectionFactory();
        MicrometerMetricsCollector metricsCollector = new MicrometerMetricsCollector(meterRegistry,"rabbitmq.producer");
        rabbitConnectionFactory.setMetricsCollector(metricsCollector);

        return channelChurnMonitor;
    }
    @Bean
    public ChannelChurnMonitor consumerChurnMonitor(MeterRegistry meterRegistry,
                                                    @Qualifier("consumer") org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {

        CachingConnectionFactory cachedFactory = (CachingConnectionFactory)connectionFactory;

        ChannelChurnMonitor channelChurnMonitor = new ChannelChurnMonitor("consumer");
        cachedFactory.addChannelListener(channelChurnMonitor);
        cachedFactory.addConnectionListener(channelChurnMonitor);
        channelChurnMonitor.addMetrics(meterRegistry);

        com.rabbitmq.client.ConnectionFactory rabbitConnectionFactory =  cachedFactory.getRabbitConnectionFactory();
        MicrometerMetricsCollector metricsCollector = new MicrometerMetricsCollector(meterRegistry,"rabbitmq.consumer");
        rabbitConnectionFactory.setMetricsCollector(metricsCollector);

        return channelChurnMonitor;
    }

    @Bean("consumer")
    public org.springframework.amqp.rabbit.connection.ConnectionFactory consumer(Cloud cloud, RabbitConnectionFactoryConfig config) {
        ConnectionFactory factory = cloud.getSingletonServiceConnector(org.springframework.amqp.rabbit.connection.ConnectionFactory.class,
                config);
        return factory;
    }
    @Bean("producer")
    @Primary
    public org.springframework.amqp.rabbit.connection.ConnectionFactory producer(Cloud cloud, RabbitConnectionFactoryConfig config) {
        ConnectionFactory factory = cloud.getSingletonServiceConnector(org.springframework.amqp.rabbit.connection.ConnectionFactory.class,
                config);
        return factory.getPublisherConnectionFactory();
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory factory) {
        RabbitAdmin admin  = new RabbitAdmin(factory);
        admin.setIgnoreDeclarationExceptions(true);  // This is key if we only have just on RabbitAdmin otherwise one
                                                     // failure could cause the rest of the declarations to fail
        return admin;
    }
}

class ChannelChurnMonitor implements ChannelListener, ConnectionListener {

    private Counter channelCreated;
    private Counter connectionCreated;
    private Counter channelShutdown;
    private Counter channelShutdownDueToFailure;
    private Counter connectionShutdown;
    private Counter connectionShutdownDueToFailure;
    private String name;

    public ChannelChurnMonitor(String name) {
        this.name = name;
    }

    @Override
    public void onCreate(Channel channel, boolean b) {
        channelCreated.increment();
    }

    @Override
    public void onShutDown(ShutdownSignalException signal) {
        if (signal.isHardError()) onConnectionShutDown(signal);
        else onChannelShutDown(signal);
    }

    @Override
    public void onCreate(Connection connection) {
        connectionCreated.increment();
    }


    public void onChannelShutDown(ShutdownSignalException signal) {
        if (signal.isInitiatedByApplication()) {
            channelShutdown.increment();
        }else {
            channelShutdownDueToFailure.increment();
        }
    }
    public void onConnectionShutDown(ShutdownSignalException signal) {
        if (signal.isInitiatedByApplication()) {
            connectionShutdown.increment();
        }else {
            connectionShutdownDueToFailure.increment();
        }
    }

    public void addMetrics(MeterRegistry meterRegistry) {
        channelCreated = meterRegistry.counter(String.format("%s.channel.created", name));
        channelShutdown = meterRegistry.counter(String.format("%s.channel.shutdown", name));
        channelShutdownDueToFailure =  meterRegistry.counter(String.format("%s.channel.shutdownDueToFailure", name));

        connectionCreated = meterRegistry.counter(String.format("%s.connection.created", name));
        connectionShutdown = meterRegistry.counter(String.format("%s.connection.shutdown", name));
        connectionShutdownDueToFailure =  meterRegistry.counter(String.format("%s.connection.shutdownDueToFailure", name));
    }
}
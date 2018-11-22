package com.pivotal.resilient.samplespringrabbitmq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.amqp.rabbit.connection.SimplePropertyValueConnectionNameStrategy;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.service.MapServiceConnectorConfig;
import org.springframework.cloud.service.messaging.RabbitConnectionFactoryConfig;
import org.springframework.cloud.service.messaging.RabbitConnectionFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


@Configuration
public class RabbitMQConfiguration {
    private Logger logger = LoggerFactory.getLogger(RabbitMQConfiguration.class);


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
    public ConnectionNameStrategy connectionNameStrategy() {
        return new SimplePropertyValueConnectionNameStrategy("spring.application.name");
    }

    @Bean
    public RabbitConnectionFactoryConfig rabbitConnectionFactoryConfig(RabbitProperties rabbitProperties) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("channelCacheSize", rabbitProperties.getCache().getChannel().getSize());
        RabbitConnectionFactoryConfig connectionFactoryConfig = new RabbitConnectionFactoryConfig(properties);
        return connectionFactoryConfig;
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


package com.pivotal.resilient.resilientspringrabbitmq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.service.messaging.RabbitConnectionFactoryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


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
    public ConnectionFactory connectionFactory(Cloud cloud) {
        ConnectionFactory factory = cloud.getSingletonServiceConnector(ConnectionFactory.class,
                null);
        return factory;
    }

}


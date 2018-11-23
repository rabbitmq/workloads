package com.pivotal.resilient.resilientspringrabbitmq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.service.messaging.RabbitConnectionFactoryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;


@Configuration
@EnableScheduling
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

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory factory) {
        RabbitAdmin admin  = new RabbitAdmin(factory);

        // This is key if we only have just on RabbitAdmin otherwise one
        // failure could cause the rest of the declarations to fail
        admin.setIgnoreDeclarationExceptions(true);

        return admin;
    }
}


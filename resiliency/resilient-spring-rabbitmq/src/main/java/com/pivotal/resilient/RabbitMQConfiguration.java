package com.pivotal.resilient;

import com.pivotal.cloud.service.messaging.SpringRabbitConnectionFactoryCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.service.common.AmqpServiceInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;


@Configuration
@EnableScheduling
public class RabbitMQConfiguration {
    private Logger logger = LoggerFactory.getLogger(RabbitMQConfiguration.class);

    private SpringRabbitConnectionFactoryCreator rabbitConnectionFactoryCreator = new SpringRabbitConnectionFactoryCreator();

    @Autowired
    RabbitProperties rabbitProperties;

    @Autowired
    ObjectProvider<ConnectionNameStrategy> connectionNameStrategies;


    @Bean
    public CloudFactory cloudFactory() {
        CloudFactory cloudFactory = new CloudFactory();
        return cloudFactory;
    }

    @Bean
    public Cloud cloud(CloudFactory factory) {
        return factory.getCloud();
    }


    @Bean("consumer")
    public org.springframework.amqp.rabbit.connection.ConnectionFactory consumer(Cloud cloud) {
        logger.info("Creating consumer Spring ConnectionFactory ...");
        ConnectionFactory factory = rabbitConnectionFactoryCreator.create(
                cloud.getSingletonServiceInfoByType(AmqpServiceInfo.class),
                rabbitProperties, connectionNameStrategies);

        return factory;
    }

    @Bean("producer")
    @Primary
    public org.springframework.amqp.rabbit.connection.ConnectionFactory producer(Cloud cloud,
                                                                                 @Qualifier("consumer") ConnectionFactory consumerConnectionFactory) {
        logger.info("Creating producer Spring ConnectionFactory ...");
        return consumerConnectionFactory.getPublisherConnectionFactory();
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


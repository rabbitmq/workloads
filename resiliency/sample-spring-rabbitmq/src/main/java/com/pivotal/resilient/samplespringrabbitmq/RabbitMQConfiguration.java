package com.pivotal.resilient.samplespringrabbitmq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.amqp.rabbit.connection.SimplePropertyValueConnectionNameStrategy;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


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
    public ConnectionNameStrategy cns() {
        return new SimplePropertyValueConnectionNameStrategy("spring.application.name");
    }

    @Bean("consumer")
    public org.springframework.amqp.rabbit.connection.ConnectionFactory consumer(Cloud cloud) {
        ConnectionFactory factory = cloud.getSingletonServiceConnector(org.springframework.amqp.rabbit.connection.ConnectionFactory.class,
                null);
        return factory;
    }
    @Bean("producer")
    @Primary
    public org.springframework.amqp.rabbit.connection.ConnectionFactory producer(Cloud cloud) {
        ConnectionFactory factory = cloud.getSingletonServiceConnector(org.springframework.amqp.rabbit.connection.ConnectionFactory.class,
                null);
        return factory.getPublisherConnectionFactory();
    }

    @Bean
    public RabbitTemplate template(@Qualifier("producer") ConnectionFactory factory) {
        return new RabbitTemplate(factory);
    }
    @Bean
    public RabbitTemplate durable_test(@Qualifier("producer") ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setRoutingKey("durable-test");
        return template;
    }
}


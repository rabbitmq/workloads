package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


@Configuration
public class RabbitMQConfiguration {
    private Logger logger = LoggerFactory.getLogger(RabbitMQConfiguration.class);

    @Bean("producer")
    @Primary
    public org.springframework.amqp.rabbit.connection.ConnectionFactory producer(ConnectionFactory consumerConnectionFactory) {
        logger.info("Creating producer Spring ConnectionFactory ...");
        return consumerConnectionFactory.getPublisherConnectionFactory();
    }


    @Bean
    public RabbitTemplate template(@Qualifier("producer") ConnectionFactory connectionFactory) {
        logger.info("Creating template ...{publisher-confirms:{}}", connectionFactory.isPublisherConfirms());


        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setRoutingKey("hello");
        template.setExchange("");

        return template;
    }

}

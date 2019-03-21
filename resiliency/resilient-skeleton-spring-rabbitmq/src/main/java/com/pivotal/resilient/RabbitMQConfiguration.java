package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * This configuration class configures all the necessary AMQP resources required by the application, or business logic,
 * which resides in {@link ResilientSpringRabbitmqApplication} class where we send messages and listens to them.
 *
 */
@Configuration
@EnableRabbit
public class RabbitMQConfiguration {
    private Logger logger = LoggerFactory.getLogger(RabbitMQConfiguration.class);


    /**
     * Creates a default RabbitTemplate bean configured with the *producer* connectionFactory built by {@link RabbitMQConfiguration} class.
     * The template sends messages by default to *amp.direct* exchange using *hello* routing key.
     *
     * @param connectionFactory
     * @return
     */
    @Bean
    public RabbitTemplate template(@Qualifier("producer") ConnectionFactory connectionFactory) {
        logger.info("Creating template ...{publisher-confirms:{}}", connectionFactory.isPublisherConfirms());


        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setRoutingKey("hello");
        template.setExchange("amq.direct");

        return template;
    }

    /**
     * Creates a default SimpleRabbitlistenerContainer baean to be used by @RabbitListener(s) annotated methods.
     * This container is configured with the @Primary (a.k.a. default) connectionFactory built by {@link RabbitMQConfiguration} class
     *
     * @param connectionFactory
     * @return
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        return factory;
    }


}

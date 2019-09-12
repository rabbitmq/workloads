package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * This configuration class configures all the necessary AMQP resources required by the application, or business logic,
 * which resides in {@link ResilientSpringRabbitmqApplication} class where we send messages and listens to them.
 *
 */
@Configuration
@EnableRabbit
public class AMQPResourceConfig {
    private Logger logger = LoggerFactory.getLogger(AMQPResourceConfig.class);


    /**
     * Creates a default RabbitTemplate bean configured with the *producer* connectionFactory built by {@link AMQPResourceConfig} class.
     * The template sends messages by default to *amp.direct* exchange using *hello* routing key.
     *
     *  We cannot use the default RabbitTemplate provide by auto-configuration because that one would use the @Primary connectionFactory
     * @param connectionFactory
     * @return
     */
    @Bean
    public RabbitTemplate template(@Qualifier("producer") ConnectionFactory connectionFactory,
                                   @Value("${spring.rabbitmq.template.routing-key:hello}") String routingKey,
                                   @Value("${spring.rabbitmq.template.exchange:amq.direct}") String exchange) {
        logger.info("Creating template ...{publisher-confirms:{}}", connectionFactory.isPublisherConfirms());


        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setRoutingKey(routingKey);
        template.setExchange(exchange);

        return template;
    }

    /**
     * Creates a default SimpleRabbitlistenerContainer bean to be used by @RabbitListener(s) annotated methods.
     * This container is configured with the @Primary (a.k.a. default) connectionFactory built by {@link AMQPResourceConfig} class
     *
     * @param connectionFactory
     * @return
     */
    //@Bean
    // This bean is not necessary because the auto-configurations builds one configured from RabbitProperties with the @Primary connectionFactory which is the consumer
    // We want a custom bean when we want to configure settings which are not in RabbitProperties such as RecoveryBackoff and similar ones
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);

        return factory;
    }


}

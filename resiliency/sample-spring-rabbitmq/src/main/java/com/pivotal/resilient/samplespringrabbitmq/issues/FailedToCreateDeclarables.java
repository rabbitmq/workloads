package com.pivotal.resilient.samplespringrabbitmq.issues;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class FailedToCreateDeclarables {

    private Logger logger = LoggerFactory.getLogger(FailedToCreateDeclarables.class);

    @Bean
    public Collection<Declarable> issuesWithThisDeclarables() {
        logger.info("Creating declarables ...");
        return new Declarables(consumerQueue(), consumerExchange(), consumerBinding()).getDeclarables();
    }

    public Queue consumerQueue() {
        return QueueBuilder.durable("declarable-q").build();
    }
    public Exchange consumerExchange() {
        return ExchangeBuilder.directExchange("declarable-e").build();
    }
    public Binding consumerBinding() {
        return new Binding("declarable-q", Binding.DestinationType.QUEUE, "declarable-e", "declarable-q", null);
    }

}

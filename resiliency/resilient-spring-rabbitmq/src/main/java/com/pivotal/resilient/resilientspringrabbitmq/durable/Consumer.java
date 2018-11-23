package com.pivotal.resilient.resilientspringrabbitmq.durable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Consumer {
    private Logger logger = LoggerFactory.getLogger(Consumer.class);

    @Bean
    public SimpleMessageListenerContainer consumerOnDurableQueue(Queue queue, ConnectionFactory connectionFactory) {
        logger.info("Creating consumer on {} ...", queue.getName());

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(queue.getName());
        container.setMessageListener(new PlainMessageListener("consumer-durable"));

        return container;
    }
}

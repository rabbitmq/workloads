package com.pivotal.resilient.resilientspringrabbitmq.durable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Consumer {
    private Logger logger = LoggerFactory.getLogger(Consumer.class);

    @Value("${durable-consumer.possibleAuthenticationFailureFatal:false}") boolean possibleAuthenticationFailureFatal;

    @Bean
    public SimpleMessageListenerContainer consumerOnDurableQueue(Queue queue, ConnectionFactory connectionFactory) {
        logger.info("Creating consumer on {} ...", queue.getName());

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(queue.getName());
        container.setMessageListener(new PlainMessageListener("consumer-durable"));

        // it set to true, it will fail nad not recover if we get access refused
        container.setPossibleAuthenticationFailureFatal(possibleAuthenticationFailureFatal);

        return container;
    }
}

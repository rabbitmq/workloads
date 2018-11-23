package com.pivotal.resilient.resilientspringrabbitmq.durable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Producer {
    private Logger logger = LoggerFactory.getLogger(Producer.class);

    private @Autowired @Qualifier("templateForDurableProducer")
    RabbitTemplate template;

    @Scheduled(fixedRate = 5000)
    public void sendMessageUsingCachedChannel() {
        try {
            template.send(MessageBuilder.withBody("hello".getBytes()).build());
        }catch(RuntimeException e) {
            logger.warn("Failed to send message due to {}", e.getMessage());
        }
    }
}

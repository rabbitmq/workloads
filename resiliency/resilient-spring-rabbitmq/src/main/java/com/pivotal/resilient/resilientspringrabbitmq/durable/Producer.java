package com.pivotal.resilient.resilientspringrabbitmq.durable;

import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Producer {

    private @Autowired @Qualifier("templateForDurableProducer")
    RabbitTemplate template;

    @Scheduled(fixedRate = 5000)
    public void sendMessageUsingCachedChannel() {
        template.send(MessageBuilder.withBody("hello".getBytes()).build());
    }
}

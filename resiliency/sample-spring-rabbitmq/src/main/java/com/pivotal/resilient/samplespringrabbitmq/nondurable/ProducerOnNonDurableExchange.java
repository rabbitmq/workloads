package com.pivotal.resilient.samplespringrabbitmq.nondurable;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "non-durable.producer", value = "enabled", matchIfMissing = true)
public class ProducerOnNonDurableExchange {

    private @Autowired @Qualifier("templateForNonDurableProducer")
    RabbitTemplate template;

    @Scheduled(fixedRate = 5000)
    public void sendMessage() {
        template.invoke(t -> {
            t.convertAndSend("hello");
            return true;
        });

    }
}

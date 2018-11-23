package com.pivotal.resilient.samplespringrabbitmq.nondurable;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
@ConditionalOnProperty(prefix = "non-durable.producer", value = "enabled", matchIfMissing = true)
public class ProducerOnNonDurableExchange {

    @Autowired
    private TaskScheduler scheduler;
    private Map<String, ScheduledFuture<?>> producers = new ConcurrentHashMap<>();

    private @Autowired
    @Qualifier("templateForNonDurableProducer")
    RabbitTemplate template;


    @Scheduled(fixedRate = 5000)
    void sendMessageUsingCachedChannel() {
        send(buildMessageWithAppId("scheduledUsingCacheChannel"));
    }

    public void scheduleProducer(String name, long fixedRate) {
        ScheduledFuture<?> producer = producers.compute(name, (key, value) -> {
            return value != null ? value :
                    scheduler.scheduleAtFixedRate(() -> {
                        send(buildMessageWithAppId(key));
                    }, fixedRate);
        });

    }

    public boolean unscheduleProducer(String name) {
        ScheduledFuture<?> producer = producers.remove(name);
        if (producer != null) {
            producer.cancel(true);
            return true;
        } else {
            return false;
        }
    }

    public void send(Message message) {
        template.send(message);
    }

    private Message buildMessageWithAppId(String appId) {
        return MessageBuilder.withBody("hello".getBytes()).setAppId(appId).build();
    }

}

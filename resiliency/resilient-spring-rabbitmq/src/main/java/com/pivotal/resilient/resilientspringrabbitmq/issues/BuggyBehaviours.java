package com.pivotal.resilient.resilientspringrabbitmq.issues;

import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BuggyBehaviours {

    @Bean
    @ConditionalOnProperty(prefix = "issues", value = "useRabbitMQClusterAtStartup", matchIfMissing = false)
    public CommandLineRunner useRabbitMQClusterAtStartup(RabbitTemplate template) {
        return (args) -> {
            template.send(MessageBuilder.withBody("hello".getBytes()).build());
        };
    }

}

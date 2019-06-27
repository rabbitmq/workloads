package com.pivotal.resilient.nondurable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "non-durable", value = "enabled", matchIfMissing = false)
public class NonDurableResourcesConfiguration {

    private Logger logger = LoggerFactory.getLogger(NonDurableResourcesConfiguration.class);

    @Value("${non-durable-consumer.queue:non-durable-q}") String queueName;
    @Value("${non-durable-consumer.directExchange:non-durable-e}") String exchangeName;
    @Value("${non-durable-consumer.routingKey:non-durable-q}") String routingKey;

    @Bean("non-durable-consumer.queue")
    public Queue consumerQueue() {
        return QueueBuilder.nonDurable(queueName).build();
    }

    @Bean("non-durable-consumer.directExchange")
    public Exchange consumerExchange() {
        return ExchangeBuilder.directExchange(exchangeName).durable(false).build();
    }

    @Bean("non-durable-consumer.binding")
    public Binding consumerBinding() {
        return new Binding(queueName, Binding.DestinationType.QUEUE, exchangeName, routingKey, null);
    }

    @Bean
    public RabbitTemplate templateForNonDurableProducer(@Qualifier("producer") ConnectionFactory connectionFactory) {
        logger.info("Creating templateForDurableProducer ...");

        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setRoutingKey(routingKey);
        template.setExchange(exchangeName);

        return template;
    }


}

class PlainMessageListener implements MessageListener {

    private Logger logger = LoggerFactory.getLogger(PlainMessageListener.class);

    private String name;
    private long receivedMessageCount;
    private long failedMessageCount;


    public PlainMessageListener(String name) {
        this.name = name;
    }
    public PlainMessageListener() {
        this("");
    }


    @Override
    public void onMessage(Message message) {

        logger.info("{}/{} received (#{}/#{}) from {}/{} ",
                name,
                Thread.currentThread().getId(),
                receivedMessageCount,
                failedMessageCount,
                message.getMessageProperties().getConsumerQueue(),
                message.getMessageProperties().getConsumerTag()

        );
        receivedMessageCount++;
    }
}
package com.pivotal.resilient.durable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "durable", value = "enabled", matchIfMissing = false)
@EnableConfigurationProperties(DurableProperties.class)
public class DurableResourcesConfiguration {

    private Logger logger = LoggerFactory.getLogger(DurableResourcesConfiguration.class);

    @Autowired
    DurableProperties properties;

    @Bean
    public Queue durableQueue() {
        return QueueBuilder.durable(properties.queueName).build();
    }

    @Bean
    public Exchange durableExchange() {
        return ExchangeBuilder.directExchange(properties.exchangeName).build();
    }

    @Bean
    public Binding durableQueueBinding() {
        return new Binding(properties.queueName, Binding.DestinationType.QUEUE,
                properties.exchangeName, properties.routingKey, null);
    }

    @Bean
    public RabbitTemplate templateForDurableProducer(@Qualifier("producer") ConnectionFactory connectionFactory) {
        logger.info("Creating templateForDurableProducer ...");

        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setRoutingKey(properties.routingKey);
        template.setExchange(properties.exchangeName);

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
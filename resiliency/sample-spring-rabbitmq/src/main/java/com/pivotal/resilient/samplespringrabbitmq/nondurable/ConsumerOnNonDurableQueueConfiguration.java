package com.pivotal.resilient.samplespringrabbitmq.nondurable;

import com.pivotal.resilient.samplespringrabbitmq.ChaosMonkey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicLong;

@Configuration
@ConditionalOnProperty(prefix = "non-durable.consumer", value = "enabled", matchIfMissing = true)
public class ConsumerOnNonDurableQueueConfiguration {

    private Logger logger = LoggerFactory.getLogger(ConsumerOnNonDurableQueueConfiguration.class);

    @Value("${non-durable-consumer.queue:non-durable-q}") String queueName;
    @Value("${non-durable-consumer.directExchange:non-durable-e}") String exchangeName;
    @Value("${non-durable-consumer.routingKey:non-durable-q}") String routingKey;
    @Value("${non-durable-consumer.requeueRejectedMessages:true}") boolean requeueRejectedMessages;

    @Autowired @Qualifier("consumer") ConnectionFactory connectionFactory;

    private AtomicLong failedMessageCount = new AtomicLong();

    @Autowired
    RabbitAdmin rabbitAdmin;

   // @PostConstruct
    public void declareResources() {
        rabbitAdmin.declareQueue(queue());
        rabbitAdmin.declareExchange(exchange());
        rabbitAdmin.declareBinding(binding());
    }
    @Bean
    public Queue queue() {
        return QueueBuilder.nonDurable(queueName).build();
    }
    @Bean
    public Exchange exchange() {
        return ExchangeBuilder.directExchange(exchangeName).durable(false).build();
    }
    @Bean
    public Binding binding() {
        return new Binding(queueName, Binding.DestinationType.QUEUE, exchangeName, routingKey, null);
    }
    private AtomicLong messageId = new AtomicLong();
    private String messageIdPrefix = String.valueOf(System.currentTimeMillis());

    @Autowired
    private ChaosMonkey chaosMonkey;


    @Bean
    @ConditionalOnProperty(prefix = "non-durable.producer", value = "enabled", matchIfMissing = true)
    public RabbitTemplate templateForNonDurableProducer(@Qualifier("producer") ConnectionFactory connectionFactory) {
        logger.info("Creating templateForNonDurableProducer ...");
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setRoutingKey(routingKey);
        template.setExchange(exchangeName);
        template.setBeforePublishPostProcessors((message) -> {
            message.getMessageProperties().setMessageId(String.format("%s-%d", messageIdPrefix, messageId.incrementAndGet()));
            return message;
        });
        return template;
    }

    @Bean
    @ConditionalOnProperty(prefix = "non-durable.consumer", value = "enabled", matchIfMissing = true)
    public SimpleMessageListenerContainer consumerOnNonDurableQueue() {
        logger.info("Creating consumerOnNonDurableQueue ...");

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(queueName);
        container.setDefaultRequeueRejected(requeueRejectedMessages);

        ChaosMonkey.ChaosListener listener = chaosMonkey.newListener();
        container.setMessageListener(listener);
        container.setErrorHandler(listener.errorHandler());

        return container;
    }
}

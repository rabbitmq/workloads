package com.pivotal.resilient.samplespringrabbitmq.durable;

import com.pivotal.resilient.samplespringrabbitmq.ChaosMonkey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
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
@ConditionalOnProperty(prefix = "durable.consumer", value = "enabled", matchIfMissing = true)
public class ConsumerOnDurableQueueConfiguration {

    private Logger logger = LoggerFactory.getLogger(ConsumerOnDurableQueueConfiguration.class);

    @Value("${durable-consumer.queue:durable-q}") String queueName;
    @Value("${durable-consumer.directExchange:durable-e}") String exchangeName;
    @Value("${durable-consumer.routingKey:durable-q}") String routingKey;
    @Value("${durable-consumer.requeueRejectedMessages:true}") boolean requeueRejectedMessages;

    @Autowired @Qualifier("consumer") ConnectionFactory connectionFactory;

    private AtomicLong failedMessageCount = new AtomicLong();

    @Bean("durable-consumer.queue")
    public Queue consumerQueue() {
        return QueueBuilder.durable(queueName).build();
    }
    @Bean("durable-consumer.directExchange")
    public Exchange consumerExchange() {
        return ExchangeBuilder.directExchange(exchangeName).build();
    }
    @Bean("durable-consumer.binding")
    public Binding consumerBinding() {
        return new Binding(queueName, Binding.DestinationType.QUEUE, exchangeName, routingKey, null);
    }
    public Binding consumerQueueBoundToDurableExchange(@Qualifier("durable-consumer.queue") Queue queue,
                                                       @Qualifier("durable-consumer.directExchange") Exchange exchange) {
        logger.info("Binding {} to {} with {}", queue.getName(), exchange.getName(), routingKey);
        Binding binding =  BindingBuilder.bind(queue).to(exchange).with(routingKey).noargs();
        logger.info("Binding {} to {} with {} => {}", queue.getName(), exchange.getName(), routingKey, binding);
        return binding;
    }

    @Autowired
    private ChaosMonkey chaosMonkey;

    private AtomicLong messageId = new AtomicLong();
    private String messageIdPrefix = String.valueOf(System.currentTimeMillis());

    @Bean
    @ConditionalOnProperty(prefix = "durable.producer", value = "enabled", matchIfMissing = true)
    public RabbitTemplate templateForDurableProducer(@Qualifier("producer") ConnectionFactory connectionFactory) {
        logger.info("Creating templateForDurableProducer ...");

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
    @ConditionalOnProperty(prefix = "durable.consumer", value= "enabled", matchIfMissing = true)
    public SimpleMessageListenerContainer consumerOnDurableQueue() {
        logger.info("Creating consumerOnDurableQueue ...");

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(queueName);
        container.setDefaultRequeueRejected(requeueRejectedMessages); // if set to true, it will discard messages that throws an exception

        container.setMissingQueuesFatal(false); // this is key in order to survive should the hosting queue node disappeared.
                                                // without this flag=false, the container will simply give up after 3 attempts
        container.setErrorHandler( throwable -> {
            failedMessageCount.incrementAndGet();
        });
        container.setMessageListener(chaosMonkey.newListener());
        return container;
    }
}

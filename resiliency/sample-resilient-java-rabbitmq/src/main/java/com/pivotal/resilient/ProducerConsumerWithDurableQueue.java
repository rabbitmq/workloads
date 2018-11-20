package com.pivotal.resilient;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;

@Configuration
@Profile("durable-workload")
public class ProducerConsumerWithDurableQueue {

    @Bean
    public Producer durableProducer(TaskScheduler taskScheduler, AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "durableProducer";
        Producer producer = Producer.sender(serviceName,"durable-test")
                .withPublisherConfirmation()
                .atFixedRate(taskScheduler, 5000);
        amqpConnectionProvider.manageConnectionFor(serviceName, producer.getRequiredAMQPResources(), producer);
        return producer;
    }

    @Bean
    public Consumer durableConsumer(/*@Qualifier("consumer")*/ AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "durableConsumer";
        Consumer consumer =  new Consumer(serviceName, new QueueDescriptor("durable-test", true))
                .withConsumptionRate(30000) // delay
                .ackEveryMessage()          // before acking
                .withPrefetch(1);           // keeping a maximum of 1 unacknowledged message
        amqpConnectionProvider.manageConnectionFor(serviceName, consumer.getRequiredAMQPResources(), consumer);
        return consumer;
    }


}



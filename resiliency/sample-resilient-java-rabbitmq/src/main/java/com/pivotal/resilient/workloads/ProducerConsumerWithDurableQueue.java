package com.pivotal.resilient.workloads;

import com.pivotal.resilient.amqp.AMQPConnectionProvider;
import com.pivotal.resilient.amqp.QueueDescriptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;

@Configuration
public class ProducerConsumerWithDurableQueue {

    @Bean
    public Producer durableProducer(TaskScheduler taskScheduler, AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "durableProducer";
        Producer producer = Producer.sender(serviceName,"durable-test")
                .withPublisherConfirmation()
                .atFixedRate(taskScheduler, 5000);
        amqpConnectionProvider.requestConnectionFor(serviceName, producer.getRequiredAMQPResources(), producer);
        return producer;
    }

    @Bean
    public Consumer durableConsumer(@Qualifier("consumer") AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "durableConsumer";
        QueueDescriptor durable_test = new QueueDescriptor("durable-test", true);

        Consumer consumer = ConsumerBuilder.named(serviceName)
                                        .consumeFrom(durable_test)
                                        .withPrefetch(1)             // keeping a maximum of 1 unacknowledged message
                                        .withConsumptionDelay(30000) // delay before akcing
                                        .ackEveryMessage()           // acking every message
                                        .build();

        amqpConnectionProvider.requestConnectionFor(serviceName, consumer.getRequiredAMQPResources(), consumer);
        return consumer;
    }


}



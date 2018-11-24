package com.pivotal.resilient.workloads;

import com.pivotal.resilient.amqp.AMQPConnectionProvider;
import com.pivotal.resilient.amqp.QueueDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

@Configuration
public class ProducerConsumerWithNonDurableQueue {

    private Logger logger = LoggerFactory.getLogger(ProducerConsumerWithDurableQueue.class);

    @Bean(name = "producer-non-durable")
    public Producer producer(TaskScheduler taskScheduler, AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "producer-non-durable";
        Producer producer = Producer.sender(serviceName, "non-durable-test")
                                    .atFixedRate(taskScheduler, 10000);
        amqpConnectionProvider.requestConnectionFor(serviceName, producer.getRequiredAMQPResources(), producer);
        return producer;
    }

    @Bean(name = "consumer-non-durable")
    public Consumer consumer(AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "consumer-non-durable";
        QueueDescriptor non_durable_test = new QueueDescriptor("non-durable-test", false);

        Consumer consumer = ConsumerBuilder.named(serviceName)
                                            .consumeFrom(non_durable_test)
                                            .withAutoack()
                                            .build();

        amqpConnectionProvider.requestConnectionFor(serviceName, consumer.getRequiredAMQPResources(), consumer);

        return consumer;
    }

    
}


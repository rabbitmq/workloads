package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;

//@Configuration
public class ProducerConsumerWithNonDurableQueue {

    private Logger logger = LoggerFactory.getLogger(ProducerConsumerWithDurableQueue.class);

    @Bean(name = "producer-non-durable")
    public Producer producer(TaskScheduler taskScheduler, AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "producer-non-durable";
        Producer producer = Producer.sender(serviceName, "non-durable-test")
                                    .atFixedRate(taskScheduler, 10000);
        amqpConnectionProvider.manageConnectionFor(serviceName, producer.getRequiredAMQPResources(), producer);
        return producer;
    }

    @Bean(name = "consumer-non-durable")
    public Consumer consumer(AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "consumer-non-durable";
        Consumer consumer =  new Consumer(serviceName, new QueueDescriptor("non-durable-test", true));
        amqpConnectionProvider.manageConnectionFor(serviceName, consumer.getRequiredAMQPResources(), consumer);
        return consumer;
    }

    
}


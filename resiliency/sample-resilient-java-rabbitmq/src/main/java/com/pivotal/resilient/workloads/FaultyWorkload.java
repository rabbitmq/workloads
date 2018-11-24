package com.pivotal.resilient.workloads;

import com.pivotal.resilient.amqp.AMQPConnectionProvider;
import com.pivotal.resilient.amqp.BindingDescriptor;
import com.pivotal.resilient.amqp.ExchangeDescriptor;
import com.pivotal.resilient.amqp.QueueDescriptor;
import com.pivotal.resilient.chaos.ChaosBunny;
import com.pivotal.resilient.chaos.RandomChannelCloser;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Executor;

@Configuration
public class FaultyWorkload {

    private Logger logger = LoggerFactory.getLogger(FaultyWorkload.class);

    @Autowired
    private ChaosBunny chaosBunny;

    // This producer will never be able to publish once the exchange is deleted because
    // the logic that declares the amqp resources will only trigger when we connect
    @Bean
    @Profile("producerOnExchangeThatEventuallyGetsDeleted")
    public Producer producerOnExchangeThatEventuallyGetsDeleted(TaskScheduler taskScheduler, AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "producerOnExchangeThatEventuallyGetsDeleted";
        Producer producer = Producer.sender(serviceName, faultyExchange, "faulty")
                                    .atFixedRate(taskScheduler, 10000);

        amqpConnectionProvider.requestConnectionFor(serviceName, producer.getRequiredAMQPResources(), producer);

        // WARNING: When exchange is deleted, all bindings referring to the exchange are also deleted
        amqpConnectionProvider.requestConnectionFor("ExchangeDeleter",
                Collections.emptyList(),
                chaosBunny.deleteExchangeAtFixedRate("faulty-exchange", 20000));

        return producer;
    }

    private QueueDescriptor faultyConsumerQ = new QueueDescriptor("faulty-consumer-q", true);
    private ExchangeDescriptor faultyExchange = new ExchangeDescriptor("faulty-exchange", BuiltinExchangeType.FANOUT, true);


    @Bean
    @Profile("consumerBindingWithNullRountingKey")
    public Consumer consumerBindingWithNullRountingKey(AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "consumerBindingWithNullRountingKey";
        Consumer consumer =  ConsumerBuilder.named(serviceName)
                    .consumeFrom(faultyConsumerQ, new BindingDescriptor(faultyConsumerQ, faultyExchange, null))
                    .build();

        amqpConnectionProvider.requestConnectionFor(serviceName, consumer.getRequiredAMQPResources(), consumer);
        return consumer;
    }

    @Bean
    @Profile("consumerOnQueueWithDeletedExchange")
    public Consumer consumerOnQueueWithDeletedExchange(AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "consumerOnQueueWithDeletedExchange";
        Consumer consumer = ConsumerBuilder.named(serviceName)
                            .consumeFrom(faultyConsumerQ, new BindingDescriptor(faultyConsumerQ, faultyExchange, ""))
                            .build();
        amqpConnectionProvider.requestConnectionFor(serviceName, consumer.getRequiredAMQPResources(), consumer);
        return consumer;
    }

    // resilient producer scenario
    @Bean
    @Profile("producerThatGetITsChannelClosedByOthersDueToFailedChannelOperation")
    public Producer producerThatGetITsChannelClosedByOthersDueToFailedChannelOperation(TaskScheduler taskScheduler, AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "producerThatGetITsChannelClosedByOthersDueToFailedChannelOperation";
        Producer producer = Producer.sender(serviceName, faultyExchange, "faulty")
                .atFixedRate(taskScheduler, 10000);
        amqpConnectionProvider.requestConnectionFor(serviceName, producer.getRequiredAMQPResources(), producer);

        RandomChannelCloser randomChannelCloser = new RandomChannelCloser(taskScheduler, 50000);
        producer.setChannelListener(randomChannelCloser);
        amqpConnectionProvider.requestConnectionFor("RandomChannelCloser", Collections.emptyList(), randomChannelCloser);
        return producer;
    }

    private QueueDescriptor faultyConsumerQOne = new QueueDescriptor("faulty-consumer-q-1", true);

    // resilient consumer scenario: Channel and consumer is restored
    @Bean
    @Profile("consumerThatGetITsChannelClosedByOthersDueToFailedChannelOperation")
    public Consumer consumerThatGetITsChannelClosedByOthersDueToFailedChannelOperation(TaskScheduler taskScheduler, AMQPConnectionProvider amqpConnectionProvider) {

        RandomChannelCloser randomChannelCloser = chaosBunny.randomChannelCloser(15000);

        String serviceName = "consumerThatGetITsChannelClosedByOthersDueToFailedChannelOperation";
        Consumer consumer =  ConsumerBuilder.named(serviceName)
                    .consumeFrom(faultyConsumerQOne)            // default binding
                    .withChannelListener(randomChannelCloser)   // so that channelCloser knows when a channel gets created
                    .build();

        amqpConnectionProvider.requestConnectionFor(serviceName, consumer.getRequiredAMQPResources(), consumer);
        amqpConnectionProvider.requestConnectionFor("RandomChannelCloser", Collections.emptyList(), randomChannelCloser);

        return consumer;
    }

    private QueueDescriptor buggyConsumerQ = new QueueDescriptor("buggy-consumer-q", false);

    // non-resilient consumer scenario: Consumer is cancelled
    @Bean
    @Profile("consumerThatGetsItsChannelClosedDueToFailedChannelOperation")
    public Consumer consumerThatGetsItsChannelClosedDueToFailedChannelOperation(TaskScheduler taskScheduler, AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "consumerThatGetsItsChannelClosedDueToFailedChannelOperation";

        Consumer consumer =  ConsumerBuilder.named(serviceName)
                    .consumeFrom(buggyConsumerQ, buggyConsumerQ.bindWith(faultyExchange, "faulty"))
                    .withAutoack()
                    .handleMessagesWith((String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) -> {
                        try {
                            logger.warn("Intentionally producing channel failure by publishing to wrong exchange");
                            channel.basicPublish("na", "na", properties, body);
                        } catch (IOException | RuntimeException e) {
                            logger.warn("Intentionally failed to publish . Reason {} {}", e.getClass().getName(), e.getMessage());
                        }
                    })
                    .build();


        amqpConnectionProvider.requestConnectionFor(serviceName, consumer.getRequiredAMQPResources(), consumer);

        return consumer;
    }

    private QueueDescriptor buggyConsumerQTwo = new QueueDescriptor("buggy-consumer-q-2", false);

    // non-resilient consumer scenario: Consumer is cancelled
    @Bean
    @Profile("consumerThatThrowsException")
    public Consumer consumerThatThrowsException(TaskScheduler taskScheduler, AMQPConnectionProvider amqpConnectionProvider) {
        BindingDescriptor binding = buggyConsumerQTwo.bindWith(faultyExchange, "faulty");
        String serviceName = "consumerThatThrowsException";
        Consumer consumer =  ConsumerBuilder.named(serviceName)
                    .consumeFrom(buggyConsumerQTwo, binding)
                    .withAutoack()
                    .throwExceptionAfter("Simulated failure", 2)
                    .build();

        amqpConnectionProvider.requestConnectionFor(serviceName, consumer.getRequiredAMQPResources(), consumer);

        return consumer;
    }


    private void waitUntilAnotherThreadAbruptedlyClosesTheChannel(Executor executor, Channel channel, QueueDescriptor queue) throws IOException {
        // simulate a buggy component -running outside of the consumer's thread- that causes this consumer channel to close
        chaosBunny.closeChannelOnSeparateThread(channel);

    }
    @Bean
    @Profile("consumerGetsItsChannelUnexpectedlyClosedWithUndeliveredMessages")
    public Consumer consumerGetsItsChannelUnexpectedlyClosedWithUndeliveredMessages(TaskScheduler taskScheduler,
                                                                                    //final ExecutorService executor,
                                                                                    AMQPConnectionProvider amqpConnectionProvider) {

        BindingDescriptor binding = buggyConsumerQTwo.bindWith(faultyExchange, "faulty");
        String serviceName = "consumerGetsItsChannelUnexpectedlyClosedWithUndeliveredMessages";
        Consumer consumer =  ConsumerBuilder.named(serviceName)
                                .consumeFrom(buggyConsumerQTwo, binding)
                                .withPrefetch(3)
                                .handleMessagesWith(
                                        (consumerTag, envelope, properties, body, channel) -> {
                                            logReadyMessagesInBroker(channel, buggyConsumerQTwo.getName());
                                            waitUntilAnotherThreadAbruptedlyClosesTheChannel(null, channel, buggyConsumerQTwo);
                                        },
                                        (consumerTag, envelope, properties, body, channel) -> {

                                        })
                                .ackEveryMessage()
                                .build();

        amqpConnectionProvider.requestConnectionFor(serviceName, consumer.getRequiredAMQPResources(), consumer);

        serviceName = "senderForConsumerGetsItsChannelUnexpectedlyClosedWithUndeliveredMessages";
        Producer producer = Producer.sender(serviceName, faultyExchange, binding.getRoutingKey())
                .once(3);
        amqpConnectionProvider.requestConnectionFor(serviceName, Arrays.asList(faultyExchange), producer);

        return consumer;
    }

    private void logReadyMessagesInBroker(Channel channel, String queue) throws IOException {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long messagesReadyInBroker = channel.messageCount(queue);
        logger.info("There are still {} messages in the broker", messagesReadyInBroker);

    }
    private QueueDescriptor buggyConsumerQThree = new QueueDescriptor("buggy-consumer-q-3", false);

    @Bean
    @Profile("cancelledAutoAckConsumerWithMessagesInBuffer")
    public Consumer cancelledAutoAckConsumerWithMessagesInBuffer(TaskScheduler taskScheduler,
                                                                 AMQPConnectionProvider amqpConnectionProvider) {
        BindingDescriptor binding = buggyConsumerQThree.bindWith(faultyExchange, "faulty-two");
        String consumerServiceName = "cancelledAutoAckConsumerWithMessagesInBuffer";

        Consumer consumer =  ConsumerBuilder.named(consumerServiceName)
                            .consumeFrom(buggyConsumerQThree, binding)
                            .withAutoack()
                            .cancelAfterFirstMessage()
                            .build();

        amqpConnectionProvider.requestConnectionFor("DeclareResourcesFor:" + consumerServiceName, consumer.getRequiredAMQPResources());

        String producerServiceName = "senderForcancelledAutoAckConsumerWithMessagesInBuffer";
        Producer producer = Producer.sender(producerServiceName, faultyExchange, binding.getRoutingKey())
                .once(10);
        amqpConnectionProvider.requestConnectionFor(producerServiceName, Arrays.asList(faultyExchange), producer);

        amqpConnectionProvider.requestConnectionFor(consumerServiceName, Collections.emptyList(), consumer);


        return consumer;
    }

    private QueueDescriptor buggyConsumerQFour = new QueueDescriptor("buggy-consumer-q-4", false);


    @Bean
    @Profile("cancelledManualAckConsumerWithMessagesInBuffer")
    public Consumer cancelledManualAckConsumerWithMessagesInBuffer(TaskScheduler taskScheduler,
                                                                   AMQPConnectionProvider amqpConnectionProvider) {
        BindingDescriptor binding = buggyConsumerQFour.bindWith(faultyExchange, "faulty-two");
        String consumerServiceName = "cancelledManualAckConsumerWithMessagesInBuffer";

        Consumer consumer =  ConsumerBuilder.named(consumerServiceName)
                .consumeFrom(buggyConsumerQFour, binding)
                .withPrefetch(5)
                .cancelAfterFirstMessage()
                .ackEveryMessage()
                .build();

        amqpConnectionProvider.requestConnectionFor("DeclareResourcesFor:" + consumerServiceName, consumer.getRequiredAMQPResources());

        String producerServiceName = "senderForcancelledManualAckConsumerWithMessagesInBuffer";
        Producer producer = Producer.sender(producerServiceName, faultyExchange, binding.getRoutingKey())
                .once(10);
        amqpConnectionProvider.requestConnectionFor(producerServiceName, Arrays.asList(faultyExchange), producer);

        amqpConnectionProvider.requestConnectionFor(consumerServiceName, Collections.emptyList(), consumer);


        return consumer;
    }

    @Bean
    @Profile("shutdownConsumer")
    public Consumer shutdownConsumer(TaskScheduler taskScheduler, AMQPConnectionProvider amqpConnectionProvider) {
        BindingDescriptor binding = buggyConsumerQFour.bindWith(faultyExchange, "faulty-two");
        String consumerServiceName = "shutdownConsumer";

        final Consumer consumer = ConsumerBuilder.named(consumerServiceName)
                            .consumeFrom(buggyConsumerQFour, binding)
                            .withAutoack()
                            .build();

        amqpConnectionProvider.requestConnectionFor("ExchangeDeclarer",  Arrays.asList(faultyExchange));
        amqpConnectionProvider.requestConnectionFor(consumerServiceName, consumer.getRequiredAMQPResources(), consumer);

        // schedule consumer shutdown 20 seconds from now
        taskScheduler.schedule(() -> {
            amqpConnectionProvider.releaseConnectionsFor(consumerServiceName);
            consumer.shutdown();
        }, Instant.now().plusSeconds(20));

        return consumer;
    }

}


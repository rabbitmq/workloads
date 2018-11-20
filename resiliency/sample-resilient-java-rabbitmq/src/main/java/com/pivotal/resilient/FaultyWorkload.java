package com.pivotal.resilient;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Configuration
public class FaultyWorkload {

    private Logger logger = LoggerFactory.getLogger(FaultyWorkload.class);

    // This producer will never be able to publish once the exchange is deleted because
    // the logic that declares the amqp resources will only trigger when we connect
    @Bean
    @Profile("producerOnExchangeThatEventuallyGetsDeleted")
    public Producer producerOnExchangeThatEventuallyGetsDeleted(TaskScheduler taskScheduler, AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "producerOnExchangeThatEventuallyGetsDeleted";
        Producer producer = Producer.sender(serviceName, faultyExchange, "faulty")
                                    .atFixedRate(taskScheduler, 10000);

        amqpConnectionProvider.manageConnectionFor(serviceName, producer.getRequiredAMQPResources(), producer);

        // WARNING: When exchange is deleted, all bindings referring to the exchange are also deleted

        ExchangeDeleter exchangeDeleter = new ExchangeDeleter(taskScheduler, 20000, "faulty-exchange");
        amqpConnectionProvider.manageConnectionFor("ExchangeDeleter", Collections.emptyList(), exchangeDeleter);

        return producer;
    }

    private QueueDescriptor faultyConsumerQ = new QueueDescriptor("faulty-consumer-q", true);
    private ExchangeDescriptor faultyExchange = new ExchangeDescriptor("faulty-exchange", BuiltinExchangeType.FANOUT, true);


    @Bean
    @Profile("consumerBindingWithNullRountingKey")
    public Consumer consumerBindingWithNullRountingKey(AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "consumerBindingWithNullRountingKey";
        Consumer consumer =  new Consumer(serviceName, faultyConsumerQ,
                new BindingDescriptor(faultyConsumerQ, faultyExchange, null));
        amqpConnectionProvider.manageConnectionFor(serviceName, consumer.getRequiredAMQPResources(), consumer);
        return consumer;
    }

    @Bean
    @Profile("consumerOnQueueWithDeletedExchange")
    public Consumer consumerOnQueueWithDeletedExchange(AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "consumerOnQueueWithDeletedExchange";
        Consumer consumer =  new Consumer(serviceName, faultyConsumerQ,
                new BindingDescriptor(faultyConsumerQ, faultyExchange, ""));
        amqpConnectionProvider.manageConnectionFor(serviceName, consumer.getRequiredAMQPResources(), consumer);
        return consumer;
    }

    // resilient producer scenario
    @Bean
    @Profile("producerThatGetITsChannelClosedByOthersDueToFailedChannelOperation")
    public Producer producerThatGetITsChannelClosedByOthersDueToFailedChannelOperation(TaskScheduler taskScheduler, AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "producerThatGetITsChannelClosedByOthersDueToFailedChannelOperation";
        Producer producer = Producer.sender(serviceName, faultyExchange, "faulty")
                .atFixedRate(taskScheduler, 10000);
        amqpConnectionProvider.manageConnectionFor(serviceName, producer.getRequiredAMQPResources(), producer);

        RandomChannelCloser randomChannelCloser = new RandomChannelCloser(taskScheduler, 50000);
        producer.setChannelListener(randomChannelCloser);
        amqpConnectionProvider.manageConnectionFor("RandomChannelCloser", Collections.emptyList(), randomChannelCloser);
        return producer;
    }

    private QueueDescriptor faultyConsumerQOne = new QueueDescriptor("faulty-consumer-q-1", true);

    // resilient consumer scenario: Channel and consumer is restored
    @Bean
    @Profile("consumerThatGetITsChannelClosedByOthersDueToFailedChannelOperation")
    public Consumer consumerThatGetITsChannelClosedByOthersDueToFailedChannelOperation(TaskScheduler taskScheduler, AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "consumerThatGetITsChannelClosedByOthersDueToFailedChannelOperation";
        Consumer consumer =  new Consumer(serviceName, faultyConsumerQOne); // default binding
        amqpConnectionProvider.manageConnectionFor(serviceName, consumer.getRequiredAMQPResources(), consumer);

        RandomChannelCloser randomChannelCloser = new RandomChannelCloser(taskScheduler, 15000);
        consumer.setChannelListener(randomChannelCloser);
        amqpConnectionProvider.manageConnectionFor("RandomChannelCloser", Collections.emptyList(), randomChannelCloser);
        return consumer;
    }

    private QueueDescriptor buggyConsumerQ = new QueueDescriptor("buggy-consumer-q", false);

    // non-resilient consumer scenario: Consumer is cancelled
    @Bean
    @Profile("consumerThatGetsItsChannelClosedDueToFailedChannelOperation")
    public Consumer consumerThatGetsItsChannelClosedDueToFailedChannelOperation(TaskScheduler taskScheduler, AMQPConnectionProvider amqpConnectionProvider) {
        String serviceName = "consumerThatGetsItsChannelClosedDueToFailedChannelOperation";

        Consumer consumer =  new Consumer(serviceName,
                buggyConsumerQ, buggyConsumerQ.bindWith(faultyExchange, "faulty"))
                    .withAutoack()
                    .handleMessagesWith((Envelope envelope, AMQP.BasicProperties properties, byte[] body, Channel channel) -> {
                        try {
                            logger.warn("Intentionally producing channel failure by publishing to wrong exchange");
                            channel.basicPublish("na", "na", properties, body);
                        } catch (IOException | RuntimeException e) {
                            logger.warn("Intentionally failed to publish . Reason {} {}", e.getClass().getName(), e.getMessage());
                        }
                    });


        amqpConnectionProvider.manageConnectionFor(serviceName, consumer.getRequiredAMQPResources(), consumer);

        return consumer;
    }

    private QueueDescriptor buggyConsumerQTwo = new QueueDescriptor("buggy-consumer-q-2", false);

    // non-resilient consumer scenario: Consumer is cancelled
    @Bean
    @Profile("consumerThatThrowsException")
    public Consumer consumerThatThrowsException(TaskScheduler taskScheduler, AMQPConnectionProvider amqpConnectionProvider) {
        BindingDescriptor binding = buggyConsumerQTwo.bindWith(faultyExchange, "faulty");
        String serviceName = "consumerThatThrowsException";
        Consumer consumer =  new Consumer(serviceName, buggyConsumerQTwo, binding)
                    .withAutoack()
                    .throwExceptionAfter("Simulated failure", 2);

        amqpConnectionProvider.manageConnectionFor(serviceName, consumer.getRequiredAMQPResources(), consumer);

        return consumer;
    }


    private void waitUntilAnotherThreadAbruptedlyClosesTheChannel(Executor executor, Channel channel, QueueDescriptor queue) throws IOException {
        // simulate a buggy component -running outside of the consumer's thread- that causes this consumer channel to close
        ChannelCloser channelCloser = new ChannelCloser(channel);

        new Thread(channelCloser).start();

        try {
            channelCloser.waitUntilChannelCloserRuns(5000);
        } catch (InterruptedException e) {

        }
    }
    @Bean
    @Profile("consumerGetsItsChannelUnexpectedlyClosedWithUndeliveredMessages")
    public Consumer consumerGetsItsChannelUnexpectedlyClosedWithUndeliveredMessages(TaskScheduler taskScheduler,
                                                                                    //final ExecutorService executor,
                                                                                    AMQPConnectionProvider amqpConnectionProvider) {

        BindingDescriptor binding = buggyConsumerQTwo.bindWith(faultyExchange, "faulty");
        String serviceName = "consumerGetsItsChannelUnexpectedlyClosedWithUndeliveredMessages";
        Consumer consumer =  new Consumer(serviceName, buggyConsumerQTwo, binding)
                                .withPrefetch(3)
                                .handleMessagesWith(
                                        (envelope, properties, body, channel) -> {
                                            logReadyMessagesInBroker(channel, buggyConsumerQTwo.getName());
                                            waitUntilAnotherThreadAbruptedlyClosesTheChannel(null, channel, buggyConsumerQTwo);
                                        },
                                        (envelope, properties, body, channel) -> {

                                        })
                                .ackEveryMessage();

        amqpConnectionProvider.manageConnectionFor(serviceName, consumer.getRequiredAMQPResources(), consumer);

        serviceName = "senderForConsumerGetsItsChannelUnexpectedlyClosedWithUndeliveredMessages";
        Producer producer = Producer.sender(serviceName, faultyExchange, binding.getRoutingKey())
                .once(3);
        amqpConnectionProvider.manageConnectionFor(serviceName, Arrays.asList(faultyExchange), producer);

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

        Consumer consumer =  new Consumer(consumerServiceName, buggyConsumerQThree, binding)
                .withAutoack()
                .cancelAfterFirstMessage();

        amqpConnectionProvider.manageConnectionFor("DeclareResourcesFor:" + consumerServiceName, consumer.getRequiredAMQPResources());

        String producerServiceName = "senderForcancelledAutoAckConsumerWithMessagesInBuffer";
        Producer producer = Producer.sender(producerServiceName, faultyExchange, binding.getRoutingKey())
                .once(10);
        amqpConnectionProvider.manageConnectionFor(producerServiceName, Arrays.asList(faultyExchange), producer);

        amqpConnectionProvider.manageConnectionFor(consumerServiceName, Collections.emptyList(), consumer);


        return consumer;
    }

    private QueueDescriptor buggyConsumerQFour = new QueueDescriptor("buggy-consumer-q-4", false);


    @Bean
    @Profile("cancelledManualAckConsumerWithMessagesInBuffer")
    public Consumer cancelledManualAckConsumerWithMessagesInBuffer(TaskScheduler taskScheduler,
                                                                 AMQPConnectionProvider amqpConnectionProvider) {
        BindingDescriptor binding = buggyConsumerQFour.bindWith(faultyExchange, "faulty-two");
        String consumerServiceName = "cancelledManualAckConsumerWithMessagesInBuffer";

        Consumer consumer =  new Consumer(consumerServiceName, buggyConsumerQFour, binding)
                .withPrefetch(5)
                .cancelAfterFirstMessage()
                .ackEveryMessage();

        amqpConnectionProvider.manageConnectionFor("DeclareResourcesFor:" + consumerServiceName, consumer.getRequiredAMQPResources());

        String producerServiceName = "senderForcancelledManualAckConsumerWithMessagesInBuffer";
        Producer producer = Producer.sender(producerServiceName, faultyExchange, binding.getRoutingKey())
                .once(10);
        amqpConnectionProvider.manageConnectionFor(producerServiceName, Arrays.asList(faultyExchange), producer);

        amqpConnectionProvider.manageConnectionFor(consumerServiceName, Collections.emptyList(), consumer);


        return consumer;
    }

    @Bean
    @Profile("shutdownConsumer")
    public Consumer shutdownConsumer(TaskScheduler taskScheduler, AMQPConnectionProvider amqpConnectionProvider) {
        BindingDescriptor binding = buggyConsumerQFour.bindWith(faultyExchange, "faulty-two");
        String consumerServiceName = "shutdownConsumer";

        final Consumer consumer =  new Consumer(consumerServiceName, buggyConsumerQFour, binding)
                .withAutoack();
        amqpConnectionProvider.manageConnectionFor("ExchangeDeclarer",  Arrays.asList(faultyExchange));
        amqpConnectionProvider.manageConnectionFor(consumerServiceName, consumer.getRequiredAMQPResources(), consumer);

        taskScheduler.schedule(() -> {
            amqpConnectionProvider.unmanageConnectionsFor(consumerServiceName);
            consumer.shutdown();
            }, Instant.now().plusSeconds(20));

        return consumer;
    }
}

abstract class ChaosMonicAbstract implements Runnable, AMQPConnectionRequester {

    private TaskScheduler scheduler;
    private Connection connection;
    private ScheduledFuture<?> chaosScheduler;

    private long frequency;

    public ChaosMonicAbstract(TaskScheduler scheduler, long frequency) {
        this.frequency = frequency;
        this.scheduler = scheduler;
    }

    protected void executeOnChannel(ChannelOperation operation) {
        Channel channel;
        try {
            channel = connection.createChannel();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            operation.executeOn(channel);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                channel.close();
            } catch (IOException | TimeoutException e) {

            }
        }
    }

    @Override
    public void connectionAvailable(Connection connection) {
        this.connection = connection;
        chaosScheduler = scheduler.scheduleAtFixedRate(this, Instant.now().plusMillis(frequency), Duration.ofMillis(frequency));
    }

    @Override
    public void connectionLost() {
        if (chaosScheduler != null) {
            chaosScheduler.cancel(true);
        }
    }

    @Override
    public void connectionBlocked(String reason) {

    }

    @Override
    public void connectionUnblocked(Connection connection) {

    }

    @Override
    public boolean isHealthy() {
        return true;
    }
}
@FunctionalInterface
interface ChannelOperation {
    void executeOn(Channel t) throws IOException;
}
class ExchangeDeleter extends ChaosMonicAbstract {
    private String exchangeName;
    private Logger logger = LoggerFactory.getLogger(ExchangeDeleter.class);

    public ExchangeDeleter(TaskScheduler scheduler, long frequency, String exchangeName) {
        super(scheduler, frequency);
        this.exchangeName = exchangeName;
    }

    @Override
    public void run() {
        logger.warn("ChaosMonic deleting exchange {}", exchangeName);
        executeOnChannel((channel) -> channel.exchangeDelete(exchangeName));
    }


    @Override
    public String getName() {
        return "ExchangeDeleter-" + exchangeName;
    }
}
class RandomChannelCloser extends ChaosMonicAbstract implements ChannelListener  {

    protected AtomicReference<Channel> lastCreatedChannel = new AtomicReference<>();
    private Logger logger = LoggerFactory.getLogger(RandomChannelCloser.class);

    public RandomChannelCloser(TaskScheduler scheduler, long frequency) {
        super(scheduler, frequency);
    }

    @Override
    public void run() {
        Channel channel = lastCreatedChannel.getAndSet(null);
        if (channel != null) {
            try {
                logger.warn("RandomChannelCloser forcing channel {} to close", channel.getChannelNumber());
                channel.queueBind("na", "na" , "na");
            } catch (IOException | RuntimeException e) {
                if (!channel.isOpen()) {
                    logger.info("RandomChannelCloser closed channel {}", channel.getChannelNumber());
                }
            }
        }
    }

    @Override
    public void createdChannel(Channel channel) {
        logger.warn("{} received a channel", getName());
        lastCreatedChannel.set(channel);
    }

    @Override
    public String getName() {
        return "RandomChannelCloser";
    }
}
class ChannelCloser extends RandomChannelCloser {

    private Semaphore semaphore;

    public ChannelCloser(Channel channel) {
        super(null, 0);
        lastCreatedChannel.set(channel);
        this.semaphore = new Semaphore(0);
    }

    @Override
    public void run() {
        try {
            super.run();
        }finally {
            semaphore.release();
        }
    }
    public void waitUntilChannelCloserRuns(long timeout) throws InterruptedException {
        this.semaphore.tryAcquire(timeout, TimeUnit.MILLISECONDS);
    }
    @Override
    public String getName() {
        return "ChannelCloser";
    }
}
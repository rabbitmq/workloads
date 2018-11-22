package com.pivotal.resilient.samplespringrabbitmq;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.ErrorHandler;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class ChaosMonkey {

    public ChaosListener newListener() {
        return new ChaosListener();
    }
    public ChaosListener newListener(String name) {
        return new ChaosListener(name);
    }
    public Message newMessage(ChaosMessageType type) {
        return MessageBuilder.withBody("hello".getBytes()).setType(type.name()).build();
    }

    public enum ChaosMessageType {
        DoNothing, AlwaysThrowException, ThrowExceptionIfNotRequeued, CauseChannelToClose, ThrowButNotRequeue;

        private static Set<String> names = new HashSet<>();
        private static String[] nameArray;

        static {
            Arrays.asList(ChaosMessageType.values()).forEach(v->names.add(v.name()));
            nameArray =  Stream.of(ChaosMessageType.values()).map(ChaosMessageType::name).toArray(String[]::new);
        }
        static ChaosMessageType parse(String value, ChaosMessageType ifNotPresent) {
            return value == null || !names.contains(value) ? ifNotPresent : ChaosMessageType.valueOf(value);
        }
        public static String[] names() {
            return nameArray;
        }

    }
    public static class ChaosListener implements ChannelAwareMessageListener {

        private Logger logger = LoggerFactory.getLogger(ChaosListener.class);

        private long receivedMessageCount;
        private long failedMessageCount;

        private String name;


        public ChaosListener(String name) {
            this.name = name;
        }
        public ChaosListener() {
            this("");
        }

        public ErrorHandler errorHandler() {
            return throwable -> {
                failedMessageCount++;
            };
        }

        @Override
        public void onMessage(Message message, Channel channel) throws Exception {
            receivedMessageCount++;
            ChaosMonkey.ChaosMessageType type = extractType(message);

            logger.info("{}/{} received (#{}/#{}) {} from {}/{} of type {}",
                    name,
                    Thread.currentThread().getId(),
                    receivedMessageCount,
                    failedMessageCount,
                    message.getMessageProperties().getMessageId(),
                    message.getMessageProperties().getConsumerQueue(),
                    message.getMessageProperties().getConsumerTag(),
                    type
            );
            SimpleAsyncTaskExecutor executor;


            switch(type) {
                case DoNothing:
                    break;
                case AlwaysThrowException:
                    throw new RuntimeException("ChaosMonkey Simulated exception");
                case ThrowExceptionIfNotRequeued:
                    if (!message.getMessageProperties().isRedelivered()) {
                        throw new RuntimeException("ChaosMonkey Simulated exception");
                    }
                case CauseChannelToClose:
                    if (!message.getMessageProperties().isRedelivered()) { // we dont want to permanently close it
                        channel.queueDeclarePassive(String.valueOf(System.currentTimeMillis()));
                    }
                    break;
                case ThrowButNotRequeue:
                    throw new AmqpRejectAndDontRequeueException("ChaosMonkey simulated exception");


            }
        }

        private ChaosMonkey.ChaosMessageType extractType(Message message) {
            String type = message.getMessageProperties().getType();
            return ChaosMonkey.ChaosMessageType.parse(type, ChaosMonkey.ChaosMessageType.DoNothing);
        }
    }
}

package com.pivotal.resilient.nomessageloss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnBean(NoMessageLossConfiguration.class)
@ConditionalOnExpression("${no-message-loss.enabled:true} and ${no-message-loss.producer.enabled:true}")
public class SynchronousProducer {
    private Logger logger = LoggerFactory.getLogger(SynchronousProducer.class);

    private @Autowired @Qualifier("templateForNoMessageLoss")
    RabbitTemplate template;


    private @Autowired
    NoMessageLossConfiguration configuration;

//    @PostConstruct
//    void configureTemplate() {
//        template.setReturnCallback((message, replyCode, replyText, exchange, routingKey) -> {
//
//        });
//    }

    @Scheduled(fixedRate = 5000)
    public void sendMessage() {
        try {
            String correlationId = String.valueOf(System.currentTimeMillis());

            if (sendSynchronously(MessageBuilder.
                    withBody("hello".getBytes())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .build(), correlationId)) {
                logger.debug("Sent message {}", correlationId);
            }

        }catch(AmqpException e) {
            logger.error("Failed to sendSynchronously message due to {}", e.getMessage());
        }

    }

    private boolean sendSynchronously(Message message, String correlationId) {
        CorrelationData cd = new CorrelationData(correlationId);

        template.send(template.getExchange(), template.getRoutingKey(), message, cd);
        return waitForConfirmation(correlationId, cd);

    }

    private boolean waitForConfirmation(String correlationId, CorrelationData cd) {
        try {
            CorrelationData.Confirm confirm = log(
                    cd.getFuture().get(configuration.properties.producer.confirmTimeoutMs, TimeUnit.MILLISECONDS),
                    cd);
            return trueWhenConfirmedAndNotReturned(confirm, cd);

        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.error("Failed to get a confirmation for message {}. Reason {}", correlationId, e);
            return false;
        }
    }

    private boolean trueWhenConfirmedAndNotReturned(CorrelationData.Confirm  confirm, CorrelationData cd) {
        if (cd.getReturnedMessage() != null) {
            return false;
        }
        return confirm.isAck();
    }

    private CorrelationData.Confirm log(CorrelationData.Confirm confirm, CorrelationData cd) {
        if (cd.getReturnedMessage() != null) {
            logger.error("Returned message {}", cd.getId());
        }
        if (!confirm.isAck()) {
            logger.error("Nacked message {}", cd.getId());
        }
        return confirm;
    }

}

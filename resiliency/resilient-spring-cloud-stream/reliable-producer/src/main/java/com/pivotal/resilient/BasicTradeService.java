package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.integration.amqp.support.NackedAmqpMessageException;
import org.springframework.integration.amqp.support.ReturnedAmqpMessageException;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@EnableBinding(BasicTradeService.MessagingBridge.class)
@ConditionalOnProperty(name="tradeService", havingValue = "basic", matchIfMissing = false)
public class BasicTradeService implements TradeService {
    private final Logger logger = LoggerFactory.getLogger(BasicTradeService.class);

    interface MessagingBridge {

        String OUTBOUND_TRADE_REQUESTS = "outboundTradeRequests";

        @Output(OUTBOUND_TRADE_REQUESTS)
        MessageChannel outboundTradeRequests();
    }

    @Autowired private MessagingBridge messagingBridge;

    public BasicTradeService() {
        logger.info("Created");
    }

    private volatile long attemptCount = 0;
    private volatile long sentCount = 0;

    @Override
    public CompletableFuture<Trade> sendAsync(Trade trade) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Trade send(Trade trade) {
        logger.info("[attempts:{},sent:{}] Requesting {}", attemptCount, sentCount, trade);

        _send(trade);
        attemptCount++;

        return trade;
    }


    private void _send(Trade trade) {

        String correlationId = String.valueOf(System.currentTimeMillis());

        logger.info("Sending trade {} with correlation {}", trade.id, correlationId);

        messagingBridge.outboundTradeRequests().send(
                MessageBuilder.withPayload(trade)
                        .setHeader("correlationId", correlationId)
                        .setHeader("tradeId", trade.id)
                        .setHeader("resend", true)
                        .setHeader("account", trade.accountId).build());

        logger.info("Sent trade {}", trade.id);

    }

    @ServiceActivator(inputChannel = "errorChannel")
    public void globalError(Message<?> message) {
        logger.error("errorChannel received {}", message);
    }
    @ServiceActivator(inputChannel = "trades.errors")
    public void error(Message<?> message) {
        logger.error("trades.errors received {}", message);

        if (message.getPayload() instanceof ReturnedAmqpMessageException) {
            try {
                returnedMessage((ReturnedAmqpMessageException) message.getPayload());
            }catch(RuntimeException e) {
                System.err.println("problem");
                e.printStackTrace();
            }
        }else if (message.getPayload() instanceof NackedAmqpMessageException) {
            try {
                nackedMessage((NackedAmqpMessageException) message.getPayload());
            }catch(RuntimeException e) {
                System.err.println("problem");
                e.printStackTrace();
            }
        }else {
            logger.warn("Unknown error {}", message);
            return;
        }
    }
    private Long returnedMessage(ReturnedAmqpMessageException e) {
        org.springframework.amqp.core.Message amqpMessage = e.getAmqpMessage();
        Long tradeId = (Long)amqpMessage.getMessageProperties().getHeaders().get("tradeId");
        String id = (String)amqpMessage.getMessageProperties().getHeaders().get("correlationId");
        logger.error("Returned Trade {}", tradeId);

        return tradeId;
    }
    private Long nackedMessage(NackedAmqpMessageException e) {
        Message<?> amqpMessage = e.getFailedMessage();
        Long tradeId = (Long)amqpMessage.getHeaders().get("tradeId");
        String id = (String)amqpMessage.getHeaders().get("correlationId");
        logger.error("Nacked Trade {}", tradeId);

        return tradeId;
    }


    @ServiceActivator(inputChannel = "trades.confirm")
    public void handlePublishConfirmedTrades(Message<Trade> message) {
        try {
            Trade trade = message.getPayload();
            String id = message.getHeaders().get("correlationId", String.class);
            logger.info("Received publish confirm w/id {} => {}", id, trade);
        }catch(Exception e) {
            System.err.println("onConfirm");
            e.printStackTrace();
        }
    }


}

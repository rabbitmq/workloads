package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.integration.amqp.support.NackedAmqpMessageException;
import org.springframework.integration.amqp.support.ReturnedAmqpMessageException;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@EnableBinding(FireAndForgetTradeService.MessagingBridge.class)
public class FireAndForgetTradeService implements TradeService {
    private final Logger logger = LoggerFactory.getLogger(FireAndForgetTradeService.class);

    interface MessagingBridge {

        String OUTBOUND_TRADE_REQUESTS = "outboundTradeRequests";

        @Output(OUTBOUND_TRADE_REQUESTS)
        MessageChannel outboundTradeRequests();
    }

    @Autowired private MessagingBridge messagingBridge;

    public FireAndForgetTradeService() {
        logger.info("Created");
    }

    private volatile long attemptCount = 0;
    private volatile long sentCount = 0;

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

}

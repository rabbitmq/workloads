package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@EnableBinding(TradeLogger.MessagingBridge.class)
@ConditionalOnProperty(name="tradeLogger", matchIfMissing = false)
public class TradeLogger {
    private final Logger logger = LoggerFactory.getLogger(TradeLogger.class);

    @Autowired private MessagingBridge messagingBridge;

    interface MessagingBridge {

        String INBOUND_TRADE_REQUESTS = "inboundTradeRequests";

        @Input(INBOUND_TRADE_REQUESTS)
        SubscribableChannel inboundTradeRequests();

    }

    private volatile long receivedTradeCount;

    public TradeLogger() {
        logger.info("Created");
    }

    @StreamListener(MessagingBridge.INBOUND_TRADE_REQUESTS)
    public void execute(@Header("account") long account, @Payload String trade) {
        String tradeConfirm = String.format("Received [%d] %s (account: %d) done", ++receivedTradeCount, trade, account);
        logger.info(tradeConfirm);
    }

}

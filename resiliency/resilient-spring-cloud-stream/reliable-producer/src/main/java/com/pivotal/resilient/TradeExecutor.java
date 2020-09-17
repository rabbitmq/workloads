package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Service;

@Service
@EnableBinding(TradeExecutor.MessagingBridge.class)
@ConditionalOnProperty(name="tradeExecutor", matchIfMissing = false)
public class TradeExecutor {
    private final Logger logger = LoggerFactory.getLogger(TradeExecutor.class);

    @Autowired private MessagingBridge messagingBridge;

    interface MessagingBridge {

        String INBOUND_TRADE_REQUESTS = "inboundTradeRequests";

        @Input(INBOUND_TRADE_REQUESTS)
        SubscribableChannel inboundTradeRequests();

        String OUTBOUND_TRADE_CONFIRMS = "outboundTradeConfirms";

        @Output(OUTBOUND_TRADE_CONFIRMS)
        MessageChannel outboundTradeConfirms();

    }

    private volatile long tradesCount;

    public TradeExecutor() {
        logger.info("Created");
    }

    @StreamListener(MessagingBridge.INBOUND_TRADE_REQUESTS)
   // @SendTo(MessagingBridge.OUTBOUND_TRADE_CONFIRMS)
    public String execute(@Header("account") long account, @Payload String trade) {
        String tradeConfirm = String.format("[%d] %s (account: %d) done", ++tradesCount, trade, account);
        logger.info(tradeConfirm);
        return tradeConfirm;
    }

}

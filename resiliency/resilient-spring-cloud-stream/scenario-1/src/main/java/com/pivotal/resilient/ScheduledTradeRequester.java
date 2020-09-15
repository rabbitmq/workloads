package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
@EnableBinding(ScheduledTradeRequester.MessagingBridge.class)
@EnableScheduling
@ConditionalOnProperty(name="scheduledTradeRequester", matchIfMissing = false)
public class ScheduledTradeRequester {
    private final Logger logger = LoggerFactory.getLogger(ScheduledTradeRequester.class);
    private Random accountRandomizer = new Random(System.currentTimeMillis());

    interface MessagingBridge {

        String OUTBOUND_TRADE_REQUESTS = "outboundTradeRequests";

        @Output(OUTBOUND_TRADE_REQUESTS)
        MessageChannel outboundTradeRequests();
    }

    @Autowired private MessagingBridge messagingBridge;

    public ScheduledTradeRequester() {
        logger.info("Created");
    }

    private volatile long tradeSequence = 1;

    @Scheduled(fixedRate = 5000)
    public void produceTradeRequest() {
        String body = String.format("Trade %d", tradeSequence++);
        long account = accountRandomizer.nextInt(10);

        logger.info("Requesting {} for account {}", body, account);

        messagingBridge.outboundTradeRequests().send(
                MessageBuilder.withPayload(body).setHeader("account", account).build());
    }
}

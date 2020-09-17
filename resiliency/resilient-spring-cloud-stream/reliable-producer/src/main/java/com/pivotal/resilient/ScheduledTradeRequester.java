package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.integration.amqp.support.NackedAmqpMessageException;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

@Service
@EnableBinding(ScheduledTradeRequester.MessagingBridge.class)
@EnableScheduling
@ConditionalOnProperty(name="scheduledTradeRequester", matchIfMissing = true)
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
    private volatile long attemptCount = 0;
    private volatile long sentCount = 0;

    @Scheduled(fixedDelayString = "${tradeRateMs:1000}")
    public void produceTradeRequest() {
        Trade trade = nextTrade();

        logger.info("[attempts:{},sent:{}] Requesting {}", attemptCount, sentCount, trade);

        // send() always return true so we cannot use it to determine a successful send
        messagingBridge.outboundTradeRequests().send(
                MessageBuilder.withPayload(trade)
                        .setHeader("tradeId", trade.id)
                        .setHeader("account", trade.accountId).build());
        pendingTrades.put(trade.id, trade);

        attemptCount++;
    }

    private Trade nextTrade() {
        if (pendingTrades.isEmpty()) {
            Trade trade = new Trade();
            trade.setAccountId(accountRandomizer.nextInt(10));
            trade.setId(tradeSequence++);
            return trade;
        }
        return pendingTrades.firstEntry().getValue();
    }
    private ConcurrentNavigableMap<Long, Trade> pendingTrades = new ConcurrentSkipListMap<>();

    @ServiceActivator(inputChannel = "trades.errors")
    public void error(Message<NackedAmqpMessageException> message) {
        Long tradeId = (Long)message.getPayload().getFailedMessage().getHeaders().get("tradeId");
        logger.error("An error occurred while publishing {}", pendingTrades.get(tradeId));
    }

    @ServiceActivator(inputChannel = "trades.confirm")
    public void handlePublishConfirmedTrades(Message<Trade> message) {
        Trade trade = message.getPayload();
        logger.info("Received publish confirm => {}", trade);
        pendingTrades.remove(trade.getId());
        sentCount++;
    }

}

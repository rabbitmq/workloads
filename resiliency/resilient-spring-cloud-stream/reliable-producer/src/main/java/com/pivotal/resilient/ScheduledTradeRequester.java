package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.DependencyDescriptor;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

@Service
@EnableScheduling
@ConditionalOnProperty(name="scheduledTradeRequester", matchIfMissing = false)
public class ScheduledTradeRequester {
    private final Logger logger = LoggerFactory.getLogger(ScheduledTradeRequester.class);
    private Random accountRandomizer = new Random(System.currentTimeMillis());
    private ConcurrentNavigableMap<Long, CompletableFuture<Trade>> pendingTrades = new ConcurrentSkipListMap<>();

    public ScheduledTradeRequester() {
        logger.info("Created");
    }

    @Autowired TradeSequencer tradeSequencer;
    @Autowired TradeService tradeService;

    @Scheduled(fixedDelayString = "${tradeRateMs:1000}")
    public void produceTradeRequest() {
        pendingTrades.navigableKeySet().removeIf(id -> pendingTrades.get(id).isDone());
        if (pendingTrades.size() > 3) {
            logger.warn("Backoff : there are too many inFlight trades");
            return;
        }
        Trade trade = nextTrade();
        pendingTrades.put(trade.getId(), tradeService.send(trade));
    }
    private Trade nextTrade() {
        Trade trade = new Trade();
        trade.setAccountId(accountRandomizer.nextInt(10));
        tradeSequencer.next(trade);
        return trade;
    }


}

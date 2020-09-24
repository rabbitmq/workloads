package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

@Service
@EnableScheduling
@ConditionalOnProperty(name="asyncScheduledTradeRequester", matchIfMissing = false)
public class AsyncScheduledTradeRequester {
    private final Logger logger = LoggerFactory.getLogger(AsyncScheduledTradeRequester.class);
    private Random accountRandomizer = new Random(System.currentTimeMillis());
    private ConcurrentNavigableMap<Long, CompletableFuture<Trade>> pendingTrades = new ConcurrentSkipListMap<>();

    public AsyncScheduledTradeRequester() {
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
        pendingTrades.put(trade.getId(), tradeService.sendAsync(trade));
    }
    private Trade nextTrade() {
        Trade trade = new Trade();
        trade.setAccountId(accountRandomizer.nextInt(10));
        tradeSequencer.next(trade);
        return trade;
    }


}

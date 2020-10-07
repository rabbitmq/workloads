package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

import java.time.Duration;
import java.util.Random;

@Service
@EnableScheduling
@ConditionalOnProperty(name="scheduledTradeRequester", matchIfMissing = true)
public class ScheduledTradeRequester {
    private final Logger logger = LoggerFactory.getLogger(ScheduledTradeRequester.class);
    private Random accountRandomizer = new Random(System.currentTimeMillis());

    @Autowired
    private TradeService tradeService;
    @Autowired
    private TradeSequencer tradeSequencer;

    public ScheduledTradeRequester() {
        logger.info("Created");
    }

    @Scheduled(fixedDelayString = "${tradeRateMs:1000}")
    public void produceTradeRequest() {
        Trade trade = tradeSequencer.next(Trade.buy(accountRandomizer.nextInt(10), "VMW", 1000, System.currentTimeMillis()));

        // send() always return true so we cannot use it to determine a successful send
        tradeService.send(trade);
    }
}
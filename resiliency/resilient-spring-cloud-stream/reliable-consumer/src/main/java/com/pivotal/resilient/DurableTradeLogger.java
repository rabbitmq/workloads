package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

@Service
@EnableBinding(DurableTradeLogger.MessagingBridge.class)
@ConditionalOnProperty(name="durableTradeLogger", matchIfMissing = true)
public class DurableTradeLogger {
    private final Logger logger = LoggerFactory.getLogger(DurableTradeLogger.class);

    @Autowired private MessagingBridge messagingBridge;

    interface MessagingBridge {

        String INPUT = "durable-trade-logger-input";

        @Input(INPUT)
        SubscribableChannel input();

    }

    @Value("${processingTime:1s}")
    private Duration processingTime;

    public DurableTradeLogger() {
        logger.info("Created");
    }

    private TrackMissingTrades missingTradesTracker = new TrackMissingTrades();
    private long processedTradeCount;

    @StreamListener(MessagingBridge.INPUT)
    public void execute(Trade trade) {
        missingTradesTracker.accept(trade);
        logger.info("Received {}", trade);

        boolean successfully = false;
        try {
            Thread.sleep(processingTime.toMillis());
            chaosMonkey.accept(trade);
            successfully = true;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            logger.info("Processed {} {} ", trade, successfully ? "success" : "failed");
            if (successfully) processedTradeCount++;
            logSummary(trade);
        }
    }

    private void logSummary(Trade trade) {
        logger.info("Trade summary after trade {}: total received:{}, missed:{}, processed:{}",
                trade.getId(), missingTradesTracker.receivedTradeCount,
                missingTradesTracker.missedTradeCount,
                processedTradeCount);
    }

    class TrackMissingTrades implements Consumer<Trade> {
        private long missedTradeCount;
        private long lastTradeId = -1;
        private long receivedTradeCount;

        @Override
        public void accept(Trade trade) {
            receivedTradeCount++;
            if (lastTradeId > -1 && lastTradeId != trade.id) {
                if (trade.id > lastTradeId + 1) missedTradeCount++;
            }
            lastTradeId = trade.id;
        }
    }

    private ChaosMonkey chaosMonkey = new ChaosMonkey();

    class ChaosMonkey implements Consumer<Trade> {
        private Map<Long, Integer> timesFailed = new HashMap<>();
        private int maxFailTimes = 2;
        private Predicate<Trade> shouldFailThisTrade = t->t.getId() == 3;
        private Predicate<Trade> hasExceededMaxFailures = t-> {
            Integer times = timesFailed.get(t.getId());
            return times != null && times > maxFailTimes;
        };

        public void accept(Trade trade) {
            if (shouldFailThisTrade.test(trade)) {
                logger.warn("Simulating processing error on trade {}", trade.getId());
                timesFailed.compute(trade.id, (aLong, integer) -> integer == null ? 1 : integer++);
                if (hasExceededMaxFailures.test(trade))
                    throw new AmqpRejectAndDontRequeueException("ChaosMonkey " + trade.getId());
                else throw new RuntimeException("ChaosMonkey " + trade.getId());
            }
        }
    }
}

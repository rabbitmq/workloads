package com.pivotal.resilient;

import com.pivotal.resilient.chaos.FaultyConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.atomic.AtomicLong;

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
    @Autowired private FaultyConsumer faultyConsumer;
    private TrackMissingTrades missingTradesTracker = new TrackMissingTrades();

    private long processedTradeCount;

    public DurableTradeLogger() {
        logger.info("Created");
    }

    @StreamListener(MessagingBridge.INPUT)
    public void execute(@Header("account") long account,
                        @Header("tradeId") long tradeId,
                        @Payload Trade trade) {

        missingTradesTracker.accept(trade);

        logger.info("Received {} done", trade);
        try {
            Thread.sleep(processingTime.toMillis());
            faultyConsumer.accept(trade);
            logger.info("Successfully Processed trade {}", trade.getId());
            processedTradeCount++;
        } catch (RuntimeException e) {
            logger.info("Failed to processed trade {} due to {}", trade.getId(), e.getMessage());
            throw e;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            logSummary(trade);
        }

    }
    private void logSummary(Trade trade) {
        logger.info("Trade summary after trade {}: total received:{}, missed:{}, processed:{}",
                trade.getId(), missingTradesTracker.getReceivedTradeCount(),
                missingTradesTracker.getMissedTradeCount(),
                processedTradeCount);
    }
}

package com.pivotal.resilient;

import com.pivotal.resilient.chaos.FaultyConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.Transformer;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Random;

@Service
@EnableBinding(TradeProcessor.MessagingBridge.class)
public class TradeProcessor {
    private final Logger logger = LoggerFactory.getLogger(TradeProcessor.class);

    @Autowired private MessagingBridge messagingBridge;

    interface MessagingBridge {

        String INPUT = "trade-input";

        @Input(INPUT)
        SubscribableChannel input();

        String OUTPUT = "deal-done-output";

        @Output(OUTPUT)
        MessageChannel outboundTradeDone();
    }

    @Value("${processingTime:1s}")
    private Duration processingTime;
    @Autowired private FaultyConsumer faultyConsumer;
    private TrackMissingTrades missingTradesTracker = new TrackMissingTrades();
    private Random randomPrice = new Random(System.currentTimeMillis());
    private long processedTradeCount;

    public TradeProcessor() {
        logger.info("Created");
    }

    @StreamListener(MessagingBridge.INPUT)
    @Output(MessagingBridge.OUTPUT)
    public Trade execute(@Header("account") long account,
                        @Header("tradeId") long tradeId,
                        @Payload Trade trade) {

        missingTradesTracker.accept(trade);

        logger.info("Received {} done", trade);
        try {
            Thread.sleep(processingTime.toMillis());
            faultyConsumer.accept(trade);

            trade = trade.executed(BigDecimal.valueOf(randomPrice.nextDouble()));
            logger.info("Successfully Executed trade {} @ {}", trade.getId(), trade.getPrice());

            processedTradeCount++;
            return trade;
        } catch (RuntimeException e) {
            logger.info("Failed to processed trade {} due to {}", trade.getId(), e.getMessage());
            throw e;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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

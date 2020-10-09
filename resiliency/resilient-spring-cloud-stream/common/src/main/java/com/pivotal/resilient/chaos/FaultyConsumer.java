package com.pivotal.resilient.chaos;

import com.pivotal.resilient.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateAcknowledgeAmqpException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Component
public class FaultyConsumer implements Consumer<Trade> {
    private final Logger logger = LoggerFactory.getLogger(FaultyConsumer.class);

    private Map<Long, Integer> timesFailed = new HashMap<>();

    private Predicate<Trade> shouldFailThisTrade;
    private Predicate<Trade> hasExceededMaxFailures;
    private ChaosMonkeyProperties properties;

    public FaultyConsumer(ChaosMonkeyProperties properties) {
        this.properties = properties;
        shouldFailThisTrade = t->t.getId() == properties.getTradeId();
        hasExceededMaxFailures = t-> {
            Integer times = timesFailed.get(t.getId());
            return times != null && times > properties.getMaxFailTimes();
        };
    }

    public void accept(Trade trade) {
        if (shouldFailThisTrade.test(trade)) {
            int attempts = timesFailed.compute(trade.getId(), (aLong, integer) -> integer == null ? 1 : ++integer);
            logger.warn("Simulating failure. Attempts:{}", attempts);
            if (hasExceededMaxFailures.test(trade)) {
                logger.warn("Simulating failure. Has exceeded maxTimes:{}. next:{}", properties.getMaxFailTimes(),
                        properties.getActionAfterMaxFailTimes().name());
                switch(properties.getActionAfterMaxFailTimes()) {
                    case immediateAck:
                        throw new ImmediateAcknowledgeAmqpException(
                                String.format("ChaosMonkey+ImmediateAck trade %d due after %d attempts", trade.getId(), attempts));
                    case reject:
                        throw new AmqpRejectAndDontRequeueException(
                                String.format("ChaosMonkey+Reject trade %d due after %d attempts", trade.getId(), attempts));
                    case exit:
                    default:
                        System.exit(-1);
                }

            }else throw new RuntimeException(String.format("ChaosMonkey on trade %d after %d attempts",
                    trade.getId(), attempts));
        }
    }
}

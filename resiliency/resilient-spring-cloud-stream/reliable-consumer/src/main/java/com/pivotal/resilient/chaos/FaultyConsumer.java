package com.pivotal.resilient.chaos;

import com.pivotal.resilient.Trade;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Component
public class FaultyConsumer implements Consumer<Trade> {
    private Map<Long, Integer> timesFailed = new HashMap<>();

    private Predicate<Trade> shouldFailThisTrade;
    private Predicate<Trade> hasExceededMaxFailures;

    public FaultyConsumer(ChaosMonkeyProperties properties) {

        shouldFailThisTrade = t->t.getId() == properties.getTradeId();
        hasExceededMaxFailures = t-> {
            Integer times = timesFailed.get(t.getId());
            return times != null && times > properties.getMaxFailTimes();
        };
    }

    public void accept(Trade trade) {
        if (shouldFailThisTrade.test(trade)) {
            timesFailed.compute(trade.getId(), (aLong, integer) -> integer == null ? 1 : integer++);
            if (hasExceededMaxFailures.test(trade))
                throw new AmqpRejectAndDontRequeueException("ChaosMonkey " + trade.getId());
            else throw new RuntimeException("ChaosMonkey " + trade.getId());
        }
    }
}

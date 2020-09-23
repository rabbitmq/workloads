package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TradeSequencer {
    private final Logger logger = LoggerFactory.getLogger(TradeSequencer.class);

    public TradeSequencer() {
        logger.info("Created");
    }

    private volatile long tradeSequence = 1;

    public Trade next(Trade trade) {
        trade.setId(tradeSequence++);
        return trade;
    }

}

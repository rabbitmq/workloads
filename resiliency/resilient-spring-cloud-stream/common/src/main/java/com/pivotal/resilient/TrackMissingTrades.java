package com.pivotal.resilient;

import java.util.function.Consumer;

public class TrackMissingTrades implements Consumer<Trade> {
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

    public long getMissedTradeCount() {
        return missedTradeCount;
    }

    public long getLastTradeId() {
        return lastTradeId;
    }

    public long getReceivedTradeCount() {
        return receivedTradeCount;
    }
}

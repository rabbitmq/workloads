package com.pivotal.resilient;

import java.util.concurrent.CompletableFuture;

public interface TradeService {
    Trade send(Trade trade);
    CompletableFuture<Trade> sendAsync(Trade trade);

    public class MessagingBridge {
    }
}

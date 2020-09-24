package com.pivotal.resilient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@EnableAsync
public class TradeRequesterController {

    @Autowired
    TradeService tradeService;

    @Autowired
    TradeSequencer tradeSequencer;

    @PostMapping("/execute")
    public Trade execute(@RequestBody Trade tradeRequest) {
        return tradeService.send(tradeSequencer.next(tradeRequest));
    }
    @PostMapping("/execute-async")
    public CompletableFuture<Trade> executeAsync(@RequestBody Trade tradeRequest) {
        return tradeService.sendAsync(tradeSequencer.next(tradeRequest));
    }
}

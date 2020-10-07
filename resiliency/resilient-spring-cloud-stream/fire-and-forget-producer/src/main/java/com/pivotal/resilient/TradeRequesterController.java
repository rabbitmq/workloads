package com.pivotal.resilient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TradeRequesterController {

    @Autowired
    TradeService tradeService;

    @Autowired
    TradeSequencer tradeSequencer;

    @PostMapping("/execute")
    public Trade execute(@RequestBody Trade tradeRequest) {
        return tradeService.send(tradeSequencer.next(tradeRequest));
    }

}

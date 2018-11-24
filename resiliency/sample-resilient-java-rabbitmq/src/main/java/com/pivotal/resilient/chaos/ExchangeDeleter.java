package com.pivotal.resilient.chaos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

class ExchangeDeleter extends ChaosBunnyAbstract {
    private String exchangeName;
    private Logger logger = LoggerFactory.getLogger(ExchangeDeleter.class);

    public ExchangeDeleter(TaskScheduler scheduler, long frequency, String exchangeName) {
        super(scheduler, frequency);
        this.exchangeName = exchangeName;
    }

    @Override
    public void run() {
        logger.warn("ChaosMonic deleting exchange {}", exchangeName);
        executeOnChannel((channel) -> channel.exchangeDelete(exchangeName));
    }


    @Override
    public String getName() {
        return "ExchangeDeleter-" + exchangeName;
    }
}

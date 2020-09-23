package com.pivotal.resilient.chaos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "chaos")
public class ChaosMonkeyProperties {

    private int maxFailTimes = 2;
    private long tradeId = 3;

    public int getMaxFailTimes() {
        return maxFailTimes;
    }

    public void setMaxFailTimes(int maxFailTimes) {
        this.maxFailTimes = maxFailTimes;
    }

    public long getTradeId() {
        return tradeId;
    }

    public void setTradeId(long tradeId) {
        this.tradeId = tradeId;
    }
}


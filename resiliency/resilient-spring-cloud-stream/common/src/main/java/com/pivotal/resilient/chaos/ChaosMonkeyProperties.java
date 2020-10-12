package com.pivotal.resilient.chaos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
@ConfigurationProperties(prefix = "chaos")
public class ChaosMonkeyProperties {

    public ActionAfterMaxFailTimes getActionAfterMaxFailTimes() {
        return actionAfterMaxFailTimes;
    }

    public void setActionAfterMaxFailTimes(ActionAfterMaxFailTimes actionAfterMaxFailTimes) {
        this.actionAfterMaxFailTimes = actionAfterMaxFailTimes;
    }

    public enum ActionAfterMaxFailTimes { nothing, reject, immediateAck, exit }

    private int maxFailTimes = 2;
    private Optional<Long> tradeId = Optional.empty();
    private ActionAfterMaxFailTimes actionAfterMaxFailTimes = ActionAfterMaxFailTimes.nothing;

    public int getMaxFailTimes() {
        return maxFailTimes;
    }

    public void setMaxFailTimes(int maxFailTimes) {
        this.maxFailTimes = maxFailTimes;
    }

    public Optional<Long> getTradeId() {
        return tradeId;
    }

    public void setTradeId(Long tradeId) {
        this.tradeId = Optional.of(tradeId);
    }

}


package com.pivotal.resilient.chaos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "chaos")
public class ChaosMonkeyProperties {

    public ActionAfterMaxFailTimes getActionAfterMaxFailTimes() {
        return actionAfterMaxFailTimes;
    }

    public void setActionAfterMaxFailTimes(ActionAfterMaxFailTimes actionAfterMaxFailTimes) {
        this.actionAfterMaxFailTimes = actionAfterMaxFailTimes;
    }

    public static enum ActionAfterMaxFailTimes { nothing, reject, immediateAck, exit }

    private int maxFailTimes = 2;
    private long tradeId = 3;
    private ActionAfterMaxFailTimes actionAfterMaxFailTimes = ActionAfterMaxFailTimes.nothing;

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


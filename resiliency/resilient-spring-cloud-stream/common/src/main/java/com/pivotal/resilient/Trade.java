package com.pivotal.resilient;

import java.math.BigDecimal;
import java.text.DecimalFormat;

public class Trade {
    long id;
    long accountId;
    String asset;
    long amount;
    boolean buy;
    long timestamp;


    BigDecimal price;

    private static DecimalFormat priceFormat = new DecimalFormat("##.00");

    @Override
    public String toString() {
        return new StringBuilder("Trade{")
                .append(" tradeId=").append(id)
                .append(" accountId=").append(accountId)
                .append(" asset=").append(asset)
                .append(" amount=").append(amount)
                .append(" buy=").append(buy)
                .append(" timestamp=").append(timestamp)
                .append(" ").append(price == null ? " Request" : " Executed @ ")
                    .append(price == null ? "" : priceFormat.format(price))
                .append("}").toString();
    }

    public Trade() {
    }

    public Trade(long accountId, String asset, long amount, boolean buy, long timestamp) {
        this.accountId = accountId;
        this.asset = asset;
        this.amount = amount;
        this.buy = buy;
        this.timestamp = timestamp;
    }

    public static Trade buy(long acountId, String asset, long amount, long timestamp) {
        return new Trade(acountId, asset, amount, true, timestamp);
    }
    public static Trade sell(long acountId, String asset, long amount, long timestamp) {
        return new Trade(acountId, asset, amount, false, timestamp);
    }

    public Trade executed(BigDecimal price) {
        Trade trade = new Trade(accountId, asset, amount, buy, timestamp);
        trade.id = id;
        trade.price = price;
        return trade;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getAccountId() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

    public String getAsset() {
        return asset;
    }

    public void setAsset(String asset) {
        this.asset = asset;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public boolean isBuy() {
        return buy;
    }

    public void setBuy(boolean buy) {
        this.buy = buy;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public BigDecimal getPrice() {
        return price;
    }

}

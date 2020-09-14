package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Random;

@Configuration
@EnableBinding(Scenario.MessagingBridge.class)
@EnableScheduling
public class Scenario {
    private final Logger logger = LoggerFactory.getLogger(Scenario.class);
    private Random accountRandomizer = new Random(System.currentTimeMillis());

    interface MessagingBridge {

        String TRADES_EMITTER = "tradesEmitter";
        String TRADES = "trades";

        @Output(TRADES_EMITTER)
        MessageChannel tradesEmitter();

        @Input(TRADES)
        SubscribableChannel trades();


    }
    @Autowired private MessagingBridge messagingBridge;

    @Scheduled(fixedRate = 5000)
    public void produceTradeRequest() {
        String body = String.format("Trade %d", System.currentTimeMillis());
        long account = accountRandomizer.nextInt(10);

        logger.info("Producing trade request {} for account {}", body, account);

        messagingBridge.tradesEmitter().send(
                MessageBuilder.withPayload(body).setHeader("account", account).build());
    }

    private volatile long tradesCount;

    @StreamListener(MessagingBridge.TRADES)
    public void execute(@Header("account") long account, @Payload String trade) {
        logger.info(String.format("[%d] %s (account: %d) done", ++tradesCount, trade, account));
    }


}

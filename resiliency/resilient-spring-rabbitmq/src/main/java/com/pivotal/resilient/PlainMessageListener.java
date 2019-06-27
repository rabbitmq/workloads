package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;

public class PlainMessageListener implements MessageListener {

    private Logger logger = LoggerFactory.getLogger(PlainMessageListener.class);

    private String name;
    private long receivedMessageCount;
    private long failedMessageCount;


    public PlainMessageListener(String name) {
        this.name = name;
    }
    public PlainMessageListener() {
        this("");
    }


    @Override
    public void onMessage(Message message) {

        logger.info("{}/{} received (#{}/#{}) from {}/{} ",
                name,
                Thread.currentThread().getId(),
                receivedMessageCount,
                failedMessageCount,
                message.getMessageProperties().getConsumerQueue(),
                message.getMessageProperties().getConsumerTag()

        );
        receivedMessageCount++;
    }
}

package com.pivotal.resilient.chaos;

import com.pivotal.resilient.amqp.AMQPConnectionRequester;
import com.rabbitmq.client.Channel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class ChaosBunny {

    @Autowired
    private TaskScheduler scheduler;

    public ChaosBunny(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void closeChannelOnSeparateThread(Channel channel) {
        ChannelCloser channelCloser = new ChannelCloser(channel);
        new Thread(channelCloser).start();

        try {
            channelCloser.waitUntilChannelCloserRuns(5000);
        } catch (InterruptedException e) {

        }
    }
    public AMQPConnectionRequester deleteExchangeAtFixedRate(String exchange, long rate){
        return new ExchangeDeleter(scheduler, 20000, exchange);
    }

    public RandomChannelCloser randomChannelCloser(long rate) {
        return new RandomChannelCloser(scheduler, rate);
    }
}

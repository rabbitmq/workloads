package com.pivotal.resilient.chaos;

import com.rabbitmq.client.Channel;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ChannelCloser extends RandomChannelCloser {

    private Semaphore semaphore;

    ChannelCloser(Channel channel) {
        super(null, 0);
        lastCreatedChannel.set(channel);
        this.semaphore = new Semaphore(0);
    }

    @Override
    public void run() {
        try {
            super.run();
        }finally {
            semaphore.release();
        }
    }
    public void waitUntilChannelCloserRuns(long timeout) throws InterruptedException {
        this.semaphore.tryAcquire(timeout, TimeUnit.MILLISECONDS);
    }
    @Override
    public String getName() {
        return "ChannelCloser";
    }
}

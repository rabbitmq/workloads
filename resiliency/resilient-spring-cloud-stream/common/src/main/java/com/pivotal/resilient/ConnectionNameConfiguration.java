package com.pivotal.resilient;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class ConnectionNameConfiguration {
    private AtomicLong incr = new AtomicLong();

    @Value("${spring.application.name:rabbitConnectionFactory}")
    private String applicationName;

    @EventListener
    public void eventListener(ContextRefreshedEvent event) {
        CachingConnectionFactory factory = event.getApplicationContext().getBean(CachingConnectionFactory.class);

        factory.setConnectionNameStrategy(
                conn -> String.format("%s#%d", applicationName, this.incr.incrementAndGet()));

    }

}

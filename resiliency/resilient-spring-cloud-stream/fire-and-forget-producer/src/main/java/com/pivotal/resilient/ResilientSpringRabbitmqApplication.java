package com.pivotal.resilient;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.atomic.AtomicLong;

@SpringBootApplication
public class ResilientSpringRabbitmqApplication {
	private AtomicLong incr = new AtomicLong();
	@EventListener
	public void eventListener(ContextRefreshedEvent event) {
		CachingConnectionFactory factory = event.getApplicationContext().getBean(CachingConnectionFactory.class);

		factory.setConnectionNameStrategy(
				conn -> "fire-and-forget" + this.incr.incrementAndGet());

	}
	public static void main(String[] args) {
		SpringApplication.run(ResilientSpringRabbitmqApplication.class, args);
	}
}

package com.pivotal.rabbitmq.example;

import com.pivotal.rabbitmq.stompws.RabbitStompWsClient;
import com.pivotal.rabbitmq.stompws.RabbitStompWsClient.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.CompletionStage;

@SpringBootApplication
@EnableScheduling
public class ClientStompWsApplication {

	Logger log = LoggerFactory.getLogger(ClientStompWsApplication.class);
	@Autowired
	TaskScheduler taskScheduler;

	@Autowired
	@Qualifier("stompForScheduledTask")
	RabbitStompWsClient rabbit;

	@Bean
	public CommandLineRunner sendMessagesToTest() {
		return (args) -> {
			taskScheduler.scheduleAtFixedRate(()-> {
				rabbit.send("/queue/test", String.valueOf(System.currentTimeMillis()))
						.thenRun(()->{log.info("Sent to test");})
						.exceptionally(throwable -> {log.error("Failed to send", throwable); return null;});
			}, 2000);
		};
	}
	@Bean
	public CommandLineRunner sendMessagesToTest2() {
		return (args) -> {
			taskScheduler.scheduleAtFixedRate(()-> {
				rabbit.send("/queue/test2", String.valueOf(System.currentTimeMillis()))
						.thenRun(()->{log.info("Sent to test2");})
						.exceptionally(throwable -> {log.error("Failed to send", throwable); return null;});
			}, 3000);
		};
	}

	@Bean
	public CompletionStage<Subscription> queueTestListener() {
		return rabbit.subscribe("/queue/test", String.class,
				(stompHeaders, s) -> {log.info("/queue/test : Received message {}", s);});
	}

	@Bean
	public CompletionStage<Subscription> queueTest2Listener() {
		return rabbit.subscribe("/queue/test2", String.class,
				(stompHeaders, s) -> {log.info("/queue/test2 : Received message {}", s);});
	}


	public static void main(String[] args) {
		SpringApplication.run(ClientStompWsApplication.class, args);
	}

}

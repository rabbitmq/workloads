package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.java.ServiceScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
@EnableScheduling
//@ServiceScan
public class ResilientSpringRabbitmqApplication {

	private Logger logger = LoggerFactory.getLogger(ResilientSpringRabbitmqApplication.class);

	@Autowired
	RabbitTemplate template;

	@Scheduled(fixedRate = 5000)
	public void sendMessage() {
		try {
			template.send(MessageBuilder.withBody("hello".getBytes()).build());
		}catch(RuntimeException e) {
			logger.warn("Failed to send message due to {}", e.getMessage());
		}
	}


	public static void main(String[] args) {
		SpringApplication.run(ResilientSpringRabbitmqApplication.class, args);
	}
}

package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
@EnableScheduling
public class ResilientSpringRabbitmqApplication {

	private Logger logger = LoggerFactory.getLogger(ResilientSpringRabbitmqApplication.class);

	@Autowired
	RabbitTemplate template;


	@Scheduled(fixedRate = 5000)
	public void sendMessage() {
		try {
			String body = String.format("hello @ %d", System.currentTimeMillis());
			template.send(MessageBuilder.withBody(body.getBytes()).build());
		}catch(RuntimeException e) {
			logger.warn("Failed to send message due to {}", e.getMessage());
		}
	}

	@RabbitListener(bindings = @QueueBinding(
			value = @Queue,
			exchange = @Exchange(name = "amq.direct"),
			key = "hello"))
	public void listenForHelloMessages(byte[] in) {
		System.out.println(new String(in));
	}


	public static void main(String[] args) {
		SpringApplication.run(ResilientSpringRabbitmqApplication.class, args);
	}
}

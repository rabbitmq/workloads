package com.pivotal.resilient.samplespringrabbitmq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
@EnableScheduling
public class SampleSpringRabbitmqApplication {

	private Logger logger = LoggerFactory.getLogger(SampleSpringRabbitmqApplication.class);


	@Autowired
	@Qualifier("durable_test")
	RabbitTemplate durable_test;


	@Scheduled(fixedDelay=5000)
	public void sendMessages() {		// really bad because we are permanently creating and deleting channels
		durable_test.invoke(t -> {
			t.convertAndSend("hello");
			return true;
		});
	}


	@Bean
	public SimpleMessageListenerContainer durableTestConsumer(@Qualifier("consumer") ConnectionFactory connectionFactory) {
		SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.setQueueNames("durable-test");
		container.setMessageListener(message -> {
			logger.info("ConsumerTag {} received message", message.getMessageProperties().getConsumerTag());
		});

		return container;
	}

	public static void main(String[] args) {
		SpringApplication.run(SampleSpringRabbitmqApplication.class, args);
	}
}

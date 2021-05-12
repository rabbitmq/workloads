package com.pivotal.resilient.samplespringrabbitmq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Controller;

@SpringBootApplication
@EnableScheduling
@Controller
public class SampleSpringRabbitmqApplication {

	private Logger log = LoggerFactory.getLogger(SampleSpringRabbitmqApplication.class);

	@Bean
	public CommandLineRunner printOutRabbitProperties(RabbitProperties rabbitProperties) {
		return (args) -> {
			log.info("Host: {}", rabbitProperties.getHost());
			log.info("Port: ", rabbitProperties.getPort());
			log.info("Addresses: {}", rabbitProperties.getAddresses());
			log.info("Username: {}", rabbitProperties.getUsername());
			log.info("Password: {}",rabbitProperties.getPassword());
			log.info("vHost:  {}", rabbitProperties.getVirtualHost());
		};
	}
	public static void main(String[] args) {
		SpringApplication.run(SampleSpringRabbitmqApplication.class, args);
	}
}

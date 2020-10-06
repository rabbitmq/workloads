package com.pivotal.resilient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
public class ResilientSpringRabbitmqApplication {


	//@Bean
	public RetryTemplate retryPublish() {
		RetryTemplate t = new RetryTemplate();
		return t;

	}

	public static void main(String[] args) {
		SpringApplication.run(ResilientSpringRabbitmqApplication.class, args);
	}
}

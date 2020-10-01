package com.pivotal.resilient;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.amqp.rabbit.connection.SimplePropertyValueConnectionNameStrategy;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ResilientSpringRabbitmqApplication {
	@Bean
	public ConnectionNameStrategy cns() {
		return new SimplePropertyValueConnectionNameStrategy("spring.application.name");
	}
	public static void main(String[] args) {
		SpringApplication.run(ResilientSpringRabbitmqApplication.class, args);
	}
}

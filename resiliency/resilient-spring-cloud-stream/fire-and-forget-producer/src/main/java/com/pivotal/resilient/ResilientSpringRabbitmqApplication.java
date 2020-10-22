package com.pivotal.resilient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.cloudfoundry.CloudFoundryConnector;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ResilientSpringRabbitmqApplication {

	@Autowired(required = false)
	Cloud cloud;

	@Autowired(required = false)
	CloudFoundryConnector connector;

	@Bean
	CommandLineRunner runner() {
		return (args)-> {

			System.out.printf("%s\n", System.getenv("VCAP_APPLICATION"));
			System.out.printf("%s\n", cloud != null ? cloud.getApplicationInstanceInfo().toString() : "none");
			if (connector != null) System.out.println("Has connector");
		};
	}
	public static void main(String[] args) {
		SpringApplication.run(ResilientSpringRabbitmqApplication.class, args);
	}
}

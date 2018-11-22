package com.pivotal.resilient.samplespringrabbitmq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Controller;

@SpringBootApplication
@EnableScheduling
@Controller
public class SampleSpringRabbitmqApplication {

	private Logger logger = LoggerFactory.getLogger(SampleSpringRabbitmqApplication.class);


	public static void main(String[] args) {
		SpringApplication.run(SampleSpringRabbitmqApplication.class, args);
	}
}

package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ResilientApplication {

    private Logger logger = LoggerFactory.getLogger(ResilientApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(ResilientApplication.class, args);
	}

}



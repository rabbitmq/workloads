package com.pivotal.resilient;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;


@Configuration
public class AMQPResourceConfig {


    /**
     * Creates a RabbitAdmin with the default/primary connectionFactory which is typically the
     * consumer one. This is very important when we use this RabbitAdmin from a listener container
     * that declares exclusive queues. For more information, https://docs.spring.io/spring-amqp/reference/html/#separate-connection
     */
    @Bean
    public RabbitAdmin rabbitAdmin(@Qualifier("producer") ConnectionFactory factory) {
        RabbitAdmin admin  = new RabbitAdmin(factory);

        // This is key if we only have just on RabbitAdmin otherwise one
        // failure could cause the rest of the declarations to fail
        admin.setIgnoreDeclarationExceptions(true);

        return admin;
    }


}


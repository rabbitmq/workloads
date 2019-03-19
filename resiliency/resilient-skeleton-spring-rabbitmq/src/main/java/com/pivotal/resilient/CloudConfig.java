package com.pivotal.resilient;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.cloud.config.java.AbstractCloudConfig;
import org.springframework.cloud.config.java.ServiceConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Use this configuration class (by uncommenting the @Configuration line) when we want to customize
 * a service's connection factory like RabbitMQ ConnectionFactory class or when we have more than one RabbitMQ service instances.
 * Else add the annotation <code>@ServiceScan</code> do the {@link com.pivotal.resilient.ResilientSpringRabbitmqApplication} class
 * and delete this class.
 *
 * {@link #rabbitFactory()} method returns the same instance that would be built by @ServiceScan.
 * However, we can override this method and customize the factory from {@link org.springframework.boot.autoconfigure.amqp.RabbitProperties}
 *
 */
@Configuration
public class CloudConfig extends AbstractCloudConfig {

    @Bean
    public ConnectionFactory rabbitFactory() {
        ServiceConnectionFactory scf = connectionFactory();
        return scf.rabbitConnectionFactory();
    }


}
package com.pivotal.resilient;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.service.common.AmqpServiceInfo;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

@SpringBootApplication
public class ResilientApplication {

    private Logger logger = LoggerFactory.getLogger(ResilientApplication.class);

    String queueName = "non-durable-test";

	@Bean
	public CloudFactory cloudFactory() {
		CloudFactory cloudFactory = new CloudFactory();
		return cloudFactory;
	}

	@Bean
    public Cloud cloud(CloudFactory factory) {
	    return factory.getCloud();
    }

    @Bean
    public ConnectionFactory amqpConnectionFactory(Cloud cloud) throws NoSuchAlgorithmException, KeyManagementException, URISyntaxException {
        ConnectionFactory factory = new ConnectionFactory();
        String uri = getAmqpAddressFrom(cloud);
        factory.setUri(uri);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);
        factory.setConnectionTimeout(10000);

        logger.info("Creating connection factory using the following uri {}", uri);

        return factory;
    }

	@Bean
	public Connection amqpConnection(ConnectionFactory factory) throws IOException, TimeoutException {
        return factory.newConnection();
	}

    public void consumeMessages(Connection amqpConn) throws IOException {
        Channel channel = amqpConn.createChannel();
        boolean durable = false;
        channel.queueDeclare(queueName, durable, false, false, null);

        channel.basicConsume(queueName, true,
                new DefaultConsumer(channel) {
                    long instanceId = System.currentTimeMillis();
                    @Override
                    public void handleDelivery(String consumerTag,
                                               Envelope envelope,
                                               AMQP.BasicProperties properties,
                                               byte[] body) throws IOException
                    {
                        String routingKey = envelope.getRoutingKey();
                        long deliveryTag = envelope.getDeliveryTag();
                        logger.debug("Received message on {} with routingKey {} and deliveryTag {} on Consumer {}",
                                queueName, routingKey, deliveryTag, instanceId);
                    }
                });
    }

	private String getAmqpAddressFrom(Cloud cloud) {
        AmqpServiceInfo amqp = cloud.getSingletonServiceInfoByType(AmqpServiceInfo.class);
        return amqp.getUri();
    }

	public static void main(String[] args) {
		SpringApplication.run(ResilientApplication.class, args);
	}

	private List<String> getUris(AmqpServiceInfo amqp) {
	    return amqp.getUris() == null ?
                Arrays.asList(amqp.getUri()) :
                amqp.getUris();
    }

	@Bean
    CommandLineRunner launchConsumer(Connection amqpConn) {
        return (args) -> {
            consumeMessages(amqpConn);
        };
    }


}

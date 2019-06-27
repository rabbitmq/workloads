package com.pivotal.resilient.nomessageloss;

import com.pivotal.resilient.durable.DurableResourcesConfiguration;
import lombok.extern.log4j.Log4j2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "no-message-loss", value = "enabled", matchIfMissing = false)
@EnableConfigurationProperties(NoMessageLossProperties.class)
/**
 * This properties requires the following properties in order to use publisher-confirms and
 * returns to prevent message loss:
 * <code>
 * spring.rabbitmq:
 *     publisher-confirms: true
 *     publisher-returns: true
 * </code>
 *
 */
public class NoMessageLossConfiguration {

    private Logger logger = LoggerFactory.getLogger(NoMessageLossConfiguration.class);

    @Autowired
    NoMessageLossProperties properties;

    @Bean
    public Queue queue() {
        return QueueBuilder.durable(properties.queueName).build();
    }

    @Bean
    public Exchange exchange() {
        return ExchangeBuilder.directExchange(properties.exchangeName).build();
    }

    @Bean
    public Binding binding() {
        return new Binding(properties.queueName, Binding.DestinationType.QUEUE,
                properties.exchangeName, properties.routingKey, null);
    }

    @Bean
    public RabbitTemplate templateForNoMessageLoss(@Qualifier("producer") ConnectionFactory connectionFactory) {
        logger.info("Creating templateForNoMessageLoss ...");

        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setRoutingKey(properties.routingKey);
        template.setExchange(properties.exchangeName);
        //template.setUsePublisherConnection(true); // this is not necessary as we are already wiring the template with the producer connection factory

        reportUnroutableMessages(template);

        return template;
    }

    // Returns are sent to the client by it registering a RabbitTemplate.ReturnCallback
    private void reportUnroutableMessages(RabbitTemplate template) {
        template.setMandatory(true);
    }
}


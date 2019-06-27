package com.pivotal.resilient;

import com.pivotal.cloud.service.messaging.SpringRabbitConnectionFactoryCreator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.AbstractConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.actuate.metrics.amqp.RabbitMetrics;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.cloud.config.java.AbstractCloudConfig;
import org.springframework.cloud.service.common.AmqpServiceInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
public class AMQPConnectionFactoryConfig extends AbstractCloudConfig {
    private Logger logger = LoggerFactory.getLogger(AMQPConnectionFactoryConfig.class);

    @Autowired
    MeterRegistry meterRegistry;

    @Value("${spring.application.name:demo}")
    String applicationName;

    private SpringRabbitConnectionFactoryCreator rabbitConnectionFactoryCreator = new SpringRabbitConnectionFactoryCreator();

    @Bean("consumer")
    public ConnectionFactory consumer(RabbitProperties rabbitProperties,
                                           ObjectProvider<ConnectionNameStrategy> connectionNameStrategies) {
        logger.info("Creating consumer Spring ConnectionFactory ...");
        return configureMetricCollections(rabbitConnectionFactory(rabbitProperties, connectionNameStrategies), "consumer");
    }

    @Bean("producer")
    @Primary
    public ConnectionFactory producer(RabbitProperties rabbitProperties,
                                      ObjectProvider<ConnectionNameStrategy> connectionNameStrategies) {
        logger.info("Creating producer Spring ConnectionFactory ...");
        return configureMetricCollections(rabbitConnectionFactory(rabbitProperties, connectionNameStrategies), "producer");
    }

    @Bean
    MeterRegistryCustomizer<MeterRegistry> commonTags() {
        return r -> r.config().commonTags(
                "cf-app-name", cloud().getApplicationInstanceInfo().getAppId(),
                "cf-app-id", cloud().getApplicationInstanceInfo().getInstanceId(),
                "app-name", applicationName,
                "cf-space-id", (String)cloud().getApplicationInstanceInfo().getProperties().get("space_id"));
    }

    private ConnectionFactory rabbitConnectionFactory(RabbitProperties rabbitProperties,
                                                      ObjectProvider<ConnectionNameStrategy> connectionNameStrategies){
        return rabbitConnectionFactoryCreator.create(
                this.cloud().getSingletonServiceInfoByType(AmqpServiceInfo.class),
                rabbitProperties, connectionNameStrategies);
    }

    private ConnectionFactory configureMetricCollections(ConnectionFactory cf, String connectionName) {
        if (cf instanceof AbstractConnectionFactory) {
            AbstractConnectionFactory acf = (AbstractConnectionFactory)cf;
            RabbitMetrics rabbitMetrics = new RabbitMetrics(acf.getRabbitConnectionFactory(), Tags.of("name", connectionName));
            rabbitMetrics.bindTo(meterRegistry);
        }
        return cf;
    }
}

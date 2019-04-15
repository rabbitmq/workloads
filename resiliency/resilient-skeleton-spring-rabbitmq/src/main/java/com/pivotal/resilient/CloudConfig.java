package com.pivotal.resilient;

import com.rabbitmq.client.impl.MicrometerMetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.AbstractConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.config.java.AbstractCloudConfig;
import org.springframework.cloud.config.java.ServiceConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.Assert;

import java.util.Collections;

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
    private Logger logger = LoggerFactory.getLogger(CloudConfig.class);

    @Autowired
    MeterRegistry meterRegistry;

    @Value("${spring.application.name}")
    String applicationName;

    @Bean("consumer")
    @Primary
    public ConnectionFactory rabbitFactory() {
        ServiceConnectionFactory scf = connectionFactory();
        return configureMetricCollections(scf.rabbitConnectionFactory(), "consumer");
    }

    @Bean("producer")
    public org.springframework.amqp.rabbit.connection.ConnectionFactory producer(ConnectionFactory consumerConnectionFactory) {
        logger.info("Creating producer Spring ConnectionFactory ...");
        ServiceConnectionFactory scf = connectionFactory();
        return configureMetricCollections(scf.rabbitConnectionFactory().getPublisherConnectionFactory(), "producer");
    }

    @Bean
    MeterRegistryCustomizer<MeterRegistry> commonTags() {
        return r -> r.config().commonTags(
                "cf-app-name", cloud().getApplicationInstanceInfo().getAppId(),
                "cf-app-id", cloud().getApplicationInstanceInfo().getInstanceId(),
                "app-name", applicationName,
                "cf-space-id", (String)cloud().getApplicationInstanceInfo().getProperties().get("space_id"));
    }

    private ConnectionFactory configureMetricCollections(ConnectionFactory cf, String connectionName) {
        if (cf instanceof AbstractConnectionFactory) {
            AbstractConnectionFactory acf = (AbstractConnectionFactory)cf;
            RabbitMetrics rabbitMetrics = new RabbitMetrics(acf.getRabbitConnectionFactory(), connectionName, null);
            rabbitMetrics.bindTo(meterRegistry);
        }
        return cf;
    }
}
class RabbitMetrics implements MeterBinder {
    private final Iterable<Tag> tags;
    private final com.rabbitmq.client.ConnectionFactory connectionFactory;
    private String name;

    RabbitMetrics(com.rabbitmq.client.ConnectionFactory connectionFactory, String name, Iterable<Tag> tags) {
        Assert.notNull(connectionFactory, "ConnectionFactory must not be null");
        this.name = name;
        this.connectionFactory = connectionFactory;
        this.tags = (Iterable)(tags != null ? tags : Collections.emptyList());
    }

    public void bindTo(MeterRegistry registry) {
        this.connectionFactory.setMetricsCollector(new MicrometerMetricsCollector(registry, "rabbitmq.client." + name, this.tags));
    }
}

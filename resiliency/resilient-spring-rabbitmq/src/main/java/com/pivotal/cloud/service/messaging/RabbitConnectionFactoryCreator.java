package com.pivotal.cloud.service.messaging;


import com.rabbitmq.client.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.RabbitConnectionFactoryBean;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.cloud.service.common.AmqpServiceInfo;

import java.time.Duration;

public class RabbitConnectionFactoryCreator  {

    public ConnectionFactory create(AmqpServiceInfo serviceInfo, RabbitProperties rabbitProperties) {
        try {
            return createConnectionFactory(serviceInfo, rabbitProperties);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ConnectionFactory createConnectionFactory(AmqpServiceInfo serviceInfo, RabbitProperties properties) throws Exception {
        com.rabbitmq.client.ConnectionFactory amqpConnectionFactory = getRabbitConnectionFactoryBean(properties).getObject();
        amqpConnectionFactory.setUri(serviceInfo.getUri());

        return amqpConnectionFactory;

    }

    private RabbitConnectionFactoryBean getRabbitConnectionFactoryBean(
            RabbitProperties properties) throws Exception {
        PropertyMapper map = PropertyMapper.get();
        RabbitConnectionFactoryBean factory = new RabbitConnectionFactoryBean();
        map.from(properties::getRequestedHeartbeat).whenNonNull()
                .asInt(Duration::getSeconds).to(factory::setRequestedHeartbeat);
        RabbitProperties.Ssl ssl = properties.getSsl();
        if (ssl.isEnabled()) {
            factory.setUseSSL(true);
            map.from(ssl::getAlgorithm).whenNonNull().to(factory::setSslAlgorithm);
            map.from(ssl::getKeyStoreType).to(factory::setKeyStoreType);
            map.from(ssl::getKeyStore).to(factory::setKeyStore);
            map.from(ssl::getKeyStorePassword).to(factory::setKeyStorePassphrase);
            map.from(ssl::getTrustStoreType).to(factory::setTrustStoreType);
            map.from(ssl::getTrustStore).to(factory::setTrustStore);
            map.from(ssl::getTrustStorePassword).to(factory::setTrustStorePassphrase);
            map.from(ssl::isValidateServerCertificate).to((validate) -> factory
                    .setSkipServerCertificateValidation(!validate));
            map.from(ssl::getVerifyHostname)
                    .to(factory::setEnableHostnameVerification);
        }
        map.from(properties::getConnectionTimeout).whenNonNull()
                .asInt(Duration::toMillis).to(factory::setConnectionTimeout);
        factory.afterPropertiesSet();

        return factory;
    }

}

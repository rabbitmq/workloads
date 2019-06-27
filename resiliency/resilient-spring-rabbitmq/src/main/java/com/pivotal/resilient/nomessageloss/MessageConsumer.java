package com.pivotal.resilient.nomessageloss;

import com.pivotal.resilient.PlainMessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnExpression("${no-message-loss.enabled:true} and ${no-message-loss.consumer.enabled:true}")
public class MessageConsumer {
    private Logger logger = LoggerFactory.getLogger(MessageConsumer.class);

    @Autowired
    private NoMessageLossConfiguration configuration;

    @Bean
    public SimpleMessageListenerContainer synchronousConsumer(@Qualifier("no-message-loss-queue") Queue queue,
                                                                 @Qualifier("consumer") ConnectionFactory connectionFactory) {
        logger.info("Creating synchronous consumer on {} ...", queue.getName());

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(queue.getName());
        container.setMessageListener(new PlainMessageListener("consumer"));

        // it set to true, it will fail nad not recover if we get access refused
        container.setPossibleAuthenticationFailureFatal(configuration.properties.possibleAuthenticationFailureFatal);

        // this is key in order to survive should the hosting queue node disappeared.
        // without this flag=false, the container will simply give up after 3 attempts
        container.setMissingQueuesFatal(configuration.properties.missingQueuesFatal);

        return container;
    }
}

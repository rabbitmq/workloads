package com.pivotal.resilient.nomessageloss;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix= "no-message-loss",  ignoreUnknownFields = true)
@Getter
@Setter
@NoArgsConstructor
public class NoMessageLossProperties {
    boolean enabled;

    String queueName = "no-message-loss-q";
    String exchangeName = "no-message-loss-e";
    String routingKey = queueName;
    boolean possibleAuthenticationFailureFatal = false;
    boolean missingQueuesFatal = false;

    SynchronousProducer synchronousProducer = new SynchronousProducer();

    @Getter
    @Setter
    @NoArgsConstructor
    class SynchronousProducer {
        boolean enabled;

        int confirmTimeoutMs = 1000;

    }
}

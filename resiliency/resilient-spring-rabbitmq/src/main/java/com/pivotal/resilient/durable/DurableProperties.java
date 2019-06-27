package com.pivotal.resilient.durable;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix= "durable",  ignoreUnknownFields = true)
@Getter
@Setter
@NoArgsConstructor
public class DurableProperties {
    boolean enabled;

    String queueName = "durable-q";
    String exchangeName = "durable-e";
    String routingKey = queueName;
    boolean possibleAuthenticationFailureFatal = false;
    boolean missingQueuesFatal = false;


}

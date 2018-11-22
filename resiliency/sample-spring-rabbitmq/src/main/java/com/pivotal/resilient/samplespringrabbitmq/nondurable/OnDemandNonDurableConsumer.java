package com.pivotal.resilient.samplespringrabbitmq.nondurable;

import com.pivotal.resilient.samplespringrabbitmq.ChaosMonkey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/non-durable")
@ConditionalOnProperty(prefix = "non-durable.consumer", value = "enabled", matchIfMissing = true)
public class OnDemandNonDurableConsumer {

    private Logger logger = LoggerFactory.getLogger(OnDemandNonDurableConsumer.class);

    private List<AbstractMessageListenerContainer> containerListeners = new ArrayList<>();

    private RabbitListenerEndpointRegistrar endpointRegistrar;

    @Value("${non-durable-consumer.queue:non-durable-q}") String queueName;

    private @Autowired
    @Qualifier("templateForNonDurableProducer")
    RabbitTemplate template;

    @PutMapping("/startListener")
    public void startListeningOn(@RequestParam("id") String listenerId) {
        startListener(listenerId, chaosMonkey.newListener(listenerId));
    }
    @PutMapping("/stopListener")
    public void stopListeningOn(@RequestParam("id") String listenerId) {
        stopListener(listenerId);
    }

    @Autowired
    private ChaosMonkey chaosMonkey;

    @PutMapping("/sendMessage")
    public void sendChaosMessage(@RequestParam ChaosMonkey.ChaosMessageType type) {
        template.send(chaosMonkey.newMessage(type));
    }
    @GetMapping("/messageTypes")
    public String[] listMessageTypes() {
        return ChaosMonkey.ChaosMessageType.names();
    }

    @Bean
    public RabbitListenerConfigurer rabbitListenerConfigurerForNonDurableConsumer(RabbitAdmin admin) {
        return (registrar) -> {
            endpointRegistrar = registrar;
        };
    }

    private String startListener(String id, MessageListener listener) {
        MessageListenerContainer listenerContainer = endpointRegistrar.getEndpointRegistry().getListenerContainer(id);
        if (listenerContainer != null) {
            return id;
        }

        SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();

        endpoint.setId(id);
        endpoint.setQueueNames(queueName);
        endpoint.setMessageListener(listener);

        endpointRegistrar.registerEndpoint(endpoint);


        endpointRegistrar.getEndpointRegistry().getListenerContainer(id).start();

        return id;
    }
    private void stopListener(String id) {
        endpointRegistrar.getEndpointRegistry().getListenerContainer(id).stop();
        endpointRegistrar.getEndpointRegistry().unregisterListenerContainer(id);
    }
}

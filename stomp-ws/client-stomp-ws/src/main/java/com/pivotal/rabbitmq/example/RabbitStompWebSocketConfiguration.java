package com.pivotal.rabbitmq.example;

import com.pivotal.rabbitmq.stompws.RabbitStompWsClient;
import com.pivotal.rabbitmq.stompws.WebSocketConnectionProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class RabbitStompWebSocketConfiguration {

    @Autowired TaskScheduler taskScheduler;
    @Value("${spring.rabbitmq.host:localhost}") String hostname;
    @Value("${spring.rabbitmq.port:15674}") int port;
    @Value("${spring.rabbitmq.username:guest}") String username;
    @Value("${spring.rabbitmq.password:guest}") String password;

    @Bean(destroyMethod = "stop")
    public RabbitStompWsClient stompForRest(WebSocketConnectionProvider provider) {
        return provider.newClient("stompForRest");
    }

    @Bean(destroyMethod = "stop")
    public RabbitStompWsClient stompForScheduledTask(WebSocketConnectionProvider provider) {
        return provider.newClient("stompForScheduledTask");
    }
    @Bean
    public WebSocketConnectionProvider webSocketConnectionProvider() {
        return WebSocketConnectionProvider.builder()
                .connectTo(hostname, port)
                .withStompHeader(headerWithCredentials())
                .withTaskScheduler(taskScheduler)
                .build();
    }
    StompHeaders headerWithCredentials() {
        StompHeaders header = new StompHeaders();
        header.setLogin(username);
        header.setPasscode(password);
        return header;
    }
}

package com.pivotal.rabbitmq.stompws;

import org.springframework.messaging.simp.stomp.StompHeaders;

import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

public interface RabbitStompWsClient {
    CompletionStage<?> whenReady();
    <T> CompletionStage<?> send(StompHeaders headers, T payload);
    <T> CompletionStage<?> send(String destination, T payload);
    <T> CompletionStage<Subscription> subscribe(String destination, Class<T> type, BiConsumer<StompHeaders,T> consumer);

    void stop();

    interface Subscription {
        void cancel();
    }
}

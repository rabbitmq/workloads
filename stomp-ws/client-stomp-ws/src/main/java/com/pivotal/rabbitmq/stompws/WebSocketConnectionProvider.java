package com.pivotal.rabbitmq.stompws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class WebSocketConnectionProvider {

    private TaskScheduler taskScheduler;
    private String url;
    private WebSocketHttpHeaders handshakeHeaders;
    private StompHeaders connectHeader;

    WebSocketConnectionProvider(String hostname, int port,
                                       WebSocketHttpHeaders handshakeHeaders,
                                       StompHeaders connectHeader,
                                       TaskScheduler taskScheduler) {
        this.handshakeHeaders = handshakeHeaders;
        this.connectHeader = connectHeader;
        this.url = String.format("ws://%s:%d/ws", hostname, port);
        this.taskScheduler = taskScheduler;
    }
    public static Builder builder() {
        return new Builder();
    }
    public static class Builder {
        String hostname = "localhost";
        int port = 15674;
        StompHeaders stompHeaders;
        WebSocketHttpHeaders webSocketHttpHeaders;
        private TaskScheduler taskScheduler;

        public Builder connectTo(String hostname, int port) {
            this.hostname = hostname;
            this.port = port;
            return this;
        }
        public Builder withStompHeader(StompHeaders stompHeader) {
            this.stompHeaders = stompHeader;
            return this;
        }

        public Builder withWebSocketHttpHeaders(WebSocketHttpHeaders webSocketHttpHeaders) {
            this.webSocketHttpHeaders = webSocketHttpHeaders;
            return this;
        }
        public Builder withTaskScheduler(TaskScheduler taskScheduler) {
            this.taskScheduler = taskScheduler;
            return this;
        }
        public WebSocketConnectionProvider build() {
            return new WebSocketConnectionProvider(hostname, port,
                    Optional.ofNullable(webSocketHttpHeaders).orElse(new WebSocketHttpHeaders()),
                    Optional.ofNullable(stompHeaders).orElse(new StompHeaders()),
                    taskScheduler);
        }
    }

    public DefaultRabbitStompWsClient newClient(String name) {
        WebSocketClient webSocketClient = new StandardWebSocketClient();
        WebSocketStompClient stompClient = new WebSocketStompClient(webSocketClient);
        stompClient.setMessageConverter(new StringMessageConverter());
        stompClient.setTaskScheduler(taskScheduler);

        return new DefaultRabbitStompWsClient(name, newSessionSupplierFor(name, "sender", stompClient),
                newSessionSupplierFor(name, "subscriber", stompClient));
    }

    private StompSessionSupplier newSessionSupplierFor(String name, String type, WebSocketStompClient stompClient) {
        return new StompSessionSupplier(String.format("%s:%s", name, type), stompClient, handshakeHeaders, connectHeader,
                url);
    }
    class StompSessionSupplier implements Supplier<CompletionStage<StompSession>> {
        Logger log;

        WebSocketStompClient stompClient;
        String url;
        StompSessionHandler sessionHandler;
        AtomicReference<CompletionStage<StompSession>> currentSession = new AtomicReference<>();
        WebSocketHttpHeaders handshakeHeaders;
        StompHeaders connectHeader;

        public StompSessionSupplier(String name, WebSocketStompClient stompClient,
                                    WebSocketHttpHeaders handshakeHeaders,
                                    StompHeaders connectHeader, String url) {
            this.stompClient = stompClient;
            this.handshakeHeaders = handshakeHeaders;
            this.connectHeader = connectHeader;
            this.url = url;
            this.log = LoggerFactory.getLogger(StompSessionSupplier.class.getName() + "." + name);
            sessionHandler = new DefaultStompSessionHandler(log, throwable -> currentSession.set(null));
        }

        @Override
        public CompletionStage<StompSession> get() {
            return currentSession.updateAndGet(cur -> {
                if (cur != null) return cur;
                log.info("Stomp connecting ...");
                ListenableFuture<StompSession> listener = stompClient
                        .connect(url, handshakeHeaders, connectHeader, sessionHandler);
                listener.addCallback(stompSession -> stompSession.setAutoReceipt(true),
                        throwable -> log.error("Failed to connect", throwable));
                return listener.completable();

            });
        }
    }

    public static class DefaultStompSessionHandler extends StompSessionHandlerAdapter {

        private Logger log;
        private Consumer<Throwable> onError;

        public DefaultStompSessionHandler(Logger log, Consumer<Throwable> onError) {
            this.log = log;
            this.onError = onError;
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
            log.error("Stomp exception {}", exception);

        }

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            log.info("Stomp connected");

        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            log.error("Stomp connection failure", exception);
            onError.accept(exception);
        }

    }
}

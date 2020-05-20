package com.pivotal.rabbitmq.stompws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class DefaultRabbitStompWsClient implements RabbitStompWsClient {

    private String name;
    private Supplier<CompletionStage<StompSession>> senderSession;
    private SubscriptionManager subscriptionManager;
    private AtomicBoolean startSubscriptionManager = new AtomicBoolean();

    DefaultRabbitStompWsClient(String name, Supplier<CompletionStage<StompSession>> senderSession,
                               Supplier<CompletionStage<StompSession>> subscriberSession) {
        this.name = name;
        this.senderSession = senderSession;
        this.subscriptionManager = new SubscriptionManager(name, Executors.newSingleThreadExecutor(),
                subscriberSession);
    }

    @Override
    public void stop() {
        subscriptionManager.stop();
    }

    @Override
    public CompletionStage<?> whenReady() {
        return senderSession.get();
    }

    @Override
    public <T> CompletionStage<?> send(String destination, T payload) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        return send(headers, payload);
    }
    @Override
    public <T> CompletionStage<?> send(StompHeaders headers, T payload) {
        CompletableFuture<Boolean> sent = new CompletableFuture<>();
        senderSession.get().whenComplete((stompSession, throwable) -> {
            if (throwable != null) { sent.completeExceptionally(throwable); return;}
            try {
                StompSession.Receiptable receipt = stompSession.send(headers, payload);
                receipt.addReceiptLostTask(()->sent.completeExceptionally(undelivered));
                receipt.addReceiptTask(()->sent.complete(true));
            }catch(Throwable e) {
                sent.completeExceptionally(e);
            }
        });
        return sent;
    }

    @Override
    public <T> CompletionStage<RabbitStompWsClient.Subscription> subscribe(String destination, Class<T> type, BiConsumer<StompHeaders,T> consumer) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        return subscribe(headers, type, consumer);
    }

    public <T> CompletionStage<RabbitStompWsClient.Subscription> subscribe(StompHeaders headers, Class<T> type,
                                                                    BiConsumer<StompHeaders,T> consumer) {
        if (startSubscriptionManager.compareAndSet(false, true)) {
            subscriptionManager.start();
        }
        return subscriptionManager.addSubscription(headers, type, consumer);
    }

    Exception undelivered = new Exception("Undelivered");

    class SubscriptionManager implements Runnable {
        private Logger logger;
        private ExecutorService executorService;
        private Supplier<CompletionStage<StompSession>> sessionSupplier;
        private BlockingQueue<StompSubscriber<?>> subscriptionRequest = new LinkedBlockingDeque<>();
        private List<StompSubscriber<?>> subscribers = Collections.synchronizedList(new ArrayList<>());
        private AtomicBoolean shouldRun = new AtomicBoolean();

        public SubscriptionManager(String name, ExecutorService executorService, Supplier<CompletionStage<StompSession>> sessionSupplier) {
            this.executorService = executorService;
            this.sessionSupplier = sessionSupplier;
            logger = LoggerFactory.getLogger(SubscriptionManager.class.getName()+"."+name);
        }

        private Exception failToAddSubscription = new Exception("Failed to add subscription");

        public <T> CompletionStage<RabbitStompWsClient.Subscription> addSubscription(StompHeaders headers, Class<T> type,
                                                                 BiConsumer<StompHeaders, T> consumer) {
            StompSubscriber<T> subscription = new StompSubscriber<>(headers, type, consumer);
            logger.info("Adding subscription {}", subscription);
            if (!subscriptionRequest.add(subscription)) {
                subscription.completionStage().completeExceptionally(failToAddSubscription);
            }
            return subscription.completionStage();
        }

        private AtomicReference<Future<?>> subscriber = new AtomicReference<>();

        void start() {
            if (shouldRun.compareAndSet(false, true)) {
                subscriber.getAndUpdate(future -> {
                    if (future != null) return future;
                    else return executorService.submit(this);
                });
            }
        }
        void stop() {
            if (shouldRun.compareAndSet(true, false)) {
                subscriber.getAndUpdate(future -> {
                    if (future != null) future.cancel(true);
                    return null;
                });
                if (current != null) current.disconnect();
            }
        }
        private StompSession current;

        @Override
        public void run() {
            while(shouldRun.get()) {
                try {
                    StompSubscriber<?> subscriber = subscriptionRequest.poll(1, TimeUnit.SECONDS);
                    if (subscriber != null) subscribers.add(subscriber);

                    sessionSupplier.get().thenAccept(stompSession -> {
                        if (current == null || !current.isConnected()) {
                            logger.info("(Re)Subscribing {} subscriptions: {}", subscribers.size(), subscribers);
                            subscribers.forEach(stompSubscriber -> stompSubscriber.subscribe(stompSession));
                        }else if (subscriber != null) {
                            logger.info("Subscribing {}", subscriber);
                            subscriber.subscribe(stompSession);
                        }
                        current = stompSession;
                    }).exceptionally(throwable -> {
                        current = null;
                        return null;
                    });
                    subscribers.removeIf(StompSubscriber::isCancelled);

                } catch (InterruptedException e) {

                }
            }
        }

    }
    interface Subscription {
        void cancel();
    }

    class StompSubscriber<T> implements StompFrameHandler, RabbitStompWsClient.Subscription {

        private StompHeaders headers;
        private Class<T> type;
        private BiConsumer<StompHeaders,T> consumer;
        private AtomicReference<StompSession.Subscription> stompSubscription = new AtomicReference<>();
        private CompletableFuture<RabbitStompWsClient.Subscription> completableFuture;

        public StompSubscriber(StompHeaders headers, Class<T> type, BiConsumer<StompHeaders, T> consumer) {
            assert headers != null && type != null && consumer != null;
            this.headers = headers;
            this.type = type;
            this.consumer = consumer;
            completableFuture = new CompletableFuture<>();
        }
        @Override
        public String toString() {
            return headers.getDestination();
        }
        public CompletableFuture<RabbitStompWsClient.Subscription> completionStage() {
            return completableFuture;
        }

        void subscribe(StompSession stompSession) {
            try {
                stompSubscription.getAndUpdate(subscription -> {
                    if (subscription != null) {
                        try {
                            subscription.unsubscribe();
                        }catch(Throwable t) { // ignore
                        }
                    }
                    return stompSession.subscribe(headers, this);
                });
                completableFuture.complete(this);
            }catch(Throwable t) {
                stompSubscription.set(null);
                completableFuture.completeExceptionally(t);
            }
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return type;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            consumer.accept(headers, type.cast(payload));
        }

        private AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                stompSubscription.getAndUpdate(subscription -> {
                    if (subscription == null) return null;
                    subscription.unsubscribe();
                    return null;
                });
            }
        }
        public boolean isCancelled() {
            return cancelled.get();
        }

    }
}

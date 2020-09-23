package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.integration.amqp.support.NackedAmqpMessageException;
import org.springframework.integration.amqp.support.ReturnedAmqpMessageException;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@EnableBinding(TradeService.MessagingBridge.class)
@EnableScheduling
public class TradeService {
    private final Logger logger = LoggerFactory.getLogger(TradeService.class);

    interface MessagingBridge {

        String OUTBOUND_TRADE_REQUESTS = "outboundTradeRequests";

        @Output(OUTBOUND_TRADE_REQUESTS)
        MessageChannel outboundTradeRequests();
    }

    @Autowired private MessagingBridge messagingBridge;

    public TradeService() {
        logger.info("Created");
    }

    private volatile long attemptCount = 0;
    private volatile long sentCount = 0;

    @Value("${maxInFlight:3}") int maxInFlight;
    @Value("${maxSendAttempt:3}") int maxSendAttempt;

    public CompletableFuture<Trade> send(Trade trade) {

        if (pendingTrades.size() >= maxInFlight) {
            throw new IllegalArgumentException("Exceeded maxInFlight");
        }
        logger.info("[attempts:{},sent:{}] Requesting {}", attemptCount, sentCount, trade);

        // send() always return true so we cannot use it to determine a successful send
        MessageTracker tracker = MessageTracker.instance(trade);
        pendingTrades.put(trade.getId(), send(tracker));

        attemptCount++;
        return tracker.task;
    }

    @Scheduled(fixedDelayString = "${delayBetweenResend:2000}")
    public void resend() {
        try {
            Set<Long> tradesToRemove = completeExceptionallyTradesHaveExceededMaxRetryAttempts();
            tradesToRemove.addAll(completeDeliveredTrades());
            removeTradesWhichHaveCompleted(tradesToRemove);
            resendFailedTrades();
        }catch(RuntimeException e) {
            logger.error("Error occurred on resend schedule task due to {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private void resendFailedTrades() {
        if (pendingTrades.isEmpty()) return;

        pendingTrades.values().stream()
                .filter(MessageTracker::wasUnsuccessfullyDelivered)
                .forEach(this::send);
    }
    private Set<Long> completeDeliveredTrades() {
        if (pendingTrades.isEmpty()) return Collections.emptySet();

        return pendingTrades.values().stream()
                .filter(MessageTracker::wasSuccessfullyDelivered)
                .map(t -> t.trade.getId())
                .collect(Collectors.toSet());
    }
    private Set<Long> completeExceptionallyTradesHaveExceededMaxRetryAttempts() {
        if (pendingTrades.isEmpty()) return Collections.emptySet();

        return pendingTrades.values().stream()
                .filter(t -> t.wasUnsuccessfullyDelivered() && t.sentCount > maxSendAttempt)
                .peek(t -> {
                    logger.info("trade {} failed after exceeding maxAttempts", t.trade.getId());
                    try {
                        t.task.completeExceptionally(new IllegalStateException("Exceeded maxInFlight"));
                    }catch(RuntimeException e) {
                        logger.error("Error occurred while exceptionally completing trade {} due to {}",
                                t.trade.getId(), e.getMessage());
                    }
                })
                .map(t -> t.trade.getId())
                .collect(Collectors.toSet());
    }
    private void removeTradesWhichHaveCompleted( Set<Long> tradesToRemove ) {
        if (!tradesToRemove.isEmpty()) {
            logger.info("Removing {} completed trades", tradesToRemove.size());
            pendingTrades.keySet().removeAll(tradesToRemove);
        }
    }
    private MessageTracker send(MessageTracker tracker) {
        Trade trade = tracker.trade;

        tracker.sending();
        logger.info("Sending trade {} with correlation {} . Attempt #{}", trade.id,
                tracker.getCorrelationId(), tracker.sentCount+1);

        messagingBridge.outboundTradeRequests().send(
                MessageBuilder.withPayload(trade)
                        .setHeader("correlationId", tracker.getCorrelationId())
                        .setHeader("tradeId", trade.id)
                        .setHeader("resend", true)
                        .setHeader("account", trade.accountId).build());

        logger.info("Sent trade {}", trade.id);

        return tracker.sent();
    }
    private ConcurrentMap<Long, MessageTracker> pendingTrades = new ConcurrentHashMap<>();

    @ServiceActivator(inputChannel = "trades.errors")
    public void error(Message<MessagingException> message) {
        logger.error("Received error {}", message);

        if (message.getPayload() instanceof ReturnedAmqpMessageException) {
            try {
                returnedMessage((ReturnedAmqpMessageException) message.getPayload());
            }catch(RuntimeException e) {
                System.err.println("problem");
                e.printStackTrace();
            }
        }else if (message.getPayload() instanceof NackedAmqpMessageException) {
            try {
                nackedMessage((NackedAmqpMessageException) message.getPayload());
            }catch(RuntimeException e) {
                System.err.println("problem");
                e.printStackTrace();
            }
        }else {
            logger.warn("Unknown error {}:{}", message.getPayload().getClass().getName(),
                    message.getPayload().getMessage());
            return;
        }
    }
    private Long returnedMessage(ReturnedAmqpMessageException e) {
        org.springframework.amqp.core.Message amqpMessage = e.getAmqpMessage();
        Long tradeId = (Long)amqpMessage.getMessageProperties().getHeaders().get("tradeId");
        String id = (String)amqpMessage.getMessageProperties().getHeaders().get("correlationId");
        logger.error("Returned Trade {}", tradeId);
        pendingTrades.get(tradeId).returned(id);
        return tradeId;
    }
    private Long nackedMessage(NackedAmqpMessageException e) {
        Message<?> amqpMessage = e.getFailedMessage();
        Long tradeId = (Long)amqpMessage.getHeaders().get("tradeId");
        String id = (String)amqpMessage.getHeaders().get("correlationId");
        logger.error("Nacked Trade {}", tradeId);
        pendingTrades.get(tradeId).nacked(id);
        return tradeId;
    }


    @ServiceActivator(inputChannel = "trades.confirm")
    public void handlePublishConfirmedTrades(Message<Trade> message) {
        try {
            Trade trade = message.getPayload();
            MessageTracker tracker = pendingTrades.get(trade.getId());

            String id = message.getHeaders().get("correlationId", String.class);
            if (tracker.delivered(id)) {
                logger.info("Received publish confirm w/id {} => {}", id, trade);
                try {
                    tracker.task.complete(trade);
                } catch (RuntimeException e) {
                    logger.error("Error occurred completing trade {}", trade.getId());
                } finally {
                    tracker.deliveredCount++;
                }
            } else {
                logger.warn("Received late publish confirm w/id {} => {}. Current state {}", id, trade,
                        tracker.state.get());
            }
        }catch(Exception e) {
            System.err.println("onConfirm");
            e.printStackTrace();
        }
    }

    static class MessageState {
        enum State { Sending, Delivered, Nacked, Returned }
        String id;
        State state;
        String toString;
        private MessageState(String id, State state) {
            if (id == null) throw new NullPointerException();
            if (state == null) throw new NullPointerException();
            this.id = id;
            this.state = state;
            this.toString = String.format("%s:%s", id, state.name());
        }

        @Override
        public String toString() {
            return toString;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MessageState that = (MessageState) o;
            return id.equals(that.id) &&
                    state == that.state;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, state);
        }

        static MessageState delivered(String id) {
            return new MessageState(id, State.Delivered);
        }
        static MessageState nacked(String id) {
            return new MessageState(id, State.Nacked);
        }
        static MessageState returned(String id) {
            return new MessageState(id, State.Returned);
        }
        static MessageState sending(String id) {
            return new MessageState(id, State.Sending);
        }
        public boolean wasSuccessfullyDelivered() {
            return state.equals(State.Delivered);
        }
        public boolean wasUnsuccessfullyDelivered() {
            return state.equals(State.Nacked) || state.equals(State.Returned);
        }
        public boolean stillWaitingForResponse() {
            return state.equals(State.Sending);
        }
    }

    static class MessageTracker {
        Trade trade;
        long sentAt;
        int sentCount;
        int deliveredCount;
        CompletableFuture<Trade> task;
        AtomicReference<MessageState> state = new AtomicReference<>();

        public static MessageTracker instance(Trade trade) {
            MessageTracker tracker = new MessageTracker();
            tracker.trade = trade;
            tracker.task = new CompletableFuture<>();
            return tracker;
        }
        public String getCorrelationId() {
            MessageState s = state.get();
            return s != null ? s.id : null;
        }
        public boolean delivered(String id) {
            return state.accumulateAndGet(MessageState.delivered(id),
                    (current, update) -> {
                        MessageState ret = (current.equals(MessageState.sending(id))) ? update : current;
                        System.out.println("delivered current:" + current + " => " + ret);
                        return ret;
                    })
                    .wasSuccessfullyDelivered();
        }
        public boolean nacked(String id) {
            return state.accumulateAndGet(MessageState.nacked(id),
                    (current, update) -> {
                        MessageState ret = (current.equals(MessageState.sending(id))) ? update : current;
                        System.err.println("nacked current:" + current + " => " + ret);
                        return ret;
                    })
                    .wasUnsuccessfullyDelivered();
        }
        public boolean returned(String id) {
            return state.accumulateAndGet(MessageState.returned(id),
                    (current, update) -> {
                        MessageState ret = (current.equals(MessageState.sending(id))) ? update : current;
                        System.err.println("returned current:" + current + " => " + ret);
                        return ret;
                    })
                    .wasUnsuccessfullyDelivered();
        }

        public MessageTracker sending() {
            state.set(MessageState.sending(String.valueOf(System.currentTimeMillis())));
            return this;
        }
        public MessageTracker sent() {
            sentAt = System.currentTimeMillis();
            sentCount++;
            return this;
        }

        public boolean wasSuccessfullyDelivered() {
            return state.get().wasSuccessfullyDelivered();
        }
        public boolean wasUnsuccessfullyDelivered() {
            return state.get().wasUnsuccessfullyDelivered();
        }

    }

}

package com.pivotal.resilient;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeoutException;

public class AMQPConnectionProviderImpl implements AMQPConnectionProvider, RecoveryListener, ShutdownListener {
    private String name;
    private ConnectionFactory connectionFactory;
    private TaskScheduler taskScheduler;
    private List<Address> amqpAddresses;
    private Map<String, AMQPConnectionRequest> connectionRequests;
    private NoOpAMQPConnectionRequester noOpAMQPConnectionRequester = new NoOpAMQPConnectionRequester();

    private Logger logger;
    private static final Map<Class<? extends AMQPResource>, AMQPResourceDeclaration> amqpDeclarations = new HashMap<>();

    static {
        amqpDeclarations.put(QueueDescriptor.class, new AMQPQueueDeclaration());
        amqpDeclarations.put(ExchangeDescriptor.class, new AMQPExchangeDeclaration());
        amqpDeclarations.put(BindingDescriptor.class, new AMQPBindingDeclaration());
    }

    public AMQPConnectionProviderImpl(String name, ConnectionFactory connectionFactory, List<Address> amqpAddresses, TaskScheduler taskScheduler) {
        this.name = name;
        this.connectionFactory = connectionFactory;
        this.taskScheduler = taskScheduler;
        this.amqpAddresses = amqpAddresses;
        this.connectionRequests = Collections.synchronizedMap(new HashMap<>());
        this.logger = LoggerFactory.getLogger(AMQPConnectionProviderImpl.class.getName() + "." + name);
    }

    @Override
    public void manageConnectionFor(String requesterName, List<AMQPResource> resources, AMQPConnectionRequester requester) {
        if (connectionRequests.putIfAbsent(requesterName, new AMQPConnectionRequest(requesterName, resources, requester)) != null) {
            throw new IllegalStateException(String.format("%s: Duplicate AMQPConnectionRequester. %s is already being managed", name, requesterName));
        }
        logger.info("Managing connection for {}", requesterName);
    }

    @Override
    public void manageConnectionFor(String requesterName, List<AMQPResource> resources) {
        if (connectionRequests.putIfAbsent(requesterName, new AMQPConnectionRequest(requesterName, resources, noOpAMQPConnectionRequester)) != null) {
            throw new IllegalStateException(String.format("%s: Duplicate AMQPConnectionRequester. %s is already being managed", name, requesterName));
        }
        logger.info("Managing connection to declare the following resources {}", resources);
    }

    @Override
    public void unmanageConnectionsFor(String requesterName) {
        connectionRequests.remove(requesterName);
        logger.info("Stop managing connections for {}", requesterName);
    }

    private void passConnectionToRequesters(final Connection connection) {

        connectionRequests.forEach((requesterName, request) -> {
            if (request.isHandled()) {
                return;
            }
            try {
                passConnectionToRequester(request, connection);
                request.handled(connection);
                logger.info("AMQPRequester {} resolved!", requesterName);
            } catch (RuntimeException e) {
                request.failedToHandle(connection);
                logger.info("AMQPRequester {} unresolved yet. Due to {}", requesterName, e.getMessage());
            }

        });
    }
    protected void executeOnNewChannel(ChannelOperation operation) {
        Channel channel;
        try {
            channel = connection.createChannel();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            operation.executeOn(channel);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                channel.close();
            } catch (IOException | TimeoutException e) {

            }
        }
    }
    private void passConnectionToRequester(final AMQPConnectionRequest request, Connection connection) {

        logger.info("Declaring AMQP resources {} for requester {}", request.getRequiredResources(), request.getName());
        executeOnNewChannel((channel) -> declareResources(request, channel));

        logger.info("Making connection available to AMQP requester {}", request.getName());
        request.getRequester().connectionAvailable(connection);
    }


    private void declareResources(AMQPConnectionRequest request, final Channel channel) {
        request.getRequiredResources().forEach(resource -> {
            try {
                amqpDeclarations.get(resource.getClass()).declare(resource, channel);
            } catch (IOException e) {
                // why could it fail?
                // 1) unexpected connection dropped
                // 2) wrong permissions
                // 3) ??
                logger.warn("Failed to declare resources for {}. Due to {}", request.getName(), e.getCause().getMessage());
                throw new RuntimeException(e);
            } catch (IllegalStateException e) {
                // when we pass invalid configuration
                // tell requester that it has wrong configuration configuration but let the other services to carry on ?
                // or maybe this is a good reason to terminate the app
                logger.warn("Failed to declare resources for {}. Due to wrong configuration: {}", request.getName(), e.getMessage());
                throw e;
            }
        });
    }


    @Override
    public void shutdownCompleted(ShutdownSignalException e) {
        logger.error("{} RabbitMQ connection has been shutdown", name);
        reportConnectionLose();
    }

    private void reportConnectionLose() {
        connectionRequests.forEach((name, requester) -> {
            requester.getRequester().connectionLost();
        });
    }

    public void shutdown() {
        try {
            logger.info("Shutting down ...");
        } catch (Throwable e) {
            logger.error("An error occurred while shutting down {}", e.getMessage());

        } finally {
            logger.info("Shutdown down completed");
        }
    }

    private Connection connection;

    private void handleAMQPConnectionRequesters() {
        if (isAMQPConnectionNeeded()) {
            try {
                logger.info("Establishing AMQP connection using {}", amqpAddresses);
                connection = connectionFactory.newConnection(amqpAddresses, name);
                connection.addShutdownListener(this);
                connection.addBlockedListener((reason) -> {
                    logger.warn("Connection {} is blocked due to {} !", name, reason);
                }, () ->{
                    logger.warn("Connection {} is unblocked", name);
                });
                logger.info("Established AMQP connection with {}:{}  [heartbeat:{}]", connection.getAddress(),
                        connection.getPort(),
                        connection.getHeartbeat());
            } catch (final Exception e) {
                logger.error("Failed to establish AMQP connection", e);
                Collections.shuffle(amqpAddresses);
                return;
            }

        }

        try {
            passConnectionToRequesters(connection);
        } catch (final RuntimeException e) {
            logger.error("Failed to pass connection to AMQPConnectionRequester(s)", e);
        }
    }
    private boolean isAMQPConnectionNeeded() {
        return connection == null && !connectionRequests.isEmpty();
    }

    private ScheduledFuture<?> tryToConnect;


    public void start() {
        scheduleAMQPConnectionRequestHandler();
    }

    private void scheduleAMQPConnectionRequestHandler() {
        tryToConnect = taskScheduler.scheduleAtFixedRate(() -> handleAMQPConnectionRequesters(), Instant.now().plusMillis(shortDelayOnFirstAttempt()),
                Duration.ofMillis(10000));
    }

    private long shortDelayOnFirstAttempt() {
        return tryToConnect == null ? 500 : 10000;
    }

    @Override
    public void handleRecovery(Recoverable recoverable) {
        logger.info("handleRecovery {}", recoverable);

    }

    @Override
    public void handleRecoveryStarted(Recoverable recoverable) {
        logger.info("handleRecoveryStarted {}", recoverable);
        reportConnectionLose();
    }
}

class AMQPConnectionRequest {
    private String name;
    private AMQPConnectionRequester requester;
    private List<AMQPResource> requiredResources;
    private boolean isResolved;
    private Connection connection;

    public AMQPConnectionRequest(String name, List<AMQPResource> requiredResources, AMQPConnectionRequester requester) {
        this.name = name;
        this.requester = requester;
        this.requiredResources = requiredResources;
    }

    public String getName() {
        return name;
    }

    public List<AMQPResource> getRequiredResources() {
        return requiredResources;
    }

    public AMQPConnectionRequester getRequester() {
        return requester;
    }

    void handled(Connection connection) {
        this.connection = connection;
        this.isResolved = true;
    }

    void failedToHandle(Connection connection) {
        this.connection = connection;
        this.isResolved = false;
    }

    public boolean isHandled() {
        return isResolved && requester.isHealthy();
    }
}

interface AMQPResourceDeclaration<T extends AMQPResource> {
    void declare(T resource, Channel channel) throws IOException;
}

class AMQPQueueDeclaration implements AMQPResourceDeclaration<QueueDescriptor> {
    private Logger logger = LoggerFactory.getLogger(AMQPQueueDeclaration.class);

    @Override
    public void declare(QueueDescriptor queue, Channel channel) throws IOException {
        logger.info("Declaring queue {}", queue);
        channel.queueDeclare(queue.getName(), queue.isDurable(), queue.isExclusive(), false, null);
    }
}

class AMQPExchangeDeclaration implements AMQPResourceDeclaration<ExchangeDescriptor> {
    private Logger logger = LoggerFactory.getLogger(AMQPExchangeDeclaration.class);

    @Override
    public void declare(ExchangeDescriptor exchange, Channel channel) throws IOException {
        logger.info("Declaring exchange {}", exchange);
        channel.exchangeDeclare(exchange.getName(), exchange.getType(), exchange.isDurable());
    }
}

class AMQPBindingDeclaration implements AMQPResourceDeclaration<BindingDescriptor> {
    private Logger logger = LoggerFactory.getLogger(AMQPExchangeDeclaration.class);

    @Override
    public void declare(BindingDescriptor binding, Channel channel) throws IOException {
        logger.info("Declaring binding {}", binding);
        channel.queueBind(binding.getQueue().getName(), binding.getExchange().getName(), binding.getRoutingKey());
    }
}

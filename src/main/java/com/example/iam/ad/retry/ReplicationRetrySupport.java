package com.example.iam.ad.retry;

import com.example.iam.ad.exception.ObjectNotFoundException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Retry-with-backoff wrapper for inter-DC replication lag.
 *
 * <p>In dependency sequences (create user → add to group) the follow-up call
 * can land on a DC that hasn't replicated the new object yet, surfacing as
 * {@link ObjectNotFoundException}. Only that exception is retried —
 * permission errors, timeouts, and genuine failures propagate immediately.
 * Backoff: 500ms doubling to a max of 8s, 5 attempts total.</p>
 */
@Component
public class ReplicationRetrySupport {

    private static final Logger log = LoggerFactory.getLogger(ReplicationRetrySupport.class);

    private final RetryRegistry retryRegistry;

    public ReplicationRetrySupport() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(5)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        Duration.ofMillis(500), 2.0, Duration.ofSeconds(8)))
                .retryOnException(t -> t instanceof ObjectNotFoundException)
                .build();
        this.retryRegistry = RetryRegistry.of(config);
        this.retryRegistry.getEventPublisher().onEntryAdded(event ->
                event.getAddedEntry().getEventPublisher().onRetry(retryEvent ->
                        log.warn("Retrying operation {} (attempt {}) after replication-lag miss: {}",
                                retryEvent.getName(), retryEvent.getNumberOfRetryAttempts(),
                                retryEvent.getLastThrowable() != null
                                        ? retryEvent.getLastThrowable().getMessage() : "n/a")));
    }

    public <T> T execute(String operationName, Supplier<T> action) {
        Retry retry = retryRegistry.retry(operationName);
        return Retry.decorateSupplier(retry, action).get();
    }

    public void run(String operationName, Runnable action) {
        execute(operationName, () -> {
            action.run();
            return null;
        });
    }
}

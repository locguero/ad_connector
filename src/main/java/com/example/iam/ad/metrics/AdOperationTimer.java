package com.example.iam.ad.metrics;

import com.example.iam.ad.domain.AdDomain;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Times every service-level connector operation as the Micrometer timer
 * {@value #METRIC_NAME}, tagged with {@code domain}, {@code operation}
 * (createUser, searchGroups, ...) and {@code outcome} ({@code success} or the
 * thrown exception's simple class name — bounded, since callers only ever see
 * the typed {@code IamIntegrationException} hierarchy).
 *
 * <p>Each operation additionally emits one INFO completion log with the same
 * dimensions in the MDC ({@code ad_operation}, {@code ad_domain},
 * {@code ad_outcome}, {@code ad_duration_ms}), so JSON encoders surface the
 * duration as a first-class field for log-based dashboards.</p>
 *
 * <p>Durations cover the full operation as callers experience it, including
 * DN resolution, paging, and replication-lag retries. Falls back to
 * {@link Metrics#globalRegistry} when the host app defines no
 * {@link MeterRegistry} bean.</p>
 */
@Component
public class AdOperationTimer {

    public static final String METRIC_NAME = "ad.connector.operation";

    private static final Logger log = LoggerFactory.getLogger(AdOperationTimer.class);

    private final MeterRegistry meterRegistry;

    public AdOperationTimer(ObjectProvider<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry.getIfAvailable(() -> Metrics.globalRegistry);
    }

    public <T> T execute(AdDomain domain, String operation, Supplier<T> action) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            return action.get();
        } catch (RuntimeException e) {
            outcome = e.getClass().getSimpleName();
            throw e;
        } finally {
            long nanos = sample.stop(Timer.builder(METRIC_NAME)
                    .description("Duration of AD connector operations")
                    .tag("domain", domain.name())
                    .tag("operation", operation)
                    .tag("outcome", outcome)
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(meterRegistry));
            logCompletion(domain, operation, outcome, nanos);
        }
    }

    public void run(AdDomain domain, String operation, Runnable action) {
        execute(domain, operation, () -> {
            action.run();
            return null;
        });
    }

    /**
     * One INFO completion event per operation. The MDC entries become
     * first-class JSON fields under JSON encoders (e.g. LogstashEncoder
     * includes the MDC by default), independent of message formatting.
     */
    private void logCompletion(AdDomain domain, String operation, String outcome, long nanos) {
        String durationMs = String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
        MDC.put("ad_operation", operation);
        MDC.put("ad_domain", domain.name());
        MDC.put("ad_outcome", outcome);
        MDC.put("ad_duration_ms", durationMs);
        try {
            log.info("Operation {} in domain {} completed in {} ms (outcome={})",
                    operation, domain, durationMs, outcome);
        } finally {
            MDC.remove("ad_operation");
            MDC.remove("ad_domain");
            MDC.remove("ad_outcome");
            MDC.remove("ad_duration_ms");
        }
    }
}

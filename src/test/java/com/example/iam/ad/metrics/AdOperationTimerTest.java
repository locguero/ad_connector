package com.example.iam.ad.metrics;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.iam.ad.domain.AdDomain;
import com.example.iam.ad.exception.ObjectNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdOperationTimerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AdOperationTimer timer = new AdOperationTimer(providerOf(meterRegistry));

    @Test
    void successRecordsTimerWithDomainOperationAndOutcomeTags() {
        String result = timer.execute(AdDomain.QA_ENT, "getUser", () -> "ok");

        assertThat(result).isEqualTo("ok");
        Timer recorded = meterRegistry.get(AdOperationTimer.METRIC_NAME)
                .tag("domain", "QA_ENT")
                .tag("operation", "getUser")
                .tag("outcome", "success")
                .timer();
        assertThat(recorded.count()).isEqualTo(1);
    }

    @Test
    void exceptionIsRethrownAndRecordedAsOutcome() {
        assertThatThrownBy(() -> timer.execute(AdDomain.AD_ENT, "deleteUser", () -> {
            throw new ObjectNotFoundException("no such user");
        })).isInstanceOf(ObjectNotFoundException.class);

        Timer recorded = meterRegistry.get(AdOperationTimer.METRIC_NAME)
                .tag("domain", "AD_ENT")
                .tag("operation", "deleteUser")
                .tag("outcome", "ObjectNotFoundException")
                .timer();
        assertThat(recorded.count()).isEqualTo(1);
    }

    @Test
    void runOverloadRecordsRunnableWork() {
        timer.run(AdDomain.DEV_ENT, "moveObject", () -> { });

        Timer recorded = meterRegistry.get(AdOperationTimer.METRIC_NAME)
                .tag("domain", "DEV_ENT")
                .tag("operation", "moveObject")
                .tag("outcome", "success")
                .timer();
        assertThat(recorded.count()).isEqualTo(1);
    }

    @Test
    void completionLogCarriesDurationAndDimensionsAsMdcFields() {
        ch.qos.logback.classic.Logger timerLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AdOperationTimer.class);
        // Snapshot the MDC at append time, exactly as an async/JSON encoder would.
        ListAppender<ILoggingEvent> appender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        appender.start();
        timerLogger.addAppender(appender);
        try {
            timer.execute(AdDomain.QA_ENT, "getUser", () -> "ok");
        } finally {
            timerLogger.detachAppender(appender);
        }

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        Map<String, String> mdc = event.getMDCPropertyMap();
        assertThat(mdc)
                .containsEntry("ad_operation", "getUser")
                .containsEntry("ad_domain", "QA_ENT")
                .containsEntry("ad_outcome", "success")
                .containsKey("ad_duration_ms");
        assertThat(Double.parseDouble(mdc.get("ad_duration_ms"))).isNotNegative();
        // MDC must not leak into subsequent log statements on this thread.
        assertThat(MDC.get("ad_duration_ms")).isNull();
        assertThat(MDC.get("ad_operation")).isNull();
    }

    private static ObjectProvider<MeterRegistry> providerOf(MeterRegistry registry) {
        return new ObjectProvider<>() {
            @Override
            public MeterRegistry getObject() {
                return registry;
            }

            @Override
            public MeterRegistry getIfAvailable() {
                return registry;
            }
        };
    }
}

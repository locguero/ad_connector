package com.example.iam.ad.config;

import com.example.iam.ad.domain.AdDomain;
import com.example.iam.ad.group.GroupDeactivationMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

/**
 * Externalized configuration for the AD connector.
 *
 * <p>Bound from {@code ad-connector.*}. Secrets (bind password, truststore
 * password/path) are expected to arrive via environment variables or mounted
 * secret files referenced with {@code ${...}} placeholders in application.yml
 * — never hardcoded.</p>
 */
@ConfigurationProperties(prefix = "ad-connector")
public record AdConnectorProperties(
        Map<AdDomain, DomainConfig> domains,
        @DefaultValue("STRIP_MEMBERSHIP") GroupDeactivationMode defaultGroupDeactivationMode) {

    public record DomainConfig(
            String host,
            @DefaultValue("636") int port,
            /** false = LDAPS on the configured port; true = plain connect + STARTTLS upgrade (typically port 389). */
            @DefaultValue("false") boolean useStartTls,
            String bindDn,
            String bindPassword,
            String baseDn,
            /** Target OU for the MOVE_TO_QUARANTINE_OU group-deactivation strategy. Optional. */
            String quarantineOu,
            TruststoreConfig truststore,
            @DefaultValue PoolConfig pool) {
    }

    public record TruststoreConfig(
            /** Filesystem path to the JKS/PKCS12 truststore (e.g. a mounted secret). */
            String path,
            String password,
            @DefaultValue("PKCS12") String type) {
    }

    public record PoolConfig(
            @DefaultValue("2") int initialConnections,
            @DefaultValue("10") int maxConnections,
            @DefaultValue("60000") long healthCheckIntervalMillis,
            @DefaultValue("5000") int connectTimeoutMillis,
            @DefaultValue("10000") long responseTimeoutMillis) {
    }
}

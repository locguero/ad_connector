package com.example.iam.ad.health;

import com.example.iam.ad.connection.AdConnectionRegistry;
import com.example.iam.ad.domain.AdDomain;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.RootDSE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Actuator health for all configured AD domains: exposed under
 * {@code /actuator/health} as component {@code adConnectionPools}. Each
 * domain reports pool utilization plus a live RootDSE read; any domain down
 * marks the component DOWN while still listing the healthy ones.
 */
@Component("adConnectionPools")
public class AdConnectionPoolHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(AdConnectionPoolHealthIndicator.class);

    private final AdConnectionRegistry registry;

    public AdConnectionPoolHealthIndicator(AdConnectionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Health health() {
        boolean allUp = true;
        Map<String, Object> details = new LinkedHashMap<>();

        for (AdDomain domain : registry.configuredDomains()) {
            try {
                LDAPConnectionPool pool = registry.getPool(domain);
                RootDSE rootDse = pool.getRootDSE();
                Map<String, Object> domainDetails = new LinkedHashMap<>();
                domainDetails.put("status", "UP");
                domainDetails.put("availableConnections", pool.getCurrentAvailableConnections());
                domainDetails.put("maxConnections", pool.getMaximumAvailableConnections());
                if (rootDse != null) {
                    domainDetails.put("serverName", rootDse.getAttributeValue("dnsHostName"));
                }
                details.put(domain.name(), domainDetails);
            } catch (Exception e) {
                allUp = false;
                log.warn("Health check failed for AD domain {}: {}", domain, e.getMessage());
                details.put(domain.name(), Map.of(
                        "status", "DOWN",
                        "error", e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }

        return (allUp ? Health.up() : Health.down()).withDetails(details).build();
    }
}

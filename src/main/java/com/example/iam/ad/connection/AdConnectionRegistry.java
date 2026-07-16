package com.example.iam.ad.connection;

import com.example.iam.ad.config.AdConnectorProperties;
import com.example.iam.ad.config.AdConnectorProperties.DomainConfig;
import com.example.iam.ad.domain.AdDomain;
import com.example.iam.ad.exception.IamIntegrationException;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes operations to the right per-domain connection pool.
 *
 * <p>Pools are created lazily on first use so one unreachable domain never
 * blocks application startup; the health indicator surfaces per-domain
 * availability instead.</p>
 */
@Component
public class AdConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(AdConnectionRegistry.class);

    private final AdConnectorProperties properties;
    private final AdConnectionFactory factory;
    private final Map<AdDomain, LDAPConnectionPool> pools = new ConcurrentHashMap<>();

    public AdConnectionRegistry(AdConnectorProperties properties, AdConnectionFactory factory) {
        this.properties = properties;
        this.factory = factory;
    }

    public LDAPConnectionPool getPool(AdDomain domain) {
        return pools.computeIfAbsent(domain, d -> factory.createPool(d, getConfig(d)));
    }

    public DomainConfig getConfig(AdDomain domain) {
        DomainConfig cfg = properties.domains() == null ? null : properties.domains().get(domain);
        if (cfg == null) {
            throw new IamIntegrationException("No configuration present for AD domain " + domain);
        }
        return cfg;
    }

    public Set<AdDomain> configuredDomains() {
        return properties.domains() == null ? Set.of() : properties.domains().keySet();
    }

    @PreDestroy
    public void shutdown() {
        pools.forEach((domain, pool) -> {
            log.info("Closing LDAP connection pool for domain {}", domain);
            pool.close();
        });
        pools.clear();
    }
}

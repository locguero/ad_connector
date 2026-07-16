package com.example.iam.ad.connection;

import com.example.iam.ad.config.AdConnectorProperties.DomainConfig;
import com.example.iam.ad.config.AdConnectorProperties.TruststoreConfig;
import com.example.iam.ad.domain.AdDomain;
import com.example.iam.ad.exception.IamIntegrationException;
import com.example.iam.ad.exception.LdapExceptionMapper;
import com.unboundid.ldap.sdk.ExtendedResult;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionOptions;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SimpleBindRequest;
import com.unboundid.ldap.sdk.SingleServerSet;
import com.unboundid.ldap.sdk.StartTLSPostConnectProcessor;
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest;
import com.unboundid.util.ssl.SSLUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * Builds one secured {@link LDAPConnectionPool} per {@link AdDomain}.
 *
 * <p>Transport is always encrypted: either LDAPS (default, port 636) or a
 * plain connect immediately upgraded via STARTTLS. Trust is anchored in a
 * per-domain JKS/PKCS12 truststore loaded from an externalized path
 * (mounted secret) — there is deliberately no trust-all fallback.</p>
 */
@Component
public class AdConnectionFactory {

    private static final Logger log = LoggerFactory.getLogger(AdConnectionFactory.class);

    public LDAPConnectionPool createPool(AdDomain domain, DomainConfig cfg) {
        validate(domain, cfg);
        try {
            SSLUtil sslUtil = buildSslUtil(domain, cfg.truststore());
            LDAPConnectionOptions options = new LDAPConnectionOptions();
            options.setConnectTimeoutMillis(cfg.pool().connectTimeoutMillis());
            options.setResponseTimeoutMillis(cfg.pool().responseTimeoutMillis());
            SimpleBindRequest bind = new SimpleBindRequest(cfg.bindDn(), cfg.bindPassword());

            LDAPConnectionPool pool;
            if (cfg.useStartTls()) {
                pool = createStartTlsPool(cfg, sslUtil, options, bind);
            } else {
                SocketFactory socketFactory = sslUtil.createSSLSocketFactory();
                SingleServerSet serverSet =
                        new SingleServerSet(cfg.host(), cfg.port(), socketFactory, options);
                pool = new LDAPConnectionPool(serverSet, bind,
                        cfg.pool().initialConnections(), cfg.pool().maxConnections());
            }
            pool.setConnectionPoolName("ad-" + domain.name().toLowerCase());
            pool.setHealthCheckIntervalMillis(cfg.pool().healthCheckIntervalMillis());
            log.info("Created LDAP connection pool for domain {} (host={}, port={}, transport={}, maxConnections={})",
                    domain, cfg.host(), cfg.port(), cfg.useStartTls() ? "STARTTLS" : "LDAPS",
                    cfg.pool().maxConnections());
            return pool;
        } catch (LDAPException e) {
            throw LdapExceptionMapper.map("createPool domain=" + domain, e);
        } catch (GeneralSecurityException e) {
            throw new IamIntegrationException(
                    "TLS initialization failed for domain " + domain + ": " + e.getMessage(), e);
        }
    }

    private LDAPConnectionPool createStartTlsPool(DomainConfig cfg, SSLUtil sslUtil,
                                                  LDAPConnectionOptions options,
                                                  SimpleBindRequest bind)
            throws LDAPException, GeneralSecurityException {
        SSLContext sslContext = sslUtil.createSSLContext();
        LDAPConnection connection = new LDAPConnection(options, cfg.host(), cfg.port());
        try {
            ExtendedResult tlsResult =
                    connection.processExtendedOperation(new StartTLSExtendedRequest(sslContext));
            if (tlsResult.getResultCode() != ResultCode.SUCCESS) {
                throw new LDAPException(tlsResult.getResultCode(),
                        "STARTTLS negotiation rejected: " + tlsResult.getDiagnosticMessage());
            }
            connection.bind(bind);
        } catch (LDAPException e) {
            connection.close();
            throw e;
        }
        return new LDAPConnectionPool(connection,
                cfg.pool().initialConnections(), cfg.pool().maxConnections(),
                new StartTLSPostConnectProcessor(sslContext));
    }

    private SSLUtil buildSslUtil(AdDomain domain, TruststoreConfig truststore)
            throws GeneralSecurityException {
        try {
            KeyStore keyStore = KeyStore.getInstance(truststore.type());
            char[] password = truststore.password() != null
                    ? truststore.password().toCharArray() : null;
            try (InputStream in = Files.newInputStream(Path.of(truststore.path()))) {
                keyStore.load(in, password);
            }
            TrustManagerFactory tmf =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            log.debug("Loaded {} truststore for domain {} from {}", truststore.type(), domain,
                    truststore.path());
            return new SSLUtil(tmf.getTrustManagers());
        } catch (IOException e) {
            throw new IamIntegrationException("Unable to read truststore for domain " + domain
                    + " at " + truststore.path() + ": " + e.getMessage(), e);
        }
    }

    private void validate(AdDomain domain, DomainConfig cfg) {
        require(cfg.host(), domain, "host");
        require(cfg.bindDn(), domain, "bind-dn");
        require(cfg.bindPassword(), domain, "bind-password");
        require(cfg.baseDn(), domain, "base-dn");
        if (cfg.truststore() == null || isBlank(cfg.truststore().path())) {
            throw new IamIntegrationException(
                    "Missing truststore.path for domain " + domain
                            + " — encrypted transport with an explicit trust anchor is mandatory");
        }
    }

    private void require(String value, AdDomain domain, String property) {
        if (isBlank(value)) {
            throw new IamIntegrationException(
                    "Missing required property ad-connector.domains." + domain + "." + property);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

package com.example.iam.ad.domain;

/**
 * The three Active Directory forests/domains this connector can route to.
 * Request context carries one of these values; each domain gets its own
 * connection pool, credentials, and truststore.
 */
public enum AdDomain {
    QA_ENT,
    DEV_ENT,
    AD_ENT
}

package com.example.iam.ad.service;

import com.example.iam.ad.config.AdConnectorProperties.DomainConfig;
import com.example.iam.ad.connection.AdConnectionRegistry;
import com.example.iam.ad.domain.AdDomain;
import com.example.iam.ad.dto.AdObjectRef;
import com.example.iam.ad.dto.MoveObjectRequest;
import com.example.iam.ad.exception.LdapExceptionMapper;
import com.example.iam.ad.exception.ObjectNotFoundException;
import com.example.iam.ad.metrics.AdOperationTimer;
import com.example.iam.ad.search.DnResolver;
import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ModifyDNRequest;
import com.unboundid.ldap.sdk.SearchResultEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** OU verification and object relocation between OUs. */
@Service
public class AdOrgUnitService {

    private static final Logger log = LoggerFactory.getLogger(AdOrgUnitService.class);

    /** Users and groups alike may be moved; match any directory object. */
    private static final Filter ANY_OBJECT = Filter.createPresenceFilter("objectClass");

    private final AdConnectionRegistry registry;
    private final DnResolver dnResolver;
    private final AdOperationTimer operationTimer;

    public AdOrgUnitService(AdConnectionRegistry registry, DnResolver dnResolver,
                            AdOperationTimer operationTimer) {
        this.registry = registry;
        this.dnResolver = dnResolver;
        this.operationTimer = operationTimer;
    }

    /** True if the DN exists and is an organizationalUnit. */
    public boolean ouExists(AdDomain domain, String ouDn) {
        return operationTimer.execute(domain, "ouExists", () -> doOuExists(domain, ouDn));
    }

    private boolean doOuExists(AdDomain domain, String ouDn) {
        LDAPConnectionPool pool = registry.getPool(domain);
        try {
            SearchResultEntry entry = pool.getEntry(ouDn, "objectClass");
            boolean exists = entry != null
                    && entry.hasAttributeValue("objectClass", "organizationalUnit");
            log.debug("OU existence check in domain {}: dn={}, exists={}", domain, ouDn, exists);
            return exists;
        } catch (LDAPException e) {
            throw LdapExceptionMapper.map("ouExists dn=" + ouDn, e);
        }
    }

    /** Moves a user or group into another OU, preserving its RDN. */
    public void moveObject(MoveObjectRequest request) {
        operationTimer.run(request.domain(), "moveObject", () -> doMoveObject(request));
    }

    private void doMoveObject(MoveObjectRequest request) {
        LDAPConnectionPool pool = registry.getPool(request.domain());
        DomainConfig cfg = registry.getConfig(request.domain());

        if (!doOuExists(request.domain(), request.targetOu())) {
            throw new ObjectNotFoundException("Target OU does not exist in domain "
                    + request.domain() + ": " + request.targetOu());
        }
        String dn = dnResolver.resolveDn(pool, cfg.baseDn(), request.object(), ANY_OBJECT);
        try {
            String rdn = new DN(dn).getRDNString();
            pool.modifyDN(new ModifyDNRequest(dn, rdn, true, request.targetOu()));
            log.info("Moved object {} to OU {} in domain {} (previous dn={})",
                    request.object(), request.targetOu(), request.domain(), dn);
        } catch (LDAPException e) {
            throw LdapExceptionMapper.map("moveObject " + request.object(), e);
        }
    }
}

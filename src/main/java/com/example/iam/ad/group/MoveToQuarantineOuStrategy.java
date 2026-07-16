package com.example.iam.ad.group;

import com.example.iam.ad.config.AdConnectorProperties.DomainConfig;
import com.example.iam.ad.exception.IamIntegrationException;
import com.example.iam.ad.exception.LdapExceptionMapper;
import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ModifyDNRequest;
import com.unboundid.ldap.sdk.SearchResultEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Moves the group into the domain's configured quarantine OU (typically one
 * excluded from application scoping and GPO linking). Requires
 * {@code ad-connector.domains.<domain>.quarantine-ou} to be set.
 */
@Component
public class MoveToQuarantineOuStrategy implements GroupDeactivationStrategy {

    private static final Logger log = LoggerFactory.getLogger(MoveToQuarantineOuStrategy.class);

    @Override
    public GroupDeactivationMode mode() {
        return GroupDeactivationMode.MOVE_TO_QUARANTINE_OU;
    }

    @Override
    public void deactivate(LDAPConnectionPool pool, SearchResultEntry groupEntry,
                           DomainConfig config) {
        if (config.quarantineOu() == null || config.quarantineOu().isBlank()) {
            throw new IamIntegrationException(
                    "MOVE_TO_QUARANTINE_OU requires quarantine-ou to be configured for this domain");
        }
        try {
            String rdn = new DN(groupEntry.getDN()).getRDNString();
            pool.modifyDN(new ModifyDNRequest(groupEntry.getDN(), rdn, true, config.quarantineOu()));
            log.info("Moved group {} to quarantine OU {} as deactivation",
                    groupEntry.getDN(), config.quarantineOu());
        } catch (LDAPException e) {
            throw LdapExceptionMapper.map("moveToQuarantine group=" + groupEntry.getDN(), e);
        }
    }
}

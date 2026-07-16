package com.example.iam.ad.group;

import com.example.iam.ad.config.AdConnectorProperties.DomainConfig;
import com.example.iam.ad.exception.LdapExceptionMapper;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.unboundid.ldap.sdk.SearchResultEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default strategy: replace {@code member} with an empty value set, removing
 * every direct member in one modify (works regardless of member count or
 * ranged retrieval). The group object itself is preserved for audit history.
 */
@Component
public class StripMembershipStrategy implements GroupDeactivationStrategy {

    private static final Logger log = LoggerFactory.getLogger(StripMembershipStrategy.class);

    @Override
    public GroupDeactivationMode mode() {
        return GroupDeactivationMode.STRIP_MEMBERSHIP;
    }

    @Override
    public void deactivate(LDAPConnectionPool pool, SearchResultEntry groupEntry,
                           DomainConfig config) {
        try {
            pool.modify(groupEntry.getDN(),
                    new Modification(ModificationType.REPLACE, "member"));
            log.info("Stripped all members from group {} as deactivation", groupEntry.getDN());
        } catch (LDAPException e) {
            throw LdapExceptionMapper.map("stripMembership group=" + groupEntry.getDN(), e);
        }
    }
}

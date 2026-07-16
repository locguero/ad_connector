package com.example.iam.ad.group;

import com.example.iam.ad.config.AdConnectorProperties.DomainConfig;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.SearchResultEntry;

/**
 * Strategy for "deactivating" a group — AD has no native disabled state for
 * groups, so each implementation approximates it differently. New strategies
 * are added by registering another Spring bean; {@code AdGroupService}
 * discovers them by {@link #mode()}.
 */
public interface GroupDeactivationStrategy {

    GroupDeactivationMode mode();

    /**
     * @param groupEntry the resolved group entry, guaranteed to include
     *                   objectGUID and groupType
     */
    void deactivate(LDAPConnectionPool pool, SearchResultEntry groupEntry, DomainConfig config);
}

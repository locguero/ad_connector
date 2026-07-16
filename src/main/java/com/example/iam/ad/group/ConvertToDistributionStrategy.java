package com.example.iam.ad.group;

import com.example.iam.ad.config.AdConnectorProperties.DomainConfig;
import com.example.iam.ad.exception.LdapExceptionMapper;
import com.example.iam.ad.util.GroupTypeHelper;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.unboundid.ldap.sdk.SearchResultEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Clears the security bit on {@code groupType} so the group no longer confers
 * access, while membership stays intact for a potential reactivation.
 */
@Component
public class ConvertToDistributionStrategy implements GroupDeactivationStrategy {

    private static final Logger log = LoggerFactory.getLogger(ConvertToDistributionStrategy.class);

    @Override
    public GroupDeactivationMode mode() {
        return GroupDeactivationMode.CONVERT_TO_DISTRIBUTION;
    }

    @Override
    public void deactivate(LDAPConnectionPool pool, SearchResultEntry groupEntry,
                           DomainConfig config) {
        Integer groupType = groupEntry.getAttributeValueAsInteger("groupType");
        if (groupType == null || !GroupTypeHelper.isSecurityGroup(groupType)) {
            log.info("Group {} is already a distribution group; conversion is a no-op",
                    groupEntry.getDN());
            return;
        }
        int converted = GroupTypeHelper.toDistributionGroup(groupType);
        try {
            pool.modify(groupEntry.getDN(),
                    new Modification(ModificationType.REPLACE, "groupType",
                            Integer.toString(converted)));
            log.info("Converted group {} from security to distribution (groupType {} -> {})",
                    groupEntry.getDN(), groupType, converted);
        } catch (LDAPException e) {
            throw LdapExceptionMapper.map("convertToDistribution group=" + groupEntry.getDN(), e);
        }
    }
}

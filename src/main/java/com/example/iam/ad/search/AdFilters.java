package com.example.iam.ad.search;

import com.example.iam.ad.exception.IamIntegrationException;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPException;

/** Shared LDAP filter fragments for AD object categories. */
public final class AdFilters {

    /** Matches real user objects (excludes computers, which also carry objectClass=user). */
    public static final Filter USER = Filter.createANDFilter(
            Filter.createEqualityFilter("objectCategory", "person"),
            Filter.createEqualityFilter("objectClass", "user"));

    public static final Filter GROUP = Filter.createEqualityFilter("objectCategory", "group");

    public static final Filter ORGANIZATIONAL_UNIT =
            Filter.createEqualityFilter("objectClass", "organizationalUnit");

    private AdFilters() {
    }

    /** Parses a caller-supplied raw filter, failing with a typed exception on bad syntax. */
    public static Filter parse(String rawFilter) {
        try {
            return Filter.create(rawFilter);
        } catch (LDAPException e) {
            throw new IamIntegrationException("Invalid LDAP filter: " + rawFilter, e);
        }
    }
}

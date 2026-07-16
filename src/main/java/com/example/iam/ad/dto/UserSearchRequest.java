package com.example.iam.ad.dto;

import com.example.iam.ad.domain.AdDomain;

import java.util.List;

/**
 * User search. Provide either a raw {@code ldapFilter} (already scoped to
 * users by the service) or a {@code sAMAccountName}; with neither, all users
 * under the search base are returned (paginated).
 */
public record UserSearchRequest(
        AdDomain domain,
        String ldapFilter,
        String sAMAccountName,
        /** Optional; defaults to the domain's configured base DN. */
        String searchBase,
        /** Optional; defaults to the connector's standard user attribute set. */
        List<String> attributes,
        /** Simple Paged Results page size; defaults to 500 (must be < AD's 1000 limit). */
        Integer pageSize,
        /** Optional hard cap on total results. */
        Integer maxResults) {

    public static UserSearchRequest bySamAccountName(AdDomain domain, String sAMAccountName) {
        return new UserSearchRequest(domain, null, sAMAccountName, null, null, null, null);
    }

    public static UserSearchRequest byFilter(AdDomain domain, String ldapFilter) {
        return new UserSearchRequest(domain, ldapFilter, null, null, null, null, null);
    }
}

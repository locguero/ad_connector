package com.example.iam.ad.dto;

import com.example.iam.ad.domain.AdDomain;

import java.util.List;

/**
 * Group search. Provide either a raw {@code ldapFilter} or a {@code name}
 * (matched against cn/sAMAccountName); with neither, all groups under the
 * search base are returned (paginated).
 */
public record GroupSearchRequest(
        AdDomain domain,
        String ldapFilter,
        String name,
        String searchBase,
        List<String> attributes,
        Integer pageSize,
        Integer maxResults) {

    public static GroupSearchRequest byName(AdDomain domain, String name) {
        return new GroupSearchRequest(domain, null, name, null, null, null, null);
    }

    public static GroupSearchRequest byFilter(AdDomain domain, String ldapFilter) {
        return new GroupSearchRequest(domain, ldapFilter, null, null, null, null, null);
    }
}

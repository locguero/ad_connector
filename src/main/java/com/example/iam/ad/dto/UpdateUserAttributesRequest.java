package com.example.iam.ad.dto;

import com.example.iam.ad.domain.AdDomain;

import java.util.List;
import java.util.Map;

/**
 * Replace-semantics attribute update: each entry replaces the attribute's
 * values; a {@code null} or empty value list removes the attribute entirely.
 */
public record UpdateUserAttributesRequest(
        AdDomain domain,
        AdObjectRef user,
        Map<String, List<String>> attributes) {

    public UpdateUserAttributesRequest {
        if (attributes == null || attributes.isEmpty()) {
            throw new IllegalArgumentException("attributes must contain at least one entry");
        }
    }
}

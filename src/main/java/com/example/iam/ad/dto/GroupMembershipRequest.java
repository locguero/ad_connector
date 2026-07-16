package com.example.iam.ad.dto;

import com.example.iam.ad.domain.AdDomain;

/** Add or remove one user from one group within a single domain. */
public record GroupMembershipRequest(
        AdDomain domain,
        AdObjectRef user,
        AdObjectRef group) {
}

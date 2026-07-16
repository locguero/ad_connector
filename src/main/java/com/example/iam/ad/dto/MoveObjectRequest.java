package com.example.iam.ad.dto;

import com.example.iam.ad.domain.AdDomain;

/** Move a user or group to another OU (LDAP ModifyDN with a new superior). */
public record MoveObjectRequest(
        AdDomain domain,
        AdObjectRef object,
        /** DN of the destination OU. */
        String targetOu) {
}

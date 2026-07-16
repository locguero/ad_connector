package com.example.iam.ad.dto;

import java.util.List;
import java.util.Map;

/** Group as returned by every read/search; {@code objectGuid} is the durable key. */
public record AdGroup(
        String objectGuid,
        String distinguishedName,
        String commonName,
        String sAMAccountName,
        String description,
        int groupType,
        boolean securityGroup,
        /** DNs of direct members. */
        List<String> members,
        Map<String, List<String>> attributes) {
}

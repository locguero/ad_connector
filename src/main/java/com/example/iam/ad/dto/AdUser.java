package com.example.iam.ad.dto;

import java.util.List;
import java.util.Map;

/**
 * User as returned by every read/search. {@code objectGuid} is the canonical
 * string form of AD's binary objectGUID and is the durable foreign key the
 * upstream framework should persist — DNs and sAMAccountNames can change.
 */
public record AdUser(
        String objectGuid,
        String distinguishedName,
        String sAMAccountName,
        String userPrincipalName,
        String displayName,
        String mail,
        boolean enabled,
        int userAccountControl,
        /** DNs of the groups this user is a direct member of. */
        List<String> memberOf,
        /** All returned attributes (objectGUID already in canonical string form). */
        Map<String, List<String>> attributes) {
}

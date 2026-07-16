package com.example.iam.ad.dto;

import com.example.iam.ad.domain.AdDomain;

import java.util.List;
import java.util.Map;

/**
 * Framework-facing request to create a user.
 *
 * <p>If {@code enabled} is true an {@code initialPassword} must be supplied
 * (AD refuses enabled accounts without one); otherwise the account is
 * created disabled and can be enabled after a password reset.</p>
 */
public record ProvisionUserRequest(
        AdDomain domain,
        String commonName,
        String sAMAccountName,
        String userPrincipalName,
        String givenName,
        String surname,
        String displayName,
        String mail,
        /** DN of the OU the user is created in, e.g. "OU=ServiceUsers,DC=qa-ent,DC=example,DC=com". */
        String targetOu,
        String initialPassword,
        boolean enabled,
        /** Any additional AD attributes to set at create time. */
        Map<String, List<String>> additionalAttributes) {

    public ProvisionUserRequest {
        additionalAttributes = additionalAttributes == null ? Map.of() : Map.copyOf(additionalAttributes);
    }
}

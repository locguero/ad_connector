package com.example.iam.ad.web;

import com.example.iam.ad.dto.AdObjectRef;
import com.example.iam.ad.dto.CreateGroupRequest.GroupScope;
import com.example.iam.ad.group.GroupDeactivationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * REST request bodies. The target domain comes from the URL path, so these
 * mirror the internal service DTOs minus the domain field. Required fields
 * are enforced with Bean Validation so malformed requests fail with 400
 * before reaching the directory.
 */
public final class ApiRequests {

    private ApiRequests() {
    }

    public record CreateUserBody(
            @NotBlank @Schema(example = "Jordan Diaz") String commonName,
            @NotBlank @Schema(example = "jdiaz") String sAMAccountName,
            @Schema(example = "jdiaz@qa-ent.example.com") String userPrincipalName,
            String givenName,
            String surname,
            String displayName,
            String mail,
            @NotBlank @Schema(example = "OU=Staff,DC=qa-ent,DC=example,DC=com") String targetOu,
            @Schema(format = "password", description = "Required when enabled=true") String initialPassword,
            boolean enabled,
            Map<String, List<String>> additionalAttributes) {
    }

    public record UpdateAttributesBody(
            @NotEmpty
            @Schema(description = "Replace semantics; an empty value list removes the attribute",
                    example = "{\"department\": [\"IAM\"], \"title\": [\"Engineer\"]}")
            Map<String, List<String>> attributes) {
    }

    public record CreateGroupBody(
            @NotBlank @Schema(example = "app-birthright") String name,
            @NotBlank @Schema(example = "OU=Groups,DC=qa-ent,DC=example,DC=com") String targetOu,
            @Schema(defaultValue = "GLOBAL") GroupScope scope,
            @Schema(defaultValue = "true") Boolean securityGroup,
            String description,
            Map<String, List<String>> additionalAttributes) {
    }

    public record DeactivateGroupBody(
            @Schema(description = "Omit to use the configured default (STRIP_MEMBERSHIP)")
            GroupDeactivationMode mode) {
    }

    public record MoveObjectBody(
            @NotBlank
            @Schema(description = "Object reference value (GUID, DN, or sAMAccountName)") String value,
            @Schema(defaultValue = "SAM_ACCOUNT_NAME") AdObjectRef.Type refType,
            @NotBlank
            @Schema(example = "OU=Disabled,DC=qa-ent,DC=example,DC=com") String targetOu) {
    }
}

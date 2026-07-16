package com.example.iam.ad.dto;

import com.example.iam.ad.domain.AdDomain;

import java.util.List;
import java.util.Map;

/** Framework-facing request to create an AD group. */
public record CreateGroupRequest(
        AdDomain domain,
        String name,
        String targetOu,
        GroupScope scope,
        boolean securityGroup,
        String description,
        Map<String, List<String>> additionalAttributes) {

    public CreateGroupRequest {
        scope = scope == null ? GroupScope.GLOBAL : scope;
        additionalAttributes = additionalAttributes == null ? Map.of() : Map.copyOf(additionalAttributes);
    }

    /** AD groupType scope bits. */
    public enum GroupScope {
        GLOBAL(0x2),
        DOMAIN_LOCAL(0x4),
        UNIVERSAL(0x8);

        private final int flag;

        GroupScope(int flag) {
            this.flag = flag;
        }

        public int flag() {
            return flag;
        }
    }
}

package com.example.iam.ad.dto;

import com.example.iam.ad.domain.AdDomain;
import com.example.iam.ad.group.GroupDeactivationMode;

/**
 * Deactivate a group. AD has no native "disabled" state for groups, so the
 * connector applies a pluggable strategy; {@code mode == null} uses the
 * configured default (strip-membership out of the box).
 */
public record DeactivateGroupRequest(
        AdDomain domain,
        AdObjectRef group,
        GroupDeactivationMode mode) {
}

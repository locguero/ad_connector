package com.example.iam.ad.util;

/**
 * Bit-level helpers for AD's signed 32-bit {@code groupType} attribute.
 * The high bit (0x80000000) distinguishes security groups from
 * distribution groups.
 */
public final class GroupTypeHelper {

    public static final int SECURITY_ENABLED = 0x80000000;

    private GroupTypeHelper() {
    }

    public static boolean isSecurityGroup(int groupType) {
        return (groupType & SECURITY_ENABLED) != 0;
    }

    /** Clears the security bit, converting Security → Distribution while keeping the scope bits. */
    public static int toDistributionGroup(int groupType) {
        return groupType & ~SECURITY_ENABLED;
    }
}

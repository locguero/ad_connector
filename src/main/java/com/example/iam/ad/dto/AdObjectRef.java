package com.example.iam.ad.dto;

/**
 * A directory-object reference the upstream framework can express three ways.
 * Prefer {@link #byGuid(String)} — the objectGUID is the durable key and is
 * immune to renames and OU moves.
 */
public record AdObjectRef(Type type, String value) {

    public enum Type {
        OBJECT_GUID,
        DN,
        SAM_ACCOUNT_NAME
    }

    public AdObjectRef {
        if (type == null || value == null || value.isBlank()) {
            throw new IllegalArgumentException("AdObjectRef requires a type and a non-blank value");
        }
    }

    public static AdObjectRef byGuid(String canonicalGuid) {
        return new AdObjectRef(Type.OBJECT_GUID, canonicalGuid);
    }

    public static AdObjectRef byDn(String dn) {
        return new AdObjectRef(Type.DN, dn);
    }

    public static AdObjectRef bySamAccountName(String sAMAccountName) {
        return new AdObjectRef(Type.SAM_ACCOUNT_NAME, sAMAccountName);
    }

    @Override
    public String toString() {
        return type + "=" + value;
    }
}

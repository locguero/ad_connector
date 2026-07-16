package com.example.iam.ad.util;

/**
 * Converts AD's binary {@code objectGUID} to/from its canonical string form
 * (e.g. {@code 90395f19-1c95-4a24-b955-92e5eaa9f6f1}).
 *
 * <p>AD stores the first three GUID components little-endian, so a straight
 * hex dump of the 16 bytes would NOT match what ADUC or PowerShell display.
 * This helper applies the required byte swaps in both directions. The
 * canonical string is the durable foreign key returned in every DTO: unlike
 * a DN or sAMAccountName it survives renames and OU moves.</p>
 */
public final class ObjectGuidConverter {

    private ObjectGuidConverter() {
    }

    /** Raw 16-byte objectGUID (AD wire order) → canonical lowercase GUID string. */
    public static String toCanonicalString(byte[] guid) {
        if (guid == null || guid.length != 16) {
            throw new IllegalArgumentException("objectGUID must be exactly 16 bytes, got "
                    + (guid == null ? "null" : guid.length));
        }
        return String.format(
                "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
                guid[3] & 0xFF, guid[2] & 0xFF, guid[1] & 0xFF, guid[0] & 0xFF,
                guid[5] & 0xFF, guid[4] & 0xFF,
                guid[7] & 0xFF, guid[6] & 0xFF,
                guid[8] & 0xFF, guid[9] & 0xFF,
                guid[10] & 0xFF, guid[11] & 0xFF, guid[12] & 0xFF,
                guid[13] & 0xFF, guid[14] & 0xFF, guid[15] & 0xFF);
    }

    /** Canonical GUID string → raw 16 bytes in AD wire order (for search filters). */
    public static byte[] toBytes(String canonical) {
        if (canonical == null) {
            throw new IllegalArgumentException("GUID string must not be null");
        }
        String hex = canonical.replace("-", "");
        if (hex.length() != 32) {
            throw new IllegalArgumentException("Invalid GUID string: " + canonical);
        }
        byte[] c = new byte[16];
        for (int i = 0; i < 16; i++) {
            c[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return new byte[]{
                c[3], c[2], c[1], c[0],
                c[5], c[4],
                c[7], c[6],
                c[8], c[9], c[10], c[11], c[12], c[13], c[14], c[15]
        };
    }
}

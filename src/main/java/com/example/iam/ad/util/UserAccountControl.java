package com.example.iam.ad.util;

/**
 * Bitmask helper for AD's {@code userAccountControl} attribute.
 *
 * <p>Enable/disable is always performed as read-modify-write on individual
 * flags — never a raw integer overwrite — so unrelated flags such as
 * DONT_EXPIRE_PASSWORD or SMARTCARD_REQUIRED are preserved.</p>
 */
public final class UserAccountControl {

    public static final int SCRIPT = 0x0001;
    public static final int ACCOUNT_DISABLED = 0x0002;
    public static final int LOCKOUT = 0x0010;
    public static final int PASSWD_NOTREQD = 0x0020;
    public static final int NORMAL_ACCOUNT = 0x0200;
    public static final int DONT_EXPIRE_PASSWORD = 0x10000;
    public static final int SMARTCARD_REQUIRED = 0x40000;
    public static final int PASSWORD_EXPIRED = 0x800000;

    private UserAccountControl() {
    }

    public static int setFlag(int current, int flag) {
        return current | flag;
    }

    public static int clearFlag(int current, int flag) {
        return current & ~flag;
    }

    public static boolean isSet(int current, int flag) {
        return (current & flag) == flag;
    }
}

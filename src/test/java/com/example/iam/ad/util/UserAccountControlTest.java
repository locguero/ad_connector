package com.example.iam.ad.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAccountControlTest {

    @Test
    void disablePreservesOtherFlags() {
        int current = UserAccountControl.NORMAL_ACCOUNT
                | UserAccountControl.DONT_EXPIRE_PASSWORD
                | UserAccountControl.SMARTCARD_REQUIRED;

        int disabled = UserAccountControl.setFlag(current, UserAccountControl.ACCOUNT_DISABLED);

        assertTrue(UserAccountControl.isSet(disabled, UserAccountControl.ACCOUNT_DISABLED));
        assertTrue(UserAccountControl.isSet(disabled, UserAccountControl.DONT_EXPIRE_PASSWORD));
        assertTrue(UserAccountControl.isSet(disabled, UserAccountControl.SMARTCARD_REQUIRED));
        assertTrue(UserAccountControl.isSet(disabled, UserAccountControl.NORMAL_ACCOUNT));
    }

    @Test
    void enableClearsOnlyDisabledBit() {
        int current = UserAccountControl.NORMAL_ACCOUNT
                | UserAccountControl.ACCOUNT_DISABLED
                | UserAccountControl.DONT_EXPIRE_PASSWORD;

        int enabled = UserAccountControl.clearFlag(current, UserAccountControl.ACCOUNT_DISABLED);

        assertFalse(UserAccountControl.isSet(enabled, UserAccountControl.ACCOUNT_DISABLED));
        assertTrue(UserAccountControl.isSet(enabled, UserAccountControl.DONT_EXPIRE_PASSWORD));
        assertEquals(UserAccountControl.NORMAL_ACCOUNT | UserAccountControl.DONT_EXPIRE_PASSWORD,
                enabled);
    }

    @Test
    void settingAnAlreadySetFlagIsIdempotent() {
        int current = UserAccountControl.NORMAL_ACCOUNT | UserAccountControl.ACCOUNT_DISABLED;
        assertEquals(current,
                UserAccountControl.setFlag(current, UserAccountControl.ACCOUNT_DISABLED));
    }
}

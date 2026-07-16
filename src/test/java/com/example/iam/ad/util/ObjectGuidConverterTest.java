package com.example.iam.ad.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjectGuidConverterTest {

    // Raw AD wire bytes for GUID 90395f19-1c95-4a24-b955-92e5eaa9f6f1:
    // first three components are stored little-endian.
    private static final byte[] WIRE_BYTES = {
            (byte) 0x19, (byte) 0x5F, (byte) 0x39, (byte) 0x90,
            (byte) 0x95, (byte) 0x1C,
            (byte) 0x24, (byte) 0x4A,
            (byte) 0xB9, (byte) 0x55,
            (byte) 0x92, (byte) 0xE5, (byte) 0xEA, (byte) 0xA9, (byte) 0xF6, (byte) 0xF1
    };

    private static final String CANONICAL = "90395f19-1c95-4a24-b955-92e5eaa9f6f1";

    @Test
    void convertsWireBytesToCanonicalString() {
        assertEquals(CANONICAL, ObjectGuidConverter.toCanonicalString(WIRE_BYTES));
    }

    @Test
    void convertsCanonicalStringBackToWireBytes() {
        assertArrayEquals(WIRE_BYTES, ObjectGuidConverter.toBytes(CANONICAL));
    }

    @Test
    void roundTripsArbitraryGuids() {
        String guid = "01234567-89ab-cdef-0123-456789abcdef";
        assertEquals(guid, ObjectGuidConverter.toCanonicalString(ObjectGuidConverter.toBytes(guid)));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> ObjectGuidConverter.toCanonicalString(new byte[15]));
        assertThrows(IllegalArgumentException.class,
                () -> ObjectGuidConverter.toCanonicalString(null));
        assertThrows(IllegalArgumentException.class,
                () -> ObjectGuidConverter.toBytes("not-a-guid"));
    }
}

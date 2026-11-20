package com.sky.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    /** MD5 of "123456", the demo password, as the original schema stored it. */
    private static final String LEGACY_MD5_OF_123456 = "e10adc3949ba59abbe56e057f20f883e";

    @Test
    void bcryptHashesVerifyAndAreSaltedDifferentlyEachTime() {
        String first = PasswordHasher.hash("correct horse");
        String second = PasswordHasher.hash("correct horse");

        assertTrue(first.startsWith("$2a$"), first);
        assertNotEquals(first, second);
        assertTrue(PasswordHasher.matches("correct horse", first));
        assertTrue(PasswordHasher.matches("correct horse", second));
        assertFalse(PasswordHasher.matches("wrong horse", first));
        assertFalse(PasswordHasher.needsRehash(first));
    }

    @Test
    void oldMd5HashesStillVerifyAndAreMarkedForReplacement() {
        assertTrue(PasswordHasher.matches("123456", LEGACY_MD5_OF_123456));
        assertFalse(PasswordHasher.matches("654321", LEGACY_MD5_OF_123456));
        assertTrue(PasswordHasher.needsRehash(LEGACY_MD5_OF_123456));
    }

    @Test
    void missingValuesNeverMatch() {
        assertFalse(PasswordHasher.matches(null, LEGACY_MD5_OF_123456));
        assertFalse(PasswordHasher.matches("123456", null));
    }
}

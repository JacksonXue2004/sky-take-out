package com.sky.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * Password hashing.
 * <p>
 * New hashes use BCrypt: salted, so equal passwords get different hashes, and slow on purpose, so
 * a stolen table cannot be guessed through quickly. Accounts created before the switch still hold
 * an unsalted MD5 hex digest, which a lookup table reverses in seconds. Those still verify, and
 * needsRehash tells the caller to replace them with BCrypt right after a successful login.
 */
public final class PasswordHasher {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();
    private static final Pattern LEGACY_MD5 = Pattern.compile("[0-9a-f]{32}");

    private PasswordHasher() {
    }

    public static String hash(String rawPassword) {
        return BCRYPT.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }
        if (isLegacyMd5(storedHash)) {
            byte[] expected = storedHash.getBytes(StandardCharsets.UTF_8);
            byte[] actual = DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8))
                    .getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expected, actual);
        }
        return BCRYPT.matches(rawPassword, storedHash);
    }

    /** True for a hash from before the switch to BCrypt. */
    public static boolean needsRehash(String storedHash) {
        return storedHash != null && isLegacyMd5(storedHash);
    }

    private static boolean isLegacyMd5(String storedHash) {
        return LEGACY_MD5.matcher(storedHash).matches();
    }
}

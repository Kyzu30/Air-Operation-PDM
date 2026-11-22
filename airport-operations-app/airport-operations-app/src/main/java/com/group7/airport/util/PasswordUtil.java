package com.group7.airport.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * SHA-256 hex hashing, identical to MySQL's SHA2(text, 256),
 * so accounts can be created with plain SQL.
 */
public final class PasswordUtil {

    private PasswordUtil() { }

    public static String sha256Hex(char[] password) {
        ByteBuffer bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
        byte[] input = Arrays.copyOfRange(bytes.array(), bytes.position(), bytes.limit());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
            return HexFormat.of().formatHex(digest); // lowercase, same as MySQL SHA2
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        } finally {
            Arrays.fill(input, (byte) 0);
        }
    }

    /* Constant-time comparison of the typed password's hash with the stored hash. */
    public static boolean matches(char[] password, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        byte[] a = sha256Hex(password).getBytes(StandardCharsets.US_ASCII);
        byte[] b = storedHash.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(a, b);
    }
}

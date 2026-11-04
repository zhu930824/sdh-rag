package cn.sdh.backend.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class ContentFingerprint {
    private ContentFingerprint() {}
    public static String sha256(String value) {
        if (value == null) return null;
        return sha256Bytes(value.getBytes(StandardCharsets.UTF_8));
    }
    public static String sha256Bytes(byte[] value) {
        if(value==null) return null;
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}

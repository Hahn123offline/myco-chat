package com.dsh.pcharness;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 桌面 harness 的 SHA1，避免引 Android ChainStore。 */
public final class Sha1 {
    private Sha1() { }
    public static String of(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Throwable t) {
            return Integer.toHexString(String.valueOf(s).hashCode());
        }
    }
}

package com.dsh.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 幂等键：sha1(排序后的 block_ids/字符串)。纯逻辑可测。 */
public final class ChainKey {

    private ChainKey() { }

    public static String of(List<Long> ids) {
        List<Long> copy = new ArrayList<>(new java.util.LinkedHashSet<>(
                ids == null ? Collections.<Long>emptyList() : ids));
        Collections.sort(copy);
        StringBuilder b = new StringBuilder();
        for (Long id : copy) {
            if (b.length() > 0) b.append(',');
            b.append(id);
        }
        return sha1(b.toString());
    }

    public static String ofBlockCsv(String csv) {
        List<Long> ids = new ArrayList<>();
        if (csv != null) {
            for (String s : csv.split(",")) {
                try { ids.add(Long.parseLong(s.trim())); } catch (Throwable ignored) { }
            }
        }
        return of(ids);
    }

    static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Throwable t) {
            return Integer.toHexString(s.hashCode());
        }
    }
}

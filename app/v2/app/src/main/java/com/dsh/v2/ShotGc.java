package com.dsh.v2;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 截图 GC：只删图片，不删 traces/lines 文本。
 *
 * 规则：
 *  - 最近 7 天全量保留
 *  - 更老的：同一小时内只留最早一张（代表帧），其余删
 *  - 总量超过 maxBytes：从最旧开始删
 *
 * 纯文件逻辑，Android/PC 测试都可用；不依赖数据库。
 */
public final class ShotGc {

    public static final long KEEP_DAYS_MS = 7L * 24 * 60 * 60 * 1000;
    public static final long MAX_BYTES = 20L * 1024 * 1024 * 1024;
    public static final long MIN_FREE_BYTES = 2L * 1024 * 1024 * 1024;

    public static final class Result {
        public int scanned;
        public int dropShots;
        public long freedBytes;
        public long totalBytes;
        public boolean lowDisk;
    }

    private ShotGc() { }

    public static Result run(File shotsDir, long nowMs, long maxBytes) {
        Result r = new Result();
        if (shotsDir == null || !shotsDir.isDirectory()) return r;
        File[] fs = shotsDir.listFiles((d, n) -> n.endsWith(".jpg") || n.endsWith(".png"));
        if (fs == null) return r;
        List<File> all = new ArrayList<>(Arrays.asList(fs));
        all.sort(Comparator.comparingLong(File::lastModified));
        r.scanned = all.size();
        long total = 0;
        for (File f : all) total += f.length();

        // 老图：同小时保留最早一张
        Set<String> keptHours = new HashSet<>();
        for (File f : all) {
            if (nowMs - f.lastModified() <= KEEP_DAYS_MS) continue;
            String hour = String.valueOf(f.lastModified() / 3_600_000L);
            if (keptHours.add(hour)) continue;
            if (f.delete()) {
                r.dropShots++;
                r.freedBytes += f.length();
                total -= f.length();
            }
        }
        // 总量上限：从最旧删，最近 7 天也不豁免（存储硬上限）
        if (total > maxBytes) {
            for (File f : all) {
                if (!f.exists()) continue;
                long len = f.length();
                if (f.delete()) {
                    r.dropShots++;
                    r.freedBytes += len;
                    total -= len;
                }
                if (total <= maxBytes) break;
            }
        }
        r.totalBytes = Math.max(0, total);
        r.lowDisk = shotsDir.getUsableSpace() < MIN_FREE_BYTES;
        return r;
    }

    /** 截图先写临时文件，再原子移入正式目录（避免半截文件）。 */
    public static boolean commitTemp(File tmp, File dst) {
        if (tmp == null || dst == null || !tmp.isFile()) return false;
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        if (dst.exists()) dst.delete();
        return tmp.renameTo(dst);
    }

    public static boolean lowDisk(File dir) {
        return dir == null || dir.getUsableSpace() < MIN_FREE_BYTES;
    }
}

package com.dsh.v2;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;

public class ShotGcTest {

    private static File file(File dir, String name, int bytes, long mtime) throws Exception {
        File f = new File(dir, name);
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(new byte[bytes]); }
        f.setLastModified(mtime);
        return f;
    }

    @Test public void 老图同小时只留最早一张() throws Exception {
        File dir = Files.createTempDirectory("gc").toFile();
        long now = 1_700_000_000_000L;
        long old = now - 10L * 24 * 3600 * 1000;
        File a = file(dir, "shot_1.jpg", 10, old);
        File b = file(dir, "shot_2.jpg", 10, old + 1000);
        File c = file(dir, "shot_3.jpg", 10, now - 1000);
        ShotGc.Result r = ShotGc.run(dir, now, ShotGc.MAX_BYTES);
        assertTrue(a.exists());
        assertFalse(b.exists());
        assertTrue(c.exists());
        assertEquals(1, r.dropShots);
    }

    @Test public void 超总量从最旧删() throws Exception {
        File dir = Files.createTempDirectory("gc2").toFile();
        long now = 1_700_000_000_000L;
        File a = file(dir, "a.jpg", 100, now - 3000);
        File b = file(dir, "b.jpg", 100, now - 2000);
        File c = file(dir, "c.jpg", 100, now - 1000);
        ShotGc.Result r = ShotGc.run(dir, now, 150);
        assertFalse(a.exists());
        assertTrue(b.exists() || c.exists());
        assertTrue(r.freedBytes >= 100);
    }

    @Test public void 临时截图原子移入正式目录() throws Exception {
        File dir = Files.createTempDirectory("gc3").toFile();
        File tmp = file(dir, "tmp.jpg", 5, System.currentTimeMillis());
        File dst = new File(new File(dir, "shots"), "final.jpg");
        assertTrue(ShotGc.commitTemp(tmp, dst));
        assertTrue(dst.exists());
        assertFalse(tmp.exists());
    }

    @Test public void 采样器只在间隔后且画面变化时截图() {
        ContentChangeSampler s = new ContentChangeSampler();
        assertFalse(s.shouldCapture(1000, "a"));      // 首次间隔未到
        assertTrue(s.shouldCapture(2000, "a"));
        assertFalse(s.shouldCapture(3000, "a"));      // hash 相同
        assertFalse(s.shouldCapture(3500, "b"));      // 间隔不足
        assertTrue(s.shouldCapture(4000, "b"));
        assertEquals(2, s.frames());
    }

    @Test public void 块内帧数上限20() {
        ContentChangeSampler s = new ContentChangeSampler();
        for (int i = 0; i < 20; i++) {
            assertTrue(s.shouldCapture(2000L * (i + 1), "h" + i));
        }
        assertFalse(s.shouldCapture(2000L * 21, "h21"));
    }
}

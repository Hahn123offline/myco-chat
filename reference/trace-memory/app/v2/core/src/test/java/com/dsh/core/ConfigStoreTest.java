package com.dsh.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dsh.core.Config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class ConfigStoreTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void 没有配置文件时返回空配置() {
        ConfigStore cs = new ConfigStore(tmp.getRoot());
        Config c = cs.load("usagetrace");
        assertEquals("", c.getString("policy", ""));
        assertFalse(cs.exists("usagetrace"));
    }

    @Test public void 能读出properties() throws Exception {
        ConfigStore cs = new ConfigStore(tmp.getRoot());
        File f = cs.fileFor("usagetrace");
        f.getParentFile().mkdirs();
        Files.write(f.toPath(),
                "policy=com.tencent.mm=tick:10000:ocr\nother=42\n".getBytes(StandardCharsets.UTF_8));

        Config c = cs.load("usagetrace");
        assertEquals("com.tencent.mm=tick:10000:ocr", c.getString("policy", ""));
        assertEquals(42, c.getInt("other", 0));
    }

    @Test public void 文件变了会自动热重载() throws Exception {
        ConfigStore cs = new ConfigStore(tmp.getRoot());
        File f = cs.fileFor("usagetrace");
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "k=v1\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("v1", cs.load("usagetrace").getString("k", ""));

        Thread.sleep(10);
        Files.write(f.toPath(), "k=v2\n".getBytes(StandardCharsets.UTF_8));
        f.setLastModified(System.currentTimeMillis() + 2000);

        assertEquals("v2", cs.load("usagetrace").getString("k", ""));
    }

    @Test public void 保存后能读回() {
        ConfigStore cs = new ConfigStore(tmp.getRoot());
        Map<String, String> v = new HashMap<>();
        v.put("policy", "com.android.browser=event");
        cs.save("usagetrace", v);

        assertTrue(cs.exists("usagetrace"));
        assertEquals("com.android.browser=event",
                cs.load("usagetrace").getString("policy", ""));
    }

    @Test public void 坏文件不影响加载_返回空配置() throws Exception {
        ConfigStore cs = new ConfigStore(tmp.getRoot());
        File f = cs.fileFor("broken");
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), new byte[]{(byte) 0xff, (byte) 0xfe, 0x00, 0x01});
        Config c = cs.load("broken");
        assertEquals("def", c.getString("x", "def"));
    }

    @Test public void 各模块配置互不干扰() throws Exception {
        ConfigStore cs = new ConfigStore(tmp.getRoot());
        cs.fileFor("a").getParentFile().mkdirs();
        Files.write(cs.fileFor("a").toPath(), "k=a\n".getBytes(StandardCharsets.UTF_8));
        Files.write(cs.fileFor("b").toPath(), "k=b\n".getBytes(StandardCharsets.UTF_8));

        assertEquals("a", cs.load("a").getString("k", ""));
        assertEquals("b", cs.load("b").getString("k", ""));
    }
}

package com.dsh.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dsh.host.api.data.MemoryBlock;
import com.dsh.host.api.data.RedactionLevel;
import com.dsh.host.api.memory.EgressTier;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 调试导出的 JSON 格式（纯逻辑，可在 JVM 单测；adb 拉取见 verify_pipeline.md）。 */
public class MemoryExportTest {

    private static MemoryBlock block(long id, String pkg, String title, String text, EgressTier tier) {
        MemoryBlock b = new MemoryBlock();
        b.id = id;
        b.episodeId = id * 10;
        b.tStart = 1_700_000_000_000L + id;
        b.tEnd = b.tStart + 5_000;
        b.pkg = pkg;
        b.title = title;
        b.maskedText = text;
        b.egressTier = tier;
        b.redactionLevel = RedactionLevel.MODEL;
        b.redactedAt = b.tStart + 1;
        b.evidenceRef = "trace:" + id;
        return b;
    }

    @Test public void 导出包含关键字段() {
        List<MemoryBlock> blocks = Arrays.asList(
                block(1, "tv.danmaku.bili", "影之刃零 攻略", "UP主分享了连招", EgressTier.WHITE),
                block(2, "com.tencent.mm", "聊天", "138*****678", EgressTier.BLACK));

        String json = MemoryExport.json(blocks, null, null);
        assertTrue(json.contains("\"exported\": 2"));
        assertTrue(json.contains("\"pkg\":\"tv.danmaku.bili\""));
        assertTrue(json.contains("\"title\":\"影之刃零 攻略\""));
        assertTrue(json.contains("\"egressTier\":\"WHITE\""));
        assertTrue(json.contains("\"egressTier\":\"BLACK\""));
        assertTrue(json.contains("\"redactionLevel\":\"MODEL\""));
        assertTrue(json.contains("\"evidenceRef\":\"trace:1\""));
        assertTrue(json.contains("\"id\":1"));
        assertTrue(json.contains("\"tStart\":1700000000001"));
        assertTrue("默认导出要暴露检索引擎", json.contains("\"searchEngine\": \"LIKE\""));
        assertTrue("降级原因必须可见", json.contains("\"searchEngineReason\": \"ok\""));
        assertTrue("db 版本用于确认 build 是否真的跑起来", json.contains("\"dbVersion\": 6"));
        assertTrue("FTS 分数字段存在", json.contains("\"score\":0.0"));
        assertTrue("块要带 label 字段（未打标为 null）", json.contains("\"label\":null"));
        assertTrue("块要带 labelStatus", json.contains("\"labelStatus\":\"PENDING\""));
        assertTrue("labels 统计存在", json.contains("\"labels\": {\"total\": -1, \"ok\": -1, \"failed\": -1, \"skipped\": -1, \"pending\": -1}"));
    }

    @Test public void 导出带打标统计() {
        String json = MemoryExport.json(List.of(), null, null, 5, 5,
                MemoryFtsInstaller.ENGINE_LIKE, MemoryFtsInstaller.REASON_FTS5_UNAVAILABLE,
                new MemoryExport.LabelStats(2, 1, 1, 1));
        assertTrue(json, json.contains("\"labels\": {\"total\": 5, \"ok\": 2, \"failed\": 1, \"skipped\": 1, \"pending\": 1}"));
    }

    @Test public void 导出暴露实际检索引擎_FTS5() {
        String json = MemoryExport.json(new ArrayList<>(), null, null, -1, -1, "FTS5");
        assertTrue(json, json.contains("\"searchEngine\": \"FTS5\""));
    }

    @Test public void 导出暴露LIKE降级原因() {
        String json = MemoryExport.json(new ArrayList<>(), null, null, -1, -1,
                MemoryFtsInstaller.ENGINE_LIKE, MemoryFtsInstaller.REASON_FTS5_UNAVAILABLE);
        assertTrue(json, json.contains("\"searchEngine\": \"LIKE\""));
        assertTrue(json, json.contains("\"searchEngineReason\": \"fts5-unavailable\""));
    }

    @Test public void 各档位计数() {
        List<MemoryBlock> blocks = Arrays.asList(
                block(1, "a", "", "", EgressTier.WHITE),
                block(2, "a", "", "", EgressTier.GRAY),
                block(3, "a", "", "", EgressTier.BLACK),
                block(4, "a", "", "", EgressTier.UNKNOWN));
        String json = MemoryExport.json(blocks, null, null);
        assertTrue(json, json.contains("\"UNKNOWN\":1"));
        assertTrue(json, json.contains("\"WHITE\":1"));
        assertTrue(json, json.contains("\"GRAY\":1"));
        assertTrue(json, json.contains("\"BLACK\":1"));
    }

    @Test public void 字符串转义_引号反斜杠换行控制字符() {
        String title = "标题\"引号\"" + "\n" + "换行" + "\t制表" + "\\反斜杠" + "\u0001";
        String json = MemoryExport.json(List.of(block(1, "p", title, "x", EgressTier.GRAY)), null, null);
        assertTrue(json.contains("\\\"引号\\\""));
        assertTrue(json.contains("\\n"));
        assertTrue(json.contains("\\t"));
        assertTrue(json.contains("\\\\反斜杠"));
        assertTrue(json.contains("\\u0001"));
        assertFalse("不能有裸换行在 title 里", json.contains("标题\"引号\"\n换行"));
    }

    @Test public void 空列表也能导出_字段稳定() {
        String json = MemoryExport.json(new ArrayList<>(), null, null);
        assertTrue(json.contains("\"exported\": 0"));
        assertTrue(json.contains("\"demoQuery\": \"\""));
        assertTrue(json.contains("\"blocks\": []"));
    }

    @Test public void 检索自测字段_demoQuery与命中块() {
        List<MemoryBlock> hits = List.of(block(9, "tv.danmaku.bili", "影之刃零 攻略", "连招", EgressTier.WHITE));
        String json = MemoryExport.json(new ArrayList<>(), "影之刃零", hits);
        assertTrue(json.contains("\"demoQuery\": \"影之刃零\""));
        assertTrue(json.contains("\"demoHits\": ["));
        assertTrue(json.contains("\"title\":\"影之刃零 攻略\""));
    }

    @Test public void 空字段按JSON_null导出() {
        MemoryBlock b = block(1, null, null, null, null);
        String json = MemoryExport.blockJson(b);
        // 空串字段导出为空串；evidenceRef 保留 null 语义
        b.evidenceRef = null;
        json = MemoryExport.blockJson(b);
        assertTrue(json.contains("\"evidenceRef\":null"));
        assertTrue(json.contains("\"egressTier\":\"UNKNOWN\""));
    }

    @Test public void 时间戳与导出时间存在() {
        String json = MemoryExport.json(List.of(block(1, "p", "", "", EgressTier.WHITE)), null, null);
        assertTrue(json.contains("\"ts\": "));
        assertTrue(json.matches("(?s).*\"ts\": \\d+.*"));
    }

    @Test public void 条数对账字段_episodes_blocks_diff() {
        String json = MemoryExport.json(List.of(block(1, "p", "", "", EgressTier.WHITE)),
                null, null, 7, 5);
        assertTrue(json, json.contains("\"reconcile\": {\"episodes\": 7, \"blocks\": 5, \"diff\": 2}"));
    }

    @Test public void 拿不到episode统计时对账值为负_不编造() {
        String json = MemoryExport.json(List.of(block(1, "p", "", "", EgressTier.WHITE)), null, null);
        assertTrue(json, json.contains("\"episodes\": -1"));
        assertTrue(json, json.contains("\"diff\": -1"));
    }

    @Test public void 没有差值时diff为0() {
        String json = MemoryExport.json(new ArrayList<>(), null, null, 3, 3);
        assertTrue(json, json.contains("\"diff\": 0"));
    }
}

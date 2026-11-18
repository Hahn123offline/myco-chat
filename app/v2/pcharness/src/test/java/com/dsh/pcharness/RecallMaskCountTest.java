package com.dsh.pcharness;

import com.dsh.memory.RecallCore;
import com.dsh.memory.RecallHit;
import com.dsh.memory.RecallQuery;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** 真实数据计数：微信/QQ 的 REDACT 块运行时 Masker 成功/占位符。 */
public class RecallMaskCountTest {

    @Test public void 微信QQ真实块mask计数() {
        JdbcMemorySource source = new JdbcMemorySource("<workspace>/raw/v2/memory.db");
        JdbcMaskProvider masker = new JdbcMaskProvider("<workspace>/raw/v2/trace.db");
        List<RecallHit> all = new ArrayList<>();
        for (String pkg : new String[]{"com.tencent.mm", "com.tencent.mobileqq"}) {
            try {
                all.addAll(RecallCore.recall(source, new RecallQuery()
                        .query("").pkg(pkg).from("2000-01-01").to("2100-01-01")
                        .limit(10000)));
            } catch (Throwable ignored) { }
        }
        RecallCore.Sanitized s = RecallCore.sanitize(all, masker);
        int placeholders = 0;
        for (RecallHit h : s.hits) if (h.text.contains("缺少有效掩码文本")) placeholders++;
        System.out.println("MASK_STATS total=" + all.size()
                + " success=" + s.maskedSuccess
                + " FULL=" + s.fullMasked
                + " TEXT_ONLY=" + s.textOnlyMasked
                + " placeholder=" + placeholders
                + " blocked=" + s.blocked);
        org.junit.Assert.assertTrue("textOnlyMasked 低于下限: " + s.textOnlyMasked,
                s.textOnlyMasked >= 150);
        org.junit.Assert.assertTrue("placeholder 超过上限: " + placeholders,
                placeholders <= 20);
    }
}

package com.dsh.agent.l2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GapDeclarationTest {

    @Test public void 结尾有缺口声明才通过() {
        assertTrue(L2Tools.hasGapDeclaration(
                "用户先看了通知栏，随后处理了设置。其中一段内容不在可处理范围内。"));
        assertFalse(L2Tools.hasGapDeclaration(
                "用户先看了通知栏，随后处理了设置。"));
        assertFalse(L2Tools.hasGapDeclaration(
                "其中一段内容不在可处理范围内。用户先看了通知栏，随后处理了设置。"));
    }
}

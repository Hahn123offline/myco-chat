package com.dsh.v2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PromptAssemblerTest {

    @Test public void 人格为空照样工作() {
        String p = PromptAssembler.assemble("", "仿真契约", "事实状态");
        assertFalse(p.contains("人格"));
        assertTrue(p.contains("仿真契约"));
        assertTrue(p.contains("事实状态"));
        assertTrue(p.indexOf("仿真契约") < p.indexOf("事实状态"));
    }

    @Test public void 人格存在且顺序在前() {
        String p = PromptAssembler.assemble("我是谁", "仿真契约", "事实状态");
        assertTrue(p.indexOf("我是谁") < p.indexOf("仿真契约"));
        assertTrue(p.indexOf("仿真契约") < p.indexOf("事实状态"));
    }
}

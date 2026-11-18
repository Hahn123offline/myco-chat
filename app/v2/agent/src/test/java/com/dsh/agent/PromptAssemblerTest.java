package com.dsh.agent;

import static org.junit.Assert.*;

import org.junit.Test;

public class PromptAssemblerTest {

    @Test public void 顺序20_25_800且persona缺失跳过() {
        String out = PromptAssembler.assemble("我是谁", "仿真", "事实");
        int p = out.indexOf("## 人格（order 20）");
        int s = out.indexOf("## 仿真契约（order 25）");
        int f = out.indexOf("## 事实状态（order 800+）");
        assertTrue(p >= 0 && s > p && f > s);
        assertTrue(out.contains("我是谁") && out.contains("仿真") && out.contains("事实"));
    }

    @Test public void persona为空时只保留sim与facts() {
        String out = PromptAssembler.assemble("", "仿真", "");
        assertFalse(out.contains("人格"));
        assertTrue(out.contains("仿真契约"));
        assertFalse(out.contains("事实状态"));
    }
}

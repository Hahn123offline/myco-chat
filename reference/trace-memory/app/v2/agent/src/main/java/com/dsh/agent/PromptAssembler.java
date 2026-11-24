package com.dsh.agent;

/** 三层提示词纯拼装：persona(order20) / sim(order25) / facts(order800)。 */
public final class PromptAssembler {

    private PromptAssembler() { }

    /** persona 不存在/为空 → 整段跳过；空人格系统必须照常工作。 */
    public static String assemble(String persona, String sim, String facts) {
        StringBuilder b = new StringBuilder();
        if (persona != null && !persona.trim().isEmpty()) {
            b.append("## 人格（order 20）\n").append(persona.trim()).append("\n\n");
        }
        if (sim != null && !sim.trim().isEmpty()) {
            b.append("## 仿真契约（order 25）\n").append(sim.trim()).append("\n\n");
        }
        if (facts != null && !facts.trim().isEmpty()) {
            b.append("## 事实状态（order 800+）\n").append(facts.trim()).append('\n');
        }
        return b.toString();
    }
}

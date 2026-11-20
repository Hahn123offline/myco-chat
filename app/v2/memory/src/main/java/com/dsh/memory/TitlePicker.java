package com.dsh.memory;

import java.util.List;

/** 标题选择：只来自 A11Y/BOTH 的顶部候选，y 最小；不要求水平居中。 */
public final class TitlePicker {

    public static final class Candidate {
        public String text = "";
        public int top;
        public String source = "";
        public String role = "";
        public Candidate(String text, int top, String source, String role) {
            this.text = text; this.top = top; this.source = source; this.role = role;
        }
    }

    private TitlePicker() { }

    public static String pick(List<Candidate> cs) {
        if (cs == null) return "";
        String best = "";
        int bestTop = Integer.MAX_VALUE;
        for (Candidate c : cs) {
            if (c == null || c.text == null) continue;
            String t = c.text.trim();
            if (t.length() < 2 || t.length() > 20) continue;
            if (!"A11Y".equals(c.source) && !"BOTH".equals(c.source)) continue;
            if (c.top < 80 || c.top > 400) continue;
            if (t.matches("^[0-9.:%+\\-\\s]+$")) continue;
            if (t.matches("^(返回.*|聊天设置|听筒模式|自动朗读|打开侧边栏|开启新对话|更多|发送|语音|相册|拍照|表情|删除|取消|确定|搜索|设置|消息|频道|联系人|动态|现在|昨天|星期.|QQ|微信|分享|收藏|编辑|复制)$")) continue;
            if (t.contains("未读") || t.contains("资料卡")) continue;
            if (c.top < bestTop) { bestTop = c.top; best = t; }
        }
        return best;
    }
}

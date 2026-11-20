package com.dsh.memory;

/** 行级 chrome 分类（纯规则，PC/Android 共用）。 */
public final class TextKind {
    private TextKind() { }
    public static String kind(String text, int y) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return "CHROME";
        if (y < 120 || y > 2350) return "CHROME";
        if (t.matches("^\\d{1,2}:\\d{2}.*")) return "CHROME";
        if (t.matches(".*(5G|4G|KB/s|KB/S|%|电量|信号|WLAN|wifi).*")) return "CHROME";
        if (t.matches("^(返回.*|聊天设置|听筒模式|自动朗读|打开侧边栏|开启新对话|发送|相册|语音|拍照|表情|更多功能|符号.*|换行|回|取消|确定|搜索|设置|消息|频道|联系人|动态|我的资料卡|.*资料卡)$")) return "CHROME";
        if (t.matches("^[A-Z]( [A-Z])*$") || t.matches("^[A-Z]{2,}$")) return "CHROME";
        int cjk = 0; for (int i=0;i<t.length();i++){char c=t.charAt(i); if(c>=0x4e00&&c<=0x9fff)cjk++;}
        boolean ascii = t.matches("(?s).*[A-Za-z]{3,}.*");
        if (cjk < 4 && !ascii) return "CHROME";
        if (t.length() < 8 && !t.matches("(?s).*[，。！？、；：,.!?;:].*")) return "CHROME";
        if (t.matches("^[0-9.:%+\\-\\s]+$")) return "CHROME";
        return "CONTENT";
    }
}

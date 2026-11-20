package com.dsh.memory;

/** 行级 chrome 分类（纯规则，PC/Android 共用）。 */
public final class TextKind {
    private TextKind() { }
    /** 本 App 自己的 UI/回答文本，永远不该进记忆消息。 */
    public static boolean isSelfUi(String text) {
        String t = text == null ? "" : text;
        return t.contains("问我：") || t.contains("问它：") || t.contains("【覆盖】")
                || t.contains("以上都是我翻记录") || t.contains("（发送者未知）")
                || t.contains("【对象消息】") || t.contains("【本次显示】")
                || t.contains("问它:") || t.contains("问我:");
    }

    /** 明确不是消息的行：状态/文件名/纯表情/单字符。 */
    public static boolean isNonMessage(String text) {
        String t = text == null ? "" : text.trim();
        if (t.length() < 2) return true;
        if (t.matches("^[\\p{So}\\p{S}\\s]+$")) return true;
        if (t.matches(".*\\.(csv|md|pdf|wav|json|srt|txt|jpg|jpeg|png|zip|rar)$")) return true;
        if (t.matches(".*(在线|听筒模式|自动朗读|打开侧边栏|开启新对话|资料卡|未读|置顶聊天|群文件).*")) return true;
        if (t.matches("^[0-9.:%+\\-\\s]+$")) return true;
        return false;
    }

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

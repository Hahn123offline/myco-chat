#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""task-67 回溯：用 page_elements 的标题节点 + 消息节点建对象线程，验证现有数据够不够。"""
import re, sqlite3, sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = Path(r"<workspace>"); DB = ROOT / "raw/v2/device/evidence.db"
OUT = ROOT / "raw/v2/thread-byobjectkey.md"
c = sqlite3.connect(DB); c.text_factory = lambda b: b.decode("utf-8", "replace")
UI = re.compile(r"^(返回|聊天设置|听筒模式|设置|发送|相册|语音|拍照|表情|更多功能|"
                r"\d{1,2}:\d{2}|\(\d+\)|\d+条新消息|展开|收起|我的资料卡|LV\d+|"
                r"25-鄂-计算机-曾嘉睿的资料卡|清除所有通知。|现在|昨天|星期.)$")
MSG = re.compile(r"^([^:：]{1,40})[:：]\s*(.+)$", re.S)

def fmt(t0): return datetime.fromtimestamp(t0 / 1000).strftime("%m-%d %H:%M")

def main():
    # 所有疑似标题节点：顶部区、非 chrome、非列表行
    titles = c.execute("select t0,text,left,top,role,source from page_elements "
                       "where source in ('A11Y','BOTH') and top<330 and length(text)>=2 "
                       "and length(text)<=40").fetchall()
    cand = defaultdict(list)
    for t0, text, left, top, role, src in titles:
        t = text.strip()
        if UI.match(t) or ", ," in t: continue
        if t in ("QQ", "微信", "消息", "频道", "联系人", "动态"): continue
        cand[t].append(t0)
    # 零杯标题
    zero = [x for x in cand if "零杯" in x]
    zero_t0 = sorted(set(t for x in zero for t in cand[x]))
    # 每个标题屏找消息节点
    msgs = []           # (t0,obj,sender,msg,trace)
    screens_with_msg = 0
    for t0 in zero_t0:
        obj = next((x for x in zero if t0 in cand[x]), "零杯")
        rows = c.execute("select text,top from page_elements where t0=? and source in ('A11Y','BOTH') "
                         "and top>300 and length(text)>3 order by top", (t0,)).fetchall()
        got = 0
        for text, top in rows:
            t = text.strip()
            if UI.match(t): continue
            m = MSG.match(t)
            if not m: continue
            sender, msg = m.group(1).strip(), m.group(2).strip()
            if sender and sender not in ("现在",) and len(msg) >= 2:
                msgs.append((t0, obj, sender, msg)); got += 1
        if got: screens_with_msg += 1
    seen = {}
    for t0, obj, sender, msg in sorted(msgs):
        seen.setdefault((sender, msg), [t0, obj, sender, msg, 1])
        seen[(sender, msg)][4] += 1
    out = ["# 用标题节点回溯对象线程（page_elements）", "",
           "对象键从顶部标题节点推断；消息取同屏 A11Y/BOTH 的 `发送者: 消息` 节点。", "",
           "## 标题识别", "",
           "- 顶部候选标题文本 %d 种；含「零杯」的标题 %s" % (len(cand), zero),
           "- 零杯标题屏 %d 个；其中至少解析出一条消息的屏 %d 个" % (len(zero_t0), screens_with_msg),
           "- 解析出消息 %d 条；去重后 **%d 条唯一消息**" % (len(msgs), len(seen)), "",
           "## 零杯消息", "",
           "| observed_at | sender | message | 次数 |", "|---|---|---|---:|"]
    for k, v in sorted(seen.items(), key=lambda kv: kv[1][0]):
        out.append("| %s | %s | %s | %d |" % (fmt(v[0]), v[2], v[3].replace("|", "\\|"), v[4]))
    out += ["", "## 判定", "",
            "- 对比上一轮列表预览：12 条唯一消息；本轮标题屏解析：%d 条" % len(seen),
            "- 结果：%s" % ("**能扩到几十条**，只差对象键" if len(seen) >= 30 else
                            "**不能扩到几十条**：标题节点有，但群打开屏的 A11Y 消息节点缺失"),
            "",
            "### 样例：零杯群打开屏（t0=1794913611464）",
            "标题 `零杯（）` 在 y=160 BUTTON；y=329–2297 只有 `我的资料卡/LV16/时间/发送/相册`，"
            "**没有一条 sender/message 节点**；同屏 OCR 源也没有补上。说明对象键能拿到，但消息正文不在 page_elements 里。",
            "",
            "## B站", "",
            "B站 A11Y 是扁平评论/广告/按钮，标题节点不是「对象名」；无稳定视频 id，本轮不建线程。", ""]
    OUT.write_text("\n".join(out), encoding="utf-8")
    print("written", OUT, "title_screens", len(zero_t0), "msg_screens", screens_with_msg,
          "msgs", len(msgs), "unique", len(seen), "zero titles", zero)

if __name__ == "__main__": main()

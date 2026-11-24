#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""task-69 v2: 用 OCR bbox 行按 y 分组抽群消息（不靠 traces.text 拼接）。"""
import re, sqlite3, sys, difflib
from collections import Counter
from datetime import datetime
from pathlib import Path
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = Path(r"<workspace>"); DB = ROOT / "raw/v2/device/ev_copy.db"
OUT = ROOT / "raw/v2/group-memory-零杯-v2.md"
c = sqlite3.connect(DB); c.text_factory = lambda b: b.decode("utf-8", "replace")
TOP_CUT, BOT_CUT = 240, 2400
SENDER = re.compile(r"(Hahn123|群主|群成员|25-鄂|25-号鄂|曾嘉)")
CHROME = re.compile(r"^(\d{1,2}:\d{2}|.*KB/s.*|[0-9.]+ ?(5G|4G|G)|发送|相册|语音|拍照|"
                    r"表情|更多功能|回|[A-Z]( [A-Z])*|符号123|换行|中っ|L英|0点击说话|"
                    r"\d+|[く<〈].*零杯.*)$")

def chrome(t):
    t = (t or "").strip()
    if not t: return True
    if CHROME.match(t): return True
    if "资料卡" in t or t.startswith("LV"): return True
    return False

def norm(s):
    return re.sub(r"[\s，,。！？!?~～、；;：:\-—_|\[\]「」『』()（）]", "", s or "")

def main():
    traces = c.execute("select id,t0,pkg,page_title,shot_ref from traces "
                       "where text like '%零杯%' and shot_ref is not null and shot_ref!='' "
                       "order by t0").fetchall()
    cand = []; screens = 0; used = []
    for tid, t0, pkg, title, shot in traces:
        rows = c.execute("select text,top,left,source from page_elements where shot_ref=? "
                         "order by top,left", (shot,)).fetchall()
        # 只处理群详情页：A11Y 标题节点精确为「零杯（）」
        has_title = any((r[0] or "").strip() in ("零杯（）", "零杯()")
                        and r[3] in ("A11Y", "BOTH") and r[1] < 350 for r in rows)
        if not has_title: continue
        ocr = [r for r in rows if r[3] in ("OCR", "BOTH") and TOP_CUT < r[1] < BOT_CUT]
        if len(ocr) < 5: continue
        screens += 1; used.append((tid, t0, pkg, title, len(ocr)))
        sender = "未知"; msgs = []; cur = None; last_y = -999
        for text, top, left, src in rows:
            if not (TOP_CUT < top < BOT_CUT): continue
            t = (text or "").strip()
            if not t: continue
            if SENDER.search(t) and (":" not in t or "群" in t) and len(t) < 40:
                sender = "我" if "Hahn123" in t else "群主"
                cur = None; continue
            if src not in ("OCR", "BOTH") or chrome(t): continue
            if cur is not None and top - last_y <= 80:
                cur[2] += t
            else:
                cur = [sender, top, t]; msgs.append(cur)
            last_y = top
        for spk, top, text in msgs:
            text = re.sub(r"\s+", "", text)
            if len(text) < 3: continue
            cand.append((t0, tid, spk, text))
    # 去重
    uniq = []
    for t0, tid, spk, msg in sorted(cand):
        n = norm(msg)
        hit = None
        for u in uniq:
            if u["spk"] != spk: continue
            if difflib.SequenceMatcher(None, n, u["n"]).ratio() >= 0.9:
                hit = u; break
        if hit: hit["count"] += 1
        else: uniq.append(dict(t0=t0, tid=tid, spk=spk, msg=msg, n=n, count=1))
    days = sorted(set(datetime.fromtimestamp(t / 1000).strftime("%m-%d") for _, t, *_ in used))
    out = ["# 群「零杯」消息记忆 v2（OCR bbox）", "",
           "来源：ev_copy.db，traces 的 shot_ref → page_elements，按 y 分组、sender 行归属、全局去重。", "",
           "## 汇总", "",
           "- 有 OCR bbox 的零杯屏：%d" % screens,
           "- 跨天：%s" % "/".join(days),
           "- 候选消息：%d" % len(cand),
           "- 去重后：**%d 条唯一消息**" % len(uniq),
           ""]
    out += ["## 消息（按 observed_at）", ""]
    for u in sorted(uniq, key=lambda x: x["t0"]):
        ts = datetime.fromtimestamp(u["t0"] / 1000).strftime("%m-%d %H:%M")
        out.append("- `[%s]` %s: %s  <sub>trace=%d ×%d</sub>" % (
            ts, u["spk"], u["msg"], u["tid"], u["count"]))
    out += ["", "## sender 分布", ""]
    for k, v in Counter(u["spk"] for u in uniq).most_common(): out.append("- %s: %d" % (k, v))
    out += ["", "## 与 v1 对比", "",
            "- v1：190 屏 → 68 候选 → 42 唯一，2/3 可读，sender 基本不可靠",
            "- v2：%d 屏 → %d 候选 → %d 唯一；bbox 让 sender 行独立、按 y 归组" % (
                screens, len(cand), len(uniq)), ""]
    OUT.write_text("\n".join(out), encoding="utf-8")
    print("written", OUT, "screens", screens, "cand", len(cand), "uniq", len(uniq), "days", days)

if __name__ == "__main__": main()

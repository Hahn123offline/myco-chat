#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""task-77b: 规则1 A11Y 重叠优选 + 规则2 乱码构成，50 屏改造前后基线。"""
import re, sqlite3, sys
from collections import Counter
from pathlib import Path
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = Path(r"<workspace>"); DB = ROOT / "raw/v2/device/ev_copy.db"
OUT = ROOT / "raw/v2/ocr-cleanup-report.md"
c = sqlite3.connect(DB); c.text_factory = lambda b: b.decode("utf-8", "replace")
PKGS = [("QQ", "com.tencent.mobileqq", 15), ("B站", "tv.danmaku.bili", 10),
        ("ChatGPT", "com.openai.chatgpt", 8), ("微信", "com.tencent.mm", 5),
        ("设置", "com.android.settings", 5), ("浏览器", "com.android.browser", 5),
        ("DeepSeek", "com.deepseek.chat", 2)]
screens = []
for name, pkg, n in PKGS:
    rows = c.execute("select distinct t.shot_ref from traces t join page_elements e "
                     "on e.shot_ref=t.shot_ref where t.pkg=? and t.shot_ref is not null "
                     "and t.shot_ref!='' and e.text!='' limit ?", (pkg, n * 3)).fetchall()
    screens += [(name, pkg, r[0]) for r in rows[:n]]
screens = screens[:50]

def cjk(s): return len(re.findall(r"[\u4e00-\u9fff]", s or ""))
def words(s): return len(re.findall(r"[A-Za-z]{2,}", s or ""))
def symdigit(s):
    return sum(1 for ch in (s or "") if ch.isdigit() or (not ch.isalnum() and ch not in " \t"))

def old_garbled(s):
    s = (s or "").strip()
    if not s: return True
    return (not re.search(r"[\u4e00-\u9fff]{2}", s)
            and not re.search(r"[A-Za-z]{3,}", s))

def garbled(s):
    s = (s or "").strip()
    if not s: return True
    w, cd, ln = words(s), cjk(s), len(s)
    if cd < 4 and w < 2: return True
    if ln and symdigit(s) * 100 / ln > 40: return True
    if not re.search(r"[，。！？、；：,.!?;:]", s) and " " not in s and ln > 20: return True
    switches = 0; run = 0; last = None
    for ch in s:
        isc = "\u4e00" <= ch <= "\u9fff"
        if not isc and not ch.isalnum(): continue
        if last is None: last = isc; run = 1; continue
        if isc == last: run += 1; continue
        if run <= 1: switches += 1
        run = 1; last = isc
    return switches >= 3

def overlap(a, b):
    ix = max(0, min(a[2], b[2]) - max(a[0], b[0]))
    iy = max(0, min(a[3], b[3]) - max(a[1], b[1]))
    area = max(1, (a[2] - a[0]) * (a[3] - a[1]))
    return ix * iy / area

def rows(shot):
    return c.execute("select text,left,top,right,bottom,source,confidence from page_elements "
                     "where shot_ref=? and text!='' order by top,left", (shot,)).fetchall()

before = Counter(); after = Counter(); changed = []
for name, pkg, shot in screens:
    rs = rows(shot)
    a11y = [r for r in rs if r[5] in ("A11Y", "BOTH")]
    ocr = [r for r in rs if r[5] == "OCR"]
    for r in rs:
        before["bad" if old_garbled(r[0]) else "ok"] += 1
        before["newbad" if garbled(r[0]) else "newok"] += 1
    new = []
    for r in rs:
        if r[5] != "OCR": new.append(r); continue
        best = None; bestr = 0
        for a in a11y:
            q = overlap((r[1], r[2], r[3], r[4]), (a[1], a[2], a[3], a[4]))
            if q > bestr: bestr = q; best = a
        if best is not None and bestr > 0.6:
            ao = max(1, (best[3] - best[1]) * (best[4] - best[2]))
            oo = max(1, (r[3] - r[1]) * (r[4] - r[2]))
            if not (0.25 <= ao / oo <= 3.0):
                new.append(r); continue
            nr = (best[0], r[1], r[2], r[3], r[4], "BOTH", 1.0)
            if best[0] != r[0]:
                changed.append((name, r[0], best[0], bestr))
            new.append(nr)
        else:
            new.append(r)
    for r in new:
        after["bad" if old_garbled(r[0]) else "ok"] += 1
        after["newbad" if garbled(r[0]) else "newok"] += 1

out = ["# OCR 清洗规则评估（task-77b）", "",
       "两条规则（离线模拟真机 50 屏；采集侧规则已进 `PageParser.mergeOcr`）：",
       "1) 同一屏 OCR block 与 A11Y 节点 bbox 重叠 >60% → 采用 A11Y 文本（位置用 OCR bbox）",
       "2) 乱码构成判据：汉字<4 且英文词<2；符号数字占比>40%；无标点无空格长串>20；CJK/拉丁交替碎化≥3 段", "",
       "| 指标 | 改造前 | 改造后 |", "|---|---:|---:|"]
b = before["ok"] + before["bad"]; a = after["ok"] + after["bad"]
out.append("| 总行 | %d | %d |" % (b, a))
out.append("| 旧启发式乱码 | %d (%.1f%%) | %d (%.1f%%) |" % (
    before["bad"], before["bad"] * 100 / max(1, b), after["bad"], after["bad"] * 100 / max(1, a)))
out.append("| 新构成判据乱码 | %d (%.1f%%) | %d (%.1f%%) |" % (
    before["newbad"], before["newbad"] * 100 / max(1, b), after["newbad"], after["newbad"] * 100 / max(1, a)))
out += ["", "## 同屏替换样例（改造前 OCR → 改造后 A11Y 文本）", ""]
seen = set()
for name, old, new, r in changed:
    if old in seen: continue
    seen.add(old)
    out.append("- [%s] `%s` → `%s`（重叠 %.0f%%）" % (name, old[:80], new[:80], r * 100))
    if len(seen) >= 12: break
out += ["", "## 说明", "",
        "- 这是离线用真机 `page_elements` 模拟，不是重新采集；A11Y 重叠替换只作用于未来采集/重新投影。",
        "- 真实收益需重装新 APK 后重新采集 50 屏才能最终确认；报告中的改造后数字是规则模拟值。", ""]
OUT.write_text("\n".join(out), encoding="utf-8")
print("written", OUT, "before_bad", before["bad"], "after_bad", after["bad"], "changed", len(changed))

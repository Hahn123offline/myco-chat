#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""task-73: chrome 清洗 + 每 app 随机 10 条 CONTENT 报告。"""
import random, re, sqlite3, sys
from collections import Counter, defaultdict
from pathlib import Path
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = Path(r"<workspace>"); DB = ROOT / "raw/v2/device/mem_copy.db"
OUT = ROOT / "raw/v2/chrome-report.md"
c = sqlite3.connect(DB); c.text_factory = lambda b: b.decode("utf-8", "replace")
APPS = [("QQ", "com.tencent.mobileqq"), ("B站", "tv.danmaku.bili"),
        ("ChatGPT", "com.openai.chatgpt"), ("微信", "com.tencent.mm"),
        ("浏览器", "com.android.browser"), ("设置", "com.android.settings")]
rows = c.execute("select id,pkg,text,y_top,block_id,object_key from lines").fetchall()
by_pkg = defaultdict(list)
for r in rows: by_pkg[r[1]].append(r)
# 频次：同 pkg 文本出现屏数 / 总屏数
screens = {p: len(set(r[4] for r in xs)) for p, xs in by_pkg.items()}
freq = {}
for p, xs in by_pkg.items():
    cnt = Counter(r[2] for r in xs)
    for t, n in cnt.items():
        if screens[p] >= 3 and n * 100 > screens[p] * 35:
            freq[(p, t)] = n / screens[p]

def chrome(r):
    p, t, y = r[1], (r[2] or "").strip(), r[3]
    if not t: return True
    if y < 120 or y > 2350: return True
    if (p, t) in freq: return True
    if re.match(r"^(返回.*|聊天设置|听筒模式|已读.*|消息未读.*|.*未读\d*)$", t): return True
    if y > 2100 and (re.match(r"^(发送|相册|语音|拍照|表情|更多功能|符号.*|换行|回)$", t)
                     or re.fullmatch(r"[A-Z]( [A-Z])*", t)
                     or re.match(r"^(中っ|L英|确定|取消)$", t)): return True
    if re.match(r"^\d{1,2}:\d{2}", t): return True
    if re.search(r"(5G|4G|KB/s|KB/S|%|电量|信号|运营商)", t): return True
    ascii3 = re.search(r"[A-Za-z]{3,}", t)
    cjk = len(re.findall(r"[\u4e00-\u9fff]", t))
    if not ascii3 and cjk < 2: return True
    return False

kept = {p: [r for r in xs if not chrome(r)] for p, xs in by_pkg.items()}
out = ["# Chrome 清洗 + 泛化随机抽检（task-73）", "",
       "规则：y<120 / >2350；同 pkg 文本出现在 >35% 屏；无 3 连 ASCII 且 <2 汉字；状态/导航/工具条。", ""]
for name, pkg in APPS:
    xs = kept.get(pkg, [])
    uniq = []
    seen = set()
    for r in xs:
        t = r[2].strip()
        if t in seen: continue
        seen.add(t); uniq.append(t)
    random.seed(7 + len(name))
    sample = random.sample(uniq, min(10, len(uniq))) if uniq else []
    out += ["## " + name, "", "保留唯一行 %d，随机 10 条：" % len(uniq), ""]
    for i, s in enumerate(sample, 1): out.append("%d. `%s`" % (i, s[:140].replace("`", "'")))
    out.append("")
OUT.write_text("\n".join(out), encoding="utf-8")
print("written", OUT)
for name, pkg in APPS:
    print(name, "kept", len(kept.get(pkg, [])), "total", len(by_pkg.get(pkg, [])))

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""task-74 真机回填后：用 mem_bf2 的 kind 直接跑 10 题 exact→fuzzy。"""
import re, sqlite3, sys, datetime
from pathlib import Path
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = Path(r"<workspace>"); DB = ROOT / "raw/v2/device/mem_bf2.db"
OUT = ROOT / "raw/v2/answerability-device.md"
c = sqlite3.connect(DB); c.text_factory = lambda b: b.decode("utf-8", "replace")
lines = c.execute("select id,pkg,text,y_top,block_id,time,object_key from lines "
                  "where kind='CONTENT'").fetchall()

def bg(s):
    s = re.sub(r"[^\u4e00-\u9fff]", "", s or "")
    return set(s[i:i+2] for i in range(len(s)-1))

def search(kw):
    ex = [r for r in lines if kw.lower() in (r[2] or "").lower()]
    if ex:
        ex.sort(key=lambda r: -(r[2].lower().count(kw.lower())))
        return [(r, "exact") for r in ex[:3]]
    q = bg(kw); out = []
    for r in lines:
        l = bg(r[2])
        if not q or not l: continue
        ratio = len(q & l) / len(q)
        if ratio >= 0.6: out.append((r, ratio))
    out.sort(key=lambda x: -x[1])
    return [(r, "fuzzy") for r, _ in out[:3]]

QS = [("1 我有没有看到过 blender","blender"),("2 群里谁在说要建模","建模"),
      ("3 我昨天在B站看了什么","B站"),("4 我有没有聊过概率论","概率"),
      ("5 微信扫码报错那个事","微信扫码"),("6 我有没有看过关于反射/折射的内容","反射"),
      ("7 电动车那个群有什么","电动车"),("8 我有没有看到过 tripo","tripo"),
      ("9 我有没有聊过代理/VPN","VPN"),("10 我昨天下午在干嘛","下午")]
out = ["# 真机回填后答案可查性（task-74）", "",
       "数据：`mem_bf2.db`（真机 memory.db 回填后，kind IS NULL=0），只查 CONTENT。", ""]
yes = 0
for q, kw in QS:
    hits = search(kw)
    out += ["## " + q, "", "keyword=`%s` 命中 %d" % (kw, len(hits)), ""]
    for r, m in hits:
        ts = datetime.datetime.fromtimestamp(r[5] / 1000).strftime("%m-%d %H:%M")
        out.append("- `[%s] %s` [block %d] match=%s" % (ts, (r[2] or "")[:150].replace("`","'"), r[4], m))
    out.append("")
OUT.write_text("\n".join(out), encoding="utf-8")
print("written", OUT, "content_lines", len(lines))

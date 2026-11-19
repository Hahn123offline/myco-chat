#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""task-65: 用现有乱数据拼对象（零杯群 / B站视频），只读 evidence.db。"""
import re, sqlite3, sys
from collections import Counter
from datetime import datetime
from pathlib import Path
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = Path(r"<workspace>"); EVID = ROOT / "raw/v2/device/evidence.db"
OUT = ROOT / "raw/v2/stitch-零杯.md"
c = sqlite3.connect(EVID); c.text_factory = lambda b: b.decode("utf-8", "replace")

TIME = re.compile(r"^\d{1,2}:\d{2}(:\d{2})?$")
GROUP = re.compile(r"^[く<〈]?\s*\d{0,3}\s*零杯")
UI = re.compile(r"^(发送|确定|取消|返回|符号|分词|预览|回|q'zhu|"
                r"[A-Z](\s+[A-Z]){2,}|[0-9.]+ ?(KB/s|5G|4G|G)|.*%\s*$|"
                r"\|?强壮|雷霆坐姿!?\?|中っ|L英|P|io|H|B|U|J|N M|KL)$")
SENDER = re.compile(r"^(Hahn123|群主|群成员)\s*[（(]?(离线版|在线)?[)）]?$")

def date_of(ms): return datetime.fromtimestamp(ms / 1000).strftime("%m-%d")

def clean(text):
    """切行 → 去 chrome → 去 UI → 去重连续行。"""
    out = []
    for line in (text or "").splitlines():
        s = line.strip()
        if not s: continue
        if TIME.match(s): continue
        if GROUP.search(s): continue
        if UI.match(s): continue
        if SENDER.match(s): continue
        if re.fullmatch(r"[0-9.,]+", s): continue
        if re.fullmatch(r"[^\w\u4e00-\u9fff]{1,3}", s): continue
        s = re.sub(r"\s+", " ", s)
        if len(s) < 2: continue
        if out and out[-1] == s: continue
        out.append(s)
    return out


SENDER_LINE = re.compile(r"(Hahn123|群主|群成员|25-鄂|25-号鄂)")
def clean_chat(text):
    """只保留「发送者行 + 下一行消息」，得到可读对话。"""
    ls = [x.strip() for x in (text or "").splitlines() if x.strip()]
    out = []
    for i, line in enumerate(ls):
        if GROUP.search(line) or TIME.match(line): continue
        spk = None
        if "Hahn123" in line: spk = "我"
        elif SENDER_LINE.search(line): spk = "群主"
        if spk is None: continue
        # 找下一行非 UI 消息
        for j in range(i + 1, min(len(ls), i + 4)):
            msg = ls[j].strip()
            if not msg or TIME.match(msg) or GROUP.search(msg) or UI.match(msg): continue
            if SENDER_LINE.search(msg): break
            msg = re.sub(r"\s+", " ", msg)
            if len(msg) >= 2:
                out.append(spk + "：" + msg)
            break
    # 去连续重复
    res = []
    for x in out:
        if not res or res[-1] != x: res.append(x)
    return res

def lcs(a, b):
    """最长公共连续行；返回 (i,j,len)。"""
    best = (0, 0, 0)
    dp = [[0] * (len(b) + 1) for _ in range(len(a) + 1)]
    for i in range(1, len(a) + 1):
        for j in range(1, len(b) + 1):
            if a[i - 1] == b[j - 1]:
                dp[i][j] = dp[i - 1][j - 1] + 1
                if dp[i][j] > best[2]: best = (i - dp[i][j], j - dp[i][j], dp[i][j])
    return best

def stitch(blocks, parser=clean):
    """acc: list[(line, src)]；返回拼接、统计。"""
    acc = []; stats = Counter(); unaligned = []
    for bid, t0, text in blocks:
        lines = parser(text)
        if not lines: stats["空块"] += 1; continue
        if not acc:
            acc = [(x, bid) for x in lines]; stats["种子块"] += 1; continue
        a = [x[0] for x in acc]
        i, j, n = lcs(a, lines)
        if n >= 3:
            stats["对齐块"] += 1
            if i + n < len(acc) and j + n < len(lines):
                stats["中间插入"] += 1
            if j > 0:
                for k in range(j - 1, -1, -1):   # 回滚：块前面还有更早内容
                    if lines[k] not in a:
                        acc.insert(i, (lines[k], bid)); a.insert(i, lines[k]); stats["回滚插入"] += 1
            if j + n < len(lines):
                for k in range(j + n, len(lines)):
                    if lines[k] not in a:
                        pos = i + n + (k - (j + n))
                        acc.insert(pos, (lines[k], bid)); a.insert(pos, lines[k])
                        stats["追加行"] += 1
        else:
            stats["未对齐块"] += 1
            unaligned.append((bid, len(lines), lines[:3]))
            for x in lines:
                if x not in a: acc.append((x, bid)); a.append(x); stats["断口追加"] += 1
            stats["断口"] += 1
    return acc, stats, unaligned

def render(name, blocks, out_lines, meta):
    parser = clean_chat if meta.get("chat") else clean
    acc, stats, unaligned = stitch(blocks, parser)
    days = sorted(set(date_of(t) for _, t, _ in blocks))
    out_lines.append("## " + name)
    out_lines.append("")
    out_lines.append("对象块数 %d，跨天 %s，输出唯一行 %d，断口 %d，回滚插入 %d，未对齐 %d。"
                     % (len(blocks), "/".join(days), len(acc), stats["断口"],
                        stats["回滚插入"], stats["未对齐块"]))
    out_lines.append("")
    for line, bid in acc:
        out_lines.append("- `[id=%d]` %s" % (bid, line))
    out_lines.append("")
    out_lines.append("### 拼歪/未对齐")
    out_lines.append("")
    if not unaligned: out_lines.append("无。")
    for bid, n, head in unaligned[:20]:
        out_lines.append("- id=%d（%d 行）头三行：%s" % (bid, n, " / ".join(head)))
    out_lines.append("")
    return stats

zero = c.execute("select id,t0,text from traces where pkg='com.tencent.mobileqq' "
                 "and text like '%零杯%' order by t0").fetchall()
bili = c.execute("select id,t0,text from traces where pkg='tv.danmaku.bili' "
                 "and text like '%评论 2402%' order by t0").fetchall()
if not bili:
    bili = c.execute("select id,t0,text from traces where pkg='tv.danmaku.bili' "
                     "and t0 between 1794844800000 and 1794931200000 order by t0 limit 40").fetchall()

out = ["# 对象拼接验证 2026-11-19", "",
       "只读 evidence.db；单位从「一屏」改为「对象」，用最长公共行对齐。", "",
       "零杯：群标题里的数字是未读数，已剥掉；只取 QQ 且文本含「零杯」。", ""]
s1 = render("零杯群", zero, out, {"chat": True})
s2 = render("B站视频（评论 2402）", bili, out, {})
out += ["## 汇总", "",
        "- 零杯：%d 块 → %d 唯一行，跨 %d 天，断口 %d，回滚插入 %d，未对齐 %d" % (
            len(zero), len(stitch(zero, clean_chat)[0]), len(set(date_of(x[1]) for x in zero)),
            s1["断口"], s1["回滚插入"], s1["未对齐块"]),
        "- B站：%d 块 → %d 唯一行，断口 %d，未对齐 %d" % (
            len(bili), len(stitch(bili)[0]), s2["断口"], s2["未对齐块"]), ""]
OUT.write_text("\n".join(out), encoding="utf-8")
print("written", OUT)
print("zero blocks", len(zero), "lines", len(stitch(zero, clean_chat)[0]), "stats", dict(s1))
print("bili blocks", len(bili), "lines", len(stitch(bili)[0]), "stats", dict(s2))

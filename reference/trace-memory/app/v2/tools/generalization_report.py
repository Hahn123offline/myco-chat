#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""task-72 D: 泛化度量（按 app 投影覆盖率/CONTENT 覆盖率/object 数）。"""
import re, sqlite3, sys
from collections import Counter, defaultdict
from pathlib import Path
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = Path(r"<workspace>")
MEM = ROOT / "raw/v2/device/mem_copy.db"
EVID = ROOT / "raw/v2/device/ev_copy.db"
OUT = ROOT / "raw/v2/generalization-report.md"
mc = sqlite3.connect(MEM); mc.text_factory = lambda b: b.decode("utf-8", "replace")
ec = sqlite3.connect(EVID); ec.text_factory = lambda b: b.decode("utf-8", "replace")
TR = {r[0]: r for r in ec.execute("select id,pkg,page_title,activity,object_key from traces")}
APPS = [("QQ", "com.tencent.mobileqq"), ("B站", "tv.danmaku.bili"),
        ("ChatGPT", "com.openai.chatgpt"), ("微信", "com.tencent.mm"),
        ("浏览器", "com.android.browser"), ("设置", "com.android.settings")]

def cls(t, y, title):
    t = (t or "").strip()
    if not t: return "CONTENT"
    if title and t == title: return "TITLE"
    if y < 300 and (re.match(r"^\d{1,2}:\d{2}", t)
                    or re.search(r"(5G|4G|KB/s|KB/S|%|电量|信号|运营商)", t)
                    or "WLAN" in t or "wifi" in t.lower()):
        return "STATUS"
    if re.match(r"^(返回.*|聊天设置|听筒模式|已读.*|消息未读.*|.*未读\d*)$", t): return "NAV"
    if y > 2100 and (re.match(r"^(发送|相册|语音|拍照|表情|更多功能|符号.*|换行|回)$", t)
                     or re.fullmatch(r"[A-Z]( [A-Z])*", t)
                     or re.match(r"^(中っ|L英|确定|取消)$", t)): return "TOOLBAR"
    if re.search(r"(加入了群聊|拍了拍|上线了|撤回了一条消息)", t): return "SYSTEM"
    return "CONTENT"

def first_nonchrome(lines):
    multi = sum(1 for x in lines if ", ," in x[1])
    if multi >= 2: return ""
    for _, t, y in lines:
        if len(t) >= 4 and len(re.findall(r"[\u4e00-\u9fff]", t)) >= 3 and cls(t, y, "") == "CONTENT":
            return t
    return ""

rows = mc.execute("select id,object_key,pkg,time,text,block_id,shot_ref,line_no,y_top from lines").fetchall()
by_shot = defaultdict(list)
for r in rows: by_shot[r[6]].append(r)
obj_by_shot = {}
for shot, xs in by_shot.items():
    tr = TR.get(xs[0][5])
    pkg = xs[0][2] or (tr[1] if tr else "")
    cur = xs[0][1] if xs[0][1] and not xs[0][1].endswith("/") else ""
    if cur: obj_by_shot[shot] = cur; continue
    title = (tr[2] if tr else "") or ""
    ok = ""
    if pkg and title: ok = pkg + "/" + title
    if not ok and tr:
        act = tr[3] or ""
        a = act.rsplit(".", 1)[-1] if act else ""
        if len(a) >= 4 and not any(x in a for x in ("Splash", "MainActivity", "FrameLayout", "Launcher", "ComponentDialog")):
            ok = pkg + "/" + a
    if not ok:
        f = first_nonchrome([(x[0], x[4], x[8]) for x in xs])
        if f and pkg: ok = pkg + "/" + f
    obj_by_shot[shot] = ok
by_pkg = defaultdict(lambda: dict(lines=0, content=0, with_obj=0, objects=set(), samples=[]))
for r in rows:
    pkg = r[2] or ""
    kind = cls(r[4], r[8], "")
    ok = obj_by_shot.get(r[6], "")
    if kind == "TITLE": kind = "TITLE"
    d = by_pkg[pkg]
    d["lines"] += 1
    if kind == "CONTENT": d["content"] += 1
    if ok: d["with_obj"] += 1; d["objects"].add(ok)
    if kind == "CONTENT" and len(d["samples"]) < 3 and len(r[4] or "") >= 4:
        d["samples"].append(r[4])
out = ["# 泛化度量（task-72）", "", "数据：`raw/v2/device/mem_copy.db` 的 20,620 行；object_key 用 fallback 链重算；kind 规则分类。", "",
       "| app | 痕迹 | 投影行 | CONTENT 行 | CONTENT 占比 | 有 object_key 行 | object 覆盖率 | 不同 object |",
       "|---|---:|---:|---:|---:|---:|---:|---:|"]
for name, pkg in APPS:
    d = by_pkg.get(pkg, dict(lines=0, content=0, with_obj=0, objects=set(), samples=[]))
    tr = ec.execute("select count(*) from traces where pkg=?", (pkg,)).fetchone()[0]
    l = d["lines"]
    out.append("| %s | %d | %d | %d | %.1f%% | %d | %.1f%% | %d |" % (
        name, tr, l, d["content"], d["content"] * 100 / max(1, l),
        d["with_obj"], d["with_obj"] * 100 / max(1, l), len(d["objects"])))
out += ["", "## 可读样例（每 app 3 条 CONTENT）", ""]
for name, pkg in APPS:
    d = by_pkg.get(pkg, {})
    out.append("### " + name)
    for s in d.get("samples", []): out.append("- " + s[:120].replace("\n", " "))
    out.append("")
# 结论
qual = []
for name, pkg in APPS:
    d = by_pkg.get(pkg, dict(lines=0, content=0, with_obj=0))
    if d["lines"] < 20: continue
    if d["with_obj"] * 100 / d["lines"] > 50 and d["content"] * 100 / d["lines"] > 40:
        qual.append(name)
out += ["## 结论", "", "满足「object_key 覆盖率>50%% 且 CONTENT 占比>40%%」的 app：**%s**。" % (
    "、".join(qual) if qual else "无"), ""]
out.append("非聊天 app 达标数：%d / 6（目标 3 个非聊天 app）。" % len([x for x in qual if x != "QQ"]))
if len([x for x in qual if x != "QQ"]) < 3:
    out.append("按判断标准：**通用层尚未立住，需要换思路**。")
else:
    out.append("按数字阈值：通用层达标；但可读样例质量仍需人工复核，不能只看覆盖率。")
OUT.write_text("\n".join(out), encoding="utf-8")
print("written", OUT, "qual", qual)
for name, pkg in APPS:
    d = by_pkg.get(pkg, dict(lines=0, content=0, with_obj=0))
    print(name, d["lines"], d["content"], d["with_obj"])

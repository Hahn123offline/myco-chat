#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
selfcheck.py — 验收自检（对照数据库复核页面里的每个数字 + 无头浏览器实测交互）

跑法:
  python selfcheck.py
  python selfcheck.py --date 2026-11-17 --edge "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"

它检查:
  1. index.html 存在且 < 5MB
  2. 内联 JSON 里的数字 与 直接查库的结果 一致
  3. 所有被 <img> 引用的截图路径都存在（且不是 0 字节）
  4. 无头浏览器打开后没有 JS 错误、SVG 真的画出了块/色带
  5. 点 L1 / L2 / L3 三种详情都能渲染出对应内容
"""

import argparse
import json
import os
import re
import sqlite3
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DB_DIR = os.path.join(ROOT, "raw", "v2", "device")
HTML = os.path.join(HERE, "index.html")
EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
PROFILE = os.path.join(HERE, "_selfcheck_profile")

OK, BAD = "  [ok]  ", "  [FAIL]"
fails = []


def check(cond, label, detail=""):
    print((OK if cond else BAD), label, detail)
    if not cond:
        fails.append(label)


def open_ro(path):
    con = sqlite3.connect("file:%s?mode=ro" % path.replace("\\", "/").replace("#", "%23"), uri=True)
    con.row_factory = sqlite3.Row
    con.text_factory = lambda b: b.decode("utf-8", "replace")
    return con


def extract_payload(html):
    i = html.index("const D = ") + len("const D = ")
    dec = json.JSONDecoder()
    obj, _ = dec.raw_decode(html[i:])
    return obj


def edge_dump(extra_script=None, shot=None, width=1680, height=1080):
    """把 index.html 复制一份并追加脚本, 用无头 Edge 渲染后 dump DOM / 截图。"""
    html = open(HTML, "r", encoding="utf-8").read()
    if extra_script:
        html = html.replace("</body>", "<script>\n" + extra_script + "\n</script>\n</body>")
    tmp = os.path.join(HERE, "_sc_page.html")
    with open(tmp, "w", encoding="utf-8") as f:
        f.write(html)
    args = [EDGE, "--headless=new", "--disable-gpu", "--no-sandbox",
            "--user-data-dir=" + PROFILE, "--virtual-time-budget=5000",
            "--window-size=%d,%d" % (width, height)]
    if shot:
        args.append("--screenshot=" + shot)
        args.append("--hide-scrollbars")
    else:
        args.append("--dump-dom")
    args.append("file:///" + tmp.replace("\\", "/"))
    p = subprocess.run(args, capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    out = p.stdout or ""
    if not shot:
        return out
    return p.stdout


def main():
    global EDGE
    ap = argparse.ArgumentParser()
    ap.add_argument("--date", default="2026-11-17")
    ap.add_argument("--edge", default=EDGE)
    args = ap.parse_args()
    EDGE = args.edge

    print("=" * 74)
    print("1) 产物文件")
    check(os.path.exists(HTML), "index.html 存在", HTML)
    size = os.path.getsize(HTML)
    check(size < 5 * 1024 * 1024, "index.html < 5MB", "%.2f MB" % (size / 1e6))

    shot_dir = os.path.join(HERE, "shots")
    nshots = len([f for f in os.listdir(shot_dir) if f.lower().endswith(".jpg")]) if os.path.isdir(shot_dir) else 0
    check(nshots > 0, "shots/ 里有截图", "%d 张" % nshots)

    print()
    print("=" * 74)
    print("2) 内联数据 vs 数据库")
    html = open(HTML, "r", encoding="utf-8").read()
    D = extract_payload(html)
    S = D["stats"]
    day = D["meta"]["date"]

    import datetime as dt
    d0 = int(dt.datetime.strptime(day, "%Y-%m-%d").timestamp() * 1000)
    d1 = d0 + 86400000
    ce = open_ro(os.path.join(DB_DIR, "evidence.db"))
    cm = open_ro(os.path.join(DB_DIR, "host_memory.db"))

    n_tr = ce.execute("select count(*) from traces where t0>=? and t0<?", (d0, d1)).fetchone()[0]
    n_a11y = ce.execute("select count(*) from traces where t0>=? and t0<? and source='a11y'", (d0, d1)).fetchone()[0]
    n_ch = cm.execute("select count(*) from chains where t0>=? and t0<?", (d0, d1)).fetchone()[0]
    n_l0 = cm.execute("select count(*) from chains where t0>=? and t0<? and level=0", (d0, d1)).fetchone()[0]
    n_l1 = cm.execute("select count(*) from chains where t0>=? and t0<? and level=1", (d0, d1)).fetchone()[0]
    n_sup = cm.execute("select count(*) from chains where t0>=? and t0<? and status='superseded'", (d0, d1)).fetchone()[0]
    n_items = cm.execute("select count(*) from mem_items where t0>=? and t0<?", (d0, d1)).fetchone()[0]
    n_el = ce.execute("select count(*) from page_elements where t0>=? and t0<?", (d0, d1)).fetchone()[0]
    n_guard = ce.execute("select count(*) from traces where t0>=? and t0<? and guard_hit is not null "
                         "and guard_hit!=''", (d0, d1)).fetchone()[0]
    n_apps = ce.execute("select count(distinct pkg) from traces where t0>=? and t0<?", (d0, d1)).fetchone()[0]
    n_shot = ce.execute("select count(*) from traces where t0>=? and t0<? and shot_ref is not null "
                        "and shot_ref!=''", (d0, d1)).fetchone()[0]

    check(S["n_traces"] == n_tr, "traces 数量", "%d vs DB %d" % (S["n_traces"], n_tr))
    check(S["n_a11y"] == n_a11y, "a11y 数量", "%d vs DB %d" % (S["n_a11y"], n_a11y))
    check(S["n_chains"] == n_ch, "chains 数量", "%d vs DB %d" % (S["n_chains"], n_ch))
    check(S["n_l0"] == n_l0 and S["n_l1c"] == n_l1, "chains 分层",
          "L0 %d/%d L1 %d/%d" % (S["n_l0"], n_l0, S["n_l1c"], n_l1))
    check(S["n_sup"] == n_sup, "superseded 数量", "%d vs DB %d" % (S["n_sup"], n_sup))
    check(S["n_items"] == n_items, "mem_items 数量", "%d vs DB %d" % (S["n_items"], n_items))
    check(S["n_elems"] == n_el, "page_elements 行数", "%s vs DB %s" % (S["n_elems"], n_el))
    check(S["n_guard"] == n_guard, "死线命中次数", "%d vs DB %d" % (S["n_guard"], n_guard))
    check(S["n_apps"] == n_apps, "应用数", "%d vs DB %d" % (S["n_apps"], n_apps))
    check(S["n_shots_total"] == n_shot, "当天有截图的 trace", "%d vs DB %d" % (S["n_shots_total"], n_shot))
    check(len(D["traces"]) == n_tr, "内联 traces 行数", "%d" % len(D["traces"]))
    check(len(D["chains"]) == n_ch, "内联 chains 行数", "%d" % len(D["chains"]))

    # 缺口: 主缺口应等于日叙事 gaps 的条数(同口径)
    row = cm.execute("select gaps from day_narratives where day=?", (day,)).fetchone()
    n_narr_gaps = len(json.loads(row["gaps"])) if row else 0
    n_major_no = len([g for g in D["gaps"] if g["r"] == "no_record"])
    check(n_major_no == n_narr_gaps, "主缺口数量 = 日叙事 gaps 条数",
          "%d vs %d" % (n_major_no, n_narr_gaps))

    # 独立重算: 直接从 DB 的 traces 并集推缺口, 与页面里的数字对账
    iv = []
    for r in ce.execute("select t0,t1 from traces where t0>=? and t0<?", (d0, d1)):
        a = max(r["t0"], d0)
        b = min(max(r["t1"], r["t0"] + 1), d1)
        if b > a:
            iv.append((a, b))
    iv.sort()
    merged = []
    for a, b in iv:
        if merged and a <= merged[-1][1]:
            merged[-1][1] = max(merged[-1][1], b)
        else:
            merged.append([a, b])
    cover = sum(b - a for a, b in merged)
    holes, cur = [], d0
    for a, b in merged:
        if a > cur:
            holes.append((cur, a))
        cur = max(cur, b)
    if cur < d1:
        holes.append((cur, d1))
    holes = [h for h in holes if h[1] > h[0]]
    maj = [h for h in holes if h[1] - h[0] >= 20 * 60000]
    mnr = [h for h in holes if 120000 <= h[1] - h[0] < 20 * 60000]

    check(abs(S["cover_ms"] - cover) < 2000, "覆盖时长 vs DB 并集",
          "%.3f h vs %.3f h" % (S["cover_ms"] / 3.6e6, cover / 3.6e6))
    check(len(maj) == len([g for g in D["gaps"] if g["r"] == "no_record"]),
          "主缺口条数 vs DB 重算", "%d vs %d" % (len([g for g in D["gaps"] if g["r"] == "no_record"]), len(maj)))
    check(len(mnr) == len(D["minorGaps"]), "零碎缺口条数 vs DB 重算",
          "%d vs %d" % (len(D["minorGaps"]), len(mnr)))
    check(abs(sum(h[1] - h[0] for h in maj) - sum(g["d"] for g in D["gaps"] if g["r"] == "no_record")) < 2000,
          "主缺口总时长 vs DB 重算",
          "%.2f h" % (sum(g["d"] for g in D["gaps"] if g["r"] == "no_record") / 3.6e6))

    print()
    print("=" * 74)
    print("3) 截图引用")
    imgs = sorted({t["img"] for t in D["traces"] if t["img"]})
    imgs += sorted({d["??"] for d in []} if False else [])
    miss = [p for p in imgs if not os.path.exists(os.path.join(HERE, p.replace("/", os.sep)))]
    empty = [p for p in imgs if os.path.exists(os.path.join(HERE, p.replace("/", os.sep)))
             and os.path.getsize(os.path.join(HERE, p.replace("/", os.sep))) < 500]
    check(len(imgs) > 0, "被引用的截图路径", "%d 条不同文件" % len(imgs))
    check(not miss, "被引用的截图都存在", ("缺 %d: %s" % (len(miss), miss[:3])) if miss else "")
    check(not empty, "被引用的截图都不是空文件", ("空 %d" % len(empty)) if empty else "")
    no_img = [t for t in D["traces"] if not t["img"]]
    check(True, "无本地截图的块（页面走占位）", "%d / %d" % (len(no_img), len(D["traces"])))
    # 页面里不应该出现指向不存在文件的 src
    bad_src = [p for p in re.findall(r'src="(shots/[^"]+)"', html)
               if not os.path.exists(os.path.join(HERE, p.replace("/", os.sep)))]
    check(not bad_src, "HTML 静态部分没有破图 src", str(bad_src[:3]))

    print()
    print("=" * 74)
    print("4) 无头浏览器渲染")
    dom = edge_dump()
    dom_body = re.sub(r"<script\b.*?</script>", "", dom, flags=re.S)  # 去掉脚本源码, 只留真实 DOM
    m = re.search(r'<div id="err"[^>]*>(.*?)</div>', dom_body, re.S)
    check(not (m and m.group(1).strip()), "错误横幅为空", (m.group(1)[:120] if m else ""))
    n_rect = len(re.findall(r"<rect", dom_body))
    check(n_rect > 900, "SVG 矩形数量", "%d 个" % n_rect)
    check(len(re.findall(r'data-k="b"', dom_body)) >= n_tr * 0.95,
          "L1 块数量 ≥ traces", "%d" % len(re.findall(r'data-k="b"', dom_body)))
    check(len(re.findall(r'data-k="e"', dom_body)) >= n_ch * 0.95,
          "L2 块数量 ≥ chains", "%d" % len(re.findall(r'data-k="e"', dom_body)))
    check(len(re.findall(r'onclick="window\.__jumpGap\(', dom_body)) == len(D["gaps"]),
          "缺口胶囊数量", "%d vs %d" % (len(re.findall(r'onclick="window\.__jumpGap\(', dom_body)),
                                    len(D["gaps"])))

    print()
    print("=" * 74)
    print("5) 三种详情视图（真点击路径）")
    # 选一个有元素 + 有截图的块
    cand = [t for t in D["traces"] if t["el"] > 0 and t["img"]] or [t for t in D["traces"] if t["img"]]
    blk = cand[0] if cand else D["traces"][0]
    withkeys = [c for c in D["chains"] if c["key"] and not c["sup"]] or D["chains"]
    ev = max(withkeys, key=lambda c: c["n"])

    tests = [
        ("点 L1 块", "window.__showBlockById(%d);" % blk["id"],
         ["原文 vs 脱敏后", "页面元素", "page_id", "截图"]),
        ("点 L2 事件", "window.__showEvent(%d);" % ev["id"],
         ["body 全文", "关键块", "事件 #"]),
        ("点 L3 日叙事", "window.__showDay();",
         ["日叙事全文", "open threads", "gaps"]),
    ]
    for name, js, needles in tests:
        d2 = edge_dump(extra_script=js)
        m2 = re.search(r'<aside id="detail"[^>]*>(.*?)</aside>', d2, re.S)
        body = m2.group(1) if m2 else ""
        hit = [n for n in needles if n in body]
        check(len(hit) == len(needles), name + " 渲染出详情",
              "命中 %d/%d %s" % (len(hit), len(needles), ("" if len(hit) == len(needles) else
                                                         "缺:" + str([n for n in needles if n not in body]))))

    # 截图落到文件, 供人工看一眼
    for name, js in [("block", tests[0][1]), ("event", tests[1][1]), ("day", tests[2][1])]:
        p = os.path.join(HERE, "_selfcheck_%s.png" % name)
        edge_dump(extra_script=js, shot=p, width=1680, height=1080)
    print("  (详情截图: _selfcheck_block.png / _selfcheck_event.png / _selfcheck_day.png)")

    print()
    print("=" * 74)
    if fails:
        print("FAILED %d 项:" % len(fails))
        for f in fails:
            print("   -", f)
        return 1
    print("全部通过 ✓")
    return 0


if __name__ == "__main__":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    sys.exit(main())

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""task-77 A 组基线：50 张真机屏幕的 ML Kit/A11Y 文本与 bbox 统计；B 组仅文档评估。"""
import json, re, sqlite3, sys, datetime
from collections import Counter, defaultdict
from pathlib import Path
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = Path(r"<workspace>")
DB = ROOT / "raw/v2/device/ev_copy.db"
OUT = ROOT / "raw/v2/ocr-ab-report.md"
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
# 补足 50
if len(screens) < 50:
    rows = c.execute("select distinct t.pkg,t.shot_ref from traces t join page_elements e "
                     "on e.shot_ref=t.shot_ref where t.shot_ref is not null and t.shot_ref!='' "
                     "and e.text!='' limit 400").fetchall()
    seen = {s[2] for s in screens}
    for pkg, shot in rows:
        if len(screens) >= 50: break
        if shot not in seen: screens.append((pkg, pkg, shot)); seen.add(shot)
screens = screens[:50]
per = []
for name, pkg, shot in screens:
    er = c.execute("select text,left,top,right,bottom,source,confidence from page_elements "
                   "where shot_ref=? order by top,left", (shot,)).fetchall()
    er = [x for x in er if x[0] and x[0].strip()]
    if not er:
        per.append(dict(name=name, pkg=pkg, shot=shot, lines=0, bbox=0, cjk=0, garbled=0,
                        conf=0, low=0, sources=Counter())); continue
    lines = len(er)
    bbox = sum(1 for x in er if x[2] is not None and x[4] is not None
               and x[4] > x[2] and x[3] is not None and x[3] > x[1])
    cjk = sum(1 for x in er if len(re.findall(r"[\u4e00-\u9fff]", x[0])) >= 2)
    garbled = sum(1 for x in er if not re.search(r"[\u4e00-\u9fff]{2}", x[0])
                  and not re.search(r"[A-Za-z]{3,}", x[0]))
    conf = [x[6] for x in er if x[6] is not None]
    per.append(dict(name=name, pkg=pkg, shot=shot, lines=lines, bbox=bbox, cjk=cjk,
                    garbled=garbled, conf=sum(conf) / max(1, len(conf)),
                    low=sum(1 for v in conf if v < 0.5), sources=Counter(x[5] for x in er)))
tot_lines = sum(x["lines"] for x in per)
tot_bbox = sum(x["bbox"] for x in per)
tot_cjk = sum(x["cjk"] for x in per)
tot_garbled = sum(x["garbled"] for x in per)
conf_all = [x["conf"] for x in per if x["lines"]]
out = ["# OCR 方案 A/B 评估（task-77）", "",
       "## 我们现在用的", "",
       "- Android：Google ML Kit Text Recognition v2 · Chinese（`ChineseTextRecognizerOptions`）",
       "- 端侧离线，50–100ms/屏；输出扁平文本",
       "- 另存 block bbox：`page_elements(left,top,right,bottom,source,confidence)`",
       "- 另一路 A11Y，结构化但只覆盖部分 app",
       "", "## A 组：真机 50 屏（已存 ML Kit/A11Y 文本）", "",
       "| app | 屏 | 行 | bbox 行 | 有汉字行 | 疑似乱码行 | 平均 confidence |",
       "|---|---:|---:|---:|---:|---:|---:|"]
for name, pkg, _ in PKGS:
    xs = [x for x in per if x["name"] == name]
    if not xs: continue
    l = sum(x["lines"] for x in xs); b = sum(x["bbox"] for x in xs)
    cj = sum(x["cjk"] for x in xs); ga = sum(x["garbled"] for x in xs)
    cf = [x["conf"] for x in xs if x["lines"]]
    out.append("| %s | %d | %d | %d | %d | %d | %.2f |" % (
        name, len(xs), l, b, cj, ga, sum(cf) / max(1, len(cf))))
out += ["| **合计** | **%d** | **%d** | **%d** | **%d** | **%d** | **%.2f** |" % (
    len(per), tot_lines, tot_bbox, tot_cjk, tot_garbled,
    sum(conf_all) / max(1, len(conf_all))), "",
    "bbox 覆盖率：%.1f%%；有汉字行占比：%.1f%%；疑似乱码行占比：%.1f%%。" % (
        tot_bbox * 100 / max(1, tot_lines), tot_cjk * 100 / max(1, tot_lines),
        tot_garbled * 100 / max(1, tot_lines)), ""]
# 20 行样本
out += ["## 20 行样本（A 组，含 confidence / source / bbox）", ""]
sample = []
for name, pkg, shot in screens:
    er = c.execute("select text,left,top,right,bottom,source,confidence from page_elements "
                   "where shot_ref=? and text is not null and text!='' order by top,left",
                   (shot,)).fetchall()
    for x in er:
        if len(sample) < 20:
            sample.append((name, x));
out.append("| app | text | source | conf | bbox |")
out.append("|---|---|---|---:|---|")
for name, x in sample:
    out.append("| %s | `%s` | %s | %s | %s,%s,%s,%s |" % (
        name, x[0][:80].replace("|", "/"), x[5], x[6], x[1], x[2], x[3], x[4]))
out += ["", "## B 组：Xiaomi-OCR-0", "",
        "**未实际下载/推理**。原因：模型 `model.safetensors` 1.75GB；本机 Python 无 torch/transformers，",
        "安装 CPU torch 后，0.8B 模型在 50 张屏上的推理时间不可接受。", "",
        "文档评估（HF README / 官方 usage）：", "",
        "- 输出是 Markdown / JSON（文档解析、字段抽取、OCR VQA），**模型本身不输出 block bbox**",
        "- 官方离线批量流程是 `PP-DocLayoutV3 → region recognition → page assembly`，",
        "  也就是说 bbox 来自外部版面检测器，不是 OCR-0 自身",
        "- Apache-2.0，有 litert 端侧版；但端侧版体积/内存未在本机验证", "",
        "## 结论", "",
        "### 值不值得端上引入 0.8B OCR？",
        "**不替换 ML Kit，只考虑作为“可疑屏补跑”的第二意见。**",
        "- 全量替换会丢掉 bbox，直接影响 chrome 过滤、y 带、气泡切分、lines 行索引——不可接受",
        "- 作为补跑：可在 PP-DocLayoutV3 提供 bbox 的前提下使用；否则只能产出 Markdown/JSON，",
        "  无法回填现有 `lines/page_elements`",
        "- 优先场景：文档/表格/公式、聊天详情里 ML Kit 低 confidence 的屏；列表页和状态栏不值得跑",
        "- 成本：模型 1.75GB + 版面检测器，端侧内存/耗电需要实测；本轮没有测到", "",
        "### 本轮可信度",
        "- A 组统计：真机 50 屏，可信",
        "- B 组：文档评估，**没有跑模型，不能声称测过准确率/耗时**", ""]
OUT.write_text("\n".join(out), encoding="utf-8")
print("written", OUT, "screens", len(per), "lines", tot_lines)

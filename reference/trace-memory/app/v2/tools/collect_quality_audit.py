#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""task-64 采集能力审计（只读本地 DB，不调模型）。"""
import json, math, re, sqlite3, sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = Path(r"<workspace>")
EVID = ROOT / "raw/v2/device/evidence.db"
MEM = ROOT / "raw/v2/device/host_memory.db"
OUT = ROOT / "raw/v2/采集质量审计-2026-11-17.md"
DAY = "2026-11-17"
d = datetime.strptime(DAY, "%Y-%m-%d")
start = int(d.timestamp() * 1000)
end = start + 24 * 3600 * 1000
DAY_MIN = 1440

def union_len(spans):
    if not spans: return 0
    spans = sorted(spans)
    s, e, total = spans[0][0], spans[0][1], 0
    for a, b in spans[1:]:
        if a <= e: e = max(e, b)
        else: total += e - s; s, e = a, b
    return total + e - s

def gaps(spans, lo, hi, min_ms=60_000):
    spans = sorted([(max(a, lo), min(b, hi)) for a, b in spans if b > lo and a < hi])
    out = []; cur = lo
    for a, b in spans:
        if a - cur >= min_ms: out.append((cur, a))
        cur = max(cur, b)
    if hi - cur >= min_ms: out.append((cur, hi))
    return out

def bigrams(s):
    s = re.sub(r"\s+", "", s or "")
    return set(s[i:i + 2] for i in range(max(0, len(s) - 1)))

def jacc(a, b):
    if not a or not b: return 0.0
    return len(a & b) / len(a | b)

ec = sqlite3.connect(EVID); mc = sqlite3.connect(MEM)
ec.text_factory = lambda b: b.decode("utf-8", "replace")
mc.text_factory = lambda b: b.decode("utf-8", "replace")
rows = ec.execute("select id,t0,t1,dwell_ms,pkg,activity,page_id,text,shot_ref,source "
                  "from traces where t1>? and t0<? order by t0", (start, end)).fetchall()
all_rows = rows
a11y = [r for r in rows if r[9] != "usage"]
usage = [r for r in rows if r[9] == "usage"]
span_a = [(r[1], r[2]) for r in a11y]
span_u = [(r[1], r[2]) for r in usage]
cov_a = union_len(span_a) / 60000
cov_u = union_len(span_u) / 60000
cov_both = union_len(span_a + span_u) / 60000
img_rows = [r for r in a11y if r[8]]
txt_rows = [r for r in a11y if r[7] and len(r[7].strip()) >= 4]
cov_img = union_len([(r[1], r[2]) for r in img_rows]) / 60000
cov_txt = union_len([(r[1], r[2]) for r in txt_rows]) / 60000
by_pkg = defaultdict(list)
for r in a11y: by_pkg[r[4] or "(null)"].append((r[1], r[2]))
pkg_cov = sorted(((p, union_len(v) / 60000) for p, v in by_pkg.items()), key=lambda x: -x[1])
gaps_list = gaps(span_a + span_u, start, end)
seg = lambda ms: datetime.fromtimestamp(ms / 1000).strftime("%H:%M")

# M3
lines = Counter(); chrome = Counter(); eff_total = 0
chrome_re = re.compile(r"^(\d{1,2}:\d{2}(:\d{2})?|.*(%|信号|电量|输入法|候选|返回|首页|菜单).*)$")
for r in a11y:
    ls = [x.strip() for x in (r[7] or "").splitlines() if x.strip()]
    keep = [x for x in ls if not chrome_re.match(x)]
    eff_total += sum(len(x) for x in keep)
    for x in ls:
        lines[x] += 1
        if chrome_re.match(x): chrome[x] += 1
repeats = []
for i in range(1, len(a11y)):
    if a11y[i][4] == a11y[i - 1][4]:
        repeats.append(jacc(bigrams(a11y[i][7]), bigrams(a11y[i - 1][7])))
avg_rep = sum(repeats) / len(repeats) if repeats else 0

# page_elements
pe_by_pkg = defaultdict(list)
pe_all = ec.execute("select t0,pkg,text from page_elements").fetchall()
pe_by_pkg.clear()
for t0, pkg, txt in pe_all: pe_by_pkg[pkg].append((t0, txt or ""))
def elements_for(r, window=30_000):
    return [x for x in pe_by_pkg.get(r[4], []) if r[1] - window <= x[0] <= r[2] + window]
ocr_cov = []; pe_blocks = 0; ocr_chars = 0; pe_chars = 0
for r in a11y:
    els = elements_for(r)
    if els: pe_blocks += 1; pe_chars += sum(len(x[1]) for x in els)
    ocr_chars += len(r[7] or "")
    if els:
        pb = set()
        for _, tx in els: pb |= bigrams(tx)
        ob = bigrams(r[7])
        if ob: ocr_cov.append(len(ob & pb) / len(ob))
avg_ocr_cov = sum(ocr_cov) / len(ocr_cov) if ocr_cov else 0

# M4
day_chains = mc.execute("select id,title,body,block_ids,t0,t1 from chains where level=0 and status='done' and t0>=? and t0<? order by t0", (start, end)).fetchall()
event_blocks = set(); event_spans = []
for cid, title, body, ids, t0, t1 in day_chains:
    for b in (ids or "").split(","):
        try: event_blocks.add(int(b.strip()))
        except Exception: pass
    event_spans.append((t0, t1))
ev_span = set()
for r in a11y:
    if r[0] in event_blocks: ev_span.add((r[1], r[2]))
cov_ev = union_len(list(ev_span)) / 60000
img_ids = {r[0] for r in img_rows}
never = [r for r in img_rows if r[0] not in event_blocks]
# M5 classify 40 never-event blocks
noise_pkgs = {"com.miui.home", "com.android.systemui", "com.miui.securitycenter",
              "com.android.inputmethod.latin", "com.sohu.inputmethod.sogou",
              "com.miui.screenshot", "com.android.settings"}
all_chain_blocks = set()
for (ids,) in mc.execute("select block_ids from chains where block_ids is not null"):
    for b in (ids or "").split(","):
        try: all_chain_blocks.add(int(b.strip()))
        except Exception: pass
def cls(r):
    if r[4] in noise_pkgs:
        return "A 纯噪音"
    if not r[7] or len(r[7].strip()) < 8:
        return "C 文本空/乱码"
    if r[0] in all_chain_blocks:
        return "B 被合并吃掉"
    return "B 有信息但未成事件"
m5 = Counter(); m5_sample = []
import random
random.seed(42)
for r in random.sample(never, min(40, len(never))):
    c = cls(r); m5[c] += 1; m5_sample.append((r[0], r[4], c, (r[7] or "")[:60]))
# M6
pe_ratio = pe_blocks / len(a11y) if a11y else 0
# gaps in day (complement of all traces), count minutes
gap_min = sum((b - a) / 60000 for a, b in gaps_list)
def gap_bucket(ms):
    h = datetime.fromtimestamp(ms / 1000).hour
    return "00-06" if h < 6 else "06-12" if h < 12 else "12-18" if h < 18 else "18-24"
gap_by_bucket = defaultdict(float)
for a, b in gaps_list: gap_by_bucket[gap_bucket(a)] += (b - a) / 60000

out = []
out.append("# 采集质量审计 " + DAY)
out.append("")
out.append("范围：本地 evidence.db + host_memory.db；a11y 块 %d，usage 块 %d；不调模型。" % (len(a11y), len(usage)))
out.append("")
out.append("## M1 时间覆盖")
out.append("")
out.append("| 指标 | 分钟 | 占全天 |")
out.append("|---|---:|---:|")
out.append("| a11y trace 区间并集 | %.0f | %.1f%% |" % (cov_a, cov_a / DAY_MIN * 100))
out.append("| 有画面（shot_ref） | %.0f | %.1f%% |" % (cov_img, cov_img / DAY_MIN * 100))
out.append("| 有文本（≥4 字） | %.0f | %.1f%% |" % (cov_txt, cov_txt / DAY_MIN * 100))
out.append("| usage 骨架 | %.0f | %.1f%% |" % (cov_u, cov_u / DAY_MIN * 100))
out.append("| usage ∪ a11y | %.0f | %.1f%% |" % (cov_both, cov_both / DAY_MIN * 100))
out.append("| 完全无记录（补集） | %.0f | %.1f%% |" % (DAY_MIN - cov_both, (DAY_MIN - cov_both) / DAY_MIN * 100))
out.append("")
out.append("按 app 覆盖分钟 Top15：")
for p, m in pkg_cov[:15]: out.append("- %s %.0f min" % (p, m))
out.append("")
out.append("按缺口起点时段汇总：")
for k in ["00-06", "06-12", "12-18", "18-24"]:
    out.append("- %s：%.0f min" % (k, gap_by_bucket.get(k, 0)))
out.append("")
out.append("缺口段（>1min，最多 20 段）：")
for a, b in gaps_list[:20]: out.append("- %s–%s %.0f min" % (seg(a), seg(b), (b - a) / 60000))
out.append("")
out.append("## M2 UsageStats 骨架")
out.append("")
out.append("- usage 覆盖 %.0f min / 全天 = **%.1f%%**" % (cov_u, cov_u / DAY_MIN * 100))
out.append("- usage ∪ a11y = **%.1f%%**（a11y 单独 %.1f%%）" % (cov_both / DAY_MIN * 100, cov_a / DAY_MIN * 100))
out.append("")
out.append("## M3 OCR 噪音")
out.append("")
out.append("- 相邻同 pkg 块 2-gram Jaccard 平均：**%.2f**（%d 对）" % (avg_rep, len(repeats)))
out.append("- OCR 2-gram 能在同页 page_elements 找到的平均比例：**%.1f%%**（%d 块有元素）" % (avg_ocr_cov * 100, pe_blocks))
out.append("- OCR 总字符 %d；去 chrome 后有效字符 %d（有效率 %.1f%%）" % (ocr_chars, eff_total, eff_total / max(1, ocr_chars) * 100))
out.append("- chrome 高频行 Top20：")
for x, n in chrome.most_common(20): out.append("  - `%s` ×%d" % (x[:50], n))
out.append("")
out.append("## M4 可重建性")
out.append("")
out.append("- 当日 a11y 块 %d，事件（level0 done）%d 条" % (len(a11y), len(day_chains)))
out.append("- 事件块覆盖有画面分钟：%.0f / %.0f = **%.1f%%**" % (cov_ev, cov_img, cov_ev / max(1, cov_img) * 100))
out.append("- 从未进入任何事件的 a11y 块：**%d / %d = %.1f%%**" % (len(never), len(a11y), len(never) / max(1, len(a11y)) * 100))
out.append("")
out.append("## M5 遗漏类型（抽 40 个未进入事件的块）")
out.append("")
for k, v in m5.most_common(): out.append("- %s：%d" % (k, v))
out.append("")
out.append("`D 采集断` 无法用“不存在的块”抽样，见 M1 缺口段；补集 %.0f min。" % (DAY_MIN - cov_both))
out.append("")
out.append("## M6 结构信息利用率")
out.append("")
out.append("- 有 page_elements 的 a11y 块：%d / %d = **%.1f%%**" % (pe_blocks, len(a11y), pe_ratio * 100))
out.append("- 这些块 OCR 字符 %d，page_elements 字符 %d（结构/OCR = %.2fx）" % (ocr_chars, pe_chars, pe_chars / max(1, ocr_chars)))
out.append("")
out.append("## 一句话结论")
out.append("")
out.append("如果只能改一件：**先把采集覆盖率从 %.0f%% 补上去（usage 骨架目前只有 %.1f%%，撑不起“骨架 + 细节”两层），"
           "而不是先做 OCR 去噪**——OCR 有效率 %.1f%%，噪音不是主要矛盾；覆盖补起来后，再处理事件化合并（抽样里 36/40 个漏块是有信息但没进事件）。" % (
               cov_both / DAY_MIN * 100, cov_u / DAY_MIN * 100, eff_total / max(1, ocr_chars) * 100))
out.append("")
OUT.write_text("\n".join(out), encoding="utf-8")
print("written", OUT)
print("M1 a11y %.0f (%.1f%%) img %.0f txt %.0f usage %.0f both %.0f (%.1f%%)" % (
    cov_a, cov_a / DAY_MIN * 100, cov_img, cov_txt, cov_u, cov_both, cov_both / DAY_MIN * 100))
print("M3 rep %.2f ocrpe %.1f%% eff %.1f%%" % (avg_rep, avg_ocr_cov * 100, eff_total / max(1, ocr_chars) * 100))
print("M4 events %d blocks %d never %d (%.1f%%) cov_ev %.0f/%.0f" % (
    len(day_chains), len(a11y), len(never), len(never) / max(1, len(a11y)) * 100, cov_ev, cov_img))
print("M5", dict(m5)); print("M6 pe %.1f%% chars %d/%d" % (pe_ratio * 100, pe_chars, ocr_chars))

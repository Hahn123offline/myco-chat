#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""task-66: 读 A11Y 会话条（结构化），建群消息线程；只读 evidence.db。"""
import re, sqlite3, sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = Path(r"<workspace>"); DB = ROOT / "raw/v2/device/evidence.db"
OUT = ROOT / "raw/v2/a11y-thread.md"
c = sqlite3.connect(DB); c.text_factory = lambda b: b.decode("utf-8", "replace")

TIME_SUFFIX = re.compile(r",\s*(\d{1,2}:\d{2}|昨天\s*\d{1,2}:\d{2}|星期[一二三四五六日天]|"
                         r"\d{1,2}/\d{1,2}|\d{4}/\d{1,2}/\d{1,2})"
                         r"(?:\s*,.*)?$")
UNREAD = re.compile(r"^\s*\d+\s*条未读\s*,")
SENDER = re.compile(r"^([^:：]{1,40})[:：]\s*(.*)$", re.S)
OBJECT = re.compile(r"^[\s<く〈「]*[\d\s]*[()（）]?\s*")

def fmt_day(ms): return datetime.fromtimestamp(ms / 1000).strftime("%m-%d %H:%M")

def parse(text):
    """返回 (object, sender, message, display_time) 或 None。"""
    t = (text or "").strip()
    if ", ," not in t: return None
    obj, rest = t.split(", ,", 1)
    obj = re.sub(r"^[\s<く〈「\[【]+", "", obj).strip()
    obj = re.sub(r"^[\d\s]+", "", obj).strip()  # 未读数/角标剥掉（仅开头）
    obj = re.sub(r"^[()（）]+\s*", "", obj).strip()
    if not obj: return None
    m = TIME_SUFFIX.search(rest)
    if not m: return None
    disp = re.sub(r"\s+", " ", m.group(1)).strip()
    body = rest[:m.start()]
    body = UNREAD.sub("", body, count=1).strip()
    sm = SENDER.match(body)
    if sm:
        sender = sm.group(1).strip()
        msg = sm.group(2).strip()
    else:
        sender, msg = "系统", body.strip()
    msg = msg.strip()
    if not msg: return None
    return obj, sender, msg, disp

def main():
    # trace 映射：observed_at -> 最接近的同 pkg trace
    traces = c.execute("select id,t0,t1,pkg,page_id from traces order by t0").fetchall()
    by_pkg = defaultdict(list)
    for tr in traces: by_pkg[tr[3]].append(tr)
    def trace_id(obs, pkg):
        best = None; bestd = 1 << 62
        for tr in by_pkg.get(pkg, []):
            if tr[1] <= obs <= tr[2]: return tr[0]
            d = min(abs(obs - tr[1]), abs(obs - tr[2]))
            if d < bestd: bestd, best = d, tr[0]
        return best

    rows = c.execute("select id,t0,pkg,text,left,top from page_elements "
                     "where source in ('A11Y','BOTH') order by t0").fetchall()
    groups = defaultdict(list)     # obj -> rows
    failures = []
    attempted = 0
    for eid, t0, pkg, text, left, top in rows:
        obj = None
        if pkg == "com.tencent.mobileqq" and (", ," in (text or "")):
            attempted += 1
            p = parse(text)
            if p: obj = p[0]
            else:
                if len(failures) < 30: failures.append(text[:160])
            if obj:
                groups[obj].append((eid, t0, pkg, text, left, top, p))
        elif pkg == "tv.danmaku.bili":
            pass  # B站另行探明
    # 对象选择
    target = {"零杯": [], "课程-COD-2026计组": []}
    for obj, xs in groups.items():
        if "零杯" in obj: target["零杯"].extend(xs)
        elif "课程-COD-2026计组" in obj: target["课程-COD-2026计组"].extend(xs)
    out = ["# A11Y 会话条线程重建", "",
           "数据源：page_elements source in (A11Y, BOTH)；按 `对象, ,未读,发送者: 消息,时间,置顶` 解析。", ""]
    stats = {}
    for name, xs in target.items():
        # 同一屏同文本只算一次观察
        uniq = {}
        for x in xs:
            key = (x[1], x[3])
            uniq.setdefault(key, x)
        obs = len(uniq)
        parsed = []
        for x in uniq.values():
            p = x[6]
            parsed.append((x[1], p[1], p[2], p[3], trace_id(x[1], x[2]), x[0]))
        seen = {}; fail = Counter()
        for t0, sender, msg, disp, tid, eid in sorted(parsed):
            k = (sender, msg)
            if k in seen:
                seen[k][4] += 1
            else:
                seen[k] = [t0, sender, msg, disp, 1, tid, eid]
        days = sorted(set(fmt_day(t0)[:5] for t0, *_ in parsed))
        conflict = 0
        for t0, sender, msg, disp, tid, eid in parsed:
            mm = re.fullmatch(r"(\d{1,2}):(\d{2})", disp)
            if mm:
                obs_dt = datetime.fromtimestamp(t0 / 1000)
                diff = abs((obs_dt.hour * 60 + obs_dt.minute) - (int(mm.group(1)) * 60 + int(mm.group(2))))
                if diff > 60: conflict += 1
        stats[name] = (obs, len(parsed), len(seen), days, conflict)
        out += ["## 群「%s」" % name, "",
                "观察次数 %d（不同屏），解析消息 %d 条，去重后 **%d 条唯一消息**，跨 %s。"
                % (obs, len(parsed), len(seen), "/".join(days)), "",
                "| display_time | observed_at | 发送者 | 消息 | 观察到次数 | trace |",
                "|---|---|---|---|---:|---|"]
        for k, v in sorted(seen.items(), key=lambda kv: kv[1][0]):
            t0, sender, msg, disp, n, tid, eid = v
            safe = msg.replace("|", "\\|").replace("\n", " ")
            out.append("| %s | %s | %s | %s | %d | %s |" % (
                disp, fmt_day(t0), sender, safe, n, tid))
        out.append("")
    # B站探明
    bili = c.execute("select count(*) from page_elements where pkg='tv.danmaku.bili' "
                     "and source in ('A11Y','BOTH')").fetchone()[0]
    top = c.execute("select text,count(*) n from page_elements where pkg='tv.danmaku.bili' "
                    "and source in ('A11Y','BOTH') and length(text)>10 "
                    "group by text order by n desc limit 10").fetchall()
    out += ["## B站 A11Y 形态", "",
            "A11Y/BOTH 节点 %d 个。形态是**扁平评论文本/广告/按钮**，没有 QQ 那种"
            "「对象, ,发送者: 消息,时间」的会话条；`page_title` 为空，节点里也没有稳定视频 id。"
            "因此本轮无法把「视频页+评论区」归到同一视频，只做形态探明。" % bili, ""]
    for text, n in top:
        out.append("- ×%d `%s`" % (n, text[:100].replace("`", "'")))
    out.append("")
    # 失败样本
    out += ["## 解析失败", "", "QQ 候选行 %d，失败 %d。" % (attempted, len(failures)), ""]
    for x in failures[:10]: out.append("- `%s`" % x.replace("`", "'"))
    out.append("")
    out += ["## 结论", "",
            "结构化会话条可解析：零杯群 %s；课程群 %s。"
            % (("观察 %d / 唯一消息 %d / 跨 %s / 时间冲突 %d" % (stats.get("零杯", (0,0,0,[],0))[0],
                                                stats.get("零杯", (0,0,0,[],0))[2],
                                                "/".join(stats.get("零杯", (0,0,0,[],0))[3]),
                                                stats.get("零杯", (0,0,0,[],0))[4])),
               ("观察 %d / 唯一消息 %d / 时间冲突 %d" % (stats.get("课程-COD-2026计组", (0,0,0,[],0))[0],
                                          stats.get("课程-COD-2026计组", (0,0,0,[],0))[2],
                                          stats.get("课程-COD-2026计组", (0,0,0,[],0))[4]))),
            "B站没有对象会话条，需要另找视频 id 字段（page_id 可能可用，但当前节点无）。", ""]
    OUT.write_text("\n".join(out), encoding="utf-8")
    print("written", OUT)
    for k, v in stats.items(): print(k, v)

if __name__ == "__main__": main()

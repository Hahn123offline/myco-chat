#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""task-68: 消息级记忆（逐屏抽消息，不跨屏对齐；全局去重）。"""
import re, sqlite3, sys, difflib
from collections import Counter
from datetime import datetime
from pathlib import Path
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = Path(r"<workspace>"); DB = ROOT / "raw/v2/device/evidence.db"
OUT = ROOT / "raw/v2/group-memory-零杯.md"
c = sqlite3.connect(DB); c.text_factory = lambda b: b.decode("utf-8", "replace")

TIME = re.compile(r"^\d{1,2}:\d{2}(:\d{2})?$")
HEAD = re.compile(r"^[く<〈「\[]?\s*\d{0,3}\s*零杯|零杯（）")
SENDER = re.compile(r"(Hahn123|群主|群成员|25-鄂|25-号鄂|曾嘉|\\bLV\\d*)", re.I)
CHROME_WORDS = set("发送 相册 语音 拍照 表情 更多功能 按住说话 返回 聊天设置 听筒模式 "
                  "确定 取消 符号 分词 预览 回 q'zhu 中っ L英 P io H B U J KL 强壮 "
                  "雷霆坐姿!? 雷霆坐姿!? 0.00".split())
def is_chrome(s):
    t = (s or "").strip()
    if t in CHROME_WORDS: return True
    if re.fullmatch(r"[A-Z](\s+[A-Z])+", t): return True
    if re.fullmatch(r"[0-9.]+\s*(KB/s|5G|4G|G)?", t, re.I): return True
    if "kb/s" in t.lower(): return True
    if re.fullmatch(r"\|?\s*[A-Z]{1,3}", t): return True
    return False


def norm(s):
    s = re.sub(r"\\s+", "", s or "")
    s = re.sub(r"[，,。！？!?~～、；;：:\\-—_|\\[\\]「」『』()（）]", "", s)
    return s

def is_msg(s):
    if not s or len(s) < 2 or len(s) > 120: return False
    if TIME.match(s) or HEAD.search(s) or is_chrome(s): return False
    cjk = len(re.findall(r"[\\u4e00-\\u9fff]", s))
    return cjk >= 2 or (cjk >= 1 and len(s) >= 4)

def extract(block_text):
    """返回 (messages[(sender,msg)], chrome_lines)。"""
    ls = [x.strip() for x in (block_text or "").splitlines() if x.strip()]
    out = []; chrome = []
    for i, line in enumerate(ls):
        if HEAD.search(line) or TIME.match(line) or is_chrome(line):
            chrome.append(line); continue
        if SENDER.search(line) and (":" not in line or "群" in line):
            spk = "我" if "Hahn123" in line else "群主"
            for j in range(i + 1, min(len(ls), i + 3)):
                nxt = ls[j].strip()
                if not nxt or TIME.match(nxt) or HEAD.search(nxt) or is_chrome(nxt):
                    continue
                if SENDER.search(nxt): break
                if is_msg(nxt):
                    out.append((spk, re.sub(r"^[|｜\\s]+", "", nxt)))
                break
            continue
        # 带发送者前缀的行
        m = re.match(r"^([^:：]{1,30})[:：]\\s*(.+)$", line)
        if m and is_msg(m.group(2)):
            out.append((m.group(1).strip(), m.group(2).strip()))
    return out, chrome

SUSPECT = re.compile(r"(在线|4G|5G|KB/s|O00|O100|O000|O00:4|C\d|l\.|点按|GIF|符号|"
                     r"^[A-Za-z0-9 .,|:!?~\-]+$)" )

def quality(s):
    """可读性过滤：CJK 太少或明显状态栏/键盘残片 → 可疑行。"""
    cjk = len(re.findall(r"[\u4e00-\u9fff]", s))
    if cjk < 2: return False
    ratio = cjk / max(1, len(s))
    if ratio < 0.25: return False
    if SUSPECT.search(s): return False
    return True

def main():
    rows = c.execute("select id,t0,text from traces where pkg='com.tencent.mobileqq' "
                     "and text like '%零杯%' order by t0").fetchall()
    cand = []; chrome_all = []; screens = 0
    for bid, t0, text in rows:
        screens += 1
        msgs, ch = extract(text); chrome_all += ch
        for spk, msg in msgs:
            if quality(msg): cand.append((t0, bid, spk, msg))
            else: chrome_all.append("SUSPECT " + msg)
    # 全局去重：sender 相同 + norm 编辑相似 ≥0.9
    uniq = []
    for t0, bid, spk, msg in sorted(cand):
        n = norm(msg)
        if not n: continue
        found = None
        for u in uniq:
            if u["spk"] != spk and u["spk"] != "未知" and spk != "未知": continue
            if difflib.SequenceMatcher(None, n, u["n"]).ratio() >= 0.9:
                found = u; break
        if found:
            found["count"] += 1
        else:
            uniq.append(dict(t0=t0, bid=bid, spk=spk, msg=msg, n=n, count=1))
    days = sorted(set(datetime.fromtimestamp(x[1] / 1000).strftime("%m-%d") for x in rows))
    out = ["# 群「零杯网页设计大赛决赛群」· 消息记忆", "",
           "逐屏抽消息、不跨屏对齐；按 observed_at 排；相似度≥0.9 合并。", "",
           "## 汇总", "",
           "- 观察屏幕：%d，跨 %s" % (screens, "/".join(days)),
           "- 候选消息：%d" % len(cand),
           "- 去重后：**%d 条唯一消息**" % len(uniq),
           "- 候选（过滤后）%d；去重 %d" % (len(cand), len(uniq)),
           "- 疑似 chrome/解析可疑：%d 行（去重后 %d 种）" % (len(chrome_all), len(set(chrome_all))),
           ""]
    out += ["## 消息（按 observed_at）", ""]
    for u in sorted(uniq, key=lambda x: x["t0"]):
        ts = datetime.fromtimestamp(u["t0"] / 1000).strftime("%m-%d %H:%M")
        out.append("- `[%s]` %s: %s  <sub>id=%d ×%d</sub>" % (
            ts, u["spk"], u["msg"], u["bid"], u["count"]))
    out += ["", "## 疑似 chrome / 解析可疑（未偷偷丢弃）", ""]
    for line, n in Counter(chrome_all).most_common(40):
        out.append("- ×%d `%s`" % (n, line[:100].replace("`", "'")))
    # B站对象：评论 2402 视频，按 t0 顺序抽标题+评论
    bili = c.execute("select id,t0,text from traces where pkg='tv.danmaku.bili' "
                     "and text like '%评论 2402%' order by t0").fetchall()
    out += ["", "## B站对象（评论 2402，OCR 顺序抽取）", "",
            "B站块 %d 个；只按 t0/行序抽正文，不做对象归并。" % len(bili), ""]
    for bid, t0, text in bili[:5]:
        out.append("### id=%d %s" % (bid, datetime.fromtimestamp(t0 / 1000).strftime("%m-%d %H:%M")))
        shown = 0
        for line in (text or "").splitlines():
            s = line.strip()
            if not s or TIME.match(s) or is_chrome(s): continue
            out.append("- " + s[:140]); shown += 1
            if shown >= 12: break
        out.append("")
    out += ["## 判定", "",
            "目标是把零杯从列表页 12 条扩到几十条。实际去重后 %d 条；**%s**。" % (
                len(uniq), "达到几十条" if len(uniq) >= 30 else "未达到几十条，卡在 OCR 行碎片/发送者前缀不稳定"),
            "详细可疑行见上；未改 Android/采集/L2。", ""]
    OUT.write_text("\n".join(out), encoding="utf-8")
    print("written", OUT, "screens", screens, "cand", len(cand), "uniq", len(uniq),
          "chrome", len(chrome_all), "bili", len(bili))

if __name__ == "__main__": main()

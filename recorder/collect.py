#!/usr/bin/env python3
"""DSH 笨记录器 v0.1 —— 只记录，不处理。

设计原则：
  1. 原始、追加写、幂等、可重放。
  2. 不做任何解析/建库/embedding/LLM/判重。
  3. 手机不在、命令失败 → 记录失败原因，退避重试，绝不崩溃。
  4. 唯一"聪明"的地方：截图做变化门控（同画面不重复存盘）。

抓四个信号：
  shot    截图（变化门控，仅亮屏）
  focus   前台应用 + 焦点窗口（变化时追加）
  notif   通知栏全文 dumpsys notification --noredact（变化时追加）
  media   媒体库新增（按 _id 去重追加）

用法：
  python recorder/collect.py --out E:\\11-15\\raw --interval 20
  可选：--device emulator-5562   --once   只跑一轮（自检用）

产出结构：
  raw/YYYY-MM-DD/shots/<ts>-<hash8>.png
  raw/YYYY-MM-DD/focus.jsonl
  raw/YYYY-MM-DD/notif.jsonl
  raw/YYYY-MM-DD/media.jsonl
  raw/YYYY-MM-DD/poll.jsonl     # 每轮采集的自检记录
"""

import argparse
import datetime as dt
import hashlib
import json
import os
import subprocess
import sys
import time
from pathlib import Path

ADB = "adb"


# ---------------------------------------------------------------- 基础设施

def run_adb(device, args, binary=False, timeout=60):
    cmd = [ADB]
    if device:
        cmd += ["-s", device]
    cmd += args
    r = subprocess.run(cmd, capture_output=True, timeout=timeout)
    if r.returncode != 0:
        raise RuntimeError(r.stderr.decode("utf-8", "replace").strip()[:400])
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def list_devices():
    try:
        out = subprocess.run([ADB, "devices"], capture_output=True, timeout=20).stdout
        out = out.decode("utf-8", "replace")
    except Exception:
        return []
    devs = []
    for line in out.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devs.append(parts[0])
    return devs


def now():
    return dt.datetime.now()


def today_dir(out: Path) -> Path:
    d = out / now().strftime("%Y-%m-%d")
    d.mkdir(parents=True, exist_ok=True)
    return d


def append_jsonl(path: Path, obj: dict):
    obj.setdefault("ts", now().isoformat(timespec="seconds"))
    with open(path, "a", encoding="utf-8") as f:
        f.write(json.dumps(obj, ensure_ascii=False) + "\n")


def sha(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()


# ---------------------------------------------------------------- 采集器

class Recorder:
    def __init__(self, out: Path, device: str | None):
        self.out = out
        self.device = device
        self.last_shot = None
        self.last_focus = None
        self.last_notif = None
        self.seen_media = set()
        self.counters = {"shot": 0, "focus": 0, "notif": 0, "media": 0}

    # --- 亮屏判断 ---------------------------------------------------
    def screen_on(self) -> bool:
        try:
            txt = run_adb(self.device, ["shell", "dumpsys", "power"])
        except Exception:
            return False
        for line in txt.splitlines():
            s = line.strip()
            if s.startswith("mWakefulness="):
                return "Awake" in s
        return True  # 拿不到就当亮着，宁可多存

    # --- 1. 截图 -----------------------------------------------------
    def grab_shot(self, d: Path):
        if not self.screen_on():
            return "screen_off"
        png = run_adb(self.device, ["exec-out", "screencap", "-p"], binary=True, timeout=60)
        h = sha(png)
        if h == self.last_shot:
            return "unchanged"
        self.last_shot = h
        p = d / "shots" / f"{now().strftime('%H%M%S')}-{h[:8]}.png"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(png)
        self.counters["shot"] += 1
        return "saved"

    # --- 2. 前台窗口 -------------------------------------------------
    def grab_focus(self, d: Path):
        txt = run_adb(self.device, ["shell", "dumpsys", "window"])
        focus = None
        for line in txt.splitlines():
            s = line.strip()
            if s.startswith("mCurrentFocus=") or s.startswith("mFocusedApp="):
                focus = s
                break
        if not focus or focus == self.last_focus:
            return "unchanged"
        self.last_focus = focus
        append_jsonl(d / "focus.jsonl", {"focus": focus})
        self.counters["focus"] += 1
        return "saved"

    # --- 3. 通知全文 -------------------------------------------------
    def grab_notif(self, d: Path):
        txt = run_adb(self.device, ["shell", "dumpsys", "notification", "--noredact"], timeout=90)
        h = sha(txt.encode("utf-8", "replace"))
        if h == self.last_notif:
            return "unchanged"
        self.last_notif = h
        append_jsonl(d / "notif.jsonl", {"sha": h[:16], "raw": txt})
        self.counters["notif"] += 1
        return "saved"

    # --- 4. 媒体新增 -------------------------------------------------
    def grab_media(self, d: Path):
        txt = run_adb(
            self.device,
            ["shell", "content", "query", "--uri",
             "content://media/external/images/media",
             "--projection", "_id:_data:date_added:datetaken:width:height"],
            timeout=120,
        )
        added = 0
        for line in txt.splitlines():
            line = line.strip()
            if not line.startswith("Row:"):
                continue
            mid = None
            for tok in line.split():
                if tok.startswith("_id="):
                    mid = tok.split("=", 1)[1]
                    break
            if not mid or mid in self.seen_media:
                continue
            self.seen_media.add(mid)
            append_jsonl(d / "media.jsonl", {"row": line})
            added += 1
            self.counters["media"] += 1
        return f"saved:{added}" if added else "unchanged"

    # --- 一轮 ---------------------------------------------------------
    def poll_once(self):
        d = today_dir(self.out)
        jobs = [("shot", self.grab_shot), ("focus", self.grab_focus),
                ("notif", self.grab_notif), ("media", self.grab_media)]
        report = {}
        for name, fn in jobs:
            try:
                report[name] = fn(d)
            except subprocess.TimeoutExpired:
                report[name] = "timeout"
            except Exception as e:
                report[name] = f"error:{str(e)[:160]}"
        append_jsonl(d / "poll.jsonl", {"report": report})
        bad = [k for k, v in report.items() if str(v).startswith(("error", "timeout"))]
        if bad:
            print(f"[warn] {now():%H:%M:%S} 失败: {bad} -> {report}")
        return report


# ---------------------------------------------------------------- 主循环

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="raw")
    ap.add_argument("--interval", type=int, default=20, help="秒")
    ap.add_argument("--device", default=None)
    ap.add_argument("--once", action="store_true")
    args = ap.parse_args()

    if not list_devices():
        print("[fatal] 没有处于 device 状态的设备。先插线 / 或 adb connect。")
        print("        当前 adb devices 输出见下：")
        subprocess.run([ADB, "devices", "-l"])
        if not args.once:
            sys.exit(2)
    dev = args.device or (list_devices() or [None])[0]
    out = Path(args.out).resolve()
    out.mkdir(parents=True, exist_ok=True)
    print(f"[ok] device={dev}  out={out}  interval={args.interval}s")

    rec = Recorder(out, dev)
    backoff = 0
    try:
        while True:
            if not list_devices():
                print(f"[wait] {now():%H:%M:%S} 设备断开，重连中…")
                time.sleep(max(5, backoff))
                backoff = min(backoff * 2 or 5, 60)
                continue
            backoff = 0
            report = rec.poll_once()
            print(f"[poll] {now():%H:%M:%S} {report}  counters={rec.counters}")
            if args.once:
                break
            time.sleep(args.interval)
    except KeyboardInterrupt:
        print(f"\n[stop] counters={rec.counters}")


if __name__ == "__main__":
    main()

"""OCR/A11Y 文本行匹配求位移：验证它在周期列表上的判别力。

和像素 NCC 对比：像素给的是"近周期梳状峰"，文本给的是"同一句话只出现一处"。
判据：共享文本行里，有多少行同意同一个 Δy（众数占比）。
"""
import sqlite3
import sys
from collections import Counter
from datetime import datetime

DB = r"<workspace>\raw\v2\design\ocr-probe\memory.db"


def load_block(c, block_id):
    q = """select frame_id, line_no, text, y_top, x_left, x_right, time
           from lines where block_id=? and frame_id is not null
           order by frame_id, line_no"""
    rows = c.execute(q, (block_id,)).fetchall()
    frames = {}
    for fr, ln, text, y, xl, xr, t in rows:
        frames.setdefault(fr, []).append((ln, text, y, xl, xr))
    return frames


def dy_votes(a, b):
    """a、b 是同一 block 的两帧；返回每个 (Δy) 的票数与参与匹配的文本。"""
    ta = {}
    for ln, text, y, xl, xr in a:
        if text and text.strip() and text.strip() not in ta:
            ta[text.strip()] = y
    tb = {}
    for ln, text, y, xl, xr in b:
        if text and text.strip() and text.strip() not in tb:
            tb[text.strip()] = y
    shared = set(ta) & set(tb)
    votes = Counter()
    detail = []
    for k in shared:
        d = ta[k] - tb[k]          # 内容上移为正
        votes[d] += 1
        detail.append((k[:18], ta[k], tb[k], d))
    return votes, detail, len(ta), len(tb)


def cluster_votes(votes, tol=6):
    """把 Δy 票按容差聚类（OCR bbox 有 ±3px 抖动，整数相等会切碎同一位移）。"""
    items = sorted(votes.items())            # 按 Δy 排序
    clusters = []
    for d, n in items:
        if clusters and d - clusters[-1]["max"] <= tol:
            cl = clusters[-1]
            cl["max"] = d
            cl["n"] += n
            cl["ws"] += d * n
        else:
            clusters.append({"min": d, "max": d, "n": n, "ws": d * n})
    for cl in clusters:
        cl["center"] = cl["ws"] / cl["n"]
    clusters.sort(key=lambda c: -c["n"])
    return clusters


def main():
    c = sqlite3.connect(DB)
    c.row_factory = sqlite3.Row
    blocks = [int(x) for x in sys.argv[1:]] or [3633, 3632, 3631]
    for bid in blocks:
        frames = load_block(c, bid)
        keys = sorted(frames)
        print(f"\n=== block {bid}  帧数={len(keys)} 帧={keys} ===")
        if len(keys) < 2:
            print("  帧不足，跳过")
            continue
        for i in range(len(keys) - 1):
            fa, fb = frames[keys[i]], frames[keys[i + 1]]
            votes, detail, na, nb = dy_votes(fa, fb)
            if not votes:
                print(f"  {keys[i]}->{keys[i+1]} 无共享文本（{na}/{nb} 条）")
                continue
            total = sum(votes.values())
            # 排除静态元素（Δy≈0）：长截图要的是内容位移
            moving = {d: n for d, n in votes.items() if abs(d) > 8}
            n_moving = sum(moving.values())
            cl = cluster_votes(moving)
            zero = sum(n for d, n in votes.items() if abs(d) <= 8)
            if not cl:
                print(f"  {keys[i]}->{keys[i+1]}  共享={total} 全为静态(Δy≈0)×{zero} → 画面未滚动")
                continue
            top = cl[0]
            dom = top["n"] / n_moving
            spread = top["max"] - top["min"]
            flag = "OK" if dom >= 0.8 and top["n"] >= 3 else "!!"
            print(f"  {keys[i]}->{keys[i+1]}  共享={total} 静态={zero} "
                  f"内容Δy={top['center']:+.0f}px 支持={top['n']}/{n_moving} "
                  f"占比={dom:.0%} 抖动={spread}px {flag}")
            if flag == "!!":
                print(f"     其他簇: {[(round(c['center'],1), c['n']) for c in cl[1:4]]}")


if __name__ == "__main__":
    main()

"""纵向滚动拼接：静态层分离 → 全局对齐（含峰锐度检查）→ 只贴非静态行。

这是设计文档 §4.4 的可执行版本。纯数据判据，不含任何 app 知识。

用法：
    python stitch_seq.py <帧目录> <输出png> [--y0 200] [--y1 2350]

输入是同一次滚动过程中按时间顺序截下的若干整屏 PNG。
"""
import sys
import glob
import os
import json
import numpy as np
from PIL import Image

GRID_C, GRID_R = 12, 200       # 列少（dx 精度要求低）行多（dy 精度要求高）
MIN_OVERLAP_RATIO = 0.25       # 重叠少于此比例视为"无重叠，不可拼"
MIN_SHARPNESS = 0.05           # 最优峰领先次峰的最小幅度
STATIC_DIFF = 2.0              # 静止行判定：同位置差异更小
LOAD_RGB = True


def load(path, y0, y1):
    a = np.asarray(Image.open(path).convert("RGB"))
    return a[y0:y1]


def downsample(a, cols, rows):
    h, w = a.shape[:2]
    ys = np.linspace(0, h, rows + 1).astype(int)
    xs = np.linspace(0, w, cols + 1).astype(int)
    out = np.zeros((rows, cols), dtype=np.float32)
    for i in range(rows):
        for j in range(cols):
            out[i, j] = a[ys[i]:ys[i + 1], xs[j]:xs[j + 1]].mean()
    return out


def ncc_scores(ga, gb):
    """所有候选 dy（单位：栅格行）的 NCC 分数。"""
    R = ga.shape[0]
    out = []
    for d in range(0, R // 2):
        x, y = ga[d:, :], gb[:R - d, :]
        if x.shape[0] < R * MIN_OVERLAP_RATIO:
            break
        xm, ym = x - x.mean(), y - y.mean()
        den = np.sqrt((xm ** 2).sum()) * np.sqrt((ym ** 2).sum()) + 1e-6
        out.append((float((xm * ym).sum() / den), d))
    return out


def estimate_dy(a, b):
    """返回 {dy_px, corr, sharpness, peaks[], status}。"""
    h = a.shape[0]
    row_px = h / GRID_R
    ga, gb = downsample(a, GRID_C, GRID_R), downsample(b, GRID_C, GRID_R)
    scores = ncc_scores(ga, gb)
    if not scores:
        return dict(dy_px=0, corr=0.0, sharpness=0.0, peaks=[], status="NO_OVERLAP")
    scores_sorted = sorted(scores, reverse=True)
    # 峰抑制：相距 <8 行视为同一个峰
    picked = []
    for s, d in scores_sorted:
        if all(abs(d - p[1]) > 8 for p in picked):
            picked.append((s, d))
        if len(picked) >= 5:
            break
    best_s, best_d = picked[0]
    second = picked[1][0] if len(picked) > 1 else 0.0
    sharp = best_s - second
    dy_px = best_d * row_px
    if dy_px > h * (1 - MIN_OVERLAP_RATIO):
        status = "NO_OVERLAP"
    elif best_s < 0.5:
        status = "LOW_CORR"
    elif sharp < MIN_SHARPNESS:
        status = "AMBIGUOUS"          # 梳状次峰逼近真峰：周期性误匹配风险
    elif abs(dy_px) < row_px:
        status = "NO_MOTION"          # 滚到底 / 没动
    else:
        status = "OK"
    return dict(
        dy_px=round(float(dy_px), 1),
        corr=round(float(best_s), 4),
        sharpness=round(float(sharp), 4),
        peaks=[(round(float(s), 4), round(float(d * row_px), 1)) for s, d in picked],
        status=status,
    )


def static_rows(a, b, dy_px):
    """b 中属于静止层（不随滚动移动）的行。"""
    h = a.shape[0]
    dy = int(round(dy_px))
    ov = h - dy
    mask = np.zeros(h, dtype=bool)
    if ov <= 4 or dy <= 0:
        return mask
    ga, gb = a.mean(axis=2), b.mean(axis=2)
    moved = np.abs(ga[dy:dy + ov] - gb[:ov]).mean(axis=1)
    still = np.abs(ga[:ov] - gb[:ov]).mean(axis=1)
    mask[:ov] = (still < moved) & (still < STATIC_DIFF)
    return mask


def stitch(frames, y0, y1, verbose=True):
    crops = [load(p, y0, y1) for p in frames]
    h, w = crops[0].shape[:2]
    steps, offsets = [], [0]
    for i in range(len(crops) - 1):
        info = estimate_dy(crops[i], crops[i + 1])
        info["from"], info["to"] = os.path.basename(frames[i]), os.path.basename(frames[i + 1])
        info["static_rows"] = int(static_rows(crops[i], crops[i + 1], info["dy_px"]).sum())
        steps.append(info)
        if verbose:
            print(f"{info['from']} -> {info['to']}: dy={info['dy_px']:+.1f}px "
                  f"corr={info['corr']:.3f} sharp={info['sharpness']:+.3f} "
                  f"static={info['static_rows']} → {info['status']}")
            if info["status"] != "OK":
                print(f"    peaks: {info['peaks']}")
        if info["status"] == "OK":
            offsets.append(offsets[-1] + int(round(info["dy_px"])))
        else:
            # 不可拼的一段：按整屏追加（保证不丢数据），并标记
            offsets.append(offsets[-1] + h)

    total_h = offsets[-1] + h
    canvas = np.zeros((total_h, w, 3), dtype=np.uint8)
    for i, c in enumerate(crops):
        base = offsets[i]
        if i == 0:
            canvas[base:base + h] = c
            continue
        info = steps[i - 1]
        if info["status"] == "OK":
            mask = static_rows(crops[i - 1], c, info["dy_px"])
            keep = ~mask
            canvas[base:base + h][keep] = c[keep]
        else:
            canvas[base:base + h] = c
    ok = sum(1 for s in steps if s["status"] == "OK")
    report = dict(
        frames=len(frames),
        pairs=len(steps),
        ok=ok,
        stitchable_rate=round(ok / max(1, len(steps)), 3),
        total_height=total_h,
        steps=steps,
    )
    return canvas, report


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(2)
    d, out = sys.argv[1], sys.argv[2]
    y0, y1 = 200, 2350
    if "--y0" in sys.argv:
        y0 = int(sys.argv[sys.argv.index("--y0") + 1])
    if "--y1" in sys.argv:
        y1 = int(sys.argv[sys.argv.index("--y1") + 1])
    frames = sorted(glob.glob(os.path.join(d, "*.png")))
    canvas, report = stitch(frames, y0, y1)
    Image.fromarray(canvas).save(out)
    small = Image.fromarray(canvas)
    small.thumbnail((560, 6000))
    small.save(out.replace(".png", "_small.png"))
    print(f"\n拼接 {canvas.shape[1]}x{canvas.shape[0]} → {out}")
    print(f"可拼率 {report['ok']}/{report['pairs']} = {report['stitchable_rate']}")
    with open(out.replace(".png", "_report.json"), "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()

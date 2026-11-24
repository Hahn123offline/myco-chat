"""栅格形状扫描：找能让微信会话列表对齐无歧义的 (cols, rows)。

判据不是 dy 精度（行数决定），而是**可判别性**（锐度 = 最优峰 - 次峰）。
"""
import glob
import os
import numpy as np
from PIL import Image

D = r"<workspace>\raw\v2\design\align-test\seq"
Y0, Y1 = 200, 2350
FRAMES = sorted(glob.glob(os.path.join(D, "f*.png")))


def load(p):
    return np.asarray(Image.open(p).convert("RGB"))[Y0:Y1]


def downsample(a, cols, rows):
    h, w = a.shape[:2]
    ys = np.linspace(0, h, rows + 1).astype(int)
    xs = np.linspace(0, w, cols + 1).astype(int)
    out = np.zeros((rows, cols), dtype=np.float32)
    for i in range(rows):
        for j in range(cols):
            out[i, j] = a[ys[i]:ys[i + 1], xs[j]:xs[j + 1]].mean()
    return out


def evaluate(crops, cols, rows):
    """返回每对的 (dy_px, corr, sharpness)。"""
    h = crops[0].shape[0]
    row_px = h / rows
    res = []
    for i in range(len(crops) - 1):
        ga, gb = downsample(crops[i], cols, rows), downsample(crops[i + 1], cols, rows)
        R = rows
        scores = []
        for d in range(0, R // 2):
            x, y = ga[d:, :], gb[:R - d, :]
            if x.shape[0] < R * 0.25:
                break
            xm, ym = x - x.mean(), y - y.mean()
            den = np.sqrt((xm ** 2).sum()) * np.sqrt((ym ** 2).sum()) + 1e-6
            scores.append((float((xm * ym).sum() / den), d))
        scores.sort(reverse=True)
        picked = []
        for s, d in scores:
            if all(abs(d - p[1]) > max(4, rows // 25) for p in picked):
                picked.append((s, d))
            if len(picked) >= 3:
                break
        best_s, best_d = picked[0]
        second = picked[1][0] if len(picked) > 1 else 0.0
        res.append((best_d * row_px, best_s, best_s - second))
    return res


def main():
    crops = [load(p) for p in FRAMES]
    print(f"{len(crops)} 帧，内容区 {crops[0].shape[1]}x{crops[0].shape[0]}\n")
    header = "cols x rows | " + " | ".join(f"pair{i+1:02d}" for i in range(len(crops) - 1))
    print(header)
    for cols, rows in [(12, 200), (24, 200), (48, 200), (96, 200),
                       (12, 400), (24, 400), (48, 400), (96, 400),
                       (24, 800), (48, 800)]:
        r = evaluate(crops, cols, rows)
        cells = []
        for dy, corr, sharp in r:
            flag = "OK" if sharp >= 0.05 and corr >= 0.5 else "!!"
            cells.append(f"{corr:.2f}/{sharp:+.3f}{flag}")
        n_ok = sum(1 for _, c, s in r if s >= 0.05 and c >= 0.5)
        print(f"{cols:3d} x {rows:3d} | " + " | ".join(cells) + f"   → {n_ok}/{len(r)}")


if __name__ == "__main__":
    main()

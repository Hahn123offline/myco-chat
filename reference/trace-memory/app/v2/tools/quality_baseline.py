"""离线质量基线：现在这条链路到底多烂。

指标（全部可在快照上算，不需要设备）：
  1. 重复率    同一秒/同一对象内，同文本重复出现多少次
  2. 串台率    对象键是 UI 状态词（"账户及设置"、"游戏中心,按钮"、"倍速中"）的比例
  3. chrome 污染  正文里混入界面控件的比例
  4. 时间覆盖率  有可读内容的分钟数 / 清醒窗口分钟数
  5. 错字线索    非中文非 ASCII 的异常字符行占比
"""
import sqlite3
import datetime
from collections import Counter, defaultdict

DB = r"<workspace>\raw\v2\data-1123\memory.db"
c = sqlite3.connect(DB)
c.text_factory = bytes


def s(b):
    if b is None:
        return ""
    return b.decode("utf-8", "replace") if isinstance(b, bytes) else b


# ---- 界面控件词表（用于估 chrome 污染，不是最终产品用的规则）----
CHROME = {
    "发送", "相册", "语音", "泡泡", "表情", "更多功能", "拍照", "返回", "关闭",
    "聊天设置", "听筒模式", "消息免打扰", "置顶聊天", "返回消息未读", "回到底部",
    "跳转回原消息", "查看更多", "打开", "取消", "确定", "搜索", "分享", "收藏",
    "倍速中", "关注", "点赞", "投币", "简介", "热门评论", "订阅", "弹幕", "播放",
}
CHROME_PREFIX = ("5G", "4G", "KB/S", "KB/s", "消息", "返回消息", "<19", "群待办",
                 "公有新文件", "有新文件", "元宝总结", "群应用中心", "文件", "相册",
                 "作业", "精华消息", "表情[")

# 疑似串台的对象键特征：UI 状态词 / 纯数字 / 明确的设置页
BAD_OBJ = {
    "账户及设置", "聊天设置", "添加书签", "游戏中心,按钮", "倍速中", "7人正在看",
    "动画", "游戏中心", "gemma5", "搜索", "设置", "系统设置",
}
BAD_OBJ_PREFIX = ("com.android.settings", "com.miui", "com.android.deskclock")


def is_chrome(t):
    t = t.strip()
    if not t:
        return False
    if t in CHROME:
        return True
    if len(t) <= 12 and any(t.startswith(p) for p in CHROME_PREFIX):
        return True
    if len(t) <= 6 and t.replace(".", "").replace("/", "").replace("0", "").strip() == "":
        return True          # 时间戳 / 数字
    return False


def obj_tail(ok):
    return s(ok).split("/", 1)[-1] if ok else ""


def main():
    total = 0
    readable = 0
    chrome = 0
    bad_obj = 0
    text_counter = Counter()
    pair_counter = Counter()          # (object_key, text)
    per_day_min = defaultdict(set)
    per_day_lines = Counter()
    pkg_lines = Counter()
    bad_utf8 = 0
    sample_bad_obj = Counter()

    cur = c.execute("select time, pkg, object_key, text from lines")
    while True:
        try:
            row = cur.fetchone()
        except Exception:
            bad_utf8 += 1
            break
        if row is None:
            break
        ts, pkg, ok, txt = row
        total += 1
        t = s(txt).strip()
        o = s(ok)
        p = s(pkg)
        if not t:
            continue
        readable += 1
        text_counter[t] += 1
        pair_counter[(o, t)] += 1
        tail = obj_tail(ok)
        if tail in BAD_OBJ or tail.startswith(BAD_OBJ_PREFIX) or p.startswith(BAD_OBJ_PREFIX):
            bad_obj += 1
            sample_bad_obj[tail[:18]] += 1
        if is_chrome(t):
            chrome += 1
        dt = datetime.datetime.fromtimestamp(ts / 1000)
        d = dt.strftime("%m-%d")
        per_day_min[d].add(dt.strftime("%H:%M"))
        per_day_lines[d] += 1
        pkg_lines[p] += 1

    print(f"总行数 {total}   有文本 {readable}")
    print(f"\n【1】重复度")
    print(f"  distinct 文本        {len(text_counter)}")
    print(f"  平均每文本出现次数    {readable / max(1, len(text_counter)):.1f}")
    dup = sum(v - 1 for v in text_counter.values() if v > 1)
    print(f"  冗余行（可省掉的行）  {dup}  = {100*dup/max(1,readable):.1f}%")
    top = text_counter.most_common(10)
    print("  最高频文本：")
    for k, v in top:
        print(f"    ×{v:<6d} {k[:50]}")

    print(f"\n【2】串台（对象键是 UI 状态词/设置页）")
    print(f"  {bad_obj} 行 = {100*bad_obj/max(1,readable):.1f}%")
    print("  典型错误对象：")
    for k, v in sample_bad_obj.most_common(8):
        print(f"    {v:6d}  {k}")

    print(f"\n【3】chrome 污染")
    print(f"  {chrome} 行 = {100*chrome/max(1,readable):.1f}%")

    print(f"\n【4】时间覆盖率（按天，有内容的分钟/当日活跃跨度）")
    for d in sorted(per_day_min):
        mins = per_day_min[d]
        if not mins:
            continue
        hs = sorted(mins)
        span = (int(hs[-1][:2]) * 60 + int(hs[-1][3:])) - (int(hs[0][:2]) * 60 + int(hs[0][3:]))
        print(f"  {d}  行={per_day_lines[d]:7d}  有内容分钟={len(mins):4d}  "
              f"跨度={span:4d}min  覆盖={100*len(mins)/max(1,span):.0f}%  "
              f"首={hs[0]} 末={hs[-1]}")

    print(f"\n【5】pkg 分布")
    for k, v in pkg_lines.most_common(8):
        print(f"  {v:8d}  {k}")

    # 同一对象同一时刻的重复（最刺眼的那种）
    print(f"\n【6】同对象同文本的最高重复（前 10）")
    for (o, t), v in pair_counter.most_common(10):
        print(f"  ×{v:<5d} [{obj_tail(o)[:16]}] {t[:44]}")


if __name__ == "__main__":
    main()

package com.dsh.contract;

/**
 * 记忆读接口 —— 两个独立 app 之间的代码级契约。
 *
 * 这是本仓（轨迹采集与查询器）向另一仓（聊天 app + agent 记忆）暴露的**唯一**入口。
 * 另一仓只应 import 本包及其子包；{@code com.dsh.memory} 下的实现细节不保证稳定。
 *
 * 设计约束（见 docs/INTERFACE.md）：
 * 1. 任何 recall 必须返回 {@link Recall.Freshness}，滞后必须对调用方可见。
 * 2. 结果必须确定：同数据同查询必须同结果，允许调用方缓存与回归。
 * 3. 证据可能已被 GC 或未随设备迁移，readFrame 必须能返回失败原因而不是抛异常。
 */
public interface MemoryReader {

    /** 主检索。允许 query / object / span 任意组合，全部为空时行为由实现决定（须在 coverage 里声明）。 */
    Recall.Result recall(Recall.Request request);

    /** 读一整块（一次停留的画面）。不存在时返回 null。 */
    BlockView readBlock(long blockId);

    /** 读原图。核不到必须给 reason，不得用推断冒充核对。 */
    FrameView readFrame(long frameId);

    /** 数据已处理到哪个时间点，供调用方判断"没查到"是否可信。 */
    Freshness freshness();

    /** 一次观察的画面块。 */
    final class BlockView {
        public final long blockId;
        public final long time;
        public final String pkg;
        /** 可能是空串：对象身份是判断，不保证存在。 */
        public final String objectKey;
        public final java.util.List<LineRef> lines;
        /** 证据引用，可能已失效。 */
        public final String shotRef;

        public BlockView(long blockId, long time, String pkg, String objectKey,
                         java.util.List<LineRef> lines, String shotRef) {
            this.blockId = blockId;
            this.time = time;
            this.pkg = pkg == null ? "" : pkg;
            this.objectKey = objectKey == null ? "" : objectKey;
            this.lines = lines == null ? java.util.Collections.emptyList() : lines;
            this.shotRef = shotRef == null ? "" : shotRef;
        }
    }

    /** 证据图。{@code ok=false} 时 {@code reason} 必非空。 */
    final class FrameView {
        public static final String REASON_EVICTED = "evidence_evicted";
        public static final String REASON_MISSING = "evidence_missing";
        public static final String REASON_MASK_DENIED = "mask_denied";
        public static final String REASON_UNAVAILABLE = "evidence_unavailable";

        public final boolean ok;
        public final String reason;
        public final byte[] image;
        /** ok=false 时也可能有描述（例如只拿到了文字层）。 */
        public final String description;

        private FrameView(boolean ok, String reason, byte[] image, String description) {
            this.ok = ok;
            this.reason = reason == null ? "" : reason;
            this.image = image;
            this.description = description == null ? "" : description;
        }

        public static FrameView ok(byte[] image) {
            return new FrameView(true, "", image, "");
        }

        public static FrameView fail(String reason) {
            return new FrameView(false, reason, null, "");
        }

        public static FrameView fail(String reason, String description) {
            return new FrameView(false, reason, null, description);
        }
    }

    /** 文本行：锚点 + 位置 + 证据引用。 */
    final class LineRef {
        public final long id;
        public final long blockId;
        public final long time;
        public final String text;
        public final int yTop;
        public final int xLeft;
        public final int xRight;
        public final String shotRef;

        public LineRef(long id, long blockId, long time, String text,
                       int yTop, int xLeft, int xRight, String shotRef) {
            this.id = id;
            this.blockId = blockId;
            this.time = time;
            this.text = text == null ? "" : text;
            this.yTop = yTop;
            this.xLeft = xLeft;
            this.xRight = xRight;
            this.shotRef = shotRef == null ? "" : shotRef;
        }
    }
}

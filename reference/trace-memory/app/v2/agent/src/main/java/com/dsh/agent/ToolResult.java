package com.dsh.agent;

/** 工具执行结果（错误也是结果，不抛异常给引擎）。 */
public final class ToolResult {

    public enum Status { OK, FAILED, REJECTED, NOT_EXECUTED }

    public final Status status;
    public final String summary;
    public final String error;
    /** 可选图片（一张 tool 结果最多一张）；引擎会把它作为单独 user 图像消息追加 */
    public final byte[] imageJpeg;
    /** 图片 detail：low / high */
    public final String imageDetail;

    public ToolResult(Status s, String summary, String error, byte[] image, String detail) {
        this.status = s;
        this.summary = summary == null ? "" : summary;
        this.error = error == null ? "" : error;
        this.imageJpeg = image;
        this.imageDetail = detail;
    }

    public static ToolResult ok(String summary) {
        return new ToolResult(Status.OK, summary, "", null, null);
    }

    public static ToolResult okImage(String summary, byte[] jpeg) {
        return okImage(summary, jpeg, null);
    }

    public static ToolResult okImage(String summary, byte[] jpeg, String detail) {
        return new ToolResult(Status.OK, summary, "", jpeg, detail);
    }

    public static ToolResult failed(String error) {
        return new ToolResult(Status.FAILED, "工具执行失败", error, null, null);
    }

    public static ToolResult rejected(String reason) {
        return new ToolResult(Status.REJECTED, "工具请求被拒绝", reason, null, null);
    }

    public static ToolResult notExecuted(String reason) {
        return new ToolResult(Status.NOT_EXECUTED, "未执行", reason, null, null);
    }

    public boolean isOk() { return status == Status.OK; }

    /** 给模型看的紧凑文本（≤2000 字）。 */
    public String forModel() {
        StringBuilder b = new StringBuilder();
        b.append("[status=").append(status).append("] ");
        b.append(summary);
        if (!error.isEmpty()) b.append("\nerror: ").append(error);
        if (b.length() > 2000) b.setLength(2000);
        return b.toString();
    }
}

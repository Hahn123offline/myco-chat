package com.dsh.memory;

/** 对象级消息（长截图记忆的最小单位）。 */
public final class ObjectMessage {
    public long id;
    public String objectKey = "";
    public String pkg = "";
    public long tFirst, tLast;
    public int seenCount = 1;
    public String sender = "";
    public String text = "";
    public String textKey = "";
    public long blockId, frameId;
}

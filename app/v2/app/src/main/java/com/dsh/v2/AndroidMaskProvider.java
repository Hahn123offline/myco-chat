package com.dsh.v2;

import com.dsh.masker.MaskedBlock;
import com.dsh.memory.MaskProvider;

/** 运行时 Masker：同帧 page_elements → MaskerBridge；拿不到返回 null。 */
public final class AndroidMaskProvider implements MaskProvider {

    private final TraceStore store;

    public AndroidMaskProvider(android.content.Context ctx) {
        store = new TraceStore(ctx);
    }

    @Override public com.dsh.memory.Masked maskedTextFor(long itemId, String pkg,
                                                          long t0, long t1, String rawText) {
        try {
            TraceStore.PageBinding b = store.bindingForTrace(t0, t1, pkg);
            if (b != null) {
                MaskedBlock mb = MaskerBridge.mask(store.pageElements(b.t0, b.pkg),
                        rawText, null);
                if (!MaskerBridge.guardPass(mb.text)) return com.dsh.memory.Masked.none();
                return new com.dsh.memory.Masked(mb.text, com.dsh.memory.MaskMode.FULL);
            }
            // 无 page_elements：纯文本降级（Masker 的规则大多与结构无关）
            MaskedBlock mb = MaskerBridge.maskText(rawText);
            if (!MaskerBridge.guardPass(mb.text)) return com.dsh.memory.Masked.none();
            return new com.dsh.memory.Masked(mb.text, com.dsh.memory.MaskMode.TEXT_ONLY);
        } catch (Throwable t) {
            return com.dsh.memory.Masked.none();
        }
    }
}

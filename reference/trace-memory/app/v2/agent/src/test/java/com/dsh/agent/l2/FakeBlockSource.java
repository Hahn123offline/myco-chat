package com.dsh.agent.l2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 测试用 BlockSource：16 块，id=1..16；mosaic 返回一张假 JPEG。 */
public final class FakeBlockSource implements BlockSource {

    public final List<L2Block> blocks = new ArrayList<>();
    public int mosaicCalls;
    public int frameCalls;
    public List<Long> lastMosaicIds;

    public FakeBlockSource(int n, boolean blockThird) {
        for (int i = 1; i <= n; i++) {
            blocks.add(new L2Block(i, "pkg.demo", i == 2 ? "搜索框" : "页面",
                    "块" + i + "的脱敏文本，内容长度足够用于测试。", 1000L * i,
                    1000L * i + 12_000, blockThird && i == 3));
        }
    }

    @Override public List<L2Block> blocks(String chainId) { return blocks; }

    @Override public byte[] mosaic(String chainId, List<Long> blockIds) {
        mosaicCalls++;
        lastMosaicIds = new ArrayList<>(blockIds);
        return new byte[]{1, 2, 3, 4};
    }

    @Override public byte[] frame(String chainId, long blockId, int maxEdge) {
        frameCalls++;
        return new byte[]{9, 9, 9};
    }
}

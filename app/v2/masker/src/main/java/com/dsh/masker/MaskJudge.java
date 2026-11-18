package com.dsh.masker;

import java.util.List;

/** 判定接口：规则实现 + 未来 1B 实现。 */
public interface MaskJudge {
    List<ElementSpan> judge(List<MaskElement> elements);
}

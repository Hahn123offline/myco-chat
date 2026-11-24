package com.dsh.guard;

/** 死线判定结果：PASS（原样上云）/ MASK（掩码后上云）/ BLOCK（整条不上云） */
public enum Action {
    PASS,
    MASK,
    BLOCK
}

package com.dsh.egress;

/**
 * 出网等级（白/灰/黑）。
 *
 * BLACK = 死线内容或明确禁止出网的块：**只能本地处理**，没有本地模型就失败，绝不回落云端。
 * GRAY  = 默认可本地、只有用户显式允许才上云。
 * WHITE = 正常可上云内容。
 */
public enum EgressTier {
    WHITE,
    GRAY,
    BLACK
}

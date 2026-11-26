# 内层展开动画（思考/工具小行）报告（2026-11-26）

## 问题

外层过程组体有展开动画，但组体内的小行（思考、工具）展开/收起是瞬间出现/消失：
原实现是 `if (expanded) item("rc-…") { … }`，item 直接增删，既没有高度动画也没有淡入淡出，
与 DSH 的行级折叠手感不一致。

## 改动（`ChatContent.kt` `TimelineBody`）

1. 详情改为**固定占一个 item**，用 `AnimatedVisibility` 包装：
   - 展开：`expandVertically(tween(180)) + fadeIn(tween(180))`
   - 收起：`shrinkVertically(tween(160)) + fadeOut(tween(160))`
2. 行间距从 `verticalArrangement = Arrangement.spacedBy(2.dp)` 挪到每个 item 内容底部
   （`Box(Modifier.padding(bottom = 2.dp))`）：
   这样收起后的详情 item 高度为 0 时不会额外撑出 2dp 空隙，视觉间距与改动前一致。
3. 收起动画结束后内容不占高度，**不影响外层窗口坐标**（展开只让内容向下长，不调整视口）。

文字行（text entry）保持不变，只补了同样的 2dp 内间距。

## 真机验证（7e7c5a68 / Android 15）

- `screenrecord` 20fps 录屏，操作序列：展开思考行 → 收起 → 展开工具行（web_search）→ 收起；
- 按帧差分：4 次操作对应 4 段连续变化，每段约 4 帧（≈200ms），说明是逐帧动画而非瞬间跳变；
- 展开中间帧可见内容以较低不透明度出现（fadeIn 中），高度逐步增大；
- 展开/收起前后，"↓ 回到最新"与输入框位置不变 → 窗口没有被移动；
- 收起后行间距与改动前一致（无 2dp 累积空隙）。

## 已知/后续

- 内层行详情很长时仍受组体高度上限约束（内部滚动），这是既有设计；
- sticky 小行头 + 零高度详情 item 的组合在极端长列表中未做专门压测（展开态 sticky 行为正常）；
- 若后续要更接近 DSH 的"折叠时给一行摘要"，可在收起态保留摘要行（当前摘要在 header 行里）。

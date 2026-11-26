# 用户消息分支（编辑重发）UI 完成报告（2026-11-26）

## 背景

DSH/DeepSeek 的真实语义（用户确认）：

- 编辑用户消息重发会**产生新分支**，不是原地覆盖；
- 对同一位置第二次编辑重发，会创建与第一次平行的另一个节点；
- 每个分支下面还能继续 `regenerate` 分叉。

后端在 B3 时已经具备：`ChatEditResend` 把新 user 挂到原 user 的 `parentId`，带 `revises`，
并把 `activeLeaf` 指到新分支；`ChatSend` 的上下文按 sidecar `activeLeaf` 回溯。
**缺的是 UI**：用户消息没有版本/分支切换器，旧分支只能在数据里存在、无法切回。

## 改动

### 1. `userVersionGroups`（`ChatContent.kt`，纯函数，可测）

用户消息没有 `nodeId`，版本关系由"同一个 `parentId`"表达：

```kotlin
internal fun userVersionGroups(messages: List<ChatMessageUi>): Map<String, List<ChatMessageUi>> {
    val groups = messages.filter { it.role == "user" }.groupBy { it.parentId ?: "\u0000root" }
    return buildMap { groups.values.forEach { vs -> vs.forEach { m -> m.messageId?.let { put(it, vs) } } } }
}
```

助手节点有 `nodeId` → 版本；用户节点共用 `parentId` → 分支。两者不会混淆。

### 2. 用户气泡下 `‹ N/M ›` 分支切换器（`UserBranchBar`）

- 仅当同组版本 >1 时显示，右下角小号，不影响气泡；
- 点击 ‹/›：`deepestLeaf(目标版本.messageId)` 得到该分支当前的叶子，
  写入本页 `activeLeaf` 并 `SessionSetActiveLeaf` 持久化；
- 切分支后不重新请求模型，直接用事件日志重建该分支（内容、过程组、来源都随分支切换）；
- `MainActivity.onActiveLeafChanged` 现在同时回写自身 `activeLeaf` 状态，避免 props 与页面状态不一致。

旧分支里的旧 user 消息会显示已有的"(已被后续编辑消息替代)"标记（B3 遗留能力），
再点 `‹` 可以随时切回去。

## 真机验证（7e7c5a68 / Android 15）

测试会话：两轮普通问答后，对第二条用户消息连续编辑重发两次。

1. 第一次编辑重发后：气泡下出现 `‹ 2/2 ›`，新回答来自编辑后的输入；
2. 点 `‹` 切到 `1/2`：显示原输入 + "(已被后续编辑消息替代)" + 原回答；
3. 点 `›` 切回 `2/2`：显示编辑后的输入与新回答；sidecar `activeLeaf` 同步变化；
4. 第二次编辑重发后：出现 `‹ 3/3 ›`，三个版本互为平行节点；
5. **分支上下文一致性**：切到 `1/3` 后发追问 "What was the number I said before?"，
   新 user 事件的 `parentId` = 旧分支的助手叶子（`…SWJTH1`），回答基于旧分支，说明 ChatSend 用的是选中分支的上下文。

事件日志（`01M8BW3P…`）可复核：

```
6  user      turn=2 parent=A1
7  assistant turn=2 parent=U2   (SWJTH1, 分支1)
10 user      turn=3 parent=A1  revises=U2   (C14R9V, 分支2)
11 assistant turn=3 parent=C14R9V
13 user      turn=4 parent=A1  revises=C14R9V (P001, 分支3)
14 assistant turn=4 parent=P001
18 user      turn=5 parent=SWJTH1  ← 切回分支1后的追问，上下文正确
```

## 单测

`VisibleMessagesTest` 新增 2 条（共 7 条全绿）：

- `edited user messages under same parent become branch versions`：三个并行版本分组正确；
- `user messages on different assistant branches are not merged`：不同助手分支下的用户消息不会被误合并。

## 已知遗留

- 切分支**没有做锚点修正**（DSH demo 会记 anchor 再按差值修正 scrollTop）；当前分支文本长度不同会导致列表轻微位移，连续点 ‹/› 时需要重新找按钮位置。属体验 polish。
- 没有独立"平行重发"按钮；"编辑并重发"本身就会产生平行节点，符合 DSH 语义。
- work 分支仍是**独立会话文件**（设计如此），不在本树内。
- 编辑对话框里的光标定位依赖 IME 行为（桌面/真机可能把插入点放在点击处），非分支逻辑问题。

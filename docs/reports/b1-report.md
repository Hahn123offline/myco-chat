# B1 消息模型报告（2026-11-26）

## 1. 目标与结论

为"消息操作条/重新生成/同节点版本/过程折叠/footer"补上持久化锚点。
**已完成**：assistant/user 消息带 id 与版本链；turn 结束带状态/耗时/主消息 id；usage 落盘；回放不丢 costUsd；
两方言解析 `reasoning_content/thinking` 与 `finish_reason`。

## 2. 事件模型变化

| 事件 | 新增字段 |
|---|---|
| `user/message` | `messageId` `nodeId` `turn` |
| `assistant/message` | `turn` `messageId` `nodeId` `version`(默认1) `finishReason` `reasoning`（usage 已有，现在真正写入） |
| `tool/call` | `turn` |
| `tool/result` | `turn` |
| `turn/end` | `status`(ok/stopped/error/truncated) `durationMs` `messageId`（costUsd 已有并保留在回放 JSON） |

- 全部为**可选字段**（除 turn/end.status 默认 "ok"），旧日志可读；`specs/session-events.schema.json` 已同步，
  fixtures 校验 **23/23**。
- 版本链语义：同一轮的主回复用 `nodeId` + `version=1`；工具步骤的中间 assistant 消息各自 messageId、无 nodeId。
  重新生成（B3）将新增同 `nodeId`、`version+1` 的 assistant 消息，不丢旧版本。

## 3. LLM 层

- 新 chunk `ChatChunk.ReasoningDelta`；`ChatChunk.Done.finishReason`。
- OpenAI 方言：解析 `delta.reasoning_content` 与 `choices[0].finish_reason`。
- Anthropic 方言：解析 `thinking_delta` 与 `message_delta.delta.stop_reason`。
- Bridge 落盘 reasoning，并发出 `BridgeEvent.ReasoningDelta`（GUI 后续折叠展示；当前 UI 忽略）。
- `AssistantDone` 事件带 `messageId/finishReason/usage`；`AssistantDelta` 带 `messageId`（流式气泡锚点）。

## 4. turn 状态

- 正常：`ok`（finishReason=length → `truncated`；content_filter → `error`）。
- 用户停止：`stopped` + assistant `finishReason="stopped"`，部分文本落盘（不再只写 `steps=0` 无状态）。
- LLM 失败：先写 `turn/end.status="error"` 再返回错误（此前失败不写 turn/end）。
- 超过工具步数：`error`。

## 5. 测试

`:core:test` **69** + `:bridge-http:test` **7** = **76 绿**。
新增：完整轮次字段断言（messageId/nodeId/version/finishReason/usage/turn status/duration/cost 回放）、
停止态 status/finishReason/messageId、OpenAI reasoning+finish_reason、Anthropic thinking+stop_reason。

## 6. 仍未做（B3/B4）

1. 历史 → 模型上下文仍是纯文本（tool/结果/图片/reasoning 不回灌）；带 tools 时 reasoning 回传是 B4 的 P0。
2. 没有 `chat.regenerate`/`editResend`（B3）；版本切换 UI 依赖它们。
3. turn 编号仍靠 count+1，无会话互斥（B3）。
4. `content_filter`/`insufficient_system_resource` 等 finishReason 归一化还需对照官方取值（B4）。

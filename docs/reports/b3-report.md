# B3 报告：重新生成、并发互斥、stop turnId（2026-11-26）

## 1. 已实现

### 会话互斥（防并发写坏日志）
- 每会话一把 `Mutex`（`sessionLocks`）：`chat.send` / `chat.regenerate` 串行执行。
- 第二次 send 会等待第一轮结束（排队语义），不会算出重复 turn 号或交叉写 JSONL。
- 测试：并发两个 send，turn 严格为 1、2。

### stop 带 turnId
- `Command.ChatStop(sessionId, turnId: Long? = null)`；`:activeTurns` 从 Job 改为 `ActiveTurn(job, turnId)`。
- turnId 不匹配返回 `TURN_MISMATCH`，**不会误停别的轮次**；不传仍兼容旧调用。

### 同节点重新生成（regenerate）
- `Command.ChatRegenerate(sessionId, messageId, requestId?)`：
  - 找到目标 assistant 的 `nodeId` 与原 turn；
  - 取该 turn 里位于它之前的 user 消息（文本+附件）；
  - 上下文截到该 user 消息之前，**不重复写 user 消息**；
  - 新 assistant 消息：同 `nodeId`、`version = max(现有版本)+1`，旧版本保留；
  - 新 turn 正常写 turn/start + assistant + turn/end；结果 JSON 带 `version/nodeId/messageId`。
- `buildMessages` 过滤：同一 nodeId 只把**最新 version** 放进模型上下文，避免重生成后旧回答重复。
- requestId 幂等：同一 regenerate 请求不会重复执行。
- HTTP：`chat.regenerate`、`chat.stop.turnId` 已映射；桌面/Android 客户端可调。

## 2. 测试

`:core:test` **77** + `:bridge-http:test` **7** = **84 绿**。
新增：regenerate 版本链（1/2 两版、旧版保留、nodeId 相同）、并发 send 串行 turn=1/2、stop 错误 turnId 不取消且正确 turnId 生效。

## 3. 未完成/下一步

1. **editResend 未做**：编辑用户消息后重跑需要"用户消息版本/撤销"语义（append-only 日志下的新消息 + supersede），留给 B3.5/U2。
2. 版本选择 UI（‹1/2›）未做：后端已能存多版本，UI 仍显示最新；U2 加。
3. 排队只是阻塞等待，没有 UI 的"排队/插话"；`stop` 的 turnId 由调用方传入，Android 尚未传（仍可空）。
4. regenerate 的上下文不含原 turn 的工具步骤（从原 user 消息前截断），对"重生成工具轮"是合理的；若要"沿用工具结果"是另一种语义，未做。
5. Android 还没有重新生成入口（U2 消息操作条）。

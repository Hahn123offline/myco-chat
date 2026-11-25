# M2 工具循环报告（2026-11-26 02:55）

## 1. 目标与结论

目标：两方言 tool calls + Bridge 真正执行工具 + 事件落盘 + schema 预算测试。
**已完成**，并用真实 DeepSeek API 在两种方言下各跑通一次"模型要求调用 → Bridge 执行 → 结果回灌 → 继续回答"。

## 2. 协议层

- `ChatClient.kt`：
  - `ToolSpec(name, description, parameters)`；`ChatRequest.tools`
  - `ChatMessage` 增加 `toolCalls` / `toolCallId` / `toolName`
  - `ChatChunk.ToolRequest(id, name, arguments)`
- `OpenAiCompatibleClient`：
  - 请求 `tools[{type:function,function:{name,description,parameters}}]`
  - assistant 消息携带 `tool_calls`；工具结果用 `role=tool, tool_call_id`
  - 流式 `delta.tool_calls[]` 按 index 累积 id/name，**arguments 跨 chunk 拼接**后解析，流结束统一发射
- `AnthropicCompatibleClient`：
  - 请求 `tools[{name,description,input_schema}]`
  - assistant `content` blocks 含 `tool_use`；工具结果转成 `user` + `tool_result`
  - `content_block_start(tool_use)` + `input_json_delta.partial_json` 累积 + `content_block_stop` 发射

## 3. Bridge 循环

- 每轮最多 `MAX_TOOL_STEPS=4` 次"模型↔工具"往返；超了明确 `MAX_STEPS` 失败（不无限烧钱）。
- 工具 schema 字符预算 `TOOL_BUDGET_CHARS=24000`：超预算在**写 turn 之前**拒绝（`TOOL_BUDGET`），不偷偷裁剪。
- 每次调用落 `tool/call`（name+args）与 `tool/result`（ok/text/reason）事件；assistant 文本按步落盘；
  `turn/end.steps` 记录实际步数，`costUsd` 汇总该回合。
- 新增 GUI 事件 `ToolStarted` / `ToolFinished`（in-process + http-sse 都会发），Android 聊天页显示"正在使用工具：xxx"。
- 停止生成仍然有效：取消时只落当前步的部分文本并标 `stopped`。

## 4. 测试（共 49：core 43 + bridge-http 6）

新增：
- `ToolCallParsingTest`：OpenAI 跨 chunk 参数累积；Anthropic `tool_use` 解析（含 id/name/args 断言）。
- `InProcessBridgeTest`：
  - `tool loop executes registry and feeds result back`：两步循环、工具执行、事件序列、`turn/end.steps=2`；
  - `tool schema over budget is rejected before any turn`：30 个工具超预算 → `TOOL_BUDGET` 且无 turn/start。

## 5. 真实链路证据

DeepSeek 官方：

```
# Anthropic Messages 方言
[tool] 调用 time_now …
[tool] time_now ok：{"local":"2026-11-26T02:50:33.5282953+08:00","epochMs":1795632633528}
现在是 2026-11-26 02:50:33（本地时间，UTC+08:00）。
--- done (turn 1) ---

# OpenAI 兼容方言
[tool] 调用 time_now …
[tool] time_now ok：{"local":"2026-11-26T02:50:39.6810785+08:00","epochMs":1795632639681}
现在是 2026年11月26日 02:50:39（本地时间，UTC+8）。
事件：tool/call seq=3，tool/result seq=4，turn/end steps=2 costUsd=7.1312E-4
```

## 6. 已知缺口（下一轮/后续）

1. 跨回合历史只回放 user/assistant 文本，**tool 调用上下文不进入下一回合**（本回合内正确）。下一步：
   从事件重建 assistant(tool_calls)+tool 结果，或把工具结果压缩进 assistant 文本。
2. Android 上工具事件只验证了编译与 UI 分支，未做真机点按（手机可用时补）。
3. 工具执行目前只有 `time_now`；M3 接 DeepSeek 附属能力（search/fetch/ocr/asr/tts）与成本声明。
4. 多个并行 tool call 已支持（同一轮多个），但 UI 只显示顺序"正在使用工具"。

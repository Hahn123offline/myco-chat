# I1 进度 · 错误/重试/用量/发现（2026-11-26 02:30）

> 本轮目标：不联网可验证的 I1 部分（重试、取消、用量、模型发现、doctor）。
> 验证：`:core:test` **32** + `:bridge-http:test` **5** = **37/37 绿**；`:app-android:assembleDebug` 成功（APK ≈9.5MB）。

## 1. 重试语义（两个方言统一）

- 触发条件：HTTP 429 / 5xx / 连接异常；**400/401/402 等不重试**（重试也只会更糟）。
- 退避：`min(8s, 400ms << (attempt-1))`，次数由 `provider.retry.maxRetries` 控制（上限 5）。
- 失败出口：`ChatChunk.Failed{ retriable }`，UI 据此决定是否给"重试"按钮——不把失败伪装成空回复。
- 测试（`LlmRetryTest`，4 条）：
  - 500×2 → 200：请求 3 次后成功；
  - 503 且重试耗尽：请求 2 次（maxRetries=1），返回 `retriable=true`；
  - 400：只请求 1 次，`retriable=false`；
  - Anthropic 方言：500→200 重试成功，并正确解析 `usage.input_tokens`。

## 2. 取消（"停止"必须真停）

- flow 取消时通过 job completion 回调 `disconnect()` 当前 `HttpURLConnection`，阻塞读立刻退出；
  不会出现"UI 停了、网络还在跑、最后又落一条 assistant/message"。
- 说明：客户端代码是 `channelFlow` + 子 job；取消路径已写但**尚未有自动化测试**（见 §5）。

## 3. 用量（usage）

- 两个方言都把 `usage` 放进 `ChatChunk.Done`：OpenAI 取 `prompt_tokens/completion_tokens/prompt_tokens_details.cached_tokens`，
  Anthropic 取 `message_start.input_tokens` + `message_delta.output_tokens`（缓存读 `cache_read_input_tokens`）。
- 已随 `assistant/message` 事件落盘；`turn/end.costUsd` 目前只在假模型/显式传入时出现——**定价表尚未实现**（I1 剩余项）。

## 4. 模型发现（显式动作）与 UI

- 新增 Bridge 命令 `ProviderDiscover(profile)` → `{models:[{id,displayName,contextWindow,maxTokens}]}`；两种传输都实现。
- 失败明确分类：`CREDENTIAL_MISSING` / `DISCOVERY_FAILED`（401/404 等由 `ModelDiscoveryException` 透传原因）。
- **Android 编辑对话框**新增「测试连通性」按钮：点击才探测、显示可用模型 id；保存时**绝不**自动探测。
- 说明：`provider-import/export` 已在 CLI；Android 上的导入导出按钮留到下一轮（不影响核心）。

## 5. doctor 扩展

新增/强化自检项：`settings`（providers.json revision）、`models`（启用 provider 能否解析 chat 角色）。
现在 doctor 共 9 项：store / sessions / providers / credentials / settings / models / tools / bridge。

## 6. 仍未做（需要真链路或后续）

1. **真链路 e2e**：需轮换后的 DeepSeek key，或用户授权对自建服务发一次最小请求（当前 `DEEPSEEK_API_KEY` 不在环境）。
   ⚠ 上一轮误提交的 `deepseek.key` 仍在工作区（未跟踪、已 ignore），**请轮换后再使用**。
2. 定价表与 costUsd 聚合（DeepSeek 峰谷/缓存命中价差；先把表结构放进 profile.pricing，后接 UI 显示）。
3. 取消的自动化测试（用慢速 SSE 服务器 + 超时断言连接被断开）。
4. Android 上的 provider 导入导出按钮；模型多行编辑。
5. 真机窗口（A8）：需要用户约 10–20 分钟。

## 7. 本轮补充（2026-11-26 03:00）

### 停止生成（端到端）
- `Command.ChatStop(sessionId)`：只取消该会话的流式收集 job；已收到的部分文本按 assistant/message 落盘，
  `turn/end.steps=0`，事件 `assistant.done{stopped=true}`；**不会留下卡死的 job**（随后再发一轮验证通过）。
- 无进行中回合时返回 `NOT_RUNNING`（明确错误，不静默）。
- 两种传输都实现（http-sse 的 `chat.stop` 与事件 `stopped` 字段）。
- Android 聊天页：生成中把“发送”换成「停止」按钮；停止后保留部分回复。
- 修了两个真问题：
  1. 直接 cancel 外层 async 会让调用方收到 CancellationException → 改为只取消“收集 job”，runChat 正常收尾；
  2. `invokeOnCompletion` 在阻塞读结束前不会触发 → 改为 `awaitCancellation` watcher 的 finally 里 disconnect，取消从 4.7s 降到 <3s。

### 用量成本（pricing）
- `ProviderProfile.pricing`（每 1M token：input/output/cachedInput，币种）+ JSON Schema 同步更新。
- `UsageCost.compute`：未命中输入 + 缓存命中输入 + 输出；**没配 pricing 就返回 null，不猜价格**。
- 两个方言在 `Done` 前自动算 `usage.costUsd`，随后进 `assistant/message` 与 `turn/end`。
- 测试：纯函数成本断言（1000/500/200 → 0.00095 USD）；未配置 pricing 返回 null；取消测试断言 <3s 且没有 Done。

### 当前测试
`:core:test` **37** + `:bridge-http:test` **5** = **42/42 绿**；`:app-android:assembleDebug` 与 `:app-desktop:installDist` 成功。

### 仍未做
- 真链路 e2e（等 key/授权）；DeepSeek 峰谷价格窗口（pricing 字段已留，乘法未做）；Android 导入导出按钮；真机窗口。

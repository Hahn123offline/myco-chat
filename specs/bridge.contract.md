# Bridge 契约 v1（GUI 与核心之间的唯一接口）

> 目的：GUI 是模块，不是核心的一部分。**没有 GUI，核心照跑**（headless 验收项）。
> GUI 只能通过 Bridge 说话：不直接读存储、不 import 核心内部、不持有会话状态机。
> 语言无关：本文件描述协议语义；各栈实现各自的 Bridge（Kotlin / TS / 任意）。

## 1. 传输（至少两种，语义相同）

| 传输 | 用途 | 约束 |
|---|---|---|
| `in-process` | 手机/桌面原生 GUI（默认） | 同一进程直接调用；不开端口；无鉴权（进程内） |
| `http-sse` | 浏览器 / 远程客户端 / 调试 | 仅本机回环或经 HTTPS 隧道；需 bearer token（写在 `~/.myco/runtime.json`，0600） |

两种传输必须跑**同一套契约测试**（同一命令/查询/事件序列，断言结果一致）。
`bridgeVersion: 1`；客户端必须拒绝未知 major。

## 2. 消息模型

```
Command   { requestId, name, payload }          GUI → core
Result    { requestId, ok, data?, error? }      core → GUI（命令应答）
Query     { requestId, name, payload }          GUI → core（只读）
Result    { requestId, ok, data?, error? }      core → GUI（查询应答）
Event     { seq, sessionId?, type, data }       core → GUI（推送，至少一次）
```

- `requestId` 由 GUI 生成，用于对账；重发同一 `requestId` 必须幂等。
- `Event.seq` 是**每会话单调**；GUI 断线重连带 `fromSeq` 重放，服务端不保证不重复，客户端按 `seq` 去重。
- 错误统一：`{ code, message, retriable, reason? }`；`code` 是稳定枚举，**客户端不得解析 message 文案**。

## 3. 命令（v1 最小集）

| name | payload | 结果 | 备注 |
|---|---|---|---|
| `chat.send` | `{ sessionId, text }` | `{ turnId }` | 触发一轮；流式内容走 Event |
| `chat.stop` | `{ sessionId, turnId }` | `{ stopped: bool }` | 取消当前请求 |
| `session.create` | `{ app: 'chat'|'work', title? }` | `{ sessionId }` | chat 默认单会话策略在 app 配置里 |
| `session.rename` | `{ sessionId, title }` | `{ ok }` | |
| `session.delete` | `{ sessionId }` | `{ ok }` | 软删（可恢复） |
| `provider.upsert` | `{ profile, expectedRevision? }` | `{ revision }` | 冲突回 `settings/conflict` |
| `provider.delete` | `{ id, expectedRevision? }` | `{ revision }` | 只清本 app 派生凭据 |
| `provider.discoverModels` | `{ profile }` | `{ models[] }` | 显式动作，保存时绝不自动探测 |
| `credential.set` | `{ ref, value }` | `{ ok }` | 只写；永不回读值 |
| `credential.describe` | `{ ref }` | `{ configured, source, writable }` | 无 value 字段 |

## 4. 查询（v1 最小集）

| name | payload | 结果 |
|---|---|---|
| `sessions.list` | `{ app? }` | `{ sessions: SessionMeta[] }` |
| `session.events` | `{ sessionId, fromSeq? }` | `{ events[] }` |
| `providers.list` | `{}` | `{ providers[], revision }` |
| `doctor.run` | `{}` | `{ checks[] }`（与 `myco doctor` 同源） |
| `persona.cards` | `{}` | `{ cards[], activeCardId, versionsByCard }` |
| `sync.preview` | `{ kinds[] }` | `{ items[], conflicts[] }`（拉取预览，不改本地） |

## 5. 事件（v1 最小集）

| type | data | 说明 |
|---|---|---|
| `assistant.delta` | `{ turnId, text }` | 流式增量 |
| `assistant.done` | `{ turnId, text, usage?, model? }` | 一轮结束（等价持久事件 `assistant/message`） |
| `tool.call` | `{ turnId, name, args }` | 工具调用开始 |
| `tool.result` | `{ turnId, name, ok, text, reason? }` | 工具结果 |
| `session.updated` | `{ sessionId, title? }` | 列表刷新用 |
| `error` | `{ code, message, retriable }` | 非命令应答的异步错误 |
| `delivery.new` | `{ sessionId, why }` | 主动消息投递（GUI 决定是否提醒） |

## 6. 状态归属（重要）

- **核心持有**：会话事件日志、provider 档案、人格卡版本、记忆、投递队列、一轮的状态机。
- **GUI 持有**：渲染状态、滚动位置、输入框草稿、导航栈、通知权限状态。
- **禁止**：GUI 直接读写 `~/.myco/` 下任何文件；GUI 自行拼模型请求；GUI 缓存"真相"超过展示需要。

## 7. 失败语义（写进契约，避免静默）

- 流中断：发 `error{retriable:true}`，GUI 展示"可重试"，**不得假装完成**。
- 工具失败：`tool.result.ok=false` 且带 `reason`；模型必须看到该 reason。
- 存储写失败：命令应答 `ok:false`；已写一半的事件由读侧丢弃不完整尾行并 warn（见事件规范）。
- 未绑定能力（如未配 provider）：`error{code:'PROVIDER_MISSING'}`，GUI 引导到设置页，不静默降级到假回答。

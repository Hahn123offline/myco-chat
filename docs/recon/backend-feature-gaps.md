# myco 后端能力缺口对照（2026-11-26）

> **口径**：目标 = 手机端 agent/chat UI 需要的每一项能力；基线 = 当前 `core/` 后端实现。
> **方法**：只读代码/规格（未改代码、未调模型 API、未跑测试）。文档层面本轮新抓取 DeepSeek 官方 thinking_mode 页（2026-11-26），其余 API 事实引用 `docs/recon/deepseek-api.md`（2026-11-25 抓取，本文件只做复核不做复述）。
> **证据标记**：`[代码已确认]` 我读到的实现 · `[文档已确认]` 官方文档/规格明文 · `[推断]` 有依据但未实测 · `[需要产品决定]`。
> 已读：`specs/bridge.contract.md`、`specs/session-events.schema.json`、`specs/provider-profile.schema.json`、`specs/fixtures/fixtures.json`；`bridge/Bridge.kt`、`bridge/InProcessBridge.kt`；`session/SessionEvent.kt`、`session/SessionStore.kt`；`llm/*`；`provider/*`；`attachment/AttachmentStore.kt`；`capability/*`；`tools/ToolRegistry.kt`；`spec/*`；`doctor/Doctor.kt`、`MycoHome.kt`；`bridge-http/*`；`app-android/*`；`docs/design/60-mvp-closed-loop-plan.md`、`docs/recon/mobile-chat-ux.md`、`docs/reports/mvp-close.md`。

---

## 0. 一页结论（P0 清单）

**P0 清单（不做则 UI 落地后会被后端卡住，或直接出正确性事故）**

| # | P0 缺口 | 一句话理由 | 规模 |
|---|---|---|---|
| P0-1 | 事件流没有 `seq`、没有重放、没有 `requestId` 幂等；`Failed` 事件不带 `sessionId` | 契约 §2 已承诺，代码层 `BridgeEvent` 连 `seq` 字段都没有。断线、切会话、平板第二窗口、桌面浏览器全部拿不到在途内容，也无法去重 | 中 |
| P0-2 | 事件模型没有 `turn`/`messageId`/结束状态/用量：`assistant/message` 无 turn，`turn/end` 无 status、回放丢 `costUsd`，`assistant/message.usage` 从不写 | 重新生成、编辑重发、分支、点赞、操作条、"停止后重开仍显示停止态"、footer 成本——全部缺锚点 | 中 |
| P0-3 | 历史 → 模型上下文只重建纯文本（`buildMessages` 只取 user/assistant.text） | 工具调用/结果、历史图片、推理内容全丢；纯图消息的 `text=""` 会作为空 user content 回灌（[推断] 部分 provider 400） | 中 |
| P0-4 | 无 `chat.regenerate` / `chat.editResend`；`turn` 编号靠 `count(turn/start)+1`，且无会话级互斥 | 消息操作条的两个核心动作没有后端入口；并发两次 `chat.send` 会算出重复 turn 号并交叉写同一 JSONL | 中 |
| P0-5 | 会话列表/搜索数据不够：无 `lastMessageAt`/预览/`pinned` 排序/归档（sidecar 有 `pinned` 字段但无命令）、无 `archivedAt`、无 filters/分页、命中定位不到 `seq` | "历史 + 搜索 + 置顶归档"是 chat 单焦点形态的**唯一**历史入口（`mobile-chat-ux.md` §5.3），数据缺一项就做不出来 | 中 |
| P0-6 | LLM 正确性：两方言都不解析 `reasoning_content`；OpenAI 方言连 `thinking` 字段都不发（DeepSeek 默认开思考）；未解析 `finish_reason`；流式 usage 未确认 | 带 tools 时 `reasoning_content` 必须原样回传否则 400（[文档已确认]）；不关思考 = 成本/延迟失控；无 finish_reason 无法区分"截断" | 小~中 |
| P0-7 | 工具执行无审批、无路径白名单：`ocr_image`/`transcribe_audio` 接受模型给的任意本地 `path` | 模型可读取 `/sdcard` 任意文件或 `~/.myco/credentials.json` 并上传到云端；这是"隐私默认本地"承诺的直接漏洞 | 小~中 |
| P0-8 | 附件无缩略图/预览查询；`attachment.save` 一次性 base64 全量 | 32MB 图 → 约 43MB 请求体 + 解码峰值；列表/历史里无法做图片预览，只能原图解码（OOM 风险） | 中 |

**最容易被"UI 先做"掩盖的三件事**（详见 §5/§6）：
1. **思考模式的 reasoning_content 回传**——多轮工具循环只要真开思考，第二次请求就 400；而 UI 上只表现为"发不出去"。
2. **并发写同一会话**——排队/插话/长按中断一旦做进 UI，就会撞上没有互斥的 `SessionStore`，产生重复 turn 号与交错事件。
3. **`tryEmit` 丢事件**——`MutableSharedFlow(replay=0, extraBufferCapacity=256)`（`InProcessBridge.kt:40`），订阅者慢或晚订阅就会静默丢 delta/tool 事件，与契约"至少一次"矛盾。

---

## 1. 现状速览

### 1.1 架构（[代码已确认]）

```
GUI(Compose) ──Bridge(Command/Query)──> InProcessBridge ──> SessionStore(JSONL+sidecar)
   ▲                │                                      ├─ ProviderStore(providers.json, revision 信封)
   └── Flow<BridgeEvent>(内存)                              ├─ CredentialResolver(env > credentials.json)
        http-sse: HttpBridgeServer /command /query /events    ├─ AttachmentStore(<home>/attachments/<sid>/)
                                                              ├─ ToolRegistry(time_now + web_search/read_url/ocr/asr)
                                                              └─ ChatClients → OpenAi / Anthropic 两方言
```

- `chat.send` 是**阻塞跑完一轮才返回**（`InProcessBridge.kt:183-357`），流式内容只走内存事件。
- 事件流：`_events = MutableSharedFlow(extraBufferCapacity = 256)`，`replay = 0`，全部 `tryEmit`（`InProcessBridge.kt:40-41`）。

### 1.2 已实现面

| 层 | 数量 | 明细 |
|---|---|---|
| 命令 | 12 | `chat.send` `chat.stop` `session.create` `session.rename` `session.delete` `session.restore` `attachment.save` `provider.save` `provider.delete` `provider.replace` `provider.discover` `credential.set`（`Bridge.kt:13-57`） |
| 查询 | 6 | `session.list` `session.events` `session.search` `providers` `doctor.run` `provider.export`（`Bridge.kt:59-70`） |
| 运行期事件 | 6 | `assistant.delta` `assistant.done(stopped)` `failed` `session.updated` `tool.started` `tool.finished`（`Bridge.kt:72-94`） |
| 持久事件 | 8 | `session/start` `turn/start` `user/message` `assistant/message` `tool/call` `tool/result` `system/inject` `turn/end`（`SessionEvent.kt`） |
| 可变元数据 | — | `meta.v1.json`：`title` `deletedAt` `pinned`（`SessionStore.kt:17-22`） |

### 1.3 契约 vs 实现的 drift（都是"UI 会踩"的）

| 契约承诺 | 实际 | 影响 |
|---|---|---|
| `Command{requestId,...}`、重发同 requestId 幂等 | 传输层完全没有 `requestId`（`HttpBridgeServer` 不读，`Bridge.send(cmd)` 无此参数） | 重试 send 会跑两轮、写两遍事件 [代码已确认] |
| `Event{seq,...}`、每会话单调、`fromSeq` 重放、按 seq 去重 | `BridgeEvent` 无 `seq`；`/events` 只订阅"当下"；`fromSeq` 只存在于 `Query.SessionEvents`（读盘，不是流） | 断线/多窗口无法增量恢复 [代码已确认] |
| `chat.stop{ sessionId, turnId }` | `Command.ChatStop(sessionId)` 无 turnId | 停止靠表 `activeTurns[sessionId]`，第二轮会覆盖第一轮 job 引用 [代码已确认] |
| `delivery.new` 事件、核心持有投递队列 | 全仓无 delivery 相关代码 | 通知跳转/Push 无入口 [代码已确认] |
| `persona.cards`、`sync.preview` 查询 | 未实现；`SessionSidecar` 也没有 revision | 同步预览无法实现 [代码已确认] |
| `credential.describe` 命令 | 无此命令，凭据状态塞在 `provider` 查询结果里 | 契约测试会缺一条 [代码已确认] |
| `provider.discoverModels` | HTTP 路由叫 `/command/provider.discover` | 命名 drift，跨栈客户端会 404 [代码已确认] |
| `bridgeVersion: 1` 校验 | `/health` 返回，客户端 `HttpBridgeClient` 不校验 major | 低风险 [代码已确认] |

### 1.4 一轮的真实数据流（决定后面所有缺口）

`chat.send` → 校验 provider/model/tool 预算 → 读全量历史 → 附件一次性读入 → **`turn = count(TurnStart)+1`** → append `turn/start` + `user/message` → async 循环（`MAX_TOOL_STEPS=4`）→ 每步 append `assistant/message`（usage 恒为 null）→ 工具 append `tool/call`+`tool/result` → 结束 append `turn/end(steps, costUsd)` → 发 `assistant.done`。停止 = cancel async，把当前步部分文本 append 成 `assistant/message`，`turn/end.steps=0`（无"已停止"标记）。

---

## 2. 能力矩阵

> 优先级：**P0** = 不做则本期 UI 落不了地/出正确性事故；**P1** = 体验必需；**P2** = 后续。
> 表格按"UI 功能/交互"排列，R 编号供 §7 路线图引用。

| # | 功能 | 需要的数据/API/事件 | 现有实现 | 差距 | 建议改动（文件/接口） | 优先级 |
|---|---|---|---|---|---|---|
| R1 | 会话列表 / 历史面板 | `sessions.list` 返回 `lastMessageAt/messageCount/preview/pinned/archivedAt/modelId` | 只 6 字段：`id,title,createdAt,app,preset,formatVersion`；按 `createdAt` 倒序；`pinned` 字段存在但无命令、不参与排序 | 无最近活动时间/预览/未读/置顶排序/归档区分；会话更新不发 `session.updated`（新一轮结束不刷新列表） | `spec/SessionMeta` 扩展；`SessionStore.list()` 合并 sidecar + 统计；`InProcessBridge.sessionJson`；turn 结束时补发 `session.updated` | P0 |
| R2 | 抽屉/历史搜索 | `search{query, filters{app,archived,from,to,role}, cursor, limit}` → `[{sessionId, seq, role, snippet, title, at}]` | `SessionStore.search`：遍历全部会话、逐个全量读 JSONL、返回会话级 snippet（`SessionStore.kt:156-187`） | 命中定位不到 `seq`（无法"跳到那条"）；O(会话×事件)；无 filters/分页；软删被排除、归档概念不存在 | 建索引（§6）；`Query.SessionSearch` 加 payload 字段与 `seq/role` 返回；`InProcessBridge` 映射 | P0 |
| R3 | 五档时间分组 | 只需 `createdAt`（有）；若按"最近活动"分组需 `lastMessageAt` | `createdAt` 有 | 按最近活动分组时缺字段 | 同 R1 | P1 |
| R4 | 置顶 / 归档 | `session.pin` `session.archive/unarchive`；list 返回 `pinned/archivedAt` 并排序 | `SessionSidecar.pinned` 字段有，**无命令、无排序、无 `archivedAt`**；只有 `deletedAt`（软删） | 归档与删除混为一谈，`mobile-chat-ux.md` §5.3 要求的"归档不是删除"无法表达 | `SessionStore` 加 `archivedAt` + `pin/archive`；`Bridge` 加两命令；list 默认排除 archived+deleted，支持 `includeArchived` | P0 |
| R5 | 滑动删除 + 撤销 | 软删（有）、恢复（有）、永久删除（缺）、撤销条（GUI） | 软删/恢复有；无永久删除；删除不清理附件 | 撤销可做；永久删除与附件 GC 缺 | `Command.SessionDeletePermanently`；`AttachmentStore.deleteSession`；sidecar `deletedAt` 已够 | P1 |
| R6 | 原位重命名 | `session.rename` | 有（title ≤80，只写 sidecar，不动事件日志） | 无（GUI 原位输入即可） | 无需后端；可顺手合并成 `session.update{title?,pinned?,archived?}` | — |
| R7 | 自动标题 | turn 结束后生成标题（本地截断 或 DeepSeek `title:{"enabled":true}` [文档已确认]） | 无，标题固定"新会话"直到手改 | 全缺 | 后台任务写 sidecar.title + 推送 `session.updated`；或 `session.autoTitle` 命令 | P1 |
| R8 | 多选批量删除 | 批量命令或逐条 + 逐条结果（失败逐条给原因） | 单条 | 缺 | `Command.SessionDeleteBatch{ids}` → `{results:[{id,ok,code}]}` | P2 |
| R9 | Markdown 渲染 | 完整文本（有）+ 代码块/链接原样 | `assistant/message.text` 原样保存 | **后端无需改**（渲染在 GUI）；只需保证不裁剪 | 无 | — |
| R10 | 流式五态（pending/streaming/done/stopped/error） | `turn.started`；`assistant.done{stopped,finishReason,usage}`；`error{sessionId,turnId,code,retriable,stage}`；持久 `turn/end.status` | 有 delta/done(stopped)/failed；`Failed` 无 `sessionId/turnId`；`turn/end` 无 status；无 turn.started | 重启后无法区分"停止"与"完成"；错误无法路由到会话（`Bridge.kt:80`）；GUI 只能自造 pending | 见 §3/§4：`turn/end` 加 `status`，`Failed` 带 `sessionId/turnId`，新增 `turn.started` | P0 |
| R11 | 消息操作条（复制/分享/引用/更多） | 稳定 message 标识（`seq` 可当，但需 `turn` + `messageId`）；引用需 `replyToSeq` | `seq` 有；无 messageId/turn；无引用字段 | 引用/分享无接口；操作条无法把"这条"映射到 turn（工具步骤会产生多条 assistant/message） | `user/message.data` 加 `replyToSeq`、`messageId`；导出命令（R16） | P1 |
| R12 | 重新生成 | `chat.regenerate{sessionId, turnId\|messageSeq, model?}` → 新 turn；上下文截断到该用户消息 | **无**；`runChat` 只能 append | 全缺；且 turn 编号依赖 `count(TurnStart)`，重跑会算错 | 新命令 + 从事件重建上下文 + 语义决策（覆盖 or 分支，见 §8） | P0 |
| R13 | 编辑用户消息重发 | `chat.editResend{sessionId, messageSeq, text, attachments}` → 新 turn + 记录 `editOfSeq` | **无** | 全缺 | 同 R12；`UserMessage.data` 加 `editOfSeq` | P0 |
| R14 | 分支 ‹1/3› | 分支结构（`branchId`/`parent`）+ 切换查询 | 无；append-only JSONL 无法表达 | 结构性缺口 → 影响 `formatVersion` | 最小方案：事件加 `branchId` + sidecar `branches{id,headSeq,active}`；`formatVersion=2`（见 §3.5） | P1 [需要产品决定] |
| R15 | 点赞/反馈 | `message.feedback{sessionId, seq, rating: up\|down\|none, reason?}` | 无 | 全缺；反馈**可变**，不应进事件日志 | sidecar `feedback.v1.json` + 命令/查询（§3.4） | P1 |
| R16 | 分享/导出 | `session.export{format: md\|jsonl}` → 内容（GUI 写文件） | 只有 `provider.export` | 全缺 | `Query.SessionExport`；markdown 版带成本/模型元信息 | P1 |
| R17 | 语音输入（ASR） | `voice.transcribe{audioBase64 或 attachmentRef, mime, durationMs}` → `{text, costCny}` | 只有模型工具 `transcribe_audio(本地 path)`（`CapabilityTools.kt:136-159`）和 `DeepSeekToolsClient.asr()`；**无 Bridge 命令** | GUI 录完音没有入口；模型工具不适合输入法转写（会进模型轮次） | `Command.VoiceTranscribe`（复用 asr；音频落 attachment，成本进 usage） | P1 |
| R18 | TTS 朗读 | `tts.speak{text, voiceId}` → 音频字节或 attachment ref | `DeepSeekToolsClient.tts()` 仅桌面 CLI 用；未注册工具、无命令 | 缺；`AttachmentRef.kind` 只有 image/text/file，需加 `audio` | `Command.TtsSpeak`；`spec/Attachment.kt` kind 扩展（schema 同步） | P2 |
| R19 | 工具/思考折叠 | `tool.call/result`（有）+ 思考文本（缺）+ 每步归属 turn（缺）+ `callId`/耗时（缺） | `ToolCall/ToolResult` 有 name/args/text/ok；无 turn/callId/duration；无 reasoning | 折叠分组只能靠位置猜；思考不可展示（DSH Go 的"折叠成一行"做不了） | 加 `turn`/`callId`/`durationMs`；新增 `assistant/reasoning` 事件（是否落盘见 §8） | P1 |
| R20 | footer（步骤/耗时/成本） | `turn/end{steps,status,usage,costUsd,durationMs,model}` 且回放保留 | 落盘有 `steps/costUsd`；`eventJson` 回放**丢掉 costUsd**（`InProcessBridge.kt:486`）；`assistant/message.usage` 恒 null；无 duration/model | 历史 footer 显示不出成本；token 数完全不可见；运行中的 `assistant.done` 也不带 usage | 补齐 `turn/end` 字段 + `eventJson` 全量输出 + usage 落 `assistant/message` | P1 |
| R21 | 权限审批 / "自动允许中"指示 | `tool.approval_required{callId,name,args,risk}` + `tool.approve{callId,decision,remember}` | **无**；`tools.execute` 直接执行（`ToolRegistry.kt:39-42`） | 全缺；且风险真实（P0-7） | `ToolRegistry` 加 risk/policy；Bridge 审批挂起；默认未批准不执行 | **P0（安全）** |
| R22 | 文件上传状态 | 分块/进度（http 传输）、单轮附件数/总大小上限、失败可重试 | `attachment.save` 一次性 base64（32MB 图 → ~43MB 请求体）；无进度/分块/数量上限 | 大图内存峰值高；长传无进度；失败只能整条重来 | 分块上传命令；`AttachmentStore` 降采样/压缩；上限策略 | P1 |
| R23 | 图片预览/缩略图 | `attachment.thumbnail{sessionId,id,maxPx}` → 小图；历史消息可重建预览 | 无缩略图；只存原图；`AttachmentRef` 无尺寸 | 全缺（`mvp-close.md` §4.3 已知） | `save` 时生成缩略图 + 查询命令；`AttachmentRef` 加 `width/height/thumb` | P0 |
| R24 | 草稿 | 契约 §6 说"GUI 持有"；但进程被杀/平板第二窗口要求一致 | 无后端 | 与契约冲突：内存草稿跨进程/跨窗口不一致 | sidecar `draft{text, attachmentIds, updatedAt}` + 可选 `session.draft.set/get` | P1 [需要产品决定] |
| R25 | 通知跳转 | `delivery.new` + 深链 `sessionId/turnId`；FGS 通知 `PendingIntent` | 无 delivery 队列；`MycoForegroundService` 通知**无 PendingIntent**（`MycoForegroundService.kt:26-33`） | 全缺 | 投递队列（契约 §6 已声明核心持有）+ `delivery.new` + PendingIntent extra | P1 |
| R26 | 离线/重连 | 事件带 `seq` + `events.replay(fromSeq)` 或 SSE `Last-Event-ID` + 每会话环形缓冲 | 无 seq、无 replay、SharedFlow `replay=0` | 断线即丢在途增量；http 客户端无法去重；切会话回来看不到进行中的流 | 见 §4.3/§4.4 | P0 |
| R27 | 多窗口/平板 | 多客户端独立订阅 + `fromSeq` 重放 + 会话级写锁 + http CORS | 单 Flow 广播、无缓冲、无锁、无 CORS 头（`HttpBridgeServer.kt` 全文无 `Access-Control-*`） | 第二窗口错过事件；并发写 turn 编号冲突；浏览器直连被 CORS 挡 | 每客户端 `fromSeq` 拉取 + `Mutex` per session；补 CORS/OPTIONS | P1 |
| R28 | 排队 / 插话 / 中断（DSH Go 三选） | `chat.send` 运行中语义（queue/steer/interrupt）+ 队列状态事件 | `chat.send` 并发**无任何保护**：第二个 turn 直接开跑、覆盖 `activeTurns[sessionId]`（`InProcessBridge.kt:318`） | 全缺；且并发写会破坏 turn 编号与事件顺序 | 会话级互斥 + `chat.queue.*` 命令 + 队列事件（依赖 P0-4 的锁） | P1 |
| R29 | 模型快捷切换 | session 级 model 覆盖；`chat.send{model?}`；可选 provider 列表 | `providers.firstEnabled()` 固定第一个（`InProcessBridge.kt:184`） | 无 | sidecar `modelId`；`ChatSend{model?}`；`session.model.set` | P1 |
| R30 | 轮次面板（长会话寻址） | `session.turns` 索引（turn/at/preview/seqRange）+ events 分页 | 无；`Query.SessionEvents` 一次返回全量 | 长会话一次性把全部事件塞给 GUI | `Query.SessionTurns`；`events{limit,cursor}` | P2 |
| R31 | 空态/错误分类 | 稳定 error code 枚举 + 分类（网络/Key/余额/限流/繁忙） | 散字符串：`PROVIDER_MISSING` `MODEL_MISSING` `LLM_FAILED` `TOOL_BUDGET`…；`LLM_FAILED` 只带原始 message（`InProcessBridge.kt:345-348`） | GUI 无法映射成 6 类动作按钮 | 统一 `bridge/ErrorCodes.kt`；把 HTTP 402/401/429 映射为 `KEY_INVALID/INSUFFICIENT_BALANCE/RATE_LIMIT` | P1 |

---

## 3. 事件与数据模型建议

### 3.1 结论

**8 类事件 + sidecar 够做"可回放的单轮记录"，不够做"可操作的消息"。** 缺口集中在三处：① 缺"归属"（turn/messageId/callId）；② 缺"结束语义"（status/finishReason/usage）；③ 缺"可变状态"的归位（pin/archive/feedback/draft/read 都要 sidecar，不能进 append-only 日志）。

### 3.2 必须补的字段（全部 optional，读侧容错）

| 事件 | 建议新增 | 为什么必须有 |
|---|---|---|
| `session/start` | 不变（`formatVersion` 仍 1） | — |
| `turn/start` | `turn`（有）+ `model`、`providerId`、`trigger`（human/regenerate/edit/continue） | 重跑/续跑要能解释"这一轮为什么来" |
| `user/message` | `turn`、`messageId`、`replyToSeq`、`editOfSeq`、`clientId` | 编辑重发/引用/幂等去重 |
| `assistant/message` | `turn`、`messageId`、`usage{input,output,cached,costUsd}`、`finishReason`、`durationMs`、`reasoningText?` | footer、成本账本、regenerate 定位、"截断"与"正常"区分 |
| `tool/call` | `turn`、`callId`、`messageId` | 多工具/多步时把调用与结果配对、折叠分组 |
| `tool/result` | `turn`、`callId`、`durationMs`、`costCny`、`truncated` | 折叠行的耗时/成本；能力计费进账本 |
| `system/inject` | `turn?` | 归属 |
| `turn/end` | `status: completed\|stopped\|failed`、`usage`、`model`、`durationMs`、`errorCode?` | **"停止"必须是可持久状态**（`mobile-chat-ux.md` §6 第 6 条禁止把停止做成异常） |

### 3.3 建议新增事件（追加型，不影响旧读）

| 事件 | data | 用途 | 优先级 |
|---|---|---|---|
| `turn/started`（运行期等价物） | `{sessionId, turnId, model, at}` | pending 态由核心给出，不再让 GUI 猜 | P0 |
| `assistant/reasoning`（或 delta 加 `kind:"reasoning"`） | `{turnId, text}` | 思考折叠；是否落盘见 §8 | P1 |
| `tool/approval_required` | `{turnId, callId, name, args, risk}` | 权限审批 UI | P0-s |
| `usage/updated` | `{turnId, sessionTotal}` | footer 实时刷新 / 成本页 | P1 |
| `delivery/new`（契约已有） | `{sessionId, why, kind}` | 通知跳转 | P1 |

### 3.4 sidecar（可变状态）归属

`meta.v1.json` 建议扩为（`schemaVersion: 2`，全部 optional、读侧 `ignoreUnknownKeys=true`）：

```json
{ "schemaVersion": 2, "title": "...", "pinned": false, "archivedAt": null, "deletedAt": null,
  "modelId": null, "lastReadSeq": 0, "notifiedAt": 0,
  "draft": { "text": "...", "attachmentIds": [], "updatedAt": 0 },
  "usage": { "turns": 12, "inputTokens": 0, "outputTokens": 0, "costUsd": 0.0 } }
```

**必须放 sidecar（不要进事件日志）**：标题覆盖、置顶、归档、软删、草稿、已读位、通知已推、会话级模型覆盖、usage 汇总缓存。
**必须留事件日志（影响模型上下文/可审计）**：一切 user/assistant/tool/system 内容、turn 边界、分支结构、审批决定（审批是一次性事实，但要能审计，可进日志或独立 `audit.jsonl`）。
**独立文件**：`feedback.v1.json`（消息级点赞/原因，按 `seq` 键；可变、不参与上下文）、`usage.v1.jsonl`（账本，追加写，一条 turn 一行）。

### 3.5 `formatVersion` 与向后兼容

- **不升版本也能加字段**的前提是读侧宽松：`MycoJson` 现在 `ignoreUnknownKeys = false`（`spec/Models.kt:9-15`），而 `SessionStore.read` 就用它解码（`SessionStore.kt:86`）。**只要给 `assistant/message` 加一个 `turn` 字段，任何旧二进制读新日志都会抛 `CorruptSessionException`**——这是"多端/旧版本"场景的地雷。
- 建议拆两个 Json 实例：写侧严格（保持现状，防拼错）、读侧 `ignoreUnknownKeys = true` + `explicitNulls=false`。这一步应该和 P0-2 同时做，否则加字段即破坏兼容。
- `specs/session-events.schema.json` 每个 `data` 都是 `additionalProperties:false`：加 optional 字段要**同步改 schema**（升 `$id` 侧注释为 v1.1），但语义上仍是 `formatVersion:1`（旧文件全部仍合法）。
- **只有结构性变化才升 `formatVersion:2`**：`branchId`/分支指针、`seq` 语义变化、事件类型改名。届时 `SessionStore.list/read` 必须按首事件版本分流，且**不自动迁移**（现有不变量：拒绝打不开，不静默改）。

### 3.6 事件日志的读侧不变量风险

- `seqCache` 是普通 `mutableMapOf`（`SessionStore.kt:40`），两个并发 turn 会竞争 `append`，可能算出同一个 `next`（P0-4 的一部分）。
- `readFrom` 每次 `Files.readAllLines` 全量解析（`SessionStore.kt:80`），`search` 对每个会话再读一遍 → 长会话 × 多会话 = O(n²) 级 IO（§6.2）。

---

## 4. Bridge 改造清单

### 4.1 命令（新增/修改）

| name | payload | result | 依赖/备注 | 优先级 |
|---|---|---|---|---|
| `chat.send`（改） | + `clientId`、`model?`、`replyToSeq?` | `{turnId}` | `clientId` 做幂等；没有它重试必重复 | P0 |
| `chat.regenerate` | `{sessionId, turnId 或 messageSeq, model?}` | `{turnId}` | 从事件重建"到该 user/message 为止"的上下文；旧回答处理见 §8 | P0 |
| `chat.editResend` | `{sessionId, messageSeq, text, attachments[]}` | `{turnId}` | 写 `editOfSeq` | P0 |
| `chat.stop`（改） | + `turnId` | `{stopped}` | 现在只能按 sessionId 停，多轮/排队下会停错 | P1 |
| `chat.continue` | `{sessionId}` | `{turnId}` | 停止后续跑；可用 DeepSeek prefix completion（beta）[文档已确认路径存在] | P2 |
| `session.update` | `{sessionId, title?, pinned?, archived?}` | `{ok}` | 合并替代 pin/archive 分开 | P0 |
| `session.deletePermanently` | `{sessionId}` | `{ok}` | 级联附件 | P1 |
| `session.export` | `{sessionId, format: md\|jsonl}` | `{content}` | GUI 写文件，核心不碰路径 | P1 |
| `session.draft.set` | `{sessionId, text, attachmentIds[]}` | `{ok}` | 与契约 §6 冲突，见 §8 | P1 |
| `message.feedback` | `{sessionId, seq, rating, reason?}` | `{ok}` | 走 sidecar | P1 |
| `attachment.thumbnail` | `{sessionId, id, maxPx}` | `{bytesRef}` 或字节 | in-process 返回值、http 走二进制端点 | P0 |
| `voice.transcribe` | `{audio, mime, durationMs}` | `{text, costCny}` | 复用 asr | P1 |
| `tts.speak` | `{text, voiceId}` | `{attachmentRef}` | kind 加 `audio` | P2 |
| `tool.approve` | `{sessionId, callId, decision: allow\|deny\|always, scope?}` | `{ok}` | 与 `tool/approval_required` 配对 | P0-s |
| `usage.summary`（可作为查询） | `{from, to, groupBy}` | `{turns, tokens, costUsd, byModel, byCapability}` | 账本 | P1 |
| `session.enqueue` / `chat.steer` / `chat.interrupt` | — | — | 排队/插话三选；先做互斥再谈语义 | P2 |
| `credential.describe` | `{ref}` | `{configured, source, writable}` | 契约已有、实现缺 | P2 |

### 4.2 查询

| 修改 | 内容 | 优先级 |
|---|---|---|
| `sessions.list` | + `lastMessageAt/messageCount/preview/pinned/archivedAt/modelId/usage`，+ `includeArchived`、`cursor/limit` | P0 |
| `session.events` | + `limit/beforeSeq`；返回值带上 §3.2 的新字段（现在 `eventJson` 只搬了部分字段） | P0 |
| `session.search` | 升级为 `{query, filters, cursor}`，命中返回 `seq/role/at` | P0 |
| `session.meta`（新） | 单个会话完整 sidecar（含 draft/feedback 汇总/usage） | P1 |
| `session.turns`（新） | turn 索引（长会话寻址） | P2 |
| `usage.summary`（新） | 成本/token 账本聚合 | P1 |
| `capabilities.list`（新） | 工具清单 + `costNote` + 是否需审批 + 是否启用 | P1 |
| `persona.cards` / `sync.preview` | 契约有、实现缺；**本期建议明确"延期"并改契约**，否则审计永远对不上 | P2 |

### 4.3 事件

- **每个事件必须带 `seq`（每会话单调）**。建议口径：落盘事件用持久 `seq`；未落盘的 delta 用 `(seq = 该轮下一个持久事件序 + chunk 计数)` 或独立 `streamSeq`，并规定"重连后以 `session.events(fromSeq)` 的持久事件为准，delta 只用于临时渲染"。
- 新增：`turn.started`、`tool.approvalRequired`、`assistant.reasoning`、`usage.updated`、`delivery.new`（§3.3）。
- 修改：`failed` 加 `{sessionId, turnId, stage, code, retriable}`；`session.updated` 加 `{title?, pinned?, archived?, deleted?}`；`assistant.done` 加 `{usage, costUsd, finishReason, durationMs}`；`tool.finished` 加 `{callId, durationMs, costCny}`。
- 可靠性：`tryEmit` 改 `emit`/带 `BufferOverflow.SUSPEND` 的通道，或改成"每客户端从 `fromSeq` 拉取 + 环形缓冲"；否则契约的"至少一次"不成立。

### 4.4 语义补全（不新增 API 但必须改）

1. `requestId`/`clientId` 幂等：in-process 用一个 `commandId` 参数或包一层 `Envelope`；http 侧从 body 读 `requestId` 并缓存 `(requestId → Result)`。
2. 会话级互斥：`ConcurrentHashMap<String, Mutex>`；`chat.send/regenerate/editResend/stop` 都在锁内推进（`stop` 用 `turnId` 校验）。
3. 错误码枚举：建 `bridge/ErrorCodes.kt`，把 HTTP 状态映射成稳定 code（`KEY_INVALID/INSUFFICIENT_BALANCE/RATE_LIMIT/PROVIDER_BUSY/NETWORK/CONTEXT_OVERFLOW`），GUI 只认 code。
4. `bridgeVersion` 校验：`HttpBridgeClient` 读 `/health` 后拒绝未知 major。

---

## 5. LLM / 上下文 / 工具

### 5.1 两方言差异（按当前实现）

| 维度 | OpenAI 方言（`OpenAiCompatibleClient`） | Anthropic 方言（`AnthropicCompatibleClient`） | 影响 |
|---|---|---|---|
| 思考开关 | **完全不发**（`buildRequestBody` 无 `thinking`/`reasoning_effort`） | 发 `{"thinking":{"type":"enabled\|disabled"}}`（`:191`） | OpenAI 方言下 DeepSeek 服务端**默认开思考**（[文档已确认] 默认 enabled、effort=high）→ 成本/延迟不可控 |
| 思考字段口径 | 官方 OpenAI 格式是 `thinking.type` + `reasoning_effort` | 官方 **Anthropic 格式是 `reasoning.effort`（none 关闭）+ `output_config.effort`** [文档已确认，2026-11-26 抓取] | 现在发的是 `thinking.type`，**是否生效必须实测**；若不生效 = 思考一直开着且无人知道 |
| `reasoning_content` | 不解析 | 不解析 | 带 tools 时必须原样回传否则 400（[文档已确认]）；当前工具循环第二轮请求必然缺它 |
| 流式 usage | 读每帧 `usage`；**未发 `stream_options.include_usage`** | `message_start` + `message_delta` 合并 | OpenAI 兼容服务可能不给流式 usage → 成本全 0（[推断]，需实测） |
| 缓存 token | `prompt_tokens_details.cached_tokens` | `cache_read_input_tokens` | 成本口径两套，已各自处理 |
| `finish_reason`/`stop_reason` | **忽略** | **忽略** | 无法区分"长度截断/工具调用/正常结束"；`length` 截断在 UI 上表现为"回答突然没了" |
| 工具结果形态 | 每条一个 `role=tool` | 每条都包成**独立 user 消息**的一条 `tool_result` | 多个工具并行时会产生多条连续 user 消息（[推断] 真实 Anthropic 要求同一条 user 里多个 `tool_result`；DeepSeek 兼容层是否接受需实测） |
| 图片 | data URL；请求体 ≤48MiB [文档已确认] | base64 source；assistant 消息插图会 400 [文档已确认] | 历史回灌图片必须只放 user 消息 |
| `max_tokens` | 可选；桥固定传 `model.maxTokens`（deepseek-flash=384000） | 必填，同上 | 上限过大不直接烧钱，但与思考叠加时真实输出可能远超预期 |
| 方言覆盖 | `openai-responses` 直接抛异常（`ChatClients.kt:11-13`） | — | Responses 方言未实现 |
| 空闲超时 | `limits.streamIdleTimeoutMs` **未被使用**（只用了 `timeoutMs` 当 readTimeout） | 同 | 死连接/长思考的区分做不了 |

### 5.2 必须修（P0，与 UI 无关的正确性）

1. **捕获并持久化 `reasoning_content`**：`ChatChunk.Delta` 之外加 `ChatChunk.Reasoning`（或 delta 带 kind）；`ChatMessage` 加 `reasoning` 字段；`ChatRequest` 序列化时，当 `tools.isNotEmpty()` 把历史每轮 `reasoning` 原样拼回（[文档已确认] 漏传 400）。
2. **思考必须显式决策**：`ChatRequest.thinking` 默认 `null` → 改为按 provider `compat.thinkingFormat` 显式发送；默认 **关**（省钱），UI 给开关（"思考"）。
3. **Anthropic 方言的思考字段按官方口径重写**（`reasoning.effort` + `output_config.effort`），并保留 `thinking.type` 作为兼容分支（DeepSeek 兼容层到底认哪个，用一个最小请求实测后定）。
4. **解析 `finish_reason`/`stop_reason`**，写进 `assistant/message.finishReason`，映射：`length→CONTEXT_TRUNCATED`、`content_filter→SAFETY_BLOCKED`、`tool_calls→TOOL_CALLS`。
5. **usage 落盘**：`ChatChunk.Done.usage` 现在只取 `costUsd` 累加（`InProcessBridge.kt:260`），token 全丢；`AssistantMessage.Data.usage` 永远写 null。改为每个 assistant/message 写 usage + 每轮 usage 汇总。
6. **空 user content**：纯图消息持久化 `text=""`，历史回灌就是空字符串 content → 建议回灌时若 `text.isBlank() && attachments` 用占位符 `（见图片）`，并核对 provider 是否接受空 content。
7. **OpenAI 方言 `stream_options: {include_usage: true}`**（若目标是官方 OpenAI 或严格兼容服务）；DeepSeek 是否已默认返回需实测。

### 5.3 上下文重建（P0，R12/R13 的前置）

`buildMessages`（`InProcessBridge.kt:359-365`）现在只做 `user.text / assistant.text` 两条映射。必须改成"从事件重建"：
- `turn/start` 起段，`user/message` → `ChatMessage(role=user, text, images=按需从 AttachmentStore 读)`
- `assistant/message`（带 `toolCalls`）→ `ChatMessage(role=assistant, toolCalls=...)`
- `tool/call` + `tool/result` → `ChatMessage(role=tool, toolCallId, content)`（OpenAI）或配对成 Anthropic 的 user+tool_result
- `system/inject` → system 消息（注意不要在中间插 system：会破坏前缀缓存，[文档已确认] 缓存按前缀匹配）
- **历史图片**：默认不重放（token 成本），但"上一轮刚发的图再问一句"必须能重放 → 策略：最近 N 轮图片重放 + 更早的降级为 OCR 文本/占位（见 §8）

### 5.4 上下文预算 / 压缩（P1）

现状完全没有：没有 token 估算、没有 `contextWindow` 检查、没有截断策略。长会话的失败形态是"发不出去且报错难懂"。建议：
- 预检：`估算 token（中文 ≈ 字符数，英文 ≈ 字符/4）+ tools schema + maxTokens > contextWindow` → 返回 `CONTEXT_OVERFLOW{retriable:false}`，GUI 给"新会话/压缩"动作；
- 截断：保留 system/人格 + 最近 N 轮 + 丢弃最老图片；工具结果按 `truncated` 标记；
- 压缩（P2）：摘要 + `system/inject` 记录，保证可回放（不要静默改历史）。

### 5.5 停止 / 继续 / 多候选

- 停止：已可用，但持久化缺 `status`（§3.2）。
- 继续：停止后部分文本已落盘；`chat.continue` 可用"同一 turn 追加"或 DeepSeek Chat Prefix Completion（Beta，需 `base_url=.../beta`）[文档已确认该能力存在]（P2）。
- 多候选 `n>1`：`ChatRequest` 无 `n`，UI 的分支切换数据源不存在（P2）。

### 5.6 DeepSeek 服务端工具 vs 客户端工具

| | 服务端工具 `deepseek_web_search` / `deepseek_read_url` | 客户端工具（现状） |
|---|---|---|
| 实现 | 请求里声明 tool，模型自己调，结果按 token 计费；**仅 deepseek-flash**；`tool_choice` 不能指向它；带 citations 回传 | `web_search`/`read_url` 走独立 `/v1/tools/*`，按次计费（$0.75/千次），**多一轮模型往返** |
| 现状 | **完全未接**（代码里无 `deepseek_web_search`） | 已接（`CapabilityTools.kt`），M3 真机验证过 |
| 建议 | provider schema 加 `serverTools` 能力位（`provider-profile.schema.json` 现在没有），由每个 provider 声明；与客户端工具**互斥**（混用/选错模型会静默忽略）[文档已确认] | 保持默认；服务端工具作为 flash 的"更省一轮"可选（P2，产品决定成本模型） |

### 5.7 自动标题（低成本高收益）

DeepSeek 有 `title:{"enabled":true}` 扩展与 conversation_title 指南 [文档已确认]；也可本地截断首条 user 消息。建议 P1：`turn/end` 后异步写 sidecar.title，推 `session.updated`。注意：声明 `title` 扩展时 `temperature/top_p/seed/n` 被服务端忽略 [文档已确认]。

### 5.8 成本

- 峰谷价未实现（`mvp-close.md` §4.2 已知）：`UsageCost.compute` 单一价，`Pricing` 无 peakWindows；建议在 `Pricing` 加窗口后只改 `UsageCost`。
- 能力计费只进工具返回 JSON 的 `estimatedCostCny`（模型能看到），**不进 usage/账本**（`CapabilityTools.kt`）→ footer"成本"永远漏算搜索/OCR/ASR。
- `TurnEnd.costUsd` 在 provider 没配 pricing 时写 `0.0`（`totalCost` 初值 0.0）→ UI 显示"免费"，应改为 `null`（"未知"）。

---

## 6. 存储 / 同步 / 性能 / 安全

### 6.1 存储缺口

| 项 | 现状 | 缺口 | 建议 | 优先级 |
|---|---|---|---|---|
| 缩略图 | 无 | 图片只能原图解码 | save 时生成（Android `BitmapFactory.inSampleSize`；桌面 ImageIO），落 `attachments/<sid>/thumb/<id>.jpg`；`AttachmentRef` 加 `width/height/thumb` | P0 |
| 搜索索引 | 无，全量扫 | O(会话×事件×文本)，输入框每次击键都触发查询 | sidecar 级 `index.v1.json`（sessionId → 首句/标题/token 倒排）或 SQLite FTS（P2）；先用"内存索引 + 启动构建" | P1 |
| 置顶/归档/软删 | `deletedAt` + `pinned` 字段 | 无归档、无命令、无排序 | sidecar 扩展（§3.4） | P0 |
| 消息反馈 | 无 | — | `feedback.v1.json` | P1 |
| usage 账本 | 只有 `turn/end.costUsd` | token 不落盘、能力成本不落盘、无聚合 | `usage.v1.jsonl` + `usage.summary` | P1 |
| 导出 | 只有 provider | 会话/消息导出全缺 | `session.export`（md/jsonl） | P1 |
| 附件 GC | 无 | 软删会话后附件永久占用；无去重/无 hash 校验 | `deleteSession`；`gc` 命令（P2） | P1 |
| sidecar 版本化 | `SessionSidecar` 无 `revision/updatedAt` | 多客户端写会互相覆盖；`sync.preview` 无法检测冲突 | 照 `SettingsStore` 的信封给 sidecar 加 `revision` | P1 |
| 会话列表性能 | 每个会话 `readAllLines` 取首行 + 解析 sidecar | 会话多时列表变慢（无缓存） | 内存索引 + 启动一次加载 | P1 |
| 附件容量 | 图 32MB / 文件 8MB | 无单轮总大小、无数量上限 | 命令层校验 | P1 |

### 6.2 性能 / 内存

1. **事件全量进内存**：`readFrom` 一次 `readAllLines` + 全量反序列化（`SessionStore.kt:80-93`）；`session.events` 无 `limit`。长会话（1M 上下文可达数万事件）在手机上会 OOM/卡顿。→ 分页 + 只解析需要的行（`fromSeq` 目前也是先全解析再过滤）。
2. **搜索 O(n²)**：`search` 对每个会话 `list()` 后 `read(meta.id)` 全量；`list()` 内部又 `readAllLines` 两次。→ 索引 + 增量。
3. **每轮重建全量上下文**：`buildMessages` 无缓存，长会话每轮 O(n) 字符串拼接（可接受，但与 5.3 重写一起做缓存）。
4. **`tryEmit` 丢事件**：`MutableSharedFlow(extraBufferCapacity=256)`、`replay=0`；慢订阅者/无订阅者时 delta 与 tool 事件静默丢失。
5. **附件 base64**：32MB 图 → 43MB base64 字符串 + 解码后 32MB 字节 + 模型请求 JSON（再叠一份）→ Android 上峰值可到 100MB+；且 `AttachmentSave` 在 in-process 也要过一次 base64（无必要）。→ 分块/流式/直接路径。
6. **流式渲染**：每个 delta 一次 Flow 事件 + Compose 重组（GUI 侧要节流，`mobile-chat-ux.md` §4.2 已提）；后端侧应允许合并（每 N ms 或每 K 字符发一次 delta，最终 done 给全文）。

### 6.3 安全 / 隐私（含 P0-s）

| 项 | 现状 | 风险 | 建议 | 优先级 |
|---|---|---|---|---|
| 工具读任意本地路径 | `ocr_image(path)` / `transcribe_audio(path)` 直接 `Files.readAllBytes`（`CapabilityTools.kt:122-158`），path 来自**模型** | 模型可让内核读取 `/sdcard/DCIM`、其它 app 文件、`~/.myco/credentials.json` 并上传云端 | 路径白名单（仅 `attachments/<sid>/` + 用户显式选择的 URI token）；工具调用前审批；不存在的路径不暴露存在性 | **P0-s** |
| 凭据明文 | `credentials.json` 明文，POSIX 0600 尽力（Windows 无 POSIX，只 warn）；Android `filesDir` 依赖设备 FBE | 设备被 root/备份导出即泄露 | Android 用 Keystore 包裹；桌面 0600 + 明确 warn；导入导出永不包含值（已做到） | P1 |
| `runtime.json` 权限 | `Files.writeString` 直接写，**未设 0600**（`app-desktop/.../Main.kt:331-334`） | token 明文可被同机其它用户/进程读取，http bridge 即可被调用 | 写入后 `setPosixFilePermissions("rw-------")`；或改 Unix domain socket | P1 |
| 附件外发边界 | 默认"只有发送时才进模型"（`AttachmentStore` 注释），已实现 | 但 OCR/ASR 工具会把**整份文件**发给 DeepSeek（用户并未"发送"它）；`read_url` 会把内网/任意 URL 交给云端抓取 | 工具外发前显式确认；`read_url` 拒绝内网/localhost/文件协议；审计日志 | P1 |
| 提示注入 | `read_url`/`web_search` 结果原样进工具结果 | 网页内容可引导模型调用 `ocr_image` 读本地文件外发（与上一条组合成完整攻击链） | 工具结果加"不可信来源"标记；高风险工具（本地文件/网络）默认需审批 | P1 |
| 审批/权限指示 | 无 | UI 想要的"完全权限中/自动允许中"没有数据源 | `capabilities.list` 返回策略 + `tool.approval_required` | P0-s（与 R21 同一项） |
| http bridge | 回环 + Bearer token（单因子），无 CORS、无 TLS、无速率限制 | 隧道/远程场景不安全；浏览器直连被 CORS 挡 | 明确"仅回环"；远程走 HTTPS 隧道 + 更短 token TTL；补 CORS/OPTIONS | P2 |
| `safety_check` | 未发送 | DeepSeek 文档称公共应用必须开（[文档已确认] `safety_check:{"level":"high"}`） | 若要分发 APK，provider 加开关 | P2 |
| 日志 | `System.err` warn，不打印 key（已做） | 低 | 保持 | — |

---

## 7. 路线图

### 7.1 P0（不做则 UI 不能落地 / 出正确性事故）

| 顺序 | 项 | 改动点 | 规模 | 备注 |
|---|---|---|---|---|
| 1 | 事件流语义（P0-1/R26/R27） | `Bridge.kt` 加 `seq`；`InProcessBridge` 每会话环形缓冲 + `fromSeq` 重放；`requestId` 幂等；`Failed{+sessionId,turnId}`；`HttpBridgeServer` 支持 `Last-Event-ID`；`tryEmit→emit` | 中 | 先做，其它 P0 依赖它做回归验证 |
| 2 | 事件模型补字段（P0-2/R10/R20） | `SessionEvent.kt`（§3.2）；`SessionStore` 读侧宽松 Json；`eventJson` 全量输出；schema v1.1 + fixtures；`turn/end.status` | 中 | 加字段前必须先做"读侧宽松"，否则破坏兼容 |
| 3 | 会话列表/搜索元数据（P0-5/R1/R2/R4） | `SessionMeta` 扩展、sidecar `archivedAt`、`list()` 合并统计、`session.update`、`session.search` filters/seq/分页、turn 结束发 `session.updated` | 中 | 历史面板的全部数据面 |
| 4 | regenerate / editResend + 会话互斥（P0-4/R12/R13/R28） | 新命令 + 事件重建上下文 + `Mutex per session` + turn 编号改为 sidecar 自增（不再 `count()`） | 中 | 消息操作条的核心；锁必须先进 |
| 5 | LLM 正确性（P0-6/R19/R20） | `ChatChunk.Reasoning`、`ChatMessage.reasoning`、`ChatRequest` thinking 显式、Anthropic 思考字段按官方口径、`finish_reason`、usage 落盘、空 user content 修复、历史上下文重建（§5.3） | 中 | 与 4 有重叠，建议同一 PR 收口 |
| 6 | 工具安全门（P0-7/R21） | `ToolRegistry` 加 `risk`/策略；路径白名单；`tool.approval_required`/`tool.approve`；默认未批准不执行 | 小~中 | 独立于 UI，先做 |
| 7 | 附件缩略图 + 预览（P0-8/R23） | `AttachmentStore` 缩略图生成、`AttachmentRef` 加尺寸/thumb、`attachment.thumbnail` 查询/端点 | 中 | 图片预览是 DeepSeek 对标项 |

### 7.2 P1（体验必需）

反馈（R15）· 分支（R14，**先要产品决定**）· 导出（R16）· 语音输入（R17）· 权限审批 UI 与"完全权限"指示（R21 的 UI 半）· 排队/插话/中断（R28）· 模型切换（R29）· 通知跳转 + `delivery.new`（R25）· 草稿（R24，需决策）· usage 汇总/账本（R20）· 上下文预算与截断（§5.4）· 错误码枚举（R31）· 搜索索引（§6.1）· sidecar revision（§6.1）· 分块上传与上限（R22）。

### 7.3 P2（后续）

永久删除 + 附件 GC（R5）· 多选批量删除（R8）· 轮次面板/事件分页（R30）· TTS 朗读（R18）· OpenAI Responses 方言 · DeepSeek 服务端 web tools · prefix continuation 续跑 · 峰谷计价 · 多设备同步（本期明确不做）· 摘要压缩 · `n>1` 多候选。

### 7.4 与 `docs/design/60-mvp-closed-loop-plan.md` 的冲突

1. **"暂不做多设备同步" vs 本报告 P0-1**：需要澄清边界——`seq/requestId/重放`不是同步，而是**同一进程内多客户端/断线恢复**的契约要求。不做它，平板第二窗口和进程重启恢复都做不了。建议在计划里加一行"本地多客户端订阅 = MVP 范围内，多设备同步 = 不做"。
2. **计划 M4 写"事件日志加索引字段"，实际未做**（`SessionStore.search` 仍是全量扫）。`mvp-close.md` 把 M4 标为完成，口径应改为"功能完成、索引未做"（§7.5）。
3. **`mvp-close.md` §4.6 把"跨回合工具上下文回放"列为"不阻塞 MVP"**，但它同时是 regenerate/editResend/多轮工具正确性的前置。**先做 UI 会先看到"模型忘了刚才查过什么"**——建议把它从"已知缺口"移到 P0（本报告 P0-3）。
4. **`mobile-chat-ux.md` §5.2 把"编辑用户消息重发/分支切换"延后到 M2+/M6**，而本次目标清单包含"编辑消息/分支"：编辑重发建议本期（P0），分支建议 P1 且先定数据模型；两者都不应该做成"前端假分支"（只在 UI 上切，后端仍是线性历史）。
5. 计划里"chat 单焦点 + 不要空抽屉"隐含"历史面板必须可用"——这正是 P0-5 的列表+搜索，不能只做 UI 壳。

### 7.5 与 `mvp-close.md` 的口径差异（诚实）

- "M1–M6 全部完成"在**功能入口**层面成立；但 `mvp-close.md` §4.4/§4.6 自己列的两项（Markdown/重新生成/语音/操作条；跨回合工具回放）恰好是本次 UI 的核心面，本报告把它们拆成 P0/P1 而不是"不阻塞"。
- 测试 70 绿覆盖的是"单轮语义 + 两传输一致"，**没有一条测试覆盖**：`seq` 重放、`requestId` 幂等、并发两次 send、停止后重开的状态区分、`fromSeq` 事件流、附件缩略图。建议 P0 每一项都补一条契约测试（`fixtures.json` 里加 case）。

---

## 8. 存疑 / 需要产品决定

1. **分支模型**：`append-only` 日志 vs 覆盖式"重新生成"。分支若要支持 `‹ 1/3 ›`，必须引入 `branchId` 并升 `formatVersion=2`；覆盖式则只需"截断后追加 + 旧回答标记 superseded"（更简单，但丢失旧回答）。**建议：先覆盖式（P0），分支留结构位（P1）**。[需要产品决定]
2. **思考内容是否落盘/展示**：`reasoning_content` 体积大（可能数万 token/轮）、含隐私；但 DSH Go 的过程折叠需要它。**建议：默认只在内存流式展示、不落盘；用户显式"保留思考"才写事件**。[需要产品决定]
3. **历史图片是否重放**：重放 = token 成本（每图 ≤1024 token [文档已确认]）+ 请求体 ≤48MiB；不重放 = "刚才那张图"问不下去。**建议：最近 2 轮重放，更早替换为 OCR/占位文本**。[需要产品决定]
4. **语音输入计费与路径**：DeepSeek ASR 单次 ≤120 秒、按秒计费（限时 0.39 元/小时）；长语音要切片，切片边界影响识别质量。是否走云端 ASR（简单、花钱、外发音频）还是本地 whisper（重、隐私好）？[需要产品决定]
5. **草稿归属**：契约 §6 写"GUI 持有草稿"；但 Android 进程被杀、平板第二窗口都要求一致性。**建议改契约：草稿进 sidecar**（或 GUI 持久化 + 启动时回填）。[需要产品决定]
6. **权限策略默认值**：全自动（体验好，风险高）/ 仅本地文件与网络工具需批准（推荐）/ 全部批准。DSH Go 用"完全权限"常驻提示条，myco 需要同样的可见性。[需要产品决定]
7. **是否接 DeepSeek 服务端 web tools**：省一轮模型往返 + citations 原生支持，但仅 flash、与客户端工具互斥、按 token 计费（不可预估）。当前客户端 Search API 按次计费（可预估）。[需要产品决定]
8. **多设备同步是否进本期**：`settings.revision` 已为同步铺路，`sync.preview` 契约也写了但没实现。若本期不做，**建议把 `sync.preview`/`persona.cards` 从契约 v1 移到 v2**，否则代码与契约永远对不上。
9. **Anthropic 方言思考字段到底认哪个**：官方文档标 Anthropic 格式为 `reasoning.effort`/`output_config.effort`，而当前代码发 `thinking.type`；M2/M3 的真实工具调用是通的，说明要么服务端兼容两种、要么思考根本没开。**上线前必须用一个最小请求实测**（`thinking` on + tools + 两轮），否则 P0-6 的修法可能建立在错误前提上。[需实测]

---

## 附：本轮引用的外部事实

- DeepSeek Thinking Mode（2026-11-26 抓取）：思考默认开启/effort 默认 high；`reasoning_content` 与 content 同级；**带 `tools` 的请求必须回传历史每轮 `reasoning_content`，否则 400**；Anthropic 格式用 `reasoning.effort`（`none` 关闭）+ `output_config.effort` — https://api-docs.deepseek.com/guides/thinking_mode/
- DeepSeek 工具调用 / Web Tools（服务端 `deepseek_web_search`/`deepseek_read_url` 仅 flash、与 Anthropic 原生 web_search 互斥）— https://api-docs.deepseek.com/guides/tool_calls/ 、 https://api-docs.deepseek.com/guides/web_tools/
- DeepSeek Chat Prefix Completion（续跑可用）— https://api-docs.deepseek.com/guides/chat_prefix_completion/
- DeepSeek 会话标题扩展 `title:{"enabled":true}` — https://api-docs.deepseek.com/guides/conversation_title/
- DeepSeek Search/Fetch、OCR、ASR、TTS 端点与限额 — 见 `docs/recon/deepseek-api.md`（2026-11-25）§3/§4
- DeepSeek 视觉限制（仅 flash；assistant 消息插图 400；单图 ≤1024 token；请求体 ≤48MiB）— 同上 §4.4
- OpenAI/Anthropic 通用协议行为（`finish_reason`、流式 usage 需 `stream_options`、Anthropic 多 `tool_result` 形态）本轮未再抓官方页，标注为 [推断]，落地前需实测。

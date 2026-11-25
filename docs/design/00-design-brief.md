# myco MVP 设计简报（第一轮 · 只讨论，不开工）

> 状态：**讨论稿**。本文不含代码承诺，只把「从六份考古里抽出的硬约束 + 一个可落地的框架骨架 + 分步 MVP」摆清楚，供拍板。
> 输入：[六份消化报告](../recon/README.md)。所有关键结论都有出处；本文只写「为什么」，可漂移的数字放配置/测试里。
> 最后核对：2026-11-25（DeepSeek API 事实以此日抓取为准）。
> 第二轮（2026-11-25 晚）：D1–D8 已拍板，**决策与方向的唯一权威是 [决策记录](20-decision-record.md)**。
> 本简报已按「两个独立 app、移动端原生、MVP=I2（可平替官方 chat）」更新；未落地的方向只作留底，不算承诺。

---

## 0. 结论先行

1. **myco 要建的是「本地 agent 内核 + 插件接缝 + 客户端」，不是一个聊天框**。聊天框是第一个客户端，不是产品边界。
2. **MVP = 能日常平替官方 chat 的最小 myco chat**（D4 + A2）：provider 管理、**多会话（会话列表 + 聊天框 + 设置页）**、流式 loop + 少量工具、取消/重试。记忆、主动、work 留到 I3+。
   "单焦点 / 连续角色 / 会主动"是人格与主动性层的属性，不是"UI 上只能有一个会话"。
   chat 与 work 是**两个独立 app**，共享一份存储、各按自己的读取策略（D3）。
3. **一切皆插件 = 目录 + `manifest.json` + `apply(ctx)` + 注销器**。核心接缝先只开 9 个（llm、embeddings、capabilities、settings+credentials、sessions、tools、prompt、delivery、memory），每个都必须有第二个使用者才允许留下。
4. **会话维度从第一天进数据模型**。前两代最大的事故都是「单实例假设 + 事后用锁去补」（第一轮 5 分钟测试就是两个会话共用 `time.json` 作废）。
5. **缓存纪律是架构级约束，不是优化**：静态前缀字节级稳定，动态内容只准以追加消息进入；一次新回合 `hit=0` 就要付 7k–24k tok 的 prefill。
6. **失败必须吵**：前代的致命形态是「记忆变薄时系统不报错，只表现为她想不起来」。MVP 里每个降级都要有 status/reason 出口。
7. **预算进 CI**：工具面 ≤24k 字符 / ≤30 个；静态提示词 ≤6k 字符；每轮动态注入 ≤500 字符。前代是从 68 工具/46.7k 字符事后裁剪的，代价是三轮文档和一次没上线的方案。
8. **D1–D8 已拍板 + A1–A5 补充修正**（2026-11-25）：桌面 daemon + Web 先跑、自研薄内核、两个 app 共享存储、MVP 到 I2、三种协议方言、记忆七表现定、唯一出网点先行、感知只留形状；A1–A5 修正人格卡同步、多会话、proot 侧载、离线独立、GUI 模块化。
9. **离线独立是硬要求**（A4）：**单手机能完整运行 chat 与 work**；没有远程电脑 ≈ 零影响；远程设备只是可选增强；桌面 daemon 是开发/预览载体，不得成为手机端的运行前提。
10. **GUI 是模块**（A5）：核心与 GUI 之间只有一个 **Bridge 接口**（命令/事件/查询）；至少 in-process 与 HTTP/SSE 两种传输；**无 GUI 时核心照跑**（要有 headless 测试）；GUI 可整体替换（重写一套也行，不只是换主题）。
   细节与理由见 [决策记录](20-decision-record.md)，未定项也在那里列了 4 条（D9 是其一，正在做完整对比）。

---

## 1. 从参考里抽出的 12 条硬约束

每条都是「别人已经付过账」的，不是设计偏好。

| # | 约束 | 证据 |
|---|---|---|
| C1 | **单会话是架构决定，不是锁漏**。单份 DB/JSON + 零文件锁 + claim 同步抛，三代同栽 | `icl-architecture` §3.2；`docs/HISTORY.md:85-89` |
| C2 | **接线在仓库外 = 测试全绿但什么都不会发生**。MVP 的装配必须能被一条仓库内命令跑出来 | `icl-architecture` §3.1 B2 |
| C3 | **文档数字靠手写必然漂移**（22 处不符 + 今天又查出 8 处）。数字由脚本产出，文档只写决策 | `icl-architecture` §1.3 |
| C4 | **静默失败对记忆系统最致命**：装配期空 catch、第 5 次重试直接丢、解压失败返回空串 → `people 0` | `icl-architecture` §2.4；`icl-memory` §4 |
| C5 | **工具 schema 是每请求固定前缀**（68 工具/46,766 字符），必须当预算管 | `docs/PRODUCT-SURFACE.md:5` |
| C6 | **上下文自耗**：E2 实测 70% 投递字符是重复 facts 快照；一次新回合 hit=0 | `icl-memory` §1.5；`docs/COMPLEXITY-AUDIT.md:5` |
| C7 | **宿主脚手架会污染产品首屏**：静态 system 56% 是 coding-agent 说明，首行 `You are an AI agent` | `docs/REDTEAM-REVIEW.md:12` |
| C8 | **「没找到」≠「没发生」**：`answer` 空串把三种世界状态折叠成一种 | `trace-memory` §2.3 |
| C9 | **判断不准写进采集层**：22.3% 串台就是「写入时定性」的产物；缺失必须 NULL，不得写 0 | `trace-memory` §3.1 |
| C10 | **纯定时器主动性 = 空转**：25 次心跳 17 次零工具；条件唤醒才砍得掉 | `icl-persona-proactive` §3.3 |
| C11 | **空 turn 也照付前缀钱**：主动性的第一原则是「值不值得起一个 turn」 | `docs/CACHE-MODEL.md:17` |
| C12 | **模型看起来笨，常是环境逼的**：57% 墙钟在工具调用、17% 在等她自己的节拍 | `docs/COMPLEXITY-AUDIT.md:5` |

> **证据修正（2026-11-25，用户口述）**：ICL 的**实际运行时间远长于文档记录**——开发停止后它仍运行过一段时间，
> 只是功能未充分验证。所以"端到端只跑过两次 5 分钟"是**文档口径**，不是真实运行史；
> 引用时写成"运行过更久、验证薄"，别写成"没跑过"。这条修正同样适用于本简报里所有引用 `icl-architecture` 的时长结论。

---

## 2. 目标架构

### 2.1 分层

```
┌─────────────────────────────────────────────┐
│ 客户端（可替换）                              │
│  web / desktop shell / 手机 shell / CLI      │  ← 只通过 HTTP+SSE/WS 说话
└───────────────▲─────────────────────────────┘
                │ 协议：message / stream / event / command
┌───────────────┴─────────────────────────────┐
│ myco daemon（Node/TS，本地单进程）            │
│  ┌───────────────────────────────────────┐  │
│  │ kernel：插件加载器 + 接缝注册表 + 预算   │  │  ← 约 600–800 行，自己写
│  └───────────────────────────────────────┘  │
│  接缝（每个都是 interface + provider + consumer 三角）：
│   llm         chat 家族：适配器（3 方言）+ 流式 + 重试 + usage/定价
│   embeddings  独立接缝（OpenAI 兼容 /embeddings），不塞进 llm
│   capabilities search/fetch/ocr/asr/tts 各一条独立接缝（DeepSeek 的 /v1/tools/* 只是其中一个 provider）
│   providers    档案与凭据：settings（namespace+path+revision）+ credentials（ref/record）
│   sessions     事件日志（append-only）+ 投影 + 多会话 + fork/导出
│   tools        作用域工具注册表 + 执行管线 + schema 预算
│   prompt       静态段（有序）+ 每轮动态注入（只追加）
│   delivery     主动消息出站队列 + 裁决纯函数 + 水位
│   memory       原文层 + 条目 + 召回 + honesty 状态（MVP 只做最小面）
└───────────────▲─────────────────────────────┘
                │
        ~/.myco/（profiles / sessions / memory.db / logs）
```

**为什么是 daemon + 客户端**：最终形态是手机上常驻的 chat 伴侣 + work agent，两者**共享一份存储**（同一份
`memory.db` / sessions / provider 凭据），但**各按自己的读取策略**（D3）。内核做成可被任意客户端连接的本地面板，
Android 壳（WebView / 原生）与桌面壳都能接同一份内核；work 的 proot Linux 只是又一种工具后端，不需要动内核。

**两个 app 的边界（先说清，避免以后混）**：

| | myco chat | myco work |
|---|---|---|
| 会话 | 单一、连续、不可切换（只压缩/归档） | 多会话，可 fork、可并行 |
| 人格 | 唯一所有者，可版本回滚 | 只读同步（可选） |
| 主动消息 | 唯一发出者 | 不主动（只发任务完成通知） |
| 读取策略 | 以「连续叙事」为主线召回 | 以「任务/工作区」为过滤，共享背景记忆只读 |
| 暴露面 | 不出现 Linux 管理、多会话列表 | 有工具/工作区/设备管理 |

**移动端原生**（从第一天，不许有桌面-only 假设）：单列流、触摸优先、安全区与键盘弹出布局、
通知即入口、断线可恢复；常驻进程必须有静默档；感知默认关；每项能力标注耗电成本。

**平台硬约束（已调研，I0 起写进契约，不等到做手机壳）**：常驻只能前台服务（FGS `specialUse`）+ 常驻通知 + 用户白名单；
「她主动找人」只能走通知（后台 socket 长连不能当唤醒）；截图/感知按「事件 → 去抖 → 单帧」，`takeScreenshot` 有 ~333ms 平台限速；
MediaProjection 只做用户显式开启的短会话；**Play 渠道禁止运行时下载可执行文件**（work 的 proot 要么 APK 内嵌 native libs，要么侧载分发）；
远程连接默认必须 HTTPS（隧道要绑 `*.nyat.app` 开自动 HTTPS，否则是明文）。
详见 [mobile-agent-landscape.md](../recon/mobile-agent-landscape.md)。

### 2.2 模块划分（monorepo）

```
packages/
  contracts/     纯类型 + zod/JSON Schema：ProviderProfile、SessionEventMap、Remote 描述符
  kernel/        插件加载、manifest 校验、接缝注册表、effect/注销器、事件分发、预算与自检
  settings/      分层配置、revision 乐观锁、按路径 mutate、schema 投影（设置页不手写持久化）
  credentials/   ref（env 形）+ record（owner/id）+ describe 永不回值；后端可换（文件→DPAPI）
  llm/           chat 家族抽象：流式 chunk、prepareCall、能力断言、provider 注册表 + 路由
  llm-deepseek/  DeepSeek Messages 方言（官方 anthropic 兼容端点）
  llm-openai/    openai-completions / responses（含 compat 子集）
  embeddings/    独立接缝（OpenAI 兼容 /embeddings）
  capabilities/  search/fetch/ocr/asr/tts 的接缝与 provider 表（DeepSeek 工具端点是其一）
  tools/         注册表 + pre/execute/post 管线 + schema 预算 + 审批
  agent/         loop：turn/step 状态机、inbox、prompt 组装、压缩触发
  session-store/ JSONL 后端 + header 版本（读侧拒绝未知版本）
  prompt/        段注册 + 组装 + 缓存纪律断言
  delivery/      出站队列（落盘）+ decide() + 水位
  memory/        ns/item/vec/source_row/evidence/job_run/cursor + recall
  api/           Remote 注册表 + RPC/SSE 载体（客户端与 UI 解耦点）
  client-core/   与 UI 无关的客户端状态机：模型目录、会话列表、发送队列、事件跟随
  preset/        app-profile：每个 app 的白名单 + 读取策略 + 默认模型（chat/work 各一份）
apps/
  daemon/        HTTP+SSE 服务、启动装配、健康自检（两 app 共用）
  chat/          chat 客户端（移动优先；**MVP 交付物**）
  work/          work 客户端（I5+，暂不做）
  web/           开发/预览用浏览器客户端（dev harness，不承诺产品形态）
  cli/           myco doctor / myco provider / myco session
plugins/         一等公民插件（后续所有能力都长这里）
  persona/ time/ tools-basic/ memory-inject/ ...
config/
  providers.yaml 内置 provider 模板（DeepSeek / 本地 OpenAI 兼容）
```

> 依赖方向：`apps → plugins → packages/* → contracts`。插件只能 import 接缝类型，不能 import 别的插件的实现。
> **客户端边界在 `api/` + `client-core/`**：手机壳只依赖这两者，不依赖 React/DOM（来自 DSH 的验证）。

### 2.3 GUI 与传输边界（A5：无 GUI 后端照跑，多 GUI 可整体替换）

```
        ┌──────────── GUI 模块（可替换/可重写）────────────┐
        │  ChatGUI(mobile)   WorkGUI   WebGUI   TUI        │
        └───────────────────────┬─────────────────────────┘
                                │ Bridge 接口（命令 / 事件 / 查询）
        ┌───────────────────────┴─────────────────────────┐
        │ Bridge 实现（至少两种）                          │
        │  · in-process：手机/桌面原生 UI 直连核心，不开服务器 │
        │  · HTTP+SSE：浏览器 / 远程客户端                  │
        └───────────────────────┬─────────────────────────┘
                                │
                           core（headless）
```

- **Bridge 是唯一 GUI 契约**：`send(command)`、`subscribe(event)`、`query(state)`；GUI 不直接碰存储、不 import 核心内部。
- **headless 是验收项**：不加载任何 GUI 模块时，核心能跑完一轮对话（含工具与流式），并由自动化测试断言。
- **手机端不强制本地 HTTP 服务**：in-process 是默认；HTTP 只在浏览器/远程/调试时启用（省电、省端口、少一个攻击面）。
- GUI 之间不共享组件是允许的；共享的只有 Bridge 契约与 client-core 状态机。

### 2.4 离线优先（A4，硬约束）

- 手机端必须独立完成：provider 直连、会话存储、agent loop、记忆、通知、主动性。
- 任何功能不得以"先连上某台电脑"为前提；同步/多设备是可选增强，缺省时功能完整只是不漫游。
- 事件日志/存储格式要同步友好（ULID、单调 seq、无绝对路径、逻辑时钟），但同步本身不是 MVP。

---

## 3. 插件契约（MVP 版）

### 3.1 manifest

```jsonc
{
  "id": "persona",
  "version": "0.1.0",
  "entry": "./src/index.mjs",
  "tools": ["persona_get"],          // 唯一真相：工具面 = manifest ∩ 已启用 ∩ 预设允许
  "slots": [{ "id": "chat", "kind": "llm.chat" }],
  "requires": { "plugins": [], "capabilities": ["llm.chat"] },
  "budget": { "toolsChars": 1200 }    // 声明式预算，装配时汇总断言
}
```

### 3.2 生命周期（抄 dsh-icl，成本最低）

```ts
export const name = 'persona'
export function apply(ctx: PluginContext) {
  const off = [
    ctx.tools.register({ ... }),          // 每个注册都返回 off()
    ctx.prompt.section({ name, tier: 'static', order: 20, text }),
    ctx.slots.declare({ id: 'chat', kind: 'llm.chat' }),
  ]
  return off                            // 卸载时逆序调用；没有第二套状态要同步
}
```

- `apply` 抛错 → 逆序注销 + 标记 failed + **启动自检输出 error**（不许空 catch）。
- 依赖不齐 → 整段消失且**报告消失**（Feature Contract 的最小版：不发布半截）。
- `surface()` 只输出三重交集，保证「模型为什么看不到这个工具」有唯一答案。

### 3.3 预算（进 CI）

| 项 | MVP 上限 | 断言方式 |
|---|---|---|
| 工具数 | ≤ 30 | 装配后 `tools.list().length` |
| 工具 schema 字符 | ≤ 24,000 | `Σ JSON.stringify({name,description,parameters})` |
| 单工具 description | ≤ 900 字符 | 注册时校验 |
| 静态提示词 | ≤ 6,000 字符 | 组装测试 |
| 每轮动态注入 | ≤ 500 字符 | `onTurn` 钩子返回值 |

---

## 4. Provider 与能力槽

### 4.1 两条轴：chat 家族用 profile + 角色路由；其它能力各自独立接缝

考古结论（DSH）：**能力路由不是一个 provider 大表，而是每个能力一条接缝**——chat 走 `ctx.llm`，
search/fetch 走 `ctx.web`，STT 走 `ctx.speechToText`；DSH 里 embedding/OCR 干脆不存在。
好处是「换一个 provider 就整体换掉产品行为」，代价是「用便宜小模型做标题/压缩」要逐功能指模型。

myco 的折中：

- **chat 家族**：一份 `ProviderProfile` 管到底，内部用 `roles` 做质量/成本分层（`chat`/`fast`/`vision`）。
  工具调用、流式、思考模式、重试、usage/定价都在这一条接缝里。
- **其它能力**：`embeddings` / `search` / `fetch` / `ocr` / `asr` / `tts` 各自一条接缝 + 各自的 provider 条目，
  **共享同一份凭据存储**。不塞进 chat profile——因为它们的计费单位、限额、失败语义完全不同。
- **插件不写模型名**，只声明槽位（`{ id, kind: 'llm.chat' | 'memory.recall' | ... }`）；
  未绑定的槽在启动自检里打 **error** 日志（前代只抛 `SLOT_UNBOUND`，不吵，于是没人知道）。

### 4.2 ProviderProfile（chat 家族）

```ts
// packages/contracts/src/provider.ts —— 纯类型，zod 一一对应
export type ProviderKind = 'deepseek-messages' | 'openai-completions' | 'openai-responses'
export type Modality = 'text' | 'image'
export type ModelRole = 'chat' | 'fast' | 'vision'

export interface ModelProfile {
  id: string                 // 端点接受的 id；永久，会话日志按它引用
  name?: string
  contextWindow: number      // 必填：压缩阈值靠它
  maxTokens: number          // 必填：每请求默认输出上限
  input: Modality[]
  reasoning?: false | { efforts: Record<string, string | null>; default?: string }
}

export type AuthProfile =
  | { type: 'api-key'; ref: string }   // ref 形如 DEEPSEEK_API_KEY，只存引用
  | { type: 'none' }                   // 本地/内网无鉴权（DSH 这里需占位凭据，myco 修掉）

export interface CompatProfile {
  supportsDeveloperRole?: boolean      // 默认 false：很多网关拒绝 developer 角色
  maxTokensField?: 'max_tokens' | 'max_completion_tokens'
  supportsReasoningEffort?: boolean
  thinkingFormat?: 'none' | 'deepseek'
}

export interface ProviderProfile {
  id: string                 // 路由键：小写连字符；永久；= settings 键 = 会话日志里的 provider 名
  kind: ProviderKind
  displayName?: string
  baseUrl?: string           // 缺省用 kind 内置端点
  auth: AuthProfile
  models: ModelProfile[]     // 非空；不做 DSH 的「已安装目录 + override」双层语义
  roles?: Partial<Record<ModelRole, string>>   // 角色 → models[].id
  defaults?: { contextWindow?: number; maxTokens?: number; input?: Modality[] }
  compat?: CompatProfile
  headers?: Record<string, string>
  limits?: { timeoutMs?: number; streamIdleTimeoutMs?: number }
  retry?: { mode: 'normal' | 'always'; maxRetries?: number }
  enabled?: boolean
}
```

**存放与并发**：`$MYCO_HOME/config/providers.yaml`（人可读、可 diff），密钥与它物理分离。
每次写入带 `revision`（单调整数）+ 文件锁；客户端带 `expectedRevision`，冲突返回 `settings/conflict`。
**会话不持有 provider 定义**：会话只记 `{provider, model, reasoningEffort?}` 三元组（事件 `model/selection`），
所以删 provider 不会让历史失效。

### 4.3 两个内置模板（对应「自己的 LLM 占大头 + DeepSeek 一站式」）

```yaml
# config/providers.yaml
- id: deepseek
  kind: deepseek-messages
  displayName: DeepSeek 官方
  baseUrl: https://api.deepseek.com/anthropic      # Messages 协议，对普通 API key 开放
  auth: { type: api-key, ref: DEEPSEEK_API_KEY }
  models:
    - { id: deepseek-flash,  name: DeepSeek V4.1 Flash, contextWindow: 1000000, maxTokens: 384000,
        input: [text, image], reasoning: { efforts: { off: null, low: low, high: high, max: max }, default: off } }
    - { id: deepseek-v4-pro, name: DeepSeek V4 Pro,     contextWindow: 1000000, maxTokens: 393216, input: [text] }
  roles: { chat: deepseek-flash, fast: deepseek-flash, vision: deepseek-flash }
  limits: { streamIdleTimeoutMs: 300000 }

- id: local                     # 示例；地址/模型在设置页改，不进仓库配置
  kind: openai-completions
  displayName: 本地 OpenAI 兼容（无密钥）
  baseUrl: http://127.0.0.1:8000/v1
  auth: { type: none }
  models:
    - { id: <由 /models 发现>, contextWindow: 32768, maxTokens: 4096, input: [text] }
  roles: { chat: <同上>, fast: <同上> }
  compat: { supportsDeveloperRole: false, maxTokensField: max_tokens, supportsReasoningEffort: false }
```

> **铁律**：`thinking` 在 myco 里默认 **off**。DeepSeek 的思考模式默认开启、effort 默认 high，
> 不显式传就等于默默付 3–5 倍输出钱，还让 `temperature` 静默失效。

能力 provider 条目（search/fetch/ocr/asr/tts）单独一张表，字段比 chat 少：

```yaml
- id: deepseek-tools
  vendor: deepseek
  auth: { type: api-key, ref: DEEPSEEK_API_KEY }
  capabilities:
    search: { endpoint: https://api.deepseek.com/v1/tools/search, priceUnit: per-success-call }
    fetch:  { endpoint: https://api.deepseek.com/v1/tools/fetch,  priceUnit: per-success-call }
    ocr:    { endpoint: https://api.deepseek.com/v1/tools/ocr,    priceUnit: per-1000-pages }
    asr:    { endpoint: https://api.deepseek.com/v1/tools/asr,    priceUnit: per-second, maxSeconds: 120 }
    tts:    { endpoint: https://api.deepseek.com/v1/tools/tts,    priceUnit: per-char, voices: [stella, tide] }
```

### 4.4 必须写进 profile 的四个行为开关（每一条都是真实 400 或静默失效）

| 开关 | 作用 | 不做的后果 |
|---|---|---|
| `reasoning.passBack` | 请求带 `tools` 时，历史 `reasoning_content` 原样回传 | 直接 400，且只有带工具的会话会炸 |
| `thinking.default = off` | 显式关闭思考 | 服务端默认开，成本翻倍 + temperature 失效 |
| `compat.stripSamplingWhenThinking` | 思考时不下发 `temperature/top_p` | 以为调了参，实际没生效 |
| `kind` 三方言 | 协议字段差异在 codec 层显式处理 | 混在一个 codec 里悄悄降级，错误被吞 |

> 路径前缀不统一（模型端点裸 `/chat/completions`，工具端点 `/v1/tools/*`），
> 且 `baseUrl + /v1/chat/completions` 是否仍被接受**未核实**——I1 的第一条 curl 实测项。

### 4.5 Provider 管理界面（学 DSH 的「最顺手」，但不手写）

DSH 的 Models 页手写了 ≈160 KB TSX/TS；myco **schema 驱动 + 3 个自定义控件**（模型表、发现多选、密钥只写框）。

```
添加提供方
 ├─ 内置模板：DeepSeek 官方 / OpenAI 兼容（本地、中转）/ Anthropic Messages
 └─ 自定义：Provider ID（不可变，作 settings 键与凭据引用词干）、端点、协议、至少一个模型
     ├─ 获取可用模型：显式点按钮才调 /models → 可搜索多选 → 「添加所选」（保存时绝不自动探测）
     └─ 模型行：id / 显示名 / 上下文 / 最大输出 / 输入类型（文本·图片）
密钥：只写不读（回显只有「已配置/缺失」两态），存 env 引用或凭据库，永不进配置文件
删除：只清自己派生的 <ROUTE>_API_KEY；别人的 key 不动
行状态：由「档案 / 设置 / 凭据」三方快照合成，永远一致；保存失败保留草稿 + 展示 Host 诊断
两个动作要分开：「仅此会话用这个模型」 vs 「设为所有新会话默认」
```

---

## 5. 会话与 agent loop

### 5.1 会话 = append-only 事件日志（唯一真相）

```
~/.myco/sessions/<sessionId>/
  meta.json          { id, title, createdAt, cwd?, parentSession?, preset, formatVersion }
  events.v1.jsonl    session/start · turn/start · user/message · assistant/message
                     · tool/call · tool/result · system/inject · turn/end
```

- **不变量：模型可见即已记录**（来自 DSH）：任何模型看得到的内容都必须能从日志重建。
- 消息由 `deriveMessages(events)` 投影，不另存一份「对话数组」。
- 未知 `formatVersion` **拒绝打开**，不自动迁移（trace-memory：能力协商按能力降级，不猜版本）。

### 5.2 一轮（turn）的时序

```
user/message → turn/start
  组装：静态 system 段（字节稳定） + 历史消息 + 本步注入（结尾追加）
  → llm.stream → assistant/message（含 usage / reasoning 摘要 / 定价）
  → 若有 tool/call：执行 → tool/result → 下一步（上限 N 步）
turn/end（含 cost、latency、steps）
```

MVP 的循环刻意小：**单 agent、顺序工具、无子 agent、无并行工具、无自动 fork**。
这些不是永久放弃，而是接缝先留：`sessions.fork()` / `tools.execute` 管线 / `agent` 抽象。

### 5.3 缓存纪律（硬规则）

1. 静态段（人格、规则、工具 schema）**字节级稳定**；顺序固定；不得含时间戳/随机 id。
2. 动态事实（时间、焦点、世界状态）**只能作为消息追加**，不得进 system。
3. 每轮动态注入 ≤500 字符、≤2 条；无内容时**整段不出现**，不许发空壳。
4. 工具结果、压缩摘要都走追加；禁止重排历史。
5. 客户端显示「本轮 prefill 估算 / 缓存命中」，超过 25k 报警。

---

## 6. 记忆接缝（MVP 只做最小面）

### 6.1 七张表（SQLite，`node:sqlite`，零原生依赖）

`ns` / `item` / `vec` / `source_row` / `evidence` / `job_run` / `cursor`。
完整 DDL 见 [icl-memory.md §5.5](../recon/icl-memory.md)，三条关键设计：

- `(namespace, key)` 唯一索引：**带 key 才 upsert，NULL 才追加**——一条 DDL 分开幂等与追加。
- `source_row.source ∈ {human, agent_visible, agent_internal, tool, env, memory}`：
  不做这个分类，后面所有记忆都会被 agent 自己的话污染。
- `evidence` 独立成表 + `quote` 必须能在原文逐字找到：这是「她能回指证据」的唯一保证。

### 6.2 召回契约（抄 trace-memory 的语义，不抄实现）

```ts
type RecallStatus = 'ok' | 'not_indexed' | 'not_covered' | 'stale'
interface RecallResult {
  status: RecallStatus        // 不用「空串」表达没找到
  answer: string              // 只放事实摘要，不承担「没找到」的表达
  ids: string[]
  coverage: { scopeFrom: number; scopeTo: number; coveredMs: number; gaps: [number,number][]; truncated: number }
  freshness: { projectedUntil: number; lagMs: number; state: 'ok'|'catching_up'|'stalled' }
}
```

- 每个数据源各带一个 `freshness`，聚合取**最差**。
- **语义契约必须配反例测试**：参考实现自己就把「渲染失败」吞成 `answer=""`，与「没找到」语义互换。
- `readFrame`/`readBlock` 失败必须带 `reason` 枚举，不抛异常。

### 6.3 MVP 做 / 不做

| 做（无模型也能验） | 不做（留接缝） |
|---|---|
| 数据地基 + 六原语 | LLM 成块、typed 分类（fact/preference/commitment） |
| 原文层 + 来源分类 | entity / relation 图 |
| 混合打分（bigram + 精确串 + 衰减），无嵌入可退化 | 向量检索（先留 `vec` 表） |
| eager 注入（≤2 条 / ≤200 字 / 诚实话术） | 主动召回、注意力预算、seen 水位 |
| 规则化整理：按「时间间隔 >30min 或话题切换」切块 + 模板摘要 | dream 的「睡眠/天」隐喻与 7 job |
| 评测：1 个 fixture + 4 指标（anyHit@5 / missRate / falseInjectionRate / provenanceRate） | 评测平台化、语义 judge 进 gate |

> 本轮设计考古不调用任何模型服务（远程算力已被占用），所以 MVP 的记忆整理先走规则化模板；
> 记忆层的价值主张是「可信的原文 + 可检索的结构 + 诚实的注入」，LLM 成块只是后面加的一个 provider。

---

## 7. 人格与主动性接缝（留缝，不实现）

### 7.1 人格 = 配置，不是行为

- 一张 **1.5–4k 字符静态卡**（身份 / 短句节奏 / 3–5 个口头禅 / 边界：不许编造、不许假装看过 / 拒绝权）
  + 一张 **≤1.5k 字符的仿真段**（「在这个环境里怎么生活」）。
- **人格卡是版本化模块**（A1）：`cards[]` 多卡 + 每卡 `versions[]`（历史/回滚）+ `activeCardId`；每个 app 持有**独立副本**。
- **同步是单向复制**：work 提供"快速单向同步通道"，把 chat 的某卡某版本复制成本地新版本（带 `source:{app,cardId,version,hash}` 溯源）；
  **不是共享文件、不是双向合并**。任一方离线/未安装/版本陈旧都不影响另一方。
- **人格是全局常量，关系是每会话数据**——第一天就拆开，否则多会话只能二选一。
- **所有权**：人格卡的写权各自独立（work 改的是 work 的副本）；主动消息只有 chat 发（见 §7.3）。
- 不做：关系值、驱动值、七情状态机、每轮 mood judge（成本 ~800 tok/轮且不可验证）。

### 7.2 事实段（每轮动态注入的唯一内容）

```
时间｜你在做什么｜谁在等你
```
≤2 行、只陈述不教导、无内容整段消失、`tier: dynamic`。
时间只写「日期 + 周几 + 时段」，**不写分钟**（分钟级抖动每 60s 作废前缀）。

### 7.3 主动性 = 出站队列 + 裁决函数 + 条件唤醒

```
pending = 未读（按 activity 三档水位） + 惦记（open note） + 积压
定时器（无 turn 在跑时才计时）→ 只做一次 pending 计算
  ├─ pending > 0 且 idle ≥ 最小空闲 → 起一个 turn，投「决策简报」
  ├─ pending = 0 → 不产生 turn，只记 skip，间隔 ×1.5（封顶 12×）
  └─ 每 turn 出口 = 回复 / ack / 明确 no-op（「什么都不做也不失礼」）
```

- `decide(input)` 是**纯函数**：`{action: deliver|queue|drop, channel, suppressReason, wake, holdMs, queueKey, explain}`；
  `drop` 只留给去重/过期，其余一律降级（进队列，不丢）。
- 队列**落盘**（`~/.myco/delivery.jsonl`）+ 启动重放未 ack（前代纯内存，崩溃即丢）。
- 预算：小时 N + **天** M（前代缺天预算），杀 switch 必须有。
- 感知与发送权分开：感知只决定「她知道了什么」，是否开口仍走裁决 + 预算。
- **发出权只有 chat**：work 只发任务完成通知；它向 chat 暴露的只有「他在忙什么」这类环境事实。
- 三层主动性：L1 处理等待中的消息（默认开）/ L2 自己的时间（可选）/ L3 无由头主动（默认关，需单独预算）。

### 7.4 感知接口（未来，先定形状）

```ts
snapshot(): Promise<Snapshot>   // 结构化、毫秒级、便宜（窗口/活动/焦点）
capture(opts): Promise<Frame>   // 像素、秒级、贵、有配额、带失败码
```

「实时」目前只能兑现成**按需单帧**：截图 7.1s/张、1.3GB 浏览器、视频抽帧 0.8s、ASR 冷启动 9s。
**常驻 1fps 已被明确否决。**

---

## 8. MVP 路线（每步可独立验收）

| 阶段 | 内容 | 验收（必须可自动跑） | 估时 |
|---|---|---|---|
| **I0 骨架** | 语言无关契约定稿（JSON Schema / 事件表 / Bridge 接口）→ 按 D9 选定的栈建 monorepo、kernel、manifest 校验、装配自检、session-store 只读、`myco doctor` | 契约样例测试先过审；装配出 0 插件不崩；坏 manifest 拒绝加载并报错；`doctor` 输出预算与未绑定槽；事件日志可写可读可投影；**headless（无 GUI）跑完一轮** | 2–3 天 |
| **I1 provider** | profile schema/持久化/凭据引用、`/models` 发现、流式对话（先 CLI） | 本地档案能流式出字；DeepSeek 档案缺 key 时**降级可用**且提示明确；usage/cost 落库 | 3–4 天 |
| **I2 会话+loop → MVP** | 事件日志实例化、工具注册表 + 3 个工具、取消/重试、**多会话 chat UI（会话列表 + 聊天框 + 设置页，移动优先）**、provider 热切 | 两会话并发互不污染；杀进程后恢复历史；工具 schema 预算测试；会话列表可增删/改名；**能连续用一天替代官方 chat** | 5–7 天 |
| **I3 记忆最小闭环** | 七表 + 原文层 + 来源分类 + eager 注入 + recall 工具 + 4 指标 | 「无关 query 返回空」；「停采集时状态为 stale 而不是空答案」；evidence 可逐字校验 | 4–5 天 |
| **I4 主动性（仅 chat）** | 出站队列落盘、decide()、条件唤醒、通知适配器 | 无 pending 时不产生 turn（断言调用次数=0）；崩溃重启不丢队列 | 3–4 天 |
| **I5 work 轻任务** | work 壳、多会话 UI、交互 HTML/算器/脚本工具、只读共享记忆 | work 不主动发消息；读 chat 记忆不写坏它 | — |
| **I6 感知与重后端** | snapshot/capture 首帧、无障碍 computer use、proot 按需启动 | 每个能力有成本声明与失败码 | — |

> **MVP（I0–I2）约 2 周**，交付"能平替官方 chat 的 myco chat"；I3–I4 再加约 2 周得到"有记忆、会主动"的陪伴版。
> work 与感知在 MVP 之后，但**接口形状在 I0 就留好**。任何新插件先回答「它属于上面哪条线」，否则不做。
> **I0 的前半段（契约）与语言无关**：D9 运行时拍板前只产出 JSON Schema / 事件表 / Bridge 接口 + 样例测试，
> 不写绑死某一栈的产品代码——这是 A4/A5 之后最重要的"避免技术债"动作。

---

## 9. 反模式清单（从两代学费里挑出来的十条）

1. 六抽象/13 服务先行，再找使用者（一半空转，每个新服务改 5 处）。
2. manifest 里写没人读的字段（`entry`/`presets` 无人读）——契约幻觉比没契约更贵。
3. 把宿主脚手架继承进产品首屏（56% 的 system 在教她「你是文件代理」）。
4. 自研 agent loop 之前先自研协议层/客户端 SDK（1008 行零测试、零调用方）。
5. 大而全工具面默认全开（68 工具/46.7k 字符）。
6. 把「她一个人在本机」当产品假设（单会话/单进程/无锁/标签可伪造）。
7. 复述式文档 + 手写数字。
8. 到点必发的状态播报心跳。
9. 让「只开一个会话」当人为约定。
10. 为「像人」故意编错事实，或让感知接口假装看到了（人格底线：只说自己真的经历过的）。

---

## 10. 决策状态

**D1–D8 已于 2026-11-25 全部拍板**，权威记录见 [20-decision-record.md](20-decision-record.md)。
原候选清单 [10-open-questions.md](10-open-questions.md) 保留为决策背景（已标注每条结论），不再作为待办。

仍未定、但**不阻塞 I0** 的四条（记录在决策记录末尾）：

1. 手机上的内核载体（WebView + 内嵌运行时 / Termux+proot / 原生壳连本机 daemon）——等移动端底盘调研。
2. 两个 app 共享存储的粒度（一份库 + 各自策略 vs 两份库 + 单向同步）——当前倾向一份库。
3. provider 配置是否两 app 共享——当前倾向共享。
4. 桌面版的定位——当前只承诺"开发与预览载体"。

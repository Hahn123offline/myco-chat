# I0 实施方案（骨架 · D9 已定 Kotlin/JVM；T0 已交付，待开工点头）

> 前置：D1=A（桌面 daemon + Web 先跑，设计移动端原生）、D2=A（自研薄内核）、D3=两个 app 共享存储、
> D4+A2=MVP 到 I2（多会话 chat）、**A4=离线独立是硬要求**、A5=GUI 模块化、A6=同步模型、
> D9=Kotlin/JVM 单核心（Play 论证降权，分发侧载）、D10=两个 APK + 显式同步。
> 决策见 [决策记录](20-decision-record.md)。
>
> **T0（语言无关契约）已交付**：[`specs/`](../../specs/README.md)——4 份 JSON Schema + Bridge 契约 + 23 条 fixture，
> `python specs/validate_fixtures.py` 23/23 通过。下一步是 S0 walking skeleton。
> 本文是**施工图**，不含业务代码。开工条件：D9 拍板 + 用户对本文点头（或改需求）。

## 0. I0 的验收定义（一句话）

**一条命令装配出可运行的内核，一条命令证明它没坏，一条命令起 GUI 看到空会话——且无 GUI 时同样能跑一轮。**

```
pnpm install && pnpm -r test          # 全绿（命令名按 D9 栈调整）
myco doctor                          # 输出 8 项自检，退出码 0
myco dev                             # 起 GUI（浏览器或原生壳），能看到会话列表与"未配置 provider"引导
myco dev --headless                  # 不加载 GUI，跑完一轮假对话，退出码 0
```

## 1. 不做什么（I0 边界）

- 不接任何模型（`llm` 只有类型与空实现，测试用 fake adapter）。
- 不做工具（除 `time.now` 一个演示工具，用来验证注册/预算/注销）。
- 不做记忆（I3）、不做主动性（I4）、不做 work 壳（I5）。
- 不做手机壳（但契约与存储格式按移动端原生 + 离线独立约束写）。
- **不做技术栈定案**：§3 的契约先以语言无关形式（JSON Schema + 接口文档 + 样例 fixture）定稿；D9 拍板后才建代码骨架。
- 不引入 ORM、不引入前端框架全家桶、不引原生依赖（SQLite 用各栈内置/官方驱动）。

## 2. 目录（I0 落点：先契约，后骨架）

**阶段 A（D9 前，必做）——语言无关规格**

```
specs/
  provider-profile.schema.json      # chat provider（见设计简报 §4）
  capability-provider.schema.json   # search/fetch/ocr/asr/tts 条目
  plugin-manifest.schema.json       # id/version/entry/tools/slots/requires/budget
  session-events.schema.json        # 事件联合 + seq/at 约束
  bridge.contract.md                # GUI Bridge：命令/事件/查询 + 两种传输语义
  fixture/                          # 每份 schema 的合法/非法样例（用来测实现）
```

**阶段 B（D9 后，按选定栈落地）——示例为 TS 形态，D9 后替换**

```
packages/
  contracts/     纯类型 + schema 校验（与 specs/ 一一对应）
  kernel/        插件加载、manifest 校验、接缝注册表、注销器、事件分发、预算、doctor
  settings/      分层配置、revision、路径 mutate
  credentials/   ref/record/describe
  session-store/ 事件日志 + 投影 + 版本守卫
  bridge/        Bridge 接口 + in-process 实现 + HTTP/SSE 实现
  tools/         注册表 + 执行管线
apps/
  chat/          chat GUI（移动优先；MVP 交付物）
  cli/           myco doctor / myco dev --headless
plugins/time-now/ 演示插件
config/providers.yaml
```

## 3. 契约（I0 必须先定死的接口）

```ts
// contracts/plugin.ts
export interface PluginManifest {
  id: string                        // ^[a-z][a-z0-9-]*$
  version: string
  entry: string                     // ./src/index.mjs（loader 读，不猜）
  tools?: string[]                  // 工具面唯一真相
  slots?: SlotDecl[]
  requires?: { plugins?: string[]; capabilities?: string[] }
  budget?: { toolsChars?: number }
}
export interface PluginContext {
  id: string
  config: unknown
  log(level: 'info'|'warn'|'error', msg: string, data?: unknown): void
  onDispose(fn: () => void | Promise<void>): void
  tools: { register(def: ToolDef): Disposer }
  prompt: { section(def: PromptSection): Disposer }
  slots: { declare(decl: SlotDecl): Disposer; provider(kind: string, p: SlotProvider): Disposer }
  services: { provide(name: string, api: unknown): Disposer; get<T>(name: string): T | undefined }
}
export type Disposer = () => void | Promise<void>
export type ApplyFn = (ctx: PluginContext) => void | Disposer | Disposer[] | Promise<void | Disposer | Disposer[]>

// contracts/tool.ts
export interface ToolDef {
  name: string                      // ^[a-z][a-z0-9_]*$
  summary: string                   // ≤160 字符，进工具索引
  description: string               // ≤900 字符
  parameters: JsonSchema
  execute(args: unknown, ctx: ToolCtx): Promise<ToolResult>
}

// contracts/session.ts
export interface SessionMeta { id: string; title: string; createdAt: number; app: 'chat'|'work'; preset: string; formatVersion: 1 }
export type SessionEvent =
  | { type: 'session/start'; seq: number; at: number; data: SessionMeta }
  | { type: 'turn/start'; seq: number; at: number; data: { turn: number } }
  | { type: 'user/message'; seq: number; at: number; data: { text: string; source?: string } }
  | { type: 'assistant/message'; seq: number; at: number; data: { text: string; usage?: Usage; model?: string } }
  | { type: 'tool/call'; seq: number; at: number; data: { name: string; args: unknown } }
  | { type: 'tool/result'; seq: number; at: number; data: { name: string; ok: boolean; text: string } }
  | { type: 'system/inject'; seq: number; at: number; data: { text: string } }
  | { type: 'turn/end'; seq: number; at: number; data: { turn: number; steps: number; costUsd?: number } }
export interface SessionStore {
  create(input: { app: 'chat'|'work'; preset: string }): Promise<SessionMeta>
  open(id: string): Promise<SessionHandle>     // 未知 formatVersion → 抛错，不迁移
  list(): Promise<SessionMeta[]>
}

// contracts/bridge.ts —— GUI 唯一契约（A5）
export interface Bridge {
  send(cmd: Command): Promise<CommandResult>     // chat.send / session.create / provider.upsert ...
  query(q: Query): Promise<QueryResult>           // sessions.list / providers.list / doctor.run
  subscribe(listener: (e: BridgeEvent) => void): Disposer  // 流式增量 / 状态变更
}
export type BridgeTransport = 'in-process' | 'http-sse'
// 规则：GUI 只能通过 Bridge 说话，不得直接读存储/import 核心内部；
// in-process 是手机端默认（不开本地服务器），http-sse 只用于浏览器/远程/调试。
```

**三条约定的实现细节**
1. `seq` 单调递增、由 store 分配；重放时按 seq 排序，不做时间排序。
2. 所有路径存相对 `$MYCO_HOME`，日志里禁止绝对路径（多设备同步前置）。
3. 写事件 = 一次 `appendFile` + `fsync` 策略可配；崩溃只允许尾部半行，读侧丢弃不完整行并报 warn。

## 4. 任务拆分（每项独立可验收，按序）

| # | 任务 | 写作用域 | 验收 |
|---|---|---|---|
| **T0** | **语言无关契约定稿**：5 份 JSON Schema + Bridge 契约文档 + 合法/非法 fixture | `specs/` | 每份 schema 有 ≥3 个非法样例被拒；Bridge 契约能覆盖"发消息/收流/列会话/查 doctor"四件事 |
| T1 | 按 D9 栈建 workspace + contracts 实现 + 测试脚手架 | `packages/contracts`, 根配置 | 编译/类型检查通过；schema 校验测试（坏 manifest 被拒） |
| T2 | kernel：registry/disposer/loader/surface/budget | `packages/kernel` | ①注册逆序注销 ②apply 抛错回滚 ③未声明工具不可见 ④预算超限报错 ⑤0 插件不崩 |
| T3 | settings + credentials | `packages/settings`, `packages/credentials` | revision 冲突被拒；密钥只进 record 不进 yaml；describe 不回值 |
| T4 | session-store（写/读/投影/版本守卫） | `packages/session-store` | 追加-重放一致；半行尾部被丢弃；未知版本拒绝打开 |
| T5 | bridge（in-process 优先 + http-sse 第二实现） | `packages/bridge` | 两种传输跑同一套契约测试；断线重连不重复事件；**不加载 GUI 时核心能跑一轮** |
| T6 | demo 插件 `time-now` + chat 空壳（会话列表 + 输入框 + 设置入口）+ doctor + `myco dev` | `plugins/time-now`, `apps/*` | doctor 8 项全过；chat 空壳能看到"未配置 provider"引导；`--headless` 退出码 0 |

> 每个任务完成即跑 `pnpm -r test` + 对应验收；**不允许**"最后一起测"。

## 5. `myco doctor` 输出什么（I0 的体检表）

```
myco 0.0.0 · home E:\myco-home
[ok]   store        ~/.myco/store 可写（sessions/x, memory.db 未建）
[ok]   plugins      1 loaded (time-now), 0 failed
[ok]   tools        1 / 预算 1,204 字符 / 上限 24,000
[ok]   slots        未绑定 0（llm.chat 尚未声明 → 预期）
[warn] providers    0 个已配置（本地与 DeepSeek 模板可一键添加）
[ok]   credentials  引用解析器就绪（env + file 后端）
[ok]   sessions     1 个（formatVersion 1）
[ok]   bridge       in-process + http-sse 各实现一次契约测试；GUI 可整体替换
```
退出码：全部 ok → 0；有 warn → 0；有 error → 1。**doctor 永不静默**（每个失败都打印 reason）。

## 6. 测试清单（I0 全部）

- `specs`: 每份 JSON Schema 的合法/非法 fixture 全部通过（契约先于实现）。
- `contracts`: 坏 manifest（缺 version、id 非法、tools 非字符串数组）被拒。
- `kernel`: 注销逆序；apply reject 回滚；surface 三重交集；预算超限；未绑定槽在装配报告里出现。
- `settings`: 并发写 revision 冲突；路径 mutate 只改目标字段。
- `credentials`: ref 只解析 env；`describe()` 不含值；文件后端权限告警。
- `session-store`: 300 条事件重放一致；截断尾行；未知版本；两会话并发写互不串。
- `bridge`: 两种传输跑同一套契约测试；SSE 心跳与断线重连（fake client）；**headless 跑完一轮**（不加载 GUI）。
- `doctor`: 每项失败时的输出与退出码。

## 7. 风险与对策

| 风险 | 对策 |
|---|---|
| 内核过早变"重"（重演 14 服务） | 只实现本文件 §3 的接口；新增接缝必须有第二个使用者 |
| 移动端约束被桌面实现带偏 | 契约按"离线独立 + in-process Bridge + mobile-first"写；路径/通知/耗电在 I0 就跑在抽象后 |
| 技术栈选错（最大风险） | 已用 T0（语言无关契约）+ D9 调研压掉；S0 用真实代码竖切验证，不写一次性 spike |
| 动态插件诱惑（侧载后失去 Play 约束） | 明确推迟：编译期模块 + `PluginLoader` 接缝；没有真实需求不做动态 dex |
| GUI 又变成"后端的一部分" | Bridge 是唯一接口 + headless 测试进 CI；GUI 模块独立目录，可整体删除 |
| 事件日志格式以后要改 | `formatVersion` + 读侧拒绝未知版本；迁移链等第一次破坏性变更再上 |
| 与 DSH 学歪（手写设置页） | I1 设置页按 schema 生成，禁止手写表单 |
| 凭证在 Windows 上的安全预期 | I0 只用 env + 文件（0600/ACL 告警），不做"看起来安全"的假象 |

## 8. 下一步：S0 walking skeleton（替代一次性 spike）

**不做一次性 spike**（MVP 很小、成熟实现很多，验证代码会变成垃圾）。改成**真实产品代码的最小竖切**，
3–5 天，边建边测；测下来的数字直接进 I0 的验收基线。

### S0 交付物

```
myco/                            Gradle 多模块
  settings.gradle.kts
  core/                          Kotlin JVM 库（Android/桌面共用）
    spec/                        按 specs/ 生成或手写的契约类型（kotlinx.serialization）
    session/                     JSONL 事件日志读写 + seq 分配 + 版本守卫
    provider/                    profile 解析 + 凭据 ref 解析（值不进日志）
    llm/                         OpenAI 兼容 SSE 客户端（只依赖 java.net.http）
    bridge/                      Bridge 接口 + in-process 实现
    tools/                       注册表 + 1 个演示工具 time_now
  app-android/                   Compose 壳：会话列表 + 聊天页 + 设置入口 + FGS 骨架
  app-desktop/                   JVM 壳（Ktor 可选）：开发/预览用
  tools/fake-sse/                假模型服务（本地 SSE，用于 spike 测量，不调真模型）
```

### 要测的三件真不确定的事（其余都是成熟实现，不测）

| # | 不确定性 | S0 怎么测 | 判据（不过就改方案） |
|---|---|---|---|
| 1 | **国产 ROM 上的后台存活与耗电** | 真机跑 FGS + 常驻通知 + 每 15min 检查，24h | 24h 存活 ≥95%；耗电 ≤3%/24h；被 kill 能自恢复 |
| 2 | **低端机上的流式体验与包体** | fake SSE 流式出字；测冷启动/内存/APK 体积 | 首 token p50<2s；冷启动≤1.5s；RSS≤120MB；arm64 增量≤12MB |
| 3 | **前代 Kotlin 代码的移植摩擦力** | 从 trace-memory 抠出事件日志 + 一个 provider codec 塞进 core，跑通 | 不引入 Android 依赖；单测可跑；改动量可接受（<500 行） |

> SQLite、JSON Schema、SSE、通知在 Android 上都是成熟实现，**不花时间做 spike**；直接按官方最佳实践写。

### S0 的验收（可自动/可复现）

```
./gradlew :core:test           # 事件日志 + provider + bridge 契约测试
./gradlew :app-desktop:run     # 桌面壳连 fake-sse，看到流式回复
./gradlew :app-android:assembleDebug
adb install ...                # 真机：会话列表 → 新建会话 → 发消息 → 流出 → 杀进程重开历史还在
```
外加一页 `docs/reports/s0-measurements.md`：包体、冷启动、RSS、24h 存活与耗电、移植行数。

### 之后

S0 通过 → 按 T1–T6 继续 I0（kernel/settings/session-store/bridge/doctor/chat 空壳），
只是"技术栈待定"的句子删掉，其余不变。

## 9. 开工前只差一句"开工"

T0 已交付（specs/ 23/23）；D9/D10 已定；S0 方案与判据如上。
你说"开工"，我先做 S0；若你想先看 Gradle 骨架再决定，我就只提交骨架 + 一份 `gradlew tasks` 输出。

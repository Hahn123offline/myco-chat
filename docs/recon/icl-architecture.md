# dsh-icl 架构考古（供 myco MVP 设计）

> 阅读范围：`E:\11-15\tools\external\dsh-icl`（HEAD 无 git 操作，按磁盘内容为准；文档最后日期 2026-11-15）。
> 方法：先读代码再读文档，**文档与代码冲突一律以代码为准并点名**。未跑构建/测试/模型服务，全部静态阅读。
> 证据坐标格式：`文件:行`，行号取本机文件实际行数（文档里的行号常已漂移）。

**本报告的证据等级（统一口径，后面每条都标）**

| 级别 | 含义 | 本项目里典型物 |
|---|---|---|
| 【代码】 | 直接读到源码/配置，事实性最强 | `host.mjs`、`presets/icl.patch.yml` |
| 【E1】 | 有单测/契约测试（假宿主、临时 SQLite） | `packages/*/test/*.test.mjs` |
| 【E2】 | 假 DSH / lab / headless 集成 | `fakeDsh`、`tools/lab.mjs`、`dsh --patch` headless |
| 【E3】 | 真实 DSH 会话跑过（真实模型/真实事件） | session `1f855bc3`、`4de2a564` |
| 【E4】 | 长时间运行（≥30min 或跨天） | night-1 6h29m / 1220 请求 |
| 【E0】 | 只有文档声称 | 「产品已部署」「效果好不好」类 |

> ⚠️ **陷阱先讲**：dsh-icl 自己用了**两套互相冲突的 E 编号**。`docs/OVERVIEW.md:55-63` 定义 E0=只有文档、E4=长跑；而 `docs/READINESS-MATRIX.md:11-17` 定义 **E0=仓库测试、E4=文档声明（不算证据）**。同一份仓库里 "E2" 既指"假 DSH 集成"（OVERVIEW）又指"lab 实测"（READINESS）。引用它的结论时必须先问"这是哪套 E"。这是 B3 文档负债的一个实例，也是 myco 要避免的第一件事。

---

## 0. 一页结论（≤15 行）

1. **它不是 agent 框架，是"记忆+作息引擎"**：agent loop / 会话 / 模型调用全在 DSH 里，它自己只有 40 个 `.mjs`（8,277 行）+ 一个宿主（`docs/OVERVIEW.md:21-24`、`ARCHITECTURE.md:35-42`，【代码】`packages/core/src/host.mjs:13`）。三代同名项目（dsh-qq → focus-core → icl）每次都在补同一块：身份连续性。
2. **"一切皆插件"的具体形态 = 目录 + `manifest.json` + `apply(ctx)` + 注销器**，能力面只有 8 个注册函数；宿主只有一个 `Map` 和一个 `dispose[]`，没有事件总线、没有 DI 容器、没有装饰器（【代码】`host.mjs:179-336`、`bootstrap.mjs:48-92`）。
3. **六大抽象落成了 14 个服务**（`host.mjs:338-347`），其中真正有第二个使用者的约 6 个；`presets`、`delivery.push`、`context.compactors()`、`focus` 大半空转（`docs/STATUS.md:55-67`）。
4. **单会话不是"锁没做好"，是架构决定**：`claimSession` 一行代码（`host.mjs:350-356`），但零文件锁、单份 `memory.db`/`time.json`（`docs/ARCHITECTURE.md:222-240`）。三代都栽在"先每宿主起一份实例，再用锁去补"（`docs/HISTORY.md:85-89` L1）。
5. **产品化最终判断 = 单用户本地 beta+**，且明确"单会话/单进程/投递不落盘/标签不可信是'她一个人在本机生活'的前提，不是产品前提"（`docs/QUALITY-CORE.md:84,93`）。
6. **最强的可复用资产是纪律而不是代码**：注销器模型、manifest 声明式工具面、证据分级、红队"文档是被告"审查。最不可复用的是 `bridge.mjs`（1543 行，与 DSH 0.2.x 内部 API 强耦合）。
7. **已付的最大一笔学费**：广度先于深度（17 插件/68 工具/两次 5 分钟），以及**第一屏 56% 是 DSH 的 coding-agent 脚手架**（`docs/REDTEAM-REVIEW.md:12`，P0）。
8. 对 myco MVP：抄"注销器+manifest+白名单+预算+失败要吵"，改"单会话→会话键控"，**别抄"六抽象先行"和自研 loop**。

---

## 1. 事实清单（带出处）

### 1.1 仓库与包形状（【代码】，2026-11-25 磁盘实测）

| 事实 | 值 | 坐标 |
|---|---|---|
| 产品包 | `packages/core`（13 个 `.mjs`）、`packages/plugins`（**22** 个插件目录）、`packages/dsh-icl`（DSH 插件+preset）、`packages/adapters`（仓库内零调用方）、`packages/mc-body` | `packages/*` |
| 唯一 loader | `bootstrapHost`：读 `packages/plugins/<id>/manifest.json` + `src/index.mjs`，逐个 `registerPlugin`+`enable`；**单插件失败只记账不拖垮** | `packages/dsh-icl/src/bootstrap.mjs:48-92` |
| 宿主 | `createHost` 建 14 个服务 + `claimSession` + `registerPlugin/enable/disable`；437 行（文档写 273 行 → 已漂移） | `packages/core/src/host.mjs:13,338-436` |
| 服务工厂 | `services.mjs` **1012 行**（文档写 655 行 → 已漂移） | `packages/core/src/services.mjs` |
| DSH 桥 | `bridge.mjs` **1543 行 / 77 KB**，只做五件事：工具面→`ctx.tools.register`、提示词→`systemPrompt.section/context`、投递→`agent.steer/followup`、回合→`session/event`、每轮→`agent/pre-step` | `packages/dsh-icl/src/bridge.mjs:1-10` |
| 产品 preset | `presets/icl.patch.yml` 227 行：`icl-core.config.plugins` **17 个**（含 `qq`，不含 `mc-link`）+ 4 段 `purifySections` + `contextWindow: 256000` | `presets/icl.patch.yml:12-57` |
| 工具面现状 | preset 注释自记：MC 下线后 **66 工具 / 44,316 字符 → 49 / 31,824**（2026-11-08） | `presets/icl.patch.yml:33-34` |
| manifest 真身 | `{"id":"recall","version":"0.1.0","entry":"./src/index.mjs","tools":["remember","recall","forget"],"slots":[],"focus":{},"requires":{"plugins":["memory"]}}` | `packages/plugins/recall/manifest.json` |
| 校验器 | `validateManifest`：id `/^[a-z][a-z0-9-]*$/`、version/entry 必填、`tools` 必须字符串数组、slot 必须有 id+kind、`focus.pierce` 值必须在 `always/mention/never`；**未知字段保留**（不是白名单重建） | `packages/core/src/contracts.mjs:13-53` |
| `entry` 的真相 | **必填但核心从不读取**——"导入模块是装配方的事"，`bootstrap.mjs` 直接拼 `src/index.mjs` | `docs/ARCHITECTURE.md:255`，`bootstrap.mjs:51` |

### 1.2 证据账（本项目到底跑到哪一步）

| 证据 | 内容 | 级别 | 坐标 |
|---|---|---|---|
| 单测 | 269 通过（11-03）→ 452（11-06）→ 748/454（11-06 产品化）→ **780 条/779 绿**（11-0x） | E1 | `docs/STATUS.md:13`；`READINESS-MATRIX.md:23`；`PRODUCTIZATION-KICKOFF.md:205`；`QUALITY-CORE.md:93` |
| headless 真会话 | session `b26031a9`（51 工具+人格段）、`46a5633b`（time_arrange 真调用）、`1f855bc3`（wake→signals→delivery→`followup`→新轮）、`4de2a564`（`agent/pre-step` 注入 `<icl-context>`） | E2/E3 | `docs/DSH-INTEGRATION.md:24-34` |
| desktop 真会话 | 单会话否决实测（第二个 ICL 会话被拒）、focus 持久化验证 | E3 | `docs/STATUS.md:225`；`docs/FOCUS.md:46` |
| 长跑 | night-1：**6h29m / 1220 请求 / 缓存命中 99.50%**，4h30m 睡眠零打扰、17 条积压一次释放 | E4 | `docs/QUALITY-CORE.md:93` |
| 复杂度实测 | E2 120 分钟 / 50 轮：57% 墙钟在工具调用、70% 上下文是重复 runtime-context（91k 字符，2.5 条/轮） | E2/E4 | `docs/COMPLEXITY-AUDIT.md:5,76` |
| 两轮各 5 分钟 | 唯一"早期端到端"，其中一轮因两会话共享 `time.json` **作废** | E3 | `docs/STATUS.md:131-144` |
| 最刺眼 P0 | 第一屏静态 system 4908 字符，其中 **2739（56%）是 DSH 工程脚手架**，首行仍 `You are an AI agent…` | E2（lab 实测） | `docs/REDTEAM-REVIEW.md:12` |

### 1.3 8 处"文档说 A、代码是 B"（我自己复核到的，比传闻的 22 处少但都硬）

| # | 文档说 | 代码/磁盘是 | 坐标 |
|---|---|---|---|
| 1 | 13 个服务 | `host.services` 实际 14 个键（多 `contracts`） | `ARCHITECTURE.md:78` vs `host.mjs:338-347` |
| 2 | `services.mjs` 655 行 / `host.mjs` 273 行 | 1012 / 437 行 | `ARCHITECTURE.md:26,37` |
| 3 | 16（或 19/21）个插件 | **22 个**插件目录 | `ARCHITECTURE.md:40`、`PLUGINS.md:36` vs `packages/plugins/` |
| 4 | 产品 68 工具 / 46,766 字符 | preset 自己记 49 / 31,824（MC 下线 + qq 上线后） | `PRODUCT-SURFACE.md:5` vs `presets/icl.patch.yml:33-34` |
| 5 | `CONTRACTS.md` 说"69 个工具" | `PLUGINS.md` 说 56，preset 说 49 | `CONTRACTS.md:60` |
| 6 | `STATUS.md` 说"单会话未强制、无物阻止同跑" | `bridge.bindSession` + `claimSession` + `agent/disposed` 释放，SL-1/2 判"达成" | `STATUS.md:38` vs `READINESS-MATRIX.md:92`、`bridge.mjs:1076-1109` |
| 7 | `DSH-INTEGRATION.md §3` 说 desktop 挂的是上一代 focus-core | 同文 §0 说 2026-11-03 已 `installBundle` 本包 | `DSH-INTEGRATION.md:106-108` vs `:41-48` |
| 8 | `index.mjs` 9 行只转出 17 个符号 | 20 行，约 30 个符号 | `ARCHITECTURE.md:47` vs `core/src/index.mjs:1-20` |

> 结论（B3 的直接教训）：**这个仓库的文档在它最擅长的"结构化事实"上仍然系统性过期**，因为数字是手写的、没有生成脚本（红队建议见 `REDTEAM-REVIEW.md:586`"文档生成化"）。myco 的第一条纪律应该是"数字由脚本产出，文档只写决策与索引"。

---

## 2. 核心设计：插件宿主与六大抽象

### 2.1 加载与注册的完整链路（【代码】）

```
DSH 启动 → 读 preset 包 (package.json 里 dsh.bundle.patch)
        → packages/dsh-icl/index.mjs (createIclPlugin)
        → 监听 session/created、agent/created、session/event、agent/pre-step
        → boot(): createHost() → createDshBridge() → bootstrapHost()
        → 对每个 plugin：loadPlugin(id) → validateManifest → registerPlugin → enable(id)
        → enable: 1 检查 requires.plugins 已启用 · 2 slots.declare(manifest.slots)
                  · 3 await apply(ctx) · 4 返回值/注册物全进 dispose[] · 5 surface.notify()
```

**一个插件到底能注册什么——只有 8 个面（【代码】`host.mjs:179-336` 逐个核对）**：

| 面 | 入口 | 典型插件 |
|---|---|---|
| 工具 | `ctx.tools.register({name,summary,description,parameters,execute,output,contract})` → 进 DSH 工具面 | recall/note/time/bili/video… |
| 提示词段 | `ctx.prompt.section({name,order,text,when})` | persona(20)/world-state(10)/focus(15)/simulation(25) |
| 每轮上下文 | `ctx.prompt.onTurn(fn)`（预算 800） | eager-recall |
| 上下文策略 | `ctx.services.context.onAssemble({name,order,apply})` / `onCompact` | compaction（产品默认关） |
| 投递源 | `ctx.signals.offer/emit` + `delivery.push`（**插件实际只用前者**） | time/dream/qq/loop-probe |
| 槽位 | `ctx.slots.declare` / `registerProvider` / `call` | memory(embed-text)、dream(dream-chat)、model-manager(provider) |
| 原文源 | `ctx.services.raw.register({id,search,list})` | dsh-raw；bridge 内部另注册 `dsh-session` |
| 插件间能力 | `ctx.services.provide(name,api)` / `get` / `require`（**不是服务，是 `shared` 表**） | time/memory/note/lexicon/persona/world/qq… |

**没有的注册面（同样是设计决定，myco 要知道）**：没有命令面（全仓 grep 无 `.command(`/`registerCommand`）、没有 UI 面、没有 HTTP 端点、没有事件总线给插件用；**插件依赖不是 `inject`**，只有 `manifest.requires.plugins` 的启用顺序检查 + `shared` 能力表（`ctx.inject` 只在桥内部用于可选服务如 `attachments`/`apiProxy`/`compaction`，`bridge.mjs:76-78,1376,1470`）。

四个关键设计决定（都不是常识，值得抄）：
- **注册即返回注销器**：`ctx.tools.register` / `prompt.section` / `focus.enter` / `slots.declare`… 每个都返回 `off()`，宿主统一按逆序调用，"下一轮装配就看不到它的东西"（`host.mjs:1-5,179-336`）。**没有第二套状态要同步**——这是整个插件体系最省的地方。
- **manifest 是工具面的唯一真相**：`surface.list()` 只输出 `manifest.tools` 声明过 ∩ 插件已启用 ∩ 预设允许（`services.mjs:179-196`）。**漏声明 = 静默隐形，不报错**（`ARCHITECTURE.md:257`）——优点是可审计，缺点是新人必踩。
- **预设由宿主代注册插槽**：插件只写"我需要什么形状"，不可能写死模型名（`host.mjs:385-393`）。
- **失败回滚但不静默**：`apply` reject → 逆序注销 + `enabled=false`（`host.mjs:394-404`）；`slot` declare 失败同样回滚（`:385-393`）。

### 2.2 六大抽象：契约 / 实现代价 / 谁真的在用

| 抽象 | 契约（关键方法） | 实现代价 & 隐藏约束 | 真实使用者（【代码】核对） |
|---|---|---|---|
| **工具面** | `tools.register(def)→off`、`list/names/index({maxChars})`、`surface.list/notify` | 描述有预算：`perTool 900`、索引行 `summary≤160`、`index 1200`（`services.mjs:17`）；名字 `/^[a-z][a-z0-9_]*$/`；重名抛。**桥侧还有一次失败回退**：DSH 不接受 image output schema 时整体切 text-only 并 warn（`bridge.mjs:349-375`） | 10 个插件注册 28 工具（`ARCHITECTURE.md:82`）；后来 22 插件、最多 68 工具 |
| **提示词段** | `prompt.section({name,order,text,when})`、`prompt.onTurn(fn)`、`assemble(ctx, range)` | `assemble` **无预算、不截断**；按 `order` 升序、同 order 按名字；`turnText` 预算 800。**两层拆分是缓存关键**：`order<800` 进静态 section，`≥800` 走 `systemPrompt.context` 每步/每轮快照（`bridge.mjs:386,410,454`） | world-state(10)/persona(20)/simulation(25)/focus(15)/note 的 `purpose`(879)/contracts(780) |
| **投递** | `push({to,source,content,batchKey,exactlyOnce})`、`drain/ack/nack`、`watermarkOf`、`pending/inflight/droppedCount`、`addGate` | `maxAttempts=5`、退避 `min(60s, 500·2^(n-2))`、`exactlyOnce` 靠 `(plugin,activity,seq)` 水位；**纯内存、无落盘**（崩溃丢队列，DL-7 判"未达成"）；第 5 次 nack **静默丢弃**只加 `droppedCount` | `signals.setDeliver` + bridge tick 是**全部**消费者；**没有任何插件直接 `delivery.push`**（`STATUS.md:59`） |
| **槽位** | `declare`、`registerProvider({kind,shape,call})`、`autoBind`、`call(id,req)`→`SLOT_UNBOUND` | 形状是"逐项相等匹配"，每个槽只取第一个匹配 provider；provider 注销会解除绑定（`services.mjs:947-967`） | 槽：`embed-text`(memory)、`dream-chat`(dream)、`compact-chat`(compaction)、`image-embed`(album,optional)；provider 只有 `model-manager` |
| **原文源** | `raw.register({id,search,list})`、`list/search` 顺序问各源 | **单源抛错被吞**，调用方分不清"没查到"和"来源坏了"（`services.mjs:65-84`）；历史上只解析 `user/assistant`，工具结果从不进原文层 → 人物记忆永远缺"从工具看到的人"（`OVERVIEW.md:132`） | `dsh-raw`（文件扫描，产品默认关）+ bridge 自己的 `dsh-session` 源（实时事件，产品默认开） |
| **上下文** | `provideHistory`、`history`、`onAssemble({name,order,apply})`、`assemble({maxChars=1200})`、`onCompact`、`compact` | `assemble` 预算 **1200 但 compaction 单条摘要就是 1200**——一条摘要吃满整轮，后面的策略整体被截掉（`ARCHITECTURE.md:138`）；`compact` 只调第一个注册者 | compaction（默认关，"纯加法不如 DSH 原生"）、bridge 的 pre-step |

### 2.3 14 个服务里，谁空转（对 myco 直接有用）

| 服务 | 有真实第二使用者？ | 判断依据 |
|---|---|---|
| `tools` `prompt` `delivery` `signals` `slots` `context` `raw` `model` | 是 | 多插件/桥都在用 |
| `surface` | 是（桥 + session） | 工具面过滤的唯一出口 |
| `focus` | **只 1 个**（time 进/出 lease），`canPierce` 无插件调用 | `ARCHITECTURE.md:84`；`HISTORY.md:96` 直接判"按'有没有第二个使用者'标准，focus/presets/delivery.push 都不该存在" |
| `presets` | **0 个**产品调用方，唯一预设 `default`，16 个 manifest 无一写 `presets` | `ARCHITECTURE.md:265-270`、`STATUS.md:60` |
| `vision` | 只 `bili` 一个（本地 8088 描述服务，产品已停用 caption） | `ARCHITECTURE.md:93` |
| `clock` | 只 `time` | `ARCHITECTURE.md:94` |
| `contracts` | 用了（compaction/purpose 两个 Feature Contract）+ 桥的 `markToolUnavailable` | `CONTRACTS.md:65-73`；`PURPOSE.md:19` |

> **这就是"HISTORY L2"**：拆得整齐 ≠ 更少。13→14 个服务的代价是每个新服务要改 5 处（`ARCHITECTURE.md:244-250`），而收益只在"有两个以上插件真的用"时才兑现。

### 2.4 失败时到底怎么表现（B4 的完整清单，【代码】核对）

| 位置 | 失败表现 | 调用方能知道吗 |
|---|---|---|
| `enable` 的 `apply` reject | 回滚已注册物 + `enabled=false`，**但这条路径不 `surface.notify()`** | 抛出，知道 |
| `disable` 单个注销器抛错 | 吞掉，其余继续，插件仍标停用 | 不知道 |
| `prompt.assemble` / `context.assemble` 单段 | 段消失，经 `core.service-error` 记一条日志 | 日志，不阻断 |
| `raw.search` 单源 | 少几行、`source` 字段缺失 | 分不清"没查到/源坏了" |
| `delivery` 第 5 次 nack | 条目丢弃、水位不推进，只有 `droppedCount()` | 必须主动查 |
| 槽未绑定 | `call` 抛 `SLOT_UNBOUND`；**启动时 `icl.slots-unmatched` 把未绑定槽打成 error 日志** | 知道（这是后期补的纪律，值得抄） |
| DSH 工具注册失败 | `contracts.markToolUnavailable` → 依赖它的 Feature Contract 立即失活 | 知道（契约层） |
| 插件没挂进 profile | 工具/人格全不出现，**什么都不报** | 不知道（B2 的根因） |

---

## 3. 已付学费的负面结论（myco 必须继承的"反面清单"）

### 3.1 五大瓶颈（`docs/OVERVIEW.md:96-141`），以及它们后来被怎么处理

| 瓶颈 | 当时的证据 | 后事（我核到的） |
|---|---|---|
| **B1 广度 > 深度** | 17 插件 / 32 工具 / 13 服务 / 7 个 dream job，端到端只有两次 5 分钟，其中一次作废 | 未根治：广度继续涨到 22 插件/68 工具，才有了 E2 120min、night-1 6h29m |
| **B2 接线在仓库外** | `icl-live` 插件/preset/profile 三处都在 `~/.dsh/`，仓库内 `adapters/*` 零调用方，`npm test` 全绿但什么都不会发生 | **部分缓解**：`packages/dsh-icl` 把接线搬回仓库 + `package.json` 的 `dsh.bundle.patch` 指向 preset（`package.json:23-29`） |
| **B3 文档负债** | 32 份 / 250KB，5 份自称过期，审计 22 处不符 + 4 处互相矛盾 | 收敛成 9 份+归档；但**数字仍靠手写**，所以我今天还能查出 8 处新的不符 |
| **B4 静默失败** | 装配期 `try/catch` 全空体；第 5 次重试直接丢；`.zstd` 解压失败返回空串 | 修法：`core.service-error`、`droppedCount`、`markToolUnavailable`、`icl.slots-unmatched`；**仍残留 60+ 处 `catch {}`**（`READINESS-MATRIX.md:140`） |
| **B5 单会话假设** | 第一轮 5 分钟测试就是两个会话共用一个工作区而作废；`claimSession` 只有 `icl-live` 调用 | 修法见 §3.2 |

### 3.1b 五大瓶颈对 myco MVP 的**直接约束**（一句话一条，可直接写进设计约束清单）

| 瓶颈 | 对 myco MVP 的直接约束 |
|---|---|
| B1 广度>深度 | MVP 冻结能力数量：先只做 1 条主线（多会话 + provider 管理 + 简单 loop）走通 E3/E4，**插件数不超过 6 个**；任何新插件必须先有"它属于哪条线"的答案 |
| B2 接线在仓库外 | 插件加载、preset、profile 配置**全部进仓库并在 CI 里跑一遍装配**；`npm test` 必须包含"装配出 N 个工具"的集成测试，否则全绿无意义 |
| B3 文档负债 | 工具数/服务数/测试数由脚本生成进文档（或断言），文档只写决策与索引；每份文档标注"最后核对 commit" |
| B4 静默失败 | 装配期禁止空 `catch`（CI 规则）；每个"被吞掉的失败"必须有计数出口或日志；启动自检未满足的依赖并 log error |
| B5 单会话 | 第一天就把会话 id 穿过 `delivery/prompt/context`；`time/focus/记忆` 的"全局单例"必须是**显式声明**而不是默认；两会话并发要有一条自动化测试 |

### 3.2 单会话：怎么被写进设计、怎么变成事故源、后来改成了什么

**写进设计的方式**（不是疏忽，是路线选择）：
- 数据面：单份 `<stateDir>/memory.db`、`time.json`、`focus.json`，**全程无文件锁**（`ARCHITECTURE.md:233-240`）；
- 进程面：`claimSession` 单值 + 第二个会话**同步 throw**（让 DSH 回滚会话，`host.mjs:350-356`、`plugin.mjs:64-74`）；
- 语义面：一个"她"= 一个焦点 + 一条时间线（`docs/QQ-GAP.md:134`："多 DSH 会话与 ICL 单人格/单焦点/单会话设计相反；三代教训就是'一人被切成 N 份'"）。

**事故源**：第一轮测试两个会话同跑 → 预写状态被启动时 `persist()` 覆盖（`STATUS.md:144`）；`workspace/archiveSession` 只 dispose agent 不发 `session/disposed` → 归档后新会话被 claim 永久拒绝（`STATUS.md:351` 根因记录）。

**后来的真实演化（重要，myco 可以直接用）**：单会话被改造成"**同一时刻一个会话，但可切换**"——
- `bridge.bindSession/unbindSession`：切换时重新 `claimSession`，并用 `delivery.clear({preserve: keepAcrossSessionBind})` 清队列，只保留 time 闹钟与 poke/@/admin/alarm/QQ 私聊群消息（`bridge.mjs:1050-1109`）；
- 无 agent 时**冷恢复**：走 `apiProxy.sessions.prompt` 而不是等用户点开（`bridge.mjs:881-884`、`presets/icl.patch.yml:17-19` 的 `autoResume:false` 注释）；
- 代价：memory/time/focus 仍是全局单例，`recall` 的 `sessionId` 只是配置项（`bootstrap.mjs:15`）——**多会话共享一份记忆库是"特性"还是"bug"，代码里没答案**。

**多会话真正要改什么**（我的判断，基于 [代码] 的全局单例清单）：
1. 每个服务需要 session 维度（现在只有 `prompt.turnText/assemble` 收 `sessionId` 参数但不做隔离）；
2. 投递队列按 session 分桶 + 落盘（现在一个全局 `queue`，切换时要"猜"哪些能丢）；
3. `time`/`focus` 必须决定：全局一个（一个她）还是每会话一个——dsh-icl 选了全局，所以它是"一个她多窗口"，不是"多 agent"；
4. 记忆命名空间加 session 前缀，或至少加 `conv` 列（`recall` 已有 `conv` 参数，但检索不按它过滤全库）。

### 3.3 其余已付学费（每条形如"他们踩过，myco 别踩"）

| 学费 | 事实 | 坐标 |
|---|---|---|
| **第一屏被宿主污染** | 静态 system 4908 字符里 2739（56%）是 DSH 的 coding-agent/Office/`present` 说明；首行 `You are an AI agent` | `REDTEAM-REVIEW.md:12` |
| 修法：purifySections | 用**同名空段 shadow** 覆盖宿主全局段；需 DSH 支持 scoped section，失败会退到 agent ctx 再注册一次 | `purify.mjs`、`plugin.mjs:96-101,167-172` |
| **上下文自耗** | 70% 上下文是重复 runtime-context（2.5 条/轮 vs 目标 ≤1），48 工具/2.9 万字符 schema 每请求都带 | `COMPLEXITY-AUDIT.md:5,76` |
| **"验收 PASS ≠ 体验成立"** | 链路 7/7 PASS，同一场 interview 里她说"我到现在没搞清这具身体带着什么来的" | `REDTEAM-REVIEW.md:16` |
| **语义与字面相反** | "暂停"后身体还在掉血；"收工"只清 `focus=="mc"`；"专注"挡不住每条带 `mention` 的私聊 | `REDTEAM-REVIEW.md:538-547` |
| **模型看起来笨，其实是环境逼的** | 57% 墙钟在工具调用（49 分钟是模型自发 `wait`）+ 17% 在等她"清醒节拍" | `COMPLEXITY-AUDIT.md:5` |
| **工具教在全局提示词里会腐烂** | 三次迁移后的契约：人格(静态)/仿真(静态)/工具教学(description+按需 skills)，且"不在 sim 里抄工具参数表" | `PROMPT-CONTRACT`（引用于 `CONFIG.mjs:69-72`）、`PURPOSE.md:26-32` |
| **不做的事也要写下来** | `HISTORY.md:39`：明确放弃 GUI/连接器/热安装/插件市场/情绪模型；`PURPOSE.md:34-39`：不做四驱动衰减、每回合 mood judge | 同左 |

### 3.4 产品化那段是怎么想的，最终判断是什么

三份文档是"方案 → 审计 → 执行"的三段，**都没上线时就把结论写死了**：

1. `PRODUCT-SURFACE.md`（11-06，**"方案，未应用"**）：产品面实测 68 工具/46,766 字符是"固定前缀成本"，目标 **45–50 工具 / 28–32k 字符**；推荐 A2+O = 去 mc-link、editor、memory_export/time_edit/time_mute → **45/31,662**；并给出三层分级（P0 生活必留 / P1 场景挂载 / P2 可裁）。
2. `PRODUCTIZATION-KICKOFF.md`（11-06，**"草案，未应用"**）：产品化不是"加插件"，而是**把隐式默认变成显式批准清单 + 首屏净化 + 沙箱边界**；列了 8 步桌面重启审批清单与逐项回滚；实施记录：A1 = 16 插件白名单 + 4 段净化 → **51/34,274**，`systemHasDynamicFacts=false`。
3. `READINESS-MATRIX.md`（11-06，蓝队）：65 条判据 → **56 达成 / 3 部分 / 2 未达成 / 4 判据有问题**，P0=0；并诚实声明"模型行为类目标只能标'判据有问题'"。

**最终产品形态的判断**（两句，都出自它自己）：
- "这不是一个半成品框架，而是一个**记忆与作息引擎**，外面裹着一层尚未被验证的接线，上面盖着一层已经过期的文档"（`OVERVIEW.md:145`）；
- "单用户本地 **beta+**……单会话、单进程、投递不落盘、库不迁移、紧急标签不可信——这些是'她一个人在本机生活'的前提，**不是产品前提**"（`QUALITY-CORE.md:84,93`）。
- 产品化真正的卡点不是代码："desktop 还没重启到 HEAD"+"关键能力的效果没被真实场景验证"（`QUALITY-CORE.md:93`）。

---

## 4. 对 myco MVP：抄 / 改 / 别抄

### 4.1 直接抄（每条一句理由）

| # | 抄什么 | 理由 |
|---|---|---|
| S1 | **注销器生命周期**：所有注册返回 `off()`，宿主逆序调；`apply` 返回值也登记 | 一个机制替代全部状态同步，是它 1543 行桥能不发散的根本原因 |
| S2 | **manifest 声明 `tools` + `surface = manifest ∩ enabled ∩ preset`** | 工具面可审计、可解释、"为什么模型看不到这个工具"有单一答案 |
| S3 | **manifest.json 形状**（id/version/entry/tools/slots/focus/requires） + `validateManifest` 拒绝加载 | 清单不合法宁可起不来，也不要半残地跑（`contracts.mjs:4`） |
| S4 | **产物预算测试**（工具数 + JSON 字符数断言写进 CI） | 它是唯一挡住"工具面通胀"的东西；68→45 的争论靠数字结束 |
| S5 | **槽位（slot provider）做 model provider 管理** | 插件只声明"要 chat/embed + 形状"，provider 由 model-manager 提供——正是 myco"模型 provider 管理"的形状 |
| S6 | **Feature Contract**（依赖不齐整段不出现，不发布半截） | 解决"提示词里有、工具清单里没有"，比在每个工具 description 里写条件便宜 |
| S7 | **失败必须吵**：启动自检未绑定槽、`droppedCount`、`markToolUnavailable`、`catch {}` 必须带注释 | B4 是一场真事故（记忆变薄不报错，只表现为"想不起来"） |
| S8 | **静态/动态两层提示词**（缓存友好）+ 快照节流（≤1 条/轮） | E2 实测 70% 上下文是重复快照；这层不做，长会话必崩 |
| S9 | **证据分级 + 端到端先行 + 红队"文档是被告"** | 三代的病都是"每块都做出来，没一条线走通" |
| S10 | **显式插件白名单写进预设**，不要 `DEFAULT_PLUGINS` 隐式默认 | 隐式默认 = 新插件被动进产品 = 一次未批准的行为变化 |

### 4.2 改后用（保留思路，换实现）

| # | 改什么 | 怎么改 | 理由 |
|---|---|---|---|
| M1 | **单会话 claim → 会话键控** | 服务层加 `sessionId` 维度（先只给 `delivery/prompt/context` 加），`time`/`focus` 保持全局单例并显式声明"一个她" | myco work 要"多会话并发"，dsh-icl 的 claim 是硬拒绝，只能靠桥重绑 |
| M2 | **14 服务 → 先做 5 个**（tools/prompt/delivery/slots/context） | focus/presets/vision/clock/contracts 等出现第二个使用者再加 | 它自己的 L2 标准："判断标准是有没有第二个使用者" |
| M3 | **order 魔法数 → 显式 `static`/`dynamic` 标记** | 段声明 `{tier:'static'|'dynamic'}`，桥按 tier 分流；不用 800 这个数 | 800 是缓存实现细节泄漏进插件契约，新人必错 |
| M4 | **投递队列落盘 + 启动重放** | `.state/delivery.jsonl` 追加 + 启动重放未 ack | 它自己把 DL-7 判"未达成"，而"agent 陪伴"最不能丢的就是"她醒来该看到的话" |
| M5 | **原文源从"扫描会话文件"改成"实时事件订阅"** | 它已经这么干了（`bridge` 的 `dsh-session` 源），但旧 `dsh-raw` 仍在仓库里制造两套真相 | 两套真相 = 两倍缺陷面 |
| M6 | **插件配置从 `configFor(id)` switch 改成插件自声明 defaults** | 现在每加一个插件要改 `bootstrap.mjs` switch（`configFor`） | 22 个 case 的 switch 是"一切皆插件"漏掉的唯一硬编码点 |
| M7 | **首屏净化机制保留，但做成"提示词 profile"而不是 shadow 段** | shadow 依赖"宿主允许同名覆盖"，脆；改成宿主 profile/模板 | `purifySections` 已踩过"DSH 拒绝同名覆盖，报 already registered"（`plugin.mjs:165-166`） |

### 4.3 别抄（明确的反模式）

| # | 别抄 | 一句理由 |
|---|---|---|
| X1 | **六抽象/13 服务先行，再找使用者** | 一半服务空转，每个新服务要改 5 处；概念重要 ≠ 该有服务 |
| X2 | **manifest 里写了没人读的字段**（`entry`、`presets`、`preset`、`focus`） | 22 个 manifest 的 `focus` 全是 `{}`，`presets` 无人读 → 契约幻觉比没契约更贵 |
| X3 | **把 DSH 工程脚手架继承进产品首屏** | 56% 系统提示词在教她"你是文件代理"，这是它最刺眼的 P0 |
| X4 | **自研 agent loop / 协议层** | `dsh-client.mjs` 1008 行零测试、零调用方，"最脆弱也最不可替代"；loop 本来就该宿主给 |
| X5 | **大而全工具面默认全开** | 68 工具/46k 字符 → 被迫做一次痛苦的裁剪；MVP 应从 20–30 工具起步 |
| X6 | **"她一个人在本机"当作产品假设** | 单会话/单进程/无锁/标签可伪造，这四条在 MVP 里就要显式写"临时约束 + 替换点" |
| X7 | **复述式文档 / 手写数字** | 22 处不符 + 今天又查出 8 处；数字必须脚本产出 |

### 4.4 插件宿主选型：自研 vs 复用 cordis vs 直接用 DSH 插件 API

| 选项 | 得到什么 | 代价 | 判断 |
|---|---|---|---|
| **A. 自研"契约层"（抄 dsh-icl 形状，≈400–600 行）** | 8 个注册面 + 注销器 + manifest + surface 过滤，全部可测、零依赖 | 要自己维护；但 dsh-icl 已把坑踩完（清单/回滚/预算/静态动态） | ✅ **推荐**：作为 myco 的 `core/`，只保留 5 服务 |
| **B. 复用 cordis（DSH 自己的 DI/插件容器）** | 生命周期、依赖注入、`inject`、作用域现成 | cordis 只管"组件装配"，不管"模型能看见什么"；dsh-icl 的 14 个服务在 cordis 之上又长了一套 registry → **双轨**（模型名/地址在自家 registry，生命周期在 cordis），这是它复杂度高的一个来源 | ⚠️ 只用它做宿主底座，不要在它上面再发明能力面 |
| **C. 直接用 DSH 插件 API（`ctx.tools.register` / `systemPrompt.section` / `agent.followup`）** | loop/会话/权限/工具协议全白送 | 绑定 DSH 版本（`steer`/`apiProxy`/`attachments` 都要 `inject`，0.2.0-rc.2 还回退 text-only）；产品形态被 DSH 首屏污染 | ✅ **MVP 首选**：myco 要的"简单 agent loop"DSH 有，重写没有收益 |

**合起来的建议**：`myco = DSH 插件（preset 作用域）+ 自研 5 服务契约层（~500 行，抄 S1–S3/S5/S7）+ 显式白名单 preset`。也就是 dsh-icl 的**第二层和第三层**，扔掉它的第一层（六抽象先行）和第四层（bridge 里 1543 行的宿主编译）。

---

## 5. 未解问题（需 Lead/用户拍板，附我的倾向）

| # | 问题 | 我的倾向 | 理由 |
|---|---|---|---|
| Q1 | **myco 是"寄生 DSH 的插件"还是"独立 app"？** | 寄生。MVP 阶段 loop/session/工具协议/权限全白送，省下的正好投到"多会话+provider 管理" | dsh-icl 三代的最大浪费就是自己造协议层（`dsh-client` 1008 行零测试）；但必须同时做首屏净化，否则产品第一行是"AI agent" |
| Q2 | **多会话隔离粒度：一库多会话 vs 一会话一库？** | 一库 + 会话列/命名空间前缀；`time`/`focus` 保持全局 | 一会话一库会让"她记得昨天"变成"每个窗口各记一份"，与陪伴定位冲突；work 与 chat 可各用一份 stateDir |
| Q3 | **"一切皆插件"的边界在哪：会话/循环/存储算不算插件？** | 插件只挂 8 个注册面（工具/提示词段/上下文策略/投递源/槽位/原文源/服务/命令）；**会话模型、loop、记忆库 schema 不进插件** | dsh-icl 的"插件"只到能力面，核心仍是硬编码 —— 这个边界是对的，只是它没写下来 |
| Q4 | **产品面预算要不要进 CI 并定死数字？** | 要：MVP ≤30 工具 / ≤24k 字符，断言写进测试；每次加工具必须删一个或过审 | 它的 68→45 是事后裁剪，代价是三轮文档与一次未应用方案；先定预算比先做工具便宜 |
| Q5 | **复用 dsh-icl 的代码还是只抄模式？** | 只抄模式 + 测试清单；`bridge.mjs` 不复制 | 1543 行里大量 DSH 0.2.x 内部 API（`steer`/`apiProxy`/`attachments inject`/`snapshotEvents`）与降级分支；DSH 一升级就整体漂移（`HISTORY.md:111-115` L5） |
| Q6 | **"一个她"是 myco chat 的定位，那 myco work 呢？** | 两个 preset、两套 stateDir、一份共享记忆只读层；work 不写人格记忆 | dsh-icl 证明了"单人格/单焦点"与"多会话"在同一份 state 上不可兼得；分库是最便宜的分界 |

---

*（本报告共约 300 行；所有行号基于 2026-11-25 的磁盘内容，若上游继续提交请以代码为准。）*

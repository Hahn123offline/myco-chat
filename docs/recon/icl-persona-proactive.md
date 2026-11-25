# dsh-icl 人格/主动性/实时感知考古（供 myco chat 模式设计）

> 来源：`E:\11-15\tools\external\dsh-icl`（只读静态阅读，未跑构建/测试/模型）。
> 证据口径沿用该仓库自己的五级表（`docs/OVERVIEW.md:56-63`）：
> **E0** 只有文档说｜**E1** 单测（假宿主/临时 DB）｜**E2** 假 DSH 集成（100–120min lab 跑）｜
> **E3** 真实 DSH 会话（真模型）｜**E4** 长时间/跨天。
> 坐标写法：`文件:行`，文档用 `docs/X.md:§`。**真实行为记录与文档设想严格分开标注。**

---

## 0. 一页结论（≤15 行）

1. 这套系统**不是人格系统**：人格 = 一段 3,924 字符的静态 system 段（`prompts/persona.md`）+ 一张版本/回滚表（`packages/plugins/persona/src/index.mjs:1-2`）。没有状态机、没有关系值、没有情绪值落地。
2. "活人感"的真实来源不是机制，是**可判定的措辞纪律**：短句/拟声/禁客服腔/"不假装看过"/"有真实新发现才分享"。这些可逐条对照检查，所以稳定生效。
3. 效果证据薄到危险：唯一 E3 效果证据是**两轮各 5 分钟**（`docs/STATUS.md:131-144`），内容是"她自己写了 10 条笔记，细节可读"；其余全是设计或单测。
4. 主动性已经过一次**真实纠错**：心跳"到点必发状态播报"导致 **25 次心跳中 17 次（68%）零工具空转**（`docs/ATTENTION-RESEARCH.md:56-58`），于是改成"有 pending 才叫醒 + 决策简报 + 无事退避 + no-op 合法"。
5. 误打扰控制的核心不是概率，是**结构性闸门**：单一投递出口、9 个 reason 收敛成 5 个 suppress、**只降级不丢弃**、按 activity 的未读水位、显式 ack。
6. 成本硬约束：一次真唤醒 = 一个完整 turn = 新回合 `hit=0`、prefill 7k–24k tok、TTFT 10–24s（`docs/CACHE-MODEL.md:15-20`）。**空 turn 也要付这个钱**——这才是条件唤醒的真正收益点。
7. 实时感知真相：没有摄像头；"看屏幕"只有 MC 结构化快照（毫秒级、几 KB）与按需截图（**7.1s/张、Edge 1.3GB**，`docs/MC-EYES.md:66-71`）；视频是本地文件抽帧（0.8s/组，ASR 冷 9s 热 0.25s）。**"连续 1fps 看"被明确否决。**
8. 多会话是这套架构的天敌：`host.claimSession` 单会话是架构决定，三代都栽在"一人被切成 N 份"（`docs/HISTORY.md:85-89`）。
9. 对 myco MVP：**静态人格 + 事实段 + 事件驱动为主/定时器兜底的条件唤醒 + 三档水位 + 一次决策简报 + no-op 合法**，就能撑住陪伴感；感知全部做成"按需、同步、带成本声明"的插件接口。

---

## 1. 事实清单（带出处）

### 1.1 真实行为记录（E2–E4，可引为"跑过"）

| # | 事实 | 证据级 | 坐标 |
|---|---|---|---|
| F1 | 两轮各约 5 分钟真实会话（preset `icl-live`、模型 `coder390-27b`）：定时器 `arrange in 1m, duration 3m` 到点叫醒 ✅；睡觉触发做梦 ✅ 但产物仅 1 条词；`dsh-raw` 读空修好后重放 wash 11 行 / events 1 块 / **people 0** / lexicon 16 条（多为噪声） | **E3** | `docs/STATUS.md:131-144`、`docs/OVERVIEW.md:70-74` |
| F2 | 同一批测试里**一轮作废**：两个会话共用一份 `time.json`，预写状态被启动 `persist()` 覆盖 | E3（失败记录） | `docs/STATUS.md:144`、`docs/OVERVIEW.md:137-141` |
| F3 | 唯一"效果"证据：她自己写了 10 条笔记，细节可读 | **E3，n=1** | `docs/STATUS.md:143` |
| F4 | night-1 夜间长测 ≈6h25m：02:09 入睡→06:39 醒→06:49 dawn 全部按时；睡眠期 **17 条**外部消息全部进积压、醒来一次批量释放；`stats` 全 0；队列峰值 17 << 500 | **E4 口径（跨夜）** | `docs/TIME-NIGHT-REPORT.md:6-36,58-70` |
| F5 | 2026-11-14 18:18 首次真实注意力 tick：世界状态报"2 个群 18 条"，她查了其中一个群（16 条）判断刷屏、决定不回，未进 focus | E3，**n=1 且已被作者自己标 caveat**（受聊天里"刷屏不用回"影响） | `docs/ATTENTION-RESEARCH.md:90-109` |
| F6 | 线上 25 次心跳：**17 次零工具**（68%）、8 次有工具、5 次真发 QQ；`focus_enter` 真实对话仅 2 次且都是 loop 测试；`focus.json` leases 空 | **E3（live state）** | `docs/ATTENTION-RESEARCH.md:48,56-62` |
| F7 | compactWake A/B（lab，同模型同场景）：wakeClass 字符 464→1408（**+203%**）、总投递 8573→9605（+12%）→ **结论：不启用** | E2 | `docs/PROMPT-WAKE-RESULT.md:9-17,76-95` |
| F8 | E2 120min/53 轮共投递 **130,253 字符**，其中 runtime-context 快照 **90,977 字符/134 条**（≈70%）；time awake-tick 43 条/5,208 字符（≈107 字符/条） | E2 | `docs/PROMPT-WAKE-DESIGN.md:13-27` |
| F9 | 缓存实测：每**新回合** `hit=0`、prefill 7k–24k tok、TTFT 10–24s；同一回合内的多步工具循环 `hit≈99%`、prefill 20–300 tok、TTFT 0.3–0.5s；布局修正后 6 轮 prefill 59,547→9,421（−84%）、hit 41.5%→91.2% | E3（真实模型路由） | `docs/CACHE-MODEL.md:15-20,318-320` |
| F10 | MC 眼睛 PoC：prismarine-viewer + Edge headless CDP，**7.1s / 张**、1254×627、352KB PNG；viewer RSS 108MB；Edge 9 进程私有内存 **1,313MB**（软件渲染 SwiftShader，不占 GPU 锁） | E2（离线演示世界，未连真服） | `docs/MC-EYES.md:11-16,61-73` |
| F11 | 视频眼睛实测（本地 8s 样例）：`video_open` 842ms（ASR off）/5,472ms（首次 ASR）、`video_storyboard` 641ms、`video_zoom` 698ms、`video_frames` 226ms；`video_locate` 冷 9,013ms / 热 255ms | E2（真 CLI + 真 ffmpeg） | `docs/VIDEO-EYES.md:176-187,288-300` |
| F12 | 8088 whisper ASR 打通但**返回无 `segments`** → 4 个 cell 拿到同一段台词，**没有秒级时间码精度** | E2 | `docs/VIDEO-EYES.md:302-308` |

### 1.2 代码/配置构成（E0–E1，可引为"存在"但不等于有效）

| # | 事实 | 证据级 | 坐标 |
|---|---|---|---|
| C1 | 提示词三层 + 两类不属三层的：人格 order 20 / 仿真 order 25 / 事实状态 order ≥800 / 每轮上下文走 `pre-step` 尾部 | E0（契约文档）/ E1（有测试） | `docs/PROMPT-CONTRACT.md:7-35` |
| C2 | 人格插件只做"一张卡 + 版本/回滚"，**不做任何主动行为**；人格是配置不是回忆（`recallable:false`） | E1 | `packages/plugins/persona/src/index.mjs:1-2,19,87` |
| C3 | 事实段硬规则：只陈述不教导、每段 ≤1–2 行、无内容整段消失、动态段 order ≥800、段首不许放随机量 | E0 | `docs/PROMPT-CONTRACT.md:31-35` |
| C4 | time 段 order 880 只写"日期+周几+时段"，**不写分钟**（分钟级抖动会每 60s 作废前缀） | E0 | `docs/TIME.md:187-190` |
| C5 | world-state 是深度 2 只读总线；`items()` 空时**默认出一行"无"**（"世界只有时间"是 bug，不是真相） | E1 | `packages/plugins/world-state/src/index.mjs:1-14,55-58` |
| C6 | 工具面：默认 68 工具/46,766 字符；A1 方案 51 工具/34,326 字符；ICL system 段 **2,899 字符**（静态 2,824 + facts 73） | E1/E2 | `docs/PRODUCT-SURFACE.md:5,11-13`、`docs/A1-LIVE-VERIFY.md:48-49` |
| C7 | 人格 3,924 字符、仿真 1,461 字符（移植前人格 2,057、旧 QQ 卡 26,055） | E0 | `docs/ATTENTION-RESEARCH.md:490`、`docs/PROMPT-OLD-COMPARE.md:13-15`、`docs/PERSONA-PORT.md:12-16` |
| C8 | 记忆清晰度 5 档已落地并在召回时改措辞（`vivid/clear` 无标签，`fuzzy`→"有点模糊"，`trace`→"只剩印象"，`forgotten`→"几乎想不起来了"） | E1 | `packages/plugins/memory/src/index.mjs:82-89`、`recall/src/index.mjs:1035` |
| C9 | 单会话锁：`claimSession` 第二个不同 id 直接抛；但"仓库内没有任何东西阻止两个实例同跑" | E1 + E3（失败记录） | `docs/ARCHITECTURE.md:228`、`docs/OVERVIEW.md:137-141` |

### 1.3 纯设想（E0，不可当依据）

七情/心情每轮 judge、关系值、驱动值（purpose）、每回合记忆自动提取、日记调度、"六维情绪数值表"——分别在 `docs/SOUL-RESEARCH.md:29-40,100-110`、`大肥鱼-QQ人格卡.md:97` 有描述，**新 persona 已把它们降级为"倾向"**（`docs/PERSONA-PORT.md:29`）。

---

## 2. 人格与提示词工程

### 2.1 人格到底是怎么落地的（按实际占比）

| 载体 | 实际占比 | 证据 | 判定 |
|---|---|---|---|
| **静态 system 人格段** | 约 85% 的"可见人格" | 3,924 字符，order 20，逐轮完全一致（`docs/PROMPT-CONTRACT.md:57`）；改一次打掉一次缓存（`docs/PERSONA-PORT.md:58-59`） | **被证明有效**（F3 唯一效果证据即来自此） |
| **静态仿真段** | 约 15% | 1,461 字符，order 25，写"在这个环境里怎么生活"（`prompts/sim.md:1-3`） | 有效，但会与 persona 重复（见 §5） |
| **动态 facts 段** | 每轮 ≤1–2 行 | time 880 / focus 875 / world 890；只陈述、不教导、无内容消失 | 有效但**极易膨胀**（F8：facts 占 E2 投递的 70%） |
| **状态机** | **0** | 人格插件无状态机；phase 状态机只属于 time（awake/sleeping/paused） | 不存在。**"动态人格"是误读**（`docs/SOUL-RESEARCH.md:87-94`） |
| **关系值** | **0（代码）** | 旧卡有"社交精力 0—100"（`大肥鱼-QQ人格卡.md:97`），新 persona 无 | 没做；`PERSONA-PORT.md:29` 明确"改写成倾向" |
| **记忆里的自我叙事** | 有，但是**批量**的 | dream 7 个 job、995 行（`docs/OVERVIEW.md:69`）；清晰度 5 档（C8） | **最便宜的活人感**：衰减措辞让"想不起来"变成可表达的诚实 |

### 2.2 提示词工程的可操作结论（不是功能列表，是"什么写法有效"）

1. **一句话一条纪律，可判定**。旧 QQ 唤醒提示词的胜利在于每行都是可检查的判断，例如
   `你不是客服：不用有求必应，可以装傻、已读乱回、拒绝推荐`（`docs/OLD-QWENSTUFF-QQ-UX.md:91-92`）。
   反面：`请自然一点`这类形容词无效。
2. **"为什么现在醒"是体验的一半**。每个 wake 原因一句生活化理由 + "**这不是对方刚发的新消息**"
   （`docs/OLD-QWENSTUFF-QQ-UX.md:97,202-204`、`docs/QQ.md:578`）。这句是防止模型把系统提醒当用户发言的结构性护栏。
3. **把"还要等多久"算好再告诉她**（旧实现 `remainingQuietMs`，`docs/OLD-QWENSTUFF-QQ-UX.md:93,205`）——
   不让模型自己估算时间，是零成本的第一感受来源。
4. **人格层与仿真层必须解耦**。旧项目把"反 AI 味/主体性/分条节奏/主动开话题"写在仿真层，与角色卡多头重复约 1,600 字符
   （`docs/PROMPT-OLD-COMPARE.md:53-60`）；当前分层已修正，但 sim 仍留有偏操作手册的内容（`docs/PROMPT-CONTRACT.md:97`）。
5. **不要在每轮唤醒里重发 orientation**——但要小心反噬：见 F7，compact 版 6 行（131 字符）比原来的短消息（33 字符）**更长**，
   因为 time 的 wake 本来就是短消息。**"压缩"必须带 guard：compact 比 full 长就回退 full**（`docs/PROMPT-WAKE-RESULT.md:88-94`）。
6. **"见过才说见过"要写进底线**，不是写进工具描述：`prompts/persona.md:96-99`（"只说自己真的经历过的"）。
   这条同时是感知插件的验收判据。

### 2.3 "活人感"的可操作经验（QQ 那条线，逐条标证据）

| 手法 | 具体数值/写法 | 真实证据？ |
|---|---|---|
| 消息长度 | 一条通常 ≤30 字，常见 1–10 字；普通接话 2–4 条，聊开 4–8 条 | E0（`prompts/persona.md:51,61`）；旧卡同义重复（`大肥鱼-QQ人格卡.md:62`） |
| 分条节奏（出站） | burst ≤8 条、间隔 1–3s、**20% 概率长停 5–10s**、打字速度 1.4 字/s、每分钟 8 条/每小时 60 条 | **E1 纯函数 + 有实现**（`packages/plugins/qq/src/humanize.mjs:10-29,166-213`）；无数值 A/B |
| 语气档位 | thoughtful 1.7× / normal 1.0 / quick 0.6 / blast 0.25 间隔系数 | E1（`humanize.mjs:32-37`） |
| 等待/沉默 | 首答前短静默 ≈10s；"话说一半"等 60–120s、静默 10–15s；三条以上集中来信按 ≈12s 合并；回复后等 ≈30s（最长 10 分钟） | E0（旧卡 `大肥鱼-QQ人格卡.md:195-198`）；**没有 A/B**，但这是全卡里最具体的体感参数 |
| 沉睡前观察 | 默认 5 分钟：除非对方明说"不聊了/晚安/拜拜"，先做一次观察 | E0（旧卡 `:202`）；ICL 侧与 time 睡眠语义打架，被建议改成"睡前提案"（`docs/OLD-QWENSTUFF-QQ-UX.md:216-217`） |
| 已读不回 | 必须显式 ack；水位分 delivered / model-seen / acknowledged 三档；`acceptance grants visibility, never acknowledgement` | **E3 教训**：ICL 只有两档时 `qq_mark_read` 能跨过未交付消息 → **消息静默消失**（`docs/OLD-QWENSTUFF-QQ-UX.md:168`）；已修为 seen 水位（`docs/ATTENTION-RESEARCH.md:450`） |
| 犯错/错字 | 允许自然小错、断句、"我刚想说啥来着"；**明确禁止为像人而故意编错事实或制造错字** | E0（`prompts/persona.md:57`） |
| 延迟 | 心跳频率 f 由模型设，后台映射成随机间隔（f=0.2 → 50–100 分钟） | E1（公式见 `docs/TIME-HEARTBEAT.md:11-19`）+ E3（线上 f=0.2） |
| 口头禅 | 拟声词清单（嘿嘿/嗷/咕噜/唔姆/呼——），一句最多一个叠词、一条最多一个括号动作 | E0（`prompts/persona.md:53-56`） |
| 主动次数上限 | 同一件事主动发起或追问**最多两次**；没人接就放下 | E0（旧卡 `:190`） |

**判定**：长度/节奏/延迟有代码或数值；"破折号""口头禅覆盖率"这类要求**没有任何真实语料统计支撑**，是人格卡作者的经验描述。
仓库里唯一接近语料证据的是 170 条真实 QQ 聊天记录的冻结题库（`docs/HISTORY.md:57`），但未见用它统计过这些指标。

---

## 3. 主动性与投递/节奏

### 3.1 触发模型（三层唤醒源 + 一个事件源）

```
事件源：signals.offer({to:'model'})        ← QQ/MC/外部连接器，只产信号不投递
   │
   ├─ 时间源：schedule wake(单发/每天/每周/倒计时) / goal 续推(turn-end，最小间隔 60s)
   ├─ 兜底源：heartbeat（模型只设"活跃度" f，后台映射随机间隔）
   └─ 规划源：qq wake planner（replyCheck 30s / nudge 10min / proactive 默认关）
   │
   ▼ time gate（时间维度）→ focus.admit（空间维度）→ delivery → agent.steer
```

- **心跳公式**：`interval = uniform(10m,20m) / f`，`f ∈ [0.01,1]`，默认 0.2，**不能关到 0**（兜底永远存在，只能拉长）
  （`docs/TIME-HEARTBEAT.md:11-19`、`packages/plugins/time/src/index.mjs:679-697`）。
- **只在没有新回合时计时**：`turn-end` 后开始，`turn-start` 取消并重排；有回合在跑绝不触发（`time/index.mjs:1174,1213`）。
- **谁能暂停心跳**：active `span`（含睡眠）、active `goal`、`time_pause` 暂停；`time_mute` **不**暂停（心跳是她自己的活动，不是外部打扰）
  （`docs/TIME-HEARTBEAT.md:28-39`）。
- **2026-11-15 条件化**：有 pending（focus 积压 / note open / time 队列 / QQ 未读）才叫醒；无 pending 只记 `heartbeat-skip-empty`
  并把间隔 ×1.5（封顶 12×）；外部活动立刻回基线；`longSilenceSec:0` 关闭"长静默 turn"，无事只按 `idleCheckMaxSec`（默认 1h）检查、
  **不产生 turn、不调模型**（`docs/TIME-HEARTBEAT.md:89-96`、`time/index.mjs:1191-1199,1337-1349`）。
- **决策简报**（取代状态播报，`time/index.mjs:1266-1290`）五段：这次是你自己醒的 / 你在（focus 或"自己的时间"）/ 等你（按 activity
  最多 3 条，含 +N、最早多少分钟前、最近一句）/ 可以做什么（含"也可以什么都不做——不回也不失礼"）/ focus 用法 + "只接在等的事，没由头不主动"。

### 3.2 投递裁决与水位

- **两个裁决点 → 一个纯函数**（设计，主体未实施）：输出 `{action: deliver|queue|drop, channel: pierce|ambient, suppressReason,
  priority, wake, holdMs, queueKey, explain}`；**drop 只留给去重/过期**，其余一律降级（`docs/DELIVERY-POLICY-DESIGN.md:14-22,159-185`）。
- **9 个 reason → 5 个 suppress**：`focus-sacred / focus-pierce / paused / sleeping / muted`；另 4 个（`no-focus/same-focus/privileged/always`
  ）是**放行**原因，被混进 reason 是历史债（`docs/DELIVERY-POLICY-DESIGN.md:70-95`）。
- **precedence 目前靠代码顺序**：`mute > sleeping > paused > focus`，没有一处可读规则（同文件 `:112-115`）。
- **行为矩阵**（同文件 `:99-106`）：
  | 状态 \ 消息 | 普通 | mention/@ | alarm/safety | 同 focus | admin |
  |---|---|---|---|---|---|
  | 无焦点 | 投递 | 投递 | 投递 | 投递 | 投递 |
  | 焦点可打断 | 进 backlog | 投递 | 投递 | 投递 | 投递 |
  | 睡眠 | 进 time 队列 | 进队列 | **进队列、最高优先级、不叫醒、醒来简报第一条** | 进队列 | 默认 admin 直投 |
  | 暂停 | 进队列 | 同左 | **穿透并叫醒** | 同左 | 同左 |
  | 静音 | 进队列 | 进队列 | **默认也进队列**（`emergencyQuota` 0，显式 opt-in 才放行） | 进队列 | `lock.allow` 命中才放行 |
- **积压有界**：`maxQueue=500`，溢出**优先丢最旧的 ambient**（alarm/mention/private/direct/admin 受保护），丢也计数可见；
  释放时"前 20 条 + 来源计数、≤3000 字、危险条目前置 `⚠ `"；**发送失败不清队列**（`docs/TIME.md:142-148`）。
- **静音有硬上限**：默认 30 分钟、最长 8h，到点自动解除并释放有界积压，不会把系统永久卡死（`docs/TIME.md:151-160`）。
- **水位三档**是"不丢消息"的核心：delivered（投给模型）/ model-seen（看过但没回）/ acknowledged（她确认处理）；
  `qq_mark_read` 只能推到"已交付高水位"（`docs/OLD-QWENSTUFF-QQ-UX.md:10-15,187-194`）。ICL 现在实现的是 seen 口径（`docs/ATTENTION-RESEARCH.md:450`）。

### 3.3 误打扰是怎么被控制的（按有效性排序）

1. **没有可处理的事就不叫醒**——直接砍掉 68% 的空 turn（F6）。这是唯一有 live 数字的控制手段。
2. **单一投递出口 + 只降级不丢弃**——被拦的消息一定在某个队列里，且有 reason 可查。
3. **按 activity 的水位 + 显式 ack**——否则同一批消息每次心跳都重报，她会"学会忽略"（habituation），
   或为了清红点敷衍回复，正好是最讨厌的"事事必回"（`docs/ATTENTION-RESEARCH.md:328-342`）。
4. **预算**：`maxWakePerHour=12`、nudge 6/小时、nudge 冷却 10 分钟（`packages/plugins/qq/src/wake.mjs:12-26`）；
   **缺天预算**（`docs/ATTENTION-RESEARCH.md:140`）。
5. **概率只用于"多个等价时机里挑一个"，不能当"要不要说话"的主判据**（`docs/ATTENTION-RESEARCH.md:140-141`）。
6. **自产主动性永不 pierce**——不能穿透自己设的勿扰（同文件 `:206`）。
7. **措辞护栏**："这不是对方刚发的新消息"（`docs/QQ.md:578`）。
8. **no-op 是一等结果**：简报里明写"也可以什么都不做——不回也不失礼"；旧 ICL 的对应文案更完整：
   "什么都不想做也可以——那就继续沉默，没人会因此觉得你失礼"（`docs/ATTENTION-RESEARCH.md:164`）。

### 3.4 成本（每轮 token / 调用）——能算的都算出来

| 项 | 数字 | 出处 |
|---|---|---|
| 一次真唤醒（新回合） | `hit=0`，prefill **7k–24k tok**，TTFT **10–24s** | `docs/CACHE-MODEL.md:17` |
| 同回合内多步工具循环 | `hit≈99%`，prefill 20–300 tok，TTFT 0.3–0.5s | 同上 `:18` |
| 系统提示词任意一字节变化 | 整条 prompt 重新 prefill（`hit=0`）；实测"尾部时钟"布局 0% hit / 8,008 tok / 5.0–5.2s | 同上 `:15,200-213` |
| 每轮投递文本 | E2 实测 130,253 字符 / 53 轮 ≈ **2.4k 字符/轮**，其中 facts 快照 90,977（≈70%）、wake-class 仅 5,208 | `docs/PROMPT-WAKE-DESIGN.md:13-27` |
| 一条 facts 快照 | ≈458–465 字符（`form:snapshot`），每轮 ≤1 条 | `docs/CACHE-MODEL.md:283` |
| 静态"人格预算" | 人格 3,924 + 仿真 1,461 ≈ **5.4k 字符**；ICL system 段合计 2,899 字符（产品 A1 实测） | C6/C7 |
| 工具 schema | 68 工具/46,766 字符（A1: 51/34,326）；**计入 prompt_tokens** | `docs/PRODUCT-SURFACE.md:5`、`docs/CACHE-MODEL.md:455` |
| QQ 出站 | 一次回复最多 8 气泡、每分钟 8/每小时 60；1.4 字/s 打字时长模拟 | `humanize.mjs:10-29` |
| 心跳空转的机会成本 | 每次空心跳 = 一个完整 turn ≈ 7k–24k tok prefill；68% 空转 → 条件唤醒的收益就是把这部分变成 0 | 推导自 F6+F9 |

> **结论**：在这套架构里，"人格/主动性"的成本不在人格文本，而在**每个被叫醒的回合要重付整个上下文前缀**。
> 所以主动性的第一原则不是"怎么说话"，是"**值不值得起一个 turn**"。

---

## 4. 实时感知（屏幕/相机/视频）的可达性

### 4.1 三条线各自到底通到哪

| 线 | 形态 | 通了吗 | 延迟/成本 | 关键限制 |
|---|---|---|---|---|
| **MC 结构化快照**（"语义眼"） | `GET /v1/eyes/snapshot?radius=8` → 位置/血/饱食/维度/时间/手持/背包/脚下俯视图/附近方块/实体/威胁/任务/安全 | ✅ 实现，毫秒级 | 几 KB、无浏览器、无 GPU | 没有"画面感"；远处/大范围要分块 |
| **MC 像素眼**（截图） | prismarine-viewer + Edge headless CDP `Page.captureScreenshot` | ✅ PoC（离线世界 + 真 Edge） | **7.1s/张**；Edge 9 进程 **1.3GB**；不占 GPU 模型锁（SwiftShader 软件渲染占 CPU） | 无 HUD/背包 UI/聊天/皮肤/名字；默认第三人称；同一时刻只允许 1 张、最小间隔 15s、`waitMs` 3–10s、超限 429 |
| **视频**（本地文件） | `video_probe/open/storyboard/zoom/locate/frames`，只读本地绝对路径，**拒绝 http(s)** | ✅ 已落地（产品默认装配生效，待用户批准工具面） | `video_open` 0.84s / 首 ASR 5.5s；`storyboard` 0.64s；`frames` 0.23s；`locate` 冷 9.0s / 热 0.26s | 图走 attachment 通道（单图 20MB/单批 20 张/200MB）；8GiB 上限；单次 CLI 15 分钟超时；ASR 无 segments → 无秒级定位 |
| **摄像头** | — | ❌ **不存在** | — | 全仓无 camera/摄像采集；`vision` 服务 44 行、**零测试**（`docs/STATUS.md:125`） |
| **B 站** | 只读（搜索/播放页/字幕/评论/热门）；写操作明确不做 | ✅ 只读实测 11/11 | ≈0.53 req/s（`minIntervalMs=1200`、重试 2） | 风控 `-412`/`-509`；账号写操作默认激进（`autoComment:true`）是死穴；cookies 到期 2026-12-01 |

### 4.2 明确被否决的方案（重要）

- **不要常驻 1fps 截图**：Edge 固定开销 1.3GB + 软件渲染 CPU 争用，"会和 lab/E2 抢资源"；1fps 连续 10 分钟 ≈200MB。
  结论是"低频（1 帧/2–5s）+ 明确触发窗口，或等机器空闲"（`docs/MC-EYES.md:16,151`）。
- **不要用 Edge CLI 的 `--screenshot`**：实测会截在 socket 区块到达之前（图里只有天空）；必须 CDP 导航 + 等真实渲染再截图
  （`docs/MC-EYES.md:54`）。
- **不要 `require('prismarine-viewer')`**：其 index 连带加载需要 `canvas` 的 viewer，Node 26/Windows 上 `canvas@3` 编译失败（实测）
  （`docs/MC-EYES.md:103`）。
- **不要让通用视频入口接受 URL**：等于给模型一个"任意 URL 下载 + ffmpeg 解码"的入口，安全/成本/审计都不可控；B 站走专用插件
  （`docs/VIDEO-EYES.md:19-27`）。
- **不要用用户客户端 F2/OBS 做默认链路**：需要用户在场、无法自动触发、"监视用户屏幕"需明确同意；只作高保真人工复核
  （`docs/MC-EYES.md:115-120`）。

### 4.3 对 myco 未来形态的硬约束

1. **"实时"这个词目前只能兑现成"按需抽一帧/一组帧"**。7s/张与 9s ASR 都撑不起对话内即时反馈；能撑的是"我看一下"级别。
2. **感知必须同步、可失败、可解释**：视频工具有 15 个明确失败码（`E_NOT_FOUND/E_REMOTE_URL/E_TOO_BIG/E_NO_FFMPEG/E_TIMEOUT…`），
   模型据此决定下一步；没有附件服务时**降级成文本路径，不假装图已在上下文**（`docs/VIDEO-EYES.md:46-55,120-136`）。
3. **图片是上下文成本，不是免费通道**。任何"持续观察"设计都必须先回答"这一帧值多少 token"。
4. **感知 = 插件，不是核心**：video 插件"不排程、不唤醒、不发信号；只是被调用时干活"（`docs/VIDEO-EYES.md:144`）。
   这个边界值得抄——感知与主动性解耦。
5. **权限/同意是产品前置**：cookies.txt 是账号凭证，不打印值只返回到期时间；用户屏幕截图需明确同意。
6. **离线优先**：视频只读本地、不出网；MC 用自渲染画面而不是用户屏幕——这套"自己能看到的，才敢说看到"是人格底线的技术对应物。

---

## 5. 已付学费的负面结论

| # | 结论 | 代价/证据 |
|---|---|---|
| N1 | **单例不是一个锁，是一个架构决定**。三代都栽在"先用锁去补，然后只开一个会话变成人为约定" | dsh-qq 把一个人切成 N 份 → focus-core 加租约 → icl 单锁；第一轮 5 分钟测试就因两会话共用 `time.json` 作废（`docs/HISTORY.md:85-89`、F2） |
| N2 | **文档跟着代码跑必然过期，然后变成负债** | 整理前 32 份文档、审计查出 22 处与代码不符、4 处自相矛盾、5 份自称过期（`docs/OVERVIEW.md:117-121`）；本次也发现 `FOCUS.md:109` 的 order 15 已过时（实际 875，`focus/src/index.mjs:185`） |
| N3 | **静默失败是系统性的**，对记忆系统最致命：记忆变薄时系统不报错，只表现为"她想不起来" | 装配期空 `try/catch`、delivery 第 5 次重试直接丢弃、`dsh-raw` 解压失败返回空串、只解析 user/assistant 导致工具结果（视频作者）永不进记忆 → 5 分钟测试里 `people 0`（`docs/OVERVIEW.md:123-135`） |
| N4 | **"到点必发状态播报"制造空转**：不是"没活硬整"，是"没事硬报" | 25 次心跳 17 次零工具，输出形如"快四点，43。"（`docs/ATTENTION-RESEARCH.md:56-72`） |
| N5 | **多定时器 + 概率乘数 = 不可预测、不可测** | 旧实现 replyCheck + proactiveCheck + sleepTimer + rateLimitRetry + turnSlotRetry 五个定时器、四个乘数（夜×0.3、刚发×0.5…）；旧项目 v2 自己推翻成"一条 20–45 分钟心跳 + 自由活动清单"（`docs/OLD-QWENSTUFF-QQ-UX.md:102-118`） |
| N6 | **压缩"定位文本"可能比原文更长**：407 行设计 + 全绿单测，A/B 结果 +203% | F7；教训是加"compact 必须比 full 短才用"的 guard |
| N7 | **每轮 LLM judge 太贵**：七情判定 ≈800 tok/轮，七维状态对轻量目标过重 | `docs/SOUL-RESEARCH.md:104`；替代方案是确定性计算的 energy/contextPressure |
| N8 | **"教轮询"的提示词会诱发轮询**：E2 里 awake-tick 43 条/120min 造成"2 分钟静止 + 教轮询"，最终关闭 | `docs/PROMPT-WAKE-DESIGN.md:77`、`docs/ATTENTION-RESEARCH.md:62` |
| N9 | **`mark_read` 越权 = 消息静默消失**：一句话"标到最后一条"就能把被拦下、从没交付的消息永久吞掉 | `docs/OLD-QWENSTUFF-QQ-UX.md:168` |
| N10 | **"看过但不回"没有出口 → 同一批永远重报 → 习得性忽略** | 18 条只看了 16 条，另一群 +2 没看；18:21 查账本仍是 121 条/已交付 103 条，16 条并未因"看过"推进（`docs/ATTENTION-RESEARCH.md:98-104,433-435`） |
| N11 | **结构性旁路会毁掉睡眠保证**：bridge 冷恢复未读摘要绕过 signals/delivery，直接把睡着的她叫醒（24:20 事故） | `docs/ATTENTION-RESEARCH.md:464-478` |
| N12 | **语义造假会变成信任问题**：MC 的"暂停"承诺了物理世界做不到的状态（vanilla MC 无服务端冻结），"暂停了但位置在动"是 P0 | `docs/PAUSE-SEMANTICS.md:20-22,56-65` |
| N13 | **测试全绿 ≠ 有人用过**：98 处引用的模式不可达、61/61 与 31/31 审计全过同时是在进不去的房间 | `docs/OLD-QWENSTUFF-QQ-UX.md:236`；本仓库同病：`focus` 真实对话 0 次使用（F6） |
| N14 | **端到端证据一次一次往后推**，三期都没有 ≥30 分钟的完整闭环 | `docs/HISTORY.md:105-109`；OVERVIEW 的建议路线是"冻结新增能力，先跑通一次闭环" |

---

## 6. 对 myco MVP：抄 / 改 / 别抄

### 6.1 MVP 阶段人格只做这几件事，就够撑住"陪伴感"

| 做什么 | 具体形态 | 为什么够 | 成本 |
|---|---|---|---|
| ① 一张 1.5–4k 字符静态人格卡 | 身份 / 短句节奏 / 3–5 个口头禅 / 边界（不许编造、不许假装看过）/ 拒绝权 | 唯一被真实证据支持的效果载体（F3） | 3–4k 字符 system，一次写死 |
| ② 事实段只陈述 | `时间｜你在做什么｜谁在等你`，≤2 行，order≥800，无内容整段消失 | 让"她知道自己此刻在哪"而不是靠模型猜 | 每轮 ≤500 字符 |
| ③ 记忆清晰度措辞 | 5 档衰减 + 召回时改措辞（"有点模糊/只剩印象"） | 最便宜的活人感；已实现过（C8） | 零额外调用，纯确定性计算 |
| ④ 情绪只做"倾向 + 余味" | "上一轮的感受会带进下一轮，不清零"；不做数值、不做每轮 judge | 数值情绪成本 ~800 tok/轮且不可验证（N7） | 0 |
| ⑤ 已读不回是显式动作 | 三档水位 + `ack` 工具 + "回复即自动 seen" | 没有它，同一批永远重报（N10） | 一个小工具 + 一列水位 |
| ⑥ 版本与回滚 | `current/set/rollback/versions`，改人格不改代码 | 人格改一次打掉缓存，需要可回滚 | 极小 |

**不做**：关系值、驱动值、purpose 衰减、七情状态机、日记调度、每回合记忆提取。这些在源仓库都没被证明有效。

### 6.2 主动性的最小闭环（推荐：事件驱动为主 + 定时器做兜底扫描）

```
pending = 未读(按 activity) + 惦记(note open) + 积压(time queue)
定时器：只在「没有 turn 在跑」时计时；到点只做一次 pending 计算
  ├─ pending > 0 且 idle ≥ 最小空闲 → 起一个 turn，投「决策简报」
  ├─ pending = 0            → 不产生 turn，只记 skip，间隔 ×1.5（封顶 12×）
  └─ 封顶后                 → 只做检查（不调模型）；长静默 turn 默认关闭
turn 内容 = 简报五段（自己醒的 / 你在 / 谁在等 / 可以什么都不做 / 只接在等的事）
turn 出口 = 回复 / ack / 明确 no-op
```

- **为什么不是纯定时器**：纯定时器 = N4 的空转；**为什么不是纯事件驱动**：没消息时绝对安静，长任务（写东西、整理）永远不推进（`docs/ATTENTION-RESEARCH.md:127-131`）。
  组合才是答案。
- **三层主动性边界**（照抄，`docs/ATTENTION-RESEARCH.md:219-229`）：L1 处理（有等的人）默认开；L2 自己的时间（不发对外消息）可选；
  **L3 无由头主动联系默认关**，需单独预算 + 归属日志 + 人工抽检。
- **第一真实场景选 L1**：群/私聊积压查看 + 按需回复；回复等待中的消息不算打扰。
- **预算**：小时 N 次 + **天** M 次（源仓库缺天预算），杀 switch 必须有。

### 6.3 必须进插件接口、第一天留好缝的 8 个点

| # | 接口 | 为什么第一天就要 | 抄自 |
|---|---|---|---|
| 1 | **时钟服务**：`status()/arrange()/pause()/mute()` + 只读 `phase`；外部注入方直投前必须查 | time 只能拦 `signals`/`delivery`，**拦不住直接 session.prompt**（N11、`docs/TIME.md:237`） | `packages/plugins/time/src/index.mjs` |
| 2 | **投递裁决纯函数** `decide(input)→{action,channel,suppressReason,wake,holdMs,queueKey,explain}` | 两闸合并成一个可穷举测试的函数；drop 只留给去重/过期 | `docs/DELIVERY-POLICY-DESIGN.md:128-185` |
| 3 | **按 activity 的三档水位** `lastSeq/deliveredSeq/seenSeq` | 决定"不丢消息"与"不重复提醒"两件事 | `docs/OLD-QWENSTUFF-QQ-UX.md:10-15` |
| 4 | **提示词段注册**：order 约定 + 只陈述 + 每段上限 + 空段消失 + 动态段在尾部 | 前缀缓存是产品级成本（F9）；混排会每轮打掉缓存 | `docs/PROMPT-CONTRACT.md`、C4 |
| 5 | **感知插件接口**：`snapshot()`（结构化、毫秒级、便宜）/ `capture()`（像素、秒级、贵、有配额）两类，**同步返回、带成本声明、带失败码** | 让"能不能看"成为可度量契约，而不是提示词祈使句 | `docs/MC-EYES.md:213-219` |
| 6 | **人格服务**：`current/set/rollback/versions`，且**人格插件不做任何主动行为** | 人格是配置，不是行为；行为归 sim/工具 | `packages/plugins/persona/src/index.mjs:1-2` |
| 7 | **世界状态总线**：深度 2 只读，`items()` 空时**默认报"无"** | "没有消息"本身是事实；否则模型把缺失读成"世界只有时间" | `packages/plugins/world-state/src/index.mjs:55-58` |
| 8 | **会话身份**：人格是单实例全局，还是 per-session 可覆盖 —— **显式定一个字段** | 这是 §7 未解问题 1 的技术落点；源仓库选了单实例 + 硬锁，代价是"只开一个会话" | C9 |

### 6.4 别抄（每一项都有代价证据）

1. **七情/心情每轮 judge**（N7）、**情绪数值表**（旧卡有，新人格已删）。
2. **概率当"要不要说话"的主判据**、多定时器 + 概率乘数（N5）。
3. **到点必发的状态播报心跳**（N4）。
4. **在唤醒消息里重发 orientation**（N6；必须带"不比 full 长"的 guard）。
5. **模式标签人格卡**（`## 表情包体系 〔二代〕` 这种按模式在卡里打标签，导致同一张卡里两套冲突指令）——`docs/OLD-QWENSTUFF-QQ-UX.md:132-137`。
6. **58 个 `qq_*` 工具面 / 25 个每会话状态字段 / 8 种唤醒触发器**（同文件 `:145-153`）。
7. **常驻 1fps 截图 / 把"暂停"承诺成物理冻结 / 让通用视频入口接 URL**（§4.2）。
8. **`mark_read` 允许跨过未交付**（N9）。
9. **让"只开一个会话"当人为约定**（N1）。

---

## 7. 未解问题（含我的倾向）

1. **人格是单实例还是每会话一份？**
   **倾向：单实例人格（全局常量）+ 每会话"关系档案"（数据）**。人格 3.9k 字符改一次打掉全部会话缓存；多会话真正冲突的不是人格，是关系值/记忆/水位。
   源仓库选了单实例 + 硬锁并付出"只开一个会话"的代价（N1），myco 若要多会话，必须**从第一天就把"人格=代码、关系=数据"分开**，而不是给每个会话复制一份人格。
2. **主动性的第一场景？**
   **倾向：L1（有人等）先跑 4 周，L3（无由头主动）默认关。** 依据：唯一真实 L1 tick 成立（F5，尽管有 caveat）；ProactiveBench 上"此刻该不该主动"微调后 F1 仅 66.47%（`docs/ATTENTION-RESEARCH.md:145-147`）——别假设模型天然会判断。
3. **定时器 vs 事件驱动？**
   **倾向：事件驱动为主 + 定时器只做"pending 兜底扫描"，且扫描本身不产生 turn。** 依据：条件心跳已把 68% 空转消掉（F6 + `docs/ATTENTION-RESEARCH.md:445-451`），但纯事件驱动会让长任务停摆。
4. **实时感知做到哪一步？**
   **倾向：MVP 只做"按需单帧 + 结构化快照"，不做连续流；摄像头推迟到有明确用户请求 + 同意 + 隐私设计。**
   依据：7.1s/1.3GB 与 ASR 冷 9s（F10–F12）都不支持"实时"，而"假装看到"直接违反人格底线（`prompts/persona.md:96-99`）。
5. **上下文成本怎么控？**
   **倾向：人格/仿真写死不动、facts ≤2 行、唤醒消息只给决策简报、图片按需。** 依据：E2 里 70% 的投递字符是 facts 快照（F8），
   而缓存一破就是 7k–24k tok 的 prefill（F9）；**"省唤醒消息的 orientation"收益远小于"省 facts 噪声"**——这是 F7 反例给出的排序。
6. **要不要做多用户？**
   **倾向：MVP 不做。** 多用户 = 多实例 = 回到"一人切 N 份"（N1）。若必须，先做"per-user 记忆库 + 单实例串行服务"，
   不做"多 agent 并发"。源仓库的结论更狠："多 DSH 会话（一个 QQ 会话一个 agent）与 ICL 单人格/单焦点设计相反；三代教训就是
   '一人被切成 N 份'"（`docs/QQ-GAP.md:134`）。
7. **感知与主动性能不能合并成"她在看屏幕所以会主动说话"？**
   **倾向：不能。** 源仓库把所有跨代的失败都指向同一件事：**主动性与发送权必须分开**（`docs/SOUL-RESEARCH.md:96-99`：
   "主动想起，不是主动发消息"）。感知只提供"她知道了什么"，是否开口仍走投递裁决 + 预算。

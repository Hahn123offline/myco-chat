# dsh-icl 记忆体系考古（供 myco 记忆层设计）

> 对象：`E:\11-15\tools\external\dsh-icl`（HEAD 约 2026-11-15）。只做静态阅读，未运行、未调用任何模型服务。
> 证据分级沿用仓库口径（`docs/OVERVIEW.md §4`）：**E0** 只有文档；**E1** 单测（假宿主/临时 SQLite）；**E2** 假 DSH 集成；**E3** 真实 DSH 会话（真模型/真日志）；**E4** 长跑（≥30min 或跨天）。
> 代码事实优先于文档自述（该仓库自己承认文档负债，见 N18）。

---

## 0. 一页结论

1. 它**不是记忆库**，是"原文层 + 一个 SQLite 三表 + 夜间批处理"的三段结构；质量杠杆在**写入侧**，不在检索算法。E3（`MEMORY-DIAGNOSIS.md §0-1`）
2. 模型三代：v1（namespace+note/block）→ v2（typed 原子事实 + entity + validity）→ **v3（episode 块唯一召回主单位，typed 降级为块内 annotations）**。v2 被用户亲手否定："原子事实破坏陪伴叙事"。E1/E3（`MEMORY-V2-DESIGN.md`、`MEMORY-V3-DESIGN.md`、`MEMORY-SCOPE-V1.md §3`）
3. 检索是**混合打分 + 指数衰减**：`score = sim × weight × decay`，`sim = 0.7cos + 0.3bigram`（无嵌入退化为 bigram），精确串 +0.5。E1（`memory/src/index.mjs:277-368`）
4. 召回两条路：**被动 eager（每轮 ≤2 条/≤200 字）** vs **主动 recall 工具（10000 token 滑动游标）**——不是两个功能，是两种"谁决定该想起来"的立场。E1/E3（`eager-recall/src/index.mjs:66-147`、`recall/src/index.mjs:54-290`）
5. 整理（dream）是**夜间 7 job 串行流水线 + 按天幂等账本**；真正起作用的是"一次批量、可重跑、带账本"，不是"梦"。E1（`dream/src/index.mjs:25-28,990-1100,3123`）
6. 评测是**最值钱的资产**：fixture 契约 + 三源真实素材 + 三场合成场景 + 20 个指标 + gate 预设 + exit 3。E1（`MEMORY-EVAL-SPEC.md`、`tools/memory-eval.mjs:32-36,169-202`）
7. 学费也重：幂等用错模型、身份无主键、指标把原料当块、评测映射假 miss、并行打爆嵌入、段分区 A/B 不通过（§4 N1–N18）。
8. 最致命一条，值得刻墙上：**记忆变薄时系统不报错，只表现为"她想不起来"**。E3（`OVERVIEW.md §6-B4`）
9. 缓存是**架构级约束**：system 里放任何每轮会变的字节 → 整条前缀作废（hit=0、TTFT 5–24s）；修好 hit 91.2%（长跑 99.5%）。记忆注入必须走**追加消息**。E3（`CACHE-MODEL.md §0,§5.4`）
10. 主动召回的结论是**收窄触发 + 明确 no-op**，不是更主动：真实心跳 68% 空转。E3（`ATTENTION-RESEARCH.md §1.2,§5.4`）
11. **对 myco 的最大差异：此仓库全部成块质量依赖 LLM**；myco 本轮禁调模型 → MVP 只能做"原文层 + 结构 + 检索 + 注入 + 规则化整理"，LLM 口子留空。这决定抄什么（§5）。
12. 建议：抄表结构、抄原文层来源分类、抄 eager 最小注入与诚实话术、抄 fixture 口径；别抄 typed 分类、别抄 7 job、别抄段分区、别抄 entity 图。

---

## 1. 事实清单（带出处）

### 1.1 规模与证据级别

| 事实 | 出处 | E |
|---|---|---|
| 记忆/召回 6 插件约 1300 行、14 个测试文件；dream 995 行 7 job；后期记忆相关约 10k 行 | `OVERVIEW.md §4` A2/A3、`MEMORY-SCOPE-V1.md §2.2` | E1 |
| 全仓测试 545→897→922→927→937 逐轮增长 → 引用数字必须带日期/commit | `MEMORY-FIXES.md §4`、`MEMORY-SEGMENT-DESIGN.md §13,19` | E1 |
| 端到端只有两次 5 分钟真实会话，第一次因"两实例共用一个工作区"作废；**从未从醒来跑到再醒来** | `OVERVIEW.md §3,§6-B5` | E3 |
| 真正每天跑的只有心跳；`focus_enter` 真实对话 0 次 | `ATTENTION-RESEARCH.md §1.2` | E3 |
| 记忆 v3 于 11-12 热迁移上 live（67 episodes / 133 段 / 16 entity / 18 relation），v2 库归档 | `MEMORY-V3-REBUILD.md §4` | E3 |
| 生产 `partition=day`（段分区 A/B 不通过，S4 关闭） | `MEMORY-SEGMENT-DESIGN.md §15-16` | E3 |
| 42 份 MEMORY-* 文档里 5 份自标过期、22 处"文档≠代码" | `OVERVIEW.md §6-B3`、`MEMORY-DIAGNOSIS.md §7` | E3 |

### 1.2 数据模型

| 事实 | 出处 | E |
|---|---|---|
| 只有三表：`mem_ns`/`mem_items`/`mem_vec`（`node:sqlite` 单文件） | `memory/src/index.mjs:26-57` | E1 |
| 唯一索引 `ux_mem_key(ns,key) WHERE key IS NOT NULL` → **只有带 key 的写才是 upsert** | 同上 `:50` | E1 |
| 更新默认**保留原 weight**（含 boost），仅 `reweight:true` 覆盖；改 text 删旧向量 | 同上 `:187-208` | E1 |
| 命名空间注册幂等且**已存在不更新** label/recallable/halfLifeDays | 同上 `:159-167` | E1 |
| v1 有 11 个命名空间；v3 追加 episode/episode-seg/entity/relation/dream-segment/dream-cursor；`recallable:false` 只挡 `search`，不挡 `query` | `MEMORY.md §1`、`MEMORY-V3-DESIGN.md §2`、`MEMORY-SEGMENT-DESIGN.md §3` | E1 |
| 能力表统一为一份代码：v3 可查询 = episode/person/lexicon；v2 旧 namespace = 归档；episode-seg/entity/relation/day = 内部 | `packages/core/src/memory-capabilities.mjs:21-48` | E1 |
| episode.data 形状：`body/evidence[{type,id,line,at}]/provisional/day/people/entities/tags/annotations{facts,preferences,commitments}/relations/mergedFrom/days` | `MEMORY-V3-DESIGN.md §2` | E1 |
| 原文层是只读投影 `{text,time,role,sourceKind,channel,speaker}`，真源 `~/.dsh/sessions/**/*.jsonl(.zstd)` | `MEMORY.md §1`、`MEMORY-COMPONENT-AUDIT.md §1-2` | E3 |
| 只有 v3 新数据能回溯：11-03..11-11 旧 day log `rowIds=0`，导入 episode 的 evidence 指向 fixture id `s1-r-*` | `MEMORY-COMPONENT-AUDIT.md §3-C7` | E3 |

### 1.3 检索

| 事实 | 出处 | E |
|---|---|---|
| `score = sim × weight × decay`；`decay = exp(-ageDays/halfLife)`；`anchor = lastRecalledAt ?? createdAt` | `memory/src/index.mjs:359-363` | E1 |
| `sim = 0.7cos + 0.3bigram`（有嵌入）/ 纯 bigram（无嵌入）；无 query 时 `sim=1` | 同上 `:334-352` | E1 |
| 精确串（路径/文件名/带单位数字/≥4 字母英文词）`+0.5·exact/terms`，封顶 1，有/无嵌入都生效 | 同上 `:353-357,125-141` | E1 |
| `scanLimit` 默认 5000 行（`created_at DESC`）→ **超 5000 条的老记忆结构上不可检索** | 同上 `:287-288` | E1 |
| 软删 `superseded` 不参与；时间旅行保护 `created_at<=now` | 同上 `:300-305` | E1 |
| `boost` 每次 +0.2 封顶 5，同时 `recall_count+1`、重置衰减计时；半衰期 event 30/person 180/slang 90/lexicon 180/resolve 1 | 同上 `:371-377`、`MEMORY.md §3` | E1 |
| eager：`minScore 0.5`/`minSim 0.5`/无嵌入 `0.2`/`maxItems 2`/`maxChars 200`/候选池 40/冷却 180s/低置信提示 0.33+300s | `eager-recall/src/index.mjs:18-52` | E1 |
| eager 按 **sim 优先**排序；`score≥阈值 或 sim≥minSim` 均可入；已消化 note 不再注入 | 同上 `:75-90` | E1 |
| recall：`minSim 0.45`（有嵌入）/`0.2`（无）/cursor 模式 0.05；默认 `limit 6` | `recall/src/index.mjs:60-70` | E1 |
| recall 游标 = `recall-cursor` 行 `cursor:<session>`，`items=[{id,tokens}]`，预算 10000 token，**第一条永远放行**，超预算从最早 shift | `recall/src/index.mjs:240-276`、`MEMORY.md §3` | E1 |
| `estimateTokens = ceil(cjk/1.5 + rest/4)` | `recall/src/index.mjs:18` | E1 |
| 意图路由（person/commitment/preference/fact/event，倍率≤1.3）、复合问句拆 3 子句、指代消解、日期归一 | 同上 `:146-231` | E1 |
| raw 兜底：每次 recall 都 `raw.search(limit 2)`，与主结果 bigram 重合 <0.6 才收 | `MEMORY.md §3` | E1 |
| live 实测：recall **只被调用过 1 次**，有 recall 记录的记忆 3 条、最高 count=1 | `MEMORY-AUDIT.md §4` | E3 |

### 1.4 整理（dream）

| 事实 | 出处 | E |
|---|---|---|
| v1/v2 默认 `wash,events,people,lexicon,consume,cleanup,index`；v3 换 `events→episodes` | `dream/src/index.mjs:25-28` | E1 |
| 账本 `run:<day>:<job>`；失败 `fail:…`、跳过 `skip:…`，**不占成功 key**（次日可重跑） | 同上 `:990,1066-1084` | E1 |
| 单飞 + 执行租约 `attempts[{id,status,startedAt}]`；信号撞在飞回合返 `skipped:'in-flight'`；崩溃遗留 running 可过期重领 | 同上 `:1018-1029,3187-3315` | E1 |
| 幂等键按天（`alreadyRan(day,job)`）——**没有跨天/增量概念** | 同上 `:999` | E1 |
| `wash` 写 `day/log`（≤40k 分段、保留绝对行号）；`episodes` 有稀疏守卫（真人材料 <60 字不调模型，<900 字合并一次调用） | `MEMORY-BLOCK-QUALITY.md §3.3`、`MEMORY-V3-REPORT.md §2.10-C` | E1 |
| 合并轮兜底："正文比原文拼接短于 80% 回退原文拼接（宁长勿丢）"+ 解析截断救回部分块 | `MEMORY-V3-REPORT.md §2.2` | E1 |
| `index` 无嵌入时 `skipped:'no-embed'`（早期 `ok:true,indexed:0` 静默 0） | `MEMORY-BLOCK-QUALITY.md §3.0` | E1 |
| 重建：逐天幂等续跑 + `semanticHash` 校验 + `--archive` 先复制旧库，实测续跑 hash 不变 | `MEMORY-V3-REBUILD.md §1-2` | E1 |

### 1.5 上下文/缓存

| 事实 | 出处 | E |
|---|---|---|
| 本地引擎（NInfer/KVMem）**system 一个字节变化 → 整条 prompt 重 prefill（hit=0）**，不是"从差异点作废" | `CACHE-MODEL.md §0,§4.2` | E3 |
| 修复前真实会话：每回合 hit=0、prefill 7k–24k tok、TTFT 10–24s | 同上 `§0` | E3 |
| 修法：system 只留 order<800 静态段（persona 20 + sim 25）；order≥800 事实走 `systemPrompt.context` → runtime-context **追加** | 同上 `§5.1`、`PROMPT-CONTRACT.md §1,§3` | E3 |
| A/B 6 轮：prefill 59547→9421 tok(-84%)、hit 41.5%→91.2%、TTFT 5.0-6.1s→0.42-0.58s、system replace 6/6→0/6 | `CACHE-MODEL.md §5.4` | E3 |
| 6h29m 长跑：hit 99.50%、TTFT p95 0.94s、全量失效 8 次**全是冷启动**、上下文 11k→104k 命中率不降 | 同上、`CACHE-NIGHT-2026-11-06.md` | E4 |
| 每轮上下文（记忆检索/压缩摘要）走 `prompt.onTurn` → pre-step 尾部**追加** user 消息，默认 `maxChars=800` | `services.mjs:475` | E1 |
| 第二股前缀破坏源：DSH compaction(80% 窗口) 与 tool-result-pruner **改写 surface 历史** | `CACHE-MODEL.md §2-行11,§7` | E3 |
| facts 快照线性累积（7 条/3213 字符/6 轮，≈130-150 tok/次） | `CACHE-MODEL.md §5.4,§7.7` | E3 |

### 1.6 注意力/主动性

| 事实 | 出处 | E |
|---|---|---|
| 心跳 `frequency 0.2`（约 50–100min）；25 次唤醒 **17 次（68%）0 工具**，8 次有动作，5 次发 QQ | `ATTENTION-RESEARCH.md §1.2` | E3 |
| 空转是"没事硬报"不是"没活硬整"：只能复述状态（"快四点，43。"） | 同上 | E3 |
| 修法：条件唤醒（`pending>0 && idle>=minIdle` 才叫醒，否则 15min 后再看）+ 决策简报 + no-op 合法 + 3h 长静默兜底；后把长静默关掉，无事只检查不产 turn | 同上 `§5.4-5.5,§10.2` | E0→E3 |
| 首次真实 tick（群积压 16 条→查看→决定不回）**被用户降级为旁证**：可能受聊天里"刷屏不用回"影响 | 同上 `§1.4 caveat` | E3→旁证 |
| 外部基准 ProactiveBench：把"此刻该不该主动"做成二分类，微调后 F1 仅 **66.47%** | 同上 `§2.4` | E0(外部) |
| "看过但不回"必须有出口，否则同一批每次心跳重报，最终学会忽略（habituation） | 同上 `§5.10` | E0→E1 |

---

## 2. 数据模型与检索/整理机制

### 2.1 五个东西是什么、怎么互相索引

```
原始会话日志（只读真源）
  │ bridge 投影 + inferRawSource
  ▼
原文层 raw 行 {id, at, role, source(human|qq_envelope|agent_visible|agent_internal|tool|env|memory), channel, speaker, text}
  │ wash：按天/段切，保留 rowIds[]（唯一回溯锚点）
  ▼
day/segment log（不可召回，原料）
  │ episodes / people / lexicon（LLM）
  ▼
mem_items：ns + kind(note|block|term|log|run|cursor) + key + text + data(JSON) + weight + 衰减
  ├─ episode/block：text=标题，data.body=叙述，data.evidence[] → day log 行 → raw 行
  ├─ person/block：key=person:<qq>|<kind>:<value>，data.profile/narrative/facts/ids/aliases
  ├─ lexicon/term：key=lexicon:<conv>:<term>，data.origins[{conv,url,meaning,status,seenAt}]
  ├─ entity/relation：确定性 syncEntities 从上述反推（支撑 whois，不进默认召回）
  └─ recall-cursor：装配层唯一落盘状态
  ▼
装配层（无自有存储）：recall 工具 / eager onTurn / whois / world 状态行
```

索引只有三条，且都很脆：**episode→evidence→day log 行号→raw id**（v1 用行号当证据，重洗后位移，v3 改存 `line`+`at`）；**person→event.data.people / episode.data.entities**（v1 存 QQ、比 `person:kind:value` 永远不匹配，缺陷#8；v3 靠 syncEntities 回填，但一半 entity 是仅显示名的 stub）；**lexicon→origin.conv/url**（`conv==='web'` 存 `null`，查询侧需归一）。

没有的东西（别误以为有）：没有 entity 主键约束、没有 evidence 表（JSON 数组，无法 join 校验 quote）、没有版本/事务、没有"谁改的/为什么改"的审计列（只有 dream-run 账本）。

### 2.2 为什么最后收敛到"块"

| 代 | 主单位 | 形态 | 检索 | 为什么被否 |
|---|---|---|---|---|
| v1 | `note`（碎片）+`event/block`（≤200 摘要 + ≤4000 body） | 一天 3 块 | r5 86–88%、miss 6.7–11.8% | 抽取型 wash 漏内容（21 条消息只抽 4 条笔记）；块碎、证据 1 条/块 |
| v2 | **typed 原子记录**（episode/fact/preference/commitment + entity + validity + evidence） | 同素材 **199 条**，正文均 148 字，typed 16–22 字 | r5 86.7–100%（均值 93.4） | 用户 11-12 判"原子事实破坏陪伴叙事"；一条 gold 对应一原子块是**评测口径造出来的对齐** |
| v3 | **episode 块**（标题+第一人称完整叙述+证据+人物+块内注释） | 同素材 **30 块**，正文 354–388 字，证据 11 行 | anyHit@5 = 100/84.6/100（三源） | 细节定位变难（focus recall@5 87.2 vs 93.4）、内容完整性 44–56% |

收敛的三条真实理由：**成本**（v2 要给每条 typed 做写时仲裁=一次模型调用，v3 塞进 annotations 少一整类调用与生命周期，`MEMORY-V2-DESIGN.md §3.2`）；**产品口径**（回答"那天发生了什么"需要上下文，16 字 fact 撑不起叙事；用户原话"召回主单位为经历块，不限制块数、不硬性规定每块长度，没活不硬整"）；**评测可判定**（粗块让 anyHit@5 成为稳定指标，fine-grained 下 gold 拆多块会让 recall@k 的 k 上限天然 <100%）。

代价（要一起抄走）：细节型问题吃亏，补救是**块+段索引**——正文按 ~260 字句边界切 `episode-seg`，段向量只用于定位、命中段回映射到父块返回完整正文（实测 QQ 80.8→83.3、live 94.4→100，`MEMORY-V3-REPORT.md §2.4`）；块内注释**没有生命周期**（25 条 commitment 只是历史字符串，直接造成 P0，见 N10）。

### 2.3 检索：打分、触发时机、注入分层

**打分（唯一公式，全仓库只此一份）**

```
sim   = 0.7·cos + 0.3·bigramOverlap(text+body+annotations, query)  // 有嵌入
sim   = bigramOverlap(...)                                        // 无嵌入且有 query
sim   = 1                                                         // 无 query
sim  += 0.5 · literalHits/|literalTerms|   （封顶 1；精确串）
age   = now - (lastRecalledAt ?? createdAt)
score = sim · weight · exp(-age / (86400·halfLife[ns]))
```

三个"文档没强调但代码决定行为"的点：`anchor` 用 `lastRecalledAt` 而非 `createdAt`（→N6 慢性消失）；`scanLimit=5000` 是硬上限；`sim<=0` 直接丢（"无 query"与"query 完全无关"是两种语义）。

**何时触发（两条完全不同的通路）**

| 维度 | eager-recall（被动） | recall（主动） |
|---|---|---|
| 触发者 | 每条用户消息插件自动跑 | 模型决定调工具 |
| 依据 | "模型不知道自己不清楚" | "模型意识到需要旧细节" |
| 输出 | ≤200 字"你可能记得（检索结果，仅供参考）" | 工具返回，带标题/正文/出处 |
| 阈值 | `score≥0.5` 或 `sim≥0.5`（无嵌入 0.2） | `sim≥0.45`（无嵌入 0.2、cursor 0.05） |
| 条数 | 2 | 默认 6，受 10000 token 游标预算约束 |
| 去重 | 会话内冷却 180s | 跨轮游标"这一轮已给过你的块" |
| 失败降级 | 0.33–0.5 区间只回一行"你可能对某段经历有相关记忆（不确定）" | 三种"没有"：范围内都不相关 / 范围太窄但全局有 / 全局真空 |
| 分工理由 | 检索结果**不是她要说的话**，推消息会把"参考"变"断言"，还留在历史里被下轮重检索（自我强化） | 工具结果只进本轮上下文，天然一次性 |

**注入分层与预算（缓存模型的直接产物）**

```
system（必须整场字节稳定，order<800）：harness:identity(-1000) → ICL persona(20) → ICL simulation(25)
runtime-context 快照（order≥800，内容变才追加，不 replace）：time / world / focus / mc:status /「记忆：经历 67 段…」
pre-step 尾部追加（append-only）：<icl-context> ← prompt.onTurn（eager 注入 + 压缩摘要，默认 ≤800 字）
tool result（append）：recall / whois / memory_status
```

预算：eager ≤200 字/2 条；`turnText` ≤800 字（多个 provider 共享）；recall 10000 token 游标，单条超预算跳过但**第一条永远放行**。

**缓存对注入布局的三条硬约束**：① system 不许放任何每轮会变的字节（包括"记忆条数"这种看着无害的计数）；② 动态事实走追加快照、记忆注入走 pre-step 追加；③ 任何**改写历史**的机制（compaction、tool-result pruner）都是第二前缀破坏源。

### 2.4 整理：7 个 job、幂等键、覆盖/回滚

| job | 读 | 写 | 幂等键 | 跳过 | 已知坑 |
|---|---|---|---|---|---|
| `wash` | 当天/本段 raw | `day/log`（≤40k 分段，带 rowIds/times/sources） | `run:<day>:wash` | already-ran/no-raw/nothing-clean | `no-raw` 不写账本 → 同日重跑；重洗覆盖旧 log、行号全变（v1 缺陷#3） |
| `episodes`（v2 名 `events`） | day/segment log + 未消化 note | `episode/block`：标题≤200、body、evidence≤20、people、annotations、relations | `run:<day>:episodes`；块 key `epi:<day\|seg>:<hash(标题)>` | already-ran/no-notes/below-salience | 摘要哈希做 key → paraphrase 必然新 key（N4）；回查窗口 200 块 |
| `people` | day log 抽人 + person note/block + event block | `person/block`（key `person:<qq>`）+ `person/note`（key=NULL，不幂等） | `run:<day>:people` | already-ran/no-notes | 无证据且补问失败仍写块 → 每晚重复进材料；relatedEvents 只按 QQ 比 |
| `lexicon` | day log + event/slang note + `lexicon.query(500)` | `lexicon/term`（按 key upsert）、老化 `usageState:'fading'`、缺释义写 note | `run:<day>:lexicon`+进程内 seenTerms | already-ran/no-lexicon-plugin | 标签取决于模型给的 reason；工具词噪声 18/37 |
| `consume` | `note/note` 中 `status==='done'` 200 条 | 按 `about.namespace` 写回 / 新建回收 note / 原 note `archived` | `run:<day>:consume` | already-ran/no-note-plugin | 只扫 200 条，done 超 200 后新条轮不到 |
| `cleanup` | resolve note 100 + block 各 500 | 合并（keep `put`、drop `superseded+supersededBy`）、改写 text/body/evidence、resolve 标 consumed | `run:<day>:cleanup` | already-ran/nothing-to-do | v1 三次 patch 基于改写前 data（#2）；只喂最后 150 块；identity 归并只走显式 alias |
| `index` | `missingVectors(200)`（只 recallable 且 text 非空） | `mem_vec` | `run:<day>:index` | already-ran/nothing-missing/no-embed | 无嵌入时曾 `ok:true,indexed:0` 静默 0（N2） |

**覆盖语义——三种东西必须分清**：① **同 key 重写=覆盖**（`put` upsert，text/data 全替换、weight 保留、向量删等重建；wash 重洗、person 档案重写都是这个）；② **软删=可追溯**（`superseded=true`+`supersededBy`，行还在，search 过滤，审计可查）；③ **归档=读取路径拒绝**（v3 的 legacyNamespaces 由能力表统一驱动 recall/eager/whois/状态行——这层是事故逼出来的，N13）。

**回滚**：块级只能人工改回 `superseded`，没有"撤销 dream"；库级 `--archive` 复制 DB + `semanticHash` 校验续跑 + `VACUUM INTO` 热备份，可整体替换回退（`MEMORY-V3-REBUILD.md §1,§4`）；段级"失败不推进 cursor、重试同段不产生重复 episode"是全仓最干净的幂等设计（`MEMORY-SEGMENT-DESIGN.md §4`），虽然它本身没通过 A/B。

### 2.5 "睡觉整理"这套隐喻：真实价值与代价

**真实价值（三条，都是工程价值）**：批处理把 N 次抽取压成 1 次/天（19 天约 31 万 prompt tok、个位数人民币）；可重跑（账本+幂等 key 是迭代 20 轮的前提）；触发语义干净（睡眠是天然安静窗口，且"睡着了"能解释为什么不回消息）。

**代价（四条）**：① 隐喻进了产品语汇（`dayCutHour`/`targetDayInstant`/`sleep-start` 让时间替 dream 决定记忆边界，同日再唤醒就出气泡，N11/C6）；② 隐喻没改善质量（提升全来自"wash 不抽取、原话全存"与 source 门控）；③ 制造了不可验证的中间层（dream-run 被当成功证据，失败只进日志 → 质量报告 failures 0 的假象，N2）；④ 让人误以为需要 7 个 job（收益集中在 wash+episodes+index，`lexicon/consume/cleanup` 净收益从未单独证明，而各自都带污染）。

**一句话**：借"批量+幂等+夜间触发"，扔掉"梦/天/睡眠决定边界"。

---

## 3. 评测方法论

### 3.1 fixture 设计（最值得抄的部分）

一份 `fixture.json`（schema=1，有 JSON Schema 契约 `tools/fixtures/memory/schemas/fixture.schema.json`）：

```
persona { self{name,ids[]}, owner{name,ids[],aliases[]} }
days[]  { day, rows[] }
  row   { id(稳定唯一), at(ISO+tz), role(user|assistant|tool|env|memory), channel(web|qq:private:*|qq:group:*),
          speaker{kind:owner|self|person|system|unknown,value}, text,
          source(human|qq_envelope|agent_visible|agent_internal|tool|env|memory|unknown), visible }
gold    { entities[]{id,kind,name,aliases,ids,importance},
          memories[]{id,type,subject,text,evidence[rowIds],validFrom,invalidatedBy},
          noise[]{rowIds,reason} }
probes[] { id, rowId(在这一行之后施加), query, kind(recall|eager|person|overview),
           expectedMemoryIds, expectedAny, expectAnswer, mustNotRecall }
```

四个关键设计点：`rowId` 稳定且跨生成顺序不变（否则证据链不可复现）；gold 必须带 evidence 行（把"能不能证明"变成结构约束）；`noise` 是显式集合（env/tool-output/agent-plan/jargon/one-off-visual），把"不该记的东西"变成一等公民；`profile` 三档（`raw-all`/`raw-main`（推荐）/`raw-human`）产品与评测共用同一分类函数，**禁止两套实现**——这一条救了他们，raw-all→raw-main 的收益是量化出来的。

素材：三源真实（focus-core 642 条+118 QQ / old-qq 170 条 / live 8-9 天 raw）+ 三场合成的构造式金标（`synthetic-alias-and-people`、`synthetic-preference-change`、`synthetic-commitment-followup`，spec.json→fixture.json ~100KB/个，构造时事实/别名/矛盾/承诺全已知）。

### 3.2 怎么判分

形成：`P=matched/extracted`、`R=matched/|gold|`、F1；`dupRate`（同类型 Jaccard≥0.6 或证据集 Jaccard≥0.8）；`noiseRate`；`selfSourceRate`；`provenanceRate`（≥1 条 evidence 能解析到 row）；`identity`/`wrongIdRate`；`idempotency`（`--repeat 2` 两次证据集差异）。
检索：`recall@1/3/5`、`MRR`、`NDCG@10`、`injectionPrecision`、`missRate`、`falseInjectionRate`、**`anyHit@k`**（粗粒度下的产品口径）、成本（injected tokens/chars、延迟 p50/p95）。
v3 追加：`goldCoverage`、`goldContentRate`（内容词覆盖率，防幸存者偏差）、`contentCompletenessAvg`、`episodes/day`、`bodyLen`、`evidence/块`。
gate：`--gate-preset focus|qq|live`，如 focus=`formation.recall≥0.75,recallAt3≥0.65,recallAt5≥0.75,missRate≤0.25,falseInjectionRate≤0`；不过 exit 3 并逐条打印未过项。

### 3.3 "看起来有效其实没测到"的坑（六条，全部实测过）

1. **映射假 miss**：gold→extracted 阈值 0.45→0.4，focus r5 直接 +4.5pt、miss 归零（`MEMORY-V3-REPORT.md §2.8`）→ 任何"检索变好了"先证明不是阈值变了。
2. **幸存者偏差**：期望映射解析不出的 probe 被排除出指标，r5 看起来高（QQ 80% 就是这么来的，`§2.1`）。
3. **k 上限**：一条 gold 期望 6 个 episode 而 recall@5 的 k=5 → 严格 recall 数学上最多 83%，这是指标形状不是系统失败（`§2.12-②`）。
4. **第 1 轮 93.6% 是水分**：实体对扫描硬编码高分把关系邻居块塞进 top5 + 期望映射挑中恰好被召回的那块，修正后 81–82%/miss 7.1%（`§2 注`）。
5. **基础设施噪声污染指标**：6 路并行 fresh 打爆 bge-m3，同配置 r5 72.7%→20.8%（`MEMORY-BASELINES.md §2`）→ 评测必须串行。
6. **raw falseInjection 高估**：三源负例被 judge 判"只提到话题、没有回答"，adequacy-adjusted = 0 → 负例指标要区分"返回了"与"当成答案宣称了"。

补两条方法学：`--seed gold`（只测检索）与 `--seed formation`（端到端）必须分开跑，差值就是"形成→检索"的桥损失；`--seed db` 复用重建库把单轮迭代从 3–5 分钟降到十几秒（`MEMORY-V3-REPORT.md §2.8`）——这是长期迭代能否持续的关键工程细节。

---

## 4. 已付学费的负面结论

| # | 结论 | 证据 | E |
|---|---|---|---|
| N1 | **素材污染第一**：live raw 708 行里 360 assistant + 291 tool = 92%，真人消息 39 行。修 source 门控的收益远大于任何检索调参。 | `MEMORY-DIAGNOSIS.md §1-R2`、`MEMORY-AUDIT.md` | E3 |
| N2 | **静默失败是系统性的**：空 catch；delivery 第 5 次重试直接丢；dsh-raw 解压失败返回空串；dream 失败只进日志不进账本；无嵌入 index 也 `ok:true` → "记忆变薄时不报错，只表现为她想不起来"。 | `OVERVIEW.md §6-B4`、`MEMORY-BLOCK-QUALITY.md §2` | E3 |
| N3 | **幂等不能用"成功账本"**：两 trigger 相差 15.012s 并行，events/cleanup 各跑两遍、11 对重复块；`recordRun` upsert 把两次压成一行，审计看不见。改法：单飞+租约+attempts+`fail:/skip:` 前缀键。 | `MEMORY-DIAGNOSIS.md §1-R3`、`dream/src/index.mjs:1018-1084` | E1 |
| N4 | **事件身份不能用"生成摘要哈希"**：同一事两次生成必 paraphrase；Jaccard≥0.8 的去重，live 5 对实测 0.35–0.64，**0/5 命中**。 | `MEMORY-DIAGNOSIS.md §1-R4` | E3 |
| N5 | **person 无主键必污染**：显示名做 key；aliases 存而不用；Hahn123 档案挂着她自己的 QQ；主人真实 QQ 查不到档案；群号当 QQ。 | `MEMORY-DIAGNOSIS.md §1-R5`、`MEMORY-AUDIT.md §3` | E3 |
| N6 | **衰减锚点选错→慢性丢失**：`anchor=lastRecalledAt ?? createdAt`，只有主动 recall 才 boost；重要但没被问过的记忆衰减到 0，而 live recall 只 1 次。 | `MEMORY-DIAGNOSIS.md §1-R7` | E3 |
| N7 | **指标把原料当块**：`day/log` 单条 138,778 字符计入 `blockChars p95`，触发"块过大"告警，掩盖真问题（0 个 event block）。 | `MEMORY-BLOCK-QUALITY.md §0,§2-R3` | E1 |
| N8 | **评测会自己骗自己**：映射阈值、幸存者偏差、k 上限、硬编码加分（§3.3 六条）。 | `MEMORY-V3-REPORT.md §2,§2.8,§2.12` | E1 |
| N9 | **typed 原子事实是过度设计**：199 条 vs 30 块；typed 16–22 字；用户回退。 | `MEMORY-V3-DESIGN.md`、`MEMORY-SCOPE-V1.md §3` | E1/E3 |
| N10 | **块内注释无生命周期=契约事故**：状态行说"未结承诺 25"，`recall kind=commitment` 返回空，模型据此判定"没有"并停止追问（P0）。 | `MEMORY-INCIDENT-2026-11-13-COMMITMENT.md §1-3` | E3 |
| N11 | **未完结日被提前 dream 会永久丢内容**：11-13 21:06 把当天 episodes 跑完置账本（episodes=0），用户 21:22 还在说话，之后内容被 skip。候选修复 A/B/C **至今未实施**。 | 同上 `§4` | E3 |
| N12 | **段分区 A/B 不通过**：不变量在单测成立，但质量 parity 没达成（live coverage 75% vs 87.5%；focus/QQ anyHit <95%）；"相邻合并"补救此消彼长已回滚。生产仍 day。 | `MEMORY-SEGMENT-DESIGN.md §15-16` | E1/E3 |
| N13 | **三套词表必然漂移**：存储/工具 enum/自知计数各写一份 → "状态说 25、查询为空"。修法是一份能力表驱动四处，不是补丁。 | `MEMORY-COMPONENT-AUDIT.md §3`、`memory-capabilities.mjs` | E3 |
| N14 | **lexicon/note 无门控=噪声工厂**：37 条 lexicon 里 18 条工具/框架味；22 条 note 里 18 条是自动"查词"。 | `MEMORY-AUDIT.md §2` | E3 |
| N15 | **抽取会放大谄媚**（外部）：MIST 实测记忆系统把谄媚率最多抬高 40%，主因"只保留用户话的离散片段、丢纠正上下文"；注入的记忆被回答引用后再被记录，自强化。 | `MEMORY-EXTERNAL.md §5.3` | E0(外部) |
| N16 | **记忆可能比朴素 RAG 更差**（"记忆税"）：每个整理步骤都可能删除/遮蔽证据；没有金标 gate 的优化很可能负收益。 | `MEMORY-EXTERNAL.md §5.2` | E0(外部) |
| N17 | **"没事硬报"比"没活硬整"更常见**：心跳 68% 空转只剩状态播报；修法是条件唤醒，不是更聪明的人格。 | `ATTENTION-RESEARCH.md §1.2,§5.4` | E3 |
| N18 | **文档负债本身是失败源**：5 份自标过期、22 处文档≠代码、4 处互相矛盾；"每读一份都要先判断真伪，判断成本高于重读代码"。 | `OVERVIEW.md §6-B3` | E3 |

**文档矛盾处点名（含判断）**

| 矛盾 | 双方 | 判断 |
|---|---|---|
| 工具结果进不进原文层 | `OVERVIEW.md §6-B4`（"从来不在"）vs `MEMORY.md §8-5/#16`（"已修：解析 tool/result"） | **以已修为准**（OVERVIEW 停在 11-03，修复在其后）。但"修了代码≠修了历史数据"：旧 raw 仍残缺。 |
| v3 检索是否优于 v2 | `MEMORY-V3-REPORT.md §2.1`（三源 r5 均≥v2，focus 93.6）vs 同文 §2.2/§2.6/§3（第 1 轮有水分；focus v3 均值 84） | **以 §2.8.1 anyHit@5 为准**：按"任一正确块被召回"v3 三源≥v2；按严格 recall@5，focus 上 v2 更好。两个口径都要报。 |
| live 覆盖多少 | `MEMORY-SCOPE-V1.md §2.1`（"67 段/15 天"）vs `MEMORY-V3-REBUILD.md §2.1`（19 天/67 episodes） | 不矛盾，两个分母：19 天=重建素材，15 天=live 实际覆盖。应写"重建 19 天/67 块"。 |
| "不设块数量配额" | `MEMORY-V3-DESIGN.md §1`（不设配额）vs `MEMORY-SCOPE-V1.md §4.2`（形成方差须"稳定 22–32 块/5 天"） | 是**隐性配额**——门槛把"不设配额"变回"必须落在 22–32"。这也是不建议抄 V1 门槛的原因之一。 |
| 段分区是否已完成 | `MEMORY-COMPONENT-AUDIT.md §5`（"不是段分区"）vs `MEMORY-SEGMENT-DESIGN.md §19`（11-14 已切 segment live） | 时间差（审计 11-13、切换 11-14），但 §15 判定"不建议执行"，§19 又执行了——**典型"判定被绕过"**。按 §16 最终建议（保持 day）理解。 |
| 数字/阈值漂移 | 测试 545/897/922/927/937；`recall.minSim` 0.35（MEMORY-FIXES）vs 0.45（`recall/src/index.mjs:69`） | 都是时序漂移。**引用任何数字必须带 commit 或日期**。 |

---

## 5. 对 myco MVP：抄 / 改 / 别抄（含最小表结构）

### 5.0 前提：三个结构性差异

① **本轮禁调模型** → dsh-icl 全部成块质量依赖 LLM（episodes/people/lexicon 都是），myco 不存在；MVP 只能是"原文层+结构+检索+注入+规则化整理"，LLM 口子接口化不实现。② **myco 是多会话框架** → dsh-icl 的"一台机器一个实例只指一个会话"从未强制（B5），第一轮 5 分钟测试就因此作废；myco 必须从第一天把 session 放进数据模型。③ **myco 一切皆插件** → 记忆应是"服务契约 + 若干小插件"（存储原语/原文源/检索/注入/整理），不是一个 10k 行大插件。

### 5.1 直接抄（纯赚）

| 抄什么 | 为什么 | 形态 |
|---|---|---|
| **SQLite 三表 + 注册式 namespace** | 零依赖、单文件；namespace 让"记忆"与"账本/游标/日志"共用原语而不互相污染；`recallable` 让内部数据天然不进检索 | `ns`/`item`/`vec`，字段照抄 |
| **`(ns,key)` 唯一索引 → 带 key 才 upsert** | 一条 DDL 就把"幂等"与"追加"分开；不幂等的写用 `key=NULL` 显式表达 | 同左 + 文档写明哪些写必须带 key |
| **原文层 source 分类** | N1：不做这个，之后所有记忆都被 agent 自己的话污染；做了"该记什么"立刻可判定 | 一行枚举 + 一个纯函数 + 单测 |
| **eager 最小注入 + 诚实话术** | 200 字/2 条/"仅供参考"/低置信只说"可能有"/三种"没有" | 一个 `onTurn` 钩子 + 阈值配置 |

### 5.2 改一改再抄

| dsh-icl | myco 改法 | 理由 |
|---|---|---|
| 以"天"为整理单位 + `alreadyRan(day,job)` | **会话/空闲边界 + 消费游标**，不引入"天" | N11/N12：天是错抽象，同日再唤醒就丢内容 |
| 夜间 7 job | **1 个 job `consolidate`（原文→块）**；cleanup/index 作尾步骤 | 收益集中在 wash+episodes+index；其余净收益未证且带污染 |
| 全库打分 + `scanLimit 5000` | 保留公式，**去掉 5000 硬窗口**，用 ns 分区 + `(ns,created_at)` 索引做候选集 | 个人库数年是多会话下会更快爆掉的经验值 |
| `anchor = lastRecalledAt ?? createdAt` | `anchor = max(lastRecalledAt, updatedAt, createdAt)` + 独立 `importance` | N6 |
| 纯文本一段渲染 | **标题行 + 正文**两级：eager 只注入标题，recall 返回正文 | 预算/缓存友好，给模型"可再查"的层级感 |
| 段分区（segment） | **抄不变量与单测，不抄切换决定**；分区正确性作内部约束，不为分区改块粒度 | N12：他们 A/B 不通过 |
| 记忆自知 world 行 | **极简版**：只报"经历 N 段（时间范围）；上次整理 <时间>"，不报"未结承诺 N" | N10：不可查询的计数直接诱发错误结论 |

### 5.3 明确别抄

typed 记忆类型 + validity 双时间轴 + 写时仲裁（N9，无模型根本跑不起来）｜entity/relation 图（live 16 entity 里 9 个 stub、18 条边）｜"梦/睡眠/夜间"语义与 `dayCutHour`（§2.5）｜LLM rerank（每轮一次调用）｜评测工具面继续膨胀（23 个工具、大量 flag、语义 judge；`MEMORY-SCOPE-V1.md §5-9` 自己列为红线）｜重建/热迁移/回滚工具链（MVP 直接删库重建）｜主动召回/注意力 L2/L3/预算/seen 水位（myco 还没有"有人在等她回消息"）｜记忆可视化/云端同步/多模态。

### 5.4 MVP 的 20%（按落地顺序）

1. **数据地基**（无模型，1 天）：7 张表 + `put/query/search/index/boost/remove` + `(ns,key)` 唯一索引。验收：带 key=upsert、无 key=追加、改 text 清向量。
2. **原文层 + 来源分类**（1 天，最高杠杆）：会话事件→`source_row`（role/source/channel/speaker/text/at/seq）；`inferSource()` 纯函数；原文层只读永不改写。验收：100 条真假混合事件，`tool/env/agent_internal` 一条都不进"可形成记忆"集合。
3. **检索+打分**（0.5 天）：照抄公式；无嵌入退 bigram；decay 用 max 锚点。验收：10 条手写 query 命中 20 条 item，无关 query 返回空。
4. **注入**（0.5 天）：`onTurn` ≤2 条/≤200 字/"仅供参考"/低置信/"三种没有"。验收：cache 纪律单测（system 段字节不变，注入只追加）。
5. **规则化整理**（1 天）：单 job 把未消费 `source_row` 按"时间间隔>30min 或话题切换"切块，模板摘要（首句/末句/关键词）写 block，evidence 全挂。验收：一天 1–5 块、每块可回溯到 row。
6. **评测**（0.5 天，与 1 并行）：一个 fixture（3 天合成 + 1 天真实）+ 4 指标（anyHit@5/missRate/falseInjectionRate/provenanceRate）+ 一条命令 + exit 3。

**推迟清单（写进文档防偷加）**：LLM 成块、typed 分类、entity/relation、注意力/主动性、lexicon、多模态、记忆可视化、云端同步。

### 5.5 最小表结构草案（SQLite）

```sql
CREATE TABLE ns (                      -- 命名空间：所有"层"共用一套原语
  id TEXT PRIMARY KEY,                 -- 'episode'|'person'|'cursor'|'job-run'|'day'
  label TEXT NOT NULL,
  recallable INTEGER NOT NULL DEFAULT 1,
  half_life_days REAL NOT NULL DEFAULT 30,
  schema_version INTEGER NOT NULL DEFAULT 1,
  owner TEXT, created_at INTEGER NOT NULL
);

CREATE TABLE item (                    -- 唯一真实源：一切记忆行
  id INTEGER PRIMARY KEY,
  ns TEXT NOT NULL,
  kind TEXT NOT NULL,                  -- 'block'|'note'|'cursor'|'run'|'log'
  key TEXT,                            -- 非空=幂等 upsert；NULL=每次追加
  text TEXT NOT NULL,                  -- 标题/摘要（渲染给模型的那一行）
  data TEXT,                           -- JSON：body/evidence/people/tags/annotations
  weight REAL NOT NULL DEFAULT 1,
  recall_count INTEGER NOT NULL DEFAULT 0,
  last_recalled_at INTEGER,
  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX ux_item_key ON item(ns, key) WHERE key IS NOT NULL;
CREATE INDEX ix_item_ns_time ON item(ns, created_at);
CREATE TABLE vec (item_id INTEGER PRIMARY KEY, dim INTEGER NOT NULL, vec BLOB NOT NULL);

CREATE TABLE source_row (              -- 原文层（只读）
  id TEXT PRIMARY KEY,                 -- '<sessionId>:<seq>'，稳定不可变
  session_id TEXT NOT NULL, seq INTEGER NOT NULL, at INTEGER NOT NULL,
  role TEXT NOT NULL,                  -- user|assistant|tool|env|memory
  source TEXT NOT NULL,                -- human|agent_visible|agent_internal|tool|env|memory
  channel TEXT,                        -- 'web'|'qq:private:<id>'
  speaker TEXT,                        -- JSON {kind,value,name}
  text TEXT NOT NULL
);
CREATE INDEX ix_row_session_seq ON source_row(session_id, seq);

CREATE TABLE evidence (                -- 可 SQL 校验，不是 JSON 里的一坨
  item_id INTEGER NOT NULL, row_id TEXT NOT NULL,
  quote TEXT,                          -- 必须能在 source_row.text 里逐字找到
  at INTEGER, ordinal INTEGER,
  PRIMARY KEY (item_id, row_id)
);

CREATE TABLE job_run (                 -- 三态账本；attempt 区分两次执行
  id TEXT PRIMARY KEY,                 -- 'run:<segmentId>:<job>'|'fail:…'|'skip:…'
  segment_id TEXT, job TEXT NOT NULL, status TEXT NOT NULL,
  attempt TEXT, started_at INTEGER, ended_at INTEGER,
  detail TEXT                          -- JSON {counts,error,missing,skipped}
);

CREATE TABLE cursor (                  -- 唯一分区事实源；整理只能通过它推进
  name TEXT PRIMARY KEY,               -- 'consume'|'recall:<sessionId>'
  value TEXT NOT NULL                  -- JSON {perSession,lastCutAt,lastItemId}
);
```

episode 的 `item.data`（照抄 v3，去掉 typed 生命周期）：

```jsonc
{
  "body": "第一人称完整叙述（长度由内容决定，不设配额）",
  "evidence": [{ "type": "row", "id": "<session>:<seq>", "quote": "…", "at": 1793 }],
  "provisional": false,                          // quote 校验失败→true，注入时标 [存疑]
  "day": "2026-11-25",                           // 只作参考日期
  "timeStart": 1793, "timeEnd": 1794,            // 真实区间，检索按区间重叠加权
  "people": ["person:qq:123"],
  "tags": ["b站"],
  "annotations": { "facts": [], "preferences": [], "commitments": [] },  // 只读展示
  "superseded": false, "supersededBy": null,
  "segmentId": "seg:…", "mergedFrom": []
}
```

### 5.6 eval 先行还是功能先行

**结论：口径先行，功能只走一条线，不做完整 eval 平台。** 先写 fixture schema + 4 指标 + 一条命令（0.5 天），再写第二个插件；之后每个改动都带"改前/改后"两行数字。但不要照抄 23 个 eval 工具与语义 judge——`MEMORY-SCOPE-V1.md §2.2` 自己承认"评测工具面已很大，继续扩会变成评测开发而非产品开发"。判据：**一条指标如果不能在一个下午内被人工抽审 10 条样本复核，就不该进 gate。**

---

## 6. 未解问题（含倾向）

| # | 问题 | 双方证据 | 我的倾向 |
|---|---|---|---|
| Q1 | 块要多大？ | v3 叙事完整但 focus 细节 r5 87.2 vs v2 93.4；v2 精确但 199 条/148 字无叙事 | **块 + 段索引**：块返回模型、段只做内部定位（他们已验证 live 94.4→100）。不回到原子事实。 |
| Q2 | 承诺/偏好要不要生命周期？ | N10 证明"无生命周期+状态计数"会直接误导；v2 完整 typed+validity 是过度设计 | **折中**：annotations 每条加 `status: open\|done\|stale` 与 `due?`，**只在用户问起时展示**，不做主动提醒/独立召回。 |
| Q3 | 要 LLM judge 吗？ | adequacy judge 把 falseInjection 从 100% 修正到 0（重要），但样本小、偏严、口径不稳 | **不进 gate**，只做离线抽审工具（人工看 10 条）；gate 只用确定性指标。 |
| Q4 | 什么时候整理？ | 夜间批处理省钱但"天"是错抽象（N11/N12）；段分区又没通过 A/B | **会话结束 + 空闲触发 + 消费游标**，一条 job。不要固定钟点，不要"梦"。 |
| Q5 | 注入 200 字够吗？ | 他们 25 次 eager 注入 44、命中 73，没得出"不够"；但 live recall 只 1 次说明模型不会主动查 | **200 字 + 标题层级**：标题行让模型知道"有这件事，可 recall 细节"。若 recall 调用率仍近 0 再提高注入。 |
| Q6 | 多会话怎么隔离？ | dsh-icl 从未强制单会话（B5），第一轮因此作废 | **全局记忆 + scope 字段**（`data.scope={owner,conv}`），检索默认全局、`conv` 可选过滤；`cursor` 必须 per-session。 |
| Q7 | 要不要 person/entity？ | N5 说没主键就污染；但 entity 图一半 stub | **MVP 只做 alias**（`aliases` + `ids` JSON 两列），不上 entity namespace；等出现真实"同人拆块"再立项。 |
| Q8 | 禁模型下记忆层还能提供什么价值？ | 所有质量提升都来自 LLM 成块；但 N1/N2/N7 表明最大失败也在写入侧与可观测性，都不需要模型 | **价值主张改成"可信的原文 + 可检索的结构 + 诚实的注入"**，而不是"她会记得"。先把"记不住时能诚实说没记住"做对。 |
| Q9 | 文档怎么写才不变成负债？ | N18：22 处文档≠代码、5 份自标过期、判断成本高于重读代码 | **一个事实一个家**（表结构只在 schema 文件、指标只在 eval spec、阈值只在 config），文档只放"为什么"；数字必须带日期/commit。 |

---

### 附：关键坐标（复核用）

存储/检索 `memory/src/index.mjs:26-57`（DDL）、`:187-208`（put）、`:277-368`（search）、`:371-377`（boost）｜被动召回 `eager-recall/src/index.mjs:18-52,66-147`｜主动召回 `recall/src/index.mjs:54-86,146-231,240-276`｜整理 `dream/src/index.mjs:25-28,990-1100,1122-1273,1694-2110,2663-2903,3123`｜能力表 `packages/core/src/memory-capabilities.mjs:21-48`｜评测 `tools/memory-eval.mjs:32-36,169-202`、`tools/memory-eval/metrics.mjs:261-278`、`tools/fixtures/memory/schemas/fixture.schema.json`｜缓存 `docs/CACHE-MODEL.md §0,§5.1,§5.4`、`docs/PROMPT-CONTRACT.md §1,§3`｜注意力 `docs/ATTENTION-RESEARCH.md §1.2,§5.4,§10`｜复盘 `docs/OVERVIEW.md §4-6`、`docs/MEMORY-DIAGNOSIS.md §1`、`docs/MEMORY-COMPONENT-AUDIT.md §3`、`docs/MEMORY-INCIDENT-2026-11-13-COMMITMENT.md`

# Agent vs 陪伴：记忆调研（2026-11-26）

> 目标：为 myco「同一存储 + domain 标签 + 各自写入/召回 + 必要时跨域」的设想收集社区/论文/开源证据，
> 输出可落地的设计约束与候选架构。**不写代码，不调任何模型服务**。
> 证据口径：一手论文/官方文档 > 官方博客/文档 > 第三方实测 > 社区项目 README。
> 本地既有结论（[icl-memory.md](icl-memory.md)、[60-mvp-closed-loop-plan.md](../design/60-mvp-closed-loop-plan.md) §4）作为一手对照，不重复其细节。

---

## 0. 一页结论

1. **陪伴记忆与 agent 记忆的差异不在存储 schema，在四件事：写什么（筛选）、忘不忘（衰减/固化）、何时想起（触发与时机）、信谁（来源与置信度）。** 同一份 schema 完全可以承载两者，但**策略必须按 domain 分开**；只加一个 `domain` 过滤字段的"单库多标签"会退化成"两套记忆用同一套规则"。
2. **单库多标签有强证据支撑**：Mem0 的 `user_id/agent_id/app_id/run_id` 多级 scope、LangMem 的 namespace 元组、SillyTavern 的 global/character/persona/chat 四级 lorebook、Letta 的 shared memory blocks、AWS AgentCore 的 namespacePath 分层，都是"一份存储 + 作用域"的工程化形态。
3. **但跨域不是靠"再查一次"**：MemXD（NeurIPS 2026）证明纯语义/关键词检索在跨域偏好迁移上会漏（把"域无关行为特征"与"域内事实"分开存、用 hook 桥接后 coverage 60%、比最强基线 +18.5pt；accuracy 最多 +21pt）。**跨域召回需要单独的表示层与闸门，不是加个 `OR domain='work'`。**
4. **陪伴侧最值钱的工程经验来自社区而非论文**：Character.AI 的 Story Memory / Facts / Pin / Memory Usage 四件套、Kindroid/Nomi 的"用户可编辑固定事实"、SillyTavern 的"关键词激活 + 预算 + sticky/cooldown"、中文项目 memory-trigger 的"双源信任（用户明说 > 自己推断，推断先 pending）+ 承诺账本 + 情绪触发召回 + 想起≠提及"。这些解决的是论文 benchmark 测不到的问题：**被记得的感受**。
5. **可信度治理是两条线的公共刚需**：来源分类（human/agent/tool/env）+ 证据可逐字校验 + 推断先挂起 + 纠正即降权 + supersede 链而非静默删除。AgentPoison 用 **0.1% 投毒记忆**把攻击成功率推到 ~82%；ICL 实测 live raw 里 92% 是 assistant/tool 自己的话——**不做来源分类，记忆会被 agent 自己的话污染**。
6. **推荐架构：A（单库多标签）做数据层 + C（策略层）做行为层，B（分库+桥接）只作为未来的物理隔离能力保留。** 数据一份（SQLite，事件日志派生 + 用户策展覆盖），domain 是策略主键而非过滤条件；跨域默认关闭、按"共享层 + 显式工具召回"开闸。
7. **myco I2（记忆）最小闭环**：原文层 + 显式记住 + 用户可见记忆面板（看/删/关/导出）+ 混合检索（FTS/bigram/可选向量）+ 时间衰减与 core 钉死 + eager 注入 ≤2 条/≤200 字 + 诚实话术 + 6 指标 fixture。**不做** LLM 自动抽取、反思/做梦、实体图、跨域自动召回、云端向量库、同步。
8. **最大的三个风险**：写入污染（尤其是"agent 自己的话变成事实"）；两域策略分叉导致"两套记忆打架"；评测只测 fact recall 导致 Goodhart（检索指标变好、被记得的感受变差）。

---

## 1. 差异对照表

| 维度 | 陪伴型记忆（chat） | Agent 型记忆（work） | 对 myco 的含义 |
|---|---|---|---|
| 记忆对象 | 经历/关系/情绪/承诺/共同叙事 | 任务/事实/偏好/项目状态/工具结果 | kind 取并集，但主单位不同：块 vs 条目 |
| 主单位与形态 | episode 块、第一人称叙述、可含"她自己的感受" | 原子事实、结构化状态、第三人称、可校验 | schema 用 `kind` 区分，content 允许叙事体 |
| 时间语义 | 主观时间线（纪念日、"上次"、关系阶段） | 绝对时间 + 有效期 + 状态迁移 | 必须双时间（事件时间/记录时间），见 Zep |
| 写入频率 | 每轮都有情绪/关系信号，但值得记的很少 | 事件驱动（任务边界/工具结果），突发 | 陪伴写入靠"筛选"，agent 写入靠"挂钩" |
| 写入主导者 | 用户明说 > 模型推断；用户可编辑 | 系统/工具确定性写入为主 | provenance 分级，见 §4 |
| 遗忘策略 | **必须会忘**（否则像硬盘）；核心永不褪色 | 关键状态不该忘，只压缩/归档 | 同一套衰减机制，参数按 domain/kind 配 |
| 召回目标 | 此刻该想起什么（时机、情绪、氛围） | 完成任务需要什么（显式、可复现） | eager（陪伴）vs 工具召回（agent） |
| 召回失败形态 | "她忘了我"——体验失败 | "用了错的信息"——任务失败 | 失败语义与话术要分开设计 |
| 可信度/冲突 | 用户明说权威最高；推断先挂起；纠错即降权 | 来源+时间戳+证据链；新信息 supersede 旧 | 冲突规则必须写死，见 §5.2 |
| 隐私敏感度 | 极高（亲密关系、身体、秘密、情绪） | 高（工作数据、凭据、项目） | 本地优先 + 分域可见 + 可删可关 |
| 评测 | 主观/长期（2 周召回、关系连贯、人格一致） | 客观/可判分（LongMemEval/MemBench） | 两套指标，不能互相替代 |
| 注入预算 | 少而准（≤2 条/≤200 字），宁缺毋滥 | 按任务取，可多，需可解释 | 预算做成 policy profile |

**一句话**：差异是"策略差异"，不是"存储差异"。这也是为什么三种架构里 **A+C 组合** 最贴合本设想。

---

## 2. 来源清单（20 条，逐条）

> 覆盖：记忆层实现（S1–S7）、跨域/多域（S9/S17）、陪伴与角色记忆（S10–S13、S18）、经典原型与安全评测（S14–S16、S20）、内部对照（S19）。
> 每条给"解决什么问题 / 数据结构与写读方式 / 可借鉴 / 局限"。抓不到正文的已标注证据级别。

### S1. Mem0：生产级抽取-更新记忆层
- 链接：[Mem0 论文 arXiv:2504.19413](https://arxiv.org/abs/2504.19413)、[How Mem0 Works](https://docs.mem0.ai/core-concepts/how-it-works)、[Entity-Scoped Memory](https://mem0.mintlify.app/platform/features/entity-scoped-memory)
- 解决：跨会话事实记忆 + 降 token/延迟（论文称 LoCoMo 上相对 OpenAI 记忆 +26%，p95 延迟 −91%，token −90%）。
- 结构：消息 → LLM 抽取事实 → 与相似现有记忆比对做 **ADD / UPDATE / DELETE / NOOP 四操作**；向量存储，可选图记忆；检索走 `search`，由应用决定注入。
- 作用域：`user_id / agent_id / app_id / run_id` + metadata 多级隔离；文档明确"记忆永远绑在传入的 scope 上，不绑消息里的 name"。
- 可借鉴：**四操作 = 写时冲突解决的最小集**；多级 scope 就是"标签化多租户"的工业答案；`run_id` 可当"未确认会话"的暂定域（其博客亦建议"身份未解析前 scope 到 run_id 并视作 tentative"）。
- 局限：抽取质量依赖 LLM；原子条目不适合叙事（myco 前代 v2 已付学费，见 [icl-memory.md](icl-memory.md) N9）；基准多为厂商自测。

### S2. Letta / MemGPT + sleep-time compute：分层记忆与异步巩固
- 链接：[MemGPT arXiv:2310.08560](https://arxiv.org/abs/2310.08560)、[Sleep-time Compute](https://www.letta.com/blog/sleep-time-compute)、[Memory blocks 文档](https://docs.letta.com/v1-sdk/memory/memory-blocks)
- 解决：上下文窗口有限下的"虚拟上下文"；把记忆管理从对话关键路径挪走。
- 结构：core memory = 可编辑 memory blocks（带 label/限额，常驻上下文）；recall memory = 会话历史检索；archival memory = 外部向量存储；模型通过工具自编辑记忆、用中断控制流程。
- sleep-time：Letta 0.7 新增的 sleep-time 型 agent 里，主 agent 不持有 core memory 编辑工具，改由**异步 sleep-time agent** 在空闲时整理双方 blocks；两个 agent 可用不同模型（整理用更强模型）。
- 可借鉴：**"写入/巩固不在关键路径"**；block 的 label + 限额就是天然的 domain/槽位；shared blocks 支持跨 agent 共享（跨域读的一种实现）。
- 局限：依赖强模型做记忆管理；block 组织需要人设计，自动演化仍不稳；benchmark 主要是 DMR/LongMemEval 类问答。

### S3. Zep / Graphiti：双时间知识图 + 冲突失效
- 链接：[Zep arXiv:2501.13956](https://arxiv.org/abs/2501.13956)（[HTML 全文](https://arxiv.org/html/2501.13956v1)）
- 解决：企业级"会话 + 业务数据"持续演化下的时序推理；DMR 94.8% vs MemGPT 93.4%，LongMemEval 最高 +18.5%，延迟 −90%（平均上下文 1.6k vs 115k token）。
- 结构：三层子图（episode 原文层 → 语义实体/事实 → 社区摘要）；边带四个时间戳（`t_valid/t_invalid` 事件时间，`t'_created/t'_expired` 记录时间）；新事实与相关旧边比对，**矛盾则置旧边 `t_invalid`，不删除**。
- 召回：cosine + BM25 + 图 BFS 三路召回，RRF/MMR/图距离/交叉编码器重排。
- 可借鉴：**原文层非损失保留 + 派生层可失效**，这是"记忆可重建、可审计"的最佳范式；supersede 用时间戳而非删除；混合检索比单向量稳。
- 局限：图构建成本高、依赖 LLM 抽取质量；实体消歧错误会传播；偏"事实"场景，情绪/关系维度无表达。

### S4. LangMem：按记忆类型分工 + 命名空间
- 链接：[LangMem SDK 发布博客](https://www.langchain.com/blog/langmem-sdk-launch)
- 解决：给 agent 提供 semantic（事实）/episodic（经历）/procedural（行为规则）三类长期记忆的 SDK。
- 结构：`create_memory_manager` 抽取事实（可传 `existing` 做更新）；`create_prompt_optimizer` 用反思/梯度类算法改提示词 = procedural memory；短期记忆交给 LangGraph checkpoint。
- 作用域：博客明确"所有记忆都有 namespace，最常见是带 user_id 防串；也可按 app route、用户、团队、全局共享"。recall 触发条件被列为设计三问之一（另两问：什么该学、该追踪什么知识）。
- 可借鉴：**先问"要学哪种能力"再选记忆类型**；procedural memory 对陪伴=人格规则、对 agent=技能，落在提示词而非数据库；namespace 元组是"标签化"的直接实现。
- 局限：官方称 episodic 尚不提供工具；抽取仍是 LLM 依赖；namespace 只是作用域，不含"跨域桥接"语义。

### S5. A-MEM：Zettelkasten 式自组织记忆
- 链接：[A-MEM arXiv:2502.12110](https://arxiv.org/abs/2502.12110)（NeurIPS 2025）
- 解决：记忆只存不组织、无法跨任务自适应的问题。
- 结构：每条新记忆生成结构化 note（上下文描述、关键词、标签），系统检索历史记忆建立链接；**新记忆可触发旧记忆的属性/上下文更新（memory evolution）**。
- 可借鉴：链接与演化让"记忆网络"而非扁平库；标签 + 关键词即天然的检索维度。
- 局限：每次写入都要 LLM 决策，成本高；演化会改写旧记忆，**与"不可静默改写用户确认事实"冲突**（需 provenance/锁）。

### S6. Memobase：用户画像槽位 + 上下文感知检索
- 链接：[Profile Fundamentals](https://docs.memobase.io/features/profile/profile)、[Profile Search](https://docs.memobase.io/features/profile/profile_search)、[Best Practices](https://docs.memobase.io/practices/tips)
- 解决：面向"用户画像"而非通用文档的记忆后端，常用于角色扮演/陪伴类应用。
- 结构：chat blob 按 token 阈值（默认 1024）与时间（默认 3600s）flush；LLM 把聊天更新进**profile slots**（basic_info/education + `additional_user_profiles` 自定义槽）；检索用 LLM 做特征分析过滤，而非纯向量。
- 实践要点：官方建议"应用 user id → memobase user id 一对多"，**角色扮演时按角色拆 user 以隔离不同角色的记忆**。
- 可借鉴：**槽位化画像 + 分段记忆**正是"用户/关系层"与"域层"分离；"一对多 user id"就是 domain 标签的应用层替代。
- 局限：结构化槽位能表达事实，表达不了叙事与情绪；LLM 检索增加延迟与成本；槽位更新会覆盖历史（需自带版本）。

### S7. MemOS：把记忆当系统资源（MemCube）
- 链接：[MemOS arXiv:2507.03724](https://arxiv.org/abs/2507.03724)、中文解读：[腾讯云开发者社区](https://cloud.tencent.com.cn/developer/article/2540464)（二手）、[InfoQ](https://www.infoq.cn/article/RivZJwpYltVDJX5imMUX)
- 解决：多来源异构记忆（明文/KV/参数）的统一调度与治理。
- 结构：`MemCube` 为最小单元，元数据含**时间戳、调用计数、版本链、ACL、访问者、敏感性标签、上下文指纹**；三类记忆 Plaintext/Activation/Parameter 可互转；生命周期 `Generated → Activated → Merged/Replaced → Archived → Purged`；MemScheduler 按热度/形态/合规/成本选择注入。
- 可借鉴：**元数据即治理**（本报告 schema 的 privacy/status/links 字段直接对应）；"生命周期状态机"比布尔删除好；"敏感信息只注入所属 session"是可落地的隐私规则。
- 局限：系统庞大、工程化成本高；论文指标与实现多为作者自测；中文解读为二手，细节需回原文核对。

### S8. Hindsight：事实/信念分离的四网络记忆
- 链接：[Hindsight ACL 2026 demo](https://aclanthology.org/2026.acl-demo.27/)
- 解决：区分"agent 知道的事实"与"agent 相信的东西"，让主观信念可演化、可置信度化。
- 结构：world / experience / observation / opinion 四个逻辑网络；retain / recall / reflect 三操作；检索并行组合向量、关键词、图遍历、时间过滤（PostgreSQL + pgvector）；opinion 带置信度；MIT 开源。
- 指标：LongMemEval 83.6%、LoCoMo 83.2%（20B 开源模型），Gemini-3 Pro 下 LongMemEval 91.4%。
- 可借鉴：**opinion 网络可映射"她对他的看法/关系判断"**——这是陪伴记忆里最难又最必要的一层；fact-belief 分离正是"防模型脑补变成事实"的结构解。
- 局限：self-reported，缺独立复现；四网络如何在产品里不变成四份重复数据仍待验证。

### S9. MemXD：跨域个性化必须显式桥接
- 链接：[MemXD (NeurIPS 2026 Workshop)](https://neurips.cc/virtual/2026/loc/sydney/161939)
- 解决：现有记忆系统把偏好留在"表达它的那个域"，跨域查询时语义/关键词检索召不回来。
- 结构：存储时把**域无关的行为特征（latent traits）**从事实记忆中蒸馏出来，给每条 trait 配"问题钩子"；检索时生成事实/潜变量两类探针，用钩子做向量匹配。
- 结果：CrossMemBench 上跨域召回覆盖 60.0%、准确率最高 35.0%（比最强基线 +18.5 / +21pt）；PersonaMem 跨场景 +7pt。
- 可借鉴：**"共享层（traits/关系/偏好）"应与"域内事实"分开存**，跨域召回走 trait→hook 的桥；这直接支持本报告推荐的三层结构。
- 局限：workshop 论文、匿名代码；CrossMemBench 未经本报告独立核验；hook 生成依赖 LLM。

### S10. SillyTavern World Info：社区最成熟的"作用域 + 激活 + 预算"工程
- 链接：[World Info 文档](https://docs.sillytavern.app/usage/core-concepts/worldinfo/)
- 解决：角色扮演中"只在该想起来的时候注入背景"，避免把设定书全塞进上下文。
- 结构：条目 = 关键词（支持正则）+ 可选过滤（AND ANY/ALL、NOT ANY/ALL）+ 内容 + 插入顺序/位置（@D 深度、A/N、Outlet）+ 概率 + inclusion group；**四级来源作用域**：chat lore / persona lore / character lore / global lore；有 token 预算（Context %/Budget）；支持递归激活（条目互相触发）；Timed Effects（sticky/cooldown/delay 按消息数）；可用向量检索替代关键词。
- 可借鉴：**这是"单库 + scope + 每域策略"的活样板**：作用域决定可见性，激活条件决定召回，预算决定注入量，sticky/cooldown 解决"别反复提"；中文场景要关 whole-word 匹配。
- 局限：用户手工维护、无自动抽取；关键词激活脆弱、无时间衰减；无 provenance/冲突解决；纯 prompt 注入，不落"记忆库"。

### S11. Character.AI：产品级陪伴记忆四件套
- 链接：[Smarter Memory for Smarter Chats](https://blog.character.ai/memory/)、[2025-04 社区更新（Auto Memories 可编辑）](https://support.character.ai/hc/en-us/articles/36429196456475-Community-Update-April-2025)、[FAQ 汇总](https://flowith.io/blog/character-ai-faq-memory-content-filters-safety-explained/)
- 说明：正文页抓取失败（两次），以下据检索摘要与二手 FAQ，**标记为待核**。
- 形态：**Story Memory**（故事/关键桥段，可长按消息 Pin 钉入）+ **Facts**（自动从对话捕获、2025 起可编辑）+ **Memory Usage**（用户可见记忆使用情况）+ 后台整理旧上下文（"tidies older context in the background"）。
- 可借鉴：**"自动捕获 + 用户可编辑 + 可见用量 + 手动钉住"**四件套是陪伴产品的最小可信组合；自动与手动并存，且手动优先级更高。
- 局限：机制细节不公开；社区实测显示免费档仍会遗忘/编造（见 S13），说明"有记忆功能"≠"记得对"。

### S12. Nomi / Kindroid / Replika：三层记忆 + 可编辑固定事实（社区实测）
- 链接：[Nomi 官方 wiki：memory & knowledge](https://wiki.nomi.ai/index.php?oldid=1134&title=How_does_Nomi_memory_and_knowledge_work%3F)、[CrushCritic 五平台两周实测](https://crushcritic.com/understanding-context-windows-and-memory-in-ai-companions/)
- Nomi：短/中/长期三层记忆；Backstory+ / Identity Core / Mind Maps 承载身份与关系；官方明确"信息在记忆里 ≠ 检索得到"（承认召回失败是常态）。
- Kindroid：key memories/backstory 用户可编辑，"写进去的永不丢"；Replika：Memory 面板 + Diary 双面。
- 实测（单评测者、两个时间点）：Day3/Day14 五天种子事实召回，Nomi 5/5 与 5/5，Kindroid 5/5 与 4/5，Character.AI 免费档 3/5 与 1/5（并把姐姐名字编成 "Sarah"）。
- 可借鉴：**"固定事实（pin）永不衰减 + 摘要/检索兜底 + 用户可改"是被市场验证的三件套**；两周种子事实召回是最便宜的可用性验收法。
- 局限：单评测者、n 小、站点有联盟营销，**不能当结论级证据**；只能当"设计模式存在且被采用"的旁证。

### S13. Memory Trigger：中文社区"伴侣型记忆"最完整的开源实践
- 链接：[GitHub riisovo/memory-trigger](https://github.com/riisovo/memory-trigger)（经 gh-proxy 抓取 README）
- 解决：人机恋/长期陪伴里 14 类真实痛点：记不住、两套记忆打架、记忆垃圾堆、记忆不归用户、脑补被当成事实、纠正不改、承诺不兑现、情绪接不住。
- 结构（本地 `memory.json`，纯 Python 零依赖，23 个 MCP 工具）：`core=true` 钉死永不衰减；日常记忆走**艾宾浩斯半衰期约 138 天**；**双源信任**：`user_explicit`（用户明说，最高权威、永不自动改）> `self_inferred`（先 `pending` 不进检索，被再次确认才转正）；`deny`（用户否认两次→降权转 pending）；`expires_at` 临时约定；承诺账本 `promise + deadline` 带闹钟与"逾期三选一"执行；`emotion_tags` + `pre_llm_call` 钩子做**情绪触发主动召回**（daemon 常驻时 3–8ms）；"**想起 ≠ 提及**"（recall 不加权、只有真被提及才续命）；写入重合 >0.7 合并、>0.35 交用户裁决，**不静默作废旧记忆**；自我进化走**举证闸门**（每条结论 ≥2 条真实记忆支撑 + 触发条件/行为改变/失效条件三字段，每轮最多 2 条）。
- 可借鉴：几乎逐条命中本报告 §4/§5 需要的字段与规则：provenance 分级、pending 状态、承诺生命周期、衰减参数、情绪触发、不静默作废、可证伪的反思。**建议作为 myco 陪伴域写入/冲突策略的主要参考实现**。
- 局限：个人项目、非学术评测；`memory.json` 全量读写不适合大数据量；钩子注入依赖宿主能力；情感触发阈值未量化。

### S14. 记忆投毒与记忆幻觉（安全侧证据）
- 链接：[AgentPoison arXiv:2407.12784](https://arxiv.org/abs/2407.12784)（NeurIPS 2024）、[MEMEVOBENCH arXiv:2604.15774](https://browse-export.arxiv.org/pdf/2604.15774)、中文：[freebuf「记忆投毒：AI Agent 的持久后门」](https://www.freebuf.com/articles/ai-security/499017.html)
- AgentPoison：仅 **0.1% 投毒比例**即可在 RAG/长期记忆里植入后门，端到端攻击成功率 ~82%。
- MEMEVOBENCH：专门测"记忆错误演化"（误导性记忆注入 7 域 36 风险类型 + 噪声工具返回 20 环境 8 风险类型）。
- 另有"自污染/归因洗白"研究指出：agent 整合自己的笔记时会**洗掉来源**，使"外部内容变成 agent 自己的信念"（[Rank-Bounded Memory](https://zenodo.org/records/22685081)）。
- 可借鉴：写入必须带来源与不可伪造的 origin；agent 自己生成的结论不得回写为事实；跨域/跨来源合并要有审计。
- 局限：攻击模型多针对 RAG/工具 agent，陪伴场景的攻击面不同（更多是"被诱导记住错误关系"），需自行建模。

### S15. 评测基准：LongMemEval / LoCoMo / MemBench
- 链接：[LongMemEval arXiv:2410.10813](https://arxiv.org/abs/2410.10813)（ICLR 2025）、[LoCoMo arXiv:2402.17753](https://arxiv.org/abs/2402.17753)、[MemBench arXiv:2506.21605](https://arxiv.org/abs/2506.21605)（ACL 2025 findings）
- LongMemEval：500 题，考**信息抽取、多会话推理、时间推理、知识更新、拒答**五项；历史可达 ~115k token；商业助手/长上下文模型掉 ~30%；把记忆拆成 indexing/retrieval/reading 三阶段，给出 session 分解、事实增强 key、时间感知 query 扩写等优化。
- LoCoMo：平均 300 轮 / ~9K token / 最多 35 个 session 的超长期对话，评 QA、事件摘要、多模态对话生成；长上下文与 RAG 都明显落后人类。
- MemBench：区分**事实记忆 vs 反思记忆**、**参与 vs 观察**两种交互场景；指标覆盖 accuracy/recall/capacity/efficiency。
- 可借鉴：LongMemEval 的"知识更新 + 拒答"两栏就是 myco 最该抄的回归项；MemBench 的"反思记忆"最接近陪伴；LoCoMo 的多会话时间跨度适合做长期回归。
- 局限：都是问答/任务导向，**测不到"被记得的感受"、关系连贯、情绪得体**；LLM-as-judge 分数跨论文不可比；陪伴场景需自建 fixture。

### S16. 记忆操作分类学（写/召回管线的公共词表）
- 链接：[Rethinking Memory in LLM based Agents arXiv:2505.00675](https://arxiv.org/abs/2505.00675)
- 解决：把记忆研究收敛成可复用的操作原语。
- 结构：参数记忆 vs 上下文记忆；**六个核心操作：Consolidation / Updating / Indexing / Forgetting / Retrieval / Condensation**；四个主题：long-term、long-context、parametric modification、multi-source memory。
- 可借鉴：把 myco 记忆层按六操作切分接口（尤其 `Condensation` 与 `Forgetting` 常被漏掉）；"multi-source memory"正是 domain 问题的学术名字。
- 局限：综述不提供实现；操作之间边界（consolidate vs condense）在工程上仍模糊。

### S17. AWS AgentCore Memory：命名空间设计模式（多租户/多域）
- 链接：[Organizing Agents' memory at scale: namespace design patterns](https://aws.amazon.com/cn/blogs/machine-learning/organizing-agents-memory-at-scale-namespace-design-patterns-in-agentcore-memory/)（正文抓取被截断，仅据检索摘要）
- 结构：namespace / namespacePath 分层；先按 actor + session 隔离，再为多租户/企业加第三层边界。
- 可借鉴：**层次化 namespace 是"单存储 + 作用域"在云厂商侧的落地形态**；隔离与"有意共享"要分开设计（shared namespace 是显式动作）。
- 局限：厂商方案，细节未读全；不解决"跨域语义桥接"（那需要 S9 的表示层）。

### S18. 中文社区实践：自动总结 → 世界书（MemoryBooks / AutoSummarizer）
- 链接：[SillyTavern-MemoryBooks](https://github.com/aikohanasaki/sillytavern-memorybooks)（含中文说明）、[AutoSummarizer](https://github.com/EphemeralAlien/AutoSummarizer)（均只读到检索摘要，**未取正文**）
- 解决：超长角色扮演/聊天里手工维护世界书太累，用 AI 自动总结并把摘要写回 lorebook，实现"无限上下文"。
- 做法：斜杠命令 `/creatememory` 一键生成记忆条目并自动创建世界书条目；AutoSummarizer 提供"全自动总结"面板，把长对话摘要存入 Lorebook 后由关键词激活召回。
- 可借鉴：**"自动总结 → 结构化条目 → 关键词激活"是中文社区对陪伴长期记忆的主流低成本解法**，与 SillyTavern 的激活/预算机制天然配套；无需 embedding。
- 局限：扩展脚本、非产品级；摘要丢细节、条目膨胀后检索质量下降、用户管理负担重；证据为摘要级。

### S19. myco 内部一手证据（不是外部来源，但权重最高）
- [icl-memory.md](icl-memory.md)：v2 原子事实被用户否定（"破坏陪伴叙事"）；质量杠杆在写入侧；`source` 分类不做则记忆被 agent 自己的话污染（live raw 92% 是 assistant/tool）；静默失败 = "她想不起来"；缓存纪律：动态注入只能追加。
- [60-mvp-closed-loop-plan.md](../design/60-mvp-closed-loop-plan.md) §4：共享 schema/来源分类/存储，**不自动共享写入策略**；chat 叙事 vs work 任务片段，读写都要有 app 维度；跨 app 吸收必须有开关与可见 provenance。
- [20-decision-record.md](../design/20-decision-record.md) D3/D6：共享 `memory.db`，差异 = 读过滤/注入策略；三条不可逆决策（`(ns,key)` 唯一索引、`source_row` 分类、`data.scope={owner,conv}`）。

### S20. 经典原型：Generative Agents（反思）与 MemoryBank（遗忘曲线）
- 链接：[Generative Agents arXiv:2304.03442](https://arxiv.org/abs/2304.03442)、[MemoryBank arXiv:2305.10250](https://arxiv.org/abs/2305.10250)
- 解决：前者证明"完整记录经历 + 定期综合为更高层反思 + 动态检索"能让 agent 行为可信；后者把长期记忆用于**陪伴场景**（聊天机器人 SiliconFriend），并用艾宾浩斯遗忘曲线让 AI 按时间与相对重要性遗忘/强化。
- 结构：Generative Agents = 自然语言记忆流 → 反思（把碎片提升为高层结论）→ 检索供规划；MemoryBank = 记忆更新机制 + 按"时间流逝 × 重要性"调整保留强度。
- 可借鉴：**"反思"是碎片→高层结论的标准操作**（对应 myco 的整理/巩固阶段）；遗忘用"时间 × 重要性"参数化比固定 TTL 更像人；陪伴场景的学术先例。
- 局限：反思依赖 LLM、成本高且不可验证；MemoryBank 实验规模小、指标偏定性；遗忘曲线是启发式，不是认知科学定论（myco 不能把 138 天之类参数当真理）。

---

## 3. 三种架构对比与推荐

### 3.1 方案定义

| | A. 单库多标签 | B. 分库 + 桥接 | C. 单库 + 策略层 |
|---|---|---|---|
| 数据形态 | 一张表/一库，`domain` 字段过滤 | chat.db / work.db 各自独立，跨域走导出/桥接 | 一张表，`domain` 是策略主键 |
| 写路径 | 一套写入 + 标签 | 两套写入，各自演化 | 一套写入 + 按 domain 选 pipeline |
| 读路径 | `WHERE domain IN (...)` + 排序 | 各库自查，跨域合并去重 | 按 domain policy 选召回器与预算 |
| 隔离强度 | 逻辑隔离 | 物理隔离 | 逻辑隔离 + 策略隔离 |
| 冲突/去重 | 单点可做（强） | 跨库难（弱） | 单点可做（强） |
| 演进速度 | 快 | 各自快、合并慢 | 快，但策略需要治理 |
| 同步（D10 两 APK） | 一份 journal（简单） | 两份 journal + 桥（复杂） | 一份 journal（简单） |

### 3.2 各自的证据

- **A 的证据**：Mem0 多级 scope、LangMem namespace、SillyTavern 四级 lorebook、Letta shared blocks、AWS namespacePath——工业界默认"一份存储 + 作用域"，成本最低。myco 已拍板 D3"共享同一份 memory.db"。
- **B 的证据**：只在"需要硬隔离"时出现——不同用户/租户（AWS 多租户的第三层 namespace 边界）、不同信任边界（工作项目要给同事看）。**没有找到"同一用户聊天与工作必须分库"的证据**；反而社区失败案例（Memory Trigger 痛点 2）正是"两套记忆互相打架"。
- **C 的证据**：Character.AI（自动捕获 + 可编辑 + 后台整理）、Nomi 三层、Memobase 槽位 + 分段、Memory Trigger 双源信任——**同一存储内部按"记忆类型/来源"走不同规则**是产品级做法。学术侧 MemXD 说明"共享表示层"必须单独设计，否则跨域召不回。

### 3.3 推荐：A 做数据层 + C 做行为层；B 只作未来物理隔离能力

**理由**：
1. myco 的硬约束（离线、单机、同步友好、用户可见一份记忆）与 A 天然一致；分库会把"删除/导出/冲突"变成跨库编排，得不偿失。
2. 真正的需求——"各自写入/召回"——是策略问题，用 **domain policy profile** 表达（写入触发、抽取方式、半衰期、召回预算、注入形态、跨域读白名单），而不是用物理边界表达。
3. 跨域召回必须"显式闸门 + 表示层"：共享层（身份/关系/偏好/traits）+ 域内层（episode/task state），跨域读默认关闭，开启时带 provenance 与理由（对齐 [plan 60](../design/60-mvp-closed-loop-plan.md) §4）。
4. B 保留为**能力而非默认**：当出现"他人可读""项目交付""合规删除"需求时，导出为独立库/包；不改变日常路径。

**风险**：
- 策略分叉：两个 domain 写出同义记忆 → 需要"共享层单例 + 域内 supersede"规则与去重阈值。
- 策略泄漏：chat 的情绪记忆被注入 work（或反之）→ policy 是白名单不是黑名单；测试要覆盖"不该出现"。
- schema 过宽：为容纳两类形态把字段做成大 JSON，查询与索引退化 → 用 `kind` + 必要列，其余进 `data` JSON 但保留可索引列。
- 治理成本：policy 多了会没人懂 → policy 只允许 2 个内置 profile（chat/work）+ 显式 override，禁止用户手调全部参数。

**演化路径**：
v0 只做 chat（一套 policy，domain 字段先占位）→ v1 引入 `domain` + `provenance` + 用户可见面板 → v2 work 接入时只加 policy 与 kind，不改表 → v3 出现跨设备/多用户时再加 B 的导出/隔离能力。

---

## 4. 最小 schema

### 4.1 表（SQLite；与现有 [icl-memory.md §5.5](icl-memory.md) 七表对齐，增加 domain/provenance/状态机）

```sql
CREATE TABLE memory_item (
  id            TEXT PRIMARY KEY,          -- ULID：同步友好、可排序
  domain        TEXT NOT NULL,             -- 'chat' | 'work' | 'shared'（策略主键，非仅过滤）
  kind          TEXT NOT NULL,             -- 'episode'|'fact'|'preference'|'commitment'|'task_state'|'trait'|'note'
  content       TEXT NOT NULL,             -- 叙事块或事实陈述（陪伴用块，agent 用短陈述）
  summary       TEXT,                      -- 一行渲染（eager 注入用）；可空
  entities      TEXT,                      -- JSON 数组 [{id,kind,name}]，不建图也能过滤
  origin        TEXT NOT NULL,             -- 见 4.2 provenance 枚举
  confidence    REAL NOT NULL DEFAULT 0.5, -- 0..1；pending 状态由 status 表达
  sensitivity   TEXT NOT NULL DEFAULT 'normal', -- 'normal'|'private'|'secret'；决定能否注入/导出
  status        TEXT NOT NULL DEFAULT 'active', -- active|pending|superseded|archived|deleted
  pinned        INTEGER NOT NULL DEFAULT 0, -- core/钉死：不衰减
  half_life_d   REAL,                      -- NULL=按 domain profile 默认
  source_session TEXT,                     -- 来源会话 id
  source_seq     INTEGER,                  -- 来源事件 seq（可空）
  source_app     TEXT,                     -- 写入时的 app（chat/work），审计用
  valid_from    INTEGER,                   -- 事件时间（双时间之一）
  valid_to      INTEGER,                   -- NULL=至今
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL,
  last_recalled_at INTEGER,
  recall_count  INTEGER NOT NULL DEFAULT 0,
  data          TEXT                       -- JSON：evidence/annotations/extra
);
CREATE INDEX ix_item_domain_status ON memory_item(domain, status);
CREATE INDEX ix_item_kind_time ON memory_item(kind, valid_from);
CREATE INDEX ix_item_session ON memory_item(source_session);

CREATE TABLE memory_source (          -- 原文层（只读，从事件日志派生，可重建）
  row_id     TEXT PRIMARY KEY,        -- '<sessionId>:<seq>'
  session_id TEXT NOT NULL, seq INTEGER NOT NULL, at INTEGER NOT NULL,
  role       TEXT NOT NULL,           -- user|assistant|tool|env
  source     TEXT NOT NULL,           -- human|agent_visible|agent_internal|tool|env|memory
  text       TEXT NOT NULL
);

CREATE TABLE memory_evidence (        -- 可校验的引用，不是 JSON 里一坨
  item_id TEXT NOT NULL, row_id TEXT NOT NULL, quote TEXT,   -- quote 必须能在 source_row 原文逐字找到
  PRIMARY KEY (item_id, row_id)
);

CREATE TABLE memory_link (            -- supersede / merge / mention / contradicts
  from_id TEXT NOT NULL, to_id TEXT NOT NULL, kind TEXT NOT NULL, at INTEGER NOT NULL,
  PRIMARY KEY (from_id, to_id, kind)
);

CREATE TABLE memory_event (           -- append-only 决策日志（同步与重建的真源）
  id TEXT PRIMARY KEY, at INTEGER NOT NULL, op TEXT NOT NULL, -- upsert|supersede|pin|deny|forget|confirm
  item_id TEXT NOT NULL, actor TEXT NOT NULL, payload TEXT
);

CREATE TABLE domain_policy (          -- domain 即策略：一行一个 profile
  domain TEXT PRIMARY KEY, label TEXT NOT NULL,
  write_triggers TEXT,                -- JSON：explicit|rule|llm_extract|tool_result|session_summary
  extractor TEXT NOT NULL,            -- 'none'|'rule'|'llm'
  default_half_life_d REAL NOT NULL,
  core_kinds TEXT,                    -- JSON 数组：永衰减排除
  recall_mode TEXT NOT NULL,          -- 'eager'|'tool'|'both'
  recall_budget TEXT,                 -- JSON {items,chars,tokens}
  cross_read TEXT NOT NULL DEFAULT '[]',  -- JSON：允许读的其它 domain（默认空）
  inject_label TEXT,                  -- 注入时的前缀话术
  updated_at INTEGER NOT NULL
);
```

### 4.2 字段要点

- **`domain` 必须是策略主键**：同一个值既做过滤又选 pipeline；再加 `kind` 与 `origin` 两个正交维度（domain=场景，kind=记忆类型，origin=可信来源）。
- **`origin` 枚举（可信度阶梯，高→低）**：`user_explicit`（用户明说/编辑，最高权威，不自动改）> `user_confirmed`（推断后被用户确认）> `tool`（工具/环境事实，可验证）> `import`（导入的旧记忆，带来源）> `self_inferred`（模型推断，**默认 `status=pending`，不进检索**）。这是 Memory Trigger 双源信任的直接落地。
- **`status` 状态机**：`pending → active → superseded/archived`；`deleted` 为墓碑（保留 id/tombstone 供同步与"别再提"）。**任何覆盖都走 supersede 链 + memory_link，不物理删（除用户要求硬删）。**
- **`pinned/core`**：陪伴域的身份/关系/约定钉死不衰减；agent 域的活跃任务状态在任务期间不衰减。参照 Nomi Identity Core / Memory Trigger `core`。
- **`sensitivity`**：`private/secret` 不参与跨域注入与默认导出；对齐 MemOS 的"敏感信息只注入所属 session"。
- **双时间**：`valid_from/to`（事实何时为真）+ `created_at/updated_at`（何时记录）；冲突时"新记录 supersede 旧事实"，但保留旧 `valid_to`——Zep 的边失效模型。
- **evidence**：`quote` 必须能在 `memory_source.text` 逐字命中（ICL 的 provenanceRate 校验），这是"她能回指"的唯一保证。
- **memory_event 与"派生"的关系**：`memory_item` 可由事件日志重建，但 `memory_event` 里用户发起的 op（pin/deny/forget/confirm）**不可被重建覆盖**；重建 = 派生 item 全量重算 + replay memory_event overlay。

---

## 5. 写入 / 召回 / 评测

### 5.1 写入管线（候选、成本、降级）

| 管线 | 触发 | 成本/延迟 | 优点 | 风险 | 适用 domain |
|---|---|---|---|---|---|
| 规则触发 | 正则/关键词（"记住""我喜欢""答应"） | <1ms，0 token | 零成本、可解释 | 覆盖低、中文口语难 | 两者 |
| 显式「记住」 | 用户指令/长按 Pin/编辑面板 | 0 token | 精度最高、用户掌控 | 依赖用户主动 | chat 主、work 辅 |
| LLM 抽取 | 每轮 / 每 N 轮 / 会话结束 | 200–2000 tok/次，数百 ms–数 s | 覆盖高 | 慢、贵、会谄媚/编造、破缓存 | work 主、chat 批处理 |
| 工具结果/环境事实 | 工具返回、系统事件 | 0 token | 确定性、可验证 | 92% 噪声（ICL live raw），必须来源分类 | work 主 |
| 会话摘要/块化 | 空闲/会话结束/每 K 轮 | 规则≈0；LLM 数百 tok | 保留叙事、成本可控 | 摘要丢细节、paraphrase 不稳定（ICL N4） | chat 主 |
| 反思/巩固 | 夜间/空闲批处理 | 一次数千 tok，不在关键路径 | 提质、可重跑 | 会删证据、"记忆税"（ICL N16） | 两者，后置 |
| 承诺/待办抽取 | 规则+LLM | 低 | 陪伴/任务都刚需 | 无生命周期会误导（ICL N10） | 两者 |
| 遗忘/衰减/合并/归档 | 定时/维护 | 确定性，≈0 | 防垃圾堆 | 静默作废伤信任 | 两者 |

**约束与降级路径**：
- **写入不占关键路径**：对话中只做规则/显式写入；LLM 抽取放 turn 结束或空闲批处理（Letta sleep-time 的理由：慢且不必要在用户等待时做）。
- **无模型可用**：显式记住 + 规则触发 + 原文层 + 规则切块（时间间隔/话题切换）+ 模板摘要（首句/末句/关键词）+ FTS/bigram 检索 + 时间衰减；**价值主张改成"可信原文 + 可检索结构 + 诚实的注入"**（[icl-memory.md](icl-memory.md) Q8）。
- **本地小模型**：本地 embedding（bge-small 类）+ 本地小 LLM 只做"候选"——产物进 `pending`，被用户确认才转 `active`（Memory Trigger 机制）；宁可少记，不可脑补。
- **硬规则**：① `source=agent_internal` 永不进"事实"；② 写入必须带 origin 与 evidence，缺一不 active；③ 用户确认/纠正的写不可被后续批处理覆盖；④ 合并阈值（>0.7 合并、>0.35 问用户）写死，禁止静默作废。

### 5.2 召回管线

1. **候选集**：domain 白名单（当前域 + 允许的共享层）→ `status=active` → 时间窗/条数上限（去掉 ICL 的 5000 硬窗，改按 `(domain,created_at)` 索引分页）。
2. **打分（混合，无向量可退化）**：`score = sim × weight × decay`；`sim = 0.7·cos + 0.3·bigram`（无嵌入退纯 bigram）+ 精确串加分；`decay = exp(-age/half_life)`，`age` 锚点取 `max(last_recalled, updated_at, created_at)`（修 ICL N6 的慢性丢失）；`pinned` 不衰减。Zep 证据：混合检索（cos+BM25+图）比单路稳。
3. **时间维度**：查询含时间线索时先做时间过滤/扩写（LongMemEval 的 time-aware query expansion）；`valid_from/to` 与查询区间重叠加权。
4. **domain 过滤**：默认 `domain IN (当前域, 'shared')`；**跨域 gated**：① 显式召回工具（模型或用户发起）；② policy 的 `cross_read` 白名单；③ 注入时带 `provenance` 标签与来源域（"这是工作里的记录"），不得伪装成她自己的经历。参考 SillyTavern 的多级 lore 作用域与 MemXD 的"显式桥接"。
5. **trigger**：陪伴 = eager（每轮自动，≤2 条/≤200 字，低置信只说"可能有"）+ 情绪触发（中文项目用向量比情绪锚点，3–8ms）；agent = 工具召回为主（模型决定查什么），游标预算按 token。
6. **呈现**：来源（会话/时间）+ 引用原文 + 置信度 + "存疑"标记；**想起 ≠ 提及**（recall 不加权、只在真正被使用时续命）。
7. **冲突/更新**：`origin` 高者胜（user_explicit 永远压 self_inferred）；新事实 supersede 旧事实并保留 `valid_to`；矛盾不删除而是标 `contradicts` 链；用户否认两次→降权转 `pending`；删除=墓碑 + 可硬删。
8. **预算**：chat eager ≤2 条/≤200–500 字；recall 工具默认 6 条 + token 游标（ICL 10000 token 游标）；work 按任务上下文预算；**注入只能追加消息，不得进 system**（缓存纪律，ICL CACHE-MODEL）。

### 5.3 评测

**两套指标，不能互相替代。**

- Agent/work：
  - 基准：LongMemEval（重点看 **knowledge-update 与 abstention** 两栏）、LoCoMo（多会话时间跨度）、MemBench（capacity/efficiency）。
  - 自建回归：`anyHit@5`、`recall@k`、`missRate`、`falseInjectionRate`、`provenanceRate`（证据可解析到原文）、`supersedeCorrect`（旧事实不再被召回）。
- 陪伴/chat：
  - **两周种子事实召回**（S12 的方法）：Day1 埋 5 条（人名/过敏/工作/宠物/计划），Day3/Day14 无提示问；记录"编造"次数。
  - **关系连续性**：跨会话提到同一事件时，叙事是否自洽、是否引用正确的共同经历。
  - **人格一致性**：同一人格卡下口吻/边界是否漂移（记忆污染会带动人格漂移）。
  - **情绪得体**：主动想起时是否"时机对、话术对"，而非把旧事逐条汇报（Memory Trigger 的"严禁复述"）。
  - **忘得对**：琐事应淡出、里程碑应留下；核心钉死项不得衰减。
  - **纠正响应**：用户说"早就不是这样了"之后，该条应降权/退场，且不再复述。
  - **成本**：每轮注入 token/字符、per-turn 延迟 p50/p95。
- **防记忆幻觉/污染的最小测试集**（离线、确定性优先）：
  1. `mustNotRecall` 负例集：噪声（tool/env/agent 自己的计划）一条都不许进记忆；
  2. 证据校验：每条 active 记忆的 quote 必须逐字命中，`provenanceRate=100%`；
  3. 自污染：让 agent 生成一条结论，验证它不会回写成 `fact`；
  4. 冲突：先记"喜欢 A"，后记"不喜欢 A"，验证旧条 supersede 且召回不再返回 A；
  5. 删除：用户删除后，检索/注入/导出都不再出现（含墓碑语义）；
  6. 推断不转正：`self_inferred` 在用户确认前不得进入召回；
  7. 悬空引用扫描：引用已 superseded 记忆的旧结论要能被检出（Memory Trigger `retraction_scan`）。
- **benchmark 适用性结论**：LongMemEval/LoCoMo/MemBench 适合 **work 域 + 共享事实层** 的回归，**不适合**作为陪伴体验的主指标；陪伴必须自建 fixture（沿用 ICL 的 `fixture.json` 口径：稳定 rowId、gold 必带 evidence、noise 显式集合、probes 带 `mustNotRecall`，见 [icl-memory.md](icl-memory.md) §3.1）。语义 judge 不进 gate，只做人工抽审（ICL Q3）。

---

## 6. 对 myco I2 的建议

> 命名澄清：老 I2 = 多会话 chat（D4/A2）；本文 I2 指 [60 计划](../design/60-mvp-closed-loop-plan.md) 里的"I2 记忆"（在该计划中排在 MVP 闭环之后）。

### 6.1 最小可行范围

**做（按落地顺序，全部可无模型验证）**
1. **数据地基**：`memory_source`（原文层，从事件日志派生，只读）+ `memory_item` + `memory_evidence` + `memory_link` + `memory_event` + `domain_policy`（先只有 chat 一个 profile，work 占位）。
2. **显式记住**：消息长按/指令"记住这个"、编辑、pin（core）；这是精度最高、成本最低的入口。
3. **用户可见记忆面板**：列表（按域/类型/时间）、跳到源会话、编辑、删除（墓碑 + 硬删选项）、单域开关、导出（md/json）、"忘掉这个"。
4. **检索**：FTS（或自建 bigram 倒排，中文必须；Android 内置 SQLite 的 FTS5 可用性需按目标 API 版本实测）+ 精确串 + 时间衰减 + pinned 不衰减；向量作为可选本地 provider（不做硬依赖）。
5. **注入**：eager ≤2 条/≤200 字、只追加消息、低置信话术、"三种没有"的诚实话术；recall 工具（带 provenance）。
6. **衰减/归档**：半衰期按 domain/kind 配置；supersede 链；不静默作废。
7. **评测**：一个 fixture（2 天合成 + 1 天真实）+ 6 指标（anyHit@5 / missRate / falseInjectionRate / provenanceRate / mustNotRecall / supersedeCorrect）+ 一条命令 + exit 3。

**不做（写死，防偷加）**
LLM 自动抽取与反思/做梦、实体关系图、跨域自动召回、云端向量库、记忆同步/多设备、多模态记忆、语义 judge 进 gate、遗忘曲线自动调参、prompt 优化（procedural memory）。

### 6.2 与现有事件日志的关系

- **原文层 = 派生**：`memory_source` 从会话 JSONL 投影，可随时重建；不重复存原文。
- **记忆条目 = 派生 + 策展覆盖**：普通 episode/摘要可由事件重建；**用户显式动作（pin/编辑/确认/否认/删除）单独进 `memory_event`（append-only），重建时 replay，不可被批处理覆盖。** 这是"记忆可重建"与"用户说了算"唯一的兼容方式。
- **不变量**：任何注入给模型的内容都必须能从日志重建（沿用 DSH 的"模型可见即已记录"）；任何用户可见的记忆都必须能回指源事件（provenance）。
- **为什么不是纯派生**：纯派生会让"用户纠错"在下次重建时丢失；纯独立写入又会与事件日志出现两套真相。混合是唯一自洽解。

### 6.3 隐私与用户可见控制

- **本地优先**：记忆默认只存本机；embedding 默认不启用，启用也只走本地 provider；无云端向量库硬依赖（对齐 A4 离线独立）。
- **分域可见**：chat 的记忆默认不进 work，反之亦然；`shared` 层由用户显式授权；跨域读有白名单 + 每次注入带来源。
- **可删可关可带走**：单条删除、整域清空、关闭记忆（停止写入与注入，保留已有）、导出（含 provenance 的 md/json）、硬删模式。
- **敏感态**：`private/secret` 不参与跨域注入与默认导出；不记录密钥类内容（对齐 D7 唯一出网点）。
- **透明**：面板显示"记忆 N 条 / 上次整理时间 / 本次注入了什么"，与 Character.AI 的 Memory Usage 同构；失败必须吵（ICL C2/C4），不许"她想不起来但不报错"。

### 6.4 建议的落地判据（可验收）

- 两会话并发时，A 会话的记忆不污染 B（域/会话维度真的生效）；
- 无关 query 返回空 + 诚实话术；停采集时状态是 `stale` 而不是空答案；
- `source=agent_internal` 的 100 条噪声一条都不进 active；
- 删除后检索/注入/导出三处都不出现；
- 陪伴域 Day3 种子事实召回 ≥4/5，编造 ≤1；work 域 LongMemEval 子集不因引入记忆而下降；
- 每轮注入 ≤200 字、只追加、system 字节稳定（缓存命中不降）。

---

## 7. 存疑 / 未证实

1. **Character.AI 的机制细节未读到正文**（blog.character.ai 两次抓取失败），Story Memory/Facts 的自动抽取频率、是否可删除、Memory Usage 的口径均为检索摘要级；上线时间在不同二手源里写 2025-03 与 2026-05，需核实。
2. **S12 的两周实测是单评测者、n=5 事实、有联盟营销**，只能当"设计模式存在"的旁证，不能当结论。
3. **Hindsight / MemXD / MemOS 的指标均为作者自测**，无独立复现；MemOS 中文解读是二手转述，细节应以原文 arXiv 为准（本报告未逐段核对）。
4. **没有任何一篇文献直接回答"同一用户 chat/work 共享记忆库"**；本报告的 A+C 推荐是综合推断，不是被验证过的结论。
5. **跨域召回的收益/风险比未知**：MemXD 证明"分开存 + 桥接"能提升跨域召回，但陪伴场景的跨域（工作情绪带入聊天）可能是负收益——需要产品实验，不能只靠 benchmark。
6. **衰减参数没有科学依据**：138 天是社区项目的经验值，不是认知科学结论；myco 自己的半衰期只能靠真实使用数据调。
7. **"记忆税"风险**（ICL N16：整理步骤可能删除/遮蔽证据，导致记忆系统比朴素 RAG 更差）没有被社区方案正面回答；本报告建议"派生可重建 + 原文层只读"来兜底，但未经实测。
8. **评测缺口是结构性的**：目前没有公认的"陪伴记忆"benchmark；LongMemEval/MemBench 的 reflective memory 只是接近，不覆盖情绪/关系/时机。
9. **工具链限制**：本会话 GitHub 直连失败，memory-trigger 经 gh-proxy 取到全文，SillyTavern MemoryBooks/AutoSummarizer 只读到检索摘要；AWS 博客正文截断；Character.AI 博客两次抓取失败。这些来源的细节可能滞后，引用时已标注证据级别。
10. **未验证的假设**：domain 的边界是"chat/work"还是"关系/项目/场景"更合适？本报告按现状假设二元，但 MemXD 与 Memory Trigger 都暗示"关系/身份层"应独立于域——若真实使用中发现域更多（如家庭/朋友/多项目），schema 的 `domain` 可能需要升级为多维 scope。

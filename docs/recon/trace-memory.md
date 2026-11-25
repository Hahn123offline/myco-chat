# trace-memory 考古：可采信结论 / 已翻案路线 / 契约教训

> 坐标约定：除特别标注外，路径均相对 `reference/trace-memory/`；`§` 指该文档的章节号，`file:line` 指源文件行号。
> 证据分级：**A** = 有设备/日期/具体数字，重做成本高，可直接采信；**B** = 数据形状/单次实测，作负面证据使用；**C** = 设计设想，未验证。
> 本仓库自述「仅为参考、不构成约束」（`docs/README.md:3-5`、`reference/README.md:5-8`）。本文严格按此处理：**采信结论，不继承实现**。

## 0. 一页结论（≤15 行）

1. 这份仓库最值钱的不是代码，而是**两个语义契约**：`freshness`/`coverage`（滞后必须可见）与 `block/visit/view`（内容寻址 / 时间寻址 / 视图不落盘）。
2. 最有价值的单条实测是**反直觉分工**：文本多的地方像素对齐必坏（微信 29%），文本少的地方像素可靠（信息流 0.97–1.00）；两个失效域几乎不重叠（设计 §4.8）。
3. 数据脏度是可引用的量化基线：637k 行里 **79.1% 冗余 / 22.3% 串台 / 35.3% 控件污染 / 覆盖仅 6–40%**（质量基线）。
4. freshness 契约对应真实故障：`object_messages` 落后 18h，系统对一条**确实存在**的消息答「没看到」（`docs/INTERFACE.md` §4）。
5. 但这套契约的**参考实现自己违反了它**：渲染异常被吞成 `answer=""`，与「没找到」的语义恰好互换（`ContractRecall.java:94-97` vs `RecallCore.java:221`）。
6. 七个翻案话题里，真正有技术定论的只有「端侧模型能力」和「采集主通道」；「要不要上云」「检索选型」是**价值取舍**，不是技术结论。
7. 文档自曝三套 L0–L3 编号、三套日叙事 schema、同组数字两用正反论证（`ARCHIVE-INDEX.md` §6）——引用前先看状态标注，别把 SUPERSEDED 当结论。
8. 对 myco：**抄类型与语义（约 150 行 TS），改字段与阈值，别抄模块划分、Android 采集实现、L0–L3 金字塔、BM25/IDF/weight 那一层**。
9. 三个 `com.dsh.contract` Java 文件值得 TS 化，但必须把 `answer` 从「空串」改成三态判别联合，否则照抄即继承它的坑。
10. 手机屏幕记忆的正确起点不是安卓：先把 **observation/visit/block 存储 + 回放 + 故障注入** 做成设备无关层，再谈采集。

## 1. 可采信的实测结论（附出处）

### 1.1 A 级：API 与设备事实（重做昂贵）

| 结论 | 出处 |
|---|---|
| `AccessibilityNodeInfo` **没有** `getScrollX/Y()`，只有 `isScrollable()` | 设计 §4.5（`javap android.jar` 确认） |
| `TYPE_VIEW_SCROLLED` 的 delta **值错**：bilibili 全 0；QQ 报 `deltaY=3` 而实际位移 897px | 设计 §4.5 |
| 位移可行来源排序：①同节点 bbox.top 差（QQ 众数 897px，18 票中 11 票）②OCR 行匹配（微信 80%，12/15）③像素 NCC（微信列表 29%，2/7） | 设计 §4.4.1 / §4.4.3 / §4.5 / §11 |
| **微信的 A11Y 树是空的**（1 个空节点 `bounds=[0,0][0,0]`），`uiautomator dump` 同样空 → 不是本 App 的 bug | 设计 §4.6 |
| QQ：252 节点 / 38 文本节点 / RecyclerView(rows=134)；bilibili：167 节点 / 38 文本 | 设计 §4.6 |
| `takeScreenshot()` 是一次性请求、无持续帧流，系统最小间隔 1s；**不引入 MediaProjection**（Android 14+ 每次冷启动授权，常驻类应用不可接受） | 设计 §4.3 |
| 栅格必须不对称：**12 × 200 uint8**（2400B/帧，≈1.2KB PNG），dy 误差 <5px；8×16 方形误差 −20~−36px **不可用于拼接** | 设计 §4.4 |
| 滚动拼接可行：3 帧 → 1200×2659 长图，8 卡片连续，无重复带/接缝 | 设计 §4.4 |
| 视口内**静止浮层**会造成重复；逐行 moved-vs-still 判定可纯数据剔除（122/2150 行=5.7%、73 行=3.4%），剔除后 1200×2902 | 设计 §4.4.2 |
| 解析成本 **95% 在 `getChild()`**（`child=94ms` / 总 98ms）；同一棵树滚动中 422/519/546ms vs 静止 37/41/46ms，**差 13 倍 → 是争用不是算力** | 设计 §4.7 |
| 三条被否的优化：早退（仍 223–498ms）、换后台线程（更糟，1.1–1.9s）、截图时全量补解析（813ms） | 设计 §4.7 |
| 采用「最后一次事件后 250ms 再解析」：主线程事件处理 581→20ms（≈23 倍） | 设计 §4.7 / README 提交 `b47a58d` |
| 字典化是前置条件：非字典化 ~65MB/天 → 字典化 ~11MB/天（活跃 3h、2s 一帧），30 天 ~330MB | 设计 §3.5 |
| `recall` 端到端 127ms（改前 60 秒）：候选 37ms / IDF 0ms（预热）/ 打分 47ms | `docs/README.md` §2 性能 |

### 1.2 A 级：数据脏度基线（2026-11-23，637,016 行）

来源：`docs/质量基线-2026-11-23.md`。

```
冗余    79.1%   637k 行只承载 133,097 条不同文本（平均每文本 4.8 次）
串台    22.3%   对象键是 UI 状态词（"游戏中心,按钮" 70,991 行 / "倍速中" 33,502 行 / 类名当对象 14,987 行）
chrome  35.3%   系统/页面控件文本被当正文（词表法只得 6.5%，必然漏）
覆盖    6–40%   清醒窗口 08:00–24:00；用量最大的 11-20 也只有 40%
```

- **无词表判据成立**：按「该文本出现在多少个不同对象里」统计，`>100 个对象` 的 **42 条 distinct 抵 91,598 行 = 14.4%**（压缩比 1:2200）；`1–2 个对象` 的 235,873 行（37.0%）全是真内容。→ **结构 chrome 可无 app 知识识别，且可测**（质量基线「关键词表判据失效」）。
- 数据健康：`quick_check` 报 `Bad ptr map entry`，部分 `lines.text` 非合法 UTF-8 → `count(*)` 可过、`text like` 时好时坏，建议重建（质量基线「数据健康」）。

### 1.3 A 级：OCR 误读 vs 编造的归因

来源：`app/v2/docs/失真定位结论-2026-11-19.md`。

- 引号 711 条：可溯 430 / 找不到 281；数字 1,574 条：时间派生 1,114 / 可溯 387 / 归一化 37 / 内容缺失 36。
- 60 条本地扩样 + 多图复扫：**≈85% 是 OCR 漏读，13% 仍未检出**；按 13.3% 外推 281 条 ≈ **37 条真编造（95% CI 19–70）**。
- 结论：`missing` 绝大多数不是模型编造，而是「写正文时看宫格图，下游只读 OCR 转写」。处置：给 agent 补 `read_frame`，并规定「细节必须看图核对」。
- **可移植教训**：同一份内容存在「高保真源（图）」与「低保真派生（OCR 文本）」时，所有下游结论必须能回指高保真源，否则误解会被当成事实引用。

### 1.4 B 级：数据形状与「治理字段空转」（负面证据）

来源：`viz/README.md` §5（画图时看出来、每条可在页面找到对应位置）。

| 现象 | 数字 | 含义 |
|---|---|---|
| 注意力碎片化 | 918 块 / 覆盖 5.02h / 中位停留 **15s** / `<10s` 41% / `<60s` 92% / 切换 231 次（≈46 次/小时） | 「一根根竖线」，不是连续河流 |
| 金字塔体积落差 | 918 块只有 88 块（9.6%）进 `mem_items`；117 条 L2 链收 837 块（均值 7.2） | 绝大部分原始观察被丢弃，丢弃规则未被评测 |
| 日叙事覆盖不足 | 5 条 mainline 只点名 83 个事件，**34 个事件完全没被提到** | 摘要层的漏报无任何告警 |
| `weight` / `recall_count` 从未运动 | `weight` 全 1.0（一条 chat 1.5）、`recall_count` 全 0 | 显著性/衰减**只有字段没有运动**，等于未验证 |
| `trace_gaps` 空表 | 两库均 0 行；但日叙事 7 条缺口用「≥20min 无记录」反推**起止完全一致** | 判定逻辑没坏，**只是没落库**；「空表」≠「没有缺口」 |
| `page_elements` 只覆盖晚上 | 662,658 行仅 18:09–23:41，白天 0 行 | 单通道断流时下游静默半可用 |
| 掩码几乎没动 | 592 个有文本块中 **583 块 `masked_text` 与原文逐字相同**，5 块被改，4 块为空；`guard_hit` 仅 2 次 | 链路「打了标记但基本没命中」，收益接近 0 |
| 假 STALLED 报警 | 心跳打出 `lastEventAgoMs=1794904060624`（≈epoch 本身），误报「MIUI bound-but-deaf」 | **时钟混用**：`uptimeMillis` 去减 `currentTimeMillis`（设计 §12，已修） |

### 1.5 C 级：明确是设想、未验证

`block`/`visit` 表未实现（README §6）；`captureChannel` 未定义（INTERFACE §10.5）；双判据融合规则只是设计（设计 §4.8.1 / §14 未勾选）；无文字累积误差闭环未做；`commit` 粒度未定；QQ 错字根因未查；`object_messages` 兼容期未定；端侧 1B 能否做「逐元素敏感判定」未验证（ARCHIVE-INDEX ⑦ 最后一跳）。

## 2. 契约：freshness 与三个工具面

### 2.1 它对应的真实故障（全文最重要的一段）

`docs/INTERFACE.md` §4 与 `Freshness.java:3-9` 写明：**`object_messages` 落后 18 小时，系统对一条确实存在的消息回答了「没看到」**。补齐的背景是：采集本身会因系统关闭无障碍而中断（质量基线覆盖期止于 11-22 18:36 且覆盖率仅 6–40%）。两条叠加的后果是——**「数据停了」的默认表现与「事情没发生」完全相同**。分仓后这个错误还会被另一仓写进对话记忆、**二次固化**，所以 freshness 被定为 v1 必选而非可选。

### 2.2 契约形状

```
recall(...) → { answer, ids, coverage, freshness }
  answer      人可读结论；空串 = 没找到，不等于没有发生（INTERFACE §3.1）
  coverage    { covered_ms, gaps[], 本次显示范围, 未显示条数 }（§3.1）
  freshness   { projectedUntil, lagMs, state }，state ∈ ok|catching_up|stalled（§4）
readBlock / readFrame → 失败必须带 reason，不抛异常（§3.2-3.3）
```

实现侧的可借鉴细节（`app/v2/contract/.../Freshness.java`）：

- 阈值是常量：`CATCHING_UP_MS=10min`、`STALLED_MS=30min`（`:13-15`）。
- `state` **不存储，由 `projectedUntil` 与 `now` 推导**（`:42-51`）——「不需要额外状态，不会与数据不一致」。
- `caveat()` 直接产出人话限定语：`"我这边还没处理到最新（差约 N 分钟）"` / `"记录在 T 之后就停了，之后没有记忆"`（`:59-71`）。
- `trustworthyForRecent()` 把「该不该据此下结论」做成一个显式谓词（`:54-56`）。

### 2.3 为什么「answer 空串 ≠ 没找到」必须写进契约

因为它把**三种完全不同的世界状态**折叠成了一个字符串：

| 世界状态 | 证据在哪 | 正确措辞 |
|---|---|---|
| 索引命中为空（确实没记到） | `answer` 空 + `coverage` 正常 | 「我没记到」 |
| 索引根本没覆盖那段时间 | `coverage.gaps` 非空 | 「那段时间我没在记录」 |
| 索引停止更新 | `freshness.state ≠ ok` | 「我这边还没处理到 X 点」 |

契约把它们拆成三个独立字段，并禁止任一折叠成「没有发生」。`read_frame` 同理：`ok=false` 必须给 reason（`evidence_evicted` / `mask_denied` / `evidence_missing` / `evidence_unavailable`），且**「核不到原图时必须明说『我没能核对原图』，不得用推断冒充核对」**（INTERFACE §3.3）。

### 2.4 契约好，参考实现却违反了它（考古发现，建议 Lead 重点看）

`memory/src/main/java/com/dsh/memory/api/ContractRecall.java`：

- `:94-97` 把 `RecallCore.render` 的任何异常吞成 `answer = ""`；而 `RecallCore.java:221` 对「无命中」返回的是 **`"没有找到记录。"`**（非空）。→ **渲染失败与真正的没找到，在 `answer` 字段上语义恰好互换**，正是契约要防的事。
- `:88-89` `coverage.gaps` 恒传 `null`（契约要求声明空档）；`covered` 是逐 hit 累加，会重复计数。
- `:80` `truncated = hits.size() >= limit`：恰好命中 limit 条也报「已截断」。
- `Recall.Request.agentPresent`（契约 `Recall.java:24-28`）在实现里**没有接线**。
- `readBlock` 把拼接文本按 `\n` 拆行，位置字段全填 0（`:113-118`），与契约承诺的「带位置」不符。

→ 教训：**语义契约必须配反例测试**（「渲染失败 + 无命中」必须可区分），否则实现会悄悄退回默认行为；「契约里有这个字段」不等于「实现兑现了」。

### 2.5 推广到 agent 的记忆/上下文

1. **每个记忆/上下文来源各有一个 freshness**：memory store、context assembler、每个 ingestion pipeline；聚合时取最差，不取平均。
2. **compaction 后的上下文就是「滞后索引」**：摘要一旦生成，近期轮次就可能不在其中；给上下文也标 `projectedUntil`（最后吸收的 turn/事件 id）与 `state`，避免 agent 把「不在我的上下文里」说成「没发生过」。
3. **措辞责任放在契约层**（`caveat()`），不要指望每个调用方自觉；这是 trace-memory 用血换来的位置选择。
4. **时间源纪律**：单调时钟与墙上时钟分字段；状态由数据推导不缓存；`uptime - epoch` 这类混用会把「完全正常」报成「挂了」（设计 §12），freshness 自己先坏掉就毫无意义。
5. **确定性**：同数据同查询同结果（稳定排序：score desc → -t0 → id，`RecallCore.java:107-109`），否则调用方无法缓存/回归（INTERFACE §5）。

## 3. 观察日志与重建 schema（可移植性分析）

### 3.1 四条公理（设计 §1）

- **A1 采集层只记录事实，不产生判断**（事实 = API 读到的值或像素测得的量；判断 = 任何「这属于什么 / 这是不是新的」）。
- **A2 顺序免费，分段昂贵**：时间戳给全序零算法；切段错了「连接永久丢失」→ 分段一律后移。
- **A3 重建是视图，不是事实**：同一段观察可重建出不同段；视图不落盘、可无限重算。
- **A4 只要证据在，读者可以自己推**：采集的验收标准是「每个过渡类型，读者推断所需的证据是否都在」，最终判断交给 agent。

推论：**错误必须便宜；宁可重建得难看，不要采集时切错。**

一个值得学的细节：他们把**「相同」与「相关」拆开**——内容哈希去重（机械判断）允许在采集层；「属不属于同一上下文/同一群」（语义判断）一律留到重建层（设计 §5）。

### 3.2 schema 骨架（设计 §3、§6）

```
observation   t_mono / t_wall / pkg / activity / window_stack[] / content_hash /
              shot_ref / grid(12×200 luma) / mean_luma / sharpness / n_nodes / elapsed_ms
obs_nodes     obs_id, idx, parent_idx, depth, cls_id, resid_id, text_id, bbox×4,
              flags(clickable|editable|scrollable|checkable|focused|visible), scroll_x/y
obs_input     t, kind(CLICK|SCROLL|KEY), node_idx, bbox, dx, dy, source(BBOX_DIFF|EVENT|PIXEL|NULL)
dict_cls / dict_resid / dict_text        字典化（前置条件，不是优化）
block         内容寻址：block_key(文本集合+栅格+pkg)、text_lines、grid、shot_ref、first_seen
visit         时间寻址：t_start/t_end、pkg、activity、window_sig、segments[(block_key, align, t_in, t_out)]
view          按问题生成：notice / thread / session；不落盘，缓存必须带 derivation_version
commit(day)   提交边界：提交前可反复重建，提交后冻结为 canon，观察日志可 GC
```

- `parent_idx` 被称为「这一层最重要的新字段」：让「这条消息属于哪个气泡 / 这个按钮属于哪个弹层」**从事实可推**，而不是从坐标先验猜（现 `sender` 只有 45% 的根因，设计 §3.2）。
- 「三值原则」：字段缺失 = `NULL` = **未知**，不得当 0/空串/「没有」（设计 §3.4）。位移拿不到时**显式 NULL，不得写 0**（§3.3）。
- `Role` 七分类（PARAGRAPH/LIST_ITEM/…）**全部废弃**，换 A11Y 原生布尔位——「前者是猜的，后者是读的」（设计 §2）。

### 3.3 哪些是安卓特有，哪些可移植

| 组件 | 安卓版 | 可移植内核 | 判决 |
|---|---|---|---|
| 可读树 | A11Y `AccessibilityNodeInfo` | 任何「树 + 布尔位 + parent 指针」的可访问性树（桌面 AXTree/UIA） | **可移植**，A11Y 只是实现之一 |
| 文字来源 | ML Kit OCR | 截图/视频帧文字识别（成本更高） | **可移植**，但作为第二判据 |
| 位移估计 | bbox 差 / OCR 行匹配 / 像素 grid | 同一问题在桌面/视频同样存在（滚动、翻页、镜头移动） | **可移植**，判据优先级按场景重算 |
| 双判据 | 文本 vs 像素 | 结构/文本 vs 像素，**失效域互补**（§4.8） | **最值得移植的内核** |
| 1s 截图限制、无帧流、MediaProjection 授权成本 | 安卓特有 | 桌面可连续捕获；视频本来就有帧流 | **不移植**（约束消失） |
| 微信空 A11Y 树 | 安卓特有现象 | 「某些窗口/应用不暴露可读树」是普适失败模式 | **作为负例移植** |
| block/visit/view、presence、commit、两库分离 | 设备无关抽象 | 完全通用 | **抄** |
| `object_key/page_title/kind/side/sender` | 安卓旧投影的禁止字段 | 任何「写入时定性」的字段都应禁止 | **抄禁令** |

### 3.4 双判据融合（可移植的结构性发现）

- 微信会话列表：文本多（15–30 行/帧）、视觉近周期 → 文本 80%、像素 29%。
- bilibili 信息流：文本少、封面各异 → 像素 0.97–1.00（误差 <5px）。
- 纯图流/视频/游戏：无文本 → 像素唯一可用。
- 结论原文：「**文本多的地方，像素就不可靠（内容同构）；文本少的地方，像素反而可靠（图像区分度高）。**这是两个互补判据，不是备胎关系」（设计 §4.8）。
- 融合规则第 4 条最值得抄：两者都过阈且**互相矛盾**时**不拼**，标 `NUM_OVERLAP` 保留两段——「宁可断开，不要拼错——断开只是多一段，拼错是结构错误」（§4.8.1）。
- 无文字持续下滑：文字出现的那一刻**立刻用它纠偏**，而不是「没文字就全靠像素」（§4.8.2）。
- 判据要能**自报不可靠**：文本法失败表现为低占比（可察觉），像素法失败是「近似平局」（无法察觉）——这比准确率更重要（§4.4.3）。

### 3.5 可逆性与提交边界

- 观察日志 = 证据层（设备本地、可 GC、不随设备迁移）；`commit` = 记忆层（可携带、提交即冻结为 canon）（设计 §7）。
- 目标原文：「换设备后 agent 和用户的记忆还在，但原始数据不可溯源，旧回忆还在，新的能继续长。」
- **「可逆是唯一硬的收益」**：分段正确率、帧级归属**不可测**（无真值），这套设计把「不可测+不可逆」换成「不可测+可逆」，不要让话术把它说成「可测」（设计 §8）。
- 悬空引用是设计的一部分：证据被 GC 后 `read_frame` 必须返回 `ok=false` + reason，导入方必须容忍（INTERFACE §6）。

### 3.6 这些采集数字如何反向指导 myco 的屏幕理解

| 实测 | 对 myco 的直接含义 |
|---|---|
| 冗余 79.1%（637k 行只承载 133k 文本） | 去重必须前移到写入/入库，内容哈希做键；留在查询期就是「每问一次付一次」 |
| 串台 22.3%（对象键 = UI 状态词） | 采集/观察层一律不写「对象身份」；「这是什么页面/这个群是谁」是重建层判断 |
| chrome 35.3% + 词表法只得 6.5% | 控件文本用**统计判据**（跨对象出现次数 >100 的 42 条抵 9 万行）过滤，不用词表；且该判据可测 |
| 解析 95% 在 `getChild()`；同树滚动 422–546ms vs 静止 37–46ms（13×） | 「读树」是最贵的动作，成本是**争用**不是算力 → 策略是「画面稳定后再读」，不是「少读」；高频探针必须是不递归的零成本 probe（只读节点数/文本集合 hash） |
| 覆盖 6–40%，最大用量日也只有 40% | 屏幕记忆的第一级指标是**覆盖率**（我看到了多少清醒时间），与召回率并列；任何「没找到」都必须能回指覆盖率 |
| 中位停留 15s、`<60s` 92%、切换 231 次/天（≈46/h） | 块粒度与采样节奏按「碎片化」设计，不要假设长停留；visit 边界应密集可切 |
| OCR 漏读 ≈85%（失真定位） | 文本派生默认不可全信；每条结论要能回指高保真源（图/帧），并给 agent 一个「看图核对」的工具面 |

## 4. 已翻案的七个话题（摘要 + 为什么翻）

来源：`docs/ARCHIVE-INDEX.md` §五（47 篇文档来自 2026-11-15~18 四天）。

| # | 话题 | 轨迹 | 为什么翻（根因） |
|---|---|---|---|
| ① | 要不要安卓 App | 「PC adb 轮询就够、砍掉安卓开发」（11-15）→ **当天**改为真机 App | A11Y 事件流/树/截图只有设备内可得；adb 轮询拿不到事件，且早期 `recorder/collect.py` 只能采 shot/focus/notif/media 四种粗略信号 |
| ② | 采集主通道 | 「ADB 为主、不走 root」（11-15）→ 实际纯无障碍，同篇 §16 自我修正 | 采集质量由 `getChild` 与事件驱动决定；adb 引入额外转义/二进制坑（`viz/README.md` §2 拉库坑） |
| ③ | 要不要碰云 | 明确「不碰云端」（11-15）→ 次日云打标 20 条成功 → 直接引入云 API 强模型 | 端侧模型能力不足（见⑦）+ 产品目标在「纯本地」与「效果优先」间漂移。**这是价值取舍，无技术定论** |
| ④ | 检索方案 | 向量（11-15，未走）→ LIKE+FTS5 讨论（11-16）→ **BM25 + tieBreak + anchor 定格**（11-18） | 数据量小（133k 文本/637k 行）+ 查询短；向量需要模型调用与调度，与「端侧/离线」冲突；BM25 可解释、可确定性排序 |
| ⑤ | 出网矩阵 | 四档 WHITE/GRAY/BLACK/UNKNOWN（11-16 14:00）→ 只剩 BLOCK/MASK（17:31）→ 三级 NEVER_CLOUD/REDACT_THEN_CLOUD/NORMAL（11-17） | 维度在「按 app 分」与「按内容分」之间摇摆；把两个正交轴塞进一个矩阵必翻。见 §5 |
| ⑥ | 人名/地址脱敏 | 350M NER+角色化+模板化（11-15）→ 1B 补人名地址（11-16）→ 不做人名/地址/机构（17:31）→ 1B 输出含 name，人名掩码回来（11-17） | 判据「**能写出『人 100% 会同意』的断言才是规则**」（ARCHIVE-INDEX §一）；语义级 PII 判不准，NER 性价比不成立 |
| ⑦ | 端侧模型能干什么 | 35B ~10 tok/s「夜间合并绰绰有余」→ 实测 4–8 tok/s 天花板、35B 不上机 → 1B 会编造 → 只用 350M PII 编码器 → NER 性价比不成立 → 又让 1B 做逐元素敏感判定 | 每一跳都在**换任务**而非换结论；指标口径与任务定义漂移。A 级残值：1B decode 51.8 tok/s（`端侧推理实测-v1`） |

**从翻案里沉淀出的五条判断**（ARCHIVE-INDEX §五末，比七个结论更可移植）：

1. 安全默认是「降级到还能做的最大能力」，不是「什么都不做」；降级要有可解释的损失。
2. 指标变差时**先验量具**——探针错三次，一半「退化」是假的。
3. 不让模型做「判断」，让它做「产出」（「用片段回答、答不了就 null」→ 负例 0）。
4. 两库按**语义**分而非体积分：记忆库可携带、证据库可弃；记忆库不能依赖证据库才能用。
5. 缺口三态语义不同（`recording_off` / `app_only` / `no_record`），>20 分钟才算缺口。

**文档自曝缺陷（引用前必看，ARCHIVE-INDEX §6）**：L0–L3 在不同文档指不同东西（三套）；日叙事三套 schema 并存且 `mainlines` 在最终版不存在；图片 token 计费三套口径；**同一组数字「R@1 41%↔66%、±25 点」既用来论证「自研评测不可靠」又用来论证「已验证足够」**；`宿主架构` 同篇两处对 Module 生命周期自相矛盾。设计文档内部也有矛盾：`obs_nodes` 保留 `scroll_x/scroll_y`（设计 §3.2）而 §4.5 说读取器不存在；§11 汇总表说「OCR 行匹配待验」而 §4.4.3 已实测 80%。

## 5. 脱敏/出网经验

### 5.1 三层结构（`app/v2` 的 `:guard` / `:masker` / `:egress`）

- **guard（内容硬死线，纯函数零依赖）**：只做**格式校验**，不做内容理解（`Guard.java:12-35`）。
  - BLOCK：银行卡（13–19 位 + Luhn + 非全同 + 首位非 0，13/15 位还需卡上下文以区分毫秒时间戳）、身份证（18 位 + GB11643 校验位）、支付密码（6 位数字 + 前后 30 字含关键词）、短信验证码（4–6 位 + 前后 15 字）、CVC（3–4 位 + 前后 15 字）。
  - MASK：手机号、邮箱。
  - 细节值得抄：上下文窗口（没有关键词时 6 位数字**绝不能**当支付密码）、多类命中去重取优先级、从后往前替换保持偏移、`50KB 文本 check() < 20ms`。
  - 实测：**404 条屏幕文本 / 937 条短信上零误报**（ARCHIVE-INDEX ACTIVE 「guard-真数据验证」）。
- **masker（元素/图片掩码）**：元素级 bbox，图片掩码外扩 3px「防 OCR 贴边露出边缘」（`MaskPolicy.java:21-22`）；`MaskPolicy` 默认 `maskAmount=false`、`maskName=false`——**金额对记忆有用，姓名默认不掩**，即按用途而非按敏感度取舍。
- **egress（唯一出网点 + 审计）**：任何上云请求必须先过（`EgressGate.java:11-21`）。
  - 三档按**来源 app 的 provenance** 取最严格：`NEVER_CLOUD` / `REDACT_THEN_CLOUD` / `NORMAL`，再叠加内容 guard。
  - BLOCK 命中 → 该段替换占位符、`effectiveTier=BLACK`、**本地仍可处理**（不是全拒）。
  - **fail-closed**：失败永远 `allowed=false`；`REDACT_THEN_CLOUD` 在 Masker 未接入时**禁止上云**（`:112-115`）——未实现的档默认拒绝。
  - 审计只落 **sha1 + 长度 + 动作计数**，不落原文（`:127-133`），避免审计成为 PII 副本。
  - recall 时再按 pkg 分级 `sanitize`：NEVER→占位；REDACT→guard 命中即占位；拿不到可用掩码→占位（`RecallCore.java:172-216`）。

### 5.2 为什么矩阵翻了三次

三次翻案的本质是**三个正交问题被塞进一个矩阵**：①来源信任（哪个 app 的内容）②内容敏感度（格式/语义）③去向信任（本地/云/哪个 provider）。稳定形态是拆轴：`provenance(来源) × guard(内容) × stage/去向`，矩阵只用来做展示（`EgressGate.checkProvenance` 的参数就是这个三轴的展开）。

### 5.3 实测收益接近 0，但不该删

`viz/README.md` §5.7：592 个有文本块里 **583 块掩码前后逐字相同**，5 块被改，`guard_hit` 全天只有 2 次。解释：日常使用中真正命中的极少，**这类机制的价值在「唯一出口 + fail-closed + 审计」，不在命中率**。不能因为没命中就删掉，也不能因为存在就宣称安全——「打了标记但基本没命中」是必须被看见的事实。

### 5.4 myco 第一天就该留的接口（不必第一天就实现）

1. **唯一出网点**：`egressPort.check({text, sourceId, stage, target}) → {allowed, sanitizedText, tier, reason, auditId}`；任何模型/网络调用绕过它即为 bug。
2. **纯函数 guard**（无模型）：卡号/身份证/密码/验证码/CVC + 手机/邮箱；**未知来源/未知内容按最严格处理**。
3. **来源 provenance**：每个 memory item / observation 带 `sourceId`；策略按来源算，多来源取最严。
4. **审计不存原文**：只存 hash/长度/计数/策略名——否则审计库就是第二份 PII。
5. **图片单独通道**：截图可能含 PII，文本 guard 管不到；先留「图不出网 / 出网前掩码 / 局部打码」三态开关。
6. **reason 码枚举**（`blocked_by_guard` / `never_cloud` / `redaction_unavailable` / `mask_denied`）供上层措辞，禁止上层猜。

## 6. 对 myco 记忆层：抄 / 改 / 别抄

前提重申：该仓库**不构成约束**，「只用契约、不继承实现」的具体含义是——
**(a)** 接受 5 条语义（freshness 必返；空≠没有；确定性；证据可失效且必须报 reason；能力协商按能力降级而非猜版本号）；
**(b)** 把它的**负面结论**当成设计 checklist，而不是把它的代码路径当基线；
**(c)** 验收自己造：用合成数据 + 故障注入（停采集 / 删证据 / 时钟跳变 / 索引滞后）验证契约，不复用它的 77 个测试。

### 6.1 抄（低成本、高收益）

| 抄什么 | 具体形态 |
|---|---|
| `Freshness` 三态 | `{projectedUntil, lagMs, state}` + `derive()` + `caveat()`；阈值按数据源类型配置 |
| `Coverage` | `{scopeFrom, scopeTo, coveredMs, gaps[], truncated, shownRange}`，**gaps 不允许 null** |
| 「空 ≠ 没有」三态 | 每个检索结果返回 `status: ok / not_indexed / not_covered / stale`，禁止只给空串/空数组 |
| 证据失败 reason 码 | `evidence_evicted` / `evidence_missing` / `mask_denied` / `evidence_unavailable` / `not_supported` |
| `block`/`visit` 分离 | 内容寻址去重 + 时间寻址永不合并；「一天去两次」必须两条 visit |
| 三值 NULL | 观察缺失 = `null` = 未知，不得写 0/空串 |
| 确定性排序 | `score desc → -t0 → id`，无随机、无隐式当前时间 |
| 两库分离 | 便携 canon（记忆）vs 本地证据（可 GC）；悬空 evidenceRef 必须容忍并报 reason |
| 唯一出网点 + fail-closed + 审计非原文 | 见 §5.4 |
| 能力协商 | `schemaVersion` + `capabilities[]`；未知版本**拒绝**而不是自动迁移 |

### 6.2 改（照搬会水土不服）

| 原设计 | 改成 | 理由 |
|---|---|---|
| `LineRef` 的 `yTop/xLeft/xRight` | 通用 `bbox: {x,y,w,h}` 或干脆只留 `anchor` | 安卓专用坐标语义；桌面/相机/视频各有自己的定位 |
| `blockKey = H(pkg + 文本集合 + grid)` | `H(surfaceId + 文本集合 + 图像指纹)`，`surfaceId` 抽象为窗口/相机/视频源 | 桌面无「pkg=应用」这一层 |
| visit 边界「>20min / pkg 变化 / 回同一 pkg 即新 visit」 | 事件源可配置 + **每条边界记录 reason**（超时/失焦/输入空闲/源切换） | 20min 是手机注意力节奏，桌面不同 |
| freshness 阈值 10min/30min | 按源配置：本地事件流秒级、桌面向导分钟级、外部同步小时级 | 阈值是节奏的函数，不是常量 |
| `presence: absent/present/shared` | 保留三态，但语义明确为「谁在观察：user / agent / 两者」，且**先只实现两态** | INTERFACE §10.3 自己也没定 shared 副本 |
| `long` id | `ULID`/UUID 字符串 | 跨设备合并、离线生成都需要 |
| L0–L3 金字塔 | 不抄层级编号，用「原始观察 / 视图」两层 + 可插拔投影 | 文档自曝三套 L0–L3 编号互相冲突（ARCHIVE-INDEX §6） |

### 6.3 别抄

- **12 个 Gradle 模块的切法**（`:core/:guard/:trace/:usage/:egress/:models/:agent/:masker/:memory/:pcharness/:app`）——这是安卓宿主形态的产物。
- **Android 采集实现**（A11Y 服务、`takeScreenshot`、ML Kit OCR、MediaProjection 决策）——平台约束不同，抄了就是负资产。
- **`object_messages` 兼容投影**、`inferTitle` / `frameObjectKey` / `Role` 七分类——作者自己已删/判死。
- **BM25 + IDF + tieBreak + decay + weight 这一层**：`weight`/`recall_count` 在真实数据里从未运动（viz §5.4），等于**未经数据验证**；检索先用可解释的文本匹配，别急着上打分体系。
- **三套日叙事 schema、L2 抽取 prompt 规格**——文档自相矛盾且图片计费口径已过期。
- **把判断写进采集层的任何字段**（A1 禁令）：这是整份设计最贵的教训，也是 myco 最容易重犯的错。

### 6.4 `com.dsh.contract` 那 3 个 Java 文件值得 TS 化吗？

**值得，但只值类型与语义，不值实现**（3 文件、零依赖、约 270 行）。逐文件判断：

| 文件 | 判决 | 怎么改 |
|---|---|---|
| `Freshness.java` | **1:1 抄** | 约 40 行 TS；保留 `derive()` 与 `caveat()`，阈值提为构造参数 |
| `Recall.java`（Request/Result/Coverage） | **抄字段名，改实现语义** | `answer` 从 `string` 改成判别联合；`gaps` 必填；`agentPresent` → `observerFilter`；时间用 epoch 不用「日」字符串 |
| `MemoryReader.java`（BlockView/FrameView/LineRef） | **抄三角关系与 reason 枚举** | 内容块 / 行 / 证据引用的关系保留；位置字段与 `shotRef` 改通用 `evidenceRef`；失败统一 `ok:false + reason`（`readBlock` 返回 null 的写法不要抄，两套失败风格不一致） |

TS 草案（可直接落地，约 40 行）：

```ts
export type FreshnessState = 'ok' | 'catching_up' | 'stalled';
export interface Freshness {
  projectedUntil: number;      // 已处理到哪个时间点
  lagMs: number;               // now - projectedUntil；从未处理过为 -1
  state: FreshnessState;
}
export interface Coverage {
  scopeFrom: number; scopeTo: number; coveredMs: number;
  gaps: Array<[number, number]>;   // 必填，空数组表示无空档
  truncated: number; shownRange: string;
}
export type RecallStatus = 'ok' | 'not_indexed' | 'not_covered' | 'stale';
export interface RecallResult {
  status: RecallStatus;            // 代替"空串"：四态互斥、可测
  answer: string;                  // 只放事实摘要，不承担"没找到"的表达
  ids: string[];
  coverage: Coverage;
  freshness: Freshness;
}
export type FrameReason = 'evidence_evicted' | 'evidence_missing'
  | 'mask_denied' | 'evidence_unavailable' | 'not_supported';
export type FrameView =
  | { ok: true; image: Uint8Array }
  | { ok: false; reason: FrameReason; description?: string };
export interface MemoryReader {
  recall(req: { query?: string; object?: string; from?: number; to?: number;
                sourceId?: string; limit?: number }): Promise<RecallResult>;
  readBlock(id: string): Promise<BlockView | null>;
  readFrame(id: string): Promise<FrameView>;
  freshness(): Promise<Freshness>;
}
```

## 7. 未解问题（含你的倾向）

1. **freshness 粒度**：每工具结果一个，还是每数据源一个？**倾向**：每数据源各一个，结果里聚合取最差；否则一次 recall 里混了新鲜与陈旧来源就无法表达。
2. **`answer` 由谁生成**？trace-memory 是模板 render（非模型）所以可确定性测试；myco 若让模型叙述，必须把 `status/freshness` 与叙述分开，**叙述不得改写状态**。**倾向**：`answer` 只放事实摘要，叙述交给上层。
3. **visit 边界在桌面怎么定**？窗口切换/多显示器/同窗口滚动都比手机细。**倾向**：前台窗口签名 + 输入空闲 + 源切换，先不做内容相似度；每条边界记 reason。
4. **隐私边界**：桌面屏幕记忆必然采到凭据/密钥/私聊。**倾向**：默认不出网、截图默认不落盘（或加密落盘）、全局暂停键；接口先留后实现。
5. **「如果以后做手机端屏幕记忆，应从哪一步开始」**（探索顺序，与直觉相反）：
   - **第 0 步（不做采集）**：先做设备无关的 `observation/visit/block` 存储 + 回放 + 故障注入测试（合成数据即可）。这一步与平台无关，也是最容易做对的一步。
   - **第 1 步（桌面单窗口）**：AX 树 + 事件流（Electron/Chromium AXTree、Windows UIA），验证 `parent_idx`、三值 NULL、双判据融合——桌面的「文本多」恰好是像素法的反例场景。
   - **第 2 步（加截图与像素）**：连续截图 + grid + 双判据 + 静态层分离，先把「拼接可用率 / 漏采率」做成可测指标。
   - **第 3 步（最后才安卓）**：挑**最难的负例**先走——微信这种空 A11Y 树 App，验证 OCR 行匹配 + 像素兜底；再处理 `takeScreenshot` 的 1s 硬限与无障碍被系统关闭的假死（freshness 的 `stalled` 就是为它准备的）。
   - **不要**从 A11Y 采集服务开始——那是最贵、最平台绑定、最容易把判断写死的一层。
6. **是否保留 `presence=shared` 双写**（INTERFACE §10.3）？**倾向**：先只记 user/agent 两态，shared 等有真实视频通话场景再定，避免为设想中的用例加字段。

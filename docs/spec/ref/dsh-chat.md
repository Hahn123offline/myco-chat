# DSH 聊天面板事实库（P1 · 只读审计）

> 审计对象：`E:\deepseek-harness\packages\client\{ui-chat,ui-renderer,ui-tool,ui-message-feedback}/src`
> 对账分母：`_ws/out/findings-dsh-chat.csv`（本路 4 个包共 **559** 条，其中 `src/` **242** 条、`tests/` **317** 条）
> 事实库：`docs/spec/ref/dsh-chat.facts.csv`（559 行；E0/E1 **158**、显式排除 **401**、uncovered **0**）
> 对账报告：`_ws/out/coverage-dsh-chat.md`
>
> **路径缩写**（§2/§5 内使用；`facts.csv` 内一律写以 `packages/client/` 为根的完整相对路径）：
> `C/` = `ui-chat/src/client/chat/` · `S/` = `ui-chat/src/client/settings/` · `P/` = `ui-chat/src/client/`
> `N/` = `ui-chat/src/client/conversation-nodes/` · `T/` = `ui-tool/src/client/tool/`
> `TC/` = `T/components/` · `TV/` = `T/toolviews/` · `F/` = `ui-message-feedback/src/client/`
>
> 依据级别：**E0** 源码行/真机 · **E1** 文档/注释声称 · **E2** 推测（只允许出现在 §4）
>
> **行号简写约定**：同一单元格内先给出带路径的文件名后，其后单独的反引号行号（如 `` `:24-26` ``）
> 一律指该文件；跨文件的引用始终写全 `路径:行`。全文 492 处行号引用已机器校验 `行号 ≤ 文件总行数`（0 越界）。

---

## 0. 一页结论

10 条核心设计取舍（每条带证据行）。**#6 回答 Q-07，#1/#3/#9 回答 Q-05。**

1. **过程组封顶是「双上限」而不是比例或行数**：`max-height: min(400px, 50vh)`，
   `scrollbar-gutter: stable`；展开态（Detailed 的 running 轮 / Verbose）整条封顶被移除
   （`C/ChatGroupSeat.module.css:86-92`、`:135-139`；E1 复述见 `N/README.md:182`）。
   → 我旧拍板的「屏高 45% / ≤360dp」是 E2，真实值见 §5.1。

2. **到边渐隐 = 24px 的 mask 渐变，不是高度、不是透明度参数**：上/下各 24px，
   两端同时可滚时合并为一条四段渐变；是否加类只看 `top > 1` / `top < floor-1`
   （`C/ChatGroupSeat.module.css:101-117`；`C/ChatGroupSeat.tsx:182-184`；`C/use-process-scroll.ts:48`）。

3. **DSH 的过程组内滚「不吸收残余速度」，与我的 G-04 相反**：`overscroll-behavior-y: auto`
   （`C/ChatGroupSeat.module.css:90`），`onWheel` 只调 `interrupt()` 取消**跟随动画**，
   不 `preventDefault`/`stopPropagation`（`C/use-process-scroll.ts:53-65`）；README 明写
   “Wheel scrolling can continue into the outer transcript at an edge”（`N/README.md:182`，E1）。
   → G-04 是**用户主动偏离 DSH** 的决定，需在面板 spec 里标注为「故意不一致」。
   对照：轮次导航条的内滚用的是 `overscroll-behavior: contain`
   （`C/TurnNavigator.module.css:54`）——同一份代码里两种口径并存。

4. **组内跟随与会话跟随是两套独立控制器**：组内阈值 **1px**（`C/use-process-scroll.ts:26,48`），
   会话阈值 **24+1=25px**（`C/use-chat-reading.ts:7,252`）。手动打开组的落点按「组是否
   已关闭」分叉：未关闭 → 贴底并继续跟随；已关闭 → 贴顶且跟随关闭
   （`C/ChatGroupSeat.tsx:175-178`；`C/use-process-scroll.ts:36-39`）。

5. **会话跟随的判据是「距底 ≤25px」而不是「用户是否上翻」**：读者移动且距底 <1px 立即恢复跟随，
   否则起 500ms 采样定时器，采样点按 `nearBottom` 重新判定；跟随中把会话位置**存成 null**
   （语义位置），不跟随才存具体锚点（`C/use-chat-reading.ts:134-144,143,186-192,218-234`；
   `C/use-scroll-follow.ts:67,90-95`）。

6. **Q-07：DSH 不是「v 钮 vs 轮次导航」二选一，两者同时存在**——
   ① 回到底部：34×34 圆形 overlay 按钮，**仅当不跟随时渲染**，右下角 16px（宿主滚动时再抬
   `--dsh-composer-height`），z-index 8 压在输入卡上方（`C/ChatView.tsx:240-251`；
   `C/ChatView.module.css:244-294`）；
   ② 轮次导航：右侧 28px 竖条、**10px 定距**刻度（20×2px tick）、hover/focus 出预览卡
   （1 行 prompt + 3 行 response）、虚拟滚动 + 24px 两端渐隐、`overscroll-behavior: contain`，
   **容器宽 ≤900px 直接 `display:none`**（即移动端没有轮次导航）
   （`C/TurnNavigator.tsx:29-33,110-177,227-281`；`C/TurnNavigator.module.css:26-46,50-78,207-211`）。
   → 「myco 现在是单个悬浮 v 钮」并不需要被替换掉，缺的是窄屏以外的轮次导航层。

7. **消息操作条 = 复制 /（插件动作）/ 分支 / 用量，28×28 图标按钮**；
   默认**不可见但仍占位**：仅在 `@media (hover: hover)` 且标记 `data-actions-reveal=hover`
   时 `opacity: 0`，hover 或 `focus-within` 恢复；触摸设备不进入该媒体查询 → **全部常驻可见**。
   轮尾「以回复结尾」的轮次标 `always`，否则 `hover`（`C/MessageIconActions.module.css:5-10,42-61,63-87`；
   `C/TurnTailNodeView.tsx:60`）。分支不可用时不用原生 `disabled`，因为 Tooltip 需要 hover/focus
   （`C/MessageIconActions.tsx:93` 注释 + `:91-106`）。

8. **轮内节点顺序由 `process-groups.ts` 的一条循环决定**（E0）：
   独立根集合 `user/steering/turn-trigger/model-retry/turn-error/turn-max-tokens/turn-tail` 出现即
   `flush` 成段并单独渲染；`turn-process` 控制条**不切段**，而是锚到「它折叠的第一个成员」的位置；
   assistant 的 reasoning 进组（`groupPart='reasoning'`）、reply 先 `flush` 再独立成
   `groupPart='response'`；其余（工具等）一律进组（`N/process-groups.ts:13,157-179`）。
   组间距：组内 6px，assistant 回复前后 12px，turn-process 与其内容 16px
   （`C/ChatView.module.css:72-84,111-116`）。

9. **思考行不是「每轮最多 1 个」**（金标准样本字段 1 需纠正）：`AssistantMarkdown` 对每个
   `reasoning` block 各渲染一个 `ReasoningRow`，一轮可 0..N 个，且每个实例的展开态各自独立
   （`C/AssistantMarkdown.tsx:76-106`；`C/use-disclosure.ts:11-20,28-34`）。
   另：Compact 模式下**静态思考行不显示摘要**，只剩标题「思考」
   （`policy.settledReasoningPreview=false`，`P/presentation-policy.ts:29-35`；
   消费处 `C/ReasoningRow.tsx:58-59`）。

10. **不存在统一的「四态（等待/思考/输出/完成）」枚举**（该提法是 E2，非 DSH 实现）：
    DSH 是三层并行——① Session 运行指示条：鲸鱼尾 + shimmer「深度求索中，用时 N ···」，
    整秒刷新、起始 1 秒、≥60 分钟才出小时（`C/RunningStatus.tsx:20-40`；
    `C/message-chrome.ts:54-64`；文案 `P/locale.ts` `chat.deepDiving*`）；
    ② 组标题 live 文案：`准备…` / `正在…` / `正在分析请求`，最短驻留 150ms（`C/ChatGroupSeat.tsx:28,59-82,109-114`）；
    ③ 轮结束控制条：`已完成，用时 …` / `处理失败` / `已停止`（`C/TurnProcessNodeView.tsx:21-32`）。

补充（工具卡，任务点 6）：**没有「原始 JSON」视图**。工具行只有「折叠摘要 + 形态化卡」
（terminal / diff / read / search / web / details / image / ask-question），未知面才落到 `JsonBlock`
兜底（`T/components/ToolRow.tsx:169-175,254-341`；`C/MessageItem.tsx:166-171,381-391`）。
长输出截断值：diff 9 行 / read 8 行 / search 8 行（`T/models/diff-card-model.ts:8`、
`read-card-model.ts:17`、`search-card-model.ts:12`），terminal 输出 224px、IN/OUT 各 150px、
代码体 260px、details 列表 320px（`T/components/ToolRow.module.css:172-175,193-201,303-308`；
`T/components/ToolDetails.module.css:11-17`）。

---

## 1. 包 → 面板归位表

| dsh 包 | 归位到 myco 面板 | 内容 / 证据 | 本路 findings |
|---|---|---|---|
| `ui-chat` | **`chat`**（主）+ `settings`（子页） | 轮/组/消息/滚动/导航/统计的绝大部分；`S/PreferenceRow`、`S/TranscriptViewRow`、`S/PerformanceUsageRow`、`S/LinkOpeningRow` 属设置页 | src 242 条中的 ~150 |
| `ui-tool` | **`chat.tool`**（工具卡区域） | ToolRow / ToolDetails / ToolCallTree / 9 个 toolview（含 bash sample） | src ~87 |
| `ui-message-feedback` | **`chat.msg`**（消息操作条内嵌）+ 一个 `sheet-overlay` 弹窗 | 点赞/点踩 + 反馈弹窗/Toast，通过 `conversation.chat.assistant-actions` 槽注入 | src 10 |
| `ui-renderer` | **`base.primitives`**（槽渲染运行时，不单独成面板） | **src 内 0 个交互点**：`registry.ts`(715) / `scoped-slots.tsx`(1256) / `bindings.tsx`(158) 全是槽注册与作用域透传；10 条 findings 全在 `tests/` | src **0** |

> 归位结论：本路 4 包只产出 **2 个面板/区域**（`chat`，含 `chat.tool`、`chat.msg` 两个子区），
> 外加 `settings` 的 1 个共享行组件。`ui-renderer` 不产出面板节点（它提供的是「槽」这一层，
> 属 `base.primitives`）。

---

## 2. 节点事实表（25 个节点 · 每个 13 字段）

> 字段 12「依据」逐条给 `E0/E1` + 行号；字段 13 必含反证。节点 ID 与 `facts.csv` 的 `node_id` 一一对应。
> 覆盖统计见文末附注：`facts.csv` 中 158 条 E0/E1 全部落到下列 25 个节点。

### 2.1 `chat.turn.process-control` — 整轮过程折叠控制条

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.turn.process-control`。所属 chat → 轮 `turn` → 过程区顶部。**仅当 Turn 已关闭**才渲染（`turn?.status !== 'closed'` 直接 `return null`，`C/TurnProcessNodeView.tsx:18`）。每轮最多 1 个；在轮内位置 = 它折叠的第一个成员之前（`N/process-groups.ts:168-170` 把 `turn-process` 的渲染锚定到 `pending[0]`）。 |
| 2 | 网格与定位 | 父：turn 纵向栈，`width:100%`；高度 `calc(33px + --dsh-content-font-delta)`，`padding: 0 0 8px`，底部 `0.5px` 分隔线（`C/TurnProcessNodeView.module.css:7-11`）。行内：文案单行 `ellipsis`，行高 `24px + delta`（`:41-48`）；chevron 14px、`margin-left:4px`（`:28-35`）；用时数字用等宽 `tabular-nums`（`:50-53`）。 |
| 3 | 命中区 | 整行 `<button>`，命中 = 行宽 × 33px（鼠标精度）。`!canCollapse` 时 `disabled` → 整行不可点（`C/TurnProcessNodeView.tsx:44`）。33px < 44dp，移动端需按 Q-03 扩边（E2）。 |
| 4 | 手势 | 单击 = 切换。键盘 Enter/Space（原生 button 默认行为）。**无**长按/双击/滑动/拖动。点前先 `event.currentTarget.focus()`，收键盘焦点（`:46-48`）。与父滚动的仲裁：无自定义逻辑，竖直拖动归外层会话滚动。 |
| 5 | 行为 | 状态迁移：`turnProcess.setOpen(!open)` → 写入 **store**（`actions.setTurnProcessOpen(spec.turn, spec.answerStep, open)`，`C/ChatNodeSeat.tsx:59-69`），不是组件本地 `useState`。隐藏效果 = 该轮过程成员被 `display` 隐藏（`C/ChatNodeSeat.tsx:109` `processHidden`）。幂等、可撤销；不发命令、不导航。 |
| 6 | 视觉状态 | default `color: tertiary`；`hover:not(:disabled)` → `secondary`（`C/TurnProcessNodeView.module.css:24-26`）；`disabled` → `cursor: default`（`:20-22`）；`data-open` → chevron `rotate(180deg)`（`:37-39`）；无 running 态（只有 closed 轮会出现）。`transition: color 100ms`（`:17`），reduced-motion 取消（`:55-59`）。 |
| 7 | 布局占位与展开形态 | 它不是展开体，而是**成员显隐开关**：始终占 33px；展开时把「过程成员」放回布局流，下方最终答案与后续轮整体下移（成员自身形态见 2.2/2.3）。按 G-03 归类：**控制条（无自身展开体）**，其成员组合形态由 2.3 决定。 |
| 8 | 滚动语义 | 随外层会话滚动，自身无滚动容器；G-04 不适用。 |
| 9 | 动效 | 仅 chevron 180° 旋转 100ms ease；无高度动画。过程成员的出现/消失由外层的 fold-motion 负责（`C/flow-motion.ts`，本节点不参与）。 |
| 10 | 边界态 | ① `aborted`/`error` → 不显示用时，只显示「已停止」/「处理失败」（`:23-29`）；② `turn.start` 缺失（分页未加载 start）→ `elapsedMs===undefined` → 文案退化为「已完成」（`:21-28`）；③ 用时 <1000ms 钳到 1s（`:21-22`）；④ `hasContent=false` → `aria-expanded` 不输出（`:45`）、chevron 不显示（`:57`）；⑤ 大字号：高度/行高随 `--dsh-content-font-delta` 同步增长；⑥ 有 `aria-live` 播报（`:30-35`）。 |
| 11 | 无障碍 | `button` + `aria-expanded`（仅 hasContent 时输出）；**无 `aria-controls`**（控制条不指向某个容器），改为带 `data-turn-process` / `-messages` / `-tool-calls` / `-subagents` 计数属性（`:40-43`）；另有视觉隐藏的 `role="status" aria-live="polite" aria-atomic="true"` 播报「已完成/处理失败/已停止」（`:30-35`）。焦点顺序：整轮过程区第一个可聚焦元素。 |
| 12 | 依据 | **E0** `C/TurnProcessNodeView.tsx:14,18,19,21-32,36-57`；**E0** `C/TurnProcessNodeView.module.css:7-59`；**E0** `P/contract/turn-process.ts:69-74`（live/aborted/error 不可折叠）；**E0** `N/process-groups.ts:168-170`；**E0** `C/ChatNodeSeat.tsx:59-69,109`；**E1** `N/README.md:86-95,107`（时长/暂停语义）。 |
| 13 | 反证 + 验收 | **反证**：若把整轮折叠态也做成「随轮次版本重置的本地态」（像思考行那样），切分支/重进会话后用户会看到自己没关过的过程被关掉（或反之），折叠意图丢失 = 每次回看都要重新点一遍；若 `!canCollapse` 时仍渲染 chevron 与 `aria-expanded`，屏幕阅读器会把纯状态行读成「可展开」→ 用户按下去没反应。**验收**：`CHAT-10` 关闭轮可折叠 / 运行中与 failed、stopped 不可折叠（真机+单测）；`CHAT-11` 分页缺 start 时不显示假时长（夹具比对）。 |

### 2.2 `chat.group.header` — 过程组标题（组折叠开关）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.group.header`。所属 chat → turn → 过程组。出现条件：`grouped === true`，即 `stepGrouping==='collapsed'`，或 `==='history'` 且该轮已结束且未处于 defer（`C/ChatGroupSeat.tsx:147-148`）；隐藏时 `useGroupHeaderAction(headerRef, !grouped || …)` 把 header 摘掉（`:164-166`）。每组 1 个（verbose 模式 `stepGrouping:'none'` 无 header，`P/presentation-policy.ts:50-56`）。 |
| 2 | 网格与定位 | 行内 flex，`gap:6px`；leading 是 `16px + delta` 的方形槽（活动图标与 chevron 叠放，`:25-46`）；标题**用正文字号 14pt**（不是 secondary ），单行 `ellipsis`（`:94-99`）；`color: tertiary`。展开时 `padding-bottom: 8px`（`:82-84`）。 |
| 3 | 命中区 | 整行 `<button>`，命中 = 标题行内容宽 × 行高（≈16–24px，无显式 `min-height`）。远小于 44dp → Q-03。 |
| 4 | 手势 | 单击 = 切换（`C/ChatGroupSeat.tsx:117`，点击即 focus 自身）。hover / `focus-visible` 时 leading 的活动图标淡出、chevron 淡入（`C/ChatGroupSeat.module.css:66-72`）。无长按/双击/滑动。 |
| 5 | 行为 | `toggle()`：先 `initialize(closed === false ? 'bottom' : 'top')` 定内滚初始位，再 `setOpen(!open)`（`C/ChatGroupSeat.tsx:175-178`）。展开态来自 `useDisclosure()`（本地、非存储，`:137`）；`outerHidden` 折叠动画中会强制 `setOpen(false)`（`:167-169`）。幂等可撤销。 |
| 6 | 视觉状态 | default：活动图标 + tertiary；hover/focus-visible：换 chevron + secondary（`:21-23,66-72`）；open：chevron 常显（换成 up）+ `padding-bottom:8px`（`:74-84`）；组未关闭（live）时标题走 `TextShimmer` 流动（`C/ChatGroupSeat.tsx:124-126`）；`prefers-reduced-motion` 取消 transition（`:119-125`）。 |
| 7 | 布局占位与展开形态 | **形态 B（capped-inner-scroll，G-03）**的头部：折叠 = 只留 header（body 隐藏，成员 `data-turn-process-hidden`）；展开 = body 出现并封顶内滚（见 2.3），下方兄弟整体下移。header 自身高度不随文案变化（`ellipsis` 单行）。 |
| 8 | 滚动语义 | 自身不滚；它决定 body 的初始滚动位置（见 2.3 字段 5）。 |
| 9 | 动效 | `color`/`opacity` 100ms ease；reduced-motion 关闭。成员间距变化 `margin-top` 160ms `cubic-bezier(.4,0,.2,1)`，仅在有 `[data-chat-motion]` 时启用（`C/ChatGroupSeat.module.css:141-149`）。 |
| 10 | 边界态 | ① closed 组：标题 = 类别计数排序拼出的成品文案（「已读取文件，执行了命令」），**不显示计数**；② 未关闭组：用「最新活动」文案 + Standard 模式追加 detail（` · detail`），无活动 → 「正在分析请求」；③ preparing → 「准备…」；④ >3 类 → 「X等」；⑤ `data === undefined` → `return null` 不占位（`C/ChatGroupSeat.tsx:108`）；⑥ live 标题最短驻留 150ms，只保留最新待显示标题（`:28,59-82`）；⑦ 大字号：leading 与字号随 `--dsh-content-font-delta`。 |
| 11 | 无障碍 | `button` + `aria-expanded` + `aria-controls={bodyId}`（`C/ChatGroupSeat.tsx:116`）；leading（图标+chevron）`aria-hidden`；标题文本本身即可达名。 |
| 12 | 依据 | **E0** `C/ChatGroupSeat.tsx:28,59-82,102-128,137,147-148,164-166,175-178,182-184`；**E0** `C/ChatGroupSeat.module.css:5-23,25-46,48-84,94-99,119-125,141-149`；**E0** `P/presentation-policy.ts:28-57`；**E1** `N/README.md:145-182`（标题合成与类别表）。 |
| 13 | 反证 + 验收 | **反证**：若 header 高度随 live 标题（detail 长短）变化，每来一个工具/每段 reasoning 都会推动下方正文 → 读者视线被逐字推走（G-02 违反）；若 live 标题不设 150ms 最短驻留，连续工具切换会让标题每帧闪变，用户读不到当前在干什么。**验收**：`CHAT-12` live 标题更新时 header 高度与下方内容 y 不变（真机录屏）；`CHAT-13` closed 组标题只列类别、无计数、>3 类带「等」（截图比对 + 文案单测）。 |

### 2.3 `chat.group.body` — 过程组封顶内滚体（★ Q-05 主体）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.group.body`。所属 chat → turn → 过程组。出现条件：组已展开；`capped = grouped && (!deferCompletedTurns || open)`（`C/ChatGroupSeat.tsx:182`）。每展开的组 1 个（`id={bodyId}` 供 header `aria-controls`，`:194`）。 |
| 2 | 网格与定位 | **封顶 `max-height: min(400px, 50vh)`**，`overflow-y: auto`，**`scrollbar-gutter: stable`**（`C/ChatGroupSeat.module.css:86-92`）。内容纵向 flex，成员间距 `6px`（`:127-134`）。展开态 `expandedBody`：`max-height:none; overflow:visible; scrollbar-gutter:auto`（`:135-139`）。 |
| 3 | 命中区 | 容器不可点，只有滚动条与内容内的可点子节点。滚动条宽由 `scrollbar-gutter: stable` 预留（Web 约 6–15px）。 |
| 4 | 手势 | 五路绑定（`C/use-process-scroll.ts:23,57-65`）：`onScroll`→ 重算到边；`onWheel` / `onTouchStart` / `onPointerDown` / `onKeyDown`(Arrow/Page/Home/End/Space 且未 `defaultPrevented`) → **`interrupt()`（仅取消跟随动画，不拦事件）**；另有 `scrollend` 监听（`:77-78`）。**到边不吸收残余速度**：`overscroll-behavior-y: auto`（`C/ChatGroupSeat.module.css:90`）→ 滚动链默认传给外层；E1 复述 `N/README.md:182`「Wheel scrolling can continue into the outer transcript at an edge」。 |
| 5 | 行为 | 展开/收起由 2.2 驱动；body 只维护 `{canScrollUp, canScrollDown}` 与跟随意图。初始落点：**未关闭组 → 贴底 + `setFollowing(true)`；已关闭组 → 贴顶 + `setFollowing(false)`**（`C/use-process-scroll.ts:36-39`；调用处 `C/ChatGroupSeat.tsx:176`）。 |
| 6 | 视觉状态 | 到边状态驱动 mask 类：`canScrollUp` → `fadeTop`，`canScrollDown` → `fadeBottom`，两者都有 → 一条四段渐变（`C/ChatGroupSeat.module.css:101-117`）。阈值恒 1px（`top > 1` / `top < floor-1`，`C/use-process-scroll.ts:48`）。无 hover/focus 样式（容器不可聚焦）。 |
| 7 | 布局占位与展开形态 | **形态 B（capped-inner-scroll，G-03）**：展开占位 = min(内容高, min(400px,50vh))；下方兄弟整体下移。取消封顶时（Detailed running / Verbose）变回「随内容长高」。**不适用 G-10 的 240dp 动画阈值**：这里是滚动容器而非展开内容。 |
| 8 | 滚动语义 | 内滚优先；到边**不拦**（见字段 4）。组内跟随只在**本组滚到底**时生效，与会话跟随互相独立（阈值 1px vs 25px）；内容增长时若在底 → `toBottom(smooth)`，否则不动（`C/use-process-scroll.ts:41-46`）。`ResizeObserver` 同时观察 body 与 content（`:76-80`）。 |
| 9 | 动效 | 渐隐是 mask 的静态切换（无 transition 声明）→ 立即切换；成员间距 `margin-top 160ms`（`C/ChatGroupSeat.module.css:141-149`）；`prefers-reduced-motion` 全部关闭。 |
| 10 | 边界态 | ① 内容不足一屏：无渐隐、无滚动条，但 gutter 仍预留（`stable`）→ 不会在「刚好超一屏」时横向跳动；② 视口变矮（`50vh` 变小）→ 封顶自动收紧；③ 大字号：可视行数减少，封顶不变；④ 组为空：不产生组（group 生成需 member，`N/README.md:211` E1）；⑤ 组被关闭/重建：`follow.reset()` 且若 `!grouped || !open` 清掉待定位（`C/use-process-scroll.ts:67-71`）。 |
| 11 | 无障碍 | 普通 `div` + `id`（供 `aria-controls`），**无 `role`、无 `tabindex`** → 键盘能否滚动取决于浏览器默认（Chromium 可聚焦滚动容器但未显式声明）→ 见 §4 `Q-CHAT-02`。 |
| 12 | 依据 | **E0** `C/ChatGroupSeat.module.css:86-92,101-117,127-149`；**E0** `C/ChatGroupSeat.tsx:174,182-184,194-200`；**E0** `C/use-process-scroll.ts:23,26,36-39,41-48,53-88`；**E1** `N/README.md:182,184`（“`min(400px, 50vh)` 高度上限与 24px 方向渐隐”“开着的封顶组只在自身贴底时跟随内容增长”）。 |
| 13 | 反证 + 验收 | **反证**：① 若封顶用固定 dp（如 360px），横屏/小屏会占掉半屏以上，正文被挤没；若用纯 `50vh`，大屏变成 500px+ 巨型窗口，用户会以为「整段工具输出就这么多」。② 若内滚容器不设 `scrollbar-gutter: stable`，内容从「不足一屏」到「超一屏」时整列横向跳动 8–15px —— 读者正在读的那一行会横移。③ 若到边仍按 DSH 的「传链」处理，用户在内层滑到边缘后整个会话继续滑，丢掉观察点（这正是 G-04 的用户原话），故 **myco 必须故意偏离 DSH**。**验收**：`CHAT-14` 400px/50vh 双上限（1440 高与 640 高设备实测）；`CHAT-15` 渐隐 24px 且不出现滚动条抖动（截图比对）；`CHAT-15b`（G-04）内滚到边后外层不动（真机手势）。 |

### 2.4 `chat.msg.thinking-header` — 思考行（★ 复核 + 补全金标准样本）

> **复核结论（对 `docs/spec/schema-node.md` §2 的 3 处修正 + 2 处补全，不推翻任何已有 E0）**
>
> | 项 | 原样本 | 复核结果 | 证据 |
> |---|---|---|---|
> | 字段 1「每轮最多 1 个」 | 最多 1 | **错误**：每个 `reasoning` block 一行，一轮可 0..N 个 | `C/AssistantMarkdown.tsx:76-106`（循环内每 block 一次 `ReasoningRow`）；`N/process-groups.ts:172` |
> | 字段 12 依据 | `ReasoningRow.tsx:38-39` 记为 E0 | **应为 E1**：那两行是 JSDoc（“Mode changes toggle CSS display without unmounting”） | `C/ReasoningRow.tsx:34-47` 是注释块 |
> | 字段 2 行高 | 「行高 20」 | 应为 `calc(20px + --dsh-content-font-delta-secondary)`（随字号档位增长） | `C/ReasoningRow.module.css:49` |
> | 字段 1 轮内位置 | 「在 assistant 正文之前（P1 补 E0）」 | **补齐见下**：默认是**过程组成员 `groupPart='reasoning'`**；最终回答自带 reasoning 时在回答行内且默认隐藏 | `N/process-groups.ts:172`；`C/AssistantNodeView.tsx:28-32` |
> | 新增 E0 | — | **Compact 模式静态思考行无摘要**（只剩「思考」标题）；`data-preview` 由 `!expanded && summary!=='' && (running \|\| settledReasoningPreview)` 决定 | `C/ReasoningRow.tsx:58-59`；`P/presentation-policy.ts:29-35` |
>
> 原样本的其余 E0（`contain:size layout`+24px 定高、sticky header、`**` 剥离、48px 流式渐隐、`--dsh-content-font-delta` 随档、二级字号+22px 缩进）逐条核对**全部成立**，不做改动。

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.thinking-header`。所属 chat → 轮 → **过程组** → 成员（assistant-step 内的 reasoning block）。出现条件：该 assistant-step 存在**非空白** reasoning block（`blocks.some(kind==='reasoning' && text.trim()!=='')`，`N/process-groups.ts:21-24`），且该块未被 inline 隐藏。**每轮 0..N 个**：每个 reasoning block 各自一行（`C/AssistantMarkdown.tsx:76-106`）。**轮内位置（E0，补齐原样本缺项）**：① 常规情况 = 过程组成员 `groupPart='reasoning'`，渲染在组 body 内（`N/process-groups.ts:172`；`C/ChatNodeSeat.tsx:77-82`）；② 该 assistant 是最终回答且 reasoning 与正文同属一个 step（`spec.inlineReasoning`）→ 思考块在**回答行内**，默认被隐藏，只有打开整轮过程才显示（`C/AssistantNodeView.tsx:28-32`；`C/AssistantMarkdown.tsx:94-104`）。 |
| 2 | 网格与定位 | 父：组 body / assistant 正文的纵向栈，续行左对齐。折叠态根节点 `contain: size layout` + **固定高 `calc(24px + --dsh-content-font-delta)`**（`C/ReasoningRow.module.css:6-9`）。行内：图标 14px（`C/ReasoningRow.tsx:9`）→ 标题「思考」（`P/locale.ts` `message.think`）→ 2×2px 圆点（左右 margin 8px，`C/ReasoningRow.module.css:32-39`）→ 摘要。摘要 secondary 字号 13 / 行高 `calc(20px + delta-secondary)`（`:44-51`）。展开体左缩进 `22px` = 图标 16 + gap 6，上下 padding 4（`:73-77`）。 |
| 3 | 命中区 | 整行可点（`expandOnRowClick`，`C/ReasoningRow.tsx:94`）。折叠视觉高仅 24px；行容器 `.row { overflow:hidden }`（`C/ReasoningRow.module.css:11-13`）→ 移动端扩边到 ≥44dp 的规则仍待 Q-03 拍板。 |
| 4 | 手势 | 单击 = 切换展开。无长按/双击/滑动（未实现即不绑）。与父滚动：竖向拖动归外层；行内无横向手势。 |
| 5 | 行为 | `toggle()`：折叠↔展开。状态是**本地**的并绑版本号：`expanded = expandedVersion === version`，版本前进（轮被隐藏/重建）→ 自动回折叠（`C/use-disclosure.ts:11-20`）；版本源是 `ChatNodeSeat` 的 `disclosureReset`（`C/ChatNodeSeat.tsx:114,119-123`），折叠动画中递增。幂等可撤销。 |
| 6 | 视觉状态 | default：标题 `font-weight:400`（`:28-30`）；`data-state=running` → 摘要 `TextShimmer` 流动；`data-state=ok` → 静态；`data-expanded` → 头部 sticky + 底色遮挡（`:15-22`）；**`data-preview` 缺失 → 圆点与摘要 `display:none`，只剩标题**（`:68-71`）。**无 hover/focus 特殊样式**（Web 默认）。 |
| 7 | 布局占位与展开形态 | **形态 A：inline-uncapped + sticky header**（G-03，Q-01 已拍板）。折叠：`contain:size layout` + 定高 → 摘要流式变化**不引起任何兄弟重排**（G-02）。展开：根节点不封顶随内容长高，下方兄弟整体下移；头部 `position:sticky; top:0; z-index:1`。**不做内部滚动**。 |
| 8 | 滚动语义 | 展开后随外层会话滚；内层无滚动容器 → G-04 不适用。头部 sticky 吸附会话视口顶。 |
| 9 | 动效 | **无高度动画**（模式切换只切 CSS display，不 unmount 摘要，`C/ReasoningRow.tsx:34-47` JSDoc，E1）。持续动效两处：运行中摘要 `TextShimmer`；流式摘要右端 **48px** `mask-image` 渐隐（G-05，`C/ReasoningRow.module.css:59-66`）。 |
| 10 | 边界态 | 摘要取法：running = **最后一个已完成段落的第一个非空行**；settled = **首行**（`C/ReasoningRow.tsx:11-32,56`）；两者都 `replaceAll('**','')`（`:57`）。空摘要 → 圆点+摘要隐藏（`:58-59` + css:68-71）。超长 → 静态单行 `ellipsis` / 流式渐隐。**Compact 模式**：`settledReasoningPreview=false` → 静态行连摘要都没有，只剩「思考」（`P/presentation-policy.ts:29-35`）。大字号：根高、行高、缩进随 delta 同步。 |
| 11 | 无障碍 | 运行中输出视觉隐藏文案 `t('row.running')`（「运行中」，`C/ReasoningRow.tsx:84`，G-08）。行是 disclosure，但 `aria-expanded` 由 `ui-primitives` 的 `DisclosureRow` 提供 —— **不在本路 4 个包内，本路未取证**，仍标 E1 待 `ui-primitives` 一路补（转交事项，见 §4 `Q-CHAT-09`）。 |
| 12 | 依据 | **E0** `C/ReasoningRow.tsx:9,11-32,56-59,70-102`；**E0** `C/ReasoningRow.module.css:6-22,24-30,32-51,53-71,73-77`；**E0** `C/use-disclosure.ts:11-20,28-34`；**E0** `C/AssistantMarkdown.tsx:76-106`；**E0** `N/process-groups.ts:21-24,172`；**E0** `C/AssistantNodeView.tsx:28-32`；**E0** `P/presentation-policy.ts:29-35`；**E1** `C/ReasoningRow.tsx:34-47`（JSDoc：不 unmount 摘要）。 |
| 13 | 反证 + 验收 | **反证**：① 折叠行不定高（G-02 违反）→ 摘要逐帧变化把下方正文推走，等于无法阅读；② 展开改为封顶内滚（形态 B）→ 长思考被锁在 45% 屏高的小窗里，且与外层抢手势（G-04），用户读不完；③ 若展开不 sticky（G-06 违反）→ 多屏思考下想收起只能滚回顶部 = 无法关闭。**验收**：`CHAT-01` 折叠行高度恒定（摘要变化时行顶 y 不动）；`CHAT-02` 展开后头部吸附可随时收起；`CHAT-03` 流式摘要右端 48px 渐隐无省略号；**新增** `CHAT-03b` 一轮两个 reasoning block 渲染为两个独立行、各自开合互不影响。 |

### 2.5 `chat.msg.actions` — 消息操作条（复制 / 分支 / 用量 / 时钟）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.actions`。两处实例：① 用户/steering 气泡下方，`clock='start'`（时间在图标前，`C/MessageItem.tsx:253-260,306-314,332-340`）；② 轮尾 assistant 行，`clock='end'`（时间在图标后，`C/TurnTailNodeView.tsx:63-77`）。每消息 1 条。分支键仅 `onBranch` 存在时渲染；用量 pill 仅 `detailed` 模式且该轮有 `tokenUsage` 时出现。 |
| 2 | 网格与定位 | flex 行 `align-items:center; gap:8px; height: calc(28px + delta)`（`C/MessageIconActions.module.css:5-10`）；用户侧时间 `padding-right:12px`、secondary 13（`:13-19`）；assistant 侧 `endInfo { margin-left:8px; gap:8px }`（`:30-37`），时间字号为 secondary−1（`:23-28`）。用户气泡右对齐（`C/MessageItem.module.css:3-8`）。 |
| 3 | 命中区 | 每个动作为 **28×28**（`padding:6px`，图标 15px；assistant 端 17px，`C/MessageIconActions.module.css:63-87`），间距 8px。28px < 44dp → 与 Q-03 合并处理。 |
| 4 | 手势 | 单击；Tooltip 走 hover/focus（`side="bottom"`）。无长按/双击/滑动。不可用分支**仍可 hover/focus**（见字段 6）。 |
| 5 | 行为 | 复制：`writeClipboard(text)`，成功 → `copied=true` **1000ms** 后复位；期间 `copyPending` 吞掉重复点击（`C/MessageIconActions.tsx:62-76`）。分支：`onBranch()` → `forkAt(data.seq)`（`C/TurnTailNodeView.tsx:69`）。用量：开关 `chat.stats.turn-usage`。幂等：复制可重复但 1s 内不重复写；分支不可撤销（新会话）。 |
| 6 | 视觉状态 | default tertiary；hover → 背景 `interactive-bg-hover` + 文字 secondary（`:89-92`）；`copied` → 图标换勾 + `aria-label`/Tooltip 改「已复制」（`:85-88`）；`data-unavailable` → `opacity:.4` + `cursor:default`，hover 无背景（`:94-103`）；**整行显隐**：`@media (hover:hover)` 下、`[data-actions-reveal='hover']` 或「非最后一个用户/steering 行」→ `opacity:0`，`:hover`/`:focus-within` → 1（`:42-61`）。 |
| 7 | 布局占位与展开形态 | 不展开。**隐藏时用 opacity，不用 display** → 高度恒 28px，鼠标进出不引起布局变化。 |
| 8 | 滚动语义 | 不滚，随消息滚。 |
| 9 | 动效 | `opacity 80ms ease`（`:48`）；勾/叉图标瞬换无动画。 |
| 10 | 边界态 | ① `time===undefined`（transient 消息）→ 不渲染时钟（`C/MessageIconActions.tsx:77`）；② 文本为空 → 复制空串；③ 分支不可用 → `aria-disabled` + 视觉隐藏原因文本（`:107-109`）；④ **无 hover 能力设备**：不进入 `@media (hover:hover)`，操作条全部常驻可见（`:42` 包裹）——即移动端可见、不需要长按；⑤ 窄屏（`max-width:480px`）用量 pill 收成纯图标、`label` 隐藏（`C/TurnUsagePanel.module.css:59-72`）。 |
| 11 | 无障碍 | 每个图标是 `button` + `aria-label`；复制成功改 aria-label；分支不可用用 `aria-disabled`（非原生 disabled，保住 Tooltip 的 focus/hover）+ `aria-describedby` 指向隐藏原因（`C/MessageIconActions.tsx:91-109`）。 |
| 12 | 依据 | **E0** `C/MessageIconActions.tsx:62-113`；**E0** `C/MessageIconActions.module.css:5-10,23-37,42-61,63-103`；**E0** `C/TurnTailNodeView.tsx:60,63-77`；**E0** `C/MessageItem.tsx:253-260,306-314,332-340`；**E1** `P/locale.ts`（`message.branch`「在新对话中分支」、`message.branchUnavailable`「仅可从已完成轮次的最后一条消息分支」）。 |
| 13 | 反证 + 验收 | **反证**：① 若 hover 显隐用 `display:none`，鼠标移入瞬间行高 0→28 突变，正文上下跳；② 若把不可用分支设为原生 `disabled` 按钮，Tooltip 拿不到 hover/focus 事件（源码注释原话：`C/MessageIconActions.tsx:93`）→ 用户永远不知道为什么不能分支；③ 若触摸设备也按 hover 隐藏，等于移动端没有复制按钮。**验收**：`CHAT-16` hover 显隐无布局跳动（真机录屏）；`CHAT-17` 不可用分支可 focus 且读出原因；`CHAT-17b` 触摸设备操作条常驻。 |

### 2.6 `chat.msg.feedback-actions` — 点赞 / 点踩

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.feedback-actions`。由 `ui-message-feedback` 通过槽 `conversation.chat.assistant-actions` 注入，落在 2.5 的「复制之后、分支之前」（`C/MessageIconActions.tsx:90` `{extraActions}`；`C/TurnTailNodeView.tsx:53-55,72`）。出现条件：该轮有 durable `messageId`。每消息 1 对。 |
| 2 | 网格与定位 | 复用操作条坐标系：28×28、gap 8（`F/MessageFeedbackActions.module.css:1-23`）。 |
| 3 | 命中区 | 两枚 28×28。失败提示跟在按钮后（`padding-left:4px`，`:41-46`）。 |
| 4 | 手势 | 单击；`onPointerEnter`/`onFocus` 触发 `seed()` 懒加载（`F/MessageFeedbackActions.tsx:87-88,102-103`）。无长按。 |
| 5 | 行为 | ① 未评分点击 → `ensure()` 后 `openDialog(messageId, rating)`（只记录，提交在弹窗里）；② 已记录且点同一评分 → `retract()` 撤回；③ `pending` 期间按钮 `disabled`（`F/MessageFeedbackActions.tsx:53-72`）。可撤销（撤回）。 |
| 6 | 视觉状态 | 未评分 outline 图标；已评分 fill 图标 + `aria-pressed` + `data-active`（`:84-91,99-107`）——**pointer 离开行后仍保持可读**（`F/MessageFeedbackActions.module.css:35-39` 注释）；`disabled` → `opacity:.4`；失败 → 旁边 `role=status` 文案（`F/MessageFeedbackActions.tsx:109-112`）。 |
| 7 | 布局占位与展开形态 | 不展开，同行内联。显隐继承 2.5 的 `data-actions-reveal`。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无（图标瞬变）。 |
| 10 | 边界态 | ① 评分加载失败 → 「加载失败」；② `version-conflict` → 「已在其他窗口修改」；③ 其他错误 → 通用失败文案（`F/MessageFeedbackActions.tsx:46-49`）；④ 未 hover 过的行不读远端 → 冷行首点仍先 `ensure()` 再判（`:56-62`）。 |
| 11 | 无障碍 | `button` + `aria-pressed` + `aria-label`（点赞/取消点赞、点踩/取消点踩）；Tooltip。 |
| 12 | 依据 | **E0** `F/MessageFeedbackActions.tsx:28-114`；**E0** `F/MessageFeedbackActions.module.css:1-46`；**E0** `C/MessageIconActions.tsx:90,110-112`（extraActions 槽位）。 |
| 13 | 反证 + 验收 | **反证**：若挂载即读每条消息的评分，一屏 50 条会打 50 次读请求；若已评分再点不做「撤回」，误点变成永久判断、用户无法改主意。**验收**：`CHAT-18` 懒加载只在首次 hover/focus 触发一次（单测计数）；`CHAT-19` 再点同一评分 = 撤回（真机）。 |

### 2.7 `chat.msg.feedback-dialog` — 反馈弹窗（sheet-overlay）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.feedback-dialog`。会话级 overlay 条目（每 Session 一个 `conversation.input.overlay` 条目）。出现条件：`state.target !== null`（评分已选、待提交）。内容：7 个分类 chip + 备注 textarea + 提交按钮（`F/FeedbackDialog.tsx:23-32,86-125`）。 |
| 2 | 网格与定位 | Modal 居中，卡宽 `min(488px, 100%)`，圆角 panel，`gap:38px`（`F/FeedbackDialog.module.css:1-7`）。chips `flex-wrap; gap:8px`、`margin-top:-14px`（`:9-14`）。textarea `min-height:116px; max-height:280px; field-sizing:content`（`:52-72`）。提交按钮 `width:100%; height:44px`（`:84-90`）。 |
| 3 | 命中区 | chip 高 **28px**（`:16-28`）；textarea 高 ≥116px；提交 44px。28px chip < 44dp → Q-03 同类问题。 |
| 4 | 手势 | 单击 chip 切换；Esc / 点遮罩关闭（由 `Modal` 提供，`closeLabel`/`onClose`）；textarea 输入。无长按。 |
| 5 | 行为 | chip：`edit({ category: 同值? null : 该值 })` → 可反选（`:111`）。提交：`submit()`，`submitting` 期间所有 chip/textarea/按钮 disabled/readOnly（`:97,105,111,122`）。成功 → Toast「已记录」（`:67-75`）；失败 → 按 code 映射文案，`holdMs:6000`（`:76-85`）。 |
| 6 | 视觉状态 | chip default `border-l4` + primary；hover → `interactive-bg-hover`（`:30-33`）；`chipActive` → 品牌填充 + 反色文字（`:45-50`）；`disabled` → `opacity:.4`；`focus-visible` → 2px 外发光环（`:40-43`）；textarea focus → 边框 l3 + 1px 品牌 ring（`:78-82`）。 |
| 7 | 布局占位与展开形态 | **形态 C（sheet-overlay，G-03）**：Modal/Toast 都 portal 到 `document.body`，不占聊天布局（G-01 兼容）。 |
| 8 | 滚动语义 | 内容超长时由 Modal 自身滚动（本包未自定义）；textarea 到 `max-height:280px` 后内部滚动（`:58`）。 |
| 9 | 动效 | chip/textarea `transition 120ms ease`；reduced-motion 关闭（`:92-97`）。Modal/Toast 动效来自 `ui-primitives`（不在本路）。 |
| 10 | 边界态 | ① 失败 code 无专属文案 → `error.generic`（`:35-38,61`）；② 提交中重复点击被 `disabled` 挡住；③ 备注超长 → `note-too-large` 错误码文案（`:35-38`）；④ 弹窗卸载会 `dismissToast`，避免回会话重放（`:56-59`）。 |
| 11 | 无障碍 | chips 容器 `role="group" aria-label`（`:103`）；每 chip `aria-pressed`；textarea 有 `aria-label`（`:119`）；Modal 自带 `title`/`closeLabel`。 |
| 12 | 依据 | **E0** `F/FeedbackDialog.tsx:23-125`；**E0** `F/FeedbackDialog.module.css:1-97`。 |
| 13 | 反证 + 验收 | **反证**：若把反馈做成行内直接提交（无弹窗），误点即写入一条无法解释的负反馈，且分类/备注无处可填；若 chip 不支持反选，用户选了错分类只能关掉重来。**验收**：`CHAT-20` chip 可反选且 `aria-pressed` 同步；`CHAT-21` 提交中所有控件禁用（真机）。 |

### 2.8 `chat.list.scroll-follow` — 会话滚动跟随与阅读位置

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.list.scroll-follow`。chat 面板的滚动策略层（`C/use-chat-scroll.ts` + `C/use-chat-reading.ts` + `C/use-chat-viewport.ts` + `C/use-scroll-follow.ts`）。无独立视觉；它决定「是否自动贴底」「上翻后是否停止跟随」「恢复跟随的判据」「保存/恢复阅读位置」。作用于会话滚动容器（`[data-conversation-scroll]` 存在时用它，否则 ChatView 自己，`C/use-chat-viewport.ts:72`）。 |
| 2 | 网格与定位 | 无自身布局。锚点元素：当前阅读轮（`readVisibleTurn`）或最新轮（`latestTurn`）。滚动容器内边距 `16px` + composer 侧净空（`C/ChatView.module.css:22-34`）。 |
| 3 | 命中区 | 不是命中区；手势来自会话滚动容器。 |
| 4 | 手势 | 阅读意图集合 `wheel / touchstart / pointerdown / keydown / beforematch`，**capture + passive** 监听（`C/use-chat-viewport.ts:46-47,80`）。命中意图时不 `preventDefault`：① `intent` → 取消待定跟随 + `interruptFollow()`（`C/use-chat-scroll.ts:134`）；② 滚动本身 → `reading.onScroll(event)`（`:124-128`）。scrollend 独立监听（`:79`）。 |
| 5 | 行为 | **贴底判据**：`|floor − top| <= threshold`，会话阈值 `FOLLOW_THRESHOLD=24` 且控制器实参为 `24+1=25`（`C/use-chat-reading.ts:7,252`；`C/use-scroll-follow.ts:67`）。**停止跟随**：读者移动（`movedByReader`）且距底 ≥1px → 起 500ms 采样定时器，采样点按 25px 重新判定（`:134-144,143`）。**恢复跟随**：读者移动后距底 <1px 立即 `followTail()`（`:137-141`）；或采样后 `nearBottom` 为真。**保存语义位置**：跟随中存 `null`，不跟随才存锚点（`:186-192`）；恢复时无存档 → 直接贴底（`:96-99`）。 |
| 6 | 视觉状态 | 自身无视觉；驱动 `data-chat-following-tail`（`C/ChatView.tsx:208`）→ 该属性存在时 `overflow-anchor: none`（`C/ChatView.module.css:36-39`）避免浏览器锚定与手动跟随打架。 |
| 7 | 布局占位与展开形态 | 不占位。折叠/展开的布局补偿由 `turnSpacer`（底部预留空间，`C/ChatView.tsx:237`；`C/ChatView.module.css:92-96`）+ `reserveBelow/reclaimBelow` 完成，属流式排布补偿，不是展开形态。 |
| 8 | 滚动语义 | 只有外层一个滚动容器；过程组内滚为独立第二容器（见 2.3）。跟随动画用原生 `scrollTo({behavior:'smooth'})`；`prefers-reduced-motion: reduce` 时改 `jump`（`C/use-scroll-follow.ts:150-152`）。内容在动画中增长 → 重定向目标而非切到 floor（`:141-148`）。手势打断 → `interrupt()` 就地停住（`:165-170`）。 |
| 9 | 动效 | smooth 原生滚动；`jump` 对已动画中的元素用 `behavior:'instant'`（`:120`）。无自绘动画。 |
| 10 | 边界态 | ① 空会话：`latestTurn=null`；② `openState==='loading'` 时 `processContent` 直接返回，不做任何滚动（`C/use-chat-scroll.ts:85`）；③ `openState==='error'` → 显示错误行（`C/ChatView.tsx:214-218`）；④ 初次打开：`navigation.reset()` + `reading.restore()`（`:94-99`）；⑤ 新提交/steering：强制贴底（`:100-105`，smooth 当 defer 模式）；⑥ 尺寸变化：跟随中 → 贴底，否则重算 activeTurn（`C/use-chat-reading.ts:168-172`）。 |
| 11 | 无障碍 | 滚动策略本身无 ARIA。阅读位置变化不播报（避免噪声）；只有运行指示有 live 区（见 §3.1「会话运行指示条」）。 |
| 12 | 依据 | **E0** `C/use-chat-reading.ts:7,44,49,83-108,134-165,168-184,186-234,244-257`；**E0** `C/use-scroll-follow.ts:16-19,67,90-126,138-170,179-182`；**E0** `C/use-chat-viewport.ts:46-47,70-92,94-108,506`；**E0** `C/use-chat-scroll.ts:45-205`；**E0** `C/ChatView.tsx:186-194,208,237,240-251`；**E0** `C/ChatView.module.css:36-39,92-96`。 |
| 13 | 反证 + 验收 | **反证**：① 若判据用「用户是否上翻过了」这种布尔，用户轻推到距底 5px 也会永久停止跟随，之后每条流式 token 都不再自动跟 → 必须手动追；② 若跟随中仍保存像素位置，下次打开会话会落在「上次的像素 y」，而内容高度已变 → 阅读位置错位（所以存 null + 语义锚点）；③ 若手势不 `interrupt` 进行中的 smooth 动画，用户手指在动而容器继续自己滑 → 手感觉「不跟手」。**验收**：`CHAT-22` 距底 25px 内自动跟随、26px 外不跟随（单测）；`CHAT-23` 上翻后流式增长不再拉底、回底后恢复（真机）；`CHAT-24` 手势打断 smooth 跟随就地停住（录屏）。 |

### 2.9 `chat.list.turn-rail` — 轮次导航条（★ Q-07 主体）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.list.turn-rail`。chat 面板右侧竖条，每轮一枚刻度。出现条件：`scroll.initialized`（`C/ChatView.tsx:199-207`）**且** `items.length >= 2`（`C/TurnNavigator.tsx:227`）。刻度集合 = 已加载轮 ∪ `turnOutline` 投影（整本会话的轮清单，`C/turn-rail-items.ts:63-89`）；未加载轮也可点（先翻页）。 |
| 2 | 网格与定位 | 轨道是 transcript 的**兄弟**（不是后代）：`.slot` absolute/sticky，`right:0;left:0;height:0;z-index:7`，水平内边距 `composer 净空 + 16px`（`C/TurnNavigator.module.css:2-15`）。`.frame` 宽 28px、`right:12px`、垂直居中于「视口高 − composer 高」的带内，`max-height: min(band−64px, 420px)`，`contain:layout`，`pointer-events:auto`（`:26-46`）。刻度：**定距 10px**、高 10px；tick 20×2px 右对齐（`C/TurnNavigator.tsx:29`；`C/TurnNavigator.module.css:84-109`）。 |
| 3 | 命中区 | 每刻度是 `button`，尺寸 = 行宽 × **10px**（含 20×2px 可见 tick）→ 10px pitch 远低于 44dp，**移动端不可用**（而且 ≤900px 直接隐藏，见字段 10）。 |
| 4 | 手势 | 单击 = 跳转（`C/TurnNavigator.tsx:74`）；`pointermove` = 预览（`:73`）；focus/blur = 预览的键盘等价（`:75-76`）；轨道内滚 = 原生滚动（`:197` `scrollBy`）。**无长按/双击/拖动滑动**。指针在轨道内时禁止「活动刻度自动居中」抢位置（`:95,217-225` `pointerInsideRef`）。 |
| 5 | 行为 | `onNavigate(item)`：已加载轮 → `viewport.scrollToTurn(turn)` + `reading.acceptNavigation`；未加载轮 → `loadThrough(seq)` 翻页后再落位，期间该刻度 `aria-busy` + 脉冲，`busyTurn` 由导航层给（`C/use-chat-navigation.ts:69-91,170-183`；`C/TurnNavigator.tsx:71`）。跳转=可撤销（再点别的轮）；不发命令。 |
| 6 | 视觉状态 | tick 默认 `scaleX(0.6)`；未加载轮 `scaleX(0.4)+opacity.6`；预览 `scaleX(0.9)`；活动 `scaleX(1)` + primary 色；busy → `opacity 1→.35→1` 1s 循环；`focus-visible` → 品牌色 + 20px 内嵌描边环（`C/TurnNavigator.module.css:97-152,201-205`）。预览卡：`translateX(4px)→0` + 淡入 120ms，`top` 位移 140ms（`:173-199`）。 |
| 7 | 布局占位与展开形态 | **浮层，不占位**（G-01 兼容）：`.slot` 高 0；预览卡 `pointer-events:none` 绝对定位在轨道左侧（`:154-175`）。 |
| 8 | 滚动语义 | 轨道**自己滚**：`overflow-y:auto`、`overscroll-behavior: contain`（**不传链**）、隐藏滚动条、两端 24px 渐隐（`:50-78`）。刻度用 `@tanstack/react-virtual` 虚拟化（10px 定距、`overscan:3`、`useScrollendEvent`，`C/TurnNavigator.tsx:110-120,165`）。活动刻度只在「离开两侧 24px 淡出带」时才居中（`scrollPaddingStart/End = FADE_PX = 24`，`:33,163-164,190-198,212-225`）。 |
| 9 | 动效 | 居中滚动 `behavior` = `prefers-reduced-motion ? 'auto' : 'smooth'`（`:35-39`）；预览进场 120ms；刻度 scaleX/背景 140ms；reduced-motion 全部关闭（css:213-223）。 |
| 10 | 边界态 | ① 轮数 <2 → 不渲染（`C/TurnNavigator.tsx:227`）；② 容器宽 ≤900px → `.frame { display:none }`（`C/TurnNavigator.module.css:207-211`）**= 移动端没有轮次导航**；③ 会话很长 → 轨道内滚、刻度虚拟化；④ 预览文案预算：prompt 50 / response 120 字符（`N/turn-navigation.ts:12-13`），超长 `…` 截断，预览卡 1 行 + 3 行 clamp（`C/TurnNavigator.module.css:177-194`）；⑤ 预览缺失（未加载轮无预览）→ 显示「第 N 轮」（`C/TurnNavigator.tsx:277`）。 |
| 11 | 无障碍 | `nav aria-label="轮次导航"`（`P/locale.ts` `chat.turnNavigation.label`）；每刻度 `aria-label`「跳转到第 N 轮」/「加载并跳转到第 N 轮」，活动 `aria-current="true"`，忙 `aria-busy`，预览 `aria-describedby`（`C/TurnNavigator.tsx:66-72`）；预览 `role="tooltip"`（`:269`）。 |
| 12 | 依据 | **E0** `C/TurnNavigator.tsx:29-33,35-39,52-79,94-95,110-177,182-225,227-281`；**E0** `C/TurnNavigator.module.css:1-223`；**E0** `C/turn-rail-items.ts:13-24,63-89`；**E0** `N/turn-navigation.ts:12-13,79-97`；**E0** `C/use-chat-navigation.ts:69-91`；**E0** `C/ChatView.tsx:79-82,199-207`。 |
| 13 | 反证 + 验收 | **反证**：① 若刻度等高密度铺满（不定距），长会话轨道会长到几百 px 遮住正文；② 若轨道内滚允许传链，在轨道上滚一下整个会话跟着跑（DSH 用 `overscroll-behavior:contain` 明确阻断）；③ 若指针在轨道上时活动刻度还自动居中，刻度会在手指下方滑走 → 点错轮。**验收**：`CHAT-25` 10px 定距 + 24px 渐隐 + 指针入轨后停止自动居中（真机）；`CHAT-26` ≤900px 隐藏（截图）；`CHAT-26b` 未加载轮点击先翻页再落位且刻度脉冲（录屏）。 |

### 2.10 `chat.list.to-bottom` — 回到底部（悬浮 v 钮）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.list.to-bottom`。chat 面板右下角悬浮圆钮。出现条件：**`!scroll.followingTail`**（不跟随时才渲染，`C/ChatView.tsx:240`）。每个会话视图 1 个。 |
| 2 | 网格与定位 | 外层 `.toBottomSlot` 绝对定位（宿主滚动时 sticky）`right:0;bottom:16px;left:0;height:0;z-index:8`，`justify-content:flex-end` + 右内边距 = max(composer 净空+16, (100%−内容宽)/2)（`C/ChatView.module.css:244-271`）。按钮 34×34、`border-radius:100px`、`margin-top:-34px`（`:273-290`）。宿主滚动时 `bottom: calc(--dsh-composer-height + 16px)`（`:264-271`）。 |
| 3 | 命中区 | 34×34 圆形（`pointer-events:auto`；外层 slot 是 `pointer-events:none` 不挡正文，`:261,289`）。34px < 44dp → Q-03。 |
| 4 | 手势 | 单击。无长按/双击/滑动。 |
| 5 | 行为 | `scroll.returnToBottom()` = `cancelPendingFollow()` + `navigation.cancel()` + `reading.followTail()`（立即贴底、清掉翻页/跳转挂起，`C/use-chat-scroll.ts:193-197`）。幂等（已贴底时按钮本就不渲染）。 |
| 6 | 视觉状态 | default：`background: button-floating-fill` + `box-shadow: elevation-panel` + primary 前景（`:285-289`）；hover → `button-floating-hover`（`:292-294`）。无 pressed/disabled。 |
| 7 | 布局占位与展开形态 | **不占位**（G-01）：slot 高 0 + 按钮负 margin 顶出；出现/消失不推动列表。 |
| 8 | 滚动语义 | 自身不滚；点击即 `followTail` 让会话贴底（默认瞬时，`C/use-chat-reading.ts:83-88`）。 |
| 9 | 动效 | 无自定义动画（出现/消失是挂载/卸载，无过渡）。 |
| 10 | 边界态 | ① 跟随中 → 整钮不渲染（不是隐藏，是不挂载）；② 空会话 → 无内容，`followingTail` 初值由存档决定；③ `@/` slash 菜单打开时宿主把该 seat 抬到 z-index 9，避免被本钮遮挡（`C/ChatView.module.css:249-252` 注释）。 |
| 11 | 无障碍 | `button` + `aria-label="回到底部"`（`P/locale.ts` `chat.toBottom`；`C/ChatView.tsx:245`）。 |
| 12 | 依据 | **E0** `C/ChatView.tsx:240-251`；**E0** `C/ChatView.module.css:242-294`；**E0** `C/use-chat-scroll.ts:193-197`；**E0** `C/use-chat-reading.ts:83-88`。 |
| 13 | 反证 + 验收 | **反证**：① 若按钮常驻（不判 followingTail），就会挡住正在阅读的正文右下角，而用户根本没想回底；② 若占布局高度（旧 myco 的 v 钮曾占整行），贴底时列表高度突变 → 内容跳动（G-01 原话）。**验收**：`CHAT-27` 仅在非跟随时出现、且出现/消失不改变内容 y（真机录屏）；`CHAT-28` 点击后立即贴底并清掉未完成的翻页（单测）。 |

### 2.11 `chat.list.load-older` — 加载更早

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.list.load-older`。列表顶部一行居中按钮。出现条件：`hasMore`（`C/ChatView.tsx:219-225`）。每会话 1 个。 |
| 2 | 网格与定位 | 父：列容器顶部，`display:flex; justify-content:center`；按钮 `border-radius:radius-sm; padding:4px 12px; font-size:12px`，背景 `interactive-bg-hover-solid`（`C/ChatView.module.css:222-235`）。 |
| 3 | 命中区 | 按钮自身 padding 后 ≈ 24px 高 × 文本宽；未达 44dp → Q-03。 |
| 4 | 手势 | 单击。键盘 Enter/Space。 |
| 5 | 行为 | `scroll.loadEarlier()` → `navigation.loadEarlier()`：`cancel()` + `beginPaging()` + `reading.pauseFollowing()` + `loadOlder()`（`C/use-chat-navigation.ts:94-99`）。**保留当前阅读锚点**（翻页后不跳到新顶，`N/README.md:103` E1）。幂等：`loadingOlder` 期间按钮禁用，重复点击无效。 |
| 6 | 视觉状态 | default `label-secondary` 12px；`:disabled` → `cursor:default; opacity:.6`（`:237-240`）。 |
| 7 | 布局占位与展开形态 | 在流内占一行；加载中高度不变（文案「加载中」替换「加载更早」）。 |
| 8 | 滚动语义 | 不滚；点击后由导航层保持锚点（`beginPaging` + `preserve`）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | ① 无更早内容 → 整行不渲染；② 加载中 → 禁用 + 文案换「加载中」；③ 加载失败 → 由 `openState==='error'` 的错误行承担（不在本节点）。 |
| 11 | 无障碍 | 原生 `button` + `disabled`；无可达性文案问题。 |
| 12 | 依据 | **E0** `C/ChatView.tsx:219-225`；**E0** `C/ChatView.module.css:222-240`；**E0** `C/use-chat-navigation.ts:94-99`；**E1** `N/README.md:101,103`（锚点规则、`Load older` 落点）。 |
| 13 | 反证 + 验收 | **反证**：若加载后直接滚到新加载内容的顶部，读者当前正在读的那一段会被甩出屏幕、失去锚点；若不禁用重复点击，会并发发起多次翻页导致重复内容。**验收**：`CHAT-29` 加载后当前阅读锚点 y 不变（真机录屏）；`CHAT-30` 加载中按钮禁用（单测）。 |

### 2.12 `chat.dialog.file-open-error` — 打开文件失败弹窗

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.dialog.file-open-error`。出现条件：`fileOpenError !== null`（宿主拒绝打开某路径，`C/ChatView.tsx:252-260`；请求以自增 id 防旧结算覆盖新请求，`:112-145`）。每次失败 1 个。 |
| 2 | 网格与定位 | `Modal`（居中浮层），footer 两枚按钮，`min-width:72px`（`C/ChatView.module.css:296-299`）。 |
| 3 | 命中区 | 两枚 `Button`（`outline` 取消 / `primary` 重试），尺寸由 `ui-primitives` 决定（本路未取证）。 |
| 4 | 手势 | 单击；Esc / 遮罩关闭（Modal 默认，`onClose`）。 |
| 5 | 行为 | 取消 → `closeFileOpenError()`（`fileOpenRequest.current += 1` 让在途结算失效 + 清 state，`:141-145`）；重试 → 以同 path 再发一次（`:257`），`busy` 期间禁用。 |
| 6 | 视觉状态 | 标准 Modal 视觉；主按钮在 busy 时 `disabled`。 |
| 7 | 布局占位与展开形态 | **形态 C（sheet-overlay）**：Modal portal，不占聊天布局。 |
| 8 | 滚动语义 | 长 message 时由 Modal 自身滚动。 |
| 9 | 动效 | 来自 `ui-primitives` Modal（本路未取证）。 |
| 10 | 边界态 | ① 宿主错误 message 为空 → 用 `t('fileOpen.unknown')` 兜底（`:22-25,132-135`）；② 用户取消后，在途请求即使成功也不再关闭/重开弹窗（id 比对，`:114-116,123-125`）；③ 连续两次失败 → 覆盖同一条。 |
| 11 | 无障碍 | Modal 自带 `title`（`fileOpen.title` =「无法打开文件」）与 `closeLabel`；按钮为语义 `Button`。 |
| 12 | 依据 | **E0** `C/ChatView.tsx:112-145,252-260,265-290`；**E0** `C/ChatView.module.css:296-299`。 |
| 13 | 反证 + 验收 | **反证**：若取消后不做请求代际隔离，一次已取消的在途响应会把弹窗重新打开（用户已明确关闭）；若不给重试入口，宿主偶发拒绝就只能重新点一次链接。**验收**：`CHAT-31` 取消后在途响应不重开弹窗（单测）；`CHAT-32` 重试按钮 busy 时禁用（真机）。 |

### 2.13 `chat.msg.compaction` — 上下文压缩标记行

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.compaction`。出现条件：存在 `compaction` 节点（`CompactionSummaryNode`，`C/MessageItem.tsx:359-362`），每节点 1 个。可展开条件：`node.summary !== null`（`C/CompactionItem.tsx:39-40`）；标题可由手动 compact 命令覆盖（`title` prop）。 |
| 2 | 网格与定位 | 行 `padding:2px 0`；按钮高 `calc(24px + delta)`（`--dsh-compaction-header-height`），`width:100%`；leading 16+delta 槽（context 图标 ↔ 展开箭头 hover 互换）；标题 secondary 13/24；2×2px 分隔点 margin 8；摘要 `flex:1` 单行 ellipsis（`C/MessageItem.module.css:51-59,61-75,124-188`）。 |
| 3 | 命中区 | 整行按钮（宽 100% × 24px）；`disabled` 时不可点。 |
| 4 | 手势 | 单击 = 切换；hover/focus-visible 时 leading 由 context 图标换成展开箭头（`:153-161`）。无长按。 |
| 5 | 行为 | `setExpanded(v => !v)`（`C/CompactionItem.tsx:55`）→ 本地 `useState`（**不绑轮次版本**，与 G-07 的思考行不同）。展开渲染 `MarkdownText` 摘要体。幂等。 |
| 6 | 视觉状态 | default tertiary；hover → secondary + `interactive-bg-hover`；展开时若 `.compactionBody` 存在 → 按钮 `sticky top:0; z-index:7` + 方角 + `bg-base` 遮挡（`:87-93`），hover 换 `hover-solid`（`:108-110`）；`disabled` → 无 pointer（`:112-114`）。 |
| 7 | 布局占位与展开形态 | **形态 A 变体（inline-uncapped + sticky header）**：展开体随页面长高、不封顶、无内滚；头部 sticky 吸在会话视口顶。摘要体内的代码块 banner 被强制下移到 `--dsh-compaction-header-height` 之下，避免盖住折叠入口（`:101-103`）。 |
| 8 | 滚动语义 | 随外层会话滚；头部 sticky。无内滚 → G-04 不适用。 |
| 9 | 动效 | `color 100ms`；reduced-motion 关闭（`:238-242`）。sticky 切换无动画。 |
| 10 | 边界态 | ① `summary===null` → 不可展开、`aria-expanded` 不输出、摘要文案「压缩摘要不可用」；② 有 `shadowedItemCount`+`shadowedTokenCount` → 「已压缩 N 条历史记录（约 M tokens）」；否则用 `fallbackSummary` 或「点击查看压缩摘要」（`C/CompactionItem.tsx:41-47`）。 |
| 11 | 无障碍 | `button` + `aria-expanded`（仅可展开时）+ `disabled`（原生）；图标容器 `aria-hidden`，箭头的展开态另带 `data-compaction-disclosure`。 |
| 12 | 依据 | **E0** `C/CompactionItem.tsx:37-75`；**E0** `C/MessageItem.module.css:51-188`；**E1** `P/locale.ts`（`message.compaction.*` 文案）。 |
| 13 | 反证 + 验收 | **反证**：① 若展开摘要不 sticky 头部，一段长摘要会把折叠入口顶出视口 → 只能滚回顶部收起（同 G-06 理由）；② 若摘要代码块的 banner 不停在下移位置，banner 的「复制」按钮会被 sticky 头盖住（源码注释直述此冲突，`:95-103`）。**验收**：`CHAT-33` 长摘要展开后头部可随时收起（真机）；`CHAT-34` 摘要内代码块 banner 的复制按钮可点（截图 + 命中测试）。 |

### 2.14 `chat.msg.context-injection` — 上下文注入行

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.context-injection`。出现条件：存在 `context` 节点（系统注入/召回/工具增删）。单条工具增删（`toolBlocks.length===1`）**不可展开**、整行只显示「已添加工具 X」（`C/ContextInjectionRow.tsx:37-57,79-80`）。 |
| 2 | 网格与定位 | 复用 `DisclosureRow` chrome：图标 14px + 标题 + 分隔点 + 生产者/摘要；展开体 `css.body` 带 `data-context-form`（`C/ContextInjectionRow.module.css`）。 |
| 3 | 命中区 | 整行可点（`expandOnRowClick`，`:81`）；不可展开时不可点。 |
| 4 | 手势 | 单击 = 切换。无长按。 |
| 5 | 行为 | `onToggle` → 本地 `useState` 取反（`:82`）；`keepContentWhenOpen`（展开体不因再次渲染卸载）。幂等。 |
| 6 | 视觉状态 | `DisclosureRow` 默认视觉；chevron 类由 `css.chevron` 定制；展开态由 `open` 驱动。 |
| 7 | 布局占位与展开形态 | **形态 A（inline-uncapped）**：展开体随内容长高、无内滚，下方兄弟下移；`ContextBody` 内部对超长内容另有上限（`C/ContextBody.tsx` 560 行，本路边界）。 |
| 8 | 滚动语义 | 随外层滚；无内滚。 |
| 9 | 动效 | 无自定义（`DisclosureRow` 决定）。 |
| 10 | 边界态 | ① form 不可读 → 渲染 opaque body，标记文案同步说法（`:33-35`）；② 生产者无 label → 分隔点一起省掉（`:63-69`）；③ 全是工具增删且 ≥2 条 → 摘要「工具已更新 +n/−n」；④ 单条 → 整行不可展开（`:79-80`）。 |
| 11 | 无障碍 | `DisclosureRow` 提供行级语义（`aria-expanded` 在 `ui-primitives`，本路未取证）；分隔点 `aria-hidden`，可达名只由标题+生产者组成（源码注释 `:64-66`）。 |
| 12 | 依据 | **E0** `C/ContextInjectionRow.tsx:31-93`；**E0** `C/MessageItem.tsx:346-357`；**E1** `P/locale.ts`（`message.contextInjection`「上下文注入」/`message.contextRecall`「跨会话召回」/`message.toolsUpdated`）。 |
| 13 | 反证 + 验收 | **反证**：若把「一条工具增删」也做成可展开行，展开后内容与折叠摘要重复两次（用户点开发现什么都没有）；若分隔点不做 `aria-hidden`，屏幕阅读器会把可达名读成「上下文注入 · 点 · 系统」（源码注释即为此）。**验收**：`CHAT-35` 单条工具增删不可展开（单测）；`CHAT-36` 可达名不含装饰点（a11y 树断言）。 |

### 2.15 `chat.msg.system-prompt` — 系统提示词行

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.system-prompt`。出现条件：`system-prompt` 节点（每节点 1 个）；`update=true` 时标题换「系统提示词更新」（`C/SystemPromptRow.tsx:25-36`）。 |
| 2 | 网格与定位 | 复用 `DisclosureRow` + `ContextInjectionRow.module.css` 的 `.body`（注释宣称展开体是 141px 代码块滚动区，`C/SystemPromptRow.tsx:18-23`，E1）。 |
| 3 | 命中区 | 整行可点（`expandOnRowClick`，`:35`）。 |
| 4 | 手势 | 单击 = 切换；无长按。 |
| 5 | 行为 | `onToggle` → 本地 `useState`（`:36`）。幂等。 |
| 6 | 视觉状态 | `DisclosureRow` 默认 + 定制 chevron 类。 |
| 7 | 布局占位与展开形态 | **形态 A**（inline，展开体内部另有滚动上限）。 |
| 8 | 滚动语义 | 外层滚；展开体（opaque body）自带滚动（`C/ContextBody.module.css:98 overflow:hidden` 等，见 `ContextBody` 实现）。 |
| 9 | 动效 | 无自定义。 |
| 10 | 边界态 | 完整提示词可能极长 → 展开体滚动而非撑爆页面；`update` 变体单独标题。 |
| 11 | 无障碍 | `DisclosureRow` 行级语义（`ui-primitives`，本路未取证）。 |
| 12 | 依据 | **E0** `C/SystemPromptRow.tsx:25-50`；**E1** `C/SystemPromptRow.tsx:18-23`（JSDoc：141px 滚动区）。 |
| 13 | 反证 + 验收 | **反证**：若系统提示词不折叠直接展开，一轮开头会被几百行 prompt 占满，用户找不到正文；若展开不设内部上限，页面被撑成几十屏。**验收**：`CHAT-37` 默认折叠、展开体有自身滚动（真机）。 |

### 2.16 `chat.msg.command-card` — 通用命令卡（`/` 命令）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.command-card`。出现条件：命令节点且**输出含换行**才可展开（`expandable = body !== null`，`C/GenericCommandCard.tsx:40,65`）。每命令 1 个。 |
| 2 | 网格与定位 | `DisclosureRow`：图标 14 + 标题（命令名）+ 分隔点 + 摘要 shimmer；展开体 `<pre>`（`:43-53`）。 |
| 3 | 命中区 | 整行可点（`expandOnRowClick`，`:66`）；单行输出时不可展开。 |
| 4 | 手势 | 单击 = 切换（`:68`）；无长按。 |
| 5 | 行为 | `toggle` → 本地 `useState`（`:30,42`）。幂等。 |
| 6 | 视觉状态 | running → `TextShimmer`；error → 摘要与 `<pre>` 加 `data-error`（error 色）；视觉隐藏状态文案「运行中」/「失败」（`:56-57`）。 |
| 7 | 布局占位与展开形态 | **形态 A**：展开体随内容长高，下方兄弟下移。 |
| 8 | 滚动语义 | 外层滚（`GenericCommandCard.module.css:42` 有 `overflow:auto` 的展开体内滚，具体上限未核实）。 |
| 9 | 动效 | 仅 shimmer（运行中）。 |
| 10 | 边界态 | ① 输出单行 → 不可展开；② `outcome===null` → 「正在运行命令」/自定义 runningSummary；③ error → 「命令失败」+ 红色输出。 |
| 11 | 无障碍 | `DisclosureRow` 行级语义；视觉隐藏 `row.running`/`row.failed`。 |
| 12 | 依据 | **E0** `C/GenericCommandCard.tsx:29-75`；**E0** `C/GenericCommandCard.module.css:25,42`（css 行本身按规则排除，仅作形态旁证）。 |
| 13 | 反证 + 验收 | **反证**：若单行命令也做成可展开，用户点开看到与摘要完全相同的一行，产生「点了没反应」的错觉。**验收**：`CHAT-38` 单行输出命令不可展开（单测）。 |

### 2.17 `chat.msg.retry` — 模型重试提示（原生 details）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.retry`。出现条件：`model-retry` 节点（每节点 1 个，`C/MessageItem.tsx:365-368`）。 |
| 2 | 网格与定位 | 原生 `<details>`；`summary` 内 `inline-flex` + 7px gap + 右侧 6×6 旋转箭头伪元素；展开体 `padding-left:14px` grid（`C/MessageItem.module.css:198-270`）。 |
| 3 | 命中区 | `<summary>` 整行（原生语义）；文字宽 × 约 20–24px。 |
| 4 | 手势 | 单击（原生 details toggle）；键盘 Enter/Space（原生）。**无自定义手势**。`summary::-webkit-details-marker` 被隐藏（`:218-220`），用自绘箭头。 |
| 5 | 行为 | 展开 → 显示「重试延迟：N ms」与「失败原因：…」；折叠只看状态行。浏览器原生 `open` 属性；无状态管理代码。 |
| 6 | 视觉状态 | 状态行：`active` → `TextShimmer`（正在重试）+ `data-active`；`cancelled` / `started` / `scheduled` 不同文案（`C/MessageItem.tsx:97-112`）；`summary:hover` → secondary；`focus-visible` → 1.5px 品牌环（`:244-247`）；`[open]` 时箭头由 −45° 转 45°（`:222-232,254-256`）。 |
| 7 | 布局占位与展开形态 | **形态 A（inline-uncapped）**：展开体在流内、随内容长高，下方兄弟下移。 |
| 8 | 滚动语义 | 随外层滚；无内滚。 |
| 9 | 动效 | 箭头 `transform 120ms ease`（`:231`）。 |
| 10 | 边界态 | ① 倒计时：`deadline = 首渲染时刻 + delayMs`（不用宿主时钟，`C/MessageItem.tsx:66-69`）；每 250ms 刷新，剩 1s 时停表（`:79-95`）；② `mode==='normal'` 显示 `maxRetries`，否则 `∞`（`:70`）；③ 非 active 时显示计划秒数（`:104`）；④ 失败码 → 专属文案（`ACCOUNT_SIGNED_OUT` / `ACCOUNT_SIGN_IN_REQUIRED` / `QUOTA` / `AUTH`，`:50-59`）。 |
| 11 | 无障碍 | 原生 `details/summary` 语义（浏览器提供 expanded 状态）；状态行 `role="status"`（`:109`）。 |
| 12 | 依据 | **E0** `C/MessageItem.tsx:61-127,365-368`；**E0** `C/MessageItem.module.css:198-270`；**E1** `P/locale.ts`（`message.retry.*`「正在重试模型请求」「等待重试模型请求」等）。 |
| 13 | 反证 + 验收 | **反证**：① 若倒计时用宿主事件时刻 + `Date.now()` 混算，跨机器时钟偏差会让倒计时从负数或几十秒开始（源码注释直述，`:66-67`）；② 若自绘箭头却不隐藏原生 marker，会出现两个箭头。**验收**：`CHAT-39` 倒计时从 delayMs 整秒起算（单测冻结时钟）；`CHAT-40` 展开详情含延迟与失败原因（真机）。 |

### 2.18 `chat.msg.trigger-notice` — 非人类触发通知

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.trigger-notice`。出现条件：`turn-trigger` 节点，且**不与 `question-reply` 同 id**（重复时被跳过，`N/process-groups.ts:164`）。每轮 0..N 个（独立根，不并入过程组，`:165-167`）。 |
| 2 | 网格与定位 | 圆角卡：`border:0.5px border-l1; radius-xl`，背景 `turn-trigger-bg`；header `padding:12px 16px; gap:10px`（图标 14 → 标题 → `margin-left:auto` 时间 → chevron 12）（`C/TurnTriggerNodeView.module.css:1-28`）。展开体 `padding:0 16px 12px 40px`（`:29`）。 |
| 3 | 命中区 | 整行 header button（宽 100% × 约 40px）。 |
| 4 | 手势 | 单击 = 切换（`C/TurnTriggerNodeView.tsx:38`）；hover 卡背景变 hover 色（`:8`）。无长按。 |
| 5 | 行为 | `setOpen(!open)` 本地 `useState`（`:30,38`）；展开显示「说明文案 + 记录正文」（`:44-47`）。**不表示执行成功**（E1 `N/README.md:84`）。 |
| 6 | 视觉状态 | default tertiary；hover → secondary + 卡背景变 `turn-trigger-bg-hover`；open → chevron `rotate(180deg)`（`:28`）。 |
| 7 | 布局占位与展开形态 | **形态 A**（inline）但展开体内部封顶：`max-height: 240px; overflow:auto; white-space:pre-wrap`（`:31`）→ 属「A + 局部内滚」。 |
| 8 | 滚动语义 | 外层滚；展开体超 240px 后自身滚动（第二滚动容器，G-04 边界待定）。 |
| 9 | 动效 | `background-color/color 100ms`；reduced-motion 关闭（`:33-35`）。 |
| 10 | 边界态 | ① 未知 source → 通用标题与图标；② 不同 source.kind → 10 种图标/标题（schedule/工具后台/插件更新/goal/agent/team/subagent/github/webhook/request，`C/TurnTriggerNodeView.tsx:15-26`）；③ 时间用日期感知时钟（同日 `HH:mm`，跨年带年月日，`C/message-chrome.ts:86-100`）。 |
| 11 | 无障碍 | `button` + `aria-expanded` + `aria-controls`；图标 `aria-hidden`；时间 `<time dateTime>`。 |
| 12 | 依据 | **E0** `C/TurnTriggerNodeView.tsx:15-50`；**E0** `C/TurnTriggerNodeView.module.css:1-35`；**E0** `N/process-groups.ts:13,164-167`；**E1** `N/README.md:80-84`（触发归因表）。 |
| 13 | 反证 + 验收 | **反证**：若触发通知并入过程组，用户折叠整轮时会看不到「是定时任务叫醒的」，误以为是自己的输入；若展开体不封顶，一条超长 webhook 正文会把整轮正文顶到屏幕外。**验收**：`CHAT-41` 触发通知始终独立于整轮折叠（真机）；`CHAT-42` 展开体 240px 后内滚（截图）。 |

### 2.19 `chat.stats.pill` — 会话统计 pill（含其 portaled 面板）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.stats.pill`。输入框下方 dock 的统计 pill，共 2 个 dock 条目：`activity`（轮/步 + 速度）与 `usage`（token 总量 + 缓存命中）。出现条件：`activity` 需 `stats.steps > 0`；`usage` 需有实际计费活动（`C/StatsPills.tsx:237-287`）。面板只在「有可展示行」时可点，否则渲染为静态 `span`（`:242,248,172-182`）。 |
| 2 | 网格与定位 | pill `inline-flex; gap:6; padding:1px 8px; border-radius:999px`，字号 secondary−1 / 行高 20（`C/StatsPills.module.css:7-30`）；图标 14px；面板 `position:fixed; width:max-content; min-width:min(300px,100vw−24); max-width:min(440px,100vw−24); padding:16px; z-index:1100`（`C/stat-dialog.module.css:10-31`）。面板 `dt/dd` 两列 grid（`:68-90`）。 |
| 3 | 命中区 | 整个 pill（padding 后约 20–22px 高）→ 低于 44dp。面板本身不可点（`cursor:default`）。 |
| 4 | 手势 | 单击 pill 开/关；面板打开时 **Esc / 外部 pointerdown / 外部 click（capture 阶段）** 关闭（`C/stat-dialog.ts:54-73`）；placement 为 trigger 上方 8px、夹在视口内 12px 边距（`:9-12,40-47`）。 |
| 5 | 行为 | 开/关都是本 pill 自己的 `open`（每个 pill 独立 state，最多同时一个 —— 靠 capture 阶段点击先关旧面板，源码注释 `:51-53`）。数据源：优先 `sessionStats` / `tokenUsage` 投影，缺失时回落窗口内 fold（`C/StatsPills.tsx:160-164,282-287`）。无命令。 |
| 6 | 视觉状态 | pill default tertiary；`button.pill:hover` / `[aria-expanded='true']` → 背景 `interactive-bg-hover` + secondary（`C/StatsPills.module.css:40-48`）；静态 span 形态不响应 hover。 |
| 7 | 布局占位与展开形态 | pill 占 dock 一行（在流内）；**面板是 C 形态浮层**：portal 到 `document.body`，`visibility:hidden` 先测尺寸再定位（`C/stat-dialog.ts:14-18`）。 |
| 8 | 滚动语义 | pill 不滚；面板不滚（内容行数有限）。 |
| 9 | 动效 | 无自定义动画（面板瞬间出现；定位用 style 直写）。 |
| 10 | 边界态 | ① `compact` 模式：activity 只显示速度、usage 只显示缓存命中；无数据则整个 pill 不渲染（`C/StatsPills.tsx:242,291-293`）；② 无计时数据 → activity 降级为静态读数（`:248`）；③ 计费为 0 → usage 不渲染（`:287`）；④ `sessionStats` 投影缺失 → 回落 fold（`:160-164`）；⑤ 窄视口 → 面板 `max-width: calc(100vw−24)`。 |
| 11 | 无障碍 | `button` + `aria-haspopup="dialog"` + `aria-expanded` + `aria-label`（含完整读数，`:198-208`）；面板 `role="dialog" aria-label`（`:210-216`）；`Escape` 关闭。 |
| 12 | 依据 | **E0** `C/StatsPills.tsx:160-334`；**E0** `C/StatsPills.module.css:1-59`；**E0** `C/stat-dialog.ts:9-76`；**E0** `C/stat-dialog.module.css:1-95`；**E1** `P/locale.ts`（`stats.counts`「N 轮 M 步」、`stats.cacheHit`、`stats.dialog.*`）。 |
| 13 | 反证 + 验收 | **反证**：① 若面板用普通下拉（点外部关闭靠冒泡），键盘激活相邻 pill 时旧面板不会关，出现两个重叠面板（源码用 capture 阶段 click 解决，`:49-53`）；② 若无「可展示行」也渲染成按钮，用户点开一个空面板（`:246-248` 注释：a window without one timed figure has no dialog rows）。**验收**：`CHAT-43` 任意时刻最多一个统计面板（真机 + 单测）；`CHAT-44` 无计费数据时不渲染 usage pill（单测）。 |

### 2.20 `chat.stats.turn-usage` — 轮内用量 pill

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.stats.turn-usage`。位于轮尾操作条图标簇末尾（`C/TurnTailNodeView.tsx:73-75`）。出现条件：**`detailed` 模式** 且该轮有 `tokenUsage`。每轮最多 1 个。 |
| 2 | 网格与定位 | `inline-flex; gap:4; height:calc(28px + delta); padding:6px 8px`，字号 secondary−1（`C/TurnUsagePanel.module.css:17-33`）；与相邻 pill `margin-left:-6px` 让一组读数视觉成簇（`:9-14`）。面板与 2.19 共用皮肤。 |
| 3 | 命中区 | 28px 高 × 文本宽；`max-width:480px` 时收成 28×28 纯图标（`:59-72`）。 |
| 4 | 手势 | 单击开/关面板；Esc/外部点击关闭（同 2.19 的 `useStatDialog`）。 |
| 5 | 行为 | 开关本地 `open`；面板数据来自 `TurnTokenUsage`（不可用时整 pill 不渲染）。 |
| 6 | 视觉状态 | default tertiary；hover / `[aria-expanded]` → 背景 hover 色（`:49-53`）。 |
| 7 | 布局占位与展开形态 | pill 在操作条内联；面板 **C 形态**（portal 浮层，`C/TurnUsagePanel.tsx:52-107`）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无自定义。 |
| 10 | 边界态 | ① 无 `cacheReadTokens` → 不显示缓存命中行（`:75-80`）；② 无 `routes` → 不显示模型行（`:69-74`）；③ `reasoningTokens` 存在 → 输出行追加「（其中推理 N tok）」（`:96-103`）；④ 窄屏只留图标（css `max-width:480`）。 |
| 11 | 无障碍 | `button` + `aria-haspopup="dialog"` + `aria-expanded`（无 `aria-label`，用可见文案「用量 N tok」作可达名）；面板 `role="dialog" aria-label="本轮用量"`。 |
| 12 | 依据 | **E0** `C/TurnUsagePanel.tsx:31-110`；**E0** `C/TurnUsagePanel.module.css:1-73`；**E0** `C/TurnTailNodeView.tsx:31,73-75`；**E1** `P/locale.ts`（`message.turnUsage.*`「本轮用量」「未缓存输入」「缓存读取」「缓存写入」「输出」）。 |
| 13 | 反证 + 验收 | **反证**：若在非 detailed 模式也显示，标准阅读视图会被一串数字图标打断；若窄屏仍显示文字，28px 图标簇会被撑宽挤掉分支/复制按钮。**验收**：`CHAT-45` 仅 detailed 出现（截图）；`CHAT-46` 480px 以下只剩图标（截图）。 |

### 2.21 `chat.settings.preference-row` — 设置项选择器（chat 设置子页共享行）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.settings.preference-row`。设置页「两列行 = 标签+说明 / 选择器」。出现次数 = 使用它的设置项数量（`Work details`、`Collapse work details` 等，`S/PreferenceRow.tsx:11-18`）。 |
| 2 | 网格与定位 | `.row` 两列；选择器 button 右对齐 + chevron（`S/PreferenceRow.module.css:47` cursor）。 |
| 3 | 命中区 | 选择器 button 自身（尺寸由该 css 决定，本路未逐值核对）。 |
| 4 | 手势 | 单击打开 Menu；选一项即关闭。键盘：`aria-haspopup="menu"` + Menu 组件键盘语义（`ui-primitives`）。 |
| 5 | 行为 | `setOpen(v=>!v)`（`:34`）；选择 → **先 `focus({preventScroll:true})` 归焦选择器，再关菜单，再 `onSelect(id)`**（`:22-26`）。幂等；选择可改。 |
| 6 | 视觉状态 | `aria-expanded` 跟菜单开合；hover/focus 由 `PreferenceRow.module.css`（`:47` cursor:pointer 旁证）。 |
| 7 | 布局占位与展开形态 | 菜单是 **C 形态浮层**（`Menu` + `portal` + `anchor`，`:47-56`），不占行内空间。 |
| 8 | 滚动语义 | 菜单自身滚动由 `Menu` 负责。 |
| 9 | 动效 | 由 `Menu` 提供。 |
| 10 | 边界态 | 选项较多时菜单滚动；无选项时（不太可能）空菜单。 |
| 11 | 无障碍 | `button` + `aria-haspopup="menu"` + `aria-expanded`；选择后焦点回到选择器（`:23`）。 |
| 12 | 依据 | **E0** `S/PreferenceRow.tsx:19-58`；**E0** `S/PreferenceRow.module.css:47`；**E1** `N/README.md:116-143`（Work details / Collapse work details 两个设置项）。 |
| 13 | 反证 + 验收 | **反证**：若选择后不归焦选择器，焦点会掉到 `body`，键盘用户要重新 Tab 十几个控件才能回到原处。**验收**：`CHAT-47` 选择后焦点在选择器上（a11y 单测）。 |

### 2.22 `chat.tool.row` — 工具行（工具卡折叠主体）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.tool.row`。每个工具调用 1 行；嵌套子调用由 `ToolCallTree` 递归渲染（`T/ToolCallTree.tsx:61-102`）。出现条件：任何非 `preparing` 且「有原始参数 / 有输出 / 有形态化卡」的调用可展开（`T/components/ToolRow.tsx:170`）。 |
| 2 | 网格与定位 | 「24px 单行：[16 leading] gap6 [标题 13/24] gap8 [2×2 点] gap8 [摘要占满 + ellipsis]」（`T/components/ToolRow.module.css:1-7`）。摘要 secondary 13/24；`summarySuffix` 不参与 ellipsis（`:45-56`）；diff 统计用等宽、secondary−2（`:58-77`）。展开体左缩进 4px（`.bodyWrap` 内各卡 `margin:4px 0 4px 4px`，`:257-265`）。 |
| 3 | 命中区 | 整行 `DisclosureRow`（`expandOnRowClick`，`ToolRow.tsx:370`）；摘要中若有文件路径/URL，**该片段是独立命中区**（见字段 4）。行高 ≈24px，远小于 44dp → Q-03。 |
| 4 | 手势 | ① 整行单击 = 展开/收起；② 摘要内**路径 button** 单击 = 打开文件（`onOpenFile`，`:189-195,214-221`），`onKeyDown` 阻止 Enter/Space 冒泡以免变成展开（`:202-204,218`）；③ 摘要内 **http(s) 链接 `<a>`** 单击 = 新标签打开，`onClick` 只 `stopPropagation` 不阻断浏览器（`:98-101,222-232`）；④ 展开体内 **Inspect pill** 单击 = 跳轨迹（`:342-351`）；⑤ 行级键盘 Enter/Space 由 `DisclosureRow` 提供（`ui-primitives`，**本路未取证**，已转交 §附注）。**无长按/双击/滑动**。 |
| 5 | 行为 | `useDisclosure()` 的 `toggleExpand`（`:149`）；`keepContentWhenOpen`（展开体在行再次渲染时不卸载）。`expandable = state!=='preparing' && (有输入|有输出|有卡)`（`:170`）。Inspect → `inspectCall(callId)`（由 `ToolCallTree.tsx:38` 注入）。幂等。 |
| 6 | 视觉状态 | `data-state` ∈ preparing/running/ok/error/stopped（`:359`）；running → `running` prop 驱动 shimmer（`:177,367`）；error → 摘要红（`.errorSummary`，`T/components/ToolRow.module.css:106-109`）且**抑制 suffix 与 diff 统计**（`:186-188`）；stopped → 摘要琥珀（`T/components/ToolRow.module.css:111-114`）；`cordis_*` 工具有 500 字重（`T/components/ToolRow.module.css:18-20`）。Inspect pill 默认 `opacity:0`，`.root:hover` 或自身 `focus-visible` → 1（`T/components/ToolRow.module.css:143-150`）。 |
| 7 | 布局占位与展开形态 | 折叠 = 定高 24px（`DisclosureRow` chrome）；展开 = **形态 A**（inline，随内容长高，下方兄弟下移）；内部若干卡各自封顶内滚（terminal 224px、IN/OUT 各 150px、code 260px，css:172-175,193-201,303-308）。Inspect pill **预留一行**，显隐不推挤布局（css:117-126 注释）。 |
| 8 | 滚动语义 | 外层滚；展开体内按卡片分治内滚（见字段 7）。内滚到边行为由各 primitive 决定（本路未取证到 `overscroll-behavior`，→ 见 §4 `Q-CHAT-03`）。 |
| 9 | 动效 | Inspect pill `opacity 100ms`（css:144）；其余由 `DisclosureRow`/primitive 提供；运行中是 shimmer，非位移动画。 |
| 10 | 边界态 | ① `preparing` → 不可展开、无 disclosure（`:170,177`）；② 无摘要 → 分隔点一起省略（`:208-211` 注释）；③ error/stopped → 不显示 diff 统计与 suffix（`:186-188`）；④ 卡互斥：`askQuestion → terminal → diff → read → image → search → web → details → 文本 IO`（按序取第一个非空，`:169`）；⑤ 窄行 → 摘要先 ellipsis，`summarySuffix` 与 diff 统计仍可见（`:44-56`）；⑥ 大字号：行高与 leading 随 delta。 |
| 11 | 无障碍 | 视觉隐藏运行态文案：`row.preparing`「准备中」/`row.running`「运行中」/`row.failed`「失败」/`row.stopped`「已停止」（`T/components/ToolRow.tsx:103-112,360`）；路径/链接有独立可达角色（button / a）。 |
| 12 | 依据 | **E0** `T/components/ToolRow.tsx:98-379`；**E0** `T/components/ToolRow.module.css:1-320`；**E0** `T/ToolCallTree.tsx:20-127`；**E0** 截断行数 `T/models/diff-card-model.ts:8`、`T/models/read-card-model.ts:17`、`T/models/search-card-model.ts:12`；**E1** `P/locale.ts`（`row.*`、`bash.*`）。 |
| 13 | 反证 + 验收 | **反证**：① 若摘要里的路径不做独立命中区，用户点路径想打开文件却展开了整行（源码专门 `stopPropagation`，`:189-203`）；② 若键盘 Enter 在链接上冒泡到行处理，会被 `preventDefault()` 吃掉 → 链接打不开（源码注释直述，`:197-204`）；③ 若失败行仍显示 `+n/−n` 统计，用户会以为改动已生效（`:184-188` 注释：failure keeps first line, omits totals）；④ 若 Inspect pill 用绝对定位浮在卡上，会盖住内容（所以预留一行）。**验收**：`CHAT-48` 路径点击打开文件且不展开行（真机 + 单测）；`CHAT-49` 失败行不显示 diff 统计（截图）；`CHAT-50` Inspect hover 显隐不推挤布局（录屏）。 |

### 2.23 `chat.tool.details` — 只读详情卡（字段/列表/代码/Markdown）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.tool.details`。工具行展开体的一种（`detailsBody` 非空时独占展开体，`T/components/ToolRow.tsx:309-310`）。每条详情项 `li`；可含 `groups`（可折叠子组）与 `unchanged`（未变化项折叠组）。 |
| 2 | 网格与定位 | 卡：`margin:4px 0 4px 4px; border:0.5px border-l1; radius-lg; background:code-block; font:xs-13`（`T/components/ToolDetails.module.css:1-9`）。`li` padding `10px 14px`，项间 0.5px 分隔（`:19-27`）。字段两列 grid `5.5em / 1fr`（`:133-141`）。列表封顶 **320px** 内滚（`:11-17`）。 |
| 3 | 命中区 | ① 路径 button（仅当 `item.location` + `onOpenFile` 同时存在，`:58-63`）；② 「未变化项」与「分组」的 `<summary>` 整行；③ 代码块复制按钮（CodeBlock 自带，不在本包）。 |
| 4 | 手势 | 单击路径 = 打开文件（`:59-62`）；单击 `<summary>` = 原生 details 切换；键盘由各元素自身语义。 |
| 5 | 行为 | 路径 → `onOpenFile(path, {line})`；分组/未变化 → 浏览器原生 `open` 属性（无状态代码）。幂等。 |
| 6 | 视觉状态 | 状态标记：`+ / − / ✓ / ▶ / □`（`:48-57`）；added 绿、removed 红且标题删除线（`:91-102`）；badge 五种 tone（`:171-174`）；`unchanged`/`previous` 用 tertiary（`:86-89`）；路径 hover 下划线、focus-visible 3px 外发光（`:255-256`）。 |
| 7 | 布局占位与展开形态 | 卡在工具行的展开体内（形态 A 的内容）；列表 320px 后内滚；子组/未变化项是卡内**二级折叠**。 |
| 8 | 滚动语义 | 列表自身内滚（320px）；代码块自身内滚（240px，`:229-236`）；卡外层由工具行展开体承担。 |
| 9 | 动效 | `summary > svg` 展开时 `rotate(90deg)`（`:121-123,216`）；无其它动画。 |
| 10 | 边界态 | ① `items.length===0` → 显示 `model.empty` 空态（`:124`）；② `hasInspect` → 内容右内边距 88px 给 Inspect pill 让位（`:29-33`）；③ 只有代码块一项 → 去掉该 `li` 的 padding（`:235-240`）；④ 长值 `pre-wrap` + `overflow-wrap:anywhere`。 |
| 11 | 无障碍 | 状态标记 `role="img" aria-label`（`:50`）；`<details>/<summary>` 原生语义；路径为 `button`。 |
| 12 | 依据 | **E0** `T/components/ToolDetails.tsx:43-139`；**E0** `T/components/ToolDetails.module.css:1-256`。 |
| 13 | 反证 + 验收 | **反证**：若列表不封顶内滚，一个大 todo/文件列表能把整轮正文顶出屏幕；若 removed 项不做删除线只换色，色盲用户无法区分增删。**验收**：`CHAT-51` 列表 320px 后内滚（截图）；`CHAT-52` 增删同时有色与符号/删除线（截图 + a11y 断言）。 |

### 2.24 `chat.tool.question-row` — 提问卡（`ask_user_question`）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.tool.question-row`。条件：工具名 `ask_user_question` / `request_user_input` 走专用 toolview（`TV/ask-question-row.tsx`）。每调用 1 行。 |
| 2 | 网格与定位 | 复用 `ToolRow.module.css` 的行 chrome + 自有 `panelButton` 样式（`TC/QuestionToolRow.module.css:18`）。折叠摘要在标题后；面板入口按钮嵌在 `collapsedContent` 里。 |
| 3 | 命中区 | ① 整行 disclosure；② **面板入口按钮**（嵌在 summary 内，独立命中区）；③ 展开体内 Inspect pill。 |
| 4 | 手势 | ① 行单击 = 展开/收起；② 面板按钮单击 → `openPanel()` 优先，打不开才 `toggle()`（`TC/QuestionToolRow.tsx:40`）；③ 面板按钮 `onKeyDown` Enter/Space **stopPropagation**，否则会被行处理吃掉（`:41`）；④ Inspect 单击。 |
| 5 | 行为 | `toggle` 来自注入的 `useDisclosure`（`:23`）；`openPanel()` 返回是否成功抢占（由 ask-question-row 提供）。展开体：`AskQuestionCard` 或 IN/OUT 文本卡（`:46-56`）。 |
| 6 | 视觉状态 | running → 摘要 shimmer + 视觉隐藏「运行中」；行视觉同 ToolRow（data-variant=others）。 |
| 7 | 布局占位与展开形态 | 形态 A；`keepContentWhenOpen`（展开体不卸载）。 |
| 8 | 滚动语义 | 外层滚；`AskQuestionCard` 内部 `overflow-y:auto`（`TC/AskQuestionCard.module.css:8`）。 |
| 9 | 动效 | 同 `DisclosureRow`。 |
| 10 | 边界态 | ① `expandable = bodyRaw|output|askQuestion` 任一非空（`:24`）；② 摘要为空 → 分隔点省略（`:35-38`）；③ 面板已被 composer 接管 → 点行只开面板不展开（`:40`）。 |
| 11 | 无障碍 | 行 `DisclosureRow`；面板按钮 `button` + 图标 + 文案（`panelLabel`）；视觉隐藏 `row.running`。 |
| 12 | 依据 | **E0** `TC/QuestionToolRow.tsx:9-64`；**E0** `TV/ask-question-row.tsx:215,347-354`；**E0** `TC/QuestionToolRow.module.css:18`；**E0** `TC/AskQuestionCard.module.css:8`。 |
| 13 | 反证 + 验收 | **反证**：若面板按钮不做 `stopPropagation`，点「回答问题」会同时展开行 → 面板与展开体抢占屏幕，用户不知道哪个是回答入口。**验收**：`CHAT-53` 点面板入口不触发行展开（单测）；`CHAT-54` 键盘 Enter 在按钮上不冒泡（a11y 单测）。 |

### 2.25 `chat.tool.bash-row` — bash 工具行（toolview 样例实现）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.tool.bash-row`。`tool.call.toolview` 槽以 `key: 'bash'` 注册的样例实现（`TV/bash-sample.tsx:169-176`）。条件：bash 调用。每调用 1 行。 |
| 2 | 网格与定位 | 自定义卡：`.card` 包一层 + `.root` 单行（图标/chevron → 视觉隐藏状态 → shimmer 标题 + 分隔点 + 摘要）（`TV/bash-sample.tsx:94-121`）。 |
| 3 | 命中区 | 整行（`role="button"` + `tabIndex=0`，**不是** `DisclosureRow`），`:102-107`。 |
| 4 | 手势 | 单击（`expandable` 才有）；`onKeyDown` 仅 Enter/Space 且 `expandable` 时 `preventDefault()` + toggle（`:79-83,105-106`）。chevron 在 hover 时替换活动图标（`:84-93`）。 |
| 5 | 行为 | `useDisclosure()` 的 `toggleExpand`（`:58`）；`expandable = terminal !== null || genericBody`（`:66`）。 |
| 6 | 视觉状态 | `data-state` ∈ preparing/running/ok/error/stopped；error → 摘要红、stopped → 琥珀（`:114-117`）；running → shimmer（`:110`）；背景/终端卡样式见 `TV/bash-sample.module.css`。 |
| 7 | 布局占位与展开形态 | 形态 A（inline 展开）；展开体 = `TerminalBlock`（maxLines=Infinity，输出区内滚 224px）或 IN/OUT 卡。 |
| 8 | 滚动语义 | 外层滚 + 终端输出区内滚（`TV/bash-sample.module.css:43` `overflow-y:auto`，banner sticky `:57`）。 |
| 9 | 动效 | shimmer；reduced-motion 由 `TextShimmer` 处理。 |
| 10 | 边界态 | ① 后台启动（`isBackgroundShellCall`）→ 保留通用 IN/OUT 卡（`:59-65`）；② 溢出/持久会话结果 → 通用卡；③ 退出码非 0 但调用本身 `isError:false` → 用 terminal 模型判失败（`:52-56` 注释）；④ 大输出 → 终端卡内滚。 |
| 11 | 无障碍 | `role="button"` + `tabIndex=0` + `aria-expanded`（仅 expandable）；视觉隐藏状态文案（`:109`）。 |
| 12 | 依据 | **E0** `TV/bash-sample.tsx:27-176`；**E0** `TV/bash-sample.module.css:43,57,88,179,219,225,241`。 |
| 13 | 反证 + 验收 | **反证**：若不可展开时仍给 `role=button|tabIndex=0`，屏幕阅读器会报一个按下去没反应的按钮；若 Enter 处理不 `preventDefault`，页面会同时触发默认滚动/提交。**验收**：`CHAT-55` 不可展开 bash 行无可点击语义（a11y 树断言）；`CHAT-56` Enter/Space 只 toggle 不滚动（单测）。 |

---

## 3. 排除清单

`facts.csv` 共 401 条 `status=excluded`，按类分布如下（机器可复核，明细见 csv）：

| 排除类 | 条数 | 理由（逐条写入 note） |
|---|---|---|
| `kind` 以 `css.` 开头（css.cursor / css.focus / css.sticky / css.contain / css.overflow） | **84** | 「结构性 CSS 属性，不构成交互点」——按任务约定统一口径；这些行的**行为含义**已作为证据写进 §2 对应节点的字段 2/6/7/8/9，不丢信息 |
| `tests/` 目录下的命行 | **317** | 测试文件非产品 UI。逐条按 kind 写具体理由：`a11y.expanded`=断言选择器字符串；`disclosure`=夹具 import/props 里的 `useDisclosure`；`container.scroll`=测试里对 `scrollTo/overscroll` 的桩与断言；`jsx.onclick`/`native.button`/`native.input`=测试内联夹具 JSX；`jsx.onscroll`=测试注册的滚动处理函数 |
| **合计** | **401** | 与 `[check] excluded=401` 一致 |

**可点击/手势类未做批量排除**：158 条可交互命中全部落到 §2 的 25 个节点
（`[check] covered=158`），其中 34 条是「`useDisclosure`/`renderSlot` 透传链路」，
仍按 E0 归到对应节点并在 note 里注明「非独立交互点，真实交互在 X:Y」——
不写进节点会丢失「每个 toolview 都必须接 disclosure hook」这一契约事实。

### 3.1 有 UI 但**无交互点**、因此不构成节点的实现（E0，一并登记以免遗漏）

| 实现 | 位置 | 为什么不是节点 | 行为摘要（供 spec 引用） |
|---|---|---|---|
| 会话运行指示条 | `C/RunningStatus.tsx:20-41` | 纯展示，无可点/可滚/可展开 | 鲸鱼尾 + shimmer「深度求索中，用时 N ···」；整秒刷新（`LIVE_RUN_CLOCK_INTERVAL_MS=1000`，`C/message-chrome.ts:13`）；起始钳 1s、≥60s 才出分钟、≥60min 才出小时；`role=status aria-live=polite` 但**只播报「深度求索中」，不播报每秒**（`:33`）。 |
| 鲸鱼尾图标 | `C/RunningWhaleTail.tsx:10-19`；`C/ChatView.module.css:178-214` | 装饰 | `aria-hidden`；`@supports(mask-mode:alpha)` + 非 reduced-motion + 非 forced-colors 才用 APNG mask，否则静态 SVG 描边；`contain: strict` 限定重绘。 |
| 轮错误条 | `C/MessageItem.tsx:129-144` | 纯展示，无重试入口 | 错误点 + 「本轮运行失败」+ 适配文案 + `code` 等宽；`role=status`；`ACCOUNT_SIGNED_OUT` → 标题改「账号已停止」。 |
| 输出上限提示 | `C/MessageItem.tsx:146-159` | 纯展示，无重试入口 | 警告点 + 「已达到输出 token 上限」+ 提示「回答被截断…发送『继续』可让模型接着输出」；`role=status`。**未见「继续」按钮**（文案让用户自己发消息）。 |
| 停止/中断标记 | `C/AssistantMarkdown.tsx:149-151` | 纯展示 | interrupted 且该部分有内容 → 行尾追加「已停止」。 |
| 静态统计读数 | `C/StatsPills.tsx:172-182` | 不可点形态 | 无面板行时渲染 `span`，不响应 hover。 |
| 未知道具面兜底 | `C/MessageItem.tsx:381-391`；`C/ChatNodeSeat.tsx:165-172` | 兜底，非交互 | `JsonBlock` + 截断标签。**这是 DSH 唯一的「原始 JSON」视图，且只用于未知类型**——工具/消息都没有「切换 JSON」入口。 |
| 空/错/加载提示 | `C/ChatView.tsx:213-218` | 纯展示 | 「载入历史…」/「历史加载失败：{message}（{code}）」。 |
| 卷宗内部行（session prompt 等） | `C/ContextBody.tsx`(560) | 纯展示容器（本路未逐行取证 body 形态） | `NoticeBody`/`OpaqueBody` 由触发通知与上下文行复用。 |

---

## 4. 待确认（E2 候选 → 建议登记 open-questions）

> 规则：源码没写清、或 DSH 做法与 myco 已拍板冲突、两种做法都说得通的，**不自行选择**，只登记。
> 编号从 `Q-CHAT-01` 起。

| ID | 问题 | 冲突/未知 | 证据 | 我的建议（理由） |
|---|---|---|---|---|
| `Q-CHAT-01` | 过程组内滚到边要不要吸收残余速度？ | **DSH 不吸收**（`overscroll-behavior-y:auto` + README 明说可继续滚外层），而 G-04 **要求吸收**（user 已拍板）。这不是「DSH 没做」，是「DSH 做了相反的事」 | `C/ChatGroupSeat.module.css:90`；`C/use-process-scroll.ts:53-65`；`N/README.md:182`；对照 `C/TurnNavigator.module.css:54`（同一代码库另一处用 `contain`） | 保留 G-04（user 已拍板），在面板 spec 里显式标注「故意偏离 DSH」并写反证（用户原话）。不建议为此改 G-04。 |
| `Q-CHAT-02` | 内滚 body 没有 `tabindex`，键盘/辅助技术能否滚动？ | 容器无 `role`/`tabindex`，Chromium 对可滚动容器默认聚焦行为依版本与 UA 而变 | `C/ChatGroupSeat.tsx:194`（无 tabindex）；`C/use-process-scroll.ts:62-64`（只处理按键，不建焦点） | 建议 myco 显式 `tabIndex=0` + `aria-label`，并把「到边吸收」在键盘路径上也实现（PageDown 到边后不再滚动外层）。 |
| `Q-CHAT-03` | 工具展开体内部滚动容器的到边行为 | 各 primitive（TerminalBlock/CodeBlock/DiffBlock…）来自 `ui-primitives`，本路未取证是否设 `overscroll-behavior` | `T/components/ToolRow.module.css:172-175,193-201,303-308`（只看到 `overflow-y:auto`/`max-height`） | 转交 `ui-primitives` 一路取证；在结果回来前，myco 一律按 G-04 实现。 |
| `Q-CHAT-04` | 移动端（容器 ≤900px）是否保留轮次导航？ | DSH 直接 `display:none`；而 `<900px` 恰是手机竖屏全部场景 → DSH 手机端只有 v 钮 | `C/TurnNavigator.module.css:207-211` | 建议 myco 手机端 = v 钮；平板/桌面宽屏才出轨道。若想手机也有轮次导航，需要新形态（下拉轮次列表？），属新设计，须用户拍板。 |
| `Q-CHAT-05` | 思考行「每轮多个」是否接受？ | 金标准样本原写「最多 1 个」，源码证伪；多行形态会改变 myco 现有「一段过程一个思考行」的实现假设 | `C/AssistantMarkdown.tsx:76-106`；`N/process-groups.ts:172` | 按 E0 改为 0..N（已在 §2.4 修正样本字段 1）；但这意味着 `CHAT-03b` 验收要新增，且需确认 myco 是否也要按 block 粒度渲染。 |
| `Q-CHAT-06` | 工具长输出截断值是否照抄？ | diff 9 行 / read 8 / search 8 / terminal 224px / IO 各 150px / code 260px / details 320px，是 DSH 的工程取值，没有理论依据 | `T/models/diff-card-model.ts:8`；`T/models/read-card-model.ts:17`；`T/models/search-card-model.ts:12`；`T/components/ToolRow.module.css:172-175,193-201,303-308`；`T/components/ToolDetails.module.css:11-17` | 手机屏更矮，建议按「一屏的 1/3」换算而不是照抄 px；需用户拍板（否则是 E2）。 |
| `Q-CHAT-07` | 统计数据从哪来？ | DSH 依赖宿主投影 `sessionStats` / `tokenUsage`（跨分页/压缩都稳定），缺投影才回落窗口内 fold | `C/StatsPills.tsx:160-164,282-287`；`C/TurnTailNodeView.tsx:73-75` | myco 若在自己算，分页/压缩后数字会变（DSH 专门为此改用投影）。建议 myco 也由「会话持久统计」提供，不要在视图层 fold。 |
| `Q-CHAT-08` | 轮次导航刻度 10px 定距是否可用于手机？ | 10px pitch 的命中区远低于 44dp，DSH 靠「宽屏 + 鼠标」兜住 | `C/TurnNavigator.tsx:29`；`C/TurnNavigator.module.css:84-95` | 手机不用轨道（见 Q-CHAT-04）即可规避；若一定要用，需改「可拖动的滑轨 + 放大命中区」。 |
| `Q-CHAT-09` | 反馈（赞/踩 + 弹窗）是否属 myco 首版？ | `ui-message-feedback` 是独立包，通过槽注入；DSH 把它当可选能力 | `C/TurnTailNodeView.tsx:52-55,72`；`C/MessageIconActions.tsx:26-33,90` | 建议首版不做（属产品运营能力，不是阅读核心），但**槽位**要在 `chat.msg.actions` 里预留，避免以后改结构。 |
| `Q-CHAT-10` | 组折叠时的落点规则（未关闭组贴底 / 已关闭组贴顶）是否照抄？ | 规则反直觉但有理：live 组要看最新；历史组要从头读 | `C/ChatGroupSeat.tsx:176`；`C/use-process-scroll.ts:36-39`；E1 `N/README.md:184` | 建议照抄并在 spec 写明理由；若用户不同意，需另一条验收（打开历史组落在顶部 = 默认就该如此）。 |
| `Q-CHAT-11` | 「思考」标题行在 Compact 模式不显示摘要是否符合预期？ | DSH Compact 静默只留标题；myco 现状是「最后活动行 + 呼吸点」（已作废，Q-02 决定按 DSH） | `P/presentation-policy.ts:29-35`；`C/ReasoningRow.tsx:58-59` | 与 Q-02「完全按 DSH」一致 → 建议照 DSH；此条只是把「Compact 也影响思考摘要」这个细节显式化。 |
| `Q-CHAT-12` | 模型重试 / 轮错误 / 输出上限三态的「重试入口」在哪？ | **DSH 没有重试按钮**：retry 是自动倒计时（`<details>` 只展开看原因）；轮错误与 max-tokens 是纯展示；max-tokens 靠文案让用户发「继续」 | `C/MessageItem.tsx:106-159`；`P/locale.ts` `message.maxTokens.hint` | myco 若想要「点一下重试」是**新增能力**（E2），不能声称「照 DSH」；建议先按 DSH 文案，重试入口进 Q 由用户决定。 |

---

## 5. Q-05 / Q-07 的直接答案

### 5.1 Q-05「过程组封顶上限取多少」

**结论：上限 = `min(400px, 50vh)`（双上限取小），不是屏高百分比、不是固定 dp、也不是行数。**

| 项 | 真实值 / 机制 | 证据 |
|---|---|---|
| cap 的定义 | `max-height: min(400px, 50vh)`，`overflow-y: auto`，`scrollbar-gutter: stable` | `C/ChatGroupSeat.module.css:86-92` |
| 语义 | 两个上限**同时**生效：绝对高度 ≤400px，且 ≤视口高的 50%；小屏听 `50vh`，大屏听 `400px` | 同上（`min()` 语义） |
| E1 文档复述 | 「Manually expanded group bodies use 6px row spacing, a `min(400px, 50vh)` height cap, and 24px directional fades」 | `N/README.md:182` |
| cap 何时被取消 | `capped = grouped && (!deferCompletedTurns \|\| open)`；取消时 `.expandedBody { max-height:none; overflow:visible; scrollbar-gutter:auto }`。即：Detailed 的 running 轮、Verbose 全时段、以及「On next message」折叠时机下的历史组展开 | `C/ChatGroupSeat.tsx:182-184`；`C/ChatGroupSeat.module.css:135-139` |
| 行距 | 组内成员 `6px`（`--dsh-chat-flow-gap:6px`）；assistant 回复前后 `12px`；turn-process 后 `16px` | `C/ChatGroupSeat.module.css:127-134`；`C/ChatView.module.css:72-84,111-116` |
| 到边渐隐的「高度/透明度参数」 | **不是高度、不是透明度数字**：`mask-image` 线性渐变，两端各 **24px**（`transparent 0 → #000 24px`，下缘对称；两端同时可滚时合并成一条四段渐变）。判定阈值 **1px**（`top>1` / `top<floor−1`）。**没有任何 opacity 参数** | `C/ChatGroupSeat.module.css:101-117`；`C/ChatGroupSeat.tsx:182-184`；`C/use-process-scroll.ts:48` |
| 内滚与外滚的手势仲裁 | **DSH 不仲裁、不吸收**：`overscroll-behavior-y: auto`（= 允许滚动链）；`onWheel/TouchStart/PointerDown/KeyDown` 只调 `interrupt()` 取消**跟随动画**，不 `preventDefault`/`stopPropagation`。README 明写 “Wheel scrolling can continue into the outer transcript at an edge” | `C/ChatGroupSeat.module.css:90`；`C/use-process-scroll.ts:53-65`；`N/README.md:182` |
| 内滚的跟随 | 独立控制器，阈值 **1px**；只有本组贴底时内容增长才跟随；手动打开的落点按「未关闭→底 / 已关闭→顶」分叉 | `C/use-process-scroll.ts:26,36-48`；`C/ChatGroupSeat.tsx:175-178` |
| 对比：同一代码库另一处内滚 | 轮次导航条用 `overscroll-behavior: contain`（**阻止**传链） | `C/TurnNavigator.module.css:54` |

**对 myco 的直接影响**

1. myco 拍的「45% 屏高 / ≤360dp」是 E2，**作废**；按 E0 取 `min(400px, 50vh)`，
   并按 G-10 的 240dp 阈值区分「展开动画」——注意 **240dp 是动画阈值，与 cap 无关**，两者别混。
2. **到边渐隐用 mask，不用 opacity、不用渐变遮罩 div**：24px、两端对称、1px 判定；
   两端都超时合并成单条 `mask-image`（避免两个 mask 叠加出现中间断色）。
3. **G-04 与 DSH 相反，必须明确选择**：DSH 传链，user 要求不传。本路建议保留 G-04
   （user 已拍板），并在面板 spec 把「偏离 DSH」写成显式决策（`Q-CHAT-01`）。
   实现要点：Compose/Web 都要「到边吸收 + 新起手势才交外层」，不是只靠 CSS。
4. **`scrollbar-gutter: stable` 必须一起抄**：否则内容在「刚好超一屏」时会横向跳动
   （现有 myco 的 45% 方案若没这一条，会有 8–15px 抖动 → 属 G-02 同类症状）。

### 5.2 Q-07「回到底部 vs 轮次导航」

**结论：DSH 两者都有，不是二选一。** 它们解决不同问题、各自有独立出现条件，
myco 现在只有 v 钮并不算「选错」，但缺一层「轮次导航」。

| 维度 | 回到底部（v 钮） | 轮次导航（右侧轨道） |
|---|---|---|
| 代码 | `C/ChatView.tsx:240-251`；样式 `C/ChatView.module.css:242-294` | `C/TurnNavigator.tsx:1-295`；样式 `C/TurnNavigator.module.css:1-223` |
| 出现条件 | **`!scroll.followingTail`**（不跟随时才挂载；跟随中整钮不存在） | `scroll.initialized` **且** 轮数 ≥2；**容器宽 ≤900px 隐藏** |
| 位置 | 右下角，`bottom:16px`（宿主滚动时 = `--dsh-composer-height + 16px`），z-index 8 压住输入卡 | 右侧 `right:12px`，垂直居中于「视口高 − composer 高」的带内；`max-height: min(band−64, 420px)` |
| 尺寸 | 34×34 圆钮 | 轨道宽 28px；刻度高 10px、**定距 10px**；tick 20×2px |
| 交互 | 单击 = 立即贴底（并取消翻页/跳转） | 单击跳轮；hover/focus 出预览卡（prompt 50 字符 + response 120 字符，1 行 + 3 行 clamp）；未加载轮先翻页再落位，期间刻度脉冲 |
| 滚动 | 不滚 | 自身滚，`overscroll-behavior: contain`，24px 两端渐隐，刻度虚拟化（10px 定距、overscan 3） |
| 视觉 | 悬浮填充 + panel 阴影；hover 换 hover 填充 | tick `scaleX 0.4/0.6/0.9/1` 对应 未加载/默认/预览/活动；busy 1s 脉冲；focus 品牌色 + 描边环 |
| 无障碍 | `aria-label="回到底部"` | `nav aria-label="轮次导航"`；刻度「跳转到第 N 轮」/「加载并跳转到第 N 轮」+ `aria-current` + `aria-busy` + `role=tooltip` 预览 |
| 动效 | 无（挂载/卸载） | 居中 `smooth`（reduced-motion → `auto`）、预览 120ms 进场、tick 140ms |
| 移动端 | 有 | **没有**（≤900px `display:none`） |

**跟随机制（回答「哪些条件下自动贴底 / 上翻如何停止 / 如何恢复」）**

1. **判据是距离不是方向**：`nearBottom = |floor − top| <= 25`（`FOLLOW_THRESHOLD=24` + 1，
   `C/use-chat-reading.ts:7,252`；`C/use-scroll-follow.ts:67`）。不是「用户往上滑了」。
2. **自动贴底**：`followingTail===true` 且内容增长 → `followTail()`；平滑滚动期间若内容继续增长，
   **重定向目标**而非跳到 floor（`C/use-scroll-follow.ts:138-158`）；
   距底在阈值内且无动画时用 `jump`（瞬时），避免「差 3px 也慢慢滑」。
3. **停止跟随**：任何被归因为「读者移动」的滚动且离底 >1px → 500ms 内采样，
   采样点判定 `followingTail = nearBottom`（`C/use-chat-reading.ts:134-144,143,218-234`）。
   手势（wheel/touchstart/pointerdown/keydown/beforematch）额外立刻 `interruptFollow()`
   取消进行中的 smooth 动画（`C/use-chat-viewport.ts:46-47,80`；`C/use-chat-scroll.ts:134`）。
4. **恢复跟随**：读者移动后落点在距底 1px 内 → 立即 `followTail()`（`C/use-chat-reading.ts:137-141`）；
   或采样后 `nearBottom` → 恢复。**没有**「点按钮才恢复」的硬门槛（v 钮只是快捷方式）。
5. **位置记忆**：跟随中存 `null`（=「打开就贴底」），不跟随才存语义锚点
   （`C/use-chat-reading.ts:186-192`）；恢复时存档缺失 → 直接贴底（`:96-99`）。
   这样不会出现「按上次像素 y 落位但内容高度已变」的错位。

**对 myco 的直接影响**

1. **保留 v 钮 + 新增轨道层**：v 钮按 G-01 已是 overlay、不占位（与 DSH 一致：`.toBottomSlot`
   高 0 + 按钮负 margin），不必替换。轨道层是新面板节点（`chat.list.turn-rail`）。
2. **v 钮的显示条件要改**：myco 现在是「已上翻就显示」，DSH 是「`!followingTail` 才挂载」。
   两者近似但不完全等价——按距离判定（≤25px）才是 DSH 语义；若按「用户滑过」判定会出现
   「轻推到距底 5px 也显示按钮」（`Q-CHAT-01` 的姊妹问题，建议按 DSH）。
3. **跟随阈值 25px、采样 500ms 必须写进 spec**，否则实现会退化成「一上翻就永久停跟随」。
4. **轨道在 ≤900px 隐藏**（`Q-CHAT-04`）：手机端最终形态 = v 钮 + 跟随，
   不需要为手机发明轮次导航；若产品要坚持，那是一条**新增**设计（E2），不能叫「照 DSH」。
5. **轨道内滚必须 `overscroll-behavior: contain`**（`C/TurnNavigator.module.css:54`）——
   与过程组的 `auto` 形成对照，别把两者写成同一条规则。

---

## 附注：覆盖率与可复核性

- 分母：`python tools\ui-audit\ui_audit.py scan --root E:\deepseek-harness\packages\client`
  → 全量 3060 条，本路 4 前缀 **559** 条（`src/` 242、`tests/` 317）。
- 对账：`[check] covered=158 excluded=401 uncovered=0 stale=0`（`_ws/out/coverage-dsh-chat.md`）。
- 158 条 E0/E1 全部映射到 §2 的 25 个节点，无一条悬空；每条的 `path:line:kind` 与 note
  见 `docs/spec/ref/dsh-chat.facts.csv`。
- 本路未取证、需转交的：`ui-primitives`（`DisclosureRow` 的 `aria-expanded`/键盘语义、各
  Block 的 `overscroll-behavior`）、`ui-conversation`（`[data-conversation-scroll]` 宿主、
  `--dsh-composer-height` 观测）、`ui-slots`（槽渲染错误边界）。均已登记 §4。

# DSH 侧栏事实库（R1c）

> 对象：`E:\deepseek-harness\packages\client` 下 `ui-sidebar`、`ui-sidebar-browser`、`ui-conversation`、`ui-session` 的 `src/`。
> 机器可读事实：`docs/spec/ref/dsh-sidebar.facts.csv`（201 行，与 `_ws/out/findings-dsh-sidebar.csv` 逐键对齐）。
> 对账：`_ws/out/coverage-dsh-sidebar.md` —— **findings 201 / E0 90 / excluded 111 / uncovered 0 / stale 0**。
> 依据级别：`E0` = 源码行（本文件全部结论的形式）；`E1` = 注释/README 声称；`E2` = 推测（只出现在 §4）。
> 铁律 G-00：本文件 §0–§3 的每一条都能落到 `path:line`；落不到的一律进 §4。

---

## 0. 一页结论

### 0.1 分母与命中

| 项 | 数 | 说明 |
|---|---|---|
| 扫描根 | `E:\deepseek-harness\packages\client` | 1918 文件 / 3060 交互点（全客户端） |
| 本路 findings | **201** | 前缀 `ui-sidebar/,ui-sidebar-browser/,ui-conversation/,ui-session/` |
| 其中取证 E0 | **90** | 全部落到 32 个节点 ID |
| 其中显式排除 | **111** | `tests/` 41 · CSS 路径 66（64 结构性属性 + 2 样式钩子）· 非交互命中 4（逐条理由见 §3） |
| 本路定义节点 | **37** | 32 个有 E0 键的节点 + 5 个「无扫描命中」的容器/滚动节点（见 §3.6 扫描器盲区） |
| uncovered | **0** | `ui_audit.py check --fail-on-uncovered` 通过 |

### 0.2 五条硬结论（每条都有 E0）

**① DSH 侧栏不是抽屉，是「网格列」。myco 的叠加覆盖抽屉在这四个包里没有任何先例。**
外壳是 `AppFrame` 的三列 grid：`gridTemplateColumns: ${cols.sidebar}px minmax(...)1fr minmax(0,${rightbarMax}px)`
（`ui-layout/src/client/AppFrame.tsx:265-266`）。侧栏宽度**占布局**，折叠只是把轨道宽度从
`SIDEBAR_DEFAULT=280` 变成 `SIDEBAR_COLLAPSED=56` 或 `0`（`ui-layout/src/client/columns.ts:13-23,52-58`）。
全包 grep `scrim|mask|backdrop|Escape|overlay`（`ui-sidebar/src`）只命中 2 处，均与遮罩无关：
一处是注释里的「overlay slots 文档序」，一处是 `mask-image` 渐变 —— **没有遮罩层**。

**② 折叠态是「三平台三形态」，不是一种。**
| 平台 | 折叠后宽度 | 面板行/列表 | 开关控件去处 | 证据 |
|---|---|---|---|---|
| 普通 Web / Linux | **56px 图标轨**（36×36 控件 + 10px 内边距） | 保留（rail 版） | 轨道内顶部，hover 由鲸鱼标换面板图标 | `SidebarRoot.module.css:31-36,344-380`；`AppFrame.tsx:169-170` |
| Windows 桌面（`data-windows-titlebar`） | **0px，无轨** | `display:none` | 提到标题栏：toggle 固定 `left:12px`、新建固定 `left:48px`，并出版 `--dsh-windows-menu-start:84px` | `SidebarRoot.module.css:39-55,73-118`；`AppFrame.tsx:169-170` |
| macOS 桌面（`darwin`） | **0px，无轨** | 无 | 搬进 frame 的 `shell.leading` 席（`left:88px; top:11px`） | `ui-sidebar/src/client/index.ts:90-98`；`AppFrame.module.css:207-215` |

**③ 窄屏（<1024）展开是「挤」不是「盖」。**
`SIDEBAR_AUTO_COLLAPSE = 1024`（`columns.ts:20-23`），`AppFrame.tsx:160-161` 判定 `narrow`。
窄屏下手动 toggle 只翻 `narrowExpanded` 覆盖位（`ui-layout/src/client/stores.ts:106-112`），
展开后仍是网格列，中心列宽度 = `max(0, viewport - side - right)`（`columns.ts:58`）——
**可以把会话列挤到 0 宽**，而不会浮在它上面。手势/遮罩/返回键：全无（见 ④）。

**④ 开合只有「点」和「快捷键」，没有手势、没有返回键语义、没有 Escape。**
- 快捷键：`sidebar.left.toggle` = desktop `⌘/Ctrl+B`、web `⌘/Ctrl+Alt+B`
  （`ui-layout/src/client/index.ts:191-201`）；`session.new` = `KeyN + primary`（web 加 Alt）
  （`ui-workspace/src/client/shortcuts.ts:88`）；`browser.new` = `KeyT + primary`（web 加 Alt）
  （`ui-sidebar-browser/src/client/index.ts:47-55`）。
- 关闭浮层的 Escape 全路只有 1 处：上下文明细面板（`ui-conversation/.../ContextMeter.tsx:80-87`）。
- 侧栏本身无 `onTouchStart/onPointerDown` 抽屉手势、无 `history.back`/返回键拦截、无焦点陷阱（grep 0 命中）。
- 宽度拖拽是另一个交互：8px 命中条、pointer capture、rAF 节流、折叠时不渲染
  （`AppFrame.tsx:58-118,301`；`AppFrame.module.css:223-233`）。

**⑤ 侧栏 shell 的两个「零重排」实现，是 myco 抽屉动效最值得抄的两处。**
- 折叠：内容先**冻结在展开宽度**（inline style）并 150ms 淡出，轨道同时被 `AppFrame` 的 grid track 裁切，
  settle 后 wide-only 内容卸载、rail 图标从 `translateX(49px)` 在余下 150ms 内入场 —— 全程不 reflow
  （`SidebarRoot.tsx:30-31,104-127`；`SidebarRoot.module.css:133-174`）。`prefers-reduced-motion` 全部关掉
  （`SidebarRoot.module.css:602-612`）。
- 滚动条：列内滚动条只在指针进入时画，离开后**保留 2000ms** 再隐；且用
  `--dsh-scrollbar-thumb: transparent` 重绑而不是 `display:none`，为的是保住 session list 的
  `scrollbar-gutter: stable` 预留 —— **显隐滚动条不会让行横移**
  （`SidebarRoot.tsx:36-39,129-170,200-212`；`SidebarRoot.module.css:120-131`）。

### 0.3 重要提醒：本路 **不包含**「会话行 / 列表组织 / 多标签」的实现

`10-panels.md` 把 `sidebar` 面板的证据包写成 `ui-sidebar, ui-sidebar-browser, ui-conversation, ui-session`，
但真实源码里**会话行、分组、搜索、行菜单都不在这四个包**：

| 用户关注点 | 真实实现位置 | 是否本路写作用域 |
|---|---|---|
| 会话行全量交互（点击/双击改名/悬浮按钮/⋯菜单/拖拽排序/悬浮卡） | `ui-workspace/src/client/rows/Rows.tsx:524-703` | ❌（`ui-workspace/` 前缀不在本路 facts 键内） |
| 分组 / 排序 / 置顶 / 归档筛选 / 折叠 / 加载更多 | `ui-workspace/src/client/tree.ts`、`rows/WorkspaceBrowser.tsx`、`stores.ts` | ❌ |
| 搜索框 / 防抖 / 结果树 / 跳转定位 | `ui-workspace/src/client/rows/WorkspaceBrowser.tsx:1015-1128,1216-1357` | ❌ |
| 多标签（右侧栏 tab 类型） | `ui-dockkit/src/components/TabLayout.tsx` + `ui-sidebar-right/src/client/tab-*.ts` | ❌ |
| 侧栏宽度/断点/拖拽 | `ui-layout/src/client/columns.ts`、`AppFrame.tsx` | ❌ |

本路**已就上述五项给出精确 `path:line` 指针**（§1.2），并登记 **Q-SB-02** 要求补一路审计。
本路 facts.csv 严格只含四个前缀的键，不替别的包登记（避免与并行的 `ui-layout`/`ui-workspace` 路冲突）。

---

## 1. 包 → 面板归位表

### 1.1 四包归位

| 包 | 归到面板/基座（`10-panels.md`） | 本路写作用域 | 包内真实内容 | 证据 |
|---|---|---|---|---|
| `ui-sidebar` | `sidebar`（壳） | ✅ 全部 12 个 E0 交互点（30 条命中 = 12 E0 + 16 CSS + 2 测试） | 只有**外壳**：列几何、品牌行、新建会话、全局面板行、`shell.leading` 窗口控件、槽位声明。列表/搜索/设置**都不在这里** | `SidebarRoot.tsx:88-314`；`contract/slots.ts:16-55`；`client/index.ts:76-98` |
| `ui-sidebar-browser` | `base.sidebar-right`（右栏 tab 类型，非 `sidebar` 面板） | ✅ 13 个 E0 交互点（20 条命中 = 13 E0 + 7 CSS） | 内嵌 HTTP(S) 浏览器的**工具条 + 内容载体**（iframe / Electron webview 双实现），注册为 `sidebar.right.pane.tab` 的一个 tab 类型 | `client/index.ts:82-104`；`view/BrowserBody.tsx:79-131` |
| `ui-conversation` | `chat`（会话骨架：header/面包屑/视图 tab/转录滚动/宽度轴）+ `composer`（输入卡/队列/计划/上下文表） | ✅ 65 个 E0 交互点（151 条命中 = 65 E0 + 47 CSS/非交互 + 39 测试） | 会话**驻留骨架** + 完整输入区状态机入口；不含消息体（在 `ui-chat`）与列表（在 `ui-workspace`） | `skeleton/ConversationMainPanel.tsx:13-56`；`skeleton/ConversationContent.tsx:188-203` |
| `ui-session` | 无面板（Session 控制器适配层） | ✅ 0 个交互点 | **纯服务层**：Session 列表选择、`session` scope 源、`running / pendingInteraction / completionUnread` 三态汇总、pending-interaction 域注册。`src/` 内无 JSX、无 DOM | `client/index.ts:275-338,483-538`；`src` 全量 2 个文件 |

> `ui-session` 虽然不是 UI，但它是**会话行状态点/未读的唯二事实源**（另一半在 `ui-workspace`）：
> `running`（正在跑）、`pendingInteraction`（等待人工回答）、`completionUnread`（后台跑完未查看）
> 由 `publishStatus()` 汇总（`client/index.ts:520-538`），并明确「主视图里的会话不产生未读」
> （`:487-489,504`）。myco 会话行的圆点/未读语义应以此为准，**不要自造第四种状态**。

### 1.2 跨包指针清单（本路只指路，不登记 facts）

**A. 会话行（E0，`ui-workspace/src/client/rows/Rows.tsx`）**

| 行为 | 证据 |
|---|---|
| 行容器：`role="treeitem"` + `aria-selected`，外层是 `role="tree"` | `Rows.tsx:595-596`；`AnimatedRows.tsx:173` |
| 单击 = 打开会话 | `Rows.tsx:598` |
| **双击标题 = 改名弹窗**（`e.stopPropagation()`，不触发打开） | `Rows.tsx:639-641` |
| 悬浮 800ms = 悬浮卡；悬浮卡带「复制标题」 | `Rows.tsx:693-701`（`openDelayMs={800}`，`:698-699`） |
| 标题 hover 跑马灯（指针进出） | `Rows.tsx:579,599-600` |
| `⋯` 菜单：4 项（置顶/改名/派生 fork/归档），按 order 100/200/300/400 | `ui-workspace/src/client/index.ts:295-300` |
| 悬浮按钮：归档(100)、置顶(200)，与时间同列右缘 | `ui-workspace/src/client/index.ts:301-303`；`Rows.tsx:687` |
| 悬浮按钮的点击不打开行（在动作条上 `stopPropagation`） | `Rows.tsx:664` |
| 拖拽排序：`draggable`，空白行/归档行不可拖；置顶行只能与置顶行互换 | `Rows.tsx:573,601-624` |
| 归档行：灰化 + 不可打开（点击提示 `toast.archivedNotOpenable`）+ 状态点留白 | `Rows.tsx:592,597,629-635`；`locales.ts:68` |
| 空白新会话行：无时间、无行动词 | `Rows.tsx:645-649` |
| 时间列 = 相对时间 `timeLabel(updatedAt, now)`；有 trailing 状态文案时改显状态且 `aria-hidden` | `Rows.tsx:649-656` |
| 置顶标记：非交互 `role="img"`（pin 用 hover 按钮替换同一右缘格） | `Rows.tsx:411-416,657-659` |

**B. 列表组织（`ui-workspace/src/client/{tree.ts,stores.ts,rows/WorkspaceBrowser.tsx}`）**

| 行为 | 证据 |
|---|---|
| **分组依据 = 工作区，不是时间**。三种模式：`workspace`（默认）/`workspaceTree`/`flat`（单列表）；无归属的进「未分组」桶 | `stores.ts:16-27,85`；`locales.ts:23-26,146-149`；`tree.ts:329-375` |
| **未见实现**：今天/昨天/更早的时间分组（全包无 today/yesterday 分组逻辑） | grep `today\|yesterday` 仅在 locales 无命中；`tree.ts` 全量无时间分桶 |
| 置顶区：置顶行在其分组内前置，且只与置顶行互换 | `tree.ts:270-283`；`pin-order.ts:12-46` |
| 分组折叠：`aria-expanded` 的 `role="treeitem"` 头行 + 本地 `groupExpansion` 持久 | `Rows.tsx:246-248`；`stores.ts:25-26,99-104` |
| 归档筛选：默认隐藏 / 只看归档 / 全显示（`ArchivedFilter`） | `tree.ts:226-255`；`stores.ts:127` |
| **「加载更多」不是虚拟滚动**：每组默认只放 5 条**空闲**行（运行中/空白行不占配额），超出的用「展开/收起」按钮，一次 +5；`hiddenCount <= 5` 时直接 `Infinity` | `WorkspaceBrowser.tsx:54-70,581-602` |
| **未见实现**：虚拟滚动（无 `react-window`/`virtualizer`；行列表只做 `slice` 级配额） | 同上有界配额，无窗口化证据 |
| 空态：`empty.noneArchived` / 无匹配 `search.noMatches` | `WorkspaceBrowser.tsx:818`；`locales.ts:160` |

**C. 搜索（`WorkspaceBrowser.tsx`）**

| 行为 | 证据 |
|---|---|
| 位置：侧栏 section header 内，就地展开（rail 下是独立 36px 图标），`aria-expanded` | `:1216-1237,1325-1336` |
| **防抖 250ms** 后才发 Host 内容搜索；查询截断到 500 UTF-16 码元（不切断代理对） | `:51-53,1099-1128,72-80` |
| Escape 收起搜索并清空展开态；点外部关闭 | `:1250-1252`；`:1082-1091` |
| 清空按钮（`search.clear`） | `:1255-1263` |
| 结果树 `role="tree"`，行 `role="treeitem"`；本地元数据匹配 + Host 结果页合并 | `:790-792`；`:768-770` |
| 结果多时给「还有 N 条」状态（`search.hasMore`） | `:821-822` |
| 选中结果 = `reveal`：先展开所属组/清配额，再 `scrollIntoView({block:'nearest'})`，然后回调确认 | `Rows.tsx:580-584,539`；`WorkspaceBrowser.tsx:1032-1039,355` |
| **未见实现**：搜索结果关键字高亮（无 `<mark>`/高亮片段渲染） | grep 无命中；`SearchResultItem`（`Rows.tsx:484-520`）只渲染标题/时间/取消归档按钮 |

**D. 多会话 / 多标签**

| 事实 | 证据 |
|---|---|
| 主面板**同一时刻只有一个会话**；切会话不重挂载，草稿与 DOM 驻留 | `ui-conversation/src/client/skeleton/ConversationRoot.tsx:1-3`；`InputBar.tsx:9-13,74`；`DefaultConversationViews.tsx:25-30` |
| 会话内的「tab」是**视图 tab**（聊天/轨迹等），不是会话 tab；仅 `tabs.length>1 && !hideChrome` 时出现 | `ConversationSession.tsx:66,140-157` |
| 视图 tab：`role=tablist`/`role=tab`/`aria-selected`，点击 `selectView`；**无关闭键、无方向键、无 roving tabindex**（见 §2 `chat.header.view-tab`） | `ConversationSession.tsx:143-155` |
| 子代理会话层级用**面包屑**下行钻取（末位纯文本，非按钮） | `ConversationSession.tsx:25-43,74-125` |
| 真正的多标签（浏览器/文档预览/终端）在右侧栏：`ui-dockkit` 的 `TabLayout/TabMenu/TabPanel` + `ui-sidebar-right` 的 `tab-registry/tab-inventory/tabs/*` | `ui-dockkit/src/components/TabLayout.tsx`；`ui-sidebar-right/src/client/tab-registry.ts` |

**E. 键盘与焦点序（跨包汇总，供反推移动端无障碍）**

| 事实 | 证据 |
|---|---|
| 侧栏 DOM 序 = 焦点序：`topStrip(darwin)` → 品牌行(toggle) → 新建会话 → 全局面板 `nav` → 浏览区 → 页脚（设置+附加动作） | `SidebarRoot.tsx:215-312` |
| 全局面板行是**逐行独立 tab 停靠点**（无 roving tabindex），无 `role=tab`/`aria-selected`，用 `aria-current="page"` 表选中 | `SidebarRoot.tsx:63-69` |
| 焦点环统一 `outline-offset: -2px`（因为 `.logoRow`/`.iconButton` 自身裁剪 overflow） | `SidebarRoot.module.css:337-342,519-522`；`QueueDock.module.css:79-82,245-248` |
| 快捷键提示通过 `aria-keyshortcuts` 暴露：侧栏开合、新建会话、刷新 | `SidebarRoot.tsx:184,248,264`；`HeaderLeadingControls.tsx:36,47`；`BrowserBody.tsx:85` |
| Escape 只关上下文明细面板；**没有**「Escape 关侧栏/关抽屉」 | `ContextMeter.tsx:80-87` |
| 树/列表行**没有方向键导航**（`role=treeitem` 但无 `onKeyDown`）→ ARIA tree 键盘语义未实现 | `Rows.tsx:586-624` 无 keydown；`AnimatedRows.tsx:176` 只 `onKeyDownCapture` 置位 armed，不做导航 |
| 会话行 `⋯` 触发器**没有** `aria-haspopup`/`aria-expanded` | `Rows.tsx:671-679` |
| 移动端相关：DSH 是鼠标精度，所有按钮 28×28 / 行 32-36px —— 全部低于 44dp（与 Q-03 同一问题，见 §4 Q-SB-08） | `SidebarRoot.module.css:317-331,492-508`；`Browser.module.css:22-35` |

---

## 2. 节点事实表（13 字段）

> 分组顺序：`sidebar.*` → `base-sidebar-right.*` → `chat.*` → `composer.*` → `settings.general.*`。
> 每个节点写满 13 字段；`反证` 必写；代码里查不到的行为一律写「**未见实现**」，不写「应该有」。
> 标注「组节点」的条目是共享容器/滚动盒（没有独立原子目标），只用于承载几何、滚动与禁用态事实。

### 2.1 `sidebar`（侧栏壳）

#### `sidebar.shell.column`（组节点 · 列容器与滚动条可见性）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.shell.column`。所属 `sidebar` 面板 → 壳 → 列容器。父：`AppFrame` 的 `.sidebarCol` 网格项（`AppFrame.tsx:280-282`）。出现条件：始终（含无会话态）。每屏 1 个。 |
| 2 | 网格与定位 | 宽 = `cols.sidebar`：宽屏 `clamp(drag,264,420)`、默认 280、折叠 56 或 0（`columns.ts:13-23,52-58`）；高 100%。内边距宽屏 `6px 12px`、rail `18px 10px 6px`（`SidebarRoot.module.css:9-15,34-36`）。纵向 flex：`topStrip?(darwin)` → `logoRow` → `newSession` → `nav.panelList` → `regionArea(flex:1)` → `footArea`（`SidebarRoot.tsx:215-312`）。底色 `--dsw-specific-sidebar-fill`，右边框画在外层 `.sidebarCol`（`AppFrame.module.css:58-63`）。 |
| 3 | 命中区 | 容器本身无点击。唯一命中相关：滚动条槽位由 `scrollbar-gutter: stable` 预留（`ConversationRoot.module.css:410-416` 同理，列表侧在 `ui-workspace`），故滚动条显隐不改行宽 —— 见字段 6。 |
| 4 | 手势 | **无抽屉手势**（无 touchstart/pointerdown 拉起、无边缘滑动、无返回键拦截；`ui-sidebar/src` grep `Escape\|scrim` 均为 0）。仅指针进出参与滚动条：`pointerenter`→取消隐藏计时并置 `pointerInside`；`pointerleave`→起 2000ms 计时（`SidebarRoot.tsx:207-211,136-146`）。因为 ui-settings 的全屏面板是本列的 `fixed` **后代**，`pointerleave` 会漏报，故另开 document `pointermove` 用列的矩形判进出（`SidebarRoot.tsx:147-170`）。 |
| 5 | 行为 | 无导航。唯一副作用：给本列绑/解绑 `--dsh-scrollbar-thumb(-hover) → transparent`（`.quietBars`，`SidebarRoot.module.css:128-131`），使嵌套滚动区（会话列表）的滑块在指针不在列内时透明。幂等；无需撤销。 |
| 6 | 视觉状态 | quiet（指针不在列内且已离开 ≥2s）：全列滚动条透明但**槽位保留**；active（指针在列内 或 离开未满 2s）：恢复 l2 色对。列背景：macOS 半透明 vibrancy + 双层蓝紫渐变（`AppFrame.module.css:110-139`），其它平台实底；macOS `prefers-reduced-transparency: reduce` 换成 90% 实色（`:145-150`）。无 pressed/selected/disabled。 |
| 7 | 布局占位与展开形态 | **参与布局流**（grid track，占位=列宽），不是浮层。不属于 G-03 三形态任何一个（本节点无展开体）；它的折叠由 `base.layout` 的轨道宽度承担。 |
| 8 | 滚动语义 | 列自身不滚。`regionArea` 是 `flex:1; min-height:0; overflow:hidden`（`SidebarRoot.module.css:555-570`）的**滚动委托盒**，真正滚动条在占用者（会话列表，`ui-workspace`）。本包不设 `overscroll-behavior` → 到边行为由清单方决定（G-04 不满足于此处）。 |
| 9 | 动效 | 列体本身不动；折叠时内容冻结在展开宽度并 150ms 淡出（`.fading > *`），轨道同时被裁切（`SidebarRoot.module.css:133-138`）；展开时 wide-only 内容 200ms 淡入（`wide-in`，`:140-147`）。`prefers-reduced-motion` 全部禁用（`:602-612`）。可中断：再次 toggle 会重启 settle 计时（`SidebarRoot.tsx:106-111`）。 |
| 10 | 边界态 | 窄屏（<1024）展开是**压缩中心列**而非覆盖，中心列最低 0（`columns.ts:58`、`AppFrame.tsx:160-161`）；Windows caption 模式折叠后 `panelList/regionArea/footArea` 直接 `display:none`（`SidebarRoot.module.css:82-86`）→ 折叠后列表完全不可达；大字号只影响内部行高，列宽不变；多语言只影响 wordmark 与面板名；横屏无特殊处理。 |
| 11 | 无障碍 | 无 role（纯布局容器）。列内 DOM 顺序 = 焦点顺序。无 landmark 名（唯一具名 landmark 是 `nav aria-label="全局面板"`，`SidebarRoot.tsx:280`）。不设焦点陷阱 → Tab 可自由进出侧栏与会话区。 |
| 12 | 依据 | `E0` `SidebarRoot.tsx:9-18,30-39,104-170,200-212,215-312`；`E0` `SidebarRoot.module.css:9-36,82-86,120-174,555-570,602-612`；`E0` `AppFrame.tsx:160-174,265-266,280-282`；`E0` `AppFrame.module.css:58-63,110-150,223-233`；`E0` `columns.ts:11-29,52-58`；`E0` grep（`ui-sidebar/src` 无 scrim/Escape/抽屉手势）。 |
| 13 | 反证 + 验收 | **反证**：若滚动条用 `display:none` 隐藏而非把 thumb 重绑成 `transparent`，`scrollbar-gutter: stable` 的预留失效 → 指针每次进出列，会话列表整列横移 8px，用户正在读的行跟着跳（与 G-02 同类事故）。若把「指针离开即隐藏」改成瞬时，指针只是路过列边缘去会话区时滚动条会闪一下（`SidebarRoot.tsx:33-39` 明写这条）。**验收**：`SB-01` 指针进出侧栏 5 次，会话列表行左缘 x 像素恒定（真机录屏 + 逐帧比对）；`SB-01b` 指针离开 1s 内返回，滚动条不得先消失再出现（真机录屏）。 |

#### `sidebar.shell.toggle`（折叠/展开开关，两个安装位）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.shell.toggle`。所属 `sidebar` → 壳 → 折叠开关。两个安装位同一行为：①窗口/列内按钮（非 darwin 平台，`SidebarRoot.tsx:178-197`）；②macOS 全隐藏侧栏时 frame `shell.leading` 席按钮（`HeaderLeadingControls.tsx:31-41`）。出现次数：每屏 1 个（darwin 折叠时位次由列内迁到窗口 chrome，不并存——`AppFrame.tsx:255-256,295-299`）。 |
| 2 | 网格与定位 | 列内：28×28（`SidebarRoot.module.css:317-331`），rail 态 36×36 圆角 md（`:344-349`）；位于 `logoRow`（高 60px，rail 36px）行尾（`:219-240`）。macOS：先占 `topStrip`（高 52px、负 margin 抵消列 padding、右对齐），全屏时左对齐（`:181-199`）。Windows caption 模式：`position:fixed; top: calc((titlebar-28)/2); left:12px; z-index:30`（`:45-51`）。window-chrome 席：`leadingSeat` 绝对定位 `top:11px; left:88px; z-index:15`（`AppFrame.module.css:207-215`）。 |
| 3 | 命中区 | 视觉 28×28 = 命中（无扩边），macOS 席同为 28×28。**低于 44dp**（Q-03/Q-SB-08）。与相邻新建会话按钮间距 8px（`HeaderLeadingControls.module.css:6-10`）。 |
| 4 | 手势 | 单击。无长按/双击/滑动。与窗口拖拽的仲裁：按钮在 `data-window-drag` 行内，靠全局「可交互元素不参与拖拽」规则从拖拽面里**减去自己**（`SidebarRoot.tsx:213-220` 注释；`AppFrame.module.css:195-200`）。 |
| 5 | 行为 | `toggleSidebar()` → `ctx.layout.toggleSidebar()`（`ui-sidebar/src/client/index.ts:69,124-125`）。分发规则：视口 <1024 时只翻 `narrowExpanded` 覆盖位（宽度偏好不动）；≥1024 时在 `0` 与 `SIDEBAR_DEFAULT=280` 之间切（`ui-layout/src/client/stores.ts:106-112`）。**注意：宽屏关闭会忘记用户拖过的宽度**（`stores.ts:69-76` 注释：preference IS the width，关闭即 0，重开回默认 280）。幂等，可撤销（再点一次）。 |
| 6 | 视觉状态 | `default`：secondary 图标、透明底。`hover`：`interactive-bg-hover` 底（`SidebarRoot.module.css:333-335`）。`focus-visible`：`outline-offset:-2px` 的实心环（`:337-342`）——因为 `.logoRow` 自带 `overflow:hidden`，外扩焦点环会被裁掉。rail 态特色：静息显示鲸鱼标（`.railMark`），hover 才换成面板图标（`.collapsed .toggle:hover .panelIcon`，`:351-364`）。`disabled`：未见实现。 |
| 7 | 布局占位与展开形态 | 参与布局流（`flex:none`），28×28 / 36×36。不展开。折叠动作会改变**父级 grid 轨道**宽度，故本节点是「三形态之外」的布局驱动型开关。 |
| 8 | 滚动语义 | 不滚，不参与滚动仲裁（固定定位三种安装位均在滚动容器之外）。 |
| 9 | 动效 | 自身无动画；它引发的轨道动画见 `sidebar.shell.column` 字段 9。rail 态 hover 换图标是 `display` 直切（`:354-364`），无过渡。 |
| 10 | 边界态 | 刷新直入折叠态：不播 rail 入场动画（`everWide` 标记，`SidebarRoot.tsx:124-127`）；`data-fullscreen` 时 macOS 顶部条改为左对齐（`SidebarRoot.module.css:195-199`）；Windows caption 模式与其它平台坐标完全不同（字段 2）；大字号不影响（图形按钮）。 |
| 11 | 无障碍 | 原生 `<button type=button>`；`aria-label` 随状态切换「打开侧边栏 / 收起侧边栏」（`SidebarRoot.tsx:103,183`）；`aria-keyshortcuts` = `sidebar.left.toggle` 的真实按键（desktop `primary+B`、web `primary+alt+B`，`ui-layout/src/client/index.ts:191-201`）；Tooltip 带快捷键可视提示（`SidebarRoot.tsx:179`）。焦点顺序：列内位 = 品牌行内、新建会话之前；chrome 位 = 窗口最左。 |
| 12 | 依据 | `E0` `SidebarRoot.tsx:101-103,112-113,178-197,203-212`；`E0` `SidebarRoot.module.css:39-51,103-118,317-349,351-380`；`E0` `HeaderLeadingControls.tsx:26-41`；`E0` `ui-layout/src/client/index.ts:191-201`；`E0` `stores.ts:69-76,106-112,113-122`；`E0` `AppFrame.tsx:255-256,295-301`；`E1` 注释声称「figma sidebar-hover flow」（`SidebarRoot.module.css:351-353`）。 |
| 13 | 反证 + 验收 | **反证**：若折叠开关只在列内、不随 macOS 全隐藏侧栏迁到窗口 chrome，折叠后**再没有入口能打开侧栏**（列宽 0，无 rail），应用等于自锁；若 rail 态不把静息图标换成 hover 图标，用户看到的是品牌鲸鱼标而不是「展开」暗示，会找不到开关（`SidebarRoot.module.css:351-364` 就是为这件事）。若关闭时不冻结宽度，一关就永久丢用户拖出来的宽度（`stores.ts:69-76` 明确取舍：关闭即忘记）。**验收**：`SB-02` macOS 折叠后窗口左上角出现开关且可展开（真机截图）；`SB-02b` 宽 1280px 拖动到 360px→关闭→再打开，宽度回到 280（真机手势 + 读数）；`SB-02c` 焦点环四边不被裁（截图比对）。 |

#### `sidebar.shell.new-session`（新建会话，三个安装位）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.shell.new-session`。所属 `sidebar` → 壳 → 新建会话。三个安装位同一动作 `startSession()`：①展开态**品牌行按钮**（仅非 darwin；darwin 该处是窗口拖拽面，不渲染 button，`SidebarRoot.tsx:216-254`）；②常驻「新建会话」条（`SidebarRoot.tsx:258-277`）；③macOS 全隐藏时的窗口 chrome 席（`HeaderLeadingControls.tsx:42-52`）。每屏可见 2 个（展开态 ①+②；darwin 折叠态只剩 ③）。 |
| 2 | 网格与定位 | ②：高 38px、宽 100%、`padding:8px 12px`、`margin:0 2px 12px`、圆角 md、0.5px 边框（`SidebarRoot.module.css:396-417`）；rail 态 36×36 圆形控件、无边框（`:442-451`）；Windows caption 态固定 `left:48px`（`:92-101`）。①：品牌行内 `flex:1`，`cursor:pointer`、无 hover 底（`:246-257`）。③：窗口 chrome 席内 28×28（`HeaderLeadingControls.module.css:12-25`）。 |
| 3 | 命中区 | ②整条 38px（≥44dp 不满足，宽足够、高不足）；rail 态 36×36；①=品牌标记+名称整块（宽度自适应、命中随内容）；③28×28。②与①纵向相邻（logoRow 60px 高、newSession 上边距 0-8px）。 |
| 4 | 手势 | 单击。②的按钮内含 `container-type:inline-size` 的标签遮罩，hover/focus 时标签让位给快捷键（见字段 6）。无长按/右键。 |
| 5 | 行为 | `startSession()` → `ctx.get('uiWorkspace').startSession(workspaceId?)`（`ui-sidebar/src/client/index.ts:65-68`）。语义（`contract/slots.ts:118-124`）：有工作区则复用/新建其空白会话并打开；无工作区则继承当前会话的工作区 → 最近工作区 → 都没有则进入「新会话纯视图」。幂等性：连点会产生/复用同一空白会话（由 ui-workspace 决定，本包不可见）。无撤销。 |
| 6 | 视觉状态 | ②`default`：`--dsw-alias-button-elevated-fill` 底 + primary 文字；`hover`：`button-floating-hover`；`focus-visible`：标签让位、右侧快捷键淡入（`:468-480`）；`disabled` 未见实现。①无 hover 底（`cursor:pointer` 是唯一提示，`:242-257` 注释明说「no hover chrome on the mark」）。macOS ②用白色半透明 wash 而非主题 elevated 填充（`:423-440`）。 |
| 7 | 布局占位与展开形态 | 参与布局流，`flex:none`；②是固定 38px 行（rail 36px），不展开、不收缩；①参与 logoRow 的 `flex:1` 分配。 |
| 8 | 滚动语义 | 不滚。②固定列宽内 `max-width:200px` 的标签 + 遮罩渐隐（`:457-468`），不换行。 |
| 9 | 动效 | ②的快捷键 `opacity:0→1`（100ms 级，`:472-480`）；标签遮罩 `mask-image` 随 hover 出现（`:468`）；rail 态随列折叠入场（见 column 字段 9）。 |
| 10 | 边界态 | 快捷键未注册时（`newShortcut === undefined`）②不渲染快捷键块、也不挂遮罩（`SidebarRoot.tsx:273`）；窄列时标签被 `max-width:0` 收成 0（`:463-465`）；rail 态只留图标；多语言长文案靠 ellipsis 收；大字号：标签 14px 固定，不随 `--dsh-content-font-size` 变。 |
| 11 | 无障碍 | 原生 button；`aria-label="新建会话"`；`aria-keyshortcuts` = `session.new`（`primary+N`，web `primary+alt+N`，`ui-workspace/src/client/shortcuts.ts:88`）；Tooltip 带快捷键。焦点顺序：②在品牌行之后、全局面板 `nav` 之前。 |
| 12 | 依据 | `E0` `SidebarRoot.tsx:101-102,216-277`；`E0` `SidebarRoot.module.css:92-101,246-257,396-480`；`E0` `HeaderLeadingControls.tsx:42-52`；`E0` `contract/slots.ts:117-130`；`E0` `ui-sidebar/src/client/index.ts:65-68`；`E0` `ui-workspace/src/client/shortcuts.ts:88`。 |
| 13 | 反证 + 验收 | **反证**：若②在 rail 态仍保留文字标签，56px 轨道装不下 → 文字被裁成半个字，或在冻结宽度淡出期间引起 reflow（`SidebarRoot.module.css:6-7` 明写 rail 布局只在淡出落定后应用）。若①在 darwin 也做成按钮，它会从窗口拖拽区里「减掉自己」，导致品牌行一段无法拖窗（`SidebarRoot.tsx:216-220`）。**验收**：`SB-03` 展开态 1280px 与 rail 态 800px 各截一张，新建会话控件在两个状态下都不出现文字裁切/换行（截图比对）；`SB-03b` 点新建会话 3 次，会话列表出现且只保留一个空白行（真机 + 列表截图）；`SB-03c` macOS 品牌行可拖窗（真机手势）。 |

#### `sidebar.shell.panel-row`（全局面板行）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.shell.panel-row`。所属 `sidebar` → 壳 → 全局面板导航 `nav`（`SidebarRoot.tsx:279-293`）。出现条件：`sidebar.panellist` 有别包注册（`panels.length > 0`）；每个注册项 1 行，按 `order` 升序、同序保留注册序（`ui-sidebar/src/client/index.ts:49-61`）。每屏 0..N 行。 |
| 2 | 网格与定位 | `nav` 纵向 `gap:4px`、`margin-bottom:8px`（`:482-488`）；行：`min-height:36px`、`padding:7px 8px`、`margin:0 2px`、圆角 md、图标 16px(宽)/18px(rail) + 8px gap + 标题（`SidebarRoot.tsx:70-77`，`SidebarRoot.module.css:490-508`）。rail 态 36×36 居中、gap 12px（`:538-550`）。 |
| 3 | 命中区 | 整行 36px 高 × 列宽（`width:100%`，`text-align:left`）；rail 36×36。行间 4px。**低于 44dp**。 |
| 4 | 手势 | 单击。无长按/右键/拖拽（排序靠注册 `order`，非用户拖）。与父 nav 无滚动仲裁（nav 不滚）。 |
| 5 | 行为 | `selectPanel(id)` → 记账（`plugins`/`schedules` 上抛埋点）→ `ctx.layout.selectPanel(id)`（`ui-sidebar/src/client/index.ts:70-73`）。选中态与「当前主面板」由 `usePanelInfo(info => info.activePanelId === id)` 逐行订阅（`SidebarRoot.tsx:60`）。幂等。无撤销（上一条状态不保存）。 |
| 6 | 视觉状态 | `default`：primary 文字、透明底。`hover`：`interactive-bg-hover`。`active`（`aria-current="page"`）：**同一个 `interactive-bg-hover` 底色**（`:514-517`）—— 也就是说**选中态与 hover 态视觉相同**，仅靠 `aria-current` 区分（见反证）。`focus-visible`：`-2px` 内收环（`:519-522`）。`disabled` 未见实现。 |
| 7 | 布局占位与展开形态 | 参与布局流，每行固定 36px；不展开、不折叠（无子项）。 |
| 8 | 滚动语义 | 所在 `nav` 不滚（`flex:none`）；行数超出列高时会被 `regionArea` 之外的剩余空间挤压，本包**未见**「面板行溢出可滚」实现。 |
| 9 | 动效 | 无行级动画（只有 hover 底色瞬切）。rail/宽屏切换时整组随列入场（column 字段 9）。 |
| 10 | 边界态 | 面板数为 0 时整个 `nav` 不渲染（`SidebarRoot.tsx:279`）；`label` 缺省回退成 id（`ui-sidebar/src/client/index.ts:53`）；标题超长 `ellipsis + nowrap`（`SidebarRoot.module.css:531-536`）；rail 态只有图标 + Tooltip（`:62` 的 Tooltip 在 wide 时 `disabled`）。 |
| 11 | 无障碍 | `nav aria-label="全局面板"`（`SidebarRoot.tsx:280`）；行是原生 button，`aria-label`=面板名、`aria-current="page"`（单选语义，非 `aria-selected`）；**不是** `role=tab`，也没有 roving tabindex —— 每行都是独立 tab 停靠点（`SidebarRoot.tsx:63-69`）。 |
| 12 | 依据 | `E0` `SidebarRoot.tsx:51-81,279-293`；`E0` `SidebarRoot.module.css:482-550`；`E0` `ui-sidebar/src/client/index.ts:49-61,70-73`；`E1` 注释声称对齐 figma（`SidebarRoot.module.css:490-491`）。 |
| 13 | 反证 + 验收 | **反证**：选中态与 hover 用同一个底色（`SidebarRoot.module.css:510-517`）时，指针停在哪一行，用户就分不清「我在哪」和「我是谁」——鼠标移开后又失去唯一线索；这是 myco 必须修的点（Q-SB-03）。若面板行改成 `role=tab` 却不给方向键，屏幕阅读器会承诺一套键盘语义而实际不响（`role=tablist` 的 ARIA 契约），比 now-`aria-current` 更糟。**验收**：`SB-04` 截「hover 非选中的行 + 选中的行」同屏对比，两者必须可区分（截图比对，规格待 P2 定）；`SB-04b` Tab 遍历面板行，焦点环始终完整可见（真机录屏）。 |

### 2.2 `base-sidebar-right`（右栏 browser tab 页）

> `ui-sidebar-browser` 注册的是**右栏 tab 类型**（`sidebar.right.pane.tab` key=`browser`，`client/index.ts:82-104`），
> 不是 `sidebar` 面板。`base.sidebar-right` 在 `10-panels.md` 里是 P0 基座；本组节点归它。

#### `base-sidebar-right.browser.history-nav`（后退/前进）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.browser.history-nav`。browser tab 工具条第一组（`BrowserBody.tsx:81-83`）。恒出现 2 个按钮（仅 disabled 态变），tab 未挂载页面时也渲染。 |
| 2 | 网格与定位 | 工具条：高 38px、`padding:5px 6px`、`gap:4px`、下边框 0.5px（`Browser.module.css:11-20`）；按钮 28×28、圆角 sm、`flex:0 0 auto`；其后紧跟地址框（`flex:1 1 auto; min-width:0`，`:52-56`）。 |
| 3 | 命中区 | 28×28 视觉=命中（无扩边）。**<44dp**（Q-SB-08）。两键间距 4px，低于 Q-03 建议的 8dp 隔离带 → 误触风险（见反证）。 |
| 4 | 手势 | 单击。无长按（**未见实现**「长按历史列表」，Chrome 的经典手势未做）。 |
| 5 | 行为 | back → `goBack(tab.id)`；forward → `goForward(tab.id)`（`BrowserBody.tsx:82-83`）。可用性完全由载体报告的 `frame.canGoBack / frame.canGoForward` 决定（`browser/BrowserFrame.ts`）。不幂等（历史指针移动）；可撤销=点反向键，但历史被新导航截断后不可恢复。 |
| 6 | 视觉状态 | `default`：secondary 图标、透明底。`hover:not(:disabled)`：primary 图标 + `interactive-bg-hover` 底（`.tool:hover:not(:disabled)`，`:37-40`）。`disabled`：`label-quaternary` + `cursor:default`（`:42-45`）。`pressed/focus-visible`：**未见实现**（本包未定义 `:focus-visible`，走浏览器默认焦点环）。 |
| 7 | 布局占位与展开形态 | 参与布局流，固定 28×28，不展开、不收缩（`flex:0 0 auto`）。 |
| 8 | 滚动语义 | 工具条不滚。页面滚动在 `.viewport`（iframe/webview）内，`.content/.viewport` 均 `min-height:0` 不自滚（`Browser.module.css:127-140`）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 首帧（`mountEpoch===0`）两键 disabled；加载失败仍有历史可用（error 只影响内容区横幅）；`address==='unknown'` 不影响历史；离线/多语言/大字号只影响地址栏与横幅，不改变按钮几何；横屏不变。 |
| 11 | 无障碍 | 原生 button + `aria-label` + `title`（同文案「后退/前进」）；无 `aria-keyshortcuts`（前进/后退的快捷键**未见实现**，只有刷新有）；键盘可达；无 `aria-disabled`（用原生 disabled）。 |
| 12 | 依据 | `E0` `BrowserBody.tsx:81-83`；`E0` `Browser.module.css:11-45,52-56,127-140`；`E0` `browser/BrowserFrame.ts`（`canGoBack/canGoForward` 字段）；`E0` `locales.ts:9-10,42-43`。 |
| 13 | 反证 + 验收 | **反证**：若用「历史数组非空」而不是载体的 `canGoBack` 决定可用性，用户点后退会静默无反应（页面没动、也没有任何反馈），比置灰更糟；若两键只在 hover 才显示，工具条宽度会跳变、地址栏横移（G-02 同类）。若沿用 28×28 + 4px 间距上移动端，拇指会误触到前进（Q-SB-08）。**验收**：`BRW-01` 空 tab 首屏两键 disabled 且地址栏左缘不动（截图比对）；`BRW-02` 移动端 44dp 版命中区下，两键之间误触率（真机重复点击 20 次，0 次串键）。 |

#### `base-sidebar-right.browser.reload`（刷新）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.browser.reload`。browser 工具条第 3 键（`BrowserBody.tsx:84-86`）。恒出现 1 个。 |
| 2 | 网格与定位 | 同工具条节奏，28×28、与相邻键 gap 4px；`Tooltip` 包一层、`side="bottom"`、`delayMs:500`（`BrowserBody.tsx:84`）。 |
| 3 | 命中区 | 28×28。**<44dp**。 |
| 4 | 手势 | 单击；同时注册为命令层命令（`tab.actions.bindCommands({ refresh })`，`:50`），因此快捷键/菜单路径与点击走同一实现。 |
| 5 | 行为 | `reload(tab.id)`；另由 `tab.refreshShortcut` 暴露按键（`aria-keyshortcuts`，`:85`）。disabled = `target === undefined \|\| mountEpoch === 0`（还没挂出载体时刷新无意义）。幂等（重复刷新只叠加重载）；不可撤销（会丢页面内未提交状态）。 |
| 6 | 视觉状态 | 同 `.tool`：default secondary / hover primary+底 / disabled quaternary+cursor:default。加载中**不改变**视觉（无 spinner，未见实现）。 |
| 7 | 布局占位与展开形态 | 固定 28×28，不展开。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 首帧 disabled（`mountEpoch===0`）；页面加载失败仍可刷新重试（`load.failed` 文案就建议刷新）；离线/超时由载体报 error，按钮保持可用；多语言 Tooltip 文案随 locale。 |
| 11 | 无障碍 | 原生 button；`aria-label="刷新"`；`aria-keyshortcuts`=载体给的刷新键；Tooltip 显示快捷键；原生 disabled。 |
| 12 | 依据 | `E0` `BrowserBody.tsx:50,84-86`；`E0` `Browser.module.css:22-45`；`E0` `locales.ts:10,43`。 |
| 13 | 反证 + 验收 | **反证**：若在 `loading` 期间禁用刷新，用户遇到卡死的加载就没有任何中断手段（只能关 tab）；所以 DSH 只按「载体是否挂出」判 disabled 而不是按 loading 判。若首帧就允许刷新，Electron 侧会在 viewport 还没建立时触发一次导航，产生孤儿 frame（`mountEpoch` 就是为这条守卫而存在）。**验收**：`BRW-03` 首帧前刷新键 disabled，挂载完成后自动可用（真机 + DOM 断言）；`BRW-04` 加载卡住时点刷新能重新发起加载（真机）。 |

#### `base-sidebar-right.browser.address-field`（地址输入）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.browser.address-field`。工具条中央的受控 input（`BrowserBody.tsx:87-96`）。恒出现 1 个；无 tab 时也在（草稿来自 tab 参数）。 |
| 2 | 网格与定位 | `.addressBox` `flex:1 1 auto; min-width:0`（`Browser.module.css:52-56`）；input 高 28px、`padding:0 34px 0 9px`（右侧 34px 留给「前往」键）、`font: var(--dsw-font-xxs-12)`、圆角 sm、0.5px 边框（`:58-68`）。 |
| 3 | 命中区 | 输入框整框 28px 高（<44dp）；右侧 34px 与「前往」键重叠区由「前往」键接管（`position:absolute; right:0`）。 |
| 4 | 手势 | 点击聚焦；Enter = 提交（在 `form onSubmit` 内，`:77,81`）。无长按/右键自定义。 |
| 5 | 行为 | 编辑写入本地 draft；提交 → `loadUrl(tab.id, draft)`（`:77`）。草稿带**版本号**：`edit.revision === state.addressRevision` 才算用户编辑，否则跟随 `target.url`（`useBrowserDraft`，`:40-44`）→ 页面自导航不会覆盖正在输入的草稿。校验（空/非法/协议/凭证/应用自身）在 `browser/url.ts`，失败经 `addressFailure` 渲染 `role="alert"`（`:115`）。幂等：同一 URL 再提交只重载。 |
| 6 | 视觉状态 | `default`：`bg-layer-1` 底 + primary 字。`unknown` 态（载体读不到新 URL）：`addressUnknown` = 灰底 `bg-layer-2` + tertiary 字，右侧显示「URL 已变化」小字（`:83-89,96`）；**聚焦时**恢复 `bg-layer-1` + primary，并隐藏那行小字（`:91-94,108-111`）。`focus`：`outline:1px` 业务蓝、`offset:-1px`（`:113-116`）。`disabled` 未见实现。 |
| 7 | 布局占位与展开形态 | 参与布局流，占据工具条剩余宽度；不展开（无下拉建议列表 —— **未见实现** 历史/补全下拉）。 |
| 8 | 滚动语义 | input 自身水平滚动（原生）；不参与工具条滚动（工具条不滚）。 |
| 9 | 动效 | 无过渡声明（focus 与 unknown 切换都是即时换色）。 |
| 10 | 边界态 | 超长 URL：input 原生水平滚动，不换行；`unknown` 时右侧提示可能挤压文字（`padding-right:34px` 只留给按键）；空串提交 → `error.empty`；含凭证 → `error.credentials`；应用自身 origin → `error.application-origin`（`locales.ts:21-25`）；多语言 placeholder「输入 HTTP(S) 地址」；大字号：`font` token 固定 xx-12，不随内容字号变。 |
| 11 | 无障碍 | `aria-label` = placeholder 文案（`BrowserBody.tsx:91`）；`spellCheck={false}`；`role=alert` 承载校验失败（`:115`）；无 `aria-describedby` 关联错误文本（**未见实现**）；焦点顺序在三个导航键之后、外部打开键之前。 |
| 12 | 依据 | `E0` `BrowserBody.tsx:40-44,56-60,72-77,87-96,115`；`E0` `Browser.module.css:52-116`；`E0` `browser/url.ts`（校验契约）；`E0` `locales.ts:17,21-25,28`。 |
| 13 | 反证 + 验收 | **反证**：若不做 `addressRevision` 版本校验，用户正在改地址时页面自己跳转（重定向/SPA 路由）会把输入框内容覆盖成新 URL → 打到一半的地址被吞（`BrowserBody.tsx:40-44` 就是为这条）。若 `unknown` 态不换灰底+提示，用户会以为自己输入的地址就是当前页面（实际已经跳走），后续所有判断都错。**验收**：`BRW-05` 页面自跳转时正在编辑的草稿不被覆盖（单测 + 真机）；`BRW-06` unknown 态截图：灰底 + 「URL 已变化」，聚焦后恢复（截图比对）。 |

#### `base-sidebar-right.browser.address-go`（前往）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.browser.address-go`。地址框内右下角的 `type=submit` 按钮（`BrowserBody.tsx:97`）。恒在 DOM，**仅地址框 `:focus-within` 时可见可点**（`Browser.module.css:70-81`）。每屏 1 个。 |
| 2 | 网格与定位 | 绝对定位在 `.addressBox` 右缘：28×28、`top:0; right:0`；地址 input 的 `padding-right:34px` 为它预留了位置（`:62`）。 |
| 3 | 命中区 | 28×28，与地址输入框重叠（叠在输入框右内边距区）。**<44dp**；与输入框的可点区重叠 → 点偏 1-2px 会变成聚焦输入框（可接受，见反证）。 |
| 4 | 手势 | 单击（提交 form）。Enter 在输入框里也能提交，两者等价。 |
| 5 | 行为 | 触发 `form onSubmit` → `loadUrl(tab.id, draft)`（`BrowserBody.tsx:77,81,97`）。无独立 disabled（是否可用取决于草稿是否合法，由提交时校验）。幂等。 |
| 6 | 视觉状态 | 隐藏态：`visibility:hidden; opacity:0`（**不是** `display:none`，布局与命中区位置都不变）；地址框聚焦：`visibility:visible; opacity:1`（`:70-81`）。无 hover 声明（继承 `.tool` 的 hover 底）。 |
| 7 | 布局占位与展开形态 | 绝对定位，不占布局流；显隐不改变地址框宽度（这正是不能用 `display:none` 的原因）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | `visibility`/`opacity` 直切，无过渡时长声明（`:70-81`）。 |
| 10 | 边界态 | 键盘聚焦输入框时同样出现（`:focus-within` 覆盖键盘）；失焦即隐藏；触摸设备上点击输入框后软键盘弹出，按钮是否仍可见取决于焦点是否留在框内（**未见实现** 移动端适配）。 |
| 11 | 无障碍 | 原生 `button type=submit` + `aria-label="前往"` + `title`；**焦点顺序陷阱**：隐藏时用 `visibility:hidden` 会自动移出 tab 序（正确），但 Tab 到它时地址框仍处于 `:focus-within` → 它会先出现再被聚焦（合理）。 |
| 12 | 依据 | `E0` `BrowserBody.tsx:77,81,97`；`E0` `Browser.module.css:58-81`；`E0` `locales.ts:12,45`。 |
| 13 | 反证 + 验收 | **反证**：若一直显示，地址栏多一个常驻图标、挤掉 URL 可读长度（34px），而「前往」是低频动作；若用 `display:none` 切换，显隐会改变 `:focus-within` 时的布局（绝对定位下不明显，但会把按钮从无障碍树里整个摘掉，键盘用户 Tab 不到）。**验收**：`BRW-07` 点进地址框，前往键出现且地址文字不被遮挡；Tab 能到达该键（真机 + 截图）。 |

#### `base-sidebar-right.browser.external`（系统浏览器打开）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.browser.external`。工具条最后一组第 1 键（`BrowserBody.tsx:99-101`）。恒出现 1 个，disabled 当 `externalUrl === undefined`（即 `address === 'unknown'`，`:72-73,99`）。 |
| 2 | 网格与定位 | 28×28，位于地址框之后、沙箱键之前；`flex:0 0 auto`（`Browser.module.css:22-35`）。 |
| 3 | 命中区 | 28×28。**<44dp**。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `window.open(externalUrl, '_blank', 'noopener,noreferrer')`（`BrowserBody.tsx:100`）。不改变内嵌页状态（不导航、不丢表单）。幂等（可重复开）。不可撤销（新窗口已开）。 |
| 6 | 视觉状态 | default secondary / hover primary+底；disabled quaternary + cursor:default（未知地址时）。 |
| 7 | 布局占位与展开形态 | 固定 28×28，不展开。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | `unknown` 时 disabled（因为拿不到可信 URL）；`load.failed` 时**仍可用**（这正是失败兜底路径，`locales.ts:26` 文案建议「在系统浏览器中打开」）；弹窗被浏览器拦截时无回执（**未见实现** 拦截提示）。 |
| 11 | 无障碍 | 原生 button；`aria-label="在系统浏览器中打开"` + `title`；无 `aria-keyshortcuts`（**未见实现** 快捷键）。 |
| 12 | 依据 | `E0` `BrowserBody.tsx:72-73,99-101`；`E0` `Browser.module.css:22-45`；`E0` `locales.ts:13,26,46`。 |
| 13 | 反证 + 验收 | **反证**：若 `unknown` 态仍拿 `target.url` 去打开，会把一个**已经过期的地址**交给系统浏览器（页面已跳走，`target` 是旧值）——用户看到的内容和内嵌页不一致，比不提供入口更糟；若不传 `noopener,noreferrer`，被打开页能通过 `window.opener` 反过来操纵宿主应用。**验收**：`BRW-08` unknown 态该键 disabled（真机截图）；`BRW-09` 打开的新窗口 `window.opener === null`（真机 console 断言）。 |

#### `base-sidebar-right.browser.sandbox`（沙箱开关）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.browser.sandbox`。工具条最后一组第 2 键（`BrowserBody.tsx:102-109`）。**条件出现**：仅当载体上报 `frame.sandboxEnabled !== undefined`（载体支持沙箱策略）才渲染。每屏 0..1 个。 |
| 2 | 网格与定位 | 28×28；图标是自绘盾牌 svg 15×15（`BrowserBody.tsx:23-32`）。关闭沙箱时加 `.sandboxOff`：红色 + 10% 红底（`Browser.module.css:47-50`）。 |
| 3 | 命中区 | 28×28。**<44dp**；且是危险动作，误触代价高（见反证）。 |
| 4 | 手势 | 单击**直接切换**（无二次确认 —— 见反证/Q-SB-09）。 |
| 5 | 行为 | `setSandbox(tab.id, !sandboxed)`（`:108`）。关闭后页面下方常驻 `role="status"` 横幅说明风险（`:111`），文案列出：可导航顶层应用、下载、模态对话框、输入锁定（`locales.ts:16`）。幂等（再点恢复）；不幂等的是**已加载页面的能力**（重载后才完全生效，**未见实现** 自动重载）。 |
| 6 | 视觉状态 | `sandboxed=true`：普通 secondary 图标 + 盾牌打勾。`sandboxed=false`：红色图标 + 红底 tint + 下方警告横幅。`aria-pressed={!sandboxed}`（`:107`）→ **按下态 = 危险态**。hover：继承 `.tool`。 |
| 7 | 布局占位与展开形态 | 按钮固定 28×28；警告横幅是内容区上方的**布局行**（`flex:0 0 auto`，`:224-230`），会把页面内容整体下推（不属于 G-03 三形态，是常驻状态条）。 |
| 8 | 滚动语义 | 按钮不滚；横幅不滚（单行/可换行由文案决定）；页面滚动仍在 viewport 内。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 载体不支持 → 整键不渲染（`sandboxed === undefined`）；切换后若页面正在加载，横幅立即出现但策略对当前文档未必生效（**未见实现** 强制重载）；多语言：两条 label 分别是「关闭沙箱限制 / 恢复沙箱限制」（`:14-15`）。 |
| 11 | 无障碍 | 原生 button；`aria-label` / `title` 随状态换文案；`aria-pressed`；警告横幅 `role="status"`（会被读屏播报）；图标 `aria-hidden`（`SandboxPolicyIcon` 是纯装饰，`:25`）。**语义问题**：`aria-pressed=true` 读作「已按下」，对应的是「限制已关闭」——读屏用户听到「关闭沙箱限制，已按下」尚可，但与「沙箱已启用」的直觉相反（Q-SB-09）。 |
| 12 | 依据 | `E0` `BrowserBody.tsx:23-32,74,102-111`；`E0` `Browser.module.css:47-50,224-230`；`E0` `locales.ts:14-16,47-49`。 |
| 13 | 反证 + 验收 | **反证**：若关闭沙箱后不给**常驻**横幅而只弹一次 toast，用户切走再回来完全忘了自己关过 → 内嵌页可导航顶层应用、锁输入、弹模态（`locales.ts:16` 逐条列出），等于把宿主交给网页。若点击直接切换而不做确认，误触一下就是同一个后果（本包现状是「无确认 + 常驻横幅」的折中，myco 是否加确认见 Q-SB-09）。**验收**：`BRW-10` 关闭沙箱后横幅常驻，切 tab 再回来仍在（真机截图）；`BRW-11` 读屏播报状态切换（TalkBack/VoiceOver 真机）。 |

#### `base-sidebar-right.browser.restore`（恢复上次页面）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.browser.restore`。内容区上的恢复卡片（`BrowserBody.tsx:118-125`）。出现条件：`restoreTarget !== undefined`（持久化里有上次目标且尚未恢复，`:58`）。每屏 0..1。 |
| 2 | 网格与定位 | `position:absolute; inset:0`，纵向 flex：`padding:24px`、`gap:10px`、`text-align:center`、`overflow:auto`；label 顶部 `margin:auto 0 0`、按钮底部 `margin-bottom:auto` → 三段居中（`Browser.module.css:149-186`）。标题/URL 均 `max-width:100%` + `overflow-wrap:anywhere`（`:167-181`）。 |
| 3 | 命中区 | 卡片本身不点；只有底部 `Button variant=primary size=sm`（高约 24-28px）。**<44dp**。卡片覆盖整个内容区 → 恢复期间页面不可交互（有意的模态语义，但**无遮罩**，靠 absolute 铺满实现）。 |
| 4 | 手势 | 单击按钮。卡片滚动区可滚（`overflow:auto`）。 |
| 5 | 行为 | `restore(tab.id)`（`:122`）→ 载体按持久化的 target 重建页面。disabled = `mountEpoch === 0`（载体还没挂出）。恢复后 `restoreTarget` 变 undefined，卡片消失（`:126` 的 placeholder 条件）。幂等（恢复后就没了）。可撤销：无（要重开只能再导航）。 |
| 6 | 视觉状态 | default：卡片透明底（透出 `bg-base`）、label tertiary、title xs-13/500、url secondary；按钮 primary。无 hover 态（卡片非交互）。 |
| 7 | 布局占位与展开形态 | **绝对定位覆盖**内容区，不占布局流；不属于 G-03 三形态（不是 sheet，因为无层、无遮罩、无独立高度）。 |
| 8 | 滚动语义 | 卡片自身 `overflow:auto`：内容（超长 URL）超出可滚；滚动到边后是否传给外层：外层 `.content` 不滚 → 无链式滚动，天然吸收（接近 G-04）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 超长 title/url 折行（`anywhere`）；恢复失败：无错误出口（**未见实现** 恢复失败提示，按钮可重复点）；`loading` 期间卡片不显示（条件 `restoreTarget===undefined` 才显示 placeholder，`:126`）；多语言文案见 `locales.ts:19-20`。 |
| 11 | 无障碍 | `<section aria-label="上次打开">`（`:118`）；label/title/url 是三个 `<p>`，无语义层级（**未见实现** 标题语义）；按钮是 Button 组件（原生 button），`disabled` 原生；焦点顺序落在内容区的按钮上（前面的工具条已在其前）。 |
| 12 | 依据 | `E0` `BrowserBody.tsx:58,118-126`；`E0` `Browser.module.css:149-186`；`E0` `browser/BrowserPersistence.ts`（`currentBrowserTarget/restoreTarget`）；`E0` `locales.ts:19-20,52-53`。 |
| 13 | 反证 + 验收 | **反证**：若不做「上次打开」恢复，Electron 载体崩溃或切 tab 后用户只能从地址栏重输（长 URL 手输几乎不可能）；若恢复卡片只居中不铺满、也不给滚动，超长 URL 会被裁掉一截，用户无法确认要恢复的是哪一条。**验收**：`BRW-12` 关掉 tab 再开，恢复卡片出现且 URL 完整可见/可滚（截图）；`BRW-13` 点恢复后页面回到原地址（真机）。 |

### 2.3 `chat`（会话骨架：标题/面包屑/视图 tab/转录滚动/宽度轴）

#### `chat.header.crumb`（会话面包屑 + 子代理层级钻取）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.header.crumb`。所属 `chat` → 会话 header → 标题簇。父：`nav aria-label="会话层级"`（`ConversationSession.tsx:73`）。出现条件：`!hideChrome`（即非空白会话，`:66,71`）；每层祖先 1 条，末位是当前会话。链路由 `deriveAncestry()` 从 `SessionSummary.parentId` + `origin==='subagent'` 回溯（`:25-43`），带环保护（`:29-31`）。 |
| 2 | 网格与定位 | header：`grid-template-columns:auto minmax(0,1fr)`、`min-height:76px`（有 tab 时）、`padding:10px 28px 0 20px`（`ConversationRoot.module.css:15-28`）；有 tab 时底部规则由 tab 的 2px 条压住。标题行 `min-height:30px`、`container-type:inline-size`（`:64-81`），并让位给窗口 chrome：`padding-inline-start: max(0, var(--dsh-frame-leading-clearance) - 20px)`（`:76-80`）。面包屑：`gap:4px`、`overflow:hidden`、`nowrap`（`:102-109`）；每段按钮 `max-width:220px`、`padding:4px 8px`、圆角 md、`ellipsis`（`:125-139`）；子代理段降字号到 12/18（`:141-144`）；分隔符「/」14px caption 色（`:118-123`）。 |
| 3 | 命中区 | 只有**非末位**段是按钮，命中=文字块 `padding 4×8` + 行高 20 → 高约 28px、宽随文字（上限 220px）。**<44dp**；相邻段之间只有 4px gap + 「/」字符，隔离带不足（Q-SB-08）。末位段是 `<span>`，点不动。 |
| 4 | 手势 | 单击跳转。无长按/右键（**未见实现** 层级段上的「在侧栏定位 / 复制 id」等二次操作）。 |
| 5 | 行为 | `open(summary.id)` → 切到该祖先会话（`ConversationSession.tsx:90`）。状态迁移：当前会话换人、主面板保留（见 `chat.list.scrollport` 的驻留语义）。幂等。能否撤销：只能再点回原会话（无「返回」栈）。 |
| 6 | 视觉状态 | 非末位：tertiary 文字、`cursor:pointer`、`hover` → `interactive-bg-hover` 底（`:146-148`）。末位：primary、`font-weight:500`、`cursor:default`、透明底（`:150-154`）。`focus-visible`：**未见实现**（本包未定义，走浏览器默认环）。`disabled`：无（末位不是 disabled 按钮，而是纯文本 —— 见字段 13 反证）。 |
| 7 | 布局占位与展开形态 | 参与 flex 行，`min-width:0` 可压缩；段内文字截断不换行。不展开。层级**不做树形展开**，只做「一路向上」的平铺面包屑。 |
| 8 | 滚动语义 | 不滚：`overflow:hidden` + nowrap，超长只截断（`:102-109`）。祖先过多时靠各段 220px 上限 + 容器裁剪，**未见实现** 中间段折叠成「…」菜单（长链会把当前标题挤到不可见，见反证）。 |
| 9 | 动效 | 无（hover 底色瞬切）。 |
| 10 | 边界态 | 祖先链为空（summary 未到）→ 回退渲染裸 `sessionId`（`:125`）；子代理会话字号降低（`:141-144`）；长标题 ellipsis 到 220px；多语言：层级 nav 有 aria-label 文案；大字号：14px 固定不随内容字号；横屏无特殊处理。 |
| 11 | 无障碍 | `nav aria-label="会话层级"`；非末位是原生 button（可聚焦）；末位是纯文本（**无** `aria-current="page"` —— 靠「不是按钮」表达当前位置，见反证）；子代理段由 `conversation.session.header.lineage` 槽位可注入额外内容（`:104-121`，本包只给 fallback）。 |
| 12 | 依据 | `E0` `ConversationSession.tsx:25-51,58-66,73-126`；`E0` `ConversationRoot.module.css:15-28,64-81,102-154`；`E1` 注释声称「当前段不用 disabled button 是为了 darwin 拖拽面」（`ConversationSession.tsx:76-79`）。 |
| 13 | 反证 + 验收 | **反证**：若末位也用 `<button disabled>`，在 darwin 上按钮会把自己从 header 的拖拽面里减掉 → 标题区一段无法拖窗，且 `disabled` 按钮在多数读屏里被跳过，用户听不到「当前在哪」（`ConversationSession.tsx:76-79` 明写这条取舍）。若祖先段不给 `max-width`，一条超长子代理标题会把当前会话名挤出屏幕。**验收**：`CHR-01` 子代理三层链截图，当前段永远可见且可拖窗（截图 + 真机手势）；`CHR-01b` 面包屑点击切换会话后，主面板不重建（真机，输入框里的草稿还在）。 |

#### `chat.header.tab-strip` + `chat.header.view-tab`（会话视图 tab，组节点 + 原子）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.header.tab-strip`（组节点：`role=tablist` 容器，`ConversationSession.tsx:143`）+ `chat.header.view-tab`（每个视图 1 个，`:145-155`）。出现条件：`!hideChrome && tabs.length > 1`（`:66`）—— 只有**多个视图**时才出现，单视图会话没有 tab 条。这些是**视图 tab**（聊天/轨迹…），不是会话 tab、也不是多标签页。 |
| 2 | 网格与定位 | `.tabs` 跨两列（`grid-column:1/-1`）、`z-index:1`、`margin-top:10px`、`padding-left:8px`、`gap:36px`、底部对齐（`ConversationRoot.module.css:191-201`）；tab `padding:0 0 9px`、13/16、字重 500（`:206-216`）；选中条是 `::after` 2px、`bottom:-1px`（压住 header 的 0.5px 规则线，`:218-238`）。header 的 76px 高度就是「10+30+10+16+9」凑出来的（`:10-14` 注释）。 |
| 3 | 命中区 | 每个 tab = 文字块 + 9px 下内边距，高约 25px、宽随文案，**未扩边** → **<44dp**。tab 间距 36px（远大于 8dp，隔离带安全）。 |
| 4 | 手势 | 单击。**未见实现**：左右方向键切换、`Home/End` 跳首尾、roving tabindex（`role=tablist` 的 ARIA 键盘契约未实现，见反证）。 |
| 5 | 行为 | `selectView(viewTab.id)`（`:151`）→ 写本地视图 ledger（`useStore(s => s.view)`）→ `resolveActiveView` 回退到默认视图（`:64`）。切视图**不重建**会话（`DefaultConversationViews.tsx:32-43` 只换 `conversation.view` 槽位内容）。幂等。可撤销（切回）。 |
| 6 | 视觉状态 | 未选中：tertiary、透明底。选中：`--dsw-alias-state-business-primary` 文字 + 同色 2px 下条（`:229-238`，注释说明「brand-primary 在这套 token 里是中性黑，所以用业务蓝」）。hover：**未见实现**（无 hover 规则）。focus-visible：**未见实现**（走浏览器默认）。 |
| 7 | 布局占位与展开形态 | tab 条参与 header 布局（`flex` 行），高度固定 25px + 10px 上边距；header 有 tab 时 `min-height:76px`，无 tab 时 `min-height:0`（`:25-28`）→ **有无 tab 会改变 header 高度 76→约 40px**，下方内容整体上移。不展开。 |
| 8 | 滚动语义 | 不滚（容器无 overflow 声明）；tab 多到超出 header 宽度时**未见实现**横向滚动/换行 → 会被裁切（见反证）。 |
| 9 | 动效 | tab 切换无过渡（选中条瞬移）。header 高度随 tab 出现/消失无动画 → 会话从空白转正式时 header 会瞬跳。 |
| 10 | 边界态 | 恰好 1 个视图 → 整条不渲染（`:66`）；`hideChrome`（空白会话）→ 不渲染；多语言长文案：36px gap + 自适应宽，超宽会裁；大字号：13px 固定；横屏无特殊处理。 |
| 11 | 无障碍 | 容器 `role="tablist"`（`aria-label` **未见实现** —— 无标签的 tablist）；tab `role="tab"` + `aria-selected`（`:148-149`）；**没有** `aria-controls` / `id` 关联面板，**没有** roving tabindex → 每个 tab 都是 Tab 停靠点，且方向键无效；读屏会报「tab 1/2」但键盘行为不符合 ARIA APG。 |
| 12 | 依据 | `E0` `ConversationSession.tsx:62-66,140-157`；`E0` `ConversationRoot.module.css:10-28,191-238`；`E0` `DefaultConversationViews.tsx:17-43`；`E0` `view-selection.ts`（`resolveActiveView`）。 |
| 13 | 反证 + 验收 | **反证**：声明了 `role="tablist"` 却不实现方向键/roving tabindex，是**比不用 tab 角色更糟**的谎言：读屏用户会尝试左右键，没反应；而且每个 tab 都进 Tab 序，n 个视图就多 n 次 Tab 才能到正文。若 tab 不做横向滚动，myco 一旦 >4 个视图就会被裁掉且无法到达。**验收**：`CHR-02` 键盘上只有选中 tab 在 Tab 序内，左右键可切换（真机键盘）；`CHR-02b` 6 个视图时全部可达（截图，实现方式待 P2 定：滚动或折叠菜单）；`CHR-03` 切视图后输入草稿不丢（真机）。 |

#### `chat.list.width-handle`（转录宽度拖拽条，左右各一）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.list.width-handle`。所属 `chat` → 会话 body → 宽度轴（`ConversationWidthControls.tsx:136-182`）。出现条件：`container !== null && phase === 'active'`（`:171`）→ **只在正式会话态出现**，hero/blank/settling 都没有。每次 2 个（left/right）。被会话视图声明 `composer overlay` 时整组隐藏（`:329-331`，`display:none`）。 |
| 2 | 网格与定位 | `position:absolute; top:0; bottom:0`，绝对定位在 `.body`（**不是**滚动容器，避免扩大可滚范围；**不是** `.root`，避免高光透到 header 透明底，`ConversationRoot.module.css:247-258`）。宽 `min(10px, (100% - 内容宽)/2 - 48px)` → 放不下时解析为 0（无错位命中区）；`left` 条 `right: calc(50% + 内容宽/2 + 24px)`，`right` 条镜像（`:259-279`）。内容宽默认 `clamp(680, 列宽*0.64, 920)`，用户偏好夹在 `[640, 列宽-176]`（`ConversationWidthControls.tsx:6-13,24-28`）。 |
| 3 | 命中区 | 宽 10px（放不下则为 0）、全高；视觉区内侧距内容列 24px、外侧保留 24px 安全区。**宽度远低于 44dp** → 移动端不可用（Q-SB-08 同类）。 |
| 4 | 手势 | `pointerdown`（仅主键）→ `setPointerCapture` + `preventDefault`；`pointermove` → rAF 节流后再回调；`pointerup` → 提交；`pointercancel/onLostPointerCapture` → 放弃（不改偏好）。拖动位移 **×2**（左右同时外扩，`outwardWidth()`，`:56-60`）。滚轮：把滚轮 delta 换算成像素后**转发给转录滚动容器**（`:105-114`，支持 line/page 两种 deltaMode）。 |
| 5 | 行为 | 拖动写 CSS 变量 `--dsh-chat-user-width`（即时）；**只有真正移动过**（`latest !== origin`）才在松手时写 `localStorage['dsh.conversation.contentWidth']`（`:93-94,163-166`）—— 单击不动不会把窄屏钳制值覆盖成偏好。取消手势回滚到已保存宽度（`onEnd` → `publishWidths`）。幂等。可撤销：无（无「重置宽度」入口，**未见实现**）。 |
| 6 | 视觉状态 | 静息：完全不可见（无分隔线、无把手）。`hover`：`::after` 2px 高光条显现（`opacity:1`，`ConversationRoot.module.css:315-318`），高光**跟随指针 Y**（`top: 指针 Y ±8px 实心 + 28px 渐隐`，`:281-313`）。`dragging`：`z-index:8` 抬到页脚之上、高光保持（`:320-325`）；拖动期间 grid 过渡关闭。`disabled`：无。 |
| 7 | 布局占位与展开形态 | 绝对定位，**不占布局流**。改变的是 `--dsh-chat-user-width` → 消息列与输入卡同步变宽（G-01 意义上的「不占位」）。不属于 G-03。 |
| 8 | 滚动语义 | 自身不滚。滚轮**不滚自己而是转发外层转录**（见字段 4）—— 这是「在宽度条上滚 = 滚对话」的意图仲裁。 |
| 9 | 动效 | 拖动期间无过渡（`[data-dragging]` 去掉 transition，`:242-245`）；松手无回弹动画；高光 `opacity` 无时长声明（瞬时）。 |
| 10 | 边界态 | 窄列（margin 不够）→ 宽度算成负 `min()` → 解析 0，命中区消失（不会错位，`:252-258` 注释）；窗口缩放中 `ResizeObserver` 重算并重钳制偏好（`:146-152`）；`localStorage` 损坏/NaN → 回退默认（`:16-21`）；多语言无关；大字号无关；横屏：变宽后可能把左右把手挤出可用区 → 归零（可接受）。 |
| 11 | 无障碍 | 纯 `div`，**无 role、无 tabindex、无键盘等价物、无 aria-valuenow** → 键盘与读屏用户**完全无法**调宽（`ConversationWidthControls.tsx:116-127` 只有指针事件）。我的判断：这是 E0 现状，不等于可接受（见 Q-SB-10）。 |
| 12 | 依据 | `E0` `ConversationWidthControls.tsx:5-38,41-129,136-182`；`E0` `ConversationRoot.module.css:240-331`；`E0` `AppFrame.tsx:58-118`（同款 pointer+rAF 模式）。 |
| 13 | 反证 + 验收 | **反证**：若把把手放进滚动容器，绝对定位的把手会把滚动范围撑大 → 内容永远「差一点滚到底」；若放在 `.root` 上，高光会透过 header 的透明底画到标题行（这两条都是源码注释里的直接取舍，`ConversationRoot.module.css:247-251`）。若单击（未移动）也写偏好，窄窗口下显示值被钳制后的宽度会被当成用户偏好存下来 → 窗口变宽后内容仍然窄（`:93-94` 就是为这条）。**验收**：`CHR-04` 单击把手不动，重载后宽度不变（真机）；`CHR-04b` 拖到最宽/最窄后重载，宽度被恢复且不超出钳制范围（真机）；`CHR-04c` 在把手上滚轮，转录内容滚动而面板宽度不变（真机录屏）。 |

#### `chat.list.scrollport`（组节点 · 转录滚动容器与驻留语义）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.list.scrollport`。所属 `chat` → `.body` → `.scrollBody`（`ConversationContent.tsx:197-203`，`ConversationRoot.module.css:401-416`）。出现条件：始终（会话与空白态都在）。每屏 1 个。**它是全局唯一的转录滚动容器**（`data-conversation-scroll`）。 |
| 2 | 网格与定位 | `flex:1; min-height:0; overflow-y:auto`；`margin-right:2px`（滚动条离列缘 2px，与 `ui-workspace` 列表同款）；`scrollbar-gutter: stable`（**无条件**预留，理由见反证）；滚动条轨道 `margin:2px`（WebKit）（`:401-422`）。父 `.body` 是宽度轴变量宿主（`:374-391`：内容宽、输入卡最大宽、侧净空 16px、dock 内缩 8px）。 |
| 3 | 命中区 | 整块可滚区；滚动条本身在 `scrollbar-gutter` 预留的 ~8px 内（悬停可拖）。无点击语义。 |
| 4 | 手势 | 滚轮/触控板滚动；指针拖动滚动条。**无**「下拉刷新」、**无**边缘滑动手势。与草稿、宽度条、队列 dock 的仲裁见下。 |
| 5 | 行为 | 无导航。写两条测量值给浮动控件用：`--dsh-composer-height`（输入座位高度）与 `--dsh-conversation-viewport-height`（滚动口高度），由 `ResizeObserver` 在 InputBar 座位与滚动口上发布（`ConversationContent.tsx:45-67`）。 |
| 6 | 视觉状态 | 静息：内容从顶部开始。hero 相位：`justify-content:center`（空白会话把输入卡居中，`:534-538`）。settling 相位：输入座 `visibility:hidden`（不回闪，`:540-545`）。滚动条：l2 色对，侧栏指针离开时被 transient 透传为透明（见 `sidebar.shell.column`）。 |
| 7 | 布局占位与展开形态 | 参与布局流（flex:1），占满 header 与底之间的全部高度。**输入座是它的 sticky 子项**（`position:sticky; bottom:0; z-index:7`，`:430-453`）→ 输入区始终贴底且参与同一滚动流（G-01 的实现方式）。 |
| 8 | 滚动语义 | 唯一外滚容器。内层到边后有**三处链式滚动/仲裁**，全部要抄准：①草稿滚到边 → wheel **转发**给本容器（`view-binding.ts:65-79`）；②队列/计划列表 `max-height:180px` 内滚，本包**不设** `overscroll-behavior` → 到边交浏览器默认（会外溢）；③宽度把手滚轮 → 直接转发（`ConversationWidthControls.tsx:105-114`）。会话视图可声明 composer overlay，此时代码改成 `overflow-x:hidden; overflow-y:auto; scrollbar-gutter:auto`（`:466-480`）。 |
| 9 | 动效 | 无滚动动画/AY 平滑滚动（源码无 `scroll-behavior:smooth`）。 |
| 10 | 边界态 | 首次进入：`scrollbar-gutter:stable` 让输入卡不会因滚动条出现而横移（`:411-414` 注释 + 归档决策链接）；会话重放（loading+blank）期间 `visibility:hidden`，避免 hero↔docked 闪跳；多语言/大字号只影响行高；横屏：内容宽按列宽 0.64 计算，仍夹在 680-920。 |
| 11 | 无障碍 | 无 role（浏览器默认可聚焦滚动容器）；无 `tabindex`（**键盘用户无法用 Tab 进入滚动区**，只能靠页内控件移动焦点）；无 `aria-label`。**未见实现** 滚动位置播报/「跳到最新」无障碍入口。 |
| 12 | 依据 | `E0` `ConversationContent.tsx:45-67,188-203`；`E0` `ConversationRoot.module.css:363-366,374-422,430-453,466-501,534-545`；`E0` `view-binding.ts:65-79`；`E0` `ConversationWidthControls.tsx:105-114`。 |
| 13 | 反证 + 验收 | **反证**：若 `scrollbar-gutter` 用 `auto`，输入卡与草稿会在「滚动条出现/消失」时横向跳 8px；且由于输入座在 Chat 里骑的是 content box、在 overlay 视图里骑 padding box，两者会错位 → 切 tab 输入卡横向漂移（源码里有一条专门的归档决策，`:411-415`）。若不给草稿做「到边链式」，短草稿会把滚轮手势**困在输入框**里，用户在输入框上滚永远滚不动对话（`view-binding.ts:60-64` 注释）。**验收**：`CHR-05` 内容由短变长、滚动条出现，输入卡左缘 x 不变（真机录屏逐帧）；`CHR-05b` 指针停在草稿上滚轮，草稿到顶后对话继续滚（真机）；`CHR-05c` 切到带 composer overlay 的视图再切回，输入卡 x 不变（截图比对）。 |

### 2.4 `composer`（输入区状态机入口）

#### `composer.input.seat`（组节点 · 贴底输入座位 + 渐隐遮罩）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.input.seat`。所属 `composer` → 会话 body 底部座位（`ConversationContent.tsx:182-186`）。出现条件：始终（`data-composer-seat`），内容由 `conversation.composer` 链决定（fallback 或占用者 overlay）。每屏 1 个。 |
| 2 | 网格与定位 | `position:sticky; bottom:0; z-index:7`（`ConversationRoot.module.css:430-441`）；高度=内容高；顶部 36px 渐隐遮罩：`linear-gradient(180deg, bg-base 0%, bg-base 36px)`（`:442-452`，px 停靠点而非百分比 —— 草稿变高时只放宽实心段，渐隐带不拉伸）；吃的是 `--dsh-composer-text-max-height:336px`（14 行 × 24px，`:346-358`）。hero 相位改为 flex 居中 + 底部 32px 内边距（`:507-518`）。 |
| 3 | 命中区 | 座位本身不点（内容命中）。渐隐遮罩区**不可点**（纯背景渐变，不拦截，`:448-452` 无 pointer-events 声明 → 默认按元素区域接收，但该区域是座位自身，事件落到底下的输入卡/消息上）。 |
| 4 | 手势 | 无自有手势；轮滚在此区域会**滚外层转录**（因为座位是滚动流的 sticky 子项，不是独立滚动容器）。 |
| 5 | 行为 | 无导航。层叠：当会话视图声明 composer overlay 时，座位从 sticky 变 `absolute; right: var(--dsh-scrollbar-width); bottom:0`（`:488-501`），且 `data-conversation-composer-overlay` 存在时宽度把手隐藏（`:327-331`）。 |
| 6 | 视觉状态 | 无自身视觉（背景渐变除外）。`settling` 相位：`visibility:hidden`（`:540-545`）。has(trigger-menu) 时 `z-index:9`（高于「回到底部」类控件，`:455-462`）。 |
| 7 | 布局占位与展开形态 | **参与布局流**（sticky 子项，占内容高度）；但它「贴底」的观感是 overlay 式的（G-01：不额外占屏——高度由输入卡自身撑开，不预留空行）。不属于 G-03（不是可选展开体）。 |
| 8 | 滚动语义 | 不滚（自身无 overflow）；它随滚动流移动但在底部粘住 —— 转录内容从它**下面**滚过，并被 36px 渐隐遮罩淡出。 |
| 9 | 动效 | 无位移/高度动画（sticky 天然跟随）；渐隐是静态背景。 |
| 10 | 边界态 | 草稿长到 336px 上限后停止长高（`:346-358`）；hero→active 相位切换是**类切换**不是重挂载，输入框 DOM 保留；无会话（`sessionId===undefined`）时座位仍在（渲染 inert 输入卡）；多语言无关；大字号：`--dsh-content-font-size` 参与草稿行高，336px 上限刚性（字大 → 可见行数变少）。 |
| 11 | 无障碍 | 无 role（布局）。座位内控件顺序 = DOM 顺序（详情→队列→输入卡→dock）。 |
| 12 | 依据 | `E0` `ConversationContent.tsx:182-203`；`E0` `ConversationRoot.module.css:327-358,430-462,466-501,507-518,540-545`；`E1` 注释声称 figma 1205:27463 的遮罩几何（`:442-447`）。 |
| 13 | 反证 + 验收 | **反证**：遮罩若用百分比（如 24%）而不是 px，草稿从 1 行长到 14 行时渐隐带会一起拉伸 → 消息在更高的位置就开始虚化，可读区变短（`:445-447` 明写）。若座位改成 `position:absolute` 常驻（很多实现的直觉做法），它就不再参与滚动流：转录滚到底时最后一条消息会被输入卡盖住且**滚不出来**（sticky 参与布局才能把它顶上去）。**验收**：`CMP-01` 草稿从 1 行打到 14 行，渐隐带高度恒定 36px（截图逐帧）；`CMP-01b` 会话滚到底，最后一条消息完整可见不被输入卡遮挡（真机截图）。 |

#### `composer.input.card`（输入卡容器 / 无工作区时整卡=选择器触发器）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.input.card`。所属 `composer` → 输入卡（`InputBar.tsx:395-402`）。出现条件：始终（含 inert 态）。每屏 1 个。当 `workspaceTrigger`（无会话且未移除、有 `onRequestWorkspace`）时，**整张卡**变成工作区选择触发器（`:152,397-400`）。 |
| 2 | 网格与定位 | `max-width: var(--dsh-composer-card-max-width)`（= 内容宽 + 32px）；`padding-top:8px`、`gap:12px`、圆角 `--dsw-radius-panel`、`box-shadow: --dsw-elevation-soft`、0.5px 通过 elevation stroke 画的边框；字号/行高继承内容字号（`InputBar.module.css:45-77`）。座位内水平净空 16px（`ConversationRoot.module.css:389`）。 |
| 3 | 命中区 | 卡片自身不点（普通态）；`workspaceTrigger` 态**整卡可点**，且卡片内 disabled 控件被设为 `pointer-events:none` 让点击穿透到卡片（`InputBar.module.css:99-104`）。这是「点哪儿都能选工作区」的有意扩边。 |
| 4 | 手势 | 普通态：`mousedown` 有特殊仲裁（见字段 5）。`workspaceTrigger` 态：`onClick=onRequestWorkspace` + `onPointerDown` 只 `stopPropagation`（`:399-400`），避免 Menu 的「点外关闭」与 click 的「重新打开」竞态造成闪烁（`:390-394` 注释）。 |
| 5 | 行为 | 普通态 `onMouseDown`：若可编辑、主键、未被 preventDefault、目标不是 `[data-composer-overlay]`、不在草稿滚动区内、且不是任何可交互控件 → `preventDefault` 并把焦点还给编辑器（`:280-292`）。意图：点卡片空白处继续打字，且不抢走已聚焦控件的点击。`workspaceTrigger` 态：打开工作区选择器。 |
| 6 | 视觉状态 | 普通：`--dsw-specific-input-major` 底 + 柔和阴影 + 描边。`workspaceTrigger`：描边换成**内嵌 1px 虚线**（`::after`，`pointer-events:none`，`:79-108`），`cursor:pointer`，hover 时虚线变业务蓝（`:106-108`）——「这是选择器不是输入框」的视觉承诺。`disabled/blocked`：无卡片级样式（内部控件各自 disabled）。 |
| 7 | 布局占位与展开形态 | 参与布局流，高度由内容（附件栏 + 草稿 + 工具行）撑开；不展开。overlay 锚点 `[data-composer-overlay]` 是 0 高度的绝对定位子盒（`:117-123`），菜单从这里向上弹。 |
| 8 | 滚动语义 | 卡片不滚（唯一滚动盒是内部 `.scroll` 草稿区）。卡片内的工具行在窄宽下会 `flex-wrap` 换行（`:251-267`）。 |
| 9 | 动效 | 虚线描边颜色 `transition: color 100ms ease`（`:95`）；其余无。 |
| 10 | 边界态 | 无工作区（inert）→ 整卡触发态 + 草稿区 disabled + placeholder「选择工作区」（`InputBar.tsx:141-154,363-372`）；被业务阻塞（`blocked`）→ 保留草稿可见但不可提交，placeholder 换成阻塞原因（`:145-161,363-372`）；父级离线（continuable 子代理）→ 同样禁止输入但独立 Stop 仍可用（`:134-141,481-496`）；多语言：虚线与文案均本地化；大字号：字号跟随内容字号，卡片高度自适应但草稿区仍受 336px 上限。 |
| 11 | 无障碍 | 普通态是 `div`（非交互，`data-composer-card`）；触发态仍是 `div` + `onClick`，**没有 `role="button"`/`tabindex`** → 键盘用户只能靠里面的草稿区（DraftEditor 在触发态给了 `tabIndex=0` + `aria-haspopup=menu` + Enter/Space，见 `composer.input.draft-surface`）触发，卡片本身的点击是纯指针便利。 |
| 12 | 依据 | `E0` `InputBar.tsx:140-161,276-302,363-372,395-404`；`E0` `InputBar.module.css:45-123,251-267`；`E0` `ConversationRoot.module.css:384-391`。 |
| 13 | 反证 + 验收 | **反证**：`workspaceTrigger` 态若不让 disabled 控件 `pointer-events:none`，用户点到「+」或发送键（已置灰）会**什么都不发生**，而点旁边 2px 却弹出选择器 —— 同一张卡两种命运。若 `pointerdown` 不 `stopPropagation`，Menu 的「点外关闭」会在 click 之前先把刚打开的菜单关掉，再被 click 重新打开 → 视觉闪烁（`:390-394` 注释就是这条）。**验收**：`CMP-02` 无工作区时卡片任意位置点击都弹出选择器（真机，含点在置灰控件上）；`CMP-03` 卡片空白处点击后光标回到草稿（真机连点 5 次，焦点不回跳）。 |

#### `composer.input.draft-surface`（草稿表面：唯一文本输入 + 到边链式滚动）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.input.draft-surface`。所属 `composer` → 输入卡 → 草稿滚动盒 + contenteditable（`DraftEditor.tsx:35-62`）。出现条件：始终（驻留 DOM，inert 时变触发器）。每屏 1 个。文本由 Lexical 编辑器提供（`ComposerContentEditable`）。 |
| 2 | 网格与定位 | 外层 `.scroll`：`max-height: var(--dsh-composer-text-max-height)`(336px) + `overflow-y:auto` + `margin-right:4px`（`InputBar.module.css:125-134`）；内层 `.input`：`min-height:36px`（hero 52px）、`padding:4px 8px 0 14px`、`white-space:pre-wrap`、`word-break:break-word`、`overflow-wrap:anywhere`、`caret-color` 业务蓝（`:165-185,233-237`）；Lexical 段落 `<p>` 去 UA margin（`:187-191`）；placeholder 绝对定位 1 行（hero 2 行 clamp）（`:209-247`）。 |
| 3 | 命中区 | 整块草稿区（宽=卡片内宽，高 36→336px）。**无扩边需求**（本身够大）；但 hero 态 52px、docked 态 36px，短草稿时高度仍 <44dp。 |
| 4 | 手势 | 指针：点击定位光标（由编辑器管理）；滚轮：到边后**转发**给 `[data-conversation-scroll]`（`view-binding.ts:65-79`，`passive:false` + `preventDefault`）；`mousedown` 在卡片上的仲裁见 `composer.input.card`。**未见实现** 长按/双击/滑动手势。 |
| 5 | 行为 | 输入写 draft；Enter/加速 Enter/空格/粘贴/文件选择都由 keymap 注册（`installDraftKeymap`，`view-binding.ts:106-145`）：加速 Enter 在有队列时 = `steerQueue()`；上传未完成时 Enter 只弹提示「文件仍在上传」；空草稿不可提交（由机器层拒绝）。`workspaceTrigger` 态：本表面退化为选择器触发器（`DraftEditor.tsx:38-51`，`onKeyDown` 处理 Enter/Space → `onRequestWorkspace`）。焦点：解锁/切会话后自动聚焦并把选区滚进可视（`InputBar.tsx:171-194`）。 |
| 6 | 视觉状态 | `data-phase` 反映输入机状态（inert/plain/claimed/submitting/adjudicating）；`aria-disabled` + `.inputDisabled`（tertiary 字 + `cursor:not-allowed`）；触发态加 `[aria-haspopup=menu]` → `cursor:pointer`（`InputBar.module.css:224-231`）；IME 组字时 `[data-composer-composing]` 隐藏 ::after 提示与 placeholder（`:201-207`）。占位符 caption 色、单行 ellipsis。 |
| 7 | 布局占位与展开形态 | 参与布局流：高度随内容从 36/52 长到 336 后**停在 336 并内滚**（不是无限长高）。这是本包里唯一「封顶内滚」的文本区，但它是**输入框**不是展开体，不归 G-03。 |
| 8 | 滚动语义 | 自身是内滚容器；到边后 wheel **链式**交给外层转录（G-04 的反例，见 Q-SB-04）；选区自动 reveal（`revealDraftSelection`，`view-binding.ts:28-46`）：只在选区出界时改 `scrollTop`，最小位移。 |
| 9 | 动效 | 无高度动画（长高是即时重排，但发生在贴底座位内部，不推动转录）。placeholder 隐藏是即时。 |
| 10 | 边界态 | 空草稿：placeholder（docked 1 行 / hero 2 行）；超长：到 336px 后内滚；IME 组字：placeholder 与幽灵提示隐藏；禁用：仍占位、仍可见草稿（不卸载，`InputBar.tsx:9-13,74` 明写「same DOM inert」）；大字号：行高 `24 + font-delta`，336px 上限刚性；多语言：placeholder 与 claim 幽灵提示本地化（`InputBar.tsx:344-372`）。 |
| 11 | 无障碍 | contenteditable `div` + `aria-label`（触发态用「选择工作区」，否则=placeholder 文案，`DraftEditor.tsx:47,432`）；`aria-disabled`；触发态 `aria-haspopup="menu"` + `aria-expanded` + `tabIndex=0` + Enter/Space；**草稿区本身没有 `role=textbox`/`aria-multiline`**（靠 contenteditable 隐式语义）；claim 幽灵提示是 CSS 生成内容（`::after`），**读屏读不到**（`InputBar.module.css:193-199`）—— 这是 a11y 缺口，见 Q-SB-11。 |
| 12 | 依据 | `E0` `DraftEditor.tsx:31-62`；`E0` `InputBar.tsx:166-204,253-312,363-372,424-438`；`E0` `view-binding.ts:28-79,106-145`；`E0` `InputBar.module.css:125-247`；`E0` `ConversationRoot.module.css:346-358`。 |
| 13 | 反证 + 验收 | **反证**：若草稿包到顶后不把滚轮交给外层，用户在输入框上滚就永远滚不动对话（短草稿时尤其明显，`view-binding.ts:60-64` 注释）—— 这与 G-04「到边吸收」正好相反，是 myco 必须拍板的冲突点（Q-SB-04）。若草稿不做 `min-height`，只有一行时输入卡会缩成一条细线，点不中。若把 inert 态改成「换一棵树」，切换会话/选工作区时输入框会重挂载 → 丢失 IME 组字与选区（`:9-13` 明写）。**验收**：`CMP-04` 空草稿→14 行→再回 1 行，输入卡高度连续变化且不跳变（真机录屏）；`CMP-05` 指针在草稿上滚轮，到顶后对话继续滚（真机）；`CMP-06` 中文输入法组字过程中 placeholder 不遮挡候选（真机，Android/桌面各一次）。 |

#### `composer.input.command-menu-trigger`（「+」命令菜单）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.input.command-menu-trigger`。所属 `composer` → 工具行左端第 1 键（`InputBar.tsx:441-454`）。出现条件：始终渲染；`disabled` 当输入被锁或没有 `toggleCommandMenu` 注入（`:448`）。每屏 1 个。 |
| 2 | 网格与定位 | `.tools` 组：`gap:12px`（窄卡 `@container max-width:560px` 降到 8px，`InputBar.module.css:285-311`）；按钮 `.add`：28×28、`border-radius:999px`、`--dsw-specific-selector` 底、primary 图标 14px（`:313-335`）；工具行 `padding:2px 8px 6px`（`:251-261`）。 |
| 3 | 命中区 | 28×28 圆形。**<44dp**；与后续模式 chip 间距 12px（够隔离）。 |
| 4 | 手势 | 单击。`mousedown` 被 `keepFocus` 拦下（不抢编辑器焦点，`:449`）。无长按。 |
| 5 | 行为 | `onToggleCommandMenu()`：先把焦点还给编辑器（**必须先**，`:294-302` 注释：菜单是挂在编辑器上的 combobox，焦点不在编辑器时恢复焦点会「重新跟踪空草稿」把菜单立刻关掉），再 `toggleCommandMenu(keyboard.caretSpan())` —— 用当前光标位置弹菜单。幂等（开关）。 |
| 6 | 视觉状态 | `aria-expanded={commandMenuOpen}`（`:447`）；`disabled`：`opacity:0.5` + `cursor:default`（`:332-335`）；hover（`:not(:disabled)`）：`interactive-bg-hover-solid` 底（`:328-330`）；`focus-visible`：**未见实现**（走浏览器默认）。 |
| 7 | 布局占位与展开形态 | 参与 flex 工具行，固定 28×28，`flex:none`；窄卡时工具行换行（`flex-wrap`，`:251-267`），本键始终留在左组。菜单本身是浮层（`.overlayAnchor` 0 高度 + 占用者绝对定位，`:117-123`），**不占位（G-01 合规）**。 |
| 8 | 滚动语义 | 不滚；菜单列表滚动由占用者（`ui-input-trigger` 等）负责，本包不设。 |
| 9 | 动效 | 无（无 rotation/无缩放）。 |
| 10 | 边界态 | 输入被锁（removed/inert/blocked/parent-offline）→ disabled；没有注入命令菜单 → disabled；窄卡（<560px）→ 间距收紧；大字号：图标按钮尺寸固定；多语言：`aria-label`「命令」随 locale。 |
| 11 | 无障碍 | 原生 button + `aria-label`；`aria-haspopup="listbox"` + `aria-expanded`（`:446-447`）；Tooltip 待 500ms；焦点顺序：工具行最左；**`aria-controls` 未见实现**（没关联到菜单 id）。 |
| 12 | 依据 | `E0` `InputBar.tsx:294-302,441-454`；`E0` `InputBar.module.css:117-123,251-335`；`E0` `locales.ts`（`input.commands`）。 |
| 13 | 反证 + 验收 | **反证**：若在打开菜单前不先把焦点放回编辑器，键盘用户点「+」后焦点在按钮上，菜单按「失焦即关」逻辑会立刻自关（`InputBar.tsx:296-300` 注释直接写这条）。若把菜单挂在按钮上而不是编辑器光标处，插入命令的位置就会错（`caretSpan()` 就是为了拿光标）。**验收**：`CMP-07` 鼠标点「+」菜单出现在光标处；键盘 Tab 到「+」再 Enter，菜单同样停在光标处且不闪关（真机键盘 + 录屏）。 |

#### `composer.input.file-picker`（隐藏的文件选择 input）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.input.file-picker`。所属 `composer` → 工具行内的隐藏 `<input type=file>`（`InputBar.tsx:455-462`）。恒在 DOM（`hidden`），每屏 1 个。 |
| 2 | 网格与定位 | `hidden`（不参与布局、不可见）；`multiple`；`disabled` 当 `subagent !== null`（子代理输入不支持通用文件，`:459`）。 |
| 3 | 命中区 | 无（`hidden`）。真实触发入口是键盘命令 `bindFilePicker`（`view-binding.ts:88-97`）与 `conversation.input.attachments` 槽位里的按钮（占用者提供）。 |
| 4 | 手势 | 无直接手势；`input.click()` 由 keymap 路径调用。 |
| 5 | 行为 | `onChange` → 取出 `files` → **先把 `value` 清空**（同一文件可再次触发）→ `intakeFiles(picked)`（`:244-249`）。`intakeFiles` 再跑图片上限预检（数量/单张/总量，`:212-239`），超限整批拒绝并 toast，不进入附件轨道。可用性还要求 `canAcceptDrop`（未锁、非忙碌、可加文件，`:241`）。 |
| 6 | 视觉状态 | 无（不可见）。 |
| 7 | 布局占位与展开形态 | 不占位（`hidden`）。 |
| 8 | 滚动语义 | 无。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 子代理会话 → disabled（且 intake 层对不同媒体的拒绝文案不同，`:214-219`）；同一文件重复选择 → 靠清空 `value` 保证再次触发；选择器被取消 → `files === null` → 空数组（`:245`）；文件系统权限拒绝 → 无回执（**未见实现** 错误提示）。 |
| 11 | 无障碍 | `hidden` 的元素不在无障碍树；键盘路径由命令/菜单提供；无 label（`hidden` 无害）。 |
| 12 | 依据 | `E0` `InputBar.tsx:212-249,455-462`；`E0` `view-binding.ts:88-97`；`E0` `image-labels.ts`（拒绝文案）。 |
| 13 | 反证 + 验收 | **反证**：若不清空 `input.value`，用户「选错文件 → 再选同一个」时 `change` 事件不会触发，界面像卡住（`:246-247` 注释）。若把图片上限预检放在上传之后，用户会先看到上传进度再被告知「太多/太大」，白等一轮（`:206-211` 注释：整批拒绝、立即播报）。**验收**：`CMP-08` 连续两次选择同一文件都能进入附件栏（真机）；`CMP-09` 一次选 11 张（上限 10）→ 整批不进入、立即 toast（真机 + 截图）。 |

#### `composer.input.stop`（独立停止键，可中断子代理）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.input.stop`。所属 `composer` → 工具行右端（`InputBar.tsx:481-496`）。出现条件：`interruptible = running && 子代理为 continuable`（`:326`）—— **只有可中断的子代理会话**才额外长出这个键；普通运行态由 `composer.input.primary` 变身 Stop 承担。每屏 0..1 个。 |
| 2 | 网格与定位 | 与主键同款 `.primary`：34×34 圆形、`--dsw-alias-button-info-fill` 底、白图标、`transform: translateY(-2px)` 对齐（`InputBar.module.css:369-391`）；与主键间距 12px（`.trailing gap`，`:293-302`）。 |
| 3 | 命中区 | 34×34 圆形。**<44dp**。 |
| 4 | 手势 | 单击。`mousedown` 走 `keepFocus`（不偷编辑器焦点，`:488`）。 |
| 5 | 行为 | `onClick={stop}`（`:489`）→ 停止当前子代理运行。`disabled` 当 `stop === undefined`（`:487`）。幂等性：重复点击第二次无效（已停）；不可撤销（停了不能续，只能重发）。 |
| 6 | 视觉状态 | default：蓝底白方块（停止图形是 10×10 rx3 rect，`:491-493`）；hover：`button-info-hover`；disabled：`opacity:0.4` + `cursor:default`（`:397-400`）；focus-visible：**未见实现**。与主键同色同形 → 两个蓝圆并列（见反证）。 |
| 7 | 布局占位与展开形态 | 参与 flex 行，固定 34×34，`flex:none`；出现/消失会让主键左移 34+12px（**没有**交叉淡入）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | `background-color 100ms ease`（`:386`）；无出现/消失动画。 |
| 10 | 边界态 | 与主键同时可见时是「停止 + 发送」并存（continuable 子代理允许在运行时继续发消息）；`stop` 注入缺失 → disabled + Tooltip disabled（`:482`）；多语言：`aria-label`「停止」+ 快捷键提示（`stopKeys`）。 |
| 11 | 无障碍 | 原生 button；`aria-label="停止"`；Tooltip 带停止快捷键；**无 `aria-describedby`/上下文说明**（读屏只听到一个「停止」，不知道停的是子代理还是主会话 —— 见反证）；焦点序在主键之前。 |
| 12 | 依据 | `E0` `InputBar.tsx:134-141,323-342,481-496`；`E0` `InputBar.module.css:369-400`；`E0` `stop-shortcut.ts`（`useStopShortcut` 键位）。 |
| 13 | 反证 + 验收 | **反证**：若只有「主键变身 Stop」而没有独立 Stop，continuable 子代理运行时用户一旦打了字，主键就变回「发送」→ **失去停止入口**，只能清空草稿才能停（`:143-145,314-326` 的注释正是这个意思：running + 非空草稿要给忙态发送，还要保留停止）。若两个蓝圆不做区分，用户会点错：一个是「停」一个是「发」。**验收**：`CMP-10` 子代理运行时输入文字，主键=发送、独立 Stop 仍在且可点（真机截图 + 点击生效）；`CMP-11` 读屏报出的两个按钮可区分（TalkBack 真机，文案待 P2 定）。 |

#### `composer.input.primary`（主操作键：发送/停止共用）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.input.primary`。所属 `composer` → 工具行右端（`InputBar.tsx:497-516`）。恒出现 1 个；形态由状态机决定：`primaryStops = running && 无子代理 && (草稿空 \|\| 被业务阻塞)` → 显示 Stop；否则显示发送箭头（`:323`）。 |
| 2 | 网格与定位 | 34×34 圆形、`border-radius:999px`、`--dsw-alias-button-info-fill` 底、静态白图标、`transition background-color 100ms`、`transform: translateY(-2px)`（抵消工具行 2px 下移，`:369-391`）。 |
| 3 | 命中区 | 34×34 圆形。**<44dp**（这是全应用最高频按钮，移动端必须放大，见 Q-SB-08）。 |
| 4 | 手势 | 单击。`mousedown` 走 `keepFocus`（`:503`）→ 打完字直接点发送，焦点不丢、光标不跳。 |
| 5 | 行为 | `onPrimary()`：`primaryStops` 时 `stop()`；否则 `keyboard.submit(primarySubmitMode, 'click')`（`:334-342`）。`primarySubmitMode` 由 `resolveSubmitMode(busyEnter, running, 'enter', steeringAvailable)` 解析 —— 即**点击与 Enter 走同一意图解析**（忙时按设置是 queue 还是 steer）。`disabled = primaryStops ? stop===undefined : empty \|\| disabled \|\| machineBusy \|\| uploadsPending`（`:325`）→ **附件还在上传时不可发送**（`:83-87` 注释：失败的上传要么重试要么移除，绝不静默丢弃）。幂等性：双击会提交两次（机器层去重，本包不防抖）；不可撤销（已提交）。 |
| 6 | 视觉状态 | 发送态：白箭头；Stop 态：白方块；`disabled`：`opacity:0.4`。hover（:not(:disabled)）：`button-info-hover`（`:393-395`）。focus-visible：**未见实现**。`aria-label` 随状态：`input.stop` / `input.send.steer` / `input.send.queue` / `input.send`（`:329-333`）—— 且只在「点击真的会投递普通消息」时才用 steer/queue 文案（`:314-322` 注释）。 |
| 7 | 布局占位与展开形态 | 参与 flex 行，固定 34×34；不展开；出现/消失不影响其他控件（始终存在，只有语义变）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | `background-color 100ms ease`；图标切换是瞬时（无 morph）。 |
| 10 | 边界态 | 空草稿 → disabled（发送态）；运行中 + 空草稿 → Stop；运行中 + 非空草稿 → 发送（忙态语义）；上传中 → disabled；阻塞（no model 等）→ 发送 disabled 但**模型选择位保持可点**（`:142-146` 注释：阻塞都是「选个模型」能解的，所以不能把模型位也锁死）；removed/parent-offline → disabled；多语言：4 种 aria-label。 |
| 11 | 无障碍 | 原生 button；`aria-label` 随时态变化（读屏能听出「发送队列/旁路发送/发送/停止」）；Tooltip 带快捷键（发送：Enter，停止：stopKeys）；无 `aria-busy`（**未见实现** 提交中的自述）。 |
| 12 | 依据 | `E0` `InputBar.tsx:83-87,142-146,314-342,497-516`；`E0` `InputBar.module.css:369-400`；`E0` `input/submission-policy.ts`（`resolveSubmitMode`）；`E0` `locales.ts`（`input.send*`）。 |
| 13 | 反证 + 验收 | **反证**：若上传未完成就允许发送，附件会在提示词已发出后才到 → 模型看到一条没有图片的消息，而用户以为发出去了（`:83-87`）。若阻塞态连模型选择位一起锁死，用户面对「请选一个模型」的提示却点不到模型选择，进入死锁（`:142-146`）。若点击与 Enter 走两套语义，同样一个「忙时按发送」，鼠标和键盘一个入队一个旁路 —— 行为不可预测。**验收**：`CMP-12` 附件上传中主键 disabled，上传完成后自动可用（真机录屏）；`CMP-13` 忙时点发送与按 Enter 产生同一种投递（真机对比队列条数）；`CMP-14` 阻塞态模型位仍可点（真机）。 |

#### `composer.workspace-chip`（工作区 chip / 选择器锚点）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.workspace-chip`。所属 `composer` → hero 工作区行（`EmptyHero.tsx:38-62`；宿主 `ConversationContent.tsx:110-134`）。出现条件：仅 **hero 相位**（无会话或空白会话）渲染该行；有工作区时显示标题、无则显示「选择工作区」占位。每屏 0..1 个。 |
| 2 | 网格与定位 | 行：`gap:2px`、`margin-top:4px`、`padding:0 16px 0 20px`、`min-width:0`（`ConversationRoot.module.css:520-530`）；chip：`inline-flex`、`gap:4px`、`min-height:28px`、`padding:0 8px`、圆角 sm、13/20/500（`HeroShell.module.css:152-167`）；宽度 `max-width: min(100%, 360px)`，label `overflow:hidden + ellipsis`（`:185-190`，注释说明「无断点降级：标签一直收缩到 0，folder 与 chevron 始终贴住剩余文字」）。 |
| 3 | 命中区 | chip 自身 28px 高（<44dp）× 自适应宽（上限 360px）。与右侧 agent preset 席位间距 2px（`heroWorkspaceRow gap:2px`）→ **隔离带不足**（Q-SB-08）。 |
| 4 | 手势 | 单击切换菜单开合（`ConversationContent.tsx:116`）。无长按/双击。 |
| 5 | 行为 | `onClick` 取反 `pickerOpen`；菜单内容由 `conversation.hero.workspace` 槽位占用者渲染（`:119-131`）；选中后 `onPick(workspaceId)`：关菜单 → 乐观置 `pendingWorkspaceId` → `selectWorkspace(id)`，失败则回滚 pending（`:123-128`）。菜单关闭由占用者回调 `onClose`。幂等（开合）。可撤销：切换后无「切回」入口（只能再选一次）。 |
| 6 | 视觉状态 | 静息：透明底、primary 字、folder 关闭图标（无 label 时）/ 打开图标（有 label）；`hover:not(:disabled)` 与 `[aria-expanded=true]`：`interactive-bg-hover` 底（`HeroShell.module.css:169-172`）；`disabled`：`cursor:default`、无 hover、文字保持全对比（锁定引导态，`:174-178`）。chevron caption 色。 |
| 7 | 布局占位与展开形态 | 参与 hero 行 flex，`min-height:28px`；不展开（菜单是浮层，见字段 8）。hero 行本身在输入卡上方 8px（`ConversationRoot.module.css:505-518`）。 |
| 8 | 滚动语义 | chip 不滚；label 单行截断。菜单滚动由占用者负责。**不占位**（G-01）。 |
| 9 | 动效 | hover 底色瞬切；无开合动画。 |
| 10 | 边界态 | 无 label：占位态「选择工作区」+ 关闭 folder 图标（`EmptyHero.tsx:55-58`）；被列表删掉的工作区：`pendingWorkspace` 消失即清 pending（`ConversationContent.tsx:76-84`）；列表加载中：用 cwd 目录名桥接，避免标题闪（`:89-108` 的 5 级解析顺序，注释逐条列出）；多语言：默认工作区名走 `workspaceDisplayTitle` 本地化（`:106-108`）；大字号：13px 固定；横屏：`max-width:100%` 自适应。 |
| 11 | 无障碍 | 原生 button；`aria-label="选择工作区"`（始终是这个标签，**不随当前标题变** — 见反证）；`aria-haspopup="menu"` + `aria-expanded`；图标 decorative；焦点顺序：hero 内第一个可点（输入卡之上）；**`aria-controls` 未见实现**。 |
| 12 | 依据 | `E0` `EmptyHero.tsx:27-62`；`E0` `ConversationContent.tsx:76-134`；`E0` `HeroShell.module.css:140-195`；`E0` `ConversationRoot.module.css:505-530`；`E0` `locales.ts`（`hero.chooseWorkspace`/`workspace.defaultName`）。 |
| 13 | 反证 + 验收 | **反证**：若 chip 的可见标题与 `aria-label` 不一致且 label 不更新（现状：可见「myco-chat」而 aria-label 恒为「选择工作区」），读屏用户听到的操作名与屏幕上的名字不同，无法确认当前在哪个工作区 —— a11y 缺口，见 Q-SB-11。若 chip 在无 label 时也画打开 folder 图标，占位态会看起来像「已经选好了」，用户不会去点（`:55-58` 的图标分支就是这条）。**验收**：`CMP-15` 无/有工作区两种截图：图标与文案成套变化（截图比对）；`CMP-16` 点 chip → 选另一个工作区 → 失败时回滚为原工作区（真机，模拟一次失败）；`CMP-17` 读屏读出当前工作区名（TalkBack 真机，文案待 P2 定）。 |

### 2.5 `composer`（队列 / 计划 / 上下文表）

#### `composer.queue.header`（队列折叠头）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.queue.header`。所属 `composer` → 输入座位上的队列 dock → 折叠头（`QueueDock.tsx:175-193`）。出现条件：`rowCount > 1`（`:175`）—— **只有多项时才出现**；单项时列表直接铺开、没有头（`:144` 的 `listVisible`）。每屏 0..1 个。 |
| 2 | 网格与定位 | dock 宽 = 卡宽 − 2×净空 − 2×dock 内缩，居中，并**向下叠 3px 盖住输入卡顶边**（读成一块连体面，`QueueDock.module.css:4-26`）；头高 36px、`padding:4px 12px`、`gap:10px`、圆角 md（`:61-77`）；chevron 14×14（`:104-111`）。 |
| 3 | 命中区 | 整头 36px 高 × 面板宽（`width:100%`）。**<44dp**；与下方行间距 0（同一面板内），与输入卡重叠 3px → 主要是视觉叠合，不产生误触风险。 |
| 4 | 手势 | 单击切换。`disabled={interactionActive}`（正在编辑或某行 busy 时禁止折叠，`:181`）—— 防止编辑中的列表被折叠走。无长按。 |
| 5 | 行为 | `setCollapsed(v => !v)`。**默认折叠**（`useState(true)`，`:132`）；`expanded = !collapsed \|\| interactionActive`（`:143`）→ 进入编辑/忙态会**强制展开**；`rowCount` 归零时自动折回去（`:135-138`）。幂等。可撤销（再点）。 |
| 6 | 视觉状态 | default：primary 文字、透明底、lead 图标与 chevron 用 tertiary。`aria-expanded` 反映真实展开。`disabled`：`cursor:default`（`:84-86`），无视觉弱化（**未见实现**）。`focus-visible`：`-2px` 内收环（`:79-82`）。方向语义：**折叠时显示上箭头、展开时显示下箭头**（`:189-191`）—— 因为 dock 向上生长（见反证）。 |
| 7 | 布局占位与展开形态 | 参与布局流（`flex:none`），高 36px 恒定；展开时列表在它下面长高（最多 180px，见 `composer.queue.list`）。**近似 G-03 形态 B**（封顶 + 内滚 + 头可收起），但折叠时头本身仍占 36px。 |
| 8 | 滚动语义 | 头不滚。`aria-controls` 指向列表 `id`（`:179`）；列表内滚见下一节点。 |
| 9 | 动效 | 无高度动画（展开/收起是 display 级直切）；chevron 无旋转过渡。 |
| 10 | 边界态 | 单项 → 不渲染头（`:144,175`）；0 项 → 整 dock 不渲染（`:140`）；编辑中 → 头 disabled（不能把正在编辑的行折走）；多语言：`queue.count` 带 `{n}`；大字号：13px 固定；横屏：宽度按卡宽自适应。 |
| 11 | 无障碍 | 原生 button；`aria-controls={listId}` + `aria-expanded`（规范用法）；`role=status` 的「发送中」小字只在折叠且有待入队项时出现（`:186-188`）；无 `aria-label`（用可见文案作名，可接受）；焦点顺序在列表行之前。 |
| 12 | 依据 | `E0` `QueueDock.tsx:104-110,132-144,175-193`；`E0` `QueueDock.module.css:4-111`；`E0` `locales.ts`（`queue.*`）。 |
| 13 | 反证 + 验收 | **反证**：若编辑中允许折叠，行会被 `hidden` 掉而 `<InlineEditor>` 仍持有焦点 → 焦点落进不可见元素，键盘用户「打字进空气」（`:181` 的 `disabled` 就是这条）。chevron 若用常规「展开=下箭头」，在这个向上生长的 dock 里方向感会反（DSH 用 折叠=上 / 展开=下，`:189-191`）—— myco 若改成向下生长的抽屉要一起改，不能只抄箭头。**验收**：`CMP-18` 队列 3 项时头出现、点一下列表收起、再点展开（真机截图）；`CMP-19` 编辑第 2 项时头不可点且列表保持展开（真机）。 |

#### `composer.queue.list`（组节点 · 队列内滚列表与行几何）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.queue.list`。所属 `composer` → 队列面板 → `<ul id>`（`QueueDock.tsx:194-382`）。出现条件：`rowCount >= 1`；`hidden={!listVisible}`（单项或已展开才可见）。每屏 0..1 个。 |
| 2 | 网格与定位 | `max-height:180px; overflow-y:auto; margin/padding:0; list-style:none`（`QueueDock.module.css:113-119`）。行：`min-height:36px`、`padding:4px 5px 4px 12px`、`gap:10px`、圆角 md（`:121-131`）；行间用 `inset 0 1px 0 border-l1` 做分隔线（`:133-135`）；面板背景是菜单面 + backdrop-filter + 0.5px 内描边（`:28-59`）。 |
| 3 | 命中区 | 行本身不可点（无 onClick）—— 只有行内 28×28 动作键可点。行高 36px（<44dp）；动作键 28×28（<44dp）、彼此 `gap:10px`。 |
| 4 | 手势 | 列表内滚（触控板/滚轮）。**未见实现** 行长按菜单、行点击编辑（编辑必须点铅笔键）。 |
| 5 | 行为 | 无导航。行内容 = 附件缩略图/文件卡 + 单行预览（`previewOf` 拼块、`QUEUE_PREVIEW_CHARS=200` 后加「…」，`:18-27`）；编辑态把预览换成 `<InlineEditor>`（`:202-211`）。 |
| 6 | 视觉状态 | 正常行：透明底 + 分隔线。`pendingRow`（本地已提交、Host 行未到）：同样式 + `role=status`「发送中」+ 三个禁用键（`:324-380`）。编辑态行：内容区换成编辑器（行高可增长，`:127-128` 注释）。`disabled`：动作键 `opacity:0.45` + `cursor:default`（`QueueDock.module.css:250-253`）。 |
| 7 | 布局占位与展开形态 | 参与布局流，**封顶 180px 后内滚**（这是 G-03 形态 B 的现成参数来源，见 Q-SB-05）；行高 36px 起、编辑时撑高。 |
| 8 | 滚动语义 | 自身内滚；**本包不设 `overscroll-behavior`**（全仓 grep 无）→ 内滚到边后浏览器默认把滚轮交给外层转录滚动。与 G-04「到边吸收」不一致，见 Q-SB-04。 |
| 9 | 动效 | 无（无行进出动画，`AnimatedRows` 在 `ui-workspace` 才用）。 |
| 10 | 边界态 | 超长预览：单行 ellipsis（`:204-210`）+ 200 字截断；大量附件：附件条 `max-width:55%`、`overflow:hidden`（`:137-144`），文件卡 `flex:0 1 180px; min-width:74px`；缩略图加载失败：保留空占位（`QueueDock.tsx:87-98`）；0 行：整 dock 消失；大字号：13px 固定；多语言：预览用 `projectUserText` 保证用户文本原样。 |
| 11 | 无障碍 | `<ul>` + `<li>`（语义正确）；「发送中」是 `role=status`（会播报）；缩略图有 `alt`（`queue.image`）；文件卡有 `aria-label`；动作键有 `aria-label` + Tooltip（`portal` 挂到 body 以免被面板 overflow 裁掉，`:241-317`）。 |
| 12 | 依据 | `E0` `QueueDock.tsx:18-27,112-140,194-382`；`E0` `QueueDock.module.css:113-253`；`E0` `locales.ts`（`queue.count/sending`）。 |
| 13 | 反证 + 验收 | **反证**：若不封顶内滚，排 20 条队列会把输入卡顶到屏幕外（用户看不见自己在打什么）。若 Tooltip 不 `portal`，面板的 `overflow:hidden` 会把它裁掉半截（`:241-317` 全部带 `portal`，`QueueDock.module.css:31` 就是裁剪源）。若用「点击行=编辑」而不是显式铅笔键，用户想选中行文字时会误入编辑态。**验收**：`CMP-20` 排队 20 条，列表停在 180px 并内滚，输入卡仍在屏上（截图）；`CMP-21` 行内 Tooltip 完整显示不被裁（截图）。 |

#### `composer.queue.row.actions`（组节点 · 28px 圆形动作簇与禁用态）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.queue.row.actions`。所属 `composer` → 队列行 → 动作簇（`QueueDock.tsx:237-320`）。出现条件：`queueMutable`（非子代理，或 continuable 子代理）才渲染（`:129,237`）；每行 0 或 2-3 个键。另在「待入队回显行」渲染 3 个**恒禁用**的同形键（`:350-378`）。 |
| 2 | 网格与定位 | 行尾 flex：`gap:10px`、`flex:none`（`QueueDock.module.css:212-217`）；键 28×28、`border-radius:999px`、透明底、tertiary 图标（`:226-239`）。 |
| 3 | 命中区 | 每键 28×28；键间 10px。**<44dp**；10px 隔离带略高于 Q-03 建议的 8dp（合格）。 |
| 4 | 手势 | 单击。无长按。 |
| 5 | 行为 | 簇内键按状态切换：常态 = 编辑/移除/旁路发送（3 键）；编辑态 = 保存/取消（2 键）；回显行 = 3 键全禁用。所有键在 `busy !== null`（有行操作在飞）时禁用 → 一次只处理一个动作（`:246,257,275,288,306`）。 |
| 6 | 视觉状态 | default：tertiary 图标。hover:not(:disabled)：`interactive-bg-hover` 圆底（`:241-243`）。disabled：`opacity:0.45` + `cursor:default`（`:250-253`）。focus-visible：`-2px` 内收环（`:245-248`）。无 pressed。 |
| 7 | 布局占位与展开形态 | 参与行 flex，`flex:none`；编辑态键数从 3 降到 2 → 内容区变宽（无布局动画）。 |
| 8 | 滚动语义 | 不滚（随行在列表内滚）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | `queueMutable=false`（不可变子代理）→ 整簇不渲染（只剩预览）；`busy` 中 → 全禁用；回显行 → 全禁用 + `title="发送中"`；多语言：每个键都有独立 `aria-label`。 |
| 11 | 无障碍 | 原生 button + `aria-label`；Tooltip `portal`；禁用是原生 `disabled`（不进 Tab 序）。回显行的禁用键仍有 `aria-label`（读屏能听到但不可用，**未见实现** `aria-disabled` 说明原因）。 |
| 12 | 依据 | `E0` `QueueDock.tsx:129,237-378`；`E0` `QueueDock.module.css:212-253`。 |
| 13 | 反证 + 验收 | **反证**：若不按 `busy` 全局禁用，用户连点「移除」两次会发出两个删除请求 —— 第二次针对已删的 id，必然报错（`:150-161` 用 busy 把动作串行化）。若回显行不给禁用键而是直接不渲染，行宽会在 Host 行到达前后跳变（`:657-689` 的注释说明键位是占位）。**验收**：`CMP-22` 连点移除两次只发一次请求（真机 + 网络面板）；`CMP-23` 行从回显态转正式态时动作键位置不动（截图逐帧）。 |

#### `composer.queue.row.edit` / `composer.queue.row.save` / `composer.queue.row.cancel` / `composer.queue.row.remove` / `composer.queue.row.steer`（队列行 5 个动作）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | 5 个原子节点同属 `composer.queue.row.*`。**edit**（`QueueDock.tsx:267-282`）：常态出现，`text === null`（含非纯文本内容）时 disabled 并用 `title` 说明「不支持编辑」。**save**（`:241-251`）：仅编辑态出现，空文本 disabled。**cancel**（`:252-263`）：仅编辑态出现。**remove**（`:283-299`）：常态出现。**steer**（`:300-317`）：常态出现，`!running` 时 disabled 并 `title` 说明「不可旁路」。每行各 1 个。 |
| 2 | 网格与定位 | 统一 28×28 圆形（`.action`，`QueueDock.module.css:226-239`），在行尾动作簇内 `gap:10px`；顺序：编辑/移除/旁路（常态）、保存/取消（编辑态）。 |
| 3 | 命中区 | 各 28×28。**<44dp**；间距 10px。 |
| 4 | 手势 | 单击。编辑保存键支持…**仅点击**：`InlineEditor` 自带 Enter 保存/Escape 取消（由 `ui-primitives` 提供，本包未显式声明 —— **E1**，待 `ui-primitives` 取证）。 |
| 5 | 行为 | edit：`setEditing({id, text})`（只对纯文本内容开放）。save：`applyAction(id, {kind:'edit', content:[{type:'text',text}]})`，成功后退出编辑态，失败 `notify('error', queue.editFailed)` 并**留在编辑态**（`:163-170`）。cancel：`setEditing(null)`（不改任何东西）。remove：`applyAction(id, {kind:'remove'})`，失败 toast。steer：`applyAction(id, {kind:'steer'})`，失败 toast。全部等待 Host 回应后才更新 UI 之外的状态（乐观界面靠 `pendingSubmissions` 回显，`:119-127`）。可撤销：均不可撤销（无 undo）。 |
| 6 | 视觉状态 | 同 `composer.queue.row.actions`：default tertiary / hover 圆底 / disabled `opacity:0.45`；`busy` 时全簇禁用。`title` 承载「为何不可用」的两条说明（edit 不支持、steer 非运行态）。 |
| 7 | 布局占位与展开形态 | 各键固定 28×28；不展开。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 内容含图片/文件块 → edit disabled（`textOf` 返回 null，`:29-32`）；非运行态 → steer disabled；失败 → toast + 状态回退（编辑保持、动作可重试）；多语言：`queue.edit/remove/steer/save/cancelEdit` + 两条 unsupported/unavailable 文案；大字号：图标键不变。 |
| 11 | 无障碍 | 原生 button + `aria-label`；Tooltip 显示标签；disabled 时 Tooltip `disabled`，改用原生 `title`（源码注释说明「禁用按钮不触发 hover 事件」，`:272-274`）；焦点序按 DOM（编辑→移除→旁路 / 保存→取消）。 |
| 12 | 依据 | `E0` `QueueDock.tsx:29-32,146-170,237-320`；`E0` `QueueDock.module.css:212-253`；`E0` `locales.ts`（`queue.*`）。 |
| 13 | 反证 + 验收 | **反证**：若编辑保存失败也退出编辑态，用户刚打的修改就**凭空消失**且不知道失败（`:163-170` 只在成功时 `setEditing(null)`）。若允许编辑含图片的队列项，保存时会用纯文本 `content` 覆盖整条消息 → 图片被静默删掉（`textOf` 的 null 分支就是这条防线）。若 steer 在非运行态可点，后端会拒绝并给用户一个无意义报错。**验收**：`CMP-24` 断网保存队列编辑 → toast 且内容仍在编辑器里（真机）；`CMP-25` 含图队列项编辑键 disabled 且 title 解释（截图）；`CMP-26` 非运行态旁路键 disabled（截图）。 |

#### `composer.todo.header`（计划面板头）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.todo.header`。所属 `composer` → 输入座位上的计划 dock → 折叠头（`TodoPanel.tsx:72-84`）。出现条件：`todos.length > 0`（`:67`，空则整面板不渲染）。每屏 0..1 个。 |
| 2 | 网格与定位 | 面板根：宽 = 卡宽 − 4×dock 内缩、`margin:0 auto`、圆角 lg、菜单面 + backdrop-filter + panel 阴影；body `gap:8px`、`padding:6px 12px`（`TodoPanel.module.css:1-40`）；头：`gap:10px`、`padding:0`（`:42-52`）；标题 13/24/500、进度 13/20 tertiary（`:61-79`）；chevron 与队列头同一竖线（12px 右内边距对齐，见 `QueueDock.module.css:68-70` 注释）。 |
| 3 | 命中区 | 整头宽 × 约 24-28px（由内容行高决定，无固定高度）。**<44dp**；与列表首行间距 8px。 |
| 4 | 手势 | 单击切换。无长按/双击。 |
| 5 | 行为 | `setCollapsed(v => !v)`。**默认折叠**（`useState(true)`，`:66`）。无「强制展开」逻辑（与队列头不同 —— 队列在编辑时强制展开，计划面板没有交互态所以不需要）。幂等。可撤销。 |
| 6 | 视觉状态 | default：透明底；`aria-expanded={!collapsed}`；标题 primary、进度 tertiary；chevron 折叠=上箭头 / 展开=下箭头（`:81-83`，与队列头同一方向语义）。hover/focus/disabled：**未见实现**（无 `:hover`/`:focus-visible` 规则 —— 见反证）。 |
| 7 | 布局占位与展开形态 | 参与布局流，头高随内容；展开时列表向下长（最多 180px，见下）。**近似 G-03 形态 B**。 |
| 8 | 滚动语义 | 头不滚；列表内滚。 |
| 9 | 动效 | 无（display 直切）。 |
| 10 | 边界态 | 0 项 → 整面板消失（`:67`）；进度文案按状态计数拼「·」分隔、0 计数段省略（`:51-63`，用 U+2002 宽空格避免 HTML 折叠）；多语言：标题/三种状态名/进度模板均本地化；大字号：13px 固定；横屏：宽度自适应。 |
| 11 | 无障碍 | `<section aria-label="计划">`（`:70`）；头是原生 button + `aria-expanded`；列表项状态点 `role="img"` + `aria-label`（完成/进行中/待办）—— 视觉符号有人工语义（`:89-91`）；**无 `aria-controls`**（不关联列表 id，见反证）；无 `aria-live`（计划变化不播报）。 |
| 12 | 依据 | `E0` `TodoPanel.tsx:24-49,51-63,65-100`；`E0` `TodoPanel.module.css:1-123`；`E0` `locales.ts`（`todo.*`）。 |
| 13 | 反证 + 验收 | **反证**：若头不给 hover/focus 反馈（现状如此），用户不知道这行能点开 —— 而计划内容默认是藏起来的（默认折叠），等于新功能不可发现（`:42-52` 无任何交互样式）。若状态点不加 `aria-label`，读屏用户只听到任务文本、不知道做没做完（完成/进行中/待办三种状态在视觉上只是颜色/形状）。**验收**：`CMP-27` 计划出现时默认可点开、头有 hover 反馈（真机 + 截图，样式待 P2 定）；`CMP-28` 读屏逐项报出状态（TalkBack 真机）。 |

#### `composer.todo.list`（组节点 · 计划展开体与内滚）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.todo.list`。所属 `composer` → 计划面板 → 展开体（`TodoPanel.tsx:85-96`）。出现条件：`!collapsed && todos.length > 0`。每屏 0..1 个。 |
| 2 | 网格与定位 | `max-height:180px; overflow-y:auto`、纵向 `gap:8px`、`list-style:none`（`TodoPanel.module.css:88-97`）；行 `gap:10px`、13/20 secondary（`:99-107`）；状态点格 16×16（`:109-115`）；文本单行 `ellipsis`、**无行内展开**（`:117-123` 注释明说）。 |
| 3 | 命中区 | 行不可点（无 onClick）—— 纯展示列表。行高 20px 起（远 <44dp，但不可交互所以不违反命中区规则）。 |
| 4 | 手势 | 仅列表内滚。**未见实现** 勾选/改状态/排序/跳转（计划是只读投影）。 |
| 5 | 行为 | 无（只渲染投影 `todos`）。 |
| 6 | 视觉状态 | 行按 `data-status` 上色；状态点由 `StateDot` 画 done/ongoing/idle；无 hover/selected。 |
| 7 | 布局占位与展开形态 | 参与布局流；**封顶 180px 内滚**（G-03 形态 B 参数来源之一）。 |
| 8 | 滚动语义 | 自身内滚，无 `overscroll-behavior` → 到边交浏览器默认（同 Q-SB-04）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 超长条目 → 单行 ellipsis（**不能展开看全** —— 见反证）；大批任务 → 180px 内滚；0 项 → 整面板不渲染；多语言/大字号：13px 固定。 |
| 11 | 无障碍 | `<ul>/<li>`；状态点 `role="img"` + 状态 `aria-label`；文本是普通文本；无列表级 `aria-label`（靠 section 的）。 |
| 12 | 依据 | `E0` `TodoPanel.tsx:30-49,85-96`；`E0` `TodoPanel.module.css:88-123`。 |
| 13 | 反证 + 验收 | **反证**：若长任务文本用单行 ellipsis 且不提供任何展开（现状），用户永远看不到计划条目的后半句 —— 而计划正是「要做什么」的契约，读不全等于没有；至少要给 `title` 或点击展开（Q-SB-06）。**验收**：`CMP-29` 超长计划条目可读到全文（真机/截图，形态待 P2 定）；`CMP-30` 20 条计划时列表 180px 内滚，输入卡不被顶走（截图）。 |

#### `composer.context-meter.trigger`（上下文占用环形触发键）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.context-meter.trigger`。所属 `composer` → 输入卡下方 dock → 上下文表（`ContextMeter.tsx:108-132`）。出现条件：`contextOccupancy(pressure) !== null`（需要 provider 同时报 pressure 与路由容量，`:62-63,89`）。每屏 0..1 个。 |
| 2 | 网格与定位 | `dock`：flex 居中、`gap:12px`、`padding-top:4px`（`InputBar.module.css:13-20`）；触发键：`inline-flex`、`gap:6px`、`padding:1px 8px`、圆角 sm、secondary 字号 13/20、`tabular-nums`（`ContextMeter.module.css:9-25`）；环形 svg 14×14、viewBox 14、`r=5.5`、stroke 2（`ContextMeter.tsx:17-19`），`strokeDasharray` 表达百分比、`rotate(-90)` 从 12 点起画（`:119-129`）。 |
| 3 | 命中区 | 高度约 22px（1px padding ×2 + 20px 行高），宽度随「nn%」。**<44dp**，且是最小的常驻可点元素之一（Q-SB-08）。 |
| 4 | 手势 | 单击切换面板。hover 无长延迟 Tooltip（200ms，`:110`）。无长按。 |
| 5 | 行为 | `setOpen(!open)`。渲染为 **portal 到 `document.body`** 的浮层（见下一节点）。占用不可用时自动关闭（`:76-78`）。幂等。可撤销（点外/Escape/再点）。 |
| 6 | 视觉状态 | default：tertiary 文字 + tertiary 环形（track 用 `border-l3`、fill 用 `label-tertiary`）。hover 与 `[aria-expanded=true]`：`interactive-bg-hover` 底 + secondary 字（`:27-31`）。`disabled`：无。 |
| 7 | 布局占位与展开形态 | 参与 dock 流式排布（`flex:none`）；**展开的面板是浮层，不占位（G-01 合规）**。 |
| 8 | 滚动语义 | 不滚；面板自身不滚（内容固定 3-4 行）。 |
| 9 | 动效 | 环形是静态（无动画）；面板定位由 `useAnchoredPosition` 计算（`open` 时才算，`:64-71`），无入场动画。 |
| 10 | 边界态 | 无 provider → 整键不渲染；切换模型导致容量暂缺 → 面板自动关闭且键消失（`:74-78`）；百分比格式化：`<1k` 直接数字、`<1M` 用 K、否则 M，且 100 以上取整、以下保留 1 位小数（`:41-48`）；多语言：aria-label 是整句「上下文已用 nn%」（`:92-94,110,114`），**文案由 locale 决定词序**，中间用 `\0` 占位切分（`:21-26,92-94`）；大字号：跟随 `--dsh-content-font-size-secondary` 与 `font-delta`（`:20-22`）。 |
| 11 | 无障碍 | 原生 button；`aria-label` = 完整句子；`aria-haspopup="dialog"` + `aria-expanded`（`:115-116`）；Tooltip 复用同一句（`:110`）；焦点顺序：dock 内（在队列/计划之后、输入卡之外）。 |
| 12 | 依据 | `E0` `ContextMeter.tsx:17-19,41-48,56-132`；`E0` `ContextMeter.module.css:4-44`；`E0` `InputBar.module.css:13-20`；`E0` `context-occupancy.ts`。 |
| 13 | 反证 + 验收 | **反证**：若百分比只画环不给数字，用户无法判断「还能不能塞下一轮」；若只给数字不给环，在小尺寸下数字本身难扫读（两者都给：`:119-131`）。若在容量未知时硬画一个 0% 环，用户会以为「上下文全空」——所以无数据时整键不渲染（`:89`）。**验收**：`CMP-31` 45% 占用截图：环弧与数字一致（截图比对）；`CMP-32` 切到无容量的模型后该键消失且面板已关（真机）。 |

#### `composer.context-meter.panel`（上下文明细浮层 + Escape）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.context-meter.panel`。所属 `composer` → 上下文表 → 明细浮层。出现条件：`open && available`；`createPortal(..., document.body)`（`ContextMeter.tsx:133-177`）。每屏 0..1 个。 |
| 2 | 网格与定位 | `position:fixed`、`z-index:1100`、`width: min(264px, calc(100vw - 24px))`、`padding:12px`、圆角 lg、菜单面 + backdrop-filter + prominent 阴影（`ContextMeter.module.css:46-62`）；定位由 `useAnchoredPosition({side:'top', gap:8, margin:12})` 贴触发键上方（`ContextMeter.tsx:64-71`），未算出前用 `visibility:hidden` 防闪（`:137`）。内部：header 行、4px 分段条（`margin:10px 0 12px`，段 `min-width:2px`）、`<dl>` 三行（`:64-154`）。 |
| 3 | 命中区 | 面板本身是可点区（`cursor:default`，不误触）；内部无可点元素（纯展示）。行高 `padding:2px 0`。由 `useDismissOnOutsidePointer` 判定「点面板内不算外部」（`:72`）。 |
| 4 | 手势 | 关闭方式三选一：点外部（`useDismissOnOutsidePointer`，`:72`）、`Escape`（document 级 keydown，`:80-87`）、再点触发键。无滑动手势。 |
| 5 | 行为 | 纯展示：`contextBreakdown` 把总量按 system/tools/messages **比例**切分，但整条长度仍用 provider 精确百分比（`:96-106`）——即「比例是启发式的，总量是精确的」。零宽段被丢弃而不是渲染成 2px 发丝（`:97-99` 注释：0% 时会画出一条假填充）。可撤销：无（无状态变更）。 |
| 6 | 视觉状态 | `role="dialog" aria-label="上下文已用"`；headline 两侧文字在 locale 留空时用 `:empty{display:none}` 收起（避免空 gap，`ContextMeter.module.css:86-90`）；百分比 primary 500、`figures` 右对齐 `~used / window`（`:70-80`）；三段颜色：neutral-bluish / 紫 literal（设计平台没有紫色 token，`:125-128` 注释）/ 蓝。 |
| 7 | 布局占位与展开形态 | **浮层，完全不占位**（G-01 / G-03 形态 C 的实例）；`fixed` 定位在 body 上，不受输入座裁剪。 |
| 8 | 滚动语义 | 自身不滚；`min(264px, 100vw-24px)` 保证窄屏不溢出。 |
| 9 | 动效 | 无入场/退场动画；`visibility:hidden` 到算好位置是瞬时。 |
| 10 | 边界态 | 窄屏：宽度取 `100vw-24px`，`margin:12` 保证不出界；`breakdown` 缺失时只画一条总量条、不画三行 legend（`:100-106,162-174`）；模型切换中途容量消失 → 自动关闭（`:76-78`）；多语言：headline 词序由 locale 决定（`\0` 占位切分）；大字号：12px 固定。 |
| 11 | 无障碍 | `role="dialog"` + `aria-label`，但**没有焦点管理**：打开后焦点不进入面板、关闭后不归还触发键、面板内无 `tabindex`；`Escape` 是 document 级监听（`:85`）→ 任何焦点位置都能关（可用，但不是标准 dialog 语义）；数值是 `<dl>/<dt>/<dd>`（语义正确）；无 `aria-live`（开关不播报）。 |
| 12 | 依据 | `E0` `ContextMeter.tsx:56-107,133-177`；`E0` `ContextMeter.module.css:46-154`；`E0` `ui-primitives`（`useAnchoredPosition`/`useDismissOnOutsidePointer`）；`E1` 注释称「~ 表示 usedTokens 优先 projectedTokens、表面增量是启发式重定价」（`:147-149`）。 |
| 13 | 反证 + 验收 | **反证**：若三段的宽度用「原始 token 数占 contextWindow 的比例」而不是「按 breakdown 内部比例缩放整条」，由于 breakdown 是启发式估算，三段之和会与精确的百分比不一致 → 条形长度和旁边数字互相打脸（`:96-102` 注释就是这条）。若 0 占比段仍按 `min-width:2px` 渲染，0% 时会画出一条假填充，看起来「已经用了」。若面板不用 `fixed` 而挂进输入座，输入座的 `overflow`/sticky 会把它裁掉。**验收**：`CMP-33` 展开明细面板，条长与数字一致、无 0% 假填充（截图比对）；`CMP-34` Escape 与点外部都能关（真机）；`CMP-35` 320px 宽视口下面板不出界（截图）。 |

### 2.6 `settings.general`（跨包设置行）

#### `settings.general.enter-behavior.selector`（忙时 Enter 行为选择器）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.general.enter-behavior.selector`。所属 `settings.general` 面板 → 设置项行（`EnterBehaviorRow.tsx:39-75`；注册进 `settings.general.item` 槽位）。出现条件：设置页挂载即出现 1 个。 |
| 2 | 网格与定位 | 行：`rowText`（标题 13/20 500 + 描述）在左，`Menu` 锚点按钮在右（`align="end"`，`portal`，`:59-72`）；按钮是 `.selector` 样式（`EnterBehaviorRow.module.css:47` 有 `cursor:pointer`）。行内布局细节由 `ui-settings-general` 的行容器决定（跨包，**E1**）。 |
| 3 | 命中区 | 锚点按钮高约 28px、宽随文案（「排队 / 旁路发送」）→ **<44dp**。行内右侧，无相邻可点元素（隔离足够）。 |
| 4 | 手势 | 单击开菜单；菜单内项单击选中并关闭。无长按。 |
| 5 | 行为 | `setOpen(v=>!v)` 开合；`onSelect(id)` → 先关菜单再 `setBusyEnter(id)`（`:55-58`，顺序固定：先关再写，避免菜单跟着状态重渲染）。写的是持久偏好（`SnapshotStore<BusyEnterBehavior>` 由注册侧提供，`:11-18`）。幂等。可撤销（改回另一项）。 |
| 6 | 视觉状态 | default：继承设置行样式；`aria-haspopup="menu"` + `aria-expanded`；菜单选中项由 `selectedId` 高亮（`:54`）。hover/focus/disabled：`Menu` 与设置行样式由跨包提供（本包只给 `.selector` 的 cursor，`EnterBehaviorRow.module.css:47`）。 |
| 7 | 布局占位与展开形态 | 参与设置行布局（右对齐，`flex` 内容）；菜单是 portal 浮层，**不占位**（G-01）。 |
| 8 | 滚动语义 | 不滚（设置页整体滚动由 `ui-settings` 承担）。 |
| 9 | 动效 | 无（菜单动画由 `ui-primitives` 的 Menu 提供）。 |
| 10 | 边界态 | 两项互斥（`queue`/`steer`），无第三态；缺失注入 → 组件不会渲染（`useBusyEnter` 是必需注入）；多语言：标题/描述/两项文案均本地化；大字号：13px 固定；横屏无特殊处理。 |
| 11 | 无障碍 | 原生 button + `aria-haspopup="menu"` + `aria-expanded`（`:64-67`）；菜单键盘由 Menu 组件提供（**E1**，待 `ui-primitives` 取证）；读屏名 = 当前选中项文案（不是字段标题 —— 见反证）。 |
| 12 | 依据 | `E0` `EnterBehaviorRow.tsx:26-32,39-75`；`E0` `EnterBehaviorRow.module.css:47`；`E0` `input/submission-policy.ts`（`BusyEnterBehavior` 的消费方）；`E1` 行容器与菜单键盘语义属 `ui-settings-general` / `ui-primitives`（跨包）。 |
| 13 | 反证 + 验收 | **反证**：若按钮的可访问名只有「排队 / 旁路发送」而不含字段名「忙碌时 Enter 的行为」，读屏用户在一长串设置行里听到三个同样的「排队」控件就分不清（Q-SB-11 的同类）。若 `onSelect` 不先关菜单，`setBusyEnter` 引起的重渲染会让 Menu 在关闭动画中途收到新 `selectedId`，出现「选中项先跳一下再关」。**验收**：`SG-01` 切换两项后重载设置页，选择保持（真机截图）；`SG-02` 读屏读出行标题 + 当前值（TalkBack 真机，文案待 P2 定）。 |

---

## 3. 排除清单

### 3.1 汇总

| 类别 | 条数 | status | 处理 |
|---|---|---|---|
| CSS 结构性属性（`css.cursor/focus/overflow/sticky/contain`） | **64** | excluded | 逐行理由写在 facts.csv 的 `note` 列，并注明「归属哪个节点」；不构成独立交互点 |
| CSS 路径下的「样式钩子」 | **2** | excluded | `InputBar.module.css:356`（`.select` 给跨包模式 chip 用）、`InputBar.module.css:410`（`.retry` 在本包 src 无使用者） |
| 单元/组件测试文件 | **41** | excluded | `ui-sidebar/tests` 2 条 + `ui-conversation/tests` 39 条；断言不是产品 UI，且测试里出现的行为已在 src 侧节点取证 |
| 非交互命中（注释 / 类型 / DOM 选择器字符串） | **4** | excluded | 见 3.4 |
| **合计** | **111** | | 与 facts.csv 的 `excluded` 计数一致 |

### 3.2 CSS 结构性属性（64 条，按文件）

> 行级理由在 `docs/spec/ref/dsh-sidebar.facts.csv`；每条都写了「结构性样式，见 <节点>」。
> 这些行**不是交互点**，但它们是节点 §2（几何）/§6（视觉状态）/§8（滚动）的证据来源，所以没有被丢掉。

| 文件 | 条数 | 承载的节点事实 |
|---|---|---|
| `ui-sidebar/src/client/SidebarRoot.module.css` | 14 | 列几何（`sidebar.shell.column`）、toggle/新建会话/面板行的光标与焦点环、标签截断、`regionArea` 裁剪 |
| `ui-conversation/src/client/skeleton/ConversationRoot.module.css` | 12 | 面包屑裁剪/光标、视图 tab 光标、转录滚动容器与 `scrollbar-gutter`、输入座 sticky + 36px 渐隐、overlay/hero/settling 相位 |
| `ui-conversation/src/client/skeleton/InputBar.module.css` | 10 | 输入卡、草稿内滚（14 行上限）、placeholder 单行、触发态光标、`+`/主键几何、`.select`/`.retry` 钩子 |
| `ui-conversation/src/client/queue/QueueDock.module.css` | 10 | 面板裁剪、头 36px、列表 180px 内滚、附件/文件/预览截断、动作键 28px 与焦点环 |
| `ui-sidebar-browser/src/client/view/Browser.module.css` | 7 | 工具键光标、地址框 focus-within 显隐、恢复卡片滚动、unknown 提示单行 |
| `ui-conversation/src/client/skeleton/TodoPanel.module.css` | 4 | 面板根裁剪、头光标、进度截断、列表 180px 内滚、条目单行 |
| `ui-conversation/src/client/skeleton/ContextMeter.module.css` | 2 | 触发键光标、分段条裁剪 |
| `ui-conversation/src/client/input/editor/{ReferenceChip,composer-editor}.module.css` | 2 | chip 标签截断、`.openable` 光标（**只挂类名、无点击处理**） |
| `ui-sidebar/src/client/HeaderLeadingControls.module.css` | 1 | 窗口 chrome 席按钮光标 |
| `ui-conversation/src/client/skeleton/HeroShell.module.css` | 2 | 工作区 chip 光标与标签降级 |
| `ui-conversation/src/client/settings/EnterBehaviorRow.module.css` | 1 | 选择器光标 |

### 3.3 测试文件（41 条）

`ui-sidebar/tests/panel-list.client.spec.tsx`（2）、`ui-conversation/tests/{assembly-surfaces,context-meter,enter-behavior-row,input-bar,queue-dock,skeleton}.client.spec.tsx`（39）。
排除理由：**测试用例不是产品 UI**，且它们断言的行为都已经在 `src/` 侧对应节点取证（如 queue 的 `aria-expanded` 在 `QueueDock.tsx:180`）。
副作用记录：测试文件里的「假按钮/假 input」会让分母虚高（本路 41/201 = 20%），建议 `ui_audit.py` 默认跳过 `tests/`、`*.spec.*`（见 §3.6）。

### 3.4 非交互命中（4 条）

| 位置 | kind | 真实内容 | 理由 |
|---|---|---|---|
| `ui-conversation/src/client/skeleton/InputBar.tsx:286` | `aria.role` | `target.closest('button, a, input, select, textarea, [role="button"], ...')` | DOM 选择器字符串，用于鼠标按下时排除可点控件；不是事件绑定 |
| `ui-conversation/src/client/skeleton/EmptyHero.tsx:35` | `jsx.onclick` | JSDoc 注释 `@param props.onClick - menu toggle.` | 注释行，无运行时行为 |
| `ui-conversation/src/client/skeleton/EmptyHero.tsx:38` | `jsx.onclick` | 函数签名解构 `{ ..., onClick, ... }` | 仅出现形参名 |
| `ui-conversation/src/client/skeleton/EmptyHero.tsx:42` | `jsx.onclick` | 类型声明 `onClick?: () => void` | 纯类型 |

> 真实控件在 `EmptyHero.tsx:46`（`<button`）与 `:53`（`onClick={onClick}`），已按 E0 归到 `composer.workspace-chip`。

### 3.5 「空槽」——本包只声明、占用者在别的包（未见实现）

这些是**槽位（slot）**，四个包内只看到声明与 fallback，真正的控件在同包其它件或别的包。
本路**没有**为它们编节点（没有可审计的原子目标）；列出来是为了让 P2 知道还有哪些控件要从别的路取。

| 槽位 | 声明/渲染处 | 占用者（已知） | 本路看到的 fallback |
|---|---|---|---|
| `sidebar.brand.mark` / `sidebar.brand.name` | `SidebarRoot.tsx:189,225,228` | 品牌包（部署可替换） | 鲸鱼 logo / 「本地构建」+ 版本号徽标 |
| `sidebar.toggle.badge` | `SidebarRoot.tsx:194` | 通知类 | 无 |
| `sidebar.panellist` | `SidebarRoot.tsx:71` | 主面板插件（`ui-workspace` 等） | 无（空则整组 `nav` 不渲染） |
| `sidebar.workspaces` | `SidebarRoot.tsx:298` | **`ui-workspace`** | 无 |
| `sidebar.settings` / `sidebar.footer.action` | `SidebarRoot.tsx:307,310` | `ui-settings` / 可选插件 | 无 |
| `conversation.header.leading` | `ConversationHeader.tsx:19-21` | 未在本包 | 无 |
| `conversation.session.header.actions/.utilities/.corner/.lineage` | `ConversationSession.tsx:127-138,104-121` | 未在本包 | `null`（`.corner:empty{display:none}`） |
| `conversation.input.{overlay,attachments,permission,plan,left,right,model,activity}` | `InputBar.tsx:404,407-418,464-479` | `ui-attachment` / `ui-permission-presets` / `ui-plan` / `ui-model-selection` 等 | 无 |
| `conversation.composer.dock` | `InputBar.tsx:520-523` | 本包内 `queue`/`todo` 两个 dock | 无 |
| `conversation.view` | `DefaultConversationViews.tsx:36-41` | `ui-chat` 等视图包 | 无 |
| `conversation.composer`（chain，含 takeover overlay） | `ConversationContent.tsx:172-176` | 业务包（trajectory 等） | 输入卡 fallback |

### 3.6 扫描器盲区（工具问题，影响整个 P1 分母）

本路有 **5 个节点在 `ui_audit.py` 里 0 命中**，原因是 `PATTERNS` 只认 `onPointerDown/Up/Move/Cancel`、不认 `onPointerEnter/Leave`，也不认 `onMouseDown` / `onKeyDownCapture`：

| 节点 | 真实交互 | 为何漏 |
|---|---|---|
| `sidebar.shell.column` | `onPointerEnter/onPointerLeave` + document `pointermove`（滚动条可见性） | `jsx.onpointer` 正则不含 Enter/Leave |
| `composer.input.card` | `onMouseDown`（点空白处把焦点还给编辑器） | 无 `jsx.onmousedown` 规则 |
| `composer.input.seat` / `chat.list.scrollport` | 粘贴/滚动/`scrollbar-gutter`（无事件属性） | 只能靠 CSS/容器语义识别 |
| `composer.input.draft-surface` 的滚轮链 | `addEventListener('wheel', ..., {passive:false})` | 规则只认 JSX `onWheel` |

**建议**（给工具，不是给 spec）：`PATTERNS` 增补 `onPointerEnter|onPointerLeave`、`onMouseDown|onMouseUp`、`addEventListener\('(wheel|keydown|pointer)'`，并把 `tests/`、`*.spec.*` 加入 `SKIP_DIRS`。否则每一路都会花大量时间解释「为什么有节点没命中」，而且分母里约 20% 是测试噪音。

---

## 4. 待确认（E2 候选）

> 以下每条都**不是** spec 结论，只是「源码给出了两种都说得通的做法 / 源码没写清」。
> 按 README 规则，任何一条未关闭，对应节点不得进入实现。编号自 Q-SB-01 起。

### Q-SB-01 侧栏在手机上到底是「列」还是「覆盖抽屉」？（本路最高优先级）

- **冲突**：用户要的「无限抽屉」是**层叠覆盖**；而 DSH 侧栏在四个包里的唯一形态是**占布局的网格列**（`AppFrame.tsx:265-266`、`columns.ts:52-58`），窄屏展开靠**挤压**中心列（可压到 0），没有遮罩、没有手势、没有返回键（`ui-sidebar/src` grep 0 命中）。
- **证据**：§2.1 `sidebar.shell.column` 字段 4/7/10；§0.2 ① ② ③ ④；`AppFrame.tsx:160-174,265-266,301`；`AppFrame.module.css:15-17,58-63`；`SidebarRoot.module.css:31-36,39-55,82-86`；`stores.ts:106-122`。
- **候选**：
  1. **移动端=覆盖抽屉（带遮罩 + 边缘手势 + 返回键/Escape 关闭），宽屏=常驻列（可拖宽 264-420）**。DSH 只提供宽屏列部分作为依据，移动端行为是 myco 自定（需 E2→user 升级为 E0）。
  2. 全平台照抄 DSH：窄屏 = 56px rail + 挤压展开。好处是 100% 有据；坏处是与「无限抽屉」的产品方向冲突、且手机屏窄时把会话压到 0 宽不可用。
  3. 窄屏 = 覆盖抽屉但不做手势，只做按钮开合（最小实现）。
- **建议**：候选 1。理由：`10-panels.md` 把抽屉壳归 `base.layout` 且明确「层叠覆盖 + 无可见抓手」，与 DSH 列语义不同；DSH 的 264-420/56/1024 三个数可以原样借给宽屏形态，但**不能**把「挤压」搬上手机。
- **影响**：`sidebar.shell.*` 全部节点的字段 4/7/10；`SB-02`；以及 `base.layout` 的层叠模型（**已被 R1a 的 Q-BASE-02 覆盖，本条只裁定侧栏这一列自己的形态，不重复问层叠栈**）。

### Q-SB-02 会话行 / 列表 / 搜索 / 多标签：本路写作用域外，缺一路审计

- **冲突**：`10-panels.md:14` 把 `sidebar` 面板证据包指成这四个包，但真实实现（行、分组、搜索、行菜单、拖拽排序、右侧栏多标签）在 `ui-workspace` / `ui-dockkit` / `ui-sidebar-right`。本路按任务只登记 `ui-sidebar|ui-sidebar-browser|ui-conversation|ui-session` 前缀的 facts 键，**不能**替别的包写事实库（会与并行路的键冲突）。
- **证据**：§1.2 A/B/C/D 四张指针表（全部带 `path:line`）。
- **候选**：①新增 R1e 专审 `ui-workspace`（会话行+列表+搜索）与 `ui-sidebar-right`+`ui-dockkit`（多标签）；②把 `10-panels.md:14` 的证据包改成「`ui-workspace` + `ui-sidebar` …」并在注册表里把 `sidebar` 拆成 `sidebar.shell` / `sidebar.list` 两行。
- **建议**：①+②同时做。理由：这是 P1 分子分母的真实缺口 —— 按 §1.2 的指针量估算，会话行+列表+搜索+行菜单至少还有 40-60 个交互点没被任何一路计入。
- **影响**：`sidebar` 面板的 P2 对照表无法开工（行/列表是用户最关心的部分）。

### Q-SB-03 全局面板行：选中态与 hover 态视觉相同，怎么区分？

- **冲突**：`.panelRow:hover` 与 `.panelRow.panelActive` 用同一个 `--dsw-alias-interactive-bg-hover` 底色（`SidebarRoot.module.css:510-517`），仅 `aria-current="page"` 区分语义。
- **证据**：§2.1 `sidebar.shell.panel-row` 字段 6/13。
- **候选**：①加左侧 2-3px 指示条（在 hover 上再叠一层）；②选中态用更深的 `bg-hover-solid`；③选中态文字加粗/提色。
- **建议**：①，因为它与 hover 正交、不依赖颜色对比，且在窄屏 rail 态也能用（rail 下没有文字）。
- **影响**：`SB-04`；`base.primitives` 的行样式契约。

### Q-SB-04 内滚到边：吸收（G-04）还是链式转发（DSH 现状）？

- **冲突**：G-04 是**用户已拍板**的铁律：「内滚到边缘不许把残余速度传给外层」。但 DSH 在本路有**三处**明确做链式转发：
  ① 草稿滚轮到边 → `e.preventDefault()` 后手动把 delta 加到外层 `[data-conversation-scroll]`（`view-binding.ts:65-79`）；
  ② 宽度把手滚轮 → 直接 `scrollport.scrollBy`（`ConversationWidthControls.tsx:105-114`）；
  ③ 队列/计划列表 180px 内滚**完全不设** `overscroll-behavior`（全仓 grep 无命中）→ 到边由浏览器默认外溢。
- **证据**：§2.3 `chat.list.scrollport` 字段 8；§2.4 `composer.input.draft-surface` 字段 8；§2.5 `composer.queue.list` 字段 8。
- **候选**：①严格 G-04：内滚容器全部 `overscroll-behavior: contain` + 触控手势吸收，**但**草稿的鼠标滚轮保留链式（因为鼠标用户滚到输入框边缘后继续滚对话是桌面惯例，且没有惯性语义）；②全平台一律吸收（按 G-04 字面）；③全平台一律链式（照 DSH）。
- **建议**：①。理由：G-04 的反证（「一次手势=一次意图」）针对的是**惯性/拖动**，而 DSH 三处全是**滚轮**（离散事件，无残余速度）；两者并不真冲突，但 spec 里必须把「滚轮」与「惯性手势」分开写，否则实现会按字面把滚轮也吸收掉，桌面体验反而变差。
- **影响**：G-04 措辞需要补一句适用范围；`CHR-05`/`CMP-05`；以及 `dsh-chat` 路的滚动跟随结论（可能已有重叠，合并时去重）。

### Q-SB-05 队列/计划 dock 的 180px 封顶，能否作为 Q-05「过程组 cap」的取值依据？

- **冲突**：Q-05 卡在「过程组封顶取多少」，现有 45%/360dp 被标为 E2。本路发现 DSH 有两处**同为 180px** 的封顶内滚列表：队列（`QueueDock.module.css:113-119`）与计划（`TodoPanel.module.css:88-97`）；而输入草稿的封顶是 **336px**（14 行 × 24px，`ConversationRoot.module.css:346-358`）。
- **证据**：§2.5 `composer.queue.list` 字段 7；§2.5 `composer.todo.list` 字段 7；`ConversationRoot.module.css:350-357`。
- **候选**：①过程组 cap 直接取 180px（与 DSH 两个 dock 一致）；②取 240dp（G-10 的动画阈值，两个数不同维度不能混）；③仍按屏高比例（现状 E2）。
- **建议**：**不能**直接用 180px 给过程组：180px 是「输入区附属小列表」的尺寸，过程组挂在消息流里，约束不同（源码里没有给过程组 cap 的证据，`use-process-scroll.ts` 需要另取）。但**可以**把「180px 固定值 + 内滚 + 到边渐隐」记成一类 E0 的形态 B 实现样本，供 Q-05 讨论时对照。
- **影响**：Q-05；（若采纳）过程组与工具组的滚动契约。

### Q-SB-06 计划（Todo）条目单行 ellipsis、不给任何展开入口

- **冲突**：`TodoPanel.module.css:117-123` 注释明写「Figma strip is single-line; long items ellipsize with **no inline expand**」→ 长任务永远读不全；而计划恰恰是「要做什么」的契约。
- **证据**：§2.5 `composer.todo.list` 字段 10/13。
- **候选**：①保持单行 + 加 `title`（原生 tooltip，零成本）；②单行 + 点击展开为两行（形态 A）；③整个计划面板改成 sheet（形态 C）。
- **建议**：①做基线（成本最低、不改变密度），把②留作用户明确要求时的增强。
- **影响**：`CMP-29`；`composer.todo.list` 的展开形态。

### Q-SB-07 会话视图 tab 声明了 `role=tablist/tab`，但没有方向键/roving tabindex

- **冲突**：`ConversationSession.tsx:143-155` 用了完整的 tab ARIA 角色（`role=tablist`、`role=tab`、`aria-selected`），但：无方向键处理、无 roving tabindex、无 `aria-controls`/`id` 关联、容器无 `aria-label`。ARIA APG 的 tab 契约要求前三项。
- **证据**：§2.3 `chat.header.tab-strip`/`view-tab` 字段 4/11。
- **候选**：①补齐 ARIA（方向键 + roving tabindex + `aria-controls`）；②降级为非 tab 语义（一排 `aria-current` 按钮，像全局面板行那样）；③照抄 DSH（声明了但不实现）。
- **建议**：①。若为省成本，②也比③好 —— 声明了却不响应键盘，是给读屏用户一个坏承诺。
- **影响**：`CHR-02`；`base.primitives` 的 tab 基座（若 myco 也要 tab）。

### Q-SB-08 命中区尺寸：DSH 实测清单（作为 Q-03 / Q-BASE-03 的输入，不另立拍板）

- **事实**（全部 E0，均为「视觉尺寸=命中尺寸」，无透明扩边）：
  | 控件 | 尺寸 | 证据 |
  |---|---|---|
  | 侧栏图标按钮 / 窗口 chrome 按钮 | 28×28 | `SidebarRoot.module.css:317-331`；`HeaderLeadingControls.module.css:12-25` |
  | rail 态控件 | 36×36 | `SidebarRoot.module.css:344-349,442-451,543-550` |
  | 全局面板行 | 36px 高（整行宽） | `SidebarRoot.module.css:492-508` |
  | 浏览器工具键 | 28×28，键间 4px | `Browser.module.css:22-45` |
  | 队列行动作键 | 28×28，键间 10px | `QueueDock.module.css:226-239,212-217` |
  | 发送/停止主键 | 34×34 | `InputBar.module.css:369-391` |
  | 上下文档触发 | ≈22px 高 | `ContextMeter.module.css:9-25` |
  | 面包屑段 | ≈28px 高，段间 4px | `ConversationRoot.module.css:125-139,102-109` |
  | 转录宽度把手 | 10px 宽 | `ConversationRoot.module.css:259-271` |
- **结论**：DSH 全线按鼠标精度设计，**没有一处达到 44dp**；且 4px 键间距低于 Q-03 建议的 8dp 隔离带。
- **建议**：Q-03 的方案 ①（视觉不变 + 透明扩边）在此清单上完全可行；但**必须**同时提高键间距到 ≥8dp，否则 44dp 命中区会互相重叠（浏览器工具键 4px、面包屑 4px、工作区 chip 与 preset 2px 是三个真实反例）。
- **影响**：Q-03 / Q-BASE-03；所有 `SB-*`/`BRW-*`/`CMP-*` 的真机验收项都要按扩边后的尺寸重算。

### Q-SB-09 浏览器沙箱开关：`aria-pressed` 语义反直觉、且无二次确认

- **冲突**：`aria-pressed={!sandboxed}`（`BrowserBody.tsx:107`）→ 按下态 = 「限制已关闭」这个危险态；读屏会念「关闭沙箱限制，已按下」。同时点击**直接切换**、无确认（`:108`），只靠常驻 `role=status` 横幅兜底（`:111`）。
- **证据**：§2.2 `base-sidebar-right.browser.sandbox` 字段 5/6/11。
- **候选**：①改成两个具名动作（「关闭沙箱限制」/「恢复沙箱限制」）作为普通按钮，不用 pressed 语义；②保留 pressed 但把可访问名写成状态句（「沙箱已关闭」）；③加二次确认。
- **建议**：①+③对调（用 ① 消歧义，确认框留给「关闭」方向；「恢复」方向不需要确认）。注意：这是**右栏插件页**，myco 首版若不做内嵌浏览器，本条可延后。
- **影响**：`BRW-10/11`；若 myco 首版不做浏览器则挂起。

### Q-SB-10 转录宽度轴（左右拖拽条）是否进 myco 首版？

- **冲突**：DSH 有一套完整的「内容宽度」体系（默认 `clamp(680, 列宽×0.64, 920)`、用户偏好 `[640, 列宽-176]`、localStorage 持久、左右双把手、指针+滚轮），但**没有任何键盘等价物**（`ConversationWidthControls.tsx:116-127` 只有 pointer 事件），移动端上 10px 宽的把手也基本不可用。
- **证据**：§2.3 `chat.list.width-handle` 字段 1-13（尤其 11）。
- **候选**：①进，但只做宽屏（≥1024）+ 在设置里加数值/档位选择（`settings.general` 已有「内容宽度」项的位置）；②不进，内容宽度固定为 `min(列宽-96, 920)`；③进且做手势（双指缩放）。
- **建议**：①。理由：这直接决定消息列可读性，且设置页给一个档位就能覆盖无障碍/移动端，成本远低于手势。
- **影响**：`CHR-04`；`settings.general` 的内容宽度项；`chat` 面板骨架。

### Q-SB-11 可访问名与「生成内容」规范（4 个具体缺口，建议合并成一条规则）

- **冲突**：四处在 DSH 现状里都是 a11y 缺口，且都是**同一类问题：可访问名与可见信息不同步 / 只存在于视觉层**。
  1. **工作区 chip**：可见名会变（「myco-chat」），但 `aria-label` 恒为「选择工作区」（`EmptyHero.tsx:50`）→ 读屏听不到当前在哪个工作区。
  2. **设置选择器**：可访问名只有当前值（「排队」「旁路发送」），不含字段名（`EnterBehaviorRow.tsx:62-71`）→ 长设置页里同名控件无法区分。
  3. **claim 幽灵提示**：`::after { content: var(--dsh-composer-hint) }`（`InputBar.module.css:193-199`）是 CSS 生成内容 → 读屏读不到，而它承载「该命令还需要什么参数」的关键信息。
  4. **草稿表面**：contenteditable 没有 `role=textbox`/`aria-multiline`（`DraftEditor.tsx:38-52`）→ 依赖浏览器的隐式 contenteditable 语义。
- **证据**：上述四处 + §2.4 `composer.workspace-chip` 字段 11、`composer.input.draft-surface` 字段 11、§2.6 `settings.general.enter-behavior.selector` 字段 11。
- **候选**：①立一条硬规则「**可见文字里的状态，必须在可访问名或 aria-live 里出现**；CSS 生成内容不得承载必需信息」；②逐节点修，不成规则。
- **建议**：①。理由：这四处是同一个错误的不同实例；只修实例不立规则，P4 会在每个新面板重犯一次。
- **影响**：所有面板的字段 11；建议写进 `00-global-rules.md`（**本路不写，交给主笔**）。

---

### 自检（对齐 `README.md §2` 的 P1 门禁）

| 检查项 | 结果 |
|---|---|
| 每个结论有 `path:line`？ | ✅ 90 个 E0 行 + 全部节点字段 12 逐条列证据；§4 的「建议」是意见、已标注 |
| 每个节点写满 13 字段？ | ✅ 37 个逻辑节点 / 32 个标题（其中 1 个标题合并「tab 容器 + tab 原子」2 节点、1 个标题合并「队列行 5 个动作」5 节点）；5 个组节点已在标题标「组节点」，其 13 字段描述容器/滚动/禁用态事实 |
| 每个节点有反证？ | ✅ 37/37（每条都写「反过来做会坏在哪」并指向源码里对应的守卫注释） |
| 代码里没有的行为写「未见实现」？ | ✅ 共 47 处「未见实现」（hover/focus 缺失、手势、高亮、虚拟滚动、时间分组、关闭键、错误出口等） |
| E2 只出现在 §4？ | ✅ 是；§0-§3 无任何「一般应用都这样」式推断 |
| 覆盖率门禁 | ✅ `uncovered=0 / stale=0`（`_ws/out/coverage-dsh-sidebar.md`） |
| 有没有改别人的写作用域？ | ❌ 没有：只写了本文件与 `dsh-sidebar.facts.csv`；`tools/`、`docs/spec/` 其它文件、app 均未改 |

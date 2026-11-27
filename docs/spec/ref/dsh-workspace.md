# DSH 工作区 / 会话行 / 右栏事实库（R1d）

> 对象：`E:\deepseek-harness\packages\client` 下 `ui-workspace`、`ui-sidebar-right`、`ui-sidebar-files`、
> `ui-sidebar-documentpreview`、`ui-sidebar-terminal` 的 `src/`（**只看 `src/`，`tests/` 全部排除**）。
> 机器可读事实：`docs/spec/ref/dsh-workspace.facts.csv`（344 行，与 `_ws/out/findings-dsh-workspace.csv` 逐键对齐）。
> 对账：`_ws/out/coverage-dsh-workspace.md` —— **findings 344 / E0 145 / excluded 199 / uncovered 0 / stale 0**。
> 依据级别：`E0` = 源码行；`E1` = 注释/README 声称；`E2` = 推测（只出现在 §4）。
> 铁律 G-00：§0–§3 每一条都能落到 `path:line`；落不到的一律进 §4。
> **本路是 `dsh-sidebar.md` 的补路**：该路 §0.3 明确「会话行 / 分组 / 搜索 / 多标签不在 ui-sidebar 包」，
> 并给出了本路要取证的指针清单；本路把那些指针逐条落成节点（对应关系见 §1.3）。
> 验收 ID：本路新分配的段是 `SBW-xx`（`sidebar.workspaces.*`）与 `RSB-xx`（`base-sidebar-right.*`），
> 避开 `dsh-sidebar.md` 已占用的 `SB-xx`/`BRW-xx`；P2 归并时统一重排。

---

## 0. 一页结论

### 0.1 分母与命中

| 项 | 数 | 说明 |
|---|---|---|
| 扫描根 | `E:\deepseek-harness\packages\client` | 1918 文件 / **3163** 交互点（全客户端，本次扫描实测） |
| 本路 findings | **344** | 前缀 `ui-workspace/,ui-sidebar-right/,ui-sidebar-files/,ui-sidebar-documentpreview/,ui-sidebar-terminal/` |
| 其中取证 E0 | **145** | 全部落到 **34** 个节点 ID |
| 其中显式排除 | **199** | `tests/` 132 · CSS 结构性属性 57 · 非 UI 源码命中 10（§3 逐条） |
| 本路定义节点 | **41** | 34 个有 E0 键的节点 + **7 个「无扫描命中」的容器/浮层节点**（清单见 §2 各组末尾的脚注） |
| uncovered | **0** | `ui_audit.py check --fail-on-uncovered` 通过 |

### 0.2 八条硬结论（每条都有 E0）

**① 会话列表的真实组织单位是「工作区」，不是时间。**
默认分组 `groupBy='workspace'`（`ui-workspace/src/client/stores.ts:16-19,84-90`），组 = Host 工作区列表
（`tree.ts:334-376`）；没有归属的会话进**「未分组」桶**（`UNGROUPED_KEY=''`，`tree.ts:18,361-374`，
标签 `group.ungrouped` `Rows.tsx:235`）。另有 `workspace-tree`（按目录祖先嵌套，`WorkspaceBrowser.tsx:315-323`）
与 `flat`（单列表）两种模式（`stores.ts:16-19`）。**全包 grep `today|yesterday` 0 命中** ——
「今天/昨天/更早」的时间分组**未见实现**（`ui-workspace/src` 全量）。
默认排序 `orderBy='updated'`（按 `updatedAt` 倒序，`tree.ts:143-157`）；切成 `manual` 后**每个工作区各存一份顺序**
（`sessionOrderByAccount[workspaceId]`，`stores.ts:27-28,96-98,114-119`），另加 `__flat_session_order__`。

**② 会话行是 32px 的整行 `role=treeitem`，单击打开；改名只有「标题双击」一条入口。**
行高 32px（`Rows.module.css:109-113`），`role=treeitem` + `aria-selected`（`Rows.tsx:595-597`），
外层是 `role=tree`（`AnimatedRows.tsx:170-177`）。单击 = 打开（`Rows.tsx:598`）；
双击**标题**才改名且 `stopPropagation` 不打开行（`Rows.tsx:636-644`）；
双击行其它位置**未见实现**；行内没有任何键盘处理（`Rows.tsx:586-625` 无 `onKeyDown`；
`AnimatedRows.tsx:176` 只有 `onKeyDownCapture` 置 armed 标记）→ **ARIA tree 的键盘语义未实现**。

**③ 行尾动作是「悬浮才出现」的两类：`⋯` 触发菜单（4 项）+ 两个悬浮键（归档/置顶）。**
`rowActions` 在 default 下 `display:none`，只有 `:hover` 或 `menuOpen` 才出现（`Rows.module.css:255-271`），
同时把时间与置顶标记藏掉（`:279-289`）。菜单项是 slot 注册的 4 行：pin(100)/rename(200)/fork(300)/archive(400)
（`ui-workspace/src/client/index.ts:295-300`）；悬浮键是 archive(100)/pin(200)（`index.ts:301-304`）。
悬浮 800ms 出**悬浮卡**（`Rows.tsx:692-702`），带「复制标题」；菜单打开或拖拽进行中时禁用（`:697`）。

**④ 排序靠 HTML5 拖拽，规则是「同组 + 同置顶块」；没有触屏等价手势。**
会话行 `draggable` 仅在非空白/非归档时为真（`Rows.tsx:573,601`）；dragover 只在同组且 `pinned` 相同
的目标行上 `preventDefault` 并报 before/after 半区（`Rows.tsx:610-617`；`WorkspaceBrowser.tsx:536-546`）；
落下后 `pinCurrentBlank` 把当前空白行钉在首位（`tree.ts:217-223`；`WorkspaceBrowser.tsx:182-203`）。
工作区行另外可拖（`Rows.tsx:249-257`；drop 判定在 `WorkspaceBrowser.tsx:456-494`）。
**触屏/长按拖动：未见实现**（全包无 `onTouchStart`/`onPointerDown` 用于排序）。

**⑤ 不是虚拟滚动：每组只放 5 条「空闲」会话，超出用「展开/收起」按钮，一次 +5。**
`COLLAPSED_SESSION_LIMIT = 5`（`WorkspaceBrowser.tsx:55`），**运行中/有运行子代理/空白行不占配额**
（`:57-70`）；按钮文案带剩余数，`hiddenCount<=5` 时一次放到 `Infinity`（`:587-596`）。
全包 grep `react-window|virtual|Virtuoso` 0 命中；行列表唯一的「窗口化」是退出行的 FLIP 覆盖层
（`AnimatedRows.tsx:60-123`）。

**⑥ 搜索 = 250ms 防抖 + 500 码元上限 + 「本地元数据 ∥ Host 内容」双源合并；无高亮、不定位到消息。**
防抖 `SEARCH_DEBOUNCE_MS=250`（`WorkspaceBrowser.tsx:51,1093-1128`）；查询截断
`SEARCH_QUERY_MAX_CODE_UNITS=500` 且不切断代理对（`:53,72-81`）。结果是本地标题/工作区名子串匹配
（`tree.ts:552-585`）与 Host 内容命中（`:565-598`）的合并，最多 `searchResultLimit` 条
（`:600-620`）。**关键字高亮未见实现**（grep `<mark` 0 命中）。选中一条结果 = 打开会话 +
清空查询 + 收起搜索 + `scrollIntoView({block:'nearest'})` 定位到**那一行**，**不是消息级定位**
（`WorkspaceBrowser.tsx:1032-1041`；`Rows.tsx:580-584`）。Escape 直接退出搜索态并清空（`:1249-1253`）；
外点关闭但 query 非空时不收起（`:1081-1091`）。

**⑦ 右栏不是抽屉，是「网格列 + dockkit 页签」；页签 strip 的方向键在 ui-dockkit 里，是有的。**
本包只提供：右栏两个 chrome 键（全屏/收起，`SidebarRight.tsx:263-296`）、折叠态唯一入口
（会话头的 `ExpandButton`，`ExpandButton.tsx:33-52`）、整列平移隐藏（`SidebarRight.module.css:44-62`）、
以及 tab 内容的 keyed 派发（`SidebarRight.tsx:193-343`）。真正的 tab 条在 `ui-dockkit`：
`role=tablist`（`ui-dockkit/src/components/TabPanel.tsx:245`）、`role=tab` + `aria-selected` + roving
`tabIndex`（`:263-266`）、**ArrowLeft/Right 循环 + Home/End** 移焦点、Enter/Space 选中（`:109-124,293-307`）、
右键开 tab 菜单（`:308-312`）、关闭键（`:315-333`）。已注册的 tab 类型：`guide`（`ui-sidebar-right/src/client/contract/seed.ts:36`）、
`files`（`ui-sidebar-files/src/client/definition.tsx:15`）、`text`（`ui-sidebar-documentpreview/src/client/definition.ts:15`）、
`terminal`（`ui-sidebar-terminal/src/client/index.ts:71-74`）、`browser`（`ui-sidebar-browser`，非本路）。

**⑧ 三个内容类型各自的「真交互」很少，状态/持久化才是主体。**
文件区：目录行=展开（`FilesBody.tsx:80-93`），文件行=打开（交给 `file:` 查看器，`:95-103,186-187`），
`other` 类型灰化不可点（`:105-111`）；**无选中态、无右键菜单、无键盘树导航**；滚动位置按 tab 恢复
（`:150-167,221-228`）。文档预览：换行/查看器/自动刷新/缩放/页码都按 **tab** 持久，自动刷新是
「变更条提示」而非直接重绘（`TextPreview.tsx:166-178,252-264`）。终端：整套连接状态机 + shell 选择菜单 +
**双击 tab chip 就地改名**（`TerminalTitle.tsx:14-59`）。

### 0.3 本路与另外两路的边界（避免重复登记）

| 事 | 位置 | 本路处理 |
|---|---|---|
| 侧栏壳（列几何、折叠、面板行、滚动条静默） | `ui-sidebar/` | 已在 `dsh-sidebar.md` 取证，本路不重复 |
| 右栏列宽/断点/拖拽条、`openRightbar/closeRightbar` | `ui-layout/`（`AppFrame.tsx`、`columns.ts`） | 本路**不取证**，只给指针（§1.2） |
| tab 条本体（选中/关闭/拆分/右键/拖拽/浮动） | `ui-dockkit/` | 本路只给指针与语义摘要（§1.2、§2.2 `tab-strip`），**不登记 facts**（避免与 ui-dockkit 路冲突） |
| 浏览器 tab 页（后退/地址栏/沙箱） | `ui-sidebar-browser/` | 已在 `dsh-sidebar.md` §2.2 取证 |
| 会话视图 tab（聊天/轨迹） | `ui-conversation/` | 已在 `dsh-sidebar.md` §1.2 D 取证；**不是**右栏 tab |
| 文档预览的具体渲染器内部（Excel canvas / PDF.js / fortune-sheet 自带菜单） | 第三方库 | 只登记本包自己的按钮/容器，第三方内部不进节点（§3.3） |

---

## 1. 包 → 面板归位表

### 1.1 五包归位

| 包 | 归到面板/基座（`10-panels.md`） | 本路写作用域 | 包内真实内容 | 证据 |
|---|---|---|---|---|
| `ui-workspace` | **`sidebar`**（会话导航：行/分组/搜索/行菜单/筛选/添加工作区/行内浮层） | ✅ **77** 个 E0 交互点（217 条命中 = 77 E0 + 23 CSS + 112 tests + 5 非 UI） | 侧栏 `sidebar.workspaces` 洞的**全部列表语义**：分组派生、排序、归档/置顶、搜索、行组件、四个行动作、两个对话框、toast；另含对话空态的工作区选择器 `WorkspacePicker`（同一份 `WorkspacePickFlow` 组件） | `WorkspaceBrowser.tsx:836-1487`；`tree.ts`；`Rows.tsx`；`index.ts:270-328` |
| `ui-sidebar-right` | **`base.sidebar-right`**（右栏壳 + guide 页） | ✅ **8** 个 E0 交互点（18 条命中 = 8 E0 + 5 CSS + 5 tests） | 右栏在 frame 里的座位、整列平移隐藏、全屏/收起两键、折叠态入口键、**tab 内容派发与焦点连续性**、guide（指南/空态）页 | `shell/SidebarRight.tsx:352-422`；`shell/ExpandButton.tsx`；`tabs/guide/*`；`focus.ts:68-137` |
| `ui-sidebar-files` | **`base.sidebar-right`**（右栏 `files` tab 类型） | ✅ **12** 个 E0 交互点（19 条命中 = 12 E0 + 4 CSS + 2 tests + 1 非 UI） | 工作区文件树：根路径行 + 刷新、目录展开/折叠、文件打开、`other` 灰化、滚动恢复 | `FilesBody.tsx:140-231`；`definition.tsx:25-39`；`index.ts:58-97` |
| `ui-sidebar-documentpreview` | **`base.sidebar-right`**（右栏 `text` tab 类型：文档预览宿主 + 7 个渲染器） | ✅ **35** 个 E0 交互点（59 条命中 = 35 E0 + 17 CSS + 7 tests） | 文本分页读取、查看器切换、换行、刷新/自动刷新、缩放控件与手势、字体缺失面板、PDF/Excel/Office/HTML/图片/代码渲染器外壳 | `TextPreview.tsx:56-405`；`zoom/*`；`office/FontNotice.tsx`；`pdf/pdf.tsx`；`excel/excel.tsx` |
| `ui-sidebar-terminal` | **`base.sidebar-right`**（右栏 `terminal` tab 类型） | ✅ **13** 个 E0 交互点（31 条命中 = 13 E0 + 8 CSS + 6 tests + 4 未接线） | xterm 终端体、连接/重试/接管/新建、shell 选择菜单、tab chip 就地改名；**Recovery/Cleanup 两组件已注释未接线** | `terminal.tsx:24-184`；`TerminalGuide.tsx:32-83`；`TerminalTitle.tsx:14-59`；`index.ts:102-129`（注释） |

> **归位分歧（已登记 Q-WS-01/Q-WS-02）**：`10-panels.md` 把 `ui-workspace` 同时写进
> `sidebar` 与 `workspace` 的证据包、把 `ui-sidebar-files` 写进 `workspace` 证据包。
> 源码事实是：会话行/分组/搜索只出现在侧栏洞 `sidebar.workspaces`（`ui-workspace/src/client/index.ts:270-290`），
> 文件树只作为右栏 tab 类型注册在 `sidebar.right.pane.tab`（`ui-sidebar-files/src/client/index.ts:86-96`）。
> 本路按「实际挂载位置」归位；`workspace` 面板行需要 P2 决定是并入 `sidebar`/`base.sidebar-right`，
> 还是另立一个空面板。

### 1.2 跨包指针清单（本路只指路，**不登记 facts**）

**A. 侧栏列几何 / 右栏列宽 / 拖拽条（`ui-layout`）**

| 行为 | 证据 |
|---|---|
| 三列 grid：`sidebar \| 中心 \| rightbar`，右栏轨道宽 = `cols.rightbar` | `ui-layout/src/client/AppFrame.tsx:265-266` |
| 右栏宽度与拖拽：8px 命中条、pointer capture、rAF 节流、折叠时不渲染 | `AppFrame.tsx:58-118,301`；`AppFrame.module.css:223-233` |
| 右栏开合落位：`layout.openRightbar(track, fullscreen)` / `closeRightbar()` | `ui-sidebar-right/src/client/index.ts:188-192` |
| 断点：侧栏 `SIDEBAR_AUTO_COLLAPSE=1024`；右栏自动全屏阈值 = **768**（在本包 seat 内读 viewportWidth） | `ui-layout/src/client/columns.ts:20-23`；`SidebarRight.tsx:360-367` |

**B. tab 条本体（`ui-dockkit`）**

| 行为 | 证据 |
|---|---|
| `role=tablist` / `role=tab` / `aria-selected` / roving `tabIndex` | `ui-dockkit/src/components/TabPanel.tsx:245,263-266` |
| ArrowLeft/Right 循环、Home/End、Enter/Space（manual activation） | `TabPanel.tsx:109-124,293-307` |
| 右键开 tab 菜单；菜单项 = 拆分/浮动/关闭 + 宿主扩展项 | `TabPanel.tsx:308-312,335-343`；`TabMenu.tsx:50-120` |
| 关闭键（不可关的 guide 单页不渲染关闭键） | `TabPanel.tsx:251-254,315-333`；`ui-sidebar-right/src/client/stores.ts:63-72` |
| 拖拽/放置区/浮动窗/分隔条 | `TabPanel.tsx:284-288`；`DockSurface.tsx`；`FloatLayer.tsx`；`PaneTree.tsx:46` |
| ＋ 新 tab（仅当该 pane 没有 guide 页时）；拆分键 | `TabPanel.tsx:350-365,367-390`；`SidebarRight.tsx:321-324` |

**C. `ui-sidebar-right` 内部但非交互的面（只作字段背景，不算节点）**

| 行为 | 证据 |
|---|---|
| tab 内容按 kind keyed 派发；未知 kind 显示「不可用」占位 | `SidebarRight.tsx:193-241` |
| 焦点连续性：pane 元素被替换后找回同一 occurrence 的可见 pane；点 pane 空白处把焦点交给 pane | `focus.ts:56-137`；`close-focus.ts:13-35` |
| 全屏时保留底下轨道、`aria-hidden` 只对折叠 docked 生效（浮层仍可达） | `SidebarRight.tsx:307-317,367-411`；`SidebarRight.module.css:31-66` |

**D. `ui-workspace` 内部被本路折叠进节点的面**

| 行为 | 证据 |
|---|---|
| 空态（无会话 / 归档-only 两种文案 + 「查看其它」） | `WorkspaceBrowser.tsx:264-278`；`WorkspaceBrowser.module.css:527-552` |
| 底部 24px 渐隐覆盖层（macOS 半透明下禁用） | `WorkspaceBrowser.module.css:329-343` |
| 行 FLIP 动画：200ms 位移 / 100ms 淡出、首次输入后才武装、reduced-motion 关闭 | `AnimatedRows.tsx:5-6,52-124,168-182` |
| 会话状态源：`running / runningSubagentCount / pendingInteraction / completed(completionUnread)` | `tree.ts:397-418`；`Rows.tsx:347-397` |
| 归档/置顶清单来自 registry-global 快照（Host 侧） | `ui-workspace/src/client/index.ts:147-148`；`tree.ts:259-267` |

### 1.3 与 `dsh-sidebar.md` §1.2 指针的对应关系（该路只指路、本路取证）

| `dsh-sidebar.md` 指针 | 本路节点 |
|---|---|
| A. 会话行全量交互（`Rows.tsx:524-703`） | `sidebar.workspaces.session-row` / `.session-title` / `.session-actions` / `.session-menu` / `.session-hover-card` / `.reorder`（§2.1） |
| B. 分组 / 排序 / 置顶 / 归档 / 折叠 / 加载更多 | `sidebar.workspaces.group-row` / `.group-menu` / `.list` / `.overflow` / `.view-options` |
| C. 搜索框 / 防抖 / 结果树 / 跳转定位 | `sidebar.workspaces.search` / `.search-result-row` |
| D. 多标签（右侧栏 tab 类型） | `base-sidebar-right.tab-strip`（指针）+ `base-sidebar-right.panel.*`（§2.2） |
| E. 侧栏宽度/断点/拖拽 | §1.2 A（`ui-layout`，本路不取证） |

---

## 2. 节点事实表（13 字段）

> 分组顺序：`sidebar.workspaces.*`（§2.1，本路从 `ui-workspace` 取证）→ `base-sidebar-right.*`（§2.2）。
> 每个节点写满 13 字段；`反证` 必写；代码里查不到的行为一律写「**未见实现**」，不写「应该有」。
> 标注「组节点」的是共享容器/滚动盒（没有独立原子目标），只承载几何、状态与禁用事实。
> **7 个无扫描命中的节点**在各自小节末尾用「⌁」标出，并给出为什么扫描器抓不到它：
> `sidebar.workspaces.section-header` / `.list` / `.session-menu` / `.session-hover-card` / `.hero-picker` /
> `base-sidebar-right.tab-strip` / `base-sidebar-right.guide`。

### 2.1 `sidebar.workspaces.*`（侧栏 `sidebar.workspaces` 洞：会话行 / 分组 / 搜索）

#### `sidebar.workspaces.section-header`（区段头） ⌁ 无扫描命中（纯布局容器 + 两个受控子控件）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.section-header`。所属 `sidebar` 面板 → `sidebar.workspaces` 洞 → 区段头。父：`WorkspaceBrowser` 根（`WorkspaceBrowser.tsx:1208-1214`）。出现条件：`wide`（侧栏展开态）才渲染；rail 态整块不渲染（`:1210,1215`）。每屏 1 个。 |
| 2 | 网格与定位 | 根 `flex:1 min-height:0` 纵向；header `flex:none` 高 **36px**、`justify-content:flex-end`、`gap:4px`、`padding-left:4px`、`margin-bottom:4px`、`overflow:hidden`、圆角 md（`WorkspaceBrowser.module.css:52-71`）。内容序：标题（`max-width:45%`）→ 搜索槽（`flex:1`，折叠 28px）→ 动作簇（`max-width:60px`）（`WorkspaceBrowser.tsx:1210-1303`）。 |
| 3 | 命中区 | 自身无命中。子命中：搜索胶囊 28px×剩余宽（展开 30px，`:149-177`）、视图选项 28×28（`:17-30`）、添加工作区 28×28（rail 36×36，`:266-272`）。控件间距 `gap:4px`。 |
| 4 | 手势 | 无自身手势；所有手势由子节点承接。无长按/双击/拖拽。 |
| 5 | 行为 | 搜索一展开：标题 `sectionLabelHidden`（`max-width:0` 收掉）、动作簇 `headerActionsHidden`（`max-width:0` + `pointer-events:none`），把整行让给输入框（`WorkspaceBrowser.tsx:1211-1213,1272`；`WorkspaceBrowser.module.css:90-141`）。幂等；无需撤销。 |
| 6 | 视觉状态 | 仅 `default`；无 pressed/selected/disabled。标题 tertiary 色；展开搜索时标题 120ms 淡出并左移 4px、动作簇 120ms 淡出右移 4px（`:82-87,127-141`）。 |
| 7 | 布局占位与展开形态 | 参与布局流，`flex:none` 固定 36px；不展开、不滚动；G-03 三形态不适用（纯行布局）。标题/动作簇的收起不改变 header 高度（max-width 收 0，不是 `display:none`）。 |
| 8 | 滚动语义 | 不滚。父级 `.list` 才是唯一滚动容器（`WorkspaceBrowser.module.css:354-374`）。 |
| 9 | 动效 | `max-width`/`opacity`/`transform` 180ms/120ms `--ds-ease-in-out`；`prefers-reduced-motion` 全部关掉（`WorkspaceBrowser.module.css:604-621`）。可中断：任何时刻再切换直接反向过渡。 |
| 10 | 边界态 | rail 态不渲染；标题超长 `max-width:45%` + nowrap（`:73-79`）；搜索展开时若 query 非空且外点，搜索不收起（`WorkspaceBrowser.tsx:1083-1090`）。大字号只抬行内文字高度，36px 容器不变（无 token 参与高度）。 |
| 11 | 无障碍 | 无 role、无 landmark；标题是纯文本。焦点序 = 搜索按钮 → 视图选项 → 添加工作区（左侧标题不可聚焦）。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/rows/WorkspaceBrowser.tsx:1208-1303`；`E0` `WorkspaceBrowser.module.css:52-141,604-621`。 |
| 13 | 反证 + 验收 | **反证**：若搜索展开时标题与动作簇不用 `max-width:0` 让位而保持宽度，因为 header `overflow:hidden`，展开的输入框只能挤在 28px 的缝里、右侧「添加工作区」被裁到点不到（`WorkspaceBrowser.module.css:64` 的 clip 就是这条的现场）。若让位用 `display:none`，动作簇会瞬间消失造成 36px 行内跳动且焦点序突变。**验收**：`SBW-01` 宽 280px 侧栏点搜索，输入框获得 ≥160px 可用宽度且添加按钮在收起前仍可见（截图 + DOM 宽度断言）；`SBW-01b` 反复开合搜索 5 次，header 高度恒 36px（真机录屏逐帧）。 |

#### `sidebar.workspaces.search`（搜索：按钮 / 输入 / 清空 / Escape / 外点 / 防抖）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.search`。父：区段头。三个安装位同一套逻辑：①宽屏搜索胶囊（按钮 + input + clear，`WorkspaceBrowser.tsx:1216-1269`）；②rail 态独立 36×36 搜索图标（`:1325-1339`）；③快捷键 `session.search`（`shortcuts.ts:90-91`）。宽屏每屏 1 个；rail 每屏 1 个。 |
| 2 | 网格与定位 | 胶囊：高 28px（展开 30px）、`flex:none`、`gap:0`、圆角 sm/md（`WorkspaceBrowser.module.css:145-177`）；按钮 28×28（展开态高 30）；input `flex:1; width:0; min-width:0`（`:207-226`）；clear 24×24（`:232-249`）。rail：搜索框 36×36 + `margin-bottom:12px`（`:274-292`）。 |
| 3 | 命中区 | 按钮 28×28 = 命中（无扩边，**<44dp**）；input 整段可点；clear 24×24；rail 36×36。与相邻动作簇间距 4px。 |
| 4 | 手势 | 单击胶囊任意处 → 展开并聚焦（`WorkspaceBrowser.tsx:1220-1224`）；输入即时 onChange；Escape 收起；点外部（document click）blur，query 为空才收起（`:1081-1091`）。无长按/双击/滑动。与父滚动无仲裁（header 不滚）。 |
| 5 | 行为 | 输入 → `sanitizeSearchQuery`（去 NUL、截 500 UTF-16 码元且不切断代理对，`:72-81`）→ `normalizedQuery=trim()`（`:1017`）。query 非空则列表体换成搜索结果（`:1344-1360`），空则回树。远端：**250ms 防抖**后调 `searchSessions`，AbortController 取消前一次（`:1093-1128`）。Escape/clear：清空 + 收起（`:1249-1253,1260-1264`）。按钮路径走 `requestSearch()`（快捷键同一通道，`:1061-1069`）。幂等。 |
| 6 | 视觉状态 | default：透明底、secondary 图标、`cursor:text`（`:145-160,158`）；hover 按钮：`interactive-bg-hover`；展开：30px + 0.5px 边框 + md 圆角（`:168-177`）；focus-visible：`outline-offset:-2px` 内收环（`:34-39`，因动作簇与胶囊自身裁剪外环）；clear 显示条件 `searchExpanded`（`:1255`）。disabled：未见实现。 |
| 7 | 布局占位与展开形态 | 参与布局流；胶囊占区段头剩余宽（`flex:1`），不改变 36px 行高；不属于 G-03（展开的是替换列表体，不是自身内容体）。 |
| 8 | 滚动语义 | 不滚。搜索结果体在 `.list` 里滚（`.searchTree` 无独立滚动，`WorkspaceBrowser.tsx:788-826`）。 |
| 9 | 动效 | 胶囊 width/padding/border/background 180ms；input opacity 120ms；标题与动作簇让位 180/120ms（`WorkspaceBrowser.module.css:106-141,161-177,219`）；reduced-motion 关闭。可中断。 |
| 10 | 边界态 | 空 query → 列表体回原树（结果不保留）；无匹配 → `search.noMatches` 文案（`:817-819`）；加载中 → 骨架行 2 条（已有本地命中时 1 条）+ `role=status`（`:802-816`）；结果超上限 → `search.hasMore` 状态行（`:820-824`）；rail 态点搜索先展开侧栏、等 `EXPAND_SLIDE_MS=300` 再聚焦（`:49,1049-1060`）；多语言只影响 placeholder/aria；大字号输入框 13px 固定（`:216`）。 |
| 11 | 无障碍 | 按钮 `aria-label="搜索会话"`、`aria-expanded`、`aria-keyshortcuts`（`:1227-1235`）；input 有 placeholder 但**无 `aria-label`**（占位即名，`：1240-1248`）；结果树 `role=tree` + `aria-label=search.results.aria`（`:790`）；清空键 `aria-label`（`:1259`）；待加载 `role=status`（`:805`）。焦点：展开即聚焦 input（`:1071-1074`）；Escape 后焦点仍留在 input（input 收起为 `tabIndex:-1`，Chrome 会把焦点移回 body —— **未见显式焦点归还**）。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/rows/WorkspaceBrowser.tsx:49-53,72-81,1012-1128,1210-1271,1324-1339`；`E0` `WorkspaceBrowser.module.css:99-249,604-621`；`E0` `shortcuts.ts:90-91`；`E0` `tree.ts:542-621`（双源合并）。 |
| 13 | 反证 + 验收 | **反证**：若不做 250ms 防抖，每个键都发一次 Host 内容搜索，输入「session」会打出 7 次往返且结果频繁回跳（旧结果与新 query 不匹配），用户看到的列表一直在闪；若不做查询截断，500 码元限制在协议层，客户端先溢出会被 Host 拒绝并整页报错。若外点关闭不判 `normalizedQuery!==''`，用户点结果列表外部就会把正在看的搜索结果清掉。**验收**：`SBW-02` 连续输入 6 个字符只发 1 次 Host 请求（DevTools Network 断言）；`SBW-02b` 粘贴 600 字符，输入框值长度 500 且末位不是半个代理对（单测）；`SBW-02c` query 非空时点结果外区域，搜索结果仍在（真机）。 |

#### `sidebar.workspaces.search-result-row`（搜索结果行 / 取消归档）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.search-result-row`。父：搜索结果树 `role=tree`（`WorkspaceBrowser.tsx:790`）。出现条件：query 非空（`:1344-1360`）；每行一个匹配会话，0..`searchResultLimit` 条。 |
| 2 | 网格与定位 | 行 `min-height:48px`、纵向两行、`padding:4px 8px`、圆角 lg、`width:100%`（`Rows.module.css:23-37`）；行序 `gap` 由 `.searchTree > treeitem + treeitem {margin-top:2px}`（`WorkspaceBrowser.module.css:376-380`）。第一行：16px 状态槽 + 4px + 标题（14/20，ellipsis）；第二行 `margin-left:20px`：工作区名（12/17，≤40% 宽，仅有一个子元素时允许 100%）+ 片段（12/17，ellipsis）（`Rows.module.css:47-96`）。 |
| 3 | 命中区 | 整行 48px 高 × 整宽（`cursor:pointer`，`Rows.module.css:34`）；归档行的取消归档键 16×16（`Rows.module.css:393-410`），行内右侧。**<44dp**（行高够、键不够）。 |
| 4 | 手势 | 单击整行 = 打开（`Rows.tsx:487`）；单击归档行的取消归档键 = 恢复，`stopPropagation` 不打开（`:506`）；无双击/长按/拖拽/右键。 |
| 5 | 行为 | 打开：`openSearchResult` → 归档则 toast 提示不可打开；否则 `setRevealSessionId` + 清 query + 收起搜索 + `open(id)`（`WorkspaceBrowser.tsx:1032-1041`）。随后目标行在树里 `scrollIntoView({block:'nearest'})` 并确认（`Rows.tsx:580-584`）。取消归档 → `unarchiveSession`（`:1166-1170`）。幂等。 |
| 6 | 视觉状态 | default 透明底；hover / `.selected`（= 当前会话）都用 `interactive-bg-hover`（`Rows.module.css:39-45`）；归档行标题/工作区/片段降到 caption 色（`:244-249`）并带 `aria-description`（`Rows.tsx:486`）；动作键仅在 `:hover` 或行 menuOpen 出现（`:265-271`）。disabled 未实现。 |
| 7 | 布局占位与展开形态 | 参与布局流，固定最小 48px；不展开；无子节点位移（取消归档后行原地变灰/消失由列表重排，AnimatedRows 给 200ms 位移）。 |
| 8 | 滚动语义 | 行随 `.list` 滚动；无内滚。到边行为归 `.list`（`overscroll-behavior` 未声明，见 §4 Q-WS-16）。 |
| 9 | 动效 | 行增删/移动由 `AnimatedRows` FLIP：位移 200ms、淡入淡出 100ms、grep 见 `AnimatedRows.tsx:5-6,96-123`；reduced-motion 关闭（`:57`）。 |
| 10 | 边界态 | 无匹配 → 不进本节点（外层 `search.noMatches`）；超上限 → 尾部 `search.hasMore`；归档行不可打开；空白会话**永不进搜索**（`tree.ts:573-575`）—— 因为空白行标题是本地化文案，匹配它会绑定语言。多语言：标题/工作区名受字典影响。 |
| 11 | 无障碍 | `role=treeitem` + `aria-selected` + `aria-description`（归档）；**无键盘处理**（treeitem 不带 tabindex/方向键，Tab 跳过整棵树）；取消归档键 `aria-label=menu.unarchiveSession`（`Rows.tsx:502-509`）。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/rows/Rows.tsx:459-522,580-584`；`E0` `Rows.module.css:23-96,244-249,265-277,393-410`；`E0` `WorkspaceBrowser.tsx:739-829,1032-1041,1166-1170`；`E0` `tree.ts:542-621`；`E0` `WorkspaceBrowser.module.css:376-380`。 |
| 13 | 反证 + 验收 | **反证**：若把结果打开做成「打开会话并把它滚到视口中间」，因为搜索态会立刻换成树，`scrollIntoView` 发生在替换后的树里；一旦不做「先清 query 再定位」，用户会停在搜索结果里、看不到自己刚打开的那一行。若结果行不给工作区名，同名会话（多个 worktree 里的 `main`）无法区分，用户会打开错会话。**验收**：`SBW-03` 搜索「needle」并打开结果，搜索收起、目标行在视口内且高亮为当前会话（真机 + DOM 断言 `scrollIntoView` 被调）；`SBW-03b` 同名标题两会话结果行显示不同工作区名（截图）。 |

#### `sidebar.workspaces.view-options`（分组 / 排序 / 归档筛选菜单）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.view-options`。父：区段头动作簇。出现条件：`wide` 才渲染（`WorkspaceBrowser.tsx:1273-1283`；rail 无）。每屏 1 个触发器，菜单 12 项（3 组标签 + 3 组选项 + 2 分隔线，`:120-134`）。 |
| 2 | 网格与定位 | 触发器 28×28、圆角 sm、secondary 图标（`WorkspaceBrowser.module.css:17-30`）；菜单 `align="end"`、`portal`、`dense`、`min-width:200px`（`:47-49`；`WorkspaceBrowser.tsx:148-153`）。菜单从按钮右缘弹出（portal 因为 header `overflow:hidden`，`:151-153` 注释）。 |
| 3 | 命中区 | 触发器 28×28（**<44dp**）；菜单项高度由 ui-primitives 的 Menu 决定（不在本包）。 |
| 4 | 手势 | 单击开合；菜单内单击选中并关闭（`WorkspaceBrowser.tsx:140-147`）；无长按/右键/滑动。 |
| 5 | 行为 | 三组单选：`groupBy` ∈ `workspace\|workspace-tree\|flat`；`orderBy` ∈ `manual\|updated`；归档过滤 ∈ `default\|show\|only`。选中即写 store（persist key `dsh.workspace.view.v5`，`stores.ts:91-98,127`）；切 `manual` 时把当前各账户顺序快照进 `sessionOrderByAccount`（`stores.ts:94-98`）。`selectedIds` 只反映三组当前值（`WorkspaceBrowser.tsx:135-139`）。幂等；视图切换不可撤销（无 undo）。 |
| 6 | 视觉状态 | 触发器 default / hover `interactive-bg-hover` / focus-visible `-2px` 内收环（`WorkspaceBrowser.module.css:34-43`）；菜单自身选中态由 ui-primitives 渲染。disabled 未实现。 |
| 7 | 布局占位与展开形态 | 参与布局流，固定 28×28；菜单是浮层（G-01 不占位、portal 到 body）。不改变列表布局；切视图时列表整体淡入（resetKey 变化让 AnimatedRows 清动画，`AnimatedRows.tsx:54-57`）。 |
| 8 | 滚动语义 | 不滚（header 内）；菜单自身超过视口时由 ui-primitives 处理（未见本包实现）。 |
| 9 | 动效 | 无本包动画（菜单动画归 ui-primitives）；触发器的 hover 背景瞬切。 |
| 10 | 边界态 | rail 不渲染（没有分组概念可切）；`archivedFilter` 缺省读作 `'default'`（兼容旧持久化，`stores.ts:29-30`；`WorkspaceBrowser.tsx:897`）；三组选项的当前值持久化跨重载。 |
| 11 | 无障碍 | 触发器 `aria-label=viewOptions.label` + Tooltip（`:155-159`）；菜单键盘语义归 ui-primitives（不在本包）。触发器焦点在搜索按钮之后、添加工作区之前。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/rows/WorkspaceBrowser.tsx:105-168,1273-1283`；`E0` `WorkspaceBrowser.module.css:17-49,34-43`；`E0` `stores.ts:16-31,91-98,127`；`E0` `tree.ts:226-257`（归档过滤语义）。 |
| 13 | 反证 + 验收 | **反证**：若菜单就地渲染（不 portal），header 的 `overflow:hidden` 会把下拉裁成一条（`WorkspaceBrowser.module.css:64` 注释明说这条）。若 `orderBy` 切到 `manual` 时不快照当前顺序，用户会看到列表「随机」重排（因为各账户还没有保存顺序）；反过来切回 `updated` 又清空手排，等于每次切换都丢掉用户排过的位置。**验收**：`SBW-04` 打开该菜单，菜单完整显示在 header 之外（截图）；`SBW-04b` 把 manually 排过的顺序切到 updated 再切回 manual，顺序按 `stores.ts:94-98` 的规则表现（单测 + 真机）。 |

#### `sidebar.workspaces.add-workspace`（＋ 添加工作区：菜单 / 空工作区直开目录流）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.add-workspace`。父：区段头动作簇。出现条件：**该面的 `sidebar.workspaces.directoryFlow` 洞被占用**（`directoryFlowAvailable`，`WorkspaceBrowser.tsx:892,1287`）；无目录流时整个按钮不渲染（`:1284-1287` 注释）。每屏 1 个；菜单 0..N 项（每个工作区一项 + 「添加工作区」）。 |
| 2 | 网格与定位 | 触发器 28×28（rail 36×36，图标 16/18px）（`WorkspaceBrowser.tsx:1289-1301`；`WorkspaceBrowser.module.css:17-30,266-272`）；菜单锚在按钮上、`side="right"`（`WorkspaceBrowser.tsx:1305-1321`）。 |
| 3 | 命中区 | 28×28（**<44dp**）；rail 36×36。菜单项高度归 ui-primitives。 |
| 4 | 手势 | 单击打开菜单（`requestAddWorkspace()`，`:1295-1298`）；菜单项单击选工作区或「添加工作区」；无长按/右键。 |
| 5 | 行为 | `WorkspacePickFlow`：①菜单列出已有工作区 → 选中 = `onPick(id)` → `closeAddWorkspace()` + `startSession(workspaceId,{clearPreviousDraft:false})`（`:1316-1319`）；②「添加工作区」→ `openDirectoryFlow()`（`WorkspacePicker.tsx:141-146`）；③**工作区列表为空且只有一个「添加」项时，触发器直接把打开当点击**，不弹单行菜单（`WorkspacePicker.tsx:148-162`）；④列表未就绪时菜单保持 loading 状态、不抢开目录流（`:156-157,202`）。一次只许一个拾取流程（`flowBusy` 时菜单项 disabled，`:86-91,107,117`）。幂等；不可撤销（已建工作区需走删除）。 |
| 6 | 视觉状态 | 触发器 default / hover `interactive-bg-hover` / focus-visible 内收环；菜单项 disabled 当 `flowBusy`（`WorkspacePicker.tsx:107,117`）；菜单加载中 `role=status` 文案（`:202`）。 |
| 7 | 布局占位与展开形态 | 触发器参与布局流 28×28；菜单与目录流都是浮层（G-01 不占位）。 |
| 8 | 滚动语义 | 不滚；菜单项过多时的滚动归 ui-primitives Menu。 |
| 9 | 动效 | 无本包动画。 |
| 10 | 边界态 | 无目录流占用 → 按钮消失（不是 disabled）；列表 pending → 菜单不自动切目录流；拾取中 → 所有菜单项 disabled；已存在同一路径 → 建工作区失败进 `add-error-dialog` 节点；多语言：菜单项标签随语言。 |
| 11 | 无障碍 | 触发器 `aria-label=workspace.add` + `aria-keyshortcuts`（`workspace.add` = `⌘/Ctrl+O`，`shortcuts.ts:92-96`）+ Tooltip；菜单键盘归 ui-primitives。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/rows/WorkspaceBrowser.tsx:1284-1322`；`E0` `WorkspacePicker.tsx:56-222`；`E0` `shortcuts.ts:92-96`；`E0` `WorkspaceBrowser.module.css:17-30,266-272`。 |
| 13 | 反证 + 验收 | **反证**：若没有目录流占用时保留一个 disabled 的 ＋ 键，用户点它永远没反应、也没有任何解释（`WorkspaceBrowser.tsx:1284-1287` 注释：宁可隐藏也不留死键）。若工作区为空时仍弹一个只有一项的菜单，等于让用户多点一次才进目录选择器（`WorkspacePicker.tsx:148-159` 专治这条）。**验收**：`SBW-05` 在未挂目录流的环境里 ＋ 键不出现（DOM 断言）；`SBW-05b` 工作区为空时点 ＋ 直接起目录流、不出单行菜单（真机）；`SBW-05c` 拾取进行中再开菜单，所有项 disabled（真机截图）。 |

#### `sidebar.workspaces.add-error-dialog`（工作区拾取失败对话框）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.add-error-dialog`。父：`WorkspacePickFlow`（`WorkspacePicker.tsx:204-219`）。出现条件：`createWorkspace` 失败或目录流自身报错（`:130-139,173-177`），`errorOpen=true`。每屏 0..1。 |
| 2 | 网格与定位 | 居中 Modal（ui-primitives），标题 `folderError.title`、内容 `role=alert` 的错误行、footer 两键（`:204-219`）；宽度归 Modal。 |
| 3 | 命中区 | 两键：取消（outline）/ 重试（primary，`disabled={!flowAvailable}`）（`:211-214`）；Modal 自身关闭键（`closeLabel`，`:207`）。 |
| 4 | 手势 | 单击；Enter 默认激活 primary（Modal 行为，归 ui-primitives）；Escape 关闭（Modal 行为，**本包未自定义**）。 |
| 5 | 行为 | 取消：`closeModal` 只清 `errorOpen/modalError`（`:125-128`）；重试：`openDirectoryFlow` → 关错误框、清错误、重开目录流（`:141-146`）。若目录流占用已消失，重试按钮 disabled（`:212-215` 注释：否则开一个没人能应答的流）。幂等。 |
| 6 | 视觉状态 | Modal 常态 + 错误行 error 色 `role=alert`（`WorkspacePicker.module.css` 的 `.modalError`）；primary 键 disabled 态。 |
| 7 | 布局占位与展开形态 | 浮层 Modal（G-03 形态 C 的近亲），不占位。 |
| 8 | 滚动语义 | 错误文本超长时由 Modal 内容区滚动（ui-primitives）。 |
| 9 | 动效 | 归 ui-primitives。 |
| 10 | 边界态 | 无目录流 → 重试 disabled；错误信息是 Host 原文（含路径冲突等）（`:136`）；多语言标题/按钮随 locale。 |
| 11 | 无障碍 | 错误行 `role=alert`（`:218`）；Modal 标题 + closeLabel 由 ui-primitives 输出；焦点陷阱归 ui-primitives。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/WorkspacePicker.tsx:125-146,204-219`。 |
| 13 | 反证 + 验收 | **反证**：若错误框只有「取消」，用户面对一次失败（例如选了不可写目录）必须从头再点 ＋ → 再挑一次目录；若重试在目录流已卸载时仍可点，`renderDirectoryFlow` 渲染空、对话框关掉后流永远打不开。**验收**：`SBW-06` 选一个会被 Host 拒绝的目录，对话框出现且主体是 Host 原文；点重试重新起目录流（真机）；`SBW-06b` 卸载目录流插件后重试键为 disabled（DOM 断言）。 |

#### `sidebar.workspaces.list`（列表滚动盒 + 行动画） ⌁ 组节点（无独立命中，扫描器只抓到子行）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.list`。父：`listArea`（`WorkspaceBrowser.tsx:1343-1420`）。三态共用一个滚动盒：树 / 平铺 / 搜索结果（`:1344-1419`）；`wide` 才渲染。每屏 1 个。 |
| 2 | 网格与定位 | `listArea` `flex:1; min-height:0`；`.list` `flex:1; min-height:0; overflow-y:auto`，左 `-4px` + `padding-left:4px`，右滚条 5px、偏移 2px、`padding-right = 边距 − 5 − 2`、`padding-bottom:16px`、`scrollbar-gutter:stable`（`WorkspaceBrowser.module.css:298-374`）。行间距：`flatList/searchTree/groupSection` 的直接子项 `margin-top:2px`（`:376-380`）；组之间 4px（`:447-449`）。 |
| 3 | 命中区 | 盒本身不命中；行命中见各节点。滚动条槽位由 `scrollbar-gutter:stable` 预留 → 条显隐不改行宽（`:354-374` 注释）。 |
| 4 | 手势 | 滚轮/触控板纵向滚动；行拖拽用 HTML5 拖放（`useNativeDragAcceptance` 在 document 层接住 dragover/drop 以免拖出列表被判拒绝，`WorkspaceBrowser.tsx:88-103`）。父级无横向手势。 |
| 5 | 行为 | 切换分组/排序/归档过滤 → `animationResetKey` 变化 → 清动画、直接落位（`AnimatedRows.tsx:54-57,78-80`）；展开/收起组与「展开更多」→ `resetKey=JSON.stringify([animationResetKey, sessionLimits])` 变化，同样不播 FLIP（`WorkspaceBrowser.tsx:616`）。 |
| 6 | 视觉状态 | 常规态 + 底部 24px 渐隐覆盖（`WorkspaceBrowser.module.css:329-343`；macOS vibrancy 下 `display:none`，`:341-343`）。无 pressed/selected/disabled（选择态在行上）。 |
| 7 | 布局占位与展开形态 | 参与布局流，吃掉侧栏剩余高度；自身是滚动盒而非展开体。列表内容变化时不做高度动画（行 FLIP 只位移/淡入淡出）。 |
| 8 | 滚动语义 | **本面板唯一滚动容器**。滚动到边后的链式外溢：本包未声明 `overscroll-behavior`（grep 0）→ 到边后交浏览器默认（可能滚到祖先）；这是 G-04 的缺口，进 §4 Q-WS-16。行的 `scrollIntoView({block:'nearest'})` 只在 reveal 时调（`Rows.tsx:580-584`）。 |
| 9 | 动效 | FLIP：位移 200ms `ease-out`、淡出 100ms；首次指针/键盘输入后才武装（`AnimatedRows.tsx:46,170-177`）；reduced-motion / resetKey 变化 / 非 ready 时全部跳过（`:54-57`）；退场行克隆成 `position:absolute; inert` 的覆盖层（`:60-73,108-123`）。 |
| 10 | 边界态 | 空：`groups.length===0` 渲染 `EmptySessions`（`:414,618-620`）；loading（`list.phase!=='ready' \|\| !workspaceReady`）时 `ready=false` → 不播动画（`:615`）；搜索态替换内容但仍是同一 `.list`（`:788-789`）—— 搜索滚动位置不会重置（**未见实现**复位）。多语言/大字号只影响行内容。 |
| 11 | 无障碍 | 树态：`role=tree` + `aria-label=section.sessions`（`AnimatedRows.tsx:170-174`；`WorkspaceBrowser.tsx:613`）；搜索结果态另一个 `role=tree` + 自己的 aria-label（`WorkspaceBrowser.tsx:790`）。**没有 `aria-activedescendant`/方向键**（见 §4 Q-WS-04）。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/rows/WorkspaceBrowser.tsx:608-626,672-728,787-829,1343-1420`；`E0` `WorkspaceBrowser.module.css:298-380,443-497`；`E0` `AnimatedRows.tsx:1-183`；`E0` grep `ui-workspace/src` 无 `overscroll-behavior`。 |
| 13 | 反证 + 验收 | **反证**：若滚动条显隐用 `display:none` 或宽度变化，`scrollbar-gutter` 的预留失效 → 每次指针进出侧栏，所有行左右各移 5px，正在读的会话行跟着跳（与 `dsh-sidebar.md` 的 SB-01 同源事故，这里是列表侧的同一条）。若切视图仍播 FLIP，用户会看到两套毫不相干的行互相穿插滑动，定位全靠猜。**验收**：`SBW-07` 指针进出侧栏 5 次，行左缘 x 恒定（录屏逐帧）；`SBW-07b` 切换分组模式，列表直接落位无穿插动画（录屏）。 |

#### `sidebar.workspaces.group-row`（工作区分组行：折叠 / 新建会话 / 悬浮卡 / 拖拽）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.group-row`。父：`.groupSection`。出现条件：每个分组 1 行（真实工作区 + 「未分组」桶）；`groupBy!=='flat'` 才出现（`WorkspaceBrowser.tsx:1361-1419`）；「未分组」桶无菜单/无悬浮卡/不可拖（`Rows.tsx:311-312`；`WorkspaceBrowser.tsx:515-527`）。每屏 0..N。 |
| 2 | 网格与定位 | 行高 **34px**、flex、gap 6px、`padding:0 8px` + 缩进 `padding-inline-start: calc(8px + --dsh-workspace-indent)`（树模式下每层 +12px，`WorkspaceBrowser.tsx:462`）（`Rows.module.css:1-12,99-107`）；行内：16px 文件夹槽 → 16px chevron 槽（hover 才显示）→ 弹性标题 → 行尾动作（hover 才显示）。 |
| 3 | 命中区 | 整行 34px × 整宽可点（`cursor:pointer`，`Rows.module.css:9`）；行内动作键 16×16（**远低于 44dp**）；chevron/folder 同占 16px 槽但只有一行是命中（chevron 只在 hover 出现，不单独绑事件）。行间距 2px。 |
| 4 | 手势 | 单击整行 = 折叠/展开（`Rows.tsx:248`）；单击行尾「新建会话」= 在该组开会话且 `stopPropagation`（`:298-303`）；单击 `⋯` = 开菜单且 `stopPropagation`（`:286-295`）；工作区行可拖（`:249-257`）；无双击/长按/右键。 |
| 5 | 行为 | 折叠：`setGroupExpanded(key,!expanded)`，**折叠同时把该组会话配额重置回 5**（`WorkspaceBrowser.tsx:502-507`）；展开时若该组包含当前会话，会自动展开为真（`:311-314`）。新建会话：先 `setGroupExpanded(key,true)` 再 `startSession(workspaceId)`（`:508-513`）。悬浮 800ms 出悬浮卡（标题 + 缩写路径 + 创建时间 + 复制路径）（`Rows.tsx:160-174,313-328`；`abbreviateHomePath`）。幂等；折叠状态持久化（`stores.ts:99`）。 |
| 6 | 视觉状态 | default：primary 文字、透明底；hover：`interactive-bg-hover` 底（`Rows.module.css:14-17`）；hover 时文件夹图标被 chevron 替换（`:143-146`），chevron 闭合右向、展开旋转 90°（`:149-155`，150ms）；`active`（含当前会话的后代 / 自身含当前会话且展开）：文件夹用 business-primary 色（`:138-140`，`Rows.tsx:236,259`）；`menuOpen`：保留 hover 底色（`:304-307`）；`containsCurrentDescendant` 让祖先在子树折叠时也高亮（`WorkspaceBrowser.tsx:499`）。disabled 未实现。 |
| 7 | 布局占位与展开形态 | 参与布局流，固定 34px；展开 = 在下方插入子组与子行（G-03 形态 A 的「兄弟被推挤」），不封顶、不内滚。折叠时兄弟上移，靠 FLIP 200ms 过渡；只有 `expanded` 且 `sessions.length` 变化才影响下方。 |
| 8 | 滚动语义 | 随 `.list` 滚动；无内滚。 |
| 9 | 动效 | chevron 旋转 150ms（reduced-motion 关闭，`Rows.module.css:148-155,418-423`）；展开/折叠引起的行位移由 `AnimatedRows` 200ms FLIP；悬浮卡的出现/消失归 ui-primitives 的 `HoverCard`（本包只传 `openDelayMs=800`）。 |
| 10 | 边界态 | 未分组桶：标题是字典文案、无行动作、不可拖（`Rows.tsx:234-235,311-312`；`WorkspaceBrowser.tsx:430`）；工作区名为空 → 显示原始路径（`tree.ts:131-135`）；树模式祖先路径匹配区分平台分隔符（`tree.ts:623-648`）；折叠后组内会话完全卸载（`tree.ts:462-465`）→ 组内滚动位置不保留（**未见实现**）。多语言组名/未分组文案。 |
| 11 | 无障碍 | `role=treeitem` + `aria-expanded`（`Rows.tsx:246-247`）；**无键盘处理、无 roving tabindex**（同 §0.2②）；动作键 `aria-label` 带组名（`:289,302`），新建键带 `aria-keyshortcuts=session.new`（`:301`）；悬浮卡内容不参与无障碍（HoverCard 是纯视觉补充）。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/rows/Rows.tsx:160-174,205-329`；`E0` `Rows.module.css:1-17,99-155,255-271,302-307,393-423`；`E0` `WorkspaceBrowser.tsx:462,496-527,311-314`；`E0` `tree.ts:328-376,623-648`；`E0` `stores.ts:99`。 |
| 13 | 反证 + 验收 | **反证**：若 hover 时不换图标，行首永远是文件夹，用户不知道点它会「收起」（`Rows.module.css:143-146` 专治这条）；若折叠不重置配额，用户在大组里点过「展开更多」后每次折叠再展开都直接全展开，列表重新变成几十行、定位丢失（`WorkspaceBrowser.tsx:502-507` 就是这条）。若「未分组」桶也给菜单，改名/删除会没有工作区对象可操作。**验收**：`SBW-08` hover 组行出现 chevron 且点行收起（真机）；`SBW-08b` 展开一个大组到全量 → 折叠 → 再展开，行数回到 5（DOM 断言）；`SBW-08c` 未分组桶 hover 无 ⋯/无新建键（DOM 断言）。 |

#### `sidebar.workspaces.group-menu`（工作区 `⋯` 菜单：改名 / 删除工作区）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.group-menu`。父：组行行尾动作条。出现条件：仅**真实工作区**行（`actions!==undefined`）渲染 `⋯`（`Rows.tsx:269-296`）；未分组桶无菜单。每屏 0..N 个触发器，菜单恒 2 项（`rename` / `delete.workspace`，delete 为 danger）。 |
| 2 | 网格与定位 | 触发器 16×16、圆角 xs、tertiary 图标（`Rows.module.css:393-410`）；`rowActions` `gap:10px`（`:255-263`）；菜单 `portal` + `closeOnPointerLeave`，`anchor` 是触发器（`Rows.tsx:282-296`）。菜单几何归 ui-primitives。 |
| 3 | 命中区 | 触发器 16×16（**远低于 44dp**）；与「新建会话」键相距 10px。 |
| 4 | 手势 | 单击 `⋯` 开合（`Rows.tsx:290`）；菜单项单击选中；鼠标移出菜单即关（`closeOnPointerLeave`）；无长按/右键。 |
| 5 | 行为 | 选中 `rename` → 开 `workspace-rename-dialog`（`WorkspaceBrowser.tsx:1406-1413`）；`delete` → 开 `workspace-delete-dialog`（`:1414-1417`）；未知 id 提前 return（不落 destructive 分支，`Rows.tsx:274-281` 注释）。选中后先关菜单（`:275`）。幂等（每次都只拉起一个对话框）。 |
| 6 | 视觉状态 | 触发器 default tertiary / hover primary（`Rows.module.css:408-410`）；`menuOpen` 时行保持 hover 底与动作条常显（`:265-271,302-307`）；delete 项红色归 ui-primitives。disabled 未实现（Host 拒绝时在对话框里报错）。 |
| 7 | 布局占位与展开形态 | 菜单浮层不占位（G-01）。 |
| 8 | 滚动语义 | 菜单自身滚动归 ui-primitives；不在 `.list` 内滚。 |
| 9 | 动效 | 归 ui-primitives。 |
| 10 | 边界态 | 菜单打开时该行悬浮卡被禁用（`Rows.tsx:323`）；工作区名超长只影响触发器 aria-label；多语言菜单项文案。 |
| 11 | 无障碍 | 触发器 `aria-label=actions.workspace.aria`（带工作区名）（`Rows.tsx:289`）；**无 `aria-haspopup`/`aria-expanded`**（与会话行 ⋯ 同样缺口，`dsh-sidebar.md` §1.2 E 已记）；菜单键盘归 ui-primitives。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/rows/Rows.tsx:238-241,268-296`；`E0` `Rows.module.css:255-271,302-307,393-410`；`E0` `WorkspaceBrowser.tsx:1406-1417`。 |
| 13 | 反证 + 验收 | **反证**：若 ⋯ 的点击不 `stopPropagation`，点菜单会把组一并折叠，用户刚想改名却先收起列表，改完还要再展开（`Rows.tsx:290` 就是这条）。若菜单项 id 落 else 分支做删除，未来新增一个菜单项会继承删除语义 —— 破坏性动作必须显式分派（`:274-281` 注释）。**验收**：`SBW-09` 点 ⋯ 组不折叠、菜单出现（真机）；`SBW-09b` 菜单同时只存在一个、移出即关（真机截图）。 |

#### `sidebar.workspaces.workspace-rename-dialog`（工作区改名对话框）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.workspace-rename-dialog`。父：`WorkspaceBrowser` 根（`WorkspaceBrowser.tsx:1422-1455`）。出现条件：`renameTarget!==null`（菜单选「改名」后）。每屏 0..1。 |
| 2 | 网格与定位 | 居中 Modal，标题 `rename.workspace.title`，body 一个输入框（高 44px、圆角 lg、0.5px 边框、14px 字）（`WorkspaceBrowser.module.css:554-568`），footer 取消/改名两键（`WorkspaceBrowser.tsx:1427-1432`）。错误行 `margin-top:8px`、12/18 error 色（`:574-579`）。 |
| 3 | 命中区 | 输入框整框 44px 高（≥44dp）；两键按钮归 ui-primitives（默认尺寸）；Modal 关闭键。 |
| 4 | 手势 | 单击按钮；输入框聚焦即全选；Enter 提交（IME composing 时忽略）；Escape 关闭（Modal 行为，本包未自定义）。 |
| 5 | 行为 | draft 由点菜单时的显示标题 seed（`WorkspaceBrowser.tsx:1406-1413`）；`renameBlocked` = 进行中 / 空 / **未改动** / **与其它工作区重名**（`:1138-1145`）→ 确认键 disabled；确认 → `renameWorkspace` → 成功关框，失败把 Host 消息写 `renameError`（`:1151-1162`）。关闭时进行中不关（`:1146-1150`）。改名会「钉住」自动生成的名字（seed 与存储标题不同时，提交即固定，`:1130-1133` 注释）。幂等（重复提交被 blocked）。 |
| 6 | 视觉状态 | 输入框 default / focus（无自定义 outline）；disabled 时 dimmed 文字（`:570-572`）；错误行 error 色 + `role=alert`；确认键 disabled 当 blocked。 |
| 7 | 布局占位与展开形态 | 浮层 Modal，不占位。 |
| 8 | 滚动语义 | 内容超出时 Modal 内容区滚动（ui-primitives）。 |
| 9 | 动效 | 归 ui-primitives。 |
| 10 | 边界态 | 重名 → 实时 `role=alert`「命名冲突」且确认键 disabled（`:1142-1143,1451-1453`）；Host 失败 → 第二条 error 行（`:1454`）；空/纯空白 → blocked；多语言标题/占位。大字号只影响输入框文字。 |
| 11 | 无障碍 | 输入框 `aria-label=field.workspaceName`、`data-modal-autofocus`（`:1437-1438`）；错误行 `role=alert`；Modal 焦点陷阱归 ui-primitives。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/rows/WorkspaceBrowser.tsx:1130-1162,1422-1455`；`WorkspaceBrowser.module.css:554-579`。 |
| 13 | 反证 + 验收 | **反证**：若「未改动」不 blocked，用户点开改名框、直接回车会把自动生成的名字永久钉住（看起来什么都没发生，其实改名了）；若允许重名，两个同名分组在列表里无法区分、按名字查找的工作区操作会命中错的那个。**验收**：`SBW-10` 打开改名框不改内容，确认键 disabled（DOM 断言）；`SBW-10b` 输入已存在名字，冲突提示出现且确认键 disabled；改成新名字后列表标题更新（真机）。 |

#### `sidebar.workspaces.workspace-delete-dialog`（删除工作区确认框）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.workspace-delete-dialog`。父：`WorkspaceBrowser` 根（`WorkspaceBrowser.tsx:1457-1481`）。出现条件：`deleteTarget!==null`（菜单选「删除工作区」后）。每屏 0..1。 |
| 2 | 网格与定位 | 居中 Modal：标题 `delete.workspace`、description 带工作区名、footer 取消/删除两键（删除键红字 `deleteAction`）（`:1457-1477`；`WorkspaceBrowser.module.css:581-583`）。 |
| 3 | 命中区 | 两键 + Modal 关闭键（ui-primitives 默认尺寸）。 |
| 4 | 手势 | 单击；Escape/遮罩关闭（Modal 行为，本包未自定义）。 |
| 5 | 行为 | 确认 → `deleteWorkspace(id)`；**成功后不立即关框**，而是等列表投影里不再含该 id 才关（防一帧陈旧列表被下一次「创建工作区」误用，`:1190-1205` 注释）；进行中显示 `role=status` 的 pending 行（`:1479`）；失败写 `role=alert` 错误行并恢复可点（`:1480,1201-1204`）。关闭时进行中不关（`:1185-1189`）。不可撤销（无 undo；会话本身留在 Host 账上？—— **本包不可见**，见 §4 Q-WS-15）。 |
| 6 | 视觉状态 | 删除键红字（`:581-583`）；deleting 时两键 disabled + pending 状态行；错误行 error 色。 |
| 7 | 布局占位与展开形态 | 浮层 Modal，不占位。删除成功后该组随列表重排（FLIP 200ms）。 |
| 8 | 滚动语义 | Modal 内容区滚动（ui-primitives）。 |
| 9 | 动效 | 归 ui-primitives；行退场由 `AnimatedRows` 100ms 淡出。 |
| 10 | 边界态 | 删除中不可取消/不可关闭；失败可重试；多语言标题/描述。 |
| 11 | 无障碍 | description 文本由 Modal 关联；pending `role=status`、错误 `role=alert`；焦点陷阱归 ui-primitives。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/rows/WorkspaceBrowser.tsx:1174-1205,1457-1481`；`WorkspaceBrowser.module.css:581-589`。 |
| 13 | 反证 + 验收 | **反证**：若成功后立刻关框，React 下一帧还会渲染一份含已删 id 的旧列表，此时用户马上点 ＋ 创建同名工作区，投影与 Host 会短暂不一致（`:1197-1199` 注释明确这条）。若删除不做二次确认，工作区（含其会话账）一次误点就没了。**验收**：`SBW-11` 删除一个空工作区，确认后列表里组消失且不报错（真机）；`SBW-11b` 删除失败时错误行出现、按钮恢复可点（注入失败的单测）。 |

#### `sidebar.workspaces.session-row`（会话行：单击打开 / 选中态 / 归档灰化 / 定位回填）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.session-row`。父：`.groupSection` 或平铺列表。出现条件：分组展开（或 `flat` 模式）时每个可见会话 1 行；空白行只显示当前这一个（`tree.ts:238-257`）；子代理会话不出现（`:244`）；归档行按过滤策略出现（`:246-256`）。每屏 0..N（每组默认 ≤5 条空闲 + 运行中的）。 |
| 2 | 网格与定位 | 行高 **32px**、`gap:0`、`padding:0 8px`、圆角 md、缩进同组（`Rows.module.css:1-12,109-117`）；行内：16px 状态槽（`slot` 16×20）→ 4px → 标题（14/20 flex）→ 6px → 时间（10px/16）→ 置顶标记（16×20，`margin-left:6px`）→ 动作条（hover 才出现）。行间距 2px。 |
| 3 | 命中区 | 整行 32px × 整宽（`cursor:pointer`，`Rows.module.css:9`）。行内动作键 16×16（**<44dp**）；行高也低于 44dp → 移动端需扩边（Q-03）。行与行最小间隔 2px（`.flatList > * + *` 等，`WorkspaceBrowser.module.css:376-380`）。 |
| 4 | 手势 | 单击整行 = 打开（`Rows.tsx:598`）；双击**标题**才改名（见 `.session-title`）；行尾键各自 stopPropagation；拖拽排序见 `.reorder`；行 hover 800ms 出悬浮卡（见 `.session-hover-card`）。行本身无长按/右键/滑动。与父滚动的仲裁：Web 的 `click` 天然不吞纵向滚动（无自定义阈值）。 |
| 5 | 行为 | 打开 → `onOpen(id)` → `guardedOpen`：归档会话改为 `notifyArchivedNotOpenable()`（toast）不导航（`WorkspaceBrowser.tsx:900-908`）；否则 `uiWorkspace.openSession`（`index.ts:143-145`）。空白行不可改名/不可拖/无时间（`Rows.tsx:563-565,649-656`）；空白行标题显示 `session.new`（`Rows.tsx:47-50`）。行内 `⋯` 打开时 `onClose` 写回（见 `.session-menu`）。选中态由 `currentId`（主视图 retained 的会话）决定，**不是**「最后点击」（`WorkspaceBrowser.tsx:294-296`）。幂等；不可撤销（切会话本身可再切回）。 |
| 6 | 视觉状态 | default：primary 文字、透明底；hover：`interactive-bg-hover` 底（`Rows.module.css:14-17`）；`selected`：同一 hover 底色（`:19-21`）→ **选中与 hover 同色**，只能靠「当前会话」语义区分（见反证）；`menuOpen`：保持 hover 底（`:304-307`）；`archived`：标题降到 caption 色、点击不打开（`:244-249`；`Rows.tsx:592-597`）；`dragging`/drop 标记：2px 插入线 + 前导尖角（`:309-349`）；`blank`：无时间、无动作条（`Rows.tsx:649-666`）。`focus-visible`：行不聚焦（无 tabindex）→ 无。 |
| 7 | 布局占位与展开形态 | 参与布局流，固定 32px；无展开体（内容在会话主区）；组折叠/展开时随组插入或卸载；下方兄弟被推挤（FLIP 过渡）。 |
| 8 | 滚动语义 | 随 `.list` 滚动；reveal 时 `scrollIntoView({block:'nearest'})`（`Rows.tsx:580-584`）；到边行为归 `.list`（§4 Q-WS-16）。 |
| 9 | 动效 | 标题跑马灯：hover 后按 0.03px/ms 匀速爬到最右再停，离开**一步回 0**（`Rows.tsx:52-57,102-134`）；reduced-motion 直接跳到最右（`:112-115`）；左右边缘渐隐 12px（`Rows.module.css:195-211`）；溢出 ≤8px 不动（`Rows.tsx:54,111`）。增删/移动 FLIP 200ms。 |
| 10 | 边界态 | 空白行：标题=「新会话」、无时间/无动作/不可拖/不可改名（`Rows.tsx:639-667`）；归档行：灰化 + `aria-description` + 点击 toast + 状态槽留白（`:631-635,597`）；未命名会话 → `session.untitled`（`:49`）；超长标题 ellipsis，hover 时变 `text-overflow:clip` 让跑马灯露字（`Rows.module.css:291-300`，触屏 hover 锁存时避免丢省略号，`:291-295` 注释）；时间格式：`刚刚/5分钟/…`（`Rows.tsx:136-140`），有 trailing 状态文案时时间被替换并 `aria-hidden`（`:649-656`）；多语言/大字号只影响文字。 |
| 11 | 无障碍 | `role=treeitem` + `aria-selected` + 归档时 `aria-description`（`Rows.tsx:595-597`）；状态点带视觉隐藏状态文案（`Rows.tsx:400-409`）；**无键盘导航、无 tabindex**（§4 Q-WS-04）；行内按钮各自有 aria-label；空白行按钮不渲染。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/rows/Rows.tsx:47-158,336-419,524-703`；`E0` `Rows.module.css:1-21,109-127,165-300,309-349`；`E0` `WorkspaceBrowser.tsx:294-296,900-908,133-140`；`E0` `tree.ts:238-257,397-418`。 |
| 13 | 反证 + 验收 | **反证**：若选中态改用「最后点击」而不是「主视图保留的会话」，切到另一个会话后列表高亮会停在旧行（主区已换人），用户会点错；若归档行可打开，打开的是一个「已归档」的空壳会话，用户以为丢了内容（DSH 选择 toast 提示，`:900-905`）。若行高不定（32 固定）且标题跑马灯直接改变布局，整列会随 hover 抖动（`contain`/ellipsis 隔离）。**验收**：`SBW-12` 点击会话行，主区切换且该行呈 selected 背景（真机）；`SBW-12b` hover 到超长标题，标题在行内爬动、行高与相邻行位置不变（录屏逐帧）；`SBW-12c` 点归档行出现 toast、主区不切换（真机 + DOM 断言）。 |

#### `sidebar.workspaces.session-title`（标题：双击改名 + hover 跑马灯）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.session-title`。父：会话行。出现条件：会话行渲染即出现（空白行显示「新会话」但不接受双击，`Rows.tsx:639-641`）。每行 1 个。 |
| 2 | 网格与定位 | `margin:0 6px 0 4px`、`flex:1`、`min-width:0`、14px/20 行高、nowrap + ellipsis（`Rows.module.css:115-117,165-172,191-193`）。左侧与 16px 状态槽的间隔 4px（figma cell 注释，`Rows.module.css:109`）。 |
| 3 | 命中区 | 标题文本区域（行内剩余宽）；**双击命中区 = 标题 span**，不是整行（`Rows.tsx:636-644`）。高度 20px（行内垂直居中）。 |
| 4 | 手势 | **双击** = 改名（空白行无）；hover 进入 = 启动跑马灯，离开 = 复位（`Rows.tsx:599-600,636-644`）；单击不单独绑定（冒泡到行 = 打开）。与行拖拽的仲裁：`draggable` 在行上，双击不改拖拽（`draggable` 默认会吞双击？—— 实测 DSH 在 Web 上双击可改名；本包未显式处理 `onDragStart` 与双击冲突 → **未见实现**专门仲裁）。 |
| 5 | 行为 | 双击 → `onRenameRequest(node.id, row.title)` → 浏览器级 `requestSessionRename` → `shell.overlay` 里的 `session-rename-dialog`（`Rows.tsx:639-641`；`WorkspaceBrowser.tsx:1373`；`index.ts:153-155,222-227`）。跑马灯：见字段 9。幂等（重复双击只重新请求同一个会话的改名框）。 |
| 6 | 视觉状态 | default 用行文字色；hover：`text-overflow:clip`（仅在 `@media (hover:hover)` 下，`:295-300`）；跑马灯位移中：左/右边缘 12px 渐隐（`data-scrolled`/`data-clipped`，`:195-211`）；归档：caption 色（`:244-249`）。无 pressed/disabled。 |
| 7 | 布局占位与展开形态 | 参与布局流（行内 flex:1）；跑马灯**只改内部 `scrollLeft`/mask，不改变 span 宽高** → 不引起兄弟重排。 |
| 8 | 滚动语义 | 标题自身是 `overflow:hidden` 的水平裁剪盒，由脚本 `scrollTo({left,behavior:'instant'})` 移动（`Rows.tsx:67-76`）；不参与页面/列表滚动。 |
| 9 | 动效 | 跑马灯速度 `0.03px/ms`、rAF 逐帧（`Rows.tsx:57,119-125`）；离开一步回 0（`:127-132`，注释解释为何不做回程动画）；reduced-motion 直接跳到最右（`:112-115`）；溢出 ≤8px 不动（`:52-54,111`）。 |
| 10 | 边界态 | 未命名/空白 → 本地化标题（`Rows.tsx:47-50`）；超长 → ellipsis，hover 时露出全文（跑马灯）；无溢出 → 不动；触屏 tap 后 `:hover` 锁存 → 用 `@media (hover:hover)` 兜住（`Rows.module.css:291-295` 注释）；多语言 RTL：未处理（**未见实现**）。 |
| 11 | 无障碍 | 标题是纯文本，不是按钮；改名只能靠双击（**键盘/读屏用户不可达** → §4 Q-WS-04）；跑马灯不影响 a11y（视觉）。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/rows/Rows.tsx:52-134,636-644`；`E0` `Rows.module.css:109-121,165-211,244-249,291-300`；`E0` `WorkspaceBrowser.tsx:1373`；`E0` `index.ts:153-155,222-227`。 |
| 13 | 反证 + 验收 | **反证**：若双击不 `stopPropagation`，改名框弹出的同时行也会打开那个会话（用户正在改名却被切走）。若跑马灯用 CSS 动画改 `transform`，标题的 ellipsis 与裁剪会打架、末尾字永远看不清 —— 所以 DSH 用真实 `scrollLeft` + 两边渐隐（`Rows.tsx:59-76` 注释）。**验收**：`SBW-13` 双击标题只弹改名框、主区不切换（真机 + 事件断言）；`SBW-13b` hover 超长标题 3s，末尾字完整露在渐隐内（截图比对）。 |

#### `sidebar.workspaces.session-actions`（行尾动作条：`⋯` + 归档/置顶悬浮键）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.session-actions`。父：会话行。出现条件：非空白行才渲染（`Rows.tsx:663`）；default 下 `display:none`，`:hover` 或 `menuOpen` 才出现（`Rows.module.css:258-271`）。每行 1 条，条内 1 个 `⋯` + N 个 slot 注册键（本包注册 archive(100)/pin(200)，`index.ts:301-304`）。 |
| 2 | 网格与定位 | `flex:none`、`display:inline-flex`、`gap:10px`；每个键 16×16（`Rows.module.css:255-263,393-410`）；位于行尾（时间/置顶标记原本占的右缘格）。 |
| 3 | 命中区 | 每键 16×16（**远低于 44dp**；Q-03）。键间距 10px。整条 `stopPropagation` 从行点击里分离（`Rows.tsx:664`）。 |
| 4 | 手势 | 单击；无长按/右键/双击。行悬浮时出现、离开消失 → 每次操作前需要先 hover（触屏不可达，§4 Q-WS-10）。 |
| 5 | 行为 | `⋯` → 开会话菜单（`.session-menu`）；归档键 → `archiveSession/unarchiveSession`（归档成功出 6s toast，带撤销 + 「查看归档」）；置顶键 → `pinSession/unpinSession`；失败出 pinFailed/unpinFailed 提示（`index.ts:169-179,186-200`）。三者都**不打开行**（条级 stopPropagation，`Rows.tsx:660-664` 注释）。幂等；归档可撤销（toast），置顶可再点。 |
| 6 | 视觉状态 | default：隐藏（`display:none` 不占宽）；hover/menuOpen：显示，键 tertiary、键 hover primary（`Rows.module.css:258-271,408-410`）；同时时间隐藏（`:279-282`）、置顶标记隐藏（`:284-289`）；归档行只显示 unarchive 图标（`ArchiveSession.tsx:60-71`），置顶键在归档行不渲染（`PinSession.tsx:56`）。 |
| 7 | 布局占位与展开形态 | **不占位**：`display:none`→`inline-flex` 的切换发生在行尾固定格内（时间被同步隐藏），因此行宽不变、标题不缩 —— 但 `display` 切换会让行内最后一段「多出几像素」的重新分配（时间 10px vs 3 键 ≈58px）→ **标题宽会变**（hover 时 ellipsis 位置跳动）；`text-overflow:clip` + 跑马灯承接（`Rows.module.css:291-300`）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无过渡（display 直切）。 |
| 10 | 边界态 | 空白行无动作条（无法改名/归档/置顶一个还没开始的会话，`Rows.tsx:645-649` 注释）；归档行：置顶键消失、归档键变恢复；`menuOpen` 时动作条常显（菜单打开期间不闪）；多语言 aria 文案。 |
| 11 | 无障碍 | 三个键各自 `aria-label`（带会话标题：`actions.session.aria`、`actions.archive`、`actions.pin`）（`Rows.tsx:674`；`ArchiveSession.tsx:61-65`；`PinSession.tsx:58-62`）；Tooltip 带快捷键（归档 `session.archive`、置顶无）；**`⋯` 无 `aria-haspopup/aria-expanded`**；触屏/键盘不可达（display:none 不进入 Tab 序，`display:none` 元素本就不可聚焦 → hover 前无法 Tab 到它们，**键盘用户拿不到行内动作**）。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/rows/Rows.tsx:660-689`；`E0` `Rows.module.css:255-289,393-410`；`E0` `session-actions/ArchiveSession.tsx:56-72`；`E0` `session-actions/PinSession.tsx:53-69`；`E0` `index.ts:169-200,301-304`。 |
| 13 | 反证 + 验收 | **反证**：若动作条常显，每行右侧固定被三个图标占掉 ≈58px，280px 侧栏里标题只剩 100px 左右，几乎所有标题都变省略号；若不做条级 `stopPropagation`，点归档会先把该会话打开再归档。**验收**：`SBW-14` 非 hover 态行内无额外图标、标题可用宽最大（截图）；`SBW-14b` hover 后点归档，会话未被打开（主区不变）且出现带撤销的 toast（真机）；`SBW-14c` 键盘 Tab 能否到达行内动作 —— 记录为已知缺口（`SBW-14c` 预期失败，作为 Q-WS-04 的证据）。 |

#### `sidebar.workspaces.session-menu`（会话 `⋯` 菜单：置顶 / 改名 / 派生 / 归档） ⌁ 无扫描命中（菜单项是 slot 里的 `onSelect`，扫描器只认 `onClick`）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.session-menu`。父：动作条 `⋯`。出现条件：非空白会话行；菜单 4 项，顺序/pin(100)、rename(200)、fork(300)、archive(400)（`index.ts:295-300`）；插件行按 order 插空（`contract/slots.ts:136-147`）。archive 项在归档行显示为「取消归档」（`ArchiveSession.tsx:31-48`）；pin 项在归档行**不渲染**（`PinSession.tsx:34`）。每屏 0..1 个菜单。 |
| 2 | 网格与定位 | 菜单 `portal` + `closeOnPointerLeave`，锚在 `⋯`（`Rows.tsx:665-687`）；项几何归 ui-primitives 的 `MenuItemButton`；rename/fork/archive 项带命令快捷键提示；archive 在非归档行带 `session.archive` 快捷键（`ArchiveSession.tsx:35,38-44`）。 |
| 3 | 命中区 | 项高度归 ui-primitives；触发器 16×16。 |
| 4 | 手势 | 单击项；鼠标移出菜单关闭；Escape 关闭（ui-primitives）；无长按/右键。 |
| 5 | 行为 | pin：`pinSession/unpinSession`；rename：`requestSessionRename(sessionId, displayTitle)` → 改名框；fork：`forkSession`（派生在最后完成轮；失败分 unavailable/failed 两种 toast，`index.ts:210-221`）；archive：`archiveSession`（运行中工作会被 Host 拒绝 → 拉起停止确认框，`index.ts:186-197`）。所有项选中后先 `setMenuOpen(false)` 再执行（各 `*MenuItem` 的 `onSelect`）。幂等。 |
| 6 | 视觉状态 | 项 default/hover/selected 归 ui-primitives；archive 项 danger；fork/rename 带快捷键徽标；菜单打开时行保持 hover 底（`Rows.module.css:302-307`）。 |
| 7 | 布局占位与展开形态 | 浮层，不占位。 |
| 8 | 滚动语义 | 菜单自身滚动归 ui-primitives。 |
| 9 | 动效 | 归 ui-primitives。 |
| 10 | 边界态 | 归档行：pin 不渲染、archive 文案变恢复、归档行不能被 fork？（**本包未拦** → fork 一个归档会话会走 Host，结果**未见实现**处理，见 §4 Q-WS-11）；空白行无菜单；插件行可插入；多语言项文案。 |
| 11 | 无障碍 | 项是 `role=menuitem` 的 button（`contract/slots.ts:142` 契约）；**键盘走查与焦点归还在 ui-primitives Menu**（`slots.ts:145-147` 说明「list 的键盘走查与焦点归还读 DOM」）；触发器缺 `aria-haspopup/aria-expanded`（同 `.session-actions`）。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/rows/Rows.tsx:665-687`；`E0` `index.ts:295-304`；`E0` `session-actions/{Pin,Rename,Fork,Archive}Session.tsx` 的 `*MenuItem`；`E0` `contract/slots.ts:136-147`。 |
| 13 | 反证 + 验收 | **反证**：若归档项不随状态换文案，用户在归档行上会看到「归档」并再归档一次（无操作或报错），永远找不到恢复入口；若 pin 项不隐藏，Host 的 pin/archive 互斥会让点击静默失败（`PinSession.tsx:1-7` 注释说明互斥）。若派生项对归档行可用，会派生出带归档父的会话，后续 source 打开提示互相矛盾。**验收**：`SBW-15` 归档行菜单只有 3 项（无名 pin、archive 显示「取消归档」）（DOM 断言）；`SBW-15b` 运行中会话点归档出现停止确认框（真机）；`SBW-15c` 派生失败出对应 toast（单测）。 |

#### `sidebar.workspaces.session-hover-card`（悬浮卡：800ms 延迟 + 复制标题/路径） ⌁ 无扫描命中（触发器是 ui-primitives `HoverCard` 的 hover 计时，扫描器不认）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.session-hover-card`。父：会话行 / 工作区行。出现条件：指针在同一行停留 **800ms**（`Rows.tsx:696,322`）；会话行在菜单打开或拖拽进行中时不出现（`:697`）；工作区行在菜单打开时不出现（`:323`）；空白行/未分组桶：无复制文本或**根本不挂 card**（会话：`copyText` 为空但 card 仍会开，`Rows.tsx:692-701`；工作区：无 `createdAt` 直接 `return ownRow`，`:311-312`）。每屏 0..1。 |
| 2 | 网格与定位 | 浮层（HoverCard 自带定位，锚为整行）；内容 `flex column gap:8px`（`Rows.module.css:352-357`）：标题 14/20 白、路径 12/16 `#CFD3D6`、时间 12/16；状态行 `gap:8px`、12/20 `#ADB2B8`（`:358-391`）。位置（上/下/左/右）归 ui-primitives。 |
| 3 | 命中区 | 卡片本身可悬停/可点「复制」按钮（复制动作由 ui-primitives HoverCard 提供，`copyText/copyLabel/copiedLabel` 是入参，`Rows.tsx:324-326,698-700`）。卡片与行之间需要有可穿过的间隙，否则打开即关 —— 归 ui-primitives。 |
| 4 | 手势 | 指针停留触发；移出关闭；卡片上「复制」单击（HoverCard 内部）。触屏：无 hover → **本节点在触屏不可达**（§4 Q-WS-07）。 |
| 5 | 行为 | 会话卡内容：标题 → 相对时间（`hoverTimeLabel`，now 桶不带 ago）→ `sidebar.session.row.hover` 插槽 → 每条状态（StateDot + 文案）→ 归档行追加归档行（`Rows.tsx:426-457`）。工作区卡：工作区名 → `abbreviateHomePath(cwd)` 路径 → 创建时间（绝对时间，走字典模板而非 `toLocaleString`，`:148-158,161-174`）。复制：会话复制标题（空白/空标题不给复制文本），工作区复制 `cwd`（`:698,324`）。幂等。 |
| 6 | 视觉状态 | 深色浮层、两主题固定色（`Rows.module.css:351-391`）；归档行状态：done/idle 被过滤，只留活的状态 + 归档行（`Rows.tsx:434-435,449-454`）；无 pressed/disabled。 |
| 7 | 布局占位与展开形态 | 浮层（G-01 不占位）；不在滚动流内。 |
| 8 | 滚动语义 | 不滚（内容极短，超长标题 `overflow-wrap:break-word`，路径 `word-break:break-all`，`Rows.module.css:358-376`）。 |
| 9 | 动效 | 归 ui-primitives（延迟 800ms 由本包给）。 |
| 10 | 边界态 | 空白行：标题「新会话」，无时间（`Rows.tsx:441`）；归档行：追加归档说明；超长标题/路径折行；无工作区 cwd（未分组桶）→ 无卡；多语言时间模板与「复制/已复制」文案。 |
| 11 | 无障碍 | 纯视觉补充：内容不进入无障碍树（HoverCard 非 dialog，无 role）；**复制动作对键盘/读屏用户不可达**（§4 Q-WS-07）。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/rows/Rows.tsx:142-174,311-328,421-457,692-702`；`E0` `Rows.module.css:351-391`。 |
| 13 | 反证 + 验收 | **反证**：若延迟为 0 或极短，指针扫过列表时每行都闪一张卡，侧栏被卡片盖住无法选取（800ms 是「停留」语义）；若菜单打开时不禁用卡，行内菜单与卡会同时抢同一个锚点、互相遮挡。**验收**：`SBW-16` 指针匀速扫过 5 行不出现卡片，停在某行 900ms 出现（真机录屏）；`SBW-16b` 点开 ⋯ 后卡片不再出现（真机）。 |

#### `sidebar.workspaces.reorder`（拖拽排序：会话行 / 工作区行）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.reorder`。父：`.list`。出现条件：会话行 `draggable` = 非空白 + 非归档 + 有 drag 接线（`Rows.tsx:573`；分组视图与 flat 视图都给接线，`WorkspaceBrowser.tsx:540-563,700-720`）；工作区行 `draggable` = 真实工作区（`Rows.tsx:249`）。每屏 N 个可拖行。 |
| 2 | 网格与定位 | 插入标记是行上的伪元素，**绝对定位**、`left:0; right:4px; height:12px`，前导 5×7 双尖角 + 2px 横线（`Rows.module.css:309-349`）；工作区标记同构造、`-8px` 偏移（`WorkspaceBrowser.module.css:451-497`）；列表首边界另有一个在滚动裁剪之外的标记（`:479-489`）。 |
| 3 | 命中区 | 整行可作拖起原点（HTML5 draggable 作用于整行）；落点按行高的一半判 before/after（`rowHalf`，`Rows.tsx:200-203`；`workspaceGroupHalf`，`WorkspaceBrowser.tsx:212-215`）。 |
| 4 | 手势 | 长按拖动（HTML5 `dragstart`→`dragover`→`drop`→`dragend`）；拖起需先移动超过浏览器阈值；拖到空白处不提交（document 级 `dragover/drop` 只 preventDefault 防判拒，`WorkspaceBrowser.tsx:88-103`）。触屏/长按等价：**未见实现**（§4 Q-WS-10）。与列表滚动的仲裁：HTML5 拖拽期间自动滚动由浏览器负责（本包无自定义）。 |
| 5 | 行为 | 会话拖放：只在**同组 + 同 pinned 块**内可落（`Rows.tsx:610-617`；`WorkspaceBrowser.tsx:536-546`）；提交 `sessionDragOrder`：从可见顺序算插入点、空白行强制 after、`pinCurrentBlank` 钉首位、写 `setSessionOrder(accountKey, order)` → **自动切到 `manual`**（`WorkspaceBrowser.tsx:182-203,358-372`；`stores.ts:114-119`）。工作区拖放：只在同一父级兄弟间移动 → `insertWorkspaceBefore(workspaceId, anchor)`（`WorkspaceBrowser.tsx:373-394`）。**放下即写入 Host/本地；无撤销**。 |
| 6 | 视觉状态 | 拖起行：原行 `data-dragging`（`TabPanel` 外的行）无特殊样式；兼容目标行 hover 时显示插入标记；**不兼容目标**（不同组/不同置顶块）无标记且 `dropEffect='none'`（`WorkspaceBrowser.tsx:474-480`）；`menuOpen`/`archived` 行不可拖；`sessionDropCommitted` 防重复提交（`:303,358-360`）。 |
| 7 | 布局占位与展开形态 | 插入标记不改变布局（绝对定位）；拖动不挪行（只用标记），落定后一次性重排 + FLIP 200ms。 |
| 8 | 滚动语义 | 拖到列表边缘时的自动滚动：浏览器原生行为（本包未实现自定义）→ **未见实现**可控边缘滚动；`.list` 无 `overscroll-behavior`。 |
| 9 | 动效 | 落定后的行位移 200ms（AnimatedRows）；插入标记瞬现（无过渡）。 |
| 10 | 边界态 | 跨组 / 跨置顶块 / 归档 / 空白：不可落（`source.pinned!==drag.pinned`、`target.pinned!==drag.pinned`、`source.blank` 都直接 return，`WorkspaceBrowser.tsx:188-192`）；拖到列表顶部：工作区有专属首边界标记，会话无（会话只能在行间插，不能直接插到组标题前 → 语义上等价于组首）。多语言不影响（无文案）；`manual` 顺序按账户持久化。 |
| 11 | 无障碍 | 纯指针拖放；**无键盘排序、无「上移/下移」命令**（§4 Q-WS-04）；插入标记 `pointer-events:none` 且是伪元素 → 不进无障碍树 → 读屏用户得不到任何排序反馈。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/rows/Rows.tsx:180-203,249-257,601-624`；`E0` `Rows.module.css:309-349`；`E0` `WorkspaceBrowser.tsx:88-103,170-215,358-394,456-494,533-580,700-720`；`E0` `WorkspaceBrowser.module.css:451-497`；`E0` `stores.ts:114-119`；`E0` `tree.ts:217-223`。 |
| 13 | 反证 + 验收 | **反证**：若允许跨组拖，会话会从工作区 A 掉进 B，但 Host 侧的 workspace 归属没变 → 下次拉取列表它又跳回去，用户会认为排序坏了。若置顶行能与普通行互换，置顶「永远靠前」的承诺失效（`tree.ts:270-287` 明确置顶分区）。若拖放不先判「已提交」而重复触发 drop，一条拖拽会写两次顺序、可能把行挪两次。**验收**：`SBW-17` A 组行拖到 B 组行上，无插入标记且落下无变化（真机）；`SBW-17b` 置顶行拖到普通行上被拒、在置顶块内可互换（真机）；`SBW-17c` 拖拽后一次性只写一条顺序（单测断言 `setSessionOrder` 调用次数）。 |

#### `sidebar.workspaces.overflow`（每组「展开 / 收起」配额按钮）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.overflow`。父：`.groupSection`。出现条件：该组可见会话数 > 配额（默认 5 条空闲；`collapsed.hiddenCount>0`）且组展开（`WorkspaceBrowser.tsx:419-426,581`）。每屏 0..N（每个超限组 1 个）。 |
| 2 | 网格与定位 | `width:100%; height:28px`、无边框、圆角 sm、`padding:0 12px 0 calc(28px + indent)`、左对齐、12px tertiary 文字（`WorkspaceBrowser.module.css:499-514`）；紧跟组内最后一行。 |
| 3 | 命中区 | 整条 100%×28px（**高度 <44dp**）。 |
| 4 | 手势 | 单击（`WorkspaceBrowser.tsx:587`）；无双击/长按/右键。 |
| 5 | 行为 | 未全展开 → 点一次 +5；剩余 ≤5 → 直接放 `Infinity`；已全展开 → 点一次收回 5（`:587-596`）。折叠组会把配额重置为 5（`.group-row` 字段 5）。把配额变化写进 `resetKey` → 不播 FLIP（`:616`）。搜索 reveal 需要让隐藏行可见时，会强制该组配额 `Infinity`（`:350-356`）。幂等（同一状态重复点收敛到边界）。 |
| 6 | 视觉状态 | default 透明底 + tertiary 文案；hover 底色仍透明、文字升到 secondary（`:516-519`）——**没有背景反馈**；无 focus-visible 自定义（走浏览器默认）；`aria-expanded=false/true` 表「是否还有隐藏」。 |
| 7 | 布局占位与展开形态 | 参与布局流，固定 28px；它是**配额开关**不是展开体：点了之后在组尾追加行（兄弟被推挤），不做高度动画。 |
| 8 | 滚动语义 | 不滚；随 `.list` 滚。 |
| 9 | 动效 | 无自身动画；新增行同时出现（resetKey 变化跳过 FLIP，`AnimatedRows.tsx:54-57`）—— 即点「展开」是**直接跳变**而不是动画。 |
| 10 | 边界态 | 组内只有运行中/空白行时永不显示（它们不占配额，`WorkspaceBrowser.tsx:57-70`）；配额被搜索 reveal 抬到 Infinity 后，该组不再显示按钮（`hiddenCount===0`）；`archivedFilter=only` 时隐藏行统计同样生效。多语言：`sessions.expand` 带 `{n}`、`sessions.collapse`。 |
| 11 | 无障碍 | 原生 button + `aria-expanded`（`:582-586`）；文案由字典输出「再展开 N 条 / 收起」；键盘可达（Tab 停）；无 `aria-controls`（**未见实现**）。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/rows/WorkspaceBrowser.tsx:54-70,343-356,414-426,581-602,616`；`E0` `WorkspaceBrowser.module.css:499-519`。 |
| 13 | 反证 + 验收 | **反证**：若运行中/空白行也占配额，一个正在跑的会话可能被藏在「再展开 3 条」后面，用户看不到它还在跑（状态点被藏）；若这里用无限列表一次渲染所有会话，侧栏一次挂几百行，打开侧栏第一帧就卡（DSH 明确没有虚拟化，用配额代替）。**验收**：`SBW-18` 组内 12 条空闲 + 1 条运行中，默认显示 5 空闲 + 运行中，按钮文案「再展开 7 条」（DOM 断言）；`SBW-18b` 点按钮到全展开再点回到 5（真机）。 |

#### `sidebar.workspaces.empty`（空态：无会话 / 归档-only）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.empty`。父：`.list`。出现条件：`groups.length===0`（`WorkspaceBrowser.tsx:414,618-620`）或 flat 模式 `rows.length===0`（`:677,681-683`）。两种文案：`empty.none` / `empty.noneArchived`（`:264-278`）。每屏 0..1。 |
| 2 | 网格与定位 | 纵向居中，`margin-top:80px`、`padding:0 12px`、gap 8px、13/20 文字、tertiary（`WorkspaceBrowser.module.css:527-542`）；图标 24px、caption 色（`:539-542`）。 |
| 3 | 命中区 | 仅归档-only 时有一个文字链按钮（`empty.viewOthers`，`padding:0`、13/20，命中=文字本身）；其余无可点。 |
| 4 | 手势 | 单击「查看其它」；无长按/双击。 |
| 5 | 行为 | 点「查看其它」→ `actions.setArchivedFilter('default')`（`WorkspaceBrowser.tsx:909,272`）→ 列表回到「隐藏归档」视图。非归档-only 的空态无动作。 |
| 6 | 视觉状态 | default：图标 + 文案（+ 可选链接）；无 hover/pressed（链接用 link 色，`WorkspaceBrowser.module.css:544-552`）；无 disabled。 |
| 7 | 布局占位与展开形态 | 参与布局流（在 `.list` 内）；不展开、不滚动（高度可小于视口）。 |
| 8 | 滚动语义 | 随 `.list`；内容短时不产生滚动。 |
| 9 | 动效 | 无（图标与文案直出）。 |
| 10 | 边界态 | `archivedFilter=only` 且无归档 → 「没有已归档会话」+「查看其它」（`:266-275`）；普通空 → 「没有会话」；无工作区且无会话 → 同上；多语言两套文案。 |
| 11 | 无障碍 | 纯文本 + 一个原生 button（文字链样式，`aria-label` 未额外提供，读作正文）；图标是装饰（未标 `aria-hidden`，**未见实现**）。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/rows/WorkspaceBrowser.tsx:264-278,414,618-620,677-683,909`；`E0` `WorkspaceBrowser.module.css:521-552`。 |
| 13 | 反证 + 验收 | **反证**：若归档-only 空态只写「没有会话」而不给回程，用户以为会话全丢了、只能去别处找筛选开关；若空态居中占据整个列表区且不可滚，窗口很矮时会溢出被裁（`margin-top:80px` 在极矮侧栏会被切，**未见实现**适配）。**验收**：`SBW-19` 归档筛选到「只看归档」且无归档时出现两条文案 + 回程链，点回程列表恢复（真机）；`SBW-19b` 320px 高侧栏里空态不裁切（截图）。 |

#### `sidebar.workspaces.session-rename-dialog`（会话改名对话框）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.session-rename-dialog`。父：`shell.overlay`（frame 级浮层，**不是**行内），由 `SessionRenameDialog` 渲染（`index.ts:307-310`；`RenameSession.tsx:47-59`）。出现条件：`renameRequest!==null`（标题双击或菜单「改名」）。每屏 0..1。 |
| 2 | 网格与定位 | 居中 Modal：标题 `rename.session.title`，输入框 44px 高（`WorkspaceBrowser.module.css:554-568` 同款 `.renameInput`），footer 取消/改名（`RenameSession.tsx:91-123`）。 |
| 3 | 命中区 | 输入框整框；两键归 ui-primitives；Modal 关闭键。 |
| 4 | 手势 | 单击；聚焦全选；Enter 提交（IME composing 忽略）；Escape 关闭（Modal 行为）。 |
| 5 | 行为 | draft seed = 请求里的 `currentTitle`；`blocked` = 进行中或空名 → 确认键 disabled（`RenameSession.tsx:73-74`）；确认 → `renameSession`（走 session controller 的 `binding.session.rename`，`index.ts:161-168`）→ 成功 `settleSessionRename()` 关框；失败写 error。**会话没有客户端重名规则**，未改动也不 blocked（`:41-43` 注释）。改名期间对话框以 `key=sessionId` 重挂载 → 新请求得到新 draft（`:51-53`）。 |
| 6 | 视觉状态 | 输入框 default/disabled；错误行 `role=alert` error 色；确认键 disabled 当 blocked。 |
| 7 | 布局占位与展开形态 | 浮层，不占位；行在改名期间**可能已被折叠导致卸载**，对话框在 overlay 层存活（`:1-6` 注释）。 |
| 8 | 滚动语义 | Modal 内容区滚动。 |
| 9 | 动效 | 归 ui-primitives。 |
| 10 | 边界态 | 空名 → blocked；Host 失败 → 错误行；行卸载（侧栏折叠）不影响对话框；多语言标题/字段名。 |
| 11 | 无障碍 | 输入框 `aria-label=field.sessionName` + `data-modal-autofocus`；错误 `role=alert`；Modal 焦点陷阱归 ui-primitives。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/session-actions/RenameSession.tsx:47-124`；`E0` `index.ts:153-155,222-227,307-310`。 |
| 13 | 反证 + 验收 | **反证**：若对话框挂在行内，侧栏一折叠行就卸载、改名框跟着消失（用户正在输入却被吞掉）；若不允许未改动提交，改名的幂等重试会在 Host 侧产生一次无意义写入。**验收**：`SBW-20` 双击标题改名、折叠侧栏后对话框仍在且可提交（真机）；`SBW-20b` 空名确认键 disabled（DOM 断言）。 |

#### `sidebar.workspaces.archive-confirm-dialog`（停止并归档确认框）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.archive-confirm-dialog`。父：`shell.overlay`（`index.ts:311-313`）。出现条件：Host 以 `workspace/session-active` 拒绝归档时（`index.ts:186-197,336-340`）。每屏 0..1。 |
| 2 | 网格与定位 | 居中 Modal：标题 `archive.confirm.title`、描述带会话标题、活动清单 `ul aria-label`（kind → 计数 + 项目名）、footer 取消/停止并归档（`ArchiveSession.tsx:122-151`）。 |
| 3 | 命中区 | 两键 + 关闭键（ui-primitives 默认尺寸）。 |
| 4 | 手势 | 单击；Escape/遮罩关闭（Modal 行为）。 |
| 5 | 行为 | 取消 → `settleSessionArchive()` 清请求、会话继续跑（`:107-110`）；确认 → `stopAndArchiveSession`（先停工作再归档，`index.ts:205-208`）→ 成功 toast `stoppedAndArchived`（带撤销）。失败 → error 行 + 可重试（`:111-121`）。幂等。 |
| 6 | 视觉状态 | 活动清单 13/20 primary，`overflow-wrap:anywhere`（`WorkspaceBrowser.module.css:591-602`）；确认键红字 `deleteAction`；pending `role=status`；error `role=alert`。 |
| 7 | 布局占位与展开形态 | 浮层，不占位。 |
| 8 | 滚动语义 | 清单长时 Modal 内容区滚动。 |
| 9 | 动效 | 归 ui-primitives。 |
| 10 | 边界态 | 活动 kind 未收录 → 走通用行（`ArchiveSession.tsx:164-170`）；多种活动同时存在 → 多条 li；aria-label 的清单名 `archive.confirm.activity`；多语言复数（one/other）。 |
| 11 | 无障碍 | Modal 标题/描述；活动清单 `ul` 有 `aria-label`；pending/error 有 role；焦点陷阱归 ui-primitives。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/session-actions/ArchiveSession.tsx:74-171`；`E0` `index.ts:180-209,336-340`；`E0` `WorkspaceBrowser.module.css:581-602`。 |
| 13 | 反证 + 验收 | **反证**：若对运行中的会话静默归档（不停工作），Host 会拒绝而 UI 只给一条 toast，用户不知道为什么没归档；若这里默认就停掉用户的工作而不列清单，等于静默杀掉正在跑的回合/子代理/作业。**验收**：`SBW-21` 运行中会话归档 → 对话框列出运行中的活动；取消后会话仍在跑（真机）；`SBW-21b` 确认后出现「已停止并归档」+ 撤销（真机）。 |

#### `sidebar.workspaces.row-toast`（行操作通知：归档撤销 / fork 失败 / 置顶失败…）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.row-toast`。父：`shell.overlay`（`index.ts:314-318`）。出现条件：`toast!==null`；同一时刻**只有一条**（`RowActionToast.tsx:1-3`）。每屏 0..1。 |
| 2 | 网格与定位 | `Toast`（ui-primitives）默认位置；长驻类（归档/停止归档/fork 恢复/创建失败）`holdMs=6000`（`RowActionToast.tsx:13,30,36,46,63`），其余用默认时长。 |
| 3 | 命中区 | 动作按钮（最多 2 个：撤销 + 查看归档；fork 失败时 1 个「打开来源会话」）；关闭动作由 Toast 自身提供。 |
| 4 | 手势 | 单击动作；Toast 自动消失；无长按。 |
| 5 | 行为 | `archived/stoppedAndArchived` → 撤销 = `unarchiveSession`；若当前归档行不可见，额外给「查看归档」= `setArchivedFilter('show')`（`:39-56`）；`forkRequiresOpen` → 打开来源会话（归档则改提示不可打开）（`:24-37`；`index.ts:228-240`）；`pinFailed/unpinFailed/defaultWorkspaceFailed/archivedNotOpenable` → 纯文案（`:78-92`）；`createFailed` → 带 Host 原因（`:57-67`）。所有动作先 `dismissToast()`。幂等。 |
| 6 | 视觉状态 | success tone（归档类）/ warning 图标（fork 失败、创建失败、普通警告）（`:29,45,62,72`）；按 `seq` 换 key 让新通知重新计时（`:28,43,60,71`）。 |
| 7 | 布局占位与展开形态 | 浮层（G-01 不占位）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | Toast 自身进出归 ui-primitives。 |
| 10 | 边界态 | 父级重渲染不延长停留（`:1-3` 注释）；归档行已经可见时不给「查看归档」（`:22,49-51`）；多语言文案与复数行（`archive.confirm.*` 用在这里的活动清单里）。 |
| 11 | 无障碍 | Toast 自身语义归 ui-primitives（通常 `role=status`）；动作是原生 button；观察者：读屏播报由 Toast 承担。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/session-actions/RowActionToast.tsx:1-92`；`E0` `index.ts:115-117,169-179,228-240,314-318`；`E0` `stores.ts:127`。 |
| 13 | 反证 + 验收 | **反证**：若归档不给撤销，误点归档后用户只能靠「查看归档 → 逐条恢复」，且不知道是哪个会话刚被归档；若通知在父级每次重渲染时重置计时，侧栏只要有任何状态更新通知就永远不消失（`:1-3` 注释专治）。**验收**：`SBW-22` 归档一个会话出现 6s 带撤销通知，点撤销后该行恢复（真机）；`SBW-22b` 通知停留期间操作别的行，通知不延长（真机计时）。 |

#### `sidebar.workspaces.hero-picker`（会话空态的工作区选择器，同源组件） ⌁ 无扫描命中（`Menu` 的 `onSelect` 不在扫描模式内）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.workspaces.hero-picker`。注册位：`conversation.hero.workspace`（`index.ts:320-328`）—— 即**聊天空态**里的工作区选择入口，不是侧栏。组件 = 同一份 `WorkspacePickFlow`（`WorkspacePicker.tsx:230-256`）。出现条件：空态槽位被占用且打开（`open`）。每屏 0..1。 |
| 2 | 网格与定位 | 锚点由宿主（空态卡片按钮）给 `anchorRef`，菜单 `side="bottom"`（`WorkspacePicker.tsx:56-76,188-201`）；`addOnly=false` 时列出已有工作区 + pin 在底部的「添加工作区」（`:106-119`）。 |
| 3 | 命中区 | 菜单项归 ui-primitives Menu；「添加工作区」项在底部固定（`pinAdd`，`:109-119`）。 |
| 4 | 手势 | 单击项；外点/Escape 关闭（`onClose`，Menu 行为）。 |
| 5 | 行为 | 选已有工作区 → `onPick(id)`（宿主打开该工作区的新会话）；选「添加工作区」→ `openDirectoryFlow()` 关菜单起目录流；目录流占用卸载时打开中的流自动撤回（`:98-105`）；失败进 `add-error-dialog`。幂等。 |
| 6 | 视觉状态 | 菜单项 disabled 当 `flowBusy`（`:107,117`）；pending 时菜单旁 `role=status` loading 文案（`:202`）；空菜单不渲染（`menuIsEmpty`，`:120-123`）。 |
| 7 | 布局占位与展开形态 | 浮层，不占位。 |
| 8 | 滚动语义 | 菜单项多时由 Menu 滚动。 |
| 9 | 动效 | 归 ui-primitives。 |
| 10 | 边界态 | 无目录流 → 只列已有工作区（`addEntries=[]`）；无工作区且无目录流 → 连菜单都不开（`menuIsEmpty`）；列表 pending → 菜单保持并显示 loading；多语言项文案。 |
| 11 | 无障碍 | 菜单语义归 ui-primitives；本组件不额外提供 aria。 |
| 12 | 依据 | `E0` `ui-workspace/src/client/WorkspacePicker.tsx:56-222,230-256`；`E0` `index.ts:264-267,320-328`。 |
| 13 | 反证 + 验收 | **反证**：若空态选择器自己实现一套「新建工作区」表单（而不是复用目录流），就会出现第二套错误处理与第二种体验，与侧栏 ＋ 的行为分叉（本包刻意复用同一个 `WorkspacePickFlow`，`WorkspacePicker.tsx:1-10` 注释）。**验收**：`SBW-23` 空态选择器与侧栏 ＋ 的菜单项、错误框、loading 行为逐项一致（对照截图 + 真机）；`SBW-23b` 卸载目录流后两处都不再提供添加项（DOM 断言）。 |

### 2.2 `base-sidebar-right.*`（右栏：壳 + 页签 + 三个内容类型）

> 本节全部归 `base.sidebar-right` 基座（`10-panels.md` §2）。**页签条本体在 `ui-dockkit`**，
> 本路只把「右栏座位/开合/内容派发」写成节点，tab 条写成指针节点（§1.2 B）。

#### `base-sidebar-right.panel`（右栏座位与内容派发 · 组节点）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.panel`。所属 `base.sidebar-right` → 右栏座位。父：`RightbarRoot`（按会话各挂一棵，`RightbarRoot.tsx:17-37`）→ `RightbarSeat`（`SidebarRight.tsx:352-422`）→ `SidebarPanel`（`:302-343`）。出现条件：当前会话存在 surface（`RightbarSeat` 在 mount 时 `actions.open(sessionId)` 物化，`:369-371`）；非当前会话的座位 `hidden`（`RightbarRoot.tsx:22`）。每屏 1 个可见座位。 |
| 2 | 网格与定位 | 面板 `position:absolute; top/right/bottom:0`，宽 = frame 给的 `width`（push 模式）或 `100vw`（fullscreen）（`SidebarRight.tsx:307-311`；`SidebarRight.module.css:31-42`）；CSS 变量 `--dsh-sidebar-width` 同步给 dockkit 做“滑出距离”（`:49`）。浮层 z：dock 10 / fullscreen 40 / float 60（`:32-33,64-66`）。 |
| 3 | 命中区 | 面板本身不命中；内部由 dockkit 页签与 tab 内容承接（`:318-341`）。 |
| 4 | 手势 | 无自定义手势（右栏宽度拖拽在 `ui-layout`，§1.2 A）；指针/键盘事件照常穿透到 dockkit。 |
| 5 | 行为 | ①开合由 `syncPresentation({shown,track,fullscreen})` 驱动 frame：`shown` 时 `layout.openRightbar(track,fullscreen)`，否则 `closeRightbar()`（`index.ts:188-192`）；②座位卸载/会话切换时报告 `shown:false` 归还轨道（`SidebarRight.tsx:407-411`）；③内容按 tab kind 从 `sidebar.right.pane.tab` keyed 派发，未知 kind 显示「不可用」占位（`:193-241`）；④折叠时 docked 内容**保持挂载**、只平移出右缘（避免重载丢失状态，`SidebarRight.module.css:44-62` 注释）。 |
| 6 | 视觉状态 | `expanded=false`：docked 子树 `translateX(宽)` + `visibility:hidden`（`SidebarRight.module.css:46-54`）；`expanded=true`：`transform:none; visibility:visible`（`:56-62`）；浮动面板在两种态下都可见（`:68-72` 注释；`SidebarRight.tsx:316` 的 `aria-hidden` 只在无浮层时生效）；fullscreen：`width:100vw`（`SidebarRight.tsx:310-311`）、Windows 下 `max-width: calc(100vw − 系统侧栏宽)`（`SidebarRight.module.css:74-77`）。 |
| 7 | 布局占位与展开形态 | **网格列**（占位）而非抽屉：push 模式占 frame 右轨道（track），fullscreen 模式仍保留底下轨道但覆盖视口（`SidebarRight.tsx:1-21` 文件头注释）。G-03 三形态不适用（基座布局，见 §4 Q-WS-09）。 |
| 8 | 滚动语义 | 面板不滚；每个 tab 内容自滚（files/doc/terminal 各自的 scrollport）。dockkit 的分隔条拖动与浮动窗不参与外层滚动。 |
| 9 | 动效 | 开/合是一次从右缘的滑动：`transform` + `visibility` 用 `--ds-transition-duration-slow` / `--ds-ease-in-out`，与 frame 轨道同步（`SidebarRight.module.css:51-62`）；fullscreen 进入时**先等覆盖动画结束再报 track**（`SidebarRight.tsx:386-406`）；reduced-motion 关掉 transition（`:104-110`）；可中断（再点收起会重新起一次 transition）。 |
| 10 | 边界态 | 视口 <768 → 自动全屏（`autoFullscreen`），手动模式记录不被改写（`SidebarRight.tsx:356-367`）；`canShow=false` 时强制折叠（`:373-375`）；Windows/macOS 全屏的标题栏与红绿灯避让（`SidebarRight.module.css:74-102`）；窄 pane 时 tab body `overflow:hidden`（`:162-169`）。 |
| 11 | 无障碍 | 面板无 role；`aria-hidden` 只在「折叠且无浮层」时置真（`SidebarRight.tsx:316`）；焦点连续性：pane 元素被替换后按 occurrence 找回同一页焦点（`focus.ts:73-107`），点 pane 空白处把焦点交给 pane（`:111-120`）。 |
| 12 | 依据 | `E0` `ui-sidebar-right/src/client/shell/SidebarRight.tsx:1-29,149-241,302-343,352-422`；`E0` `SidebarRight.module.css:1-169`；`E0` `RightbarRoot.tsx:17-37`；`E0` `index.ts:188-195,203-233`；`E0` `focus.ts:56-137`。 |
| 13 | 反证 + 验收 | **反证**：若折叠时把 docked 内容卸载（`display:none` + unmount），每次开合文件树/终端/文档都会重载一遍：终端重连、PDF 重新解析、文件树重拉（`SidebarRight.module.css:8-18` 注释就是这条的取舍）；若 fullscreen 在覆盖动画开始前就上报 track，frame 会先把会话列压窄，用户看到「先挤一下再盖住」。**验收**：`RSB-01` 折叠再展开终端页，终端仍是同一进程、滚动缓冲不丢（真机 + 进程断言）；`RSB-01b` 全屏进入过程中会话列宽度不变直到面板覆盖完成（录屏逐帧）。 |

#### `base-sidebar-right.panel.collapse`（收起右栏）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.panel.collapse`。父：面板页签条右端 chrome 席（dockkit 渲染 chrome，`SidebarRight.tsx:334-337`；`ui-dockkit/src/components/TabPanel.tsx:391-403`）。出现条件：面板已展开（dock 或 fullscreen）时恒在。每屏 1 个。 |
| 2 | 网格与定位 | 28×28、`padding:6px`、15px 图标、圆角 sm（`SidebarRight.module.css:112-137`）；位于 tab 条最右端（`data-sidebar-right-toggle`，`SidebarRight.tsx:288`）。 |
| 3 | 命中区 | 28×28（**<44dp**）；与全屏键相邻（gap 由 dockkit chrome 决定）。 |
| 4 | 手势 | 单击；无长按/双击。 |
| 5 | 行为 | `actions.toggleExpanded(sessionId)` → `planSetExpanded(!expanded)`（`SidebarRight.tsx:289`；`stores.ts:279-281`）→ 整列平移到右缘外（`SidebarRight.module.css:44-62`）。同义命令 `sidebar.right.toggle`：desktop `⌘/Ctrl+Alt+B`、web `⌘/Ctrl+Shift+B`（`shortcuts.ts:27-42`）。幂等，可撤销（再用折叠态入口打开）。 |
| 6 | 视觉状态 | default secondary 图标、透明底；hover `interactive-bg-hover`；focus 走浏览器默认（本表 `:focus-visible` 未定义）；disabled 未实现。图标是左栏收起图标的镜像（`SidebarRight.module.css:139-142`；`ExpandButton.tsx:12-14` 同源说辞）。 |
| 7 | 布局占位与展开形态 | 参与 dockkit 页签条布局（28×28）；不展开。它的效果是改变整个右栏轨道（布局驱动型）。 |
| 8 | 滚动语义 | 不滚（页签条固定）。 |
| 9 | 动效 | 自身无；触发整列滑动（见 `panel` 字段 9）。 |
| 10 | 边界态 | 窄视口（<768）自动全屏时，命令层 `toggleFullscreen` 把它读作**关闭面板**（`service.ts:618-623` 跨包指针）；本按钮走 `actions.toggleExpanded` 直接收起 → 两条路径结果一致（都回到折叠）；Windows 标题栏避让见 panel 字段 10。 |
| 11 | 无障碍 | 原生 button；`aria-label=chrome.collapseAria`；`aria-keyshortcuts` = `sidebar.right.toggle`（`SidebarRight.tsx:282-292`）；Tooltip 带快捷键。焦点序：页签条内、全屏键之后。 |
| 12 | 依据 | `E0` `ui-sidebar-right/src/client/shell/SidebarRight.tsx:262-296`；`E0` `SidebarRight.module.css:112-142`；`E0` `shortcuts.ts:27-42`；`E0` `stores.ts:276-281`。 |
| 13 | 反证 + 验收 | **反证**：若右栏折叠态不提供任何入口（例如把入口放在右栏自己身上），折叠后列宽为 0，用户永远打不开（DSH 把它放进会话头，见 `.panel.expand`）。若收起按钮只在页签条内、浮层模式下被浮窗覆盖，用户在被浮窗挡住时无法收起整个右栏。**验收**：`RSB-02` 点收起，右栏滑出、会话列恢复全宽（真机录屏）；`RSB-02b` 快捷键与按钮结果一致（真机）。 |

#### `base-sidebar-right.panel.fullscreen`（全屏 / 恢复右栏）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.panel.fullscreen`。父：页签条 chrome 席，收起键左侧（`SidebarRight.tsx:263-281`）。出现条件：面板已展开时恒在。每屏 1 个。 |
| 2 | 网格与定位 | 28×28、15px 自绘 glyph（全屏四角 / 退出双箭头）（`SidebarRight.tsx:243-260`）；`data-sidebar-right-mode` = 下一步模式（`push`/`fullscreen`）（`:276`）。 |
| 3 | 命中区 | 28×28（**<44dp**）。 |
| 4 | 手势 | 单击；无长按/双击。 |
| 5 | 行为 | `toggleFullscreen()` → controller 读当前 target 后切模式（`SidebarRight.tsx:277`；`index.ts:195`）：push↔fullscreen，**fullscreen 保留底下轨道**（`SidebarRight.tsx:8-13`）。另有命令 `pane.fullscreen.toggle` = `⌘/Ctrl+Alt+Enter`（`shortcuts.ts:43-63`），且当目标在浮窗时**拒绝**（`t('command.float')`，`:24-26`）。幂等。 |
| 6 | 视觉状态 | default secondary；hover `interactive-bg-hover`；fullscreen 时图标换成退出图标、Tooltip/aria-label 换成「退出全屏」（`SidebarRight.tsx:264-280`）；disabled 未实现。 |
| 7 | 布局占位与展开形态 | 参与页签条布局 28×28；不展开。切换影响整个面板的 `width` 与 z 层（`SidebarRight.tsx:310-313`）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 面板从 push 宽 → 100vw 的过渡由 frame/轨道过渡承担；本包只改 width/style（`SidebarRight.tsx:310-311`）。 |
| 10 | 边界态 | 视口 <768 时自动全屏；此时该键的 command 路径（`toggleFullscreen`）把「全屏」读作**关闭面板**：`const fullscreen = autoFullscreen \|\| layout.mode==='fullscreen'; if (fullscreen && autoFullscreen) setExpanded(false)`（`service.ts:618-623`，跨包指针），按钮路径 `actions.toggleExpanded` 直接收起，两者结果一致；Windows 全屏 `max-width` 让出标题栏（`SidebarRight.module.css:74-82`）；macOS 全屏让红绿灯（`:84-102`）；浮动窗目标拒绝全屏命令（`shortcuts.ts:24-26`）。 |
| 11 | 无障碍 | 原生 button；`aria-label` 随模式换文案；`aria-keyshortcuts=pane.fullscreen.toggle`；Tooltip 带快捷键。 |
| 12 | 依据 | `E0` `ui-sidebar-right/src/client/shell/SidebarRight.tsx:86-94,243-296`；`E0` `SidebarRight.module.css:64-102`；`E0` `shortcuts.ts:19-63`；`E0` `index.ts:122,195`；`E0`（跨包指针）`service.ts:618-623`（自动全屏时该命令改为关闭）。 |
| 13 | 反证 + 验收 | **反证**：若全屏时不保留底下轨道，退出全屏后会话列要从 100vw 重新分到 `1fr`，用户读完的位置被重新排版；若窄视口下按钮仍表「全屏」，点它等于把面板关掉（用户以为只是放大）——所以自动全屏时命令层把同一动作解释为关闭（`:88-94` 注释）。**验收**：`RSB-03` 全屏→恢复，会话列宽与滚动位置不变（真机 + 读数）；`RSB-03b` 760px 视口下按全屏键，面板关闭而不是变形（真机）。 |

#### `base-sidebar-right.panel.expand`（折叠态唯一入口，在会话头）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.panel.expand`。父：`conversation.session.header.corner` 席位（`index.ts:238-243`）。出现条件：**仅当前会话右栏折叠时**渲染；展开时不渲染（`ExpandButton.tsx:33-38`）。每屏 0..1。 |
| 2 | 网格与定位 | 会话头角落席，自带 `ExpandButton.module.css` 的 `.button`；glyph 是左栏收起图标的镜像（`ExpandButton.tsx:9-14`；`IconPanelLeftOutlineRegular` + `css.icon` 镜像）。尺寸沿用 `Button size="sm"`（`:41-50`）。 |
| 3 | 命中区 | 会话头小按钮（按 `Button sm`，通常 28-32px，**<44dp**）；位于 utilities 行之外独立角落（`ExpandButton.tsx:1-15`）。 |
| 4 | 手势 | 单击；无长按/双击。 |
| 5 | 行为 | `actions.setExpanded(sessionId,true)`（`ExpandButton.tsx:47`）→ 右栏展开、内容物化。幂等。不可撤销（再点收起键）。 |
| 6 | 视觉状态 | 仅 default / hover / focus（Button 由 ui-primitives 提供）；无 disabled。`data-sidebar-right-expand` 供测试定位（`:46`）。 |
| 7 | 布局占位与展开形态 | 挂在会话头；不展开。它的存在与否**不改变会话头其它控件位置**（角落席在 utilities 之外，`ExpandButton.tsx:6-11` 注释）——展开后席位随按钮一起消失。 |
| 8 | 滚动语义 | 不滚（会话头固定）。 |
| 9 | 动效 | 无自定义；面板滑动见 `panel`。 |
| 10 | 边界态 | 无 surface 的会话按「折叠」处理（store 读不到 `layout.expanded` 即 false，`ExpandButton.tsx:34-37`）；多语言 aria；焦点：会话头内顺序归会话头基座（ui-conversation）。 |
| 11 | 无障碍 | `aria-label=chrome.expandAria`、`aria-keyshortcuts=sidebar.right.toggle`、Tooltip 带快捷键（`ExpandButton.tsx:40-47`）；原生 button。 |
| 12 | 依据 | `E0` `ui-sidebar-right/src/client/shell/ExpandButton.tsx:1-53`；`E0` `index.ts:234-243`。 |
| 13 | 反证 + 验收 | **反证**：若入口放在右栏自己身上（折叠后不可见），或放在全局侧栏，用户收起右栏后就没有任何能打开它的控件（`ExpandButton.tsx:1-15` 注释明写这条：折叠不能给会话区留下任何成本）。若入口在展开态也渲染，会话头会出现一个点了没反应的按钮。**验收**：`RSB-04` 收起右栏后会话头出现入口键、点它右栏滑回（真机）；`RSB-04b` 展开态该键不存在（DOM 断言）。 |

#### `base-sidebar-right.tab-strip`（页签条：选中 / 关闭 / 右键 / 拖拽 / 键盘） ⌁ 无扫描命中（**本节点不登记 facts**：实现全在 `ui-dockkit`）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.tab-strip`。父：每个 docked pane 的顶部（`ui-dockkit/src/components/TabPanel.tsx:245-404`）。出现条件：面板展开时每个 pane 1 条；浮窗有自己的标题栏（`FloatLayer.tsx`）。每屏 1..2 条（最多两栏拆分）。 |
| 2 | 网格与定位 | 条 38px 高（dockkit 样式）；右侧依次：页签 → ＋（仅当该 pane 没有 guide 页，`SidebarRight.tsx:321-324`；`TabPanel.tsx:350-365`）→ 填充 → 拆分键（`:367-390`）→ 宿主 chrome（本包的全屏/收起，`:393-403`）。页签 chip 宽度随标题。 |
| 3 | 命中区 | chip 高度 ≈ 整条（28-30px 级，**<44dp**）；关闭键 14px glyph（`TabPanel.tsx:315-333`）；＋ 键与拆分键同为小图标键。 |
| 4 | 手势 | 单击 chip = 选中（`:289-292`）；单击关闭键 = 关 tab（`:326-329`，stopPropagation）；右键 chip = 开 tab 菜单（`:308-312`）；chip `onPointerDown` 起拖（`:284-288`）；触摸/触控板拖拽走 dockkit pointer 逻辑。 |
| 5 | 行为 | 选中：`focusTab`（`SidebarRight.tsx:163`）；关闭：走资源 owner 的 close handler（`index.ts:225-228`；terminal 的 close handler 在 `ui-sidebar-terminal/src/client/index.ts:75-77`）；不可关：唯一 docked 的 guide 页（`stores.ts:63-72`）；＋ = 在 pane 里开 guide 页（`SidebarRight.tsx:166-171`）；右键菜单含拆分/浮动/关闭 + `sidebar.right.tab.menu.item` 扩展项（`SidebarRight.tsx:332-333`；`TabPanel.tsx:335-343`）。 |
| 6 | 视觉状态 | selected chip（`aria-selected` + `data-dockkit-tab`）、quiet chip（唯一不可关的 guide 当作标签，`TabPanel.tsx:251-254`）、dragging、drop 前插入位（caret，`:257-262,348`）、strip 两端 24px 渐隐（`:126-172`）；关闭键 hover 归 dockkit css。disabled：不可关时关闭键不渲染。 |
| 7 | 布局占位与展开形态 | 参与 pane 布局（38px 固定）；页签超宽时 strip 横向滚动并自动把激活 chip 滚入视口（`:174-198`）；不是展开体。 |
| 8 | 滚动语义 | 页签条横向滚动（`hiddenSides` 渐隐两端，`:131-172`）；内容区滚动在各 tab body。 |
| 9 | 动效 | chip 增删由 React 直切（dockkit 无 FLIP）；激活 chip 自动滚入（`:182-198`）。 |
| 10 | 边界态 | 一 pane 只容纳每种 page（guide/files/text/terminal 各一个 page tab），资源 tab 可重复（`stores.ts:126-138,322-326`）；pane 拆满 2 个后拆分键禁用（`SidebarRight.tsx:321`）；窄 pane 拆分键提示「太窄」（`TabPanel.tsx:203-209`）。 |
| 11 | 无障碍 | `role=tablist`（`TabPanel.tsx:245`）+ `role=tab` + `aria-selected` + roving `tabIndex`（`:263-266`）；**ArrowLeft/Right 循环、Home/End 移焦点，Enter/Space 选中（manual activation）**（`:103-124,293-307`）；关闭键 `aria-label` + `aria-keyshortcuts`（`:316-322`）；拆分键 `aria-label`（`:367-390`）。**这是全包最完整的键盘实现**。 |
| 12 | 依据 | `E0` `ui-dockkit/src/components/TabPanel.tsx:96-124,131-198,211-406`（跨包指针）；`E0` `ui-sidebar-right/src/client/shell/SidebarRight.tsx:149-152,161-181,321-337`；`E0` `stores.ts:63-72,126-138`；`E0` `index.ts:225-228,252-268`。 |
| 13 | 反证 + 验收 | **反证**：若 `role=tablist` 只给 role 不给方向键，读屏用户会听到「选项卡，共 3 个」但按方向键毫无反应 —— 比不加 role 更糟（`TabPanel.tsx:103-107` 注释明确按 WAI-ARIA manual activation 实现）。若关闭不可关的 guide 页，右栏会变成空白面板且再无入口（`stores.ts:63-72`）。**验收**：`RSB-05` 焦点在页签上按 ArrowRight/Home/End 焦点移动且不切换，Enter 才切换（真机 + 读屏）；`RSB-05b` 唯一 guide 页的关闭键不出现（DOM 断言）。 |

#### `base-sidebar-right.guide`（指南页：空态入口容器） ⌁ 无扫描命中（整个 body 是 chain/fallback 组合）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.guide`。父：`sidebar.right.pane.tab` 的 guide kind（`GUIDE_ID`，`index.ts:252-268`；`GUIDE_KIND='guide'`，`contract/seed.ts:36`）。出现条件：右栏展开且当前 pane 没有其它 page → 默认 seed 一个 guide（`stores.ts:110-124`；`SidebarRight.tsx:149-152`）；每 pane 最多 1 个。 |
| 2 | 网格与定位 | 纵向居中列：gap 14px、`padding:0 24px`、`min-height:100%`、`justify-content:center`，`::after` 抬升 10%（`GuideBody.module.css:7-25`）；顶部 56px 罗盘水印（`:27-36`），下方一排 380px 宽胶囊（`:42-63`），每个胶囊 56px 起、`padding:14px 20px`。 |
| 3 | 命中区 | 胶囊整块可点（380×≥56px，**高≥44dp 达标**）；罗盘是装饰（`aria-hidden`，`GuideBody.tsx:81`）。 |
| 4 | 手势 | 单击胶囊（见 `.guide.entry`）；无长按/双击/拖拽。 |
| 5 | 行为 | 渲染链：`sidebar.right.tab.guide` 的 chain 优先（可整块替换空态），无人注册/全部 decline 时渲染内置指南（`GuideBody.tsx:1-19,88-112`）；入口来自各 tab 类型注册的 guide 项，按 `order` 排（terminal 是 order 20，`ui-sidebar-terminal/src/client/index.ts:71-74`；files 是 order 10，`ui-sidebar-files/src/client/definition.tsx:31-38`）。幂等。 |
| 6 | 视觉状态 | 胶囊 default `bg-layer-1` + 0.5px 边框；hover `interactive-bg-hover`（`GuideBody.module.css:59-67`）；无 pressed/selected/disabled（terminal 的自定义胶囊例外，见 `.guide.terminal-entry`）；罗盘随主题取纯灰（`:32-40`）。 |
| 7 | 布局占位与展开形态 | 占满 pane 内容区、自身可随 pane 高度被压缩（`min-height:100%` + 可被 pane 滚动）；不展开。 |
| 8 | 滚动语义 | 内容超出 pane 高度时由 pane body 滚动（指南自身不设 overflow）。 |
| 9 | 动效 | 无自定义动画。 |
| 10 | 边界态 | 入口 ≤4 个时显示描述，>4 个时全部隐藏描述以保持轻量（`GuideBody.tsx:44-45,96-97`）；无任何入口时只剩罗盘（空列）；多语言标题/描述；chain 插件可替换整页（此时本包的 fallback 不渲染）。 |
| 11 | 无障碍 | 无 heading（刻意，`GuideBody.tsx:11-15` 注释：像浏览器起始页）；胶囊是原生 button，`aria-label`/`aria-keyshortcuts` 由入口提供；罗盘装饰。 |
| 12 | 依据 | `E0` `ui-sidebar-right/src/client/tabs/guide/GuideBody.tsx:44-112`；`E0` `GuideBody.module.css:1-123`；`E0` `index.ts:203,244-268`；`E0` `contract/seed.ts:29-46`；`E0` `stores.ts:110-124`；`E0` `SidebarRight.tsx:149-152,321-324`。 |
| 13 | 反证 + 验收 | **反证**：若空态不做成“门”而是常驻页面，用户打开一个 tab 后指南还占着一个页签位（本包用 `replaceTab:true` 让指南让位，`GuideBody.tsx:105`）；若入口超过 4 个仍显示描述，胶囊会变成两行小字列表、pane 里得滚动才能看到全部入口。**验收**：`RSB-06` 空右栏显示指南，点「文件」入口后指南页签被文件页替换（真机）；`RSB-06b` 注册 5 个入口时所有描述消失（DOM 断言）。 |

#### `base-sidebar-right.guide.entry`（指南入口胶囊）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.guide.entry`。父：`guide` 的入口列。出现条件：每个注册了 guide 项的 tab 类型 1 个（`GuideBody.tsx:88-107`）；也可被插件通过 `sidebar.right.tab.guide.entry` 覆盖渲染（`:99-107`）。每屏 0..N（实测内置 4 个：guide 自身不列、files order10、terminal order20、browser order30 —— `ui-sidebar-browser` 非本路）。 |
| 2 | 网格与定位 | 胶囊 380px 宽（`max-width:100%` 收窄）、`min-height:56px`、`padding:14px 20px`、gap 14px、图标 26px 固定盒 + 文本列 + 快捷键徽标（`GuideBody.module.css:42-86`）；描述 11px 仅在 ≤4 入口时出现（`:106-114`）。 |
| 3 | 命中区 | 整块 380×≥56px（**达标**）；快捷键徽标 `pointer-events` 不阻断（`ShortcutKeys` 组件）。 |
| 4 | 手势 | 单击；无长按/双击。 |
| 5 | 行为 | 单击 → `tab.actions.openTab(entry.kind, { replaceTab: true })`：用该类型在**本页签位**打开并替换指南（`GuideBody.tsx:105`）。幂等（指南已不在时不会触发）。 |
| 6 | 视觉状态 | default / hover / focus-visible（`:59-67`）；无 disabled；图标缺失时用占位立方体（`entry.icon ?? CubeGlyph`，`:48-66`）。 |
| 7 | 布局占位与展开形态 | 参与列布局；不展开。 |
| 8 | 滚动语义 | 随 guide/pane 滚动。 |
| 9 | 动效 | 无自定义。 |
| 10 | 边界态 | 描述在 >4 入口时隐藏；无快捷键的入口不显示徽标（`GuideBody.tsx:72`）；类型未注册图标用占位；多语言标题/描述。 |
| 11 | 无障碍 | 原生 button；`aria-keyshortcuts` 取该类型命令（`GuideBody.tsx:61`）；标题/描述是按钮内文本（描述 `aria-hidden` 未标 —— 会一起被读，**可接受**）。 |
| 12 | 依据 | `E0` `ui-sidebar-right/src/client/tabs/guide/GuideBody.tsx:48-75`；`E0` `GuideBody.module.css:42-123`。 |
| 13 | 反证 + 验收 | **反证**：若入口不 `replaceTab`，每开一个内容页就多一个页签，用户很快把右栏塞满（指南的定位是门，`GuideBody.tsx:16-18` 注释）。若描述不随入口数量隐藏，5 个以上入口时胶囊高度翻倍、指南要滚动。**验收**：`RSB-07` 点两个入口，页签数恒为 1（指南被替换，DOM 断言）；`RSB-07b` 5 入口时长文描述不出现（DOM 断言）。 |

#### `base-sidebar-right.files.header`（文件页头：根路径 + 刷新 + 扩展动作）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.files.header`。父：`FilesBody` 根（`FilesBody.tsx:193-220`）。出现条件：`files` tab 有 cwd 且已起树（`cwd===undefined` 时只渲染「无工作区」状态，`:175-182`）。每屏 1 个。 |
| 2 | 网格与定位 | 高 **38px**、`padding:0 6px 0 16px`、gap 4px、下边框 0.5px（`FilesBody.module.css:15-28`）；内容：根路径 `PathLabel`（`margin-right:12px`）→ 刷新键 28×28 → `sidebar.right.tab.files.actions` 插槽（`:205-219`）。 |
| 3 | 命中区 | 刷新键 28×28（**<44dp**）；路径本身不可点（`PathLabel` 是否可点取决于 ui-primitives，本包只传 `path`）。 |
| 4 | 手势 | 单击刷新；无长按/双击。 |
| 5 | 行为 | 刷新 → `refresh(tab.id)`，同时注册为命令 `{refresh}` 供 `page.refresh` 快捷键/页菜单调用（`FilesBody.tsx:145,190-192`）；命令层刷新键 `⌘/Ctrl+R`（web `⌘/Ctrl+Alt+R`，`ui-sidebar-right/src/client/shortcuts.ts:65-97`）。幂等（重复刷新只重拉根层，展开层按 store 保留？—— `refresh` 在 store 里清层，**本包未见「保留展开层」实现**，见 §4 Q-WS-17）。 |
| 6 | 视觉状态 | 刷新键 default secondary / hover primary + 底（`FilesBody.module.css:141-167`）；`aria-pressed` 的自动刷新键在 `<span hidden>` 里（视觉不可见，见 `.files.tree` 字段 10）；disabled 未实现。 |
| 7 | 布局占位与展开形态 | `flex:0 0 auto` 固定 38px；不展开。 |
| 8 | 滚动语义 | 不滚；树体在下方 `.body` 滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 无 cwd → 整个头不渲染（改渲染 no-workspace 状态，`FilesBody.tsx:175-181`）；根路径超长 `PathLabel` 自带裁剪；多语言 aria/tooltip。 |
| 11 | 无障碍 | 刷新键 `aria-label=reload` + `aria-keyshortcuts=tab.refreshShortcut` + Tooltip（`FilesBody.tsx:205-216`）；无 landmark；焦点序：刷新 → 扩展动作。 |
| 12 | 依据 | `E0` `ui-sidebar-files/src/client/FilesBody.tsx:140-231`；`E0` `FilesBody.module.css:15-33,141-172`；`E0` `ui-sidebar-right/src/client/shortcuts.ts:65-97`。 |
| 13 | 反证 + 验收 | **反证**：若刷新不绑定命令层，用户按 `page.refresh` 时刷新的是别的 tab（页签焦点决定目标），文件树显示的内容与快捷键不符；若 38px 行高不固定，路径换行会把下方树整体下推。**验收**：`RSB-08` 焦点在文件页时按刷新快捷键，根层重拉（Network 断言）；`RSB-08b` 超长路径下 header 仍 38px（DOM 断言）。 |

#### `base-sidebar-right.files.tree`（文件树：目录展开 / 文件打开 / other 灰化 / 滚动恢复）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.files.tree`。父：`FilesBody` 的 `.body`（`FilesBody.tsx:221-228`）。出现条件：每个目录层按需渲染（根层恒在）；节点类型 = directory / file / other 三种，各 1 行（`:80-137`）。每屏 0..N 行。 |
| 2 | 网格与定位 | 行 `padding:5px 10px`、gap 6px、圆角 md、`width:100%`、文本 13px（`FilesBody.module.css:70-108`）；每层缩进 18px（`.level .level`，`:60-63`）；图标 16px；行紧挨（无行间 gap，视觉空气来自行内 padding，`:76-78` 注释）。 |
| 3 | 命中区 | 整行（宽 100%、行高 ≈ 16+10=26px 级，**<44dp**）；`other` 行 `aria-disabled`、`cursor:default` 不接收点击（`:105-115`）。 |
| 4 | 手势 | 单击目录 = 展开/折叠（懒加载该层，`FilesBody.tsx:87,171-173`）；单击文件 = 打开（`:98,186-187`）；**无右键菜单、无长按、无多选、无拖拽、无重命名**（本包全无）。 |
| 5 | 行为 | 目录：`toggle(tabId,parent,path,expanded,signal)`（`:185`）→ store 维护 `expanded[]` 与 `levels[path]`；文件：`tabActions.openResource(fileAddressFor(sessionId, root, path))` 交给 `file:` 查看器（`dsh-resource://file/...`，`:186-187`）。层级失败/空/截断各有行（`:114-137`）。幂等（重复点同一目录收敛）。 |
| 6 | 视觉状态 | default 透明底；hover `interactive-bg-hover`（`:88-90`）；目录图标开/合（`IconFolderOpen/Close`，`:88`）；文件图标按类型着色（`FileTypeIcon`，`:99`）；`other` 灰化 tertiary + 无 hover 底（`:112-119`）；loading/failed/empty/truncated 都是 tertiary 小字行（`:121-126`）；无选中态（**未见实现**选中高亮/当前文件）。 |
| 7 | 布局占位与展开形态 | 目录展开 = 在行下方插入 `<ul>` 子层（G-03 形态 A：兄弟被推挤、不封顶、不内滚）；折叠卸载子树（store 仍留层数据 —— 由 `expanded` 控制渲染，`FilesBody.tsx:84-92`）。 |
| 8 | 滚动语义 | 唯一滚动容器是 `.body`（`overflow:auto` + `scrollbar-gutter:stable` + 2px 偏移，`FilesBody.module.css:35-52`）；滚动只写 `scrollTopRef`，**卸载时**才存 store，重挂载按 store 恢复（`FilesBody.tsx:150-167`）；到边行为未声明 `overscroll-behavior`。 |
| 9 | 动效 | 无（展开/折叠直切；没有高度动画）。 |
| 10 | 边界态 | loading 行「加载中」；failed 按 Host 错误码换文案（`not-found`/`outside-workspace`/`not-directory`/其它，`:61-70`）；空目录「空」；截断「已截断」（`:128-136`）；`other` 类型（socket/设备等）灰化不可点（`:105-111`）；**自动刷新按钮在 `<span hidden>` 里**（`:197-204`）→ 视觉不可达，且 `setAutoRefresh` 入口只剩 store 内部（DSH 现状，§4 Q-WS-06）；无工作区 → 顶层状态文案（`:175-181`）；多语言全部走字典。 |
| 11 | 无障碍 | 目录行是 button + `aria-expanded`（`:87`，无 `aria-controls`）；文件行 button（无 expanded）；`other` 用 `aria-disabled` 的 span（不进 Tab 序）；**整棵树没有 `role=tree`/`treeitem`、没有方向键导航**（与侧栏会话列表同一缺口，§4 Q-WS-04）；文本行无 role。 |
| 12 | 依据 | `E0` `ui-sidebar-files/src/client/FilesBody.tsx:38-137,150-167,183-189,217-231`；`E0` `FilesBody.module.css:35-178`。 |
| 13 | 反证 + 验收 | **反证**：若目录行与文件行共用“单击=打开”的语义，点目录会去打开一个无法渲染的目录资源（本包用 `aria-expanded` 区分两件事）；若展开不懒加载而在挂载时递归列全树，侧栏一打开就对着大仓库发几百个 RPC。若 `other` 行也做成可点，用户会反复尝试打开 socket 文件、每次得到同一个错误。**验收**：`RSB-09` 展开一个 3 层目录，只对该层发一次列表请求（Network 断言）；`RSB-09b` 点文件行打开文档预览页签（真机）；`RSB-09c` `other` 行点不动（真机）。 |

#### `base-sidebar-right.terminal.status`（终端页：状态机 / 重连 / 接管 / 新建）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.terminal.status`。父：`TerminalBody`（`ui-sidebar-terminal/src/client/terminal.tsx:24-70`）。出现条件：`terminal` tab 的 store 已有状态（`state!==undefined`）；状态区在「有状态文案 / 可重试 / 只读」任一成立时渲染（`:58`）；`missingTerminal` 时整页换成空态（`:36-45`）。每屏 1 个。 |
| 2 | 网格与定位 | 页体 `flex column`；状态条 `role=status` 在屏幕上方，内含文案 + 内联按钮（`Button variant=outline size=sm`）（`:58-65`）；空态：图标 36px + `role=alert` 文案 + 主按钮（`:36-45`）；`.screen` 吃掉剩余高度（`terminal.module.css:2`）。 |
| 3 | 命中区 | 内联按钮由 ui-primitives Button（sm/md，**<44dp**）；空态主按钮 md（≥44dp 附近）。 |
| 4 | 手势 | 单击按钮；终端本体输入由 xterm 接管（`:106`）；无长按/双击。 |
| 5 | 行为 | 按 phase 分派：`idle/loading` → 「加载中」；`creating/connecting/disconnected` → 对应文案；`info.state==='exited'` → 退出码；`failed` → 「不可用」；`closed` → 已关闭（`:46-53`）。可重试 = 未结束且 `failed` 或 `disconnected`：无 info 走 `refresh()`，有 info 走 `connect()`（`:54,61-63`）；只读（connected+running 但不可写）→ 「接管控制」`connect()`（`:55-60`）；结束/缺失 → 「新建终端」`openTab('terminal',{replaceTab:true})`（`:31-35,64`）。错误行 `role=alert`（`:67`）。幂等（重复重连只在 Host 侧重复一次连接尝试）。 |
| 6 | 视觉状态 | 状态条文案 secondary（`.status`，`terminal.module.css` 相关规则）；错误 error 色；终端屏幕内部由 xterm theme 决定（`TerminalTheme`，`:99`）；xterm 光标闪烁 `cursorBlink:true`、最小对比度 4.5（`:93`）。无 disabled（连接中由文案表达）。 |
| 7 | 布局占位与展开形态 | 状态条是页内布局行（`flex:0 0 auto` 级），会把屏幕下推；不是展开体。`missingTerminal` 是整页空态（G-03 形态：整页替换）。 |
| 8 | 滚动语义 | xterm 自己维护滚动缓冲（`scrollback` 来自 Host 环境，`:94`）；本包不额外滚动；`fitScreen` 按 pane 尺寸算 cols/rows（`:175-184`），可见时才 fit（`:155-159`）。 |
| 9 | 动效 | 无自定义；光标闪烁来自 xterm。 |
| 10 | 边界态 | 缺终端记录（`missingTerminal`）→ 空态 + 新建（`:36-45`）；只读 → 接管；断开 → 重连；退出/关闭 → 新建；密码/worker 失败归 PDF 侧（不适用）；pane 尺寸为 0 时不 fit（`:108,136,156`）；多语言全走字典；大字号：xterm `fontSize` 取主题 `theme.fontSizes.terminal`（`:86,93`）。 |
| 11 | 无障碍 | 状态条 `role=status`（`:58`）；错误 `role=alert`；xterm 的 textarea 被显式设 `aria-label=title`（`:102,160-162`）；隐藏/只读时 `disableStdin`（`:155`）。终端内容对读屏的可读性由 xterm 自身提供（未额外处理）。 |
| 12 | 依据 | `E0` `ui-sidebar-terminal/src/client/terminal.tsx:24-70,73-184`；`E0` `terminal.module.css:1-19`；`E0` `ui-sidebar-terminal/src/client/index.ts:54-101`。 |
| 13 | 反证 + 验收 | **反证**：若只读时不提供「接管控制」，用户看到终端在跑但打不了字、以为是应用坏了；若失败态只显示错误不提供重试，用户只能关 tab 重开（会丢滚动缓冲）。若 pane 尺寸不可见时也 fit，侧栏折叠的瞬间会把终端缩成 2 列再报给 Host，重开后终端内容错位。**验收**：`RSB-10` 断网后终端进入 disconnected 并给出重连，重连成功后屏幕缓冲保留（真机）；`RSB-10b` 只读会话出现「接管控制」且点击可输入（真机）；`RSB-10c` 折叠侧栏时 Host 侧 cols/rows 不被改成 2（Host 读数断言）。 |

#### `base-sidebar-right.terminal.title-rename`（终端页签 chip 名：双击就地改名）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.terminal.title-rename`。父：`sidebar.right.pane.tab.title` 的 terminal 渲染位（`index.ts:99-101`）。出现条件：终端 tab 的 chip / 浮动标题渲染时（`TerminalTitle.tsx:14-16`）。每 tab 1 个。 |
| 2 | 网格与定位 | 渲染 `TerminalIcon` + 名字 span（`.title` 单行 ellipsis，`TerminalTitle.module.css:1`）；编辑态换成 input（`maxLength:120`，`css.name`）。位置由 dockkit 的 chip 布局决定（`TerminalTitle.tsx:21-32` 说明 dockkit 的拖拽把手会接住双击，所以监听挂在 chip 上）。 |
| 3 | 命中区 | chip 文本区域；双击命中；编辑态 input 占同宽。 |
| 4 | 手势 | **双击 chip = 进入改名**（事件挂在最近的 `[data-dockkit-tab]` / `[data-dockkit-float-grip]` 上，`TerminalTitle.tsx:21-32`）；编辑态内 pointerdown/click/dblclick 全 stopPropagation（`:41-43`），避免把按击交给 dockkit 拖拽/选中；Enter/Escape 收尾。 |
| 5 | 行为 | 双击 → `setEditing(true)`（`cancelled=false`）→ 自动 focus + select（`:33-37`）；blur 提交：`value.trim()` 非空且与原 title 不同才 `view(tab.id).rename(next)`（`:44-48`）；Escape 先置 `cancelled` 再 blur → 不提交（`:53-56`）；Enter 只 blur（提交在 blur 里做）（`:56`）；IME composing（`isComposing` 或 keyCode 229）时忽略按键（`:50-52`）。幂等（重复提交同值不请求）。 |
| 6 | 视觉状态 | 非编辑：纯文本；编辑：输入框（同一行高度，无自定义 focus ring）；disabled 未实现。 |
| 7 | 布局占位与展开形态 | 在 chip 内，不改变 page 布局；编辑态替换文本节点（宽度随 chip）。 |
| 8 | 滚动语义 | 不滚；名字超长 ellipsis（`TerminalTitle.module.css:1`）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 名字来自 Host 实时状态（`info.title ?? state.title ?? tab.title`，`TerminalTitle.tsx:16`）；空名/未变不提交；`maxLength=120`；IME 输入不误提交；多语言 aria-label `rename`。 |
| 11 | 无障碍 | input `aria-label=rename`（`:40`）；名字 span 无 role（chip 本身由 dockkit 的 `role=tab` 承载）；**改名只能双击** → 键盘/读屏不可达（§4 Q-WS-04 同类缺口）。 |
| 12 | 依据 | `E0` `ui-sidebar-terminal/src/client/TerminalTitle.tsx:14-60`；`E0` `TerminalTitle.module.css:1`；`E0` `index.ts:99-101`。 |
| 13 | 反证 + 验收 | **反证**：若改名监听不挂在 dockkit chip 上而挂在内部 span，dockkit 的拖拽把手会先捕获 pointer、双击永远到不了改名逻辑（`TerminalTitle.tsx:21-24` 注释明说）。若 Enter 直接提交而不是 blur，某些输入法/组合键情况下会丢最后一次输入。**验收**：`RSB-11` 双击终端 chip 出现输入框并全选，Escape 放弃、Enter 提交（真机）；`RSB-11b` 中文输入法输入后用 Enter 确认不误触发提交（真机 IME）。 |

#### `base-sidebar-right.guide.terminal-entry`（终端类型的指南卡：整卡启动 + shell 选择菜单）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.guide.terminal-entry`。父：`guide` 的入口列（通过 `sidebar.right.tab.guide.entry` keyed 注册，`index.ts:86-93`）。出现条件：terminal 类型已注册（恒在）。每屏 1 个（终端类型只有 1 个 guide 项）。 |
| 2 | 网格与定位 | 卡片与通用胶囊同尺寸（`min-height:56px; padding:14px 20px`，`TerminalGuide.module.css:1`）；内部：铺满的「主按钮」绝对定位层（`.main`，`:2`）+ 26px 图标 + 文本列（标题行内嵌 chevron 触发器 20×20）+ 快捷键徽标（`:55-82`）。 |
| 3 | 命中区 | 整卡 = 主按钮（`.main` `position:absolute; inset:0`，**整卡 56px 高，达标**）；右下 chevron 20×20 是**更上层**的第二个命中（`.trigger`，`:9-10`）。两个命中嵌套：点卡片任意非 chevron 处 = 启动，点 chevron = 开菜单。 |
| 4 | 手势 | 单击主按钮 = 用记住的 shell 开终端；单击 chevron = 开/关 shell 菜单（`TerminalGuide.tsx:73-76`）；无长按/双击。 |
| 5 | 行为 | 启动：`tab.actions.openTab('terminal',{replaceTab:true})`（`TerminalGuide.tsx:57`）；菜单：打开时异步 `loadShells`（loading → ready/failed），失败项给「重试」（`:40-54,67-72`）；选中 shell → `selectShell(path)` + 关菜单 + 用该 shell 开终端（`:67-72`）；菜单打开即重置为 loading（`:74`）。幂等（打开会替换指南页）。 |
| 6 | 视觉状态 | 卡片 default `bg-layer-1` + 0.5px 边框；主按钮 `variant=ghost`（无可见 chrome，整卡为止）；`.main:focus-visible` 内收 2px 环（`TerminalGuide.module.css:3`）；chevron ghost 键；菜单项 disabled 用于 loading/empty/failed（`TerminalGuide.tsx:50-54`）。 |
| 7 | 布局占位与展开形态 | 卡片参与指南列；菜单浮层不占位。 |
| 8 | 滚动语义 | 不滚（菜单滚动归 ui-primitives）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 无可用 shell → 菜单给 `shellEmpty` 禁用项（`TerminalGuide.tsx:64`）；加载失败 → 错误项 + 重试项（`:54`）；Host 无 shell 时主按钮仍可用（走默认 shell）；多语言 shell 名来自 Host。 |
| 11 | 无障碍 | 主按钮 `aria-label` = 标题/描述组合，`aria-keyshortcuts=terminal.new`（`TerminalGuide.tsx:56`）；chevron `aria-label=shell` + `aria-haspopup=menu` + `aria-expanded`（`:73`）；标题与描述 `aria-hidden`（避免与主按钮重复读，`:61,79`）；菜单键盘归 ui-primitives。 |
| 12 | 依据 | `E0` `ui-sidebar-terminal/src/client/TerminalGuide.tsx:24-83`；`E0` `TerminalGuide.module.css:1-12`；`E0` `index.ts:86-93`；`E0` `ui-sidebar-right/src/client/tabs/guide/GuideBody.tsx:99-107`。 |
| 13 | 反证 + 验收 | **反证**：若 chevron 不独立成键，用户想换 shell 时只能先开一个终端（用错的 shell）再关掉重开；若主按钮不用绝对定位铺满卡片，卡片上会有一块“点了没反应”的空白，用户会以为卡坏。**验收**：`RSB-12` 点卡片空白处开终端；点 chevron 只开菜单不开终端（真机）；`RSB-12b` 选另一个 shell 后新终端使用该 shell（Host 断言/终端内 `$SHELL`）。 |

#### `base-sidebar-right.doc.text-preview.header`（文档预览页头：查看器 / 换行 / 刷新 / 扩展动作）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.doc.text-preview.header`。父：`TextPreview` 的 `.preview`（`ui-sidebar-documentpreview/src/client/TextPreview.tsx:265-327`）。出现条件：`text` tab 已能渲染正文（`state!==undefined && selected!==undefined`）；不支持的类型（unviewable 且无渲染器）走另一条 header（只路径 + 扩展动作，`:199-216`）。每屏 1 个。 |
| 2 | 网格与定位 | 高 **38px**、`padding:0 6px 0 16px`、gap 4px、0.5px 下边框（`TextPreview.module.css:12-29`）；内容左起：路径 `PathLabel`（margin-right 12px）→ 查看器下拉（文字按钮，`max-width:160px` ellipsis，`:281-291`）→ 换行键 28×28 → `sidebar.right.tab.document.action`（keyed 插槽）→ 隐藏的自动刷新键 → 刷新键 28×28 → `document.actions` 插槽。 |
| 3 | 命中区 | 各 28×28 键（**<44dp**）；查看器按钮文字区 + `max-width:160px`；扩展插槽按钮由插件决定。 |
| 4 | 手势 | 单击下拉/换行/刷新；无长按/双击。 |
| 5 | 行为 | 查看器下拉：`actions.selected(tab.id,id)`，切换后 `set([])` 重挂资源（`TextPreview.tsx:86-88,278`）；换行：`actions.toggledWrap`（`:295`）；刷新：`reload()`，按当前模式走分页/整字节/渲染器（`:169-174`），并与命令层 `{refresh}` 绑定（`:175`）；自动刷新开关：`actions.toggledAutoRefresh`（`:306`）。幂等。 |
| 6 | 视觉状态 | 28px 圆形键 default secondary / hover primary + 底（`TextPreview.module.css:248-275`）；`aria-pressed=true` 的自动刷新键高亮（`:277-280`，但它在外层 hidden 里）；查看器按钮 hover 归 `.tool`；disabled 未实现。 |
| 7 | 布局占位与展开形态 | `flex:0 0 auto` 38px；不展开；body 在下方占余高（`TextPreview.module.css:1-10`）。 |
| 8 | 滚动语义 | 不滚；body 滚动（`.body`，`:49-66`；代码/PDF 渲染器可换成内层 scrollport，`:74-78`）。 |
| 9 | 动效 | 无过渡（hover 换色瞬切）。 |
| 10 | 边界态 | 只有 1 个候选渲染器 → 不渲染查看器下拉（`TextPreview.tsx:267-268`）；渲染器不支持 wrap → 不渲染换行键（`:285`）；`data/document.actions` 插槽未占用 → 无扩展按钮；`unviewable` 无渲染器 → 走 unsupported 页头（只路径 + actions）；多语言：查看器名来自渲染器定义、按钮 aria 走字典。 |
| 11 | 无障碍 | 查看器按钮 `aria-label=openWith` + `title`（`:272`）；换行键 `aria-pressed` + 稳定 `aria-label=wrap.aria` + Tooltip 表动作（`:288-299`）；刷新键 `aria-keyshortcuts=tab.refreshShortcut`；**自动刷新键在 `<span hidden>` 内**（`:302-310`）→ DOM 在、可访问树/视觉不可达（§4 Q-WS-06）。 |
| 12 | 依据 | `E0` `ui-sidebar-documentpreview/src/client/TextPreview.tsx:162-178,232-327`；`E0` `TextPreview.module.css:12-33,248-291`。 |
| 13 | 反证 + 验收 | **反证**：若刷新不按当前内容模式分派（永远只重拉第一页），用户在「加载更多」之后的滚动位置会被截断重置；若换行键在渲染器不支持时也渲染，点了没反应（`TextPreview.tsx:285` 用 `selected.wrap===true` 门控）。**验收**：`RSB-13` 加载 3 页后点刷新，内容重载且滚动能回到原位置（真机）；`RSB-13b` 图片预览页没有换行键（DOM 断言）。 |

#### `base-sidebar-right.doc.text-preview.state`（正文状态：变更条 / 失败重试 / 加载更多）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.doc.text-preview.state`。父：`.preview`（页头下方 + body 内）。出现条件（互斥子状态）：①`meta.failure && hasContent` → 元数据失败条；②`changed && !meta.failure` → 「文件已变更」条；③body 内失败且已有内容 → 重试行；④无内容失败 → 整页空态（含 retry 或交给 unpreviewable 插槽）；⑤`!eof` → 「加载更多」；⑥无内容未失败 → LoadingIndicator。每屏 0..1 组。 |
| 2 | 网格与定位 | 变更条：`flex:0 0 auto`、gap 10px、`padding:6px 10px`、12px、`bg-layer-2` 底、下边框 0.5px（`TextPreview.module.css:36-47`）；立即重载按钮：`padding:4px 10px` 小胶囊（`:207-221`）；空态：整页居中、gap 16px、`height:100%`、`::after` 抬升 12%（`:118-141`）；重试胶囊 `height:32px`（`:154-174`）；加载更多：`margin:8px 10px` + `padding:4px 10px` 12px 胶囊（`:224-246`）。 |
| 3 | 命中区 | 变更条的「立即重载」= 文字胶囊（高约 26px，**<44dp**）；空态重试 32px 高（**<44dp**）；加载更多胶囊（**<44dp**）；行内其它区域不可点。 |
| 4 | 手势 | 单击三类按钮；无长按/双击。滚到底会自动 `loadNext()`（`TextPreview.tsx:333-341`）。 |
| 5 | 行为 | 变更条：**只提示不自动换内容**（autoRefresh 打开时由 effect 自动 reload：`TextPreview.tsx:176-178`；手动路径点「立即重载」）；失败重试：有内容时 `loadNext()`，无内容时 `reload()`（按 recourse 选）（`:353-390`）；加载更多：`loadNext()`，loading 中 disabled（`:391-401`）；无内容失败时若 recourse 是 `open`，不给重试而渲染 `document.unpreviewable` 插槽（交给能打开它的插件）（`:376-377`）。幂等（重复加载同页由 store 的 loading 位挡住，`:145-149`）。 |
| 6 | 视觉状态 | 变更条 secondary 文字 + 底；重试/加载更多胶囊 default `bg-layer-2` + 0.5px 边框 / hover `bg-layer-3`（`TextPreview.module.css:219-246`）；`more:disabled` tertiary + `cursor:default`（`:243-246`）；错误行 error 色归 `.statusLine`（`:182-192`）；空态图标降饱和（`:144-148`）。 |
| 7 | 布局占位与展开形态 | 变更条是**布局行**（把正文下推，不覆盖阅读位置 —— `TextPreview.module.css:35` 注释“announced, not applied”）；加载更多在正文末尾参与布局；空态整页替换。均不属于 G-03 展开体。 |
| 8 | 滚动语义 | 变更条固定在滚动容器外；加载更多在滚动内容末尾；body 是滚动容器；代码/PDF 渲染器会把滚动交给内层（`:74-82`）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 元数据失败（文件被删/工作区未知）优先于“已变更”（`TextPreview.tsx:234-251`）；无内容时不重复显示失败条（`:236-240` 注释）；`eof` 后不显示加载更多；超长错误文案换行（`white-space:normal`）；多语言全部走字典；大字号 body 用 `--dsh-content-font-size-secondary`。 |
| 11 | 无障碍 | 变更条 `<p>` 无 live region（**未见 `role=status`**，读屏不会主动播报「文件已变更」→ §4 Q-WS-18）；失败行同样是普通 `<p>` + `data-*` 标记；重试/加载更多是原生 button（文字即名）；LoadingIndicator 自带 aria-label（`label=t('loading')`，`TextPreview.tsx:219-224,344`）。 |
| 12 | 依据 | `E0` `ui-sidebar-documentpreview/src/client/TextPreview.tsx:119-160,176-178,217-401`；`E0` `TextPreview.module.css:35-246`。 |
| 13 | 反证 + 验收 | **反证**：若检测到文件变更就自动替换正文，用户正在读的那一段会被新内容顶走、滚动位置也失效（DSH 选择“只提示”，`:35` 注释）。若 `eof` 之后仍显示「加载更多」，用户会反复点一个永远没有结果的按钮。若「无内容失败」也给“加载更多”，点它只会再失败一次。**验收**：`RSB-14` 外部改动文件，正文不动、变更条出现，点「立即重载」后内容更新（真机）；`RSB-14b` 读到文件末尾后「加载更多」消失（DOM 断言）。 |

#### `base-sidebar-right.doc.zoom-controls`（缩放工具条：档位菜单 / 加减）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.doc.zoom-controls`。父：`ZoomViewport`（`zoom/ZoomViewport.tsx:208-210`）。出现条件：该渲染器提供 `intrinsicWidth`（图片/PDF）（`ZoomControls.tsx:24-32`；`ImageBody.tsx:114-115`；`pdf.tsx:99-102`）；工具条在指针靠近底部揭示带或控件被 hover/focus 时可见（`ZoomViewport.tsx:69-100`），触屏常显（`ZoomControls.module.css:37-43`）。每个可缩放页 1 个。 |
| 2 | 网格与定位 | `position:absolute`、`inset-inline-start:50%`、`bottom:14px`、`translate(-50%,…)`、`gap:2px`、`padding:4px 6px`、圆角 lg、带阴影、`z-index:2`（`ZoomControls.module.css:1-23`）；内部：减 28px 键 + 百分比键（min-width 58px）+ 加 28px 键（`:32-35`）。 |
| 3 | 命中区 | 每键 28×28（**<44dp**）；键间距 2px（低于 Q-03 建议隔离）。 |
| 4 | 手势 | 单击减/百分比/加；`onMouseDown preventDefault` 防止按下时抢焦点/启动文本选择（`ZoomControls.tsx:52,61,72`）；ctrl+滚轮缩放是**视口节点**的手势（`.doc.zoom-viewport`）。 |
| 5 | 行为 | 减/加：`steppedZoom(displayZoom,±1)` = 「向上/向下取整到档位再 ±1 档」；步长 `ZOOM_STEP=0.25`，范围 `MIN_FIXED_ZOOM=0.25`–`MAX_FIXED_ZOOM=4`；菜单固定档位 `[0.25,0.5,1,1.5,2]`（`ZoomControls.tsx:8-11`；`zoom/types.ts`）；百分比菜单与档位列表 `selectedId` 反映当前（`:39-45,65-69`）；到边界时对应键 disabled（`:52,72`）。幂等。 |
| 6 | 视觉状态 | default `bg-layer-1` + 边框 + 阴影 / hover 键继承 Button hover；隐藏态 `opacity:0; pointer-events:none`；显示态 `opacity:1`（`ZoomControls.module.css:18-30`）；disabled 到 MIN/MAX；`aria-expanded` 表菜单。 |
| 7 | 布局占位与展开形态 | 浮层（G-01 不占位）；`aria-hidden` 加在揭示带上（`ZoomViewport.tsx:207`），工具条本身不被 aria 隐藏（可见时可达）。 |
| 8 | 滚动语义 | 不滚；缩放后 scrollport 的滚动位置按锚点重算（`ZoomViewport.tsx:109-135`）。 |
| 9 | 动效 | 显隐 140-160ms opacity+transform（`ZoomControls.module.css:18-30`）；reduced-motion 压到 0.01ms（`:45-48`）。可中断（反复移动指针直接反向）。 |
| 10 | 边界态 | 到 MIN（0.25）减键 disabled、到 MAX（4）加键 disabled；非档位值（pinch 中间态）在菜单里无选中项（`selectedId=undefined`，`ZoomControls.tsx:44-45`）；触屏常显；多语言标签/百分比格式；大字号不影响（12px 固定）。 |
| 11 | 无障碍 | `role=toolbar` + `aria-label=labels.controls`（`ZoomControls.tsx:46-47`）；三键都有 `aria-label`；百分比键 `aria-expanded`；Tooltip 描述；焦点进入工具栏即保持可见（`ZoomControls.tsx:38,49`）。 |
| 12 | 依据 | `E0` `ui-sidebar-documentpreview/src/client/zoom/ZoomControls.tsx:8-78`；`E0` `ZoomControls.module.css:1-48`；`E0` `ZoomViewport.tsx:134-151,208-210`。 |
| 13 | 反证 + 验收 | **反证**：若缩放控件常驻显示，文档阅读时中间偏下永远漂一条工具条，遮住 PDF 页码/图片底部（DSH 用揭示带 + 420ms 收起，`ZoomViewport.tsx:77-84`）。若只给「+/-」不给档位菜单，用户从 25% 回到 100% 要点 3 次（步长 0.25）。若按下键时抢焦点，文档的文本选择会被清掉。**验收**：`RSB-15` 指针移到底部 72px 内出现工具条，移开 500ms 后消失（真机录屏）；`RSB-15b` 从 25% 用菜单一步到 100%（真机）；`RSB-15c` 在 PDF 文本层已选文字时点缩放，选择不丢（真机）。 |

#### `base-sidebar-right.doc.zoom-viewport`（缩放视口：ctrl+滚轮以指针为锚 / 揭示带）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.doc.zoom-viewport`。父：图片/PDF 渲染器（`ImageBody.tsx:114`；`pdf.tsx:99`）。出现条件：可缩放渲染器渲染时；每个这样的页 1 个。 |
| 2 | 网格与定位 | `.frame` 相对定位、`height:100%`、裁剪；`.scrollport` 100% 高 + `overflow:auto`（`ZoomViewport.module.css:1-11`）；内容表面按模式定宽：fit-width = `min(intrinsic,100%)`，fixed = `intrinsic × zoom`（`:24-30`）；底部 72px 揭示带 `pointer-events:none`（`:17-22`；`ZoomViewport.tsx:10,207`）。 |
| 3 | 命中区 | 视口整面接收指针移动（揭示判断用 `clientY` 是否落在底 72px 内，`ZoomViewport.tsx:90-96`）；实际可点内容（图片/PDF canvas）不受影响。 |
| 4 | 手势 | 指针移动/离开驱动控件显隐（`ZoomViewport.tsx:205`）；**ctrl+wheel = 以指针为锚连续缩放**，`preventDefault` 拦下浏览器页面缩放，`delta` 夹在 ±40，系数 `exp(-delta*0.01)`（`:178-192`）；120ms 无事件后落为固定档（`:8,186-191`）。触摸双指缩放：**未见实现**（依赖 ctrl+wheel，触屏无 ctrlKey → §4 Q-WS-19）。 |
| 5 | 行为 | 缩放时不改内容布局，只改 `--document-zoom` 与 scrollport 的 `scrollLeft/scrollTop`，让指针下的点保持不动（优先用 `elementFromPoint` 找表面做锚，找不到退化为视口中心，`ZoomViewport.tsx:109-135`）；fit-width 由容器宽/固有宽算出（`:31-34,141-151`）；缩放偏好写回 tab store（`onPreference` → `actions.zoom`，`ImageBody.tsx:64-66`；`pdf.tsx:62-64`）。幂等。 |
| 6 | 视觉状态 | 无自身视觉；控件显隐见 `.doc.zoom-controls`；`data-document-zoom-mode` 反映 fit-width/fixed（`ZoomViewport.tsx:165-167`）。 |
| 7 | 布局占位与展开形态 | 占满 tab body；不是展开体。 |
| 8 | 滚动语义 | `.scrollport` 双轴滚动；缩放时保持锚点（`:126-133`）；到边行为未声明 `overscroll-behavior`（§4 Q-WS-16 同类）。 |
| 9 | 动效 | 缩放本身即时（无过渡，避免漂移）；控件显隐过渡见 zoom-controls；程序缩放走 `style` 直写（`:121-124`）。 |
| 10 | 边界态 | 固有宽未知（图片未 load、PDF 首页未渲染）→ zoom 视为 1（`:31-34`）；`intrinsicWidth` 未定义 → 不渲染控件（`:208`）；缩放范围 0.25–4 夹紧（`:112`）；旋转/打印布局未处理（**未见实现**）；多语言只影响控件。 |
| 11 | 无障碍 | 视口本身无 role；缩放只能靠工具条按钮（键盘可达）；ctrl+wheel 是增强通道，无 aria 暴露（无 `aria-keyshortcuts` 给缩放）；揭示带 `aria-hidden`（`:207`）。 |
| 12 | 依据 | `E0` `ui-sidebar-documentpreview/src/client/zoom/ZoomViewport.tsx:8-215`；`E0` `ZoomViewport.module.css:1-30`；`E0` `ImageBody.tsx:96-134`；`E0` `pdf.tsx:92-103`。 |
| 13 | 反证 + 验收 | **反证**：若 ctrl+wheel 不 `preventDefault`，浏览器会把整个应用页面缩放，右栏文档放大但主界面也一起虚掉；若不按指针锚定而按中心缩放，用户正在看的那一行会跑出视口（`:109-135` 就是为这条）；若 120ms 静置后不落固定档，下次进入会以 fit-width 覆盖用户的缩放手感。**验收**：`RSB-16` 在 PDF 某段文字上 ctrl+滚轮，该点保持在指针下（录屏逐帧）；`RSB-16b` 滚轮缩放不改变主应用页面缩放（DevTools 断言 `devicePixelRatio`/页面 zoom 不变）。 |

#### `base-sidebar-right.doc.font-notice`（Office 缺字体警告与详情面板）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.doc.font-notice`。父：Office 渲染器工具条区（`office/FontNotice.tsx`，由 office 渲染器在字体缺失时渲染）。出现条件：`fonts.length>0`（`:20-22,37`）；`open = fonts.length>0 && expanded`。每个 Office 预览 0..1 个。 |
| 2 | 网格与定位 | 警告按钮 28×28（`common.tool`）+ warning 色（`css.warning`）；详情面板用 `useAnchoredPosition({gap:8,margin:12})` 定位在锚点旁，portal 到 body（`:26,49-51`）；面板内：标题 + 关闭键 + 说明 + 计数 + 字体列表（`overflow:auto`，`FontNotice.module.css:23`）。 |
| 3 | 命中区 | 警告键 28×28（**<44dp**）；面板关闭键（ui-primitives Button sm）；面板本体可接收焦点（`tabIndex=-1`）。 |
| 4 | 手势 | 单击警告键开合（`:44`）；单击面板关闭键（`:59`）；Escape 关闭（面板 `onKeyDown`，`:52`）；点外部关闭（`useDismissOnOutsidePointer`，`:27`）。 |
| 5 | 行为 | 开：`setExpanded(true)` → 面板 focus（`:33-36`）；关：`closeDetails()` = 收起 + **把焦点还回警告按钮**（`:28-31`）；blur 离开面板与锚点外也收起（`:53-56`）。幂等。 |
| 6 | 视觉状态 | 警告键 warning 色 + Tooltip；打开时 `aria-expanded` + `aria-controls`；面板 `role=dialog` 非模态、有阴影/边框（`FontNotice.module.css`）；无 disabled。 |
| 7 | 布局占位与展开形态 | 按钮参与工具条布局；面板是浮层（portal），不占位（G-01）。 |
| 8 | 滚动语义 | 面板字体列表 `overflow:auto`（`FontNotice.module.css:23`）；外层文档滚动不参与。 |
| 9 | 动效 | 无过渡（定位后立即出现；`visibility:hidden` 直到量出位置，`:51`）。 |
| 10 | 边界态 | 无缺字体 → 整组不渲染；字体很多 → 列表滚动；面板靠近视口边缘 → `useAnchoredPosition` 夹边（margin 12）；多语言标题/说明/计数。 |
| 11 | 无障碍 | 警告键 `aria-expanded`、`aria-controls`（打开时）、`aria-haspopup="dialog"`（`:42-44`）；面板 `role=dialog` + `aria-labelledby`/`aria-describedby` + `tabIndex=-1`（`:49-51`）；Escape 关闭并还焦点；非模态（无焦点陷阱 —— 与 Modal 的区别，`:49`）。 |
| 12 | 依据 | `E0` `ui-sidebar-documentpreview/src/client/office/FontNotice.tsx:20-66`；`E0` `office/FontNotice.module.css`；`E0` `office/index.ts`（缺字体数据来源，非本节点）。 |
| 13 | 反证 + 验收 | **反证**：若缺字体只提示一次 toast，用户滚走后再也找不到“哪些字体缺了”；若面板做成模态，用户就不能一边看面板一边滚动文档找那段排版异常的地方（DSH 用非模态 anchored dialog）。若关闭不还焦点，键盘用户从面板关闭后焦点落到 body，Tab 从头开始。**验收**：`RSB-17` 打开缺字体面板，Escape 关闭后焦点回到警告键（真机 + DOM 断言）；`RSB-17b` 面板开着时可滚动文档（真机）。 |

#### `base-sidebar-right.doc.renderer`（文档渲染器外壳：PDF / Excel / Office / HTML / 图片 / 代码 / Markdown）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base-sidebar-right.doc.renderer`。父：`TextPreview` 的 `sidebar.right.tab.document` keyed 插槽（`TextPreview.tsx:346-352`）。出现条件：按文件路径/后缀匹配到某个 `DocumentPreviewDefinition`（`document/registry.ts`）且内容 `kind` 与渲染器期望一致（`bytes` / `text` / `renderer`）；内置渲染器：code / markdown / html / image / pdf / office / excel（`index.ts:33-40,122-129`）。每页 1 个。 |
| 2 | 网格与定位 | 各渲染器自管：PDF 一页一块纵向排（`pdf.tsx:92-98`），页面宽随缩放；Excel `fortune-sheet` 只读表格（`excel.tsx:51-66`），`showFormulaBar/Tab` 开、工具栏关；HTML iframe 铺满（`html/HtmlBody.tsx:68,102`）；图片用 zoom 表面（`ImageBody.tsx:114-133`）；代码复用 ui-primitives 的 `CodeBlock`（`code/CodeBody.tsx:19-33`）。 |
| 3 | 命中区 | 本节点自己的按钮：Excel/PDF/Office 的「重试」、Excel 公式警告键 14px；其余可交互面（PDF 文本层选择、Excel 单元格复制、iframe 内页面、CodeBlock 的复制/换行）在第三方/基座内，不属本节点。 |
| 4 | 手势 | 重试/公式警告 = 单击；PDF 页滚动（IntersectionObserver 懒渲染 + 记忆当前页，`pdf.tsx:127-150,59-61`）；Excel 只读，单元格可选中复制（`cellContextMenu=['copy']`，`excel.tsx:65`）；HTML iframe 内手势归沙箱页面。 |
| 5 | 行为 | 失败重试：Excel `attempt+1` 重解析（`excel.tsx:26-35,48`）；PDF 整体 `attempt+1` 重开文档（`pdf.tsx:67-83,89`）、单页 `attempt+1` 重渲（`:192-199`）；Office `request.reload()`（`office/OfficeBody.tsx:63-69`）。PDF 可见页写回 tab store（`actions.page`，`pdf.tsx:59-61,143`）。幂等（重试是纯重算）。 |
| 6 | 视觉状态 | 各渲染器自管：PDF 页占位 skeleton（`role=status`，`pdf.tsx:193-195`）、错误 `role=alert`；Excel 不支持特性 `role=note` 横幅（`excel.tsx:53-56`）、公式警告 hover/focus-visible 有样式（`ExcelBody.module.css:105`）；HTML 失败 `role=alert`（`html/HtmlBody.tsx:67,101`）；图片失败 `role=alert`（`ImageBody.tsx:85,90,118`）。 |
| 7 | 布局占位与展开形态 | 渲染器替换 body 内容（整块替换语义），不展开；PDF/Excel 有内滚，代码块有内滚。 |
| 8 | 滚动语义 | PDF：页面纵向堆叠在 `.body` 滚（大缩放时横向滚），IntersectionObserver rootMargin `100% 0px` 懒渲染（`pdf.tsx:137-144`）；Excel：内层表格自滚（`ExcelBody.module.css:6-67`）；HTML：iframe 内自滚；图片：`.scrollport` 双轴滚；代码：CodeBlock 滚。 |
| 9 | 动效 | 无统一动效；PDF 页渲染完成即换 canvas（`pdf.tsx:193-205`）。 |
| 10 | 边界态 | PDF：加密 → `PasswordException` 文案、worker 失败 → 专门文案、其它 → 原文报错（`pdf.tsx:209-213`）；Excel：超限/超时/编码/非法 各有文案（`excel.tsx:46-49`）；Office：转换失败显示错误 + 重试（`OfficeBody.tsx:63-69`）；HTML：非交互模式退化成静态文档、脚本不跑（`html/HtmlBody.tsx:79-86`）、`interactivePreview` 关闭时清资源（`:81-83`）；图片：不支持的扩展名直接 `role=alert`（`ImageBody.tsx:84-86`）、解码失败（`:87-90`）；代码：内容非 text 时返回 null（`code/CodeBody.tsx:15`）。多语言全部走各自字典。 |
| 11 | 无障碍 | iframe 有 `title`（`html/HtmlBody.tsx:68,102`）；PDF canvas `role=img` + `aria-label=pageImage({page})`（`pdf.tsx:203`），文本层是可选中的 DOM 文本；Excel 区 `aria-label=title`（`excel.tsx:51`）；重试/公式警告都有 `aria-label`；Office 缺字体面板见 `.doc.font-notice`。 |
| 12 | 依据 | `E0` `ui-sidebar-documentpreview/src/client/{pdf/pdf.tsx,excel/excel.tsx,office/OfficeBody.tsx,html/HtmlBody.tsx,image/ImageBody.tsx,code/CodeBody.tsx,markdown/MarkdownBody.tsx}`；`E0` `index.ts:33-40,88-130`；`E0` `document/registry.ts,contract.ts`。 |
| 13 | 反证 + 验收 | **反证**：若 PDF 一次渲染全部页，打开一个 200 页 PDF 会把主线程和内存打满（DSH 按 IntersectionObserver 懒渲染，`pdf.tsx:127-150`）；若 HTML 预览允许脚本访问父页面，一个恶意 md 里内嵌的 html 就能操纵应用（DSH 用 `sandbox="allow-scripts"` 无 same-origin，`html/HtmlBody.tsx:68`）。若 Excel 允许编辑/重算，公式会被本地重算并与 Excel 结果不一致，而预览必须忠实展示存储值（`excel.tsx:63-65`）。**验收**：`RSB-18` 打开 100 页 PDF 只渲染首屏 ±1 屏页（DOM 断言 canvas 数）；`RSB-18b` 内嵌 HTML 里 `window.parent.document` 抛异常（真机 console/单测）；`RSB-18c` 含公式的 xlsx 打开后显示公式警告、单元格不可编辑（真机）。 |

---

## 3. 排除清单（199 条，逐条理由在 `dsh-workspace.facts.csv` 的 `note` 列）

> `ui_audit.py check` 只要求「每条命中要么有节点、要么有排除理由」。下表是**规则级汇总**，
> 每条命中在 facts.csv 里都有自己的 `status=excluded` + 具体理由（不是“批量排除”一句话）。

### 3.1 `tests/`：132 条

| 包 | 条数 | 说明 / 代表文件 |
|---|---|---|
| `ui-workspace` | 112 | 主要来自 `tests/workspace-browser.client.spec.tsx`（80 条：用 `closest('[role=\"treeitem\"]')` 断言树行为）、`tests/rows.client.spec.tsx`（26）、`tests/rename-assembly.client.spec.tsx`（3）、`tests/animated-rows.client.spec.tsx`（1）、`browser-styles.client.spec.ts`（1）、`session-actions.client.spec.tsx`（1）。**测试文件不是产品 UI**；它们断言的每个行为都已在 §2.1 的 src 节点里取证 |
| `ui-sidebar-documentpreview` | 7 | `tests/basic-html.client.spec.ts`（4，含 `<a href>` 的 HTML 消毒样例）、`tests/office-font-notice.client.spec.tsx`（3） |
| `ui-sidebar-terminal` | 6 | `tests/terminal-body.client.spec.tsx`（6，含 mock 的 `role=tab` 包装与事件转发断言） |
| `ui-sidebar-right` | 5 | `tests/seat.client.spec.tsx`（4）、`tests/guide-body.client.spec.tsx`（1） |
| `ui-sidebar-files` | 2 | `tests/files-body.client.spec.tsx`（2） |

**规则**：`tests/` 下的一切命中（包括测试里 mock 的 `<button>`/`<input>`/`role=tab`）一律 `excluded`；
理由模板 =「测试用例文件，非产品 UI 源码；对应行为已在 src 取证」。若某行为**只**在测试里出现
（例如插件行注册的示例按钮），说明它没有产品实现 → 不进 spec。

### 3.2 CSS 结构性属性：57 条（`css.overflow` 32 / `css.cursor` 19 / `css.focus` 5 / `css.contain` 1）

这些是**样式属性命中**，不是交互点。它们的价值是给节点提供几何/状态证据（节点字段 2/6/7/8 已引用），
所以全部 `excluded` 并在 note 里指向承载它的节点。分类：

- **滚动容器**（`.list` / `.body` / `.scrollport` / `.screen` / `.workbook` …）：`overflow(-y):auto|hidden` → 指向 `sidebar.workspaces.list`、`base-sidebar-right.files.tree`、`base-sidebar-right.doc.text-preview.state`、`base-sidebar-right.doc.zoom-viewport`、`base-sidebar-right.terminal.status`、`base-sidebar-right.doc.renderer`。
- **命中光标**（`cursor:pointer|text`）：指向 `session-row` / `search` / `view-options` / `session-actions` / `guide.entry` / `files.tree` / `doc.text-preview.*` / `doc.zoom-controls`。
- **焦点环**（`:focus-visible`）：`ui-workspace` 3 处（`outline-offset:-2px`，因动作簇/胶囊自身裁剪）、`ui-sidebar-terminal` 1 处、`excel` 1 处 → 分别指向对应节点字段 6。
- **尺寸隔离**（`contain:strict`，`AnimatedRows.module.css:4`）：给退场覆盖层用，指向 `sidebar.workspaces.list` 字段 9。
- **PDF 文本层 `cursor:text`**（`PdfBody.module.css:94`）：可选中复制，不是点击交互 → 指向 `doc.renderer`。
- **`display:contents` 的 tab 标题**（`SidebarRight.module.css:157-160`，本包 `.tabTitle`）：无扫描命中，只作 `tab-strip` 背景。

### 3.3 非 UI 源码命中：10 条

| 位置 | 条数 | 为什么不是 UI |
|---|---|---|
| `ui-workspace/src/client/contract/slots.ts:142`（`native.button` + `aria.role`）、`:159`（`jsx.onclick`） | 3 | 契约 JSDoc 的 `@example`：演示插件怎么注册一个菜单行。注释里的代码不会运行 |
| `ui-workspace/src/client/rows/Rows.tsx:212`（`disclosure`）、`:223`（`disclosure`） | 2 | 分别是 JSDoc `@param onToggle` 说明与 Props 类型声明；真正的事件在 `:248`（已取证为 `group-row`） |
| `ui-sidebar-files/src/client/FilesBody.tsx:75`（`disclosure`） | 1 | `TreeContext` 接口的 `onToggle` 字段类型；实现在 `:185`（已取证为 `files.tree`） |
| `ui-sidebar-terminal/src/client/TerminalCleanup.tsx:27`（`jsx.onclick` + `native.button`） | 2 | **实现存在但未接线**：`ui-sidebar-terminal/src/client/index.ts:102-129` 把 `TerminalCleanup` 的 `shell.overlay` 注册整段注释掉 |
| `ui-sidebar-terminal/src/client/TerminalRecovery.tsx:28`（`jsx.onclick` + `native.button`） | 2 | 同上，`index.ts:102-129` 把 `TerminalRecovery` 的会话头注册注释掉 |

> **这条是发现，不只是排除**：「关闭失败重试」与「终端恢复失败重试」两个面在源码里写完了、
> 但 apply 中被注释（`index.ts:17-18` 连 import 都注释了）。结论：DSH 当前**产品 UI 不可达**，
> 见 §4 Q-WS-12。

### 3.4 明确**不属于**本路、因此根本没进分母的（避免“漏项”误判）

- 侧栏壳 / 列几何 / 面板行（`ui-sidebar`）→ 已在 `dsh-sidebar.md`。
- 右栏宽度拖拽、frame 列（`ui-layout`）→ §1.2 A。
- 页签条本体、浮动窗、拆分条（`ui-dockkit`）→ §1.2 B（本路只给指针 + 一个节点摘要）。
- 浏览器页（`ui-sidebar-browser`）→ 已在 `dsh-sidebar.md` §2.2。
- 第三方渲染库内部（PDF.js 的文本层菜单、fortune-sheet 的右键菜单、xterm 的选择/链接）→ 不作为本路节点；
  只把「本包接进第三方库的那一层」写成节点（`doc.renderer`）。

---

## 4. 待确认（E2 候选 —— 禁止进实现）

> 本节每条都是「源码里没有 / 有但语义要拍板」的问题。**P2 之前不得按推测实现**。
> 每条给出：现象、源码位置、为什么现在不能定、建议给用户看的选项。

| ID | 问题 | 现状证据 | 为什么进 §4（E2） | 建议选项 |
|---|---|---|---|---|
| **Q-WS-01** | 面板归位：`ui-sidebar-files` 与 `ui-workspace` 到底算 `sidebar`、`workspace` 还是 `base.sidebar-right`？ | 文件树只注册为右栏 tab（`ui-sidebar-files/src/client/index.ts:86-96`）；会话行只挂在 `sidebar.workspaces` 洞（`ui-workspace/src/client/index.ts:270-290`）；而 `10-panels.md:34` 把 `workspace` 面板的证据包写成 `ui-workspace, ui-sidebar-files` | `10-panels.md` 的表格与源码实际挂载位置不一致；两处都是注册表，冲突要人拍板 | A. 删掉 `workspace` 面板，文件树并入 `base.sidebar-right`、工作区管理并入 `sidebar`；B. 保留 `workspace` 面板但只放「工作区管理」（重命名/删除/添加），文件树仍归右栏 |
| **Q-WS-02** | `10-panels.md` 的 `sidebar` 行需要补哪些证据包？ | `sidebar` 行的证据包是 `ui-sidebar, ui-sidebar-browser, ui-conversation, ui-session`，而会话行/分组/搜索全在 `ui-workspace`（`dsh-sidebar.md` §0.3 已确认） | 注册表是所有权来源，本路不能改别人的文件，且归并顺序要 lead 统一 | 在 `sidebar` 行追加 `ui-workspace`（本路已取证） |
| **Q-WS-03** | 是否要做「今天 / 昨天 / 更早」的时间分组？ | **DSH 未见实现**：`ui-workspace/src` grep `today\|yesterday` 0 命中，`tree.ts` 只有工作区分组；myco 旧历史页有时间分组 | DSH 是唯一参考实现，但用户可能在旧 myco 里习惯时间分组；这是产品取舍不是事实 | A. 只做工作区分组（照抄 DSH）；B. 加「按时间」作为第四种分组模式（需要新契约） |
| **Q-WS-04** | 列表/树的键盘与读屏支持要做到哪一步？ | 会话行 `role=treeitem` 无 `onKeyDown`、无 roving tabindex（`Rows.tsx:586-625`；`AnimatedRows.tsx:176` 只置 armed）；文件树没有 `role=tree`（`FilesBody.tsx:80-137`）；改名/归档/置顶/拖拽全部只能指针操作 | DSH 是鼠标精度产品，明确**没有**键盘树导航；照抄则失去无障碍，补做则是新增契约（键位、焦点策略、读屏播报文案都要定） | A. 首版照抄（记录为已知缺口，移动端可接受）；B. 补 WAI-ARIA tree 模式（↑↓←→/Home/End/Enter/ContextMenu + 行内动作的键盘入口）；**推荐 B 的简化版**：上行下移 + Enter 打开 + `Shift+F10` 开行菜单 |
| **Q-WS-05** | 命中区尺寸：DSH 全线低于 44dp，myco 移动端怎么定？ | 会话行 32px（`Rows.module.css:110-113`）、行内动作 16×16（`:393-410`）、右栏 chrome 28×28（`SidebarRight.module.css:112-128`）、文件树行 ~26px、缩放键 28×28 | Q-03 已登记移动端扩边问题；这里给出的是**具体清单**，扩边会改变行高与布局（32→44 会让侧栏可见行数从 20 掉到 14） | A. 移动端行高 44dp + 行内动作改「长按菜单」；B. 保持 32dp、把行内动作移到菜单（牺牲快捷性）；C. 双端不同（Web 32 / 移动 44） |
| **Q-WS-06** | `<span hidden>` 里的「自动刷新」按钮怎么处理？ | 文件树 `FilesBody.tsx:197-204`、文档预览 `TextPreview.tsx:302-310` 都把 `aria-pressed` 的自动刷新开关包在 `<span hidden>` 中；点击逻辑完整但用户永远点不到 | 代码在、UI 不可达 —— 可能是 DSH 临时禁用的入口，也可能是「由设置页统一控制」的预留。没有 E0 能说明它的归宿 | A. 恢复成可见键（放在刷新键旁，`aria-pressed` 已就绪）；B. 删掉，自动刷新只在设置里开关；C. 保留 hidden 但补文档说明 |
| **Q-WS-07** | 行悬浮卡（800ms）在触屏/键盘上的等价物？ | `Rows.tsx:692-702,313-328` 传 `openDelayMs=800` + `copyText`；HoverCard 本体在 `ui-primitives`（本路未取证）；**没有**长按/键盘触发路径 | hover 是鼠标专属；移动端无 hover，复制标题/路径的能力会整块丢失 | A. 长按 400-500ms 出同一张卡；B. 行菜单加「复制标题/路径」项（菜单已有 4 项，可扩展）；C. 不做（移动端无此需求） |
| **Q-WS-08** | 搜索要不要关键字高亮、要不要定位到消息？ | `ui-workspace` grep `<mark` 0 命中；结果只显示标题/工作区/片段（`Rows.tsx:459-522`）；打开结果只定位到行（`Rows.tsx:580-584`），**不定位到会话内的消息** | DSH 是客户端元数据 + Host 内容搜索的合并，Host 返回的片段没有偏移量（`tree.ts:565-568` 只用 snippet），做高亮需要新的协议字段；消息级定位也需要 Host 给 event id | A. 照抄（无高亮/无消息定位）；B. 客户端对标题/工作区名做本地高亮（片段不高亮）；C. 与 Host 协议一起做消息级定位 |
| **Q-WS-09** | 右栏是「网格列」还是「myco 抽屉」？宽度竞争怎么算？ | 右栏占 frame 轨道（`ui-layout/src/client/AppFrame.tsx:265-266`），推挤而非覆盖；`track`/`fullscreen` 两种呈现（`SidebarRight.tsx:59-67,367`）；窄屏 <768 自动 fullscreen（`:356-367`）；侧栏窄屏是“挤”不是“盖”（`dsh-sidebar.md` §0.2③） | 这是 `base.layout` 的抽屉契约与 DSH 双栏的**正面冲突**：抽屉不占位（G-01），DSH 右栏占位。谁覆盖谁必须用户拍板 | A. 大屏照 DSH（推挤列 + 可拖宽）；B. 全尺寸抽屉（覆盖、无推挤）；C. 大屏推挤 / 窄屏抽屉（混合） |
| **Q-WS-10** | 拖拽排序的触屏等价？ | 排序是 HTML5 `draggable`（`Rows.tsx:601-624`；`WorkspaceBrowser.tsx:456-494`），**无 touch/pointer 长按拖动**；`ui-workspace/src` grep `onTouchStart` 0 命中 | 触屏浏览器不支持 HTML5 拖放；照抄 = 移动端不能排序 | A. 移动端不做排序（排序进「排列」模式/菜单）；B. 长按进入排序模式（drag handle）；C. 长按 + 拖动（最接近原生） |
| **Q-WS-11** | 归档行的可操作面到底有哪些？ | 归档行：不可打开（toast）、不可拖、状态槽留白、**无置顶项**（`PinSession.tsx:34`）、archive 项变恢复（`ArchiveSession.tsx:31-48`）、**但 fork 项未拦**（`ForkSession.tsx` 无 archived 判断） | 「归档行点 fork」会发生什么取决于 Host（可能派生成功、可能拒绝），源码没有 E0 能证明预期行为 | A. 归档行禁止 fork（UI 层拦）；B. 允许 fork（Host 行为即真相）；C. 归档行整行禁用除「恢复」外全部动作 |
| **Q-WS-12** | 终端的恢复/清理两个面要不要做？ | `TerminalRecovery.tsx` 与 `TerminalCleanup.tsx` 实现完整但 `index.ts:17-18` import 注释、`:102-129` 注册注释 | 无法判断是「临时下线」还是「产品不要」；照抄会做出两个 owner 不显示的组件 | A. 不做（跟随 DSH 现状）；B. 做（恢复 = 打开已保留终端 tab，清理 = 关失败重试），需确认关闭失败的实际发生路径 |
| **Q-WS-13** | 列表用「5 + 每次 5」配额，还是 myco 的虚拟滚动？ | `COLLAPSED_SESSION_LIMIT=5`（`WorkspaceBrowser.tsx:55`），无虚拟化（grep 0 命中）；行 FLIP 只给 200ms 位移 | DSH 用配额是“没有虚拟化”的折中；myco 若已有虚拟列表，两套语义会打架（组内配额 vs 全局窗口化） | A. 照抄配额（简单、可预期）；B. 虚拟滚动 + 保留组折叠；C. 配额 + 「全部展开」偏好 |
| **Q-WS-14** | 右栏首版要哪几种 tab 类型？ | 已注册：`guide`（`ui-sidebar-right`）、`files`、`text`（含 7 渲染器）、`terminal`、`browser`（`ui-sidebar-browser`） | 每种类型都是一整套面板（files 有树、text 有分页/缩放/渲染器体系、terminal 有连接状态机），首版全做代价极大 | A. P2 只做 files + text；B. 加 terminal；C. 加 browser（最重） |
| **Q-WS-15** | 删工作区后它的会话/置顶/归档怎么处理？ | `deleteWorkspace` 只调用 Host（`index.ts:251`）；`retainAccountKeys` 会清掉该工作区的排序/展开持久化（`WorkspaceBrowser.tsx:965-972`；`stores.ts:100-109`）；会话是否被删 **本包不可见** | Host 语义不在客户端源码里；这决定用户是否会在「删分组」时丢掉整个会话账 | A. 照 Host（需确认 Host 文档/实测）；B. 客户端先提示「该工作区有 N 个会话」（需要 Host 暴露计数） |
| **Q-WS-16** | G-04：列表/文件树/缩放视口到边后的残余滚动怎么处理？ | `ui-workspace` 与 `ui-sidebar-files` 均无 `overscroll-behavior`（grep 0）；缩放视口滚动也不拦（`ZoomViewport.module.css:8-11`） | G-04 是铁律（user 拍板），但 DSH 现状是浏览器默认（会链式滚到外层）。实现 G-04 需要 Web 层统一 `overscroll-behavior: contain` + 触屏惯性吸收策略 | A. 全局统一 `overscroll-behavior: contain`（Web 一行，但会改变 DSH 体验）；B. 移动端 Compose 自定义吸收、Web 保持默认；C. 只在本列内 scoped 处理（需评估穿透） |
| **Q-WS-17** | 文件树刷新后是否保留已展开层级与滚动位置？ | 刷新 = `refresh(tab.id)`（`FilesBody.tsx:190-192`），store 的 refresh 语义本路未展开（`store.ts` 只看到 `toggle/scrolled/setAutoRefresh` 在 props）；滚动位置会恢复（`:155-167`）但展开层是否清空**未见实现/未取证** | 需要读 `store.ts` 的 refresh 实现 + 真机验证；若刷新清空展开层，用户每次刷新要重新点开 3 层目录 | A. 保留展开层（只重拉已展开目录）；B. 清空回根（DSH 可能如此）；需真机确认后定 |
| **Q-WS-18** | 文档「已变更」提示要不要动态播报？ | 变更条是普通 `<p>`，无 `role=status`/`aria-live`（`TextPreview.tsx:240-264`） | 读屏用户不会知道文件在后台被改动了（G-08 的同类问题：视觉提示需配 a11y 文案） | A. 加 `role=status`；B. 只在自动刷新开启时播报（避免频繁打扰） |
| **Q-WS-19** | 触屏双指缩放？ | `ZoomViewport` 只实现 ctrl+wheel（`ZoomViewport.tsx:178-192`），**无 touch pinch**；触屏只有控件按钮可缩放 | 触屏无 ctrlKey → 手势通道完全缺失；是否要自研 pinch（与 G-04 的触屏吸收同层） | A. 只靠 +/- 与档位菜单（简单）；B. 实现双指捏合（需要 pointer events 两层手势仲裁） |
| **Q-WS-20** | 「未分组」桶的排序与位置？ | 未分组桶恒**最后**（`tree.ts:361-374`），内部顺序 = 浏览器本地顺序（`orderedUngrouped`，`:311-326`），空则按最近（`:317-320`） | DSH 把它排在所有真实工作区之后；myco 若无「工作区」概念（旧版只有历史列表），这个桶会变成唯一分组，位置语义要重新定义 | A. 照抄（最后）；B. 置顶（最常用的历史在最前）；C. 当没有工作区时取消分桶（直接平铺） |

---

## 5. 会话行的完整交互矩阵

> 全部落在 `sidebar.workspaces.session-row` 及其子节点。**空 = 未见实现**（不是“应该有”）。
> 证据列都是 `path:line`；行内事件带 `stopPropagation` 的都在“结果”里写明。

| 区域 | 手势 | 触发 | 结果 | 证据行 |
|---|---|---|---|---|
| 整行 | 单击 | pointerup | 打开会话（归档行 → 提示不可打开、不导航） | `Rows.tsx:598`；`WorkspaceBrowser.tsx:900-908` |
| 整行 | 双击（非标题） | — | **未见实现** | `Rows.tsx:586-625` 无 `onDoubleClick` 于行根 |
| 整行 | 长按 | — | **未见实现**（无 `onPointerDown`/`onTouchStart`） | `Rows.tsx:586-625` |
| 整行 | 右键 | — | **未见实现**（无 `onContextMenu`） | 同上 |
| 整行 | 拖动（HTML5） | `dragstart` | 起拖；空白/归档行不可拖 | `Rows.tsx:573,601-608` |
| 整行 | 拖过 | `dragover` | 同组同置顶块 → 显示 before/after 插入线；否则 `dropEffect='none'` | `Rows.tsx:610-617`；`WorkspaceBrowser.tsx:444-480,536-546` |
| 整行 | 落下 | `drop` | `sessionDragOrder` → 写该账户顺序 + 切 `manual` | `Rows.tsx:618-624`；`WorkspaceBrowser.tsx:358-372,182-203` |
| 整行 | 拖出列表外松手 | document `drop` | 只 preventDefault，不提交 | `WorkspaceBrowser.tsx:88-103` |
| 整行 | 指针停留 800ms | `pointerEnter` 计时 | 悬浮卡出现（菜单打开/拖拽中不出现） | `Rows.tsx:692-702`；`:697` |
| 整行 | 指针移出 | `pointerleave` | 跑马灯复位；悬浮卡关闭（归 HoverCard） | `Rows.tsx:600,127-132` |
| 标题 | 双击 | `onDoubleClick` | 改名对话框；`stopPropagation` 不打开行；空白行无 | `Rows.tsx:636-644` |
| 标题 | hover 进入 | `pointerEnter`（行级） | 溢出 >8px 时匀速跑马灯到最右；≤8px 不动；reduced-motion 跳到最右 | `Rows.tsx:54-57,102-134` |
| 标题 | hover 离开 | `pointerleave`（行级） | 一步回到起点 | `Rows.tsx:127-132` |
| 状态槽（16px） | 任何手势 | — | 非交互：`StateDot` + 视觉隐藏状态文案；空白/归档行留白 | `Rows.tsx:400-409,631-635`；`Rows.module.css:119-127` |
| 时间格 | 任何手势 | — | 非交互；有 trailing 状态文案时改显文案并 `aria-hidden` | `Rows.tsx:649-656` |
| 置顶标记 | 任何手势 | — | 非交互 `role=img`；hover 时被置顶键取代（同一右缘格） | `Rows.tsx:411-419,657-659`；`Rows.module.css:229-238,284-289` |
| 动作条 `⋯` | 单击 | `onClick` | 开会话菜单 4 项；`stopPropagation` 不打开行 | `Rows.tsx:664-689` |
| 动作条 归档键 | 单击 | `onClick` | 归档/取消归档；成功出 6s 撤销 toast；`stopPropagation` | `Rows.tsx:664`；`ArchiveSession.tsx:56-72`；`index.ts:186-200` |
| 动作条 置顶键 | 单击 | `onClick` | 置顶/取消置顶；失败 toast；归档行不渲染；`stopPropagation` | `Rows.tsx:664`；`PinSession.tsx:53-69` |
| 菜单项 置顶/改名/派生/归档 | 单击 | `onSelect` | 对应动作；归档行：pin 不渲染、archive 变恢复 | `index.ts:295-304`；各 `*MenuItem` |
| 菜单项 | 右键/长按 | — | **未见实现** | — |
| 行内任意 | 键盘（Tab/方向键/Enter） | — | **未见实现**：行无 tabIndex、无 `onKeyDown`；行内动作键在 `display:none` 时不可聚焦 | `Rows.tsx:586-625`；`Rows.module.css:258-263` |
| 空白新会话行 | 单击 | `onClick` | 打开该空白会话；无时间、无动作条、不可拖、不可双击改名 | `Rows.tsx:563-565,639-667` |
| 归档行 | 单击 | `onClick` | `notifyArchivedNotOpenable()` toast，不导航 | `WorkspaceBrowser.tsx:902-908` |
| 归档行 | 拖拽 | — | 不可拖（`draggable=false`） | `Rows.tsx:573` |
| 归档行 | 动作条 | 单击 | 只显示「取消归档」键；置顶键不渲染 | `ArchiveSession.tsx:59-71`；`PinSession.tsx:56` |
| 行内搜索定位（reveal） | 无手势 | 搜索结果打开后 | `scrollIntoView({block:'nearest'})` + 清 reveal 标记 | `Rows.tsx:580-584`；`WorkspaceBrowser.tsx:1042-1044` |

**分组矩阵（同一层级的组织行为，补在会话行之后）**

| 区域 | 手势 | 触发 | 结果 | 证据行 |
|---|---|---|---|---|
| 组行 | 单击 | `onClick` | 展开/折叠该组；折叠时配额重置 5 | `Rows.tsx:248`；`WorkspaceBrowser.tsx:502-507` |
| 组行 | 拖动 | `dragstart/dragover/drop` | 同父兄弟间重排工作区 → `insertWorkspaceBefore` | `Rows.tsx:249-257`；`WorkspaceBrowser.tsx:373-394,456-494` |
| 组行 | hover | `pointerEnter` | 文件夹图标换 chevron；800ms 出工作区悬浮卡（无 cwd 的未分组桶无卡） | `Rows.module.css:143-146`；`Rows.tsx:313-328,311-312` |
| 组行「新建会话」 | 单击 | `onClick` | 展开组 + 在该工作区开会话；`stopPropagation` | `Rows.tsx:298-307`；`WorkspaceBrowser.tsx:508-513` |
| 组行 `⋯` | 单击 | `onClick` | 改名 / 删除工作区菜单；`stopPropagation` | `Rows.tsx:286-296`；`WorkspaceBrowser.tsx:1406-1417` |
| 组尾「展开 N 条」 | 单击 | `onClick` | 配额 +5 / `Infinity` / 收回 5 | `WorkspaceBrowser.tsx:581-602` |
| 分组模式 | 菜单选择 | `onSelect` | workspace / workspace-tree / flat + manual/updated + 归档三档 | `WorkspaceBrowser.tsx:120-147`；`stores.ts:16-30,91-98,127` |
| 搜索 | 输入/按钮/Escape/外点 | — | 见 `.search` 节点 | `WorkspaceBrowser.tsx:1216-1269,1081-1128` |

---

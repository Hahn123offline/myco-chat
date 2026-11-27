# 太墟 / TaiXu —— 原生 Compose 移动 agent 应用的架构事实库（R4）

> **对象**：`_ws/refs/taixu`（[taixu-lab/taixu](https://github.com/taixu-lab/taixu)，源码 v1.0.0，Kotlin 2.4 + Jetpack Compose + miuix + Navigation3 + Koin）。
> 稀疏检出 `app/src`、`core`、`feature`、`harness/src`、`docs`；盘面 2974 文件 / 30.9MB。
> **本文所有路径相对 `_ws/refs/taixu`**；行号以该检出为准。
> **级别**：`E0` = 源码行；`E1` = 仓库内文档/注释声称（未运行验证）；`E2` = 推测 —— **本文正文只出现 E0/E1，E2 全部收敛到 §4**。
> **分母**：`python tools/ui-audit/ui_audit.py scan --root _ws\refs\taixu` → **2154 交互点 / 2269 文件**（`jsx.onclick` 1444、`kt.widget` 342、`kt.gesture` 246、`disclosure` 74、其余 48）。
> **事实库**：`docs/spec/ref/taixu.facts.csv`（2154 行：E0 2097 / excluded 57），对账报告 `_ws/out/coverage-taixu.md`，**uncovered = 0**。
> **节点口径**：节点 ID 一律落到 myco 面板注册表（`docs/spec/10-panels.md`）的命名空间；太墟独有的功能页在 §1 标注"myco 无对应面板（注册表缺口）"。

---

## 0. 一页结论

> 每条都是"太墟的选择 + 反证原因 + 证据"。**这是给 Q-BASE-02（无限抽屉）、G-03、G-04 的输入。**

1. **面板模型 = 单栈导航 + 4 个覆盖式根页，没有底部导航、没有多标签、没有分屏页签。**
   一级入口只有 4 个（`Home`/`Workspace`/`Bots`/`Settings`），它们作为"覆盖页"压进**同一条**返回栈，与普通子页共享历史与转场；智枢（`AgentDestination`）是第 5 个、也是唯一可"退到底"的根。
   证据：`E1` `docs/ARCHITECTURE_RULES.md:51`（"主页面不显示底部导航栏……项目、系统概览与设置均进入当前导航栈，使用与其他子页面一致的覆盖转场及返回行为"）；`E0` `feature/navigation/src/main/java/top/wkbin/taixu/ui/navigation/TaiXuNavHost.kt:103`（`rememberNavBackStack(AgentDestination)` 唯一栈）、`:651-655`（单个 `NavDisplay`，`onBack = ::popBack`）；`E0` `MainPageNavigation.kt:10-22`（`openMainOverlay`：已在栈中的设置页先弹回再入栈，避免重复）。

2. **导航只有"抽屉 + 栈"两层；抽屉内容硬编码为会话列表，"抽象邻接/无限层叠"在太墟未见实现。**
   这是与 myco 无限抽屉最本质的差距：太墟的抽屉（`SessionsSideDrawer`）是**一个固定内容**的会话浏览器，不是可插拔面板容器；兄弟面板（系统概览/工坊/设置）走的是"覆盖根页"而不是抽屉层。
   证据：`E0` `feature/chat/src/main/java/top/wkbin/taixu/ui/chat/ChatNavigationDrawer.kt:97-171`（`drawerContent` 直接 = `SessionsSideDrawer`，参数是 session 列表/分区/新建回调，没有一个"面板槽"）；`E0` `TaiXuNavHost.kt:186-634`（60+ 目的地全部由 `entryProvider` 静态注册，无动态面板注册表）。

3. **抽屉三态由窗口宽度断点自动切换**：Compact = 前景页整体横移的 reveal 抽屉（宽 `min(75%, 360dp)`）；Medium = 图标 rail + 从 rail 边缘滑出的覆盖侧栏（宽 320dp，遮罩 `alpha=0.32*progress`，点遮罩关闭）；Expanded = 320dp 常驻停靠且可收起为 rail。
   证据：`E0` `feature/components/src/main/java/top/wkbin/taixu/ui/components/RuntimeRevealNavigationDrawer.kt:53-56,66-78`（前景页 `translationX` 整体横移，drawer 与前景"共享一个位置、在直边相接"，Solab 式配对滑动）、`:67`（`(maxWidth*0.75f).coerceAtMost(360.dp)`）、`:106-109`（动画前先 `keyboard?.hide()`）；`E0` `RuntimeAdaptiveNavigationScaffold.kt:54-58,96-143`（三态分支）、`:52`（`RuntimeSidebarWidth = 320.dp`）、`:164-189`（OverlaySidebar：scrim 0.32、`shadowElevation=6dp`、隐藏时保持组合）；`E0` `RuntimeWindowSize.kt:15-31`（断点 600/840dp）。

4. **抽屉手势权限绑在"栈顶是谁"上：只有聊天根页允许滑动打开抽屉，覆盖页禁用；返回聊天后恢复。**
   证据：`E0` `MainPageNavigation.kt:7`（`internal fun List<NavKey>.canSwipeOpenNavigation(): Boolean = lastOrNull() == AgentDestination`）；`E0` `TaiXuNavHost.kt:112`（`sidebarGesturesEnabled = activeStack.canSwipeOpenNavigation() && !phoneSurfaceOpen`，关闭时联动收起抽屉）；`E0` `RuntimeRevealNavigationDrawer.kt:132-138`（`anchoredDraggable(enabled = gesturesEnabled || visible || state.settledValue == Open)` —— 已开时始终允许关）。

5. **跨断点/折叠屏用 `movableContentOf` 迁移页面与面板，不重建状态；形态相关选择（抽屉停靠、伴随面板显示与比例、终端/浏览器选择）全部 `rememberSaveable` 上提。**
   证据：`E0` `RuntimeAdaptiveNavigationScaffold.kt:86-87`（`page`/`sidebar` 两个 `movableContentOf`）、`:89-94`（Expanded 下 `visible` 自动转成 docked 并 dismiss 覆盖态）；`E0` `feature/chat/src/main/java/top/wkbin/taixu/ui/chat/ChatAdaptiveLayout.kt:112-114,115-116`（页面/终端/浏览器/分身均 `movableContentOf` + `rememberSaveable` 的 `selectedTab`/`companionRatio`）；`E1` `docs/ARCHITECTURE_RULES.md:54`（"必须以 `movableContentOf` 迁移而非重建"）。

6. **返回键是一条 5 级优先链，抽屉/浮层永远先于导航栈**：全屏虚拟屏 → 虚拟屏页内（注入 BACK 给容器）→ 浏览器页内返回 → 关伴随面板 → 弹导航栈。
   证据：`E0` `ChatAdaptiveLayout.kt:184-205`（`BackHandler(enabled = browserPage || phonePage || overlayOpen)`，`when` 依次 `phoneFullscreen=false` / `injectKey(KEYCODE_BACK)` / `browserBackPressed()` / `companionShown=false`）；`E0` `RuntimeRevealNavigationDrawer.kt:131`、`RuntimeAdaptiveNavigationScaffold.kt:106`（抽屉各自 `BackHandler`）；`E0` `TaiXuNavHost.kt:653`（`NavDisplay(onBack = ::popBack)` 只在前面都没消费时执行）。

7. **聊天过程链把"思考 + 普通工具"合成一条可折叠链**：标题只数工具调用（思考不计），默认展开最近 8 步 + 最近一条待关注记录；`render_surface`（A2UI）与子代理**切断**链、作为独立卡片保持原序；思考进入链内后正文气泡不再重复渲染思考。
   证据：`E0` `feature/chat/src/main/java/top/wkbin/taixu/ui/chat/ChatTimelineProjection.kt:62-78`（`groupToolSteps`）、`:80-84`（`isChainToolStep` 排除 `SUBAGENT`/`RENDER_SURFACE`）、`:114-125`（`visibleToolChainSteps(limit=8)` + attention + focused）、`:45-56`（`splitReasoning`）；`E0` `feature/chat/src/main/java/top/wkbin/taixu/ui/chat/ToolStepsCard.kt:30-38,47-69`（`visibleCount` 初始 8、每次 +8、标题 `chat_tool_chain_title(toolCount)`、竖线 `outlineVariant` 1dp）；`E1` `docs/ARCHITECTURE_RULES.md:47`。

8. **思考行按"流式/定稿"分裂成两种形态**（这是 G-03 的第三种输入）：流式时 `heightIn(max=160.dp)` 限高内滚、跟尾、用户拖动即暂停跟随并可点「跟随」恢复；定稿后不封顶走 Markdown；展开/收起用 `AnimatedVisibility(expandVertically+shrinkVertically)` 高度动画。
   证据：`E0` `feature/chat/src/main/java/top/wkbin/taixu/ui/chat/ChatMessageBubbles.kt:524-544`（`liveScroll` + `DragInteraction.Start → liveFollowing=false`、`:541-542` 到边自动恢复并 `scrollTo(max)`）、`:565-573`（头部 `heightIn(min=28.dp)` + `clickable` 切换 + 触感 `TextHandleMove`）、`:649-653`（展开动画）、`:706-735`（live → `heightIn(max=160.dp)` 内滚；settled → `MarkdownText`）。

9. **A2UI（`render_surface`）是完整闭环**：LLM 输出 A2UI v0.9 JSON 消息数组 → 进程级 `A2uiSurfaceBus` 登记并校验 → `androidx.a2ui` 官方引擎 + **自定义 Catalog 白名单**（Basic 15 组件 + 宿主 Image/Video/AudioPlayer + Table = 19 类）在 Compose 渲染 → 用户交互/运行时错误格式化后**回注原会话**（忙时 `steer`、闲时 `send`）。
   证据：`E0` `harness/src/main/java/top/wkbin/taixu/harness/A2uiSurfaceContract.kt:14-37`（工具名/描述/参数 Schema）、`harness/.../A2uiSurfaceBus.kt:16-26,195-207,237-245`、`feature/a2uipoc/.../TaiXuA2uiRenderer.kt:57-67,104-136,144-166,189-253`、`feature/chat/.../A2uiChatBridge.kt:15-26`。**完整契约见 §5。**

10. **命中区是三套规则并存，不是一套**：① 图标钮"视觉 40dp + 20dp 图标"（明确**拒绝** Material 48dp touch-target 与 60dp FAB）；② 行内动作 28dp 行高（思考/工具行），内嵌次级图标钮实测 `18dp`/`16dp`/`12dp`；③ 对文字链/小行提供显式 `Modifier.minimumInteractiveComponentSize() = sizeIn(48.dp,48.dp)` 助手，按处选择性使用。
    证据：`E0` `feature/components/src/main/java/top/wkbin/taixu/ui/miuix/Buttons.kt:171`；`E0` `feature/chat/.../ComposerSendButton.kt:20-24`（`ComposerActionSize = 40.dp`，注释"不再叠 Material 的 48dp minimumInteractive"）；`E0` `ChatMessageBubbles.kt:609,631,644`（18/18/16dp）、`ToolStepsCard.kt:59-60`（12dp chevron）；`E1` `docs/UI_GUIDELINES.md:15`。→ **与 myco 44/48dp 底线冲突，进 §4 Q-TX-02。**

11. **内滚冲突由"结构上不产生嵌套滚动"+"显式排序"两条一起解决**：① 聊天主列是 LazyColumn，任何内嵌纵向懒列表都会拿到无限高约束并崩溃，因此 A2UI 的 `List` 被**整体覆写**（≤12 项展平成 `Column`，>12 项才 `LazyColumn(heightIn(max=320.dp))`）；② 主列关掉 `overscrollEffect`（`overscrollEffect = null`，防 EdgeEffect 残留 pending scroll 与程序化滚动冲突闪退）；③ 设置页的 `scrollEndHaptic().overScrollVertical().nestedScroll(topBarCollapse)` 明确要求 **nestedScroll 在 overscroll 内侧**，否则下拉先橡皮筋、大标题不展开。
    证据：`E0` `feature/a2uipoc/.../TaiXuNonLazyList.kt:27-51,86-122,196-197`；`E0` `feature/chat/.../ChatMessageList.kt:143-152`；`E0` `feature/components/.../RuntimeSettingsScaffold.kt:98-109`。→ **G-04 第三输入的骨架见 §6.4。**

---

## 1. 目录 → 面板归位表

> 归位目标 = myco 面板注册表（`docs/spec/10-panels.md`）。"缺口"= 太墟有、myco 注册表没有对应面板，需要主笔决定"新建面板 / 归入现有面板 / 明确不做"。

| 太墟目录 | 主要内容（行数） | myco 面板/基座 | 缺口 / 备注 |
|---|---|---|---|
| `feature/navigation/` | `TaiXuNavHost`(661)、`AppDestinations`(71)、`MainPageNavigation`(49)、插件页壳 | `base.layout` | **Q-BASE-02 主证据**；`AppDestinations` 是"页面清单即代码"的范式 |
| `feature/components/` | 72 文件：按钮/偏好行/抽屉/骨架/过程图标/Markdown（`RuntimeComponents` 635、`RuntimeGlassControls` 368） | `base.primitives`、`base.layout`、`base.theme`、`base.renderer` | 对应 myco 的 `ui-primitives`/`ui-layout` 位置；**命中区助手与 nestedScroll 排序都在这里** |
| `feature/theme/` | 13 文件：`RuntimeColorScheme`(379)、`SemanticColors`(179)、`Shapes/Typography/Motion`(96)、`RuntimeSpacing`(19) | `base.theme` | token 单一入口 `RuntimeDesignTheme`；卡片圆角封顶 16dp |
| `feature/chat/` | 194 kt：聊天页/过程链/工具卡/Diff/抽屉/输入区 | `chat`、`composer`、`sidebar`、`tool`、`attachment`、`model` | 最重的一块；`chat` 主战场 |
| `feature/a2uipoc/` | 37 文件：A2UI 目录/渲染器/归一化/媒体/骨架/预览页 | `base.renderer`（A2UI 属渲染注册） | myco 无 A2UI 概念 → 归 `base.renderer`，**接口范式见 §5/§6** |
| `feature/settings/` | 213 文件：设置壳 + 30+ 子页 + 搜索 + 工具中心 + 统计 | `settings`、`settings.general`、`settings.models`、`settings.agent-loop`、`settings.plugins`、`skill` | 太墟设置子页数量 ≈ myco 注册表全部设置子页的 5 倍 |
| `feature/workspace/` | 64 文件：项目网格/Explorer/编辑器/Diff/构建/签名/模板 | `workspace` | 对应 myco `workspace`（文件浏览）+ 需新增"构建/签名"子面板 |
| `feature/home/` | 8 文件：系统概览（体检/资源/引擎/远程服务卡） | **无对应面板** | **缺口**：建议新建 `panels/home.md`（或 `settings.general` 的只读概览页） |
| `feature/bot/` | 130 文件：Bot 列表/详情/群聊/像素办公室/群治理 | **无对应面板** | **缺口**：对应 myco `subagent` 的一部分 + 会话来源；建议先归 `sidebar` 的"会话来源"，不做像素办公室 |
| `feature/workflow/` | 46 文件：DAG 画布/运行视图/审批/计划/结果 | **无对应面板**（最近似 `plan`/`schedule`/`jobs`） | **缺口**：myco 首版不做可视化 DAG，`plan` 面板可借其"步骤状态"呈现 |
| `feature/git/` | Git 工作台：改动/提交/分支/标签/远程 | **无对应面板** | **缺口**：建议 `workspace` 子面板 `workspace.git` |
| `feature/terminal/` | 原生 PTY 终端（Termux terminal-emulator） | `base.sidebar-right`（terminal） | myco 注册表有"右栏终端"概念，但无独立 terminal 面板 |
| `feature/browser/` | 多 Tab WebView 池/调试/网络时间线 | **无对应面板** | **缺口**：myco 首版不做内嵌浏览器（建议 ⏸） |
| `feature/onboarding/` | 首次引导（欢迎/免责/沙箱/权限/模型接入） | **无对应面板** | **缺口**：建议 `settings.general` 的首次启动分支 |
| `feature/custom_iteration/` | 自定义迭代页（一句话发起 agent 任务） | 无 | 归 `chat` 的 task 入口 |
| `app/src/` | `MainActivity`、主题宿主、闪屏、前台服务、通知/深链 | `base.layout` + 系统集成 | 深链/通知入口是导航栈的第二个写入口（`GlobalNavigationBus`） |
| `harness/src/`、`core/`、`tools/`、`runtime/` | agent 循环/数据库/网络/序列化/构建脚本 | **非 UI（excluded）** | 见 §3；`harness` 内仅 5 个交互点，其中 1 个误命中 |

**面板注册表缺口汇总（交主笔）**：`home`（系统概览）、`bot`（多智能体群聊）、`workflow`（DAG）、`git`、`terminal`、`browser`、`onboarding` 共 7 个概念在 myco 注册表中无 ID；本事实库先按"最近邻面板"归位并在节点 ID 中保留太墟语义（如 `bot.screen`），P2 合并时由主笔决定新建还是 ⏸。

---

## 2. 节点事实表（13 字段）

> 格式照抄 `docs/spec/schema-node.md` §2 金标准样本。**每个节点 13 行、缺一不合格。**
> 字段 13 里的"验收 ID"用 `TX-<节点短名>-NN` 占位（P3 分配到 `CHAT-xx`/`BASE-xx` 时改号，不冲突）。
> 附：`§2.7 归位简表` 覆盖 myco 首版不做/低优先的 17 个节点（只登记身份、关键证据、反证，不展开 13 字段——因为它们**不进 myco 首版实现**，展开等于伪造精度）。

### 2.1 基座：导航与面板容器

#### `base.layout.nav-stack`（单栈导航 / 覆盖根页 / 返回语义）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.nav-stack`。所属：全局壳层（`TaiXuNavHost` 是唯一 `NavDisplay` 宿主）。出现条件：Activity 启动即存在，全应用仅 1 个；它不"出现/消失"，只承载栈变化。子项 = 60+ 个 `AppDestination`（`AppDestinations.kt:9-71`），其中 4 个是根覆盖页（`Home`/`Workspace`/`Bots`/`Settings`），其余全是二级页。 |
| 2 | 网格与定位 | 全屏 `fillMaxSize`，包在 `Surface(color=background)` 内（`TaiXuNavHost.kt:651-656`）。栈顶页占满整个内容区；上一页**仍在组合树里**（Navigation3 转场需要），用 `GuardedEntry` 包一层全屏 `Box` 拦截指针（`:161-184`）。 |
| 3 | 命中区 | 全屏，无自有命中区。被覆盖页的命中区被 `pointerInput{ awaitPointerEvent(Initial); consume() }` 整层吞掉（`TaiXuNavHost.kt:172-179`）——**不是不可见，是不可点**。 |
| 4 | 手势 | 系统返回键：`NavDisplay(onBack = ::popBack)`（`:653`），`popBack` 只在 `size>1` 时 `removeLastOrNull`（`:157-159`）。顶栏返回箭头由各页 `onBack` 传入（绝大多数 = `::popBack`）。侧滑返回：**未见实现**（无 `PredictiveBackHandler`/`OnBackInvokedCallback`）。 |
| 5 | 行为 | 入栈走三个受控入口：① `push(from, destination)` 只在 `lastOrNull()==from` 时入栈（`:151-155`，防连点重复入栈）；② `pushRaw` 直接入栈且 `lastOrNull()!=destination`（`:108-110`）；③ `openMainOverlay` 切根页（`MainPageNavigation.kt:10-22`）。出栈 `popBack` 幂等（栈底不动作）。深链/通知统一进 `GlobalNavigationBus`，在 `LaunchedEffect` 里翻译成同样的三个入口（`TaiXuNavHost.kt:126-149`）。 |
| 6 | 视觉状态 | 页面转场由 Navigation3 `NavDisplay` 默认 + `entryDecorators`（`rememberSaveableStateHolderNavEntryDecorator` + `rememberViewModelStoreNavEntryDecorator`，`:638`）保证页面状态/VM 跨推弹存活。被覆盖页无视觉变化（不可见）。**未见自定义转场曲线**（E2：可能用 Navigation3 默认）。 |
| 7 | 布局占位与展开形态 | 不存在展开形态（G-03 不适用）。栈是**独占式**：任一时刻只有栈顶页参与视觉布局，其余仅保活 —— 这就是"面板不能并存"的根因（对比 §6）。 |
| 8 | 滚动语义 | 不涉及（由各页自己的 LazyColumn 负责）；覆盖转场期间两页的滚动状态各自由 `rememberSaveableStateHolder` 保存（`:638`）。 |
| 9 | 动效 | 由 `NavDisplay` 负责；页面内部另有 `RuntimeAdaptiveMotion`（spring `NoBouncy/MediumLow`）用于抽屉与面板宽度（`RuntimeWindowSize.kt:58-66`）。**未见自定义页面转场曲线（E2）**。 |
| 10 | 边界态 | ① 栈底返回：`onBack` 回调由页面自己兜底（如 `HomeScreen` 栈底再按返回 → `navigateMain(Agent)`，`TaiXuNavHost.kt:190`）；② 进程重建：`rememberNavBackStack` + 两个 decorator 恢复栈与 VM；③ 深链进设置子页用 `openSettingsPath` 保证"设置页 → 子页"两层一定同时存在（`MainPageNavigation.kt:34-38`）；④ 同一服务器概览重复打开会被 `openRemoteHome` 弹回已有实例（`:41-49`）。 |
| 11 | 无障碍 | 被覆盖层用 `Modifier.clearAndSetSemantics{}` 或指针消费屏蔽（`TaiXuNavHost.kt:172-179` 只消费指针，**未清语义** —— 被覆盖页仍可能进入 TalkBack 焦点序，见 §4 Q-TX-11）。返回箭头 `/` 顶栏由 `RuntimeTopBar` 提供 `contentDescription`。 |
| 12 | 依据 | `E0` `feature/navigation/.../TaiXuNavHost.kt:82-117,151-184,186-634,636-658`；`E0` `MainPageNavigation.kt:7-49`；`E0` `AppDestinations.kt:1-71`；`E0` `RuntimeAdaptiveNavigationScaffold.kt:106`；`E1` `docs/ARCHITECTURE_RULES.md:51-53`。 |
| 13 | 反证 + 验收 | **反证**：若给每个根页各自一条返回栈（很多 App 的做法），则"设置→子页→工坊→通知深链"会出现 4 条栈各自为政，用户按返回时回到"上一次同一栈"而不是"上一次看得见的页面"，且深链需要跨栈寻址；太墟选单栈 + 覆盖根页，代价是**一次只能显示一个根页**，换来返回语义唯一。若把被覆盖页留在组合里却不吞指针，则转场中两页同时响应点击 = 幽灵点击。**验收**：`TX-NAVSTACK-01` 从任意二级页连按返回，逐页回退且栈底停在智枢（真机手势录屏）；`TX-NAVSTACK-02` 转场期间点击旧页面不触发任何动作（真机连点/自动化点击）；`TX-NAVSTACK-03` 通知深链进 `设置→系统→ADB 日志` 后按返回回到系统页再回到设置首页（真机）。 |

#### `base.layout.drawer`（抽屉三态：reveal / rail+覆盖 / 停靠）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.drawer`。所属：全局壳层（`RuntimeAdaptiveNavigationScaffold` 包住 `NavDisplay`，`TaiXuNavHost.kt:643-658`）。出现条件：**始终存在**，形态由 `LocalRuntimeWidthClass` 决定 —— Compact → reveal 抽屉；Medium → 图标 rail + 覆盖侧栏；Expanded → 停靠侧栏（可收起为 rail）。同屏 1 个，无并存。 |
| 2 | 网格与定位 | Compact：抽屉 `align(CenterStart)`，宽 `min(0.75*maxWidth, 360dp)`；前景页 `fillMaxSize` 覆盖其上、`translationX = offset`（`RuntimeRevealNavigationDrawer.kt:66-78,146-156`）。Medium：左侧 rail 宽 `RuntimeNavigationRailWidth = 84dp + Margin`，覆盖侧栏宽 320dp 从 rail 边缘滑入（`RuntimeAdaptiveNavigationScaffold.kt:52,110-143`）。Expanded：左侧 320dp 停靠，宽度 `lerp(railWidth, 320dp, dockProgress)`（`:114-130`）。 |
| 3 | 命中区 | 抽屉本体全高；遮罩全屏可点（Compact 无独立遮罩：前景页空白区即遮罩，`RuntimeRevealNavigationDrawer.kt:162-174`，`clickable(onClickLabel=关闭)`；Medium/Expanded 覆盖态遮罩 `alpha=0.32*progress`，`RuntimeAdaptiveNavigationScaffold.kt:168-180`）。rail 图标钮 `GlassIconButton(size=40.dp)` + `semantics{Role.Tab, selected}`（`RuntimeNavigationRail.kt:80-97`）。 |
| 4 | 手势 | Compact：`anchoredDraggable(orientation=Horizontal, flingBehavior=AnchoredDraggableDefaults.flingBehavior(spring NoBouncy/MediumLow))`，`enabled = gesturesEnabled \|\| visible \|\| settled==Open`（`RuntimeRevealNavigationDrawer.kt:132-138`）。打开手势仅在聊天根页可用（`MainPageNavigation.kt:7`）。Medium/Expanded：无滑动手势，只能点 rail/顶栏菜单开（`RuntimeAdaptiveNavigationScaffold.kt:107-109`）。 |
| 5 | 行为 | 开：`visible=true` → `state.animateTo(Open)` + `keyboard?.hide()`（`RuntimeRevealNavigationDrawer.kt:106-109`）；手势停下后与 `visible` 双向同步（`:111-124`：settle 到 Open 且 `!visible` → `onOpen()`，若手势被禁用则强制弹回 Closed）。关：手势/点遮罩/返回键（`:125-131`）。Expanded 下 `visible=true` 会被**吸收**为停靠并立即 dismiss（`RuntimeAdaptiveNavigationScaffold.kt:89-94`）。幂等，可逆。 |
| 6 | 视觉状态 | 抽屉隐藏时**保持组合**（"Keep the drawer composed so the first drag does not build the session list mid-frame"，`RuntimeRevealNavigationDrawer.kt:145`）但 `clearAndSetSemantics{}`（`:149`）；遮罩：Compact 用前景页自身（无独立 scrim），Medium 覆盖态 `drawBehind{ drawRect(onSurface 0.12*progress) }` 或 `scrim 0.32`（两处不同：`RuntimeRevealNavigationDrawer.kt:163-166` 用 `onSurface@0.12`，`RuntimeAdaptiveNavigationScaffold.kt:169-172` 用 `scrim@0.32` —— **两套遮罩值，不一致**，见 §4 Q-TX-12）。 |
| 7 | 布局占位与展开形态 | 形态 **C. sheet-overlay**（G-03 的浮层语义）：Compact/Medium 抽屉不占布局流、前景页不缩窄（前景整体平移）；Expanded 停靠态是**真占位**（Row 内 320dp 一列，内容区 `weight(1f)` 自适应缩窄），收起为 rail 时同样占位 84dp。兄弟节点位移：Compact 无（浮层），Expanded 全内容区整体让位。 |
| 8 | 滚动语义 | 抽屉内部会话列表自己滚（`SessionsSideDrawer` 内 LazyColumn）；前景页在抽屉打开时被覆盖、不再接收滚动（指针被前景页遮罩接住）。**到边不传速**：横向 anchoredDraggable 与纵向列表正交，无外层横向滚动可冲突。 |
| 9 | 动效 | spring `dampingRatio=NoBouncy, stiffness=MediumLow`（`RuntimeRevealNavigationDrawer.kt:88-93`）；停靠/覆盖进度 `animateFloatAsState` 同参（`RuntimeAdaptiveNavigationScaffold.kt:104-105`）；`RuntimeSidebarWidth`/rail 宽度用 `lerp` 随 `dockProgress` 线性插值（`:115`）。可中断（`LaunchedEffect(visible...)` 直接 `animateTo`）。 |
| 10 | 边界态 | ① 手势越界：`progress()` 用 `coerceIn(0f,1f)`，非有限值按 0（`RuntimeRevealNavigationDrawer.kt:103`）；② 键盘：开合抽屉前 `keyboard?.hide()`；③ RTL：`direction = if (Rtl) -1f else 1f`（`:69`）；④ 大字号：抽屉内列表自带 `maxLines=1 + ellipsis`（E1 `docs/ARCHITECTURE_RULES.md:58`）；⑤ 横屏缺口：rail 吸收 `systemBars ∪ displayCutout` 的起始侧（`RuntimeNavigationRail.kt:35-37`），停靠侧栏吸收 `navigationBars ∪ displayCutout` 起始侧（`RuntimeAdaptiveNavigationScaffold.kt:157-159`）。 |
| 11 | 无障碍 | 隐藏层 `clearAndSetSemantics{}`（前景/抽屉两侧各一处，`RuntimeRevealNavigationDrawer.kt:149,160`；覆盖侧栏 `:127,139,153,185`）；遮罩按钮 `onClickLabel = 关闭`；rail 项 `Role.Tab + selected + contentDescription`（`RuntimeNavigationRail.kt:84-88`）。抽屉**不提供关闭按钮**（E1 `docs/ARCHITECTURE_RULES.md:52`）。 |
| 12 | 依据 | `E0` `RuntimeRevealNavigationDrawer.kt:53-56,66-78,88-109,111-138,145-176`；`E0` `RuntimeAdaptiveNavigationScaffold.kt:52-58,86-94,104-143,146-189`；`E0` `RuntimeNavigationRail.kt:32-99`；`E0` `MainPageNavigation.kt:7`；`E0` `TaiXuNavHost.kt:112,643-658`；`E1` `docs/UI_GUIDELINES.md:44-50`、`docs/ARCHITECTURE_RULES.md:51-54`。 |
| 13 | 反证 + 验收 | **反证**：若把抽屉做成"前景页缩窄 + 并排推挤"（并列式），在手机上内容区只剩 240dp，会话标题全被 ellipsis 吃掉，用户失去"当前在哪个会话"的判断依据；太墟选前景页整体右移、抽屉在下方露出（配对滑动），前景页**始终全宽**，代价是打开时用户看不见当前页。若在 Compact 用手指开抽屉却不禁用非根页，则用户在代码编辑器里横滑想选文本会误开抽屉 → 太墟用 `canSwipeOpenNavigation()` 限定根页。**验收**：`TX-DRAWER-01` Compact 下右滑打开、左滑关闭、半开停顿不回弹（真机录屏）；`TX-DRAWER-02` 在设置/工坊页横向拖动不打开抽屉（真机）；`TX-DRAWER-03` Expanded 下点菜单键 → 侧栏停靠且覆盖态消失（真机 + 截图）；`TX-DRAWER-04` 断点切换（折叠屏展开/分屏改宽）时抽屉内容滚动位置不丢（真机）。 |

#### `base.layout.adaptive-panes`（伴随面板四模式：Pager / Overlay / Docked / None）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.adaptive-panes`。所属：聊天页内容区（`ChatAdaptiveLayout` 包住对话与伴随面板，`feature/chat/.../ChatAdaptiveLayout.kt:91-107`）。出现条件：`terminalPane`/`browserPane`/`twinPane` 至少一个非空；模式由宽度 + 可用宽度决定：`terminalPane!=null && Expanded && maxWidth>=720dp → Docked`；`terminalPane!=null && !Compact → Overlay`；`browserPane/twinPane!=null → Pager`；否则 `None`（`:137-144`）。每个面板 1 个，同一时刻最多"对话 + 1 个伴随面板"并存。 |
| 2 | 网格与定位 | Docked：`Row` 内 `chatContent(weight(1f))` + 可拖拽把手 + `companion(width = innerWidth*ratio*dockedFraction)`，`statusBarsPadding().padding(top=64.dp)`（`:287-301`）。Overlay：宽 `(maxWidth*0.55).coerceIn(360dp,480dp).coerceAtMost(maxWidth-48dp)`（`:323-330`）。Pager（Compact）：整页切换，对话与虚拟屏均 keep-alive（`:86-90,305-322`）。比例 `companionRatio` 默认 0.52，范围 0.35–0.65（`:59-61,116,291-294`）。 |
| 3 | 命中区 | 拖拽把手 `ResizeHandleWidth`（`:287`，自定义 `CompanionResizeHandle`）；全屏退出钮 `RuntimeIconButton(size=40.dp)` + `Modifier.size(40.dp)` + `contentDescription`（`:355-367`）；上部 `RuntimeFloatingToolbar` 包住（`:355`）。 |
| 4 | 手势 | Docked 把手水平拖动改比例（`onDrag delta → ratio -= delta，coerceIn(0.35,0.65)`，`:291-294`）；Pager 页签由顶栏 `ChatSurfacePill`（`RuntimeSegmentedControl`，`ChatTopBar.kt:181-195`）切换，另有 `onSelectPhone` 直接切；返回键按 §0-6 的优先链处理（`:184-205`）。 |
| 5 | 行为 | 打开/关闭：`companionShown` 单布尔 + `selectedTab`（Terminal/Browser/Twin）共同决定显示哪个面板（`:145-160`）；画中画进入强制全屏（`:163-170`）；大窗收起且任务仍在时"钉住应用内小窗"（`:171-178`）。`browserVisible` 变化会记 `lastSeenBrowserTick` 用于"有新页面"高亮（`:180-183`）。 |
| 6 | 视觉状态 | 选中态在 `ChatCompanionPane` 内部分段（`selectedTab`）；tab 高亮 `browserHighlight`（`browserActivityTick > lastSeenBrowserTick`，`:376`）；`coverChrome = browserPage \|\| phonePage \|\| overlayProgress>0.5` 时对话层 `clearAndSetSemantics{}`（`:269,282-286`）。 |
| 7 | 布局占位与展开形态 | 三种形态各归 G-03：**Overlay = C**（浮层不占位）；**Docked = 真分栏**（Row 内两列，对话被压窄）；**Pager = 页面级切换**（不是抽屉，是替换整个内容区）。兄弟位移：Docked 下对话区宽度随比例实时变化（`animateFloatAsState` + 拖拽直接改 `ratio`）；Overlay 下对话不位移。 |
| 8 | 滚动语义 | 每个面板内部自己滚（终端/浏览器/虚拟屏各自接管）；对话列仍是 LazyColumn。**Docked 双栏时两列各自独立滚动，未做"滚到边传速"**（横向无滚动）。Overlay/Pager 打开时对话层 `clearAndSetSemantics` 且被盖住，指针不再分发给对话。 |
| 9 | 动效 | `dockedFraction`/`overlayProgress` 用 `animateFloatAsState(RuntimeAdaptiveMotion.float)`（`:153-156`）；面板迁移用 `movableContentOf`（`:112-114`）保证不重建；比例拖拽是直接跟手（无动画）。 |
| 10 | 边界态 | ① 可用宽度不足：Docked 判定同时要求 `Expanded` 且 `maxWidth>=720dp`（`:139-140`），否则降级 Overlay/Pager；② 无 terminalPane：`overlayOpen` 不成立（`:179`）；③ 虚拟屏在画中画模式：强制 `selectedTab=Twin + phoneFullscreen=true`（`:163-170`）；④ 分身 SurfaceView 在聊天页 `INVISIBLE` 保活（`LocalTwinPreviewVisible`，`:65-66`）；⑤ 自动操控进行中按返回会给容器注入 BACK 而不是关面板（`:188-200`）。 |
| 11 | 无障碍 | 被覆盖内容 `clearAndSetSemantics{}`（`:282-286,373`）；全屏退出钮有 `contentDescription`（`:359`）；`RuntimeSegmentedControl` 的选中态由组件自身语义表达（未在源码内查证，E2）。 |
| 12 | 依据 | `E0` `feature/chat/.../ChatAdaptiveLayout.kt:58-63,74-90,112-116,137-160,163-205,209-250,268-303,304-346,347-368`；`E0` `ChatTopBar.kt:70-95,181-195`；`E0` `feature/chat/.../ChatCompanionPane.kt`、`ChatCompanionChrome.kt`（页签壳）；`E1` `docs/ARCHITECTURE_RULES.md:53-54`。 |
| 13 | 反证 + 验收 | **反证**：若在手机上强行分栏（对话 60% + 面板 40%），360dp 机型对话只剩 216dp，每条消息 2–3 个字就换行，等于不可读；太墟用"Pager 整页切换 + 双端 keep-alive"保证手机端任一时刻只有一栏。若 Docked 比例不可拖，宽屏用户遇到"终端只显示 5 行"就必须关面板重开；可拖比例把决定权交给用户。**验收**：`TX-PANES-01` 600–719dp 窗口下终端为覆盖态、720dp+ 为停靠态（真机/折叠屏）；`TX-PANES-02` 拖把手到 0.35/0.65 两端不越界且对话列不塌（真机）；`TX-PANES-03` 打开面板后返回键先关面板、再退栈（真机）；`TX-PANES-04` 分屏改宽时面板内容不重建（录屏看输入框文本/终端输出是否保留）。 |

### 2.2 基座：通用组件

#### `base.primitives.action`（统一按钮 / 命中区三套规则）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.action`。所属：`feature/components` 的 `Runtime*` 按钮族 + `miuix` 薄包装。出现条件：任何可点动作 —— 全仓 `kt.widget` 342 处、`jsx.onclick` 1444 处大多落在这里的实例上。同一屏可出现 1..N 个。 |
| 2 | 网格与定位 | 统一由调用点决定；族内默认：`RuntimeButton` 走 miuix `Button` 默认高度（`RuntimeSpacing.buttonMinHeight = 48.dp` 是 token，`RuntimeSpacing.kt:14`）；图标钮 `size(40.dp) + icon(size(20.dp))` 是工具栏/输入区/侧栏的默认组合（`ComposerSendButton.kt:20-24`、`ComposerQuickActions.kt:29-63`、`ChatTopBar.kt:81-84`、`RuntimeNavigationRail.kt:81-95`）；动作行 `RuntimeActionRow` 等分宽度、窄屏/大字号自动换行（`E1` `docs/ARCHITECTURE_RULES.md:42`）。 |
| 3 | 命中区 | **三套并存**：① 图标钮视觉 40dp（`ComposerActionSize = 40.dp`，注释明确"对齐 miuix IconButtonDefaults 的 40dp，不再叠 Material 的 48dp minimumInteractive，避免实心底被撑成 FAB"，`ComposerSendButton.kt:20-24`）——即**视觉 40dp = 命中 40dp**，不扩边；② 显式 48dp 助手 `fun Modifier.minimumInteractiveComponentSize() = sizeIn(minWidth = 48.dp, minHeight = 48.dp)`（`feature/components/.../miuix/Buttons.kt:171`），按处使用（全仓 38 处，如 `ToolDiffView.kt:404,423,446`、`GitChangesTab.kt:179,204,227`、`SettingsScreen` 类行）；③ 行内次级图标钮**小于 40dp**：思考块的翻译/复制 `IconButton(modifier = Modifier.size(18.dp))`（`ChatMessageBubbles.kt:609,631`）、chevron `16.dp`（`:644`）、过程链 `12.dp`（`ToolStepsCard.kt:59-60`）、工具行 state 文字不扩边（`ToolStepHeader.kt:33`）。**相邻命中区间隔**：思考块头部 `Arrangement.spacedBy(8.dp)`（`:568`），输入区动作 `spacedBy(6.dp)`（`ChatComposer.kt:336,357`）。 |
| 4 | 手势 | 单击 = `onClick`；长按仅发送钮有（`detectTapGestures(onLongPress=…)` = 发送但不等待回答，`ComposerSendButton.kt:34-38` + `ChatComposer.kt:365`）；双击/右键：未见实现。与父滚动仲裁：`clickable` 自带 touch slop，竖直拖动归列表。 |
| 5 | 行为 | 纯回调（无内部状态）；**幂等性由调用点保证**（如发送按钮 `canSend = !initializing && (input.isNotBlank() \|\| attachments.isNotEmpty())`，`ChatComposer.kt:332`；初始化期间禁发，`:113-122`）。`enabled=false` 时 miuix `Button` 吞掉点击（`Buttons.kt:144-152` 的 `MiuixIconButton(onClick, enabled=…)`）。 |
| 6 | 视觉状态 | default/pressed/disabled 由 miuix 提供（`RuntimeButtons.kt` 委托）；running 态由调用点额外染色：发送钮在 `STEER` 模式用 `semanticColors().agent`、`NEXT_RUN` 用 `secondary`（`ChatComposer.kt:340-349`）；stop 用 `errorContainer`（`ComposerSendButton.kt:56-57`）；审批模式盾牌 `FULL_ACCESS→error / ASSISTED→primary / REQUEST→onSurfaceVariant`（`ComposerQuickActions.kt:52-56`）。**无 focus 态**（触屏优先，全仓仅 1 处 `css.focus`）。 |
| 7 | 布局占位与展开形态 | 不适用（无展开）。命中区助手 `sizeIn` **参与布局测量**（会给最小 48dp 占位，可能撑高行）—— 这是它只能选择性使用的原因。 |
| 8 | 滚动语义 | 无自身滚动；在 LazyColumn 内随列表滚。**长按不拦截滚动**（`detectTapGestures` 消费后仍允许父级 drag？E0 未验证，见 §4 Q-TX-13）。 |
| 9 | 动效 | 按压反馈走 miuix `folmeSpring`（`RuntimeDesignTheme.kt:57-63`：damping 0.9 / response 0.35、0.5、0.2）；shimmer/呼吸在过程行（`RuntimeProcessPulse`）。 |
| 10 | 边界态 | 大字号：动作行自动换行（E1 `ARCHITECTURE_RULES.md:42`）、文字按钮 `maxLines=1`（E1 `:58-59`）；禁用态 miuix 自动变灰；超长标签由调用点 `maxLines=1 + ellipsis`。 |
| 11 | 无障碍 | miuix `IconButton` 包 `semantics{ role = Role.Button; if(!enabled) disabled() }`（`Buttons.kt:149-152`）；但 `RuntimeIconButton` 的 `contentDescription` 由调用点传（`ChatTopBar.kt:82`、`ComposerSendButton.kt:45`），**未传则 TalkBack 只剩图标无语义** —— 全仓抽查有遗漏（如 `ChatMessageBubbles.kt:629-638` 传了，`ToolDetailsSheet.kt:55` 传了）。 |
| 12 | 依据 | `E0` `feature/components/.../miuix/Buttons.kt:136-171`；`E0` `feature/components/.../RuntimeComponents.kt:607-633`（FilterChip 注释声称 48dp 但实现未调用 —— 见 §4 Q-TX-10）；`E0` `ComposerSendButton.kt:20-48`；`E0` `ComposerQuickActions.kt:28-64`；`E0` `ChatMessageBubbles.kt:565-647`；`E0` `RuntimeNavigationRail.kt:74-97`；`E1` `docs/UI_GUIDELINES.md:15`、`docs/ARCHITECTURE_RULES.md:39,42`。 |
| 13 | 反证 + 验收 | **反证**：若给 40dp 图标钮强行加 48dp `minimumInteractiveComponentSize`，输入框工具条会被撑高 8dp、与左侧 `ContextUsageRing` 高度不齐，且 60dp FAB 那种"实心大圆"视觉会把发送动作的重量压过内容（太墟注释里明确拒绝）。若反过来把 18dp 内嵌图标钮也当默认（不扩边），手指按不准、误触到"折叠思考"整行 —— 用户的感受是"点复制却把思考收起来了"。**验收**：`TX-ACTION-01` 输入区发送/停止/附件/模型/审批 5 个钮视觉 40dp、间距 6dp、基线对齐（截图量测）；`TX-ACTION-02` 文字链/小行（工具详情复制、Diff 文件头）命中区 ≥48dp（真机点击边缘 4 次 4 次命中）；`TX-ACTION-03` 思考块 18dp 复制钮必须与 28dp 行头的展开点击区分开（真机：点复制不折叠）。 |

#### `base.primitives.preference-row`（miuix 偏好行：开关/单选/滑杆/箭头/下拉）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.preference-row`。所属：`feature/settings` 的 `PersonalizationComponents` + `feature/components` 的 `RuntimePreferenceRows`/`RuntimeDropdownPreference`/`RuntimeTabs`。出现条件：任何"一行一个设置项"处；设置子页每屏几十行。语义化封装：`PersonalizationToggle`（开关）、`ThemeOptionRow`（单选）、`PreferenceSlider`（滑杆，带 `onFinished`）、`SettingsRow`（箭头入口，`SettingsCategoryCard` 包它）。 |
| 2 | 网格与定位 | 行由 miuix `BasicComponent`/`ArrowPreference`/`SwitchPreference`/`RadioButtonPreference`/`SliderPreference` 默认密度管理（E1 `ARCHITECTURE_RULES.md:50`）；分组由 `PreferenceSurface(first, last)` 拼成"一张卡"：首段上圆角、末段下圆角、中间直角（`PersonalizationComponents.kt:30-49`），圆角 `MiuixCardDefaults.CornerRadius`（16dp）；组内行间距由 miuix 默认，不额外加。 |
| 3 | 命中区 | 整行可点（miuix preference 默认行高，未在本仓覆写）；**行的可点判定在 miuix 内部，不可见**；`PreferenceSurface` 自身在 `onClick!=null` 时整块 `clickable`（`:46`）。 |
| 4 | 手势 | 单击：开关切换 / 单选选中 / 箭头进子页；滑杆：水平拖动改值、松手落盘（`onValueChangeFinished`）；下拉：点行弹 `WindowDropdownPreference` 单选弹窗（E1 `UI_GUIDELINES.md:67`）。无长按/双击。 |
| 5 | 行为 | **保存语义分两类**：① **即时落盘**——开关/单选/语言，点完立刻 `viewModel?.setXxx()`（`AppearanceSettingsScreen.kt:52,83-88,125`）；② **松手落盘**——滑杆拖动时只改本地 `rememberSaveable` 状态，`onFinished` 才写（`:35-38,65-68,117-120`，`onValueChange` 更新本地、`onFinished` 调 VM）。无"保存按钮"，也没有脏态提示；表单类页面（模型编辑）例外，见 `settings.models`。 |
| 6 | 视觉状态 | 选中：`ThemeOptionRow(selected=…)` → `RuntimeRadioPreference` 选中；按压：miuix 库默认（`PreferenceSurface` 用 `squircleSurface` 保证涟漪裁进圆角，E1 `UI_GUIDELINES.md:57`）；禁用：`PersonalizationToggle(enabled=false)`（`AgentRoleCards.kt` 等）。 |
| 7 | 布局占位与展开形态 | 不适用（行不展开）。分组是 **LazyColumn 逐行 item**：禁止 `item { Card { 多行 Preference } }`（E1 `UI_GUIDELINES.md:56`），因此一行 = 一个 lazy item，首/末段各自能拿到圆角。 |
| 8 | 滚动语义 | 行随设置页 LazyColumn 滚；滑杆的水平拖动与竖直滚动正交，不冲突。 |
| 9 | 动效 | 行内条件展开用 `Modifier.animateItem(...)`（E1 `UI_GUIDELINES.md:58`，且要求 placement spec 不能长期为 null）；分组卡本身不做位移动画。 |
| 10 | 边界态 | 依赖行（如"仅当开启 X 才显示 Y"）用 `buildList` 动态拼 item（E1 同上）；长标题/副标题 `maxLines=1 + ellipsis`；大字号跟随系统自然增高（`RuntimeTextStyles` 比 miuix 默认收紧一档，E1 `ARCHITECTURE_RULES.md:50`）。 |
| 11 | 无障碍 | 委托 miuix preference 自带语义（`SwitchPreference`/`RadioButtonPreference` 原生 role）；`PreferenceSlider` 额外挂 `semantics{ contentDescription = title }`（`PersonalizationComponents.kt:101-103`）。 |
| 12 | 依据 | `E0` `feature/settings/.../PersonalizationComponents.kt:18-110`；`E0` `AppearanceSettingsScreen.kt:35-128`；`E0` `feature/components/.../RuntimePreferenceRows.kt`、`RuntimeDropdownPreference.kt`、`RuntimeTabs.kt`；`E1` `docs/UI_GUIDELINES.md:54-68`、`docs/ARCHITECTURE_RULES.md:50`。 |
| 13 | 反证 + 验收 | **反证**：若滑杆每移动 1px 就写 DataStore，一次拖动会写上百次（I/O 抖动 + 主题重算导致拖动跟手变差），所以必须"拖时本地、松手落盘"；若给设置项加"保存"按钮，用户改 5 项要按 1 次确认且中途返回全丢 —— 移动端设置页的默认预期是即时生效。**验收**：`TX-PREF-01` 改主题模式/语言立即生效且返回后仍生效（真机 + 重启进程）；`TX-PREF-02` 拖动界面缩放过程中不触发持久化、松手才写入（单测/日志计数）；`TX-PREF-03` 分组卡首末圆角 16dp、中间段无圆角且涟漪不出界（截图比对）。 |

#### `base.primitives.sheet-dialog`（BottomSheet / Dialog / 下拉菜单壳）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.sheet-dialog`。所属：`feature/components/.../miuix/Dialogs.kt` 的 `AlertDialog`/`RuntimeDialog`/`ModalBottomSheet` + `feature/chat/.../ChatBottomSheet`/`PlainBackdropModalBottomSheet`。出现条件：任一"从底部上来的临时面板"或"居中确认框"；同屏最多 1 个（除叠加：子代理成果抽屉叠在分支列表之上，`ChatScreen.kt:547`）。 |
| 2 | 网格与定位 | 底部抽屉：`ModalBottomSheet` 统一垫 `systemBars ∪ displayCutout`（只 Bottom）+ `imePadding()`（`miuix/Dialogs.kt:163-189`；注释明确"只垫底、不含 ime（外层另 imePadding）、不含水平（sheet 底对齐限宽居中）"）；长内容弹窗 `heightIn(max ≈ 窗口高×0.8)` + 正文 `weight(1f, fill=false)`、按钮钉底（E1 `UI_GUIDELINES.md:65`）。`ChatBottomSheet` 是聊天侧薄包装（`feature/chat/.../ChatBottomSheet.kt:1-54`）。 |
| 3 | 命中区 | 标题栏关闭钮 `RuntimeIconButton`（40dp，`ToolDetailsSheet.kt:55-57`）；遮罩全屏可点关闭（`PlainBackdropModalBottomSheet` 例外：**无背景遮罩**，点外部不关，只能点关闭/下拉，`ChatTopBar.kt:146-151`）；底部按钮行 `RuntimeActionRow` 等分 + `dialogActionGap = 20.dp`（`RuntimeSpacing.kt:18`）。 |
| 4 | 手势 | 底部抽屉：下拉关闭（miuix `SheetState`）、`BackHandler` 关闭（`canDismiss()`，`Dialogs.kt:128-129`）；点击遮罩关闭（除 PlainBackdrop 变体）；长内容在 `text` 槽内 `verticalScroll`（`ToolDetailsSheet.kt:75-76`）。 |
| 5 | 行为 | 打开/关闭由调用点 `rememberSaveable` 布尔控制（全仓一致，如 `ChatScreen.kt:161-167` 的 8 个 `showXxx`）；关闭即丢弃（无"确定/取消"事务）；仅模型编辑等表单页有自己的保存语义。**叠层顺序**：子代理成果抽屉打开时分支列表保持在下（`ChatScreen.kt:538-559`）。 |
| 6 | 视觉状态 | 遮罩：molten 默认（`PlainBackdrop` 无遮罩但仍有独立 surface）；`shadowElevation` 见抽屉；`WindowDialog` 不限 content 高（E1 `UI_GUIDELINES.md:65`）。 |
| 7 | 布局占位与展开形态 | G-03 形态 **C. sheet-overlay**：完全不占对话布局流，打开时列表高度/滚动锚点不变（`ToolDetailsSheet.kt:30` 注释："Inspect commands in a modal, so opening output never changes the chat list's height or anchor"）。 |
| 8 | 滚动语义 | `text` 槽自身 `verticalScroll`（`ToolDetailsSheet.kt:75`）；抽屉内容用 LazyColumn 时它自己滚；**到边不传速**给下层（下层被遮罩覆盖，指针不再到达）。 |
| 9 | 动效 | miuix sheet 入场/退场；内容高度突变时要求"用与 sheet 入场同参的高度过渡（miuix `folmeSpring`），别自己凑 spring"（E1 `UI_GUIDELINES.md:68`）。 |
| 10 | 边界态 | 键盘：正文区 `imePadding()`；长内容：`heightIn(max≈0.8 屏)` + 内滚；横屏缺口：只垫 Bottom + 水平由调用方负责（E1 同上）；空内容：`ToolDetailsSheet` 显示"无输出"占位（`:85-88`）。 |
| 11 | 无障碍 | 关闭钮必须带 `contentDescription`（`ToolDetailsSheet.kt:55`）；遮罩的 `onClickLabel` 仅在 reveal/overlay 抽屉里显式给了（`RuntimeAdaptiveNavigationScaffold.kt:176`），sheet 遮罩**未查证**（E2）。 |
| 12 | 依据 | `E0` `feature/components/.../miuix/Dialogs.kt:45-60,87-128,139-189,236-245`；`E0` `feature/chat/.../ChatBottomSheet.kt`、`PlainBackdropModalBottomSheet.kt:1-37`；`E0` `ToolDetailsSheet.kt:30-108`；`E1` `docs/UI_GUIDELINES.md:63-69`。 |
| 13 | 反证 + 验收 | **反证**：若工具输出在聊天列表里"原地展开"，一次 2000 行日志会把整条会话拉长几十屏、并让跟随滚动失效（用户再也回不到"看最新"）—— 所以必须浮层；若抽屉自己再 `navigationBarsPadding()` 会与统一垫底叠加成双倍留白（E1 明令禁止）。**验收**：`TX-SHEET-01` 打开工具详情时聊天列表不位移、关闭后原锚点不跳（录屏比对首行 y）；`TX-SHEET-02` 抽屉底部与手势条间距 = 系统 inset（真机三键/手势两种导航模式）；`TX-SHEET-03` 键盘弹出时表单可滚到"保存"（真机）。 |

#### `base.theme.tokens`（设计 token：颜色 / 字阶 / 形状 / 间距 / 动效）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.theme.tokens`。所属：`feature/theme`（唯一装配入口 `TaiXuTheme` → `RuntimeDesignTheme`）。出现条件：全局，1 份；**"视觉 token 只能在 `feature/theme` 定义，其他模块只消费不定义"**（E1 `ARCHITECTURE_RULES.md:70`）。 |
| 2 | 网格与定位 | 不适用（token 本身不占位）。派生关系：`TaiXuTheme`(动态取色/HCT 兜底) → `RuntimeDesignTheme` 注入 4 个 `CompositionLocal`：`LocalRuntimeColors`/`LocalRuntimeShapes`/`LocalRuntimeTypography`/`LocalTextStyle`，并套 `MiuixTheme(colors, textStyles)`（`RuntimeDesignTheme.kt:77-96`）。 |
| 3 | 命中区 | 不适用。 |
| 4 | 手势 | 不适用。 |
| 5 | 行为 | 读取方式唯一：`RuntimeTheme.colorScheme.*` / `RuntimeTheme.shapes.*` / `RuntimeTheme.typography.*` / `RuntimeSpacing.*` / `RuntimeMotion.*`；语义色另一入口 `semanticColors()`（`buildSemanticColors` 向主色 `Blend.harmonize`，色相旋转 ≤15°，E1 `ARCHITECTURE_RULES.md:68`）。 |
| 6 | 视觉状态 | 形状 token 实测：`hairline 2 / tiny 4 / extraSmall 6 / chip 8 / small 12 / medium 16 / large 16 / extraLarge 16 / largeIncreased 16 / extraLargeIncreased 20 / extraExtraLarge 28` dp —— **卡片/菜单/输入统一 16dp**，20/28 只用于非卡片造型（`RuntimeDesignTheme.kt:17-31`；`RuntimeSpacing` 另有 12dp 页边距）；字阶：M3 角色名映射到 miuix `defaultTextStyles`（`bodyLarge=main`、`bodyMedium=body1`、`bodySmall=body2`、`labelLarge/Medium/Small=body2/footnote1/footnote2 + Medium`，`:33-55`），**主题层禁止全局加粗**（E1 `ARCHITECTURE_RULES.md:67`）；聊天阅读面另有更松字阶 15sp/23sp（`ChatReadingTypography.kt:26-64`）。 |
| 7 | 布局占位与展开形态 | 不适用。 |
| 8 | 滚动语义 | 不适用。 |
| 9 | 动效 | `RuntimeMotion`：`folmeSpring(damping=0.9, response=0.35)` 默认空间、0.5 慢、0.2 快；效果类 `damping=1, response=0.25/0.18`（`RuntimeDesignTheme.kt:57-63`）。自适应面板另有 `RuntimeAdaptiveMotion = spring(NoBouncy, MediumLow)`（`RuntimeWindowSize.kt:58-66`）。 |
| 10 | 边界态 | ① 动态取色：API31+ 系统 Monet 优先，否则壁纸种子 HCT `SchemeTonalSpot` 兜底（E1 `ARCHITECTURE_RULES.md:64`；`Theme.kt:49-61`）；② 深浅色：`isSystemInDarkTheme()` + 手动 palette；③ 大字号：字阶跟随系统、行高自然增高（E1 `ARCHITECTURE_RULES.md:50`）；④ 色板预览等"展示色板本身"的例外允许硬编码色值但必须集中（E1 `UI_GUIDELINES.md:74`）。 |
| 11 | 无障碍 | 语义色需过对比度（有单测 `SemanticCategoricalContrastTest.kt`）；`statusBars/navigationBars` 图标明暗随主题（`Theme.kt:41-45`）；系统导航栏对比度强制关闭（`:41`）。 |
| 12 | 依据 | `E0` `feature/theme/.../RuntimeDesignTheme.kt:17-96`；`E0` `feature/theme/.../RuntimeSpacing.kt:6-19`；`E0` `feature/theme/.../ChatReadingTypography.kt:11-64`；`E0` `feature/theme/.../Theme.kt:23-72`；`E1` `docs/ARCHITECTURE_RULES.md:62-70`、`docs/UI_GUIDELINES.md:72-75`。 |
| 13 | 反证 + 验收 | **反证**：若允许页面里散落 `Color(0xFF…)` 或 `RoundedCornerShape(13.dp)`，三套主题（M3/动态取色/液态玻璃）下总有一处对不上，且"卡片最大 16dp"的视觉秩序会崩（太墟用 `atMostCardRadius` 强约束）；若在主题层统一加粗 label，设置页会变成"整页粗体"，层级反而消失。**验收**：`TX-THEME-01` 全文搜索无页面级硬编码色值/圆角（lint/脚本门禁）；`TX-THEME-02` 换主题（浅/深/动态取色）后无任何控件失配（截图三连）；`TX-THEME-03` 字号 +30% 后设置行不截断、卡片不重叠（真机）。 |

#### `base.renderer.a2ui`（A2UI 渲染管线：目录 / 引擎 / 事件）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.renderer.a2ui`。所属：`harness`（总线 + 工具契约）+ `feature/a2uipoc`（渲染）。出现条件：① Agent 调 `render_surface` 工具；② 用户进"设置→系统开发→A2UI"预览页。每个 surface 是一张独立界面卡；一次会话可累积 80+ 张（`TaiXuA2uiRenderer.kt:87-88` 注释）。**完整契约见 §5**。 |
| 2 | 网格与定位 | 渲染位置 = 聊天流内 `ToolCard` 的下方：`if (call.tool == RENDER_SURFACE && result?.success == true) A2uiPocSurfaceCard(...)`（`ChatToolCards.kt:133-136`），`fillMaxWidth`；根骨架高 `TaiXuA2uiRootSkeletonHeight = 64.dp`（`TaiXuA2uiSkeleton.kt:280`）。 |
| 3 | 命中区 | 由 Catalog 组件自身决定（Button/CheckBox/ChoicePicker/Slider/TextField/DateTimeInput 的可点区由 miuix 渲染，见 `MiuixA2uiBasicCatalogV1*.kt`）；`A2uiAccessibility.kt:26-43` 把协议 `accessibility` 属性映射成 `contentDescription` + `onClick(label)`。 |
| 4 | 手势 | 组件内建（点击 = `dispatchAction(action)` → 出站事件；输入类组件改数据模型）。A2UI 卡整体**不可展开/不可长按**（无宿主手势）。 |
| 5 | 行为 | 三步：`processMessages(messagesJson, replayKey)` 逐条 `parser.parse` → `processor.processMessage`（`TaiXuA2uiRenderer.kt:104-136`）；`SurfaceView` 订阅单个 surface 渲染（`:264-287`）；用户事件经 `processor.outboundEvents` → `A2uiSurfaceBus.publishUserEvent` → `A2uiChatBridge` 回注会话（`:189-253`、`A2uiChatBridge.kt:18-25`）。同一载荷只投喂一次（SHA-256 指纹 LRU 1024）。 |
| 6 | 视觉状态 | 三态：Loading → 宿主骨架屏（非官方转圈，`A2uiSurface.kt:58,108-118` + `TaiXuA2uiRenderer.kt:274-284`）；Error → `errorContainer` 卡 + 原文 JSON 兜底（`A2uiPocScreen.kt:134-160`）；Success → `A2uiComponent`，容器/子项间 `AnimatedContent` 淡入 + `SizeTransform(clip=false)`（`A2uiSurface.kt:130-151`）。 |
| 7 | 布局占位与展开形态 | 卡片**参与布局流**（是聊天项的一部分），高度随内容；容器尺寸变化播 `SizeTransform` 动画。无折叠形态。 |
| 8 | 滚动语义 | **内嵌滚动是最大风险点**：A2UI `List` 覆写保证纵向 ≤12 项展平、>12 项 `LazyColumn(heightIn(max=320.dp))`，否则在聊天 LazyColumn 里拿无限高约束直接崩（`TaiXuNonLazyList.kt:27-51,86-122,196-197`）。Table 超宽用 `horizontalScroll`（`TaiXuA2uiCatalog.kt:117`）。 |
| 9 | 动效 | `transitionSpec` = fadeIn(defaultEffects 0.25) + fadeOut(fastEffects 0.18) + SizeTransform(defaultSpatial 0.35)（`A2uiSurface.kt:130-151`）；骨架 shimmer 1.2s 扫光 + 2.4s 呼吸、子项错峰 0.15s（`TaiXuA2uiSkeleton.kt:271-278`）。 |
| 10 | 边界态 | ① 载荷畸形 → `publishFromTool` 返回失败文案让模型自纠（`A2uiSurfaceBus.kt:195-198`）；② 组件不在 Catalog → 深度校验在**工具执行期**就拒绝（`TaiXuA2uiRenderer.kt:144-166`）；③ 重复 createSurface → 吞掉 `already exists`（`:127,221-223`）；④ 运行时错误按 15s 冷却回传（`:227-248`）；⑤ 会话删除 → `releaseSession` 拆 surface（`A2uiSurfaceBus.kt:115-129`）；⑥ 预览列表只留 8 条但路由表留 1024 条（`:338-341`）；⑦ 加载宽限期后仍无 surface → 回退原文（`A2uiPocScreen.kt:94-129`）。 |
| 11 | 无障碍 | `a2uiAccessibility` 映射 label/description；Button 校验失败时 `semantics{ error(message) }`（`MiuixA2uiBasicCatalogV1Button.kt:87-91`）；`List` 的 align/label 映射（`TaiXuNonLazyList.kt:166-194`）。**运行时错误不播报**（未见 `liveRegion`）。 |
| 12 | 依据 | `E0` `harness/.../A2uiSurfaceBus.kt` 全文件；`E0` `harness/.../A2uiSurfaceContract.kt:12-47`；`E0` `feature/a2uipoc/.../TaiXuA2uiRenderer.kt`、`TaiXuA2uiCatalog.kt`、`TaiXuA2uiInputNormalizer.kt`、`TaiXuNonLazyList.kt`、`miuix/A2uiSurface.kt`、`miuix/A2uiAccessibility.kt`；`E1` `README.md:30`（"Agent 可通过 `render_surface` 输出 A2UI 声明式界面"）。 |
| 13 | 反证 + 验收 | **反证**：若 Agent 能渲染任意组件类型（无 Catalog），等于把"任意 UI + 任意行为"下放给模型 —— 一次幻觉就能造出假"确认删除"按钮骗用户点；太墟用 Catalog 白名单（组件必须命中目录，否则工具期就报错）+ URL 白名单（只 http/https）+ 值型组件必须走数据绑定，三处共同构成安全边界。若不覆写 List，聊天里滑到那张卡就崩进程（官方实现用 LazyColumn 拿无限高）。**验收**：`TX-A2UI-01` 杜撰组件名 → 工具返回失败原因且聊天流无卡片（单测 `A2uiSurfaceBusTest` + 真机）；`TX-A2UI-02` 垂直 20 项 List 卡片内滚、聊天不崩（真机）；`TX-A2UI-03` 表单填值 + 点提交 → 会话收到带 dataModel 的 `[A2UI 界面事件]`（真机 + 日志）；`TX-A2UI-04` 删会话后该 surface 不残留（单测 `releaseSession`）。 |

### 2.3 聊天面板

#### `chat.topbar`（悬浮圆形菜单 + 右侧胶囊操作区）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.topbar`。所属：聊天面板顶部（`ChatTopBar`）。出现条件：聊天根页始终显示；**宽度类为 Compact/Medium 时左侧有抽屉菜单钮，Expanded（持久导航）时没有**；虚拟屏全屏态整体隐藏（`ChatTopBar.kt:70-86`、`ChatAdaptiveLayout.kt:347-378`）。同屏 1 个。 |
| 2 | 网格与定位 | 悬浮在内容之上，**不占整行占位**：`Row(fillMaxWidth).statusBarsPadding().padding(horizontal=12dp, vertical=8dp)`，`SpaceBetween`（`ChatTopBar.kt:71-75`）。左 = `RuntimeFloatingToolbar{IconButton(40dp)}`；中 = 虚拟屏/对话分段 pill（`widthIn(max = 136dp*fontScale)`，`:181-195`）；右 = `RuntimeFloatingToolbar{ 固定入口 + "更多" }`。内容列表通过 `contentPadding.top = statusBars + 64.dp` 让消息**滚到顶栏下方**（`ChatMessageList.kt:148-151`），并用顶部渐隐遮罩（`ChatPaneContent.kt:169-177`）。 |
| 3 | 命中区 | 全部 `RuntimeIconButton(size(40.dp)) + icon(size(22.dp))`（`:81-84,103-113,121-124,134-143`）；分段 pill 由 `RuntimeSegmentedControl` 提供。相邻按钮在 `Row` 内无额外 spacing（浮动工具条内部自带）。 |
| 4 | 手势 | 单击 = 打开抽屉 / 打开"更多"sheet / 切虚拟屏 / 全屏。无长按、无双击。与列表滚动：顶栏是覆盖层，列表在其下方滚，不存在手势争用；`CoverChrome` 时顶栏被隐藏。 |
| 5 | 行为 | 菜单键 → `showNavigation=true`（`TaiXuNavHost.kt:209`）；"更多" → 本地 `expanded=true` → `PlainBackdropModalBottomSheet`（`ChatTopBar.kt:121-178`）；分段 → `onSelectPhone(bool)`；全屏 → `phoneFullscreen=true`。**"更多"里的入口按"是否已固定到顶栏"去重**：`pinned` 集合决定哪些入口出现在顶栏、哪些沉到 sheet（`ChatWorkbenchControls.kt:34-52,86-110`）。 |
| 6 | 视觉状态 | 有"有新内容"高亮的入口：`browserHighlight`（浏览器 tick 变化）、`repositoryHighlight`（`REPOSITORY_HIGHLIGHT_TOOLS` 在 `lastRepositoryVisitAt` 之后被调用，`ChatScreen.kt:77-83,227-232`）、`toolchainHighlight`（工具链缺口）。 |
| 7 | 布局占位与展开形态 | **不占位**（G-01 同类）：整个顶栏是 overlay，列表用 contentPadding 让出视觉空间而非布局占位。展开形态 = 底部 sheet（形态 C）。 |
| 8 | 滚动语义 | 不滚；内容从它下方穿过（顶部渐隐 mask，`ChatPaneContent.kt:169-177`）。 |
| 9 | 动效 | 浮动工具条/按钮走 miuix 玻璃材质（`RuntimeFloatingToolbar`）；无自定义显隐动画（`clearAndSetSemantics` 在 cover 时）。 |
| 10 | 边界态 | ① 手机全屏虚拟屏：左侧留 48dp 空位、右侧只留全屏键（`ChatTopBar.kt:76-117`）；② 持久导航（Expanded）：左侧 `Spacer(48dp)` 代替菜单钮（`:86`）；③ 大字号：分段 pill 宽度乘 `fontScale`（`:193`）；④ 无钉选入口：右侧只有"更多"；⑤ `pinned` 为空集合时全部沉到 sheet。 |
| 11 | 无障碍 | 每个 IconButton 都传 `contentDescription`（`R.string.chat_open_session_list`、`chat_workbench_controls`、`chat_surface_phone` 等，`:81-124`）；分段控件语义由组件提供（未在源码查证，E2）。 |
| 12 | 依据 | `E0` `feature/chat/.../ChatTopBar.kt:25-195`；`E0` `ChatWorkbenchControls.kt:28-118`；`E0` `ChatScreen.kt:339-367,227-232`；`E0` `ChatMessageList.kt:143-152`；`E0` `ChatAdaptiveLayout.kt:347-378`；`E1` `docs/ARCHITECTURE_RULES.md:52`。 |
| 13 | 反证 + 验收 | **反证**：若顶栏占一整行（常见 AppBar），首屏可用阅读高度少 56–64dp，等于每屏少一条消息；若固定在内容外不透明，消息无法"滚到顶栏下方"，用户失去"往下翻时内容还在继续"的连续感。若把 10+ 个入口全塞进顶栏，右侧胶囊会溢出成两行 —— 所以必须"钉选 + 抽屉"。**验收**：`TX-TOPBAR-01` 消息首行可滚到顶栏下方并被顶部渐隐裁掉（录屏）；`TX-TOPBAR-02` 顶栏不占用列表高度（列表首项 y 与 contentPadding 一致，截图像素量测）；`TX-TOPBAR-03` 钉选/取消钉选后入口位置在顶栏与 sheet 间正确迁移且不重复（真机）。 |

#### `chat.list.scroll-follow`（消息流 / 贴底跟随 / 回到底部）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.list.scroll-follow`。所属：聊天面板主列表（`ChatMessageList` + `ChatMessageViewport` + `rememberChatFollowLatest`）。出现条件：聊天页始终存在；"回到底部"按钮仅在"离开底部且未处于跟随时"出现（`ChatScrollToBottomButton.kt:90-96`）。 |
| 2 | 网格与定位 | LazyColumn 全屏；`contentPadding(top = statusBars + 64dp, bottom = composerHeight)`（`ChatMessageList.kt:148-151`、`ChatPaneContent.kt:162-166`）；列表外再套 `ChatMessageViewport`，FAB `align(BottomEnd).padding(end=8dp, bottom=8dp+bottomInset).zIndex(1f)`（`ChatScrollToBottomButton.kt:82-87`）。 |
| 3 | 命中区 | FAB `RuntimeFloatingActionButton(size(40.dp))` + `semantics{contentDescription}`（`:99-104`）。 |
| 4 | 手势 | 竖直拖拽 = 列表滚动；**用户一旦 `DragInteraction.Start` 就关闭跟随**，`Stop/Cancel` 时若已回到底部则恢复（`ChatScrollToBottomButton.kt:52-61`）；程序化滚动（流式跟随/发送回底/按钮跳转）**不顶掉跟随状态**（`:45-48` 注释 + `canScrollForward=false` 自动恢复，`:62-66`）。 |
| 5 | 行为 | 新消息/签名变化时：`delay(30)` 后若"会话切换 or 仍在跟随 or 用户刚发消息" → `safeScrollToAbsoluteBottom()`（`ChatScreen.kt:256-269`）；键盘弹出且仍在跟随时 `delay(80)` 后贴底（`ChatImeFollowLatest.kt:12-18`）；FAB 点击 → `safeScrollToAbsoluteBottom()`（先 `scrollToItem(末项)` 再 `scrollBy(1_000_000f)`，`ChatListScroll.kt:32-45`）。 |
| 6 | 视觉状态 | 上下渐隐遮罩：`topContentFade = chatTopEdgeFadeHeight()`，`bottomContentFade = composerHeight + chatBottomNavFadeHeight()`，分别挂 `.chatTopContentFade()/.chatBottomContentFade()`（`ChatPaneContent.kt:169-177`）；FAB 用 `surfaceContainerHigh/onSurface`、箭头旋转 180°（`ChatScrollToBottomButton.kt:99-106`）。 |
| 7 | 布局占位与展开形态 | 不适用。**FAB 是 overlay 不占位**（`ChatMessageViewport` 的 Box 兄弟层 + zIndex），列表末项留白由 `bottomContentPadding` 提供（`:113-114`）。 |
| 8 | 滚动语义 | **主列 `overscrollEffect = null`**（`ChatMessageList.kt:145-146`）：注释写明 EdgeEffect 拖拽分派会残留 pending scroll，触发 LazyListState 校验闪退 —— 这是"内滚到边再传速"在 Compose 上的一个真实故障面。滚动锚定：绝对底部用 `scrollBy` 越过末项剩余高度（`ChatListScroll.kt:32-45`）。 |
| 9 | 动效 | 贴底 `animateScrollToItem`（经 `runProgrammaticScroll` 串行器，`ChatListScroll.kt:25-29`）；FAB 有无入场动画未查证（条件渲染 `if (visible)`，无 AnimatedVisibility）。 |
| 10 | 边界态 | ① `totalItemsCount=0`：`lastLazyItemIndex` 返回 null 直接 return（防 `Index should be non-negative`，`:8-24`）；② 首屏：会话切换后按 `currentSessionKey` 只定位一次（`ChatScreen.kt:241,264-267`）；③ 流式增长中 FAB 抗抖：`derivedStateOf + 64dp tolerance`（`:92-94`）；④ 拖拽与程序化滚动冲突：`rememberProgrammaticScrollGuard(listState)`（`ChatScreen.kt:174-177`）；⑤ 用户上滑阅读时流式输出不抢滚动（`:258-267` 只在 `followLatest || userSent` 时滚）。 |
| 11 | 无障碍 | FAB 有 `contentDescription`（`R.string.chat_scroll_to_bottom`）；流式新内容**不**自动播报（未见 `liveRegion`）。 |
| 12 | 依据 | `E0` `feature/chat/.../ChatScrollToBottomButton.kt:27-108`；`E0` `ChatListScroll.kt:7-45`；`E0` `ChatImeFollowLatest.kt:10-18`；`E0` `ChatScreen.kt:174-177,256-271`；`E0` `ChatMessageList.kt:143-152`；`E0` `ChatPaneContent.kt:162-177`；`E1` `docs/ARCHITECTURE_RULES.md:47`（"软键盘弹出仅在仍跟随最新消息且用户没有滚动时定位底部"）。 |
| 13 | 反证 + 验收 | **反证**：若"新 token 到达就无条件贴底"，用户上滑读历史时每帧被拽回底部 —— 等于无法阅读；太墟用"仅跟随态才贴底 + 拖拽即退出跟随 + 回到底自动恢复"三态解决。若用 `scrollToItem(末项)` 代替 `scrollBy(大值)`，末项高于一屏时只能停在其顶部，流式增长仍看不见尾巴（`ChatListScroll.kt:32-35` 注释）。**验收**：`TX-SCROLL-01` 上滑读取 → 流式输出不拽回（真机录屏）；`TX-SCROLL-02` 回到绝对底部后恢复自动跟随（真机，连续 3 轮）；`TX-SCROLL-03` 键盘弹出时若在底部则贴底、若在看历史则不动（真机两种情形）；`TX-SCROLL-04` 回底按钮不占布局高度、滚到底自动消失（截图像素 + 真机）。 |

#### `chat.msg.process-chain`（过程链：思考 + 普通工具合成一条可折叠链）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.process-chain`。所属：聊天面板 → 消息流 → 轮次内过程区（`ToolStepsItem` + `ToolStepsCard`）。出现条件：一条连续序列里含 ≥1 个"链内工具调用"（`tool != SUBAGENT && tool != RENDER_SURFACE`）才成链；**只有思考没有工具时不成链**（`ChatTimelineProjection.kt:62-84`）。一轮 0..N 条链；文本/交互界面/子代理/A2UI 卡会**切断**链（`:69-76`）。 |
| 2 | 网格与定位 | 链头 `Row(fillMaxWidth).heightIn(min=28dp).padding(horizontal=2dp, vertical=4dp)`，`spacedBy(8dp)`；图标 `ChatProcessIcon`（18dp 槽/24 网格，E1 `ARCHITECTURE_RULES.md:47`）+ 标题 `labelMedium/onSurfaceVariant/maxLines=1` + `attention` 计数 `labelSmall/warning` + chevron `12.dp`（`ToolStepsCard.kt:47-60`）。展开体：`Column(padding(start=8dp))` 内画 1dp `outlineVariant` 竖线 + `padding(start=12dp)`（`:62-64`），子项按 `stableKey` 逐个渲染。 |
| 3 | 命中区 | 整行 `clickable` 切换（`:48-51`），有效高度 = `heightIn(min=28dp)` + `padding(vertical=4dp)` ⇒ **视觉约 28dp、命中约 36dp**；"显示更多"是 `RuntimeTextButton(contentPadding = 2dp/0dp)`（`:65-69`，命中区小，见 Q-TX-02）。 |
| 4 | 手势 | 单击链头 = 展开/收起（并清空 `timelineFocus`，`:50`）；链内工具行单击 = 打开工具详情 sheet（见 `chat.msg.tool-row`）；无长按/双击/滑动。 |
| 5 | 行为 | `expanded`/`visibleCount` 均为 `rememberSaveable(item.stableKey)`（`:30-31`）；`visibleToolChainSteps` = 末 8 步 ∪ 最近一条"待关注"（FAILED/UNKNOWN/WAITING/DEFERRED）∪ 当前焦点（`ChatTimelineProjection.kt:114-125`）；`hidden>0` 时显示"+N"按钮每次 +8（`ToolStepsCard.kt:65-69`）。焦点落在链内时自动展开（`:34-35`）。 |
| 6 | 视觉状态 | 运行中：链内工具行图标/标题 1.7s 呼吸（0.65–1，`RuntimeProcessPulse`）；`attention>0` 显示"N 待关注"warning 色（`:57-58`）；完成态不染色（E1 `ARCHITECTURE_RULES.md:47`）。链头本身无 running 态样式（E0 未见）。 |
| 7 | 布局占位与展开形态 | G-03 形态 **B（capped-inner-scroll 的近亲：截断 + 分页展开）**：链体**不进内滚容器**，而是**截断为可见步数**并向外长高（兄弟整体下移）；展开时 `animateContentSize(RuntimeTheme.motionScheme.defaultSpatialSpec())`（`:47`）。**这是第四种形态：分页展开（不是 A/B/C 任一）**，见 §4 Q-TX-08。 |
| 8 | 滚动语义 | 链体不滚，随外层会话滚；`RENDER_SURFACE`/`SUBAGENT` 被排除出链正是为了不让它们进"截断"（`ChatTimelineProjection.kt:80-84`）。 |
| 9 | 动效 | 展开 `animateContentSize(defaultSpatial)`；运行中呼吸（0.65–1，1.7s）；计时 `ChatProcessTimer` 100ms 刷新一位小数（E1 `ARCHITECTURE_RULES.md:47`）。 |
| 10 | 边界态 | ① 全部步骤都在末 8 步内：无"+N"按钮；② 长链 100+ 步：默认只组合 8 步（性能）；③ 老轮次的未完成调用不会因新一轮而变运行态（E1 同上）；④ 失败/审批/结果未知必须与完成区分（`ToolStepState` 7 态，`ChatTimelineProjection.kt:103-112`）。 |
| 11 | 无障碍 | 链头 `semantics{stateDescription = 展开/收起}`（`ToolStepsCard.kt:51`）；**无 `expand/collapse` 自定义动作**；链内每行 `stateDescription=状态文案`（`ToolStepHeader.kt:33-34`）。 |
| 12 | 依据 | `E0` `ChatTimelineProjection.kt:33-137`；`E0` `ToolStepsCard.kt:24-96`；`E0` `ToolStepHeader.kt:22-57`；`E0` `ChatTimelineSpacing.kt:9-20`（项间距：两条紧凑工具行 0、混合 4、普通 8、用户消息后 12/前 24）；`E1` `docs/ARCHITECTURE_RULES.md:47`。 |
| 13 | 反证 + 验收 | **反证**：若每个工具调用都独立成卡（旧 myco 式），一轮 30 次工具 = 30 张卡，用户永远找不到正文；若整链默认全展开，长任务会把会话拉成几十屏。太墟的取舍 = "标题只给数量 + 首 8 步 + 待关注固定露出 + 分页展开"，代价是**看不见中间步骤的细节（要点开）**。若把 A2UI/子代理也算进链，它们的卡片会被截断成"第 N 步"而失去独立语义（所以必须切断）。**验收**：`TX-CHAIN-01` 一轮 30 次工具调用，链头显示"30 个工具调用"、默认 8 行（真机 + 截图计数）；`TX-CHAIN-02` 中间某步失败后该步始终可见且有 warning 计数（真机构造失败）；`TX-CHAIN-03` 点"+N"每次多 8 行、不跳动（真机）；`TX-CHAIN-04` A2UI 卡与子代理卡不出现在链内（真机）。 |

#### `chat.msg.tool-row`（工具行：28dp 行高 / 18dp 图标 / 计时 / 状态语义）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.tool-row`。所属：聊天面板 → 过程链内一步 / 独立工具卡（`ToolStepHeader` + `ToolCard`）。出现条件：每个 `ToolCall` 一个；出现在链内（`ToolStepsCard`）或独立 `ToolCard` 里（子代理/A2UI 卡之外的普通工具）。 |
| 2 | 网格与定位 | `Row(fillMaxWidth).heightIn(min=28dp).padding(horizontal=2dp, vertical=4dp)`，`spacedBy(8dp)`，`CenterVertically`（`ToolStepHeader.kt:33-35`）；元素序：图标 → 标题(+`· target`) `weight(1f)` → 状态文字（仅非成功/非运行）→ Diff 徽标 → 计时 → chevron `16.dp`。 |
| 3 | 命中区 | 独立 `ToolCard` 的**行区域**整块 `clickable { showDetails = true }`（`ChatToolCards.kt:98-102`），命中 = 整行宽 × 行高（≈36dp）；链内行本身不可点（点链头展开），工具详情需展开链后点行 —— 见 §4 Q-TX-14。 |
| 4 | 手势 | 单击 = 打开工具详情（底部 sheet）；无长按/双击；竖直拖动归外层。 |
| 5 | 行为 | 打开 `ToolDetailsSheet`（`showDetails` 由 `rememberToolDetailsVisible(call.id)` 持有）；重试在详情 sheet 底部（`canRetry = !running`，`ToolDetailsSheet.kt:103-107`）。**标题不含命令参数/脚本正文**：`toolStepTarget` 只取 `path/name/id` 的 basename 且截 100 字符、去换行（`ToolStepHeader.kt:76-92`）。 |
| 6 | 视觉状态 | 7 态 → 颜色：FAILED/UNKNOWN = `error`；WAITING/DEFERRED = `semanticColors().warning`；SUCCEEDED/RUNNING/INCOMPLETE = `chatProcessColor()`（`onSurfaceVariant` 次级灰）（`ToolStepHeader.kt:28-32`）；运行中图标呼吸 + 计时跳动；成功态**不染色**（`stateText` 只在非成功/非运行显示，`:46-47`）；下载类工具显示 2dp 线性进度条（不确定进度/确定百分比两种，`ChatToolCards.kt:104-131`）。 |
| 7 | 布局占位与展开形态 | 行本身不展开（展开交给 sheet，形态 C）。**定高性**：`heightIn(min=28dp)` 是下限不是定高，标题 `maxLines=1` → 内容变化不改行高（等价 G-02 效果）。 |
| 8 | 滚动语义 | 不滚；长输出进 sheet 内滚。 |
| 9 | 动效 | 运行中呼吸 1.7s（0.65–1）；计时 100ms 刷新但"动画只更新绘制层，不逐帧重组正文"（E1 `ARCHITECTURE_RULES.md:47`）。 |
| 10 | 边界态 | ① 未知工具：`LocalToolUiRegistry.resolve` 回退默认渲染器（`ToolUiRegistry.kt`）；② 无耗时：`result?.durationMs ?: createdAt-call.createdAt`（`ToolStepHeader.kt:52-53`）；③ 超长文件名：`maxLines=1 + ellipsis`；④ 大字号：`heightIn(min)` 允许自然增高（E1 同上）。 |
| 11 | 无障碍 | `semantics{stateDescription = 状态文案}`（`ToolStepHeader.kt:33-34`），7 态各有独立字符串资源（`:66-74`）；运行中是否播报未查证（E2）。 |
| 12 | 依据 | `E0` `ToolStepHeader.kt:22-93`；`E0` `ChatToolCards.kt:69-142`；`E0` `ChatTimelineProjection.kt:103-112`；`E0` `feature/chat/.../ToolUiRegistry.kt`、`ChatProcessTimer.kt`、`ToolActivityPill.kt`；`E1` `docs/ARCHITECTURE_RULES.md:47`。 |
| 13 | 反证 + 验收 | **反证**：若行内直接贴出 shell 命令/脚本正文（很多 agent App 的做法），长参数会把一行撑成 5 行、且把工作区路径/URL 泄到会话首屏；太墟只用"工具名 · 文件名"作摘要、参数进 sheet。若成功态也染色，一屏几十个绿点会盖过失败红点，用户看不出哪一步出事。**验收**：`TX-TOOLROW-01` 行高恒定 28dp（大字号除外），流式参数预览不改变行高（录屏量测）；`TX-TOOLROW-02` 失败/待审批/运行三态颜色与文字可区分（截图三连）；`TX-TOOLROW-03` 行内不出现完整命令行/URL（真机 + 文本断言）。 |

#### `chat.msg.tool-detail-sheet`（工具详情：参数 / 输出 / 预览三页签）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.tool-detail-sheet`。所属：聊天面板 → 工具行 → 详情抽屉（`ToolDetailsSheet`）。出现条件：点工具行打开，同一时刻 1 个；`fileTool`（渲染器提供 `previewLabel`）时多出第三页签。 |
| 2 | 网格与定位 | `ChatBottomSheet`，标题区 = 图标 22dp + 两行标题（titleMedium + bodySmall 单行 ellipsis）+ 关闭钮 40dp（`ToolDetailsSheet.kt:44-58`）；正文 = `RuntimeSegmentedControl` 页签行 + 状态文字 + 内滚内容区（`weight(1f, fill=false).verticalScroll`，`:67-76`）；底栏 = 复制（左）+ 重试（右）。 |
| 3 | 命中区 | 关闭钮 40dp；页签由分段控件；底栏 `RuntimeTextButton`（默认 contentPadding 12/8，视觉约 36dp，未挂 48dp 助手）；长文本 `SelectionContainer` 可长按选择（`:80-82`）。 |
| 4 | 手势 | 单击页签切换；单击复制 → 剪贴板 + Toast；单击重试 → 关闭 + 触发 `onRetry`；下拉/返回关闭；正文竖直滚动。 |
| 5 | 行为 | 初始页签：`fileTool → 2（预览）` 否则 `0（参数/命令）`（`:40`）；复制内容随页签变化（输出页复制输出、参数页复制参数，`:42,93-101`）；重试仅当 `canRetry && result != null`（`:103-107`）。 |
| 6 | 视觉状态 | 当前页签高亮由分段控件；状态文字（7 态之一）`onSurfaceVariant`（`:73-74`）；超长截断提示"（预览）"文案（`:83-84`）。 |
| 7 | 布局占位与展开形态 | 形态 **C**（sheet）。正文截断到 64_000 字符再渲染（`:81`, `currentText.take(64_000)`），超长提示不静默截断。 |
| 8 | 滚动语义 | 正文单层 `verticalScroll`；sheet 自身可下拉关闭 —— **内滚与外层下拉方向相同**，是 G-04 风险点（E0 未见 `nestedScroll` 仲裁，见 §4 Q-TX-15）。 |
| 9 | 动效 | sheet 入场（miuix）；页签切换无动画（E0 未见 AnimatedContent）。 |
| 10 | 边界态 | ① 无输出：显示"无输出"占位（`:85-88`）；② JSON 参数：`prettyPrint` 后渲染（`:111`）；③ 二进制/图片：走渲染器 `Preview`（`:77-78`）；④ 打开预览后点文件 → 先 `onDismiss()` 再 `open()`（`:78`）。 |
| 11 | 无障碍 | 关闭/复制/重试都有文字标签（复制有图标+文字）；`SelectionContainer` 提供文本选择语义。 |
| 12 | 依据 | `E0` `feature/chat/.../ToolDetailsSheet.kt:30-116`；`E0` `feature/chat/.../ChatBottomSheet.kt`；`E1` `docs/ARCHITECTURE_RULES.md:47`（"点击命令后用底部弹窗查看参数、输出、文件与 Diff，聊天列表不原地展开日志"）。 |
| 13 | 反证 + 验收 | **反证**：若在列表里原地展开输出（形态 A/B），长日志会把会话拉爆、跟随滚动失效、且回退时阅读锚点丢失（G-03 的 C 反证原句）。若把 64k 以上内容整段渲染，一次 10MB 日志会让 Compose 文本测量卡死。**验收**：`TX-TOOLDETAIL-01` 打开/关闭后聊天首行 y 不变（截图像素）；`TX-TOOLDETAIL-02` 10MB 输出只渲染前 64k 且有提示（真机构造）；`TX-TOOLDETAIL-03` 复制/重试可点且重试在运行中隐藏（真机）。 |

#### `chat.msg.thinking`（思考块：流式限高内滚 / 定稿不封顶 / 离线翻译）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.thinking`。所属：聊天面板 → 轮次内过程区（`ThinkingBlock`）。出现条件：① `AssistantText.reasoning` 非空且**未被链吸收**时由 `ReasoningRow` 渲染；② 带推理的助手消息被 `splitReasoning` 拆出后进入过程链（`ChatTimelineProjection.kt:45-56`）。一轮 0..N 个，每个展开态独立（`rememberSaveable(id)`）。 |
| 2 | 网格与定位 | 头部 `Row(fillMaxWidth).heightIn(min=28dp).padding(horizontal=2dp, vertical=4dp)`，`spacedBy(8dp)`：`ChatProcessIcon(Brain)` + 标题/耗时 `weight(1f)` + [展开时] 翻译 18dp + 复制 18dp + chevron 16dp（`ChatMessageBubbles.kt:565-647`）。展开体 `ChatProcessContent` 内 `padding(top=12dp, bottom=4dp)`（`:654-662`）。 |
| 3 | 命中区 | 头部整行 `clickable`（≈36dp 宽整行）；**翻译/复制 `IconButton(size(18.dp))`**（`:609,631`）与 `chevron 16.dp`（`:644`）—— 远小于 44dp，见 Q-TX-02；展开体本身也 `clickable{ expanded=false }`（`:657-661`，点正文即收起）。 |
| 4 | 手势 | 单击头部/正文 = 折叠或展开，带 `HapticFeedbackType.TextHandleMove`（`:571,:659`）；流式态内滚区竖直拖动 → **`DragInteraction.Start` 即暂停跟随**，`Stop/Cancel` 时到边自动恢复（`:528-537`、`:541`）；无长按/双击。 |
| 5 | 行为 | `expanded` 初值 = `defaultExpanded`（= 全局"思考展开"开关，`ChatScreen.kt:110`）；流式态跟随到底：`snapshotFlow{Triple(maxValue, following, inProgress)}` 在"展开 && 跟随 && 未拖动"时 `scrollTo(max)`（`:538-544`）；翻译：离线 ML Kit，`showTranslation` 切换原文/译文，点击复制复制"当前显示的那份"（`:558-563`、`:578-627`）；未下载模型弹 `RuntimeAlertDialog` 并可跳「智能体设置」（`:741-782`）。 |
| 6 | 视觉状态 | `live`（正在生成）：图标呼吸（`ChatProcessIcon(running=live)`）；`settled`：静态；文字统一 `chatProcessColor()`（onSurfaceVariant 次级灰）+ `chatReadingTypography().body`；译文头显示"中文译文 (ML Kit 离线生成)" `primary`（`:676-680`）；翻译中显示 24dp 转圈 + "正在离线翻译思考内容..."（`:687-704`）。 |
| 7 | 布局占位与展开形态 | **同一节点两种形态**：`live` → **B（限高 160dp 内滚）**（`:706-728`）；`settled` → **A（不封顶 Markdown）**（`:729-734`）。展开/收起用 `AnimatedVisibility(expandVertically()+fadeIn() / shrinkVertically()+fadeOut())`（`:649-653`）—— **对长内容也做高度动画**，与 myco G-10 直接冲突（Q-TX-01）。兄弟位移：展开时下方所有兄弟整体下移。 |
| 8 | 滚动语义 | `live`：内层 `verticalScroll(liveScroll)` 限高 160dp，**用户拖动即暂停跟随**，未跟随时右下角显示「跟随」`TextButton`（`:717-727`）；到边后自动恢复跟随（`>= maximum - 2`，`:541`）。`settled`：无内滚，随外层会话滚。→ 这是 **G-04 "手势=意图"** 的一个原生 Compose 实现样本。 |
| 9 | 动效 | 展开/收起高度动画（`expandVertically`/`shrinkVertically`，默认 spring）；运行中图标 1.7s 呼吸；翻译中转圈。 |
| 10 | 边界态 | ① 空 reasoning：不渲染（调用点 `takeIf{isNotBlank}`）；② 极长（数千行）：`live` 时限高内滚、`settled` 时不封顶（可能很长，用户可折叠）；③ 未下载翻译模型：弹窗 + 下载进度文案（`:741-782`）；④ 纯思考占位的流式气泡：只有思考没有正文（`ChatTimelineProjection.kt:41-43` 注释）；⑤ 大字号：`heightIn(min)` + 限高 160dp 是**固定值**，大字号下只能显示更少行（隐患，见 Q-TX-16）。 |
| 11 | 无障碍 | 头部 `clickable` 无 `role`/`stateDescription`（**未见**，与过程链不一致）；翻译/复制有 `contentDescription`（`:610,632`）；运行中**没有**视觉隐藏的"正在思考"播报（对照 myco G-08 缺失）。 |
| 12 | 依据 | `E0` `feature/chat/.../ChatMessageBubbles.kt:492-783`；`E0` `ChatReasoningRow.kt:5-18`；`E0` `ChatTimelineProjection.kt:41-56,80-84`；`E1` `docs/ARCHITECTURE_RULES.md:47`（"思考保留限高视口、跟随尾部和手动阅读保护"——**这是群聊描述，单聊实现同样如此**）。 |
| 13 | 反证 + 验收 | **反证**：流式时若不限高，一条两屏的思考会把正在阅读的正文顶出屏幕，每来一个 token 就重排一次（G-02 症状）；而限高后若"不支持暂停跟随"，用户想回头看一句正在被追加的思考就会被不断推走 —— 太墟用"拖动即暂停 + 到边自动恢复 + 显示跟随按钮"解决。定稿后若仍限高，长思考被锁进 160dp 小窗，用户读不完（G-03 的 B 反证）—— 所以必须**按状态切形态**。**验收**：`TX-THINK-01` 流式中思考区高度恒为 ≤160dp、正文不被逐 token 推动（录屏量测）；`TX-THINK-02` 拖动思考内滚后不再自动跟尾，点「跟随」恢复（真机）；`TX-THINK-03` 定稿后思考完整不截断、无内滚（真机 + 截图）；`TX-THINK-04` 点正文任意处收起、点头部收起（真机两处）。 |

#### `chat.msg.assistant-bubble`（助手正文气泡 + 轮次动作）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.assistant-bubble`。所属：聊天面板 → 消息流 → `AssistantBubble`。出现条件：每条 `AssistantText` 一个；若 `reasoning` 已作为链内步骤渲染则气泡内不再重复（`showReasoning=false`，`ChatMessageList.kt:218-228`）。**只有"每轮最后一条有正文的助手消息"获得完整动作条**（`assistantToolbarIds`，`ChatTimelineProjection.kt:127-137`），中间更新只有 details 菜单。 |
| 2 | 网格与定位 | 左对齐全宽（`fillMaxWidth`），无气泡背景（"普通正文不套色面卡片"，E1 `ARCHITECTURE_RULES.md:48` 群聊条款；单聊同样无底色）；正文 `chatReadingTypography().body`（15sp/23sp）；用户气泡有 `widthIn(max=560.dp)`（E1 `:56`）。Markdown 由 `feature/components/MarkdownText` 渲染。 |
| 3 | 命中区 | 正文内链接/代码块可点（Markdown 组件）；动作条按钮为 `IconButton`(40dp 级)或文字钮；"每段正文直接提供复制和创建分支按钮"（E1 `ARCHITECTURE_RULES.md:47`）。 |
| 4 | 手势 | 单击动作：复制 / 创建分支 / 重新生成 / 反馈；正文内长按进入文本选择（`SelectionContainer`，未在所有 Markdown 处核实，E2）；链接点击走系统 Intent。 |
| 5 | 行为 | 复制 → 剪贴板 + Toast；创建分支 → `CreateBranchDialog`（`ChatScreen.kt:612-615`）；重新生成 → `regenerateLast(sessionId, lastAssistantMessageId)`（`:430`）；反馈 → `MessageFeedbackAction`。 |
| 6 | 视觉状态 | 流式中末尾光标/等待指示；`live` 与 `settled` 的差异在 Markdown 渲染器；`prominentActions` 决定动作条是否常显（`ChatMessageList.kt:223`）。 |
| 7 | 布局占位与展开形态 | 不展开（思考另立节点）。高度随内容；流式追加时**下方兄弟会被推动**（这是聊天流的固有行为，与折叠行不同，见 G-02 适用范围说明）。 |
| 8 | 滚动语义 | 不自身滚；超长正文随会话滚。代码块横向滚动（`SyntaxHighlighter`/Markdown 组件）。 |
| 9 | 动效 | 流式追加无动画（逐帧重组）；复制/反馈的 Toast 无动画。 |
| 10 | 边界态 | ① 空正文（纯思考占位）：`AssistantText.text.isBlank()` 时由 splitReasoning 只保留思考步骤；② 超长：Markdown 分段渲染；③ 失败/重试：错误横幅 + 消息级重试（`ChatErrorBanner`）；④ 多语言：正文不受 UI 语言影响。 |
| 11 | 无障碍 | 动作条按钮都应有 `contentDescription`（`ChatMessageActions.kt`）；正文 Markdown 的可访问文本未特别处理（E2）。 |
| 12 | 依据 | `E0` `feature/chat/.../ChatMessageBubbles.kt:370-491`；`E0` `ChatMessageList.kt:218-228`；`E0` `ChatTimelineProjection.kt:127-137`；`E0` `ChatMessageActions.kt`、`MessageFeedbackAction.kt`、`AssistantMessageFooter.kt`；`E0` `feature/components/.../Markdown*.kt`；`E1` `docs/ARCHITECTURE_RULES.md:47-48,56`。 |
| 13 | 反证 + 验收 | **反证**：若每条中间助手消息都挂满"复制/分支/重生成/反馈"，一屏会出现十几组重复按钮，正文被按钮切割；太墟只给每轮最后一条完整动作条，中间更新收进 details。若给正文套色面卡片，长文阅读密度下降、且与"过程行无底色"的层级对比消失。**验收**：`TX-ASSIST-01` 一轮内只有最后一条有完整动作条（真机 + 截图）；`TX-ASSIST-02` 复制得到纯文本（不含 Markdown 标记）（单测/真机）；`TX-ASSIST-03` 正文宽度在 560dp 上限内不超宽（宽屏真机）。 |

#### `chat.msg.user-bubble`（用户消息气泡 + 编辑/重发/回滚）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.user-bubble`。所属：聊天面板 → 消息流 → `UserBubble`。出现条件：每条 `UserMessage` 一个；带附件时含缩略图行（`ImageThumbnail`，`ChatMessageBubbles.kt:335-369`）。 |
| 2 | 网格与定位 | 右对齐；`widthIn(max = 560.dp)`（E1 `ARCHITECTURE_RULES.md:56`）；背景 `primaryContainer/onPrimaryContainer`（E1 `:48`）；气泡形状 `clipBubbleShape`（右上小圆角，`ChatMessageBubbles.kt:322-334`）；`@提及` 用 `ChatMentionText` 高亮。 |
| 3 | 命中区 | 整气泡长按/点击 → 菜单（编辑/删除/创建分支/回滚）；具体动作在 `UserBubble` 参数里（`onEdit`/`onDelete`/`onCreateBranch`/`onRewind`，`ChatMessageList.kt:210-217`）。 |
| 4 | 手势 | 单击 = 打开操作菜单（E0：`UserBubble` 内 `clickable`/`combinedClickable` 未逐行核实，由 facts.csv 该文件 38 处交互点覆盖）；回滚有独立 `RewindScopeOption`（单选作用域，`ChatMessageBubbles.kt:286-321`）。 |
| 5 | 行为 | 编辑 → `EditAndResendDialog`（`ChatScreen.kt:479-488`）→ `editAndResend`；删除 → `deleteMessage` + `SessionDeletion`；创建分支 → `CreateBranchDialog`；回滚 → `rewindToMessage(id, scope)`，完成后 Snackbar 提供"撤销回滚"（`ChatScreen.kt:312-325`）。 |
| 6 | 视觉状态 | 默认 `primaryContainer`；`@提及` 高亮；附件缩略图圆角裁切。 |
| 7 | 布局占位与展开形态 | 不展开。 |
| 8 | 滚动语义 | 不滚；超长正文换行（无内滚）。 |
| 9 | 动效 | 无自定义（菜单/sheet 由底层组件）。 |
| 10 | 边界态 | ① 超长文本：粘贴长文本会转附件（`PasteLongTextReceiver`/`addTextAttachment`）；② 附件：缩略图 + 删除；③ 编辑后重发会消耗历史（回滚语义）；④ 多语言/大字号：跟随主题。 |
| 11 | 无障碍 | 气泡 `clickable` 是否有 `role=Button` 未查证（E2）；附件缩略图有 `contentDescription`（`ImageThumbnail`）。 |
| 12 | 依据 | `E0` `feature/chat/.../ChatMessageBubbles.kt:100-369`；`E0` `ChatMessageList.kt:210-217`；`E0` `ChatScreen.kt:479-488,612-615`；`E0` `ChatMentionText.kt`、`UserMessagePresentation.kt`；`E1` `docs/ARCHITECTURE_RULES.md:48,56`。 |
| 13 | 反证 + 验收 | **反证**：若用户气泡不限宽，一句 2000 字的需求会占满整屏宽、行长达 60+ 字符，阅读视线回扫成本剧增；若不提供"编辑并重发"，用户改一个字要重新输入整段。**验收**：`TX-USER-01` 气泡最宽 560dp（宽屏真机量测）；`TX-USER-02` 编辑并重发后旧消息与新消息的替换关系正确（真机）；`TX-USER-03` 回滚后 Snackbar 可撤销且状态一致（真机）。 |

#### `chat.msg.approval-card`（审批卡 / 提问卡 / 交互门控）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.approval-card`。所属：聊天面板 → 消息流末尾（`pendingApprovals.firstOrNull()` 插在列表最后，`ChatMessageList.kt:270-288`）。出现条件：`AgentApprovalRequestEntity` 非空；`toolName == ask_user_questions` 走提问卡，否则审批卡（`:271-284`）。同一时刻只显示**第一条**待审批。 |
| 2 | 网格与定位 | 列表末尾 item；`Surface(shape=small, color=surfaceContainerLow, border=1dp 风险色@0.55)`（`ChatToolCards.kt:155-160`）；内部 `Column(padding 12dp, spacedBy 8dp)`：盾牌 18dp + "需要审批" titleSmall + 风险等级徽标（`:165-175`）。 |
| 3 | 命中区 | 底部动作按钮（`RuntimeActionRow`/`RuntimeTextButton`）；"记住选择"复选框；风险徽标不可点。 |
| 4 | 手势 | 单击批准/拒绝；长按无；`ApprovalRequestCard` 的批准可带 `rememberForSession`（`ChatScreen.kt:442`、`ChatToolCards.kt:281`）。 |
| 5 | 行为 | 批准 → `resolveApproval(id, true, rememberForSession)`；拒绝 → `resolveApproval(id, false, false)`；提问卡 → `resolveQuestion(id, answer)` / 跳过（`ChatMessageList.kt:271-284`）。决议提交后卡片从列表消失（VM 状态驱动）。 |
| 6 | 视觉状态 | 风险三色：`critical → error`、`high → warning`、其他 `primary`（`ChatToolCards.kt:150-154`）；按钮禁用态由 `Runtime*` 组件。 |
| 7 | 布局占位与展开形态 | **占列表末尾一项**（不是浮层）—— 与"回到底部按钮"的 overlay 策略相反，这是太墟唯一"必须占位"的交互门：审批必须挡住对话流、不能飘走。 |
| 8 | 滚动语义 | 随列表末尾；用户上滑读历史时审批卡不在视口内（只有第一条显示，其余等解决后依次出现）。 |
| 9 | 动效 | 无自定义（出现/消失是列表 item 增删）。 |
| 10 | 边界态 | ① 多条待审批：只显示第一条；② 审批模式为"全部放行"：不产生卡片（策略层决定）；③ `AskUserQuestions`：卡片形态不同（问答 + 跳过）；④ 恢复会话时保留未决审批（E1 `README.md`：待审批操作保留具体调用）。 |
| 11 | 无障碍 | 风险等级是**颜色 + 文本徽标**双编码（`:168-175`），不单靠颜色；按钮文字即标签。 |
| 12 | 依据 | `E0` `feature/chat/.../ChatToolCards.kt:144-500`；`E0` `ChatMessageList.kt:270-288`；`E0` `ChatDialogs.kt`（审批/提问对话框集合，facts.csv 34 条）；`E0` `ChatScreen.kt:440-443`；`E1` `README.md`（分级工具审批）、`docs/ARCHITECTURE_RULES.md:47`（"审批模式在会话输入框工具栏展示与切换，全部放行状态始终可见"）。 |
| 13 | 反证 + 验收 | **反证**：若把审批做成浮层（toast/sheet），用户读到一半可能没看见就滑过去了，agent 一直挂着；若只显示风险色不给文字，色弱用户无法判断 critical。**验收**：`TX-APPROVAL-01` 待审批卡固定出现在列表最末且不被输入框遮挡（真机 + 截图）；`TX-APPROVAL-02` 风险三级的颜色+文字都可区分（色弱模拟）；`TX-APPROVAL-03` 批准"记住本次会话"后同类调用不再拦（真机两轮）。 |

#### `chat.msg.plan-card`（计划看板 / 置顶计划条）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.plan-card`。所属：聊天面板 → 输入区上方 `StickyPlanBar`（`ChatPaneContent.kt:225-234`）+ 列表内 `PlanBoard`/`ChatTaskPlanCard`。出现条件：`activePlan != null` 且 `PlanStepParser.parse(stepsJson)` 非空。 |
| 2 | 网格与定位 | `StickyPlanBar(fillMaxWidth, padding vertical 4dp)` 钉在**输入区上方、错误横幅之下**（`ChatPaneContent.kt:218-234`）；列表内 `PlanBoard`（389 行）展开看步骤详情。 |
| 3 | 命中区 | 计划条点击展开 `PlanBoard`（`ChatTaskPlanCard.kt`）；步骤行可点定位（E2）。 |
| 4 | 手势 | 单击展开/收起；无长按/滑动。 |
| 5 | 行为 | 计划由 agent 的 plan 工具写入数据库（`AgentPlanEntity`），VM 投影成 `activePlan`；步骤状态由 `PlanStepParser` 解析。**进度呈现** = 步骤清单 + 状态色，不是进度条（E0 未见百分比）。 |
| 6 | 视觉状态 | 步骤状态（待办/进行/完成/失败）由 `PlanBoard` 内部渲染；进行中步骤呼吸（沿用过程行样式，E2）。 |
| 7 | 布局占位与展开形态 | **占位**（在输入区上方占一条，不是浮层）—— 与 G-01"回到底部不占位"不同：计划条被明确放在 `bottomChrome` 高度统计内（`ChatPaneContent.kt:222` 的 `onSizeChanged` 把整个底部 Column 高度算进 `bottomChromePx`，列表 `bottomContentPadding = bottomChrome`）。 |
| 8 | 滚动语义 | 自身不滚；列表底部留白随它高度变化（通过 `onSizeChanged` 反馈）。 |
| 9 | 动效 | 出现/消失是条件渲染（无 AnimatedVisibility，E0）。 |
| 10 | 边界态 | ① 空步骤：不显示；② 长计划（20+ 步）：看板内滚/折叠（未逐行核实，E2）；③ 计划完成：是否自动隐藏未查到（E2）。 |
| 11 | 无障碍 | 步骤状态是否播报未查证（E2）。 |
| 12 | 依据 | `E0` `feature/chat/.../ChatPaneContent.kt:218-234`；`E0` `feature/chat/.../PlanBoard.kt:1-389`、`ChatTaskPlanCard.kt:1-210`；`E0` `ChatScreen.kt:151`（`activePlan`）；`E1` `README.md`（"双智能体模式……支持依赖感知调度与步骤状态展示"）。 |
| 13 | 反证 + 验收 | **反证**：若计划只在列表里（不在输入区上方），用户滚到底部输入时看不到"agent 现在做到哪一步"，只能反复上滑；若计划条做成浮层（不占位），会覆盖输入框上沿的消息。**验收**：`TX-PLAN-01` 计划条常驻输入区上方且不遮挡最后一条消息（真机）；`TX-PLAN-02` 步骤状态与工具行状态一致（同轮对照）。 |

#### `chat.msg.a2ui-surface`（A2UI 界面卡：聊天内嵌声明式界面）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.a2ui-surface`。所属：聊天面板 → `ToolCard(RENDER_SURFACE)` 内嵌（`A2uiPocSurfaceCard`）。出现条件：`call.tool == RENDER_SURFACE && result.success`（`ChatToolCards.kt:133-136`）；`sessionId` 从 `LocalA2uiSessionId` 取（`ChatScreen.kt:371`）。一轮 0..N 张。 |
| 2 | 网格与定位 | `fillMaxWidth` 卡片，位于工具行**下方**（`ToolCard` Column 内 `spacedBy(2dp)`，`ChatToolCards.kt:84-136`）；内部 `Column(fillMaxWidth, spacedBy 6dp)`：可选标题（labelMedium SemiBold primary）+ Surface 宿主（`A2uiPocScreen.kt:108-131`）。 |
| 3 | 命中区 | 由 Catalog 组件决定；宿主不加额外手势。 |
| 4 | 手势 | 组件内建（按钮/开关/滑杆/文本输入）；整个卡片不可折叠/不可长按。 |
| 5 | 行为 | 渲染：`scopeMessages(messagesJson, sessionId)` 给 surfaceId 加会话前缀 → `A2uiSurfaceBus.engineSurfaceId` → `processMessages(scoped, replayKey=call.id)` → `SurfaceView(engineId)`（`A2uiPocScreen.kt:89-116`）；同一 `call.id` 重进列表不重复投喂（指纹去重）。 |
| 6 | 视觉状态 | 三态：骨架（加载宽限期内）→ 成功渲染 → 错误卡（`errorContainer@0.4` + 前 1200 字原文，10 行 monospace，`A2uiPocScreen.kt:134-160`）。 |
| 7 | 布局占位与展开形态 | **占位**（是聊天项的一部分）。**无折叠形态**：模型要更新同一 surface 必须再调 `render_surface` 带 `updateComponents`，卡片内容原地替换（引擎内部状态）；旧版本是否保留在列表里 —— 同一 surfaceId 的卡片会各自持有一份工具结果，聊天里可同时存在多张（历史轮次）。 |
| 8 | 滚动语义 | 卡片整体随会话滚；内部若含 List/Table 则限高/横向滚动（见 `base.renderer.a2ui`）。**这是 G-04 最危险的一处**：卡片内的 `LazyColumn(320dp)` 与聊天主列嵌套。 |
| 9 | 动效 | `A2uiSurface` 的 `AnimatedContent`（加载→成功）+ `SizeTransform(clip=false)`；卡片内容更新播放同上（`A2uiSurface.kt:74-95,130-151`）。 |
| 10 | 边界态 | ① 载荷为空 → "载荷为空"错误卡；② surfaceId 对不上 → 宽限期后回退原文；③ 会话删除 → `releaseSession` 拆 surface，卡片下次渲染回退原文；④ 同一会话 80+ 张卡 → 去重表 1024 容量（`TaiXuA2uiRenderer.kt:87-88,314-315`）；⑤ 同 surfaceId 跨会话隔离（`engineSurfaceId` 前缀）。 |
| 11 | 无障碍 | 由 Catalog 组件语义（见 `base.renderer.a2ui`）；错误卡是纯文本，无 role。 |
| 12 | 依据 | `E0` `feature/chat/.../ChatToolCards.kt:133-136`；`E0` `feature/a2uipoc/.../A2uiPocScreen.kt:45-160`；`E0` `ChatScreen.kt:371`（`LocalA2uiSessionId provides currentSessionId`）；`E0` `ChatTimelineProjection.kt:80-84`（不进过程链）；`E0` `ChatTimelineSpacing.kt:22-26`（不计紧凑行）；`E1` `README.md:30`。 |
| 13 | 反证 + 验收 | **反证**：若把 A2UI 卡塞进过程链（当成普通工具步骤），它会被"首 8 步"截断、要展开才看得见，而它恰恰是**用户要点按操作的界面**（不是日志）；所以必须切断链、独立成卡。若失败时静默（只留空白），用户只会看到一张空卡、模型也不知道渲染失败 —— 太墟把错误回传会话让模型自纠。**验收**：`TX-A2UISURF-01` 卡片始终独立于过程链且不随链折叠消失（真机）；`TX-A2UISURF-02` 非法载荷显示错误卡 + 原文（真机构造）；`TX-A2UISURF-03` 同 surfaceId 二次 `render_surface` 后界面原地更新而非新增卡片（真机 + 计数）。 |

#### `chat.msg.subagent-card`（子代理卡 / 分支浏览 / 成果抽屉）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.subagent-card`。所属：聊天面板 → 消息流（`SubagentCard`）+ 抽屉（`BranchBrowserSheet`/`SubagentResultSheet`）。出现条件：`ToolCall(tool == SUBAGENT)` 独立成卡（不进链，`ChatTimelineProjection.kt:80-84`）； drawers 由顶栏/消息动作打开。 |
| 2 | 网格与定位 | `SubagentCard` 全宽卡（`ChatMessageBubbles` 同级）；`BranchBrowserSheet`/`SubagentResultSheet` 在 `ChatWorkbenchPanels.kt:139-499`。子代理成果抽屉**叠在分支列表之上**，关闭后回列表（`ChatScreen.kt:538-559`）。 |
| 3 | 命中区 | 卡内"查看详情/打开分支"按钮；抽屉内分支卡整卡可点（`BranchCard`，`ChatWorkbenchPanels.kt:230-292`）。 |
| 4 | 手势 | 单击进入子代理 transcript；抽屉内点分支切换；返回键先关成果抽屉再关分支列表（由 sheet 各自 BackHandler）。 |
| 5 | 行为 | `openSubagentResult(branch)` 打开只读 transcript（`SubagentResultSheet` 有 `onRefresh`）；切换会话时自动关闭遗留成果抽屉（`ChatScreen.kt:327-331`）。 |
| 6 | 视觉状态 | 分支卡有 running 态（`BranchBrowserSheet(branches, running)`）；只读 transcript 无输入区。 |
| 7 | 布局占位与展开形态 | 卡片占位；抽屉形态 **C**。 |
| 8 | 滚动语义 | transcript 自身滚动；不嵌套主列。 |
| 9 | 动效 | sheet 入场。 |
| 10 | 边界态 | ① 无子代理：卡片不出现；② 分支很多：列表滚；③ 成果为空：刷新按钮可重试。 |
| 11 | 无障碍 | 按钮有标签（E2 未逐条核实）。 |
| 12 | 依据 | `E0` `feature/chat/.../SubagentCard.kt:1-356`；`E0` `ChatWorkbenchPanels.kt:139-499`；`E0` `ChatScreen.kt:327-331,538-559`；`E0` `ChatTimelineProjection.kt:80-84`。 |
| 13 | 反证 + 验收 | **反证**：若子代理过程也进主过程链，主链会被子代理的长任务撑爆且用户分不清"谁在做"；独立卡片 + 只读 transcript 保住"主会话是我和主 agent，子代理是另一个 lane"的心智。**验收**：`TX-SUBAGENT-01` 子代理卡不进链（真机）；`TX-SUBAGENT-02` 切换会话时成果抽屉自动关闭（真机）。 |

#### `chat.sheet.workbench`（顶栏"更多"工作台 + 各类诊断抽屉）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.sheet.workbench`。所属：聊天面板 → 顶栏"更多"入口打开的 `PlainBackdropModalBottomSheet` + 各类 sheet（分支、运行时间线、大纲、记忆、上下文用量、请求上下文诊断、MCP/工作流建议横幅）。出现条件：点"更多"或对应入口；每次 1 个（可叠：成果抽屉叠分支列表）。 |
| 2 | 网格与定位 | 分组结构：`ChatWorkspaceBar`（浏览器/仓库）→ `SessionActions`（Bot/工坊/记忆/大纲/导出）→ `ChatWorkbenchControls`（运行模式/分支/运行时/工具链/思考强度/悬浮聊天/语音），`Column(padding 12dp, spacedBy 8dp)`（`ChatWorkbenchControls.kt:28-118`）；每组 `RuntimeSectionTitle + RuntimeSheetCard + WorkbenchRow`。 |
| 3 | 命中区 | `WorkbenchRow` 整行可点（高度由 `RuntimeSheetCard` 行密度决定）；"更多"入口是 40dp 图标钮（`ChatTopBar.kt:121-124`）。 |
| 4 | 手势 | 单击执行动作并**自动关闭 sheet**（`closeThen` 包装，`ChatWorkbenchControls.kt:32-118`）；无长按/滑动（除 sheet 下拉）。 |
| 5 | 行为 | 动作路由：运行模式/审批模式 `RunModeSheet`/`ApprovalModeSheet`；分支 `BranchBrowserSheet`；运行时间线 `RuntimeTimelineSheet`；大纲 `ChatOutlineSheet`；记忆 `SessionMemorySheet`；思考强度 `ReasoningEffortSlider`（`ChatScreen.kt:594-610`）；导出 Markdown → `cacheDir/exports` + FileProvider 分享（`ChatScreen.kt:236`）。**入口去重**：`pinned` 里的动作不出现在 sheet（`ChatTopBar.kt:120`、`ChatWorkbenchControls.kt:86-110` 的 `hideXxx`）。 |
| 6 | 视觉状态 | 高亮项：`browserHighlight`/`repositoryHighlight`/`toolchainHighlight` 由顶栏传入；运行时时间线按事件类型着色（`eventVisual`，`ChatWorkbenchPanels.kt:1233-1304`）。 |
| 7 | 布局占位与展开形态 | 形态 **C**（sheet）。**`PlainBackdrop` 变体无背景遮罩**：点外部不关、必须点关闭或下拉（`ChatTopBar.kt:146-151`）。 |
| 8 | 滚动语义 | sheet 内容 `rememberScrollState` + `scrollState`（`ChatTopBar.kt:150`）；`RuntimeTimelineSheet` 内按轮次分组的 LazyColumn（`ChatWorkbenchPanels.kt:500-711`）。 |
| 9 | 动效 | sheet 入场；无内部动画。 |
| 10 | 边界态 | ① 入口全被钉选：sheet 只剩少数项；② 空记忆/空事件：分区显示空态（未逐条核实，E2）；③ 导出失败：Toast（`ChatExportMarkdown.kt`）；④ 长事件列表：按轮次折叠（`CollapsibleRuntimeSectionCard`）。 |
| 11 | 无障碍 | 每行有文字标签；图标为辅助。 |
| 12 | 依据 | `E0` `ChatTopBar.kt:130-178`；`E0` `ChatWorkbenchControls.kt:28-244`；`E0` `ChatWorkbenchPanels.kt:500-1304`；`E0` `ChatScreen.kt:577-610,236`；`E0` `ChatScrollToBottomButton.kt`（不同文件，此处不引）；`E1` `docs/ARCHITECTURE_RULES.md:52`（"工作台控制按需展开"）。 |
| 13 | 反证 + 验收 | **反证**：若把 10+ 个入口全铺在顶栏（拥挤）或全塞进一个"设置"页（深），日常操作路径变长；"钉选 + 抽屉"把高频留在一级、低频沉到二级，代价是**入口位置不稳定**（用户需要记"我把它钉哪了"）。若用有遮罩的 sheet，"更多"里再叠一层会连遮罩一起叠出黑压压一片，所以选无遮罩变体。**验收**：`TX-WORKBENCH-01` 钉选/取消钉选后入口不重复（真机）；`TX-WORKBENCH-02` sheet 内动作执行后自动关闭（真机）；`TX-WORKBENCH-03` 无遮罩变体点外部不关闭、点关闭/下拉才关（真机）。 |

#### `chat.round.collapse`（轮次折叠条 + 分帧揭示）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.round.collapse`。所属：聊天面板 → 消息流（`CollapseButtonItem` + `RoundCollapseButton` + `RoundRevealState`）。出现条件：**历史轮次中间过程自动折叠默认关闭**（`collapseEnabled=false`，`RoundCollapse.kt:77-91`）；开启后每轮插一个折叠条，`hiddenItemCount` = 该轮被隐藏的过程项数。 |
| 2 | 网格与定位 | 折叠条是一个列表项（`CollapseButtonItem`，`stableKey = collapse_btn_<roundKey>`，`RoundCollapse.kt:47-59`）；按钮为 `RoundCollapseButton`（全宽行，含隐藏数文案）。 |
| 3 | 命中区 | 整行可点（`RoundCollapseButton`）；`hiddenItemCount` 文案不可点。 |
| 4 | 手势 | 单击 = 展开（分帧揭示）/ 收起（立即）。 |
| 5 | 行为 | 点击 → `reveal.onToggled(item, wasExpanded, enabled)`：若"展开且隐藏段 > 3 条"→ 启动分帧揭示（每帧 3 条）；否则立即生效（`ChatRoundReveal.kt:56-63`）；同时写 `expandedOverrides[roundKey]`（`ChatMessageList.kt:183-192`）。 |
| 6 | 视觉状态 | 展开/收起两态（`isExpanded`）；无动画（`start/stop` 直接改 limit）。 |
| 7 | 布局占位与展开形态 | **分帧展开（第四形态）**：不是一次性长高，而是每帧插入 3 条，直到 `total`（`ChatRoundReveal.kt:45-54`）。兄弟位移：逐帧推动（视觉上是"内容一帧帧冒出来"）。 |
| 8 | 滚动语义 | 随外层会话滚；**分帧期间不主动滚动**（没有 `scrollToItem`），用户可能看到下方内容涌入。 |
| 9 | 动效 | 无高度动画（靠分帧）；`limit` 更新触发重组。 |
| 10 | 边界态 | ① 隐藏段 ≤3：不分帧直接全出；② 揭示中切换轮次：`LaunchedEffect(state.roundKey)` 自动取消重启（`:70-78`）；③ 折叠关闭（默认）：完全无折叠条、投影不读 `toolResults`（`:93`）。 |
| 11 | 无障碍 | 折叠条是否有 `stateDescription` 未查证（E2）。 |
| 12 | 依据 | `E0` `feature/chat/.../ChatRoundReveal.kt:1-100`；`E0` `RoundCollapse.kt:47-59,77-91,128-184,272-292`；`E0` `ChatMessageList.kt:183-192`；`E0` `ChatRoundCollapseButton.kt:1-61`。 |
| 13 | 反证 + 验收 | **反证**：若一次把 20+ 张工具卡插入列表，同一帧完成组合 → 长轮次展开必掉帧（作者注释原话）；若做成高度动画同样要逐帧测量长内容。分帧把单帧组合量压到常数，代价是"展开过程持续若干帧、不是瞬间"。**验收**：`TX-COLLAPSE-01` 200 项隐藏轮次展开不丢帧（设备 profiler）；`TX-COLLAPSE-02` 揭示中切换会话不残留半展开（真机）；`TX-COLLAPSE-03` 关闭折叠开关时无折叠条且滚动位置一致（真机）。 |

### 2.4 输入区（composer）

#### `composer.input`（输入胶囊：状态机 / @提及 / 命令 / 附件挂点）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.input`。所属：聊天面板底部（`ChatComposer`，`ChatPaneContent.kt:258-298`）。出现条件：聊天页始终存在（初始化中也渲染，只是禁发）；虚拟屏全屏时不显示。 |
| 2 | 网格与定位 | 胶囊 `Surface(shape = extraLargeIncreased(20dp), color = chatBackdropSurface(surfaceContainer), border = running ? 1dp primary@0.5 : null)`，`fillMaxWidth().padding(vertical=4dp)` + 软阴影（`ChatComposer.kt:160-166`）；内部 `Column(padding 8dp/6dp, spacedBy 4dp)`：附件行 → 能力标签 LazyRow → 文本框 → `FlowRow` 动作行（`:167-370`）；整体 `windowInsetsPadding(chatBottomInsets())`（navigationBars ∪ ime，`ChatPaneContent.kt:223`、`ChatScreen.kt:85`）。 |
| 3 | 命中区 | 左侧 3 个 40dp 图标钮（+ / 模型 / 审批）；右侧发送 40dp / 停止 40dp；能力标签 `Surface.clickable` + `heightIn(min=32dp)`（`:210,252`）—— **32dp 命中区**（小于 44dp，Q-TX-02）；文本框占满中间。 |
| 4 | 手势 | 文本输入（`ImeAction.Default`，支持换行）；长按粘贴长文本 → 转附件（`contentReceiver(pasteLongTextReceiver)`，`:314`）；能力标签单击 = 移除/取消钉选（`:201-207`）；发送钮长按 = "发送但不等待回答"（`:365`）。 |
| 5 | 行为 | 状态机：`initializing` → 禁发（`canSend=false`）；`running` → 显示"停止"+"插入消息"（发送钮按 `sendMode` 着色）；`!running` → 只有发送。发送 `doSend` 校验 `!initializing && (input.isNotBlank() \|\| attachments.isNotEmpty())`，成功后 `delay(60)` 贴底（`:113-122`）。@提及：输入 `@` 弹 `MentionPopup`（仅光标在末尾且折叠时，`:133-138`）；删除一个 mention 会整段删除（`:277-303`）；`MentionVisualTransformation` 高亮（`:304-310`）。斜杠命令：`matchingCommands` 非空弹 `SlashCommandPopup`（`:126-131`）。 |
| 6 | 视觉状态 | running：边框 primary@0.5 + placeholder 换成"运行中"文案（`:165,274`）；停止钮 `errorContainer`；发送钮 STEER=agent 色 / NEXT_RUN=secondary（`:340-349`）；能力标签 skill=`primaryContainer`、其他=`tertiaryContainer`，钉选加"已固定"小徽标（`:193-236`）。 |
| 7 | 布局占位与展开形态 | **占位**（底部 Column 的一部分），高度经 `onSizeChanged` 反馈给列表 `bottomContentPadding`（`ChatPaneContent.kt:258,215`）—— 列表末尾留白等于输入区高度，保证最后一条不被遮。附件预览行在文本框**上方**（"避免被胶囊底栏或键盘区域遮挡"，`:171-175`）。 |
| 8 | 滚动语义 | 文本框内部 `verticalScroll`（miuix TextField 自带）；能力标签 `LazyRow` 横向滚动；附件预览行横向。键盘弹出只把输入区顶起（`imePadding`），列表是否贴底见 `chat.list.scroll-follow`。 |
| 9 | 动效 | 无自定义（胶囊背景/边框切换是条件渲染）；软阴影 `chatSoftDropShadow`。 |
| 10 | 边界态 | ① 文本空但附件非空：可发送（`:332`）；② 初始化中：禁发；③ 附件处理中：顶部显示 14dp 转圈 + "处理中"文案（`:140-159`）；④ 极大输入：长文本走附件（`addTextAttachment`）；⑤ 多语言/大字号：`FlowRow` 自动换行（`:318-324`）。 |
| 11 | 无障碍 | 3 个图标钮都有 `contentDescription`（`ComposerQuickActions.kt:32,39,60`）；发送/停止有（`ComposerSendButton.kt:45,58`）；文本框 placeholder 随状态切换（读屏可读）；能力标签是 `Surface+Text`，**无 role**（E0 未见）。 |
| 12 | 依据 | `E0` `feature/chat/.../ChatComposer.kt:54-386`；`E0` `ComposerQuickActions.kt:19-65`；`E0` `ChatMentionText.kt`、`MentionItem.kt`、`SlashCommands.kt`、`PasteLongTextReceiver.kt`；`E0` `ChatPaneContent.kt:218-298`；`E1` `docs/ARCHITECTURE_RULES.md:47`（"输入区统一一体化圆角组件，保留附件、@成员以及执行中追加消息和停止操作"）。 |
| 13 | 反证 + 验收 | **反证**：若把"运行中"和"空闲"做成两个输入框（隐藏/显示），切换时焦点与草稿会丢；用一个胶囊 + 内部状态切换，草稿始终在。若长按粘贴不转附件，20 万字符的日志直接进输入框会让测量卡顿。**验收**：`TX-COMPOSER-01` 运行中草稿保留、可插入消息（真机）；`TX-COMPOSER-02` `@` 提及弹出/插入/整段删除（真机 4 步）；`TX-COMPOSER-03` 键盘弹出时输入区不被遮挡、最后一条消息可见（真机两种跟随时态）。 |

#### `composer.send`（发送 / 停止 / 长按不等待）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.send`。所属：输入胶囊右侧（`ComposerSendButton` + `ComposerStopButton`）。出现条件：空闲 1 个发送；运行中"有内容可插入"时 发送+停止 两个、否则只有停止（`ChatComposer.kt:333-368`）。 |
| 2 | 网格与定位 | `size(ComposerActionSize=40dp)` 圆形实心钮，图标 20dp；与 `ContextUsageRing`、停止钮 `spacedBy(6dp)`（`:334-368`）。 |
| 3 | 命中区 | **视觉 40dp = 命中 40dp**（明确不加 Material 48dp，`ComposerSendButton.kt:20-24`）。 |
| 4 | 手势 | 单击 = 发送（运行中则插入/steer）；长按发送 = `onSendWithoutAnswer`（`detectTapGestures(onLongPress)`，`:34-38` + `ChatComposer.kt:365`）；无双击。 |
| 5 | 行为 | 长按检测用 `rememberUpdatedState` 包住回调，避免流式重组重启检测器打断长按（`ComposerSendButton.kt:34-38` 注释）；`enabled = canSend`；幂等由 `doSend` 的 `initializing` 守卫保证。 |
| 6 | 视觉状态 | 空闲：`primary/onPrimary`；运行中 STEER：`agent/onAgent`、NEXT_RUN：`secondary/onSecondary`（`ChatComposer.kt:340-349`）；停止：`errorContainer/onErrorContainer`（`:56-57`）；禁用：miuix 自动灰化。 |
| 7 | 布局占位与展开形态 | 不适用。 |
| 8 | 滚动语义 | 不适用。 |
| 9 | 动效 | 按压反馈 miuix 默认。 |
| 10 | 边界态 | ① 初始化：禁用（`canSend`）；② 空输入：禁用；③ 运行中且无内容：只有停止钮，不显示禁用发送（`:339-350`）；④ 大字号：`FlowRow` 换行后按钮仍右对齐。 |
| 11 | 无障碍 | `contentDescription = chat_send` / `chat_stop_generation`（`:45,58`）。 |
| 12 | 依据 | `E0` `feature/chat/.../ComposerSendButton.kt:20-62`；`E0` `ChatComposer.kt:113-122,332-368`；`E0` `ComposerQuickActions.kt:28-64`（同尺寸族）。 |
| 13 | 反证 + 验收 | **反证**：若长按发送不是"不等待回答"而是弹菜单，用户会误以为长按是"更多选项"；若运行中直接把发送钮换成停止钮（唯一动作），用户无法"补一句"引导当前任务（只能等结束）。**验收**：`TX-SEND-01` 空闲/运行中/停止三态按钮出现规则正确（真机三态）；`TX-SEND-02` 长按发送不触发普通发送、只发一次（真机 + 日志计数）；`TX-SEND-03` 连点发送不重复提交（真机暴力点击）。 |

#### `composer.attach-sheet`（附件抽屉：照片 / 文件 / 扩展 / 草图）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.attach-sheet`。所属：输入区"+"打开的 `ComposerSheets` → `ChatAttachSheet`。出现条件：点"+"；同屏 1 个。 |
| 2 | 网格与定位 | 底部抽屉（`ChatAttachSheet.kt:1-204`）；网格 + 扩展入口 + 紧凑行（E1 `ComposerQuickActions.kt:19` 注释"the '+' opens the attach sheet (grid + extensions + compact)"）；附件预览在输入胶囊**上方** `AttachmentPreviewRow`（`ChatComposer.kt:171-175`）。 |
| 3 | 命中区 | 网格项（照片缩略）；扩展入口行；`ChatAttachment` 缩略图删除钮（`ChatAttachment.kt:372` 用 48dp 助手）。 |
| 4 | 手势 | 单击选照片/文件/扩展；长按预览（E2 未核实）；草图抽屉独立（`ChatSketchSheet`）。 |
| 5 | 行为 | 选完 → `onAttachmentsPicked(uris, fromGallery)` → VM 处理（复制/压缩/编码）→ `attachmentsProcessing` 期间输入区顶部转圈（`ChatComposer.kt:140-159`）；移除 → `onRemoveAttachment`。照片走系统 Photo Picker（`ActivityResultContracts` 未在 chat 内直查，E2）。 |
| 6 | 视觉状态 | 处理中：转圈 + 文案；附件缩略图 48dp 级圆角。 |
| 7 | 布局占位与展开形态 | sheet 形态 **C**；附件预览行在输入区内**占位**（改变输入胶囊高度 → 列表底部留白随之变化）。 |
| 8 | 滚动语义 | 抽屉内网格滚动；预览行横向滚动。 |
| 9 | 动效 | sheet 入场；处理中指示无动画（转圈）。 |
| 10 | 边界态 | ① 处理失败：VM notice → Toast（`ChatScreen.kt:300-310`）；② 超大文件：压缩/落盘策略在 VM；③ 二进制文件：`BinaryFileSniffer` 判定不当文本处理（E1 `ARCHITECTURE_RULES.md:102`）；④ 权限拒绝：走 `permissionRequests` Snackbar（`ChatScreen.kt:277-298`）。 |
| 11 | 无障碍 | 缩略图有 `contentDescription`（`ImageThumbnail`）；删除钮需有描述（未逐条核实）。 |
| 12 | 依据 | `E0` `feature/chat/.../ChatAttachSheet.kt:1-204`；`E0` `ChatComposer.kt:140-175,374-385`；`E0` `ChatAttachment.kt:1-395`；`E0` `ComposerSheets.kt:1-79`、`ChatSketchSheet.kt:1-144`；`E1` `README.md`（附件/备份）、`docs/ARCHITECTURE_RULES.md:102`。 |
| 13 | 反证 + 验收 | **反证**：若附件预览放进胶囊底部动作行，选 3 张图会把它挤成两行、发送钮移位（误触）；放上方则只增高不移动按钮。若把二进制文件当文本读，回写会损坏产物。**验收**：`TX-ATTACH-01` 选 3 张图后发送钮位置不变（截图）；`TX-ATTACH-02` 处理中不可发送且显示进度（真机）；`TX-ATTACH-03` 移除单个附件不误删其他（真机）。 |

#### `composer.queue`（排队提示条：编辑 / 移除 / 转 steer）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.queue`。所属：输入区上方独立悬浮条（`QueuedPromptStack`，`ChatPaneContent.kt:251-257`）。出现条件：`queuedPrompts` 非空（运行中追加的消息进队列）。 |
| 2 | 网格与定位 | 与 Plan 同级、在输入胶囊之上，`padding(vertical=4dp)`；"不计入输入框渐隐高度"（`ChatPaneContent.kt:251-252` 注释）。 |
| 3 | 命中区 | 每条的编辑/移除/转 steer 按钮（`QueuedPromptStack`）；行高未逐一核实（E2）。 |
| 4 | 手势 | 单击编辑 → `QueuedPromptEditDialog`；单击移除；单击"转为插入" → `convertQueuedPromptToSteer`。 |
| 5 | 行为 | 队列由 VM 维护；编辑对话框是独立 `Dialog`（`QueuedPromptEditDialog.kt`）；`sendMode` 决定新消息是排队还是 steer（`ChatComposer.kt:340-349`）。 |
| 6 | 视觉状态 | 排队条与 plan 条同级样式（未逐条核实，E2）。 |
| 7 | 布局占位与展开形态 | **占位**（悬浮条位于输入区上方、计入底部 chrome 高度的一部分？注释说"不计入输入框渐隐高度"，但在 `bottomChromePx` 的 Column 内 ⇒ 实际参与 `bottomContentPadding`）。 |
| 8 | 滚动语义 | 多条队列时横向/纵向滚动（未核实，E2）。 |
| 9 | 动效 | 无（条件渲染）。 |
| 10 | 边界态 | ① 队列很多：超出可视区的处理未核实（E2）；② 编辑中取消：`QueuedPromptEditor` 回滚。 |
| 11 | 无障碍 | 未核实（E2）。 |
| 12 | 依据 | `E0` `feature/chat/.../QueuedPromptStack.kt:1-132`；`E0` `QueuedPromptEditor.kt:1-83`、`QueuedPromptEditDialog.kt:1-51`；`E0` `ChatPaneContent.kt:251-257`；`E0` `ChatScreen.kt:133,414-417`；`E1` `docs/10-panels.md` 对应的 myco `composer`（"🟡 有，缺排队/命令/触发词"）。 |
| 13 | 反证 + 验收 | **反证**：若运行中不允许追加消息，用户想纠正 agent 的方向只能等整轮结束；若追加消息直接进队列但不显示，用户不知道"我刚才那句话到底发没发"。**验收**：`TX-QUEUE-01` 运行中发消息进队列并显示条数（真机）；`TX-QUEUE-02` 编辑/移除后队列与实际发送一致（真机）；`TX-QUEUE-03` 转 steer 后该条从队列消失且被插入当前运行（真机 + 日志）。 |

### 2.5 侧栏（sidebar）

#### `sidebar.drawer.sessions`（会话抽屉：搜索 / 分区 / 新建 / 底部导航）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.drawer.sessions`。所属：全局抽屉内容（`SessionsSideDrawer`，由 `ChatNavigationDrawer` 提供）。出现条件：抽屉可见（三态之一）；抽屉内 1 个实例。 |
| 2 | 网格与定位 | 顶：`SessionDrawerSearch(query, onNew, onCollapse)`（`SessionsSideDrawer.kt:138`）；中：分区列表 `RecentSection`/`PinnedSessionsSection`/项目/Bot/远程服务（`:324-397`）；底：`SessionDrawerFooter(sessions.size, onSettings, navigationContent)`（`:272`）固定（E1 `UI_GUIDELINES.md:48` "底部设置入口保持固定"）。 |
| 3 | 命中区 | 搜索框、新建钮（`SessionDrawerChrome` 内）、每行会话（见 `sidebar.session-row`）、底部导航瓦片（`AppNavigationSection` 2 列 + 全宽终端）。 |
| 4 | 手势 | 抽屉开合手势见 `base.layout.drawer`；行内单击切换会话、长按行菜单（`SessionDrawerItem`）；"**抽屉不放关闭按钮**，靠滑动或点遮罩关闭"（E1 `docs/ARCHITECTURE_RULES.md:52`）。 |
| 5 | 行为 | 切会话：`switchSession(id) + navigateMain(Agent) + onDismiss()`（`ChatNavigationDrawer.kt:115`）；新建：`onNewSession` → `NewSessionDialog`（`:174-185`）；在项目内新建：`createSession("", project.linuxPath, type)`（`:119-122`）；在远程服务器新建：需 `profilePresent`，否则 `showNotice`（`:123-137`）；删除/重命名：`viewModel.deleteSession/renameSession`（`:157-158`）。 |
| 6 | 视觉状态 | 当前会话高亮（`currentSessionId` 对比，`:108`）；运行态 `sessionRunStates`（`:112`，行内显示运行指示）；分区标题 `RuntimeSectionTitle`。 |
| 7 | 布局占位与展开形态 | 抽屉本体形态随断点（C 浮层 / 停靠占位）；**分区不折叠**（E2：未见可折叠分区，目录里的"最近会话按更新时间排序，不再按日期分组"是 E1 `ARCHITECTURE_RULES.md:51`）。 |
| 8 | 滚动语义 | 单 LazyColumn 纵向滚；分区标题随列表滚（非 sticky，E2）；远程项目监视 `setWatchedRemoteProjects` 在抽屉不可见时清空（`ChatNavigationDrawer.kt:66-69`）。 |
| 9 | 动效 | 无自定义（列表项增删）。 |
| 10 | 边界态 | ① 无会话：显示空态（未核实文案，E2）；② 隐藏 Bot 会话：`hiddenBotSessionIds` 过滤（`:104-107`）；③ 远程服务器离线：`profilePresent=false` 时点击给提示而不是静默（`:123-137`）；④ 多分区都空：只剩底部导航。 |
| 11 | 无障碍 | 每行有标题文本；底部导航瓦片有 `icon + label`。 |
| 12 | 依据 | `E0` `feature/chat/.../SessionsSideDrawer.kt:45-386`；`E0` `ChatNavigationDrawer.kt:71-186`；`E0` `SessionDrawerChrome.kt`、`SessionDrawerItem.kt`、`SessionsDrawerPartitions.kt`、`ProjectSessionsSection.kt`、`BotSessionsSection.kt`、`RemoteServersSection.kt`、`NewSessionDialog.kt`；`E1` `docs/ARCHITECTURE_RULES.md:51-52`。 |
| 13 | 反证 + 验收 | **反证**：若把搜索放在抽屉顶部但不可见时仍订阅远程项目，会在后台持续轮询（耗电）—— 太墟用 `sidebarShowing` 门控订阅。若抽屉放关闭按钮，会与"滑动关闭"形成两个关闭语义，且按钮占标题栏空间（他们的取舍：不放）。**验收**：`TX-DRAWER-SESS-01` 抽屉不可见时远程轮询停止（日志/网络）；`TX-DRAWER-SESS-02` 新建会话后自动进入智枢并关闭抽屉（真机）；`TX-DRAWER-SESS-03` 分区顺序（项目/Bot/最近）与底部固定设置入口（真机 + 截图）。 |

#### `sidebar.session-row`（会话行）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.session-row`。所属：会话抽屉列表行（`SessionDrawerItem`）。出现条件：每条会话一行；分 pinned/最近/项目/Bot 分区各出现一次（不重复）。 |
| 2 | 网格与定位 | 整行：标题单行 ellipsis + 右侧运行/更多；**"会话项只显示标题，不放左侧图标"**（E1 `docs/ARCHITECTURE_RULES.md:51`）。 |
| 3 | 命中区 | 整行可点（切换会话）；右侧"更多"是 40dp 图标钮（未逐条核实，E2）。 |
| 4 | 手势 | 单击切换；长按/更多 → 行菜单（重命名/删除/钉选/隐藏 bot 会话）；拖动排序：未实现（E2）。 |
| 5 | 行为 | 切换 → `switchSession`；菜单 → `setSessionPinned`/`deleteSession`/`renameSession`/`setBotSessionHidden`（`ChatNavigationDrawer.kt:105-158`）。 |
| 6 | 视觉状态 | 当前会话高亮 + 运行态指示（`sessionRunStates`）；未读/新消息指示未核实（E2）。 |
| 7 | 布局占位与展开形态 | 不展开；行高固定（miuix 行密度）。 |
| 8 | 滚动语义 | 随抽屉列表滚；行内无滚动。 |
| 9 | 动效 | 无（删除/新增是列表 diff）。 |
| 10 | 边界态 | ① 超长标题：ellipsis（E1 `ARCHITECTURE_RULES.md:58`）；② 运行中会话：显示运行态；③ 被隐藏的 Bot 会话：不在列表。 |
| 11 | 无障碍 | 行有标题文本；运行态是否有 `stateDescription` 未核实（E2）。 |
| 12 | 依据 | `E0` `feature/chat/.../SessionDrawerItem.kt:1-109`；`E0` `SessionsSideDrawer.kt:324-397`；`E0` `ChatNavigationDrawer.kt:105-158`；`E1` `docs/ARCHITECTURE_RULES.md:51,58`。 |
| 13 | 反证 + 验收 | **反证**：若每行带会话头像/图标，抽屉每屏少了 20% 的会话数，而会话的区分信息 90% 在标题上（他们的取舍：只留标题）。**验收**：`TX-SESSROW-01` 超长标题单行 ellipsis 且不挤出"更多"（真机构造）；`TX-SESSROW-02` 运行中会话有可辨指示（真机）。 |

### 2.6 设置面板

#### `settings.shell`（设置首页：大标题 + 分类卡分组）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.shell`。所属：设置面板（`SettingsScreen` + `PersonalizationScaffold`）。出现条件：从抽屉底部"设置"或顶栏进入；`SettingsDestination` 在栈中 1 个。 |
| 2 | 网格与定位 | `PersonalizationScaffold` = `RuntimeSettingsScaffold(title, onBack, itemSpacing = SettingsDensity.sectionGap)`（`PersonalizationComponents.kt:18-20`）→ 内部 miuix `Scaffold` + 大标题 `RuntimeTopBar(scrollBehavior)` + `LazyColumn`（`RuntimeSettingsScaffold.kt:43-81`）；内容水平边距 `runtimeContentPadding`（默认 12dp）、列表项间距 `RuntimeSpacing.cardGap`（12dp）。首页结构：搜索入口 → 外观卡 → 「环境」组（智能体 / Linux）→ 「功能与数据」组（插件中心 / 本地备份）→ 「应用」组（诊断 / 关于 / 使用须知）（`SettingsScreen.kt:34-77`）。 |
| 3 | 命中区 | 搜索入口整行；`SettingsCategoryCard` → `PreferenceSurface{ SettingsRow }` 整行可点（`:89-91`）；行高 miuix 默认。 |
| 4 | 手势 | 单击进子页；下拉刷新手势仅 `onRefresh != null` 时挂 `RuntimePullToRefresh`（`RuntimeSettingsScaffold.kt:83-94`，设置首页未用）；滚动时大标题折叠/展开（`exitUntilCollapsedScrollBehavior`）。 |
| 5 | 行为 | 每个分类卡 = 一个导航目的地（`onOpenAgentEco`/`onOpenLinuxEnv`/`onOpenAppearance`/`onOpenSystemDev`/`onOpenAboutCommunity`/`onOpenSearch`/`onOpenPluginCenter`）；"使用须知"打开 `RuntimeUsageNoticeDialog`（`SettingsScreen.kt:31-32,72-74`）。 |
| 6 | 视觉状态 | 分组卡首/中/末段圆角由 `PreferenceSurface(first,last)` 拼（16dp 外边 + 组内直角）；`SettingsRow(tonalIcon=true)` 图标带色调底。 |
| 7 | 布局占位与展开形态 | 不展开（子页用导航栈）。**大标题列表**占位：首个 item 前 `RuntimeSpacing.pageTop` 由 Scaffold 补（E1 `UI_GUIDELINES.md:30`）。 |
| 8 | 滚动语义 | 单 LazyColumn；`runtimeListScrollEffects(nestedScrollConnection)` = `scrollEndHaptic → overScrollVertical → nestedScroll(topBar)`（顺序不可换，见 §0-11）；顶部 inset 并进 `contentPadding` 而不是整包 padding，保证内容能滚到毛玻璃顶栏后方（E1 `UI_GUIDELINES.md:37`）。 |
| 9 | 动效 | `exitUntilCollapsedScrollBehavior` 大标题折叠；`RuntimeAdaptiveMotion` 用于宽度。 |
| 10 | 边界态 | ① 宽屏：内容 max 840dp 居中、LazyColumn 保持全宽（E1 `UI_GUIDELINES.md:47`）；② 横屏缺口：水平 `displayCutout ∪ navigationBars`（E1 `:50`）；③ 键盘：`imePadding()`（`RuntimeSettingsScaffold.kt:60`）；④ 底部 inset 并入 `contentPadding.bottom`（E1 `:29`）。 |
| 11 | 无障碍 | 大标题顶栏返回钮有描述；分组标题 `RuntimeSectionTitle` 是文本；行按钮语义由 miuix preference 提供。 |
| 12 | 依据 | `E0` `feature/settings/.../SettingsScreen.kt:19-92`；`E0` `PersonalizationComponents.kt:18-28`；`E0` `feature/components/.../RuntimeSettingsScaffold.kt:25-109`；`E1` `docs/UI_GUIDELINES.md:26-30,44-51`。 |
| 13 | 反证 + 验收 | **反证**：若设置首页直接用"一行一个二级入口"的平铺列表（几十行），用户找不到东西且首屏全是文字；太墟用 3 组 + 5 张分类卡把 30+ 子页压到一屏可扫。若滚动根整包 `padding(scaffold)`，毛玻璃顶栏采不到列表内容（变成死白条）。**验收**：`TX-SETTINGS-01` 首页一屏内可见全部分组与 5 张卡（真机 + 截图）；`TX-SETTINGS-02` 内容滚到顶栏后方、毛玻璃有内容透出（录屏）；`TX-SETTINGS-03` 宽屏 840dp 居中且两侧可滚（折叠屏）。 |

#### `settings.search`（全局设置搜索：注册表 + 热门标签 + 结果跳转）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.search`。所属：设置面板子页（`SettingsSearchScreen` + `SettingsSearchRegistry`）。出现条件：设置首页搜索入口打开；1 个。 |
| 2 | 网格与定位 | 顶栏 `RuntimeTopBar(title="搜索功能与设置", statusText="全局核心能力一键直达")`；下方 `RuntimeExpandableSearchBar`（固定顶栏下、结果列表不再叠进顶栏）；空查询显示"热门快捷检索" FlowRow 标签（`SettingsSearchScreen.kt:77-150`）；结果列表带命中高亮（`buildAnnotatedString` + SpanStyle）。 |
| 3 | 命中区 | 搜索框；热门标签 `Surface.clickable`（`:134-140`，高度约 28dp，小于 44dp）；结果行整行。 |
| 4 | 手势 | 输入过滤（`SettingsSearchRegistry.search(query)`，`:64-66`）；点热门标签填入查询；点结果跳转；进入页面自动聚焦（`focusRequester.requestFocus()`，`:68-71`）。 |
| 5 | 行为 | 结果跳转是**大 when 表**：`SettingsSearchTarget` → 具体目的地/多级 push（`TaiXuNavHost.kt:313-382`，约 40 个 target）；有些 target 需要"先切根页再压两层"（如 `WORKSHOP_ENVIRONMENT`：`navigateMain(Workspace) + push WorkshopSettings + push WorkshopEnvironment`，`:352-356`）。 |
| 6 | 视觉状态 | 结果命中词高亮；空态/无结果态（未逐条核实，E2）。 |
| 7 | 布局占位与展开形态 | 搜索框可展开（`searchExpanded`），展开后结果落在下方；无折叠面板。 |
| 8 | 滚动语义 | 结果 LazyColumn 自身滚；键盘 `imePadding()`（`:90`）。 |
| 9 | 动效 | SearchBar 展开动画由组件提供（未核实曲线）。 |
| 10 | 边界态 | ① 空查询：只显示热门标签；② 无结果：空态；③ 深链目标在别的根页：先 `navigateMain` 再 push（`:348-365`）；④ 多语言/拼音：注册表含拼音关键词（`docs/ARCHITECTURE_RULES.md:51` 提到注册表；拼音检索在 `SettingsSearchRegistry.kt`）。 |
| 11 | 无障碍 | 搜索框有 placeholder；结果行有文本；高亮不改变语义。 |
| 12 | 依据 | `E0` `feature/settings/.../search/SettingsSearchScreen.kt:53-455`；`E0` `SettingsSearchRegistry.kt:1-910`（约 40 个 target）；`E0` `SettingsSearchItem.kt`；`E0` `TaiXuNavHost.kt:310-386`；`E0` `feature/components/.../RuntimeSearchBar.kt`、`RuntimeSearchEntry.kt`。 |
| 13 | 反证 + 验收 | **反证**：若不给搜索，30+ 子页只能靠记忆层级（"智能体→子代理→技能→权限"四层才能到达一个开关）；若搜索结果直接改页面而不进栈，用户按返回会回到搜索页并丢失上下文 —— 太墟让结果走正常入栈，返回逐层回退。**验收**：`TX-SEARCH-01` 用中文/英文/拼音三路都能命中（真机）；`TX-SEARCH-02` 结果跳转后按返回逐层回退（真机）；`TX-SEARCH-03` 空查询显示热门标签且点击可查（真机）。 |

#### `settings.appearance`（外观与终端：即时生效 / 松手落盘）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.appearance`。所属：设置 → 外观（`AppearanceSettingsScreen`）。出现条件：从设置首页"外观与终端"卡进入；1 个。 |
| 2 | 网格与定位 | `PersonalizationScaffold` 列表：颜色模式（3 单选）→ 聊天身份卡 → 聊天背景卡 → 顶栏入口卡 → 工具状态样式卡 → 界面缩放滑杆 → 语言（3 单选）→ 终端外观（色板 4 个 chip + 字号滑杆 + 触感开关）→ 终端预览卡（`AppearanceSettingsScreen.kt:39-129`）。 |
| 3 | 命中区 | 单选整行（`PreferenceSurface` + `ThemeOptionRow`）；滑杆（miuix slider）；终端色板 `TerminalThemeChip` 等分 `weight(1f)`（`:104-113`）；开关整行。 |
| 4 | 手势 | 单选单击即生效；滑杆拖动预览、松手落盘；色板 chip 单击即生效。 |
| 5 | 行为 | **即时**：`setThemeMode(mode)`（`:52`）、`setTerminalColorScheme`（`:106-112`）、`setTerminalHapticsEnabled`（`:125`）、语言 `AppCompatDelegate.setApplicationLocales(...)`（`:83-88`）。**松手落盘**：`setAppFontScale(pageScale)`（`:68`）、`setTerminalFontSize(...)`（`:120`）。本地态 `terminalSize`/`pageScale` 是 `rememberSaveable` 并在外部值变化时同步（`:35-38`）。 |
| 6 | 视觉状态 | 选中单选高亮；滑杆值标签实时（"120%"、"14 sp"）；终端预览卡随色板/字号变化（`TerminalPreviewCard`）。 |
| 7 | 布局占位与展开形态 | 不展开（除终端预览卡本身）。 |
| 8 | 滚动语义 | 设置列表滚；滑杆水平拖动正交。 |
| 9 | 动效 | 主题切换即时（无过渡动画，E0 未见）。 |
| 10 | 边界态 | ① 语言切换：`LocalConfiguration.current` 触发重组（`:32` 注释）；② 页面缩放 80%–130%、步进 5%（`:66-68`）；③ 终端字号 10–24sp（`:117-120`）；④ 头像裁切（`AvatarCropDialog`，1:1 可拖拽缩放）。 |
| 11 | 无障碍 | 主题/语言单选有 title+description 双行（读屏可读）；滑杆有 `contentDescription = title`（`PersonalizationComponents.kt:101-103` 的同一实现）。 |
| 12 | 依据 | `E0` `feature/settings/.../AppearanceSettingsScreen.kt:22-130`；`E0` `PersonalizationComponents.kt:79-110`；`E0` `ThemePalettePicker.kt`、`TerminalAppearancePreview.kt`、`ChatIdentitySettingsCard.kt`、`ChatBackgroundSettingsCard.kt`、`AvatarCropDialog.kt`；`E1` `docs/ARCHITECTURE_RULES.md:65`。 |
| 13 | 反证 + 验收 | **反证**：若主题模式也要"保存"才生效，用户改完得返回才知道效果，且中途退出全丢；若滑杆每帧落盘，拖动会卡（DataStore 写 + 全树重组）。**验收**：`TX-APPEAR-01` 三处即时项（主题/终端色板/语言）改完立刻生效（真机）；`TX-APPEAR-02` 缩放拖动不落盘、松手落盘（日志计数）；`TX-APPEAR-03` 页面缩放 130% 后设置列表不截断（真机）。 |

#### `settings.agent`（智能体设置：执行 / 子代理 / 技能 三类目）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.agent`。所属：设置 → 环境 → 智能体（`AgentEcoSettingsScreen` → `AgentSettingsScreen(category)`）。出现条件：`AgentSettingsCategory` 三值（EXECUTION/SUBAGENTS/SKILLS）各对应一个目的地（`TaiXuNavHost.kt:454-472`）；每屏 1 个。 |
| 2 | 网格与定位 | `AgentSettingsScreen` 1236 行：大标题 `RuntimeTopBar` + LazyColumn（`nestedScroll`，`:171`）；`AgentEcoSettingsScreen` 是 8 个二级入口的列表（模型档案/本地模型/执行/子代理/技能/MCP/快捷短语/统计，`TaiXuNavHost.kt:399-408`）。 |
| 3 | 命中区 | 偏好行整行；`onToggle` 开关；`TextButton` 编辑提示词（`AgentSettingsScreen.kt:991`，`minimumInteractiveComponentSize()` 于 1012 行处）；子代理/技能行的开关。 |
| 4 | 手势 | 开关切换即时；行单击进编辑对话框（子代理编辑/技能详情/插件详情）；长按无。 |
| 5 | 行为 | 即时落盘：`toggleSubagent(id, enabled)`（`:467`）、`toggleSkill`（`:580`）、`togglePlugin`（`:642`）；编辑走 `SubagentEditorDialog`/`AddSkillDialog`/`AgentPromptSheet`；不存在的模型/工具会给引导入口而不是静默失败。 |
| 6 | 视觉状态 | `AgentRoleCards` 角色卡（`:1-148`）带开关与 `RuntimeSwitch(onCheckedChange=onToggle)`（`:107`）；禁用行（未安装/不可用）`.clickable(enabled = enabled && !locked)`（`BundleComponentSetupDialog.kt:239` 同族）。 |
| 7 | 布局占位与展开形态 | 不展开；子代理/技能详情用 Dialog/Sheet（形态 C）。 |
| 8 | 滚动语义 | 设置列表滚；长表单（系统提示）在 Dialog 内滚（`ARCHITECTURE_RULES.md:45` 要求 Dialog 内容 `verticalScroll` + 按钮钉底）。 |
| 9 | 动效 | 无自定义。 |
| 10 | 边界态 | ① 未下载本地模型：给"去下载"入口；② 技能 JSON 非法：`SkillSecurityAuditDialog` 审计 + 错误；③ 子代理数量上限/预算：由 agent 策略层校验（未在 UI 层，E2）；④ 多语言：大量字符串走 `SettingsLocalization`。 |
| 11 | 无障碍 | 开关有 role（miuix）；"编辑提示词"是文字按钮。 |
| 12 | 依据 | `E0` `feature/settings/.../AgentSettingsScreen.kt:171,467,580,642,936-1181`；`E0` `AgentEcoSettingsScreen.kt:1-91`；`E0` `AgentRoleCards.kt:82-107`、`SubagentEditorDialog.kt:1-133`、`AddSkillDialog.kt:1-48`、`SkillSecurityAuditDialog.kt:1-328`；`E0` `TaiXuNavHost.kt:395-472`；`E1` `README.md`（子智能体协作、技能）。 |
| 13 | 反证 + 验收 | **反证**：若三类目（执行/子代理/技能）挤在一屏 60+ 行，用户改一个开关要滚 3 屏且不知道自己在哪个类目；拆成三个入口共享同一屏实现（`category` 参数）既保留分类又不复制代码。**验收**：`TX-AGENT-01` 三类目分别进入、标题正确、行集合互不越界（真机）；`TX-AGENT-02` 开关即时生效并持久（杀进程重进）；`TX-AGENT-03` 技能非法 JSON 有审计对话框而非崩溃（真机构造）。 |

#### `settings.models`（模型档案列表 + 模型编辑表单）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.models`。所属：设置 → 智能体 → 模型档案（`ModelProfilesScreen`）→ 模型编辑（`ModelEditorScreen`）。出现条件：列表页 1 个；编辑页 `ModelEditorDestination(modelId?)`（新建/编辑各 1 个实例）。 |
| 2 | 网格与定位 | 列表：`ModelProfilesContent` + `ModelProfileCard`（整卡选预设 + 操作区可换行，E1 `ARCHITECTURE_RULES.md:50`）；编辑：紧凑顶栏 + 可展开分段（`ModelEditorExpandableSection`）+ 表单字段（provider 选择、模型多选、baseUrl、API Key 列表、rpm、temperature、maxTokens、context、压缩参数、自定义 headers、能力开关…约 25 项，`ModelEditorScreen.kt:208-339`）。 |
| 3 | 命中区 | 卡片操作区按钮（换行布局）；表单控件 miuix；API Key 显示/隐藏（`revealedKeyIndices`）；`minimumInteractiveComponentSize()` 用于小操作（`:568,595`）。 |
| 4 | 手势 | 单击选模型/编辑；表单输入；展开分段；保存按钮提交。 |
| 5 | 行为 | **显式保存**：`save(...)` → `viewModel.saveModels(...)` → `saved==true` 才 `onSaved()` 回退（`ModelEditorScreen.kt:141-167`）；保存失败 → `profileSaveError` 流提示（`:98`）；导入 JSON 预览后"复制或更新"（E1 `README.md`"模型档案另支持 JSON 导入预览、复制或更新、导出与系统分享"）。所有字段 `rememberSaveable(modelId)`（`:214-339`），旋转不丢。 |
| 6 | 视觉状态 | 激活模型有"色面"表达（E1 `ARCHITECTURE_RULES.md:50`）；表单错误态（miuix TextField 内置 error）；保存中 `state.busy`。 |
| 7 | 布局占位与展开形态 | 编辑页分段可展开（`ModelEditorExpandableSection`）；**展开形态 = inline 长高**（不是 sheet）。 |
| 8 | 滚动语义 | 编辑页 LazyColumn + `imePadding`，键盘弹出仍可滚到保存（E1 `ARCHITECTURE_RULES.md:50`）；Dialog 内表单 `verticalScroll`。 |
| 9 | 动效 | 分段展开动画由组件（未核实）。 |
| 10 | 边界态 | ① 新建（`modelId=null`）：字段空、provider 默认；② API Key 多条：`keyList` + 批量导入对话框（`:252`）；③ 非法 URL（外部模型必须 HTTPS，本地模型允许 `http://127.0.0.1`，E1 `ARCHITECTURE_RULES.md:76`）；④ 保存冲突：错误流提示。 |
| 11 | 无障碍 | 表单字段有 label（miuix 浮动标签）；展开/收起分段是否有 state 语义未核实（E2）。 |
| 12 | 依据 | `E0` `feature/settings/.../ModelEditorScreen.kt:76,98,141-167,208-339`；`E0` `ModelProfilesScreen.kt:102`、`ModelProfileCard.kt`、`ModelProfilesContent.kt`；`E0` `ModelEditorCandidateList.kt`、`ModelEditorExpandableSection.kt`、`ModelEditorToggleRow.kt`、`ModelEditorImportDialogs.kt`、`ModelImportDialog.kt`、`ModelExportDialog.kt`、`LocalLlmScreen.kt`；`E1` `docs/ARCHITECTURE_RULES.md:50,74-77`。 |
| 13 | 反证 + 验收 | **反证**：模型配置是"一次改多项再测试"的场景，若即时生效（像外观页），用户改 baseUrl 一半就会触发重连/报错；所以必须显式保存 + 失败回滚。若把所有 25 个字段平铺，一屏放不下且找不到关键项 —— 分段折叠解决。**验收**：`TX-MODELS-01` 改 5 项后保存成功才回退（真机）；`TX-MODELS-02` 保存失败停留在页面并有错误（真机构造非法 URL）；`TX-MODELS-03` 旋转屏幕/键盘弹出后表单值不丢（真机）。 |

### 2.7 工作区（workspace）

#### `workspace.screen`（工坊首页：项目网格 + 更多菜单 + 构建横幅）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `workspace.screen`。所属：工坊根页（`WorkspaceScreen`）。出现条件：`WorkspaceDestination` 在栈顶（覆盖根页之一）；1 个。 |
| 2 | 网格与定位 | 顶栏 `RuntimeTopBar(title=工坊, statusText="进行中 N")` + 右侧 `WorkspaceMoreMenu`（新建/导入/模板/插件中心/工作流/工坊设置）（`WorkspaceScreen.kt:164-182`）；内容 `LazyVerticalGrid(GridCells.Adaptive(340.dp))`，卡片间距 `cardGap`（`:184-190`）；`fullWidthItem` 承载横幅/共享存储入口/分区标题（`:191-266`）。 |
| 3 | 命中区 | 顶栏按钮、"更多"菜单项、项目卡整卡与卡内动作（运行/终端/文件/工作流/删除/导出）、空态的两个按钮。 |
| 4 | 手势 | 单击；长按项目卡 → 项目菜单（`WorkspaceProjectMenu`，E2）；列表滚动。 |
| 5 | 行为 | 新建/导入/模板 → 向导（`workspace.wizard`）；项目卡动作 → Explorer/终端/Agent/工作流/构建/导出/删除；后台构建横幅可点开构建对话框（`:198-208`）；首次进入有聚光灯引导（`SpotlightGuideOverlay`，`:313-320`）。 |
| 6 | 视觉状态 | 构建中：横幅 + 项目卡 `isBuilding`；`busy` 时操作禁用；`NoticeBanner` 成功/错误色。 |
| 7 | 布局占位与展开形态 | 构建横幅与共享存储入口**占位**（fullWidthItem）；构建日志对话框是**可最小化**的弹窗（"支持后台运行与随时最小化"，`:323-329`）。 |
| 8 | 滚动语义 | 单 LazyVerticalGrid 滚；宽屏 `GridCells.Adaptive(340dp)` 自动列数（E1 `ARCHITECTURE_RULES.md:57`）。 |
| 9 | 动效 | 聚光灯引导遮罩（`SpotlightGuide`）；卡片增删无动画（E0 未见）。 |
| 10 | 边界态 | ① 无项目：`WorkspaceEmptyState`（新建/导入两入口）；② 运行时未就绪：`runtimeReady` 门控；③ 构建中：卡片显示进度、操作禁用；④ 远程工作区：`RemoteWorkspaceScreen` 另一实现；⑤ 导入进度：`WorkspaceImportProgress`。 |
| 11 | 无障碍 | 卡片动作有 label；横幅文本可读。 |
| 12 | 依据 | `E0` `feature/workspace/.../WorkspaceScreen.kt:67-458`；`E0` `WorkspaceProjectCard.kt:1-243`、`WorkspaceMoreMenu.kt:1-82`、`WorkspaceEmptyState.kt:1-56`、`WorkspaceProjectMenu.kt:1-53`；`E1` `README.md`（"创建空项目、导入本地 ZIP……克隆 GitHub 仓库"）、`docs/ARCHITECTURE_RULES.md:57`。 |
| 13 | 反证 + 验收 | **反证**：若把新建/导入/模板/插件/工作流/设置 6 个入口都摆成顶部一排按钮，360dp 屏会挤成图标海；收进"更多"菜单后首个入口（新建）反而多一次点击 —— 他们用空态页放大按钮兜住新手路径。**验收**：`TX-WORKSPACE-01` 空态页可见新建/导入两个主入口（真机）；`TX-WORKSPACE-02` 宽屏自适应列数、窄屏单列（折叠屏两态）；`TX-WORKSPACE-03` 构建中退出页面再回来横幅仍在（真机）。 |

#### `workspace.explorer`（文件树 / 源码浏览 / 编辑器）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `workspace.explorer`。所属：工坊 → `WorkspaceExplorerScreen` → `CodeEditorScreen`。出现条件：从项目卡/共享存储进入；每个项目 1 个 Explorer 实例；编辑器按文件入栈。 |
| 2 | 网格与定位 | Explorer 701 行：路径栏 + 文件列表（`WorkspaceExplorerScreen.kt:660` 附近有 48dp 助手）；编辑器 `CodeEditorScreen` 190 行 + `NativeWorkspaceEditor`；`WorkspaceEditorChrome`（顶栏）/`WorkspaceEditorFilePicker`/`WorkspaceEditorSearch`。 |
| 3 | 命中区 | 文件行整行、返回、搜索、保存/撤销等 chrome 按钮；行内操作 48dp 助手（`WorkspaceExplorerScreen.kt:660`）。 |
| 4 | 手势 | 单击进目录/开文件；编辑器内文本选择/滚动；搜索面板（`WorkspaceEditorSearch`）。 |
| 5 | 行为 | 打开文件 → `CodeEditorDestination(projectName, relativePath)` 入栈（`TaiXuNavHost.kt:217-219,287-295`）；编辑草稿 `EditorDraft`/`EditorSessionController` 管理脏态；二进制文件用 `BinaryFileNotice` 拒绝编辑（E1 `ARCHITECTURE_RULES.md:102`）。 |
| 6 | 视觉状态 | 脏文件标记、保存中、只读。 |
| 7 | 布局占位与展开形态 | 不展开；编辑器全屏页。 |
| 8 | 滚动语义 | 文件列表 LazyColumn；编辑器内部滚动（`BasicTextField`/自定义）。 |
| 9 | 动效 | 页面转场由导航栈。 |
| 10 | 边界态 | ① 空目录：空态；② 超大文件：提示/截断（未核实，E2）；③ 二进制：只可分享/另存（E1）；④ 外部修改冲突：checkpoint/冲突检测（README"外部修改冲突检测"）。 |
| 11 | 无障碍 | 文件行有文件名文本；图标辅助。 |
| 12 | 依据 | `E0` `feature/workspace/.../WorkspaceExplorerScreen.kt:1-701`；`E0` `CodeEditorScreen.kt:1-190`、`NativeWorkspaceEditor.kt:1-103`、`WorkspaceEditorChrome.kt:1-169`、`WorkspaceEditorSearch.kt:1-204`、`EditorDraft.kt`、`EditorSessionController.kt:1-160`、`BinaryFileNotice.kt:1-73`；`E0` `TaiXuNavHost.kt:217-221,278-295`；`E1` `README.md`、`docs/ARCHITECTURE_RULES.md:102`。 |
| 13 | 反证 + 验收 | **反证**：若把编辑器做成 sheet，编辑时看不到完整行、且与聊天输入法冲突；全屏 + 独立导航项是唯一能容纳软键盘的方案。若允许把二进制文件按 UTF-8 打开再保存，产物必坏。**验收**：`TX-EXPLORER-01` 长路径单行 ellipsis 不挤掉操作（真机）；`TX-EXPLORER-02` 二进制文件给出提示且不给编辑入口（真机构造）；`TX-EXPLORER-03` 编辑后返回再进，脏态/草稿行为正确（真机）。 |

#### `workspace.build`（构建配置 / 进度 / 签名交付）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `workspace.build`。所属：工坊 → 构建对话框/进度弹窗/构建设置/签名。出现条件：点项目卡"运行"或横幅；`BuildProgressDialog` 可见时 1 个；设置页 `WorkshopSettingsDestination` 系列。 |
| 2 | 网格与定位 | `WorkspaceBuildDialogs.kt` 523 行（构建配置 + 进度 + 日志）；`WorkshopSettingsScreen` 315 行（环境/SDK/签名入口）；`WorkshopSigningScreen` 475 行；进度在首页有常驻横幅（`WorkspaceScreen.kt:198-208`）。 |
| 3 | 命中区 | 构建类型选择（`WorkspaceBuildTypePicker`）、开始/取消/隐藏、日志复制、安装（`launchInstaller`）、签名表单。 |
| 4 | 手势 | 单击；日志区滚动/选择复制。 |
| 5 | 行为 | 构建在沙箱后台跑（`--no-daemon --max-workers=2`，E1 `ARCHITECTURE_RULES.md:92`）；进度通过 `buildProgress` 流驱动横幅 + 弹窗；弹窗可最小化（"随时最小化"）→ 横幅继续显示；完成后可安装/导出（`onLaunchInstaller`）。 |
| 6 | 视觉状态 | 构建中/成功/失败三态；日志等宽字体；失败保留完整日志与复制（E1 `ARCHITECTURE_RULES.md:47` 的"弹窗保留完整内容复制"）。 |
| 7 | 布局占位与展开形态 | 进度横幅占位（fullWidthItem）；日志对话框为可最小化浮层（形态 C）。 |
| 8 | 滚动语义 | 日志在 Dialog 内滚（`verticalScroll`，E1 `ARCHITECTURE_RULES.md:45` 对 Dialog 的硬要求）。 |
| 9 | 动效 | 无自定义。 |
| 10 | 边界态 | ① 运行时未就绪：禁止构建；② 构建超时/被杀：状态保留为失败可重试；③ 签名缺失：签名设置页引导；④ APK 产物缺失/SHA 不符：显式提示不静默（E1 `ARCHITECTURE_RULES.md:103`）。 |
| 11 | 无障碍 | 进度条有语义（miuix）；日志纯文本。 |
| 12 | 依据 | `E0` `feature/workspace/.../WorkspaceBuildDialogs.kt:1-523`、`WorkspaceBuildTypePicker.kt:1-131`、`WorkspaceBuildTaskCoordinator.kt:1-127`、`WorkshopSettingsScreen.kt:1-315`、`WorkshopSigningScreen.kt:1-475`、`WorkshopSdkControls.kt:1-92`；`E0` `WorkspaceScreen.kt:198-208,323-329`；`E1` `README.md`（"沙箱内 Gradle / Flutter 后台构建、APK 签名与安装"）、`docs/ARCHITECTURE_RULES.md:92-93,103`。 |
| 13 | 反证 + 验收 | **反证**：若构建必须停在该页面（不能后台），手机端一次 Gradle 构建几分钟不能切应用；太墟用"横幅常驻 + 弹窗可最小化 + 前台服务保活"（README：FGS/WakeLock）保住可用性。**验收**：`TX-BUILD-01` 构建中切到聊天再回工坊，横幅进度连续（真机）；`TX-BUILD-02` 最小化弹窗后横幅仍显示并可再展开（真机）；`TX-BUILD-03` 失败保留完整日志可复制（真机）。 |

#### `workspace.diff`（Diff 呈现：工具内联 / 详情 / 文件改动）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `workspace.diff`。所属：聊天工具卡（`ToolDiffView`）与工具详情第三页签、`TurnChangesCard`。出现条件：`write/edit` 工具结果、或轮次有文件改动。 |
| 2 | 网格与定位 | `ToolDiffView` 464 行：文件路径头 + 行级 diff；`EditToolDiff` 用 `LazyColumn` 虚拟化（大 diff 只组合可见行，`:107`）；`WriteToolDiff`/`ReadToolDiff`/`BaseToolDiff` 各自 `horizontalScroll`（`:190,227,259`）。 |
| 3 | 命中区 | 文件路径头 `minimumInteractiveComponentSize().clickable{}`（`:404,423,446`）→ 打开预览/文件。 |
| 4 | 手势 | 单击路径打开；横向滚动看长行；行选择复制（`SelectionContainer` 在详情里）。 |
| 5 | 行为 | 点击 → `onOpenFile(project, path)` 或预览 sheet；diff 颜色 `success/error` 语义色。 |
| 6 | 视觉状态 | 增/删行底色（语义色）；`DiffStatBadge(+n -m)` 在工具行（`ToolStepHeader.kt:48-51`）。 |
| 7 | 布局占位与展开形态 | 内联在工具卡内**占位**；长 diff 自身虚拟化但不设最大高（会很长）→ 与 G-03 的"长内容要么内滚要么弹层"冲突，见 §4 Q-TX-17。 |
| 8 | 滚动语义 | 纵向 LazyColumn（虚拟化）+ 横向 `horizontalScroll`；嵌套在聊天主列内 **E2 风险**：编辑工具 diff 的 LazyColumn 在聊天 LazyColumn 项里（与 A2UI List 同类问题），未见显式限高。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | ① 超长行：横向滚动；② 二进制/超大文件：只给文件头不渲染 diff；③ 空 diff：不显示。 |
| 11 | 无障碍 | diff 颜色 + 前缀符号双编码（未核实符号，E2）。 |
| 12 | 依据 | `E0` `feature/chat/.../ToolDiffView.kt:1-464`；`E0` `HashlineDiffPreview.kt:1-82`；`E0` `TurnChangesCard.kt:1-94`；`E0` `ToolStepHeader.kt:48-51`；`E0` `ToolDetailsSheet.kt:77-78`（第三页签 Preview）。 |
| 13 | 反证 + 验收 | **反证**：若不虚拟化，一次 5000 行 diff 会在组合期构建 5000 个 Text（掉帧数秒）；若 diff 用别色（非 semantic success/error），暗色主题下对比度不可读。**验收**：`TX-DIFF-01` 5000 行 diff 首帧 < 200ms、滚动不卡（profiler）；`TX-DIFF-02` 增删色在浅/深主题都过对比度（截图 + 取色）；`TX-DIFF-03` 点文件头可跳到编辑器对应文件（真机）。 |

### 2.8 归位简表（不进 myco 首版实现的 17 个节点）

> 这些节点在 `taixu.facts.csv` 里都有明确 `node_id`（覆盖率门禁已过），但它们的面板**不在 myco 首版范围**（见 §1 缺口），
> 或属于"基座内部实现细节"。**P1 不展开 13 字段**：展开等于给不会实现的东西伪造精度（G-00）。
> 每行给"身份 / 关键证据 / 反证（一句话）"；P2 若决定纳入，再按 13 字段补全。

| node_id | 身份（一句话） | 关键证据（`path:line`） | 反证（反过来做会坏在哪） | myco 处置建议 |
|---|---|---|---|---|
| `base.layout.overlay` | 应用级浮层：悬浮聊天窗、引导遮罩、使用须知、更新提示 | `feature/chat/.../floating/FloatingChatView.kt:465`（列表 `overscrollEffect=null`）、`FloatingChatService.kt:149`（`onDragEnd` 拖动悬浮窗）、`feature/components/.../SpotlightGuide.kt:1-199` | 若浮层参与布局流，聊天页高度随浮层出现跳动（G-01 症状） | 归 `base.layout`（G-01 已有规则，无需新节点） |
| `base.primitives.screen-scaffold` | 二级页骨架：大标题顶栏 + 滚动效果 + insets | `RuntimeSettingsScaffold.kt:25-109`（nestedScroll 顺序）、`RuntimeLargeTopBar.kt`、`RuntimeSmallTopBar.kt`、`RuntimeScrollInsets.kt` | 滚动根整包 padding → 毛玻璃采不到内容（死白条） | 归 `base.primitives`（P3 写进 primitives spec） |
| `base.renderer.tool-registry` | 工具卡渲染注册表：每个工具一套 icon/title/target/diffStat/Preview | `feature/chat/.../ToolUiRegistry.kt:1-102`（`LocalToolUiRegistry`、`resolve(call)`）、`ChatToolNames.kt:59` | 若在 UI 里 `when(tool)` 硬编码，新增 MCP 工具就要改 UI 层 | 归 `base.renderer`（与 DSH `ui-renderer` 同构） |
| `model.picker` | 模型/供应商/思考强度选择 | `ChatModelPicker.kt`、`ChatProviderPicker.kt`、`ModelIcon.kt`、`ProviderBadge.kt`、`ChatScreen.kt:490-512`（`ModelDialog`）、`:600-605`（`ReasoningEffortSlider`） | 模型名与思考强度若分散在设置页，聊天中切换要跳 4 层 | 归 `model`（myco 已有面板，P3 直接对照） |
| `settings.mcp` | MCP 服务清单/编辑/工具策略 | `McpSettingsScreen.kt:120`（nestedScroll）、`McpServerEditorDialog.kt`、`McpToolPolicyRow.kt`、`McpServerJsonImport.kt` | MCP 工具若不做 per-tool 策略，一次授权=全部工具放行 | 归 `settings.plugins`（P3 取舍） |
| `settings.tools` | 工具中心（目录/安装/自愈）+ 工具详情 | `ToolCenterScreen.kt:116,756`、`ToolDetailScreen.kt:1-1289`、`Bundle*`（插件安装事务）、`PackagedPlugin*` | 工具安装失败若无诊断/自愈入口，用户只能重装 | 归 `settings.plugins` |
| `settings.subpages` | Linux 环境 / 存储 / SSH / FTP / 诊断 / 关于 / 赞助 / 许可 / 备份 / 统计 / 快捷短语 / 权限向导 | `LinuxEnvironmentSettingsScreen.kt:1-310`、`DistroManagementScreen.kt:1-622`、`StorageUsageScreen.kt:1-1078`、`SystemDevSettingsScreen.kt:1-482`、`AboutCommunityScreen.kt:1-438`、`StatsScreen.kt:1-248`、`QuickPhrasesScreen.kt:1-264` | 这 12+ 子页若平铺在 settings 首页，首屏放不下需要滚动 3 屏 | 归 `settings.general` + 各专项面板（P3） |
| `workspace.wizard` | 新建/导入（ZIP/GitHub/APK）/模板向导 | `WorkspaceCreateDialog.kt:1-708`、`WorkspaceImportDialog.kt:1-398`、`WorkspaceTemplateDialogs.kt:1-150`、`WorkspaceImportProgress.kt` | 若导入直接解压到目标目录（无 Zip Slip 防护），可被路径穿越写任意文件 | 归 `workspace`（安全要求见 `ARCHITECTURE_RULES.md:99-102`） |
| `home.screen` | 系统概览：体检 / 资源 / 引擎状态 / 远程服务 / WebChat 卡 | `HomeScreen.kt:119,201`（nestedScroll）、`:333`（`EnvironmentDoctorCard`）、`:810`（`RuntimeEngineStatusCard`）、`HomeResourceMetricCard.kt` | 若把体检项埋在设置里，沙箱没装好的用户第一步就卡住 | **缺口**：新建 `panels/home.md` 或归 `settings.general` |
| `bot.screen` | Bot 列表 / 详情 / 群聊 / 像素办公室 / 群治理 / 预算 / 计划 | `BotsScreen.kt:44`、`GroupChatScreen.kt:231`、`GroupInfoDialog.kt`、`GroupBudgetDialog.kt`、`PixelOffice*`、`TeamArchitectureGraph.kt` | 多智能体协作若不显示"谁在做什么"，用户无法判断该找哪个 agent | **缺口**：归 `subagent`（只做树/状态，不做像素办公室） |
| `workflow.editor` | 可视化工作流：DAG 画布 / 运行 / 审批 / 计划 / 结果 | `WorkflowScreen.kt:66,106,251,286`、`WorkflowCanvas2D.kt:171`（pointerInput 拖拽）、`WorkflowApprovalDialog.kt`、`WorkflowScheduleDialog.kt`、`WorkflowStructuredResultButton.kt` | DAG 画布在 360dp 屏上几乎不可操作（节点/连线最小可视尺寸不足） | **缺口**：myco 首版 ⏸（`plan` 面板只借其"步骤状态"） |
| `developer.screen` | 开发者/诊断：运行时控制、ADB 日志、远程命令 | `DeveloperScreen.kt:62,534`、`AdbLogcatScreen.kt:1-`、`AgentDiagnosticsSection.kt`、`RemoteCommandPanel.kt` | 若无日志入口，agent 出错时用户只能靠"再试一次" | 归 `settings.plugins`/诊断（低优先） |
| `git.screen` | Git 工作台：改动/提交/分支/标签/远程/AI 提交说明 | `GitScreen.kt:70,400,513,624,770`、`GitChangesTab.kt:179,204,227`（48dp 助手） | 若把 Git 操作塞进聊天消息，误触 push 无法撤销 | **缺口**：建议 `workspace.git` 子面板 |
| `terminal.pane` | 原生 PTY 终端（多会话 / Ctrl+C / 尺寸变化） | `TaiXuTerminalHost.kt:29`（`OVER_SCROLL_NEVER`）、`TerminalScreen.kt`、`TerminalSessionDialogs.kt`、`TerminalExtraKeys.kt` | 终端若与聊天共用滚动容器，Ctrl+C 长按会被误判为滚动 | 归 `base.sidebar-right`（myco 已提右栏终端） |
| `browser.pane` | 多 Tab WebView / URL 栏 / 调试 / 网络时间线 | `BrowserScreen.kt:57,89,181,257,406`、`NetworkTimelineSheet.kt`、`SnapshotSheet.kt` | 内嵌浏览器在手机上会与抽屉边缘手势冲突（太墟用 `canSwipeOpenNavigation` 规避） | **缺口**：myco 首版 ⏸ |
| `a2ui.preview-screen` | A2UI 预览页（注入示例 / 清空 / 浏览最近载荷） | `A2uiPocScreen.kt:162-242`、`A2uiPocScreenPreviews.kt` | 若无预览页，开发者无法在不触发模型的情况下调试 A2UI | 归 `base.renderer`（调试入口） |
| `onboarding.flow` | 首次引导：欢迎 / 免责 / 沙箱初始化 / 权限 / 模型接入 | `OnboardingScreen.kt`、`OnboardingDisclaimerPage.kt`、`OnboardingSystemPage.kt`、`OnboardingModelPage.kt`、`OnboardingPermissionPage.kt` | 沙箱未初始化就进聊天，agent 每个工具都失败且提示不清 | **缺口**：`settings.general` 的首次启动分支 |

---

## 3. 排除清单（按理由聚合）

> 分母 2154，已归节点 **2097**（E0）、显式排除 **57**、未覆盖 **0**（`_ws/out/coverage-taixu.md`）。
> 排除判定标准：**不是 Android Compose 运行时的用户可交互点**，或**不是本仓库手写 UI 的证据**。

| # | 排除理由（具体） | 条数 | 示例位置 | 为什么不能作为契约依据 |
|---|---|---|---|---|
| 1 | **第三方前端打包产物**：`app/src/main/assets/webchat/assets/index-*.js/css`（Vite minified bundle） | **20** | `app/src/main/assets/webchat/assets/index-BDo-fpBS.js:1`、`index-bHZ5jIte.css:1` | 是 WebChat 电脑端前端（预构建产物），不是 Android Compose UI；其 `onClick` 属于 Web 页面 DOM 事件，与原生面板契约无关 |
| 2 | **单元 / 渲染测试代码**：`*/src/test/**` 内的 `onClick`/`onToggle` | **19** | `feature/settings/src/test/.../McpMaterialRenderTest.kt:53`、`feature/chat/src/test/.../SessionComposerDraftRenderTest.kt` | 测试替身/断言桩（如 `onToggle = { fail(...) }`），不进入运行时界面；把它们当节点会污染面板交互清单 |
| 3 | **Compose `@Preview` 预览样张**：`*Previews.kt` 内的空 lambda / 假数据 | **17** | `feature/components/.../RuntimeButtonsPreviews.kt:28-35`、`feature/settings/.../SettingsDensityPreviews.kt:28-58` | 只在 IDE 预览渲染，不打包进运行时；`onClick = {}` 无真实行为 |
| 4 | **扫描器误命中**：`WorkflowGuiPilot.kt` 的 `it.clickable` 是工作流 GUI 自动化的**布尔字段名**（用于排序候选节点），不是 Compose 修饰符 | **1** | `harness/src/main/java/top/wkbin/taixu/harness/workflow/WorkflowGuiPilot.kt:170` | 该文件是 GUI 自动化驱动（在沙箱里点 UI），不是界面实现；误命中会造出一个不存在的节点 |
| | **合计** | **57** | | |

**其他非 UI 目录（正常有 0 命中，不作为排除项）**：`core/`（数据库/网络/序列化/DataStore）、`tools/`（工具注册与安装事务）、`runtime/`（PRoot/RootFS/PTY/工作区）、`harness/` 的循环与 Provider（除上述 1 条误命中外无交互点）。这些目录在 `scan` 阶段就没有命中，**不需要逐条排除**；它们的职责由 §1 归位表的"非 UI"行说明。

---

## 4. 待确认（E2 候选，Q-TX 编号）

> 这些是**代码里能读到事实、但"myco 该不该跟"需要用户拍板**的问题；也包含太墟内部自相矛盾处（同一份代码两套做法）。
> 按 `docs/spec/README.md` G-00：**推测禁止进 spec/实现**，必须先拍板再写 `panels/*.md`。

| ID | 问题 | 事实（E0/E1） | 需要拍板的选项 |
|---|---|---|---|
| **Q-TX-01** | 展开动画：太墟对长内容也做 `expandVertically` 高度动画，myco G-10 规定 >240dp 直接切换 | `E0` `ChatMessageBubbles.kt:649-653`（思考块用 `AnimatedVisibility(expandVertically()+shrinkVertically())`，未按内容高度分档） | A. 守 G-10（长内容直接切换）；B. 跟太墟（统一高度动画）；C. 分层（≤240dp 动画、超过直接切换，但需实测太墟是否掉帧） |
| **Q-TX-02** | 命中区：太墟有 40dp 图标钮、28dp 过程行、18/16/12dp 内嵌图标钮三套；myco 是否允许"行内次级动作 < 44dp" | `E0` `Buttons.kt:171`（48dp 助手 `sizeIn`）、`ComposerSendButton.kt:20-24`（40dp 且明确不加 48）、`ChatMessageBubbles.kt:609,631,644`（18/18/16dp）、`ToolStepsCard.kt:59-60`（12dp） | A. 全局 44dp 底线，18dp 钮必须扩边；B. 允许"行内次级动作"例外（但需定义什么叫次级）；C. 学太墟：40dp 图标 + 48dp 只给文字链 |
| **Q-TX-03** | G-03 三形态是否允许"同一节点按状态切形态"：太墟思考块 live=限高 160dp 内滚（B）、settled=不封顶（A） | `E0` `ChatMessageBubbles.kt:706-735`、`:524-544` | A. 允许（推荐，因为 live 内容无限增长）；B. 禁止混用，一律 A 或一律 B（需给理由） |
| **Q-TX-04** | 抽屉滑动打开权限：太墟只在聊天根页允许滑动开抽屉，其余覆盖页禁用 | `E0` `MainPageNavigation.kt:7`、`TaiXuNavHost.kt:112` | A. myco 无限抽屉也做"仅根层可滑开"；B. 任意层都可滑开（需解决与页面内横滑手势冲突） |
| **Q-TX-05** | 抽屉"不放关闭按钮，只靠滑动/遮罩关闭" | `E1` `ARCHITECTURE_RULES.md:52`；`E0` `RuntimeRevealNavigationDrawer.kt:125-131`（只有手势/遮罩/返回） | A. 跟太墟（无关闭按钮）；B. 加关闭按钮（无障碍/发现性更好，但多一个关闭语义）；C. 无按钮但加"边缘可点关闭"热区 |
| **Q-TX-06** | 悬浮顶栏 + 内容滚到顶栏下方（无整行占位） | `E0` `ChatTopBar.kt:71-75`（悬浮 Row）、`ChatMessageList.kt:148-151`（`contentPadding.top = statusBars+64dp`）、`ChatPaneContent.kt:169-177`（顶部渐隐） | A. 采用悬浮顶栏（省 64dp 阅读面积，但需渐隐遮罩）；B. 常规 AppBar 占位（实现简单、可访问性好） |
| **Q-TX-07** | 是否把 A2UI（目录白名单 + 数据协议 + 事件回传）作为 myco「无限抽屉的抽象接口」范式 | `E0` §5 全节 | A. 直接采用（抽屉内容 = 声明式数据 + 目录渲染）；B. 只借"目录白名单"思想，不引入协议；C. 都不做 |
| **Q-TX-08** | 过程链"分页展开（+8）"是否作为第四种展开形态纳入 G-03 | `E0` `ToolStepsCard.kt:30-38,65-69`、`ChatTimelineProjection.kt:114-125` | A. 纳入 G-03 作为形态 D；B. 归入"列表分页"通用规则（不进 G-03）；C. 不做（直接展开全部） |
| **Q-TX-09** | 过程链"首 8 步 + 最近待关注 + 焦点强制展开"策略是否照搬 | `E0` `ChatTimelineProjection.kt:114-125`、`ToolStepsCard.kt:30-38` | A. 照搬（含 8 这个数字）；B. 只照搬"待关注固定露出"；C. 不折（全部展开） |
| **Q-TX-10** | 太墟内部不自洽：`RuntimeFilterChip` 注释声称"包含 minimumInteractiveComponentSize() 满足 48dp"，实现未调用 | `E0` `feature/components/.../RuntimeComponents.kt:607-611`（注释）vs `:612-633`（实现无该 modifier） | 这是**参考实现的缺陷**：myco 不可照抄；若要 48dp 需真调用，且要评估 32dp chip 撑高后的布局 |
| **Q-TX-11** | 被覆盖页只吞指针未清语义：TalkBack 仍可能聚焦到不可见页面 | `E0` `TaiXuNavHost.kt:172-179`（只 `consume()` 指针）vs 抽屉/侧栏都做了 `clearAndSetSemantics{}`（`RuntimeRevealNavigationDrawer.kt:149,160`） | A. myco 在页面级也加 `clearAndSetSemantics`；B. 用 `Modifier.pointerInput` + `semantics{invisibleToUser()}`；C. 忽略（不可接受，需说明理由） |
| **Q-TX-12** | 太墟两套遮罩值不一致：reveal 抽屉用 `onSurface@0.12`，覆盖侧栏用 `scrim@0.32` | `E0` `RuntimeRevealNavigationDrawer.kt:163-166` vs `RuntimeAdaptiveNavigationScaffold.kt:169-172` | myco 需定义**唯一**遮罩 token（0.12? 0.32? 按层深分级？）——建议进 `base.theme` 的 G-01 补充 |
| **Q-TX-13** | 发送钮长按手势与列表滚动仲裁未验证：`detectTapGestures(onLongPress)` 是否吞掉竖直拖动 | `E0` `ComposerSendButton.kt:34-38`（只有 onLongPress，无 `onPress` 消费策略） | 需真机验证"按住发送钮上滑"是滚动还是长按；若误判需改成 `combinedClickable` 或加方向锁 |
| **Q-TX-14** | 工具行可点性在两种容器里不一致：独立 `ToolCard` 整行可点开详情；链内 `ToolStepsCard` 的行不可点（要先展开链） | `E0` `ChatToolCards.kt:98-102`（`clickable{showDetails=true}`）vs `ToolStepsCard.kt:70-93`（只渲染行，无 clickable） | A. 统一（链内行也可点开详情）；B. 保持（链内先展开再点） |
| **Q-TX-15** | 工具详情抽屉：内滚与"下拉关闭"同向，未见 `nestedScroll` 仲裁 → 可能是 G-04 的反例 | `E0` `ToolDetailsSheet.kt:75-76`（`verticalScroll`）+ `miuix/Dialogs.kt:139-189`（sheet 下拉） | A. 内滚到顶才允许下拉关闭（G-04 式）；B. 跟太墟（不仲裁，需实测是否误关）；C. 关闭下拉、只留按钮 |
| **Q-TX-16** | 思考流式视口固定 160dp，不随字号缩放 | `E0` `ChatMessageBubbles.kt:707`（`heightIn(max = 160.dp)`） | A. 改成 `160dp * fontScale` 或改为"屏高比例"；B. 保持固定（大字号下显示行数减少） |
| **Q-TX-17** | Diff 内联在聊天列表项里，编辑 diff 用了 LazyColumn（嵌套滚动风险，与 A2UI List 同类）但未见限高 | `E0` `ToolDiffView.kt:107`（`LazyColumn` 虚拟化）、`:63`（`fillMaxWidth`，无 `heightIn`） | A. myco 必须给 diff 限高或改非懒列表（否则同 A2UI 崩溃面）；B. 一律进 sheet 展示 |

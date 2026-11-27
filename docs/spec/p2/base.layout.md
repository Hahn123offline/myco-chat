# P2 合并：base.layout（无限抽屉壳 / 层叠 / 返回栈）

> 合并员：M2。输入：`ref/dsh-base.md`（§0、§2.6）、`ref/dsh-sidebar.md`（§0.1/§0.2、§2.1）、`ref/dsh-workspace.md`（§0.5、§2.2）、`ref/dsh-go.md`（§0、§2.7、§2.15、§6）、`ref/taixu.md`（§0、§2.1、§5、§6）、`ref/dsh-mobile.md`（§0、§2、§3.1、§3.2、§6）、`ref/legacy-myco.md`（§0、§2.6、§2.7、§2.8、§4、§5、§6）+ `00-global-rules.md` + `_conflicts.md` + `open-questions.md` + `docs/design/90-m5-visual-system.md`。
> 本文件**只做归一取舍**，不新增事实。凡 ref 未写的一律「未取证」；架构方案可以是**我方新设计**，但逐条标注 `【我方设计】` 并给出理由与代价。
> 只写本文件；未改 `ref/**`、`00-global-rules.md`、`_conflicts.md`、`open-questions.md`、`_merge-*.md`、`p2/_plan.md`、其它 `p2/*.md`、`90-m5-visual-system.md`；未改 app；未 git commit。

## 0. 读表须知（先看）

### 0.1 证据缩写（沿用各事实库原文）

| 缩写 | 文件 | 行内前缀 |
|---|---|---|
| `DSH` | `ref/dsh-base.md` | `P/`=`ui-primitives/src`、`L/`=`ui-layout/src/client`、`D/`=`ui-dockkit/src/components`、`E/`=`ui-dockkit/src/engine`、`T/`=`ui-theme/src` |
| `DSS` | `ref/dsh-sidebar.md` | 相对 `E:\deepseek-harness\packages\client`，如 `SidebarRoot.tsx`、`columns.ts` |
| `DSW` | `ref/dsh-workspace.md` | 同上，如 `SidebarRight.module.css`、`RightbarRoot.tsx` |
| `GO` | `ref/dsh-go.md` | `CL/HS/PT/AL/TH/MO/GL/SR/TR/LT/OF/CO/CS/AT/TH`（其 §0 已定义） |
| `TX` | `ref/taixu.md` | 相对 `_ws/refs/taixu`，如 `TaiXuNavHost.kt`、`RuntimeAdaptiveNavigationScaffold.kt` |
| `MOB` | `ref/dsh-mobile.md` | `M/:line`=`mobile-adapt/lib/client.js`；`A/:line`=`MainActivity.java`；`X/:line`=`AndroidManifest.xml`；`R1/R2/D2` 为其文档 |
| `LEG` | `ref/legacy-myco.md` | `MA/CC/CR/CP/MS/PN/DR/IB/BR/SS/AS`（其 §方法 已定义） |
| `M5` | `docs/design/90-m5-visual-system.md` | **我方设计文档（E1，待拍板）**，不是事实库 |
| `G-xx` | `docs/spec/00-global-rules.md` | 全局规则 |

### 0.2 本任务的三处取证缺口（必须显式说明，不许脑补）

1. **`taixu.md` 在 13:18 被扩写到 §5/§6**（本任务第一次读到的是 §0–§4 的 836 行版本，已按最新版重读）。现在的 §5（A2UI 完整契约，行级）与 §6（导航/面板架构对照）**已可作为 E0/E1 引用**。仍缺：§4 中 Q-TX-07 的**拍板**（A2UI 是否作为我方接口范式）——那是用户决定，进 §F `Q-BL-12`。
2. **`chat-demo` 不在 M2 任务书必读清单**（`_merge-brief.md` 的示例表头有它，`p2/_plan.md` M2 行没有）。为守 G-00，本文件**不设 chat-demo 列**，也不引用未读内容；若 Lead 需要四方对照，请另开补充任务（属跨面板共性，已在最终回复上报）。
3. **旧 myco 的"抽屉视觉参数"未取证**：`legacy-myco.md` 只取证了交互（96dp 触发、22%/600px·s⁻¹ 退出、20dp 让系统、180ms 退出动画、`drawerState.open/close`、三屏层叠），**没有**取证 Material `ModalNavigationDrawer` 的遮罩 alpha、宽度、曲线、是否缩放底层。用户硬要求"最终外观与旧结构一致" → 外观基线只能以**P4 真机逐帧对照旧版**为准（见 §E.3 风险 R6），不许用 M5 文档的 0.96/0.5/4dp 冒充"旧外观"（M5 是设想，且与旧结构是否一致**未取证**）。

### 0.3 拍板列口径

- `✅ 采用建议`＝我给出可验收建议、等用户确认；`⏸ 待拍（Q-BL-nn）`＝决定权在用户且影响全局面板；`❌` 本次无。
- **未拍板的行不得进入 P3**（`_merge-template.md:23`）。
- 本面板**新发现**的互斥冲突用 `C-BL-nn`（待 Lead 收进 `_conflicts.md`，我不改那份文件）；已登记的用 `C-xx`。

### 0.4 本面板的定位（防止和 M1/M3 抢写作用域）

`base.layout` = **容器契约**：层叠几何、栈/邻接数据、返回与预测性返回、遮罩、宽度与断点、z 阶梯、保活、被覆盖层语义。**不**包含：原子（按钮/披露行/模态/菜单，归 M1 `base.primitives`）、侧栏内容（会话行/搜索/分组，归 `sidebar` 面板 spec）、右栏页签（归 `base.sidebar-right`）、输入区（归 `composer`）。按 G-00b 单一事实源，本文件只定义"列 vs 浮层"的判据与槽位，不复述它们的内部节点。

---

## 1. 节点清单（先穷举，再填表）

> 口径：本面板是容器，节点含**交互原子**与**结构契约原子**两类；后者没有独立命中区，但必须逐条定契约（否则 P3 写不出 13 字段与验收）。不允许"其他"。
> `参考里有没有`：只统计 ref 事实库里**已落成节点**的做法；`旧 myco 有没有` 以 `legacy-myco.md` 为准。

| 节点 ID | 出现条件 | 参考里有没有 | 旧 myco 有没有 |
|---|---|---|---|
| `base.layout.panel-graph` | 壳启动时加载；每次打开邻接面板前查表 | 四家**都没有**「抽象邻接图」：DSH 无邻接概念（`DSH §0-6`）、dsh-Go 靠代码里写死的入口（`GO §0-8`）、太墟 `drawerContent` 硬编码 + 60+ 目的地静态注册（`TX §0-2`） | **无**（`MainActivity` 里 `when`/布尔状态机，`LEG §0`、`MA:223-256`） |
| `base.layout.panel-host` | 始终；壳的主容器 | DSH：`activePanelId` 单槽覆盖（`L/stores.ts:16-22,94-96`）；dsh-Go：`HomeShell` + `MaterialPageRoute` 多页面栈（`GO §0-8`）；太墟：唯一 `NavDisplay` 单栈（`TX §2.1`） | **有**（单 Activity + 三屏层叠，`MA:409-476`） |
| `base.layout.route` | 每个被打开的面板实例 1 个 | DSH 无栈；dsh-Go 二级页是路由页（`GO §2.15` 字段 1）；太墟栈项 = `AppDestination`（`TX §2.1` 字段 1） | **有**（`screen` 状态 + drawerState，`MA:223-256`） |
| `base.layout.route.scrim` | `width` 非全宽或需要压暗时 1 个/层 | DSH：列模式无遮罩（`DSS §0.2-①`）；dsh-Go：scrim 随进度淡化（`GO §2.15` 字段 6）；太墟：覆盖态 scrim 0.32（`TX §2.1 drawer` 字段 3）；mobile-adapt：遮罩 35%（`M/:345-366`） | **有**（Material Drawer 默认遮罩；具体 alpha 未取证） |
| `base.layout.route.back` | 栈深 ≥1 或常驻列收起时；顶栏/页内返回入口 | DSH 无返回栈（`DSH §0-6`）；dsh-Go 二级页返回键 44 浮动玻璃（`GO §2.15` 字段 3）；太墟顶栏返回箭头（`TX §2.1 nav-stack` 字段 4） | **有**（`base.primitives.topbar-back`，`LEG §2.7`） |
| `base.layout.route.drag-dismiss` | 栈顶面板可关时 | dsh-Go：横滑关抽屉/右面板、跟手（`GO §2.15` 字段 4）；太墟：`anchoredDraggable` 已开时始终可关（`TX §2.1 drawer` 字段 4）；mobile-adapt：**无侧滑**（缺陷，见 §3） | **有**（`base.nav.swipe-back`：>22% 或 v>600，`LEG §2.8`；旧退出动画 180ms，`PN:84-96`） |
| `base.layout.route.drag-open` | 当前层声明了可滑开的邻接边时 | dsh-Go：抽屉边缘手势区整屏（`GO §2.15` 字段 3）+ 跟手；太墟：仅聊天根页可滑开（`MainPageNavigation.kt:7`）；mobile-adapt：**无**（缺陷） | **有**（聊天正文右滑 ≥96dp 打开抽屉、只观察不消费，`LEG §2.8`） |
| `base.layout.route.predictive-back` | 系统返回手势进行中（Android 13+） | DSH 无；dsh-Go：Android 预测性返回（`GO §2.15` 字段 4）；太墟：**未见实现**（`TX §2.1 nav-stack` 字段 4 明写无 `PredictiveBackHandler`） | **有**（`PN:34-114` 三通道共享 progress） |
| `base.layout.back.dispatcher` | 任何返回事件（键/手势/预测式完成） | dsh-Go：`PopScope` 顺序（右面板 → 多选 → 抽屉，`GO §0-8`）；太墟：分层 `BackHandler` 显式优先链 7 级（`TX §6.3`） | **有**（设置/bin 各自 `settingsNav/binNav`，`LEG §2.7`；抽屉打开时系统返回=预测式关闭，`LEG §2.8`） |
| `base.layout.stack-lifetime` | 栈深 ≥2 或全宽覆盖时 | DSH 右栏：整列平移隐藏、**内容保持挂载**（`DSW §0.5`、`SidebarRight.module.css:44-62`）；太墟：隐藏抽屉保持组合 + `clearAndSetSemantics`（`TX §0-5`、`RuntimeRevealNavigationDrawer.kt:145-160`） | **有**（每会话滚动状态复用，`MA:238-239`） |
| `base.layout.a11y.covered` | 任一层被全宽/部分覆盖时 | dsh-Go：浮层打开 `ExcludeSemantics` 屏蔽底层（`GO §2.15` 字段 11）；太墟：隐藏层 `clearAndSetSemantics`（`TX §0-5`）；mobile-adapt：**无 focus trap / 无 dialog 语义**（缺陷） | **无**（未取证有任何语义屏蔽） |
| `base.layout.window-class` | 每帧随窗口尺寸重算 | DSH：仅宽度断点 1024（`DSH §2.6 frame` 字段 10）；dsh-Go：`<600/600–840/≥840` 且**高<480 一律 compact**（`GO §2.15` 字段 2）；太墟：`600/840dp`（`RuntimeWindowSize.kt:15-31`）；mobile-adapt：`767px`（`M/:198`） | **有**（手机抽屉 / 宽屏常驻，`MA:547-587`；断点未取证） |
| `base.layout.shell` | 始终 | DSH 三列 CSS Grid（`L/AppFrame.module.css:1-8`、`AppFrame.tsx:265-266`）；dsh-Go 三栏 `AdaptiveScaffold`（`GO §2.15` 字段 2）；太墟 `RuntimeAdaptiveNavigationScaffold`（`TX §0-3`） | **有**（单 Activity 全屏 + 层叠） |
| `base.layout.sidebar-column` | 宽屏（`window-class` 判定为宽）且侧栏展开/收起 | DSH：280/56/0 网格列、无遮罩/无手势/无返回键（`DSS §0.2-①②`）；dsh-Go：≥840 常驻 sidebar 320、rail 68（`GO §2.15` 字段 2）；太墟：Expanded 320 停靠、Medium rail 84+覆盖 320（`TX §2.1 drawer` 字段 2） | **有**（宽屏常驻，`MA:547-587`；列宽未取证） |
| `base.layout.slots.overlay` | 始终存在（框架浮层槽） | DSH：`shell.overlay` 整层 `pointer-events:none`、子元素 auto（`L/AppFrame.module.css:281-290`）；其余三家无同名槽 | **无** |
| `base.layout.slots.right-rail` | 宽屏且右栏轨道存在 | DSH：网格第 3 列、最小 300、最大 70%、首开 45%（`L/columns.ts:11-29`）；dsh-Go：右面板 320–420（`GO §2.15` 字段 2）；太墟：伴随面板 Docked/Pager/Overlay（`TX §2.1 adaptive-panes`） | **无**（旧 myco 无右栏） |
| `base.layout.z-ladder` | 常量契约 | DSH：显式阶梯 10/11/15/20/40 + modal 1000 + menu 1100（`DSH §0-7`）；dockkit 另开 10/60/70（`DSH §0-7`） | **无**（层叠依赖 Compose 默认 draw order + Material 组件内部 z） |

> 计数：**17 个节点**（交互原子 7 个：scrim/back/drag-dismiss/drag-open/predictive-back/sidebar-column/overlay；结构契约 10 个）。其中 `panel-graph`、`stack-lifetime`、`a11y.covered`、`z-ladder` 是**新增节点**；`predictive-back`、`slots.overlay` 是**已有节点的新增契约**（见 §5 口径）。

---

## 2. 对照表（主线）

> 冲突列只写"做到 A 就做不到 B"。`C-BL-nn` = 本面板新发现（待 Lead 登记），**我不改 `_conflicts.md`**。

| 节点 ID | DSH | dsh-Go | 太墟 | 旧 myco | 冲突 C-xx | 建议 + 理由 | 状态 |
|---|---|---|---|---|---|---|---|
| `base.layout.panel-graph` | **无邻接概念**：只有 `activePanelId` 覆盖 + 槽位注册（`DSH §0-6`、`L/stores.ts:16-22`）；槽位是"谁注册谁渲染"，不是"从 A 能到 B" | **无数据图**：抽屉入口/右面板入口写死在布局代码（`GO §0-8`、`AdaptiveLayout`），宽屏三栏由宽度决定 | **无图**：`drawerContent` 硬编码 `SessionsSideDrawer`；60+ 目的地静态注册（`TX §0-2`）；§6.2 明写"邻接关系：未见实现" | **无**：`when` + 布尔，`MA:223-256` | **C-11** | **新增数据图**（用户硬要求"接口是抽象数据"）：节点=面板 id，边=`(from, edge) → to`，只允许注册表内 id（白名单，借太墟 Catalog 思想 `TX §5.6`）。理由：四家都没有 → 无法"抄"，只能新设计；反证：不做图就只能像太墟一样把入口硬编码，M6"用户自定义哪里接什么"永远做不了（`M5 §3.1` 明确图为纯数据） | ⏸ 待拍（Q-BL-01/13） |
| `base.layout.panel-host` | 单槽覆盖、替换即"换面板"，**无返回栈**（`L/stores.ts:16-22,94-96`；`L/service.ts:28-54` 无 pop） | `HomeShell` + `MaterialPageRoute` 多页面栈；二级页 iOS 式推入（`GO §0-8`） | **单栈 `NavDisplay`**（`TX §2.1 nav-stack` 字段 1：`rememberNavBackStack` 唯一栈）+ 4 覆盖根页共享历史（`MainPageNavigation.kt:10-22`） | 单 Activity 三屏层叠（`MA:409-476`） | **C-11**（Q-BASE-02） | **方案 1「覆盖栈」**：`List<PanelRoute>`，栈顶可见、下层保活、返回=pop。理由：①手机端两家（dsh-Go 页面栈 / 太墟单栈）**一致指向栈**，DSH 无栈是因为桌面主面板不切换会话（`L/stores.ts:18-20` 反证：栈会与其"全局面板不切会话"语义打架，而 myco 恰恰要切）；②旧 myco 已是"叠层+退一层"（`LEG §0`：settings 叠在 chat 上、bin 叠在 settings 上、`settingsNav/binNav` 各自 exit），迁移成本最低；③浮层数组无 pop 语义（见方案 2）。代价：无现成源码可抄，栈行为全部要自建（记录在 §A.3） | ⏸ 待拍（Q-BL-01） |
| `base.layout.route` | 无 | 路由页全屏 `fillMaxSize`，页面状态由 `MaterialPageRoute` 保活；返回键入口 44（`GO §2.15` 字段 1/3） | 栈项全屏、上一页仍在组合树 + 全屏 Box 吞指针（`TaiXuNavHost.kt:161-184`）；状态由 `rememberSaveableStateHolder` 保活（`TX §2.1 nav-stack` 字段 6） | `screen` 状态 + 每会话滚动复用（`MA:238-239`） | — | 采纳"全屏 route + 被覆盖层吞指针"（太墟 E0 做法）。宽度由 route 自己声明（见 §C.4），**不**把宽度写死在 host。反证：若 host 统一写全屏，侧栏抽屉就无法半宽（与旧外观冲突） | ✅ 采用建议 |
| `base.layout.route.scrim` | 列模式无遮罩，全屏右栏 z 40（`DSH §0-7`） | scrim 随 progress 淡化（`GO §2.15` 字段 6） | 覆盖态 `alpha=0.32*progress`；**同时存在 0.12 与 0.32 两套**（`TX §2.1 drawer` 字段 6 自评不一致，Q-TX-12） | Material 抽屉默认遮罩（alpha 未取证） | —（C-BP-02 是"判据"，不是"有无"） | **采纳"随 progress 线性、单一 token 0.32"**：0.32 有三家证据（太墟覆盖侧栏、M5 §3.3、mobile-adapt 0.35 同量级）；0.12 是太墟 reveal 抽屉的另一个值，属其内部不一致（**缺陷，不作为另一种解**）。全屏不透明面板**不画遮罩**（mobile-adapt 为全屏设置面板去掉 mask 并给了理由：多一层 backdrop-filter 白花成本，`M/:719-737`）。点击关闭判据引用已登记 **C-BP-02**（按下+松手都在遮罩上），归 M1 定义、本文件只引用 | ✅ 采用建议（token 归 `base.theme`） |
| `base.layout.route.back` | 无返回栈 → 无返回钮语义 | 二级页返回键 44 浮动玻璃、常驻（`GO §2.15` 字段 3） | 顶栏返回箭头由各页 `onBack` 传入（`TX §2.1 nav-stack` 字段 4） | 有（`topbar-back`，48dp；设置/bin 用 `settingsNav/binNav`，`LEG §2.7`） | — | 采纳"每层顶部有显式返回入口"，命中盒按 **G-11** 取 `F`（`F=48` 主档，见 C-TX-02 的分层取值建议），视觉可 24–28dp；`aria-label` 必给（旧 myco「‹」无 contentDescription 是缺陷，`LEG §2.7` 字段 11）。反证：只有手势/系统返回时，大屏/键盘用户找不到退出路径（`LEG §2.7` 同款反证） | ✅ 采用建议 |
| `base.layout.route.drag-dismiss` | 无 | 跟手拖动 + 松手按速度弹簧落位（`GO §0-8`、`HomeShell`） | `anchoredDraggable` + spring NoBouncy/MediumLow；已开时始终允许关（`TX §2.1 drawer` 字段 4） | **有**：>22% 或 v>600px/s 提交；退出动画 180ms；三通道共享 progress（`LEG §2.8`、`PN:34-114,84-96`） | **C-BL-01**（物理：固定 180ms vs 速度驱动） | **采纳"跟手 1:1 + 释放速度驱动 + 22%/600dp·s⁻¹ 提交阈值"**：阈值直接用旧 myco 的 E0（用户已习惯且是唯一有数值的手机实现）；动画换成 `settle` spring（`M5 §2.7`：旧 tween 正是用户抱怨"手停了还会顿一下"的根因）——**C-BL-01 选 spring**。反证：固定 180ms tween 在松手速度不为零时速度不连续，就是用户已抱怨的症状 | ⏸ 待拍（Q-BL-06） |
| `base.layout.route.drag-open` | 无 | 抽屉边缘手势区整屏、跟手开/关（`GO §2.15` 字段 3、`GO §0-8`） | **仅聊天根页可滑开**；覆盖页禁用（`MainPageNavigation.kt:7`、`TaiXuNavHost.kt:112`） | **有**：聊天正文右滑 ≥96dp 开抽屉、**只观察不消费**（`LEG §2.8`） | **C-BL-03**（打开手势"只观察" vs "消费跟手"） | **采纳"仅根层可开 + 内容区拖拽 + 过阈值才接管"**：门槛用旧 96dp（≈22% × 390dp，和退出阈值同源）；开放权限按太墟 `canSwipeOpenNavigation`（`MainPageNavigation.kt:7`）——深层页面里横滑是内容手势（选字/横向滚动），不能让抽屉抢走。**不采用屏幕边缘侧滑**（会与系统返回手势争边，见 §C.5）。反证：任意层都能滑开 → 在设置页横滑也会拉出面板，用户永远不知道自己会触发什么（太墟 §2.1 drawer 字段 13 反证同款） | ⏸ 待拍（Q-BL-03） |
| `base.layout.route.predictive-back` | 无 | Android 预测性返回（`GO §2.15` 字段 4） | **未见实现**（`TX §2.1 nav-stack` 字段 4；§6.6 不可迁移 4 条未含此，故按"太墟不做"处理） | **有**：`PN:34-114` 三通道（按钮/系统手势/右滑）写同一 progress | — | **采纳**：栈顶 route 提供 `progress(0..1)`，系统返回手势期间只改 progress（面板位移），松手由系统决定 commit/cancel；commit 后 pop 并播反向 spring。理由：旧 myco 已有现成 progress 源，不接等于丢掉已实现的体验；G-10 的"≤120ms 淡入淡出"不适用于位移转场（G-10 只约束高度动画）。反证：不接预测式返回 → 用户手势期间画面不动，松手才跳（Android 13+ 上明显"廉价"） | ✅ 采用建议（版本降级见 Q-BL-07） |
| `base.layout.back.dispatcher` | 无 | **`PopScope` 顺序**：右面板 → 结束多选 → 关抽屉（`GO §0-8`、`HS:319-333`） | **7 级优先链**：虚拟屏全屏 → 页内注入 BACK → 浏览器页 → 关伴随面板 → 关抽屉 → `popBack` → 栈底回智枢（`TX §6.3`）；实现=每层自己 `BackHandler` + 栈兜底（缺点：顺序隐式依赖组合顺序，`TX §6.3` 末段自述） | 设置/bin 各自 exit + 抽屉打开时系统返回=预测式关闭（`LEG §2.7/§2.8`） | — | **采纳"单一 dispatcher + 显式深度表"**（不采纳各层自称的隐式 `BackHandler` 顺序）：把浮层/面板/抽屉统一注册为 `(depth, kind)`，按 depth 降序消费。理由：太墟自述隐式组合顺序是缺点（`TX §6.3` 末段原文），myco 有 Infinite 层，必须显式排序；dsh-Go 的 `PopScope` 顺序是"单层的 when 穷举"，层数一多会失控。反证：多个 `BackHandler(enabled=true)` 同时存在时，谁最后组合谁赢——栈深 ≥2 时返回会随机关错层 | ✅ 采用建议（链内容见 §C.3） |
| `base.layout.stack-lifetime` | 右栏整列平移隐藏、**内容保持挂载**（避免重载丢失终端/文件树状态，`SidebarRight.module.css:8-18,44-62`） | 抽屉/面板是 overlay，关掉是否保活未写（**未取证**） | 隐藏抽屉保持组合（"第一次拖动不在帧内构建列表"，`RuntimeRevealNavigationDrawer.kt:145`）+ `rememberSaveable`/`movableContentOf` 迁移（`TX §0-5`） | 每会话滚动状态复用（`MA:238-239`；切会话不重挂载亦见 `DSS §1.2-D` 指针） | — | **深度 0–1 保活、深度 ≥2 冻结**（`【我方设计】`，见 §C.6）：证据只支持"1–2 层必须保活"（三家）；"无限层全保活"没有任何实现可抄（dockkit floats 无上限但全可见、不是栈）。反证：全保活 → 层数越深内存线性涨、每层都在重组；全冻结 → 抽屉第一次拖动才构建列表（太墟注释明写这会掉帧，`RuntimeRevealNavigationDrawer.kt:145`） | ⏸ 待拍（Q-BL-05） |
| `base.layout.a11y.covered` | overlay 层不拦指针（`L/AppFrame.module.css:281-290`），无"被覆盖层语义"概念 | `ExcludeSemantics` 屏蔽底层（`GO §2.15` 字段 11） | 隐藏层 `clearAndSetSemantics`（`TX §0-5`，`RuntimeRevealNavigationDrawer.kt:149,160`）；但**页面级只吞指针未清语义**（`TX §4 Q-TX-11` 自评缺口） | **无** | — | **采纳"覆盖即屏蔽语义"**：任一层被全宽覆盖（或部分覆盖但不可点）时，下层必须 `clearAndSetSemantics`/等价物，且焦点不得进入。理由：两家同向（dsh-Go、太墟），且太墟自己把"页面级只吞指针"列为待修缺口（Q-TX-11）；G-08 的精神是"读屏必须与视觉一致"。反证：读屏焦点落到不可见图层 → 用户点了没反应且不知道自己在哪（mobile-adapt 抽屉无 focus trap 就是这一类缺陷，`MOB §3.1 drawer` 字段 11 自评） | ✅ 采用建议 |
| `base.layout.window-class` | **只有宽度断点 1024**，无高度断点（`DSH §2.6 frame` 字段 10） | `<600 / 600–840 / ≥840`，**高<480 一律 compact**（`GO §2.15` 字段 2）；判据用"最短边"（`GO §2.15` 字段 13 反证） | `600/840dp`（`RuntimeWindowSize.kt:15-31`） | 手机抽屉 / 宽屏常驻（`MA:547-587`），断点未取证 | **C-BL-02**（600 / 767 / 840 / 1024 互斥） | **采纳 dsh-Go 的"三档 + 高<480 强制单栏"**：`<600` 单栏；`600–839` rail+覆盖抽屉；`≥840` 常驻列。理由：①手机横屏（915×412）按宽度会被误判成平板——dsh-Go 用高度兜底，这是唯一解决该问题的 E0（`GO §2.15` 字段 13 反证原话）；②dsh-Go 与太墟在 600/840 上一致，DSH 的 1024 是桌面鼠标密度下的值（`DSS §0.2-③`：窄屏靠挤压中心列，手机上会把会话压到 0）；mobile-adapt 的 767 是 CSS 适配层产物。代价：三档比两档多一套测试矩阵（见 Q-BL-08） | ⏸ 待拍（Q-BL-08） |
| `base.layout.shell` | 三列 CSS Grid：`sidebarPx minmax(center) rightbarPx`，行 `1fr auto`（`L/AppFrame.module.css:1-8`、`AppFrame.tsx:265-266`） | `AdaptiveScaffold` 三栏（左/聊天/右），聊天列最小 400（`GO §2.15` 字段 2） | `RuntimeAdaptiveNavigationScaffold` 三态（rail/覆盖/停靠）包 `NavDisplay`（`TX §0-3`、§6.1 结构图） | 单 Activity 全屏 + 层叠（无列） | —（C-10 的母体） | **采纳"宽屏三列 + 窄屏单栏覆盖栈"**：列只在 `window-class` 判宽时存在；窄屏把侧栏/右栏都渲染成覆盖 route（不挤压主列）。理由：①C-10 不是真冲突，是**断点分工**——dsh-Go 同时实现了"手机覆盖 + ≥840 常驻"（`GO §0-8`、`AdaptiveLayout`），太墟同时实现"Compact 覆盖 + Expanded 停靠"（`TX §0-3`），证明 coexists；②窄屏挤压会把主列压到 0（`DSS §0.2-③` 明写可压到 0），手机不可用。反证：把 DSH 的"挤压"照搬到手机 = 聊天列被压没（太墟 §2.1 drawer 字段 13 同款反证） | ✅ 采用建议（C-10 收敛） |
| `base.layout.sidebar-column` | 280 默认 / 56 折叠 / 0 隐藏；264–420 可拖；窄屏挤压中心列（`DSS §0.2-①②`、`columns.ts:13-23,52-58`） | 320 固定，rail 68；≥840 常驻（`GO §2.15` 字段 2） | 320 停靠，rail 84；Expanded 可收起为 rail（`TX §2.1 drawer` 字段 2） | 宽屏常驻（宽度未取证） | **C-10** | **采纳"列族参数借 DSH、是否常驻借两家手机"**：列宽 `264–420 clamp、默认 280、rail 56、0`（DSH E0 唯一带 clamp 的实现；C-10 已定"宽屏可借 DSH 列宽"），≥840 常驻、其余覆盖。**不做列宽拖拽**（8px 手柄远低于 G-11 的 F，触屏不可用，`DSH §2.6 column-resize` 字段 3/10 自评 + Q-BASE-04）→ §4。反证：取 320 固定则 1024 屏侧栏占 31%，且无法按内容调；取 DSH clamp 则至少给用户一个可调范围（若将来做拖拽） | ✅ 采用建议（列宽细节归 `sidebar` spec） |
| `base.layout.slots.overlay` | `shell.overlay` 整层 `pointer-events:none`，子元素 `auto` → 浮层不挡点击（`L/AppFrame.module.css:281-290`） | 无同名槽；overlay 面板直接进 Stack（`GO §2.15` 字段 7） | 无同名槽（浮层用各组件自己的层级） | 无 | — | **采纳 DSH 的穿透槽**：所有非布局浮层（Toast、提示、悬浮按钮、菜单锚点）挂在 `shell.overlay`，槽本身不拦指针。理由：G-01（浮层不占位）需要一个"不改变布局、不挡点击"的挂载点；这是 G-01 在 Web 端的实现方式（`DSH §2.6 frame` 字段 3 原话）。反证：无穿透槽 → 空浮层区域挡住下层点击（mobile-adapt 右栏全屏透明层就用 `pointer-events:none/auto` 手动纠这个，`M/:241-261`） | ✅ 采用建议 |
| `base.layout.slots.right-rail` | 网格第 3 列：min 300、max 70% 视口、首开 45%；<300 时轨道消失（`L/columns.ts:11-29,52-58`）；内容保持挂载（`DSW §0-5`） | 右面板 320–420（`panelFraction 0.3`）；窄屏 overlay（`GO §2.15` 字段 2、§2.7 quick-panel 字段 7） | 伴随面板 Pager/Overlay/Docked（≥720dp 才真分栏，比例 0.35–0.65，`TX §2.1 adaptive-panes`） | 无 | **C-19**（已澄清） | **采纳"列 vs 全屏浮层"按宽度二选一**：宽屏=列（300/70%/45%，内容保持挂载），窄屏=全屏覆盖 route（不拦指针的透明层兜底，`M/:237-264`）。理由：C-19 已澄清"参与布局流的列不属 overlay"（G-01 边界），所以同一种右栏在两种窗口下分属不同层族是合法的；dsh-Go/太墟 都按宽度切换形态。反证：手机宽度下保留列 → 轨道被移除（`columns.ts:52-58`）导致功能不可达，mobile-adapt 记录的 bug 原话是"右栏被整体隐藏→功能不可达"（`MOB §2.7 #4`） | ✅ 采用建议（细节归 `base.sidebar-right`） |
| `base.layout.z-ladder` | **显式 z 阶梯**：右栏 10 → 列宽手柄 11 → `shell.leading` 15 → 框架 overlay 20（穿透）→ 全屏右栏 40；modal 1000、lightbox 1000、portal menu 1100（`DSH §0-7`）；dockkit 另开 10/60/70（`DSH §0-8`） | 未写 z 常量（层叠靠组件树顺序 + `Transform` paint-only，`GO §2.15` 字段 6） | 未写 z 常量（Compose 默认 draw order；`clearAndSetSemantics` 而非 z） | 无 | — | **采纳"显式阶梯 + 栈内用渲染顺序"**：跨家族用常量（列 10 / 框架浮层 20 / **面板栈 40** / modal 1000 / menu 1100），栈内部**不用数字**、按列表顺序渲染（借 dockkit "`floats` 数组序即 z、抬升=移到末尾"的不变量，`DSH §0-8`、`D/FloatLayer.tsx:177`）。理由：dockkit 的反证明确——"z 直接用 style 写而不由模型数组决定，抬升后一次重渲染就会打回旧值"（`D/FloatLayer.tsx` 字段 13）。反证：无限栈若给每层写 `zIndex=40+i`，层数一多就与 modal/menu 家族撞号，且 Compose 里没有全局 z 排序可用 | ✅ 采用建议（常量表见 §C.6） |

---

## 3. 补充项（不互斥的差异，不进对照表的冲突列）

| # | 节点 / 事实 | 谁有、谁没有 | 是否采纳 |
|---|---|---|---|
| 3-1 | 抽屉/面板隐藏时"保持组合"（避免首拖构建列表） | 太墟有（`RuntimeRevealNavigationDrawer.kt:145`）；dsh-Go/DSH 未写 | **采纳**：栈顶-1 层保持组合；深度 ≥2 用 saveable 快照（见 §C.6） |
| 3-2 | 跨断点迁移不重建（`movableContentOf`） | 太墟有（`TX §0-5`）；其余未写 | **采纳**：宽屏列 ↔ 窄屏覆盖切换用 `movableContentOf` 包同一内容，不动状态；这是折叠屏/分屏刚需 |
| 3-3 | 被覆盖层吞指针 | 太墟有（`TaiXuNavHost.kt:172-179`）；dsh-Go 用 `ExcludeSemantics`（`GO §2.15` 字段 11） | **采纳**：吞指针 + 清语义都要（太墟只吞指针是它自评缺口，不能只抄一半） |
| 3-4 | 面板位移必须 paint-only（`Transform`/`graphicsLayer`），不用每帧重布局 | dsh-Go 有（`GO §2.15` 字段 13 反证）；DSH 用 CSS transform | **采纳**；Compose 用 `graphicsLayer.translationX` 或 `offset` 的绘制层，禁止每帧改 `padding/width` |
| 3-5 | 抽屉/面板开合前先收键盘 | 太墟有（`RuntimeRevealNavigationDrawer.kt:106-109`）；mobile-adapt 靠 `visualViewport` 重测 | **采纳**：打开任何面板前 `keyboard.hide()`；理由：键盘下打开全屏面板会把输入区夹在中间（`GO §2.5` 字段 7 `resizeToAvoidBottomInset:false` 由页面自己管，myco 若不收键盘会出现"半屏键盘 + 半屏面板"） |
| 3-6 | 滚动条用 `transparent` 重绑保住 gutter | DSH 有（`DSS §0.2-⑤`、`SidebarRoot.module.css:120-131`） | **采纳**：抽屉/侧栏列表沿用；否则指针进出时整列横移 8–15px（G-02 同类事故）。Compose 侧等价物=预留滚动条槽（`LEG §2.6` 亦未取证，需 P4 实测） |
| 3-7 | 抽屉关闭路径 = 汉堡/遮罩/Esc/导航后自动收 + 500ms 守卫 + 60ms 延迟 | mobile-adapt 有（`M/:1274-1339,1287-1293,1295-1301`）；太墟只有手势/遮罩/返回（`Q-TX-05`）；dsh-Go 有返回/遮罩 | **部分采纳**：返回/遮罩/自动收 + 500ms 防连关（采纳）；**Esc 不适用**（无物理键盘主场景，但宽屏可保留）；60ms 延迟采纳（让 pressed 反馈先出现） |
| 3-8 | reveal 抽屉"前景整体平移"（配对滑动） | 太墟 Compact 形态（`RuntimeRevealNavigationDrawer.kt:53-56`）；dsh-Go/mobile-adapt 不用 | **不采纳进首版**（→ §4）：与用户"外观与旧结构一致"冲突（旧结构里聊天底不动） |
| 3-9 | 遮罩值多源不一致（0.12 / 0.32 / 0.35） | 太墟内部两套（`Q-TX-12` 自评）；mobile-adapt 0.35 | **不采纳不一致**：统一 0.32 单 token（见 §2 scrim 行）；0.12 属缺陷，不作解 |
| 3-10 | 手势权限按栈顶分配 | 太墟有（`MainPageNavigation.kt:7`）；dsh-Go 未写 | **采纳**：`canSwipeOpen = 栈深==0`（首版），深层禁开（见 §F Q-BL-03） |
| 3-11 | 减少动画全局生效 | 四家都有（DSH `AppFrame.module.css:52-56`；GO `motion.dart:182-186`；TX `RuntimeAdaptiveMotion`；MOB `M/:1091-1101`） | **采纳**：`prefers-reduced-motion`/Android「移除动画」→ 位移转场改 ≤120ms 淡入淡出或不播（与 G-10 的降级要求同源） |
| 3-12 | 宽屏内容列有最大宽度（太墟 `UI_GUIDELINES.md:47` "内容 840 居中"） | 太墟有（`TX §6.6` 可迁移 6）；DSH 未写 | **采纳原则，常量待定**：宽屏主列建议设最大阅读宽（具体 840 是太墟文档值，E1），归 `base.theme`/`chat` spec；本文件只声明"宽屏主列可封顶" |
| 3-13 | 左侧系统边缘手势保留 | 旧 myco 有（`LEG §2.8`：左缘 20dp 让系统）；dsh-Go 二级页 20dp 边缘返回；太墟用整条左缘开抽屉 | **采纳旧 myco 的 20dp 让系统**（见 §C.5）；这与"屏幕边缘侧滑开抽屉"互斥 → Q-BL-03 |
| 3-14 | 返回键在栈底的行为 | dsh-Go 栈底回智枢（`GO §0-8`）；太墟栈底再按回 Agent（`TaiXuNavHost.kt:190`）；旧 myco 栈底开草稿/退出（`LEG §2.7` 字段 10） | **采纳 dsh-Go/太墟式"栈底回根"**：首版栈底=聊天根，返回=退出 App 前先回根；是否二次确认 → Q-BL-09 |

---

## 4. 明确不做（参考里有的）

| 能力 | 出处 | 不做的理由 |
|---|---|---|
| dockkit **分栏树 / 拆分 pane / 停靠上限 4 / 最小份额 12%** | `DSH §0-8, §2.6 dock-pane`；`E/constraints.ts:10-13` | 用户明确"不做并排拼贴"；myco 首版没有"同屏多工作区"需求。太墟的 Docked 伴随面板（≥720dp 0.35–0.65）同样不做（首版无 terminal/browser/twin 面板） |
| dockkit **浮窗**（380×300、最小 220×140、拖动/缩放/抬升） | `DSH §0-8, §2.6 float-panel`；`E/constraints.ts:16-19` | 浮窗语义是"多窗并存"，与"层叠覆盖/返回"冲突；且 dockkit 浮层**没有任何边缘露出实现**（`DSH §4 Q-BASE-02 解 B` 明写"完整矩形，无边缘露条"），抄不到用户要的抽屉外观 |
| dockkit **标签芯片 / 右键菜单 / 页签拖拽 / 页签横向滚动** | `DSH §2.6 dock-tab`、`dock-tab-close`、`dock-tab-strip-controls`；`D/TabPanel.tsx:240-404` | 无多标签需求；右栏页签归 `base.sidebar-right`（首版可能只要 1 个 tab） |
| **列宽拖拽手柄**（8px 宽） | `DSH §2.6 column-resize`；`L/AppFrame.module.css:217-233` | G-11：8px 远低于 `F`，触屏几乎不可命中（`Q-BASE-04` 已登记）；且旧 myco 无此能力、用户未要求。列宽改为"断点 + 默认值"，不做连续拖拽 |
| **ui-slots 插件槽**（register/abdicate/唯一渲染权） | `DSH §1`（`ui-slots` 包） | 无插件体系；只借"overlay 槽"一个实现（`slots.overlay`），不引入注册/让位协议 |
| **DSH 三平台折叠形态**：Windows caption 模式（0 宽、toggle 进标题栏）、macOS `shell.leading` 席 | `DSS §0.2-②`；`AppFrame.module.css:207-215`、`SidebarRoot.module.css:39-55` | 平台不存在（Android-only）；myco 折叠统一为"rail 56 + 0"两档 |
| **太墟 Compact `reveal` 抽屉**（前景页整体横移） | `TX §0-3`；`RuntimeRevealNavigationDrawer.kt:53-56` | 用户要求"最终外观与旧结构一致"：旧结构里聊天页在抽屉打开时**不位移**（Material 抽屉是覆盖式）；reveal 是另一种外观，不采纳 |
| **太墟 companion panes 四模式**（Pager/Overlay/Docked/None） | `TX §2.1 adaptive-panes` | 依赖 terminal/browser/twin 面板，首版范围外（`10-panels.md` 无这些面板） |
| **太墟 A2UI 协议本身**（`render_surface`/A2UI v0.9/19 类 Catalog） | `TX §5` 全节；`Q-TX-07` | 协议是"模型生成内容"的机制，不是"面板邻接"机制；首版面板内容原生渲染。只借其**四层校验/白名单/原文兜底**原则（见 §B.4）→ 是否引入协议见 Q-BL-12 |
| **dsh-Go 右侧快速面板**（模式/网页/设置入口） | `GO §2.7 quick-panel` | myco 注册表无"快速面板"；其入口能力已由侧栏 + 设置覆盖；新增会与"不做并排拼贴"冲突 |
| **dsh-Go iOS 式 20dp 边缘页面返回手势** | `GO §2.15` 字段 4、`PT:428-442` | Android-only：页面返回用系统预测性返回；iOS 式边缘手势在 Android 上与系统返回手势重复且冲突（左缘 20dp 已让系统） |
| **dockkit 浮窗"只有 140ms 遮罩淡入、无位移/尺寸动画"** | `DSH §0-8`；`D/dockkit.module.css:610-621` | 该约束属于"浮窗"家族；myco 抽屉/面板必须滑动（旧外观 + 手机端四家都滑动），两者不是同一家族。不做浮窗即不适用 |
| **DSH 列宽手柄 z=11 / `shell.leading` z=15** | `DSH §0-7`；`L/AppFrame.module.css:211,231` | 无列宽拖拽、无窗口 chrome 席 → 两个 z 槽位不存在；保留编号空隙以语义稳定（见 §C.6） |

### 4.1 参考实现自身缺陷（按 `_conflicts.md` 判定口径：**不作为"另一种解"**，也不抄）

> 判定依据：`_conflicts.md` §判定口径（"参考实现的缺陷不是另一种解"）。以下每一条都在事实库里被自评或有代码证据，列出是为了**明确不采纳**，不是与上述方案并列。

| 缺陷 | 出处 | 我们的处理 |
|---|---|---|
| mobile-adapt 文档声称 "sidebar becomes a swipe drawer"，**代码无任何 touch 监听** | `MOB §0-4`、`R1/:6` vs `M/:1269-1353` | 不采纳"无侧滑"；myco 保留旧版 E0 的 96dp 内容区拖拽（§C.5） |
| mobile-adapt 抽屉**无 `role=dialog`/`aria-modal`/焦点陷阱**；汉堡无 `aria-expanded` | `MOB §3.1 drawer` 字段 11、hamburger 字段 11；§6 Q-MOB-14 | 不采纳；myco 必须补（Q-BL-10） |
| mobile-adapt 安全区 `env(safe-area-inset-*)` 在 DSH 页面**空转**（无 `viewport-fit=cover`） | `MOB §0-6`；`apps/web/index.html:5` | 不采纳其"写了但没生效"；myco 原生 insets 直接生效（§C.7） |
| mobile-adapt 伪元素扩边**无相邻隔离**，重叠带最大 24px；字号 stepper 因此必须重排 | `MOB §0-3`；`M/:934-945,802-865` | 不采纳无隔离扩边；命中区一律走 G-11（C-TX-02 已收口，本文件不另立） |
| 太墟**页面级被覆盖层只吞指针、未清语义**（TalkBack 仍可能聚焦） | `TX §4 Q-TX-11`；`TaiXuNavHost.kt:172-179` | 不采纳；`a11y.covered` 要求"吞指针 + 清语义"一起做（§2 该行） |
| 太墟两套遮罩值不一致（0.12 / 0.32） | `TX §4 Q-TX-12`；`RuntimeRevealNavigationDrawer.kt:163-166` vs `RuntimeAdaptiveNavigationScaffold.kt:169-172` | 不采纳不一致；统一 0.32 单 token（§C.2） |
| DSH 全库**无触控下限**（24/16/28px） | `DSH §0-10` | 不采纳；命中区走 G-11 + C-TX-02 的分层 `F` |
| 旧 myco `topbar-back` 的「‹」**无 contentDescription** | `LEG §2.7` 字段 11 | 不采纳；`route.back` 强制 `aria-label`（§2 该行） |

---

## 5. 新增节点（参考里都没有、myco 要新设计）

> 口径：前 4 行是**新增节点**；后 2 行是**已有节点的新增契约 / 扩展带**（不是新节点，列在此处是因为"契约本身"无参考）。

| 节点 ID | 为什么需要 | 依据（用户要求 / 平台规范 / 我方约束） | 代价 |
|---|---|---|---|
| `base.layout.panel-graph` | "接口是抽象数据"是用户硬要求；四家都没有邻接数据（`TX §6.2` 明写"邻接关系：未见实现"） | 用户原话（每张卡片有滑动接口、接口是抽象数据）；`M5 §3.1`（图是纯数据，M6 只改图）；太墟 §5 证明"数据+白名单+渲染"可行（`TX §5.6`） | 多一层注册/校验；图配错会出现"点不进/进错"（用启动期校验兜底） |
| `base.layout.stack-lifetime`（深度策略） | 无限层必须有内存策略；refs 只覆盖 1–2 层保活（`DSW §0-5`、`TX §0-5`） | 用户"无限抽屉"；G-00：refs 无"无限层"实现 → 我方设计并标注 | 深度 ≥2 恢复时需 saveable 快照，滚动/输入草稿可能丢（P4 验证） |
| `base.layout.a11y.covered` | 被覆盖层必须对读屏隐藏；四家中只有两家的局部做法（dsh-Go `ExcludeSemantics`、太墟 `clearAndSetSemantics`） | G-08 精神（读屏与视觉一致）；`TX §4 Q-TX-11` 自评缺口 | 每层进出都要改语义树，层级切换时可能引起 TalkBack 焦点跳（需真机） |
| `base.layout.z-ladder`（面板栈常量带） | DSH 有阶梯但无"栈"这一带；dockkit 有"数组序"但无跨家族常量 | `DSH §0-7/8` 证据 + 我方栈模型；反证见 `D/FloatLayer.tsx` 字段 13 | 常量一旦定错要全局改；先按 40 留出与 modal(1000)/menu(1100) 的空间 |
| `base.layout.route.predictive-back`（统一 progress 契约） | 旧 myco 已有 3 通道 progress，但 refs 里 dsh-Go 只写"用预测性返回"、太墟没做 | `LEG §2.8/PN:34-114`（E0 现状）+ `GO §2.15` 字段 4（E0 用法） | 预测式返回在 API<33 无系统手势，需要降级（Q-BL-07） |
| `base.layout.slots.overlay` 的"面板栈带"扩展（`shell.overlay` 之上、modal 之下） | DSH 的 overlay 是"框架级穿透层"（z 20），没有"面板栈"这一带 | `DSH §0-7` + 我方栈；反证：面板若与 modal 同带，菜单/弹窗会被面板盖住 | 需与 base.primitives 约定 modal/menu 两个家族号（1000/1100）不占用 40 带 |

---

## 6. 待确认（阻塞项）

> 全部为 `Q-BL-nn`，**两解及后果详见 §F**；本表只是一句话索引。未拍板不得进 P3。

| ID | 问题（一句话） | 阻塞的节点 | 我的建议 | 状态 |
|---|---|---|---|---|
| `Q-BL-01` | 层叠模型三选一：覆盖栈 / 浮层数组 / 混合（C-11、Q-BASE-02） | `panel-host`、`route`、全部 | 方案 1「覆盖栈」 | ⏸ 待拍（§A） |
| `Q-BL-02` | 是否存在"静止半开 / 露边"状态，还是只在拖拽中露边 | `route`、`scrim`、`drag-dismiss` | 静止只有 0/1 两态，拖拽中才露边 | ⏸ 待拍（§C.1） |
| `Q-BL-03` | 打开手势：屏幕边缘 / 内容区拖拽 / 仅按钮；是否消费手势（C-BL-03） | `drag-open`、`window-class`、`back.dispatcher` | 内容区拖拽 + 过阈值接管 + 仅根层；边缘留给系统 | ⏸ 待拍（§C.5） |
| `Q-BL-04` | 下层面板是否缩放/压暗/模糊（M5 §3.3 设想 vs "外观与旧结构一致"） | `route`、`scrim`、`shell` | 不缩放、不模糊，只做 0.32 遮罩 | ⏸ 待拍（§C.2） |
| `Q-BL-05` | 无限层保活策略（全保活 / 顶 2 层保活 / 全冻结） | `stack-lifetime` | 深度 0–1 保活，≥2 冻结 | ⏸ 待拍（§C.6） |
| `Q-BL-06` | 转场物理与阈值：spring+22%/600 vs 固定 340/300/280/180ms（C-BL-01） | `route.drag-dismiss`、`predictive-back` | spring + 旧阈值 | ⏸ 待拍（§C.8） |
| `Q-BL-07` | 预测性返回的启用范围与 API<33 降级 | `predictive-back` | 13+ 启用，低版本瞬间 pop | ⏸ 待拍（§C.5） |
| `Q-BL-08` | 断点/列宽/宽屏形态与"双栏是否进返回栈"（C-BL-02） | `window-class`、`sidebar-column`、`slots.right-rail` | 600/840 + 高<480 单栏；列不进栈 | ⏸ 待拍（§C.4） |
| `Q-BL-09` | 返回优先级链 + 栈底行为（是否二次确认退出）+ 面板内浮层归属 | `back.dispatcher`、`slots.overlay` | 单一 dispatcher、浮层在上、栈底回根 | ⏸ 待拍（§C.3） |
| `Q-BL-10` | 抽屉关闭路径集合 + a11y 语义修正（role/focus/aria-expanded） | `drag-dismiss`、`scrim`、`a11y.covered` | 返回/遮罩/拖出/自动收；补 dialog 语义与焦点陷阱 | ⏸ 待拍（§C.3） |
| `Q-BL-11` | 安全区与 edge-to-edge 策略（Q-MOB-12） | `window-class`、`shell`、所有面板 | edge-to-edge + 全量 insets | ⏸ 待拍（§C.7） |
| `Q-BL-12` | 是否引入 A2UI/`render_surface` 协议作为面板内容接口（Q-TX-07） | `panel-graph`、`route` | 只借"白名单+四层校验"，不引协议 | ⏸ 待拍（§B.5） |
| `Q-BL-13` | "可组合"边界：首版图是否可外部配置/模型生成 | `panel-graph` | 首版数据内置、白名单强校验；编辑器归 M6 | ⏸ 待拍（§B.4） |

---

## 7. 自检

- [x] 每个节点在对照表里有一行（17/17；结构契约节点也在表内并注明无命中区）。
- [x] 每条「建议」都引了 G 规则或给了反证（G-01/G-02/G-08/G-11/G-00b 及逐行反证；架构方案是 `【我方设计】`+理由+代价）。
- [x] 没有 E2 推测出现在 §2/§3：未取证处写「未取证」（旧抽屉视觉、dsh-Go overlay 关后保活、dsh-Go 抽屉几何细节）。
- [x] 明确不做项都写了理由（§4 主表 13 条 + §4.1 参考实现自身缺陷 8 条，均写明为什么不抄）。
- [x] 阻塞项已编号（`Q-BL-01`…`Q-BL-13`，两解/后果见 §F）。
- [x] 未改 ref / 全局规则 / 冲突表 / open-questions / 其它 p2 文件 / M5 文档 / app；未 commit。
- [x] 冲突处理：C-10（断点分工，不成立为互斥）、C-11（由 §A 处理，选方案 1）、C-19（列 vs 全屏浮层，已澄清）均有落点；新冲突 `C-BL-01/02/03` 待 Lead 收进 `_conflicts.md`。
- [ ] **未决（需 Lead）**：① `_conflicts.md` 的"待补入"段落仍写着 dsh-go/taixu 未产出（已过时）；② `taixu.md §0-6` 说"5 级优先链"而 `§6.3` 列 7 级（事实库自述口径不一致，建议 Lead 修，不影响本文件——本文件按 §6.3 行级证据引用）；③ chat-demo 未进 M2 必读，本表无 chat-demo 列。

---

# §A 架构方案（给用户拍板用）

## A.0 四个样本的"层模型"（先看样本）

| 样本 | 层模型 | 能否支撑"无限抽屉" | 证据 |
|---|---|---|---|
| **样本 1：DSH `ui-layout`** | **单槽覆盖**：一个 `activePanelId`，换面板 = 替换；无返回栈、无手势返回 | ❌ 没有"层"的概念，"无限"无从谈起 | `DSH §0-6`；`L/stores.ts:16-22,94-96`；`L/service.ts:28-54` |
| **样本 2：DSH `ui-dockkit`** | **浮层数组**：`floats[]` 顺序即 z 序，按下抬升；停靠上限 4；无返回栈；浮层是完整矩形、**无边缘露出** | ⚠️ 有"无限个浮窗"，但没有"返回/退一层"，且外观是多窗不是抽屉 | `DSH §0-8`；`Q-BASE-02 解 B` 明写无露条；`D/FloatLayer.tsx:63-66,177` |
| **样本 3：dsh-Go** | **抽屉 overlay + 页面栈**：手机左抽屉（≥840 常驻列）+ `MaterialPageRoute` 二级页栈；手势/返回按层仲裁 | ✅ 有栈 + 有抽屉，但只有"1 个抽屉"，没有"从任意层再开任意层"的邻接 | `GO §0-8`、`§2.15`；`HS:249,319-333`；`AL:20-49` |
| **样本 4：太墟** | **单栈 + 4 覆盖根页 + 1 固定抽屉**：`NavDisplay` 唯一栈；Home/Workspace/Bots/Settings 作为覆盖页压进同栈、共享历史；抽屉内容硬编码 `SessionsSideDrawer`；最多并存=宽屏三栏 | ⚠️ 有"栈 + 覆盖根页"，但**抽屉只有 1 个、内容硬编码、无邻接、无多层同屏**；"层叠几何（每层偏移+缩放+圆角+阴影）"**未见实现** | `TX §0-1/2/3`；`§6.1/§6.2/§6.4/§6.6`；`ChatNavigationDrawer.kt:97-171` |

**结论（Lead 要的回答）：myco 的"无限抽屉"必须在【导航栈层】扩展。**
- 太墟证明了"单栈 + 覆盖根页"可行，但它的根页只有 4 个、全部静态注册、抽屉 1 个且内容硬编码（`TX §6.2` 三层能力缺口）。myco 要把这套结构**泛化两层**：
  1. 把"4 个固定覆盖根页"泛化为 **N 个可邻接的 `PanelRoute`**（邻接数据 = 用户要的"抽象接口"）；
  2. 把"1 个固定抽屉"泛化为 **任意边可挂的 drawer 型 route**（同一套栈，只是 `width/coverMode` 不同）。
- **不是**在"面板层"扩展：面板内容不感知导航（否则退化成太墟的 `drawerContent` 硬编码，`TX §6.2` 已判"能力缺口"）。
- **不是**在"浮层数组层"扩展：dockkit 的 floats 是"多窗并存 + 无 pop"（`Q-BASE-02 解 B`），拿它当导航模型等于自建栈 + 多一套浮窗机制（方案 2 的坏处）。

## A.1 三个候选方案

### 方案 1：覆盖栈（`List<PanelRoute>`，栈顶渲染 + 下层保活，返回 = pop）

| 维度 | 内容 |
|---|---|
| **数据结构** | `PanelGraph`（邻接表）+ `stack: List<PanelRoute>`；`PanelRoute{routeId, panelId, params, from, edge, width, cover, statePolicy, returnMode}`（字段定义见 §B） |
| **z 序规则** | 栈内按列表顺序渲染（后入者在上），**不写 z 数字**；跨家族用显式阶梯（列 10 / 浮层槽 20 / 面板栈 40 / modal 1000 / menu 1100） |
| **转场** | 从 `edge` 滑入（`translationX: ±W → 0`）；pop 反向滑出；拖拽/预测性返回期间 progress∈[0,1] 直接驱动位移；松手速度驱动 `settle` spring |
| **返回 / 预测性返回** | 返回 = `pop()`；系统预测式返回 = 系统手势 progress 驱动栈顶位移，commit 才 pop、cancel 回弹；返回优先级链见 §C.3 |
| **手势从哪进入** | 打开：显式入口（顶栏菜单/按钮）+ 根层内容区拖拽（≥96dp）；关闭：拖出/遮罩/返回。不新增"屏幕边缘"手势（边缘 20dp 让系统） |
| **多层内存 / 状态** | 深度 0–1 保活（组合+状态），≥2 冻结（saveable 快照，恢复时重建 UI）；流式/网络等业务状态在 store 层，不随 UI 冻结 |
| **坏处** | 无现成源码可抄（DSH 明确没有；dsh-Go/太墟都是"页面栈 + 单抽屉"，不是"可邻接多层栈"）；返回语义/深链/保活/预测式返回全要自建；与 Material `ModalNavigationDrawer` 的默认语义（含自带 scrim/手势/返回）需要替换或包裹，改动面大 |

### 方案 2：浮层数组（借 dockkit：z 由数组序决定、可抬升，不做分栏）

| 维度 | 内容 |
|---|---|
| **数据结构** | `floats: List<PanelInstance>`；无父子/无栈；点击抬升 = 移到末尾；`MAX` 不限（dockkit 只限停靠 4，不限浮动） |
| **z 序规则** | 数组下标即 z；抬升 = 移到数组末尾（`D/FloatLayer.tsx:177`） |
| **转场** | dockkit 现实：**只有 140ms 遮罩淡入，没有位移/尺寸动画**（`D/dockkit.module.css:610-621`）→ 与用户要的"抽屉滑入"外观不符，必须另加动画 |
| **返回 / 预测性返回** | **没有 pop 语义**（`DSH §0-8`/`Q-BASE-02 解 B`）："返回"只能变成"点下层抬升"或另建关闭栈；预测式返回没有对应物 |
| **手势从哪进入** | 无现成（dockkit 的拖拽是移动浮窗，不是开抽屉） |
| **多层内存 / 状态** | 全部"存在"且全部渲染（浮窗同时可见）→ 与"不做并排拼贴"直接冲突 |
| **坏处** | ①视觉是多窗不是层叠抽屉（用户明确不做并排）；②没有返回/退层，必须自建 → 等于方案 1 再加一套浮窗机制；③dockkit 浮层无边缘露出（`Q-BASE-02 解 B` 原话），"卡片露边"也得自研；④z 上限靠数组长度，层数多时管理成本高 |

### 方案 3：混合（导航层用栈、面板内浮层用浮层数组）

| 维度 | 内容 |
|---|---|
| **数据结构** | 两套：`stack`（导航）+ 每层面板内 `overlays: List<OverlayInstance>`（sheet/menu/dialog） |
| **z 序规则** | 两套 z 系统；必须写死仲裁：面板内浮层 > 当前面板 > 下层 |
| **转场** | 两套转场：面板位移 + 浮层淡入；同一帧可能同时跑（层退出 + 浮层退出） |
| **返回 / 预测性返回** | 两套返回语义 + 仲裁：浮层先、面板后；预测式返回只对面板有意义，浮层要么吃掉手势要么不接 |
| **手势从哪进入** | 面板手势 + 浮层手势（下滑关）两套；冲突面 ×2 |
| **多层内存 / 状态** | 栈层保活策略 + 浮层临时态；浮层层数不受控（每层可开多个） |
| **坏处** | **两套 z、两套返回、两套测试面**；G-00b 单一事实源会被破坏（"哪套是层叠真源"说不清）。**但**：如果浮层家族由 M1 `base.primitives` 的 modal/menu 槽承担（z 1000/1100 独立家族），则"混合"退化成"方案 1 + 职责边界"，不是两套导航——**这正是推荐方案 1 的一个附带条件**（见 A.3） |

## A.2 比较表（一眼选）

| 维度 | 方案 1 覆盖栈 | 方案 2 浮层数组 | 方案 3 混合 |
|---|---|---|---|
| 数据结构 | 邻接图 + `List<PanelRoute>` | `List<PanelInstance>`（无栈） | 栈 + 每层浮层数组（两套） |
| z 序 | 渲染顺序（栈内零数字）+ 跨家族常量 | 数组下标即 z，抬升移末尾 | 两套，需仲裁 |
| 转场 | 滑入/滑出 + 跟手 + spring | dockkit 只有 140ms 遮罩，要另加 | 两套同帧可能并跑 |
| 返回 / 预测式返回 | **原生 pop + progress** | **无 pop，必须自建** | 两套返回 + 仲裁 |
| 手势入口 | 按钮/菜单 + 根层拖拽 | 无现成抽屉手势 | 两套手势 |
| 多层内存 | 顶 2 层保活、深层冻结（可调） | 全部可见全部渲染 | 栈策略 + 浮层无上限 |
| 与"外观与旧结构一致" | ✅ 可（滑入覆盖 + 遮罩） | ❌ 多窗外观 | ✅ 可（若浮层只做弹窗） |
| 与"不做并排拼贴" | ✅ | ❌ 并存可见 | ✅（浮层是瞬态） |
| 与"无可见抓手" | ✅（无抓条，入口是按钮/手势） | ⚠️ 浮窗头部就是抓手（dockkit 头部整行=移动抓手，`D/FloatLayer.tsx:126-127`） | ✅ |
| 现有源码可抄度 | 低（无栈） | 中（z 序/抬升可抄） | 中 |
| 迁移成本（旧 myco） | **最低**（旧已是叠层+退一层） | 高（要重定义返回） | 中高（两套） |
| 结论 | **推荐** | 否决 | 否决做导航；其"职责分离"部分并入方案 1 |

## A.3 推荐：方案 1「覆盖栈」，并明确三条不变量

**推荐理由（四条，每条有据）**
1. **用户要求是"抽象邻接 + 层叠覆盖 + 无抓手"** → 栈是唯一同时表达"上一层/退一层/从哪边来/回哪去"的结构；浮层数组没有"层序"语义（`Q-BASE-02 解 B` 已取证"无返回栈"）。
2. **旧 myco 已经是"叠层 + 退一层"**（chat 底 / settings 从右滑入覆盖 / bin 叠在 settings 上，`LEG §0`；`settingsNav/binNav` 各自 exit，`LEG §2.7`）→ 方案 1 是**迁移成本最低**；方案 2 要把"退一层"改成"点下层"，用户预期会被破坏（`LEG §2.8` 系统返回=预测式关闭抽屉）。
3. **手机端两家一致指向栈**：dsh-Go 用页面栈 + `PopScope` 顺序（`GO §0-8`），太墟用单栈 + 7 级返回链（`TX §6.3`）；DSH 无栈是桌面语义（换面板不切会话，`L/stores.ts:18-20`），不适用于手机。
4. **z 序可只抄 dockkit 的一条不变量**：`z 由模型顺序决定、不直接写 style`（`D/FloatLayer.tsx` 字段 13 反证）——只抄这条，不抄它的 floats 家族（§4）。

**三条不变量（P3 可直接写进实现契约）**
- **I1：单一导航真源** = `stack`；任何"层"的存在/顺序只能由 stack 表达。面板内弹窗（menu/modal/sheet）**不进 stack**，属 `base.primitives` 的 z 家族（1000/1100），由 §C.3 的 dispatcher 统一登记（避免 G-00b 双真源）。
- **I2：栈内零 z 数字**；跨家族只用 §C.6 常量表。抬升（如"点下层浮层到最上"）只允许在浮层家族内发生，**禁止**在导航栈内"点某层就把它抬到顶"（那等于改变历史，返回语义会乱）。
- **I3：覆盖即隔离**：任何进入栈的 route，只要覆盖区 > 0 就吞指针；覆盖区 = 全宽时同时清语义（§C.3）。

**方案 1 的代价（必须接受，写进 P4 风险）**
- 无现成源码：栈行为、返回链、预测式返回、保活、无障碍全部自建；
- 与 Material `ModalNavigationDrawer` 的冲突：旧抽屉自带 scrim/手势/返回语义，替换为自建 host 后要自己补 a11y（role=dialog/焦点陷阱/aria-expanded）——这是旧 myco 与 mobile-adapt 都没做对的点（§C.3、Q-BL-10）；
- 图数据一旦上线，改图 = 改导航（需要 schema 版本）。

---

# §B 抽象接口的数据契约（用户最在意的部分）

## B.1 "抽象接口"在本工程里指三层，必须都定

| 层 | 是什么 | 谁消费 | 参考依据 |
|---|---|---|---|
| **① 邻接接口** | 图：`(fromPanel, edge) → toPanel`。描述"从哪张卡能滑出哪张卡" | 壳（`panel-host`）、手势/按钮入口 | 用户要求（接口是抽象数据）；四家都没有 → `【我方设计】` |
| **② 实例接口** | `PanelRoute`：一次打开产生的实例（携带参数、边、宽度、覆盖关系、保活、返回语义、滑动参数） | 壳 + 面板内容（只读） | 用户要求（滑动接口字段）；`M5 §3.1` 的 `PanelSpec` 只有 id/kind/edges，不够 |
| **③ 内容与事件接口** | 面板内容怎么描述、面板内操作怎么回传 | 面板内容 + 业务层 | 太墟 A2UI 是内容接口的现成范本（`TX §5`）；本文件**只定义边界**，内容 schema 归各面板 spec |

## B.2 字段级契约

### B.2.1 `PanelSpec`（每张卡一次定义，图的一部分）

| 字段 | 类型 | 必填 | 含义 | 依据 / 约束 |
|---|---|---|---|---|
| `specId` | String | ✅ | 面板类型 id，必须在注册表白名单内 | 借太墟 Catalog 白名单思想（`TX §5.6` L3）；`10-panels.md` 是白名单来源 |
| `kind` | enum | ✅ | `chat / sessions / settings / bin / workspace / ...`（首版 = 注册表已有面板） | `M5 §3.1` 的 `PanelKind` |
| `edges` | `Map<PanelEdge, String>` | ⬜ | 从本面板的哪条边能打开哪张卡：`LEFT/RIGHT/TOP/BOTTOM → specId` | 用户要求；`M5 §3.1` 同名字段（但 M5 只有 LEFT/RIGHT） |
| `presentation` | enum | ✅ | `ROUTE_FULL`（全屏 route）/ `ROUTE_DRAWER`（抽屉型 route）/ `DOCKED_COLUMN`（宽屏常驻列） | 旧 myco 只有前两种（`LEG §0`）；`DOCKED_COLUMN` 只允许宽屏（`DSS §0.2`、`GO §0-8`） |
| `widthSpec` | 见 B.2.2 | 抽屉/列必填 | 宽度规则（全宽/固定/比例/上限） | 手机抽屉 320+上限屏宽-56（`GO §2.7 sidebar.drawer` 字段 2）/ `min(320,86vw)`（`M/:268-292`）；太墟 `min(75%,360)` 是 reveal 形态，不采用 |
| `coverMode` | enum | ✅ | `OVER_OPAQUE`（不透明覆盖）/ `OVER_DIM`（覆盖+遮罩）/ `PUSH_COLUMN`（宽屏挤列） | 三家按宽度切换（`DSH` 列 / `GO` 覆盖+列 / `TX` 三态）；推挤**只允许**宽屏列 |
| `scrim` | enum | ⬜ | `NONE` / `DIM(0.32)`；默认由 `coverMode` 推导 | 单一 token 0.32（§C.2）；全屏不透明→NONE（`M/:719-737`） |
| `statePolicy` | enum | ⬜ | `KEEP` / `FREEZE` / `REBUILD` 的**覆盖值**；默认走全局深度策略 | 全局策略 = 顶 2 层 KEEP（§C.6）；`DSS §0.2-⑤`/`TX §0-5` 支持 1–2 层保活 |
| `returnMode` | enum | ✅ | `POP`（退一层）/ `TO_ROOT`（回根）/ `HAND_TO_GESTURE`（交还手势，用于预测式返回） | 旧 myco 的 exit 语义 = POP/TO_ROOT（`LEG §2.7`）；预测式 = HAND_TO_GESTURE（`PN`） |
| `edgesSwipe` | `Map<PanelEdge, SwipeSpec>` | ⬜ | 每条边的滑动接口（见 B.2.3）；缺省 = 只能按钮开 | 用户要求"每张卡有滑动接口" |
| `backDepthPolicy` | enum | ⬜ | `CONSUME_WHEN_VISIBLE` / `PASS_THROUGH`；缺省前者 | 太墟 7 级链（`TX §6.3`）的逐层登记形式 |
| `a11y` | object | ⬜ | `role`（dialog 等）、`label`、`focusTrap: Bool` | `MOB §3.1 drawer` 字段 11 的缺口（无 role/aria-modal/焦点陷阱）是缺陷，必须补 |
| `schemaVersion` | String | ✅ | 图/内容的 schema 版本；老数据降级 | 借太墟 `catalogId` 版本化（`TX §5.8`） |

### B.2.2 `WidthSpec`

| 取值 | 语义 | 依据 |
|---|---|---|
| `FULL` | 全屏宽（手机栈内默认） | 旧设置/bin 全页覆盖（`LEG §0`） |
| `MIN_MAX(min, frac, max)` | `min(minDp + leftInset, screenWidth × frac, screenWidth − maxInsetDp)` | dsh-Go `min(320 + 左侧安全区, 屏宽 − 56)`（`GO §2.7 sidebar.drawer` 字段 2）+ mobile-adapt `min(320px,86vw)` & `max-width: calc(100vw-56px)`（`M/:268-292`） |
| `FIXED(dp)` | 固定宽（宽屏常驻列） | 暂定 280/56（DSH 列族，§C.4） |

### B.2.3 `SwipeSpec`（用户点名的"滑动接口"）

| 字段 | 类型 | 含义 | 取值 / 依据 |
|---|---|---|---|
| `edge` | `LEFT/RIGHT/TOP/BOTTOM` | 从哪条屏幕边进入/退出（决定位移方向） | 旧 myco：左抽屉从左侧、设置从右侧（`LEG §0`、`MA:429-456`） |
| `direction` | `IN / OUT / BOTH` | 该边允许"拉出"还是"推回" | 抽屉 = BOTH（开/关都跟手，`GO §0-8`）；全屏面板 = OUT（只能推回） |
| `openSource` | `CONTENT_DRAG / SCREEN_EDGE / NONE` | 打开手势的触发区 | **建议 CONTENT_DRAG**（旧 96dp，`LEG §2.8`）；`SCREEN_EDGE` 与系统返回争边（Q-BL-03） |
| `revealDp` | Dp | **静止态**露出下层多少像素 | **0**（§C.1；四家都无静止露边实现） |
| `follow` | Float | 跟手系数（1.0 = 手指走 1px 面板走 1px） | 1.0（`M5 §2.7` "跟手 1:1"；dsh-Go/太墟 anchoredDraggable 均 1:1） |
| `commitRatio` | Float | 松手时位移 ≥ 屏宽 × 该值即提交 | **0.22**（旧 myco E0，`LEG §2.8`） |
| `commitVelocityDpS` | Int | 松手速度 ≥ 该值即提交 | **600**（旧 myco E0，`LEG §2.8`） |
| `enabledWhen` | enum | 手势生效条件 | `ROOT_ONLY`（首版；太墟 `canSwipeOpenNavigation`）+ `TOP_LAYER_ONLY`（关闭手势） |
| `consume` | Float | 过哪个阈值后消费手势（防与内容横滑/选字打架） | **96dp**（`LEG §2.8` 的"只观察不消费" → 我们建议"过阈值才消费"，见 Q-BL-03） |

### B.2.4 `PanelRoute`（实例，最小数据结构）

| 字段 | 类型 | 必填 | 含义 |
|---|---|---|---|
| `routeId` | String | ✅ | 实例唯一 id（用于预测式返回/状态恢复/事件回注） |
| `panelId` | String | ✅ | = `PanelSpec.specId` |
| `params` | `Map<String, Any>` | ⬜ | 携带参数（如 `sessionId`/`tab`/`scrollToSeq`）；**按面板声明 schema 白名单校验** |
| `from` | String? | ⬜ | 从哪张卡打开（`panelId`；根为 null） |
| `edge` | `LEFT/RIGHT/TOP/BOTTOM` | ⬜ | 本次进入的边（决定转场方向） |
| `widthSpec` | WidthSpec | ✅ | 运行时宽度（可被窗口类改写） |
| `coverMode` | enum | ✅ | 覆盖/遮罩/推挤 |
| `scrim` | enum | ⬜ | 由 coverMode 推导，可覆盖 |
| `statePolicy` | enum | ⬜ | 保活策略（默认全局） |
| `returnMode` | enum | ✅ | 返回语义 |
| `swipe` | SwipeSpec? | ⬜ | 本次有效的滑动接口（可被窗口类禁用，如宽屏列） |
| `depth` | Int | ✅ | 栈内深度（0=根），由 host 计算，不可外部写 |
| `transitionProgress` | Float | ✅ | 0..1，拖拽/预测式返回期间由手势写、壳只读 |
| `a11y` | object | ⬜ | 本实例的语义（合并 spec 默认值） |

> **状态机（route 生命周期）**：`CREATED → ENTERING → ACTIVE(top) → COVERED(下方) → EXITING → DESTROYED`；`COVERED → FROZEN` 只在深度 ≥2 且覆盖区=全宽时发生（§C.6）。

## B.3 图与栈的示例（JSON/伪代码）

```jsonc
// ① 图：只允许注册表内 id；每条边 = 一个"滑动接口"
{
  "schemaVersion": "1",
  "graphId": "chat-profile",
  "root": "chat",
  "specs": {
    "chat":     { "kind": "chat",     "presentation": "ROUTE_FULL", "coverMode": "OVER_OPAQUE",
                  "edges": { "LEFT": "sessions", "RIGHT": "settings" },
                  "edgesSwipe": {
                    "LEFT":  { "edge": "LEFT",  "direction": "BOTH", "openSource": "CONTENT_DRAG",
                               "revealDp": 0, "follow": 1.0, "commitRatio": 0.22,
                               "commitVelocityDpS": 600, "enabledWhen": "ROOT_ONLY", "consume": 96 }
                  },
                  "returnMode": "HAND_TO_GESTURE" },
    "sessions": { "kind": "sessions", "presentation": "ROUTE_DRAWER", "coverMode": "OVER_DIM",
                  "widthSpec": { "type": "MIN_MAX", "minDp": 320, "frac": 0.86, "maxInsetDp": 56 },
                  "edges": { "RIGHT": "chat" },
                  "edgesSwipe": {
                    "RIGHT": { "edge": "RIGHT", "direction": "OUT" } },
                  "returnMode": "POP", "statePolicy": "KEEP" },
    "settings": { "kind": "settings", "presentation": "ROUTE_FULL", "coverMode": "OVER_OPAQUE",
                  "edges": { "LEFT": "chat", "RIGHT": "bin" },
                  "returnMode": "POP", "statePolicy": "FREEZE" },
    "bin":      { "kind": "bin", "presentation": "ROUTE_FULL", "coverMode": "OVER_OPAQUE",
                  "edges": { "LEFT": "settings" }, "returnMode": "POP" }
  }
}
```

```jsonc
// ② 运行时的单层参数（params 必须过白名单；未知字段拒绝）
{ "sessionId": "s_01H...", "tab": "chat", "scrollToSeq": 42 }
```

```jsonc
// ③ 栈 = 唯一导航真源（P3 可直接照此写状态机）
[
  { "routeId": "r1", "panelId": "chat",     "depth": 0, "edge": null,   "coverMode": "OVER_OPAQUE", "statePolicy": "KEEP" },
  { "routeId": "r2", "panelId": "settings", "depth": 1, "from": "chat", "edge": "RIGHT",
    "coverMode": "OVER_OPAQUE", "returnMode": "POP", "statePolicy": "FREEZE" },
  { "routeId": "r3", "panelId": "bin",      "depth": 2, "from": "settings", "edge": "RIGHT",
    "coverMode": "OVER_OPAQUE", "returnMode": "POP", "statePolicy": "FROZEN" }
]
```

## B.4 校验 / 白名单（借太墟 A2UI 的四层防线，`TX §5.6`）

| 层 | 时机 | 我们检查什么 | 失败表现 |
|---|---|---|---|
| L1 参数层 | 打开前 | `panelId` ∈ 注册表白名单；`params` 符合该面板声明的 schema；`edge` 必须与 `from` 的 `edges` 匹配 | 拒绝打开 + 开发期断言（release 降级为"打开失败"提示） |
| L2 图版本层 | 加载图时 | `schemaVersion` 支持范围（`SUPPORTED_VERSIONS` 白名单，借 `TX §5.2`）；未知字段拒绝 | 图加载失败 → 回退内置默认图 |
| L3 结构层 | 加载图时 | 所有 `to` 可达且在白名单；无边指向自身；根存在；无重复 `(from,edge)` | 启动期崩溃（fail-fast，配置错误不许静默） |
| L4 渲染层 | 运行中 | 面板内容渲染失败 → 显示"原始数据卡"兜底（借 `TX §5.4` 三级降级：骨架/原文/局部错误） | 不白屏、不转圈到死 |

> 首版图**内置在代码里**（`chat-profile` 一份），但按数据格式定义；M6 的"用户自定义/模型生成图"只改数据不改壳。**模型生成图 = 高风险**（假按钮/越权面板），必须 L1–L3 强校验（Q-BL-13）。

## B.5 与太墟 A2UI 的关系（回答 Q-TX-07 / Q-BL-12）

- **不引入 A2UI 协议**作为面板内容接口（首版）：A2UI 解决的是"模型生成内容"，不是"面板邻接"；且其 §5.7 自述多条缺口（无 liveRegion、属性 schema 不在本仓、预览只留 8 条、嵌套滚动未仲裁）。
- **借三条原则**（E1，见 `TX §5.8` 的映射建议，它自己标注"不是结论"）：① 组件/面板白名单；② 版本化 schema + 未知字段拒绝；③ 失败降级为"原文可读"而不是空白。
- **面板邻接图比 A2UI 多一维**：`surfaceId → 抽屉 id + 层级`（`TX §5.8` 原话）；所以我们的契约**不能直接套** A2UI 的扁平 surface，必须是"图 + 栈"。

---

# §C 无可见抓手的实现约束

## C.1 边缘露出：静止 **0px**，只在拖拽/预测式返回中露

- **规则**：任何 route 静止时 `revealDp = 0`；拖拽/预测式返回过程中，露出的上层（或下层）宽度 = `progress × width`，松手后收敛到 0 或全开。
- **依据**：①四家都没有"静止露边"实现——DSH dockkit 浮层是完整矩形、无露条（`Q-BASE-02 解 B` 原话）；mobile-adapt 抽屉关闭时 `left:-110%`（完全移出，`M/:268-292`）；太墟 reveal 抽屉关闭时抽屉在屏外、前景页全宽（`RuntimeRevealNavigationDrawer.kt:66-78`）；dsh-Go 抽屉关闭=overlay 收起。
- **反证**：静止露一条 16dp 的边 = **可见抓手**（用户明令禁止），且会抢走下层 hit 区（G-11 命中区契约：重叠的命中盒会被最后绘制者吃掉）。旧 myco 也没有静止露边（Material 抽屉关闭后不可见）。
- 若用户仍要"卡片叠层"视觉 → Q-BL-02；**建议不做**，因为那会改变"最终外观与旧结构一致"。

## C.2 遮罩（与 M5 液态玻璃的关系）

| 规则 | 取值 | 依据 / 反证 |
|---|---|---|
| 抽屉型（`OVER_DIM`）遮罩 | `alpha = 0.32 × progress`，纯黑/`scrim` 色，**不加 blur** | 0.32：太墟覆盖侧栏（`RuntimeAdaptiveNavigationScaffold.kt:169-172`）、M5 §3.3、mobile-adapt 0.35 同量级；0.12 是太墟 reveal 的另一个值（内部不一致→缺陷，不作解） |
| 全屏不透明面板 | **不画遮罩** | mobile-adapt 为全屏设置面板直接 `display:none` 遮罩并给理由：不透明面板上再叠 backdrop-filter 是白花成本（`M/:719-737`）；太墟 Settings 是导航页、无 scrim（`TX §0-1`） |
| 遮罩模糊 | **不做** | 四家均无 scrim blur 证据；M5 的玻璃面 ≤3 且只给输入区/顶栏/抽屉搜索（M5 §2.1），遮罩不在玻璃家族；blur 遮罩会与 G-01 无关地增加每帧成本 |
| 多层遮罩 | **只画最顶层的遮罩**（下层遮罩抑制） | 反证：三层叠加各 0.32 → 屏幕接近全黑，且遮罩层数随栈深线性增长（无任何 ref 这样做；dockkit/mobile-adapt 都是单遮罩） |
| 遮罩点关 | 采用 **C-BP-02 严判据**（按下+松手都在遮罩上才关） | 已登记冲突（`_conflicts.md` C-BP-02，归 M1）；本文件只引用，防"框内拖选、遮罩上松手"误关 |
| 动效 | `alpha` 跟 `progress` 线性（不单独 tween） | dsh-Go "scrim 随进度淡化"（`GO §2.15` 字段 6）；M5 §2.7 "释放必须有速度"（遮罩跟进度即天然连续） |

## C.3 下层是否可交互 / 返回键优先级

**指针**：覆盖区 > 0 的 route 一律吞指针（`TaiXuNavHost.kt:172-179` 的 E0 做法）；遮罩本身可点（关闭），遮罩之下不可点。

**语义**：覆盖区 = 全宽时，下层 `clearAndSetSemantics`/`invisibleToUser`（dsh-Go `ExcludeSemantics`、太墟 `clearAndSetSemantics`）；**建议连部分覆盖（drawer）也清**——因为露出的那条不可点，读屏不该停在那。

**返回优先级链（本文件建议，Q-BL-09 拍板）**：

```
1. IME 打开            → 收键盘（不 pop 面板）
2. 面板内浮层（modal/menu/sheet/lightbox，z 1000/1100）
                       → 关最上层浮层（由 base.primitives 的浮层栈登记到 dispatcher）
3. 导航栈顶 route      → pop（预测式返回 progress 驱动位移；commit 才真的 pop）
4. 栈底（=聊天根）      → 回到根（若当前不在根）/ 二次确认退出（Q-BL-09）
```

- **必须单一 dispatcher**：太墟的分层 `BackHandler` 顺序"隐式依赖组合顺序"（`TX §6.3` 自述），层数一多会不可控；dsh-Go 的 `PopScope` `when` 穷举同样不适合 N 层。→ 用显式 `(depth, kind)` 表。
- **浮层归属**：menu/modal/sheet **不进导航栈**（I1），但**必须登记**到 dispatcher（否则返回键会穿透到面板，把整页 pop 掉，用户丢上下文）。
- **抽屉**：抽屉是 route（不是浮层），返回 = pop；但"打开抽屉的那一层"是它的来源，pop 后回到来源。

## C.4 宽度规则（手机全宽 / 平板百分比 / 与 DSH grid 的衔接）

| 窗口类 | 判据（建议） | 主面板 | 侧栏（sessions） | 右栏（base.sidebar-right） |
|---|---|---|---|---|
| 手机 | `width < 600` **或** `height < 480` | 覆盖栈，全宽 | 覆盖抽屉 `MIN_MAX(320, 0.86W, W−56)` | 全屏覆盖 route |
| 小平板 | `600 ≤ width < 840` 且 `height ≥ 480` | 覆盖栈，全宽 | rail 56 + 覆盖抽屉（展开时） | 全屏覆盖 route 或 rail 入口 |
| 宽屏 | `width ≥ 840` 且 `height ≥ 480` | 主列 `1fr`（最小 400） | 常驻列：280 / 56 / 0（clamp 264–420，首版不拖） | 列：min 300、max 70%、首开 45%；< 300 时轨道消失 |
| 横屏手机 | `height < 480`（不论宽度） | **强制单栏覆盖栈** | 覆盖抽屉 | 全屏覆盖 |

- **依据**：断点 600/840（dsh-Go/太墟 E0，`GO §2.15` 字段 2、`TX §0-3`）；高<480 强制 compact（dsh-Go 反证："915×412 横屏按宽度会被判成平板、每列只剩一条缝"，`GO §2.15` 字段 13）；列宽 280/56/0 与 300–70% 来自 DSH（`DSS §0.2`、`L/columns.ts:11-29`）；主列最小 400（`GO §2.15` 字段 2）与 DSH `CENTER_MIN=400`（`L/columns.ts:11`）一致。
- **与 DSH grid 的衔接**：宽屏壳 = `grid(280px, minmax(400, 1fr), minmax(0, 70%))`，侧栏列可收成 56/0（DSH 轨道语义）；**窄屏同一内容用覆盖 route 承载**，切换时用 `movableContentOf`（太墟 E0，`TX §0-5`）保证不重建。
- **不做**：列宽拖拽（§4）、内容最大阅读宽（归 chat/theme，§3-12）。

## C.5 手势入口与系统边缘

| 手势 | 建议 | 依据 / 反证 |
|---|---|---|
| 打开抽屉 | **根层内容区水平右滑 ≥ 96dp**，过阈值后才消费；`ROOT_ONLY` | 旧 myco E0（`LEG §2.8`，96dp≈22%×390dp）；太墟"仅根页可滑开"（`MainPageNavigation.kt:7`） |
| 关闭抽屉/面板 | 从该面板的进入边拖出（跟手），阈值 22% 或 v>600dp/s | 旧 myco E0（`LEG §2.8`）；dsh-Go/太墟均跟手 |
| 屏幕左/右缘 20dp | **让给系统返回手势**，App 不接管 | 旧 myco E0（`LEG §2.8`：左缘 20dp 让系统）；反证：接管边缘会与 Android 手势导航的"返回"直接竞争，用户可能每次滑都想退出却拉出抽屉 |
| 系统预测式返回 | 接管为"栈顶 route 的 progress"，commit 才 pop | dsh-Go E0（`GO §2.15` 字段 4）；旧 myco 有 progress 源（`PN:34-114`）；太墟未做（不抄） |
| 手势与内滚仲裁 | 抽屉是**横向**、会话列表是**纵向**→ 正交，不仲裁；但"只观察不消费"阶段允许列表继续竖向滚 | 太墟："横向 anchoredDraggable 与纵向列表正交"（`TX §2.1 drawer` 字段 8）；G-04 的仲裁只在同轴嵌套时适用 |
| 手势与文本选择 | 深层页面禁开手势；根层 96dp 阈值 + 方向锁（未取证，Q-BL-03 拍板） | 反证：太墟在非根页禁用正是因为"代码编辑器横滑想选文本会误开抽屉"（`TX §2.1 drawer` 字段 13） |

## C.6 保活与 z 阶梯

**保活（建议，Q-BL-05 拍板）**：`depth 0–1 → KEEP`（组合+状态）；`depth ≥2 → FROZEN`（`rememberSaveable` 快照 + 释放 UI 组合，恢复时用同一 key 重建）。业务状态（流式、会话树、草稿）在 store 层，不受 UI 冻结影响。

**z 阶梯（常量表，建议）**：

| z | 家族 | 依据 |
|---|---|---|
| 0 | 根 route（聊天） | 栈内顺序，不写数字 |
| 10 | 宽屏常驻列（侧栏/右栏） | DSH 右栏 10（`DSH §0-7`） |
| 20 | 框架浮层槽（`shell.overlay`，穿透） | DSH overlay 20（`DSH §0-7`） |
| 40 | **导航栈 route（本次新增带）** | DSH 全屏右栏 40（`DSH §0-7`）作参考；栈内按顺序 |
| 1000 | modal / lightbox | DSH（`P/Modal.module.css:6-15`、`ImageLightbox.module.css:2-6`） |
| 1100 | portal menu | DSH（`P/Menu.module.css:42-45`） |

> 11/15/60/70 槽位保留不用（无列宽手柄/无窗口 chrome 席/无浮窗/无 dockkit 菜单）——语义编号稳定，避免以后插层时全局改号。

## C.7 安全区 / IME / 旋转（Q-BL-11 拍板）

- **建议 edge-to-edge + 全量 insets**（现代 Android 做法）：面板容器吃 `statusBars/top`、`navigationBars/bottom`、`displayCutout`；抽屉顶部/底部 padding 用 insets（mobile-adapt 的 `env(safe-area-inset-*)` 思路，`M/:277-278`，但它在 DSH Web 页因无 `viewport-fit` 空转，`MOB §0-6`——myco 原生不存在该问题）。
- **旋转 = 不重建**：旧 myco 单 Activity；Compose 用 `configChanges`/`rememberSaveable`；断点切换时用 `movableContentOf`（太墟 E0）迁移，不重启面板。
- **IME**：打开面板前收键盘（太墟 E0，`RuntimeRevealNavigationDrawer.kt:106-109`）；面板内输入区自己 `imePadding()`（`MOB §3.2 host.ime` 反证：不处理会被键盘盖住）。

## C.8 动效（C-BL-01 / Q-BL-06 拍板）

| 场景 | 建议 | 依据 / 反证 |
|---|---|---|
| 按钮触发开/关 | `settle` spring（dampingRatio 0.86, stiffness 420），目标时长 ≈300ms（spring 无固定时长） | M5 §2.7（我方设计，E1）；参考值：dsh-Go 340ms、DSH grid 300ms、mobile-adapt 0.28s。选 spring 而非 tween 的理由是**速度连续**（旧 tween 180ms 正是用户抱怨"手停了还会顿一下"的根因，M5 §2.7 记录） |
| 拖拽/预测式返回 | 跟手 1:1，只改 progress；松手把末速度交给 `Animatable.animateTo(initialVelocity=v)` | 同上；dsh-Go/太墟均跟手（E0） |
| 遮罩 | 跟 progress（无独立 tween） | dsh-Go（E0） |
| reduced-motion | 位移转场改 ≤120ms 淡入淡出/直切 | G-10 的降级要求 + 四家均有关闭实现 |
| 禁止 | 弹跳（dampingRatio<0.8）、定长 tween 收尾、同屏 >3 类动效 | M5 §2.7（我方设计，E1；与 G-10 同向） |

---

# §D 状态矩阵 + 线框

> 手机 390×844dp 为例；`W`=屏宽。所有"线框"都是**结构描述**，不是像素稿。六个必答状态 + 1 个补充状态。

## D.1 状态表

| # | 状态 | 命中区 | 手势阈值 | 松手判定 | 动画（曲线 / 时长） | 返回键行为 | 线框 |
|---|---|---|---|---|---|---|---|
| D0 | **关闭**（根，无面板） | 聊天全屏可点；顶栏菜单/按钮可点 | 内容区水平右滑 ≥96dp 开抽屉 | 达到 96dp 即触发（不等松手） | 打开用 settle spring（≈300ms） | 栈底：退出 App（是否二次确认 → Q-BL-09） | D-0 |
| D1 | **跟手拖拽中**（打开或关闭） | 被拖 route 的可见部分可点；其余区域被吞指针/遮罩接住 | 无阈值（已进入手势）；方向锁 + 96dp 消费阈值 | 位移 ≥22%W **或** 速度 ≥600dp/s → 提交；否则回弹 | 跟手 1:1；回弹/提交 `settle` spring（带末速度） | 若拖拽由系统预测式返回驱动：commit=pop，cancel=回弹；自行拖拽时返回键忽略 | D-1 |
| D2 | **半开**（瞬时态，拖拽中 progress≈0.5） | 面板区域可点；露出的下层被遮罩盖住、不可点 | 同 D1 | 同 D1（不允许停在半开） | 同 D1 | 同 D1 | D-2 |
| D3 | **全开**（抽屉停稳 / 全屏面板停稳） | 面板全内容可点；抽屉型：右侧遮罩可点关闭；全屏型：面板内顶部返回 | 拖出：从进入边向外 ≥22%W 或 v≥600dp/s；点遮罩：C-BP-02 严判据 | 提交 → pop；点遮罩 → pop | pop 反向 settle spring（≈300ms） | pop 一层（面板内浮层先被 dispatcher 消费） | D-3 |
| D4 | **多层叠加**（栈深 ≥2，如 chat→settings→bin） | 只有最顶层可点；深度 1 冻结、不可点；深度 2+ 释放 UI | 同 D3（只作用于栈顶） | 同 D3（只 pop 栈顶） | 同 D3（只动栈顶 route） | pop 栈顶；连续按逐层退（太墟/旧 myco 同款） | D-4 |
| D5 | **横屏 / 平板常驻**（`width≥600 且 height≥480` 或宽屏） | 列：侧栏列可点；主列可点；列不是栈层、不遮罩 | 列形态**无开合手势**；rail 点击开；主列手势只用于栈内 route | 无（列不参与拖拽提交） | 列宽度切换用 `grid-template-columns` 过渡（DSH 300ms 语义）/ 面板走 settle | 返回键**不关列**（列不是栈层） | D-5 |
| D6 | **预测式返回中**（系统手势） | 系统手势区（边缘由系统管）；面板可见部分不响应其它点击 | 进度由系统给；App 只读 progress | commit/cancel 由系统决定；App 在 commit 后 pop | 跟手；commit 后反向 settle；cancel 回弹 | 手势 commit = 返回；无二次确认 | D-6 |

> "命中区"统一受 **G-11** 约束：本面板新增的可点层只有"遮罩""返回入口""菜单/按钮入口"，它们都按 `F`（48dp 主档）+ `e/g/B` 判定式落位；不新增"整条边=热区"这类不可见大热区（那等于可见抓手的隐形版）。

## D.2 线框

**D-0 关闭（根）**
```
+--------------------------------+
| [≡]  会话标题            [⋯]  |  ← 顶栏入口（打开抽屉/设置）
|                                |
|  消息列表（唯一命中层，可滚）   |
|  ...                           |
|  [输入区]                      |
+--------------------------------+
返回键：栈底 → 退出（或回根）
```

**D-1 跟手拖拽中（从左侧拉抽屉，p=progress）**
```
+--------------------------------+
|####|  聊天（遮罩 0.32·p，不可点）|
|####|  消息列表                   |
|####|  ...                        |
|####|  [输入区]                   |
+--------------------------------+
 ^ 抽屉左缘: x = -Wdrawer·(1-p)，跟手 1:1
 命中：抽屉可见区可点；聊天区被遮罩接住
```

**D-2 半开（p≈0.5，仅拖拽中；松手必须收敛到 0 或 1）**
```
+--------------------------------+
|########|  聊天可见条（0.5W，遮罩盖住不可点） |
|########|                              |
|########|  ...                         |
+--------------------------------+
 不允许"停在半开"当静止态（否则=可见抓手）
```

**D-3-a 全开：抽屉型（sessions）**
```
+--------------------------------+
|##########################|#####|
| 抽屉 min(320, 0.86W)     |遮罩 |  ← 右侧遮罩可点关闭
| 会话列表（可滚，44dp 行） |     |
| ...                      |     |
| 设置入口                 |     |
+--------------------------------+
返回键：pop（关闭抽屉，回聊天）
```

**D-3-b 全开：全屏型（settings）**
```
+--------------------------------+
| [←]  设置                      |  ← 顶部返回入口（G-11 命中）
|  分组 / 子页                    |
|  ...                           |
+--------------------------------+
无遮罩（面板不透明）
返回键：pop（回聊天；chat 保持挂载）
```

**D-4 多层叠加（chat → settings → bin）**
```
   ┌ chat      depth0 KEEP（被全宽覆盖 → 语义隐藏，不重布局）
   │   └ settings  depth1 FREEZE（快照，UI 释放）
   │        └ bin   depth2 FREEZE（快照）
   └─ 屏幕只画 bin（不透明全屏）
返回键：pop bin → settings（恢复快照）→ pop settings → chat
```

**D-5 横屏/平板常驻（宽屏三栏 / 横屏手机单栏）**
```
宽屏（≥840 且 height≥480）:
+--------+--------------------------+-----------+
| 侧栏列 | 主列（聊天，min 400）     | 右栏（可选）|
|280/56/0|                          | 300~70%   |
+--------+--------------------------+-----------+
 列不参与返回栈；窄屏同一内容变覆盖 route（movableContentOf 迁移）

横屏手机（height<480）:
+--------------------------------+
|   强制单栏（覆盖栈）            |
+--------------------------------+
```

**D-6 预测式返回中**
```
+--------------------------------+
|            |  栈顶面板左缘跟随系统 |
|            |  手势 -> progress 0..1 |
|            |  松手：系统 commit/cancel
+--------------------------------+
```

---

# §E 与现有实现的迁移路径

## E.1 哪些旧行为必须保留（E0 现状 → 新壳承接）

| # | 旧行为 | 证据 | 新壳如何承接 | 验收 |
|---|---|---|---|---|
| 1 | 返回键/手势退一层：设置退设置、bin 退设置 | `LEG §2.7`（`settingsNav/binNav.exit`） | 栈 POP + 统一 dispatcher | `BL-01` 设置→bin→返回一次回设置、两次回聊天 |
| 2 | 抽屉打开时系统返回 = 预测式关闭 | `LEG §2.8`（`MA:483-503`） | 抽屉是栈顶 route，预测式 progress 驱动关闭 | `BL-02` 抽屉打开按返回：跟手关闭，不是直接退 App |
| 3 | 抽屉开合：按钮打开 + 长滑开（96dp） | `LEG §2.1 chat.topbar.menu`、`§2.8` | `drag-open`（根层）+ 顶栏入口 | `BL-03` 点菜单开；根层右滑 ≥96dp 开；深层右滑不开 |
| 4 | 点会话行 → 关抽屉 + 切会话 | `LEG §2.6 sidebar.row.select`（`MA:334-341`） | 面板内动作回调业务层 + `pop` 抽屉 | `BL-04` 点行后抽屉关、会话切换、返回不回到抽屉 |
| 5 | 抽屉底部设置入口 → 关抽屉 + 开设置 | `LEG §2.6 sidebar.footer.settings` | 两步：`pop(sessions)` + `push(settings)`（同帧，避免闪） | `BL-05` 点设置后抽屉关、设置全屏、返回回聊天 |
| 6 | 宽屏 = 常驻侧栏（同一内容），窄屏 = 抽屉 | `LEG §0`（`MA:547-587`） | 同一 `sessions` 内容：宽屏 `DOCKED_COLUMN` / 窄屏 `ROUTE_DRAWER`，`movableContentOf` 迁移 | `BL-06` 旋转/改宽后抽屉变列，列表滚动位置不丢 |
| 7 | 每会话滚动位置复用；层叠不重载 chat | `LEG §2.7`（`MA:238-239`） | 深度 0 恒 KEEP；滚动位置由会话 store 保存 | `BL-07` 设置里待 30s 返回，聊天位置不变、流式不中断 |
| 8 | 设置/bin 右滑退出（22%/600）与左缘 20dp 让系统 | `LEG §2.8`（`PN:122-177`） | `drag-dismiss` + §C.5 的"系统边缘让位"契约 | `BL-08` 设置右滑 >22% 关闭；左缘 20dp 起手不误开面板 |
| 9 | 三通道（按钮/系统手势/右滑）共享同一 progress | `PN:34-114` | `predictive-back` 契约：所有来源写同一 `progress` | `BL-09` 三种方式关面板的位移曲线一致、无跳变 |
| 10 | 抽屉开合后键盘收起 | 未取证（旧 myco 无此语义；太墟有 E0：`RuntimeRevealNavigationDrawer.kt:106-109`） | 打开面板前 `keyboard.hide()` | `BL-10` 键盘打开时拉抽屉，键盘先收 |

## E.2 迁移顺序（先壳后页，逐面板可回退）

> 用户/Lead 的既有里程碑也是这个方向：`M5 §5` M5.2 = 壳 + 迁移后 4 个面板；且 `M5 §3` 的"外观与旧结构一致"要求壳先稳定。

| 步 | 做什么 | 可回退点 | 完成判据 |
|---|---|---|---|
| S1 | **抽 `PanelGraph` + `PanelHost`（空壳）**：内置 chat-profile 图；只把旧"screen 状态机"翻译成 stack，不换内容 | feature flag：`usePanelShell=false` 走旧 `screen` 分支 | 旧行为完全等价（E.1 的 1–9 全绿） |
| S2 | **接入 drawer 型 route**：sessions 面板首次以 route 形式出现（窄屏抽屉 / 宽屏列） | flag 关闭 → 旧 drawer | `BL-03/04/05/06` |
| S3 | **接入全屏面板**：settings → bin（栈深 2） | flag 按面板粒度关闭 | `BL-01/02/07` |
| S4 | **接预测式返回与统一 dispatcher**（替换旧 `BackHandler`/`BackHandler-Exit`） | flag 回退旧 BackHandler | `BL-02/08/09` |
| S5 | **清旧实现**：删除 `screen`/`drawerState` 直连分支、Material Drawer 包裹层；接入 a11y（role/focus trap/aria-expanded） | tag 回退 | `BL-11` 读屏：抽屉开时焦点不跑到聊天、关时焦点回入口 |
| S6 | **M5 换肤**（视觉层，与本任务解耦）：settle spring、玻璃/新拟物、遮罩 token | 按 token 开关 | 慢动作录屏速度连续 |

> 不建议"先页后壳"：旧页面每一处都直连 `drawerState/screen`（`MA:223-256`），先改页面会把同一耦合复制多份，壳一改全返工。

## E.3 风险点（按严重度）

| # | 风险 | 证据/原因 | 缓解 |
|---|---|---|---|
| R1 | **预测性返回与自建栈双触发**：系统手势 + 旧 `PredictiveHandler` + `BackHandler` 同时存在 → 一次返回退两层 | 旧 myco 三通道共享 progress（`PN:34-114`），但新栈又加一层 | S4 一次性替换所有返回入口；单一 dispatcher；验收 `BL-09` |
| R2 | **边缘手势与系统返回冲突**：屏幕边缘 vs 系统手势 | `LEG §2.8` 左缘 20dp；dsh-Go 用 20dp 边缘做页面返回（`GO §2.15`）；太墟整条左缘开抽屉（`TX`） | 首版不做屏幕边缘开合（Q-BL-03）；左缘 20dp 保留 |
| R3 | **Material `ModalNavigationDrawer` 语义丢失**：旧抽屉自带 scrim/手势/返回/a11y | 旧用 `drawerState`（`MA:418`），未取证其内部语义 | S5 时逐项补：scrim 0.32、C-BP-02 判据、role=dialog/焦点陷阱/`aria-expanded`（Q-BL-10） |
| R4 | **深度 ≥2 冻结后恢复不完整**（滚动位置/输入草稿/展开态） | 旧只覆盖 1–2 层保活（`DSS §0.2-⑤`、`TX §0-5`）；深层无先例 | 冻结用 `rememberSaveable` + 业务 store；P4 逐层恢复验收；失败则退化为"KEEP 到深度 2" |
| R5 | **宽屏列 ↔ 窄屏覆盖切换重建** | 太墟用 `movableContentOf` 才不重建（`TX §0-5`）；myco 若直接换 composable 会重建 | 同一内容包 `movableContentOf`；验收 `BL-06` |
| R6 | **"外观与旧结构一致"缺基线**：旧抽屉/设置滑入的 alpha/曲线/曲线起点未取证 | `legacy-myco.md` 未取证（§0.2-3） | P4 用旧版真机录屏做逐帧对照；本文件不给"旧值"，只给建议值（Q-BL-04/06） |
| R7 | **无障碍回归**：被覆盖层语义/焦点陷阱是新代码 | 两家参考都有缺口（太墟页面级未清语义 Q-TX-11；mobile-adapt 无 focus trap） | S5 必须有 TalkBack 验收；`BL-11` |
| R8 | **图数据配错**（点不进/回不去/成环） | 我方新设计 | 启动期 L3 校验（fail-fast）+ 开发期单测覆盖全部边 |

## E.4 迁移验收门（进 P4 前必须可执行）

- `BL-01`…`BL-11` 全部有真机截图/录屏 + 读屏（TalkBack）记录；
- 连续 20 次"开-关-开"无位移跳变（录屏 0.25x 逐帧）；
- 断点切换（竖屏→横屏→分屏）内容不重建（录屏看输入框文本/滚动位置）；
- 旧/M5 差异表（`LEG §4`）里与壳相关的 #1（宽屏死按钮）/ #25（导航未换肤）**必须在本任务后消失**。

---

# §F 待确认（阻塞项，Q-BL-01 起）

> 每条=参考没有 / 只能用户拍板。**两解 + 各自后果 + 建议**。未拍板不得进 P3。

### Q-BL-01 层叠模型选哪个（C-11 / Q-BASE-02 / Lead 指定必答）

- **解 A（覆盖栈）**：`List<PanelRoute>`，栈顶渲染+下层保活，返回=pop。
  - 后果：层数无上限、返回语义唯一、与旧 myco 结构同构（迁移最省）；但无源码可抄，预测式返回/保活/无障碍全自建。
- **解 B（浮层数组）**：借 dockkit `floats[]`，z=数组序、按下抬升、不做分栏。
  - 后果：z 序模型有现成依据；但**没有返回/退层语义**、浮窗是完整矩形无露边、外观是多窗（用户明确不做并排拼贴）→ 等于自建栈 + 多一套浮窗。
- **解 C（混合）**：导航用栈 + 面板内浮层用数组。
  - 后果：表达力最强；但两套 z/返回/测试面，G-00b 单一事实源被破坏。若浮层归属 `base.primitives`（z 1000/1100），则退化为"解 A + 职责边界"。
- **建议：解 A**（详见 §A.3）；**扩展层 = 导航栈层**（把太墟的"4 覆盖根页"泛化为 N 个可邻接 route，把"1 固定抽屉"泛化为任意边 drawer route），**不是面板层、不是浮层数组层**。

### Q-BL-02 是否存在"静止半开 / 露边"状态

- **解 A（只在拖拽中露边，静止 0px）**：与旧结构一致、无可见抓手（用户硬要求）；用户可能觉得"层叠感"不够。
- **解 B（静止露 16–24dp 下层边缘，形成叠卡）**：视觉更像"卡片堆"；但**这就是可见抓手**（用户禁止），且抢下层 hit 区（G-11）、改变旧外观。
- **建议：解 A**（§C.1）；若用户要 B，需同时改"无抓手"这条铁律（不建议）。

### Q-BL-03 打开手势：边缘侧滑 / 内容区拖拽 / 仅按钮；是否消费（C-BL-03）

- **解 A（内容区拖拽 + 过阈值消费 + 仅根层）**：旧 myco 已有（96dp），不与系统手势争边；代价：手势"发现性"靠用户自己摸（但旧版已验证用户会）。
- **解 B（屏幕边缘侧滑开抽屉）**：与 Android 系统返回手势直接竞争；需 `systemGestureExclusion` 让系统少一块边，或反向让抽屉只从右缘开（与旧左抽屉方向冲突）。
- **解 C（仅按钮/菜单）**：最安全、可发现；但用户原话"每张卡片有滑动接口"，去掉滑动等于违背需求。
- **建议：解 A**（根层 96dp + 深层禁开；关闭仍跟手拖出）。

### Q-BL-04 下层面板是否缩放 / 压暗 / 模糊

- **解 A（不缩放、不模糊，只 0.32 遮罩；全屏面板无遮罩）**：与旧结构一致（旧 chat 不动）；M5 §3.3 的 `scale 0.96 / alpha 0.5 / blur 4dp` 是**我方设想且未取证**（M5 是待拍板文档）。
- **解 B（照 M5 §3.3 做底层缩放+压暗+可选模糊）**：更有"层叠纵深"；但改变旧外观（违反用户"与旧结构一致"）、模糊每帧成本、且四家参考都**没有**前景缩放的证据（dsh-Go/mobile-adapt 都不缩放；太墟 Compact 是前景平移而非缩放）。
- **建议：解 A**（首版），把"纵深感"留给 M5 的玻璃/阴影语言，不用缩放底层。

### Q-BL-05 无限层保活策略

- **解 A（顶 2 层 KEEP，深≥2 FREEZE；部分覆盖的下一层仍 KEEP）**：内存可控、抽屉首拖不掉帧（太墟 E0 理由）。
- **解 B（全部 KEEP）**：返回零恢复成本；深度一多内存/重组线性涨（无参考支持无限层全保活）。
- **解 C（全部 FREEZE，只保留栈顶）**：最省内存；抽屉/聊天切回会重建，违反旧 myco"切会话不重挂载"（`DSS §1.2-D`、`LEG §2.7`）。
- **建议：解 A**（规则写进 `stack-lifetime`，Q-BL 只为确认）。

### Q-BL-06 转场物理与松手阈值（C-BL-01）

- **解 A（spring + 22%/600dp·s⁻¹）**：速度连续（用户"手停要自己滑，不能顿"）、阈值沿用旧版；spring 无固定时长，P4 需用录屏验证观感。
- **解 B（固定时长：dsh-Go 340ms / DSH 300ms / mobile-adapt 280ms / 旧 180ms）**：可预测、易对齐设计稿；但松手速度不连续（旧 tween 已被用户抱怨）。
- **建议：解 A**；数值只定阈值（22%/600），时长用 spring 等效 ≈300ms。

### Q-BL-07 预测性返回启用范围与低版本降级

- **解 A（Android 13+ 启用；API<33 瞬间 pop + 反向 spring）**：现代设备体验好；低版本无跟手预览。
- **解 B（全版本关闭，只做"松手后动画"）**：实现简单、行为一致；Android 13+ 用户会觉得"廉价"。
- **解 C（全版本自绘边缘跟手，不依赖系统）**：最统一；但与系统返回手势冲突（Q-BL-03）。
- **建议：解 A**。

### Q-BL-08 断点 / 列宽 / 双栏是否进返回栈（C-BL-02）

- **解 A（600/840 + 高<480 强制单栏；列不进栈）**：对齐两台手机原生参考；代价=三档测试矩阵。
- **解 B（≥600 就双栏，M5 §3.5）**：更早进入"平板态"；但 600dp 下 `280+400=680` 已超过屏宽，聊天列会 <400（与 dsh-Go/DSH 的 min 400 冲突）。
- **解 C（1024 才常驻，DSH 值）**：宽屏最保守；平板 800dp 只能抽屉，浪费横向空间。
- **建议：解 A**；列宽 280/56/0（DSH clamp），不做拖拽（§4）。

### Q-BL-09 返回优先级链 / 栈底行为 / 面板内浮层归属

- **解 A（单一 dispatcher：IME → 面板内浮层 → 栈顶 route → 栈底回根/退出）**：可穷举、可测试；需 M1 把 modal/menu 注册进来。
- **解 B（各层自注册 `BackHandler`，按组合顺序响应）**：实现分散、加层容易；但顺序隐式（太墟自述缺点）、深栈时不可控。
- **解 C（返回只 pop 栈，浮层自己处理）**：最简单；浮层打开时返回会穿透把面板 pop 掉（丢上下文）。
- **建议：解 A**；栈底二次确认是否要（解 A 的一个子项，建议"再按一次退出"）。

### Q-BL-10 抽屉关闭路径集合与 a11y 语义修正

- **解 A（返回/遮罩/拖出/导航后自动收；补 `role=dialog`+`aria-modal`+焦点陷阱+`aria-expanded`）**：补齐参考实现的缺口（mobile-adapt 无 role/focus trap、汉堡无 aria-expanded；太墟无关闭按钮但有手势/遮罩/返回）。
- **解 B（再给抽屉加显式关闭按钮）**：发现性最好；多一个关闭语义、与"无可见抓手"无关但增加视觉件。
- **解 C（不补语义，靠 Compose `ModalNavigationDrawer` 现成语义）**：省事；但自建 host 后可能丢失，且旧版未验证。
- **建议：解 A**，不加显式 X（旧版没有）。

### Q-BL-11 安全区 / edge-to-edge

- **解 A（edge-to-edge + 全量 insets）**：现代 Android 观感（列表/视频到边）、需逐面板 insets padding。
- **解 B（非 edge-to-edge，系统栏自动留边）**：零避让成本（mobile-android 壳做法，`MOB §3.2 host.window`）；有黑边、观感旧。
- **建议：解 A**（myco 是原生 Compose，不是 WebView 套壳，不存在 mobile-android 的 targetSdk 限制理由）。

### Q-BL-12 是否引入 A2UI / `render_surface` 作为面板内容接口（Q-TX-07）

- **解 A（直接采用协议）**：模型可声明式生成面板内容+事件回注；代价：引入大依赖、A2UI §5.7 多条缺口（无 liveRegion/属性 schema 外部依赖/嵌套滚动未仲裁）、与"面板邻接"不是一回事。
- **解 B（只借白名单+四层校验+失败降级原则，不引协议）**：风险最低、与首版原生渲染不冲突。
- **解 C（都不做）**：最省；但失去"模型生成界面"的可能性（未来能力）。
- **建议：解 B**（§B.5）。

### Q-BL-13 "可组合"的边界：首版图是否可外部配置 / 模型生成

- **解 A（首版图内置、白名单强校验；编辑器 M6）**：安全（无假按钮/越权面板）、可先验证壳；用户不能自定义。
- **解 B（首版就支持外部 JSON 图）**：真"可组合"；但图=导航，配错即不可用，且外部输入必须过 L1–L3 白名单（否则模型可生成任意面板 = 安全面）。
- **解 C（模型生成图 + 白名单）**：最激进；需要权限/审批面板配合（myco 首版无 approval 面板）。
- **建议：解 A**；数据格式按 §B 定义，M6 直接接编辑器。

---

## 附：本文件与 Lead 的交接点

1. **新冲突**（请收进 `_conflicts.md`）：`C-BL-01`（转场物理：固定时长 vs spring，Q-BL-06）、`C-BL-02`（断点 600/767/840/1024，Q-BL-08）、`C-BL-03`（打开手势只观察 vs 消费跟手，Q-BL-03）。
2. **跨面板共性**：
   - 遮罩"唯一 token 0.32 + 全屏不透明不画遮罩"应进 `base.theme`（不只在 base.layout）；
   - 返回 dispatcher 需要 M1 的 modal/menu 注册接口（否则浮层打开时返回会穿透）；
   - `G-01` 边界已有（列/固定头尾/内联状态条不属 overlay），本文件按该边界执行；
   - 命中区四家无共识已由 C-TX-02 收口，本文件不另立，只引用 G-11 的参数化 `F`。
3. **事实库/文档问题**（请 Lead 修）：`_conflicts.md` 末尾"待补入（等 R2/R3/R4）"已过时（dsh-go/taixu 均已成稿）；`taixu.md §0-6` 说"5 级"而 `§6.3` 列 7 级。

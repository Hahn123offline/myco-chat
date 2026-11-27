# P2 合并：sidebar（会话导航面板）

> 合并员：M7。输入：`ref/dsh-sidebar.md`（§0、§1.2、§2.1）、`ref/dsh-workspace.md`（§0、§2.1、§5 交互矩阵）、`ref/dsh-go.md`（§0、§2.7、§2.15）、`ref/taixu.md`（§0、§2.1 drawer、§2.5、§6）、`ref/chat-demo.md`（§1、§2.18–2.23）、`ref/legacy-myco.md`（§0、§2.6、§2.8、§3、§4、§5）、`ref/dsh-mobile.md`（§0、§2、§3.1）、`00-global-rules.md`、`_conflicts.md`、`open-questions.md`、`p2/base.layout.md`（M2，容器契约，**本文件一律服从它**）、`p2/settings.md`（M6，回收站入口归属）。
> 本文件**只做归一取舍**，不新增事实。ref 未覆盖的一律「未取证」→ §6；架构衔接直接引 M2，不自定另一套。
> 只写本文件；未改 `ref/**`、`00-global-rules.md`、`_conflicts.md`、`_merge-*.md`、`open-questions.md`、`decision-pack.md`、其他 `p2/*.md`；未改 app；未 commit。

## 0. 读表须知（不占章节号）

**证据缩写**（`path:line` 全部来自对应事实库，不另行取证）：

| 缩写 | 文件 | 引用形态 |
|---|---|---|
| `DSS` | `ref/dsh-sidebar.md` | §2.1 节点表；`SidebarRoot.tsx` / `columns.ts` / `AppFrame.tsx` 等 |
| `DSW` | `ref/dsh-workspace.md` | §2.1 节点表 + §5 交互矩阵；`Rows.tsx` / `WorkspaceBrowser.tsx` / `stores.ts` |
| `GO` | `ref/dsh-go.md` | §2.7 侧栏 / §2.15 布局；`home_shell.dart` / `sessions_drawer.dart` / `session_tile.dart` |
| `TX` | `ref/taixu.md` | §0 / §2.1 drawer / §2.5 / §6；`RuntimeAdaptiveNavigationScaffold.kt` / `SessionsSideDrawer.kt` |
| `DEMO` | `ref/chat-demo.md` | §2.18–2.23；`S/Sider.tsx` / `S/SessionItem.tsx` / `svc/chat/sessionGroups.ts` |
| `LEG` | `ref/legacy-myco.md` | §2.6 / §2.8 / §3；`DR`=Drawer.kt、`MA`=MainActivity.kt、`IB`=InProcessBridge.kt |
| `MOB` | `ref/dsh-mobile.md` | §3.1；`M/`=`mobile-adapt/lib/client.js` |
| `M2` | `docs/spec/p2/base.layout.md` | 容器/栈/断点/手势/返回 |
| `M6` | `docs/spec/p2/settings.md` | 回收站入口归属 |
| `G-xx` | `docs/spec/00-global-rules.md` | 全局规则 |

**口径**：① 只有「做到 A 就做不到 B」才进冲突列，可并存的进 §3；② 参考实现自评/可证的缺陷**不作为另一种解**，只进 §4.2；③ ref 没写的写「未取证」→ §6，不写「大概/一般应用」。

**本面板范围**：会话导航 = 壳（抽屉/列）+ 头部/底部 + 列表/分组 + 会话行全交互 + 搜索 + 行菜单/改名/删除 + 归档撤销 + 批量选择 + **回收站列表本体**（入口归 `settings`，`M6:167` 明确把本体留给本面板）。`base.sidebar-right`（右栏）首版范围结论见 §6 `Q-SB-12`，**本文件不列它的节点**（避免与 M2 的容器契约重复登记）。节点共 **28 个**。

---

## 1. 节点清单（先穷举，再填表）

| 节点 ID | 出现条件 | 参考里有没有 | 旧 myco 有没有 |
|---|---|---|---|
| `sidebar.shell.route` | 每次打开侧栏 = 栈里新增 1 个 drawer route | 四家都**没有**（都是 overlay 布尔/Material drawer，无 pop 语义） | 无（`drawerState` 布尔） |
| `sidebar.shell.width` | 每帧随窗口类重算 | DSH✓ GO✓ TX✓ DEMO✓ MOB✓ | ✓（手机抽屉/宽屏常驻；宽度未取证） |
| `sidebar.shell.column` | 始终（手机=覆盖、宽屏=列） | DSH✓ GO✓ TX✓ DEMO✓ | ✓ |
| `sidebar.shell.scrim` | 部分宽度覆盖时 | DSH✗ GO✓ TX✓ DEMO✓（无背景色） MOB✓ | ✓（alpha 未取证） |
| `sidebar.shell.toggle` | 始终（入口 1 个/屏） | DSH✓ GO✓ TX✓ DEMO✓ MOB✓ | ✓（顶栏 ☰） |
| `sidebar.shell.gesture` | 打开手势仅根层；关闭随时 | DSH✗ GO✓ TX✓ DEMO✗ MOB✗（声称有=E1 不成立） | ✓（96dp 开 / 预测式关） |
| `sidebar.shell.scroll` | 列表区始终 | DSH✓ GO✓ TX✓ DEMO✓ MOB✓ | ✓ |
| `sidebar.header` | 抽屉/列展开时 | DSH✓ GO✓ TX✓ DEMO✓ | ✓ |
| `sidebar.header.new-session` | 头部恒在 | DSH✓ GO✓ TX✓ DEMO✓ | ✓ |
| `sidebar.footer.settings` | 底部恒在 | DSH✓ GO✓ TX✓ DEMO✓ | ✓ |
| `sidebar.list` | 始终 | DSH✓ GO✓ TX✓ DEMO✓ | ✓ |
| `sidebar.group.header` | 有分组时每组 1 行 | DSH✓ GO✓ TX✓ DEMO✓ | ✓ |
| `sidebar.session.row` | 每个会话 1 行 | DSH✓ GO✓ TX✓ DEMO✓ | ✓ |
| `sidebar.session.title` | 每行 1 个 | DSH✓ GO✓ TX✓ DEMO✓ | ✓ |
| `sidebar.session.meta` | 每行（时间/状态点） | DSH✓ GO✓ TX✓ DEMO✗（仅标题） | ✓（时间+片段） |
| `sidebar.session.actions` | 每行行尾 | DSH✓（hover） GO✗ TX✓ DEMO✓ | ✓（⋯ 常驻） |
| `sidebar.session.menu` | 点 ⋯ / 长按 | DSH✓ GO✓ TX✓ DEMO✓ | ✓ |
| `sidebar.session.hover-card` | 指针停留 800ms | 仅 DSH✓ | ✗ |
| `sidebar.session.reorder` | 拖拽行时 | 仅 DSH✓（HTML5） | ✗ |
| `sidebar.session.rename` | 菜单「重命名」/双击标题 | DSH✓ DEMO✓ | ✓ |
| `sidebar.session.delete-confirm` | 菜单「删除」 | DSH✗（无删除） DEMO✓ | ✓ |
| `sidebar.bin` | 从设置「会话数据」进入 | DSH✗（归档筛选替代） | ✓（回收站/归档页） |
| `sidebar.undo.toast` | 归档/删除/批量后 | DSH✓ LEG✓ | ✓ |
| `sidebar.selection` | 进入批量模式后 | DSH✗ GO✓ | ✓ |
| `sidebar.search` | 点搜索键/快捷键 | DSH✓ GO✓ TX✓ DEMO✗ | ✓（无防抖） |
| `sidebar.search.result` | query 非空 | DSH✓ GO✓ | ✓（仅过滤） |
| `sidebar.search.empty` | 无匹配/加载中 | DSH✓ GO✓ | 部分（无空态） |
| `sidebar.a11y.keyboard` | 始终（语义契约） | DSH✗（缺口） GO✓（部分） TX 未取证 DEMO✗ LEG✗ | ✗ |

> 计数：**28 个节点**。其中 `sidebar.shell.route` 与 `sidebar.a11y.keyboard` 是四家都没有/都残缺的**新增契约**（§5）；其余 26 个都有至少一家 E0。

---

## 2. 对照表（主线）

> 冲突列只写互斥项。`C-SB-nn` = 本文件新登记（待 Lead 收进 `_conflicts.md`）。**所有「建议」都以 M2 的容器结论为前提**。

| 节点 ID | DSH | dsh-Go | 太墟 | chat-demo | 旧 myco | 冲突 C-xx | 建议 + 理由 | 状态 |
|---|---|---|---|---|---|---|---|---|
| `sidebar.shell.route` | 无栈：`activePanelId` 覆盖，无 pop（`DSS §0.2`；M2 §A.0 样本 1） | `MaterialPageRoute` 页面栈 + 抽屉 overlay（`GO §2.15`） | 单栈 `NavDisplay` + 固定抽屉（`TX §6.1`） | 无栈，布尔 `openMobileSider`（`DEMO §2.22`） | 单 Activity 三屏层叠 + `drawerState` 布尔（`LEG §0`） | — | **抽屉 = M2 覆盖栈里的一层 route**：`PanelRoute{widthSpec, coverMode=partial, edge=start, returnMode=pop}`，打开=push、返回=pop、遮罩/拖出/导航后自动收都走同一 pop。理由：M2 §C.3 明写「抽屉是 route（不是浮层），返回 = pop」；反证：若照四家用布尔/overlay，返回键只能另写一套仲裁，与 M2 的单一 dispatcher 打架，深层面板里返回会先关抽屉再 pop 面板（顺序错）。 | ✅ 采用建议（随 Q-BL-01） |
| `sidebar.shell.width` | grid 列 280/56/0，clamp 264–420；<1024 自动折叠；窄屏展开=**挤压**中心列（可到 0），无遮罩（`DSS §0.2①②③`；`columns.ts:13-23,52-58`） | <600 单栏；600–840 rail 68；≥840 常驻 320；抽屉宽 min(320+inset, W−56)（`GO §2.7 字段2`、`§2.15 字段2`） | Compact min(75%,360dp)；Medium rail 84+覆盖 320；Expanded 320 停靠；断点 600/840（`TX §0-3`、`§2.1 drawer 字段2`；`RuntimeWindowSize.kt:15-31`） | 桌面 261；移动抽屉 300、max W−48；断点 768（`DEMO §2.22 字段2/10`） | 手机抽屉 / 宽屏常驻；宽度**未取证**（`LEG §0`；M2 §0.2-3） | **C-10** | **直接消费 M2 §C.4**：手机或高<480 = 抽屉 `min(320dp, 0.86W, W−56)`（320 上限与 GO/TX 同量级；0.86W 同 MOB；W−56 留出 56 轨道，GO/MOB 同款）；600–839 = rail 56 + 覆盖抽屉；≥840 = 常驻列 280/56/0（clamp 264–420，首版不拖）。**不抄 DSH 的 1024 自动折叠**：反证=窄屏展开会把中心列压到 0 宽（`columns.ts:58`），手机上聊天不可读（`DSS §0.2③` 原文），这正是 C-10 不是「真冲突」而是「断点分工」的原因。 | ⏸ Q-SB-20 |
| `sidebar.shell.column` | grid 项**占位**；折叠=内容冻结展开宽 150ms 淡出 + 轨道裁切 + rail 入场，全程不 reflow，reduced-motion 关（`DSS §0.2⑤`、`§2.1 column 字段7/9`） | 手机 overlay / 宽屏常驻；位移 paint-only `Transform`；跟手+弹簧（`GO §2.7 字段7`、`§2.15 字段6/9`） | Compact 前景整体横移（reveal，M2 §4 不采纳）；Medium/Expanded 隐藏保持组合 + `clearAndSetSemantics`（`TX §2.1 drawer 字段5/6/11`） | 移动 absolute 不占位、**无开合动画**、遮罩无背景色（`DEMO §2.22 字段2/6/7/9`） | Material 抽屉 + `drawerState`；**视觉参数未取证**；抽屉打开时系统返回=预测式关闭（`LEG §2.8`；M2 §0.2-3） | **C-10** | 手机=覆盖 route、**不占位**（G-01：overlay 不参与布局流）；宽屏=列、**占位**（G-01 边界，M2 §2 同款）。位移 paint-only、开合前 `keyboard.hide()`（`TX` E0，反证：键盘+全屏面板会半屏夹层）；reduced-motion 直切。**不抄 DEMO 的「无开合动画」**：反证=抽屉突兀出现，用户不知道刚才发生了什么，且与 M2 §C.8 的 spring 结论冲突。 | ✅ 采用建议 |
| `sidebar.shell.scrim` | **无**（`ui-sidebar/src` grep scrim/Escape 0 命中；`DSS §0.2①④`） | 随 progress 淡化（`GO §2.15 字段6`） | 覆盖态 `alpha=0.32*progress`；另处 0.12（自评不一致，缺陷不作解，`TX §2.1 drawer 字段6`；Q-TX-12） | z799 遮罩存在但**无背景色**（E0，缺陷不作解，`DEMO §2.22 字段2/6`） | Material 默认 alpha **未取证**（M2 §0.2-3） | —（C-BP-02 是判据不是有无） | **单 token 0.32、随 progress 线性**（M2 §C.2；TX E0 + MOB 0.35 同量级）。全屏不透明面板不画遮罩（反证：多一层 backdrop-filter 纯耗电，`MOB` 设置面板原文）。点击关闭判据直接引 **C-BP-02**（按下与松手都在遮罩上），归 `base.primitives`。 | ✅ 采用建议 |
| `sidebar.shell.toggle` | 28×28 两安装位 + 快捷键；**无手势/无返回键语义/Escape 不关侧栏**（`DSS §2.1 toggle`、`§0.2④`） | ≡ 玻璃圆键；宽屏 rail（`GO §2.7 字段3`、`§2.15 字段3`） | rail 项 40dp + `Role.Tab/selected`；顶栏菜单键开抽屉（`TX §2.1 drawer 字段3/4`） | 移动顶栏菜单钮 `xl=44`；桌面收起钮 34（`DEMO §2.22 字段3`） | 顶栏 ☰（M5 宽屏是 `onClick={}` 死按钮 = 缺陷，`LEG §2.1 topbar.menu`） | **C-SB-02** | **顶栏菜单键（48 布局盒）+ 宽屏列折叠键/rail**，不做悬浮汉堡。理由：四家 3/4 用顶栏键（GO/DEMO/旧）；反证=MOB 的悬浮汉堡是「改不了上游 header」的**适配层产物**，代价是内容侧固定让位 64px（`MOB 字段2`），myco 自有 header 无此约束，交了这笔税还白盖内容首行。补 `aria-expanded`（MOB 缺=缺陷）。 | ✅ 采用建议（C-SB-02 落点） |
| `sidebar.shell.gesture` | 全无：无 touch/pointer 拉起、无 history.back、无焦点陷阱（`DSS §0.2④`） | 边缘横滑跟手 + 松手弹簧；返回先结束多选/再关抽屉（`GO §2.7 字段4`、`§2.15 字段4`） | `anchoredDraggable`；**仅根页可滑开**；开合前 `keyboard.hide()`；返回 7 级优先链（`TX §0-4`、`§6.3`） | 移动**无滑动开合**（E0）；切会话/切布局自动收（`DEMO §2.22 字段4/5`） | 聊天正文右滑 ≥96dp 开抽屉（只观察不消费）；系统返回=预测式关闭（`LEG §2.8`） | —（C-BL-03 已登记于 M2） | **全量引 M2 §C.5**：打开 = 根层内容区拖拽 ≥96dp 过阈值才消费，左右缘 20dp 让系统返回；关闭 = 从进入边拖出（22% 或 600dp/s）；关闭路径集合 = 返回 / 遮罩 / 拖出 / 导航后自动收 + 500ms 防连关 + 60ms 让 pressed 先出（`MOB §3.1 auto-close` E0）；返回顺序走 M2 单一 dispatcher：IME → 面板内浮层 → 栈顶 route（=抽屉时先关抽屉）→ 栈底。反证：边缘侧滑会与系统返回抢手势。 | ✅ 采用建议（Q-BL-03/09 在 M2） |
| `sidebar.shell.scroll` | 列表是唯一滚动容器；`scrollbar-gutter:stable` + 滚动条指针进出 2s 静默（重绑 `transparent` 不改行宽）；到边**无** `overscroll-behavior`（缺口 Q-WS-16）（`DSS §0.2⑤`、`§2.1 column 字段6/8`） | 列表纵向滚、面板内滚到边不外传（`GO §2.7 字段8`、`§2.15 字段8`） | 抽屉内单 `LazyColumn`；横向抽屉与纵向列表正交（`TX §2.5 字段8`、`§2.1 drawer 字段8`） | 列表容器滚（`DEMO §2.18 字段8`） | `LazyColumn`（`LEG §2.6 row.select 字段8`） | — | 唯一滚动盒；到边 **contain/吸收**（G-04 铁律 + `MOB §3.1 drawer 字段8` 的 `overscroll-behavior:contain` E0）；滚动条槽预留、显隐不改变行宽（G-02 同类事故：DSH 用重绑 thumb 而不是 `display:none`）。抽屉内不嵌第二个纵向滚动区（搜索输入区不自滚）。 | ✅ 采用建议 |
| `sidebar.header` | 区段头 36px：标题（max 45%）+ 搜索槽（flex）+ 动作簇（max 60px）；搜索展开时标题/动作 `max-width:0` 让位（`DSW §2.1 section-header`）。另有品牌行/新建条/面板 nav（`DSS §2.1 column`） | 品牌+模式+新对话 → 安全横幅 → 搜索 → 工作区切换 → 状态筛选胶囊 → 列表 → 身份栏（`GO §2.7 字段2`） | 顶部搜索 + 新建；底部固定 footer（`TX §2.5 字段2`；E1 `UI_GUIDELINES.md:48`） | 新对话大按钮（无搜索）；底部设置（`DEMO §2.21/2.23`） | 头 Row：标题 + 选择 + ＋新对话；底设置 + 「本地优先·离线可用」（`LEG §2.6`） | — | **简化头 = 标题 + 搜索键 + 新建键（+批量态换成选择头）**；底部设置入口保留。不移植 DSH 的品牌/模式/安全横幅/工作区切换/筛选胶囊、GO/TX 的项目/Bot/远程分区（无对应域，§4）。搜索入口放头（DSH E0）；搜索展开时标题与动作让位用 `max-width:0` 而不是 `display:none`（反证：DSH 明写 `display:none` 会让 36px 行内跳动且焦点序突变）。 | ✅ 采用建议 |
| `sidebar.header.new-session` | 38px 整条 + 品牌行钮 + chrome 席三安装位；`startSession` 复用/新建空白会话（`DSS §2.1 new-session`） | 新对话玻璃圆键 ≥44（`GO §2.7 字段3`） | `SessionDrawerChrome` 内新建钮（`TX §2.5 字段3`） | 侧栏 100%×40 + 收起态/移动 44；`newChat` 不落库（null 会话）（`DEMO §2.21`） | ＋新对话 → `SessionEnsureDraft` **复用唯一空白草稿**，幂等（`LEG §2.6 header.new-chat`；`IB:69-77`） | — | **保留旧语义**：复用唯一空白草稿，不新建多条空记录；48 布局盒；点击后 pop 抽屉并打开该会话。反证：每次新建都落库会像 DEMO 自评那样堆空记录（`DEMO §2.21` 反证原文）；不复用则列表被空会话填满。 | ✅ 采用建议 |
| `sidebar.footer.settings` | `footArea`：设置 + 附加动作（`DSS §2.1 column 字段2`） | 底部身份栏 host/网页版/设置（`GO §2.7 字段2`） | `SessionDrawerFooter` 固定设置入口（E1 `UI_GUIDELINES.md:48`） | 底部设置 64 高、吸底（`DEMO §2.23`） | 底部设置 TextButton + 文案；宽屏同样可点（`LEG §2.6 footer.settings`） | — | **保留（4/4 参考都有底部设置入口）**：吸底、不随列表滚、48 布局盒；点击 = pop 抽屉 + `openSettings`。反证：`LEG` 原文「不保留 → 设置页没有任何入口（模型/provider/主题全不可配）」。 | ✅ 采用建议 |
| `sidebar.list` | 三态共用滚动盒；组间 4px；底部 24px 渐隐（macOS vibrancy 下禁用）（`DSW §2.1 list`） | 会话列表纵向滚；面板内滚到边不外传（`GO §2.7 字段8`） | 单 `LazyColumn`，分区标题随滚（E2 非 sticky）（`TX §2.5 字段8`） | 列表容器滚（`DEMO §2.18 字段8`） | `LazyColumn`；空态「还没有会话/没有匹配」（`LEG §2.6 row.select 字段10`） | **C-18** | **一次性加载会话元数据 + `LazyColumn`**（旧 myco 现状 = `LazyColumn`，`LEG §2.6`；太墟单 `LazyColumn` E0；chat-demo 一次渲染；GO 实现细节未取证）。**不采用 DSH 的 5 条配额**：DSH 配额的存在理由写得很清楚——它没有虚拟化，「一次渲染几百行，打开侧栏第一帧就卡」（`DSW §2.1 overflow 字段13` 反证原文）；Compose `LazyColumn` 天生虚拟化，这个前提在 myco 不成立。空态两文案（DSH E0）；加载中 2 条骨架 + `role=status`（借 `DSW §2.1 search 字段10` 的 E0 形态）；**无「加载更早」**（四家均未见：GO/TX/DEMO/DSH 都是一次取全量元数据）。 | ⏸ Q-SB-16 |
| `sidebar.group.header` | 34px 整行可点**折叠**；文件夹↔chevron；组菜单/组内新建/悬浮卡；未分组桶恒最后（`DSW §2.1 group-row`） | 置顶/文件夹/按天 分区（`GO §2.7 字段2`） | 分区标题（最近/pinned/项目/Bot/远程），**不可折叠**（E2）（`TX §2.5`） | 今天/昨天/7 天内/30 天内/按月 + **sticky** 组头（`DEMO §2.18 字段1/8`；`sessionGroups.ts:9-33`） | 分组头 置顶/今天/…，不可折叠（`DR:83-87`；`LEG §2.6 row.select 字段7`） | **C-12** | **时间分档标题：不可折叠、不 sticky、无菜单、无组内新建**；**置顶区恒最前**（LEG E0 + TX pinned E0）。反证：①若照 DSH 做可折叠组头，时间档不是容器，折起「今天」会让用户以为今天没有会话；②若把置顶放进所在天分组，跨天置顶会被拆散（LEG 的置顶是独立组头，`DR:83-87`）。档位取**旧 myco 四档（今天/昨天/本周/更早）**：用户要求「最终外观与旧结构一致」（M2 §0.2-3），且 DEMO 五档只是另一种实现（§3）。 | ⏸ Q-SB-13 |
| `sidebar.session.row` | 32px 整行 `role=treeitem`+`aria-selected`；单击开；选中=**主视图 retained**（不是最后点击）；归档灰化+toast；空白行特例；**无键盘**（`DSW §2.1 session-row`、`§5`） | 44 整行；长按多选；当前行主色淡底；状态点（`GO §2.7 字段3/6`） | miuix 行密度、整行可点；当前高亮+运行态；只显标题（`TX §2.5 session-row`） | 40 / 移动 44；选中品牌浅底；行是 `div`+onClick 无 role（缺陷）（`DEMO §2.18`） | 2–3 行（标题+片段+时间）；整行点开 / 长按菜单 / 批处理 Checkbox；行无 role（`LEG §2.6 row.select`） | **C-17** | **整行 = 单一命中盒 48dp**（行高即命中盒，`e=0` 不扩边）。理由：G-11 默认 `F=48`（Material + 旧 `TextButton` 48 的 E0），且 C-17 的扩边死结可算——DSH 行距 2px + 视觉 32px，要 48 命中需 `e=8` → `2e=16 > g=2` **必然侵入邻行**；G-11 降级顺序第③步就是「抬视觉高」。**单行内容**（不抄 LEG 的片段第二行）：48dp 装不下两行，两行需 56–64dp，抽屉可视行数减半；三份手机参考（GO/TX/DEMO）都是单行。选中态 = retained id + **左侧 2px 指示条 + 底色**（修 Q-SB-03：DSH 选中与 hover 同色，指针移开就分不清「我在哪」）。归档行灰化+不可打开+toast（DSH E0）。空白草稿行特例（无 ⋯、无时间）。 | ⏸ Q-SB-14 |
| `sidebar.session.title` | 14/20 flex；**双击标题=改名**（`stopPropagation` 不打开）；hover 跑马灯 0.03px/ms + 两端 12px 渐隐 + 溢出 ≤8px 不动（`DSW §2.1 session-title`） | 只显标题，不放左侧图标（`GO §2.7 字段2`） | 单行 ellipsis（`TX §2.5 session-row`） | 单行 ellipsis；重命名内联（`DEMO §2.18`） | 标题可横向拖动查看；改名走对话框（`LEG §2.6 row.select`、`rename.save`） | — | **单行 ellipsis（4/4）**；**不做跑马灯、不做双击改名**（§4）。反证：触屏没有 hover，跑马灯无触发通道；双击在触屏与「缩放/单击开会话」冲突——MOB 全站用 `touch-action:manipulation` 消双击缩放正是这条 E0。改名统一走「长按/⋯ 菜单 → 居中 Modal」。 | ✅ 采用建议 |
| `sidebar.session.meta` | 16px 状态槽 + **相对时间**（刚刚/5分钟…）；有 trailing 状态文案时时间被替换并 `aria-hidden`；状态源 = `running / pendingInteraction / completionUnread`（`DSS §1.1`；`DSW §2.1 session-row 字段6/10`） | 呼吸点 / 待确认黄字 / 未读灰点（`GO §2.7 字段6`） | 运行态指示；未读/新消息 **E2 未核实**（`TX §2.5 session-row 字段6`） | 无时间、无状态（仅标题）（`DEMO §2.18`） | 时间靠右 + 片段（`LEG §2.6 row.select 字段2`） | — | **相对时间右对齐（DSH E0）+ 运行中状态点 + 视觉隐藏文案（G-08）**。运行态可由 bridge 流式事件推导（`LEG` `MA:726-730` streaming E0）。**未读/待确认不做**（§4）：myco core 无 unread/approval 字段（`LEG §3` 后端缺口 4/9），自造第四种状态违背 DSH 的「不要自造状态」（`DSS §1.1`）。片段不进行，只在搜索结果第二行（DSH E0）。 | ⏸ Q-SB-19 |
| `sidebar.session.actions` | hover 才出 `⋯` + 归档 + 置顶（16×16、gap10）；出现时**时间被隐藏、标题可用宽变化**（`DSW §2.1 session-actions`） | 行内无动作键（长按多选）（`GO §2.7 字段4`） | 行尾「更多」40dp（E2 未逐条核实）（`TX §2.5 session-row 字段3`） | `⋯` hover 显 / `@media(hover:none)` **常显**，`IconButton xs=28`（`DEMO §2.18 字段2`） | `⋯` **常驻** TextButton + 长按等价（`LEG §2.6 row.menu-open`） | **C-SB-01** | **常驻单个 `⋯`，48×48 布局盒（视觉图标 24，e=0 不扩边）**；归档/置顶**只进菜单**。理由：①G-02——hover 显隐会让标题宽在指针进出时跳动（DSH 自己写在字段 7），而 G-02 的目标就是「兄弟零重排」；②G-11——三个 16×16 键在触摸端做不到 F，并排还要 B=8dp 隔离，一个 48 盒最省；③触屏可达：`DEMO` 用 `hover:none` 兜底常显、`LEG` 直接常驻，两家手机端都证明「常驻」是触屏的必选。嵌套按键 `stopPropagation`（DSH E0），补 `aria-haspopup/expanded`（DSH 缺=修）。 | ⏸ Q-SB-15 |
| `sidebar.session.menu` | 4 项 pin(100)/rename(200)/fork(300)/archive(400)；归档行 pin 不渲染、archive 变「取消归档」；运行中归档→停止确认；触发器缺 aria（`DSW §2.1 session-menu`） | 行菜单含文件夹/置顶/归档/删除（`GO §2.7 字段5`） | 重命名/删除/钉选/隐藏 bot（`TX §2.5 session-row 字段4`） | 重命名/删除（删除 danger）（`DEMO §2.19`） | 置顶/归档/重命名/删除（删除 error 色）（`LEG §2.6 row-menu.actions`） | —（fork 无对应域） | **4 项：置顶 / 重命名 / 归档 / 删除（软删）**，顺序对齐 DSH order（100/200/400）+ LEG。**不做 fork**（`LEG §3` 命令表无 fork，§4）；「隐藏 bot 会话」无域不做。归档行只留「取消归档」且不渲染置顶（反证：DSH 原文，pin/archive 互斥，留着会静默失败）。项高 48（C-BP-01）；补触发器 aria。运行中会话归档是否要「停止并归档」确认 → `Q-SB-21`。 | ✅ 采用建议（运行中归档挂 Q-SB-21） |
| `sidebar.session.hover-card` | 800ms 出卡：标题/路径/时间/状态 + 「复制标题」；菜单打开或拖拽中禁用；**触屏不可达**（Q-WS-07）（`DSW §2.1 session-hover-card`） | 无 | 无 | 无 | 无 | —（无互斥） | **不做（§4）**：四家里只有 DSH 有，它是鼠标 hover 的补充面；触屏无 hover（`DSW §4 Q-WS-07` 自述）。「复制标题」首版不进（低频，可后续加到行菜单）。反证：做了在手机上永远不触发 = 死代码。 | ❌ 不做（§4） |
| `sidebar.session.reorder` | HTML5 `draggable`，同组+同置顶块，**无触屏等价**（Q-WS-10）（`DSW §2.1 reorder`） | 未见 | 拖动排序 E2 未实现（`TX §2.5 session-row 字段4`） | 无 | 无 | — | **不做（§4）**：四家手机端零 E0；HTML5 drag 在触屏浏览器不工作（`DSW §4 Q-WS-10`）。排序 = `updated` 倒序 + 置顶区；反证：做一套触屏长按拖拽 = 与「长按=多选/菜单」两条手势打架（C-SB-03）。 | ❌ 不做（§4） |
| `sidebar.session.rename` | 居中 Modal 挂 `shell.overlay`（**不在行内**，行卸载不影响）；空名 blocked；IME composing 忽略；失败 `role=alert`（`DSW §2.1 session-rename-dialog`） | 菜单→重命名，**表单形态未取证** | 菜单→`renameSession`，**表单形态未取证** | **内联 input**：回车提交 / Esc 放弃 / 空值不提交（`DEMO §2.18/2.19`） | `AlertDialog` + 输入框；空名/超 80 字后端拒绝但 UI **丢弃错误**（缺陷）（`LEG §2.6 rename.save`） | —（对话框 vs 内联可并存，选一） | **居中 Modal**（危险/表单类按 C-BP-03 分工；DSH + LEG 两家）。挂 `shell.overlay`：反证=DSH 原文「挂行内，侧栏一折叠行就卸载、改名框跟着消失」。空名 blocked + **前端先校验 80 字**（修 LEG 缺陷：后端报错被丢弃，用户看到框关了但标题没变）；IME composing 忽略（DSH E0）。 | ✅ 采用建议 |
| `sidebar.session.delete-confirm` | 无删除（只有归档）（`DSW §2.1 session-menu`） | 归档/删除在行菜单与选择动作里（`GO §2.7 字段5`），确认形态未取证 | 删除走 `deleteSession`，表单未取证 | 删除确认 Modal；`deletePending` 期间按钮禁用；**无 undo**（自评「全仓唯一没有 undo」）（`DEMO §2.20`） | 确认框「会进入回收站，可撤销」+ Snackbar 撤销 + 回收站（`LEG §2.6 delete.confirm`） | —（删除语义已由 M6 定） | **沿用旧 myco：软删 + 二次确认 + Snackbar 撤销 + 回收站**（与 `M6:278` 「会话删除 = 软删 + Snackbar 撤销 + 回收站 ✅ 采纳」同口径）。危险操作居中 Modal + danger 主键（C-BP-03）；pending 期间不可关（反证=`DEMO` 原文「弹窗关了但删除还在跑」的错觉）。「删除全部」归 settings（M6）。 | ✅ 采用建议 |
| `sidebar.bin` | 无回收站；用归档三档筛选（default/only/show）替代（`DSW §2.1 view-options`；`tree.ts:226-255`） | 归档在行菜单 + 状态筛选；回收站**未取证** | 隐藏 Bot 会话过滤，不是回收站 | 删除永久，无回收站（`DEMO §2.20`） | 归档页/回收站：行 标题+时间+恢复/永久删；永久删二次确认；**归档页也显示永久删除**（Q-LEG-02）（`LEG §2.6 bin.*`） | — | **保留软删+回收站（M6 已把入口移到 settings「会话数据」，列表本体归本面板，`M6:167`）**：列表复用 `session-row`（不可打开）+ 行内「恢复」+「永久删除」（二次确认，不可撤销）。**归档页只给「恢复」、不给永久删除**：反证=归档≠删除，旧 myco 让归档行一步永久删除（`LEG` 字段 10 + Q-LEG-02），误触即丢会话+附件；须用户确认（`Q-SB-18`）。 | ⏸ Q-SB-18 |
| `sidebar.undo.toast` | 6s hold；最多 2 个动作（撤销 + 查看归档）；同刻只一条；浮层不占位（`DSW §2.1 row-toast`） | 无 | 无 | 无（删除无 undo，自评缺口） | Snackbar「已归档 N 个会话」/「已删除 X」+ 撤销；窗口短（`LEG §2.6 batch.archive`、`delete.confirm`） | — | **6s hold + 撤销**（DSH 的 `holdMs=6000` E0）；批量带 N；最多 2 动作（撤销 + 查看归档）；**挂 `shell.overlay`**（G-01：Snackbar 不得占布局流；且批量操作会关抽屉，挂抽屉内会跟着消失）；新通知替换旧（DSH 同刻一条 E0 反证：父级重渲染若重置计时，通知永不消失）。 | ✅ 采用建议 |
| `sidebar.selection` | 无多选（`DSW` 无批量节点） | 长按进多选；顶部换选择头、底部换动作栏；全选 + 批量归档/删除（`GO §2.7 字段5`） | 无 | 无 | 「选择」→ 已选 N + 全选 + 完成；底部归档/删除；全选=当前 sessions（搜索过滤后）；**批量调用忽略每个返回值**（缺陷）（`LEG §2.6 batch.*`） | **C-SB-03**（长按语义） | **做（保留旧能力 + GO 先例）**：入口 = 头部「选择」（LEG E0；避免长按与菜单抢，见 C-SB-03）；顶部「已选 N / 全选 / 完成」、底部「归档 / 删除」；全选 = **当前可见列表**（沿用 LEG 语义并在文案写明）；**逐条检查返回值 + 失败汇总提示**（修 LEG「忽略返回值仍报已归档 N 个」缺陷）；切换分组/搜索清空选择（反证：跨筛选的隐藏选择会让「已选 N」与可见项不一致）。 | ⏸ Q-SB-18 |
| `sidebar.search` | 区段头内就地展开（wide）；rail 态 36 图标；**250ms 防抖**；查询截 500 UTF-16 码元；Escape 收起并清空；点外部 blur（query 非空不收起）；清空键；快捷键 `session.search`（`DSW §2.1 search`） | **独立全屏页**（搜索框 48 + 结果列表 44+）；实时搜索（防抖）；返回关闭；范围跟随 drawer scope（`GO §2.7 search`） | `SessionDrawerSearch(query/onNew/onCollapse)`（`TX §2.5 字段2`） | **无搜索**（E1 声明，`DEMO §1 #15`） | 输入即查询、**每键一次无 debounce**（缺陷）；空=SessionList / 非空=SessionSearch；`Query.SessionSearch` 搜标题+正文，返回 `snippet/seq/role`（`LEG §2.8 sidebar.search`、`§3`） | —（形态可按断点分工） | **250ms 防抖**（DSH E0；反证=LEG 每键一次会打出 N 次 bridge 往返、结果频繁回跳）；500 码元截断、清空键、外点不收起（DSH E0）；Escape/返回清空退出。**形态按断点分工**：`<840` = 全屏 route（GO E0；反证=320 宽抽屉 + 键盘后结果区只剩两行，`GO §2.7 search 字段13` 原文），`≥840` = 列内联（DSH E0）。范围 = 标题 + 正文（core 的 `Query.SessionSearch` 已支持，`LEG §3`）。 | ⏸ Q-SB-17 |
| `sidebar.search.result` | 48px 两行（状态槽+标题 / 第二行 工作区名+片段）；点击 = 打开 + 清 query + 收起 + `scrollIntoView` **到行**；**无关键词高亮**（未实现）（`DSW §2.1 search-result-row`） | 整行可点；**命中词高亮**；单击结果 = 跳到会话**对应消息**；结果按会话/内容分组（`GO §2.7 search`） | 未取证 | 无 | 只当列表过滤；`seq` 未用于跳消息；无高亮（`LEG §3` 后端缺口 7） | — | 48dp 两行（标题 + 命中片段，DSH E0）；**本地高亮关键词**（GO E0）。反证=DSH 的「无高亮」是因为 Host 片段不带偏移（`DSW §4 Q-WS-08` 原文），而 myco 的 snippet 是本地文本，该条件不成立 → DSH 的缺口不是约束。点击 = 打开会话 + **定位到行**（DSH E0）；**消息级定位 ⏸**（需新增 bridge Query，`LEG §3` 缺口 7，`Q-SB-17`）。 | ⏸ Q-SB-17 |
| `sidebar.search.empty` | 无匹配 `search.noMatches`；加载中骨架 2 行（已有本地命中时 1 行）+ `role=status`；超上限 `search.hasMore`（`DSW §2.1 search 字段10`） | 空态 + 加载中进度（`GO §2.7 search 字段6`） | 未取证 | 无 | 无匹配只显示空列表（无空态） | — | **三态**：空查询提示 / 无匹配文案 / 加载骨架 + `role=status`（DSH E0）；**不做 `hasMore` 状态行**（myco 的搜索结果上限未取证，不自造）。反证：无匹配若什么都不画，用户会以为会话被删了。 | ✅ 采用建议 |
| `sidebar.a11y.keyboard` | 行 `role=treeitem` 但**无 tabindex/无方向键**，改名/归档/置顶/拖拽全指针；`⋯` 无 `aria-haspopup`；触控全线 <44dp（`DSW §4 Q-WS-04`；`DSS §1.2 E`；Q-SB-08） | 行 `Semantics(selected, checked, button)` + 标题/状态拼接标签；筛选胶囊 `Semantics(selected,label)`（`GO §2.7 字段11`） | 行有标题文本；运行态 `stateDescription` **未核实**（E2）（`TX §2.5 session-row 字段11`） | 行 `div`+onClick **无 role/tabindex/aria-current**（缺陷）；菜单 `role=menu` 无焦点管理（缺陷）（`DEMO §2.18/2.19 字段11`） | 行 `combinedClickable` **无 role/selected**（缺陷）；`⋯` 无 contentDescription（`LEG §2.6`） | **Q-WS-04** | **采用 GO 的语义口径并补齐**：行语义 `selected + 状态标签 + 行内动作 aria-label`；**不做 Web ARIA tree 的方向键/roving tabindex**——Android/TalkBack 是线性遍历语义，方向键树是 Web 契约（四家手机端零 E0）；改由 **customActions 暴露「置顶/重命名/归档/删除」**（§5 新增，依据 G-08 + Q-WS-04 的「简化版」建议）。抽屉 route 补 dialog 语义 + 焦点陷阱 + 关闭归还触发键（MOB/DEMO 缺=缺陷，Q-BL-10）。G-08：状态点配视觉隐藏文案。 | ✅ 采用建议（Q-WS-04 落点） |

---

## 3. 补充项（不互斥的差异，不进对照表的冲突列）

| # | 节点 / 事实 | 谁有、谁没有 | 是否采纳 |
|---|---|---|---|
| 3-1 | 折叠态 rail（宽屏窄档） | DSH 56 / GO 68 / TX 84；DEMO 用「收起态浮组」替代；LEG 无（宽屏常驻） | **采纳 56**（M2 §2 sidebar-column 已定；DSH 是唯一带 clamp 的实现，GO/TX 的 68/84 是同类参数）。DEMO 的浮组是「桌面收起后不占位」的取巧，不采纳（宽屏列收起=轨道归 0，与 M2 一致） |
| 3-2 | 抽屉关闭路径集合 + 自动收 + 防连关 | MOB：导航后自动收 + 500ms 守卫 + 60ms 延迟；DEMO：切会话/切布局自动收；GO：返回先结束多选再关抽屉；DSH：无（无抽屉） | **采纳**：返回 / 遮罩 / 拖出 / 导航后自动收（GO+DEMO+MOB 三家 E0）；500ms 防连关 + 60ms 延迟（MOB E0，让 pressed 反馈先发生） |
| 3-3 | 打开前先收键盘 | TX `keyboard?.hide()`（E0）；其余未写 | **采纳**（M2 §C.7 也已定） |
| 3-4 | 滚动条静默 + gutter 预留 | DSH E0（`scrollbar-gutter:stable` + 2s 静默 + 重绑 transparent）；其余未写 | **采纳**（反证：指针进出时整列横移，G-02 同类事故） |
| 3-5 | 搜索形态按断点分工 | DSH：列内联（wide 才渲染）；GO：独立全屏页（手机） | **采纳**（`<840` 全屏 route = GO；`≥840` 列内联 = DSH）。这不是冲突：由 M2 的窗口类决定，同一手机不会同时出现两种 |
| 3-6 | 时间分组的档位 | LEG 四档（今天/昨天/本周/更早）vs DEMO 五档（今天/昨天/7 天内/30 天内/按月）vs GO「置顶/文件夹/按天」 | **采纳 LEG 四档**（现状 + 「外观与旧结构一致」）；DEMO 五档与 GO 按天作为备选记录，不另做档位开关 |
| 3-7 | 置顶区的归属 | LEG：置顶是独立组头（最前）；TX：pinned 分区；DSH：置顶行在其（工作区）组内前置；GO：置顶在列表分区 | **采纳「置顶区恒最前」**（LEG+TX）；理由：时间分组下，组内前置会把跨天置顶拆散 |
| 3-8 | 状态点的可见语义 | DSH：三态（running/pendingInteraction/completionUnread）+ 视觉隐藏文案；GO：呼吸点/黄字/灰点；TX：运行态；DEMO：无 | **部分采纳**：只做「运行中」+ 视觉隐藏文案（G-08）；未读/待确认无后端字段（§4，`Q-SB-19`） |
| 3-9 | 归档行不可打开 + toast | DSH E0（点击 toast「已归档不可打开」）；其余无 | **采纳**（反证：打开的是空壳会话，用户以为丢内容） |
| 3-10 | 空态两种文案 | DSH：无会话 / 归档-only（带「查看其它」）；LEG：还没有会话 / 没有匹配 | **采纳两套文案**（无会话 / 无匹配）；「查看其它」随归档筛选一起不做（§4） |
| 3-11 | 搜索快捷键（宽屏） | DSH `session.search`（E0）；其余未写 | **采纳**（宽屏键盘入口，成本极低） |
| 3-12 | 会话行 ⋯ 的 aria 契约 | DSH 缺 `aria-haspopup/expanded`（自评缺口）；未见其它家 | **采纳「补齐」**（G-08 精神；反证：只给按钮不给状态，读屏不知道会弹菜单） |
| 3-13 | 抽屉/列切换不重建内容 | TX `movableContentOf` + `rememberSaveable`（E0）；DSH 右栏「整列平移、内容保持挂载」（`DSW §0.5`） | **采纳**（M2 §3-2 已定；侧栏列表状态随断点切换保留） |
| 3-14 | 跨筛选的批量选择 | LEG：全选=当前 sessions（搜索过滤后）；未写「筛选变化后是否清空」 | **采纳「切换分组/搜索清空选择」**（见 §2 selection 反证） |
| 3-15 | 搜索「查看归档/查看其它」动作 | DSH：结果含归档行 + 行内「取消归档」；toast 带「查看归档」 | **不做**：归档查看入口在设置「会话数据」（`M6:167`），不在侧栏再开一条路（避免双入口 + 菜单膨胀） |
| 3-16 | 身份区/环境文案（「本地优先 · 离线可用」、host/网页版） | GO：底部身份栏（host/网页版/设置）；LEG：底部「本地优先·离线可用」；DEMO/TX：只留设置 | **不做**（§4）：myco 无 host/网页版概念；LEG 的文案属营销/状态，首版不搬（若要保留，归 `settings` 的关于页） |

---

## 4. 明确不做（参考里有的）

### 4.1 能力不做的（逐条写理由）

| 能力 | 出处 | 不做的理由 |
|---|---|---|
| 全局面板行 nav / 品牌行（wordmark）/ 三平台折叠形态（Windows caption 0 宽、macOS `shell.leading` 席） | `DSS §2.1 column/panel-row`、`§0.2②` | myco 无插件面板体系，面板入口由 M2 的邻接 route 图承担（M2 §A.0）；无桌面窗口 chrome（Android-only）；rail 统一为 56。 |
| 工作区分组 + 工作区菜单（改名/删除）+ 「＋添加工作区」目录流 + 「未分组」桶 | `DSW §2.1 group-row/group-menu/add-workspace`；`GO §2.7 字段2`；`TX §2.5` | myco 现无工作区域（`10-panels.md` `workspace` 面板 ❌ 低优先；core 无 workspace 类型，`LEG §1.3` 目录表无该包）；做它需新增 Host 数据契约（Q-WS-01/03）。分组改为时间分档（C-12）。 |
| 组头折叠 / 组内新建会话 / 组尾配额「展开更多 N 条」/ 虚拟滚动替代 | `DSW §2.1 group-row/overflow`；`DSW §2.1 overflow 字段13` | 时间分档不是容器，折叠会隐藏「今天有哪些会话」（C-12）；配额是 DSH 无虚拟化时的补丁，Compose `LazyColumn` 已虚拟化（C-18，§2 list）。 |
| 派生 fork 菜单项 | `DSW §2.1 session-menu`（fork order 300） | core 命令表**无 fork**（`LEG §3` 全文 22 个 Command 无派生）；做它 = 新增后端能力，不在 P2 范围。 |
| 会话行「文件夹」分组 / bot 会话隐藏 / 项目与远程服务分区 | `GO §2.7 字段2/5`；`TX §2.5` | 无对应域（myco 无 folder/bot/remote 概念）。 |
| 状态筛选胶囊（横滚）/ 安全模式横幅 / 工作区切换 | `GO §2.7 字段2` | 无对应域；安全模式与筛选在 myco 没有可筛维度（分组=时间、归档入口在设置）。 |
| 右侧快速面板 quick-panel / 会话详情右栏（session-detail） | `GO §2.7 quick-panel`、`§2.7 session-detail` | 右栏首版不做（`Q-SB-12`）；会话详情无对应面板。 |
| 底部导航瓦片（AppNavigationSection 2 列 + 全宽终端） | `TX §2.5 字段2/3` | myco 无底部导航（M2 §A.0：面板入口=邻接 route 图）；终端面板首版不做。 |
| 视图选项菜单（分组工作区/排序 manual/归档三档） | `DSW §2.1 view-options` | 三组都不做：分组固定时间档（C-12）、排序固定 updated（无 hand-order 需求）、归档查看入口在设置（`M6:167`）。反证：留一个全默认值的菜单=死菜单。 |
| 行悬浮卡 800ms（含复制标题）/ 标题跑马灯 / 双击标题改名 / 行尾「归档+置顶」双悬浮键 | `DSW §2.1 session-hover-card/session-title/session-actions` | 全是鼠标 hover 面；触屏不可达（`DSW §4 Q-WS-07` 自述 hover 卡触屏不可达）；被「常驻 ⋯ + 长按菜单」替代（C-SB-01/03）。 |
| 会话拖拽排序（HTML5 drag，同组同置顶块） | `DSW §2.1 reorder`；`DSW §4 Q-WS-10` | 四家手机端零 E0；触屏浏览器不支持 HTML5 drag；且与长按手势冲突（C-SB-03）。 |
| 归档三档筛选（default/only/show） | `DSW §2.1 view-options`；`tree.ts:226-255` | 归档查看入口在设置「会话数据」（`M6:167`），侧栏不再开第二条路（双入口会让「我刚归档的去哪了」有两个答案）。 |
| 收起态「浮组」（桌面收起后 absolute 浮在内容左上） | `DEMO §2.22` 字段 7 | 宽屏收缩走 rail/0 宽（DSH/GO/TX 三家）；浮组会永久盖住内容左上，且 DEMO 自己承认「收起后没有设置入口」（`DEMO §2.23` 字段 10）。 |
| 未读 / 待确认（pendingInteraction）状态点 | `DSS §1.1`（DSH 三态）；`GO §2.7 字段6` | myco core 无 unread / approval 字段（`LEG §3` 后端缺口 4/9）；自造第四种状态违背 DSH 的「不要自造第四种状态」（`DSS §1.1` 原文）。见 `Q-SB-19`。 |

### 4.2 参考实现自身缺陷（按 `_conflicts.md` §判定口径：**不作为「另一种解」**，也不抄）

| 缺陷 | 出处 | 我们的处理 |
|---|---|---|
| DSH 会话行 `role=treeitem` 但**无 tabindex/无方向键**，改名/归档/置顶/拖拽全指针 | `DSW §4 Q-WS-04`；`DSW §2.1 session-row 字段11` | 不抄；补行语义 + customActions（§2 `a11y.keyboard`、§5） |
| DSH 选中态与 hover 态**同色**（`interactive-bg-hover`），指针移开就分不清自己在哪 | `DSS §2.1 panel-row 字段6/13`（Q-SB-03）；`DSW §2.1 session-row 字段6` | 不抄；选中 = 左 2px 指示条 + 底色（§2 row） |
| DSH 行内 `⋯` 缺 `aria-haspopup/aria-expanded` | `DSS §1.2 E`；`DSW §2.1 session-actions 字段11` | 不抄；补齐（§3-12） |
| DSH 全线触控尺寸 <44dp（28/16/32/34），4px 键间距低于隔离带 | `DSS §4 Q-SB-08`；`DSW §2.1 session-actions 字段3` | 不抄；行级 48 布局盒（G-11；§2 row） |
| mobile-adapt 文档 `R1/:6` 声称「sidebar becomes a swipe drawer」，代码**无 touch 监听**（E1 不成立） | `MOB §0-4`；`MOB §3.1 drawer 字段4` | 不抄「无侧滑」；采纳旧版 96dp 内容区拖拽（M2 §C.5） |
| mobile-adapt 抽屉**无 `role=dialog`/`aria-modal`/焦点陷阱**；汉堡无 `aria-expanded` | `MOB §3.1 drawer 字段11`、`hamburger 字段11` | 不抄；抽屉 route 补 dialog 语义 + 焦点陷阱 + 关闭还焦点（Q-BL-10） |
| mobile-adapt 伪元素扩边**无相邻隔离**（重叠带最大 24px） | `MOB §0-3` | 不抄；一律「命中盒=布局盒」，不扩边（G-11） |
| chat-demo 移动抽屉**遮罩无背景色**、**无开合动画**、无 focus trap、行 `div` 无 role、菜单无键盘、删除无 undo | `DEMO §2.22 字段6/9/11`；`DEMO §2.18 字段11`；`DEMO §2.20` | 不抄；遮罩 0.32（§2 scrim）、开合 spring（M2 §C.8）、语义与撤销按本文件 |
| 旧 myco 行 `combinedClickable` 无 role/selected；`⋯` 无 contentDescription | `LEG §2.6 row.select 字段11`、`row.menu-open 字段11` | 不抄；补语义（§2 a11y） |
| 旧 myco 改名失败错误被丢弃、批量归档/删除**忽略每个返回值**仍报「已归档 N 个」、搜索**无防抖**每键一次查询、搜索无高亮/不跳消息 | `LEG §2.6 rename.save 字段10`；`LEG §2.6 batch.archive 字段10`；`LEG §2.8 sidebar.search`；`LEG §3` 缺口 7 | 不抄；逐条修（§2 rename / selection / search） |
| 太墟两套遮罩值（0.12 / 0.32）自评不一致 | `TX §2.1 drawer 字段6`；Q-TX-12 | 不抄不一致；统一 0.32 单 token（§2 scrim） |
| 太墟会话分区「不再按日期分组」（E1）与 LEG/DEMO/GO 的时间分组相反 | `TX §2.5 字段7`（E1 `ARCHITECTURE_RULES.md:51`） | 这是太墟的产品取舍，不是本工程约束；本工程按 C-12 走时间分档（§2 group） |

---

## 5. 新增节点（参考里都没有、myco 要新设计）

| 节点 ID | 为什么需要 | 依据（用户要求 / 平台规范 / 我方约束） | 代价 |
|---|---|---|---|
| `sidebar.shell.route` | 四家的侧栏都是「布尔 open/overlay」，没有「抽屉是导航栈里一层」的概念；myco 的无限抽屉必须让侧栏参与 M2 的覆盖栈，否则返回语义/保活/断点迁移要另写一套 | 用户「无限抽屉」硬要求；M2 §A.3 方案 1 + §C.3「抽屉是 route（不是浮层），返回=pop」；TX 单栈 + `clearAndSetSemantics` 的 E0 证明栈模型可行 | 需与 M2 的 `PanelRoute`/WidthSpec/dispatcher 一起实现；此节点本身不含 UI |
| `sidebar.a11y.keyboard` | 四家都没解决「会话列表的键盘/读屏入口」：DSH 无键盘、DEMO 无 role、LEG 无 role、GO 只有部分语义；myco 是 Android 原生，需要一套**不照抄 Web ARIA tree** 的语义契约 | Q-WS-04（自评缺口 + 建议 B 简化版）；G-08（运行态必须有 a11y 文案）；Android/TalkBack 线性遍历语义（平台惯例） | 每行要挂 customActions；读屏播报文案要写；真机 TalkBack 验收成本 |

> 计数：**2 个新增节点**（其余 26 个都能在 ref 里找到至少一家 E0）。

---

## 6. 待确认（阻塞项）

> 编号从 `Q-SB-12` 起（`Q-SB-01…11` 是 `ref/dsh-sidebar.md` 的 ref 级问题，本文件已裁定/转交如下：`Q-SB-01`→本文件 C-10 裁定；`Q-SB-02`→已由 R1e 关闭（`_conflicts.md`「已澄清」）；`Q-SB-03`→随「全局面板行不做」消解（§4.1）；`Q-SB-04`→归 `composer`；`Q-SB-05/06/07`→归 `composer`/`chat`；`Q-SB-08`→归 Q-03/G-11；`Q-SB-09/10/11`→随「右栏不做」/其它面板消解）。未拍板行不得进入 P3。

| ID | 问题 | 两解及各自后果 | 建议 |
|---|---|---|---|
| `Q-SB-12` | **右栏（base.sidebar-right）首版做不做、做哪几种 tab？**（承 C-19 / Q-WS-14） | A 做：files（文件树，12 E0）/ text（文档预览，35 E0）/ terminal（13 E0）/ browser（9 E0）——每一种都是一整套面板，代价极大且 myco 无对应后端（PTY/内嵌浏览器未定）。B 不做：首版侧栏=会话导航，右栏轨道由 M2 预留（`slots.right-rail` 已定宽/断点/全屏覆盖形态），内容 tab 另立 P3 面板——代价=宽屏双栏能力首版空置（但旧 myco 本来也没有右栏） | **B：首版不做（⏸）**。若将来做，先做 `files`（与 workspace 面板重叠，待其定稿）+ `text`；terminal/browser 不做。理由：Q-06 已拍板的首版面板范围不含右栏；手机端右栏必须变全屏覆盖 route（`MOB §3.1 right-sidebar.layer`：桌面右栏在手机宽度轨道被移除→功能不可达是它修过的 bug）。 |
| `Q-SB-13` | **会话分组默认：DSH 工作区分组 vs 旧 myco 时间分组；要不要「视图开关」？**（C-12 落点） | A 工作区分组：需新增工作区域（当前 `workspace` 面板 ❌ 低优先、core 无类型），代价=先做后端 + 一个面板；B 时间分组：零后端改动（LEG 现状），代价=与 DSH 的默认不同、未来接工作区要再切。视图开关=第三解：两种都做（多一层菜单 + 持久化 + 空桶语义） | **B：时间分组（旧 myco 四档 今天/昨天/本周/更早 + 置顶区恒最前）为默认**；**首版不做视图开关**（`Q-SB-13` 待将来工作区面板落地后再加）。反证：做工作区分组=把一个未实现的产品域当成默认值，用户看到的永远是「未分组」桶（`DSW §4 Q-WS-20` 同款问题）。 |
| `Q-SB-14` | **会话行结构/行高：单行 48dp vs 双行（标题+片段）vs 32–36 视觉+扩边？**（C-17 落点，依赖 Q-03 的 `F`） | A 单行 48：命中达标、密度≈14 行/屏，代价=看不到片段；B 双行 56–64：信息多，代价=可视行数减半、抽屉里一天会话要滚；C 32–36 视觉 + 扩边：几何上**不可能**（行距 2px → `2e≤2` → 命中最多 34px，G-11 ②），除非把行距抬到 ≥20px（过程区那种做法，列表会变稀） | **A：单行 + 行高 = `F`（建议 48，G-11 默认值）**；片段只在搜索结果第二行出现（DSH E0）。若 Q-03 最终定 `F=44`，整行降为 44（**不混用两种**）。 |
| `Q-SB-15` | **行尾动作显隐 + 长按语义：常驻 ⋯ + 长按多选（A）vs hover/长按=菜单 + 头部「选择」（B）vs DSH 三键悬浮（C）**（C-SB-01/C-SB-03 落点） | A（推荐）：⋯ 常驻 48 盒、长按=进多选（GO 模式）——代价=老用户的长按=菜单习惯要改，收益=菜单仍可点 ⋯、多选最快；B：⋯ 常驻、长按=菜单（LEG 模式）——代价=多选只能靠头部「选择」，效率低一档；C：hover 出三键（DSH）——触屏不可达，且 G-02 反证（标题宽跳变），不可行 | **A**：常驻 ⋯（菜单入口）+ 长按=多选（GO E0 + 批量操作效率）。前提=批量选择进首版（`Q-SB-18`）。若批量不做，则长按=菜单（B）。 |
| `Q-SB-16` | **列表加载策略：一次性元数据 + LazyColumn（A）vs DSH 每组 5 条 + 展开更多（B）**（C-18 落点；**myco 会话量级未取证**） | A：零学习成本、与旧 myco 一致、Compose 已虚拟化；风险=会话数极大时首屏 bridge 查询慢（量级未知）。B：首屏行数可控；代价=多一套配额状态 + 折叠组重置语义，且「运行中/空白不占配额」等规则要全抄，用户还会问「我另一条会话去哪了」 | **A**（理由是 DSH 配额的存在前提不成立：它没有虚拟化）；但需用户确认 myco 的会话量级上限。若量级 >500 且 bridge 列表慢 → 改为按组懒加载（仍不是 5+5 配额）。 |
| `Q-SB-17` | **搜索形态与定位深度：手机全屏 route（A）vs 抽屉内联（B）；点击结果只定位到行（①）vs 跳到消息（②）** | A：GO E0，键盘弹出后结果区仍够高；代价=搜索变成一层 route（返回要 pop）。B：DSH E0，少一层；代价=320 宽抽屉 + 键盘后结果区只剩两行（GO 原文反证）。②需要新增 bridge Query（core 的 SessionSearch 返回 `seq` 但无「打开会话并滚到 seq」的 Query，`LEG §3` 缺口 7）；①零后端 | **A + ①**：手机全屏 route、宽屏列内联；点击只定位到行。②留待新增 Query（记入 `LEG §3` 缺口，进 P4 需求池）。 |
| `Q-SB-18` | **批量选择首版做不做；回收站列表本体范围（归档页是否给「永久删除」）** | 批量：做（LEG 能力不丢 + GO 先例）/不做（少一个模式 + 顶部头切换 + 底部条，代价=会话整理只能逐条）。回收站：归档页只给恢复（A，安全）/恢复+永久删除（B，LEG 现状，一步误删风险） | **批量做（A）**；**回收站归档页只给恢复（A）**，永久删除只在「回收站」视图出现（修 LEG Q-LEG-02 隐患）。回收站列表本体归本面板（`M6:167` 已明确入口归 settings）。 |
| `Q-SB-19` | **要不要「未读 / 待确认」状态点？** | A 不做：core 无字段（`LEG §3` 缺口 4/9），只有运行态可推导；代价=后台完成的会话没有提示。B 做：要新增 unread/approval 持久化 + 「主视图不产生未读」规则（DSH `ui-session` 三态），代价=后端+面板联动 | **A 不做（首版）**；只做「运行中」状态点（可从流式事件推导，`LEG` E0）。B 需要后端先行，不在 P2。 |
| `Q-SB-20` | **手机侧栏形态与 M2 覆盖栈的衔接：覆盖式抽屉 route（A）vs 照抄 DSH grid 列挤压（B）**（C-10 / Q-SB-01 落点，**本任务最高优先**） | A：手机=覆盖抽屉（遮罩+96dp 手势+返回键）、宽屏=常驻列；抽屉是 M2 栈里一层 route，返回先关抽屉（代价=需 M2 栈落地）。B：全平台照抄 DSH；好处=100% 有据；坏处=手机窄屏把中心列压到 0 宽（`columns.ts:58`）、无遮罩/无手势/无返回（`DSS §0.2③④`），与「无限抽屉」方向相反 | **A**。四家手机实现（GO/TX/MOB/DEMO）全部是覆盖抽屉，DSH 的 grid 列只是桌面实现——C-10 实为「断点分工」而非真冲突（M2 §2 shell 行同结论）。需用户确认的一条：**抽屉打开时系统返回先关抽屉**（M2 §C.3 链），而不是先 pop 下层面板。 |
| `Q-SB-21` | **运行中会话被归档时，要不要「停止并归档」确认框？** | A 照 DSH：先弹确认列运行中活动，确认才停+归档（`DSW §2.1 archive-confirm-dialog`）；B 照旧 myco：直接归档（后端是否停工作**未取证**）；C 归档按钮在运行态禁用 | **待真机/后端取证后定**（记「未取证」）：先按 B 实现并对失败给明确错误；若实测归档会中止运行 → 升级为 A。DSH 的 A 依赖 Host 的 `session-active` 拒绝语义，myco core 无对应码（`LEG §3` 无该错误码），不能照抄。 |

> 计数：**10 个阻塞项**（`Q-SB-12`…`Q-SB-21`）。其中 `Q-SB-20`、`Q-SB-13`、`Q-SB-14` 建议优先拍板。

---

## 7. 自检

- [x] 每个节点在对照表里有一行（28/28；`sidebar.shell.route`、`sidebar.a11y.keyboard` 在 §5 也登记了「为什么是新设计」）。
- [x] 每条「建议」都引了 G 规则（G-01/G-02/G-04/G-08/G-11）或给了反证；容器类结论一律引 M2。
- [x] 没有 E2 推测出现在 §2/§3：旧抽屉视觉参数、myco 会话量级、core 归档运行中行为、搜索是否有结果上限、`stateDescription` 均写「未取证」并进 §6。
- [x] 明确不做项都写了理由（§4.1 能力 14 条 + §4.2 参考缺陷 12 条）。
- [x] 阻塞项已编号 `Q-SB-12`…`Q-SB-21`（10 条）。
- [x] 冲突：**7 条**——已登记的 `C-10 / C-12 / C-17 / C-18` 全部落点；新登记 `C-SB-01`（行尾动作显隐）、`C-SB-02`（开合入口位置）、`C-SB-03`（长按语义），待 Lead 收进 `_conflicts.md`（我不改那份文件）。
- [x] 与 M2 衔接：抽屉=覆盖栈的一层 route（M2 §C.3）、宽度消费 M2 §C.4、手势消费 M2 §C.5、浮层槽用 M2 `slots.overlay`、保活/语义屏蔽引 M2 `stack-lifetime`/`a11y.covered`。
- [x] 与 M6 衔接：回收站**入口**归 `settings`（`M6:167`），**列表本体**归本面板；删除=软删+撤销+回收站与 `M6:278` 同口径。
- [x] 未改 `ref/**`、`00-global-rules.md`、`_conflicts.md`、`_merge-*.md`、`open-questions.md`、`decision-pack.md`、其它 `p2/*.md`；未改 app；未 commit。
- [ ] **需 Lead 处理（跨面板，不写在这里）**：① `docs/spec/10-panels.md:14` 的 `sidebar` 证据包缺 `ui-workspace`（`dsh-sidebar.md` Q-SB-02 已指，R1e 已补事实库但注册表行未改）；② 新冲突 `C-SB-01/02/03` 收进 `_conflicts.md`；③ `Q-SB-21` 的「core 归档运行中会话」需要后端/真机取证；④ `Q-SB-17②` 的消息级定位需要新增 bridge Query，记入 `LEG §3` 缺口 7 的 P4 需求池。

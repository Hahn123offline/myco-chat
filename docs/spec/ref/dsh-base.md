# DSH 基础包事实库（R1a：ui-primitives / ui-layout / ui-dockkit / ui-slots / ui-theme / locale）

> 审计对象：`E:\deepseek-harness\packages\client` 下 6 个基座包（只读 `src/`，跳过 node_modules）。
> 盘点分母：`python tools/ui-audit/ui_audit.py scan --root E:\deepseek-harness\packages\client`（全客户端 1918 文件 / 3060 命中）；
> 本路范围内命中 **530** 条（`ui-primitives/` 420、`ui-dockkit/` 70、`ui-theme/` 24、`ui-layout/` 16、`ui-slots/` 0、`ui-locale/` 0）。
> 事实库：`docs/spec/ref/dsh-base.facts.csv`（537 行：E0 248 / E1 3 / excluded 286；其中 7 行属 `locale/`，不在 `--only-prefix` 范围，故对账计 530）。
> 对账结果：`_ws/out/coverage-dsh-base.md` → **covered 248 / excluded 282 / uncovered 0 / stale 0**。

**路径缩写**（全部相对 `E:\deepseek-harness\packages\client\`）：

| 缩写 | 实际路径 |
|---|---|
| `P/` | `ui-primitives/src/` |
| `L/` | `ui-layout/src/client/` |
| `D/` | `ui-dockkit/src/components/` |
| `E/` | `ui-dockkit/src/engine/` |
| `T/` | `ui-theme/src/` |
| `C/` | `locale/src/client/`（**注意**：DSH 没有 `ui-locale` 包，见 §4 Q-BASE-01） |

---

## 0. 一页结论

> 每条一句取舍 + 证据行。**推测一律不在本节**（进 §4）。

1. **披露行没有"扩边"，命中矩形就是 24px 高那根行盒**。`.row` 高 `calc(24px + --dsh-content-font-delta)`、`overflow:hidden`，无 padding 扩边、无伪元素扩边、无 44dp 下限（`P/DisclosureRow.module.css:30-44`）。Q-03 在 DSH Web 侧**没有现成答案可抄**，必须 myco 自己拍板（→ Q-BASE-03）。
2. **DSH 自己就并存两种披露命中规格，差 8 倍面积**：`expandOnRowClick` 时整行可点（宽满行 × 24px，`P/DisclosureRow.tsx:96`）；否则唯一开关是 `16×16(+delta)` 的图标 `<button>`（`padding:0;border:none`，`P/DisclosureRow.module.css:50-63` + `P/DisclosureRow.tsx:99-108`）。**给不给 `expandOnRowClick` 是调用方的决定，不是组件默认**（默认 `false`，`P/DisclosureRow.tsx:50`）。
3. **`aria-expanded` 有两个互斥来源、且没有 `aria-controls`**：整行模式挂在 `div[role=button]`（`:95`），图标模式挂在 `button`（`:104`）；两者由三元分支保证同时只存在一个（`:99-108`）。展开体没有 id、没有 `aria-controls` 关联（`:86-123` 全文未见）→ 读屏能报展开态，但无法跳转到展开内容。
4. **披露行展开/收起完全没有高度动画**：`{open && children}` 条件挂载 + 无任何 height/transform 过渡（`P/DisclosureRow.tsx:121`；`P/DisclosureRow.module.css` 只有 color/opacity 的 100ms）。与 G-10「高内容直接切换」同向：DSH 连低内容也不做（→ Q-BASE-14：myco 是否要在 ≤240dp 补动画）。
5. **披露行是 LTR-only**：leading 用物理属性 `margin-right: 6px`（`P/DisclosureRow.module.css:58`），全文件无 `dir` / `inset-inline` / `margin-inline-*`。大字号靠 `--dsh-content-font-delta` 同步行高、leading 盒、svg 三处（`:36,53-54,70-71,103`），但**标题字号只跟 secondary token**（`:102`）→ 行高按 delta 线性长、字号按档位跳，比例会漂移。
6. **`ui-layout` 不是面板栈，是三列 CSS Grid 壳**：网格 `sidebar px | minmax(center) | rightbar px`（`L/AppFrame.module.css:1-8`、`L/AppFrame.tsx:259-274`）；主区只有一个 `activePanelId`，换面板是**覆盖**不是入栈（`L/stores.ts:16-22,94-96`）；**无返回栈、无历史、无手势返回**（`L/service.ts` 全文只有 selectPanel / toggleSidebar / openRightbar / closeRightbar）。「无限抽屉」的层叠语义必须另找依据。
7. **层叠是一张显式 z-index 阶梯，不是隐式堆叠**：右栏面板 10 → 列宽手柄 11 → `shell.leading` 15 → 框架 overlay 层 20（`pointer-events:none` 的穿透层）→ 全屏右栏 40（`L/AppFrame.module.css:211,231,284` 及注释 `:202-206`）；dockkit 另开 10（停靠）/ 60（浮动）/ 70（菜单，`D/dockkit.module.css:147,159,547-549`）；模态与灯箱 1000（`P/Modal.module.css:6-15`、`P/ImageLightbox.module.css:2-6`）、portal 菜单 1100（`P/Menu.module.css:42-45`）。
8. **`ui-dockkit` 才是真实的多面板层叠机制**：停靠面板上限 4（`E/constraints.ts:10`），浮动面板 z = `floats` 数组下标 + 1、按下即抬到最上（`D/FloatLayer.tsx:177,179`）；浮动尺寸默认 380×300 / 最小 220×140（`E/constraints.ts:16-19`），拖拽阈值 4px（`E/geometry.ts:141`），停靠边缘带 = 面板 25%（`E/constraints.ts:22`）。转场只有遮罩 140ms 淡入（`D/dockkit.module.css:610-621`），**没有位移/尺寸动画**。
9. **token 是三层 + 一条运行时字号轴**：`--dsw-static-*` 原始色板（`T/styles/design-platform.css:5-83`）→ `--dsw-alias-*` 语义别名，`body` 一套、`body[data-ds-dark-theme]` 一套（`:166-282` / `:284-398`）→ 组件只消费 alias / radius / elevation。字号轴：`--dsh-content-font-size`（10–22，默认 14，`T/theme-settings.ts:24-30`）由 boot 脚本与 ThemePresenter 写到 `body`（`T/boot-theme.ts:28-44`、`L/theme-presenter.ts:77-79`），再由样式表派生 `--dsh-content-font-delta = size − 14`、`--dsh-content-font-size-secondary = min(size−1, max(13, size−2))`、`--dsh-content-font-delta-secondary`（`T/styles/gradient-shadow-text.css:56-59`）。
10. **整套基座没有任何 44dp 触控下限**：图标动作 24×24（`P/CodeCard.module.css:56-61`）、开关 36×20（`P/Switch.module.css:5-17`）、模态关闭 28×28（`P/Modal.module.css:87-93`）、dock 控件 28×28 / 标签关闭 20×20（`D/dockkit.module.css:417-425,449-455,511-518`）、字号步进箭头 17×12（`T/client/FontSizeRow.module.css:90-102`）、披露图标按钮 16×16。**移动端命中区规则必须由 myco 新建**（→ Q-BASE-03 披露行 / Q-BASE-04 全局 44dp 策略）。

---

## 1. 包 → 面板归位表

| 包 | 职责（E0 依据） | 对应 myco 面板 / 基座 | 首版是否需要 | 关键证据 |
|---|---|---|---|---|
| `ui-primitives` | 无框架依赖的原子层：Button / Pill / DisclosureRow / FoldToggle / Menu / Modal / Tooltip / HoverCard / Switch / Checkbox / Input / SegmentedControl / Toast / 图标集 / markdown 渲染 | `base.primitives`（10-panels.md §2） | **必须**（披露行、菜单、弹层、图标动作条被所有面板复用） | `P/index.ts` 导出面；本次落 21 个节点 |
| `ui-layout` | 三列网格壳 + 列宽拖拽 + 子槽位声明（sidebar/main/rightbar/shell.bottom/shell.overlay/shell.leading）+ 主题投影 | `base.layout`（框架与槽位） | **必须**（壳），但「无限抽屉」层叠**不能**从这包取依据 | `L/AppFrame.tsx:242-305`；`L/stores.ts:78-146`；`L/service.ts:28-54` |
| `ui-dockkit` | 分栏树（≤4 pane）+ 标签页拖放 + 浮动面板层叠层（z 由数组序决定）+ 拖拽/缩放手势 | `base.layout`（层叠与面板栈；对应「无限抽屉」的**层叠覆盖**部分） | **部分要**：层叠/抬升/尺寸/阈值模型要；分栏（split）myco 首版无多窗格需求 | `D/DockSurface.tsx:209-296`；`D/FloatLayer.tsx:74-193`；`E/constraints.ts:10-22` |
| `ui-slots` | 槽位声明与渲染契约（single/keyed/list、占用唯一、abdicate、scoped store） | `base.layout` 的槽位约束；myco 无插件体系 | **不要实现**，只借约束：一个槽位「register 即独占渲染权」、崩溃可 abdicate | `ui-slots/src/index.ts`、`renderer.ts:110-257`；本包扫描命中 0（无 UI 交互点） |
| `ui-theme` | token（static→alias→specific）+ 字号轴（content/code/terminal，10–22px）+ 字体族 + 深浅色 + corner-shape + focus ring | `base.theme` + `settings.general` 外观/字体行 | **必须**（token 命名与派生规则是所有面板的排版基准） | `T/styles/design-platform.css:1-408`；`T/styles/base.css:18-23`；`T/theme-settings.ts:23-61`；`T/styles/gradient-shadow-text.css:56-63` |
| `ui-locale` | **不存在**（本机 `packages/client` 下无此目录） | `settings.general` 语言行 | 以 `locale` 包替代 | `Test-Path E:\deepseek-harness\packages\client\ui-locale` = False；实际 `locale/src/client/LanguageRow.tsx`（→ Q-BASE-01） |
| `locale` | i18n 运行时 + 语言设置行（36px 选择器按钮 → Menu） | `settings.general` 语言行 | 是（语言切换需要） | `C/LanguageRow.tsx:30-67`、`C/LanguageRow.module.css:29-47` |

---

## 2. 节点事实表

> 字段编号对应 `schema-node.md §1`：1 身份与出现条件 / 2 网格与定位 / 3 命中区 / 4 手势 / 5 行为 / 6 视觉状态 / 7 布局占位与展开形态 / 8 滚动语义 / 9 动效 / 10 边界态 / 11 无障碍 / 12 依据 / 13 反证 + 验收。
> 每条依据都写到 `路径:行`；`E1` 只在仅有注释/README 时使用。

### 2.1 `base.primitives.disclosure` —— 披露行基座（决定 Q-03）

#### `base.primitives.disclosure.row`（整行即开关）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.disclosure.row`。所属：`base.primitives` → 披露行基座。父：任意纵向栈的行容器。即 `DisclosureRow` 的「整行可点」形态：`expandable && expandOnRowClick`（`P/DisclosureRow.tsx:64`）。出现条件：调用方显式传 `expandOnRowClick`（默认 `false`，`:50`）且 `expandable`。同一行内 1 个。本包内无调用点；ui-chat 的 `ReasoningRow` 走此形态（属 R1b 取证范围）。 |
| 2 | 网格与定位 | 根 `.root`：纵向 flex、`width:100%`（`P/DisclosureRow.module.css:9-14`）。`.header` 横向 flex；带 accessory 时 `gap:8px` 且 `.row` 从 `flex:1 1 auto` 收为 `flex:0 1 auto`（`:16-28`）。`.row` 高 `calc(24px + var(--dsh-content-font-delta, 0px))`，行内序列：leading 盒 `16px+delta`、`margin-right:6px` → 标题（`13px / calc(24px+delta)`），字号取 `--dsh-content-font-size-secondary`（`:50-63,100-105`）。 |
| 3 | 命中区 | **矩形 = `.row` 盒本身**：高 `24px+delta`、宽 = 满行（无 accessory）或内容宽（有 accessory）。**无 padding 扩边、无 ::before/::after 扩边**（`:30-40` 只有 `overflow:hidden`，`:42-44` 只加 `cursor:pointer`）。与相邻命中区最小间隔 = accessory 的 8px（`:22-24`）。物理最小边 **24px**，远低于 44dp。 |
| 4 | 手势 | 单击 = 切换（`:96`）。键盘 Enter / Space = 切换，`preventDefault` 防滚动（`:69-73,97`）。长按 / 双击 / 滑动 / 拖拽：**未见实现**（未绑，不得继承父级）。与父滚动仲裁：无自定义，依赖浏览器 `click` 语义（拖动不产生 click）。 |
| 5 | 行为 | `onToggle()` 由调用方注入，组件**不持有状态**（`:13,48`）；`open` 受控，纯渲染（`:87` `data-open`、`:121` `{open && children}`）。幂等/可撤销由调用方实现；组件不做动画、不写存储。 |
| 6 | 视觉状态 | default：文字 `--dsw-alias-label-tertiary`（`:38`）。hover：文字转 secondary（`:46-48`，100ms color）。折叠时 hover：原图标 `opacity:1→0`、chevron `0→1`（`previewChevron` 默认 = `expandable`，`:51,74-81` + css `:78-98`）。展开：leading 换成 `chevron-up`（`:82-84`）。pressed/selected/disabled：**未见实现**。focus：组件自身无样式，焦点环走 `T/styles/focus.css:10-21` 全局规则。 |
| 7 | 布局占位与展开形态 | **形态 A（G-03）**：折叠时只占 header 高；展开时 `{open && children}` 直接挂进 `.root`，**不封顶、不内滚**，下方兄弟整体下移（`P/DisclosureRow.tsx:121`）。**不做尺寸隔离**（本文件无 `contain`）→ G-02 的 `contain:size layout` 由调用方（如 ReasoningRow）另加。 |
| 8 | 滚动语义 | 本组件不是滚动容器；`.row` 的 `overflow:hidden`（`:33`）只裁自身不产生滚动。展开内容随**外层**滚。无 sticky、无吸附（G-06 的 sticky header 由调用方在 `[data-disclosure-header]` 上另加）。 |
| 9 | 动效 | **无展开/收起高度动画**：条件挂载、无 transition（`:121`）。仅两处 100ms：hover 文字色（`:39`）、图标↔chevron 的 opacity（`:84-98`）。`prefers-reduced-motion` 关闭文字色过渡（`:107-111`）。可中断性：无从谈起（无动画）。 |
| 10 | 边界态 | 超长标题：`.title` 无 `nowrap`/`ellipsis`（`:100-105`），由 `.row` 的 `overflow:hidden` 硬裁 → 会切字、无省略号。空标题：允许（无校验）。大字号：行高/leading/svg 随 delta 长（`:36,53-54,70-71`），标题字号跟 secondary 档（`:102`）→ 行高线性、字号跳档。RTL：**未见实现**，`margin-right` 物理属性（`:58`）在 RTL 下把 6px 间隙放到错误一侧。多语言：标题不做断行控制。 |
| 11 | 无障碍 | 整行模式：`role="button"` + `tabIndex={0}` + `aria-expanded={open}`（`P/DisclosureRow.tsx:93-95`）。**无 `aria-controls`、无 id 关联展开体**（`:86-123` 未见）。可访问名 = 行内文本（title + collapsedContent），无 `aria-label`。键盘 Enter/Space 已实现（`:69-73`）。 |
| 12 | 依据 | `E0` `P/DisclosureRow.tsx:13,17,48,50,51,64,69-73,87,93-97,100-108,121`；`E0` `P/DisclosureRow.module.css:16-28,30-48,50-63,74-76,78-98,100-111`。 |
| 13 | 反证 + 验收 | **反证**：若保留现状 24px 命中高度做移动端，一次点击有相当概率落在「滚动 vs 点击」的模糊区，用户会以为点了没反应；若反过来把整行永久做成 44px 高，桌面一屏要少放约 40% 过程行，DSH 的「过程可折叠、不抢正文」意图被破坏。**验收**：`BASE-01` 行高在字号 10/14/22 三档实测 = 24px + delta（真机录屏）；`BASE-02` 单指点击行内任意 x 都能切换（真机手势）；`BASE-03` 读屏报「可折叠/已展开」（TalkBack）。 |

#### `base.primitives.disclosure.leading`（只有图标可点）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.disclosure.leading`。同一 `DisclosureRow` 的另一种形态：`expandable=true` 且 `expandOnRowClick=false`（含默认值，`P/DisclosureRow.tsx:50,64,99`）→ leading 渲染为 `<button>`，行本身无交互。每次出现 1 个。 |
| 2 | 网格与定位 | `.row` 的第一个子元素。盒 `width/height = calc(16px + delta)`、`margin-right:6px`、`padding:0`、`border:none`、背景透明、`color:inherit`（`P/DisclosureRow.module.css:50-63`）；盒内 svg 强制 `calc(14px+delta)`（`:69-72`），随字号长大以保持图标在行内的光学占比。 |
| 3 | 命中区 | **矩形 = 16px × 16px（+delta）**，无扩边（`padding:0`、`border:none`，css `:59-60`）。与标题间距 6px（`:58`）。面积约为整行模式的 1/8。 |
| 4 | 手势 | 单击切换；先 `event.stopPropagation()` 再 `onToggle()`（`P/DisclosureRow.tsx:65-68,105`）。键盘：原生 button 的 Enter/Space。长按/双击/滑动：未见实现。 |
| 5 | 行为 | 受控 `open` → 调 `onToggle()`；`stopPropagation` 避免外层行级处理器双重切换（`:66`）。幂等、可撤销由调用方保证。 |
| 6 | 视觉状态 | 默认图标继承 tertiary（css `:38`）。hover：`button.leading{cursor:pointer}`（css `:74-76`）+ 图标淡出/chevron 淡入（`:78-98`）。展开：变成 `chevron-up`（tsx `:82-84`）。pressed/disabled：未见实现。 |
| 7 | 布局占位与展开形态 | 自身 `flex:none`，尺寸随 delta 变；展开体仍由 `open` 决定是否挂载（形态 A，tsx `:121`）。本节点不改变兄弟顺序。 |
| 8 | 滚动语义 | 非滚动容器，展开内容随外层滚。 |
| 9 | 动效 | 仅 hover 的 opacity 100ms（css `:81,89`）；无展开动画。 |
| 10 | 边界态 | 大字号：命中盒随之变 `(16+delta)²`，是唯一会随字号放大的命中区。RTL：`margin-right` 同样错位（`:58`）。禁用态：不存在（expandable 即可点）。 |
| 11 | 无障碍 | `<button type="button" aria-label={title} aria-expanded={open}>`（`P/DisclosureRow.tsx:100-106`）→ 可访问名来自 `title`，与行内可见标题重复；无 `aria-controls`。 |
| 12 | 依据 | `E0` `P/DisclosureRow.tsx:50,64-68,74-84,99-108`；`E0` `P/DisclosureRow.module.css:50-76,78-98`。 |
| 13 | 反证 + 验收 | **反证**：若只有 16px 图标目标，移动端命中率极低（约 3mm，约为 44dp 目标的 1/7 面积），用户必须瞄准图标；若无条件放大到 44px 又会撑破 G-02 的 24px 定高，摘要流式变化时行高跳动。**验收**：`BASE-04` 图标按钮实测矩形 = 16×16+delta（量测截图）；`BASE-05` 带 accessory 时行宽收缩到内容宽、点 accessory 不触发披露（真机）。 |

#### `base.primitives.disclosure.fold-toggle`（头尾折叠按钮）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.disclosure.fold-toggle`。read/diff 结果「还有 N 行」的折叠开关。出现条件：内容行数 > 调用方 `maxLines`（`P/ReadBlock.tsx:138-146`、`P/DiffBlock.tsx:206-214`）。每个被截断的块 1 个。 |
| 2 | 网格与定位 | 位于被截断内容的尾部（头 `ceil(max/2)` 行 + 按钮 + 尾 `max-头` 行）。**组件自身不含任何尺寸/行高/间距声明**（`P/FoldToggle.tsx` 全文 33 行只有 props 与一个 button），尺寸完全由调用方 `className`（必传，`:2`）决定。 |
| 3 | 命中区 | 原生 button 的内容盒；**本组件不扩边、不设最小尺寸** → 实际矩形由调用方 CSS 决定（本节点无法给出统一像素值；调用点 `P/ReadBlock.module.css:36`、`P/DiffBlock.module.css:73` 仅声明 `cursor:pointer`）。 |
| 4 | 手势 | 单击切换（`P/FoldToggle.tsx:28`）；键盘 Enter/Space（原生 button）。无长按/双击/滑动。 |
| 5 | 行为 | 调 `onToggle()`；无内部状态（`:19-32`）；文案由 `labels.expand(hidden)` / `labels.collapse` 决定（`:27,30`）→ **隐藏行数是文案的一部分**。 |
| 6 | 视觉状态 | 全部来自调用方 className；组件不自绘 default/hover/pressed/focus。 |
| 7 | 布局占位与展开形态 | inline 按钮，处于内容流中；展开后尾部行重排（形态 B「capped-inline + 头尾」由外层块声明，见 `base.primitives.content-fold`）。 |
| 8 | 滚动语义 | 自身无滚动；外层块是否封顶内滚由外层决定（`P/TerminalBlock.module.css:47,157` 有 `overflow-y:auto`）。 |
| 9 | 动效 | 无动效声明。 |
| 10 | 边界态 | 多语言：文案含隐藏行数；组件不设 `width/max-width`（唯一影响尺寸的是调用方 className），长文案会把按钮撑宽。大字号：继承调用方字号，无自适应。空数据：不渲染（外层 `hidden>0` 才渲染）。 |
| 11 | 无障碍 | `aria-expanded={expanded}`（`:26`）+ `aria-label` 区分展开/收起（`:27`）。可访问名不含可见文案（aria-label 覆盖）。 |
| 12 | 依据 | `E0` `P/FoldToggle.tsx:1-33`；调用点 `E0` `P/ReadBlock.tsx:138-146`、`P/DiffBlock.tsx:206-214`。 |
| 13 | 反证 + 验收 | **反证**：若截断后不给按钮、只留「…」，用户不知道还有 N 行没显示（数据被静默丢弃）；若按钮只写「展开」而不带 aria-expanded，读屏复述时无法区分当前是展开还是收起。**验收**：`BASE-06` 点击后 aria-expanded 与文案同步翻转（本包已有单测断言，见 §3 测试条目；真机再验一次触控）。 |

### 2.2 `base.primitives.atoms` —— 按钮 / 动作 / 浮层

#### `base.primitives.button`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.button`。token 化按钮原子。出现：任何需要主/次/危险外框/工具栏按钮处（`P/Button.tsx:1-2,9`）。0..n 个。 |
| 2 | 网格与定位 | 原生 `<button>`；`display:inline-flex`、`gap:4px`、圆角 `--dsw-radius-md`；`md` 高 36px、水平 padding 14px；`sm` 高 28px、padding 10px、圆角 `--dsw-radius-sm`、字号 12/18（`P/Button.module.css:2-16,23-34`）；可选前导图标盒 16×16（`:71-77`）。 |
| 3 | 命中区 | 按钮自身盒：高 36（md）/28（sm），宽 = 内容 + padding；无外扩；最小边 28px。 |
| 4 | 手势 | 单击 = `rest.onClick`（透传，`P/Button.tsx:31`）；键盘原生；`disabled` 由原生拦截。无长按/双击。 |
| 5 | 行为 | 纯表现组件，无内部状态；`type="button"` 固定（不会误提交表单，`:31`）。 |
| 6 | 视觉状态 | 4 变体：`primary` / `ghost` / `outline` / `toolbar`（`:9`；css `:36-69`）。hover 各变体有自己的底色；**active 只在 ghost 声明**（`:49-51`）；disabled `opacity:.4`+`cursor:not-allowed`（`:18-21`）；focus 无自定义（走全局 focus.css）。 |
| 7 | 布局占位与展开形态 | inline-flex，参与布局流；不展开、不占位浮层。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无 transition 声明 → hover 立即变色。 |
| 10 | 边界态 | disabled：`.4` 透明 + not-allowed。**字号固定 14/12，不跟 `--dsh-content-font-size` 轴** → 用户把内容字号调到 22px 时按钮文字仍 14px（见 Q-BASE-05）。超长文本：无 nowrap/ellipsis，会被容器裁或撑宽。 |
| 11 | 无障碍 | 原生 button 语义；可访问名 = 子文本或调用方 `aria-label`；`type=button`。 |
| 12 | 依据 | `E0` `P/Button.tsx:27-36`；`E0` `P/Button.module.css:1-77`。 |
| 13 | 反证 + 验收 | **反证**：若没有 `sm` 变体，28px 工具栏行只能塞 36px 按钮，行高被撑高、工具条与文本行错位；若按钮字号跟随内容字号轴，用户调大字号会让同一行的按钮与文字互相挤爆。**验收**：`BASE-07` 四变体×两尺寸截图比对；`BASE-08` disabled 不触发 onClick（本包单测已有，真机复验）。 |

#### `base.primitives.pill`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.pill`。24px 行高的胶囊 chip；`onClick` 存在 → 渲染 button（可选中），否则渲染静态 span（`P/Pill.tsx:16-35`，`E1` 注释声称 `:1,11`）。出现：视图切换/筛选 chip、终端退出码只读徽标。 |
| 2 | 网格与定位 | `height:24px`、`padding:0 8px`、`gap:4px`、圆角 999px + `corner-shape:round`、字号 12/18（`P/Pill.module.css:1-14`）。 |
| 3 | 命中区 | 交互态 = 胶囊盒（24px 高，宽=内容+16px padding）；无外扩；最小边 **24px**。 |
| 4 | 手势 | 单击 = `onClick`；无长按/双击。只读态（span）不接收手势。 |
| 5 | 行为 | 受控：`active` 只影响样式，选中状态由调用方持有；单击后由调用方写状态（`P/Pill.tsx:16-35`）。 |
| 6 | 视觉状态 | default：`label-secondary` 文字 + `bg-layer-2` 底（css `:1-14`）；hover：`interactive-bg-hover`（`:20-22`）；active：主文字色 + `button-ghost-active-fill` + 内描边（`:24-28`）。disabled/focus：未见声明。 |
| 7 | 布局占位与展开形态 | inline-flex，不进浮层、不展开。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无过渡声明。 |
| 10 | 边界态 | 超长文本：无 ellipsis，会撑宽或裁切。字号固定 12。RTL：纯 `padding`/`gap` 逻辑无关，无问题。 |
| 11 | 无障碍 | 交互态是原生 `<button type="button">`（`:27-31`），语义由调用方 `rest` 补；**无 `aria-pressed`**（选中态只有样式，见 Q-BASE-06）。只读态是 `<span>`，无 role。 |
| 12 | 依据 | `E0` `P/Pill.tsx:16,23-35`；`E1` 注释 `P/Pill.tsx:1,11`；`E0` `P/Pill.module.css:1-28`。 |
| 13 | 反证 + 验收 | **反证**：若选中 chip 不暴露 `aria-pressed`，读屏用户无法知道当前筛选项；若把只读徽标也做成 button，会被 tab 到却点不动。**验收**：`BASE-09` 选中的 Pill 读屏报「已按下」（若采用 `aria-pressed` 方案）；`BASE-10` 只读 Pill 不在 tab 顺序中。 |

#### `base.primitives.icon-action`（图标动作条 / 24×24 与 28×28）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.icon-action`。图标动作按钮族：代码卡动作条（换行切换 + 复制，`P/CodeToolbar.tsx:47-59`）、内容块文字复制按钮（`P/SearchBlock.tsx:248`、`P/TerminalBlock.tsx:256`）、markdown 代码块复制（`P/markdown/CodeBlock.tsx:203`）。出现：可复制/可换行的代码或结果块头部。 |
| 2 | 网格与定位 | 代码卡动作条：24×24、圆角 `--dsw-radius-sm`、`gap:4px`，位于卡头右端（`P/CodeCard.module.css:43-48,56-68`）。dock 的同类图标按钮是 28×28 / 图标 15px（`D/dockkit.module.css:511-530`，由 `base.layout.dock-tab-strip-controls` 复用）。 |
| 3 | 命中区 | **24×24 px**（代码卡） / **28×28 px**（dock）；`padding:0`，无扩边。低于 44dp。 |
| 4 | 手势 | 单击 = 动作（复制 → 1s 后回态；换行 → 切换 `aria-pressed`）；无长按。复制结果不弹 toast，按钮图标换成对勾（`P/CodeToolbar.tsx:55-57`）。 |
| 5 | 行为 | 复制走 `writeClipboard` 并在 1000ms 后清 `copied`（`P/ReadBlock.tsx:95-102`）；重复点击在 copied 期间被忽略（幂等）。换行切换由卡片持有 `wrapped`（`P/ReadBlock.tsx:134`）。 |
| 6 | 视觉状态 | default：`label-secondary` 图标；hover：`interactive-bg-hover` 底（`P/CodeCard.module.css:70-72`）；focus-visible：1px 环 + offset 2（`:74-77`）；copied：图标换对勾（不是颜色变化）；disabled：无声明。 |
| 7 | 布局占位与展开形态 | 绝对不展开；`flex:none` 固定在卡头右端，不随内容宽度变化。 |
| 8 | 滚动语义 | 不滚；卡头不在滚动区内。 |
| 9 | 动效 | 无过渡（图标瞬时替换）。 |
| 10 | 边界态 | 复制失败：`writeClipboard` 返回 false 时不置 copied（`P/ReadBlock.tsx:97-98`）→ 按钮保持原样、无错误提示（静默失败，见 Q-BASE-07）。多语言：aria-label 随语言变（`labels.wrapLabel`/`clipboardLabel`）。 |
| 11 | 无障碍 | 原生 button + `aria-label`；换行按钮 `aria-pressed={wrapped}`（`P/CodeToolbar.tsx:50`）；复制按钮无 `aria-live` 播报（文案变化只体现在 aria-label 上）。 |
| 12 | 依据 | `E0` `P/CodeToolbar.tsx:38-61`；`E0` `P/CodeCard.module.css:43-77`；`E0` `P/SearchBlock.tsx:248`、`P/TerminalBlock.tsx:256`、`P/markdown/CodeBlock.tsx:203`；跨包同构 `D/dockkit.module.css:511-530`。 |
| 13 | 反证 + 验收 | **反证**：若图标按钮小于触控下限（24/28px），移动端连点会误触到相邻按钮（动作条 `gap` 仅 4px，两个 24px 目标中心距 28px，低于 44dp 的手指落点抖动）；若复制后只换图标不播报，读屏用户不知道复制成功。**验收**：`BASE-11` 动作条按钮实测 24×24（量测）；`BASE-12` 复制成功/失败两态读屏可辨（TalkBack + 真机）。 |

#### `base.primitives.tooltip`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.tooltip`。给任意锚点元素挂 hover/focus 提示的包装器（`P/Tooltip.tsx:35-62`）。出现：图标按钮、快捷键提示处。每个锚点 1 个。 |
| 2 | 网格与定位 | 提示气泡 `position:fixed`，按锚点矩形定位并做视口翻转；`side` 默认 `right`，`bottom/top` 时 `gap` 默认 8px；`maxWidth` 可限宽（`:38-53,62`）。气泡自身尺寸由内容决定（`P/Tooltip.module.css` 未在本节点展开）。 |
| 3 | 命中区 | **本组件不新增命中区**：它 `cloneElement` 锚点，把 `onMouseEnter/Leave/onClick/onFocus/onBlur` 链式注入锚点（`:227-251`），命中区仍是锚点自己的矩形。 |
| 4 | 手势 | hover 进入 → `delayMs` 后显示（默认 0）；focus → `focusDelayMs` 后显示，但**指针模态下 focus 不触发**（`pointerModality()` 拦截，`:244-248`）；`openOnClick` 时单击钉住，再点/Escape/Tab/外部 pointerdown 关闭（`:54-55,189-204`）；普通动作型提示在点击后立即收回（`:237-243`）。 |
| 5 | 行为 | 事件链接管顺序：**先跑锚点自己的 handler，再跑 tooltip 的**（`:232,237,244,250`）。行为不改变锚点动作（不 preventDefault 锚点 onClick）。 |
| 6 | 视觉状态 | visible / pinned（`data-pinned`）/ suppressed（嵌在另一提示内时让位，`TooltipSuppression`，`:20,206`）；隐藏不 unmount 锚点（`disabled` 也不重挂，注释 `:50-51`）。 |
| 7 | 布局占位与展开形态 | **浮层不占位**（G-01）：气泡 `position:fixed`，可选 `portal` 到 `document.body` 逃出裁剪与堆叠上下文（`:43-44,252`）。 |
| 8 | 滚动语义 | 气泡随锚点定位；页内滚动时未声明重定位逻辑（`useAnchoredPosition` 负责，本节点未展开）。 |
| 9 | 动效 | 未在本节点取证（`P/Tooltip.module.css` 未逐行读）；`visible` 由状态切换控制。**未见**高度/位移动画声明于 TSX。 |
| 10 | 边界态 | 空 label 且无快捷键：不显示内容（`:220-221` 两段都为空则不渲染字幕）；`disabled`：抑制气泡但锚点渲染不变（`:50-51`）；外部 iframe/窗口切换：`useDismissOnOutsidePointer` 处理（`:194`）。 |
| 11 | 无障碍 | 普通模式用 `role="tooltip"` + 不给 `aria-describedby`（`:217,229-231）；`openOnClick` 模式才把气泡 id 接进 `aria-describedby`（`:209,229-231`）；快捷键以 `aria-label` 拼接播报（`:218`）。 |
| 12 | 依据 | `E0` `P/Tooltip.tsx:20,35-62,180-204,206-223,225-254`。 |
| 13 | 反证 + 验收 | **反证**：若 tooltip 包装器吞掉或改写锚点的 onClick（顺序反了），所有「按钮 + 提示」组合都会变成点不动或双触发；若在触屏上把 hover 提示当成唯一说明手段，移动端用户永远看不到图标含义。**验收**：`BASE-13` 包裹后锚点 onClick 仍只触发一次（本包单测已有）；`BASE-14` 触屏上图标按钮有可替代的可访问名（aria-label 必填）。 |

#### `base.primitives.hover-card`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.hover-card`。悬停/聚焦弹出的卡片（预览、可复制文本）。出现：`inline` 变体包裹 markdown 图片链接（`P/markdown/render.tsx:603-607`）；`copyable` 时整卡可点复制。 |
| 2 | 网格与定位 | 卡片 `position:fixed`、宽 244px、padding 12/16、圆角 `--dsw-radius-lg`（`P/HoverCard.module.css:9-19`）；`preview` 变体 padding 0 且裁切（`:52-64`）；`media` 变体可滚（`:80-90`）。 |
| 3 | 命中区 | `copyable` 时**整张卡片**是可点矩形（`role=button` + `tabIndex=0`，`P/HoverCard.tsx:235-248`），尺寸 = 卡宽 244px × 内容高；非 copyable 时无命中区。 |
| 4 | 手势 | hover 停留 `openDelayMs` 后打开，移出有 grace 期（回到卡内取消关闭，`:271-279`）；`inline` 变体在 `:focus-visible` 时打开、失焦关闭（`:265-270`）；`pointerType === 'touch'` 时 inline 变体不因 hover 打开（`:272`）。单击（copyable）= 复制；键盘 Enter/Space = 复制（`:249-255`）。复制前检查是否有文本选区，有选区则不复制（`:240-247`）。 |
| 5 | 行为 | copy 走 `writeClipboard`，成功后整卡换成「已复制」文案 1000ms（`:204-216,257`）；copy 期间用 `copyingRef` 去重（幂等）。 |
| 6 | 视觉状态 | open / closing（`data-closing` → opacity 0 + pointer-events:none，css `:70-73`）/ copied（`:30-41`）/ suppressed（被内层 tooltip 让位）。 |
| 7 | 布局占位与展开形态 | **形态 C（sheet-overlay）**：`position:fixed`，完全不占位；`@starting-style` 淡入（css `:66-68`）。 |
| 8 | 滚动语义 | `media` 变体自身可滚（`overflow:auto`，`:80-90`）；`preview` 变体裁切不滚（`:52-58`）。 |
| 9 | 动效 | `preview` 的 opacity 过渡时长由 `--dsh-hover-preview-fade` 注入（`P/HoverCard.tsx:233`）；`prefers-reduced-motion` 关闭（css `:75-77`）。 |
| 10 | 边界态 | 视口底部溢出：定位时上翻到 `innerHeight - h - VIEWPORT_MARGIN`（`P/HoverCard.tsx:198-201`）。触屏：inline 变体不靠 hover 打开，只靠点击锚点（由锚点自身动作负责）。空内容：仍渲染空卡（无校验）。 |
| 11 | 无障碍 | copyable 卡 `role="button"` + `tabIndex=0` + `aria-label="{copyLabel}: {copyText}"`（`:235-237`）；复制结果另有 `role="status"` 隐藏播报（css `.status:43-50` + TSX 未见对应节点，属 R1b 复核范围）。非 copyable 卡无 role。 |
| 12 | 依据 | `E0` `P/HoverCard.tsx:198-201,204-216,218-259,261-279`；`E0` `P/HoverCard.module.css:8-90`。 |
| 13 | 反证 + 验收 | **反证**：若卡片盖住锚点却拦截点击（不该点时点了没反应）、或 hover 打开后一移动就消失（无 grace），用户无法把指针移进卡里读内容；若复制不检查文本选区，用户想选一段字却触发整卡复制。**验收**：`BASE-15` 指针从锚点移到卡内不关闭（真机）；`BASE-16` 有选区时点击不复制（真机 + 单测）。 |

#### `base.primitives.modal`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.modal`。居中模态框（`P/Modal.tsx:28-47`）。出现：确认、创建、风险确认（`P/RiskConfirmation.tsx:44-82` 是它的组合）。打开时 1 个，嵌套可多个（栈）。 |
| 2 | 网格与定位 | 全视口层 `position:fixed; inset:0; z-index:1000`，flex 居中，四周 `max(24px, --dsh-frame-overlay-top)` / 24px 内边距（`P/Modal.module.css:6-15`）；卡片宽 `min(380px,100%)`、圆角 `--dsw-radius-panel`、`gap:20px`、底部 padding 24（`:33-48`）；头部 `padding:22px 14px 12px 24px`（`:71-77`）。 |
| 3 | 命中区 | 三个：①**遮罩全屏**可点关闭（`P/Modal.tsx:59`）；②关闭按钮 28×28（`P/Modal.module.css:87-93`）；③`footer` 内调用方按钮（`P/RiskConfirmation.tsx:51-65`）。 |
| 4 | 手势 | 单击遮罩 = `onClose`；单击关闭按钮 = `onClose`；Escape = 关闭，但**仅当本层是栈顶**（`P/useModalLayer.ts:62-69`）；Tab 在对话框内循环（`:70-83`）；嵌套菜单打开时 Escape 先归菜单（`:72` 注释 + `Menu.tsx:340-348` 捕获期处理）。 |
| 5 | 行为 | 打开时记录 `document.activeElement`，把焦点移到 `[data-modal-autofocus]`（否则第一个可聚焦元素，`useModalLayer.ts:59-61`）；关闭时把焦点还回原处（`:91-94`）。回调由调用方持有（`P/Modal.tsx:53`）→ 是否幂等由调用方决定。 |
| 6 | 视觉状态 | 遮罩：`--dsw-alias-bg-mask-1` + `backdrop-filter: var(--dsw-mask-blur)`（`P/Modal.module.css:18-30`）；卡片：`bg-layer-2` + `elevation-prominent`（`:33-48`）；进入动画 `modalEnter` 200ms 淡入（`:29,47-53`）；`.dialog:focus{outline:none}`（`:59-61`）。 |
| 7 | 布局占位与展开形态 | **形态 C（sheet-overlay）**：`position:fixed` 全屏层，不参与布局流；页内所有滚动被遮罩挡住（G-01 同向）。 |
| 8 | 滚动语义 | 卡内内容区由调用方给 `contentClassName` 自行滚动（`P/Modal.tsx:38,73`）；本组件不设 `max-height`，只靠容器的 padding box 约束（注释 `:3-5`）。 |
| 9 | 动效 | 遮罩伪元素与卡片各 200ms `modalEnter`（opacity 0→1，`P/Modal.module.css:29,47-53`）；reduced-motion 关闭（`:55-57`）。 |
| 10 | 边界态 | 多模态嵌套：后开的层独占 Escape/Tab，前面层的快捷键被 `isBehindModal` 屏蔽（`useModalLayer.ts:28-32`）；Windows 标题栏：遮罩从 `--dsh-frame-chrome-top` 之下开始画，标题栏保持可拖（`P/Modal.module.css:18-22` 注释）；超长内容：卡片会被视口 padding 挤压，需调用方限制高度。 |
| 11 | 无障碍 | `role="dialog"` + `aria-modal="true"` + `aria-label={title}`（`P/Modal.tsx:60-68`）；遮罩 `aria-hidden="true"`（`:59`）；焦点陷阱与返回焦点（`useModalLayer.ts:46-96`）；`headless` 模式仍保留遮罩/卡片/Escape/aria-label（注释 `:43-44`）。 |
| 12 | 依据 | `E0` `P/Modal.tsx:28-91`；`E0` `P/Modal.module.css:1-129`；`E0` `P/useModalLayer.ts:7-97`；组合用法 `E0` `P/RiskConfirmation.tsx:43-82`。 |
| 13 | 反证 + 验收 | **反证**：若不做焦点陷阱，键盘用户可以 Tab 到遮罩背后正在滚动的会话里操作「看不见的东西」；若关闭后不还焦点，用户回到页面时焦点掉到 body，下一次键盘操作从页首开始。**验收**：`BASE-17` 打开→Tab 循环→Esc 关闭→焦点回到触发按钮（真机 + 单测）；`BASE-18` 嵌套两层时 Esc 只关最上层。 |

#### `base.primitives.toast`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.toast`。底部/顶部横幅通知，可带动作按钮（`P/Toast.tsx:52,89-96`）。出现：撤销、筛选等一次性提示。 |
| 2 | 网格与定位 | portal 到 `document.body`（`P/Toast.tsx:99`），水平定位由 `left` 注入（`:80`），持有 `--dsh-toast-hold` 毫秒变量控制停留（`:81`）。 |
| 3 | 命中区 | **仅动作按钮文字**：`.action{display:inline;padding:0;pointer-events:auto}`（`P/Toast.module.css:55-70`），横幅本身 `pointer-events:none`（`:67-69` 注释）→ 命中区 = 文字行高 × 文字宽，无扩边。 |
| 4 | 手势 | 单击动作文字 = 执行回调（`P/Toast.tsx:92`）；无长按/滑动关闭（未见）。 |
| 5 | 行为 | `action.onClick` 后是否关闭由调用方决定；`holdMs` 控制自动消失（`:81`）。 |
| 6 | 视觉状态 | 成功 tone 有绿色对勾（`:84-86`）；动作文字 `--dsw-static-deepseek-400`，hover `opacity:.8`（css `:55-74`）；进入动画 `dsh-toast-in`（css `:76-79`）。 |
| 7 | 布局占位与展开形态 | **浮层不占位**（G-01），`position:fixed` + portal。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 进入 出动画（css `:76-79` + `--dsh-toast-hold`）；本节点未逐行取证退出曲线。 |
| 10 | 边界态 | 多个动作：按数组顺序内联渲染（`P/Toast.tsx:89-96`）；超长文本：`.text{min-width:0}`（css `:51-53`）无断行策略声明。 |
| 11 | 无障碍 | 容器 `role="alert"`（`P/Toast.tsx:78`）→ 出现即播报；动作按钮是原生 button（可聚焦，但在 pointer-events:none 的横幅里需要自己恢复事件，css `:69` 已做）。 |
| 12 | 依据 | `E0` `P/Toast.tsx:52,78-101`；`E0` `P/Toast.module.css:40-79`。 |
| 13 | 反证 + 验收 | **反证**：若横幅整体可点（不设 pointer-events:none），它会挡住下方内容，且用户点「空白处」会误触发；若动作没有 `role=alert` 播报，TalkBack 用户不知道出现了一个可撤销操作。**验收**：`BASE-19` toast 出现不遮挡下方点击（真机）；`BASE-20` 动作按钮触控命中（移动端量测）。 |

#### `base.primitives.image-lightbox`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.image-lightbox`。图片全屏查看层（`P/ImageLightbox.tsx:26-67`）。出现：点开 markdown 图片（`P/markdown/render.tsx:658-660`）。同时 1 个。 |
| 2 | 网格与定位 | `position:fixed; inset:0; z-index:1000`（`P/ImageLightbox.module.css:2-6`）；舞台在 Windows 标题栏之下、四周 40px（`:9-16`）；图片 `max-width:min(100%,1600px)`、`max-height:100%`、圆角 `--dsw-radius-lg`（`:26-35`）；关闭按钮 36×36 圆形，距右上 20px（`:37-52`）。 |
| 3 | 命中区 | ①遮罩整层（`onMouseDown` 关闭，`P/ImageLightbox.tsx:58`）；②关闭按钮 **36×36**（css `:37-52`）。 |
| 4 | 手势 | 单击遮罩 = 关闭；单击关闭按钮 = 关闭；Escape = 关闭；Tab 被强制停在关闭按钮上（`P/ImageLightbox.tsx:39-42`）。无缩放/平移手势（**未见实现**）。 |
| 5 | 行为 | 打开时记录当前焦点并聚焦关闭按钮；关闭时恢复原焦点（`:37-38,46`）。 |
| 6 | 视觉状态 | 遮罩 `bg-mask-1` + `backdrop-filter`（css `:18-24`）；图片 `elevation-prominent`（`:34`）；关闭按钮 focus-visible 有 3px offset 环（`:54`）。 |
| 7 | 布局占位与展开形态 | **形态 C（sheet-overlay）**，不占位。 |
| 8 | 滚动语义 | 不滚；超视口图片按 `object-fit:contain` 缩放（css `:26-30`）。 |
| 9 | 动效 | 未见声明（无入场动画）。 |
| 10 | 边界态 | 图片加载失败：无错误占位（未见）；超长宽比：受 max-width/max-height 约束；键盘：Tab 被吞，只有 Esc/关闭按钮可用（`:41`）。 |
| 11 | 无障碍 | `role="dialog"` + `aria-modal="true"` + `aria-label`（`P/ImageLightbox.tsx:51-56`）；关闭按钮有 `aria-label`；遮罩 `aria-hidden`（`:58`）。 |
| 12 | 依据 | `E0` `P/ImageLightbox.tsx:26-67`；`E0` `P/ImageLightbox.module.css:1-54`。 |
| 13 | 反证 + 验收 | **反证**：若不把 Tab 锁在唯一按钮上，键盘用户会 Tab 到遮罩背后的页面（看不见焦点）；若关闭后不还焦点，用户回到正文时丢失阅读位置。**验收**：`BASE-21` Esc 关闭后焦点回图片按钮（真机）；`BASE-22` Tab 不逃出灯箱。 |

### 2.3 `base.primitives.controls` —— 输入与选择

#### `base.primitives.switch`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.switch`。两态开关，`label` 必填（无默认）→ 不可能渲染出无名开关（`P/Switch.tsx:1-2,18-19`）。出现：设置项布尔值。 |
| 2 | 网格与定位 | `width:36px; height:20px; padding:2px`，轨道圆角 999px + `corner-shape:round`；滑块 16×16，`aria-checked=true` 时 `translateX(16px)`（`P/Switch.module.css:5-49`）。 |
| 3 | 命中区 | **36×20 px** 整轨道（等于控件盒，无扩边）。 |
| 4 | 手势 | 单击 = `onChange(!checked)`（`P/Switch.tsx:37`）；键盘：原生 button 的 Space/Enter；**无滑动切换**（未见）。 |
| 5 | 行为 | 受控组件，不持有状态；`onChange` 收到的是「请求的下一态」而非已生效态（`:8-10,37`）→ 写失败时调用方保持原值即可回弹。 |
| 6 | 视觉状态 | 关：`--dsw-alias-border-l3` 轨道（css `:15`）；开：`--dsw-alias-brand-primary`（`:19-21`）+ 滑块右移 120ms（`:40,47-49`）；disabled：`opacity:.5`+`cursor:default`（`:23-26`）；focus-visible：2px 环 offset 2（`:28-31`）。样式键在 `aria-checked` 上而不是并行 class（注释 `:1-3`）→ 视觉与读屏态不可能不一致。 |
| 7 | 布局占位与展开形态 | inline 控件，不展开。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 滑块 `transform 120ms ease`（css `:40`）；无 reduced-motion 分支（未见）。 |
| 10 | 边界态 | 写进行中：调用方应同时 `disabled`（注释 `:13-14`）；超长 label：label 在 aria 里，不影响视觉宽度（控件固定 36px）。大字号：控件不随字号轴缩放。 |
| 11 | 无障碍 | `<button role="switch" aria-checked={checked} aria-label={label}>`（`P/Switch.tsx:29-37`）；`title` 可选说明锁定原因。 |
| 12 | 依据 | `E0` `P/Switch.tsx:1-42`；`E0` `P/Switch.module.css:1-49`。 |
| 13 | 反证 + 验收 | **反证**：若样式用并行 class 而不是 `aria-checked`，某些时刻会出现「看起来开了、读屏说关了」；若做成原生 checkbox 而不带 role=switch，读屏会报「复选框」而非「开关」。**验收**：`BASE-23` 开关视觉与 `aria-checked` 始终一致（截图+读屏）；`BASE-24` 触控命中（36×20 偏小，移动端是否扩边由 Q-BASE-04 决定）。 |

#### `base.primitives.checkbox`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.checkbox`。带可见文字标签的原生复选框（`P/Checkbox.tsx:1,16-31`）。出现：风险确认的「我已了解」勾选（`P/RiskConfirmation.tsx:71-80`）。 |
| 2 | 网格与定位 | `label` 横向 flex + `gap:6px`、字号 14/20（`P/Checkbox.module.css:1-9`）；input 16×16（`:11-18`）。 |
| 3 | 命中区 | **整个 label**（16px 方框 + 6px + 文字宽）→ 命中区包含文字，实际远大于 16px。 |
| 4 | 手势 | 原生 `change`；点文字或方框都切换。无长按。 |
| 5 | 行为 | 受控：`onChange(event.target.checked)`（`P/Checkbox.tsx:26-27`）。 |
| 6 | 视觉状态 | 用原生 `accent-color: --dsw-alias-brand-primary`（css `:16`）；focus-visible 2px 环 offset 2（`:20-23`）；disabled：整 label `opacity:.5` + cursor 默认（`:25-28`）。 |
| 7 | 布局占位与展开形态 | inline-flex，不展开。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无（原生绘制）。 |
| 10 | 边界态 | 超长 label：可换行（无 nowrap），label 换行会把方框顶到第一行中线（align-items:center）。大字号固定 14。 |
| 11 | 无障碍 | 原生 `input[type=checkbox]` + 外层 `<label>` 包裹 → 可访问名 = 可见文字（`P/Checkbox.tsx:25-29`）；键盘 Space 原生。 |
| 12 | 依据 | `E0` `P/Checkbox.tsx:16-31`；`E0` `P/Checkbox.module.css:1-28`。 |
| 13 | 反证 + 验收 | **反证**：若只有 16px 方框可点、文字不可点，移动端勾选要精确命中 16px；若用自绘 div 代替原生 input，键盘 Space 与读屏「已勾选」态都要自己重造。**验收**：`BASE-25` 点文字也能切换（真机）；`BASE-26` Space 可勾选（键盘）。 |

#### `base.primitives.input`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.input`。受控文本输入（`P/Input.tsx:21`）及其行内编辑变体（`P/InlineEditor.tsx:45-69`，用于重命名类就地编辑）。出现：字体列表输入（`T/client/FontFamilyRow.tsx:47-62`）、设置字段、重命名。 |
| 2 | 网格与定位 | `Input`：外层 `.wrap` 高 32px、padding 0 8px、gap 6、圆角 `--dsw-radius-md`、0.5px 边框（`P/Input.module.css:1-10`）；内层 input flex:1、字号 14/22（`:25-34`）。`InlineEditor`：单行 textarea 自动长高（`P/InlineEditor.tsx:33-43`）。 |
| 3 | 命中区 | 整个输入容器（≥32px 高 × 可用宽）；**输入区本身是全宽矩形**，符合触控但高度 32px 仍低于 44。 |
| 4 | 手势 | 单击聚焦输入；`Input` 无自定义键处理（透传 `rest`）；`InlineEditor`：Enter 提交、Shift+Enter 换行、Esc 取消，IME 组合期（`isComposing` 或 keyCode 229）不提交（`P/InlineEditor.tsx:45-55`）。 |
| 5 | 行为 | 完全受控（`value`+`onChange`）；`InlineEditor` 另有 `onSave`/`onCancel` 两个显式意图（`:45-55`），取消即回滚。 |
| 6 | 视觉状态 | `Input`：`focus-within` 时边框转 `--dsw-alias-state-business-primary`（css `:12-14`）；placeholder `label-dimmed`（`:36-38`）。`InlineEditor`：`.editor:focus` 有样式（`P/InlineEditor.module.css:21`，本节点未逐行取证）。 |
| 7 | 布局占位与展开形态 | 参与布局流；`InlineEditor` 高度随内容增长（ResizeObserver 重算，`P/InlineEditor.tsx:39-42`）→ **会推挤下方兄弟**。 |
| 8 | 滚动语义 | `InlineEditor` 的 `overflow-y:auto`（`P/InlineEditor.module.css:17`）→ 编辑区可有内滚（但高度按内容自适应，正常情况下不滚）。 |
| 9 | 动效 | 无过渡声明。 |
| 10 | 边界态 | 超长输入：input 横向滚动（原生）；`InlineEditor` 长高到上限？未见上限（无 max-height 于本节点取证范围）。大字号：输入字号固定 14，不跟内容字号轴。RTL：纯 padding/gap，无物理方向依赖。 |
| 11 | 无障碍 | `Input` 透传 `rest`（含 `aria-label`，见 `T/client/FontFamilyRow.tsx:52`）；`InlineEditor` 有必填 `aria-label`（`P/InlineEditor.tsx:63`）；无 `aria-invalid`（由 `settings-field` 节点负责）。 |
| 12 | 依据 | `E0` `P/Input.tsx:21`；`E0` `P/Input.module.css:1-38`；`E0` `P/InlineEditor.tsx:33-69`；用法 `E0` `T/client/FontFamilyRow.tsx:47-62`。 |
| 13 | 反证 + 验收 | **反证**：若行内编辑不在 IME 组合期跳过提交，中文用户打拼音按 Enter 选词会直接提交半截内容；若取消不还原，用户按 Esc 后看到草稿留在原地会以为已保存。**验收**：`BASE-27` 中文 IME 组合期 Enter 不提交（真机）；`BASE-28` Esc 取消后值回到原值（真机）。 |

#### `base.primitives.segmented-control`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.segmented-control`。等宽分段切换（tablist + 滑动指示器，`P/SegmentedControl.tsx:1-7,108-138`）；同族实现二 `SegmentedTabs`（`P/SegmentedTabs.tsx:33-74`）。出现：卡片/面板的少量模式切换（如 JSON/文本视图）。 |
| 2 | 网格与定位 | `role="tablist"` 容器 + 一个 `aria-hidden` 指示器 + N 个等宽 `role=tab` 按钮（`P/SegmentedControl.tsx:108-137`）；宽度由 CSS `--dsh-segment-count/--dsh-segment-index` 驱动（`:103-106`）。 |
| 3 | 命中区 | 每个 segment = 自己的按钮盒（等高、等宽，宽度 = 容器/N）；点击跨度较大，符合触控；具体高度在 `P/SegmentedControl.module.css`（本节点未逐行取证高度值）。 |
| 4 | 手势 | 单击非选中项 = `onChange(value)`（选中项点击不触发，`:131`）；键盘方向键走位、Home/End 跳首尾，**走位即选中**（`:96-101`）→ 与 WAI-ARIA tabs 的自动激活一致；键盘走位后焦点跟到新的选中项（`:88-94`）。 |
| 5 | 行为 | 受控（`value` + `onChange`）；不会以当前值调 `onChange`（注释 `:62-63`）。 |
| 6 | 视觉状态 | `aria-selected`（`:125`）+ 滑动指示器按 index 移动；disabled：整体或单项禁用（`:128`）。 |
| 7 | 布局占位与展开形态 | 水平 flex，不展开；切换只换面板内容（面板由调用方渲染，`aria-controls`/`aria-labelledby` 双向绑定，`:5-7,126`）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 指示器移动的曲线在本节点未取证（CSS 未逐行读）。 |
| 10 | 边界态 | 只有 1 个 segment：仍渲染 tablist（无最少数量校验，注释说 ≥2 是调用方责任 `:61`）；全部 disabled：走位跳过禁用项（`:44-53`）。大字号：未跟字号轴。 |
| 11 | 无障碍 | `role=tablist` + `aria-label`（`:111-112`）；每项 `role=tab` + `aria-selected` + 漫游 `tabIndex`（`:124-127`）+ `aria-controls`（`:126`）。 |
| 12 | 依据 | `E0` `P/SegmentedControl.tsx:26-54,56-140`；`E0` `P/SegmentedTabs.tsx:33-74`。 |
| 13 | 反证 + 验收 | **反证**：若方向键只移焦点不切换（手动激活），用户要再按一次 Enter，与「分段控件」的即时切换直觉不符；若点击已选中项也调 `onChange`，会触发无意义重渲染/重取数。**验收**：`BASE-29` 方向键切换后面板立即变（真机）；`BASE-30` 点已选中项不触发 onChange（单测）。 |

### 2.4 `base.primitives.menu` —— 菜单

#### `base.primitives.menu`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.menu`。锚定下拉菜单（可 portal、可带子菜单、可多选/填充选中）。出现：语言选择（`C/LanguageRow.tsx:41-64`）、标签页右键菜单（`D/TabMenu.tsx`，dockkit 自实现同语义菜单）、JSON 复制方式菜单（`P/JsonTree.tsx:129-161`）。打开时 1 个。 |
| 2 | 网格与定位 | 列表 `position:absolute/fixed`、`min-width:144px`、padding 4、圆角由 `--dsw-radius-*`；每行 `padding:6px 8px`、字号 13/20、圆角 12px；紧凑变体 `min-width:156px`、行 `padding:2px 6px`、字号 11（`P/Menu.module.css:7-14,28-45,95-107,133-145`）；`z-index`：内联 100 / portal 1100（`:32,45`）；可滚时 `max-height: calc(100vh − 12px − max(12px, --dsh-frame-overlay-top))`（`:65-67`）。 |
| 3 | 命中区 | 每一行 = 整行按钮（宽 100%、行盒 = padding 6/8 + 20px 行高 ≈ 32px）；在 28px 密集模式下 ≈ 21px；**行高随模式下降**，移动端需注意（→ Q-BASE-04）。 |
| 4 | 手势 | 单击行 = 选中；单击列表外 = 关闭（`P/Menu.tsx:326-332`）；Esc/Shift+Tab = 关闭并还焦点到锚点；Tab = 结算当前聚焦行（`:340-381`）；方向键/Home/End = 走位（`:390-404`）；子菜单父行 hover 或 focus 打开（`:453,464`），到达非父行关闭（`:506-511`）；指针离开整个区域（wrapper 判定，含 4px 缝）后才 grace 关闭（`:549-558`）。 |
| 5 | 行为 | 选中调 `onSelect(id)`，**是否关闭由调用方决定**（注释 `P/Menu.tsx:73-74`）；选中后把焦点还给锚点（`:529-533`）。一个手势 = 一个意图：`onClick` 在列表层 stopPropagation，避免回冒到锚点行（`:524-533`）。 |
| 6 | 视觉状态 | 行 hover / focus-visible / disabled / danger / selected（`fill` 或 `check` 两种选中标记，`:456-478`）；被遮挡的 portal 菜单用 `isBehindModal` 让位（`:334`）。 |
| 7 | 布局占位与展开形态 | **形态 C（sheet-overlay）**：`position:absolute` 或 portal 到 body（`:43,517-547`）；不占位、不推挤。 |
| 8 | 滚动语义 | 无子菜单时才启用高度上限 + 内滚（`.scrollable`，`:437,517-520`）→ **有子菜单的菜单不做内滚**（子菜单卡会被裁，注释 `:435-436`）。 |
| 9 | 动效 | 未在本节点取证（`P/Menu.module.css` 未逐行读过渡）。 |
| 10 | 边界态 | 空菜单：本组件无「空则隐藏」逻辑（`P/Menu.module.css` 全文 285 行无 `:has()` 规则；有该规则的是 dockkit 的独立菜单 `D/dockkit.module.css:557-559`）；焦点在 iframe（沙箱预览）导致 window blur：关闭（`P/Menu.tsx:405-411`）；输入法组合期：`observeComposition` 拦截（`:333-334`）。 |
| 11 | 无障碍 | 列表 `role="menu"`（`:522`），行 `role="menuitem"` + `role="separator"` + 标签 `role="presentation"`（`:441-458`）；子菜单父行 `aria-haspopup="menu"` + `aria-expanded`（`:462-463`）；快捷键 `aria-keyshortcuts`（`:461`）。 |
| 12 | 依据 | `E0` `P/Menu.tsx:69-104,319-425,437-547,549-559`；`E0` `P/Menu.module.css:7-14,28-45,65-67,95-107,133-145`；同语义独立实现 `E0` `D/TabMenu.tsx:60-127`。 |
| 13 | 反证 + 验收 | **反证**：若菜单不 portal 而留在被裁剪的卡片里，下拉会被卡片 `overflow:hidden` 切掉一半；若行不 stopPropagation，点菜单项会同时触发锚点的开合（点了就关、关了又开）；若不做「有子菜单时不滚」，子菜单卡会被列表裁剪。**验收**：`BASE-31` 菜单项点击只触发一次选中（单测 + 真机）；`BASE-32` 窄卡片内菜单完整可见（截图）。 |

### 2.5 `base.primitives.content` —— 内容块与设置字段

#### `base.primitives.content-fold`（头尾截断 + 展开）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.content-fold`。长内容块的「头 N₁ 行 + 展开按钮 + 尾 N₂ 行」折叠模式，覆盖 read / diff / terminal / search / json 块（`P/ReadBlock.tsx:104-148`、`P/DiffBlock.tsx:185-218`、`P/TerminalBlock.tsx:202-283`、`P/SearchBlock.tsx:189-279`、`P/markdown/JsonBlock.tsx:30`）。出现条件：行数 > `maxLines`。每块 1 个开关（search 还有每文件头折叠）。 |
| 2 | 网格与定位 | 头 = `ceil(maxLines/2)` 行、尾 = `maxLines − 头`（`P/ReadBlock.tsx:106-109`、`P/DiffBlock.tsx:189-195`、`P/TerminalBlock.tsx:219`）；按钮位于头尾之间（`P/ReadBlock.tsx:138-147`）。文件头折叠按钮占整行（`P/SearchBlock.tsx:231-240`）。 |
| 3 | 命中区 | 展开按钮 = 原生 button，尺寸由 `.expand` 类决定（`P/ReadBlock.module.css:36`、`P/DiffBlock.module.css:73` 仅 cursor）；search 文件头 = 整行宽按钮（`P/SearchBlock.tsx:231-240`）。 |
| 4 | 手势 | 单击展开/收起；search 文件头单击折叠该文件的匹配行（`P/SearchBlock.tsx:235`）；无长按/双击。 |
| 5 | 行为 | 展开态是块本地状态（`useState`，`P/ReadBlock.tsx:104`、`P/TerminalBlock.tsx:202` 等）；不持久化、不跨块共享；search 的折叠是 `Set<number>`（`:180-198`）。切换幂等。 |
| 6 | 视觉状态 | 按钮文案随 hidden 数变（「展开 N 行」/「收起」）；search 文件头 `aria-expanded`（`:234`）；无自定义 hover（由 CSS 类决定）。 |
| 7 | 布局占位与展开形态 | **形态 B（capped-inline）**：折叠时块高被 maxLines 截断，展开后长高并推挤下方内容；块体是否内滚由各自 CSS 决定（`P/TerminalBlock.module.css:47,157`、`P/ReadBlock.module.css` 有 `overflow`）。**不是**形态 A（无 sticky 头）。 |
| 8 | 滚动语义 | 块体自身可滚（`overflow-y:auto`，`P/TerminalBlock.module.css:47,157`、`P/SearchBlock.module.css:25,47`）；与本节点折叠叠加时会出现「内滚 + 折叠」两层，需明确谁优先（→ Q-BASE-08）。 |
| 9 | 动效 | 无高度动画（切换即重排）。 |
| 10 | 边界态 | 空内容：不渲染块（`P/TerminalBlock.tsx:261-263` 有空态文案）；行数少于 maxLines：不显示折叠按钮（`hidden>0` 判断）；超长单行：横向滚动（code 块）。大字号：行数换算按行数不按像素 → 大字号下实际可视行变少（块更高）。 |
| 11 | 无障碍 | 折叠按钮 `aria-expanded` + `aria-label`（展开含隐藏行数，`P/TerminalBlock.tsx:269-277`）；search 文件头 `aria-expanded` 但**无 aria-label**（可访问名 = 文件路径文本，`P/SearchBlock.tsx:231-239`）。 |
| 12 | 依据 | `E0` `P/ReadBlock.tsx:104-150`；`P/DiffBlock.tsx:185-220`；`P/TerminalBlock.tsx:197-286`；`P/SearchBlock.tsx:180-281`；`P/markdown/JsonBlock.tsx:30`；`P/FoldToggle.tsx:19-33`。 |
| 13 | 反证 + 验收 | **反证**：若不做头尾截断而整块全展开，一条 2000 行终端输出会把会话拉成几十屏（G-03 的「B 做成 A」失效）；若折叠只是 CSS 隐藏而不改按钮 aria-expanded，读屏永远报「已折叠」。**验收**：`BASE-33` 展开/收起后 aria-expanded 正确（单测已有）；`BASE-34` 展开后不出现内外滚动打架（真机，对应 G-04）。 |

#### `base.primitives.json-tree`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.json-tree`。只读 JSON 树（`P/JsonTree.tsx`）。出现：工具结果里的 JSON 结构。每个 JSON 值 1 棵。 |
| 2 | 网格与定位 | 缩进树；每节点 = expander 图标（12–14px）+ 字段名 + 预览值 + 可选 copy 按钮（`P/JsonTree.tsx:589-631`、`P/JsonTree.module.css` 提供 `.expander/.actionButton` 尺寸，本节点取 12px 图标 `:151-152`）。 |
| 3 | 命中区 | ①展开三角：`role=button` 的 span，尺寸由 CSS `.expander`（`P/JsonTree.module.css:310,333,340` 附近，含 `cursor:pointer`）；②copy 按钮 `.actionButton`（`P/JsonTree.module.css:277,288`）；③整块 `NodeField` 可点（`P/JsonTree.tsx:312`）；④长字符串的 stringToggle。**均为小图标尺寸**，未见 44dp 扩边。 |
| 4 | 手势 | 单击三角/展开区 = 展开或收起；单击 copy = 复制（默认复制值/JSON，object 复制 pretty JSON，`:143`）；**右键 copy = 打开复制方式菜单**（路径/JSON/值，`:144-148`）；键盘：Enter/Space 展开、Tab 在树内漫游（`:595-602` + `tabStopId` 漫游 tabIndex `:599`）。 |
| 5 | 行为 | 展开态是每节点本地 `useState`（`initialExpanded` 默认 false，`:621`）；copy 成功换对勾 + 1s 后回态（`:121-124`）。 |
| 6 | 视觉状态 | copy 按钮 `data-state`（copied/failed，`:140`）；展开/收起 icon class 切换（`:593`）；focus 通过 `:has(> .expander:focus-visible)` 给整行焦点环（`P/JsonTree.module.css:80`）。 |
| 7 | 布局占位与展开形态 | **形态 A**：展开后子树 inline 挂载（`{expanded && <ul>}`，`:609-628`）→ 推挤下方内容，无高度上限、无内滚。 |
| 8 | 滚动语义 | 树本身可滚（`P/JsonTree.module.css:11,128`）；树内展开不出内滚。 |
| 9 | 动效 | 无（未见）。 |
| 10 | 边界态 | 超长字符串：默认折叠 3 行（`collapsedStringLines` 默认 3，`:663-664`）+ stringToggle 展开；循环引用/大数：无特殊处理（只读渲染）；滚动裁剪探测会向上找 `overflowY` 祖先（`:380`）以决定浮层定位。 |
| 11 | 无障碍 | 展开器 `role="button"` + `aria-label`（展开/收起节点）+ `aria-expanded` + `aria-controls`（`:595-598`）；展开的子树 `role="group"`（`:610`）；树容器有 `label`（`:659`）；copy 按钮 `aria-label`（`:141`）。 |
| 12 | 依据 | `E0` `P/JsonTree.tsx:117-165,302-312,380,415-494,542-632,656-669`；`E0` `P/JsonTree.module.css:11,80,104,121,128,181,189,200,259,277,288,310,333,340`（CSS 命中行，结构属性）。 |
| 13 | 反证 + 验收 | **反证**：若展开器不是独立可聚焦按钮（只整行可点），键盘用户无法逐层展开；若右键菜单没接上，power user 想复制 JSON path 只能手动拼。**验收**：`BASE-35` 键盘可逐层展开（真机 + 读屏）；`BASE-36` 右键出现复制方式菜单（真机）。 |

#### `base.primitives.markdown-link`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 关键身份与出现条件 | `base.primitives.markdown-link`。markdown 内所有「可点文本」：外链锚点、本地文件链接、文件提及 chip、图片按钮、task 复选框、以及 `user-text` 的引用 chip（`P/markdown/render.tsx:318-330,349-357,446,556-608,658-660`；`P/user-text.tsx:127-150`）。 |
| 2 | 网格与定位 | 内联元素，继承正文排版；文件 chip 复用 `fileMention` 类（`P/user-text.tsx:141`），链接图标按路径类型选（`classifyLinkPath`，`P/markdown/render.tsx:325,598`）。 |
| 3 | 命中区 | **文字本身**（inline），无扩边；chip 的可点区域 = 文本宽 × 行高。低于 44dp（一行文字高约 24px）。 |
| 4 | 手势 | 单击打开（外链交给宿主 `openExternalLink`；本地文件 `openFile`；图片打开灯箱）；外链**只有无修饰键时**才 preventDefault 转给宿主，Cmd/Ctrl/Shift/Alt 点击保留浏览器行为（`P/markdown/render.tsx:560-564`）；`user-text` chip：**双击（detail>1）或有文本选区时不跳转**（`P/user-text.tsx:144-147`）→ 与「选中文字」手势仲裁。 |
| 5 | 行为 | 只读渲染；目的地由宿主 delegate 决定（`useMarkdownDelegate`，`:553-555,586-587`）；task 复选框 `disabled`（`:446`）→ 不可点击，只表达状态。 |
| 6 | 视觉状态 | 链接/chip 有 hover/focus 样式在 `P/markdown/MarkdownText.module.css:78-85,316-325,378-380,457`（css 命中，结构属性）；图片按钮 focus-visible 有环（`:457`）。 |
| 7 | 布局占位与展开形态 | 全 inline，不展开；图片链接可再包 HoverCard 预览（形态 C，`P/markdown/render.tsx:603-607`）。 |
| 8 | 滚动语义 | 跟随正文滚动；宽表格另有 `.tableScroll` 可横向滚（`P/markdown/MarkdownText.module.css:214-221`）。 |
| 9 | 动效 | 未见。 |
| 10 | 边界态 | 非法/未知 scheme：`inlineCodeHttpUrl` 只认 http(s)，其余保持惰性文本（`P/markdown/render.tsx:614-619`）；流式渲染中的本地链接**不解析**（`streaming` 时 `file=undefined`，`:573-574`）→ 流式期间链接不可点；锚点内再出现提及 chip：保持惰性（button 不能嵌套，注释 `:313`）。 |
| 11 | 无障碍 | 外链 `<a href>` + `target=_blank` + `rel=noopener noreferrer`（`:556-559`）；文件链接是 `<button type=button title=...>`（`:591-597`）→ 语义从「链接」变成「按钮」，读屏不会报「链接」；提及 button 有 `aria-label`（`:322`）；checkbox 原生。 |
| 12 | 依据 | `E0` `P/markdown/render.tsx:301-331,349-357,446,550-608,610-619`；`E0` `P/user-text.tsx:127-150`；`E0` `P/WebBlock.tsx:114`。 |
| 13 | 反证 + 验收 | **反证**：若本地文件链接也渲染成 `<a href>`，浏览器会尝试导航到不存在的 URL 并丢掉 SPA 状态；若流式期间就解析半截路径，用户会在参数中间点到错文件；若 chip 不判断双击/选区，用户想选中复制一段文字时会被跳转打断。**验收**：`BASE-37` 流式期间文件 chip 不可点（真机）；`BASE-38` 双击引用 chip 不跳转（真机）。 |

#### `base.primitives.settings-field`（设置字段与保存）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.settings-field`。设置表单的字段与保存控件，两个来源：`ConfigField`（兼容「choices / secret / 文本」三型 + 重置，`P/ConfigField.tsx:33-53`）与 `settings-form/fields.tsx`（带帮助展开、重置、密码字段，`P/settings-form/fields.tsx:64-115,125-151`）+ 表单底部的保存按钮（`P/settings-form/SettingsForm.tsx:63-68`）。出现：设置页每个可编辑项 1 个；保存按钮每组 1 个。 |
| 2 | 网格与定位 | 字段：label 行（label + 可选族徽/重置）在上，控件在下（`P/settings-form/fields.tsx:65-106`）；控件是原生 `<input class=css.input>`（尺寸由 `P/settings-form/fields.module.css` 决定，本节点未逐行取证宽高）。`ConfigField` 的 head 行：label 左、重置按钮右（`P/ConfigField.tsx:35-39`）。 |
| 3 | 命中区 | ①输入框整盒（宽度撑满字段列）；②帮助按钮（12px 图标，`P/settings-form/fields.tsx:71-76`）——**图标按钮的命中盒在 `fields.module.css` 定义，本节点未取证到具体像素**，只知其视觉图标 12px；③重置按钮（文字按钮，`:83-92`、`P/ConfigField.tsx:38`）；④保存按钮（原生 button，`P/settings-form/SettingsForm.tsx:63-68`）。 |
| 4 | 手势 | 单击帮助按钮 = 展开/收起说明区（`aria-expanded` 切换，`P/settings-form/fields.tsx:73`）；单击重置 = `onReset`（清用户覆盖，`:87`、`P/ConfigField.tsx:38`）；单击保存 = `props.onSave`（`P/settings-form/SettingsForm.tsx:67`）；输入走原生 `onChange`。`ConfigField` 的 `select` 在选空值时会调 `onReset` 而不是 `onChange`（`P/ConfigField.tsx:42`）→ **「继承默认」是下拉里的一个选项**。 |
| 5 | 行为 | 全部受控（值/草稿由表单持有）；`overridden` 为真才显示重置入口（`P/settings-form/fields.tsx:79-93`、`P/ConfigField.tsx:38`）；`disabled` 时所有控件与重置同时禁用；校验态由 `invalid` 驱动（`aria-invalid` + 消息文本，`:100-109`、`P/ConfigField.tsx:41,47,49`）。 |
| 6 | 视觉状态 | 输入 focus-visible 有环（`P/settings-form/fields.module.css:119`）、帮助/重置按钮有 focus-visible 样式（`:62`）；invalid 文案与 hint 文案两种样式（`P/settings-form/fields.tsx:107-109`）；保存按钮有 focus-visible 与 hover（`P/settings-form/SettingsForm.module.css:40,50`）。 |
| 7 | 布局占位与展开形态 | 字段纵向排列；帮助说明区 `{helpOpen && <div role=region>}` **inline 挂载**（`P/settings-form/fields.tsx:110-112`）→ 展开时下方字段整体下移（形态 A 变体、无高度动画）。 |
| 8 | 滚动语义 | 跟随设置页滚动；`textarea`（`P/ConfigField.tsx:49-50`）自带内滚，行数按内容 `1..8` 收敛。 |
| 9 | 动效 | 无声明（帮助区直接显示）。 |
| 10 | 边界态 | 无效值：`aria-invalid` + `role="status"` 的错误行（`P/ConfigField.tsx:51`、`P/settings-form/fields.tsx:107-109`）；密码字段永不回显值（`type=password` + `autoComplete=new-password`，`P/ConfigField.tsx:47`、`P/settings-form/fields.tsx:139-147`）；多语言：`labels.reset/inherited/invalid` 全部外部注入。 |
| 11 | 无障碍 | 原生 label `htmlFor` 关联（`P/ConfigField.tsx:37`、`P/settings-form/fields.tsx:68`）；帮助按钮 `aria-expanded` + `aria-controls`（`:71-73`）；帮助区 `role="region"` + `aria-label`（`:111`）；错误用 `role="status"`（`P/ConfigField.tsx:51`）+ `aria-describedby` 串联（`P/settings-form/fields.tsx:101`）。 |
| 12 | 依据 | `E0` `P/ConfigField.tsx:33-53`；`E0` `P/settings-form/fields.tsx:64-115,125-151`；`E0` `P/settings-form/SettingsForm.tsx:63-68`；CSS 命中行 `E0` `P/ConfigField.module.css:5`、`P/settings-form/fields.module.css:53,62,96,119`、`P/settings-form/SettingsForm.module.css:40,50`。 |
| 13 | 反证 + 验收 | **反证**：若密码字段回显已存值，凭据会出现在屏幕上/可被截图；若重置按钮在未覆盖时也显示，用户会以为「恢复默认」可用却点了没变化；若「继承默认」不做成显式选项而只靠清空文本框，用户分不清「空字符串」与「未设置」。**验收**：`BASE-69` 密码字段初始为空、不回显（真机 + 截图）；`BASE-70` 只有 overridden 时才有重置入口（单测）；`BASE-71` 无效值触发 aria-invalid 与 status 播报（真机读屏）。 |

### 2.6 `base.layout` —— 框架 / 列宽 / dock / 浮层

#### `base.layout.frame`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.frame`。整个应用的三列壳，注册进内建 `root` 槽位（`L/AppFrame.tsx:1-16,120-128`）。出现：始终 1 个（应用唯一）。 |
| 2 | 网格与定位 | `display:grid`；列模板 `sidebarPx minmax(centerMinOrZero, 1fr) minmax(0, rightbarMax)`，其中 center 的 min 在右栏无轨道时为 0、有轨道时 `CENTER_MIN=400`（`L/AppFrame.tsx:265-266`、`L/columns.ts:11,54-58`）；行 `minmax(0,1fr) auto`（css `:1-8`）；常量：sidebar 264–420（默认 280、折叠 56）、右栏最小 300、最大 70% 视口、首开 45%（`L/columns.ts:11-29`）。 |
| 3 | 命中区 | 框架本身不接收手势（除拖拽手柄，见 `base.layout.column-resize`）；`shell.overlay` 层整体 `pointer-events:none`、其子元素 `auto`（`L/AppFrame.module.css:281-290`）→ 浮层不挡点击（G-01 的实现方式）。 |
| 4 | 手势 | 本节点无直接手势；子区（列宽手柄）独立。**无返回手势、无边缘滑动**（未见实现）。 |
| 5 | 行为 | 三个动作面：`selectPanel(panelId 或 null)`（换主面板，覆盖式）、`toggleSidebar()`、`openRightbar(track, fullscreen)/closeRightbar()`（`L/service.ts:36-53`）；窄屏（<1024）打开右栏会自动折叠左栏（`L/stores.ts:127-136`）；`activePanelId` 不在注册表里时被重置为 `null`→回到会话（`L/stores.ts:97-101`）。 |
| 6 | 视觉状态 | `data-sidebar-collapsed` / `data-rightbar-collapsed` / `data-rightbar-fullscreen` / `data-dragging` / `data-animating`（`L/AppFrame.tsx:268-273`）驱动 CSS；macOS 透明 + 通透底、Windows 标题栏内缩（css `:25-50,77-190`）。 |
| 7 | 布局占位与展开形态 | 列宽是**轨道**不是盒子：右栏 occupant 自己把面板锚在框架右缘，轨道只决定中间让不让位（注释 `L/AppFrame.tsx:10-15`、css `:260-271`）；全屏右栏时轨道仍保留（`rightbarTrack`）但手柄隐藏（`:302`）。 |
| 8 | 滚动语义 | 三列各自管滚动（`.sidebarCol/.centerCol` `overflow:hidden`，`L/AppFrame.module.css:58-70`）；`shell.bottom` 行 `overflow:auto`（`:273-279`）。 |
| 9 | 动效 | 只有「离散开合」才缓动 `grid-template-columns` 300ms（`--ds-transition-duration-slow` + `--ds-ease-in-out`，css `:15-17`）；拖拽中 `transition:none`（`:19-23`）；视口连续变化不动画（注释 `:10-14`）；手柄的 `left` 跟同一曲线（`:238-244`）；reduced-motion 关闭（`:52-56`）。 |
| 10 | 边界态 | 极窄视口：center 可降到 0（`L/columns.ts:58`）；右栏放不下（可用宽 < 300）时轨道直接消失（`:55-57`）；macOS 折叠侧栏时靠 `shell.leading` 座位放窗口控件并发布 `--dsh-frame-leading-clearance`（`L/AppFrame.tsx:250-256`、css `:178-193`）。**竖屏/横屏切换**：只有宽度断点 1024，无高度断点（未见）。 |
| 11 | 无障碍 | 本节点无 role（纯容器）；子槽位的语义由 occupant 决定；`shell.overlay` 不拦截指针（css `:281-290`）。 |
| 12 | 依据 | `E0` `L/AppFrame.tsx:1-16,58-118,120-307`；`E0` `L/AppFrame.module.css:1-290`；`E0` `L/columns.ts:1-59`；`E0` `L/stores.ts:1-146`；`E0` `L/service.ts:1-106`；`E0` `L/index.ts:53-121,158-214`。**盘点说明**：本节点自身没有独立的扫描命中——`L/AppFrame.tsx` 的 12 条命中全部是列宽手柄的手势实现，已归 `base.layout.column-resize`；框架层只通过槽位渲染子区，不直接绑定事件。 |
| 13 | 反证 + 验收 | **反证**：若列宽在拖拽时也走缓动，列边缘会跟不上指针（css 注释 `:19-23` 明说）；若 `shell.overlay` 层不穿透，所有浮层下面都点不动；若把主面板做成栈（可返回），DSH 的「全局面板不切会话」语义（`L/stores.ts:18-20`）会与返回栈互相打架。**验收**：`BASE-39` 拖拽列宽时边缘跟手（真机）；`BASE-40` overlay 层上的空处点击能穿透（真机）。 |

#### `base.layout.column-resize`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.column-resize`。列宽拖拽手柄（`L/AppFrame.tsx:54-118`）。出现：侧栏展开时 1 个；右栏 `rightbarShown && !fullscreen && 正常宽>0` 时 1 个（`:301-304`）。 |
| 2 | 网格与定位 | 绝对定位、位于列边界：`width:8px`、`margin-left:-4px`（跨在边界 ±4px）、`top:0;bottom:0`、`cursor:col-resize`（`L/AppFrame.module.css:217-233`）；`left` 由 JS 注入 = 解析后的列宽（`L/AppFrame.tsx:108,301`）。 |
| 3 | 命中区 | **8px 宽 × 全列高**的竖条；无可见把手（注释 `:217-222`：`No handle draws a visible pill`）→ 命中区 8px 远低于 44dp，但有全高补偿。 |
| 4 | 手势 | `pointerdown`（仅主键 `button===0`）→ `setPointerCapture` + `preventDefault`；`pointermove` 用 rAF 节流只报 dx；`pointerup` 报最终 dx；`pointercancel/lostpointercapture` 结束（`L/AppFrame.tsx:78-117`）。**无双击复位、无键盘调整**（未见）。 |
| 5 | 行为 | 拖动写 `setSidebar(base+dx)` / `setRightbar(base−dx)`，base 是**手势开始时的渲染宽度**（防跳变，`:180-186,225-232`）；写入时 clamp 到范围内（`L/stores.ts:102-105,123-126`）。手势期间 track 过渡暂停（`:187` + css `:242-244`）。 |
| 6 | 视觉状态 | `data-dragging`（`:110`）；无 hover 视觉（透明条，靠 `cursor:col-resize` 表达）。 |
| 7 | 布局占位与展开形态 | `position:absolute`，**不占布局**；折叠时侧栏无手柄（`:300-301`）。 |
| 8 | 滚动语义 | 自身不滚；覆盖在列内容上但只有 8px 宽。 |
| 9 | 动效 | 手柄 `left` 在 `data-animating` 时跟 300ms 曲线（css `:238-240`），拖拽中 none（`:242-244`）。 |
| 10 | 边界态 | 拖动超界：clamp 到 sidebar 264–420 / rightbar ≥300 且 ≤视口 70%（`L/columns.ts:38-40`、`L/stores.ts:104,125`）；触屏：`touch-action:none`（css `:232`）避免被滚动手势抢走，但 8px 目标在触屏上几乎无法命中（→ Q-BASE-04）。 |
| 11 | 无障碍 | 手柄是**无 role 的 div**（`L/AppFrame.tsx:106-116`）→ 键盘用户无法调整列宽、读屏不报（见 Q-BASE-09）。 |
| 12 | 依据 | `E0` `L/AppFrame.tsx:54-118,180-186,224-232,300-304`；`E0` `L/AppFrame.module.css:217-258`。 |
| 13 | 反证 + 验收 | **反证**：若无 pointer capture，指针一离开 8px 条手势就断、列宽卡在半路；若用「偏好值」而不是手势起点做 base，从被 clamp 的宽度起拖会瞬间跳回存储值。**验收**：`BASE-41` 拖出 8px 条外列宽仍跟手（真机）；`BASE-42` 快速拖到极限后松手不跳变（真机录屏）。 |

#### `base.layout.dock-pane`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.dock-pane`。dockkit 的停靠面板容器（`D/TabPanel.tsx:425-445`、`D/TabLayout.tsx:84-110`）。出现：每个停靠 pane 1 个，上限 4 个（`E/constraints.ts:10`）。 |
| 2 | 网格与定位 | pane = 纵向 flex：38px 标签条（内容带 28px，`D/dockkit.module.css:223-248`）+ body；`overflow:hidden`（`:209-216`）。 |
| 3 | 命中区 | **整个 pane 盒**可点用于聚焦（`onClick`，`D/TabPanel.tsx:433`）；与 pane 内其他命中区的关系：内层按钮各自 stopPropagation 或由 React 冒泡顺序决定（tab chip 的 onClick 也 stopPropagation，`:290`）。 |
| 4 | 手势 | 单击 pane 内空白 = 聚焦该 pane（`activePaneId` 改变）；无长按。 |
| 5 | 行为 | `callbacks.onFocusPane(pane.id)`；已是活动 pane 时不触发（`D/TabPanel.tsx:433-435`）。浮动形态下同一语义由 `useFloatGestures` 的 `raise()` 承担（`D/FloatLayer.tsx:79-82`）。 |
| 6 | 视觉状态 | `data-dockkit-pane-active`（`D/TabPanel.tsx:432`）；`:focus{outline:none}`（css `:218-221`）。 |
| 7 | 布局占位与展开形态 | 参与 flex 分栏布局；不展开（分栏由 split 树决定）。 |
| 8 | 滚动语义 | 面板整体不滚（`overflow:hidden`），body 内滚（`.paneBody overflow:auto`，css `:579-589`）。 |
| 9 | 动效 | 无（分栏尺寸变化无过渡声明）。 |
| 10 | 边界态 | 空 pane：显示 `labels.emptyPane`（`D/TabPanel.tsx:439-441`）；分栏到最小份额 12%（`E/constraints.ts:13`）；窗口标题栏/全屏下的可用高度调整见 float 节点。 |
| 11 | 无障碍 | `<section>` + `tabIndex={-1}`（可编程聚焦但不入 tab 序，`D/TabPanel.tsx:428-432`）；无 role/label（依赖内部 tablist）。 |
| 12 | 依据 | `E0` `D/TabPanel.tsx:425-445`；`E0` `D/TabLayout.tsx:84-110`；`E0` `D/dockkit.module.css:209-216,223-248,579-589`。 |
| 13 | 反证 + 验收 | **反证**：若点击 pane 不聚焦，用户拖完分栏还是在旧 pane 里打字（键盘事件去错面板）；若聚焦只靠 tab chip，点击 body 里的空白不改变活动面板，与「最后一个碰过的面板是活动的」直觉不符。**验收**：`BASE-43` 点 pane 空白改变活动 pane（真机）；`BASE-44` 点 pane 内按钮不改变活动 pane（除按钮自身语义）。 |

#### `base.layout.dock-tab`（标签芯片：切换 / 拖拽 / 右键）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.dock-tab`。dockkit 标签条里的芯片（`D/TabPanel.tsx:240-348`）。出现：每个 tab 1 个；pane 内 tab 数 ≥1。 |
| 2 | 网格与定位 | chip：高 **28px**、`min-width:80px`、`max-width:170px`、`padding:0 10px`、圆角 `--dsw-radius-sm`；与相邻 chip 之间夹 10px 的 slot（发丝线/插入光标）；关闭按钮 20×20 绝对定位在 chip 右端 4px 处（`D/dockkit.module.css:237-248,257-268,324-341,361-378,417-437`）。 |
| 3 | 命中区 | chip 盒 = **28px 高 × 80–170px**（远大于 44dp 的宽度、远小于 44dp 的高度）；关闭按钮 20×20；未 hover/未激活时关闭按钮 `pointer-events:none` → **那块区域属于 chip 的命中区**（注释 css `:434-436` 明示触屏误关问题）。 |
| 4 | 手势 | 单击 = 激活（`onClick` + stopPropagation，`D/TabPanel.tsx:289-292`）；**pointerdown 起拖**（阈值 4px，`E/geometry.ts:141-153`）；未过阈值在释放点算 click（`D/DockSurface.tsx:214-247`）；右键 = 打开标签菜单（`onContextMenu`，`D/TabPanel.tsx:308-312`）；键盘：方向键/Home/End 走位、Enter/Space 选中（`:293-307` + `chipToFocus` `:109-124`）。 |
| 5 | 行为 | 拖动释放：落在另一条 strip → 重排/跨 pane 移动（`placeTab`）；落在 pane 边带 → 分屏（`dropTab`）；落在界面外 → 变浮动面板（`floatTab`，`D/DockSurface.tsx:235-245`）。激活是 `onFocusTab`；已激活时不重复报（`D/TabPanel.tsx:228-231`）。拖拽期间 chip `opacity:.5`（css `:505-507`）。 |
| 6 | 视觉状态 | 默认：secondary 文字、无底（css `:361-378`）；hover：`interactive-bg-hover` 底（`:475-477`）；active：`markdown-tag` 底 + 主文字色（`:480-483`）；quiet（唯一且不可关的 chip）：主字色、无 hover 底、`cursor:default`（`:488-503`）；dragging：半透明；标题超宽：右侧 16px 渐隐遮罩（`:399-401`），显示关闭时改为在关闭圆下方渐隐（`:409-413`）。 |
| 7 | 布局占位与展开形态 | 水平 flex，`flex:0 1 auto`；窄 pane 时 chip 盒是**唯一收缩部**，收缩到不能缩后 chip 盒内横向滚动并出 24px 渐隐边（`:250-284`）；控件（add/split/chrome）永不移动（`:286-303`）。 |
| 8 | 滚动语义 | 芯片盒横向滚动（`overflow-x:auto`，隐藏滚动条，`:257-272`）；活动 chip 进入视野时会平滑滚动并让出 24px 渐隐带（`D/TabPanel.tsx:182-201`）；`touch-action:none` 使触屏按住移动变拖拽而非滚条（css `:252-253`）。 |
| 9 | 动效 | 芯片盒 `scroll-behavior:smooth`（css `:264-265`）；拖拽预览无过渡（跟随指针）。 |
| 10 | 边界态 | 触屏：`touch-action:none` 保证 press-move 是拖拽不是平移（注释 `:251-256`）；多指：只跟随第一个 pointerId（`D/pointer.ts:47-58`）；窗口失焦/指针取消：手势取消并复位预览（`pointer.ts:88-109`）。 |
| 11 | 无障碍 | chip `role="tab"` + `aria-selected` + 漫游 `tabIndex`（`D/TabPanel.tsx:263-266`）；容器 `role="tablist"`（`:245`）；关闭按钮 `aria-label` + `aria-keyshortcuts`（`:318-323`）；WAI-ARIA tabs **手动激活**（注释 `:103-107`）。 |
| 12 | 依据 | `E0` `D/TabPanel.tsx:103-124,182-201,227-348`；`E0` `D/DockSurface.tsx:214-247`；`E0` `D/dockkit.module.css:237-284,324-341,361-378,399-413,417-446,475-507`；`E0` `E/geometry.ts:140-153`；手势 `E0` `D/pointer.ts:28-110`。 |
| 13 | 反证 + 验收 | **反证**：若 pointerdown 就直接进入拖拽（无 4px 阈值），单击会被吃掉半数（第一次移动就取消 click）；若关闭按钮在不可见时仍接收指针，触屏点在芯片尾部会误关标签（css 注释明确为此设 `pointer-events:none`）；若芯片盒不收缩而是控件先动，窄 pane 下 split/add 按钮会飘出视野。**验收**：`BASE-45` 4px 内的按下-释放是 click、超出是拖拽（真机 + 单测）；`BASE-46` 未 hover 时点芯片尾部不会关闭（真机触屏）。 |

#### `base.layout.dock-tab-close`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.dock-tab-close`。标签芯片内的关闭按钮（`D/TabPanel.tsx:315-334`）。出现：`canCloseTab(tabId)` 为真时 1 个/芯片。 |
| 2 | 网格与定位 | 20×20、绝对定位 `top:4px; right:4px`（覆盖在标题尾部，不占流），图标 14px（`D/dockkit.module.css:417-437`、`D/TabPanel.tsx:331`）。 |
| 3 | 命中区 | **20×20 px**；不可见时 `pointer-events:none`（css `:433-436`）→ 不可见时命中区为 0，那 20px 归芯片。 |
| 4 | 手势 | 单击 = 关闭（先 stopPropagation，`D/TabPanel.tsx:326-329`）；`pointerdown` 也 stopPropagation，**防止按下被芯片当成拖拽起点**（`:325` 注释 `:322-324`）。 |
| 5 | 行为 | `callbacks.onCloseTab(tabId)`；组件本身不关闭任何状态——是否真的关闭由调用方实现（本文件无强制关闭逻辑）。 |
| 6 | 视觉状态 | 默认 `opacity:0`；芯片 hover / `:focus-within` / 激活时 `opacity:1` + `pointer-events:auto`（css `:439-446`）；hover 时主字色 + hover 底（`:470-473`）。**恒可见于激活芯片**（「下一个要关的就是当前在看的」）。 |
| 7 | 布局占位与展开形态 | 绝对定位不占位 → 有无关闭按钮芯片同宽（注释 `:357-360`）。 |
| 8 | 滚动语义 | 不滚；随芯片在横向滚动的芯片盒内移动。 |
| 9 | 动效 | opacity 继承芯片 hover（无独立 transition 声明）。 |
| 10 | 边界态 | 触屏：未激活的芯片上关闭不可见且不可点（`pointer-events:none`）→ 触屏用户需先点激活再关（两步，见 Q-BASE-10）；禁用关闭：`canCloseTab` 为假时整颗不渲染，只由右键菜单补位（`:339`）。 |
| 11 | 无障碍 | 原生 button + `aria-label={labels.closeTab}` + `aria-keyshortcuts`（`:318-323`）；不可见时仍在 tab 序里（opacity 而非 visibility，注释 css `:434-436` 只讲 pointer-events）→ 键盘可 Tab 到并显示。 |
| 12 | 依据 | `E0` `D/TabPanel.tsx:315-334`；`E0` `D/dockkit.module.css:357-360,415-446,470-473`。 |
| 13 | 反证 + 验收 | **反证**：若关闭按钮常驻可见，芯片标题可用宽度被吃掉且窄 pane 更容易误触；若不可见时仍可点，触屏点芯片尾部会误关（关闭是不可撤销的破坏性动作）。**验收**：`BASE-47` 未激活芯片触屏点击尾部 = 激活而非关闭（真机）；`BASE-48` 激活芯片关闭按钮可键盘 Tab 到（键盘 + 截图）。 |

#### `base.layout.dock-tab-strip-controls`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.dock-tab-strip-controls`。标签条末端控件：新增标签 28×28、分栏 28×28、宿主 chrome 槽（`D/TabPanel.tsx:350-403`）。出现：`canAddTab` 为真 → add；`hideSplitWhenBlocked` 未隐藏且允许 → split；chrome 仅右上 pane 渲染（`:393`）。 |
| 2 | 网格与定位 | 全部 28×28、圆角 `--dsw-radius-sm`、图标 15px（`.iconButton`，`D/dockkit.module.css:511-530`）；与芯片之间由 4px gap 分隔；`stripFill` 吃掉剩余空间保证控件不被挤走（`:286-291`）。 |
| 3 | 命中区 | 每个按钮 **28×28 px**，无扩边（`padding:6px` 内含 15px 图标）。低于 44dp。 |
| 4 | 手势 | 单击 = `onAddTab` / `onSplitPane`；均 stopPropagation（`D/TabPanel.tsx:357-360,381-384`）；被禁用的 split 按钮 `disabled` + 外层 span `tabIndex=0` + `aria-label` 说明原因（`:368-379`）。 |
| 5 | 行为 | add = 请求新标签；split = 请求分栏（是否成功由 `canSplit`/宽度规则决定，`D/DockSurface.tsx:203-207`）；两者最终都走 embedder 的 intents（操作序列是唯一事实源）。 |
| 6 | 视觉状态 | default secondary；hover `interactive-bg-hover`；`disabled`: `opacity:.4` + `cursor:default`（css `:532-539`）；分栏被挡时按钮 disabled（`:373-379`）。 |
| 7 | 布局占位与展开形态 | `flex:none`，永不移动；芯片盒独吞收缩（css `:286-291`）。 |
| 8 | 滚动语义 | 不滚（不在芯片盒内）。 |
| 9 | 动效 | 无过渡声明。 |
| 10 | 边界态 | 分栏被挡的两种原因分别是预算（`canSplit=false`）和宽度不足（`hasRoom=false`），**禁用标题文案不同**（`D/TabPanel.tsx:203-209`）；`hideSplitWhenBlocked` 时直接不渲染（`:367`）。 |
| 11 | 无障碍 | 原生 button + `aria-label`；split 有 `aria-keyshortcuts`；被禁用时把原因挂在外层 span 的 `aria-label` 上（`:370-371`）→ 键盘仍能聚焦读到原因。 |
| 12 | 依据 | `E0` `D/TabPanel.tsx:203-209,350-403`；`E0` `D/DockSurface.tsx:203-207`；`E0` `D/dockkit.module.css:286-303,511-539`；`E0` `E/constraints.ts:9-40`。 |
| 13 | 反证 + 验收 | **反证**：若禁用按钮只 `disabled` 不给原因，用户不知道为什么不能分栏（预算用尽 vs 宽度不够是两种解法）；若控件先于芯片收缩，窄 pane 下就再也点不到分栏。**验收**：`BASE-49` 两种禁用原因文案正确（截图 + 读屏）；`BASE-50` 窄 pane 下 add/split 始终可见（真机缩窄）。 |

#### `base.layout.dock-divider`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.dock-divider`。分栏分隔条（`D/PaneTree.tsx:38-50`、`D/TabLayout.tsx:140-157`）。出现：每个 split 的每个边界 1 个。 |
| 2 | 网格与定位 | `position:relative`、`width/height:0`（**不占布局**）、`z-index:1`；可见发丝线画在 `::before`（0.5px 居中）；命中区由 `::after` 向两侧各伸出 4px → **8px 命中带宽**（`D/dockkit.module.css:39-99`）。 |
| 3 | 命中区 | 8px 宽 × split 全长（`::after` 扩边，`:75-80,94-99`）。低于 44dp，但有长度补偿。 |
| 4 | 手势 | `pointerdown` 进入 resize 手势；`touch-action:none`（css `:50`）；`cursor:col-resize/row-resize`（`:63-66,82-85`）。 |
| 5 | 行为 | 拖动算 fractions（`dividerSizes`），clamp 到最小份额 12%（`E/constraints.ts:13,84-104`）；**松手时若 fractions 没变则不报 resize**（`D/DockSurface.tsx:262-275`）→ 点击分隔条不产生操作。 |
| 6 | 视觉状态 | 默认只画 0.5px 发丝（`::before`）；hover 时 `::after` 淡入 120ms 显示 1px 渐隐「抓手」（`:101-120`）。 |
| 7 | 布局占位与展开形态 | 0 宽，**不占布局**；两侧 pane 直接相邻使 pane 自己画的 hairline 能连续通过接缝（注释 `:39-45`）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | hover 抓手 opacity 120ms ease-out（`:105-108`）。 |
| 10 | 边界态 | `z-index:1` 保证右/下侧兄弟 pane 不会抢走 4px 的覆盖命中（注释 `:44-45`）；最小份额处 clamp 后继续拖不改变（幂等）；触屏 8px 目标命中困难（→ Q-BASE-04）。 |
| 11 | 无障碍 | 无 role / 无键盘调整（`D/PaneTree.tsx:38-50` 是普通 div）→ 键盘用户不能调分栏（同 Q-BASE-09 一类问题）。 |
| 12 | 依据 | `E0` `D/PaneTree.tsx:38-50`；`E0` `D/TabLayout.tsx:140-157`；`E0` `D/DockSurface.tsx:249-276`；`E0` `D/dockkit.module.css:39-120`；`E0` `E/constraints.ts:12-13,84-104`；`E0` `E/geometry.ts:155-174`。 |
| 13 | 反证 + 验收 | **反证**：若可见线本身当命中区（0.5px），用户永远抓不到；若点击分隔条也报 resize，会往撤销栈里塞空操作。**验收**：`BASE-51` 距边界 ±4px 内可起拖（真机）；`BASE-52` 点击分隔条不产生 resize 操作（单测）。 |

#### `base.layout.dock-menu`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.dock-menu`。标签页右键菜单（`D/TabMenu.tsx:60-127`），dockkit **独立实现**的一套菜单（不复用 `P/Menu`）。出现：右键标签芯片时 1 个。 |
| 2 | 网格与定位 | `position:fixed`、**`z-index:70`**（高于浮动层 60，低于模态）、`min-width:96px`、padding 4、0.5px 边框（`D/dockkit.module.css:541-555`）；行 `padding:4px 7px`、字号 12（`:561-570`）。 |
| 3 | 命中区 | 行 = 整行按钮（高约 25px）；低于 44dp。 |
| 4 | 手势 | 右键打开（`D/TabPanel.tsx:308-312`）；**捕获期**监听 `keydown`（Escape/Tab 关闭）与 `pointerdown`（外部关闭）（`D/TabMenu.tsx:71-95`）；菜单面自身 stopPropagation（`:113-114`）。 |
| 5 | 行为 | 只提供「关闭」（`onClose`，可由 embedder 用 `renderTabMenuItems` 追加项，`D/TabPanel.tsx:341`）；选中后 dismiss。 |
| 6 | 视觉状态 | `.menu:not(:has([role=menuitem...])){display:none}` → **空菜单不显示**（`D/dockkit.module.css:557-559`）；行 hover `interactive-bg-hover`（`:572-574`）。 |
| 7 | 布局占位与展开形态 | 形态 C（浮层不占位）；portal 到 body。 |
| 8 | 滚动语义 | 不滚（`D/TabMenu.tsx` 未见 max-height）。 |
| 9 | 动效 | 无声明。 |
| 10 | 边界态 | 同一芯片重复右键：再次右键切换关闭（`D/TabPanel.tsx:311` 的 `current?.tabId === tabId ? undefined : ...`）；不可关闭的标签：不渲染关闭项，只剩 embedder 追加项（`:339`）。 |
| 11 | 无障碍 | `role="menuitem"` + 原生 button（`D/TabMenu.tsx:117`）；菜单容器由 `MenuSurface` 提供 `role`（调用处传）。**无方向键走位/aria-haspopup 于本实现**（未见）。 |
| 12 | 依据 | `E0` `D/TabMenu.tsx:60-127`；`E0` `D/TabPanel.tsx:308-312,335-343`；`E0` `D/dockkit.module.css:541-574,557-559`。 |
| 13 | 反证 + 验收 | **反证**：若不复用统一菜单语义，同一个应用里有两种键盘行为（`P/Menu` 有方向键走位，dockkit 菜单没有），无障碍体验不一致；若不 portal，模块级 `z-index:70` 会被祖先堆叠上下文困住。**验收**：`BASE-53` 右键菜单 Escape/Tab 关闭（真机）；`BASE-54` 菜单不被浮动面板遮挡（截图）。 |

#### `base.layout.float-panel`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.float-panel`。浮层面板（把标签拖出停靠区得到，`D/FloatLayer.tsx:1-15`）。出现：每个浮动 pane 1 个；`floats` 数组无上限（停靠上限才是 4）。 |
| 2 | 网格与定位 | `position:fixed`、`border-radius: var(--dsw-radius-panel)`（28px）、`box-shadow: --dsw-elevation-prominent`；`left/top/width/height` 全部按状态写内联样式；`top` 取 `max(--dsh-dockkit-float-top, y)`（Windows 标题栏下让位）（`D/FloatLayer.tsx:166-180`、`D/dockkit.module.css:750-765,742-748`）；默认尺寸 380×300，最小 220×140（`E/constraints.ts:16-19`）。 |
| 3 | 命中区 | ①头部整行 = 移动抓手（`D/FloatLayer.tsx:126-127`，`pointerdown` 起 move 手势）；②两个控件 28×28（回停靠 / 关闭，`D/dockkit.module.css:511-530`）；③右下角 **20×20** 缩放角（`D/FloatLayer.tsx:183-187`、css `:805-813`）；④面板任意处 `pointerdown` = 抬升（`D/FloatLayer.tsx:179`）。 |
| 4 | 手势 | pointerdown 头部 → 移动；pointerdown 缩放角 → 缩放（最小尺寸 clamp，`E/geometry.ts:193-207`）；释放时**矩形没变则视为点击 → 只抬升**（`D/FloatLayer.tsx:99-105`）→ 一个手势一个意图；拖出停靠区创建（`floatRectAt` 让头部落在指针下，`E/geometry.ts:209-218`）。 |
| 5 | 行为 | 抬升 = `focusPane`（改 `activePaneId` 并把它移到 `floats` 末尾）→ **z 序由模型数组决定，不直接写 style**（`D/FloatLayer.tsx:63-66,79-82,177`）；回停靠 = `unfloatPane`；关闭 = `closeTab`。 |
| 6 | 视觉状态 | 激活/非激活**用同一个框**（同一阴影，注释 css `:767-769`）→ 抬升不靠视觉强调只靠 z 序与遮挡；`data-dockkit-float-active` 保留给测试（`:171`）。头部 `cursor:move` + `user-select:none`（`:773-776`）；缩放角 hover/拖拽时显示 1.5px 折角（`:815-838`）。 |
| 7 | 布局占位与展开形态 | **形态 C（sheet-overlay / 浮层不占位，G-01）**：`position:fixed`，不参与任何布局流、不推挤停靠区。`z` 层由 `.floatingCell{z-index:60}` 提供堆叠上下文（`D/dockkit.module.css:154-160`）。 |
| 8 | 滚动语义 | 面板 body 内滚（`.floatBody{overflow:auto}`，css `:794-803`）；头部固定不滚。 |
| 9 | 动效 | 无移动/尺寸动画：拖动期间内联宽高直接跟随指针（无 transition）；移动端跟手（E0）。 |
| 10 | 边界态 | Windows 标题栏：`top` 有下限、拖动时 y 也 `max(saved.y, caption+20)`（`D/FloatLayer.tsx:89-96`）；全屏切换：`--dsh-dockkit-float-top` 变 20px（css `:746-748`）；多浮动面板重叠：后按的在上（`:177`）。 |
| 11 | 无障碍 | 面板 `tabIndex={-1}`（可编程聚焦）；头部/缩放角/控件都是无 role 的 div 或原生 button 混用 → **移动/缩放手势对键盘不可用**（未见键盘替代，Q-BASE-09 同类）。 |
| 12 | 依据 | `E0` `D/FloatLayer.tsx:29-39,63-109,111-150,152-193`；`E0` `D/dockkit.module.css:154-160,541-555,742-838`；`E0` `E/constraints.ts:15-19`；`E0` `E/geometry.ts:140-153,189-221`。 |
| 13 | 反证 + 验收 | **反证**：若在 pointerdown 就改 z 序（而不是靠模型数组），拖动时 DOM 顺序变化会重建子树、取消指针（`D/TabPanel.tsx:279-283` 记录了同类事故）；若「按下不动」不抬升，用户点一下浮层想把它提到前面就做不到；若 z 直接用 style 写而不由模型数组决定，抬升后一次重渲染就会把顺序打回旧值。**验收**：`BASE-55` 点浮动面板空白处即抬到最上（真机）；`BASE-56` 拖动中面板不重建、不丢指针（真机录屏）；`BASE-57` 浮层不占位（G-01 截图：停靠区不因浮层出现而变窄）。 |

### 2.7 `base.theme` —— 设置行（含 locale）

> 本节末补录一个归 `base.primitives` 的原子（连接状态按钮），为免打断设置行的表格序列放在最后。

#### `base.theme.appearance-row`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.theme.appearance-row`。外观三选一（浅色/深色/跟随系统），注册进设置 General 区的 item 槽（`T/client/AppearanceRow.tsx:1-7,42-62`）。出现：设置页 1 个。 |
| 2 | 网格与定位 | 行容器：纵向 gap 8、`padding:16px 0`、底部 0.5px 分隔线（`T/client/AppearanceRow.module.css:4-10`）；cube 行横向 flex gap 8、可换行（`:19-24`）；每个 cube `flex:1 1 180px`、`padding:20px 32px`、圆角 `--dsw-radius-xl`（20px）（`:29-46`）。 |
| 3 | 命中区 | **整个 cube**（约 180px 宽 × 82px 高量级）→ 远超 44dp，是本次审计里唯一达标的触控目标。 |
| 4 | 手势 | 单击 = `setTheme(id)`（`T/client/AppearanceRow.tsx:54`）；无长按/双击。 |
| 5 | 行为 | 写 preference（`light/dark/system`）；**选中态跟持久化偏好，不跟解析后的实际主题**（注释 `:5-7`）→ 系统深色下「跟随系统」仍是选中的那个。 |
| 6 | 视觉状态 | 选中：`bg-module-platform` 底 + `--dsw-static-neutral-bluish-400` 边框（`T/client/AppearanceRow.module.css:52-57`）+ `aria-pressed=true`；hover（未选中）：`interactive-bg-hover`（`:48-50`）；无 pressed/focus 自定义（focus 走全局）。 |
| 7 | 布局占位与展开形态 | 参与设置列布局流；不展开。 |
| 8 | 滚动语义 | 跟随设置页滚动。 |
| 9 | 动效 | 无过渡声明（主题切换本身是全局 token 替换，见 `base.theme` token 结论）。 |
| 10 | 边界态 | 窄宽：cube 换行（`flex-wrap:wrap`，`:23`）；多语言：cube 内文字为 label，长语言会把 cube 撑高（无行数限制）；大字号：字号固定 14，不跟内容字号轴。 |
| 11 | 无障碍 | 原生 `<button>` + `aria-pressed={preference === id}`（`T/client/AppearanceRow.tsx:49-55`）→ 三选一读屏语义清楚；无 `radiogroup`（互斥组语义由 aria-pressed 近似表达）。 |
| 12 | 依据 | `E0` `T/client/AppearanceRow.tsx:19-63`；`E0` `T/client/AppearanceRow.module.css:1-57`；偏好定义 `E0` `T/theme-settings.ts:6-30`。 |
| 13 | 反证 + 验收 | **反证**：若选中态跟随「解析后的实际主题」而不是偏好，系统深色时用户会看到「深色」被选中而自己明明选的是「跟随系统」，无法判断设置是否生效；若用 radio 但样式不保证，读屏会播报两套状态。**验收**：`BASE-58` 系统深色下「跟随系统」仍高亮（真机）；`BASE-59` 读屏报「已按下」（TalkBack）。 |

#### `base.theme.font-size-row`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.theme.font-size-row`。字号设置行（文本/代码/终端三种 role 共用同一组件），文本行还带「更多字体设置」展开器（`T/client/FontSizeRow.tsx:49-98`、`T/client/FontSettingsGroup.tsx:34-56`）。出现：设置 General 区 3 行（其中 text 行带 expander）。 |
| 2 | 网格与定位 | 行：横向 flex gap 8、`padding:16px 0`、底部 0.5px 分隔线（`T/client/FontSizeRow.module.css:5-11`）；右侧控件组 gap 8（`:37-41`）；步进器 `min-width:72px; height:36px`、圆角 `--dsw-radius-md`（`:43-52`）；单位标签在步进器外（`:63-67`）。 |
| 3 | 命中区 | ①上/下箭头 **17×12 px**（`:90-102`）——**全审计最小命中区**；②展开器 **24×24**（`:114-126`）；③步进器容器 36px 高但不接收点击（value 是 span）。箭头仅在 hover/`focus-within` 时 `opacity:1`（`:73-85`），但**保留在 tab 序**（注释 `:69-72`：用 opacity 而非 visibility，正是为了让键盘 focus 触发显示）。 |
| 4 | 手势 | 单击箭头 = `setFontSize(±1)`（`T/client/FontSizeRow.tsx:67,76`）；到达 min/max 时 `disabled`（`:66,75`）；单击展开器 = 切换（`:91`）。**无长按连发、无滑动调值**（按一下 +1，从 10 调到 22 需 12 次点击）。 |
| 5 | 行为 | 受控：值来自 store（`:50`），显示「持久化值而非点击回声」（注释 `:6-8`）；展开器是**受控组件**（open/controls/onToggle 全由父级给，`:34-42`），展开区域 `{open && ...}` 挂载（`T/client/FontSettingsGroup.tsx:46-54`）。 |
| 6 | 视觉状态 | 箭头：默认半透明白底 chip，hover 完全不透明；disabled 用 `label-caption` 色（css `:104-111`）；箭头列默认 `opacity:0`，hover/focus-within 显示（`:73-85`）；展开器 hover 有 hover 底（`:128-130`），展开时箭头旋转 180° 100ms（`:132-138`）。 |
| 7 | 布局占位与展开形态 | 行本身不展开；展开区域在**下方另起 div**（`T/client/FontSettingsGroup.tsx:46-54`）→ 形态 A 的变体（inline 挂载、推挤后续行），**无高度动画**（`{open && ...}`，无 transition）。 |
| 8 | 滚动语义 | 跟随设置页滚动；`stepper:focus-within` 在滚动时不会自动滚到箭头（无 scrollIntoView）。 |
| 9 | 动效 | 箭头 opacity 瞬时（无 transition 声明于 `.arrows`）；展开器图标 `transform` 100ms（css `:132-138`）；展开区无动画。 |
| 10 | 边界态 | min/max：箭头 disabled（10/22 像素边界来自 `T/theme-settings.ts:24-27`）；多语言：`aria-label` 分「增大/减小」；大字号：**本行字号固定 14/12，不跟内容字号轴** → 用户把内容字号调到 22 时，字号设置行自己仍显示 14px（反讽但符合「设置界面不缩放」策略，见 Q-BASE-05）。 |
| 11 | 无障碍 | 三个原生 button：上/下各带 `aria-label`（`T/client/FontSizeRow.tsx:65,74`）；展开器 `aria-expanded` + `aria-controls`（`:89-90`）；**没有 `role=spinbutton`/`aria-valuenow`** → 读屏听不出当前值（值在相邻 span 里，`aria-label` 不含值，见 Q-BASE-12）。 |
| 12 | 依据 | `E0` `T/client/FontSizeRow.tsx:34-42,49-99`；`E0` `T/client/FontSizeRow.module.css:1-138`；`E0` `T/client/FontSettingsGroup.tsx:34-56`；范围 `E0` `T/theme-settings.ts:23-30,57-61`。 |
| 13 | 反证 + 验收 | **反证**：若箭头常驻可见，行内视觉噪音大且 17×12 的目标更容易被误触；若用 `visibility:hidden` 藏箭头，键盘用户永远 tab 不到（这正是注释 `:69-72` 的理由）；若按一下只 +1，从最小调到最大要连点 12 次，触屏上是明确的挫败源（→ Q-BASE-11 是否改长按连发或滑杆）。**验收**：`BASE-60` 箭头触控命中（真机，可能需要扩边）；`BASE-61` 键盘 Tab 到隐藏箭头时箭头显形（键盘 + 截图）；`BASE-62` 到达 10/22 时对应箭头 disabled（单测）。 |

#### `base.theme.font-family-row`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.theme.font-family-row`。字体族列表输入行（文本/代码/终端各一行），位于字体设置展开区内（`T/client/FontFamilyRow.tsx:34-64`，由 `T/client/FontSettingsGroup.tsx:47-53` 渲染）。 |
| 2 | 网格与定位 | 行布局复用 `FontSizeRow.module.css` 的 `.row/.rowText`（`T/client/FontFamilyRow.tsx:42-46`）；输入框用 `Input`（32px 高，见 `base.primitives.input`）。 |
| 3 | 命中区 | 输入框整体（32px 高 × 可用宽）；无额外点击目标。 |
| 4 | 手势 | **blur 提交**（`onBlur` → `commit`，`T/client/FontFamilyRow.tsx:55`）；**Enter 触发 blur** 从而提交（`:57-61`），且 IME 组合期（`isComposing` 或 keyCode 229）不处理（`:58-59`）。**没有显式保存按钮**。 |
| 5 | 行为 | `commit` 先 `normalizeFontFamily` 归一化（去引号/反斜杠/控制字符、非泛型族名加双引号），再写回 input.value 并 `setFontFamily`（`:37-40`）；清空 = 恢复内置字体栈（注释 `:5-6` + `T/theme-settings.ts:163-169`）。是否持久化由 store 决定。 |
| 6 | 视觉状态 | 输入框自身状态见 `base.primitives.input`；归一化后值会被就地改写（用户能看到引号被加上）。 |
| 7 | 布局占位与展开形态 | 位于展开区内的普通行，不展开。 |
| 8 | 滚动语义 | 跟随设置页滚动；输入框内横向滚动（原生 input）。 |
| 9 | 动效 | 无声明。 |
| 10 | 边界态 | `maxLength=200`（`:56`）；空字符串 → 内置栈；非法字符被静默剥离（不报错、不提示）；`key={value}` 强制在持久化值变化时重建输入（`:48`）→ 外部改值会冲掉未提交的草稿（见 Q-BASE-13）。 |
| 11 | 无障碍 | `Input` 带 `aria-label={title}`（`:52`）；无错误播报（归一化不报错）；无 `aria-describedby` 关联描述文本。 |
| 12 | 依据 | `E0` `T/client/FontFamilyRow.tsx:8,34-64`；`E0` `T/theme-settings.ts:149-169`；`E0` `P/Input.tsx:21`。 |
| 13 | 反证 + 验收 | **反证**：若只在 Enter 提交而不在 blur 提交，用户改完直接点别处会丢修改；若 IME 组合期按 Enter 就提交，中文输入法选词会打断输入；若归一化不静默剥离非法字符，注入引号/反斜杠能破坏 CSS 字体列表。**验收**：`BASE-63` 改完点别处自动生效（真机）；`BASE-64` 输入含引号的字体名不破坏样式（单测 + 截图）。 |

#### `base.theme.language-row`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.theme.language-row`。语言选择行（`C/LanguageRow.tsx:30-67`），注册进设置 General 区。**注意**：`10-panels.md` 把该行归给 `ui-locale`，但 DSH 没有这个包（见 §4 Q-BASE-01）。 |
| 2 | 网格与定位 | 行：横向 flex gap 8、`padding:16px 0`、底部分隔线（`C/LanguageRow.module.css:4-10`）；选择器按钮高 **36px**、`padding:0 14px`、gap 12、圆角 `--dsw-radius-md`（`:28-43`）。 |
| 3 | 命中区 | 选择器按钮 = 36px 高 × 内容宽（达标宽度、高度略低于 44dp）；行内其余部分不可点。 |
| 4 | 手势 | 单击 = 开/关菜单（`onClick setOpen(v=>!v)`，`C/LanguageRow.tsx:58`）；菜单内点击 = 选语言并关闭（`:46-49`）。 |
| 5 | 行为 | `setLocale(id)` + 关闭；选中态由 store 的 `active` 决定；菜单 `selectedId` 标记当前项（`:45`）。 |
| 6 | 视觉状态 | selector hover：`interactive-bg-hover`（css `:45-47`）；`aria-expanded` 反映开合（`C/LanguageRow.tsx:57`）；无 pressed/disabled 声明。 |
| 7 | 布局占位与展开形态 | 行不展开；菜单是形态 C 浮层（`align="end"` + `portal`，`:50-51`）。 |
| 8 | 滚动语义 | 菜单内部可滚（`base.primitives.menu` 的 scrollable 分支）；行本身跟随设置页滚动。 |
| 9 | 动效 | 无声明（菜单动效在 Menu 内）。 |
| 10 | 边界态 | 当前语言不在 options 里时显示原始 id（`?: active`，`:34`）；多选项很多时依赖 Menu 的高度上限；`aria-haspopup="menu"` 已给（`:56`）。 |
| 11 | 无障碍 | 原生 button + `aria-haspopup="menu"` + `aria-expanded`（`:56-57`）；可访问名 = 当前语言文本；全文件无 `aria-label`，可访问名不含「切换语言」这类动作词（`:53-63`）。 |
| 12 | 依据 | `E0` `C/LanguageRow.tsx:30-67`；`E0` `C/LanguageRow.module.css:1-47`。 |
| 13 | 反证 + 验收 | **反证**：若选择和确认合成一步（点菜单项立即切换），误触即改全局语言、用户会瞬间看不懂界面（不可撤销感）；若不用 menu 而用原生 select，移动端会弹系统选择器、与统一菜单观感断裂。**验收**：`BASE-65` 误触后能用同一入口切回（真机）；`BASE-66` 菜单项读屏报「当前选中」（TalkBack）。 |

#### `base.primitives.connection-indicator`（补录，归 atoms）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.connection-indicator`。连接状态指示器，坏态本身是「重连」按钮（`P/ConnectionIndicator.tsx:58-103`）。出现：断线/连接中/已恢复三态，挂在会话/壳的某处。 |
| 2 | 网格与定位 | 一行：图标（14px）+ label，具体尺寸在 `P/ConnectionIndicator.module.css`（本节点未逐行取证）。 |
| 3 | 命中区 | button 盒（图标 + 文案整块，`P/ConnectionIndicator.tsx:75-81`）；非 button 的「已恢复」态不可点（`:60-71`）。 |
| 4 | 手势 | 单击 = `onReconnect`（`:80`）；连接中态同一按钮语义变为「重新开始」（aria-label 用 `restartActionLabel`）。 |
| 5 | 行为 | 由 `state` 驱动：`connecting` / `warning` / `recovered`；恢复态显示定时后自行隐藏（`:55-56` 的 timeout）。 |
| 6 | 视觉状态 | `.indicator` 三态 class；`.success` 绿（css 命中 `:39,58`）；`leaving` 过渡（`:59`）。 |
| 7 | 布局占位与展开形态 | 内联块，参与布局流（非浮层）；出现/消失会推挤周围内容（**未用 overlay**，与 G-01 只对浮层生效不冲突）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 有 leaving 过渡（`:59` + css 命中行）。 |
| 10 | 边界态 | 离线/超时：三态覆盖；恢复后自动消失（`:55-56`）；连接中 `.dots` 动画点（`:92-94`，视觉隐藏于 `aria-hidden`）。 |
| 11 | 无障碍 | 恢复态 `role="status"` + `aria-label`（`:63-65`）→ 自动播报；可点态用 `aria-label` 说明动作（重连/重启，`:79`）。 |
| 12 | 依据 | `E0` `P/ConnectionIndicator.tsx:55-103`；`E0` `P/ConnectionIndicator.module.css:39,58`（CSS 命中行，结构属性）。 |
| 13 | 反证 + 验收 | **反证**：若指示器不可点，用户断线后只能等自动重连，没有「立刻重试」的出口；若「已恢复」也给 button，用户会点到一个无动作控件。**验收**：`BASE-67` 断线时点指示器触发重连（真机）；`BASE-68` 恢复态不会被键盘聚焦。 |

---

## 3. 排除清单（按理由聚合）

> 口径：`--only-prefix` 范围内的 530 条命中 = 已归节点 **248 条**（E0 245 + E1 3）+ 排除 **282 条**（下表按理由聚合）。
> 排除行全部在 `docs/spec/ref/dsh-base.facts.csv` 里有逐行 note（不是按 kind 批量）。

### 3.1 结构性 CSS 属性，不构成交互点 —— 138 条

| kind | 条数 | 说明（举例） |
|---|---|---|
| `css.overflow` | 45 | `overflow:hidden/auto` 只是裁剪/滚动容器声明，不绑定任何事件。例：`P/DisclosureRow.module.css:33`（行盒裁切）、`L/AppFrame.module.css:60`（列裁切）、`D/dockkit.module.css:584`（body 内滚）。滚动语义已写进对应节点的字段 8，不再单独计为交互点。 |
| `css.cursor` | 45 | `cursor:pointer` 只是光标语义，不创造命中区；真正的命中区在节点的字段 3。例：`P/DisclosureRow.module.css:43,75`、`D/dockkit.module.css:375`（chip）、`T/client/AppearanceRow.module.css:45`。 |
| `css.focus` | 44 | `:focus` / `:focus-visible` / `:focus-within` 选择器只负责焦点视觉，焦点顺序与语义在节点的字段 11。例：`T/styles/focus.css:10,18`（全局焦点环）、`P/Menu.module.css:119`。 |
| `css.sticky` | 4 | `position:sticky` 是吸附定位声明。例：`P/MenuGroup.module.css:20`（分组头吸附）、`P/TerminalBlock.module.css:126,134`、`P/markdown/CodeBlock.module.css:25`（代码头吸附——属 G-06 同族但不在本次交互点定义内）。 |
| `css.contain` | 0 | 本路 6 个包的 `src/` 内 0 命中——G-02 的 `contain:size layout` 只在 `ui-chat/ReasoningRow.module.css`（R1b 范围），不在基座包里。 |

### 3.2 测试代码 —— 142 条（+ `locale/tests` 3 条，共 145）

全部位于 `*/tests/*.spec.tsx`。这些命中是**测试夹具/断言**：fixture 里的 `<button>` 用来挂 Tooltip/Menu/Modal，`onClick` 是 `vi.fn()` mock，`aria-expanded` 是对产品组件属性的断言。逐行 note 已写清该 spec 的角色，例如：
`ui-primitives/tests/tooltip.client.spec.tsx` 62 条（anchor 按钮夹具）、`atoms.client.spec.tsx` 16 条（回调次数断言）、`modal-layer.client.spec.tsx` 11 条（焦点/快捷键归属用例）。
另：`ui-theme/tests/font-settings-group.client.spec.tsx:72,75` 同样按测试理由排除（不保留 E0）。**本文件所有 E0 节点都另有源码行依据**，没有任何一个节点的唯一依据是测试。

### 3.3 非 UI 实现（注释 / 契约类型）—— 2 条

| 位置 | kind | 具体理由 |
|---|---|---|
| `ui-dockkit/src/contract/adapter.ts:59` | `aria.role` | TypeScript 契约文件里的 JSDoc 注释，描述**调用方应提供** `role="menuitem"` 才能被菜单探测；文件本身不渲染任何元素。 |
| `ui-primitives/src/Tag.tsx:2` | `jsx.onclick` | 注释明确写「a tag takes no `onClick` and no `active`」——即 Tag 是**非交互**原子，本条命中是注释里的反证说明。 |

### 3.4 本路范围内的"零命中"包

| 包 | 命中 | 结论 |
|---|---|---|
| `ui-slots` | **0** | 纯契约/渲染器（`ensure`/`renderer.ts` 只有类型与接口），无 DOM 交互点 → 无可归节点，也无排除行。 |
| `ui-locale` | **0** | **包不存在**（`Test-Path ...\ui-locale` = False）。改以 `locale/` 取证：非测试命中 4 条 = 3 条 E0（`locale/src/client/LanguageRow.tsx:53,57,58`，已归 `base.theme.language-row`）+ 1 条 CSS 排除（`LanguageRow.module.css:42`）。`locale/` 在 `--only-prefix` 外，故不计入 530，也不会被判为 stale。 |

### 3.5 E1 条目（只有文档/注释依据，已进事实库）

| 位置 | 节点 | 注释声称的内容 | 为什么仍可用 |
|---|---|---|---|
| `P/Pill.tsx:1` | `base.primitives.pill` | 「给 `onClick` 时可选中，否则静态 span」 | 同一文件 `:23-31` 有 E0 实现佐证 → E1 只用于这句设计意图 |
| `P/Pill.tsx:11` | `base.primitives.pill` | 同上（JSDoc） | 同上 |
| `P/Menu.tsx:155` | `base.primitives.menu` | 列表行是 `role="menuitem"` 的 button | 实现在 `:456-478`（E0），注释只解释设计 |

---

## 4. 待确认（E2 候选，必须用户拍板）

> 依据 `README.md §0` G-00：源码没说清、或两种解都通的问题**只能**写在这里，禁止进 §1/§2。
> 每条给出两解（或以上）各自的后果。编号从 `Q-BASE-01` 起。

### Q-BASE-01 `ui-locale` 包不存在——文档与源码不一致，以哪个为准？

- **事实**：`E:\deepseek-harness\packages\client\ui-locale` 不存在（`Test-Path` False）；实际 i18n 包是 `locale/`，语言设置行在 `locale/src/client/LanguageRow.tsx`。但 `docs/spec/10-panels.md:18` 把 `settings.general` 的证据包写成 `ui-theme`, `ui-locale`。
- **解 A（以源码为准）**：把 `10-panels.md` 的 `ui-locale` 改为 `locale`，并在 `panels/settings.general.md` 引用 `locale/LanguageRow.tsx`。
  - 后果：证据链可对账；需要 Lead 改这份 spec 文件（本路无权改）。
- **解 B（保留 `ui-locale` 作为逻辑名）**：在注册表里把 `ui-locale` 定义为「语言/文案基座」的逻辑 ID，实现指向 `locale` 包。
  - 后果：`10-panels.md` 与源码目录名永久错位，后续每次审计都要解释一次；`ref` 事实库的 path 与实际包名也对不上。
- **建议**：解 A。本条属**文档缺陷**，不是设计分歧。

### Q-BASE-02 「无限抽屉」的层叠模型：DSH 没有 drawer 栈，myco 该借哪一套？

- **事实**：`ui-layout` 的主区是**单槽位覆盖式**——只有一个 `panelInfo.activePanelId`，换面板是替换，无返回栈（`L/stores.ts:16-22,94-96`；`L/service.ts:28-54` 无 pop/back）。`ui-dockkit` 的层叠是**浮层数组**——`state.floats` 顺序即 z 序，按下抬升（`D/FloatLayer.tsx:63-66,177`），同样无返回栈，且浮层**不会**让停靠区让位（`dockkit.module.css:154-160`：零尺寸 grid item 提供堆叠上下文）。
- **解 A（覆盖式 + 自建返回栈）**：借 `ui-layout` 的单面板替换语义，但 myco 自己在抽屉壳里维护 `stack: DrawerId[]`，返回 = pop。
  - 后果：无现成源码依据，栈行为（返回手势、跨层状态保持、深链）全部要在 P3 从零定义；好处是层数无上限、每层全屏、符合「无限抽屉」字面。
- **解 B（浮层数组式）**：借 dockkit 的 `floats[] → zIndex = index + 1` 模型，抽屉 = 依次抬升的浮层，最上面那个全屏、下面露出边缘。
  - 后果：有现成源码可对账（抬升/尺寸/阈值都能抄）；但 dockkit 的浮层**不推挤底层**、且 z 上限靠数组长度，`MAX_DOCK_PANES=4` 只限停靠不限浮动（`E/constraints.ts:10`），所以「无限」在技术上成立但**没有边缘露出的实现**（dockkit 浮层是完整矩形，无边缘露条）→ 「无可见抓手 + 边缘露出」仍需自研。
- **解 C（两层混合）**：底层用 `ui-layout` 三列（会话/侧栏），抽屉层用浮层数组，抽屉之间不返回而是「同时存在多层、点下层即抬升」。
  - 后果：最接近 dockkit 的真实语义，也最省实现；但「返回」变成「点下层」，与移动端用户对抽屉的返回预期（左滑/Esc/返回键）不同 → 需要额外定义手势。
- **必须用户拍板**，因为它决定 `panels/base.layout.md` 的核心契约（层数上限、返回语义、边缘露出宽度）。

### Q-BASE-03 披露行移动端命中区怎么放大（Q-03 的直接输入）

- **事实**：DSH 披露行命中矩形 = `24px + delta` 高的行盒（`P/DisclosureRow.module.css:36`），且 `.row` 有 `overflow:hidden`（`:33`）→ **无法**用伪元素 `::before` 向外扩边（会被裁掉）；第二种形态只有 16×16 图标按钮（`:50-63`）。全库无 44dp 先例（§0-10）。
- **解 A（改行盒高度）**：折叠行在移动端直接做 44px 高。
  - 后果：最省事、命中最好；但破坏 G-02 的「定高 24px」基准，桌面与移动两套高度 → G-02 的 `contain:size layout` 与 `height: calc(24px+delta)` 要改成分断点两套，摘要流式变化的行高抖动风险仍在（只是基线变了）。
- **解 B（外层包裹扩边）**：保持 24px 视觉行，外面套一层 44px 可点容器，命中区上下各扩 10px。
  - 后果：视觉不变、符合 G-02；代价是行间实际占位从 24 → 44px，**垂直密度照样掉**，且相邻两个披露行的命中区会重叠（行距 24px 时上下扩边必然相交）→ 需要额外的「重叠仲裁」规则。
- **解 C（整行命中 + 命中区不重叠）**：只把 `expandOnRowClick` 设为 true（整行宽命中），高度保持 24px（面积 × 宽），不做垂直扩边。
  - 后果：命中率提升但仍是 24px 高，手指落点垂直偏差 >12px 就失败；且 `.row` 的 `overflow:hidden` 让横向扩边也做不到（只能靠行宽）。
- **解 D（改交互形态）**：移动端不使用「行内展开」，点行 → 底部 sheet（形态 C）展示内容。
  - 后果：命中问题彻底消失、也符合移动端惯例；但 G-03 说三形态禁止混用，思考行在移动端变成 sheet 与桌面不一致，且「边读边看正文」的意图被打断。
- **必须用户拍板**（这正是待拍板 Q-03 的技术输入）。

### Q-BASE-04 触控最小命中 44dp 的统一策略放在哪一层？

- **事实**：DSH 全部基座原子都低于 44dp：图标动作 24×24（`P/CodeCard.module.css:56-61`）、switch 36×20、modal close 28×28、dock 控件 28×28、标签关闭 20×20、字号箭头 **17×12**、列宽手柄 8px、分栏手柄 8px、披露图标按钮 16×16。
- **解 A（改原子尺寸）**：在 `base.primitives` 统一给交互原子最小 44×44 命中盒（视觉可以仍是 24px，用伪元素/透明 padding 扩）。
  - 后果：一次性解决全部面板；但 28px 的行内动作条与 24px 密集行会被 44px 命中盒互相重叠（同一行内两个 24px 图标中心距 28px < 44px）→ 必须接受「相邻按钮命中区重叠」或把行高抬到 ≥44px。
- **解 B（只在移动端断点扩边）**：桌面保持 DSH 尺寸，`@media (pointer:coarse)` 下逐组件加扩边。
  - 后果：不伤桌面密度；代价是每个原子一张移动端覆盖清单，且 `pointer:coarse` 在触屏笔记本上会误判。
- **解 C（只放大高频动作，接受其余小目标）**：披露行、发送、关闭、复制放大；字号箭头/分栏手柄这类低频精确操作保持小。
  - 后果：工作量最小、风险可控；但「低频」是推测（无数据支撑），且无障碍标准（WCAG 2.5.8 24px 最小、移动平台 44/48dp 建议）会留缺口。
- **必须用户拍板**：这是全局面板契约，不能由单个面板决定。

### Q-BASE-05 字号轴覆盖范围：只有「内容」跟着缩放，控件不缩，myco 跟不跟？

- **事实**：`--dsh-content-font-size`（10–22）只驱动内容区派生量（`T/styles/gradient-shadow-text.css:56-63`、`T/theme-settings.ts:57-61`）；`Button` 字号固定 14/12（`P/Button.module.css:11,30`）、设置行字号固定 14/12（`T/client/FontSizeRow.module.css:22-34`）、Switch/Pill 固定（`P/Pill.module.css:10`）。即**用户把内容字号调到 22 时，设置界面与按钮仍是 14px**。
- **解 A（照 DSH：只缩放内容）**：字号设置只影响会话正文/过程文字。
  - 后果：与 DSH 一致、实现成本低；但对低视力用户，设置页本身仍读不清（他们恰恰要改设置）。
- **解 B（全局按比例缩放）**：所有字号乘同一比例。
  - 后果：可读性最好；但 G-02 的 `24px + delta` 定高是按「内容字号」设计的，全局缩放会让行高/图标/间距一起漂，`contain` 隔离也挡不住（行高本身变了）。
- **解 C（分两档）**：内容按用户字号，chrome（设置/菜单/按钮）用固定的「大字号模式」单独放大。
  - 后果：兼顾；多一套 token，实现量最大。
- **必须用户拍板**：决定 `base.theme` 的 token 命名（是 `--dsh-content-*` 还是 `--dsh-ui-*` 两套轴）。

### Q-BASE-06 选中态 chip（Pill）的语义：`aria-pressed` 还是 tab/radio？

- **事实**：`Pill` 选中只有样式（`.active`，`P/Pill.module.css:24-28`），组件不带 `aria-pressed`（`P/Pill.tsx:27-31` 只透传 `rest`）。DSH 里筛选 chip 的可访问性完全靠调用方补。
- **解 A**：myco 的 Pill 强制 `aria-pressed={active}`。
  - 后果：读屏能报「已按下」；但多个 Pill 互斥时读屏会连听 N 个「未按下」开关，不如 radio 组清楚。
- **解 B**：myco 用 `role="radiogroup"` + `role="radio"` + `aria-checked`。
  - 后果：互斥语义正确、可报「第 2 项，共 5 项」；但不再是「chip」的自由多选模型（过滤器允许多选时得换控件）。
- **后果依赖**：myco 的筛选项到底是单选还是多选（需 P3 面板表确认）。

### Q-BASE-07 复制失败如何反馈？

- **事实**：DSH 内部**不一致**——`ReadBlock`/`DiffBlock`/`TerminalBlock`/`SearchBlock` 复制失败时 `return`，按钮保持原样、无任何提示（`P/ReadBlock.tsx:97-98`、`P/DiffBlock.tsx:178-180`）；`JsonTree` 的 copy 按钮有 `failed` 态（`P/JsonTree.tsx:121-124,140`）。
- **解 A（DSH 主流：静默）**：失败即无反馈。
  - 后果：实现最简；但用户点了没反应会重复点，且不知道是「没复制成功」还是「按钮坏了」。
- **解 B（统一失败态 + 播报）**：失败显示错误图标/文案 + `role=status` 播报。
  - 后果：可诊断；增加每处复制按钮的状态机，且需要本地化错误文案。
- **解 C（走 toast）**：失败弹 toast。
  - 后果：与 G-01（浮层不占位）兼容；但高频动作弹 toast 会吵。

### Q-BASE-08 内容块的「内滚」与「折叠」谁优先？

- **事实**：内容块同时有两套机制——头尾截断折叠（`base.primitives.content-fold`）与块体内滚（`P/TerminalBlock.module.css:47,157`、`P/SearchBlock.module.css:25,47`）。DSH 没有定义二者同时生效时的优先级（源码里折叠按行数、内滚按像素，互不知情）。
- **解 A（折叠优先）**：先折叠到 maxLines，块体不再内滚（高度自然）。
  - 后果：G-04（内滚到边停住）不适用，手势统一交给会话滚动；但超长块仍会很高（maxLines 行数 × 行高）。
- **解 B（内滚优先）**：块体固定高度内滚，折叠按钮管「显示多少行」。
  - 后果：块高可控；但会出现内外双层滚动，必须实现 G-04 的到边吸收，否则滑动会串联。
- **解 C（二选一由块类型决定）**：终端/搜索用 B，diff/read 用 A。
  - 后果：最贴近 DSH 现状（不同块 CSS 不同）；规则碎片化，P3 要逐块声明。

### Q-BASE-09 列宽 / 分栏 / 浮层拖拽 需要键盘可达吗？

- **事实**：三处拖拽**全部无 role、无键盘替代**——列宽手柄是无 role 的 div（`L/AppFrame.tsx:106-116`），分栏分隔条是无 role 的 div（`D/PaneTree.tsx:38-50`），浮层移动/缩放是 div + 指针手势（`D/FloatLayer.tsx:126-127,183-187`）。
- **解 A（不做，桌面专用）**：承认这些是「鼠标/触控专有」，myco 首版不做键盘替代。
  - 后果：无障碍缺口（WCAG 2.1.1 键盘可达）；但用户群若以触屏为主，分栏/浮层可能根本不做。
- **解 B（给每个手柄 role=separator + aria-valuenow + 方向键）**：补 ARIA 窗口分割器语义。
  - 后果：达标、可测；实现量 = 3 个组件的键盘状态机 + 焦点环设计。
- **解 C（提供菜单替代）**：不给手柄键盘操作，但在菜单里给「列宽/面板尺寸」预设项。
  - 后果：成本最低、可发现性反而更好；但无法精确调整。

### Q-BASE-10 触屏上关闭标签需要两步（先激活再关）是否可接受？

- **事实**：标签关闭按钮默认 `opacity:0` **且** `pointer-events:none`，只有 hover / `focus-within` / **激活芯片**时才可点（`D/dockkit.module.css:433-446`）。触屏没有 hover → 必须先把标签切成激活态才能关。
- **解 A（保持）**：触屏两步。
  - 后果：不会误关（关闭是破坏性动作）；但关一个后台标签要两次点击，且第二次点击的按钮才刚出现（位置跳变）。
- **解 B（触屏常显关闭按钮）**：`@media (pointer:coarse)` 下常显。
  - 后果：一步关闭；但每个 chip 尾部 20px 都是关闭区，误关风险高（DSH 注释 `:434-436` 明确担心此事）。
- **解 C（长按 chip 出菜单，菜单里关闭）**：把关闭从 chip 表面移走。
  - 后果：零误关、符合移动端惯例；但长按手势与 chip 拖拽的 pointerdown 冲突（需阈值加时长判定，DSH 未做）。

### Q-BASE-11 字号步进器：现在是「按一下 ±1」，10→22 要点 12 次

- **事实**：`FontSizeRow` 两个 17×12 箭头，单击 ±1，到边界 disabled（`T/client/FontSizeRow.tsx:62-79`、`T/client/FontSizeRow.module.css:90-111`）；无长按连发、无滑杆、无号码输入（`value` 是 `<span>`，`:60`）。
- **解 A（保持 ±1）**：简单、精确、可预期。
  - 后果：调大字号要连点 12 次，且每次 17×12 的目标极难命中。
- **解 B（长按连发）**：按住 400ms 后每 80ms ±1。
  - 后果：快；但需要处理「按住到边界」与「松手后误多一步」，触屏长按与滚动手势仲裁。
- **解 C（滑杆 / 直接输入）**：用 Slider 或可编辑数字。
  - 后果：一步到位；但滑杆的取值粒度（22 档）在窄屏上不好点，输入框增加校验。
- **附带**：无论哪种，都要回答「读屏如何知道当前值」——现在三处按钮都**没有** `aria-valuenow`/`role=spinbutton`（`:62-84` 只有 aria-label 分增/减）→ 见 Q-BASE-12。

### Q-BASE-12 字号设置行的读屏语义：补 spinbutton 还是留在 button 上？

- **事实**：值在 `span.value`（`T/client/FontSizeRow.tsx:60`），两个 button 只有 `aria-label`「增大/减小」（`:65,74`）；无 `aria-valuenow`/`aria-valuemin`/`aria-valuemax`，无 `role=spinbutton`。
- **解 A（改用 `role=spinbutton` + `aria-valuenow`）**：把整个 stepper 做成 spinbutton，箭头作为内部操作。
  - 后果：读屏能报「14，最小 10，最大 22」；但 spinbutton 语义要求键盘上下键调值，与现有「箭头是独立 tab 停靠点」冲突，需重排焦点。
- **解 B（保留两个 button，把值放进它们的 aria-label）**：如「增大字号，当前 14，最大 22」。
  - 后果：改动小、立刻可懂；每次读都要念一遍当前值，稍啰嗦。
- **解 C（值本身可聚焦，`aria-live` 播报变化）**：值 span 加 `role=status`。
  - 后果：调值有播报；但每次点击播报 + 按钮 aria-label 也播报 → 可能双重播报。

### Q-BASE-13 字体族输入框被 `key={value}` 重建，会冲掉未提交草稿

- **事实**：`FontFamilyRow` 的 `Input` 带 `key={value}`（`T/client/FontFamilyRow.tsx:48`）→ 只要持久化值变化（包括同一设置页另一处写入触发 store 更新），输入框就重建、未提交的草稿丢失；提交时机是 blur/Enter（`:55,60`）。
- **解 A（保持）**：值变化即重建，丢弃草稿。
  - 后果：外部改值能正确回显（`key` 就是为此）；但用户正在输入时如果别处写了同一个 store，草稿被静默吞掉。
- **解 B（改为受控 + dirty 保护）**：本地 draft，外部值变化时若 dirty 则不覆盖，标「未保存」。
  - 后果：不丢草稿；多一套 dirty 状态与「放弃修改」的 UI 语义（DSH 没有）。
- **解 C（提交时机改为 onChange 防抖）**：边打边存。
  - 后果：无草稿概念、最简单；但字体名打到一半会被当作真实值写进 store，产生闪烁与无效 CSS。


### Q-BASE-14 可展开组件要不要「≤240dp 才动画」这一档？（G-10 的分支在 DSH 无对应）

- **事实**：本路基座里**所有**可展开组件都没有高度动画——`DisclosureRow` 条件挂载（`P/DisclosureRow.tsx:121`）、`FoldToggle` 无动画（`P/FoldToggle.tsx:19-33`）、内容块 head-tail 折叠无动画（`P/ReadBlock.tsx:104`、`P/DiffBlock.tsx:185`）、`JsonTree` 展开无动画（`P/JsonTree.tsx:609`）、字号设置展开区 `{open && ...}` 无动画（`T/client/FontSettingsGroup.tsx:46`）。DSH 唯一有的动效是 opacity/color 类微过渡（100–200ms）。
- **解 A（照 DSH：全部无高度动画）**：只保留淡入淡出。
  - 后果：与参考实现完全对齐、性能最稳；但用户会感觉「点了就跳」，短内容（如 3 行的 diff）失去「展开了」的物理反馈。
- **解 B（按 G-10：≤240dp 用纵向 spring，>240dp 直接切换）**：myco 新建一档动画。
  - 后果：短内容手感更好；但 240dp 阈值是 `user` 授权下的工程取值（`00-global-rules.md:220` 自标 E1），且**没有任何 DSH 代码可对账** → 需要在真机上单独验「动画期间内容是否可读」「是否掉帧」（G-10 的反证正是指这个）。
- **解 C（只有 ≤3 行的极小内容才动画）**：把阈值从 240dp 降到约 72dp（3×24px）。
  - 后果：动画几乎不阻塞阅读；但「什么算短」变成按行数判断，与 G-10 的像素阈值两套口径并存。
- **必须用户拍板**：它决定 `base.primitives.disclosure` / `content-fold` 的字段 9 是「无动画」还是「有阈值动画」。

---

## 附录 A. 与既有规则的交叉点（不改规则，只登记）

| 既有规则 | 本路取证结果 | 关系 |
|---|---|---|
| G-02 折叠行定高 | `P/DisclosureRow.module.css:36` 确实用 `calc(24px + delta)` 定高，但**该组件没有 `contain`**（`contain` 在 ui-chat ReasoningRow，属 R1b） | **确认 G-02 的写法形态**；`contain` 归属需在 P3 明确「谁负责隔离」 |
| G-03 展开三形态 | 基座只提供形态 A（DisclosureRow inline 挂载）与形态 B 的折叠按钮（FoldToggle/head-tail）；形态 C 由 Modal/HoverCard/Menu/lightbox 提供 | **三形态在基座层各有对应组件**，P3 选型有据 |
| G-06 展开态头部 sticky | 基座 `DisclosureRow` **没有** sticky（全文件无 `position:sticky`），只有 `MenuGroup`/`TerminalBlock`/`CodeBlock` 有 sticky | **G-06 不能当成 DisclosureRow 的默认行为**，它是调用方（ReasoningRow）加的 |
| G-07 展开状态随版本重置 | `DisclosureRow`/`FoldToggle`/`content-fold` 都是**受控或块本地 state**，无版本号机制；版本重置在 `use-disclosure.ts`（ui-chat，R1b） | 基座不提供 G-07，需要在面板层实现 |
| G-09 行内层级 secondary | `P/DisclosureRow.module.css:102` 用 `--dsh-content-font-size-secondary`，与 `T/styles/gradient-shadow-text.css:58` 的派生一致（≤14 减 1、≥15 减 2） | **确认 G-09 的派生公式**（E0 补齐） |
| G-10 展开动画阈值 240dp | 基座**全部**可展开组件都无高度动画（DisclosureRow/FoldToggle/content-fold/JsonTree/dockkit） | 若 myco 对 ≤240dp 加动画，将是**偏离 DSH 的新设计**（Q-BASE-04 相关） |
| G-04 内滚到边停住 | 基座里只有 3 个文件写了 `overscroll-behavior`，且都是「横向/模态内容含住」而非「到边停住 + 新手势才交外层」：`P/RiskConfirmation.module.css:10` (`contain`，模态内容)、`P/JsonTree.module.css:16-17` (`x:contain` / `y:auto`)、`P/markdown/MarkdownText.module.css:195` (`x:contain`，宽表格)。**注意**：`ui_audit.py` 的 `container.scroll` 正则只匹配驼峰 `overscrollBehavior`，这 4 行 CSS kebab 形式**未被扫描器计入分母**（见附录 B） | **G-04 在 DSH 基座仍无完整 E0 依据**（没有「残余速度吸收」实现，只有 overscroll 的容器内含），需 myco 自建（与 `00-global-rules.md` G-04「E0 证据待补」一致） |

---

## 附录 B. 工具与盘点口径缺陷（供 Lead 决定是否修 `ui_audit.py`）

> 本路发现盘点器本身有漏计，直接影响「P1 分母是否可信」。以下每条都已在本机验证。

| # | 缺陷 | 证据 | 对本路的影响 | 建议 |
|---|---|---|---|---|
| B-1 | `ui_audit.py` 的 `container.scroll` 正则只匹配**驼峰** `overscrollBehavior`，不匹配 CSS 里的 kebab `overscroll-behavior`；而 CSS 文件走的是另一张表（只有 cursor/focus/sticky/contain/overflow） | `tools/ui-audit/ui_audit.py:57`（PATTERNS）与 `:60-66`（CSS_PATTERNS）；实际存在 `P/RiskConfirmation.module.css:10`、`P/JsonTree.module.css:16-17`、`P/markdown/MarkdownText.module.css:195` 四处，**均未被扫到** | 「内滚到边」这类 G-04 关键线索被系统性漏掉 | 在 `CSS_PATTERNS` 增加 `css.overscroll`（`overscroll-behavior`、`scroll-behavior`、`touch-action`、`pointer-events`、`user-select`） |
| B-2 | `aria.role` 只匹配 6 个角色值（button/menuitem/tab/switch/option/checkbox） | `ui_audit.py:53` | `role="dialog"`（`P/Modal.tsx:65`、`P/ImageLightbox.tsx:53`）、`role="menu"`（`P/Menu.tsx:522`）、`role="tablist"`（`P/Menu.tsx` 无，`D/TabPanel.tsx:245`、`P/SegmentedControl.tsx:111`）**全部不在分母里**；铺层面板的「层」本身没被计数 | 角色白名单补 dialog/menu/tablist/listbox/radiogroup/menuitemradio，或把 `role=` 全量计入再分类 |
| B-3 | `native.anchor` 正则 `<a\s[^>]*href` 要求同一行内出现 href | `ui_audit.py:50` | 多行书写的 `<a>` 会漏（本路 `P/markdown/render.tsx:557-559` 恰好是单行 href 所以命中了；同类多行写法会漏） | 统计时可放宽为「含 `<a` 且后续 N 行内出现 href」 |
| B-4 | `--only-prefix` 是**字面前缀匹配**，`ui-locale/` 匹配不到实际包 `locale/` | `ui_audit.py:167-171`；`Test-Path ...\ui-locale` = False | 本路必须手工扩展前缀才能覆盖语言行，否则该行证据永远为 0 | 前缀表加别名映射（`ui-locale` → `locale`） |
| B-5 | 事实库键是 `kind|path|line` 三元组，同一条源码行上的多个 kind 必须各写一行事实 | `ui_audit.py:146-148,182-195` | 本路 530 条事实里，同一行最多 3 条（如 `P/DisclosureRow.tsx:96` 同时是 `jsx.onclick` + `disclosure`）→ 写事实库时不能按行去重 | 无需修工具，但 Lead 合并时要按 (kind,path,line) 而不是 path:line 对账 |

**口径结论**：本路 `uncovered=0` 是在「当前扫描器能看见的 530 条」上成立的；按 B-1/B-2 修正后分母会上升，但**上升的部分都已在本文件 §2 里以节点形式写了 13 字段**（Modal/Menu/lightbox 的 role、四个 overscroll 容器），补扫描后只需补 facts 行，不需要重写节点。

# P2 合并：base.primitives

> 合并员：M1。输入：`ref/dsh-base.md`、`ref/dsh-go.md`、`ref/taixu.md`、`ref/chat-demo.md`、`ref/legacy-myco.md`、`ref/dsh-mobile.md`、`ref/dsh-workspace.md`、`ref/dsh-settings.md`、`ref/dsh-chat.md`（仅取原子层段落）+ `00-global-rules.md` + `_conflicts.md`。
> 本文件只做**归一取舍**，不新增事实。凡 ref 未写的一律「未取证」，需要但没取证的进 §6。
> **v2（13:40 修订）**：`ref/dsh-go.md` 与 `ref/taixu.md` 于 13:18 落库 → 本版补齐两列、删掉原 `Q-BP-07`（"两路事实库不存在"，前提失效）、§2.1 命中区结论按 dsh-Go/太墟 重写。

## 0. 读表须知（三条，先看）

1. **四路手机/桌面实现的口径必须分开读**：`dsh-base`/`chat-demo` 是 **Web**（鼠标精度，无扩边），`dsh-go`/`taixu` 是 **手机原生**（dsh-Go = Flutter、太墟 = Compose），`legacy-myco` = 我方现状（Compose）。**同一节点在 Web 上的尺寸不能当手机答案**——`dsh-base.md` §0-10 与 `dsh-go.md` §5.2 从两个方向证明了这一点。
2. **证据缩写沿用各事实库原文，行内标出处库名**：
   - `dsh-base`：`P/`=`ui-primitives/src/`、`L/`=`ui-layout`、`D/`=`ui-dockkit`、`T/`=`ui-theme`；
   - `dsh-go`：`CL/`=`ui/chat/chat_list.dart`、`SR/`=`ui/chat/widgets/stream_row.dart`、`TP/`=`turn_process.dart`、`CO/`=`composer.dart`、`AT/`=`ui/theme/app_theme.dart`、`MO/`=`ui/theme/motion.dart`、`BL/`=`ui/common/bleed.dart`、`OF/`=`overscroll_forwarder.dart`、`LT/`=`ui/chat/widgets/long_text*.dart`（与 `dsh-go.md` 表内一致）；
   - `taixu`：全写包内相对路径（`feature/chat/.../ChatMessageBubbles.kt` 等，与 `taixu.md` 一致）；
   - `demo`：`B/`、`CI/`、`M/`、`MD/`、`S/`、`SE/`、`APP/`；`legacy`：`MA/`、`CC/`、`CR/`、`CP/`、`MS/`、`PN/`；`mobile`：`M/:line` = `dsh-client-ui-mobile-adapt/lib/client.js`。
3. **拍板列**：`✅ 采用建议`＝我给出可验收建议、等用户确认；`⏸ 待拍`＝决定权在用户且影响全局面板。**未拍板的行不得进入 P3**（`_merge-template.md:23`）。

---

## 1. 节点清单（先穷举，再填表）

> 口径：`base.primitives` = 所有面板复用的**原子层**。容器几何（三列壳、抽屉栈、dock 页签条 38px/拖拽/拆分）归 M2 `base.layout`，本表只取页签的**语义原子**。
> 「参考里有没有」= 五路事实库里是否登记了对应节点。`旧 myco` 以 `legacy-myco.md` §2.7（只有 2 个基座节点）为准，Material3 默认组件不算「有节点」。

| 节点 ID | 出现条件 | 参考里有没有 | 旧 myco 有没有 |
|---|---|---|---|
| `base.primitives.disclosure.row` | 可展开行的「整行即开关」形态 | dsh-base §2.1；dsh-go `chat.msg.thinking`/`chat.turn.process`/`chat.msg.assistant-markdown`；taixu `chat.msg.thinking`/`chat.msg.tool-row` | 无节点；过程组头/行是整行 `.clickable`（`CC:1074`） |
| `base.primitives.disclosure.leading` | 同一组件的另一形态：只有 16×16 图标是开关 | 仅 dsh-base §2.1（**dsh-Go / 太墟 均无此形态**） | 无 |
| `base.primitives.disclosure.fold-toggle` | 「还有 N 行」的头尾截断开关 | dsh-base §2.1（`FoldToggle`）；dsh-go `<details>` 折叠块 | 无 |
| `base.primitives.button` | 主/次/危险/工具栏文字按钮 | dsh-base §2.2 + demo §2.28 + dsh-go §5.1（主题尺寸）+ taixu `base.primitives.action` | 有（Material `TextButton`/`Button`，未登记 13 字段） |
| `base.primitives.pill` | 胶囊 chip（分段/筛选/只读徽标） | dsh-base §2.2 + dsh-go `base.primitives.row`（`SegmentedPill`/`StatusChip`）+ taixu `RuntimeFilterChip` | 无节点；M5 用 `FilterChip` 系（`MS:169-186`） |
| `base.primitives.icon-action` | 单个图标动作按钮 | dsh-base §2.2 + demo §2.8/§2.28 + dsh-go `chat.msg.actions` + taixu `base.primitives.action` | 有（M5 操作条 15dp 图标，`CR:601-607`） |
| `base.primitives.icon-action-bar` | 承载 ≥2 个图标动作的一行 + 显隐策略 | **无基座抽象**；chat 级实例：dsh-go `chat.msg.actions`、taixu `chat.msg.assistant-bubble`、dsh-chat §2.5、demo §2.8 | 有（`CR:600-607`、`CC:1477-1496`） |
| `base.primitives.tooltip` | 图标按钮的提示/可达名 | dsh-base §2.2 + demo §2.31；dsh-go 用 Flutter `Tooltip(label)`；太墟无（只 `contentDescription`） | 无（Android 无 hover，旧 myco 未做） |
| `base.primitives.hover-card` | 悬停/聚焦浮出的预览卡 | dsh-base §2.2 + demo §2.14；**dsh-Go / 太墟 均无** | 无 |
| `base.primitives.modal` | 居中模态框（确认/表单/风险确认） | dsh-base §2.2 + demo §2.30 + dsh-go `base.primitives.sheet`（含 `RiskNotice`）+ taixu `base.primitives.sheet-dialog`（`AlertDialog`/`RuntimeDialog`） | 有（`AlertDialog`，`MA:590-681`） |
| `base.primitives.dialog-dismiss` | 任意弹层的取消/关闭路径 | 无独立节点（并入 modal）；dsh-go 有 `DiscardGuard`（丢弃守卫） | **有**（legacy §2.7，11 处实例） |
| `base.primitives.bottom-sheet` | 底部升起面板（选择/来源/详情） | dsh-go `base.primitives.sheet`（全 App 唯一弹层入口）；taixu `base.primitives.sheet-dialog`（`ModalBottomSheet`/`ChatBottomSheet`） | **有**（`ModalBottomSheet`，`MA:1291-1315`） |
| `base.primitives.menu` | 锚定下拉/上下文集 | dsh-base §2.4 + demo §2.29；dsh-go **无锚定下拉**（菜单=底部 sheet）；太墟 无 popup menu（`WindowDropdownPreference` 单选弹窗） | 无节点；M5 模型面板是 `ModalBottomSheet` 内扁平行 |
| `base.primitives.toast` | 底部一次性提示（可带动作/撤销） | dsh-base §2.2 + dsh-go `showBriefNotice` + taixu Toast/Snackbar（调用点分散） | 有（Material `Snackbar`，`MA:477-480`） |
| `base.primitives.image-lightbox` | 图片全屏查看 | dsh-base §2.2 + dsh-go `chat.msg.image`（含缩放/图库） | 无节点（旧 myco 无灯箱） |
| `base.primitives.connection-indicator` | 断线/连接中/已恢复三态 | 仅 dsh-base §2.7 | 无 |
| `base.primitives.switch` | 设置项布尔值 | dsh-base §2.3 + dsh-go `base.primitives.row`（整行接管）+ taixu `preference-row` | 有（`MS:169-186` 自绘） |
| `base.primitives.checkbox` | 勾选/多选 | dsh-base §2.3 + taixu（「记住选择」+ A2UI CheckBox） | 无节点（`MA:1916-2030` 有 3 个 Checkbox） |
| `base.primitives.input` | 受控文本输入 + 行内编辑 | dsh-base §2.3 + dsh-go `composer.editor` + taixu `composer.input` | 无节点（`OutlinedTextField`，`CP:387-395`） |
| `base.primitives.segmented-control` | 少量模式切换 | dsh-base §2.3 + dsh-go `SegmentedPill` + taixu `RuntimeSegmentedControl` | 无 |
| `base.primitives.tab-strip` | 页签条的**语义原子** | dsh-workspace `base-sidebar-right.tab-strip`（唯一完整 WAI-ARIA 实现）；dsh-go/太墟 均无右栏多标签 | 无（M5 无右栏/无页签） |
| `base.primitives.content-fold` | 长内容块头尾截断/限高内滚 | dsh-base §2.5 + dsh-go `chat.msg.code-block`/`chat.tool.detail` + taixu `chat.msg.tool-detail-sheet` | 无 |
| `base.primitives.json-tree` | 只读 JSON 结构树 | 仅 dsh-base §2.5 | 无 |
| `base.primitives.markdown-link` | 正文内可点文本/文件 chip | dsh-base §2.5 + dsh-go `chat.msg.assistant-markdown`（链接热区补 48）；太墟未登记 | 有（`withLink`，`CC:402-416`） |
| `base.primitives.settings-field` | 设置表单字段 | dsh-base §2.5 + dsh-settings §2.2 + dsh-go `base.primitives.row` + taixu `preference-row` | 有（12 输入框 + 3 Checkbox，`MA:1916-2030`） |
| `base.primitives.form-save` | 表单底部「保存」 | dsh-settings §2.2 + dsh-go 保存条实例（`plugin_config_save_bar.dart:147-156`）；**太墟无保存按钮** | 有（`settings.provider-editor.save`） |
| `base.primitives.hit-target` | **新增原语**：命中盒包裹层（扩边落点） | **手法有 E0**（dsh-go §5.3 三种 + 太墟 `sizeIn(48,48)` 助手），**抽象无先例** | 无（整行 `.clickable`，无统一扩边层） |

> 计数：**27 个节点** = 25 个五路已有节点 + 1 个「行为有 E0、抽象为新」（`icon-action-bar`）+ 1 个新增原语（`hit-target`，见 §5）。

---

## 2. 对照表（主线）

> 冲突列只写「做到 A 就做不到 B」。`C-xx` = `_conflicts.md` 已登记；`C-BP-xx` = 本面板新发现、**编号待 Lead 收进 `_conflicts.md`**。
> **给手机端结论时以 dsh-Go / 太墟 为主证据**（同为触摸原生），DSH Web 作为「桌面兜底形态」保留。

| 节点 ID | DSH（Web 基准） | dsh-Go（Flutter 手机） | 太墟（Compose 手机） | chat-demo（Web） | 旧 myco | 冲突 C-xx | 建议 + 理由 | 状态 |
|---|---|---|---|---|---|---|---|---|
| `base.primitives.disclosure.row` | 整行即开关，`expandOnRowClick` 默认 `false`、由调用方开；命中=行盒 `24px+delta`，`overflow:hidden` 无扩边；形态 A 条件挂载、无高度动画（`P/DisclosureRow.tsx:50,64,96,121`；`module.css:30-44`） | 折叠头 **32dp 整行** + `Bleed` 横向各 8（`thinking_pill.dart:181-185`、`bleed.dart:87-96`）；`Semantics(button, expanded)`（`:181-184`）；展开后点正文也可收起（`:367-380`）；整轮过程摘要行同解剖 32（`turn_process.dart:165-166`）；**流式中另用「恒 3 行实时窗口」**（`thinking_pill.dart:383-388,416-440`） | 折叠头 `heightIn(min=28dp)` 整行 `clickable` + 触感（`ChatMessageBubbles.kt:565-573`）；工具行 28dp（`ToolStepHeader.kt:33-35`）；**无 `role`/`stateDescription`**（`chat.msg.thinking` 字段11 明写未见）；**流式/定稿切形态**（见 C-BP-05） | 无此原语；过程区标题 `div`+onClick，无 `role`/`tabindex`/`aria-expanded`（demo §2.11；`M/ReplyArea.tsx:81`） | 过程组头/行整行 `.clickable`，视觉 24–30dp，**无 `aria-expanded`**（legacy §2.1；`CC:1074`、`CR:340-344`） | **C-03**、**C-14**、**C-BP-05** | 采用形态 A + **整行可点 + 视觉高取 32dp**（dsh-Go 实测口径；太墟 28dp 亦可，取 32 是因为 dsh-Go 有「24 图标 + 上下各 4」的完整解剖）。**竖向不追 48**：命中面积由「整行宽 × 32 + 横向 Bleed 8」给（dsh-Go §5.2-2「行级可点优先于按钮级扩边」）。`aria-expanded` 必须有（demo/legacy/太墟 三家都缺 → 反面教材）。**若取 32 则 G-02 的 `24px+delta` 示例值要改**（G-02 的意图是定高不重排，值可换）→ Q-BP-01 + §6 文档修正 | ✅ 采用建议（数值随 Q-BP-01） |
| `base.primitives.disclosure.leading` | 16×16（+delta）图标 `<button>`，唯一开关，`aria-label`+`aria-expanded`（`P/DisclosureRow.module.css:50-63`；`tsx:99-108`）；面积≈整行的 1/8 | **无此形态**：折叠一律整行承载；行内附属图标只是指示器（chevron，`stream_row.dart:85-98`） | **无此形态**：chevron 16dp 不独立可点（`ChatMessageBubbles.kt:644`） | 无 | 无（图标在整行 `.clickable` 内） | **C-03** | **myco 收窄为一种形态：只做整行，不保留「只有图标可点」**（dsh-Go/太墟 2:1 对 DSH，且两家手机参考的反证一致：手机上可点面积只剩 20~30dp 时功能等于「找不到」，`dsh-go.md` `chat.msg.user` 字段13 原话）。首版没有「行内还有 accessory」的披露行场景（消息操作条是独立行）→ 无保留理由。见 §4-7 | ✅ 采用建议（不做该形态） |
| `base.primitives.disclosure.fold-toggle` | 原生 `button`，**组件无任何尺寸声明**，全由调用方 `className`（`P/FoldToggle.tsx:1-33`）；`aria-expanded`+`aria-label` 含隐藏行数 | `<details>` 折叠块：标题行整行 `GestureDetector(opaque)`、`SmoothSize`+`AnimatedRotation` 240ms、折叠态零高（`markdown_blocks.dart:669-748`）；`Semantics(button, expanded)`（`:708-712`） | 无「还有 N 行」按钮；超长内容走 sheet / 过程链分页 +8（`ToolStepsCard.kt:30-38`） | 无 | 无 | — | 采纳 DSH 语义（`aria-expanded` + 文案含 N 行）**+ dsh-Go 的「折叠态零高」**；尺寸必须由 myco 补（DSH 这里「未定义」不是可抄做法，G-00）→ `hit-target`；动画按 Q-BP-02 | ✅ 采用建议 |
| `base.primitives.button` | `md` 36 / `sm` 28；4 变体；字号固定 14/12 不跟内容字号轴（`P/Button.module.css:2-34`；Q-BASE-05） | 主题级：`FilledButton 64×50`、`TextButton 48×40`、`IconButton 44×44`（`AT:251-308`） | `RuntimeButton` 走 miuix 默认，token `RuntimeSpacing.buttonMinHeight = 48.dp`（`RuntimeSpacing.kt:14`）；图标钮 **40dp**（`ComposerSendButton.kt:20-24`） | `l=40/m=36/s=32`；IconButton 28/34/44；有 44 token（demo §2.28；`APP/theme.css:9-13`） | Material3 `TextButton` 默认 48dp（legacy §2.7；`CP:69-71`） | — | **文字按钮高 48**、**图标钮 40**（太墟明确拒绝 48，理由是「会把输入工具条撑高 8dp、实心底变 FAB」，`ComposerSendButton.kt:20-24` 注释）；不取 DSH 的 36/28（无触控下限）。反证：图标钮给 48 → 一行 5 个键在 360dp 屏上排不下（`dsh-go.md` `chat.msg.actions` 字段13 同款反证） | ✅ 采用建议 |
| `base.primitives.pill` | 高 24、`padding:0 8px`、字号 12；`onClick` 存在才渲染 button；**无 `aria-pressed`**（`P/Pill.module.css:1-14`；`P/Pill.tsx:16-35`；Q-BASE-06） | `SegmentedPill`（`segmented_pill.dart:137,233`）、`StatusChip`（只读徽标） | `RuntimeFilterChip` **注释声称含 48dp 助手但实现未调用**（`RuntimeComponents.kt:607-611` vs `:612-633`）→ 参考实现的缺陷（Q-TX-10） | 无同型原子（引用角标是自绘 span） | 无节点（M5 用 `FilterChip`/自绘 pill） | — | 采纳视觉；**强制 `aria-pressed`（Compose: `Modifier.selectable(selected=…)`）**；**不照抄太墟的「注释≠实现」缺陷**。反证：原子不带状态 → 读屏无法知道当前筛选项（`dsh-base.md:798` 原话） | ✅ 采用建议 |
| `base.primitives.icon-action` | 24×24（代码卡）/28×28（dock）；无扩边；复制成功换对勾 1000ms；**复制失败静默**（`P/CodeCard.module.css:56-61`；`P/ReadBlock.tsx:97-98`） | 操作条每键 **36×36** `tapTargetSize: shrinkWrap`、图标 20、`Bleed` 横向 8（`message_actions.dart:96-105,212`）；复制 1.4s 对勾（`:83-89`）；busy 用 spinner | 图标钮 **40dp**（图标 20/22，`ComposerSendButton.kt:20-24`、`ChatTopBar.kt:81-84`）；**行内次级图标 18/16/12dp 且不扩边**（`ChatMessageBubbles.kt:609,631,644`、`ToolStepsCard.kt:59-60`） | 图标 28×28（`B/IconButton.module.css:35-38`）；复制 1000ms、吞重复点击（`M/CopyButton.tsx:20-31`） | M5 操作条图标 **15dp 且无扩边**、无 `contentDescription`（legacy §4#5；`CR:601-607`） | **C-03** | 独立图标动作 **40dp**（太墟）或 36（dsh-Go）→ 取 40，理由见 button 行；**行内次级图标两家都做到 12–18dp，myco 不跟**：太墟自己的验收就要求「18dp 复制钮必须与 28dp 行头的展开点击区分开」（`action` 字段13 `TX-ACTION-03`），说明小目标是已知风险，不是可抄结论。复制成功**必须播报**（G-08 同族），复制失败**必须有失败态**（不采纳 DSH 静默；`JsonTree` 有 `failed`，`P/JsonTree.tsx:121-124`） | ✅ 采用建议（40）/ ⏸ 次级图标见 Q-BP-01 |
| `base.primitives.icon-action-bar` | **无基座抽象**；chat 级：一行 28×28、`gap:8`、高 `28+delta`；`@media(hover:hover)` 才 `opacity:0`，**触摸设备不进媒体查询 → 常驻**；隐藏用 `opacity` 不用 `display`（`C/MessageIconActions.module.css:5-10,42-61`） | **常驻**，且只给「轮内最后一条助手正文」（`chat_rows.dart:45-58,189-190`）；高 36（`message_actions.dart:96-105`）；反证明写「若塞进长按菜单，复制回复埋两层且读屏用户无法一次到达」（字段13） | **常驻**，且同样「只有每轮最后一条有正文的助手消息」获得完整动作条（`assistantToolbarIds`，`ChatTimelineProjection.kt:127-137`），中间更新只有 details 菜单 | `.actions{opacity:0}` + hover/focus，`isLast` 常显；**无 `@media(hover:none)` 兜底 → 触摸端历史消息不可达**（demo §0#6；`M/AssistantMessage.module.css:4-7,95-105`） | 常驻（旧 `TextButton` 文字常驻；M5 图标常驻） | **C-04** | **无条件常驻 + 只给轮尾最后一条**。理由升级为**三源一致**：DSH Web 触摸端常驻、dsh-Go 常驻、太墟 常驻；且 DSH 哪怕桌面也**仍占位**（高恒 `28px+delta`）→ 常驻只改 `opacity`，**零布局代价**。反证：照抄 demo 的 hover 显隐 = 把它的缺陷搬进来（该缺陷就是「无 hover 兜底」） | ✅ 采用建议（C-04 已可定） |
| `base.primitives.tooltip` | `fixed` 气泡 + 视口翻转；`role=tooltip` 但普通模式不给 `aria-describedby`；**指针模态下 focus 不触发**（`P/Tooltip.tsx:244-248,217,229-231`） | 图标键挂 Flutter `Tooltip(label)`（`message_actions.dart:117,145,157,189` 等）——ref **只取证到「读屏可见」**，长按是否显形未取证 | 无 tooltip；只有 `contentDescription`，且全仓抽查**有遗漏**（`action` 字段11） | 移动端 `isMobile` 时完全不显示（`B/Tooltip.tsx:24,43`） | 无（Android 无 hover） | — | **不做 hover 提示原语**（§4-3）；但**图标键必须挂可达名**：dsh-Go 用 `Tooltip(label)`、太墟用 `contentDescription`（且有遗漏 → 反面教材）。myco 统一走 `contentDescription`（§2.2）。反证：太墟的「未传则 TalkBack 只剩图标无语义」就是缺可达名的后果 | ✅ 采用建议 |
| `base.primitives.hover-card` | `fixed` 宽 244、grace 期、触屏 inline 变体不靠 hover 开、`copyable` 整卡复制（`P/HoverCard.module.css:9-19`；`P/HoverCard.tsx:235-248,272`） | **无**（手机端无 hover；引用/来源走 sheet） | **无** | `[citation:N]` 角标 + hover 300ms 来源卡（demo §2.14）；无移动兜底 | 无 | — | **首版不做**（两家手机参考都无；触摸端 hover 预览不可达）。若 Q-BP-04 判定要支持平板/键鼠，再「仅宽屏保留预览」。见 §4-1 | ⏸ 待拍（Q-BP-04） |
| `base.primitives.modal` | `fixed;inset:0;z:1000`；卡片 `min(380px,100%)`；**遮罩 `onClick` 关**；Esc 仅栈顶、Tab 框内循环、开记焦点→关还焦；`role=dialog`+`aria-modal`；入场 200ms（`P/Modal.tsx:59-68`；`P/useModalLayer.ts:59-69,91-94`；`module.css:29,47-57`） | 统一 `showAppSheet`：标题栏/关闭键/拖拽把手 36×4/风险提示/**`DiscardGuard` 丢弃守卫**/短提示（`app_sheet.dart:45-58`、`sheet_title_bar.dart:60`、`discard_guard.dart`）；返回键关（`PopScope` 2 处）；`RiskNotice` 强制勾选后确认（`risk_notice.dart:251-276`） | `sheet-dialog` 三合一：`AlertDialog`/`RuntimeDialog`/`ModalBottomSheet`；底部抽屉垫 `systemBars ∪ displayCutout` + `imePadding()`（`Dialogs.kt:163-189`）；**下拉/BackHandler/点遮罩三路关**（`Dialogs.kt:128-129`；`PlainBackdropModalBottomSheet` 例外：无遮罩、点外不关，`ChatTopBar.kt:146-151`）；**关闭即丢弃、无确定/取消事务**（字段5） | 遮罩 `onMouseDown`+`target===currentTarget`（防「框内拖选、遮罩上松手误关」）；Esc 栈；**无焦点陷阱/无初始焦点/关闭不还焦**（`B/Modal.tsx:9-10,36-49,54`；§2.30 字段10/11） | `AlertDialog` + 返回键 `onDismissRequest`；表单类关闭丢弃且无二次确认（`MA:1357,2012`） | **C-BP-02**、**C-BP-03** | ①遮罩判据取 demo 的 `onMouseDown`+target（互斥于 DSH 的 `onClick`：后者在「框内按下、遮罩上松开」时会触发，正是 demo 反证里「丢掉刚填的内容」）；②焦点契约取 DSH；③`role=dialog`+`aria-modal`+label 三路一致；④关闭语义取**太墟的三路（下拉/返回/遮罩）+ dsh-Go 的 `DiscardGuard`** | ✅ 采用建议 |
| `base.primitives.dialog-dismiss` | 无独立节点（见 modal） | **有**：`DiscardGuard` 在离开前拦截未保存改动（`discard_guard.dart`，`app_sheet.dart` 字段5） | 关闭即丢弃、**无事务**（字段5）；表单页（模型编辑）例外有自己的保存语义（`settings.models`） | 同 modal（`closable` 未传不显示 X） | **有节点**：`confirmButton`/`dismissButton`+`onDismissRequest`，11 处；关闭不写命令，**但表单类丢草稿**（legacy §2.7；`MA:1357,2012`） | **C-BP-04** | **采纳 dsh-Go 的 `DiscardGuard`**：`dirty` 时离开需确认，`clean` 时直接关（不是无条件确认、也不是无条件丢弃）。互斥点：DSH `SettingsForm` 是「离开即丢弃、无放弃按钮」（`SettingsForm.tsx:52-54`）。反证：无条件丢弃会静默吃掉半改的输入（legacy 已踩 `MA:2012`）；无条件确认则在无改动时多点一次 | ✅ 采用建议（dsh-Go 有 E0 实现） |
| `base.primitives.bottom-sheet` | **无此原语**（`ui-primitives` 21 节点里没有 sheet） | **有**：就是 `base.primitives.sheet`，全 App 唯一弹层入口（`showAppSheet`，`app_sheet.dart:45-58`）；全局把手 36×4、`showDragHandle:true`（`app_theme.dart:386,389`）；进 380/出 260ms（`motion.dart:35-36`）；中宽屏按 `WindowClass` 调宽（`app_sheet.dart:12`） | **有**：`ModalBottomSheet` + `ChatBottomSheet` 薄包装；长内容 `heightIn(max≈窗口高×0.8)`+正文 `weight(1f,fill=false)`、按钮钉底（`UI_GUIDELINES.md:65` E1）；正文 `verticalScroll`；`ToolDetailsSheet.kt:30` 注释明写「进 modal 是为了打开输出不改变聊天列表高度/锚点」 | 无 | **有**：`ModalBottomSheet`（附件 `MA:1291-1315`；M5 模型面板 `MS:49-148`，`onDismiss` 下滑/点外） | **C-BP-03** | **升级为主线采纳**（原 ⏸）：两家手机参考都把 sheet 当**默认弹层**，同时保留 Dialog 做确认 → 正是「确认类 Modal / 选择与详情类 BottomSheet」的两分法。关闭语义：下滑（内滚在顶才触发）+ 点遮罩 + 返回键；**不做内容高度动画** | ✅ 采用建议（原待拍项已由两源印证） |
| `base.primitives.menu` | 行 `padding:6px 8px`+20px ≈32px；紧凑 ≈21px；portal `z:1100`；点外关/Esc+还焦/Tab 结算/方向键；**有子菜单不内滚**（`P/Menu.tsx:326-332,340-404,437,517-547`；`P/Menu.module.css:28-45,133-145`） | **无锚定下拉原语**：消息 `⋯`/长按菜单 = **底部弹层**（`message_menu.dart:89,135,299`，走 `showAppSheet`）；菜单项整行 44+（`chat.msg.user` 字段3） | **无通用 popup menu**：设置行下拉走 `WindowDropdownPreference` 单选弹窗；顶栏「更多」= `PlainBackdropModalBottomSheet`（`ChatTopBar.kt:121-178`） | 菜单项 ≈40、Select 选项 ≥40；**无键盘/无焦点归还**（`B/Select.tsx:48-86`） | 无节点（M5 模型面板是 sheet 内扁平行） | **C-BP-01**、**C-BP-03**（形态） | **手机端菜单 = 底部 sheet 单层列表**（dsh-Go/太墟 2:1 对 DSH 的锚定 popup）：单手可达、行高可到 44~48、无需处理「视口翻转/裁剪」。DSH 的键盘/焦点/子菜单契约**只在宽屏保留**；子菜单不做（§4-2）。反证：锚定 popup 在手机上要么被键盘顶走、要么贴边裁剪（demo 用 `anchored.ts` 视口 clamp 才勉强成立） | ⏸ 待拍（C-BP-03 / Q-BP-03） |
| `base.primitives.toast` | portal 到 body、`left` 注入、`--dsh-toast-hold`；**横幅 `pointer-events:none`，只有动作文字可点**；`role=alert`；成功态对勾（`P/Toast.tsx:78-101`；`P/Toast.module.css:55-79`） | `showBriefNotice` 短提示（`brief_notice.dart:29-31`），挂在 sheet 基座里；复制成功用它（`message_actions.dart:19-28`） | Toast + Snackbar 混用：失败走 Toast（`ChatScreen.kt:300-310`）、权限拒绝走 Snackbar（`:277-298`）、**回滚撤销走 Snackbar**（`:312-325`） | 无同型原语（配额超限走 toast+文档链接） | Material `Snackbar`：「已归档 N 个」+撤销，窗口短（legacy §0-3；`MA:360-376,477-480`） | — | 采纳 DSH 形态（不拦指针 + 仅动作可点 + `role=alert`）+ **统一成一个**：太墟「Toast 与 Snackbar 混用」是它自己的不一致（同一 App 两种底部提示），myco 只用一种；撤销能力放 action（不用 Snackbar 专属模型）。动作命中区由 `hit-target` 抬到 ≥44（DSH 动作命中=文字行高） | ✅ 采用建议 |
| `base.primitives.image-lightbox` | `fixed;z:1000`；关闭钮 36×36；遮罩 `onMouseDown` 关；Esc 关；**Tab 锁唯一关闭钮**；开记焦点→关还焦；`role=dialog`；**无缩放/平移**（`P/ImageLightbox.tsx:37-47,58`；`module.css:2-52`） | **`chat.msg.image` 全屏查看器**：黑底+顶部返回/动作+底部计数；**双击 1×/2× + 双指缩放 + 平移**；**缩放态切 `NeverScrollableScrollPhysics` 禁翻页**（`image_gallery.dart:58`）；长按菜单（保存/分享/复制） | **无灯箱节点**（图片走 A2UI `Image` 组件 / 渲染器 `Preview`） | 无节点（未登记灯箱） | 无 | — | **补上 dsh-Go 的缩放能力**（推翻我上一版的「不加缩放」）：它给出的是 E0 实现 + 一条硬反证——「若缩放态仍允许翻页，双指缩放会误触发翻页」，并已有对应做法（切 `NeverScrollableScrollPhysics`）。Compose 侧等价物待 P4 验证。关闭语义仍取 DSH（点外/Esc/Tab 锁/还焦） | ✅ 采用建议（修订：**要缩放**） |
| `base.primitives.connection-indicator` | 三态；坏态即「重连」按钮；恢复态 `role=status` 自动播报并定时自隐；**内联块参与布局流**（`P/ConnectionIndicator.tsx:55-103`） | **无**（有 header 状态行 `spoken` 附带播报，不是本节点） | **无** | 无 | 无 | — | 仅 DSH 有 → 保留（断线可手动重连是移动网络刚需）；并**明确 G-01 边界**：G-01 只约束 overlay，本节点占位推挤内容是正确的（`dsh-base` §2.7 字段7 原文） | ✅ 采用建议（含文档修正请求） |
| `base.primitives.switch` | 轨道 36×20、滑块 16×16、`translateX` 120ms；**样式键挂 `aria-checked`**；`role=switch`+必填 `label`；无滑动切换（`P/Switch.module.css:5-49`；`P/Switch.tsx:29-37`） | **整行接管命中**：`SwitchTheme.materialTapTargetSize: shrinkWrap` 让整行可点（`list_page_row.dart:214`、`overview_card.dart:155`） | miuix `SwitchPreference`，**整行可点**（`preference-row` 字段3） | Material 默认尺寸 | `Switch`（`MA:1916-2030` 内自绘） | — | 采纳「样式挂 `aria-checked`」（视觉与读屏不可能不一致）+ **整行接管命中**（dsh-Go/太墟 一致）→ 开关本体视觉不变，命中区=行盒。反证：只点 36×20 本体 → 行尾大片空白点不动（`dsh-go.md` `base.primitives.row` 字段13 原话） | ✅ 采用建议 |
| `base.primitives.checkbox` | 原生 `input`+`<label>` 包裹 → 整个 label 是命中区；`accent-color` 用 token（`P/Checkbox.module.css:1-28`；`P/Checkbox.tsx:16-31`） | 无独立节点（未登记） | 「记住选择」复选框（`chat.msg.approval-card` 字段3）；A2UI `CheckBox` 组件（目录 19 类之一） | 无 | 无节点（3 个 Checkbox 在 provider 编辑器里） | — | 采纳「整 label 可点」（命中区做进布局盒，天然达标）；反证：只有 16×16 方框可点 → 手机上精确命中 3mm 方块 | ✅ 采用建议 |
| `base.primitives.input` | `.wrap` 高 32；`InlineEditor` 自动长高；**IME 组合期不提交**、Enter 提交、Shift+Enter 换行、Esc 取消回滚（`P/Input.module.css:1-10`；`P/InlineEditor.tsx:33-69`） | `composer.editor` 玻璃胶囊 `minLines:1,maxLines:6`（`composer.dart:482-483`）；点胶囊空白处聚焦；6 行后内部滚、到边不外传（字段8） | miuix `TextField`（`composer.input`）；`imePadding`；`BasicTextField`（编辑器） | 无同型（输入区归 composer） | `OutlinedTextField`（`CP:387-395`） | — | 基座只定两条**硬契约**：①**IME 组合期不提交**（中文拼音选词按 Enter 不能被当提交）；②**Esc 取消回滚原值**。尺寸/键盘避让归 M4 `composer` | ✅ 采用建议 |
| `base.primitives.segmented-control` | `role=tablist`+`role=tab`+`aria-selected`+漫游 `tabIndex`+`aria-controls`；**方向键走位即选中**；点已选中项不回调（`P/SegmentedControl.tsx:26-54,56-140`） | `SegmentedPill`（`segmented_pill.dart:137,233`） | `RuntimeSegmentedControl`（工具详情页签 `ToolDetailsSheet.kt:67`；顶栏 pill `ChatTopBar.kt:181-195`） | 无 | 无 | — | 采纳全部语义 + 与 `pill` 的分工（segmented = tablist 互斥；pill = 可多选/可切换）。两家手机参考都只把它用于「单选互斥」→ 分工成立。反证：方向键只移焦点不切换 → 要再按一次 Enter，与分段控件直觉不符 | ✅ 采用建议 |
| `base.primitives.tab-strip` | `role=tablist`+`role=tab`+`aria-selected`+**roving `tabIndex`**；ArrowLeft/Right 循环、Home/End、**Enter/Space 才选中（manual activation）**；关闭键 `aria-label`+`aria-keyshortcuts`（`TabPanel.tsx:245,263-266,103-124,293-322`） | 无本节点场景（插件页两 tab 走 `TabBar`，`plugins_screen.dart`；**无右栏/无 dock**） | **明写「没有多标签、没有分屏页签」**（§0-1）；`RuntimeTabs` 只用于局部切换 | 无 | 无（M5 无右栏） | — | myco 若做右栏页签：**语义沿用 DSH（唯一完整 WAI-ARIA 实现）**；几何归 M2。**键盘前提未定**：两家手机参考都没有硬件键盘先例 → 见 Q-BP-05。反证（DSH 注释原话）：只给 `role=tablist` 不给方向键，读屏报「选项卡，共 3 个」而方向键无反应，**比不加 role 更糟**（`TabPanel.tsx:103-107`） | ⏸ 待拍（Q-BP-05） |
| `base.primitives.content-fold` | 头 `ceil(max/2)` 行+按钮+尾若干行；**形态 B（capped-inline）**；按钮 `aria-expanded`+`aria-label` 含隐藏行数；**无高度动画**（`P/ReadBlock.tsx:104-150`；`P/TerminalBlock.tsx:269-277`） | **「行不封顶、井封顶、全文另起页」**：代码块限高内滚（`code_block.dart:577-583`）+ 详情井 `_kDetailMaxHeight=280`（`tool_row.dart:32,465`）+ 长文本头 60/尾 140 行、上限 16000 字 + `minHeight:44` 的「查看全部」进全文页（`LT:14-19,202-210,261-307`） | 工具输出**进 sheet 不原地展开**（`ToolDetailsSheet.kt:30` 注释原话）；过程链分页 +8（`ToolStepsCard.kt:30-38`） | 无 | 无 | **C-14** | 采纳形态 B + 三家的共同结构：**块内不展开超长内容**，改「限高内滚 + 全文页/sheet」。dsh-Go 的 280dp 井上限与太墟的「一律进 sheet」是同一意图的两种落地；myco 取「限高内滚 + 查看全部」。动效按 Q-BP-02。「折叠 vs 内滚」优先级见 Q-BP-08 | ✅ 采用建议 |
| `base.primitives.json-tree` | 展开器 `role=button`+`aria-expanded`+`aria-controls`、子树 `role=group`、漫游 `tabIndex`；右键=复制方式菜单；`copy` 有 `failed` 态（`P/JsonTree.tsx:117-165,302-312,595-628,663-664`） | **无** | **无**（JSON 走 `prettyPrint` 文本渲染，`ToolDetailsSheet.kt:111`） | 无 | 无 | — | 本面板不建该原语（§4-5）：两家手机参考都把 JSON 当文本渲染（太墟 `prettyPrint` 后进详情），触摸端它的核心交互（右键菜单、漫游 tabIndex）都不可达 | ✅ 采用建议（不做） |
| `base.primitives.markdown-link` | 外链 `<a>`、本地文件 `<button>`、chip 有 `aria-label`；无修饰键点击才接管；`user-text` chip 双击/有选区不跳转；**流式期间本地链接不解析（不可点）**（`P/markdown/render.tsx:550-619`；`P/user-text.tsx:127-150`） | 正文链接热区由**自绘 `RenderProxyBox` 补到 48、视觉不变**：`_RenderLinkHitArea.hitTest()` 把链接所在行上下扩到 `kMinInteractiveDimension`，命中后转发到文字锚点，注释要求祖先放行（`relay_credit.dart:135-139,174-259`）；无修饰键分支 | 无（未登记链接原语） | 正文链接 `withLink` → 系统浏览器（`CC:402-416`） | 有（`withLink`，legacy §2.8） | — | 采纳：外链→系统浏览器、本地文件/提及→按钮语义、**流式期间不可点**、**有选区不跳转**；修饰键分支不做（§4-4）。**链接热区补 48 的手法**见 §2.1 路径 b（Compose 等价物未取证 → Q-BP-06） | ✅ 采用建议（补边手法待验） |
| `base.primitives.settings-field` | label+控件纵排；`htmlFor`；help 按钮 12px+`aria-expanded`+`aria-controls`+`role=region`；`invalid`→`aria-invalid` 且错误文案替换 hint；重置仅 `overridden` 时出现；密码只 write-only（`P/settings-form/fields.tsx:64-115,125-151`；`P/ConfigField.tsx:33-53`） | `base.primitives.row`：`ListPageRow` **整行可点**（`list_page_row.dart:145,214`）；开关行 `shrinkWrap` 整行接管；插件配置页另有保存条（`plugin_config_save_bar.dart:147-156`） | `base.primitives.preference-row`：miuix 偏好行，**整行可点**；分组用 `PreferenceSurface(first,last)` 拼卡（首末圆角、中间直角，`PersonalizationComponents.kt:30-49`）；**即时落盘 / 松手落盘，无保存按钮**（字段5） | 无同型 | 12 输入框+3 Checkbox（`MA:1916-2030`），无 help/重置/校验结构 | — | 采纳 DSH 结构 + 两家「整行可点」；**帮助区展开不做高度动画**（§2.3）。保存语义不在这里拍（C-15 归 M6） | ✅ 采用建议（语义归 M6） |
| `base.primitives.form-save` | 显式保存唯一写入点：`dirty && !invalid && !saving`；**无「放弃」按钮**；失败保留草稿+`role=status`；**卸载即丢草稿**（`P/settings-form/SettingsForm.tsx:1-74`） | 有保存条实例（`plugins/config/plugin_config_save_bar.dart:147-156`；本路 13 字段未展开） | **无保存按钮**：开关/单选/语言即时落盘、滑杆松手落盘；**无脏态提示**（`preference-row` 字段5）；模型编辑表单例外 | 无同型（即时写 + 显式 Apply） | 有（`settings.provider-editor.save`，legacy §2.5） | — | 采纳 DSH 状态机；**给 M6 的输入**：两家手机参考的默认都是「即时落盘」，显式保存只出现在配置类表单页（dsh-Go 保存条）→ C-15 的「按页声明」有了两个手机端样本 | ✅ 采用建议（语义归 M6） |
| `base.primitives.hit-target` | **无**：全库无扩边（`dsh-base.md` §0-1、§0-10）；mobile-adapt 用 CSS 伪元素（`M/:934-959`），Compose 无伪元素 | **三种真实手法（§5.3）**：a 整行承载（`session_tile.dart:88-118`、`stream_row.dart:46-68`）；b 自绘 `RenderProxyBox` 扩 hitTest、**视觉完全不变**（`relay_credit.dart:135-139,174-259`）；c 横向 `Bleed`（`bleed.dart:6-13,40-47,87-96`）；全仓 `shrinkWrap` **48 处** vs `padded` **4 处**（§5.2-3） | **三套并存**：40 图标钮（`ComposerSendButton.kt:20-24`，注释明确不加 48）/ 显式 48 助手 `fun Modifier.minimumInteractiveComponentSize() = sizeIn(48,48)` 全仓 38 处（`Buttons.kt:171`）/ 行内次级 12–18（`ChatMessageBubbles.kt:609,631,644`）——**但字段7 明写该助手参与布局测量、会撑高行，所以只能选择性用** | 有 44px token 但无统一扩边层（`APP/theme.css:9-13`） | 无（整行 `.clickable`，没有独立扩边层） | **C-03** | **拆成两个能力**：①`hit-target.box`＝布局盒扩边（dsh-Go 手法 a/c，Compose 可直接做）；②`hit-target.inline`＝文字片段扩边（dsh-Go 手法 b，Compose 等价物 **未取证**，必须 P4 验证）。详见 §2.1 | ⏸ 待拍（Q-BP-01 / Q-BP-06） |

---

### 2.1 附：命中区策略（C-03 / Q-03 的完整答案，v2 按三源重写）

**先说结论：「单一 48dp 底线」这个框架本身被手机端参考否掉了。**

- **dsh-Go 的实测口径**（`dsh-go.md` §5.2 结论 1 原话）：**行级 44**（会话行/设置行/回底钮）、**过程行 32（整行可点 + 横向扩 8）**、**密集图标 36**（回复操作条/输入键）；**48 只出现在「正文里的链接、输入类控件、主操作按钮、插件控件」**；全仓 `MaterialTapTargetSize.shrinkWrap` **48 处** vs `padded` **4 处** → **密集列表主动放弃 48 的平台默认**。
- **太墟的口径**（`taixu.md` §0-10①、Q-TX-02）：图标钮 **视觉 40dp = 命中 40dp**（注释明确「不再叠 Material 的 48dp minimumInteractive，避免实心底被撑成 FAB」，`ComposerSendButton.kt:20-24`）、过程行 **28dp**、行内次级 **12–18dp**；另备一个 `sizeIn(48,48)` 助手按处使用（38 处）——**但它自己指出该助手参与布局测量、会撑高行**（`action` 字段7）。
- **DSH Web** 是 24/16 且无扩边；**旧 myco** 的 Material 默认 48 只是「用了默认组件」，不是设计决策。

#### 2.1.1 按类别的命中口径（myco 建议）

| 类别 | DSH Web | dsh-Go | 太墟 | **myco 建议** | 代价 |
|---|---|---|---|---|---|
| 行/整块（会话行、设置行、列表行、回底钮） | 24–32 无扩边 | **44** | 40（图标钮）/ 整行接管（偏好行） | **44** + 整行可点 | 行高 32→44（一屏少 ~3 行@400dp） |
| 过程行 / 工具行（密集、连续阅读） | 24 | **32** + 整行 + `Bleed` 横向 8 | **28** + 整行 | **32** + 整行 + 横向 8 | 行节距 30→约 36–38dp（约 **−17%~−21%**，算术；dsh-Go 的行间距由外层 `Padding` 给，ref 未给具体值） |
| 密集图标动作（并排 ≥2） | 24/28 无扩边 | **36×36**（净距 0） | **40×40** | **40×40** | 一行 5 键：5×40=200dp（40 比 36 宽 20dp） |
| 行内次级图标（复制/翻译/chevron） | 16 | 20（工具行图标） | **12–18** | **≥36，且必须与父行点击区分** | 太墟自己的验收 `TX-ACTION-03` 就要求「点 18dp 复制不折叠思考」→ 说明 <36 是已知风险 |
| 正文内链接/文字片段 | 文字高 | **48（视觉不变）** | 无 | **≥44**，手法见 2.1.2 路径 b | 需知道段落几何（dsh-Go 自绘层） |
| 输入控件 / 主操作 | 28–36 | 48–64 | 48 token / 50 主按钮 | **48** | 无 |

> **硬规则（建议进 G 规则）**：①任何**独立**可点原子命中盒 ≥ **36dp**，禁止 <36；②行内次级动作若 <36dp，**必须**与父行的点击语义可区分（否则点复制会触发折叠）；③相邻命中盒**不得相交**；④「48dp」只用于链接、输入类、主操作——**不做全局 48 底线**（这是 dsh-Go §5.2 与太墟注释共同的反证）。

#### 2.1.2 扩边落在哪一层：三条真实路径（dsh-Go §5.3 全部 E0）

| 路径 | dsh-Go 的实现（E0） | Compose 等价物 | 适用 |
|---|---|---|---|
| **a. 整行承载**（首选） | 行 `InkWell`/`GestureDetector` 包住整行、行高给到 44/32（`session_tile.dart:88-118`、`stream_row.dart:46-68`）；`SwitchTheme.materialTapTargetSize: shrinkWrap` 让开关行整行接管（`list_page_row.dart:214`） | **直接可做**：`Box(Modifier.fillMaxWidth().heightIn(min=44.dp).clickable{})`，视觉内容居中 | 独占一行的行/块（设置行、会话行、过程行、开关行） |
| **b. 自绘命中层补 hitTest**（视觉完全不变） | `_RenderLinkHitArea.hitTest()` 把链接所在行上下扩到 `kMinInteractiveDimension`(48)，命中后把点击**转发到文字锚点**，注释要求祖先放行（`relay_credit.dart:135-139,174-259`） | **未取证**：Compose 无 `RenderProxyBox`。可能做法（都需 P4 验证）：拆成独立可点节点并给垂直 padding / 自定义 `PointerInputModifierNode`。**不得在 spec 里假定可行** | 正文内文字片段（链接、文件 chip）——这是「唯一」能让视觉不变又补到 48 的手法 |
| **c. 横向 Bleed**（只扩宽不扩高） | `Bleed` 让子组件按「列宽 + 2×8」布局、画在 −8 处，`_RenderBleed.hitTest` 把 `[-bleed, width+bleed)` 都算命中（`bleed.dart:6-13,40-47,87-96`） | 可直接做：可点父盒加 8dp 水平 padding，视觉内容用负 offset 贴回正文缘 | 首尾贴正文缘的行（过程行、操作条）——**它不解决竖向 32→44** |
| **d. 布局盒直接抬到目标尺寸** | —— | 太墟的 `sizeIn(48,48)` 助手（38 处） | 只对「能接受行高变大」的原子；太墟自己指出它会撑高行（`action` 字段7），所以**选择性使用** |

> **对上一版结论的修正**：我上一版写「扩边必须落在布局盒，不画在 `Modifier`」——**路径 b 证明存在「绘制/命中层扩边」的实现**（dsh-Go 的 `RenderProxyBox`）。正确表述是：**Compose 里能确定的只有路径 a/c/d（布局盒）；路径 b 需要自绘命中层，Compose 等价物未取证 → 进 Q-BP-06 验证清单**。

#### 2.1.3 几何判定式（保留，仅用于「必须扩边时」）

```
h = 视觉盒高，T = 该类别目标命中高，e = 单侧扩边量，g = 相邻视觉盒净距
约束1（达标）   h + 2e ≥ T
约束2（不相交） 2e ≤ g
```

- 过程行 `h=32`、目标 44：`e=6` → 要求 `g≥12`。dsh-Go 实测行内上下各 4、行间由外层 `Padding` 给（`dsh-go.md` §5.4）→ 行距 8 < 12 → **dsh-Go 的做法是「不竖向扩，改横向整行」**（正是路径 a/c）。
- 若沿用「竖向也要 44」：`g≥12` 才能扩，过程行节距要从 38 抬到 44+ → 密度再降。
- **隔离带**：**不设固定 8dp 规则**（上一版是我从 C-03 倾向推的、无 E0）。dsh-Go 实测：回复操作条 **36 键相邻净距 0**，靠「图标 20 + 每键 8 内边距」形成视觉间距（`message_actions.dart:96-105`）；会话行上下净距 0、左右 10；过程行上下各 4 + `Bleed` 8；输入条主键 36 + 胶囊上下 6/左右 8（§5.4）。→ **规则改为「命中盒不得相交 + 视觉间距由内边距给出」**，只在两个**都小于 36dp** 的小目标并排时额外要求 ≥8dp 净距。

---

### 2.2 附：ARIA → Compose 语义映射（本面板全部原子的无障碍基线）

> 原则：**Compose 没有 `aria-expanded` / `role=dialog` / roving `tabIndex` 的一一对应物**。下表给「语义等价物」，级别 **E1（平台 API 语义），必须 P4 用 TalkBack 验收**。
> **太墟的实测反例**：`chat.msg.thinking` 头部 `clickable` **没有** `role`/`stateDescription`（字段11 明写未见）——这正是 myco 不能照抄的缺口。

| DSH 契约（E0 出处） | Compose 落法（`semantics{}`） | 验收 |
|---|---|---|
| `role=button` + `aria-expanded`（`P/DisclosureRow.tsx:93-95`、`:104`） | `Modifier.semantics { role = Role.Button; stateDescription = if (open) "已展开" else "已折叠"; onClick(label = if (open) "收起" else "展开") { toggle(); true } }` | TalkBack 报「可展开/已展开」 |
| `role=dialog` + `aria-modal` + `aria-label`（`P/Modal.tsx:60-68`、`P/ImageLightbox.tsx:51-56`） | **用 `Dialog`/`ModalBottomSheet` 承载浮层**（独立 window，TalkBack 焦点天然被限制在浮层内）→ 再补 `Modifier.semantics { paneTitle = title }`；**同 composition 内自绘遮罩不会产生焦点隔离** | 开浮层后 TalkBack 焦点不进背后页面 |
| 焦点陷阱 + 回焦（`P/useModalLayer.ts:46-96`；`P/ImageLightbox.tsx:37-47`） | 打开前记 `FocusRequester`；关闭后 `prev.requestFocus()`。**`Dialog` 只保证陷阱，不保证回焦**，回焦必须自己写 | 关闭后焦点回到触发按钮 |
| `role=switch` + `aria-checked`（`P/Switch.tsx:29-37`） | `Modifier.toggleable(value, role = Role.Switch)` + **样式由 `value` 派生**（照抄 DSH「样式挂状态键」，`P/Switch.module.css:1-3`） | 视觉与读屏态不可能不一致 |
| `role=menu/menuitem`（`P/Menu.tsx:522,441-458`） | 手机端菜单=底部 sheet（C-BP-03）→ `ModalBottomSheet` + 每行 `Modifier.clickable` + `paneTitle`；宽屏若做锚定 popup 再用 `Popup(focusable=true)` | 菜单项可逐项读出 |
| `role=tablist/tab` + `aria-selected` + roving `tabIndex`（`TabPanel.tsx:245,263-266`） | `Modifier.selectable(selected, role = Role.Tab)` + 外层 `Modifier.selectableGroup()`；**roving tabIndex 无等价物** → 方向键需 `onKeyEvent` 自实现（Q-BP-05） | 只给 role 不给键位 = DSH 注释里的「比不加更糟」 |
| `role=alert`（`P/Toast.tsx:78`） | `Modifier.semantics { liveRegion = LiveRegionMode.Assertive }` 或 `clearAndSetSemantics { contentDescription = text }` | toast 出现即播报 |
| `role=status`（`P/ConnectionIndicator.tsx:63-65`） | `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` | 状态变化播报 |
| 视觉隐藏文案（G-08；`ReasoningRow.tsx:84` 的 `visuallyHidden` span） | Compose 无 `visuallyHidden`：用**零视觉占位 + 语义节点**——`Box(Modifier.size(1.dp).clearAndSetSemantics { liveRegion = …; contentDescription = "正在思考" })`，或挂到可见容器的 `stateDescription` | TalkBack 能听到运行态 |
| `aria-label`（所有图标按钮） | `Modifier.semantics { contentDescription = label }`；**必填**（dsh-Go 用 `Tooltip(label)`、太墟用 `contentDescription` 且有遗漏 → 两家都证明它会漏） | 每个图标按钮有可达名 |
| DSH 分支按钮不用原生 `disabled`（保 Tooltip，`C/MessageIconActions.tsx:93`） | **可简化**：Android 无 hover tooltip → `enabled=false` + `stateDescription` 说明原因 | 不可用项不被误点，且读屏能听出原因 |
| `aria-pressed`（Pill/换行） | `Modifier.toggleable(value, role = Role.Checkbox)` 或 `selectable` | 选中态可读 |
| `aria-controls` | 只在确有可跳转目标时关联（DSH 的 `DisclosureRow` 本身也没有，`P/DisclosureRow.tsx:86-123`；`JsonTree` 有） | 不做为缺口登记（与 DSH 对齐） |

---

### 2.3 附：动画落地（G-10 在各原子上的取值）

> **G-10 是上界不是下限**——「≤240dp 允许动画」≠「必须动画」。DSH 基座**全部**可展开组件都不做高度动画（`dsh-base.md` §4 Q-BASE-14 事实段）；**太墟相反**：思考块对长内容也做 `AnimatedVisibility(expandVertically)`（`ChatMessageBubbles.kt:649-653`，`taixu.md` Q-TX-01）；**dsh-Go 第三套**：思考行展开 160ms（`thinking_pill.dart:223`）、流式中**不做**高度动画（`:220-223`）、整轮过程展开 280ms + 逐帧滚动补偿（`turn_process.dart:129-151`）。
> myco：**只在 `disclosure.row` 的 ≤240dp 分支用 G-10 的动画档，其余一律直接切换。**

| 原子 | 展开后内容高度 | 动作 | 依据 |
|---|---|---|---|
| `disclosure.row` | 通常 ≫240dp（思考正文） | **直接切换**（可选 ≤120ms 淡入） | G-10 上档；DSH 同向；dsh-Go 流式中也明确不做高度动画（`thinking_pill.dart:220-223`） |
| `disclosure.row` | 实测 ≤240dp | 允许纵向 expand（M5 `settle`），仅纵向 | G-10 下档（Q-BP-02 待确认是否保留） |
| `disclosure.fold-toggle`（`<details>`） | 常 >240dp | 直接切换 | dsh-Go 对 `<details>` 用 `SmoothSize` 240ms（`markdown_blocks.dart:702,717-721`）→ 属长内容动画，**不跟**（G-10） |
| `content-fold` | 头尾截断型/限高井 | **直接切换** | 折叠常伴随外层滚动位置变化，动画会加剧错位 |
| `settings-field` 帮助区 | 短 | **不做动画** | G-10 允许≠必须；DSH 也无（`fields.tsx:110-112`） |
| `modal` / `bottom-sheet` | — | 只允许 opacity 淡入（modal ≤200ms）；sheet 只做「升起/落下」位移 | DSH E0（`P/Modal.module.css:29,47-57`）；dsh-Go sheet 进 380/出 260ms（`motion.dart:35-36`）、太墟 miuix sheet 入场 |
| `toast` | — | 只允许进/出位移或淡入，**不做高度动画** | `P/Toast.module.css:76-79` |
| `menu` / `tooltip` / `hover-card` | — | 只允许 opacity，reduced-motion 关闭 | `P/HoverCard.module.css:66-77` |
| `switch` | — | 只允许滑块 `transform` 120ms；**不做轨道高度动画** | DSH E0（`P/Switch.module.css:40`） |
| `pill` / `segmented-control` / `tab-strip` | — | 只允许颜色/指示器位移；**不做尺寸动画** | DSH 无过渡声明 |
| 全局 | — | 系统「关闭动画」时必须能关掉以上全部过渡 | DSH 五处 `prefers-reduced-motion`；dsh-Go `reduced` 档不动画（`pressable.dart:97`） |

**不该有动画的清单**：① 任何浮层的**高度/尺寸**动画；② 表单帮助区展开；③ `content-fold`；④ 页签/chip 出现消失；⑤ 开关轨道高度；⑥「复制成功」图标切换（DSH 瞬换，`P/CodeToolbar.tsx:55-57`；dsh-Go 是 160ms `AnimatedSwitcher`（`message_actions.dart:106-114`）→ 取 DSH 瞬换，理由见 G-10 反证：动画期间不可读）。

---

## 3. 补充项（不互斥的差异，不进对照表的冲突列）

| # | 节点 ID | 谁有、谁没有 | 是否采纳 |
|---|---|---|---|
| 3-1 | `disclosure.row` 的形态 | dsh-Go：整行 + 32；太墟：整行 + 28；DSH：整行或图标**由调用方决定**（`P/DisclosureRow.tsx:50`）；demo/legacy：只有整行 | 采纳「只做整行」（见 §4-7）；**视觉高 32 vs 28 不互斥**（都是整行），取 32（dsh-Go 有完整解剖：24 图标 + 上下各 4） |
| 3-2 | `tooltip` | DSH 指针模态下抑制 focus 提示（`P/Tooltip.tsx:244-248`）；demo `isMobile` 硬关（`B/Tooltip.tsx:24,43`）；dsh-Go 挂 Flutter `Tooltip(label)`（ref 只取证「读屏可见」）；太墟只有 `contentDescription`（有遗漏） | 采纳「触摸端不出 hover 提示 + 必须以 `contentDescription` 给可达名」；不建 tooltip 原语（§4-3） |
| 3-3 | `toast` 形态 | DSH 单行横幅（`role=alert`、不拦指针）；dsh-Go `showBriefNotice`；太墟 **Toast + Snackbar 混用**（失败走 Toast、撤销走 Snackbar）；legacy Material `Snackbar` | 采纳 DSH 形态并**只保留一种**；撤销用 action 承载。太墟的混用是它自己的不一致，不照抄 |
| 3-4 | `image-lightbox` 缩放 | DSH 明写**无**缩放/平移；dsh-Go 有双击 1×/2× + 双指缩放 + 平移，且**缩放态禁翻页**（`image_gallery.dart:58`）；太墟无灯箱 | **采纳 dsh-Go**（能力差异非互斥；且它有明确反证）。Compose 手势等价物待 P4 |
| 3-5 | `menu` 的宽屏形态 | DSH 锚定 popup（完整键盘/焦点契约）；dsh-Go/太墟 手机端不用 | 手机端走 sheet（C-BP-03）；宽屏保留 popup。子菜单不做（§4-2） |
| 3-6 | `menu` 紧凑行高 | DSH 有紧凑模式 ≈21px（`P/Menu.module.css:133-145`）；两家手机参考无 | 紧凑档只留给宽屏/鼠标；触摸端 44~48 |
| 3-7 | `pill` vs `segmented-control` | DSH 两者都有；dsh-Go 有 `SegmentedPill` + `StatusChip`；太墟有 `RuntimeFilterChip` + `RuntimeSegmentedControl` | 采纳按用途分工：segmented=tablist 互斥；pill=可多选/可切换 |
| 3-8 | `connection-indicator` | 只有 DSH 有，且**内联块参与布局流**（`dsh-base` §2.7 字段7） | 采纳；⚠ 需澄清 G-01 边界（见 §6 文档修正） |
| 3-9 | `bottom-sheet` 的底部安全区 | dsh-Go 全局把手 36×4 + `showDragHandle`（`app_theme.dart:386,389`）；太墟统一垫 `systemBars ∪ displayCutout` + `imePadding()`，且注释明令**不要在 sheet 内再垫一次**（`Dialogs.kt:163-189`） | 采纳太墟的「只垫一次」契约（否则双倍留白） |
| 3-10 | `content-fold` 的超长处理 | dsh-Go「限高内滚 + 头 60/尾 140 行 + 查看全部进全文页」（`LT:14-19,202-210`）；太墟「一律进 sheet」（`ToolDetailsSheet.kt:30`） | 采纳 dsh-Go 的三段式（在聊天流内可继续读，不必跳层）；太墟的「一律 sheet」作为备选 |
| 3-11 | 复制成功时长 | DSH 1000ms；dsh-Go **1.4s**（`message_actions.dart:83-89`）；demo 1000/1500 两套 | 采纳 1000ms（DSH + demo 主流）；不取 1.4s |
| 3-12 | 图标动作不可用态 | DSH `aria-disabled` + 保留 hover/focus（为 Tooltip）；dsh-Go `onPressed:null` + 透明 38%；太墟 `enabled=false` 由 miuix 吞点击 | 采纳原生 disabled（Android 无 hover tooltip，`open-questions.md:108-109`） |
| 3-13 | 按压反馈 | DSH 无（Web 无 press 态）；dsh-Go 有完整 `base.primitives.pressable`（缩放 0.97 / 弹簧 90ms / 触感 / **滚动中不闪**：按下 90ms 才显示压感，`pressable.dart:101-108`）；太墟 miuix `folmeSpring` | **采纳 dsh-Go 的 pressable 作为 myco 按压基座**（D 形态：`scale 0.97` + 触感 + 滚动中延迟显形）；这是上一版遗漏的一个原语能力（不单列节点，挂在 `button`/`icon-action`/`disclosure.row` 的实现契约里） |

---

## 4. 明确不做（参考里有的）

| # | 能力 | 出处 | 不做的理由 |
|---|---|---|---|
| 4-1 | `hover-card` 的 `copyable` 整卡复制 | `P/HoverCard.tsx:235-248`（`role=button`+检查文本选区 `:240-247`） | 触摸端无 hover 卡片（dsh-Go/太墟 都没有此原语）；真在预览卡里复制会与「长按选字」原生手势打架，DSH 自己也只能靠「有选区则不复制」规避 |
| 4-2 | `menu` 的子菜单 | `P/Menu.tsx:437,453-511,517-520` | 触摸端无 hover，「点击=选中」与「点击=展开子菜单」互斥；dsh-Go/太墟 的菜单都是**单层**（dsh-Go 用底部 sheet，太墟用单选弹窗）→ 需要层级时用 sheet 二次选择 |
| 4-3 | `tooltip` 的 hover 形态与 `openOnClick` 钉住模式 | `P/Tooltip.tsx:54-55,189-204,244-248` | 触摸端无 hover 事件源（四路一致：DSH 指针模态抑制、demo 硬关、dsh-Go 只当可达名、太墟无）。**登记为已知缺口**：视觉用户在图标含义不明时无提示 → 若 P4 发现关键图标不易识别，改为「长按出提示」（dsh-Go 的 Flutter `Tooltip` 可能天然如此，**长按显形未取证**） |
| 4-4 | `markdown-link` 的「按修饰键保留浏览器行为」 | `P/markdown/render.tsx:560-564` | Android 无修饰键点击；dsh-Go 也没有该分支（全部点击交宿主） |
| 4-5 | `json-tree` 整棵原语（漫游 `tabIndex`、右键「复制方式」菜单、`aria-controls` 子树） | `P/JsonTree.tsx:117-165,380,595-628,663-664` | 触摸端两条核心交互不可达（无右键、无硬件键盘）；两家手机参考都把 JSON 当文本（太墟 `prettyPrint` 进详情，`ToolDetailsSheet.kt:111`）。首版用 `content-fold` + 文本 |
| 4-6 | `disclosure.row` 的 `previewChevron` 悬停换图标（原图标淡出、chevron 淡入） | `P/DisclosureRow.module.css:74-98` | 触摸端无 hover 可触发；改**常显 chevron**（方向随状态变），顺带消掉一处 100ms 过渡 |
| 4-7 | `disclosure.leading`（只有 16×16 图标可点）这一形态 | `P/DisclosureRow.module.css:50-63`、`P/DisclosureRow.tsx:99-108` | **dsh-Go / 太墟 都只有整行形态**；首版无「行内带 accessory 的披露行」场景；16×16 在触摸端无法达标（dsh-Go 反证：可点面积只剩 20~30dp 时功能=「找不到」）→ 收窄为单形态 |

---

## 5. 新增节点（参考里都没有、myco 要新设计）

> 本版修订：`bottom-sheet` **移出**本节——dsh-Go `base.primitives.sheet` 与太墟 `base.primitives.sheet-dialog` 都是基座级弹层原语，它已有两路 E0 依据（已升进 §2 并 ✅）。

| 节点 ID | 为什么需要 | 依据（用户要求 / 平台规范 / 我方约束） |
|---|---|---|
| `base.primitives.hit-target` | 命中区策略需要一个**统一的、可验收的落点**；否则 HIT 规则会散落在每个面板每一行，各写一套 | **手法有 E0**（dsh-Go §5.3 三手法、太墟 `sizeIn(48,48)` 助手），**但「原语化」无先例**。**我方约束**：Compose 无 CSS 伪元素，能确定的扩边只有路径 a/c/d（§2.1.2）；路径 b（自绘命中层）Compose 等价物未取证 |
| `base.primitives.icon-action-bar` | 动作条显隐策略（C-04）目前只存在于 chat 面板的私有实现里；myco 的消息操作条、代码块动作条、工具卡动作条要共用一套「常驻 / 只给轮尾」规则 | **行为有 E0**（dsh-Go `chat.msg.actions`、太墟 `assistantToolbarIds`、DSH 触摸端常驻），**抽象无先例**。**我方约束**：Android 无 `@media(hover:hover)`，策略必须集中，否则每处 `if (isLast)` 复制一遍 |

---

## 6. 待确认（阻塞项）

> 编号 `Q-BP-nn`（BP = base.primitives）。**`Q-BP-01` 未拍板前，不得实现任何矮行**（延续 `open-questions.md:31`）。
> **`Q-BP-07`（原「dsh-Go / 太墟 事实库不存在」）已撤销**：两路已于 13:18 落库并回填本表，前提失效；**编号不复用**（保留占位以免与其它面板的引用串号）。

| ID | 问题 | 两解及各自后果 | 建议 |
|---|---|---|---|
| **Q-BP-01** | **命中区口径**：我上一版提的「统一 48dp 底线」已被 dsh-Go §5.2 与太墟 `ComposerSendButton.kt:20-24` 注释**从两个方向反证** | **解 A（统一 48dp）**：可验收、无障碍最稳；但两家手机参考都明确反对——dsh-Go 全仓 48 只用在链接/输入/主操作，太墟注释说 48 会把工具条撑高 8dp、实心底变 FAB。<br>**解 B（学 dsh-Go 分级）**：行 44 / 过程行 32+整行+横向 8 / 密集图标 36 / 48 只给链接·输入·主操作。有完整 §5.1 尺寸频次表 + §5.3 三手法 + §5.4 隔离实测，是**最完整的手机端答案**。<br>**解 C（学太墟）**：40 图标 + 48 助手按处 + 允许 12–18 次级。最省字，但太墟自己承认小目标是风险（`TX-ACTION-03`）且没有命中率数据 | **解 B + 一条 myco 硬规则**：独立可点原子 ≥36dp；行内次级 <36 必须与父行点击可区分；相邻命中盒不得相交；48 只给链接/输入/主操作。按类别表见 §2.1.1。**这是本面板第一优先** |
| **Q-BP-02** | **动画档位**：G-10 已拍板「≤240dp 动画、>240dp 直接切」，但 DSH 基座全无高度动画、**太墟全部做高度动画**（含长内容，Q-TX-01）、dsh-Go 两套（思考 160ms / 流式不做 / 过程 280ms） | **解 A（照 DSH 全无动画）**：与 DSH/旧 myco 现状之一对齐、性能最稳；但**推翻已拍板的 G-10 下档**（Q-04）。<br>**解 B（按 G-10 分档）**：保留短内容手感；240dp 是 E1 工程值，无代码可对账。<br>**解 C（照太墟统一动画）**：与 G-10 直接冲突，且 G-10 的反证（动画期间不可读、逐帧测量掉帧）正是太墟 Q-TX-01 自己承认的疑点 | **解 B**（G-10 是 user 已拍板，解 A/C 都属越权）+ 把「G-10 是上界不是下限」写进规则：只有 `disclosure.row` 的 ≤240dp 分支用动画 |
| **Q-BP-03** | **弹层家族分工**（含菜单形态）：两家手机参考把 sheet 当默认弹层、Dialog 只做确认；DSH 是居中 Modal + 锚定 popup Menu | **解 A（全用 Modal/锚定 popup，照 DSH）**：DSH 可对账；但手机单手关不掉、popup 会被键盘/视口挤。<br>**解 B（确认类 Dialog / 选择与详情类 BottomSheet / 菜单=底部 sheet 单层列表）**：dsh-Go/太墟 都这么用（`app_sheet.dart:45-58`；`Dialogs.kt:163-189`）；代价是两套关闭语义要各自写死并验收。<br>**解 C（全 sheet）**：统一；但破坏性确认从底部升起降低「慎重」感 | **解 B**（两源印证）。关闭语义矩阵：Dialog=点外(遮罩 `onMouseDown` 判据)+Esc/返回键+关闭键；BottomSheet=下滑(内滚在顶才触发)+点外+返回键；菜单=底部 sheet 单层（宽屏才用锚定 popup）；Toast=自动过期；Lightbox=点外+Esc，Tab 锁唯一按钮。附带待验：①灯箱双指缩放的 Compose 实现；②Snackbar 是否拦指针（未取证，不参与决策） |
| **Q-BP-04** | **myco 是否只有触摸形态**：决定 hover 支线（tooltip/hover-card/桌面紧凑菜单行/动作条 hover 显隐）是否存在，以及 Q-BP-05 是否必须做 | **解 A（只有触摸）**：可砍掉整条 hover 支线（§4-1/4-3 变「永久不做」），动作条无条件常驻，实现面最小。两家手机参考（dsh-Go/太墟）都无 hover-card、无 hover 提示 → 与解 A 一致。<br>**解 B（含平板+外接键鼠）**：需要 hover 与键盘两条支线，`hit-target` 的路径 b 也要一并做 | **解 A**（myco 现状是单 Activity Android App；dsh-Go 虽有 ≥840dp 三栏但仍是触摸，太墟明写无多标签/无硬件键盘先例）。若解 B，§4-1/4-3 要重开、Q-BP-05 变必须 |
| **Q-BP-05** | **页签键盘模式**：DSH 是 manual activation（`TabPanel.tsx:103-124`）；Compose 无 roving tabIndex；**两家手机参考都没有键位先例** | **解 A（只给 `Role.Tab`+`selected`，不做方向键）**：触摸够用；但违反 DSH 注释的反证（「只给 role 不给方向键比不加更糟」）。<br>**解 B（`onKeyEvent` 自实现）**：达标；只对外接键盘有意义 | 取决于 Q-BP-04：解 A 则**同时不给 `Role.Tab`**（改 `Role.Button`+`selected`，不承诺键盘能力）；解 B 则必须实现键位。**不允许「给了 tablist 语义却没有键位」** |
| **Q-BP-06** | **Compose 语义/命中手法的正确性无人验证**（§2.2 全为 E1）。四条风险：①`Dialog` 关闭后焦点能否回到 `FocusRequester`；②视觉隐藏播报能否被 TalkBack 播报；③`stateDescription` 是否被读成「已展开」；④**dsh-Go 的「自绘命中层扩 hitTest」在 Compose 有无等价物**（决定 inline 链接能否视觉不变补到 44/48） | **解 A（先写 spec，P4 验证再回填）**：不阻塞 P3；若任一不成立，对应字段要重写。<br>**解 B（P3 前做 4 个最小原型）**：一次 ~30 分钟真机实验消除风险 | **解 B**（成本最低、风险最大）。④ 尤其关键：若不成立，`markdown-link` 的命中方案必须改成「抬行高/拆节点」，P3 要改 |
| **Q-BP-08** | **`content-fold` 的「折叠 vs 内滚」优先级**（Q-BASE-08）：DSH 没定义；dsh-Go 的答案是「井封顶 280dp 内滚 + 全文另起页」；太墟是「一律进 sheet 不原地展开」 | **解 A（折叠优先）**：G-04 不适用；超长块仍很高。<br>**解 B（内滚优先）**：块高可控；需实现 G-04 到边吸收。<br>**解 C（按块类型二选一）**：最贴近现状；规则碎片 | **解 B**（G-04 是铁律，解 A 等于让内容块永久绕过它）；采纳 dsh-Go 的 280dp 井 + 「查看全部」出口。每类块在 P3 逐块声明到边行为 |
| **Q-BP-09** | **G-03「展开三形态禁止混用」是否允许「同一节点按状态切形态」**：太墟思考块 live=`heightIn(max=160dp)` 内滚(B)、settled=不封顶(A)（`ChatMessageBubbles.kt:706-735`）；dsh-Go 流式中用「恒 3 行实时窗口」（`thinking_pill.dart:383-388,416-440`）——**两家手机参考都在流式期限制了视口** | **解 A（守 G-03，禁混用）**：规则干净；但要么流式不封顶（每 token 推走正文，G-02 症状）、要么定稿仍封顶（长思考读不完）——两家都证明这两条不可接受。<br>**解 B（允许「state-switched A/B」并作为第 4 形态写进 G-03）**：必须逐节点声明「两个形态的触发条件 + 切换点」，否则 G-03 无法验收。<br>**解 C（只用 dsh-Go 的「流式固定窗口 + 定稿展开」而仍叫形态 A）**：本质同 B，只是措辞回避 | **解 B**（需改 G-03：加第 4 形态 `D. state-switched（live=B 限高内滚 / settled=A 不封顶）`，并要求 P3 逐节点写触发条件）。理由：G-03 的意图是「同一内容的意图不混用」，而 live/settled 是两个不同意图；两家手机参考都独立得出同一结论 |

**附：需 Lead 动笔的文档修正（非阻塞，均在他人写作用域）**

1. `_conflicts.md`：新增 `C-BP-01`（菜单行高 dense 21/32 vs 触摸 44~48）、`C-BP-02`（Modal 遮罩判据 `onClick` vs `onMouseDown+target`）、`C-BP-03`（弹层/菜单形态：DSH 居中 Modal+锚定 popup vs dsh-Go/太墟 底部 sheet+Dialog）、`C-BP-04`（dirty 表单关闭：丢弃 vs `DiscardGuard` 拦截）、`C-BP-05`（G-03 是否允许按状态切形态）；并**更新 C-03**（现倾向「视觉不变+扩边+8dp」已被三源手机事实修正：dsh-Go 分级 + 整行承载 + 横向 Bleed；8dp 隔离带无 E0）。
2. `00-global-rules.md`：**G-02** 的 `24px+delta` 示例值需按 Q-BP-01 的类别表更新（意图不变：定高不重排；dsh-Go 用 32、太墟用 28，需登记为「值按类别定」）；**G-03** 需加第 4 形态 `state-switched`（Q-BP-09）；**G-01** 补边界一句（只约束 overlay，不约束内联状态条/布局列）；**G-10** 补一句（240dp 是上限不是下限，基座原子默认无高度动画）。
3. `open-questions.md`：Q-03 并入 §2.1 的类别表与三条扩边路径（现 3 个候选缺 dsh-Go 的「整行承载 / Bleed / 自绘命中层」三种真实做法）；Q-BASE-14 可标「已由 G-10 决定 + Q-BP-02 收口」。

---

## 7. 自检

- [x] 每个节点在对照表里有一行 —— §1 列 27 个，§2 有 27 行（含 2 个新增抽象）。
- [x] **dsh-Go / 太墟 两列已按 13:18 落库的事实库补齐**（27 行全部有值；无对应节点的写「无」并说明其替代做法）。
- [x] 每条「建议」都引了 G 规则或给了反证 —— G-01/G-02/G-03/G-04/G-08/G-10 均有引用；其余全部写了「反证 / 反证原话」（含 dsh-Go、太墟原文反证）。
- [x] 没有 E2 推测出现在 §2/§3 —— 未取证项共 3 处，均**就地标注**：dsh-Go 的 `Tooltip` 长按是否显形、Snackbar 是否拦指针、Compose 是否有 hitTest 扩边等价物（后者进 Q-BP-06）。
- [x] 明确不做项都写了理由 —— §4 共 7 条，每条含出处 + 理由（4-3 额外标「已知缺口」）。
- [x] 阻塞项已编号 —— `Q-BP-01`…`Q-BP-06`、`Q-BP-08`、`Q-BP-09`（共 8 条）；`Q-BP-07` 已撤销并留占位。
- [x] 一行一节点、无「其他」类目。
- [x] 未修改 `docs/spec/ref/**`、`00-global-rules.md`、`_conflicts.md`、`_merge-*.md`、`p2/_plan.md`；未改 app；未 git commit。

# P2 合并：base.primitives

> 合并员：M1。输入：`ref/dsh-base.md`、`ref/chat-demo.md`、`ref/legacy-myco.md`、`ref/dsh-mobile.md`、`ref/dsh-workspace.md`、`ref/dsh-settings.md`、`ref/dsh-chat.md`（仅取与原子层有关的段落）+ `00-global-rules.md` + `_conflicts.md`。
> 本文件只做**归一取舍**，不新增事实。凡 ref 未写的一律「未取证」，需要但没取证的进 §6。

## 0. 读表须知（三条，先看）

1. **`dsh-Go` / `太墟` 两列为空不是「他们没有这个做法」，而是本仓库没有事实库**：`docs/spec/ref/` 下不存在 `dsh-go.md` 与 `taixu.md`（P1 未产出）。按 G-00 不得推测，全表该两列写 `—`。此项本身是阻塞项 → §6 `Q-BP-07`。
2. **证据缩写沿用各事实库原文，行内标出处库名**：
   - `dsh-base`：`P/`=`ui-primitives/src/`、`L/`=`ui-layout`、`D/`=`ui-dockkit`、`T/`=`ui-theme`（与 `dsh-base.md` §0 一致）；
   - `demo`：`B/`、`CI/`、`M/`、`MD/`、`S/`、`SE/`、`APP/`（与 `chat-demo.md` §2 一致）；
   - `legacy`：`MA/`、`CC/`、`CR/`、`CP/`、`MS/`、`PN/`、`IB/`（与 `legacy-myco.md` §0 一致）；
   - `mobile`：`M/:line` = `dsh-client-ui-mobile-adapt/lib/client.js`（与 `dsh-mobile.md` §0 一致）。
3. **拍板列**：`✅ 采用建议`＝我给出了可验收的建议、等用户确认；`⏸ 待拍`＝决定权在用户且影响全局面板；`❌` 本次无。**未拍板的行不得进入 P3**（`_merge-template.md:23`）。

---

## 1. 节点清单（先穷举，再填表）

> 口径：`base.primitives` = 所有面板复用的**原子层**。容器几何（三列壳、抽屉栈、dock 页签条 38px/拖拽/拆分）归 M2 `base.layout`，本表只取页签的**语义原子**（§2 `tab-strip`）。
> 「参考里有没有」只统计 DSH 的 `ui-primitives`/基座事实库与两份有原子节点的参考；`旧 myco 有没有` 以 `legacy-myco.md` §2.7（只有 2 个基座节点）为准，其余 Material3 默认组件不算「有节点」。

| 节点 ID | 出现条件 | 参考里有没有 | 旧 myco 有没有 |
|---|---|---|---|
| `base.primitives.disclosure.row` | 可展开行的「整行即开关」形态；调用方显式开 `expandOnRowClick` | dsh-base §2.1（E0） | 无节点；过程组头/行是整行 `.clickable`（`CC:1074`） |
| `base.primitives.disclosure.leading` | 同一组件的另一形态：只有 16×16 图标是开关 | dsh-base §2.1（E0） | 无 |
| `base.primitives.disclosure.fold-toggle` | 「还有 N 行」的头尾截断开关 | dsh-base §2.1（E0） | 无 |
| `base.primitives.button` | 所有主/次/危险/工具栏文字按钮 | dsh-base §2.2（E0）+ demo §2.28 | 有（Material `TextButton`/`Button`，未登记 13 字段） |
| `base.primitives.pill` | 24px 胶囊 chip（筛选/视图切换/只读徽标） | dsh-base §2.2（E0） | 无节点；M5 用 `FilterChip` 系（`MS:169-186`） |
| `base.primitives.icon-action` | 单个图标动作按钮（复制/换行/关闭/更多） | dsh-base §2.2（E0）+ demo §2.8/§2.28 | 有（M5 操作条 15dp 图标，`CR:601-607`） |
| `base.primitives.icon-action-bar` | 承载 ≥2 个图标动作的一行 + 其显隐策略 | **无此抽象**；仅 chat 级实例（`dsh-chat` §2.5）+ demo §2.8 | 有（`CR:600-607`、`CC:1477-1496`） |
| `base.primitives.tooltip` | 图标按钮的 hover/focus 提示 | dsh-base §2.2（E0）+ demo §2.31 | 无（Android 无 hover，旧 myco 未做） |
| `base.primitives.hover-card` | 悬停/聚焦浮出的预览卡 | dsh-base §2.2（E0）+ demo §2.14（来源卡） | 无 |
| `base.primitives.modal` | 居中模态框（确认/表单/风险确认） | dsh-base §2.2（E0）+ demo §2.30 | 有（`AlertDialog`，`MA:590-681`） |
| `base.primitives.dialog-dismiss` | 任意 Dialog 的取消/关闭路径（点外/返回/取消键） | **无独立节点**（并入 modal 的关闭路径，dsh-base §2.2 字段4） | **有**（legacy §2.7，11 处实例） |
| `base.primitives.bottom-sheet` | 底部升起面板（选择类/来源类） | dsh-base **无**；legacy 有（`MA:1291-1315`、`MS:49`） | **有**（`ModalBottomSheet`） |
| `base.primitives.menu` | 锚定下拉/上下文集（含多选标记） | dsh-base §2.4（E0）+ demo §2.29 | 无节点；M5 模型选择面板是扁平列表（`MS:49-148`） |
| `base.primitives.toast` | 底部一次性提示（可带动作/撤销） | dsh-base §2.2（E0） | 有（Material `Snackbar`，`MA:477-480`） |
| `base.primitives.image-lightbox` | 图片全屏查看 | dsh-base §2.2（E0） | 无节点（旧 myco 无灯箱） |
| `base.primitives.connection-indicator` | 断线/连接中/已恢复三态 | dsh-base §2.7（E0） | 无 |
| `base.primitives.switch` | 设置项布尔值 | dsh-base §2.3（E0）+ demo §2.28 | 有（Material `Switch`，`MS:169-186` 自绘） |
| `base.primitives.checkbox` | 风险确认「我已了解」/多选 | dsh-base §2.3（E0） | 无节点（`MA:1916-2030` 有 3 个 Checkbox） |
| `base.primitives.input` | 受控文本输入 + 行内编辑 | dsh-base §2.3（E0） | 无节点（`OutlinedTextField`，`CP:387-395`） |
| `base.primitives.segmented-control` | 少量模式切换（JSON/文本等） | dsh-base §2.3（E0） | 无 |
| `base.primitives.tab-strip` | 页签条（选中/关闭/键盘）的**语义原子** | dsh-workspace `base-sidebar-right.tab-strip`（E0，`TabPanel.tsx:245,263-266,103-124`） | 无（M5 无右栏/无页签） |
| `base.primitives.content-fold` | 长内容块头尾截断 + 展开 | dsh-base §2.5（E0） | 无 |
| `base.primitives.json-tree` | 只读 JSON 结构树 | dsh-base §2.5（E0） | 无 |
| `base.primitives.markdown-link` | 正文内可点文本/文件 chip/图片按钮 | dsh-base §2.5（E0） | 有（`withLink`，`CC:402-416`） |
| `base.primitives.settings-field` | 设置表单字段（label/控件/帮助/重置/校验） | dsh-base §2.5 + dsh-settings §2.2（E0） | 有（12 输入框 + 3 Checkbox，`MA:1916-2030`） |
| `base.primitives.form-save` | 表单底部「保存」（显式保存型页面） | dsh-settings §2.2 `settings.form.save`（E0） | 有（`settings.provider-editor.save`） |
| `base.primitives.hit-target` | **新增原语**：统一命中盒包裹层（扩边落点） | 无（DSH 无扩边；mobile 用 CSS 伪元素，Compose 无等价物） | 无（整行 `.clickable`，无统一扩边层） |

> 计数：**27 个节点**（24 个来自参考、3 个是我方新增抽象：`icon-action-bar` / `bottom-sheet` / `hit-target`；`bottom-sheet` 有 legacy E0 行为依据，另两个见 §5）。

---

## 2. 对照表（主线）

> 冲突列只写「做到 A 就做不到 B」。`C-xx` = `_conflicts.md` 已登记；`C-BP-xx` = 本面板新发现、**编号待 Lead 收进 `_conflicts.md`**（我不改那份文件）。
> 三个新增抽象在本表也有行（要求「每个节点在对照表里有一行」），其「参考里有没有」已在 §1 说明。

| 节点 ID | DSH | dsh-Go | 太墟 | chat-demo | 旧 myco | 冲突 C-xx | 建议 + 理由 | 状态 |
|---|---|---|---|---|---|---|---|---|
| `base.primitives.disclosure.row` | 整行即开关，`expandOnRowClick` **默认 `false`、由调用方开**（dsh-base §2.1 字段1/3；`P/DisclosureRow.tsx:50,64,96`）；命中矩形 = `.row` 盒（`24px+delta`），`overflow:hidden` → **无法伪元素扩边**（`P/DisclosureRow.module.css:30-44`）；形态 A 条件挂载、无高度动画（`tsx:121`） | — | — | 无此原语；过程区标题为 `div`+onClick，无 `role`/`tabindex`/`aria-expanded`（demo §2.11 字段11、§3#4；`M/ReplyArea.tsx:81`） | 过程组头/行整行 `.clickable`，视觉 24–30dp，**无 `aria-expanded`**（legacy §2.1 字段3/11；`CC:1074`、`CR:340-344`） | **C-03**、**C-14** | 采用形态 A（G-03）+ 默认整行可点（见 §3-1）；命中区由 `base.primitives.hit-target` 按 Q-BP-01 判定式决定，**不硬编码 44/48dp 到本行**。`aria-expanded` 必须有（对比 demo/legacy 均缺，是它们被判定为缺陷的原因），`aria-controls` 不做（DSH 本身也没有，`P/DisclosureRow.tsx:86-123`）。反证：抬视觉到 48dp 会让过程行密度 −44%（Q-BP-01 算术），G-03 的「过程不抢正文」意图被反向放大 | ⏸ 待拍（Q-BP-01） |
| `base.primitives.disclosure.leading` | 16×16（+delta）图标 `<button>`，`padding:0;border:none`，唯一开关，`aria-label`+`aria-expanded`（`P/DisclosureRow.module.css:50-63`；`tsx:99-108`）；面积 ≈ 整行形态的 1/8（字段3） | — | — | 无 | 无（其图标在整行 `.clickable` 内，不可单独点） | **C-03** | **两形态都保留，但 myco 默认整行**：图标形态只给「行内还有 accessory（如徽标/按钮）」的场景。理由：触摸端 16×16 ≈ 3mm，是 48dp 目标的约 1/9 面积（dsh-base §2.1 字段3 反证原话）；在 24dp 定高、无法扩边的行上，「整行宽」是唯一不改视觉就能提命中的维度。若默认取图标形态，等于把 DSH 的 Web 鼠标精度假设搬到手指上（`dsh-base.md` §0-2 明确「给不给是调用方决定」） | ✅ 采用建议 |
| `base.primitives.disclosure.fold-toggle` | 原生 `button`，**组件自身无任何尺寸/行高声明**，全由调用方 `className` 决定（`P/FoldToggle.tsx:1-33`）；`aria-expanded` + `aria-label` 含隐藏行数（`:26-30`） | — | — | 无 | 无 | — | 采纳语义（`aria-expanded` + 文案含 N 行），但**尺寸必须由 myco 补**：DSH 这里「未定义」不是可抄做法（G-00），触摸端会退化成文字行高。统一由 `hit-target` 给最小 F×F | ✅ 采用建议 |
| `base.primitives.button` | `md` 高 36 / `sm` 高 28；4 变体；字号固定 14/12 **不跟内容字号轴**（`P/Button.module.css:2-34`；Q-BASE-05） | — | — | `l=40 / m=36 / s=32`，IconButton 正圆 28/34/44，且有 **44px 触控 token** `--ds-control-height-xl`（demo §2.28；`APP/theme.css:9-13`） | Material3 `TextButton` 默认 **48dp**（legacy §2.7 字段3；`CP:69-71`） | — | 尺寸取 **48dp 主档 + 28dp 紧凑档**，不取 DSH 的 36/28（无触控下限）也不取 demo 的 40/36/32（Web 断点产物）。依据：E0 —— 旧 myco 的 Material 默认就是 48dp 且已在真机跑（`legacy-myco.md` §2.7）；E1 —— Material/Android 触控下限 48dp。反证：照抄 36/28 则每个文字按钮在手机上都低于平台下限（`dsh-base.md` §0-10 已确认 DSH 全库无 44dp 先例） | ✅ 采用建议 |
| `base.primitives.pill` | 高 24、`padding:0 8px`、字号 12；`onClick` 存在才渲染 button，否则静态 span；**无 `aria-pressed`**（`P/Pill.module.css:1-14`；`P/Pill.tsx:16-35`；Q-BASE-06） | — | — | 无同型原子（引用角标是自绘 span，demo §2.14） | 无节点（M5 用 `FilterChip`/自绘 pill） | — | 采纳 24px 视觉；**myco 强制 `aria-pressed`（Compose: `Modifier.selectable(selected=…)`）**，选中的 chip 必须能被读屏读成「已选中」。理由/反证：Q-BASE-06 的两解（`aria-pressed` vs radio 组）不是互斥——原子只负责「我是否被按下」，互斥是调用方组合（radio 组由调用方声明）；若原子不带状态，读屏用户无法知道当前筛选项（`dsh-base.md:798` 原话） | ✅ 采用建议 |
| `base.primitives.icon-action` | 24×24（代码卡）/ 28×28（dock）；`padding:0` 无扩边；复制成功换对勾 1000ms；换行 `aria-pressed`；**复制失败静默**（`P/CodeCard.module.css:56-61`、`dsh-base` §2.2 字段3/10；`P/ReadBlock.tsx:97-98`） | — | — | 图标按钮 28×28（`B/IconButton.module.css:35-38`）；复制成功换勾 1000ms 且吞重复点击（demo §2.8 字段5；`M/CopyButton.tsx:20-31`） | M5 操作条图标 **15dp 且无扩边**、无 `contentDescription`（legacy §4#5；`CR:601-607`） | **C-03** | 视觉 24/28dp 不变，命中盒由 `hit-target` 强制 F×F（单枚按钮独占一格时可直接把盒做 48×48，视觉图标居中）；复制成功**必须播报**（G-08 同族：运行/结果态要配 a11y 文案），复制失败**必须有失败态**（不采纳 DSH 的静默：`dsh-base.md:802-810` Q-BASE-07 已指出其自身不一致，`JsonTree` 有 `failed` 态 `P/JsonTree.tsx:121-124`）。反证：静默失败 → 用户重复点 → 不知道是「没复制上」还是「按钮坏了」 | ✅ 采用建议 |
| `base.primitives.icon-action-bar` | **无原子抽象**，只有 chat 级实现：一行 28×28、`gap:8px`、`height:calc(28px+delta)`；`@media(hover:hover)` 且 `data-actions-reveal=hover` 才 `opacity:0`，`:hover`/`:focus-within` 复现；**触摸设备不进该媒体查询 → 常驻可见**；隐藏用 `opacity` 不用 `display`（`C/MessageIconActions.module.css:5-10,42-61`；`dsh-chat` §0#7） | — | — | `.actions{opacity:0}` + `:hover`/`:focus-within`，`isLast` 常显；**没有 `@media(hover:none)` 兜底 → 触摸端历史消息不可达**（demo §0#6、§3#10；`M/AssistantMessage.module.css:4-7,95-105`） | 常驻（旧 `TextButton` 文字常驻；M5 图标常驻，legacy §4#5） | **C-04** | **无条件常驻**（不做 hover 显隐分支）。理由：①myco 是 Android 原生触摸 App，`@media(hover:hover)` 这个 Web 判据在 Compose 里没有可靠等价物，照抄等于把 demo 的缺陷搬进来（demo 的缺陷就是「无 hover 兜底」）；②DSH 哪怕在桌面也**仍占位**（行高恒 `28px+delta`），所以常驻只改 `opacity` → **零布局代价**，只增加视觉显性；③demo 的 `.alwaysShow` 证明「常驻」是它自己的兜底方向。反证：若做成 hover 显隐，Android 上无 hover 事件 → 复制/重生成永远不可见（demo `M/AssistantMessage.tsx` 同款） | ✅ 采用建议（C-04 的答案） |
| `base.primitives.tooltip` | `position:fixed` 气泡 + 视口翻转；`role=tooltip` 但普通模式**不给 `aria-describedby`**；**指针模态下 focus 不触发**（`P/Tooltip.tsx:244-248,217,229-231`）；`openOnClick` 钉住模式（`:54-55,189-204`） | — | — | 移动端 **`isMobile` 时完全不显示**（demo §2.31 字段4；`B/Tooltip.tsx:24,43`）；`role=tooltip` 但未与触发器关联 | 无（Android 无 hover） | — | **两家在触摸端结论一致（都不出 tooltip）**，故不是冲突：不做 hover tooltip 原语（→ §4），图标含义由 `contentDescription` 承载（读屏）+ 图标自明。理由：G-00 —— DSH 的 hover 路径在触摸端无事件源；demo 已用硬关回避；继续做只会得到一个「永远不显示或与长按选字打架」的原语 | ✅ 采用建议 |
| `base.primitives.hover-card` | `fixed` 宽 244、`gap` 预览、开启有 grace 期、触屏 inline 变体不因 hover 打开、`copyable` 时整卡 `role=button` 复制（`P/HoverCard.module.css:9-19`；`P/HoverCard.tsx:235-248,272`） | — | — | `[citation:N]` 角标 + hover 300ms 来源卡（demo §2.14；右侧另来源面板 §2.15） | 无 | — | **仅宽屏保留预览**；触摸端不用 hover-card，改为「点角标 → `bottom-sheet`/详情」（分工见 Q-BP-03）。**不做 `copyable` 整卡复制**（→ §4）。反证：手机上 hover 不存在，把预览塞进 hover-card 等于该能力不可达；而 demo 的 hover 300ms 在触摸端同样不可达（它靠右侧分栏兜底） | ⏸ 待拍（Q-BP-03） |
| `base.primitives.modal` | `fixed;inset:0;z-index:1000`；卡片 `min(380px,100%)`；**遮罩用 `onClick` 关闭**（`P/Modal.tsx:59`）；Escape 仅栈顶、Tab 在框内循环、开时记 `activeElement`→关时还回（`P/useModalLayer.ts:59-69,91-94`）；`role=dialog`+`aria-modal`+`aria-label`（`:60-68`）；入场 200ms 淡入 + reduced-motion 关（`P/Modal.module.css:29,47-57`） | — | — | 遮罩 **`onMouseDown` 且 `target===currentTarget`**（专为「框内拖选文字、遮罩上松手不关」）；Escape 用模块级 `openModals` 栈；**无焦点陷阱、无初始焦点、关闭后焦点不还**（demo §2.30 字段4/10/11；`B/Modal.tsx:9-10,36-49,54`） | `AlertDialog` + 返回键 `onDismissRequest`；**表单类关闭 = 丢弃未保存输入且无二次确认**（legacy §2.7；`MA:2012`、`MA:1357`） | **C-BP-02**（遮罩判据） | 三条一起定：①**遮罩判据采用 demo 的 `onMouseDown`+`target` 判定**（`B/Modal.tsx:54`）——互斥于 DSH 的 `onClick`：后者在「框内按下、遮罩上松开」时会触发（`click` 落在共同祖先上），正好命中 demo 反证里「丢掉刚填的内容」；②**焦点契约采用 DSH**（`P/useModalLayer.ts:46-96`）：开时移焦、Tab 循环、关时还焦；③`role=dialog`+`aria-modal`+label 两家一致。反证：不做陷阱 → Tab 到遮罩背后正在滚动的会话；不做还焦 → 焦点掉到页面顶部 | ✅ 采用建议（②③）/ **C-BP-02 选 demo** |
| `base.primitives.dialog-dismiss` | 无独立节点（关闭路径见上方 modal） | — | — | 同 modal（`closable` 未传时不显示 X，demo §2.30 字段5） | **有节点**：`confirmButton`/`dismissButton` + `onDismissRequest`，11 处实例；**关闭不产生任何写命令，但表单类丢草稿**（legacy §2.7 字段5/10；`MA:1357,2012`） | **C-BP-04**（dirty 表单的关闭语义） | 保留该能力但合并为 modal 的关闭路径，并加一条 **dirty 拦截**规则：弹层内表单 `dirty=true` 时，关闭需二次确认（或保留草稿）。互斥点：DSH `SettingsForm` 是「离开即丢弃、无放弃按钮」（`P/settings-form/SettingsForm.tsx:52-54`），与「dirty 拦截」不能同时成立。反证：无条件丢弃会在用户改到一半时静默吃掉输入（legacy 已踩，`MA:2012`）；无条件确认则在无改动时多点一次 | ⏸ 待拍（C-BP-04） |
| `base.primitives.bottom-sheet` | **DSH 无此原语**：`ui-primitives` 21 个节点里没有 sheet（`dsh-base` §2 全文）；弹层只有 Modal / Menu / HoverCard / Tooltip / Lightbox | — | — | 无 | **有**：`ModalBottomSheet`（附件三选一 `MA:1291-1315`；M5 模型面板 `MS:49-148`，`onDismiss` 下滑/点外 → `MA:1489`）；`legacy-myco` §4#24 明确「M5 未覆盖 attachment」 | **C-BP-03** | **保留为 myco 原子**（DSH 无先例不构成禁止，G-00 只禁止推测不禁止新增）；关闭语义取 legacy 的「下滑 + 点外 + 返回键」三路，并要求**下滑关闭必须与内部滚动仲裁**（内滚在顶时下滑才关）。反证：不给下滑关闭，则手机上关一个 sheet 只能点 X 或遮罩（拇指落在屏幕上半部，单手不可达）；而 G-03 形态 C 的「独立高度 + 浮层内滚」在 Compose 里也只有 sheet 这一类容器能同时给出 | ⏸ 待拍（Q-BP-03） |
| `base.primitives.menu` | 行 `padding:6px 8px` + 20px 行高 ≈ **32px**；**紧凑模式行 ≈21px**（`P/Menu.module.css:28-45,133-145`）；portal `z-index:1100`；点外关、Esc/Shift+Tab 关且还焦到锚点、Tab 结算当前行、方向键/Home/End；**有子菜单时不内滚**（`P/Menu.tsx:326-332,340-404,437,517-547`；`role=menu/menuitem :522,441-458`） | — | — | 菜单项约 40 高、Select 选项 ≥40；**无键盘上下键/Enter 选择/焦点归还**（demo §2.29 字段4/11；`B/Select.tsx:48-86` 是全仓唯一 `aria-expanded`） | 无节点（M5 模型面板是 `ModalBottomSheet` 内扁平行，`MS:100-123`） | **C-BP-01**（行高） | 行高**直接抬到 48dp**，不做扩边、不保留 DSH 的 32/21px 档（触摸端）。理由：菜单是**瞬时浮层**，不存在「过程行阅读连续性」问题，dsh-mobile 的主策略就是 `min-height:44` 打在菜单行上（`mobile` §4.1 S1 + §2#9；`M/:507-538`）；反之保留 32/21px 就是 Q-BASE-04 里的「解 C 只放大高频动作」，而菜单行不是低频。其余交互（点外关、Esc 还焦、Tab 结算、方向键）**全采纳**（demo 缺键盘是其缺陷，`chat-demo.md` §3#20 自评）。代价：一屏菜单项约 12→8 项（按 400dp 可用高）。子菜单不做（→ §4） | ✅ 采用建议（行高）/ ⏸ C-BP-01 待拍 |
| `base.primitives.toast` | portal 到 body、`left` 注入定位、`--dsh-toast-hold` 控制停留；**横幅 `pointer-events:none`，只有动作文字可点**；`role=alert` 即出现即播报；成功态带对勾（`P/Toast.tsx:78-101`；`P/Toast.module.css:55-79`） | — | — | 无同型原语（配额超限走 toast + 文档链接，demo §1#9） | Material `Snackbar`：「已归档 N 个」+「撤销」，撤销窗口短（legacy §0-3；`MA:360-376,477-480`） | — | **采用 DSH 形态**（横幅不拦指针 + 仅动作可点 + `role=alert`），但动作命中区必须由 `hit-target` 扩到 F×F（DSH 的动作命中 = 文字行高，`P/Toast.module.css:55-70`，触摸端远小于下限）。保留 legacy 的「撤销动作」能力（挂在 toast 的 action 上即可，不是两套 UI）。理由：G-01（浮层不占位）已定 overlay；再叠一个整条可点的 Snackbar 会让底部输入区在 1–4s 内不可点（这是推测 → 标注：**Snackbar 是否拦指针未取证**，见 §6 Q-BP-03 附注）。反证：动作命中只有文字行高 → 手机上点不中 | ✅ 采用建议（形态） |
| `base.primitives.image-lightbox` | `fixed;inset:0;z-index:1000`；关闭按钮 **36×36**；遮罩 `onMouseDown` 关；Escape 关；**Tab 被强制停在唯一关闭按钮上**；开时记焦点→聚焦关闭按钮→关时还焦（`P/ImageLightbox.tsx:37-47,58`）；`role=dialog`+`aria-modal`（`:51-56`）；**无入场动画、无缩放/平移手势**（dsh-base §2.2 字段4/9） | — | — | 无节点（未登记灯箱） | 无 | — | 采纳（含「Tab 锁在唯一按钮」与「还焦」两条契约，`ImageLightbox.tsx:39-47` 是真实实现）；关闭按钮命中由 `hit-target` 抬到 F；**不加双指缩放/平移**（DSH 明写未见实现，属参考没有的能力，不进 §2 建议，也不进 §4 因为不是「明确不做」而是「无依据」→ 见 Q-BP-03 附注）。反证：不锁 Tab → 键盘/读屏焦点跑到遮罩背后；不还焦 → 关灯箱后丢失阅读位置 | ✅ 采用建议 |
| `base.primitives.connection-indicator` | 三态 `connecting/warning/recovered`；**坏态本身是「重连」按钮**；恢复态 `role=status` 自动播报并定时自隐；**内联块参与布局流（非浮层）**（`P/ConnectionIndicator.tsx:55-103`） | — | — | 无 | 无 | — | 采纳。并**明确一条 G-01 边界**（与 C-19 同族）：G-01 只约束 overlay（回到底部/FAB/Snackbar/提示），**不约束内联状态条**；本节点出现/消失推挤内容是正确的（`dsh-base` §2.7 字段7 原文）。这条需 Lead 补进 `00-global-rules.md` 的 G-01 | ✅ 采用建议（含文档修正请求） |
| `base.primitives.switch` | 轨道 **36×20**、滑块 16×16、`translateX` 120ms；样式键挂在 `aria-checked` 上（视觉与读屏不可能不一致）；`role=switch` + `aria-checked` + 必填 `label`；**无滑动切换**（`P/Switch.module.css:5-49`；`P/Switch.tsx:29-37`） | — | — | Material 默认尺寸（`MS:169-186` 自绘） | `Switch`（`MA:1916-2030` 内 3 个 Checkbox + Switch） | — | 采纳 `aria-checked`-驱动样式的写法（这是全表最强的一条 E0 经验：读屏态与视觉态**不可能**不一致）；视觉 36×20 不变，命中盒由 `hit-target` 扩到 F（这正是 dsh-mobile 唯一用伪元素扩边的场景：`mobile` §4.1 S2，`M/:940-945`）；**不做滑动切换**（DSH 未见 → 我不新增）。反证：并行 class 会造成「看起来开了、读屏说关了」（`P/Switch.module.css:1-3` 注释） | ✅ 采用建议 |
| `base.primitives.checkbox` | 原生 `input` + `<label>` 包裹 → **整个 label 是命中区**（16px 方框 + 6px + 文字宽）；`accent-color` 用 token（`P/Checkbox.module.css:1-28`；`P/Checkbox.tsx:16-31`） | — | — | 无 | 无节点（3 个 Checkbox 在 provider 编辑器里） | — | 采纳「整 label 可点」（把命中区做进布局盒，天然满足高度/宽度）；反证：只有 16×16 方框可点 → 手机上要精确命中 3mm 方块 | ✅ 采用建议 |
| `base.primitives.input` | 外层 `.wrap` 高 **32px**、内层字号 14/22；`InlineEditor` 自动长高；**IME 组合期（`isComposing`/keyCode 229）不提交**、Enter 提交、Shift+Enter 换行、Esc 取消回滚（`P/Input.module.css:1-10`；`P/InlineEditor.tsx:33-69`） | — | — | 无同型（输入区归 composer 面板） | `OutlinedTextField`（`CP:387-395`） | — | 基座只定两条**硬契约**：①**IME 组合期不提交**（`P/InlineEditor.tsx:45-55`）——中文拼音选词按 Enter 不能被当成提交，否则会写入半截内容；②**Esc 取消必须回滚原值**（`:45-55`）。尺寸/键盘避让归 M4 `composer`，本表不拍（避免越界）。反证：违反① → 中文用户输入被打断；违反② → 用户以为已保存 | ✅ 采用建议 |
| `base.primitives.segmented-control` | `role=tablist` + N 个等宽 `role=tab` + `aria-selected` + 漫游 `tabIndex` + `aria-controls`；**方向键走位即选中**（自动激活）、Home/End；点已选中项不触发 `onChange`（`P/SegmentedControl.tsx:26-54,56-140`；`dsh-base` §2.3 字段4/11） | — | — | 无 | 无 | — | 采纳全部语义（尤其「走位即选中」与「点已选中项不回调」）；Compose 侧 `Role.Tab` + `selected` 状态（映射见 §2.2）。与 `pill` 的分工：**segmented = 互斥模式切换（tablist 语义）**，**pill = 可多选/可切换的筛选（button+`aria-pressed`）**，两者不混用（Q-BASE-06 的两解在此按用途分工，不是折中）。反证：方向键只移焦点不切换 → 用户要再按一次 Enter，与分段控件的即时直觉不符 | ✅ 采用建议 |
| `base.primitives.tab-strip` | 容器 `role=tablist`、页签 `role=tab`+`aria-selected`+**roving `tabIndex`**；**ArrowLeft/Right 循环、Home/End 移焦，Enter/Space 才选中（manual activation）**；关闭键 `aria-label`+`aria-keyshortcuts`（`ui-dockkit/src/components/TabPanel.tsx:245,263-266,103-124,293-322`；`dsh-workspace.md` §2 `tab-strip` 字段11） | — | — | 无 | 无（M5 无右栏） | — | **语义全采纳**（这是全库最完整的键盘实现），**几何归 M2**（38px 条、chip 28–30px、拖拽/拆分/浮动 → `base.layout.dock-tab*`），本行只管「选中/关闭/键盘/ARIA」。**但有一个必须一起决定的前提：manual activation 的键位在触摸端无意义**（无物理方向键）→ 见 Q-BP-05。反证（DSH 源码注释原话）：只给 `role=tablist` 不给方向键，读屏会报「选项卡，共 3 个」而方向键毫无反应，**比不加 role 更糟**（`TabPanel.tsx:103-107`） | ⏸ 待拍（Q-BP-05） |
| `base.primitives.content-fold` | 头 `ceil(max/2)` 行 + 按钮 + 尾若干行；**形态 B（capped-inline）**；按钮 `aria-expanded` + `aria-label` 含隐藏行数；**无高度动画**（`P/ReadBlock.tsx:104-150`；`P/TerminalBlock.tsx:269-277`；`P/FoldToggle.tsx:19-33`） | — | — | 无 | 无 | **C-14** | 采纳形态 B + `aria-expanded`；动效按 Q-BP-02（长内容通常 >240dp → 直接切换）。**必须同时声明「折叠 vs 内滚」谁优先**——这是 Q-BASE-08 未决项，属本原语的边界态，不解决会导致 G-04 没有落实对象（→ Q-BP-08，建议取解 B「内滚优先 + 每块声明到边行为」） | ✅ 采用建议（动效见 Q-BP-02 / 内滚见 Q-BP-08） |
| `base.primitives.json-tree` | 展开器 `role=button`+`aria-expanded`+`aria-controls`（**本库唯一做对了 controls 的原子**）、子树 `role=group`、漫游 `tabIndex`；**右键 = 复制方式菜单**；`copy` 有 `failed` 态（`P/JsonTree.tsx:117-165,302-312,595-628,663-664`） | — | — | 无 | 无 | — | **本面板不建该原语**（→ §4）：工具结果渲染归 `tool` 面板，触摸端它最关键的两条交互（右键菜单、漫游 tabIndex）都不可达，硬搬会得到一个半残原语。若 `tool` 面板确需结构树，按 `content-fold` + 文本先做 | ✅ 采用建议（不做） |
| `base.primitives.markdown-link` | 外链 `<a href target=_blank>`、本地文件改 `<button>`、提及 chip 有 `aria-label`；**无修饰键点击才接管，Cmd/Ctrl/Shift/Alt 保留浏览器行为**；`user-text` chip **双击或有选区时不跳转**；流式期间本地链接不解析（不可点）（`P/markdown/render.tsx:550-619`；`P/user-text.tsx:127-150`） | — | — | 正文链接 `withLink` → 系统浏览器（`CC:402-416`，与 DSH 行为一致） | 有（`withLink`，legacy §2.8 `chat.msg.link`） | — | 采纳：外链→系统浏览器（Android `Intent`）、本地文件/提及→按钮语义、**流式期间不可点**、**有选区时不跳转**。**「修饰键」分支不做**（→ §4：Android 无 Cmd/Ctrl）。反证：流式期解析半截路径 → 用户点到错文件（DSH 字段10 原话） | ✅ 采用建议 |
| `base.primitives.settings-field` | label+控件纵排；`htmlFor` 关联；help 按钮 12px 图标 + `aria-expanded`+`aria-controls` + 帮助区 `role=region`；`invalid` → `aria-invalid` + 用错误文案替换 hint；重置只在 `overridden` 时出现；密码只 write-only、永不回显（`P/settings-form/fields.tsx:64-115,125-151`；`P/ConfigField.tsx:33-53`；`dsh-settings` §2.2） | — | — | 无同型（demo 设置是弹窗内控件，`SE/Settings`） | 12 输入框 + 3 Checkbox（`MA:1916-2030`），无 help/重置/校验结构 | — | 采纳全部结构；**帮助区展开不做高度动画**（DSH 也无，`fields.tsx:110-112`；理由见 §2.3）。**保存语义不在这里拍**——「显式保存 vs 即时写」是 C-15，归 M6 的 `settings` 面板，本表只给原子的状态机接口 | ✅ 采用建议（语义归 M6） |
| `base.primitives.form-save` | 显式保存唯一写入点：保存条件 `dirty && !invalid && !saving`；**无「放弃」按钮**；Host 拒绝 → 保留草稿 + `role=status` 文案；**卸载即丢草稿**（`P/settings-form/SettingsForm.tsx:1-74`；`dsh-settings` §2.2 `settings.form.save`） | — | — | 无同型（demo 用即时写 + 显式 Apply） | 有（`settings.provider-editor.save`，legacy §2.5） | — | 采纳「三个布尔条件才可点 + 失败保留草稿 + `role=status`」；**保存语义（显式/即时）按页声明**归 M6（C-15），本行只保证按钮原子不发明第二套语义。与 `dialog-dismiss` 的 dirty 拦截规则联动（C-BP-04）。反证：失败不保留草稿 → 用户重填一遍；成功不置 dirty=false → 重复写 | ✅ 采用建议 |
| `base.primitives.hit-target` | **无**：DSH 全库无扩边（`dsh-base.md` §0-1、§0-10）；mobile 用 CSS 伪元素（`::after{inset:-12px -8px}`、`::after{inset:-14px}`，`M/:934-959`），**Compose 没有伪元素** | — | — | 有 44px token 但**无统一扩边层**（`APP/theme.css:9-13` 只是尺寸档） | 无（整行 `.clickable`，没有独立扩边层） | **C-03**（本体） | **新增原语**：一个只负责「命中盒 ≥ F、视觉盒居中不变」的包裹层。**扩边画在布局盒这一层，不画在 `Modifier`**：Compose 的 `Modifier.offset/padding` 改的是绘制/内距，不产生独立命中盒；能产生命中盒的只有「带 `clickable` 的布局节点」。落地式与判定式见 **§2.1** | ⏸ 待拍（Q-BP-01） |

---

### 2.1 附：命中区策略（C-03 / Q-03 的完整答案）

**这是本面板最重要的一条。C-03 的现登记倾向（「视觉不变 + 透明扩边 + 8dp 隔离带」）在几何上只对「行距足够」的行成立**：按 44dp 口径、24dp 行、上下各扩 10dp 计算，相邻两行**不相交**要求净距 ≥20dp；若要再加 8dp 隔离带则要求 ≥28dp。而过程组行距只有 6px，扩到 44dp 需要 e=10dp，相邻两行**必然重叠 14dp**（dsh-mobile §4.4 已算，`dsh-mobile.md:799-806`）。所以不能只给一个数，必须给**判定式 + 降级顺序**。

**定义**（全部为 dp，`h`=视觉盒高，`F`=触控下限，`B`=并排小目标的隔离带，`e`=单侧扩边量，`g`=相邻视觉盒净距）：

```
约束1（达标）  h + 2e ≥ F
约束2（不相交） 2e ≤ g
约束3（隔离带，仅并排 ≥2 个命中盒时） g − 2e ≥ B
有解条件      g ≥ F − h + B
```

**取值建议**

| 量 | 建议值 | 依据 | 级别 |
|---|---|---|---|
| `F` 触控下限 | **48dp** | Material/Android 下限 48dp（E1 平台规范）；旧 myco 的 `TextButton` 默认就是 48dp（E0，`legacy-myco.md` §2.7 字段3） | E1+E0 |
| `B` 隔离带 | **8dp** | 无 E0：DSH/mobile/chat-demo/legacy **四家都没有**「最小无命中带」规则（`dsh-mobile.md` §4.2 末段「没有任何最小 8dp 无命中带的全局规则」）；8dp 只来自 C-03 待拍倾向 + Material 8dp 栅格 | **E1（平台栅格）→ 进 Q-BP-01 拍** |
| 计算例（24dp 折叠行） | 需 `g ≥ 48−24+8 = 32dp` | 算术 | — |
| 计算例（28dp 图标并排） | 需 `g ≥ 48−28+8 = 28dp`，即**中心距 ≥56dp** | 算术 | — |
| 计算例（36×20 开关） | 需 `g ≥ 48−20+8 = 36dp` | 算术 | — |

> **若沿用 C-03 现行的 44dp 口径**：把上表 `F` 换成 44，所有阈值同步 −4dp（24dp 行需 `g≥28`；28dp 图标需中心距 ≥52；开关需 `g≥32`）。判定式不变。

**降级顺序（可验收的执行规则，不是折中）**

1. **首选：把布局盒做成 F×F，视觉内容居中不变。** 适用：独占一行的原子（设置行、菜单行、列表行、48dp 按钮、checkbox 整 label）。此时 `g=0` 也成立——相邻盒相接、不相交，中心距 = F 已满足误触前提。
   - 代价：行高变大。菜单 32→48（−4 项/屏@400dp）；设置行 44→48（+4dp/行）。
2. **次选：视觉盒不能变大时（紧凑行/密集列表），用 `hit-target` 扩 `e = min((F−h)/2, g/2)`，并把横向面积拉满行（整行可点）。** 结果命中高可 < F，必须逐行记录实测值。
   - 代价（算术，非实测）：折叠行 `h=24,g=6` → `e=3` → 命中 **30dp**；要达到 48dp 需 `g=32dp`，行节距 30→56dp，**过程行密度 −46%**。
3. **若第 2 步算出的命中高 < 40dp：禁止继续靠扩边，必须换形态**（G-03 形态 C 或重排拉开中心距）。因为 dsh-mobile §4.2 已证明「e 与 g 冲突时扩边无解」，其正面教材是字号 stepper：两个 17×12 箭头中心距 14dp，「想调大却触发调小」，最终解法是**重排成 44×44 并排**，不是扩边（`M/:802-808`；`R1/:54-57`）。
   - 代价：过程行要么变成 sheet（改变 G-03 形态、打断「边读过程边看正文」），要么接受 56dp 行节距。

**为什么扩边必须落在「布局盒」而不是 `Modifier`**：Compose 没有 CSS 伪元素；`Modifier.offset`/`Modifier.padding` 不产生独立命中盒，只有带 `clickable`/`pointerInput` 的**布局节点**才接收触摸。旧 myco 的所有行级命中都是 `.clickable` 打在行盒上（E0：`CC:1074`、`CR:340-344`），这与「盒即命中区」一致。**这条是实现选型，不是参考事实**（DSH/mobile 都是 Web，无 Compose 证据）→ 必须 P4 真机验证最小原型（见 Q-BP-06）。

---

### 2.2 附：ARIA → Compose 语义映射（本面板全部原子的无障碍基线）

> 原则：**Compose 没有 `aria-expanded` / `role=dialog` / roving `tabIndex` 的一一对应物**。下表给「语义等价物」，级别 **E1（平台 API 语义），必须在 P4 用 TalkBack 真机验收**；不做 = 没有（G-08 是硬要求）。

| DSH 契约（E0 出处） | Compose 落法（`semantics{}`） | 验收 |
|---|---|---|
| `role=button` + `aria-expanded`（`P/DisclosureRow.tsx:93-95`、`:104`） | `Modifier.semantics { role = Role.Button; stateDescription = if (open) "已展开" else "已折叠"; onClick(label = if (open) "收起" else "展开") { toggle(); true } }` | TalkBack 报「可展开/已展开」；`base.primitives` 的验收 `BASE-03` |
| 无 `aria-controls`（DSH 自己也没有，`P/DisclosureRow.tsx:86-123`） | 不强行加；`JsonTree` 那套 `aria-controls` 只在确有可跳转目标时用 `@Semantics` 的 id 关联 | 不做为缺口登记（与 DSH 对齐） |
| `role=dialog` + `aria-modal` + `aria-label`（`P/Modal.tsx:60-68`、`P/ImageLightbox.tsx:51-56`） | **用 `Dialog`/`ModalBottomSheet` 承载浮层**（独立 window，TalkBack 焦点天然被限制在浮层内）→ 再补 `Modifier.semantics { paneTitle = title }`；同 composition 内自绘遮罩**不会**产生焦点隔离 | 开浮层后 TalkBack 焦点不能读到背后页面 |
| 焦点陷阱 + 回焦（`P/useModalLayer.ts:46-96`；`P/ImageLightbox.tsx:37-47`） | 打开前 `val prev = focusManager?` 记 `FocusRequester`；关闭时 `prev.requestFocus()`。**`Dialog` 只保证陷阱，不保证回焦**，回焦必须自己写 | 关闭后焦点回到触发按钮（`BASE-17`/`BASE-21`） |
| `role=switch` + `aria-checked`（`P/Switch.tsx:29-37`） | `Modifier.toggleable(value, role = Role.Switch)`（自带 state）+ **样式由 `value` 派生**（照抄 DSH「样式挂状态键」的写法，`P/Switch.module.css:1-3`） | 视觉与读屏态不可能不一致 |
| `role=menu/menuitem`（`P/Menu.tsx:522,441-458`） | `Popup(focusable = true)` + 每行 `Modifier.semantics { role = Role.Button }` + `paneTitle`；DSH 的子菜单 `aria-haspopup/expanded` 首版不用（§4） | 菜单项可逐项读出 |
| `role=tablist/tab` + `aria-selected` + roving `tabIndex`（`TabPanel.tsx:245,263-266`） | `Modifier.selectable(selected, role = Role.Tab)` + 外层 `Modifier.selectableGroup()`；**roving tabIndex 无等价物**——Android 无 DOM 焦点环，靠 `focusGroup`/方向键需 `onKeyEvent` 自实现（Q-BP-05） | 只给 role 不给键位 = DSH 注释里「比不加更糟」，见 Q-BP-05 |
| `role=alert`（`P/Toast.tsx:78`） | `Modifier.semantics { liveRegion = LiveRegionMode.Assertive; paneTitle/… }` 或 `clearAndSetSemantics { contentDescription = text }` | toast 出现即播报 |
| `role=status`（`P/ConnectionIndicator.tsx:63-65`、HoverCard 复制结果） | `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` | 状态变化播报 |
| 视觉隐藏文案（G-08；`ReasoningRow.tsx:84` 的 `visuallyHidden` span） | Compose 无 `visuallyHidden` class：用**零视觉占位 + 语义节点**——`Box(Modifier.size(1.dp).clearAndSetSemantics { liveRegion = …; contentDescription = "正在思考" })`，或直接把它们挂到可见容器的 `stateDescription` 上 | TalkBack 能听到运行态（`chat.assist.running` 归 M3，但原语落在这里） |
| `aria-label`（所有图标按钮） | `Modifier.semantics { contentDescription = label }`；**必填**（对应 `IconButton` 的 tooltip 兼 aria-label，demo `B/IconButton.tsx:26-33`） | 每个图标按钮有可达名 |
| DSH 分支按钮**故意不用原生 `disabled`**（为保住 Tooltip 的 hover/focus，`C/MessageIconActions.tsx:93`） | **可简化**：Android 无 hover tooltip（见 `open-questions.md:108-109` 附注）→ 不可用态直接用 `enabled = false` + `stateDescription`（说明原因） | 不可用项不被误点，且读屏能听出原因 |
| `aria-pressed`（Pill/换行） | `Modifier.toggleable(value, role = Role.Checkbox)` 或 `selectable` | 选中态可读 |

---

### 2.3 附：动画落地（G-10 在各原子上的取值）

> 先明确一条解读：**G-10 是上界不是下限**——「≤240dp 允许动画」不等于「必须动画」。DSH 基座**全部**可展开组件都不做高度动画（E0，`dsh-base.md` §4 Q-BASE-14 事实段：`DisclosureRow` 条件挂载 `tsx:121`、`FoldToggle` `P/FoldToggle.tsx:19-33`、content-fold `P/ReadBlock.tsx:104`、`JsonTree` `P/JsonTree.tsx:609`、设置帮助区 `T/client/FontSettingsGroup.tsx:46`）。myco 只在「用户必须感知到发生了展开」的地方用动画，其余直接切换。

| 原子 | 展开后内容高度 | 动作 | 依据 |
|---|---|---|---|
| `disclosure.row` | 通常 ≫240dp（思考正文） | **直接切换**，≤120ms 淡入可选 | G-10 上档；DSH 同向（C-14 ✅已定） |
| `disclosure.row` | 实测 ≤240dp | 允许纵向 expand（M5 `settle`），仅纵向 | G-10 下档（Q-BP-02 待确认是否保留该档） |
| `content-fold` | 头尾截断型，多数 ≫240dp | **直接切换** | 同上；且折叠/展开常伴随外层滚动位置变化，动画会加剧错位 |
| `json-tree`（若在 tool 面板做） | 子树通常小 | 可动画，但**首版不做原语**（§4） | — |
| `settings-field` 帮助区 | 短 | **不做动画**（直接切换） | G-10 允许 ≠ 必须；表单里动画会让下方字段在 200ms 内不可读，且 DSH 无动画（`fields.tsx:110-112`） |
| `modal` | — | 只允许 opacity 淡入 ≤200dp + reduced-motion 关闭 | DSH E0（`P/Modal.module.css:29,47-57`） |
| `toast` | — | 只允许进/出位移或淡入，**不做高度动画** | `P/Toast.module.css:76-79` |
| `menu` / `tooltip` / `hover-card` | — | 只允许 opacity（hover-card 可有 `--dsh-hover-preview-fade`）+ reduced-motion 关闭 | `P/HoverCard.module.css:66-77` |
| `bottom-sheet` | — | 只有「升起/落下」的位移；**不做内容高度动画** | 无 E0（DSH 无 sheet）→ 属新增（§5），位移曲线待 Q-BP-03 拍 |
| `switch` | — | 只允许滑块 `transform` 120ms；**不做轨道高度动画** | DSH E0（`P/Switch.module.css:40`） |
| `pill` / `segmented-control` / `tab-strip` | — | 只允许颜色/指示器位移；**不做尺寸动画** | DSH 无过渡声明（`Pill.module.css`、`SegmentedControl`） |
| 全局 | — | `prefers-reduced-motion`（Compose：`LocalMotionDurationScale`/系统动画关闭）必须能关掉以上全部过渡 | DSH 五处 `prefers-reduced-motion` 分支（`P/DisclosureRow.module.css:107-111`、`P/Modal.module.css:55-57`、`P/HoverCard.module.css:75-77` 等） |

**不该有动画的清单（结论）**：① 任何浮层的**高度/尺寸**动画（modal/toast/menu/hover-card/tooltip/sheet）；② 表单帮助区展开；③ `content-fold`；④ 页签/chip 出现消失；⑤ 开关轨道高度；⑥ 「复制成功」图标切换（DSH 是瞬换，`P/CodeToolbar.tsx:55-57`）。

---

## 3. 补充项（不互斥的差异，不进对照表的冲突列）

| # | 节点 ID | 谁有、谁没有 | 是否采纳 |
|---|---|---|---|
| 3-1 | `disclosure.row` / `.leading` | **两形态并非互斥**：DSH 用三元分支保证「同时只存在一个」（`P/DisclosureRow.tsx:99-108`），并**把选择权交给调用方**（默认 `false`，`:50`）；demo/legacy 只有整行形态（`M/ReplyArea.tsx:81`、`CC:1074`） | **采纳 DSH 的两形态保留 + myco 默认整行**。理由见 §2 该行；这不是冲突（可并存），所以不进冲突列 |
| 3-2 | `tooltip` | DSH 靠 `pointerModality()` 在指针模态下抑制 focus 提示（`P/Tooltip.tsx:244-248`）；demo 用 `isMobile` 硬关（`B/Tooltip.tsx:24,43`）——**两家结论一致：触摸端不出 tooltip** | 采纳「触摸端不出提示」；且**不建 tooltip 原语**（§4） |
| 3-3 | `toast` | DSH 是「单行横幅 + 动作文字」（`P/Toast.tsx:78-101`）；legacy 是 Material `Snackbar`（整条底部，`MA:477-480`）。**两者不是互斥能力**（都能给撤销），只是形态不同 | 采纳 DSH 形态；legacy 的「撤销」动作保留在 toast 的 action 里（不是两套 UI）。⚠ Snackbar 是否拦截指针**未取证**，不作为反对理由 |
| 3-4 | `menu` | DSH 有紧凑模式（行 ≈21px，`P/Menu.module.css:133-145`），demo/legacy 无 | **紧凑档只留给宽屏/鼠标**；触摸端一律 48dp（见 C-BP-01） |
| 3-5 | `pill` vs `segmented-control` | DSH 两者都有（`P/Pill.tsx` 与 `P/SegmentedControl.tsx`），职责靠语义区分 | 采纳按用途分工（见 §2 两行）：segmented=tablist 互斥、pill=可多选/可切换 |
| 3-6 | `connection-indicator` | 只有 DSH 有；且它是**内联块参与布局流**（`dsh-base` §2.7 字段7） | 采纳。⚠ 需澄清 G-01 边界（G-01 只管 overlay）→ 见 §6「需 Lead 动笔的文档修正」第 2 条 |
| 3-7 | `bottom-sheet` | 只有 legacy 有（`MA:1291-1315`、`MS:49`）；mobile-adapt 的统计面板/轨迹详情是**自定义 fixed 浮层**，不是通用 sheet（`mobile` §2#13/#14） | 采纳为 myco 原语；`dsh-mobile` 那两处只作「浮层锚点用实时变量、不用魔数」的实现经验（`M/:1039-1040`），不当作 sheet 依据 |
| 3-8 | `icon-action` 的「复制成功」时长 | DSH 1000ms（`P/ReadBlock.tsx:95-102`）；demo 消息条 1000ms、代码块 **1500ms**（demo §0#7，`MD/CodeBlock.tsx:56-67`） | 采纳 **1000ms 统一**（demo 自己也承认两套时长应统一，Q-DEMO-06） |
| 3-9 | 图标动作的「不可用态」 | DSH 用 `aria-disabled` + 保留 hover/focus（为 Tooltip，`C/MessageIconActions.tsx:91-109`）；demo 用原生 disabled（`B/IconButton`） | 采纳**原生 disabled**（Android 无 hover tooltip，`open-questions.md:108-109` 已指出该取舍在移动端不成立） |

---

## 4. 明确不做（参考里有的）

> 规则：不删减能力，但每一条都必须写清「为什么不进首版」。以下全部**不是**「没想到」，是决定不做。

| # | 能力 | 出处 | 不做的理由 |
|---|---|---|---|
| 4-1 | `hover-card` 的 `copyable` 整卡复制 | `P/HoverCard.tsx:235-248`（`role=button` + 检查文本选区 `:240-247`） | 触摸端没有 hover 卡片（本表已定不在触摸端做 hover 预览）；真要在预览卡里复制，会与「长按选字」原生手势打架，且 DSH 自己必须靠「有选区则不复制」来规避——成本高、收益只在桌面 |
| 4-2 | `menu` 的子菜单（父行 hover/focus 展开、非父行关闭、「有子菜单时不内滚」） | `P/Menu.tsx:437,453-511,517-520` | 触摸端没有 hover，「点击=选中」与「点击=展开子菜单」直接互斥；DSH 自己也明确「有子菜单就不内滚」是妥协（注释 `:435-436`）。首版菜单只做单层；需要层级时用 sheet 二次选择 |
| 4-3 | `tooltip` 的 hover 形态与 `openOnClick` 钉住模式 | `P/Tooltip.tsx:54-55,189-204,244-248` | 触摸端无 hover 事件源（两家参考在触摸端都不出提示，§3-2）；图标含义由 `contentDescription`（读屏）+ 视觉自明承担。**登记为已知缺口**：视觉用户在图标含义不明时无提示 → 若 P4 真机发现关键图标不易识别，改为「长按出提示」再说（见 Q-BP-04） |
| 4-4 | `markdown-link` 的「按修饰键保留浏览器行为」 | `P/markdown/render.tsx:560-564`（`Cmd/Ctrl/Shift/Alt` 点击放行） | Android 无修饰键点击；全部点击都交宿主（外链→系统浏览器、文件→应用内打开） |
| 4-5 | `json-tree` 整棵原语（漫游 `tabIndex`、右键「复制方式」菜单、`aria-controls` 子树） | `P/JsonTree.tsx:117-165,380,595-628,663-664` | 触摸端它的两条核心交互都不可达：无右键（`dsh-mobile` §2#21 明确 Android 壳未接管长按菜单）、无硬件键盘（漫游 tabIndex 无意义）。硬搬 = 半残原语。工具结果渲染归 `tool` 面板，首版用 `content-fold` + 文本 |
| 4-6 | `disclosure.row` 的 `previewChevron` 悬停换图标（原图标淡出、chevron 淡入） | `P/DisclosureRow.module.css:74-98` | 触摸端无 hover 态可触发；折叠/展开的指示改为**常显 chevron**（方向随状态变），不需要动画淡入淡出。这也顺带消掉一处 100ms 过渡 |

---

## 5. 新增节点（参考里都没有、myco 要新设计）

| 节点 ID | 为什么需要 | 依据（用户要求 / 平台规范 / 我方约束） |
|---|---|---|
| `base.primitives.hit-target` | 命中区策略（C-03/Q-03）需要一个**统一的、可验收的落点**；否则 HIT 规则会散落在每个面板的每个行里，且每个面板各写一套 | **我方约束**：目标平台 Compose 无 CSS 伪元素/无 `::after` 扩边，`Modifier` 不产生命中盒（§2.1）→ 必须新增「布局盒即命中盒」的包裹层。**平台规范**：Material/Android 48dp 下限（E1） |
| `base.primitives.icon-action-bar` | 「动作条显隐策略」（C-04）目前只存在于 chat 面板的一个私有组件里；myco 的消息操作条、代码块动作条、工具卡动作条都要用同一套「常驻/悬停/最后一条常显」规则 | **用户要求**：C-04 已定「触摸端常驻」；**我方约束**：Android 无 `@media(hover:hover)` 可靠等价物，策略必须集中在一个原语里，而不是每处 `if (isLast)` 复制一遍 |
| `base.primitives.bottom-sheet` | G-03 形态 C 需要一个「从底部升起、可下滑关闭、内部独立滚动」的容器；DSH 的 Modal 是居中 380px 且**没有任何手势关闭**，直接搬会导致手机上单手关不掉 | **我方约束**：触摸端需要下滑关闭（拇指可达）；**E0 依据**：legacy 的 `ModalBottomSheet` 已在真机使用（`MA:1291-1315`、`MS:49`，`legacy-myco.md` MISSING-1 记录其关闭手势）；DSH 无此原语是**缺口**不是禁止 |

> 说明：`live-region` / `focus-restore` 没有单列为节点——它们没有独立的「可点/可滚/可展开」载体，属**行为契约**，已写进 §2.2 的映射表与各弹层节点的建议里。

---

## 6. 待确认（阻塞项）

> 编号 `Q-BP-nn`（BP = base.primitives）。**Q-BP-01 未拍板前，不得实现任何低于 48dp 的行**（延续 `open-questions.md:31` 的「未定前不许实现任何矮行」）。

| ID | 问题 | 两解及各自后果 | 建议 |
|---|---|---|---|
| **Q-BP-01** | **触控下限与命中区策略**：C-03 现倾向「视觉不变 + 透明扩边 + 8dp 隔离带」在几何上**只对行距 ≥20dp 成立**；过程组行距 6px 时扩到 44 必重叠 14dp（`dsh-mobile.md:799-806`） | **解 A（视觉不变 + 扩边 + 隔离带）**：只在行距足够的行成立；过程行只能拿到 30dp 命中 → 仍不达标，且留下「算不出 48 就 30 凑合」的灰色地带。<br>**解 B（行高直接抬到 F）**：全部达标、可验收；代价是过程行节距 30→56dp（**密度 −46%**）、菜单一屏 12→8 项。<br>**解 C（分档：独占行走 B，密集行走 A 并把 <40dp 视为例外并记录）**：工作量最小；但「过程行 30dp」会成为永久技术债，且 WCAG 2.5.8 的 24×24 只保底不解决手感 | **解 C + 一条硬规则**：①`F=48dp`（若沿用 C-03 的 44dp 口径，判定式同步 −4）；②边界带 `B=8dp` 只在并排小目标强制；③算出的命中 <40dp 时**禁止扩边，必须换形态**（G-03 形态 C 或重排）。见 §2.1。**这是本面板第一优先，需用户拍板** |
| **Q-BP-02** | **动画档位**：G-10 已拍板「≤240dp 允许动画、>240dp 直接切」，但 Q-BASE-14 未关闭，且 DSH 基座**全部无高度动画**（`dsh-base.md:874-883`） | **解 A（照 DSH 全无动画）**：与参考完全对齐、性能最稳；但会**推翻已拍板的 G-10 下档**（Q-04）。<br>**解 B（按 G-10，≤240dp 才有动画）**：保留短内容手感；但 240dp 是 E1 工程值，且**无 DSH 代码可对账**（`dsh-base.md:880` 原话）。<br>**解 C（阈值降到 ~72dp）**：几乎不阻塞阅读；但变成按行数/像素两套口径 | **解 B**，并把「G-10 是上界不是下限」写进规则：myco 只在 `disclosure.row` 的 ≤240dp 分支用它，其余原子（§2.3 表）一律直接切换。理由：G-10 是 user 已拍板（`open-questions.md:14` Q-04），解 A 会推翻用户决定，属越权 |
| **Q-BP-03** | **弹层家族分工**：Menu / Modal / BottomSheet / Lightbox / Toast 各自管什么、关闭语义如何，DSH 只有 Modal+Menu+Lightbox+Toast（无 sheet、无手势关闭），legacy 有 sheet（下滑关） | **解 A（全用 Modal，不引入 sheet）**：DSH 可对账、实现少；但移动端选择类面板全是 380px 居中框，单手关不掉、列表可视区更小。<br>**解 B（确认类用 Modal / 选择类用 BottomSheet）**：符合 G-03 形态 C 的两种子形态，legacy 已按此用（附件/模型面板）；代价是两套关闭语义（Modal 点外+Esc，sheet 下滑+点外+返回键）要各自写死并验收。<br>**解 C（全部改成 sheet）**：统一；但破坏性确认框从底部升起会降低「慎重」感，且 DSH 的 Modal 焦点契约不能直接复用 | **解 B**，并把关闭语义矩阵写进 P3：Modal=点外(遮罩 onMouseDown 判据)+Esc/返回键+关闭键；BottomSheet=下滑(内滚在顶才触发)+点外+返回键；Menu=点外+Esc+Tab 结算；Toast=自动过期，不可手势关；Lightbox=点外+Esc，Tab 锁唯一按钮。附带两个待验：①**双指缩放/平移**要不要（DSH 明写未见实现）；②**Snackbar 是否拦截指针**（未取证，不参与决策） |
| **Q-BP-04** | **myco 是否只有触摸形态**：这决定 hover 支线（tooltip/hover-card/桌面紧凑菜单行/动作条 hover 显隐）到底存不存在 | **解 A（只有触摸，Android 手机）**：可**砍掉**整条 hover 支线（§4 的 4-1/4-3 变成「永久不做」而非「首版不做」），动作条无条件常驻，实现面最小。<br>**解 B（含平板 + 外接键鼠）**：需要 hover 与键盘两条支线，Q-BP-05 也变成必须做 | **解 A**（myco 现状是单 Activity Android App，`legacy-myco.md` §0 首段）；若解 B，则本表 §4 的 4-1/4-3 要重新打开，并且 `hover-card`/`tooltip` 要从「不做」改回「宽屏做」 |
| **Q-BP-05** | **页签的键盘模式**：DSH 是 WAI-ARIA manual activation（方向键移焦 + Enter 才选中，`TabPanel.tsx:103-124`）；Compose 无 roving tabIndex | **解 A（只给 `Role.Tab` + `selected`，不做方向键）**：触摸端够用；但违反 DSH 源码注释的反证——「只给 role 不给方向键，比不加 role 更糟」（`TabPanel.tsx:103-107`），会出现「读屏报选项卡、按方向键没反应」。<br>**解 B（`onKeyEvent` 自实现方向键 + Home/End + manual activation）**：达标；成本 = 一个键盘状态机 + 焦点环，且只对外接键盘有意义 | 取决于 Q-BP-04：解 A 则**同时不给 `Role.Tab`**（改用 `Role.Button` + `selected` 状态，避免承诺键盘能力）；解 B 则必须实现键位。**不允许「给了 tablist 语义却没有键位」** |
| **Q-BP-06** | **Compose 语义映射的正确性无人验证**：§2.2 全部是 E1（平台 API 语义），其中三条风险最高：视觉隐藏播报能否被 TalkBack 播报、`Dialog` 关闭后是否真能让焦点回到 `FocusRequester`、`Modifier.semantics{stateDescription}` 是否被 TalkBack 朗读为「已展开」 | **解 A（先写 spec，P4 真机验证再回填）**：不阻塞 P3；但若三条中任一不成立，对应原子的无障碍字段要重写。<br>**解 B（P3 之前先做 3 个最小原型验证）**：一次 30 分钟的真机实验消除风险；代价是需要一台设备与一个空壳 App | **解 B**（成本最低、风险最大项）。验证脚本与结论回填到本节 |
| **Q-BP-07** | **`dsh-Go` / `太墟` 两列永久为空**：`docs/spec/ref/` 下没有 `dsh-go.md` 与 `taixu.md`，本表 2/9 的对照信息缺失 | **解 A（先拍，缺口记进 P1 门禁）**：M1/M2 不被阻塞；但「三家一致 → 高置信」这条质量门（`_merge-template.md:50`）在本面板永远只能用到两家，命中区/动作条这两个最争议的点少了两个样本。<br>**解 B（等两路事实库补齐再拍）**：质量最高；代价 = M1 阻塞、且 M3–M6 全排在其后 | **解 A（先拍）+ Lead 在 P1 门禁里登记缺口**；只对 `Q-BP-01`、`C-04` 两条保留「补齐后复核」的标记 |
| **Q-BP-08** | **`content-fold` 的「折叠 vs 内滚」优先级**（Q-BASE-08 未决）：内容块同时有头尾截断与块体内滚，DSH 没定义谁优先（`dsh-base.md:812-820`） | **解 A（折叠优先）**：G-04 不适用，手势统一交外层；超长块仍很高。<br>**解 B（内滚优先）**：块高可控；但出现内外双层滚动，必须实现 G-04 的到边吸收，否则滑动串联。<br>**解 C（按块类型二选一）**：最贴近 DSH 现状；规则碎片化 | **解 B**，因为 G-04 是**铁律**（user 拍板），解 A 等于让所有内容块永久绕过它。执行时要求每类块在 P3 逐块声明到边行为（`open-questions.md:89-91` 的要求） |

**附：需 Lead 动笔的文档修正（非阻塞，均在他人写作用域）**

1. `_conflicts.md`：新增 `C-BP-01`（菜单行高 dense 21/32 vs 触摸 48）、`C-BP-02`（Modal 遮罩判据 `onClick` vs `onMouseDown+target`）、`C-BP-03`（DSH 无 BottomSheet / 移动端弹层形态）、`C-BP-04`（dirty 表单关闭：丢弃 vs 拦截）；并把 C-03 的「视觉不变+扩边+8dp」倾向按 §2.1 的判定式更新（原倾向在 6px 行距下不成立）。
2. `00-global-rules.md`：G-01 补边界一句——「G-01 只约束 overlay 层，不约束内联状态条（如 connection-indicator）与布局列（如右栏），后两者占位/推挤内容是正确的」；G-10 补一句——「240dp 是上限不是下限，基座原子默认无高度动画」。
3. `open-questions.md`：Q-03 需并入 §2.1 的判定式与「48 vs 44」的口径确认（Q-03 现只有 3 个候选，缺「降级顺序」）。

---

## 7. 自检

- [x] 每个节点在对照表里有一行 —— §1 列 27 个，§2 有 27 行（含 3 个新增抽象）。
- [x] 每条「建议」都引了 G 规则或给了反证 —— 逐行核对：G-01（toast/connection-indicator）、G-02/G-03（disclosure）、G-04（content-fold）、G-05（未涉及，本面板无流式摘要）、G-08（icon-action/toast/lightbox/§2.2）、G-10（§2.3 + Q-BP-02）；其余全部写了「反证 / 反证原话」。
- [x] 没有 E2 推测出现在 §2/§3 —— 已逐条核对；两处**明确标注未取证**（Snackbar 是否拦指针、双指缩放是否要），均未写进建议依据，只写进 Q-BP-03 的「附带待验」。
- [x] 明确不做项都写了理由 —— §4 共 6 条，每条含出处 + 理由（其中 4-3 额外标了「已知缺口」）。
- [x] 阻塞项已编号 —— `Q-BP-01` … `Q-BP-08`（共 8 条）。
- [x] 一行一节点、无「其他」类目。
- [x] 未修改 `docs/spec/ref/**`、`00-global-rules.md`、`_conflicts.md`、`_merge-*.md`、`p2/_plan.md`；未改 app；未 git commit。

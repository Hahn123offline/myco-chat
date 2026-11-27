# P2 合并：composer

> 角色：P2 合并员（M4）。输入 = `docs/spec/ref/*.md` 已取证的 E0 事实；本文件**不新增调研**。
> 证据级别：§2/§3 里"某实现的做法"全部来自 ref（E0/E1 已就地标注）；**我的建议**一律给 G 规则或反证。
> 未取证 = 写「未取证」并进 §6，**不当依据**。
>
> **重要更新（本文件写作期间）**：`ref/dsh-go.md`（手机端既有答案）与 `ref/taixu.md`（Compose 原生 agent 应用）
> 两份 P1 事实库在 13:18 落地。初版只按 DSH/chat-demo/旧 myco 三列；现**已补入 dsh-Go 与 太墟 两列并重做判断**——
> 结论有实质变化（`+` 语义、主键语义、输入上限、键盘避让、命令浮层），下文一律以五路证据为准。
>
> 列约定（所有对照表统一）：
> - 参考列：`DSH`（`dsh-sidebar`/`dsh-settings`/`dsh-chat`/`dsh-mobile` 的 compose 归属段）、`dsh-Go`、`太墟`、`chat-demo`、`旧 myco`。
> - 状态列：`✅建议` = 建议采用（随本面板整批拍板）；`⏸ Q-CMP-xx` = 有互斥/未取证，必须用户拍；`❌§4-x` = 建议不做（需用户在 §4 确认）。
> - 写作用域：本文件只写 `composer`。**附件来源/选择器/上传状态机归 `attachment`（M5）**；输入区里的**呈现**归本面板（边界见 §2.6）。

---

## 1. 节点清单（先穷举，再填表）

> DSH 侧 composer 节点分布在两处：`dsh-sidebar.md` §2.4 + §2.5（8 + 13 = **21 个**，非任务书的 20 个）、
> `dsh-settings.md` §2.9/§2.10（slash/command/attach，19 个）。dsh-Go `§2.2` 6 个、太墟 `§2.4` 4 个。
> **去重/归并四处**：
> ① `composer.input.command-menu-trigger`（sidebar 路）= `composer.command.launcher`（settings 路），同一 DOM（`InputBar.tsx:441-453`），保留前者为规范 ID；
> ② `composer.attach.remove` 内含的 `composer.attach.file-remove` 并入 remove；
> ③ DSH 把 `@` 并进 slash combobox；dsh-Go 是独立 `composer.mention` 面板、太墟是独立 `MentionPopup` → 本表单列 `composer.mention.menu` 作为候选节点，拍板见 §3-25；
> ④ 旧 myco 的 `pending.attachments` 并入 `composer.attach.rail`；旧 `send-stop` 并入 `composer.input.primary`+`stop`。

| 节点 ID | 出现条件 | 参考里有没有 | 旧 myco 有没有 |
|---|---|---|---|
| `composer.input.seat` | 始终（会话 body 底部座位） | DSH §2.4；dsh-Go §2.2（输入条在布局流）；太墟 §2.4 | 未登记（§4#19 只到"输入区"层） |
| `composer.input.card` | 始终（含 inert 态） | DSH §2.4；dsh-Go §2.2；太墟 §2.4；chat-demo §2.1 | 未登记（M5 玻璃岛） |
| `composer.workspace-chip` | 仅 hero 相位 | DSH §2.4 | 无 |
| `composer.input.draft-surface` | 始终（同一 DOM，inert 退化） | DSH §2.4；dsh-Go §2.2（editor）；太墟 §2.4；chat-demo §2.1 | 未登记（M5 BasicTextField） |
| `composer.input.primary` | 始终 | DSH §2.4；dsh-Go §2.2（send-stop）；太墟 §2.4（send）；chat-demo §2.2 | 有（send-stop） |
| `composer.input.stop` | 运行中（DSH 仅 continuable 子代理；太墟 恒可见） | DSH §2.4；太墟 §2.4 | 无（§4#19） |
| `composer.voice` | 有语音模型/权限时 | dsh-Go §2.2；chat-demo §2.5；旧 §2.2（形态不同） | 有 |
| `composer.input.command-menu-trigger`（`+`） | 始终 | DSH §2.4（命令菜单）；dsh-Go §2.2（附件键）；太墟 §2.4（附件抽屉）；chat-demo §2.4（上传键） | 有（`composer.attach.open-sheet`） |
| `composer.input.file-picker` | 恒在 DOM（Web hidden） | DSH §2.4；chat-demo §2.4 | 无 |
| `composer.tools.row` | 手机端 | dsh-Go §2.2（胶囊单行）；太墟 §2.4（FlowRow）；mobile-adapt §3.1（CSS-only） | 有（32dp 胶囊行，未登记） |
| `composer.capability-chips` | 太墟：有钉选能力时 | 太墟 §2.4（能力标签 LazyRow） | 无 |
| `composer.slash.menu` | 光标前有活 `/`（dsh-Go：草稿以 `/` 开头） | DSH §2.9；dsh-Go §2.2；太墟 §2.4 | 无（无命令系统） |
| `composer.mention.menu` | 光标处是 `@` 词 | DSH 并入 slash.menu；dsh-Go §2.2（独立面板）；太墟 §2.4（MentionPopup） | 无 |
| `composer.slash.row` | 每个候选 1 行 | DSH §2.9；dsh-Go §2.2；太墟 §2.4 | 无 |
| `composer.slash.drill` | 候选可钻取（DSH 独有） | DSH §2.9 | 无 |
| `composer.slash.crumb` | source 发布面包屑（DSH 独有） | DSH §2.9 | 无 |
| `composer.command.popup` | 命令 face=popupSelect（DSH 独有） | DSH §2.9 | 无 |
| `composer.command.row` | 每个选项 1 行（DSH 独有） | DSH §2.9 | 无 |
| `composer.command.retry` | options 加载失败（DSH 独有） | DSH §2.9 | 无 |
| `composer.command.confirmation` | 选项带 `confirmation`（DSH 独有） | DSH §2.9 | 无 |
| `composer.queue.header` | 队列行数 > 1（DSH 折叠头） | DSH §2.5；dsh-Go 无（长按三选一）；太墟 无头（独立条） | 无 |
| `composer.queue.list` | 队列行数 ≥ 1 | DSH §2.5；太墟 §2.4（QueuedPromptStack）；dsh-Go 长按 sheet | 无 |
| `composer.queue.row.actions` | 队列可变 | DSH §2.5；太墟 §2.4（编辑/移除/转 steer） | 无 |
| `composer.queue.row.edit` | 常态；非纯文本 disabled | DSH §2.5；太墟（编辑 → Dialog） | 无 |
| `composer.queue.row.save` | 编辑态（DSH 行内编辑器） | DSH §2.5 | 无 |
| `composer.queue.row.cancel` | 编辑态（DSH 行内编辑器） | DSH §2.5 | 无 |
| `composer.queue.row.remove` | 常态 | DSH §2.5；太墟 §2.4 | 无 |
| `composer.queue.row.steer` | 常态；非 running disabled | DSH §2.5；太墟 §2.4（转 steer） | 无 |
| `composer.todo.header` | 计划条数 > 0（**归 chat 面板**） | dsh-Go §2.1 `chat.dock`；太墟 §2.3 `chat.msg.plan-card` | 无 |
| `composer.todo.list` | 展开且有条目（**归 chat 面板**） | dsh-Go `chat.dock`；太墟 `chat.msg.plan-card` | 无 |
| `composer.context-meter.trigger` | provider 报压力+容量 | DSH §2.5；太墟（`ContextUsageRing`，行为未取证） | 无 |
| `composer.context-meter.panel` | 打开且可用 | DSH §2.5 | 无 |
| `composer.attach.rail` | 草稿附件 > 0 | DSH §2.10；dsh-Go §2.2（tray）；太墟 §2.4（AttachmentPreviewRow）；chat-demo §2.4 | 有（待发附件条） |
| `composer.attach.rail-arrow` | 轨有溢出（DSH 独有） | DSH §2.10 | 无 |
| `composer.attach.image-thumb` | 草稿有图片 | DSH §2.10；dsh-Go §2.2；太墟 §2.4；chat-demo §2.4 | 有（chip，无预览） |
| `composer.attach.remove` | 每个附件 1 个 | DSH §2.10；dsh-Go §2.2；太墟 §2.4；chat-demo §2.4 | 有（11dp 关闭） |
| `composer.attach.file-card` | 草稿有通用文件 | DSH §2.10；chat-demo §2.4 | 有（chip，无进度） |
| `composer.attach.file-retry` | 文件上传 error | DSH §2.10；chat-demo §2.4 | 无 |
| `composer.attach.drop-target` | 文档级拖入（Web） | DSH §2.10；chat-demo §2.4 | 无 |
| `composer.attach.paste` | 草稿编辑器粘贴 | DSH §2.10；太墟 §2.4（长文本转附件）；chat-demo §2.4 | 无 |
| `composer.attach.picker` | `/file` 或 `+` | DSH §2.10；dsh-Go §2.2；太墟 §2.4；chat-demo §2.4 | 有（`composer.attach.open-sheet`） |
| `composer.attach.lightbox` | 点缩略图 | DSH §2.10；dsh-Go §2.2（tile 预览） | 无预览 |
| `composer.attach.open-sheet` | 旧 myco：点 `+` | 旧 §2.2；dsh-Go §2.2（附件面板）；太墟 §2.4（attach sheet） | 有 |
| `composer.toggle-thinking` | 旧 myco：常驻 chip | 旧 §2.2（DSH/dsh-Go/太墟 均在 model 面板） | 有 |
| `composer.toggle-websearch` | 旧 myco：常驻 chip | 旧 §2.2 | 有 |
| `composer.stats.pill` | mobile-adapt：≤767px 且有统计量 | mobile-adapt §3.1（DSH/dsh-Go/太墟 均无此形态） | 无 |
| `composer.stats.close` | 报表打开 | mobile-adapt §3.1 | 无 |
| `composer.stats.panel` | 点胶囊 | mobile-adapt §3.1 | 无 |
| `composer.stats.panel.edge-lock` | 报表打开 | mobile-adapt §3.1 | 无 |

**dsh-Go / 太墟 的 composer 节点归并映射**（它们命名不同，已映射到上表规范 ID）

| ref 节点 | 归并到 | 说明 |
|---|---|---|
| dsh-Go `composer.editor` | `composer.input.card` + `draft-surface` | 玻璃胶囊 + 1–6 行编辑器 |
| dsh-Go `composer.send-stop` | `composer.input.primary` + `composer.voice` | 四态键（发送/停止/麦克风/停听写） |
| dsh-Go `composer.attach` | `composer.input.command-menu-trigger` + `attach.rail` + `attach.picker` | `+`→附件面板、tray、移除 |
| dsh-Go `composer.mention` | `composer.mention.menu` | @ 选择器（独立面板） |
| dsh-Go `composer.slash` | `composer.slash.menu` + `slash.row` | 斜杠面板 |
| dsh-Go `composer.voice` | `composer.voice` | 听写 |
| dsh-Go `chat.dock` | `composer.todo.*` → **归 chat** | 目标条/待办条/运行条 dock 在输入条上方，但 dsh-Go 自己归 chat 面板 |
| 太墟 `composer.input` | `card` + `draft-surface` + `mention.menu` + `slash.menu` + `capability-chips` | 胶囊状态机 + @/命令 + 能力标签 |
| 太墟 `composer.send` | `composer.input.primary` + `stop` | 发送/停止/长按不等待 |
| 太墟 `composer.attach-sheet` | `attach.picker` + `attach.open-sheet` + `attach.rail` | 附件抽屉 + 预览行 |
| 太墟 `composer.queue` | `composer.queue.*` | 排队条（编辑 Dialog/移除/转 steer） |

**跨面板引用（不计入本面板节点数，只声明依赖）**

| 节点 ID | 归属 | 与 composer 的关系 | 证据 |
|---|---|---|---|
| `settings.general.enter-behavior.selector` | settings（M6） | 决定"忙时 Enter = 排队/旁路"（DSH 有；手机端 dsh-Go/太墟 用长按/按钮替代） | `dsh-sidebar.md §2.6`；`submission-policy.ts:30-39` |
| `model.trigger` | model（M5） | 输入区模型胶囊；阻塞态**必须保持可点** | `dsh-settings.md §2.8`；`InputBar.tsx:142-146` |
| `chat.list.to-bottom` | chat（M3） | 位置读 composer 高度：DSH `bottom:calc(--dsh-composer-height + 16px)`；dsh-Go 距输入条上沿 12 | `dsh-chat.md §2.10`；`dsh-go.md §0#2` |
| `chat.dock` / `chat.msg.plan-card` | chat（M3） | 目标/待办/运行条 dock 挂在输入区上方，但 dsh-Go/太墟 都归 chat 面板 | `dsh-go.md §2.1`；`taixu.md §2.3` |
| `base.layout.host.ime` | base.layout（M2） | 键盘/安全区容器契约；DSH Web 与 Android 壳均缺 | `dsh-mobile.md §3.2`；§2#22 |
| `conversation.input.attachments` 槽位 | attachment（M5） | 草稿附件轨的容器槽；来源与状态机归 M5 | `dsh-sidebar.md §2.4`（槽位表） |

---

## 2. 对照表（主线）

> `无` = 该参考**明确没有这个节点/概念**（依据其 ref 的"未见实现/不做什么"）；`未取证` = ref 没覆盖，**不得当依据**。

| 节点 ID | DSH | dsh-Go | 太墟 | chat-demo | 旧 myco | 冲突 | 建议 + 理由 | 状态 |
|---|---|---|---|---|---|---|---|---|
| `composer.input.seat` | sticky `bottom:0 z7`、参与布局流、高=内容；顶部 36px **固定 px** 渐隐；ResizeObserver 发布 `--dsh-composer-height`（`ConversationContent.tsx:45-67`） | 输入条在 `_ChatLayout` 布局流内，先排输入条、同帧排列表下沿/遮罩/斜杠面板（`chat_screen.dart:2610-2666`）；横屏 typing 时 header/dock 让位 | **占位**（底部 Column 一部分）；`onSizeChanged` → 列表 `bottomContentPadding`=输入区高度（`ChatPaneContent.kt:258,215`）；`windowInsetsPadding(chatBottomInsets())`=navBars∪ime（`:223`） | 桌面 `sticky bottom:0 z10` + 顶部 **30% 渐变**（`SessionView.module.css:32-50`）；移动 relative + safe-area-bottom | 未登记 | 无（三家 in-flow 一致；DSH 的 absolute 模式是 overlay 视图专用） | **in-flow + 列表底部留白=实时输入区高度**（dsh-Go/太墟 E0；DSH 同语义用 sticky+变量）：反证——绝对定位常驻会让最后一条消息被盖住且滚不出来（DSH `CMP-01b`）。渐隐用 36px 固定 px（chat-demo 30% 正是 DSH 反证点名的"会随草稿拉伸"）。容器细节待 M2/M3 | ⏸ Q-CMP-08 |
| `composer.input.card` | 宽=内容+32；`padding-top:8 gap:12`、圆角 panel、软阴影；点空白回焦编辑器；无工作区时整卡=选择器 | 玻璃胶囊 圆角 28（展开 lg）、单行 48=36 键+上下 6、左右留白 18/6、点胶囊空白聚焦（`composer.dart:31-35,383-386,603-606`） | `Surface(extraLargeIncreased(20dp))` + running 时 1dp primary@0.5 边 + 软阴影；内部 Column：附件行→能力标签→文本框→FlowRow 动作行（`ChatComposer.kt:160-370`） | 卡宽=列表宽−64、圆角 24、1px 边；空白点击聚焦 textarea | M5 玻璃岛未登记 | 无 | 采用一致项：**点卡片空白回焦草稿**、卡内唯一滚动盒是草稿区；圆角/阴影/token 归 `base.theme`（M1/M2） | ✅建议 |
| `composer.workspace-chip` | hero 工作区 chip + 菜单锚点 | 无（有 workspace 概念但 composer 无 chip） | 无 | 无 | 无 | 无 | **不做**：myco 无工作区实体（旧 myco 107 个 E0 节点无 workspace；`10-panels.md` 里 `workspace` ❌ 低优先）。见 §4-1 | ❌§4-1 |
| `composer.input.draft-surface` | contenteditable；`min-height:36`（hero 52）、`max-height:336`+内滚、行高 `24+delta`；到边 wheel **链式转发**；IME 组字期 Enter 不提交（`keyCode 229`+10ms 尾窗，`keymap.ts:47-52,135-146`）；上传未完成 Enter 只提示；`data-phase` 四态 | **1–6 行自适应**（`maxLines:6`），可见行数按"键盘上方 40% 高"算 **2–6**（`composer.dart:158-169,482-483`）；6 行后自身滚；`textInputAction: newline`；硬件 Enter 发送默认**关**（`hardwareEnterSends:140-141`）；行数变化 `SmoothSize` 160ms；大字号按 textScaler | 文本框内部 `verticalScroll`；`ImeAction.Default`（支持换行）；长按粘贴长文本→**转附件**；极大输入走附件；FlowRow 动作行自动换行 | mirror 撑高、`max-height:336`、min=2 行+12；Enter 发送/Shift+Enter 换行；`isComposing` 不发送；`overscroll-behavior:contain`；**无 maxLength**；移动端不自动聚焦 | 未登记 | ①**高度上限互斥**：6 行（dsh-Go，且随键盘自降）vs 14 行/336px（DSH/chat-demo）vs 不设上限只靠内滚+长文转附件（太墟）→ **Q-CMP-11**；②**到边滚动互斥**：链式(DSH)/转发拖动(dsh-Go)/contain(chat-demo)/结构上不嵌套(太墟) → **C-01 / Q-08 / Q-GO-01**（已登记） | 手机采纳 **dsh-Go**：行数随"键盘上方可用高"自适应 2–6，既给上限又给键盘避让（反证：14 行在手机上占掉半屏，dsh-Go 因此压到 6；横屏再降）；IME 组字判定抄 DSH（比只判 `isComposing` 多覆盖 Safari/旧引擎）；Enter 见 §3-7；到边随 Q-08 | ⏸ Q-CMP-11 + Q-08 |
| `composer.input.primary` | 34×34；`primaryStops = running && 无子代理 && (草稿空 \|\| 被阻塞)`；running+非空草稿=**发送**（queue/steer）；双击提交两次（机器层去重，本包不防抖） | 36×36 **四态键**（发送/停止/麦克风/停听写）；运行中空草稿→停止；运行中**有字**→发送；**长按发送**=三选一（插话 steer / 停下先处理 / 排队，`chat_screen.dart:930-990`） | 40dp 圆钮；运行中"有内容可插入"时**发送 + 停止两个并存**，否则只有停止（`ChatComposer.kt:333-368`）；长按发送="发送但不等待回答"；STEER/NEXT_RUN 双色 | 34×34 同位置互斥：`streaming`→停止，否则发送；`aria-disabled`+三重守卫 | 旧 UI 两互斥按钮；M5 单按钮切图标、无 disabled；BUG-004 首 token 前可重入 | **运行中主键语义/停止位置互斥**：同位置恒停止（chat-demo/旧；DSH 只在空草稿或阻塞时）vs 主键=发送+独立停止（太墟；DSH 子代理态同理）vs 主键四态+长按模式（dsh-Go）→ **Q-CMP-01** | 倾向 **太墟 模型**（主键在运行中仍=发送【插入当前运行】，停止键恒在旁）：五路参考里，"把发送换成停止（唯一动作）"被 taixu 反证点名——"用户无法补一句引导当前任务"；dsh-Go 的长按模式被 taixu 反证点名——"用户会误以为长按是更多选项"。若 Q-CMP-06 不做队列/steer，则退回 chat-demo 的恒停止。无论选哪条，**BUG-004 的"提交瞬间置位"必须补**（§2.7） | ⏸ Q-CMP-01 |
| `composer.input.stop` | 仅 continuable 子代理 running 才多一个独立 Stop（普通态由主键承担）；读屏分不清停谁（a11y 缺口） | 无独立停止（主键空草稿时=停止） | **独立停止键**：运行中且"有内容可插入"时与发送并存（40dp，`errorContainer` 色）；否则只有停止 | 无 | 无 | 无（可并存：主键语义之一） | 若 Q-CMP-01 选"发送+停止并存"，则采用太墟的独立停止（比 DSH 只在子代理态给更通用）；补 a11y：读屏必须能区分"停什么"（G-08 同类） | ⏸ Q-CMP-01 |
| `composer.voice` | 无 | 完整听写：麦克风**占据主键位置**、四颗键**同位同尺寸**不移动；开麦前 `unfocus` 收键盘；识别中文本框只读、主键=停听写、✕ 取消；失败 SnackBar 可跳语音设置；无模型/无权限则不显示麦克风（`composer.dart:347-375,403-446,481,610-632`） | 未取证（composer 无语音节点；`speech.call` 是通话面板） | 点按录音（idle→recording→transcribing，≤120s，转写插入光标处，非 HTTPS 置灰；**无长按**） | 点 `🎤` → 系统选音频文件 → `TranscribeAudio`；仅在输入为空时出现、与发送按钮互斥 | **形态互斥**：录音（dsh-Go/chat-demo）vs 选音频文件（旧）→ **Q-CMP-10** | 若保留语音 → 采用 **dsh-Go 形态**（四态键同位替换、开麦先收键盘、识别中只读、失败可跳设置）；反证：听写中键位若换位置手指会按错（dsh-Go 注释原话）。Compose 录音权限/ASR 通道**未取证** → 随 Q-CMP-10 | ⏸ Q-CMP-10 |
| `composer.input.command-menu-trigger`（`+`） | 28×28、工具行左端；`aria-haspopup=listbox`；**必须先回焦编辑器**再 `toggleSource('command')`；打开的是**只有 command 一个 source 的 slash 候选（combobox，焦点留输入框）**，不是 command-popup（`InputBar.tsx:294-302,441-453`→`apply.ts:506-518`→`controller.ts:187-205`） | **`+` = 附件键** 36×36（`InkResponse radius:20`）→ 打开附件面板（相册/拍照/文件，`attachments.dart:19-24`、`plus_panel.dart:141-143`） | **`+` = 附件抽屉**（照片/文件/扩展/草图 + 紧凑行，`ChatAttachSheet.kt:1-204`；E1 `ComposerQuickActions.kt:19` 注释） | `+` 位是附件上传按钮 → 直接开系统文件选择器 | `+` → 附件 ModalBottomSheet（拍照/相册/文件三选一） | **`+`=命令候选（DSH）vs `+`=附件入口（dsh-Go/太墟/chat-demo/旧，4/5）** → 互斥 → **Q-CMP-02** | **改用多数派：`+` = 附件入口**（打开 M5 的附件面板/内建选择器）。DSH 是唯一把 `+` 做成命令菜单的实现，其理由（命令需常驻入口）在 dsh-Go/太墟 里由输入触发词 `/` 承担；反证：`+` 与 `/` 两套菜单 = 两套候选与键位（DSH `SET-124/125`）。旧 `+`→sheet 的直连形态因此**被采纳**，不再是"旧特例" | ⏸ Q-CMP-02 |
| `composer.input.file-picker` | 隐藏 `<input type=file multiple hidden>`；subagent disabled；change→清空 value→`intakeFiles` | 无隐藏 input；媒体选择用 `image_picker` / `file_picker`（`attachments.dart:152-165`，全仓 file_picker 13 处） | 系统 Photo Picker（E2 未直查）+ VM 处理（复制/压缩/编码，`ChatComposer.kt:140-159`） | 隐藏 input + 上传按钮；选完即传（3 并发）；格式/大小/数量校验整批 | 无（走系统选择器 + `AttachmentSave` 落盘） | 无 | 行为契约采纳：同一文件可重选（清空 value）、上限预检在**入轨前**、子代理只收图片；**实现归 M5**（Android 无 `<input>`） | ✅建议（依赖 M5） |
| `composer.tools.row` | `.tools gap:12`（窄卡 <560 收 8）；`+` 28、主键 34；窄卡允许 wrap | **单行胶囊**：左 `+`、右侧主键、footer 模型胶囊；四颗键**位置尺寸固定**（听写时也不换位）；狭屏/横屏自动降行数 | 左侧 3 个 **40dp** 图标（+ / 模型 / 审批）、右侧发送/停止 40dp、能力标签 `LazyRow`、动作行 `FlowRow`（**自动换行**，大字号更明显） | 功能行 margin/gap 8；窄屏收起 toggle 文字 | 32dp 胶囊行 | 单行 vs 换行（不同实现选择，非必然互斥：太墟只在窄/大字号换行） | 默认**单行 + 键位固定**（dsh-Go 反证：听写时键位若变会按错；`composer.dart:36-39`）；大字号/极窄允许动作行换行（太墟 `FlowRow` 证据）。间距/裁切兜底见 §3-5 | ✅建议 |
| `composer.capability-chips` | 无 | 无 | 能力标签 `LazyRow`（skill=`primaryContainer`、其他=`tertiaryContainer`、钉选"已固定"徽标；单击移除/取消钉选，`ChatComposer.kt:193-236`） | 有 thinking/search 两个 toggle（功能不同） | 有 thinking/websearch toggle（不同） | 无 | **首版不做**（§4-9）：依赖 skill/能力钉选（`skill` 面板在 `10-panels.md` 低优先）；旧 myco 的 thinking/websearch 是另一类（推理开关，归 model/设置，见 §2 后两行） | ❌§4-9 |
| `composer.slash.menu` | `/`、`@` 候选 combobox：`max-height:400` + 按 composer 上方空间 clamp；**焦点留输入框**、`aria-activedescendant`；↑↓ 移高亮、Enter 选中、Tab 钻取、Esc/Shift+Tab 关；查询细化 stale-while-revalidate；`frozen` 时冻结 | `/` 开头触发；浮层**宽=屏宽−24、离输入条上沿 6、maxHeight 137、行高 52**；行单击=执行/填入；方向键+Enter；**Esc 关闭并记住本次草稿不再自动弹**；点外关；与 `@` 面板**同槽**（同时出现会重叠，见反证） | `matchingCommands` 非空即弹 `SlashCommandPopup`（`ChatComposer.kt:126-131`）；与 `MentionPopup` 各自独立 | 无 | 无 | 无（三家都有，形态大同小异；**唯一分歧是 DSH 把 `@` 并进同一 combobox，dsh-Go/太墟 是两个独立面板**，见 §3-25） | 采用三家共有行为：浮层贴输入条上方、**焦点留输入框**、方向键+Enter、Esc/点外关闭、行整行可点；尺寸取 dsh-Go 的手机值（行 52、maxHeight 137 量级）而非 DSH 桌面值。**反证**：抢焦点→打字进搜索框（DSH `SET-118`）；逐字清列表→闪（`SET-111`） | ✅建议 |
| `composer.mention.menu` | 并入 slash.menu（同一 combobox，`@` 是 source） | **独立 @ 面板**（与 slash 同槽）：光标处 `@` 词触发、无结果显示关闭、行 ≥44、方向键/Enter/Esc、点外关、选中→token 插入并**焦点回输入框**、列表随输入过滤（`mention_picker.dart:35,91,177-186`） | 独立 `MentionPopup`：**仅光标在末尾且折叠时**弹出；删除一个 mention 整段删除；`MentionVisualTransformation` 高亮 | 无 | 无 | 实现形态分歧（可并存）：统一 combobox vs 两个独立面板 | 采用 **在 slash.menu 内合并两个 source**（DSH 做法）：反证来自 dsh-Go 自己——两个面板同槽，**同时出现会重叠**（`dsh-go.md §2.2 composer.slash` 反证）；合并后用户只需一套键盘语义。若将来 @ 需要独立视觉（成员卡片）再拆 | ✅建议（§3-25） |
| `composer.slash.row` | 行 34、图标 14、标题 40% ellipsis、共享高亮（指针与键盘一套）、钻取行显示 Tab 键帽 | 整行可点、行高 52（`minimumSize`）、`pressed` 水波、建议文案三级色 | 行 `Semantics(button)`+命令名（未逐条） | 无 | 无 | 无 | 采用：整行可点 + 指针/键盘**一套高亮**（反证 DSH `SET-112`：两套高亮会出现双高亮）；行高取手机值（dsh-Go 52） | ✅建议 |
| `composer.slash.drill` | 18×18 独立命中区，钻取不选中；Tab 也能钻取 | 无 | 无 | 无 | 无 | 无 | **首版不做**（§4-10）：4/5 无钻取；myco 的 `@`（会话/成员）与 `/`（命令）都是扁平列表（dsh-Go 多会话时平铺会话、太墟列成员），没有目录层级需求。若将来 @ 需要文件树再补 | ❌§4-10 |
| `composer.slash.crumb` | 组头面包屑、当前层 disabled、不进 ↑↓ 循环 | 无 | 无 | 无 | 无 | 无 | **首版不做**（§4-10，随钻取） | ❌§4-10 |
| `composer.command.popup` | popupSelect 命令弹窗：`max-height:320`；**持焦点**（搜索框）、本地过滤、单飞提交、失败保留、高风险确认、错误条重试 | 无 | 无 | 无 | 无 | 无 | **首版不做**（§4-11）：4/5 无 popupSelect；需要参数选择的命令（如 `/model`）由既有面板（model/设置）承担，避免在 composer 里造第二套"持焦点搜索浮层"。反证（DSH `SET-118/119`）说明它确有必要时该长什么样，留给将来 | ❌§4-11 |
| `composer.command.row` | 行 12、badge/detail、打开时高亮停在「当前值」、失败行不可选 | 无 | 无 | 无 | 无 | 无 | **首版不做**（§4-11） | ❌§4-11 |
| `composer.command.retry` | 错误条 retry，保留搜索词 | 无 | 无 | 无 | 无 | 无 | **首版不做**（§4-11） | ❌§4-11 |
| `composer.command.confirmation` | 选项带 confirmation 时叠 RiskConfirmation；取消只退确认 | 无 | 无 | 无 | 无 | 无 | **首版不做**（§4-11）；高风险确认能力归 `approval` 面板 | ❌§4-11 |
| `composer.queue.header` | `rowCount>1` 才出；36px；默认折叠；编辑/忙强制展开；折叠=上箭头（dock 向上生长） | 无队列 UI（运行中长按发送键出三选一"插话/停下先处理/排队"） | 无折叠头：队列是**独立悬浮条**（`QueuedPromptStack`），与 plan 同级、在输入胶囊上方；注释称"不计入输入框渐隐高度"但在 bottom chrome 内 | 无 | 无 | 无（3/5 有排队语义，形态不同） | 采用**太墟形态**（独立条、无折叠头）：手机上一个折叠头会多占一行；反证：队列不可见时用户不知道"刚才那句发没发"（taixu `TX-QUEUE-01`）。排队语义本身见 Q-CMP-06 | ⏸ Q-CMP-06 |
| `composer.queue.list` | `max-height:180px` 内滚；行 36、预览 200 字；pending 回显 `role=status`；无 overscroll | 无 | 队列条参与 bottomContentPadding；多条时滚动**未核实**（E2） | 无 | 无 | 无 | 采用：队列条固定在输入胶囊上方、**计入列表底部留白**（否则最后一条被遮挡）；封顶取 DSH 180px 量级（可作 Q-05 参数）；到边随 Q-08 | ⏸ Q-CMP-06 |
| `composer.queue.row.actions` | 28×28 圆形簇；busy 全禁用（串行化）；回显行 3 键禁用占位 | 无 | 每条 3 键：编辑 / 移除 / 转 steer（`QueuedPromptStack`），行高未核实（E2） | 无 | 无 | 无 | 采用太墟的 3 动作集合；命中区按 Q-03 全局口径（DSH 28、太墟未核实）；busy 串行化抄 DSH（反证：连点会发两个删除请求） | ⏸ Q-CMP-06 |
| `composer.queue.row.edit` | 常态；非纯文本 disabled | 无 | 编辑 → **独立 Dialog** `QueuedPromptEditDialog`（`QueuedPromptEditDialog.kt:1-51`） | 无 | 无 | 无（Dialog vs 行内编辑，可并存） | 采用**太墟 Dialog**：手机上行内编辑器要处理"隐藏焦点/强制展开"（DSH 因此加 `disabled={interactionActive}`）；Dialog 少一半状态。反证（DSH）：保存失败必须留在编辑态，Dialog 天然满足 | ⏸ Q-CMP-06 |
| `composer.queue.row.save` | 行内编辑器保存键；空文本 disabled；失败留在编辑态 | 无 | 无独立 save 键（确认在 Dialog 内） | 无 | 无 | 无 | 采用太墟：Dialog 确认即保存（无独立行内 save/cancel 节点）→ 这两个原子在本方案下**不独立存在** | ⏸ Q-CMP-06 |
| `composer.queue.row.cancel` | 行内编辑器取消键 | 无 | Dialog 取消（`QueuedPromptEditor` 回滚） | 无 | 无 | 无 | 同上（并入 Dialog） | ⏸ Q-CMP-06 |
| `composer.queue.row.remove` | 常态；失败 toast | 无 | 移除键（`QueuedPromptStack`） | 无 | 无 | 无 | 采用 | ⏸ Q-CMP-06 |
| `composer.queue.row.steer` | 常态；`!running` disabled | 无（排队是长按三选一之一） | "转为插入"= `convertQueuedPromptToSteer` | 无 | 无 | 无 | 采用（与 DSH 同义）；`!running` 时 disabled 并说明原因 | ⏸ Q-CMP-06 |
| `composer.todo.header` | 输入座位上计划 dock，默认折叠，>0 才出现 | **归 chat**：`chat.dock` 待办条（输入条上方，参与布局流、横屏打字折叠） | **归 chat**：`StickyPlanBar` 钉在输入区上方（计入 bottom chrome 高度） | 无 | 无 | 无（归属分歧：DSH 放 composer，dsh-Go/太墟 放 chat） | **归 chat 面板（M3）**：两家手机端都把计划/待办放在 chat 的 dock/plan 条，且它依赖 plan 工具（`plan` 面板低优先）→ composer 不定义。反证：放 composer 会在没有 plan 能力时留下死节点 | ❌（移交 M3） |
| `composer.todo.list` | 展开体 180px 内滚、单行 ellipsis 无展开 | `chat.dock`（截断到 N 行） | `PlanBoard` 看板（列表内） | 无 | 无 | 无 | 同上（移交 M3） | ❌（移交 M3） |
| `composer.context-meter.trigger` | 需 provider 报压力+容量；环 14 + 数字；无数据不渲染 | 无（用量在 `trajectory.sheet`） | `ContextUsageRing` 在动作行（与停止钮 spacedBy 6，`ChatComposer.kt:334-368`）——**行为未取证**（点击/展开/数据源） | 无 | 无 | 无 | 条件采纳：等 provider 能报容量/压力再做（Q-CMP-06）；形态 C 浮层（G-01）。太墟只证明"环可以放在 composer 动作行" | ⏸ Q-CMP-06 |
| `composer.context-meter.panel` | portal fixed 264px、Escape/点外关、三段比例、无焦点管理 | 无 | 未取证 | 无 | 无 | 无 | 条件采纳（Q-CMP-06）；补焦点进入/归还（G-01 + 反证：只声明 dialog 不给焦点，键盘用户开完不知道焦点在哪） | ⏸ Q-CMP-06 |
| `composer.attach.rail` | 卡内横轨：项高 64（图 64×64 / 文件 240×64）；**新增自动滚到末端**；竖滚转横滚并消费；`role=group` | tray：高 56 + overhang、横向 ListView gap 8、`ExpandReveal` 280ms 进出、展开时玻璃长高但**文字行不跳**（`composer.dart:569-570`）；tile 右上 ×、tile 整块可点预览 | `AttachmentPreviewRow` 在文本框**上方**（显式理由："避免被胶囊底栏或键盘区遮挡"，`ChatComposer.kt:171-175`）；缩略 48dp 级；横向滚；处理中顶部 14dp 转圈+文案 | 附件条在 textarea 上方横滚、图 64、文档卡 240×≥64；出现/消失使卡高突变（无过渡） | pending 条横滚 + 末尾「清空」 | 无（形态一致：都在输入区上方/内部横排；高度 56/64 量级） | 采用：轨在**输入文本上方**（三家一致，太墟给了反证：放底部动作行会把发送钮挤移位）；新增滚末端（DSH 反证：不滚末端用户以为没加上）；竖滚转横滚并消费（DSH 反证：不消费会滚走会话）。旧「清空全部」不保留（§3-19） | ✅建议 |
| `composer.attach.rail-arrow` | 左右 24×24 绝对定位箭头，按溢出显隐 | 无（原生横滑） | 无（原生横滑） | 无 | 无 | 无（4/5 无箭头） | **不做**（§4-12）：手机横滑是原生手势、dsh-Go/太墟 都不给箭头；DSH 的反证（隐藏滚动条 → 不知道能横滚）进验收：**真机做一次"发现性"检查**，若实测发现不了再加 | ❌§4-12 |
| `composer.attach.image-thumb` | 64×64 按钮，点开共享 ImageLightbox；预览中附件被移除自动关 | tile 单击=预览、**长按=图片动作菜单（保存/分享）**（`attachments.dart:100-160`→`chat.msg.image`） | 缩略图 48dp 级；预览未逐条取证 | 草稿图片可点开原图/灯箱**未取证**；失败整卡可点重试 | chip 无预览 | 无 | 采用：点缩略图开**全屏预览**（形态 C，反证：64×64 裁切后难分辨）；dsh-Go 的长按动作菜单（保存/分享）归 `attachment`/`chat.msg.image`，不在 composer 定义 | ✅建议 |
| `composer.attach.remove` | 18×18 右上；图片 hover/focus/粗指针显示；文件失败态常显红底；无二次确认 | tile 右上 × 独立命中；tile 长按另有动作菜单 | 删除键（`ChatAttachment.kt:372` 用 48dp 助手） | 18×18、hover 才显、失败常显 | 11dp 关闭（缺陷） | 无 | 采用 DSH 显隐规则 + 太墟的**大命中**（48dp 助手）：反证——触屏不常显删不掉、18dp 太小（Q-03 待拍） | ✅建议（Q-03） |
| `composer.attach.file-card` | 240×64 三态（uploading/ready/error）+ 2px 进度条；进度条 `aria-hidden` 无 progressbar 角色（缺口） | 无独立文件卡（tray tile 统一）；远程宿主暂存不了文件时附件面板只剩相册/拍照 | 二进制文件走附件（VM 复制/压缩/编码），无三态卡 | 文档卡 240×≥64；上传中 spinner 蒙层/失败红边 | chip 无进度 | 无 | 采用 DSH 三态（文件必须能看见"在传/失败"）；补 `progressbar` 语义（G-08 同类：运行态必须有 a11y 文案） | ✅建议 |
| `composer.attach.file-retry` | 失败时正文变重试按钮（≈190×64）；重试中回 uploading | 无（未取证） | 无（处理失败 → VM notice → Toast，`ChatScreen.kt:300-310`） | 失败整卡可点重试 | 无 | 无 | 采用：重试入口必须显式（Toast 会消失）；反证 DSH：失败只红描边没重试 → 只能删掉重选（大文件重传） | ✅建议（依赖 M5） |
| `composer.attach.drop-target` | 文档级 drag + 全屏 DropOverlay（pointer-events:none、role=status） | 无 | 无 | 同样 document 级 DragMask | 无 | 无 | **首版不做**（§4-3）：两家手机端都无拖放；机制是 Web document 事件。若 M5 真机取证 Android 拖放可行，来源归 M5、本面板只出接入点 | ❌§4-3 |
| `composer.attach.paste` | 编辑器 PASTE_COMMAND：file 项→`intakeFiles`；**同时有文本则文本也插入**；只有文件才 preventDefault | 未取证 | **长按粘贴长文本 → 转附件**（`PasteLongTextReceiver` + `addTextAttachment`，`:314`）；短文本正常粘贴 | `onPaste` 取 `clipboardData.files`；文本走原生 | 无 | 无 | 采用 DSH 分流（反证：同时 preventDefault 文本会丢"图片+说明"里的说明）；**长文本转附件**采纳太墟（理由：20 万字符日志直接进输入框会测量卡顿——taixu 注释原话） | ✅建议（依赖 M5） |
| `composer.attach.picker` | `/file` 命令 + 隐藏 input → `inputHub.pickFiles()`（`hub.ts:202-214`→`facade.ts:592-615`→`view-binding.ts:88-97`）；multiple、无 accept | `+`→附件面板（相册/拍照/文件三源）；`image_picker`/`file_picker` | `+`→attach sheet（照片/文件/扩展/草图四源）+ 系统 Photo Picker；VM 复制/压缩/编码 | 上传按钮→系统文件对话框 | `+`→附件 sheet（拍照/相册/文件） | 无（入口形态随平台；来源集合不同：DSH 1 源 / dsh-Go 3 源 / 太墟 4 源） | 采用：**`+` 打开 M5 的附件面板**（来源集合由 M5 按 C-09 定：相机/相册/文件，是否加"扩展/草图"随 myco 能力）；multiple/无 accept 由 M5 实现 | ✅建议（依赖 M5） |
| `composer.attach.lightbox` | 共享 ImageLightbox：全屏遮罩+原图+关闭；无 pinch/zoom/滑动切换；role=dialog | tile 单击预览（图片查看器全屏；`chat.msg.image`） | 未取证 | 未取证 | 无预览 | 无 | 采用，组件归 `base.primitives`（M1）；形态 C——反证：把图展开在 composer 里会把输入区顶走、丢编辑位置 | ✅建议 |
| `composer.attach.open-sheet`（旧） | 无此节点（DSH 附件入口在命令行） | 附件面板（相册/拍照/文件） | attach sheet（照片/文件/扩展/草图） | 无（上传键直连系统选择器） | 有：`+`→ModalBottomSheet 三源 | 与 `+` 语义绑定（Q-CMP-02） | **采纳并升格为 `+` 的规范行为**（4/5 实现）：sheet/面板由 `attachment`（M5）实现，composer 只拥有 `+` 键与"点 `+` → 请求 M5 打开面板"的接线 | ⏸ Q-CMP-02 |
| `composer.toggle-thinking`（旧） | 无 composer 开关（推理等级在 model 面板） | 无（模型/思考在 `model.sheet`） | 无（`model.sheet` 模型/思考强度/预设） | 有「深度思考」toggle（34 高、aria-pressed、localStorage、创建回复时快照） | 有 FilterChip（旧）/「深度思考」胶囊=开模型面板（M5） | 位置互斥（composer 内 vs 面板内）；已登记 **C-08 / BUG-003** | **移出 composer**：思考强度归 model 面板（C-08 方向"只放模型×强度"）；BUG-003 要求先定唯一真源。三家（含两家手机端）都不在 composer 放开关注入开关 | ⏸（C-08） |
| `composer.toggle-websearch`（旧） | 无 | 无 | 无 | 有「智能搜索」toggle | 有 FilterChip/胶囊 | 同上 | 移出 composer（联网归 tool/settings）；理由同 BUG-003/L1 | ⏸（C-08） |
| `composer.stats.pill` | 无（桌面只读统计行） | 无（用量在 trajectory sheet） | 无（trajectory sheet） | 无 | 无 | 无 | **不做**（§4-2）：仅 mobile-adapt 插件有；myco 无 token/费用真源 | ❌§4-2 |
| `composer.stats.close` | 无 | 无 | 无 | 无 | 无 | 无 | **不做**（§4-2） | ❌§4-2 |
| `composer.stats.panel` | 无 | 无 | 无 | 无 | 无 | 无 | **不做**（§4-2，属 trajectory 面板） | ❌§4-2 |
| `composer.stats.panel.edge-lock` | 无 | 无 | 无（但太墟有 Compose 侧真故障面证据：主列 `overscrollEffect=null`，因为 EdgeEffect 残留下 pending scroll 会触发 LazyListState 校验闪退，`ChatMessageList.kt:145-146`） | 无 | 无 | 无 | **不做**（§4-2）；教训转给 Q-08/Compose 实现：内滚容器到边策略在 Compose 上要用 `overscrollEffect`/嵌套滚动显式处理，不能只靠默认 | ❌§4-2 |

### 2.1 附：七个必答点的结论（每条指回上表行 / 证据）

**① 输入区状态机（主按钮 / 次要操作 / 发送与停止是否同位置互斥）**

| 状态 | 进入条件（E0） | 主按钮 | 次要操作怎么放 | 依据 | 状态 |
|---|---|---|---|---|---|
| hero / 空白 | 无会话或空白会话 | disabled 发送 | `+` 常驻、草稿可用 | DSH §2.4 seat/card | ✅（工作区触发器不做） |
| 空闲 | 草稿空/有字 | 空→disabled；有字→发送 | `+`、模型胶囊、语音（若有） | 5 家一致 | ✅ |
| 生成中 + 草稿空 | `running && draft===''` | **停止** | 输入不禁用 | DSH/dsh-Go/太墟 | ✅（5 家一致） |
| 生成中 + 有字 | `running && draft!==''` | DSH=发送(queue/steer)；dsh-Go=发送（长按三选一）；太墟=**发送+停止并存**；chat-demo/旧=停止 | 停止键位置见 Q-CMP-01 | 五路证据 | ⏸ **Q-CMP-01** |
| 初始化中 / 未连接 | 会话 initializing / 连接断开 | 禁发；dsh-Go 仍可打字但提示"连接后才能发送" | 主键禁用 | 太墟 `initializing`（`ChatComposer.kt:113-122`）；dsh-Go `:416-417` | ✅ |
| 附件处理中 | 附件正在复制/压缩/编码 | **不可发送** + 输入区顶部转圈 | 处理中提示 | 太墟 `:140-159` | ✅（依赖 M5） |
| 排队 | 队列非空 | 仍=发送 | 队列条固定在输入胶囊上方、计入列表底部留白 | DSH/太墟；dsh-Go 长按 mode | ⏸ Q-CMP-06 |
| 听写中 | voice listening/transcribing | 主键=停听写；✕ 取消 | 文本框只读、键位不换位 | dsh-Go | ⏸ Q-CMP-10 |
| 锁定/只读会话 | 会话只读 | dsh-Go 整条换成 `ReadOnlyBar` | — | dsh-Go `chat_screen.dart:2263-2264` | ✅ |
| 子代理会话 | `subagent!==null`（DSH） | 主键不承担 Stop；独立 Stop 承担 | 文件 input disabled（只收图片） | DSH §2.4 | ⏸ Q-CMP-06 |
| 提交中 | `phase ∈ adjudicating/claimed/submitting`（DSH frozen 槽） | 主键禁用/幂等 | 菜单冻结；claim 后 Esc 不释放（只能退格 token） | `contract/input.ts:282,291-308`；`facade.ts:78-82` | ✅（BUG-004 修法见 §2.7） |

结论：**"发送/停止同位置互斥"不是共识**——太墟 让两者并存（运行中且有字），dsh-Go 用长按模式解决同一问题；只有 chat-demo/旧 是"同位置单键"，DSH 仅在空草稿/阻塞/非子代理时同位置。是否需要"运行中继续发"取决于 Q-CMP-01/Q-CMP-06。

**② 多行高度（自适应上限 / 内滚 vs 整体撑高 / 与列表联动）**
1. **上限三档不是一档**：DSH/chat-demo = 14 行（336px）；**dsh-Go = 6 行，且可见行数随"键盘上方 40% 高"降到 2–6**（`composer.dart:158-169`）；太墟 = 不设行数上限、文本框内滚 + **长文本走附件**；旧未登记 → **互斥**（Q-CMP-11）。手机端建议取 dsh-Go：它同时解决"上限"与"键盘避让"（反证：14 行在手机上占掉半屏；横屏更窄，dsh-Go 明确因此压行数）。
2. **超出后内滚，不整体撑高**：DSH（336 后内滚）、chat-demo（336 后内滚）、dsh-Go（6 行后内滚）、太墟（内部 `verticalScroll`）**四家一致** → 采用内滚。反证（chat-demo §2.1）：无限长高会把消息列表挤没，用户看不见自己正在回复的那段。
3. **撑高时列表让位**：dsh-Go（输入条在布局流、同帧排列表下沿）与太墟（`onSizeChanged` → 列表 `bottomContentPadding`）**都把"输入区实时高度"喂给列表底部留白**；DSH 用 sticky+`--dsh-composer-height` 达到同样效果 → 采用"in-flow + 列表底部留白=输入区高度"（Compose 天然对应 `contentPadding`）。反证：绝对定位常驻会让最后一条被盖住且滚不出来（DSH `CMP-01b`）。
4. **渐隐遮罩**：DSH 36px 固定 px；chat-demo 30%；dsh-Go/太墟 的底部渐隐在 chat 列表侧（`bottomContentFade = composerHeight + navFade`，`ChatPaneContent.kt:169-177`）→ 采用 **36px 固定 px**（DSH 反证：百分比会随草稿长高拉伸）；Compose 侧由 chat 列表的 fade 参数承担（M3）。
5. **与列表跟随的联动（硬契约）**：输入区高度变化必须可被 chat 读到（dsh-Go 同帧布局；太墟 `bottomContentPadding` + `ChatImeFollowLatest`；DSH ResizeObserver 变量）。**反证**：写死像素（mobile-adapt 旧实现 `bottom:216px`，`R1/:96`）会在附件/队列/键盘变化后悬空或压住输入框。通道归 M2/M3（Q-CMP-08）。

**③ 键盘与安全区（输入区/列表/回到底部；imePadding 等价物；横屏）**
- **两家手机端有 E0 做法**：dsh-Go = `Scaffold.resizeToAvoidBottomInset:true` + 自定义 `MultiChildLayout` 同帧排版（`chat_screen.dart:1871,2610-2666`），可见行数按"键盘上方 40% 高"降档，**横屏打字时 header/dock 让位**（`:1841-1859,2498-2559`）；太墟 = `windowInsetsPadding(chatBottomInsets())`，其中 `chatBottomInsets = navigationBars ∪ ime`（`ChatPaneContent.kt:223`、`ChatScreen.kt:85`），列表底部留白=输入区高度，键盘弹起且仍在跟随时 `delay(80)` 后贴底（`ChatImeFollowLatest.kt:12-18`）。
- **DSH Web = 0 处理**（`packages/client`+`apps/web` 全量 grep 无 `visualViewport`/`safe-area-inset`/`viewport-fit`），只有 IME 组字层仲裁；**Android 壳 = 完全没做键盘避让**（无 `windowSoftInputMode`/`WindowInsets`/visualViewport 桥）——这是**缺口不是做法**（`dsh-mobile.md §3.2 base.layout.host.ime` 字段 1 明写"未见实现"）；mobile-adapt 写了 `dvh`+`env()`+`visualViewport` 但 `viewport-fit` 缺失 → `env()` 恒 0，输入区本身没做键盘避让。
- **结论**：契约采 **dsh-Go/太墟 的"insets 感知 + 输入区在布局流内 + 列表底部留白=输入区高度"**；Compose 等价物 = `WindowInsets.ime` / `imePadding`。三条硬要求：①IME 弹出时输入座整体上移、**草稿不重挂载**（反证：重挂载丢 IME 组字与选区，DSH `InputBar.tsx:9-13` 明写"same DOM inert"）；②列表与输入区同时收缩、跟随判据不变（C-07；太墟 `ChatImeFollowLatest` 是现成做法）；③**"回到底部"按钮随键盘上移且不被输入卡遮住**（dsh-Go 的回底钮距输入条上沿 12、随输入条上移，`CL:1097-1101`；G-01：浮层不占位，反证：按钮被输入卡盖住时用户必须先收键盘）。insets 组合归 **M2 base.layout**（Q-CMP-07）。
- **横屏**：dsh-Go 有明确做法（横屏打字 header/dock 让位、行数降档，`chat_screen.dart:1841-1859`；不锁方向、靠窗口分类降级）；DSH/mobile-adapt 无 composer 横屏规则；Android 壳只 `configChanges` 不重建 → 采用 dsh-Go 的"横屏让位 + 行数降档"，量值随 Q-CMP-07。

**④ 命令与触发面板（`+` vs 命令 popup；`@`/`/`；过滤；键盘导航）**
- **五路事实**：`/` 与 `@` 在 DSH 是**一个 combobox 的两个 source**（焦点留输入框、`aria-activedescendant`、↑↓ 移高亮、Enter 选中、Esc/点外关、stale-while-revalidate、`frozen` 冻结）；dsh-Go 是**两个独立面板**（同槽、Esc 记住本次草稿不再弹、`/` 面板 maxHeight 137 行高 52、`@` 行 ≥44、token 化后焦点回输入框）；太墟是 `SlashCommandPopup` + `MentionPopup`（`@` 仅光标在末尾且折叠时弹、删 mention 整段删）；chat-demo/旧 无命令系统。
- **`+` 不是命令入口**：dsh-Go/太墟/chat-demo/旧 四家都把 `+` 给附件；只有 DSH 用 `+` 打开 command source 的 combobox（跨包 E0 链已核实：`InputBar.tsx:294-302,441-453`→`apply.ts:506-518`→`controller.ts:187-205`，且它打开的是 slash 候选菜单、**不是** popupSelect 弹窗）。→ Q-CMP-02。
- **命令参数选择（popupSelect）只有 DSH 有**（`/model` 等：持焦点、搜索、单飞提交、失败保留、风险确认）→ v1 不做（§4-11），命令映射到既有面板。
- **建议**：v1 = **`/` + `@` 两个输入触发，合并进同一个候选组件（DSH 做法，dsh-Go 自证两面板会重叠）+ `+`=附件**；键盘行为取三家共有集合；面板尺寸取手机值（行 52、maxHeight 137 量级）。
- 反证：抢焦点→打字进搜索框（DSH `SET-118`）；逐字清列表→闪（`SET-111`）；两套面板同槽重叠（dsh-Go 反证）；`+`/`/` 两套命令菜单→两套键位（DSH `SET-124/125`）。

**⑤ 草稿（保留范围 / 附件随草稿 / 清空时机）**
- 切会话保留：DSH 输入机按会话（`InputState` per-session，`contract/input.ts:274-289`）+ DOM 驻留（`dsh-sidebar.md §0.2`、验收 `CHR-01b`）；dsh-Go **草稿按会话保存/恢复**（`composer.dart` 状态层 `draft_store`）；chat-demo 按 `sessionId` 存在 zustand Map（含 `null` 新会话，`drafts.ts:25-31`）→ **三家一致：按会话保留，切会话不丢**。
- 杀进程/重进：chat-demo 明确不落 localStorage/IndexedDB（`drafts.ts:25-28`）；DSH/dsh-Go 未取证 → Q-CMP-04。
- 附件随草稿：DSH `InputState.attachmentIds`；dsh-Go tray 在会话草稿里、移除后"草稿保留"、发送成功才清空（`composer.attach` 字段 5）；chat-demo drafts 存 files → **三家一致：附件随草稿按会话保留**（采用）。
- 清空时机：dsh-Go 明确"**发送成功后清空**"（`attachments.dart` / 状态层）；DSH 只有提交前 `draftSnapshot` + 结算携带 draft 做后缀保留/claim 重入（`contract/input.ts:299-303,324`）；chat-demo 未取证 → 采用 dsh-Go + BUG-004 的"失败回滚保留"：**成功才清、失败保留**。

**⑥ 附件入口在输入区的呈现（与 attachment 面板的边界）**
- **composer 拥有（呈现 + 接入点）**：`+` 键（打开 M5 附件面板）、草稿附件轨（rail/thumb/file-card/remove/file-retry）、轨道横滚与新增滚末端、粘贴/长文本转附件的**接入点**、灯箱**触发**（组件归 base.primitives）。
- **attachment（M5）拥有（来源 + 状态机）**：三路来源的选择器实现与取消（`dsh-settings.md §6.1`）、intake 限制与拒绝文案（§6.2：子代理只收图片、图片上限整批拒绝、通用文件无客户端限制）、附件项状态机（§6.3）、上传通道/进度/取消/重试（§6.4）、内建相机/相册选择器（C-09 用户点名；dsh-Go 的相册/拍照/文件三源、太墟的四源可作为形态参考）。
- **边界结论**：`+` 属 composer（一个键 + 接线），**面板/来源集合属 M5**；rail 只定义"长什么样、怎么滚、怎么移除"，不定义"何时进入草稿/上限/上传状态"（G-00b）。
- 冲突提醒：若 M5 再给一个常驻"相机/相册"按钮，会与 `+` 形成双入口 → 在 Q-CMP-02 一起拍。

**⑦ 发送防重入（BUG-004）**
- 缺陷事实（E0）：`send()` 只挡 `streaming`，而 `streaming` 要等首个 delta → 首 token 前窗口可重入（`legacy-myco.md §5.3-a`；`MA:942-944`）。
- 参考做法：DSH 输入机有**单一 frozen 在飞槽 + `phase=submitting`**（`contract/input.ts:291-308`；`facade.ts:78-82` 把 adjudicating/submitting 都映射成 frozen），keymap 忽略 `event.repeat`（`keymap.ts:154`，长按 Enter 不连发）；dsh-Go 提交中主键禁用、停止后转 busy 转圈（`composer.dart:442-446`）、长按模式先校验"草稿/连接代次"再发送（`chat_screen.dart:976-989`）；太墟**幂等由 `doSend` 的 `initializing` 守卫**并明确验收"连点发送不重复提交"（`TX-SEND-03`）；chat-demo `aria-disabled`+三重守卫。
- **建议（写进 P3 字段）**：提交瞬间置位 `submitting`（= 快照草稿/附件 + 单一在飞 attempt）→ 期间主键禁用且幂等、Enter/点击/长按都进同一 attempt、忽略 repeat；**失败回滚**（清置位、草稿与附件保留、错误可重试）；**成功才清草稿**。依据：`bugs.md` BUG-004 已定"提交瞬间置位、失败回滚" + DSH frozen 槽（E0）+ 太墟 `TX-SEND-03` + legacy 反证（两轮提问/两份回答）。验收：连点两次只产生一条；失败后草稿/附件仍在；长按 Enter 只发一条。

---

## 3. 补充项（不互斥的差异，不进冲突列）

| # | 节点 | 谁有、谁没有 | 是否采纳 |
|---|---|---|---|
| 1 | 输入高度上限 | DSH/chat-demo 14 行/336px；dsh-Go 6 行且随键盘降 2–6；太墟不设上限+长文转附件 | 手机采纳 dsh-Go（互斥项见 Q-CMP-11） |
| 2 | 占位/贴底机制 | dsh-Go/太墟 in-flow + 列表底部留白=输入区高；DSH sticky+高度变量 | 采纳；Compose 用 `contentPadding` 方案（M2/M3 通道待定） |
| 3 | 渐隐遮罩 | DSH 36px 固定；chat-demo 30%；手机端在 chat 列表侧做 fade | 采纳 36px 固定（反证：百分比会拉伸） |
| 4 | 工具行布局 | dsh-Go 单行四键同位；太墟 FlowRow 会换行；mobile-adapt 强制 nowrap | 默认单行+键位固定（dsh-Go 反证）；大字号/极窄允许换行（太墟证据） |
| 5 | 输入条内工具行宽度兜底 | mobile-adapt：触发器 max-width 112 + min-height 44；dsh-Go 靠胶囊留白；太墟 40dp 图标 | 采纳"触发器限宽 + 主键固定位"；具体 44 与否随 Q-03 |
| 6 | 命中区尺寸 | DSH 28/34；dsh-Go 36（图标 44）；太墟 40（拒绝 48 平台默认，Q-TX-02）；mobile-adapt 44；chat-demo 34 | **本面板不自定**：登记实例（主键 34/36/40；`+` 28/36/40；rail remove 18/48；队列动作 28/未核实），随全局 **Q-03** 拍板 |
| 7 | Enter / Shift+Enter | 桌面 DSH/chat-demo：Enter 发送；手机 dsh-Go `textInputAction:newline` + 硬件 Enter 发送默认**关**；太墟 `ImeAction.Default`（换行）+ 发送按钮 | **手机采纳"软键盘 Enter=换行、发送走按钮"**（dsh-Go/太墟 E0）；桌面键盘语义若将来支持，保留 dsh-Go 的 `hardwareEnterSends` 开关式 |
| 8 | IME 组字判定 | DSH：`isComposing \|\| keyCode 229 \|\| compositionend 后 10ms`；chat-demo：只 `isComposing` | 采纳 DSH（多覆盖 Safari/旧引擎，`keymap.ts:47-52`） |
| 9 | 到边滚动 | DSH 链式；dsh-Go 转发"拖动增量"（不转惯性，`OF:8-47`）；太墟结构上不嵌套（`overscrollEffect=null` 防闪退）；chat-demo contain | 不在本面板定，随 **C-01 / Q-08 / Q-GO-01**（三大输入已齐） |
| 10 | 长文本粘贴 | 太墟：长按粘贴长文本→附件；DSH：文件+文本都处理；chat-demo：files；dsh-Go 未取证 | 采纳太墟（反证：20 万字符直接进输入框会测量卡顿）；文件粘贴分流抄 DSH |
| 11 | 附件轨方向 | 四家都在输入区上方/内、横向；高度 56（dsh-Go）/64（DSH、chat-demo）/48 缩略（太墟） | 采纳横向轨 + 新增滚末端；高度取 56–64 量级（M5 定） |
| 12 | `+` 的附件键尺寸 | DSH 无（命令键 28）；dsh-Go 36；太墟 40；旧 32 | 随 Q-03；建议 36–40（dsh-Go/太墟 区间） |
| 13 | 附件删除按钮 | DSH 18（hover/focus/coarse）；dsh-Go tile 右上 ×；太墟 48dp 助手 | 采纳"视觉小 + 命中大"（太墟 48dp 助手 + DSH 显隐规则）；实现随 Q-03 |
| 14 | 文件卡三态 | DSH/chat-demo 有；dsh-Go/太墟 无（靠处理中转圈+Toast） | 采纳 DSH 三态（文件在传/失败必须可见可重试）；补 progressbar 语义 |
| 15 | 上传中禁止发送 | DSH `uploadsPending`；chat-demo `draftReady=false`；太墟 `attachmentsProcessing` 期间转圈+不可发 | 采纳（三家一致） |
| 16 | 队列形态 | DSH 折叠头+180px 内滚；太墟 独立悬浮条+Dialog 编辑；dsh-Go 长按三选一 | 采纳太墟形态（无折叠头、Dialog 编辑）；封顶/到边随 Q-08；是否做队列见 Q-CMP-06 |
| 17 | 命令面板尺寸/位置 | DSH 桌面 clamp 400/320；dsh-Go 手机 maxHeight 137、行 52、离输入条 6；太墟 同槽 | 手机采纳 dsh-Go 值；位置"贴输入条上方"三家一致 |
| 18 | 命令发现性 | DSH 有 `+` 常驻命令入口；dsh-Go/太墟 只有输入 `/` | 采纳 dsh-Go/太墟（无 `+` 命令菜单）；**风险**：命令不可发现 → 验收加"首次使用能否发现命令"（真机） |
| 19 | 旧 myco「清空全部附件」 | 旧独有 | **不采纳**：与单项移除重复、破坏性且无撤销；若用户要，需新节点 + 撤销窗口 |
| 20 | 语音形态 | dsh-Go 听写四态；chat-demo 点按录音；旧 选音频文件 | 采纳 dsh-Go（互斥项 Q-CMP-10） |
| 21 | 旧 myco 思考/联网 toggle | 旧在 composer；DSH/dsh-Go/太墟 在 model 面板/工具层 | 移出 composer（C-08 / BUG-003 已登记） |
| 22 | 发送后焦点 | chat-demo：桌面 focus、移动 blur（收键盘）；dsh-Go 发送后未取证；DSH 未取证 | **未取证**，不采纳也不写反证；随 Q-CMP-01/Q-CMP-07 |
| 23 | 移动端统计胶囊/报表 | 仅 mobile-adapt | 不做（§4-2） |
| 24 | 桌面拖放 DropOverlay | DSH/chat-demo（Web document 事件） | 首版不做（§4-3） |
| 25 | `@` 面板是否独立 | DSH 合并进 combobox；dsh-Go/太墟 两个独立面板 | 采纳 DSH 合并（dsh-Go 自证两面板同槽会重叠） |
| 26 | 能力标签（skill 钉选） | 仅太墟 | 首版不做（§4-9） |
| 27 | 长按发送 / 长按模式的显隐 | dsh-Go 长按发送=三选一（隐藏 affordance）；太墟 长按发送=不等待回答（功能，不是模式菜单） | 采纳太墟的"长按=不等待回答"**只在有该需求时**；**不采纳** dsh-Go 的"长按出三选一"（taixu 反证：用户会以为长按是更多选项） |
| 28 | 空草稿时的主键 | DSH：disabled 发送；dsh-Go：空草稿+无附件 → 只显示麦克风（有语音时）；太墟：空输入禁用 | 采纳：无语音时 disabled；有语音时空草稿主键=麦克风（dsh-Go） |

---

## 4. 明确不做（参考里有的）

| # | 能力 | 出处 | 不做的理由 |
|---|---|---|---|
| 4-1 | `composer.workspace-chip`（hero 工作区 chip + 无工作区整卡触发器） | `dsh-sidebar.md §2.4` | myco 无工作区实体：旧 myco 107 个 E0 节点无 workspace；`10-panels.md` 里 `workspace` ❌ 低优先。G-00：无依据不引入；将来 `workspace` 面板立项时回填 |
| 4-2 | `composer.stats.*`（手机统计胶囊+报表+到边锁） | `dsh-mobile.md §3.1` | 仅 mobile-adapt 一路有（DSH/dsh-Go/太墟 均无此形态）；myco 无 token/费用真源；统计属 `trajectory` 面板。**教训保留**：其"只挡冒泡挡不住默认滚动"的缺陷 + 太墟"Compose 主列 `overscrollEffect=null` 防闪退"证据转给 Q-08 |
| 4-3 | `composer.attach.drop-target`（文档级拖放 + 全屏 DropOverlay） | `dsh-settings.md §2.10`；`chat-demo.md §2.4` | 两家手机端（dsh-Go/太墟）都无拖放；机制是 Web document 事件。首版不做；若 M5 真机取证 Android 拖放可行，来源归 M5 |
| 4-4 | `composer.input.file-picker`（隐藏 `<input type=file>`） | `dsh-sidebar.md §2.4` | Web 专有元素；Android 由 M5 调系统/内建 picker。**能力不丢**，只是实现形态不照搬 |
| 4-5 | 桌面键盘加速键（Ctrl/Cmd=旁路发送等） | `keymap.ts:136-158`；`submission-policy.ts` | Android 无对应常驻硬件键；dsh-Go 把硬件 Enter 做成**默认关闭的可选项**、太墟不提供 → 首版不做 |
| 4-6 | `composer.slash.drill` + `composer.slash.crumb`（钻取/面包屑） | `dsh-settings.md §2.9` | 4/5 无；myco 的 `/`（命令）与 `@`（会话/成员）都是扁平列表（dsh-Go 平铺会话、太墟列成员），无目录层级需求 |
| 4-7 | `composer.command.popup` / `.row` / `.retry` / `.confirmation`（popupSelect 命令弹窗） | `dsh-settings.md §2.9` | 4/5 无；需要参数选择的命令由既有面板（model/设置）承担；避免在 composer 里造第二套持焦点搜索浮层。高风险确认能力归 `approval` 面板 |
| 4-8 | `+` 作为"命令候选菜单"（DSH 的用法） | `InputBar.tsx:294-302,441-453`→`apply.ts:506-518`→`controller.ts:187-205` | 被 Q-CMP-02 否决（4/5 用 `+`=附件）；命令入口改为输入 `/`（dsh-Go/太墟 同做法）。**该 E0 链保留**作为"若将来要做常驻命令入口"的实现参考 |
| 4-9 | `composer.capability-chips`（能力/技能钉选标签） | `taixu.md §2.4`（`ChatComposer.kt:193-236`） | 仅太墟有；依赖 skill/能力钉选（`skill` 面板低优先）；首版不做 |
| 4-10 | `composer.attach.rail-arrow`（轨翻页箭头） | `dsh-settings.md §2.10` | 仅 DSH 有；手机横滑是原生手势（dsh-Go/太墟/chat-demo 均无箭头）；DSH 的发现性反证转为验收项（真机实测） |
| 4-11 | 旧 myco `+`→附件 sheet 的三源 sheet 本体 | `legacy-myco.md §2.2`/§2.3 | **入口形态采纳**（见 §2 `+` 行），但 sheet 的**本体实现**归 M5（attachment 面板），composer 不重复定义；这里"不做"的是 composer 自有的 sheet，不是不做附件导入 |
| 4-12 | 旧 myco「清空全部附件」 | `legacy-myco.md §2.2` | 与单项移除重复、破坏性且无撤销；若用户坚持，需新节点 + 撤销窗口 |

> 注：§4-1…§4-12 每一条都对应 §2 表里的 `❌§4-x` 行；用户确认"不做"后，P3 才可省略。

---

## 5. 新增节点（参考里都没有、myco 要新设计）

| 节点 ID | 为什么需要 | 依据（用户要求 / 平台规范 / 我方约束） |
|---|---|---|
| `composer.submit.inflight` | "提交中"的可**见**可**播报**状态：主键禁用外观、失败回滚、可重试。参考里只有"内部相位/禁用"，没有统一的可见态：DSH 有 frozen 相位但无可见态、无 `aria-busy`；dsh-Go 有"停止后转 busy 转圈"；太墟只有幂等守卫与验收要求 | `bugs.md` BUG-004（user 已定"提交瞬间置位、失败回滚"）；G-08（运行态必须有 a11y 文案）；反证：没有可见态时用户会以为没点上而重复点 |
| `composer.draft.persistence`（条件） | 草稿跨进程恢复（落库 + 恢复提示/过期策略） | 仅当 Q-CMP-04 选"持久化"；chat-demo 明确不持久化、DSH/dsh-Go 未取证 → 属新设计 |
| `composer.attach.entry`（条件） | 移动端常驻附件入口（相机/相册），与 `+` 的仲裁 | 仅当 Q-CMP-02 选"双入口"或 M5 按 C-09 的内建选择器要求常驻入口；用户点名"导入媒体缺内建 UI"（C-09/M-02） |

> 说明：初版曾把"IME/安全区契约"与"手机队列形态"列在 §5；现两者在 dsh-Go/太墟 都已有 E0 做法（见 §2.1③、§2 queue 行），**不再是"参考里都没有"**，已移出 §5。

---

## 6. 待确认（阻塞项）

> 编号 `Q-CMP-nn`。每条给"两解 + 后果"，用户一句话能拍。**容器/层叠类注明"待 M2 base.layout 结论"**，本文件不自行决定。
> 与既有编号的关系：C-01/Q-08（到边滚动）、C-08/BUG-003（思考/联网开关）已在主台账，本文件只引用不重开。
> **编号空缺说明**：`Q-CMP-03`（命令浮层做几套）与 `Q-CMP-05`（手机软键盘 Enter 语义）在五路证据落地后**已由证据定案**，
> 分别落到 §4-7（popupSelect 不做）与 §3-7（手机 Enter=换行），故编号保留空缺、不再作为阻塞项。

| ID | 问题 | 两解及各自后果 | 建议 |
|---|---|---|---|
| **Q-CMP-01** | 运行中（生成中）的主键语义与停止键位置 | **A. 同位置恒停止**（chat-demo/旧；DSH 空草稿态）：运行中只有停止，想插话/排队只能等或停。**B. 发送+独立停止并存**（太墟）：运行中主键仍=发送（插入当前运行），旁边恒有停止键；可随时补一句。**C. 主键四态+长按发送出模式菜单**（dsh-Go）：一屏只一颗键，但"长按=插话/停下先处理/排队"是隐藏 affordance。 | **B（太墟 模型）**：两家手机端参考（dsh-Go/太墟）都不约而同保留"运行中能继续发"，而 taixu 的反证点名 A（"无法补一句引导当前任务"）与 C（"用户会误以为长按是更多选项"）；B 的代价只是多一颗键（太墟用 40dp×2 + 6dp 间距解决）。若 Q-CMP-06 决定**不做队列/steer**，则退回 A（chat-demo） |
| **Q-CMP-02** | `+` 按钮与附件入口的边界 | **A. DSH**：`+` = 命令/触发候选（combobox），附件是其中一行；命令有常驻入口，但"选附件"多一步、且只有 DSH 一家这么做。**B. 多数派**：`+` = 附件入口（打开 M5 附件面板），命令靠输入 `/` 触发（dsh-Go/太墟/chat-demo/旧 四家）。**C. 双入口**：`+` 附件 + 独立"命令"键。 | **B**：4/5 证据（含两家手机原生参考）+ 用户点名需要"内建媒体选择器"（C-09），`+`→附件面板是最短路径；命令仍可达（输入 `/`），代价是**命令发现性下降**（验收加真机项）。C 只有在 M5 要求常驻相机/相册入口时才考虑（同时要定双入口仲裁） |
| **Q-CMP-04** | 草稿生命期：杀进程/重进要不要保留？ | **A. 只内存/会话内**（chat-demo 现状；DSH/dsh-Go 未取证）：切会话保留、杀进程丢失（含附件）。代价：打了一半的长消息被系统杀进程吞掉。**B. 落库恢复**：跨进程恢复草稿与附件；代价=新节点 `composer.draft.persistence` + 过期/清理 + 附件本地文件生命周期（与 Q-LEG-06 孤儿文件同题）。 | **A（首版）**：四家中只有 chat-demo 明确表态（不落盘），其余未取证 → G-00 不引入持久化；在 P3 写死"杀进程丢失"边界。若要跨进程恢复走 B + 新节点 |
| **Q-CMP-06** | agent 形态相关节点是否进 composer 首版：队列（太墟/DSH 有，dsh-Go 用长按 sheet）、计划 dock（**两家手机端都归 chat**）、上下文占用（DSH 有、太墟只有环且行为未取证）、子代理态（仅 DSH 明确） | **A. 都不做**：composer 只留"输入+发送+附件+命令"，最轻；但 `10-panels.md` 把"排队/命令/触发词"列为 composer 现状缺口。**B. 全做**：与 DSH 对齐；但计划/子代理依赖 plan/subagent 面板（低优先），且手机形态无先例。**C. 只做队列**：运行中追加/编辑/移除/转 steer（太墟形态），计划归 chat、上下文占用与子代理态延后。 | **C：只做队列**（3/5 有排队语义，太墟给了完整手机形态：悬浮条 + Dialog 编辑 + 转 steer）；**计划 dock 明确归 chat**（dsh-Go `chat.dock` / 太墟 `StickyPlanBar` 都在 chat 层）；上下文占用等 provider 能报容量/压力再说；子代理态随 subagent 面板（低优先）延后 |
| **Q-CMP-07** | IME/安全区/横屏的容器契约 — **待 M2 base.layout 结论** | **A. 容器层统一**：base.layout 提供"insets 感知的底部贴底区"，composer 只消费（dsh-Go/太墟 都是这个结构：输入区在布局流内、insets 由外层给）。**B. composer 自管**：自己读 `WindowInsets.ime`/`imePadding`。后果：M2 未定前先自管会有两套 inset 真源。 | **A**：G-00b 单一事实源 + M2 是容器契约归属；composer 只声明三条行为（输入座底部含 ime inset、列表与浮层随之收缩、回到底部按钮上移）。**横屏与手机输入行数上限一并由 M2 定**（dsh-Go 有横屏让位做法，可作输入） |
| **Q-CMP-08** | 输入座贴底方式与"输入区高度→列表"的发布通道 — **待 M2 base.layout / M3 chat 结论** | **A. in-flow + 列表底部留白=实时输入区高**（dsh-Go 同帧布局 / 太墟 `bottomContentPadding`；Compose 天然）。**B. overlay/absolute + 预留 padding**：反证（DSH `CMP-01b`）最后一条消息会被盖住且滚不出来。 | **A**：三家 in-flow 一致（DSH 用 sticky 达到同效果）。具体"谁测高、谁写 padding"归 M2/M3；本面板只要求"高度变化可被 chat 读到" |
| **Q-CMP-09** | 附件 rail 与 `attachment` 面板（M5）的边界与上传交互（进度、取消上传 Q-SET-12、内建选择器入口） | **A. 按 §2.6 切分**：rail 呈现归 composer，来源/状态机/上传/面板归 M5，取消上传由 M5 定。**B. 全归 M5**：composer 只留 `+` 一行；但"附件条长什么样"是输入区布局的一部分。 | **A**：用户明确要求"输入区里的 rail 归 composer，来源与状态机归 attachment"；**需 M5 合并时确认同一分割线**（否则两个合并任务会各写一套 rail） |
| **Q-CMP-10** | 语音输入保留吗？形态取哪个？ | **A. 不做**：旧 myco 有、M5 已丢（D6）；不做手机只剩打字。**B. 做，采 dsh-Go 听写**（麦克风占主键位、四键同位同尺寸、开麦先收键盘、识别中只读、✕ 取消、失败可跳语音设置）：最完整；代价=Compose 录音权限/ASR 通道**未取证**（太墟 Compose 侧只有 `speech.call` 通话面板）。**C. 做，采 chat-demo 点按录音**：状态机更简单。**D. 做，采旧"选音频文件"**：实现最省但已过时。 | **B（若保留）**；若担心 ASR 通道，退 C。D 不做（不是语音输入，是上传音频转文字） |
| **Q-CMP-11** | 输入区高度上限取哪档？（此前未单列，现已暴露为互斥） | **A. 336px/14 行**（DSH/chat-demo 桌面值）：一屏能看更长草稿；但手机上占掉接近半屏，横屏更糟。**B. 6 行、且随"键盘上方 40% 高"降 2–6**（dsh-Go）：手机友好、与键盘避让同源；代价=长 prompt 只能内滚。**C. 不设行数上限、靠内滚 + 长文本转附件**（太墟）：最少规则；代价=内滚条很长、长文本依赖"转附件"策略。 | **B**：手机端唯一同时给了"上限"与"键盘自适应"的实现；A 的桌面值在手机上被 dsh-Go 明确否证（14 行占半屏），C 依赖额外的长文转附件策略（可作补充，不作上限方案） |

> 附注（给 Lead）：
> 1. dsh-Go/太墟 两份事实库在本文件写作期间落地（13:18），初版结论已按五路证据重做；两者各自的待拍项也在引用范围：
>    `Q-GO-01`（到边转交 vs G-04）、`Q-GO-02`（命中区 32/36/44/48 口径）、`Q-GO-03`（过程折叠形态）、`Q-GO-10`（横屏让位）、`Q-TX-02`（拒绝 48dp 平台默认）。
> 2. 本文件新登记 4 个冲突（Q-CMP-01/02/10/11）+ 引用 2 个已登记（C-01/Q-08、C-08/BUG-003）；需 Lead 把 Q-CMP-01/02/10/11 补进 `_conflicts.md`（本文件无权改）。
> 3. `composer.input.command-menu-trigger` 与 `composer.command.launcher` 是同一按钮被两条 P1 路线各登记一次；P3 写 `panels/composer.md` 时只能留一个 ID（本文件保留前者），否则重复定义（G-00b）。

---

## 7. 自检

- [x] 每个节点在对照表里有一行（**49 个 composer 节点**；跨面板引用 6 个另表；去重/归并 4 处已注明；dsh-Go/太墟 节点映射表另列）
- [x] 每条「建议」都引了 G 规则或给了反证（G-00/G-00b/G-01/G-02/G-03/G-08 + 各 ref 原文反证 + `SET/CMP/DEMO/GO/TX` 验收编号）
- [x] 没有 E2 推测出现在 §2/§3（未取证一律写「未取证」并进 §6：横屏量值、草稿持久化、发送后焦点、语音 ASR 通道、队列多条滚动）
- [x] 明确不做项都写了理由（§4 共 12 项）
- [x] 阻塞项已编号（`Q-CMP-01`…`Q-CMP-11`，共 9 条——已合并/删除原 Q-CMP-03 命令浮层与 Q-CMP-05 手机 Enter，二者现由证据定案进 §4/§3；Q-CMP-07/08 标注"待 M2"）
- [x] 互斥才进冲突列：本表冲突 **6 处**（新登记 4：Q-CMP-01/02/10/11；引用已登记 2：C-01/Q-08、C-08/BUG-003），其余差异在 §3（28 条）
- [x] 五路参考（DSH / dsh-Go / 太墟 / chat-demo / 旧 myco）每行都有取值或明确「无/未取证」
- [x] 未修改 `ref/**`、`00-global-rules.md`、`_conflicts.md`、`_merge-*.md`、`p2/_plan.md`、其它 `p2/*.md`；未改 app；未 commit
- [ ] **待 Lead 补**：Q-CMP-01/02/10/11 需登记进 `_conflicts.md`；Q-CMP-07/08 需 M2 base.layout 先出结论

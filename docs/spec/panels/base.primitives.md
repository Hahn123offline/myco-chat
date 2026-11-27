# 面板规格：base.primitives

> P3 写手产物。输入：`_p3-brief.md`、`schema-node.md`、`00-global-rules.md` G-00…G-18、`decision-pack.md` §6（D-01…D-20）+ §6.1/§6.2、`p2/base.primitives.md`（27 节点）+ 必要时回查 `ref/dsh-base.md`、`ref/dsh-go.md`、`ref/taixu.md`、`ref/legacy-myco.md`。
> 本文件是 P4 实现 `base.primitives` 的唯一依据：数值全部给依据；我方取值一律标「我方取值（P4 校准）」。
> **本面板只写原子层**：容器几何、遮罩 alpha、overlay 宿主、insets 由 `base.layout` 负责；具体面板（chat/composer/settings…）负责装配与业务语义。

---

## 0. 一句话 + 范围

**一句话**：`base.primitives` 是全部面板复用的 27 个原子交互单元——把「命中盒、关闭语义、语义树、动效、状态」五套契约一次写死，让上层面板只做装配，不再各自发明穿透规则。

**负责**（本文件定死）：

1. 原子自身的几何（行高、内边距、图标尺寸、圆角、间距）与命中区落点（G-11/G-16 的 Compose 落地）。
2. 原子自身的手势、行为、视觉状态、动效、无障碍语义、边界态。
3. 浮层家族（Modal / BottomSheet / Menu / Lightbox / Toast）的**关闭语义**与**焦点契约**（D-03/D-08 的翻译），不含遮罩视觉与宿主 insets。
4. 「明确不做」的技术断言（供 P4 静态检查，见 §4）。

**不负责**（不在本文件，去相邻面板）：

| 事项 | 归属 |
|---|---|
| 遮罩颜色/alpha（0.32，D-05）、overlay 宿主、页面栈 `List<PanelRoute>`、预测式返回 | `base.layout` |
| 面板级布局（聊天列宽、设置页分组卡、输入区高度与键盘避让） | `chat` / `settings` / `composer` |
| 过程行/工具行/组头在会话流里的**出现条件与接线**（本文件只给原子契约；含 D-11=C 形态 D 的切换点与流式期封顶值 cap） | `chat` |
| 显式保存 vs 即时写的**按页声明**（D-10）与 dirty 拦截的触发时机 | `settings` / `composer`（`dialog-dismiss` 原子只提供拦截能力） |
| 附件进度、上传状态机 | `attachment` |
| 颜色/字号/动效 token 的具体值 | `base.theme`（本文件只引用语义名与对比关系） |

**明确不做（P2 §4 摘要，一行）**：hover-card 与 `copyable` 整卡复制、tooltip 的 hover 形态与 `openOnClick` 钉住、menu 子菜单、markdown-link 修饰键分支、`json-tree` 整棵原语、`disclosure.leading`（只有 16×16 图标可点）形态、`previewChevron` 悬停换图标——**均不实现**（依据 P2 §4-1…4-7 + D-09 触摸-only）。另：`tab-strip` 的 roving/方向键支线随 D-09 一并砍掉；`ProgressPill` 在 P2 的 27 节点清单与五路参考库中**均不存在**（已 grep 无命中），本面板不建该节点（见 §5 TBC-02）。

**边界自问**：一个东西该写进本文件吗？判据 = 「换一个面板还会一模一样地用到它吗」。会 → 原子（本文件）；只在一种装配下出现 → 上层面板。

---

## 1. 布局与网格

### 1.1 线框（三个状态 + 三个关键解剖）

**(a) 过程行 `disclosure.row`（折叠 / 展开 / 长内容）**

```
折叠（高恒 32dp，命中盒 = 行宽+2×8 × 32）：
|<-- 8 bleed -->|<-------------- 行内容 -------------->|<-- 8 -->|
                ┌────────────────────────────────────┐
                │ (24 图标) gap8  思考   13/20  ⇣16  │  ← 头部 sticky 锚
                └────────────────────────────────────┘
                  ↑ 内容贴面板 16dp 缘 → 命中盒从 x=8 起

展开（形态 A：不封顶，头部吸附）：
                ┌────────────────────────────────────┐  ← 头部 sticky，页面底色遮挡
                │ (24)  思考                          ⇡ │
                ├────────────────────────────────────┤
                │        展开体（缩进 32 = 24+8）    │
                │        随外层会话滚，无内滚          │
                └────────────────────────────────────┘

长内容（>240dp）：
                ┌────────────────────────────────────┐  ← 头部 y 不变
                │ (24)  思考                          ⇡ │
                ├────────────────────────────────────┤
                │  1…N 屏正文；收起入口永远在吸附头    │
                └────────────────────────────────────┘
```

**(b) 图标动作条 `icon-action-bar` + 内容块 `content-fold`**

```
轮尾最后一条助手消息（D-19：常驻、只轮尾）：
正文 ………………………………………………
┌──────────────────────────────────────┐  ← 40dp 行，top 4
│ [40][40][40][40][40]                 │  ← 键 40×40、图标 20、净距 0（靠每键 10 内边距）
└──────────────────────────────────────┘
  ↑ Bleed 8：首键图标左缘与正文行首对齐

内容块（限高内滚 280dp + 井底「查看全部」）：
┌──────────────────────────────────────┐
│ 代码/终端/结果（可纵向内滚，到边吸收）│  ← max 280dp
│ …                                    │
├──────────────────────────────────────┤
│ 查看全部 ›                           │  ← 44dp 整条可点 → 全文页（独立 route）
└──────────────────────────────────────┘
```

**(c) 弹层家族与关闭路径**

```
Modal（确认/危险，D-03）        BottomSheet（轻选择，D-03）      Lightbox（图片）
┌──────── 遮罩 scrim ────────┐   ┌──────── 遮罩 ────────┐        ┌──── 黑底全屏 ────┐
│    ┌───────────────┐       │   │                      │        │            [44 X]│
│    │ 标题      [40]│       │   │ ┌──────────────────┐ │        │                  │
│    │ 内容（可内滚） │       │   │ │ ▔▔ 把手 36×4     │ │        │     [ 图片 ]     │
│    │ [取消] [危险] │       │   │ │ 标题        [40] │ │        │                  │
│    └───────────────┘       │   │ │ 选项行 44 …      │ │        │  双击缩放/双指    │
│ 关闭：遮罩(D-08)+返回      │   │ └──────────────────┘ │        │  缩放态禁翻页     │
│ 无下滑关（D-03 A）         │   │ 关闭：遮罩+返回+下滑  │        │ 关闭：遮罩+返回+X │
└────────────────────────────┘   └──────────────────────┘        └──────────────────┘
```

**(d) P4 契约演示页（建议 P4 建 `AtomGalleryScreen`，本文件 §4 的截图验收都跑这一页）**

```
状态 1 空：      「本页无内容」空态（原子不渲染时空态是可判定的：文档说明每个原子的空态）
状态 2 常态：    分组竖排：行类 / 按钮类 / 选择类 / 输入类 / 浮层入口 / 内容块 / 语义
状态 3 极端内容：标题 200 字 + 正文 1MB（内滚）+ 字号 200% + 深色 + 横屏（高 412dp）
```

### 1.2 网格表（全部 dp；无依据的值标「我方取值」）

| 项 | 值 | 依据 |
|---|---|---|
| 面板内容水平内边距 | 16 | DSH 设置行 `padding:16px 0`（`ref/dsh-base.md` 引 `T/client/FontSizeRow.module.css:5-11`，E0）；dsh-Go sheet 内容留白 20（`dsh-go.md` 引 `app_sheet.dart:25-29`，E0）→ 原子按 16，sheet 另加 20 |
| **行级原子高**（会话行/菜单行/设置行/开关行整行） | **44** | G-16；dsh-Go 会话行 44（`dsh-go.md` 引 `session_tile.dart:51`，E0） |
| **过程行高**（思考行/工具行） | **32**（最小高，实际 = max(32, 文字高+8)） | G-16；dsh-Go `StreamRow.minHeight=32`（`dsh-go.md` 引 `SR:29-30,46-68`，E0） |
| 过程行间距（节距 = 32+间距） | 间距 **4** → 节距 36 | P2 §6.1-3 修正为「节距 36–38dp」；G-11 降级顺序①「先加大 g」；**我方取值（P4 校准）**：4dp 取 P2 区间下沿，保留密度 |
| **密集图标高**（操作条每键） | **40×40**，图标 20 | G-16；太墟 `ComposerSendButton.kt:20-24`（40dp=命中，E0，且其注释明确拒绝 Material 48） |
| 图标动作条行内上下留白 | top 4 | dsh-Go 操作条 `Padding(top:4)`（`dsh-go.md` 引 `message_actions.dart:193-208`，E0） |
| 图标动作条横向 Bleed | 8（左右） | dsh-Go `Bleed(horizontal:8)` + `_RenderBleed.hitTest`（`BL:6-13,87-96`，E0；`dsh-go.md` §5.3-c） |
| **输入框/主操作高** | **48** | G-16 |
| 行内片段 / 次级可点 | **≥36** 命中 | G-16 |
| 正文内链接 | **≥44** 命中 | G-16；dsh-Go 链接热区补到 `kMinInteractiveDimension`（`relay_credit.dart:135-139,236-259`，E0） |
| 只读徽标（Pill 非交互） | 视觉 24、无命中 | DSH `P/Pill.module.css:1-14`（24px，E0） |
| 交互 chip（Pill/Chip） | 视觉 32、布局命中 36 | DSH 视觉 24（E0）不够触摸 → 抬到 G-16 的 36；**我方取值（P4 校准）** |
| 分段控件容器高 | 44（段命中共高） | dsh-Go 分段控件最小高 44（`dsh-go.md` §5.1 引 `AT:321-331`，E0） |
| 圆角：chip/徽标 | 999（全圆） | DSH `P/Pill.module.css:1-14`（`border-radius:999px`，E0） |
| 圆角：行卡/输入/浮层容器 | 16 | 太墟 `RuntimeDesignTheme.kt:17-31`（卡/菜单/输入统一 16dp，E0）；与 M5 现状一致 |
| 圆角：菜单行（仅宽屏 popup） | 12 | DSH `P/Menu.module.css:28-45`（E0） |
| 间距：图标—文字 | 8 | dsh-Go 过程行 24+gap8（`SR:11-33`，E0） |
| 间距：并排图标键 | 0（靠每键 10 内边距形成视觉间距） | dsh-Go 操作条 36 键净距 0（`message_actions.dart:96-105`，E0）；G-16「间距由内边距给出」 |
| 展开体缩进（过程行） | 32 = 图标 24 + gap 8 | dsh-Go `StreamRow.indent=32`（`thinking_pill.dart:225-227`，E0） |
| 弹层内容水平留白 | 20（sheet）/ 24（modal 外边距） | dsh-Go `kSheetGutter=20`（E0）；DSH `P/Modal.module.css:6-15` 24px（E0） |
| Modal 最大宽 | `min(380, 屏宽 − 48)` | DSH `min(380px,100%)` + 四周 24（E0） |
| BottomSheet 最大宽（宽屏） | 640 居中 | **我方取值（P4 校准）**：dsh-Go 只写「按 WindowClass 调宽」（`app_sheet.dart:12`，E0 行为/E1 数值），640 为 M5 现有 sheet 宽上限上取，P4 按 600/840 断点校准 |
| BottomSheet 最大高 | `窗口高 × 0.8`，正文内滚、按钮钉底 | 太墟 E1 `UI_GUIDELINES.md:65`（`taixu.md` 引）；配 G-13 |
| Toast 最大宽 / 底距 | 480 / 宿主给底部 offset | **我方取值（P4 校准）**：Toast 属 overlay，偏移归 `base.layout`（G-01） |
| 内容块内滚限高 | 280 | dsh-Go `_kDetailMaxHeight=280`（`dsh-go.md` 引 `tool_row.dart:32,465`，E0） |
| 代码块横向渐隐 | 24 | dsh-Go `HorizontalEdgeFade 24px`（`edge_fade.dart:53-67`，E0） |
| 长文本截断 | 头 60 行 + 尾 140 行、总 16000 字 | dsh-Go `long_text.dart:14-19`（E0） |
| 字号：正文 / 行标题 / 过程行 / 说明·chip | 14·22 / 14·20 / 13·20（secondary） / 12·18 | DSH 13/20（G-09 + `ReasoningRow.module.css:41-51`，E0）；Pill 12/18（`P/Pill.module.css:1-14`，E0）；Input 14/22（`P/Input.module.css:25-34`，E0） |
| 最小按钮宽 | 64 | dsh-Go 主按钮最小宽 64（`dsh-go.md` §5.1 引 `AT:258`，E0） |
| 图标：过程行 leading / 操作条 / 行内次级 / chevron | 24 / 20 / 20 / 16 | dsh-Go `SR:11-33`（E0）、`message_actions.dart`（E0）、太墟 `ChatMessageBubbles.kt:644`（chevron 16，E0） |
| 按压反馈 | 缩放 0.97 + 轻触感；滚动中按下 90ms 才显形 | dsh-Go `pressable.dart:97-108`、`motion.dart:162`（E0）；P2 §3-13 已采纳 |

> **字号与「内容字号轴」**：myco 跟随系统 `fontScale`（大字号验收按 2.0×），但应用内「内容字号」轴（DSH `--dsh-content-font-size`）**不作用于**按钮/chip/开关标签/图标尺寸（Q-BASE-05 口径，P2 引 `dsh-base.md` §Q-BASE-05）。过程行行高随 fontScale 线性增高（`height = max(32, 文字高 + 8)`，我方取值口径，G-02 只要求恒高）。

### 1.3 与容器的关系（M2 覆盖栈 / 返回 / insets）

| 问题 | 契约 | 依据 |
|---|---|---|
| 原子在栈里的位置 | 原子**不是** route；原子寄生在某个面板 route 的渲染树里 | D-01 覆盖栈只装面板（`decision-pack.md:328`） |
| 浮层是 route 吗 | **不是**。浮层由所在面板的 overlay 宿主以独立 window（`Dialog` / `ModalBottomSheet`）渲染，不 push/pop `PanelRoute` | D-01；E-17「返回键登记为面板内浮层」（`decision-pack.md:64`） |
| 返回键顺序 | ① 有浮层 → 关栈顶浮层（消费）；② 无浮层 → 面板 pop（归 `base.layout`） | D-01 + G-13；本文件 §2.2 逐浮层写死 |
| 浮层 → 下层保活 | 浮层打开时下层面板**不销毁**（保留滚动锚点）；浮层关闭后回到原滚动位置 | D-01「下层保活」+ 太墟 `ToolDetailsSheet.kt:30` 注释（E0，`taixu.md` 引） |
| insets：BottomSheet | 底部垫 `systemBars ∪ displayCutout` **只垫一次**；不在 sheet 内再垫；IME 由 sheet 外层 `imePadding` 统一处理 | 太墟 `Dialogs.kt:163-189` + 注释「不要在 sheet 内再垫一次」（E0，P2 §3-9 采纳） |
| insets：Modal / Lightbox | 全屏层自行避让状态栏与手势条：Modal 四周 `max(24, statusBar)`；Lightbox 关闭钮在 `statusBar + 8` 之下 | DSH `P/Modal.module.css:6-15`（E0）；Lightbox 位移为**我方取值（P4 校准）**（DSH 是桌面 36×36 距右上 20px，E0 数值不适用手机安全区） |
| insets：Toast | 底部 offset 由 overlay 宿主（`base.layout`）给；本面板只要求「不占位、不拦指针」 | G-01 + DSH `P/Toast.module.css:55-79`（E0） |
| 宽屏（≥600dp） | Modal 宽仍 `min(380, …)`；BottomSheet 宽 640 居中；Toast 宽 480 居中；Lightbox 图片 `max 100%×100%` 舞台留 16 | D-04 断点（`decision-pack.md:331`）；数值见 §1.2 |
| 高度 <480dp 的横屏 | 一律单栏；BottomSheet 最大高仍 `0.8 × 高`（189dp@412 高 → P4 需实测最小可用高，见 TBC-01） | D-04「高<480 强制单栏」（`decision-pack.md:331`） |

### 1.4 命中区落地：Compose 层四条路径（重点 1）

> **唯一硬约束：命中盒不得相交**（G-16 取代「≥8dp 隔离带」）。任何一条路径都不得越过这条；装不下就按 G-11 降级顺序退（① 加大间距 → ② 关键区可点 → ③ 抬视觉高），**禁止硬扩边制造重叠**。

| 路径 | Compose 落法 | 用于哪些原子 | 依据 / 级别 |
|---|---|---|---|
| **A. 整行承载** | `Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button){})`，视觉内容居中；开关行用整行切换、`Switch` 本体 `materialTapTargetSize = shrinkWrap` 式「本体不吃命中」 | `disclosure.row`（32）、`settings-field` 行、`switch` 行、`checkbox` 行、`menu` 行、`form-save` 按钮区、`fold-toggle`（36+整行） | dsh-Go `session_tile.dart:88-118`、`list_page_row.dart:214`（E0）；`dsh-go.md` §5.3-a |
| **B. 自绘命中层补 hitTest**（视觉完全不变） | **我方方案，P4 原型验证**：在段落容器上挂 `pointerInput`，由 `TextLayoutResult` 把点击点映射到字符区间（x）并允许 y 落在行盒上下各 e 的**命中层**内；命中层由自定义 `PointerInputModifierNode` 消费，不改变任何布局尺寸。**Compose 无 `RenderProxyBox` 等价物，等价性未取证**（Q-BP-06 ④） | `markdown-link` 正文内链接/chip（≥44）；行内次级图标（≥36，如 18dp 复制钮） | dsh-Go `_RenderLinkHitArea.hitTest()`（`relay_credit.dart:135-139,174-259`，E0 手法）；Compose 落法=**我方方案**，P4 验 |
| **C. 横向 Bleed** | 可点父盒加 8dp 水平 padding，视觉内容用 `Modifier.offset(x = -8.dp)` 或负 padding 贴回正文缘；命中盒随父盒加宽 | `disclosure.row`、`icon-action-bar`、`content-fold` 井底条 | dsh-Go `Bleed` + `_RenderBleed.hitTest`：`[-bleed, width+bleed)` 算命中（`bleed.dart:6-13,87-96`，E0；§5.3-c） |
| **D. 抬布局盒** | 直接 `heightIn(min = F)` / 上下 `padding` 把**布局盒**抬到目标值（视觉内容不一定跟着变大） | `icon-action`（40）、`input`（48）、主按钮（48）、`button`（48）、`pill`（36）、`checkbox` 方框行 | 太墟 `sizeIn(48,48)` 助手（`Buttons.kt:171`，E0）但**参与布局测量、会撑高行**（`taixu.md` `action` 字段 7）→ 只在可接受行高代价的原子用 |

**禁用项（P4 必须静态检查）**：`Modifier.offset` / `graphicsLayer` / 绘制层扩边**改变不了命中盒**（G-11）；用它们冒充扩边 = 命中盒实际没变，验收必挂（BP-73）。

**逐原子落点（本面板的最终答案）**

| 类别 | 原子 | 路径 | 最终命中盒 |
|---|---|---|---|
| 过程行 | `disclosure.row` | A + C | 行宽+16 × 32（竖向不追 44：G-16 明确 32 + 整行 + Bleed 8） |
| 行级 | `menu` 行 / `settings-field` 行 / `switch` 行 / `checkbox` 行 | A | 行宽 × 44 |
| 密集图标 | `icon-action` / `icon-action-bar` 每键 | D + C | 40×40（净距 0，靠内边距形成视觉间距） |
| 行内片段 | `pill`（交互）/ `fold-toggle` / Toast 动作 | D | ≥36；Toast 动作 ≥44（P2 §2 toast 行明确「抬到 ≥44」） |
| 输入/主操作 | `input` / 主按钮 / `form-save` | D | 48 |
| 正文链接 | `markdown-link` | **B（我方方案，P4 验证）** | 文字行盒上下各扩 e，使行内链接命中 ≥44；不加宽文本视觉 |
| 浮层关闭 | Modal/BottomSheet/Lightbox 关闭钮、sheet 把手 | D | 关闭钮 44×44（图标 20）；把手 36×4 视觉 + 44 高命中（横向 96 宽） |

---

## 2. 状态矩阵

### 2.1 全部 27 个节点逐节点覆盖 9 个状态（`pill` 按交互/只读两型分列，故表中 28 行）

> 列口径：**空**=数据为空；**加载**=等待中；**正常**；**长内容**=超长/超宽；**失败**=写入/加载失败；**禁用**；**深色**；**大字号**=fontScale 2.0；**横屏**=高<480dp。
> 「不适用」必须写原因（硬规则 4）。「同竖」= 布局不变、仅宽度变化。**不建节点的 5 项**（`disclosure.leading`、`tooltip`、`hover-card`、`tab-strip`、`json-tree`）同样列行：全列「不建节点」，原因见对应 3.x 节——目的就是让「实现时顺手做了」当场可见。

| 原子 | 空 | 加载 | 正常 | 长内容 | 失败 | 禁用 | 深色 | 大字号 | 横屏 |
|---|---|---|---|---|---|---|---|---|---|
| `disclosure.leading` | **不建节点**（P2 §4-7） | — | — | — | — | — | — | — | — |
| `disclosure.row` | 不渲染（无 reasoning/tool 内容） | 运行中：摘要 shimmer + liveRegion | 折叠 32；展开 A（D-11=C 下：流式=B 封顶 / 定稿=A） | 单行 nowrap+渐隐/ellipsis | 由内容托管（行不感知） | 不适用（可展开即可点，无 disabled） | token 文字色 | 行高=max(32,文+8)，节距同步 | 同竖 |
| `disclosure.fold-toggle` | 不渲染（`hidden=0`） | 不适用（无异步） | 「还有 N 行 ⌄」 | 文案含 N，单行 ellipsis | 不适用 | 不适用（无异步） | token | 36 命中不缩水，文字换行→行高增 | 同竖 |
| `button` | 不适用（按钮不由数据空态决定） | loading 用转圈替换文字、`enabled=false` | 48 高 | 文字单行 ellipsis、minWidth 64 | 不适用（失败由调用方 toast/错误行表达） | 40% 不透明+不触发点击 | token | 高随 fontScale（48 为最小） | 同竖 |
| `pill`（交互） | 不适用 | 不适用 | 32 视觉/36 命中 | 文字 ellipsis，最大宽=容器 | 不适用 | 整 chip 40%+不可点 | token | 命中不缩；文字 1 行 ellipsis | 同竖 |
| `pill`（只读徽标） | 不适用 | 不适用 | 24 高 | 同 chip | 不适用 | 不适用（非交互） | token | 文字跟随 fontScale，高=max(24,文+4) | 同竖 |
| `icon-action` | 能力缺失→不渲染 | busy 用 spinner 替换图标 | 40×40 | 不适用（图标固定） | 复制失败：换失败图标 + 播报（BP-15） | 38% 不透明、`enabled=false` | token 着色 | 命中 40 固定、图标不缩 | 同竖 |
| `icon-action-bar` | 不渲染（无正文/非轮尾） | 不适用（键各自 busy） | 常驻 40 高 | 键数按能力增删，可换行→**不允许**换行（BP-18） | 单键失败态 | 单键禁用，行不消失 | token | 键不缩（40），条高不变 | 同竖 |
| `tooltip` | **不建节点**（D-09 + P2 §4-3） | — | — | — | — | — | — | — | — |
| `hover-card` | **不建节点**（D-09 + P2 §4-1） | — | — | — | — | — | — | — | — |
| `modal` | 不适用（模态必有内容） | 内容区可放 loading | 居中卡片 | 内容内滚，卡高≤屏−48 | 内容区错误行 | 确认钮 disabled | 遮罩 0.32 + 卡片 token | 卡宽不变、文字换行、内容内滚 | 卡宽 `min(380, 宽−48)` |
| `dialog-dismiss` | 不适用 | 不适用 | 关闭/取消 | 不适用 | 脏检查失败→留在原地+提示 | busy 时连取消也禁用（E-10 口径） | token | 按钮高 48 不缩 | 同竖 |
| `bottom-sheet` | 空态文案 | 内容区 loading | 半屏 sheet | 高≤0.8 屏、正文内滚、按钮钉底 | 内容区错误行 | 内容区禁用 | token | 标题单行 ellipsis，正文可滚 | 高≤0.8×412≈330 |
| `menu` | 不适用（空菜单不弹） | 不适用 | sheet 单层列表 | 列表内滚，高≤0.8 屏 | 不适用 | 项 40%+不可点 | token | 项高 44 不缩、文字 1 行 | 同竖 |
| `toast` | 不适用 | 不适用 | 单行 + 可选动作 | 文本 2 行截断 + 动作仍在 | 不适用（失败本身就是一个 toast） | 不适用 | 反色底/`inverseSurface` | 高增、动作仍 ≥44 | 宽 480 居中 |
| `image-lightbox` | 不适用 | 图片加载中：骨架 | 黑底全屏 | 长图缩放/平移；缩放态禁翻页 | 加载失败：占位 + 重试/关闭 | 不适用 | 黑底不随主题反转 | 关闭钮 44 固定，控件不随字号 | 舞台边距 16 |
| `connection-indicator` | 不适用（三态之一） | connecting：点动画 + 文案 | 三态条（占位） | 文案单行 ellipsis | 断线/重连失败→坏态可点重连 | 不适用（坏态即可点） | token（success/warn/error） | 高增，图标 14 固定 | 同竖 |
| `switch` | 不适用 | 写进行中禁用整行 | 36×20 轨道 + 整行命中 44 | 不适用 | 写失败→回弹原值（调用方保持原值） | 50% + 不可点 | on=primary/off=outline token | 行高增、轨道不缩 | 同竖 |
| `checkbox` | 不适用 | 不适用 | 16 方框 + 整 label 命中 44 | 长 label 换行 | 不适用 | 整 label 50% | token（accent=primary） | 方框 16 固定、文字换行 | 同竖 |
| `input` | 空=placeholder；空提交按调用方规则 | 不适用（输入本身无 loading） | 48 高 | 单行横滚 / 多行 6 行后内滚 | `aria-invalid` + 错误文案 | 50% + 不可聚焦 | token（focus 边框=primary） | 高随 fontScale，错误行下推 | IME 遮挡由 composer 处理；本原子不自行避让 |
| `segmented-control` | 不适用（至少 2 段） | 不适用 | 44 容器 + 指示器 | 段数>4 时等分压缩，文字 ellipsis | 不适用 | 整组禁用 | token | 容器高=max(44,文字+20) | 同竖 |
| `tab-strip` | **不建节点**（见 §3.21） | — | — | — | — | — | — | — | — |
| `content-fold` | 空内容→不渲染 | 井内可 loading | 限高 280 内滚 | 头 60/尾 140/16000 字截断 | 加载失败→错误行 + 查看全部仍可用 | 不适用 | token（代码色 3 种变体） | 井高仍 280（**不跟字号涨**，避免吃掉整屏） | 井宽变宽，高仍 280 |
| `json-tree` | **不建节点**（见 §3.23） | — | — | — | — | — | — | — | — |
| `markdown-link` | 不适用 | 流式期不可点（BP-66） | 行内可点 | 长链接折行 | 打开失败→toast | 不适用 | token（链接色需对比 ≥4.5） | 行高随 fontScale，命中层同步扩 | 同竖 |
| `settings-field` | 不适用（字段必在） | 帮助区/控件可 loading | label+控件纵排 | label 2 行、帮助区可滚 | invalid：文案替换 hint + `role=status` | 整字段禁用 | token | label 换行、控件高增 | 同竖 |
| `form-save` | 不适用 | saving：主按钮转圈 | `dirty && !invalid && !saving` 才可点 | 不适用 | 保存失败：保留草稿 + `role=status` 播报 | clean/invalid/saving→disabled | token | 高 48 不缩 | 同竖 |
| `hit-target` | 不适用（纯布局修饰） | 不适用 | — | 装不下走 G-11 降级 | 不适用 | 不适用 | 不适用 | 命中不缩水 | 同竖 |

> **验收**：深色/大字号/横屏三列由 BP-74/BP-75/BP-76 覆盖（截图矩阵：3 主题 × 3 字号 × 2 方向）。
> **宽屏（≥600dp，D-04）不单列**：原子宽度只受容器约束，形态不变；宽屏下的全部差异收敛在浮层最大宽（Modal 380 / Sheet 640 / Toast 480）与 Lightbox 舞台边距，见 §1.3；逐原子不新增状态。

### 2.2 弹层关闭语义矩阵（重点 2）

> **通用判据（全部浮层）**：点遮罩关闭 = **按下与松手都在遮罩上**（D-08/G-18，`decision-pack.md:362`）；只做按下判断会在「框内拖选、拖到遮罩松手」时误关（demo 反证，P2 引 `B/Modal.tsx:9-10,36-49`）。
> 返回键顺序见 §1.3；「焦点陷阱」= 浮层用独立 window（`Dialog`/`ModalBottomSheet`）承载后的平台行为，级别 **E1**，必须 P4 TalkBack 验收（BP-78）；**回焦必须自己写**（平台只保证陷阱）。

| 浮层 | 点遮罩 | 返回键 | 下滑 | Esc（外接键盘） | 焦点陷阱 | 回焦 | 其他关闭路径 |
|---|---|---|---|---|---|---|---|
| `modal` | 关（D-08 判据） | 关 | **不关**（D-03：确认类无下滑关） | 不承诺（D-09 触摸-only；`onPreviewKeyEvent` 可选接 Esc，不构成键盘支线） | E1 由 Dialog 保证（P4 验） | **必做**→触发元素 | 卡片内关闭钮 44；底部「取消/危险」按钮 |
| `bottom-sheet` | 关（D-08 判据） | 关 | **关，仅当内滚在顶**；内滚不在顶时下滑归内滚（G-04 到边吸收，不外传） | 同上 | E1（P4 验） | **必做**→触发元素（`+`/模型胶囊等） | 把手下拉（36×4，命中 96×44）；标题栏关闭钮 44 |
| `menu`（手机=底部 sheet 单层） | 关（D-08 判据） | 关 | 关（单层列表，仅内滚在顶时；因**复用 sheet 容器**，下滑属容器语义，不是给菜单新增一条关闭路径） | 同上 | E1（P4 验） | **必做**→锚点按钮 | 选中某项后由调用方决定是否关（默认关，P2 引 `P/Menu.tsx:73-74`）；标题栏关闭钮 44 |
| `image-lightbox` | 关（D-08 判据） | 关 | **不关**（下滑被缩放/平移手势占用；避免与「拖动看图」打架） | 同上 | E1（P4 验） | **必做**→图片缩略图 | 关闭钮 44（右上，距 `statusBar+8`）；**双击/双指缩放**，缩放态禁翻页（BP-41） |
| `toast` | 不适用（无遮罩） | **不消费**（返回键直通下层） | 不适用（不拦指针） | 不适用 | 不适用（非模态） | 不适用 | 自动过期（无动作 3000ms / 有动作 6000ms，我方取值（P4 校准））；动作点击后立即消失 |
| `dialog-dismiss`（非独立浮层） | 不适用 | 不适用 | 不适用 | 不适用 | 不适用 | 不适用 | 是上面 4 个浮层的**统一关闭路径契约**：`dirty → 拦截并确认；clean → 直接关`（P2 §2 采纳 dsh-Go `DiscardGuard`，`discard_guard.dart`，E0） |
| `hover-card` / `tooltip` | **不做**（D-09 + P2 §4-1/4-3） | — | — | — | — | — | — |

**为什么 Modal 与 Sheet 的关闭集合不同**：D-03 的分工就是「慎重感」——破坏性确认多一条下滑关会让「删除 provider」这类不可逆操作变得太轻（`decision-pack.md:23`）；反之选择类 sheet 必须单手可关（`decision-pack.md:23`、`p2/base.primitives.md` bottom-sheet 行）。

**下滑关阈值**：竖向位移 **> 96dp** 或末速度 **> 600dp/s**，二者先到即提交（我方取值（P4 校准）：96dp 与 G-15 抽屉消费阈值同源，600dp/s 与 G-14 提交阈值同值，`decision-pack.md:361`）。松手把末速度交给 spring（G-14）。

### 2.3 焦点与语义对照（重点 3；全部 **E1**，P4 TalkBack 验收）

> 原则（P2 §2.2）：Compose 没有 `aria-expanded`/`role=dialog`/roving tabIndex 的一一对应物 → 给语义等价物；**不给假控件**（D-09 的 C 方案被否就是这条）。
> 语义必须落在**可点节点自身**，`hit-target` 包装层不得再加语义节点（否则读屏重复报两次）。

| 原子 | `semantics{}` 落法 | 读屏期望 | 级别 |
|---|---|---|---|
| `disclosure.row` | `role=Role.Button`；`stateDescription = 展开/已折叠`；`onClick(label = 收起/展开) { toggle(); true }` | 「思考，已折叠，可点按」→ 展开后「已展开」 | E1（P4 验）；DSH 契约 `P/DisclosureRow.tsx:93-95`（E0） |
| `disclosure.row` 运行态 | 挂 `liveRegion = Polite` 的 1dp 占位节点：`contentDescription = "正在思考"` | 生成中播报一次，结束后不重复 | E1；G-08 + `ReasoningRow.tsx:84`（E0） |
| `disclosure.fold-toggle` | `stateDescription`（展开/已折叠）+ `contentDescription` 含「还有 N 行」 | 播报「还有 12 行，已折叠」 | E1；`P/FoldToggle.tsx:26-27`（E0） |
| `button` | `role=Role.Button`；禁用时 `Modifier.semantics { disabled(); stateDescription = 原因 }` | 禁用可听出原因（不用原生 `disabled` 隐藏节点） | E1；P2 §2.2 末行（DSH 为保 Tooltip 不用原生 disabled） |
| `pill`（交互） | `Modifier.selectable(selected = …, role = Role.Button)`（**不给 Role.Checkbox/RadioButton**，避免「已勾选」误导） | 「已选中/未选中」 | E1；P2 §2 pill 行要求 `aria-pressed` 等价物 |
| `pill`（只读） | 无 role、不可聚焦（`clearAndSetSemantics` + 文本并入父行） | 不被当作控件读出 | E1；`P/Pill.tsx:16-35`（E0） |
| `icon-action` / 每键 | `contentDescription = 动作名`（**必填**，缺一个即缺陷） | 每个图标键有名字 | E1；太墟遗漏 `contentDescription` 是反面教材（`taixu.md` action 字段 11） |
| `icon-action` 复制成功 | `liveRegion = Assertive` 播报「已复制」；1000ms 后静默 | 播报一次，不刷屏 | E1；G-08 同族 + DSH 1000ms（E0，`P/ReadBlock.tsx:95-102`） |
| `icon-action-bar` | 容器不加语义；只读语义（「消息操作」） | 读屏逐键报名字 | E1 |
| `modal` | 独立 window + `paneTitle = 标题`；无标题时 `contentDescription` 取首行 | 开浮层后焦点不进背后页面 | E1；`P/Modal.tsx:60-68`（E0） |
| `bottom-sheet` | 独立 window + `paneTitle = 标题`；把手 `contentDescription = "拖动关闭"` | 面板有名字，可听出可拖 | E1 |
| `menu` | 每项 `role=Role.Button` + `contentDescription`；选中项加 `stateDescription = "已选中"` | 可逐项读出 | E1 |
| `toast` | 容器 `liveRegion = Assertive`；横幅本身不拦指针（`pointerEvents = None` 等价：只给动作按钮手势） | 出现即播报全文一次 | E1；`P/Toast.tsx:78`（E0） |
| `image-lightbox` | 独立 window + `paneTitle = "图片查看"`；关闭钮 `contentDescription = "关闭"`；图库 `contentDescription = "第 N / 共 M 张"` | 可听出计数与关闭 | E1 |
| `connection-indicator` | 坏态 `role=Role.Button` + `contentDescription = "连接已断开，点按重连"`；恢复态 `liveRegion = Polite` + `contentDescription = "已重新连接"` | 断线可点、恢复可听 | E1；`P/ConnectionIndicator.tsx:55-103`（E0） |
| `switch` | `Modifier.toggleable(value, role = Role.Switch, onValueChange)`；**样式由 `value` 派生**（不许并行 class） | 「开关，已开启」 | E1；DSH 样式键挂 `aria-checked`（`P/Switch.module.css:1-3`，E0） |
| `checkbox` | `Modifier.toggleable(value, role = Role.Checkbox)` 包**整 label** | 「复选框，未勾选」+ 可点文字 | E1 |
| `input` | `contentDescription = label`；错误 `stateDescription = 错误文案`；建议 `semantics { error(文案) }` | 可听出字段名与错误 | E1；`aria-invalid` 契约见 `P/ConfigField.tsx:41-51`（E0） |
| `segmented-control` | 容器 `Modifier.selectableGroup()`；每段 `Modifier.selectable(selected, role = Role.Button)`（**D-09：不给 `Role.Tab`、不做方向键**） | 「已选中/未选中」，不会被报成「选项卡」 | E1；D-09 + `decision-pack.md:363` |
| `settings-field` | label 与控件同节点语义（`contentDescription = label`）；帮助钮 `stateDescription`（展开/已折叠）+ 帮助区 `liveRegion` 不必要 | 可听出字段名/帮助开合 | E1 |
| `form-save` | `role=Role.Button` + `disabled()` 由状态派生；失败 `liveRegion = Polite` | 可听出「不可保存」与失败原因 | E1 |
| `markdown-link` 外链 | 保持链接语义（`Role.Button` + `contentDescription` 含「链接」）；本地文件是按钮语义（不是链接） | 读屏能区分「链接/打开文件」 | E1；`P/markdown/render.tsx:556-597`（E0） |
| `content-fold` 井底条 | `role=Role.Button` + `contentDescription = "查看全部，共 N 行"` | 可听出总量 | E1；dsh-Go `long_text.dart:238-241,279-306`（E0） |
| `hit-target` | **不加任何语义**（`clearAndSetSemantics {}` 也不要加；语义归宿主） | 不产生重复节点 | E1 |

**焦点顺序总则**：同一面板内按视觉从上到下、从左到右；浮层打开时焦点进浮层（首个可聚焦元素或标题），关闭时回触发元素；**回焦失败兜底** = 焦点交给面板根（`FocusRequester` 已被移除时），不得把焦点丢给 body/系统（DSH 反证：关闭不还焦 → 焦点掉到 body，`P/Modal.tsx` 反证段）。

### 2.4 动画清单（重点 4）

> G-10 是**上限不是下限**：浮层出现消失、chip/tab、开关轨道、列表项插入删除**一律不做高度动画**；且全部必须能被系统「移除动画」整批关闭（`MotionDurationScale == 0.dp` 时所有过渡即时完成；**Compose 的读取方式 E1，P4 验证**）。转场物理一律 spring 0.86/420（G-14，`decision-pack.md:361`），禁止固定时长 tween。

| 原子/事件 | 属性 | 曲线 | 时长 | 可中断 | reduced-motion / 移除动画 |
|---|---|---|---|---|---|
| 所有可点原子 按下 | `scale 0.97` | spring（dsh-Go 定格） | 按压 90ms 显形；松手回弹 | 是（滚动开始即取消） | 关闭（`reduced` 档不动画，dsh-Go `pressable.dart:97`，E0） |
| `disclosure.row` 展开/收起 | **直接切换**（内容通常 ≫240dp）+ 可选 ≤120ms 淡入 | 淡入 linear；**无高度动画** | ≤120ms 或不加 | 不适用 | 关闭（瞬时） |
| `disclosure.row` ≤240dp 分支（仅实测成立时） | 纵向 expand（仅高） | spring 0.86/420 | 等效 ≈300ms | 是（再点即反向） | 关闭 |
| 过程行 流式摘要 shimmer / 呼吸 | 透明度/位移扫光 | 线性循环 | 呼吸 2400ms / 扫光 1s 一轮（dsh-Go E0） | 不适用（循环） | 关闭（改为静态点） |
| 流式摘要右端渐隐 | `mask` 48dp（Web）/ alpha 渐隐 | 不适用（静态遮罩） | — | — | 不适用 |
| `icon-action` 复制成功 | 图标瞬时替换为对勾 | 无（DSH 瞬换，E0） | 1000ms 后复原 | 不适用 | 不受影响（非动画） |
| `icon-action` busy | spinner 旋转 | 线性循环 | 无限 | 不适用 | 关闭（改静态「忙」图标 + a11y 文案） |
| `icon-action-bar` 出现/消失 | 无（常驻，D-19）；**不用 opacity 显隐动画** | — | — | — | 不适用 |
| `switch` 滑块 | `translateX` | 线性/ease | 120ms（DSH E0） | 否（短） | 关闭（瞬移） |
| `switch` 轨道高度/颜色 | **不做高度动画**；颜色瞬时 | — | — | — | 不适用 |
| `pill` / `segmented-control` 选中 | 颜色 + 指示器位移 | spring 0.86/420 | ≈300ms | 是 | 关闭（瞬时跳位） |
| `modal` 出现/消失 | 遮罩 alpha + 卡片 `scale 0.96→1`（不动物理尺寸） | spring 0.86/420 | 等效 ≈300ms | 否（模态不可被打断） | 关闭（瞬时出现） |
| `bottom-sheet` 出现/消失 | `translationY` | spring 0.86/420（拖动跟手，松手交末速度） | 等效 ≈300ms | **是**（下滑中途可上拉取消） | 关闭（瞬时） |
| `bottom-sheet` 内容高度变化 | **不做高度动画**（G-10 明令浮层出现消失不做高度动画） | — | — | — | 不适用 |
| `menu` 出现/消失 | 同 sheet（手机=底部 sheet） | spring | ≈300ms | 是 | 关闭 |
| `toast` 出现/消失 | `translationY` 12dp + alpha | spring 0.86/420 | ≈300ms | 否 | 关闭（瞬时） |
| `image-lightbox` 出现/消失 | **无入场动画**（DSH E0 未见声明）→ 瞬时 | — | — | — | 不适用 |
| 双击缩放 | `scale` 1×↔2×（居中到点按处） | spring 0.86/420 | ≈300ms | 是 | 关闭（瞬时跳到目标倍率） |
| `content-fold` 展开/收起 / `fold-toggle` | **直接切换，无高度动画**（G-10；折叠常伴外层滚动位置变化） | — | — | — | 不适用 |
| `settings-field` 帮助区展开 | **不做动画**（G-10） | — | — | — | 不适用 |
| `connection-indicator` 恢复态自隐 | alpha 退出 | spring | ≈300ms | 否 | 关闭（瞬时消失） |
| `form-save` saving | 主按钮转圈 | 线性循环 | 无限 | 不适用 | 关闭（静态忙态 + a11y） |

**不该有动画的清单（P4 静态检查，BP-74）**：① 任何浮层的高度/尺寸动画；② 表单帮助区展开；③ `content-fold`；④ 页签/chip 出现消失；⑤ 开关轨道高度；⑥ 复制成功图标切换；⑦ 原子出现消失的 `size`/`height` 过渡。

---

## 3. 节点规格（13 字段，逐个写满）

> 依据缩写沿用 P2 口径：`P/`=DSH `ui-primitives/src`、`T/`=DSH `ui-theme`、`C/`=DSH `ui-chat`、`SR:`=dsh-Go `stream_row.dart`、`RR:`=dsh-Go `thinking_pill.dart`、`MA:`=旧 myco `MainActivity.kt`。E1 一律标出，E2 不出现（G-00）。

### 3.1 `base.primitives.disclosure.row` — 可展开行的唯一形态（整行即开关）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.disclosure.row`。所属 `base.primitives`；父 = 任意纵向栈（会话轮、工具组、详情页）。出现条件：内容可展开且展开体非空，**0..N 个**，各自展开态独立。**只有这一种形态**：不做「只有 16×16 图标可点」（P2 §4-7；dsh-Go/太墟均只有整行，2:1）。 |
| 2 | 网格与定位 | 父纵向栈，左对齐；行高恒 **32dp**（最小高，文字增高时 = max(32, 文字高+8)）；解剖 = leading 24×24 + gap 8 + 文字列（13/20 secondary，G-09）+ 尾部 chevron 16；水平 Bleed 8（内容贴面板 16dp 缘，命中盒从 x=8 起）；展开体左缩进 **32**（=24+8）、上 4 下 8。 |
| 3 | 命中区 | 矩形 = 行宽 + 2×8（Bleed）× 32dp（路径 A+C）。竖向**不追 44**：G-16 明确「32 + 整行可点 + 横向 Bleed 8」；与相邻过程行间距 4（节距 36），命中盒不相交。展开体正文**不绑点击**（与 dsh-Go 不同，见字段 13 反证）。 |
| 4 | 手势 | 单击头部 = 切换展开；长按 = 交给外层 `SelectionContainer` 选字；竖直拖动 = 外层滚动（clickable touch slop，拖动不产生 click）；双击不绑；**不得继承父级长按**。 |
| 5 | 行为 | `toggle()`：折叠↔展开；状态为**节点本地 + 版本号绑定**，轮次重建（版本前进）→ 自动回折叠（G-07，`use-disclosure.ts:11-20`，E0）；幂等、可撤销。展开不改变业务数据、不发命令。 |
| 6 | 视觉状态 | default：文字 `onSurfaceVariant`；运行中：摘要 shimmer + 右端 48dp 渐隐（G-05）；展开：头部 sticky + 页面底色遮挡（G-06）；pressed：整行按压反馈（scale 0.97 + 触感，§2.4）；**无 hover/focus 视觉**（D-09）；disabled：不适用。 |
| 7 | 布局占位与展开形态 | **默认形态 A（inline-uncapped + sticky header）**，G-03。折叠：定高 32 + 尺寸隔离（G-02：固定 `height` + 不测量子内容；Compose 用固定 `height` + `Layout` 不 measure 子项）。展开：根节点不封顶、下方兄弟整体下移；头部 sticky 不可滚走（G-06）；**不做内滚**。**若使用方选择 D-11=C 的形态 D**（G-03 第 4 形态）：`streaming == true` → 本原子退化为 B（封顶内滚 + 跟尾 + 拖动即暂停），`streamed == false`（定稿）→ 切回 A（不封顶）；**切换点 = 该轮 stream 由 true→false 的那一刻**（一次性切换，不在中途反复切）；**封顶值归使用方（`chat`）按 D-11=C 声明，本原子不写死 cap**。 |
| 8 | 滚动语义 | 不是滚动容器；展开内容随**外层会话**滚；G-04 不适用（无内滚）；sticky 头 `zIndex` 抬到同级之上。 |
| 9 | 动效 | >240dp → **直接切换** + 可选 ≤120ms 淡入（G-10 上档）；实测 ≤240dp 才允许纵向 spring（仅高，G-14 曲线）；可中断（再点反向）；reduced-motion 全关（§2.4）。**流式期（形态 D 的 B 分支）**：窗口换行/跟尾动画归使用方（`chat`），本原子自身不做高度动画。 |
| 10 | 边界态 | 空：不渲染；超长标题：单行 nowrap，流式用渐隐、静态用 ellipsis（G-05 只约束流式）；流式摘要变化**不引起任何兄弟重排**（G-02）；大字号：行高随 fontScale，节距同步；横屏：同竖；深色：token；离线/失败：由内容托管，行本身不感知。 |
| 11 | 无障碍 | `role=Button` + `stateDescription`（已展开/已折叠）+ `onClick(label)`（收起/展开）；运行态 `liveRegion=Polite` 播「正在思考」（G-08）；E1，P4 TalkBack 验（BP-05/BP-06）。 |
| 12 | 依据 | `E0` dsh-Go `SR:29-30,46-68`（32 行高、24+8+文字列）、`RR:181-185`（整行 onTap）、`bleed.dart:87-96`（横向 8）、`RR:225-227`（缩进 32）；`E0` DSH `P/DisclosureRow.tsx:64,93-95,121`、`module.css:16-40`；`E0` G-02/G-03/G-05/G-06/G-07/G-10；`user` G-16（D-02）、D-09、**D-11=C**（形态 D 的触发条件：流式=B、定稿=A）；两家手机参考的流式期限制（太墟 live `heightIn(max=160)`+跟尾+拖动即暂停 `ChatMessageBubbles.kt:706-735`；dsh-Go 恒 3 行窗口 `turn_process.dart:129-151`，P2 引）；`E1` Compose 的尺寸隔离与 `stateDescription` 读法（P4 验）。 |
| 13 | 反证 + 验收 ID | **反证**：① 折叠行不定高 → 流式摘要每帧推走正文，读者无法阅读（G-02）；② 展开后头部不 sticky → 多屏内容下「收起」入口不可达 = 无法关闭（G-06）；③ 若学 dsh-Go「点展开正文也收起」，会与长按选字/正文链接抢手势，用户想选字或点链接却把块收起来（dsh-Go 靠「更里层优先」规避，实现脆弱）→ **我方只让头部收起**（我方取值（P4 校准））；④ 若保留 hover 换 chevron（P2 §4-6）→ 触摸端永不触发，死代码 + 多一处过渡。**验收**：`BP-02` 折叠行高恒定（录屏：摘要变化时行顶 y 不动 ±1dp）；`BP-03` 点行内任意 x 都能切换（真机）；`BP-04` 展开后头部吸附、向下滚 3 屏仍可点收起（真机）；`BP-05` TalkBack 报「已展开/已折叠」（P4）；`BP-07` 常显 chevron、源码无 hover 换图标分支（源码断言）。 |

### 3.2 `base.primitives.disclosure.fold-toggle` — 「还有 N 行」的头尾截断开关

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.disclosure.fold-toggle`。出现条件：块内行数 > 调用方 `maxLines`（`P/ReadBlock.tsx:138-146`，E0）；每个被截断的块 1 个；块内位置 = 头 `ceil(max/2)` 行与尾行之间。与 `content-fold` 的分工：**本节点是「行数截断」开关**，`content-fold` 是「限高内滚容器」。 |
| 2 | 网格与定位 | 占内容流一整行；按钮水平居中或行首（由调用方定，本文件建议行首）；视觉文字 13/20 secondary；上下内边距 8（间距由外层给）；宽 = 内容宽（不强制满行）。 |
| 3 | 命中区 | **整行可点 + 布局盒抬到 36 高**（路径 D，G-16 行内片段 ≥36）：`heightIn(min=36).fillMaxWidth().clickable{}`；与相邻内容行之间无独立可点节点 → 不相交。 |
| 4 | 手势 | 单击 = 切换；长按不绑（交给外层选字）；拖动 = 外层滚动。 |
| 5 | 行为 | `onToggle()`；**无本地状态**（展开态由外层块持有）；文案含隐藏行数（「还有 12 行」/「收起」）；幂等。 |
| 6 | 视觉状态 | default：`onSurfaceVariant` 文字 + 可选 chevron 16；pressed：按压反馈；无 selected/disabled（未截断时不渲染，所以没有禁用态）。 |
| 7 | 布局占位与展开形态 | 位于内容流中，切换后**尾部行重排**（形态 B 的组成部分，形态归 `content-fold` 声明）；**无高度动画**（§2.4）。 |
| 8 | 滚动语义 | 自身不滚；外层块是否封顶内滚由 `content-fold` 决定；滚动到边按 G-04 由外层容器吸收。 |
| 9 | 动效 | 无（直接切换）。 |
| 10 | 边界态 | 空/未截断：不渲染（`hidden == 0`）；长文案：单行 ellipsis；多语言：文案模板含 N，N 为数字（本地化数字格式由 base.theme/i18n 负责）；大字号：36 为最小高，文字换行则行高再增；横屏/深色：同态。 |
| 11 | 无障碍 | `role=Button` + `contentDescription` 含「还有 N 行」+ `stateDescription`（已展开/已折叠）；E1（P4）。 |
| 12 | 依据 | `E0` DSH `P/FoldToggle.tsx:1-33`（`aria-expanded` + `aria-label` 含 N）、`P/ReadBlock.tsx:138-146`；`E0` G-16（36 档）；P2 §2 fold-toggle 行（采纳 DSH 语义 + dsh-Go「折叠态零高」）。 |
| 13 | 反证 + 验收 ID | **反证**：截断后不给按钮 = 数据被静默丢弃（用户不知道还有 N 行）；按钮只写「展开」不带状态 = 读屏复述时分不清当前状态。**验收**：`BP-08` 点击后 `stateDescription` 与文案同步翻转、命中实测 ≥36×行宽（真机 + TalkBack）。 |

### 3.3 `base.primitives.button` — 文字按钮（主/次/危险/工具栏）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.button`。出现：确认主操作、次要动作、危险动作、工具栏文字键；0..N 个，同一行不超过 2 个（超过改用 icon-action 或菜单）。 |
| 2 | 网格与定位 | 高 **48**（G-16 输入/主操作档）、最小宽 **64**、水平内边距 20、圆角 16；文字 14/20 单行；前导图标 20 + gap 8；同一行两按钮间距 8。 |
| 3 | 命中区 | 按钮自身布局盒（48 高 × ≥64 宽）= 命中盒（路径 D）；与相邻按钮间距 8 → 不相交；禁用态仍占位。 |
| 4 | 手势 | 单击 = `onClick`；长按不绑；双击不绑；禁用时点击被吞（不冒泡到父行）。 |
| 5 | 行为 | 纯回调，无内部状态；`enabled=false` 时不回调；**幂等由调用方保证**（提交类按钮在提交瞬间置位，见 D-14/BUG-004 口径，本面板只提供「置 busy = 转圈 + 禁用」的表达）。 |
| 6 | 视觉状态 | `primary`：primary 底 + onPrimary 文字；`secondary`：outline 边框；`danger`：error 底或 error 文字（删除/不可逆）；`toolbar`：无底色文字键；disabled：40% 不透明 + 不可点；pressed：按压反馈；**无 hover/focus**（D-09）。 |
| 7 | 布局占位与展开形态 | inline 参与布局流；不展开、不占浮层；空闲高 48（禁用不变高）。 |
| 8 | 滚动语义 | 不滚；若在可滚行内随行滚。 |
| 9 | 动效 | 无尺寸/高度动画；按压反馈见 §2.4；loader 只转圈。 |
| 10 | 边界态 | 超长文案：单行 ellipsis（不许换行撑高，除大字号导致换行→高增）；loading：转圈替换文字并禁用；失败：由调用方用 toast/错误行表达（按钮不变红）；大字号：高随 fontScale（48 最小）；横屏/深色：同态。 |
| 11 | 无障碍 | `role=Button`；禁用用 `disabled()` + `stateDescription`（原因），**不用原生 disabled 隐藏节点**（P2 §2.2 末行）；loading 时 `stateDescription = "处理中"`；E1（P4）。 |
| 12 | 依据 | `E0` G-16（48）；dsh-Go 主按钮 `64×50`/最小宽 64（`AT:251-308,258`，`dsh-go.md` §5.1）；`E0` DSH 四变体（`P/Button.tsx:9`、`module.css:2-34`）；P2 §2 button 行（未声明尺寸，myco 补）；P2 §2.2 语义表。 |
| 13 | 反证 + 验收 ID | **反证**：取 DSH 的 36/28 无触控下限 → 手机上确认类主操作容易点空（G-16 明确不许全局套 48、但主操作必须 48）；危险动作与次要动作同色 → 用户点错不可逆。**验收**：`BP-09` 四变体×禁用态截图 + 禁用不触发点击（单测）；`BP-10` 字号 200% 下主按钮不被裁切（截图）。 |

### 3.4 `base.primitives.pill` — 胶囊 chip（交互/只读两型）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.pill`。出现：筛选/模式切换（交互型）、状态徽标（只读型）；交互型 0..N，只读型 0..N。与 `segmented-control` 分工：**segmented = 互斥单选**；**pill = 可多选/可切换的独立开关**（P2 §3-7）。 |
| 2 | 网格与定位 | 交互型：视觉高 32、布局命中 36（`heightIn(min=36)`）、水平内边距 12、圆角 999、文字 12/18、可选前导图标 16 + gap 4；并排间距 8。只读型：视觉高 24、padding 0 8、圆角 999、文字 12/18，**不可聚焦**。 |
| 3 | 命中区 | 交互型 = 布局盒 ≥36 高 × 文字+24 宽（路径 D）；只读型 = **无命中**（非交互）。并排 chip 间距 8 ≥ 0 → 不相交。 |
| 4 | 手势 | 单击 = 切换/选中（交互型）；长按/双击不绑；只读型无手势。 |
| 5 | 行为 | 交互型受控：`selected` + `onToggle`；**选中态必须进语义**（不许只有样式）；只读型纯展示。 |
| 6 | 视觉状态 | 交互 default：surfaceContainer 底 + onSurfaceVariant 文字；selected：primaryContainer 底 + onPrimaryContainer 文字 + 可选对勾 16；pressed：按压反馈；disabled：40% + 不可点；只读：无状态。 |
| 7 | 布局占位与展开形态 | inline 参与布局流；不展开；chip 出现/消失**不做动画**（G-10）。 |
| 8 | 滚动语义 | 不滚；可横向滚动的 chip 行由上层容器负责。 |
| 9 | 动效 | 只有颜色/指示器过渡（spring，§2.4）；**无尺寸动画**。 |
| 10 | 边界态 | 超长文字：单行 ellipsis，最大宽 = 容器宽；大字号：文字跟随 fontScale，命中不缩；深色：token；横屏：同竖；空列表：不渲染（由上层给空态）。 |
| 11 | 无障碍 | 交互型 `Modifier.selectable(selected, role = Role.Button)`（不给 Checkbox/RadioButton 以免语义错位）；只读型 `clearAndSetSemantics` 并入父文本；E1（P4）。 |
| 12 | 依据 | `E0` DSH Pill 24/12/999（`P/Pill.module.css:1-14`、`P/Pill.tsx:16-35`）；`E0` 选中态语义缺口（`dsh-base.md` §Q-BASE-06）；`E0` G-16（行内片段 ≥36）；P2 §2 pill 行（强制 `aria-pressed` 等价物）。 |
| 13 | 反证 + 验收 ID | **反证**：chip 不带状态语义 → 读屏无法知道当前筛选项（`P/Pill.tsx` 无 `aria-pressed`，是已知缺口，不能照抄）；把只读徽标做成可点 → 被聚焦却点不动。**验收**：`BP-11` 选中 chip TalkBack 报「已选中」+ 命中实测 ≥36（真机 + TalkBack）；`BP-12` 只读徽标不在焦点序（TalkBack 遍历）。 |

### 3.5 `base.primitives.icon-action` — 单个图标动作键

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.icon-action`。出现：代码块头部复制/换行、内容块复制、工具卡动作、行内次级动作；0..N 个。**独立可点**，与「行内次级图标」的区别：后者若 <36 必须走 §1.4 路径 B 或与父行语义可区分。 |
| 2 | 网格与定位 | 键 **40×40**（布局盒=命中盒），图标 20，圆角 12；并排净距 0（靠每键 10 内边距形成视觉间距）；在 44 高 header 内垂直居中；行内次级版本：视觉图标 20、命中 36（路径 D）或路径 B。 |
| 3 | 命中区 | 40×40（路径 D）；与相邻键净距 0 但**布局盒不重叠**（各 40 宽相邻排列，中心距 40）；与行级可点区（如代码块头部若整行可点）**不得重叠**：本原子的键必须 `pointerInput` 消费事件（不冒泡到父行），且布局上与父行命中区分层（父行只覆盖非键区域或父行不绑定点击）。 |
| 4 | 手势 | 单击 = 动作；长按不绑；双击不绑；busy/disabled 时点击被吞。 |
| 5 | 行为 | 复制：写剪贴板 → 成功换对勾 1000ms（DSH E0）→ 失败换失败图标 + 播报（不静默）；换行：切换折行；busy 期间重复点击忽略（幂等）。 |
| 6 | 视觉状态 | default：onSurfaceVariant；pressed：按压反馈；busy：spinner 替换图标；success：对勾 1000ms；failed：error 图标 + 播报；disabled：38% + `enabled=false`。 |
| 7 | 布局占位与展开形态 | flex 固定尺寸，不随内容变化；不展开。 |
| 8 | 滚动语义 | 不滚（在 header 区，不随内容滚）；若放在可滚区，随区滚。 |
| 9 | 动效 | 图标瞬时替换（DSH E0，不做 AnimatedSwitcher；G-10 反证：动画期间不可读）；busy spinner 循环；按 §2.4 受移除动画影响。 |
| 10 | 边界态 | 复制失败：必须有失败态 + 播报（不采纳 DSH 静默失败）；能力缺失：不渲染；大字号：键 40 固定、图标不缩；深色：token；横屏：同竖。 |
| 11 | 无障碍 | `contentDescription` **必填**（动作名）；成功 `liveRegion=Assertive` 播「已复制」；busy `stateDescription="处理中"`；E1（P4）。 |
| 12 | 依据 | `E0` G-16（密集图标 40）、太墟 `ComposerSendButton.kt:20-24`（40=命中，E0）、dsh-Go 36×36 `shrinkWrap`（`message_actions.dart:96-105`，E0，取 40 因 G-16 定档）；`E0` DSH 复制 1000ms（`P/ReadBlock.tsx:95-102`）、失败静默缺陷（`dsh-base.md` §Q-BASE-07）；P2 §2 icon-action 行。 |
| 13 | 反证 + 验收 ID | **反证**：24×24 无扩边 → 两键中心距 28，低于手指抖动，误触率不可接受（`dsh-base.md` 反证原文）；复制成功只换图标不播报 → 读屏用户不知结果。**验收**：`BP-13` 每个图标键实测 40×40 且 disabled 不触发（单测 + 真机）；`BP-14` 复制成功 TalkBack 播报一次、1000ms 后对勾复原（真机）；`BP-15` 复制失败有失败态且播报（真机 mock 失败）。 |

### 3.6 `base.primitives.icon-action-bar` — 图标动作条（常驻，只给轮尾最后一条）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.icon-action-bar`。出现：消息轮**最后一条**助手正文下方（D-19）、代码块/工具卡头部；每轮 **0..1 个**（轮尾）+ 每个块 0..1 个。 |
| 2 | 网格与定位 | 父 = 消息轮底部；`Padding(top=4)` + 水平 Bleed 8 + `Row`；每键 40×40、图标 20；条高 40；右侧可挂脚注（如「已编辑」）。 |
| 3 | 命中区 | 每键 40×40（路径 D），横向 Bleed 8（路径 C）让首尾键与正文缘对齐；键间净距 0；与消息正文之间 top 4 不构成可点区 → 不相交。 |
| 4 | 手势 | 单击键 = 动作；长按键不绑（长按消息本体的菜单是 chat 面板的事）；滑动 = 外层会话滚动。 |
| 5 | 行为 | **常驻可见**（不做 hover 显隐、不做「点气泡唤出」）：D-19 已拍板；显隐策略收敛到本原语，禁止各调用点 `if (isLast)` 复制。 |
| 6 | 视觉状态 | default：键 onSurfaceVariant；pressed：按压反馈；busy：单键 spinner；done：复制对勾 1000ms；disabled：单个键 38%，**整条不消失**；无 hover（D-09）。 |
| 7 | 布局占位与展开形态 | 参与布局流，**恒高 40**（+top 4），常驻时**零布局位移**（这是 D-19 的「零布局代价」）；不做出现/消失动画。 |
| 8 | 滚动语义 | 不滚；随消息列表滚。 |
| 9 | 动效 | 无出现/消失动画；键内反馈同 `icon-action`。 |
| 10 | 边界态 | 无正文/非轮尾：不渲染；能力缺失：对应键不渲染（条不变空）；6 键在 360dp 屏**必须一行放下**（6×40=240 ≤ 屏宽−32）；大字号：键 40 固定；横屏：键数不变；深色：token。 |
| 11 | 无障碍 | 容器不加语义；每键 `contentDescription` 必填；E1（P4）。 |
| 12 | 依据 | `user` D-19（`decision-pack.md:382`）；`E0` dsh-Go 常驻 + 只轮尾（`chat_rows.dart:45-58,189-190`，`message_actions.dart:96-114`）、太墟 `assistantToolbarIds`（`ChatTimelineProjection.kt:127-137`）；`E0` DSH 触摸端常驻且隐藏用 opacity 仍占位（`C/MessageIconActions.module.css:5-10,42-61`）；`E0` G-01/G-16；P2 §5（新增抽象）。 |
| 13 | 反证 + 验收 ID | **反证**：照抄 demo 的 `opacity:0` + hover 显隐 → 触摸端历史消息操作不可达（demo 已知缺陷）；若默认收起、点气泡唤出 → 与长按菜单形成两套入口，且读屏用户无法一次到达（dsh-Go 字段 13 原话）。**验收**：`BP-16` 触摸端首屏即可见、源码无 hover 显隐分支、显隐不产生布局位移（截图 + 源码断言）；`BP-17` 一轮多条助手消息时只有最后一条有操作条（真机）；`BP-18` 360dp 宽 6 键一行不换行且命中盒不相交（几何单测）。 |

### 3.7 `base.primitives.tooltip` — **不建节点**（决议记录）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.tooltip`。P2 §1 曾登记；**首版不建**（D-09 触摸-only + P2 §4-3）。 |
| 2 | 网格与定位 | 不适用（不建节点）。 |
| 3 | 命中区 | 不适用。**替代契约**：每个图标键必须用 `contentDescription` 给可达名（必填，见 3.5 字段 11）。 |
| 4 | 手势 | 不适用。**长按出提示也不做**（D-09；dsh-Go 的 Tooltip 长按是否显形未取证，不能当依据）。 |
| 5 | 行为 | 不适用。 |
| 6 | 视觉状态 | 不适用（无气泡）。 |
| 7 | 布局占位与展开形态 | 不适用（G-01 本不占位，但既然不建节点，无此层）。 |
| 8 | 滚动语义 | 不适用。 |
| 9 | 动效 | 不适用（少一处「只允许 opacity」的动效）。 |
| 10 | 边界态 | **已知缺口（登记）**：视觉用户在图标含义不明时没有 hover 提示。若 P4 真机发现关键图标不易识别 → 由 Lead 决定是否改为「长按出提示」并回到 §5（不自行实现）。 |
| 11 | 无障碍 | 不适用（替代契约在字段 3）。 |
| 12 | 依据 | `user` D-09（`decision-pack.md:363`）；P2 §4-3（`P/Tooltip.tsx:54-55,189-204,244-248` E0 为不做的证据）；`demo` `B/Tooltip.tsx:24,43` 移动端硬关。 |
| 13 | 反证 + 验收 ID | **反证**：若做 hover tooltip，触摸端永不触发 → 等于一条死代码 + 维护两套说明体系；若做 `openOnClick` 钉住，会与图标键的单击动作冲突。**验收**：`BP-19` 源码断言不存在 tooltip 触发路径（无 `onMouseEnter`/无长按提示），且每个 `icon-action` 键都有 `contentDescription`（静态扫描 + TalkBack 抽查）。 |

### 3.8 `base.primitives.hover-card` — **不建节点**（决议记录）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.hover-card`。P2 §1 曾登记；**首版不建**（D-09 + P2 §4-1）。 |
| 2 | 网格与定位 | 不适用（不建节点）。替代：引用/来源预览走 BottomSheet（D-03 轻选择/详情类）。 |
| 3 | 命中区 | 不适用。替代：引用角标的点击由 `markdown-link` 承担。 |
| 4 | 手势 | 不适用。 |
| 5 | 行为 | 不适用。**整卡复制（`copyable`）明确不做**（P2 §4-1：与长按选字冲突）。 |
| 6 | 视觉状态 | 不适用。 |
| 7 | 布局占位与展开形态 | 不适用。 |
| 8 | 滚动语义 | 不适用。 |
| 9 | 动效 | 不适用。 |
| 10 | 边界态 | 不适用。已知缺口：hover 预览不存在，来源信息只能靠 sheet（已在 chat 面板以来源面板承接）。 |
| 11 | 无障碍 | 不适用。 |
| 12 | 依据 | `user` D-09；P2 §4-1（`P/HoverCard.tsx:235-248` E0 为「不做 copyable」的证据）；dsh-Go/太墟均无此原语（P2 §1 行）。 |
| 13 | 反证 + 验收 ID | **反证**：触摸端无 hover 事件源 → 预览卡永不出现；若改为长按唤出，会与长按选字/长按菜单冲突。**验收**：`BP-20` 源码断言不存在 hover-card 组件与 hover 触发路径（静态扫描）。 |

### 3.9 `base.primitives.modal` — 居中模态框（确认/危险/表单）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.modal`。出现：确认/危险/不可逆操作、表单类编辑（D-03 A 档）；同时 1 个（不嵌套，嵌套需求走 BottomSheet 二次选择）。 |
| 2 | 网格与定位 | 独立 window；卡片宽 `min(380, 屏宽−48)`、圆角 16、内边距 24（内容 24/按钮区 16）；标题 16/24 加粗 + 关闭钮 44 在右上；内容区可内滚；按钮区底部：主/危险钮右、取消钮左或次行（调用方定，本文件要求**按钮高 48**、两钮间距 8）。 |
| 3 | 命中区 | ①遮罩整层（关闭判据见字段 4）；②关闭钮 44×44（图标 20）；③底部按钮 48 高。三者不相交。 |
| 4 | 手势 | 点遮罩 = 关（**按下与松手都在遮罩上**，D-08/G-18）；点关闭钮 = 关；**下滑不关**（D-03）；返回键 = 关；Esc 不承诺（D-09）。 |
| 5 | 行为 | `onDismiss()` 由调用方决定后果：`dirty` 时进 `dialog-dismiss` 拦截（确认丢弃/继续编辑），`clean` 直接关；关闭后**回焦触发元素**。 |
| 6 | 视觉状态 | 遮罩 0.32（D-05，归 base.layout）；卡片 surfaceContainerHigh + elevation；进入：alpha + scale 0.96→1（spring）；pressed 按钮反馈；无 hover。 |
| 7 | 布局占位与展开形态 | **形态 C（sheet-overlay）**：独立 window，完全不占位；下层面板保活。 |
| 8 | 滚动语义 | 内容区可内滚（`verticalScroll`），到边按 G-04 吸收、不外传给下层（遮罩拦截指针）；标题/按钮区固定不滚。 |
| 9 | 动效 | 出现/消失 spring 0.86/420（alpha+scale，不动高度）；不可中途打断（模态）；移除动画 → 瞬时。 |
| 10 | 边界态 | 空：不适用（模态必有内容）；超长内容：内容区滚动，卡高 ≤ 屏−48；失败：内容区错误行 + 主按钮可重试；禁用：主按钮 disabled；大字号：卡宽不变、内容内滚；横屏：宽 `min(380, 宽−48)`；深色：token。 |
| 11 | 无障碍 | 独立 window + `paneTitle = 标题`；关闭钮 `contentDescription`；焦点陷阱 E1（Dialog 保证，P4 验）；**回焦自己写**；E1（P4）。 |
| 12 | 依据 | `E0` DSH `P/Modal.tsx:28-91`（`role=dialog`/`aria-modal`/焦点陷阱/Esc 仅栈顶/记录-还焦）、`P/useModalLayer.ts:46-96`、`module.css:6-15,29,47-57`；`user` D-03（Modal 档）、D-08、D-09；`E0` demo 反证（遮罩 `onMouseDown+target` 防误关，P2 引 `B/Modal.tsx:9-10,36-49`）；`E1` Compose 焦点陷阱/`paneTitle`（Q-BP-06）。 |
| 13 | 反证 + 验收 ID | **反证**：① 遮罩用「按下即关」→ 用户在输入框内拖选文字、拖到遮罩松手就把弹窗关掉、刚填的内容全丢（D-08 反证）；② 不给回焦 → 关闭后焦点掉到 body，下一次键盘/读屏操作从页首开始（DSH 反证）；③ 给下滑关 → 不可逆操作只需一个下滑就没了「慎重感」（D-03）。**验收**：`BP-21` 框内按下拖到遮罩松手**不关**、遮罩按下松手**关**（真机手势）；`BP-22` 下滑不关（真机）；`BP-23` 返回键关闭且焦点不进背后页面（真机 + TalkBack）；`BP-24` 关闭后焦点回到触发按钮（TalkBack）；`BP-25` 内容 3 屏时卡不超屏、内容内滚（截图）。 |

### 3.10 `base.primitives.dialog-dismiss` — 弹层「离开」的统一路径（dish 拦截）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.dialog-dismiss`。不是独立视觉节点，而是 modal/sheet/menu/lightbox 与**表单页**共用的关闭路径契约；出现：任意可关浮层 + 任意含未保存草稿的页面。 |
| 2 | 网格与定位 | 无几何（复用宿主浮层/页面的按钮与关闭入口）；只定义**判定顺序**：`dirty?` → 拦截 → 确认；`clean?` → 直接关。 |
| 3 | 命中区 | 无独立命中（确认弹窗内按钮按 `modal` 的 48 高；「放弃修改」按钮按 `button` danger/secondary 变体）。 |
| 4 | 手势 | 覆盖三条出口：系统返回键、侧滑返回（手势导航的 back）、点遮罩/下滑（浮层）。**三条出口走同一判定**。 |
| 5 | 行为 | `dirty == true`：弹「放弃修改？」Modal（两个按钮：继续编辑 / 放弃）；`clean == true`：直接关（不弹确认，避免无改动时多点一次）；`busy == true`：连取消也禁用（防半途卸载，E-10 口径）。 |
| 6 | 视觉状态 | 无独立视觉；确认弹窗复用 `modal`，危险按钮用 danger 变体。 |
| 7 | 布局占位与展开形态 | 不适用（不产生布局）。 |
| 8 | 滚动语义 | 不适用。 |
| 9 | 动效 | 复用 `modal` 的 spring；无额外动画。 |
| 10 | 边界态 | 脏检查本身失败（保存状态未知）：**按 dirty 处理**（保守，宁可多问一次）；大字号/横屏/深色：复用 modal。 |
| 11 | 无障碍 | 确认弹窗 `paneTitle = "放弃修改？"`；按钮语义复用 `button`；播报由 modal 承担；E1（P4）。 |
| 12 | 依据 | `E0` dsh-Go `DiscardGuard`（`discard_guard.dart`，P2 §2 采纳）；`user` D-10（`decision-pack.md:364`：离开时 dirty 二次确认，覆盖系统返回/侧滑/手势三条出口）；`E0` G-12；`E0` 旧 myco 反证（表单关闭静默丢草稿 `MA:2012`）。 |
| 13 | 反证 + 验收 ID | **反证**：无条件丢弃 → 半改的密钥/baseURL 静默消失，用户以为已保存（旧 myco 已踩）；无条件确认 → 无改动时每次离开多点一次。**验收**：`BP-26` clean 直接关、dirty 弹确认（真机 + 单测）；`BP-27` 三条出口（返回键/侧滑/遮罩）都触发同一拦截（真机，三路各一次）。 |

### 3.11 `base.primitives.bottom-sheet` — 底部升起面板（选择/详情/菜单）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.bottom-sheet`。出现：轻选择、单选、附件入口、模型选择、来源/详情（D-03 B 档）；同时 1 个；菜单（手机形态）也复用它（3.12）。 |
| 2 | 网格与定位 | 独立 window；高按内容、`max = 0.8 × 窗口高`；底部贴边；水平留白 20（sheet gutter）；顶部把手 36×4、标题行高 56（标题 16/24 + 关闭钮 44）；正文可内滚、`weight(1f, fill=false)`；按钮区钉底 48 高；圆角 16（仅上两角）。 |
| 3 | 命中区 | ①遮罩（判据见字段 4）；②关闭钮 44；③把手：视觉 36×4、命中 96×44；④列表行 44 整行；⑤底部按钮 48。彼此不相交（把手与关闭钮不同区）。 |
| 4 | 手势 | 点遮罩 = 关（D-08 判据）；返回键 = 关；**下滑 = 关，仅当内滚在顶**；内滚不在顶时下滑归内滚、到边吸收（G-04，不外传给遮罩）；拖动跟手、松手把末速度交 spring。 |
| 5 | 行为 | `dismiss()` 走 `dialog-dismiss`（浮层内有草稿时拦截；纯选择无草稿）；选中项后默认关（可配）；关闭后**回焦触发元素**。 |
| 6 | 视觉状态 | 遮罩 0.32；sheet 表面 surfaceContainer + 上圆角 16；拖动时遮罩 alpha 跟手；行 pressed = 背景色 pressed 态 + 触感（**不用整行缩放**，避免整行缩放显脏；**我方取值（P4 校准）**：sheet 行与 `menu` 行背景态取代涟漪，其余原子用 scale 0.97）。 |
| 7 | 布局占位与展开形态 | **形态 C（sheet-overlay）**：不占位；打开时下层聊天列表高度/锚点不变（太墟 `ToolDetailsSheet.kt:30` 注释 E0）。 |
| 8 | 滚动语义 | 正文内滚（`LazyColumn`/`verticalScroll`）；到顶才允许下滑关（G-04 + G-13）；到边不把速度传给遮罩/下层。 |
| 9 | 动效 | 出现/消失 `translationY` spring 0.86/420（≈300ms）；拖动跟手；**内容高度变化不做动画**（G-10）；移除动画→瞬时。 |
| 10 | 边界态 | 空：空态文案 + 关闭入口；长列表：内滚，高 0.8 屏；失败：错误行 + 重试；大字号：标题单行 ellipsis、正文可滚；横屏（高<480）：高 ≤ 0.8×高（≈330@412），P4 需实测最小可用（TBC-01）；深色：token；insets：只垫一次（§1.3）。 |
| 11 | 无障碍 | 独立 window + `paneTitle = 标题`；把手 `contentDescription="拖动关闭"`；关闭钮 `contentDescription="关闭"`；焦点陷阱 E1；回焦自写；E1（P4）。 |
| 12 | 依据 | `E0` dsh-Go `showAppSheet`（`app_sheet.dart:45-58`）、把手 36×4（`app_theme.dart:386,389`）、留白 20（`app_sheet.dart:25-29`）；`E0` 太墟 `Dialogs.kt:163-189`（insets 只垫一次）、`heightIn(max≈0.8)`（E1 `UI_GUIDELINES.md:65`）；`user` D-03（B）、D-08、G-13；`E0` G-04。 |
| 13 | 反证 + 验收 ID | **反证**：① 全用居中 Modal → 手机上选择类面板单手关不掉、列表可视区更小（D-03 选项 A 的反证）；② 下滑在任意滚动位置都能关 → 用户想滚列表却把面板滑没了（G-13 的「内滚在顶才触发」正是为此）；③ 在 sheet 内再垫一次导航栏 → 双倍留白（太墟注释明令禁止）。**验收**：`BP-28` 列表滚到中间下滑不关、滚到顶下滑关（真机）；`BP-29` 遮罩按下+松手才关、返回键关（真机）；`BP-30` 底部间距 = 系统 inset（真机三键/手势两种导航模式截图）；`BP-31` 3 屏内容高 ≤0.8 屏、内滚、按钮钉底（真机录屏）。 |

### 3.12 `base.primitives.menu` — 菜单（手机 = 底部 sheet 单层列表）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.menu`。出现：消息 `⋯`/长按菜单、会话行菜单、次级操作集合；每屏 1 个。**手机形态 = BottomSheet 单层列表**（P2 §2 menu 行、C-BP-03）；**子菜单不做**（P2 §4-2）。 |
| 2 | 网格与定位 | 复用 `bottom-sheet` 容器（把手/标题/inset）；列表行高 **44**、水平内边距 20、行内：图标 20 + gap 12 + 文字 14/20；危险项文字 error 色；带选中标记的项右侧 20 对勾；分隔线无（用间距 4 分组）。 |
| 3 | 命中区 | 行 = 整行 44（路径 A）× 满宽；相邻行净距 0（布局盒相邻不重叠）；关闭钮 44；与把手区不相交。 |
| 4 | 手势 | 单击行 = 选中并关（默认）；返回键/遮罩/下滑（内滚在顶）= 关；长按不绑。 |
| 5 | 行为 | `onSelect(id)` → 调用方决定是否关（默认关）；选中后**焦点回锚点按钮**；幂等（重复点同一项只回调一次）。 |
| 6 | 视觉状态 | 行 default/pressed/disabled(40%)/danger(error 文字)/selected(对勾)；无 hover（D-09）。 |
| 7 | 布局占位与展开形态 | 形态 C（overlay），不占位。 |
| 8 | 滚动语义 | 列表内滚；内滚在顶才允许下滑关（同 sheet）。 |
| 9 | 动效 | 同 sheet 进出 spring；**菜单项出现/消失不做动画**（G-10）。 |
| 10 | 边界态 | 空菜单：**不弹**（`items.isEmpty()` 时不打开，调用方负责）；长列表：内滚；超长文案：单行 ellipsis；大字号：行高 44 最小、文字可换行→行高增（此时不再保证 44→P4 校准）；横屏/深色：同 sheet。 |
| 11 | 无障碍 | 每行 `role=Button` + `contentDescription`；选中项 `stateDescription="已选中"`；`paneTitle` 取菜单名；E1（P4）。 |
| 12 | 依据 | `E0` dsh-Go 消息菜单=底部弹层（`message_menu.dart:89,135,299`，P2 引）、菜单行 44+（`chat.msg.user` 字段 3）；`E0` 太墟 `PlainBackdropModalBottomSheet`（`ChatTopBar.kt:121-178`）；`E0` DSH 桌面菜单契约（`P/Menu.tsx:326-404,517-547`、`P/Menu.module.css:28-45`）仅作宽屏参考；`user` D-03；P2 §4-2（不做子菜单）。 |
| 13 | 反证 + 验收 ID | **反证**：① 锚定 popup 在手机上会被键盘顶走或贴边裁剪（demo 用视口 clamp 才勉强成立，P2 menu 行反证）；② 做子菜单 → 触摸端「点击=选中」与「点击=展开子菜单」互斥，必须靠长按过渡，两层后读屏无法预期。**验收**：`BP-32` 手机形态为底部 sheet 单层、源码断言无子菜单/popup 路径（源码 + 真机）；`BP-33` 每项 44 高、危险项可辨、选中项读屏报已选中（真机 + TalkBack）；`BP-34` 关闭后焦点回到锚点按钮（TalkBack）。宽屏 popup 首版不实现（BP-35 断言）。 |

### 3.13 `base.primitives.toast` — 底部一次性提示（可带动作/撤销）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.toast`。出现：复制成功/失败、撤销类操作（归档/删除后撤销）、配额超限等一次性提示；**同一时刻最多 1 条**（新的替换旧的）。 |
| 2 | 网格与定位 | overlay（归 base.layout 宿主给 offset）；宽 `min(屏宽−32, 480)`、圆角 16、底 `inverseSurface` + `inverseOnSurface` 文字、高 ≥48、内边距 16；单行 14/20（2 行截断）；动作文字 14/20 + 左间隔 16。 |
| 3 | 命中区 | 横幅**不拦指针**（G-01 + DSH `pointer-events:none`，E0）→ 只有动作按钮可点，命中 **≥44 高 × 动作文字+16**（路径 D，P2 §2 toast 行要求抬到 ≥44）。 |
| 4 | 手势 | 单击动作 = 执行并立即消失；横幅其它区域点击=透传给下层；**下划/长按不绑**；返回键**不消费**（toast 非模态）。 |
| 5 | 行为 | 自动过期：无动作 **3000ms**、有动作 **6000ms**（我方取值（P4 校准）：动作需要额外反应时间，撤销窗口不能太短）；动作回调后立即移除；被新 toast 替换时旧的立即消失。 |
| 6 | 视觉状态 | 单一样式（不混 Snackbar/Toast 两套，P2 §3-3）；成功/失败可用左侧图标区分（对勾/感叹号 16）；无 hover；无 pressed 底色（只有动作文字可点）。 |
| 7 | 布局占位与展开形态 | **浮层不占位**（G-01）；不推挤内容；不产生空行。 |
| 8 | 滚动语义 | 不滚；不拦截列表滚动。 |
| 9 | 动效 | 进/出 `translationY 12dp` + alpha，spring 0.86/420；**不做高度动画**；移除动画→瞬时。 |
| 10 | 边界态 | 超长文案：2 行截断（不省略号？截断用 ellipsis，因为 toast 不是流式）；多动作：最多 1 个（需要两个动作改用 Modal）；大字号：高增、动作仍 ≥44；横屏：宽 480 居中；深色：inverse 表面适配；离线/失败：失败本身就是一个 toast。 |
| 11 | 无障碍 | `liveRegion = Assertive`（出现即播报全文一次）；动作 `contentDescription` 含动作名（如「撤销」）；E1（P4）。 |
| 12 | 依据 | `E0` DSH `P/Toast.tsx:78-101`（`role=alert`、横幅 `pointer-events:none` 仅动作可点）、`P/Toast.module.css:55-79`；`E0` dsh-Go `showBriefNotice`（`brief_notice.dart:29-31`）；`E0` 太墟反证（Toast/Snackbar 混用，P2 §3-3 不照抄）；P2 §2 toast 行（统一一种 + 撤销进 action）。 |
| 13 | 反证 + 验收 ID | **反证**：① 横幅整体可点 → 挡住下方内容且点空白会误触发（DSH 反证）；② 撤销窗口太短 → 用户来不及反应（旧 myco「Snackbar 短时窗」已踩）；③ Toast 与 Snackbar 混用 → 同一 App 两种底部提示，用户要重新学。**验收**：`BP-36` toast 出现时下方内容仍可点（真机）：`BP-37` 动作命中 ≥44（量测）；`BP-38` 出现即播报一次、同时最多 1 条（TalkBack）；`BP-39` 无动作 3000ms/有动作 6000ms 自动消失、返回键不消费（真机计时）。 |

### 3.14 `base.primitives.image-lightbox` — 图片全屏查看（含缩放/翻页）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.image-lightbox`。出现：点开正文/工具图片；同时 1 个；同组多图进图库横滑。 |
| 2 | 网格与定位 | 全屏黑底（不随主题反转）；关闭钮 44×44 右上，距 `statusBar + 8` 与右 `16`（我方取值（P4 校准））；图片 `ContentScale.Fit`、舞台边距 16；图库计数（如「2/5」）底部安全区之上 16；底部动作条（保存/分享）高 48。 |
| 3 | 命中区 | ①遮罩（非图片区）；②关闭钮 44；③底部动作 48；④图片本身 = 缩放手势区（不绑定点击关）；彼此不相交。 |
| 4 | 手势 | 点遮罩 = 关（D-08 判据）；返回键 = 关；**双击 = 1×/2× 缩放**（居中到点按处）；双指缩放 + 平移；未缩放时横滑 = 图库翻页；**缩放态禁翻页**（`NeverScrollableScrollPhysics` 等价：翻页手势不响应）；下滑不关。 |
| 5 | 行为 | 缩放态在 1× 时恢复翻页；保存到相册 → toast 确认；关闭后**回焦缩略图**。 |
| 6 | 视觉状态 | 黑底 + 图片无边框；加载中骨架；失败占位（图标 + 「重新加载」+ 关闭仍可用）；缩放态无 UI 变化（不显示倍率也可，P4 若加倍率需另立验收）。 |
| 7 | 布局占位与展开形态 | **形态 C**，独立全屏 window/route 层，不占位；下层保活。 |
| 8 | 滚动语义 | 查看器内无纵向滚动（图片 Fit）；图库横向 `PageView`，缩放态禁用；到边不传速（G-04）。 |
| 9 | 动效 | **无入场动画**（DSH E0 未见声明）；双击缩放 spring 0.86/420；翻页跟手；移除动画→瞬时。 |
| 10 | 边界态 | 空：不适用；加载失败：占位 + 重试；超长图：缩放/平移；多图：图库翻页；横屏：舞台边距 16、图片按宽高比适配；大字号：控件（关闭钮/动作）不随字号缩；深色：黑底不反转。 |
| 11 | 无障碍 | 独立 window + `paneTitle="图片查看"`；关闭钮 `contentDescription="关闭"`；图库 `contentDescription="第 N / 共 M 张"`；E1（P4）。 |
| 12 | 依据 | `E0` DSH `P/ImageLightbox.tsx:26-67`（遮罩 `onMouseDown` 关、Esc、Tab 锁关闭钮、记录-还焦、`role=dialog`）；`E0` dsh-Go 双击 1×/2× + 双指缩放 + 缩放态禁翻页（`image_gallery.dart:58`，P2 §3-4 采纳）；`user` D-03/G-13（Lightbox 档）、D-08；`E1` Compose 手势组合（P4 验，Q-BP-06）。 |
| 13 | 反证 + 验收 ID | **反证**：① 缩放态仍允许翻页 → 双指缩放会误触发翻页（dsh-Go 明确为此切 `NeverScrollable`）；② 遮罩按下即关 → 用户拖动看图时手指落到遮罩上松手就退出。**验收**：`BP-40` 双击 1×↔2×（真机）；`BP-41` 缩放态横滑不翻页、1× 时横滑翻页（真机录屏）；`BP-42` 遮罩按下+松手才关、关闭钮 44 可点、返回键关（真机）；`BP-43` 关闭后焦点回缩略图（TalkBack）；`BP-44` 加载失败有占位且关闭仍可用（mock 失败）。 |

### 3.15 `base.primitives.connection-indicator` — 断线/连接中/已恢复三态（**内联占位**）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.connection-indicator`。出现：断线、重连中、已恢复三态；挂在会话或壳的固定位置；每处 0..1 个。 |
| 2 | 网格与定位 | 内联横条：图标 14 + gap 8 + 文案 13/20；左右 16、上下 8；高 = max(36, 文字+16)；满宽。 |
| 3 | 命中区 | 坏态/连接中态：**整条**可点（44 高，路径 A）；恢复态：不可点（无命中）。 |
| 4 | 手势 | 单击（坏态）= 重连；单击（连接中）= 重新开始；恢复态无手势。 |
| 5 | 行为 | 由状态机驱动：`connecting/warning/recovered`；恢复态显示后**定时自隐**（时长由调用方给，建议 3000ms，我方取值（P4 校准））；重连幂等（连接中重复点只重启一次）。 |
| 6 | 视觉状态 | connecting：点动画 + 「连接中…」；warning：error/warn 色 + 「连接已断开，点按重连」；recovered：success 色 + 「已重新连接」+ 退出 alpha；无 hover。 |
| 7 | 布局占位与展开形态 | **参与布局流、占位推挤内容**（G-01 只约束 overlay，本节点不违反，P2 §3-8 明确）；出现/消失会引起一次推挤（可接受，且必须用高度瞬时切换，不做高度动画）。 |
| 8 | 滚动语义 | 不滚；若在滚动区内随区滚（建议钉在壳层而非滚动区内）。 |
| 9 | 动效 | connecting 点动画循环；恢复态退出 alpha spring；**无高度动画**（G-10）；移除动画→静态点/瞬时消失。 |
| 10 | 边界态 | 离线/超时：三态覆盖；恢复后自隐；大字号：高增（44 最小）；横屏：同竖；深色：token。 |
| 11 | 无障碍 | 坏态 `role=Button` + `contentDescription="连接已断开，点按重连"`；恢复态 `liveRegion=Polite` + `contentDescription="已重新连接"`，且**不可聚焦**；E1（P4）。 |
| 12 | 依据 | `E0` DSH `P/ConnectionIndicator.tsx:55-103`（三态、坏态即按钮、恢复态 `role=status` 自隐、内联参与布局流）、`module.css:39,58`；P2 §2 connection-indicator 行（保留 + G-01 边界澄清）。 |
| 13 | 反证 + 验收 ID | **反证**：① 指示器不可点 → 断线后只能等自动重连，没有「立刻重试」出口；② 恢复态也给按钮 → 用户点到一个无动作控件；③ 若把它做成 overlay 浮层 → 会盖住内容且违反「内联状态条本不属 G-01 约束」的边界。**验收**：`BP-45` 断线点整条触发重连（真机 + mock 断线）；`BP-46` 恢复态播报一次、定时自隐、不可聚焦（TalkBack）；`BP-47` 出现/消失参与布局流（截图对比内容 y 位移）。 |

### 3.16 `base.primitives.switch` — 布尔开关（整行接管命中）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.switch`。出现：设置项布尔值；每个设置行 1 个；**label 必填**（无无名开关）。 |
| 2 | 网格与定位 | 轨道 36×20、滑块 16×16、内边距 2；**行**高 44、水平 16；开关在行尾、标签在行首；行内 gap 12。 |
| 3 | 命中区 | **整行 44**（路径 A）：开关本体视觉 36×20，但命中由行承担（本体 `shrinkWrap` 不吃命中）；行与上下行之间无其它可点节点 → 不相交。 |
| 4 | 手势 | 单击整行（含标签、空白、开关）= 切换；**无滑动切换**（DSH 未见，且与页面滚动冲突）；长按不绑。 |
| 5 | 行为 | 受控：`onChange(!checked)` 收到「请求的下一态」；写进行中整行禁用；写失败回弹原值（调用方保持原值）。 |
| 6 | 视觉状态 | off：轨道 outline；on：轨道 primary、滑块右移；**样式由 `value` 派生**（不许并行 class）；disabled：50% + 不可点；滑块 `translateX` 120ms（DSH E0）；按压反馈：行背景 pressed 色。 |
| 7 | 布局占位与展开形态 | inline 行，不展开；行高 44 恒定（大字号例外）。 |
| 8 | 滚动语义 | 不滚；随设置页滚。 |
| 9 | 动效 | 滑块 120ms（DSH E0）；**无轨道高度动画**；移除动画→瞬移。 |
| 10 | 边界态 | 写进行中：禁用 + 可选转圈；写失败：回弹 + toast；长 label：换行（行高增）；大字号：行高增、轨道不缩；横屏/深色：同态。 |
| 11 | 无障碍 | `Modifier.toggleable(value, role = Role.Switch)` 包整行；`stateDescription` 可选；E1（P4）。 |
| 12 | 依据 | `E0` DSH `P/Switch.tsx:1-42`、`module.css:5-49`（36×20、16 滑块、120ms、样式键挂 `aria-checked`）；`E0` dsh-Go 整行接管（`list_page_row.dart:214`、`overview_card.dart:155`，P2 引）；`E0` 太墟 miuix `SwitchPreference` 整行可点（`taixu.md` `preference-row` 字段 3）；`E0` G-16（44）。 |
| 13 | 反证 + 验收 ID | **反证**：只点 36×20 本体 → 行尾大片空白点不动（dsh-Go 字段 13 原话）；样式与状态用并行字段 → 出现「看起来开了、读屏说关了」。**验收**：`BP-48` 点标签/行尾空白都能切换（真机）；`BP-49` 视觉与 TalkBack 状态永远一致（截图 + TalkBack 同帧对照）；`BP-50` 滑动不切换、只滚动页面（真机手势）。 |

### 3.17 `base.primitives.checkbox` — 复选框（整 label 命中）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.checkbox`。出现：风险确认「我已了解」、多选列表；0..N 个（列表内每项 1 个）。 |
| 2 | 网格与定位 | label 横向：方框 16×16 + gap 6 + 文字 14/20；行高 44（可点），方框与首行文字中线对齐；多行 label 时方框对齐首行。 |
| 3 | 命中区 | **整个 label 行 44**（路径 A），含方框与文字；与相邻项间距 ≥0（列表项自身分隔）→ 不相交。 |
| 4 | 手势 | 单击方框或文字都切换；长按不绑（交给选字/父级）。 |
| 5 | 行为 | 受控 `onChange(checked)`；勾选是本地草稿还是立即写由调用方决定（D-10 按页声明，本原子不表态）。 |
| 6 | 视觉状态 | default：outline 方框；checked：primary 填充 + 对勾；disabled：整 label 50%；pressed：行 pressed 色；无 hover。 |
| 7 | 布局占位与展开形态 | inline，不展开。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无（勾选态瞬时切换；如需动效也属 §2.4 禁止项）。 |
| 10 | 边界态 | 空：不适用；长 label：换行、行高增；禁用：整 label 50%；大字号：方框 16 固定、文字换行；横屏/深色：同态。 |
| 11 | 无障碍 | `Modifier.toggleable(value, role = Role.Checkbox)` 包整 label；E1（P4）。 |
| 12 | 依据 | `E0` DSH `P/Checkbox.tsx:16-31`、`module.css:1-28`（原生 input + label 包裹、方框 16、gap 6）；`E0` G-16（44 行档）；P2 §2 checkbox 行（整 label 可点）。 |
| 13 | 反证 + 验收 ID | **反证**：只有 16×16 方框可点 → 手机上要精确命中 3mm 方块；用自绘 div 取代原生 → 勾选态与读屏语义要自己重造。**验收**：`BP-51` 点文字也切换（真机）；`BP-52` 禁用不响应且读屏报不可用（TalkBack）。 |

### 3.18 `base.primitives.input` — 受控文本输入 + 行内编辑

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.input`。出现：设置字段、行内重命名、任意受控文本；每字段 1 个。**尺寸与键盘避让归各面板**（composer 的几何不在此）。 |
| 2 | 网格与定位 | 外框高 **48**（`heightIn(min=48)`）、水平内边距 16、圆角 16、1dp 边框（focus 时 1.5dp primary）；内层文字 14/22；单行 / 多行（多行默认 1..6 行自适应，超过内滚）；错误行在下方 8dp、12/18 error 色。 |
| 3 | 命中区 | 整个输入容器（48 高 × 可用宽）= 命中盒（路径 D）；与相邻控件间距 ≥8 → 不相交。 |
| 4 | 手势 | 单击聚焦；长按 = 系统文本选择；拖动 = 文本选择/滚动；**IME 组合期不提交**（`isComposing`/keyCode 229）；Enter（IME action）= 提交；多行 Shift+Enter = 换行（外接键盘可选，不承诺）；**Esc（外接键盘）= 取消回滚原值**；触摸端取消出口 = 系统返回键（先取消编辑并回滚，再考虑关页）。 |
| 5 | 行为 | 完全受控；行内编辑有 `onCommit`/`onCancel` 两个意图；取消 = 回滚到进入编辑前的值；提交幂等（重复 Enter 不重复提交）。 |
| 6 | 视觉状态 | default：outline 边框 + surfaceContainer 底；focused：primary 边框；error：error 边框 + 错误文案；disabled：50%；placeholder：onSurfaceVariant 40%；无 hover。 |
| 7 | 布局占位与展开形态 | 参与布局流；多行输入长高会推挤下方兄弟（形态 A 变体）；**长高不做动画**（G-10）。 |
| 8 | 滚动语义 | 单行横向滚动（原生）；多行超过 6 行内滚，到边按 G-04 吸收、不外传给页面（避免「滚输入框把页面滚走」）。 |
| 9 | 动效 | 无（边框色瞬时切换；不做高度动画）。 |
| 10 | 边界态 | 空：placeholder；超长：单行横滚/多行内滚；IME 打开：由面板负责避让（composer）；失败：`aria-invalid` + 错误文案 + `role=status` 播报；大字号：高随 fontScale，错误行下推；横屏：同竖；深色：token。 |
| 11 | 无障碍 | `contentDescription = label`；错误 `stateDescription = 错误文案` + `semantics { error(...) }`（E1，P4 验）；E1。 |
| 12 | 依据 | `E0` DSH `P/Input.module.css:1-34`（32 高、0.5 边框、14/22）、`P/InlineEditor.tsx:33-69`（Enter 提交 / Shift+Enter 换行 / Esc 取消 / IME 组合期不提交）；`E0` G-16（48）；dsh-Go composer 6 行后内滚+到边不外传（`composer.dart:482-483`，E0，P2 引）。 |
| 13 | 反证 + 验收 ID | **反证**：IME 组合期不跳过提交 → 中文用户打拼音按 Enter 选词会提交半截内容（`P/InlineEditor.tsx:45-55` 反证原文）；取消不还原 → 用户按返回后看到草稿还在，以为已保存。**验收**：`BP-53` 中文 IME 组合期 Enter 不提交（真机）；`BP-54` 返回键取消后值回到进入编辑前（真机 + 单测）；`BP-55` 高 48 / focus 边框 / 错误态 aria-invalid + 播报（截图 + TalkBack）。 |

### 3.19 `base.primitives.segmented-control` — 少量模式切换（互斥单选；**不给 `Role.Tab`**）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.segmented-control`。出现：卡片/面板少量模式切换（如「文本/JSON」）、tool 详情页签；每处 1 个，段数 2..4。 |
| 2 | 网格与定位 | 容器高 **44**、圆角 16、内边距 4；段等分宽、段内文字 14/20 居中；指示器 = 圆角 12 的填充块；段间无间隔（靠指示器与内边距区分）。 |
| 3 | 命中区 | 每段 = 容器/N 宽 × 44（路径 A/D 组合）；段与段布局盒相邻不重叠 → 不相交。 |
| 4 | 手势 | 单击非选中段 = 选中并回调；**单击已选中段不回调**（幂等）；长按/双击不绑；**方向键/Home/End 不做**（D-09）；水平拖动不切换（触摸端不做 swipe）。 |
| 5 | 行为 | 受控 `value` + `onChange`；不会以当前值回调；切换只换内容面板（面板由调用方渲染）。 |
| 6 | 视觉状态 | 选中：primaryContainer 底 + 指示器位移；未选：透明底 + onSurfaceVariant；disabled：整组 40%；pressed：按压反馈；无 hover。 |
| 7 | 布局占位与展开形态 | inline 水平，不展开；切换只换下游内容；**段出现/消失不做动画**（G-10）。 |
| 8 | 滚动语义 | 不滚；段数>4 时压缩等分（文字 ellipsis），不横向滚。 |
| 9 | 动效 | 指示器位移 spring 0.86/420；无尺寸动画；移除动画→瞬时跳位。 |
| 10 | 边界态 | 只有 1 段：**不渲染**（调用方责任，本原子断言 ≥2 才渲染）；全部禁用：整组禁用；大字号：文字跟随 fontScale，容器高 = max(44, 文字+20)；横屏：同竖；深色：token。 |
| 11 | 无障碍 | 容器 `Modifier.selectableGroup()`；每段 `Modifier.selectable(selected, role = Role.Button)`；**不给 `Role.Tab`、不做 roving tabindex、不做方向键**（D-09：宁可不给语义，也不给「假选项卡」）；E1（P4）。 |
| 12 | 依据 | `user` D-09（`decision-pack.md:363`）+ P2 §2 segmented-control 行；`E0` dsh-Go 分段最小高 44（`dsh-go.md` §5.1 引 `AT:321-331`）、`SegmentedPill`（`segmented_pill.dart:137,233`）；`E0` DSH 契约（`P/SegmentedControl.tsx:26-54,56-140`，注入 tab 语义前的行为：点已选中不回调）；`E0` DSH 注释反证「只给 role 不给方向键比不加更糟」（`TabPanel.tsx:103-107`，P2 引）。 |
| 13 | 反证 + 验收 ID | **反证**：给 `Role.Tab` 却不做方向键 → 读屏报「选项卡，共 3 个」而方向键毫无反应，比不加语义更糟（DSH 注释原话）；点已选中项也回调 → 无意义重渲染/重取数。**验收**：`BP-56` 源码断言无 `Role.Tab`/无 `onKeyEvent`/无 roving（静态）；TalkBack 报「已选中/未选中」而非「选项卡」（P4）；`BP-57` 点已选中段不回调（单测）；`BP-58` 字号 200% 与横屏下段文字不裁切（截图）。 |

### 3.20 `base.primitives.tab-strip` — **不建节点**（决议记录）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.tab-strip`。P2 §1 登记为「页签条的语义原子」；**首版不建**：① myco 首版无右栏多页签（P2 §1 tab-strip 行「无右栏/无页签」）；② D-09 决定不给 `Role.Tab`、不做 roving/方向键，本节点的核心契约（`tablist`+roving+manual activation）已被砍掉。 |
| 2 | 网格与定位 | 不适用（不建节点）。若将来出现页签，**复用 `segmented-control` 的落法**（44 高、`selectableGroup` + `Role.Button` + `selected`）。 |
| 3 | 命中区 | 不适用。 |
| 4 | 手势 | 不适用。 |
| 5 | 行为 | 不适用。 |
| 6 | 视觉状态 | 不适用。 |
| 7 | 布局占位与展开形态 | 不适用。 |
| 8 | 滚动语义 | 不适用。 |
| 9 | 动效 | 不适用。 |
| 10 | 边界态 | 不适用。若未来重启：必须同时回答 Q-BP-05（键盘模式）且不得「给了 tablist 却没有键位」。 |
| 11 | 无障碍 | 不适用（替代 = `segmented-control` 的 `Role.Button`+selected）。 |
| 12 | 依据 | `user` D-09（`decision-pack.md:363`）；P2 §1 tab-strip 行、§2 tab-strip 行（⏸ Q-BP-05 → 被 D-09 消解）；`E0` DSH 唯一完整 WAI-ARIA 实现（`TabPanel.tsx:245,263-266,103-124,293-322`）仅作宽屏参考。 |
| 13 | 反证 + 验收 ID | **反证**：给了 `role=tablist` 却不实现方向键 → 假控件（DSH 注释原话）；首版凭空做右栏页签 → 无使用场景的死节点。**验收**：`BP-59` 源码断言首版不存在 `Role.Tab`/`tablist`/roving tabindex（静态扫描）。 |

### 3.21 `base.primitives.content-fold` — 长内容块：限高内滚（280）+ 全文页

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.content-fold`。出现：代码块、终端输出、read/diff/search 结果、JSON **文本**渲染；每块 1 个；出现条件 = 内容超过限高或超长。**变体**：`code`（横向可滚 + 24dp 渐隐 + 头部复制/换行）、`text`（头 60/尾 140 行截断）、`diff`（按文件折叠）。 |
| 2 | 网格与定位 | 块 = header 44（可选：语言/文件名 + 动作键 40）+ 内容体 + 井底条 44；内容体 `maxHeight = 280`、内边距 16/12（代码）；圆角 16、1dp 边框；井底条：文字 13/20 + 「查看全部 ›」primary 13/20，水平 16。 |
| 3 | 命中区 | ①header 动作键 40×40（`icon-action`，路径 D）；②井底条**整条 44 可点**（路径 A，dsh-Go `minHeight:44` E0）；③内容体本身不可点（长按选字）；④代码块横向拖动 ≠ 点击。彼此不相交。 |
| 4 | 手势 | 内容体：纵向拖动 = 内滚（到边按 G-04 吸收，不外传）；横向拖动 = 代码横滚（不折行时；**横向永不转交纵向**）；长按 = 选择文本；井底条单击 = 进全文页；header 键 = 复制/换行。 |
| 5 | 行为 | 全文页是**独立 route**（进 M2 栈），返回 = pop 回原块且**保持原滚动位置**；复制的成败反馈见 `icon-action`；`code` 变体换行切换是块本地状态。 |
| 6 | 视觉状态 | default：3 种底色变体（well/outlined/card，DSH 代码卡 E0）；横滚到边：左右 24dp 渐隐（G-05 同族，不用省略号）；复制成功：对勾 1000ms；无 hover。 |
| 7 | 布局占位与展开形态 | **形态 B（capped-inner-scroll）**：参与布局流，高度封顶 280（或内容更矮时取内容高）；内滚优先、不原地展开（Q-BP-08 解 B + P2 §3-10 三段式）；井底条固定不随内容滚。 |
| 8 | 滚动语义 | 内层优先滚；**到边吸收剩余拖动/惯性，不传给外层**（G-04 铁律；Compose 需禁用 overscroll 传播或自实现到边吸收，P4 验）；横向滚动独立、不转交；锚定元素 = header（滚动时 header 不离开块）。 |
| 9 | 动效 | **无展开/收起高度动画**（G-10）；内滚无色变过渡；横向渐隐随滚动即时变化（非动画）。 |
| 10 | 边界态 | 空内容：不渲染块（或空态行）；超长：头 60 行 + 尾 140 行 + 总 16000 字截断 + 「查看全部」；超长单行：横向滚动/按字截断；大字号：**井高仍 280**（不随字号涨，避免吃掉整屏），行高变高 → 可视行变少；横屏：井宽变宽、高仍 280；深色：3 变体各有深色值。 |
| 11 | 无障碍 | 井底条 `role=Button` + `contentDescription="查看全部，共 N 行"`；header 键 `contentDescription`；内容文本可逐行读屏；E1（P4）。 |
| 12 | 依据 | `E0` dsh-Go 井 280（`tool_row.dart:32,465`）、井底条 44（`long_text.dart:261-307`）、头 60/尾 140/16000 字（`long_text.dart:14-19`）、横向 24 渐隐（`edge_fade.dart:53-67`）、到边转交/不转交（`code_block.dart:577-583` + `overscroll_forwarder.dart:24-37`）；`E0` DSH 代码卡/ReadBlock 形态 B（`P/TerminalBlock.module.css:47,157`、`P/ReadBlock.tsx:104-150`）；`user` Q-BP-08 解 B（内滚优先）+ G-04 + G-10；`E1` Compose 到边吸收实现（P4 验）。 |
| 13 | 反证 + 验收 ID | **反证**：① 不封顶 → 一段 2000 行输出把会话拉成几十屏、回底钮永远可用（G-03「B 做成 A」失效）；② 折叠优先（Q-BP-08 解 A）→ 内容块永久绕过 G-04，用户在块内滚到边会把整页带走；③ 若把 JSON/长文本一次性排版 → 布局卡死（dsh-Go 注释原话：`Laying out a megabyte of text as one paragraph is what froze the list`）。**验收**：`BP-60` 1MB 输出内滚到底后继续拖**不带走会话**（真机录屏）；`BP-61` 井底条 44 高可点进全文页、返回保住原滚动位置（真机）；`BP-62` 代码块横向渐隐无省略号、横滑不改纵向位置（截图 + 真机）；`BP-63` 展开/收起无高度动画（录屏逐帧）。 |

### 3.22 `base.primitives.json-tree` — **不建节点**（决议记录）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.json-tree`。P2 §1 曾登记；**首版不建**（P2 §4-5）：触摸端两条核心交互（右键复制菜单、硬件键盘漫游 tabIndex）不可达。 |
| 2 | 网格与定位 | 不适用。**替代**：JSON 走 `prettyPrint` 文本 → `content-fold`（限高 280 内滚 + 全文页）。 |
| 3 | 命中区 | 不适用。 |
| 4 | 手势 | 不适用。替代：长按选字 + 复制全部（全文页动作）。 |
| 5 | 行为 | 不适用。 |
| 6 | 视觉状态 | 不适用。 |
| 7 | 布局占位与展开形态 | 不适用。 |
| 8 | 滚动语义 | 不适用（替代形态的内滚由 `content-fold` 给）。 |
| 9 | 动效 | 不适用。 |
| 10 | 边界态 | 不适用。已知代价：嵌套深的结构只能看缩进文本，不能逐层折叠（P2 已接受）。 |
| 11 | 无障碍 | 不适用。 |
| 12 | 依据 | P2 §4-5（`P/JsonTree.tsx:117-165,380,595-628,663-664` E0 为不做依据）；太墟 `prettyPrint` 进详情（`ToolDetailsSheet.kt:111`，E0，`taixu.md` 引）。 |
| 13 | 反证 + 验收 ID | **反证**：做整棵原语 → 右键菜单与漫游 tabIndex 在触摸/无键盘设备上是死功能，维护成本换不到可达性。**验收**：`BP-64` 源码断言不存在 json-tree 组件，JSON 显示走 `content-fold`（静态 + 真机截图）。 |

### 3.23 `base.primitives.markdown-link` — 正文内可点文本（链接/文件 chip）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.markdown-link`。出现：正文外链、本地文件链接、文件提及 chip、引用 chip；每处 0..N。 |
| 2 | 网格与定位 | 内联，继承正文 14/22；chip 形式：inline 背景 + 圆角 8 + 水平 4；图标 16 与文字 gap 4。 |
| 3 | 命中区 | **路径 B（自绘命中层，我方方案，P4 原型验证）**：文字行盒上下各扩 e，使正文链接命中 **≥44**；chip 命中 ≥44（同法）。**若 P4 证明 Compose 不可行** → 见 §5 TBC-01（阻塞，不自行降级改排版）。 |
| 4 | 手势 | 单击 = 打开（外链→系统浏览器；本地文件→文件打开；chip→跳转/预览）；**双击（detail>1）或有文本选区时不跳转**（DSH `user-text` 反证）；长按 = 选字（父级 SelectionContainer）；**无修饰键分支**（P2 §4-4）。 |
| 5 | 行为 | 只读渲染 + 宿主 delegate 打开；**流式期间本地链接不可点**（半截路径）；task 复选框 `disabled` 只表达状态。 |
| 6 | 视觉状态 | default：primary 色（对比 ≥4.5）；pressed：下划线或底色；无 hover（D-09）；流式期：本地链接降为普通文本色。 |
| 7 | 布局占位与展开形态 | 内联，不展开；不改变行高（命中层不改布局）。 |
| 8 | 滚动语义 | 随正文滚；命中层不拦截滚动（只消费 tap，拖动归父滚动）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 非法/未知 scheme：保持惰性文本；超长 URL：折行；流式：不可点；失败：打开失败 toast；大字号：行高随 fontScale、命中层同步扩；横屏/深色：token。 |
| 11 | 无障碍 | 外链 `role=Button` + `contentDescription` 含「链接」；本地文件是按钮语义（不报「链接」）；chip `contentDescription` 含文件名；E1（P4）。 |
| 12 | 依据 | `E0` G-16（正文链接 ≥44）；`E0` dsh-Go 自绘 `RenderProxyBox` 补 48 手法（`relay_credit.dart:135-139,174-259`）；`E0` DSH 语义分支（`P/markdown/render.tsx:556-608`：外链 `<a>`、本地文件 `<button>`、流式不解析 `:573-574`）、`P/user-text.tsx:127-150`（双击/有选区不跳转）；P2 §4-4（不做修饰键）；**Compose 落法 = 我方方案，P4 验证**（Q-BP-06 ④）。 |
| 13 | 反证 + 验收 ID | **反证**：① 链接命中只有文字行高（22）→ 手指点不中，连续两次点不中用户就放弃（G-16 反证）；② 双击不判断 → 用户想选字复制却跳转；③ 流式期解析半截路径 → 点到错文件。**验收**：`BP-65` 外链报「链接」、本地文件报按钮、引用 chip 双击不跳转（TalkBack + 真机）；`BP-66` 流式期间本地链接不可点（真机录屏）；`BP-67` 正文链接命中 ≥44（P4 原型量测；不达标按 TBC-01 处理）。 |

### 3.24 `base.primitives.settings-field` — 设置表单字段（label + 控件 + 帮助 + 重置）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.settings-field`。出现：设置页每个可编辑项 1 个；字段型（文本/密钥/选择）+ 帮助区 + 重置入口。**保存语义不在此**（D-10 按页声明归 settings）。 |
| 2 | 网格与定位 | 纵向：label 行（label 14/20 + 可选重置文字键 44 高）→ 控件（`input`/`switch`/`segmented`）→ 帮助区（可展开）；字段间距 16、水平 16；错误行在控件下 8。 |
| 3 | 命中区 | 控件自身（48 或 44）+ 帮助钮 44×44（图标 20）+ 重置键 48 高 × ≥64 宽；彼此间距 ≥8 → 不相交；行内 label **不**另绑点击（本字段不是整行可切换的开关行，那个由 `switch` 承担）。 |
| 4 | 手势 | 单击帮助钮 = 展开/收起说明；单击重置 = 清除用户覆盖；单击控件 = 各自语义；长按不绑。 |
| 5 | 行为 | 受控（草稿由表单持有）；重置入口仅在 `overridden` 时出现；`「继承默认」` 是下拉里的显式选项（不是清空文本）；写失败保持草稿 + 错误行。 |
| 6 | 视觉状态 | default/focused/invalid/disabled 随控件；帮助区展开时箭头旋转（瞬时，无动画）；重置键 ghost 变体；无 hover。 |
| 7 | 布局占位与展开形态 | 帮助区 inline 挂载、下方字段整体下移；**不做高度动画**（G-10 明确「设置项帮助区」）；不封顶（说明短）。 |
| 8 | 滚动语义 | 随设置页滚；textarea 类控件自带内滚（1..8 行）。 |
| 9 | 动效 | 无（帮助区直接显示；箭头瞬时翻）。 |
| 10 | 边界态 | 无效值：error 边框 + 错误行 + `role=status`；密码字段永不回显（`type=password` + 不回显已存值）；超长 label 换行；大字号：label 换行、控件高增；横屏：字段宽 = 列宽；深色：token。 |
| 11 | 无障碍 | label 与控件同一语义节点（`contentDescription = label`）；帮助钮 `stateDescription`（展开/已折叠）+ 展开区 `paneTitle`/`contentDescription`；错误 `error(...)`；E1（P4）。 |
| 12 | 依据 | `E0` DSH `P/settings-form/fields.tsx:64-115,125-151`（label 上下排、help `aria-expanded`+`role=region`、`invalid`→错误文案替换 hint、重置仅 overridden）、`P/ConfigField.tsx:33-53`（密码只写不回显、`「继承默认」`是选项）、`P/settings-form/SettingsForm.tsx:1-74`（无放弃按钮）；`E0` 手机端整行可点先例（dsh-Go `list_page_row.dart:145,214`、太墟 `preference-row`）；P2 §2 settings-field 行。 |
| 13 | 反证 + 验收 ID | **反证**：① 帮助区做高度动画 → 设置页连点帮助会持续掉帧（G-10 明令）；② 密码回显已存值 → 凭据出现在屏幕上可被截图；③ 重置按钮在未覆盖时也显示 → 点了没变化，用户以为坏了。**验收**：`BP-68` 字段间距/控件命中达标、帮助区展开无动画且下方字段位移（真机录屏）；`BP-69` 帮助钮 TalkBack 报已展开/已折叠、焦点顺序为 label→控件→帮助（TalkBack）；`BP-70` 无效值触发 aria-invalid + 播报、密码字段初始为空（真机 + 截图）。 |

### 3.25 `base.primitives.form-save` — 显式保存按钮（唯一写入点）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.form-save`。出现：显式保存页的底部按钮区（按 D-10 声明的页）；每页 1 个；**无「放弃」按钮**（P2 §2 form-save 行、D-10）。 |
| 2 | 网格与定位 | 按钮区钉底（宽满、水平 16、上下 12）；主按钮高 48、最小宽 64；saving 时转圈替换文字；与内容区之间 1dp 分隔线（可选）。 |
| 3 | 命中区 | 按钮 48 高 × ≥64 宽（路径 D）；与相邻按钮间隔 8。 |
| 4 | 手势 | 单击 = 保存（仅在可保存态）；不可保存态点击被吞（不冒泡）；长按不绑。 |
| 5 | 行为 | 状态机：可点 = `dirty && !invalid && !saving`；点击后 `saving=true` → 成功清 dirty、失败保留草稿 + `role=status` 播报 + 主按钮恢复可点；**幂等**（saving 期间重复点击忽略）。 |
| 6 | 视觉状态 | disabled（clean/invalid/saving）：40%；enabled：primary；saving：转圈 + disabled；失败：可配 error 边框或错误行；无 hover。 |
| 7 | 布局占位与展开形态 | 固定占位（钉底条高度不变，转圈不改变高度）→ 不推挤内容。 |
| 8 | 滚动语义 | 不滚；内容区自己滚（按钮区固定）。 |
| 9 | 动效 | 无高度动画；saving 转圈循环；移除动画→静态忙态。 |
| 10 | 边界态 | clean：disabled；invalid：disabled + 错误在字段上；saving：disabled；失败：保留草稿、可重试；卸载：草稿丢弃由 `dialog-dismiss` 拦截；大字号：高 48 不缩；横屏/深色：同态。 |
| 11 | 无障碍 | `role=Button`；disabled 用 `disabled()` + `stateDescription` 原因（未修改/有错误/保存中）；失败 `liveRegion=Polite`；E1（P4）。 |
| 12 | 依据 | `E0` DSH `P/settings-form/SettingsForm.tsx:1-74`（唯一写入点、`dirty && !invalid && !saving`、无放弃按钮、失败保留草稿+`role=status`、卸载丢草稿）；`user` D-10（按页声明）；P2 §2 form-save 行（给 M6 的输入：手机端默认即时写，显式保存只出现在配置类表单页）。 |
| 13 | 反证 + 验收 ID | **反证**：① 省略 disabled 条件 → 用户在密钥未填完时点保存，弱网半成品写进去；② 给「放弃」按钮 → 与 D-10「无放弃按钮 + 离开 dirty 确认」重复且两条互斥路径；③ 失败清空草稿 → 用户重填一遍。**验收**：`BP-71` 三态（clean/invalid/saving）都不可点且读屏有原因（单测 + TalkBack）；`BP-72` 失败后草稿仍在、主按钮可重试、播报失败（真机 mock 失败）。 |

### 3.26 `base.primitives.hit-target` — 命中盒包裹层（唯一扩边落点）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.hit-target`。**非视觉原语**：任何需要扩边/抬命中盒的可点原子在内部使用它；不单独出现在界面上。 |
| 2 | 网格与定位 | 三种修饰符（一一对应 §1.4 的路径）：`hitTargetRow(min = 44.dp)`（整行抬）、`hitTargetBleed(h = 8.dp)`（横向加宽）、`hitTargetMin(min = 36.dp)`（单点抬）；均在**布局层**生效（`heightIn`/`padding`），不改变视觉子元素尺寸。 |
| 3 | 命中区 | 输出必须满足 G-11 三式：`h + 2e ≥ F`、`2e ≤ g`、与相邻命中盒不相交（G-16 取消固定 B，间距由内边距给）。 |
| 4 | 手势 | 不绑手势；手势归宿主 `clickable`/`toggleable`。 |
| 5 | 行为 | 纯布局修饰；**不得**用 `Modifier.offset`/`graphicsLayer`/绘制层冒充扩边（G-11 明令：这些不改变命中盒）；**不得**在此层加语义节点。 |
| 6 | 视觉状态 | 无。 |
| 7 | 布局占位与展开形态 | 会改变宿主布局尺寸——**使用前必须在本面板对应节点的字段 2 里声明代价**（行高从 32→36？整行 44 会少放几行？）。 |
| 8 | 滚动语义 | 不涉及。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 装不下时按 G-11 降级顺序退：① 加大间距 g → ② 关键区可点（只让标题+箭头响应）→ ③ 抬视觉高；**禁止硬扩边导致重叠**（「点大反而变小」，mobile-adapt issue #2）。大字号下命中不得缩水。 |
| 11 | 无障碍 | 不加语义（`clearAndSetSemantics` 也不加，避免产生空节点）；语义归宿主；E1（P4）。 |
| 12 | 依据 | `E0` dsh-Go 三手法（`session_tile.dart:88-118`、`relay_credit.dart:135-139,174-259`、`bleed.dart:6-13,40-47,87-96`，`dsh-go.md` §5.3）、太墟 `sizeIn(48,48)` 助手（`Buttons.kt:171`）及其「参与布局测量会撑高行」的自述（`taixu.md` `action` 字段 7）；`E0` G-11 + G-16；`E1` 路径 B 的 Compose 等价物（Q-BP-06 ④）。 |
| 13 | 反证 + 验收 ID | **反证**：① 用 `Modifier.offset` 扩边 → 视觉变大、命中盒没变，验收永远过不了（G-11）；② 硬扩边到命中盒重叠 → 点击落在重叠带被最后绘制者吃掉，点 A 触发 B（比点不到更糟，G-11 反证）。**验收**：`BP-73` 静态断言：所有扩边实现只用布局属性（无 offset/graphicsLayer 扩边）、每个可点原子声明了所用路径（源码 + 单测）；`BP-79` 自动几何测试：遍历同屏原子对，命中矩形不相交（组合单测）。 |

---

### 3.27 `base.primitives.disclosure.leading` — **不建节点**（决议记录，与 3.1 同族）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.disclosure.leading`。P2 §1 曾登记为「只有 16×16 图标是开关」的形态；**首版不建**：myco 的披露行收窄为唯一「整行即开关」形态（3.1）。依据：dsh-Go / 太墟 都只有整行形态（2:1 对 DSH），且首版无「行内带 accessory 的披露行」场景（P2 §4-7）。 |
| 2 | 网格与定位 | 不适用（不建节点）。若出现带 accessory 的行，accessory 按**独立 `icon-action`（40）**或行内次级（≥36）排布，其命中盒必须与整行命中区分层且不相交。 |
| 3 | 命中区 | 不适用。反契约（**禁止**）：16×16 独立可点目标（`(16+delta)²`，约为 44dp 目标的 1/7 面积）。 |
| 4 | 手势 | 不适用。披露手势只由整行承担（3.1 字段 4）。 |
| 5 | 行为 | 不适用。 |
| 6 | 视觉状态 | 不适用。chevron 只需常显 + 随开合翻向（无 hover 换图标，P2 §4-6）。 |
| 7 | 布局占位与展开形态 | 不适用。 |
| 8 | 滚动语义 | 不适用。 |
| 9 | 动效 | 不适用（顺带砍掉 DSH 的 100ms 图标↔chevron 过渡）。 |
| 10 | 边界态 | 不适用。已知缺口：无「行内 accessory 只点图标」的表达；若某面板确需，改用行内次级键（≥36）并单独验收。 |
| 11 | 无障碍 | 不适用。语义由整行承担（3.1 字段 11）。 |
| 12 | 依据 | P2 §4-7（`P/DisclosureRow.module.css:50-63`、`P/DisclosureRow.tsx:99-108` E0 为「不做」的依据）；`E0` dsh-Go / 太墟 均无此形态（2:1）；P2 §2.1「独立可点原子 ≥36」硬规则。 |
| 13 | 反证 + 验收 ID | **反证**：保留 16×16 形态 → 手机上可点面积只剩 20~30dp 时功能等于「找不到」（dsh-Go 字段 13 原话），且必须为它再造一套扩边方案（与 G-11 打架）。**验收**：`BP-07` 源码断言不存在独立 16×16 披露开关形态（含「只有图标绑定 onClick」的写法）；`BP-01` 该行若出现 accessory，其命中盒与整行不相交（几何单测）。 |

---

## 4. 验收清单

> 判定式一律写死；方式列取值：**真机**（手势/录屏）、**截图**（对比/量测）、**单测**（含几何单测）、**TalkBack**、**源码断言**（静态扫描/代码评审门）。

| ID | 判定 | 方式 | 依赖 |
|---|---|---|---|
| BP-01 | 同屏任意两个可点原子的命中矩形**不相交**（遍历 §1.2 全部类别） | 单测（几何） | §1.4、`hit-target` |
| BP-02 | `disclosure.row` 折叠行高恒定：摘要逐字变化时行顶 y 位移 ≤1dp | 真机录屏 | G-02、3.1 |
| BP-03 | `disclosure.row` 点行内任意 x（含 Bleed 8 区间）都切换 | 真机 | G-16、3.1 |
| BP-04 | 展开后头部吸附：向下滚 3 屏后点头部可收起；若使用方启用 D-11=C（形态 D），stream 由 true→false 一次性切到不封顶后仍可收起、且切换过程无高度动画 | 真机 | G-06、D-11、3.1 |
| BP-05 | TalkBack 报「思考，已折叠/已展开」 | TalkBack | 3.1 字段 11 |
| BP-06 | 运行中播报「正在思考」一次，结束不重复 | TalkBack | G-08 |
| BP-07 | 源码断言不存在 16×16 独立图标开关形态、不存在 hover 换图标 | 源码断言 | P2 §4-6/4-7 |
| BP-08 | `fold-toggle` 点击后文案与 `stateDescription` 同步翻转，命中 ≥36 高 | 真机 + TalkBack | 3.2 |
| BP-09 | 四变体按钮高 48、minWidth 64、disabled 不触发 onClick | 截图 + 单测 | G-16、3.3 |
| BP-10 | 字号 200% 下四变体不裁切、不重叠 | 截图 | 3.3 |
| BP-11 | 选中 chip TalkBack 报「已选中」，命中 ≥36 | TalkBack + 量测 | 3.4 |
| BP-12 | 只读徽标不在焦点序、不被报成控件 | TalkBack | 3.4 |
| BP-13 | 每个 `icon-action` 键实测 40×40；disabled 不触发 | 量测 + 单测 | 3.5 |
| BP-14 | 复制成功播报一次 + 对勾 1000ms 后复原 | 真机 + TalkBack | 3.5 |
| BP-15 | 复制失败有失败态且播报（非静默） | 真机（mock 失败） | 3.5 |
| BP-16 | 操作条触摸端常驻；源码无 hover 显隐；显隐零布局位移 | 截图 + 源码断言 | D-19、3.6 |
| BP-17 | 一轮多条助手消息时只有最后一条有操作条 | 真机 | D-19、3.6 |
| BP-18 | 360dp 宽 6 键一行不换行；键命中盒不相交 | 几何单测 + 截图 | 3.6 |
| BP-19 | 源码无 tooltip 触发路径；每个图标键有 `contentDescription` | 源码断言 + TalkBack 抽查 | 3.7 |
| BP-20 | 源码无 hover-card 组件/hover 触发路径 | 源码断言 | 3.8 |
| BP-21 | Modal：框内按下→遮罩松手**不关**；遮罩按下+松手**关** | 真机手势 | D-08/G-18、3.9 |
| BP-22 | Modal 下滑不关 | 真机 | D-03 |
| BP-23 | Modal 返回键关闭；开浮层后焦点不进背后页面 | 真机 + TalkBack | 3.9 |
| BP-24 | Modal 关闭后焦点回触发按钮 | TalkBack | 3.9 |
| BP-25 | Modal 内容 3 屏时卡不超屏、内容内滚、按钮区固定 | 截图 + 真机 | 3.9 |
| BP-26 | `dialog-dismiss`：clean 直接关、dirty 弹确认 | 真机 + 单测 | D-10 |
| BP-27 | 脏检查覆盖返回键/侧滑/遮罩三条出口（各 1 次验证） | 真机 | G-12、D-10 |
| BP-28 | Sheet：内滚不在顶时下滑不关；在顶时下滑关 | 真机 | G-13 |
| BP-29 | Sheet：遮罩按下+松手才关；返回键关 | 真机 | D-08 |
| BP-30 | Sheet 底部间距 = 系统 inset（三键/手势两种导航一致、无双倍留白） | 截图（两种导航模式） | 3.11 |
| BP-31 | Sheet 3 屏内容高 ≤0.8 屏、内滚、按钮钉底 | 真机录屏 | 3.11 |
| BP-32 | 手机菜单为底部 sheet 单层；源码无子菜单/popup 路径 | 源码 + 真机 | P2 §4-2 |
| BP-33 | 菜单项 44 高、危险项可辨、选中项播报已选中 | 真机 + TalkBack | 3.12 |
| BP-34 | 菜单关闭后焦点回锚点按钮 | TalkBack | 3.12 |
| BP-35 | 宽屏 popup 路径在首版不存在（若实现需 ≥600dp 且焦点契约达标） | 源码断言 | 3.12 |
| BP-36 | Toast 出现时下方内容仍可点（不拦指针） | 真机 | G-01、3.13 |
| BP-37 | Toast 动作命中 ≥44 | 量测 | 3.13 |
| BP-38 | Toast 出现即播报一次；同时最多 1 条（新替旧） | TalkBack | 3.13 |
| BP-39 | 无动作 3000ms / 有动作 6000ms 消失；返回键不被消费 | 真机计时 | 3.13 |
| BP-40 | Lightbox 双击 1×↔2×，位置居中到点按处 | 真机 | 3.14 |
| BP-41 | 缩放态横滑不翻页；1× 时横滑翻页 | 真机录屏 | 3.14 |
| BP-42 | Lightbox 遮罩按下+松手才关；关闭钮 44 可点；返回键关 | 真机 | D-08、3.14 |
| BP-43 | Lightbox 关闭后焦点回缩略图 | TalkBack | 3.14 |
| BP-44 | 图片加载失败有占位 + 重试且关闭仍可用 | 真机（mock） | 3.14 |
| BP-45 | 断线时点整条触发重连 | 真机（mock 断线） | 3.15 |
| BP-46 | 恢复态播报一次、定时自隐、不可聚焦 | TalkBack | 3.15 |
| BP-47 | 指示器出现/消失参与布局流（内容 y 有位移且非 overlay） | 截图 | 3.15 |
| BP-48 | 点开关行任意位置（标签/空白/本体）都切换 | 真机 | 3.16 |
| BP-49 | 开关视觉与 TalkBack 状态同帧一致（无「看起来开、读屏关」） | 截图 + TalkBack | 3.16 |
| BP-50 | 行内滑动不切换开关、只滚动页面 | 真机手势 | 3.16 |
| BP-51 | Checkbox 点文字也切换 | 真机 | 3.17 |
| BP-52 | Checkbox 禁用不响应且播报不可用 | 真机 + TalkBack | 3.17 |
| BP-53 | 中文 IME 组合期 Enter 不提交（选词不误提交） | 真机 | 3.18 |
| BP-54 | 返回键取消编辑后值回滚到进入编辑前 | 真机 + 单测 | 3.18 |
| BP-55 | Input 高 48、focus 边框、错误态 aria-invalid + 播报 | 截图 + TalkBack | 3.18 |
| BP-56 | Segmented 源码无 `Role.Tab`/无方向键/无 roving；TalkBack 报「已选中」 | 源码断言 + TalkBack | D-09 |
| BP-57 | 点已选中段不回调 | 单测 | 3.19 |
| BP-58 | 字号 200% / 横屏下段文字不裁切 | 截图 | 3.19 |
| BP-59 | 源码不存在 `tablist`/`Role.Tab`/roving tabindex | 源码断言 | D-09 |
| BP-60 | 1MB 输出内滚到边继续拖**不带走会话**（G-04） | 真机录屏 | G-04、3.21 |
| BP-61 | 井底条 44 高可点进全文页；返回保住原滚动位置 | 真机 | 3.21 |
| BP-62 | 代码块横向渐隐无省略号；横滑不改变纵向位置 | 截图 + 真机 | G-05、3.21 |
| BP-63 | `content-fold`/帮助区/fold-toggle 展开无高度动画（逐帧对比） | 录屏逐帧 | G-10 |
| BP-64 | 源码无 json-tree；JSON 走 `content-fold` 文本渲染 | 源码断言 + 截图 | P2 §4-5 |
| BP-65 | 外链报「链接」、本地文件报按钮、引用 chip 双击不跳转 | TalkBack + 真机 | 3.23 |
| BP-66 | 流式期间本地链接不可点 | 真机录屏 | 3.23 |
| BP-67 | 正文链接命中 ≥44（路径 B 原型量测） | 量测（P4 原型） | §1.4 路径 B、TBC-01 |
| BP-68 | 设置字段控件/帮助钮命中达标；帮助区展开无动画且下方字段位移 | 真机录屏 | 3.24 |
| BP-69 | 帮助钮 TalkBack 报开合；焦点序 label→控件→帮助 | TalkBack | 3.24 |
| BP-70 | 无效值触发 aria-invalid + 播报；密码字段初始为空 | 真机 + 截图 | 3.24 |
| BP-71 | `form-save`：clean/invalid/saving 均不可点且播报原因 | 单测 + TalkBack | D-10、3.25 |
| BP-72 | 保存失败后草稿仍在、可重试、播报失败 | 真机（mock 失败） | 3.25 |
| BP-73 | 所有扩边只用布局属性（无 offset/绘制层扩边）；每原子在代码注释里声明所用路径 A/B/C/D | 源码断言 | G-11、§1.4 |
| BP-74 | 系统「移除动画」开启后，§2.4 表内全部过渡 0 帧完成（逐原子遍历） | 真机（设置→移除动画） | G-10、§2.4 |
| BP-75 | 深色模式：全部原子文字/图标对比 ≥4.5:1，遮罩仍为 0.32，无硬编码色 | 截图 + 源码断言 | §2.1、D-05 |
| BP-76 | 字号 200%：全部原子不裁切/不重叠/命中不缩水 | 截图矩阵 | §2.1 |
| BP-77 | 横屏（高 412dp）：Sheet/Modal/Lightbox/Toast 不超屏、内容可滚、关闭入口可达 | 截图 + 真机 | D-04、§1.3 |
| BP-78 | 每个浮层：打开→TalkBack 焦点陷阱生效→关闭→焦点回触发元素（4 类浮层各 1 次） | TalkBack | §2.2 |
| BP-79 | 浮层返回优先级：有浮层时返回键只关浮层、不 pop route | 真机 | D-01、§1.3 |

**覆盖自检**：27 个 P2 节点 → **27 节**（3.1–3.27；其中 3.7/3.8/3.20/3.22/3.27 为「不建节点」的 13 字段决议记录，仍占节）；验收 **79 项**（BP-01…BP-79）；关闭语义（§2.2 + BP-21…BP-44/78/79）、命中区（§1.4 + BP-01/03/13/61/67/73/79）、动画（§2.4 + BP-63/74）、无障碍（§2.3 + BP-05/06/14/19/23/24/34/38/43/46/49/56/65/69/78）四类各有 ≥1 条可判定项。

---

## 5. 待确认（若有）

| ID | 问题 | 两解与后果 | 是否阻塞 |
|---|---|---|---|
| **TBC-01** | **`markdown-link` 路径 B（自绘命中层补 hitTest）在 Compose 是否可行**（Q-BP-06 ④）。P2 只取证到 dsh-Go 用 `RenderProxyBox`（Flutter 私有），Compose 等价物未取证。 | **解 A（路径 B 成立）**：正文链接命中 ≥44 且视觉不变（本文件当前写法）。<br>**解 B（不成立）**：只能二选一——① 抬正文行高到 44（改变整篇正文排版密度，属 `chat` 面板的排版契约）；② 接受链接命中 <44 并登记为已知缺口（与 tooltip 缺口同级）。**两者都必须改别的面板/新增缺陷记录，不是本面板能定的**。 | **阻塞**（阻塞 `markdown-link` 实现与 BP-67；不阻塞其它 26 节点）。建议 P4 在正式实现前先做该最小原型（P2 §6 Q-BP-06 建议解 B）。 |
| **TBC-02** | **`ProgressPill` 归属**：任务书点名要覆盖，但 P2 §1 的 27 节点清单与五路参考库**均无此节点**（已 `grep ProgressPill` 全库无命中；进度/上传在 P2 归 `attachment` 与 `chat` 的 pending 条）。 | **解 A（不建节点）**：进度表达由 `attachment` 面板的 pending 条 + `chat` 的 notice 行承担（本文件当前写法）。<br>**解 B（建原子）**：需要一个「带进度/状态的 pill」，会新增第 28 个原子，并要补它的 13 字段与命中/语义契约。 | **不阻塞**（本文件已按解 A 写；若 Lead 判 B，需另派任务补 13 字段，且要新增参考证据——目前无 E0 来源，**按 G-00 不得凭空写**）。 |
| **TBC-03** | **横屏（高<480dp）BottomSheet 的最小可用高**：412dp 高屏按 `0.8 × 高` 只剩 330dp，扣掉标题 56 + 按钮区 48 + 内边距 32 后正文约 194dp（≈3 行 44dp 选项），长选项文案时不够用。D-04 只定了断点，没定 sheet 在矮屏的降级。 | **解 A（保持 0.8，内容内滚）**：最省规则；极端情况下正文可视区 <100dp（用户要滚着选）。<br>**解 B（矮屏允许全高 sheet 到 `高−statusBar`）**：可用面积大，但与「底部升起」的距离感消失、遮罩几乎不可见。<br>**解 C（矮屏改用 Modal）**：可用性最好，但与 D-03 的形态分工冲突。 | **不阻塞 P3**（本文件按解 A 写 + BP-77 验收超屏）；若 P4 真机判定不可用，需回 D-03/D-04 复核（登记在此，不自行改）。 |

> 除上述 3 条外，本面板未发现与 D-01…D-20 冲突之处；P2 §4「明确不做」已搬入 §0 一行摘要；P2 §6 的 Q-BP-01/02/03/04/05/08/09 均已由 D-02/D-03/D-07/D-09/D-11/G-16 收口（Q-BP-06 见 TBC-01）。

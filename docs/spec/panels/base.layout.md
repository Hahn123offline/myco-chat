# 面板规格：base.layout（无限抽屉壳）

> P3 写手产物。**P4 实现的唯一依据**（实现时只看本文件，不看 `ref/`）。
> 输入：`_p3-brief.md`、`schema-node.md`、`00-global-rules.md`、`decision-pack.md` §1/§2/§6、`p2/base.layout.md`。
> 拍板前提（不重开）：**D-01=A 覆盖栈**、**D-05=A 只 0.32 遮罩**、**D-06=A 内容区拖拽 96dp + 左缘 20dp 让系统 + 深层禁开**、**D-07=A spring 0.86/420 + 22%/600dp·s⁻¹**、**D-08=B 按下+松手都在遮罩才关**、**D-04=A 600/840 + 高<480 单栏**、**D-09=A 只做触摸**、**E-01=A 层叠壳六默认值**、**E-02=A insets 归容器**、**E-23=A 不引 A2UI**。
> 本文件只写 `docs/spec/panels/base.layout.md`；未改 `p2/**`、`ref/**`、规则、决策包、其它 `panels/*.md`；未改 app；未 commit。

## 0. 一句话 + 范围

**一句话**：`base.layout` 是 myco 的**唯一导航真源与层叠容器**——把"面板"表达为 `List<PanelRoute>`（覆盖栈）+ 纯数据邻接图，负责层叠几何、开合手势、返回/预测性返回仲裁、保活、遮罩、insets、断点与 z 阶梯；它**不含任何面板内容**。

### 0.1 负责

| # | 负责 | 落点 |
|---|---|---|
| 1 | 覆盖栈：栈顶渲染、深度 0–1 保活、≥2 冻结、返回=pop | §1.8 + `base.layout.panel-host` / `base.layout.stack-lifetime` |
| 2 | 邻接图（纯数据）+ 白名单/四层校验 + 非法边拒绝 | §1.5 + `base.layout.panel-graph` |
| 3 | 面板实例数据 `PanelRoute`（字段级） | §1.4 + `base.layout.route` |
| 4 | 层叠几何、遮罩 0.32 单 token、全屏不画遮罩 | §1.7 + `base.layout.route.scrim` |
| 5 | 开合手势（96dp 消费 / 22% / 600dp·s⁻¹ / 左缘 20dp / 深层禁开） | `base.layout.route.drag-open` / `.drag-dismiss` |
| 6 | 单一返回 dispatcher（IME → 面板内浮层 → 栈顶 route → 栈底） | `base.layout.back.dispatcher` |
| 7 | 预测性返回 progress 契约（13+ 跟手、低版本瞬间 pop） | `base.layout.route.predictive-back` |
| 8 | edge-to-edge insets（`systemBars ∪ displayCutout` + IME）真源 | §1.3 + `base.layout.shell` |
| 9 | 断点与宽屏列（600/840 + 高<480） | `base.layout.window-class` / `.shell` / `.sidebar-column` |
| 10 | z 阶梯常量 + 穿透 overlay 槽 | §1.6 + `base.layout.z-ladder` / `.slots.overlay` |
| 11 | 被覆盖层隔离（吞指针 + 清语义 + dialog/焦点陷阱/回焦） | `base.layout.a11y.covered` |

### 0.2 不负责（边界，防 G-00b 双写）

| 不负责 | 归谁 |
|---|---|
| 原子内部结构/视觉/关闭动画：按钮、行、Modal、BottomSheet、Menu、Lightbox、Toast | `base.primitives`（本文件只引用其 z 家族号与"浮层不进栈"契约） |
| 颜色、字号、间距、圆角、阴影、遮罩**颜色** | `base.theme`（本面板只定 `alpha=0.32` 与几何/token 名） |
| 侧栏列内容：会话行、搜索、分组、rail 图标、汉堡/菜单入口 | `sidebar` + `base.primitives`（本面板只定列宽/槽位/是否进栈） |
| 宽屏第三列（右栏）内容 | `base.sidebar-right`（**首批不启用**，Q-06；本面板只定槽位与 z） |
| 输入区与 IME 消费 | `composer`（容器给 insets 真源，E-02=A） |
| 聊天内容/列表/滚动锚点 | `chat` |
| `params` 的字段 schema | 各面板 spec（本面板只校验"符合声明"） |

### 0.3 P4 直接照做：10 条结构契约索引

| # | 契约 | 本文件位置 | 验收 |
|---|---|---|---|
| 1 | `PanelRoute` 数据结构（id / 来源邻接 / 参数 / coverMode(full\|partial) / edge / returnMode / 保活） | **§1.4** | BL-04/BL-05 |
| 2 | 邻接图、非法边拒绝、深链落到栈 | **§1.5** | BL-01/BL-02/BL-03 |
| 3 | 栈生命周期 push/pop/replace/回根 + 保活/冻结（状态、滚动、浮层） | **§1.8** + `stack-lifetime` | BL-04…BL-09、BL-32…BL-34 |
| 4 | 单一返回 dispatcher：优先级表 + 每层如何登记 | **`base.layout.back.dispatcher` 字段 5** | BL-22…BL-27 |
| 5 | insets 契约：谁垫哪条边、顺序 | **§1.3.2** | BL-40/BL-41/BL-46 |
| 6 | z 阶梯：面板 / 遮罩 / sheet/modal/menu/toast/lightbox | **§1.6** | BL-50 |
| 7 | 遮罩 token：0.32 唯一值、何时不画、关闭判据 | **§1.7** | BL-16…BL-21 |
| 8 | 预测性返回：13+ 跟手、低版本瞬间 pop、如何接入 | **`route.predictive-back` 字段 5** | BL-28…BL-31 |
| 9 | 宽屏三列：≥840 280/56/自适应；600–839 rail+覆盖；<600 全宽 | **§1.2 + §1.3.3** | BL-35…BL-39 |
| 10 | 无障碍：吞指针 + `clearAndSetSemantics`、`role=dialog`、焦点陷阱/回焦 | **`a11y.covered` 字段 5** | BL-42…BL-45 |

### 0.4 明确不做（P2 §4 摘要，一行）

不做 dockkit 分栏树/浮窗/标签拖拽、不做列宽拖拽手柄（8px 命中不可用）、不做太墟 `reveal` 前景平移与 companion panes、不做 iOS 式边缘翻页（Android-only）、不做 DSH 1024 断点与平台折叠形态、不引 A2UI/外部图（M6 才做编辑器）、**不做静止露边/叠卡外观**、不做右栏内容（首批）。

> 依据：p2 §4 主表 13 条 + §4.1 参考缺陷 8 条；D-09（触摸-only）、E-23=A（不引协议）、Q-06（首批范围）。
> 本轮不再复述理由，P4 若想"顺手做"任一项，先回来重开对应 D/Q。

## 1. 布局与网格

> 线框按手机 **390×844dp** 画（W=屏宽）；宽屏按 EXPANDED（≥840dp）另画。所有数值单位 dp。
> 线框是结构描述，不是像素稿；颜色/圆角/阴影一律 `base.theme`。

### 1.1 线框（8 态：空 / 常态 / 抽屉 / 全屏 / 多层 / 宽屏 / 中屏 / 横屏 / 预测返回中）

**D-0 根（空/常态）**
```
┌────────────────────────────────────┐
│ [≡] 会话标题                    [⋯] │ ← 顶栏；内容根垫 insets.statusBars∪cutout(top)
├────────────────────────────────────┤
│                                    │
│   消息列表（唯一命中层，可竖滚）      │
│                                    │
├────────────────────────────────────┤
│ [输入区]                            │ ← 底部垫 union(navigationBars, IME, cutout)
└────────────────────────────────────┘
栈：[chat] depth0 ACTIVE。返回键=栈底 → 交系统（§5 P3-BL-01 待确认是否二次确认）
```

**D-1/D-2 跟手拖拽中（p=progress∈(0,1)，禁止停滞在此）**
```
┌──────────┬─────────────────────────┐
│ 抽屉      │ 聊天（遮罩 0.32×p）      │
│ 宽 = p×W  │ 不可点；不缩放、不模糊    │
└──────────┴─────────────────────────┘
抽屉左缘 x = −Wdrawer×(1−p)，跟手 1:1
松手必须收敛到 p=0（回弹）或 p=1（pop/push），不存在"静止半开"
```

**D-3a 抽屉全开（sessions，PARTIAL + OVER_DIM）**
```
┌──────────────────────┬─────────────┐
│ 会话列表（sidebar）   │ 遮罩 alpha=0.32 │
│ 宽=min(320+startInset,│ 可点=关闭     │
│     0.86W, W−56)     │ G-18 严判据   │
│ 设置入口（footer）    │              │
└──────────────────────┴─────────────┘
栈：[chat, sessions] ；返回=pop 回 chat
```

**D-3b 全屏面板全开（settings / bin，FULL + OVER_OPAQUE）**
```
┌────────────────────────────────────┐
│ [←] 设置                            │ ← 返回入口 48×48（G-11/G-16）
│  分组 / 子页                        │
│  ...                               │
└────────────────────────────────────┘
无遮罩、下层不缩放不模糊（D-05）；返回=pop
```

**D-4 多层叠加（chat → settings → bin）**
```
chat      depth0  KEEP      （被全宽覆盖 → 吞指针 + 清语义；不重布局）
  └ settings depth1  KEEP    （仍在组合树；被覆盖 → 吞指针 + 清语义）
       └ bin   depth2  ACTIVE（栈顶；屏幕只画它，不透明全屏）
返回：bin → settings（组合在，直接可见）→ chat
```

**D-5 宽屏 EXPANDED（w≥840 且 h≥480）**
```
┌──────────┬────────────────────────────┬────────┐
│ 侧栏列    │ 主列 minmax(400, 1fr)       │ 右栏槽  │
│ 280/56/0 │ panel-host（唯一栈内容）     │ 0（预留）│
└──────────┴────────────────────────────┴────────┘
列参与布局流、不进栈、无遮罩、无开合手势；返回键不关列
```

**D-6 中屏 MEDIUM（600≤w<840 且 h≥480）**
```
┌────┬───────────────────────────────┐
│rail│ 主列 1fr（min 400）             │
│ 56 │ 侧栏展开 = 覆盖抽屉（320/0.86W/W−56）│
└────┴───────────────────────────────┘
```

**D-7 横屏手机（h<480，不论宽度）**
```
┌────────────────────────────────────┐
│ 强制 COMPACT 单栏（915×412 也绝不分栏）│
└────────────────────────────────────┘
```

**D-8 预测性返回中（Android 13+）**
```
┌──────────────┬─────────────────────┐
│ 下层露出      │ 栈顶面板跟系统手势位移 │
└──────────────┴─────────────────────┘
progress 由系统写（只驱动栈顶位移）；commit=pop 不重播动画；cancel=回弹
```

### 1.2 网格表（dp）

| 量 | COMPACT（`w<600` 或 `h<480`） | MEDIUM（`600≤w<840` 且 `h≥480`） | EXPANDED（`w≥840` 且 `h≥480`） | 依据 |
|---|---|---|---|---|
| 侧栏列（会话导航） | **不存在**（以覆盖 route 出现） | rail **56** 常驻 + 内容 | **280** / rail **56** / 隐藏 0 | `columns.ts:13-23,52-58`（E0）；D-04 |
| 主列（panel-host） | 屏宽（1fr） | 1fr，**min 400** | 1fr，**min 400** | `GO §2.15 字段2`；`columns.ts:11`（`CENTER_MIN=400`） |
| 右栏列（第三列） | 全屏覆盖 route | 全屏覆盖 route | **0（预留槽）**；启用后 `minmax(0, 70%)`、首开 45%、min 300 | `columns.ts:11-29`；Q-06（首批不启用，见 `slots.right-rail`） |
| 抽屉型面板宽 | `min(320 + startInset, 0.86×W, W−56)` | 同左 | —（列形态） | `GO §2.7 sidebar.drawer 字段2`；`M/:268-292`（`min(320px,86vw)` + `max-width:calc(100vw−56px)`） |
| 侧栏列宽 clamp | 264–420（首版不拖拽） | 同左 | 同左 | `columns.ts:13-23`；p2 §4「列宽拖拽不做」 |
| 全屏面板 | 宽=W，高=屏高（含系统栏，内容根垫） | 同左 | 同左 | D-01 / 旧 myco 全页覆盖（`LEG §0`） |
| 壳行模板 | 单行 `1fr`（只承载主列）；顶栏/内容/输入区**行**由各面板内部布局，壳不设 `shell.bottom` 行槽 | 同左 | 同左 | **我方取值（P4 校准）**：DSH 有 `shell.bottom` 槽（`L/AppFrame.module.css:1-8`），但 myco 输入区属 chat/composer（G-00b 单一真源），壳不重复设槽 |
| 壳内边距（容器级） | **0**；insets 由 route 内容根按 §1.3.2 垫；面板内容 padding 归各面板 | 同左 | 同左 | §1.3.2；G-00b |
| 最小宽度 | 抽屉 320 / 主列 400 / rail 56 | 同左 | 同左 | `GO §2.15 字段2`；`columns.ts:11` |
| 最大宽度 | 抽屉 `W−56`；侧栏 420（clamp 上限）；右栏 70% | 同左 | 同左 | `M/:292`；`columns.ts:11-29` |
| 列间距 | 0（分隔用 **0.5dp hairline**，颜色归 theme） | 0 | 0 | **我方取值（P4 校准）**：无参考给出列 gap；DSH 用 border 分隔（`DSS §0.2`），我方取 0 gap + hairline |
| 抽屉/面板圆角、阴影 | 归 `base.theme`（本文件不定义） | | | G-00：旧 myco 未取证（p2 §0.2-3） |
| 返回入口命中盒 | **48×48** | 48×48 | 48×48 | G-11 判定式 + G-16「主操作 48」；p2 §2 `route.back` |
| 遮罩 alpha | **0.32**（全屏不透明面板不画） | | | `RuntimeAdaptiveNavigationScaffold.kt:169-172`（E0）；D-05 |
| 最小命中盒间距 | 命中盒**不得相交**（G-16 唯一硬约束） | | | G-16 |
| 面板内容内边距 | 归各面板（本文件只交 insets 真源） | | | G-00b 单一事实源 |

> 行高/字体/间距 token 一律归 `base.theme` 与各面板；本表只列**壳拥有的几何量**。

### 1.3 与容器的关系

#### 1.3.1 在 M2 覆盖栈里的位置与返回语义

| presentation | 宽/形态 | 是否进栈 | 遮罩 | 开合手势 | 返回语义 | 依据 |
|---|---|---|---|---|---|---|
| `ROUTE_FULL` | 全屏 | ✅ 进栈（每实例一层） | **无**（OVER_OPAQUE） | 只允许"拖出关闭"（OUT）；禁开新面板 | `POP`（退一层）/ `TO_ROOT` | D-05/D-06；p2 §C.2/§C.5 |
| `ROUTE_DRAWER` | `min(320+inset,0.86W,W−56)` | ✅ 进栈 | **0.32**（OVER_DIM） | 根层可拖开（CONTENT_DRAG）；已开可拖出关 | `POP` | D-05/D-06；p2 §C.2/§C.5 |
| `DOCKED_COLUMN` | 280 / 56 | ❌ **不进栈**（占列槽） | 无 | 无（列无开合手势） | 返回键**不关列** | D-04；p2 §C.4；`DSS §0.2` |

#### 1.3.2 insets 契约（edge-to-edge，E-02=A）

**总原则**：壳根在 edge-to-edge 下（系统栏透明、内容画到边），**滑动面/遮罩/背景吃满全屏不垫**，只有"内容根"按边垫；底部**只垫一次 union**，禁止双重底垫。

| 区域 | 垫什么 | 谁垫 | 顺序 / 规则 |
|---|---|---|---|
| shell 根 | 不垫（只负责 edge-to-edge + 透明系统栏） | shell | ① 系统栏透明与图标明暗归 `base.theme` |
| 每层 route 外框（背景/遮罩/滑动面） | **不垫**（必须贴到屏幕边） | route | ② 否则滑动面/遮罩会被 inset 缩进，出现"卡边 + 遮罩盖不住系统栏区" |
| route 内容根 · top | `statusBars.top ∪ displayCutout.top` | 该层内容根 | ③ 顶栏在其内部，不再二次垫 |
| route 内容根 · bottom | `union(navigationBars ∪ IME ∪ displayCutout.bottom)` **一次** | 该层内容根 | ④ 禁止 `imePadding()` 与 `navigationBarsPadding()` 叠加（双垫 = 输入区浮空）；IME 出现时底部只按 union 高度变 |
| route 内容根 · start/end | `displayCutout` 对应侧 | 该层内容根 | ⑤ 横屏挖孔机必查（内容不得进挖孔区） |
| 抽屉内容 | start（cutout）+ top（statusBars）+ bottom（navBars） | 抽屉内容根 | ⑥ 抽屉背景仍吃到屏幕边 |
| 底部贴底槽（E-02=A） | 容器把 `union(navBars, IME)` 的真源经 CompositionLocal 交给输入区 | **容器提供，composer 只消费** | ⑦ composer 自己不读 `WindowInsets.ime`；聊天列表底部留白 = 输入区实测高 |
| overlay 槽（`slots.overlay`） | 不垫 | 槽 | ⑧ 子浮层按自己锚点垫（如 Toast 底部 = union(navBars, IME)，归 primitives） |
| 旋转 / 分屏 / 折叠 | insets 重算，**不重建** | shell | ⑨ 断点跨档用 `movableContentOf` 迁移内容（太墟 E0，`TX §0-5`） |

**依据**：Q-BL-11 / E-01=A（edge-to-edge + 全量 insets）；`M/:277-278`（抽屉 top/bottom safe-area，E0 意图）；`MOB §0-6`（DSH Web 的 `env()` 空转是缺陷，myco 原生不存在）；`RuntimeRevealNavigationDrawer.kt:106-109`（开面板前收键盘）。**"单次 union 底垫"为【我方设计】**：反证 = 两套 inset 各自补偿时键盘一动两处同时改高度，输入区会浮空再落下（p2 §C.7 同款反证），代价 = 需一个 CompositionLocal 真源。

#### 1.3.3 宽屏三列规则（摘要，细节在 `window-class`/`shell`/`sidebar-column` 节点）

- `w≥840 && h≥480` → **EXPANDED**：侧栏**常驻列 280**（可收 **rail 56**），主列 **自适应且 min 400**，右栏槽位 0（预留）。
- `600≤w<840 && h≥480` → **MEDIUM**：rail **56** 常驻 + 覆盖抽屉（展开时）。
- `w<600` **或** `h<480` → **COMPACT**：全宽单栏，侧栏/右栏都走覆盖 route，无 rail。
- **列不进返回栈**；断点跨档时同一内容用 `movableContentOf` 迁移、栈与 `dockedRoutes` 之间互转，不重建。

### 1.4 数据结构契约（P4 直接照做，字段级）

> 三个类型 + 一张常量表。**栈内不写 z 数字**；`depth`/`transitionProgress` 由 host 计算或手势写，外部只读。

#### 1.4.1 `PanelSpec`（每张"卡"一次定义，是图的一部分）

| 字段 | 类型 | 必填 | 含义 | 约束 / 取值 |
|---|---|---|---|---|
| `specId` | String | ✅ | 面板类型 id | 必须在注册表白名单内（`10-panels.md` + 首批 Q-06） |
| `kind` | enum | ✅ | `chat / sessions / settings / bin / ...` | 首版 = 已有面板 |
| `presentation` | enum | ✅ | `ROUTE_FULL / ROUTE_DRAWER / DOCKED_COLUMN` | `DOCKED_COLUMN` 只在 EXPANDED 生效 |
| `widthSpec` | WidthSpec | 抽屉/列必填 | 宽度规则 | 见 §1.4.3；全屏缺省 `FULL` |
| `coverMode` | enum | ✅ | `OVER_OPAQUE / OVER_DIM / PUSH_COLUMN` | `PUSH_COLUMN` 只允许宽屏列；**不是**手写宽度 |
| `scrim` | enum | ⬜ | `NONE / DIM(0.32)` | 缺省由 coverMode 推导：OPAQUE→NONE、DIM→DIM、PUSH_COLUMN→NONE |
| `returnMode` | enum | ✅ | `POP / TO_ROOT / HAND_TO_GESTURE` | 见 §1.8.1 |
| `edges` | `Map<Edge, specId>` | ⬜ | 可打开的邻接边（不含"返回"） | 只允许白名单 id；`(from,edge)` 唯一；不含自环 |
| `edgesSwipe` | `Map<Edge, SwipeSpec>` | ⬜ | 每条边的滑动接口 | 缺省 = 只能按钮/命令开 |
| `statePolicy` | enum | ⬜ | `KEEP / FREEZE` 的显式覆盖值 | **不得覆盖** §1.8.2 的全局深度规则（首版无人使用） |
| `backDepthPolicy` | enum | ⬜ | `CONSUME_WHEN_VISIBLE / PASS_THROUGH` | 缺省前者；首版全部前者（PASS_THROUGH 预留） |
| `a11y` | object | ⬜ | `{role, label, focusTrap}` | 抽屉缺省 `role=dialog, focusTrap=true` |
| `schemaVersion` | String | ✅ | 图/内容 schema 版本 | 支持范围白名单，未知字段拒绝 |

#### 1.4.2 `PanelRoute`（一次打开产生的实例；**P4 照此建类**）

| 字段 | 类型 | 必填 | 含义 | 备注（brief 点名的项） |
|---|---|---|---|---|
| `routeId` | String | ✅ | 实例唯一 id（ULID 或自增） | 用于预测返回 / 状态恢复 / 事件回注 |
| `panelId` | String | ✅ | = `PanelSpec.specId` | **id** |
| `params` | `Map<String, Any>` | ⬜ | 携带参数（`sessionId`/`tab`/`scrollToSeq`/`anchorId`） | **携带参数**；按面板声明 schema 校验，未知字段拒绝 |
| `from` | String? | ⬜ | 来源 `panelId`；根为 null | **来源/邻接**（与 `edge` 一起构成来源） |
| `edge` | `Edge`(`LEFT/RIGHT/TOP/BOTTOM`) | ⬜ | 本次进入的边（决定转场方向与拖出边） | **edge** |
| `widthSpec` | WidthSpec | ✅ | 运行时宽度（可被窗口类改写） | 见 §1.4.3 |
| `cover` | `FULL / PARTIAL` | ✅ **派生** | 覆盖区是否=屏宽 | **coverMode(full/partial)** 的口径：`widthSpec=FULL→FULL`，`MIN_MAX/FIXED→PARTIAL`；**不可手写**，由 host 计算 |
| `coverMode` | enum | ✅ | `OVER_OPAQUE / OVER_DIM / PUSH_COLUMN` | 视觉/遮罩/是否挤列 |
| `scrim` | enum | ⬜ | `NONE / DIM` | 由 coverMode 推导 |
| `returnMode` | enum | ✅ | `POP / TO_ROOT / HAND_TO_GESTURE` | **returnMode** |
| `keepAlive` | `KEEP / FREEZE / ACTIVE` | ✅ **派生** | 实际生效的保活态 | **是否保活**：由 §1.8.2 规则算，不外部写 |
| `swipe` | SwipeSpec? | ⬜ | 本次有效滑动接口（宽屏列/深层为 null 或部分禁用） | 见 §1.4.4 |
| `depth` | Int | ✅ **派生** | 栈内深度（0=根） | host 计算，不可外部写 |
| `transitionProgress` | Float | ✅ | 0..1，拖拽/预测返回期间写，壳只读 | 三通道共用同一 progress（旧 myco E0） |
| `a11y` | object | ⬜ | 实例级语义（合并 spec 默认） | 见 `a11y.covered` |

> 生命周期：`CREATED → ENTERING → ACTIVE(top) → COVERED(KEEP) → EXITING → DESTROYED`；`COVERED → FROZEN` 只在 §1.8.2 条件成立时发生。
> 首版固定字段顺序与默认值，**新增字段必须升 `schemaVersion`**。

#### 1.4.3 `WidthSpec`

| 取值 | 语义 | 首版使用 |
|---|---|---|
| `FULL` | 屏宽（手机栈内默认） | chat / settings / bin |
| `MIN_MAX(minDp, frac, maxInsetDp)` → `min(minDp + startInset, W×frac, W−maxInsetDp)` | 抽屉宽 | sessions：`MIN_MAX(320, 0.86, 56)`（`GO §2.7` + `M/:268-292`，E0） |
| `FIXED(dp)` | 固定宽（列） | 侧栏 280 / 56 |

#### 1.4.4 `SwipeSpec`

| 字段 | 类型 | 取值 | 依据 |
|---|---|---|---|
| `edge` | Edge | `LEFT/RIGHT/TOP/BOTTOM` | 旧 myco 左抽屉/右设置（`LEG §0`） |
| `direction` | `IN / OUT / BOTH` | 抽屉 `BOTH`；全屏 `OUT` | `GO §0-8`（开/关都跟手，E0） |
| `openSource` | `CONTENT_DRAG / SCREEN_EDGE / NONE` | 首版只有 `CONTENT_DRAG` 与 `NONE`；**不做 SCREEN_EDGE**（D-06） | 旧 96dp 内容区（`LEG §2.8`）；反证见 `drag-open` |
| `enabledWhen` | `ROOT_ONLY / ANY_DEPTH` | 开手势 `ROOT_ONLY`；关手势 `TOP_LAYER_ONLY`（栈顶才生效） | D-06/G-15；`MainPageNavigation.kt:7`（E0） |
| `revealDp` | Dp | **0**（静止露边） | Q-BL-02=A / E-01；`M/:268-292` 关闭时完全移出 |
| `follow` | Float | **1.0** | M5 §2.7（E1）；dsh-Go/太墟均 1:1（E0） |
| `commitRatio` | Float | **0.22**（屏宽比例） | D-07/G-14；`LEG §2.8`（E0） |
| `commitVelocityDpS` | Int | **600** | D-07/G-14；`LEG §2.8`（600px/s→按 dp/s 记） |
| `consume` | Dp | **96**（开手势"只观察"阶段） | D-06/G-15；`LEG §2.8`（E0） |
| `systemEdgeReserveDp` | Dp | **20**（左缘让系统返回） | D-06/G-15；`LEG §2.8`（E0） |

### 1.5 邻接图（纯数据 + 四层校验 + 非法边拒绝 + 深链落地）

#### 1.5.1 定义

- 节点 = `PanelSpec.specId`；边 = `(fromPanelId, edge) → toPanelId`，存在 `PanelSpec.edges` 里。
- 边只描述"**可打开**"（滑动或按钮），**返回不是边**（返回 = `pop`，由 `from` 决定）——所以 P2 §B.3 示例里 `settings.edges.LEFT→chat` 这类"反向边"**不写**；返回语义只看 `from`/`returnMode`。
- 边可带 `openSource=NONE`：表示只能按钮/命令打开（不能滑动）；图仍是白名单。

#### 1.5.2 内置图 `chat-profile` v1（首版唯一启用图，内置在代码里）

| from | edge | to | openSource | 手势允许层 | 说明 |
|---|---|---|---|---|---|
| `chat` | LEFT | `sessions` | `CONTENT_DRAG` | `ROOT_ONLY`（depth0） | 旧版右滑 ≥96dp 开抽屉（D-06） |
| `chat` | RIGHT | `settings` | `NONE` | — | 旧版由顶栏菜单打开（`LEG §2.7`），非右滑 |
| `settings` | RIGHT | `bin` | `NONE` | — | 旧版 settings 上叠 bin（`LEG §0`） |
| `sessions` | — | — | — | — | 抽屉无出边（footer 设置走命令，见 §1.5.4） |
| `bin` | — | — | — | — | 叶子层 |

- `root = chat`；所有节点必须由 root 沿边（含 `NONE` 边）可达；`(from,edge)` 不允许重复。
- `openSource=NONE` 的边**只用于两件事**：① 作为白名单参与 L1（按钮入口的 `target` 必须能在图里找到路径）；② 深链的反向祖先链合成（§1.5.5）。它**不表示可滑动**，也不表示"从该边进入"。
- 打开请求统一为：

```
OpenRequest { from: panelId?, edge: Edge?, target: panelId, params: Map }
```

#### 1.5.3 校验与失败（L1–L4，借太墟四层防线 `TX §5.6`，E1）

| 层 | 时机 | 检查 | 失败表现 |
|---|---|---|---|
| L1 参数 | 每次 `OpenRequest` | ① `target` ∈ 白名单 ② `params` 符合该面板 schema ③ `edge` 非 null 时必须匹配 `edgesOf(from)[edge]` ④ 不得自环 | **拒绝**：不 push、零转场、`depth` 不变；dev 断言；release 记录 + 经 `slots.overlay` 的 Toast 提示"该入口不可用"（Toast 归 primitives） |
| L2 图版本 | 加载图 | `schemaVersion` ∈ `SUPPORTED_VERSIONS`；未知字段拒绝 | 图加载失败 → **回退内置默认图**（不白屏） |
| L3 结构 | 加载图（启动期） | 根存在、边界可达、无自环、无重复 `(from,edge)`、所有 `to` 在白名单 | **fail-fast 崩溃**（配置错误不许静默；`p2 §B.4`） |
| L4 渲染 | 运行中 | 面板内容渲染异常 | 原始数据卡兜底（不白屏、不转圈到死），壳与返回仍可用 |

#### 1.5.4 按钮/命令入口 vs 图边

- **命令入口**（面板内容发起的导航）不要求图边，只过 L1-①②；首版两条，必须在实现里显式写死，不许面板自己改栈：
  - `closeThenPush(target, edge, params)`：用于**抽屉 footer 设置入口** —— 同帧 `pop(sessions)` + `push(settings)`（`LEG §2.6 sidebar.footer.settings` → p2 §E.1 #5）。
  - `closeThenSelect(sessionId)`：用于**会话行点击** —— 同帧 `pop(sessions)` + 业务层切会话（`LEG §2.6 sidebar.row.select` → p2 §E.1 #4）。
- 反证：若把这两条也塞进图，`sessions` 需要一条"RIGHT 出边"，但它是左抽屉、右键语义冲突；若允许面板直接改栈，则 G-00b 单一真源被破坏（每个面板都直连 `screen/drawerState` 就是旧 myco 的问题，p2 §E.2）。

#### 1.5.5 深链落到栈（契约；首版是否接外部入口见 §5 P3-BL-02）

1. 深链目标 `target` + `params` → 沿图**反向** BFS 求一条 `root → ... → target` 的祖先链（`NONE` 边也参与，因为按钮入口同样可达）。
2. **一次 commit** 把整条链压栈：`[root, …, target]`；只对 `target` 播进入转场，中间层瞬时构建（无动画、无闪烁）。
3. 每条中间 route 的 `from`/`edge` 按链填（`edge` 取该边方向；`NONE` 边取 `null` → 转场用淡入 ≤120ms）。
4. 不可达 / 不合法 → **拒绝整条深链**，栈保持原样（不半压）。
5. 若 `target` 已在栈中：`popTo(target)`；若 `params` 不同 → `replace` 该层（见 §1.8.1）。

> 依据：太墟 E0 `openSettingsPath` 保证"设置页→子页"两层一定同时存在（`MainPageNavigation.kt:34-38`，`TX §2.1` 字段 10）；反证 = 直接压目标单层会让返回一次就回根，用户丢失"我是从哪进来的"上下文（旧 myco `settingsNav` 是两级，`LEG §2.7`）。

### 1.6 z 阶梯（跨家族常量；栈内零数字）

| z | 家族 | 内容 | 依据 |
|---|---|---|---|
| — | 栈内 route | 按**列表顺序**渲染（后入者在上），**不写 z 数字** | p2 §A.3 I2；`D/FloatLayer.tsx:177`（数组序即 z，E0 反证） |
| **0** | 根 route（chat） | 栈底 | 栈序 |
| **10** | 宽屏常驻列（侧栏 / 右栏） | 参与布局流的列 | `DSH §0-7`；`L/AppFrame.module.css:211`（E0） |
| **20** | 框架浮层槽 `shell.overlay`（穿透） | Toast / FAB / 悬浮提示 / 菜单锚点 | `L/AppFrame.module.css:281-290`（`pointer-events:none`，E0） |
| **40** | **导航栈 route（本次新增带）** | 所有 `PanelRoute`；栈内仍按顺序，不递增 | 参考 DSH 全屏右栏 40（`AppFrame.module.css:231`）；**我方新增带**，见 p2 §C.6 |
| （无） | **遮罩** | **不占独立 z 家族**：属于栈顶 route 内部，按渲染顺序画在"下层 → 遮罩 → 该层内容"之间；全 app 同时最多 1 个（§1.7） | p2 §C.2；§1.7「多层抑制」 |
| **1000** | modal / lightbox / **bottom sheet** | 三者互斥，同家族内按打开序 | `P/Modal.module.css:6-15`、`P/ImageLightbox.module.css:2-6`（E0）；D-03=B（sheet 与 modal 分工） |
| **1100** | portal menu | 菜单高于 modal | `P/Menu.module.css:42-45`（E0） |
| （20） | **Toast** | 在框架浮层槽（20），**低于面板栈（40）** → 面板打开时 Toast 被面板盖住 | `L/AppFrame.module.css:281-290`（E0）；DSH 行为如此，不自创"Toast 永远最上" |

**规则**：
- 常量只读；新增家族必须用保留槽 **11 / 15 / 60 / 70**（无列宽手柄/无窗口 chrome/无浮窗/无 dockkit 菜单，编号留空隙避免以后全局改号）。
- **抬升只允许发生在浮层家族内**；导航栈内禁止"点某层把它抬到顶"（= 改变历史，返回语义会乱，I2）。
- 栈深增长**不新增 z 常量**（否则层数一多与 1000/1100 撞号；Compose 没有可用的全局 z 排序）。

### 1.7 遮罩 token 与关闭判据

| 项 | 唯一取值 / 规则 | 依据 |
|---|---|---|
| token | **`DIM = 0.32`**，全 app 唯一值；颜色由 `base.theme` 的 `scrim` token 提供（本面板只定 alpha 与几何） | D-05；`RuntimeAdaptiveNavigationScaffold.kt:169-172`（E0）；0.12 是太墟 reveal 的另一套值（内部不一致→缺陷，不作解，`Q-TX-12`） |
| 何时画 | `route.cover == PARTIAL && route.coverMode == OVER_DIM` 才画；**全屏不透明（OVER_OPAQUE）不画**；`PUSH_COLUMN` 不画 | D-05；`M/:722-725`（全屏面板 `display:none` 遮罩并给理由，E0） |
| 几何 | 全屏 `inset:0`（含系统栏区），不随抽屉宽度缩；绘制在**面板内容之下、下层之上** | 抽屉被内容盖住的那部分也属于遮罩 |
| alpha 与 progress | `alpha = 0.32 × progress`，线性，无独立 tween；progress 由手势/系统预测返回写 | `GO §2.15 字段6`（scrim 随进度淡化，E0） |
| 多层抑制 | **只画最顶层的遮罩**；下方 route 即使 KEEP 也不渲染其遮罩 | 反证：三层各 0.32 → 0.54 叠加接近全黑、层数线性增（无参考这样做） |
| 下层视觉 | 不缩放、不模糊、不改变下层任何属性，只压暗 | **D-05=A**；M5 §3.3 的 0.96/0.5/4dp 作废 |
| 关闭判据 | **按下与松手都必须在遮罩上**（G-18）；拖动超过 touch slop 即取消点关 | **D-08=B**；`_conflicts.md` C-BP-02（chat-demo 严判据） |
| 可点时机 | 只在 `progress == 1`（停稳）时接收点关；拖拽中（0<progress<1）不接点关 | **我方取值（P4 校准）**：反证 = 拖到一半点遮罩 = "拖拽"与"关闭"两个意图同帧，容易误关；代价 = 拖拽中想放弃须松手回弹 |
| 不透传 | 遮罩吞掉所有下层点击；遮罩之下不可点 | `TX §2.1 nav-stack` 字段 3（吞指针 E0） |

### 1.8 栈生命周期与保活（速查；细则在 `panel-host` / `stack-lifetime`）

#### 1.8.1 四个操作（**唯一改栈入口**，面板内容不得直接改栈）

| 操作 | 语义 | 转场 | 幂等 / 前置 |
|---|---|---|---|
| `push(OpenRequest)` | 压入新 route，`depth = stack.size`，`from/edge` 来自请求 | 从 `edge` 滑入；弹簧 0.86/420；跟手 progress 可续 | 前置：**同帧无动画 dismiss 当前栈顶层的所有面板内浮层**并从 dispatcher 注销；`ENTERING` 中重复同请求 → 忽略 |
| `pop()` | 移除栈顶；新栈顶 `ACTIVE`（FREEZE 层先恢复） | 反向 spring；若由预测返回 commit → **不再播** | `EXITING` 中忽略；栈底 pop 不动作（交 dispatcher 下一级） |
| `popToRoot()` | `returnMode=TO_ROOT`：栈只留 `depth0` | **只对原栈顶播一次反向转场**，中间层直接销毁（不逐层连播） | **我方取值（P4 校准）**：反证 = 逐层连播会把"回根"变成 3 秒动画；代价 = 中间层无各自退出观感 |
| `replace(route)` | 同 `depth` 换 `target/params`；栈长与历史位置不变；`from/edge` 继承 | 旧层淡出 ≤120ms + 新层直接渲染（不位移） | 首版唯一调用点 = 深链目标已在栈中且 `params` 不同（§1.5.5-5）；不做"先 pop 再 push"（两帧闪 + 状态丢） |

#### 1.8.2 保活规则（E-01=A：深度 0–1 保活、≥2 冻结）

| route 位置 | keepAlive | 含义（**状态 / 滚动 / 已注册浮层**） |
|---|---|---|
| 栈顶（任何 depth） | `ACTIVE` | 正常渲染与交互；持有 1 个 route 级返回登记 |
| 非栈顶且 `depth ≤ 1` | `KEEP` | **组合树保留**（不重挂载）＋全部本地状态保留：滚动位置、展开态、输入焦点；浮层**不允许存在**（push 时已同帧关闭并注销）；吞指针 + 清语义 |
| 非栈顶且 `depth ≥ 2` | `FROZEN` | **释放 UI 组合**，用 `rememberSaveable`（含 `LazyListState`、tab、`scrollToSeq` 等）存快照；恢复时同 key 重建。**业务状态（流式/网络/草稿/store）永不冻结**。浮层在冻结前必须已注销 |
| 栈顶为 `PARTIAL`（抽屉）时的下一层 | 强制 `KEEP` | 抽屉打开时露出的条带必须真实渲染；否则首帧构建会掉帧（`RuntimeRevealNavigationDrawer.kt:145`，E0） |

- 恢复时机：`pop`/`popTo` 的目标层在转场**开始前**构建（预组合），避免"先白一帧再出现"。
- 进程重建：从 saveable 恢复 `[routeId, panelId, params, from, edge]`；`transitionProgress` 归零；不恢复进行中的转场。
- `statePolicy=KEEP/FREEZE` 是**覆盖值**，首版无人使用；全局规则不可被面板静默绕过。

> 依据：E-01=A；p2 §C.6/§3-1；`DSW §0-5`（右栏内容保持挂载，E0）；`TX §0-5`（隐藏保持组合 + saveable，E0）；`RuntimeRevealNavigationDrawer.kt:145`（首拖不构建列表，E0）。

## 2. 状态矩阵

> 覆盖 brief 要求的 9 类：空 / 加载 / 正常 / 长内容 / 失败 / 禁用 / 深色 / 大字号 / 横屏·宽屏；另加 4 个本面板特有态（拖拽中 / 预测返回中 / 冻结恢复 / 空栈异常）。

| # | 状态 | 进入条件 | 布局变化 | 视觉 | 可交互项 | 退出 |
|---|---|---|---|---|---|---|
| S0 | **空栈**（异常/恢复中） | 进程恢复失败或首帧栈为空 | shell 只有底色，无 route | 纯底色（theme），无骨架 | **无**（全屏吞指针，防指针穿透到不存在的层） | ≤1 帧内 push 根；失败 → S10 |
| S1 | **加载** | 图加载 / 根面板内容首帧 | 根 route 已占满；内容区骨架 | 骨架屏（各面板定义）；顶栏可见 | 顶栏入口可点（但 push 会与前一次去重） | 内容到达 → S2；加载失败 → S10 |
| S2 | **正常·根** | 栈深 1，无浮层 | D-0 | 无遮罩 | 聊天区可滚/可点；顶栏入口可点；内容区右滑可开抽屉 | push → S4/S3；退出 → S0 |
| S3 | **正常·抽屉全开** | 栈顶 `cover=PARTIAL` 且 progress=1 | D-3a：抽屉 + 遮罩 | 遮罩 alpha=0.32；下层不缩放不模糊 | 抽屉内容可点可滚；遮罩可点关闭（G-18）；返回 pop | pop → S2；push 全屏 → S4 |
| S4 | **正常·全屏面板** | 栈顶 `cover=FULL && OVER_OPAQUE` | D-3b | **无遮罩**；下层完全被盖 | 面板内容 + 返回入口；拖出可关 | pop → 下层（S2/S4） |
| S5 | **拖拽中** | 开/关手势已接管（progress∈(0,1)） | 面板按 progress 位移（paint-only） | 遮罩 0.32×p；无露条（静止 0） | 面板可见区可点；下层被遮罩接住；**遮罩不接点关** | 松手 → progress 0 或 1 |
| S6 | **多层叠加**（≥2） | 栈深 ≥2 | 只画栈顶 + 遮罩（若有）；KEEP 层仍在树内但不可见 | 同 S3/S4 | **只有栈顶可点**；下层吞指针+清语义；返回只 pop 栈顶 | 连续返回逐层退 |
| S7 | **冻结恢复中** | pop 到 `FROZEN` 层 | 目标层预组合，无占位闪 | 恢复在同一帧完成，无淡入 | 恢复完成前短暂禁用（≤1 帧） | 恢复完成 → S4/S3 |
| S8 | **预测返回中** | 系统返回手势进行中（13+） | 栈顶跟手位移，下层露出 | 遮罩 0.32×系统 progress；面板不缩放 | 系统手势区；面板其它点击暂停；**其它返回入口忽略** | commit → pop；cancel → 回弹 |
| S9 | **长内容** | 栈深 >3 / 侧栏长列表 / 面板内长列表 | 栈深无上限；**只渲染保活窗内 + 栈顶**（其余 FROZEN） | 同 S6 | 滚动归各面板；壳不滚动 | 同 S6 |
| S10 | **失败** | ① 图 L2 失败 ② L3 结构错 ③ 面板渲染失败 ④ 深链不可达 | ① 回退内置图 ② 启动崩溃（不进 UI）③ 该面板显示原始数据卡 ④ 栈不变 | 各面板定义兜底视觉；壳保持底色 | ③ 兜底卡不可交互但返回可用；④ 无变化 | 修复/返回 |
| S11 | **禁用** | 深层禁开手势 / 宽屏列形态 / `EXITING` 中 / 动画进行中 / 预测手势进行中 | 无布局变化 | 无视觉变化（**不给禁用态外观**，因为手势不可发现） | 按钮/返回仍可用；手势不响应 | 条件消失 |
| S12 | **深色** | 系统深色 | 布局不变 | 全部 token 切换；**遮罩 alpha 仍 0.32**、hairline 可见；切换瞬间无白闪 | 同正常态 | 浅色 |
| S13 | **大字号** | `fontScale` 1.3–2.0 | **壳几何不变**：列宽/抽屉宽/命中盒不随字号变；文字重排、顶栏可换行/撑高 | 无 | 返回入口命中盒仍 48；抽屉宽仍按屏宽 | 字号复原 |
| S14 | **横屏 / 宽屏** | `h<480` 或 `w≥600`/`≥840` | `h<480`→强制 COMPACT 单栏；`600–839`→rail+覆盖；`≥840`→常驻列 | 同对应形态 | 列/rail/抽屉按形态；**列不进栈、返回键不关列** | 尺寸变化（不重建） |

## 3. 节点规格（13 字段，照 `schema-node.md` 格式）

> 本面板 = **容器**：17 个节点里 7 个是交互原子（scrim/back/drag-dismiss/drag-open/predictive-back/sidebar-column/overlay），
> 10 个是结构契约原子（无独立命中区，但字段 3 必须写"无命中区 + 原因"，13 字段一个不少）。
> 数值依据见 §1.2/§1.4–§1.8；每个节点至少 1 条验收 ID（§4）。

### `base.layout.panel-graph` — 邻接图（纯数据）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.panel-graph`。所属：`base.layout` → `panel-host` 的输入。出现条件：**壳启动时加载 1 份**（首版内置 `chat-profile` v1），此后只读；每次 `OpenRequest` 前被查询。每进程 1 份，**无 UI**。 |
| 2 | 网格与定位 | 不参与布局（0×0，不产生任何尺寸）。提供两个查询：`edgesOf(panelId) → Map<Edge, specId>`、`resolve(target) → 祖先链`。 |
| 3 | 命中区 | **无命中区**（纯数据）。它派生出的可点入口（顶栏菜单、抽屉 footer、会话行）归 `base.primitives` / `sidebar` / `chat`，本节点不定义命中盒。 |
| 4 | 手势 | 无。只被手势层查询：`openSource` 决定"是否可滑动"、`enabledWhen` 决定"当前深度是否允许"、`consume` 决定消费阈值。 |
| 5 | 行为 | 加载 → L2（schema 版本）→ L3（结构：根存在/边界可达/无自环/无重复 `(from,edge)`/to 在白名单）→ 只读。打开前 L1 校验（白名单/params schema/edge 匹配）。L2 失败 → 回退内置默认图；**L3 失败 → 启动期 fail-fast 崩溃**（配置错误不许静默）。图不可变；换图 = 换 `schemaVersion`。 |
| 6 | 视觉状态 | 无（0 绘制）。 |
| 7 | 布局占位与展开形态 | 不参与布局流，不占位（0dp）。不属于 G-03 三形态（无展开）。 |
| 8 | 滚动语义 | 无。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 空图/缺根/成环/自环/重复 `(from,edge)`/`to` 不在白名单/未知 `schemaVersion` → L2 回退或 L3 崩溃；**深链不可达 → 拒绝整条深链**（§1.5.5-4）；多语言：错误只进日志，用户可见提示由 release Toast（primitives）承担。 |
| 11 | 无障碍 | 无节点语义。（用户实际接触的是入口按钮，`aria-expanded` 由入口按钮契约给出，见 `drag-open` 字段 11。） |
| 12 | 依据 | `user` **D-01=A**（覆盖栈 + 邻接图）；**E-23=A**（首版图内置、白名单强校验、编辑器归 M6）；`E0` `p2/base.layout.md §B.4`（L1–L4 四层防线，借太墟 `TX §5.6`）；`E1` `TX §5.6`（白名单/版本化/降级三原则）；`E0` `p2/base.layout.md §A.3` I1（单一导航真源）。 |
| 13 | 反证 + 验收 ID | **反证**：若任何面板都能直接指定 `(from,edge)→任意 to`，M6 的自定义图可造出"从设置滑出删除确认"的假入口，且返回栈的 `from` 不可信（假按钮/越权面板）；若把校验推迟到运行时，配错图 = 在用户手上白屏，而不是启动期崩溃。**验收**：`BL-01`（内置图通过 L1–L3，启动 0 崩溃）、`BL-02`（非法边请求被拒且栈不变）、`BL-03`（深链按图补祖先链）。 |

### `base.layout.panel-host` — 栈宿主（唯一改动栈的地方）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.panel-host`。所属：`base.layout` → `shell` 的主列槽。出现条件：**始终存在，1 个**；宽屏时为内容列，窄屏时占满屏宽。父节点：`shell`。 |
| 2 | 网格与定位 | 占满主列（`1fr × 1fr`，min 400）；栈内 route **同 origin 叠放**（不是并排）。`host` 自身零 padding（insets 由 route 内容根垫，§1.3.2）。 |
| 3 | 命中区 | 自身无命中区（容器）。命中由**栈顶 route** 及其遮罩拥有；空栈时 host 全屏吞指针（防指针穿到不存在的层）。 |
| 4 | 手势 | 不接手势；把指针流交给 `drag-open`（仅深度 0）/`drag-dismiss`（仅栈顶）/预测返回模块。 |
| 5 | 行为 | 只暴露 4 个改栈操作：`push / pop / popToRoot / replace`（§1.8.1）；`depth`、`keepAlive`、`cover` 由 host 计算，外部只读。状态机 `CREATED→ENTERING→ACTIVE→COVERED(KEEP)→FROZEN→EXITING→DESTROYED`；`push` 前置 = 同帧无动画 dismiss 栈顶层浮层并注销（否则浮层滞留被覆盖层，返回会先关一个不可见的东西）。**幂等**：同一 `OpenRequest` 在 `ENTERING` 中重复到达 → 忽略；`pop` 在 `EXITING` 中重复 → 忽略。不可撤销（pop 后不能 redo）。 |
| 6 | 视觉状态 | 自身透明。绘制集合 = 保活窗内的 route（`KEEP` 层 + 栈顶）+ 栈顶遮罩；`FROZEN` 层不在组合树内。 |
| 7 | 布局占位与展开形态 | 始终占满主列 → **栈深变化不改变 host 尺寸**，壳内兄弟零重排（G-02 同类要求）。栈属"形态 C（覆盖）"：不推挤下层布局。 |
| 8 | 滚动语义 | 不滚；滚动全部归各面板。 |
| 9 | 动效 | 不拥有曲线：转场属性/曲线由 route 的 `edge` + D-07 spring 决定；host 只负责调度与预组合。 |
| 10 | 边界态 | ① 空栈（≤1 帧，S0）；② 栈深无上限但只渲染保活窗；③ 进程重建 → 从 saveable 恢复栈（`routeId/panelId/params/from/edge`），`transitionProgress` 归零；④ 快速双击入口 → ENTERING 去重；⑤ 冻结层恢复失败（快照损坏）→ 该层降级为重新构建 + 记录，不整栈崩溃。 |
| 11 | 无障碍 | 容器无 role；焦点顺序由层语义决定（见 `a11y.covered`）。 |
| 12 | 依据 | `user` **D-01=A**；**E-01=A**；`E0` `p2/base.layout.md §A.1` 方案 1 / §A.3 I1+I2 / §B.2.4；`E0` `TX §2.1`（单栈 E0）、`GO §0-8`（页面栈 E0）；`E1` 冻结恢复快照方案（`TX §0-5` saveable 迁移）。 |
| 13 | 反证 + 验收 ID | **反证**：若 host 允许并存渲染（多窗/浮层数组），返回语义就没有单一真源（D-01 解 B 的坏处）；若 host 统一写死全屏宽度，抽屉无法半宽，旧外观丢失（p2 §2 `route` 行反证）；若 host 不负责"push 前收浮层"，返回链会先消费一个不可见浮层，用户连按两次才有反应。**验收**：`BL-04`（按钮 push 入栈）、`BL-05`（pop 一层）、`BL-06`（连点去重）、`BL-07`（回根）、`BL-08`（replace 不变量）、`BL-27`（push 时浮层同帧关闭）。 |

### `base.layout.route` — 面板实例（一层面板的几何与生命周期）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.route`。所属：`panel-host` 的栈项，或宽屏 `dockedRoutes` 槽项。出现条件：每次打开 1 个（`PanelRoute`，§1.4.2），栈深无上限；`presentation` ∈ `ROUTE_FULL / ROUTE_DRAWER / DOCKED_COLUMN`。 |
| 2 | 网格与定位 | 全屏 origin。宽：`FULL`=W；`MIN_MAX(320,0.86,56)`=`min(320+startInset, 0.86W, W−56)`；`FIXED`=280/56（列）。高=屏高（含系统栏区，内容根垫 insets）。进入边 `edge=LEFT` 时起点 `x=−W`，RIGHT 时 `x=+W`；`TOP/BOTTOM` 为纵向（首版无使用者，保留）。 |
| 3 | 命中区 | 覆盖区整层吞指针（`cover=FULL` 或 `PARTIAL` 的已覆盖部分）。**非栈顶 route 无命中区**（先吞指针，再清语义，见 `a11y.covered`）。内容命中归各面板。 |
| 4 | 手势 | 只在**栈顶**且 `swipe != null` 时生效：`direction=OUT/BOTH` 可拖出关；`direction=IN/BOTH` 且 `enabledWhen=ROOT_ONLY` 可拖开（仅 depth0）。深层**禁开新面板**（G-15），但允许"关自己"。 |
| 5 | 行为 | 生命周期见 `panel-host` 字段 5。`returnMode`：`POP`=退一层；`TO_ROOT`=回根（§1.8.1）；`HAND_TO_GESTURE`=把这次返回交给系统预测手势（13+ 走 progress，低版本等价 pop）。`route` 不直接改栈（由 host 执行）。 |
| 6 | 视觉状态 | `OVER_OPAQUE`：不透明底、**无遮罩**；`OVER_DIM`：半宽 + 遮罩（`alpha=0.32×progress`）；`PUSH_COLUMN`：宽屏列（无遮罩）。转场中只改位移（paint-only），**不缩放/不模糊下层**（D-05）。圆角/阴影归 theme。 |
| 7 | 布局占位与展开形态 | **形态 C（覆盖）**：不参与下层布局、不推挤下层；**静止 `revealDp=0`**（无露边/无叠卡）。只在拖拽/预测返回中露出（Q-BL-02=A / E-01）。 |
| 8 | 滚动语义 | 容器不滚；面板内容自己滚，内滚到边停住、不传残余速度（G-04）；抽屉是横向、列表纵向 → 正交不仲裁（`TX §2.1 drawer 字段8`）。 |
| 9 | 动效 | 进入：从 `edge` 滑入 `translationX ±W→0`，**paint-only**（`graphicsLayer`/`Transform`，禁止每帧改 padding/width）；退出：反向；弹簧 **0.86/420**（≈300ms 等效，D-07）；可中断（手指按下即接管，末速度交给动画）；reduced-motion → ≤120ms 淡入或直切（G-10 降级）。无高度动画。 |
| 10 | 边界态 | ① 空/加载/失败：L4 原始数据卡兜底；② 超长内容：面板内滚，壳不撑破；③ 大字号：宽度不变（dp），文字重排；④ 横屏：按新 W 重算宽度；⑤ 深色：token；⑥ RTL：`LEFT/RIGHT` 镜像（`direction = if (Rtl) −1 else 1`，`RuntimeRevealNavigationDrawer.kt:69`，E0）；⑦ 旋转/分屏：不重建（§1.3.2-⑨）。 |
| 11 | 无障碍 | 见 `a11y.covered`。抽屉型缺省 `role=dialog` + `paneTitle`；全屏型为普通页面语义；`route` 切换完成时 TalkBack 播报新页面标题（E1，Compose 语义映射待原型，decision-pack §4.2-6）。 |
| 12 | 依据 | `E0` `p2/base.layout.md §B.2.4`（`PanelRoute` 字段级）/§C.1（露边 0）/§C.4（宽度）/§C.8（动效）；`user` **D-01/D-05/D-06/D-07**；`E0` `TX §2.1`（全屏栈项 + 吞指针）；`E0` `GO §2.15`（全屏 route）；`E0` `M/:268-292`（抽屉宽公式）；`E0` `LEG §0`（旧结构全页覆盖）。 |
| 13 | 反证 + 验收 ID | **反证**：若 route 由 host 统一写死全屏 → 抽屉不存在（与旧外观冲突）；若静止露边 16–24dp 形成叠卡 → 就是"可见抓手"（用户明令禁止）＋抢下层命中盒（G-11）；若 route 参与下层布局 → 打开面板把聊天挤走，阅读锚点丢失（G-03 形态 C 反证）；若位移走每帧重布局 → 掉帧且第一帧就与手指错位。**验收**：`BL-15`（静止露边 0）、`BL-20`（全屏无遮罩且下层无缩放/模糊）、`BL-35`（宽屏列宽）、`BL-37`（COMPACT 全宽）、`BL-48`（reduced-motion）。 |

### `base.layout.route.scrim` — 遮罩（可点关闭）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.route.scrim`。所属：栈顶 route 的遮罩层。出现条件：**当且仅当** `cover=PARTIAL && coverMode=OVER_DIM`；**每屏最多 1 个**（只画栈顶那层）。全屏不透明面板**不创建**。 |
| 2 | 网格与定位 | 全屏 `inset:0`（含系统栏/挖孔区）；宽=W、高=屏高；**不随抽屉宽度缩**（抽屉内容画在它上面）。绘制顺序：下层 → 遮罩 → 抽屉内容。 |
| 3 | 命中区 | 全屏可点（被抽屉内容覆盖处由抽屉接住）；命中判定 = **G-18 严判据**（按下与松手都在遮罩上）。`onClickLabel="关闭"`。无扩边（本身全屏）。 |
| 4 | 手势 | 单击 = 关闭（严判据）；拖动超过 touch slop 即取消（不关）；不接上下滑；拖拽中（progress<1）不接点关（§1.7）。 |
| 5 | 行为 | 关闭 = 请求 `pop()` 栈顶 route（由 dispatcher/host 执行）；幂等（重复点只 pop 一次）；不可撤销。 |
| 6 | 视觉状态 | `alpha = 0.32 × progress`，线性；纯色（颜色 token 归 `base.theme`）；**无 blur、无缩放/压暗变化**；浅色/深色都同 alpha；按下遮罩无额外 pressed 视觉（避免多点一下的假反馈）。 |
| 7 | 布局占位与展开形态 | **浮层不占位**（G-01）：不进布局流、不产生高度、不推挤任何内容。 |
| 8 | 滚动语义 | 无（不接滚动，底层已不可滚）。 |
| 9 | 动效 | alpha 跟 progress 线性（无独立 tween，D-07 的"速度连续"同向）；reduced-motion 时同样由 progress 驱动，不另做时长。 |
| 10 | 边界态 | ① progress=0 → 不存在（route 关闭）；② 0<progress<1 → 半透明且**不可点关**；③ progress=1 → 可点关；④ 大字号/深色/横屏：几何与 alpha 不变；⑤ 多层叠加 → 只存在 1 个（S6）。 |
| 11 | 无障碍 | 遮罩自身为可点节点（`Role.Button` + `onClickLabel="关闭"`），**不单独播报/不抢焦点**（不做 liveRegion）；下层语义已被清（`a11y.covered`），读屏不会停在遮罩下的内容上。 |
| 12 | 依据 | `user` **D-05=A**（0.32、不缩放不模糊）、**D-08=B**（按下+松手都在遮罩）；`E0` `p2/base.layout.md §C.2`；`E0` `RuntimeAdaptiveNavigationScaffold.kt:169-172`（`scrim@0.32`）；`E0` `M/:722-725`（全屏面板 `display:none` 遮罩并给理由）；`E0` `GO §2.15 字段6`（scrim 随 progress 淡化）；`E1` `TX §4 Q-TX-12`（0.12/0.32 双值不一致=缺陷，不抄）。 |
| 13 | 反证 + 验收 ID | **反证**：若用"按下即关"（D-08 解 A），用户在面板内拖选文字、拖到遮罩上松手会关掉整页 → 丢掉刚填的表单（`_conflicts.md` C-BP-02）；若全屏不透明面板也画遮罩，白花一层 backdrop 成本且无视觉收益（`M/:722-725` 原话）；若多层各画遮罩，0.32×3≈全黑；若拖拽中就接受点关，拖到一半松手会同时触发"提交/取消"两个意图。**验收**：`BL-16`（正例）、`BL-17`（反例：框内起手拖到遮罩松手不关）、`BL-18`（不误触下层）、`BL-19`（0.32/线性）、`BL-21`（多层只一层）。 |

### `base.layout.route.back` — 每层返回入口

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.route.back`。所属：该层顶栏（顶栏本身归 `base.primitives`）。出现条件：**栈深 ≥1 且该层为栈顶**时 1 个；根层（depth0）无。父节点：该层顶栏。 |
| 2 | 网格与定位 | 顶栏最左（RTL 镜像到最右）；视觉图标 24dp；**命中盒 48×48**；与顶栏标题/其它图标净距 ≥8dp（G-16：命中盒不得相交）。顶栏高由 primitives 定；若顶栏 <48 高，按钮自身撑到 48（G-11）。 |
| 3 | 命中区 | 48×48 **布局盒**（padding 实现，不用 `Modifier.offset` 扩边）；与相邻图标命中盒不相交。 |
| 4 | 手势 | 单击 = 返回；长按/双击/滑动**不绑定**（不得继承父级手势）。 |
| 5 | 行为 | 调 `dispatcher.dispatch()`（与系统返回共用一个入口，**不直接 pop**）；幂等（`EXITING` 中忽略）；不可撤销。 |
| 6 | 视觉状态 | default / pressed（theme 提供）；**无 hover/focus 特殊**（D-09 触摸-only）；无 disabled（层存在即返回可用）。 |
| 7 | 布局占位与展开形态 | 参与顶栏布局流，占 48×48（顶栏 padding 归 primitives）；不展开。 |
| 8 | 滚动语义 | 不滚。顶栏是否随内容收起归各面板，本节点固定。 |
| 9 | 动效 | 无自身动画；按压反馈 ≤120ms 不透明度（theme）。 |
| 10 | 边界态 | 大字号：命中盒不变、图标不放大；深色：token；横屏：保留；RTL：镜像到右侧；宽屏列：列内面板无返回（列不进栈）。 |
| 11 | 无障碍 | `Role.Button`，`contentDescription` 必填（"返回"，随语言本地化）；**焦点顺序 = 顶栏第一个**；TalkBack 双击触发同一 dispatcher。 |
| 12 | 依据 | `E0` `p2/base.layout.md §2 route.back 行`（采纳"每层顶部有显式返回入口"）+ §C.4（F 取值）；`E0` `G-11`（判定式）；`E0` `G-16`（48 主档）；`user` **D-09=A**（触摸-only，无 hover/focus 分支）；`E0` `LEG §2.7`（旧有返回入口但**「‹」无 contentDescription**——缺陷，不抄）。 |
| 13 | 反证 + 验收 ID | **反证**：若只有手势/系统返回，大屏或外接键盘用户找不到退出路径（p2 §2 同款反证）；若无 `contentDescription`，TalkBack 只报"按钮"，用户不知道会退回哪；若返回钮直接 pop（绕过 dispatcher），浮层打开时会把整页 pop 掉（p2 Q-BL-09 解 C 的反证）。**验收**：`BL-45`（a11y 标签 + 48×48 量测）、`BL-22…BL-25`（走 dispatcher 的返回链）。 |

### `base.layout.route.drag-dismiss` — 拖出关闭

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.route.drag-dismiss`。所属：栈顶 route 的手势模块。出现条件：栈顶 route `swipe.direction ∈ {OUT, BOTH}` 且是栈顶；**同时最多 1 个活动实例**（只作用栈顶）。 |
| 2 | 网格与定位 | 作用于该 route 覆盖区；位移方向 = `edge` 的反向（LEFT 进入 → 向左拖出）；跟手 1:1；最大位移 = route 宽（拖出后完全离屏）。 |
| 3 | 命中区 | 不新增命中区（不建隐形热区）。起手落在"声明为横向可滚/可拖"的控件上 → **让给控件**；落在返回钮等点击控件上 → 让给点击；其余内容区可起手。 |
| 4 | 手势 | 水平拖拽；方向锁（水平位移 > 竖直且超过 touch slop 后锁横）；**起手 x<20dp 不接管**（左缘让系统返回，D-06）；提交 = 拖出宽 **≥0.22W** **或** 沿关闭方向末速度 **≥600dp/s**；否则回弹；与纵向列表正交、不仲裁（G-04 同轴才仲裁）。 |
| 5 | 行为 | 拖动期写 `transitionProgress`（paint-only 位移）；松手：未过阈值 → spring 回 0（cancel）；过阈值 → `pop()`（反向 spring，末速度交给动画）。**可中断**：手指再次按下立即接管。一次手势最多 1 次 pop。 |
| 6 | 视觉状态 | 位移中遮罩 `alpha=0.32×p`；下层不缩放不模糊（D-05）；**无静止露条**；无"抓到抓手"的视觉（无可见手柄）。 |
| 7 | 布局占位与展开形态 | 浮层不占位（G-01）；拖动中不改变下层布局。 |
| 8 | 滚动语义 | 与抽屉内列表纵向滚正交；列表到边停住、不把残余速度横传给关闭手势（G-04）。 |
| 9 | 动效 | spring `settle` 0.86/420，带末速度；reduced-motion → 直切或 ≤120ms 淡出；不弹跳（damping 0.86，禁止 <0.8）。 |
| 10 | 边界态 | ① 拖拽中收到系统返回 → 取消自绘手势，交给预测返回（防双通道，p2 R1）；② 栈深 ≥2 仍可拖出关自己（只作用栈顶）；③ 横屏/宽屏同理；④ 宽屏列形态无此手势（列不开合）；⑤ 键盘打开时拖出：pop 后焦点回入口，键盘由触发入口收起。 |
| 11 | 无障碍 | 手势**不是唯一关闭路径**（返回键、遮罩点关都在）；不依赖无障碍自定义动作；TalkBack 用返回键关。 |
| 12 | 依据 | `user` **D-06=A / D-07=A**；`E0` `G-14/G-15`；`E0` `LEG §2.8`（22% / 600px·s⁻¹ / 跟手 / 左缘 20dp）；`E0` `GO §0-8`、`TX §2.1 drawer 字段4`（anchoredDraggable 跟手）；`E1` `M5 §2.7`（spring 而非 tween 的理由）。 |
| 13 | 反证 + 验收 ID | **反证**：若收尾用固定时长 tween（旧 180ms），松手速度不为 0 时速度不连续 = 用户已抱怨的"手停了自己还会顿一下"（C-BL-01/D-07）；若拖到阈值后"突然跳到位"而不是 1:1，用户无法预判松手结果；若接管屏幕边缘，会与系统返回手势直接竞争（D-06 解 B 反证）。**验收**：`BL-12`（左缘让位）、`BL-13`（22% 提交/回弹）、`BL-14`（600dp/s 快速甩）、`BL-49`（手势中断/连续 20 次无跳变）。 |

### `base.layout.route.drag-open` — 根层内容区拖拽打开

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.route.drag-open`。所属：根层内容区（depth0）的手势模块。出现条件：**`currentDepth==0`** 且存在 `openSource=CONTENT_DRAG` 的边；每次手势最多产生 1 次 push。深层**不注册**（连观察都不做，避免与内容横滑打架）。 |
| 2 | 网格与定位 | 触发区 = 根层内容区（排除顶栏、输入区、浮层、横向滚动控件）；目标 route 从 `edge` 滑入；抽屉目标宽按 §1.4.3。 |
| 3 | 命中区 | **无新增命中区**（不建隐形热区，否则等于"可见抓手的隐形版"，p2 §D.1 注）；只在既有内容指针流上观察；左缘 20dp 不接管。 |
| 4 | 手势 | ① 观察阶段：累计水平位移 **0–96dp**，**不消费**（列表仍可竖向滚）；② 到 96dp **一次性接管**，令 `progress = clamp((dx−96dp)/Wdrawer, 0, 1)` 从 0 起跟手；③ 松手：面板**露出宽 ≥0.22W** 或 沿打开方向末速度 **≥600dp/s** → 提交 push；否则 spring 回 0、不 push。方向锁：水平 > 竖直且超 touch slop。 |
| 5 | 行为 | commit → 先 `keyboard.hide()`（开面板前收键盘）再 `push(target, edge, params)`；转场从当前 progress **继续到 1**（不重启动画）；cancel → 回弹且栈不变。幂等：一次手势只 push 一次（注释：96dp 前后都不重复触发）。 |
| 6 | 视觉状态 | 遮罩 `0.32×p`；下层聊天不缩放不模糊（D-05）；观察阶段**画面完全不动**（不预位移）。 |
| 7 | 布局占位与展开形态 | 浮层不占位；被拉出的面板不改变 chat 布局。 |
| 8 | 滚动语义 | 观察阶段列表仍可竖滚（只观察不消费的是横向，不干扰纵向）；接管后横向独占，纵向滚动停止；抽屉内列表到边停住（G-04）。 |
| 9 | 动效 | 接管后跟手 1:1；submit/cancel 走 spring 0.86/420；reduced-motion → 提交后直切/≤120ms 淡入。 |
| 10 | 边界态 | ① depth≥1 → 完全不响应；② 起手 x<20dp → 不接管（交系统返回）；③ 起手在横向列表/选字中 → 不接管；④ 键盘打开 → 提交时先收键盘（同帧，不阻塞转场）；⑤ 宽屏/MEDIUM 列形态：列无开合手势，rail 点击走按钮（本模块只在 COMPACT/MEDIUM 的内容区生效，且 MEDIUM 侧栏是覆盖 route → 仍可开）。 |
| 11 | 无障碍 | 手势**不是唯一入口**（顶栏菜单/按钮）；本节点自身无 a11y 语义。入口按钮的 `aria-expanded` / 展开态播报由 `base.primitives` 按钮契约给出（mobile-adapt 汉堡缺 `aria-expanded` 是缺陷，不抄，Q-BL-10）。 |
| 12 | 依据 | `user` **D-06=A**（内容区拖拽 + 过 96dp 才消费 + 深层禁开 + 左缘 20dp 让系统）；`E0` `G-15`；`E0` `LEG §2.8`（96dp、只观察不消费）；`E0` `MainPageNavigation.kt:7`（仅聊天根页可滑开）；`E1` `TX §2.1 drawer 字段13`（深层禁用原因：代码编辑器横滑选字误开）；`E0` `RuntimeRevealNavigationDrawer.kt:106-109`（开前收键盘）。 |
| 13 | 反证 + 验收 ID | **反证**：若任意层可滑开 → 用户在设置页横滑（选字/横向列表）会误开面板，且不知道"我是怎么进来的"（太墟反证原话）；若门槛设成"触摸即消费" → 内容横向滚动永远被抽屉抢（C-BL-03）；若做屏幕边缘侧滑 → 与 Android 系统返回手势直接竞争（D-06 解 B）；若不收键盘就开全屏面板 → 半屏键盘 + 半屏面板。**验收**：`BL-10`（<96dp 不消费、≥96dp 接管且 p 从 0 起）、`BL-11`（深层横滑不开）、`BL-12`（左缘让位）。 |

### `base.layout.route.predictive-back` — 预测性返回

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.route.predictive-back`。所属：系统返回通道（13+）。出现条件：Android 13+（API 33+）且**栈顶无面板内浮层**；每层栈顶 1 个活动回调。 |
| 2 | 网格与定位 | 不产生布局；只写栈顶 route 的 `transitionProgress`（位移）与遮罩 alpha。 |
| 3 | 命中区 | 手势区由**系统**管（屏幕边缘）；App 不建热区、不做 `systemGestureExclusion`。 |
| 4 | 手势 | 系统 started → progressed(0..1) → cancelled / invoked；App **只读 progress**；期间忽略自绘 drag-dismiss 与其它返回入口（防双触发）。 |
| 5 | 行为 | ① progress 驱动栈顶位移 + 遮罩；② **commit（invoked）**：`pop()` 但**不再播进入/退出动画**（画面已到目标态）；③ **cancel**：spring 回 0（末速度为 0，damping 0.86）；④ **浮层打开时忽略 progress**（面板不动），commit 时只关浮层（返回链第 2 级）；⑤ 同一手势重复 invoked → 去重，只 pop 一次。 |
| 6 | 视觉状态 | 跟手位移；遮罩 `0.32×progress`；下层不缩放不模糊（D-05）；无"系统预测快照"（我们自绘跟手，不接管系统的返回目标预览）。 |
| 7 | 布局占位与展开形态 | 浮层不占位；不进布局流。 |
| 8 | 滚动语义 | 无（手势期间不滚）。 |
| 9 | 动效 | 跟手；commit 后**无额外动画**；cancel 用 spring 0.86/420；reduced-motion 由系统/`prefers` 决定直切。 |
| 10 | 边界态 | ① API<33：无 progress → 收到返回立即 `pop()` + 反向 spring（≈300ms 等效）；② 手势中旋转/退后台 → cancel，progress 归 0；③ 浮层打开 → 忽略 progress；④ 栈底（depth0）→ 不注册（交系统退出）；⑤ 宽屏列 → 不参与（列不进栈）。 |
| 11 | 无障碍 | 无独立语义（系统手势）；TalkBack 用户走返回键/返回钮（同一 dispatcher）。 |
| 12 | 依据 | `user` **D-07=A**、**E-01=A**（13+ 用系统预测返回、低版本瞬间 pop）；`E0` `GO §2.15 字段4` + `PT:15-35,428-442`（dsh-Go 接入预测返回 E0）；`E0` `PN:34-114`（旧 myco 三通道共享 progress）；`E1` Android 平台文档：`enableOnBackInvokedCallback` + `OnBackInvokedDispatcher`（started/progressed/cancelled/invoked）/ Compose `PredictiveBackHandler`（**平台 API 名与 API 级别需 P4 核对**）。 |
| 13 | 反证 + 验收 ID | **反证**：不接预测返回 → 13+ 上用户手势期间画面不动、松手才跳，明显"廉价"（E-01 解 B 反证）；若自绘手势与系统通道**同时**写 progress → 一次返回退两层（p2 R1 风险，旧 myco 三通道共享 progress 正是为此）；若浮层打开时让面板跟手，会看到"浮层不动、背后面板滑走"的错乱。**验收**：`BL-28`（13+ 跟手）、`BL-29`（commit/cancel）、`BL-30`（低版本降级）、`BL-31`（无双触发）。 |

### `base.layout.back.dispatcher` — 单一返回 dispatcher

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.back.dispatcher`。所属：`panel-host` 内的**单例**（不是每层一个 `BackHandler`）。出现条件：**始终存在 1 个**。被 4 个来源调用：返回键、系统返回手势、`route.back`、预测返回 invoked。 |
| 2 | 网格与定位 | 无 UI（0×0）。 |
| 3 | 命中区 | 无命中区；不可点，只被事件调用。 |
| 4 | 手势 | 不接手势；被事件调用。**忽略规则**：`EXITING` 中的 route、预测手势进行中、上一条 dispatch 未完成（同 tick 去重）。 |
| 5 | 行为 | **优先级表 + 登记协议**见下表。返回 `Boolean`（是否消费）；`false` 才交系统（退出）。**每层如何登记**：① `base.primitives` 浮层管理器在 `show()` 时登记 `(ownerLayerRouteId, family, handler)`、`dismiss()` 时注销；② `panel-host` 在栈顶变化时为栈顶 route 登记 `(routeId, ROUTE, handler)`、销毁/失顶时注销；③ shell 常驻 ROOT 处理者；④ IME 不进表（每次 dispatch 实时读 `imeVisible`）；⑤ 被覆盖层的浮层不允许注销（push 时已同帧 dismiss）。dev 断言：层销毁未注销 → 断言失败；release 忽略并记录。 |
| 6 | 视觉状态 | 无。 |
| 7 | 布局占位与展开形态 | 无。 |
| 8 | 滚动语义 | 无。 |
| 9 | 动效 | 不拥有动画；只触发各层动作。 |
| 10 | 边界态 | ① 无处理者 → `false` → 系统退出（栈底）；② 浮层未登记 → 会穿透 pop 面板（必须避免）；③ 连续快按 → 每次只消费一层，不跳层；④ 浮层关闭动画中再按 → 忽略直到注销完成；⑤ 多语言：无 UI 文案（`onClickLabel` 归各节点）。 |
| 11 | 无障碍 | 无自身语义；它保证 TalkBack 的返回动作与视觉返回走同一顺序（不会出现"读屏退一层、视觉退两层"）。 |
| 12 | 依据 | `user` **E-01=A**（返回链 = IME→面板内浮层→栈顶 route→栈底回根，单一 dispatcher）；`E0` `p2/base.layout.md §C.3`；`E0` `GO §0-8` + `HS:319-333`（PopScope 顺序：右面板→多选→抽屉）；`E1` `TX §6.3`（7 级链，且自述"隐式依赖组合顺序"是缺点）；`E0` `p2/base.layout.md §A.3` I1（浮层不进导航栈但必须登记）。 |
| 13 | 反证 + 验收 ID | **反证**：若各层自己注册 `BackHandler`（太墟做法），谁最后组合谁赢，栈深 ≥2 时返回会随机关错层（`TX §6.3` 自述）；若浮层不登记到 dispatcher，浮层打开时返回会**穿透把整页 pop 掉**，用户丢上下文（Q-BL-09 解 C）；若不区分 IME，键盘开着按返回会退面板而不是收键盘（用户以为丢了页面）。**验收**：`BL-22`（IME 优先）、`BL-23`（浮层优先）、`BL-24`（逐层 pop）、`BL-25`（栈底）、`BL-26`（登记/注销）、`BL-27`（push 时浮层清理）。 |

#### `back.dispatcher` 优先级表（字段 5 展开）

| 序 | 层（ownerKind） | 谁登记 | 登记时机 | 消费动作 | 跳过条件 |
|---|---|---|---|---|---|
| 0 | （防重入） | — | — | 直接忽略 | 预测手势进行中 / 上一条 dispatch 未完成 |
| 1 | **IME** | shell（隐式读 `imeVisible`） | 每次 dispatch 实时判断 | 收键盘，**不 pop** | IME 不可见 |
| 2 | **面板内浮层 · 栈顶层的浮层栈顶项** | `base.primitives` 浮层管理器 | `show()` | dismiss 该浮层（1000/1100 家族；同一层多个浮层按打开逆序） | 该层无浮层 / 浮层不在栈顶层 |
| 3 | **栈顶 route** | `panel-host` | 成为栈顶 / 进入完成 | `returnMode=HAND_TO_GESTURE`（13+）→ 交系统预测手势；否则 `pop()` | route 声明 `PASS_THROUGH`（首版无）/ `EXITING` 中 |
| 4 | **栈底（ROOT）** | shell（常驻） | 常驻 | 当前不在根 → `popToRoot()`；已在根 → `false`（交系统退出；是否二次确认见 §5 P3-BL-01） | 栈非空时不轮到它 |

> 只有**栈顶 route 自己那层的浮层**可被消费：被覆盖层的浮层不允许存在（覆盖即隐藏 + push 时同帧 dismiss），所以 dispatcher 无需"跨层找浮层"，顺序是确定的。

### `base.layout.stack-lifetime` — 保活/冻结策略

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.stack-lifetime`。所属：`panel-host` 的保活策略计算器（每 route 生效态 `ACTIVE/KEEP/FROZEN`）。出现条件：**始终生效**；每次栈变化重算一次。无 UI。 |
| 2 | 网格与定位 | 无布局；影响的是**组合树成员**（`FROZEN` 层不在树内）。 |
| 3 | 命中区 | 无自身命中区；它决定 KEEP/FROZEN 的非栈顶层**无命中区**（吞指针由 `a11y.covered` 执行）。 |
| 4 | 手势 | 无；但它是"只有栈顶接手势"这一规则的来源。 |
| 5 | 行为 | 规则（§1.8.2）：栈顶 `ACTIVE`；非栈顶 `depth≤1` → `KEEP`（组合+状态保留）；非栈顶 `depth≥2` → `FROZEN`（`rememberSaveable` 快照 + 释放组合）；栈顶为 PARTIAL 时其下一层**强制 KEEP**。恢复：目标层在转场开始前预组合、同 key 重建。**业务状态（流式/网络/草稿/store）永不进冻结**。已注册浮层在冻结前必须已注销（由 `push` 保证），否则出现"幽灵浮层"。 |
| 6 | 视觉状态 | 冻结层不可见；恢复无淡入（≤1 帧内完成），避免"淡入一个重建的页面"。 |
| 7 | 布局占位与展开形态 | 不占位（全屏覆盖层，与布局无关）。 |
| 8 | 滚动语义 | `KEEP`：滚动位置天然保留；`FROZEN`：`LazyListState`/`scrollToSeq` 进 `rememberSaveable`，恢复后回到原位置（±1dp）。 |
| 9 | 动效 | 冻结/恢复**无动画**。 |
| 10 | 边界态 | 栈深 0/1/2/3+ 各自按规则算；进程重建走同一条恢复路径；快照损坏 → 该层降级为重新构建并记录（不整栈崩）；内存压力下系统杀进程 → 同重建。 |
| 11 | 无障碍 | `FROZEN`/`KEEP` 的非栈顶层必须清语义（见 `a11y.covered`）；恢复完成后才参与焦点序。 |
| 12 | 依据 | `user` **E-01=A**（深度 0–1 保活、≥2 冻结）；`E0` `p2/base.layout.md §C.6` / §3-1；`E0` `DSW §0-5`（右栏内容保持挂载，避免重载丢状态）；`E0` `TX §0-5`（隐藏保持组合 + `rememberSaveable`/`movableContentOf`）；`E0` `RuntimeRevealNavigationDrawer.kt:145`（保持组合以免首拖构建列表掉帧）。 |
| 13 | 反证 + 验收 ID | **反证**：全保活 → 层数越深内存/重组线性涨，且无任何参考实现支持"无限层全保活"；全冻结 → 抽屉第一次拖动才构建列表，太墟注释明说会掉帧（`RuntimeRevealNavigationDrawer.kt:145`）；冻结时保留浮层 → 返回先关一个不可见浮层，用户以为"按了没反应"。**验收**：`BL-09`（进程重建恢复栈）、`BL-32`（KEEP 流式/滚动）、`BL-33`（FROZEN 恢复滚动位置）、`BL-34`（PARTIAL 下层 KEEP）、`BL-27`（冻结前浮层已清理）。 |

### `base.layout.window-class` — 窗口类别（断点）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.window-class`。所属：`shell` 的窗口类别计算器。出现条件：**每帧/每次尺寸变化重算**，结果为 `COMPACT / MEDIUM / EXPANDED`。无 UI。 |
| 2 | 网格与定位 | 判据：`w<600` **或** `h<480` → `COMPACT`；`600≤w<840 && h≥480` → `MEDIUM`；`w≥840 && h≥480` → `EXPANDED`（§1.3.3）。 |
| 3 | 命中区 | 无命中区。 |
| 4 | 手势 | 无；输出"是否允许开合手势/是否列形态/是否 rail"。 |
| 5 | 行为 | 输出窗口类 → `shell` 选列模板、`sidebar-column` 选列态、`route` 选宽度（列/抽屉/全屏）。跨档时触发列↔栈迁移（§1.3.3），**内容不重建**。 |
| 6 | 视觉状态 | 不直接控制视觉；列的有无由 shell 落实。断点跨档**直切**（不留中间宽度动画）。 |
| 7 | 布局占位与展开形态 | 影响 shell 列模板（§1.2）。 |
| 8 | 滚动语义 | 无。 |
| 9 | 动效 | 断点跨档**直切**（**我方取值（P4 校准）**：旋转/分屏是系统事件，宽度 tween 会与系统 resize 同帧打架；代价=无过渡感）；用户手动收/开侧栏列用 spring（若启用）。 |
| 10 | 边界态 | ① 915×412 横屏 → COMPACT（不误判平板）；② 分屏 600–839 → MEDIUM；③ 折叠屏展开 ≥840 → EXPANDED；④ 大字号**不影响断点**（按 dp 逻辑宽，不按 sp）；⑤ 极窄 <320：COMPACT 仍单栏（面板 min 0）。 |
| 11 | 无障碍 | 无（rail/列头语义归 `sidebar`）。 |
| 12 | 依据 | `user` **D-04=A**（600/840 + 高<480 强制单栏）；`E0` `p2/base.layout.md §C.4`；`E0` `GO §2.15 字段2` + `AL:20-49`（断点 + 高度兜底）；`E0` `RuntimeWindowSize.kt:15-31`（太墟 600/840）；`E0` `columns.ts:23`（DSH 1024 为桌面鼠标密度值，**不采用**）；`E1` `M/:198`（mobile-adapt 767 为 CSS 适配产物，**不采用**）。 |
| 13 | 反证 + 验收 ID | **反证**：只按宽度 → 915×412 横屏被判平板，每列只剩一条缝（dsh-Go 反证原话）；用 1024（DSH）→ 800dp 平板只能抽屉，浪费横向；用 767（mobile-adapt）→ 与两家手机参考的 600/840 不一致且无高度兜底。**验收**：`BL-35`（EXPANDED）、`BL-36`（MEDIUM）、`BL-37`（COMPACT）、`BL-38`（高<480）、`BL-39`（跨档不重建）。 |

### `base.layout.shell` — 壳根（列网格 + overlay 槽 + insets 真源）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.shell`。所属：`base.layout` 根容器。出现条件：**始终 1 个**；覆盖全屏（edge-to-edge）。 |
| 2 | 网格与定位 | 列模板随窗口类：`COMPACT` = `[main] 1fr`；`MEDIUM` = `[rail] 56, [main] minmax(400,1fr)`；`EXPANDED` = `[sidebar] 280 / 56 / 0, [main] minmax(400,1fr), [right] 0（预留）`；行 `1fr`；列间距 0 + hairline（§1.2）。 |
| 3 | 命中区 | 自身无命中区；各列区域由内容拥有；`slots.overlay` 不拦指针（§1.6）。 |
| 4 | 手势 | 不接手势；分发给各模块。 |
| 5 | 行为 | 组装：侧栏列/rail（`sidebar-column`）+ 主列（`panel-host`）+ 右栏槽（预留）+ `slots.overlay` + z 阶梯；是 **insets 真源**（§1.3.2）：读一次 `safeDrawing` 并向下提供分解值 + 底部 union 真源。 |
| 6 | 视觉状态 | 底色/列 hairline 走 `base.theme`；**自身不画遮罩**（遮罩属于 route）。 |
| 7 | 布局占位与展开形态 | 列**参与布局流**（G-01 边界①：列不属 overlay）；面板栈/浮层不占位。 |
| 8 | 滚动语义 | 不滚（各列/各面板自己滚）。 |
| 9 | 动效 | 断点直切；手动收/开列 spring（若启用）。 |
| 10 | 边界态 | 空栈/加载/失败/极窄（<320 仍单栏）/大字号（列宽不变）/深色/横屏（§2 S0/S1/S10/S13/S14）。 |
| 11 | 无障碍 | 容器无 role；`overlay` 槽穿透（见 `slots.overlay`）；被覆盖层隔离见 `a11y.covered`。 |
| 12 | 依据 | `E0` `p2/base.layout.md §2 shell 行` / §C.4；`E0` `L/AppFrame.module.css:1-8` + `AppFrame.tsx:265-266`（三列 grid）；`E0` `DSS §0.2`（280/56/0）；`E0` `GO §2.15 字段2`（主列 min 400）；`user` **E-02=A**（insets 真源归容器）。 |
| 13 | 反证 + 验收 ID | **反证**：窄屏保留列 → 主列被压到 0（`DSS §0.2-③` 明写可压到 0），手机不可用；若 shell 给面板统一写全屏宽 → 抽屉无半宽；若 shell 也画遮罩 → 全屏面板出现莫名压暗（D-05 冲突）。**验收**：`BL-35/BL-36/BL-37`（三档列）、`BL-40`（bars/cutout）、`BL-41`（IME 单次底垫）、`BL-50`（overlay 穿透）。 |

### `base.layout.sidebar-column` — 宽屏侧栏列（rail/常驻列）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.sidebar-column`。所属：`shell` 的左侧栏槽（内容归 `sidebar`）。出现条件：`MEDIUM` 只有 **rail 56**；`EXPANDED` 有 **280 展开 / 56 rail / 0 隐藏**；`COMPACT` **不存在**（改走覆盖 route）。 |
| 2 | 网格与定位 | `EXPANDED`：展开 280（clamp 264–420，**首版不做拖拽**）；rail 56；隐藏 0。`MEDIUM`：rail 56 常驻。高度=全高（内容根垫 insets）。 |
| 3 | 命中区 | 列内命中归 `sidebar`（rail 图标、会话行等）；**列边缘无拖拽手柄**（8px 命中不可用，G-11 + p2 §4）。 |
| 4 | 手势 | 列形态**无开合手势**（不能滑开/滑关列）；rail/汉堡的点击由 `sidebar`/`primitives` 提供。 |
| 5 | 行为 | 展开/收起 = **列状态，不是 route**：不进栈、无遮罩、无返回键。`MEDIUM` 点 rail 开覆盖抽屉（= `push(ROUTE_DRAWER)`）。`EXPANDED` 展开/收 rail 由 `sidebar` 入口触发。跨断点用 `movableContentOf` 在**列槽 ↔ route 槽**间迁移同一内容；被移到列里的 route 暂存 `dockedRoutes[panelId]`（不参与返回），回窄屏时按原 `depth` 重插（抽屉形态）。 |
| 6 | 视觉状态 | 列底色/hairline 走 theme；无遮罩；收起成 rail 时只显示图标（尺寸归 `sidebar`）。 |
| 7 | 布局占位与展开形态 | **参与布局流**（G-01 边界①）；展开/收起改变主列宽（主列挤压，`min 400` 优先：侧栏只能占 `W−400`，超了先缩侧栏）。 |
| 8 | 滚动语义 | 列内列表自己滚；**滚动条槽预留**（`transparent` 重绑）防指针进出时整列横移 8–15px（p2 §3-6）。 |
| 9 | 动效 | 断点直切；手动收/开 spring（若启用）。 |
| 10 | 边界态 | ① `EXPANDED` 且 `W=840`：280+400=680 ≤840 ✓，侧栏不受挤压；`W` 逼近 840 时优先保主列 400；② 大字号：列宽不变（dp），内容换行；③ 横屏 `h<480`：列不存在（COMPACT）；④ 深色：token。 |
| 11 | 无障碍 | 列容器无 role；展开/收起状态由 `sidebar` 的入口按钮播报；收起为 rail 时内容语义随 sidebar。 |
| 12 | 依据 | `E0` `p2/base.layout.md §2 sidebar-column 行` / §C.4；`E0` `DSS §0.2-①②` + `columns.ts:13-23,52-58`（280/56/0 + clamp，E0）；`E0` `GO §2.15 字段2`（≥840 常驻 320 / rail 68）；`E0` `TX §0-3`（三态脚手架）；`E0` `RuntimeWindowSize.kt:15-31`；`E1` p2 §3-6（滚动条 gutter）。 |
| 13 | 反证 + 验收 ID | **反证**：取 dsh-Go 的 320 固定 → 1024 屏侧栏占 31% 且不能按内容调；做列宽拖拽手柄（DSH 8px）→ 触屏命中不可用且无需求（p2 §4）；列进返回栈 → 按返回关侧栏，与"返回=退面板"矛盾；列与覆盖实现两份内容 → 断点切换重建、滚动/输入丢失（p2 R5）。**验收**：`BL-35`（EXPANDED 280/56）、`BL-36`（MEDIUM rail）、`BL-39`（列↔覆盖迁移不重建、列不进栈）。 |

### `base.layout.slots.overlay` — 框架浮层槽（穿透）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.slots.overlay`。所属：`shell` 的独立层。出现条件：**始终存在 1 层**；`MEDIUM` 也可承载。 |
| 2 | 网格与定位 | 覆盖整个 shell（`inset:0`）；不参与布局；子元素按各自锚点定位（FAB 右下、Toast 底部、菜单锚点）。 |
| 3 | 命中区 | **槽本身 0 命中区、不消费指针**；子元素各自命中；空区域点击**穿透**到下层。 |
| 4 | 手势 | 不接；子浮层自己接。 |
| 5 | 行为 | 注册/注销浮层实例（"谁注册谁渲染"）；**不引入** ui-slots 的 abdicate/唯一渲染权协议（p2 §4）。层 z = **20**（低于面板栈 40）。 |
| 6 | 视觉状态 | 槽透明。 |
| 7 | 布局占位与展开形态 | **不占位**（G-01）；不产生高度、不推挤内容。 |
| 8 | 滚动语义 | 无。 |
| 9 | 动效 | 归各浮层；出现/消失按 G-10 不做高度动画（浮层本就无高度动画）。 |
| 10 | 边界态 | ① 空槽：零影响；② 多子元素：按注册顺序绘制；③ 与面板关系：面板打开时 z 40 > 20 → 浮层被面板盖住（**DSH 行为如此，E0**；需在面板上提示时由面板内联，不抬 Toast）；④ 多语言/大字号：子元素自负。 |
| 11 | 无障碍 | 槽无 role；子元素语义自定（Toast `role=alert` 归 primitives）。 |
| 12 | 依据 | `E0` `G-01`（浮层不占位）；`E0` `p2/base.layout.md §2 slots.overlay 行`；`E0` `L/AppFrame.module.css:281-290`（整层 `pointer-events:none`、子元素 auto；DSH z 20 于 `AppFrame.module.css:284`）；`E1` `D/Toast` 相关（`P/Toast.module.css:55-70`，横幅 `pointer-events:none`）。 |
| 13 | 反证 + 验收 ID | **反证**：无穿透槽 → 空 overlay 区域挡住下层点击（mobile-adapt 右栏全屏透明层要用 `pointer-events:none/auto` 手动纠，`M/:241-261`）；若把 Toast 抬到面板之上 → 与 DSH 层级语义不一致，且用户在面板里被"全局提示"抢焦点。**验收**：`BL-50`（z 关系 + 穿透 + Toast 不拦指针）。 |

### `base.layout.slots.right-rail` — 右栏列槽（首批不启用，预留）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.slots.right-rail`。所属：`shell` 第三列。出现条件：**首批不启用**（Q-06 首批不含 `base.sidebar-right`）；仅定义槽位、宽度规则与 z。 |
| 2 | 网格与定位 | `EXPANDED`：列宽 `minmax(0, 70%)`，首开 45%、**min 300**；宽度不足时按 `min(70%, W−280−400)` 收缩；`COMPACT/MEDIUM`：右栏以**全屏覆盖 route** 出现（归 `base.sidebar-right`）。 |
| 3 | 命中区 | 首批无。启用后归 `base.sidebar-right`。 |
| 4 | 手势 | 首批无。 |
| 5 | 行为 | 槽存在但宽 0；启用条件由 `base.sidebar-right` 定（宽≥840 且用户打开）。**不得静默消失**：宽度不足时降级为覆盖 route，而不是"轨道消失导致功能不可达"。 |
| 6 | 视觉状态 | 首批无（宽 0）。 |
| 7 | 布局占位与展开形态 | 启用后参与布局流（列，不属 overlay，G-01 边界①）。 |
| 8 | 滚动语义 | 启用后归 `base.sidebar-right`。 |
| 9 | 动效 | 首批无。 |
| 10 | 边界态 | ① 宽 0 时主列吃满；② 启用后 `W<840` → 覆盖 route；③ 启用后主列仍须 ≥400，不足则右栏自动收窄。 |
| 11 | 无障碍 | 首批无。 |
| 12 | 依据 | `E0` `p2/base.layout.md §2 slots.right-rail 行`；`E0` `L/columns.ts:11-29`（min 300 / max 70% / 首开 45%）；`E0` `DSS §0.2`；`E0` `MOB §2.7 #4`（右栏被整体隐藏 → 功能不可达 = 实测缺陷，不抄）。 |
| 13 | 反证 + 验收 ID | **反证**：若首批就启用 → 与 Q-06 范围冲突（右栏内容无 spec，等于写死节点）；若 `<300` 直接"轨道消失"（DSH 做法）→ 复现 mobile-adapt 实测的"右栏不见、功能不可达"。**验收**：`BL-51`（槽宽 0 / 主列吃满 / 常量就位）。 |

### `base.layout.z-ladder` — z 阶梯常量

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.z-ladder`。所属：全局常量契约。出现条件：**始终 1 份**（只读）。 |
| 2 | 网格与定位 | z 表（§1.6）：栈内零数字；跨家族 `0 / 10 / 20 / 40 / 1000 / 1100`；保留 `11/15/60/70`。 |
| 3 | 命中区 | 无命中区；但决定"谁吃指针/谁画在上面"。 |
| 4 | 手势 | 无。 |
| 5 | 行为 | 常量只读；栈内按渲染顺序、**不写 z 数字**；抬升只允许在浮层家族内，导航栈禁止抬升。 |
| 6 | 视觉状态 | 决定绘制顺序（面板盖列、modal 盖面板、menu 盖 modal、Toast 在面板之下）。 |
| 7 | 布局占位与展开形态 | 无。 |
| 8 | 滚动语义 | 无。 |
| 9 | 动效 | 无（层级切换不带动画）。 |
| 10 | 边界态 | 新增家族只能用保留槽；栈深增长不新增常量；浮层家族互斥（modal/sheet/lightbox 不并存）。 |
| 11 | 无障碍 | 无；但 z 与语义顺序必须一致（读屏焦点顺序 = z 降序，见 `a11y.covered`）。 |
| 12 | 依据 | `E0` `p2/base.layout.md §C.6` z 表；`E0` `DSH §0-7`（`L/AppFrame.module.css:211,231,284`、`P/Modal.module.css:6-15`、`P/ImageLightbox.module.css:2-6`、`P/Menu.module.css:42-45`）；`E0` `D/FloatLayer.tsx:177`（数组序即 z，且"写死 style 会被重渲染打回"= 反证）；`E0` p2 §A.3 I2。 |
| 13 | 反证 + 验收 ID | **反证**：若栈层写 `z=40+i` → 层数一多与 1000/1100 撞号，且 Compose 无全局 z 排序；若 z 写死在 style 而非模型序 → 抬升后一次重渲染打回旧值（`D/FloatLayer.tsx` 字段 13）；若 Toast 抬到面板上 → 与 DSH 层语义不一致。**验收**：`BL-50`（menu>modal>面板、Toast 在 overlay 不拦指针）。 |

### `base.layout.a11y.covered` — 被覆盖层隔离

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.a11y.covered`。所属：语义/命中隔离契约。出现条件：**任一非栈顶 route 被覆盖时**（KEEP 或 FROZEN 状态）。每层 1 份。 |
| 2 | 网格与定位 | 无布局（语义层）；覆盖区 = 该 route 的 `cover` 宽度（全宽或半宽）。 |
| 3 | 命中区 | 非栈顶层覆盖区**整层吞指针**（`awaitPointerEvent(Initial).consume()` 等价物）；全宽覆盖与部分覆盖（露出的条带不可点）**都吞**。 |
| 4 | 手势 | 吞掉全部指针（点击/滚动/拖拽），不区分。 |
| 5 | 行为 | ① 全宽覆盖 → 下层 `clearAndSetSemantics{}`（清空子树语义）；② 部分覆盖（抽屉）→ **也清**（露条不可点，读屏不该停）；③ 栈顶抽屉 → 本层 `Role.Dialog` + `paneTitle` + **焦点陷阱** + 关闭后**回焦**触发入口；④ 全屏栈顶 → 普通页面语义、无 trap（下层已清，焦点进不去）；⑤ 浮层语义归 `base.primitives`（modal/sheet 自带 dialog）。**切换时机**：进入转场完成时清下层；退出转场开始时恢复下层（避免转场中读屏读到正在动的层）。 |
| 6 | 视觉状态 | 无。 |
| 7 | 布局占位与展开形态 | 无。 |
| 8 | 滚动语义 | 被覆盖层不可滚（不可见）。 |
| 9 | 动效 | 无（语义切换按字段 5 时机，不加动画）。 |
| 10 | 边界态 | ① 快速连续开关 → 只在转场结束切换语义，避免焦点抖动；② 进程恢复 → 按当前栈重算；③ 多语言 → `paneTitle` 取面板标题（各面板 spec）；④ 大字号（不影响语义）。 |
| 11 | 无障碍 | 焦点陷阱：抽屉内容根 `FocusRequester` 首次请求 + 拦截焦点逃逸（`focusProperties`）；回焦：触发入口持有 `FocusRequester`，pop 完成回调 `requestFocus()`；入口 `aria-expanded` 由 `base.primitives` 按钮契约给出。⚠ **Compose 语义映射未验证**（`stateDescription`/`invisibleToUser`/回焦行为），依据级别 **E1**，需 P3/P4 最小原型（decision-pack §4.2-6）。 |
| 12 | 依据 | `user` **E-01=A**（覆盖即吞指针 + 清语义；抽屉补 `role=dialog`/焦点陷阱）；`E0` `p2/base.layout.md §2 a11y.covered 行` / §C.3；`E0` `TaiXuNavHost.kt:172-179`（页面级只吞指针**未清语义** = 自评缺口 Q-TX-11，不抄）；`E0` `RuntimeRevealNavigationDrawer.kt:149,160`（`clearAndSetSemantics`）；`E0` `GO §2.15 字段11`（`ExcludeSemantics`）；`E1` `MOB §3.1 drawer 字段11` + §6 Q-MOB-14（无 role/焦点陷阱 = 缺陷）；`E1` decision-pack §4.2-6（Compose 语义映射未验证）。 |
| 13 | 反证 + 验收 ID | **反证**：只吞指针不清语义 → TalkBack 仍可聚焦不可见页面，用户点了没反应且不知道自己在哪（太墟 Q-TX-11 实测）；无焦点陷阱 → 抽屉打开时焦点跑到背后聊天（mobile-adapt 缺陷）；转场中就切语义 → 读屏焦点被转场中的层吸走，用户"迷路"。**验收**：`BL-42`（吞指针）、`BL-43`（清语义）、`BL-44`（dialog/焦点陷阱/回焦）、`BL-46`（大字号下语义仍正确）。 |

## 4. 验收清单

> ID 前缀 `BL`，全局唯一，一旦分配不复用、不改号。方式三选：**真机手势** / **截图·录屏比对** / **单测**。
> **与 P2 的编号关系**：`p2/base.layout.md §E.1` 曾用 `BL-01…BL-11` 作迁移草案的占位标签（"设置→bin→返回一次回设置"等），**不是**验收 ID 分配；本表是最终分配，P4 按行为描述对齐，**不按 P2 那个草案编号**。

| ID | 判定 | 方式 | 依赖节点 |
|---|---|---|---|
| BL-01 | 内置 `chat-profile` v1 通过 L2/L3；注入 5 类坏图（缺根/自环/重复边/`to` 不在白名单/未知版本）→ L2 回退或 L3 启动崩溃，不进入 UI | 单测 + 启动冒烟 | `panel-graph` |
| BL-02 | 非法边请求（`from=bin, edge=RIGHT, target=settings`；`from=chat, edge=TOP, target=bin`）→ 拒绝、栈深不变、零转场、记录 1 条；dev 断言触发 | 单测 | `panel-graph` |
| BL-03 | 深链 `target=bin` → 栈变 `[chat,settings,bin]`（depth 0/1/2），只对 bin 播转场；不可达目标 → 栈不变 | 单测 + 真机（调试入口） | `panel-graph`/`panel-host` |
| BL-04 | 根层点顶栏菜单 → sessions 入栈 depth1、栈顶 ACTIVE、从 LEFT 滑入、下层聊天滚动位置不变（±0dp） | 真机录屏 | `panel-host` |
| BL-05 | settings 返回 → settings 出栈、chat 立即可见、聊天滚动位置不变、流式未中断 | 真机 | `panel-host` |
| BL-06 | 0.3s 内连点同一入口 5 次 → 只入栈 1 层（无重叠面板） | 单测 + 真机 | `panel-host` |
| BL-07 | 栈深 3 触发 `TO_ROOT` → 栈长 1，只播 **1 个**反向转场（无逐层连播） | 单测 + 录屏 | `panel-host` |
| BL-08 | `replace` 后：栈长不变、`depth` 不变、`from/edge` 继承、旧 route DESTROYED、返回仍回原来源 | 单测 | `panel-host` |
| BL-09 | 系统杀进程后恢复 → 栈恢复（`routeId/panelId/params/from/edge` 一致）、`progress=0`、无崩溃/空白帧 | 真机（开发者选项「不保留活动」）+ 单测 | `panel-host`/`stack-lifetime` |
| BL-10 | 根层右滑 80dp 松手 → 抽屉不出现且列表仍能竖滚；右滑过 96dp 继续到 200dp → p 从 0 起跟手；松手时露出宽 <22%W → 回弹且不 push | 真机手势 + 录屏（dp 标尺） | `drag-open` |
| BL-11 | settings 页内容区横滑 300dp → 无面板打开、横滑归内容、无遮罩闪 | 真机 | `drag-open` |
| BL-12 | 起手 x=10dp 横滑 → 面板不响应（交系统返回）；x=30dp 起手 → 可开/关 | 真机（手势导航开） | `drag-open`/`drag-dismiss` |
| BL-13 | 抽屉拖出 21.9%W 松手 → 回弹 0 且不 pop；22.1%W → pop | 真机 + 录屏量测 | `drag-dismiss` |
| BL-14 | 抽屉仅拖出 10%W 但末速度 >600dp/s 朝关闭 → pop | 真机 | `drag-dismiss` |
| BL-15 | 抽屉关闭后、面板打开后，屏幕边缘/面板边缘**无任何下层露出**（逐像素比对差=0）；拖拽中才露出 | 截图比对 | `route` |
| BL-16 | 抽屉全开，按下+松手都在遮罩 → pop | 真机 | `route.scrim` |
| BL-17 | ① 面板内按下、拖到遮罩上松手 → 不关；② 遮罩上按下、拖动 >touch slop 再松手 → 不关 | 真机 | `route.scrim` |
| BL-18 | 遮罩下内容点不中（聊天里链接/图片无响应）；遮罩按下时下层无 pressed 反馈 | 真机 | `route.scrim` |
| BL-19 | 全开遮罩实测 alpha=0.32±0.01；拖拽中采样 3 帧 alpha≈0.32×progress（±0.02）；无 blur（边缘锐利度比对） | 截图取色 | `route.scrim` |
| BL-20 | ① 单测：`coverMode=OVER_OPAQUE → scrim=NONE`，且组合树中不存在遮罩节点（`z` 阶梯里没有遮罩层）；② 录屏：关闭瞬间下层为原尺寸原清晰度（无缩放回弹、无 blur 残留） | 截图比对 + 录屏 + 单测 | `route`/`route.scrim` |
| BL-21 | 3 层叠加只存在 1 个遮罩：最暗采样点 alpha≈0.32（不是 0.54） | 真机截图采样 | `route.scrim` |
| BL-22 | 键盘打开时按返回 → 只收键盘，面板不 pop、栈深不变 | 真机 | `back.dispatcher` |
| BL-23 | sheet 打开时按返回 → 只关 sheet；再按一次 → pop 面板 | 真机 | `back.dispatcher` |
| BL-24 | 栈深 3 连按返回 3 次 → 逐层退 bin→settings→chat，每次只退一层 | 真机录屏 | `back.dispatcher` |
| BL-25 | depth0 按返回 → 交系统（退出），无空白帧、不 pop 不存在的层；栈深>1 时不触发退出 | 真机 + 单测 | `back.dispatcher` |
| BL-26 | dispatcher 单测：浮层 `show()`→dispatch 关浮层；`dismiss()`→dispatch 走下一级；层销毁未注销 → dev 断言 | 单测 | `back.dispatcher` |
| BL-27 | settings 打开 sheet → push(bin) → sheet 同帧无动画关闭并注销；pop 回 settings 无残留浮层/幽灵命中区 | 单测 + 真机 | `back.dispatcher`/`stack-lifetime` |
| BL-28 | Android 13+：系统手势进行中栈顶位移 = 系统 progress 1:1（逐帧误差 ≤2dp），遮罩同步 | 真机 13+ | `predictive-back` |
| BL-29 | commit → 停在目标态无第二次动画；cancel → 回弹无过冲（damping 0.86） | 真机慢动作 | `predictive-back` |
| BL-30 | Android 12（API<33）返回 → 立即 pop + 反向 spring（无跟手）、无崩溃 | 真机 API<33 | `predictive-back` |
| BL-31 | 13+ 上 20 次「手势 / 返回键」交替 → 每次只退一层（无一次退两层） | 真机录屏 | `predictive-back` |
| BL-32 | 流式中打开设置停留 30s 返回 → 流式未中断、滚动位置不变、无重载闪白 | 真机录屏 | `stack-lifetime` |
| BL-33 | chat→settings→bin→再开 1 层使 settings 进 FROZEN → 返回两次到 settings：滚动位置恢复（±1dp）、tab 保持、无重建闪白；流式仍连续 | 真机 + 单测快照 | `stack-lifetime` |
| BL-34 | 抽屉打开时其下层保持 KEEP：关闭后无重建闪白；抽屉首拖无列表构建掉帧（帧率采样无 >2 帧抖动） | 真机录屏/帧率 | `stack-lifetime` |
| BL-35 | EXPANDED（≥840）：侧栏 280、rail 56、主列自适应 ≥400；收成 56 后主列 ≥400 | 真机量测截图 | `window-class`/`sidebar-column` |
| BL-36 | MEDIUM（600/700/839）：rail 56 常驻、主列 ≥400；侧栏展开为覆盖抽屉（宽=`min(320+inset,0.86W,W−56)`） | 真机/模拟器改宽 + 量测 | `window-class`/`sidebar-column` |
| BL-37 | COMPACT（<600）：全宽单栏、无 rail、侧栏为覆盖抽屉（PARTIAL） | 真机/模拟器 | `window-class` |
| BL-38 | 915×412 横屏 → 判定 COMPACT（单栏），不出现三列、主列不被压 <400 | 真机 + 单测 `window-class` | `window-class` |
| BL-39 | 竖→横→分屏→宽屏：输入框文本/滚动位置/栈内容不丢；列↔覆盖迁移不重建（无闪白/无光标丢）；列形态按返回**不关列** | 真机录屏 | `window-class`/`sidebar-column` |
| BL-40 | edge-to-edge：顶栏不被状态栏遮、底部不被手势条遮；横屏挖孔机内容不进 cutout 区 | 真机（含挖孔机）+ 截图 | `shell` |
| BL-41 | 键盘弹出：输入区上移不被遮、最后一条消息可见、列表底部留白=输入区实测高；收起无跳位；无「双垫」多余空隙 | 真机 Gboard 中文 | `shell` |
| BL-42 | 抽屉/全屏打开时点击覆盖区落到下层 → 无任何下层响应（链接/列表/滚动都不动） | 真机 + 单测命中 | `a11y.covered` |
| BL-43 | TalkBack 打开面板后逐元素滑动 → 焦点序列中不存在被覆盖层节点 | 真机 TalkBack | `a11y.covered` |
| BL-44 | 抽屉打开 → TalkBack 报「对话框」并读标题；连划 10 次焦点不逃出；关闭后焦点回触发入口 | 真机 TalkBack | `a11y.covered` |
| BL-45 | 每层返回入口 `contentDescription="返回"`（随语言）；命中盒实测 48×48 | 真机 + 量测截图 | `route.back` |
| BL-46 | fontScale 2.0：列宽/抽屉宽不变、返回入口 48 命中、顶栏不裁切（可换行/撑高）、抽屉内容可读 | 真机截图 | `shell`/`route.back`/`a11y.covered` |
| BL-47 | 深色：遮罩 alpha 仍 0.32、列 hairline 可见、切换瞬间无白闪 | 截图逐帧 | `route.scrim`/`shell` |
| BL-48 | 系统「移除动画」：开合 ≤120ms 淡入或直切（无位移 spring），返回仍正确 | 真机（开发者选项） | `route`/`drag-dismiss` |
| BL-49 | ① spring 进行中反向按下 → 立即接管、无回跳；② 连续 20 次开-关-开录屏 0.25x 无位移跳变 | 真机录屏逐帧 | `drag-dismiss`/`predictive-back` |
| BL-50 | ① menu 在 modal 之上；② modal/lightbox 在面板栈之上；③ Toast 空白处点击穿透；④ overlay 槽空区域点击穿到下层 | 真机 + 单测常量断言 | `z-ladder`/`slots.overlay` |
| BL-51 | EXPANDED 且右栏未启用 → 第三列宽 0、主列吃满（无空白列/无敌挡）；常量 min 300/70%/45% 已配置 | 单测 + 截图 | `slots.right-rail` |
| BL-52 | 注入面板内容渲染异常 → 原始数据卡兜底（不白屏/不转圈到死）、壳不崩、返回可用、栈状态正确 | 故障注入 + 真机 | `panel-host`/`route` |

## 5. 待确认

> 只列 P2 未拍板的**新决策点**与**文档冲突**。已拍板项（D-01/04/05/06/07/08/09、E-01/02/23）一律不重开。

| ID | 问题 | 两解与后果 | 阻塞？ |
|---|---|---|---|
| P3-BL-01 | **栈底（depth0）再按一次返回**：直接交系统退出，还是「再按一次退出」提示？ | **A 直接交系统**：E-01 只写"栈底回根"，未要求确认；少一类浮层与计时器，返回链止于 `false`。**B 二次确认**：再按一次退出（Toast/Snackbar 提示），防误退；多一个浮层、一个计时器、一条 a11y 播报，且浮层归 primitives。 | **仅阻塞 BL-25 的"退出"分支判定**（dispatcher 的 `false` 已可实现，不阻塞壳主体）。建议 A，二次确认留设置项。 |
| P3-BL-02 | **首版是否接外部深链**（通知/URL/进程恢复之外的入口）进入面板栈？ | **A 不接**：Q-06 首批无深链入口；§1.5.5 契约只用于内部恢复与测试入口。**B 接**：需 URL scheme + 图反向合成祖先链（§1.5.5），外部输入直接成为导航输入，安全面扩大（必须过 L1–L3）。 | **阻塞 BL-03 的"真机入口"**（单测可先做）；不阻塞壳。建议 A。 |
| P3-BL-03 | **[文档勘误，非决策]** `p2/base.layout.md` §D.1-D4 写"深度 1 冻结、深度 2+ 释放 UI"、§B.3 示例把 depth1 写 `FREEZE`，与 §C.6 / **E-01=A**"深度 0–1 保活、≥2 冻结"**冲突**。本文件按 **E-01** 执行（§1.8.2 / `stack-lifetime`）。 | **A 以 E-01 为准**（本文件已如此写）；**B 改 E-01**（则抽屉/聊天保活语义回退，与"切会话不重挂载"冲突）。请 Lead 修 p2 两处原文。 | 不阻塞（本文件已收敛）。 |
| P3-BL-04 | **抽屉无显式关闭按钮**（Q-BL-10 解 A）时，是否给读屏用户一个自定义"关闭"动作？ | **A 不给**：系统返回/TalkBack 返回手势可关；视觉与旧版一致，语义最少。**B 给 `customActions=[关闭]`**：不占视觉件、读屏发现性更好；多一条要验收的语义。 | **阻塞 BL-44 的一条子项**（焦点陷阱与回焦不受影响）；不阻塞布局。建议 B。 |
| P3-BL-05 | **面板内浮层打开时发生 push**（内容动作导航），浮层怎么处理？本文件已按 **A** 写（`panel-host` 字段 5）：同帧无动画 dismiss 并从 dispatcher 注销。 | **A（已写）**：符合 I1（浮层不进导航栈、被覆盖后不可见）；用户返回时直接退面板。**B 保留浮层随层冻结/恢复**：返回时先关浮层，用户感觉"连按两次没反应"；且浮层归属与 z 会跨层混乱。 | 不阻塞（契约已按 I1 推导）；**若选 B 须重开 I1/浮层归属**。建议 A。 |

---

**自检（对照 `_p3-brief.md` 硬规则）**

1. 未重开任何拍板项：全文以 D-01/04/05/06/07/08/09、E-01/02/23 为公理；发现 p2 与 E-01 冲突 → 写入 §5 P3-BL-03，未自行改决策。
2. 数值来源：全部给 `p2` 具体节 / `ref:line`（E0）/ G 规则；纯我方取值标「我方取值（P4 校准）」并给反证（列间距 0、拖拽中不接点关、回根单转场、断点直切、单次 union 底垫）。
3. 17 个节点每节 13 字段、唯一 ID、证据、反证、≥1 验收 ID；结构契约节点字段 3 明写"无命中区 + 原因"。
4. 状态矩阵（§2）覆盖 空/加载/正常/长内容/失败/禁用/深色/大字号/横屏·宽屏，另加 4 个本面板特有态。
5. 验收 52 条均写"判定 + 方式 + 依赖"，无"点开正常"式空话。
6. 明确不做已在 §0.4 一行摘要（不逐条展开）。
7. 只写本文件；未改 `p2/**`、`ref/**`、规则、决策包、其它 `panels/*.md`；未改 app；未 commit。




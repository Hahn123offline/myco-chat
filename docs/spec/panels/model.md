# 面板规格：model（模型 × 推理等级面板）

> 本文件是 P4 实现 `model` 面板的**唯一依据**。实现时不得再回看 `ref/`、不得自行改数值。
> 输入：`_p3-brief.md`、`schema-node.md`（13 字段 + 金标准）、`00-global-rules.md`（G-00…G-18）、`decision-pack.md` §6（D-01…D-20 + §6.1）、`p2/model.md`（13 节点）。
> 证据级别：`E0` 源码行/真机/用户拍板（唯一可进实现）· `E1` 文档声称（可进 spec）· `E2` 推测（**本文件零出现**）。
> **本文件的关键证据链已回源码复核（不是转抄 ref 的结论）**，缩写表：

| 缩写 | 真实路径 | 说明 |
|---|---|---|
| `MSel/…tsx` / `MSel/…css` | `E:\deepseek-harness\packages\client\ui-model-selection\src\client\ModelSelect.tsx` / `.module.css` | DSH 模型胶囊 + 菜单 |
| `MSel/index.ts` | 同目录 `index.ts` | DSH 两入口注册 + `available` 判定 |
| `IBar.tsx` | `E:\deepseek-harness\packages\client\ui-conversation\src\client\skeleton\InputBar.tsx` | DSH 输入区：模型座位的锁定口径 |
| `go/model_sheet.dart` | `E:\myco-chat\_ws\refs\dsh-Go\app\lib\ui\chat\model_sheet.dart` | dsh-Go 模型面板（E0） |
| `IB:` | `core/src/main/kotlin/dev/myco/core/bridge/InProcessBridge.kt` | **myco 后端**：档位→请求参数的唯一映射点 |
| `BR:` | `core/src/main/kotlin/dev/myco/core/bridge/Bridge.kt` | `Command.ChatSend` / `Command.SessionSetModel` 定义 |
| `MD:` | `core/src/main/kotlin/dev/myco/core/spec/Models.kt` | `ModelProfile` / `ReasoningProfile` schema |
| `SS:` | `core/src/main/kotlin/dev/myco/core/session/SessionStore.kt` | 会话 sidecar（选择持久化） |
| `OA:` | `core/src/main/kotlin/dev/myco/core/llm/OpenAiCompatibleClient.kt` | provider 线协议字段 |
| `IBT:` | `core/src/test/kotlin/dev/myco/core/bridge/InProcessBridgeTest.kt` | **已有单测**（档位→请求字段断言） |
| `MA:` | `app-android/src/main/kotlin/dev/myco/android/MainActivity.kt` | 旧 myco UI（缺陷来源） |
| `M/` | `_ws/refs/dsh-client-ui-mobile-adapt/lib/client.js` | mobile-adapt（工具行 112/44 的唯一 E0） |
| `BP` / `BL` | `panels/base.primitives.md` / `panels/base.layout.md` | 原子与容器契约（本文件只引用） |

> 验收 ID：`MODEL-01…MODEL-45`（本文件全新分配，`acceptance.md` 无 MODEL 历史号）。
> 尺寸一律 dp；px→dp 按 E1 平台映射（`open-questions.md` Q-05）。

---

## 0. 一句话 + 范围

**一句话**：`model` 面板 = 输入区工具行上的**一颗胶囊**（显示 `模型名 · 推理等级`）+ 一个**底部 sheet**（按 provider 分组的模型列表 × 推理等级小节 × 条件搜索），它是「本轮/后续轮用哪个模型、用哪档思考」的**唯一写入面**——选中即写会话级 sidecar，发送时原样透传，**全 app 不再存在第二个写思考状态的入口**。

**负责**：胶囊内容/降级/不可用态；sheet 的容器几何与状态机；模型分组（组头吸顶 + 折叠）；推理等级小节（含 `off` 档与 `Default` 行）；条件搜索；目录加载失败/部分失败/空目录三态；提交语义（乐观写、pending、失败回滚 + Toast、同值只关）；档位→发送参数的**唯一映射表**（§2.2）。

**不负责**（归邻面板，本文件只引用不定义）：
- **工具行座位、工具行单行 nowrap、主键语义**（`composer`；P2 §6.2-5 已切分：composer 拥有**座位**，model 拥有**胶囊内容与面板本体**）；
- **provider 目录与模型增删、上下文长度、默认档**（`settings.models`；C-08/C-M5-07）；
- **浮层宿主、遮罩 alpha 0.32、返回链 dispatcher、insets 真源、z 阶梯**（`base.layout` D-01/E-01/E-02 + `base.primitives` sheet 原子）；
- **思考内容本身**（`chat`；本面板只决定"发不发/发哪档"，不改写任何历史轮）；
- **agent preset**（首版不做，见下）。

**与 P2 的 6 处收敛（已按拍板落实，不再讨论）**：
1. **布尔「深度思考」开关删除**（D-12=A）；`off` 档 = 不思考；模型无 `off` 档时给 `Default` 行；composer 不留一键开关。
2. **形态 = 底部 sheet**（D-13=B），单页：`标题 → 搜索(条件) → 模型分组(内滚) → 推理等级小节`；无两级钻取（推翻 P2 `model.menu.root` 的根菜单）。
3. **面板只有两维 + 搜索**（C-08 已定）；preset 小节**只预留位置、不渲染**；上下文长度不出现。
4. **行级命中 44dp、图标 40dp**（D-02/G-16）；胶囊属于"操作条控件"→ **40dp**，不是 44。
5. **只触摸**（D-09=A）：无 hover、无方向键虚拟高亮、无 tooltip、无 `Role.Tab`。
6. `model.preset.dimension` / `model.context.dimension` / `model.command-popup` **不进节点表**（前两个不渲染、第三个不做）；`model.thinking-switch` **删除**。节点从 P2 的 13 个收敛为 **11 个**（新增 `model.sheet`、`model.menu.model-group-header`、`model.menu.empty`；合并/删除其余，见 §3 末尾对照）。

**明确不做（§0 末尾一行摘要，P2 §4 共 5 条 + 2 条删减，实现时不得"顺手做了"）**：agent preset 全链路（布局预留、功能不做）｜面板内可改上下文长度｜`/model` 命令弹窗第二入口｜温度/采样第三维｜定价/额度/用量汇总卡｜**布尔「深度思考」开关**（D-12 删除）｜**两级钻取根菜单**（D-13=B 单页取代）。

---

## 1. 布局与网格

### 1.1 与容器的关系

| 项 | 契约 | 依据 |
|---|---|---|
| 胶囊所在槽位 | `composer` 工具行的**命名座位**；座位宽度由 composer 的工具行约束给（本面板只声明"最多要 112dp"） | P2 §6.2-5；`MSel/index.ts:168-190` |
| sheet 的形态 | **G-03 形态 C（sheet-overlay）**：独立 window、不占位、不进 `List<PanelRoute>`、不参与布局流 | D-03=B / G-01 / D-13；`BP §3.11` |
| 在 M2 覆盖栈中的位置 | **不在栈里**（浮层）；由所在层（chat 深度 0）的浮层管理器 `show()` 登记 | D-01 + E-01（"面板内浮层"）；`BL §1.8` |
| 返回语义 | 单一 dispatcher 优先级：**IME → 本 sheet → 栈顶 route（chat 本身在 depth0 时交系统）**。sheet 打开时按返回**只关 sheet**，一次只退一层 | E-01=A；`BL-23`（原文："sheet 打开时按返回 → 只关 sheet；再按一次 → pop 面板"）；D-13 明确要求"返回键先关 sheet" |
| insets | 底部垫 `systemBars ∪ displayCutout` **只垫一次**（不在 sheet 内容里二次垫）；IME 由 sheet 外层 `imePadding` 统一处理 | `BP §1.3`（太墟 `Dialogs.kt:163-189` 注释"不要在 sheet 内再垫一次"）；E-02=A |
| 遮罩 | 0.32 alpha，无 blur、无缩放（D-05=A）；**只建一层**，不因内部结构升级 | D-05；`BL-19` |
| z | sheet = 浮层家族 **1000**；Toast = overlay 槽 **20**（**低于 sheet**，所以失败 Toast 不能在 sheet 打开时被看见 → §2.3 的"失败先关 sheet"由此推导） | `BL §1.6` z 阶梯（1000 modal/sheet/lightbox、20 Toast）；`BL-50` |
| 与 composer 的关系 | 打开后 sheet **盖住输入区（含草稿与附件条）**；这是 D-13=B 已接受的代价，不做"半高露出草稿"的花活 | D-13（题设明确列出"盖住输入区时的 insets"这一代价项） |
| 宽屏（≥600dp） | sheet 宽 **640 居中**，形态不变；不改成居右面板 | `BP §1.2`（BottomSheet 最大宽 640，**我方取值（P4 校准）**，BP 已登记） |

### 1.2 线框（三个状态）

**A. 空（目录加载成功但没有任何可用模型）**

```
┌──────────────────────────── 遮罩 0.32 ────────────────────────────┐
│  (chat 列表 / composer 被盖住、保活、吞指针、语义已清)              │
│                                                                   │
│  ┌───────────────────── sheet（全宽） ──────────────────────────┐ │
│  │                        ▔▔ (把手 36×4)                        │ │
│  │  模型与推理等级                                        [ ✕ 44 ] │ │
│  │                                                              │ │
│  │                     ( 图标 32 )                              │ │
│  │                  还没有可用的模型                             │ │
│  │            先到「设置 › 模型」添加 provider 与模型            │ │
│  │                                                              │ │
│  │              [      去设置 ›       ] 48dp                    │ │
│  │            （push 前先关本 sheet，返回不自动重开）             │ │
│  │                                                              │ │
│  │  ▁▁▁▁ systemBars inset（只垫一次）▁▁▁▁                        │ │
│  └──────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────┘
```

**B. 常态（3 个模型 / 当前 `DeepSeek-V3.2 · 中` / 无搜索行）**

```
┌────────────────────────── 遮罩 0.32 ──────────────────────────────┐
│  ┌───────────────────── sheet（全宽） ──────────────────────────┐ │
│  │                        ▔▔ (把手 36×4，命中 96×44)             │ │
│  │  模型与推理等级                                        [ ✕ 44 ] │ │
│  ├──────────────────────────────────────────────────────────────┤ │
│  │  DeepSeek（组头 44，吸顶）                          ⌄         │ │
│  │  DeepSeek-V3.2                                    ✓ 20        │ │  ← 行 44
│  │  DeepSeek-R1                                       —           │ │
│  │  OpenRouter（组头 44）                              ⌄         │ │
│  │  Llama-3.3-70B                                     —           │ │
│  ├──────────────────────────────────────────────────────────────┤ │  ← 0.5 分隔 + 8
│  │  推理等级（小节标签 32，不可点）                               │ │
│  │  关闭                                     （off 档，apiValue 空）│ │  ← 行 44
│  │  低                                                        —   │ │
│  │  中                                                       ✓   │ │
│  │  高                                                        —   │ │
│  │  ▁▁▁▁ systemBars inset（只垫一次）▁▁▁▁                        │ │
│  └──────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────┘
```

**C. 极端内容（20 模型 / 搜索有 query / 一组折叠 / 一 provider 部分失败 / 6 档位 / 当前模型不可用）**

```
┌────────────────────────── 遮罩 0.32 ──────────────────────────────┐
│  ┌───────────────────── sheet（max 0.8×窗口高）───────────────┐ │
│  │  模型与推理等级                                        [ ✕ 44 ] │ │
│  │  ┌──────────────────────────────────────────────────────────┐ │ │ ← 搜索行 44（20>4 才出现）
│  │  │ ⌕  v3                                          [ ✕ 44 ] │ │ │
│  │  └──────────────────────────────────────────────────────────┘ │ │
│  │  ⚠ 3 个 provider 未加载：OpenRouter、Local、Proxy      [重试] │ │ │ ← 警告条（部分失败）
│  │  DeepSeek（组头吸顶 44）                              ⌄      │ │
│  │  DeepSeek-V3.2                                        ✓      │ │
│  │  DeepSeek-V3.2-Exp                                    —      │ │
│  │  ▸ OpenRouter（已折叠的组头 44）                              │ │  ← 折叠：行移除、不占位
│  │  Anthropic（组头吸顶 44）                             ⌄      │ │
│  │  Claude-Sonnet-4                                     —      │ │
│  │  Claude-Haiku-4                            不可用 12/18       │ │ ← disabled 行（40% 不透明 + 原因）
│  │  ─────────────────────────────────────────────────────────── │ │
│  │  推理等级                                                                │
│  │  Default（由模型决定档位）                             ✓      │ │ ← 模型无 off 档才有此行
│  │  最小 / 低 / 中 / 高 / 极高 / 最高（>4 档：小节内滚，上限 4 行）  │ │
│  │  ▁▁▁▁ inset ▁▁▁▁                                              │ │
│  └──────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────┘
```

**胶囊的 3 个降级态（工具行内，不是浮层）**

```
态 A（完整）        态 B（丢档位）       态 C（只图标）
┌──────────────┐    ┌────────────┐       ┌──────┐
│DeepSeek-V3.2 · 中 ⌄│    │DeepSeek-V… ⌄│       │ ▣  ⌄ │
└──────────────┘    └────────────┘       └──────┘
 ← ≤112dp →          ← ≤112dp →          ← 44dp →
```

### 1.3 网格表（全部 dp）

| 项 | 值 | 依据 |
|---|---|---|
| **sheet** 最大高 | `min(内容高, 0.8 × 窗口高)` | `BP §1.2/§3.11`（太墟 `UI_GUIDELINES.md:65`，E1） |
| sheet 宽 | COMPACT 全宽；≥600dp → **640 居中** | `BP §1.2`（**我方取值（P4 校准）**） |
| sheet 上圆角 | 16（仅上两角） | `BP §3.11` |
| 水平 gutter（sheet 内容左右内边距） | **20** | `BP §1.2`（dsh-Go `kSheetGutter=20`，E0） |
| 把手 | 视觉 36×4；命中 **96×44** | `BP §3.11` |
| 标题行 | **56**（标题 16/24 单行 ellipsis + 关闭钮 44） | `BP §3.11` |
| 关闭钮 | **44×44**（图标 20），标题行右端 | `BP §1.2` 表（E0 家族） |
| 搜索行 | **44**（输入框 44 高、圆角 12、左右内边距 12），在标题下固定、不随列表滚 | 我方取值（P4 校准）：行级 44 = G-16 行档；圆角 12 = 与 `BP` input 圆角族一致 |
| 搜索清除钮 | **44×44**（查询非空时出现；输入框 `padding-right: 48`） | `M/:507-538`（E0：清除钮 44、输入右 padding 48） |
| 列表行（模型行 / 档位行 / 空态按钮） | **44** | G-16 + D-02（行/整块可点 44） |
| 组头（可折叠） | **44** 高、满宽、可点 | G-16（行级 44，折叠开关是"行"） |
| 小节标签「推理等级」 | **32** 高、不可点、12/18 tertiary | 我方取值（P4 校准）：非交互标签不占行档，32 = 12/18 + 上下 8；与 `BP` 说明文字族一致 |
| 行内文字 | 模型名 **14/20**（正文级）；组头 13/20 secondary；小节标签与原因/副文案 12/18 | `BP §1.2` 字号族（14·20 / 13·20 / 12·18，E0） |
| 行内布局 | 左内边距 20 → 文字（单行 ellipsis，`flex:1`）→ gap 12 → 尾部 20 槽（对勾/状态点）→ 右内边距 20 | 我方取值（P4 校准）：20 = sheet gutter；尾部槽固定 20 使选中/未选中/待定三态**行宽不变** |
| 模型列表最小高 | **132**（1 组头 44 + 2 行 44×2） | 我方取值（P4 校准）：保证至少看到"provider + 2 个模型"，否则列表区退化成一条缝 |
| 推理等级小节上限 | **4 行 = 176**（标签 32 + 分隔 8 + 行 44×4 = 216 总高）；>4 档小节内滚 | 我方取值（P4 校准）：myco 常见档位 ≤4（`MA:1977` 写死 off/low/medium/high）；>4 只出现在导入的自定义 profile（`MD:59-62`） |
| 分隔（模型区 ↔ 档位区） | 0.5dp hairline + 上下 8 | 我方取值（P4 校准）：细线 + 8 间距是 `BP` 列表分隔惯例 |
| 底部 | 内容 `padding-bottom 8` + `systemBars` inset（只垫一次） | `BP §1.3`；`BP-30` |
| **胶囊** 命中盒 | **40dp 高**（命中盒 = 布局盒，无扩边）× `44…112` 宽 | G-16「密集图标（操作条）40」+ D-02；G-11（命中盒=布局盒） |
| 胶囊视觉 | 高 40、圆角 20（全圆）、左内边距 8 / 右 4、内部 gap 4、文字 13/20 secondary、chevron 12 | `MSel/…css:9-30`（E0：`gap:4; height:28; padding:0 4px 0 8px; 13/20`）+ `:78-86`（chevron 12、120ms）→ **高度从 28 抬到 40 = P4 实现取值（D-02/G-16 的操作条档）** |
| 胶囊图标（态 C） | 16×16 | `MSel/…css:66-69`（E0：窄容器用图标**替代**文字） |
| 胶囊宽度上限 | **112** | `M/:458-505`（E0：`max-width:112px !important`）；判定见 §1.4 |
| 工具行其余元素的占用（预算用） | 左右 padding 6+6、`+` 40、主键 **48**、gap 6 | `+`/主键取 G-16（密集图标 40 / 主操作 48）；padding 6 + gap 6 取 `M/:458-505`（E0） |
| Toast（失败回滚） | 宽 `min(屏宽−32, 480)`、高 ≥48、内边距 16、圆角 16；底 = `union(navBars, IME) + 8` | `BP §1.2`/toast 行（E0 家族） |

### 1.4 胶囊宽度上限 112dp 的判定（"发送钮不被挤掉"）

**判定式**（在最窄支持宽度 `W_min = 320dp` 上算，`320` 取自 `BL §1.4` 断点表的最窄 COMPACT 屏）：

```
工具行固定占用 = 左 padding 6 + [ + ] 40 + gap 6 + gap 6 + 主键 48 + 右 padding 6 = 112
胶囊可用宽 = W − 112 = 320 − 112 = 208
判据：胶囊可用宽 ≥ 胶囊上限 112  →  208 ≥ 112  ✓（余量 96dp）
等价写法：W ≥ 224 时"112 上限 + 主键 48 完整可见"恒成立；支持范围 320… 全部满足。
```

**主键保护**（比"算得下"更强的一条）：主键 `width: 48; flex: 0 0 auto`，**永不收缩**；胶囊 `flex: 0 1 auto; max-width: 112; min-width: 44`。空间不足时**先降级胶囊**（§1.5），**禁止压主键** —— 反证：`M` 仓库的回归记录就是因为不限宽把发送钮挤出屏幕（`R1/:37`，ref 原文"强制单行…触发器限宽 112px"）。

**112 够不够显示 `模型名 · 档位`？**（文字预算算术）
```
胶囊文字预算 = 112 − 左 8 − gap 4 − chevron 12 − 右 4 = 84
态 A 最小文字 = 模型名 4 汉字(52) + 分隔·(13) + 档位 1 汉字(13) = 78 ≤ 84  ✓
  → 112 下至少能显示「4 字模型名 + 1 字档位」；名字更长时走 ellipsis，档位优先保住
态 B 最小文字 = 模型名 4 汉字 = 52  → 需要 ≥80 宽
态 C（只图标）自然宽 = 8 + 16 + 4 + 12 + 4 = 44
```
> 上方汉字宽度按 13sp 全角 13dp/字计（`BP §1.2` 13/20 字号族）。**上限 112 是"能显示的最小完整信息"，不是"最长可显示"**：`45cqw`（DSH 桌面容器查询，`MSel/…css:17`）在 320dp 上是 144，但它没有移动端 E0 支持，且会让工具行在 320dp 上少 32dp 余量 → 不采用（P2 Q-MODEL-03=A 已选 112）。

### 1.5 胶囊降级顺序（先丢档位 → 再丢模型名 → 只图标）

| 态 | 内容 | 触发条件（按顺序判定，命中即停） | 视觉 |
|---|---|---|---|
| **A 完整** | `模型名 · 档位` + chevron | 文字预算 ≥ 78（≈宽 ≥106，112 上限内成立） | 模型名 13/20 secondary、`·` 与档位 caption 色（`MSel/…css:56-63`） |
| **B 丢档位** | `模型名`（ellipsis）+ chevron | 文字预算 < 78 且 ≥ 52 | 档位信息**不进 UI，只进 `aria-label`**（D-09 无 tooltip） |
| **C 只图标** | 图标 16 + chevron 12，**不显示文字** | 文字预算 < 52（即模型名显示不出 4 个字） | 图标替代文字（`MSel/…css:65-69` E0 手法） |

- 降级由**实测可用宽**驱动（DSH 用 `--dsh-composer-model-icon-display` 变量 + 容器测量，`MSel/…css:66-69,71-76`）；切换**瞬时、无宽度过渡**（否则工具行每帧重排）。
- 态 C 时胶囊宽 = 44（= 命中盒最小宽），仍 ≥44 命中。
- **不许**新增第 4 档（例如"只显示档位"）：模型是主信息、档位是修饰（`MSel/…tsx:416-428` 的 label 拼装顺序也是模型在前）。
- `modelSelectionAllowed=false`（非用户直控会话）→ **胶囊整颗不渲染**（不是灰态），见 §2.6 禁用条件表。

---

## 2. 状态矩阵与行为契约

### 2.1 状态矩阵（必须覆盖的 9 类全在表内）

| # | 状态 | 进入条件 | 布局变化 | 视觉 | 可交互项 | 退出 |
|---|---|---|---|---|---|---|
| S1 | **空**（无可用模型） | 目录加载成功且 `groups` 为空（无 provider，或 provider 全停用/无模型） | sheet = 空态块（图标+标题+说明+按钮），**不渲染搜索行/模型区/档位小节** | 标题 16/24、说明 13/20、按钮 48 | 关闭（✕/遮罩/返回/下滑）；「去设置」48 | 点「去设置」→ 先关 sheet 再 push `settings.models`；返回不自动重开 |
| S2 | **加载中** | 打开 sheet 时目录尚未加载 / 点重试后 | 搜索行不渲染（无模型可比）；列表区 = 3 行骨架 44 或一行「正在加载模型…」 | 骨架呼吸 / caption 文字 | 关闭；骨架不可点 | 加载回执到达（成功→S3/S1；失败→S5） |
| S3 | **正常** | 目录就绪、至少 1 个模型 | 见 §1.2-B | token | 全部（行 44/组头 44/关闭/搜索条件） | 选中 → S7；关闭 → S0（关） |
| S4 | **长内容** | 模型总数 ≥5（出搜索）或 ≥15（必内滚）/ 档位 ≥5 | 搜索行出现；列表 = 唯一内滚容器（高被 `0.8×窗口` 压缩，最小 132）；档位小节 >4 档 → 小节内滚 | 组头吸顶带不透明底；滚动条 overlay 不占宽 | 内滚、组头折叠、搜索、行；到边按 G-04 吸收 | 同 S3 |
| S5 | **失败（整体）** | 目录加载返回错误 | 搜索行不渲染；列表区 = 错误条（高 `max(36, 文字+16)`）+ 「重试」；档位小节不渲染 | 红底红字（error token）；`role=alert` 播报一次 | 关闭；「重试」44 | 重试 → S2 → S3/S5；关闭 → S0 |
| S6 | **部分失败** | 至少 1 个 provider 条目加载失败，但仍有可用模型 | 警告条在列表上方 inline（挤压列表高度，列表仍可滚） | warn 底 + warn 字 12/18，列出 provider 名 | 关闭；「重试」44（重跑全部）；列表可滚可选 | 同 S3 |
| S7 | **提交中（pending）** | 已发出 `SessionSetModel` 且未回执 | sheet 内：命中的行尾部 = 状态点 8，**其它行 disabled**；胶囊尾部 = 状态点（替 chevron，占 12 槽不变宽） | 状态点脉冲；`aria-busy`；设置页无变化 | 关闭（可随时关，不取消提交）；**行点击无效**；胶囊仍可点（重开 sheet 看 pending） | 回执：成功 → S8；失败 → S9；>5s 无回执 → 按失败处理（S9） |
| S8 | **提交成功** | `SessionSetModel` 返回 Ok | sheet 关闭；胶囊文案更新为新值 | 无 Toast；胶囊获得焦点**但抑制一次焦点环** | 胶囊 | 稳定态 |
| S9 | **提交失败** | Err / 超时 | **强制关闭 sheet**（z 表：Toast 20 < sheet 1000，不关就看不见）→ 胶囊回滚到旧值 | 胶囊回旧文案；Toast 3s：「切换失败：<原因>」 | Toast 不接点击；胶囊 | Toast 过期 / 下一次操作 |
| S10 | **禁用·会话不可用** | `modelSeatLocked = removed ‖ inert ‖ !live`（会话已删/未就绪/父端离线） | 不变 | 胶囊文字 dimmed、无 chevron 旋转 | 胶囊 disabled（点了零行为）；**不渲染** sheet | live 恢复 → 可点 |
| S11 | **禁用·非用户直控会话** | `subagentAddress(session) !== undefined`（被委派的子代理会话） | **胶囊整颗不渲染**（工具行不占位） | — | 无 | 会话切回用户直控 |
| S12 | **深色** | 系统暗色 | 同 S3 | token：表面 `surfaceContainer`、遮罩仍 0.32、文字/对勾用语义色，无硬编码色值 | 同 S3 | 切回亮色 |
| S13 | **大字号（fontScale 2.0）** | 系统字号 2.0 | 行高 = `max(44, 文字+20)`（**允许超过 44**）；标题单行 ellipsis；胶囊高仍 40（文字 ellipsis 更早，可能直接落到态 B/C） | 对勾/状态点/图标**不随字号放大** | 同 S3 | 回 1.0 |
| S14 | **横屏（高 <480dp）** | 窗口高 <480（D-04 强制单栏） | sheet 高 = `0.8×窗口高`（412 高 → 330）；**降级为"单一滚动容器"**：标题+搜索固定，模型区 + 档位小节都进同一内滚（避免两个钉子区放不下，算术见 §5 `P-MODEL-01`） | 同 S3 | 同 S3（内滚一个而不是两个） | 竖屏恢复双区 |
| S15 | **宽屏（≥600dp）** | 断点 ≥600 | sheet 宽 640 居中（高度规则不变） | 同 S3 | 同 S3 | 变窄 |
| S16 | **当前模型不可用** | `Query.SessionModel` → `providerAvailable=false` 或 `modelAvailable=false` | 胶囊不变宽；sheet 该行 `disabled` + 原因 | 胶囊文字 error 色 + 尾部 6dp error 点（替 chevron）；`aria-label` 加「当前模型不可用」 | 胶囊**必须保持可点**（这是唯一修复入口）；sheet 内容见 S3 | 改选成功 → 回 S3 |

> **不适用说明**：S2 的"搜索行"在无模型时不存在（无内容可比）→ 不适用；S5/S1 的"档位小节"不渲染（无当前模型可谈档位）→ 不适用；S12/S13/S14/S15 的"进入条件"互不排斥，可叠加。

### 2.2 唯一真源：档位 ↔ 发送参数映射表（**本面板最重要的表**）

**真源定义**：`session.modelSelection = { providerId, modelId, reasoningLevel }`，存在会话 sidecar（`SS:256-259`），**只有 `Command.SessionSetModel` 一处写**（`IB:190-201`）。UI 层**不允许**再有任何独立的"思考开关/布尔"状态（D-12=A；BUG-003 的根因就是 `ChatSend.thinking` 与 `reasoningLevel` 两条独立写点，`MA:962-968` + `IB:478-495`）。

**myco 后端的档位语义（E0，逐行）**：
```
IB:479-483   level = ChatSend.reasoningLevel ?: sidecar.reasoningLevel(若该 key 在新模型 efforts 里) ?: model.reasoning.default
IB:484-489   apiValue = efforts[level]；level 不在 efforts → Err(REASONING_UNSUPPORTED)
IB:490-494   thinking = level==null ? ChatSend.thinking : (apiValue.isNullOrBlank() ? false : true)
IB:495       reasoningEffort = apiValue?.takeIf { it.isNotBlank() }
OA:183-190   DeepSeek 或 thinking!=null → 发 "thinking":{type:"enabled"|"disabled"}；reasoningEffort!=null 且 compat.supportsReasoningEffort!=false → 发 "reasoning_effort"
```

> **线协议的一个易错点**：`thinking` 字段是否发出，判据是 `deepseek || ChatRequest.thinking != null`（`OA:185`）。所以：#1/#2 两档**永远**会发 `thinking`（因为后端已把它推导成 false/true）；#3/#4 两档在非 DeepSeek provider 上**不发**（`thinking==null`）、在 DeepSeek 上发 `disabled`。→ "不发任何思考相关字段"只发生在 #3/#4 + 非 DeepSeek。

**映射表（唯一实现口径；UI 只做"选档"，其余全由这张表决定）**

| # | 用户在 sheet 里选的 | 判据（怎么认出这一档） | `SessionSetModel.reasoningLevel`（写） | `ChatSend.thinking`（UI 恒不传） | `ChatSend.reasoningLevel`（发送透传） | 后端产出（E0） | provider 线字段（E0） |
|---|---|---|---|---|---|---|---|
| 1 | **`off` 档**（"关闭思考"） | 该档的 `apiValue` **为空（null 或 ""）** —— 用 `IB:492` 的判据认，**不是**按 key 名字认 | 该档 **key**（例如 `"off"`；显式写，**绝不清空**） | `null` | 同 sidecar 值 | level 命中 → `thinking=false`；`reasoningEffort=null` | `thinking:{type:"disabled"}`；**不发** `reasoning_effort` |
| 2 | **具体档**（低/中/高/自定义） | `apiValue` 非空 | 该档 **key** | `null` | 同 sidecar 值 | `thinking=true`；`reasoningEffort=apiValue` | `thinking:{type:"enabled"}`（**任何 provider 都会发**，因 `ChatRequest.thinking==true`，`OA:185-187`）+ `reasoning_effort:<apiValue>`（`compat.supportsReasoningEffort!=false` 时，`OA:188-190`） |
| 3 | **`Default`**（模型**没有 off 档**时才有这行） | 当前模型 `efforts` 里**没有任何空 apiValue 的档** | **显式清空**（`null`） | `null` | `null` | level = sidecar(已清) ?: `model.reasoning.default`；若两者皆空 → `thinking=null`、`reasoningEffort=null` | DeepSeek：`thinking:{disabled}`（`OA:186` 对 null 取 disabled）；其它 provider：两个字段都不发 → **由 provider 默认决定** |
| 4 | **无 reasoning 的模型**（不显示档位小节） | `model.reasoning == null` | `null` | `null` | `null` | level=null → `thinking=null` | 同 #3 的"不指定" |

**为什么 `off` 必须显式传 key 而不是"什么都不传"**（P4 最容易写错的一处）：
> 不传 `reasoningLevel` 时，后端会回落到 `sidecar.reasoningLevel`，再回落到 `model.reasoning.default`（`IB:479-483`）。若该模型的默认档是 `medium`，那么"省略 = 使用默认档 = 仍然思考"——这正是旧 BUG-003"关掉开关还在思考"的第二条复现路径。**只有显式写 `off` 的 key 才能让 `IB:484-494` 走进 `apiValue.isNullOrBlank() → thinking=false`**。已有核心单测就是这个口径：`IBT:629-631`（`ChatSend(reasoningLevel="off")` → `thinking=false`、`reasoningEffort=null`）。

**为什么 `Default` 要"清空 sidecar"而不是"发送时省略"**：发送时省略仍会读 sidecar（上一条）。必须 `SessionSetModel(..., reasoningLevel=null)` 把 sidecar 清掉，发送时才真正落到 `model.reasoning.default` 或 provider 默认。

**`Default` 的文案必须诚实**（D-12 已接受"模型无 off 档时无法彻底关闭"这个代价）：
- 行文案：`Default`，副文案 `跟随模型默认档`（若 `model.reasoning.default != null`）或 `由服务端决定`（若 default 也为空）；
- **不许**写成「不思考/关闭」（模型没有 off 档，写了就是假承诺）。

**档位显示名来源**（E0 事实 + 落法）：
- myco schema `efforts: Map<String, String?>` **只有 key 和 apiValue，没有显示名**（`MD:59-62`）；
- 落法（三级 fallback，全部可实现、无 E2）：① 若未来 catalog 下发 `name` → 用它；② 否则内置**显示词表**（`off/none → 关闭`、`minimal → 最小`、`low → 低`、`medium → 中`、`high → 高`、`xhigh → 极高`、`max → 最高`）；③ 未知 key → **原样显示 key**（不猜译名）。
- 证据：dsh-Go 就是这么做的（`go/model_sheet.dart:52-58` 用 key→本地化标签），且它的 off 档就显示为「不思考」——**这条同时证明"off 档显示为关闭语义"是参考实现的既有做法**。
- 注意：**该词表只用于显示**，不参与写库；provider 编辑器里"不可写死档位表"的结论（P2 `C-M5-08`，反证 `MA:1977`）不受影响。

### 2.3 提交语义（选中即提交、无确认键、同值只关、失败回滚）

| 步骤 | 契约 | 依据 |
|---|---|---|
| 触发 | 点模型行 / 点档位行 = **立即提交**。**没有"确定/保存"按钮**，没有二次确认 | P2 §2 `model.menu.root` 建议③（移动端 3/3 + DSH 一致）；G-12（纯单选页 = 即时写） |
| 写什么 | **一条** `Command.SessionSetModel(sessionId, providerId, modelId, reasoningLevel)`；档位按 §2.2 表 | `BR:32-37`、`IB:190-201` |
| 同值 | 该行已是当前值 → **只关 sheet，不发命令、不 pending、无 Toast** | `MSel/…tsx:394-414`（`choose`/`chooseEffort` 先比再提交，E0） |
| pending | 乐观：胶囊立刻显示目标值 + 状态点；目标行状态点；其它行 disabled；sheet **保持打开**直到回执 | `MSel/…tsx:386-400,593-596`（E0：提交后 trigger 聚焦、行显示 StateDot、成功才 `closeAfterSelection`） |
| 成功 | 关 sheet → 回焦胶囊（**抑制一次焦点环**）→ 无 Toast | `MSel/…tsx:370-375`（ok → closeAfterSelection）、`:36-38,458`（`data-selection-focus` 抑制焦点环） |
| 失败 | **先强制关 sheet**，再回滚胶囊到旧值，再 Toast 3s：「切换失败：<原因>」 | 推导：Toast 在 z20、sheet 在 z1000（`BL §1.6`）→ sheet 开着时 Toast 不可见；且 BL-50 反证"不许把 Toast 抬到面板上"。DSH 的失败文案分支 `MSel/…tsx:376-383` 是 `session/writer-held` 专属文案，**myco 无该错误码**（`IB:190-201` 只有 `SESSION_MODEL`），故用原因文本 |
| 超时 | 发出后 **5s** 无回执 → 按失败处理（同失败分支，文案「切换超时」） | **我方取值（P4 校准）**：`SessionSetModel` 是同进程 IPC（`IB:190`），正常 <50ms；5s 是"用户会认为没反应"的上限 |
| 可撤销 | **不可撤销**（选中即写）；恢复旧值只能再选一次。**因此**：不得在 sheet 里再放"撤销/恢复"按钮（会构成第二个写点） | D-12 的立意；G-12 |
| 关闭与提交并发 | pending 中用户可关闭 sheet（**不取消**在飞提交，IPC 无法取消）；关闭后胶囊一直 pending 到回执 | 推导：`IB:190` 无取消语义 |
| 提交中再次点行 | 无效（行 disabled）。胶囊仍可点开 sheet 查看 pending 点 | `MSel/…tsx`（pending 时行 `disabled`，trigger 无 `disabled`，E0） |

### 2.4 切换影响面

| 影响面 | 结论 | 依据 |
|---|---|---|
| **对已发消息/已完成轮次** | **不改写**。每轮在 `runChatInternal` 开头一次性解析出 `provider/model/thinking/reasoningEffort` 并构造 `ChatRequest`（`IB:459-495,585-593`），解析结果只属于该轮；历史事件是 append-only（`SS`/`IB:824-827` 只读） | E0 `IB:459-495,585-593`；`IB:824-827` |
| **进行中的响应（流式中）** | **允许切**，且当前轮**不受影响**（`ChatRequest` 已构造，`thinking/reasoningEffort` 是局部 val）。切换只写 sidecar → **下一轮生效**。胶囊在流式中**保持可点**（不是禁用） | E0 `IBar.tsx:136`（"Running input stays free"）+ `IBar.tsx:140-150`（`modelSeatLocked` 不含 running/blocked）；`IB:585-593` |
| **作用域** | **会话级**。写 sidecar（`SS:256-259`）；不写全局 prefs。旧 myco 的 `thinking` 存全局 SharedPreferences（`MA:741-743`）是缺陷 L3，**随 D-12 一并消失** | E0 `SS:256-259`、`BR:31-32`（注释"会话级模型选择，不写全局"） |
| **持久化** | sidecar 落盘 → 杀进程/重进会话仍在；`Query.SessionModel` 可读（`IB:829-843`，连同 `providerName/modelName/available` 一起返回） | E0 `IB:829-843`、`SS:252-259` |
| **新会话** | 新会话没有 sidecar → 走 `model.reasoning.default`（`IB:483`）与 provider 的 chat 角色模型（`IB:470-477`）；**不从别的会话继承**（旧 L3 的反面） | E0 `IB:470-483` |
| **切模型时档位怎么办** | **重置为新模型的 `reasoning.default`**；新模型无 `default` → 重置为 `Default`（清空）；新模型无 reasoning → 清空 | P2 §2 `model-row` 采纳（DSH `directory.ts:157-194`，E0）；**但 dsh-Go 实际是"旧档若新模型支持则保留"**（`go/model_sheet.dart:459-465`）→ 冲突登记 §5 `P-MODEL-03`（非阻塞；本文件按 P2/DSH 写） |
| **对正在编辑的草稿** | 无影响（草稿归 composer；sheet 盖住但不清空） | D-13 代价；E-02 |

### 2.5 与 `settings.models` 的边界（两个面板不得同字段可写）

| 字段 | `model` 面板（本文件） | `settings.models` | 判定 |
|---|---|---|---|
| 会话当前模型 / 档位 | **读写**（唯一写入面 `SessionSetModel`） | **不可写**（设置页不显示、不改会话选择） | 互斥 ✓ |
| provider 列表/增删/启停 | 只读（`providers.load()`） | 读写 | 互斥 ✓ |
| 模型的 `contextWindow` / `maxTokens` | **不出现**（C-08；3/3 参考一致：`dsh-settings.md §5`、`dsh-go §2.10`、`taixu §2.8`） | 读写 | 互斥 ✓ |
| 模型的 `reasoning.efforts` 表 / `reasoning.default` | **只读**（面板只选档，不改档表） | 读写（provider 编辑器） | 互斥 ✓ |
| 思考状态（布尔） | **不存在**（D-12 删除） | **不存在** | 双删 = BUG-003 根除 ✓ |

- 唯一的"看起来重叠"处是 `reasoning.default`：settings 写的是**新会话的默认档**，面板写的是**本会话已选档**；两者是不同字段（`MD:59-62` vs `SS:20-26`），**不得互相回写**。表现规则：改 settings 的 default → 已开会话的选择不变（sidecar 已存在）；新会话才用新默认。
- 面板里**不放**"管理模型/去设置"以外的设置入口；空态按钮是唯一跨面板跳转（§3.9）。

### 2.6 禁用条件三态（题设指定：子代理会话 / 锁定 / 目录加载失败）

| 态 | 进入条件（真源） | 胶囊 | sheet | 依据 |
|---|---|---|---|---|
| **非用户直控会话（子代理）** | `subagentAddress(sessionId) !== undefined` | **整颗不渲染**（工具行不留位）；不做灰态 | 不可达（点了也没有胶囊） | E0 `MSel/index.ts:177-186`（`available=false`，`select` 直接返回 undefined）、`MSel/…tsx:242`（`available` 才渲染） |
| **锁定** | `modelSeatLocked = removed ‖ inert ‖ !live`（会话被删/未就绪/父端离线） | `disabled`：文字 dimmed、点了零行为、`aria-disabled`；**不是**灰到看不见（要让用户知道"这里本该有"） | 不可达 | E0 `IBar.tsx:140-150`（`modelSeatLocked = removed ‖ inert ‖ !live`） |
| **目录加载失败** | `providers.load()` 返回 Err | **仍显示当前选择**（不隐藏、不换文案），可点 | 打开后 = S5（错误条+重试）或空态（若已无 provider） | E0 `MSel/…tsx`（DSH 失败后 trigger 仍显示当前选择，ref `dsh-settings.md:990` E1）；`go/model_sheet.dart:501-519`（打开后失败态 + 重试） |

> **两条来自 E0 的"反向"规则（P4 最容易做错）**：
> 1. **`blocked` 不锁模型座位**：`IBar.tsx:140-150` 的 `locked` 含 `blocked`，但模型座位用的是 `modelSeatLocked`（不含 `blocked`），注释原文："The model seat is the ONE control a block leaves live: every block this contract has is cleared by choosing a model"。→ 缺 provider/缺模型/发送被阻塞时，**胶囊必须保持可点**（这正是 L7 的修复路径）。
> 2. **流式中不锁**：`IBar.tsx:136` "Running input stays free"。→ 进行中也能切，只影响下一轮（§2.4）。

---

## 3. 节点规格（13 字段）

> 节点 11 个。每个节点字段 12 只写 E0/E1/user，字段 13 必须带反证 + ≥1 个 `MODEL-` 验收 ID。
> 与 P2 节点表的对应：`entry.trigger`→3.1；`entry.unavailable`→3.2；`menu.root` **删除**（D-13=B 单页，由 3.3 `sheet` 取代）；`menu.model-row`→3.6；`menu.effort-row`→3.7；`menu.search`→3.4；`menu.load-retry`→3.8（并入空态 3.9）；`menu.dismiss`→3.10；`submit.feedback`→3.11；`thinking-switch` **删除**（D-12）；`preset.dimension`/`context.dimension` **不渲染**；`command-popup` **首版不做**。新增：3.3 `sheet`、3.5 `model-group-header`、3.9 `empty`。

### 3.1 `model.entry.trigger` — 输入区模型胶囊（可点，唯一的入口）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.entry.trigger`。所属：`composer` 工具行的"模型座位"（座位归 composer，本节点只定内容与打开行为，P2 §6.2-5）。出现条件：会话 `live && !removed` 且 `subagentAddress == undefined`；**每会话 1 个**。目录未加载也渲染（显示「正在加载模型…」）；目录失败也渲染（显示当前选择）。 |
| 2 | 网格与定位 | 工具行内单行；布局盒 **40dp 高 × 44…112dp 宽**（§1.3）；圆角 20、左内边距 8 / 右 4、内部 gap 4；内容顺序 =〔(图标 16，仅态 C)〕→ 文字列（13/20，单行 ellipsis）→ chevron 12。与 `+`/主键间距 ≥6（工具行 gap 6）。 |
| 3 | 命中区 | 命中盒 = 布局盒：**40dp 高 × 44…112dp 宽**，无扩边（G-11「命中盒=布局盒」）。F=40 取 G-16「密集图标（操作条）40」，D-02 分级表定稿。与相邻命中盒不相交（工具行 gap 6 + 左 padding 6）。 |
| 4 | 手势 | 单击 = 打开 sheet（sheet 是独立 window，打开后本胶囊不可达，不存在"再点关"）。无长按/双击/滑动；竖直拖动不被消费（D-09 只触摸，不给 hover/键位）。 |
| 5 | 行为 | 打开 `model.sheet`（同一 `session.modelSelection` 状态）。打开**不改变任何值**；关闭后回焦本胶囊（抑制一次焦点环）。幂等。pending 时仍可点开（看 pending 点）。**不发任何命令**。 |
| 6 | 视觉状态 | default：文字 secondary、底透明；pressed：背景 10% hover/pressed token（**不做整颗 scale**）；pending：尾部 12dp 槽放 8dp 状态点（替 chevron，占位不变宽）；disabled（S10）：文字 dimmed、无 pressed；unavailable（S16）：文字 error + 尾部 6dp error 点；loading：文字「正在加载模型…」。**无 hover/focus 环**（D-09；回焦靠 `suppressFocusRingOnce`）。 |
| 7 | 布局占位与展开形态 | 在工具行布局流内、恒 40dp 高；sheet 是形态 C 浮层（G-01 不占位）。三个降级态只改宽度、**不改高度**（否则工具行抖）。 |
| 8 | 滚动语义 | 不滚（工具行 contract：单行 nowrap，由 composer 定）；文字 ellipsis。 |
| 9 | 动效 | chevron 旋转 120ms（`MSel/…css:81`）；pending 点脉冲；降级切换**瞬时**（无宽度动画）；`prefers-reduced-motion`/系统「移除动画」→ 全部直切。 |
| 10 | 边界态 | 空目录/无 provider：显示「选择模型」，点开 = S1 空态。加载中：「正在加载模型…」。加载失败：显示当前选择（不换文案）。当前模型不可用：见 S16/§3.2。模型名超长：ellipsis（先丢档位 → 再丢名 → 只图标，§1.5）。档位为 `Default`：显示「Default」。大字号 2.0：高仍 40，文字 ellipsis 更早 → 更早落态 B/C。横屏/宽屏：`max-width 112` 不放宽。深色：token。 |
| 11 | 无障碍 | `role=button`、`aria-haspopup="dialog"`、`aria-expanded`、`aria-busy`（pending）；`aria-label` 三式（照 `MSel/…tsx:422-428` E0）：加载中 →「正在加载模型」；无选择 →「选择模型」；有选择 →「选择模型，当前 <模型>，推理等级 <档>」；S16 追加「当前模型不可用」。不做键盘快捷键（D-09）。 |
| 12 | 依据 | **E0** `MSel/…css:9-30`（28 高/13/20/padding 0 4 0 8/gap 4）、`:45-50`（label ellipsis）、`:56-63`（档位 `flex-shrink:1000` 先丢）、`:65-69`（图标替代文字）、`:78-86`（chevron 12/120ms）；**E0** `MSel/…tsx:416-428`（label 拼接 + aria 三式）、`:437-475`（trigger 渲染）、`:459-460`（`disabled={locked}`）；**E0** `MSel/index.ts:175-190`（座位与 `available`）；**E0** `IBar.tsx:136-150`（`modelSeatLocked` 不含 blocked/running）；**E0** `M/:458-505`（工具行 nowrap / 触发器 `max-width:112` / `min-height:44` / gap 6）、`R1/:37`（不限宽把发送钮挤出屏幕的回归记录）；**user** D-12（胶囊不绑 thinking）、D-02/G-16（40 档）、D-09（只触摸）、D-13（关后回焦）；**P2** §2 `entry.trigger`（采用 DSH 形态、拒绝纯图标钮）；**我方取值（P4 校准）**：40dp 高、112dp 上限（P2 建议）、降级阈值 106/80/52dp（§1.4 算术）。 |
| 13 | 反证 + 验收 | **反证**：① 不显示档位 → 用户改了思考档后在输入区看不到，只能反复点开确认（DSH 反证原文）；② 胶囊绑定「深度思考」底色（旧 M5 `MC:127`）→ 一颗胶囊两个语义，是 BUG-003 的土壤（D-12 明确删）；③ 不设上限 → 320dp 上把主键挤出屏幕（`R1/:37` 实证）；④ 做成纯图标钮（太墟）→ 用户永远看不到当前模型与档位（旧 myco 的"模型 · label"行存在本身即反证）；⑤ 流式中禁用胶囊 → 与 "Running input stays free" 冲突，用户想换模型必须等整轮结束。**验收**：`MODEL-01`（文案格式）、`MODEL-02`（112 上限 + 320dp 主键完整可见）、`MODEL-03`（三态降级）、`MODEL-04`（40dp 命中盒、不相交）、`MODEL-05`（aria 三式 + busy）、`MODEL-06`（子代理会话不渲染）、`MODEL-07`（流式中可点、本轮不变）、`MODEL-08`（不可用态）、`MODEL-09`（加载中/失败文案）。 |

### 3.2 `model.entry.unavailable` — 胶囊的"当前模型不可用"态（可点：唯一修复入口）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.entry.unavailable`。是 3.1 的一个**视觉态 + 点击语义**，不是独立控件；出现条件：`Query.SessionModel` 返回 `providerAvailable=false` 或 `modelAvailable=false`（`IB:829-843` 的字段**必须被消费** —— 旧 myco 的 L7 缺陷就是不消费）。每次 1 个，与胶囊同位置。 |
| 2 | 网格与定位 | 与 3.1 完全同盒（40dp × 44…112dp），**不新增元素、不改宽**：尾部 chevron 12 槽换成 6dp error 圆点（居中），文字沿用 error 色。 |
| 3 | 命中区 | 同 3.1（40dp 高 × 44…112dp）。 |
| 4 | 手势 | 单击 = **打开 sheet**（让用户改选）。**不做**"点击重试发送"（本面板不发消息）。 |
| 5 | 行为 | 打开 sheet 时：该已失效模型**不出现**在列表里（或出现但 disabled + 原因）；无可用模型 → S1 空态；**不弹 Toast**（每次进会话都弹 = 骚扰）。不自动改选、不自动修复。 |
| 6 | 视觉状态 | default：文字 error + 尾部 6dp error 点（无 chevron）；pressed：error 底 10%；pending/disabled 同 3.1。 |
| 7 | 布局占位与展开形态 | 同 3.1（高不变宽不变）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无（进入会话即呈现；从可用变不可用时 ≤120ms 淡入 error 色）。 |
| 10 | 边界态 | provider 停用 vs 模型被删：胶囊文案统一「<模型名>（不可用）」（视觉简洁）；**原因在 sheet 行尾**显示（「provider 已停用」/「模型已删除」）。大字号/横屏/深色：同 3.1。 |
| 11 | 无障碍 | 在 3.1 的 label 末尾追加「，当前模型不可用」；不额外加 role。 |
| 12 | 依据 | **E0** `IB:829-843`（`providerName/providerAvailable/modelName/modelAvailable` 四字段已存在）；**E0** `IB:465,473`（发送时才报 `PROVIDER_MISSING`/`MODEL_MISSING`）；**缺陷** 旧 myco L7（`legacy-myco.md §5 L7`、`IB:835-842` vs `MA:864-877`：后端返了字段、UI 只取 3 个）；**P2** §5 新增节点（唯一新节点）；DSH 无此态（只有失败行不可选）→ **我方新增**；**我方取值（P4 校准）**：胶囊只标"不可用"不标原因（原因进 sheet）。 |
| 13 | 反证 + 验收 | **反证**：不消费这两个字段 → 用户看到旧模型名、写完一屏字才被告知 `PROVIDER_MISSING`（L7 原话），且此前无任何提示；进入会话即弹 Toast → 每次切会话都骚扰；隐藏胶囊 → 用户失去唯一修复入口（连"哪里改"都不知道）；把不可用模型从列表里静默删掉 → 用户以为"模型不见了"。**验收**：`MODEL-08`（删 provider 后胶囊 ≤1 帧标不可用、点开仍可选其它）、`MODEL-41`（不可用行 disabled + 行尾原因、点击零行为）、`MODEL-09`（加载中不误报不可用）。 |

### 3.3 `model.sheet` — 底部 sheet 容器（形态 C：打开/滚动/insets/关闭的全部契约）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.sheet`。打开者 = 3.1 胶囊；同时最多 1 个（浮层家族互斥，`BL §1.6`）。出现条件：胶囊可点且未被禁用。**不在导航栈内**（不 push `PanelRoute`）。每次打开重置：搜索 query 清空、分组折叠态全部展开、列表滚到当前选中行（已可见则不动）。 |
| 2 | 网格与定位 | 独立 window；宽 = 全宽（COMPACT）/ 640 居中（≥600dp）；高 = `min(内容高, 0.8×窗口高)`；上圆角 16；水平 gutter 20；结构（自上而下）：把手区（8+4+4+40 命中）→ 标题行 56 → 搜索行 44（条件，见 3.4）→ 模型分组区（唯一内滚，`weight(1)`，最小 132）→ 分隔 8+0.5 → 档位小节（标签 32 + 行 44×N，N≤4）→ 底部 8 + `systemBars` inset（只垫一次）。 |
| 3 | 命中区 | ①遮罩整层（关闭判据见字段 4）；②把手命中 96×44；③关闭钮 44×44（标题行右端）；④列表行/档位行 44（各自节点给）；⑤「重试」44。彼此不相交（把手居中、关闭钮在标题行右缘，中间是标题文字）。 |
| 4 | 手势 | ①点遮罩 = 关（**按下与松手都在遮罩上**，D-08/G-18）；②返回键/侧滑 = 关（dispatcher 优先级 IME→sheet→route）；③**下滑 = 关，仅当"触点所在的内滚容器在顶"**（G-13）；内滚不在顶 → 下滑归内滚、到边**吸收**不外传（G-04）；④把手下拉 = 关（跟手，松手交末速度）；⑤拖动阈值/提交阈值按 D-07（22% 屏宽 或 600dp/s，spring 0.86/420）。 |
| 5 | 行为 | 打开：测量 → 滑入；**不改变任何数据**。关闭：滑出 → 回焦胶囊（3.10）。push 其它 route 前必须先关（`BL-27`：浮层不得跨层存活，空态「去设置」尤其注意）。不取消在飞提交。幂等。 |
| 6 | 视觉状态 | 遮罩 0.32（D-05）；表面 `surfaceContainer` + 上圆角 16；拖动时遮罩 alpha 跟手；标题 16/24；关闭钮 pressed 态；无 hover（D-09）。 |
| 7 | 布局占位与展开形态 | **形态 C（sheet-overlay）**：完全不占位；下层 chat/composer 保活（滚动位置、草稿、流式全保留），吞指针 + 清语义（`BL-42/43`）。**同一时刻最多 1 个纵向内滚容器**（S3：模型列表；S14 矮屏：整个内容区一个）。 |
| 8 | 滚动语义 | 常态：模型分组区是唯一内滚；组头在**该容器内**吸顶（不是 sheet 顶）。档位小节 ≤4 行不滚，>4 行自身内滚（上限 176）。到边一律按 G-04 吸收；不把残余速度传给遮罩/下层。 |
| 9 | 动效 | 进出 = spring `dampingRatio 0.86 / stiffness 420`（D-07，禁 tween）；**不做高度动画**（G-10：浮层出现消失一律无高度动画）；内容区结构变化（折叠/搜索出没）瞬时 + ≤120ms 不透明度；`reduced-motion` → 直切。 |
| 10 | 边界态 | **空**：S1。**加载**：S2。**长内容**：S4（列表内滚、组头吸顶）。**失败**：S5/S6。**禁用**：S10/S11（胶囊侧）。**深色**：token、遮罩仍 0.32。**大字号 2.0**：行高 `max(44, 文字+20)`，标题单行 ellipsis，sheet 高仍 `0.8×窗口`。**横屏 412dp 高**：S14（`0.8×412≈330`；标题 56+搜索 44+档位 216+inset ≈ 320 已吃掉全部高度 → 降级为单滚动容器）。**宽屏**：640 居中。**IME**：搜索聚焦时 sheet 高 = `min(0.8×(窗口高−IME), 内容高)`，搜索行保持可见。**离线**：同 S5。 |
| 11 | 无障碍 | 独立 window + `paneTitle = "模型与推理等级"`；焦点陷阱（平台能力，**E1**，P4 TalkBack 验）；关闭后**必须回焦胶囊**（平台只保证陷阱，回焦要自己写，`BP §1.3`）；把手 `contentDescription="拖动关闭"`；关闭钮 `contentDescription="关闭"`。 |
| 12 | 依据 | **user** D-13=B（底部 sheet，必须补返回键先关/回焦/insets）、D-03、D-05、D-07、D-08、G-13、G-01、G-10、D-04；**E0** `BP §3.11`（sheet 原子：max 0.8×高、gutter 20、把手 36×4/命中 96×44、标题 56、insets 只垫一次、返回键关、回焦必做、关闭集合表）；**E0** `BL §1.6`（z 1000 / Toast 20）、`BL-23`（返回只关 sheet）、`BL-27`（push 前关浮层）；**E0** `go/model_sheet.dart:396-576`（showAppSheet 单页：标题栏 + body 单 ScrollView + 底部链接 / 空态 / 失败态）；**E0** `go/app_sheet.dart:25-58`（gutter 20、把手、insets）；**E1** 矮屏单滚动降级与 640 宽（**我方取值（P4 校准）**，见 §5 `P-MODEL-01`）。 |
| 13 | 反证 + 验收 | **反证**：① 返回键不先关 sheet → 一次返回退出会话，用户丢掉"我只是想改模型"的意图（P2 反证原话）；② 下滑在任意位置可关 → 用户想滚列表却把面板滑没了（G-13 反证）；③ 两个内滚并列 → 一次手势两层都能滚，归属不确定（G-04 反证"整个页面会乱滑"）；④ 底部不垫 inset → 手势条压住最后一行/最后一行点不中（`BP-30`）；⑤ 打开时 push `settings` 不先关 → 浮层滞留被 route 覆盖，返回时先关一个看不见的东西（`BL-27` 反证）。**验收**：`MODEL-10`（返回只关 sheet）、`MODEL-11`（遮罩按下+松手才关）、`MODEL-12`（内滚在顶才下滑关）、`MODEL-13`（回焦胶囊 + 成功后无焦点环）、`MODEL-14`（inset 只垫一次）、`MODEL-15`（IME 下搜索行可见）、`MODEL-16`（≤0.8 屏、内滚、标题/搜索固定）、`MODEL-42`（深色/大字号/横屏矩阵）、`MODEL-43`（只触摸）。 |

### 3.4 `model.menu.search` — 条件搜索（>4 个模型才出现）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.menu.search`。出现条件：**全部模型总数 > 4**（不是过滤后）；每张 sheet 1 个；在标题行下、列表上方**固定不滚**；清除钮仅 `query` 非空时出现。 |
| 2 | 网格与定位 | 行 44（输入框 44 高、圆角 12、左右 12）；leading 搜索图标 16；文字 14/20；query 非空时 `padding-right: 48` + 行尾清除钮 44×44；placeholder「搜索模型…」。 |
| 3 | 命中区 | 输入框整行 44 可点；清除钮 44×44；两者不相交（输入右 padding 48 ≥ 44+4）。 |
| 4 | 手势 | 点输入框 = 聚焦 + 弹 IME；逐键本地过滤（无防抖：模型数 × 名字长度 <1000 次比较，同帧完成）；点清除 = 清空 + 焦点回输入框（IME 保持）；IME 的「完成」= 收键盘（不提交、不选行）。**无 ↑↓ 虚拟高亮/Enter 接受**（D-09=A 只触摸，见 §5 `P-MODEL-02`）。 |
| 5 | 行为 | 过滤 = 对模型名与 id 做**大小写不敏感的有序子序列匹配**；排序 = 前缀命中 > 词边界对齐 > 目录序（照 `MSel/…tsx:107-109` 的 `rankByName`）；**按 provider 分组内过滤，过滤后空组整组隐藏（组头也不渲染）**；query 空 → 恢复全量；无匹配 → 列表区空态「没有匹配的模型」（`role=status`，播报一次）。 |
| 6 | 视觉状态 | default 透明底 + 1dp 边；focus 边 primary；placeholder caption 色；清除钮 pressed 背景；无 hover（D-09）。 |
| 7 | 布局占位与展开形态 | 固定行、不随列表滚；列表内滚区高 = sheet 高 − 上方固定区 − 档位小节；无高度动画（G-10，出现/消失瞬时或 ≤120ms 不透明）。 |
| 8 | 滚动语义 | 不滚（单行，超长输入横向内滚 = 系统输入框行为）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | ≤4 模型：**不渲染**。无匹配：空态行（高度 = 132）。pending：`readOnly`（不可输入，但可见）。大字号：行高 `max(44, 文字+20)`。横屏：同竖（宽度不变、行高不变）。多语言：placeholder/空态走 i18n。 |
| 11 | 无障碍 | `role=searchbox` + label「搜索模型」+ `aria-controls` 指向列表；清除钮 label「清除搜索」；无匹配 `role=status` 播报「没有匹配的模型」。 |
| 12 | 依据 | **E0** `MSel/…tsx:106`（`showSearch = choices.length > 4`）、`:107-109`（分组内过滤 + 空组丢弃 + `rankByName`）、`:509-537`（搜索行/清除）、`:604-606`（无匹配文案）、`MSel/…css:158-214`（搜索行几何）；**E0** `M/:507-538`（清除钮 44×44、输入右 padding 48）；**user** D-09（无键盘链路）、D-16/A（选中即处理的口径同族）；G-16（行 44）；**我方取值（P4 校准）**：无防抖阈值（模型量级小，实测 <1ms）。 |
| 13 | 反证 + 验收 | **反证**：① ≤4 模型也显示搜索 → 凭空多一行、把列表挤短（DSH 反证）；② 只做前缀匹配 → 用户记得"flash"却敲"deep"就找不到（有序子序列是 DSH 的解法）；③ 过滤后保留空组头 → 一片空标题（"这里本来有模型"的错觉）；④ 清除钮小于 44 → 主要恢复操作点不中。**验收**：`MODEL-17`（>4 才出现）、`MODEL-18`（匹配与排序 + 空组隐藏 + 无匹配空态）、`MODEL-19`（清除钮 44、清空后焦点回输入框）。 |

### 3.5 `model.menu.model-group-header` — provider 组头（吸顶 + 可折叠）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.menu.model-group-header`。每个 provider 分组 1 个（provider 顺序 = `ProviderProfile` 列表顺序，即用户在设置里的配置顺序）；组内模型数 ≥1（搜索过滤后为 0 的组整组隐藏）。可折叠是 D-13 题设要求（"模型分组折叠"）。 |
| 2 | 网格与定位 | 44dp 高、满宽；左内边距 20 → 组名（13/20 secondary，单行 ellipsis）→ `flex:1` → 尾部 chevron 16（展开 ⌄ / 折叠 ›）→ 右内边距 20。在**列表滚动容器内** sticky。 |
| 3 | 命中区 | 整行 44 × 满宽（G-16 行级 44）；chevron 不独立命中（G-11：不给双命中区）。 |
| 4 | 手势 | 单击 = 折叠/展开本组。无长按/双击；竖直拖动归列表。 |
| 5 | 行为 | `toggle(group)`（本地态，**仅在本次 sheet 打开期间保留**；关闭 sheet 后重置为全部展开，下次打开从展开态开始）。折叠 = 该组行从布局移除（不占位、不保留幽灵空间）；再点恢复。幂等可撤销。不改任何数据。 |
| 6 | 视觉状态 | default：组名 secondary、chevron tertiary；pressed：背景 10%；**吸顶时**：不透明底色（`surfaceContainer`）+ 底部 0.5dp hairline（遮挡下方行，否则文字重叠）；折叠时 chevron 旋转 -90°。 |
| 7 | 布局占位与展开形态 | 在列表流内；折叠时该组行**即时移除**（G-10：列表项插入删除不做高度动画）；下方内容即时上移；展开即时下移。 |
| 8 | 滚动语义 | 随列表滚；sticky 到**列表容器顶**（不是 sheet 顶）；离开本组范围即解除。吸顶判定阈值 1dp。 |
| 9 | 动效 | chevron 120ms 旋转；高度变化**瞬时**（G-10 明令：列表项插入删除不做高度动画）；`reduced-motion` 直切。 |
| 10 | 边界态 | 只有 1 个 provider：**仍显示组头**（保持结构一致、也可折叠）。全部组被折叠：列表只剩组头（允许；用户可再展开）。provider 显示名为空：用 `provider.id`。大字号 2.0：44 最小高、文字换行 → 行高增。横屏/深色：同态。 |
| 11 | 无障碍 | `role=button` + `aria-expanded`；label = provider 显示名；折叠不播报行数（无计数，避免噪声）。 |
| 12 | 依据 | **user** D-13=B 题设（"模型分组折叠"）、P2 §3-1（采纳分组折叠，反证"模型多时不能只靠滚动"）；**E0** DSH 组头吸顶（`ref/dsh-settings.md:1019` 引 `MSel/…css:196-200` `observeStickyMenuGroups`；**DSH 的组头不可折叠**）；**E0** dsh-Go `model_sheet.dart:688-737`（`_ChannelBar`：provider 多时是**筛选条**，不是折叠——P2 引"单击分组=展开/收起"与其源码不符，**见 §5 `P-MODEL-04`**）；**E0** `go/model_sheet.dart:597`（单 provider 时用 `SheetSectionLabel` 显示组名）；**我方取值（P4 校准）**：折叠态"关闭 sheet 即重置"（不留持久偏好，避免"下次打开发现组全折叠了"）。 |
| 13 | 反证 + 验收 | **反证**：① 组头不吸顶 → 滚到第 15 个模型时不知道自己是哪个 provider 的（DSH 用 `observeStickyMenuGroups` 正是为此）；② 吸顶不带不透明底 → 组名与滚过的模型行文字重叠；③ 折叠做成"渠道筛选条"（dsh-Go 的实际做法）→ 用户看不到其它 provider 有哪些模型，且会把筛选误当成"已经在筛选里选模型"；④ 折叠态持久化 → 用户下次打开看到空列表（组全折叠）以为模型丢了。**验收**：`MODEL-20`（滚 3 屏后当前组头仍在列表顶 ±1dp 且不透明）、`MODEL-21`（折叠/展开：行即时移除/恢复、无高度动画、关 sheet 后重置为展开）、`MODEL-22`（44 整行命中 + `aria-expanded`）。 |

### 3.6 `model.menu.model-row` — 模型行（整行单命中区）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.menu.model-row`。每组内每个模型 1 行，顺序 = `ProviderProfile.models` 目录序；provider 停用 / 模型不可用（行级 `disabled`）也显示（给原因，不给"可点却报错"）。 |
| 2 | 网格与定位 | 44 高、满宽；左内边距 20 → 模型名（`name ?: id`，14/20，单行 ellipsis，`flex:1`）→ gap 12 → 尾部 20 槽（选中 = 对勾 20；pending = 状态点 8；disabled = 12/18 原因文字）→ 右内边距 20。行间 gap 0（相邻命中盒不重叠）。 |
| 3 | 命中区 | 整行 44 × 满宽（G-16 行级）；**行内不设第二命中区**（对勾/原因文字都不可点）。 |
| 4 | 手势 | 单击 = 选中（同值仅关闭）；无长按/双击；竖直拖动归列表。 |
| 5 | 行为 | 提交 `SessionSetModel(sessionId, providerId=该行 provider, modelId=该行 model, reasoningLevel=§2.4 的"重置规则")`。成功 → 关 sheet + 胶囊更新；失败 → §2.3 失败分支。disabled 行点击**零行为**（不弹 Toast、不提交）。 |
| 6 | 视觉状态 | default：文字 primary；pressed：背景 10%；**selected = 尾部对勾**（**不用底色**、不加粗）；pending：尾部状态点 + 本行不可点 + 其它行 disabled；disabled：文字 40% 不透明 + 行尾 12/18 原因；无 hover。 |
| 7 | 布局占位与展开形态 | 列表流内 44 固定（大字号例外，见字段 10）；不参与高度动画。 |
| 8 | 滚动语义 | 随列表内滚；打开 sheet 时当前选中行滚入可视区（`nearest` 语义，不居中以免列表跳动）。 |
| 9 | 动效 | 无行内动画；状态点脉冲；`reduced-motion` → 静态点。 |
| 10 | 边界态 | 超长模型名：单行 ellipsis（`aria-label` 全文）。当前模型不在目录：无 selected 行（胶囊走 S16）。0 模型：该组不渲染（组头也不渲染）。20+ 模型：列表内滚。大字号 2.0：行高 `max(44, 文字+20)`，文字可换行 → 行高增（44 不再是保证，P4 校准）。深色/横屏：token/同竖。 |
| 11 | 无障碍 | 列表 `role=radiogroup`（每组的父容器），行 `role=radio` + `aria-checked`；label = 模型名（disabled 追加「不可用」）；pending 行 `aria-busy`。 |
| 12 | 依据 | **E0** `MSel/…tsx:480-608`（模型行渲染）、`:593-596`（pending 状态点 vs 对勾）、`MSel/…css:221-283`（行 34 / 尾部 check 14 / 名称 13-18 ellipsis）；**E0** `go/model_sheet.dart:454-471`（`_pickModel`：选中即 `onSelected`；`findModel` 校验）；**E0** `IB:190-201`（`SessionSetModel` 是唯一写点）、`IB:829-843`（可用性字段）；**E0** `IB:470-483` + P2 §2（切模型重置档位；`dsh directory.ts:157-194`）；**user** G-16/D-02（44）、D-09、D-12；**P2** C-M5-07（**不显示 ctx/out 副标**）；**我方取值（P4 校准）**：对勾 20、原因 12/18、行尾固定 20 槽。 |
| 13 | 反证 + 验收 | **反证**：① 行内再放独立 RadioButton 命中区（旧 UI 双命中）→ 同一点击两条事件路径，点行与点圈行为可能不一致（P2 反证）；② selected 用底色 → 与 pending/高亮混淆，"当前高亮即将被接受"被误读成"已选中"（DSH 反证）；③ 显示 `id · ctx · out` 副标（旧 `MA:1393-1406`）→ 把 provider 配置值误当本轮用量（C-M5-07）；④ 切模型不重置档位 → 新模型没有旧档时后端直接 `REASONING_UNSUPPORTED`（`IB:486-488`），用户点一下模型就报错。**验收**：`MODEL-23`（44 整行单命中、无第二命中区）、`MODEL-24`（选中/待定/禁用三态可区分）、`MODEL-25`（切模型重置档位、不产生 `REASONING_UNSUPPORTED`）、`MODEL-26`（切到无 reasoning 模型 → 档位小节隐藏、胶囊只显示模型名）、`MODEL-41`（禁用行零行为 + 原因）。 |

### 3.7 `model.menu.effort-row` — 推理等级小节 / 档位行（含 `off` 与 `Default`）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.menu.effort-row`。小节恒在 sheet 底部固定区（S14 矮屏降级进主滚动）。出现条件：当前模型 `reasoning != null` 且 `efforts` 非空。行集合 = ① `efforts` 逐档（**含 apiValue 为空的 off 档**）；② 若 `efforts` 中**没有任何空 apiValue 档** → 追加一行 `Default`（D-12）。模型无 reasoning / efforts 为空 → 整个小节不渲染（或只显示单行只读文案「当前模型未提供推理档位」）。 |
| 2 | 网格与定位 | 小节 = 分隔（8 + 0.5dp hairline）+ 标签 32（12/18 tertiary，不可点）+ 行 44×N（N≤4；>4 → 小节内滚，上限 176）。行内：左 20 → 档位显示名（14/20）→ `flex:1` → 尾部 20 槽（对勾/状态点）→ 右 20。`Default` 行 = 主文案 `Default` + 副文案 12/18「跟随模型默认档」或「由服务端决定」（同行右侧或第二行，**不撑高 44**：副文案与主文案同行、caption 色）。 |
| 3 | 命中区 | 整行 44 × 满宽；标签/副文案不可点；尾部槽不独立命中。 |
| 4 | 手势 | 单击 = 选中该档（同值仅关闭）。无长按/双击；>4 档时竖直拖动归本小节内滚。 |
| 5 | 行为 | 提交 `SessionSetModel(provider, model 不变, reasoningLevel = 档 key / Default → null)`（§2.2）。成功 → 关 sheet + 胶囊更新；失败 → §2.3。选中后**不回滚**、不做"抄到设置默认值"（§2.5 边界）。 |
| 6 | 视觉状态 | selected = 尾部对勾；**选中判定用"生效档"** = `sidecar.reasoningLevel ?: model.reasoning.default`（所以会话未选过时，勾在模型默认档上——与 DSH `effectiveEffort` 同口径，`MSel/…tsx:610-643`）；pending = 状态点；disabled = 整个模型不可用时整小节禁用（40% + 不可点）。 |
| 7 | 布局占位与展开形态 | 常态固定区（不随模型列表滚）；超过 4 档或 S14 矮屏 → **整张 sheet 降级为单一滚动容器**（规则见字段 8）——同一时刻只允许一个内滚容器。 |
| 8 | 滚动语义 | **仲裁规则（唯一可滚）**：常态下模型列表是唯一内滚、档位小节不滚。若 `effortCount > 4`，让档位小节自己内滚就会出现**两个**内滚容器（禁止，G-04）→ 该情形与 S14 走同一降级：**整张 sheet 单滚**（标题 + 搜索固定，其余一个容器滚）；到边吸收（G-04），不把残余速度传给遮罩/下层。 |
| 9 | 动效 | 无（状态点脉冲）；选中即关的前后无高度动画（G-10）。 |
| 10 | 边界态 | 模型无 reasoning：小节不渲染（胶囊只显示模型名，`MODEL-26`）。`efforts` 为空但 `default != null`：显示单行只读「当前模型未提供推理档位」（`MSel/…tsx:618-619` 同口径）。**无 off 档**：出现 `Default` 行（文案诚实，§2.2）。档 key 未知：显示 key 原文（§2.2 显示名规则）。大字号：行高 `max(44, 文字+20)`。横屏：S14 单滚。深色：token。 |
| 11 | 无障碍 | 小节容器 `role=radiogroup` + label「推理等级」；行 `role=radio` + `aria-checked`；`Default` 行 label 含「由模型决定档位」；pending `aria-busy`。 |
| 12 | 依据 | **user** D-12=A（`off`=不思考；无 off 档给 `Default`=交 provider；**这才是本节点存在的理由**）、G-16（行 44）、D-09；**E0** `MSel/…tsx:129-140`（档位列表 + Default 条件 `defaultEffort === undefined`）、`:610-643`（行渲染、生效档勾选、pending）、`:618-619`（空文案）；**E0** `MD:59-62`（`efforts: Map<String,String?>` + `default`，**无显示名**）；**E0** `IB:479-495`（level→apiValue→thinking 唯一映射，off=空 apiValue→false）、`:486-488`（REASONING_UNSUPPORTED）；**E0** `MA:2072-2078`（旧编辑器 `off to ""`，证明"off=空 apiValue"是既有语义）；**E0** `go/model_sheet.dart:52-58`（档位 key→显示词表）、`:629-655`（档位小节在模型区之下、独立 section label）；**P2** C-M5-8（离散列表不用滑杆——`efforts` 是任意 Map，滑杆无法表达"关闭/Default"这类非数值档）。 |
| 13 | 反证 + 验收 | **反证**：① 用滑杆（太墟 `ReasoningEffortSlider`）→ myco 档位是任意 Map（含"关闭"这种非数值档），线性滑杆只能假装线性（P2 C-M5-08 反证）；② 用横排 chips（旧 UI FilterChip / dsh-Go `SegmentedPill`）→ 6 档在 320dp 上每段 `(320−40)/6 ≈ 47dp`，中文两字标签必截断，且选中态读不出来（算术）；③ 模型无 off 档时**不给** `Default` → 用户没有任何"不指定档位"的表达方式（D-12 代价项）；④ `Default` 写成「不思考」→ 假承诺（模型仍有默认档时会思考，见 `IB:483` 回落链）；⑤ 保留 composer 一键开关来"关思考" → 双真源回归，BUG-003 复现（D-12 的直接动机）。**验收**：`MODEL-27`（行 44、off 显示「关闭」、`Default` 仅在无 off 档时出现）、`MODEL-32`（**off 档实际请求字段**：构造一次发送 → `ChatRequest.thinking=false` 且 `reasoningEffort=null`）、`MODEL-33`（具体档：`thinking=true`、`reasoningEffort=apiValue`）、`MODEL-34`（`Default`：sidecar 被清空、发送 `reasoningLevel=null`、无 `REASONING_UNSUPPORTED`）、`MODEL-45`（无 reasoning / 空 efforts 时不渲染可选行）。 |

### 3.8 `model.menu.load-retry` — 目录加载失败/部分失败条（+ 重试）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.menu.load-retry`。出现条件（两类，可同时存在，整体错误优先显示）：① **整体失败** = 目录加载返回 Err（此时不显示列表与档位小节）；② **部分失败** = 至少 1 个 provider 加载失败但有可用模型（显示警告条，列表可滚）。**只在"最近一次动作 = 加载"时显示**；选择失败走 Toast（§3.11），**绝不**显示在本条（否则用户以为要点重试才能撤销一次已提交的选择）。 |
| 2 | 网格与定位 | 条在列表上方 inline：高 `max(36, 文字+16)`、左右 20、上下 8；文案 13/20；右端「重试」文字按钮（44 高命中）。 |
| 3 | 命中区 | 「重试」按钮 44 高 × (文字宽 + 16)；条内其它文字不可点；与关闭钮/行不相邻。 |
| 4 | 手势 | 单击「重试」= 重新加载（重跑**全部** provider，不只失败的）。无长按。 |
| 5 | 行为 | 点击 → 条被「正在刷新模型列表…」替代 → 成功：条消失、列表渲染、保留当前选择；失败：条保留可再点。**不清空**已选值（胶囊仍显示当前选择，§2.6）。幂等（连点只跑一次，加载中有进行中标志）。 |
| 6 | 视觉状态 | error 条：error 底 + error 字；warning 条：warn 底 + warn 字（12/18）；loading：caption 灰字、无按钮；pressed 按钮背景 10%。 |
| 7 | 布局占位与展开形态 | inline 占位（挤压列表高度）；在列表滚动容器**之外**（固定），不随列表滚走。 |
| 8 | 滚动语义 | 不滚；列表在下方独立内滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 整体失败 + 分组警告同时存在 → 只显示整体错误条（分组信息并入其文案的 provider 名列表）。无 provider（空目录）→ **不是失败**，走 §3.9 空态。离线 → 同一错误条。大字号：高增。横屏/深色：同态。 |
| 11 | 无障碍 | 错误/警告文本 **`role=alert` 播报一次**（DSH 是普通 `span`、无 role —— 我方**补这个缺口**，`ref/dsh-settings.md` `Q-SET-16` 已记录该缺口）；重试按钮 label「重新加载模型列表」。 |
| 12 | 依据 | **E0** `MSel/…tsx:538-552`（错误条/警告条/loading 替代、重试重跑全部）、`:77-84,144-147`（`lastActionRef` 区分 load/select）、`:370-384`（选择失败走 Toast）；**E0** `MSel/…css:127-156`（条 11/16、retry 文字键）；**E0** `go/model_sheet.dart:501-519`（loading / hasError → `EmptyView` + `FilledButton.tonal(重试)`；disconnected 空态）；**E0** `G-16`（行内次级 ≥36 → 取 44）；**E1** `ref/dsh-settings.md` `Q-SET-16`（DSH 错误文本无 `role=alert`，我方补）。 |
| 13 | 反证 + 验收 | **反证**：① 选择失败也显示菜单内错误条（旧 `MA:1386-1388`）→ 用户以为必须点"重试"才能撤销已提交的选择（DSH 反证原文）；② 空白菜单无任何解释（旧 D11）→ 用户不知道是"没配"还是"挂了"；③ 重试没有 44 命中 → 唯一恢复出口点不中；④ 重试时清空当前选择 → 用户莫名丢配置。**验收**：`MODEL-39`（失败条 + 重试：成功恢复列表/失败保留可再点 + `role=alert` 播报）、`MODEL-40`（部分失败警告条列出 provider 名，与整体错误条区分）、`MODEL-09`（失败时胶囊仍显示当前选择）。 |

### 3.9 `model.menu.empty` — 空目录态（无可用模型 → 去设置）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.menu.empty`。出现条件：目录加载**成功**但可用模型为 0（无 provider / provider 全停用 / provider 无模型）。此时**不渲染搜索行、模型区、档位小节**，整块内容区替换为空态。 |
| 2 | 网格与定位 | 内容区居中列：上 24 下 24、水平 20；图标 32 → 标题 16/24（「还没有可用的模型」）→ 说明 13/20（2 行）→ 主按钮 48（「去设置 ›」）。provider 全停用时标题改「已配置的 provider 都不可用」+ 副按钮「重试」。 |
| 3 | 命中区 | 主按钮 48 高 × 内容宽（≥120）；「重试」文字按钮 44 高；两者间距 ≥8（不相交）。 |
| 4 | 手势 | 单击「去设置」= **先关 sheet，再 push `settings.models`**；单击「重试」= 重载目录（同 3.8 字段 5）。无长按。 |
| 5 | 行为 | 关闭 sheet 与 push 的**顺序不可颠倒**（`BL-27`：push 前必须同帧关闭面板内浮层并注销）；返回后**不自动重开** sheet（用户重开时目录自动重载）。幂等。 |
| 6 | 视觉状态 | 无 pressed 之外的状态；加载中主按钮 disabled；无 hover。 |
| 7 | 布局占位与展开形态 | 占满 sheet 内容区（替代列表+档位），高度按内容；结构变化瞬时（G-10）。 |
| 8 | 滚动语义 | 不滚（内容 < 0.8 屏）；横屏矮屏若放不下 → 允许内容区滚动（S14 单滚规则同用）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 未配置（无 provider）vs 已配置但全停用 vs 有 provider 无模型：三种说明文案不同（dsh-Go 用 `configured` 区分，E0）；离线 → 同"全停用"并给重试；大字号：说明换行；深色：token。 |
| 11 | 无障碍 | 标题 `heading`；说明可读；按钮 label 明确（「去设置配置模型」/「重新加载模型列表」）；进入空态时 `role=status` 播报标题一次。 |
| 12 | 依据 | **E0** `go/model_sheet.dart:501-535`（无模型 / 加载失败 / 未配置三态分支 + `configured` 判定 + `failures` 取首条消息）、`:665-686`（「管理模型 ›」→ push `ProvidersPage`）；**E0** `MSel/…tsx:604-606`（DSH 只有「暂无可用模型」文案，**没有去配置入口** = 被 dsh-Go 补齐的缺口）；**缺陷** 旧 myco D11（provider 全停时对话框空白）；**E0** `BL-27`（push 前关浮层）；**user** G-16（主操作 48）。 |
| 13 | 反证 + 验收 | **反证**：① 只有一句「暂无可用模型」（DSH）→ 新用户不知道下一步去哪；② 把"没配"与"挂了"混成一个文案 → 用户去设置发现明明配过（dsh-Go 用 `configured` 区分正是为此）；③ 在 sheet 上直接 push 设置页 → 浮层滞留被覆盖、返回时先关一个看不见的浮层（`BL-27` 反证）。**验收**：`MODEL-38`（三种空态文案分支 + 「去设置」先关 sheet 再 push + 返回不自动重开）。 |

### 3.10 `model.menu.dismiss` — 关闭契约（4 条出口 + 回焦）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.menu.dismiss`。不是独立视觉节点，是 sheet 的**关闭路径契约**；出现：sheet 打开期间始终生效。 |
| 2 | 网格与定位 | 4 个出口：①标题行关闭钮 44×44（右端）；②把手（视觉 36×4、命中 96×44）；③遮罩整层（判据见字段 4）；④下滑手势（整张 sheet）。 |
| 3 | 命中区 | 关闭钮 44（与标题文字不相邻）；把手 96×44（居中，与关闭钮不同区）；遮罩整层；三者互不相交。**没有**"点输入区/Esc 关闭"（D-09 无键盘）。 |
| 4 | 手势 | ①点遮罩 = 关，**按下与松手都在遮罩上**（D-08/G-18）；②返回键/侧滑 = 关（`BL-23`）；③下滑 = 关，**仅当触点所在内滚在顶**（G-13）；内滚不在顶 → 归内滚、到边吸收（G-04）；④把手下拉 = 关。 |
| 5 | 行为 | 关闭 = 滑出 → **回焦胶囊**（必做）→ 不取消在飞提交 → 不改任何值。**搜索框文字不作为"草稿"**：关闭即弃、无二次确认（G-12 的 dirty 检查针对"含输入框的设置页"，本 sheet 的搜索是临时过滤，不是待保存内容 —— 给它二次确认等于每次关面板多一次点击）。关闭动画中再次触发 → 忽略。 |
| 6 | 视觉状态 | 关闭钮/把手的 pressed 态；拖动时遮罩 alpha 跟手。 |
| 7 | 布局占位与展开形态 | 形态 C：退出不占位；退出后下层滚动位置/草稿/流式状态不变（锚点不跳）。 |
| 8 | 滚动语义 | 关闭时丢弃内滚位置与搜索 query；折叠态重置（§3.5）；下次打开滚到当前选中行。 |
| 9 | 动效 | spring 退出（D-07）；可被系统"移除动画"降级为直切（G-10 末段）。 |
| 10 | 边界态 | 提交 pending 中关闭：允许（提交继续）；关闭动画中再点：忽略；返回键优先级：**IME → sheet → chat route → 系统**（`BL-22…BL-25`）；大字号/横屏/深色：同态。 |
| 11 | 无障碍 | 关闭钮 label「关闭」；把手 label「拖动关闭」；关闭后焦点回到胶囊（P4 TalkBack 验收，`BP §1.3` 明写"回焦必须自己写"）；焦点陷阱 E1。 |
| 12 | 依据 | **user** D-13（返回键先关 sheet、关闭后回焦）、D-08/G-18、G-13、D-07、D-09；**E0** `BL-23`（返回只关 sheet）、`BL-22…BL-25`（dispatcher 优先级链）；**E0** `BP §3.11` 关闭集合表（遮罩/返回/下滑/把手/关闭钮 + 回焦必做）；**E0** `MSel/…tsx:260-266`（关闭与回焦、`close(true)`）、`:386-392`（提交时先聚焦 trigger）；**E0** `go/model_sheet.dart:484-488`（选中后延时 pop）、`app_sheet.dart`（拖拽/遮罩关）。 |
| 13 | 反证 + 验收 | **反证**：① 返回键不先关 sheet → 一次返回退出会话（P2 反证原话）；② 遮罩"按下即关" → 用户在搜索框里拖选文字、手指滑到遮罩松手就关了（D-08/G-18 反证）；③ 任意位置下滑可关 → 想滚列表却把面板滑没了（G-13）；④ 关闭不回焦 → 读屏焦点掉到页首，下一次滑动从顶部开始（DSH 反证）；⑤ 给搜索框做"草稿二次确认" → 每次关面板多一次点击、而内容本来就不保存（G-12 的适用边界）。**验收**：`MODEL-10`（返回只关 sheet、连按第二次才退 route）、`MODEL-11`（遮罩按下+松手才关）、`MODEL-12`（内滚在顶才下滑关）、`MODEL-13`（回焦胶囊 + 无焦点环）、`MODEL-15`（IME 打开时关闭仍可达）。 |

### 3.11 `model.submit.feedback` — 提交反馈（pending / 成功 / 失败回滚 / 同值）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.submit.feedback`。不是独立视觉节点：出现于每次"点行选中"的 1 个提交事务（pending → 成功/失败）。状态承载在**胶囊尾部状态点**（pending）、**行尾部状态点**（pending，sheet 内）、**Toast**（失败）。**没有成功 Toast**。 |
| 2 | 网格与定位 | pending 点 = 8dp 圆点，放在胶囊尾部 **12dp 槽**（替 chevron，占位不变宽）；行内同样放尾部 20 槽（替对勾）；Toast 按 `BP` toast 原子（宽 `min(屏−32,480)`、高 ≥48、内边距 16、圆角 16、底部 = `union(navBars, IME) + 8`）。 |
| 3 | 命中区 | 状态点不可点；Toast 整块不拦指针、不接点击（无动作）。 |
| 4 | 手势 | 无（Toast 自动过期；不提供"重试"动作 —— 重试 = 重新打开 sheet 再选，比给一个只能重放同一次失败写的按钮更有意义）。 |
| 5 | 行为 | ①按下行 → **立即**（同帧）发 `SessionSetModel` 并把胶囊/行切 pending；②成功 → 关 sheet + 回焦胶囊 + 更新值（无 Toast）；③失败/超时（5s）→ **强制关 sheet** → 胶囊回滚到旧值 → Toast 3s「切换失败：<原因>」（超时文案「切换超时」）；④同值 → 只关 sheet（不 pending、不写、无 Toast）；⑤pending 中点同一行 → 零行为（行 disabled）。 |
| 6 | 视觉状态 | pending：状态点脉冲 + `aria-busy` + 视觉隐藏文案「正在切换模型」（G-08）；成功：值更新（无额外视觉）；失败：胶囊回旧文案 + Toast（inverse 表面）；**成功/失败都不显示焦点环**（DSH `data-selection-focus` 抑制一次，E0）；焦点环仅键盘路径需要 —— D-09 无键盘，故**恒不显示**。 |
| 7 | 布局占位与展开形态 | 胶囊 pending 时宽度不变（状态点占 12 槽）；Toast 是 overlay（G-01 不占位）。 |
| 8 | 滚动语义 | 不适用（无内滚）。 |
| 9 | 动效 | 状态点脉冲；Toast 进 ≤120ms 淡入、出瞬时（G-10：浮层不做高度动画）；`reduced-motion` → 静态。 |
| 10 | 边界态 | 会话在提交中被删除/归档（`IB:190-201` 返回 `SESSION_MODEL` Err）→ 走失败分支；pending 中用户关 sheet → 提交继续，胶囊持续 pending 到回执（回执到达时 sheet 已关 → 只在胶囊更新，无 Toast）；pending 中用户切走会话 → 回执只作用于原会话，胶囊不跨会话显示；离线下 `SessionSetModel` 仍可能成功（本地 IPC），不额外处理。 |
| 11 | 无障碍 | pending `aria-busy` + 隐藏文案（G-08）；失败 Toast `role=status` 播报一次；成功靠胶囊 label 变化被读屏捕捉（不再额外播报，避免噪音）。 |
| 12 | 依据 | **E0** `MSel/…tsx:370-400`（`settleSelection`：ok → `closeAfterSelection`；err → Toast + `session/writer-held` 专属文案）、`:386-392`（提交先聚焦 trigger）、`:472-474`（`StateDot` 替 chevron）、`:593-596,635-638`（行内状态点 vs 对勾）；**E0** `MSel/…css`（StateDot/`data-selection-focus` 抑制焦点环）；**E0** `go/chat_screen.dart:1835`（保存失败提示）；**E0** `IB:190-201`（唯一写命令：成功 emit `SessionUpdated`、失败 `Err("SESSION_MODEL")`）、`SS:256-259`；**E0** `BL §1.6`（Toast z20 < sheet z1000）；**E0** `G-08`（运行态 a11y 文案）；**user** G-12（纯单选 = 即时写）、D-16/A（选中即处理的同族口径）、D-09；**P2** `C-M5-05` 分层（动作级 Toast / 会话级持久警告）；**我方取值（P4 校准）**：5s 超时、Toast 3s、失败强制关 sheet（由 z 表推出）。 |
| 13 | 反证 + 验收 | **反证**：① 旧 M5 无 loading 态（差异表 §4#23）→ 用户连点两次产生两次写，或以为没点上；② 失败信息放面板内错误条（旧 `MA:1386-1388`）→ 用户以为必须点它才能撤销已提交的选择（DSH 反证原文）；③ 在 sheet 里弹 Toast → z 上被 sheet 盖住（Toast 20 < sheet 1000），用户根本看不见（`BL-50` 明令不许把 Toast 抬到面板上）；④ 成功给 Toast → 每次切模型都弹一次，纯噪声；⑤ 提交不置位（连点）→ 两次写、两次 pending、可能两次 Toast（旧 BUG-004 的同族）。**验收**：`MODEL-28`（同值只关不写）、`MODEL-29`（选中即提交：同帧发命令、无确认键、成功关 sheet）、`MODEL-30`（pending：胶囊/行状态点 + 隐藏文案 + 行禁用）、`MODEL-31`（失败：先关 sheet 再 Toast + 胶囊回滚；超时同分支）、`MODEL-35`（唯一真源源码断言）。 |

---

## 4. 验收清单

> 全部可判定。方式栏：**单测** = 可直接在 core/UI 层写断言；**真机** = 需真机手势/录屏；**截图** = 静态比对；**源码** = 静态断言（grep/AST）。
> 依赖：`§2.2`=档位映射表；`§3.x`=节点；缩写同文件头。

| ID | 判定 | 方式 | 依赖 |
|---|---|---|---|
| MODEL-01 | 胶囊文案 = `模型名 · 档位`（中间为 `·` + 单空格）；档位为空（无 reasoning 模型）时 = 仅模型名；`Default` 档显示 `Default`。三种情况各截图一张 | 截图 + 单测（label 拼接函数） | §3.1 |
| MODEL-02 | 320dp 宽屏：工具行单行、主键 48dp 完整可见（右缘距屏右 ≥6dp）、胶囊宽 ≤112dp；量测截图；`W=320` 时 `320 − 112 = 208 ≥ 112` 判定式成立 | 截图量测 | §1.4 |
| MODEL-03 | 容器可用宽依次取 112 / 100 / 90 / 80 / 70 / 44 时：112/100 → 态 A（含档位）；90/80 → 态 B（无档位、模型名 ellipsis）；70/44 → 态 C（图标，无文字）。任一值下高度恒 40、宽 ≤112 | 单测（布局约束）+ 截图 3 张 | §1.5 |
| MODEL-04 | 胶囊命中盒 = 40dp 高 × 布局盒宽（命中盒 = 布局盒，无伪元素/offset 扩边）；与 `+`、主键命中盒不相交（矩形断言） | 单测（hit rect）+ 真机点击 | §3.1/G-11 |
| MODEL-05 | `aria-label` 三式命中（加载中/选择模型/「当前 X，推理等级 Y」）；pending 时 `aria-busy=true`；不可用态末尾含「不可用」 | 单测（快照）+ TalkBack | §3.1/§3.2 |
| MODEL-06 | 构造 `subagentAddress != undefined` 的会话 → 工具行无胶囊节点（不是灰态、不留位）；同会话下打开 sheet 的入口不存在 | 单测（渲染树断言） | §2.6 |
| MODEL-07 | 流式中（`running=true`）胶囊**可点**（打开 sheet），切换模型后：本轮 `lastRequest.model/thinking/reasoningEffort` 不变（断言）；结束本轮再发 → 新值生效 | 单测（fake client 捕获请求）+ 真机录屏 | §2.4/§3.1 |
| MODEL-08 | 删除当前模型/停用其 provider 后：胶囊 ≤1 帧显示「<模型名>（不可用）」且**可点**；打开 sheet 后该模型不出现（或 disabled），可正常改选 | 真机 + 单测（`Query.SessionModel` 注入 false） | §3.2 |
| MODEL-09 | 目录加载中 → 胶囊「正在加载模型…」；加载失败 → 胶囊仍显示当前选择（文案不变）且可点开；两者不误报"不可用" | 真机（mock 延迟/失败） | §3.1/§3.8 |
| MODEL-10 | sheet 打开时按返回 → **只关 sheet**，chat 未 pop（栈深不变）、滚动位置不变；再按一次才走 chat 的返回分支 | 真机 | §3.10/`BL-23` |
| MODEL-11 | ①在 sheet 内容上按下、拖到遮罩松手 → **不关**；②在遮罩按下、拖动 >slop 再松手 → **不关**；③遮罩按下+松手 → 关 | 真机手势 | §3.3/D-08 |
| MODEL-12 | 列表滚到中部下滑 → 不关、列表继续滚；滚到顶再下滑 → 关；滚到顶后一次手势不把残余速度传给遮罩/下层（录屏逐帧） | 真机录屏 | §3.3/G-13/G-04 |
| MODEL-13 | 关闭后焦点回到胶囊（TalkBack 报读焦点在胶囊）；成功切换后**无焦点环**（截图）；动画中再次触发关闭被忽略 | 真机 TalkBack + 截图 | §3.10 |
| MODEL-14 | 底部间距 = `systemBars` inset（三键导航与手势导航各测一次，无「双垫」多余空隙）；sheet 内容内不再垫一次 | 截图（两种导航模式）+ 单测（padding 计算） | §3.3/`BP-30` |
| MODEL-15 | 搜索框聚焦弹 IME：搜索行仍完整可见、列表可滚、关闭钮可达、sheet 底边在 IME 之上（不被键盘压住） | 真机（Gboard 中文） | §3.3/§3.4 |
| MODEL-16 | 20 个模型时：sheet 高 ≤ `0.8×窗口高`、列表内滚、标题行与搜索行固定不动（滚动中 y 不变 ±1dp） | 真机录屏 + 截图 | §3.3 |
| MODEL-17 | 模型总数 = 4 → 无搜索行；= 5 → 出现搜索行（单测两例） | 单测 | §3.4 |
| MODEL-18 | 输入 `dsk` 能命中 `DeepSeek-V3.2`（有序子序列）；排序为「前缀 > 词边界 > 目录序」；过滤后为空的 provider 组**整组不渲染**；无匹配显示「没有匹配的模型」 | 单测（给定固定目录断言顺序与结果） | §3.4 |
| MODEL-19 | 清除钮 44×44；点击后 query 清空、焦点回输入框（IME 保持打开）、列表恢复全量 | 真机 + 单测 | §3.4 |
| MODEL-20 | 列表 20 模型滚 3 屏：当前 provider 组头始终贴列表顶（y 误差 ≤1dp）且**不透明**（下方行文字不透出，截图逐像素） | 真机录屏 + 截图像素 | §3.5 |
| MODEL-21 | 点组头 → 该组行**即时**消失（无高度动画，同帧完成）、下方内容上移 ≤1 帧；再点恢复；关闭 sheet 后重开 → 所有组均为展开态 | 真机录屏 + 单测（状态重置） | §3.5 |
| MODEL-22 | 组头命中 = 整行 44 × 满宽；`aria-expanded` 随折叠态变化 | 单测量测 + TalkBack | §3.5 |
| MODEL-23 | 模型行命中 = 整行 44 × 满宽；行内**只有一个**可点节点（无独立 RadioButton/对勾命中区）—— 源码断言 + hit rect 测试 | 源码 + 单测 | §3.6 |
| MODEL-24 | 三态可区分（截图）：选中 = 尾部对勾且**无底色**；pending = 尾部状态点；disabled = 文字 40% + 行尾原因文案 | 截图 + 单测 | §3.6 |
| MODEL-25 | 当前模型档=高，切到只有 `off/low` 的新模型 → 提交的 `reasoningLevel` 是新模型 `default`（或 `off`），**不是**旧档；任意切换后 `ChatSend` 都不产生 `REASONING_UNSUPPORTED` | 单测（fake store + fake client） | §2.4/§3.6 |
| MODEL-26 | 切到 `reasoning == null` 的模型 → 档位小节不渲染；胶囊只显示模型名（无 `·`）；发送时 `reasoningLevel=null`、`thinking=null` | 单测 + 截图 | §3.7 |
| MODEL-27 | 档位行 44 整行；`off` 档（apiValue 空）显示「关闭」；`Default` 行**仅在** efforts 无空 apiValue 时出现（两套 fixture 各断言一次） | 单测 + 截图 | §3.7/§2.2 |
| MODEL-28 | 点当前已选中的行/档 → sheet 关闭，且 fake `SessionSetModel` **调用次数 = 0**（不写、不 pending、无 Toast） | 单测 | §3.11 |
| MODEL-29 | 点新值 → **同帧**发出 `SessionSetModel`（无"确定"按钮、无二次确认）；成功后 sheet 关闭且胶囊更新；全程无确认 UI | 单测 + 真机 | §3.11 |
| MODEL-30 | pending 期间：胶囊尾部与目标行尾部各出现 8dp 状态点；视觉隐藏文案「正在切换模型」存在（`aria-busy`）；其它行 disabled（点击零行为） | 单测 + TalkBack | §3.11 |
| MODEL-31 | 注入 `SESSION_MODEL` Err：**sheet 先关闭** → 胶囊回到旧文案 → Toast「切换失败：…」可见（不被面板遮挡）并在 3s 内消失；超时（5s 无回执）走同一分支、文案「切换超时」 | 单测 + 真机（截图证明 Toast 未被遮） | §3.11/§2.3 |
| MODEL-32 | **（"关掉后确实不思考"）** 选 `off` 档 → 发送一次：fake `ChatClient` 捕获的 `ChatRequest.thinking == false` **且** `ChatRequest.reasoningEffort == null`；同时 `ChatSend.reasoningLevel == "off"`（≠ 省略）。禁止只断言 UI 文案 | 单测（现有 `IBT:629-631` 口径直接复用/扩展） | §2.2 |
| MODEL-33 | 选具体档 `low` → `ChatRequest.thinking == true` 且 `reasoningEffort == efforts["low"]`；DeepSeek provider 下 body 含 `"thinking":{"type":"enabled"}` 与 `"reasoning_effort"`（body 快照断言） | 单测（含 body 快照） | §2.2 |
| MODEL-34 | 选 `Default`（无 off 档模型）→ `Query.SessionModel.reasoningLevel == null`（sidecar 被清空）；发送时 `ChatSend.reasoningLevel == null` 且不产生 `REASONING_UNSUPPORTED`；后端回落到 `model.reasoning.default`（若有） | 单测 | §2.2 |
| MODEL-35 | **唯一真源**：全 UI 源码断言无"思考/thinking 布尔"控件与写点（grep `thinking` 仅出现在发送透传与 a11y 文案）；`SessionSetModel` 全仓库**只有一个调用点**；`ChatSend.thinking` 恒为 null | 源码静态断言 | §2.2 |
| MODEL-36 | 已完成轮次的历史参数不被改写：选 A·high 发一轮 → 切 B·low → 断言历史事件/已完成轮的 `provider/model/reasoningEffort` 仍为 A·high（事件流字节级不变） | 单测（事件流快照对比） | §2.4 |
| MODEL-37 | 会话级持久：会话 S1 选 B·低 → 切到 S2（显示各自值）→ 回 S1 仍是 B·低；杀进程重进 S1 仍是 B·低；**新建**会话 S3 用模型默认档（不是 S1 的值） | 真机 + 单测 | §2.4 |
| MODEL-38 | 空目录（3 种：未配置 / provider 全停用 / provider 无模型）各显示对应文案；点「去设置」→ **sheet 先关**再 push `settings.models`（录屏帧序）；返回 chat 后 sheet 未自动重开 | 真机录屏 + 单测 | §3.9 |
| MODEL-39 | 目录整体失败：显示错误条 + 「重试」44；点重试 → loading 文案 → 成功恢复列表 / 失败保留可再点；错误文本 `role=alert` 被播报一次（TalkBack） | 单测 + 真机 | §3.8 |
| MODEL-40 | 部分失败：警告条列出失败 provider 名（≥2 个时全列）+ 「重试」；有可用模型时列表仍可滚可选；整体错误条与警告条不重复出现（整体优先） | 单测 + 截图 | §3.8 |
| MODEL-41 | disabled 行（provider 停用 / 模型不可用）：文字 40%、行尾 12/18 原因、点击**零行为**（命令调用次数 = 0、无 Toast） | 单测 + 真机 | §3.2/§3.6 |
| MODEL-42 | 截图矩阵（3 主题 × 字号 1.0/2.0 × 竖/横）：sheet 不超屏、行不裁切、对勾/状态点/图标不随字号变形、命中盒不缩水；横屏 412dp 高时降级为单滚动容器且能滚到档位行 | 截图矩阵 + 真机 | §3.3 |
| MODEL-43 | 只触摸（D-09）：源码无 hover 分支、无方向键处理、无 `Role.Tab`、无 tooltip；TalkBack 从标题→组头→行→档位行线性可完成一次选择 | 源码断言 + TalkBack | §3.3/§3.10 |
| MODEL-44 | 与 settings 边界：在 `settings.models` 改某模型 `reasoning.default` → 已开会话 S1 的选择不变（胶囊与 sidecar 均不变）；本面板不写 `efforts`/`default`/`contextWindow`（命令调用断言） | 单测 | §2.5 |
| MODEL-45 | 当前模型无 reasoning 或 `efforts` 为空：档位小节不渲染可选行（后者显示单行只读「当前模型未提供推理档位」）；sheet 仍可正常开关 | 单测 + 截图 | §3.7 |

---

## 5. 待确认

> 只列 P3 阶段发现的 **P2 遗漏/证据冲突/新决策点**。已拍板项（D-01…D-20、C-08、D-12/D-13/D-02/G-16/D-09/G-13/G-18）一律不重开。
> **阻塞判定**：本文件所写规格在下列项"按现状"均可实现；下列各项**不阻塞 P4 开工**，但其中 `P-MODEL-02`/`P-MODEL-03` 若用户改判会改写法。

| ID | 问题 | 两解与后果 | 建议 / 本文件现状 |
|---|---|---|---|
| `P-MODEL-01` | **横屏（高<480dp）与大档位集（>4 档）时 sheet 的双固定区放不下**：`0.8×412 ≈ 330`，而标题 56 + 搜索 44 + 档位小节 216 + inset ≈ 320，模型列表最小 132 放不进去。这是 `BP` `TBC-03` 同族问题（矮屏 sheet 最小可用高）。 | **A（本文件已写）**：只要任一区超限（矮屏或 >4 档）→ 整个 sheet 降级为**单一滚动容器**（标题+搜索固定，其余一滚）。代价 = 矮屏/6 档时改档位要滚过模型列表。<br>**B**：矮屏改用居右/全高抽屉（形态变化，需重开 D-13）。<br>**C**：矮屏把档位小节折叠成"当前档位"一行、点开 inline 展开（多一个展开态要测）。 | **A**（非阻塞，`BP TBC-03` 同类先例：按解 A 写 + 验收）；P4 真机若判定"改档位要滚太久"，再取 C。 |
| `P-MODEL-02` | **P2 §3 已采纳"DSH 搜索键盘全链路（↑↓/Enter/Tab/Esc）"，与 D-09=A「只做触摸、不做方向键」冲突**。 | **A（本文件已写）**：按 D-09 不实现键盘链路，搜索框只做输入 + IME 完成收键盘；外接键盘用户走触摸/线性焦点。<br>**B**：保留 DSH 链路 → 需重开 D-09（并连带影响全 app 的 hover/keyboard 口径）。 | **A**（D-09 是拍板项，P2 的建议与拍板冲突时以拍板为准）；**与 D-09 冲突已在 §0 收敛第 5 条声明**。 |
| `P-MODEL-03` | **切换模型时档位怎么处理：P2 采 DSH「重置为新模型 defaultEffort」，但 dsh-Go 实际是「旧档若新模型支持则保留」**（`go/model_sheet.dart:459-465`：`keep = _selection?.reasoningEffort; supported ? keep : startingEffort(model)`）—— P2 当时标注"dsh-Go 未细证 → 采纳 DSH"，现有 E0 反证。 | **A（本文件已写）**：重置为新模型 `reasoning.default`（无则 `Default`/清空）。后果 = 与 DSH 一致、行为可预测、不会带过去一个语义不同的档。<br>**B**：保留旧档（新模型支持时）→ 少一次选择、但"同一档名在两个模型上含义不同"（`apiValue` 可能不同）会产生静默语义漂移。 | **A**（非阻塞；若用户偏好 B，只改 §2.4 的一行映射 + `MODEL-25` 的期望值，不影响其它节点）。 |
| `P-MODEL-04` | **[文档勘误，非决策]** P2 §3-1 称"dsh-Go 分组标题可点展开/收起（`model.sheet` 字段 4）"；**回源码核实后：dsh-Go 是"渠道筛选条"（`_ChannelBar`，`go/model_sheet.dart:688-737`）+ 2 列模型卡片网格，既没有组头折叠，也没有分组列表**；DSH 有吸顶组头但**不可折叠**。P2 引的"单击分组=展开/收起"在当前源码中找不到。 | **A（本文件已写）**：仍做"分组 + 组头吸顶 + 可折叠"（题设 D-13 明确要求折叠；反证仍成立：20+ 模型只靠滚动找不到）。折叠的证据级别降为"**我方设计**（`P2 §3-1` 采纳 + Lead 题设）"，不再是 dsh-Go E0。<br>**B**：改成 dsh-Go 的渠道筛选条 → 与"两维 + 搜索"（C-08）冲突，多一个筛选维度且用户会误当选中。 | **A**（非阻塞）；请 Lead 修 `p2/model.md` §3 的证据行（本文件无权改 `p2/**`）。 |
| `P-MODEL-05` | **两处禁用态的真源在 myco core 里还不存在**：①"非用户直控会话"= DSH 的 `subagentAddress`，myco core 无 subagent 概念；②"锁定"= `removed/inert/!live`，myco core 无"会话 inert/父端离线"概念。 | **A（本文件已写）**：面板**不自己判断**这两态，只消费容器给的两个布尔 `modelSelectionAllowed` / `modelSeatLocked`（与 DSH 同构的接口）；当前实现里恒为 `true`/`false` 时两态不可达（不影响其它验收）。<br>**B**：由 `model` 面板自己从 `Query.SessionModel` + 本地状态推断 → 两套真源、后续加子代理会话时要改面板。 | **A**（非阻塞）；请 Lead 在 `base.layout`/`composer` 的 P3 里把这两个布尔落到"会话壳 → 工具行 → 座位"的传递路径（若最终没有子代理会话，就删掉 3.1 的这一行条件）。 |

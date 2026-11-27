# P2 合并：`model`（模型面板）

> 面板归属：`10-panels.md` 的 `model`（依赖 `composer`）。本表只处理「模型选择/推理等级」这一面板，不重复 `settings.models` 与 `composer` 的 spec。
> 参考列只许写事实库 `ref/*.md` 已有证据（带 `文件:行`）。**`太墟` 的模型面板在 `taixu.md` §2.8 是"简表级"证据**（P1 明确未展开 13 字段，理由是"不给不会实现的东西伪造精度"），本表引用时标「简表级」，其余写「未取证」。
> 状态图例：`✅` 已有 G 规则或已拍板项可直接定；`📌` 多参考一致/属缺陷修复，建议一键确认；`⏸` 需用户拍板（编号见 §6）。
> 冲突编号：`C-08` 已在 `_conflicts.md` 登记；`C-M5-01…C-M5-08` 为本次新发现、**尚未登记**（建议 Lead 顺延登记为 C-20 起）。

---

## 1. 节点清单（先穷举，再填表）

| 节点 ID | 出现条件 | 参考里有没有 | 旧 myco 有没有 |
|---|---|---|---|
| `model.entry.trigger` | composer 工具行常驻（非 addressed 子代理会话、会话就绪） | DSH ✅（输入区胶囊，`dsh-settings.md §2.8`）；dsh-Go ✅（顶栏模型 chip ＋ 输入条下方 `ComposerModelPill`，`dsh-go.md §2.1 chat.header`／§2.4 `model.sheet` 字段 1）；太墟 ✅ 简表级（输入区第 2 个 40dp 图标钮，`taixu.md §2.4 composer.input` + §2.8 `model.picker`）；chat-demo 无（模型写死） | ✅ 旧 UI 顶栏行（`MA:1041-1053`）/ M5 工具行胶囊（`MC:123-142`） |
| `model.entry.unavailable` | 会话保存的 provider/model 已被删或停用 | DSH 部分（`locked` disabled、失败行不可选）；dsh-Go 部分（行 `disabled`＝模型不可用/无凭据、无模型→空态＋去配置入口，`model.sheet` 字段 6/10）；太墟 未取证；chat-demo 无 | ❌ 后端已返回 `providerAvailable/modelAvailable`，UI 未消费（L7） |
| `model.menu.root` | 点入口打开、当前模型在目录且目录就绪 | DSH ✅（锚定 popover，根菜单两行→钻取）；dsh-Go ✅（底部 sheet，模型列表＋思考强度小节＋预设小节，单击行即选中并关）；太墟 ✅ 简表级（`ModelDialog`＋供应商选择器）；chat-demo 无 | ✅ 旧 AlertDialog 单屏平铺（`MA:1360-1441`）/ M5 扁平面板（`MS:49-148`） |
| `model.menu.model-row` | 模型列表内的行（按 provider 分组） | DSH ✅（分组＋对勾＋状态点＋组头吸顶）；dsh-Go ✅（分组标题＋行＋行尾「当前/锁定」标记＋disabled／busy）；太墟 ✅ 简表级（`ChatModelPicker`＋`ModelIcon`／`ProviderBadge`）；chat-demo 无 | ✅（`MA:1371-1408`；M5 `MS:100-123`） |
| `model.menu.effort-row` | 当前模型有 `reasoning` | DSH ✅（竖列表＋条件「Default」）；dsh-Go ✅（面板内「思考强度」独立小节，行形态未细证）；太墟 ✅ 简表级（**滑杆** `ReasoningEffortSlider`，`ChatScreen.kt:600-605`）；chat-demo 只有布尔开关 | ✅ 旧 FilterChip 横排（`MA:1409-1436`）/ M5 pills（`MS:127-140`） |
| `model.menu.search` | 模型数 > 4（DSH 判据） | DSH ✅；dsh-Go 未取证（模型 sheet 未见搜索）；太墟 未取证；chat-demo 无 | ❌ 旧/M5 均无搜索（差异表 §4#23） |
| `model.menu.load-retry` | 目录加载失败（整体）或部分 provider 失败 | DSH ✅（错误条＋Retry／分组警告条）；dsh-Go 有相关但不同（无模型→空态＋去配置入口）；太墟 未取证 | ❌（M5 模型为空时只剩开关与文案） |
| `model.menu.dismiss` | 菜单/面板已打开 | DSH ✅（外点／焦点移出／Esc 逐层退）；dsh-Go ✅（拖拽／点遮罩）；太墟 未取证（Dialog 关闭未逐条核实）；旧 myco ✅（M5 下滑/点外部，MISSING-1） | ✅（`MS:62`＋`MA:1489`） |
| `model.submit.feedback` | 选择提交中 / 提交失败 / 同值选择 | DSH ✅（StateDot＋行 disabled＋失败 Toast）；dsh-Go ✅（`busy` 转圈＋保存失败顶栏警告，`chat_screen.dart:1835`）；太墟 未取证 | ⚠️ 无 loading 态；失败显示在面板顶部（`MA:1386-1388`） |
| `model.thinking-switch` | 旧 M5：面板内；旧 UI：composer chips 行 | DSH **无**；dsh-Go **无**（只有「思考强度」小节）；太墟 **无**（只有滑杆）；chat-demo ✅ 布尔 toggle（但在 composer，且无档位维度） | ✅ 双份：M5 面板 Switch（`MS:87-96,169-186`）＋composer FilterChip（`CP:405-411`） |
| `model.preset.dimension` | 三维取舍点（是否进面板） | DSH **面板无**（hero chip／设置页）；dsh-Go **面板有**（预设独立小节，`model_sheet.dart`）；太墟 面板无（预设归设置功能页，`taixu.md §2.8 settings.features`） | ❌ core 无 preset 类型/命令（legacy-myco §3 缺口 5） |
| `model.context.dimension` | 面板内**不应出现**；三家都在 `settings.models` | DSH ✅ `settings.models.model-advanced`；dsh-Go ✅ `settings.models`（含 `model_context_page.dart` 上下文页）；太墟 ✅ 简表级 `settings.models` 编辑表单（maxTokens/context）；面板内三家都无 | ✅ provider editor 有 ctx/max（`MA:1916-2030`）；旧选择器行内只读显示 `id · ctx · out`（`MA:1393-1406`） |
| `model.command-popup` | `/model` 命令触发（DSH 第二入口） | DSH ✅；dsh-Go 未取证（有 `composer.slash` 面板，但 `/model` 命令未证）；太墟 未取证（有 `SlashCommandPopup`）；chat-demo 无 | ❌ core 无 slash 命令面（legacy-myco §3 缺口 3） |

> 合计 **13 个节点**；`model.entry.unavailable` 是参考里都没有"会话当前模型已失效"这一专门态的节点（→ §5）。

---

## 2. 对照表（主线）

| 节点 ID | DSH | dsh-Go | 太墟 | chat-demo | 旧 myco | 冲突 C-xx | 建议 + 理由 | 状态 |
|---|---|---|---|---|---|---|---|---|
| `model.entry.trigger` | 输入区工具行命名座位；胶囊「模型名 · 推理等级」13/20 secondary、高 28、`max-width:min(360px,45cqw)`；窄容器**先丢档位**（`flex-shrink:1000`）**再丢模型名**→图标+tooltip；`aria-label` 含模型+档位；作用域＝会话级投影（`ModelSelect.tsx:416-475`；`ModelSelect.module.css:9-30,45-69`；`directory.ts:17-35`） | **两个入口**：顶栏模型 chip（**只在非窄屏或新对话出现**，单击开 sheet，44×44 命中；顶栏宽度按优先级收缩：整条→chip 降档→标题缩短→chip 缩短→时钟键让位，`dsh-go.md §2.1 chat.header`，`chat_screen.dart:2015-2021`、`chat_header.dart:383-392`）＋输入条下方 `ComposerModelPill`（窄屏/新对话兜底，`chat_screen.dart:2310-2315`） | 输入区工具行第 2 个 **40dp 图标钮**（＋/模型/审批），**不显示当前模型名/档位**；模型名与强度只在 Dialog 里（`taixu.md §2.4 composer.input` 字段 3；§2.8 `model.picker` 简表级） | 无：模型常量写死 `deepseek-flash`（`store.ts:42-43`） | 旧 UI：顶栏下方常驻行「模型 · label」**占布局**（`MA:1041-1053`）；M5：32dp 工具行胶囊，但**底色绑定「深度思考」状态**（`MC:123-142`、`MC:127`）→ 一个胶囊两个语义（L4） | `C-M5-03`（入口形态与宽度） | 采用**输入区文字胶囊**（DSH 形态；myco M5 已在此位置）：显示「模型名 · 档位」，降级照 DSH（先丢档位再丢名）。**拒绝太墟的纯图标钮**：反证＝用户看不到当前模型与档位，每次都要开面板确认；旧 myco 的顶栏文本行证明"显示值"是刚需。**不搬 dsh-Go 的顶栏 chip**：它自己在窄屏都要把 chip 降级/隐藏（`chat_header.dart:383-392`），myco 顶栏已有标题/菜单/操作，再加 chip 只会挤；dsh-Go 的"窄屏回落到输入条"只能说明入口应待在输入区。**胶囊不绑 thinking**（反证：`MC:127` 双语义，L4）；手机宽度上限见 `Q-MODEL-03` | ⏸（宽度上限）／其余 ✅ |
| `model.entry.unavailable` | 无本节点；只有：addressed 子代理会话两入口不可用（`ui-model-selection/index.ts:134-156`）、`locked` 时 trigger disabled（`ModelSelect.tsx:460`）、目录加载失败时 trigger 仍显示当前选择 | 部分：模型行 `disabled`（"模型不可用/无凭据"灰，`model.sheet` 字段 6）；无模型 → **空态＋去配置入口**（字段 10）；保存失败 → 顶栏警告（`chat_screen.dart:1835`） | 未取证 | 无（模型写死） | 后端 `Query.SessionEvents` 已返回 `providerAvailable/modelAvailable`，UI **只取 id/level**（`IB:835-842`、`MA:864-877`）→ 会话模型被删/停用后仍显示旧名，发送才报错（L7） | — | 新增节点（§5）：消费两个已有字段 → 胶囊打「不可用」标记；打开面板时该行 `disabled` 并给原因（照 dsh-Go 行级 disabled，`model.sheet` 字段 6）；无可用模型时给"去配置"入口（照 dsh-Go 空态）。**反证**＝L7：用户写完一屏字才被告知模型没了，且此前无任何提示 | 📌 |
| `model.menu.root` | 根菜单两行（模型/推理等级）**两级钻取**；模型无 `reasoning` → effort 行消失；锚定 trigger 上方 8px、右缘对齐、视口 clamp；卡 `max-height:min(360px,100vh-96)`；选中即提交并关闭、无确认按钮（`ModelSelect.tsx:215-239,437-505`；`ModelSelect.module.css:91-117`） | **底部 sheet 单页分段**：顶部模型 `ListView`＋分组标题，下面「思考强度」「预设」各自独立小节；单击行＝选中并关闭；分组标题可点开/收起；关闭靠拖拽/遮罩；高度按内容、超屏内滚（`dsh-go.md §2.4 model.sheet` 字段 2/4/7，`model_sheet.dart:455-495`） | `ModelDialog`（供应商+模型+思考强度同屏）＋独立 `ChatProviderPicker`（简表级，`ChatScreen.kt:490-512`、`ChatPicker` 文件族） | 无 | 旧 AlertDialog 单屏平铺 + 「关闭」按钮（`MA:1360-1441`；差异表 §4#23）；M5 底部面板单屏平铺、选完即关（`MS:49-148`、`MA:1465-1477`） | `C-M5-02`（形态）／`C-M5-04`（提交语义）／`C-M5-06`（两级钻取 vs 单页分段） | ① **形态＝底部 sheet**（`C-M5-02`）：移动端原生参考 2/3 是模态（dsh-Go sheet、太墟 dialog），DSH 的锚定 popover 是桌面 Web 形态（dsh-mobile 只是给 Web 打补丁，不是原生答案）；myco 旧 M5 已是 sheet，改动面最小。② **结构＝单页分段**（`C-M5-06`）：移动端 3/3 平铺（dsh-Go 分段＋分组折叠、太墟 dialog、旧 M5），反证＝两级钻取在手机上每换一个维度要退回根层，而 sheet 高度足够放"模型分组（折叠）＋强度小节"；模型多时用分组折叠＋内滚，不是加层级。③ **提交＝选中即关闭并生效**（`C-M5-04`，3/3 移动端＋DSH 一致）；反证＝旧 UI 的「关闭」不撤销任何写（`MA:1385`），只会让用户以为能反悔 | 📌（结构、提交）／⏸（形态 → Q-MODEL-02） |
| `model.menu.model-row` | provider 分组（DeepSeek 账号→官方→其它，`provider-order.ts:8-12`）；行 34px、选中＝尾部对勾（不用底色）、待定＝状态点、高亮≠选中；组头吸顶；内滚（`ModelSelect.tsx:480-608`；`ModelSelect.module.css:216-331`）；切换模型把档位重置为新模型 `defaultEffort`（`directory.ts:157-194`） | 分组标题＋行（`model_sheet.dart:455-495`）；行 `minimumSize` 44+；**行尾「当前/锁定」标记**；`disabled`（模型不可用/无凭据）；`busy` 转圈；模型超多内滚；空态给"去配置"入口（§2.4 字段 3/6/10） | `ChatModelPicker`＋`ModelIcon`／`ProviderBadge`（简表级：模型行带图标与供应商徽标）；供应商由 `ChatProviderPicker` 单独选 | 无 | 旧：provider 分组＋RadioButton 行，**整行与 RadioButton 两个命中区同动作**，副标显示 `id · ctx · out`（`MA:1371-1408`）；`chooseModel` 强制 `level=m.reasoningDefault`（`MA:1385`）；M5：扁平行＋provider 名副标（`MS:100-123`） | `C-M5-07`（行信息密度） | 采用 DSH＋dsh-Go 合并：分组标题＋**整行一个命中区**（反证：旧 UI 双命中会让点行与点单选产生两条事件路径）＋选中对勾＋`disabled` 状态（dsh-Go）＋空态去配置。**切换模型重置档位**：DSH 与旧 myco 一致（`directory.ts:157-194`／`MA:1385`），dsh-Go 未细证 → 采纳（📌）。**不显示 ctx/out 副标**（`C-M5-07`）：反证＝ctx 是 provider 配置值、与真实上下文不同源（`dsh-settings.md` `settings.models.model-advanced` 字段 10），行内显示会被误认为"本轮用量"或"可在此改"；需要看容量去 `settings.models`（三家一致把容量放设置页） | 📌 |
| `model.menu.effort-row` | 档位词表由 Host catalog 下发（客户端无词表，`ModelSelect.tsx:19-21,129-140`）；无 `defaultEffort` → 首项「Default」＝不指定、交 provider 默认；selected＝对勾；无 reasoning → 该层不可达（`model.menu.effort-row`） | 面板内「思考强度」**独立小节**（`model_sheet.dart`），行为＝"思考强度 → 改本轮参数"（§2.4 字段 5；**行/段/滑杆形态未细证**） | `ReasoningEffortSlider`：**滑杆**（`ChatScreen.kt:600-605`；工作台 sheet 里也有一个，`:594-610`）；供应商+模型+强度在同一个 Dialog（简表级） | 无档位维度；只有布尔「深度思考」toggle（`chat.input.toggles`，`svc/settings.ts:127-135`） | 旧：「思考强度」FilterChip 横排（默认+levels，`MA:1409-1436`）；M5：横向 pills，点选即提交并关面板（`MS:127-140`、`MA:1478-1488`） | `C-M5-08`（档位控件形态：列表 vs 滑杆） | 采用**离散列表/胶囊**，不用滑杆（`C-M5-08`）。理由：① myco 的 efforts 是后端任意 Map（`MD:58-62`），不是有序数值——滑杆无法表达"Default／任意自定义档"，只能假装线性；② DSH 与旧 myco 都是离散项（2 对 1）；③ dsh-Go 的形态未细证，不能当滑杆先例。**保留「Default」条件项**（`ModelSelect.tsx:129-140`），语义是"不指定档位"，不写成"关闭思考"（反证＝L2：Default 在 myco 后端仍回落到 `model.reasoning.default`，`IB:483`）。**档位词表不许客户端写死**（反证＝`MA:1977` 写死 4 档导致自定义档无法编辑）；显示名来源未取证 → `Q-MODEL-01` | 📌（控件形态）／⏸（Default 语义、显示名 → Q-MODEL-01） |
| `model.menu.search` | 仅 `choices.length>4` 出现；本地过滤（有序子序列，前缀>对齐分>目录序）；焦点留搜索框、↑↓ 虚拟高亮、Enter/Tab 接受；清除 24px（`ModelSelect.tsx:106-115,509-537`；`ModelSelect.module.css:158-214`）。dsh-mobile：清除钮 44×44、输入右 padding 48、菜单行 44（`dsh-mobile.md` `popover.mobile-constraint`） | 未取证（模型 sheet 未见搜索框；用**分组折叠＋内滚**处理长列表） | 未取证 | 无 | ❌ 无搜索（`MA:1360-1441`；差异表 §4#23） | — | 采纳 DSH 条件出现＋交互模型（焦点必留输入框，否则 Enter 会先触发行 click）；**同时采纳 dsh-Go 的分组折叠**（两者不互斥：模型少靠折叠，多了才出搜索）。命中区按 Q-03（dsh-mobile 44）→ Q-03 拍板前不实现矮行。反证＝无搜索/无折叠时模型 20+ 要滚过整屏才能找到目标 | 📌（随 Q-03） |
| `model.menu.load-retry` | 整体错误条＋Retry；部分 provider 失败→警告条（列 provider 名与消息）＋Retry 重跑全部；loading 时条被「正在刷新…」替代；只在 `lastAction==='load'` 时显示（选择失败走 Toast）（`ModelSelect.tsx:77-84,144-147,370-384,538-552`） | **无模型 → 空态＋去配置入口**（`model.sheet` 字段 10），未给"加载失败重试"证据 | 未取证 | 无 | ❌ 无（M5 为空时只剩开关与文案；旧 UI provider 全停时对话框空白，D11） | — | 采纳 DSH（加载失败可重试）**并补 dsh-Go 的空态去配置**：两者是不同错误类别（目录加载失败 vs 根本没有模型），不互斥。反证＝空白菜单无解释（旧 D11 现象）；若选择失败也显示菜单内 Retry，用户会以为要点它才能撤销已提交的选择（DSH 字段 13 反证原文） | 📌 |
| `model.menu.dismiss` | 外点关闭；焦点移出 trigger+menu 关闭；Esc 先退层再关；关闭回焦 trigger；每次打开重置 pane（`ModelSelect.tsx:149-159,260-264,297-360,362-368`） | sheet 关闭＝**拖拽／点遮罩**（`model.sheet` 字段 3）；无"逐层退"（单页结构不存在层） | 未取证（Dialog 关闭未核实） | 无 | M5：ModalBottomSheet 下滑/点外部关闭（`MS:62`＋`MA:1489`，MISSING-1 补登记）；旧 AlertDialog 点外部/返回（`base.primitives.dialog-dismiss`） | `C-M5-02` | 随形态：拍**底部 sheet** 后照 dsh-Go／旧 M5（拖拽＋遮罩＋返回键）。Android 返回键优先级引用已登记的 **Q-MOB-04**（`dsh-mobile.md §6`），不另起编号。反证＝若不做"返回键先关 sheet"，用户按一次返回会直接退聊天页，丢失"我只是想改模型"的意图 | 📌（随形态） |
| `model.submit.feedback` | 待定：trigger `StateDot`＋目标行状态点，trigger 与相关行 disabled；成功＝关闭＋回焦（无焦点环）；失败＝composer 卡锚定 Toast；同值只关闭不写（`ModelSelect.tsx:370-400,472-474,592-596,634-640`） | `busy` 转圈（行/面板）；`selected` 主色勾/底；**保存失败 → 顶栏警告**（`chat_screen.dart:1835`）；选择幂等 | 未取证 | 无 | ⚠️ 提交中无 loading 态（差异表 §4#23）；失败＝面板顶部错误文本（`MA:1386-1388`） | `C-M5-05`（提交失败反馈位置） | 分层（不互斥）：① **动作级**（这一次选择）→ pending 行内转圈＋失败 Toast（DSH；反证：旧 myco 把选择失败放面板内，用户会以为要再点一次才能撤销）；② **会话级**（写后端/同步失败）→ 顶栏持久警告（dsh-Go）。**同值只关闭不写**（DSH；反证：重复提交会闪一次 pending）。**成功后回焦但不显焦点环**（鼠标/触屏都看不到"刚点过"的框） | 📌（C-M5-05） |
| `model.thinking-switch` | **无此控件**；"思考"由档位表达（`dsh-settings.md §5`、§0#6） | **无**：面板只有「思考强度」小节，没有布尔开关（`dsh-go.md §2.4 model.sheet` 字段 2/5） | **无**：只有 `ReasoningEffortSlider`（简表级） | 有布尔 toggle「深度思考」（`chat.input.toggles`），值在**创建回复时快照**（`store.ts:264-277`）；但它的模型写死、无档位维度，所以不是双真源 | **双真源**：发送同时传 `thinking` 与 `reasoningLevel`（`MA:962-968`），后端 level 非空时覆写 thinking（`IB:490-494`）→ 关掉开关无效（BUG-003）；作用域分裂：`thinking` 全局 prefs（`MA:741-743`）vs `reasoningLevel` 会话 sidecar（`IB:190-201`）→ L3 | `C-M5-01`（布尔开关 vs 档位唯一真源） | **删除布尔开关，唯一真源＝档位（会话级）**。三家参考（DSH／dsh-Go／太墟）都没有布尔开关——这是 3/3 一致的高置信结论；反证＝保留它必然与 level 覆写冲突（BUG-003 现象），且 `thinking` 与 `level` 作用域不同（L3）会让"换会话后开关还在、强度却变了"。chat-demo 有布尔开关只因它没有档位维度，不能作为 myco 的先例。「不思考」如何表达（off 档 / Default、composer 要不要留一键快捷）见 `Q-MODEL-01` | ⏸（Q-MODEL-01） |
| `model.preset.dimension` | **面板无此维度**；preset 在 hero chip（只暂存，空白会话才应用）＋设置页设默认＋会话头只读标签；会话一旦开始 Host 拒绝切换（`dsh-settings.md §5`、§2.6；`seat-store.ts:193-247`） | **面板有**：末尾「预设」独立小节，选中＝改 agent preset（`dsh-go.md §2.4 model.sheet` 字段 2/5；Host 侧约束未取证） | 面板无：预设归设置「功能」页（`taixu.md §2.8 settings.features`） | 无 | ❌ 无（core 无 preset 类型/命令：`SessionCreate(preset=…)` 只有默认 `chat-default`，legacy-myco §3 缺口 5） | `C-08`（维度取舍） | **面板布局按 dsh-Go 预留第三小节位，但首版功能不做**。理由必须换成"后端能力"而不是"结构上不该放"——dsh-Go 已经证明移动端模型面板可以容纳预设小节；真正阻塞是 myco core 没有任何 preset 命令（legacy-myco §3 缺口 5），连"选中/应用/被拒"都实现不了。**不学 DSH 的"会话前 hero chip"**：myco 首版不做 preset，"会话开始前才可选"这条限制因此不需要 UI 载体。待后端有 preset 能力时，按 dsh-Go 在面板末尾补一节（保留位置，不改结构） | ✅（首版不做＋预留） |
| `model.context.dimension` | **面板无**；只在 `settings.models` 逐模型编辑（`contextWindow`/`maxTokens`，K/M 文本，空＝继承）；切换模型不改它（`dsh-settings.md §5`、§2.3） | **面板无**；`settings.models` 有独立上下文页（`model_context_page.dart`，`dsh-go.md §2.10`） | **面板无**；`settings.models` 编辑表单含 maxTokens/context（简表级） | 无 | provider editor 12 字段含模型 ctx/max（`MA:1916-2030`）；旧选择器行内只读 `id · ctx · out`（`MA:1393-1406`） | `C-08` | **面板不出现**（3/3 参考一致，高置信）；容量继续在 `settings.models` 改。反证：它写 provider/模型层配置（`ModelRow.tsx:86-111`），在会话内改会让同一会话前后轮上下文窗口不一致；旧选择器的只读副标按 `C-M5-07` 不采纳 | ✅ |
| `model.command-popup` | 第二入口 `/model` 弹窗：与输入区座位**同一 state、同一目录**；一列模型（分组、fuzzy 搜索、失败行不可选、当前值 active）；**无 effort 维度**；任一处切换另一处下次显示新值（`dsh-settings.md §5`；`ui-model-selection/index.ts:51-101,124-166`） | 未取证（有 `composer.slash` 斜杠面板，但 `/model` 命令未证） | 未取证（有 `SlashCommandPopup`，`/model` 未证） | 无 | ❌ 无 slash 命令面（core 无命令，legacy-myco §3 缺口 3） | `C-08`（双入口部分） | **首版不做**（→ §4）。若后续做：必须与胶囊共享**同一会话级模型状态**——反证＝DSH 用同一 `ModelDirectory` 实例（`ui-model-selection/index.ts:1-12,168-190`）正是为避免"胶囊显示 A、命令弹窗显示 B"。前置＝composer 先有 slash 命令面（M4） | ✅（首版不做） |

> **新冲突索引（`C-M5-01…08`，均未登记；建议 Lead 顺延登记为 C-20 起）**：
> `01` 布尔「深度思考」开关（旧 myco `MA:962-968`＋`IB:490-494`）vs 档位唯一真源（DSH／dsh-Go／太墟 3/3 无布尔开关）｜
> `02` 面板形态：桌面锚定 popover（DSH `ModelSelect.tsx:215-239`）vs 移动端模态（dsh-Go 底部 sheet／太墟 dialog／旧 M5 sheet）｜
> `03` 入口形态：文字胶囊（DSH；dsh-Go 顶栏 chip＋composer pill）vs 纯图标钮（太墟 40dp，无当前值）＋手机宽度上限（`45cqw` vs dsh-mobile 112px）｜
> `04` 提交语义：选中即提交并关闭（DSH／dsh-Go／M5）vs 面板内「关闭」确认（旧 `MA:1360-1441`）｜
> `05` 提交失败反馈：菜单内错误条（旧 `MA:1386-1388`）vs 动作 Toast（DSH）vs 顶栏持久警告（dsh-Go `chat_screen.dart:1835`）｜
> `06` 面板结构：两级钻取（DSH）vs 单页分段＋分组折叠（dsh-Go／太墟／旧 M5）｜
> `07` 模型行信息：只名＋对勾（DSH）vs 附 `id · ctx · out` 副标（旧 `MA:1393-1406`）｜
> `08` 档位控件形态：离散列表/胶囊（DSH／旧 myco）vs 滑杆（太墟 `ReasoningEffortSlider`）。

> **三处「影响面」结论**（任务书第 3 问）：① **不影响已发消息**——myco 发送时把 `providerId/modelId/reasoningLevel` 随 `ChatSend` 快照（`MA:962-968`），chat-demo 同样在创建回复时快照（`store.ts:264-277`），DSH 未见改写历史（`directory.ts:17-35`）；② **切换模型重置档位**——DSH 与旧 myco 一致（`directory.ts:157-194`／`MA:1385`）；③ **作用域＝会话级，不跨会话共享**（DSH `directory.ts:17-35`；旧全局 prefs thinking 属缺陷 L3，删除）。`dsh-Go` 写"思考强度改本轮参数"，是"本轮/会话级"表述差异，未细证（→ §6.1）。

---

## 3. 补充项（不互斥的差异，不进冲突列）

| 节点 ID | 谁有、谁没有 | 是否采纳 |
|---|---|---|
| `model.menu.model-row`（分组标题**可展开/收起**） | dsh-Go 有（`model.sheet` 字段 4"单击分组＝展开/收起"）；DSH 只有吸顶组头、无折叠（`ModelSelect.tsx:196-200`）；旧 myco 分组不可折叠；太墟未取证 | ✅ 采纳（长列表用折叠替代层级；反证：模型多时不能只靠滚动，折叠能让用户先看 provider 概览） |
| `model.menu.model-row`（空态 + 去配置入口） | dsh-Go 有（`model.sheet` 字段 10）；DSH 只有"暂无可用模型"文案；旧 D11 是空白对话框 | ✅ 采纳（反证：新用户没配 provider 时看到空白菜单，不知道下一步去设置——旧 D11 已记录该缺陷） |
| `model.menu.model-row`（行级 `disabled`：模型不可用/无凭据） | dsh-Go 有（字段 6）；DSH 只有失败行不可选；太墟未取证 | ✅ 采纳（与 `model.entry.unavailable` 同一字段消费；反证：可点的灰色行会浪费一次提交并报错） |
| `model.submit.feedback`（保存失败顶栏持久警告） | dsh-Go 有（`chat_screen.dart:1835`）；DSH 失败走 Toast（不持久）；旧只有面板内文字 | ✅ 采纳为"会话级失败"通道（与动作级 Toast 分层，见该行建议） |
| `model.menu.search`（键盘全链路：↑↓／Enter／Tab／Esc／Shift+Tab） | DSH 有（`ModelSelect.tsx:297-360`）；dsh-Go 只有键盘事件旁证（`composer.slash`/`mention` 有方向键处理，模型 sheet 未见）；太墟未取证 | ✅ 采纳 DSH（反证：外接键盘与读屏用户若没有虚拟高亮＋Enter 接受，面板不可操作；G-08 同族：状态必须可播报） |
| `model.menu.search`（同值选择只关闭不写） | DSH 有；旧 myco 重复提交（幂等但会闪 pending） | ✅ 采纳 DSH |
| `model.entry.trigger`（dsh-Go 的"顶栏 chip → 窄屏回落输入条"迁移） | dsh-Go 有（`chat_header.dart:383-392`、`chat_screen.dart:2015-2021,2310-2315`）；DSH 用容器查询降级；太墟固定图标钮 | ❌ 不采纳"两处入口"：myco 入口固定在输入区工具行（M5 现状），顶栏已有标题/菜单/操作；反证＝dsh-Go 自己都要按宽度在两处之间迁移，说明"顶栏常驻 chip"在窄屏不可行，myco 没必要引入第二个入口与迁移逻辑。**只借它的降级思想**（见 `Q-MODEL-03`） |
| `model.menu.model-row`（太墟的独立供应商选择器 `ChatProviderPicker`＋`ProviderBadge`） | 太墟有（简表级）；DSH 与旧 myco 都是 provider 分组标题（`provider-order.ts`／`MA:1372-1376`） | ❌ 不采纳独立供应商选择器：那会把"选模型"变成"先选供应商再选模型"两级（正是 `C-M5-06` 要避免的层级），而分组标题已能表达归属；`ProviderBadge` 属视觉项，交 `base.primitives` |
| 旧 myco 特色：模型行显示 `ctx · out` | 旧选择器有（`MA:1393-1406`）；三家参考都无 | ❌ 不采纳（`C-M5-07`：容量属 provider 配置层，会把"配置值"误当"本轮用量"） |
| 旧 myco 特色：顶栏「模型 · label」行 | 旧 UI 有（`MA:1041-1053`）；DSH 无、dsh-Go 顶栏 chip 会按宽度迁走、太墟无 | ❌ 不采纳（常驻吃一行高度，见 `model.entry.trigger`） |
| `model.menu.dismiss`（"返回键先关面板"优先级） | dsh-Go：`PopScope` 顺序＝右面板→多选→抽屉（`dsh-go.md §0#8`），面板关闭优先于页面；太墟：返回键 5 级优先链（`taixu.md §0#6`），浮层永远先于导航栈；DSH 无返回键概念（Web） | ✅ 采纳"浮层优先于页面"（引用已登记 `Q-MOB-04` 的优先级链；两家移动端参考一致） |

---

## 4. 明确不做（参考里有的）

| 能力 | 出处 | 不做的理由 |
|---|---|---|
| agent preset 全链路（DSH 的新会话 hero chip、会话头只读标签、设置页 roster／设默认、Creator、YAML 查看器、Add-plugin 菜单项） | `dsh-settings.md §2.6`（`agent-preset.hero-chip`、`settings.agent-preset.card/creator/help/view`、`agent-preset.plugins-add-item`） | myco core **没有任何 preset 类型/命令**（legacy-myco §3 缺口 5：`SessionCreate(preset=…)` 只有默认 `chat-default`），无法实现"选中/应用/被 Host 拒绝"任一环节。**首版不做功能，但面板布局按 dsh-Go 预留小节位**（→ `model.preset.dimension`）；等后端能力再补 |
| 上下文长度在模型面板内可改 | `dsh-settings.md §5`「面板没有此维度」＋`settings.models.model-advanced`；dsh-Go `settings.models`（含上下文页）；太墟 `settings.models` | 三家参考都把容量放设置页（3/3），它写 provider/模型层配置，与"会话内选模型"不同作用域；放面板会诱导用户以为改的是本会话上限 |
| `/model` 命令弹窗（第二入口） | `dsh-settings.md §5` 第 9 行；`ui-model-selection/index.ts:51-101` | myco core 无 slash 命令面（legacy-myco §3 缺口 3：`BR:13-117` 无相关命令）；dsh-Go/太墟 有斜杠面板但 `/model` 未取证。双入口成立的前提是共享同一目录实例（`index.ts:1-12`），没有命令面时只做单入口更稳。若用户要求首版补，先由 M4 落 composer 命令面 |
| 温度/采样参数等第三维（在**模型面板**内） | DSH 面板无（`dsh-settings.md §0#6`）；太墟把它放在 `settings.models` 编辑表单（temperature/maxTokens/context，简表级）；myco `ChatSend` 无 temperature 字段（`MA:962-968`） | 三家面板都没有第三维；myco 后端也没有该字段 → 面板不设计。若日后要暴露，放 `settings.models`（太墟先例）并先给后端字段 |
| 定价/额度/上下文用量汇总卡（面板内） | DSH 面板无汇总（`dsh-settings.md §5`）；dsh-Go/太墟 面板也无且未取证 | 参考里没有；做它需要新的数据源（价格/额度），本路未取证，不推测 |

---

## 5. 新增节点（参考里都没有、myco 要新设计）

| 节点 ID | 为什么需要 | 依据（用户要求 / 平台规范 / 我方约束） |
|---|---|---|
| `model.entry.unavailable`（会话当前模型不可用态） | 旧 myco 缺陷 L7：后端已返回 `providerAvailable/modelAvailable`，UI 未消费 → 用户看到旧模型名、发送才报错（`IB:835-842`、`MA:864-877`）。dsh-Go 只有"行级 disabled／无模型空态"，没有"当前会话模型已失效"的入口级提示 | **我方约束 + 缺陷修复**：字段已存在（无需新能力），属接线；反证＝L7 现象。验收：删掉 provider 后胶囊立即标不可用、面板该行不可选并给原因（真机） |

> 说明：模型面板在 `C-08` 上的结论是**减少**维度（三合一 → 两维），不是新增；唯一新节点是上面这个入口状态。

---

## 6. 待确认（阻塞项）

> 编号 `Q-MODEL-nn`；Lead 汇总后并入 `open-questions.md`。与已有编号交叉的条目已注明（Q-SET-06/07/08、Q-LEG-10、Q-03、Q-MOB-04），避免两套编号。

| ID | 问题 | 两解及各自后果 | 建议 |
|---|---|---|---|
| `Q-MODEL-01` | **「不思考」怎么表达？composer 要不要留一个一键关思考的快捷开关？**（= BUG-003 的修复口径，与 Q-LEG-10 同题） | 事实：后端 level 非空时覆写 thinking（`IB:490-494`）；旧 provider editor 档位写死 `off/low/medium/high`，`off` 映射空串（`MA:1977`、`MA:2074`）→ myco 语义里 `off` = 关；**三家参考都没有布尔开关**（DSH `§5`、dsh-Go `model.sheet`、太墟只有滑杆），档位形式＝列表（DSH）／小节（dsh-Go）／滑杆（太墟）；myco efforts 是任意 Map、客户端无显示名词表（`MD:58-62`，未取证）<br>**A. 档位唯一真源**：`off` 档即"不思考"，无 `off` 档时给「Default（交给 provider）」，composer 不留独立开关。后果：无价差/无无效控件；代价＝关思考要开面板，且模型无 off 档时无法彻底关闭（只能 Default）。<br>**B. 保留 composer 一键开关，但写入同一档位**（off ↔ 上次档）。后果：一键关思考、手机友好；代价＝需定义"再打开回到哪个档"（上次档/模型默认），且模型无 off 档时开关必须置灰（否则又成 BUG-003） | **A（首版）**，B 登记为后续增强。理由：A 是唯一能从契约上消掉双真源的解；B 成立前置是"模型有 off 档"，该条件未取证。**同时需拍**：档位显示名从哪来（客户端词表 / 后端下发）——DSH 由 Host 下发（`ModelSelect.tsx:19-21`），dsh-Go/太墟 未取证，myco 目前只有 apiValue |
| `Q-MODEL-02` | **面板形态：锚定 popover / 底部 sheet / 对话框？**（`C-M5-02`） | **A. 锚定 popover**（DSH `ModelSelect.tsx:215-239`；dsh-mobile 在 Web 上沿用并约束 `width:min(240px,100vw-16px)`、`max-height:min(60dvh,100dvh-120px)`）。后果：与胶囊空间关系明确、不遮草稿；代价＝240dp 宽对中文模型名只能 ellipsis，且要处理键盘/贴边 clamp。<br>**B. 底部 sheet**（dsh-Go `model.sheet`：`sheet-overlay`、高度按内容、超屏内滚、拖拽/遮罩关闭；旧 M5 已是 sheet）。后果：全宽、可容纳模型分组＋强度小节、拇指易达；代价＝盖住输入区（改模型时看不到草稿与附件），需要"关闭后回焦"与"返回键先关 sheet"。<br>**C. 对话框**（太墟 `ModelDialog`）。后果：居中、可同时放供应商/模型/强度；代价＝手机上对话框高度受限、不如 sheet 便于长列表内滚（太墟简表级，未给内滚证据） | **B（底部 sheet）**。理由：移动端原生参考 2/3 是模态（dsh-Go／旧 M5；太墟 dialog 亦属模态），DSH 的 popover 是桌面 Web 形态；myco 旧 M5 已是 sheet，改动面最小；且 sheet 能用全宽承载"分组折叠＋强度小节"。**必须补**：返回键先关（Q-MOB-04 优先链）、关闭后回焦触发胶囊 |
| `Q-MODEL-03` | **手机端入口形态与宽度上限**（`C-M5-03`）：文字胶囊多宽、怎么降级？命中区是否 44dp？ | **A. 文字胶囊＋`max-width:112dp`＋44dp 高**（dsh-mobile `composer.tools.row`：`max-width:112px !important; min-height:44`；该仓库 S1 主策略"视觉直接 44"；回归记录：不限宽会把发送钮挤出屏幕）。后果：工具行单行不挤爆；代价＝中文模型名基本只剩图标，信息靠 tooltip/aria。<br>**B. 文字胶囊＋`min(360px,45cqw)`＋DSH 降级序列**（`ModelSelect.module.css:45-69`）。后果：390dp 屏≈175dp，模型名可读；代价＝与"单行 nowrap＋44 按钮＋多触发器"直接冲突。<br>**C. 纯图标钮**（太墟 40dp，不显示当前值）。后果：最省宽；代价＝用户看不到当前模型/档位（太墟靠 Dialog 才能确认） | **A＋DSH 降级序列**（先丢档位→再丢名→只图标）。理由：工具行是单行 nowrap（dsh-mobile `M/:459`），112dp 是唯一能保证发送钮完整可见的取值；`45cqw` 是桌面容器查询值，没有移动端 E0 支持；C 被旧 myco「模型 · label」存在本身否证（用户需要看到当前值）。命中区：胶囊 44dp、sheet 行 44dp（dsh-mobile S1／dsh-Go 行 44+），**随 Q-03 一并拍**（Q-03 未定前不实现矮行） |

> **本面板阻塞项：3 条**（`Q-MODEL-01/02/03`）。
> 备案（不阻塞）：`/model` 第二入口已在 §4 给出结论（首版不做，前置＝composer slash 命令面，需 M4 先落）；若用户要求首版做，此项升级为阻塞并同步 M4。

### 6.1 未取证清单（G-00：ref 没有 = 不许猜）

| # | 未取证项 | 影响 | 处理 |
|---|---|---|---|
| 1 | dsh-Go「思考强度」小节的控件形态（列表行/分段/滑杆）与档位来源 | `C-M5-08` 只能靠 DSH＋旧 myco 定"列表"，dsh-Go 不能算滑杆先例 | `dsh-go.md §2.4 model.sheet` 只写"独立小节"；需补 13 字段细证或真机确认 |
| 2 | dsh-Go 写"思考强度→改本轮参数"：是**本轮**还是**会话级** | 影响"切换是否影响下一轮/历史轮"的口径（本表暂按会话级，DSH／旧 myco 一致） | 需补证 `model_sheet.dart` 写入路径；PS：若真是"仅本轮"，会与"切换模型重置档位"叠加出第三种语义 |
| 3 | dsh-Go 面板内预设小节与会话中切换的 Host 约束 | 若后端允许会话中换 preset，`C-08` 的"必须会话前选"不成立 | `dsh-go.md` 未写该约束；需 P4 真机/后端确认 |
| 4 | 太墟 `model.picker` 的 13 字段（P1 明确只登记简表级） | 太墟列的证据强度低于其他参考；`C-M5-06`（结构）不能算太墟一票 | `taixu.md §2.8` 声明"P1 不展开、避免伪造精度"→ 若需要，按 13 字段补 |
| 5 | 太墟 effort 滑杆的档位序来源（是否固定枚举/数值） | 决定 `C-M5-08` 的反证是否成立（myco efforts 是任意 Map） | 需补 `ReasoningEffortSlider` 取值来源 |
| 6 | `/model` 命令在 dsh-Go／太墟 是否存在 | `model.command-popup` 的"未取证"是否应改"无" | 两仓库都有 slash 面板（`composer.slash`／`SlashCommandPopup`），但 `/model` 命令未证 |
| 7 | 旧 myco「会话模型不可用」字段的消费方式 | `model.entry.unavailable` 的表现形式 | `IB:835-842` 只证明字段存在；UI 该显示角标/红字/禁用未取证 → 首版取最小（角标＋文案），P4 真机定 |

### 6.2 需 Lead 修正的文档冲突（我不改这些文件）

| # | 文档 | 冲突内容 | 建议 |
|---|---|---|---|
| 1 | `10-panels.md:16` | 注册表把 `model` 定义为「模型 + 思考强度 + **agent preset**（合并面板）」——现在 dsh-Go 的面板确实含预设小节，但 myco 后端无 preset 能力，本表结论是「布局预留、功能不做」 | 改为「模型 + 推理等级（两维；预设小节预留，首版不做）」 |
| 2 | `p2/_plan.md` M5 行 | 原定输出 `p2/model-attachment.md`，实际按 Lead 本次指令拆为 `p2/model.md` + `p2/attachment.md` | 更新 `_plan.md` 输出列 |
| 3 | `dsh-settings.md §4` | Q-SET-06（模型面板要几个维度）/07（是否明示仅影响后续回合）/08（当前生效汇总）与本面板 `Q-MODEL-01/02/03`、§3、影响面结论重叠 | 合并编号时以本表为准，Q-SET 条目改为"已并入 Q-MODEL-nn / §2 结论" |
| 4 | `docs/spec/ref/` | `dsh-go.md`、`taixu.md` 在本次合并过程中才落库（时间戳晚于任务启动），此前 `_plan.md` 的"P1 十份事实库全部完成"表述与实际不同步 | Lead 确认 ref 已齐；本表已按其 E0 内容填列（太墟模型面板标简表级） |
| 5 | `composer`（M4，已产出 `composer.md`） | **边界确认**：M4 把 `model.trigger` 归给 model（M5），并拥有输入区工具行座位与布局（`composer.md` §1/§2 节点表）；本表 `model.entry.trigger` 定义胶囊内容/降级/打开面板，宽度上限依赖 composer 的 tools row 约束（`Q-MODEL-03`）。另外 M4 已把 `composer.toggle-thinking` 判为"移出 composer、归 model 面板"（同引 C-08/BUG-003） | 切分成立、无需改：composer 拥有**座位**（槽位/间距/单行 nowrap/44dp 下限由 tools row 约束），model 拥有**胶囊内容与面板本体**；`composer.toggle-thinking` 按 M4 结论删除，思考状态由本表档位表达（`C-M5-01`）。**`C-M5-01` 已被 M4/M5 两个合并任务独立确认，Lead 可直接合并登记** |

---

## 7. 自检

- [x] 每个节点在对照表里有一行（13 个节点 → 13 行；`model.entry.unavailable` 同时在 §5 登记）
- [x] 每条「建议」都引了 G 规则或给了反证（G-01/G-08 同族、其余均为 `文件:行` 反证；无主观化表述）
- [x] 没有 E2 推测出现在 §2/§3（dsh-Go 未细证处写「未细证」、太墟标「简表级」、缺失处「未取证」，全集中在 §6.1）
- [x] 明确不做项都写了理由（§4 共 5 条，含 preset 功能、面板内上下文、`/model` 第二入口）
- [x] 阻塞项已编号（`Q-MODEL-01/02/03`，共 3 条）
- [x] 一行一节点；仅互斥项进冲突列（`C-08`＋`C-M5-01…08`）
- [x] 每条建议可执行：都指明"采用谁的做法 + 证据行 + 反证/规则"
- [x] 未修改 `docs/spec/ref/**`、`00-global-rules.md`、`_conflicts.md`、`_merge-*.md`、`p2/_plan.md`；未改 app；未 git commit

---

## 勘误（P3 复核，2026-11-27）

P3 写手回源码核对后指出本文件 §3 的两处事实错误，**以源码为准**：

1. **"dsh-Go 分组标题可折叠"不成立**：dsh-Go 的模型面板实际是**渠道筛选条 + 2 列卡片网格**，无折叠分组（`model_sheet.dart:688-737`）。
   → 本文件据此推出的"折叠分组"结论**证据级别降为我方设计**，不是参考实现做法。
2. **切换模型时档位处理**：本文件写"重置为新模型默认档"（沈 DSH 做法，`ModelDirectory.select`），但 dsh-Go 是**旧档若被新模型支持则保留**（`model_sheet.dart:459-465`）。
   → 我方仍采用 DSH 的"重置"（已写入 `panels/model.md`），此处登记差异供将来改判。

# 旧 myco 事实库（legacy-myco）—— P1 / R6

> 审计对象：本仓库**现有 Kotlin/Compose 实现**（旧 UI + M5 局部新 UI）。只读审计，不改代码。
> 范围：`app-android/src/main/kotlin/`、`app-android/src/main/res/`、`core/src/main/kotlin/`（后者只用于取证"点了之后发什么命令/后端有没有能力"）。**不含 desktop 端**。
> 生成时间：2026-11-27。
> **补扫更新（同日第 2 轮）**：盘点器新增 Kotlin/Compose 专用 kind（`kt.gesture`/`kt.widget`），本目录 facts 已从 158 行补到 **298 行**、
> 节点 53 → **63**、`uncovered=0`。本文正文未重写，**最新数字与归类口径见文末「附2」**（旧数字以附2为准）。

## 方法与分级

- 分母（机器扫描，`tools/ui-audit/ui_audit.py`）：
  - `app-android/src/main/kotlin` → **24 文件 / 158 命中**（`jsx.onclick` 136 + `disclosure` 22）→ `_ws/out/findings-legacy.csv`
  - `core/src/main/kotlin` → **24 文件 / 0 命中**（core 无 UI 代码）→ `_ws/out/findings-legacy-core.csv`
- 对账：`docs/spec/ref/legacy-myco.facts.csv`（158 行 = 107 `E0` + 51 `excluded`）覆盖 **53 个逻辑节点**；
  `uncovered = 0`、`stale = 0`（报告 `_ws/out/coverage-legacy.md`）。core 目录 findings 为 0，单独跑 check 也是 `uncovered = 0`；
  其报告里的 `stale=158` 是"同一 facts 文件被两个 scan root 共用"的已知噪声（core 没有任何 facts 键，也不该有）。
- 分级：`E0` = 本轮已读源码行；`E1` = 文档声称（本轮不采用）；`E2` = 推测，**只出现在 §6**。
- 事实库只登记 scanner 能产出的 kind（`jsx.onclick` / `disclosure`）。**scanner 盲区**（文本输入 `onValueChange`、
  `BackHandler`/`PredictiveBackHandler`、`onDismissRequest`、裸 `.clickable {}`、`withLink` 链接、`Switch`/`Checkbox`
  的自绘版本）在 §1.2 / §2.8 单独登记，不伪造 facts 键（否则对账工具会把它们报成"失效条目"）。

### 路径简写（全文通用；完整路径见 §1）

`MA`=app-android/src/main/kotlin/dev/myco/android/MainActivity.kt · `BH`=…/BridgeHolder.kt · `FG`=…/MycoForegroundService.kt ·
`CC`=…/ui/ChatContent.kt · `CP`=…/ui/Components.kt · `DR`=…/ui/Drawer.kt · `PN`=…/ui/PredictiveNav.kt · `MO`=…/ui/Motion.kt ·
`TH`=…/ui/Theme.kt · `TP`=…/ui/ThemePrefs.kt · `AP`=…/ui/AppPrefs.kt · `FA`=…/ui/Favicons.kt · `UM`=…/ui/model/UiModels.kt ·
`CR`=…/ui/m5/ChatReal.kt · `CS`=…/ui/m5/ChatStates.kt · `MC`=…/ui/m5/M5Composer.kt · `MS`=…/ui/m5/M5ModelSheet.kt ·
`CK`=…/ui/m5/ChatSample.kt · `GL`=…/ui/m5/Gallery.kt · `GS`=…/ui/m5/Glass.kt · `NS`=…/ui/m5/NeoSurface.kt ·
`IC`=…/ui/m5/Icons.kt · `TK`=…/ui/m5/Tokens.kt · `SE`=…/ui/m5/Superellipse.kt ·
`BR`=core/src/main/kotlin/dev/myco/core/bridge/Bridge.kt · `IB`=…/core/bridge/InProcessBridge.kt · `EV`=…/core/session/SessionEvent.kt ·
`SS`=…/core/session/SessionStore.kt · `AS`=…/core/attachment/AttachmentStore.kt · `TR`=…/core/tools/ToolRegistry.kt ·
`CT`=…/core/capability/CapabilityTools.kt · `DS`=…/core/capability/DeepSeekToolsClient.kt · `MD`=…/core/spec/Models.kt ·
`PS`=…/core/provider/ProviderStore.kt · `DI`=…/core/provider/ModelDiscovery.kt · `SST`=…/core/settings/SettingsStore.kt ·
`DO`=…/core/doctor/Doctor.kt · `CCL`=…/core/llm/ChatClients.kt · `CCL2`=…/core/llm/ChatClient.kt。

---

## 0. 一页结论

**交互骨架**：单 Activity（`MA:126`），三屏**层叠**而非路由——① 常驻聊天基座；② 设置层从右滑入覆盖（`MA:429-456`）；
③ 归档/回收站再叠在设置上（`MA:459-476`）。会话导航是同一个 `MycoDrawerContent`：手机=抽屉、宽屏=常驻侧栏（`MA:547-587`）。
聊天页内部再分：顶栏（`CP:56` / `CR:55`）、消息列表 + 过程组 + 来源 + 操作条（`CC:518` / `CR:93`）、输入区
（`CP:359` / `MC:35`）。模型选择是 AlertDialog（旧，`MA:1360-1441`）或 M5 底部面板（`MS:49`）；附件是 ModalBottomSheet
（`MA:1291-1315`）。**M5 不是新导航，只是聊天页的局部换肤**：只有 `m5_chat_ui` intent extra 时才切（`MA:166`），
抽屉/设置/附件/旧模型选择器全部仍是旧实现。

**分母与对账**：158 命中 → 107 E0（53 节点）+ 51 excluded（死代码 17 / 签名行 8 / 原语内部接线 9 / 不可达分支 2 /
非交互 1 / adb-only 调试页 14）；`uncovered=0`。

**最危险的 10 条迁移缺口**（按"会不会丢功能"排序，证据见 §4/§5）：

1. **M5 没有"助手消息版本切换"**：旧 UI 操作条有 `‹ 1/3 ›`（`CC:1489-1494`），M5 操作条只有复制/重新生成（`CR:601-607`）；
   `ChatMessagesM5` 收下了 `onVersionSelected` 形参却**从未调用**（`CR:102`）。迁移到 M5 后会丢失"重生成后回看旧版本"。
2. **M5 不渲染来源区**：旧 UI 在回复下方渲染"来源 N 个网页"并能点开网页（`CC:669-670`、`CC:1169-1235`）；M5 列表完全没有
   `SourcesSection` 调用（`CR:93-248`），且工具卡里的来源只是纯文字不可点（`CR:449-469` vs 旧 `CC:983-997`）。搜索回答的引用链会断。
3. **用户消息「复制」两套 UI 都只弹 Toast、不写剪贴板**（`MA:1323-1326`）：这是共用同一个 AlertDialog（`MA:1317-1336`）造成，
   旧/M5 都受影响 → "复制"功能实际不存在。同一菜单的"编辑并重发"是好的（`MA:1327-1331`）。
4. **附件面板"有入口无内建 UI"**：只有拍照/相册/文件三个系统选择器（`MA:1304-1312`），选中即落盘（`MA:790-817`），
   界面只有文件名 chip（`MA:1228-1233`、`CS:94-121`）；**契约层没有读取附件的 Query、没有删除附件的 Command**
   （`BR:119-144` 只有 `AttachmentSave`；`AS` 无删除 API）→ 不可能做预览/删除/进度，孤儿文件也无法清理。
5. **"深度思考"开关与 `reasoningLevel` 双真源**：发送时同时传 `thinking` 与 `reasoningLevel`（`MA:962-968`），
   后端在 level 非空时用 `apiValue` 覆写 thinking（`IB:478-495`）→ 对声明了默认推理档的模型，UI 上关掉"深度思考"**不生效**。
6. **M5 缺语音输入**：旧 UI 有 🎤 → `TranscribeAudio`（`CP:399`、`MA:826-848`），M5 输入区工具行只有 ＋/深度思考/搜索/发送（`MC:113-173`）。
7. **发送防重入存在窗口**：`send()` 只挡 `streaming != null`（`MA:942-944`），而 `streaming` 要等首个文本 delta 才被置值
   （`MA:726-730`、`MA:888-895`）→ 首 token 前连点会提交两轮（`IB:393-399` 的 session 锁只会把它们排队，不会去重）。
8. **M5 宽屏顶栏是死按钮**：`M5ChatTopBarReal` 永远渲染菜单钮并且 `onClick = onMenu ?: {}`（`CR:63-72`），宽屏传 `onMenu=null`
   （`MA:420`）→ 点了没反应；旧 `MycoTopBar` 在这种情况下不渲染按钮（`CP:65-72`）。
9. **M5 只能靠 adb 打开**：`intent?.getBooleanExtra("m5_chat_ui", false)`（`MA:166`）；`m5_chat`/`m5_gallery` 两个演示页同理
   （`MA:134-158`）。**不能用"仓库里已有 M5 文件"判断迁移完成度**。
10. **过程组折叠有三套实现且行为不一致**：旧 `TimelineHeaderRow/TimelineBody`（`CC:1044-1082`/`CC:742-904`）、
    未被调用的 `TimelineFold`/`ProcessFold`（`CC:1238`/`CC:1376`，死代码）、M5 `ThinkingHeaderM5`（`CR:321-356`）。
    旧 UI 流式过程头强制展开且点击无反应（`CC:676`），M5 可点但默认折叠（`CR:548-589`）。

**用户点名的三处，一句话**：① 导入媒体 = 只有一个三按钮 sheet + 系统选择器，缺预览/进度/删除/最近照片，根因是 Bridge 契约缺读/删；
② 模型切换 = 旧 UI 是 provider 分组列表 + 思考强度 chips（`MA:1360-1441`），M5 是扁平模型行 + 强度 pills（`MS:49-148`），
两者都缺"模型可用性提示/搜索/agent preset"，且"深度思考"与强度档双真源（见缺口 5）；③ 偶发 bug 的可疑代码见 §5.3
（发送竞态、手势双通道、分支切换锚点、内滚仲裁），均为**已读源码可复现的窗口/分支**，不是猜测。

---

## 1. 文件 → 面板归位表

### 1.1 Kotlin 源码（app-android，24 文件）

| 文件（相对 `app-android/src/main/kotlin/dev/myco/android/`） | 行数 | 归位面板/基座 | 说明（E0） |
|---|---|---|---|
| `MainActivity.kt` | 2363 | `chat` / `sidebar` / `settings` / `settings.models` / `attachment` / `model` / `base.layout` | 单文件承载壳 + 全部弹层：三屏层叠 `MA:409-476`；抽屉 `MA:326-395`；设置 `MA:1577-1890`；provider 编辑 `MA:1893-2018`；附件 sheet `MA:1291`；模型选择 `MA:1360`/`MA:1442`；bin `MA:1496`；演示页入口 `MA:134-158` |
| `BridgeHolder.kt` | 49 | `base`（内核装配，无 UI） | in-process bridge 构建 `BH:27-47`；每次 provider 变更后重建 `BH:33-47` |
| `MycoForegroundService.kt` | 62 | `settings`（常驻开关的后端服务） | 前台服务 + 通知 `FG:25-35`；生成期间临时持有 `FG:46-58` |
| `ui/AppPrefs.kt` | 20 | —（**无面板：死代码**） | `AppMode CHAT/WORK` 从未被引用（`AP:6-19`；全仓 grep 仅定义处） |
| `ui/ChatContent.kt` | 1575 | `chat`、`tool` | Markdown 渲染 `CC:238-450`；消息/过程组/来源/操作条 `CC:516-694`；死代码 `CC:1238-1441` |
| `ui/Components.kt` | 428 | `base.primitives` | 顶栏 `CP:56`、SettingsRow `CP:145`、SessionListItem（死）、SearchField `CP:206`、EmptyState `CP:218`、InlineError `CP:255`、ChatBubble `CP:280`、ChatComposer `CP:359` |
| `ui/Drawer.kt` | 197 | `sidebar` | 抽屉内容 `DR:40-126`；会话行 `DR:140-190`；NewChatButton（死）`DR:193` |
| `ui/Favicons.kt` | 79 | `chat`（来源图标） | 站点 favicon 直连抓取 `FA:34-49`；失败退回首字母 `FA:71-78` |
| `ui/Motion.kt` | 41 | `base.primitives`（动效 token） | 按压 spring + scale `MO:19-41` |
| `ui/PredictiveNav.kt` | 178 | `base.layout` | 预测式返回/右滑退出进度源 `PN:34-114`；`swipeRightToExit` `PN:122-177` |
| `ui/Theme.kt` | 195 | `base.theme` | M1 令牌（spacing/radius/扩展色）`TH:28-53` |
| `ui/ThemePrefs.kt` | 18 | `settings.general`（外观） | 主题偏好三态落 SharedPreferences `TP:6-17` |
| `ui/model/UiModels.kt` | 42 | `base.renderer`（展示模型） | `SessionItemUi`/`ChatMessageUi`/`ToolStepUi` 等 `UM:4-41` |
| `ui/m5/ChatReal.kt` | 672 | `chat`（M5） | M5 顶栏 `CR:55`、消息列表 `CR:93`、过程头/行 `CR:321-490`、操作条 `CR:601-607`、回到底部 `CR:610`、死代码 `CR:628-672` |
| `ui/m5/ChatStates.kt` | 171 | `chat`（空态）/`composer`（待发附件）/`base`（错误条） | 空态 `CS:33-55`；附件条 `CS:77-132`；错误条 `CS:136-170` |
| `ui/m5/ChatSample.kt` | 297 | —（**adb-only 样张页**） | 静态假数据样张 `CK:49-112`；入口 extra `m5_chat`（`MA:135,148-158`） |
| `ui/m5/Gallery.kt` | 572 | —（**adb-only 视觉画廊**） | 组件画廊 `GL:52-91`（入口 extra `m5_gallery`，`MA:134,137-147`）；10 处 `onClick={}` 视觉样例 |
| `ui/m5/Glass.kt` | 186 | `base.primitives`（M5 玻璃原语） | `GlassSurface` `GS:47-102`；唯一带 onClick 的产品消费方是画廊 |
| `ui/m5/Icons.kt` | 204 | `base.primitives`（M5 图标） | Canvas 自绘图标 `IC:24-30` |
| `ui/m5/M5Composer.kt` | 176 | `composer`（M5） | 输入区 `MC:35-176`：附件/深度思考入口/搜索/发送停止 |
| `ui/m5/M5ModelSheet.kt` | 187 | `model`（M5） | 模型+思考强度合一面板 `MS:49-148`；自绘 Switch `MS:169-186` |
| `ui/m5/NeoSurface.kt` | 322 | `base.primitives`（M5 新拟物原语） | `NeoSurface` `NS:69-154`；按压/阴影/波纹 `NS:157-322` |
| `ui/m5/Superellipse.kt` | 130 | `base.primitives`（形状） | Lamé 超椭圆 `SE:17-25` |
| `ui/m5/Tokens.kt` | 188 | `base.theme`（M5 令牌） | `M5Palette`/`M5Type`/`M5Space` `TK:22-40` |

> 归位到 `settings.models` 的是 provider 管理（注册表 `settings.models` = 模型与 provider 管理）；
> `settings.agent-loop` / `settings.web-search` / `settings.plugins` / `approval` / `plan` / `goal` / `jobs` / `schedule` /
> `subagent` / `skill` / `deliverables` / `trajectory` / `workspace` / `message-feedback` 在旧 App 中**未见实现**（不补全）。

### 1.2 Android 资源（`app-android/src/main/res`，3 文件，0 可点项）

| 文件 | 归位 | 说明（E0） |
|---|---|---|
| `values/themes.xml` | `base.theme` | 仅一条 NoActionBar 主题继承，无可点项 |
| `xml/file_paths.xml` | `attachment` | FileProvider 只暴露 `cache/camera/`（拍照落临时文件，`MA:852-861`） |
| `xml/network_security_config.xml` | `settings.models` | 默认禁明文，只放行 127.0.0.1/localhost（与"远程 provider 必须 HTTPS"一致） |

### 1.3 core（`core/src/main/kotlin`，24 文件；0 scanner 命中，命令/能力取证源）

| 路径 | 归位 | 说明 |
|---|---|---|
| `dev/myco/core/bridge/Bridge.kt` | 命令面 | **22 个 `Command` + 8 个 `Query` + 7 类 `BridgeEvent`**（`BR:13-227`） |
| `dev/myco/core/bridge/InProcessBridge.kt` | 命令实现 | 每个 Command 的实现与错误码；工具循环/停止/重生成/编辑重发（`IB:68-292`、`IB:398-791`） |
| `dev/myco/core/session/SessionEvent.kt`、`SessionStore.kt` | 数据 | 会话事件（含 reasoning/revises/versions/pinned/archived/draft）；存储/软删/永久删/分支 `EV:13-177`、`SS:85-377` |
| `dev/myco/core/attachment/AttachmentStore.kt` | 附件能力 | 仅 save/read/textContent/exists（**无删除**；大小上限 图片 32MB / 文件 8MB）`AS:23-71` |
| `dev/myco/core/tools/ToolRegistry.kt`、`capability/CapabilityTools.kt`、`capability/DeepSeekToolsClient.kt` | 工具能力 | 本地 `time_now`；DeepSeek 能力工具 `web_search/read_url/ocr_image/transcribe_audio`（限 api.deepseek.com）`TR:59-71`、`CT:20-162`、`DS:74-...` |
| `dev/myco/core/spec/Models.kt`、`Attachment.kt` | 数据 | Provider/Model/Reasoning/Usage 等 schema 运行时类型 `MD:17-139` |
| `dev/myco/core/provider/*`、`settings/SettingsStore.kt` | 配置 | provider 读写 + revision 乐观锁；模型发现 `PS:19-65`、`SST:25-69`、`DI:29-101` |
| `dev/myco/core/llm/*` | 模型客户端 | `ChatClient` 契约 + Fake/OpenAI 兼容/Anthropic 兼容 + 两个未实现方言报错 `CCL2:72-103`、`CCL:7-27` |
| `dev/myco/core/doctor/Doctor.kt`、`MycoHome.kt`、`io/TextFiles.kt`、`session/Ulid.kt`、`spec/UsageCost` 等（见 `UsageCost.kt`） | 基础 | 自检 7 项 `DO:26-88`；目录布局；原子写；id 生成；用量计价 |

---

## 2. 节点事实表（13 字段）

> 分组按 `<面板>.<区域>.<节点>`。**一个节点可以由多处实例实现**（旧 UI/M5/组件原语），"身份"字段列全部实例行号。
> 验收 ID 为本轮临时编号 `LEG-nn`，P3 迁入 `panels/*.md` 时按面板重编（规则见 README §3：一旦分配不改号，故此处只作候选）。
> 13 字段缺失即不合格；本表所有"现状"均来自源码行，未实现的行为写「未见实现」。

### 2.1 `chat`（12 节点）

#### `chat.topbar.menu`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.topbar.menu`。属 chat → 顶栏。父：TopAppBar `navigationIcon`（旧 `CP:64-73`）/ M5 自绘 Row 首钮（`CR:63-72`）。出现条件：手机宽度传入 `onMenu`（`MA:420`）；宽屏旧 UI 不渲染、M5 仍渲染（`CR:67`）。每次 1 个。 |
| 2 | 网格与定位 | 旧：TopAppBar 导航槽位，未自定义尺寸；M5：36dp 圆钮 + 与标题间距 10dp，行 padding h16/v10（`CR:59-73`）。 |
| 3 | 命中区 | 旧 TextButton（Material 默认最小 48dp，代码未声明）；M5 36dp 圆（`CR:64`）→ **小于 44dp 物理最小边**，需按 Q-03 扩边。 |
| 4 | 手势 | 单击。无长按/双击。抽屉打开时系统返回与右滑改由抽屉手势接管（`MA:483-503`）。 |
| 5 | 行为 | `drawerState.open()`（`MA:418`）；M5 同（`MA:420`）。仅本地 UI 状态，**不发 Command**；幂等。 |
| 6 | 视觉状态 | 旧 Material ripple；M5 Raised 新拟物：按压凹陷 + 超椭圆波纹（`NS:112-154`）。无 disabled/selected。 |
| 7 | 布局占位与展开形态 | 不参与展开（非 G-03 三形态）；固定占顶栏高度，不额外占位。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | M5 按压 tween 90ms、回弹 spring(damping .78/stiffness 520)（`NS:157-165`）；旧 ripple。 |
| 10 | 边界态 | 宽屏：旧无钮（常驻侧栏替代）；M5 有钮但 `onMenu ?: {}` = **死按钮**（`CR:67`）。深色/大字号随 M5Type。 |
| 11 | 无障碍 | 旧有文本「☰」但无 contentDescription；M5 Canvas 图标无 contentDescription（`CR:69-71`）→ 现状缺口。 |
| 12 | 依据 | `E0` `CP:56-77`、`CR:55-80`、`MA:418-422`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 手机上无法打开会话导航，等于丢"切会话/看历史"全部入口。**候选** `LEG-01`：手机点击抽屉出现；宽屏不得出现无响应按钮（真机手势 + 截图比对）。 |

#### `chat.msg.user-long-press`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.user-long-press`。属 chat → 消息轮 → 用户气泡。出现条件：每条 user 消息，1 个。父：列表 item（`CC:589-606`、`CR:163-181`）。 |
| 2 | 网格与定位 | 旧：右对齐气泡，`fillMaxWidth(0.86)`，圆角 18/18/4/18（`CP:304-315`）；M5：右对齐，`widthIn(max=300dp)`，圆角 26（`CR:254-262`）。 |
| 3 | 命中区 | 整个气泡（旧 `combinedClickable` 包 Column `CP:328`；M5 包 NeoSurface `CR:258`）。未声明最小 44dp。 |
| 4 | 手势 | 长按 = 打开用户消息菜单；单击 = 显式空实现 `onClick = {}`（`CP:328`、`CR:258`）→ 单击无动作；纵向拖动归列表滚动（touch slop）。 |
| 5 | 行为 | 置 `userMenuTarget = msg` → 弹 AlertDialog（`MA:1317-1336`）。不发 Command；重复长按幂等。 |
| 6 | 视觉状态 | 旧无按压反馈；M5 有 NeoSurface 按压态（`CR:255-262`）。 |
| 7 | 布局占位与展开形态 | 气泡参与布局流；不展开。 |
| 8 | 滚动语义 | 随外层会话列表滚动；无内滚。 |
| 9 | 动效 | 仅 M5 按压动效；长按无动效/无震动反馈（E0 现状）。 |
| 10 | 边界态 | 超长文本自动折行；`messageId == null` 的消息仍可长按，但"编辑并重发"会在 `MA:1004` 早退（无提示）。 |
| 11 | 无障碍 | 无 role/长按动作语义（E0 现状缺口）；TalkBack 不会播报"可长按"。 |
| 12 | 依据 | `E0` `CP:280-356`、`CR:252-289`、`MA:1317-1336`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 用户失去"编辑重发/复制自己消息"的唯一入口（M5 也无替代）。**候选** `LEG-02`：长按弹菜单、空白区长按不触发（真机手势）。 |

#### `chat.msg.user-menu.actions`（复制 / 编辑并重发）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.user-menu.actions`。属 chat → 用户消息菜单。出现条件：`userMenuTarget != null`；1 个 AlertDialog 内含 2 个 TextButton（`MA:1317-1336`；旧/M5 共用）。 |
| 2 | 网格与定位 | AlertDialog `text` 内纵向 Column；按钮 Material 默认间距，未自定义。 |
| 3 | 命中区 | TextButton 默认最小 48dp 高（代码未声明）；两行相邻无额外间距。 |
| 4 | 手势 | 单击。无长按。 |
| 5 | 行为 | ① 复制：`Toast("已复制")` + 关菜单——**未调用剪贴板**（`MA:1323-1326`，缺陷）；② 编辑并重发：`editTarget=msg; editText=msg.text`（`MA:1327-1331`）。均不发 Command（提交时才 `ChatEditResend`）。复制幂等；编辑是本地草稿。 |
| 6 | 视觉状态 | 两行同权重，无 destructive 色。 |
| 7 | 布局占位与展开形态 | Dialog 浮层，不占布局（G-01 同类）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | Material Dialog 默认淡入。 |
| 10 | 边界态 | 超长用户消息 → 编辑框预填全文（`MA:1329`），maxLines=6 内滚（`MA:1342-1347`）。 |
| 11 | 无障碍 | Dialog 标题「用户消息」；按钮文本可读；复制无结果播报（只会 Toast）。 |
| 12 | 依据 | `E0` `MA:1317-1336`、`MA:1003-1021`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 用户无法修正已发消息（只能重开会话）；且当前"复制"是假功能，用户点完粘贴得到旧内容，比没有更糟。**候选** `LEG-03`：点击复制后系统剪贴板=消息全文（真机）；编辑重发后旧消息标"已被后续编辑消息替代"（`CC:222-226`）。 |

#### `chat.msg.edit-dialog.send`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.edit-dialog.send`。属 chat → 编辑消息对话框。出现条件：`editTarget != null`；1 个 AlertDialog（`MA:1337-1359`）。 |
| 2 | 网格与定位 | 标题「编辑消息」+ 多行 OutlinedTextField + 取消/发送（`MA:1337-1358`）。 |
| 3 | 命中区 | 发送 TextButton 默认 48dp；输入框整块可聚焦。 |
| 4 | 手势 | 单击发送；键盘输入；无长按。 |
| 5 | 行为 | `text=editText.trim()`；空文本仅关框不发送（`MA:1352-1354`）；否则 `editResend` → `Command.ChatEditResend(sessionId, messageId, text)`（`MA:1014`）。成功后旧 user 消息标注"已被后续编辑消息替代"（`CC:222-226`），新消息 `revises` 旧 id（`IB:443-457`）。**不可撤销**（旧日志保留但不再进上下文，`IB:793-815`）。 |
| 6 | 视觉状态 | 输入框聚焦高亮；发送无 disabled 态（空文本时点了只关框，无提示）。 |
| 7 | 布局占位与展开形态 | Dialog 浮层。 |
| 8 | 滚动语义 | 输入框内滚（maxLines=6）。 |
| 9 | 动效 | Dialog 默认。 |
| 10 | 边界态 | 空文本：静默关闭（无错误提示）；超长：输入框内滚；请求失败：全局错误条 `error = code: message`（`MA:1015-1018`）+ optimisticUser 回滚（`MA:1017`）。 |
| 11 | 无障碍 | 标题/按钮可读；错误不朗读。 |
| 12 | 依据 | `E0` `MA:1337-1359`、`MA:1003-1021`、`IB:436-457,793-815`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 只能整条重发导致上下文重复与历史错乱。**候选** `LEG-04`：编辑发出后旧消息变灰标注、回答重跑（真机）。 |

#### `chat.process.header-toggle`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.process.header-toggle`。属 chat → 消息轮 → 过程组头。出现条件：该 turn 有过程事件（`CC:619-645`）；每 turn 最多 1 个（`renderedProcessTurns` 去重 `CC:585,621`）。实例：旧 `CC:622-628`+`CC:1070-1081`；M5 `CR:186-194`+`CR:337-345`。 |
| 2 | 网格与定位 | 旧：整行宽、圆角 8dp、底色 surfaceVariant α.75、padding h8/v4（`CC:1070-1076`）；M5：整行宽、padding v5、无底色（`CR:337-346`）。 |
| 3 | 命中区 | 整行（`.clickable(onClick=onToggle)` `CC:1074`；`CR:340-344`）。视觉高约 24-30dp → 需 ≥44dp 扩边（Q-03）。 |
| 4 | 手势 | 单击开合；无长按；竖直拖动归外层（clickable slop）。 |
| 5 | 行为 | `expandedProcess[turn] = expandedProcess[turn] != true`（`CC:626`、`CR:192`）；流式结束自动置开（`CC:563-566`、`CR:136-138`）。本地状态、幂等可逆、不发 Command。 |
| 6 | 视觉状态 | 折叠 ▸ / 展开 ▾；摘要文案（"已搜索网页…/已调用…/思考过程"，`CC:1051-1069`）；无 disabled/hover。 |
| 7 | 布局占位与展开形态 | **形态 B（capped-inner-scroll）**：展开体 `heightIn(max=屏高45%且≤360dp)` + 内滚（`CC:568,639-642,779-780`；`CR:207-215`）；收起高度 0（`CC:629-644`；`CR:195-226`）。展开时下移兄弟。旧 UI 用 expandVertically 高度动画（`CC:630-634`）→ 与 G-10 ">240dp 直接切换"口径冲突，P2 需拍（见 Q-LEG-09）。 |
| 8 | 滚动语义 | 展开体内滚：旧有自定义 pointerInput 吸收并在到边时把**新手势**交外层（`CC:779-835`，注释明确 G-04 意图）；M5 只有裸 `verticalScroll`（`CR:210-224`）→ 到边残余速度会传外层，违反 G-04。 |
| 9 | 动效 | 旧 expandVertically 200ms / shrink 160ms + fade（`CC:630-634`）；M5 expand 200 / shrink 160 + clip（`CR:198-205`）。 |
| 10 | 边界态 | 无过程事件不出现；条目很多 → 内滚封顶；工具失败行显示「失败」（`CC:936`）；流式中 header 显示"正在使用工具…/正在思考…"（`CC:1062-1066`）。 |
| 11 | 无障碍 | 无 `aria-expanded`/disclosure 语义（E0 现状缺口）；点击无朗读状态变化。 |
| 12 | 依据 | `E0` `CC:619-645,1044-1082,742-904,568`、`CR:186-194,321-356,548-589`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 长过程组只能永远展开（会话被撑爆）或永远收起（看不到工具细节）；B 形态不做边缘吸收会让"读过程"变成"滑整个会话"。**候选** `LEG-05`：开合正常；内滚到边不外泄（真机手势 + G-04）。 |

#### `chat.process.line-toggle`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.process.line-toggle`。属 chat → 过程组 → 单条过程行。出现条件：过程组展开后每条 reasoning/tool 各 1 个（`CC:844-901`；M5 `CR:218-223`）。旧 `CC:907-948`；M5 `CR:376-490`。 |
| 2 | 网格与定位 | 旧：底色 surfaceVariant α.75、padding h8/v4（`CC:911-914,927-930`）；M5：padding v3、缩进 20dp、无底色（`CR:402-408`）。 |
| 3 | 命中区 | 整行（`CC:912,928`、`CR:406`）；视觉高 22-28dp → 需扩边（Q-03）。 |
| 4 | 手势 | 单击开合；正文不可点（正文 Text 可长按选中，`CC:366`）。 |
| 5 | 行为 | `expandedTools["$keyPrefix-$index"]` 取反（`CC:851-874`）；M5 `expandedLines["p-$turn-$i"]`（`CR:218-222`）。仅 reasoning/tool 可折叠；`text` 条目直接渲染（`CC:946`）。不发 Command。 |
| 6 | 视觉状态 | ▸/▾ + 状态（进行中/失败/完成 + "已阅读 N 个网页" `CC:933-943`）；M5 用 icon + 标题（"思考/运行中/运行命令/写入" `CR:381-401`）。 |
| 7 | 布局占位与展开形态 | 形态 B 的二级：展开体 `AnimatedVisibility(expandVertically)`（`CC:877-893`）；展开行 sticky（`CC:852-863`）+ 尾部 1dp 推头（`CC:894-899`）；兄弟行移动。 |
| 8 | 滚动语义 | 随过程组内滚（G-04 归上一节点）。 |
| 9 | 动效 | 旧 180/160ms（`CC:880-889`）；M5 180/140ms（`CR:429-438`）。 |
| 10 | 边界态 | reasoning 空文本不渲染（`CC:954,1088`）；长文本截断：旧 args 400/300、结果 1200、reasoning 2000/6000（`CC:968,1133,1143,1286,1105`），M5 4000/4000（`CR:478,516`）。 |
| 11 | 无障碍 | 无展开语义（E0 现状缺口）。 |
| 12 | 依据 | `E0` `CC:844-948,1044-1165`、`CR:376-490`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 要么所有工具参数/结果全量铺开（撑爆会话），要么一步细节都看不到。**候选** `LEG-06`：单行开合不影响其它行状态（G-07 同轮多块互不串）。 |

#### `chat.live.header-toggle`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.live.header-toggle`。属 chat → 流式中过程块。出现条件：`liveEntries` 非空（`CC:674-686`；M5 `CR:240-244,548-589`）。 |
| 2 | 网格与定位 | 旧：`TimelineHeaderRow(expanded=true, onToggle={}, live=true)`（`CC:676`）；M5：`ThinkingHeaderM5` + `liveExpanded` 默认 false（`CR:550-558`）。 |
| 3 | 命中区 | 整行可点；旧行绑了空 lambda（**点了无反应**），M5 有效。 |
| 4 | 手势 | 单击。旧=无效；M5=开合。 |
| 5 | 行为 | 旧不发状态变化（`onToggle = {}` `CC:676`）；M5 `liveExpanded = !liveExpanded`（`CR:558`）。不发 Command。 |
| 6 | 视觉状态 | live 时 M5 PulseDot 呼吸点（`CR:350-353,359-373`）；旧有"正在思考…/正在使用工具…"文案（`CC:1062-1066`）。 |
| 7 | 布局占位与展开形态 | 旧=固定展开、不可折叠（形态 A 变体）；M5=形态 B（`heightIn(max=45%/360dp)+verticalScroll` `CR:571-587`）。 |
| 8 | 滚动语义 | M5 内滚无边缘吸收（`CR:573-587`）。 |
| 9 | 动效 | M5 200/160ms（`CR:560-570`）；呼吸点 700ms infinite reverse（`CR:360-369`）。 |
| 10 | 边界态 | 流式结束 → 整块替换为落盘过程组（`CC:674-693`、`CR:240-244`）；旧 UI 用 `autoExpandTurn` 保持展开避免跳窗（`CC:563-566`）。 |
| 11 | 无障碍 | **无运行态 a11y 文案 → 违反 G-08**（旧/M5 都没有"正在思考"的可读文本；只有视觉呼吸点）。 |
| 12 | 依据 | `E0` `CC:674-693,1044-1082,563-566`、`CR:240-244,548-598`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 长任务生成过程中用户不知道在做什么；TalkBack 全程静默（G-08）。**候选** `LEG-07`：生成期间过程块可见/可开合；TalkBack 播报"正在思考"；结束不跳窗。 |

#### `chat.action.row`（助手消息操作条）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.action.row`。属 chat → 助手消息 → 操作条。出现条件：每条 assistant 消息；"重新生成"仅 `messageId != null`（`CC:1488`）；版本 ‹ › 仅 `versions.size > 1`（`CC:1489-1494`）。旧 `CC:1477-1496`；M5 `CR:600-607`。 |
| 2 | 网格与定位 | 旧 Row 左对齐（未声明）、TextButton 默认内边距，计数 bodySmall；M5 Row 间距 16dp、15dp 图标（`CR:601-607`）。 |
| 3 | 命中区 | 旧 TextButton 默认 48dp；M5 图标 15dp 且**无扩边**（Q-03 高风险）。 |
| 4 | 手势 | 单击；无长按。 |
| 5 | 行为 | 复制 → `onCopy` → 剪贴板+Toast（`MA:1158-1161`，无 Command）；重新生成 → `Command.ChatRegenerate`（`MA:997`，先切视图到父节点 `MA:991`）；版本 ‹› → prefs `v_$nodeId` + `Command.SessionSetActiveLeaf`（`CC:655-665`、`MA:1165-1171`）。复制/切版本幂等；重生成不幂等（新增 version `IB:402-434`）。 |
| 6 | 视觉状态 | 旧版本按钮两端 disabled（`CC:1491,1493`）；M5 无 disabled 态（图标总可点 `CR:604-605`）。 |
| 7 | 布局占位与展开形态 | 常驻行，不展开。 |
| 8 | 滚动语义 | 随外层列表。 |
| 9 | 动效 | 无自定义。 |
| 10 | 边界态 | 无 messageId → 无"重新生成"；单版本 → 不显示 ‹ ›；请求失败 → 全局错误条（`MA:998`）。 |
| 11 | 无障碍 | 旧有文本；M5 图标无 contentDescription（缺口）。 |
| 12 | 依据 | `E0` `CC:1477-1496`、`CR:600-607`、`MA:984-1001,1136-1145`、`IB:402-434`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 回答不满意只能整轮重发（浪费 token 且丢上下文）；旧回答无法回看。**候选** `LEG-08/09/10`：复制进剪贴板、重生成新增 version、‹› 切换后正文变化。 |

#### `chat.branch.user-switch`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.branch.user-switch`。属 chat → 用户消息 → 分支切换器。出现条件：同一 parentId 下有多个 user 版本（编辑重发产生，`CC:510-513,588-604`）；≥2 个才出现。旧 `CC:1443-1475`；M5 `CR:291-318`。 |
| 2 | 网格与定位 | 旧：Row 右对齐，26dp 高 TextButton，计数 bodySmall（`CC:1451-1473`）；M5：右对齐，16dp 图标 + 计数 + 6dp 间距（`CR:299-317`）。 |
| 3 | 命中区 | 旧 26dp 高按钮；M5 16dp 图标 → 均 <44dp（Q-03 必须扩边）。 |
| 4 | 手势 | 单击 ‹ / ›。 |
| 5 | 行为 | `onSwitch(target)` → `deepestLeaf(target.messageId)` → `onActiveLeafChanged` → `Command.SessionSetActiveLeaf`（`CC:596-603`；M5 `CR:170-178`）。幂等。 |
| 6 | 视觉状态 | 旧首/末版本 disabled（`CC:1457,1469`）；M5 用颜色降级但图标仍可点（内部 if 拦，`CR:306,314`）。 |
| 7 | 布局占位与展开形态 | 用户气泡下方常驻一行。 |
| 8 | 滚动语义 | 随外层列表。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 单版本不显示；切换后列表可能跳位（M5 有 pendingAnchor，但只在 `resolved.messages` 变化时恢复，`CR:129-135` → 条件可疑，见 §5.3）。 |
| 11 | 无障碍 | 旧有文本；M5 图标无 contentDescription（缺口）。 |
| 12 | 依据 | `E0` `CC:1443-1475,588-604`、`CR:291-318`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 编辑重发后无法回到旧措辞/旧回答分支，会话树能力对用户不可见。**候选** `LEG-11`：切换后上下文与回答都切到对应分支（真机 + 截图比对）。 |

#### `chat.code.copy`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.code.copy`。属 chat → Markdown 代码块。出现条件：每段 ``` 围栏代码块 1 个（`CC:253-262,424-450`）。 |
| 2 | 网格与定位 | 代码块顶部 Row：语言标签占 weight(1f)、复制按钮在行尾（`CC:434-439`）；块圆角 control、padding m。 |
| 3 | 命中区 | TextButton 默认 48dp（未自定义）。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `clipboard.setText(AnnotatedString(code))`（`CC:438`）——**正确写剪贴板**（与用户菜单"复制"形成对照）。无 Command、幂等。 |
| 6 | 视觉状态 | Material 默认；无"已复制"反馈。 |
| 7 | 布局占位与展开形态 | 行内常驻，不展开。 |
| 8 | 滚动语义 | 代码区 `horizontalScroll`（`CC:440-447`）；不竖向滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 无语言 → 左侧占位 Box（`CC:437`）；超长代码横向滚。 |
| 11 | 无障碍 | 按钮文本「复制」可读。 |
| 12 | 依据 | `E0` `CC:424-450`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 移动端只能长按手动选长代码，极易漏行。**候选** `LEG-12`：点击后剪贴板=代码全文（真机粘贴验证）。 |

#### `chat.scroll.back-to-latest`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.scroll.back-to-latest`。属 chat → 消息列表 → 悬浮回到底部。出现条件：`!followBottom && (有消息或流式)`（`MA:1186`）。旧 `MA:1199-1208`；M5 `CR:610-626`。 |
| 2 | 网格与定位 | 旧：`align(BottomEnd)`+padding end8/bottom8 文字按钮；M5：end16/bottom12 的 36dp 圆钮（`MA:1188-1197`）。 |
| 3 | 命中区 | 旧 TextButton 默认；M5 36dp 圆（<44dp，Q-03）。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `followBottom = true` + `scrollToLatest()`（`MA:1204-1207`、`MA:708-711`）。无 Command、幂等。 |
| 6 | 视觉状态 | 仅"未贴底"时出现；浮层（G-01）。 |
| 7 | 布局占位与展开形态 | overlay，不占布局高度（`MA:1186-1210`，符合 G-01）。 |
| 8 | 滚动语义 | 点击后 `scrollToItem(total-1, 100000)` 直接到底，无动画（`MA:708-711`）。 |
| 9 | 动效 | 出现/消失无动画（E0 现状）。 |
| 10 | 边界态 | 空会话不出现；流式中出现；贴底即消失（`atBottom` derived `MA:1057-1064`）。 |
| 11 | 无障碍 | 旧有文本；M5 图标无 contentDescription（缺口）。 |
| 12 | 依据 | `E0` `MA:1057-1064,1186-1210,708-711`、`CR:610-626`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 上翻后流式内容持续增长，手动长滑几乎追不回最新位置。**候选** `LEG-13`（挂 Q-07：保留 v 钮还是轮次导航）。 |

#### `chat.empty.suggestion`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.empty.suggestion`。属 chat → 空会话态。出现条件：M5 列表为空且无流式（`CR:122-125`）；**旧 UI 无空态**（空列表即白屏，`CC:577-694`）。每次 2 张建议卡（`CS:50-52`）。 |
| 2 | 网格与定位 | 整页居中 Column，padding h28；标题 + 副标（间隔 6dp）+ 24dp 间隔 + 两张卡（间隔 10dp）（`CS:37-52`）；卡片全宽、圆角 card、padding 16/14（`CS:60-66`）。 |
| 3 | 命中区 | 整卡可点（NeoSurface onClick），高约 44dp+。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `onSuggestion(text)` → `input = it`（`MA:1149`）**只填入输入框，不发送**。无 Command、幂等。 |
| 6 | 视觉状态 | M5 Raised 按压态（`NS:112-154`）。 |
| 7 | 布局占位与展开形态 | 空态占满列表区；点击后文本进输入框，空态仍在（消息仍为空）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 按压动效。 |
| 10 | 边界态 | 文案硬编码两条（`CS:50-52`）；副标「她记得你们聊过的事。」（`CS:44`）承诺记忆能力——本仓库 core 只有会话历史回放，**未见记忆/检索实现**（§5）。 |
| 11 | 无障碍 | 卡片为 clickable Box，无 role 声明（E0 现状缺口）。 |
| 12 | 依据 | `E0` `CS:31-73`、`CR:122-125`、`MA:1149`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 首次打开只有空白，用户不知道能聊什么；但保留"记忆"承诺文案而无实现=虚假承诺，P2 必须定文案（Q-LEG-08）。**候选** `LEG-14`：点卡片后输入框出现该句且不自动发送。 |

### 2.2 `composer`（6 节点）

#### `composer.attach.open-sheet`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.attach.open-sheet`。属 composer → 工具行 → 附件入口。旧 `CP:382-386`；M5 `MC:117-122`。每次 1 个。 |
| 2 | 网格与定位 | 旧：OutlinedButton「＋」18sp，在输入框左侧（Row 底部对齐，间距 8dp，`CP:377-386`）；M5：32dp Raised 圆钮，工具行第一个（`MC:113-122`）。 |
| 3 | 命中区 | 旧 Material 默认（含 48dp 最小）；M5 32dp 圆（<44dp，Q-03）。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `showAttachSheet = true`（`MA:1273`/`MA:1253`）→ 弹 ModalBottomSheet。无 Command、幂等。 |
| 6 | 视觉状态 | 旧 Outlined 默认；M5 Raised 按压态（`NS:112-154`）；无 disabled。 |
| 7 | 布局占位与展开形态 | 常驻工具行，不展开。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | M5 按压缩放/波纹；底部 Sheet 默认滑动。 |
| 10 | 边界态 | 无。 |
| 11 | 无障碍 | 旧按钮内容「＋」无 contentDescription；M5 Canvas 图标无 a11y 标签（缺口）。 |
| 12 | 依据 | `E0` `CP:358-386`、`MC:113-122`、`MA:1253,1273,1291-1315`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 完全无法导入媒体（用户点名的 attachment 面板入口消失）。**候选** `LEG-15`：点击弹出附件 sheet（真机）。 |

#### `composer.send-stop`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.send-stop`。属 composer → 工具行 → 发送/停止。旧 UI 是两个互斥按钮（停止 `CP:397` / 发送 `CP:398`，另有 disabled 兜底 `CP:400`）；M5 是同一按钮按 `streaming` 切图标（`MC:159-172`）。每次 1 个可见。 |
| 2 | 网格与定位 | 旧：Outlined/Button 在输入框右侧；M5：32dp 圆钮、`p.moss` 主态填充、工具行末尾（`MC:159`）。 |
| 3 | 命中区 | 旧 Material 默认；M5 32dp（<44dp）。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | 非流式：`send()`（`MA:942-982`）→ 组 `Command.ChatSend(sessionId, text, attachments, thinking, webSearch, providerId, modelId, reasoningLevel)`（`MA:962-968`）；流式：`Command.ChatStop(sessionId)`（`MA:1248,1270`）。发送不幂等（同文本重发=新一轮）；停止对"无进行中轮次"返回 `NOT_RUNNING`（`IB:338-347`）但 UI 忽略结果。 |
| 6 | 视觉状态 | 旧：`enabled = value.isNotBlank() || enabled`（`CP:398`）；M5 **无 disabled 态**（空文本仍可点，靠 `send()` 内 guard `MA:944`）。 |
| 7 | 布局占位与展开形态 | 常驻工具行。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | M5 按压；无发送中动画（流式光标在正文）。 |
| 10 | 边界态 | 空文本+无附件 → `send()` 直接 return（`MA:944`，无提示）；发送前请求通知权限（`MA:954-958`）；前台服务 begin/end（`MA:959,978-980`）；失败 → 错误条 + optimistic 用户消息回滚（`MA:973-976`）。 |
| 11 | 无障碍 | 旧有文本「发送/停止」；M5 图标无 contentDescription（缺口）。 |
| 12 | 依据 | `E0` `CP:396-401`、`MC:159-172`、`MA:942-982,1246-1248,1267-1270`、`IB:327-347`。 |
| 13 | 反证 + 验收 | **反证**：不保留/防重入不足 → 首 token 前连点会排队两轮（§5.3-a），用户看到重复提问与两份回答。**候选** `LEG-16`：空输入点击无请求；连点只产生一轮（真机 + 事件条数断言）。 |

#### `composer.voice`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.voice`。属 composer → 工具行 → 语音输入。**仅旧 UI**：`CP:399`（`onVoice != null` 且输入为空时显示），M5 未见实现（`MC:113-173` 无麦）。 |
| 2 | 网格与定位 | OutlinedButton「🎤」在输入框右侧（`CP:399`），与发送按钮互斥出现。 |
| 3 | 命中区 | Material 默认最小 48dp。 |
| 4 | 手势 | 单击 → 系统文件选择器选音频（`MA:1274`，`OpenDocument("audio/*")`）。无长按录音（**不是按住说话**）。 |
| 5 | 行为 | 读音频字节 → `Command.TranscribeAudio(name, mime, base64)`（`MA:826-848`、`MA:834-840`）→ 返回 `text` 拼到输入框（`MA:842-845`）；失败/空文本 → 错误（`MA:841-845`）。不可撤销（文本已进输入框，可手动删）。 |
| 6 | 视觉状态 | Material 默认。 |
| 7 | 布局占位与展开形态 | 常驻工具行（仅在无文本时占位）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 仅 DeepSeek provider 可用：否则 `ASR_UNAVAILABLE`（`IB:151-167`）；非音频文件/读失败 → `语音转写失败：…`（`MA:845`）；识别文本直接拼接，无确认/预览。 |
| 11 | 无障碍 | 按钮内容是 emoji「🎤」，无 contentDescription（缺口）。 |
| 12 | 依据 | `E0` `CP:358-401`、`MA:826-848,1274`、`IB:151-167`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 移动端只能打字；M5 若迁移时漏掉，语音能力直接消失（M5 现状即已消失）。**候选** `LEG-17`：选音频后输入框出现转写文本（真机）；非 DeepSeek provider 给出明确错误。 |

#### `composer.toggle-thinking`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.toggle-thinking`。属 composer → 功能 chips → 深度思考。旧 UI FilterChip（`CP:405-411`）；M5 **没有独立开关**，入口在「深度思考」胶囊=打开模型面板（`MC:123-142`、`MS:87-96`）。每次 1 个。 |
| 2 | 网格与定位 | 旧：chips Row 在输入区下方、间距 8dp（`CP:403-420`）；M5：工具行第二个 32dp 胶囊（`MC:123-142`）。 |
| 3 | 命中区 | 旧 FilterChip 默认 ≥32dp 高；M5 32dp（<44dp）。 |
| 4 | 手势 | 单击。旧=直接切布尔；M5=打开底部面板（多一步）。 |
| 5 | 行为 | 旧：`thinking = !thinking` + 写 `chatPrefs("thinking")`（`MA:1276-1279`）；M5 面板内 Switch → 同一 prefs（`MA:1461-1464`）。发送时作为 `ChatSend.thinking`（`MA:965`）。**后端在 `reasoningLevel != null` 时忽略该值**（`IB:490-494`）→ 缺陷，见 §5.2。 |
| 6 | 视觉状态 | 旧 FilterChip selected/未选；M5 胶囊 `fill = if (thinking) mossTint else surface` + 图标着色（`MC:127-134`）。 |
| 7 | 布局占位与展开形态 | 常驻；M5 打开面板时面板为 overlay。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | M5 按压；面板默认滑入。 |
| 10 | 边界态 | 模型无 reasoning 能力时，旧 UI 仍显示开关（`thinking` 仅对支持的 provider 生效，后端按 provider 决定，`IB:490-494`）；状态跨会话共享（SharedPreferences `myco.chat`，`MA:741-743`）→ 不是会话级。 |
| 11 | 无障碍 | 旧 FilterChip 有文本「深度思考」可读；M5 文本可读。 |
| 12 | 依据 | `E0` `CP:403-420`、`MC:123-142`、`MA:1254-1261,1275-1279,1461-1464`、`IB:478-495`。 |
| 13 | 反证 + 验收 | **反证**：开关与后端推理档位冲突时，用户以为关了思考却仍在思考（多花钱、回复变慢）。**候选** `LEG-18`：开关状态与实际请求参数一致（单测 bridge 参数 / 真机抓包）。 |

#### `composer.toggle-websearch`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.toggle-websearch`。属 composer → 功能 chips → 智能搜索。旧 `CP:412-418`；M5 `MC:143-157`。每次 1 个。 |
| 2 | 网格与定位 | 旧 chips Row 第二项；M5 工具行第三个 32dp 胶囊（`MC:143-157`）。 |
| 3 | 命中区 | 旧 FilterChip 默认；M5 32dp（<44dp）。 |
| 4 | 手势 | 单击切布尔。 |
| 5 | 行为 | `webSearch = !webSearch` + 写 prefs（`MA:1280-1284`、`MA:1258-1261`）；发送时 `ChatSend.webSearch`（`MA:966`）；后端 `false` 时从工具 schema 过滤 `web_search/read_url`（`IB:497-505`）。不发独立 Command；幂等。 |
| 6 | 视觉状态 | 旧 FilterChip selected；M5 `fill = if (webSearch) mossTint else surface`（`MC:147`）。 |
| 7 | 布局占位与展开形态 | 常驻。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 按压。 |
| 10 | 边界态 | provider 不支持能力工具时（非 api.deepseek.com），工具未注册（`CT:20-31`、`BH:39-41`）→ 开关开着也没有搜索；状态跨会话共享（prefs）。 |
| 11 | 无障碍 | 文本「智能搜索」可读。 |
| 12 | 依据 | `E0` `CP:403-420`、`MC:143-157`、`MA:1258-1261,1280-1284,966`、`IB:497-505`、`CT:20-31`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 用户无法阻止模型联网（隐私/费用）。**候选** `LEG-19`：关闭后请求里无 web_search 工具（单测 schema 断言）。 |

#### `composer.pending.attachments`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.pending.attachments`。属 composer → 待发送附件条。出现条件：`pending` 非空。旧 `MA:1220-1237`；M5 `CS:77-132`。每个附件 1 个可删 chip + 1 个「清空」。 |
| 2 | 网格与定位 | 旧：横向滚动 Row + 末尾「清空」TextButton（`MA:1220-1236`）；M5：横向滚动胶囊 + 右侧「清空」文本（`CS:86-130`）。 |
| 3 | 命中区 | 旧 AssistChip 整块可点；M5 胶囊内 11dp 关闭图标（远小于 44dp，Q-03）。 |
| 4 | 手势 | 单击 chip/关闭图标 = 移除该附件；单击「清空」= 全清。 |
| 5 | 行为 | 旧：`pending = pending - ref` / `pending = emptyList()`（`MA:1230,1235`）；M5：`onRemove/onClear`（`MA:1216-1217`、`CS:116,128`）。**只改内存列表，不删已落盘文件**（`MA:790-817` 选完即 `AttachmentSave`）→ 孤儿文件（`AS` 无删除 API）。无 Command。 |
| 6 | 视觉状态 | 旧 AssistChip 默认；M5 Raised 胶囊 + 关闭图标。 |
| 7 | 布局占位与展开形态 | 在消息列表与输入区之间占一行（`MA:1212`），**会挤压消息区高度**；附件为空时不占位。 |
| 8 | 滚动语义 | 横向滚动查看（`MA:1224-1233`、`CS:90-121`）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 单附件名超长 → 单行省略（`MA:1231`、`CS:105-112`）；附件过大 → `AttachmentSave` 失败 → 错误条（`MA:805`、`AS:24-25`）；kind=file 无预览。 |
| 11 | 无障碍 | chip 文本=文件名可读；M5 关闭图标无 contentDescription（缺口）。 |
| 12 | 依据 | `E0` `MA:790-817,1212-1237,1253,1216-1217`、`CS:77-132`、`AS:20-72`、`BR:119-144`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 用户无法在发送前撤掉选错的附件，只能整条重发。**候选** `LEG-20`：移除后该附件不进 `ChatSend.attachments`（单测）；文件清理策略待 P2（Q-LEG-06）。 |

### 2.3 `attachment`（1 节点）

#### `attachment.sheet.pick-source`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `attachment.sheet.pick-source`。属 attachment → 底部 sheet → 来源三选一。出现条件：`showAttachSheet == true`（`MA:1291-1315`）。三个入口：拍照（`MA:1304`）/相册（`MA:1305`）/文件（`MA:1309`），每次各 1 个。 |
| 2 | 网格与定位 | ModalBottomSheet 内 Column padding l：标题「添加附件」+ 说明文本 + 3 个 TextButton 纵向排列（`MA:1296-1313`）。 |
| 3 | 命中区 | TextButton 默认 ≥48dp 高；纵向间距由 Material 默认。 |
| 4 | 手势 | 单击；sheet 下滑/点外部关闭（`onDismissRequest` `MA:1293`）。 |
| 5 | 行为 | ① 拍照 → `showAttachSheet=false` → `launchCamera()`（FileProvider 写 cache，`MA:852-861`）→ 系统相机 → `attachUri`；② 相册 → `PickVisualMedia(ImageOnly)`（`MA:1305-1308,819-821`）；③ 文件 → `OpenDocument("*/*")`（`MA:1309-1312,822-824`）。三者最终都进 `attachUri` → `Command.AttachmentSave(sessionId,name,mime,base64)`（`MA:797-804`）。取消系统选择器 → 无副作用。不可撤销（文件已落盘）。 |
| 6 | 视觉状态 | TextButton 默认；无 pressed 自定义。 |
| 7 | 布局占位与展开形态 | ModalBottomSheet（形态 C 的 overlay，`MA:1292-1295`），**不占布局**。 |
| 8 | 滚动语义 | 当前 3 行不需要滚；sheet 内是普通 Column（无 inner scroll，未来附件多时会溢出）。 |
| 9 | 动效 | Material sheet 默认。 |
| 10 | 边界态 | 无相机/权限失败 → `相机不可用：…`（`MA:861`）；读文件失败 → `附件失败：…`（`MA:815`）；大文件 → 后端上限错误（图片 32MB / 其它 8MB `AS:24-25`）；**无预览/无进度/无多选**（`PickVisualMedia` 单张；`MA:819-824`）。 |
| 11 | 无障碍 | 三个 TextButton 有文本（拍照/相册/文件）可读；sheet 标题可读。 |
| 12 | 依据 | `E0` `MA:819-861,1291-1315`、`AS:23-38`、`IB:136-149`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 完全无法导入媒体（用户点名的核心功能）；当前版本只保留"能选"不保留"能看"（无预览/进度）→ 用户会以为没导入成功。**候选** `LEG-21`：三个入口各自能落盘并出现在 pending 条；大文件失败有明确提示。 |

### 2.4 `model`（4 节点）

#### `model.entry.open-picker`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.entry.open-picker`。属 model → 入口。旧：聊天页顶栏下方一行 `TextButton("模型 · $label")`（`MA:1041-1053`），仅非 M5 UI；M5：输入区「深度思考」胶囊（`MC:130`）。每次 1 个。 |
| 2 | 网格与定位 | 旧：Row padding h16/v4，入口按钮在行首（`MA:1041-1045`）；M5：工具行第二个胶囊（`MC:123-142`）。 |
| 3 | 命中区 | 旧 TextButton 默认；M5 32dp 高胶囊（<44dp）。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | 旧：`showModelPicker = true`（`MA:1045`）→ AlertDialog；M5：`showM5Model = true`（`MA:1252`）→ ModalBottomSheet。无 Command；幂等。 |
| 6 | 视觉状态 | 旧 TextButton；M5 胶囊 `fill = if (thinking) mossTint else surface`（`MC:127`）——**入口底色绑定"深度思考"状态**，同一胶囊承担两个语义。 |
| 7 | 布局占位与展开形态 | 旧：聊天页顶部固定行（占布局，推挤消息区）；M5：工具行内。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 面板默认。 |
| 10 | 边界态 | provider 全被停用/为空 → 旧选择器无任何行（`MA:1371` filter enabled）也无空态提示；M5 面板 `models` 为空时只剩开关与文案（`MS:100-129`）。 |
| 11 | 无障碍 | 旧文本含模型名可读；M5 文本可读。 |
| 12 | 依据 | `E0` `MA:1041-1053,1252,1360-1441,1442-1491`、`MC:123-142`、`MS:49-148`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 用户无法切换模型/思考强度，只能用会话默认，等于丢模型面板全部能力。**候选** `LEG-22`：点开面板后能看到当前 provider/模型（真机截图）。 |

#### `model.picker.model-row`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.picker.model-row`。属 model → 旧选择器（AlertDialog）→ 模型行。出现条件：面板打开且 `providersUi.filter { enabled }` 非空，每个 model 一行（`MA:1371-1408`）。 |
| 2 | 网格与定位 | 每个 provider 先一行 provider 名（titleSmall，`MA:1372-1376`），其下模型行为 RadioButton + 两行文本（名称、`id · ctx · out`）（`MA:1393-1406`）；整块 `heightIn(max=420dp)+verticalScroll`（`MA:1367`）。 |
| 3 | 命中区 | **整行 clickable（`MA:1393-1394`）+ RadioButton 自身 onClick（`MA:1397`）** 两个命中区指向同一动作。 |
| 4 | 手势 | 单击行或单选钮。 |
| 5 | 行为 | `chooseModel` → `Command.SessionSetModel(sessionId, providerId, modelId, m.reasoningDefault)`（`MA:1382-1392`、`MA:1385`）；成功才更新本地选中（`MA:1389`）；失败把错误显示在面板顶部（`MA:1386-1388`）。幂等（同值重复设置）。 |
| 6 | 视觉状态 | RadioButton 选中；无按下自定义；提交中无 loading 态。 |
| 7 | 布局占位与展开形态 | 列表内常驻行；不展开。 |
| 8 | 滚动语义 | 面板内滚动（max 420dp），无边缘吸收自定义。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | provider 无模型 → 显示「（没有模型）」（`MA:1377-1379`）；无 enabled provider → **整个列表空且无提示**（缺陷 §5.2）；会话保存的模型已被删/停用 → 面板不提示（后端返回 `providerAvailable/modelAvailable`，UI 未消费，见 §5.2）。 |
| 11 | 无障碍 | RadioButton 有语义；行文本可读。 |
| 12 | 依据 | `E0` `MA:1360-1408`、`MA:864-877`、`IB:829-844`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 无法按会话切模型（多模型场景直接不可用）。**候选** `LEG-23`：选择后顶栏标签变化且下次发送用新模型（单测 `Command.SessionSetModel` 参数）。 |

#### `model.picker.reasoning-chip`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.picker.reasoning-chip`。属 model → 旧选择器 → 思考强度。出现条件：当前选中模型 `reasoningEfforts` 非空（`MA:1409-1436`）；「默认」+ 每个 level 1 个 chip。 |
| 2 | 网格与定位 | provider 列表之后：标题「思考强度」+ Row（间距 8dp）的 FilterChip 横排（`MA:1410-1435`）。 |
| 3 | 命中区 | FilterChip 默认 ≥32dp 高。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | 「默认」→ `SessionSetModel(sessionId, providerId, modelId, null)`（`MA:1414-1419`）；level → 传该 level（`MA:1425-1431`）；失败 → 面板顶部错误（`MA:1428`）。幂等。 |
| 6 | 视觉状态 | FilterChip selected（`MA:1413,1424`）。 |
| 7 | 布局占位与展开形态 | 常驻行；不展开。 |
| 8 | 滚动语义 | 跟随面板滚动。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 无 reasoning 能力的模型 → 整块不显示（`MA:1409`）；选「默认」后端仍会回落到 `model.reasoning.default`（`IB:483`）→ "默认"不等于"关闭思考"（§5.2）；切模型后 level 不同步清空（`chooseModel` 用新模型 default `MA:1384`，会覆盖）。 |
| 11 | 无障碍 | Chip 文本可读。 |
| 12 | 依据 | `E0` `MA:1409-1436`、`IB:478-495`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 无法为每次会话选推理档（费用/时延不可控）。**候选** `LEG-24`：选 level 后下一次 `ChatSend.reasoningLevel` 等于该值（单测）。 |

#### `model.sheet.reasoning-pill`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.sheet.reasoning-pill`。属 model → M5 模型面板 → 思考强度 pills。出现条件：`models[selected].levels` 非空（`MS:127-140`）；「默认」+ 每个 level 1 个（`MS:135-138`）。 |
| 2 | 网格与定位 | 面板底部：标题「思考强度」+ `horizontalScroll` 的 Row（间距 8dp）（`MS:124-140`）；`ReasoningPill` 圆角 15dp、padding h12/v7（`MS:150-166`）。 |
| 3 | 命中区 | 胶囊高度≈30dp（<44dp，Q-03）。 |
| 4 | 手势 | 单击 → 立即提交并**关闭面板**（`MA:1478-1488`）。 |
| 5 | 行为 | `onPickReasoning(level)` → `Command.SessionSetModel(sessionId, selProviderId, selModelId, level)`（`MA:1480`）；成功置 `selReasoningLevel` 并 `showM5Model=false`（`MA:1483-1485`）。幂等。 |
| 6 | 视觉状态 | selected 用 moss 填充 + 深色字（`MS:156-164`）。 |
| 7 | 布局占位与展开形态 | 面板内横排行；不展开。 |
| 8 | 滚动语义 | 横向滚动（`MS:132`），无边缘吸收。 |
| 9 | 动效 | 按压（NeoSurface）。 |
| 10 | 边界态 | 无 level → 显示「当前模型不支持思考强度」（`MS:128-129`）；面板关闭后聊天页 `reasoningLabel` 显示在胶囊上（`MC:136-138`）。 |
| 11 | 无障碍 | 文本可读；无 selected 语义（自绘 NeoSurface，无 role）。 |
| 12 | 依据 | `E0` `MS:49-148,150-166`、`MA:1442-1491`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → M5 用户无法调思考强度（只能回旧 UI）。**候选** `LEG-25`：选档后面板关闭且胶囊显示档位（真机截图）。 |

### 2.5 `settings`（14 节点）

#### `settings.appearance.theme-chip`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.appearance.theme-chip`。属 settings → 外观卡 → 主题三选一。出现条件：设置页搜索命中"外观/主题/…"（`MA:1601,1668-1690`）；`ThemeMode.values()` 3 个 FilterChip。 |
| 2 | 网格与定位 | 外观卡内 Row（间距 8dp），三个 FilterChip（`MA:1671-1687`）。 |
| 3 | 命中区 | FilterChip 默认 ≥32dp。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `onThemeChange(mode)` → `mode = it` + `ThemePrefs.save`（`MA:162-165,1675`）。**纯本地，不发 Command**；立即生效；幂等。 |
| 6 | 视觉状态 | FilterChip selected（`MA:1674`）。 |
| 7 | 布局占位与展开形态 | 常驻。 |
| 8 | 滚动语义 | 设置页 LazyColumn 滚动。 |
| 9 | 动效 | 主题切换无过渡动画（`MA:162-166` 直接换色）。 |
| 10 | 边界态 | 跟随系统时随系统深浅（`MA:138-142`）；设置页搜索不命中则整卡隐藏（`MA:1668`）。 |
| 11 | 无障碍 | 文本可读（跟随系统/浅色/深色）。 |
| 12 | 依据 | `E0` `MA:1601,1668-1690,162-165`、`TP:10-17`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 用户无法改外观（深色/浅色是基础预期）。**候选** `LEG-26`：选择后重启 App 仍生效（真机）。 |

#### `settings.session.bin-rows`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.session.bin-rows`。属 settings → 会话卡 → 2 行导航。出现条件：搜索命中"会话/归档/回收站/…"（`MA:1602,1691-1699`）。两行：归档会话（`MA:1694`）/回收站（`MA:1696`）。 |
| 2 | 网格与定位 | SettingsCard 内两行 SettingsRow + Divider（`MA:1693-1697`）；`SettingsRow` 整行 padding vertical m、右侧「›」（`CP:145-163`）。 |
| 3 | 命中区 | 整行可点（`CP:146-150` clickable）。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `onOpenBin(BinKind.ARCHIVED/DELETED)` → 设置层之上再叠一层 bin（`MA:451-476`、`MA:252-256`），本地导航，无 Command。幂等。 |
| 6 | 视觉状态 | 无按压反馈自定义；「›」提示可进。 |
| 7 | 布局占位与展开形态 | 常驻行。 |
| 8 | 滚动语义 | 设置页滚动。 |
| 9 | 动效 | bin 从右滑入（`MA:252-256,459-476`）。 |
| 10 | 边界态 | 两页都空时有空态文案（`MA:1526-1530`）；加载中为空白（`MA:1524-1525`，无 spinner）。 |
| 11 | 无障碍 | 行文本可读；无 role 声明（clickable Box）。 |
| 12 | 依据 | `E0` `MA:1691-1699,252-256,1496-1555`、`CP:145-163`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 归档/软删的会话没有任何入口找回，等于"删除即永久丢失"。**候选** `LEG-27`：两行分别打开对应列表（真机）。 |

#### `settings.provider.row-actions`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.provider.row-actions`。属 settings → 模型提供方 → provider 行操作。出现条件：该 provider 渲染一行（`MA:1709-1734`）；3 个 TextButton：编辑（`MA:1727`）/设置密钥（`MA:1728`，仅 `authRef != null`）/删除（`MA:1729`）。 |
| 2 | 网格与定位 | provider 卡内：卡片（GlassSurface）+ 3 行文本 + 操作 Row（间距 4dp）（`MA:1710-1732`）。 |
| 3 | 命中区 | TextButton 默认 ≥48dp。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | 编辑 → `editor = p.toEditor()`（`MA:1727`，无 Command）；设置密钥 → `KeyDialog`（`MA:1728`）→ `Command.CredentialSet`（`MA:1882`）；删除 → 确认框（`MA:1729`）→ `Command.ProviderDelete(id, revision)`（`MA:1862`）。删除不可撤销（只移 providers.json，`MA:1857` 文案）。 |
| 6 | 视觉状态 | 普通 TextButton；「删除」无 error 色（确认框按钮也未着色 `MA:1867`）。 |
| 7 | 布局占位与展开形态 | 常驻行。 |
| 8 | 滚动语义 | 设置页滚动。 |
| 9 | 动效 | Dialog 默认。 |
| 10 | 边界态 | 密钥已配置 → 显示「已配置（不回显）」；缺失 → 红字（`MA:1719-1725`）；删除失败 → `message = code: message`（`MA:1863`）显示在 provider 区顶部（`MA:1703-1705`，仅当搜索命中 provider 区才可见）。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `MA:1709-1734,1873-1889,1853-1871`、`IB:249-325`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 无法维护 provider（改地址/换 key/删旧 provider），模型面板也就无法更新。**候选** `LEG-28`：三项各自打开对应弹层；删除后 provider 消失且 revision+1。 |

#### `settings.provider.add-actions`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.provider.add-actions`。属 settings → 模型提供方 → 添加入口。3 个按钮：加本地模板（`MA:1737`）/加 DeepSeek（`MA:1738`）/更多模板（`MA:1739`）。 |
| 2 | 网格与定位 | provider 列表尾部 Row（间距 8dp）：Button + 2×OutlinedButton（`MA:1736-1740`）。 |
| 3 | 命中区 | Material 默认 ≥48dp。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | 前两个直接用 `ProviderEditor.localTemplate()/deepseekTemplate()` 预填编辑器（`MA:1737-1738`、`MA:2160-2179`）；「更多模板」打开模板对话框（`MA:1739`、`MA:1790-1816`）。均无 Command（保存时才 `ProviderSave` `MA:1836`）。 |
| 6 | 视觉状态 | Button/OutlinedButton 默认。 |
| 7 | 布局占位与展开形态 | 常驻 Row。 |
| 8 | 滚动语义 | 设置页滚动。 |
| 9 | 动效 | 弹窗默认。 |
| 10 | 边界态 | 模板写死 6 个 provider（deepseek/openai/anthropic/kimi/openrouter/gemini，`MA:2196-2213`）；模板里 `anthropic` 的 kind 写成 `deepseek-messages`（`MA:2205`）→ 实际走 Anthropic 兼容客户端（`CCL:8-15` 按 kind 分派），需 P2 确认是否有意（Q-LEG-04）。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `MA:1735-1741,1790-1816,2160-2213`、`CCL:7-15`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 新用户无法在没有文档的情况下配置 provider（整个 App 不可用）。**候选** `LEG-29`：三个入口都能打开预填编辑器。 |

#### `settings.provider.export`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.provider.export`。属 settings → 模型提供方 → 导出。`MA:1744`。 |
| 2 | 网格与定位 | provider 区底部 Row（间距 8dp）左侧 OutlinedButton「导出」（`MA:1742-1747`）。 |
| 3 | 命中区 | Material 默认。 |
| 4 | 手势 | 单击 → 系统 `CreateDocument("application/json")` 写文件（`MA:1620-1634,1744`）。 |
| 5 | 行为 | `Query.ProviderExport`（`IB:882-893`）→ 把返回 JSON 写入用户选的文件（`MA:1623-1633`）；成功/失败写 `message`（`MA:1629-1631`）。不发 Command；幂等（可重复导出）。 |
| 6 | 视觉状态 | 无 loading；结果只显示文本（`MA:1703-1705`）。 |
| 7 | 布局占位与展开形态 | 常驻 Row。 |
| 8 | 滚动语义 | 设置页滚动。 |
| 9 | 动效 | 系统文件选择器。 |
| 10 | 边界态 | 密钥值不导出（只导 `auth.ref` 与 credential 状态，`IB:882-893`）；写失败 → 「导出失败：…」；取消文件选择 → 静默。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `MA:1620-1634,1744`、`IB:882-893`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 换机/分享配置只能手抄；provider 多时几乎不可用。**候选** `LEG-30`：导出文件可被导入端解析（真机 round-trip）。 |

#### `settings.provider.import`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.provider.import`。属 settings → 模型提供方 → 导入。`MA:1745`。 |
| 2 | 网格与定位 | 与导出同 Row 右侧 OutlinedButton「导入」（`MA:1742-1747`）。 |
| 3 | 命中区 | Material 默认。 |
| 4 | 手势 | 单击 → `OpenDocument(json/text/*)`（`MA:1635-1658,1745`）。 |
| 5 | 行为 | 读文件 → 解析 `providers|data|裸数组` 三种形状（`MA:1643-1649`）→ `Command.ProviderReplace(list, revision)`（`MA:1650`）→ 成功后 `BridgeHolder.rebuild` + reload（`MA:1652-1654`）。**整份替换**，不是 merge（`IB:282-291`）。不可撤销（旧 provider 被覆盖）。 |
| 6 | 视觉状态 | 无 loading。 |
| 7 | 布局占位与展开形态 | 常驻 Row。 |
| 8 | 滚动语义 | 设置页滚动。 |
| 9 | 动效 | 系统选择器。 |
| 10 | 边界态 | revision 冲突 → `SETTINGS_CONFLICT`（`IB:287`）；文件损坏/形状不识别 → `导入失败：…`（`MA:1655`）；成功后消息只在 provider 区可见（搜索隐藏则不显示，`MA:1703`）。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `MA:1635-1658,1745`、`IB:282-291`、`SST:47-66`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 无法批量迁移已有 provider。**候选** `LEG-31`：导入后列表与导出前一致；冲突时明确报错不写盘。 |

#### `settings.foreground.toggle`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.foreground.toggle`。属 settings → 常驻卡 → 启动/停止。2 个按钮：`MA:1754` / `MA:1758`。 |
| 2 | 网格与定位 | 常驻卡标题 + Row（间距 8dp）：Button「启动前台服务」+ OutlinedButton「停止」+ 状态文本（`MA:1752-1767`）。 |
| 3 | 命中区 | Material 默认。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | 启动 → `context.startForegroundService(MycoForegroundService)` + `running=true`（`MA:1754-1757`）；停止 → `stopService` + `running=false`（`MA:1758-1761`）。纯本地，无 Command；`running` 只是本地状态，**不查询服务真实状态**（重启页面后显示"未启动"，`MA:1596`）。 |
| 6 | 视觉状态 | `running` 决定状态文本（`MA:1763-1767`）。 |
| 7 | 布局占位与展开形态 | 常驻卡。 |
| 8 | 滚动语义 | 设置页滚动。 |
| 9 | 动效 | 通知栏出现/消失。 |
| 10 | 边界态 | 系统限制/权限问题 → `startForegroundService` 异常未捕获（`MA:1755` 无 runCatching）→ 可能崩溃；Android 13+ 通知权限未授予（生成时会请求 `MA:954-958`，设置页不请求）。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `MA:1750-1770`、`FG:15-59`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 无法验证/演示常驻能力（A7 尽力而为）；但保留而不读真实状态会误导用户以为没启动。**候选** `LEG-32`：点启动后通知栏出现常驻通知（真机）；停止后消失。 |

#### `settings.templates.pick`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.templates.pick`。属 settings → 第三方模板对话框。出现条件：`showTemplates==true`（`MA:1790-1816`）；每个模板 1 个 TextButton（`MA:1797`）。 |
| 2 | 网格与定位 | AlertDialog 内 `verticalScroll + heightIn(max=420dp)` 的 Column，每个模板一个 TextButton（内含两行文本）（`MA:1794-1812`）。 |
| 3 | 命中区 | TextButton 整行宽度（未显式 fillMaxWidth，实际按内容宽）。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `editor = t.toEditor(); showTemplates=false`（`MA:1797-1801`）→ 预填编辑器（无 Command）。 |
| 6 | 视觉状态 | 默认。 |
| 7 | 布局占位与展开形态 | Dialog overlay。 |
| 8 | 滚动语义 | 对话框内滚动（>420dp）。 |
| 9 | 动效 | Dialog 默认。 |
| 10 | 边界态 | 6 个模板（`MA:2196-2213`）；模板元数据是写死的（非远程目录）。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `MA:1790-1816,2196-2213`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 只有 2 个快捷模板，"更多模板"变死路。**候选** `LEG-33`：选中模板后编辑器 ID/BaseURL 预填正确。 |

#### `settings.provider-editor.save`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.provider-editor.save`。属 settings → provider 编辑器（全屏 Dialog）→ 保存。`MA:2013`；编辑器出现条件 `editor != null`（`MA:1818-1851`）。 |
| 2 | 网格与定位 | 全屏 Dialog（宽满、高 0.92 屏），底部 Row 右对齐：取消 + 保存（`MA:1901-1905,2011-2014`）。 |
| 3 | 命中区 | Button 默认。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `onSave(form)`：`ProviderSave(form.toProfile(), revision)`（`MA:1836`）→ 若填了 apiKey 再 `CredentialSet(ref, key)`（`MA:1838-1841`）→ 成功则关编辑器 + `BridgeHolder.rebuild` + reload（`MA:1843-1847`）；失败显示错误（`MA:1842`）。不幂等（revision 递增）；不可撤销。 |
| 6 | 视觉状态 | 无 loading/禁用；保存中可重复点（可能触发 revision 冲突）。 |
| 7 | 布局占位与展开形态 | Dialog overlay（形态 C 类），内部滚动区 + 固定底部按钮（`MA:1911-1913,2011`）。 |
| 8 | 滚动语义 | 表单区 verticalScroll。 |
| 9 | 动效 | Dialog 默认。 |
| 10 | 边界态 | 非法 id/kind/空 models/contextWindow≤0 → 后端 `ProviderConfig` 错误显示在 `message`（`MA:1842`；校验见 `PS:45-58`）；revision 冲突 → `SETTINGS_CONFLICT`（`IB:303-304`）；密钥保存失败 → 「密钥保存失败：…」（`MA:1840`）。 |
| 11 | 无障碍 | 按钮文本可读。 |
| 12 | 依据 | `E0` `MA:1818-1851,1893-2018`、`IB:249-308`、`PS:45-58`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → provider 无法新增/修改，App 无法接真模型（只能 FakeChatClient）。**候选** `LEG-34`：保存后 provider 列表出现新项且可被模型面板选中。 |

#### `settings.provider-editor.discover`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.provider-editor.discover`。属 settings → provider 编辑器 → 测试连通性。`MA:2005`。 |
| 2 | 网格与定位 | 表单底部 Row：OutlinedButton「测试连通性」+ 状态文本（`MA:2004-2009`）。 |
| 3 | 命中区 | Material 默认。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `onDiscover(form)` → `Command.ProviderDiscover(form.toProfile())`（`MA:1822-1831`、`MA:1825`）→ 返回模型列表**只拼成字符串显示**（`MA:1827,2215-2218`），**不回填表单 models**（缺陷 §5.2）。无副作用（不写盘，`BR:79-80` 注释明确）。 |
| 6 | 视觉状态 | 「获取中…」→ 结果/错误文本（`MA:1823,1826-1829`）。 |
| 7 | 布局占位与展开形态 | 常驻 Row。 |
| 8 | 滚动语义 | 表单区滚动。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 缺凭据 → `CREDENTIAL_MISSING`；网络/404 → `DISCOVERY_FAILED`（`IB:275-279`、`DI:29-57`）；**成功也只显示字符串，用户还得手抄模型 id**。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `MA:1822-1831,2005`、`IB:258-281`、`DI:29-101`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 用户必须凭空知道模型 id 与上下文窗口；保留但只看字符串=半成品（P2 需定是否补"采用"动作，Q-LEG-05）。**候选** `LEG-35`：连通性结果可见；失败有明确 code。 |

#### `settings.provider-editor.add-remove-model`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.provider-editor.add-remove-model`。属 settings → provider 编辑器 → 模型目录增删。删除：每个模型行（`models.size>1` 时）1 个 TextButton「删除」（`MA:1931-1935`）；添加：「＋ 添加模型」（`MA:2001-2003`）。 |
| 2 | 网格与定位 | 每个模型块：标题行（"模型 N" + 删除）+ 4 输入框 + checkbox 行 + 强度配置（`MA:1927-1999`）；添加按钮在模型列表之后（`MA:2001`）。 |
| 3 | 命中区 | TextButton 默认。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | 删除 → `models.filterIndexed{...}`（`MA:1932-1934`）；添加 → `models + ModelEditor.blank(index)`（`MA:2001-2002`、`MA:2084-2087`）。**纯本地表单**，保存时才落盘（`MA:2013`）。可逆（取消编辑器）。 |
| 6 | 视觉状态 | 最后一个模型不给删除按钮（`MA:1931`）。 |
| 7 | 布局占位与展开形态 | 表单内可增删的块；块数量变化使下方内容位移。 |
| 8 | 滚动语义 | 表单区 verticalScroll。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 删到只剩 1 个模型时删除按钮消失；添加的模型 id 自动 `model`/`model-N`（`MA:2085`）→ 可能重复（用户需手改，重复 id 后端不校验唯一性？`PS:45-58` 未查重）。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `MA:1927-2003,2050-2101`、`PS:45-58`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 无法维护多模型 provider（绝大多数 provider 都有多个模型）。**候选** `LEG-36`：增删后保存结果与表单一致（单测 profile 序列化）。 |

#### `settings.provider-editor.model-level-chip`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.provider-editor.model-level-chip`。属 settings → provider 编辑器 → 模型默认思考档。出现条件：该模型勾选"支持思考强度"（`MA:1975-1986`）；4 个 FilterChip（off/low/medium/high）。 |
| 2 | 网格与定位 | 模型块内 Row（间距 4dp）：4 个 FilterChip「默认 $level」（`MA:1976-1986`）。 |
| 3 | 命中区 | FilterChip 默认 ≥32dp。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `defaultLevel = level`（本地表单，`MA:1980-1982`）；保存时写入 `ReasoningProfile.default`（`MA:2072-2080`）。无 Command。 |
| 6 | 视觉状态 | selected（`MA:1979`）。 |
| 7 | 布局占位与展开形态 | 常驻 Row。 |
| 8 | 滚动语义 | 表单区滚动。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | **档位写死 4 个**（`MA:1977`），而 schema 的 efforts 是任意 Map（`MD:59-62`）→ 导入的自定义档位无法在此编辑；efforts 的 API 值三输入框只覆盖 low/medium/high（`MA:1987-1997`），`off` 固定映射空串（`MA:2074`）。 |
| 11 | 无障碍 | Chip 文本可读。 |
| 12 | 依据 | `E0` `MA:1975-1997,2072-2080`、`MD:58-62`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 无法为模型指定默认推理档（每次会话都要手动选）。**候选** `LEG-37`：保存后 profile.reasoning.default 等于所选档。 |

#### `settings.key.save`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.key.save`。属 settings → 设置密钥对话框。出现条件：`keyTarget != null` 且 `authRef != null`（`MA:1873-1889`）；保存按钮 `MA:2033`。 |
| 2 | 网格与定位 | AlertDialog：标题「设置密钥 $ref」+ 说明 + 单行输入框 + 保存/取消（`MA:2021-2037`）。 |
| 3 | 命中区 | TextButton 默认；输入框整块。 |
| 4 | 手势 | 单击（输入用键盘）。 |
| 5 | 行为 | `Command.CredentialSet(ref, value.trim())`（`MA:1880-1886`、`MA:1882`）→ 成功后「密钥已保存（不回显）」（`MA:1883`）；值只写不读（`BR:76-77`）。幂等（覆盖写）。 |
| 6 | 视觉状态 | `enabled = value.isNotBlank()`（`MA:2033`）→ 空值禁用保存。 |
| 7 | 布局占位与展开形态 | Dialog overlay。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | Dialog 默认。 |
| 10 | 边界态 | 非法 ref → `INVALID_CREDENTIAL`（`IB:251-257`）；保存后不回显、无"清除密钥"入口（E0 现状缺失）。 |
| 11 | 无障碍 | 输入框 label「API key」；无密码遮罩声明（`OutlinedTextField` 明文显示，`MA:2029`）→ 现状缺口。 |
| 12 | 依据 | `E0` `MA:1873-1889,2021-2037`、`IB:251-257`、`BR:76-77`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → provider 永远缺密钥（`configured=false`），模型调用报错。**候选** `LEG-38`：保存后 provider 行显示"已配置"且不回显明文（真机+截图）。 |

#### `settings.provider.delete-confirm`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.provider.delete-confirm`。属 settings → 删除 provider 确认框。出现条件：`deleteTarget != null`（`MA:1853-1871`）；确认按钮 `MA:1859`。 |
| 2 | 网格与定位 | AlertDialog 标题「删除 {id}？」+ 说明 + 删除/取消（`MA:1854-1870`）。 |
| 3 | 命中区 | TextButton 默认。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `Command.ProviderDelete(p.id, revision)`（`MA:1862`）→ 失败写 `message`（`MA:1863`）→ 无论成败 `BridgeHolder.rebuild` + reload（`MA:1864-1865`）。**不可撤销**（"只从 providers.json 移除；已派生的密钥引用不动"，`MA:1857`）。 |
| 6 | 视觉状态 | 普通 TextButton（未标 error 色）。 |
| 7 | 布局占位与展开形态 | Dialog overlay。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | Dialog 默认。 |
| 10 | 边界态 | provider 已不存在 → `PROVIDER_NOT_FOUND`（`IB:314-316`）；revision 冲突 → `SETTINGS_CONFLICT`（`IB:320-321`）。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `MA:1853-1871`、`IB:310-325`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 只能通过导入整份替换来删 provider，误删风险更高。**候选** `LEG-39`：确认后 provider 从列表消失且密钥引用保留。 |

### 2.6 `sidebar`（14 节点）

#### `sidebar.header.new-chat`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.header.new-chat`。属 sidebar → 抽屉头部。出现条件：抽屉/常驻侧栏渲染时（`DR:57-71`）；非批处理态 1 个（`DR:66-70`）。 |
| 2 | 网格与定位 | 头部 Row（padding start16/end12/top16）：「myco」标题 weight(1f) + 「选择」+「＋ 新对话」（`DR:58-71`）。 |
| 3 | 命中区 | TextButton 默认 ≥48dp；与「选择」相邻无额外间隔（误触风险）。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `createSession()` → `openDraft()` → `Command.SessionEnsureDraft`（`MA:275-296`、`MA:343`、`IB:69-77`），然后 `drawerState.close()`（`MA:294`）。已有空白草稿则复用（`IB:70-74`）→ 幂等（不会造多个空白会话）。 |
| 6 | 视觉状态 | 默认。 |
| 7 | 布局占位与展开形态 | 常驻头部行。 |
| 8 | 滚动语义 | 不滚（列表在下方单独滚）。 |
| 9 | 动效 | 抽屉关闭动画。 |
| 10 | 边界态 | 已存在空白草稿 → 不新建；失败（bridge Err）→ `openDraft` 返回 null，**无提示、屏幕可能停在空态**（`MA:276-277`）。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `DR:56-71`、`MA:275-296,343`、`IB:69-77`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 没有入口开新对话，只能重复使用旧会话。**候选** `LEG-40`：点击后出现新空白会话（且不会叠加多个空白）。 |

#### `sidebar.batch.toggle`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.batch.toggle`。属 sidebar → 批处理开关。3 个实现：「选择」（非批处理且有会话，`DR:68`）/「完成」（批处理，`DR:65`）/「取消」（批处理底部，`DR:113`）。每次可见 1-2 个。 |
| 2 | 网格与定位 | 「选择」在头部标题右侧；「完成」在头部左侧（批处理态显示"已选 N"）；「取消」在底部批量条最右（`DR:62-70,101-114`）。 |
| 3 | 命中区 | TextButton 默认。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `batchMode = !batchMode; selectedIds = emptySet()`（`MA:352-355`）。本地状态；退出批处理即清空选择；幂等。 |
| 6 | 视觉状态 | 批处理态头部换成"已选 N"+全选+完成；底部换批量条（`DR:62-70,102-114`）。 |
| 7 | 布局占位与展开形态 | 常驻头部/底部；切换时列表行多出 Checkbox（`DR:162-164`）→ 行布局变化。 |
| 8 | 滚动语义 | 列表滚动。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | `sessions` 为空时「选择」不显示（`DR:68`）→ 不能进入批处理；批处理态无会话选择时批量按钮 disabled（`DR:108-111`）。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `DR:56-126`、`MA:350-359`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 只能逐条长按整理会话，批量归档/删除能力不可达。**候选** `LEG-41`：进入/退出批处理且选择清空（真机）。 |

#### `sidebar.batch.select-all`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.batch.select-all`。属 sidebar → 批处理头部。出现条件：批处理态（`DR:62-65`）；每屏 1 个。 |
| 2 | 网格与定位 | 头部"已选 N"右侧、「完成」左侧（`DR:62-65`）。 |
| 3 | 命中区 | TextButton 默认。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `onSelectAll` → `selectedIds = sessions.map{it.id}.toSet()`（`MA:359`）。注意：选择的是**当前 sessions 列表**（搜索过滤后的结果）全部，而非全部会话（`MA:359` 与 `MA:259-265` 的搜索联动）。幂等。 |
| 6 | 视觉状态 | 无 selected 态（按钮不带勾选标记）。 |
| 7 | 布局占位与展开形态 | 常驻头部。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 搜索中全选只选中搜索结果（可为 0 条，批量按钮仍 disabled）。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `DR:62-65`、`MA:259-265,359`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 会话多时批量操作退化为逐条点，等于没有批量。**候选** `LEG-42`：全选后批量按钮可用且计数正确。 |

#### `sidebar.batch.archive`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.batch.archive`。属 sidebar → 批量条。出现条件：批处理态（`DR:102-114`）；1 个，`enabled = selectedIds.isNotEmpty()`（`DR:108`）。 |
| 2 | 网格与定位 | 底部 Row（padding h12/v8，间距 8dp）第一个 TextButton「归档」（`DR:103-108`）。 |
| 3 | 命中区 | TextButton 默认。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `onBatchArchive`（`MA:360-376`）：对每个 id `Command.SessionArchive(it, true)` → 清批处理/关抽屉 → 若当前会话被归档则开新草稿，否则刷新列表 → Snackbar「已归档 N 个会话」+「撤销」→ 撤销时逐个 `SessionArchive(it,false)`（`MA:364-374`）。可撤销（Snackbar 短时窗内）。 |
| 6 | 视觉状态 | disabled（无选择）；Snackbar 出现。 |
| 7 | 布局占位与展开形态 | 常驻底部条；Snackbar overlay（`MA:477-480`）。 |
| 8 | 滚动语义 | 列表滚动。 |
| 9 | 动效 | 抽屉关闭；Snackbar 默认。 |
| 10 | 边界态 | 部分失败：`forEach { bridge.send }` **忽略每个返回值**（`MA:364`）→ 失败不会提示，仍显示"已归档 N 个"；撤销在 Snackbar 消失后不可用。 |
| 11 | 无障碍 | 文本可读；Snackbar 会被 TalkBack 播报（Material 默认）。 |
| 12 | 依据 | `E0` `DR:101-114`、`MA:360-376`、`IB:233-240`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 批量整理只能逐条（用户明确有的会话整理需求）。**候选** `LEG-43`：批量归档后列表消失且撤销可恢复。 |

#### `sidebar.batch.delete`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.batch.delete`。属 sidebar → 批量条。批处理态（`DR:102-114`）；1 个，`enabled = selectedIds.isNotEmpty()`（`DR:109`）。 |
| 2 | 网格与定位 | 底部 Row 第二个 TextButton「删除」（error 色文本，`DR:109-111`）。 |
| 3 | 命中区 | TextButton 默认。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `onBatchDelete`（`MA:377-393`）：逐个 `Command.SessionDelete(it)`（软删）→ 清批处理/关抽屉 → 当前会话被删则开新草稿 → Snackbar「已删除 N 个会话」+「撤销」→ 撤销 `SessionRestore`（`MA:381-391`）。**软删**，可从回收站恢复/永久删（`MA:1496-1575`）。 |
| 6 | 视觉状态 | disabled；文本 error 色。 |
| 7 | 布局占位与展开形态 | 底部条。 |
| 8 | 滚动语义 | 列表滚动。 |
| 9 | 动效 | Snackbar。 |
| 10 | 边界态 | 同批量归档：逐个返回值被忽略（`MA:381`）；撤销窗口短。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `DR:101-114`、`MA:377-393`、`IB:209-224`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 批量删除只能逐条长按；且用户失去"一次清理多条"的能力。**候选** `LEG-44`：批量软删后进回收站且可从回收站恢复。 |

#### `sidebar.row.select`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.row.select`。属 sidebar → 会话行。出现条件：每个会话 1 个（`DR:88-97`）。实例：行点击 `DR:94`、`combinedClickable` `DR:158`、批处理 Checkbox `DR:163`。 |
| 2 | 网格与定位 | 行：padding h8、圆角 control、选中态 secondaryContainer 背景；内部 padding h12/v8；标题（可横向拖）+ 片段 + 时间（`DR:151-189`）。 |
| 3 | 命中区 | 整行（`DR:158`）；Checkbox 默认 48dp（`DR:163`）。 |
| 4 | 手势 | 单击：非批处理=打开会话；批处理=勾选/取消（`DR:94`）。长按：非批处理=打开行菜单（`DR:95,158`）。标题可横向拖动查看（`DR:171`，与行点击共存）。 |
| 5 | 行为 | 非批处理：`showChat(Screen.Chat(id,title))` + 关抽屉（`MA:334-341`）；批处理：`onToggleSelected`（`MA:356-358`）；长按：`rowMenu = row`（`MA:342`）。打开会话触发 `ChatScreen` 的 `Query.SessionEvents/SessionModel`（`MA:763-788`）。无写 Command；打开会话幂等。 |
| 6 | 视觉状态 | selected（当前会话）背景高亮（`DR:156,170`）；批处理勾选 Checkbox；无 pressed 自定义（`DR:157` pressable 缩放，`MO:32-41`）。 |
| 7 | 布局占位与展开形态 | 行定高（内容 2-3 行）；分组头（置顶/今天/…，`DR:83-87`）。 |
| 8 | 滚动语义 | 列表 LazyColumn 滚动；时间靠右。 |
| 9 | 动效 | `pressable` 缩放（`MO:35-40`）。 |
| 10 | 边界态 | 标题超长：单行 + 可横滑（`DR:166-172`）；无会话时显示"还没有会话/没有匹配"（`DR:73-79`）；搜索结果 snippets 来自 `SessionSearch`（`IB:856-874`）。 |
| 11 | 无障碍 | 行是 combinedClickable Box，无 role/selected 语义（E0 现状缺口）。 |
| 12 | 依据 | `E0` `DR:81-99,140-190`、`MA:326-395`、`IB:817-874`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 无法切会话（App 的核心导航）；长按入口丢失则无法改名/归档/删除。**候选** `LEG-45`：点击切会话且抽屉关闭；长按只有菜单不切会话（真机手势）。 |

#### `sidebar.row.menu-open`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.row.menu-open`。属 sidebar → 会话行 → 行菜单入口。出现条件：非批处理态每行 1 个「⋯」（`DR:184-188`）；与长按同一动作。 |
| 2 | 网格与定位 | 行尾，`contentPadding 4dp` 的 TextButton「⋯」（`DR:185-187`）。 |
| 3 | 命中区 | TextButton 默认（会撑高行）。 |
| 4 | 手势 | 单击（与长按等效）。 |
| 5 | 行为 | `onLongClick` → `onSessionLongPress(item)` → `rowMenu = sessions.firstOrNull{...}`（`MA:342`）。无 Command；幂等。 |
| 6 | 视觉状态 | 默认。 |
| 7 | 布局占位与展开形态 | 行内常驻。 |
| 8 | 滚动语义 | 列表滚动。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 批处理态不显示（`DR:184` 条件）。 |
| 11 | 无障碍 | 「⋯」无 contentDescription（缺口）；长按有同等能力但不可发现。 |
| 12 | 依据 | `E0` `DR:184-188`、`MA:342`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 长按是隐藏手势，新用户永远发现不了改名/删除入口（可发现性）。**候选** `LEG-46`：点击「⋯」弹的行菜单与长按一致。 |

#### `sidebar.footer.settings`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.footer.settings`。属 sidebar → 抽屉底部。出现条件：非批处理态（`DR:115-124`）；1 个。 |
| 2 | 网格与定位 | 底部 Row（padding h12/v8）：TextButton「设置」+ 弹性空间 + 文案「本地优先 · 离线可用」（`DR:116-123`）。 |
| 3 | 命中区 | TextButton 默认。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `onSettings` → 关抽屉 + `openSettings()`（`MA:344-349`、`MA:246-250`）。本地导航，无 Command。 |
| 6 | 视觉状态 | 默认。 |
| 7 | 布局占位与展开形态 | 常驻底部。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 设置页从右滑入（`MA:246-250`）。 |
| 10 | 边界态 | 宽屏常驻侧栏时同样可点。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `DR:115-124`、`MA:344-349,246-250`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 设置页没有任何入口（模型/provider/主题全不可配）。**候选** `LEG-47`：点击进入设置页（真机）。 |

#### `sidebar.row-menu.actions`（置顶 / 归档 / 重命名 / 删除）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.row-menu.actions`。属 sidebar → 会话行菜单。出现条件：`rowMenu != null`（`MA:590-627`）；4 个 TextButton：置顶（`MA:596`）/归档（`MA:602`）/重命名（`MA:619`）/删除（`MA:620`）。 |
| 2 | 网格与定位 | AlertDialog `text` 内纵向 Column；标题=会话标题（`MA:593`）。 |
| 3 | 命中区 | TextButton 默认 ≥48dp。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | 置顶 → `Command.SessionPin(id, !pinned)`（`MA:599`）；归档 → `SessionArchive(id,true)` + Snackbar 撤销（`MA:606-616`）；重命名 → 打开重命名框（`MA:619`）；删除 → 打开删除确认（`MA:620`）。置顶/归档可逆（归档有撤销；置顶再点取消）；重命名/删除见各自节点。 |
| 6 | 视觉状态 | 「删除」error 色（`MA:621`）；其余普通。 |
| 7 | 布局占位与展开形态 | Dialog overlay。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | Dialog 默认。 |
| 10 | 边界态 | 当前会话被归档/删除时：关抽屉并开新草稿（`MA:605-608`、`MA:663-666`）；非当前会话：抽屉保持打开便于连续整理（注释 `MA:607,665`）。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `MA:590-627`、`IB:225-240`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 会话无法整理（置顶/归档/改名/删除全丢）。**候选** `LEG-48`：四个动作各自生效且失败有反馈（当前失败仅 snackbar 不出现，无错误提示）。 |

#### `sidebar.rename.save`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.rename.save`。属 sidebar → 重命名对话框。出现条件：`renameTarget != null`（`MA:628-652`）；保存按钮 `MA:641`。 |
| 2 | 网格与定位 | AlertDialog：标题「重命名」+ 单行输入框 + 取消/保存（`MA:629-651`）。 |
| 3 | 命中区 | TextButton 默认。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `Command.SessionRename(target.id, renameText)`（`MA:645`）→ reload 列表（`MA:646`）；聊天顶栏标题经 `SessionUpdated` 事件同步（`MA:308-324`）。可再次改名覆盖（可逆）。 |
| 6 | 视觉状态 | 默认；输入框未做前端空校验，空标题会由后端 `require` 拒绝（`MA:645`、`SS:277-280`），但 UI 不显示错误。 |
| 7 | 布局占位与展开形态 | Dialog overlay。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | Dialog 默认。 |
| 10 | 边界态 | 空标题/超 80 字 → 后端 `require` 抛出（`SS:279-280`）→ `SESSION_RENAME` 错误被**丢弃**（`MA:644-647` 忽略返回值）→ 用户看到对话框关闭但标题没变；超长标题同上。 |
| 11 | 无障碍 | 输入框无 label（仅 Dialog 标题），TalkBack 可能读不出字段含义（现状缺口）。 |
| 12 | 依据 | `E0` `MA:628-652`、`IB:202-208`、`SS:277-289`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 所有会话都叫"新会话/首句"，无法辨认。**候选** `LEG-49`：改名后列表与顶栏同步更新；空标题被拒或有提示（P2 定）。 |

#### `sidebar.delete.confirm`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.delete.confirm`。属 sidebar → 删除确认框。出现条件：`deleteTarget != null`（`MA:653-681`）；确认按钮 `MA:659`。 |
| 2 | 网格与定位 | AlertDialog 标题「删除会话？」+ 文案「会进入回收站，可撤销」+ 取消/删除（`MA:654-680`）。 |
| 3 | 命中区 | TextButton 默认。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `Command.SessionDelete(id)`（软删，`MA:664`）→ 当前会话则开新草稿，否则刷新（`MA:663-666`）→ Snackbar「已删除 X」+撤销 → `SessionRestore`（`MA:667-675`）。**软删**，回收站可恢复/永久删（`MA:1496-1575`）。可撤销（短窗）。 |
| 6 | 视觉状态 | 默认。 |
| 7 | 布局占位与展开形态 | Dialog overlay。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | Dialog + Snackbar。 |
| 10 | 边界态 | 撤销窗过期 → 只能去回收站恢复（入口在设置，`MA:1696`）。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `MA:653-681`、`IB:209-224`。 |
| 13 | 反证 + 验收 | **反证**：不保留（改成直接删） → 误触即丢会话；保留确认=给误触一次反悔。**候选** `LEG-50`：删除后 Snackbar 撤销可恢复会话（真机）。 |

#### `sidebar.bin.restore`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.bin.restore`。属 sidebar → 归档页/回收站 → 行内恢复。出现条件：bin 有行（`MA:1537-1548`）；每行 1 个，文案「取消归档」或「恢复」（`MA:1544`）。 |
| 2 | 网格与定位 | 每行 Column：标题 + 时间 + 操作 Row（恢复/永久删除，间距 8dp）（`MA:1534-1551`）。 |
| 3 | 命中区 | TextButton 默认。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | 归档模式 → `Command.SessionArchive(id,false)`；删除模式 → `Command.SessionRestore(id)`（`MA:1540-1542`）→ reload 本页（`MA:1542`）。可逆（可再归档/删除）。 |
| 6 | 视觉状态 | 默认。 |
| 7 | 布局占位与展开形态 | 列表行。 |
| 8 | 滚动语义 | LazyColumn 滚动。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 失败无提示（`MA:1539-1543` 忽略返回值）；恢复后抽屉列表要等下次刷新才看到（本页只 reload 自己）。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `MA:1496-1555`、`IB:217-240`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 归档/软删成单向操作，用户会不敢删。**候选** `LEG-51`：恢复后会话回到抽屉列表。 |

#### `sidebar.bin.permanent-delete`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.bin.permanent-delete`。属 sidebar → bin → 打开永久删除确认。每行 1 个（`MA:1545-1547`）。 |
| 2 | 网格与定位 | 行内操作 Row 第二个 TextButton「永久删除」（error 色，`MA:1545-1547`）。 |
| 3 | 命中区 | TextButton 默认。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `permanentTarget = row` → 弹确认框（`MA:1557-1574`）。无 Command；幂等。 |
| 6 | 视觉状态 | error 色文本。 |
| 7 | 布局占位与展开形态 | 常驻行内。 |
| 8 | 滚动语义 | 列表滚动。 |
| 9 | 动效 | Dialog。 |
| 10 | 边界态 | 归档页也显示「永久删除」（同一行操作，`MA:1537-1548`）→ 归档中的会话可被一步永久删除（P2 需确认是否符合预期，Q-LEG-02）。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `MA:1537-1548,1557-1574`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 无法真正清空回收站（数据只增不减/附件不释放）。**候选** `LEG-52`：确认后本页行消失。 |

#### `sidebar.bin.permanent-confirm`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.bin.permanent-confirm`。属 sidebar → 永久删除确认框。出现条件：`permanentTarget != null`（`MA:1557-1574`）；确认按钮 `MA:1563`。 |
| 2 | 网格与定位 | AlertDialog 标题「永久删除？」+ 文案「日志与附件会被删除，不可恢复」+ 取消/永久删除（`MA:1558-1573`）。 |
| 3 | 命中区 | TextButton 默认。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `Command.SessionDeletePermanent(id)`（`MA:1567`）→ reload。**不可恢复**（后端删除日志与附件，`IB:241-248`、`SS:263-275`）。 |
| 6 | 视觉状态 | 确认按钮 error 色（`MA:1570`）。 |
| 7 | 布局占位与展开形态 | Dialog overlay。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | Dialog。 |
| 10 | 边界态 | 失败 → 无提示（`MA:1566-1569` 忽略返回值）。 |
| 11 | 无障碍 | 文本可读。 |
| 12 | 依据 | `E0` `MA:1557-1574`、`IB:241-248`、`SS:263-275`。 |
| 13 | 反证 + 验收 | **反证**：不保留二次确认 → 误触即永久丢会话+附件。**候选** `LEG-53`：确认后会话与附件文件都不存在（真机 + files 检查）。 |

### 2.7 `base.primitives`（2 节点）

#### `base.primitives.dialog-dismiss`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.dialog-dismiss`。属 base.primitives → 所有 AlertDialog/Dialog 的取消/关闭。出现条件：任意 AlertDialog 打开时。实例 11 处：`MA:625,650,679,1334,1357,1439,1572,1814,1869,2012,2035`；另有各 `onDismissRequest`（`MA:592,630,655,1319,1339,1364,1559,1792,1855,2024`，scanner 盲区）。 |
| 2 | 网格与定位 | Material AlertDialog 的 `confirmButton`/`dismissButton` 槽位（右下角），未自定义。 |
| 3 | 命中区 | TextButton 默认 48dp；另可用系统返回键/点外部关闭（`onDismissRequest`）。 |
| 4 | 手势 | 单击；系统返回键（各 Dialog 的 onDismissRequest）。 |
| 5 | 行为 | 置对应状态为 null（`rowMenu=null` / `renameTarget=null` / …）→ 关闭不执行任何写命令。幂等。 |
| 6 | 视觉状态 | 默认。 |
| 7 | 布局占位与展开形态 | 浮层，不占位。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | Material Dialog 默认。 |
| 10 | 边界态 | 表单类对话框关闭 = **丢弃未保存输入**（provider 编辑器/编辑消息），无二次确认（`MA:2012`、`MA:1357`）。 |
| 11 | 无障碍 | 文本可读（关闭/取消）。 |
| 12 | 依据 | `E0` `MA:590-681,1317-1359,1360-1441,1557-1574,1790-1816,1853-1889,1893-2037`。 |
| 13 | 反证 + 验收 | **反证**：不保留取消/关闭 → 对话框成单向陷阱（想反悔只能杀进程）。**候选** `LEG-54`：任意弹层可关闭且不产生副作用。 |

#### `base.primitives.topbar-back`

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.topbar-back`。属 base.primitives → 顶栏返回。出现条件：传入 `onBack` 的页面：设置页（`MA:1661`）与归档/回收站页（`MA:1520`）；每屏 1 个。旧实例 `CP:69`。 |
| 2 | 网格与定位 | TopAppBar `navigationIcon`，文本「‹」26sp（`CP:69-71`）。 |
| 3 | 命中区 | Material TextButton 默认 48dp。 |
| 4 | 手势 | 单击；另可用系统预测式返回（`MA:505-543`）与右滑（`MA:433-476`）。 |
| 5 | 行为 | 设置页 → `settingsNav.exit { leaveSettings() }`（`MA:452`，回上一个聊天窗口或开草稿 `MA:401-408`）；bin → `binNav.exit { screen = Settings }`（`MA:472`）。本地导航，无 Command；幂等。 |
| 6 | 视觉状态 | 默认。 |
| 7 | 布局占位与展开形态 | 顶栏常驻。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 右滑出动画 180ms（`PN:84-96`）。 |
| 10 | 边界态 | 从无聊天基座进设置（如首次启动异常）→ 返回时开草稿（`MA:405-407`）；三套返回通道（按钮/系统手势/右滑）写同一 progress（`PN:34-114`）。 |
| 11 | 无障碍 | 「‹」是符号不是文字，无 contentDescription（缺口）；TalkBack 可能读作未知字符。 |
| 12 | 依据 | `E0` `CP:56-77`、`MA:433-476,505-543,401-408,1520,1661`、`PN:34-177`。 |
| 13 | 反证 + 验收 | **反证**：不保留 → 设置/回收站只能靠系统手势退出（可发现性差，且大屏用户习惯点返回）。**候选** `LEG-55`：点击返回回到进入前的聊天窗口且阅读位置不丢（`MA:238-239` 每会话滚动状态复用）。 |

### 2.8 scanner 盲区交互（人工审计，不入 facts.csv 键）

> **2026-11-27 第 2 轮补扫后更新**：scanner 新增 `kt.gesture`/`kt.widget`，本表 16 项里 **9 项已可用合法 facts 键登记**
> （见文末「附2」）；剩余 7 项仍无 scanner kind，继续只在此表登记。
> 下列原始说明保留（历史依据）：这些点确实可交互，但当时 scanner 的 kind 只有 `onClick`/`disclosure` 类正则，无法为它们产出合法 facts 键；
> 强制写进 facts 会被对账工具报成"失效条目"。因此在此登记，P3 写 `panels/*.md` 时按 13 字段补全。

| node_id | 位置（E0） | 交互 / 行为 | 命令或回调 |
|---|---|---|---|
| `composer.input` | `CP:387-395`（旧 OutlinedTextField）/ `MC:97-111`（M5 BasicTextField） | 文本输入；placeholder「说点什么…」；maxLines=4（旧） | 无（发送时才 `ChatSend`） |
| `sidebar.search` | `DR:72` → `CP:206-215`；`MA:302,333` | 输入即查询：空=SessionList，非空=SessionSearch（每键一次，无 debounce） | `Query.SessionList` / `Query.SessionSearch` |
| `settings.search` | `MA:1667`；过滤逻辑 `MA:1599-1605` | 按关键词显隐 5 个分组；无匹配时只剩搜索框（无空态） | 无（纯本地过滤） |
| `chat.msg.link` | `CC:402-416`（`withLink(LinkAnnotation.Url)`） | 点正文内 Markdown 链接 → 系统浏览器 | `LocalUriHandler.openUri`（`CC:409`） |
| `chat.source.section` | `CC:1169-1235` | 回复下方「来源 N 个网页 ›」→ 打开来源 sheet；入口行整行可点（裸 `.clickable`） | `LocalUriHandler.openUri`（`CC:1205`） |
| `chat.source.item` | `CC:1200-1229` | sheet 内每个来源项 → 打开网页 | `LocalUriHandler.openUri`（`CC:1205`） |
| `chat.tool.source.item` | `CC:983-998` | 工具卡展开后前 3 个来源 → 打开网页 | `LocalUriHandler.openUri`（`CC:985`） |
| `model.sheet.model-row` | `MS:100-123`（`onPickModel`） | M5 面板内模型行 → 立即提交并关面板（`MA:1465-1477`） | `Command.SessionSetModel` |
| `model.sheet.thinking-switch` | `MS:87-96` + `MS:169-186`（自绘 Switch） | M5 面板内「深度思考」开关 | 写 prefs（`MA:1461-1464`），无 Command |
| `settings.provider-editor.fields` | `MA:1916-2030` | 12 个输入框（provider id/名称/BaseURL/APIKey；模型 id/名称/ctx/max；efforts low/medium/high）+ 3 个 Checkbox（文本/图片/支持思考） | 无（保存时 `ProviderSave`） |
| `attachment.sheet.dismiss` | `MA:1293` | 下滑/点外部关闭附件 sheet | 无 |
| `chat.list.scroll-follow` | `MA:1069-1119` | 列表跟随流式内容逐帧贴底；用户拖动即停跟随（`MA:1072-1082`） | 无 |
| `chat.process.inner-scroll` | `CC:779-835` | 过程组内滚：自己分发/自己 fling；到边时把**新手势**交外层（G-04 的旧实现） | 无 |
| `chat.drawer.swipe-open` | `CC:710-740` + `MA:421-422,1122-1125` | 聊天正文右滑 ≥96dp 打开抽屉（只观察不消费） | 无 |
| `base.nav.swipe-back` | `MA:433-476,505-543` + `PN:122-177` | 设置/bin 右滑退出（超 22% 或速度>600px/s 提交）；左缘 20dp 让给系统手势 | 无 |
| `chat.drawer.system-back` | `MA:483-503` | 抽屉打开时系统返回 = 预测式关闭 | 无 |

---

## 3. 命令映射表

| 节点 | 用户动作 | 发出的 Command / 回调（E0） | 后端能力（core，E0） | 缺口 / 备注 |
|---|---|---|---|---|
| `composer.send-stop` | 发送 | `Command.ChatSend(sessionId,text,attachments,requestId=null,thinking,webSearch,providerId,modelId,reasoningLevel)` `MA:962-968` | `IB:459-791`：会话锁 → 分支上下文 → 附件内联 → 工具循环（≤64 步）→ 落盘 `turn/start,user/message,tool/*,assistant/message,turn/end`；错误码 `PROVIDER_MISSING/MODEL_MISSING/REASONING_UNSUPPORTED/TOOL_BUDGET/ATTACHMENT_NOT_FOUND/LLM_FAILED/MAX_STEPS` | UI **不传 requestId**（幂等键形同未用）；无排队/steer；`thinking` 会被 `reasoningLevel` 覆写（`IB:490-494`）；首 token 前可重入（§5.3-a） |
| `composer.send-stop` | 停止 | `Command.ChatStop(sessionId)` `MA:1248,1270` | `IB:338-347` 取消 job；部分文本按 `finishReason=stopped` 落盘并正常收尾（`IB:716-757`） | 无进行中轮次 → `NOT_RUNNING`，UI 忽略该错误（`MA:1248` 不处理返回值） |
| `chat.action.row` | 重新生成 | `Command.ChatRegenerate(sessionId,messageId)` `MA:997` | `IB:402-434`：同 nodeId `version+1`，上下文截到父 user 之前；旧版本保留 | 无 nodeId/turn → `MESSAGE_NOT_VERSIONED`；UI 先 `activeLeaf=parentId`（`MA:991`） |
| `chat.msg.edit-dialog.send` | 编辑重发 | `Command.ChatEditResend(sessionId,messageId,text)` `MA:1014` | `IB:436-457`：旧消息 `revises`、新消息同 parentId；旧日志不进后续上下文（`IB:793-815`） | 空文本前端静默不提交（`MA:1354`） |
| `sidebar.header.new-chat` | 新对话 | `Command.SessionEnsureDraft()` `MA:276` | `IB:69-77`：复用唯一空白草稿，否则创建 | `Command.SessionCreate`（`IB:131-135`）**无任何 UI 调用** |
| `chat.branch.user-switch` / `chat.action.row` | 切分支/版本 | `Command.SessionSetActiveLeaf(sessionId, leaf)` `MA:1145,1171`（`CC:663`） | `IB:122-130` 写 sidecar `activeLeaf` | 无 |
| `sidebar.row.select` | 打开会话 | `Query.SessionEvents` + `Query.SessionModel` `MA:764-765` | `IB:824-844`：事件回放 + 会话模型选择 + provider/model 可用性 | 返回的 `providerAvailable/modelAvailable` **UI 未消费**（`MA:864-877`） |
| `sidebar.search` | 搜索 | `Query.SessionList` / `Query.SessionSearch(query)` `MA:261` | `IB:818-823`、`IB:856-874`；`SS:304-...` 搜标题+正文，返回 `snippet/seq/role` | UI 未用 `seq` 做"跳到命中消息"，只当列表过滤 |
| `sidebar.row-menu.actions` | 置顶 | `Command.SessionPin(id, pinned)` `MA:599` | `IB:225-232` | 无 |
| `sidebar.row-menu.actions` / `sidebar.batch.archive` / `sidebar.bin.restore` | 归档 / 取消归档 | `Command.SessionArchive(id, archived)` `MA:606,615,364,372,1540` | `IB:233-240` | Snackbar 撤销窗口短；批量调用**忽略每个返回值**（`MA:364`） |
| `sidebar.row-menu.actions` / `sidebar.delete.confirm` / `sidebar.batch.delete` | 软删 | `Command.SessionDelete(id)` `MA:664,381` | `IB:209-216`（只写 `deletedAt`，`SS:289-295`） | 批量忽略返回值（`MA:381`） |
| `sidebar.bin.restore` | 恢复 | `Command.SessionRestore(id)` `MA:389,673,1541` | `IB:217-224`（清 `deletedAt`） | 失败无提示 |
| `sidebar.bin.permanent-confirm` | 永久删除 | `Command.SessionDeletePermanent(id)` `MA:1567` | `IB:241-248` → `SS:262-268` 删会话目录 + 附件目录 | 不可恢复；失败无提示 |
| `sidebar.rename.save` | 改名 | `Command.SessionRename(id, title)` `MA:645` | `IB:202-208`；`SS:277-287` 空/超 80 字抛错 | 错误被丢弃（`MA:644-647`） |
| `model.picker.*` / `model.sheet.*` | 选模型 / 思考强度 | `Command.SessionSetModel(sessionId, providerId, modelId, level)` `MA:1385,1416,1427,1467,1480` | `IB:190-201` 写 sidecar；实际生效在下一轮 `IB:461-495` | 写入时不校验 efforts 合法性 → 发送时才 `REASONING_UNSUPPORTED` |
| `settings.provider-editor.save` | 保存 provider | `Command.ProviderSave(profile, revision)` `MA:1836` + `Command.CredentialSet(ref, key)` `MA:1839` | `IB:294-308`（merge 后整份写，revision 乐观锁）；`PS:45-58` 严格校验；`IB:251-257` 只写凭据 | 先存 provider 再存 key：key 失败时 provider 已落盘（部分成功） |
| `settings.provider.delete-confirm` | 删 provider | `Command.ProviderDelete(id, revision)` `MA:1862` | `IB:310-325`（`PROVIDER_NOT_FOUND`/`SETTINGS_CONFLICT`） | 无 |
| `settings.provider.import` | 导入 | `Command.ProviderReplace(list, revision)` `MA:1650` | `IB:282-291` 整份替换 + revision；成功后 `BridgeHolder.rebuild`（`BH:33-47`） | 无 merge；失败时 UI 文案走"导入失败" |
| `settings.provider.export` | 导出 | `Query.ProviderExport` `MA:1623` | `IB:882-893`（含 schemaVersion/revision/providers，**不含密钥值**） | 无 |
| `settings.provider-editor.discover` | 测试连通性 | `Command.ProviderDiscover(profile)` `MA:1825` | `IB:258-281` + `DI:29-101`（`/models`、`/v1/models`，404 换端点，鉴权错立即抛） | 结果只显示字符串，**不回填表单**（`MA:1827`） |
| `settings.key.save` | 设置密钥 | `Command.CredentialSet(ref, value)` `MA:1882` | `IB:251-257`（只写不读，`BR:76-77`） | 无"清除密钥"入口 |
| `composer.voice` | 语音输入 | `Command.TranscribeAudio(name,mime,base64)` `MA:834-840` | `IB:151-167` → DeepSeek ASR（`DS`/`CT` 能力端点） | 仅 `api.deepseek.com` provider；否则 `ASR_UNAVAILABLE` |
| `attachment.sheet.pick-source` | 添加附件 | `Command.AttachmentSave(sessionId,name,mime,base64)` `MA:797-804` | `IB:136-149` → `AS:23-38`（kind=image/text/file，图片 32MB / 其它 8MB） | **无"读附件/预览/删除附件"的 Command 或 Query**；`kind=file` 发送时只送元数据（`IB:533`） |
| `composer.pending.attachments` | 移除附件 | 无 Command（只改内存 `pending`）`MA:1230,1235` | — | 已落盘文件成孤儿：只有**永久删除会话**才清附件目录（`SS:262-268`） |
| `settings.foreground.toggle` | 启停常驻 | 无 Command（本地 Service Intent `MA:1755,1759`） | `FG:25-35` 前台服务 + 通知 | `running` 不查真实服务状态（`MA:1596`） |
| `settings.appearance.theme-chip` | 主题 | 无 Command（`ThemePrefs.save` `MA:162-165`） | — | 无 |
| `chat.code.copy` / `chat.action.row` 复制 / `chat.msg.user-menu.actions` 复制 | 复制 | 剪贴板（`CC:438`、`MA:1158-1160`）；用户菜单**未写剪贴板**（`MA:1323-1326`） | — | 用户菜单"复制"= 假功能 |
| `chat.scroll.back-to-latest` | 回到底部 | 本地滚动 `MA:1204-1207` | — | 挂 Q-07（是否换 DSH 式轮次导航） |
| `chat.source.*` / `chat.tool.source.item` / `chat.msg.link` | 打开来源/链接 | `LocalUriHandler.openUri` `CC:409,985,1205` | — | 无 |
| `chat.empty.suggestion` | 点建议卡 | 本地 `input = text` `MA:1149` | — | 无 |
| `chat.process.*` / `chat.live.*` / `chat.msg.user-long-press` / 导航类 | 开合/长按/导航 | 本地状态与 `screen`/`drawerState`（`MA:223-256`） | — | 无 |
| —（无 UI） | 后端有、用户不可主动触发 | `SessionCreate`、`EventReplay`、`SessionGenerateTitle`（发送后自动）、`DoctorRun`（设置页自动） | `IB:131-135,845-855,78-121,894-898` | 无入口 |
| —（无 UI） | 模型可调但用户不可见 | `time_now`、`web_search`、`read_url`、`ocr_image`、`transcribe_audio` 工具 | `TR:59-71`、`CT:34-162` | 用户看不到工具清单/单次成本 |

**后端缺口汇总**（UI 有意图 / 面板注册表要求，但 core 契约没有对应 Command/Query）：

1. 附件**读取/预览/删除**（`BR:119-144` 只有 `AttachmentSave`；`AS` 无 delete/read-for-UI）→ attachment 面板无法做预览/进度/最近照片。
2. 排队 / steer（输入区在流式中不可再发，`MA:942-944` 直接 return；无 queue 命令）→ composer 做不了 DSH 式排队。
3. 触发词 / slash 命令面板（`BR:13-117` 无相关命令）→ composer 的命令面缺失。
4. 审批 / 权限预设 / 用户提问（无 Command、无 Event）→ approval 面板不可能实现。
5. agent preset（`Command` 无 preset 切换；`SessionCreate(preset=…)` 只有默认 `chat-default`，`IB:74`）→ model 面板无法合并 agent preset。
6. provider 启停（`ProviderProfile.enabled` 字段存在 `MD:111`，但无"只改 enabled"的 Command；UI 也没有开关）→ 只能删除/重导入。
7. 会话内消息跳转（`SessionSearch` 返回 `seq/role`，但 UI 没有"打开会话并滚到 seq"的 Query 组合）。
8. 消息反馈（赞/踩）、trajectory/用量面板（`Usage` 只在 `turn/end` 里，`EV:155-176`；无专用 Query）。
9. 计划/目标/作业/定时任务/子代理/技能/交付物：**core 无任何相关类型**（`EV:13-177` 只有 8 类事件）。

---

## 4. 旧 UI vs M5 差异表

> M5 的适用范围：仅当 `m5_chat_ui=true`（`MA:166`）时才替换 **顶栏/消息列表/输入区/错误条/回到底部/模型面板**；
> 抽屉、设置、附件 sheet、回收站、旧模型选择器仍是旧实现。M5 文件集中 `ui/m5/*`。

| # | 交互 | 旧路径（E0） | M5 路径（E0） | 一致？ | 行为差异 / 风险 |
|---|---|---|---|---|---|
| 1 | 顶栏菜单钮 | `CP:66` / `MA:418` | `CR:67` / `MA:420` | **不一致** | 宽屏 `onMenu=null` 时 M5 是 `onClick={}` 死按钮；旧 UI 不渲染 |
| 2 | 顶栏标题/副标题 | `CP:56-77`（仅标题） | `CR:55-80`（标题+副标） | 不一致 | M5 副标取 provider 名，回退写死 `"DeepSeek"`（`MA:1028`）→ provider 未选时品牌错误 |
| 3 | 用户气泡长按菜单 | `CP:328` → `MA:1317-1336` | `CR:258` → `MA:1317-1336` | 一致（共用旧 Dialog） | 视觉未 M5 化（仍是旧 Material AlertDialog） |
| 4 | 用户消息「复制」 | `MA:1323-1326` | 同左（共用） | 一致（**都缺**） | 只 Toast 不写剪贴板 |
| 5 | 助手操作条 | `CC:1477-1496`（复制/重生成/版本‹›） | `CR:600-607`（复制/重生成图标） | **不一致** | M5 无 disabled 态、图标无 a11y、**无版本切换** |
| 6 | 重新生成 | `CC:1488` → `MA:984-1001` | `CR:605` → `MA:984-1001` | 一致 | 入口形态不同（文字 vs 图标） |
| 7 | 助手版本切换 | `CC:1489-1494` | **未见实现**（形参 `CR:102` 未用） | **缺失** | 重生成后 M5 无法回看旧版本 → 功能丢失（最高危） |
| 8 | 用户分支切换 | `CC:1443-1475` | `CR:291-318` | 基本一致 | M5 增加锚点恢复（`CR:129-135`）；旧路径无锚点 → 切分支可能跳位（§5.3-b） |
| 9 | 过程组头 | `CC:1044-1082` | `CR:321-356` | 不一致 | 旧有底色 + 分类摘要（"已搜索网页/读取网页…" `CC:1051-1069`）；M5 无底色，摘要取最后一条（`CR:328-336`） |
| 10 | 流式过程头 | `CC:676`（固定展开 + `onToggle={}`） | `CR:548-589`（默认折叠可点） | **不一致** | 旧：点了没反应且收不起来；M5：可开合但默认收起（与旧"生成中必须看到过程"的意图相反） |
| 11 | 过程行 | `CC:907-948` | `CR:376-490` | 不一致 | 旧显示"进行中/失败/完成 + 已阅读 N 网页"；M5 用 icon+标题；M5 详情用 Inset 块（参数/来源/结果），旧是裸 Text |
| 12 | 过程组内滚到边 | `CC:779-835`（自定义吸收，到边交外层） | `CR:210-224`（裸 `verticalScroll`） | **不一致** | M5 违反 G-04：内滚到边残余速度传外层 |
| 13 | 来源区（回答下方） | `CC:669-670,1169-1235` | **未见实现** | **缺失** | M5 列表无 `SourcesSection` 调用 → 搜索引用链断 |
| 14 | 工具卡来源可点 | `CC:983-997`（可点开网页） | `CR:449-469`（纯 Text） | 不一致 | M5 来源不可点 |
| 15 | 代码块复制 | `CC:424-450`（共用 `MarkdownText`） | 同左（M5 复用 `MarkdownText` `CR:230,591`） | 一致 | — |
| 16 | 正文链接 | `CC:402-416` | 同左（复用） | 一致 | — |
| 17 | 回到底部 | `MA:1199-1208`（文字「↓ 回到最新」） | `CR:610-626`（36dp 圆钮） | 一致（行为）/视觉不同 | — |
| 18 | 空会话态 | **未见实现**（白屏） | `CR:122-125` + `CS:33-55` | **M5 新增** | 新能力，但文案承诺"记忆"（§5.5） |
| 19 | 输入区 | `CP:359-423`（OutlinedTextField + 发送/停止两按钮 + chips） | `MC:35-176`（BasicTextField 玻璃岛 + 单按钮切图标 + 胶囊） | 视觉不同，行为有差 | M5：发送无 disabled、深度思考胶囊=打开面板、无语音、无"停止按钮独立存在"的稳定位置 |
| 20 | 语音输入 | `CP:399` → `MA:826-848` | **未见实现** | **缺失** | M5 用户丢失语音输入 |
| 21 | 待发附件条 | `MA:1220-1237`（AssistChip） | `CS:77-132`（胶囊 + 11dp 关闭图标） | 基本一致 | M5 关闭热区过小（Q-03） |
| 22 | 错误条 | `InlineError` `MA:1182` | `M5ErrorBar` `MA:1179` | 视觉不同、行为一致 | **两者都不传 onRetry** → 不会出现"重试"按钮 |
| 23 | 模型面板 | `MA:1360-1441` AlertDialog：provider 分组标题 + RadioButton 行（含 ctx/max）+ 强度 chips + 「关闭」 | `MS:49-148` 底部面板：扁平模型行（provider 名副标）+ 强度 pills + 深度思考 Switch；选完即关（`MA:1474`） | **不一致** | M5 丢：provider 分组、ctx/max 展示、面板不自动关的手动确认；M5 增：面板内 thinking 开关。两者都缺：可用性提示、搜索、agent preset |
| 24 | 附件 sheet | `MA:1291-1315` | 同左（**未换肤**） | 一致 | M5 未覆盖 attachment 面板 |
| 25 | 抽屉/设置/回收站 | `DR`、`MA:1577-1890`、`MA:1496-1575` | 同左（**未换肤**） | 一致 | M5 未覆盖 sidebar/settings |
| 26 | 主题 | `TP` + `MA:162-165` | 同左 | 一致 | — |

**汇总**：26 项里 **13 处旧/M5 差异 = 9 项行为不一致（#1/2/5/9/10/11/12/14/23）+ 3 项 M5 直接缺失（#7/13/20）+ 1 项交互成本不同（#19）**，
另有 1 项 M5 新增能力（#18 空态）。其中 3 处缺失是真正的"功能丢失"（版本切换、来源区、语音输入），
另 12 处是同一功能的两套行为/视觉不同。**M5 的完成度 ≈ 聊天页局部（消息/过程/输入/模型面板），导航与设置仍是旧 UI。**

---

## 5. 可疑代码与缺陷

### 5.1 明确缺陷（E0 可直接复现）

| # | 位置 | 现象 | 影响 |
|---|---|---|---|
| D1 | `MA:1323-1326` | 用户消息「复制」只 `Toast("已复制")`，未调用剪贴板 | "复制"是假功能；旧/M5 共用同一 Dialog，两套 UI 都受影响 |
| D2 | `CC:676` | 流式过程头 `TimelineHeaderRow(expanded=true, onToggle={})`：绑了空回调 | 生成中过程块**无法收起**，长过程把会话撑长；点击无任何反馈（像卡死） |
| D3 | `CR:228-238` + `CR:102` | M5 助手操作条没有版本 ‹›，且 `onVersionSelected` 形参从未调用 | 重生成后旧版本在 M5 无入口 → 迁移后功能丢失 |
| D4 | `CR:93-248`（无 `SourcesSection` 调用）vs `CC:669-670` | M5 不渲染"来源 N 个网页"区 | 搜索型回答的引用不可见，可核查性丢失 |
| D5 | `CR:449-469` vs `CC:983-997` | M5 工具卡里来源是纯 `Text`，不可点 | 用户无法从工具结果跳到网页 |
| D6 | `MC:113-173` vs `CP:399` | M5 输入区没有语音入口 | 语音输入在 M5 消失 |
| D7 | `CR:67` + `MA:420` | `M5ChatTopBarReal` 菜单钮 `onClick = onMenu ?: {}`，宽屏 `onMenu=null` | 宽屏出现死按钮（点了没反应） |
| D8 | `CP:255-273`、`CS:136-170`、`MA:1179,1182` | 错误条支持 `onRetry`，但两个调用点都不传 | 网络/限流失败后用户没有一键重试，只能重新发 |
| D9 | `MA:1524-1525` | bin 页 loading 时 `Box(fillMaxSize())` 空白 | 进入归档/回收站先看到纯白，像卡住 |
| D10 | `MA:1599-1605,1663-1787` | 设置搜索无匹配时只剩搜索框，无"没有匹配"提示 | 用户以为设置页坏了 |
| D11 | `MA:1371-1408` | 模型选择器遍历 `providersUi.filter{enabled}`，为 0 时无空态 | 对话框空白无提示（新用户常见） |
| D12 | `MA:790-817,1230,1235` + `AS:20-72` | 选中附件即落盘；移除 chip 只改内存；`AttachmentStore` 无删除 API | 未发送/被移除的附件永久残留（只在永久删会话时清，`SS:262-268`） |
| D13 | `MA:644-647` | 重命名 `bridge.send` 返回值被丢弃 | 空标题/超 80 字（后端 `SS:279-280` 拒绝）时对话框关闭但标题没变，无错误提示 |
| D14 | `MA:364,381,1540,1542` | 批量归档/批量删除/恢复的 `forEach { bridge.send }` 全部忽略返回值 | 部分失败仍提示"已归档 N 个"；用户以为成功 |
| D15 | `MA:962-968` + `BR:18-19` | 发送不传 `requestId`（后端已支持幂等键） | 重试/重入会重复跑一轮，无法去重 |
| D16 | `MA:1827,2215-2218` | `ProviderDiscover` 结果只拼字符串显示 | 用户还得手抄模型 id/ctx，功能半成品 |
| D17 | `CC:264` | 分隔线正则写成 `Regex("^\\\\s*([-*_])(\\\\s*\\\\1){2,}\\\\s*$")`（4 个反斜杠 → 匹配字面 `\s`） | Markdown `---` 分隔线永不渲染为分隔线（落到普通段落）；同时 `line.contains('|')`（`CC:303`）把含竖线的普通段落误判为表格 |
| D18 | `CK:151`、`GL:201,241,352,355,530,538,552,563` | 10 处 `onClick = {}` 空实现 | 画廊是 adb-only 调试页（`GL:52-55`），不影响产品；但说明这些"可点元素"没有行为——盘点时必须排除，不能算功能 |

### 5.2 逻辑 / 一致性缺陷

| # | 位置 | 现象 | 影响 |
|---|---|---|---|
| L1 | `MA:962-968` + `IB:478-495` | 同时传 `thinking` 与 `reasoningLevel`；后端在 level 非空时用 `apiValue` 覆写 `thinking` | 对声明了 `reasoning.default` 的模型，UI 的"深度思考"开关**无效**（关了仍在推理） |
| L2 | `MA:1385,1467` + `IB:483` | 选模型时强制 `level=m.reasoningDefault`；而"默认"档（level=null）后端仍回落到 `model.reasoning.default` | "默认"≠"关闭"，用户没有"彻底关掉推理"的显式档（除非模型 efforts 里有 `off`） |
| L3 | `MA:741-743` vs `IB:190-201` | `thinking` 存全局 SharedPreferences（跨会话共享），`reasoningLevel` 存会话 sidecar（会话级） | 两套模型状态作用域不同：换会话后开关还在，强度却按会话变 → 行为难以预测 |
| L4 | `MC:130` vs `CP:408` | M5「深度思考」胶囊点击=打开模型面板；旧 UI 点击=直接切开关 | 同一意图两套操作成本；M5 用户要多一步才能关思考 |
| L5 | `MC:159-172` | M5 发送键无 disabled 态，空输入仍可点 | 点击无反馈（`MA:944` 静默 return），用户以为没点上 |
| L6 | `CR:604-605` | M5 助手复制/重生成图标无 disabled、无 a11y 标签 | 首/末版本语义缺失（旧 UI 用 disabled） |
| L7 | `MA:864-877` vs `IB:835-842` | 后端返回 `providerAvailable/modelAvailable`，UI 只取 providerId/modelId/reasoningLevel/activeLeaf | 会话模型被删/停用后，UI 仍显示旧模型名，"发送才报错" |
| L8 | `MA:1028` | M5 副标回退写死 `"DeepSeek"` | 用户配了其它 provider 时品牌显示错误 |
| L9 | `IB:520-538` + `CT:104-134` | 附件 `kind=file` 只送"元数据"（`IB:533`）；`ocr_image` 工具要设备文件路径，模型拿不到应用私有目录路径 | 文件类附件等于没读；用户以为文件被理解 |
| L10 | `MA:842-845` | 语音转写文本直接 `input = (input + " " + text).trim()` | 无确认/撤销；识别错只能手删 |
| L11 | `MA:302` | `LaunchedEffect(searchQuery) { refreshSessions() }` 每键一次 Query | 输入"项目名"时逐字查全库（正文搜索 `SS:304-...`）→ 卡顿风险 |
| L12 | `MA:698,784,906,1131` | `streamingReasoning` 状态被写入并传给 `ChatMessagesM5`，但该函数（`CR:97`）与旧 `ChatMessages`（`CC:522`）都**未使用**该值 | 死状态；思考流只经 `liveEntries` 进入 UI（两套路径重复） |
| L13 | `MA:1853-1871` | 删除 provider 无论成败都 `BridgeHolder.rebuild` + reload | 失败时用户看到 provider 还在但错误文本一闪；rebuild 会重建内核（短暂重置） |
| L14 | `IB:69-77` | `SessionEnsureDraft` 复用"任意草稿"（`firstOrNull { it.draft }`） | 多入口并发时可能抢同一草稿（单进程 UI 下影响小） |

### 5.3 偶发 bug 候选（代码事实已取证；是否即用户遇到的 BUG-001 未证实 → 见 §6）

- **(a) 发送竞态窗口**：`send()` 只挡 `streaming != null`（`MA:942-944`）；`streaming` 要等首个 `AssistantDelta` 被 50ms 节流刷出才非 null（`MA:726-730,888-895,934-940`）。
  首 token 前连点发送 → 两次 `ChatSend` 都进队列，后端 session 锁（`IB:393-399`）只是**串行执行**，不会去重 → 两轮提问/两份回答。
- **(b) 旧路径分支切换无锚点**：`ChatMessages` 的 `UserBranchBar.onSwitch`（`CC:596-603`）与 `MessageActions.onSwitch`（`CC:655-665`）直接改 activeLeaf，
  没有记录/恢复首个可见项；M5 有 `pendingAnchor`（`CR:129-135,173`）但旧路径没有 → 切换分支后列表按新内容高度重排，读者视线位置丢失（"跳一下"）。
- **(c) 过程组手势仲裁**：`TimelineBody` 用自定义 `pointerInput`（`CC:779-835`）在 `handling==null` 时用 `canScrollForward/Backward` **一次性**判定本次手势归属（`CC:798-808`）；
  判定为内滚后自己 `dispatchRawDelta` 并 `consume()`，到边时不消费交给外层。该逻辑与 G-04 意图一致，但"一次手势跨两层"（内层到边后外层接管）是已知易出问题的形态。
- **(d) 三套折叠实现状态不共享**：旧 `TimelineHeaderRow`/`TimelineBody`（`CC:742-904`）、死代码 `TimelineFold`/`ProcessFold`（`CC:1238,1376`）、M5 `ThinkingHeaderM5`（`CR:321-356`）各有一套展开状态模型；
  迁移时若同时保留，展开状态会"点 A 处、B 处不变"。
- **(e) 折叠行不满足 G-02**：过程组头视觉高约 24-30dp（`CC:1070-1076`、`CR:337-346`），但折叠/展开、流式摘要变化时**整块高度参与布局**（AnimatedVisibility `CC:629-644`、`CR:195-226`）→ 摘要逐字变化会让下方内容位移（G-02 要求折叠行定高 + 尺寸隔离）。

### 5.4 死代码 / 未接线（17 处 excluded 的事实来源）

| 位置 | 说明 |
|---|---|
| `CC:1085-1165` `TimelineEntryRow` | 无调用点（被 `TimelineEntryHeader` 取代） |
| `CC:1238-1373` `TimelineFold` | 无调用点 |
| `CC:1376-1441` `ProcessFold` | 无调用点 |
| `CP:168-202` `SessionListItem` | 无调用点（`MA:85` 还留了一个未使用 import） |
| `DR:193-197` `NewChatButton` | 无调用点 |
| `AP:6-19` `AppPrefs/AppMode` | 无调用点（WORK 形态未实现） |
| `CR:628-640` `StreamingTextM5` | 无调用点（与 `StreamingTurnM5` 重复） |
| `CR:644-672` `M5ModelBar` | 无调用点（模型入口实际在 M5Composer 130） |
| `MA:194,2284-2301` `ChatLine/parseLines` | 无调用点 |
| `MA:287-289` `openMostRecent()` | 无调用点 |
| `CP:249` EmptyState `onAction` 按钮 / `CP:271` InlineError"重试" | 组件支持，但调用点不传 → **不可达**（见 D8） |
| `NS:69-154` `NeoSurface`、`GS:47-102` `GlassSurface` 的 onClick 接线 | 原语内部接线；真实节点在消费方（见 §2.8 说明） |

> 补充事实：全仓 `TODO/FIXME/HACK` 命中 **0**；`^\s*//` 里含 onClick/Button/入口/TODO 的注释仅 1 条说明性注释（`MS:86`），
> **没有被注释掉的入口**。旧 App 的缺陷形态不是"显式待办"，而是"空实现 / 未接线 / 双真源 / 调用点缺失"（见 D1-D18、L1-L14）。

### 5.5 假数据 / 写死值

| 位置 | 内容 | 迁移时注意 |
|---|---|---|
| `CK:66-97`、`GL:72-78` | ChatSample 整页静态对话/工具行；Gallery 无数据 | adb-only 演示页，**不可当功能证据** |
| `CS:42-52` | 空态标题/副标/两条建议文案硬编码；「她记得你们聊过的事。」 | 承诺记忆能力，但 core 只有历史回放（`EV`/`SS`），**未见记忆/检索实现** |
| `MA:1028` | 副标回退 `"DeepSeek"` | provider 通用化后必须改 |
| `MA:2196-2213` | 6 个 provider 模板写死（地址/模型/ctx） | 无远程目录；`anthropic` 模板 kind=`deepseek-messages`（`MA:2205`）待确认（Q-LEG-04） |
| `MA:1977` | 思考档位写死 `off/low/medium/high` | schema efforts 是任意 Map（`MD:59-62`）→ 导入自定义档无法编辑 |
| `CC:568`、`CR:207` | 过程组封顶 `屏高45% 且 ≤360dp` | spec Q-05 明确为 E2 工程值，待 P1 取证 |
| `MA:1702` | 设置页显示 `revision=$revision · 保存冲突会提示重试` | 工程调试文案暴露给用户 |
| `MA:1752` | 卡片标题「常驻（A7：不承诺 24/7，尽力而为）」 | 内部代号进 UI |
| `DR:122` | 抽屉底部「本地优先 · 离线可用」 | 与"需要 provider/网络"的现实不完全一致（有 FakeChatClient 兜底） |
| `CR:473,516`、`CC:968,1143,1200` | 各种 `take(4000/1200/300/80)` 截断 | 长工具输出/长代码被静默截断，无"展开全部" |

---

## 6. 待确认（E2 候选 / 需用户拍板）

> 规则：以下每条的"为什么需要拍"都来自 E0 代码冲突；但"应该怎么选"是产品判断，本轮**不自行决定**。
> 编号 `Q-LEG-nn` 供 P2 合并进 `open-questions.md`（不改该文件，等主笔合并）。

**Q-LEG-01｜M5 是"新 UI 的正式实现"还是"视觉参考"？**
- 事实：M5 聊天页只能通过 adb extra `m5_chat_ui` 打开（`MA:166`）；抽屉/设置/附件/回收站全部仍旧实现（§4 #24-26）。
- 冲突：若 M5 被当作迁移目标，则 §4 的 15 处差异都要在 M5 补齐；若只作视觉参考，P4 会重写而不是"补 M5"。
- 建议：当作**视觉/组件参考**，P4 按 `panels/*.md` 重写壳；不建议在 M5 上继续叠功能（避免第二套真源）。

**Q-LEG-02｜归档页是否允许"永久删除"？永久删除要不要更强确认？**
- 事实：归档页与回收站共用同一行操作，都显示「永久删除」（`MA:1537-1548`）；永久删除不可恢复（`SS:262-268`）。
- 风险：归档本意是"先藏起来"，却提供一步到位的永久删除入口，误触即丢会话+附件。
- 建议：归档页只保留「取消归档」；永久删除仅回收站可见，并要求输入会话名确认（待拍）。

**Q-LEG-03｜用户消息菜单要几个动作？**
- 事实：当前只有「复制」（假功能，D1）与「编辑并重发」（`MA:1323-1331`）。
- 缺：删除消息、引用/复制为引用、从该消息开分支、查看该消息上下文用量。
- 建议：P2 明确首版集合；「复制」必须先修成真复制（与 D1 一起修）。

**Q-LEG-04｜provider 模板的 `anthropic` kind 是否有意？`openai-responses` 要不要在 UI 屏蔽？**
- 事实：模板 `anthropic` 的 kind 写成 `deepseek-messages`（`MA:2205`），走 `AnthropicCompatibleClient`（`CCL:8-11`）；
  `openai-responses` 方言未实现，构造时抛错并由 `createOrFailed` 变成流式错误（`CCL:11-14,17-27`）。
- 建议：模板 kind 修正为 `anthropic-messages`（若无该方言则不要暴露该模板）；UI 里对未实现 kind 直接禁用。

**Q-LEG-05｜`ProviderDiscover` 结果是否要"一键采用"？**
- 事实：发现结果只拼成字符串显示（`MA:1827`），不回填 `form.models`；用户需手抄 id/ctx/max。
- 建议：补"采用全部/逐条采用"；否则该按钮价值仅"验证连通性"（可改名）。

**Q-LEG-06｜未发送/被移除的附件怎么清理？**
- 事实：选完即落盘（`MA:797-804`）；移除 chip 不删文件（`MA:1230,1235`）；core 无删除附件 API（`AS` 全文件）；只有永久删会话才清目录（`SS:262-268`）。
- 建议：新增附件删除命令 + 会话发送成功后清理未引用附件；或在 P4 用"发送时才落盘"改契约（需 P2 拍，涉及 `BR:46-52`）。

**Q-LEG-07｜发送失败后的重试与错误呈现？**
- 事实：`BridgeEvent.Failed` 有 `retriable`（`BR:180-186`、`IB:772`），但 UI 只把错误字符串塞进错误条（`MA:903-907`），不提供重试；
  `InlineError`/`M5ErrorBar` 的 retry 能力从未接线（D8）。
- 建议：错误条带「重试」（同 `requestId` 复用幂等键，`BR:18-19`）；乐观消息失败后保留原文供重发。

**Q-LEG-08｜空态文案「她记得你们聊过的事。」保留吗？**
- 事实：`CS:44` 硬编码该文案；本仓库 core 只有会话事件回放与标题/正文搜索（`EV`,`SS:304-...`），**未见记忆/检索/画像实现**。
- 建议：要么本版改文案（描述当前能力：本地历史、可搜索），要么把记忆能力立为独立面板/后端能力（超出本轮范围）。

**Q-LEG-09｜过程组/思考行的展开动效用哪种？**
- 事实：旧过程组用 `expandVertically` 高度动画（`CC:630-634`），M5 同（`CR:198-205`）；而 G-10 规定 **>240dp 直接切换**，且 spec 已拍板"45% 屏高封顶"（Q-05 仍是 E2）。
- 冲突：现有实现与 G-10 直接冲突；且"封顶内滚"的 cap 值无依据。
- 建议：P2 先定 cap（Q-05）与动效（G-10 已定），实现按"超阈值直接切"改。

**Q-LEG-10｜"深度思考"开关的唯一语义？**
- 事实：UI 同时有 `thinking`（全局 prefs）与 `reasoningLevel`（会话 sidecar），后端优先 level（`IB:478-495`）；
  M5 的入口还是"打开面板"而非"切换"（`MC:130`）。
- 建议：收敛为一个状态——要么"开关=off/默认档"，要么"开关=跟随模型默认"，二者选一并删除另一个真源（见 L1-L3）。

**Q-LEG-11｜发送防重入 / 排队怎么处理？**
- 事实：`send()` 只挡 `streaming`（`MA:942-944`），首 token 前可重入（§5.3-a）；core 无排队命令（§3 缺口 2）。
- 建议：P2 定"生成中发送"的语义：①禁用发送（最小改动）；②排队（需新 Command）；③打断并重发（需 steer）。

**Q-LEG-12｜设置页的工程/调试控件是否对用户保留？**
- 事实：设置页直接显示 `revision=$revision …`（`MA:1702`）、「常驻（A7：不承诺 24/7，尽力而为）」（`MA:1752`）、
  「启动前台服务/停止」（`MA:1750-1770`）、自检 7 项（`MA:1771-1786`，`DO:26-88`）。
- 建议：拆到 `settings.general`/调试页（首版可留但改名），或首版隐藏；不要出现在普通用户主路径。

**Q-LEG-13｜回到底部 vs 轮次导航（与 spec Q-07 同题）**
- 事实：旧/M5 都是单个"回到底部"（`MA:1199-1208`、`CR:610-626`）；DSH 的等价物是 `use-scroll-follow` + TurnNavigator（spec Q-07 已登记）。
- 建议：P1 已把旧实现取证为"单按钮 + 跟随"，P2 只需在"保留单按钮/换轮次导航"里二选一。

**Q-LEG-14｜"会话模型不可用"如何提示？**
- 事实：后端已返回 `providerAvailable/modelAvailable`（`IB:835-842`），UI 未消费（L7）；发送时才报 `PROVIDER_MISSING/MODEL_MISSING`。
- 建议：顶栏/模型入口显示"模型不可用"状态，点开面板时不列已删模型。

---

## 附：对账与复现

```powershell
# 分母（本轮已执行）
python tools\ui-audit\ui_audit.py scan --root app-android\src\main\kotlin --out _ws\out\findings-legacy.csv
python tools\ui-audit\ui_audit.py scan --root core\src\main\kotlin --out _ws\out\findings-legacy-core.csv --include kt

# 门禁（本轮结果：107 E0 + 51 excluded，uncovered=0，stale=0）
python tools\ui-audit\ui_audit.py check --findings _ws\out\findings-legacy.csv `
       --facts docs\spec\ref\legacy-myco.facts.csv --out _ws\out\coverage-legacy.md --fail-on-uncovered

# core root 单独对账（其 findings=0；报告中的 stale=158 是"同一 facts 文件被两个 root 共用"的已知噪声）
python tools\ui-audit\ui_audit.py check --findings _ws\out\findings-legacy-core.csv `
       --facts docs\spec\ref\legacy-myco.facts.csv --out _ws\out\coverage-legacy-core.md --fail-on-uncovered
```

- 扫描命中 158 = `jsx.onclick` 136 + `disclosure` 22。
- 事实库 158 行 = `E0` 107 + `excluded` 51；`excluded` 分布：死代码 17 / 签名行 8 / 原语内部接线 9 / adb-only 调试页 14 / 不可达分支 2 / 非交互 1。
- 逻辑节点 53（`E0` 去重）+ 16（§2.8 scanner 盲区）= **69**。
- 与 code 的对应：`legacy-myco.facts.csv` 的 `path` 是相对 scan root 的路径（app-android root 下 `dev/myco/android/...`），与 `findings-legacy.csv` 完全同构。

---

## 附2：补扫更新（kt 模式，第 2 轮）

> **本节数字覆盖上文「方法与分级」「附」里的 158/53/69，引用时以本节为准。**
> 盘点器新增 `kt.gesture`（`clickable`/`combinedClickable`/`pointerInput`/`toggleable`/`selectable`/`swipeable`/`detectTapGestures`/`detectDragGestures`）
> 与 `kt.widget`（`IconButton(`/`TextButton(`/`OutlinedButton(`/`FilterChip(`/`DropdownMenuItem(`/`ListItem(`/`AlertDialog(`/`ModalBottomSheet(`/`Switch(`/`Slider(`）两种 kind。
> 重扫 `app-android/src/main/kotlin`：**命中 158 → 298**；facts 同步补到 **298 行**，对账 **`uncovered=0`、`stale=0`**（报告 `_ws/out/regap-legacy.md`）。
> 合计：`E0` **213** + `excluded` **85**（新增 140 行 = 106 `E0` + 34 `excluded`）；逻辑节点 **63**（原 53 + 新增 10，其中 1 个为 MISSING 标记）。

### 附2.1 补扫归类口径

- **同一行多 kind**：`TextButton(onClick=…)` 同时命中 `jsx.onclick` 与 `kt.widget` → 拆成两条 facts 键、指向**同一个 `node_id`**（scanner 按 kind+line 计数，键必须逐条存在）。
- **弹层容器行**（`AlertDialog(` / `ModalBottomSheet(`）：归 `base.primitives.dialog-dismiss` 或对应 sheet 节点，理由=容器自身提供"点外部/返回/下滑关闭"（`onDismissRequest` 在其后一行）；不另立节点。
- **excluded（34 条新增）**：import 语句 18、死代码内 clickable 6、原语内部接线 4、不可达按钮 2（`InlineError`/`M5ErrorBar` 的 retry 调用点未接线 → `CP:271`、`CS:166`）、KDoc 注释 1（`MO:30`）、adb-only 样张 1（`CK:196`）、`SettingsRow` 原语接线 1（`CP:149`）、`SessionListItem` 死代码 1（`CP:178`）。每条理由见 facts.csv `note`。
- **E0 新增 106 条**：全部落到 md 已登记节点（53 个既有节点中的 40 个 + §2.8 的 9 个），未新增交互语义。

### 附2.2 由 §2.8 转为合法 facts 键的 9 个节点（md 未改，13 字段仍以 §2.8 为准）

`chat.source.section`（`CC:1175`/`CC:1190`）、`chat.source.item`（`CC:1205`）、`chat.tool.source.item`（`CC:985`）、
`model.sheet.model-row`（`MS:105`）、`model.sheet.thinking-switch`（`MS:176`）、`attachment.sheet.dismiss`（`MA:1292`）、
`chat.process.inner-scroll`（`CC:781`）、`chat.drawer.swipe-open`（`CC:714`）、`base.nav.swipe-back`（`PN:131`）。

### 附2.3 仍无 scanner kind 的 7 个 §2.8 点（facts 无法登记；P3 仍按 §2.8 补 13 字段）

`composer.input`（`OutlinedTextField`/`BasicTextField`）、`sidebar.search`（同）、`settings.search`（同）、
`chat.msg.link`（`withLink`/`LinkAnnotation`）、`settings.provider-editor.fields`（12 个输入框 + 3 个 Checkbox）、
`chat.list.scroll-follow`（纯派生状态，无手势回调）、`chat.drawer.system-back`（`PredictiveBackHandler`，不在新 kind 列表里）。

### 附2.4 MISSING 清单（md 完全没有的交互，共 1 条）

| # | 位置 | 是什么 | 用户能点到吗 | 影响 / 建议 |
|---|---|---|---|---|
| MISSING-1 | `app-android/src/main/kotlin/dev/myco/android/ui/m5/M5ModelSheet.kt:62`（`ModalBottomSheet(`） | **M5 模型面板的关闭手势**：下滑关闭 / 点外部关闭 → `onDismiss = { showM5Model = false }`（回调在 `MA:1489`；面板内容见 `MS:49-148`） | **能**：面板是 overlay，下滑或点遮罩即可关闭；没有此关闭态时用户只能"选一个模型/强度"被动关闭（`MA:1474/1485`） | 低危（不丢功能入口/不丢数据），但 md §2.4 只登记了面板内的模型行/强度 pill/思考开关，**没有"面板关闭"节点**，P3 建 `model` 面板时应补 `model.sheet.dismiss`（13 字段：命中区=遮罩或下滑手势，边界态=有未保存选择时直接丢弃等） |

> 另：`AlertDialog(` 的"点外部/返回键关闭"不是 MISSING —— 已归入 `base.primitives.dialog-dismiss`（该节点在 §2.7 第 1 字段就显式写了 `onDismissRequest` 盲区）。






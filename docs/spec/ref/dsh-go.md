# dsh-Go 事实库（P1 · 只读审计 · 手机端既有答案）

> 审计对象：`_ws/refs/dsh-Go/app/lib`（Flutter/Dart，**1230 个 .dart**；上游 ~1.97 万 star 为用户提供的背景信息 **E1**，版本 `1.4.1` 见 `app/pubspec.yaml:23`，Dart SDK `^3.13.4` 见 `:26`）。**只读**：不写实现、不改 app。
> 对账分母：`_ws/out/findings-dsh-go.csv`（2261 条交互点 / 420 个文件 / 顶层 7 个目录；`python tools/ui-audit/ui_audit.py scan --root _ws/refs/dsh-Go/app/lib`）。
> 事实库：本目录 `dsh-go.facts.csv`（2261 行；E0 **2243**、显式排除 **18**（其中注释/文档行 9）、uncovered **0**）。
> 对账报告：`_ws/out/coverage-dsh-go.md`。
> 分母边界：**只审 `app/lib`**。`packages/`（953 个 .dart：`dsh_client` 214 / `dsh_tavern` 506 / `dartssh2` 171 / `dsh_remote` 62）是纯 Dart 传输与酒馆引擎，`grep package:flutter` = 0 命中 → **不是 UI，不进分母**（§1.3）。

> **依据级别**：`E0` = 源码行 `路径:行`（相对 `app/lib`）/ 真机 · `E1` = 注释、README、docs 声称 · `E2` = 推测（**只允许出现在 §4**）。铁律 G-00：§0–§3 每条都能落到 `app/lib` 的 `文件:行`。
> **路径缩写**（仅 §2 字段 12 使用；`facts.csv` 内一律写以 `app/lib` 为根的完整相对路径）：
> `CL:` = `ui/chat/chat_list.dart` · `CS:` = `ui/chat/chat_screen.dart` · `CO:` = `ui/chat/composer.dart` · `SR:` = `ui/chat/widgets/stream_row.dart` · `TP:` = `ui/chat/widgets/turn_process.dart` · `TH:` = `ui/chat/widgets/thinking_pill.dart` · `TR:` = `ui/chat/widgets/tool_row.dart` · `LT:` = `ui/chat/render/long_text.dart` · `OF:` = `ui/common/overscroll_forwarder.dart` · `BL:` = `ui/common/bleed.dart` · `PR:` = `ui/common/pressable.dart` · `MO:` = `ui/theme/motion.dart` · `GL:` = `ui/theme/glass.dart` · `AT:` = `ui/theme/app_theme.dart` · `AL:` = `ui/shell/adaptive/adaptive_layout.dart` · `HS:` = `ui/shell/home_shell.dart` · `SD:` = `ui/shell/sessions_drawer.dart` · `PT:` = `ui/theme/page_transitions.dart`
> **行号校验**：全部行号已机器校验 ≤ 文件总行数（0 越界）；`dsh-Go` 是 2026 年仓库，行号以本次下载的快照为准。

---

## 0. 一页结论（手机客户端 10 条关键取舍，每条带 E0）

1. **消息列表不是普通 reverse 列表，而是「居中锚定」的两段 sliver**：`CustomScrollView(center: _centerKey, anchor: 输入条上沿比例)`，
   打开会话时已有的行往上长、流式新行往下长 → 头部插历史、尾部流式都**不移动**用户正在看的那一行
   （`CL:69-90,1049-1090`）。跟随也不靠 offset 补偿：自定义 `_FollowPhysics.adjustPositionForNewDimensions`
   在**同一次布局**里把位置放到新的末尾（`CL:1790-1826`）。→ 手机端答案：follow 必须是布局期的，不是帧后 `jumpTo`。

2. **跟随判据 = 「贴底」，不是「用户是否上翻」**：`_atBottom(slop=24)`（`CL:472`）；上拖立即 `_pinned=false`（`CL:613-616`）；
   离底 >160 出回底钮、回到 24 内收（`CL:635`）；点击后恢复跟随（`CL:534-542`）。回底钮是 **44×44 chrome 玻璃圆钮**、
   Stack overlay **不占位**（`CL:1097-1101,1667-1726`）；有待确认审批/提问时换成**同高 44 的计数胶囊**
   （`CL:1689-1711,326-333`）。→ **Q-07 的 dsh-Go 答案：一个按钮，两种形态，0 占位。**

3. **整轮过程折叠在手机上被改成「inline 不封顶 + 逐帧滚动补偿」**，与 DSH Web 的 capped-inner-scroll 相反：
   `TurnProcess` 展开体没有 `maxHeight`，展开动画每帧在布局前把长出来的高度从滚动位置扣掉（`TP:14-28,129-151,220-238`），
   摘要行因此**在屏幕上不动**。→ G-03 的形态选择在手机端出现了第三种实现：A + 滚动补偿（§4 Q-GO-03 待拍板）。

4. **工具行 = 32dp 单行摘要；详情井 = 280dp 封顶内滚**：`StreamRow.minHeight=32`、横向 `Bleed=8`
   （`SR:29-33,46-68`）；展开详情 `_kDetailMaxHeight=280`（`TR:32,429-465`）；长文本只排头 60 行 + 尾 140 行、上限 16000 字，
   井底一条 `minHeight:44` 的「查看全部」进全文页（`LT:14-19,202-210,261-307`）。→ 手机端答案：**行不封顶、井封顶、全文另起页**。

5. **内嵌滚动区到边后，dsh-Go 主动把剩余拖动交给外层（与 G-04 相反）**：`OverscrollForwarder` 监听
   `OverscrollNotification`，把纵向未消费增量 clamp 后 `jumpTo` 到外层列表（`OF:8-47`）；只转**拖动 overscroll**，
   不转 fling 速度；iOS 上强制 `ClampingScrollPhysics` 才能收到通知（`OF:38-46`）；`motion.dart` 明令
   **聊天列表与其内部滚动区禁用 bouncing physics**（`MO:177-180`）。用在代码井、diff、长文本井三处
   （`ui/chat/render/code_block.dart:581`、`ui/chat/render/diff_view.dart:166`、`LT:204`）。→ §6 的完整答案。

6. **命中区实际口径不是「全部 48dp」**：全局主题给 `FilledButton 64×50`、`TextButton 48×40`、`IconButton 44×44`
   （`AT:251-308`）；过程行 32 高但横向 ±8 可点（`SR:29-33,46-68` + `BL:87-96`）；会话行 44（`ui/shell/drawer/session_tile.dart:51`）；
   回复操作条 36×36 + `shrinkWrap`（`ui/chat/widgets/message_actions.dart:96-105,212`）；输入条主键 36×36（`CO:175-176,976-979`）；
   正文内链接用**自绘 RenderProxyBox 把热区补到 48 而视觉不变**（`ui/remote/relay_credit.dart:135-139,200-259`）。
   → **Q-03 的直接答案在 §5**：行级 32/44/48、图标级 36~44、48 只用于链接/输入/主操作。

7. **输入条：玻璃胶囊 28 圆角、1–6 行自适应、四态主键（发送/停止/麦克风/停听写）**：单行胶囊 48 = 36 键 + 上下 6
   （`CO:31-35,175-176`）；行数按「键盘上方 40% 高」算 2–6 行（`CO:158-169`）；运行中空草稿时发送键变停止键（`CO:403-441`）；
   **长按发送键**出「插话 / 停下先处理 / 排队」三选一（`CS:930-990`）。键盘由 `Scaffold.resizeToAvoidBottomInset:true` +
   自定义 `MultiChildLayout` 同帧排版（`CS:1871,2610-2666`）；横屏打字时顶栏与 dock 让位（`CS:1841-1859,2498-2559,2565-2606`）。

8. **导航 = 左抽屉（会话）+ 右快速面板 + ≥840dp 三栏；没有底部导航**：全仓 `NavigationBar(`/`BottomNavigationBar` **0 处**
   （主题里只定义了 `navigationBarTheme`，`AT:471-493`，未被使用）；二级页是 `MaterialPageRoute` 多页面栈（`HS:249`），
   抽屉跟手拖动、松手按速度弹簧落位（`HS:199-233`）；面板位移用 paint-only `Transform`（`HS:588-604`）；
   `PopScope` 顺序：右面板 → 结束多选 → 关抽屉（`HS:319-333`）；二级页 iOS 式推入 + 20dp 边缘返回 + Android 预测性返回
   （`PT:15-35,428-442`）。窗口分类用 Material 3 断点**且高度 <480 一律 compact**（横屏手机）（`AL:20-49`）。

9. **主题是集中 token，不是散落样式**：`AppMotion`（按压 90 / 快 160 / 标准 240 / 折叠 280 / 强调 340 / 弹层 380-260 /
   页面 480 ms，弹簧落位，`MO:18-186`）；三级玻璃 `GlassSurface` 同屏真模糊 ≤3 件、低端机/高对比度降级不透明
   （`GL:12-39,130-140`）；字体缩放 = 系统 `textScaler` × 会话字号系数（`CS:2669-2706`）。

10. **手机专属能力在 lib 侧的做法**：`SafeArea` 163 处（34 个目录）；每页 `AnnotatedRegion<SystemUiOverlayStyle>` 设状态栏字色
   （统一函数 `AT:92-100`，`HS:335`、`CS:1867` 等 20 处）；**不锁方向**（全仓 `setPreferredOrientations` = 0 次），横屏靠
   `WindowClass` 降级；剪贴板 208 处、`HapticFeedback` 121 处；通知栏在原生侧、lib 内只有应用内提醒宿主（`ui/notify/*`）；
   媒体选择用 `image_picker`（`ui/chat/widgets/attachments.dart:152-165`）/`file_picker`（全仓 13 处）。


---

## 1. `app/lib` 目录 → 面板归位表

> `app/lib` 共 31 个顶层目录 / 1230 个 .dart；交互点（findings）只落在其中 7 个顶层目录 → 归位见 1.1。
> 其余 24 个目录是状态、传输、序列化、l10n、原生桥（1.2）——**不是 UI**，没有交互点，不进分母。

### 1.1 有交互点的目录

| 目录 | findings | → 面板/节点 |
|---|---|---|
| `ui/chat/` | 398 | `chat.tool.plugin-view`（插件原生视图（DshUi 工具卡 / 表单 / 图表 / 选择）） ×58；`chat.msg.image`（正文/工具图片 + 全屏查看器 + 图库） ×41；`chat.turn.process`（整轮过程折叠（摘要行 + 展开过程 + 改动/产物卡）） ×23；`chat.empty`（空态（新对话页 / 建议卡 / 工作区与文件夹入口）） ×22；`chat.dock`（输入条上方 dock（目标条 / 待办条 / 运行条 + 目标 sheet）） ×20；`trajectory.sheet`（执行轨迹 / 用量 / 产物预览 sheet） ×19；`chat.msg.assistant-markdown`（助手正文 Markdown + <details> 折叠 + 密钥遮罩） ×19；`attachment.panel`（附件面板（相册/拍照/文件 + 生成模式 + 本对话权限/手机操控入口）） ×19；`chat.tool.detail`（工具详情井 / 步骤详情页 / diff 页（限高内滚 + 截断 + 全文页）） ×18；`chat.list.scroll-follow`（消息列表：贴底跟随 + 头部插入历史） ×17；`chat.header`（聊天顶栏（玻璃件 + 标题胶囊 + 模型chip + 新对话键）） ×16；`model.sheet`（模型 / 思考强度 / Agent 预设选择面板） ×16；`composer.attach`（＋ 附件键 / 附件条 / 移除） ×15；`chat.msg.notice`（系统提示行 / 活动条 / 后台任务通知 / 会话卡） ×13；`approval.card`（审批卡（批准/拒绝/本对话都允许）+ 权限胶囊） ×13；`chat.msg.code-block`（代码块（限高内滚 + 横向渐隐 + 复制/换行）） ×10；`chat.msg.user`（用户气泡 + 消息长按菜单 + 引用条） ×10；`approval.question`（提问卡（选项 / 自由文本 / 回答后卡片）） ×9；`chat.msg.thinking`（思考行（32dp 单行 + 三行实时窗口 + 展开全文）） ×9；`composer.send-stop`（发送/停止/麦克风四态键 + 长按发送模式） ×7；`composer.mention`（@ 提及选择器） ×6；`chat.msg.actions`（回复操作条（复制/分享/朗读/分叉/重答/⋯）） ×5；`composer.slash`（斜杠命令面板） ×4；`chat.tool.row`（工具调用行（32dp 摘要 + 状态尾 + 失败/沙箱标记）） ×4；`chat.list.to-bottom`（回到底部 / 待确认计数胶囊（overlay）） ×3；`composer.voice`（语音输入（听写 / 识别中 / 音量条）） ×2 |
| `ui/tavern/` | 285 | `tavern.chat`（酒馆对话（树状分支 / 候选回复 ‹2/5› / 消息长按 / 草稿）） ×67；`tavern.characters`（角色卡 / 人设 / 群聊 / 频道） ×60；`tavern.library`（资源库（角色包 / 世界书 / 预设 / 正则 / 快捷回复 / 导入导出）） ×53；`tavern.stage`（舞台侧面板（本局/状态/导演/本回合）+ VN/互动/骰子/故事书） ×49；`tavern.home`（酒馆首页（书架 / 对话 / 资源库 / 我的）+ 搜索/收藏/用量） ×36；`tavern.settings`（酒馆设置（音频 / 分支 / 偏好）） ×20 |
| `ui/settings/` | 195 | `settings.appearance`（外观（主题工作室 / 主题包 / 壁纸 / 启动屏 / 图标 / 界面与个性化）） ×93；`settings.voice`（语音与媒体服务（语音模型 / 识别 / 媒体服务配置）） ×38；`settings.system`（系统（诊断 / Linux 环境 / 后台保活指南 / 性能 / 隐私 / 关于 / 更新 / 安全模式恢复）） ×34；`settings.home`（设置首页（分组 hub + 搜索）） ×27；`settings.notifications`（通知设置 + 应用内通知路由（提醒条 / 后台网络）） ×2；`settings.engine`（引擎管理（运行时 / 日志 / 重置 / 设置 schema 表单 / agent loop）） ×1 |
| `ui/companion/` | 143 | `companion.room`（伙伴小屋（WebView 房间 + Flutter 浮层对话 + 输入条）） ×78；`companion.activity`（伙伴任务 / 记忆 / 通知 / 语音 / 助手设置） ×65 |
| `ui/phone/` | 113 | `phone.control`（手机操控（主屏 / 技能 / 系统操作 / 确认方式 / 无障碍恢复）） ×86；`phone.modes`（非干扰模式 / Android 壳 / 访问授权选择） ×27 |
| `ui/shell/` | 111 | `sidebar.drawer`（会话抽屉（品牌/搜索/工作区/筛选/会话行/多选/身份栏）） ×86；`base.layout`（布局基座（HomeShell / 自适应三栏 / 二级页框架 / 转场）） ×11；`sidebar.search`（会话搜索页（关键词 / 结果 / 筛选）） ×10；`sidebar.quick-panel`（右侧快速面板（模式/网页/设置/本会话信息）） ×4 |
| `ui/plugins/` | 99 | `settings.plugins`（插件（市场 / 已装 / 详情 / 配置 / 原生 seat / Cordis / ui.json 配置）） ×99 |
| `ui/bots/` | 88 | `bots.manage`（Bot 管理（列表 / 详情 / 新建 / 房间 / 记忆 / 安装卡）） ×63；`bots.chat`（Bot 对话 / 群聊（浮顶栏 + 卡片 + 输入条）） ×25 |
| `ui/remote/` | 65 | `remote.hosts`（远程宿主（宿主切换 sheet / 远程页 / 会话备份 / 连接进度）） ×36；`remote.pairing`（配对 / SSH 主机表单 / 中继登录 / 主机密钥） ×29 |
| `ui/providers/` | 60 | `settings.models`（模型渠道 / Provider / 模型页 / 订阅） ×60 |
| `ui/features/` | 59 | `settings.features`（功能页（定时任务 / 作业 / 权限 / 预设 / 技能 / 会话导出）） ×59 |
| `ui/common/` | 57 | `base.primitives.row`（通用行/卡片/分段/胶囊/搜索框/空态（列表基元）） ×30；`base.primitives.sheet`（底部弹层基座（showAppSheet / 标题栏 / 风险提示 / 短提示 / 丢弃守卫）） ×14；`base.primitives.pressable`（按压反馈基座（Pressable：缩放 / 弹簧 / 触感 / 滚动中不闪）） ×13 |
| `ui/workshop/` | 53 | `workshop.page`（创意工坊（浏览 / 已装 / 我的 / 发布 / 安装 / 举报）） ×53 |
| `ui/engine_settings/` | 51 | `settings.engine`（引擎管理（运行时 / 日志 / 重置 / 设置 schema 表单 / agent loop）） ×51 |
| `ui/forum/` | 47 | `forum.page`（论坛（首页 / 话题 / 发帖 / 搜索 / 通知 / 账号）） ×47 |
| `ui/engine/` | 34 | `settings.engine`（引擎管理（运行时 / 日志 / 重置 / 设置 schema 表单 / agent loop）） ×34 |
| `ui/onboarding/` | 32 | `onboarding.setup`（首启引导（欢迎 / 运行时选择 / 安装 / 连通 / API key / 外观步骤）） ×32 |
| `ui/speech/` | 31 | `speech.call`（语音通话（玻璃球 / 字幕 / 静音挂断扬声器 / 就地审批）） ×31 |
| `ui/workspace/` | 30 | `workspace.browser`（工作区 / 文件浏览 / 目录选择 / 表格） ×30 |
| `ui/pets/` | 29 | `pets.stage`（宠物舞台（聊天页浮层 / 点击拖拽 / 气泡 / 菜单 / 设置）） ×29 |
| `ui/session/` | 26 | `sidebar.session-detail`（会话详情（概览 / 插件 / 产物 / 上传）） ×26 |
| `ui/terminal/` | 25 | `terminal.page`（会话终端（全屏暗色 + 键行 + 手势）） ×25 |
| `ui/share/` | 23 | `share.panel`（分享面板（范围 / 内容开关 / 图片 / 链接 / 文件 / 分享码 / 托管链接）） ×23 |
| `ui/safe_mode/` | 20 | `settings.system`（系统（诊断 / Linux 环境 / 后台保活指南 / 性能 / 隐私 / 关于 / 更新 / 安全模式恢复）） ×20 |
| `ui/update/` | 18 | `settings.system`（系统（诊断 / Linux 环境 / 后台保活指南 / 性能 / 隐私 / 关于 / 更新 / 安全模式恢复）） ×18 |
| `ui/share_code/` | 17 | `share.panel`（分享面板（范围 / 内容开关 / 图片 / 链接 / 文件 / 分享码 / 托管链接）） ×17 |
| `ui/files/` | 16 | `workspace.browser`（工作区 / 文件浏览 / 目录选择 / 表格） ×16 |
| `ui/account/` | 15 | `account.page`（账号（登录 / 资料 / 余额 / 订阅入口）） ×15 |
| `ui/web/` | 13 | `web.viewer`（内嵌网页（DSH Web / 本地网页）） ×13 |
| `ui/agents/` | 12 | `subagent.team`（子代理团队（头部胶囊 / 团队页 / 成员行）） ×9；`chat.header`（聊天顶栏（玻璃件 + 标题胶囊 + 模型chip + 新对话键）） ×3 |
| `ui/access/` | 11 | `phone.modes`（非干扰模式 / Android 壳 / 访问授权选择） ×11 |
| `ui/cordis/` | 10 | `settings.plugins`（插件（市场 / 已装 / 详情 / 配置 / 原生 seat / Cordis / ui.json 配置）） ×10 |
| `ui/replay/` | 9 | `replay.page`（回放（会话回放 / 分享码播放器）） ×9 |
| `ui_config/` | 8 | `settings.plugins`（插件（市场 / 已装 / 详情 / 配置 / 原生 seat / Cordis / ui.json 配置）） ×8 |
| `ui/community/` | 7 | `community.page`（社区 / 求星提示） ×7 |
| `ui/notify/` | 7 | `settings.notifications`（通知设置 + 应用内通知路由（提醒条 / 后台网络）） ×7 |
| `ui/launch/` | 6 | `settings.appearance`（外观（主题工作室 / 主题包 / 壁纸 / 启动屏 / 图标 / 界面与个性化）） ×6 |
| `ui/share_hosted/` | 6 | `share.panel`（分享面板（范围 / 内容开关 / 图片 / 链接 / 文件 / 分享码 / 托管链接）） ×6 |
| `ui/theme/` | 6 | `base.theme`（主题与 token（玻璃 / 形状 / 间距 / 动效 / 转场 / 视觉特效）） ×6 |
| `ui/wallpaper/` | 6 | `settings.appearance`（外观（主题工作室 / 主题包 / 壁纸 / 启动屏 / 图标 / 界面与个性化）） ×6 |
| `ui/fx/` | 6 | `base.theme`（主题与 token（玻璃 / 形状 / 间距 / 动效 / 转场 / 视觉特效）） ×6 |
| `share_code/` | 3 | `excluded`（§3） ×3 |
| `tavern/` | 3 | `excluded`（§3） ×3 |
| `dev/` | 2 | `excluded`（§3） ×2 |
| `ui/star/` | 2 | `community.page`（社区 / 求星提示） ×2 |
| `plugins/` | 2 | `settings.plugins`（插件（市场 / 已装 / 详情 / 配置 / 原生 seat / Cordis / ui.json 配置）） ×2 |
| `pets/` | 1 | `excluded`（§3） ×1 |
| `ui/modes/` | 1 | `base.layout`（布局基座（HomeShell / 自适应三栏 / 二级页框架 / 转场）） ×1 |

### 1.2 无交互点的目录（不是 UI / 没有可点单元）

| 目录 | 内容 | 处理 |
|---|---|---|
| `state/`（143 dart） | Riverpod 状态、会话/模型/草稿/连接状态机 | 非 UI，无交互点 |
| `runtime/`（16） | 本地引擎进程桥/生命周期 | 非 UI |
| `plugins/`（21） | 插件协议、seams、native 状态 | 非 UI（UI 在 `ui/plugins/`） |
| `companion/`（62） | 伙伴逻辑/记忆/动作（无 Widget） | 非 UI（UI 在 `ui/companion/`） |
| `tavern/`（45） | 酒馆数据层 + HTML 字符串模板 | 非 UI；模板里的 HTML/JS 命中已排除（§3） |
| `media/`（2） | 媒体路径/读取工具 | 非 UI |
| `l10n/`（5） | 生成文案 | 非 UI |
| `links/`、`launch/`、`notify/`、`clipboard/`、`secrets/`、`performance/`、`usage/`、`wallpaper/`、`voice/`、`speech/`、`update/`、`workshop/`、`pets/`（20） | 平台能力与领域逻辑 | 非 UI；`pets/pet_voice_dialog.dart` 唯一命中已按死代码排除（§3） |
| `forum/`、`share_hosted/`、`remote/`、`diagnostics/`、`ui_config/`、`themes/`（13） | 领域逻辑/MethodChannel | 非 UI（`ui_config/` 的 8 条 UI 命中在 `ui_config/widgets/`，已归 `settings.plugins`） |
| `share_code/`（19） | 分享码编解码 + 生成的 Web 播放器 | 非 UI；`.g.dart` 内 HTML 命中已排除（§3） |

### 1.3 `packages/` 是什么、是否与我们相关

| 包 | .dart | 内容 | 与 myco 的关系 |
|---|---|---|---|
| `packages/dsh_client` | 214 | 纯 Dart 客户端：HTTP/SSE、mux、会话与删除 API | **不相关**：协议层，无 Flutter import；若 myco 走 WebSocket/HTTP 协议可参考，但不是 UI 事实 |
| `packages/dsh_tavern` | 506 | 酒馆引擎：角色卡、世界书、正则、prompt 组装 | **不相关**：`ui/tavern/*` 的数据层；酒馆面板本身在 myco 首版范围之外 |
| `packages/dartssh2` | 171 | 第三方 SSH 库（vendor） | **不相关**：库代码 |
| `packages/dsh_remote` | 62 | WebRTC / LAN / TURN / 中继 | **不相关**：传输层 |

> 结论：`packages/` 953 个 .dart **全部不进分母**（`grep package:flutter` = 0 命中；无 Widget/手势/滚动配置）。§2 全部节点都只引用 `app/lib` 行号。

---

## 2. 节点事实表（72 个节点 · 每个 13 字段）

> 字段定义照抄 `docs/spec/schema-node.md §1`；每个节点写满 13 字段。
> 节点 ID 与 `dsh-go.facts.csv` 的 `node_id` 一一对应；每个节点末尾括号里是落在它上面的 findings 条数。
> **无扫描命中的容器节点**（扫描器只认手势回调/组件名，容器与纯布局节点扫不到，但仍按契约写满 13 字段）：
> `composer.editor`

### 2.1 聊天主界面（面板 ID：`chat`；归位：myco `chat`）

#### `chat.list.scroll-follow` — 消息列表：贴底跟随 + 头部插入历史　（findings 17）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.list.scroll-follow`。所属：聊天面板→消息列表。一个会话一个实例，同屏恒 1 个（E0 `ui/chat/chat_screen.dart:1949-1963` 挂在 body 槽）。出现条件：打开任一会话；`sessionId == null` 时走空态分支，不出现（`ui/chat/chat_screen.dart:1885-1935`）。 |
| 2 | 网格与定位 | 父 = 聊天页 `CustomMultiChildLayout` 的 `_Slot.body`；宽 = 屏宽，高 = 输入条上沿 + `overlap(6)`（`ui/chat/chat_screen.dart:2615-2641`，`overlap` 定义 `:2619`）。列表顶部让出 `headerHeight = 安全区 + 56 + 8`（`ui/chat/chat_header.dart:93,104`；`ui/chat/chat_screen.dart:1859,1953`），底部留 `bottomInset + AppSpace.sm + 宠物 headroom`（`ui/chat/chat_list.dart:1018,1089`）。内容两段 sliver：`center` 之上是已有行（倒序向上长），之下是流式新行（`ui/chat/chat_list.dart:69-90,1049-1090`）。 |
| 3 | 命中区 | 整屏列表，`CustomScrollView` 本身不是按钮，无命中区问题。浮在右下角的回底钮 44×44（`ui/chat/chat_list.dart:1097-1101,1678`），距阅读列右缘 `AppSpace.floatInset=12`、距输入条上沿 12（`:1097-1101`）。 |
| 4 | 手势 | 竖直拖动 = 列表滚动。`Listener.onPointerDown` 记手指数并立刻停掉自动 glide（`ui/chat/chat_list.dart:1033-1044`）；上拖（`ScrollDirection.forward`）立即停跟随（`:613-616`）。`keyboardDismissBehavior: manual`（`:1057`）：拖列表**不**自动收键盘，改由滚动通知里在主焦点非首要时手动 `unfocus`（`:602-605`）。 |
| 5 | 行为 | 贴底时新内容在**同一次布局**里跟到新末尾（`_FollowPhysics.adjustPositionForNewDimensions` `ui/chat/chat_list.dart:1794-1826`）；离底 >160 出回底钮、回到 24 内收（`:635`）；`jumpToBottom()` 关角标并恢复跟随（`:534-542`）；距顶 <800 自动 `loadOlder()`（`:641-647`）；头部插入历史用 `center` 两段 sliver 保住当前阅读行（`:69-90`）。运行中插话/展开用 `_held` 挡住"行自己长高"触发的追底（`:218-221,592-595`）。 |
| 6 | 视觉状态 | `default`：无遮罩；`scrolledUnder`：内容压到顶栏下方时出页面色渐隐（`ui/chat/chat_screen.dart:1955` + `ui/chat/chat_screen.dart:2587`）。输入条区域下方有底色 `BackdropVeil`（`ui/chat/chat_screen.dart:1983-1986,2638`）。`fluid` 档才有 glide 跟随（`ui/chat/chat_list.dart:244-259`）。 |
| 7 | 布局占位与展开形态 | 参与布局流，占满 body 槽；不展开、无折叠。输入条高度变化时列表下沿同帧跟着变（`ui/chat/chat_screen.dart:2610-2616` 注释 + `:2635-2640`）。 |
| 8 | 滚动语义 | 本面板**唯一**滚动 owner。内层代码井/工具井/长文本井到边后，由 `OverscrollForwarder` 把纵向 overscroll 转交给它（`ui/common/overscroll_forwarder.dart:22-37`）；它自己到顶/底由平台 clamping 吸收，**不再外传**（页面根不是 Scrollable）。横向滚动（代码横滑）复用 `HorizontalEdgeFade`（`ui/common/edge_fade.dart:11-51`），不参与纵向仲裁。 |
| 9 | 动效 | glide 跟随：时间常数 0.07 s、仅当距底 ≤240 才滑，否则直接 `jumpTo`（`ui/chat/chat_list.dart:252-257,302-317`）。回底钮显隐：`AnimatedScale` + `AnimatedOpacity` 160 ms（`:1761-1767`）。`jumpTo` 本身无动画（`:534-542`）。 |
| 10 | 边界态 | 空会话：走空态分支（`ui/chat/chat_screen.dart:1885-1935`），列表不出现；超长历史：分段 `loadOlder`（`ui/chat/chat_list.dart:641-647`）；加载失败/离线：列表保留已同步内容，header 显示状态（`ui/chat/chat_screen.dart:2430-2460`）；横屏 + 键盘：header 让位，列表上沿抬到状态栏下（`ui/chat/chat_screen.dart:1844-1859`）；大字号：`_ChatTextScale` 叠加系统缩放（`ui/chat/chat_screen.dart:2669-2706`）。 |
| 11 | 无障碍 | 回底钮 `Semantics(button, label=回到底部/回到底部（N 待确认）)`（`ui/chat/chat_list.dart:1768-1772`）；运行/等待状态由顶栏状态行播报（`ui/chat/chat_screen.dart:2457-2459`）。列表本身无自定义语义。 |
| 12 | 依据 | E0 `ui/chat/chat_list.dart:69-90,218-221,252-257,302-317,472-479,534-542,592-647,1018-1101,1049-1057,1667-1786,1790-1826`；E0 `ui/chat/chat_screen.dart:1859,1885-1935,1949-1963,2430-2460,2615-2667`；E0 `ui/chat/chat_header.dart:93,104`；E0 `ui/common/overscroll_forwarder.dart:8-47`。 |
| 13 | 反证 + 验收 | **反证**：若用普通 reverse `ListView`，头部插历史与流式增长都必须做偏移补偿，而惰性列表 `maxScrollExtent` 只是估算值 → 读者读到一半会被逐帧推走（等于无法读）；若按"用户是否上翻"而不是"是否贴底"判跟随，运行中插话/展开会抢走阅读位置。**验收** GO-01：真机录屏——流式 30 s 内正文行顶 y 不跳、上翻即停跟随、回底钮只在离底后出现。 |

#### `chat.list.to-bottom` — 回到底部 / 待确认计数胶囊（overlay）　（findings 3）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.list.to-bottom`。所属：聊天面板→消息列表浮层。同屏最多 1 个；两种形态：圆钮（无待确认）与胶囊（有审批/提问）。出现条件：列表离底 >160 才显形（`ui/chat/chat_list.dart:635`），隐藏阈值 24 防抖（`:635`）。 |
| 2 | 网格与定位 | 父 = 列表 `Stack` 的第 2 层（`ui/chat/chat_list.dart:1097-1101`）：`right = max(0,(列宽-readingWidth)/2) + AppSpace.floatInset`、`bottom = bottomInset + 12`。圆钮 44×44（`:1678,1714-1717`），图标 22（`:1726`）；胶囊高 44、横向 padding 16、图标 18（`:1696-1710`）。G-01 合规：不在布局流里。 |
| 3 | 命中区 | 圆钮 44×44 整块可点（`ui/chat/chat_list.dart:1714-1727` GlassSurface `onTap`）；胶囊整条可点（`:1689-1711`）。与输入条上沿净距 12 + 列表 `bottomInset`；与宠物有 `PetAvoid` 避让（`:1757-1758`）。 |
| 4 | 手势 | 单击 = `jumpToBottom()`（`ui/chat/chat_list.dart:332,542`）；无长按/双击/滑动。（拖拽不绑，浮层不参与列表手势。） |
| 5 | 行为 | 显隐由 `_showJump`/`_hasNew` 两个 `ValueNotifier` 驱动，不 setState 整表（`ui/chat/chat_list.dart:319-322`）；`hasNew` 只在离开视口后出现过新内容时为真，回到 24 内清零（`:636-637`）；待确认计数 = 提问数 + 未自动放行的审批数（`:326-333`）。点击幂等（已贴底时列表不动）。 |
| 6 | 视觉状态 | `default`：chrome 玻璃、`blur:false`（悬在滚动内容上不模糊，`ui/chat/chat_list.dart:1692-1693`）；`hasNew`：右上角 9×9 主色角标（`:1739-1747`）；`waiting>0`：换成等高的警告色胶囊（`ui/chat/chat_list.dart:1689-1711`）；两形态交叉淡变 + `AnimatedSwitcher`（`:1773-1778`）。 |
| 7 | 布局占位与展开形态 | 浮层，不占位（G-01）。进入：scale 0.8→1 + opacity，160 ms（`ui/chat/chat_list.dart:1761-1767`）；`fluid` 档 scale 0.6 起 + `popCurve`（同上）。 |
| 8 | 滚动语义 | 不滚。它盖在列表之上，`IgnorePointer(ignoring: !show)` 让隐藏时不挡点击（`ui/chat/chat_list.dart:1759-1760`）。 |
| 9 | 动效 | 显隐 160 ms；角标 `AnimatedScale`（fluid 用 `popCurve`）（`ui/chat/chat_list.dart:1735-1738,1761-1767`）；形态切换 `AnimatedSwitcher` 160 ms（`:1773-1775`）。可中断：中途反向只切目标值。 |
| 10 | 边界态 | 等待计数为 0 → 圆钮；>0 → 显示「N 待确认」胶囊（`ui/chat/chat_list.dart:326-333,1700-1710`）；壁纸/深色跟随主题玻璃；大字号：胶囊随文字长高（`SizedBox(height:44)` 内 Row，由 `labelLarge` 撑，`ui/chat/chat_list.dart:1696-1705`）；宠物站立时整体上移（`PetAvoid`，`:1757`）。 |
| 11 | 无障碍 | `Semantics(button, label)`，`excludeSemantics` 避免重复读内部图标；文案「回到底部」/「回到底部（N 待确认）」（`ui/chat/chat_list.dart:1768-1772`）。 |
| 12 | 依据 | E0 `ui/chat/chat_list.dart:319-333,534-542,635-637,1097-1101,1667-1786`。 |
| 13 | 反证 + 验收 | **反证**：若让回底钮占布局高度（G-01 违反），滚到底时列表高度突变 → 画面顿跳；若在离底 24~160 之间反复显隐，用户滚动时按钮闪烁。**验收** GO-02：真机手势——离底出钮、贴底消失、等待审批时变胶囊且点击后贴底。 |

#### `chat.header` — 聊天顶栏（玻璃件 + 标题胶囊 + 模型chip + 新对话键）　（findings 19）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.header`。所属：聊天面板→顶栏。同屏恒 1 个。出现条件：聊天页任意状态（新对话/会话/子代理会话）；子代理会话用「‹ 上级对话」替换菜单键（`ui/chat/chat_screen.dart:1795-1797`）。 |
| 2 | 网格与定位 | 条高 56（`ui/chat/chat_header.dart:93`），总高 = 安全区 + 56 + 8（`:104`）；不是整条色带，是几枚浮动玻璃件：左 ≡ / 面包屑、标题胶囊（标题 + 状态行）、模型 chip、右侧「新对话」/+ 键（`:39-50` 结构图）。模型 chip 只在非窄屏或新对话出现（`ui/chat/chat_screen.dart:2015-2021`）。 |
| 3 | 命中区 | 键 = 44×44（`AdaptiveMetrics.key=44`，`ui/shell/adaptive/adaptive_layout.dart:81-83`；圆形玻璃键）；标题胶囊整块可点（`onTitleTap`，`ui/chat/chat_screen.dart:2007-2009`）；模型 chip 点击弹模型面板（`:2017-2021`）。相邻键间距 8（`AdaptiveMetrics.gap`）。 |
| 4 | 手势 | ≡ 单击 = 菜单（手机开抽屉/宽屏折展侧栏，`ui/shell/home_shell.dart:179-191`）；标题单击 = 打开会话详情（宽屏切换右面板，`ui/chat/chat_screen.dart:2007-2009`）；「新对话」单击 = 回新对话 + 触感（`:1998-2003`）；模型 chip 单击 = 模型 sheet；时钟键（有自动化任务时）= 任务 sheet（`ui/chat/header_schedule.dart`）。 |
| 5 | 行为 | 顶栏状态行三态：`busy`（生成中/连接中/停止中）、`warn`（等待回答/重连）、`error`（断流/未授权）（`ui/chat/chat_screen.dart:2430-2460`）；`scrolledUnder` 时叠加页面色渐隐（`ui/chat/chat_header.dart:39-41` + `ui/chat/chat_screen.dart:2587`）。 |
| 6 | 视觉状态 | `default` 玻璃件浮在内容上；`scrolledUnder` 追加顶部渐隐；状态点颜色 + 文案（不单靠颜色，`ui/chat/chat_header.dart:27-28`）。 |
| 7 | 布局占位与展开形态 | 不占满宽色带；内容从其下滚过（`ui/chat/chat_header.dart:39-41`）。横屏 + 键盘时整条淡出上移让位（`ui/chat/chat_screen.dart:1841-1859,2565-2606`）。 |
| 8 | 滚动语义 | 不滚。标题胶囊宽按优先级收缩：整条 → chip 降档 → 标题缩短 → chip 缩短 → 时钟键让位（`ui/chat/chat_header.dart:383-392`）。 |
| 9 | 动效 | 状态点呼吸（`StatusDot`）；横屏键盘时 `AnimatedSlide(0,-0.2)` + 淡出 240 ms（`ui/chat/chat_screen.dart:2595-2599`）；`scrolledUnder` 渐隐随滚动跟手。 |
| 10 | 边界态 | 标题空 → 中性「未命名对话」（`ui/shell/sessions_drawer.dart:69-72`）；超长标题 → 胶囊内截断（`ui/chat/chat_header.dart:383-392`）；离线/重连 → 状态行文案（`ui/chat/chat_screen.dart:2430-2460`）；大字号：`pillHeightOf` 随字号增高（`ui/chat/chat_header.dart:97-104`）；横屏：见字段 7。 |
| 11 | 无障碍 | ≡/新对话/模型/时钟均为有 label 的按钮（`Semantics(button)` + tooltip 文案）；状态行由 `HeaderStatus.spoken` 附带给读屏（`ui/chat/chat_screen.dart:2455-2459`）。 |
| 12 | 依据 | E0 `ui/chat/chat_header.dart:27-50,93-104,383-392`；E0 `ui/chat/chat_screen.dart:1795-1808,1841-1859,1998-2021,2430-2460,2565-2606`；E0 `ui/shell/adaptive/adaptive_layout.dart:81-83,219-223`；E0 `ui/agents/header_agents.dart:115-117`。 |
| 13 | 反证 + 验收 | **反证**：若把顶栏做成整条不透明色带 + 常驻高度，手机上 56+安全区就吃掉 1/8 屏，且滚动态与静态无差别、没有"内容压过去"的层次；若标题与模型 chip 不做宽度优先级，窄屏会挤成两行或互相覆盖。**验收** GO-03：真机截图——窄屏标题不换行、横屏打字顶栏让位、滚动态出渐隐。 |

#### `chat.msg.user` — 用户气泡 + 消息长按菜单 + 引用条　（findings 10）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.user`。所属：聊天面板→消息轮→用户消息。每条用户消息 1 个气泡；长按/⋯ 出消息菜单（`ui/chat/widgets/message_menu.dart`）；引用条出现在输入条 tray（`ui/chat/widgets/quote_chip.dart`）。 |
| 2 | 网格与定位 | 用户气泡行内右对齐、整行内边距按阅读列（`ui/chat/chat_list.dart` 行 frame）；菜单是底部弹层（`ui/chat/widgets/message_menu.dart:89,135,299`）；引用 chip 为 44 高行（`ui/chat/widgets/quote_chip.dart:28,34,44` `minimumSize`）。 |
| 3 | 命中区 | 气泡整块可点/长按（`ui/chat/widgets/user_bubble.dart` 手势 + `LongPress`）；菜单项为整行（44+）；引用 chip 右侧 × 是独立命中区（`quote_chip.dart`）。 |
| 4 | 手势 | 单击气泡 = 选中/无动作（按当前配置）；**长按 = 消息菜单**（复制/引用/编辑/重新发送/分叉等，`ui/chat/widgets/message_menu.dart:105-206`）；滑动不绑。与列表滚动仲裁：长按需超过 touch slop 不移动才成立（Flutter `GestureDetector.onLongPress` 默认）。 |
| 5 | 行为 | 菜单动作：复制（`Clipboard.setData` + 触感 + brief notice，`ui/chat/widgets/message_actions.dart:19-28`）、引用（塞进 composer tray）、编辑重发（回填输入框）、分叉（新建会话）。均幂等或可撤销（引用可移除）。 |
| 6 | 视觉状态 | 气泡 `default`/`pressed`（长按后菜单弹出）；菜单项 `default`/`pressed`/`disabled`；引用 chip 有删除键。 |
| 7 | 布局占位与展开形态 | 气泡参与布局流、长高；菜单是 sheet-overlay（G-03 C），不占位；引用 chip 在 tray 内展开/收起（`ExpandReveal`，`ui/chat/composer.dart:583-588`）。 |
| 8 | 滚动语义 | 气泡不滚；菜单列表可滚（`ListView`，`ui/chat/widgets/message_menu.dart:188,367`）；tray 横向可滚（附件条同款）。 |
| 9 | 动效 | 菜单 `showAppSheet` 弹入/落回（`AppMotion.sheetIn/Out` 380/260 ms，`ui/theme/motion.dart:35-36`）；引用 chip 进出用 `ExpandReveal`（`ui/chat/composer.dart:583-588`）。 |
| 10 | 边界态 | 空文本消息不渲染；超长文本换行；图片消息走 `chat.msg.image`；大字号跟随 `_ChatTextScale`（`ui/chat/chat_screen.dart:2669-2706`）；横屏同布局。 |
| 11 | 无障碍 | 气泡 `Semantics` 带文本；长按菜单每项有 label；引用 chip 有「移除引用」语义。 |
| 12 | 依据 | E0 `ui/chat/widgets/message_menu.dart:89-206,299,364-367`；E0 `ui/chat/widgets/message_actions.dart:19-28`；E0 `ui/chat/widgets/quote_chip.dart:28-44,92`；E0 `ui/chat/widgets/user_bubble.dart:163,304-309`；E0 `ui/chat/composer.dart:583-588`。 |
| 13 | 反证 + 验收 | **反证**：若只做气泡内右侧小按钮而不做长按菜单，手机上可点面积只剩 20~30dp，复制/引用变成"找不到的功能"；若长按同时触发文本选择与菜单，两者会争手势。**验收** GO-04：真机长按出菜单、选「复制」有 toast、引用后输入条出现可移除 chip。 |

#### `chat.msg.actions` — 回复操作条（复制/分享/朗读/分叉/重答/⋯）　（findings 5）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.actions`。所属：聊天面板→消息轮→最后一条回复下方。出现条件：轮内最后一条助手正文（`showActions`，`ui/chat/chat_rows.dart:45-58,189-190`）；按钮按能力增删（分享/朗读/分叉/重答可空）。 |
| 2 | 网格与定位 | 父 = 回复行底部；`Padding(top:4)` + `Bleed(horizontal:8)` + `Row`；每个键 36×36、图标 20、四周各 8（`ui/chat/widgets/message_actions.dart:96-105,193-208,212`）；右侧可挂回合脚注（`:192-205`）。 |
| 3 | 命中区 | 每键 36×36，`tapTargetSize: shrinkWrap`（`ui/chat/widgets/message_actions.dart:98-105`）——**低于 48**；`Bleed` 让首尾图标与正文左右缘对齐，横向各溢出 8 可点（`ui/common/bleed.dart:6-13,87-96`）。相邻键净距 0。 |
| 4 | 手势 | 单击 = 复制/分享/朗读（开/停）/分叉/重答/⋯；长按不绑；按键处于 busy 时 `onPressed:null`（`ui/chat/widgets/message_actions.dart:116-134,186-189`）。 |
| 5 | 行为 | 复制：写剪贴板 + 轻触感 + 1.4 s 对勾（`ui/chat/widgets/message_actions.dart:83-89`）；分享：打开分享面板；朗读：进入 loading/playing；分叉/重答：转圈直到完成，期间不可点；⋯：与长按同一个消息菜单。 |
| 6 | 视觉状态 | `default` 三级色；`pressed` 水波纹 + 轻触感；`busy` 16 圈；`done`（复制）主色对勾 1.4 s 后复原；`disabled` 透明 38%。 |
| 7 | 布局占位与展开形态 | 参与布局流，高 36（+top 4）；无展开。 |
| 8 | 滚动语义 | 不滚；随消息列表滚动。 |
| 9 | 动效 | 图标原地 `AnimatedSwitcher` 160 ms（淡入 + 0.7→1 缩放，`ui/chat/widgets/message_actions.dart:106-114`）；对勾 `DrawnCheck`（fluid 档，`:149-150`）。 |
| 10 | 边界态 | 能力缺失 → 键不渲染（`if (onShare != null)` 等，`ui/chat/widgets/message_actions.dart:154,165,186-189`）；无回复 → 整行不渲染；大字号：36 键不随字号变（固定），文字脚注换行；横屏：键数不变。 |
| 11 | 无障碍 | 每键 `tooltip` = label（读屏可见，`ui/chat/widgets/message_actions.dart:117,145,157,189`）；busy 时用 spinner 代替图标，语义仍为按钮。 |
| 12 | 依据 | E0 `ui/chat/widgets/message_actions.dart:19-28,83-89,96-114,116-134,141-212`；E0 `ui/common/bleed.dart:6-13,87-96`；E0 `ui/chat/chat_rows.dart:45-58,189-190`。 |
| 13 | 反证 + 验收 | **反证**：若把这些操作塞进长按菜单而不给行尾常驻键，手机上"复制回复"埋两层，且键盘/读屏用户无法一次到达；若给每键 48，6 个键在 360dp 屏上排不下，会换行或压缩正文。**验收** GO-05：真机截图——6 键一行不换行、点击有触感、复制出对勾。 |

#### `chat.msg.assistant-markdown` — 助手正文 Markdown + <details> 折叠 + 密钥遮罩　（findings 14）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.assistant-markdown`。所属：聊天面板→消息轮→助手正文。一条助手消息 1 个；正文内 `<details>` 各渲染 1 个折叠块（`ui/chat/render/markdown_blocks.dart:669-687`）；被识别为密钥的片段各 1 个遮罩（`ui/chat/secret_mask.dart`）。 |
| 2 | 网格与定位 | 父 = 消息行；正文左缘贴阅读列，行内代码/表格自带边框；`<details>` 标题行 = 图标 `fontSize*1.25` + 间距 `fontSize*0.25` + 文本，展开体左缩进 `fontSize*1.5`、上间隙 `fontSize*0.25`（`ui/chat/render/markdown_blocks.dart:714-740`）。 |
| 3 | 命中区 | `<details>` 标题行整行可点（`GestureDetector(behavior: opaque)`，`ui/chat/render/markdown_blocks.dart:711-732`）；链接为正文内 span，热区被自绘 RenderProxyBox 补到 `kMinInteractiveDimension`（48，`ui/remote/relay_credit.dart:135-139,236-259`——同一实现类，用于链接热区）；密钥遮罩整块可点以显示明文（`ui/chat/secret_mask.dart:681-724`）。 |
| 4 | 手势 | 单击 `<details>` 标题 = 展开/收起（`ui/chat/render/markdown_blocks.dart:713`）；正文文字长按 = 选择（`SelectionArea`，`ui/chat/render/code_block.dart:551-556` 同策略）；横向滑动 = 表格/公式横滚（`HorizontalEdgeFade`）；密钥单击 = 显示/隐藏。 |
| 5 | 行为 | `<details>`：`_open` 初值取 `initiallyOpen`（来自 HTML `open` 属性），点击切换（`ui/chat/render/markdown_blocks.dart:679-694`）；密钥：切换明文/掩码；复制走行内复制按钮（代码块）。 |
| 6 | 视觉状态 | `<details>`：标题字重 500、图标旋转 0/0.25 圈；密钥：掩码态为等宽圆点、揭示态为明文；长按选择态由 `SelectionArea` 提供。 |
| 7 | 布局占位与展开形态 | `<details>` 折叠态**不占位**（`SizedBox(width: double.infinity)` 零高，`ui/chat/render/markdown_blocks.dart:734-744`）；展开后兄弟整体下移（inline，G-03 A）。密钥遮罩行内替换，不改变行数。 |
| 8 | 滚动语义 | 正文随会话滚；表格/公式区横向内滚并两端 24px 渐隐（`ui/common/edge_fade.dart:11-51`，`ui/chat/render/markdown_body.dart:809` 统一套 `EdgeFadeScrollBehavior`）；`<details>` 展开体不内滚。 |
| 9 | 动效 | `<details>`：`SmoothSize` + `AnimatedRotation` 240 ms（`ui/chat/render/markdown_blocks.dart:702,717-721,734-744`）；密钥揭示无动画。 |
| 10 | 边界态 | `<details>` 无 summary → 显示「详情」（`ui/chat/render/markdown_blocks.dart:674-686,726`）；超长正文：代码块/长文本走各自截断（见 `chat.msg.code-block`/`chat.tool.detail`），Markdown 本体不截断；未知 HTML 标签原样显示、`<details>` 换成折叠块（`ui/chat/render/markdown_body.dart:39-41`）；大字号：缩进/图标按 `fontSize` 算（`markdown_blocks.dart:700-702`）；横屏：正文宽度跟随阅读列。 |
| 11 | 无障碍 | `<details>` 头 `Semantics(button, expanded)`（`ui/chat/render/markdown_blocks.dart:708-712`）；链接 span 自带语义；密钥遮罩有开/关语义（`ui/chat/secret_mask.dart`）。 |
| 12 | 依据 | E0 `ui/chat/render/markdown_blocks.dart:669-748`；E0 `ui/chat/render/markdown_body.dart:36-41,809`；E0 `ui/chat/render/markdown_split.dart:19,193`；E0 `ui/chat/secret_mask.dart:681-727,749`；E0 `ui/remote/relay_credit.dart:135-139,200-259`；E0 `ui/common/edge_fade.dart:11-51`。 |
| 13 | 反证 + 验收 | **反证**：若 `<details>`「展开」改为固定小窗内滚，手机上一段长思考/长答案被锁在 200dp 窗口里且与外层抢手势；若链接热区只等于文字高度（约 13dp 行），手指永远点不中。**验收** GO-06：真机点开 `<details>` 全文可读、链接点击命中、表格横滑不带动消息列表纵向滚动。 |

#### `chat.msg.code-block` — 代码块（限高内滚 + 横向渐隐 + 复制/换行）　（findings 10）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.code-block`。所属：聊天面板→正文/工具详情→代码块与 diff。每块 1 个；diff 作为代码块的一种（`ui/chat/render/code_block.dart` 内 `_DiffLines`）。 |
| 2 | 网格与定位 | 父 = Markdown 块或工具详情；块 header（语言 + 复制/换行键）+ 代码体；代码 padding 16/12（dense 时 12/4）（`ui/chat/render/code_block.dart:536-544`）；行号列右对齐（`:557-575`）。 |
| 3 | 命中区 | 复制键/换行键在 header，44 高行内 icon 按钮（`ui/chat/render/code_block.dart:780-783` `minimumSize`）；代码体本身不是按钮，长按选字。 |
| 4 | 手势 | 单击复制 = 复制整段（触感 + toast）；换行键 = 横滚/折行切换（`_wrap`）；长按选字（`SelectionContainer`，`ui/chat/render/code_block.dart:551-556`）；横向拖动 = 横滚（不折行时）。 |
| 5 | 行为 | 复制：`Clipboard`；折行：切换 `_wrap` 并重建；长按：并入外层 `SelectionArea`（外面已有选区时不再套，`:554-556`）。 |
| 6 | 视觉状态 | `default` 底色按主题 codeBlockStyle（well/outlined/card，`ui/chat/render/code_block.dart:600-617`）；`pressed` 按钮态；横滚到边：`HorizontalEdgeFade` 24px 渐隐（`ui/common/edge_fade.dart:53-67,111-124`）。 |
| 7 | 布局占位与展开形态 | 参与布局流；高度由内容决定但**受 maxHeight 约束 → 内滚**（当 `widget.maxHeight != null`，`ui/chat/render/code_block.dart:577-583`）；展开/收起由工具行或 Markdown 决定，块自身无展开。 |
| 8 | 滚动语义 | 代码体纵向上限 = 工具详情的 `_kDetailMaxHeight=280`（`ui/chat/widgets/tool_row.dart:32,465`）；到边后 `OverscrollForwarder` 把纵向 overscroll 转交外层会话（`ui/chat/render/code_block.dart:577-583` + `ui/common/overscroll_forwarder.dart:24-37`）；横向滚动独立、不转交（`:27`）。 |
| 9 | 动效 | 横滑跟手；无展开动画；渐变随 `ScrollMetrics` 变化（`ui/common/edge_fade.dart:30-50`）。 |
| 10 | 边界态 | 空代码 → 空体；超长行 → 横滚（不折行时）；超长内容 → 内滚，不撑爆列表（高度锁 280）；大字号 → 行高跟随 `AppType`；横屏 → 宽度变宽。 |
| 11 | 无障碍 | 复制键有 tooltip；代码文本可被读屏按行读取；无自绘可点区域。 |
| 12 | 依据 | E0 `ui/chat/render/code_block.dart:536-583,600-617,664,702-703,774-783`；E0 `ui/common/overscroll_forwarder.dart:8-47`；E0 `ui/common/edge_fade.dart:11-67`；E0 `ui/chat/widgets/tool_row.dart:32,465`。 |
| 13 | 反证 + 验收 | **反证**：若代码块不封顶且不内滚，一段 500 行的终端输出会把会话拉成几十屏（回底钮几乎永远可用）；若横向也转交纵向手势，左右看长行时会带着页面上下乱滑。**验收** GO-07：真机打开长代码井内滚到底后，继续拖才轮到会话滚动；横向滑不触发会话滚动。 |

#### `chat.msg.image` — 正文/工具图片 + 全屏查看器 + 图库　（findings 41）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.image`。所属：聊天面板→正文图片/工具图片/生成结果。每张图 1 个缩略；点击进全屏查看器（`ui/chat/media/media_viewer.dart`）；同组多图可横滑成图库（`ui/chat/widgets/image_gallery.dart`）。 |
| 2 | 网格与定位 | 缩略按列宽等比、最大高受 `maxHeight` 约束（`ui/chat/media/media_card.dart:109,133`）；查看器全屏（黑底 + 顶部返回/动作、底部计数）；图库横向 page（`ui/chat/widgets/image_gallery.dart:58` `PageScrollPhysics`）。 |
| 3 | 命中区 | 缩略整块可点（`GestureDetector(opaque)`，`ui/chat/render/markdown_image.dart:803-828,979`）；长按图 = 菜单（保存/分享/复制，`media_card.dart:625-755`）；查看器双击缩放、双指缩放。 |
| 4 | 手势 | 单击 = 全屏；长按 = 图片菜单；双击 = 1×/2× 缩放；双指缩放 + 平移；横滑 = 同组切换（未缩放时）。与会话滚动仲裁：查看器是独立路由，不在列表内。 |
| 5 | 行为 | 全屏查看器：返回、保存到相册（`showBriefNotice` 确认）、分享、复制；生成图另有重新生成/编辑/首帧等动作（`ui/chat/media/media_card.dart:380-466,759`）；图库翻页计数。 |
| 6 | 视觉状态 | 缩略 `default`/占位/失败重试（`ui/chat/render/markdown_image.dart` 加载失败可点重载）；查看器 `default`/缩放态/加载态；生成中占位骨架。 |
| 7 | 布局占位与展开形态 | 缩略参与布局流；查看器 = 全屏路由（G-03 C，不占位）；图库 = 查看器内横向 PageView。 |
| 8 | 滚动语义 | 图库横滑由 `PageScrollPhysics`；缩放时切 `NeverScrollableScrollPhysics`（`ui/chat/widgets/image_gallery.dart:58`）→ 缩放态禁用翻页（G-04 的正向实现：手势归属明确）；查看器内无纵向滚动。 |
| 9 | 动效 | 查看器进场淡入/缩放（路由过渡由 `ui/theme/page_transitions.dart` 统一）；图库翻页跟手。 |
| 10 | 边界态 | 加载失败 → 占位 + 重载；超长图 → 缩放查看；多图 → 图库横滑；大字号不影响图片；横屏 → 查看器适配宽高。 |
| 11 | 无障碍 | 图片有语义标签（alt/文件名）；查看器有「关闭」「保存」标签；图库播报「第 N / 共 M」。 |
| 12 | 依据 | E0 `ui/chat/render/markdown_image.dart:803-828,979`；E0 `ui/chat/media/media_viewer.dart:559`；E0 `ui/chat/media/media_card.dart:109-133,380-466,622-759`；E0 `ui/chat/media/media_video_viewer.dart`；E0 `ui/chat/widgets/image_gallery.dart:58,150-156`；E0 `ui/chat/widgets/tool_images.dart:142`。 |
| 13 | 反证 + 验收 | **反证**：若图片点击直接外跳系统相册/浏览器，用户失去同组翻页与「保存到相册」的确定结果；若缩放态仍允许翻页，双指缩放会误触发翻页（dsh-Go 明确在缩放时切 `NeverScrollable`）。**验收** GO-08：真机点图进查看器、双指缩放不翻页、长按可保存。 |

#### `chat.msg.thinking` — 思考行（32dp 单行 + 三行实时窗口 + 展开全文）　（findings 9）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.thinking`。所属：聊天面板→消息轮→过程区。每条带 reasoning 的助手消息 1 个；一轮可 0..N 个（按 `reasoning` 分块）。出现条件：`reasoning` 非空（`ui/chat/widgets/thinking_pill.dart:54-62`）。 |
| 2 | 网格与定位 | 与工具行同一解剖：图形列 24 + 间隙 8 + 文字列（`ui/chat/widgets/stream_row.dart:11-33`）；行最小高 32（24 图标 + 上下 4，`:29-30,46-68`）；实时窗口恒 3 行（`ui/chat/widgets/thinking_pill.dart:389-440`）；展开体左缩进 `StreamRow.indent=32`、上 4 下 8（`:225-227`）。 |
| 3 | 命中区 | 折叠头整行可点（`StreamRowFrame(onTap)`，`ui/chat/widgets/thinking_pill.dart:181-185`）：命中 = 行宽 × 32 + `Bleed` 横向各 8（`ui/common/bleed.dart:87-96`）→ 竖向仅 32，**低于 44**。展开态正文整块也可点以收起（`_CollapsibleReasoningBody`，`ui/chat/widgets/thinking_pill.dart:360-381`）。 |
| 4 | 手势 | 单击头 = 展开/收起（`:184`）；展开后单击正文 = 收起（`:367-380`，链接/代码复制按钮更里层仍优先）；长按 = 选字（`SelectionArea`）；滑动交给外层列表。 |
| 5 | 行为 | 展开：`_expanded=true`，槽内从三行实时窗口换成完整 Markdown（`:199-238`）；一轮结束（`streaming:true→false`）自动收起（`:118-123`）；同一时刻只画一块内容（`_body` 单槽，`:217-238`）。 |
| 6 | 视觉状态 | `active`：品牌蓝图形 + 标签「思考中」+ 亮度扫过 + 转圈（`:131-160,243-292`）；`settled`：静态图形 + 「思考」+ 首行预览；`expanded`：头部不动、正文次级排版；`preview`：折叠时用全文首行并去掉 `**`（`:83-90`）。 |
| 7 | 布局占位与展开形态 | 折叠：头 32 高，恒 3 行窗口（流式中高度不变，`:383-388,416-440`）；展开：inline 不封顶（G-03 A），下方兄弟整体下移；展开动画 `SmoothSize` 160 ms（`:219-229`），流式中**不做高度动画**（`:220-223`）。 |
| 8 | 滚动语义 | 展开内容随会话滚，思考行自身无滚动容器 → G-04 不适用；实时窗口内部用 `ClipRect + AnimatedContainer` 上滑（不是滚动，`:425-440`）。 |
| 9 | 动效 | 窗口换行上滑：160 ms 匀速（`:426-431`）；展开/收起：160 ms（`:223`）；标签扫光：起始 600 ms、每 1 s 一次、每 4 s 一轮（`:259-262`）；图形旋转慢。 |
| 10 | 边界态 | 空 reasoning → 不渲染（由上层判断）；超长 → 预览单行 + 右端 40px 渐隐遮罩（`:164-173`，G-05）；引擎开「思考翻译」时展开体加「翻译/查看原文」（`:49-51`）；大字号：行高按 `textScaler` 算（`:401-402`）；横屏：三行窗口保留。 |
| 11 | 无障碍 | `Semantics(button, expanded)`（`:181-184`）；运行中标签本身是文字；展开体正文可读屏按段读。 |
| 12 | 依据 | E0 `ui/chat/widgets/thinking_pill.dart:54-62,83-123,131-186,199-238,243-292,360-440`；E0 `ui/chat/widgets/stream_row.dart:11-33,46-68`；E0 `ui/common/bleed.dart:87-96`。 |
| 13 | 反证 + 验收 | **反证**：若折叠行不定高（G-02 违反），思考摘要每帧变化都会推走下方正文；若展开改成封顶内滚（G-03 B），长思考被锁在小窗且与外层抢手势；若流式窗口高度随文字增长，列表每行都在追底。**验收** GO-09：真机录屏——思考 20 s 内收起行高恒 32、点开可读全文、结束后自动收起。 |

#### `chat.turn.process` — 整轮过程折叠（摘要行 + 展开过程 + 改动/产物卡）　（findings 23）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.turn.process`。所属：聊天面板→消息轮。出现条件：`foldCompletedTurns` 开启且该轮正常结束（`ui/chat/chat_rows.dart:373-390`）；一轮可切成多片（运行中插话），最后一片保留最终回答（`chat_rows.dart:128-153`）；被停下/出错的轮**不**折叠（`:126-127`）。改动卡/产物卡挂在轮尾（`:393-407`）。 |
| 2 | 网格与定位 | 摘要行与工具行同解剖（24+8+文字列，`ui/chat/widgets/turn_process.dart:159-179`）；展开体左缩进 `StreamRow.indent` + 左侧一道 1px 细线在 x=12（`ui/chat/widgets/stream_row.dart:100-138`）；上下内边距 4（`turn_process.dart:233`）。 |
| 3 | 命中区 | 摘要行整行可点（`StreamRowFrame(onTap)`，`ui/chat/widgets/turn_process.dart:165-166`）→ 32 高、横向 Bleed 8 可点；展开后每行子内容各自有命中区。 |
| 4 | 手势 | 单击摘要行 = 展开/收起（`:164-166`）；展开后点某一行 = 该行自己的动作（工具行/思考行）；拖动 = 外层列表滚动（展开动画自己补偿滚动位置，不抢手势）。 |
| 5 | 行为 | 展开时按 `rows` 原序重放（思考→工具→中间消息），`copy/toolCalls/messages` 计数进摘要（`ui/chat/widgets/turn_process.dart:53-62`）；一轮刚结束时先按展开态出现再自动收成摘要（`settle`，`:193-218`）。 |
| 6 | 视觉状态 | 摘要行三段：`✓ 已完成 · N 次工具调用 · M 条消息`（`:53-62`）；`settle` 中展开体缩小 + 摘要放大交叉（`:193-218`）；`open` 时 chevron 旋转 180°（`stream_row.dart:85-98`）。 |
| 7 | 布局占位与展开形态 | 折叠：摘要行 32 高；展开：**inline 不封顶**（G-03 A），兄弟整体下移。关键实现：展开动画每帧在布局前把长出来的高度从滚动位置扣掉（`_holdHeader`，`ui/chat/widgets/turn_process.dart:129-151`）→ 摘要在屏幕上不动，内容向下展开。 |
| 8 | 滚动语义 | 展开内容随外层列表滚，无内滚；`_holdHeader` 只在 `growthDirection == reverse`（头部插入段）补偿（`:144-151`）。 |
| 9 | 动效 | 展开/收起 `SizeTransition` 280 ms（`AppMotion.collapse`，`ui/theme/motion.dart:29`）+ 淡入 `Interval(0.3,1)`（`turn_process.dart:65-78,225-236`）；`settle` 用 240 ms（`:93`）。 |
| 10 | 边界态 | 只有思考没有工具/消息 → 不收这一层（`:355-356`）；被中断/出错 → 不折叠（`chat_rows.dart:126-127`）；大字号 → 摘要行随文字长高且仍单行截断（`turn_process.dart:171-173`）；横屏：不封顶意味着可能一屏放不下（与 G-03 B 相反的选择）。 |
| 11 | 无障碍 | 摘要行 `Semantics(button, expanded=true/false, label=摘要文案)`（`ui/chat/widgets/turn_process.dart:159-164`）。 |
| 12 | 依据 | E0 `ui/chat/widgets/turn_process.dart:14-62,64-151,159-238`；E0 `ui/chat/chat_rows.dart:126-153,183-203,355-356,373-410`；E0 `ui/chat/widgets/stream_row.dart:46-138`；E0 `ui/theme/motion.dart:26-29`。 |
| 13 | 反证 + 验收 | **反证**：DSH Web 的过程组是封顶内滚（`min(400px,50vh)`）；dsh-Go 手机上改成**不封顶 + 逐帧滚动补偿**——若照抄桌面版的封顶内滚，手机上内外两层滚动会抢手势（G-04 症状），且展开点开的长内容被锁在小窗。**验收** GO-10：真机展开一轮 10 次工具调用的过程、滚动位置不跳、再点收起回到原处。 |

#### `chat.msg.notice` — 系统提示行 / 活动条 / 后台任务通知 / 会话卡　（findings 13）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.notice`。所属：聊天面板→消息流与输入条上方。`NoticeRow` 每条系统提示 1 行（压缩/停止/错误等，`ui/chat/widgets/notice_row.dart`）；活动条 1 条（运行中轨迹，`activity_strip.dart`）；后台任务提示 1 条（`background_task_notice.dart`）；会话卡（`chat_card.dart`）。 |
| 2 | 网格与定位 | 提示行与工具行同解剖（24+8+文字列）并带操作键；活动条贴着输入条上沿（`ui/chat/widgets/activity_strip.dart:216-227`）；操作键 `minimumSize:48×48` 或 `40×40`（`notice_row.dart:264,273`）、后台任务键 `48×48`（`background_task_notice.dart:67`）。 |
| 3 | 命中区 | 整行可点（有动作时）；行内按钮 ≥40~48（`notice_row.dart:196-197,264,273`）；活动条整条可点看轨迹。 |
| 4 | 手势 | 单击 = 对应动作（重试/打开设置/看轨迹/停止后台任务）；长按 = 复制文本（部分）；与列表滚动仲裁由 `InkWell` 处理。 |
| 5 | 行为 | 活动条：点开运行轨迹 sheet（`ui/chat/chat_screen.dart:2416-2421`）；后台任务：停止/查看；提示行：按 `kind` 提供重试/忽略。 |
| 6 | 视觉状态 | 提示按语气取语义色（warn/danger/info）；活动条运行中带进度/脉冲；后台任务有 busy 点。 |
| 7 | 布局占位与展开形态 | 参与布局流（提示行在消息流内；活动条在输入条上方、占一条高度，但只在运行中出现）；无展开。 |
| 8 | 滚动语义 | 随会话滚（提示行）/固定（活动条）；无内滚。 |
| 9 | 动效 | 活动条脉冲；提示行进出淡入（`AppMotion.quick`）；后台任务点呼吸。 |
| 10 | 边界态 | 离线/错误 → 提示行文案 + 重试键；后台被系统杀 → 后台任务提示；大字号 → 行高增；横屏键盘时胶囊行让位。 |
| 11 | 无障碍 | 行内按钮有 tooltip/语义；活动条播报「正在执行 N 步」。 |
| 12 | 依据 | E0 `ui/chat/widgets/notice_row.dart:196-197,264,273`；E0 `ui/chat/widgets/activity_strip.dart:216-227`；E0 `ui/chat/widgets/background_task_notice.dart:62-67`；E0 `ui/chat/widgets/chat_card.dart:153`；E0 `ui/chat/chat_screen.dart:2416-2421`。 |
| 13 | 反证 + 验收 | **反证**：若把"运行轨迹"做成常驻一大块，手机上输入条上方被吃掉 1/5 屏；若提示行不带动作键，用户只能去设置里找恢复入口。**验收** GO-11：真机运行中出活动条、点开看轨迹、后台任务可停。 |

#### `chat.tool.row` — 工具调用行（32dp 摘要 + 状态尾 + 失败/沙箱标记）　（findings 4）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.tool.row`。所属：聊天面板→消息轮→工具调用。每次工具调用 1 行（`ui/chat/widgets/tool_row.dart:34-43`）；连续 ≥3 次且无原生卡片时收成组（`ui/chat/chat_rows.dart:177,236-263`）；带原生卡片的调用单独成行并默认展开（`tool_row.dart:101-108`）。 |
| 2 | 网格与定位 | 32dp 单行 = 图标块 24 + gap 8 + 标题/摘要 + 行尾状态（`ui/chat/widgets/stream_row.dart:11-33`）；标题「动词 · 对象」、行尾状态图标 16、diff 计数等（`ui/chat/widgets/tool_row.dart:230-290`）。 |
| 3 | 命中区 | 整行可点（`StreamRowFrame(onTap)`，`ui/chat/widgets/tool_row.dart:249-250`）→ 行高 32 + 横向 Bleed 8；行高低于 44（同思考行）。 |
| 4 | 手势 | 单击 = 展开/收起详情；子代理行单击 = 打开子代理会话（`tool_row.dart:138-141`）；长按不绑；拖动交给列表。 |
| 5 | 行为 | 展开：详情井（参数/结果/diff/图片）在下方长出（`tool_row.dart:429-560`）；审批中 → 行尾 spinner + 审批卡；失败 → 红色摘要 + 失败图标（`:239-240,391-419`）；沙箱阻断 → 锁形图标 + 空尾（`:291-311,374-375`）、「查看完整输出」进全页。 |
| 6 | 视觉状态 | `streaming/running`：spinner；`awaitingApproval`：spinner/警告；`failed`：红色 + 失败图标；`denied`：拒绝图标；`cancelled`：取消；`succeeded`：diff 计数（+n −m）或空（`tool_row.dart:391-419,162`）。 |
| 7 | 布局占位与展开形态 | 折叠：32 高；展开：inline 长出详情（详情内部再按 `_kDetailMaxHeight=280` 封顶内滚，`tool_row.dart:32,461-465`）→ 混合：外层不封顶、内层封顶（G-03 B 用于内井）。 |
| 8 | 滚动语义 | 详情井内滚到边后由 `OverscrollForwarder` 转交外层（见 `chat.tool.detail`）；行本身不滚。 |
| 9 | 动效 | 行内 spinner；展开/收起 160~240 ms；状态尾切换 `AnimatedSwitcher`（`tool_row.dart:311-360` fluid 档）。 |
| 10 | 边界态 | 失败/超时/拒绝/取消各有图标与红色文案（`tool_row.dart:239-240,391-419`）；超长参数/结果 → 详情内截断 + 「查看全部」；大字号 → 行高随文字；横屏同布局。 |
| 11 | 无障碍 | 整行 `Semantics(button)` + 标题/状态播报；行尾图标无语义（由标题承载）。 |
| 12 | 依据 | E0 `ui/chat/widgets/tool_row.dart:32-43,96-141,162,230-311,374-419,429-465,535-560,649-748`；E0 `ui/chat/widgets/stream_row.dart:11-33,46-68`；E0 `ui/chat/chat_rows.dart:60-79,177,236-263`。 |
| 13 | 反证 + 验收 | **反证**：若工具行不折叠而全部平铺，一轮 20 次调用会把会话拉成几十屏；若行高按 44~48 设计，一轮工具调用就多占半屏，正文被推远。**验收** GO-12：真机单行 32、状态尾准确、失败行红色可辨、点开详情可读。 |

#### `chat.tool.detail` — 工具详情井 / 步骤详情页 / diff 页（限高内滚 + 截断 + 全文页）　（findings 18）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.tool.detail`。所属：聊天面板→工具卡展开体；另有独立全屏页（`ui/chat/step_detail_page.dart`、`ui/chat/diff_page.dart`）。工具行展开时 1 个井；长文本被截断时井底 1 条「查看全部」进全页。 |
| 2 | 网格与定位 | 井：`ConstrainedBox(maxHeight)` + `SingleChildScrollView`；`LongTextWell` 上限由调用方给（工具详情 280，`ui/chat/widgets/tool_row.dart:32`）；井底「查看全部」条 `minHeight:44`、左右 12/8（`ui/chat/render/long_text.dart:261-307`）；截断标记行上下 padding 10（`:238-257`）。 |
| 3 | 命中区 | 「查看全部」条整条可点（`InkWell` + `minHeight:44`，`ui/chat/render/long_text.dart:279-306`）；省略 N 行那行整行可点（`GestureDetector(opaque)`，`:238-257`）；全页返回键 40×40（`ui/chat/step_detail_page.dart:698`）。 |
| 4 | 手势 | 单击「查看全部」/省略行 = 进全页；全页里长按选字 + 复制全部；拖动 = 井内滚动（到边转交外层）。 |
| 5 | 行为 | 打开全页 `OutputPage.route`（`ui/chat/render/long_text.dart:165-168`）；全页支持复制全部/分享；diff 页支持按文件折叠。 |
| 6 | 视觉状态 | 井底条：三级色摘要 + 主色「查看全部 ›」（`long_text.dart:261-307`）；全页：系统标题 + 等宽正文。 |
| 7 | 布局占位与展开形态 | 井：**封顶内滚**（G-03 B）；全页：独立路由不占位（G-03 C）。 |
| 8 | 滚动语义 | 内层先滚，到边后由 `OverscrollForwarder` 把纵向剩余拖动交给外层会话列表（`ui/chat/render/long_text.dart:202-210` + `ui/common/overscroll_forwarder.dart:24-37`）；横向不转交（`:27`）。 |
| 9 | 动效 | 无展开动画；渐隐跟手；全页进出用统一路由过渡。 |
| 10 | 边界态 | 超长：头 60 行 + 尾 140 行、总 16000 字（`long_text.dart:14-19`），中间「省略 N 行」；单行超长 → 按字截断（`:70-111`）；ANSI 着色跨截断保留（`:109`）；大字号 → 行高增、井高不变；横屏 → 井宽变宽。 |
| 11 | 无障碍 | 「查看全部」有 label；正文可读屏；井底条 `Semantics(button)`（`long_text.dart:238-241`）。 |
| 12 | 依据 | E0 `ui/chat/render/long_text.dart:14-19,70-111,113-221,223-309`；E0 `ui/common/overscroll_forwarder.dart:8-47`；E0 `ui/chat/step_detail_page.dart:698`；E0 `ui/chat/diff_page.dart:163-274`；E0 `ui/chat/widgets/tool_row.dart:32,429-560`。 |
| 13 | 反证 + 验收 | **反证**：若把 500KB 终端输出整段排版，列表帧率直接崩（代码注释：`long_text.dart:1-3` 明说"Laying out a megabyte of text as one paragraph is what froze the list"）；若井不封顶，一次输出撑爆整屏。**验收** GO-13：真机打开 1MB 输出井不卡、内滚到边后外层接着滚、点「查看全部」进全页。 |

#### `chat.tool.plugin-view` — 插件原生视图（DshUi 工具卡 / 表单 / 图表 / 选择）　（findings 58）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.tool.plugin-view`。所属：聊天面板→工具卡与插件 UI。出现条件：工具调用带 `meta['dsh-go/view']` 原生卡片（`ui/chat/widgets/tool_row.dart:79-81,124-130`）或插件在主聊天里注册了 UI 块（`ui/chat/dsh_ui/*`）。数量取决于插件与工具返回。 |
| 2 | 网格与定位 | 父 = 工具行/卡片槽；块内为插件声明的控件网格（行/列/表单/图表/选择），命中尺寸用插件控件默认值（`ui/chat/dsh_ui/dsh_ui_plugin_controls.dart:109-118,522` 48 高、`dsh_ui_block.dart:156-161` 32 高+shrinkWrap）。 |
| 3 | 命中区 | 控件级：按钮 48 高（`dsh_ui_plugin_controls.dart:109,522,924,1646`）、块级按钮 32 高 + `shrinkWrap`（`dsh_ui_block.dart:156-161`）；`Semantics` 覆盖 53 处（`dsh_ui*`），可点区域多为整块/整行。 |
| 4 | 手势 | 单击/长按由插件声明（选择项、提交、展开、图表点选）；滚动交给外层，表单内长列表（`ListView`）自带滚动（`dsh_ui_blocks.dart:520`、`dsh_ui_plugin_controls.dart:513,1588`）。 |
| 5 | 行为 | 控件行为由插件 `action` 回传（提交/选择/打开），失败在卡内显示错误文案；无统一状态机（插件自定）。 |
| 6 | 视觉状态 | 按插件样式 token 渲染（`ui/chat/dsh_ui/dsh_ui_plugin_styles.dart`）；禁用/只读态由插件给。 |
| 7 | 布局占位与展开形态 | 参与布局流；可展开块按插件声明；表单内滚动封顶（`maxHeight`，`dsh_ui_blocks.dart:703,736-737`）。 |
| 8 | 滚动语义 | 表单/列表内滚优先，到边后转交外层（与 `chat.tool.detail` 同机制：外层是会话列表）；图表横滑独立。 |
| 9 | 动效 | 由插件声明；块进出无统一动画。 |
| 10 | 边界态 | 插件缺失 → 退化为普通工具行（`tool_row.dart:124-130`）；空数据 → 块不渲染；大字号 → 控件随文字；横屏 → 块宽自适应。 |
| 11 | 无障碍 | 控件 `Semantics`（按钮/开关/输入）；插件可给 label。 |
| 12 | 依据 | E0 `ui/chat/dsh_ui/dsh_ui_block.dart:156-161`；E0 `ui/chat/dsh_ui/dsh_ui_blocks.dart:253,520-523,571,624-649,703-737`；E0 `ui/chat/dsh_ui/dsh_ui_plugin_controls.dart:109-118,513-522,924,1588,1646`；E0 `ui/chat/dsh_ui/dsh_ui_plugin_choice.dart`；E0 `ui/chat/widgets/tool_row.dart:79-81,124-130`。 |
| 13 | 反证 + 验收 | **反证**：若插件卡沿用桌面 Web 的 hover/小目标交互，手机上点不中；若插件块不封顶内滚，一张插件表单会把会话拉长到无法阅读。**验收** GO-14：真机装入带原生卡的插件，卡内按钮可点、表单可滚、失败有文案。 |

#### `chat.empty` — 空态（新对话页 / 建议卡 / 工作区与文件夹入口）　（findings 22）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.empty`。所属：聊天面板→空态。出现条件：`sessionId == null` 或会话为空（`ui/chat/chat_screen.dart:1885-1935`）。建议卡 0..N（设置开启 `chatSuggestions` 时），底部一行「换一条提示」轮换。 |
| 2 | 网格与定位 | 空态内容居中偏上（`Alignment(0,-0.25)`，`ui/chat/chat_screen.dart:1889`），可整页 `SingleChildScrollView`（`:1890`）；建议卡横排小卡（`ui/chat/empty_actions.dart`）；工作区/文件夹入口在建议下方（`ui/chat/new_chat_options.dart`）。 |
| 3 | 命中区 | 建议卡整卡可点（`InkWell`）；「换一条」提示行整行可点；工作区入口整行可点；`empty_actions.dart:108,712` 有 `minimumSize`。 |
| 4 | 手势 | 单击建议卡 = 把提示填进输入条（不直接发送，`ui/chat/chat_screen.dart:1910`）；单击「换一条」= 下一条提示；单击文件夹/工作区 = 选择；最近会话行 = 打开该会话。 |
| 5 | 行为 | 建议 → `_onSuggestion` 写入 composer；文件夹 → 新建会话的起始目录；工作区 → sheet 选择/管理（`:1924-1928`）；最近会话 → 切换会话。 |
| 6 | 视觉状态 | 建议卡：surface 玻璃 + 主色图标；`pressed` 水波纹；提示行三级色；入口行 secondary。 |
| 7 | 布局占位与展开形态 | 参与布局流（空态页自身可滚）；无展开。 |
| 8 | 滚动语义 | 整页 `SingleChildScrollView`（`ui/chat/chat_screen.dart:1890`）——键盘弹起时不遮；到边无转交（页面根）。 |
| 9 | 动效 | 建议卡按压 160 ms；提示轮换淡入。 |
| 10 | 边界态 | 无建议（设置关）→ 只剩标题与输入条；无工作区 → 隐藏入口；本地引擎失败 → 顶部引擎失败卡（`ui/chat/chat_screen.dart:1896-1897`）；大字号 → 卡片换行；横屏 → 居中列。 |
| 11 | 无障碍 | 建议卡 `Semantics(button)` + 提示文案；提示行有「下一条建议」label。 |
| 12 | 依据 | E0 `ui/chat/chat_screen.dart:1885-1935`；E0 `ui/chat/empty_actions.dart:90-136,206,712`；E0 `ui/chat/empty_state.dart`；E0 `ui/chat/new_chat_options.dart:190-281`。 |
| 13 | 反证 + 验收 | **反证**：若空态只放一行「没有消息」，新用户不知道能做什么、也不知道模型/目录从哪选；若建议卡直接发送，用户失去改写机会（误触即扣费）。**验收** GO-18：真机首启空态可见建议、点击填入输入条不发送、工作区入口可选。 |

#### `chat.dock` — 输入条上方 dock（目标条 / 待办条 / 运行条 + 目标 sheet）　（findings 20）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.dock`。所属：聊天面板→输入条上方。出现条件：本会话有目标（goal）、有 todo 列表、或正在运行（run strip）（`ui/chat/dock/chat_dock.dart`、`goal_bar.dart`、`todo_bar.dart`、`dock_stack.dart`）。同屏可同时 0..3 条。 |
| 2 | 网格与定位 | 贴在输入条上沿、宽 = 阅读列；每条 = 一行图标 + 文本 + 行尾动作；高由内容给，`maxHeight` 约束（`ui/chat/dock/todo_bar.dart:151-155`）。多条时 `DockStack` 叠放（`dock_stack.dart:198-219`）。 |
| 3 | 命中区 | 整条可点展开/跳转；行尾动作键独立；`HitTestBehavior` 折叠时 opaque、展开时 deferToChild（`ui/chat/dock/dock_stack.dart:216-219`）。 |
| 4 | 手势 | 单击目标条 = 打开目标 sheet（`ui/chat/dock/goal_sheet.dart:23`）；单击 todo 行 = 勾选/详情；长按 = 菜单；与键盘同现时：横屏打字让位（`_DockAside`，`ui/chat/chat_screen.dart:2498-2559`）。 |
| 5 | 行为 | 目标 sheet：查看/编辑/清除目标；todo 行：勾选完成；运行条：点开当前运行信息。 |
| 6 | 视觉状态 | `default`/`pressed`/`done`（todo 完成划线）；运行条带脉冲。 |
| 7 | 布局占位与展开形态 | 参与布局流，占输入条上方一条高度；`_DockAside` 在横屏打字时高度折叠到 0（`ui/chat/chat_screen.dart:2537-2559`）。 |
| 8 | 滚动语义 | 条内无滚动（`todo_bar.dart` 截断到 N 行）；随页面固定，不随消息列表滚。 |
| 9 | 动效 | 横屏键盘：`Align(heightFactor)` 折叠 + 透明度，240 ms（`ui/chat/chat_screen.dart:2537-2559`）；目标 sheet 弹入。 |
| 10 | 边界态 | 无目标/无 todo → 不渲染；超长目标 → 单行截断；大字号 → 条高增；横屏键盘 → 折叠。 |
| 11 | 无障碍 | 条 `Semantics`（11 处），播报目标/todo 数；行尾动作有 label。 |
| 12 | 依据 | E0 `ui/chat/dock/dock_stack.dart:198-219`；E0 `ui/chat/dock/goal_sheet.dart:23,55`；E0 `ui/chat/dock/todo_bar.dart:151-155`；E0 `ui/chat/chat_screen.dart:2498-2559`。 |
| 13 | 反证 + 验收 | **反证**：若把目标/待办放进顶栏，手机上顶栏被挤成两行且与标题抢宽；若它常驻不折叠，横屏打字时聊天只剩一条缝。**验收** GO-19：真机有目标时出目标条、点开可编辑、横屏打字时让位。 |

#### `share.panel` — 分享面板（范围 / 内容开关 / 图片 / 链接 / 文件 / 分享码 / 托管链接）　（findings 46）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `share.panel`。所属：聊天面板→分享（sheet 多页）。出现条件：回复操作条「分享」（`ui/chat/widgets/message_actions.dart:53-56,154-164`）或抽屉入口（`ui/share/share_entry.dart`）。 |
| 2 | 网格与定位 | 分享 sheet 多页：第一页（分享范围 + 内容开关 + 摘要卡）、链接/图片/文件/文字四页（`ui/share/share_panel_pages.dart:1-11`）；`minimumSize` 20 处；`shrinkWrap` 2 处。 |
| 3 | 命中区 | 整行可点；开关 `shrinkWrap`；底部动作栏按钮 `ShareActionBar.height`（`share_panel_pages.dart:467`）。 |
| 4 | 手势 | 单击 = 切页/切范围/复制/保存；开关 = 是否包含思考/工具/图片（`:6-9`）；返回 = 上一页。 |
| 5 | 行为 | 分享 → 生成图片/链接/文件/分享码（`ui/share_code/*`）；托管 → `ui/share_hosted/*` 管理链接。 |
| 6 | 视觉状态 | `default`/`selected`（范围）/`busy`（生成中）/`expired`（链接过期）。 |
| 7 | 布局占位与展开形态 | sheet-overlay（G-03 C），多页在同一 sheet 内切（`:1-11`）。 |
| 8 | 滚动语义 | sheet 内滚；到边不转交。 |
| 9 | 动效 | 页切换 160 ms；生成中进度。 |
| 10 | 边界态 | 无内容可分享 → 空；链接过期 → 重建；大字号 → 摘要卡换行；横屏 → sheet 限宽。 |
| 11 | 无障碍 | 开关/行 label；摘要卡数字播报。 |
| 12 | 依据 | E0 `ui/share/share_panel_pages.dart:1-11,119-144,259-298,449-467`；E0 `ui/share/share_picker.dart:31,260`；E0 `ui/share/share_image_page.dart:276-405,557-567`；E0 `ui/share_code/share_code_panel.dart:379`；E0 `ui/share_code/import_sheet.dart:50`；E0 `ui/share_code/profile_share_sheet.dart:27`；E0 `ui/share_code/share_content_sheet.dart`；E0 `ui/share_code/share_code_widgets.dart`；E0 `ui/share_hosted/hosted_links_page.dart`；E0 `ui/share_hosted/hosted_links_tile.dart`；E0 `ui/share_hosted/manage_links_button.dart:21`。 |
| 13 | 反证 + 验收 | **反证**：若分享直接把整段对话（含工具输出/密钥）导出，用户会泄露敏感内容（dsh-Go 有内容开关 + 密钥遮罩 `secret_mask`）；若分享只有「复制文本」一种，手机上没法把回复变成图片发微信。**验收** GO-69：真机分享回复成图片/链接/文件，关掉「工具调用」后内容不含工具输出。 |

### 2.2 输入区状态机（面板 ID：`composer`；归位：myco `composer`）

#### `composer.editor` — 输入条玻璃胶囊 + 1–6 行自适应编辑器　（findings 0）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.editor`。所属：输入区面板→编辑区。一个会话 1 个（`ui/chat/composer.dart:40-71`）；只读会话换成 `ReadOnlyBar`（`ui/chat/chat_screen.dart:2263-2264`）。出现条件：会话可写。 |
| 2 | 网格与定位 | chrome 玻璃胶囊，圆角 28（闭合）/ `AppShape.lg`（展开）（`ui/chat/composer.dart:31-34,640-706`）；单行时胶囊高 48 = 键 36 + 上下 gap 6，半径 28 = 键半径 20 + 8（`:33-35`）；文字在 40 高里居中（`:383-386`）；1–6 行自动增高（`maxLines: 6`，`:67,482-483`）；左右留白 18（无附件键）/6（`:603-606`）。 |
| 3 | 命中区 | 编辑器占剩余宽、整块可聚焦；`minLines:1,maxLines:6`（`ui/chat/composer.dart:482-483`）；点击胶囊空白处聚焦（TextField 的点击区域 = 自身矩形）；两侧 36 键净距 8（`:537-542`）。 |
| 4 | 手势 | 单击 = 聚焦弹键盘；输入多行；`textInputAction: newline`（`:484-485`）；硬件 Enter 发送是可选项（`hardwareEnterSends`，默认关，`:140-141,2267`）；`Escape` 关斜杠面板（`ui/chat/chat_screen.dart:248-258`）。 |
| 5 | 行为 | 文字变化 → 发送/麦克风/停止键在四态间切换（`:403-441`）；引文/附件在 tray 里（`:583-588,2316-2331`）；底部 footer（模型胶囊）在键盘弹起时展开成两行（`:144-156,532-568`）；草稿按会话保存/恢复（状态层 `draft_store`）。 |
| 6 | 视觉状态 | `default`（玻璃）/`focused`（圆角收紧为 lg，`_ComposerGlass` 动画）/`disabled`（未连接时仍可打字但发不出，`:408-409,416-417`）/`dictating`（识别中只读，`:481`）/`readOnly`（整条被 ReadOnlyBar 取代）。 |
| 7 | 布局占位与展开形态 | 参与布局流：`_ChatLayout` 先排输入条、再在同一次布局里排列表下沿/遮罩/斜杠面板（`ui/chat/chat_screen.dart:2610-2666`）；键盘弹起由 `Scaffold.resizeToAvoidBottomInset:true` + 布局 delegate 同帧处理（`:1871`）。 |
| 8 | 滚动语义 | 编辑器内部多行文本在 6 行后自身滚动（Flutter TextField 默认）；到边不外传（不是 Scrollable 手势竞争对象）。 |
| 9 | 动效 | 开合：`_openController` + `CurvedAnimation` 280 ms（`ui/chat/composer.dart:191-199,571-601`）；行数变化 `SmoothSize` 160 ms（`:595-600`）。 |
| 10 | 边界态 | 长文本 → 6 行后内滚；大字号 → 行高按 `textScaler`、可用行数按「键盘上方 40% 高」降到 2–6（`:158-169`）；横屏 + 键盘 → 行数自动降、顶栏让位（`ui/chat/chat_screen.dart:1841-1859`）；离线 → hint「连接后才能发送」（`:416-417`）。 |
| 11 | 无障碍 | `TextField` 自带文本编辑语义；聚焦顺序在 header 键之后；键盘类型 = 多行。 |
| 12 | 依据 | E0 `ui/chat/composer.dart:28-71,144-169,175-199,383-441,470-506,532-606,640-706`；E0 `ui/chat/chat_screen.dart:1841-1859,1871,2263-2338,2498-2559,2610-2666`。 |
| 13 | 反证 + 验收 | **反证**：若输入条用固定 1 行 + 内部横滚，多行提示词在手机上无法校对；若不按键盘高度自适应行数，横屏打字时聊天区会被挤成一条缝（dsh-Go 因此把行数压到 2–6）。**验收** GO-20：真机 6 行内跟手长高、键盘弹起列表不遮最后一条、横屏打字仍见消息。 |

#### `composer.send-stop` — 发送/停止/麦克风四态键 + 长按发送模式　（findings 7）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.send-stop`。所属：输入区面板→主键。同屏 1 个，随状态换图形：发送 / 停止生成 / 麦克风 / 停止听写（`ui/chat/composer.dart:432-441,950-955`）。 |
| 2 | 网格与定位 | 36×36 圆键（`_keySize=36`，`ui/chat/composer.dart:175,976-979`），与胶囊同心（`:33-35`）；图标 20（停止 18，`:950-955`）；按下缩到 0.9（`:972-975`）。 |
| 3 | 命中区 | 命中区 = 36×36 本身（`GestureDetector` + `AnimatedContainer`，`ui/chat/composer.dart:960-1004`）——**低于 44/48**，靠胶囊 6 的留白与相邻键 8 的间距隔离（`:175-176,537-542`）。 |
| 4 | 手势 | 单击 = 按当前 mode 执行（发送/停止/开麦/停听，`ui/chat/composer.dart:519-530`）；**长按 = 发送模式 sheet**（仅 send 态可长按，`:518` → `ui/chat/chat_screen.dart:930-990`）；长按有中触感（`:962-968`）。 |
| 5 | 行为 | 发送：`_send()`（空文本+无附件时禁用，`ui/chat/composer.dart:408-410`）；停止：`_stop()`（stopping 后键转 busy 转圈，`:442-446,994-999`）；长按 → 三选一：插话 steer / 停下先处理 interrupt+send / 排队 queue（`ui/chat/chat_screen.dart:956-969`），选后校验草稿/连接代次再发送（`:976-989`）。 |
| 6 | 视觉状态 | `send`：主色实心（可配 tonal/outlined/ink，`ui/chat/composer.dart:915-948`）；`stop`：主色停止图形 + 转圈；`mic`：淡底圆（`onSurface 6%`）；`disabled`：12% 底 + 38% 图标；`pressed`：scale 0.9。 |
| 7 | 布局占位与展开形态 | 参与布局流（在输入条右端）；无展开。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 按压 90 ms 弹簧（`ui/theme/motion.dart:20,64`）；四态切换 `AnimatedSwitcher` 160 ms（`ui/chat/composer.dart:988-993`）；流体档发送图标的飞机飞出（`_FluidKeySwap`，`:1009-1027`）。 |
| 10 | 边界态 | 未连接/未就绪/提交中 → 禁用（`:408-411,442-446`）；等待审批/提问时停止键退让、显示为安静键（`quiet`，`:511`）；空草稿+无附件 → 不显示发送只有麦克风（若有语音）；`sendWhenEmpty`（酒馆）时空框也可发（`:144-147,410`）；大字号 → 键不变（固定 36），胶囊变高。 |
| 11 | 无障碍 | `Semantics(button, enabled, label=发送/停止生成/语音输入/停止听写/停止中)`（`ui/chat/composer.dart:956-959`）。 |
| 12 | 依据 | E0 `ui/chat/composer.dart:175-176,403-446,508-531,900-1004,1009-1027`；E0 `ui/chat/chat_screen.dart:930-990`；E0 `ui/theme/motion.dart:20,64`。 |
| 13 | 反证 + 验收 | **反证**：若发送/停止分两个键，手机上输入条要塞 3 颗键、正文更窄；若运行中发送键仍叫「发送」，用户不知道点下去是插话还是排队（dsh-Go 用长按出三选一）。**验收** GO-21：真机空态显麦克风、运行中显停止、长按发送出三选一 sheet。 |

#### `composer.attach` — ＋ 附件键 / 附件条 / 移除　（findings 15）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.attach`。所属：输入区面板→附件入口。同屏 1 个 ＋ 键；已选未发附件在 tray 里横排（`ui/chat/widgets/attachments.dart:19-24,72-100`）；附件为空时 tray 不出现。 |
| 2 | 网格与定位 | ＋ = 36×36 圆键（类注释写「40dp」但代码 36，`ui/chat/widgets/attachments.dart:26,54-66`），`InkResponse radius:20`（`:46-53`）；tray 高 56 + overhang、横向 `ListView` 间距 8（`:72-100`）；tile 上可叠「首帧/尾帧」小标签（`:79-83`）。 |
| 3 | 命中区 | ＋ 命中 36×36 + `InkResponse` 水波半径 20（比视觉略大，`:46-53`）；tile 右上 × 独立命中；tile 整块可点预览。 |
| 4 | 手势 | 单击 ＋ = 打开附件面板（`showAttachMenu` → `showPlusPanel`，`ui/chat/widgets/attachments.dart:19-24`）；单击 tile = 预览；单击 × = 移除（`:100-160`）；长按 tile = 图片动作菜单（保存/分享，见 `chat.msg.image`）。 |
| 5 | 行为 | 选源：相册 / 拍照 / 文件（`ui/chat/widgets/plus_panel.dart:141-143`）；选中后进 tray、`hasAttachments` 让发送可用（`ui/chat/chat_screen.dart:2307`）；移除后 tray 收起（`ExpandReveal`），草稿保留；发送成功后清空（状态层）。 |
| 6 | 视觉状态 | ＋ `default`：`onSurface 6%` 底、图标 22；`disabled`（未连接）：38% 图标（`ui/chat/widgets/attachments.dart:37-65`）；tile：缩略图 + 圆角 + × 角标。 |
| 7 | 布局占位与展开形态 | tray 在输入条上方展开/收起（`ui/chat/composer.dart:583-588`），占高 56；展开时玻璃长高、文字行不跳（注释 `:569-570`）。 |
| 8 | 滚动语义 | tray 横向滚（`ListView.separated horizontal`，`ui/chat/widgets/attachments.dart:90-100`）；纵向不滚。 |
| 9 | 动效 | tray 进出 `ExpandReveal`（280 ms）；＋ 按压水波 + 轻触感（`:49-51`）。 |
| 10 | 边界态 | 无可选源（远程宿主暂存不了文件）→ 面板只剩相册/拍照（`ui/chat/widgets/attachments.dart:19-24`）；数量上限由状态层；大字号 → tray 高不变、标签换行；横屏 → 面板按 sheet 适配。 |
| 11 | 无障碍 | ＋ 有 `Semantics(button,label=添加附件)` + Tooltip（`attachments.dart:40-45`）；tile 有「移除附件」语义。 |
| 12 | 依据 | E0 `ui/chat/widgets/attachments.dart:19-24,26-70,72-160,211,272-274,323-332,575-576`；E0 `ui/chat/widgets/plus_panel.dart:89-143,164-260`；E0 `ui/chat/chat_screen.dart:2307-2331`。 |
| 13 | 反证 + 验收 | **反证**：若＋ 与发送键同尺寸同色并列，用户误发/误开面板；若附件不进 tray 而直接发送，用户失去发送前确认的机会。**验收** GO-22：真机选 3 张图进 tray、可单张移除、发送后清空。 |

#### `composer.mention` — @ 提及选择器　（findings 6）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.mention`。所属：输入区面板→@ 选择器。出现条件：草稿中光标处是 `@` 词（`ui/chat/mention/mention_picker.dart:91,177-178`）；结果为空时不出现。 |
| 2 | 网格与定位 | 浮在输入条上方的面板（与斜杠面板同槽，`ui/chat/chat_screen.dart:2025-2074`）；宽 = 屏宽 − 24、离输入条上沿 6（`:2621-2623,2650-2652`）；列表 `maxHeight` 约束（`ui/chat/mention/mention_picker.dart:35,98`）。 |
| 3 | 命中区 | 每行整行可点（`minimumSize`，`mention_picker.dart:186`）；行高 ≥44。 |
| 4 | 手势 | 单击行 = 插入 @ 词；方向键/Enter/Esc 由键盘事件处理（`ui/chat/chat_screen.dart:248-258` → `_mentions.handleKey`）；点面板外 = 关闭（`TextFieldTapRegion`，`:2061-2064`）。 |
| 5 | 行为 | 选中 → 把 mention 写进输入框（token 化）、焦点回输入框；关闭不改草稿；列表随输入过滤。 |
| 6 | 视觉状态 | `default` 玻璃面板；行 `pressed` 水波；选中项主色。 |
| 7 | 布局占位与展开形态 | 浮层不占位（overlay 槽，`ui/chat/chat_screen.dart:2025-2074`）；键盘弹起时随输入条上移。 |
| 8 | 滚动语义 | 列表内滚（`ListView`，`mention_picker.dart:99`）；到边不转交（面板独立）。 |
| 9 | 动效 | 进出 `AnimatedSwitcher` 160 ms + 0.96→1 缩放（`ui/chat/chat_screen.dart:2045-2056`）。 |
| 10 | 边界态 | 无匹配 → 面板关闭；多会话时列出会话与最近；大字号 → 行高增；横屏 → 面板仍浮在输入条上方（高度受限）。 |
| 11 | 无障碍 | `Semantics(button)` 每行；`@` 触发时读屏播报面板出现。 |
| 12 | 依据 | E0 `ui/chat/mention/mention_picker.dart:35,91,98-99,177-186,234-253`；E0 `ui/chat/chat_screen.dart:248-258,2025-2074,2621-2652`。 |
| 13 | 反证 + 验收 | **反证**：若 @ 只在提交后解析，用户看不到能提谁、也改不了引用目标；若面板做成全屏页，输入上下文中断。**验收** GO-24：真机输入 @ 出会话列表、点选后 token 插入、Esc 关闭。 |

#### `composer.slash` — 斜杠命令面板　（findings 4）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.slash`。所属：输入区面板→斜杠命令。出现条件：草稿以 `/` 开头且能解析出命令前缀（`ui/chat/slash_palette.dart:146,225-234`）；Esc 或点外面关闭并记住本次草稿不再弹（`ui/chat/chat_screen.dart:218-238`）。 |
| 2 | 网格与定位 | 同 @ 面板槽：宽 = 屏宽 − 24、离输入条上沿 6（`ui/chat/chat_screen.dart:2621-2623,2650-2652`）；列表 `maxHeight:137`（`ui/chat/slash_palette.dart:137-138`），行高 ≥ `minimumSize`（`:242`）。 |
| 3 | 命中区 | 每行整行可点；行高按 `minimumSize`（`slash_palette.dart:242`）。 |
| 4 | 手势 | 单击行 = 执行/填入命令；方向键 + Enter 由 `_composerKey` 处理；Esc 关闭（`ui/chat/chat_screen.dart:248-258`）；点外关闭（`:2061-2064`）。 |
| 5 | 行为 | 选中 → 命令进输入框或直接执行（按命令类型）；命令执行结果作为 `CommandNoticeRow` 插进消息流（`ui/chat/chat_rows.dart:167-175,412-426`）。 |
| 6 | 视觉状态 | `default` 玻璃面板；行 `pressed`；建议文案三级色。 |
| 7 | 布局占位与展开形态 | 浮层不占位；随输入条上移。 |
| 8 | 滚动语义 | 列表内滚（`ListView`），到边不转交。 |
| 9 | 动效 | 进出 160 ms 淡入 + 缩放（`ui/chat/chat_screen.dart:2045-2056`）。 |
| 10 | 边界态 | 无匹配命令 → 面板不出现；命令不可用（未连接）→ 不出现（`:2034`）；大字号 → 行高增。 |
| 11 | 无障碍 | 行 `Semantics(button)` + 命令名；面板出现时读屏播报。 |
| 12 | 依据 | E0 `ui/chat/slash_palette.dart:137-146,225-242`；E0 `ui/chat/chat_screen.dart:218-258,2025-2074,2621-2652`；E0 `ui/chat/chat_rows.dart:167-175,412-426`。 |
| 13 | 反证 + 验收 | **反证**：若斜杠命令只在提交后报错，用户不知道可用命令集；若面板与 @ 面板同时出现，两个浮层会重叠。**验收** GO-25：真机输入 / 出命令面板、选中后插入、Esc 关闭且不再自动弹。 |

#### `composer.voice` — 语音输入（听写 / 识别中 / 音量条）　（findings 2）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.voice`。所属：输入区面板→语音听写。出现条件：`VoiceInputController.available`（已选语音模型，`ui/chat/composer.dart:407`）；不可用则不显示麦克风。 |
| 2 | 网格与定位 | 麦克风占据发送键位置（`ui/chat/composer.dart:437-438`）；听写时 ＋ 换成 ✕、发送键换成停止键、中间显示识别结果与音量条（`VoiceWaveform`，`:425-430`）；四颗键位置尺寸不变（注释 `:36-39`）。 |
| 3 | 命中区 | 键 36×36（同发送键）；✕ 36；音量条非交互。 |
| 4 | 手势 | 单击麦克风 = 开始听写（先 unfocus 收键盘，`ui/chat/composer.dart:369-375`）；单击停止 = 结束并转写；单击 ✕ = 取消听写。 |
| 5 | 行为 | 听写 → 文本写入输入框（`readOnly: true` 防手动改，`:481`）；转写中键转圈；失败 → SnackBar（可跳语音设置，`:347-367`）。 |
| 6 | 视觉状态 | `mic` 淡底圆；`listening` 主色 + 输出波形（`VoiceBeam` 光效，`:610-632`）；`transcribing` 转圈。 |
| 7 | 布局占位与展开形态 | 参与布局流（同键位替换，不改变输入条高度）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 听写光效呼吸（`AppMotion.breath` 2.4 s，`ui/theme/motion.dart:45`）；键态切换 160 ms。 |
| 10 | 边界态 | 无麦克风权限/无模型 → 不显示麦克风；引擎不支持 → 走系统语音；大字号 → 波形区挤占文字但键位不变；横屏 → 同布局。 |
| 11 | 无障碍 | 麦克风有「语音输入」label；听写中读屏播报「正在听写」。 |
| 12 | 依据 | E0 `ui/chat/composer.dart:36-39,347-375,403-446,481,610-632`；E0 `ui/chat/voice/voice_widgets.dart:25-26,86-87,111-112`；E0 `ui/theme/motion.dart:45`。 |
| 13 | 反证 + 验收 | **反证**：若听写时键盘还开着，识别结果被键盘挡住看不到；若四颗键在听写时换位置/换尺寸，用户手指会按错（dsh-Go 明确保持同位同尺寸）。**验收** GO-26：真机开听写、键盘收起、识别文本出现在输入框、✕ 可取消。 |

### 2.3 导入媒体/附件（面板 ID：`attachment`；归位：myco `attachment`）

#### `attachment.panel` — 附件面板（相册/拍照/文件 + 生成模式 + 本对话权限/手机操控入口）　（findings 19）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `attachment.panel`。所属：附件面板。出现条件：点 ＋（`ui/chat/widgets/plus_panel.dart:89-99`）；可选源固定 3 项 + 插件工具格（`_PluginTools`）+「本对话」卡 +「手机操控」卡（`:141-175`）。 |
| 2 | 网格与定位 | 底部弹层；tile 是 60 彩色方块 + 名称（`PlusPanelMetrics.square`，`plus_panel.dart:217-252`），4 个一行；面板内还可嵌 `SessionPermissionPage`（四档权限）与生成 sheet（`ui/chat/media/generate_sheet.dart`）。 |
| 3 | 命中区 | tile 整块可点（`plus_panel.dart:217-260`）；权限行整行可点；生成 sheet 按钮 44~48。 |
| 4 | 手势 | 单击 tile = 选源并关面板；「生成」= 进生成模式（`generate_mode.dart`）；「语音通话」= 进通话页；「本对话」= 权限/计划页；手机操控卡 = 确认方式页。 |
| 5 | 行为 | 相册 → `ImagePicker`（`attachments.dart` 内 import `image_picker`，全仓 6 处）；拍照 → camera；文件 → `file_picker`（13 处）；生成模式 → 打开生成 sheet 并把草稿标记为生成参数（`ui/chat/media/generate_mode.dart:78-115`）。 |
| 6 | 视觉状态 | tile：彩色方块 + 图标 26 + 名称两行；`pressed` 水波；选中项有主色勾。 |
| 7 | 布局占位与展开形态 | sheet-overlay（G-03 C），不占位；高度按内容，超出内滚。 |
| 8 | 滚动语义 | 面板内容少（≤1 屏）不滚；插件工具多时 `ListView`（`plus_panel.dart:104-120`）；到边不转交。 |
| 9 | 动效 | 弹层进/出 380/260 ms（`ui/theme/motion.dart:35-36`）；权限页切换左右滑（`_SessionPermissionPage`）。 |
| 10 | 边界态 | 插件无工具 → 工具格不渲染（`plus_panel.dart:178-207`）；无会话（新对话）→ 不展示「本对话」卡（`:164`）；介质能力未就绪 → 「生成」不出现（`ui/chat/chat_screen.dart:1809-1825`）；大字号 → tile 名称换行；横屏 → sheet 限宽。 |
| 11 | 无障碍 | 每个 tile 有 label；生成/通话有独立语义。 |
| 12 | 依据 | E0 `ui/chat/widgets/plus_panel.dart:47-175,177-260,262-370,371-545`；E0 `ui/chat/media/generate_sheet.dart:36-41,162,306`；E0 `ui/chat/media/generate_mode.dart:78-115`；E0 `ui/chat/chat_screen.dart:1809-1825,2307`；E0 `ui/chat/widgets/attachments.dart:6,19-24`。 |
| 13 | 反证 + 验收 | **反证**：若附件只能从系统文件选择器进（无拍照/相册快捷），手机上发截图要多步；若把所有入口塞进一级菜单，一个面板会出现 8+ 项、误触率上升（dsh-Go 用 tile 网格 + 分组卡）。**验收** GO-23：真机＋面板三源可用、生成入口只在模型支持时出现。 |

### 2.4 模型选择（面板 ID：`model`；归位：myco `model`）

#### `model.sheet` — 模型 / 思考强度 / Agent 预设选择面板　（findings 16）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.sheet`。所属：模型面板（sheet).出现条件：点顶栏模型 chip（`ui/chat/model_sheet.dart` 由 `ui/chat/chat_screen.dart:2017-2021,2310-2315` 打开）或输入条下方的 `ComposerModelPill`（`:2310-2315`）。 |
| 2 | 网格与定位 | 底部弹层；顶部模型列表（`ListView`，`ui/chat/model_sheet.dart:455-495`）+ 分组标题 + 行；行 `minimumSize`（`:94,168`）；思考强度/预设分为独立小节。 |
| 3 | 命中区 | 每行整行可点（44+）；行尾可能带「当前/锁定」标记；关闭靠拖拽/遮罩。 |
| 4 | 手势 | 单击行 = 选中并关闭；单击分组 = 展开/收起；长按模型 = 详情（若有）；无滑动。 |
| 5 | 行为 | 选中模型 → 写会话默认（必要时同步到 host，`ui/chat/chat_screen.dart:1835` 有保存失败提示）；思考强度 → 改本轮参数；预设 → 改 agent preset；选择幂等。 |
| 6 | 视觉状态 | `default` 行；`selected` 主色勾/底；`disabled`（模型不可用/无凭据）灰；`busy` 转圈。 |
| 7 | 布局占位与展开形态 | sheet-overlay（G-03 C）；不占正文；高度按内容、超屏内滚。 |
| 8 | 滚动语义 | 面板内列表滚；到边不转交（独立路由）。 |
| 9 | 动效 | 弹层进/出 380/260 ms；选中行淡入标记。 |
| 10 | 边界态 | 无模型（未配 provider）→ 空态 + 去配置入口；模型超多 → 内滚；保存失败 → 顶栏警告（`ui/chat/chat_screen.dart:1835`）；大字号 → 行高增；横屏 → 限宽。 |
| 11 | 无障碍 | 行 `Semantics(button, selected)` + 模型名/提供方；弹层有标题。 |
| 12 | 依据 | E0 `ui/chat/model_sheet.dart:87-94,158-168,402,455-495,555`；E0 `ui/chat/chat_screen.dart:2015-2021,2310-2315,1835`。 |
| 13 | 反证 + 验收 | **反证**：若模型只能去设置页改，用户想换模型要先离开对话（丢失上下文）；若放顶栏常驻下拉，窄屏标题与模型会互相挤压（dsh-Go 让窄屏在输入条下方展示）。**验收** GO-27：真机点 chip 换模型、选中标记即时生效、无凭据给配置入口。 |

### 2.5 审批/提问（面板 ID：`approval`；归位：myco `approval`）

#### `approval.card` — 审批卡（批准/拒绝/本对话都允许）+ 权限胶囊　（findings 13）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `approval.card`。所属：审批面板（内嵌聊天流）。出现条件：`ToolChatItem.pendingApproval` 非空且未被本机自动放行（`ui/chat/widgets/tool_row.dart:133-136`）；或游离审批（`ui/chat/chat_rows.dart:90-96,485-490`）。同屏可 0..N。另有两枚胶囊贴在输入条上沿：`AutoApprovePill`（自动允许中）与 `PermissionPill`（完全权限警告）（`ui/chat/widgets/auto_approve_pill.dart`、`permission_pill.dart`）。 |
| 2 | 网格与定位 | 审批卡 = 工具行下的卡片：标题 + 参数摘要 + 按钮组；按钮 `minimumSize` 传入 + `shrinkWrap`（`ui/chat/widgets/approval_card.dart:149-184`）；参数区高 219 封顶 + 内滚列表 220（`:219-220`）。胶囊高度与输入条上沿同行、圆角胶囊。 |
| 3 | 命中区 | 按钮 ≥ `button`（工具行传入，多为 44~48）× 内容宽，`tapTargetSize: shrinkWrap`（`approval_card.dart:152-165`）；卡片内参数列表可滚；胶囊整枚可点（`auto_approve_pill.dart:31-38`、`permission_pill.dart`）。 |
| 4 | 手势 | 单击批准/拒绝 = 提交审批响应（`ApprovalResponder`，`tool_row.dart:65-77`）；「本对话都允许」= 自动放行后续同类（`onAllowAll`）；胶囊：点「自动允许中」= 关闭自动允许（`ui/chat/chat_screen.dart:2367-2368`），点「完全权限」= 打开权限 sheet（`:2373-2377`）。 |
| 5 | 行为 | 批准 → 工具继续执行；拒绝 → 工具 `denied` 状态；允许全部 → 本会话后续同类不再询问；所有动作幂等（重复点击被 `pending` 状态挡住）。 |
| 6 | 视觉状态 | `default`：卡片在流里；`pending`：按钮可点；`submitting`：按钮转圈；`auto`：卡片不出现，行尾 spinner（`tool_row.dart:86-88,133-136`）；`warning`：完全权限胶囊用警告色。 |
| 7 | 布局占位与展开形态 | 卡片参与布局流，展开在工具行下方；胶囊 overlay 在输入条上沿，不占正文高度（`ui/chat/chat_screen.dart:2356-2389`）。 |
| 8 | 滚动语义 | 卡片内参数列表可滚（`approval_card.dart:219-220`，封顶）；到边转交外层会话（同 `OverscrollForwarder` 机制）。 |
| 9 | 动效 | 按钮态切换 160 ms；胶囊进出淡入。 |
| 10 | 边界态 | 无待审批 → 不渲染；超长参数 → 卡内滚动；离线/提交失败 → 卡上错误文案并保持可重试；大字号 → 按钮随文字长高；横屏同布局。 |
| 11 | 无障碍 | 按钮有 `Semantics(button)` + 明确文案；卡片标题播报工具名与动作。 |
| 12 | 依据 | E0 `ui/chat/widgets/approval_card.dart:149-184,219-220`；E0 `ui/chat/widgets/tool_row.dart:65-77,86-88,133-136`；E0 `ui/chat/widgets/auto_approve_pill.dart:31-38`；E0 `ui/chat/widgets/permission_pill.dart`；E0 `ui/chat/chat_screen.dart:2356-2389`；E0 `ui/chat/chat_rows.dart:90-96,485-490`。 |
| 13 | 反证 + 验收 | **反证**：若审批只能去设置页处理，生成中的回合会卡住且用户找不到入口；若「本对话都允许」不给撤销入口，用户被一次误点永久放行。**验收** GO-15：真机审批卡三键可用、拒绝后工具行变 denied、胶囊可关自动允许。 |

#### `approval.question` — 提问卡（选项 / 自由文本 / 回答后卡片）　（findings 9）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `approval.question`。所属：审批面板（内嵌聊天流）。出现条件：`UserQuestionRequest` 存在（`ui/chat/chat_rows.dart:98-103,491-493`）；回答后由 `QuestionReplyCard` 显示已答内容。同屏 0..N。 |
| 2 | 网格与定位 | 卡片 = 问题标题 + 选项按钮（`minimumSize` + `shrinkWrap`，`ui/chat/widgets/question_card.dart:152-207`）+ 自由文本行（320-317）。选项按钮上下排列、间距由卡片 padding 给。 |
| 3 | 命中区 | 选项按钮 ≥ 传入尺寸（`question_card.dart:152-165,203-207`）；自由文本输入框；提交键 36（`:316-317`）。 |
| 4 | 手势 | 单击选项 = 直接作答（单/多选）；多选需「提交」；自由文本 = 输入后提交；作答后卡片变只读回执（`question_reply_card.dart`）。 |
| 5 | 行为 | 作答 → 回合继续；已答卡片保留在流里；拒绝/超时由上游给（未见独立超时 UI）。 |
| 6 | 视觉状态 | `default` 可答；`selected` 主色；`submitted` 只读；`busy` 转圈。 |
| 7 | 布局占位与展开形态 | 参与布局流；无展开。 |
| 8 | 滚动语义 | 卡片不滚（选项随内容长高）；长选项换行；随会话滚。 |
| 9 | 动效 | 选项选中 160 ms；提交后卡片淡出为回执。 |
| 10 | 边界态 | 无问题 → 不渲染；自由文本为空 → 提交禁用；大字号 → 按钮长高；横屏 → 卡片宽随阅读列。 |
| 11 | 无障碍 | 选项 `Semantics(selected)`；输入框有 label。 |
| 12 | 依据 | E0 `ui/chat/widgets/question_card.dart:152-165,184-207,239-317`；E0 `ui/chat/chat_rows.dart:98-103,491-493`；E0 `ui/chat/widgets/question_reply_card.dart`。 |
| 13 | 反证 + 验收 | **反证**：若提问只能靠输入框回答，选择题变成打字负担且易答错；若作答后卡片消失，用户回看不到自己答了什么。**验收** GO-16：真机点选项即答、作答后卡片变回执、输入法弹出时选项不被遮。 |

### 2.6 执行轨迹/用量（面板 ID：`trajectory`；归位：myco `trajectory`）

#### `trajectory.sheet` — 执行轨迹 / 用量 / 产物预览 sheet　（findings 19）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `trajectory.sheet`。所属：轨迹面板（sheet-overlay）。出现条件：点活动条（运行中轨迹，`ui/chat/chat_screen.dart:2416-2421`）、点上下文环（用量，`:2332-2337`）、点产物卡（预览，`ui/chat/artifact_preview.dart`）。 |
| 2 | 网格与定位 | 底部弹层：`showAppSheet` 统一入口（`ui/common/app_sheet.dart:45-50`），内容左右留白 `kSheetGutter=20`（`:25-29`）；轨迹列表行高按内容、`maxHeight` 限制（`ui/chat/trajectory_sheet.dart:48-83`）。 |
| 3 | 命中区 | 每行可点（跳步骤详情）；关闭键/拖拽把手（`showDragHandle:true` 全局主题，`ui/theme/app_theme.dart:389`）；行命中整行。 |
| 4 | 手势 | 单击行 = 打开步骤；下拉/点遮罩 = 关闭；拖动把手 = 收起。 |
| 5 | 行为 | 轨迹 sheet：列出本轮步骤（工具/思考/耗时），点行进步骤详情；用量 sheet：token/费用分项；产物预览：图片/文件预览。 |
| 6 | 视觉状态 | `default` 玻璃弹层（`glass:true` 时真模糊，否则不透明，`app_sheet.dart:45-50`）；行 `pressed` 水波纹。 |
| 7 | 布局占位与展开形态 | sheet-overlay（G-03 C），不占正文位置；高度按内容，超过则内滚。 |
| 8 | 滚动语义 | sheet 内列表滚；到边不转交（弹层是独立路由）；背景遮罩拦截外层滚动。 |
| 9 | 动效 | 弹层进/出 380/260 ms（`ui/theme/motion.dart:35-36`）；拖动跟手。 |
| 10 | 边界态 | 空轨迹 → 空态；超长 → 内滚；失败 → 错误行；大字号 → 行高增；横屏：`showAppSheet` 按 `WindowClass` 调整（`ui/common/app_sheet.dart:12` import + 用法）。 |
| 11 | 无障碍 | 行 `Semantics(button)`；关闭有「关闭」标签。 |
| 12 | 依据 | E0 `ui/chat/trajectory_sheet.dart:29,48-83,462-472,620-621`；E0 `ui/chat/usage_sheet.dart:148`；E0 `ui/chat/artifact_preview.dart:584,1218,1585,1767`；E0 `ui/common/app_sheet.dart:25-50`；E0 `ui/theme/app_theme.dart:378-390`。 |
| 13 | 反证 + 验收 | **反证**：若把轨迹做成长驻面板，手机上会挤掉正文；若用全屏页，用户失去"边看回复边看轨迹"的上下文。**验收** GO-17：真机点活动条出轨迹、点行看步骤、下滑关闭回到原阅读位置。 |

### 2.7 会话导航/侧栏（面板 ID：`sidebar`；归位：myco `sidebar`）

#### `sidebar.drawer` — 会话抽屉（品牌/搜索/工作区/筛选/会话行/多选/身份栏）　（findings 86）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.drawer`。所属：侧栏面板→会话抽屉。出现条件：手机上从左侧拉出或点 ≡（`ui/shell/home_shell.dart:259-271,423-441`）；宽屏 ≥840dp 常驻为 sidebar（`ui/shell/adaptive/adaptive_layout.dart:51-61,172-181`）。 |
| 2 | 网格与定位 | 宽 = min(320 + 左侧安全区, 屏宽 − 56)（`ui/shell/home_shell.dart:135-140`）；内容左右 10（`ui/shell/sessions_drawer.dart:112-114`）；从上到下：品牌 + 模式/网页模式 + 新对话 → 安全模式横幅 → 搜索 → 工作区切换 → 状态筛选（横滚胶囊）→ 会话列表（置顶/文件夹/按天）→ 底部身份栏（host/网页版/设置）（`sessions_drawer.dart:80-84,154-199`）。 |
| 3 | 命中区 | 会话行 44 高整行可点、长按进多选（`ui/shell/drawer/session_tile.dart:51,88-118`）；新对话键、模式键、网页模式键为玻璃圆键；筛选胶囊整枚可点（`ui/shell/drawer/drawer_filter_bar.dart:53-66`）；「对话列表」圆键 36（`:82-100`）。 |
| 4 | 手势 | 单击行 = 打开会话（`SessionTile.onTap`）；**长按行 = 多选**（`onLongPress`，`session_tile.dart:39,99-100`）；抽屉边缘横滑 = 拉开/推回（`ui/shell/home_shell.dart:259-271`）；返回键先结束多选/关抽屉（`:319-333`）。 |
| 5 | 行为 | 打开会话 → 关抽屉并切换（手机上）；多选 → 顶部换选择头、底部换动作栏（`sessions_drawer.dart:154-158`）；文件夹/置顶/归档/删除在行菜单与选择动作里（`ui/shell/drawer/folder_sheets.dart`、`selection_bars.dart`、`session_deletion.dart`）。 |
| 6 | 视觉状态 | 当前会话行：主色淡底（`session_tile.dart:36-37,104-118`）；运行中：呼吸点；待确认：黄色文字标记；未读：灰点（`:71-80`）；筛选选中：胶囊主色。 |
| 7 | 布局占位与展开形态 | 抽屉是 overlay（手机）/常驻列（宽屏）；多选替换头尾两栏（`sessions_drawer.dart:154-158`）——不改变行位置（AnimatedSwitcher 原地）。 |
| 8 | 滚动语义 | 会话列表纵向滚；筛选胶囊横滚；列表底部留身份栏高度（`sessions_drawer.dart` 结构）；被拉出的抽屉在移动中关闭背景模糊（`:106-110`）。 |
| 9 | 动效 | 抽屉跟手拖拽 + 速度阈值弹簧落位（`ui/shell/home_shell.dart:199-233`）；宽度动画 340 ms `emphasized`（`:90-96`）；多选头尾切换 160 ms。 |
| 10 | 边界态 | 空会话 → 空态（`ui/common/empty_view.dart`）；离线/只读 → 只有缓存列表 + 状态（`sessions_drawer.dart:129-153` 只读 scope）；横屏短屏（height<480）→ 品牌/搜索/筛选随列表一起滚走（`:147-149`）；大字号 → 品牌锁标 `FittedBox` 缩小、新对话键收字（`:165-190`）。 |
| 11 | 无障碍 | 行 `Semantics(selected, checked, button)` + 标题/状态拼接标签（`session_tile.dart:81-95`）；筛选胶囊 `Semantics(selected,label)`（`drawer_filter_bar.dart:55-60`）。 |
| 12 | 依据 | E0 `ui/shell/home_shell.dart:90-96,135-147,199-271,319-333,423-441`；E0 `ui/shell/sessions_drawer.dart:80-114,154-199,106-110`；E0 `ui/shell/drawer/session_tile.dart:12-61,71-118`；E0 `ui/shell/drawer/drawer_filter_bar.dart:15-100`；E0 `ui/shell/adaptive/adaptive_layout.dart:51-61,172-181`。 |
| 13 | 反证 + 验收 | **反证**：若会话列表做成全屏页，手机上每次切会话都要"返回/进入"两次导航，丢掉"随时看到其它会话"的能力；若行高按 48+，320 宽抽屉一屏只看 6 条；若长按不产生多选，批量归档/删除在手机上无法完成。**验收** GO-28：真机拉抽屉、长按进多选、横屏短屏头部滚走、返回键先退多选。 |

#### `sidebar.search` — 会话搜索页（关键词 / 结果 / 筛选）　（findings 10）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.search`。所属：侧栏面板→搜索。出现条件：点抽屉搜索条或宽屏 rail 的搜索键（`ui/shell/home_shell.dart:193-197,411`）。 |
| 2 | 网格与定位 | 独立全屏页：顶部搜索框（48 高） + 结果列表；行高 44+（`ui/shell/session_search_page.dart:58-66,290-300`）；结果按会话/内容分组。 |
| 3 | 命中区 | 搜索框整行可输入；结果行整行可点；清除键 40×40（`session_search_page.dart:295-300`）；`minimumSize` 5 处。 |
| 4 | 手势 | 输入 = 实时搜索（防抖）；单击结果 = 跳到该会话对应消息；单击历史/清除 = 清空；返回 = 关闭。 |
| 5 | 行为 | 选中结果 → 关闭搜索并打开该会话（`ui/shell/home_shell.dart:193-197`）；搜索范围跟随当前 drawer scope（host）。 |
| 6 | 视觉状态 | `default`；命中词高亮；空结果 → 空态；加载中 → 进度。 |
| 7 | 布局占位与展开形态 | 全屏页（路由），覆盖聊天；不占聊天布局。 |
| 8 | 滚动语义 | 结果列表滚；到边无转交（页面根）。 |
| 9 | 动效 | 页面推入用统一过渡（`ui/theme/page_transitions.dart:15-35`）；输入即时过滤。 |
| 10 | 边界态 | 空查询 → 显示最近/提示；无结果 → 空态；离线 → 只搜本地缓存；大字号 → 行高增；横屏 → 列表更宽。 |
| 11 | 无障碍 | 搜索框有 label；结果行播报标题与命中数。 |
| 12 | 依据 | E0 `ui/shell/session_search_page.dart:58-66,290-300,373-399,433,455-461,716`；E0 `ui/shell/home_shell.dart:193-197`；E0 `ui/theme/page_transitions.dart:15-35`。 |
| 13 | 反证 + 验收 | **反证**：若搜索只在抽屉内联，输入时键盘会把结果区压到只剩两行；若结果不定位到消息，用户还要自己再找。**验收** GO-29：真机搜索关键词、点结果跳到对应会话、返回回到原界面。 |

#### `sidebar.quick-panel` — 右侧快速面板（模式/网页/设置/本会话信息）　（findings 4）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.quick-panel`。所属：侧栏面板→右抽屉。出现条件：从左向右滑（屏幕右缘往左拉）或宽屏按钮（`ui/shell/home_shell.dart:273-288,442-458`）；可用性由 `quickPanelProvider.available`/`swipe` 决定（`:309-315`）。 |
| 2 | 网格与定位 | 宽 = min(QuickPanel.width（无会话）/ conversationWidth（有会话）+ 右侧安全区, 屏宽 − 56)（`ui/shell/home_shell.dart:143-147`）；内容为入口列表 +（有会话时）该会话信息/插件（`ui/shell/quick_panel.dart`）。 |
| 3 | 命中区 | 入口行整行可点（`minimumSize`，`quick_panel.dart:133`）；面板边缘手势区在屏幕右缘。 |
| 4 | 手势 | 横滑 = 拉出/推回（`QuickPanelDragRecognizer`，`ui/shell/home_shell.dart:273-288`）；单击入口 = 打开对应页面；点遮罩/返回 = 关闭（`:321-333`）。 |
| 5 | 行为 | 入口 → 跳页（模式切换、网页版、设置、插件…）；有会话时展示会话信息与插件 entry。 |
| 6 | 视觉状态 | 玻璃/不透明按主题；`default`；`pressed` 行态。 |
| 7 | 布局占位与展开形态 | overlay 抽屉（手机）；宽屏下侧栏存在时禁用（`ui/shell/home_shell.dart:307-315`）。 |
| 8 | 滚动语义 | 面板内列表滚；不转交。 |
| 9 | 动效 | 跟手拖拽 + 弹簧（`ui/shell/home_shell.dart:199-233`）；340 ms。 |
| 10 | 边界态 | 无入口 → 空面板；横屏仍可用；大字号 → 行高增。 |
| 11 | 无障碍 | 行 `Semantics(button)`；面板打开时读屏焦点移入。 |
| 12 | 依据 | E0 `ui/shell/home_shell.dart:143-147,273-315,442-458`；E0 `ui/shell/quick_panel.dart:89-133,92,126-130`。 |
| 13 | 反证 + 验收 | **反证**：若把右侧面板入口全塞进左侧抽屉，左手持机用户需要跨屏操作；若面板与左抽屉同时可拉，两个 overlay 会互相抢手势（dsh-Go 按布局互斥，注释 `:305-315`）。**验收** GO-30：真机右缘左拉出面板、点入口跳页、宽屏下有侧栏时不可拉。 |

#### `sidebar.session-detail` — 会话详情（概览 / 插件 / 产物 / 上传）　（findings 26）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sidebar.session-detail`。所属：侧栏面板→会话详情。出现条件：点顶栏标题胶囊（`ui/chat/chat_screen.dart:2007-2009`）或宽屏右面板（`ui/session/session_detail_page.dart`）。三种呈现：手机整页 / 中屏双高度 sheet / 宽屏常驻面板（`ui/shell/adaptive/adaptive_layout.dart:225-230,75-76`）。 |
| 2 | 网格与定位 | 页面结构：标题大标题 + 概览卡 + 分 tab（概览/插件/产物）（`ui/session/session_detail_page.dart`）；sheet 版两档高度（`ui/shell/adaptive/detail_sheet.dart`）。 |
| 3 | 命中区 | tab 键、行、上传键均整块可点；`minimumSize`（`ui/session/conversation_overview.dart:119`）；`detail_sheet` 有拖拽把柄。 |
| 4 | 手势 | 单击 tab = 切换；单击行 = 进入（插件/产物/上传）；拖动把柄 = sheet 两档高度；返回 = 退出（整页）或收起（面板）。 |
| 5 | 行为 | 插件 tab 可改本会话插件（`ui/plugins/seats/session_plugins_tab.dart`）；产物 tab 可预览；上传 = 选文件进会话工作区（`ui/session/workspace_upload.dart`）。 |
| 6 | 视觉状态 | `default` 卡片；tab 选中主色下划线；sheet 玻璃。 |
| 7 | 布局占位与展开形态 | 手机 = 全屏页（G-03 C 路由）；中屏 = sheet-overlay 两档；宽屏 = 常驻右栏（不占聊天宽度以外）。 |
| 8 | 滚动语义 | 页面/sheet 内列表滚；宽屏面板独立滚；到边不转交。 |
| 9 | 动效 | 推入过渡 300 ms（`ui/theme/motion.dart:148-149`）；sheet 两档拖动跟手。 |
| 10 | 边界态 | 无会话 → 不出现；只读/远程 → 插件与上传隐藏；大字号 → 卡片换行；横屏 → 宽屏走面板。 |
| 11 | 无障碍 | tab `Semantics(selected)`；上传键有 label。 |
| 12 | 依据 | E0 `ui/session/session_detail_page.dart:430,730`；E0 `ui/session/conversation_overview.dart:69-119`；E0 `ui/session/conversation_panel.dart:72,158`；E0 `ui/session/session_artifacts.dart`；E0 `ui/session/workspace_upload.dart`；E0 `ui/shell/adaptive/adaptive_layout.dart:75-76,225-230`；E0 `ui/shell/adaptive/detail_sheet.dart`。 |
| 13 | 反证 + 验收 | **反证**：若详情只能整页打开，平板上"边看会话边改插件"被打断；若宽屏右栏可无限宽，聊天列会被压到不可读（dsh-Go 给面板 320~420 并让聊天 ≥400）。**验收** GO-31：真机手机开详情页返回、平板右栏常驻、中屏 sheet 两档可拖。 |

### 2.8 设置壳（面板 ID：`settings`；归位：myco `settings`）

#### `settings.home` — 设置首页（分组 hub + 搜索）　（findings 27）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.home`。所属：设置面板→首页。全局 1 个，从抽屉身份栏/rail 进入（`ui/shell/home_shell.dart:242-244`）。 |
| 2 | 网格与定位 | 二级页框架 `AppPage`（浮动返回键 + 大标题，`ui/shell/page_frame.dart:14-27`）；顶部搜索胶囊（`ui/common/search_capsule.dart`）；内容按「组」分块，每组一个色相（`ui/settings/settings_screen.dart:70`）；行由 `SettingsTile` 绘制。 |
| 3 | 命中区 | 整行可点（`SettingsTile` 内部 `InkWell`）；行高由 `SettingsTile`/`ListPageRow` 决定（`ui/settings/settings_widgets.dart:176-295` 的 `minimumSize`）；搜索胶囊整枚可点。 |
| 4 | 手势 | 单击行 = 进子页；单击搜索 = 进设置搜索（`ui/settings/search/settings_search_page.dart`）；返回 = 退出设置。 |
| 5 | 行为 | 行 → 子页/开关；“设置搜索”可跨子页定位（`seekSpot` 机制，`ui/shell/page_frame.dart:10`）。 |
| 6 | 视觉状态 | 行 `default`/`pressed`；组标题三级色；搜索胶囊填充场。 |
| 7 | 布局占位与展开形态 | 参与布局流（整页滚）；行不展开。 |
| 8 | 滚动语义 | 整页纵向滚（`AppPage` 的 slivers）；到边不转交（页面根）。 |
| 9 | 动效 | 二级页推入 300/360 ms（`ui/theme/motion.dart:148-152`）；大标题随滚动收起。 |
| 10 | 边界态 | 远程/只读 → 部分行隐藏（`ui/settings/settings_screen.dart:9-23` 的 provider 判断）；大字号 → 行高增；横屏 → `contentWidth` 限宽居中。 |
| 11 | 无障碍 | 行 `Semantics(button)` + 标题/副标题；搜索框有 label。 |
| 12 | 依据 | E0 `ui/settings/settings_screen.dart:1-70`；E0 `ui/settings/settings_widgets.dart:176,219,295,576`；E0 `ui/shell/page_frame.dart:14-27`；E0 `ui/settings/search/settings_search_page.dart`；E0 `ui/common/search_capsule.dart:91`。 |
| 13 | 反证 + 验收 | **反证**：若设置做成一个大平铺列表，手机上要滚 20+ 屏才找到项；若没有设置内搜索，找「拒绝动画」这类深层项只能靠记忆路径。**验收** GO-37：真机设置首页分组点击进子页、搜索直达项、返回回上一层。 |

#### `settings.notifications` — 通知设置 + 应用内通知路由（提醒条 / 后台网络）　（findings 9）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.notifications`。所属：设置→通知。`ui/settings/notification_settings.dart` 是设置页；`ui/notify/*` 是运行时的应用内提醒宿主（`chat_alert_host.dart`、`background_net_host.dart`）。 |
| 2 | 网格与定位 | 设置页为行列表；提醒宿主不绘制（只路由），后台网络提示在顶栏下一条（`ui/notify/background_net_host.dart:200-227`）带 48 高按钮（`:150,185,202`）。 |
| 3 | 命中区 | 设置行整行可点；提醒条的打开/忽略键 48 高（`background_net_host.dart:150-202`）。 |
| 4 | 手势 | 单击提醒条 = 跳该会话（`ChatAlertHost.onReveal` → `_revealChat`，`ui/shell/home_shell.dart:350,165-174`）；系统通知栏点击由原生侧分发进 lib（`MethodChannel`，全仓 70 处）。 |
| 5 | 行为 | 提醒条：把 App 拉到前台并切到对应会话；后台网络提示：前台恢复/继续；设置项：开/关通知类别。 |
| 6 | 视觉状态 | 提醒条浮在顶栏下、`status` 色；后台提示 busy 点。 |
| 7 | 布局占位与展开形态 | 提醒条 overlay，不占聊天布局（`ChatAlertHost` 绘制在 Stack 顶层，`ui/shell/home_shell.dart:346-381`）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 提醒条进出淡入/上滑（`AppMotion.quick`～`standard`）；后台提示脉冲。 |
| 10 | 边界态 | 无权限 → 设置项提示去系统开启（原生侧，lib 内未见实现）；离线 → 后台提示；大字号 → 条高增；横屏 → 条宽受限。 |
| 11 | 无障碍 | 提醒条 `Semantics(button)` + 文案；设置开关有语义。 |
| 12 | 依据 | E0 `ui/settings/notification_settings.dart`；E0 `ui/notify/chat_alert_host.dart:204-273`；E0 `ui/notify/background_net_host.dart:150-227`；E0 `ui/shell/home_shell.dart:165-174,346-381`；E0 `ui/chat/chat_screen.dart`（提醒跳转回调）。 |
| 13 | 反证 + 验收 | **反证**：若通知点击只打开 App 不定位会话，用户回 App 后还要自己找；若提醒条占一条布局高度，聊天区会突然缩短。**验收** GO-39：真机后台收到提醒 → 点进 App 直达该会话；提醒条不改变列表高度。 |

#### `settings.voice` — 语音与媒体服务（语音模型 / 识别 / 媒体服务配置）　（findings 38）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.voice`。所属：设置→语音与媒体。`voice_settings_page.dart`（语音模型/朗读/通话）；`ui/settings/media/*`（媒体生成服务地址/鉴权/详情）。 |
| 2 | 网格与定位 | 行列表 + 表单；模型卡 + 下载进度；媒体服务详情页含 URL 表单与测试键（`media_service_detail_page.dart`）。 |
| 3 | 命中区 | 行/按钮整块可点（`minimumSize` 3 处：`voice_settings_page.dart:496,697`、`media_settings_page.dart:141`）；下载/删除键独立。 |
| 4 | 手势 | 单击行 = 选模型/进详情；下载 = 长任务（进度条）；测试连接 = 请求后回显。 |
| 5 | 行为 | 选语音模型 → 听写/朗读可用；媒体服务配置 → 生成能力就绪；失败 → 行内错误文案（重试）。 |
| 6 | 视觉状态 | `default`/`selected`（当前模型）/`downloading`（进度）/`failed`（红字）。 |
| 7 | 布局占位与展开形态 | 参与布局流；不展开（详情走子页/sheet）。 |
| 8 | 滚动语义 | 页面纵向滚；到边不转交。 |
| 9 | 动效 | 下载进度呼吸（`AppMotion.breath`）；行切换淡入。 |
| 10 | 边界态 | 无网络 → 下载失败可重试；模型不兼容 → 灰；大字号 → 行高增；横屏 → 限宽。 |
| 11 | 无障碍 | 行/按钮有 label；进度播报百分比。 |
| 12 | 依据 | E0 `ui/settings/voice_settings_page.dart:79-186,496,697`；E0 `ui/settings/media/media_settings_home.dart`；E0 `ui/settings/media/media_service_detail_page.dart:384`；E0 `ui/settings/media/media_url_sheet.dart`；E0 `ui/settings/media_settings_page.dart:138-181`。 |
| 13 | 反证 + 验收 | **反证**：若语音模型只能跟随系统 TTS，中文识别质量无法保证（dsh-Go 让用户选本地/云模型）；若媒体服务配置埋在 provider 里，生成失败时用户找不到原因。**验收** GO-40：真机下载语音模型、听写可用、媒体服务配错时行内报错。 |

#### `settings.system` — 系统（诊断 / Linux 环境 / 后台保活指南 / 性能 / 隐私 / 关于 / 更新 / 安全模式恢复）　（findings 72）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.system`。所属：设置→系统。含：诊断页（导出/日志）、Linux 环境、后台运行指南、性能（刷新率/动效档）、隐私、关于、检查更新、系统访问（无障碍/凭据恢复）、安全模式恢复动作（`ui/safe_mode/*`）。 |
| 2 | 网格与定位 | 标准二级页（`AppPage`）；诊断导出卡、开关行、按钮组；更新 sheet 为弹层（`ui/update/update_sheet.dart:29`）；安全模式动作为卡片 + 主按钮（`ui/safe_mode/recovery_actions.dart:60`）。 |
| 3 | 命中区 | 行整行可点；按钮 `minimumSize` 11 处（`recovery_actions.dart:60`、`background_guide_page.dart:521,594`）；开关 `shrinkWrap`（`list_page_row.dart:214` 同款）。 |
| 4 | 手势 | 单击 = 进子页/执行诊断导出/检查更新/恢复动作；开关 = 即时生效；危险动作（卸载/重置）走确认弹层。 |
| 5 | 行为 | 诊断：打包日志/生成报告（可能外发，需人工检查）；更新：下载 APK 并安装（`update_sheet.dart:523-654`）；安全模式：重置会话/切模型/清插件等恢复路径（`safe_mode_actions.dart`）。 |
| 6 | 视觉状态 | `default`/`pressed`/`disabled`（依赖未满足）；诊断中 busy；更新下载进度。 |
| 7 | 布局占位与展开形态 | 参与布局流；日志视图单独页（`ui/engine/log_view.dart`）可滚。 |
| 8 | 滚动语义 | 页面纵向滚；日志页内滚；到边不转交。 |
| 9 | 动效 | 更新 sheet 进/出 380/260 ms；进度条动画；安全模式横幅淡入。 |
| 10 | 边界态 | 无更新 → 「已是最新」；下载失败 → 可重试；诊断含敏感信息 → 导出前提示检查；大字号 → 行高增；横屏 → 限宽。 |
| 11 | 无障碍 | 按钮/开关有语义；诊断导出前有提示文案（读屏可读）。 |
| 12 | 依据 | E0 `ui/settings/diagnostics_page.dart`；E0 `ui/settings/linux_environment_page.dart`；E0 `ui/settings/background_guide_page.dart:521,594`；E0 `ui/settings/performance_page.dart`；E0 `ui/settings/privacy_page.dart`；E0 `ui/settings/about_page.dart`；E0 `ui/settings/system_access_page.dart`；E0 `ui/settings/credential_recovery_dialog.dart`；E0 `ui/safe_mode/recovery_actions.dart:56-87`；E0 `ui/safe_mode/safe_mode_actions.dart:53-134,273`；E0 `ui/safe_mode/safe_mode_banner.dart`；E0 `ui/safe_mode/safe_mode_settings.dart`；E0 `ui/update/update_sheet.dart:29,523-654`。 |
| 13 | 反证 + 验收 | **反证**：若引擎起不来只报「启动失败」，用户只能重装（dsh-Go 做安全模式的恢复动作 + 诊断导出）；若更新强弹全屏，正在生成的回合被打断。**验收** GO-41：真机导出诊断、检查更新、引擎故障时安全模式可恢复。 |

#### `settings.features` — 功能页（定时任务 / 作业 / 权限 / 预设 / 技能 / 会话导出）　（findings 59）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.features`。所属：设置→功能（含 myco 将来可能独立的 schedule / jobs / approval / skill 面板）。`schedules_page.dart`（自动化任务）、`jobs_page.dart`、`permission_page.dart`、`agent_presets_page.dart`/`preset_editor`、`skills_page.dart`、`skill_use.dart`、`session_export_page.dart`、`all_features_page.dart`（功能总览）、`phone_skill_use.dart`。 |
| 2 | 网格与定位 | 列表页（`AppPage`）+ 详情/编辑页 + 底部弹层（时间选择等 `schedule_pickers.dart:32-101`）；行高按内容，`minimumSize` 2 处（`skill_use.dart:50-62` 弹层按钮高度）。 |
| 3 | 命中区 | 行整行可点；开关 `shrinkWrap`；新建键、立即运行键、删除键独立。 |
| 4 | 手势 | 单击任务 = 详情；新建 = 编辑器；立即运行 = 触发一次；编辑 = 表单（名字/指令/时间规则）；删除 = 确认；权限 = 四档选择；导出 = 生成文件并分享。 |
| 5 | 行为 | 定时任务 → 写 host schedule（新任务由会话模型创建，`schedules_page.dart:1-6`）；立即运行 → 触发一轮；权限 → 改会话/全局预设；导出 → 文件落盘/分享。 |
| 6 | 视觉状态 | `default`/`enabled`/`ended`（已结束灰）/`running`（转圈）/`error`。 |
| 7 | 布局占位与展开形态 | 参与布局流；行不展开；picker 为 sheet-overlay。 |
| 8 | 滚动语义 | 列表滚；编辑器滚；到边不转交。 |
| 9 | 动效 | 时间选择 sheet 进/出 380/260 ms；运行中转圈。 |
| 10 | 边界态 | 无任务 → 空态 + 新建；无权限 → 行提示；大字号 → 行高增；横屏 → 限宽。 |
| 11 | 无障碍 | 行/按钮 label；开关语义；任务状态播报。 |
| 12 | 依据 | E0 `ui/features/schedules_page.dart:1-14`；E0 `ui/features/schedule_editor/schedule_editor_page.dart:563`；E0 `ui/features/schedule_editor/schedule_pickers.dart:32-101`；E0 `ui/features/jobs_page.dart`；E0 `ui/features/permission_page.dart`；E0 `ui/features/agent_presets_page.dart:94-163`；E0 `ui/features/preset_editor/preset_editor_page.dart`；E0 `ui/features/skills_page.dart`；E0 `ui/features/skill_use.dart:50-62`；E0 `ui/features/session_export_page.dart`；E0 `ui/features/all_features_page.dart`；E0 `ui/features/phone_skill_use.dart:101`。 |
| 13 | 反证 + 验收 | **反证**：若定时任务只能在宿主 Web 里建，手机上无法管理自动化；若「立即运行」不显示运行状态，用户重复触发。**验收** GO-45：真机新建/编辑/立即运行/删除定时任务；权限四档可切换。 |

### 2.9 通用（外观）（面板 ID：`settings.general`；归位：myco `settings.general`）

#### `settings.appearance` — 外观（主题工作室 / 主题包 / 壁纸 / 启动屏 / 图标 / 界面与个性化）　（findings 105）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.appearance`。所属：设置→通用（外观）。入口：设置首页「外观」；子页含主题工作室（颜色/组件/分组/JSON 导入）、主题包、壁纸、启动屏、App 图标、图标快捷方式、界面与个性化、回复偏好、快捷面板项。 |
| 2 | 网格与定位 | 主题工作室：分组 tab + 色板网格 + 滑杆 + 预览（`ui/settings/appearance/theme_studio_*`）；壁纸编辑（`ui/wallpaper/wallpaper_editor_page.dart`）；启动屏编辑（`ui/launch/launch_editor_page.dart`）；图标选择器网格（`app_icon_picker.dart`）。 |
| 3 | 命中区 | 控件整块可点；`minimumSize` 5 处、`shrinkWrap` 5 处（census：`theme_studio_controls.dart:204,248`、`theme_studio_groups.dart:296`）；色板格/图标格为方形网格（尺寸未见统一 48 约束）。 |
| 4 | 手势 | 单击 = 选择/切换；滑杆 = 拖动；长按色板 = 可能出菜单；主题切换用圆形揭示动画（`ui/theme/theme_reveal.dart:120-140`）。 |
| 5 | 行为 | 选择 → 即时预览 + 持久化（主题包/壁纸/启动屏/图标）；导入 JSON 主题 → 校验后应用（`theme_studio_json.dart`）；壁纸 → 编辑器裁剪后设为背景。 |
| 6 | 视觉状态 | `default`/`selected`（主色圈）/`pressed`；主题切换时全树重建（`ui/theme/theme_reveal.dart`）。 |
| 7 | 布局占位与展开形态 | 参与布局流；色板网格/预览卡内部可滚；无展开（部分分组可折叠）。 |
| 8 | 滚动语义 | 页面纵向滚；色板网格横向/纵向滚；到边不转交。 |
| 9 | 动效 | 主题揭示 480 ms（`ui/theme/motion.dart:41-42`）；滑杆跟手；网格项淡入。 |
| 10 | 边界态 | 低端机 `blurEnabled` 关；壁纸透明效果可关（`ui/settings/appearance/wallpaper_section.dart`）；大字号 → 色板格换行；横屏 → 预览区变宽。 |
| 11 | 无障碍 | 每格有 label（颜色名/图标名）；滑杆有语义值。 |
| 12 | 依据 | E0 `ui/settings/appearance/appearance_page.dart`；E0 `ui/settings/appearance/theme_studio_controls.dart:204,248`；E0 `ui/settings/appearance/theme_studio_groups.dart:296,17`；E0 `ui/settings/appearance/theme_studio_colors.dart:364,497,530`；E0 `ui/settings/appearance/theme_studio_json.dart`；E0 `ui/settings/appearance/theme_packs_section.dart:157`；E0 `ui/settings/appearance/icon_shortcut_sheet.dart:53,255,324`；E0 `ui/wallpaper/wallpaper_editor_page.dart`；E0 `ui/launch/launch_editor_page.dart:203-297`；E0 `ui/settings/interface_page.dart`；E0 `ui/settings/personalization_page.dart`；E0 `ui/theme/theme_reveal.dart:120-140`。 |
| 13 | 反证 + 验收 | **反证**：若外观只有「深/浅」两档，用户无法统一圆角/玻璃/字体（dsh-Go 做了主题包与 JSON）；若主题切换不做揭示动画，整屏闪白。**验收** GO-38：真机改色板/壁纸/启动屏即时生效、切换有揭示、低端机降级可读。 |

### 2.10 模型与 provider（面板 ID：`settings.models`；归位：myco `settings.models`）

#### `settings.models` — 模型渠道 / Provider / 模型页 / 订阅　（findings 60）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.models`。所属：设置→模型。`providers_page.dart`（渠道列表）→ `provider_detail_page.dart` → `provider_form_page.dart`（增改）→ `provider_model_page.dart`（单模型）、`model_context_page.dart`（上下文）、`subscription_page/sheet`（订阅）。 |
| 2 | 网格与定位 | 标准列表页 + 表单页；表单字段行、测试连接键；模型行带上下文/开关；`minimumSize` 5 处、`shrinkWrap` 3 处（census）。 |
| 3 | 命中区 | 行整行可点；保存/测试/删除键独立；开关 `shrinkWrap` 让整行点（`provider_model_page.dart:526`）；删除走确认。 |
| 4 | 手势 | 单击行 = 进详情/编辑；保存 = 校验后写 host（失败行内报错）；测试 = 请求并回显延迟/错误；删除 = 确认后删。 |
| 5 | 行为 | 增改 provider/模型 → 顶栏模型面板即时可选；设为默认 → 新对话默认模型；订阅 → 走账号登录桥（`subscription_sheet.dart`）。 |
| 6 | 视觉状态 | `default`/`pressed`/`invalid`（表单错误红）/`busy`（保存/测试转圈）。 |
| 7 | 布局占位与展开形态 | 参与布局流；表单页可滚；不展开。 |
| 8 | 滚动语义 | 表单页纵向滚（长表单）；到边不转交。 |
| 9 | 动效 | 保存后行状态切换 160 ms；测试转圈；删除确认弹层。 |
| 10 | 边界态 | 无凭据 → 行提示去填 key；保存失败 → 顶栏/行内报错（`ui/chat/chat_screen.dart:1835` 有对应提示）；离线 → 测试失败可重试；大字号 → 表单行换行；横屏 → 限宽。 |
| 11 | 无障碍 | 表单字段有 label；错误有语义；开关有 `toggled`。 |
| 12 | 依据 | E0 `ui/providers/providers_page.dart:47-107,202-325`；E0 `ui/providers/provider_detail_page.dart`；E0 `ui/providers/provider_form_page.dart:782,876-948`；E0 `ui/providers/provider_model_page.dart:490,526`；E0 `ui/providers/model_context_page.dart:146-203`；E0 `ui/providers/provider_widgets.dart:242-270`；E0 `ui/providers/subscription_page.dart`；E0 `ui/providers/subscription_sheet.dart:31`；E0 `ui/providers/new_chat_defaults_card.dart:183-195`。 |
| 13 | 反证 + 验收 | **反证**：若模型只能改配置文件，手机上换模型不可行；若保存失败静默，用户以为已生效、下一条消息用错模型。**验收** GO-42：真机新增 provider、测试连接、设为默认后新对话生效；失败有可见错误。 |

### 2.11 agent loop / 引擎（面板 ID：`settings.agent-loop`；归位：myco `settings.agent-loop`）

#### `settings.engine` — 引擎管理（运行时 / 日志 / 重置 / 设置 schema 表单 / agent loop）　（findings 86）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.engine`。所属：设置→引擎与 agent-loop。`engine_screen.dart`（运行时状态、启动/停止、Linux 环境、重置、诊断导出）+ `engine_settings_hub.dart`（引擎设置 schema 表单、字段 sheet、序列字段、web search、subagent 设置）。 |
| 2 | 网格与定位 | 运行时卡片 + 大按钮（`engine_screen.dart:346,518-525`，圆键 `kMinInteractiveDimension` 约束 `:525-526`）；schema 表单为字段行列表（`field_row.dart`），字段编辑用底部 sheet（`field_sheets.dart:151`）。 |
| 3 | 命中区 | 按钮整块可点、`minimumSize` 13 处；字段行 `shrinkWrap`（`field_row.dart:254`、`sequence_field.dart:285`）；sheet 内 `minimumSize: SheetActions.height`（`field_sheets.dart:91-94`）。 |
| 4 | 手势 | 单击 = 启动/停止/重置/进字段编辑；开关 = 即时写；危险动作（重置/卸载）走确认。 |
| 5 | 行为 | 启动 → 本地引擎进程起来并连上；停止 → 断开；重置 → 清状态（确认后）；字段修改 → 写 host 设置（失败回滚并报错）。 |
| 6 | 视觉状态 | `default`/`busy`（启动中/停止中）/`error`（引擎失败红）/`disabled`（不可用）。 |
| 7 | 布局占位与展开形态 | 参与布局流；schema 分组可折叠（`schema_collapsed_groups.dart`）；字段编辑 sheet-overlay。 |
| 8 | 滚动语义 | 页面/表单纵向滚；到边不转交。 |
| 9 | 动效 | 启动进度呼吸（`AppMotion.breath`）；按钮 busy 转圈；字段 sheet 进/出 380/260 ms。 |
| 10 | 边界态 | 引擎缺失/版本不符 → 修复入口（`ui/engine/local_uninstall.dart`、`diagnostics_export_card.dart`）；Linux 未装 → 引导；离线 → 设置仍可改（延迟生效）；大字号 → 表单行换行；横屏 → 限宽。 |
| 11 | 无障碍 | 字段 label + 说明（schema documentation）；按钮 busy 语义；错误播报。 |
| 12 | 依据 | E0 `ui/engine/engine_screen.dart:182-240,346,518-526`；E0 `ui/engine/engine_status.dart`；E0 `ui/engine/linux_section.dart`；E0 `ui/engine/log_view.dart:38`；E0 `ui/engine/local_uninstall.dart`；E0 `ui/engine/reset_outcome.dart`；E0 `ui/engine/workspace_recovery_settings.dart:89`；E0 `ui/engine_settings/engine_settings_hub.dart`；E0 `ui/engine_settings/schema_form.dart`；E0 `ui/engine_settings/field_row.dart:254`；E0 `ui/engine_settings/field_sheets.dart:49,91-94,151,513`；E0 `ui/engine_settings/sequence_field.dart:282-285`；E0 `ui/engine_settings/web_search_page.dart`；E0 `ui/engine_settings/subagent_settings_page.dart`；E0 `ui/settings/direct_engine_settings.dart`。 |
| 13 | 反证 + 验收 | **反证**：若引擎只能装完就走、失败无恢复，手机 App 会变成"打开就报错"；若 schema 设置用自由 JSON 文本框，用户改错无法回滚。**验收** GO-43：真机启动/停止/重置引擎、改 agent-loop 字段保存生效、失败可回滚可见。 |

### 2.12 插件（面板 ID：`settings.plugins`；归位：myco `settings.plugins`）

#### `settings.plugins` — 插件（市场 / 已装 / 详情 / 配置 / 原生 seat / Cordis / ui.json 配置）　（findings 119）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.plugins`。所属：设置→插件。`plugins_screen.dart` 两个 tab（市场/已装）+ 详情 sheet + 配置页 + 原生插件（native/*）+ seat 注入（seats/*）+ Cordis 插件（`ui/cordis/*`）+ ui.json 配置状态（`ui_config/*`）。 |
| 2 | 网格与定位 | 市场/已装列表（`ListView`）+ 详情 sheet（`plugin_detail_sheet.dart`，按钮组 `minimumSize` 20+ 处）；配置页为表单（`plugin_config_page.dart`），保存条固定在底部（`plugin_config_save_bar.dart:147-156`）。 |
| 3 | 命中区 | 行/按钮整块可点；`shrinkWrap` 2 处（`installed_tab.dart:285`、`plugin_widgets.dart:180`）；配置保存条按钮 `minimumSize`。 |
| 4 | 手势 | 单击插件行 = 详情；安装/更新/卸载/启用 = 按钮；配置 = 表单；Cordis 行 = 打开 Cordis sheet（`plugins/seams/adapters/cordis_adapter.dart:59`）；ui.json 徽标可点（`ui_config/widgets/from_ui_json_badge.dart:42-47`）。 |
| 5 | 行为 | 安装 → 下载/校验/写盘（长任务 + 进度）；启用/禁用 → 即时生效或提示重启；配置保存 → 校验后写；失败行内报错。 |
| 6 | 视觉状态 | `default`/`installed`/`update-available`/`busy`/`error`；详情按钮主次分明（安装主色、卸载危险色）。 |
| 7 | 布局占位与展开形态 | 参与布局流；详情/配置为 sheet 或页；列表可滚。 |
| 8 | 滚动语义 | 列表纵向滚；配置页滚；详情 sheet 内滚；到边不转交。 |
| 9 | 动效 | 安装进度呼吸；详情 sheet 进/出 380/260 ms；保存成功对勾。 |
| 10 | 边界态 | 市场离线 → 错误 + 重试；插件不兼容/校验失败 → 详情内错误；`ui.json` 校验失败 → 徽标可点看问题列表（`ui_config_status.dart:59-69`）；大字号 → 行高增；横屏 → 限宽。 |
| 11 | 无障碍 | 行/按钮有 label；安装进度播报；ui.json 问题列表可读屏。 |
| 12 | 依据 | E0 `ui/plugins/plugins_screen.dart`；E0 `ui/plugins/plugin_detail_sheet.dart:270-707`；E0 `ui/plugins/plugin_actions.dart`；E0 `ui/plugins/installed_tab.dart:49-285`；E0 `ui/plugins/market_tab.dart:158-266`；E0 `ui/plugins/plugin_inventory_page.dart`；E0 `ui/plugins/config/plugin_config_page.dart`；E0 `ui/plugins/config/plugin_config_save_bar.dart:147-156`；E0 `ui/plugins/native/*`；E0 `ui/plugins/seats/*`；E0 `ui/cordis/cordis_page.dart`；E0 `ui/cordis/cordis_plugin_sheet.dart:171,265`；E0 `ui_config/widgets/from_ui_json_badge.dart:42-85`；E0 `ui_config/widgets/ui_config_status.dart:59-114`；E0 `plugins/seams/adapters/cordis_adapter.dart:59`；E0 `plugins/seams/adapters/schema_config_adapter.dart:84`。 |
| 13 | 反证 + 验收 | **反证**：若插件只能在宿主 Web UI 里管理，手机上装不了也停不了（dsh-Go 做了原生插件页 + ui.json 校验入口）；若安装不显示进度/失败原因，用户会反复点安装。**验收** GO-44：真机市场安装插件、详情可停用/卸载、ui.json 错误可查看。 |

### 2.13 工作区/文件（面板 ID：`workspace`；归位：myco `workspace`）

#### `workspace.browser` — 工作区 / 文件浏览 / 目录选择 / 表格　（findings 46）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `workspace.browser`。所属：工作区面板。`workspaces_page.dart`（工作区列表）、`workspace_switcher.dart`（切换 sheet）、`workspace_actions.dart`（新建/编辑/删除）、`directory_picker.dart`（目录选择）、`text_prompt.dart`（重命名/新建）、`ui/files/workspace_browser.dart`（文件树）、`table_view.dart`（表格：冻结表头/列）。 |
| 2 | 网格与定位 | 列表页 + 文件树（行高按内容）+ 表格（`table_view.dart:217-243` 两个 `NeverScrollableScrollPhysics` 的同步滚动视图实现冻结列/表头）。 |
| 3 | 命中区 | 行整行可点；长按 = 文件动作（14 处 `onLongPress`）；表格单元格可点；选择器行整行可点（`minimumSize` `table_view.dart:296`）。 |
| 4 | 手势 | 单击 = 进目录/打开文件；长按 = 菜单（重命名/删除/下载）；表格横滑 + 纵滑（两个方向分别由两个 ScrollController 同步，`table_view.dart:217-243`）。 |
| 5 | 行为 | 工作区切换 → 新会话起始目录；文件 → 预览/下载/分享；目录选择 → 写回会话设置。 |
| 6 | 视觉状态 | 行 `default`/`selected`；文件类型图标；加载中骨架；错误行红字。 |
| 7 | 布局占位与展开形态 | 参与布局流；目录树可展开/收起；预览走 sheet/页。 |
| 8 | 滚动语义 | 文件树纵向滚；表格双轴滚（冻结行列用 `NeverScrollableScrollPhysics` 的两层同步滚动，`ui/files/table_view.dart:217-243`）；到边不转交。 |
| 9 | 动效 | 目录展开 160 ms；下拉刷新（`workspace_browser.dart` 有 30 处交互含刷新）。 |
| 10 | 边界态 | 目录为空 → 空态；无权限 → 错误行；大文件 → 不预览只下载；大字号 → 行高增；横屏 → 表格更宽。 |
| 11 | 无障碍 | 行/文件有 label；目录展开有 `expanded` 语义。 |
| 12 | 依据 | E0 `ui/files/workspace_browser.dart:334-601,857`；E0 `ui/files/table_view.dart:158-243,296`；E0 `ui/workspace/workspaces_page.dart`；E0 `ui/workspace/workspace_switcher.dart:143`；E0 `ui/workspace/workspace_actions.dart:64`；E0 `ui/workspace/directory_picker.dart:44,156`；E0 `ui/workspace/text_prompt.dart`；E0 `ui/session/workspace_upload.dart`。 |
| 13 | 反证 + 验收 | **反证**：若文件浏览只在宿主 Web 里有，手机上无法完成"把文件放进会话"；若表格只允许单向滚动，宽表格在手机上永远看不到右半边（dsh-Go 用双 ScrollController 同步冻结列）。**验收** GO-46：真机切换工作区、浏览/打开文件、宽表格横滑时表头列不跑。 |

### 2.14 子代理（面板 ID：`subagent`；归位：myco `subagent`）

#### `subagent.team` — 子代理团队（头部胶囊 / 团队页 / 成员行）　（findings 9）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `subagent.team`。所属：子代理面板。头部 agents 胶囊（`ui/agents/header_agents.dart`）常驻于聊天顶栏；团队页（`ui/agents/team_page.dart`）列出子代理；子代理会话由工具行打开（`ui/chat/widgets/tool_row.dart:43-63,136-141`）。 |
| 2 | 网格与定位 | 头部胶囊在小屏收成图标（`ui/agents/header_agents.dart:115-117,219-230`）；团队页为列表（`team_page.dart:489-509` 列表 + 详情）。 |
| 3 | 命中区 | 胶囊整枚可点；成员行整行可点（`team_page.dart:152-155` `minimumSize`）；详情 sheet 内按钮独立。 |
| 4 | 手势 | 单击胶囊 = 打开团队页/sheet；单击成员 = 打开该子代理会话；长按 = 中断（若支持）。 |
| 5 | 行为 | 团队页展示地址/状态/任务；点成员 → 切到该子代理会话（只读一次性子代理不可发消息，`ui/chat/chat_screen.dart:1791-1794`）；中断由父代理完成。 |
| 6 | 视觉状态 | `default`/`running`（呼吸点）/`done`（对勾）/`failed`（红）。 |
| 7 | 布局占位与展开形态 | 参与布局流（胶囊在顶栏）；团队页为 sheet/页。 |
| 8 | 滚动语义 | 团队列表滚；到边不转交。 |
| 9 | 动效 | 胶囊出现淡入；成员状态点呼吸。 |
| 10 | 边界态 | 无子代理 → 胶囊不出现；一次性子代理 → 打开后聊天只读（`ui/chat/widgets/read_only_bar.dart` 显示）；大字号 → 胶囊收图标；横屏 → 团队页限宽。 |
| 11 | 无障碍 | 胶囊/成员行有 label + 状态；只读提示可读屏。 |
| 12 | 依据 | E0 `ui/agents/header_agents.dart:115-117,219-230`；E0 `ui/agents/agent_widgets.dart:187-264`；E0 `ui/agents/team_page.dart:152-155,343,483-509,708-719`；E0 `ui/chat/widgets/tool_row.dart:43-63,136-141`；E0 `ui/chat/chat_screen.dart:1791-1797`。 |
| 13 | 反证 + 验收 | **反证**：若子代理只在工具行里能看到，用户不知道有几个在跑；若一次性子代理会话仍可输入，消息会发进一个不会回复的会话。**验收** GO-47：真机开子代理 → 顶栏出胶囊 → 点开可切换会话、只读会话无输入条。 |

### 2.15 布局与层（面板 ID：`base.layout`；归位：myco `base.layout`）

#### `base.layout` — 布局基座（HomeShell / 自适应三栏 / 二级页框架 / 转场）　（findings 12）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout`。所属：基座→布局与层。全局 1 个 `HomeShell`（`ui/shell/home_shell.dart:74`）；每个二级页 1 个 `AppPage` 框架（`ui/shell/page_frame.dart:28`）；聊天页内部的槽布局见 `chat.list.scroll-follow`。 |
| 2 | 网格与定位 | 三栏决策：`WindowClass` <600 / 600–840 / ≥840，且**高度 <480 一律 compact**（横屏手机）（`ui/shell/adaptive/adaptive_layout.dart:20-49`）；右面板 320~420（`panelFraction 0.3`）、聊天列最小 400、侧栏 320、rail 68（`:79-109,154-181`）。 |
| 3 | 命中区 | rail 圆键 44、边距 12、间距 8（`ui/shell/adaptive/adaptive_layout.dart:80-87`）；抽屉边缘手势区整个屏幕（`HomeShell` 的 `RawGestureDetector` translucent，`ui/shell/home_shell.dart:392-400`）；二级页返回键 44 浮动玻璃（`ui/shell/page_frame.dart:14-20`）。 |
| 4 | 手势 | 横滑开/关抽屉与右面板（跟手）；返回键/系统返回先关浮层（`ui/shell/home_shell.dart:319-333`）；二级页 iOS 式推入 + 20dp 边缘返回手势 + Android 预测性返回（`ui/theme/page_transitions.dart:15-35,428-442`）。 |
| 5 | 行为 | `AdaptiveScaffold` 按列计划排列左栏/聊天/右栏（`ui/shell/adaptive/adaptive_scaffold.dart`）；`≡` 在 `overlay/expand/collapse` 三动作间切换（`adaptive_layout.dart:219-223`）；`AppPage` 大标题随滚动收起、返回键常驻（`ui/shell/page_frame.dart:14-27`）。 |
| 6 | 视觉状态 | 浮层：scrim 随进度淡化、面板位移由 `Transform`（paint-only，`ui/shell/home_shell.dart:588-604`）；二级页：大标题渐变 + 顶部渐隐（`page_frame.dart:18-20`）。 |
| 7 | 布局占位与展开形态 | 面板 overlay 不占位；宽屏三栏都参与布局；`resizeToAvoidBottomInset:false` 由聊天页自己管键盘（`ui/shell/home_shell.dart:344-345`）。 |
| 8 | 滚动语义 | 各栏独立滚；面板内滚到边不外传（独立 overlay）。 |
| 9 | 动效 | 抽屉/面板 340 ms `emphasized` + 弹簧落位（`ui/shell/home_shell.dart:90-96,220-225`）；二级页推入 300/360 ms（`ui/theme/motion.dart:148-152`）；减少动画时归零（`motion.dart:182-186`）。 |
| 10 | 边界态 | 窗口尺寸变化实时重算列（`AdaptiveLayout.resolve`）；横屏（高度<480）强制单列（`adaptive_layout.dart:36-45`）；大字号 → 页框架标题/键不缩放（只缩内容，`_ChatTextScale` 只管聊天）；平板分屏 → 按实际约束重算。 |
| 11 | 无障碍 | 浮层打开时 `ExcludeSemantics` 屏蔽底层（`ui/shell/home_shell.dart:382-384`）；返回键有 label；`AppPage` 标题语义为 header。 |
| 12 | 依据 | E0 `ui/shell/adaptive/adaptive_layout.dart:11-49,79-109,143-181,219-230`；E0 `ui/shell/home_shell.dart:74-96,135-233,259-333,382-400,588-604`；E0 `ui/shell/page_frame.dart:14-40`；E0 `ui/theme/page_transitions.dart:15-35,428-442`；E0 `ui/theme/motion.dart:148-152,182-186`。 |
| 13 | 反证 + 验收 | **反证**：若手机上沿用桌面三栏，360dp 屏会被切成三条不可读；若横屏手机按宽度判成平板（915×412），每列只剩一条缝（dsh-Go 用「最短边」判手机）；若面板位移用 `Positioned` 每帧重布局，中端机会掉帧（dsh-Go 用 paint-only）。**验收** GO-32：真机竖屏单列、横屏单列、平板三栏、二级页边缘滑动可返回。 |

### 2.16 主题 token（面板 ID：`base.theme`；归位：myco `base.theme`）

#### `base.theme` — 主题与 token（玻璃 / 形状 / 间距 / 动效 / 转场 / 视觉特效）　（findings 11）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.theme`。所属：基座→主题。全局生效；`GlassSurface` 是每个玻璃件（顶栏胶囊、输入条、回底钮、抽屉、弹层）的实现（`ui/theme/glass.dart:40-55`）。 |
| 2 | 网格与定位 | 三级玻璃：`chrome`/`sheet` 默认真模糊、`card` 不模糊；模糊件同屏 ≤3；同组不重叠的玻璃用 `grouped` 共享一次背景采样（`ui/theme/glass.dart:24-39`）；降级：`DshSurfaces.blurEnabled=false` 或系统高对比度 → 近乎不透明 `fallbackFill`（`:32-34`）。 |
| 3 | 命中区 | 玻璃件命中区由包裹的 `GlassSurface.onTap`/`Pressable` 决定；`GlassSurface` 本身不做 48 扩边；形状 token 见 `AppShape`（圆角/连续圆角）。 |
| 4 | 手势 | 单击玻璃件 = 其 `onTap`（回底钮/顶栏键/抽屉卡）；`Pressable` 提供按下 0.97 弹簧与触感（`ui/common/pressable.dart:10-30,158-170`）。 |
| 5 | 行为 | 主题切换（深浅/主题包/壁纸）→ 全树重建；`AppMotion.fluid` 灵动档只加位移/缩放（`ui/theme/motion.dart:170-175`）；系统「移除动画」→ 时长 0（`:182-186`）。 |
| 6 | 视觉状态 | `default` 玻璃/实心两套；`pressed` 缩放 0.97；`disabled` 降透明；`fluid` 档额外回弹（`ui/theme/motion.dart:80-96`）；`reduced` 档静止。 |
| 7 | 布局占位与展开形态 | 不参与业务布局（纯装饰/token）。 |
| 8 | 滚动语义 | 滚动条主题 4 宽（`ui/theme/app_theme.dart:546-551`）；玻璃件不滚。 |
| 9 | 动效 | 动效 token 表：按压 90 / 快 160 / 标准 240 / 折叠 280 / 强调 340 / 弹层 380-260 / 页面 480 / 呼吸 2400 ms（`ui/theme/motion.dart:18-45`）；弹簧落位（`:111-140,119-140`）；`ThemeReveal` 圆形揭示切换主题（`ui/theme/theme_reveal.dart:120-140`）。 |
| 10 | 边界态 | 低端机/省电：`blurEnabled=false` 走不透明；高对比度 → 同；大字号 → 主题字号 token 随 `textScaler`（页面不额外缩放）；横屏/壁纸 → `WallpaperBackdrop`。 |
| 11 | 无障碍 | 颜色不单独表意（顶栏状态点配文案，`ui/chat/chat_header.dart:27-28`）；玻璃对比度降级保证可读（`glass.dart:32-34`）。 |
| 12 | 依据 | E0 `ui/theme/glass.dart:12-80,130-140`；E0 `ui/theme/motion.dart:10-45,111-140,170-186`；E0 `ui/theme/app_theme.dart:251-340,392-400,546-551`；E0 `ui/theme/page_transitions.dart:15-35`；E0 `ui/theme/theme_reveal.dart:120-140`；E0 `ui/fx/metal/metal_fx.dart:217-220`；E0 `ui/fx/bot_avatar/bot_avatar.dart:360`。 |
| 13 | 反证 + 验收 | **反证**：若每个页面各写一套圆角/玻璃，切主题会出现"半玻璃半实心"的割裂；若玻璃不做降级，中端机滚动时整屏重采样直接掉帧（dsh-Go 注释 `glass.dart:24-34` 明确这条）。**验收** GO-33：真机切深浅/主题包/减少透明效果，界面一致且滚动不卡。 |

### 2.17 基础组件（面板 ID：`base.primitives`；归位：myco `base.primitives`）

#### `base.primitives.pressable` — 按压反馈基座（Pressable：缩放 / 弹簧 / 触感 / 滚动中不闪）　（findings 11）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.pressable`。所属：基座→基础组件。被所有可点卡片/行使用（如聊天列表行、建议卡、玻璃件）；每实例 1 个。 |
| 2 | 网格与定位 | 包在任意子组件外，不改变布局（`ScaleTransition` 只改绘制变换，`ui/common/pressable.dart:173-178`）。 |
| 3 | 命中区 | 不自带命中区：`ownsTap` 时用 `GestureDetector(behavior: opaque)`，否则 `Listener(behavior: 传入)` 只监听指针（`:179-213`）；命中区 = 子组件。 |
| 4 | 手势 | 有 `onTap`/`onLongPress` 时自己赢手势（`:198-212`）；只做反馈时**永不进手势竞技场**，只监听原始指针，移动超过 touch slop 就取消（`:159-165`）；滚动容器里按下 90 ms 才显示压感（`:101-108`）。 |
| 5 | 行为 | `_down/_up/_cancel` 驱动缩放弹簧；`onTap` 触发轻触感 + 回调（`:167-170`）；长按先 `_up()` 再回调（`:205-210`）；禁用时不动画也不回调（`:54-58`）。 |
| 6 | 视觉状态 | `default` scale 1；`pressed` 0.97（`ui/theme/motion.dart:162`）；`fluid` 松手回弹过 1（`pressable.dart:143-155`）；`reduced` 不动画（`:97`）。 |
| 7 | 布局占位与展开形态 | 不占位、不改布局。 |
| 8 | 滚动语义 | 不滚；在滚动内容里延迟显示压感避免"滚过一片都闪一下"（`:101-108`）。 |
| 9 | 动效 | 按压 90 ms 弹簧（`ui/theme/motion.dart:64`）；流体档回弹 320 ms/bounce0.45（`:80-84`）；可被滚动中断（`:131-139`）。 |
| 10 | 边界态 | 禁用：子树照常绘制（同树结构，`:173-175`）；大字号/横屏无影响。 |
| 11 | 无障碍 | `ownsTap` 时 `Semantics(button, enabled, label, onTap, onLongPress)`（`ui/common/pressable.dart:190-197`）；只做反馈时不加语义。 |
| 12 | 依据 | E0 `ui/common/pressable.dart:10-30,79-170,173-213`；E0 `ui/theme/motion.dart:20,64,80-84,162`。 |
| 13 | 反证 + 验收 | **反证**：若每个可点卡片自己写 `InkWell`，滚动时手指扫过会闪一片按压效果（dsh-Go 为此做了 90 ms 延迟 + slop 取消）；若压感不进手势竞技场之外，会与外层滚动抢手势。**验收** GO-34：真机快速滚动列表不闪按压、慢按有缩放与触感、长按不触发点击。 |

#### `base.primitives.row` — 通用行/卡片/分段/胶囊/搜索框/空态（列表基元）　（findings 29）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.row`。所属：基座→基础组件。用于设置页、列表页、详情页、分享页、论坛等一切"行/卡"。每行 1 个实例。 |
| 2 | 网格与定位 | `ListPageRow` 行高按内容（`minHeight` 145 处用到，`ui/common/list_page_row.dart:145`）；`OverviewCard` 卡片圆角 + 内边距（`overview_card.dart:191`）；`SegmentedPill` 分段；`SearchCapsule` 搜索框；`StatusChip` 状态；`TileGrid` 网格。 |
| 3 | 命中区 | 行整行可点（`ListPageRow` 的 `InkWell`）；开关行用 `SwitchTheme.materialTapTargetSize: shrinkWrap` 让整行点（`list_page_row.dart:214`、`overview_card.dart:155`）；`empty_view` 按钮 `minHeight 38/44`（`empty_view.dart:75`）。 |
| 4 | 手势 | 单击行 = 主操作；行尾 chevron/开关/动作键独立；长按部分行有菜单（9 处 `onLongPress`）；不绑滑动手势。 |
| 5 | 行为 | 行 → 打开子页/切换开关/执行动作；分段 → 切换选中；搜索框 → 输入过滤。 |
| 6 | 视觉状态 | `default`/`pressed` 水波；开关 `on/off`；分段选中主色；chip 选中主色底；`disabled` 降透明。 |
| 7 | 布局占位与展开形态 | 参与布局流；行不展开（展开用 `ExpansionTile`/`SheetGroup`，`ui/theme/app_theme.dart:352-362` 把 ExpansionTile 的框去掉）。 |
| 8 | 滚动语义 | 列表页整体滚；行不滚。 |
| 9 | 动效 | 开关切换 160 ms；分段滑块弹簧（`ui/theme/motion.dart:93-96`）；行水波。 |
| 10 | 边界态 | 空态 `EmptyView` 有图标 + 文案 + 可选动作（`empty_view.dart:72-75`）；超长标题单行截断；大字号 → 行高增；横屏 → `contentWidth` 限宽居中。 |
| 11 | 无障碍 | 行 `Semantics(button)` + 副标题；开关有 `Semantics(toggled)`；搜索框有 label。 |
| 12 | 依据 | E0 `ui/common/list_page_row.dart:145,214`；E0 `ui/common/overview_card.dart:155,191`；E0 `ui/common/empty_view.dart:72-75`；E0 `ui/common/segmented_pill.dart:137,233`；E0 `ui/common/search_capsule.dart:91`；E0 `ui/common/status_chip.dart`；E0 `ui/common/tile_grid.dart`；E0 `ui/common/breadcrumb_row.dart:67-100`；E0 `ui/theme/app_theme.dart:352-362`。 |
| 13 | 反证 + 验收 | **反证**：若每页各写行样式，切主题/大字号时会出现有的行 48、有的 40 的错位；若开关只点开关本体（约 40×22），行右侧大片空白点不动。**验收** GO-35：真机设置页整行可点开关、分段切换、大字号不截断。 |

#### `base.primitives.sheet` — 底部弹层基座（showAppSheet / 标题栏 / 风险提示 / 短提示 / 丢弃守卫）　（findings 14）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.sheet`。所属：基座→弹层。全 App 的弹层统一入口（`showAppSheet`，`ui/common/app_sheet.dart:45-58`）；带标题栏、分组、关闭键、拖拽把手、风险提示、短提示（brief notice）、未保存丢弃守卫。 |
| 2 | 网格与定位 | 内容左右留白 20（`kSheetGutter`，`ui/common/app_sheet.dart:25-29`）；标题栏高度与关闭键尺寸由 `SheetTitleBar.inset` 统一（`ui/common/sheet_title_bar.dart:60`）；全局拖拽把手 36×4（`ui/theme/app_theme.dart:386`）；弹层默认 `showDragHandle:true`（`:389`）。全仓直接调 `showModalBottomSheet` 仅 1 处，其余都走 `showAppSheet`（E0：全量 grep）。 |
| 3 | 命中区 | 关闭键/拖拽把手/行均整块可点；`SheetTitleBar` 关闭键（`sheet_title_bar.dart:60`）；`RiskNotice` 按钮 `minimumSize: fromHeight`（`risk_notice.dart:276`）且开关 `shrinkWrap`（`:329`）。 |
| 4 | 手势 | 下拉把手 = 关闭；点遮罩 = 关闭（可配 `dismissal`）；返回键 = 关闭（`PopScope`，`app_sheet.dart` 内 2 处）；风险提示需勾选后确认。 |
| 5 | 行为 | 弹层可返回选择结果（`showAppSheet<T>`）；`RiskNotice` 强制确认；`DiscardGuard` 在离开前拦截未保存改动（`ui/common/discard_guard.dart`）。 |
| 6 | 视觉状态 | 玻璃弹层（`glass:true`，背后聊天透出）/不透明弹层；遮罩随进度（`ui/theme/app_theme.dart:378-390`）；`pressed` 行态。 |
| 7 | 布局占位与展开形态 | sheet-overlay（G-03 C），不占正文；高度按内容、超屏内滚。 |
| 8 | 滚动语义 | 弹层内列表滚；到边不转交；背景遮罩拦截外层。 |
| 9 | 动效 | 进 380 / 出 260 ms（`ui/theme/motion.dart:35-36`）；拖动跟手；`SheetDismissal` 手势（`ui/common/sheet_dismissal.dart`）。 |
| 10 | 边界态 | 中屏/宽屏：`showAppSheet` 按 `WindowClass` 调整宽度/位置（`app_sheet.dart:12` import）；大字号 → 内容滚动；横屏 → 限高。 |
| 11 | 无障碍 | 标题栏 `Semantics(header)`；关闭键 label；风险提示读屏播报风险项。 |
| 12 | 依据 | E0 `ui/common/app_sheet.dart:25-58,346-388`；E0 `ui/common/sheet_title_bar.dart:60`；E0 `ui/common/risk_notice.dart:251-276,329`；E0 `ui/common/brief_notice.dart:29-31`；E0 `ui/common/ask_ai.dart:101-107`；E0 `ui/common/discard_guard.dart`；E0 `ui/theme/app_theme.dart:378-390`；E0 `ui/theme/motion.dart:35-36`。 |
| 13 | 反证 + 验收 | **反证**：若每个弹层自己 `showModalBottomSheet`，把手/留白/关闭方式不一致，用户在不同弹层上要重新学关闭手势；若风险提示不强制确认，危险操作一点就过。**验收** GO-36：真机打开 5 个不同弹层，留白/把手/关闭一致；风险弹层必须勾选。 |

### 2.18 手机操控（候选新面板）（面板 ID：`phone`；归位：myco 无）

#### `phone.control` — 手机操控（主屏 / 技能 / 系统操作 / 确认方式 / 无障碍恢复）　（findings 86）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `phone.control`。所属：手机操控面板（myco 无此概念）。出现条件：Android 真机且工具链可用（`ui/phone/phone_control_screen.dart:1-50,52-70`）；同屏 1 页。 |
| 2 | 网格与定位 | 二级页：概览卡 + 开关行 + 技能列表 + 系统操作页（`ui/phone/phone_control_screen.dart:166,868`）；行由 `ListPageRow`/`OverviewCard` 画；`hold_to_confirm.dart` 是长按确认键。 |
| 3 | 命中区 | 行整行可点；`minimumSize` 7 处（`phone_disclosure_sheet.dart:103-111`、`phone_widgets.dart:162-163` 64×32 + `shrinkWrap`）；长按确认键本身 44+。 |
| 4 | 手势 | 单击 = 开关/进子页；长按 = 危险操作确认（`hold_to_confirm.dart:133-136`）；拖拽未见。 |
| 5 | 行为 | 开关 → 申请无障碍/授权；技能 → 执行一次或排进队列（`ui/features/phone_skill_use.dart`）；系统操作（返回/Home/最近任务）→ 走 `system_actions_page.dart`；确认方式三档（每步问/任务内问/不确认，`phone_control_screen.dart:52-70`）。 |
| 6 | 视觉状态 | `default`/`on`（主色）/`restricted`（受限灰）/`busy`（执行中）/`denied`（红）。 |
| 7 | 布局占位与展开形态 | 参与布局流；无展开。 |
| 8 | 滚动语义 | 页面纵向滚；到边不转交。 |
| 9 | 动效 | 开关 160 ms；执行中呼吸；长按进度环。 |
| 10 | 边界态 | 无权限 → 引导页（`restricted_settings_guide.dart`、`a11y_restore_sheet.dart`）；系统版本不支持 → 行灰 + 原因；大字号 → 行高增；横屏 → 限宽。 |
| 11 | 无障碍 | 开关/行有 label + 状态；危险操作有读屏提示。 |
| 12 | 依据 | E0 `ui/phone/phone_control_screen.dart:1-70,156-166,868`；E0 `ui/phone/hold_to_confirm.dart:133-136`；E0 `ui/phone/phone_widgets.dart:162-163`；E0 `ui/phone/phone_disclosure_sheet.dart:103-111`；E0 `ui/phone/system_actions_page.dart`；E0 `ui/phone/phone_skills_page.dart`；E0 `ui/phone/phone_skill_detail_page.dart`；E0 `ui/phone/always_allow_page.dart:75-138`；E0 `ui/phone/app_rules_page.dart:151`；E0 `ui/phone/screen_text_sheet.dart`；E0 `ui/phone/phone_self_check_panel.dart`；E0 `ui/phone/background_preview.dart`；E0 `ui/phone/phone_unlock_page.dart`；E0 `ui/phone/ios_pet_pip_section.dart`；E0 `ui/phone/ios_phone_link_section.dart`。 |
| 13 | 反证 + 验收 | **反证**：若手机操控直接执行不确认，一条幻觉指令就可能乱点支付/删除（dsh-Go 用三档确认 + 长按高危确认 + 每次支付必问）；若授权失败只说「失败」，用户找不到无障碍设置的深层入口。**验收** GO-48（仅当 P2 决定纳入）：真机授权、执行一次返回/Home、支付类强制确认。 |

#### `phone.modes` — 非干扰模式 / Android 壳 / 访问授权选择　（findings 38）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `phone.modes`。所属：手机操控面板→模式与授权。`dnd_mode_page.dart`（非干扰模式）、`android_shell_page.dart`（Android 壳编辑）、`ui/access/*`（访问授权选择：哪些应用/文件可被 Agent 访问）。 |
| 2 | 网格与定位 | 模式页：开关 + 说明 + 预览；授权页：应用/目录选择列表（`access_pick_page.dart:132`、`access_summary_page.dart:65-71`）；行整行可点、`minimumSize` 2 处（`access_widgets.dart:128`、`phone_mode_sheet.dart:102`）。 |
| 3 | 命中区 | 行整行可点；应用行带复选；`access_widgets.dart:122-197` 为行/开关；授权 sheet 有确认键。 |
| 4 | 手势 | 单击 = 选择/切换；长按 = 批量（未见）；返回 = 保存并退出（`PopScope`）。 |
| 5 | 行为 | 授权范围 → 决定 Agent 能看/操作什么（应用、目录）；非干扰模式 → 后台屏是否实时预览（`background_preview.dart`）；Android 壳 → 自定义状态栏/导航栏行为。 |
| 6 | 视觉状态 | `default`/`selected`/`disabled`（系统不支持）/`warning`（危险权限）。 |
| 7 | 布局占位与展开形态 | 参与布局流；无展开。 |
| 8 | 滚动语义 | 选择列表纵向滚；到边不转交。 |
| 9 | 动效 | 开关 160 ms；列表选中复选滑入。 |
| 10 | 边界态 | 系统不支持 → 整块说明 + 隐藏（`androidSystemFeaturesProvider`，`ui/state/android_features.dart`）；无应用 → 空态；大字号 → 行高增；横屏 → 限宽。 |
| 11 | 无障碍 | 复选/开关有语义；危险权限有风险文案。 |
| 12 | 依据 | E0 `ui/phone/dnd_mode_page.dart`；E0 `ui/phone/android_shell_page.dart:389`；E0 `ui/access/access_pick_page.dart:132`；E0 `ui/access/access_summary_page.dart:65-71`；E0 `ui/access/access_widgets.dart:122-197`；E0 `ui/access/phone_mode_sheet.dart:35-102`。 |
| 13 | 反证 + 验收 | **反证**：若把访问范围默认全开，Agent 能读任何应用内容（隐私事故）；若默认全关，功能形同不存在（dsh-Go 用逐个应用授权 + 明确列表）。**验收** GO-49（仅当 P2 纳入）：真机逐应用授权、只读受限应用、非干扰模式可开。 |

### 2.19 伙伴小屋（候选新面板）（面板 ID：`companion`；归位：myco 无）

#### `companion.room` — 伙伴小屋（WebView 房间 + Flutter 浮层对话 + 输入条）　（findings 78）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `companion.room`。所属：伙伴小屋面板（myco 无此概念）。出现条件：用户开启伙伴小屋并从抽屉/宠物菜单进入（`ui/companion/companion_page.dart:1-5`）；同屏 1 页。 |
| 2 | 网格与定位 | 整页 = 一个 WebView 画角色与房间 + Flutter 在上层画她的对话气泡/工具/提问/审批 + 一条小输入（`ui/companion/companion_page.dart:1-5`；`companion_chrome.dart` 为浮层 chrome；`companion_log.dart` 为日志；`companion_room_panel.dart` 侧面板）。 |
| 3 | 命中区 | 浮层气泡/按钮整块可点；输入条同聊天页（36 键）；`minimumSize` 3 处（`companion_chrome.dart:783`、`companion_drawer_row.dart:88`、`companion_room_panel.dart:284`）；`SafeArea` 10 处。 |
| 4 | 手势 | 单击 = 对话/互动；拖动 = 角色拖拽（WebView 内）；长按 = 菜单；下拉 = 刷新（`companion_chrome.dart:309` `AlwaysScrollableScrollPhysics(parent: ClampingScrollPhysics)` + `OverscrollNotification` 下拉手势 `:351-382`）。 |
| 5 | 行为 | 消息 → 追加到她的会话；审批/提问 → 就地在浮层卡片回答（复用 `approval_card`/`question_card`）；互动（摸头/双击）→ 触发动作；进入后台 → 她可主动发起（`companion_initiative`）。 |
| 6 | 视觉状态 | `default`/`thinking`（浮层显示处理中）/`speaking`（朗读中）/`waiting`（审批）/`error`。 |
| 7 | 布局占位与展开形态 | WebView 占满页面；浮层气泡 overlay 不占位；输入条在底部 safe area 内。 |
| 8 | 滚动语义 | WebView 内部自己滚；Flutter 侧下拉刷新走 `OverscrollNotification` 监听（`companion_chrome.dart:351-382`）；浮层日志列表可滚（`companion_log.dart:286`）。 |
| 9 | 动效 | 下拉刷新跟手（`companion_chrome.dart:309,351-382`）；气泡淡入。 |
| 10 | 边界态 | WebView 加载失败 → 重试/回退；离线 → 只显示缓存台词；大字号 → 浮层文字增大、WebView 内不跟随（已知割裂）；横屏 → 布局重排。 |
| 11 | 无障碍 | 浮层按钮有 label；WebView 内容语义由 H5 提供。 |
| 12 | 依据 | E0 `ui/companion/companion_page.dart:1-5,1407-1477,2033,3723`；E0 `ui/companion/companion_chrome.dart:279-309,351-382,461-594,749-783`；E0 `ui/companion/companion_log.dart:232-286`；E0 `ui/companion/companion_room_panel.dart:153,284`；E0 `ui/companion/companion_drawer_row.dart:88`。 |
| 13 | 反证 + 验收 | **反证**：若房间用纯 Flutter 画，Live2D/复杂动画在手机上掉帧且包体巨大（dsh-Go 用 WebView + Flutter 浮层分工）；若把对话也放进 H5，输入法与键盘处理会失去原生能力。**验收** GO-50（仅当 P2 纳入）：真机进小屋、发一句、她回、审批就地可答。 |

#### `companion.activity` — 伙伴任务 / 记忆 / 通知 / 语音 / 助手设置　（findings 65）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `companion.activity`。所属：伙伴面板→活动与设置。`companion_tasks_page.dart`（任务）、`companion_memory_page.dart` + 模型弹窗（记忆）、`companion_notices_page.dart`（通知）、`companion_voice_page.dart`（语音）、`companion_assistant_settings.dart`（设置）。 |
| 2 | 网格与定位 | 二级页 + 列表；任务行带状态；记忆页分段（人设/长期记忆）；`minimumSize` 1 处（`companion_notices_page.dart:290`）。 |
| 3 | 命中区 | 行整行可点；开关/删除键独立；记忆条目可长按删除（未见统一）。 |
| 4 | 手势 | 单击 = 进详情/编辑；开关 = 即时；删除 = 确认；语音 = 试听/选音色。 |
| 5 | 行为 | 任务 → 她自主执行的后台任务；记忆 → 增删改；通知 → 她的主动消息开关；语音 → 朗读声线。 |
| 6 | 视觉状态 | `default`/`running`/`done`/`failed`；记忆条目选中。 |
| 7 | 布局占位与展开形态 | 参与布局流；无展开。 |
| 8 | 滚动语义 | 列表纵向滚；到边不转交。 |
| 9 | 动效 | 行切换 160 ms；任务运行呼吸。 |
| 10 | 边界态 | 空列表 → 空态；离线 → 缓存只读；大字号 → 行高增；横屏 → 限宽。 |
| 11 | 无障碍 | 行/开关 label；删除确认可读屏。 |
| 12 | 依据 | E0 `ui/companion/companion_tasks_page.dart:713`；E0 `ui/companion/companion_memory_page.dart`；E0 `ui/companion/companion_memory_model_dialog.dart:81-87`；E0 `ui/companion/companion_notices_page.dart:119-126,290`；E0 `ui/companion/companion_voice_page.dart`；E0 `ui/companion/companion_assistant_settings.dart:54-96`。 |
| 13 | 反证 + 验收 | **反证**：若记忆只能看不能删，错误记忆会永久影响她的行为；若通知开关默认开，她会持续打扰。**验收** GO-51（仅当 P2 纳入）：真机增删记忆、关通知后不再主动发消息。 |

### 2.20 Bot 管理（候选新面板）（面板 ID：`bots`；归位：myco 无）

#### `bots.chat` — Bot 对话 / 群聊（浮顶栏 + 卡片 + 输入条）　（findings 25）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `bots.chat`。所属：聊天面板的 Bot 变体（复用 chat 行：`MarkdownBody`/`UserBubble`，`ui/bots/bot_chat_kit.dart:1-5`）。出现条件：打开某个 Bot/群聊会话。 |
| 2 | 网格与定位 | 页框 = 玻璃下的列表 + 底部卡片与输入条（`bot_chat_kit.dart:1-5`）；浮顶栏可折叠；输入条复用 `Composer`。 |
| 3 | 命中区 | 消息整块可点/长按（复用 chat 菜单）；卡片按钮 `minimumSize` 5 处 + `shrinkWrap` 4 处（`bot_chat_kit.dart:1120-1123,1254-1257`、`room_chat_page.dart:408-420`）。 |
| 4 | 手势 | 同上（单击、长按、拖动滚动）；另有「切模型/回答问题」卡片交互。 |
| 5 | 行为 | Bot 卡片：提问/视图/选模型 → 回答后继续；群聊：多角色发言。 |
| 6 | 视觉状态 | 同 chat；Bot 卡片有 `waiting` 态。 |
| 7 | 布局占位与展开形态 | 同 chat（参与布局流、卡片在流里）。 |
| 8 | 滚动语义 | 列表滚动 + 下拉加载更早（`bot_chat_kit.dart:427-452`：`ScrollNotification(depth:0)` + 距顶 400 触发 `loadEarlier`）；到边不转交（页面根）。 |
| 9 | 动效 | 卡片展开 160~240 ms；浮顶栏收起。 |
| 10 | 边界态 | 空会话 → 空态；Bot 失败 → 提示行；大字号 → 卡片换行；横屏 → 限宽。 |
| 11 | 无障碍 | 复用 chat 语义；Bot 卡片按钮有 label。 |
| 12 | 依据 | E0 `ui/bots/bot_chat_kit.dart:1-5,112-189,218,427-452,559,1120-1123,1254-1257`；E0 `ui/bots/room_chat_page.dart:408-420`。 |
| 13 | 反证 + 验收 | **反证**：若为 Bot 另写一套聊天界面，行高/长按菜单/滚动跟随会与主聊天不一致（dsh-Go 明确复用 chat 行组件）；若 Bot 的「回答问题」卡不在流里而在浮层，用户会漏答。**验收** GO-52：真机 Bot 对话与主聊天手感一致、卡片可就地回答。 |

#### `bots.manage` — Bot 管理（列表 / 详情 / 新建 / 房间 / 记忆 / 安装卡）　（findings 63）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `bots.manage`。所属：Bot 面板。`bots_page.dart`（列表）、`bot_details_page.dart`、`new_bot_page.dart`、`new_room_sheet.dart`、`room_details_page.dart`、`room_relay_limit_sheet.dart`、`working_folder_sheet.dart`、`memory_card.dart`、`install_card.dart`、`bot_sheets.dart`、`bot_mention.dart`、`duplicate_bot_row.dart`、`drawer_row.dart`。 |
| 2 | 网格与定位 | 列表页 + 详情页 + 多个 sheet；`minimumSize` 2 处（`bots_page.dart:266`、`new_room_sheet.dart:149`）；`maxHeight` 列表 5 处（`bot_mention.dart:207-230`、`bot_sheets.dart:175`）。 |
| 3 | 命中区 | 行整行可点；新建/保存/删除键独立；安装卡按钮整块可点。 |
| 4 | 手势 | 单击 = 详情/进入对话；新建 = 表单；房间设置 = sheet；删除 = 确认。 |
| 5 | 行为 | Bot → 会话入口；房间 → 群聊配置（成员/中继上限/工作目录）；记忆卡 → 查看/清空。 |
| 6 | 视觉状态 | `default`/`pressed`；安装卡 `installed`/`installing`；房间 `limit` 警告。 |
| 7 | 布局占位与展开形态 | 参与布局流；sheet-overlay 用于新建/设置。 |
| 8 | 滚动语义 | 列表滚；表单滚；到边不转交。 |
| 9 | 动效 | sheet 进/出 380/260 ms；保存对勾。 |
| 10 | 边界态 | 空列表 → 空态 + 新建；Bot 包损坏 → 安装卡错误；大字号 → 行高增；横屏 → 限宽。 |
| 11 | 无障碍 | 行/按钮 label；安装进度播报。 |
| 12 | 依据 | E0 `ui/bots/bots_page.dart:112-266`；E0 `ui/bots/bot_details_page.dart:348,437`；E0 `ui/bots/new_bot_page.dart`；E0 `ui/bots/new_room_sheet.dart:138-149`；E0 `ui/bots/room_details_page.dart`；E0 `ui/bots/room_relay_limit_sheet.dart`；E0 `ui/bots/working_folder_sheet.dart`；E0 `ui/bots/memory_card.dart`；E0 `ui/bots/install_card.dart`；E0 `ui/bots/bot_sheets.dart:26-182`；E0 `ui/bots/bot_mention.dart:207-231`；E0 `ui/bots/duplicate_bot_row.dart`；E0 `ui/bots/drawer_row.dart`。 |
| 13 | 反证 + 验收 | **反证**：若 Bot 只能通过导入文件创建、无管理页，手机上删不掉/改不了（dsh-Go 有完整管理页）；若房间中继上限不给提示，群聊会静默丢消息。**验收** GO-53：真机新建 Bot/房间、改成员、看记忆。 |

### 2.21 远程宿主（候选新面板）（面板 ID：`remote`；归位：myco 无）

#### `remote.hosts` — 远程宿主（宿主切换 sheet / 远程页 / 会话备份 / 连接进度）　（findings 36）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `remote.hosts`。所属：侧栏/远程。`host_switcher.dart`（宿主切换 sheet）、`remote_screen.dart`（远程管理页）、`session_backup_page.dart`（会话备份）、`link_progress.dart`（连接进度）。 |
| 2 | 网格与定位 | 切换 sheet 列表（`host_switcher.dart:74-169`）；远程页为列表 + 状态卡；备份页为文件/会话列表；进度条在行内（`link_progress.dart:182-195`）。 |
| 3 | 命中区 | 行整行可点；`shrinkWrap` 1 处（`remote_screen.dart:453`）；进度条按钮 `minimumSize 0×36`（`link_progress.dart:182-195`）。 |
| 4 | 手势 | 单击 = 切宿主/连接；长按 = 删除/重命名；下拉 = 刷新连接。 |
| 5 | 行为 | 切宿主 → 当前会话/模型/插件集合全部切换（需确认）；连接 → 握手/配对；备份 → 上传/下载会话。 |
| 6 | 视觉状态 | `default`/`connected`（绿点）/`connecting`（转圈）/`error`（红）/`offline`。 |
| 7 | 布局占位与展开形态 | 参与布局流；sheet-overlay 用于切换。 |
| 8 | 滚动语义 | 列表滚；到边不转交。 |
| 9 | 动效 | 连接进度条动画；切宿主后列表淡入。 |
| 10 | 边界态 | 无宿主 → 空态 + 添加；连接失败 → 错误详情（`showRemoteNetworkDetails`）；大字号 → 行高增；横屏 → 限宽。 |
| 11 | 无障碍 | 行 label + 状态播报；连接失败错误可读屏。 |
| 12 | 依据 | E0 `ui/remote/host_switcher.dart:39-169`；E0 `ui/remote/remote_screen.dart:452-453,660`；E0 `ui/remote/session_backup_page.dart`；E0 `ui/remote/link_progress.dart:182-195`；E0 `ui/shell/home_shell.dart:411-414`。 |
| 13 | 反证 + 验收 | **反证**：若切换宿主不确认直接切，正在跑的回合会丢（dsh-Go 在切换前有状态/确认）；若连接失败只显示红点，用户无法自助排查（dsh-Go 有网络详情 sheet）。**验收** GO-54：真机切宿主、连接失败看详情、备份会话。 |

#### `remote.pairing` — 配对 / SSH 主机表单 / 中继登录 / 主机密钥　（findings 29）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `remote.pairing`。所属：远程→配对。`pairing_page.dart`（配对码/扫码）、`ssh_host_form.dart`（SSH 主机表单）、`relay_login_sheet.dart`（中继登录）、`host_key_dialog.dart`（主机密钥确认）。 |
| 2 | 网格与定位 | 表单页 + 弹层；配对页含二维码/倒计时；`minimumSize` 2 处（`pairing_page.dart:776` 168×56、`ssh_host_form.dart:858` + `shrinkWrap`）。 |
| 3 | 命中区 | 按钮整块可点；密钥确认对话框两键；二维码长按可保存（未见）。 |
| 4 | 手势 | 单击 = 配对/登录/确认；长按 = 复制配对码；拖动未见。 |
| 5 | 行为 | 配对成功 → 新增宿主并连接；SSH 表单 → 保存后测试连接；主机密钥 → 首次确认后记住。 |
| 6 | 视觉状态 | `default`/`busy`（配对中）/`error`（码过期/密钥不匹配）。 |
| 7 | 布局占位与展开形态 | 参与布局流；对话框/表单。 |
| 8 | 滚动语义 | 表单滚；到边不转交。 |
| 9 | 动效 | 倒计时；配对成功对勾。 |
| 10 | 边界态 | 配对码过期 → 重新生成；SSH 连接失败 → 行内错误；大字号 → 表单换行；横屏 → 限宽。 |
| 11 | 无障碍 | 表单字段 label；密钥指纹可读屏。 |
| 12 | 依据 | E0 `ui/remote/pairing_page.dart:776`；E0 `ui/remote/ssh_host_form.dart:858-859`；E0 `ui/remote/relay_login_sheet.dart:13,84`；E0 `ui/remote/host_key_dialog.dart:44-217`。 |
| 13 | 反证 + 验收 | **反证**：若 SSH 主机密钥首次连接不确认（无 TOFU 提示），中间人可劫持；若配对码不复用倒计时，过期码会一直显示"失败"。**验收** GO-55：真机配对成功、SSH 首连弹指纹、错误可重试。 |

### 2.22 酒馆（候选新面板）（面板 ID：`tavern`；归位：myco 无）

#### `tavern.home` — 酒馆首页（书架 / 对话 / 资源库 / 我的）+ 搜索/收藏/用量　（findings 36）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `tavern.home`。所属：酒馆面板（myco 无此概念）。出现条件：切到酒馆模式（`ui/tavern/tavern_home_page.dart:1-5`）。四个分区：书架、对话、资源库、我的。 |
| 2 | 网格与定位 | 二级页：分区标题 + 横向书架 + 每角色卡（`tavern_home_page.dart:239-318`）；`minimumSize` 4 处（`:243`）；`maxHeight` 5 处（`tavern_island.dart:67,243`）。 |
| 3 | 命中区 | 整卡/整行可点；长按角色 = 菜单（删除/导出）；`onLongPress` 3 处。 |
| 4 | 手势 | 单击 = 进对话/角色/资源；长按 = 角色菜单；返回 = 回工作模式（模式键切换，`:1-5`）。 |
| 5 | 行为 | 进对话 → 打开该角色会话；资源库 → 包/世界书/预设；我的 → 酒馆设置。 |
| 6 | 视觉状态 | `default`/`running`（有进行中对话）/`unread`。 |
| 7 | 布局占位与展开形态 | 参与布局流；分区可展开（书架横向滑）。 |
| 8 | 滚动语义 | 页面纵向滚；书架横向滚（`ListView` 多处）；到边不转交。 |
| 9 | 动效 | 卡进 160 ms；横向书架惯性。 |
| 10 | 边界态 | 空书架 → 空态 + 导入入口；角色包损坏 → 卡错误；大字号 → 卡片换行；横屏 → 书架列数增加。 |
| 11 | 无障碍 | 卡/行 label + 角色名；横滑书架有语义。 |
| 12 | 依据 | E0 `ui/tavern/tavern_home_page.dart:1-5,239-318`；E0 `ui/tavern/tavern_bookmarks_page.dart:75-81`；E0 `ui/tavern/tavern_search_page.dart:192`；E0 `ui/tavern/tavern_usage_page.dart`；E0 `ui/tavern/tavern_report_page.dart`；E0 `ui/tavern/tavern_runs_page.dart:96-109`；E0 `ui/tavern/tavern_scripts_page.dart:156`；E0 `ui/tavern/tavern_island.dart:66-67,243`；E0 `ui/tavern/tavern_live2d_tile.dart`。 |
| 13 | 反证 + 验收 | **反证**：若照搬桌面酒馆的密集列表，手机上每屏只能看 2 个角色且无封面；若书架不做横向滑动，长列表要滚很久才到资源库。**验收** GO-56（仅当 P2 纳入）：真机四分区可切换、书架横滑、角色可进对话。 |

#### `tavern.chat` — 酒馆对话（树状分支 / 候选回复 ‹2/5› / 消息长按 / 草稿）　（findings 67）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `tavern.chat`。所属：酒馆面板→对话。出现条件：打开某角色对话（`ui/tavern/tavern_chat_page.dart:1-4`）；每条消息可在整页画布上画（canvas 模式）。 |
| 2 | 网格与定位 | 聊天页：消息流 + 底部候选回复条（‹ 2/5 ›）+ 输入；树状路径提示；`tavern_component_view.dart:83` `minimumSize 48×40`；`shrinkWrap` 1 处（`tavern_chat_page.dart:3020`）。 |
| 3 | 命中区 | 消息整块可点/长按（8 处手势）；候选切换箭头 44+；输入同 Composer。 |
| 4 | 手势 | 单击 = 展开/菜单；长按 = 消息菜单（编辑/删除/重生成/分支）；左右滑 = 切换候选/分支（`tavern_chat_page.dart:948-960,1985-2009` 滚动监听 + 沉浸式头部）。 |
| 5 | 行为 | 候选：‹/› 切换同位置回复；编辑 → 改文本并重算后续；分支 → 新建路径；草稿页（`tavern_draft_page.dart`）用于写多段剧情。 |
| 6 | 视觉状态 | `default`/`streaming`（正在生成）/`swiped`（候选切换）/`editing`。 |
| 7 | 布局占位与展开形态 | 参与布局流；长消息可折叠（`maxHeight` 4 处，`tavern_draft_page.dart:438,858`）。 |
| 8 | 滚动语义 | 消息列表滚动（`tavern_chat_page.dart:1262,1448`）；到边不转交（页面根）；下拉/滚动联动头部沉浸（`:1985-2009`）。 |
| 9 | 动效 | 候选切换 160 ms；流式生成跟底（同 chat 跟随思路）；头部沉浸随滚动。 |
| 10 | 边界态 | 空对话 → 开场白；生成失败 → 重试；超长剧情 → 草稿/分支；大字号 → 行高增；横屏 → 画布模式。 |
| 11 | 无障碍 | 消息/候选/箭头均有 label；流式播报。 |
| 12 | 依据 | E0 `ui/tavern/tavern_chat_page.dart:1-4,948-960,1172-1448,1985-2009,2075,3020`；E0 `ui/tavern/tavern_component_view.dart:83`；E0 `ui/tavern/tavern_message_edit_page.dart`；E0 `ui/tavern/tavern_draft_page.dart:438,858`；E0 `ui/tavern/tavern_draft_prompt_page.dart`；E0 `ui/tavern/tavern_draft_trial_page.dart`；E0 `ui/tavern/tavern_draft_history_page.dart`；E0 `ui/tavern/tavern_drafts_page.dart`；E0 `ui/tavern/tavern_model_rows.dart`；E0 `ui/tavern/tavern_stepper.dart`；E0 `ui/tavern/tavern_toasts.dart`；E0 `ui/tavern/tavern_tool_strip.dart`；E0 `ui/tavern/tavern_widgets.dart`；E0 `ui/tavern/tavern_chat_files.dart:74-84`。 |
| 13 | 反证 + 验收 | **反证**：若酒馆对话复用主聊天的"单线消息"，分支/候选（重生成 5 版）无处存放，用户只能覆盖历史；若候选切换做成整页刷新，会丢失滚动位置。**验收** GO-57（仅当 P2 纳入）：真机切候选 ‹2/5›、编辑消息后分支、草稿可继续。 |

#### `tavern.stage` — 舞台侧面板（本局/状态/导演/本回合）+ VN/互动/骰子/故事书　（findings 49）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `tavern.stage`。所属：酒馆面板→舞台。出现条件：对话页打开舞台侧面板（`ui/tavern/tavern_stage_panel.dart:1-6`，四页：本局/状态/导演/本回合）；VN 视图（`tavern_vn_view.dart`）、互动（`tavern_interact_view.dart`）、骰子（`tavern_dice_sheet.dart`）、故事书（`tavern_storybook_page.dart`）、检查器（`tavern_inspector_page.dart`）。 |
| 2 | 网格与定位 | 侧面板 = 抽屉/整页（随宽度）；四页 tab；状态卡为字段网格；`minimumSize` 5 处（`tavern_interact_view.dart:105,199`、`tavern_stage_panel.dart:1100`）；`maxHeight` 3 处（`tavern_stage_panel.dart:586`）。 |
| 3 | 命中区 | tab/行整块可点；字段可编辑（行内输入）；`interact_view` 选项按钮 44（`:199`）。 |
| 4 | 手势 | 单击 = 切换 tab/编辑字段；滑动 = 切页（VN 视图左右滑）；骰子 = 点投掷。 |
| 5 | 行为 | 改状态/导演笔记 → 影响下一回合 prompt；投骰 → 结果进上下文；VN 视图 → 视觉小说式推进。 |
| 6 | 视觉状态 | `default`/`editing`/`rolled`（骰子结果）/`dirty`（未保存）。 |
| 7 | 布局占位与展开形态 | 面板内 tab 页；VN/画布为全屏覆盖（G-03 C）。 |
| 8 | 滚动语义 | 面板内各页纵向滚；VN 横滑换页；到边不转交。 |
| 9 | 动效 | tab 切换 160 ms；骰子滚动动画。 |
| 10 | 边界态 | 无状态 → 空态；字段非法 → 行内红字；大字号 → 字段换行；横屏 → 面板更宽。 |
| 11 | 无障碍 | tab `selected` 语义；字段有 label；骰子结果播报。 |
| 12 | 依据 | E0 `ui/tavern/tavern_stage_panel.dart:1-6,131,467,580-586,1100`；E0 `ui/tavern/tavern_vn_view.dart:595`；E0 `ui/tavern/tavern_interact_view.dart:105-199,296`；E0 `ui/tavern/tavern_dice_sheet.dart:30,269,324-399`；E0 `ui/tavern/tavern_storybook_page.dart`；E0 `ui/tavern/tavern_inspector_page.dart:290`。 |
| 13 | 反证 + 验收 | **反证**：若状态/导演设置只能进设置页改，扮演过程中改天气/关系要离开对话（丢失沉浸）；若 VN 视图用页面栈推进，返回会退出整场对话。**验收** GO-58（仅当 P2 纳入）：真机开舞台四页、改状态、投骰、VN 推进可返回。 |

#### `tavern.characters` — 角色卡 / 人设 / 群聊 / 频道　（findings 60）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `tavern.characters`。所属：酒馆→角色与群聊。`tavern_character_page.dart`（角色详情）、`tavern_character_edit_page.dart`（编辑）、`tavern_personas.dart`（人设库）、`tavern_group_page.dart` / `group_new_page`（群聊）、`tavern_channels_page.dart`（频道）。 |
| 2 | 网格与定位 | 详情页：头像 + 标签 + 简介 + 操作；人设库为列表/swiper（`tavern_personas.dart:232` `minimumSize`）；群聊成员网格；频道列表（`tavern_channels_page.dart:526-598`）。 |
| 3 | 命中区 | 行/卡整块可点；长按角色 = 菜单（8 处 `onLongPress`）；成员可移除。 |
| 4 | 手势 | 单击 = 进角色/对话；长按 = 导出/删除；群聊 = 增删成员；频道 = 进入/编辑。 |
| 5 | 行为 | 角色 → 开始或继续对话；人设 → 切换当前人设；群聊 → 多角色同一会话；频道 → 话题分流。 |
| 6 | 视觉状态 | `default`/`selected`（当前人设）/`disabled`（包缺失）。 |
| 7 | 布局占位与展开形态 | 参与布局流；sheet/页。 |
| 8 | 滚动语义 | 列表滚；人设卡横向滑；到边不转交。 |
| 9 | 动效 | 卡切换 160 ms；头像淡入。 |
| 10 | 边界态 | 空角色 → 导入入口；包不完整 → 卡错误；大字号 → 卡换行；横屏 → 网格列数增。 |
| 11 | 无障碍 | 卡/行 label；群聊成员播报。 |
| 12 | 依据 | E0 `ui/tavern/tavern_character_page.dart:413-418`；E0 `ui/tavern/tavern_character_edit_page.dart`；E0 `ui/tavern/tavern_personas.dart:232`；E0 `ui/tavern/tavern_group_page.dart`；E0 `ui/tavern/tavern_group_new_page.dart`；E0 `ui/tavern/tavern_channels_page.dart:52-89,304,482-598`。 |
| 13 | 反证 + 验收 | **反证**：若角色卡不支持编辑，导入的卡有错只能重新导入；若群聊不做成员管理，多角色对话无法控制谁说话。**验收** GO-59（仅当 P2 纳入）：真机编辑角色、切人设、群聊增删成员。 |

#### `tavern.library` — 资源库（角色包 / 世界书 / 预设 / 正则 / 快捷回复 / 导入导出）　（findings 53）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `tavern.library`。所属：酒馆→资源库。含包列表/详情/绑定/导出/预览、世界书 + 测试、预设、正则 + 测试、快捷回复、DeepSeek 导入。 |
| 2 | 网格与定位 | 列表页 + 详情 + sheet（`safe` 13 处、sheet 13 处）；`maxHeight` 2 处（`tavern_pack_widgets.dart:57`、`tavern_preset_page.dart:582`）。 |
| 3 | 命中区 | 行整行可点；导入/导出/删除键独立；世界书条目可展开。 |
| 4 | 手势 | 单击 = 详情/编辑；导入 = 文件选择 + 校验；导出 = 生成包文件并分享；绑定 = 把资源绑到角色/对话。 |
| 5 | 行为 | 资源 → 影响对话上下文（世界书/预设/正则）；导入失败 → 列出原因。 |
| 6 | 视觉状态 | `default`/`bound`/`error`；导入进度。 |
| 7 | 布局占位与展开形态 | 参与布局流；详情页；sheet 用于绑定/导入。 |
| 8 | 滚动语义 | 列表滚；世界书条目可展开；到边不转交。 |
| 9 | 动效 | 导入进度；条目展开 160 ms。 |
| 10 | 边界态 | 包校验失败 → 错误列表（`ui/tavern/tavern_deepseek_import.dart`）；空库 → 空态；大字号 → 行高增；横屏 → 限宽。 |
| 11 | 无障碍 | 行/按钮 label；校验错误可读屏。 |
| 12 | 依据 | E0 `ui/tavern/tavern_packs_page.dart`；E0 `ui/tavern/tavern_pack_page.dart`；E0 `ui/tavern/tavern_pack_bindings_page.dart:222`；E0 `ui/tavern/tavern_pack_export_page.dart`；E0 `ui/tavern/tavern_pack_preview_page.dart`；E0 `ui/tavern/tavern_pack_widgets.dart:57`；E0 `ui/tavern/tavern_lorebooks_page.dart:278-367`；E0 `ui/tavern/tavern_lorebook_page.dart`；E0 `ui/tavern/tavern_lorebook_test_page.dart`；E0 `ui/tavern/tavern_presets_page.dart`；E0 `ui/tavern/tavern_preset_page.dart:582`；E0 `ui/tavern/tavern_regex_page.dart`；E0 `ui/tavern/tavern_regex_edit_page.dart`；E0 `ui/tavern/tavern_regex_test_page.dart`；E0 `ui/tavern/tavern_quick_replies_page.dart`；E0 `ui/tavern/tavern_deepseek_import.dart:33-136`。 |
| 13 | 反证 + 验收 | **反证**：若资源库做成纯文件管理，用户不知道某个世界书是否生效；若导入不做校验，坏包会让整场对话崩。**验收** GO-60（仅当 P2 纳入）：真机导入角色卡/世界书、绑定生效、导出可分享。 |

#### `tavern.settings` — 酒馆设置（音频 / 分支 / 偏好）　（findings 20）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `tavern.settings`。所属：酒馆→设置。`tavern_settings_page.dart` + 音频 section（`tavern_audio_section.dart`）+ 分支页（`tavern_branches_page.dart`）。 |
| 2 | 网格与定位 | 设置行列表 + 分节（音频/分支/常规）；行由 `SettingsTile` 画；`safe` 3 处。 |
| 3 | 命中区 | 行整行可点；开关 `shrinkWrap`；滑块（音量/语速）跟手。 |
| 4 | 手势 | 单击 = 开关/进子页；拖动 = 滑块；返回 = 保存。 |
| 5 | 行为 | 音频 → TTS/环境音开关与音量；分支 → 管理对话树路径（切换/删除）。 |
| 6 | 视觉状态 | `default`/`pressed`；分支 `current` 标记。 |
| 7 | 布局占位与展开形态 | 参与布局流；无展开。 |
| 8 | 滚动语义 | 页面滚；到边不转交。 |
| 9 | 动效 | 滑块跟手；开关 160 ms。 |
| 10 | 边界态 | 无音频资源 → 灰；分支为空 → 空态；大字号 → 行高增；横屏 → 限宽。 |
| 11 | 无障碍 | 行/开关 label；滑块语义值。 |
| 12 | 依据 | E0 `ui/tavern/tavern_settings_page.dart`；E0 `ui/tavern/tavern_audio_section.dart:45-86`；E0 `ui/tavern/tavern_branches_page.dart:148-305`。 |
| 13 | 反证 + 验收 | **反证**：若分支管理不给可视化树，用户无法知道当前在哪个分支、也无法回到主线；若音量只跟系统，角色语音与背景音无法平衡。**验收** GO-61（仅当 P2 纳入）：真机切分支、调音量、开关环境音。 |

### 2.23 论坛（候选新面板）（面板 ID：`forum`；归位：myco 无）

#### `forum.page` — 论坛（首页 / 话题 / 发帖 / 搜索 / 通知 / 账号）　（findings 47）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `forum.page`。所属：论坛面板（myco 无此概念）。`forum_home_page.dart`（最新/热门/分类胶囊 + 话题列表 + 下拉刷新 + 无限加载）、`forum_topic_page.dart`、`forum_composer_sheet.dart`、`forum_search_page.dart`、`forum_notifications_page.dart`、`forum_account_sheet.dart`、`forum_post_body.dart`。 |
| 2 | 网格与定位 | 首页：分类胶囊横滚 + 话题卡列表 + 下拉刷新（`forum_home_page.dart:82` `ListView`）；发帖 sheet（`forum_composer_sheet.dart:101`）；`minimumSize` 11 处（`forum_account_sheet.dart:114,333,341`）。 |
| 3 | 命中区 | 整卡可点；投票/点赞键独立；`shrinkWrap` 行。 |
| 4 | 手势 | 单击 = 进话题/点赞/投票；长按 = 复制链接；下拉 = 刷新；滚动到底 = 加载更多。 |
| 5 | 行为 | 发帖 → 进 composer sheet；登录 → 账号 sheet（Discourse）；点赞/收藏 → 状态即时。 |
| 6 | 视觉状态 | `default`/`liked`/`pinned`/`solved`/`loading`。 |
| 7 | 布局占位与展开形态 | 参与布局流；composer sheet。 |
| 8 | 滚动语义 | 首页纵向滚 + 下拉刷新 + 触底加载（`ScrollUpdateNotification`，`forum_home_page.dart:162`、`forum_topic_page.dart:298`）。 |
| 9 | 动效 | 下拉刷新指示；加载更多 spinner；点赞动画。 |
| 10 | 边界态 | 未登录 → 只读 + 登录入口；无网络 → 错误 + 重试；大字号 → 卡换行；横屏 → 限宽。 |
| 11 | 无障碍 | 卡/按钮 label；点赞数播报。 |
| 12 | 依据 | E0 `ui/forum/forum_home_page.dart:1-5,82,162`；E0 `ui/forum/forum_topic_page.dart:298`；E0 `ui/forum/forum_composer_sheet.dart:90-101,304`；E0 `ui/forum/forum_search_page.dart`；E0 `ui/forum/forum_notifications_page.dart:106`；E0 `ui/forum/forum_account_sheet.dart:28-114,247,333-341`；E0 `ui/forum/forum_post_body.dart:166,275-287,476`；E0 `ui/forum/forum_widgets.dart`。 |
| 13 | 反证 + 验收 | **反证**：若论坛只做 WebView，发帖/登录的键盘与文件选择在手机上体验差；若不做下拉刷新与触底加载，长列表只能手动重进。**验收** GO-62：真机刷话题、发帖（含登录）、搜索、看通知。 |

### 2.24 创意工坊（候选新面板）（面板 ID：`workshop`；归位：myco 无）

#### `workshop.page` — 创意工坊（浏览 / 已装 / 我的 / 发布 / 安装 / 举报）　（findings 53）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `workshop.page`。所属：工坊面板（myco 无此概念）。`workshop_browse.dart`、`workshop_installed*.dart`、`workshop_mine*.dart`、`workshop_item_sheet.dart`、`workshop_publish_*`、`workshop_report_sheet.dart`、`workshop_sign_in.dart`、`workshop_disclosure.dart`。 |
| 2 | 网格与定位 | 列表页 + 详情 sheet（`workshop_item_sheet.dart:34`）+ 发布表单（多步）；`minimumSize` 20 处（`workshop_disclosure.dart:124-130`、`workshop_installed_sheet.dart:288`）；`shrinkWrap` 3 处。 |
| 3 | 命中区 | 卡整块可点；安装/更新键独立；发布表单字段行。 |
| 4 | 手势 | 单击 = 详情；安装 = 下载+校验+写盘（进度）；发布 = 多步表单 + 预览；举报 = sheet。 |
| 5 | 行为 | 安装 → 进已装并生效；发布 → 上传（需登录）；删除 → 确认。 |
| 6 | 视觉状态 | `default`/`installed`/`update`/`installing`（进度）/`failed`（红）。 |
| 7 | 布局占位与展开形态 | 参与布局流；详情/安装为 sheet；发布为页。 |
| 8 | 滚动语义 | 列表滚；到边不转交。 |
| 9 | 动效 | 安装进度；sheet 进/出 380/260 ms；发布步骤切换 300 ms。 |
| 10 | 边界态 | 未登录 → 发布入口转登录；空间不足/哈希不符 → 安装失败原因（`workshop_item_sheet.dart:1-8` 注释：安装由 provider 复核 freshness/version/hash/空间）；大字号 → 卡换行；横屏 → 限宽。 |
| 11 | 无障碍 | 卡/按钮 label；安装进度播报。 |
| 12 | 依据 | E0 `ui/workshop/workshop_browse.dart:109,222`；E0 `ui/workshop/workshop_item_sheet.dart:1-8,34`；E0 `ui/workshop/workshop_installed_sheet.dart:51-288`；E0 `ui/workshop/workshop_installed.dart:297`；E0 `ui/workshop/workshop_mine_sheet.dart:323-354`；E0 `ui/workshop/workshop_mine.dart`；E0 `ui/workshop/workshop_publish_page.dart:837`；E0 `ui/workshop/workshop_publish_form.dart`；E0 `ui/workshop/workshop_publish_preview.dart:224-225`；E0 `ui/workshop/workshop_publish_recovery.dart:62`；E0 `ui/workshop/workshop_report_sheet.dart:199-241`；E0 `ui/workshop/workshop_sign_in.dart:102-129`；E0 `ui/workshop/workshop_disclosure.dart:28-130`；E0 `ui/workshop/workshop_widgets.dart:188-273`。 |
| 13 | 反证 + 验收 | **反证**：若安装不在客户端复核版本/哈希/空间，坏包或半装会直接把 App 弄坏（dsh-Go 在 provider 里统一复核）；若发布只给一个文件框，手机上无法完成"填表—预览—发布"。**验收** GO-63：真机安装/更新/卸载工坊项、发布流程可走到预览。 |

### 2.25 宠物（候选新面板）（面板 ID：`pets`；归位：myco 无）

#### `pets.stage` — 宠物舞台（聊天页浮层 / 点击拖拽 / 气泡 / 菜单 / 设置）　（findings 29）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `pets.stage`。所属：聊天面板→宠物浮层（可关）。出现条件：用户开启宠物（`ui/pets/pet_stage.dart:1-10`）；每页 1 个 `PetStage`。 |
| 2 | 网格与定位 | 覆盖在页面之上的图层，不改变列表布局（`pet_stage.dart:1-3`）；宠物站在输入条上方胶囊顶上（`PerchKind.composer`，`ui/chat/composer.dart:392-395`）；气泡/菜单为主要可点件（`minimumSize` 2 处：`overlay_settings_tile.dart:170`、`pet_bubble.dart:79`）。 |
| 3 | 命中区 | 只有宠物身体接收触摸（`pet_stage.dart:6-7`）；气泡/菜单按钮整块可点。 |
| 4 | 手势 | 单击 = 互动（左右不同）；连点 = 挠痒；拖动 = 移动；长按 = 菜单（`onLongPress` 5 处）；60 s 空闲睡觉。 |
| 5 | 行为 | 互动 → 改变状态/气泡台词；菜单 → 宠物设置/隐藏；回合结束/别处等待 → 主动出气泡（`pet_stage.dart:3-7`）。 |
| 6 | 视觉状态 | `idle`/`walking`/`sleeping`/`reacting`/`bubble`。 |
| 7 | 布局占位与展开形态 | 浮层 overlay，不占布局（G-01 同族）；宠物位置随输入条高度变化同帧跟随。 |
| 8 | 滚动语义 | 不滚；`HitTestBehavior.translucent` 让指针穿透到列表（`pet_stage.dart:183-185`）。 |
| 9 | 动效 | 宠物动作逐帧（walk/hop）；气泡淡入。 |
| 10 | 边界态 | 关闭宠物 → 不渲染；资源缺失 → 静态图/不显示；大字号 → 气泡文字增大；横屏 → 舞台重排。 |
| 11 | 无障碍 | 宠物有「互动/菜单」语义；气泡文字可读屏。 |
| 12 | 依据 | E0 `ui/pets/pet_stage.dart:1-10,183-185`；E0 `ui/pets/pet_bubble.dart:66-79`；E0 `ui/pets/pet_menu.dart:44-80`；E0 `ui/pets/pet_picker.dart:146`；E0 `ui/pets/pet_settings_page.dart`；E0 `ui/pets/overlay_settings_tile.dart:87-172`；E0 `ui/chat/composer.dart:392-395`。 |
| 13 | 反证 + 验收 | **反证**：若宠物占布局高度，聊天区每帧都被它的移动重排（dsh-Go 用 overlay + 只让身体接收触摸）；若它吞掉触摸，列表就没法滚。**验收** GO-72：真机点宠物有反应、拖动能移动、滚列表不被挡、可关闭。 |

### 2.26 首启引导（候选新面板）（面板 ID：`onboarding`；归位：myco 无）

#### `onboarding.setup` — 首启引导（欢迎 / 运行时选择 / 安装 / 连通 / API key / 外观步骤）　（findings 32）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `onboarding.setup`。所属：引导流程（多步全屏）。出现条件：首次启动或重置后（`ui/onboarding/welcome_page.dart:12-14`）。步骤：欢迎 → 运行时选择（本地 PRoot / 远程）→ 运行时安装/进度 → 连接/API key → 外观。 |
| 2 | 网格与定位 | `OnboardingScaffold` 统一页框（`ui/onboarding/onboarding_scaffold.dart:78`）；底部主按钮 `minimumSize` 1 处（`connect_page.dart:356`）+ `shrinkWrap` 1 处（`usage_notice_row.dart:76`）；进度页 `ListView`（`setup_progress.dart:486`）。 |
| 3 | 命中区 | 按钮整块可点；步骤指示器不可点；用量统计开关行 `shrinkWrap` 整行点。 |
| 4 | 手势 | 单击 = 下一步/选择/重试；返回 = 上一步（`PopScope` 1 处）；长按无。 |
| 5 | 行为 | 选择运行时 → 下载/安装（长任务 + 进度）；连接 → 输入地址/扫码；API key → 校验并保存（脱敏显示 `maskApiKey`）；完成后进主页。 |
| 6 | 视觉状态 | `default`/`selected`（运行时选择）/`installing`（进度）/`error`（失败可重试）/`done`。 |
| 7 | 布局占位与展开形态 | 全屏步骤页；安装进度步骤可滚。 |
| 8 | 滚动语义 | 安装进度列表滚；到边不转交。 |
| 9 | 动效 | 步骤切换 300 ms（`AppMotion.page`）；安装进度呼吸。 |
| 10 | 边界态 | 无网络 → 安装失败 + 重试；架构不支持 → 说明；用户跳过 → 允许（远程模式）；大字号 → 步骤内容滚；横屏 → 居中式布局。 |
| 11 | 无障碍 | 每步有标题语义；用量统计开关有说明文案；错误播报。 |
| 12 | 依据 | E0 `ui/onboarding/welcome_page.dart:12-14,128`；E0 `ui/onboarding/onboarding_scaffold.dart:78`；E0 `ui/onboarding/runtime_choice.dart`；E0 `ui/onboarding/runtime_setup_page.dart:354`；E0 `ui/onboarding/setup_progress.dart:445-486`；E0 `ui/onboarding/connect_page.dart:356`；E0 `ui/onboarding/api_key_page.dart:151-227`；E0 `ui/onboarding/local_install.dart`；E0 `ui/onboarding/appearance_step.dart`；E0 `ui/onboarding/usage_notice_row.dart:51-76`。 |
| 13 | 反证 + 验收 | **反证**：若首启直接进空聊天页，用户不知道要装引擎/配 key（dsh-Go 把安装做成引导步骤 + 进度可恢复）；若 API key 明文回显，用户可能被旁观者看到。**验收** GO-70：真机全流程走完、安装进度可恢复、key 脱敏显示。 |

### 2.27 账号（候选新面板）（面板 ID：`account`；归位：myco 无）

#### `account.page` — 账号（登录 / 资料 / 余额 / 订阅入口）　（findings 15）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `account.page`。所属：设置→账号。出现条件：点设置首页账号行或远程页账号（`ui/account/account_page.dart`）。 |
| 2 | 网格与定位 | 头像 + 资料行 + 余额/订阅卡；`minimumSize` 1 处（`account_widgets.dart:358`）。 |
| 3 | 命中区 | 行整行可点；登录/退出键整块可点。 |
| 4 | 手势 | 单击 = 登录/退出/管理订阅；长按 = 复制账号 id（未见）。 |
| 5 | 行为 | 登录 → 走账号 API（`state/account_api.dart`）；退出 → 清本地凭据；订阅 → 打开订阅页。 |
| 6 | 视觉状态 | `logged-out`/`logged-in`/`loading`/`error`。 |
| 7 | 布局占位与展开形态 | 参与布局流；无展开。 |
| 8 | 滚动语义 | 页面滚；到边不转交。 |
| 9 | 动效 | 登录中 spinner；资料淡入。 |
| 10 | 边界态 | 离线 → 显示缓存资料 + 提示；未登录 → 登录卡；大字号 → 行高增；横屏 → 限宽。 |
| 11 | 无障碍 | 行/按钮 label；账号邮箱脱敏播报。 |
| 12 | 依据 | E0 `ui/account/account_page.dart:96-97,197-204,303,388,461`；E0 `ui/account/account_widgets.dart:358`。 |
| 13 | 反证 + 验收 | **反证**：若账号信息在设置里明文显示完整 token/邮箱，旁观者可读；若退出不清本地凭据，换人用机仍能发消息。**验收** GO-71：真机登录/退出、离线看缓存资料、凭据清除。 |

### 2.28 终端（候选新面板）（面板 ID：`terminal`；归位：myco 无）

#### `terminal.page` — 会话终端（全屏暗色 + 键行 + 手势）　（findings 25）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `terminal.page`。所属：会话终端（myco 无此概念）。出现条件：从会话详情/顶栏进（`ui/terminal/terminal_page.dart:1-3`：官方 Web 右栏终端，手机做成全屏页）。 |
| 2 | 网格与定位 | 全屏：顶部胶囊 tab + 暗色终端卡 + 键盘上方的键行（`terminal_page.dart:1-3`，`terminal_keys.dart`）；键行键高 `TerminalBar.keyHeight`（`terminal_keys.dart:534-535`）。 |
| 3 | 命中区 | 键行每键整块可点（`onPointerDown/Move/Up`，`terminal_keys.dart:452-456`）；终端区手势 = 滚动/选择。 |
| 4 | 手势 | 单击 = 发送按键/字符；长按 = 连发（`_press/_move/_release` 长按重复）；拖动终端 = 滚动缓冲（`terminal_screen.dart:462-466`）。 |
| 5 | 行为 | 按键 → 写进远端 shell；tab → 切会话；清屏/复制等。 |
| 6 | 视觉状态 | `default`/`pressed`（键态）/`error`（连接断）。 |
| 7 | 布局占位与展开形态 | 全屏页；键行固定在键盘上方（`SafeArea` 3 处）。 |
| 8 | 滚动语义 | 终端缓冲纵向滚（`terminal_screen.dart:462-466`）；到边不转交（页面根）。 |
| 9 | 动效 | 键按下高亮即时；无动画。 |
| 10 | 边界态 | 连接断 → 提示行 + 重连；无 shell → 空态；大字号 → 键行换行；横屏 → 键更多。 |
| 11 | 无障碍 | 键有 label；终端输出可读屏（性能考虑可能限流）。 |
| 12 | 依据 | E0 `ui/terminal/terminal_page.dart:1-3,85,143,226,368-455`；E0 `ui/terminal/terminal_keys.dart:289,452-456,526-535`；E0 `ui/terminal/terminal_screen.dart:462-466,563`。 |
| 13 | 反证 + 验收 | **反证**：若把终端做成只读日志，手机上的 Agent 排障只能看不能动；若键行用系统键盘，一屏被键盘吃掉一半且没有 Ctrl/Esc。**验收** GO-65：真机执行命令、长按连发、断线重连。 |

### 2.29 内嵌网页（面板 ID：`web`；归位：myco 无）

#### `web.viewer` — 内嵌网页（DSH Web / 本地网页）　（findings 13）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `web.viewer`。所属：网页浏览（DSH Web 在手机上的入口）。出现条件：抽屉「网页版」（`ui/shell/home_shell.dart:236-247`）或本地 web 页（`ui/web/local_web_screen.dart`）。 |
| 2 | 网格与定位 | 全屏 WebView（`webview_flutter`）；顶部浮层返回/刷新；`minimumSize` 5 处（`local_web_screen.dart:137-143`、`web_view_page.dart:1162`）；`shrinkWrap` 1 处（`:1365`）。 |
| 3 | 命中区 | 返回/刷新键整块可点；WebView 内由网页自己决定。 |
| 4 | 手势 | 系统返回 = 网页内后退优先，再退出（`web_view_page.dart` 有 `PopScope`）；手势由页面吞掉（注入 CSS `overscroll-behavior:none`，`:472-476`）。 |
| 5 | 行为 | 外部链接 → 交给系统浏览器/应用内打开（`web_handle.dart`）；登录态由 WebView 保持。 |
| 6 | 视觉状态 | `default`/`loading`（进度条）/`error`（加载失败 + 重试）。 |
| 7 | 布局占位与展开形态 | 全屏页（路由）。 |
| 8 | 滚动语义 | WebView 内部滚；注入样式关闭 overscroll 链（`web_view_page.dart:472-476`）；外层不参与。 |
| 9 | 动效 | 加载进度条；页面切换原生过渡。 |
| 10 | 边界态 | 加载失败 → 错误页 + 重试；离线 → 提示；大字号 → 网页自身缩放（原生不跟随）；横屏 → WebView 重排。 |
| 11 | 无障碍 | 返回/刷新键有 label；WebView 语义由 H5 提供。 |
| 12 | 依据 | E0 `ui/web/web_view_page.dart:472-476,1085,1162,1365,1396-1406`；E0 `ui/web/local_web_screen.dart:136-143`；E0 `ui/web/web_handle.dart:67-146`；E0 `ui/shell/home_shell.dart:236-247`。 |
| 13 | 反证 + 验收 | **反证**：若把 DSH Web 直接塞进聊天 App 的同一页面栈，系统返回会先退 App 而不是网页内导航；若 WebView 允许 overscroll 链，网页滚到底会拖着整个页面晃。**验收** GO-66：真机打开网页版、网页内导航、返回先退网页再退 App。 |

### 2.30 语音通话（候选新面板）（面板 ID：`speech`；归位：myco 无）

#### `speech.call` — 语音通话（玻璃球 / 字幕 / 静音挂断扬声器 / 就地审批）　（findings 31）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `speech.call`。所属：聊天面板→语音通话（全屏页）。出现条件：顶栏通话键（`ui/chat/chat_screen.dart:2004`→`_callFromHeader`）。 |
| 2 | 网格与定位 | 全屏：暗场/人物背景 + 中央玻璃球 + 下方字幕 + 底部三键（静音/挂断/扬声器）+ 审批/提问卡浮在上方（`ui/speech/voice_call_page.dart:1-8`）。 |
| 3 | 命中区 | 底部三键 44+；审批卡按钮同 `approval_card`；`maxHeight` 3 处（`call_parts.dart:459`、`voice_call_page.dart:650-653`）。 |
| 4 | 手势 | 单击 = 静音/挂断/扬声器/回答审批；长按 = 无；上滑 = 收起字幕（未见）。 |
| 5 | 行为 | 通话 → 语音对话（TTS/ASR）；审批 → 就地回答；挂断 → 回到聊天并保留记录。 |
| 6 | 视觉状态 | `listening`（波形）/`speaking`（球呼吸）/`muted`/`error`。 |
| 7 | 布局占位与展开形态 | 全屏路由（G-03 C），不占聊天布局。 |
| 8 | 滚动语义 | 无滚动（全屏固定）；字幕区截断。 |
| 9 | 动效 | 玻璃球呼吸 2.4 s（`AppMotion.breath`）；波形跟音量。 |
| 10 | 边界态 | 无语音模型 → 入口不出现/提示去设置；网络差 → 重连；大字号 → 字幕增高；横屏 → 元素重排。 |
| 11 | 无障碍 | 三键有 label；通话状态播报。 |
| 12 | 依据 | E0 `ui/speech/voice_call_page.dart:1-8,490-495,650-676,913`；E0 `ui/speech/call/call_parts.dart:459`；E0 `ui/speech/call/call_backdrop.dart:178-184`；E0 `ui/chat/chat_screen.dart:2004`。 |
| 13 | 反证 + 验收 | **反证**：若通话时审批弹回聊天页，用户正在说话却被强制跳页（dsh-Go 把审批卡搬进通话页）；若挂断不清状态，下次进聊天会看到"通话中"。**验收** GO-64：真机发起通话、静音/扬声器/挂断、就地回答审批。 |

### 2.31 社区（候选新面板）（面板 ID：`community`；归位：myco 无）

#### `community.page` — 社区 / 求星提示　（findings 9）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `community.page`。所属：社区面板（myco 无此概念）。`community_page.dart` + widgets + `star_prompt_dialog.dart`（求 star 弹窗）。 |
| 2 | 网格与定位 | 列表页卡片 + 弹窗；`minimumSize` 5 处（`community_widgets.dart:190-198`、`star_prompt_dialog.dart:124`）。 |
| 3 | 命中区 | 卡整块可点；弹窗两键整块可点。 |
| 4 | 手势 | 单击 = 打开链接/进子页；弹窗 = 稍后/去 star。 |
| 5 | 行为 | 外链 → `url_launcher` 打开；star 弹窗频率受控（`star_prompt_host`，`ui/star`）。 |
| 6 | 视觉状态 | `default`/`pressed`；弹窗遮罩。 |
| 7 | 布局占位与展开形态 | 参与布局流；弹窗 overlay。 |
| 8 | 滚动语义 | 页面滚；到边不转交。 |
| 9 | 动效 | 弹窗进/出 380/260 ms。 |
| 10 | 边界态 | 网络失败 → 卡片提示；大字号 → 卡换行；横屏 → 限宽。 |
| 11 | 无障碍 | 卡/按钮 label。 |
| 12 | 依据 | E0 `ui/community/community_page.dart:88-211`；E0 `ui/community/community_widgets.dart:110-198`；E0 `ui/star/star_prompt_dialog.dart:124-129`；E0 `ui/star/star_prompt_host.dart`。 |
| 13 | 反证 + 验收 | **反证**：若求 star 弹窗每次启动都出，用户会被反复打断（dsh-Go 用 host 做频率控制）。**验收** GO-67：真机社区页可进、求星弹窗可关闭且不重复弹。 |

### 2.32 回放（候选新面板）（面板 ID：`replay`；归位：myco 无）

#### `replay.page` — 回放（会话回放 / 分享码播放器）　（findings 9）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `replay.page`。所属：回放面板。`replay_page.dart`（回放列表/播放）+ 顶层 `share_code/replay_viewer.g.dart`（生成的 Web 播放器）。 |
| 2 | 网格与定位 | 列表页 + 播放页；`minimumSize` 8 处（`replay_page.dart:300,473,570`）。 |
| 3 | 命中区 | 列表行整行可点；播放控制键整块可点。 |
| 4 | 手势 | 单击 = 播放/暂停/拖进度；长按 = 速度（未见）。 |
| 5 | 行为 | 回放 → 按时间轴重演消息；分享码 → 打开 Web 播放器。 |
| 6 | 视觉状态 | `playing`/`paused`/`ended`。 |
| 7 | 布局占位与展开形态 | 参与布局流；播放页全屏。 |
| 8 | 滚动语义 | 列表滚；播放页内滚；到边不转交。 |
| 9 | 动效 | 播放进度动画；键切图标 160 ms。 |
| 10 | 边界态 | 无回放数据 → 空态；损坏 → 错误；大字号 → 行高增；横屏 → 限宽。 |
| 11 | 无障碍 | 控制键 label；播放进度播报。 |
| 12 | 依据 | E0 `ui/replay/replay_page.dart:120-156,217,253-298,375-391,473,534,570`；E0 `share_code/replay_viewer.g.dart`（生成的 Web 资源，见 §3 排除）。 |
| 13 | 反证 + 验收 | **反证**：若回放只是把消息一次性列出来，用户看不到"当时是怎么一步步生成的"；若播放器只做 Web 版，手机上分享码打不开。**验收** GO-68：真机回放可播放暂停、分享码可打开。 |

---

## 3. 排除清单（18 行）

> 每一条都给出**为什么不是交互点**。扫描器的 pattern 会命中 Dart 字符串/注释里的 Web 内容，这类必须逐条说明。

### 3.1 非产品 UI（死代码 / 开发探针 / 生成物）

| kind | 路径:行 | 理由 |
|---|---|---|
| `dart.widget` | `dev/pet_actions_probe_main.dart:400` | 开发探针入口（`dev/pet_actions_probe_main.dart` 的 main 只为注入宠物事件，不是产品 UI；全仓无 import 引用） |
| `dart.widget` | `dev/pet_actions_probe_main.dart:401` | 开发探针入口（`dev/pet_actions_probe_main.dart` 的 main 只为注入宠物事件，不是产品 UI；全仓无 import 引用） |
| `dart.widget` | `pets/pet_voice_dialog.dart:91` | 旧/孤立对话框：全仓 grep 无 import 引用（现产品路径在 `ui/pets/*`）；按死代码排除 |
| `container.scroll` | `share_code/replay_viewer.g.dart:1663` | 生成物（`.g.dart`，内嵌 HTML/JS 播放器字符串）；`window.scrollTo` 是网页内滚动，不是 Flutter 交互点 |
| `container.scroll` | `share_code/replay_viewer.g.dart:1679` | 生成物（`.g.dart`，内嵌 HTML/JS 播放器字符串）；`window.scrollTo` 是网页内滚动，不是 Flutter 交互点 |
| `container.scroll` | `share_code/replay_viewer.g.dart:1713` | 生成物（`.g.dart`，内嵌 HTML/JS 播放器字符串）；`window.scrollTo` 是网页内滚动，不是 Flutter 交互点 |
| `container.scroll` | `tavern/tavern_canvas.dart:378` | Dart 字符串里的 HTML 模板（`window.scrollTo`），不是 Flutter 交互点；真实现见 `ui/tavern/*` |
| `aria.role` | `tavern/tavern_canvas_host.dart:103` | Dart 字符串里的 JS 选择器（`aria.role` 匹配 `[role=button]` 等），不是 Flutter 交互点 |
| `native.anchor` | `tavern/tavern_storybook.dart:213` | EPUB 生成器里的 HTML `<a href>` 模板字符串，不是 Flutter 交互点 |

### 3.2 注释 / 文档行（不是可交互代码）

| kind | 路径:行 | 理由 |
|---|---|---|
| `dart.gesture` | `ui/common/overview_card.dart:35` | 注释/文档行（非可交互代码）；对应实现见该节点 §2 的依据行 |
| `dart.gesture` | `ui/common/pressable.dart:21` | 注释/文档行（非可交互代码）；对应实现见该节点 §2 的依据行 |
| `dart.gesture` | `ui/common/pressable.dart:26` | 注释/文档行（非可交互代码）；对应实现见该节点 §2 的依据行 |
| `dart.gesture` | `ui/theme/glass.dart:18` | 注释/文档行（非可交互代码）；对应实现见该节点 §2 的依据行 |
| `native.details` | `ui/chat/render/markdown_blocks.dart:1` | 注释/文档行（非可交互代码）；对应实现见该节点 §2 的依据行 |
| `native.details` | `ui/chat/render/markdown_blocks.dart:669` | 注释/文档行（非可交互代码）；对应实现见该节点 §2 的依据行 |
| `native.details` | `ui/chat/render/markdown_body.dart:39` | 注释/文档行（非可交互代码）；对应实现见该节点 §2 的依据行 |
| `native.details` | `ui/chat/render/markdown_body.dart:41` | 注释/文档行（非可交互代码）；对应实现见该节点 §2 的依据行 |
| `native.details` | `ui/chat/render/markdown_split.dart:19` | 注释/文档行（非可交互代码）；对应实现见该节点 §2 的依据行 |

> 注释行共 9 条；其中 `ui/chat/render/markdown_blocks.dart:1,669` 与 `markdown_body.dart:39-41` 的 `<details>` 注释**有真实实现**（`MarkdownDisclosure`，见 §2 `chat.msg.assistant-markdown`），只是命中的是注释行，实现本体已另在字段 12 取证。

---

## 4. 待确认（E2 候选，编号 Q-GO-01 起）

> G-00：以下都是**推测/需要拍板**的取舍，不许进实现。已在 dsh-Go 里找到对应做法，但"我们抄不抄"要用户定。

| ID | 问题 | dsh-Go 的做法（E0） | 与 myco 现状/规则的冲突 | 需要谁拍板 |
|---|---|---|---|---|
| **Q-GO-01** | 内滚到边后是否把剩余拖动交给外层？ | **交**（`OverscrollForwarder`，`OF:8-47`；只交拖动、不交 fling 速度） | 与铁律 **G-04** 相反 | 用户（已在 open-questions Q-04 同族） |
| **Q-GO-02** | 最小命中区取多少？ | 行级 **32/44/48**，图标级 **36~44**，48 只给链接/输入/主操作（§5） | Q-03 待拍板；与 Flutter 官方 48dp 建议不完全一致 | 用户（Q-03 第二输入） |
| **Q-GO-03** | 整轮过程折叠用形态 A（不封顶 + 滚动补偿）还是 DSH Web 的 B（封顶内滚）？ | **A**：展开无 `maxHeight`，动画每帧补偿滚动位置（`TP:129-151`） | G-03 目前把"过程组"归为 B；手机端证据指向 A | 用户 |
| **Q-GO-04** | 可展开块的高度动画？ | 思考行展开 **160 ms** `SmoothSize`（`TH:219-229`）；整轮折叠 **280 ms** `SizeTransition`（`TP:65-78`） | 与 **G-10**（>240dp 直接切换）可能冲突——需实测手机帧率与"是否必须等动画" | 用户 |
| **Q-GO-05** | 思考行的折叠形态？ | 不是 24px 定高：**32dp 一行**（24 图标 + 上下 4），流式中下面是**恒 3 行**实时窗口，写满一行整体上滑（`TH:389-440`） | 金标准样本 `chat.msg.thinking-header` 写的是 24px + 摘要；dsh-Go 的流式形态是"三行窗口" | 用户 |
| **Q-GO-06** | 工具行折叠后要不要保留卡片？ | 带原生卡片的调用：工具行收进过程、**卡片留在外面**（`ui/chat/chat_rows.dart:318-324`） | 与 DSH Web 的"卡片在组内"不同 | 用户 |
| **Q-GO-07** | 窄屏还要不要"轮次导航条"（turn rail）？ | **未见实现**：手机聊天页没有右侧轮次刻度——全仓 grep `TurnNavigator` / `turn_rail` = **0 命中**，聊天里导航只有回底钮（`CL:1667-1786`） | DSH Web 是 hover 出预览、≤900px 隐藏（见 `dsh-chat.md §0 #6`）；手机端 dsh-Go 直接不做 | 用户（Q-07 已有 DSH 一半答案） |
| **Q-GO-08** | 二级页返回手势与系统返回的优先级？ | 预测性返回 + 20dp 边缘滑动（`PT:428-442`），浮层/多选优先（`HS:319-333`） | myco 旧实现是单层 `BackHandler` | 用户 |
| **Q-GO-09** | 主题 token 是否照 dsh-Go 的 GlassSurface 三级 + 降级？ | 是（`GL:12-39`），同屏真模糊 ≤3 | 与 myco "减少透明效果"设置的关系待定 | 用户 |
| **Q-GO-10** | 大字号时横屏键盘让顶栏/dock 让位是否接受？ | 接受：`typing` 时 header/dock 淡出折叠（`CS:1841-1859,2498-2559,2565-2606`） | myco 未定义该行为 | 用户 |
| **Q-GO-11** | `ui/theme/`（真实 token）与顶层 `themes/`（主题包数据）如何归位？ | `ui/theme/*` 是 token 实现；顶层 `themes/`（3 dart：`theme_pack`/`theme_library`/`theme_pack_validator`）是主题包模型与校验，**无交互点** | 归位已写清（§1.2），无需拍板 | — |
| **Q-GO-12** | 哪些"候选新面板"（酒馆/伙伴/手机操控/Bot/工坊/论坛/终端…）进 myco 首版？ | 全部有完整原生实现（§2 已归位） | 都不在 `10-panels.md` 首版范围；P2 决定 | 用户 |

> 编号规则：`Q-GO-xx` 只在本路使用；P2 归并进 `open-questions.md` 时统一重排。

---

## 5. Q-03 命中区直接答案（dsh-Go 实测口径）

> 回答 Q-03 的第二个输入：**真手机客户端到底用多大命中区、怎么在视觉不变的前提下扩边、与相邻元素怎么隔离**。
> 全部 E0。

### 5.1 实际尺寸（按频次与场景）

| 尺寸 | 用在哪（E0） | 备注 |
|---|---|---|
| **32** | 过程行最小高：`StreamRow.minHeight=32`（`SR:29-30,46-68`）；工具行/思考行/过程摘要行 | 视觉高 32，**低于 44/48**；靠横向 ±8 扩宽（5.3-b）与行间 4+4 内边距隔离 |
| **36** | 回复操作条图标（`ui/chat/widgets/message_actions.dart:212`，`shrinkWrap` 同一行 `:99-105`）；输入条主键（发送/麦克风/停止，`CO:175-176,976-979`）；＋ 附件键（`ui/chat/widgets/attachments.dart:26,54-56`，注意注释写「40dp」但代码是 36）；「对话列表」圆键（`ui/shell/drawer/drawer_filter_bar.dart:82-83`） | 密集图标区取 36，靠 6~8 间距隔离；**这是 dsh-Go 最激进的取值** |
| **40** | `TextButton` 全局最小高（`AT:297-303`）；若干 `minimumSize(0,40)`（`ui/chat/balance_widgets.dart:394-421`、`ask_ai.dart:105`） | 文本按钮 |
| **44** | `IconButton` 全局最小（`AT:306-308`）；会话行（`ui/shell/drawer/session_tile.dart:51`）；回底钮（`CL:1678`）；顶栏/rail 圆键（`AL:81-83`）；分段控件最小高（`AT:321-331`）；「查看全部」条（`LT:283-284`）；空态按钮（`ui/common/empty_view.dart:75`） | **行/整块件的标准值** |
| **48** | `kMinInteractiveDimension`：正文内链接热区补到 48（`ui/remote/relay_credit.dart:135-139,236-259`）；搜索框 `minHeight:48`（`AT:333-341`）；聊天里的整块动作键（`ui/chat/widgets/notice_row.dart:264`、`ui/chat/widgets/background_task_notice.dart:67` 48×48）；插件控件 48（`ui/chat/dsh_ui/dsh_ui_plugin_controls.dart:109-118,522`） | **只给"主操作/输入/文字链接"**，不给密集列表图标 |
| **50 / 52 / 56** | 页面主按钮 50（`AT:251-295`）；**选择器行 52**（斜杠面板 `ui/chat/slash_palette.dart:242`、@ 面板 `ui/chat/mention/mention_picker.dart:186`）；房间名输入 56（`ui/bots/new_room_sheet.dart:149`） | 选择器行反而比聊天过程行高——因为它是"独立决策点" |
| **64** | 抽屉宽 320（`AL:89`）、面板 320~420、聊天列最小 400（`AL:92-109`）；主按钮最小宽 64（`AT:258`） | 布局尺寸，不是命中区 |

### 5.2 结论（可直接抄进 Q-03 的判据）

1. **不是「全部 48dp」**：dsh-Go 的移动端口径是 **行级 44（会话行/设置行/回底钮）**、**过程行 32（但整行可点 + 横向扩 8）**、
   **图标级 36（回复操作条/输入键）**；**48 只出现在"正文里的链接、输入类控件、主操作按钮、插件控件"**。
2. **行级可点优先于按钮级扩边**：设置/列表页把整行做成点击目标（开关行用 `SwitchTheme.materialTapTargetSize: shrinkWrap`
   让整行接管，`ui/common/list_page_row.dart:214`、`ui/common/overview_card.dart:155`、`ui/providers/provider_model_page.dart:526`），
   而不是把 40×22 的开关补到 48。
3. **没有出现 `MaterialTapTargetSize.padded` 的大规模使用**：全仓 `MaterialTapTargetSize.shrinkWrap` **48 处** vs
   `MaterialTapTargetSize.padded` **4 处**——密集列表主动**放弃** 48 的平台默认，换成"行高 36~44 + 行间距"
   （`kMinInteractiveDimension` 全仓仅 4 处，用于链接热区/圆键上限，见 §5.1）。

### 5.3 视觉不变前提下扩大热区的 3 种真实做法（全部 E0）

| 做法 | 实现 | 代价 / 约束 |
|---|---|---|
| **a. 整行承载** | 行 `InkWell`/`GestureDetector` 包住整行，行高给到 44（会话行 `session_tile.dart:88-118`；过程行 `SR:46-68`） | 需要行高本身 ≥32；行内再放独立小按钮会互相抢 |
| **b. 自绘 RenderProxyBox 扩 hitTest（视觉完全不变）** | `_RenderLinkHitArea.hitTest()` 把链接所在行上下扩到 `kMinInteractiveDimension`，命中后把点击**转发到文字的锚点**（`ui/remote/relay_credit.dart:174-259`）；注释明确要求祖先放行（`:135-139`） | 只对"文字内片段"值得做；需要知道段落几何，实现成本高 |
| **c. 横向 Bleed（扩宽不扩高）** | `Bleed` 让子组件按"列宽 + 2×8"布局、画在 −8 处，`_RenderBleed.hitTest` 把 `[-bleed, width+bleed)` 都算命中（`BL:6-13,40-47,87-96`） | 只解决横向（首尾贴正文缘），不解决竖向 32→44 |

> **反证（若照抄桌面版）**：桌面 hover 区可以 24~28px 且靠光标精度；手机上 24px 行高 + 20px 图标意味着
> **连续两次点不中**就放弃——dsh-Go 把过程行做成 32 且整行可点、把会话行做到 44，正是为手机补这一档。
> **但仍有一处明显低于平台建议**：回复操作条 36×36 与输入条 36×36（`shrinkWrap`），说明"密集图标区"在该产品里是
> **被接受的取舍**——这是 Q-03 需要用户拍板的关键分歧点（Q-GO-02）。

### 5.4 与相邻元素的隔离

- 过程行：行内上下各 4 + `Bleed` 横向 8（`SR:46-68`），行与行之间由行外 `Padding` 给（`CL:919-920` 注释）；
- 回复操作条：36 键**相邻净距 0**，靠图标 20 + 每键 8 内边距形成视觉间距（`ui/chat/widgets/message_actions.dart:96-105`）；
- 输入条：主键 36 + 胶囊上下 6/左右 8（`CO:175-176,537-542,603-606`），与文字区间距 8；
- 会话行：44 高、左右 10（`session_tile.dart:115-118`），与上下行 0 净距（同列表项）；
- 回底钮：距输入条上沿 12、距阅读列右缘 12（`CL:1097-1101`）。

---

## 6. G-04 滚动物理直接答案（内滚到边后速度是否外传）

> 回答重点 1：**Flutter 的滚动物理会不会把剩余速度传给外层？** → **默认不会；dsh-Go 是"手动加回来的"，而且只加拖动、不加惯性。**
>
> **依据级别**：dsh-Go 的实现与 API 用法 = **E0**；Flutter 框架内部语义（手势竞技场归属、clamping/bounce 是否发
> `OverscrollNotification`）= **E1**（上游框架行为；本机无 Flutter SDK 可查源码，由 dsh-Go 注释与实现互为印证，
> 已在条目内标注）。结论不依赖 E1 的部分都写死了 E0。

### 6.1 事实链（E0 标注在条目内，框架语义标 E1）

1. **嵌套滚动默认不传递（E1，注释声称 + 实现需求印证）**：内层 `Scrollable` 在手势竞技场里赢下这次拖动，外层消息列表
   **接不到**这次手势——这正是 `OverscrollForwarder` 存在的理由，注释原文："内层滚动件会抢先赢下手势竞技场，手指按在内层上拖，
   外层消息列表永远接不到这次拖动 —— 哪怕内层已经滚到了头"（`OF:8-13`）。**E0 侧证据**：整个 forwarder 的实现只做
   "把内层通知转给外层"，如果框架本来就会传递，这段代码没有存在必要。
2. **dsh-Go 的选择是"到边让路"**（`OF:14`）：它监听内层的 `OverscrollNotification`，把**未被消费的拖动增量**
   `clamp` 到外层位置后 `jumpTo` 转交（`OF:24-37`）。三个使用点：代码井（`ui/chat/render/code_block.dart:581`）、
   diff 井（`ui/chat/render/diff_view.dart:166`）、长文本井（`LT:202-210`）。
3. **只转"拖动剩余量"，不转"惯性速度"**：
   - E0：`OverscrollNotification` 携带的是 `overscroll`（本次拖动未消费的像素增量），转发用 `position.jumpTo(target)`
     （`OF:29-35`）——`jumpTo` **没有 velocity 参数**，且会终止外层正在进行的 ballistic 动画；
   - E1（框架语义）：clamping 物理下惯性（ballistic）到边界即停、位置被夹在 `[min,max]` 内，不产生越界像素 →
     不产生 `OverscrollNotification`；dsh-Go 的 iOS 分支注释也印证"bounce 不发 overscroll"（`OF:38-39`）。
     因此在 dsh-Go 的配置下**不存在"内层 fling 到边 → 外层继续滑"的链路**；即使某平台把惯性也算成 overscroll，
     转发仍走 `jumpTo`，只搬位置、不搬速度。
   - 方向锁（E0）：只接管 `metrics.axis == Axis.vertical`（`OF:26-27`），代码块横向到边不会拿去竖着晃消息列表。
4. **iOS 特殊情况被显式处理**：bounce 物理**不发** `OverscrollNotification`，所以 iOS 上给内层强制
   `ClampingScrollPhysics(parent: RangeMaintainingScrollPhysics())`（`OF:38-46`），Android 默认就是 clamping。
   同一个坑在 `motion.dart` 里写成硬约束：**"别用在聊天列表和它里面的滚动区：回弹不发 OverscrollNotification，
   `OverscrollForwarder` 会失效"**（`MO:177-180`）——`AppMotion.scrollPhysics` 只在"灵动档 + 非聊天滚动区"返回
   `BouncingScrollPhysics`（`MO:179-180`）。
5. **可禁用/可替换的 API 都在用**：
   - `ScrollConfiguration.of(context).copyWith(overscroll: false)` 关掉 Material 的**拉伸指示器**（不是通知），
     因为拉伸会把首行画移但 pixels 不动（`CL:1052-1054` 注释 + 用法）；
   - `NeverScrollableScrollPhysics`：表格冻结列/表头的同步滚动（`ui/files/table_view.dart:217-243`）、图片缩放时禁翻页
     （`ui/chat/widgets/image_gallery.dart:58`）；
   - `AlwaysScrollableScrollPhysics`：下拉刷新页（`ui/workshop/workshop_screen.dart:90-93`、`ui/plugins/plugins_screen.dart:82-85`、
     `ui/companion/companion_chrome.dart:309`、`ui/session/workspace_files.dart:51`）——内容不满屏也能下拉；
   - 下拉刷新用手势监听而非 `RefreshIndicator`：`companion_chrome.dart:351-382` 监听 `OverscrollNotification` + `ScrollMetricsNotification`。
6. **聊天列表自己到边不外传**：列表是消息流的滚动 owner，页面根不是 Scrollable，所以它的 overscroll 被平台 clamping 吸收
   （`CL:1051` 只给它 `_FollowPhysics`，没有再包 forwarder）。→ **"内滚→外层"只有一层**：井 → 会话列表；会话列表之上没有第三层。

### 6.2 对 G-04 的判定

| 问题 | 答案 |
|---|---|
| Flutter 默认会把剩余速度传给外层吗？ | **不会**（E1：嵌套 Scrollable 内层赢竞技场，外层拿不到这次手势；E0 印证：`OF` 整个实现就是为补这一空缺）。 |
| dsh-Go 把残余拖动传给外层吗？ | **会**，而且是**设计目标**（E0 `OF:14` 注释"到头让路"），仅纵向、仅拖动增量。 |
| 传的是"速度/惯性"还是"位移增量"？ | **位移增量**（E0）：`jumpTo` 无速度（`OF:29-35`）；惯性不外传（E1 框架语义见 6.1-3）。 |
| 能禁用吗？ | 能：不包 `OverscrollForwarder` 即恢复 Flutter 默认（内层到底即停）；也可用 `NeverScrollableScrollPhysics` 彻底交给外层（表格冻结列用法）。 |
| 与 G-04 矛盾吗？ | **矛盾**：G-04 要求"到边吸收、只有新手势才允许外层接管"，dsh-Go 是"同一手势内继续拖就交给外层"。 |
| 为什么 dsh-Go 这么选？ | 反证写在注释里：若不转交，手指按在内层井上拖到边后**页面看起来卡住了**（内层赢竞技场，外层永远不动）——这是"软件没猜中意图"的另一面。 |

### 6.3 结论（一句话给 P2）

**G-04 在 dsh-Go 是「用户偏好级」选择，不是「手机端共识」**：真手机客户端同样遇到"内层吃掉手势"的问题，
但 dsh-Go 的解法是**同手势内把剩余拖动让给外层**（且严格只让拖动、不让惯性），而不是 G-04 的"吸收 + 等新手势"。
两者都消灭了"页面乱滑"症状，差别在**同一次手势是否允许换作用对象**——这正是需要用户拍板的语义（Q-GO-01）。
附带的工程事实（可直接复用）：① 要让内层在 iOS 上也吐 `OverscrollNotification`，必须强制 clamping；
② 聊天列表及其内层禁止 bouncing；③ Material 的 overscroll 拉伸指示器要单独关（`copyWith(overscroll:false)`）。

# 移动端 agent/chat UI 行为拆解（2026-11-26）

> 目标：给 myco（Kotlin/Compose Android 陪伴/agent chat 基座）做**组件级 + 行为级**对照。只拆交互逻辑、功能、状态、边界与数据契约，**不拆像素级视觉**。
> 证据口径：`[官方]` 官方帮助/文档/官方仓库源码 · `[截图实测]` 本地 `shots/` 12 张截图逐张读过 · `[仓库文档]` 开源项目官方文档 · `[社区]` 第三方指南/媒体 · `[推断]` 我的判断，未在真机验证。
> 约束：未调用任何模型 API、未 clone/安装、未改 myco 代码；GitHub 直连不可达处走 jsDelivr 镜像读同一份文件（`@master`/`@main`，复核请固定 commit）。
> 上一轮 `docs/recon/mobile-chat-ux.md` 已覆盖"形态/导航/Compose 配方"，本报告不重复，只补**组件状态机与数据需求**。
> 覆盖对象：产品 4 个（DeepSeek App、ChatGPT、Claude、DSH Go）+ 开源实现 3 个（deepseek-ai/chat-demo、S-Neur/deepseek-api-app、rikkahub/rikkahub）。

## 0. 一页结论

1. **顶部栏在"去标题化"**：ChatGPT 不显示会话名（浮动玻璃圆钮压在内容上）；DeepSeek 是「汉堡 + 左对齐标题 + 朗读类图标 + 新对话」；DSH Go 是「标题胶囊（标题 + `● 本机 · workspace` 副行）+ 电话 + 新对话」。标题职责从"显示名字"变成"显示上下文状态 + 点进详情"。
2. **抽屉宽度都在 82–85%**，都保留右侧页面一条可见边；搜索是抽屉第一等公民（DeepSeek 直接写"搜索对话内容"，即搜正文）。
3. **会话行主入口是长按**（DeepSeek 重命名/置顶/删除；DSH Go 七项），但成熟实现都另有 `⋯`/••• 备入口；DSH Go 还有「全部/运行中/待确认/未读」状态筛选与文件夹排序。
4. **助手消息不用气泡**（三家全宽文本 + markdown），气泡只给用户；DSH Go 在消息尾部放角色头像，靠"时长·步数"footer 区分轮次。
5. **消息操作条常显**（复制/赞/踩/朗读/分享 + 重新生成），长按是"完整菜单"的加速器而非唯一入口；ChatGPT 官方明确 Android 选择文字走系统选择菜单。
6. **重新生成有三种语义**：替换当前（DeepSeek/ChatGPT 的直觉）、原位加版本 `‹1/3›`（RikkaHub/chat-demo）、**开新对话继续**（DSH Go：重新回答/分叉/编辑后重发都进新会话，原对话不变）。
7. **思考/工具必须折叠**：DSH Go"完成后收起过程"默认开、思考块恒高 3 行可点开；Claude 是回答上方"Thinking"折叠节 + 计时；DSH Go footer 带「耗时 · 步数」可点开步骤面板。
8. **输入区是状态机**：空输入→语音/麦克风；生成中→停止；有排队→队列条；DSH Go 长按发送键选「插话/中断并发送/排队」，撤回内容回输入框。
9. **附件面板三种形态**：内联展开（DeepSeek，+ 变 ✕，最近照片 + 拍照/相册/文件）、锚定 + 的弹出菜单（ChatGPT，含插件/深入思考开关）、ModalBottomSheet（DSH Go，含"本对话"权限/计划模式）。
10. **撤销是标配**：DSH Go 归档、删除分享链接都 4 秒可撤销，永久删除二次确认且"不偷偷改成归档"；反例 RikkaHub 删除无回收站（官方明说）。
11. **数据侧最缺的是"会话状态"**：DSH Go 筛选依赖 `运行中/待确认/未读`；操作条依赖 `版本/分叉/耗时/步数`；输入区依赖 `context 用量/排队消息/权限模式`。
12. **对 myco 的硬结论**：当前 0 抽屉、0 Markdown、0 消息操作条、0 折叠过程、0 撤销。先做「抽屉 + 全文搜索 + 长按菜单 + 行内操作条 + 思考折叠 + 撤销条 + 输入区状态机」，再谈视觉。

## 1. 参考截图逐张分析（12 张）

> 尺寸均为 1200×2670px，按 3x 估为 400×890dp 的直屏。宽度百分比按 837px 预览图里的抽屉右缘估算，误差 ±3%。

### 1.1 `deepseek chat聊天窗口.jpg`
- 顶栏：无底色、浮在内容上；左汉堡（线性）、**左对齐**标题「AI率指标异化」（单行截断）、右侧「扬声器斜杠」图标 + ⊕「新对话」；**没有**会话级 `⋯`。`[截图实测]`
- 消息区：助手消息全宽无气泡；H2「一个关键区分」大号加粗；有序列表**悬挂缩进**（第二行缩进对齐文字而非编号）；段距约一行；结尾小字斜体免责声明。`[截图实测]`
- 操作条常显在免责声明下：复制 / 👍 / 👎 / 朗读 / 分享，最右端单独一个「重新生成」。`[截图实测]`
- 输入区：一个大圆角胶囊 ≈两行高——上排 placeholder「发消息或按住说话」，下排 chips「深度思考」「智能搜索」（智能搜索蓝底高亮=开）+ ⊕ + 语音圆钮；空输入时**没有发送键**。`[截图实测]`
- 底部：再一行「回答由 AI 生成，仅供参考」+ 手势条。`[截图实测]`
- 数据需求：`thinking_enabled`、`web_search_enabled`、语音输入、TTS 朗读、`regenerate`、反馈打分。`[推断]`

### 1.2 `deepseek chat左侧多会话管理.jpg`
- 抽屉宽 ≈82%（≈330dp/400dp），右侧露出聊天页一条边。`[截图实测]`
- 顶部搜索框「搜索对话内容…」→ 搜的是**消息正文**，不只标题。`[截图实测]`
- 分组：置顶（右上"管理置顶/排序"滑杆图标）→ 今天 → 昨天；当前项蓝底蓝字 + 右侧 `⋯`，其余行只有单行标题（**无时间、无预览、无未读点**）。`[截图实测]`
- 底部账户区：头像 + 昵称「星湖鸭鸭NS」+ `⋯`。抽屉内**没有**新建对话（新建在聊天页顶栏 ⊕）。`[截图实测]`
- 状态：默认 / 选中 / 分组折叠不可见（未截到）。行间有分隔线。`[截图实测]`
- 数据需求：`pinned_at`、`updated_at`（分组）、`title`、`selected_id`、账户资料。`[推断]`

### 1.3 `deepseek chat设置页.jpg`
- 顶栏：圆形返回钮（玻璃底）+ 居中「设置」。`[截图实测]`
- 一节一卡、卡内行间**无分隔线**：账户（账号管理/数据管理）、应用（语言→"中文(简体中文)"、外观→"系统"、字体大小、个性化）、语音（朗读音色）、关于（检查更新→"2.8.0(297)"、服务协议）；底部独立「帮助与反馈」卡。`[截图实测]`
- 行 = 左 icon + 标题 + **右值即状态** + `›`；无设置搜索。`[截图实测]`
- 数据需求：语言/主题/字号/音色持久值、版本号、检查更新、帮助跳转。`[推断]`

### 1.4 `deepseek chat文件上传.jpg`
- 点 ⊕ 后：⊕ 变 ✕，输入区下方**内联展开**面板（不是 ModalBottomSheet，页面不压暗、键盘不弹）。`[截图实测]`
- 面板上部是最近照片横向缩略图（带圈选标记），下部三个大按钮：拍照 / 相册 / 文件（图标 + 文字）。`[截图实测]`
- 限制：单文件 ≤100MB、移动端单次 ≤20 个（`[社区]`，未在官方 FAQ 找到，见 §6）。`[社区]`
- 数据需求：最近媒体权限、附件队列、上传进度/失败态。`[推断]`

### 1.5 `ChatGPT聊天框.jpg`
- 顶栏**无标题**：左汉堡在玻璃圆钮里，右侧一个玻璃胶囊把「新建(✎) + ⋯」包在一起；栏浮动，**内容从栏下滚过**（顶部文字被遮住可见）。`[截图实测]`
- 助手消息全宽无气泡；列表悬挂缩进；emoji 原样。`[截图实测]`
- 操作条：复制 / 👍 / 👎 / 朗读 / 分享 / `⋯`（6 个），无显式"重新生成"图标。`[截图实测]`
- 输入区：单行玻璃胶囊——左 ⊕、placeholder「回复 ChatGPT」、右侧麦克风 + 蓝色圆形语音键（波形）；空输入无发送键。`[截图实测]`
- 数据需求：会话级 `⋯`（删除/锁定）、语音模式、朗读、行内反馈。`[截图实测]` + `[官方]`
- 官方：Android 选文字用系统选择菜单，复制/朗读/分享用行内按钮或 •••；删除会话走 ••• → Delete + 确认。`[官方]` [ChatGPT mobile](https://help.openai.com/en/articles/8142208-using-the-chatgpt-mobile-app-classic-experience)

### 1.6 `ChatGPT左侧多会话管理.jpg`
- 抽屉宽 ≈82%，右侧聊天页被压暗。`[截图实测]`
- 顶部大标题「ChatGPT」+ 右侧圆形搜索钮；导航清单：图像/资料空间/自定义/项目/站点/查看更多。`[截图实测]`
- 分隔线后会话列表：单行标题、**无时间/预览/未读/分组头/`⋯`**；选中行整行浅色高亮。`[截图实测]`
- 底部：蓝色胶囊 FAB「聊天」（新建）+ 圆形设置齿轮。`[截图实测]`
- 官方：sidebar 最近会话最多 8 条（2026-08 Android 起）；搜索搜标题与消息、**含归档**、不含锁定会话。`[官方]` [release notes](https://help.openai.com/en/articles/6825453-chatgpt-release-notes)

### 1.7 `ChatGPT设置.jpg`
- 顶栏：圆形返回 + 居中「设置」+ 右侧 i 信息钮。`[截图实测]`
- 卡片：账户组（账户=周子涵 / 切换账户 / 升级至 ChatGPT Pro）→ 自定义 ChatGPT（副标题"个性、记忆、插件等"）→ 常规/用量与限额/通知/语音/安全与身心健康/账户安全与登录。`[截图实测]`
- 行 = 左 icon + 标题 + 右值 + `›`；卡片内行间**有**分隔线。底部固定「搜索设置」输入框（吸底，列表可滚）。`[截图实测]`
- 官方：Android 版本在 About；General 下有 Haptic feedback 开关。`[官方]`（同上文）

### 1.8 `ChatGPT文件上传.jpg`
- 点 ⊕ 弹出**锚定在 + 上的圆角菜单**（非 sheet、不压暗）：拍照 / 照片 / 文件 / 插件 / 「深入思考」（右侧 ✓ = 当前开）。`[截图实测]`
- 菜单项 = 圆形 icon 底 + 文字；「深入思考」是**开关型**菜单项（模式而非附件）。`[截图实测]`
- 官方(iOS)：长按 + 直接出最近照片；连接异常会提示「waiting for an internet connection」。`[官方]` release notes 2026-08-21

### 1.9 `dshgo聊天窗口.jpg`
- 顶栏：左汉堡玻璃圆 + 中间**标题胶囊**（双行：标题截断 + `● 本机 · workspace` 绿点状态）+ 右电话（语音通话）玻璃圆 + 新对话玻璃圆。`[截图实测]`
- 助手消息：全宽 markdown，inline code 有胶囊底；粗体、编号列表；消息尾部右下角角色头像。`[截图实测]`
- 操作条：复制 / 分享 / 朗读 / 分叉 / 重新回答 / `⋯`，右端 metadata「1 分 07 秒 · 6 步 ›」（点开步骤面板）。`[截图实测]`
- 输入区：胶囊 + placeholder + 右侧上下文用量圆环「31%」+ 麦克风；空输入无发送键。输入框上方有琥珀色「⚠ 完全权限 ›」芯片。`[截图实测]`
- 官方：完成后过程收起；空输入 + 生成中→停止键；长按发送键→三种发送方式。`[官方]` [对话指南](https://github.com/ccch1mneyyy/dsh-go/blob/master/docs/guide/zh/01-chat.md)

### 1.10 `dshgo左侧多会话管理.jpg`
- 抽屉宽 ≈85%。头部：logo「DSH」+ 三个钮（层叠=主机/工作区、换向=切换主机、蓝色「新对话」）。`[截图实测]`
- 搜索「搜索对话」→ 行「💬 全部对话 ⌄」+ `⋯` → 筛选 chips：全部（白底选中）/运行中/待确认/未读。`[截图实测]`
- 分组：昨天 / 本周；单行标题；选中 = 圆角底色 + 蓝字。`[截图实测]`
- 底部：本机 ▾（设备图标 + `● 运行中` + 上下箭头）+ 全部功能（九宫格）+ 设置齿轮。`[截图实测]`
- 官方：长按行→置顶/重命名/移到文件夹/分享/多选/归档/永久删除；搜索归档可「恢复并打开」；`⋯` 里排序/新建文件夹/管理文件夹。`[官方]`

### 1.11 `dshgo设置.jpg`
- 大字**左对齐**标题「设置」+ 顶部返回圆钮；吸顶「搜索设置」。`[截图实测]`
- 账号卡（头像/昵称/余额 ¥16.64）+ 卡内分隔行「● 本机引擎 · 运行中 · DSH 0.2.2-rc.1」（点进引擎页）。`[截图实测]`
- 分组 AI：模型 / 权限（值"完全权限"，琥珀色）/ 个性化（未设置）；能力：手机操控（"还差一步"，琥珀）/联网搜索（默认）/图像与视频/插件（24 个）。`[截图实测]`
- 行右值直接显示当前状态；**需要用户注意的值用警示色**。`[截图实测]`
- 官方：六组 = AI/能力/外观/通用/主机/帮助与关于；设置搜索支持旧名与口语；改危险项要确认。`[官方]` [设置指南](https://github.com/ccch1mneyyy/dsh-go/blob/master/docs/guide/zh/10-settings.md)

### 1.12 `dshgo文件上传.jpg`
- 点 ⊕ → **ModalBottomSheet**（顶部拖拽柄、大圆角、页面被 scrim 压暗）。`[截图实测]`
- 第一排 5 个彩色磁贴：相册 / 拍照 / 文件 / 生成 / 语音通话（图标+文字）。`[截图实测]`
- 分组「本对话」：卡片内「权限 → 完全权限」「计划模式 [switch]」；分组「手机操控」：确认方式 → 始终免确认。`[截图实测]`
- 官方：每消息 ≤9 附件、单文件 200MB、上传失败不发送、远程不支持文件则隐藏"文件"、同名自动改名。`[官方]`

## 2. 组件总表（组件｜位置｜状态｜手势｜数据需求）

| # | 组件 | 位置/层级 | 关键状态 | 手势 | 数据/事件需求 |
|---|---|---|---|---|---|
| 1 | 顶栏容器 | 聊天页顶层 | 默认/滚动中（浮动或吸顶）/编辑标题中 | 点、长按标题 | `session.title`、`host_status` |
| 2 | 汉堡/抽屉开关 | 顶栏左 | 默认/展开后（ChatGPT 仍可点） | 点、边缘右滑（DSH Go/RikkaHub） | `drawer_state`、返回栈 |
| 3 | 会话标题 | 顶栏中/左 | 显示/截断/重命名中 | 点（信息页或改名）、长按 | title、`auto_title` 标记 |
| 4 | 新建对话 | 顶栏右 / 抽屉底 FAB | 默认/生成中（是否允许） | 点 | 首条消息才建会话 |
| 5 | 顶栏 ⋯ / 更多 | 顶栏右 | 默认/展开 | 点 | 删除/锁定/分享/重命名 |
| 6 | 抽屉容器 | 全局覆盖层 | 关闭/拖拽中/打开/预测返回中 | 汉堡、左缘右滑、点遮罩、返回键 | 当前会话、列表数据 |
| 7 | 抽屉搜索框 | 抽屉顶部 | 空/聚焦/输入中/无结果/搜索中 | 点、输入、清空 | 全文搜索 API（含归档） |
| 8 | 时间分组头 | 抽屉列表 | 今天/昨天/本周/更早/置顶 | 滚动吸顶 | `updated_at`、`pinned_at` |
| 9 | 会话行 | 抽屉/历史页 | 默认/选中/未读/运行中/待确认/长按中/多选中/归档 | 点、长按、`⋯` | id/title/unread/status |
| 10 | 会话行菜单 | 弹层 | 收起/展开 | 长按触发、点项 | 重命名/置顶/归档/删除/多选 |
| 11 | 抽屉底部账户/工具区 | 抽屉底 | 已登录/未登录/主机离线 | 点 | 账户、主机状态、设置入口 |
| 12 | 消息列表 | 聊天页主体 | 空/加载/流式中/失败/停止 | 滚动、长按、双击? | 事件分页、锚点 |
| 13 | 用户消息 | 列表 | 默认/发送中/失败重发/编辑中/选中 | 长按、点编辑 | 原文、附件、`client_id` |
| 14 | 助手消息 | 列表 | 流式中/完成/停止/失败/版本 1..N | 长按、选文字 | 内容块、状态、版本 |
| 15 | 消息操作条 | 消息下方 | 默认/已复制/朗读中/打分后 | 点、长按扩展菜单 | 复制/反馈/TTS/重生成 |
| 16 | 分支/版本切换器 | 消息内联 | `‹1/3›` 首/末（箭头变淡） | 点箭头 | `message_node.versions` |
| 17 | 思考折叠块 | 助手消息上方 | 生成中（恒高 3 行）/完成收起/展开 | 点展开、再点收起 | thinking 文本、时长 |
| 18 | 工具调用块 | 助手消息内 | 折叠/展开/失败 | 点 | tool name/args/result/ok |
| 19 | 步骤·耗时 footer | 消息底部右 | 默认/可点 | 点 | duration、step_count |
| 20 | 权限/审批芯片 | 输入框上方 | 完全权限/自动允许中/待确认 | 点改权限 | permission_mode、`turn_id` |
| 21 | 输入区胶囊 | 底部 | 空/有字/组字中/生成中/附件中/禁用 | 点、长按、上滑 | draft、ime 状态、queue |
| 22 | 附件按钮/面板 | 输入区左 / 底部弹层 | 关闭/展开/上传中/失败 | 点、拖拽柄 | 权限、附件队列、上传进度 |
| 23 | 语音键/长按录音 | 输入区右 | 空闲/录音中/转写中/不可用 | 点、长按、上滑取消 | 麦克风权限、ASR 结果 |
| 24 | 模式 chips | 输入区内/上方 | 关/开/禁用 | 点 | `thinking_enabled`、`search_enabled` |
| 25 | 模型选择 | 输入区下方/顶栏胶囊 | 默认/弹层/加载模型列表/未配置 | 点 | provider/model、能力位（vision/tool） |
| 26 | 发送/停止键 | 输入区右 | 发送/停止/插话/排队/禁用 | 点、长按 | 生成状态机、队列 |
| 27 | 上下文用量指示 | 输入区内 | 正常/偏高/超限 | 点? | token 统计或估算 |
| 28 | 附件 chip 行 | 输入区上方 | 上传中/成功/失败 | 长按? 点×移除 | 附件 id/状态 |
| 29 | 图片查看器 | 全屏覆盖 | 加载/正常/失败/缩放中 | 双击、双指、左右滑、单指拖 | 图片组、页码 |
| 30 | 撤销条 Snackbar | 页面底部 | 4s 倒计时/已撤销/超时 | 点撤销 | 软删 id、动作回执 |
| 31 | 设置页/行 | 二级页 | 正常/值即状态/危险项确认 | 点、开关 | 所有设置持久化 |
| 32 | 空态/错误条 | 列表/输入区上 | 5 类空态/可重试 | 点主按钮 | 错误分类、重试 |

## 2.1 关键组件卡（8 字段）

**① 顶部栏（ChatBar）**
1) 位置：聊天页全局顶层；抽屉打开时被遮罩。2) 组成：左开关 / 中标题（或标题胶囊）/ 右 1–3 个操作；ChatGPT 用"浮动玻璃组"绕过标题。3) 状态：默认、滚动中（浮动/变色）、标题编辑中、连接中/离线副标。4) 手势：点开关、点标题（进详情/改名）、长按标题（改名/复制）。5) 转场：抽屉开启时顶栏不动或 60–120ms 位移；返回时反向。6) 数据：`session.title`、`host`、`online`、`permission_mode`。7) 无障碍：开关 ≥48dp、`contentDescription="打开对话列表"`、标题 `heading()`。8) 证据：`[截图实测]` 三产品；`[官方]` DSH Go 标题胶囊=信息面板入口。

**② 左侧抽屉（SessionDrawer）**
1) 位置：全局覆盖层，压在聊天页上。2) 组成：搜索框 → 分段/筛选 → 分组列表 → 底部账户/工具区。3) 状态：关闭/拖拽跟手/打开/搜索无结果/离线。4) 手势：左缘右滑开、回滑或点遮罩关、返回键先关；**代码块/表格横向滚动区域内不让位**。5) 转场：跟手位移 + 释放按速度归位（180–240ms）。6) 数据：会话列表（含 archived）、分组时间、置顶、未读/运行状态。7) 无障碍：抽屉开启时焦点移入、关闭回汉堡；每行读"标题，未读，按钮"。8) 证据：`[截图实测]` 宽度 82–85%；`[官方]` RikkaHub「左缘滑或汉堡」、DSH Go「菜单键或左缘右滑」。

**③ 会话行（SessionRow）**
1) 位置：抽屉/历史页列表。2) 组成：标题（1 行）+ 可选时间/预览/未读点/状态点 + 右侧 `⋯`。3) 状态：默认、选中、未读、运行中、待确认、长按菜单打开、多选勾选、归档（仅归档页）。4) 手势：点=切换、长按=菜单、`⋯`=菜单、左滑可选（ChatGPT 有 ••• 备入口）。5) 转场：选中态即时；菜单用锚定 Dropdown 或 bottom sheet。6) 数据：`id/title/updated_at/unread/status/archived`。7) 无障碍：单节点合并语义、role=button、`⋯` 有独立标签。8) 证据：`[截图实测]` 三产品行元素不同；`[官方]` DeepSeek FAQ（长按→重命名/置顶/删除）。

**④ 消息列表 + 消息气泡（MessageList）**
1) 位置：聊天页主体唯一滚动容器。2) 组成：用户行（右对齐气泡）/助手行（全宽文本容器 + 操作条 + footer）/系统行（居中细字）/错误行。3) 状态：空、加载、流式中、停止、失败、版本切换、长按选中文字。4) 手势：滚动、长按（菜单/选字）、双击（无）、点链接。5) 转场：新消息入场 120–160ms、流式光标呼吸；**贴底只在未上翻时跟随**。6) 数据：事件流（delta/done/stopped/error）、`message_id/role/created_at`。7) 无障碍：流式区 `liveRegion=Polite` 或独立"正在生成"节点，避免逐 token 朗读。8) 证据：`[截图实测]` 助手无气泡；`[官方仓库]` chat-demo 40px 贴底阈值 + 只用 `scrollTop` 判断上翻。

**⑤ 消息操作条（MessageActions）**
1) 位置：每条助手消息下方；用户消息默认不显示（DSH Go 长按才出"编辑后重发"）。2) 组成：复制/赞/踩/朗读/分享 + 右端重生成或 `⋯`；DSH Go 右端是"时长·步数"。3) 状态：默认、已复制(1.5s)、朗读中(再点停)、已打分、生成中禁用。4) 手势：点=主操作；长按消息=完整菜单（复制/选择文字/引用/分享/重新回答/从这里分叉）。5) 转场：反馈用图标切换 + 轻提示，不弹窗。6) 数据：`message_id`、TTS 可用性、反馈打分、版本数。7) 无障碍：每个图标独立 `contentDescription`；长按不是唯一入口。8) 证据：`[官方]` ChatGPT（"复制/朗读/分享用行内按钮或 •••"）、DeepSeek FAQ（长按分享）；`[截图实测]` 图标集。

**⑥ 思考/工具折叠块（ProcessBlock）**
1) 位置：助手消息上方（思考）或消息内（工具调用）。2) 组成：折叠头（icon+标题+状态/耗时）+ 可展开正文。3) 状态：生成中（恒高显示最近 3 行）、完成后收起（默认）、展开、失败（带 reason）。4) 手势：点折叠头展开/收起；长按可复制。5) 转场：高度动画 180–220ms，生成中不做循环动画。6) 数据：thinking 文本/耗时、tool name/args/result/ok/cost。7) 无障碍：折叠头 role=button + `expanded` 语义；失败必须文字说明。8) 证据：`[官方]` DSH Go「完成后收起过程默认开、思考块恒高 3 行」、Claude「Thinking 折叠节 + 计时」。

**⑦ 输入区胶囊（Composer）**
1) 位置：底部吸底、IME 之上。2) 组成：附件钮 + 文本框 + 语音/发送/停止 + 模式 chips + 模型入口 + 上下文指示。3) 状态：空/有字/组字中/聚焦/生成中/队列中/附件上传中/禁用/草稿恢复。4) 手势：点聚焦（不自动聚焦）、长按语音、上滑取消录音、长按发送键选发送方式。5) 转场：IME 升降 200ms 内贴底重算；发送键↔停止键即时切换。6) 数据：`draft`、`ime_composing`、`streaming`、`queue`、`context_used/limit`。7) 无障碍：发送/停止独立标签；组字状态不自动发送。8) 证据：`[截图实测]` 三种输入区；`[官方]` DSH Go 输入区符号随状态变。

**⑧ 附件面板（AttachSheet）**
1) 位置：输入区下方内联面板（DeepSeek）、锚定菜单（ChatGPT）、ModalBottomSheet（DSH Go）。2) 组成：来源按钮（拍照/相册/文件/生成/语音通话）+ 最近媒体 + 会话内设置（DSH Go）。3) 状态：关闭/展开/权限申请中/上传中/成功/失败可重试。4) 手势：点来源、拖拽柄下关、点遮罩关。5) 转场：内联用高度动画，sheet 用 drag 160–260ms。6) 数据：媒体权限、附件队列、上传进度、大小/数量上限。7) 无障碍：每个来源 ≥48dp + 图标标签；失败项可单独重试。8) 证据：`[截图实测]` 三形态；`[官方]` DSH Go 上传失败不发送。

**⑨ 撤销条（UndoBar）**
1) 位置：页面底部（输入区上方或之上），覆盖内容。2) 组成：一句结果文案 + 「撤销」+ 可选关闭。3) 状态：显示中（4s 倒计时）、已撤销、超时消失。4) 手势：点撤销；滑动不消除（避免误触）。5) 转场：进入 150ms 上滑，退出淡出。6) 数据：软删记录 id、动作类型（归档/删除/停止分享）。7) 无障碍：`liveRegion=Assertive` 播报；撤销按钮可聚焦停留。8) 证据：`[官方]` DSH Go 归档/删除分享链接 4 秒撤销；永久删除二次确认且不静默降级。

## 3. A–K 逐项详细行为

### A. 顶部栏

| 问题 | 同行做法 | 证据 |
|---|---|---|
| 汉堡在哪 | 全在左上；ChatGPT 做成玻璃圆钮、DSH Go 同款；DeepSeek 是无底线性图标 | `[截图实测]` |
| 标题 | DeepSeek 左对齐会话名；DSH Go 标题胶囊双行（标题 + 运行状态/workspace）；ChatGPT **不显示**；Claude 会话页标题也不常驻 | `[截图实测]` `[社区]` |
| 新建 | DeepSeek 顶栏 ⊕；DSH Go 顶栏 ✎ + 抽屉「新对话」双入口；ChatGPT 顶栏 ✎（另有抽屉底 FAB） | `[截图实测]` |
| 更多 | ChatGPT 顶栏 `⋯`（会话级：删除/锁定 chat）；DSH Go 无顶栏 ⋯（信息在标题胶囊）；DeepSeek 无 | `[截图实测]` `[官方]` |
| 状态/权限 | DSH Go 把「● 本机 · workspace」放进标题胶囊；权限芯片放在**输入框上方**而非顶栏；DeepSeek 有"扬声器斜杠"（朗读/静音类，含义未证实） | `[截图实测]` |
| 语音 | DSH Go 顶栏有电话钮（语音通话页）；DeepSeek 顶栏有扬声器类图标；ChatGPT 语音在输入区内 | `[截图实测]` |

**差异**：三方都在把"会话身份 + 运行状态"合并成一个可点芯片，而不是堆图标；ChatGPT 与 DSH Go 明确把模型选择移出顶栏（DSH Go 手机端在输入框下方，平板才在顶栏）。`[官方]`
**推荐 myco**：顶栏只留三槽——左「历史/抽屉」、中「标题 + 副行（本机·在线/离线）」、右 1–2 个操作（新建、语音）；权限/模式芯片放输入框上方，模型入口放输入框下方。

### B. 左侧抽屉/会话面板

| 问题 | 同行做法 | 证据 |
|---|---|---|
| 打开 | 汉堡；DSH Go/RikkaHub 另支持左缘右滑；ChatGPT 只点汉堡；deepseek-api-app 支持**整页任意位置右滑**（跟手+按速度归位） | `[官方]` [RikkaHub](https://docs.rikka-ai.com/chat/conversations)、`[官方]` DSH Go 01-chat、`[官方仓库]` [deepseek-api-app](https://github.com/S-Neur/deepseek-api-app) |
| 宽度/遮罩 | 82–85% + 右侧留边；DSH Go 下方有 scrim；大屏常驻（RikkaHub 平板/折叠横屏、DSH Go 侧边常驻时手势关闭） | `[截图实测]` `[官方]` |
| 搜索 | DeepSeek「搜索对话内容」= 正文；ChatGPT 搜标题/消息/含归档/不含锁定；DSH Go 搜对话并可「恢复并打开」归档；RikkaHub 搜索跳独立全文搜索页 | `[截图实测]` `[官方]` |
| 分组 | DeepSeek 置顶/今天/昨天；DSH Go 昨天/本周 + 筛选 chips + 文件夹；chat-demo 五档（今天/昨天/7 天/30 天/按月）；ChatGPT 仅"最近 8 条 + 查看全部" | `[截图实测]` `[官方仓库]` |
| 行元素 | 均单行标题为主；DeepSeek 选中行才有 `⋯`；都有选中底色；未读/运行状态只有 DSH Go 有 | `[截图实测]` |
| 行手势 | 点=切换；长按=菜单（DeepSeek 3 项、DSH Go 7 项、ChatGPT 走 •••）；左滑在各产品**未证实** | `[官方]` DeepSeek FAQ；`[官方]` DSH Go |
| 置顶/归档/删除 | 置顶=独立分组；归档可撤销/可搜索恢复；永久删除二次确认 | `[官方]` DSH Go |
| 多选 | DSH Go 长按→多选→底部删除，失败逐条报因，连接中断停止剩余批次 | `[官方]` |
| 底部账户区 | DeepSeek 头像+昵称+⋯；DSH Go 主机状态+全部功能+设置；ChatGPT 新建 FAB+设置齿轮（无账户行） | `[截图实测]` |
| 抽屉内新建 | ChatGPT FAB、DSH Go 顶部按钮；DeepSeek 无（顶栏 ⊕） | `[截图实测]` |

**差异**：DeepSeek 是"标题列表"流派（不显示时间/预览），DSH Go 是"状态控制台"流派（筛选+文件夹+主机），ChatGPT 是"最近 8 条 + 全部"的极简流派。
**推荐 myco**：抽屉 = 搜索（**全文**）+ 五档分组 + 置顶段 + 行尾常驻 `⋯`（无障碍备入口）+ 长按菜单（重命名/置顶/归档/删除）+ 底部「设置 / 全部历史」。不做"空抽屉"；不把"多选"放进 M1。

### C. 聊天区消息模型

| 产品 | 助手消息 | 用户消息 | 分组/间距 |
|---|---|---|---|
| DeepSeek | 全宽无气泡，markdown 标题/列表/正文；末尾免责声明 | 右对齐气泡（截图未含，`[推断]`） | 段距≈1 行；轮间无明显分割 |
| ChatGPT | 全宽无气泡；行内操作条 6 键 | 右对齐浅色气泡（`[推断]`） | 段落式，无轮次线 |
| DSH Go | 全宽 markdown + inline code 胶囊 + 角色头像 | 右对齐气泡（`[推断]`） | 「时长 · 步数」footer 分轮；过程折叠 |
| 开源参考 | chat-demo：`thinking/text/tool` 块按序追加；RikkaHub：每个消息是 node，可多版本 | 同左 | RikkaHub 提供 fork 与版本导航 |

- **流式增长**：三家都是"文本变长"而非"气泡变高"（助手无气泡）；DeepSeek 助手区没有骨架，首 token 前只有空白/等待点（`[推断]`，截图未捕获）。`[截图实测]`
- **贴底规则**（可直接照抄）：chat-demo 官方仓库——阈值 40px；**内容变高不取消跟随，只有用户主动上滑才取消**；`ResizeObserver` 同时观察消息列/滚动容器/键盘，任一变化重贴。`[官方仓库]` [chat-demo](https://github.com/deepseek-ai/chat-demo)
- **回到底部**：本组截图都未出现（滚动中未捕获）；上一轮已建议浮动按钮。`[截图实测]`
- **流式节流**：chat-demo 按"实测渲染耗时×2、上限 2s"合并 token，避免逐 token 重排 markdown。`[官方仓库]`
- **差异**：DeepSeek 靠排版（大标题/缩进/段距）表达结构；ChatGPT 更"素"（段落+列表）；DSH Go 把结构信息塞进折叠块与 footer；agent 场景下 DSH Go 的"消息=一个可折叠过程"模型最贴 myco 的 tool loop。
- **推荐 myco**：助手消息去掉气泡背景（保留容器以便操作条/折叠块对齐）；用户气泡保留；同轮多条助手消息紧贴、跨轮松；贴底用"用户上滑才暂停跟随"；流式按帧合并、不重解析整段 Markdown。

### D. 消息级操作条

| 产品 | 常显操作 | 长按/更多菜单 | 重新生成的语义 |
|---|---|---|---|
| DeepSeek | 复制/赞/踩/朗读/分享 + 最右重生成 | 至少含"分享"；其余未证实 | 替换当前条 `[推断]` |
| ChatGPT | 复制/赞/踩/朗读/分享/⋯ | 长按→"复制、编辑"（官方 2024 更新）；选文字走系统菜单 | 替换当前条；`⋯` 里另有会话级项 |
| DSH Go | 复制/分享/朗读/分叉/重新回答/⋯ + 右端「耗时·步数」 | 复制/选择文字/引用/分享/重新回答/从这里分叉；长按自己的消息→「编辑后重发」 | **新对话继续**：原对话不变，提示条「返回原对话」；仅最后一条可用，生成中/有排队/带文件不可用 |
| Claude | 复制/分享/朗读/赞/踩/Try again | 社区记录 `‹1/N›` 版本导航；编辑/重试是否保留旧版本有争议 | 官方未明说；社区观察为"重试+版本切换" |
| RikkaHub | 行内操作菜单 | 长按→Regenerate/Edit/Delete/Fork/Share/Favorite | **原位追加版本**，`2/3` 箭头切换；Fork=复制到新会话 |

- `[官方]` [ChatGPT mobile 帮助](https://help.openai.com/en/articles/8142208-using-the-chatgpt-mobile-app-classic-experience)；`[官方]` ChatGPT 2024 release note「长按消息 → 复制/编辑」；`[官方]` [DSH Go 01-chat](https://github.com/ccch1mneyyy/dsh-go/blob/master/docs/guide/zh/01-chat.md)；`[官方]` [RikkaHub conversations](https://docs.rikka-ai.com/chat/conversations)。
- **差异**：编辑用户消息后——DeepSeek 未证实；ChatGPT/RikkaHub 原位重跑（RikkaHub 产生新版本）；DSH Go 开新对话且**要求重新添加附件**。`[官方]`
- **推荐 myco**：助手行内常显 4 键（复制/朗读/重新生成/更多）+ 长按完整菜单；用户消息长按→编辑/复制；重新生成**原位加版本**（不删旧），仅最后一条可重生成；编辑用户消息→新版本分支，旧分支可切回；行内"更多"必须存在（TalkBack 可达）。

### E. Markdown 与富内容

| 能力 | 同行做法 | 证据 |
|---|---|---|
| 标题/粗体/列表/引用 | 三家均渲染；DeepSeek 标题明显加大加粗；列表悬挂缩进 | `[截图实测]` |
| 代码块 | chat-demo：语言标签 + 复制，语言包按需加载（未加载先纯文本不闪）；RikkaHub：右上复制；DSH Go：inline code 胶囊 + 代码块可横向滚动 | `[官方仓库]` `[仓库文档]` |
| 表格 | RikkaHub 表格横向滚动；DSH Go 文档明确"在代码块、表格这些能横向滚动的区域滑动，仍是滚动它们" | `[仓库文档]` `[官方]` |
| 链接 | 默认外部/内置浏览器；ChatGPT Work 有内置浏览器 | `[官方]` |
| 图片 | DSH Go：全屏看、双指 8x、双击 2.5x、组内左右滑 + 页码、保存/长按菜单针对当前图；ChatGPT：生成图全宽（2026-08 Android） | `[官方]` DSH Go 01-chat；`[官方]` release notes |
| 公式/Mermaid | chat-demo 用 KaTeX + Mermaid（图表/代码切换）；RikkaHub 原生 LaTeX + Mermaid 可导出图片 | `[官方仓库]` `[仓库文档]` |
| 流式半截 Markdown | 只找到 chat-demo 的"节流合并"策略；未找到"未闭合 fence 如何渲染"的官方说明 | `[官方仓库]` `[推断]` |

- **差异**：DeepSeek/ChatGPT 面向"读答案"，富内容克制（图片/代码为主）；chat-demo/RikkaHub 面向"开发者+多模型"，把 KaTeX/Mermaid/表格做全；DSH Go 面向"交付"，额外做生成物（图/视频）的**重生成/保存/详情**操作行。myco 是陪伴+agent，取"克制但代码/图片必须可用"。
- **推荐 myco**：M1 先支持 标题/粗体/斜体/列表/引用/行内代码/代码块（语言标签+复制+横滚）/链接；M2 加表格（横滚）与图片消息；公式/Mermaid 押后。流式策略：**未闭合代码 fence 先按等宽纯文本渲染，闭合后切高亮**；表格只渲染已完整的行；Markdown 解析按帧节流（≥50ms 或"渲染耗时×2"）。

### F. 工具/思考/步骤展示

| 问题 | 同行做法 | 证据 |
|---|---|---|
| 折叠 | DSH Go「完成后收起过程」默认开；思考块生成中恒高显示最近 3 行，点开全文再点收回；Claude 回答上方"Thinking"折叠节 + 计时 + 生成中 indicator | `[官方]` DSH Go 09-appearance；`[官方]` [Claude thinking](https://support.claude.com/en/articles/8664678-change-the-model-effort-and-thinking-settings) |
| 计数/耗时 | DSH Go 消息右端「1 分 07 秒 · 6 步 ›」，点开步骤面板 | `[截图实测]` |
| 工具参数/结果 | DSH Go 授权卡展示命令/文件/原因 + 「允许本次/拒绝」；「本对话都允许」后可撤销 | `[官方]` |
| 失败 | 工具失败必须带 reason（上一轮结论）；DSH Go 主机失败给修复按钮/日志/诊断包；旧主机不支持永久删除时明确提示"只能归档，不偷偷改" | `[官方]` 11-faq |
| 展开态持久 | 未找到明确说明；"完成后收起"是设置项，推测重启后按设置重置 | `[推断]` |
| 权限芯片 | 输入框上方「完全权限 / 自动允许中 / 发送文字将回答待答问题」常驻可点 | `[官方]` `[截图实测]` |

- **差异**：思考展示有三档——只给计时（Claude 折叠节头）、恒高三行摘要（DSH Go 生成中）、完整过程折叠（完成后可展开）。工具审批只有 DSH Go 做到"卡片内看清命令/文件/原因 + 本次/本对话允许"；另两家把工具过程当内容块而非审批对象。
- **推荐 myco**：每条助手消息一个可折叠「过程」区（思考 + 工具调用混排，按发生顺序），折叠头显示"N 步 · Xs"；生成中恒高 3 行展示；失败工具红色标记 + reason 单行；权限/待确认状态做输入框上方芯片，点击进设置或批准；过程展开态**按会话记忆**（不做全局持久化）。

### G. 输入区

| 问题 | 同行做法 | 证据 |
|---|---|---|
| 形状 | 均为大圆角胶囊；DeepSeek 两行高（文本框 + chips 行），ChatGPT/DSH Go 单行（DSH Go 模型入口在框下方） | `[截图实测]` |
| 附件 | ⊕ 左端；DeepSeek 内联面板、ChatGPT 锚定菜单、DSH Go bottom sheet | `[截图实测]` |
| 语音 | DeepSeek placeholder「按住说话」+ 语音钮；ChatGPT 麦克风 + 蓝色语音键；DSH Go 空输入时发送键变麦克风，点它说话、文字回填 | `[截图实测]` `[官方]` |
| 模式 chips | DeepSeek「深度思考/智能搜索」在框内；ChatGPT 放附件菜单（含 ✓）；Claude 模型/effort/thinking 在发送键旁 + 底部"Search and tools" | `[截图实测]` `[官方]` |
| 模型入口 | DSH Go 手机端在输入框下方（平板在顶栏）；RikkaHub 顶栏模型选择器；Claude 模型名在发送键旁 | `[官方]` `[仓库文档]` |
| 发送/停止 | 空=无发送键（语音优先）；生成中=停止；DSH Go 有字生成中=插话；长按=菜单 | `[截图实测]` `[官方]` |
| 排队/插话 | DSH Go：插话/中断并发送/排队；底部队列条可「立即发送/改为插话/撤回」，撤回回输入框 | `[官方]` |
| 上下文 | DSH Go 输入框内圆环「31%」；Claude 用量在菜单/设置；ChatGPT 余额在设置 | `[截图实测]` |
| IME | 不自动聚焦；组字中不发送；Enter 换行 / Ctrl+Enter 发送（外接键盘）；多行上限（chat-demo 202px；myco 现 maxLines=4） | `[官方]` DSH Go 01-chat；`[官方仓库]` chat-demo |
| 草稿 | ChatGPT 切设计前提示"保存草稿"（说明有草稿）；DSH Go 撤回内容回输入框 | `[官方]` |

- **差异**：DeepSeek 用"框内两排"（文本+chips）压平层级；ChatGPT 极简单行、把模式藏进 + 菜单；DSH Go 单行+框下模型胶囊+框内上下文圆环，多出来的信息全给"运行态"。myco 要陪伴感又要有 agent 可观测性 → 单行输入 + 第二行 chips/模型/上下文。
- **推荐 myco**：输入区做成"可扩展状态机"——空输入时右侧是麦克风（长按/点按），有字时是发送，生成中是停止；长按发送给 2–3 个选项（M2 之后做插话/排队）；框内左侧 + 附件、框下 chips（深度思考/联网）+ 模型胶囊 + 上下文百分比；IME 用 `imePadding()`、不自动聚焦、组字中不发送；草稿按会话保存。

### H. 文件/附件上传

| 问题 | DeepSeek | ChatGPT | DSH Go | RikkaHub |
|---|---|---|---|---|
| 面板形态 | 内联展开（+→✕） | 锚定 + 菜单 | ModalBottomSheet | chips + 系统选择器 |
| 来源 | 拍照/相册/文件 + 最近照片 | 拍照/照片/文件 + 插件/深入思考 | 相册/拍照/文件/生成/语音通话 | 相机/相册/文件 |
| 数量/大小 | 移动端 ≤20 个、单文件 ≤100MB `[社区]` | 单文件 512MB、图片 20MB、20 附件/消息 `[社区]` | ≤9 附件/消息、单文件 200MB（App 不另设 20MB） | 未给硬限制，长文档会超上下文 |
| 上传中/失败 | 失败卡片可重试（chat-demo：上传中/失败时发送禁用）`[官方仓库]` | 未证实 | 上传失败**不会继续发送**；同名不覆盖自动改名；远程不支持则隐藏"文件" | 非视觉模型自动 OCR（可配 OCR 模型），结果缓存 3 天 |
| 消息内呈现 | 附件 chip/缩略图 | 图片全宽 | 图片可点全屏、组内左右滑 | 附件 chip 可 × 移除 |

- **差异**：DeepSeek 把附件当"输入区的一部分"（内联、轻）；ChatGPT 把附件菜单当"能力入口"（插件/深入思考也在这）；DSH Go 把附件面板当"会话控制台"（权限/计划模式/确认方式）。RikkaHub 最"工程化"（OCR 回退/粘贴/系统分享入队）。
- **推荐 myco**：附件统一走"队列对象"（id/name/mime/size/localUri/uploadState/error）；chips 显示状态（转圈/失败可重试/×）；发送前要求全部 `ready`；图片消息内先缩略图点击全屏；失败**不得静默继续发送**。

### I. 设置页

| 产品 | 分组结构 | 控件 | 特别点 |
|---|---|---|---|
| DeepSeek | 账户 / 应用（语言/外观/字体/个性化）/ 语音 / 关于 + 底部帮助与反馈 | 行右值即状态；子页 | 无设置搜索；版本号内联，检查更新有版本号 |
| ChatGPT | 账户组（含切换账户/升级）/ 自定义 ChatGPT / 常规 / 用量与限额 / 通知 / 语音 / 安全与身心健康 / 账户安全与登录 | switch、选择器、子页 | **吸底设置搜索**；顶部 i 信息；危险/账户操作在子页 |
| DSH Go | AI / 能力 / 外观 / 通用 / 主机 / 帮助与关于；顶部账号+引擎卡 | switch、选择器、子页、确认弹窗 | **吸顶设置搜索**（支持旧名/口语）；右值即状态+警示色；完全权限要确认；「后台运行」引导页 |
| Claude | Profile / Billing / Capabilities / Connectors / Permissions / Color mode / Font style / Speech language / Haptic feedback / Privacy / Shared links / Info / Log out `[社区]` | switch、子页 | 外观类设置直接给选择器 |

- **返回路径**：设置从抽屉底部齿轮（DSH Go/RikkaHub）或抽屉账户 `⋯`（DeepSeek）或 sidebar profile（ChatGPT）进入；返回回到进入前页面。`[截图实测]` `[官方]`
- **差异**：DeepSeek 是"少而一屏"（无搜索，靠分组）；ChatGPT 是"多而可搜"（吸底搜索+i 信息）；DSH Go 是"状态面板"（账号/引擎状态卡 + 右值即状态 + 危险项确认）；Claude 把外观与账户拆成独立子页。
- **推荐 myco**：分组 = 模型与渠道 / 外观 / 对话（字号、完成后收起过程、朗读）/ 权限与工具 / 数据管理 / 通知 / 关于；每行右值显示当前值；危险操作（删除数据、完全权限）二次确认；M2 后加设置搜索；版本号放"关于"。

### J. 导航与系统集成

| 问题 | 同行做法 | 证据 |
|---|---|---|
| 返回键 | DSH Go 显式导航栈：设置→搜索→轮次面板→抽屉→主页→退出；deepseek-api-app 也新增 `navStack` 把返回键从"直接退出"改成逐层退；左滑快捷面板先被返回收起 | `[官方]` DSH Go 01-chat；`[官方仓库]` deepseek-api-app；`[官方]` [predictive back](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back) |
| 预测性返回 | Android 13+ 可选入，`OnBackInvokedCallback` / AndroidX `PredictiveBackHandler`；Compose 用 `BackHandler` 优先消费弹层 | `[官方]` 同上 |
| 深链 | DSH Go `dshgo://open/settings` 等深链；分享链接/`.dshgo` 文件可打开回放 | `[官方]` DSH Go 07-sharing/10-settings |
| 通知 | DSH Go 按事件类型开关：一轮完成/出错或被停下/等你确认/向你提问；远程提醒需 App 运行在线；点击进对应会话（`[推断]`） | `[官方]` DSH Go 10-settings/11-faq |
| 后台/重连 | DSH Go：电池优化引导、前台通知、引擎恢复但**不重放中断命令**；远程断开给重连/重登/重信任；ChatGPT 明确提示"waiting for an internet connection" | `[官方]` DSH Go 11-faq；`[官方]` release notes |
| 平板/横屏 | RikkaHub 大屏抽屉常驻；DSH Go 平板顶栏放模型胶囊、侧边常驻时关手势 | `[官方]` |
| 多窗口/分屏 | 未见专门说明 | 存疑（§6） |

- **差异**：DSH Go 把返回键当成"导航栈的一部分"并写进文档；ChatGPT/DeepSeek 依赖系统默认行为（未写文档）；深链只有 DSH Go 成体系（`dshgo://open/*` + 分享回放）。myco 现在只有内存 `screen` 状态，进程被杀即丢。
- **推荐 myco**：用单 Activity + 自有导航状态（当前已是 `screen` 枚举），但补：抽屉/弹层优先消费返回、预测性返回开关、通知携带 `session_id` 深链、深链 `myco://chat/{id}`（为将来分享/通知铺路）、后台回来重新拉 events 并修正贴底。

### K. 其他影响"日常不烧心"的能力

- **撤销**：DSH Go 归档/删除分享链接 4s 撤销；RikkaHub 删除**无回收站**（官方明说不可恢复）→ 反例。`[官方]` `[仓库文档]`
- **离线/错误**：DSH Go 先看主机状态再给日志/修复按钮；chat-demo 错误分类（网络/Key/余额/限流/繁忙/未知）+ 每类带动作按钮。`[官方]`
- **草稿**：ChatGPT 有草稿概念；DSH Go 撤回内容回输入框；myco 现在切会话丢输入。`[官方]` `[推断]`
- **输入中/运行状态**：DSH Go 会话可标「运行中/待确认/未读」，抽屉可筛；chat-demo 用 3 点 + "正在思考" shimmer。`[截图实测]` `[官方仓库]`
- **@引用 / 指令**：DSH Go 输入 `@` 引用文件/其他对话，`/` 打开指令（`/compact` `/export` `/plan` `/goal` `/permission` `/new` `/model`）；外接键盘可用 ↑↓/Enter 选择。`[官方]`
- **切换不丢**：ChatGPT 切会话不自动聚焦输入框（官方）；DSH Go 切主机列表就换（会话属于主机）。`[官方]`
- **锁定/隐私**：ChatGPT Locked chats（••• → Lock chat，PIN 打开，隐藏于历史/置顶/搜索）；Claude 有 incognito；ChatGPT temporary chat。`[官方]` `[社区]`
- **收藏**：RikkaHub 心形收藏 + 抽屉 Favorites，点击跳回原会话原位。`[仓库文档]`
- **上下文用量**：DSH Go 圆环 31%；Claude 在模型菜单显示用量档位。`[截图实测]` `[官方]`
- **语音通话**：DSH Go 独立通话页（说完停顿自动发、边写边读、可打断、静音/挂断/免提、后台即结束）。`[官方]`
- **多主机/工作区**：DSH Go 抽屉底部显示"本机·运行中"并可切主机；设置里有独立「主机」组。`[截图实测]` `[官方]`
- **推荐 myco**：以上是"日常不烧心"的候选池，M1–M2 只取三样——**撤销条、错误分类可操作、草稿不丢**；@引用/指令、锁定会话、收藏、语音通话、多主机都押后（它们依赖 tool loop / 多会话 / 权限体系先成立）。

## 4. 跨产品差异与收敛点

### 4.1 事实标准（做错就被视为不合格）

| # | 标准 | 证据 |
|---|---|---|
| 1 | 助手消息不用气泡（全宽文本），用户消息右对齐 | 三家截图 |
| 2 | 抽屉宽 80–85% + 遮罩 + 返回先关抽屉 | 三家截图 + Material 规范 |
| 3 | 会话长按菜单（重命名/置顶/删除）+ 行内 `⋯` 备入口 | DeepSeek FAQ、DSH Go、RikkaHub |
| 4 | 消息操作条常显（复制/反馈/朗读/分享/重生成） | 三家截图 |
| 5 | 流式贴底：用户上滑即停止跟随；回到底部按钮 | chat-demo 源码 |
| 6 | 删除必须可撤销或二次确认（最好两者） | DSH Go；反例 RikkaHub |
| 7 | 空输入时发送键位让给语音 | DeepSeek/ChatGPT/DSH Go |
| 8 | 设置行右值 = 当前状态 | DeepSeek/ChatGPT/DSH Go |
| 9 | 大圆角输入胶囊 + 附件 + 语音 + 发送/停止切换 | 三家截图 |
| 10 | 思考/工具折叠，不占满屏 | DSH Go/Claude |

### 4.2 产品选择（没有唯一答案，按产品定位选）

| 分歧 | 选项 A | 选项 B | 对 myco 的取舍 |
|---|---|---|---|
| 会话标题 | 顶栏显示（DeepSeek） | 不显示/标题胶囊（ChatGPT/DSH Go） | 陪伴产品：显示标题更好（"她"的名字/话题感） |
| 抽屉列表信息量 | 只标题（DeepSeek/ChatGPT） | 标题+状态筛选+文件夹（DSH Go） | M1 只标题+分组；M4 加状态筛选 |
| 重新生成 | 替换（ChatGPT/DeepSeek 直觉） | 新对话（DSH Go） | 选**原位加版本**（省数据、可回看） |
| 附件面板 | 内联（DeepSeek） | 弹层（DSH Go）/锚定菜单（ChatGPT） | 内联最轻、弹层能塞"本对话设置"；M5 先内联 |
| 模型选择 | 顶栏胶囊（平板） | 输入框下方（手机） | 手机放输入框下方，横屏放顶栏 |
| 语音 | 长按说话（DeepSeek） | 点麦克风转文字（DSH Go） | 两者都留：点=转文字、长按=按住说 |
| 设置搜索 | 无（DeepSeek） | 有（ChatGPT/DSH Go） | M2 后加，先保证分组合理 |

## 5. 对 myco 的组件清单 + 后端数据需求

### 5.1 必须做（M1–M4，按依赖排序）

| 优先级 | 组件 | 现状（`app-android/.../MainActivity.kt`） | 后端/事件需求 |
|---|---|---|---|
| P0 | 抽屉容器（含遮罩、返回先关、边缘滑） | 无 | 纯 UI + 会话列表查询 |
| P0 | 抽屉搜索（全文，含归档） | 只有整页 `SearchField` + `SessionSearch` | `search(query)` 覆盖消息正文；结果带 `session_id`/命中片段 |
| P0 | 时间分组（今天/昨天/本周/更早/置顶） | 平铺列表 | `updated_at`、`pinned_at` |
| P0 | 会话行 `⋯` + 长按菜单 | 有 AlertDialog（改名/删除两项） | rename/pin/archive/delete 命令 |
| P0 | 删除软删 + 4s 撤销条 | 软删但无撤销 | 删除返回 `undo_token` + `restore` 命令 |
| P0 | 消息行内操作条（复制/朗读/重新生成/更多） | 只有长按=复制 | `message_id`、TTS、`regenerate(message_id)` |
| P0 | 助手消息 Markdown 渲染（标题/列表/代码块+复制/引用） | 纯 `Text` | 内容块数组（`thinking/text/tool/error`） |
| P0 | 思考/工具折叠块 + 步数耗时 | `ChatLine("system", …)` 平铺 | `tool.call/result` 事件（含 args/ok/reason/duration） |
| P0 | 流式贴底 + 上翻不跟随 + 回到最新 | 有 `atBottom` 近似 + 按钮 | 无 |
| P0 | 输入区状态机（空=语音、生成=停止、有字=发送、长按） | 显示"停止"按钮但无麦克风/插话 | `streaming`、`queue`、ASR |
| P0 | 错误分类 + 可操作重试 | 单行 `InlineError` | 错误码→动作映射（换 Key/重试/充值） |
| P1 | 输入区上方权限/状态芯片 | 无 | `permission_mode`、`pending_approval` |
| P1 | 草稿按会话保存 | 无（切会话丢） | `draft` 本地表 + `last_session_id` |
| P1 | 版本切换 `‹1/3›` | 无 | `message_node.versions[]` + `active_version` |
| P1 | 图片查看器（缩略图→全屏、组内滑） | 无 | 附件 `mime/width/height`、`group_id` |
| P1 | 附件 chip 状态机 | 有 chips，无上传状态 | `upload_state`、重试 |
| P2 | 设置搜索 / 高亮命中 | 无 | 设置项索引（本地静态表） |
| P2 | 通知深链 + 多主机状态 | 无 | `session_id` in intent、host 状态 |

### 5.2 数据/事件契约（报告级建议，供 bridge 设计）

- **会话**：`id, title, updated_at, created_at, pinned_at?, archived_at?, unread_count, status(running|awaiting_confirm|idle)`。
- **消息**：`id, session_id, role, node_id, version, created_at, blocks[]`；`blocks = thinking|text|tool_call|tool_result|image|error`，每块带 `seq`。
- **流式事件**：`message.delta{node_id, block, text}` / `message.stop{reason}` / `error{code, message, retryable}`；停止是**正常终态**，不是 error。
- **反馈**：`feedback{message_id, up|down, reason?}`。
- **版本/分叉**：`message.regenerate{node_id}` → 新 `version`；`conversation.fork{node_id}` → 新 `session_id`（DSH Go 式"新对话"或 RikkaHub 式"同会话新 node"，二选一，建议后者）。
- **撤销**：`session.delete` → `{ok, undo_token, ttl_ms=4000}`；`session.restore{undo_token}`。
- **附件**：`attachment{id, name, mime, size, width?, height?, upload_state, group_id?}`；`attachment.retry{id}`。
- **上下文**：`context{used_tokens, limit_tokens}`，或返回 `usage` 事件由前端累加。
- **权限**：`permission{mode: view|workspace|full, pending: approval[]}`；审批动作 `allow_once|deny|allow_session`。
- **草稿**：本地 `draft(session_id, text, attachments, updated_at)`。

## 6. 未证实 / 存疑

1. **用户消息样式**：本组 12 张截图全是助手消息，用户气泡为 `[推断]`（右对齐浅色气泡是行业惯例）。
2. **DeepSeek 顶栏"扬声器斜杠"** 的确切含义（朗读开关/静音/通话）未证实；截图里聊天页两个图标只识别出"新对话 ⊕"。
3. **DeepSeek 是否支持编辑用户消息、消息是否带版本切换**：未找到官方说明，`[推断]` 不支持。
4. **DeepSeek 上传限制**（单文件 100MB、移动端 20 个）来自 `[社区]` 教程，未在官方 FAQ/帮助页核实。
5. **ChatGPT 重新生成入口**：截图行内未见 refresh，`[推断]` 在 `⋯` 或长按菜单；官方未逐字说明 Android 端入口位置。
6. **Claude Android 的横向导航形态**（底部 Tab vs 侧栏）：上一轮据 The Verge/dev.ua 报道为底部 Tab（Chat/Projects/Code/Artifacts）`[观察]`，本轮未拿到官方像素证据；Claude 消息 `‹1/N›` 版本导航在近期版本是否保留有社区争议。
7. **流式半截 Markdown 的渲染策略**：除 chat-demo 的节流外，未找到任何产品公开说明"未闭合代码块/表格"如何处理。
8. **思考/工具折叠的展开态是否跨重启持久**：DSH Go 文档只说明默认收起设置，未说明展开态持久化。
9. **抽屉左滑/右滑手势**：截图无法体现；除 DSH Go/RikkaHub 官方文档明确"左缘右滑"外，DeepSeek/ChatGPT 是否有边缘滑未证实。
10. **预测性返回**：官方文档页在本次抓取中正文被截断，具体 API 行为按 Android 13+ 常识与上一轮结论处理，未逐字核对。
11. **多窗口/分屏/横屏布局**：只有"平板抽屉常驻/顶栏放模型胶囊"两条官方证据，分屏与横屏细节未调研。
12. **附件大小限制的产品差异**（ChatGPT 512MB/图片 20MB/20 附件）来自 `[社区]` 汇总，可能随套餐变化。
13. 本报告**未在真机/模拟器运行任何产品**，也未调用模型服务；"动画时长/缓动"除 DSH Go 设置里的"界面动效/刷新率"可关闭外，均无官方数值，标 `[推断]`。
14. GitHub 直连不可达，DSH Go/RikkaHub/chat-demo 均经 jsDelivr 或官方文档站读取；`@master`/`@main` 会随上游变化，复核请固定 commit。

**来源汇总**（2026-11-26 抓取）：[DeepSeek FAQ（官方源镜像）](https://static.deepseek.com/faq/index.html?lang=zh#/category/2) · [DeepSeek API thinking mode](https://api-docs.deepseek.com/guides/thinking_mode) · [chat-demo](https://github.com/deepseek-ai/chat-demo) · [deepseek-api-app](https://github.com/S-Neur/deepseek-api-app) · [DSH Go 对话指南](https://github.com/ccch1mneyyy/dsh-go/blob/master/docs/guide/zh/01-chat.md) · [DSH Go 设置](https://github.com/ccch1mneyyy/dsh-go/blob/master/docs/guide/zh/10-settings.md) · [DSH Go 外观](https://github.com/ccch1mneyyy/dsh-go/blob/master/docs/guide/zh/09-appearance.md) · [DSH Go FAQ](https://github.com/ccch1mneyyy/dsh-go/blob/master/docs/guide/zh/11-faq.md) · [DSH Go 分享](https://github.com/ccch1mneyyy/dsh-go/blob/master/docs/guide/zh/07-sharing.md) · [ChatGPT release notes](https://help.openai.com/en/articles/6825453-chatgpt-release-notes) · [ChatGPT mobile 帮助](https://help.openai.com/en/articles/8142208-using-the-chatgpt-mobile-app-classic-experience) · [ChatGPT locked chats](https://help.openai.com/en/articles/20001411) · [Claude Android 应用集成](https://support.claude.com/en/articles/11869629-use-claude-with-android-apps) · [Claude 模型/effort/thinking](https://support.claude.com/en/articles/8664678-change-the-model-effort-and-thinking-settings) · [Claude 图标指南（社区）](https://techpp.app/explained/claude-icon-guide) · [Claude 输出 UX teardown（社区）](https://aiuxplayground.com/teardowns/claude/output/) · [RikkaHub conversations](https://docs.rikka-ai.com/chat/conversations) · [RikkaHub branching](https://docs.rikka-ai.com/chat/branching) · [RikkaHub multimodal](https://docs.rikka-ai.com/chat/multimodal) · [Android predictive back](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back) · [Compose edge-to-edge](https://developer.android.com/develop/ui/compose/system/setup-e2e) · [Material 3 bottom sheets](https://m3.material.io/components/bottom-sheets/overview) · [Android 无障碍触摸目标](https://developer.android.com/guide/topics/ui/accessibility/apps)

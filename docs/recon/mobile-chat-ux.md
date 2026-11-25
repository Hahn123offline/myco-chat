# 移动端 chat UX 参考（2026-11-26）

> **口径**：全程只读公开网页与仓库 README/官方指南；GitHub 直连在本机不可达（`fetch failed`），改走 jsDelivr 镜像读同一份文件，未反复重试。
> **未调用任何模型服务/API、未 clone、未安装、未在真机或模拟器运行任何产品**——所有"产品行为"均为二手证据。
> 证据标记：`[官方]` 官方文档/官方仓库/官方指南 · `[观察]` 媒体或教程的行为转述 · `[社区]` 社区经验 · `[推断]` 我的判断，未验证。
> 用途：给 myco **M1 / M4 / M6** 的交互参照。原则按用户的话：**UX 可以抄，逻辑先行，别反用户**。

---

## 0. 一页结论

1. 首屏正在从"会话列表"变成"入口 + 最近几条"：Claude 2026 移动端改版上了底部 Tab（Chat / Projects / Code / Artifacts），ChatGPT 同期也上了底部 Tab `[观察]`。行业答案**不是纯单焦点**，是**单焦点 + 一等的历史入口**。
2. 会话单条操作已收敛：长按（或左滑）出菜单 → 重命名 / 置顶 / 归档 / 删除；删除必须二次确认，且最好能撤销 `[官方][观察]`。
3. 流式的用户感知由四件事决定：等待/流中/停止/失败**四态可见**、贴底**只在用户没上翻时**生效、停止后**保留已收到内容**、错误可分类并给下一步动作 `[官方仓库]`。
4. 输入区的稳定配方：多行自适应、位于 IME 之上、**发送键位随状态变**（空输入→语音；生成中→停止；有排队→插话）、**不自动聚焦** `[官方仓库][官方]`。
5. DSH Go 最值得抄的是"复杂 agent 塞进手机"的收纳术：**过程折叠成一行、插话/排队/中断三选、长按发送键选发送方式、返回键导航栈** `[官方]`。
6. 民间 WebView 移植版（deepseek-api-app）最值得抄的是长列表：**只渲最近 N 条 + 右缘轮次跳转 + 撤销条 + 导入导出** `[官方仓库]`。
7. Compose **没有官方 backdrop blur API**；myco 现在 `GlassSurface` 的"半透明 tint + 1dp 边框"就是正确默认 `[推断]`。真模糊放 M6，且必须可关。
8. **M4 推荐**：chat 单焦点，把"多会话"降级为「历史」+ 搜索（**不要留空抽屉**）；work 保留多会话列表；将来合壳再上底部双 Tab。
9. **M1 必须**：列表项长按菜单、消息长按菜单、停止/重试、输入区 IME 贴底、5 类空态 + 错误条、删除确认 + 撤销。**M1 延后**：模糊、插画、装饰动效、分支切换、多选。
10. **不要抄**：只靠长按才能到的关键操作（TalkBack 不可用）、删除无撤销、隐藏历史入口、强制动画、把"停止"做成异常态、把"provider 未配置"做成首启硬墙。

---

## 1. 心流基线（列表 / 聊天 / 设置 / 空态错误）

### 1.1 首屏与会话列表

| 维度 | 证据（标注） | 对 myco 的结论 |
|---|---|---|
| 首屏结构 | DeepSeek 官方 chat-demo：无 router，`activeId==null` 显示 Welcome（一句问候语 + 输入框），否则会话页 `[官方仓库]`。ChatGPT 移动端侧边栏为**浮动**、切会话自动收起，切会话时输入框**不自动聚焦** `[官方 release notes]`。豆包：左上角"两条杠"进会话列表 `[社区]`。 | 首屏 = 当前对话（或空态）+ 显式的"历史/新建"入口；不要首屏就摆一屏会话卡片。 |
| 新建会话 | chat-demo：侧栏顶部独立大按钮「开启新对话」`[官方仓库]`；DSH Go：对话列表「新对话」+ 聊天页右上角新对话，**发出第一条消息才建立对话**（不留空会话）`[官方]`；ChatGPT：顶栏"新聊天"。 | 抄 DSH Go 的"首条消息才落会话"，能消掉一堆空会话脏数据。 |
| 时间分组 | chat-demo：固定五档 **今天 / 昨天 / 7 天内 / 30 天内 / 按月**，跨午夜与页面可见时重算 `[官方仓库]`。 | 直接抄五档；分组标签必须随时钟重算。 |
| 单条操作 | chat-demo：`⋮` → 重命名 / 删除，**重命名是原位 input**（自动聚焦+全选，Enter 提交、Esc 取消、失焦提交、输入法组字中不提交）`[官方仓库]`。DSH Go：长按 → 置顶 / 重命名 / 移到文件夹 / 分享 / 多选 / 归档 / 永久删除 `[官方]`。ChatGPT：会话**左滑**露出 `⋮` 再重命名/删除 `[观察]`。 | 手机上没有 hover：**长按是主入口，条目尾部常驻 `⋯` 是备入口**。左滑是加分项，不是必需。 |
| 删除与撤销 | chat-demo：删除走确认弹窗，文案写明"不可恢复"`[官方仓库]`。DSH Go：归档后 **4 秒内可点提示条撤销**；永久删除前二次确认，且"不会偷偷改成归档" `[官方]`。 | 软删 + 4s 撤销条是标配；归档与永久删除**必须是两个动作、两套文案**。 |
| 搜索 | ChatGPT 2026-07 统一搜索：一次搜**聊天 / 项目 / 文档 / 图片**，Web/iOS/Android 全量，含免费版 `[观察]`。DSH Go：搜索找到归档会话后给「恢复并打开」`[官方]`。chat-demo：**没有**本地会话搜索（它的 search 面板是联网搜索）`[官方仓库]`。 | 搜索必须能覆盖"已归档"；结果项要能直接"恢复并打开"。 |
| 多选 | DSH Go：长按 → 多选，底部删除，失败条目逐条给原因，连接中断就停止剩余批次 `[官方]`。 | 多选放 M1 之后；但删除的"部分失败要逐条报"逻辑现在就定下来。 |
| 返回 | DSH Go 有显式导航栈：设置 → 搜索 → 轮次面板 → 抽屉 → 主页 → 才退出 `[官方]`；deepseek-api-app 同样新增 `navStack` 把返回键从"直接退出"改成逐层退 `[官方仓库]`。 | 返回键必须逐层；抽屉/弹层开着时先关它，不能直接退 App。 |

- `[推断]` 会话列表的"标题"只在用户改过名或模型生成过标题时才有意义；未命名的会话显示"新对话"比显示首句截断更好（chat-demo 就是这么做的）。
- `[推断]` 列表性能是设计项不是顺手活：chat-demo 用 `memo`、active 由外部传入；deepseek-api-app 实测"几百条消息全量渲染，切一次 5.7s，读库只要 2ms"——瓶颈全在渲染。

### 1.2 聊天页

**流式状态机（这部分最值钱，直接照字段命名）**

| 状态 | 表现 | 证据 |
|---|---|---|
| pending | 已发请求未收事件 → 3 个跳动点 | chat-demo `[官方仓库]` |
| streaming | 有内容按块追加；标题显示"正在思考"+ shimmer；每秒刷新"已思考（N 秒）" | 同上 |
| done | 收到 message_stop | 同上 |
| stopped | 用户停止或关页：**保留已收到内容**，不追加，显示"停止" | 同上 |
| error | 请求或流失败，分类成人话（网络 / Key 无效 / 余额不足 / 限流 / 繁忙 / 未知），每条带动作按钮 | 同上 |

- DSH Go 在此之上多一层**过程收纳**：工具调用等"做完的过程折叠成一行" `[官方]`；回答下方常驻操作行（复制 / 分享 / 分叉 / 重新回答），长按出完整菜单 `[官方]`。→ 对 myco 的 tool loop 直接有用。
- 生成中的输入语义（DSH Go 最值得抄的一条）：输入框为空 → 发送键变**停止**；有文字 → 直接发 = **插话**（不打断，下一步看到）；长按发送键选**插话 / 中断并发送 / 排队**；排队消息显示在对话底部，可**立即发送 / 改为插话 / 撤回**（撤回内容回到输入框）；停止后排队消息继续下一轮 `[官方]`。→ myco M2 之后必有大价值。
- 消息动作：复制后 1.5s 内变"已复制"，失败不静默（提示"复制失败，请长按选择文本"）；重新生成对某条回答重发；编辑用户消息且**重发产生新分支**；分支用 `‹ 1/3 ›` 切换且切换时用锚点修正滚动位置 `[官方仓库]`。
- 无障碍反例（重要）：Claude Android 的消息操作**只能长按**到达，TalkBack 里只有一个冗余的"打开菜单"动作 `[社区]` → myco 必须给消息行一个**可聚焦的"更多"按钮**，长按是加速手段，不是唯一路径。

**滚动贴底规则（最容易被做成 bug 的地方）**

- 贴底阈值 40px；**内容变高时不取消跟随，只有用户主动往上滚才取消**（用 `scrollTop < 上次 scrollTop` 判定）`[官方仓库]`。
- 用 `ResizeObserver` 同时观察消息列和滚动容器：内容、键盘、输入框高度任一变化都重新贴底 `[官方仓库]`。
- 切会话/切分支**不是无脑滚到底**：分支恢复用 anchor 修正，跨断点保留阅读位置 `[官方仓库]`。
- `[推断]` 反向证据：Compose 里 `LazyColumn(reverseLayout=true)` 加 key 后"新增消息不贴底"是常见坑——见 §4.2。

**输入框**

- 自适应高度 textarea（约 202px 封顶）；**移动端关掉 autoFocus**（避免一进页面弹键盘），点击时 `focus({preventScroll:true})` `[官方仓库]`。
- 回车发送、Shift+回车换行、**输入法合成中（isComposing）不发送** `[官方仓库]`；→ Compose 侧：不要依赖 `KeyboardActions(onSend)` 单键发送，用显式发送按钮 + 组字状态判断。
- 语音：豆包长按输入框或点语音键说话转文字 `[社区]`；chat-demo 录音时**用实时频谱替代文本框内容**，转写结果插入光标处 `[官方仓库]`；DSH Go "输入框为空且有语音可用时，发送键变麦克风" `[官方]`。
- 附件：DSH Go 加号 → 相册 / 拍照 / 文件，每条最多 9 个；**上传失败不会继续发送问题** `[官方]`；chat-demo "有附件上传中/失败时发送按钮禁用，失败卡片可点重试" `[官方仓库]`。
- 上下文提示可直接放在输入框上方一条细状态栏（DSH Go：`自动允许中`、`完全权限中`、`发送文字将回答待答问题`）`[官方]`。→ myco 可对应"思考开着 / 工具已授权 / 正在等确认"。

### 1.3 设置页

| 观察 | 证据 |
|---|---|
| chat-demo 设置只有两组：通用（主题三档 / 语言 / 音色 / 位置）+ 数据管理（API Key / 导入 / 导出 / 删除全部），且专门处理"切分组不丢正在输入的 Key" `[官方仓库]`。**没有** provider 管理、**没有**模型下拉 —— 这是 myco 的空白区，不能从它抄。 | `[官方仓库]` |
| DSH Go 设置覆盖面更全：对话 / 模型与渠道 / 外观（明暗、6 套配色、壁纸、对话字号、图标）/ 权限 / 语音 / 隐私与分享 / 检查更新 `[官方]`。 | `[官方]` |
| RikkaHub 文档把设置拆成 Appearance / Web Access / Backup 三块，另加 Assistants / Extensions 两个一级区 `[官方]`。 | `[官方]` |
| `[推断]` 设置页的正确单位是"一行一个决定"，不是"一屏一个表单"；provider 编辑这种重表单用全屏页，不要塞对话框（myco 现在 `ProviderEditDialog` 里有 6 个 OutlinedTextField + 滚动，手机上偏挤）。 | `[推断]` |

### 1.4 空态 / 错误态 / 断线 / 未配置

- **未配置 provider**：chat-demo 用 `ensureApiKey()` 把首启用户**强制拦到设置页**——可用但硬；`[推断]` myco 更好的做法是首屏 = 空态卡 + 主按钮"配置模型"，**允许先浏览**，但任何发送动作都指向配置。
- **错误分类**：网络 / Key 无效（附"更换 API Key"）/ 余额不足（附"前往充值"）/ 限流 / 服务器繁忙 / 未知 `[官方仓库]`。安全拒绝走模板答复而不是错误态。
- **保存失败**：挂页面内**带"重试"的横幅**，不要弹窗 `[官方仓库]`。
- **断线/后台被杀**：DSH Go 检测到 ROM 省电策略会杀后台时**主动提醒并直达对应放行设置**（vivo / 小米 / 华为 / 荣耀 / OPPO）`[官方]`；连接中断时批量删除会停下剩余批次并提示刷新列表 `[官方]`。→ myco 的 A7"尽力而为"正好需要这层引导。
- `[推断]` 四类空态不可合并：无会话 / 无 provider / 无网络 / 搜索无结果。myco `50-visual-tokens.md` §5 已列，沿用。

---

## 2. 开源项目与 DSH-GO 可抄点

> 许可差异很实际：**MIT 的可以读代码甚至搬实现；AGPL-3.0 的只学形态，不要复制源码**（myco 要分发 APK）。

| # | 项目 | 技术栈 | 形态 | 许可 |
|---|---|---|---|---|
| 1 | [deepseek-ai/chat-demo](https://github.com/deepseek-ai/chat-demo) | React 18 + TS + rspack + zustand | 官方静态 Web，768px 断点响应式 | **MIT** |
| 2 | [S-Neur/deepseek-api-app](https://github.com/S-Neur/deepseek-api-app) | 官方 chat-demo + Android WebView 壳（Java + 手写打包） | 民间"限额自救"移植 | **MIT** |
| 3 | [ccch1mneyyy/dsh-go](https://github.com/ccch1mneyyy/dsh-go) | **Flutter**（Dart）+ Kotlin Android 实现 + 内置 PRoot Linux | 复杂 coding agent 的手机客户端（本机/远程双主机） | **AGPL-3.0**（`sdk/`、`examples/` 为 MIT） |
| 4 | [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) | **Kotlin + Jetpack Compose** + Room + DataStore + Koin + Navigation 3 + M3 Expressive | 原生 Android 多 provider chat + Workspace(proot) + Phone Use | **AGPL-3.0** |
| 5 | [puppy-bai/dsh-Go](https://github.com/puppy-bai/dsh-Go) | 基于 #3 的社区增强 fork | 多步执行 / 可复用任务 / 插件 | AGPL-3.0 |

**#1 chat-demo（官方，MIT）** — 细节见 [deepseek-chat-demo-ui.md](deepseek-chat-demo-ui.md)。本轮新增判断：它的价值是**三个状态机 + 移动端细节**（五态回复、列表/消息分离加载、上传完成才可发送），不是像素。**不值得抄**：provider/模型管理、会话内搜索——它都没有。

**#2 deepseek-api-app（MIT，最像"民间移植"的样本）**
- 相对上游 `5d5dd75`：85 个文件、+9176/−222 行，**UI 主体与样式、Markdown 渲染、SSE 解析、分支逻辑全部没动**，只加外壳与交互 `[官方仓库]`。
- 值得抄：① **整页任意位置右滑开侧栏**（跟手 + 松手按速度归位），不是只点左上角；② **返回键导航栈**（设置→搜索→轮次面板→抽屉→主页→退出）；③ **按住屏幕右边缘呼出"轮次列表"**拖动定位并平滑跳转——长对话寻址的极佳方案；④ 会话**置顶**（侧栏最上方独立分组）；⑤ 长列表**只渲最近 12 条**，滚到顶部或点"显示更早"再补 12 条，**补内容时按高度差修正 scrollTop**（实测切换 5.7s → 可用）；⑥ 导入导出显示"正在打包/正在写入 x%"。
- 不值得抄：WebView 套壳本身（`file://` Origin、content provider 随机读 30ms/46B、`prefers-color-scheme` 不可靠等 6 个坑都在 README 里）——myco 是原生 Compose，这些坑不用踩；也**不要**抄它"导入=覆盖"的破坏性语义。

**#3 DSH Go（AGPL，复杂 agent 的手机形态样本）**
- 结构：本机 PRoot Linux 引擎 **或** 远程主机（SSH / DSH Remote）二选一，首启就让用户选 `[官方]`；原生界面与网页版可一键切换且停在同一条对话 `[官方]`。
- **最值得抄的 5 条**：① 过程**折叠成一行**（工具/思考不占屏）；② 生成中发消息 = 插话，长按发送键选插话/中断/排队，排队条可"立即发送/改为插话/撤回"；③ **发出第一条消息才建会话**；④ 归档 4s 撤销、归档与永久删除严格分离、搜索里可"恢复并打开"；⑤ 长按发送键这类"**次级手势承载次级语义**"的模式（主流程一键可达，复杂选择长按展开）。
- 不值得抄：桌宠 / Live2D / 酒馆 / 分享回放这类"内容生态"（myco MVP 不做）；内置 Linux 运行时的重后端（myco 用 D10 双 APK 拆分）；**AGPL 代码不要搬**。

**#4 RikkaHub（Compose，与 myco 最同构的一个）**
- 栈：Kotlin / Compose / Room / DataStore / Koin / Navigation 3 / M3 Expressive / OkHttp / kotlinx.serialization `[官方仓库]`。
- 能力面与 myco 野心高度重叠：多 provider 切换、**Workspace（proot Linux agent 环境）**、Phone Use、MCP + Skills、文件记忆、Markdown+KaTeX+Mermaid、provider 二维码导入导出、SillyTavern 角色卡导入 `[官方仓库]`。
- 值得抄：**"chat 与 workspace 在同一个 app 里共存"的导航组织**（一级区 = Chat / Assistants / Extensions，而 Workspace 是 Extensions 里的一个二级页）；Material 3 Expressive 的取舍（它敢用新 M3 API，说明 1.3.x 之后可跟进）。
- 不值得抄：它的功能宽度（MCP/Skills/生图/视频/翻译/角色卡）远超 myco MVP；而且**它明确不接受 PR**、AGPL——只能看形态。
- `[推断]` 这是 myco 最该"对着看"的一个：如果 myco 未来要合壳，RikkaHub 已经把"chat 一级 + agent 能力二级"的 IA 走通了。

**#5 puppy-bai/dsh-Go（社区 fork）** — 只在"生态自然分叉"这个现象上有参考价值（官方仓库之外会长出增强版，带来签名冲突、隐私风险、权限膨胀）。`[推断]` 对 myco 的启示：**插件/皮肤入口要官方可控**，否则用户会去装来路不明的 fork。

---

## 3. chat/work 双形态的先例与推荐

### 3.1 先例（诚实版：**"同一 app 两种模式"在 2026 年已经有明确赢家形态 = 底部 Tab**）

| 先例 | 形态 | 证据 |
|---|---|---|
| **Claude 移动端 2026 改版** | 底部 Tab：**Chat / Projects / Code / Artifacts**；侧栏专门放会话历史；官方文档明确"Claude Code 没有独立 app，云会话与 Remote Control 都在 Claude app 的 **Code** tab 里"，Code tab 内是多会话列表 + 推送通知 + 审批 | `[观察]` The Verge / dev.ua；`[官方]` [Claude Code on mobile](https://code.claude.com/docs/en/mobile.md) |
| **ChatGPT 2026 改版** | 同期上底部 Tab；侧栏浮动、切会话自动收起；统一搜索覆盖聊天/项目/文档/图片 | `[观察]` The Verge / notebookcheck；`[官方]` release notes |
| **Pi（Inflection）** | **单一连续会话**，多数平台不按"会话"分历史条目 | `[社区]` |
| **DSH Go** | 不做双 Tab，改做**一个对话面 + 「全部功能」抽屉 + 双主机切换 + 原生/网页双界面**；work 面（终端/子代理/工作区/自动化）全收进二级入口 | `[官方]` |
| **RikkaHub** | 一级 = Chat / Assistants / Extensions，**Workspace 是 Extensions 下的二级页** | `[官方仓库]` |
| Slack / Notion 的 workspace 切换器 | 成熟但**切换成本高**（换的是"整个上下文"），对陪伴型产品不合适 | `[推断]` |

- 关键分歧点：Claude/ChatGPT 用**底部 Tab 并列**两个模式；DSH Go/RikkaHub 用**主面 + 二级抽屉**。前者入口更显眼，后者更省一级导航。
- `[推断]` 陪伴型产品不能学 Slack 式"切 workspace 就换整个人"——那等于"换了一个她"，直接违反 myco "一个她" 的人格约束。

### 3.2 三种可选形态（给 M4 用）

**形态 A：底部双 Tab（推荐给"将来合壳"）**
- `[对话]` Tab：**单焦点当前对话** + 顶栏左侧「历史」；`[工作]` Tab：多会话列表 + 任务/工具。
- 优点：Claude/ChatGPT 已验证、入口 0 层不可丢、chat 与 work 数据同源但视图分离；缺点：底部 Tab 会长期占 ~56dp，且和"两个 APK"（D10）冲突。
- 前置条件：D10 若改为合壳。**M4 不做，但组件层要按"可被底部 Tab 承载"来写**（各屏自带 TopBar，不依赖外层）。

**形态 B：单 app + 「更多」抽屉（M4 最小改动，推荐）**
- chat 默认面 = 当前对话；顶栏左上 `☰` 打开抽屉，抽屉分两段：**上段"对话"**（最近 5–8 条 + 「全部历史」+「搜索」），**下段"另一个界面"**（work / 数据管理 / 设置）。
- 优点：一处入口同时解决"找历史"和"去 work"，不留空抽屉；缺点：chat 的"单焦点感"被抽屉削弱 —— 用"默认关闭 + 不常驻"处理。
- 这一条与 DSH Go 的"全部功能"抽屉、RikkaHub 的 Extensions 二级页同构，**风险最低**。

**形态 C：双 APK 各自形态（当前 D10 的直译，M4 落地版）**
- chat APK：**单焦点**当前对话 + 「历史」次级页（搜索 / 归档恢复 / 删除）；work APK：多会话列表 + 工作区。
- 优点：不引入任何新导航概念、改动最小、符合"逻辑先行"；缺点：用户要换 app 才能开新任务，跨 app 的"她记得刚才"要靠同步。
- **建议：M4 就按 C 交付，同时把 B 的抽屉结构留成组件**（抽屉里那一段"另一个界面"先只放设置与数据，不放假入口）。

### 3.3 反用户防护清单（无论选哪个形态，以下 6 条必须成立）

1. **历史永远可达**：chat 单焦点也必须有"全部历史"入口（抽屉上段 / 顶栏左侧），**不允许留空抽屉或空 Tab**——空入口比没有入口更反用户。
2. **误删可撤销**：删除走软删 + 4s 撤销条；永久删除二次确认并写清"不可恢复"。
3. **归档不是删除**：归档可从搜索/归档页"恢复并打开"；两套文案不混。
4. **切换不丢上下文**：切模式/切会话后输入草稿要保留（chat-demo 专门处理"切分组不丢正在输入的 Key"，同理）。
5. **通知能直达**：主动消息/任务完成的通知点击直接落到对应会话，而不是首页。
6. **副入口不承载主功能**：把"新建任务"藏在二级抽屉里 = 每周都要多点两次；work 的主按钮必须在一级。

---

## 4. Compose 实现要点（含毛玻璃/发光/贴底/键盘）

> 现有工程基线：Compose BOM `2024.10.01`（Compose 1.7.x / Material3 1.3.x）、`minSdk 26`、`targetSdk 35`、Kotlin 2.x + `kotlin("plugin.compose")`。

### 4.1 会话列表：LazyColumn + 长按菜单

- `LazyColumn` + `items(key = { it.id })`（**key 必须有**，否则改名/置顶会整列重排闪烁）。
- 分组用 `stickyHeader`（1.7 仍标 `@ExperimentalFoundationApi`）；分组数据用纯函数 `groupSessions(list, now)`，**时钟每跨午夜/页面可见时重算**（照抄 chat-demo 的五档）。
- 长按：`Modifier.combinedClickable(onClick=…, onLongClick=…)`（若编译报实验性，补 `@OptIn(ExperimentalFoundationApi::class)`）；长按同时触发 `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)`。
- 长按菜单承载物：条目少（≤5）用 `DropdownMenu` 锚在条目上；条目多/带二级（移到文件夹、分享）用 `ModalBottomSheet`。**条目尾部再加一个常驻 `⋯` IconButton**（无障碍要求，见 §4.6）。
- 删除确认：`AlertDialog` 写清"该对话及附件不可恢复"；确认后**先软删 + 显示 `SnackbarHost` 撤销条（4s）**，超时才真删。
- `[推断]` 列表项高度固定（标题 1 行 + 时间 1 行）可以让滚动最稳；摘要行不要做多行省略。

### 4.2 流式滚动贴底

推荐配方（不用 `reverseLayout`）：

```kotlin
val listState = rememberLazyListState()
val atBottom by remember {
    derivedStateOf {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()
        last == null || last.index >= info.totalItemsCount - 1 &&
            last.offset + last.size <= info.viewportEndOffset + 40   // 40px 阈值
    }
}
// 用户上滑即取消跟随（非 suspend 读取，避免每帧 recompose）
var follow by remember { mutableStateOf(true) }
LaunchedEffect(listState) {
    snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
        if (scrolling) follow = atBottom
    }
}
// 内容增长 / 键盘变化 / 新消息：只在跟随时贴底，且不用动画
// imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)，键盘升降时也要重新贴底
LaunchedEffect(lines.size, streamingText.length, imeBottom) {
    if (follow) listState.scrollToItem(maxOf(0, lines.size - 1))
}
```

- **不要**每个 token 都 `animateScrollToItem`：会和用户手势打架并掉帧；流式期间用 `scrollToItem`（无动画），用户主动发送新消息时才 `animateScrollToItem`。
- `reverseLayout = true` 的坑：**加 key 后新增项不自动贴底**、底部 item 内容增高时不重排；社区结论是"要么不用 reverse，要么每次追加后主动 `scrollToItem(0)`"`[社区]`。myco 选"不用 reverse + 手动贴底"。
- 长会话寻址：抄 deepseek-api-app 的"右缘手势呼出轮次列表"或退一步做"回到最新"浮动按钮；`[推断]` MVP 先做浮动按钮足够。
- 流式渲染节流：chat-demo 按实测渲染耗时×2 节流（上限 2s）合并 token `[官方仓库]`；Compose 侧对应"每帧最多合并一次 state 更新"+ 长文本用 `Text` 而非每 token 重建 `AnnotatedString`。

### 4.3 键盘与安全区

- `targetSdk 35` 在 Android 15+ **强制 edge-to-edge** `[官方]`：必须 `enableEdgeToEdge()`（activity-compose 1.9.2 已有），并自己消费 insets。
- 输入区：`Scaffold` 的 content 里给外层 `Modifier.navigationBarsPadding()`，输入容器再 `Modifier.imePadding()`；**`imePadding()` 必须放在 `verticalScroll()`/`weight(1f)` 之前**，否则键盘弹出时列表不缩 `[社区/官方 edge-to-edge 指南]`。
- **不要同时用** `contentWindowInsets = WindowInsets.safeDrawing` + `imePadding()` —— 会双重 padding `[社区]`。选一种：要么 `Scaffold(contentWindowInsets = WindowInsets(0))` 全手动，要么用 `Scaffold` 默认 + 只补 `imePadding()`。
- 不自动聚焦：`TextField` 不要 `focusRequester.requestFocus()`；点击才升键盘（chat-demo 也关了 autoFocus `[官方仓库]`）。
- 键盘升起时贴底规则要复用 §4.2 的 `follow`（键盘变化会改变 viewport，不要把它当"用户上滑"）。
- `[推断]` 输入法中文组字期间不要触发"回车发送"：用 `KeyboardOptions(imeAction = ImeAction.Default)` + 手动发送按钮，比强依赖 `onSend` 更稳。

### 4.4 暗色 / 浅色主题

- 现有 `MycoTheme(mode)` + `LocalMycoColors` + `MycoSpacing/MycoRadius` 的结构是对的：组件零硬编码 hex，三档（系统/浅/深）。保持。
- `[推断]` **不要**接 Material You 动态取色：品牌色卡（`#B0C591` / `#E09A42`）是产品气质，动态取色会把它冲掉。`50-visual-tokens.md` 的语义映射优先。
- 对比度：`onSurfaceVariant` 在浅色 `#5A5F52` / 背景 `#FAFDF3` ≈ 6.5:1，达标；深色 `#C9C2B6` / `#161412` 更高。**新增的任何 warmContainer 文字色都要测一遍 AA** `[推断]`。
- 字号：全部用 `sp`（跟随系统字体缩放）；`bodyLarge 16sp` 在 1.3x 缩放下会变 20.8sp，行高要跟着走（`lineHeight` 用 sp 定义即可）。

### 4.5 基础毛玻璃与边缘发光（**真实 backdrop blur 的限制要说清**）

- **Compose/官方只有"图层模糊"，没有"背景模糊"**：`Modifier.blur(radius)` 模糊的是**这个 composable 自己画的内容**（layer/surface blur），不是它背后已有的内容；且 `[官方]` 该 modifier **在 API 31 以下被忽略**（`BlurredEdgeTreatment` 默认会按内容边界裁切）。`[官方]` [Blur effects overview](https://developer.android.com/develop/ui/compose/graphics/blur/overview)
- 所以"毛玻璃卡片"只有三条路：
  1. **半透明 tint + 细边框（现状，推荐 M1–M2）**：零性能成本、全 API 可用、不闪。`GlassSurface` 已实现。`[推断]` 视觉上做到"像玻璃"靠的是边框高光 + 轻微渐变更稳。
  2. **Haze 库**（`dev.chrisbanes.haze`）：真正的 backdrop blur；**API 32+ 才可靠**，API 31 有性能与正确性 caveat（会跳过 RenderNode 实现），API 31 以下走 RenderScript 回退 `[官方]` [Haze platforms 文档](https://github.com/chrisbanes/haze/blob/main/docs/blur/platforms.md)。代价：多一个依赖 + 每帧重绘开销。
  3. 手写 `graphicsLayer { renderEffect = ... }`（API 31+）：等于自己实现 Haze 的子集，不建议。
- `[推断]` 结论：**M6 再评估 Haze，且要做成"低端机关闭"开关**；M1–M5 保持 tint 方案。真模糊只放在**一个**位置（输入区/底部弹层），不要用在滚动列表项上。
- 边缘发光：不用 `Modifier.shadow`（`spotColor/ambientColor` 在 API 28+ 才生效，行为不一致 `[推断]`），用**径向渐变 + drawBehind**：

  ```kotlin
  Modifier.drawBehind {
      drawRect(
          Brush.radialGradient(
              colors = listOf(glow.copy(alpha = .14f), Color.Transparent),
              center = Offset(size.width / 2, size.height),
              radius = size.maxDimension * .8f,
          )
      )
  }
  ```

  规则沿用 `50-visual-tokens.md` §3：深色模式**只给一个焦点元素**、8–16% alpha、不做大面积填充、浅色模式不发光。

### 4.6 无障碍（移动端最常被跳过的验收项）

- 触摸目标 ≥48dp：用 `Modifier.minimumInteractiveComponentSize()`（M3 内置），图标按钮别做 24dp。
- **长按不能是唯一入口**：每个"长按出菜单"的条目都配一个 `IconButton`/`⋯` 且 `contentDescription = "更多操作"`（Claude 的反例见 §1.2）。
- 语义：`Modifier.semantics(mergeDescendants = true) { contentDescription = "…"; role = Role.Button }`；会话项读出来应是"标题，时间，未读，按钮"。
- 流式输出：TalkBack 会逐次朗读变化文本 —— 给流式区域 `liveRegion = LiveRegionMode.Polite` **或**用一个独立的"正在生成"状态节点播报，避免屏幕阅读器被 token 轰炸 `[推断]`。
- 对比度 ≥4.5:1（正文） / 3:1（大字与图标）；错误态不能只靠颜色（加图标 + 文案）。
- 手势冲突：抽屉边缘手势不要在可横向滚动的代码块/表格上触发（DSH Go 明确处理了这条 `[官方]`）。
- 返回：用 `PredictiveBackHandler`（activity-compose 1.9）逐步退，抽屉/弹层优先消费返回事件。

---

## 5. 对 M1 / M4 / M6 的建议

### 5.1 M1 必须做（12 件，每件一句理由）

| 组件 | 现状（当前未提交 `ui/`） | 理由 |
|---|---|---|
| 会话列表项 + 五档时间分组 | 有平铺列表，无分组 | 分组是"历史可扫读"的最低成本手段 `[官方仓库]` |
| 列表项长按菜单（重命名/置顶/归档/删除） | **缺** | 没有它 M4 的会话管理无从落地 |
| 重命名：原位 input（自动聚焦+全选+Esc 取消） | **缺** | 抄官方 demo 的最小正确实现，别开对话框 |
| 删除二次确认 + 4s 撤销 | 缺撤销 | "别反用户"的第一条硬约束 |
| 搜索入口（含归档） | **缺** | 找不到历史 = 产品不可信 |
| 消息长按菜单（复制/重试/引用/选择文字） | **缺** | 流式客服式交互的基本盘 |
| 停止生成 + 停止态气泡 | 有 `stopped` 形参，无入口 | "停止"是状态不是异常 `[官方仓库]` |
| 失败气泡 + 分类文案 + 重试按钮 | 只有一行红字 | 错误必须可操作 |
| 输入区 `imePadding` + 不自动聚焦 | **缺**（当前键盘会挡输入区） | 移动端第一体验 bug |
| 空态 5 类（无会话/无 provider/无网络/搜索无结果/未配置） | 仅 1 条文案 | 首启不撞墙 |
| 流式贴底 + 上翻不拽回 | **缺**（当前不滚动） | 最容易被做成"bug"的地方 |
| 消息行"更多"按钮（a11y 备入口） | **缺** | 长按不可达 = 无障碍不合格 |

### 5.2 M1 可以延后（M2+ / M6）

- 附件/拍照/相册/文件（M5 计划里已有）、语音输入、TTS 朗读。
- 分支切换 `‹ 1/3 ›`、编辑用户消息重发（等 tool loop 稳定后）。
- 多选批量删除、文件夹/分组管理、置顶排序自定义。
- 真模糊（Haze）、插画、装饰动效、Mermaid/KaTeX。
- 会话内跳转轮次面板（先给"回到最新"按钮）。

### 5.3 M4 形态推荐

1. **主推：形态 C 落地 + 形态 B 预留** —— chat APK 做**单焦点当前对话**；「历史」作为顶栏一级按钮（不是空抽屉），内含搜索 / 五档分组 / 归档恢复 / 删除；work APK 保留多会话列表。理由：符合 D10 双 APK，且**不新增导航概念**，返工成本最低。
2. **抽屉写法**：如果一定要抽屉，抽屉上段放"最近对话 + 全部历史 + 搜索"，下段放"设置 / 数据管理"；**禁止留空段或空 Tab**（`60-mvp-closed-loop-plan.md` 里"抽屉先留空"这条建议要改）。
3. **合壳的时机**：等 work 真能跑任务（I5）再上底部双 Tab（形态 A）；现在上只会多一层空壳。
4. **验收补一条**：把"从 chat 找到 30 天前某条消息并继续对话"做成一条真机脚本路径，三步内完成——这是 M4 唯一的硬指标。
5. **删除语义三件套**：软删（可撤销）→ 归档（可恢复）→ 永久删除（二次确认）。三套文案不合并；子/关联任务存在时**拒绝而非静默降级**（学 DSH Go"不偷偷改成归档"）`[官方]`。

### 5.4 M6 视觉细化优先级（高 → 低）

1. **深色发光只留一处**（输入区或当前会话卡片），alpha 8–16%，浅色模式换边框层级；这是最便宜的"气质"来源。
2. **气泡层级与留白**：用户/助手气泡现在形状差异已对，但缺"分组间距 vs 单条间距"的区分（同一轮多条紧贴、不同轮松开）。
3. **空态插画/文案**（1 张就够），以及流式光标 `▍` 的呼吸感。
4. **微动效**：仅保留 150–200ms 的功能性过渡（消息入场、抽屉、按钮按下）；不做装饰循环动画。
5. **字体**：`50-visual-tokens.md` 的待定项（中文系统字体 vs 品牌字体）必须先做一次并排截图再定。
6. 真模糊（Haze）放最后，且必须可关。

---

## 6. 不要抄清单

1. **只靠长按可达的关键操作**——TalkBack 用户直接没有这条路（Claude Android 已踩）`[社区]`。
2. **删除无确认 / 有确认但无撤销**——四秒撤销条的成本极低。
3. **"永久删除"和"归档"共用一套文案或同一个 API**——用户会以为删的是书签，实际删的是数据。
4. **把入口藏进空抽屉/空 Tab**——空入口比没有入口更反用户（myco 原计划"抽屉先留空"应改）。
5. **强制装饰动画 / 无法关闭的动效**——尤其影响低端机与无障碍（`prefers-reduced-motion` 在 Android 对应"移除动画"开发者选项）。
6. **把"停止生成"实现成异常/错误态**——它必须是一个正常的、可持久的状态。
7. **流式中每 token 重排/重解析整段 Markdown**——chat-demo 专门做了节流；这是滑动卡顿的元凶。
8. **用户上翻看历史时把视图拽回底部**——单条 bug 就足以让产品"不可用"。
9. **首启强制跳设置页**（chat-demo 的做法）`[官方仓库]`——首屏可以是空态卡，但**任何发送动作**指向配置，不要把人拦在门外。
10. **给普通聊天塞"provider / 参数"这种工程概念到一级界面**——放设置里、放"关于"里都可以。
11. **一次渲染整个长会话**（deepseek-api-app 实测切一次 5.7s）`[官方仓库]`。
12. **把"切换 workspace = 换一个 She"**——陪伴产品的身份连续性不能靠导航切换。
13. **静默失败**（保存失败用弹窗挡一下、上传失败继续发、工具失败不报 reason）。
14. **直接搬 AGPL 项目源码**（DSH Go / RikkaHub）——只学形态，myco 要分发 APK。

---

## 7. 未找到 / 存疑

- **GitHub 直连在本机不可达**（`fetch failed`）——第 2 节所有仓库结论来自 jsDelivr 镜像 `@main`/`@master` 的 README；`@main` 会随上游变化，复核时请固定 commit。星数/版本号来自搜索摘要（如 DSH Go ≈2.1 万 star、v1.0.15–1.0.18），**未在 GitHub 页面上核对**。
- **DSH Go / RikkaHub 的 UI 细节来自官方文档与 README 文字**，我没有看到真实截图（图是 README 里的 webp，未加载图像）；像素级（间距/字号/圆角）本报告不覆盖。
- **ChatGPT / 豆包的官方设计规范未找到**：其行为结论来自 release notes 摘要、媒体教程与社区文章，标注为 `[观察]`/`[社区]`，可信度中等；"左滑露出 ⋮" 这一条只有二手来源。
- **Claude 底部 Tab 的官方设计文档未找到**：Tab 组成（Chat/Projects/Code/Artifacts）来自 The Verge/dev.ua 报道 `[观察]`；"Code tab 存在且是多会话客户端"来自 Claude Code 官方文档 `[官方]`。结论方向可信，但**像素级形态未核**。
- **Haze 在 API 31 的性能/正确性 caveat** 来自其官方 `docs/blur/platforms.md` 与 changelog，**未在本机真机验证**；若 M6 采用，必须做一次 API 26/31/33+ 三档实测。
- **`stickyHeader` / `combinedClickable` 的实验性标注**按 Compose 1.7 文档判断，实际编译时以工程报错为准（加 `@OptIn(ExperimentalFoundationApi::class)` 即可）。
- **未见任何"chat/work 双形态"的移动端对照实验数据**（比如底部 Tab vs 抽屉的留存差异）——§3.2 的推荐是基于形态先例 + myco 的 D10 约束的 `[推断]`。
- **本报告未在真机/模拟器运行任何产品**，也未调用模型服务；M1 现状表基于工作区当前未提交的 `app-android/ui/` 代码，可能与 lead 正在进行的改动不一致。

**来源汇总**（2026-11-26 抓取）：[chat-demo](https://github.com/deepseek-ai/chat-demo) · [deepseek-api-app](https://github.com/S-Neur/deepseek-api-app) · [dsh-go](https://github.com/ccch1mneyyy/dsh-go) · [dsh-go 对话指南](https://github.com/ccch1mneyyy/dsh-go/blob/main/docs/guide/zh/01-chat.md) · [RikkaHub](https://github.com/rikkahub/rikkahub) · [RikkaHub 文档](https://docs.rikka-ai.com/introduction) · [Claude Code on mobile](https://code.claude.com/docs/en/mobile.md) · [The Verge：Claude 移动端新设计](https://www.theverge.com/ai-artificial-intelligence/1039188/claude-has-a-new-mobile-design-too) · [ChatGPT release notes](https://help.openai.com/en/articles/6825453-chatgpt-release-notes) · [ChatGPT 统一搜索](https://www.notebookcheck.net/ChatGPT-New-search-finds-your-old-chats-images-and-documents.1344318.0.html) · [Compose Blur 概览](https://developer.android.com/develop/ui/compose/graphics/blur/overview) · [Haze 平台说明](https://github.com/chrisbanes/haze/blob/main/docs/blur/platforms.md) · [Android edge-to-edge](https://developer.android.com/develop/ui/compose/system/setup-e2e)

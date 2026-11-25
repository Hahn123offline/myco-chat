# DeepSeek chat app demo / 最小 chat UI 参考

> 抓取日期 **2026-11-25**。全程只读公开网页与仓库源文件（GitHub 直连被网络挡住，改走 jsDelivr 镜像读同一份文件）；
> **未调用任何模型 API、未 clone、未安装**。
> 证据口径：`[官方]` = api-docs.deepseek.com / deepseek-ai 组织；`[仓库]` = chat-demo 源码文件（镜像读取）；
> `[社区]` = 第三方镜像/搜索摘要；`[推断]` = 我的解读，未经运行验证。

## 0. 一页结论

1. **用户说的 demo 确实存在，而且就是官方开源的那一个**：`deepseek-ai/chat-demo`，MIT，静态前端，直连 `api.deepseek.com`，无应用后端。[官方] [公告](https://api-docs.deepseek.com/news/news261113/) · [指南](https://api-docs.deepseek.com/guides/build_a_chatbot/) · [仓库](https://github.com/deepseek-ai/chat-demo)
2. **它不是原生 app，是响应式 Web（768px 断点）**；移动端形态 = 抽屉会话列表 + 顶栏 + 吸底输入框 + 底部弹层设置，正好对上「会话列表 / 聊天框 / 设置页」三件套。[仓库] `src/app/viewport.ts` `src/components/sider/Sider.tsx`
3. 技术栈：**React 18.3.1 + TypeScript + rspack 2 + zustand 4.4.7**，无 UI 框架、无 markdown→HTML 转换（自写 mdast→React 渲染），Markdown 能力由 prismjs/KaTeX/Mermaid 补齐。[仓库] [package.json](https://cdn.jsdelivr.net/gh/deepseek-ai/chat-demo@main/package.json)
4. **最值钱的不是像素，是三个状态机**：`pending/streaming/done/stopped/error` 的回复状态、会话列表与消息分离的加载、输入框「上传完成才可发送」。这三处直接决定"用起来像不像官方"。[仓库] `src/services/chat/types.ts` `MessageList.tsx` `ChatInput.tsx`
5. 它**没有**模型切换 UI、**没有** provider 管理（只有 API Key 一个输入框）、**没有**会话内全文搜索、**没有**多设备同步。想要这几样，得自己设计，不能从它抄。[仓库] `src/components/settings/SettingsDialog.tsx`；`[推断]`
6. 对 myco 的正确用法：**抄结构 + 抄状态机 + 抄移动端细节（键盘/滚动/首 token），不抄 provider 与模型选择**（那两样 myco 比它强，要自己定）。

## 1. 找到的仓库/资料

| 对象 | 地址 | 栈 / 形态 | 许可证 | 可信度 |
|---|---|---|---|---|
| 官方公告（全家桶 API，点名 demo） | [news261113](https://api-docs.deepseek.com/news/news261113/) | — | — | `[官方]` 高 |
| 官方指南（列 demo 功能清单） | [build_a_chatbot](https://api-docs.deepseek.com/guides/build_a_chatbot/) | — | — | `[官方]` 高 |
| 仓库 chat-demo | [github.com/deepseek-ai/chat-demo](https://github.com/deepseek-ai/chat-demo) | React 18 + TS + rspack + zustand，静态站 | **MIT** | `[官方]`/`[仓库]` 高 |
| 在线 demo | [cdn.deepseek.com/chat-demo](https://cdn.deepseek.com/chat-demo) | 同一份 `index.html`，标题「API 使用 demo」 | 同上 | `[官方]` 中（只读到 title） |
| 镜像（内容与 GitHub 同源） | [atomgit.com/gh_mirrors/chatdem/chat-demo](https://atomgit.com/gh_mirrors/chatdem/chat-demo?tab=license) | 标 TypeScript / MIT / 14 commits | MIT | `[社区]` 中 |

- **GitHub 直连在本机失败**（`github.com` 与 `raw.githubusercontent.com`、`api.github.com` 均 fetch failed，2026-11-25 多次不重试）；
  改为 `cdn.jsdelivr.net/gh/deepseek-ai/chat-demo@main/...` 读取同一份源文件，文件路径与 README 描述一致，判断可信。[推断]
- 版本 `0.1.4`，Node 22.12+ / pnpm，`pnpm dev` 起 8090 端口。[仓库] [README](https://cdn.jsdelivr.net/gh/deepseek-ai/chat-demo@main/README.md) [package.json](https://cdn.jsdelivr.net/gh/deepseek-ai/chat-demo@main/package.json)
- 关键依赖（直接决定能力边界）：`react 18.3.1`、`zustand 4.4.7`、`prismjs 1.30`、`katex 0.16.22`、`mermaid 11.10.1`、`idb 8.0.3`（IndexedDB）、`fflate 0.8.3`（ZIP 导入导出）、`mdast-util-*`（自己渲染）。同前。
- 协议：**Anthropic-compatible Messages API**（`/anthropic/v1/messages`）+ Files API；默认模型 `deepseek-flash`；语音走 ASR/TTS 端点。[仓库] README；[官方] 指南

## 2. 界面骨架（屏与导航）

**没有 router**，单页 + 三个全局浮层：`activeId` 为 null 显示 Welcome，否则显示 SessionView；`SettingsDialog`、`SearchPanel`、`Toast` 常驻顶层。[仓库] `src/app/App.tsx`

```
桌面（≥768px）                      移动（<768px，单列）
┌──────────┬──────────────────┐    ┌──────────────────┐
│ Sider    │ 标题栏（会话名）  │    │ MobileHeader     │ ← ☰ / 标题 / ＋
│ appName  │──────────────────│    │  消息区（滚动）   │
│ ＋开新对话│ 消息区（滚动）    │    │                  │
│ 分组列表 │  ⋮               │    │ 输入框（吸底）    │
│          │──────────────────│    │ 免责声明          │
│ 设置     │ 输入框 + 免责声明 │    └──────────────────┘
└──────────┴──────────────────┘    Sider → 抽屉；Settings → 底部弹层
```

| 屏 | 桌面形态 | 移动形态 | 组成（组件文件） |
|---|---|---|---|
| 会话列表 | 常驻左栏，可折叠成细条 | 点击 ☰ 的抽屉，点遮罩关闭 | `Sider`/`CollapsedSider`/`MobileSider`/`SessionItem` |
| 会话（聊天） | 标题栏 + 消息列 + 吸底输入 | 顶栏（☰/标题/新建）+ 消息列 + 吸底输入 | `SessionView`/`MobileHeader`/`MessageList`/`ChatInput` |
| 新对话 | 居中问候语 + 输入框 | 同左，输入框吸底 | `Welcome`（问候语只有一句 `今天有什么可以帮到你？`） |
| 设置 | 居中弹窗，左侧两个分组 | 底部弹层，导航横排在内容上方 | `SettingsDialog`（17.9 KB 单文件） |
| 其他浮层 | 搜索结果面板、Toast、各种 Modal | 同左 | `search/SearchPanel`、`base/Toast`、`base/Modal` |

- 「会话列表」与「会话消息」**分离加载**：列表只拿 `SessionSummary`（id/title/createdAt/updatedAt），点开某会话才加载消息。[仓库] `src/services/chat/types.ts`、`App.tsx` 的 `sessionCache`/`sessionLoadErrors`
- 首次使用被 `ensureApiKey()` 拦到设置页——**空 app 的第一个强制动作是配 Key**，不是聊天。[仓库] `App.tsx`、`components/settings/settingsDialogState.ts`

## 3. 聊天页交互清单（流式、停止、重生成、markdown、滚动、键盘）

### 3.1 流式状态机（照抄这一层）
- 回复状态只有 5 个：`pending`（已发请求未收事件）/ `streaming` / `done`（收到 `message_stop`）/ `stopped`（用户停止或关页）/ `error`（请求或流失败）。[仓库] `src/services/chat/types.ts`
- 内容按**块**存：`thinking`、`text`（带 citations）、服务端工具块（`server_tool_use`/`web_search_tool_result`/`web_fetch_tool_result`）按到达顺序追加；续写把新块**追加**到已有块后，而不是重开一条消息。[仓库] `types.ts`、`src/services/chat/reply.ts`
- 流式期间按钮位变成**停止**；停止后不再追加，保留已收到的内容，搜索步骤显示「搜索已停止」。[仓库] `ChatInput.tsx`、`ReplyArea.tsx`
- 流式与"正在等"分开：`streaming && 没有任何块` 时显示 3 个跳动圆点；有思考内容前标题显示「正在思考」并带 shimmer 动画。[仓库] `AssistantMessage.tsx`、`ReplyArea.tsx`

### 3.2 消息动作（都在气泡下方，最后一条常显、其余 hover/长按显）
| 动作 | 行为细节 | 出处 |
|---|---|---|
| 复制 | 复制成功 1.5s 内变「已复制」；失败不静默，提示「复制失败，请长按或选中文本后复制」 | `CopyButton.tsx`、`i18n/zh.ts` |
| 重新生成 | 对某条回答重发请求；流式中禁用 | `AssistantMessage.tsx` |
| 继续生成 | 仅最后一条、且满足可续写条件时出现，按钮文案「继续生成」 | 同上 + `services/chat/reply.ts` |
| 编辑用户消息 | 原位变 textarea（最高 202px），取消/发送两键；Enter 提交（桌面）、Esc 取消；**重发产生新分支** | `UserMessage.tsx` |
| 版本切换 | `‹ 1/3 ›` 分支切换器，切换时保持切换器在视口原位（先记 anchor 再按差值修正 scrollTop） | `BranchSwitcher.tsx`、`MessageList.tsx` |
| 朗读 | 回答转 TTS 播放，切换会话自动停 | `ReadAloudButton.tsx`、`App.tsx` |

### 3.3 Markdown / 代码 / 公式 / 图表
- 自写 **mdast → React** 渲染，不走 HTML 字符串（避免 XSS 与重排）；GFM 表格 + 数学（`mdast-util-math` + KaTeX）+ 代码高亮 + Mermaid。[仓库] `components/markdown/*`、`package.json`
- 代码块：标题栏左侧语言名、右侧「复制」；语言包**按需加载**，未加载时按纯文本显示不闪烁；Mermaid 代码块可在「图表 / 代码」间切换。[仓库] `CodeBlock.tsx`
- 流式渲染**按实测渲染耗时节流**：提交后记录耗时，下次更新至少等 `耗时×2`（上限 2s），连续 token 合并为最新内容——防止长回答把主线程打满。[仓库] `useThrottleMarkdownRender.ts`

### 3.4 滚动（这个最容易被忽略）
- 「贴底」阈值 40px；**内容变高时不取消跟随，只有用户主动往上滚才取消**（代码用 `el.scrollTop < 上一次 scrollTop` 判定）。[仓库] `MessageList.tsx`
- 用 `ResizeObserver` 同时观察消息列和滚动容器：内容、键盘、输入框高度任一变化都重新贴底。[仓库] 同上
- 切换会话/切换分支时**不是无脑滚到底**：分支恢复用 anchor 修正，跨断点切换保留用户阅读位置。[仓库] 同上

### 3.5 输入框
- 自适应高度 textarea（`rows=2`，另有一份 mirror 撑高）；**移动端 `autoFocus` 关闭**（避免一进页面就弹键盘），点击时手动 `focus({preventScroll:true})` 防 Safari 把页面滚走。[仓库] `ChatInput.tsx`
- 回车发送、Shift+回车换行、**输入法合成中（`isComposing`）不发送**；粘贴文件、拖拽文件都进附件队列。[仓库] 同上
- 「深度思考 / 智能搜索」两个开关在输入框内部（不是设置页），窄屏时自动折叠成图标（ResizeObserver 量 `scrollWidth > clientWidth`）。[仓库] 同上
- 语音输入：录音时移动端**用实时频谱替代文本框内容**；转写结果插入光标处并把光标移到插入内容之后。[仓库] `Spectrum.tsx`、`useVoiceInput.ts`
- 有附件在上传/失败时**发送按钮禁用**，失败卡片可点击重试。[仓库] `ChatInput.tsx`、`DraftAttachments.tsx`

### 3.6 错误与提示
- 错误按类映射成人话：网络 / Key 无效（附「更换 API Key」）/ 余额不足（附「前往充值」）/ 触发限流 / 服务器繁忙 / 未知。[仓库] `services/chat/describe.ts`
- 安全拒绝走模板答复（「你好，这个问题我暂时无法回答，让我们换个话题再聊聊吧。」），思考与正文按协议丢弃。[仓库] `AssistantMessage.tsx`、`describe.ts`
- 附件失败只显示「上传失败」，具体原因用 toast；保存失败在顶部挂一条**带「重试」的横幅**而不是弹窗。[仓库] `App.tsx`

## 4. 会话列表与设置页

### 4.1 会话列表（`Sider` / `SessionItem`）
- 顶部：产品名 + 收起按钮；下面是独立的大按钮「**开启新对话**」。[仓库]
- 分组固定五档：**今天 / 昨天 / 7 天内 / 30 天内 / 按月**（`sessionGroups.ts`），分组标签随时钟刷新（跨午夜 + 页面重新可见时重算）。[仓库]
- 单项：标题（无标题显示「新对话」）+ `⋮` 菜单（**重命名 / 删除**）；重命名是**原位 input**：自动聚焦并全选，Enter 提交、Esc 取消、失焦提交，中文输入法合成中不提交。[仓库] `SessionItem.tsx`
- 删除走确认弹窗，文案明确「该对话及其中上传的文件将不可恢复」；失败 toast「删除对话失败，请重试」。[仓库]
- 桌面可折叠成细条（`CollapsedSider`）；移动端抽屉点遮罩/选中会话/切布局自动收起。[仓库] `App.tsx`
- 列表项用 `memo`，`active` 高亮由外部传入——**列表性能是明确设计项**，不是顺手。[仓库]

### 4.2 设置页（只有两个分组，值得注意"克制"）
| 分组 | 条目 | 说明 |
|---|---|---|
| 通用设置 | 主题（浅色/深色/跟随系统，三选块）、语言（简中/English）、朗读音色（海星·女声 / 暗潮·男声）、位置信息（省份+城市，作为 user metadata 发给模型） | `SettingsDialog.tsx` |
| 数据管理 | API Key（含验证态）、导入历史对话（ZIP，**导入=覆盖**，二次确认）、导出所有历史对话（ZIP，不含 Key）、删除所有对话（二次确认） | 同上 |
- 存储分层：**API Key 与设置进 localStorage，会话与附件原件进 IndexedDB**；导入导出是 ZIP；官方明说「demo 仅供演示，数据只在你的浏览器里」。[仓库] README；[官方] 指南
- **没有** provider 管理、**没有**模型下拉、**没有**温度/参数、**没有**会话内搜索。模型固定在构建配置里（默认 `deepseek-flash`）。[仓库] README + `rspack.config.ts` 描述；`[推断]` 这一点对 myco 是**空白区**，必须自己设计
- 设置弹窗里专门处理「切换分组不丢正在输入的 API Key」，移动端多一个 `ApiKeyConfirm` 确认块。[仓库]

## 5. 对 myco MVP（Compose Android）的照抄/改造建议

> 前提对齐：myco 的 MVP 是**多会话 chat**（会话列表 + 聊天框 + 设置页），Android 壳是 Compose，GUI 只经 Bridge 说话（A5）。见 [决策记录](../design/20-decision-record.md)、[I0 方案](../design/30-i0-plan.md)。

### 5.1 屏与导航（3 屏 + 3 个全局浮层）
1. **ChatList**：`会话列表`屏。手机可做成 drawer（贴近 DeepSeek）或独立首屏；分组 + 新建 + 长按菜单。
2. **Chat**：`会话`屏。`TopAppBar`（返回/标题/新建-更多）+ `LazyColumn` + 吸底输入区。
3. **Settings**：`设置`屏。两个分组起步：**Provider/模型**（myco 必须有，demo 没有）+ **通用与数据**（主题、语言、上下文长度提示、会话数据管理）。
4. 全局：`Snackbar`/通知、确认弹窗、错误横幅（保存失败这类**留在页面内**，不要弹）。

### 5.2 组件映射（Web → Compose）
| demo | Compose |
|---|---|
| `MobileSider` + 遮罩 | `ModalNavigationDrawer` + `ModalDrawerSheet` |
| `Sider` 分组列表 | `LazyColumn` + `stickyHeader`，分组逻辑移植 `groupSessions` 五档 |
| `SessionItem` 的 `⋮` / 重命名 | `DropdownMenu` / 行内 `BasicTextField`（自动聚焦 + 全选） |
| `Modal` / `Menu` / `Toast` | `AlertDialog` / `DropdownMenu` / `SnackbarHost`（注意 DMR：**通知优先于 toast**） |
| 自适应 textarea + mirror | `BasicTextField` + `onTextLayout` 算高度，封顶 ≈202px |
| Markdown 渲染 | 自写 `AnnotatedString` 渲染器或成熟库；**代码块单独 composable**（`SelectionContainer` + 水平滚动 + 复制按钮） |
| 流式节流 | `snapshotFlow` 合帧 / 短间隔 `MutableState` 增量追加，禁止每个 token 全量重解析 |
| localStorage + IndexedDB | DataStore（设置/Key）+ Room（会话/消息/附件元数据） |
| zustand store | **Bridge 的 ViewModel 层**；GUI 不直接读存储（A5 硬约束） |

### 5.3 三个状态机（直接照 demo 的字段命名，减少返工）
```kotlin
// 1) 会话列表：与消息分离加载
sealed interface SessionListState { Loading; data class Loaded(val groups: List<SessionGroup>); data class Error(val retryable: Boolean) }
// 2) 单条回复：demo 的五态，停止是可存储状态而不是"取消异常"
enum class ReplyStatus { Pending, Streaming, Done, Stopped, Error }
// 3) 输入：能否发送由"流式中/上传中/语音中/Key 未配置"共同决定
data class InputState(val text: String, val attachments: List<Attachment>, val sending: Boolean) {
    val canSend get() = text.isNotBlank() && !sending && attachments.all { it.uploaded }
}
```
- 会话页的 `streaming` 要能**按会话 id 索引**（demo 是 `streaming: Record<sessionId, boolean>`），否则切换会话会看到别人的停止按钮。[仓库] `ChatInput.tsx`

### 5.4 照抄 / 改造 / 砍掉
- **照抄**：五档时间分组、吸底输入 + 免责声明、最后一条消息动作常显、复制反馈、删除二次确认、保存失败横幅+重试、错误分类文案（Key/余额/限流/繁忙）、分支切换的锚点修正。
- **改造**：`⌘/Ctrl` 类桌面交互改成触摸（长按出菜单、返回键收抽屉）；hover 才显示的动作在手机上**必须常显或长按可见**；`window.visualViewport` 那套换成 `WindowInsets.ime` + `adjustResize`。
- **砍掉（MVP 不做）**：Mermaid/KaTeX（体积与渲染成本高，先纯 Markdown+代码块）、朗读 TTS、语音输入、附件上传（I2 后期再加）、历史导入导出（先本地导出）。

## 6. 影响第一印象的 5 个细节

1. **空态**：demo 的空态只有一句问候语「今天有什么可以帮到你？」+ 输入框，没有模板卡片、没有功能列表。[仓库] `Welcome.tsx`
   myco 照抄即可；**但"未配置 provider"必须是第一屏的分支**（demo 用 `ensureApiKey()` 强制弹设置），否则用户首启即撞墙。
2. **首 token 等待**：demo 把"等"做成三段可感知：`pending` 时三个跳动点 → 思考面板标题「正在思考」带 shimmer → 每秒刷新「已思考（用时 N 秒）」。[仓库]
   myco 至少要有"已发送/正在连接/正在思考"三级反馈 + 计时，**不能只有一个转圈**；思考默认开时要显示「用时 N 秒」。
3. **错误重试**：错误不吞、可操作——Key 无效带「更换 API Key」、余额不足带「前往充值」、网络错误一句人话；保存失败给页面内重试横幅。[仓库] `describe.ts`、`App.tsx`
   myco 的硬约束是"失败必须吵"（简报 §0.6），这里正好有现成文案可抄。
4. **长列表滚动**：贴底阈值 40px、**只在用户上滑时取消跟随**、内容增高/键盘弹出时重新贴底、切分支用锚点修正、LazyColumn 不要每次 streaming 都 `scrollToItem` 到底（会打架）。
   最容易被做成 bug 的是"用户上翻看历史时新 token 把他拽回底部"——demo 明确避开了。[仓库] `MessageList.tsx`
5. **键盘遮挡**：demo 的 `viewport.ts` 是整套方案：视觉视口挂钩 → 写 `--app-viewport-height/top` → 只对输入框聚焦生效、失焦立即恢复、双指缩放不干预、先同步后再下一帧校准 Safari。[仓库]
   Compose 对应：`Scaffold` + `Modifier.imePadding()`（或 `WindowCompat.setDecorFitsSystemWindows(false)` + `WindowInsets.ime`），输入区在 IME 之上而非被顶出屏幕；**不要 autofocus**，点输入框才升键盘；键盘升起时消息列表要能继续贴底。

## 7. 未找到/存疑

- **没有官方 Android 原生 demo**：demo 是静态 Web，移动端是响应式布局，不是 Compose/RN/原生。[仓库] `viewport.ts` 断点 768px；官方文档也未提移动端仓库。[官方]
- **没有模型切换与 provider 管理**：demo 只有 API Key 一栏，模型名在构建配置里。所以"官方 chat app 的模型选择长什么样"**本报告无法给出**，需另找官方 App 截图（`[推断]`、未找到可引用的官方设计文档）。
- **没有会话内全文搜索**：`src/components/search/` 是联网搜索结果面板，不是本地会话检索。[仓库] 目录用途见 `ReplyArea.tsx`
- **GitHub 直连不可达**（本机网络，2026-11-25）：仓库结论来自 jsDelivr 对 `@main` 的镜像；`@main` 是移动分支，文件会随上游变化，复核时请固定 commit。[推断]
- **README 自相矛盾**：Releases 链接指向 `deepseek-ai/deepseek-chat-demo`，而仓库名是 `chat-demo`，两者是否为同一仓库存疑。[仓库] README
- **LICENSE 全文未读**：README、`package.json`、AtomGit 均标 MIT，三者一致，判定 MIT 可信；但未逐字核对 `LICENSE` 文件，也未核第三方依赖许可。[仓库]/`[推断]`
- **未实际打开 demo 页面看渲染效果**（只读 `index.html` 标题与源码）；像素级样式（间距/圆角/字号）本报告不覆盖，需要真机/浏览器截图时另开一轮。
- 官方文档里只有**一张**响应块标注截图（thinking/search/read/answer 的位置），未见完整设计规范或组件库。[官方] `build_a_chatbot` 的 `chatbot_response_blocks_en.webp`

---
来源汇总（均 2026-11-25 抓取）：[公告](https://api-docs.deepseek.com/news/news261113/) · [指南](https://api-docs.deepseek.com/guides/build_a_chatbot/) · [仓库](https://github.com/deepseek-ai/chat-demo) · [README 镜像](https://cdn.jsdelivr.net/gh/deepseek-ai/chat-demo@main/README.md) · [package.json](https://cdn.jsdelivr.net/gh/deepseek-ai/chat-demo@main/package.json) · [AtomGit 镜像](https://atomgit.com/gh_mirrors/chatdem/chat-demo?tab=license)

# chat-demo 聊天面板事实库（P1 · 只读审计）

> 审计对象：`E:\myco-chat\_ws\refs\chat-demo`（DeepSeek 官方 chat-demo，MIT，TypeScript/React 18 + zustand + rspack）。
> 规模：仓库 **170** 文件 / `src/` **159** 文件 ≈ **19,755** 行（`*.tsx` 46 / `*.ts` 76 / `*.css` 36）。
> 对账分母：`_ws/out/findings-chat-demo.csv`（**226** 条）
> 事实库：`docs/spec/ref/chat-demo.facts.csv`（226 行；E0 **123**、显式排除 **103**、**uncovered 0 / stale 0**）
> 对账报告：`_ws/out/coverage-chat-demo.md`
>
> **路径缩写**（§0/§1/§3/§5 内使用；`facts.csv` 内一律写以 `src/` 为根的完整相对路径）：
> `CI/` = `src/components/chatInput/` · `M/` = `src/components/messages/` · `MD/` = `src/components/markdown/`
> `S/` = `src/components/sider/` · `SS/` = `src/components/session/` · `SE/` = `src/components/settings/`
> `B/` = `src/components/base/` · `SR/` = `src/components/search/` · `F/` = `src/components/files/`
> `APP/` = `src/app/` · `svc/` = `src/services/`
>
> 依据级别：**E0** 源码行/真机 · **E1** 文档/注释声称 · **E2** 推测（只允许出现在 §5）。
> 行号约定：同一单元格内先给带路径的文件名，其后单独的反引号行号（如 `` `:24-26` ``）一律指该文件；跨文件引用始终写全 `路径:行`。
>
> **验收 ID 说明**：本文件是"参考实现事实库"，不占用 myco 的 `CHAT-xx` 号段。
> 各节点字段 13 的 `DEMO-nn` 仅用于 P2 对照与参考实现回归，**不进入** `docs/spec/acceptance.md`。

---

## 0. 一页结论

> 本路的核心问题：**"对外产品级单用户问答页"的最小完备集长什么样**。以下 10 条均可直接进 P2 对照表。

1. **回复生命周期是一个 5 值状态枚举 + 三处渲染派生，不是显式状态机。**
   `ReplyStatus = pending | streaming | done | stopped | error`（`src/services/chat/types.ts:70-74`），
   `createReply()` 以 `pending` 建立（`store.ts:264-277`），首个事件把它推进到 `streaming`
   （`reply.ts:78-79`），收尾时由 `settleReply` 落到 `done / stopped / error`（`reply.ts:143-160`）。
   用户看到的"状态"由三处独立派生：**轮内等待三点**（`M/AssistantMessage.tsx:57,79-85`）、
   **过程区标题**（`M/ReplyArea.tsx:69-88` → `svc/chat/view.ts:377-408`）、
   **错误/模板 notice**（`M/AssistantMessage.tsx:49,86-126` → `svc/chat/describe.ts:19-59`）。
   → 对 myco：不要造"等待/思考/输出/完成"四态枚举，直接把 status 映射到这三处。

2. **"还没开始返回"的空窗不是骨架屏，是 34px 高的三点脉冲，且它是互斥渲染而非占位。**
   `waiting = streaming && steps.length===0 && !answer && !message.thinking`
   （`M/AssistantMessage.tsx:57`）；三点 6×6、gap 6、`0.9s linear infinite`、错峰 0.1/0.2s
   （`M/AssistantMessage.module.css:14-47`）；一旦有 thinking/步骤/正文，整块**被卸载**（`M/AssistantMessage.tsx:79-85`）。
   → 这是"发送后 0.3~3 秒"这个尴尬窗口的全部视觉语言；myco 直接沿用成本极低。

3. **过程区（思考/搜索/浏览/查找/阅读链接）是一条默认展开、可一键收起的时间线，形态 A（不封顶、无内滚、头部不 sticky）。**
   标题 34px、整行可点（`M/ReplyArea.tsx:81`；`M/ReplyArea.module.css:5-33`）；展开体随内容长高，
   **整份 CSS 里没有 `position:sticky` / `contain` / `max-height`**（E0：`M/ReplyArea.module.css:1-316` 通读）。
   → **与 G-06 冲突**：长过程区展开后头部会滚出视口，想收起只能滚回顶部。
   chat-demo 的做法只在"过程很短"时成立，myco **不应**照抄这一条（见 Q-DEMO-02）。

4. **滚动跟随的判据是"距底 <40px"，且只有用户主动上翻（scrollTop 变小）才停跟随。**
   `STICKY_THRESHOLD = 40`（`M/MessageList.tsx:33`）；`nearBottom → sticky=true`，否则仅当
   `scrollTop` 变小才 `sticky=false`（`:128-136`）；内容/容器尺寸变化用 `ResizeObserver`
   贴底（`:63-69,72-82`）；**没有平滑动画**（直接赋 `scrollTop`）。
   → 比 DSH 的 25px + 500ms 采样简单得多，对陪伴场景够用；但 **没有"回到底部"按钮**
   （全仓 grep `toBottom|scrollToBottom|bottomButton` 0 命中）——长会话上翻后想恢复只能手动滚。

5. **输入区 = mirror 撑高的 textarea（零 JS 测量）+ 336px 上限 + Enter 发送 / Shift+Enter 换行。**
   可见 textarea 绝对定位、隐藏 mirror 决定高度（`CI/ChatInput.tsx:198-200`；
   `CI/ChatInput.module.css:171-220`，mirror `max-height:336px`）；到上限后 textarea 内部滚动。
   发送与停止是**同一位置的互斥按钮**（`CI/ChatInput.tsx:263-285`），34×34；
   不可用时用 class + `aria-disabled` 而非原生 disabled（`:276-284`），由 `send()` 内部三重守卫兜底
   （`:135-146`）。**无字符数上限**（全仓 `maxLength` 0 命中）。

6. **消息操作条默认 `opacity:0`、hover/focus-within 才显形，最后一条常显；但触摸设备没有兜底。**
   `M/AssistantMessage.module.css:4-7,95-105`（`.actions{opacity:0}` + `.alwaysShow`）、
   `M/AssistantMessage.tsx:144`（`isLast` → 常显）；**没有 `@media (hover:none)` 分支**
   （对照 `S/SessionItem.module.css:62-66` 会话行有）。
   → 移动端历史消息的复制/重新生成**不可达**，这是本仓库的真实缺陷（Q-DEMO-01）。

7. **复制是真的写剪贴板，且有两套实现、两个时长。**
   `svc/utils` 侧：`navigator.clipboard.writeText` → 失败退回 `textarea + execCommand('copy')`
   （`src/utils/clipboard.ts:1-28`，注释说明是为"手机通过局域网 HTTP 访问"兜底）；
   消息操作条：成功图标换勾 **1000ms**、期间吞重复点击（`M/CopyButton.tsx:20-31`）；
   代码块：成功图标换勾 + 文字「已复制」**1500ms**（`MD/CodeBlock.tsx:56-67`）。
   → myco 应统一成一套（Q-DEMO-06）。

8. **会话管理 = 单列表 + 时间分组 + 内联重命名 + 删除二次确认；没有搜索。**
   分组：今天 / 昨天 / 7 天内 / 30 天内 / 按月（`svc/chat/sessionGroups.ts:9-33`；文案 `src/i18n/zh.ts:22-25`），
   列表按 `updatedAt` 降序（`store.ts:99-100`）；重命名走 ⋯ → 内联 input（`S/SessionItem.tsx:25-48`）；
   删除走确认 Modal（`S/Sider.tsx:102-138`）；**sider/session 目录 grep `search` 0 命中**。
   新会话入口 3 处：侧栏大按钮（`S/Sider.tsx:64-76`）、收起态圆钮（`S/CollapsedSider.tsx:21-27`）、
   移动顶栏（`SS/MobileHeader.tsx:23-29`），且"新会话"**不落库**（只把 `activeId` 置 null，`store.ts:450-455`）。

9. **异常面是本仓库最成熟的部分：按 HTTP 状态分类 + inline 动作 + 可重试分级。**
   401→更换 API Key、402→前往充值、429→稍后再试、5xx→服务器繁忙、无状态码→网络异常
   （`svc/api/errors.ts:17-34`；`svc/chat/describe.ts:19-46`；动作 `M/AssistantMessage.tsx:99-123`）；
   上传失败区分 `retryable`（网络/限流/5xx 可重试，格式/过大不可重试，`svc/attachments.ts:189-206`），
   配额上限另走 toast + 文档链接（`:93-103`）；断流但已收到 `stop_reason` 按 done 收尾
   （`store.ts:1088-1090`）。**没有加载骨架屏**（grep `skeleton` 仅 2 处 CSS 变量），加载态 = 文字 + 重试
   （`APP/App.tsx:96-121`）。

10. **移动端是真适配，不是"能跑"：768px 断点 + visualViewport 键盘跟随 + 独立滚动层 + 44px 触控目标。**
    断点 `@media not all and (min-width:768px)`（`APP/viewport.ts:3-4`）；键盘：`visualViewport`
    读写 `--app-viewport-height/top`，输入框聚焦时撑满可见区、失焦复位（`:45-108`）；
    整页滚动锁（`:13-43`）；移动端滚动层改为内层 `.scroll` 并 `overscroll-behavior-y:contain`
    （`M/MessageList.module.css:15-30`）；触控尺寸 token `--ds-control-height-xl: 44px`（`APP/theme.css:9-13`）。
    → 但**过程区标题、引用角标、会话行仍是 div/span + onClick**（无 role/tabindex），
    键盘与读屏不可达；触摸端消息操作条不可达（见 #6）——无障碍是"按需补"的水平。

---

## 1. 项目定位与规模

**它是什么**（E0/E1）
- DeepSeek 官方开放的**静态聊天 demo**：无应用后端，浏览器直连配置的 API 服务（`README.md:5-7`，E1）；
  可部署静态站或直接双击 `index.html`（`README.md:7,28`，E1）。MIT（`LICENSE:1-3`），版本 `0.1.4`（`package.json:3`）。
- 技术栈：React 18.3 + zustand 4.4 + rspack 2 + TypeScript（`package.json:15-46`）；CSS Modules（`rspack.config.ts:52-58`）；
  IndexedDB 持久化（`svc/chat/db.ts:1-32`）+ localStorage 存设置（`svc/settings.ts:22-31`）。
- 目标形态：**单用户、单模型（`deepseek-flash` 写死，`store.ts:42-43`）、多会话问答**，带深度思考、智能搜索、
  链接阅读、引用角标、附件、语音、朗读、Markdown/KaTeX/Mermaid、中英双语、明暗主题、移动端布局、
  历史导入导出（`README.md:30-36`，E1；对应实现文件齐全）。

**它不做什么**（未见实现 / 明显超出 demo 范围）
- 无后端、无账号、无多用户/同步（`README.md:38-44`，E1：数据全在浏览器本地）。
- 无模型选择、无参数调节、无 token/费用/用量的 UI（`MessageUsage` 只存不展示，`svc/api/types.ts` + 全仓无用量组件）。
- 无会话搜索、无置顶/归档、无单会话导出/分享、无消息删除、无引用回复（sider/session/message 目录 grep 均 0 命中）。
- 无"回到底部"按钮、无轮次导航/大纲、无未读标记（grep `toBottom|rail|outline` 0 命中）。
- 无插件/工具系统、无 MCP、无 agent 过程流——只有服务端工具（搜索/阅读）的展示产物（`svc/chat/view.ts:31-80`）。
- 无加载骨架屏；无快捷键设置；无长按菜单；无限流/并发控制（除附件 3 并发队列，`svc/attachments.ts:21-22,105-145`）。

**代码成熟度**（判断依据全部 E0）
- CI 只跑 `pnpm type` + `pnpm lint` + `pnpm build`（`.github/workflows/pages.yml:47-56`），**无测试步骤**；
  全仓 **0 个测试文件**（`src/` 159 文件无 `*.test.*` / `*.spec.*`）。
- 但工程细节明显经过真实使用打磨：DB schema 版本 11、导入用 staging 表 + 原子替换（`db.ts:4-32,26-32`）；
  流式按 `requestAnimationFrame` 合帧、页面隐藏时立即 flush（`store.ts:994-1000,1053-1057`）；
  断流缺 `message_stop` 的容错（`:1088-1090`）；Safari/file:// 特判（`APP/viewport.ts:76-85`、
  `rspack.config.ts:35-36,77-82`）；CSS 里大量 `prefers-reduced-motion` 局部处理（3 处）。
- 注释密度高，且多为**解释"为什么"**（如 `svc/attachments.ts:64-67` 说明为何不直接展示服务端英文原文；
  `svc/api/client.ts:93` 说明 GET /models 不产生费用）。
- 结论：**"产品级 demo"，可作行为参照，不是可扩展框架**——没有主题/槽位/插件抽象，
  `components/base/*` 只是 7 个自用原语。

---

## 2. 节点事实表（31 个节点 · 每个 13 字段）

> 字段 12 逐条给 `E0/E1` + 行号；字段 13 必含反证 + 验收 ID（`DEMO-nn`，仅参考实现回归用）。
> 节点 ID 与 `chat-demo.facts.csv` 的 `node_id` 一一对应（行数见各节末尾括号）。
> 归位：`chat.*` → 聊天面板；`sider.*` → 侧栏/会话管理；`settings.*` → 设置面板；`base.*` → 通用原语（不单独成面板）。

### 2.1 `chat.input.textarea` — 多行自适应输入框

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.input.textarea`。所属 chat → 输入区（会话页吸底 / 欢迎页居中）。出现条件：会话页与欢迎页各 1 个；`sessionId=null` 即"新对话"草稿（`CI/ChatInput.tsx:38-41` 用 key 强制重建，草稿按 sessionId 存在 zustand Map：`svc/chat/drafts.ts:25-31`）。父：输入卡 wrapper（`.wrapper`）。数据：受控 `draft.text`。 |
| 2 | 网格与定位 | 输入卡宽 `calc(--message-list-max-width − 64px)`、`max-width: calc(100% − 32px)`（`SS/SessionView.module.css:32-50`）；圆角 24、边框 1px（`CI/ChatInput.module.css:1-15`）。textarea 与 mirror 同盒模型：`padding: 12px 12px 0 16px`、行高 24、字号 16（`:171-186`）；可视高由 mirror 内容高决定，**上限 336px**（`:216-220`）；最小高 = 2 行 + 12 = 60px（`:176`），`<440px` 时 1.25 行 + 12（`:209-214`）。功能行 `margin-top:4; padding:12; gap:8`（`:238-244`）。 |
| 3 | 命中区 | 整个输入卡空白处可点 → 聚焦 textarea（wrapper `onClick`，`CI/ChatInput.tsx:158-163`）；textarea 覆盖文本区全部（absolute `height:100%`，css `:188-202`）。功能行按钮各有独立命中区（见 2.2–2.5）。 |
| 4 | 手势 | 单击聚焦；移动端首次点击用 `onMouseDown` + `preventDefault` + `focus({preventScroll:true})`，避免 Safari 把整页滚到输入框（`:176-182`）；粘贴文件走 `onPaste`（`:189-195`）；无长按/双击/滑动。 |
| 5 | 行为 | Enter（无 Shift 且非 `isComposing`）→ `send()`（`:183-188`）；Shift+Enter 由浏览器默认插入换行。`send()` 三重守卫：导入中 / 录音非 idle / `prepareDraftMessage` 为 null → 直接 return（`:135-146`）。发送成功后桌面 focus、移动端 blur（`:148-152`）。文本变化写内存草稿（`setDraftText`，`drafts.ts:45-46`），**不落 localStorage/IndexedDB**（`drafts.ts:25-28`）。 |
| 6 | 视觉状态 | default 透明底（卡片底 `--dsw-specific-input-major`）、placeholder 用 caption 色（`:198-201`）；focus **无额外环**（`outline:none`，`:192`）；录音中移动端 `visibility:hidden` 但保留高度（`:205-207`；`ChatInput.tsx:168`）；`disabled=importingHistory` 时沿用浏览器 disabled 视觉。 |
| 7 | 布局占位与展开形态 | 不展开。输入区吸底：桌面 `position:sticky; bottom:0; z-index:10`（`SS/SessionView.module.css:32-50`，顶部 30% 透明渐变遮罩）；移动端 `position:relative` + `safe-area-inset-bottom`（`:62-69`）。文本增长 → mirror 撑高、功能行下移，**不产生内滚**（高度=内容，css `:171-186`）。移动端 wrapper 另受 `calc(100dvh − 120px)` 上限（`:222-236`）。 |
| 8 | 滚动语义 | textarea 自身 `overflow:auto`，仅当到达 336px 上限后出现内部滚动（css `:171-186,216-220`）；`overscroll-behavior:contain`（`:193`）阻止滚动继续传给会话；会话纵向滚动由消息容器承担（见 2.16）。 |
| 9 | 动效 | 无高度动画、无 transition（高度即时跟随内容）。 |
| 10 | 边界态 | ① 空输入 → 发送按钮 `aria-disabled`；② 超长：**无 maxLength**（全仓 0 命中），到 336px 后内滚；③ 中文输入法组字中按 Enter 不发送；④ 移动端不自动聚焦（避免弹键盘，`:171-172`）；⑤ 切换会话：组件重建但文字从草稿恢复（`:39-40`）；⑥ 生成中仍可继续输入（只禁发送）。 |
| 11 | 无障碍 | textarea 只有 placeholder，**无 `aria-label`**（E0：该元素无 aria 属性）；wrapper 的 onClick 不改变语义；无字符计数播报。 |
| 12 | 依据 | **E0** `CI/ChatInput.tsx:135-153,158-200,183-196`；**E0** `CI/ChatInput.module.css:1-15,166-236`；**E0** `svc/chat/drafts.ts:25-46,95-118`；**E0** `SS/SessionView.module.css:32-69`；**E0** `SS/Welcome.tsx:8-20`；**E1** `README.md:34`（多行输入/附件能力声称）。 |
| 13 | 反证 + 验收 | **反证**：若 textarea 随内容无限长高，超过一屏后输入区会把消息列表挤没，用户看不见自己正在回复的那段；若固定高度 + 永远内滚，多行编辑时每写三行就要手动拖滚动条。故"自适应到 336px 上限、之后内滚"是唯一同时保住两端的方案。若 Enter 改成 Ctrl+Enter 才发送，与主流聊天习惯相反，用户会频繁误换行。**验收**：`DEMO-01` 输入 1→20 行时输入卡高度线性增长到 336px 后停止、列表底部不被遮住（真机录屏）；`DEMO-02` 中文输入法组字中按 Enter 不发送（真机 IME）。 |

### 2.2 `chat.input.send-stop` — 发送 / 停止（同位置互斥）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.input.send-stop`。同一位置的两个互斥按钮：生成中 = 停止，其余 = 发送（`CI/ChatInput.tsx:263-285`）。每输入区 1 个；`streaming && sessionId` 时渲染停止，否则渲染发送。 |
| 2 | 网格与定位 | 34×34 正圆（`CI/ChatInput.module.css:308-322`）；右侧容器 `gap:4; margin-left:auto`（`:300-306`），按钮 `margin-left:6`（`:313-315`）→ 输入卡最右下角，与语音/附件同一行。 |
| 3 | 命中区 | 34×34 圆形（<44dp，移动端未扩边；对照 Q-03 同题）。 |
| 4 | 手势 | 单击。无长按/双击。 |
| 5 | 行为 | 停止：`stopReadAloud()` + `stopReply(sessionId)`（abort controller，`store.ts:549`）→ 流收尾为 `stopped`（`:1062-1064`）。发送：`send()`（见 2.1 字段 5）。**不可用时不禁用，而是 `aria-disabled` + 点击守卫**（`:276-284`；`canSend` 定义 `:133`）。停止幂等（已无流时 abort 无副作用）。 |
| 6 | 视觉状态 | default 品牌底白图标（css `:319-321`）；hover 底色加深（`:324-326`）；`disabled` 类 `opacity:.4` + `not-allowed`（`:328-331`）；生成中换成停止图标（无颜色区分）。无 pressed 态。 |
| 7 | 布局占位与展开形态 | 不展开；两态同尺寸挂载 → 切换不引起重排；不参与任何展开逻辑。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | `background 0.2s ease`（css `:322`）。 |
| 10 | 边界态 | ① 空草稿 → 灰（aria-disabled）；② 附件上传中 → `draftReady=false` → canSend=false（`:48,133`）；③ 录音/转写中 → canSend=false（`:133`）；④ 历史导入中 → disabled（`:133,173`）；⑤ 停止后同一帧切回发送（streaming 清空，`store.ts:1112-1116`）。 |
| 11 | 无障碍 | 原生 button + `aria-label`（发送 / 停止生成）；`aria-disabled` 保留可聚焦性；**无 aria-live 播报"已开始/已停止生成"**。 |
| 12 | 依据 | **E0** `CI/ChatInput.tsx:133,135-153,263-285`；**E0** `CI/ChatInput.module.css:300-331`；**E0** `svc/chat/store.ts:549,992,1112-1116`；**E1** `src/i18n/zh.ts:29,139`（发送/停止生成文案）。 |
| 13 | 反证 + 验收 | **反证**：若发送/停止做成两个并排按钮，生成中用户会误点发送（排队出第二条），空闲时会点到停止（无反应）；若用原生 `disabled`，浏览器不给 disabled 元素 hover/tooltip，用户不知道为什么发不出去。**验收**：`DEMO-03` 生成中同位置变停止、点击后 100ms 内切回发送（真机）；`DEMO-04` 附件上传中发送按钮 aria-disabled 且点击无效果（夹具）。 |

### 2.3 `chat.input.toggles` — 深度思考 / 智能搜索开关

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.input.toggles`。功能行左侧两个 toggle（`CI/ChatInput.tsx:204-229`）。始终出现（与 API Key 无关）；每输入区 1 组 2 个。 |
| 2 | 网格与定位 | 按钮高 34、`padding:0 12`、圆角 18、gap 4、字号 13/20（`CI/ChatInput.module.css:258-274`）；组 `flex:1; gap:10; min-width:0`（`:246-252`）；窄屏时隐藏文字只留图标（`.collapsedToggles .toggleLabel{display:none}`，`:254-256`，由 JS 判定 `scrollWidth>clientWidth`，`ChatInput.tsx:60-75`）。 |
| 3 | 命中区 | 34 高 × 文字宽（含 padding）；窄屏收起后约 34×34（<44dp）。 |
| 4 | 手势 | 单击切换；hover 100ms 出 Tooltip（上方）。无长按/双击。 |
| 5 | 行为 | `setThinking / setSearch` → 写 localStorage + 更新 zustand（`svc/settings.ts:127-135`）。开关值在**创建回复时快照**（`store.ts:264-277`），改开关不影响已在生成的回复，也不改历史请求。幂等、可撤销。 |
| 6 | 视觉状态 | default 透明 + l2 边；hover 灰底（css `:280-282`）；selected 品牌浅底 + 品牌字 + 品牌边（`:289-297`）。无 disabled 分支（E0：组件未传 disabled）。 |
| 7 | 布局占位与展开形态 | 不展开；selected 只换色不改尺寸 → 切换零重排；窄屏隐藏 label 会变宽一次（一次性 class 切换）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | `border/background 0.2s`（css `:277-278`）。 |
| 10 | 边界态 | ① 窄屏自动收起文字（依赖 ResizeObserver，`:60-75`）；② 无 API Key 时仍可切换（onClick 只 setThinking/setSearch）；③ 刷新后保持（localStorage）；④ 中英文 label 宽度不同 → 会触发收起判定（`:75` 依赖 `t`）。 |
| 11 | 无障碍 | button + `aria-label` + `aria-pressed`（`:208-210,219-222`）；Tooltip 文案与 aria-label 相同；两个独立 toggle（无 radiogroup 语义）。 |
| 12 | 依据 | **E0** `CI/ChatInput.tsx:52-58,60-75,204-229`；**E0** `CI/ChatInput.module.css:246-298`；**E0** `svc/settings.ts:127-135`；**E0** `svc/chat/store.ts:264-277`；**E0** `src/i18n/zh.ts:134-137`。 |
| 13 | 反证 + 验收 | **反证**：若开关不持久化，用户每次开会话都要重开深度思考，且不知道当前状态；若改开关会回改历史请求参数，同一会话前后轮语义不一致、无法复现回答。**验收**：`DEMO-05` 切换后刷新仍保持（真机）；`DEMO-06` 窄屏隐藏文字后按钮仍可点、`aria-pressed` 正确（截图 + a11y 树）。 |

### 2.4 `chat.input.attach` — 附件（上传 / 拖拽 / 粘贴 / 失败重试）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.input.attach`。整组：上传按钮 + 隐藏 file input + 草稿附件卡片（图片/文档）+ 拖拽遮罩 + 粘贴 + 配额 toast 链接。每输入区 1 组；附件卡片仅在有草稿附件时渲染（`CI/DraftAttachments.tsx:24-26`）。 |
| 2 | 网格与定位 | 附件条在 textarea 上方：`padding:12 12 0; gap:10; overflow-x:auto`（`CI/ChatInput.module.css:34-41`）；图片 64×64 圆角 16（`:43-64`）；文档卡 240×≥64（`F/FileCard.module.css:1-24`）；删除钮 18×18 右上（`ChatInput.module.css:138-157`）；拖拽遮罩全屏 fixed、z1000（`CI/DragMask.module.css:1-17`）。 |
| 3 | 命中区 | 上传按钮 34×34（IconButton m）；删除钮 18×18（hover 才显形）；失败图片整卡可点重试（`DraftAttachments.tsx:70-77`）；失败文档卡整卡可点（`FileCard.tsx:45,62`）。 |
| 4 | 手势 | 单击上传/删除/重试；**页面任意位置拖入文件**（document 级 dragenter/leave/over/drop，进出计数，`CI/DragMask.tsx:16-55`）；**粘贴**（`onPaste` 取 `clipboardData.files`，`ChatInput.tsx:189-195`）；无长按。 |
| 5 | 行为 | 选择即上传（最多 3 并发，`svc/attachments.ts:21-22,105-145`）；校验顺序 格式→大小→数量，任一不满足整批不添加（`:50-62`）；全部上传成功前不能发送（`drafts.ts:96-118`）；删除会 `cancelUpload`（`drafts.ts:120-130`）；失败按 `retryable` 区分（`attachments.ts:189-206`）；配额上限失败走 notice→Toast +「删除文件」文档链接（`:93-103` + `APP/App.tsx:60`）。 |
| 6 | 视觉状态 | 上传中 spinner 蒙层（css `:66-84`）；失败红描边 + 深色蒙层 + 重试图标（`:92-136`）；不可重试时删除钮常显红色（`:159-164`）；图片失败原因在底部 60px 渐变白字（`M/MessageFile.module.css:72-92`）；拖拽遮罩白 70% + blur 10（`DragMask.module.css:10-17`）。 |
| 7 | 布局占位与展开形态 | 附件条条件渲染 → **出现/消失会让输入卡高度突变**（无过渡）；不展开；图片与文档同条横向排列。 |
| 8 | 滚动语义 | 附件条横向滚动（css `:34-41`）；纵向不滚。 |
| 9 | 动效 | 删除钮 `opacity .2s`；spinner `0.8s linear infinite`；无 reduced-motion 处理（E0：本组件 CSS 无该 media query）。 |
| 10 | 边界态 | ① 扩展名白名单（`svc/uploadExts.ts`，986 行清单）；② 单文件 64MB / user_data 类 32MB（`attachments.ts:16-19`）；③ 最多 50 个（`:20`）；④ 不可重试失败只能删除后再发（`DraftAttachments.tsx:56-60`）；⑤ 上传不阻塞输入；⑥ 导入历史期间不收新文件（`ChatInput.tsx:78,88,96`）。 |
| 11 | 无障碍 | 上传/删除按钮 aria-label；失败重试 button 的 aria-label 含原因；图片 `title`/`alt`=文件名；**拖拽无键盘等价（未见实现）**。 |
| 12 | 依据 | **E0** `CI/ChatInput.tsx:77-102,189-195,244-262`；**E0** `CI/DraftAttachments.tsx:42-103`；**E0** `F/FileCard.tsx:45-83`；**E0** `CI/DragMask.tsx:16-75`；**E0** `svc/attachments.ts:12-22,50-62,93-103,105-210`；**E0** `CI/ChatInput.module.css:34-164`；**E0** `F/FileCard.module.css:1-128`；**E1** `README.md:34`。 |
| 13 | 反证 + 验收 | **反证**：若"选完不上传、发送时统一传"，附件大时点发送会长时间无反馈甚至超时，且用户无法在输入期间发现格式/大小错误；若失败只显示"失败"不给原因，用户不知道换文件还是重试。若不显示全屏拖拽遮罩，用户不知道"整页都能放文件"。**验收**：`DEMO-07` 拖入非白名单文件整批被拒并 toast（真机）；`DEMO-08` 上传失败区分可重试/不可重试两种视觉（截图）。 |

### 2.5 `chat.input.voice` — 语音输入（录音 / 转写 / 移动端频谱）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.input.voice`。语音按钮 + 录音读秒 + （移动端）频谱。每输入区 1 个；`available = isSecureContext`（`CI/useVoiceInput.ts:14`）。 |
| 2 | 网格与定位 | 34×34 圆钮，位于右侧附件按钮左侧（`ChatInput.tsx:230-243`；`CI/VoiceButton.module.css:19-48`）；读秒 12px 在按钮左、`<768px` 隐藏（`:1-17`）；频谱占 textarea 第一行区域（`ChatInput.tsx:197`）。 |
| 3 | 命中区 | 34×34（<44dp）。 |
| 4 | 手势 | 单击开始 / 结束 / 取消（idle→recording→transcribing）。**无长按录音**（E0：无 pointer/touch 处理，只在 onClick 调 `voice.toggle`）。 |
| 5 | 行为 | idle→recording：`getUserMedia` + 16kHz AudioContext + WAV 采集（`svc/speech/recorder.ts:23-98`）；录音中 500ms 刷新秒数（`useVoiceInput.ts:133-144`）；满 120s 自动结束 + toast（`recorder.ts:8-9,72-75`；`useVoiceInput.ts:114-119`）；再点 → stop → transcribing → 转写文本**插入光标处**并把光标移到文本后（`ChatInput.tsx:104-115`）；转写中再点 = 取消（`useVoiceInput.ts:107-110`）；失败按 DOMException 分类 toast（`:16-26`）；非 HTTPS 置灰 + tooltip（`:14`；`VoiceButton.tsx:30-32,51`）。 |
| 6 | 视觉状态 | idle 麦克风 outline；recording 双层圆（halo .2 + core .75）+ halo 1.8s 呼吸 + 图标切换（`VoiceButton.module.css:55-157`）；transcribing loading 圈（`:159-167`）；disabled 灰 + not-allowed（`:50-53`）；`prefers-reduced-motion` 关闭动画（`:143-157`）。 |
| 7 | 布局占位与展开形态 | 不展开；录音中移动端**频谱替换** textarea 文字（`visibility:hidden` 保高，`ChatInput.module.css:205-207`；桌面端不渲染频谱，`ChatInput.tsx:197`）；录音中发送不可用（canSend）→ 同位置无重排。 |
| 8 | 滚动语义 | 不滚；频谱柱数按容器宽度算、最多 60 根（`CI/Spectrum.tsx:28-40`）。 |
| 9 | 动效 | halo 入场 .15s、呼吸 1.8s、核心 .2s；reduced-motion 关闭（css `:143-157`）；频谱每帧改 `transform`、不触发 React 渲染（`Spectrum.tsx:93-102`）。 |
| 10 | 边界态 | ① 非 HTTPS/无麦克风/权限拒绝逐类 toast；② 单次 ≤120s；③ 空识别 → toast「未识别到语音内容」；④ 切会话/导入 → cancel（`ChatInput.tsx:117-127` + hook 卸载 cleanup `useVoiceInput.ts:146`）；⑤ 桌面端无频谱（条件渲染）；⑥ ASR 接口失败 toast。 |
| 11 | 无障碍 | button + aria-label 随状态变（语音输入 / 结束录音并转写 / 正在转写点击取消 / 需 HTTPS）+ `aria-pressed=recording` + `aria-disabled`；**无 aria-live 播报开始/结束**；频谱 `aria-hidden`（`Spectrum.tsx:110`）。 |
| 12 | 依据 | **E0** `CI/VoiceButton.tsx:14-78`；**E0** `CI/useVoiceInput.ts:8-152`；**E0** `svc/speech/recorder.ts:1-98`；**E0** `CI/Spectrum.tsx:24-116`；**E0** `CI/ChatInput.tsx:104-115,129-133,197,231-243`；**E0** `CI/VoiceButton.module.css:1-173`；**E0** `src/i18n/zh.ts:150-160`。 |
| 13 | 反证 + 验收 | **反证**：若做成"按住说话"，移动端滚动/点击会与长按冲突，松手位置可能已在按钮外 → 误取消；oneshot 点按更稳。若录音中不禁用发送，用户会同时发出半截文本并保留录音 = 两份意图。**验收**：`DEMO-09` 录音中发送不可点、结束后文本插入光标处（真机）；`DEMO-10` 120s 自动结束并 toast（夹具/真机）。 |

### 2.6 `chat.msg.user` — 用户消息（气泡 + 附件 + 操作条）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.user`。每个 `role=user` 消息 1 个（`M/MessageList.tsx:149-161`）；首条 `isFirst` 加 34 顶部间距；`isLast`（最后两条之一）操作条常显。 |
| 2 | 网格与定位 | 右对齐列（`M/UserMessage.module.css:1-14`）；气泡 `max-width: calc(100% − 88px)`（`<440px` 时 −68px）、`padding:10 16`、圆角 22、字号 16/24（`:30-47`）；附件条在气泡上方 `gap:10` 右对齐（`:16-28`）；底部操作行高 28、`gap:10`、`margin-top:6`（`:49-55`）。 |
| 3 | 命中区 | 气泡不可点；附件图片可点开外链或重试；操作按钮 28×28。 |
| 4 | 手势 | 单击附件/复制/编辑；无长按/双击/滑动。 |
| 5 | 行为 | 附件补传失败且可重试 → 点整张图 `retryUpload` → 重发该回答（`M/MessageFile.tsx:99-113`；`store.ts:736-759`）；复制 = 原文；编辑见 2.7。重试在 streaming 时被 store 守卫拦下（`store.ts:741`）。 |
| 6 | 视觉状态 | 气泡底 `--dsw-specific-bubble`（浅蓝/深灰）；操作条 `opacity:0`，消息 `:hover` 或 `:focus-within` → 1（`:11-13,57-67`），`isLast` 常显（`:69-71`）；附件上传中 spinner 蒙层、失败红边（`MessageFile.module.css`）。 |
| 7 | 布局占位与展开形态 | 气泡在纵向流中；操作条**固定 28px 高、只切 opacity** → hover 不引起重排；附件条仅在有附件时占位。 |
| 8 | 滚动语义 | 随会话滚动。 |
| 9 | 动效 | `opacity .2s`（css `:62`）。 |
| 10 | 边界态 | ① 空文本 + 有附件 → 不渲染气泡（`UserMessage.tsx:117`）；② 纯附件消息可编辑（canSend 允许 `files>0`，`:47`）；③ 导入的历史附件无本地原件 → 只显示文件信息（`svc/chat/types.ts:12-16`；`F/useFileView.ts` 返回 `url=null`）；④ 大字号：字号固定 16px（E0 无 font-delta 变量）。 |
| 11 | 无障碍 | 图片 `alt=文件名`、失败时 `aria-label=原因`；操作按钮 aria-label；**气泡是纯 div 无 role**；无"你说"的语义标注。 |
| 12 | 依据 | **E0** `M/UserMessage.tsx:64-137`；**E0** `M/UserMessage.module.css:1-104`；**E0** `M/MessageFile.tsx:28-135`；**E0** `M/MessageList.tsx:149-161`；**E0** `svc/chat/store.ts:736-759`；**E0** `F/useFileView.ts:19-49`。 |
| 13 | 反证 + 验收 | **反证**：若用户消息操作条常驻，每条气泡下多一行图标，消息密度骤降且视觉噪声大；若 hover 用 `display:none`，鼠标移入瞬间行高 0→28，整段对话上下跳。故"opacity 显隐 + 固定行高"是唯一不跳的方案。**验收**：`DEMO-11` hover 用户消息时其下方内容 y 不变（真机录屏）。 |

### 2.7 `chat.msg.edit` — 编辑用户消息并重发

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.edit`。内联编辑器（替换气泡 + 操作条）。出现条件：点用户消息操作条编辑按钮（`M/UserMessage.tsx:121-130`）；每条同时最多 1 个。 |
| 2 | 网格与定位 | 编辑器宽 100%、1px 品牌边、圆角 22（`:73-80`）；textarea 最小高 44、**最大 202**、`padding:10 16`、16/24（`:82-97`）；按钮行右对齐 `gap:8`、`margin:6 10 10`（`:99-104`）。 |
| 3 | 命中区 | textarea 整块；取消/发送按钮（Button s：高 32、min-width 58）。 |
| 4 | 手势 | 单击按钮；输入；**Esc 取消**（`UserMessage.tsx:98-99`）；无长按。 |
| 5 | 行为 | 进入时把高度设成 `min(scrollHeight, 202)`（`:49-55`）；发送 → `editMessage`：以原消息父节点新增**同父版本**（question + 新 reply），**保留原附件**（`store.ts:654-684`；`files: previous.files`），旧回答与后续对话仍可经版本切换找回（`branches.ts:76-101`）；成功后 `stopReadAloud` 并退出编辑（`UserMessage.tsx:57-62`）；busy 时 textarea disabled、按钮 disabled、不提交。 |
| 6 | 视觉状态 | 编辑器品牌描边；取消=outlined、发送=primary（`:107-112`）；无 hover 特效。 |
| 7 | 布局占位与展开形态 | 编辑器替换气泡与操作条（同位置），高度随内容到 202 上限；提交后回到普通消息行。 |
| 8 | 滚动语义 | 到 202px 后 textarea 内滚（css `:82-97`）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | ① 空文本且原消息无附件 → 发送禁用（canSend，`:47`）；② 生成中不可编辑（`disabled={busy}`，`:92,107,110`）；③ 移动端 Enter **不提交**（`:100` 的 `!isMobile`）→ 手机靠按钮；④ 中文输入法组字不提交（`:95-97`）；⑤ Esc 不保存。 |
| 11 | 无障碍 | textarea `aria-label=编辑消息`；按钮有可见文字；**无提交后焦点还原**（E0 无 focus 管理）。 |
| 12 | 依据 | **E0** `M/UserMessage.tsx:43-62,83-114`；**E0** `M/UserMessage.module.css:73-104`；**E0** `svc/chat/store.ts:654-684`；**E0** `svc/chat/branches.ts:39-117`。 |
| 13 | 反证 + 验收 | **反证**：若编辑直接覆盖原消息，用户无法对比旧版、也退不回（回答生成坏了只能重新提问）；若编辑不保留附件，用户改文字时附件被静默丢弃。**验收**：`DEMO-12` 编辑后原回答仍可经版本切换找回（真机）；`DEMO-13` 编辑保留原附件（夹具）。 |

### 2.8 `chat.msg.actions` — 助手回复操作条（复制 / 朗读 / 重新生成 / 继续生成）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.actions`。轮底操作条。出现条件：**非 streaming**（`M/AssistantMessage.tsx:141`）；每回答 1 条；「继续生成」独立按钮仅 `isLast && canContinueReply`（`:160-173`）；复制/朗读按内容存在性出现（`:145-148`）。 |
| 2 | 网格与定位 | `.bottom` `position:relative; left:-6; flex-wrap:wrap; gap:10; padding:12 0 10`（`M/AssistantMessage.module.css:84-93`）；`.actions` gap 10（`:95-101`）；图标按钮均 28×28（IconButton xs，`B/IconButton.module.css:35-38` + `APP/theme.css:13`）；继续生成 `margin-left:auto` 靠右（`AssistantMessage.module.css:107-109`）。 |
| 3 | 命中区 | 每图标 28×28（间距 10）；继续生成按钮高 32、宽 ≥58。 |
| 4 | 手势 | 单击；Tooltip hover（bottom 向）；无长按/双击。 |
| 5 | 行为 | 复制 → `copyText`（2.6 字段 12 / 2.8 依据）；朗读 → `toggleReadAloud`（loading/playing，再点停止）；重新生成 → `stopReadAloud()` + `regenerateReply`（**同问题下新增版本，不覆盖**，`store.ts:628-651`）；继续生成 → `stopReadAloud()` + `continueReply`（内容追加到原回复，`store.ts:698-733`）；生成中整条不渲染。 |
| 6 | 视觉状态 | default `opacity:0`；消息 `:hover` / `:focus-within` → 1（`AssistantMessage.module.css:4-7,95-105`）；`isLast` → `alwaysShow` 常显（`:103-105`）；朗读中图标换 4 根波形（`M/ReadAloudButton.tsx:18`；`M/ReadingBars.tsx`）；复制成功换勾（`M/CopyButton.tsx:17-18`）。 |
| 7 | 布局占位与展开形态 | 占位恒定（只切 opacity）；不展开；继续生成出现在同一行末尾。 |
| 8 | 滚动语义 | 随会话滚动。 |
| 9 | 动效 | `opacity .2s`；勾/波形瞬换；波形 SMIL 1.6s 循环（`ReadingBars.tsx:60-80`，reduced-motion 静态帧 `:24,52`）。 |
| 10 | 边界态 | ① 无正文 → 不渲染复制（`:145`）；② 无正文 → 不渲染朗读（`:146-148`）；③ refusal → 朗读不渲染、重生成禁用（`:153`）；④ streaming → 整条隐藏；⑤ **触摸设备：非最后一条无 hover 兜底**（CSS 无 `hover:none` 分支）→ 历史消息操作不可达（Q-DEMO-01）。 |
| 11 | 无障碍 | 每个 IconButton 有 aria-label（tooltip 兼任）；**无 `aria-pressed` 表示朗读中**；无 live 播报"已复制"。 |
| 12 | 依据 | **E0** `M/AssistantMessage.tsx:141-175`；**E0** `M/AssistantMessage.module.css:4-7,84-109`；**E0** `M/CopyButton.tsx:9-33`；**E0** `M/ReadAloudButton.tsx:13-26`；**E0** `svc/speech/readAloud.ts:8-144`；**E0** `svc/chat/store.ts:628-651,698-733`。 |
| 13 | 反证 + 验收 | **反证**：若操作条常驻，每条回答下方都是三枚图标，页面像调试面板；若 hover 用 `display:none`，鼠标进入时行高突变（同 2.6）；若生成中就显示重新生成，用户会在流式中途点它 → 旧回答被新版本顶掉（当前用"整条隐藏"规避）。**验收**：`DEMO-14` 生成中无操作条、结束后出现（真机）；`DEMO-15` 重新生成后旧回答仍可切换（真机）。 |

### 2.9 `chat.msg.branch` — 消息版本切换器（`‹ N/M ›`）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.branch`。出现条件：同一父节点下兄弟消息 >1（`M/BranchSwitcher.tsx:28-30`）；用户消息与助手回答各可有一个。 |
| 2 | 网格与定位 | 高 28、`gap:2`（`M/BranchSwitcher.module.css:1-6`）；左右各一个 IconButton xs（28）；中间计数 15/18、`tabular-nums`（`:12-20`）；在用户消息是底部行、在助手回答是 `.bottom` 首位（`AssistantMessage.tsx:143`）。 |
| 3 | 命中区 | 箭头各 28×28；计数文字不可点。 |
| 4 | 手势 | 单击箭头；**无左右滑切换**（未见实现）。 |
| 5 | 行为 | 点击 → `onSelect(messageId, anchorTop)`：`MessageList` 先记锚点与 y（`M/MessageList.tsx:104-115`）→ `selectMessageVersion` 改写父节点 `selectedChildId`（`store.ts:686-695`）→ `useLayoutEffect` 把切换器移回原 y（`:85-97`）；streaming / importingHistory 时禁用（`store.ts:690-692`；`BranchSwitcher disabled=busy`）。 |
| 6 | 视觉状态 | 箭头 tertiary；端点箭头 `disabled`（原生 disabled → 灰 + not-allowed，`IconButton.module.css:29-32`）；计数 tertiary。 |
| 7 | 布局占位与展开形态 | 行内 28 高；仅在多版本时挂载 → 首次重新生成后该行「从无到有」出现（见反证）。 |
| 8 | 滚动语义 | 随会话滚动。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | ① 单版本不渲染；② 端点禁用；③ 生成中禁用；④ 切换后该版本已选的后续分支恢复（`branches.ts:103-117`）；⑤ 版本列表对象未变时复用，避免流式重绘历史（`branches.ts:39-74`）。 |
| 11 | 无障碍 | 容器 `aria-label=消息版本`（`:35`）；箭头 `aria-label=上一个/下一个版本`；**计数无可达名**（纯 span）；无 live 播报切换结果；`data-message-version` 供锚点查询（`:36`）。 |
| 12 | 依据 | **E0** `M/BranchSwitcher.tsx:11-56`；**E0** `M/BranchSwitcher.module.css:1-20`；**E0** `M/MessageList.tsx:85-115`；**E0** `svc/chat/branches.ts:39-117`；**E0** `svc/chat/store.ts:686-695`；**E1** `README.md:32`（分支/编辑/重生成声称）。 |
| 13 | 反证 + 验收 | **反证**：若切换后直接滚到底，用户刚看的那句话被甩出屏幕（无法对比两版差异）；若切换覆盖原消息，历史不可逆。故"锚点保持 + `selectedChildId` 软切换"是必须。**验收**：`DEMO-16` 切换版本后切换器自身 y 不变（真机录屏）；`DEMO-17` 切到旧版本后其后续对话恢复（夹具）。 |

### 2.10 `chat.msg.status` — 回复的非正文状态（首 token 空窗 / 错误 / 拒绝 / 空回复）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.status`。回复体内除过程区/正文外的状态表现：等待三点、错误 notice（含动作）、拒绝模板正文。出现条件：`waiting = streaming && steps.length===0 && !answer && !message.thinking`（`M/AssistantMessage.tsx:57,79-85`）；`notice = streaming ? null : describeReply(message)`（`:49,86-126`）；`refused = stopReason==='refusal'`（`:50-52`）。 |
| 2 | 网格与定位 | waiting 高 34、`gap:6`、点 6×6（`M/AssistantMessage.module.css:14-47`）；notice 顶部 `margin-top:8`、`gap:6`、14/22、图标 16（`:49-68`）；notice 内动作是行内链接样式（`:70-82`）；模板正文复用正文字体（`:9-12`）。 |
| 3 | 命中区 | waiting 不可点；notice 内「更换 API Key」按钮 /「前往充值」链接可点（`AssistantMessage.tsx:102-123`）。 |
| 4 | 手势 | 单击动作链接；无长按。 |
| 5 | 行为 | 状态来源 `ReplyStatus`（`svc/chat/types.ts:70-74`）+ `reply.error.kind/status`；分类：401→更换 Key、402→充值、429→限流、5xx→服务器繁忙、`network`→网络异常、其他→服务端原文（`svc/chat/describe.ts:19-46`）；动作 → `openSettings('data')` / 外链充值（`AssistantMessage.tsx:99-123`）。 |
| 6 | 视觉状态 | waiting：三点 `0.9s linear infinite` 明暗、错峰 0.1/0.2s（css `:21-47`）；notice：错误红 + 图标；refusal：正文样式模板（`AssistantMessage.tsx:86-93`）；停止且无正文 → 过程区标题「已停止」（`view.ts:391-393`）。 |
| 7 | 布局占位与展开形态 | waiting 固定 34px，首个内容块出现时**整块卸载**；notice 追加在正文后，无预留占位。 |
| 8 | 滚动语义 | 随会话；正文出现时 ResizeObserver 触发贴底（若在跟随态，见 2.16）。 |
| 9 | 动效 | 三点 fading；无 reduced-motion 处理（E0：本 CSS 无 media query）。 |
| 10 | 边界态 | ① **空回复**（done 且无 blocks）：waiting=false、answer=null、notice=null → 只剩操作条（重新生成；`canContinueReply` 为 false 故无继续）可点；② 停止早于首 token：过程区不渲染、出现「继续生成」；③ 上传失败：notice 文案来自 `describeUploadError`，附件卡另有重试；④ refusal：思考/正文按协议丢弃，只显示模板（`reply.ts:143-160`；`describe.ts:52-55`）；⑤ 错误回复不进后续上下文（只有 done/stopped 且有文本的回复进上下文，`reply.ts:193-202,287-297`）。 |
| 11 | 无障碍 | waiting **无 aria-live / role**（E0 无 aria 属性）→ 读屏不知道在生成；notice 无 `role=alert`；动作是原生 button/a。 |
| 12 | 依据 | **E0** `M/AssistantMessage.tsx:49-127,141-175`；**E0** `M/AssistantMessage.module.css:14-82`；**E0** `svc/chat/describe.ts:19-59`；**E0** `svc/chat/view.ts:377-408`；**E0** `svc/chat/reply.ts:70-160,193-202`；**E0** `svc/chat/types.ts:70-106`；**E0** `src/i18n/zh.ts:67-79`。 |
| 13 | 反证 + 验收 | **反证**：若首 token 前什么都不显示，用户会以为发送失败而重复发送；若用骨架屏，首 token 到达时骨架被内容替换会闪一下；若错误只弹 3s toast，用户回看时不知道这条为什么是空的。故"固定高三点 + 常驻 inline 错误行"是这三处取舍的结果。**验收**：`DEMO-18` 发送后 0.3s 内出现三点、首个内容块出现时三点消失且无纵向跳动（录屏）；`DEMO-19` 401 错误行含「更换 API Key」且点击打开数据管理（真机）；`DEMO-20` 空回复仍可点重新生成（夹具/真机）。 |
### 2.11 `chat.msg.process` — 思考 / 搜索 / 浏览过程区（可折叠时间线）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.process`。过程区 = 可折叠标题 + 步骤时间线（thinking / search / open / find / read_link）。出现条件：`view.steps.length>0` 或（`message.thinking && streaming`）（`M/ReplyArea.tsx:71-73`）。`thinking` 开关为 false 时**不渲染标题**、只让工具步骤直出（`:74-76,89-102`）。每回复 0..1 个区域。 |
| 2 | 网格与定位 | 区域 `margin-bottom:10`（`M/ReplyArea.module.css:1-3`）；标题高 34、图标 16 + gap 6 + 文字（`:5-33`）；内容项 `padding:5 0 5 22` + 左侧 16×16 圆点槽 + 5px 圆点 + 1px 竖线（`:59-125`）；工具行高 34、图标 14（`:127-145`）；chip 高 20、圆角 6、13px（`:280-295`）。 |
| 3 | 命中区 | 标题整行（34px 高 × 全宽）可点；搜索行仅 `!wip && results>0` 时 `width:fit-content` 可点（`ReplyArea.tsx:201,253`；css `:148-151`）；页面链接与 chip 可点。 |
| 4 | 手势 | 单击标题折叠/展开；单击搜索行 / 「查看全部」开来源面板；单击页面链接新窗口；**无键盘/ARIA 等价**（见字段 11）。 |
| 5 | 行为 | 标题 → 本地 `useState(open=true)`（`ReplyArea.tsx:68,81`）；折叠只隐藏步骤区（`:89-102`）；打开搜索面板 `entry = 消息id + step.key`，再点同一入口收起（`SR/searchPanelState.ts:13-18`）；多查询时搜索词每 2s 轮播下一个（`ReplyArea.tsx:199,211-215`）；`isLast` 决定竖线收尾（`:98,176`）。 |
| 6 | 视觉状态 | 标题 default secondary、hover primary（css `:5-18`）；流式中标题 shimmer 2s（`:35-57`）；停止态图标降为 tertiary（`ReplyArea.tsx:77-83`；css `:26-28`）；搜索行 clickable hover 出胶囊底（css `:148-174`）、active 为 `interactive-bg-active`（`:176-184`）；失败换 warning 图标（`:255-257`）。 |
| 7 | 布局占位与展开形态 | **形态 A（inline-uncapped，G-03）**：标题固定 34px；展开体随内容长高，**无 max-height、无内滚、头部不 sticky**（E0：`M/ReplyArea.module.css:1-316` 通读，无 `position:sticky`/`contain`/`max-height`）；折叠时步骤区不占位，展开/折叠让下方正文与后续消息整体位移。 |
| 8 | 滚动语义 | 全部随外层会话滚动（无内层容器）→ G-04 不适用；但长过程区展开后头部会滚出视口（与 **G-06** 冲突）。 |
| 9 | 动效 | 标题 shimmer 2s；搜索词切换 fadeIn .3s（css `:193-205`）；活动圆点 pulse 1.5s（`:96-125`）；无高度动画；无 reduced-motion。 |
| 10 | 边界态 | ① 未开思考且无工具步骤 → 整区不渲染（`:71-73`）；② 思考关闭但有工具 → 无标题、步骤直出（`:74-88`）；③ 最后一块 thinking 未结束 → 圆点 pulse，结束后停（`:126-139`）；④ 搜索失败/停止/无结果各有文案（`:219-244`；`src/i18n/zh.ts:47-57`）；⑤ 阅读链接只展示进行态，成功后整行消失（`svc/chat/view.ts:322-326`）；⑥ 页内查找失败 chip 带 tooltip。 |
| 11 | 无障碍 | 标题是 **div + onClick**，无 `role`/`tabIndex`/`aria-expanded`/键盘支持（E0：`ReplyArea.tsx:81`）→ 键盘与读屏无法折叠过程区（Q-DEMO-02）；可点的搜索行同样是 div + onClick（`:246-253`）；页面链接是原生 `a`；chip 是 span + onClick（`:330-340`）。 |
| 12 | 依据 | **E0** `M/ReplyArea.tsx:56-394`；**E0** `M/ReplyArea.module.css:1-316`；**E0** `svc/chat/view.ts:154-355,357-375`；**E0** `SR/searchPanelState.ts:13-18`；**E0** `M/thinking.markdown.css:1-96`；**E0** `src/i18n/zh.ts:47-79`；**E1** `README.md:33`（深度思考/搜索/链接阅读/引用声称）。 |
| 13 | 反证 + 验收 | **反证**：若过程区默认展开且不封顶，一屏对话会被几十行"正在思考"挤满，用户找不到回答；若做成 capped-inner-scroll（形态 B），读长思考要在大对话里再套一个小滚动条，手势与外层打架（G-04）；若默认折叠，流式时看不到进展、验证"它想了什么"还要多点一次。当前"默认展开、一键收起、不封顶"只在短过程下成立 —— **长过程下缺 sticky 头（G-06）是真缺陷**。**验收**：`DEMO-21` 点标题可收起步骤区且箭头方向同步（真机）；`DEMO-22` 流式中标题 shimmer、结束后变「已思考（用时 N 秒）」（录屏）。 |

### 2.12 `chat.msg.markdown` — 回复正文渲染（Markdown → AST → React）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.markdown`。助手正文块。出现条件：`view.answer !== null`（`M/AssistantMessage.tsx:69-78`）；每回复 1 块（模板正文另 1 块，`:86-93`）。 |
| 2 | 网格与定位 | 正文 16/28（`APP/theme.css:234`）；段落 `margin:16 0`（`MD/markdown.css:49-56`）；h1–h4 24/22/20/16（`theme.css:226-236`）；表格 cell `padding:10 16`、`max-width:min(30vw,320px)`（css `:153-184`）；代码块圆角 12（css `:309-343`）；引用角标 9px 上标（css `:276-307`）；列表 `padding-left:18`、`li` 间距 6（css `:104-115`）。 |
| 3 | 命中区 | 链接、引用角标、代码块按钮、图片链接；正文文本不可点（可选中复制）。 |
| 4 | 手势 | 单击链接新窗口；长按选择文本（浏览器默认）；无自定义手势。 |
| 5 | 行为 | 流式时按**渲染耗时**节流：上次耗时 ×2 后才允许下次更新、上限 2000ms（`MD/useThrottleMarkdownRender.ts:1-44`）；AST 变化后在 `useLayoutEffect` 请求贴底（`MD/MarkdownAstRenderer.tsx:229-233`）；`[citation:N]` 解析成角标（`svc/chat/view.ts:158-181`）；外链 `sanitizeUrl` 只放行 http/https/mailto（`MD/Link.tsx:4-19`）；页内锚点与 mailto 只显示文字（`:28-36`）。 |
| 6 | 视觉状态 | 正文 primary；链接品牌色 + hover 下划线（css `:77-97`）；内联代码灰底 6px 圆角（`:192-202`）；表格横向滚动容器（`:345-347`）；公式超宽横向滚动（`:223-230`）。 |
| 7 | 布局占位与展开形态 | 参与纵向流；不展开；表格/公式/代码块内部横向滚动不撑破列宽（`max-width:100%`）。 |
| 8 | 滚动语义 | 正文内部横向滚动（表格/公式/pre）；纵向随会话。 |
| 9 | 动效 | 无（流式靠节流合并更新，不做逐字动画）。 |
| 10 | 边界态 | ① 空正文不渲染；② 超长表格横向滚；③ 公式渲染失败回退原文（`MD/MathFormula.tsx:31-47`）；④ 高亮语言包按需加载，未加载时纯文本（`MD/CodeBlock.tsx:82-105`）；⑤ Markdown 图片只给链接不给 `<img>`（`MD/Link.tsx:48-55`）；⑥ 删除线/脚注在解析阶段降级为文本（`MarkdownAstRenderer.tsx:142-144`）。 |
| 11 | 无障碍 | 语义标签齐全（p / h1–h6 / ul / ol / table / blockquote / a）；外链无"新窗口"提示；引用角标 `role=link` 但无 tabindex（见 2.14）。 |
| 12 | 依据 | **E0** `M/AssistantMessage.tsx:69-78,86-93`；**E0** `MD/Markdown.tsx:14-44`；**E0** `MD/MarkdownAstRenderer.tsx:16-247`；**E0** `MD/useThrottleMarkdownRender.ts:1-44`；**E0** `MD/markdown.css:1-380`；**E0** `APP/theme.css:225-253`；**E0** `MD/Link.tsx:4-55`。 |
| 13 | 反证 + 验收 | **反证**：若不节流，流式每个 token 都重解析整段 Markdown，主线程被占满 → 滚动掉帧、输入卡顿（长回答尤其明显）；若把 Markdown 图片直接 `<img>` 渲染，任意 URL 都会触发外链请求（隐私/追踪）。**验收**：`DEMO-23` 长回答流式时滚动不掉帧（真机录屏）；`DEMO-24` 非 http(s)/mailto 链接不产生 href（夹具）。 |

### 2.13 `chat.msg.code-block` — 代码块 banner（复制 / Mermaid 图表切换）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.code-block`。Markdown 每个 code block 1 个（`MD/MarkdownAstRenderer.tsx:194-202`）；`infostring==='mermaid'` 时额外有图表/代码切换（`MD/CodeBlock.tsx:82-83,113`）。 |
| 2 | 网格与定位 | banner `flex; justify-content:space-between; padding:6`（`MD/CodeBlock.module.css:1-7`）；语言名 12/18 等宽 + `margin-left:8`（`:9-15`）；按钮高 28、`padding:0 10`、圆角 8、13px、gap 3（`:17-31`）；`pre` padding 16、13/22（`markdown.css:337-343`）；图表区 padding 16 + 横向滚动（css `:359-370`）。 |
| 3 | 命中区 | 每个按钮 28 高 × 内容宽；图表/代码切换两枚并排 gap 4（css `:349-357`）。 |
| 4 | 手势 | 单击复制 / 单击切换；无长按。 |
| 5 | 行为 | 复制 → `copyText`，成功换勾 + 文案「已复制」**1500ms**（`CodeBlock.tsx:56-67`）；Mermaid 默认显示图表，切到代码后 `<pre>` 高亮（`:113-120`）；流式中图表按"只画完整行"策略（`MD/MermaidDiagram.tsx:43-64`）。 |
| 6 | 视觉状态 | banner 底 `--markdown-code-block-banner`；切换按钮 `aria-pressed=true` 时加底色（`markdown.css:353-356`）；复制成功图标换勾。 |
| 7 | 布局占位与展开形态 | 代码块在纵向流中；图表高度由 SVG 决定；切换图表/代码**改变块高** → 下方内容整体位移。 |
| 8 | 滚动语义 | 代码体 `white-space:pre-wrap; word-break:break-all`（`markdown.css:337-343`）→ **不横向滚动、长行折行**；图表容器横向滚（`:359-370`）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | ① 未知语言 → plaintext 高亮（`CodeBlock.tsx:102-105`）；② 空 Mermaid →「图表为空」（`MermaidDiagram.tsx:79-89`）；③ Mermaid 渲染失败只在加载结束后报错（`:60-62`）；④ 复制失败 toast；⑤ 高亮语言包加载失败不阻断（`CodeBlock.tsx:88-101`）。 |
| 11 | 无障碍 | 按钮都是原生 button；图表切换用 `aria-pressed`；复制成功换图标 + 文字（文字即"已复制"，读屏可听到变化）。 |
| 12 | 依据 | **E0** `MD/CodeBlock.tsx:11-123`；**E0** `MD/CodeBlock.module.css:1-47`；**E0** `MD/MermaidDiagram.tsx:7-92`；**E0** `MD/markdown.css:309-380`；**E0** `src/i18n/zh.ts:26-28,41-46`。 |
| 13 | 反证 + 验收 | **反证**：若代码块也做横向滚动，移动端要在页面里再横向拖一次且看不到行尾（折行更好读）；若复制成功只换图标不给文字，读屏用户听不到结果。**验收**：`DEMO-25` 复制代码后按钮显示「已复制」1.5s（真机截屏）。 |

### 2.14 `chat.msg.citation` — 正文引用角标 + 悬停来源卡

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.citation`。出现条件：文本块带 `citations`（`svc/chat/view.ts:158-181`）；每个引用角标 1 个，编号按来源首次出现顺序。 |
| 2 | 网格与定位 | 角标高 14、`padding:0 4`、`min-width:14`、圆角 `6 6 6 0`、上移 5px、9/14 等宽、`margin:0 2`（`MD/markdown.css:276-307`）。 |
| 3 | 命中区 | 角标本身约 18×14（<44dp）；hover 触发区仅角标。 |
| 4 | 手势 | **hover 300ms** 出 SourceCard（`SR/HoverCard.tsx:6,36-37`）；单击新窗口打开；鼠标可移入卡片（离开延迟 100ms，`:37-38,46-47`）；无长按。 |
| 5 | 行为 | 点击 `preventDefault + window.open(url,'_blank','noopener,noreferrer')`（`SR/CitationLink.tsx:11-15`）；悬停卡内容 = SourceCard（站点图标/站点名/日期/标题/摘要）。 |
| 6 | 视觉状态 | 角标灰底 + tertiary 字；hover 换 citation 底（`markdown.css:305-307`）；悬停卡 portal 到 body、居中于角标、放不下翻面（`HoverCard.tsx:24-27`）。 |
| 7 | 布局占位与展开形态 | 角标 inline 参与文本流（`top:-5px` 不改行高）；悬停卡是浮层（portal）不占位。 |
| 8 | 滚动语义 | 页面滚动时悬停卡跟随重定位（`B/anchored.ts:184-190`）；**滚动不自动关闭**（对比 Tooltip 会关，`B/Tooltip.tsx:34-41`）→ 长页面滚动时卡片可能停在旧内容旁（见 Q-DEMO-10）。 |
| 9 | 动效 | 悬停卡无进场动画（`SR/HoverCard.module.css` 11 行仅定位）。 |
| 10 | 边界态 | ① 同一 URL 只编号一次（`view.ts:109-152`）；② 无 title 用 URL 当标题（`:140-144`）；③ 角标可嵌在链接文字内（`CitationLink.tsx:7-9` 注释 + leaf 解析）；④ 触摸设备无 hover → 单击直接开外链。 |
| 11 | 无障碍 | `role="link"` 但**无 tabIndex、无 onKeyDown**（E0）→ 键盘不可达；可达名只有数字（读屏读"1"）；悬停卡无 role/describedby。 |
| 12 | 依据 | **E0** `SR/CitationLink.tsx:10-26`；**E0** `SR/HoverCard.tsx:1-56`；**E0** `MD/markdown.css:276-307`；**E0** `svc/chat/view.ts:109-181`；**E0** `SR/SourceCard.tsx:14-30`。 |
| 13 | 反证 + 验收 | **反证**：若角标点击直接跳外链而无预览，用户无法判断这个来源值不值得点开（搜索场景常见 10+ 来源）；若把角标做成大按钮，正文行高被撑开、排版断裂。**验收**：`DEMO-26` hover 角标 300ms 出卡、移入卡片不消失（真机）；`DEMO-27` 点击在新标签打开（真机）。 |

### 2.15 `chat.search.panel` — 右侧搜索结果 / 来源面板

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.search.panel`。右侧来源面板。出现条件：`entry !== null`（`SR/SearchPanel.tsx:13-15`）；入口 = 搜索步骤行 / 「查看全部」/ 轮尾「N 个网页」pill（`M/ReplyArea.tsx:253,332`；`M/AssistantMessage.tsx:130`）。 |
| 2 | 网格与定位 | 面板 `width:calc(50vw − 4rem); max-width:390; height:100%`、左边框 1px（`SR/SearchPanel.module.css:1-12`）；header 高 60、`padding:0 14 0 20`（`:14-22`）；内容 `padding:8`、item `margin-bottom:6; padding:12; 圆角12`（`:34-55`）；移动端 `fixed; inset:0; z-index:700` 全屏（`:57-66`）。 |
| 3 | 命中区 | 关闭按钮 28×28；来源卡整卡外链；header 标题不可点。 |
| 4 | 手势 | 单击关闭 / 单击来源卡；**同一入口再点收起**（toggleSearchPanel）。 |
| 5 | 行为 | `toggleSearchPanel(entry, sources)`：entry 相同则关闭，否则**替换内容**（`searchPanelState.ts:13-18`）；切会话 / 消息变化 / 主动关闭 → 清空（`M/MessageList.tsx:102`；`APP/App.tsx:64`；`closeSearchPanel`）；面板是 `.frame` 的兄弟节点（`App.tsx:133`）。 |
| 6 | 视觉状态 | 面板白底；来源卡 hover 换底色（css `:52-54`）；关闭图标 tertiary；移动端**无遮罩**（fixed 全屏直接盖住聊天）。 |
| 7 | 布局占位与展开形态 | 桌面：作为 `.frame` 内右侧 flex 兄弟 → **打开时挤窄聊天列**（`App.tsx:78-134`），不 overlay、不遮正文（与 G-01 的对照案例，属"并排分栏"而非三形态之一）；移动端 fixed 全屏覆盖。 |
| 8 | 滚动语义 | 面板内容 `overflow-y:auto`（css `:34-44`）；移动端全屏滚动。 |
| 9 | 动效 | 无进场动画；item hover 背景 `.2s`（css `:46-54`）。 |
| 10 | 边界态 | ① 来源为空时面板仍可开（入口要求 `results>0`，仅"查看全部"可能传空）；② 同一 URL 去重（`view.ts:109-152`）；③ 切会话即关；④ 移动端只能靠 header 关闭钮；⑤ 桌面宽度 `calc(50vw−4rem)` 与 768px 断点不冲突（<768 直接全屏）。 |
| 11 | 无障碍 | `<aside>` **无 aria-label**（E0）；标题 `role="heading"` 无 `aria-level`（`SearchPanel.tsx:19`）；关闭按钮 aria-label=关闭；来源卡是原生 `a`，标题/摘要无链接语义问题。 |
| 12 | 依据 | **E0** `SR/SearchPanel.tsx:9-35`；**E0** `SR/SearchPanel.module.css:1-66`；**E0** `SR/searchPanelState.ts:1-18`；**E0** `M/MessageList.tsx:102`；**E0** `APP/App.tsx:63-64,133`；**E0** `SR/SourceCard.tsx:14-30`；**E0** `SR/SourceCard.module.css:45-66`。 |
| 13 | 反证 + 验收 | **反证**：若来源面板是 overlay 浮层（形态 C），长列表阅读时它会挡正文且关闭成本高；若是独立页面，来回切会丢失聊天位置。当前"桌面挤宽 / 移动全屏"符合分栏直觉，代价是桌面正文列被压窄 —— 是否可让取决于正文最大宽 840px（`APP/global.css:1-11`）。**验收**：`DEMO-28` 打开面板后消息列不被遮住、关闭后回到原滚动位置（真机）。 |

### 2.16 `chat.list.scroll-follow` — 会话滚动跟随与阅读位置

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.list.scroll-follow`。滚动跟随策略层（无独立视觉）。作用于消息滚动容器：桌面 = `.container`，移动端 = 内层 `.scroll`（`M/MessageList.tsx:146-147`）。 |
| 2 | 网格与定位 | 桌面容器 `flex:1; overflow-y:auto; scrollbar-gutter:stable both-edges`（`M/MessageList.module.css:1-9`）；移动内层 `flex:1; overflow-y:auto; overscroll-behavior-y:contain`（`:15-30`）；内容列宽 `min(840−2×44, 100%−40)`、`padding-bottom:24`（`:32-37`；`APP/global.css:1-11`）；输入区吸底（见 2.1）。 |
| 3 | 命中区 | 非命中区；手势来自滚动容器。 |
| 4 | 手势 | 原生滚轮/触摸滚动（passive 监听，不 `preventDefault`）；移动端单指 touchmove 先判断目标链上是否有可滚容器，没有才阻止整页滚动（`APP/viewport.ts:13-43`）。 |
| 5 | 行为 | **贴底判据 = 距底 <40px**（`scrollHeight − scrollTop − clientHeight < 40`，`M/MessageList.tsx:33,129`）；在底部 → `sticky=true`；**只有 `scrollTop` 变小（用户上翻）才置 false**（`:133-135`）→ 内容增长/窗口变高不会停跟随；内容或容器尺寸变化（`ResizeObserver`）→ sticky 时 `scrollTop = scrollHeight`（`:63-69,72-82`）；Markdown AST 更新后同样请求贴底（`MD/MarkdownAstRenderer.tsx:229-233`）；切会话/新消息 → sticky=true + 贴底（`:85-100`）；切版本 → 记锚点再回位（`:85-97,104-115`）；跨 768 断点换滚动层时保留 sticky 与 scrollTop（`:118-140`）。 |
| 6 | 视觉状态 | 无自身视觉；`scrollbar-gutter:stable both-edges` 防"内容从不足一屏到超一屏"时横向跳动（css `:7`）；滚动条颜色 token（`APP/global.css:27-38`）。 |
| 7 | 布局占位与展开形态 | 不占位；贴底是滚动位置而非布局。 |
| 8 | 滚动语义 | 只有一个纵向滚动容器（移动端换层但语义相同）；无内滚容器 → G-04 不适用；**没有"回到底部"按钮、没有未读标记**（全仓 grep 0 命中）。 |
| 9 | 动效 | **无平滑滚动**（直接赋 `scrollTop`，E0：无 `behavior:'smooth'`）→ 跟随表现为"视口被内容顶起"而非动画。 |
| 10 | 边界态 | ① 空会话无滚动；② 用户上翻后新 token 不拉底，回到底部 40px 内自动恢复（`:131-132`）；③ 版本切换保持锚点；④ 移动端软键盘：`visualViewport` 改变高度 → 容器 resize → sticky 时再次贴底（`APP/viewport.ts:45-108`）；⑤ 来源面板打开使消息列变窄 → 容器 resize → sticky 时仍贴底。 |
| 11 | 无障碍 | 滚动容器无 role/tabindex/aria（E0）；键盘 PageUp/PageDown 依赖浏览器默认；无"新消息"播报。 |
| 12 | 依据 | **E0** `M/MessageList.tsx:32-140,146-148`；**E0** `M/MessageList.module.css:1-37`；**E0** `MD/MarkdownAstRenderer.tsx:229-233`；**E0** `APP/viewport.ts:13-111`；**E0** `SS/SessionView.module.css:32-69`；**E0** `APP/global.css:1-11`。 |
| 13 | 反证 + 验收 | **反证**：① 若用"用户是否上翻过"布尔，用户轻推到距底 5px 也会永久停跟随，之后每个 token 都要手动追；② 若内容增长不贴底，读长回答时必须不断手动滚（这正是跟随存在的理由）；③ 若跟随用平滑动画，流式时手势与动画打架（用户一滚就被拉回）。当前"40px 内跟、只有上翻才停、瞬时贴底"简单可靠，**但缺"回到底部"入口**：用户上翻后想恢复只能自己滚回去（长会话成本高）。**验收**：`DEMO-29` 上翻后流式增长不拉底、滚回距底 40px 内恢复跟随（真机录屏）；`DEMO-30` 内容增长时贴底不跳（录屏逐帧）。 |

### 2.17 `chat.app.status` — 应用级状态（保存失败横幅 / 加载失败）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.app.status`。保存失败横幅：任一 `sessionSaveErrors` 为真（`APP/App.tsx:37,81`）；加载/读取失败态：`!ready || (activeId !== null && !session)`（`:96-121`）。每应用各 1 处。 |
| 2 | 网格与定位 | 横幅在 main 顶部：`flex-wrap; gap:12; padding:12 16; 14px`（`APP/App.module.css:23-33`）；加载态居中列 `gap:16; padding:24`（`:42-53`）。 |
| 3 | 命中区 | 横幅内「重试」（Button s）；加载态「重试」（Button m）。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | 保存失败重试 → `retrySaveSessions()` 遍历未保存会话重新落盘（`store.ts:228-232`）；加载失败重试 → `!ready ? initChat() : selectSession(activeId)`（`App.tsx:109-115`）；导入历史期间重试按钮禁用（`:87`）。 |
| 6 | 视觉状态 | 横幅与页面同底色、**无错误红**（E0：仅 `<span>` + 按钮，`App.module.css:23-33`）；加载态 secondary 字；**无骨架屏**（grep skeleton 仅 2 个 CSS 变量）。 |
| 7 | 布局占位与展开形态 | 横幅在内容上方**参与布局**（压缩内容高度）；加载态 `position:absolute; inset:0` 覆盖内容区（`App.module.css:35-40`）。 |
| 8 | 滚动语义 | 不滚；加载态内容居中。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | ① 初始化失败 vs 单会话读取失败文案不同（`sessionListLoadFailed` / `sessionLoadFailed`，`src/i18n/zh.ts:9-10`）；② 保存失败文案明确"关闭页面可能丢失"（`zh.ts:11`）；③ 导入中按钮禁用；④ **无离线状态提示**（E0 未见）。 |
| 11 | 无障碍 | 横幅 `role="alert"`；加载态 `role="status" aria-live="polite"`（`App.tsx:82,99`）；按钮有可见文字。 |
| 12 | 依据 | **E0** `APP/App.tsx:35-121`；**E0** `APP/App.module.css:23-53`；**E0** `svc/chat/store.ts:228-232,298-325`；**E0** `src/i18n/zh.ts:8-12,168`。 |
| 13 | 反证 + 验收 | **反证**：若存储失败静默（只 console），用户写完的对话在关页后丢失且无从知晓；若用 3s toast 承担"可能丢数据"的警告，用户错过通知就永久丢稿。故"常驻横幅 + 重试"必需。**验收**：`DEMO-31` 模拟 IndexedDB 写入失败出现横幅并可重试（夹具）；`DEMO-32` 初始化失败文案与重试可用（夹具）。 |

### 2.18 `sider.session-row` — 会话列表行（选中 / ⋯ / 内联重命名）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sider.session-row`。每个会话 1 行（`S/Sider.tsx:82-89`）；外层按时间分组（今天/昨天/7 天内/30 天内/按月，`svc/chat/sessionGroups.ts:9-33`）。 |
| 2 | 网格与定位 | 行高 40、`padding:9 6 9 10`、圆角 12、14/22（`S/SessionItem.module.css:1-12`）；移动端高 44、圆角 16、字号 16（`:68-86`）；标题单行 ellipsis（`:33-40`）；操作区默认 `display:none`，`@media(hover:hover)` + hover/active 时 `flex`（`:19-30,42-46`），`@media(hover:none)` 常显（`:62-66`）。 |
| 3 | 命中区 | 整行可点（导航）；⋯ 在行右侧（IconButton xs=28；移动端行高 44）。 |
| 4 | 手势 | 单击选中；hover 显示 ⋯；单击 ⋯ 打开菜单（见 2.19）；无长按/双击/滑动。 |
| 5 | 行为 | 点行 → `selectSession` + `closeMobileSider`（`SessionItem.tsx:53-57`）；选中高亮；重命名提交 → `renameSession`（`store.ts:466-485`），并移除自动标题请求（`:470-473`）→ 手动名不被后续生成覆盖。 |
| 6 | 视觉状态 | default primary；active 品牌浅底 + 品牌字（css `:14-17`）；hover 灰底（`:19-23`）；编辑态输入框品牌描边（`:48-60`）。 |
| 7 | 布局占位与展开形态 | 行固定高；⋯ 出现/消失**改变标题可用宽度**（flex 1 + min-width 0 → 截断位置变化），不改行高。 |
| 8 | 滚动语义 | 列表容器滚动（`S/Sider.module.css:89-95`）；分组头 `position:sticky; top:0; z-index:20`（`:107-118`）。 |
| 9 | 动效 | 无 transition（E0：`.item` 无 transition 声明）。 |
| 10 | 边界态 | ① 空标题 → 显示「新对话」（`SessionItem.tsx:23`）；② 超长标题 ellipsis；③ 重命名空值不提交（`:28-31`）；④ 重命名后自动标题被取消（`store.ts:470-473`）；⑤ 移动端 ⋯ 常显；⑥ 导入/删除中行本身无 disabled（E0）→ 仍可点击选中。 |
| 11 | 无障碍 | 行是 **div + onClick**（无 `role`/`tabindex`/`aria-current`）→ 键盘不可达、读屏不报"当前会话"；⋯ 是 IconButton + aria-label=更多；重命名 input 无 aria-label（E0）。 |
| 12 | 依据 | **E0** `S/SessionItem.tsx:12-84`；**E0** `S/SessionItem.module.css:1-86`；**E0** `S/Sider.tsx:77-93`；**E0** `svc/chat/store.ts:457-485`；**E0** `svc/chat/sessionGroups.ts:9-33`；**E0** `S/Sider.module.css:89-118`。 |
| 13 | 反证 + 验收 | **反证**：若 ⋯ 常驻，小屏上标题可用宽度被永久挤压（"新对话"3 字也可能被截）；若 ⋯ 只用 hover 且触摸设备无兜底，移动端永远无法重命名/删除（本仓库用 `@media(hover:none)` 兜底，E0 css `:62-66`）。**验收**：`DEMO-33` `hover:none` 环境下 ⋯ 常显（截图）；`DEMO-34` 重命名回车提交、Esc 放弃（真机）。 |

### 2.19 `sider.session-menu` — 会话行 ⋯ 下拉菜单

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sider.session-menu`。点 ⋯ 打开（`Menu` 组件本地 state）；每行 1 个实例（E0：`Menu.tsx:23` 为组件内 `useState`，无全局状态）；点另一行的 ⋯ 时旧菜单先由 `useDismiss` 的外点关闭（`anchored.ts:214-224`）。选项：重命名 / 删除（`SessionItem.tsx:60-76`）。 |
| 2 | 网格与定位 | 菜单 `min-width:124; padding:4; 圆角12` + 阴影（`B/Menu.module.css:5-17`）；选项 `padding:8 10; gap:8; 圆角8; 14/24`（`:19-34`）；定位 `align:start; alignOffset:-5; offset:4`（`Menu.tsx:26-29`）→ 与 ⋯ 图标左对齐。 |
| 3 | 命中区 | 每项高 40（padding 8 + line-height 24）；宽度 ≥124。 |
| 4 | 手势 | 单击选项；点外部或 Esc 关闭（`useDismiss`，`B/anchored.ts:203-241`）；**无键盘上下键导航**（E0：Menu 无 onKeyDown）。 |
| 5 | 行为 | 重命名 → `setEditing(true)`（行切换为 input）；删除 → `onDelete()`（打开 2.20 确认弹窗）；点选项先关菜单再执行（`Menu.tsx:50-53`）。幂等。 |
| 6 | 视觉状态 | 菜单底 `--dsw-specific-menu` + 双层阴影；选项 hover 灰底；删除项 danger 红色（css `:41-46`）。 |
| 7 | 布局占位与展开形态 | 浮层 portal 到 body（`Menu.tsx:37-62`）→ 不占位；`position:fixed` + 打开时测量（`anchored.ts:85-200`）。 |
| 8 | 滚动语义 | 菜单自身不滚（无 maxHeight）；页面滚动时跟随重定位（`anchored.ts:184-190`）。 |
| 9 | 动效 | 无进场动画。 |
| 10 | 边界态 | ① 打开后页面滚动 → 跟随；② 窗口变窄 → clamp 到 8px 边距（`anchored.ts:52-63`）；③ 打开中该行被删 → 随 anchor 卸载消失（E0 无额外处理）；④ 键盘触发（Enter on ⋯）→ 菜单出现但焦点不进菜单（E0）。 |
| 11 | 无障碍 | `role=menu` + `role=menuitem`（`Menu.tsx:43,48`）；**无焦点管理/方向键/Esc 后焦点归还**；菜单项是 div（非 button）。 |
| 12 | 依据 | **E0** `B/Menu.tsx:16-65`；**E0** `B/Menu.module.css:1-47`；**E0** `B/anchored.ts:81-241`；**E0** `S/SessionItem.tsx:60-81`。 |
| 13 | 反证 + 验收 | **反证**：若重命名/删除做成常驻两个图标，行内空间不够且删除离标题太近易误触；若菜单按鼠标坐标弹出，列表滚动时会明显漂移。**验收**：`DEMO-35` 菜单与 ⋯ 对齐、点外部关闭、删除项红色（截图）；`DEMO-36` Esc 关闭菜单（真机）。 |

### 2.20 `sider.delete-dialog` — 删除单会话确认弹窗

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sider.delete-dialog`。出现条件：`deleting !== null`（`S/Sider.tsx:102`）。每次删除 1 个。 |
| 2 | 网格与定位 | Modal 默认宽 380、`padding:22 24`、圆角 24（`B/Modal.module.css:17-34`）；footer 右对齐 `gap:8; margin-top:20`（`:63-68`）；主按钮 danger、次按钮 outlined。 |
| 3 | 命中区 | 两枚 Button（size=l 高 40、min-width 72）；**标题栏无关闭 X**（未传 `closable`，`Sider.tsx:103-109`）。 |
| 4 | 手势 | 单击取消/删除；Esc 或点遮罩关闭（`onClose` 在 `deletePending` 时忽略，`Sider.tsx:105-109`）。 |
| 5 | 行为 | 确认 → `removeSession(id)`：abort 进行中的流 → 等流收尾 → 删 IndexedDB → 清缓存/草稿 → 若删除的是当前会话则 `activeId=null`（`store.ts:491-547`）；成功 `setDeleting(null)`，失败 toast（`Sider.tsx:118-132`）；`deletePending` 期间两按钮禁用（`:112-117`）。**不可撤销**。 |
| 6 | 视觉状态 | 标准 Modal（遮罩黑 0.36 + blur 2，深色 0.6，`Modal.module.css:1-15`）；危险按钮红底白字（`B/Button.module.css:75-82`）。 |
| 7 | 布局占位与展开形态 | **形态 C（sheet-overlay）**：portal 到 body，不占聊天布局（G-01 兼容）。 |
| 8 | 滚动语义 | 内容短，不滚。 |
| 9 | 动效 | Modal 无动画（E0：`Modal.module.css` 无 animation/transition）；toast 有进场 .2s（`B/Toast.module.css:24`）。 |
| 10 | 边界态 | ① 取消/遮罩/Esc 均关闭；② 删除中不可关闭；③ 失败保留会话并 toast；④ 删除当前会话 → 回欢迎页；⑤ 附件原件只在无其他会话引用时清理（`store.ts:487-490` 注释 + `db.ts` fileIds 索引）。 |
| 11 | 无障碍 | `role=dialog` + `aria-modal` + `aria-labelledby`（`Modal.tsx:57-61`）；**无初始焦点、无焦点陷阱、关闭后不归还焦点**（E0）；按钮为原生 button。 |
| 12 | 依据 | **E0** `S/Sider.tsx:102-138`；**E0** `B/Modal.tsx:13-82`；**E0** `B/Modal.module.css:1-68`；**E0** `svc/chat/store.ts:487-547`；**E0** `src/i18n/zh.ts:15-16`。 |
| 13 | 反证 + 验收 | **反证**：删除是全仓**唯一没有 undo** 的操作（连删除全部都要二次确认），一次误点就永久丢失整段对话与附件，必须二次确认；若确认弹窗与删除动作之间还有异步（等流收尾），取消/关闭必须被 `deletePending` 拦住，否则会出现"弹窗关了但删除还在跑"的错觉。**验收**：`DEMO-37` 删除弹窗 Esc/取消不删除（真机）；`DEMO-38` 删除失败保留会话并 toast（夹具）。 |

### 2.21 `sider.new-chat` — 新会话入口（3 处 + 欢迎页）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sider.new-chat`。实例：侧栏大按钮（`S/Sider.tsx:64-76`）、收起态圆钮（`S/CollapsedSider.tsx:21-27`）、移动顶栏右钮（`SS/MobileHeader.tsx:23-29`）；欢迎页本身即"空会话"态。 |
| 2 | 网格与定位 | 侧栏按钮 `100% × 40`、圆角 100、白底双阴影、图标右距 6（`S/Sider.module.css:37-80`）；收起态浮组 absolute top/left 0 + `margin:12px 0 0 16px`、组高 40（`:171-193`）；移动顶栏 IconButton `xl=44`（`SS/MobileHeader.module.css:1-8`）。 |
| 3 | 命中区 | 侧栏按钮整行 40 高；收起态/移动端 44×44（xl）。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `newChat()` 仅把 `activeId` 置 null（`store.ts:450-455`，**不新建记录**）→ 渲染 Welcome；移动端同时关抽屉（`Sider.tsx:69`）；草稿存在 `null` 名下（`drafts.ts:25-31`）。幂等。 |
| 6 | 视觉状态 | 侧栏按钮 default 白底 + 双阴影、hover 阴影加深（css `:56-74`）；深色模式去阴影改内描边（`:69-75`）；收起态为浮白圆组。 |
| 7 | 布局占位与展开形态 | 侧栏按钮在列表上方固定（`flex-shrink:0`）；收起态浮层 absolute **不占位**；移动顶栏固定在顶（60 高）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 侧栏按钮 `box-shadow .3s`（css `:60`）。 |
| 10 | 边界态 | ① 导入历史中 `newChat` 无效（`store.ts:451`）；② 已有草稿时点新会话 → 切到 null 草稿，原会话草稿保留在 Map 里，切回仍在；③ 当前已在欢迎页再点 → 无变化。 |
| 11 | 无障碍 | 侧栏按钮是原生 button + 可见文字；收起态/移动端 IconButton 有 tooltip/aria-label（新对话 / 打开边栏 等）。 |
| 12 | 依据 | **E0** `S/Sider.tsx:64-76`；**E0** `S/CollapsedSider.tsx:21-27`；**E0** `SS/MobileHeader.tsx:23-29`；**E0** `svc/chat/store.ts:450-455`；**E0** `svc/chat/drafts.ts:25-31`；**E0** `S/Sider.module.css:37-80,171-193`；**E0** `APP/App.tsx:122-130`。 |
| 13 | 反证 + 验收 | **反证**：若点"新对话"立刻创建并持久化一条空会话，用户每点一次就多一条空记录，列表被垃圾填满；若入口只在侧栏（收起后不可见），收起侧栏的用户找不到入口。故"三处常驻入口 + 不落库的 null 会话"是必须。**验收**：`DEMO-39` 点新对话后列表不新增空记录、回到欢迎页（真机）；`DEMO-40` 侧栏收起态/移动顶栏仍能新建（截图）。 |
### 2.22 `sider.toggle` — 侧栏收起/展开 + 移动端抽屉

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sider.toggle`。实例：桌面顶栏收起钮（`S/Sider.tsx:56-62`）、收起态展开钮（`S/CollapsedSider.tsx:14-20`）、移动顶栏菜单钮（`SS/MobileHeader.tsx:13-19`）、抽屉遮罩（`S/Sider.tsx:151`）。 |
| 2 | 网格与定位 | 桌面侧栏宽 261、`padding:6 12 0`（`S/Sider.module.css:1-11`）；收起态浮组左上（`:171-193`）；移动抽屉宽 300、`max-width:calc(100vw − 48px)`、`z-index:800`、遮罩 `z-index:799`（`:195-211`；`Sider.tsx:144-157`）；移动顶栏高 60（`SS/MobileHeader.module.css:1-8`）。 |
| 3 | 命中区 | 收起/展开/菜单按钮 IconButton m=34 或 xl=44；遮罩全屏。 |
| 4 | 手势 | 单击；移动端**无滑动开合**（E0：无 touch/pointer 处理）→ 只能点按钮/遮罩。 |
| 5 | 行为 | 桌面 → `setSiderCollapsed(bool)` 写 localStorage（`svc/settings.ts:137-140`），收起后内容区出现 `CollapsedSider`（`APP/App.tsx:95`）；移动端 → `openMobileSider/closeMobileSider`（`S/mobileSiderState.ts:1-8`）；**切会话、切布局自动收抽屉**（`App.tsx:68`）；桌面收起态下会话标题左移 113px 避开浮组（`SS/SessionView.module.css:28-30`）。 |
| 6 | 视觉状态 | 抽屉浮于页面（absolute top/bottom left，**无位移动画**）；遮罩**无背景色**（E0：`.drawerMask` 无 background）；收起态白色胶囊组 + 阴影。 |
| 7 | 布局占位与展开形态 | 桌面展开时侧栏是 flex 列、占 261px（`APP/App.module.css:1-7`）；收起后**不占位**、浮组 absolute 覆盖内容左上（`CollapsedSider` 在 `.content` 内，`App.tsx:95`；css `:171-180`）；移动端抽屉 absolute 不占位。 |
| 8 | 滚动语义 | 桌面侧栏列表独立滚（`Sider.module.css:89-95`）；抽屉内列表同样滚；抽屉打开时页面其余部分无滚动锁（E0 未见）。 |
| 9 | 动效 | 无开合动画（E0：drawer/CollapsedSider 无 transition）；仅按钮 hover。 |
| 10 | 边界态 | ① 移动端打开抽屉 → 点遮罩/选会话/新对话/设置后关闭（`Sider.tsx:69,151`；`App.tsx:68`）；② 跨 768 断点时 `siderCollapsed` 被忽略（`App.tsx:34` `&& !isMobile`）；③ 抽屉打开时键盘 Tab 可跑到背后页面（无 focus trap，E0）；④ 深色主题抽屉配色跟随 token。 |
| 11 | 无障碍 | 按钮有 tooltip/aria-label（收起边栏 / 打开边栏）；遮罩是 div + onClick（**无 role、无 aria-label、不可键盘关闭**）；抽屉无 `role=dialog`/focus trap（E0）。 |
| 12 | 依据 | **E0** `S/Sider.tsx:19-62,144-157`；**E0** `S/Sider.module.css:1-11,171-247`；**E0** `S/CollapsedSider.tsx:9-30`；**E0** `SS/MobileHeader.tsx:9-34`；**E0** `S/mobileSiderState.ts:1-8`；**E0** `svc/settings.ts:137-140`；**E0** `APP/App.tsx:34,68,95`；**E0** `SS/SessionView.module.css:28-30`。 |
| 13 | 反证 + 验收 | **反证**：若收起后侧栏仍占 261px（只隐藏内容），用户收起是为了给内容腾宽却什么都没腾出来；若移动端抽屉不提供遮罩点击关闭，用户只能精确点回菜单按钮（一次误触要重开）。**验收**：`DEMO-41` 桌面收起后内容区变宽且标题避开浮组（截图）；`DEMO-42` 移动端点遮罩关闭抽屉（真机）。 |

### 2.23 `sider.settings-entry` — 侧栏底部设置入口

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `sider.settings-entry`。出现条件：侧栏展开（桌面/移动抽屉）时各 1 个（`S/Sider.tsx:94-101`）。 |
| 2 | 网格与定位 | `.bottom` 高 64（`flex-shrink:0`）；按钮占满、`padding:6 2 6 6`、圆角 12（`S/Sider.module.css:120-149`）；图标槽 32×32；文字 14 单行（`:151-169`）；移动端 `padding:9 10`、图标 20、文字 16（`:228-246`）。 |
| 3 | 命中区 | 整行（宽 100% × 按钮自身高约 44）。 |
| 4 | 手势 | 单击。 |
| 5 | 行为 | `openSettings('general')` 打开设置弹窗并选中「通用设置」（`SE/settingsDialogState.ts:13-14`）。 |
| 6 | 视觉状态 | default 透明；hover 侧栏 hover 底（css `:142-144`）；`focus-visible` 内 2px 环（`:146-148`）。 |
| 7 | 布局占位与展开形态 | 固定在侧栏底部；移动抽屉里同样吸底（css `:223-226`）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | ① 桌面**收起侧栏后没有设置入口**（`CollapsedSider` 只有展开/新对话两个按钮，E0 `:9-30`）→ 必须先展开；② 移动端抽屉内可用。 |
| 11 | 无障碍 | 原生 button + 可见文字「设置」+ 图标；无额外 aria-label（文字即名）。 |
| 12 | 依据 | **E0** `S/Sider.tsx:94-101`；**E0** `S/Sider.module.css:120-169,223-246`；**E0** `SE/settingsDialogState.ts:13-14`；**E0** `S/CollapsedSider.tsx:9-30`。 |
| 13 | 反证 + 验收 | **反证**：若设置藏在头像下拉里，首次使用必须填 API Key 的用户可能找不到入口；demo 把设置直接暴露在侧栏底部，新用户可三步内开始。但收起侧栏后入口消失是代价（Q-DEMO-09）。**验收**：`DEMO-43` 侧栏底部可开设置并停在通用设置（截图）。 |

### 2.24 `settings.dialog` — 设置弹窗（分组导航 + 关闭）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.dialog`。出现条件：`open=true`（`SE/settingsDialogState.ts:8-16`）；打开来源：侧栏设置入口、无 API Key 时自动（`App.tsx:70-74`）、401 错误动作（2.10）。 |
| 2 | 网格与定位 | 桌面 760×500（`max-height:100vh−32`）、`padding:19 24 22`（`SE/Settings.module.css:1-7`）；导航列宽 172、gap 4（`:16-24`）；内容列 `flex:1` + 自身滚动（`:62-71`）；移动端底部弹层：100% 宽、高 `100%−64`、`border-radius:24 24 0 0`（`:351-360`），导航横排可横滚（`:365-388`）。 |
| 3 | 命中区 | tab 按钮 `min-height:40`（移动端 `min-width:108`）；关闭 X 在标题栏；行内控件见 2.25/2.26/2.27。 |
| 4 | 手势 | 单击 tab 切换；Esc / 遮罩 / X 关闭（Modal）；无左右滑。 |
| 5 | 行为 | 打开时按调用方指定分组（`useSettingsDialog.getState().section`，`SE/SettingsDialog.tsx:66-67`）；切换分组是本地 state、**不持久化**（`:95`）；关闭：非删除中 → `historyImport.dismiss()` + `closeSettings()`（`:80-85`）；删除全部进行中点关闭无效（`.busy` 守卫）。 |
| 6 | 视觉状态 | 标准 Modal 遮罩；tab hover/active 灰底（css `:52-55`）；`focus-visible` 2px 环（`:57-59`）。 |
| 7 | 布局占位与展开形态 | **形态 C（sheet-overlay）**：桌面居中、移动底部弹层；打开不改变聊天布局。 |
| 8 | 滚动语义 | 内容列 `overflow-y:auto`（css `:70`）；移动端导航横向滚动（`:370`）。 |
| 9 | 动效 | Modal 无进场动画；导入进度条 4px 高品牌填充（`:153-169`）。 |
| 10 | 边界态 | ① 移动端打开时不自动聚焦 API Key 输入（见 2.26）；② 弹窗内再叠确认弹窗（导入/删除全部）→ Esc 只关最上层（Modal 栈，2.30）；③ 分组只 2 个（通用 / 数据）；④ 关闭时丢弃未确认的导入选择（dismiss）；⑤ 桌面高度不足时由 max-height 兜底（内容列滚动）。 |
| 11 | 无障碍 | Modal 自带 `role=dialog/aria-modal/aria-labelledby`；`tablist/tab/aria-selected/tabpanel` 齐全（`SettingsDialog.tsx:87-102`）；**tab 无方向键切换、tabpanel 无 aria-labelledby、无焦点陷阱**（E0）。 |
| 12 | 依据 | **E0** `SE/SettingsDialog.tsx:44-139`；**E0** `SE/Settings.module.css:1-71,351-395`；**E0** `B/Modal.tsx:36-82`；**E0** `SE/settingsDialogState.ts:1-25`。 |
| 13 | 反证 + 验收 | **反证**：若设置做成整页路由，用户在对话中改主题/Key 会丢失滚动位置与草稿上下文；若做成 hover 面板，移动端不可用。**验收**：`DEMO-44` 移动端设置从底部弹出且导航可横滚（真机截图）；`DEMO-45` 打开弹窗后 Esc 只关最上层（真机）。 |

### 2.25 `settings.preferences` — 通用设置控件（主题 / 语言 / 音色 / 位置）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.preferences`。设置「通用设置」分组内的控件组：主题三选（radiogroup）+ 语言 Select + 朗读音色 Select + 位置（省/市 或 国家/地区）（`SE/SettingsDialog.tsx:103-109`）。 |
| 2 | 网格与定位 | 主题组 `margin-top:10`、options `gap:12; margin:8 0 10`（`SE/Settings.module.css:209-219`）；每块 `min-width:128; height:82; padding:21 32; 圆角12`、竖排图标+文字（`:221-253`）；Select 触发器高 36、圆角 18、`min-width:90`、右侧箭头（`B/Select.module.css:1-42`）；省/市宽 159/120，国家/地区 `max-width:min(285,100%)`（`Settings.module.css:196-207`）。 |
| 3 | 命中区 | 主题块整块（≥128×82）；Select 触发器整块 36 高。 |
| 4 | 手势 | 单击主题块；单击 Select 开下拉（见 2.29）；无长按。 |
| 5 | 行为 | 主题 → `setThemePreference` 写 localStorage 并立即重绘（`svc/settings.ts:112-115` + `APP/theme.ts:55-70`）；语言 → `setLocale`（同时改 `document.documentElement.lang`，`App.tsx:55-57`）；音色 → `setTtsVoice`（下一次朗读生效，`svc/speech/readAloud.ts:95`）；位置 → 省/市联动（未选省时市 Select disabled，`SettingsDialog.tsx:244-258`）、非中文浏览器只选国家/地区（`:260-271`）；**位置模式跟随浏览器语言**而非界面语言（`svc/settings.ts:53-55`）。 |
| 6 | 视觉状态 | 主题块 default 透明 + l2 边；hover/selected 浅灰底（css `:245-248`）；Select 见 2.29。 |
| 7 | 布局占位与展开形态 | 内容列内依次排列，cell 间 1px 分隔线（css `:73-85`）；不改聊天布局；主题切换有全局"禁过渡"一帧（`APP/global.css:40-44`）避免整页颜色渐变。 |
| 8 | 滚动语义 | 内容列滚动（见 2.24）。 |
| 9 | 动效 | 主题块背景 `.2s`（css `:243`）；主题切换瞬间禁用所有 transition 一帧（`global.css:40-44`）。 |
| 10 | 边界态 | ① 主题=跟随系统 → 监听 `prefers-color-scheme`（`APP/theme.ts:6-23`）；② 位置为空 → placeholder；③ 省份无城市 → 市 Select disabled；④ 浏览器非中文 → 只选国家/地区。 |
| 11 | 无障碍 | 主题组 `role=radiogroup` + aria-label；每块 `role=radio` + `aria-checked`（`SettingsDialog.tsx:380-390`）；Select 有 aria-label + aria-haspopup/expanded（见 2.29）。 |
| 12 | 依据 | **E0** `SE/SettingsDialog.tsx:214-400`；**E0** `SE/Settings.module.css:196-253`；**E0** `svc/settings.ts:53-140`；**E0** `APP/theme.ts:6-71`；**E0** `APP/global.css:31-44`；**E0** `B/Select.tsx:14-93`。 |
| 13 | 反证 + 验收 | **反证**：若主题只改 CSS 变量不监听系统变化，"跟随系统"在系统切深浅色时要刷新才生效；若语言切换只改文案不改 `document.lang`，读屏发音与浏览器翻译会错。**验收**：`DEMO-46` 系统切深浅色时"跟随系统"即时生效（真机）；`DEMO-47` 切英文后 `<html lang>` 同步（夹具）。 |

### 2.26 `settings.apikey` — API Key 字段（掩码 / 编辑 / 校验后保存）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.apikey`。设置「数据管理」分组顶部；`showInput = !apiKey \| editing`（`SE/ApiKeyField.tsx:11-16`）。每设置弹窗 1 个。 |
| 2 | 网格与定位 | cell `min-height:60`（`SE/Settings.module.css:73-85`）；已保存态胶囊高 40（桌面）/52（移动）、圆角 28/40、`padding:0 10 0 16`（`:274-297,424-436`）；输入态高 40/52、gap 12、`padding:0 14`、圆角 22（`:299-326`）；错误文案 12/18 红（`:261-265`）；移动端整行确定钮高 52、圆角 100（`:448-468`）。 |
| 3 | 命中区 | 编辑图标 IconButton xs=28；输入框整块可聚焦；桌面内联「确定」（文字钮）、移动端整行确定钮（52 高）。 |
| 4 | 手势 | 单击编辑/确定；输入；Enter 提交（桌面与移动端都绑，`ApiKeyField.tsx:51-55`）。 |
| 5 | 行为 | 提交 → `verifyApiKey` GET /models（`svc/api/client.ts:94-100`）→ 成功 `setApiKey` 写 localStorage、退出编辑、清空输入（`SE/useApiKeyForm.ts:30-47`）；失败分类：401→API Key 无效、network→网络异常、其他→「校验失败：{message}」（`:14-28`）；verifying 期间按钮禁用 + 文案「正在验证...」。 |
| 6 | 视觉状态 | 掩码 = `'*'.repeat(apiKey.length)`（`ApiKeyField.tsx:32`）→ **泄露 Key 长度**；输入框 `focus-within` 品牌描边（css `:306-308`）；错误红色小字。 |
| 7 | 布局占位与展开形态 | 内容列内；高度随已保存/编辑态与错误文案变化，会推下方的导入/导出/删除项。 |
| 8 | 滚动语义 | 内容列滚动。 |
| 9 | 动效 | 边框色 `.2s`（css `:304`）。 |
| 10 | 边界态 | ① 已有 Key 只显示掩码，不显示明文；② 修改时清空输入并失焦 → 回到已保存态（`:56-61`）；③ 移动端打开设置不 autoFocus（`:47-48`）；④ 校验中重复提交被 verifying 挡住；⑤ 首次使用无 Key → 自动打开该分组（`settingsDialogState.ts:19-25` + `App.tsx:70-74`）。 |
| 11 | 无障碍 | `input type=password` + placeholder，**无 aria-label**（E0）；错误文案是 div，**未用 aria-live / aria-describedby 关联**（E0）；按钮有可见文字。 |
| 12 | 依据 | **E0** `SE/ApiKeyField.tsx:11-100`；**E0** `SE/useApiKeyForm.ts:1-50`；**E0** `SE/Settings.module.css:73-85,261-350,424-468`；**E0** `svc/api/client.ts:93-100`；**E0** `svc/settings.ts:122-125`；**E0** `SE/settingsDialogState.ts:19-25`；**E0** `APP/App.tsx:70-74`。 |
| 13 | 反证 + 验收 | **反证**：若保存前不校验（直接写），用户把 Key 打错后要等到第一次提问失败才知道，且失败发生在聊天里而非设置里；若保存后仍显示明文，截图/录屏都会泄露 Key；若保存后不给"编辑"入口，换 Key 只能清 localStorage。**验收**：`DEMO-48` 错误 Key 校验失败显示「API Key 无效」且不保存（夹具）；`DEMO-49` 保存后只显示掩码、刷新后仍掩码（真机）。 |

### 2.27 `settings.history` — 历史导入 / 导出 / 删除全部

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.history`。数据管理三组操作：导入历史对话（选 ZIP → 二次确认 → 进度）、导出全部（ZIP 下载）、删除所有对话（二次确认）。出现条件：设置「数据管理」分组（`SE/SettingsDialog.tsx:111-131`）。 |
| 2 | 网格与定位 | cell = label+description+status 左、按钮右（css `:414-443`）；按钮 `size=m` 高 36、移动端 `min-height:44`（`:400-403`）；进度条容器、track 4px（`:142-169`）；导入确认 / 删除确认是各自独立 Modal（`:142-176,324-349`）。 |
| 3 | 命中区 | 按钮高 36（桌面）/44（移动）；进度条非交互；确认弹窗两枚 Button。 |
| 4 | 手势 | 单击；无拖放。 |
| 5 | 行为 | 导入 = **覆盖**：选文件 → 弹「确认导入历史对话？」→ 确认后才检查 ZIP 并流式写暂存、成功原子替换全部会话与附件（`SettingsDialog.tsx:445-486,488-564`；`svc/chat/backup.ts:47-52`；`store.ts:395-448 replaceHistory`）；导出 = 生成 ZIP 并 `<a download>` 点击（`SettingsDialog.tsx:573-589`）；删除全部 = 确认后 `clearHistory()`（空历史原子替换，`backup.ts:54-58`）；三者在 `backup.ts` 内**串行队列**（`:9-16`）。 |
| 6 | 视觉状态 | 按钮 outlined；进行中按钮换 loading 图标 + `aria-busy`（`:508-525`）；进度条品牌色；成功/失败文案显示在 description 位（成功「已导入 N 个对话」、失败为长错误文案 `src/i18n/zh.ts:113-127`）；导出失败带原因（`:584-586,406-412`）。 |
| 7 | 布局占位与展开形态 | 内容列内；导入中 cell 换行（css `:111-123`）→ 高度增长；确认弹窗叠加在设置之上（Modal 栈）。 |
| 8 | 滚动语义 | 内容列滚动；删除确认弹窗 `max-height:100dvh−32` + 自身滚动（css `:479-482`）。 |
| 9 | 动效 | 导入中图标 `spin .8s`（css `:125-134`，reduced-motion 关闭）。 |
| 10 | 边界态 | ① 无会话时导出按钮禁用（`:569,601`）；② 导入校验失败 → 保留现有数据 + 长错误文案（`backup.ts:43-52`）；③ 导入/导出/删除互斥（`historyBusy` 禁用全部按钮，`:72,113-114,123`）；④ 导入成功会清空草稿并停止生成（`store.ts:405-408,426`）；⑤ 导出不含 API Key/设置/非图片原件（`README.md:42`，E1）；⑥ 删除失败 toast。 |
| 11 | 无障碍 | 进度条 `role=progressbar` + `aria-valuemin/max/now` + `aria-label`（`:542-548`）；按钮 `aria-busy`；Modal 语义见 2.24/2.30。 |
| 12 | 依据 | **E0** `SE/SettingsDialog.tsx:107-212,323-349,445-609`；**E0** `SE/Settings.module.css:107-169,400-403,470-502`；**E0** `svc/chat/backup.ts:1-58`；**E0** `svc/chat/store.ts:395-448`；**E0** `src/i18n/zh.ts:108-127`；**E1** `README.md:38-44`（存储与导入导出范围）。 |
| 13 | 反证 + 验收 | **反证**：若导入不二次确认且不"先暂存后原子替换"，用户选错文件会立刻清空全部历史且无法回滚（源码用 staging store 正是为此，`db.ts:26-32`）；若导入/导出/删除可并发，读写同一 IndexedDB 会让导出拿到半替换数据。**验收**：`DEMO-50` 导入确认前取消不改变任何数据（夹具）；`DEMO-51` 导入失败保留原会话（夹具）；`DEMO-52` 无会话时导出禁用（截图）。 |

### 2.28 `base.button` — 通用按钮原语（Button / IconButton）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.button`。通用按钮原语，被全站复用（发送/停止/侧栏/设置/确认弹窗/继续生成/图标按钮）。非独立区域；每处实例独立。 |
| 2 | 网格与定位 | Button 高由 size 决定：`l=40 / m=36 / s=32`（`APP/theme.css:9-13`），`padding:0 14`、圆角 4096px、`min-width:72`（s 为 58）、14/22（`B/Button.module.css:1-53`）；IconButton 正圆 `28/34/44`，图标 16/20（`B/IconButton.module.css:35-48`；`IconButton.tsx:7`）。 |
| 3 | 命中区 | Button 高 32–40 × 文本宽（min 58–72）；IconButton 28/34/44 见方（28 与 34 均 <44dp）。 |
| 4 | 手势 | 单击；键盘 Enter/Space（原生 button）；无长按。 |
| 5 | 行为 | 透传原生 button 语义；固定 `type="button"`（`Button.tsx:16-17`；`IconButton.tsx:27`）→ 不会误提交表单；disabled 为原生（`Button`/`IconButton` 均透传 `disabled`）。 |
| 6 | 视觉状态 | Button 4 变体（primary / outlined / danger / dangerOutlined）；hover 各自换色、disabled `opacity:.45` + `not-allowed`（`Button.module.css:26-29,55-97`）；`focus-visible` 2px 品牌环（`:22-24`；`IconButton.module.css:25-27`）；IconButton `tone=tertiary/primary` 决定前景色（`:50-56`）。 |
| 7 | 布局占位与展开形态 | 不展开；尺寸由 class 决定；IconButton `flex-shrink:0`（`IconButton.module.css:6`）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | `background/opacity .2s`（`Button.module.css:18-20`；`IconButton.module.css:12`）。 |
| 10 | 边界态 | ① disabled 时 hover 提示不可用（原生行为）；② 大字号：尺寸为 px/主题变量，不随字号档变化（E0）；③ 长文案 `white-space:nowrap` 可能溢出容器（E0 无 truncate）。 |
| 11 | 无障碍 | 原生 button；Button 用可见文字；IconButton 用 tooltip 当 aria-label（`IconButton.tsx:26-33`），无 tooltip 时必须外部传 aria-label（源码注释 `:8-9`）；disabled 原生禁用（不可聚焦）。 |
| 12 | 依据 | **E0** `B/Button.tsx:5-21`；**E0** `B/IconButton.tsx:10-42`；**E0** `B/Button.module.css:1-97`；**E0** `B/IconButton.module.css:1-56`；**E0** `APP/theme.css:8-26`。 |
| 13 | 反证 + 验收 | **反证**：若图标按钮都用 `div role=button`，会丢掉 Enter/Space 与 disabled 的原生行为；而"用 `aria-disabled` 但仍可点击"（发送按钮就是这样）必须自己守卫，否则点击仍会执行——这是本仓库在发送按钮上做三重守卫的原因（`ChatInput.tsx:135-146`）。**验收**：`DEMO-53` 所有图标按钮有可达名（a11y 树检查）；`DEMO-54` disabled Button 不可聚焦不可点（夹具）。 |

### 2.29 `base.menu` — 浮层菜单 / 列表框原语（Menu + Select + anchored）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.menu`。浮层菜单（Menu）与下拉列表框（Select）原语 + 共用的定位/关闭逻辑（`B/anchored.ts`）。被会话 ⋯ 菜单与设置的选择器复用。 |
| 2 | 网格与定位 | Menu `min-width:124; padding:4; 圆角12`（`B/Menu.module.css:5-17`），选项 `padding:8 10; 圆角8`（`:19-34`）；Select 触发器 36 高圆角 18（`B/Select.module.css:1-42`），下拉 `min-width:124; max-width:280; max-height:320; 圆角12`（`:62-78`），选项 `min-height:40; 圆角10`（`:80-100`）。 |
| 3 | 命中区 | 菜单项约 40 高；Select 选项 ≥40 高；触发器 36 高。 |
| 4 | 手势 | 单击选项/触发器；点外部关闭（document pointerdown）；Esc 关闭（Select 用 `captureEscape` 防连带关 Modal，`Select.tsx:38-39`）；**无键盘上下键/Enter 选择**（E0）。 |
| 5 | 行为 | 开合由触发组件本地 state；定位 `fixed` + 视口 clamp + 放不下翻面（`anchored.ts:52-79,125-157`）；内容/锚点尺寸变化与任意滚动容器滚动时重定位（ResizeObserver + scroll capture，`:166-191`）；打开时同步测量一次（`:159-164`）；选中项：关闭并 `onChange`（`Select.tsx:78-86`）。 |
| 6 | 视觉状态 | 菜单/下拉用 `--dsw-specific-menu` + 双层阴影；选项 hover 灰底；Select 选中项右侧打勾 + `aria-selected`（`Select.tsx:71-86`）。 |
| 7 | 布局占位与展开形态 | portal 到 body（`Menu.tsx:37-62`；`Select.tsx:62-90`）→ 不占位、不推挤内容（G-01 兼容）。 |
| 8 | 滚动语义 | 下拉菜单 `max-height:320` 内滚 + `overscroll-behavior:contain`（`Select.module.css:62-78`）→ 到边不传链；Menu 无 maxHeight（不内滚）。 |
| 9 | 动效 | 无进场动画；选项背景 `.2s`。 |
| 10 | 边界态 | ① 视口空间不足翻面/收缩（`maxHeight` 时可用高度 = 空间−16，最小 40，`anchored.ts:145-157`）；② 打开中滚动 → 跟随；③ 触发元素卸载 → 菜单消失；④ 点选项先关后执行（`Menu.tsx:50-53`）；⑤ `display:contents` 触发器用 firstChild 测量（`anchored.ts:115-122`）。 |
| 11 | 无障碍 | Menu `role=menu`/`role=menuitem`；Select `aria-haspopup=listbox`/`aria-expanded`/`role=listbox`/`role=option`/`aria-selected`（`Select.tsx:48-86`，**本仓库唯一 `aria-expanded` 实例**）；**无 roving tabindex/方向键/Enter 选择/焦点归还**（E0）→ 键盘可用性不完整。 |
| 12 | 依据 | **E0** `B/anchored.ts:1-258`；**E0** `B/Menu.tsx:16-65`；**E0** `B/Menu.module.css:1-47`；**E0** `B/Select.tsx:14-93`；**E0** `B/Select.module.css:1-100`。 |
| 13 | 反证 + 验收 | **反证**：若下拉用 CSS `position:absolute` 挂在触发元素里，会被设置弹窗的 `overflow-y:auto` 裁掉（这正是必须 portal + fixed 的原因）；若关合只看"点外部"，Esc 会穿透去关整个设置弹窗（Select 用 `captureEscape` 专门防这一点）。**验收**：`DEMO-55` 设置里打开语言下拉不被弹窗裁剪、Esc 只关下拉（真机）；`DEMO-56` 列表滚动时菜单跟随（真机）。 |

### 2.30 `base.modal` — 模态弹窗原语（遮罩 + Esc 栈）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.modal`。模态弹窗原语。用于删除会话确认、删除全部确认、导入确认、设置弹窗。每次打开 1 个实例；可叠加（设置 → 确认）。 |
| 2 | 网格与定位 | 遮罩 `fixed; inset:0; z-index:1000`、黑 0.36 + `blur(2px)`（深色 0.6）（`B/Modal.module.css:1-15`）；卡片宽 380、`max-width:calc(100vw−32)`、`padding:22 24`、圆角 24、阴影（`:17-34`）；header 标题 16/24 500（`:36-48`）；footer 右对齐 `gap:8; margin-top:20`（`:63-68`）。 |
| 3 | 命中区 | 关闭 X（closable 时）28×28；footer 按钮；遮罩整面可点关闭。 |
| 4 | 手势 | 单击遮罩（`onMouseDown` 且 `target===currentTarget`）、单击关闭、Esc（只关最上层）。 |
| 5 | 行为 | Esc 用模块级 `openModals` 栈，后打开的最后一个才响应（`B/Modal.tsx:9-10,36-49`）；遮罩用 `onMouseDown` 而非 click（从弹窗内拖选文字到遮罩上松手**不关闭**，`:54`）；未传 `closable` 时不显示 X（`Sider` 的删除确认、导入确认即如此）。 |
| 6 | 视觉状态 | 标准遮罩 + 卡片；深色主题遮罩更暗（`:12-14`）。 |
| 7 | 布局占位与展开形态 | **形态 C（sheet-overlay）**：portal 到 body，不占页面布局（G-01 兼容）。 |
| 8 | 滚动语义 | 长内容由调用方控制（如删除全部弹窗自身滚动，`SE/Settings.module.css:479-482`）；Modal 本身不滚。 |
| 9 | 动效 | 无（E0：无 animation/transition）。 |
| 10 | 边界态 | ① 多弹窗叠加 → Esc 只关最上；② 点遮罩关闭可被调用方拦截（如 `deletePending` 时不关，`Sider.tsx:105-109`）；③ 从弹窗内按下、遮罩上松手不关；④ **无焦点陷阱与初始焦点**（除调用方传 `autoFocus`）；⑤ 关闭后焦点不还原。 |
| 11 | 无障碍 | `role=dialog` + `aria-modal=true` + `aria-labelledby`（`Modal.tsx:56-61`）；无 `aria-describedby`；焦点不锁定（E0）→ Tab 可跑到遮罩后的页面。 |
| 12 | 依据 | **E0** `B/Modal.tsx:13-82`；**E0** `B/Modal.module.css:1-68`；**E0** `SE/Settings.module.css:479-502`。 |
| 13 | 反证 + 验收 | **反证**：若遮罩用 `click` 关闭，用户从弹窗内拖选文字、在遮罩上松手会误关（丢掉刚填的内容）；若不用栈，叠加弹窗时一次 Esc 连关两层（设置 + 确认），用户丢掉刚选的导入文件。**验收**：`DEMO-57` 遮罩上拖选松手不关闭（真机）；`DEMO-58` Esc 只关最上层（真机）。 |

### 2.31 `base.tooltip` — 悬停提示原语

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.tooltip`。悬停提示原语（图标按钮的可达名来源）。全站复用；无独立触发区。 |
| 2 | 网格与定位 | 浮层 `padding:4 8`、圆角 10、12px/1.5、`max-width:calc(100vw−28)`（`B/Tooltip.module.css:5-16`）；触发器外包 `display:contents` 不产生盒子（`:1-3`）。 |
| 3 | 命中区 | 无独立命中区（`pointer-events:none`，`:16`）；trigger 继承子元素命中区。 |
| 4 | 手势 | hover 进入延迟 **100ms** 出现、离开立即消失（`B/Tooltip.tsx:9,50-51`）；**移动端不显示**（`isMobile` 时 `visible=false`，`:24,43`）；无长按出提示。 |
| 5 | 行为 | 定位优先侧 bottom/top、放不下翻面（`anchored.ts:65-79`）；滚动时收起（`Tooltip.tsx:34-41`）；内容变化同步重测（`measureKey`，`:28`）。 |
| 6 | 视觉状态 | 深底浅字、圆角 10、12px（`Tooltip.module.css:5-16`）；深色主题改主字色 + 阴影（`:18-24`）。 |
| 7 | 布局占位与展开形态 | portal + fixed → 不占位（G-01 兼容）。 |
| 8 | 滚动语义 | 跟随重定位（`anchored.ts:184-190`）但**任何滚动都会先收起**（`Tooltip.tsx:38-41`）。 |
| 9 | 动效 | 无进场动画（只有延迟出现）。 |
| 10 | 边界态 | ① 移动端完全不出提示 → 图标按钮在移动端只剩 aria-label（读屏可用，视觉用户靠图标识别）；② `content` 为空 → 不显示；③ 滚动即消失；④ 长文案按 max-width 换行。 |
| 11 | 无障碍 | `role="tooltip"`（`Tooltip.tsx:58`）但**未与触发器 `aria-describedby` 关联**（E0）；IconButton 把 tooltip 字符串同时用作 aria-label（`IconButton.tsx:29`）→ 可达名成立。 |
| 12 | 依据 | **E0** `B/Tooltip.tsx:9-70`；**E0** `B/Tooltip.module.css:1-25`；**E0** `B/anchored.ts:85-198,243-258`；**E0** `B/IconButton.tsx:26-38`。 |
| 13 | 反证 + 验收 | **反证**：若移动端也弹 tooltip，点按钮会先弹提示再执行（或需要两次点击）；若提示不做延迟（0ms），鼠标划过一排图标会连续闪出提示，噪声大。**验收**：`DEMO-59` 移动端不出现 tooltip（真机）；`DEMO-60` hover 100ms 后出现、离开立即消失（录屏）。 |

---

## 3. 与 DSH 的差异对照

> DSH 侧证据全部来自本仓库已完成的 P1 审计 `docs/spec/ref/dsh-chat.md`（§0 十条 + §2 节点表，E0/E1 已就地标注）；chat-demo 侧证据为本文件 §2。
> "谁更适合 myco"是**取舍建议**（P2 入口），不是拍板结论。

| # | 能力 | chat-demo 做法（证据） | DSH 做法（证据） | 谁更适合 myco | 理由 |
|---|---|---|---|---|---|
| 1 | 回复生命周期呈现 | 单个 5 值 `ReplyStatus` + 三处派生：轮内三点 / 过程标题 / inline notice（本文件 2.10；`svc/chat/types.ts:70-74`） | **不存在统一四态枚举**，三层并行：会话运行指示条 / 组标题 live 文案 / 轮尾控制条（`dsh-chat.md` §0#10） | **chat-demo** | myco 是单用户问答，不需要"组/过程控制"的抽象层；5 值状态足以驱动全部可见态，且实现可测。 |
| 2 | 首 token 前空窗 | 34px 三点脉冲，`waiting` 为互斥渲染（`M/AssistantMessage.tsx:57,79-85`；css `:14-47`） | 无轮内占位；靠会话级运行指示条（§0#10） | **两者组合** | 轮内三点回答"这条在等"，会话级指示回答"整个会话在跑"；myco 若不做 agent 过程，三点足够。 |
| 3 | 思考/过程区形态 | 单条可折叠时间线，默认展开，形态 A 不封顶、无 sticky（2.11；`M/ReplyArea.module.css` 全无 sticky/contain/max-height） | 思考行（每 reasoning block 一行，形态 A + sticky 头）+ 过程组（形态 B，`max-height:min(400px,50vh)`，§0#1、schema-node 样本） | **DSH** | chat-demo 的过程短所以没暴露问题；myco 若接工具/长思考，必须用 A/B 组合并补 sticky，否则长过程读完无法收起（G-06）。 |
| 4 | 过程区折叠入口 | `div` + onClick，无 role/tabindex/aria-expanded（2.11 字段 11；`M/ReplyArea.tsx:81`） | `DisclosureRow` + `aria-expanded`（schema-node 字段 11） | **DSH** | 键盘与读屏可达性是硬要求；chat-demo 在此是明显欠账。 |
| 5 | 滚动跟随阈值 | 距底 <40px 即跟，只有 `scrollTop` 变小才停（2.16；`M/MessageList.tsx:33,128-136`） | 距底 ≤25px + 500ms 采样 + 语义锚点（§0#5） | **DSH**（更精确） | 40px 会让"想停下来读、但仍在 40px 内"的用户被继续拉底；25px 更贴近"在底部"的直觉，且 DSH 有采样避免抖动。 |
| 6 | 回到底部 | **未见实现**（全仓 grep 0） | 34×34 悬浮圆钮，**仅非跟随时渲染**，右下角 16px（§0#6） | **DSH** | 长会话上翻后恢复跟随的成本极高；chat-demo 这是功能缺口，myco 已有 v 钮的方向是对的（G-01 不占位）。 |
| 7 | 轮次导航/大纲 | **未见实现** | 右侧 28px 竖条、10px 定距刻度、虚拟滚动、≤900px 隐藏（§0#6） | **DSH（桌面）** | 移动端两者都没有；myco 移动优先可缓，桌面长会话需要。 |
| 8 | 输入区自增高 | mirror 撑高（零 JS 测量）+ 336px 上限 + Enter 发送/Shift+Enter 换行；**无字符上限**（2.1；`CI/ChatInput.module.css:171-220`） | DSH composer 细节本路未取证（转交输入区一路） | **chat-demo（可直接抄实现）** | mirror 方案在 Web 端零测量成本、无跳动；上限与快捷键语义可整体复用。 |
| 9 | 发送 / 停止 | 同位置互斥，34px 圆钮，`aria-disabled` + 内部守卫（2.2；`CI/ChatInput.tsx:263-285`） | 未取证 | **chat-demo** | 同位置换图标是产品级最小方案；注意"aria-disabled + 自守卫"与原生 disabled 的取舍（2.2 反证）。 |
| 10 | 消息操作条显隐 | `opacity:0` + hover/focus-within，最后一条常显；**触摸无兜底**（2.8；`M/AssistantMessage.module.css:95-105`，无 `hover:none`） | `@media(hover:hover)` 才隐藏，触摸设备常显；28×28 图标（§0#7） | **DSH** | 触摸可达性是硬伤：chat-demo 非最后一条消息在手机上无法复制/重生成（Q-DEMO-01）。 |
| 11 | 复制 | `navigator.clipboard` + `execCommand` 兜底；换勾 1000ms；失败 toast（2.8；`src/utils/clipboard.ts:1-28`） | `writeClipboard`，成功换勾 1000ms，期间吞重复（§0#7） | **chat-demo（多兜底）** | 行为等价；chat-demo 额外覆盖"局域网 HTTP 非安全上下文"这一真实场景。 |
| 12 | 重新生成 | 同问题下**新增版本**，旧回答可切换（2.9；`store.ts:628-651`） | 分支 = 新会话（§0#7 MessageIconActions 分支） | **chat-demo** | 陪伴场景"重说一次"比"开分支会话"自然；DSH 分词为 agent 实验服务。 |
| 13 | 编辑后重发 | 新增**同父用户版本** + 新回答，保留附件，旧线可切回（2.7；`store.ts:654-684`） | 本路未取证 | **chat-demo** | 编辑成版本而非原地改，历史可回、可对比。 |
| 14 | 版本切换 UI | `‹ N/M ›` 计数 + 锚点保持（2.9；`M/MessageList.tsx:104-115`） | 无对应（分支在新会话） | **chat-demo** | 就地切换避免"分支会话"这个重概念。 |
| 15 | 会话管理 | 侧栏单列表 + 今天/昨天/7/30 天/按月分组 + 内联重命名 + 删除确认；**无搜索**（§1；`sessionGroups.ts:9-33`） | sidebar 面板（`dsh-sidebar.md`，本路未审） | **待 P2（DSH sidebar 一路）** | 时间分组文案可直接用；会话搜索 chat-demo 缺，是否要由 DSH/产品侧定。 |
| 16 | 异常提示 | inline 红色 notice + 分类文案 + 动作链接（更换 Key / 充值）+ toast 兜底（2.10；`describe.ts:19-59`） | `turn-error` 独立节点 + 模板正文（§0#10） | **chat-demo（更轻）** | 单用户问答不需要"错误节点"抽象；代价是 inline 错误会被后续消息推走。 |
| 17 | 限流/配额 | 429/402/上传配额各有文案，配额走 toast + 文档链接（§1#9；`svc/api/errors.ts:17-34`；`svc/attachments.ts:93-103`） | 本路未取证 | **chat-demo** | 产品级必备；尤其 402 的"前往充值"动作。 |
| 18 | 加载骨架屏 | **未见实现**（文字 + 重试，2.17） | 本路未取证 | **chat-demo 可接受** | 本地 IndexedDB 读取快，骨架收益低；若 myco 首屏有网络/DB 迁移，则需补。 |
| 19 | 移动端适配 | 768 断点 + visualViewport 键盘跟随 + 独立滚动层 `overscroll-behavior:contain` + 44px 触控 + 300px 抽屉（§1#10；`APP/viewport.ts:3-4,45-108`） | 本路未取证（DSH Web 是否移动适配未审） | **chat-demo（已实现）** | 官方 demo 做了真适配：输入框聚焦时跟随可见视口（iOS Safari 关键）、软键盘不遮输入。 |
| 20 | 无障碍 | aria-pressed / aria-expanded(仅 Select) / role=dialog / role=menu；缺过程标题 aria-expanded、行 aria-current、tooltip 关联、焦点陷阱、流式 aria-live（2.11/2.18/2.22/2.30 字段 11） | 有运行态视觉隐藏文案（G-08）与 DisclosureRow 语义 | **DSH** | chat-demo 的无障碍是"按需补"水平，不满足 G-08 级要求；myco 可借 DSH 的 a11y 范式。 |
| 21 | 引用与来源 | `[citation:N]` 角标 + hover 300ms 来源卡 + 右侧分栏来源面板（2.14/2.15） | 本路未取证 | **chat-demo** | 智能搜索闭环完整：角标 → 预览 → 面板 → 外链；分栏挤压正文的代价需 P2 决定。 |
| 22 | 附件 / 语音 / 朗读 | 上传（拖拽/粘贴/3 并发/64MB/50 个）+ ASR oneshot（120s）+ 移动端频谱 + TTS 边收边播（2.4/2.5/2.8） | 本路未取证 | **chat-demo** | 若 myco 做语音陪伴，这是现成参考；但都依赖服务端 ASR/TTS 接口。 |
| 23 | 流式渲染节流 | 按渲染耗时自适应节流（上次耗时×2，上限 2s）+ AST 变化后贴底（2.12；`MD/useThrottleMarkdownRender.ts`） | 本路未取证 | **chat-demo** | 长回答流式不掉帧的关键；myco 若自研 Markdown 渲染应直接复用该策略。 |

**一句话总结**：chat-demo 更适合 myco 的部分是**会话/消息层的产品语义**（生命周期呈现、重生成=新版本、编辑=新版本、版本就地切换、复制兜底、异常文案与动作、引用闭环、附件/语音/移动端适配）；
DSH 更适合 myco 的部分是**过程/滚动/无障碍层的工程约束**（过程区 A/B 形态与 sticky、回到底部、轮次导航、25px 跟随、触摸端操作条常显、a11y 语义）。

---

## 4. 排除清单

### 4.1 分母对账

| 项 | 条数 | 说明 |
|---|---|---|
| 扫描命中（findings） | **226** | `_ws/out/findings-chat-demo.csv`；覆盖 `src/` 内 58 个文件 |
| 已取证进节点（E0） | **123** | 落在 31 个节点上，全部在 `facts.csv` 有 `node_id` |
| 显式排除（excluded） | **103** | 见 4.2 / 4.3；`node_id` 一律 `-` |
| 未覆盖（uncovered） | **0** | 门禁 `check ... --fail-on-uncovered` 退出码 0 |
| 失效条目（stale） | **0** | 事实库与扫描 1:1，无多余行 |
| 非 UI / 测试 / 构建产物命中 | **0** | `src/services/`、`src/utils/`、`src/i18n/` 全部 0 命中；无测试目录；`*.module.css.d.ts` 为生成文件 0 命中 |

### 4.2 结构性 CSS（92 条，逐类理由）

| kind | 条数 | 典型证据 | 排除理由 |
|---|---|---|---|
| `css.overflow` | 38 | `M/MessageList.module.css:7`（scrollbar-gutter）、`M/ReplyArea.module.css` 无 max-height（反证形态 A）、`B/Select.module.css:68`（overscroll contain） | 结构性属性，为节点字段 2/7/8 提供视觉证据，本身不构成独立交互点 |
| `css.cursor` | 36 | `CI/ChatInput.module.css:106`（上传按钮 cursor:pointer）、`M/UserMessage.module.css` 等 | 同上；光标语义是命中的副产品，命中区已在对应节点的字段 3 取证 |
| `css.focus` | 16 | `B/IconButton.module.css:25`、`B/Button.module.css:22`、`S/Sider.module.css:146` | 焦点环是视觉状态（字段 6），不单独成交互点 |
| `css.sticky` | 2 | `SS/SessionView.module.css:33`（输入区吸底）、`S/Sider.module.css:108`（分组头吸附） | 属布局证据，写入对应节点字段 2/7；不单独成点 |

> 注：`css.sticky` 只有 2 条命中，而 §2 判定"过程区是形态 A（无 sticky）"的依据是**整份 `ReplyArea.module.css` 通读无 sticky**，不是"扫描 0 命中"——扫描只覆盖模式匹配，不作为否定证据。

### 4.3 扫描误报与清理行（11 条）

| 位置 | 条数 | 真实理由 |
|---|---|---|
| `CI/DragMask.tsx:10,14,15` | 3 | 扫描正则命中 `onDrop` 的 JSDoc / ref 赋值；真实拖放监听在 `:16-55` 的 `document` 事件，已在 2.4 取证 |
| `CI/VoiceButton.tsx:18,25,54` | 3 | 命中 props 解构名 / 类型声明 / 内部调用行；真实绑定在 `:52`，已取证 |
| `B/anchored.ts:237,238`；`B/Modal.tsx:47`；`B/Tooltip.tsx:40`；`M/MessageList.tsx:139` | 5 | 同一交互的 `removeEventListener` 清理行，非新增交互点（重复） |

### 4.4 未见实现清单（代码里没有，不是"排除"，是缺口）

| 能力 | 状态 | 依据 |
|---|---|---|
| 回到底部按钮 / 未读标记 | 未见实现 | 全仓 grep `toBottom`/`scrollToBottom` 仅内部函数名，无 UI 按钮 |
| 轮次导航 / 大纲 | 未见实现 | 无 rail/outline/navigator 组件 |
| 会话搜索 | 未见实现 | `S/`、`SS/` 目录 grep `search` 0 命中 |
| 加载骨架屏 | 未见实现 | grep `skeleton` 仅 2 处 CSS 变量 |
| 消息删除 / 引用回复 / 分享单条 | 未见实现 | 消息操作条只有复制/朗读/重生成（2.8） |
| token 用量 / 费用显示 | 未见实现 | `MessageUsage` 只存不展示 |
| 模型选择 / 参数设置 | 未见实现 | 模型常量写死 `deepseek-flash`（`store.ts:42-43`） |
| 单会话导出 | 未见实现 | 仅全量 ZIP（2.27） |
| 键盘快捷键（Cmd+K / Ctrl+Enter 等） | 未见实现 | 全仓仅 Enter/Esc（输入框/重命名/弹窗） |
| 触摸手势（左右滑切版本、下拉刷新、滑动删除） | 未见实现 | 无 touch/pointer 手势处理 |
| 长按菜单 | 未见实现 | 无 `onContextMenu`/长按计时 |
| 过程区标题 aria-expanded / 会话行 aria-current | 未见实现 | 2.11 / 2.18 字段 11 |
| 焦点陷阱 / 焦点归还（Modal、抽屉、菜单） | 未见实现 | 2.22 / 2.29 / 2.30 字段 11 |
| 流式内容 aria-live 播报 | 未见实现 | 全仓 aria-live 仅 2 处（加载态、toast 无关） |

---

## 5. 待确认（E2 候选）

> 以下条目**不得**直接进 myco 实现；每条形如「问题 + 已知证据 + 可选方向」，需用户拍板后改写为 E0/E1 进 spec。

**Q-DEMO-01｜触摸端的消息操作条该常驻、长按还是保持不可达？**
- 已知（E0）：`M/AssistantMessage.module.css:95-105` 与 `M/UserMessage.module.css:57-67` 只有 `opacity:0` + hover/focus-within，**无 `@media(hover:none)` 分支**；对照 `S/SessionItem.module.css:62-66` 会话行有兜底。
- 结果：移动端只有最后一条消息能复制/重生成。chat-demo 是 demo 可接受，myco 不可。
- 方向：A 触摸端全常驻（DSH §0#7 做法）；B 长按出操作条；C 点按消息出操作条。需拍板。

**Q-DEMO-02｜过程区（思考/工具）展开后头部是否必须 sticky？**
- 已知（E0）：chat-demo 形态 A 无 sticky（`M/ReplyArea.module.css` 通读）；DSH 思考行 sticky（schema-node 样本 `ReasoningRow.module.css:15-22`），G-06 已定"折叠入口永不可达 → 必须 sticky"。
- 矛盾：chat-demo 是"产品级 demo"却也没做 sticky，说明短过程下可接受；G-06 是否对所有过程区（含工具组）都强制？
- 方向：按内容高度分档（≤某高度不 sticky，超过则 sticky），阈值需拍板（与 G-10 的 240dp 同族问题）。

**Q-DEMO-03｜滚动跟随阈值取 40px（chat-demo）还是 25px（DSH）？**
- 已知（E0）：chat-demo `STICKY_THRESHOLD=40`（`M/MessageList.tsx:33`），且无 500ms 采样、只有"上翻才停"；DSH 25px + 采样（dsh-chat.md §0#5）。
- 影响：40px 在长回答下会让用户"想停但还在 40 内"时被继续拉底；25px 更容易在快速滚动中误判为"不在底部"。
- 方向：25px 作为判据 + 只在上翻时停（两者取长）；需实测确认。

**Q-DEMO-04｜输入框最大高度 336px 能否作为 myco 上限？**
- 已知（E0）：`CI/ChatInput.module.css:216-220`（mirror max-height 336px）；移动端另有 `100dvh−120px` 限制（`:222-236`）。
- 疑问：336px 对应 24px 行高约 12 行；是否按屏高百分比（如 40dvh）更稳？
- 方向：桌面 336px / 移动端 min(336, 40dvh)；需拍板（E1，可调）。

**Q-DEMO-05｜"发送后到首 token"的空窗用三点、骨架屏还是运行文案？**
- 已知（E0）：chat-demo 用 34px 三点（2.10）；DSH 无轮内占位、用会话级运行指示条（§0#10）；myco 旧实现未定。
- 方向：陪伴场景三点最轻；若首 token 中位数 >3s，可补一行"正在连接…"文案；需真机时延实测后定。

**Q-DEMO-06｜复制成功反馈统一成"图标换勾 1s"还是"图标+文字 1.5s"？**
- 已知（E0）：消息操作条 1000ms 纯图标（`M/CopyButton.tsx:29`）；代码块 1500ms 图标+「已复制」（`MD/CodeBlock.tsx:61-67`）。
- 影响：纯图标对读屏不友好（无文字变化）；图标+文字占宽。
- 方向：图标+文字 + `aria-live` 播报，时长 1.2s 折中；需拍板。

**Q-DEMO-07｜助手错误用 inline 错误行还是独立错误节点？**
- 已知（E0）：chat-demo inline 红字 + 动作链接（2.10，`M/AssistantMessage.tsx:86-126`）；DSH `turn-error` 是独立节点（§0#8 根集合），可被轮次导航定位。
- 影响：inline 错误在长会话里会被后续消息推走，用户回看找不到原因；独立节点需要轮内顺序模型。
- 方向：myco 若不做轮次导航，inline + 常驻错误清单（设置页"最近错误"）更省；需拍板。

**Q-DEMO-08｜输入是否需要字符/token 上限与计数？**
- 已知（E0）：chat-demo **无上限**（全仓 `maxLength` 0 命中），超长由服务端 400 兜底（`svc/api/client.ts:70-84` 直接抛服务端原文）。
- 疑问：myco 是否给"超长"前置提示（如 32k token 预估）？
- 方向：仅当服务端有硬限制时加；否则不加（避免自负的计数误差）。

**Q-DEMO-09｜侧栏收起后是否保留设置入口？**
- 已知（E0）：`S/CollapsedSider.tsx:9-30` 收起态只有"展开/新对话"两个按钮，设置入口消失（`S/Sider.tsx:94-101` 仅展开态）。
- 影响：首次使用必须填 API Key（`settingsDialogState.ts:19-25` 会自动弹设置，所以仅"想再改 Key"时受影响）。
- 方向：收起态加第三个齿轮钮（三钮浮组宽度仍可接受）或从欢迎页提供入口；需拍板。

**Q-DEMO-10｜引用角标的悬停卡在滚动时是否应关闭？**
- 已知（E0）：`HoverCard` 滚动不关闭、只重定位（`B/anchored.ts:184-190`）；`Tooltip` 滚动即关（`B/Tooltip.tsx:38-41`）——同仓两种口径。
- 影响：长来源卡在滚动中悬停会遮住正文且不消失。
- 方向：统一为"滚动即关"（Tooltip 口径）+ 移动端点击开面板；需拍板。

---

### 附：本路与其他 ref 的分工

- 与 `docs/spec/ref/dsh-chat.md`：**不重复取证 DSH**；§3 只引用其 §0/§2 的既有 E0/E1。
- 与 `docs/spec/ref/dsh-sidebar.md`：会话列表的 DSH 口径以 sidebar 一路为准；本文件 2.18–2.21 只描述 chat-demo 自身实现。
- 与 `docs/spec/ref/dsh-settings.md`：设置页控件以 settings 一路为准；本文件 2.24–2.27 只描述 chat-demo 自身实现。
- 本文件新增的通用原语（2.28–2.31）供 `base.primitives` 面板在 P2 对照时引用，不单独立面板。

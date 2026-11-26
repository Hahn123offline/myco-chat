# DSH（DeepSeek Harness）多步 Agent 过程的前端实现调研

> 调研对象：本机安装版 **@deepseek-ai/dsh-desktop 0.2.2-rc.5**（`app.asar`，2026-11-18）。
> 只读操作：未运行、未修改 DSH；本报告也只在 `E:\myco-chat` 下写这一个文件。
> 结论以**安装版 asar 内自带的前端源码**为准；`E:\deepseek-harness` 的 git checkout 是 **0.2.1-alpha.2**，有漂移，仅用于补齐 asar 里没有单独发包的 `ui-primitives`。

---

## 0. 一句话结论（TL;DR）

DSH 的会话视图不是"一条消息一个气泡"，而是 **Turn → ProcessGroup（二级组）→ 行（Node）** 三层结构，三层各有独立的折叠开关：

1. **行按会话事件顺序投影**成节点（`assistant-step` / `tool-call` / `turn-process` / `turn-tail` …），节点再被分组器按边界切成 `ProcessGroup`；同一 Turn 内不因 step 变化而断组，但**一次中间回复（reply）会断开组**。
2. **思考行**不是独立消息：它是 `assistant-step` 里 `reasoning` block 被单独拆成的一行（`groupPart: 'reasoning'`），和同一条回复（`groupPart: 'response'`）共享同一个 Node。
3. **工具行**是 `tool-call` Node，按 `callId` 走 `preparing → start → result` 生命周期，参数是流式 `PartialArguments` 延迟解析，结果按工具类型渲染成终端 / diff / read / search / web / image / details 卡片。
4. 整轮完成后：**整轮 process 折叠成一行 Turn 控件**（"已完成，用时 12秒"，16px 间距），二级组折叠成一行组头（"已读取文件并执行了命令"）；默认折叠时机是 completion（还有 next-input 延迟折叠）。
5. **流式**：每个 Node 的数据对象被整体替换（同 key，不 remount），高频事件合并到**连续 3 个 rAF** 才发布一次；Markdown 用"冻结前缀 + 只重解析最后 2 个 block"的增量渲染。
6. **自动滚动**是独立状态机 `ScrollFollow`：跟底阈值 **25px**、滚动采样 **500ms**、用户一有阅读意图就停止跟随、回到底部按钮出现；内容增长不靠 scroll 事件而靠 **ResizeObserver** 触发跟底。
7. **不虚拟化**主 transcript（只有右侧 Turn 导航栏用 `@tanstack/react-virtual`）；**没有**行内 citation 角标——搜索来源只出现在 `web_search` 工具卡展开后的有序来源列表里。
8. 折叠 / 流式增长 / 历史前插的"不跳"靠四件事：同一帧时钟驱动的折叠批次（同时改行高、margin、scrollTop、底部 spacer）、折叠前测量 + `keepAboveClosing` 补偿、语义 scroll anchor（`data-chat-anchor-key`）、跟底时 `overflow-anchor: none`。

---

## 1. 证据来源与版本对应

### 1.1 app.asar 解析方法（可复现）

```python
# 已在本机验证：209,083,008 字节的 app.asar
import json, struct
ASAR = r"C:\Users\11096\AppData\Local\Programs\DeepSeek Harness\resources\app.asar"
with open(ASAR, "rb") as f:
    head = f.read(16)
size0, header_size, json_len, _ = struct.unpack("<IIII", head)
# size0 = 4
# header_size = 5,671,400   （= 8 + align4(5,671,389)）
# json_len    = 5,671,389   （JSON header 真实长度）
base = 8 + header_size                     # = 5,671,408  payload 起点，与实测一致
with open(ASAR, "rb") as f:
    f.seek(16)
    header = json.loads(f.read(json_len).decode("utf-8"))

def walk(node, prefix=""):
    for name, meta in node.get("files", {}).items():
        p = f"{prefix}/{name}" if prefix else name
        if "files" in meta: yield from walk(meta, p)
        else:               yield p, meta

files = dict(walk(header))                 # 21,722 个文件

def read(entry_path: str) -> bytes:        # 文件内容 = payload[base + offset, size]
    m = files[entry_path]
    with open(ASAR, "rb") as f:
        f.seek(base + int(m["offset"]))
        return f.read(int(m["size"]))
```

- 解析结果：**21,722** 个文件；顶层分布 `dsh/`（20,564）、`node_modules/`（1,128）、`lib/`（15）、`renderer/`（14）、`package.json`（1）。
- `read("package.json")` → `{"name":"@deepseek-ai/dsh-desktop","version":"0.2.2-rc.5"}`。
- `app.asar.unpacked/dsh/node_modules` 只有原生/二进制依赖（`@deepseek-ai/libreoffice-kit`、`@img`、`@swc`、`koffi`、`node-pty`、`sherpa-onnx`…，共 867 个文件），**UI 源码全部在 asar 内**，不需要额外解包。

### 1.2 前端代码的真正位置

| 层 | 路径（asar 内） | 说明 |
|---|---|---|
| Electron 外壳 | `renderer/*.html/js/css` | 欢迎页、更新弹窗、主题；不含会话 UI |
| Web 前端入口 bundle | `dsh/node_modules/@deepseek-ai/dsh-web-frontend/dist/assets/index-Ciz7DZxv.js`（1,093,870 B）、`index-CSAdOBx6.css`（92,252 B）、`vendor-BUMdD3V7.js` | 壳 + 被内联的 `ui-primitives`，已压缩 |
| **客户端 UI 插件包（自带 TS/TSX 源码）** | `dsh/node_modules/@deepseek-ai/dsh-client-ui-chat/`（141 文件）、`-ui-conversation`（110）、`-ui-tool`（66）、`-ui-trajectory`（57）、`-ui-renderer`、`-ui-session`、`-ui-layout` … | **本次调研的主要依据**；每个包同时有 `src/` 和构建产物 `lib/client.js` |
| 会话/工具宿主包 | `dsh/node_modules/@deepseek-ai/dsh-session*`、`dsh-llm*`、`dsh-tool-*` | 事件类型、`StreamChunk` 协议 |

已验证源码与构建产物一致：`dsh-client-ui-chat/lib/client.js` 中 `assistant/live-chunk` 出现 20 次、`tool-call-delta` 5 次、`reasoning-delta` 3 次、`data-chat-following-tail` 3 次、`--dsh-chat-flow-gap` 8 次。

### 1.3 与 `E:\deepseek-harness` checkout 的差异（重要）

| 项 | 值 |
|---|---|
| checkout 版本 | `0.2.1-alpha.2`，HEAD `d743267388`（2026-10-09） |
| 安装版 | `0.2.2-rc.5`（asar 2026-11-18） |
| 同包 `src/*.{ts,tsx,css}` 逐文件 md5 对比（ui-chat/ui-tool/ui-conversation/ui-renderer/ui-session/ui-layout） | **114 相同 / 144 不同 / 30 仅存在于安装版** |
| 例：`ChatFlow.tsx`、`ChatView.tsx`、`use-chat-scroll.ts`、`use-chat-viewport.ts`、`process-groups.ts`、`ReasoningRow.tsx` | 不同 |
| 例：`use-scroll-follow.ts`、`ui-tool/.../ToolRow.tsx` | 相同 |

→ 所以：**正文以 asar 抽取源为准**（下称 `asar-src`，路径写成 `dsh-client-ui-chat/src/...`）。
→ 例外：`@deepseek-ai/dsh-client-ui-primitives` **在 asar 里没有单独发包**（被内联进 web bundle），`MarkdownText` / `DisclosureRow` / `WebBlock` / `TextShimmer` 的源码只能读 checkout 0.2.1（下文会标注 `[checkout-0.2.1]`，并注明可能的漂移）。

### 1.4 可执行规格（供后续核对）

checkout `apps/web/tests/` 里有大量行为契约（0.2.1 版，但机制描述一致度很高，可当"验收清单"）：
`chat-scroll-contract.e2e.ts`（1611 行滚动契约：锚点不动、跟底、回到底部、历史与流式并发）、`chat-flow-layout.e2e.ts`（process 与 answer 的 16px/12px 间距）、`collapse-timing.e2e.ts`、`reasoning-preview.e2e.ts`、`thinking-markdown.e2e.ts`、`streaming-fence-highlight.e2e.ts`、`tool-details.e2e.ts`、`web-search-round.e2e.ts`、`trajectory-virtualization.e2e.ts`；`tests/expected/*/*.expected.md` 是无障碍树快照（例：`reasoning-preview/running.expected.md` 里能直接看到折叠思考行的可访问名 `button "Think  <首行预览>"`）。

---

## 2. 数据流：会话事件 → Node → Group → 行

### 2.1 三层投影

```
SessionEventWindow (revision 严格 +1；kind = append | prepend | settle-assistant | replace)
        │  每帧最多发布一次（高频流式合并 3 个 rAF）
        ▼
ConversationAssembler  ── 每个 NodeDefinition: match / start / update / publication / buildViewNode
        │                    (assistant.ts, tool.ts, turn-process.ts, turn-tail.ts, message.ts ...)
        ▼
ChatSnapshot { nodes: Map<key, ChatNode>, order, navigation, timeline }
        │  Chat 专属分组定义 processGroupDefinition.update() → ProcessState.accept()
        ▼
RenderEntry[]  =  [ {kind:'node', key, groupPart?} | {kind:'group', key} ]   ← 分组结果（顺序即渲染顺序）
        ▼
ChatView → conversation.chat.flow 槽 → ChatFlow → ChatGroupSeat / ChatNodeSeat → conversation.chat.node 槽 → 行组件
```

关键源码：
- `dsh-client-ui-chat/src/client/conversation-nodes/{assistant,tool,turn-process,turn-tail,message,process-activity,process-groups}.ts`
- `dsh-client-ui-conversation/src/client/conversation/{assembler,assembly,group-store,location-index}.ts`
- `dsh-client-ui-chat/src/client/contract/{chat-nodes,snapshot,slots,process-groups,turn-process}.ts`

### 2.2 发布节流：高频流式事件合并到 3 个 rAF

`dsh-client-ui-conversation/src/client/conversation/assembly.ts:141-158`：

```ts
private publish(publication: ConversationPublication): void {
  if (publication === 'none') return
  if (publication === 'animation-frame' && typeof requestAnimationFrame === 'function') {
    if (this.frame !== undefined) return
    // Cross three paint opportunities before publishing high-frequency stream updates.
    this.frame = requestAnimationFrame(() => {
      this.frame = requestAnimationFrame(() => {
        this.frame = requestAnimationFrame(() => {
          this.frame = undefined
          this.flush()
        })
      })
    })
    return
  }
  this.cancelFrame()
  this.flush()
}
```

- 多个事件在一次 append 窗口里按 `PUBLICATION_RANK`（`none=0 / animation-frame=1 / immediate=2`）取最高级（`assembler.ts:90-107`）。
- 谁算 `animation-frame`：`assistant/live-chunk`（除 `usage`/`finish`）和 `tool-call` 的 live chunk；`tool/result`、`assistant/message`、`step/*`、`turn/end` 等是 `immediate`（`assistant.ts:332-337`、`tool.ts:331`、`turn-process.ts:280-286`）。
- 这解释了 DSH 的"打字机不卡"：同一 Node 的多个 delta 在一次发布里合并成一次 React 渲染。

### 2.3 节点身份：流式期间 key 不变

- Assistant 节点在 `step/start` 时创建，id=`${turn}:${step}`（`assistant.ts:303-321`）；之后所有 `assistant/live-chunk` 都是 `role:'update'`。
- Tool 节点在第一个 `tool-call-delta`（或 `block-end`/`tool/call`）时创建，id=`String(callId)`（`tool.ts:292-319`）。
- Turn 控件节点 id=`String(turn)`，在 `turn/start` 创建（`turn-process.ts:249-278`）。

→ **同一个逻辑行在流式全程是同一个 React key**，内容只是数据对象替换。

---

## 3. DOM / 组件层级（可直接照搬的结构）

### 3.1 整体结构

`dsh-client-ui-chat/src/client/chat/ChatView.tsx:231-323`：

```
div.frame
├─ TurnNavigator                     // 右侧 Turn 导航栏（唯一虚拟化区域）
└─ div.root[data-chat-following-tail]         // 跟底时存在该属性 → 关掉原生 scroll anchoring
   ├─ div.scroll  (ref=listRef)                // 滚动容器（若外层有 [data-conversation-scroll] 则用外层滚动）
   │  ├─ [openState=loading/error 提示]
   │  ├─ div.older[data-chat-history]          // "加载更早"按钮（historyEdgeRef）
   │  ├─ MarkdownDelegateProvider
   │  │  └─ slot conversation.chat.flow → ChatFlow
   │  │     ├─ ChatGroupSeat …（组）
   │  │     ├─ ChatNodeSeat  …（独立行：用户消息 / turn-process / turn-tail / reply …）
   │  │     ├─ PendingSubmissionBubble / PendingSteeringBubble（本地回显）
   │  │     └─ RunningStatus（跟底"深度求索中，用时 …"）
   │  └─ div.textInteraction[data-text-interaction-surface]
   └─ div.turnSpacer[data-chat-turn-spacer]    // 折叠预留的底部空间，height 由视口 owner 设置
└─ (跟随关闭时) div.toBottomSlot > button.toBottom   // 回到底部（34×34 圆形浮钮）
```

`div.column[data-chat-flow][data-chat-motion]` 是行容器：`max-width: var(--dsh-chat-content-width); margin: 0 auto`（`ChatView.module.css:69-76`）。

### 3.2 行节点 `ChatNodeSeat`

`dsh-client-ui-chat/src/client/chat/ChatNodeSeat.tsx:152-177`：

```tsx
<div
  ref={wrapperRef}
  className={css.flowItem}
  data-chat-anchor-key={flowKey}          // 语义锚点 key（"node:…"/"group:…"/"call:…"）
  data-chat-flow-key={flowKey}
  data-chat-paging-anchor={routedNode.kind !== 'turn-process' || undefined}
  data-chat-node-key={routedNode.key}
  data-chat-group-part={groupPart}        // 'reasoning' | 'response' | undefined
  data-chat-flow-kind={routedNode.kind}   // assistant-step / tool-call / turn-process / turn-tail / user …
  data-chat-turn={turn}
  data-turn-process-member={processMember || undefined}
  data-turn-process-hidden={processHidden || undefined}
>
  {renderSlot('conversation.chat.node', routedOwner, { entryKey: routedNode.kind, fallback: <JsonBlock/> })}
</div>
```

其中 `flowKey = groupPart === undefined || groupPart === 'response' ? node.key : JSON.stringify([node.key, groupPart])`（同文件 151 行）——**同一个 assistant Node 的 reasoning 行和 response 行有不同 key，是两行**。

### 3.3 组节点 `ChatGroupSeat`

`dsh-client-ui-chat/src/client/chat/ChatGroupSeat.tsx:190-207`：

```tsx
<div ref={rootRef} className={css.root}
  data-chat-group-key={groupKey}
  data-chat-flow-key={groupKey}
  data-chat-anchor-key={`group:${groupKey}`}
  data-chat-turn={turn}
  data-chat-paging-anchor={grouped && !open || undefined}     // 折叠的组以组头为分页锚点
  data-step-process
  data-group-expanded-mode={!grouped || undefined}>
  <div ref={headerRef}>
    <ProcessGroupHeader … />          // <button class=title aria-expanded aria-controls data-process-activity>
  </div>
  <div ref={bodyRef} id={bodyId} className={bodyClasses} data-step-process-body
       data-scroll-up data-scroll-down {...events}>
    <div ref={contentRef} className={css.content} data-step-process-content data-chat-flow="">
      <GroupMembers … />              // members.map(m => <ChatNodeSeat key=chatRenderKey(m) …/>)
    </div>
  </div>
</div>
```

组头按钮结构（`ChatGroupSeat.tsx:116-129`）：`[icon | chevron] + TextShimmer(label + ' · ' + detail)`；hover / focus / 展开时图标换成 chevron（前两者用 CSS `opacity` 过渡）；展开时组头 `padding-bottom: 8px`。

### 3.4 行身份 key：`chatRenderKey`

`dsh-client-ui-chat/src/client/chat/render-entry.ts:10-16`：

```ts
export function chatRenderKey(entry: RenderEntry): string {
  switch (entry.kind) {
    case 'node':  return JSON.stringify(['node', entry.key, entry.groupPart ?? null])
    case 'group': return JSON.stringify(['group', entry.key])
  }
}
```

→ 行是否"重挂载"完全由这个 key 决定；`groupPart` 变化（reasoning ↔ response）会换 key，而分组导致的父子变化也会换 React 树位置。

### 3.5 关键 CSS 数值（`ChatView.module.css` / `ChatGroupSeat.module.css`）

| 项 | 值 | 出处 |
|---|---|---|
| 滚动容器 padding | `16px calc(var(--dsh-composer-side-clearance) + 16px)`；`overflow-x: clip` | ChatView.module.css:22-38 |
| 跟底时 | `.root[data-chat-following-tail] .scroll { overflow-anchor: none }` | :45-48 |
| 行间距 | 默认 `--dsh-chat-flow-gap: 4px`；`[data-chat-group-part="response"]` → **12px**；Turn 控件前后 → **16px** | :82-98, :137-144 |
| 组内行间距 | `--dsh-chat-flow-gap: 4px`；相邻可见子项 `margin-top` | ChatGroupSeat.module.css:102-108,143-150 |
| 组体高度上限 | `max-height: min(400px, 50vh); overflow-y:auto; overscroll-behavior-y:auto` | :102-108 |
| 组体上下渐隐 | `mask-image` 24px 方向渐变 | :117-133 |
| 折叠时的间距过渡 | `margin-top 160ms cubic-bezier(0.4,0,0.2,1)`（`[data-chat-motion]` 内） | ChatView.module.css:113-116 |
| 折叠行高度（思考/工具行） | `calc(24px + var(--dsh-content-font-delta,0px))` | ReasoningRow.module.css:6-9 |
| 回到底部按钮 | 34×34、圆角 100px、浮起阴影、`bottom:16px`（共享滚动容器时 sticky 到 composer 之上） | ChatView.module.css:357-405 |
| 跟底指示器 | 12px 间距、字号 `content-2px`、行高 22px、whale 尾 + shimmer | ChatView.module.css:218-235 |

---

## 4. 行排序、分组、折叠

### 4.1 排序：就是"会话事件顺序"，但 reply 会切开组

`dsh-client-ui-chat/src/client/conversation-nodes/process-groups.ts:120-171`（`TurnGroups.rebuild`，每个 Turn 单独重建）：

```ts
let pending: NodeReference[] = []
for (const key of input.readTurn(this.turn)) {          // ← 该 Turn 内按投影顺序遍历
  const position = readPosition(input, key)
  if (previous !== undefined && position.previous !== previous) flush(true)   // 历史前插
  previous = key
  followed = position.next !== undefined
  const node = readNode(input, key)
  if (isAtOrOutside(node, 'process')) {                 // 用户/steering/turn-trigger/retry/error/tail …
    flush(true); emit(key, { kind: 'node', key })        //   → 断开当前组，自己作为独立根
  } else if (node.kind === 'assistant-step') {
    if (reasoning(node)) pending.push({ kind:'node', key, groupPart:'reasoning' })  // 思考 → 进组
    if (reply(node)) { flush(true); emit(key, { kind:'node', key, groupPart:'response' }) } // 回复 → 断组 + 独立行
  } else pending.push({ kind: 'node', key })             // 工具等 → 进组
}
flush(followed)                                          // 尾部未闭合段
```

分组规则表（同时见 asar 内 README `.../conversation-nodes/README.md:202-240`）：

| 输入 | 处理 |
|---|---|
| assistant 非空 reasoning | 作为 `groupPart:'reasoning'` 追加进当前组，必要时新建组 |
| assistant 回复（文本/图片等可见内容） | **结束**当前组，回复本身作为独立 `groupPart:'response'` 行 |
| user / steering / turn-trigger / model-retry / turn-error / turn-max-tokens / turn-tail | 结束当前组，各自独立成根 |
| `turn-process` | 独立成根，**不结束**当前组 |
| 其它可见节点（工具） | 追加进当前组 |
| 隐藏节点 / system prompt / 普通 context 注入 / permission | 完全不参与分组（不加入也不断开） |
| step 变化 | **不**断组 |
| `run_code` 的嵌套子调用 | 不单独成根（挂在父工具 Node 的 `subCalls` 里） |

### 4.2 组身份：历史前插时保持原组 key（避免整组重挂载）

`process-groups.ts:173-190` 的 `extendedGroup`：只有"旧组的完整成员仍连续存在、且新增成员只在两端出现"时才复用旧 `GroupKey`，否则新建（key = `JSON.stringify(['process', firstKey, firstPart])`）。
→ 加载更早历史时，**已有组的 header/展开状态/滚动位置都能保住**。

### 4.3 组数据（`ProcessGroupData`）

- `summary.counts`：按工具名分类计数（`process-activity.ts`），最大 3 类参与组头文案，**不显示数字**。
  - 分类表（README:260-278）：`read` / `read_image`→readImage / `grep|glob|*_inspect`→search / `write` / `edit|apply_patch` / `bash|pwsh|exec_command|write_stdin|terminal_*`→commands / `run_code`→code / `web_search` / `web_fetch` / `subagent*`→subagents / `todo_write|create_goal|update_goal|get_goal`→plan / `ask_user_question|request_user_input`→questions / 其它→tools。
  - 排序：count 降序，同分按首次出现顺序；同一 `callId` 只计一次；`run_code` 的 PTC 子调用各自计数。
- `summary.running` + `runningDetail`：**运行中**的调用里 `time` 最大的那个（同 time 取后访问者）决定"当前活动类别"和详情；无运行中调用时用最近一条非空 reasoning 段做 detail。组一旦 closed 立刻清空 live 字段（README:297-317）。
- 详情字段优先级（README:318）：`title > description > objective > task > task_name > name > question(s) > prompt > message > command > cmd > queries > query > pattern > url > uri > file_path > path > target > action > status`，压空白、截断 **160 grapheme**（尾部加 `…`）。

### 4.4 组头文案

`dsh-client-ui-chat/src/client/chat/step-process.ts:11-28`（closed 组）：

```ts
const labels = summary.counts.slice(0, 3).map(({ kind }) => t(`message.stepProcess.done.${kind}`))
const first = labels[0]
if (first === undefined) return t('message.stepProcess.done.thinking')   // "已完成分析"
const second = labels[1]
if (second === undefined) return first
if (labels.length === 2) {
  const prefix = t('message.stepProcess.sharedPrefix')                    // 中文 "已"
  const shared = prefix !== '' && first.startsWith(prefix) && second.startsWith(prefix)
  return t('message.stepProcess.joinTwo', { first, second: continuation(shared ? second.slice(prefix.length) : second) })
}
const title = [first, ...labels.slice(1).map(continuation)].join(t('message.stepProcess.comma'))
return summary.counts.length > 3 ? t('message.stepProcess.more', { title }) : title   // "…等"
```

英文文案（`dsh-client-ui-chat/src/client/locale.ts`）：
运行中 `Analyzing the request / Reading files / Searching code / Writing files / Editing files / Running commands / Running code / Searching the web / Visiting web pages / Coordinating subagents / Updating the plan / Waiting for your action / Calling tools`；准备中 `Preparing to …`（`message.stepProcess.prepare.<kind>`，thinking→`prepare.tools`="Preparing to call tools"）；完成 `Analysis completed / Read files / Searched code / Wrote files / Edited files / Ran commands / Ran code / Searched the web / Visited web pages / Coordinated subagents / Updated the plan / Asked questions / Called tools`。
中文：`正在分析请求 / 正在读取文件 / …`；`已完成分析 / 已读取文件 / 执行了命令 / 修改了文件 …`；双类拼接 `A并B`（第二个去掉重复的"已"），三类用 `、`，超过三类尾加 `等`。

**运行中标题稳定性**（`ChatGroupSeat.tsx:60-83`）：`useStableLiveProcessTitle` 保证每个 live 标题至少显示 **150ms**，期间只保留最新待显示值；组一关闭（`data.closed`）立刻切到完成文案，不走 150ms。

组头图标（`ChatGroupSeat.tsx:38-54`）：thinking→Think、read/readImage→Browse、search→Search、edit/write→Edit、commands→Api、code→Code、webSearch→Globe、webFetch→Browse、subagents→AgentPreset、plan→Plan、questions→Question、tools→Sparkle、context→ContextInjection。

运行中的组头用 `<TextShimmer active={!data.closed}>` 做流光；Standard/Detailed 追加 live detail（`label · detail`），Compact/Verbose 不显示 detail（`presentation-policy.ts` 的 `liveProcessDetail`）。

### 4.5 折叠层级与状态归属

| 层 | 状态存在哪 | API |
|---|---|---|
| 整轮 process（Turn 级） | **Chat store**（per-session，key = turn + answerStep，只记 `collapsed`） | `turnProcessOpen()` / `actions.setTurnProcessOpen()`（`stores.ts:36-62`） |
| 二级组 | 组件本地 `useDisclosure()`（**不持久化**） | `ChatGroupSeat.tsx:138,176-179` |
| 单行（思考 / 工具） | 组件本地 `useDisclosure(version)`；`version` 是外层隐藏时自增的 `disclosureReset` | `use-disclosure.ts:11-35`、`ChatNodeSeat.tsx:122-131` |

隐藏机制：**不卸载 DOM**，而是 `hidden="until-found"`（`searchable-hidden.ts:24-37`），所以浏览器 Ctrl+F 仍能命中并 `beforematch` 自动展开；若隐藏会夺走焦点/选区则先 `reveal()` 不隐藏。

`ChatNodeSeat.tsx:63-121` 的整轮可见性判定：

```ts
const liveProcess = processPresentation !== undefined && !processPresentation.turnClosed
const interleavedInput = processPresentation?.hasInterleavedInput === true
const alwaysOpen = liveProcess || interleavedInput || turnProcessAlwaysOpen(routedNode)
const processOpen = alwaysOpen || storedOpen
const processWindowReady = foldCompletedTurns && processPresentation.turn === spec.turn
                          && (processPresentation.turnStarted || processPresentation.turnClosed)
const processMember = processWindowReady && !replyRow && !isAtOrOutside(routedNode,'turn')
  && routedNode.anchorSeq >= spec.processStartSeq
  && (liveProcess || spec.answerAnchorSeq === null || routedNode.anchorSeq < spec.answerAnchorSeq
      || (groupPart === 'reasoning' && routedNode.kind === 'assistant-step' && routedNode.data.step === spec.answerStep))
const processHidden = controllerInactive || (foldable && processMember && !processOpen)
```

→ **正在跑 / 被中断 / 失败 / 中间夹了输入**的 Turn 永远展开（`alwaysOpen`）；只有"正常完成且无夹层输入"的 Turn 才允许整轮折叠，默认收起。

### 4.6 四种展示模式（`presentation-policy.ts:25-54`）

| 行为 | Compact | Standard | Detailed | Verbose |
|---|---|---|---|---|
| 组头 | 有 | 有 + live detail | 运行中隐藏组头（flat），历史 Turn 有 | 无组头 |
| 组体 | 初始收起 | 初始收起 | 运行中直接展开、无高度上限；历史 Turn 手动 | 一直直接展开、无上限 |
| 已结算思考预览 | 不显示 | 首行 | 首行 | 首行 |
| 完成的 Turn | 整轮收起 | 整轮收起 | 整轮收起（但本轮 flat 保留到下一次输入） | 永远展开，无折叠按钮 |
| 设置项 | `ui-chat.transcriptView`（Desktop 默认 standard，Web 默认 detailed）；`collapseTiming = completion` | | | |

`collapseTiming: 'next-input'` 时，最新完成的 Turn 保留运行态外观（组头/平铺），直到下一次输入或更新的 Turn 出现才折叠，并配合输入揭示动画（`ChatView.tsx:56-57` `MOTION_TAIL_MS = 800`、`deferCompletedTurns` 相关分支）。

---

## 5. 流式：增量如何进入已有的行

### 5.1 Assistant：按 block 索引累积，同 key 替换数据

`dsh-client-ui-chat/src/client/conversation-nodes/assistant.ts:102-171`（节选，已省略 `visibleByIndex` 维护）：

```ts
case 'text-delta': {
  const previous = blocks[chunk.index]
  changedIndex = chunk.index
  previousVisible = state.visibleByIndex[chunk.index] === true
  blocks[chunk.index] = { kind: 'text', text: (previous?.kind === 'text' ? previous.text : '') + chunk.text }
  nextVisible = (previous?.kind === 'text' && previousVisible) || chunk.text.trim() !== ''
  break
}
case 'reasoning-delta': {   // 同理，kind:'reasoning'
  blocks[chunk.index] = { kind: 'reasoning', text: (previous?.kind === 'reasoning' ? previous.text : '') + chunk.text }
  ...
}
case 'tool-call-delta': {   // 助手侧只保留身份，参数归 Tool 节点
  blocks[chunk.index] = {
    kind: 'tool-call',
    callId: (previous?.kind === 'tool-call' ? previous.callId : '') || String(chunk.id),
    name: chunk.name ?? (previous?.kind === 'tool-call' ? previous.name : ''),
    argsRaw: previous?.kind === 'tool-call' ? previous.argsRaw : '',
  }
}
case 'block-start': blocks[chunk.index] = emptyAssistantBlock(chunk.blockType)
case 'block-end':   blocks[chunk.index] = toAssistantBlock(chunk.block)   // 用完整 block 覆盖
case 'usage':       return { ...state, usage: chunk.usage }
```

- 可见性按 block 是否非空追踪（`visibleByIndex`），空 reasoning/text block 不渲染。
- `llm/retry` → `resetForRetry()` 清空 blocks 但保留 `firstTokenTime`。
- **结算**：`assistant/message` 到达时 `settleMessage()` 用 `toAssistantBlocks(event.data.message.content)` **整体替换** blocks（assistant.ts:173-188），并记下 `final` match。
- 时间线：`anchorSeq` 取首个可见内容 seq（保证行不会因为后续 block 变化而重排），`firstTokenTime` 用于统计。

### 5.2 Tool：`preparing → start → result`，参数用惰性视图

`dsh-client-ui-chat/src/client/conversation-nodes/tool.ts:62-118`：

```ts
// tool-call-delta：第一个命名 delta 创建 preparing 节点，参数累进 PartialArguments
const args = new PartialArguments(); args.append(chunk.argumentsDelta)
root = { phase:'preparing', callId:String(chunk.id), name:chunk.name, turn, step, time, subCalls:[], args }
// 后续 delta：root.args.append(chunk.argumentsDelta)（就地追加，args.refresh() 按需解析）
// block-end（tool-call）：把 name/args 定稿
// tool/call：phase='start'（args 用 fromText / settle 出完整 JSON）
// tool/result：变成 ToolResultNode { kind:'tool-result', content, isError, meta, subCalls:[] }
// tool/ptc-dispatch[-start]：把嵌套子调用挂到父 callId 的 children，深度上限 MAX_DEPTH=256，防环
```

- 每组 `PartialArguments` 有 512 UTF-16 的读取前缀上限（组头 detail 用）；准备态节点不可展开。
- 中断：Turn/Step 关闭时若调用无结果，投影成 `isError:true, error:{name:'Interrupted',code:'interrupted'}` 的合成结果（tool.ts:226-267）。

### 5.3 渲染层：为什么不会 remount

- `ChatFlow` 用 `chatRenderKey` 做 React key；流式期间 entries 不变（组边界只在 reply/输入出现时变），所以 **seat 组件实例不变**。
- `ChatNodeSeat` 是按 key 订阅的 memo 组件：只有该 Node 的数据变才重渲（`useChatNode(nodeKey)`），兄弟行不重渲。
- `MarkdownText` 的 `streaming` 分支：同一个组件实例内 `useRef<StreamingRenderer>` 持有解析器与缓存，`text` 变化时增量渲染，不重建 DOM 树。

### 5.4 文本增量渲染：冻结前缀 + 只重解析最后 2 块

`[checkout-0.2.1] packages/client/ui-primitives/src/markdown/incremental.ts:4-29`：

```
CommonMark 的 block 解析是逐行的，追加文本只可能改写"解析边界"（最后一个顶层 block：
段落变 setext 标题 / 表格 / 空行后的列表延续），更早的 block 已经确定。
因此冻结除最后 UNSTABLE_TAIL_BLOCKS(=2) 个 block 以外的所有 block，只重解析尾部源码；
未闭合的顶层 fence 用第二层边界：只把"最后一个完整行 + 当前半行"送回语法解析。
每个源码区域在整条流里只被解析有界次，而不是每 chunk 一次。
```

`markdown/MarkdownText.tsx:66-147` 的 `StreamingRenderer`：

```ts
render(text: string): ReactNode[] {
  if (text === this.lastText) return this.lastRendered          // 幂等
  const { frozen, tail, generation } = this.parser.update(text) // 只增量解析尾部
  if (generation !== this.generation) { /* 非追加输入：清空所有冻结缓存 */ }
  const newlyFrozen = frozen.slice(this.frozenCount)            // 新越过边界的 block
  collectReferenceTargets(newlyFrozen.map(b => b.node), this.frozenTargets)
  // 只为 newlyFrozen 渲染一次，缓存成 ReactNode[]（含块间 '\n' 分隔符）
  const children = [...this.frozenElements, ...renderBlocks(tail, tailContext)]
  return children
}
```

- block 的 **React key = 该 block 在全文中的起始 source offset**（`incremental.ts:70-73`），越过冻结边界时 key 不变 → React 复用而非重建。
- 流式中 fence（代码块）只对追加文本重新 tokenize（`streaming-fence-highlight.e2e.ts` 即测此）；TeX 在流式中按字面渲染，结算后才换 KaTeX，避免半截公式报错闪烁。
- 结算：`streaming=false` 时 `streamRef.current = null`，走 `renderSettled()` **全量解析**（含引用链接/脚注），已知偏差是流式期间跨冻结边界的引用式链接会先按字面显示，结算后自愈。
- 性能约束：`labels`、`fileMentions`、`pathImages` 必须引用稳定，否则每 chunk 丢缓存（源码注释明确要求）。

---

## 6. 完成后：是否重构/替换、如何避免位置跳动

### 6.1 会发生的"重构"

1. **Assistant 内容整体替换**：`assistant/message` → `settleMessage()` 用持久化内容覆盖流式 blocks（同 Node key，不 remount 行容器；MarkdownText 从 streaming 切 settled，清缓存重解析）。
2. **分组结果重建**：`structureChanged()` 检测 `kind / turn / 可见性 / groupTree / reasoning / reply` 变化（`process-groups.ts:42-49`），任一变化就 `rebuild(turn)`。典型：reasoning 从"只有思考"变成"思考 + 回复"时，reply 把组切开，同一 Node 的 reasoning 行留在组里、response 行成为独立根。
3. **Turn 折叠**：Turn 关闭后 `processPresentation.turnClosed = true` → `foldCompletedTurns && !alwaysOpen && !storedOpen` → 整轮 process 成员被 `hidden="until-found"` 隐藏（带折叠动画），Turn 控件从空（运行中 `TurnProcessNodeView` 返回 `null`）变成"已完成，用时 …"。
4. **组头文案切换**：`.closed=true` 时立即从运行中文案切到完成文案（150ms 只约束运行中变化）。

### 6.2 位置不动靠的五层机制

**① 折叠前测量**（`use-chat-viewport.ts:497-558` `measureClosing`）
在提交前（行开始关闭的那一帧）记录：第一个可见行、其后屏幕内的行、上方一个视口内的行，以及每行到"内容末尾"的距离（`fromEnd`）。只在"跟底、无折叠批次、无动画、无未采样滚动、无分页"时做。

**② 瞬时隐藏的补偿**（`keepAboveClosing`，:575-604）
若同一提交里内容确实变矮，则把第一个仍存在的锚点行放回原屏幕位置，缺口用**底部 spacer** 填，且缺口上限 = 视口高度的一半（`cap = height / 2`），超过则内容末尾停在视口中线。

**③ 折叠批次：同一帧时钟驱动所有盒子**（`flow-motion.ts`）
常量：`FADE_OUT_MS=70 / CLOSE_DELAY_MS=50 / CLOSE_MS=110 / SETTLE_SLACK_MS=80`，缓动 `cubic-bezier(0.4,0,0.2,1)`。
批次里同一 `progress`（smoothstep）同时写：行的 `height/marginTop/opacity`、视口 `scrollTop`、底部 spacer 所需的"房间"（`current.prepare/frame`，见 §7.4）。
`collapse()`（:385-432）先记录 `offsetHeight`/`marginTop`，设 `height` 与 `marginTop` 后加过渡：

```ts
style.transition = `opacity ${FADE_OUT_MS}ms ${EASING}, `
  + `height ${CLOSE_MS}ms ${EASING} ${CLOSE_DELAY_MS}ms, margin-top ${CLOSE_MS}ms ${EASING} ${CLOSE_DELAY_MS}ms`
style.opacity = '0'; style.height = '0px'; style.marginTop = '0px'
```

批次 `begin()` 里若判定"落在尾部"（跟底读者的自动折叠），先把批次外已经长大的内容一次性补进 `scrollTop`，再动画批次自己的行，保证只有折叠行在动。
`data-chat-motion` 作用域 + `prefers-reduced-motion` 双重门控。

**④ 底部 spacer**：`[data-chat-turn-spacer]` 由视口 owner 写 `style.height`；只在防 clamp/补缺口时增加，`reclaimBelow()` 在读者滚动或内容增长后按需归还——**滚动范围只收缩不突变**。

**⑤ 原生 scroll anchoring 的开关**：跟底期间 `.root[data-chat-following-tail] .scroll{overflow-anchor:none}`；折叠批次进行中 `motion` 回调把 `scroller.style.overflowAnchor='none'`，批次结束恢复 `''`（`use-chat-viewport.ts:104-110`）。非跟底读者的历史前插则交给浏览器原生锚定（`historyConsumption()` 返回 `'native'`，:1019-1030）。

### 6.3 已知会"换 key"的地方（迁移时必须注意）

- reasoning 行 ↔ response 行的拆分由 `groupPart` 决定，结算时若从"仅思考"变成"思考+回复"，**response 行是新增 key**（本来就没有，不算跳动）。
- 分组重建导致某行从"组内成员"变"独立根"（或反向）时，React key 的父级变了 → **DOM 重挂载**，展开状态会丢；DSH 用 `extendedGroup` 保持组身份、用 `disclosureReset` 统一重置内层展开来让"重挂载"后果可预期，再用 §6.2 的滚动补偿吃掉视觉跳动。
- Detailed 模式在"运行中 flat ↔ 关闭后成组"之间切换组头出现/消失，由折叠批次动画承担（组头 `useGroupHeaderAction(headerRef, !grouped || …)`，`ChatGroupSeat.tsx:165-167`）。

---

## 7. 自动滚动算法（`ScrollFollow` + `ChatViewport` + `ChatReading`）

### 7.1 核心状态机：`ScrollFollow`（`dsh-client-ui-chat/src/client/chat/use-scroll-follow.ts`）

```ts
export class ScrollFollow {
  private target: number | null = null        // 未完成的原生 smooth 动画目标
  private sampledTop: number | undefined
  constructor(private following: boolean, private readonly threshold: number) {}

  nearBottom(m: ViewportMetrics) { return Math.abs(m.floor - m.top) <= this.threshold }   // :67
  sample(m, movedByReader = this.sampledTop === undefined || Math.abs(m.top - this.sampledTop) > 0.5) {
    this.sampledTop = m.top
    if (!this.animating && movedByReader) this.following = this.nearBottom(m)              // :90-95
    return this.following
  }
  settle(m) {                                  // scrollend：原生动画落点离目标太远 → 放弃跟随
    const target = this.target; this.target = null
    return this.sample(m, target === null ? undefined : Math.abs(m.top - Math.min(target, m.floor)) > this.threshold)
  }
  toBottom(el, m, behavior) {                  // :138-158
    this.following = true
    if (m.top === m.floor) return this.jump(el, m, m.floor)
    if (behavior === 'instant' && this.animating) {        // 内容在动画中长大 → 重定向目标
      if (this.target !== m.floor) { this.target = m.floor; el.scrollTo({top:m.floor, behavior:'smooth'}) }
      return m
    }
    if (behavior === 'instant' || (!this.animating && this.nearBottom(m))) return this.jump(el, m, m.floor)
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) return this.jump(el, m, m.floor)
    if (this.target === null) { this.target = m.floor; el.scrollTo({top:m.floor, behavior:'smooth'}) }
    return m
  }
  interrupt(el, m) {                           // 用户手势：在当前位置停住动画并把它当新采样基线
    if (!this.animating) return
    this.target = null; this.sampledTop = m.top
    el.scrollTo({ top: m.top, behavior: 'instant' })
  }
}
```

- **跟底阈值 = `FOLLOW_THRESHOLD + 1 = 25px`**（`use-chat-reading.ts:7,277`）。即距底部 25px 以内视为"在底部"。
- **组内滚动用同一类的 threshold = 1px**（`use-process-scroll.ts:26`）。
- `target` 的存在使 `animating` 可区分"原生动画进度"和"读者移动"：动画期间收到的 scroll 事件不算读者移动，只有 `scrollend` 的落点决定是否放弃跟随。
- 用户手势（wheel/touchstart/pointerdown/scroll 键/`beforematch`）先 `interrupt()`，只有"之后真的位移"才改变 following。

### 7.2 接线与采样节奏（`use-chat-reading.ts`）

```ts
const FOLLOW_THRESHOLD = 24
const SCROLL_SAMPLE_INTERVAL_MS = 500

onScroll(scroll) {
  if (this.follow.animating) return
  if ((!scroll.movedByReader && this.state.followingTail)
      || (scroll.movedByReader && Math.abs(scroll.metrics.top - scroll.metrics.floor) < 1)) {
    this.followTail(); ...; return                      // 在底部 / 布局把它顶到底部 → 立即继续跟
  }
  this.sampleTimer ??= window.setTimeout(this.flushSample, SCROLL_SAMPLE_INTERVAL_MS)  // 否则 500ms 后再判定
}

onScrollEnd(outer) {
  if (this.follow.animating) {
    if (!outer) return
    if (this.follow.settle(metrics)) { this.followTail(); return }     // 动画落到底 → 继续跟随
    this.viewport.acknowledge(metrics); publish({ followingTail: false })   // 提前停住 → 停止跟随
    return
  }
  this.flushSample()
}

onResize() {                                 // 内容/视口尺寸变化（ResizeObserver）
  if (this.pending) return                   // 读者仍有未采样移动 → 不抢
  if (this.state.followingTail) this.followTail()      // ← 流式增长真正的驱动
  else this.refreshActiveTurn()
}

flushSample() {                              // 500ms 到点 / scrollend
  const followingTail = this.follow.sample(metrics, movedByReader)
  if (!movedByReader && followingTail) this.followTail()
  else { position = followingTail ? null : capturePosition(); acknowledge(); save(position) }
}
```

要点：
- **流式"跟随底部"由 ResizeObserver 触发**（`ChatViewport.attach` 观察 `column`、`scroller`、`composer`），不是 scroll 事件；因为内容变高不会产生 scroll 事件。
- 阅读位置按 **500ms 采样后**才写回 memory（避免每像素写 store / 每像素重渲）；但"到达底部"（距底 <1px）立即恢复跟随。
- 位置记忆是**语义锚点**（`{anchorKey, anchorTop, scrollTop}`），不是裸 scrollTop；跟底时存 `null`，重开会话时 `restore()`：有记忆 → `viewport.restore()`（找 `data-chat-anchor-key` 行，恢复其相对视口位置；找不到才退化为裸 scrollTop），无记忆 → `followTail()`。

### 7.3 提交后决策树（`use-chat-scroll.ts:70-109` `processContent`）

```ts
if (!current.isOpen && !opened) return
const ownInput = (lastIsUser && lastKey !== prev.lastKey) || steeringChanged || submissionChanged
if (reading.pending && !ownInput) return            // 读者还没采样完，别抢滚动
if (首次打开) { navigation.reset(); reading.restore(); return }
if (ownInput) {                                      // 用户刚发消息
  navigation.cancel()
  if (deferCompletedTurns && reading.followInputFold(true)) return   // 折叠动画接管落尾
  reading.followTail(steering ? 'instant' : 'smooth') // 自发消息平滑；steering/注入立即
  return
}
if (navigation.contentCommitted()) { navigation.reconcile(); return }
const tipChanged = … lastKey / order.length / running / steeringId / submissionId 变化
if (tipChanged && reading.followingTail) reading.followTail()
else navigation.reconcile()
```

- 新消息：`smooth` 滚到底（`behavior` 受 reduced motion 影响），并清空语义记忆。
- steering 到运行中的 Turn：**instant** 落底，避免 Turn 在回显上方继续增长时把回显挤走（源码注释：`Steering lands at once…`）。
- 其它增长：只有 `tipChanged`（尾部结构变化）才重新落底；否则交给 `reconcile()`（处理历史分页/锚点）。
- 切换展示模式：`reading.interruptFollow()`，等折叠批次 idle 后 `viewport.resetBelow()` 再 `onResize()`（:181-191）。

### 7.4 折叠批次的"落尾"数学（`use-chat-viewport.ts:261-454` `foldDriver`）

批次开始（`begin`）：
1. 判定 `tail = this.tailRequested || (follow.active && !reader.pending())` —— **读者在跟底，或本次是读者自己的输入**，才做自动落尾。
2. `base = max(0, start - above)`：`above` 是被折叠行在视口上方的总高度，扣掉参考行自身位移。
3. `initialHeight = max(column.offsetHeight, scrollHeight - spacer)`；`aim()` 算 `target = tail ? max(base, finalHeight - clientHeight, 0) : base`，并算 `finalRoom`（target 之上还缺多少"房间"才能落到目标）。
4. 先写 `setSpacer(max(originalRoom, finalRoom, removed))` 防中间 clamp（真正的差值最后一帧再精确安装）。
5. tail 批次若外部已有增长（例如 Turn 控件出现），先把差值直接补进 `scrollTop`，让批次只动画自己的行。

每帧（`frame`）：`scrollTop = position(progress)`；`spacer = room(top, progress)`（`room = max(finalRoom 或 0, top + clientHeight - (initialHeight + (growth-removed)*progress))`）。
结束（`finish`）：
- 非 tail：把参考行放回原屏幕位置（二次补偿隐藏行提交后的间距），再按 `top + clientHeight - content` 安装剩余 spacer。
- tail：`top = max(target, content - clientHeight, 0)`，spacer 补足差值，`scrollTop = top`，回报 `landedAtTail(landing)` → `reading.commit(... followingTail = true)`。
- 取消（读者手势）：`finish(true)` 不再提交落尾，批次内的盒子全部复位。

### 7.5 加载更早历史（分页）：锚定而不是"跳回顶部"

- 点"加载更早"前 `beginManualPaging()`：优先记 `[data-chat-paging-anchor]` 行（组折叠时=组头；展开时=第一个可见成员；`turn-process` 被排除，因为它会随分页前移）。
- 新页提交后 `finishHistoryPrepend()` → `applyPaging()`：**先补偿组内滚动**（`[data-step-process-body]`），再补偿外层滚动；两次写都限制在真实 scroll 范围内，范围不够就停止（不额外加底部空间）。
- 消费策略 `historyConsumption()`（:1019-1030）：触摸/指针未抬起 → 延后；有折叠动画/原生动画 → `motion`；有内层组在滚 → `inner-scroll`；`scrollTop<=0` → `prepend`；否则浏览器支持 `overflow-anchor` 就 `native`，否则 `preserve`。
- 自动加载：`confirmHistoryStart()` 判断历史按钮是否在 **2 个视口高度**内（:998-1007）。
- 效果：**"加载更早"按钮下第一项在屏幕上的位置不变**（README:102 明确这条），新内容出现在它上方。

### 7.6 回到底部按钮与跟底指示

- `ChatView.tsx:301-312`：`!scroll.followingTail` 时渲染 `toBottomSlot > button`，`aria-label = t('chat.toBottom')`；点击 = `cancelPendingFollow() → navigation.cancel() → reading.followTail()`（instant）。
- 跟底指示 `RunningStatus`（`RunningStatus.tsx`）：只在运行中（或占位 holding）出现，文案 `深度求索中，用时 {d} ···`，`LIVE_RUN_CLOCK_INTERVAL_MS = 1000`（`message-chrome.ts:13`），整秒更新，`TextShimmer` 流光；`aria-live="polite"` 只播报一次"深度求索中"，时钟 tick 不播报。

### 7.7 虚拟列表 / scroll anchoring 结论

| 区域 | 是否虚拟化 | 依据 |
|---|---|---|
| 主 transcript | **否**（全部已加载行都挂载） | `ChatFlow.tsx:52-63` 直接 `entries.map`；CSS 注释称 `.flowItem` 为"未来虚拟化的天然测量/挂载单元"（`ChatView.module.css:100-104`） |
| 右侧 Turn 导航栏 | **是**，`@tanstack/react-virtual`（`useVirtualizer`，固定 pitch） | `TurnNavigator.tsx:1-6,110,245-257` |
| Trajectory 视图 | 有独立虚拟化实现（`trajectory-virtual-rows.ts` + `trajectory-virtualization.e2e.ts`） | ui-trajectory 包 |

- DSH 关闭主 transcript 虚拟化的原因是：流式增长 + 折叠高度动画 + 语义锚点三者都要求"真实布局"，虚拟化会让高度估计和锚点补偿互相打架。
- scroll anchoring：跟底时关闭（否则原生的"锚定上方内容"会和 follow 打架）；非跟底时开启，用于历史前插；折叠批次期间也关闭。

---

## 8. 工具行：详情、参数、结果的展示形态

### 8.1 行本身（`dsh-client-ui-tool/src/client/tool/components/ToolRow.tsx`）

```tsx
<div className={css.root} data-variant={variant} data-tool={toolName} data-state={state}>
  {status && <span className={css.visuallyHidden}>{status}</span>}       // preparing/running/failed/stopped 的无障碍文本
  <DisclosureRow
    icon={icon} title={title}                                            // 例：Bash / Read / Edit / Web Search
    running={running}                                                    // preparing|running → 流光
    open={open} expandable={expandable} expandOnRowClick keepContentWhenOpen
    onToggle={toggleExpand}
    collapsedContent={collapsedContent}>                                 // 标题后的灰色摘要（可点击打开文件/URL）
    {expandedContent}
  </DisclosureRow>
</div>
```

- `expandable = state !== 'preparing' && (inputRaw !== null || outputText !== null || card !== null)`（:217）——准备态不可展开。
- 折叠行高 24px；摘要单行省略；`running` 时 `TextShimmer`。
- `collapsedContent` 组成：`· 摘要`（`css.sep` 小圆点）+ 可选的 `ExitCodeBadge` + `summarySuffix` + diff `+N -M`；error/stopped 时摘要用首行错误并染红/灰，不显示 diff 统计。
- 摘要可交互：
  - 有 `filePath` → 渲染成按钮，点击打开文件（可带行号），阻止冒泡避免触发展开；
  - 有 `href`（web_fetch 的 http(s) URL）→ 渲染成 `<a target="_blank" rel="noopener noreferrer">`；
  - `fileTargets`（apply_patch 类）→ 每个目标文件一个独立按钮，逗号/箭头分隔。

### 8.2 展开体（同一文件的卡片优先级，:332-458）

```
askQuestion ? <AskQuestionCard>
: terminal  ? <TerminalBlock maxLines={Infinity} className={css.terminalBody}>
: diff      ? <DiffBlock maxLines={CHAT_DIFF_MAX_LINES}> + (error 时追加 OUTPUT 文本)
: read      ? <ReadBlock maxLines={CHAT_READ_MAX_LINES}>
: image     ? 图片画廊(slot tool.call.images) + 路径 + 每张尺寸/原尺寸
: search    ? <SearchBlock maxLines={CHAT_SEARCH_MAX_LINES}> + 截断恢复定位串
: web       ? <WebBlock>                        // 见 §9
: details   ? <ToolDetails model={…}>           // 结构化字段/列表卡片
: 通用       ? variant==='code' ? <CodeBlock lang="typescript">
            : <div class=ioCard> IN: 参数(格式化) │ OUT: 结果文本 </div>
```

- `variant` 由工具名决定（`tool-call-model.ts` 的 `ToolRowVariant`），决定摘要格式与 IN 区排版（如 diff 变体用多文件链接）。
- `inspect`：右下角"在 Trajectory 中查看"按钮（`IconInspect`），点击 `inspectCall(callId)`。
- 结果前的 "Inspect" 与"工具调用树"：`ToolCallTree.tsx` 对根调用 + 递归 `subCalls` 渲染，每行包围 `div[data-chat-anchor-key="call:${callId}"][data-chat-call-id]`，因此**每个嵌套子调用也是可锚定/可搜索的独立行**。

### 8.3 结构化详情卡（`ToolDetails.tsx`）

`ToolDetailItem` 支持：`title/subtitle/description/badge/status(completed|in_progress|pending)/change(added|removed|updated)/fields(dl)/lines(ul)/markdown/code/location(可点开文件)/groups(<details> 嵌套)`；空列表显示 `empty`；`caption`；`unchanged` 折叠"未变化项"。这套模型常被 todo/plan/文件清单类工具复用。

---

## 9. 搜索来源 / 引用的展示形态

**结论：DSH 没有"行内引用角标"**（assistant Markdown 里不会把 `[1]` 变成可点 chip）。来源只在 `web_search` / `web_fetch` 工具卡展开后出现；另有 Trajectory Inspector 的 `detail.trace.sources`（"Source events"/"引用来源"）是给调试视图用的。

### 9.1 `web_search` 卡（`[checkout-0.2.1] packages/client/ui-primitives/src/WebBlock.tsx:150-170`）

```
div[data-web="search"]
├─ answer（可选）→ <MarkdownText>   provider 生成的答案，按正文 Markdown 渲染
├─ 空结果 → "No results found."
├─ ol.sources
│   └─ li[value=ordinal]                     // 显式锁定 1-based 序号（避免滚动容器里 <ol> 计数错）
│      ├─ <a href=http(s) target=_blank>      // 非 http(s) 只渲染成纯文本，杜绝 javascript:/data:
│      │   └─ LinkIcon + (title || hostname || 原 URL)
│      ├─ .snippet                            // 可选摘要
│      └─ .published                          // 可选 publishedAt 字符串（不解析时间）
└─ truncated → "来源列表已截断"
```

数据来源：`dsh-client-ui-tool/src/client/tool/models/web-card-model.ts:58-82` —— 仅对**成功的根调用**（`block.parentCallId === undefined && 'kind' in block && !block.isError`）且 `meta.truncated: boolean` 时生成；`web_search` 读 `meta.sources[{url,title?,snippet?,publishedAt?}]` + `meta.answer?`；`web_fetch` 读 `meta.url + meta.statusCode`，失败即退回通用路径。

### 9.2 `web_fetch` 卡（同文件 177-187）

`URL 链接 + HTTP {statusCode} (+ 内容已截断)`；URL 来自参数里的 `http(s)` 且仅成功时可点。

### 9.3 迁移建议

如果目标产品需要"行内引用"（`[1]` 角标 → 点击跳到来源），DSH 没给现成实现；可借鉴的是：**用 stable ordinal + 安全 URL 白名单 + 截断标记** 这套数据形状，把 `meta.sources` 暴露给 Markdown 渲染器做 inline chip。

---

## 10. 可迁移到 Jetpack Compose 的行为清单

> 按"行为契约"写，括号里是 DSH 依据。可直接当实现 / 验收 checklist。

### A. 数据与状态模型

- [ ] **A1 三层结构**：`Turn`（一轮）→ `ProcessSegment`（`Group` 或 `Node`）→ `RowItem`。分组在**数据层一次算好**，UI 只消费有序 `List<RenderEntry>`；不要边渲染边分组。（`process-groups.ts`）
- [ ] **A2 行 key 规则**：`node:(nodeKey, groupPart)` / `group:groupKey`，`groupPart ∈ {reasoning, response, null}`。同一个 assistant 节点可同时产出"思考行"和"回复行"。（`render-entry.ts`）
- [ ] **A3 分组边界规则**：assistant 非空 reasoning 入组；assistant 可见回复 → 断组 + 独立 response 行；user/steering/trigger/retry/error/max-tokens/turn-tail → 断组；`turn-process` 独立但不断组；step 变化不断组；隐藏节点不参与。（§4.1）
- [ ] **A4 组身份复用**：历史前插时，如果旧组成员仍连续且新增只在两端，复用旧 groupId，否则新建。（`extendedGroup`）
- [ ] **A5 组摘要**：`counts`（工具名精确分类，同 callId 去重，count 降序、同分按首次出现）+ `running`（运行中调用 `time` 最大者）+ `runningDetail`（参数优先级列表 → 压空白 → 160 grapheme 截断）；组 closed 时清空 running。（`process-activity.ts`）
- [ ] **A6 三层折叠状态**：Turn 级持久（answeredStep + collapsed）；组级内存；行级内存，并在"外层隐藏"时自增 reset 版本清掉内层展开。（§4.5）
- [ ] **A7 展示模式**：至少实现 `compact/standard/detailed/verbose` 四档的 5 个开关：`foldCompletedTurns`、`stepGrouping(collapsed|history|none)`、`liveProcessDetail`、`settledReasoningPreview`、`foldedReplies`。默认按平台选择（桌面 standard / Web detailed 是 DSH 的默认）。（`presentation-policy.ts`）

### B. 列表与渲染身份

- [ ] **B1 主列表不虚拟化**（或只虚拟化"已结算且非末尾"的历史段）：流式 + 折叠高度动画 + 语义锚点都依赖真实布局。DSH 主 transcript 全量挂载，只虚拟化右侧 Turn 导航栏。（§7.7）
- [ ] **B2 key 用稳定业务 key**，禁止用 index；`LazyColumn` 的 `key = (nodeKey, groupPart)`，组的 `key = groupKey`。
- [ ] **B3 每行独立订阅**：一行只应因其自身数据变化重组（DSH：按 nodeKey 的 memo seat）。Compose 用 `derivedStateOf` + 稳定 key，避免 scroll 采样触发整表重组。
- [ ] **B4 空行/隐藏行不占高度**：`hidden="until-found"` 在 Compose 里没有等价物——需要"保留语义可搜索、但零高度"的自定义实现（例如把被折叠行留在语义树但测量为 0，或提供全文搜索索引）。

### C. 流式

- [ ] **C1 高频 delta 合并**：不要每 chunk 重组。用 `Channel(CONFLATED)` + `withFrameNanos` 合并（DSH 是"连续 3 个 rAF 才发布一次"）。（`assembly.ts:141-158`）
- [ ] **C2 数据替换而非追加渲染节点**：block 按 index 累积 `text += delta`，Node key 不变；只更新该行的 immutable snapshot。
- [ ] **C3 文本增量排版**：不要每帧对全文重新解析 Markdown。冻结除最后 2 个 block 外的内容、block key 用"起始 source offset"，只重解析尾部；未闭合代码块只对追加部分重新高亮；公式在流式中按字面显示、结算后替换。（`incremental.ts` + `MarkdownText.tsx`）
- [ ] **C4 结算整体替换**：`assistant/message` 到达时用持久内容整体覆盖流式内容，并切到"已结算"渲染路径（清流式缓存、全量解析、允许文件链接/图片解析等流式期被禁用的能力）。
- [ ] **C5 工具生命周期**：`preparing`（参数流式、不可展开、标题用"准备…"）→ `start`（运行中、摘要可交互）→ `result`（可展开卡片；error/stopped 用首行错误）。嵌套子调用挂 `subCalls`，渲染成可锚定的子行。
- [ ] **C6 流式状态的无障碍**：`running` 行加隐藏文本（"运行中"）；跟底时钟 `aria-live=polite` 只播报一次，tick 不播报。

### D. 滚动

- [ ] **D1 `ScrollFollow` 状态机**：`following: Boolean` + 未完成的 `smoothTarget`；距底 **25px** 内视为到底；动画进行中不把 scroll 当读者移动；`scrollend` 落点离目标 >25px → 放弃跟随。（`use-scroll-follow.ts`）
- [ ] **D2 阅读意图即停止跟随**：wheel/touch/pointerdown/滚动键（以及浏览器 find）→ 先停动画、记采样基线；随后真的位移才 `following=false`。
- [ ] **D3 500ms 采样**写回滚动记忆；到达底部（<1px）立即恢复跟随。（`use-chat-reading.ts`）
- [ ] **D4 内容增长驱动跟底**：监听布局尺寸变化（Compose `onGloballyPositioned`/`snapshotFlow { layoutInfo }`），跟底时在**同一帧**把滚动位置补到新的 max；不要依赖滚动事件。
- [ ] **D5 新消息**：平滑落底（reduced-motion 时立即）；**steering/注入**：立即落底（instant），避免上方继续增长把它挤走。
- [ ] **D6 回到底部按钮**：`!following` 时显示，点击 → immediate 落底并恢复跟随；位置在输入框上方 16px、右下。
- [ ] **D7 加载更早**：点按前记录"第一个可见可锚定行 + 其相对视口 top"；插入后恢复该行位置（组内滚动优先、外层其次、范围不足就停）；自动加载阈值为 **2 个视口高**。
- [ ] **D8 scroll anchoring**：跟底时禁用原生 anchoring/自动滚动补偿，非跟底时启用；折叠动画期间也禁用。

### E. 折叠动画与"不跳"

- [ ] **E1 时间常量**：淡出 70ms；延迟 50ms 后高度/间距收 110ms；缓动 `cubic-bezier(0.4, 0, 0.2, 1)`；间距过渡 160ms；`prefers-reduced-motion` 时全部直接落位。（`flow-motion.ts`）
- [ ] **E2 同一进度驱动"行高 + 间距 + 滚动位置 + 底部占位"**：这是不跳的核心。Compose 里用一个 `Animatable<Float>` 的 progress，在每帧同时改 `height`（用 `Modifier.height` + 自定义 Layout）、`scrollState.scrollTo()` 和底部 spacer 高度。
- [ ] **E3 折叠前测量**：开始隐藏的那一帧之前记录第一个可见行及其"到内容末尾的距离"；同一提交内内容变矮时，把该行放回原屏幕位置，缺口用底部 spacer 填，缺口上限 = **视口高度/2**。（`measureClosing` + `keepAboveClosing`）
- [ ] **E4 落尾批次**：跟底读者遇到折叠时，折叠动画同时把 `scrollTop` 从"当前"补到"折叠后的新底部"，且先把批次外已增长的内容补掉（Turn 控件出现等），保证"只有折叠行在动"。（`foldDriver`）
- [ ] **E5 底部 spacer 只收缩不突变**：新增的占位只在防 clamp/补缺口时增加，读者滚动或内容增长后按需归还。
- [ ] **E6 折叠用 `hidden` 语义**：不卸载、不移出语义树（浏览器 find 可命中），但零高度、不参与布局；外层隐藏时重置内层展开状态。
- [ ] **E7 组头高度=行高**（24dp），折叠组的头可点开/收起；hover/focus 显示 chevron。

### F. 组头 / 工具行 / 来源

- [ ] **F1 组头文案状态机**：运行中 = 当前 live 类别（无 live 类别 → "正在分析请求"）+ Standard 的 ` · detail`；关闭 = 前 3 类完成文案（`A并B` / `A、B、C` / `…等`），无类别 → "已完成分析"；运行中标题变化最小间隔 **150ms**，关闭时立即切换。（`step-process.ts` + `useStableLiveProcessTitle`）
- [ ] **F2 live 类别选择**：运行中调用里 `time` 最大者；无则最新非空 reasoning 段；组关闭立刻清空。
- [ ] **F3 组体**：高度上限 `min(400dp, 50vh)`、上下 24dp 渐隐、`overscroll` 到边界后可继续滚外层（Compose：嵌套滚动 `nestedScroll` 联动）。
- [ ] **F4 工具行**：24dp 行高；标题 + 灰色摘要（单行省略）+ 可选 suffix/exit code/diff 统计；准备态不可展开；运行态 shimmer；错误/停止摘要变体。
- [ ] **F5 工具详情卡**：按工具类型分派（终端/diff/read/search/web/image/结构化 details/IN-OUT 文本）；结果区默认折叠，展开后懒渲染；`Inspect` 入口跳调试视图。
- [ ] **F6 来源卡**：`answer(Markdown) + 有序来源列表`；每项 `安全链接 + snippet + publishedAt`；显式序号；`truncated` 提示；非 http(s) 不生成链接。DSH **不做行内角标**，若要做得自建。
- [ ] **F7 间距契约**（可直接照抄）：process 行间 **4dp**；assistant 回复前后 **12dp**；Turn 控件与输入、与后续内容 **16dp**；组头与组体 **8dp**；纯流程行 24dp。

### G. 可访问性与可测试性

- [ ] **G1** 组头/工具行/思考行都是 `<button aria-expanded aria-controls>`（Compose：`Modifier.semantics { expanded = …; role = Button }`）。
- [ ] **G2** 折叠按钮被点击时先 `focus()` 再收（DSH 显式 `event.currentTarget.focus()`），防焦点丢失。
- [ ] **G3** 隐藏会吃掉当前焦点/选区时**不隐藏**，先展开供阅读（`useSearchableHidden`）。
- [ ] **G4** 运行中状态/回合结束用 `role=status aria-live=polite`，只播报状态转换，不播报时钟 tick。
- [ ] **G5** 滚动/折叠断言用"同一行的 `getBoundingClientRect().top` 不变"来验收；DSH 的 `chat-scroll-contract.e2e.ts` 就是这种写法（`expectSameFlowTop`）。

### H. 已知坑（DSH 内部同样踩过）

- [ ] **H1** 流式 append 时若 block key 用"块序号"，冻结边界移动会 remount → 必须用 source offset 或稳定 id。
- [ ] **H2** 滚动位置不要每像素写进持久状态（DSH 500ms 采样 + 语义锚点）；否则每帧重组。
- [ ] **H3** 折叠时若只改 `hidden` 不做 scroll 补偿，跟底读者会看到内容整体上移（DSH 用 keepAboveClosing 修）。
- [ ] **H4** 平滑滚动动画进行中内容继续增长要把目标重定向，而不是取消（`toBottom` 的 instant+animating 分支）。
- [ ] **H5** steering 回显要立即落底；平滑滚动期间上方增长会把回显顶下去。
- [ ] **H6** 组折叠/展开时 `overscroll` 与外层滚动要联动，否则用户在组内滚到边界后"卡住"。

---

## 11. 局限与未验证项

1. 未运行 DSH，所有行为来自源码/README/测试断言，未做运行时验证。
2. asar 内 `src/` 是**发布时附带的可读源码**，但 `ui-primitives`（MarkdownText/DisclosureRow/WebBlock/TextShimmer）在 asar 中被打进 web bundle，本文该部分引自 checkout **0.2.1**，与安装版可能有细微漂移（同为 Markdown 增量/披露行/来源卡的核心逻辑，风险较低）。
3. `lib/client.js` 是压缩构建产物；事件名/属性名已做字符串核对，但未逐函数比对。
4. 未覆盖 DSH 的其它视图：Trajectory（独立虚拟化表格）、Plan、Workflow、Subagent、Schedule、Deliverables、Terminal 面板；它们各有自己的行模型。
5. 桌面壳（Electron）与纯 Web 的差异主要在默认展示模式与滚动容器归属（外层 `[data-conversation-scroll]` 时由外层滚动，本文两种都标注）。
6. 检索来源展示只在工具卡维度确认；assistant 正文里是否存在正文级引用语法（如 `[text](url)`）未做穷举。

---

## 附录 A：关键文件索引（安装版 asar 路径）

前缀 `dsh/node_modules/@deepseek-ai/`，均为 `0.2.2-rc.5`。

| 主题 | 文件 | 关键符号 / 行 |
|---|---|---|
| Chat 视图根 | `dsh-client-ui-chat/src/client/chat/ChatView.tsx` | 231-323 JSX；`MOTION_TAIL_MS=800` :56 |
| 行列表 / 尾部槽位 | `.../chat/ChatFlow.tsx` | 52-63 rows；69-99 尾部排列 |
| 组容器 | `.../chat/ChatGroupSeat.tsx` | 93-130 组头；133-207 组体；60-83 150ms |
| 行容器 | `.../chat/ChatNodeSeat.tsx` | 63-121 整轮可见性；152-177 DOM |
| 行 key | `.../chat/render-entry.ts` | 10-16 |
| 分组/组摘要 | `.../conversation-nodes/process-groups.ts` | 120-171 rebuild；173-190 extendedGroup；199-294 ProcessState |
| 活动计数 | `.../conversation-nodes/process-activity.ts` | 工具名分类 + live 选择 |
| 组头文案 | `.../chat/step-process.ts` | 11-28 |
| 思考行 | `.../chat/ReasoningRow.tsx` + `ReasoningContent.tsx` + `reasoning-text.ts` | 49-111；12-33 预览截取 |
| Turn 控件 | `.../conversation-nodes/turn-process.ts`、`.../chat/TurnProcessNodeView.tsx` | 249-341；14-59 |
| Turn 尾（footer） | `.../chat/TurnTailNodeView.tsx` | 48-79 |
| Assistant 投影 | `.../conversation-nodes/assistant.ts` | 86-171 流式；173-188 结算；303-367 定义 |
| Tool 投影 | `.../conversation-nodes/tool.ts` | 62-118 阶段；226-267 嵌套/中断；291-353 定义 |
| 展示模式 | `.../presentation-policy.ts` | 25-54 |
| 折叠状态 store | `.../stores.ts` | 36-62 |
| 折叠动画 | `.../chat/flow-motion.ts` | 10-16 常量；268-306 tick；385-432 collapse |
| 跟随状态机 | `.../chat/use-scroll-follow.ts` | 22-171 |
| 阅读策略 | `.../chat/use-chat-reading.ts` | 7-8 常量；159-259 |
| 视口/锚点/折叠批次 | `.../chat/use-chat-viewport.ts` | 261-454；497-604；684-798；885-952；1019-1030 |
| 滚动编排 | `.../chat/use-chat-scroll.ts` | 70-109；152-197 |
| 组内滚动 | `.../chat/use-process-scroll.ts` | 全文件（threshold=1） |
| 可搜索隐藏 | `.../chat/searchable-hidden.ts` | 24-37 |
| 披露行 | `.../chat/use-disclosure.ts` | 11-35 |
| 时间格式 | `.../chat/message-chrome.ts` | 13, 54-64 |
| 样式数值 | `.../chat/ChatView.module.css`、`ChatGroupSeat.module.css`、`ReasoningRow.module.css` | 见 §3.5 |
| 发布节流 | `dsh-client-ui-conversation/src/client/conversation/assembly.ts` | 141-158 |
| 发布等级 | `.../conversation/assembler.ts` | 90-107 |
| 工具行/树 | `dsh-client-ui-tool/src/client/tool/components/{ToolRow,ToolDetails}.tsx`、`tool/ToolCallTree.tsx` | 见 §8 |
| 来源卡 | `dsh-client-ui-tool/src/client/tool/models/web-card-model.ts` | 58-100 |
| Web 卡渲染 `[checkout-0.2.1]` | `packages/client/ui-primitives/src/WebBlock.tsx` | 130-187 |
| Markdown 增量 `[checkout-0.2.1]` | `packages/client/ui-primitives/src/markdown/{incremental.ts,MarkdownText.tsx}` | incremental 4-29,70-73；MarkdownText 66-147,174-199 |
| 披露行 `[checkout-0.2.1]` | `packages/client/ui-primitives/src/DisclosureRow.tsx` | 43-123 |
| 行为规则文档 | `dsh-client-ui-chat/src/client/conversation-nodes/README.md`（安装版 0.2.2 版本） | 折叠/分组/计数/文案全表；191 行含 4px 间距 |

## 附录 B：常量与选择器速查

| 名称 | 值 | 用途 |
|---|---|---|
| `FOLLOW_THRESHOLD` | 24（实际阈值 `+1` = **25px**） | 距底判定 |
| `SCROLL_SAMPLE_INTERVAL_MS` | 500ms | 读者位置采样 |
| 发布节流 | 3 × `requestAnimationFrame` | live chunk 合并 |
| `MOTION_TAIL_MS` | 800ms | Turn 停止后折叠动画尾窗 |
| `FADE_OUT_MS` / `CLOSE_DELAY_MS` / `CLOSE_MS` / `SETTLE_SLACK_MS` | 70 / 50 / 110 / 80 ms | 折叠动画 |
| 折叠间距过渡 | 160ms | margin-top |
| 运行标题最小停留 | 150ms | 组头 live 文案 |
| 组体上限 / 渐隐 / 间距 | `min(400px,50vh)` / 24px / 4px | 组展开 |
| 行间距 | process 4px、response 12px、Turn 控件 16px | 间距契约 |
| 底部缺口上限 | 视口高 / 2 | 折叠补偿 |
| 自动加载历史阈值 | 2 × 视口高 | 分页 |
| 工具详情截断 | 160 grapheme；参数前缀 512 UTF-16 | 组头摘要 |
| `MAX_DEPTH` | 256 | PTC 子调用嵌套上限 |
| 时钟刷新 | 1000ms | 运行中用时 |
| 关键属性 | `data-chat-following-tail`、`data-chat-anchor-key`、`data-chat-flow-kind`、`data-chat-group-part`、`data-step-process[-body|-content]`、`data-chat-motion`、`data-chat-turn-spacer`、`data-chat-paging-anchor`、`data-process-activity`、`data-turn-process`、`data-web` | 测试/样式/滚动锚点 |

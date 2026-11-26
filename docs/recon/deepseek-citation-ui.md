# DeepSeek chat demo 的网络搜索引用展示与跳转链路

> 调研日期 **2026-11-26**。只读调研，未修改任何业务代码（本文件是唯一产出）。
> 目标：找出"chat demo 更好的网络搜索引用"到底是什么、怎么实现，产出可照抄到 Android Compose 的行为规格。
>
> **证据口径**
> - `[demo仓库]` = 官方 `deepseek-ai/chat-demo` 源码（`@main`，经 jsDelivr 镜像读取；本机 GitHub API 直连失败，未 clone、未安装）。
> - `[本机 DSH 源码]` = `E:\deepseek-harness`（`@deepseek-ai/dsh-root` **0.2.1-alpha.2**，本机可读的实现 checkout）。
> - `[本机 asar]` = 已安装 DSH 的 `app.asar` 字符串扫描（209,083,008 B；JS 已打包，未解包）。
> - `[myco]` = 本仓库代码（行号为撰写时快照，见 §5.0 的并发修改说明）。
> - `[推断]` = 未经运行验证的解读。
> - 未输出任何 API key/token；未下载大文件；未调用模型 API。

---

## 0. 一页结论

1. **本机没有 chat demo 的源码副本**：`C:\Users\11096\{Desktop,Documents,Downloads,source}`、`E:\{CODE,workspace,deepseek-harness,tools,tempspace,...}` 按目录名/文件名（`chat-demo`/`web-chat`/`webui`/`CitationLink.tsx`/`SourceCard.tsx`）搜索均无命中。用户说的 demo 就是官方 `deepseek-ai/chat-demo`（React 静态前端，响应式 Web；上一轮 recon 已确认仓库与形态，见 [deepseek-chat-demo-ui.md](deepseek-chat-demo-ui.md:10)）。本次通过 jsDelivr 镜像**只读**读到了它的引用相关全部源码。
2. **demo 的引用链路是"服务端给 citations → 客户端编号 → 正文角标 + 来源面板"**，不是把搜索结果塞进工具结果：
   `citations_delta` → `text block.citations` → `SourceRegistry`（按 URL 去重、按出现顺序编号）→ 在**文本块尾部**注入 `[citation:N]` → mdast `findAndReplace` 换成 `citation` 节点 → `CitationLink`（悬浮预览 `SourceCard` / 点击新窗口打开）→ 来源面板 `SearchPanel`（桌面右栏、移动端全屏）。
3. **编号规则**：同一 URL 只编号一次，编号 = 工具结果（搜索结果/打开的网页）在回复中首次出现的顺序；**与正文引用先后无关**；编号只增不改（append-only）→ **流式不重排**。
4. **来源卡片字段**：`siteIcon` / `siteName` / `publishedAt`（`YYYY/MM/DD`）/ `title` / `snippet`；缺字段整段省略；**仅 http(s) 才可点**，其余渲染纯文本。
5. **流式时序**：搜索结果块先原子到达（编号先定），正文的 `citations_delta` 在块文本之后到达，角标出现在**该文本块尾部**（服务端按引用点切块，所以视觉上紧跟被引句子）；完成后不重排；**复制出来的正文不带编号**（复制的是原始 `text`）。
6. **本机 DSH 是另一套实现**：`web_search` 结果是工具行展开后的 `WebBlock` 卡片（provider answer + `<ol>` 编号来源），**没有正文角标、没有站点 favicon**；安装包 asar 里能扫到 "来源列表已截断"/"未找到结果"，且没有 demo 专属类名 `ds-markdown-cite`。所以"更好的引用"确实来自 chat demo，不是 DSH。
7. **对 myco 的可落地结论**：分两步——先抄**卡片化 + 编号 + "已阅读 N 个网页"摘要行 + 来源面板**（纯客户端可做，无需协议变更）；再决定是否接 DeepSeek **服务端 web tools** 拿原生 `citations` 实现正文角标（这是唯一能做到"角标钉在被引句子旁"的路，代价见 §5.4）。

---

## 1. 找到/没找到什么（本机与外部证据）

| 对象 | 位置 | 状态 | 结论 |
|---|---|---|---|
| chat-demo 源码 | 本机常见目录 + `E:\` 工作盘 | **未找到**（目录名/文件名两轮搜索均 0 命中） | 本机无副本 |
| chat-demo 源码（外部） | `https://cdn.jsdelivr.net/gh/deepseek-ai/chat-demo@main/...` | ✅ 可读（GitHub API `api.github.com` 直连失败） | 本报告 `[demo仓库]` 来源 |
| DSH 源码 checkout | `E:\deepseek-harness` | ✅ 可读（`dsh-root 0.2.1-alpha.2`） | 本机 DSH 实现证据 |
| DSH 安装包 | `...\resources\app.asar`（209 MB）+ `app.asar.unpacked` | asar.unpacked 只有原生模块（`libreoffice-kit*`/`node-pty` 等）；JS 全在 asar 内 | 用源码 checkout + asar 字符串扫描交叉验证 |
| myco 现状 | `E:\myco-chat` | ✅ 可读 | §5 对接建议 |

**asar 字符串扫描方法**（可复现）：按 4 MB 分块读 `app.asar`，UTF-8 字节匹配。结果：

| 字符串 | 命中数 | 含义 |
|---|---|---|
| `来源列表已截断` | 2 | 已安装 DSH 含 `WebBlock` 来源截断提示 |
| `未找到结果` | 2 | 同上（空搜索结果态） |
| `ds-markdown-cite`（demo 角标类名） | 0 | 已安装 DSH **不含** demo 的正文角标实现 |
| `citation` | 1373（**误报**） | 逐条看上下文全是 `elicitation` 的子串（DSH 的 ACP elicitation 协议），不能作为 citation 证据 |

> `[本机 asar]` 扫描口径：只做字符串存在性验证，不代表已解包逐文件核对；DSH 的语义证据以本机源码 checkout 为准。

---

## 2. demo 的引用链路（证据 + 代码）

> 本节代码为**节选**：为可读性删去了 TypeScript 类型标注与未相关分支，逻辑与原文一致（原文件路径见附录 A.1）。

### 2.1 数据层：块模型与引用字段 `[demo仓库]`

`src/services/api/types.ts`（Anthropic 兼容 Messages API，`deepseek-flash` 专用）：

```ts
/** 正文引用：服务端把模型的引用标记还原为来源，挂在对应文本块上 */
export type TextCitation = {
  type: 'web_search_result_location'
  url: string
  title: string | null
  /** 当前文本块截至该引用处的全部正文（去除首尾空白） */
  cited_text: string
  encrypted_index: string   // 恒为空串
}
/** 文本块：挂过引用的块之后，后续正文另起新块；同一块内引用按 url 去重 */
export type TextBlock = { type: 'text'; text: string; citations?: TextCitation[] }

export type WebSearchResult = {
  type: 'web_search_result'
  title: string
  url: string
  image_url?: string          // 图搜原站图片，仅保存不展示
  snippet: string             // 可直接展示的网页摘要
  site_icon: string | null    // 站点图标 URL；无法取得注册域名时 null
  site_name: string | null    // 无站点名称时 null
  encrypted_content: string   // AES-256-GCM，客户端解不开
  page_age: string | null     // RFC 3339，无发布时间时 null
}
```

- 引用在流里以 `content_block_delta.delta.type = 'citations_delta'` 到达，`reply.ts` 把它 append 到当前 `text` 块的 `citations` 数组；`text_delta` 累积 `text`。
- 文本块**原样保存、不因展示而改写**（渲染层派生 `[citation:N]`），这是可回传历史消息的前提。

### 2.2 编号与来源登记：`SourceRegistry`（去重/补齐/排序的唯一真源）`[demo仓库]`

`src/services/chat/view.ts`：

```ts
export type Source = {
  url: string
  title: string
  siteName: string          // 优先服务端 site_name，否则域名去 www
  publishedAt: string | null // 搜索结果 RFC3339；阅读链接为原文日期
  snippet: string | null
  siteIcon: string | null
  index: number             // 1-based，正文角标与来源列表共用
}

/** 来源登记：同一 URL 只编号一次，编号按首次出现的顺序 */
class SourceRegistry {
  readonly list: Source[] = []
  private byUrl = new Map<string, Source>()

  add(url, title, publishedAt, metadata?: Pick<WebSearchResult,'snippet'|'site_icon'|'site_name'>): Source | null {
    if (!isWebUrl(url)) return null              // 只接受 http/https
    const siteName = metadata?.site_name?.trim() || getSiteName(url)  // hostname.replace(/^www\./,'')
    const snippet = metadata?.snippet?.trim() || null
    const siteIcon = metadata?.site_icon?.trim() || null
    const existing = this.byUrl.get(url)
    if (existing) {
      // 同一网页可能先被打开，或先返回不完整元数据；后续结果补齐，不覆盖已有正文信息
      if (existing.title === url && title) existing.title = title
      if (existing.siteName === getSiteName(url)) existing.siteName = siteName
      existing.publishedAt ||= publishedAt
      existing.snippet ||= snippet
      existing.siteIcon ||= siteIcon
      return existing
    }
    const source = { url, title: title || url, siteName, publishedAt, snippet, siteIcon,
                     index: this.list.length + 1 }
    this.list.push(source); this.byUrl.set(url, source)
    return source
  }
}
```

`buildReplyView()` 里对编号顺序有明确注释：

```ts
// 来源编号按搜索结果、打开的网页在回复中出现的顺序，与正文引用无关
```

**规则表（可直接移植）**

| 规则 | 行为 |
|---|---|
| 去重键 | **精确 URL 字符串**（不做 canonicalize） |
| 非 http(s) | 直接丢弃（`isWebUrl`） |
| 编号 | `list.length + 1`，首见即定；后续同 URL 复用 |
| 标题补齐 | 已有 title 是占位（=== url）且新 title 非空 → 升级 |
| siteName 补齐 | 已有 siteName 只是裸域名（== hostname 去 www）→ 用服务端 `site_name` 覆盖 |
| 其余元数据 | `||=` 只补空，**从不覆盖**已有 snippet/publishedAt/siteIcon |
| 排序 | 注册顺序 = 搜索结果项顺序 + 打开的网页顺序（按 block 顺序），正文引用不参与 |

### 2.3 正文角标注入：`joinTextBlocks`（在块尾，不是句内）`[demo仓库]`

```ts
/**
 * 文本块拼成一份 markdown：服务端在挂过引用的块之后另起新块，引用位置即块尾。
 * 引用之后的空白（含换行）仍留在被引用的块里，所以 [citation:N] 插在块尾空白之前。
 */
const joinTextBlocks = (blocks, registry) => {
  let markdown = '', text = ''
  for (const block of blocks) {
    text += block.text
    const indexes = new Set<number>()
    for (const citation of block.citations ?? []) {
      const source = registry.add(citation.url, citation.title, null)
      if (source) indexes.add(source.index)
    }
    const body = indexes.size > 0 ? block.text.trimEnd() : block.text
    markdown += body
    for (const index of indexes) markdown += `[citation:${index}]`
    markdown += block.text.slice(body.length)
  }
  return { markdown, text }
}
```

要点：
- marker 插在**块尾、结尾空白之前**；因为服务端"挂过引用的块之后另起新块"，块尾就是被引句子结尾 → 视觉位置正确。
- 同一块内同 URL 多次引用 → `Set` 只出一个角标；多个来源按 `citations` 数组顺序依次追加。
- `text` 保留原始文本（**复制/回传不带 marker**）；`markdown` 只用于渲染。

### 2.4 解析：`[citation:N]` → 角标节点 `[demo仓库]`

`src/services/markdown/parse.ts`：

```ts
if (sources) {
  const sourcesByIndex = new Map(sources.map(s => [s.index, s]))
  findAndReplace(ast, [
    /\[citation:(\d+)\]/g,
    (_: string, index: string): CitationNode | '' => {
      const source = sourcesByIndex.get(Number(index))
      return source ? { type: 'citation', source } : ''   // 找不到来源：整个 marker 删除
    },
  ], { ignore: ['linkReference'] })
}
```

- 未知编号**静默删除**，不留空角标。
- `code` 块内不做替换？`findAndReplace` 会跳过 `code`/`inlineCode`（mdast 默认不进入不可替换节点）；[推断] 这个行为符合预期，未单独验证。

### 2.5 渲染：`CitationLink` = 角标 + 悬浮卡 `[demo仓库]`

`src/components/search/CitationLink.tsx`：

```tsx
export const CitationLink = ({ source }: { source: Source }) => {
  const open = (event) => {
    event.preventDefault(); event.stopPropagation()
    window.open(source.url, '_blank', 'noopener,noreferrer')
  }
  const renderTrigger = ({ domRef, domProps }) => (
    <span ref={domRef} className="ds-markdown-cite" {...domProps} role="link" onClick={open}>
      {source.index}
    </span>
  )
  return <HoverCard renderTrigger={renderTrigger}><SourceCard source={source} /></HoverCard>
}
```

`src/components/search/HoverCard.tsx`：`ENTER_DELAY = 300ms`、`LEAVE_DELAY = 100ms`、`GAP = 6px`，`createPortal` 到 `body`，`useDelayedOpen` + `useAnchoredPosition({ align: 'center', offset: 6 })`；**鼠标可以移入卡片**（`onMouseEnter={cancel}`）→ 这是桌面 hover 行为，与移动端无关。

角标样式 `src/components/markdown/markdown.css`（`.ds-markdown-cite`）：

```css
.ds-markdown-cite{
  position:relative; top:-5px; height:14px; padding:0 4px; min-width:14px;
  font-variant:tabular-nums; border-radius:6px 6px 6px 0;          /* 左下角直角 */
  box-shadow: inset 0 0 0 .5px var(--dsw-alias-border-l1);
  box-sizing:border-box; user-select:none; white-space:nowrap;
  background: var(--dsw-alias-markdown-placeholder);
  font: 500 9px/14px var(--ds-font-family-code);
  color: var(--dsw-alias-label-tertiary);
  display:inline-flex; justify-content:center; align-items:center; flex-shrink:0;
  margin:0 2px; cursor:pointer;
  + .ds-markdown-cite{ margin-left:0; }                            /* 相邻角标贴合 */
}
.ds-markdown-cite:hover{ background: var(--dsw-alias-markdown-citation); }
```

主题值 `src/app/theme.css`（浅/深）：

| 令牌 | 浅色 | 深色 |
|---|---|---|
| `--dsw-alias-markdown-placeholder`（角标底） | `rgb(245,246,247)` | `rgb(44,44,46)` |
| `--dsw-alias-markdown-citation`（hover 底 / 图标底） | `rgb(235,238,242)` | `rgb(53,54,56)` |
| `--dsw-alias-label-tertiary`（角标字） | `rgb(129,133,140)` | `rgb(173,178,184)` |
| `--dsw-alias-border-l1`（0.5px inset 描边） | `rgba(0,0,0,.04)` | `rgba(255,255,255,.06)` |

### 2.6 来源卡片 `SourceCard`（悬浮卡 / 面板内共用）`[demo仓库]`

```tsx
export const SourceCard = ({ source, className }) => {
  const date = source.publishedAt ? (formatDate(source.publishedAt, '/') ?? source.publishedAt) : ''
  return (
    <a className={styles.anchor} href={source.url} target="_blank" rel="noreferrer">
      <div className={clsx(styles.card, className)}>
        <div className={styles.meta}>
          <SiteLogo url={source.siteIcon} className={styles.siteIcon} />
          {source.siteName ? <span className={styles.siteName}>{source.siteName}</span> : null}
          {source.siteName && date ? <span className={styles.divider} /> : null}
          {date ? <span className={styles.date}>{date}</span> : null}
        </div>
        <div className={styles.title}>{source.title}</div>
        {source.snippet ? <div className={styles.snippet}>{source.snippet}</div> : null}
      </div>
    </a>
  )
}
```

尺寸（`SourceCard.module.css`，桌面基础值；`SearchPanel` 里用 CSS 变量收紧）：

| 元素 | 基础值（悬浮卡） | 面板内覆盖 |
|---|---|---|
| meta 行 | `gap:6px`，`500 12px/17px` | `13px`，`padding-top:2px` |
| siteIcon | `18×18`，圆形，底 `markdown-citation`；失败/无图标回退链接图标（`70%`） | 同 |
| siteName | 单行省略，`label-secondary` | `label-tertiary` |
| 分隔线 | `1×12px`，`border-l3` | 同 |
| 日期 | `YYYY/MM/DD`；解析失败原样显示 | 同 |
| title | `margin-top:6px`，`500 16px/25px`，最多 2 行 | `500 14px/22px` |
| snippet | `margin-top:6px`，`13px/20px`，最多 2 行，secondary | tertiary |

- 整卡是 `<a target="_blank" rel="noreferrer">` → **点击 = 新窗口打开**（无 JS 处理、无应用内浏览器、无"复制链接"）。
- `SiteLogo`（`src/components/search/SiteLogo.tsx` + `FallbackImage.tsx`）三态：`loading` 骨架（`markdown-citation` 底）→ `loaded` 显示图 → `error`（含 `src` 为空）显示链接图标。
- 摘要里的 `SiteThumbnails`：前 **4** 个站点图标，`16×16`、圆形、`margin-left:-4px` 重叠、外圈 1px 底色描边。

### 2.7 来源面板 `SearchPanel`（桌面右栏 / 移动全屏）`[demo仓库]`

- `src/components/search/SearchPanel.tsx`：标题「搜索结果」+ 关闭按钮；内容按 `sources` 顺序渲染 `SourceCard`（**卡片上不显示编号**，编号只在正文角标上）。
- `SearchPanel.module.css`：桌面 `width: calc(50vw - 4rem); max-width: 390px; height:100%; border-left`；header 高 `60px`；内容 `padding:8px`；卡片 `padding:12px; border-radius:12px; margin-bottom:6px`，hover 背景 `interactive-bg-hover`；`<768px` 时 `position:fixed; inset:0; z-index:700; width:auto`（全屏覆盖）。
- 状态 `searchPanelState.ts`：`toggleSearchPanel(entry, sources)` —— **同一入口再点收起，换入口替换内容**；`entry` 是入口标识（搜索行 / 消息底部 summary / "查看全部"）。

**触发入口（3 个）**：

1. 工具步骤行「已阅读 N 个网页」+ 缩略图（`ReplyArea.tsx` 的 `SearchRow`）；仅当 `!wip && count>0` 可点。
2. 消息底部 summary（`AssistantMessage.tsx`）：**仅 `!streaming && view.searchResults.length>0`** 时出现，缩略图 + 「N 个网页」（`messageSearchSummary/Summaries`），点击开面板，面板打开时该行高亮。
3. 打开网页行超过 5 个时的「查看全部」chip（用该 step 的 pages 过滤 sources 后开面板）。

### 2.8 工具步骤行（搜索/浏览/查找/阅读链接）`[demo仓库]`

`ReplyArea.tsx` 的 `SearchRow`（普通搜索 / 深搜 / 图搜共用）：

| step 状态 | 行内容（zh） | 可点 |
|---|---|---|
| `wip`（有 query） | 「搜索 {query}」，多 query 每 **2s** 轮播 | 否 |
| `wip`（无 query，图搜） | 「图像搜索中...」 | 否 |
| failed + 用户已停止 | 「搜索已停止」 | 否 |
| failed | 「联网搜索暂不可用」 | 否 |
| done + 0 结果 | 深搜「未搜索到合适内容」/ 普通「未搜索到相关内容」 | 否 |
| done + N>0 | 深搜「搜索到 N 个网页」/ 普通「已阅读 N 个网页」+ 前 4 站点缩略图 | 是 → 面板 |

聚合规则（`buildReplyView`）：普通搜索按 `kind` **合并到首次出现的那一行**（多次搜索 = 一行 N 个网页）；深搜/图搜只合并**相邻且同类**的调用；`status` 合并为 `wip > failed(全失败) > done`。`read_link` 只在进行中显示，成功后整行消失。

### 2.9 流式时序与重排（结论表）

| 阶段 | 发生什么 | 用户看到 |
|---|---|---|
| 1 | `server_tool_use` + `web_search_tool_result` **原子到达**（块一次性完整） | 搜索行出现；来源已注册、**编号此刻已定** |
| 2 | `text` 块 `text_delta` 逐步到达 | 正文流式增长（此时还没有角标） |
| 3 | 该块 `citations_delta` 到达 | `buildReplyView` 在**该块尾部**注入 `[citation:N]` → 角标出现 |
| 4 | 服务端在挂引用的块后另起新块，继续正文 | 角标后继续出字，结构不跳动 |
| 5 | `message_stop` / 停止/出错 | 工具行收尾；底部「N 个网页」summary 出现（仅非流式）；**不重排、不重新编号** |

- `buildReplyView` 是纯函数，每次渲染从 `message.blocks` 重算；编号 append-only，所以天然稳定。
- `useReplyView.ts` 专门做了 `sameSource` 比较：**来源数组没变时复用旧数组**，避免流式每个 token 都绕过 Markdown 的解析节流（`useThrottleMarkdownRender`）。
- 停止/出错时保留已收到的块与来源（`settleReply` 只在 `stop_reason==='refusal'` 时丢弃 thinking/text）。
- 复制按钮复制 `answer.text`（原始文本）→ **复制内容不含 `[citation:N]`**。

### 2.10 失败/空/安全边界

- 搜索失败：行显示「联网搜索暂不可用」；用户主动停止时显示「搜索已停止」（区分"失败"与"我停的"）。
- 空结果：行内一句「未搜索到合适内容」，不开面板。
- 只有 http(s) 才渲染成可点链接 / 交给 `window.open`；`javascript:`/`data:`/`file:`/不可解析字符串一律纯文本。
- 无 title 时用 hostname（去 www）做 label；hostname 为空时回退原始 URL（保证 label 非空）。
- 来源列表**不折叠**：`SearchPanel` 展示全部（滚动），正文角标不受列表长度影响。

---

## 3. 本机 DSH 的对照实现（WebBlock 卡片，无正文角标）`[本机 DSH 源码]`

证据：`packages/client/ui-primitives/src/WebBlock.tsx`、`WebBlock.module.css`、`tests/web-block.client.spec.tsx`、`packages/client/ui-tool/src/client/tool/models/web-card-model.ts`、`.../components/ToolRow.tsx:307-308`、`packages/client/ui-conversation/src/client/locales.ts:350-351`；安装包 asar 字符串交叉验证（§1）。

| 维度 | DSH `WebBlock` | chat demo |
|---|---|---|
| 落点 | `web_search` **工具行展开后**的卡片（`ToolRow` 的 `expandedContent`） | 正文角标 + 来源面板 |
| 正文角标 | **没有** | 有 `[citation:N]` 角标 |
| 编号 | 来源列表 `<li value={n}>`，1-based 显式编号；无按钮/无展开 | 角标显示编号；面板卡片**不显示编号**（仅顺序对应） |
| 字段 | `url/title?/snippet?/publishedAt?`（**无 siteName/siteIcon**） | `url/title/siteName/publishedAt/snippet/siteIcon` |
| 图标 | 无 favicon；链接前是通用 `LinkIconMedium`（已知站点用站点 mark，未知用地球） | 站点 favicon 圆形 18px，失败回退链接图标 |
| 布局 | 卡：`margin:16px 0; padding:12px 14px; radius`；answer 在上，来源 `<ol>` 在下；`max-height:320px; overflow-y:auto`（原地滚动） | 悬浮卡 355px；面板滚动 |
| 折叠 | **整列表都渲染**，只有 max-height 滚动；**无展开按钮、无 head/tail 折叠**（测试明确断言 `[aria-expanded]` 不存在） | 不折叠 |
| 跳转 | `target="_blank" rel="noopener noreferrer"`；非 http(s) 渲染 `<span>` | 同（badge 用 `window.open`） |
| 截断 | `truncated` → 「来源列表已截断」；fetch 卡「内容已截断」 | 无截断概念（搜索结果全展示） |
| 空态 | 「未找到结果」 | 搜索行一句话，无卡 |
| 数据源 | 工具结果 `meta = {answer?, sources[], truncated}`（`web-card-model.ts:58-82` 严格校验） | 服务端 `web_search_tool_result` + `text.citations` |

**结论**：如果用户说的"chat demo"指 DSH Web GUI，则它**没有**正文引用，只有卡片；用户描述的"更好的网络搜索引用"更符合官方 chat-demo 的行为。两者可以互补：DSH 的"卡片 + 显式编号列表 + 截断提示"适合做**工具结果区**，demo 的"正文角标 + 悬浮/点击 + 来源面板"适合做**正文引用区**。

---

## 4. 可照抄的行为规格（Compose 视角）

> 尺寸换算：demo CSS 的 px 在 Android 上建议按 1px≈1dp 起步再微调；角标字号 9px 直接搬会偏小，下面给 `dp/sp` 建议值。颜色用 demo 令牌名，落到 myco 的 `MaterialTheme` 语义色。

### 4.1 数据契约（建议新增/扩展）

```kotlin
/** 一条可被引用的来源；index 由 SourceRegistry 分配，正文角标与来源列表共用 */
data class WebSource(
    val url: String,                 // 仅 http(s) 才允许进入
    val title: String,               // 空标题时 = url
    val siteName: String = "",       // 服务端 site_name，否则 hostname.removePrefix("www.")
    val snippet: String? = null,     // 明文摘要（demo 用 snippet；myco 搜索 API 另有 summary）
    val publishedAt: String? = null, // 原样字符串；显示层格式化为 yyyy/MM/dd
    val siteIcon: String? = null,    // favicon URL；null → 链接图标兜底
    val index: Int = 0,              // 1-based；0 表示未登记（不应渲染）
)
```

登记器（纯函数，放 `core` 或 `ui` 均可，建议 `core` 便于测试）：

```kotlin
class SourceRegistry {
    private val byUrl = LinkedHashMap<String, WebSource>()
    val list: List<WebSource> get() = byUrl.values.toList()

    fun add(url: String, title: String?, publishedAt: String?,
            snippet: String? = null, siteName: String? = null, siteIcon: String? = null): WebSource? {
        if (!isWebUrl(url)) return null
        val host = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull().orEmpty()
        val existing = byUrl[url]
        if (existing != null) {
            return existing.copy(  // 或就地可变；补齐但不覆盖
                title = if (existing.title == url && !title.isNullOrBlank()) title else existing.title,
                siteName = if (existing.siteName == host) (siteName?.trim().orEmpty().ifEmpty { existing.siteName }) else existing.siteName,
                publishedAt = existing.publishedAt ?: publishedAt,
                snippet = existing.snippet ?: snippet,
                siteIcon = existing.siteIcon ?: siteIcon,
            ).also { byUrl[url] = it }
        }
        val src = WebSource(url, title?.ifBlank { null } ?: url, siteName?.trim().orEmpty().ifEmpty { host },
                            snippet?.trim()?.ifEmpty { null }, publishedAt, siteIcon, byUrl.size + 1)
        byUrl[url] = src
        return src
    }
}
```

### 4.2 正文角标规格

| 属性 | demo 值 | Compose 建议 |
|---|---|---|
| 形态 | 上标小圆角方块（左下直角） | `InlineTextContent` 里的 `Box`：圆角 `6.dp,6.dp,6.dp,0.dp` |
| 字号/行高 | `500 9px/14px` mono | `10.sp`，`lineHeight 14.sp`，`FontWeight.Medium`，`FontFamily.Monospace` |
| 盒 | `height 14px`、`padding 0 4px`、`min-width 14px` | `height 15.dp`、`padding(horizontal = 4.dp)`、`defaultMinSize(minWidth = 15.dp)` |
| 位置 | `position:relative; top:-5px`（上标） | `baselineShift` 或者用 `InlineTextContent` + `Typography` 的 `baselineOffset`；简单做法：角标容器 `Modifier.offset(y = (-4).dp)` |
| 底色 | `markdown-placeholder`（浅 `#F5F6F7` / 深 `#2C2C2E`） | `surfaceVariant.copy(alpha=.6f)` 或新增令牌 |
| 文字色 | `label-tertiary`（浅 `#81858C` / 深 `#ADB2B8`） | `onSurfaceVariant` |
| 描边 | `inset 0 0 0 .5px border-l1` | `Box(border = BorderStroke(.5.dp, outlineVariant))` |
| 间距 | `margin 0 2px`，相邻角标 `margin-left:0` | 角标前后用 `appendText(" ")` 或固定 2.dp 间隔；相邻连续角标不要双倍间距 |
| hover | 背景变 `markdown-citation` | 省略（触摸无 hover）；按压态用 `interactionSource` 变色 |
| 点击 | `window.open(url, '_blank', 'noopener,noreferrer')` | `LocalUriHandler.openUri(url)` 或 Custom Tabs；**不要**在点击时校验失败/静默（失败给 Snackbar） |
| 长按 | 无（桌面靠 hover） | 建议扩展：长按弹 `Popup`/底部 sheet 显示来源卡（demo 行为在触屏不可达，属于合理补全） |
| 无障碍 | `role="link"` | `semantics { role = Role.Link; contentDescription = "第 $index 个来源：$title" }`；命中区至少 24dp（`minimumInteractiveComponentSize()` 更好） |
| 键盘/焦点 | `:focus-visible` 外圈 `box-shadow: 0 0 0 2px brand` | Compose 焦点指示器/外接键盘场景 |

**marker 约定**：文本里保留 `[citation:N]` 字样（例如 `"…结论如此。[citation:1][citation:3]"`），渲染前替换为角标；`N` 对应 `WebSource.index`；找不到对应来源直接删除 marker。建议**只在 UI 派生**，不要写回数据库/消息文本。

### 4.3 来源卡片规格（Compose）

```kotlin
@Composable
fun SourceCard(source: WebSource, compact: Boolean = false, onClick: () -> Unit)
```

| 区域 | 规格 |
|---|---|
| 卡片 | `Column`，`padding(12.dp)`，`RoundedCornerShape(12.dp)`，`clickable { onClick() }`；面板内 `margin-bottom 6.dp`，按压/按下背景 `interactive-bg-hover` |
| meta 行 | `Row(verticalAlignment = CenterVertically, horizontalArrangement = spacedBy(6.dp))` |
| 站点图标 | `Box(18.dp).clip(CircleShape)`，`AsyncImage(siteIcon)`：加载中骨架底、失败/空 → 链接图标（14dp） |
| 站点名 | `bodySmall`（12–13sp/17–20sp），`onSurfaceVariant`，`maxLines=1, overflow=Ellipsis`，`weight(1f, fill=false)` |
| 分隔线 | `Box(1.dp × 12.dp)`，`outlineVariant`；两端 `3.dp` 间距 |
| 日期 | `publishedAt` 格式化 `yyyy/MM/dd`；解析失败原样显示；`maxLines=1` |
| 标题 | `margin(top=6.dp)`；`16.sp/25.sp`（面板内 `14.sp/22.sp`），`FontWeight.Medium`，`maxLines=2, Ellipsis` |
| 摘要 | `margin(top=6.dp)`；`13.sp/20.sp`，`onSurfaceVariant`，`maxLines=2, Ellipsis`；null/空 → 不占位 |
| 点击 | 整卡点击 → 打开浏览器（仅 http(s)；否则纯文本、无点击） |

### 4.4 来源面板 / 底部 sheet 规格

| 入口 | 行为 |
|---|---|
| 搜索步骤行「已阅读 N 个网页」+ 缩略图 | 点击 → 打开面板 |
| 消息底部「N 个网页」summary | 仅**流式结束后**出现；点击 → 面板；打开时高亮 |
| 「查看全部」chip | 打开面板（内容 = 该 step 命中的来源） |
| 关闭 | 关闭按钮 / 点遮罩 / 返回键；同一入口再点 = 收起（toggle） |

- 桌面 demo 是右侧栏（`max-width 390px`）；**Android 用 `ModalBottomSheet`**（全屏或 `half` 展开），header「搜索结果」+ 关闭。
- 列表 `LazyColumn`：顺序 = `registry` 顺序 = 正文角标编号顺序；**不分组、不折叠**。
- **建议 myco 在面板卡片上显式显示编号**（如左侧 `1`），因为 demo 的"对应"只靠顺序，触屏没有 hover 预览时用户很难把角标和卡片对上；DSH 的 `<li value>` 做法可借鉴。这是相对 demo 的小改进，需产品确认。

### 4.5 搜索步骤行 + 底部 summary（照抄 demo 的"摘要感"）

| 状态 | 文案（zh，可直接用） | 附加 |
|---|---|---|
| 进行中（有 query） | `搜索 {query}`（多 query 2s 轮播） | shimmer/ticker |
| 进行中（无 query） | `搜索中...` / `图像搜索中...` | — |
| 失败（非用户停止） | `联网搜索暂不可用` | 警告图标 |
| 用户停止 | `搜索已停止` | — |
| 成功 0 结果 | `未搜索到合适内容` | — |
| 成功 N 结果 | `已阅读 N 个网页` | 前 4 个站点缩略图（16dp、重叠 -4dp、1px 描边） |
| 深搜 N 结果 | `搜索到 N 个网页` | 同 |
| 消息底部 | `N 个网页` | 缩略图；仅完成后显示 |

### 4.6 流式与数据流规则（Compose 实现约束）

1. **状态里存原始块**（text/citations/tool results 分离），UI 用 `derivedStateOf`/`remember` 计算 `view`；**不要**把 `[citation:N]` 写进持久化文本。
2. 编号 append-only：来源只追加、不重排；新来源到达时只影响之后的 marker。
3. 角标出现时机 = 该文本块的 citations 到达；如果后端只能"完成后一次性给 citations"，就退化成"完成后角标出现"，这必须提前接受（见 §5.4 路线 A 的限制）。
4. 解析节流：长回答每个 token 全量重解析会掉帧；沿用现有 `MarkdownText` 的增量策略，marker 替换只对新增文本做，或整段按 ≥50ms 合帧。
5. 复制：复制**原始正文**（不带编号）；如果要"复制带引用"，另做"复制 Markdown"，明确写进交互文档。
6. 来源数组未变化时保持引用相等（`remember` key 用 `sources.hashCode()` 或 registry 版本号），避免滚动列表反复重组。

### 4.7 边界与安全清单

- [ ] 只有 `http://`/`https://` 可点；`javascript:`/`data:`/`file:`/无法解析 → 纯文本。
- [ ] 未知 `[citation:N]` → 删除 marker，不留空角标/不崩溃。
- [ ] 无 title → 站点名或 hostname；hostname 为空 → 原始 URL；永不为空 label。
- [ ] 非链接文本照常可选中（角标 `user-select:none` → Compose 不参与 SelectionContainer 的文本复制；如要复制角标编号需另处理）。
- [ ] 跳转失败不静默（demo 是 `window.open`，没有失败反馈；Android 上 `openUri` 抛异常要 catch + 提示）。
- [ ] 来源面板打开时列表很长 → `LazyColumn`；不要一次性 `Column` 全量渲染。
- [ ] 深色主题下角标底/字色走反色令牌（§4.2 表）。
- [ ] TalkBack：角标读"第 N 个来源：标题"；面板每张卡读"站点、日期、标题、摘要"。

---

## 5. 与 myco 现状的对接建议

### 5.0 现状快照（注意到并发修改）

> 调研期间 `app-android/src/main/kotlin/dev/myco/android/ui/ChatContent.kt` 从 783 行变到 814 行（有其他 agent 正在改这个文件）。本节行号以 **2026-11-26 ~15:55** 的快照为准，数据类/字段名以正文为准；合入前请以最新代码复核。

- `TimelineEntry`：`app-android/.../ui/ChatContent.kt:57-65`，`sources: List<SourceRef>` 挂在**工具行**上。
- `SourceRef(title, url, site)`：`ChatContent.kt:55` —— **只有 3 个字段**。
- `parseSources(raw)`：`ChatContent.kt:68-77`，从 `web_search` 工具结果 JSON 的 `results[]` 取 `title/url/site`。
  - 但后端产出**已含更多字段**：`core/.../capability/CapabilityTools.kt:57-71` 写入 `{title,url,snippet,summary?,site?,date?}`；`DeepSeekToolsClient.kt:91-103` 从 Search API 读 `site_name/date/snippet/summary/rerank_score`（**未读 `site_icon`**）。→ `snippet/date/summary` 现在是**拿到了但被 UI 丢弃**。
- `turnSources: Map<Long, List<SourceRef>>`：`ChatContent.kt:79-86`，在 `tool/result` 聚合时按 url 去重（`ChatContent.kt:158-162`，只保留首见，无补齐规则）。
- 渲染：
  - 工具折叠区里 `entry.sources.take(6)` 逐条渲染（编号是显示位置 `i+1`）：`ChatContent.kt:638-660`；
  - 消息尾部 `SourcesSection(sources)`：`ChatContent.kt:533-536` → 实现 `ChatContent.kt:554-579`（标题「来源 · N」+ `i+1` 编号 + title + site/url，整行 `LocalUriHandler.openUri`）。
  - 正文：`MarkdownText` → `inlineAnnotated`（`ChatContent.kt:331-389`），已支持 `**`/行内码/斜体/`[label](url)`（`withLink(LinkAnnotation.Url(url))`，`:369-383`），**没有**角标概念。
- 实时流：`MainActivity.kt:654-661` 在 `ToolFinished` 时对 `web_search` 调 `parseSources`，挂到 live `TimelineEntry.sources`。
- 后端形态：myco 目前走 **OpenAI 兼容 chat + 客户端工具**（`web_search` 是独立 Search API `/v1/tools/search`）；仓库内 `citations` 在 Kotlin 侧 **0 命中**，即**当前没有服务端 citations 协议**。
- 已有决策缺口（现成文档）：`docs/recon/backend-feature-gaps.md:283`（服务端 web tools vs 独立 Search API 的取舍）、`:386`（"是否接 DeepSeek 服务端 web tools" 待产品决定）；API 事实见 `docs/recon/deepseek-api.md:120`（Web Search 声明 `deepseek_web_search` + 关思考，带 citations 作答）、`:127-129`（result 块/签名回传/计费）。

### 5.1 字段缺口

| demo 字段 | myco 现状 | 动作 |
|---|---|---|
| `url` | ✅ `SourceRef.url` | — |
| `title` | ✅ | 空标题回退 url |
| `siteName` | 🟡 `site`（capability 里已把 `site_name` 改名 `site`） | 语义保留，改名/加注释避免与 URL site 混淆 |
| `snippet` | ❌ 被 `parseSources` 丢弃（JSON 里有） | 加字段 + 解析 |
| `publishedAt` | ❌ 被丢弃（JSON 里 `date`） | 加字段 + 解析；显示 `yyyy/MM/dd` |
| `siteIcon` | ❌ 客户端没读 `site_icon` | `DeepSeekToolsClient.SearchHit` 加 `siteIcon`，capability JSON 加 `site_icon`；MVP 可先不做图标（用字母/链接图标兜底） |
| `index` | ❌ 显示时 `i+1` | **改为 registry 分配**，角标/列表/面板统一用它 |
| （额外）`summary` | 🟡 JSON 里有、未用 | 可选：来源卡片摘要优先 `snippet`，没有时用 `summary.take(200)` |

### 5.2 纯客户端可先做的（不依赖协议变更，建议 P0）

1. **扩展 `SourceRef`**：`+ snippet/publishedAt/siteIcon/index`；`parseSources` 解析 `snippet/date`（可选 `summary`）。
2. **引入 `SourceRegistry`**（§4.1），替换 `ChatContent.kt:158-162` 的"首见去重"：编号一致、元数据补齐、`SourceRef.index` 从此可信。
3. **重写 `SourcesSection`** → `SourceCard`（§4.3）：站点名/日期/标题/两行摘要/图标；编号用 `source.index` 而不是 `i+1`；标题定「来源 · N」。
4. **工具行瘦身**：`TimelineFold` 里 `take(6)` 的卡片**移出折叠区**，工具行只留「已阅读 N 个网页」+ 前 4 缩略图（demo `SearchRow` 的做法）；完整列表进 `SourcesSection` 或底部 sheet。理由：工具行本来就是"过程"，来源是"结果"，demo 与 DSH 都不把长卡片铺在过程行里。
5. **底部 summary + sheet**：消息完成后渲染「N 个网页」行，点击弹 `ModalBottomSheet`（§4.4）。
6. **安全**：`openUri` 前校验 http(s)；失败 catch + 提示（现有 `runCatching{}` 静默失败，建议改）。
7. **复制**：确认复制的是原始正文；如果产品要"复制带来源"，单独加 action（不做进主复制）。

### 5.3 正文角标：两条路线（关键取舍）

**路线 A：保持客户端工具（现状），用文本里的链接反推角标（P1，可做但保真度有限）**

- 现状：模型被要求"回答时引用 URL"（`CapabilityTools.kt:38` 的 tool description），正文里通常会出现 `[标题](https://...)` 或裸 URL；`inlineAnnotated` 已能识别 markdown 链接。
- 做法：渲染时若 link 的 url ∈ `turnSources`，在链接尾部追加角标 `InlineTextContent`，编号 = source.index；消息尾部 `SourcesSection` 用同一编号 → 角标与列表能对上。
- 限制（必须写进验收）：
  - 位置由模型决定，**不能保证"钉在被引句子后"**；模型不写链接就没有角标。
  - 无 `citations_delta`，因此没有"引用文本（cited_text）"、无法做"引用片段高亮"。
  - 裸 URL 需要正则识别并转换成可点链接（当前 `inlineAnnotated` 只认 `[label](url)`）。
  - 需要防误配：URL 归一化（去 `#fragment`/尾斜杠）后再匹配，避免同页不同锚点算两个来源。
- 成本：**低**，1 个 UI 文件 + 1 个解析函数 + 字段扩展。

**路线 B：接 DeepSeek 服务端 web tools 拿原生 citations（P2，保真度最高）**

- 前置：切 Anthropic 兼容 Messages API（`/anthropic/v1/messages`），声明 `{"type":"deepseek_web_search","name":"deepseek_web_search"}`（**仅 deepseek-flash**；普通搜索需关思考，开思考=边想边搜）。参考 `docs/recon/deepseek-api.md:120-129`。
- 数据层：落 `blocks`（thinking/text/server_tool_use/web_search_tool_result/web_fetch_tool_result）+ `text.citations`；历史回传规则（只回传 read_link、结果块带 signature）照 demo。
- UI 层：完全照抄 §2.2–§2.9（registry → 块尾 marker → 角标 → 面板 → 搜索步骤行）。
- 代价：协议/存储/上下文回传改动大；`deepseek-flash` 限定；按 token 计费不可预估（`docs/recon/backend-feature-gaps.md:283,386` 已列为待决）。
- 建议：**P1 走路线 A 交付"可跳转的来源 + 编号一致"；P2 再评估路线 B**。在路线 A 落地时，数据层按路线 B 的 `WebSource` 形状设计（字段对齐），避免之后二次迁移。

### 5.4 分阶段改动清单（文件级，供排期）

| 阶段 | 改动 | 文件 |
|---|---|---|
| P0 | `SourceRef` 加字段 + `parseSources` 解析 snippet/date | `app-android/.../ui/ChatContent.kt` |
| P0 | `SourceRegistry`（纯函数 + 单测） | `core/...`（新文件，如 `capability/WebSources.kt`） |
| P0 | tool/result 聚合改用 registry；turnSources 编号稳定 | `ChatContent.kt:151-169` |
| P0 | `SourcesSection` → `SourceCard`（§4.3）；工具行只留摘要 | `ChatContent.kt:533-660` |
| P0 | 完成后「N 个网页」行 + `ModalBottomSheet` 来源面板 | 新 `ui/SourcesSheet.kt` + `ChatContent.kt` |
| P0 | http(s) 校验 + 打开失败提示 | `ChatContent.kt` |
| P1（路线 A） | `inlineAnnotated` 支持 `[citation:N]` → `InlineTextContent` 角标；链接命中 sources 时追加角标 | `ChatContent.kt:331-389` |
| P1 | 长按角标 → 来源卡 Popup（触屏补全） | 新 `ui/CitationBadge.kt` |
| P2（路线 B） | 服务端 web tools 协议 + blocks/citations 模型 + 搜索步骤行/深搜 | `core` 协议层 + bridge 事件 + UI |

### 5.5 与现有 `TimelineEntry.sources` 的兼容

- **保留字段名 `sources`**，类型从 `List<SourceRef>` 扩展即可（Kotlin data class 加默认值不会破坏现有构造点；`MainActivity.kt`/`ChatContent.kt` 的解析处需要补参数）。
- `TimelineEntry` 是"过程"数据，天然只挂 tool 行；**正文角标不要塞进 `TimelineEntry`**——它属于 assistant message 的渲染派生数据，建议放一个 `CitationView`/`SourceRegistry` 结果，key 用 `turn`（现有 `turnSources` 正好可复用，只需把值升级为带 `index` 的 `WebSource`）。
- 现有 `turnSources` 的 key 是 `turn: Long`，与 `TimelineEntry` 一致；UI 侧 `ChatMessages` 已经能按 turn 取（`ChatContent.kt:535`），改成 registry 结果不需要改 LazyColumn 结构。
- 如果并发修改已经把 `SourcesSection` 重命名/删除，**以新实现为准，只搬 §4 的规格**，不要回滚别人的改动。

---

## 6. 未验证 / 存疑（不要当结论用）

1. **本机没有 demo 源码副本**：本节 `[demo仓库]` 证据全部来自 jsDelivr 对 GitHub `@main` 的镜像；`api.github.com` 直连在本机失败，**无法固定 commit**。文件树 API 给出的内容指纹见附录，复核时先比 hash。
2. **未在浏览器里真实打开 demo 看渲染**（无浏览器自动化/截图）：像素值来自 CSS 原文，未做真机对照；角标"上标"的实际基线偏移需真机确认。
3. **移动端触摸行为是代码推断**：demo 的 `HoverCard` 只绑 `mouseenter/mouseleave`，没有 touch 处理 → 触屏上"悬浮预览"不可达，点击角标直接 `window.open`；Android 上 `window.open` 等价"打开系统浏览器"。
4. **demo 来源面板卡片不显示编号**：正文角标与面板的"对应"只是**顺序对应**，没有显式数字（`SourceCard.tsx` 无 index 渲染）。照抄时是否给 myco 面板加编号是产品决策（§4.4 建议加）。
5. **DSH 安装包未逐文件解包**：asar 内 JS 打包，只用源码 checkout（0.2.1-alpha.2）+ asar 字符串交叉验证；若运行中的 DSH 版本与源码 checkout 不同，`WebBlock` 细节可能有差异（但"卡片、无正文角标"这个结论有源码与字符串双重支撑）。
6. **myco `ChatContent.kt` 在调研期间被并发修改**（783→814 行）：§5 行号会漂移，字段/函数名以最新代码为准。
7. **"用户说的 chat demo"存在歧义**：若指 DSH Web GUI，则它没有正文引用（§3）；若指 `deepseek-ai/chat-demo`，则是 §2。本报告按后者写规格，前者作对照。
8. **`utils/url.ts` 与 jsDelivr 文件树不一致**：文件树 API 列出的 `src/utils/` 只有 clipboard/format/id，但 `src/utils/url.ts` 实际可读（`view.ts` 也 import 它）→ 说明 jsDelivr 目录元数据有缓存滞后，**以具体文件内容为准**。

---

## 附录 A：证据清单

### A.1 chat-demo 源码（jsDelivr 镜像，`@main`，2026-11-26 读取）

| 文件 | 作用 | 内容指纹（SRI base64 SHA-256） |
|---|---|---|
| `src/services/chat/view.ts` | `Source`/`SourceRegistry`/`joinTextBlocks`/`buildReplyView`（编号与视图核心） | `d0GoALTZfMmFMmNNUgF8qCgFr9WDJAWJSMjC8/fpBC8=` |
| `src/services/api/types.ts` | `TextCitation`/`WebSearchResult`/`server_tool_use` 协议类型 | `Fp+M6A/jItFddynGzOn4tKL57kGbeJhaHALp83lNFCc=` |
| `src/services/chat/types.ts` | `ReplyBlock`/`TextPart.citations`/`AssistantMessage` | `Y2OREzHxlH4G1RwcMPUP3VUHSM+sBy5TqNw9UbskTJg=` |
| `src/services/chat/reply.ts` | `applyEvent`（`citations_delta` 累积）、`settleReply` | `qMho4ZtulcElCXprv4Dd32YSzOVqfoqqyYIz16KumGs=` |
| `src/services/markdown/parse.ts` | `[citation:N]` → `citation` 节点（未知编号删除） | `al/pZkRxV1xP/ORFo8OFvaoDkyxUeLVb82KzWJES/rA=` |
| `src/components/markdown/Markdown.tsx` | 节流 → AST → 渲染，传 `sources` | `Eo52PdytZEdKABlePUHXRzJfQiWjGHelrTUEOAe0bo0=` |
| `src/components/markdown/MarkdownAstRenderer.tsx` | `case 'citation'` → `<CitationLink>` | `TifVUt1NwA1pR3JG5q0S0YyxCyEoNtqA+CEhk/k6PVI=` |
| `src/components/markdown/markdown.css` | `.ds-markdown-cite` 角标样式 | `WcFAOpGSgkTXu2hOs5Diec+O95xXphVozXz0plV4uaw=` |
| `src/components/search/CitationLink.tsx` | 角标 + hover 卡 + `window.open` | `oTpzfJxk+k0jXXGB1Zz/XkAYfCSIBuktlNsYKRC1rRE=` |
| `src/components/search/HoverCard.tsx` | 300/100ms、gap 6、portal | `PkgLQvktqehEzZBS+AtRC6wQvLO+Moxj/ckd0lD7N1U=` |
| `src/components/search/HoverCard.module.css` | 悬浮卡 355px/16px padding/12px 圆角 | `EvaJTvzjnPt9NzxV8s8luzT5gDwvdcNojXdLx2a/ops=` |
| `src/components/search/SourceCard.tsx` | 来源卡片字段与结构 | `OKiKm+SM6uZDksrfQaOcCT5QAWefGo7+Mwd1TFurRl4=` |
| `src/components/search/SourceCard.module.css` | 卡片尺寸 | `NqR7KcP0MbyFIrQFOOSzFKnN0ZuTefPvVTZv5Zcs52k=` |
| `src/components/search/SiteLogo.tsx` / `.module.css` | favicon 三态 + 4 缩略图 | `lomzo5k1n66F799lYIK5m+tw6u7tVLq9VqS71gQj8Qw=` / `tItYOcvSX9ksWRllgdnXy1yy+8999E1WjjWYlUG776c=` |
| `src/components/search/FallbackImage.tsx` | loading/loaded/error 三态 | `PxU5qS6eBkD6pwbOShI/3RVXL62LsgjTSEEduMx9a2s=` |
| `src/components/search/SearchPanel.tsx` / `.module.css` | 右栏/移动全屏面板 | `tbEVK7YBnVbl9OHcDDD/aLgmeNKUoXAvuBz4be0Rzg4=` / `gkqMrfyDNRlMhbWL4BEwyPwCmc98RjG56Y+eQqZJdgM=` |
| `src/components/search/searchPanelState.ts` | toggle / 换入口替换 | `oX6C4ZI9ddjhu6HaFso+hVFZwBI5n+Derz2jUibSvgc=` |
| `src/components/messages/ReplyArea.tsx` | 搜索步骤行/浏览/查找/阅读链接 | `9zr0CwV65hadLyrPHSZhinM94YS3OopPFLKT96D2K7Y=` |
| `src/components/messages/AssistantMessage.tsx` | 底部「N 个网页」summary 入口 | `YeRJ/0CljO6kpHNp9KW3MHJ5s4rp9MPk+mSClZj+BZs=` |
| `src/components/messages/useReplyView.ts` | 来源数组复用（流式不重解析） | `y6gfy7600K8IjtkCNtVcmgAMTppo3r6UPn1VFrffHNQ=` |
| `src/app/theme.css` | 颜色/字体令牌 | `gKsnGZK3k5f9C0aqpfFxua0sofIpGGlLCrTwYehuxAc=` |
| `src/i18n/zh.ts` | 全部中文文案 | `S5t2c0wgSqs8RZidtLjdcsuVepbyE52PAZhOdwZWJbE=` |
| `src/utils/url.ts` | `isWebUrl`（仅 http/https） | （文件树无记录，见 §6.8） |
| `src/utils/format.ts` | `formatDate(sep='/')` → `YYYY/MM/DD` | `pPazl+vhuGa5PupkNaR466LHVDoPo0sEFOWJ0n9NOIw=` |

> 复核方式：`GET https://data.jsdelivr.com/v1/packages/gh/deepseek-ai/chat-demo@main` 取文件树与 SRI，比对 hash；或直接读 `https://cdn.jsdelivr.net/gh/deepseek-ai/chat-demo@main/<path>`。

### A.2 本机 DSH（`E:\deepseek-harness`，`0.2.1-alpha.2`）

| 路径 | 行 | 证据 |
|---|---|---|
| `packages/client/ui-primitives/src/WebBlock.tsx` | 11-20 | `WebSourceView` 只有 url/title?/snippet?/publishedAt? |
| 同上 | 56-62 | `WebBlockLabels`（`noResults/sourcesTruncated/http/contentTruncated`） |
| 同上 | 74-81 | `safeHref` 仅 http(s)，否则纯文本 |
| 同上 | 92-100 | `linkLabel`：title → hostname → 原始 URL |
| 同上 | 110-119 | `SafeLink`：`target=_blank rel=noopener noreferrer` |
| 同上 | 130-142 | `SourceItem`：`<li value={ordinal}>` 显式 1-based 编号 |
| 同上 | 150-170 | `WebSearchBlock`：answer 在上、`<ol>` 在下、空态「未找到结果」、截断提示 |
| `packages/client/ui-primitives/src/WebBlock.module.css` | 40-48 | 来源列表 `max-height:320px; overflow-y:auto`，`padding-left:2.5em` |
| 同上 | 78-96 | snippet/published/truncated 字号与色 |
| `packages/client/ui-primitives/tests/web-block.client.spec.tsx` | 160-173 | 30 条来源全渲染、**无展开按钮**、每个直接子节点都是 `<li>` |
| 同上 | 175-179 | 编号由 `<li value>` 给出 `1..N` |
| 同上 | 139-150 | snippet/date 有则渲染、空则省略 |
| `packages/client/ui-tool/src/client/tool/models/web-card-model.ts` | 58-82 | `meta = {answer?, sources[], truncated}` 契约；fetch 卡 `url/statusCode/truncated` |
| `packages/client/ui-tool/src/client/tool/components/ToolRow.tsx` | 307-308 | `WebBlock` 只在工具行展开后渲染 |
| `packages/client/ui-conversation/src/client/locales.ts` | 350-351, 728-729 | 「未找到结果」「来源列表已截断」/ 英文对应 |
| `C:\...\resources\app.asar` | — | 字符串扫描：`来源列表已截断`×2、`未找到结果`×2、`ds-markdown-cite`×0 |

### A.3 myco（本次快照）

| 路径 | 行 | 内容 |
|---|---|---|
| `app-android/src/main/kotlin/dev/myco/android/ui/ChatContent.kt` | 55 | `SourceRef(title, url, site)` |
| 同上 | 57-65 | `TimelineEntry(... sources: List<SourceRef>)` |
| 同上 | 68-77 | `parseSources` 只取 `results[].title/url/site` |
| 同上 | 79-86 | `ChatContentState.turnSources` |
| 同上 | 151-169 | `tool/result` → sources 首见去重 + timeline 挂载 |
| 同上 | 331-389 | `inlineAnnotated`：`**`/`` ` ``/`*`/`[label](url)`，无角标 |
| 同上 | 533-536 | 消息尾部 `SourcesSection(sources)` |
| 同上 | 554-579 | `SourcesSection`：「来源 · N」+ `i+1` 编号 + title/site |
| 同上 | 638-660 | 工具折叠区内 `entry.sources.take(6)` 卡片 |
| `app-android/src/main/kotlin/dev/myco/android/MainActivity.kt` | 654-661 | 实时 `ToolFinished` 解析 sources |
| `core/src/main/kotlin/dev/myco/core/capability/CapabilityTools.kt` | 34-73 | `web_search` tool 定义；结果 JSON `{query,count,estimatedCostCny,results:[{title,url,snippet,summary?,site?,date?}]}` |
| `core/src/main/kotlin/dev/myco/core/capability/DeepSeekToolsClient.kt` | 79-103 | Search API 读取 `site_name/date/snippet/summary/rerank_score`（**无 site_icon**） |
| `docs/recon/deepseek-api.md` | 120, 127-129, 139 | 服务端 web tools 带 citations / 结果块与签名 / 独立 Search API 字段 |
| `docs/recon/backend-feature-gaps.md` | 283, 386 | 服务端 web tools vs 独立 Search API 的取舍与待决 |
| `docs/recon/deepseek-chat-demo-ui.md` | 10, 64 | 上一轮 demo recon：仓库身份 + "text 块带 citations" |

## 附录 B：demo 关键流程伪代码（可直接翻译成 Compose 侧逻辑）

```
onEvent(citations_delta{citation}) -> currentTextBlock.citations += citation
onEvent(content_block_start{server_tool_use|web_search_tool_result}) -> blocks += block

buildView(message):
  registry = SourceRegistry()
  # 1) 先登记所有工具结果（编号 = 回复中出现的顺序，与正文无关）
  for block in message.blocks where block is server_tool_use:
      result = blocksByToolUseId[block.id]
      if result is web_search_tool_result:
          for item in result.content where item is web_search_result:
              registry.add(item.url, item.title, item.page_age, item.snippet, item.site_name, item.site_icon)
      if result is web_fetch_tool_result and block.kind == 'deep_search':
          registry.add(result.url, result.content.title, result.published_at)
  # 2) 文本块拼正文：块尾注入 [citation:N]
  markdown = ''
  for block in message.blocks where block is text and not isTemplate:
      indexes = OrderedSet<Int>()
      for c in block.citations: indexes += registry.add(c.url, c.title, null).index
      body = if indexes: block.text.trimEnd() else block.text
      markdown += body
      for i in indexes: markdown += "[citation:$i]"
      markdown += block.text.substring(body.length)
  # 3) 步骤行（搜索/浏览/查找/阅读）与 searchResults（底部 summary 用）
```

渲染：把 `[citation:N]` 替换成角标组件（编号 = `registry[N-1]`）；点击 → 打开 `source.url`；来源面板 = `registry.list` 顺序。

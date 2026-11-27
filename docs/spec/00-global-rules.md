# 全局规则 G-xx

> 每条规则 = **规则 + 为什么（反证）+ 证据 + 违反症状**。
> 无证据的条目留在 [open-questions.md](open-questions.md)，不许写在这里。
> 证据来源：`dsh` = `E:\deepseek-harness`（DSH 全量源码，本机），`myco` = 本仓库旧实现，`user` = 用户拍板。

## 索引

| ID | 规则 | 级别 | 证据 |
|---|---|---|---|
| G-00 | 禁止推测 | 🔒 铁律 | user |
| G-01 | 浮层不占位 | 🔒 铁律 | user + myco |
| G-02 | 折叠行定高、兄弟零重排 | 🔒 硬约束 | dsh E0 |
| G-03 | 展开三形态（禁止混用） | 🔒 硬约束 | dsh E0 |
| G-04 | 内滚到边停住，不传残余速度 | 🔒 铁律 | user（E0 待补） |
| G-05 | 流式边缘用遮罩渐隐，不用省略号 | 视觉 | dsh E0 |
| G-06 | 展开态头部 sticky，折叠入口永不可达 | 🔒 硬约束 | dsh E0 |
| G-07 | 展开状态随轮次版本重置 | 行为 | dsh E0 |
| G-08 | 运行态必须有 a11y 文案 | 无障碍 | dsh E0 |
| G-09 | 行内文字层级 = secondary，一档低于正文 | 排版 | dsh E0 |
| G-10 | 展开动画阈值 240dp：超过则直接切换 | 动效 | user 拍板 + dsh E0 |

---

## G-00 禁止推测

见 [README.md §0](README.md)。不重复。

---

## G-01 浮层不占位（铁律）

**规则**：回到底部、FAB、Snackbar、悬浮提示一律 overlay 渲染，**不参与布局流**，
不产生高度、不推挤内容、不触发空行。

**为什么（反证）**：一旦占位，滚动到底部时列表高度突变 → 内容跳动；
用户正读到一半会被推走；且"回到底部"按钮本身变成遮挡物，
用户为了看到它下面那段必须再滚一下。用户已实际踩过：v 圆钮曾占整行。

**证据**：`user`（明确要求"悬浮在消息列表右下角，不再占布局高度"）；
`myco` commit `16d87df`/`bc99cbe`。

**违反症状**：出现一条横向空带、按钮随列表滚动、滚到底时画面顿跳。

---

## G-02 折叠行定高 + 兄弟零重排（硬约束）

**规则**：可折叠行折叠时必须是**固定高度**，并对该行启用尺寸隔离
（Web: `contain: size layout`；Compose: 固定 `height` + `Layout` 不测量子内容）。

**为什么（反证）**：折叠行的展示内容（摘要文本、呼吸点、工具名）是**流式变化**的；
若不隔离尺寸，摘要每变一次，整条消息里它下面的所有兄弟节点都会重排 →
用户正在读的正文上下抖动。这不是"动画不好看"，是**根本没法读**。

**证据**：`dsh` `packages/client/ui-chat/src/client/chat/ReasoningRow.module.css:6-9`

```css
.root:not([data-expanded]) {
  contain: size layout;
  height: calc(24px + var(--dsh-content-font-delta, 0px));
}
```

**违反症状**：思考中摘要逐字出现时，正文/工具块跟着上下跳；
折叠行展开收起后，其下方内容出现 1–2px 累积错位。

---

## G-03 展开三形态（硬约束，禁止混用）

**规则**：任何"可展开"节点必须先声明它属于哪一种，三选一：

| 形态 | 高度 | 谁滚 | 折叠时兄弟 | 适用 |
|---|---|---|---|---|
| **A. inline-uncapped + sticky header** | 不封顶，随内容长高 | 外层会话 | 被推挤 | 思考行（ReasoningRow） |
| **B. capped-inner-scroll** | 封顶，内层滚 | 内层优先，到边交给外层 | 被推挤（封顶后） | 过程组 / 工具组（process group） |
| **C. sheet-overlay** | 浮层，独立高度 | 浮层内 | 完全不占位 | 详情、来源、附件预览 |

**为什么（反证）**：混用会互相破坏意图——
- 把 A 做成 B：思考内容被截断在一个小窗口里，用户永远读不完，且内滚与外滚打架（旧 myco 就是）；
- 把 B 做成 A：长工具输出把整个会话拉成几十屏，回到底部按钮几乎永远可用；
- 把 C 做成 A/B：详情抢走正文位置，用户退回时丢失阅读锚点。

**证据**：`dsh` 两类实现并存，且**不是同一个组件**：
- A：`ReasoningRow.tsx:70-74`（展开直接塞完整 Markdown，无高度限制）+
  `ReasoningRow.module.css:15-22`（展开时头部 `position: sticky; top: 0`）；
- B：`use-process-scroll.ts:1` 文件头注释
  *"Capped process-group scrolling and fades over the shared follow controller."*
  + `:48` 用 `canScrollUp/canScrollDown` 计算边缘渐隐。

**违反症状**：思考链读不完、滚动"卡在中间某处"、或整页被一段工具输出撑爆。

---

## G-04 内滚到边停住，不传残余速度（铁律）

**规则**：手势在一个内滚容器里到达上/下边缘时，**吸收掉剩余拖动/惯性**，不把速度传给外层；
只有当用户**在边缘继续施加新的手势**时，才允许外层接管。

**为什么（反证）**：用户明确给出——"那样用户就没法稳定地看自己想看的块，整个页面会乱滑，
软件没能成功猜测用户的操作意图从而让用户感到挫败。" 即：
一次手势 = 一次意图；意图在内层完成时被外泄，等于软件擅自改变了作用对象。

**证据**：`user`（明确拍板）。E0 代码证据待补：`dsh` 的
`use-scroll-follow.ts` / 浏览器 `overscroll-behavior` 具体实现（P1 补齐后升级本条）。

**违反症状**：展开过程组里往上滑，到头后整个会话开始滚动，读者丢失位置。

---

## G-05 流式边缘用遮罩渐隐，不用省略号

**规则**：流式追加中的单行摘要，右端用 `mask-image` 渐隐（Web）/ alpha 渐隐（Compose），
**不用 `text-overflow: ellipsis`**。

**为什么**：省略号是"内容到此结束"的语义；流式文本并没有结束。渐隐表达"还在长"，
且不会因省略号位置跳变产生视线跳动。

**证据**：`dsh` `ReasoningRow.module.css:59-66`

```css
.summary[data-streaming] { mask-image: linear-gradient(to right, black calc(100% - 48px), transparent); }
.summary[data-streaming] .summaryText { overflow: visible; text-overflow: clip; }
```

**违反症状**：思考中摘要显示 "…"，看起来像已经结束。

---

## G-06 展开态头部 sticky（硬约束）

**规则**：inline-uncapped 形态展开后，**头部（折叠开关那一行）吸附在会话视口顶部**，
背景用页面底色遮住下方内容。

**为什么（反证）**：展开的思考/正文可能比屏幕长得多；若头部随内容滚走，
用户想收起时已找不到入口，只能一路滚回顶部 —— 多屏内容下等于**无法关闭**。

**证据**：`dsh` `ReasoningRow.module.css:15-22`

```css
.root[data-expanded] [data-open] [data-disclosure-header] {
  position: sticky; top: 0; z-index: 1; background: var(--dsw-alias-bg-base);
}
```

**违反症状**：展开长思考后，只能滚到最上面才能收起。

---

## G-07 展开状态随轮次版本重置

**规则**：每个可展开节点的 open 状态是**本地状态**，绑在一个"版本号"上；
当所属轮次被隐藏/重建（版本前进）时，自动回到折叠。

**为什么（反证）**：若不重置，重新进入/重建的那一轮会带着上一轮的展开状态出现
（用户看到自己没展开过的块是展开的）；若不做版本绑定而直接全局存，
则同一轮的多个块会互相串状态。

**证据**：`dsh` `use-disclosure.ts:11-20`

```ts
const expanded = expandedVersion === version   // 版本前进 → 自动折叠
const toggle = () => setExpandedVersion(p => p === version ? null : version)
```

**违反症状**：切换分支/重进会话后展开状态错乱；或展开 A 导致 B 也展开。

---

## G-08 运行态必须有 a11y 文案

**规则**：任何"运行中/生成中"的视觉指示（呼吸点、shimmer、动画）必须配一段
屏幕阅读器可见（视觉隐藏）的文案。

**证据**：`dsh` `ReasoningRow.tsx:84`

```tsx
{running && <span className={a11yCss.visuallyHidden}>{t('row.running')}</span>}
```

**违反症状**：TalkBack 用户完全无法知道还在生成。

---

## G-09 行内层级 = secondary，一档低于正文

**规则**：折叠行标题/摘要、流式摘要等"过程性文字"使用 secondary 字号/行高
（正文 −1 档；正文 >14 时 −2 档），且**位置与正文光学对齐**
（`thinkBody` 左缩进 = leading 宽 + gap，不是任意值）。

**证据**：`dsh` `ReasoningRow.module.css:41-51,73-77`

```css
.summary { font-size: var(--dsh-content-font-size-secondary, 13px); line-height: calc(20px + ...); }
.thinkBody { padding: 4px 0 4px calc(22px + var(--dsh-content-font-delta, 0px)); }
/* 注释：Indent under the title, which starts at leading (16 + delta) + gap 6. */
```

**违反症状**：思考文字与正文同大小同粗细，读者分不清哪段是"过程"。

---

## G-10 展开动画阈值 240dp（已拍板）

**规则**：可展开节点的**展开后内容高度**决定动效：

| 展开后高度 | 动效 |
|---|---|
| ≤ 240dp | 允许位移动画（纵向 expand，曲线用 M5 `settle`），仅纵向、无横向生长 |
| > 240dp | **直接切换**，不做高度动画；允许 ≤120ms 的淡入淡出做过渡 |

**为什么（反证）**：长内容做高度动画时，动画期间内容是"被压缩渲染/逐帧重新测量"的，
在移动端会明显掉帧；更关键的是**用户必须等动画结束才能开始读**——
一段两屏的思考，动画 300ms，看起来是"卡了一下"。DSH 干脆全部不做高度动画
（`ReasoningRow.tsx:38-39`），我们只在小内容上保留手感。

**证据**：`user`（2026-11-27 拍板：高内容直接切换，≤240dp 才动画）；
`dsh` `ReasoningRow.tsx:38-39`（模式切换只切 display，不 unmount）；
阈值 240dp 为 `user` 授权下的工程取值（**E1**，可调，改动需记录在此）。

**违反症状**：点开一段长思考，画面先卡顿再跳到位；或动画结束后光标位置错位。

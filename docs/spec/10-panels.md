# 面板注册表（穷举）

> 来源：`E:\deepseek-harness\packages\client` 的 **70 个 UI 包**（DSH 客户端真实分解，E0）+
> myco 现状。这张表是"一个页面都不许漏"的分母：**每个面板必须有自己的 `panels/<id>.md`**，
> 没有 spec 的面板不得进入实现。
>
> 状态：✅ 已有可用 / 🟡 有但不合契约 / ❌ 没有 / ⏸ 明确不做（需用户确认并在表内注明理由）

## 1. 一级面板（myco 首版范围）

| ID | 面板 | myco 现状 | DSH 证据包 | 依赖 | 优先级 |
|---|---|---|---|---|---|
| `chat` | 聊天主界面（轮/消息/过程/操作） | 🟡 M5 壳有，密度远低于 DSH | `ui-chat` | — | **P0 第一优先** |
| `sidebar` | 会话导航（列表/搜索/分组/行菜单/筛选） | 🟡 旧历史页 + 抽屉半成品 | `ui-sidebar`, `ui-sidebar-browser`, `ui-conversation`, `ui-session`（壳）+ `ui-workspace`（会话行/分组/搜索/多标签）+ `ui-sidebar-right/files/documentpreview/terminal` | `chat` | 高 |
| `composer` | 输入区状态机（发送/停止/排队/附件/命令/触发词） | 🟡 有，缺排队/命令/触发词 | `ui-chat`, `ui-input-trigger`, `ui-attachment`, `ui-commands` | `chat` | 高 |
| `model` | **两维：模型 × 推理等级**（+ 预设小节预留，首版不做） | 🟡 简易 sheet（四维合并，需拆） | `ui-model-selection`, `ui-agent-preset`, dsh-Go/太墟 面板 | `composer` | 高 |
| `settings` | 设置壳 + 子页 | 🟡 单页堆叠 | `ui-settings`, `ui-settings-shell` | — | 高 |
| `settings.general` | 通用（语言/主题/字体/内容宽度） | ❌ | `ui-settings-general`, `ui-theme`, `locale`（注意：`ui-locale` 包不存在，实际是 `locale`） | `settings` | 中 |
| `settings.models` | 模型与 provider 管理 | 🟡 有 provider 编辑 | `ui-settings-models` | `settings` | 高 |
| `settings.agent-loop` | agent loop / 采样参数 | ❌ | `ui-settings-agent-loop` | `settings` | 中 |
| `settings.web-search` | 搜索 provider | ❌ | `ui-settings-web-search` | `settings` | 低 |
| `settings.plugins` | 插件清单与配置 | ❌ | `ui-settings-plugins`, `ui-settings-plugin-inventory`, `ui-plugin-manager` | `settings` | 低 |
| `attachment` | **导入媒体/附件选择与预览**（用户点名缺内建 UI） | 🟡 有 sheet，缺预览/进度/最近照片 | `ui-attachment`, `file-upload` | `composer` | **高** |
| `tool` | 工具卡/工具详情/结果渲染 | 🟡 平铺文本 | `ui-tool`, `ui-renderer` | `chat` | 高 |
| `approval` | 审批 / 权限预设 / 提问 | ❌ | `ui-approval`, `ui-permission-presets`, `ui-user-questions`, `interaction/*` | `chat` | 中 |
| `plan` | 计划面板 | ❌ | `ui-plan` | `chat` | 低 |
| `goal` | 目标面板 | ❌ | `ui-goal` | `chat` | 低 |
| `jobs` | 作业/后台任务 | ❌ | `ui-jobs` | `chat` | 低 |
| `schedule` | 定时任务 | ❌ | `ui-schedule` | `chat` | 低 |
| `subagent` | 子代理树 | ❌ | `ui-subagent` | `chat` | 低 |
| `skill` | 技能清单 | ❌ | `ui-skill` | `settings` | 低 |
| `deliverables` | 交付物 | ❌ | `ui-deliverables` | `chat` | 低 |
| `trajectory` | 执行轨迹/上下文用量 | ❌ | `ui-trajectory`, `ui-chat/TurnUsagePanel`, `StatsPills` | `chat` | 中 |
| `workspace` | 工作区/文件浏览 | ❌ | `ui-workspace`, `ui-sidebar-files` | `sidebar` | 低 |
| `message-feedback` | 消息赞踩/反馈 | ❌ | `ui-message-feedback` | `chat` | 中 |

## 2. 基座（非面板，但必须先定，否则每个面板都会各写一套）

| ID | 基座 | 内容 | DSH 证据包 |
|---|---|---|---|
| `base.primitives` | 基础组件 | 按钮/行/图标/披露行/图标动作条/shimmer | `ui-primitives` |
| `base.layout` | 布局与层 | 面板栈、层叠渲染、视口、边缘 | `ui-layout`, `ui-dockkit`, `ui-slots` |
| `base.renderer` | 渲染注册 | 节点渲染器注册（决定消息内每类节点长什么样） | `ui-renderer` |
| `base.theme` | 主题与 token | 颜色/字号/间距/圆角/阴影 | `ui-theme` |
| `base.sidebar-right` | 右栏（宽屏双栏） | 详情/文档预览/终端 | `ui-sidebar-right`, `ui-sidebar-documentpreview`, `ui-sidebar-terminal` |

> **无限抽屉壳**归入 `base.layout`：层叠覆盖 + 抽象邻接数据 + 无可见抓手，
> 必须先在 `panels/base.layout.md` 定死契约，再写任何面板排版。

## 3. 覆盖面自检（P1 门禁）

- [x] `ui-*` 包已被 10 份事实库归位（`ref/dsh-*.md` §1 各自的包→面板表）。
- [x] myco 现有每个页面/弹层都能在表里找到归属（`ref/legacy-myco.md` §1）。
- [ ] 每个 `❌` 面板都有一份 spec（P3 进行中）。
- [x] 移动端三个参考（dsh-Go / 太墟 / mobile-adapt）的面板已归位（`ref/dsh-go.md`、`ref/taixu.md`、`ref/dsh-mobile.md`）。

## 4. 参考实现有、myco 首版不做的面板（⏸，需用户确认）

> 来源：`ref/taixu.md` §1 指出太墟有 **7 个概念在 myco 面板注册表中没有 ID**。
> 这里逐条给结论：**新建 / 合并进现有面板 / 明确不做**。未确认前不进实现。

| 概念 | 太墟里的形态 | myco 的处置 | 理由 |
|---|---|---|---|
| `home` | 4 个"覆盖根页"之一（多入口首页） | ⏸ **待用户确认** | 用户此前说过"聊天/工作后续分离"，若要做分离就需要一个入口页；首版可不做 |
| `bot` | 多 Bot 管理与切换 | 🔁 **合并进 `model` 或独立 `persona` 面板** | myco 是陪伴型单人格，persona 是长期记忆的一部分，不是"多 bot 市场" |
| `workflow` | 工作流编辑与运行 | ⏸ 不做 | myco 无编排需求 |
| `git` | 仓库状态/提交 | ⏸ 不做 | 无代码工作区 |
| `terminal` | 内置终端 | ⏸ 不做 | 无 shell 运行环境 |
| `browser` | 内置浏览器（工具产物查看） | 🔁 合并进 `tool` 面板的"打开链接/预览" | 只需查看工具产出的网页结果，不需要完整浏览器 |
| `onboarding` | 首次引导 | 🟡 **最小引导**（provider 配置 + 权限 + 首次发送） | 新用户首次进入必须能配好 provider，否则发不出消息；legacy 已有 provider 编辑器可复用 |

## 5. 追加证据包（P1 第二批次）

| 事实库 | 覆盖对象 | 关键补充 |
|---|---|---|
| `ref/dsh-go.md` | Flutter 手机客户端（2261 交互点 / 72 节点） | 回底钮双形态、过程组"inline + 逐帧补偿"、工具"行/井/页"三层、滚动到边**主动转发拖动**、命中区分层口径 |
| `ref/taixu.md` | Kotlin/Compose 移动 agent（2154 交互点 / 54 节点） | **A2UI/render_surface 完整契约**（抽象数据的现成范本）、Navigation3 单栈 + 4 覆盖根页 + 1 抽屉、三档宽度几何、分层 BackHandler 优先链 |
| `ref/dsh-mobile.md` | 两个"把桌面 UI 搬手机"的壳/适配层 | 命中区 44 的实际做法与"扩边无隔离"的缺陷；Android 壳完全没做键盘避让 |

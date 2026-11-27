# P2 合并分工（6 个合并任务）

> 前置：P1 十份事实库全部完成（`docs/spec/ref/*.md`）+ 全部 `facts.csv` 对账 `uncovered=0`。
> 每个合并任务读 `_merge-brief.md` + `_merge-template.md`，只写 `docs/spec/p2/<panel>.md`。
> **写作用域互不重叠**，可并行。

| # | 合并任务 | 输出 | 必读 ref 段落（Lead 会逐条列进 prompt） | 决策焦点 |
|---|---|---|---|---|
| M1 | `base.primitives` | `p2/base.primitives.md` | `dsh-base` §0 与 §2（披露行/菜单/模态/灯箱/开关/Toast/图标动作条/JsonTree）、`chat-demo` §2 的 4 个 base 原语、`legacy-myco` §2 里的按钮与弹层 | 命中区策略（Q-03 / C-03）、披露行两形态、菜单与焦点陷阱、Toast |
| M2 | `base.layout`（**无限抽屉壳**） | `p2/base.layout.md` | `dsh-base` §0-6/7/8（ui-layout 三列网格 + z 阶梯 + dockkit 浮层序）、`dsh-sidebar` §0-1（侧栏是 grid 列，无返回栈）、`taixu` §6（导航架构）、`dsh-mobile`（抽屉/返回键）、`legacy-myco` 现有导航与 BackHandler | **C-10 侧栏形态 / C-11 层叠栈模型（Q-BASE-02 / Q-SB-01）**——全工程最大的架构决策 |
| M3 | `chat` | `p2/chat.md` | `dsh-chat` §0 全部 + §2 的 25 个节点 + §5、`chat-demo` §0/§2（17 个 chat 节点）/§3 对照、`legacy-myco` §2/§4（旧/M5 差异）、`dsh-go` 聊天页、`dsh-mobile` 手势与滚动 | C-01 滚动物理 / C-02 形态 / C-05 sticky / C-06 回到底部 / C-07 跟随判据 / C-13 重试 |
| M4 | `composer` | `p2/composer.md` | `dsh-sidebar` §2.5（20 个 composer 节点）、`chat-demo` 输入区（mirror 撑高/336px/aria-disabled）、`dsh-settings` §6.1 的三路来源、`legacy-myco` 输入区节点、`dsh-mobile` IME | 输入区状态机、附件三路、发送/停止、草稿、键盘避让 |
| M5 | `model` + `attachment` | `p2/model-attachment.md` | `dsh-settings` §5（模型面板维度表）、§6（附件来源/状态机/上传通道）、`legacy-myco` 模型与附件、`chat-demo` 引用闭环与附件、`dsh-mobile` 媒体选择器、`dsh-go` 媒体 | **C-08 模型面板维度（用户点名）/ C-09 内建媒体选择器（用户点名）** |
| M6 | `settings` | `p2/settings.md` | `dsh-settings` §0–§4（壳/子页/保存语义）、`legacy-myco` §2 设置类节点、`taixu` 设置页 | C-15 保存语义按页声明；设置搜索（DSH 没有，要不要做） |

## 合并完成后 Lead 的动作

1. 交叉检查：每个 `C-xx` 是否都被某个合并任务处理掉（没处理的补进 `_conflicts.md` 状态列）。
2. 汇总所有 `§6 待确认` → 按面板分组的**决策包**交用户逐项拍板。
3. 用户拍板后写 `panels/<id>.md`（P3，13 字段定稿 + 验收 ID），再进 P4。

## 顺序建议

- M2（无限抽屉壳）**先出**：它决定所有面板的容器契约，其它合并任务的"层叠/返回/宽度"判断都要引用它。
- M1 与 M2 可并行（一个管原子、一个管容器）。
- M3–M6 等 M1/M2 结论出来后更好写，但为了并行度允许同时开，**发现容器相关冲突一律写进 §6 引用 M2**，不自行决定。

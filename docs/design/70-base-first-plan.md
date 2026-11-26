# 基座优先计划（Base-first）· 2026-11-26

> 决策背景：chat/work 分化暂缓；myco 当前形态就是产品的**第一形态**，不是一次性原型。
> 先把这个基座做到"日常用不烧心"，再谈 preset/插件化与独立 app。
> 配套：UI 规格 [80-ui-component-inventory.md](80-ui-component-inventory.md)、
> 后端缺口 [../recon/backend-feature-gaps.md](../recon/backend-feature-gaps.md)、
> 记忆调研 [../recon/memory-agent-vs-companion.md](../recon/memory-agent-vs-companion.md)。

## 0. 三条修正后的原则

1. **组件复用与"设计会变"不矛盾**：交互原语（抽屉/Sheet/Dialog/预测返回/滑动/菜单/搜索/输入区）稳定投资；
   视觉令牌才允许变。此前的"先不做组件"判断作废。
2. **基座单形态**：UI 上移除 chat/work 开关；内部保留 `preset` 配置（未来 chat 单焦点 / work 多会话
   用 preset + 插件差异表达，不复制两套 UI）。
3. **先补后端契约，再做吃契约的 UI**：避免 UI 做完才发现没有 messageId/版本/seq/重放。
   抽屉与设置可以先做，聊天操作条/版本/搜索必须等后端 P0。

## 1. 后端前置（P0，先做）

按 `backend-feature-gaps.md` 的顺序：

| 批次 | 内容 | 为什么必须先做 |
|---|---|---|
| B0 事件契约 | `BridgeEvent` 加 `seq/sessionId`；`MutableSharedFlow` 改可靠投递（replay/ack 或 Channel）；`fromSeq` 重放；`requestId` 幂等；Failed 带 sessionId | 断线/切会话/多窗口/进程恢复全部依赖；`tryEmit` 会静默丢 delta |
| B1 消息模型 | `turnId/messageId/version/activeVersion/finishReason/usage`；`turn/end.status`（ok/stopped/error/truncated）；回放不丢 costUsd；历史重建支持 tool/reasoning/图片 | 重新生成、版本切换、操作条、footer、"停止态持久" |
| B2 会话列表/搜索 | `lastMessageAt/preview/messageCount/pinned/archivedAt/unread`；pin/archive 命令与排序；搜索返回 `seq/role/snippet`；索引入口 | 抽屉是唯一历史入口 |
| B3 会话并发与命令 | `chat.regenerate(messageId)`、`chat.editResend`（P1）、会话级互斥/排队、stop 带 turnId | 操作条与排队/插话 |
| B4 LLM 正确性 | 解析并回传 `reasoning_content`（带 tools 时必须）；OpenAI 方言显式关/开思考；`finish_reason`；按轮 thinking/tools 开关 | 不修会 400/成本失控 |
| B5 安全与附件 | 工具路径白名单 + 审批（模型不能读任意 path/credentials）；附件缩略图/预览；上传分块或压缩；runtime token 0600 | 隐私承诺与 OOM |

## 2. UI 阶段（U0–U4）

| 阶段 | 内容 | 依赖 | 验收 |
|---|---|---|---|
| **U0 Shell/导航** | 单 Activity + 导航栈；**Drawer（手机覆盖/平板常驻）**；顶栏统一；PredictiveBack；Snackbar 宿主；移除 chat/work UI 开关 | 无（可先行） | 抽屉开合/返回手势/平板侧栏；任意页可返回 |
| **U1 会话抽屉** | 搜索（后端 B2）、时间分组、置顶/归档、行菜单、滑动删除+4s 撤销、选中/未读、空态 | B2 | 100+ 会话流畅；搜索能跳到命中 |
| **U2 聊天内容** | 助手全宽 Markdown（标题/列表/引用/代码/表格/链接/图片）；消息操作条（复制/赞/踩/重生成/更多）；版本 ‹1/3›；过程折叠 + footer；错误可重试；免责声明 | B1/B3/B4 | 与参考图并排；长回答不卡；半截 Markdown 不崩 |
| **U3 输入区与附件** | 状态机（空=语音/有字=发送/生成=停止）；模式 chips（思考/搜索）；附件 Sheet（拍照/相册/文件+最近照片）；缩略图/上传状态；语音占位（P1 真实现） | B3/B5 | 无键盘遮挡；失败不发；草稿 |
| **U4 设置与抛光** | 分组卡片设置（AI/能力/外观/数据/关于）；搜索设置；回收站（P1）；关于/更新；空态/断网/无 provider；无障碍与截图基线 | B2 | 真机 daily script 一周不烧心 |

> 估：B0–B2 约 3–5 轮；U0/U1 约 2–3 轮；U2 约 2–3 轮；U3/U4 约 2 轮。
> 手机不在时做后端与 Compose Preview/截图基线，手机回来做真机验证。

## 3. 记忆（I2）位置

- 调研结论：**A 数据层 + C 策略层**（单 SQLite 多标签，domain 是策略主键）；禁 B 分库作为默认。
- 最小 schema 六表（memory_item/source/evidence/link/event/domain_policy）、来源分级、evidence 逐字校验、
  用户策展动作独立 append-only journal。
- I2 最小范围：原文层派生 + 显式记住/pin + 用户面板（看/改/删/关/导出）+ FTS/bigram 检索 + 衰减
  + eager ≤2 条/≤200 字 + recall 工具 + 6 指标 fixture。**不做**自动抽取/反思做梦/跨域自动召回/云端向量。
- 时序：**U0–U4 基座稳定后再进入 I2**；现在只保留事件日志中"可派生"的字段与 origin 信息。

## 4. 待用户确认

1. 顺序：先 B0–B2（后端契约）再 U0，还是 U0 与 B0 并行（我先做抽屉壳、同时改事件契约）？
2. 重新生成语义：同节点版本 ‹1/3›（推荐）还是新会话分支？
3. 归档/回收站是否进入本期（P1）？
4. 语音输入：系统语音识别还是 DeepSeek ASR（P1）？
5. 是否需要我扩 discovery（例如指定某个开源实现逐行读）？
6. 是否再设 goal 自动推进 B0–B3 + U0–U2。

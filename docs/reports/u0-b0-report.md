# U0/B0 报告：导航骨架与事件契约（2026-11-26）

> 目标：并行 B0（事件契约）+ U0（Shell/抽屉/返回/低耦合/动效排版基础）。
> 手机不在，本报告以构建/测试与代码审查为证据；真机截图待手机回来补。

## 1. B0 事件契约（已完成）

- `BridgeEvent` 全部带 `seq`（每会话从 1 单调递增）与 `sessionId`；`Failed` 不再丢会话归属。
- 每会话运行期事件环形缓冲（500 条）：`Query.EventReplay(sessionId, fromSeq, limit)`。
- HTTP：`/query/event.replay`；SSE `/events?sessionId=&fromSeq=` 先补发再订阅、按 seq 去重。
- `chat.send` 支持 `requestId` 幂等（同 id 重发不会跑两轮）。
- 投递：`_events.tryEmit`（静默丢）→ `emit` + 1024 缓冲；SSE 客户端解析 seq/sessionId。
- 修复 SSE 测试竞态：`Thread.sleep` 会阻塞 runBlocking 调度，改用 `delay`。
- 测试：core 67 + bridge-http 7 = **74/74 绿**。

## 2. U0 Shell（已完成代码，未真机验证）

- **单 Activity + 状态导航栈**：`Screen.Chat` / `Screen.Settings` / `null`=空态；移除独立历史页。
- **抽屉**：`ModalNavigationDrawer` + `MycoDrawerContent`（手机覆盖式；`gesturesEnabled=false`，MVP 只走汉堡，避免与系统边缘返回冲突）。
- **返回语义**：manifest `enableOnBackInvokedCallback=true`；抽屉打开→返回关抽屉；聊天/空态根页→返回打开抽屉（**不再直接退出 App**）；设置页→返回上一个会话。
- **移除 chat/work UI 开关**；代码保留 `AppPrefs` 供未来 preset 使用。
- **低耦合**：新增 `ui/model/UiModels.kt`（SessionItemUi/SessionGroup）与 `ui/Drawer.kt`；抽屉组件只收数据模型+回调，不直接依赖 Bridge。
- **动效/排版基础**（frontend-design-recon 的 NOW 4 项）：
  - `ui/Motion.kt`：press/enter/gesture/exit/quick token + `Modifier.pressable`；
  - Typography：正文 16/26、次级 14/22、辅助 13/20、标题 20/28，`includeFontPadding=false` + LineHeightStyle；
  - 抽屉行用 pressable；会话行 64–72dp 目标高度（实际由 padding 控制，待真机校准）；
  - 返回栈 + manifest 已接。
- 删除确认 + **4s Snackbar 撤销**（软删→restore）。

## 3. 已知风险 / 待验证

1. 无手机：抽屉开合动画、返回手势、平板常驻形态均未真机验证；Compose Preview 基线尚未建立（后续加截图测试）。
2. 抽屉会话分组暂时用 `createdAt`（后端缺 `lastMessageAt`，B2 补）。
3. 平板常驻侧栏尚未实现（当前统一覆盖式；`WindowSizeClass` 分支在 U1 加）。
4. Markdown/消息操作条/同节点版本是 U2，未开始。
5. 抽屉手势关闭（滑动）为 false；后续评估与系统返回冲突后再开。

## 4. 下一步

B1 消息模型（turnId/messageId/version/activeVersion/finishReason/usage、turn/end.status、回放保留 costUsd）
→ U1 抽屉（全文搜索/置顶/归档/平板常驻）→ B2 → U2。

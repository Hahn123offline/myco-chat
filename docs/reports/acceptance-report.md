# myco 基座重构验收报告（2026-11-26）

对照 `docs/design/70-base-first-plan.md` 与 `docs/design/80-ui-component-inventory.md` 逐项验收。
测试：`:core:test` **80** + `:bridge-http:test` **7** = **87 绿**（另有 `specs/validate_fixtures.py` 23/23）。
构建：`:app-android:assembleDebug`、`:app-desktop:installDist` 通过。

## B0 事件契约

| 要求 | 状态 | 证据 |
|---|---|---|
| BridgeEvent 带 seq/sessionId | ✅ | `Bridge.kt`，测试 `failed event carries sessionId` 等 |
| 可靠投递（emit 而非 tryEmit） | ✅ | `InProcessBridge.emit` |
| 每会话 500 事件重放环 | ✅ | `ringBySession` + `Query.EventReplay` |
| HTTP `/event.replay`、SSE `fromSeq` 重放+去重 | ✅ | `HttpBridgeServer/Client`、bridge 7 测 |
| chat.send requestId 幂等 | ✅ | `completedRequests` + 测试 |
| Failed 带 sessionId | ✅ | 测试覆盖 |

## B1 消息模型

| 要求 | 状态 | 证据 |
|---|---|---|
| user/assistant messageId/nodeId/version/turn | ✅ | `SessionEvent.kt`、schema fixtures |
| assistant finishReason/reasoning/usage | ✅ | 落盘 + 回放保留 costUsd |
| turn/end.status(ok/stopped/error/truncated)+durationMs+messageId | ✅ | 测试 + U2 footer 真机 |
| 两个客户端解析 reasoning/finish_reason | ✅ | OpenAi/Anthropic 客户端 + 测试 |

## B2 会话管理

| 要求 | 状态 | 证据 |
|---|---|---|
| 摘要（lastMessageAt/preview/messageCount/pinned/archived/deleted） | ✅ | `SessionStore.summaries` |
| 置顶/归档/软删/永久删除（含附件目录） | ✅ | store + bridge + 测试 |
| 搜索含 seq/role，可含归档 | ✅ | 测试 |
| 抽屉：搜索/分组/⋯菜单（置顶/归档/重命名/删除） | ✅ | 真机验证 |
| 回收站/归档页 + 撤销 Snackbar | ✅(撤销点击未点中) | 真机验证删除+Snackbar；撤销超时未点到 |

## B3 重新生成/编辑/并发/停止

| 要求 | 状态 | 证据 |
|---|---|---|
| 同节点 regenerate（version+1，旧版保留） | ✅ | 测试 + U2 真机 `2/3` 切换 |
| editResend（append-only revises/supersede） | ✅ | 测试 + 真机日志含 `revises=1`、新 turn 正常 |
| 会话互斥（并发 send 串行） | ✅ | 测试 turn=1/2 |
| stop 带 turnId 且不误停 | ✅ | 测试 `TURN_MISMATCH` |

## U0 Shell

| 要求 | 状态 | 证据 |
|---|---|---|
| 单 Activity 导航栈 | ✅ | `MainActivity` Screen 状态机 |
| 抽屉手机覆盖 / 平板常驻 | ✅ | 真机宽屏截图 `u4-tablet.png` |
| 返回不直接退 App（聊天/空态→抽屉；设置→聊天） | ✅ | 真机验证 |
| PredictiveBack 回调启用 | ✅ | manifest `enableOnBackInvokedCallback`；用 BackHandler 语义（未做预测动画） |
| 组件低耦合接口 | ✅ | `MycoDrawerContent` + `SessionItemUi`，宽窄屏复用同一组件 |
| 移除 chat/work UI 开关 | ✅ | 只剩单形态 |

## U1 抽屉

✅ 搜索、置顶/今天/昨天/本周/更早分组、时间/预览、⋯菜单、设置底部固定；真机验证（`u1-*.png`）。

## U2 聊天

| 要求 | 状态 | 证据 |
|---|---|---|
| 助手全宽无气泡（基座形态） | ✅ | `ChatBubble` + 截图 |
| Markdown 基础集（标题/粗斜体/行内码/列表/引用/代码块复制/表格） | ✅ | 真机 `u2-markdown.png` |
| 操作条（复制/重新生成） | ✅ | 真机 |
| 同节点版本切换 ‹n/m› + 持久化 | ✅ | 真机 `u2-versions.png`；切换写偏好 |
| 过程折叠 + reasoning | ✅ | 折叠块（fake 无工具，未真机覆盖工具步骤） |
| footer（耗时/步数/成本/状态） | ✅ | 真机 `3.5s · 1 步` |
| 用户消息长按：复制/编辑并重发 | ✅ | 真机编辑对话框 + 日志 revises |

## U3 输入

| 要求 | 状态 | 证据 |
|---|---|---|
| 状态机（空=🎤 / 有字=发送 / 生成中=停止 / 有附件=发送） | ✅ | 真机 `u3-composer.png` |
| 思考/搜索 chips 按轮生效 | ✅ | `thinking`/`webSearch` 透传 + 单测 |
| chips 状态持久化 | ✅ | SharedPreferences |
| DeepSeek ASR 可选入口 | ✅(文件转写) | 🎤→系统音频选择器；实时录音未做 |
| 附件（相册/拍照/文件、chip、清空） | ✅ | 既有 + 真机 |

## U4 设置与抛光

| 要求 | 状态 | 证据 |
|---|---|---|
| 设置分组卡片 | ✅ | 外观/会话/常驻/自检卡片，provider 卡片；截图 `u4-settings-cards.png` |
| 设置搜索 | ✅ | 中英文关键词过滤；真机 `archive` 验证 |
| 平板/横屏常驻侧栏 | ✅ | 真机宽屏 |
| 版本选择持久化 | ✅ | 偏好 `v_<nodeId>` |
| 视觉：不透明渐变+阴影（拒绝廉价半透明） | ✅ | `GlassSurface` / `ChatBubble` |

## 前端技术调研

✅ `docs/recon/frontend-design-tech.md`（动效曲线/按压/排版光学/token/毛玻璃可行性/边缘返回），另 `ui-component-behavior.md`、`backend-feature-gaps.md`。

## 明确未完成 / 已知限制

1. **实时麦克风 ASR 未做**：目前是选音频文件转写；实时需 AudioRecord+WAV。
2. **PredictiveBack 动画**：只做了返回语义（`BackHandler`），未接 `PredictiveBackHandler` 的预览动画。
3. **Markdown 非全集**：链接不可点、表格为等宽近似、嵌套列表/公式未处理。
4. **撤销点击/归档页**：真机点验未跑通（时序/键盘问题）；代码与单测存在。
5. **过程折叠未覆盖工具步骤**：fake provider 无工具调用，工具路径只有单测。
6. **事件重放环在内存**：进程重启后不能回放历史增量（历史仍可从 JSONL 读）。
7. **GitHub push 被 502 阻塞**：本地提交完整，网络侧需换节点或 SSH key。

结论：基座单形态（助手无气泡）、B0–B3 后端契约、U0–U4 主要交互均已落地并在可用设备上验证；剩余为上述增强项，不影响基座继续迭代（chat 变体气泡、work、记忆等后续层）。

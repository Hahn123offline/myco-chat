# M5.3 真实数据接入报告（2026-11-27）

> 入口：`adb shell am start -n dev.myco.android/.MainActivity --ez m5_chat_ui true`
> 同一套 `ChatScreen` 数据管道（事件流/会话树/分支/流式），只把列表与顶栏/输入区换成 M5 渲染。

## 1. 怎么接的

- `ChatScreen` 增加 `m5Ui` 开关：数据加载、事件收集、流式缓冲、回到底部跟随、发送/停止、分支 activeLeaf 全部复用；
- 新增 `ChatMessagesM5`（与 `ChatMessages` 同签名）：解析 `resolveVisibleMessages` 的可见路径 + `userVersionGroups`，渲染粉气泡、思考文字行、Markdown 正文、meta、流式光标；
- 新增 `M5ChatTopBarReal`（玻璃浮条 + 菜单 + 标题/状态）与 `M5Composer` 的真实发送/停止（流式时发送键变停止键）。

## 2. 真机实测（真实会话 + 真实模型）

发了一条 `test-m5-real`，模型走了 `time_now` + `web_search` 两个工具：
- 用户粉气泡、思考段（`思考 · 执行了 2 个命令` + 4 条真实过程行）、Markdown 正文、meta（`3.7s · 2 步`）都正常；
- 流式期间发送键变停止、结束后回落到发送；
- 标题自动生成后同步到 M5 顶栏。

## 3. 设计缺口 / 接不上的地方（不静默跳过）

1. **消息操作缺失**：M5 列表还没有「复制 / 重新生成 / 用户长按编辑」的视觉入口（回调已接，长按可用但无菜单样式）；旧版有 `MessageActions` 文本按钮。
2. **回到最新按钮**：仍是 M1 `TextButton`，和 M5 风格不一致，需要做成 M5 浮起的「↓ 回到最新」胶囊。
3. **过程组没有内层滚动隔离/折叠分组**：长 reasoning 会整段撑高；旧版有 `maxGroupHeight` + 内层滚动。M5 版需要重新设计（考虑"摘要行 + 抽屉式展开"）。
4. **Markdown 代码/列表问题被暴露**：有序列表里的行内代码 `\`time_now\`` 仍是原样反引号，说明 `MarkdownText` 的列表分支没走 inline 解析——这是 M1 遗留的 Markdown 问题，需要在 M5 的排版里一起修。
5. **webSearch 开关**：M5 输入区只有「深度思考」，`智能搜索` 开关还没进新布局。
6. **附件胶囊/待发送附件预览**：旧版用 AssistChip，M5 还没做；发送区只显示文本。
7. **错误条**：`InlineError` 仍是 M1 颜色，暂未换 M5。
8. **dark 判定**：M5 内层根据 `MaterialTheme.background` 亮度推断明暗（因为真实页嵌在 MycoTheme 下），后续 M5 主题接管后可直接读 token。

## 4. 下一步

先补 1/2/3（消息操作、回到最新、过程组样式），再决定是否把 `m5Ui` 设为默认；随后抽屉/设置重做。

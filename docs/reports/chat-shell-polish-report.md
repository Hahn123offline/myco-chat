# 聊天壳层结构性细节修复（2026-11-26，M5 前最后一轮）

五条反馈逐条处理，全部真机验证（7e7c5a68 / Android 15）。

## 1. 新会话标题生成后不实时同步到聊天顶栏

- 根因：顶栏标题来自 `Screen.Chat(title)`，只在打开会话时写入；`SessionGenerateTitle` 之后只 emit
  `BridgeEvent.SessionUpdated`，UI 没消费。
- 修复：`MycoApp` 订阅 `SessionUpdated`：若是当前会话，则查一次会话列表取最新标题并 `showChat(...)`
  同步顶栏；抽屉打开时顺带刷新列表。
- 验证：新会话发首句后，顶栏从"新会话"自动变成模型生成的标题（"花猫一句话解释"），无需开抽屉。

## 2. 标题提示词限字数；不硬切；长标题在抽屉里可横向滚动

- 提示词：`不超过 12 个字` → `不超过 16 个字符（中文字按 1 个算）`，仍只输出标题；
- 去掉了原来的 `.take(24)` 硬切；只保留 80 字符安全上限（防模型发疯，正常不触发）；
- 抽屉会话标题：单行 + `horizontalScroll`，过长时可横向拖动查看完整标题（不再省略号截断）；
- 标题生成单测仍绿（已有"只生成一次/跳过已有标题"覆盖）。

## 3. 进入会话直接定位到最底部（不再是"从顶部滚下去"）

- 新增每个会话一份 `ChatScrollState`（含 `LazyListState` + `initialBottomDone`）：
  进入会话时等 item 就绪后 `scrollToItem(last, 100000)` **瞬间落底**，不打动画；
- 只在"首次进入某会话"触发一次；发送/重生成仍走流式跟底。

## 4. "思考"吸顶头把后面的 act 文本一起黏住

- 用户澄清 + 截图确认：滚动过程组内部时，**收起态的"思考"小行**也会作为 stickyHeader
  黏在组体顶部，把下一行 act 文本（"I'll do both parts…"）当成自己的 thinking 行盖在下面。
- 修复（`ChatContent.kt` 内层列表）：
  - **收起行改为普通 item，不再 sticky**（从根上避免它黏住后续内容）；
  - **展开行才用 stickyHeader**，并在其内容后放一个 1dp 的占位 sticky 头，
    让"吸顶"范围止于本行自己的内容，滚过就立刻把头部推走；
  - 内层展开/收起动画保持不变（详情 item 仍在，AnimatedVisibility 高度+淡入淡出）。
- 验证：展开组体 / 展开/收起小行正常，展开的"思考"里能正常滚动查看内容。

## 5. 从设置返回后会话自己滚到底部

- 根因（实测日志确认）：进入设置/回收站时，聊天页会被**销毁重建**（同一 sessionId 的
  `ChatScreen enter/exit` 成对出现），重建后 `LazyListState` 归零 + "进入会话落底"逻辑
  再次执行 → 阅读位置被拉到底部。
- 修复：
  1. 把 `LazyListState` 提升为 **会话级 `ChatScrollState`**，以 `lastChatScreen.id` 为 key
     挂在页面生命周期之外；页面重建后滚动位置（index/offset）保留；
  2. "首次进入落底"用 `initialBottomDone` 标记，同一会话只执行一次；
  3. 自动跟底循环加上条件：只有 `streaming != null || liveEntries.isNotEmpty()` 或
     刚结束的 12 帧收尾时才跟底；纯导航/重组不再主动滚动。
- 验证：长会话滚到中段 → 进设置 → 返回，阅读位置保持原样（不再回到底部）；
  抽屉切换会话仍直接落在最底部；开始流式/发送时仍正常跟底。
- 顺带：聊天窗口改为常驻基座（进出设置不销毁），并用同步的 `showChat()` 维护
  `lastChatScreen`，避免异步 effect 造成重建。

## 回归

- `:core:test` 87（含标题生成测试）+ `:bridge-http:test` 7 + `:app-android:testDebugUnitTest` 7 全绿；
- `:app-android:assembleDebug`、`:app-desktop:installDist` 通过；
- 无 FATAL。

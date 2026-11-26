# P1 流式自动滚动：统一进度、结束不跳（2026-11-26）

## 反馈现象

1. 自动滚动"只关心 act 段"：思考/工具阶段，过程组体内的新内容不跟随，只有正文开始流式后窗口才动；
2. 模型输出结束时窗口乱跳一下（有时内容先消失再出现，或过程组收起导致整体高度突变）。

## 根因

1. **组体内不跟底**：`TimelineBody` 的 live 组体只在 `entries.size` 变化时 `animateScrollToItem(entries.size-1)`；
   思考/工具文本是在"最后一条 entry"里持续变长的，条数不变 → 不滚动。且 `pointerInput(entries.size, …)`
   会在每次新增 entry 时重启手势识别。
2. **结束时先清 live 再 reload**：`AssistantDone` 里先 `liveEntries=emptyList(); streaming=null`，然后才
   `reload()`（内部两次 suspend）。中间帧里正文凭空消失，列表瞬间变短 → 窗口先跳上去，reload 完成后再跳回来。
3. **落盘后过程组默认收起**：live 组体是展开的，落盘后 `expandedProcess[turn]` 默认 false → 高度骤减（最多 400dp），
   下方正文整体上移，跟随循环再逐帧追下去 = 肉眼可见的"跳一下"。
4. **外层跟底判定被程序滚动误伤**：旧逻辑用 `isScrollInProgress` 判断"用户是否在滚"，任何程序滚动都会把
   `followBottom` 关掉，于是出现"↓ 回到最新"却没人滚过。

## 修复

### 1. 组体内逐帧跟底（`ChatContent.kt` TimetableBody）

- 每帧：最后一条不在视口内 → `scrollToItem(last, 100000)` 直接落底；在视口内但底部溢出 →
  `dispatchRawDelta((overflow*0.35).coerceIn(1,64))` 小步推进（与外壳一致的平滑跟底）；
- 用户手指拖动组体时暂停跟底；松手后若内容不在底部则保持暂停，直到用户自己滚回底部（不抢窗口）；
- `pointerInput(maxHeight)` 不再随 entries.size 重启，避免流式中途打断手势。

### 2. 结束前先落盘再清 live（`MainActivity.kt` reload/AssistantDone）

- `reload(clearLive)` 改为：先取事件与 sidecar，**同一延续内一次性提交** content / activeLeaf，
  需要时再清 live 缓冲。`AssistantDone` 调 `reload(clearLive = true)`，
  中间帧不会再出现"正文消失"。

### 3. 落盘后保持过程组展开（`ChatScreen.streamingTurn` → `ChatMessages(autoExpandTurn)`）

- 流式期间记录 `streamingTurn`，`ChatMessages` 对它 `expandedProcess[turn] = true`；
- 该回合落盘后组体仍展开、高度不变；同时该组体 `autoScroll = true`，落盘后仍停在底部
  （和流式时看到的位置一致，不会重置回顶部）。

### 4. 外层跟底判定（`ChatScreen`）

- 用 `listState.interactionSource` 的 `DragInteraction.Start/Stop` 判断"用户真的在拖"，
  松手时按是否在底部决定是否恢复跟随；程序滚动不再关掉跟随；
- 最后一项在视口外时直接 `scrollToItem(last, 100000)` 落底，不再出现落后于内容却不动的情况。

## 验证（真机 7e7c5a68 / Android 15）

- `adb shell screenrecord` 录屏 75s，`ffmpeg -vf fps=4` 抽帧逐帧核对：
  - 思考/工具阶段组体内新条目持续出现在底部（不再停在顶部）；
  - 正文流式阶段窗口贴住最后一行（含 Markdown 表格段落）；
  - 结束帧（`v0143 → v0144`）只多出"复制/重新生成 + 时长·步数"，窗口只跟随新增高度小幅下移，**无跳变**，也没有"内容消失一帧"；
- 结束后上滑到顶部：过程组仍是展开状态（`autoExpandTurn` 生效），组体内部停在流式结束时的底部位置；
- 用户上滑：出现"↓ 回到最新"，持续流式期间不会被抢回底部；
- 单元测试：`:core:test` 86 + `:bridge-http:test` 7 + `:app-android:testDebugUnitTest` 5 = 98 绿；Android/desktop 构建通过。

## 已知遗留

- 发送后软键盘不会自动收起，流式期间聊天视口被压缩（不影响跟随逻辑，但观感一般）；
- 思考行仍是单行预览（展开需手动点开），流式时不会把思考正文铺开——与之前"长思考掉帧"的取舍一致，
  是否改成"流式时自动展开、结束折叠"属设计取舍，待定；
- 极快输出（>64px/帧）时外壳是逐帧小步追，肉眼是平滑滚动而非瞬移；若想更贴可调系数。

# P0 长会话"打开即闪退"——根因与修复（2026-11-26）

## 现象

长会话（尤其带联网搜索/多步工具）打开后有概率直接闪退；崩溃后再次进入仍崩，会话看起来"死了"。

## 定位过程

1. `adb logcat -b crash -d` 抓到真机 FATAL（设备 7e7c5a68，Android 15）：

```
java.lang.IllegalArgumentException: Key "ph-1" was already used.
If you are using LazyColumn/Row please make sure you provide a unique key for each item.
  at LazyListMeasuredItemProvider.getAndMeasure ...
  at LazyListMeasureKt.measureLazyList ...
```

不是 JSON 解码、不是内存/递归、不是 JSONL 尾部损坏——是 **Compose LazyColumn 的 item key 重复**。

2. 把真机会话日志拉到本地（`.myco-dev-e2e/pulled/`）用 Python 复刻 UI 的建模逻辑，确认：

   - 工具循环会落盘"中间步"assistant 消息：`nodeId = null`、`finishReason = "tool_use"`，`parentId = null`（不在会话树上）；
   - 正式回答的 assistant 消息与它**共用同一个 turn**；
   - 旧渲染逻辑对"过程组"使用的 key 是 `ph-$turn` / `pb-$turn`；
   - 当 `activeLeaf` 为空或失效时，`visible` 会退化成 **全部消息**，于是同一 turn 的"中间步 + 正式回答"各生成一次 `ph-1` → 重复 key → 闪退。

3. 失效场景：`ChatScreen` 在切换会话时被复用（未按 sessionId 重建），`loadModelInfo()` 先写入新会话的 `activeLeaf`，`reload()` 才异步写入新内容。中间这一帧：

   - 内容还是旧会话的 → `pathOf(新 activeLeaf)` 在旧消息里找不到 → 空；
   - 于是 `ifEmpty { state.messages }` 退化成旧会话全部消息；
   - 旧会话只要带工具步 → 重复 key → 崩。

   这也解释了"有概率"：取决于上一个会话是否带工具步、以及时序。

## 修复

### 1. 会话树只渲染唯一分支（`ChatContent.kt`）

新增 `resolveVisibleMessages(all, activeLeaf)`：

- 移除"工具中间步"（`role=assistant && nodeId=null && finishReason in {tool_calls, tool_use}`），它们只属于过程时间线；
- 有树时只渲染 `activeLeaf` 回溯出的分支；
- `activeLeaf` 失效/悬空时退回**最新叶子**（没有子节点的最后一条消息）的分支，**绝不**退化成"全部消息"；
- 无树（早期扁平会话）才渲染整列表。

`activeLeaf` 有效时不再需要"按版本去重"（路径上每个 node 只出现一次）；旧扁平数据仍按选中/最大版本去重。

### 2. key 结构性唯一（`ChatContent.kt`）

- 过程组 key 改为 `ph-$index-$turn` / `pb-$index-$turn`，同一 turn 的过程组只渲染一次（`renderedProcessTurns`）；
- 消息 key 继续带 index；
- 即历史数据再异常，也不会出现重复 key。

### 3. 会话切换不再串状态（`MainActivity.kt`）

`ChatScreen` 用 `key(s.id)` 包裹，切换会话即重建该页状态（content/activeLeaf/streaming/liveEntries 全部隔离），从根上消除"旧内容 + 新 activeLeaf"的中间态。

### 4. 解析/载入容错

- `ChatContent.kt` 的 JSON 取值改为宽容访问（对象/数组/null 一律当缺失），单个脏事件不再打断整页解析；
- `reload()` 用 `runCatching` 包住解析，异常时显示"会话数据异常"而不是崩溃；
- 事件重放失败（`QueryResult.Err`）时显示"载入失败：code: message"。

## 回归测试

新增 `app-android/src/test/.../VisibleMessagesTest.kt`（5 条，全绿）：

1. 工具中间步不作为正文渲染；
2. `activeLeaf` 失效时退回最新分支（不会同时出现同 turn 的两个过程组）；
3. `activeLeaf=null` 时取最新叶子路径（新格式会话）；
4. 旧扁平会话保留全部消息；
5. 有树时路径只包含选中的版本。

测试命令：

```powershell
gradle -p E:\myco-chat :core:test :bridge-http:test :app-android:testDebugUnitTest :app-android:assembleDebug
```

`:core:test` 86 + `:bridge-http:test` 7 + `:app-android:testDebugUnitTest` 5 = **98 绿**。

## 真机验证（7e7c5a68）

1. 安装新 APK，冷启动进入草稿；
2. 打开带工具步的长会话（3 步、15 来源）正常显示；
3. 在 4 个会话之间连续快速切换 7 次（含两次往返），无 FATAL，进程未重启（PID 不变）；
4. `adb logcat -b crash` 无新增崩溃。

## 仍未做（见 backlog）

- 损坏事件的**恢复提示 UI**（目前只到"会话数据异常"文案）；
- 会话中有 mid-file 损坏行时 `SessionStore.read()` 仍会整体抛 `CorruptSessionException`（表现为空会话 + 载入失败提示，不崩，但无法恢复部分内容）；
- 自动滚动统一进度、用户消息分支、内层展开动画等见 `docs/reports/backlog-and-issues.md`。

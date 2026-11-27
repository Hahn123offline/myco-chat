# Bug 台账

> 偶发/稳定 bug 一律登记在此，不进聊天记录就丢。
> 每条：现象 → 复现步骤 → 定位（文件:行，E0）→ 影响面板 → 状态。
> **禁止**只写"偶发崩溃"。E2 猜测必须标注且不得作为修复依据。

## 已确认（E0 代码级）

### BUG-001 用户消息「复制」是假功能：弹提示但不写剪贴板
- **现象**：长按自己的消息 → 复制 → 弹出「已复制」，但剪贴板没有内容。
- **定位**：`app-android/src/main/kotlin/dev/myco/android/MainActivity.kt:1323-1326`
  —— 只 `Toast.makeText(..., "已复制")`，没有 `clipboard.setText(...)`。
- **对照**：助手消息复制是**正确实现**的（`MainActivity.kt:1132-1135` 与 `1158-1161` 都有 `clipboard.setText(...)`）。
- **影响面板**：`chat`（用户消息长按菜单）。
- **状态**：已确认待修（改 2 行即可，等面板重构时一并做）。

### BUG-002 M5 丢失助手消息「版本切换」入口
- **现象**：重新生成后，旧版本在 M5 UI 里没有任何入口可切回。
- **定位**：`MainActivity.kt:1139-1141` 把 `onVersionSelected` 传进 `ChatMessagesM5`，
  但 `ui/m5/ChatReal.kt:102` 收下后**从未调用**；M5 操作条 `AssistantActionsM5`
  （`ChatReal.kt:601-607`）只有复制 + 重新生成两个按钮。
- **对照**：旧实现有 `‹ 1/N ›`（`ui/ChatContent.kt:1489-1494` 的 `onVersionSelected` 消费处）。
- **影响面板**：`chat`（消息操作条）。
- **状态**：已确认待修（属 M5 功能回填，P4 chat 面板一并做）。

### BUG-003 「深度思考」开关与推理档是双真源，UI 开关可能无效
- **现象**：对声明了默认推理档的模型，关掉「深度思考」开关后仍按推理档发送。
- **定位**：发送时同时传 `thinking` 与 `reasoningLevel`（`MainActivity.kt:962-968`）；
  后端在 `level` 非空时用 `apiValue` 覆写 `thinking`（`core/.../bridge/InProcessBridge.kt:478-495`）。
- **影响面板**：`composer`（深度思考开关）+ `model`（推理强度）。
- **状态**：已确认待修（需先在 `open-questions.md` 定「开关与档位谁是唯一真源」）。

### BUG-004 发送防重入窗口
- **现象**：极快连点两次发送，可能发出两条。
- **定位**：`MainActivity.kt:942-944`——防重入依赖 `streaming`，而 `streaming` 要等首个 delta 才变非 null，
  这个窗口期内可再次提交。
- **影响面板**：`composer`。
- **状态**：已确认待修（改为提交瞬间置位、失败回滚）。

## 待复现（用户口述）

### BUG-010 偶发 UI 异常
- **现象**：用户提到"还有偶发 bug"，未收集细节。
- **下次遇到请记下**：哪个页面、做了什么手势、前后发生了什么、能否稳定复现。
- **状态**：待复现。

## 迁移高风险（不是 bug，但会丢功能）

| # | 项 | 证据 | 说明 |
|---|---|---|---|
| M-01 | M5 完全没有「来源区」 | `ChatReal.kt:93-248` 无 `SourcesSection` 调用；旧实现在 `ChatContent.kt:669-670,1169-1235` | 搜索回答的引用在 M5 里不可点、不可读 |
| M-02 | 附件只有三个系统选择器 | `MainActivity.kt:1304-1312`（拍照/相册/文件） | 无内建选择器/预览/进度/最近照片；且 Bridge 只有 `AttachmentSave`，无读/删附件命令（`core/.../InProcessBridge.kt:119-144`） |
| M-03 | 移除附件 chip 不删已落盘文件 | `MainActivity.kt:790-817,1230` | 磁盘会累积孤儿文件 |

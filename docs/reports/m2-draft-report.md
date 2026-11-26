# M2 起点：新会话语义（草稿/转正/自动标题）（2026-11-26）

## 1. 已实现

### 唯一"新会话"草稿
- `SessionSidecar.draft`；`SessionStore.create(..., draft=true)`。
- 新命令 `SessionEnsureDraft`：已有草稿则复用，没有才创建（同一时间只有一个空白草稿）。
- `ChatSend` 写入第一条 user 消息时自动把草稿**转正**（`draft=false`）。
- `Query.SessionList` 返回 `draft` 字段。

### 启动与导航语义（对齐 DSH）
- App 启动：不再显示"开始聊天"空页；自动 `SessionEnsureDraft` 并打开"新会话"。
- "新会话"按钮：复用现有草稿，不再每点一次建一个新空会话。
- **删除/归档当前会话 → 自动打开新的"新会话"草稿**，不再跳到"下一个会话"。
- 抽屉里空白草稿只在它是当前会话时显示；切到别的会话后，空草稿行隐藏（数据仍保留，下次"新会话"复用）。
- 抽屉打开时刷新会话列表（标题/预览会更新）。

### 首条消息后自动标题
- 新命令 `SessionGenerateTitle(sessionId)`：读第一句用户消息，用同一 provider/model 发一次小请求（maxTokens=64，关思考）生成 ≤12 字标题；
  - 标题已是自定义/非"新会话"则跳过；
  - 失败不影响回答；
  - 成功后 rename + 发 `SessionUpdated`。
- Android 在首轮回答成功后触发（fire-and-forget）；标题在下次打开抽屉/列表刷新时可见。
- HTTP `session.ensureDraft` / `session.generateTitle` 已映射。

## 2. 测试

`:core:test` **85** + `:bridge-http:test` **7** = **92 绿**。
新增：草稿唯一性（复用/转正后新建）、自动标题生成且只生成一次、草稿/列表 JSON。

## 3. 未完成（M2 继续）

1. **会话树**：消息 `parentId` + sidecar `activePath`；regenerate/editResend 生成新分支并切 activePath；后续消息跟新分支；旧分支完整可切回（chat）；work 的 branch 另开会话文件（概念分开）。
2. 标题生成后 chat 顶栏标题未实时刷新（需要会话标题状态回传）；标题生成会额外消耗一次模型调用。
3. 删除/归档→新会话后，撤销 Snackbar 只恢复旧会话，不会自动切回（保留新会话）；是否切回待定。
4. 真机未验证（ADB 断开）；本轮只做编译与单测。
5. 草稿在列表里的"唯一/隐藏"规则还需要真机手感确认（尤其从抽屉点"新会话"时）。

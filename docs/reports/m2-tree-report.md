# M2 会话树：parentId / activeLeaf / 分支重生成（2026-11-26）

## 1. 已实现（核心）

### 树结构
- `user/message` 与 `assistant/message` 事件新增 `parentId`：
  - 每个 user 消息挂在当前分支叶子上；
  - assistant 消息挂在对应 user 消息上（regenerate 的新版本也挂同一个 user）。
- `SessionSidecar.activeLeaf` 记录当前分支叶子（根→叶可回溯出 activePath）。
- `Query.SessionModel` 返回 `activeLeaf`；新增 `SessionSetActiveLeaf` 命令（UI 切分支时持久化）。
- 旧会话无 parentId/leaf 时退化为线性历史（兼容）。

### 上下文与分支语义
- **普通发送**：上下文 = activeLeaf 回溯路径；新 user 的 parent = activeLeaf。
- **regenerate**：新 assistant 与原 assistant 同 nodeId、version+1、**parent 同为原 user**；
  上下文截到原 user 的父路径（不含旧回答）；完成后 activeLeaf 指向新回答。
  → **旧分支（旧回答及其后的 user/assistant）完整保留，但不在新分支路径上；后续消息跟新分支走。**
- **editResend**：新 user 的 parent = 原 user 的 parent；上下文截到原 user 之前；activeLeaf 指向新 user。
  → 旧 user 及其后续消息成为旧分支。
- 模型上下文只取 activePath，不再把"旧分支的后续消息"发给模型。
- 事件 JSON 补 `parentId`/`revises`（UI 现在能构建树、能显示"已被编辑替代"）。

### UI（编译通过，未真机）
- `ChatMessageUi.parentId`；聊天渲染按 activeLeaf 回溯路径过滤，只显示当前分支。
- 版本切换 `‹ n/m ›` 时：
  - activeVersions 更新；
  - 找到该版本消息的**最深后代叶子**作为新 activeLeaf；
  - 调 `SessionSetActiveLeaf` 持久化，后续发送跟该分支。
- 标题/模型信息查询同时带回 activeLeaf。

## 2. 测试

`:core:test` **86** + `:bridge-http:test` **7** = **93 绿**。
新增用例：
- regenerate 第一轮回答 → activeLeaf=新回答；再发消息 → 上下文只有 `问题1 + 新回答 + 问题3`，不含旧分支 `问题2/回答2`；旧分支事件仍在日志。
- editResend 上下文截断、revises/parent 关系正确。
- 草稿唯一/标题生成仍绿。

## 3. 与 work 的区别（已记录）
- **chat regenerate**：同一棵会话树内新建分支节点，切换 activeLeaf；旧分支节点保留可切回。
- **work branch**：另开一个会话文件（独立 SessionMeta/日志），不是同一棵树里的节点。
  两者不要共用 UI 语义；M5 做 work 时单独实现"分支会话"。

## 4. 未完成（M2 收尾）

1. UI 分支切换未真机验证；"旧分支"入口目前只能通过切回旧版本（‹）触发，缺少"分支列表/分叉点跳转"视觉。
2. regenerate 旧回答时，旧分支的**后续消息**在日志里保留，但聊天页没有"旧分支存在"的提示；需要分支指示器。
3. editResend 旧 user 消息的"已被替代"标记现在事件 JSON 带 revises，UI 应能显示（待真机）。
4. work branch 的独立会话模型未实现。
5. 无设备，全部编译/单测验证。

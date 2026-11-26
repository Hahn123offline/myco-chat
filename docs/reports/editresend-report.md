# 编辑重发（editResend）+ 输入偏好持久化（2026-11-26）

## 1. editResend（补齐 B3 缺口）

**语义（append-only 日志下的编辑）**
- 旧 user 消息**不删除、不改写**；新消息在 `UserMessage.Data` 记 `revises = 旧 messageId`。
- 模型上下文构建时：
  - 被替代的旧 user 消息**排除**；
  - 新消息正常进入上下文；其后的 assistant/turn 正常保留。
- 上下文截到旧 user 消息之前再追加新文本（不会把旧问题也发出去）。
- 新 turn、新 assistant 正常落盘；`requestId` 幂等；会话互斥沿用。
- HTTP `chat.editResend` 已映射。

**UI**
- 用户消息长按 → 菜单：复制 / 编辑并重发。
- 编辑对话框预填原文本；发送后调用 `ChatEditResend`，并乐观显示新消息。
- 历史里被替代的旧消息显示「（已被后续编辑消息替代）」，保留 provenance。

**测试**
- 新测：编辑后日志有 2 条 user（第二条 revises=第一条）、第二次请求上下文只有新问题；
  再发一轮时上下文为 `[修改后的问题, 继续]`，旧问题不出现。
- `:core:test` **80** + `:bridge-http:test` **7** = **87 绿**。

## 2. 输入偏好持久化
- 「深度思考」「智能搜索」chips 状态存 `SharedPreferences("myco.chat")`，下次进会话保持。

## 3. 真机状态
- U3 的 chips/🎤 已真机验证。
- editResend UI 本轮**未完成真机点验**（脚本在输入/发送步骤反复受键盘与坐标影响失败，服务端语义有单测覆盖）。
  手机在线，下轮优先补点验。

## 4. 仍未做（U4 收尾）
1. 设置页分组卡片 + 搜索设置。
2. 平板/横屏常驻侧栏（WindowSizeClass）。
3. 过程折叠里展示 reasoning；版本选择持久化（activeVersion）。
4. 归档/回收站真机点验 + 撤销点击时序。
5. push 仍被 GitHub 502 挡住。

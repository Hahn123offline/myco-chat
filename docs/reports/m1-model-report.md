# M1 起点报告：模型选择 / 推理强度映射 / 工具结果修复（2026-11-26）

## 1. 本轮完成

### 工具调用 400 修复
- 报错：`tool_use ids were found without tool_result blocks immediately after`。
- 根因：Anthropic 方言要求 assistant 的**多个 tool_use 必须由紧随其后的同一个 user 消息里的多个 tool_result 应答**；
  之前每个 tool 消息被转换成一个独立 user 消息，多工具时第二个 tool_use 后面紧跟的不是它的结果。
- 修复：`AnthropicCompatibleClient.messagesJson` 把连续 tool 消息合并为一个 user 消息、多个 tool_result block。
- 新增单测：两个 tool_use + 两个 tool_result → wire 上只有 1 个 assistant + 1 个 user，含 2 个 tool_result。

### 键盘顶飞（双倍 IME 留白）
- `enableEdgeToEdge()` + 默认 adjustResize 叠加 `imePadding()`，导致输入框和键盘之间多出一块。
- 修复：manifest 显式 `android:windowSoftInputMode="adjustNothing"`，IME 只由 Compose `imePadding()` 处理。

### M1：会话级模型选择 + 推理强度
- `ChatSend` 新增 `providerId/modelId/reasoningLevel`；新增 `SessionSetModel` 命令与 `Query.SessionModel`。
- 会话 sidecar 保存 `providerId/modelId/reasoningLevel`（按会话记忆，符合决策 2）。
- Bridge 解析顺序：请求显式 > 会话保存 > 第一个可用 provider 的 chat 角色。
- 推理等级映射：
  - `ModelProfile.reasoning.efforts: Map<level, apiValue?>` + `default`；
  - `apiValue=null` 表示该档关闭思考；非空则开思考并把 apiValue 作为强度参数；
  - 不支持/未知等级返回 `REASONING_UNSUPPORTED`，不会静默忽略。
- `ChatRequest` 新增 `reasoningEffort`；OpenAI 方言发 `reasoning_effort`（provider compat 可关）；Anthropic 数字值映射 `thinking.budget_tokens`。
- Android chat 顶栏新增**当前模型**按钮，点开模型选择：
  - 按 provider 分组列出模型（名称/id/context/maxTokens）；
  - 选择后 `SessionSetModel` 持久化；
  - 若模型声明了推理档位，显示「默认/off/low/medium/high…」chips（自定义映射由模型配置决定，默认值来自 `reasoning.default`）。
- 单测：会话级选择驱动 provider/model/reasoning；`off` 关闭思考；不支持等级报错。
- 测试：core **83** + bridge **7** = **90 绿**。

## 2. 本轮完成（第二批）

### Provider 模型目录编辑（DSH 方向）
- Provider 编辑弹窗从"单模型"改为**多模型目录**：
  - 每个模型可配：模型 ID、显示名、上下文窗口、最大输出、输入类型（文本/图片）、
    **是否支持思考强度**、默认档位（off/low/medium/high）、三档 API 参数值（自定义映射）。
  - 支持添加/删除模型；`roles.chat/fast` 取第一个模型。
- Provider 字段补充显示名称；API Key 仍是只写字段，保存后写 credentials，ref 自动生成。
- 推理映射的"关闭思考"用空字符串表示（`efforts["off"]=""`），避免 JSON 显式 null 被省略后丢档位；
  Bridge 把空值当关闭思考。

### 内置第三方 Provider 模板
- 新增「更多模板」入口，内置：DeepSeek、OpenAI、Anthropic、Kimi（Moonshot）、OpenRouter、Gemini（OpenAI 兼容）。
- 模板只带地址/协议/模型目录，key 由用户填写；可再编辑模型目录/参数。
- DeepSeek 模板默认两个模型：deepseek-chat（无思考）、deepseek-reasoner（off/low/medium/high 自定义映射，默认 medium）。

### 说明
- 手机 ADB 断开，本轮无真机验证；模型目录 UI 已编译通过。
- 尚未做「发现模型」写回目录（只保留测试连通性/发现 id 提示）。

## 3. 未完成（M1 继续）

1. Provider 编辑器仍是"单模型"表单；需要 DSH 式模型目录（多模型、上下文/最大输出/输入类型/推理档位/发现模型/恢复默认）。
2. 内置第三方 provider 目录（OpenAI/Anthropic/Kimi/DeepSeek/OpenRouter…）与自定义 provider 的两页式 UI。
3. 模型选择目前只在 chat 顶栏；设置页模型管理还没有列表/编辑。
4. `time_now`/`web_search` 工具本身失败（工具执行层）未单独排查；本轮修的是工具结果消息格式，需真机/真模型复测。
5. 新会话语义（删除/归档后进新会话、唯一"新会话"、首条消息后模型总结标题）尚未实现，设计已记录在 `bugs-and-next-plan.md`。

## 3. 真机
- 手机 ADB 已断开，本轮无真机验证；修的都是可单测的后端/协议层 + 可编译的 UI。

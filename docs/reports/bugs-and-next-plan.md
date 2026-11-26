# Bug 清单与下一步计划（2026-11-26）

## 0. 这次先修掉/已修的

| # | 问题 | 处理 | 状态 |
|---|---|---|---|
| 1 | DeepSeek 填 key 报 `PROVIDER_CONFIG: 非法 auth.ref: DEEPSEEK_API_KEYsk-...` | 根因：Provider 编辑框把「密钥引用(ref)」和「API Key 值」混成同一个字段，DeepSeek 模板又把 ref 预填成 `DEEPSEEK_API_KEY`，用户把 key 粘在后面 → ref 非法。现在拆成：API Key 单独只写字段（保存后写 credentials，不回显），ref 自动由 provider id 生成大写常量（如 `DEEPSEEK_API_KEY`），编辑时留空=保持原 key。 | ✅ 已修（待真机回归） |
| 5a | 抽屉滑出太多、右侧只剩细边 | 抽屉宽度改为屏幕 84%（右侧保留约 1/6），与官方 chat 一致；平板仍常驻 320dp。 | ✅ 已改 |
| 5b | 底部拖拽区黑条遮住 UI | targetSdk 35 强制 edge-to-edge，但未调用 `enableEdgeToEdge()`，系统给导航区加了黑色底/对比遮罩。已启用 edge-to-edge，Scaffold/Drawer 按 insets 布局。 | ✅ 已改（需真机确认） |
| 6 | Markdown 表格没生成 | 之前把 `\|` 行当等宽文本；现在解析连续表格行（跳过分隔行），渲染成真正的表格（表头加粗、单元格宽度、横向滚动）。 | ✅ 已修 |

## 1. 你报的其余问题（未修，需要设计）

### 1.1 chat 页没有模型列表（严重）
- 现状：`ChatSend` 不带模型选择，永远用 `providers.firstEnabled().model("chat")`；用户看不到也没法选当前模型。
- 目标：输入区上方/顶部显示当前 provider·model，点开可选；选择按会话记忆（或全局默认 + 会话覆盖）。
- 需要：`ChatSend` 增加 `providerId/modelId`，Bridge 校验；会话 sidecar 存选择；chat 顶栏展示。

### 1.2 重新生成不建新树，后续消息不跟着变（严重）
- 现状：`buildMessages` 对同 nodeId 只取最新 version，但**已生成的后续消息仍在同一线性日志里**。重生成旧回复后，后续问答还挂在旧回答之后；展示上也只是"版本"而非分支。
- 目标：真正会话树。每条消息有 `parentId`；重生成 = 新 assistant 节点 + 把 activePath 切到新节点；后续消息只属于其父路径。UI 提供「‹ 1/2 ›」+「切到此分支继续」；旧分支保留可回看。
- 备注：这推翻了之前"chat 单会话结构上不能分支"的简化；work 反正需要树，一次做对。
- 需要改动：SessionEvent 增 parentId；sidecar 存 activeLeaf/activePath；Query 返回 activePath；UI 按 path 渲染；regenerate/editResend 改父链。

### 1.3 深度思考只有开关，没有强度（严重，模型管理的一部分）
- 现状：`ChatSend.thinking: Boolean?` 只开/关；`ModelProfile` 只有 contextWindow/maxTokens。
- 目标（对齐 DSH 截图）：每个模型有**能力清单**：
  - 输入类型（文本/图片/音频）
  - 上下文窗口、最大输出
  - 是否支持推理 / 推理等级（off/low/medium/high…），每一档映射到该模型实际 API 参数（如 `reasoning_effort=low` 或自定义值/关闭思考）
  - 显示名、模型 id、协议
- 请求侧：`ChatSend` 带 `reasoningLevel`，由 model capability → request 参数映射；不支持时隐藏/禁用。
- 这也是"我们不是官方 chat 只接一个模型"的核心复杂度。

### 1.4 模型管理约等于没有（严重）
对照 DSH 截图，需要：
1. **第三方提供商**（内置目录：OpenAI/Anthropic/Kimi/DeepSeek/OpenRouter/Bedrock…，填 key 即用）
2. **自定义模型 API**（provider id、显示名、API 地址、协议、key、模型目录、发现模型）
3. **模型目录**：每模型可配上下文窗口、最大输出、输入类型、推理等级映射、默认/恢复默认
4. 多 provider、多模型、chat/fast 角色、失败重试策略、连通性测试/发现模型
5. 对话里选模型；会话记录用了哪个模型（B1 已存 model 字段，可直接展示）

### 1.5 滑动进/出会话管理不顺手（中）
- 已把 `gesturesEnabled=true`，但系统返回手势占用了左边缘；实测从极左边缘滑动不会开抽屉。
- 目标：明确手势方案二选一：
  a) 左边缘 200dp 内用 `systemGestureExclusion` 抢少量区域做抽屉（会削弱系统返回）；
  b) 保持系统返回，抽屉只靠汉堡/返回键，另加"正文区右滑开抽屉"（不与返回冲突）。
  建议 b（更安全），并给抽屉加明显可拖手柄。
- 同时抽屉打开时右侧露出的 1/6 应可点击关闭（现在露出内容会被遮罩拦截是正常的；可加点击关闭）。

### 1.6 UI/UX 整体不合格（长期）
- 交互手感、排版逻辑、视觉质感/干净利落度都不够；需要独立的设计系统阶段（token、间距、字阶、动效、图标光学对齐、卡片层级、空态/加载/错误统一），而不是继续堆功能。

## 2. 下一步计划（建议顺序）

### M1 模型服务（最高优先，先做对）
1. 数据模型：`ModelProfile` 扩展 capability（inputTypes、reasoningLevels、defaultReasoning、displayName、protocol 覆盖）
2. Provider 目录/模板 + 自定义 provider；key 只写；连通性测试；发现模型
3. 模型目录编辑器（上下文/最大输出/输入类型/推理等级 ↔ API 参数）
4. chat 顶栏模型选择 + 按会话记忆 + 请求带 provider/model/reasoningLevel
5. 请求参数映射器（OpenAI/Anthropic/DeepSeek/自定义兼容）
6. 测试：模型选择、reasoning 映射、无 key/不可用模型回退、会话记忆
验收：能添加 DeepSeek + 自定义中转站，chat 里切换模型，思考强度按模型生效并显示。

### M2 会话树
1. 事件加 `parentId`；sidecar 存 activePath/activeLeaf
2. regenerate/editResend 生成新分支并切 active path
3. Query 返回 activePath；UI 按路径渲染；版本/分支切换 + "在此继续"
4. 迁移：旧日志无 parentId → 按线性补链
验收：重生成旧回复后，后续消息不跟旧回答；切旧分支能完整回看并继续。

### M3 交互与 Insets
1. 手势方案定型（建议正文右滑开抽屉 + 返回键/汉堡关）；抽屉宽度/露出区域点击关闭
2. 预测式返回动画（`PredictiveBackHandler`）与抽屉/返回统一
3. 底部导航条/键盘/edge-to-edge 全场景检查（真机 + 手势导航 + 三键导航）
4. 输入区、抽屉、设置的手感统一（点击反馈/滚动/长按）

### M4 Markdown/内容
1. 链接点击、代码高亮、引用/嵌套列表、表格对齐与宽度
2. 流式渲染优化（避免每帧全量重排）
3. 图片/附件嵌入展示

### M5 设计系统/抛光（持续）
1. token：间距/圆角/字阶/颜色/阴影/动效曲线统一
2. 组件：按钮、输入、列表、卡片、弹层、空态/错误/加载
3. 真机逐屏 review（手机+平板、明暗、字号放大）

## 3. 结论：能不能把基座做明白？

**能，但现在不是继续加功能的时候。** 基础链路（事件、消息、会话、抽屉、输入、Markdown）已经通了，剩下的"最严重"是三件基础设施：**模型服务、会话树、交互/设计系统**。
建议冻结新功能，按 M1 → M2 → M3 → M4/M5 做；M1/M2 完成后基座才算"明白"，M5 是长期打磨。

需要你拍板：
1. 会话树：重生成是否默认把后续消息视为旧分支（推荐），旧分支保留可切换？
2. 模型选择：按会话记忆（推荐）还是全局默认？
3. 推理等级：是否每个模型自定义映射（推荐，贴合中转站），还是先做 off/low/medium/high 四档固定？
4. 抽屉手势：接受"正文右滑开抽屉"（推荐），还是要左边缘抢手势？

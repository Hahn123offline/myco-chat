# DSH 源码考古：插件框架 / provider / 会话 / agent loop

> 只读静态分析 `E:\deepseek-harness`（pnpm monorepo，未执行任何 git / install / build，未调用任何模型服务）。
> 行号引用格式 `路径:行`；根前缀 `E:\deepseek-harness\`，文档内省略。重点落在用户点名的 **provider profile 管理**（§1，最细）。

## 0. 一页结论（≤15 行）

1. DSH 没有"内核"：HTTP server、会话日志、agent loop、模型适配器、**设置页本身**都是 Cordis 插件（`docs/architecture.zh.md:9-11`）。
2. "profile"在 DSH 有两个含义，别混：**启动组装 profile**（`$DSH_HOME/profiles/<name>`，= 有序 bundle 列表 + `cordis.patch.yml`）与 **provider profile**（pi-ai 的一条 provider 配置对象）。用户要学的是后者 + 它所栖身的设置页。
3. provider 配置**不是**数据库实体，而是 Cordis 配置树里的一个条目（`id: llm-pi-ai` / `llm-deepseek`）的 config；设置页通过 `settings.mutate` 按路径写进 profile patch（`docs/subsystems/settings.zh.md:9-15`）。
4. 层级只有两层：**provider 定义 = 用户/部署级**（profile patch + home patch + CLI overlay），**模型选择 = 会话级**（`model/selection` 仅日志事件 + `request/header`）。没有工作区级 provider 配置（`docs/subsystems/session.zh.md:181-224`）。
5. 密钥与配置彻底分离：配置只写 `apiKeyEnv: DEEPSEEK_API_KEY` 这种引用，值在 `$DSH_HOME/.credentials.yaml`（version 1，`refs` + `records`），`describe()` 只回存在性/来源/可写性，永不回值（`packages/credentials/credentials-local/README.zh.md:71-111`）。
6. 多 provider 靠**路由键**并存：适配器注册 `provider` 名，请求里 `provider`/`model` 二选一；同名注册直接失败（`DUPLICATE_ADAPTER`），pi-ai 与 deepseek 两个适配器同时挂载是因为路由名不冲突（`packages/llm/llm/README.zh.md:84-93`）。
7. **能力路由不是一个 provider 表，而是每个能力一个 seam**：chat=`ctx.llm`；search/fetch=`ctx.web`（`searchProvider` 显式钉住，缺省"唯一可用则自动选"，`docs/config-catalog.zh.md:4249-4257`）；stt=`ctx.speechToText`（实验，显式选择）；**embedding / OCR 在 DSH 里根本不存在**。
8. 模型目录是"适配器目录 ∪ 已注册路由"的 join：声明式目录项 `LlmConfigurableProvider{ provider, displayName, settingsNs, settingsPath, declared?, error? }`，设置页把它和 live 路由对齐成行（`packages/llm/llm/src/types.ts:245-268`，`packages/client/ui-settings-models/src/client/store.ts:26-70`）。
9. 切换 provider 不动历史：已发过请求的会话沿用日志里记录的模型，选择只是追加 `model/selection` + 下一次 `request/header`（`docs/user/guide/providers.zh.md:37`）。
10. 会话 = 仅追加的类型化 `SessionEvent` 日志，模型历史由 `deriveMessages()` 投影；持久化是 seam，JSONL 后端按 `<根>/<项目目录>/<会话目录>/session.vN.jsonl[.zstd]` 存，带 generation 与相邻迁移（`packages/session/session-persistence-jsonl/README.zh.md:56-72`）。
11. 一轮 = 0..N 步；step = 一次模型请求 + 它请求的工具。所有模型可见输入必须先落日志（"模型可见 ⟺ 已记录"），请求是从日志派生的纯函数并深冻结（`packages/llm/llm/README.zh.md:133-140`）。
12. Web UI = 宿主 Cordis 树 + 浏览器 Cordis 树，中间只有 `Connection` RPC + Typert `@Remote`；客户端插件是 `dsh.client` 声明的惰性 CJS bundle，UI 组合用类型化 slot（`packages/client/modules/README.zh.md:12-50`，`docs/api-gateway.zh.md:7-15`）。
13. **手机客户端边界 = `api/remotes` + `client-connection`**，React/DOM 一点不用带；但 DSH 的 slot/组件层不可复用。
14. 用户要的"最顺手"体验，真正的工作量在 UI：DSH 的 Models 页手写了 ≈160 KB TSX/TS（ModelsSection 32K + ProviderEditor 25K + ModelListEditor 19K + store 16K + …）——**myco 应当 schema 驱动 + 少量自定义控件**，别照抄手写编辑器。
15. 薄抄清单：provider/model profile 字段、credentials ref/record、settings namespace+path+revision、session 事件表与派生、request/header 与 request/context 拆分、`dsh.*` manifest 字段、按 id patch 的分层、JSONL generation 迁移。**不要**抄：cordis loader/HMR/runtime-resolution、Typert 构建期代码生成、pi-ai 兼容矩阵、60+ 包广度、Desktop/ACP/SDK profile。

---

## 1. Provider 与 Profile（含 schema 草案）

### 1.1 名词与层级（先把歧义钉死）

| 层 | 是什么 | 存在哪 | 谁写 |
|---|---|---|---|
| 启动组装 profile | 应用界面 = 有序 bundle 列表 + patch 层 | `$DSH_HOME/profiles/<name>/package.json`（`dsh.profile.bundles`）+ `cordis.patch.yml` | `dsh --from-default-profile`、插件管理器 |
| provider profile（本报告主角） | 一条 provider 路由的完整配置对象 | 上面那份 `cordis.patch.yml` 里 `id: llm-pi-ai` 条目的 `config.providers.<route>`（pi-ai）或 `id: llm-deepseek` 条目本身（DeepSeek 直连） | 设置 → 模型页、手编 patch |
| 凭据 | 密钥值，按名引用 | `$DSH_HOME/.credentials.yaml`（version 1） | 设置页、手编文件 |
| 默认模型 | 新会话用哪个 `{provider, model, reasoningEffort}` | profile patch 中 `dsh-agent-default-model` 条目（volatile Config） | 模型选择器 / 设置页 |
| 会话级模型选择 | 这个会话用哪个模型 | 会话日志 `model/selection`（log-only）+ `request/header.config` | 会话内 `/model` 或选择器 |

配置层叠加顺序（先低后高，patch 按条目 id **整体替换 config** 或插入新条目）：`dsh.profile.bundles` 各 bundle patch → profile 的 `cordis.patch.yml` → `$DSH_HOME/cordis.patch.yml` → `--patch` overlay（`apps/cli/README.zh.md:41-48`，`docs/architecture.zh.md:23-25`）。

**结论：没有"工作区级 provider 配置"**。工作区（workspace）只是会话分组（§3.3），不参与 provider 定义。

### 1.2 一个 provider 定义包含什么（pi-ai，逐字段）

pi-ai 是"多 provider 适配器"，`providers` 字典的键就是路由名（`packages/llm/llm-pi-ai/src/config.ts:222`，字段全文见 `docs/config-catalog.zh.md:1750-1993`）：

| 字段 | 语义 | 设置页可编辑 |
|---|---|---|
| `apiKeyEnv` | 凭据引用名，按请求经 `ctx.credentials` 解析；省略 = configured-but-keyless（交给 provider 自己的 ambient 认证） | ✓（只输字面密钥，UI 自动派生引用） |
| `displayName` | 选择器显示名，缺省 = 路由键 | ✓ |
| `api` | 线路协议：`openai-completions` / `openai-responses` / `openai-codex-responses` / `azure-openai-responses` / `anthropic-messages` / `bedrock-converse-stream` 等；目录内路由可省略（继承 catalog） | ✓（自定义路由必填） |
| `baseURL` | 端点；缺省 = 已安装目录端点 | ✓ |
| `models: PiAiModelProfile[]` | **整体替换**目录；每条从同 id 的已安装模型补默认值 | ✓ 模型表 |
| `modelOverrides: Record<id, Omit<PiAiModelProfile,'id'>>` | 只改个别目录模型（"修 1 个留 37 个"）；与 `models` 并存会被拒绝 | ✗ |
| `compat: PiAiCompatProfile` | 网关线格式开关（20+ 项：`supportsDeveloperRole`、`maxTokensField`、`thinkingFormat`、`supportsReasoningEffort`、`cacheControlFormat`…）；路由级 = 模型默认，模型级逐字段胜出 | ✗（高级折叠区也不做） |
| `defaultContextWindow` / `defaultMaxTokens` / `defaultInput` | 未描述模型的容量/模态回退（262144 / 32768 / `[text]`） | 部分（模型行内联） |
| `headers` | 部署级请求头（Fetch 校验，Harness 归因头优先） | ✗ |
| `reasoning` / `thinkingBudgets` | 路由级推理等级 + 预算 | ✗ |
| `cacheRetention` / `transport` | 缓存保留、流式传输偏好 | ✗ |
| `timeoutMs` / `websocketConnectTimeoutMs` / `streamIdleTimeoutMs` | 超时三件套 | ✗ |
| `maxRequestImageBytes` / `requestImagePixelBudget` / `requestImageMaxBytes` | 图片预算 | ✗ |
| `retryPolicy` | normal/always + 重试次数（由 `llm-retry` 在 step 边界执行） | ✗ |

`PiAiModelProfile`（设置页真正编辑的那一层，`docs/config-catalog.zh.md:1855-1892`）：

| 字段 | 语义 |
|---|---|
| `id` | 发给 provider 的模型 id，也是会话日志/引用用的名字 |
| `name` | 选择器显示名，缺省 catalog 名 → id |
| `contextWindow` | 请求+响应总上下文 |
| `maxTokens` | 最大输出；配置后同时成为该模型每请求默认上限 |
| `input: Modality[]` | `text` / `image`；缺省继承 catalog → 路由 `defaultInput`；**是断言不是探测** |
| `reasoningEfforts` | `false`（声明非推理模型）或 `{ 等级名: 线上拼写 }`，`off` 可空 |
| `compat` | 模型级线格式开关 |

DeepSeek 直连路由字段较少（`packages/llm/llm-deepseek/src/config.ts:18-55`，表格见 `packages/llm/llm-deepseek/README.zh.md:54-74`）：`baseURL`（默认 `https://api.deepseek.com/anthropic`）、`thinking`（部署策略 enabled/disabled）、`reasoningEffort`（默认 `high`）、`maxTokens`（256000）、`defaultContextWindow`（1e6）、`models`（`id/name/description/contextWindow/maxTokens/inputModalities/imagePixelBudget/imageMaxBytes/systemPromptUpdate/toolUpdate`）、流空闲与图片/Files 预算、`retryPolicy`；密钥引用 `apiKeyEnv` 在 `llm-deepseek-api-key` 插件上。**两条路由不互相回退**：`deepseek-official` 只认 API Key 引用，`deepseek-account` 只认账号授权（`packages/llm/llm-deepseek/README.zh.md:88-90`）。

### 1.3 密钥怎么存（可直接照搬的设计）

- seam：`ctx.credentials` 两个键空间——**引用**（POSIX 环境变量形，`credentialRef('DEEPSEEK_API_KEY')`）与**记录**（`<owner>/<id>`，如 `llm-pi-ai/openai-codex`，存 OAuth grant 或 provider 环境值）（`packages/credentials/credentials/README.zh.md:52-58`）。
- API：`resolve(ref) → {value, source}?`、`describe(ref) → {configured, source?, writable}`（**永不回值**）、`set/unset`、记录侧 `readRecord/modifyRecord/deleteRecord/listRecords`（`modifyRecord` 是唯一写路径，变更函数看到独占时刻的记录）。
- 文件格式（`packages/credentials/credentials-local/README.zh.md:86-111`）：
```yaml
version: 1
refs:
  DEEPSEEK_API_KEY: sk-…
records:
  llm-pi-ai/openai-codex: { kind: grant, payload: { type: oauth, access: …, refresh: …, expires: 1786000000000 } }
```
- 解析优先级（高→低）：**启动环境**（只读，写入被拒）> 存储文件 > 项目 `.env` > `$DSH_HOME/.env`；启动器拍环境快照，之后 export 的变量不可见。
- 权限：文件仅属主可读写（POSIX 上非 600 拒绝加载；Windows 跳过 mode 检查）；同 UID 的 agent 工具进程仍可读，**这不是安全边界**，只是审慎；OS keychain/KMS provider 是延期项（`:188-190`）。
- 消费方按操作解析 → 轮换密钥下一请求即生效，无需重启（`:113-114`"消费方按操作解析"）。
- 事件：`credentials/reference-updated(ref)` / `credentials/record-updated(key)`，监听器失败被隔离（`:119-123`）。

### 1.4 多 provider 并存、模型列表级联、默认与按用途

- **并存**：`ctx.llm` 是适配器注册表，适配器可一次注册多条路由（pi-ai 的 `providers` dict；deepseek 的 official/account 两条）。注册原子：候选集合整体校验，冲突则旧路由继续服务（`packages/llm/llm-pi-ai/README.zh.md:157-159`）。
- **可配置目录**：适配器除了注册 live 路由，还声明"我能被配置出来但还没激活"的路由目录项（`declared?: boolean` 区分"用户新增"与"收窄内置"），零路由挂载即"休眠姿态"（`packages/llm/llm/README.zh.md:86`，`.../llm-pi-ai/README.zh.md:159`）。
- **目录级联**（设置页一次 join，三源对齐）：
  1. `llm/listProviders()`（live 路由）∪ `llm/listConfigurableProviders()`（声明目录）→ 行；
  2. 行对应的 settings namespace + `settingsPath` 决定它"configured / removable（只有用户层有、base 层没有）"；
  3. `credentials/describe(refs)` 批量补密钥状态；`credentialError` 只降级徽标不失败整页（`store.ts:200-271`）。
  4. 每行的**模型列表**：`settings` 里 `models` 数组 → 否则适配器 `listModels` 目录；"获取可用模型"走 `llm/discoverModels(settingsNs, {provider?, baseURL?, api?, apiKey?})`（`store.ts:96-108`，`packages/llm/llm/src/index.ts:589,630`）。
- **会话级目录**：`session.modelCatalog()` 返回 Host 代际目录：部署默认值 + 可路由 provider id + 成功的 provider 分组 + **相互隔离的 provider 失败**（某个 provider 挂了只影响它那组）（`packages/api/session-controller/src/types.ts:135-163`，`docs/subsystems/session.zh.md:810`）。
- **默认模型**：`ctx.agentDefaultModel.currentSelection()` / `saveSelection({provider,model,reasoningEffort?})`，进程级单一默认，写回 profile（`packages/core/agent-default-model/README.zh.md:30-57`，API 见 `docs/subsystems/core.zh.md:432-450`）；会话里选模型会**同时**改该会话选择并在后台保存为新会话默认（`docs/user/guide/providers.zh.md:37`）。
- **按用途选模型**：DSH 没有"主模型/小模型/embedding"角色表。它把"用途"拆成每个功能自己的 `{provider,model}` 配置：压缩摘要 `summarizationProvider/summarizationModel`（`docs/config-catalog.zh.md:711-714`）、会话标题 `session-title-*-llm`、子 agent `subagentModelSelection`（`docs/capability-seams.zh.md:634`）、语音转写 `ctx.speechToText` 的显式选择。**教训见 §1.9**。

### 1.5 切换 provider 时已有会话怎么处理

- 选择是**日志事件**：`model/selection: ModelSelection`（log-only，永不进入派生历史；`docs/persistence-catalog.zh.md:658-670`），由 `session.selectModel` 写入并"显式恢复会话后再选"（`docs/subsystems/session.zh.md:869-874`）。
- 下一次请求把新路由写进 `request/header.config`（provider/model/effort/采样标量）与 `request/context`（provider/model/contextWindow/systemPromptUpdate），**不重写历史**（`docs/subsystems/session.zh.md:181-224`）。
- 因此：已发过请求的会话继续用日志里的模型；新会话用默认；两者可以不同（`providers.zh.md:37`）。
- 缓存：换 provider/model 即换缓存域，从第一个变化的 token 起失效（`packages/llm/llm-deepseek/README.zh.md:180,192-194`）。
- 目录漂移：已保存的选择即使模型从目录消失仍可提交请求（core 调用接受未列出的 id），但 GUI 选择要求目录成员；缺失时选择器隐藏该 id 但**保留**持久选择（`packages/llm/llm/README.zh.md:28`，`ui-model-selection/src/client/catalog.ts:56-70`）。

### 1.6 连通性测试与错误呈现

没有"Ping/测试连接"按钮。等价物有三层：

1. **服务发现**：`discoverModels` 直接打端点——`openai-completions`/`openai-responses` 用 bearer `GET {baseURL}/models`；`anthropic-messages` 用 `x-api-key` + `anthropic-version` 的 `GET /v1/models?limit=1000`（只读第一页）；解析器接受标准 `data` 数组或富信息 `models` 对象；**不存储任何东西**（`packages/llm/llm-pi-ai/README.zh.md:113-115`）。失败以 `llm/model-discovery-rejected` 返回。
2. **保存时校验**：`settings.mutate` 先用完整 Config 校验再持久化，过期 revision 以 `settings/conflict` 拒绝；pi-ai 只严格校验**发生变化的** provider（未变的坏 provider 不阻塞其他编辑），但"只改名不修模型错误"会被整体拒绝（`packages/llm/llm-pi-ai/README.zh.md:121-123`）。
3. **行级诊断**：`LlmConfigurableProvider.error` 携带可修复诊断；受影响的 provider 仍显示编辑/删除入口（`ui-settings-models/README.zh.md:36`）。

错误码是稳定契约，绝不解析文案（`packages/llm/llm/README.zh.md:93`）：`NO_ADAPTER` / `MISSING_CREDENTIAL` / `INVALID_CREDENTIAL` / `UNKNOWN_MODEL` / `AUTH` / `QUOTA` / `ACCOUNT_QUOTA` / `RATE_LIMIT` / `CONTEXT_WINDOW_EXCEEDED` / `INVALID_REQUEST` / `SERVER` / `HTTP_<status>` / `TRANSPORT` / `ABORTED` / `TIMEOUT` / `EMPTY_RESPONSE` / `STREAM_CLOSED` / `MALFORMED_RESPONSE` / `UNSUPPORTED_REASONING_EFFORT` / `IMAGE_OFFLOAD_REQUIRED` / `UNSUPPORTED_CONTENT` / `INVALID_RESPONSE`；设置写侧 `settings/conflict`、`settings/rejected`；凭据侧 `credentials/*`。

### 1.7 DSH 设置页的实际交互（可照着画 UI）

页面：设置 → 模型（`@deepseek-ai/dsh-client-ui-settings-models`）。要点（`packages/client/ui-settings-models/README.zh.md:30-68`）：

- 每个"已配置的 provider"一行，**一次只展开一张编辑卡**；排序：DeepSeek 账号 > DeepSeek 官方 > 第三方按目录顺序（`store.ts:42-70`）。
- 行的绿色实心点 = 引用已确认配置；红色 = 具名引用确认缺失；无引用（provider 原生认证）不欠密钥（`store.ts:300-305`）。
- **API 密钥**是编辑卡主字段：一个只写输入框，从不问环境变量名。`credentials.set` 存到 profile 的引用下；profile 没有引用时派生 `<ROUTE>_API_KEY`（`store.ts:117-119`），pi-ai profile 把派生记进 `apiKeyEnv`，于是 `cordis.patch.yml` **从不出现密钥值**。
- 空 id / 重复 id / 空显式名 / 非正小数容量 → 写入前失败；密钥必须去空白后非空且全为可打印 ASCII（`[\x21-\x7E]`，与 `normalizeApiKey` 镜像）。
- **新增**只有一个入口"添加模型提供商"，卡片内分段两 tab：
  - **第三方模型提供商**：从"休眠目录"下拉选 pi-ai 内置 provider（`openai`/`anthropic`/`moonshotai`/`zai`…），端点/协议/模型目录由已安装 catalog 提供；
  - **自定义模型 API**：表单 = Provider ID（唯一、永久）、显示名称、API 地址、**API 协议**（下拉显示产品名，存 schema 标识符：OpenAI Chat Completions / OpenAI Responses / Anthropic Messages）、API 密钥、≥1 个模型。
  - 切换 tab 不丢草稿；有写入/探测进行中时锁 tab。
- **获取可用模型**：用表单当前地址+协议+密钥探测 → 可搜索多选（等宽原始 id）→ "添加所选"才写入；已存在的行保留用户改过的值；"全选/取消全选"只作用于可见结果。
- **模型行**：`id`、可选显示名、`contextWindow`/`maxTokens`（两列）、**输入类型**（文本/图片复选框，独占一行；DeepSeek 写 `inputModalities`，pi-ai 写 `input`；至少留一种；取消图片时 DeepSeek 还要移除 `imagePixelBudget`+`imageMaxBytes`）。"恢复默认模型"清空整个 models 覆盖。
- **字段边界**：卡片只编辑 API 密钥 + 精选折叠区（`baseURL`、模型目录、pi-ai 显示名/协议）；推理等级**刻意不可编辑**（它是按模型的能力，provider 级控件会被某些模型拒绝）；`headers`、timeout、retry、compat 留在 `cordis.patch.yml`。
- **删除**：只有当 profile 引用等于本页派生的 `<ROUTE>_API_KEY` 时才清凭据（自定义引用/环境凭据一律保留）；两个操作幂等。
- **持久化实现**：页面只持脱敏描述符，每次编辑都落到 `settings.mutate` 的**路径操作**（改一个字段一次 set、清一次 unset、删 provider 一次 unset 整棵），带 `expectedRevision`；settings 提交成功后才写凭据；订阅 `settings/document-updated`、`credentials/reference-updated`、`llm/adapters-updated`、`connection/reset` 收敛外部编辑，**无轮询**（`README.zh.md:78-93`）。
- 首次运行引导：有**任何**可用 provider 就直接结束该步；只有"一个都没有"时才问官方 DeepSeek 密钥；只读部署/适配器缺失时不渲染直接完成（`store.ts:308-376`）。
- 扩展席位：`settings.models.provider-card`（keyed，`entryKey = settingsNs`）与 `settings.models.footer`（list），仓库外插件可插卡片（`README.zh.md:66`）。

### 1.8 myco provider profile schema 草案（TS + JSON Schema + 两个内置模板）

设计取舍（相对 DSH 的改动，都有理由）：① myco 没有 pi-ai 的"已安装目录"，**一律显式 models**，不做两层覆盖语义；② 显式 `auth.type: 'none'`，避免 DSH 那种"无 key 本地网关仍需占位凭据"的坑（`.../llm-pi-ai/README.zh.md:232`）；③ 增加 `roles` 角色映射（DSH 无，myco 需要）；④ 协议枚举收窄到 myco 真正实现的三种；⑤ `compat` 只留最常踩的 4 个开关。

```ts
// packages/contracts/src/provider.ts —— 纯类型，zod 可一一对应
export type ProviderKind = 'deepseek-messages' | 'openai-completions' | 'openai-responses'
export type Modality = 'text' | 'image'
export type ModelRole = 'chat' | 'fast' | 'embedding' | 'vision' | 'ocr' | 'search' | 'stt' | 'tts'

export interface ModelProfile {
  id: string                 // 端点接受的 id；永久，会话日志按它引用
  name?: string              // 选择器显示名，缺省 = id
  contextWindow: number      // 必填：压缩阈值要靠它
  maxTokens: number          // 必填：每请求默认输出上限
  input: Modality[]          // 端点断言，缺省 [text]
  reasoning?: false | { efforts: Record<string, string | null>; default?: string } // 等级名→线上拼写
}

export type AuthProfile =
  | { type: 'api-key'; ref: string }   // ref 形状 ^[A-Z][A-Z0-9_]*$，只存引用
  | { type: 'none' }                    // 本地/内网无鉴权
  // 预留: { type:'oauth', ref:string } | { type:'account', provider:string }

export interface CompatProfile {       // 只覆盖 myco 实际支持 openai-* 的开关
  supportsDeveloperRole?: boolean       // 默认 false（很多网关拒绝 developer 角色）
  maxTokensField?: 'max_tokens' | 'max_completion_tokens'
  supportsReasoningEffort?: boolean
  thinkingFormat?: 'none' | 'deepseek'
}

export interface ProviderProfile {
  id: string                 // 路由键：小写连字符；永久；= settings 键 = 会话日志里 provider 的名字
  kind: ProviderKind         // 一个 provider 一种协议
  displayName?: string
  baseUrl?: string           // 缺省用 kind 内置端点
  auth: AuthProfile
  models: ModelProfile[]     // 非空
  roles?: Partial<Record<ModelRole, string>>  // 角色 → models[].id
  defaults?: { contextWindow?: number; maxTokens?: number; input?: Modality[] }
  compat?: CompatProfile
  headers?: Record<string, string>
  limits?: { timeoutMs?: number; streamIdleTimeoutMs?: number; maxRequestImageBytes?: number }
  retry?: { mode: 'normal' | 'always'; maxRetries?: number }
  enabled?: boolean          // 与层叠加的 disabled 语义等价，UI 里是开关
}
```

JSON Schema（2020-12，数组/对象结构裁掉，保留 UI 需要的约束与 `x-ui` 提示）：

```json
{
  "$id": "https://myco.dev/schema/provider-profile.json",
  "type": "object", "additionalProperties": false,
  "required": ["id", "kind", "auth", "models"],
  "properties": {
    "id": { "type": "string", "pattern": "^[a-z][a-z0-9-]{1,31}$" },
    "kind": { "enum": ["deepseek-messages", "openai-completions", "openai-responses"] },
    "displayName": { "type": "string", "maxLength": 64 },
    "baseUrl": { "type": "string", "format": "uri" },
    "auth": { "oneOf": [
      { "type": "object", "required": ["type", "ref"],
        "properties": { "type": { "const": "api-key" },
          "ref": { "type": "string", "pattern": "^[A-Z][A-Z0-9_]*$", "x-ui:secret-ref": true } } },
      { "type": "object", "required": ["type"], "properties": { "type": { "const": "none" } } } ] },
    "models": { "type": "array", "minItems": 1, "x-ui:widget": "model-table",
      "items": { "type": "object", "additionalProperties": false,
        "required": ["id", "contextWindow", "maxTokens"],
        "properties": {
          "id": { "type": "string", "minLength": 1, "x-ui:monospace": true },
          "name": { "type": "string" },
          "contextWindow": { "type": "integer", "minimum": 1 },
          "maxTokens": { "type": "integer", "minimum": 1 },
          "input": { "type": "array", "minItems": 1, "items": { "enum": ["text", "image"] } },
          "reasoning": { "oneOf": [ { "const": false }, { "type": "object" } ] } } } },
    "roles": { "type": "object", "additionalProperties": {
      "enum": ["chat","fast","embedding","vision","ocr","search","stt","tts"] } },
    "compat": { "type": "object", "additionalProperties": false,
      "properties": { "supportsDeveloperRole": { "type": "boolean" },
        "maxTokensField": { "enum": ["max_tokens", "max_completion_tokens"] },
        "supportsReasoningEffort": { "type": "boolean" },
        "thinkingFormat": { "enum": ["none", "deepseek"] } } },
    "limits": { "type": "object" }, "retry": { "type": "object" },
    "enabled": { "type": "boolean", "default": true }
  }
}
```

两个内置 profile（`config/providers.yaml`，可直接给 UI 当"模板"预填）：

```yaml
- id: deepseek
  kind: deepseek-messages
  displayName: DeepSeek 官方
  baseUrl: https://api.deepseek.com/anthropic
  auth: { type: api-key, ref: DEEPSEEK_API_KEY }
  models:
    - { id: deepseek-flash,   name: DeepSeek V4.1 Flash, contextWindow: 1000000, maxTokens: 384000,
        input: [text, image], reasoning: { efforts: { off: null, low: low, high: high, max: max }, default: high } }
    - { id: deepseek-v4-pro,  name: DeepSeek V4 Pro,     contextWindow: 1000000, maxTokens: 393216, input: [text] }
  roles: { chat: deepseek-flash, fast: deepseek-flash, vision: deepseek-flash }
  limits: { streamIdleTimeoutMs: 300000 }
- id: local
  kind: openai-completions
  displayName: 本地 OpenAI 兼容（无密钥）
  baseUrl: http://127.0.0.1:11434/v1
  auth: { type: none }                     # 关键差异：DSH 这里仍需占位凭据
  models:
    - { id: qwen3-8b, contextWindow: 32768, maxTokens: 4096, input: [text] }
  roles: { chat: qwen3-8b, fast: qwen3-8b }
  compat: { supportsDeveloperRole: false, maxTokensField: max_tokens, supportsReasoningEffort: false }
```

补充约定（myco 侧须自己定，DSH 里有答案但依赖其基建）：
- **模型 id 与能力**：内置模板只做"初始值"，启动后允许"从端点刷新"覆盖；DeepSeek 的模型元数据建议用 `GET /models` 拉（`context_window`/`max_output_tokens`/`input_modalities`），别硬编码。
- **provider 定义存放**：`$MYCO_HOME/config/providers.yaml`（人可读、可 git diff），密钥与它物理分离；每次写入用 `revision`（单调整数）+ 文件锁，UI 带 `expectedRevision`，冲突回 `settings/conflict` 语义（照搬 DSH）。
- **会话级选择**：只存 `{provider, model, reasoningEffort?}` 三元组，作为会话事件 `model/selection`；请求时把实际路由写进 `request/header`。**会话不持有 provider 定义**，这样删 provider 不会让历史失效。

### 1.9 角色路由 vs seam 路由：myco 该怎么折中

DSH 的答案是"能力 = seam"：chat 只有一条通道 `ctx.llm`；search/fetch 一个商品化的 `ctx.web`（`searchProvider`/`fetchProvider` 配置在 `@deepseek-ai/dsh-web`）；STT 另一个；embedding/OCR 干脆没有。好处是替换一个 provider 就整体换掉产品行为（`docs/architecture.zh.md:136-140`）；代价是"我要用便宜的小模型做标题/压缩"只能逐功能配 `{provider,model}`。

myco 建议：**`roles` 只用于 chat 家族内的质量/成本分层**（`chat`/`fast`/`vision`），跨模态能力各自独立：
- `chat`（含 tool calling）= `ctx.llm` 的统一流式接口，`roles.chat` 定默认；
- `embedding` = 独立 `ctx.embeddings` seam（OpenAI 兼容 `/embeddings`），provider 表复用同一份凭据存储；
- `search` / `ocr` / `stt` / `tts` = 各自 seam + 各自 provider 表（DSH 已证明这样最省事），**不要**塞进 llm provider profile 里。
- 派生小任务（会话标题、压缩摘要、记忆抽取）默认 `roles.fast`，可逐功能覆写。

---

## 2. 插件系统（cordis 模型、贡献点、加载与隔离）

### 2.1 cordis 的插件模型（`docs/cordis-primer.zh.md:9-13`）

- **插件 = 实现 Service 的对象**：带可选 `inject` 与 `apply(ctx)` 的函数，或 `Service` 子类。
- **上下文 = 容器**：服务占稳定 `ctx.<key>`；别人按键查找，从不 import 具体实现。
- **依赖注入**：`inject` 声明后自动等依赖就绪；加载顺序由依赖表达，不手排 start 序列。
- **类型化事件**：声明合并写事件名；5 种分发（`emit` / `waterfall` / `parallel` / `serial` / `bail`），模式是公开契约（`:23-27`）。waterfall 是环绕中间件：`(...args, next)`，不调 `next()` 即短路（`:35-39`）。
- **注册即可逆副作用**：`ctx.effect()` / `ctx.on()` 安装，reload/teardown 自动撤销；每个注册都要有 disposer（`:49-51`）。
- **scope**：贡献要么全局，要么归属恰好一个 scope（活跃 agent 就是自己 scope 的 key）；带作用域注册**不向子 agent 继承**，子树行为用 lineage 数据表达；同名时最具体者胜（shadowing）（`docs/glossary.zh.md:11-21`）。
- **isolate realm**：preset 用 `isolate` 把"一个提供方 + 它的消费方"关进同一 realm，从而做出"这个会话的 bash 不一样"（`docs/architecture.zh.md:150`，`packages/boot/app-boot/README.zh.md:140`）。
- Loader 额外能力：`!!js` 表达式节点、按插件上下文插值 `config`、`disabled` 动态求值（`docs/cordis-primer.zh.md:43-45`）。

### 2.2 一个插件能贡献什么（`docs/architecture.zh.md:146-167` 的归属表，节选）

| 贡献 | 机制 |
|---|---|
| 模型 provider | 在 `ctx.llm` 注册适配器（可选声明可配置目录项） |
| 面向模型的工具 | `ctx.tools` 注册；schema 自动进提示词组装 |
| 用户命令（斜杠） | `ctx.commands`，无需模型轮次 |
| 提示词段/变量 | `ctx.systemPrompt.section({order, complete?})` / `.variable()`，`{{name}}` 插值，严格失败（`packages/core/system-prompt/README.zh.md:56-75`） |
| 模型可见上下文（持久） | `agent.inject()`，落成 user-role 消息（与普通消息同样可回放/压缩） |
| 持久会话状态 | 扩展 `SessionEventMap`（declaration merging）+ 从日志渲染/回放 |
| 派生投影 | `ctx.sessionProjections` 注册折叠单元；载体用 `snapshot()` |
| 插件私有 KV | `ctx.storageDomain`（`storage` + 后端 + `storage-domain` 三行；后端 json/sqlite）（`packages/storage/storage/README.zh.md:34-52`） |
| UI | client bundle（`dsh.client`）+ `ctx.slots.register/inject` 类型化 slot |
| Web Client Chat 节点 | `ConversationNodeDefinition` + keyed renderer |
| 压缩/标题/子 agent/搜索/沙箱/shell/fs 后端 | 各自 seam 的 provider |
| 会话持久化后端 | 实现 `SessionPersistence`（`create/open/stat/list/export`） |

### 2.3 manifest 与加载

package.json 的 `dsh` 字段（`packages/util/package-manifest/src/types.ts:8-94`）：

```jsonc
{ "name": "@acme/dsh-thing", "version": "0.1.0", "private": true, "type": "module",
  "peerDependencies": { "@deepseek-ai/dsh": "^0.x" },
  "dsh": {
    "manifestVersion": 1,
    "bundle":  { "patch": ["patch/base.yml", "patch/ui.yml"] },   // 本包作为组合包贡献的 patch 文件
    "profile": { "bundles": ["@deepseek-ai/dsh-base", "@deepseek-ai/dsh-web-app"] }, // 本包作为 profile 的层序
    "client":  { "platform": "web", "inject": ["..."], "immediately": false, "external": ["pkg/subpath"] }
  } }
```

- 目录形态（`docs/cookbook/adding-a-package.zh.md:9-23`）：`packages/<group>/<pkg>/{package.json, tsconfig.json, src/index.ts, locale/en.json, README.md}`；client 包另加 `dsh.client`、`./client` 导出、共享 tsdown preset（`:38`）。
- **发现/启用/禁用**：启用 = 在 profile 的 `dsh.profile.bundles` 有序列表里；单条目开关 = `cordis.patch.yml` 里该条目 `disabled: true/false`（插件管理器只改**最后一条匹配覆盖**，没有就追加；bundle 开关改 `package.json` 的 bundles 列表）（`packages/boot/plugin-manager/README.zh.md:40-44`）。安装/删除 = `dsh plugin`（转发 pnpm）+ 选择状态；peer 版本检查（`@deepseek-ai/dsh*` peer 范围 vs 运行时版本），不兼容需 `compatibility.json` 显式豁免（`packages/boot/app-boot/README.zh.md:52-56`，`plugin-manager:66-75`）。
- **HMR**：`dsh-hmr` 监听 profile manifest + 两份用户 patch，串行重载；未启用则改动需重启。web 模板实时重载，headless/sdk/acp 仅启动时应用（`docs/architecture.zh.md:27`）。

### 2.4 "插件坏了不拖垮主进程"靠什么

- 每个插件一个 **fiber**，失败是局部：optional 条目启动失败只警告并继续；**required 条目**（全局列表：`agent-loop`、`webserver`、`web-runtime`、`modules`、`connection`、`headless-runner`、`acp`、`sdk-jsonrpc-server`）失败才拆卸整个应用并非零退出（`packages/boot/app-boot/README.zh.md:43`）。
- 完整失败矩阵见 `:98-113`：import 失败/schema 校验失败/`apply()` 同步或异步 throw/服务不可用/端口绑定失败 → 保留成功兄弟插件；**未被 await 的 promise rejection 或同步回调抛错 → 致命**（这是硬边界，需要在插件 API 上引导）。
- patch 级隔离：组合包解析/manifest 读取/patch 加载失败 → 跳过该 bundle 并 `skippedBundles` 上报，不改选择状态；profile 与用户 patch 错误则启动失败（`:50`）。
- 注册原子性：适配器/目录注册先整体校验候选集合，被拒绝的变更让旧状态继续服务（`packages/llm/llm/README.zh.md:139`）。
- 事件监听器隔离：`credentials` 的 `notifyUpdated` 保证每个监听器都跑，抛错/拒绝只记录不改变已提交操作（`packages/credentials/credentials/README.zh.md:119-123`）。
- 运行中重载：新条目未激活/配置变化导致诊断变化才使 reload 失败；被移除 fiber 必须完成释放（`app-boot:94-96`）。

---

## 3. 会话与持久化

### 3.1 数据结构

`Session` = 内存里**仅追加**的类型化事件日志，是 agent 完整交互历史的唯一真源；**LLM 消息历史由 `deriveMessages()` 从日志投影，从不单独存**（`docs/subsystems/session.zh.md:5`）。核心事件表（`packages/core/session/src/types.ts`，逐条 JSDoc 在 `docs/subsystems/session.zh.md:27-174`）：

| 事件 | 作用 |
|---|---|
| `turn/start{turn}` / `turn/end{turn,reason}` | 轮次括起；可零 step |
| `step/start` / `step/end` | 一次模型请求 + 其工具 |
| `user/message` (UserMessage) | 人类提示 / `agent.inject()` 注入 / steering / goal 续行，`source` 区分 |
| `developer/message` | 对话中工具增删（带 `headerSeq` 回指声明） |
| `system/message` | **渲染后的系统提示词就是 surface 第 0 号节点的普通事件**——提示词变化 = 替换/追加节点，不是改 header |
| `assistant/message` | 成功请求的组装消息 + 内嵌**精确带时间紧凑流** + `usage`；中断轮次带 `interrupted: true` 保留已交付前缀 |
| `assistant/attempt` | 失败/重试/取消/stream error 的 settlement，不产生模型可见历史 |
| `tool/call` / `tool/result` | 原始 JSON 参数字符串；结果可带 `error{name,code,reason}` 与不透明 `meta`（必须 JSON 可序列化） |
| `request/header` | 请求信封快照：`config`(provider/model/effort/采样) + `adapterDefaults` + `tools`；log-only，`foldRequestHeader()` 取最新 |
| `request/context` | 路由元数据：provider/model/contextWindow/systemPromptUpdate；只在变化时追加 |
| `session/end-seed{inherited?}` | 区分继承历史与后续生命周期工作（fork 边界） |

扩展方式：declaration merging 往 `SessionEventMap` 加事件（compaction 就是这么加的），生成的持久化目录会收录所有成员（`docs/persistence-catalog.zh.md`）。约束：`seq` 连续、`data` 必须无损 JSON（`Session.append` 用 `isJsonValue` 从源头拒绝）、surface 事件才能产生消息。

### 3.2 持久化

- seam：`ctx.sessionPersistence`（`create` / `open(id,'read'|'write')` / `stat` / `list` / `flush`）；句柄 `read/append/flush/close`；**没有按 id 直接 append/load 的方法**（`packages/session/session-persistence/README.zh.md:36-49`）。
- 不变量：仅追加且 `seq` 连续；撕裂尾部永不送达读者（写路径先截断）；损坏以 `SessionPersistenceCorruptionError`；每个后端实例单写者；`flush` 才是持久性屏障（`:83-90`）。
- 崩溃恢复：日志保留未闭合的最终轮次；恢复时 agent-loop 计算 `interruptedTurnClosers`（合成 `tool/result` 错误、未闭合 `step/end`、`turn/end{interrupted}`）作为普通批次追加；只读观察方只在内存里配平（`:57-61`）。
- 格式版本：唯一真源 `SESSION_FORMAT_VERSION`（当前 4，`docs/session-format-status.zh.md:20-24,32-35,45-49`）；JSONL 后端把每个会话存成 `<root>/<项目目录>/<会话目录>/session.vN.jsonl[.zstd]`，**已提交代际绝不改名/替换/删除**，写 open 才发布后继，相邻迁移一个包负责一步 `vN→vN+1`（`packages/session/session-persistence-jsonl/README.zh.md:56-72,84-90`）。
- `stat`/`list` 只读 header 不读事件；历史格式的 `stat` 需要全库指纹（`:88-90`）。

### 3.3 session 与 workspace

- workspace 是**独立的持久注册表**（`ctx.workspaceRegistry`，靠 `ctx.storageDomain` 落盘）：命名目录的**有序列表** + 每个目录下的会话（`packages/workspace/workspace/README.zh.md:12,36-49`）。
- 归属规则：会话**按创建时 cwd** 落到对应项目；一个会话只属于一个项目；目录记不住/被移动的会话保持 Ungrouped（`:71-73`）。
- "隐藏会话"与"移除项目"都**不删数据**：隐藏只是从可见列表消失（有后台工作的会话隐含拒绝，可先停再隐）；移除项目后会话变 Ungrouped，再加同一目录从空项目开始（`:75-79`）。
- 所以：**workspace 是会话的展示分组/投影，不是父子存储关系**；session header 带 cwd，workspace 记录用 cwd 匹配。

---

## 4. Agent loop 与时序

一轮（turn）在领取首条输入前打开，在不再欠工作时关闭；一步（step）= 一次模型请求 + 它请求的工具；一轮含 0..N 步（`docs/glossary.zh.md:37-39`）。完整时序（`docs/architecture.zh.md:85-110`，图见 `docs/agent-lifecycle.zh.md`）：

```text
turn/start
  claim 下一批 next-step 输入 + 至多一条排队消息
  assemble 提示词段 + 工具 schema
  -> agent/pre-step（waterfall；可改写/拒绝；可设 startsRequestSeries）
     拒绝 / 首批为空 → 关轮次且不产生 step
     step/start
     agent/request（waterfall）→ ctx.llm.prepareCall(config, signal)   # 绑定具体适配器代次
     以准备好的能力做同步准入：system/message 按节点 reconcile；user/message 只准入一次
     按需记 request/header、request/context
     从日志派生并冻结请求（深冻结，不可改写）
     流式分发（llm/stream waterfall）→ agent/assistant-stream start/chunk*/end
     成功 → assistant/message（内嵌紧凑流+usage）；失败 → assistant/attempt → agent/request-error（可重试）
     tool/call* → tools/pre-execute → [单调守卫] → [ctx.approval] → tools/execute → 工具体
                → projectContent → tools/post-execute → finalizeContent → tools/result*
     step/end
     还有欠账或新输入 → claim → 下一步
  -> agent/turn-stopping（serial，本轮终检）
turn/end
```

关键点：

- **两套事件域**：`turn/*`、`step/*`、`system|user|assistant|tool/*` 是持久会话事件；`agent/*` 是实时协调（inbox/状态/steering/续跑）；`llm/*`、`fs/*`、`tools/*` 是能力 seam 的策略钩子（`docs/architecture.zh.md:72-76`）。
- **三个 waterfall 可改写一次工具调用**：`tools/pre-execute` → 单调守卫 → `tools/execute`（超时/重试/指标用环绕包装）→ `tools/post-execute`；之后 `projectContent`/`finalizeContent`/`tools/result` 固定内容不变式（`docs/tool-execution-pipeline.zh.md:8-63`）。
- **取消是协作式的**：`prepareCall` 两个异步阶段的取消都不提交系统提示词/用户消息；流取消保留已交付前缀（`assistant/message{interrupted:true}`）。
- **重试不重跑 pre-step**：同一 open step 内重新 prepare + reconcile，不重复组装/pre-step/用户准入（`docs/agent-lifecycle.zh.md:85-87`）。
- **压缩挂在两个点**：`agent/pre-step` 处理压力；`agent/request-error` 仅处理规范上下文溢出；先可选剪枝工具结果，再摘要；恢复只在 surface replacement 代际推进时重试（`:87`）。默认阈值 `floor(min(W×0.8, W−O−B))`，`B=65536`，保留 `W−O` 的 16%（`packages/compaction/compaction-basic/README.zh.md:62-78`）。
- **扩展点清单**：`agent/pre-step`（改/拒输入）、`agent/request`（改请求配置/补 provider+model）、`llm/stream`（包装流）、`tools/*`（策略/审批/改写）、`agent/request-error`（重试）、`agent/turn-stopping`（终止）、`agent.inject()`（加模型可见上下文）、`ctx.systemPrompt.section`（提示词）、`ctx.sessionProjections`（派生状态）。
- 并发：`maxParallelToolCalls` 限制"并行安全"调用（默认 10）；独占调用单独跑并构成排序屏障（`packages/core/agent-loop/README.zh.md:46-57`）。

---

## 5. UI/客户端边界

- **两个 Cordis 树**：宿主 Node 树（真实服务）与浏览器树（client 插件）。宿主侧 `ctx.clientModules` 扫描启用的 Loader 条目、把每包的 `dsh.client` 组合成启动图注入 `window.__DSH_BOOT__`；浏览器侧 `ctx.modules` 惰性物化 bundle（执行 bundle 只注册 factory，模块副作用在首次使用时才跑）（`packages/client/modules/README.zh.md:12,34-50`）。
- **模块基座**：外壳初始化冻结的 `PLATFORM_MODULES`（React、Cordis、静态 UI 库）；`dsh.client.external` 只加基座之外的精确请求；组合期拒绝畸形/缺失/自请求/同步环（`:44-46`）。
- **UI 组合**：`ctx.slots.register()` 类型化 React 组合——`SlotMap` 编译期注册 key/cardinality/scope/owner props/keyed props；`root` 是唯一内建声明；注册与声明走 effect 生命周期，owner 折叠时贡献递归移除（`docs/subsystems/slots.zh.md:5,13-21`）。**功能插件不 import 别的功能插件组件**。
- **前后端协议**：`api-remotes`（选哪些 namespace 上线）+ `api-gateway`（`@Remote`/`@RemoteScope` 一元与流式；对象参数用 `TypertLookupMap` 的 wire id 解析）+ `client-connection`（RPC carrier、认证、取消、`/api` HTTP bridge）。Client 侧是普通对象上的具体函数，不是 Proxy；`ctx.remote.<ns>` 与 `agentCtx.remote.<ns>`（`docs/api-gateway.zh.md:7-15,56-80`）。
- **手机客户端边界**：要做一个非浏览器客户端，只需要
  1. 生成/复用 `api/remotes` 的 client 声明（类型 + codec），**不需要 React**（`docs/api-gateway.zh.md:80` 明确 "不依赖 React"）；
  2. 实现 `client-connection` 的 RPC carrier（WebSocket 多路复用 `/api/remote.mux` + `/api` HTTP：上传、静态资源）；
  3. 消费会话事件流（`session/event` follow）与 `session/modelCatalog`、`selectModel`、`inspect`、`settings/credentials` controller；
  4. 自己重写 UI（slot 与组件层不可复用）。
  换言之：**DSH 的"UI 内核"在 contracts/remotes/connection，React 与 slot 只是其中一个渲染实现**。纯文本/通知型客户端几乎零共享代码。

---

## 6. myco 可落地的模块划分建议

原则：**contracts 先行、插件系统借现成、provider 层自写、UI schema 驱动、客户端与 UI 解耦**。

```
myco/
  packages/contracts/          # 纯类型 + zod/JSON Schema：ProviderProfile、SessionEventMap、Remote 描述符
  packages/kernel/             # 会话日志（内存）、effect/disposer、事件分发（emit/waterfall/serial/bail）
  packages/plugin-host/        # manifest 解析(dsh.* 同构)、加载/启停/隔离、profile 层叠 + by-id patch
  packages/settings/           # 分层配置、revision 乐观锁、路径操作(mutate)、schema 投影
  packages/credentials/        # refs/records seam + file 后端（未来 keychain 后端）
  packages/llm/                # 抽象：流式 StreamChunk、prepareCall、模型能力、provider 注册表+路由
  packages/llm-deepseek/       # deepseek-messages 方言（官方 anthropic 兼容端点）
  packages/llm-openai/         # openai-completions / responses（含 compat 开关子集）
  packages/embeddings/         # 独立 seam（OpenAI 兼容 /embeddings）
  packages/tools/              # 工具注册表 + pre/execute/post 三 waterfall + 审批
  packages/agent/              # loop：turn/step 状态机、inbox、prompt 组装、压缩触发器
  packages/session-store/      # JSONL 后端 + header 版本；迁移链预留
  packages/context/            # 提示词段/变量、时间、AGENTS.md、@file 提及
  packages/api/                # Remote 注册表 + RPC carrier（服务端）+ 生成的 client 声明
  packages/client-core/        # 与 UI 无关的客户端状态机：模型目录、会话列表、发送队列、事件跟随
  packages/ui-web/             # React + slots（仅 Web）
  packages/ui-mobile/          # 手机外壳（React Native/原生），复用 client-core，不碰 ui-web
  apps/server/ apps/web/ apps/mobile/
  config/providers.yaml        # 内置 provider 模板（§1.8）
```

落地顺序（对应 MVP 目标）：`contracts → kernel/session-store → settings+credentials → llm+llm-deepseek+llm-openai → agent+tools → api+client-core → ui-web → 插件 manifest/启停 → 记忆/人格/主动性（后续 seam）`。

---

## 7. 薄抄 / 自重写 清单

**可以薄抄（概念 + schema + 目录形态，直接搬）**
1. provider profile / model profile 字段集与"断言 vs 探测"的语义（§1.2）：`contextWindow`、`maxTokens`、`input`、`reasoningEfforts`、`compat` 子集。
2. credentials 的 **ref（环境变量形）+ record（`<owner>/<id>`）+ describe 永不回值** 三段式，以及"配置只带引用"的纪律（§1.3）。
3. settings 的 **namespace（条目 id）+ settingsPath + revision + 路径操作** 四件套（`docs/subsystems/settings.zh.md:9-15`）——这是"设置页不手写持久化"的关键。
4. 会话事件表核心十几种 + `deriveMessages()` 投影 + `request/header` 与 `request/context` 拆分（§3.1）。
5. "持久事实 vs 实时协调"两套事件域，以及 `waterfall` 五模式分发语义（§2.1）。
6. `dsh.*` manifest 四字段（bundle/profile/client/manifestVersion）+ 按条目 id 的 patch 分层 + `disabled` 开关（§2.3）。
7. per-session `preset.config.plugins`（子插件列表，`packages/preset/agent-preset/README.zh.md:28-48`）——"不同会话不同能力集"的最简实现。
8. JSONL 每会话一文件 + header 版本 + generation/相邻迁移的**规则**（不一定要马上实现整条迁移链）（§3.2）。
9. 模型目录 join（live 路由 ∪ 可配置目录 ∪ 凭据状态）与"行级诊断"的 UI 数据模型（§1.4）。
10. 错误码清单与"绝不解析文案"的约定（§1.6）。

**不要抄 / 必须自重写**
1. **cordis 全栈**：vendor 的 loader、`!!js` 插值、runtime resolution、HMR、peer 版本检查、`compatibility.json` 豁免——为 60+ 包与 pnpm monorepo 设计的重机器，myco MVP 只需要 ctx/服务键/inject/effect/事件分发（约 300–500 行）。直接 vendor cordis **核心**可以，别 vendor loader。
2. **Typert 构建期代码生成流水线**：两阶段 tsc + tsdown + 生成 `typert.host.js`/`typert.remote-client.*`。myco 用 zod schema 手写/半自动 Remote 描述符即可。
3. **pi-ai 依赖与 20+ 项 compat 矩阵**：DSH 的目录/兼容层是它接 30 家 provider 的代价；myco 只做 2–3 家 + 一个 OpenAI 兼容方言。
4. **DSH 的"已安装 catalog"双层语义**（`models` 替换 vs `modelOverrides` 增改）：myco 无 catalog，一层显式 models 足够。
5. **手写 Models 设置页**（≈160 KB TSX/TS）：改成 schema 驱动表单 + 3 个自定义控件（模型表、发现多选、密钥只写框）。
6. **Electron/Desktop、ACP/SDK/headless profile、遥测、Claude/Codex hooks、PTC runtime、plugin inventory、`subagent-acp/codex/claude-code`**：都是产品广度，不是 MVP 扩展性。
7. **每请求深冻结 + 冻结证据复用**这类性能优化：先保证正确性，请求对象可以浅拷贝不可变。
8. **"twins adapter"**（同一家 provider 两个结构不同的适配器：`llm-deepseek` 直连 + `llm-pi-ai` 通用）：myco 用一个适配器 + 方言表即可（DeepSeek 的 Messages 方言单独一个 `kind`）。

---

## 8. 未解问题（含我的倾向）

1. **是否 vendor cordis 核心？** 倾向：是（MVP 省掉插件生命周期/DI/事件分发风险，DSH 已证明可行）；但不要带 loader/HMR，自己写 manifest 与 profile 层叠。
2. **provider 配置要不要"内置 catalog"隐藏层？** 倾向：不要。内置模板只是 `config/providers.yaml` 的初始值，用户面永远看到同一层显式 `models`；"从端点刷新"是显式动作、覆盖写回，不做 pi-ai 式的继承合并。
3. **角色路由的边界？** 倾向：`roles` 只覆盖 chat 家族（chat/fast/vision）；embedding/search/ocr/stt/tts 各自 seam + provider 表，不塞进 llm provider。
4. **密钥存储做不做 OS keychain？** DSH 用纯文件（同 UID 可读，明说是审慎不是边界）。倾向：MVP 文件 + 权限校验 + `credentials` 后端可替换；桌面端后续加 DPAPI/Credential Manager 后端，接口不变。
5. **会话格式要不要一上来就带迁移链？** 倾向：v1 单文件 JSONL + header `version` 字段 + 读侧拒绝未知版本；相邻迁移链等第一次破坏性变更前再上（DSH 的 generation 规则照抄，迁移机制延后）。
6. **要不要 HMR？** 倾向：MVP 不要（重启生效），但插件 API 保留 `dispose`/effect 语义，后续可加。
7. **模型发现（`GET /models`）是否自动跑？** 既含外发请求又会用密钥。倾向：只在用户点"获取可用模型"时手动触发；保存 provider 时**绝不**自动探测；启动时只用本地模板。
8. **多会话并发的写模型**：DSH 是"每后端实例单写者 + 每会话写句柄"，myco 手机+桌面可能同时写。倾向：先单进程单写者（服务端唯一写），客户端只读 + 通过服务端 RPC 写；客户端本地只缓存 UI 状态。
9. **"模型选择 vs 默认"的冲突**：DSH 选模型会同时改默认，容易让人误以为历史变了。倾向：myco 区分两个动作——"仅此会话"（默认）与"设为所有新会话默认"（显式）。

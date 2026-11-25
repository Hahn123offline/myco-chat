# DeepSeek API 现状（访问日期 2026-11-25）

> 事实来自**公开文档实际抓取** + **本地 DSH 源码实现证据**（`E:\deepseek-harness`），两者交叉验证。
> 全程未调用任何模型服务/API。页面均于 2026-11-25 抓取。

## 0. 一页结论（和旧认知不同的地方）

1. **`deepseek-chat` / `deepseek-reasoner` 已彻底停用**：2026-07-24 15:59 UTC 起请求直接失败。现存只有 `deepseek-flash`（V4.1-Flash-1017）与 `deepseek-v4-pro`（V4-Pro-0813）。
2. **上下文 1M、最大输出 384K**（旧认知 64K/8K 作废）。`max_tokens` 默认：非思考 8K、思考 64K、`effort=max` 时 128K。
3. **思考模式默认开启**（effort 默认 `high`）；思考下 `temperature`/`top_p` 等采样参数**静默失效**。
4. **Web Search/Fetch、OCR、ASR、TTS 全部有官方 API**（2026-11-13 全家桶发布），不再是"App 专有"。
5. **服务端搜索自 2026-11-18 12:00（北京）起不再单独计费**，结果按 token 收费；仅客户端直连 Search/Fetch API 按 $0.75/千次。
6. **峰谷计价**：北京时间周一至周五 9:00–12:00、14:00–18:00 双倍价，其余时段（含周末/法定节假日全天）半价。
7. **三协议并存**：OpenAI Chat Completions、OpenAI Responses、Anthropic Messages；路径前缀不统一（§2）。
8. `deepseek-v4-pro` **不支持图像**；视觉只在 `deepseek-flash`。
9. `frequency_penalty`/`presence_penalty` 已**全局废弃**（传了不生效）。
10. **DSH 本地实现已落后于新 API**：web 搜索仍烧一整个模型轮次、无 OCR/TTS 客户端、默认模型名用的是退役别名 `deepseek-v4-flash`（§9）。

来源：[模型&价格](https://api-docs.deepseek.com/quick_start/pricing/)、[更新日志](https://api-docs.deepseek.com/updates/)、[全家桶发布](https://api-docs.deepseek.com/news/news261113/)、[Search&Fetch](https://api-docs.deepseek.com/guides/search/)、[思考模式](https://api-docs.deepseek.com/guides/thinking_mode/)

---

## 1. 模型与定价

### 1.1 可用模型（只有两个）

| | `deepseek-flash` | `deepseek-v4-pro` |
|---|---|---|
| 底层版本 | V4.1-Flash-1017 | V4-Pro-0813 |
| 上下文 / 最大输出 | 1M / 384K（393216） | 1M / 384K |
| 思考模式 | 支持，**默认开**，effort 默认 high | 同 |
| JSON / Tool Calls / Responses / Anthropic | 全 ✓ | 全 ✓ |
| 对话前缀续写 / FIM（Beta） | ✓ / 仅非思考模式 | ✓ / 仅非思考模式 |
| 图像理解 | ✓ | **✗**（替换为 `[Unsupported Image]`） |
| 并发上限 | 2500 | 500 |

- 旧名 `deepseek-v4-flash`、`deepseek-v4-flash-vision-exp` 仍可调用，但模型已下线，请求被路由到 V4.1-Flash-1017 并按 Flash 价计费。
- `deepseek-chat`/`deepseek-reasoner`：2026-04-24 公告、**2026-07-24 15:59 UTC 停用**，旧请求失败（二手交叉验证：[腾讯云](https://cloud.tencent.com.cn/developer/article/2715785)）。
- `GET /models` 可拿机器可读元数据（`context_window`、`max_output_tokens`、`input_modalities`、`effort.supported_levels`、`api_capabilities.anthropic_messages.system_prompt_update`），建议 myco 启动时拉取而非硬编码。

来源：[模型&价格](https://api-docs.deepseek.com/quick_start/pricing/)、[首次调用](https://api-docs.deepseek.com/)、[Lists Models](https://api-docs.deepseek.com/api/list-models/)、[更新日志](https://api-docs.deepseek.com/updates/)

### 1.2 价格（USD / 1M tokens）

| | flash 空闲 | flash 高峰 | v4-pro 空闲 | v4-pro 高峰 |
|---|---|---|---|---|
| 输入·缓存命中 | $0.003 | $0.006 | $0.022 | $0.044 |
| 输入·缓存未命中 | $0.15 | $0.30 | $0.66 | $1.32 |
| 输出 | $0.60 | $1.20 | $1.98 | $3.96 |

人民币价：flash 命中 0.02/0.04 元、未命中 1/2 元、输出 4/8 元；v4-pro 0.15/0.30、4.5/9.0、13.5/27.0 元。
高峰时段 = UTC 01:00–04:00、06:00–10:00（北京 9:00–12:00、14:00–18:00）周一至周五，不含中国法定节假日。扣费优先扣赠送余额。

来源：[价格 EN](https://api-docs.deepseek.com/quick_start/pricing/)、[价格 ZH](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)

### 1.3 上下文缓存

默认开启、无需改代码；按"缓存前缀单元"整段匹配，命中部分按命中价计。响应 `usage.prompt_cache_hit_tokens` / `prompt_cache_miss_tokens`（亦有 `prompt_tokens_details.cached_tokens`）。持久化时机：每次请求的"用户输入结束位置"和"模型输出结束位置"各生成一个前缀单元；检测到跨请求公共前缀也会落盘；长输入/输出按固定间隔切单元。best-effort 不保证 100% 命中，缓存数秒构建、不用后几小时到几天清除。`user_id` 可用于 KVCache 隔离。
缓存命中/未命中价差 1:50 → **多轮前缀是否稳定几乎决定成本量级**。

来源：[Context Caching](https://api-docs.deepseek.com/guides/kv_cache/)、[限速与隔离](https://api-docs.deepseek.com/quick_start/rate_limit/)

---

## 2. OpenAI 兼容细节与坑

### 2.1 端点与鉴权

| 协议 | Base URL | 文档示例路径 |
|---|---|---|
| OpenAI Chat Completions | `https://api.deepseek.com` | `POST /chat/completions` |
| OpenAI Responses | `https://api.deepseek.com` | `POST /responses` |
| Anthropic Messages | `https://api.deepseek.com/anthropic` | `POST /anthropic/v1/messages` |
| 工具 API | 同上 | `/v1/tools/search|fetch|ocr|tts|asr` |
| Files API | 同上 | `POST /files`（Anthropic 侧 `/v1/files`） |

鉴权 `Authorization: Bearer <key>`；Anthropic 也接受 `x-api-key`，`anthropic-version` 被忽略。**路径前缀不统一**：模型端点是裸 `/chat/completions`、`/responses`，工具端点是 `/v1/tools/*`。`claude*` 名映射：`claude-opus*` → `deepseek-v4-pro`（按 Pro 计费），其余 `claude*` → `deepseek-flash`，非 `claude` 开头未知模型名 400。

来源：[首次调用](https://api-docs.deepseek.com/)、[Search&Fetch](https://api-docs.deepseek.com/guides/search/)、[Anthropic API](https://api-docs.deepseek.com/guides/anthropic_api/)、[Responses API](https://api-docs.deepseek.com/guides/responses_api/)

### 2.2 思考模式（旧认知完全没有）

- 开关：OpenAI 格式 `{"thinking":{"type":"enabled|disabled"}}`；或 `reasoning_effort: none|low|high|max`。Anthropic 用 `reasoning.effort`（`none` 关闭）+ `output_config.effort`；Responses 用 `reasoning.effort`。
- **默认开启、默认 high**。effort 映射：`minimal`/`low`→low、`medium`/`high`/`xhigh`→high、`max`/`ultra`→max。
- 思考内容在 `reasoning_content`（与 `content` 同级）。
- **回传规则（最容易 400）**：请求**带 `tools`** 时，历史每轮 `reasoning_content` 必须原样回传并拼进上下文，漏传直接 400；**不带 `tools`** 时无需回传，传了也被忽略。
- 思考模式下 `temperature` 无效（不报错）；`frequency_penalty`/`presence_penalty` 全局废弃。
- `top_p`：flash 思考模式生效（下限 0.95，更低按 0.95）、非思考固定 1.0；v4-pro 思考模式忽略（开工具固定 0.95，否则 1.0）、非思考生效。
- 思考模式下 `tool_choice` 的 `required` 与具名工具**不支持**（400）；`any` 退化为 `auto`。

来源：[思考模式](https://api-docs.deepseek.com/guides/thinking_mode/)、[Chat Completions API](https://api-docs.deepseek.com/api/create-chat-completion/)、[Anthropic API](https://api-docs.deepseek.com/guides/anthropic_api/)

### 2.3 其他字段差异

- `user_id` 是**顶层字段**，OpenAI SDK 需放 `extra_body`；Anthropic 放 `metadata.user_id`。
- DeepSeek 扩展：`safety_check:{"level":"high"}`、`title:{"enabled":true}`（公共应用必须开 safety_check）。
- JSON Output：`response_format={"type":"json_object"}` + prompt 必须含 "json" 与样例；解析失败时非 JSON 内容被替换为空格（`content` 可能全空格而 `finish_reason=stop`）。
- `strict` 工具（Beta）：需 `base_url=https://api.deepseek.com/beta`，每个 function 设 `strict:true`，object 必须 `additionalProperties:false` 且全属性 required；`minLength`/`maxLength`/`minItems`/`maxItems` 不支持。
- 流式：Chat Completions 以 `data: [DONE]` 结束；Anthropic/Responses **不发 `[DONE]`**（Responses 以 `response.completed|incomplete|failed` 收尾）。
- Responses API 明确**无状态**：`previous_response_id`/`conversation`/`store`/`background`/`truncation` 不支持（静默忽略）；超上下文直接 400。
- 保活：非流式连续返回空行、流式发 `: keep-alive`；发送后 10 分钟未开始推理则断连。

来源：[Chat Completions API](https://api-docs.deepseek.com/api/create-chat-completion/)、[JSON Output](https://api-docs.deepseek.com/guides/json_mode/)、[Tool Calls](https://api-docs.deepseek.com/guides/tool_calls/)、[Responses API](https://api-docs.deepseek.com/guides/responses_api/)

### 2.4 与 myco 现有实现的直接冲突（参考 `reference/trace-memory/app/v2/models/`）

- `OpenAiCompatProvider.chatEndpoint()` 拼 `baseUrl + "/v1/chat/completions"`，而官方文档只示例 `/chat/completions` → **`/v1` 是否仍被接受未确认，上线前必须实测**（§8.1）。
- `ModelsConfig` 默认 `maxContext=64000`（真实 1,000,000）；默认 `caps=JSON,PROMPT_CACHE,LONG_CTX`，缺 `TOOL_CALL`/`VISION`。
- `ModelRequest.thinking` 为空时 codec **不发** thinking 字段 → 服务端默认开思考（贵、慢、temperature 失效）。这是**当前最可能的线上事故点**。
- `frequency_penalty`/`presence_penalty` 若仍在别处拼装，属无效字段。

---

## 3. Web Search / Fetch

### 3.1 服务端工具（对话内联网，按 token 计费）

| 能力 | 开启 | 行为 |
|---|---|---|
| Web Search | 声明 `deepseek_web_search` + **关思考** | 模型自行决定是否查，带 citations 作答 |
| Deep Thinking Search | 同上 + **开思考** | 多步推理中自主 search/open_page/find_in_page，约 ≤20 次工具调用 |
| URL Reading | 声明 `deepseek_read_url` | 只读**最后一条 user 消息**中链接（去重后≤前 10 个），抓取入上下文 |
| 图片搜索 | `deepseek_web_search` + 消息带图 | `kind`=`image_search`/`deep_image_search`，结果多 `image_url` |

- 声明形如 `{"type":"deepseek_web_search","name":"deepseek_web_search"}`；`tool_choice` **不能**指向这两个工具（400）。
- **仅 `deepseek-flash` 支持**这两个 server tool。
- 结果块 `server_tool_use` + `web_search_tool_result`/`web_fetch_tool_result`；链接正文不返回、加密在 `signature`，多轮必须原样回传（含 `kind`/`signature`）才能恢复。
- 计费：**2026-11-18 12:00 起工具调用不再单独计费**，结果按 token 计。
- 声明 web tools / safety_check / title 时，`temperature`、`top_p`、`stop`、`seed`、`n` 被服务端忽略。

### 3.2 独立 Search / Fetch API（agent/RAG，按次计费）

| API | Endpoint | QPS | 价格 |
|---|---|---|---|
| Search | `POST /v1/tools/search` | 账户级 100 | $0.75/千次成功（5 元） |
| Fetch | `POST /v1/tools/fetch` | 25（另有限域名 QPS） | $0.75/千次 |

- Search 入参：`query`(必填 1–128 字符)、`count`(1–50,默认 10)、`ori_question`、`localization`、`need_content`(默认 false)；支持 `site:`/`-site:`/`-`/`"..."`/`lang:`/`after:`/`before:` 运算符。
- 出参 `results[]`：`rerank_score`(0–3,≥1.5 高质量)、`title`、`url`、`snippet`(100–200 字)、`summary`(600–800 字给模型读)、`content`、`site_name`、`site_icon`、`date`、`authority`(0–4,-1 未知)。
- Fetch 入参仅 `url`(≤8192 字符，禁含账号密码)，返回 `{title, content}`（Markdown）。
- **只对成功请求计费**（Search 需非空 `results`，Fetch 需有正文）；失败/空结果/超时不收费；`count` 不影响单价。
- **图片搜索尚未支持**（仅 web tools 有）。

### 3.3 两个 web search 工具不能混（坑）

| 工具 | 支持模型 | 场景 |
|---|---|---|
| `deepseek_web_search`/`deepseek_read_url`（DeepSeek 扩展） | **仅 flash** | 复刻 App |
| `web_search`/`web_search_20250305`（Anthropic 原生）、Responses `web_search` | **v4-pro**（flash 上被忽略） | Claude Code / Codex 兼容 |

两者不能同时声明。Responses 的 `web_search` 服务端执行且不计费。选错模型 → 工具被静默忽略。

来源：[Web Tools](https://api-docs.deepseek.com/guides/web_tools/)、[Search & Fetch](https://api-docs.deepseek.com/guides/search/)、[模型&价格](https://api-docs.deepseek.com/quick_start/pricing/)

---

## 4. OCR / ASR / TTS / 多模态

**结论：四类能力官方全部有 API**（2026-11-13 起）。注意：**DSH 本地源码里没有 OCR/TTS 客户端，ASR 也不是 DeepSeek 官方 ASR**（§9）——不能从"DSH 没实现"推断"官方没有"。

### 4.1 OCR — `POST /v1/tools/ocr`
multipart 仅两个字段：`file`、`purpose`(`text_recognition`|`rich_layout_recognition`)，多传即 400。支持 PDF/DOC/DOCX/PPT/PPTX 与 PNG/JPEG/WebP/GIF/BMP/TIFF（按内容嗅探）。返回按页 `pages[].text`、`finish_reason`(`stop`/`length`)、`page_count`、`usage`（仅供参考不计费）。逐页结果保留版面 token `<｜ref｜>标题<｜/ref｜><｜box｜>[[x1,y1,x2,y2]]<｜/box｜>`，坐标归一化 0–1000（左上原点），可据此裁剪。限额：≤100 MiB、≤1000 页、上传须 120 s 内完成且暂停≤30 s、总时长 180 s；账户同时最多 **15 个**识别（断开不取消，跑完仍计费）。计费 **$0.225/千页**（1.5 元），按成功 `page_count`，失败/超时不收费。
与 File Parsing（`POST /files` + `purpose` → `file_id` 在对话中引用）区别：OCR 不存 Files、不进模型上下文；文件解析同价。

### 4.2 ASR — `POST /v1/tools/asr`
单次整段音频、**不支持流式**、无语言参数（自动检测）。格式 16-bit PCM WAV 或 Ogg Opus；WAV 支持 8k/16k/22.05k/24k/32k/44.1k/48k Hz；双声道只识别左声道。限额：≤8 MiB、10 分钟内传完、**音频时长 ≤120 秒**（长音频必须切片）。返回 `{request_id, text, duration_ms}`。21 语言：zh/yue/en/ja/ko/de/fr/es/pt/it/ru/ar/hi/id/vi/th/tr/ms/fil/pl/hu。计费按秒（不足 1 s 按 1 s），刊例 $0.116/h（0.78 元），**限时 5 折 $0.058**（0.39 元）。并发 50。

### 4.3 TTS — `POST /v1/tools/tts` 或 `wss://api.deepseek.com/v1/tools/tts`
音色仅 `stella`（女,默认）与 `tide`（男）；**不支持语速控制、不支持断点续传**。语言 zh/yue/en/ja/ko/ms/ar/id/th/vi（自动判断）。HTTP：`{text, voice_id, format(wav|pcm), filter_markdown(默认 true)}`，≤10,000 计费字符、请求体 ≤64 KiB、单请求 ≤30 分钟音频；`pcm` 为 16-bit 单声道 24 kHz 裸流。WebSocket：查询参数 `voice_id`/`format`(`pcm`|`opus`)/`filter_markdown`，鉴权可 `Authorization` 头或子协议 `Sec-WebSocket-Protocol: tts, bearer.<key>`；发 `{"event":"text","text":...}` 分片 + `{"event":"finish_text"}`；60 s 无文本结束、会话 ≤30 分钟、25 s ping。计费：**汉字 1 字算 2 字符**，其余 1 字符；刊例 $0.45/万字符（3 元），**限时 5 折 $0.225**（1.5 元）。并发 50。未过审返回 400 `Content Exists Risk`。

### 4.4 图像理解（Vision，不是 OCR API）
仅 `deepseek-flash`。三种传图：base64 data URL / 公网 URL / Files `file_id`。格式 JPEG/PNG/GIF/WebP；`detail`: `low`(下采样 512×512)/`high`/`original`/`auto`。限额：URL ≤8192 字符、请求体 ≤48 MiB、单图 ≤32 MiB（`file_id` ≤64 MiB）、≤600 图/请求、单边 ≤8192 px（≥15 图时降 4096 px）、URL 下载 ≤60 s。计费：图像折算 token，**每图上限 1024 token**（<≈544×544 放大，大图缩到 ≈1300×1300 等效像素），与文本同价。图片可出现在 user/system/tool 消息，**assistant 消息插图 400**。

**替代方案**（需要 v4-pro 或非 DeepSeek 时）：OCR → PaddleOCR/Tesseract 或云 OCR；ASR → faster-whisper/FunASR 本地（长音频必须分段，官方单次 120 s）；TTS → edge-tts/CosyVoice/云 TTS（官方仅 2 音色、无语速）。

来源：[OCR](https://api-docs.deepseek.com/guides/ocr/)、[ASR](https://api-docs.deepseek.com/guides/asr/)、[TTS](https://api-docs.deepseek.com/guides/tts/)、[Vision](https://api-docs.deepseek.com/guides/vision/)、[File Parsing](https://api-docs.deepseek.com/guides/document_parsing/)、[IT之家 2026-11-13](https://m.ithome.com/html/1026960.htm)（人民币价交叉验证）

---

## 5. 限流、鉴权、错误码

**没有 RPM/TPM，只有并发数。** 账户级并发：`deepseek-flash` 2500、`deepseek-v4-pro` 500；从请求发出到响应完成算一个并发，按账户计（不按 key）。可申请扩容（不额外收费），扩容后仍按 `user_id` 分别限 2500/500。工具 API 独立限额：Search QPS 100、Fetch QPS 25、OCR 同时 15、File Parsing 同时 15、TTS 50、ASR 50。超限统一 429。
余额：`GET https://api.deepseek.com/user/balance` → `is_available`、`balance_infos[].{currency(CNY|USD), total_balance, granted_balance, topped_up_balance}`。

| 码 | 含义 | 处理 |
|---|---|---|
| 400 | 请求非法（含 `Content Exists Risk`、思考模式非法 `tool_choice`） | 改请求，不盲目重试 |
| 401 / 402 | 鉴权失败 / 余额不足 | 修 key / 充值 |
| 408 / 413 | 上传太慢 / 文件过大 | 检查网络 / 减小文件 |
| 422 | 参数非法（Anthropic `type:"custom"` 客户端工具等） | 改请求 |
| 429 | 并发或 QPS 超限 | 退避重试 |
| 500 / 503 / 504 | 内部错误 / 过载 / 网关超时 | 稍后重试 |

重试：408/429/5xx 可重试；402 充值后；其他 4xx 不改请求勿重试。`finish_reason` 中 `insufficient_system_resource`、`aborted` 可重试，`content_filter` 不可。超时：非流式可能长时间无输出（保活空行），**10 分钟未开始推理才断连**；OCR/解析最长 180 s/120 s → 建议 chat ≥120 s、OCR/解析 ≥240 s。

来源：[限速与隔离](https://api-docs.deepseek.com/quick_start/rate_limit/)、[错误码](https://api-docs.deepseek.com/quick_start/error_codes/)、[Get User Balance](https://api-docs.deepseek.com/api/get-user-balance/)、各工具 guide

---

## 6. 成本估算

**假设**（myco 单用户开发/内测，1 元≈$0.14，按人民币价）：每天 200 轮、每轮输入 6K tok（缓存命中 60%）、输出 1.5K tok（含思考）；50 次服务端搜索/天（工具免费，结果≈6K tok/次入上下文）；OCR 20 页/天、ASR 10 分钟/天、TTS 2 万字符/月；一半用量落高峰（峰谷均摊≈1.5×空闲价）。

| 项目 | 用量/月 | 单价(元) | 月成本(元) |
|---|---|---|---|
| 输入·缓存命中 | 21.6M tok | 0.02–0.04 | ≈0.6 |
| 输入·未命中 | 14.4M tok | 1–2 | ≈21 |
| 输出 | 9M tok | 4–8 | ≈54 |
| 搜索带来的额外输入 | 9M tok（按未命中） | 1–2 | ≈13 |
| OCR / 文件解析 | 600 页 | 1.5/千页 | 0.9 |
| ASR | 5 小时 | 0.39–0.78/时 | 2–4 |
| TTS | 2 万字符 | 1.5–3/万字符 | 3–6 |
| **合计（flash）** | | | **≈95–100 元/月（$13–14）** |

三类放大器：①**思考默认开**且不控输出 → 思考 token 按输出价计，输出可能翻 3–5 倍（+100–200 元/月）；②**缓存前缀断裂**（消息重排、system prompt 带时间戳/随机 id、工具结果顺序抖动）→ 命中率 60%→0，输入成本从 ≈0.6 元涨到 ≈30 元/月；③**全换 v4-pro** → 约 3–4 倍，350–400 元/月。若搜索改走独立 Search API（不经模型）：1,500 次/月×5 元/千次 = **7.5 元/月**，但少了模型自主改写 query 与引用。

来源：价格见 §1.2；用量为本文自设假设，非官方数据。

---

## 7. 对 myco 内置 provider profile 的建议（字段草案）

保留现有 `models.properties` 语义，**扩展字段 + 修默认值**，不引入"每厂商一个类"：

```properties
provider.deepseek.kind=cloud
provider.deepseek.baseUrl=https://api.deepseek.com
provider.deepseek.model=deepseek-flash
provider.deepseek.cost=cheap
# 容量（旧默认 64000 已过时）
provider.deepseek.maxContext=1000000
provider.deepseek.maxOutput=393216
provider.deepseek.timeoutMs=120000
provider.deepseek.caps=JSON,TOOL_CALL,VISION,PROMPT_CACHE,LONG_CTX,REASONING,WEB_SEARCH,READ_URL,OCR,ASR,TTS
# 思考模式（必须显式，否则服务端默认开）
provider.deepseek.thinking=disabled
provider.deepseek.reasoningEffort=low
provider.deepseek.maxTokens.nonThinking=8192
provider.deepseek.maxTokens.thinking=65536
provider.deepseek.maxTokens.thinkingMax=131072
# server tools（仅 flash 支持）
provider.deepseek.tool.webSearch=deepseek_web_search
provider.deepseek.tool.readUrl=deepseek_read_url
provider.deepseek.tool.webTools.model=deepseek-flash
provider.deepseek.tool.strict.baseUrl=https://api.deepseek.com/beta
# 独立能力端点（chat 之外的调用走这里）
provider.deepseek.ep.search=/v1/tools/search
provider.deepseek.ep.fetch=/v1/tools/fetch
provider.deepseek.ep.ocr=/v1/tools/ocr
provider.deepseek.ep.asr=/v1/tools/asr
provider.deepseek.ep.tts=/v1/tools/tts
provider.deepseek.ep.tts.ws=wss://api.deepseek.com/v1/tools/tts
provider.deepseek.ep.files=/files
provider.deepseek.ep.responses=/responses
provider.deepseek.ep.anthropicBase=https://api.deepseek.com/anthropic
# 计价（峰谷 + 缓存命中，CostEstimator 需要）
provider.deepseek.price.currency=CNY
provider.deepseek.price.inputHit.offPeak=0.02
provider.deepseek.price.inputHit.peak=0.04
provider.deepseek.price.inputMiss.offPeak=1
provider.deepseek.price.inputMiss.peak=2
provider.deepseek.price.output.offPeak=4
provider.deepseek.price.output.peak=8
provider.deepseek.price.peakWindows=Asia/Shanghai:Mon-Fri:09:00-12:00,14:00-18:00
provider.deepseek.price.ocrPerKPage=1.5
provider.deepseek.price.asrPerHour=0.39
provider.deepseek.price.ttsPer10kChars=1.5
```

`Capability` 扩展（现仅 `VISION/TOOL_CALL/JSON/PROMPT_CACHE/LONG_CTX`）：`REASONING`、`WEB_SEARCH`、`READ_URL`、`OCR`、`ASR`、`TTS`、`FIM`、`RESPONSES_API`。关键：**能力必须能落到"端点 + 计费单位"**，不能只有布尔开关——OCR/ASR/TTS 按页/秒/字符计费，不属于 chat。
再加三个行为开关（都是真实踩坑）：`passBackReasoningContent`（带 tools 时必须回传，否则 400）、`stripSamplingWhenThinking`（思考下不发 temperature/top_p）、`endpointStyle=chatCompletions|responses|anthropicMessages`（三协议字段差异大，别在一个 codec 里悄悄降级）。

---

## 8. 仍未核实/存疑的点（不可靠，必须实测）

1. **`/v1/chat/completions` 是否仍被接受**：官方只示例 `/chat/completions`，工具 API 用 `/v1/tools/*`；历史版本两者都收。**myco codec 拼的正是 `/v1/chat/completions`，最高优先级实测项**。⚠
2. **发票/账单接口**：只找到 `GET /user/balance`，未找到任何发票/消费明细 API；大概率只能控制台人工开票。⚠ 未找到 ≠ 绝对没有。
3. **模型下线政策**：只观察到个案（旧名给 3 个月通知；V4-Flash 下线但保留旧名路由），无正式 deprecation/SLA 页面。⚠
4. **ASR/TTS 限时 5 折截止时间**未写，随时可能回刊例价。⚠
5. **Search API 图片搜索**文档明说"暂不支持、以后加"，时间未知。⚠
6. **峰值时段判定**：中英页换算一致，但"中国法定节假日调休"如何判定未细说。⚠
7. **ASR 120 秒上限**：是否计划支持长音频/流式未知；切片边界影响识别质量。⚠
8. **`Responses web_search` 仅 v4-pro**、`deepseek_web_search` 仅 flash，官方未解释原因；把"能联网"当模型无关能力会静默降级。⚠
9. **本文未做真实请求验证**：字段默认值、`/v1` 前缀、思考默认开这三条必须用最小请求实测一轮才能进生产。

---

## 9. DSH 实现证据 vs 官方文档（应 Lead 要求追加）

源码位置：`E:\deepseek-harness`（本机 checkout，非 app.asar）。以下每条均已读原文核实。

| 事实 | DSH 实现证据 | 官方文档 | 判定 |
|---|---|---|---|
| 会话协议 | Anthropic Messages：baseURL 默认 `https://api.deepseek.com/anthropic`，追加 `/v1/messages`、`/v1/files`（`llm-deepseek/src/messages-api.ts`、`README.zh.md`） | 同 `/anthropic/v1/messages` | **一致，上游原生**；证明 Messages 端点对普通 API key 开放 |
| 默认模型目录 | `deepseek-flash`(text+image, ctx 1,000,000)、`deepseek-v4-pro`(text, ctx 1,000,000)（`src/models.ts`） | 完全一致 | 一致 |
| 思考强度 | `off/low/high/max`；`output_config.effort` 序列化，`off`→`thinking.type=disabled` | 同 | 一致 |
| system prompt 中途更新 | flash 目录声明 `systemPromptUpdate: in-history` | `GET /models` 暴露 `api_capabilities.anthropic_messages.system_prompt_update` | 一致（DSH 消费该元数据） |
| 工具中途变更 | `toolUpdate: addition-only`；`in-history` 需 beta 头 `mid-conversation-tool-changes-2026-07-01` | 公开文档**未见**此 beta 头；`anthropic-beta` 除 files 外被忽略 | **DSH 私有/未公开 beta**，myco 不要依赖 |
| web 搜索 | Messages + 原生 `web_search_20250305`（`max_uses`），默认模型 `deepseek-v4-flash`；README 称"DeepSeek 不提供专用搜索端点" | 2026-10-17 起有 `/v1/tools/search|fetch`；11-18 起服务端搜索不再单独计费 | **DSH README 已过时**；myco 应改用独立 Search API（省一个模型轮次） |
| 默认搜索模型名 | `deepseek-v4-flash`（已退役别名，仍被路由到 V4.1-Flash-1017） | 旧名仍接受但模型已下线 | DSH 用兼容别名可跑，应换 `deepseek-flash` |
| Files API | `/v1/files` + `anthropic-beta: files-api-2025-04-14`，图片 ≤600/请求 | 同 | 一致 |
| 图像 token 计量 | 14 px patch、3:1 降采样、544×544 放大下限、单图 ≤1024 token、正方形 ≤1302×1302 | ≈544×544 起放大、缩到 ≈1300×1300、≤1024 token/图 | 一致（DSH 更精确） |
| 推理回传 | pi-ai `compat.requiresReasoningContentOnAssistantMessages` | 带 tools 必须回传，否则 400 | 一致 |
| 顶部扩展字段 | `dsh_plugin_packages`、`dsh_session_log`（默认开） | 无 | **DSH 私有**；发到官方端点属未知字段 |
| 归因标头 | `x-deepseek-harness-user-id/-session-id/-compact`、`user-agent` | 无 | **DSH 私有** |
| 账号登录 | 浏览器 OAuth → `x-dsh-auth-token`；读 `GET /auth-api/v0/users/current`、`GET /api/v0/users/get_user_summary` | 公开 API 只有 `GET /user/balance` | **DSH 私有**：内部 Platform 端点，非公开 API |
| 模型发现 | API-key 路由**不拉** `GET /models`，用硬编码 `DEFAULT_MODELS`；pi-ai 用内置目录 | 有公开 `GET /models`（含 context_window/max_output_tokens/effort/api_capabilities） | **DSH 未用**公开发现端点；myco 可用它做自更新 |
| ASR | 只有 provider 抽象：本地 SenseVoice 与"远程 Web Remote"（`packages/experimental/{speech-to-text,speech-to-text-sensevoice,api-speech-to-text}`），**无 DeepSeek ASR 客户端** | 有 `/v1/tools/asr` | DSH 未接入 |
| OCR | **无任何 OCR package**（全仓 grep `ocr` 无命中） | 有 `/v1/tools/ocr` | DSH 未接入 |
| TTS | **无任何 TTS package** | 有 `/v1/tools/tts`（含 WS 流式） | DSH 未接入 |
| Fetch | `web-fetch-http` 自己抓页面 | 有 `/v1/tools/fetch` | DSH 未接入 |
| 协议可配置性 | `llm-deepseek` 只接受 Messages，"不提供 `protocol` 字段"；Chat Completions/Responses 由 `llm-pi-ai` 手工声明 | 三协议都官方支持 | 架构选择，非能力缺失 |

**三条结论（影响 myco 选型）：**
1. DSH 的"DeepSeek 原生 web 搜索"是 **Anthropic Messages + 原生 `web_search` 服务端工具**，一次搜索=一个完整模型轮次；而官方 2026-11-13 后已有 `/v1/tools/search`，**myco 应优先用独立 Search API**（省一整轮 token 与延迟），DSH 的说法不要照抄。
2. **OCR/TTS 在 DSH 中完全不存在、ASR 也不是官方 ASR** —— 这说明的是"DSH 未接入"，不是"官方没有"。myco 要用这三项，只能照 §4 的官方接口自己写。
3. **不要把 DSH 私有扩展当上游能力**：`dsh_*` 正文扩展、`x-deepseek-harness-*` 标头、`x-dsh-auth-token`、`mid-conversation-tool-changes-2026-07-01` 都是 DSH 自己的；myco 若复用会把 provider 绑死到 DSH 网关。官方账号登录机制**不存在**公开等价物（只有 API key + `GET /user/balance`）。

---

### 抓取清单（全部 2026-11-25 访问）

- 文档：`/`、`/quick_start/pricing/`(EN/ZH)、`/updates/`、`/news/news261113/`、`/guides/{thinking_mode,search,web_tools,ocr,asr,tts,vision,document_parsing,tool_calls,json_mode,kv_cache,responses_api,anthropic_api,safety,build_a_chatbot}/`、`/api/{create-chat-completion,list-models,get-user-balance}/`、`/quick_start/{rate_limit,error_codes}/`
- 二手：`m.ithome.com/html/1026960.htm`（2026-11-13 全家桶与人民币价）、`cloud.tencent.com.cn/developer/article/2715785`（2026-07-24 旧名停用）
- 本地实现证据：`E:\deepseek-harness\packages\{llm/llm-deepseek,llm/llm-deepseek-api-key,web/web-search-deepseek,credentials/deepseek-account-platform,experimental/*speech*}`、`docs/deepseek-llm-api-wire-extensions.zh.md`

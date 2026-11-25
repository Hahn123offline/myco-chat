# M3 DeepSeek 附属能力报告（2026-11-26 03:10）

## 1. 结论

`search / fetch / ocr / asr / tts` 五类官方能力已接成 **capability client + 工具**（search/fetch/ocr/asr 四个工具；
TTS 是"返回音频字节"的能力，不进聊天工具面，先用 CLI 验证）。全部用**真实 DeepSeek API** 跑过（TTS/ASR 用同一条
TTS 生成的 wav 回灌 ASR）。成本按刊例价给出估算，不假装是账单。

## 2. 实现

- `core/capability/DeepSeekToolsClient.kt`
  - 端点：`/v1/tools/search|fetch|ocr|asr|tts`；基址从 provider.baseUrl 去掉 `/anthropic`、`/v1` 得到站点根。
  - 鉴权 `Authorization: Bearer <key>`；429/5xx 退避重试 2 次；错误码 `HTTP_xxx`/`NETWORK`，`retriable` 只标网络类。
  - OCR/ASR 用 multipart（字段名与官方一致：`file`、`purpose`）；TTS 返回二进制 wav。
- `core/capability/CapabilityTools.kt`
  - `web_search`（query/count）：结果含 title/url/snippet/summary/site/date，输出附 `estimatedCostCny`。
  - `read_url`（url）：返回 title + 正文（>12k 截断）。
  - `ocr_image`（path/purpose）：返回逐页文本 + 页数成本。
  - `transcribe_audio`（path）：返回 text/duration/成本。
  - **只对 api.deepseek.com 注册**（自定义 OpenAI 兼容服务没有这些端点，注册会误导模型）。
  - `ToolDef.costNote` 新字段：成本说明进 UI/日志，不进模型 schema（不占 token）。
- 计费（刊例，仅展示用）：search/fetch ¥0.005/次成功；OCR ¥0.0015/页；ASR ¥0.000108/秒（0.39 元/时）；
  TTS ¥0.00015/字符（1.5 元/万字符，按汉字 2 字符计）。
- 桌面 CLI 新增：`tools`、`search`、`fetch`、`ocr`、`asr`、`tts`；Android 的 BridgeHolder 自动给第一个 DeepSeek provider 注册工具。
- 修正 `init-deepseek` 的 flash 定价为官方口径：输入 1 元/M、缓存命中 0.2 元/M、输出 2 元/M（此前输出 4 元是旧数据）。

## 3. 真实链路证据

```
search "DeepSeek V4 API 更新"
  cost ≈ ¥0.005（成功才计费）
  1. DeepSeek-V4 预览版… https://api-docs.deepseek.com/zh-cn/news/news260424/#1
fetch https://api-docs.deepseek.com/quick_start/pricing/  → 标题 "Models & Pricing | DeepSeek API Docs" + 正文
ocr 一张自生成大字号 PNG → "你好，myco OCR 测试 2026"（pages=1，cost ≈ ¥0.0015）
tts "你好，这是 myco 的语音测试。" → tts-test.wav 147884 字节（RIFF），cost ≈ ¥0.00255
asr tts-test.wav → "你好，这是Michael的语音测试。" duration=3080ms，cost ≈ ¥0.000334（TTS→ASR 闭环）
```

**模型自主调用（真实对话）**：问"用 web_search 搜 DeepSeek V4 百万上下文并引用来源"，
模型连续调用 `web_search`(count=20) → `read_url` → 最终给出带 5 个来源链接的长回答；
`turn/end.steps=3`，`costUsd=0.0295 CNY`（含搜索摘要进上下文带来的输入）。

## 4. 测试

- `DeepSeekToolsClientTest`（5）：search 解析/auth/body、fetch、429 可重试、origin 规整、成本常量。
- `CapabilityToolsTest`（2）：注册 4 个工具与 costNote、web_search 执行输出；非 deepseek 主机不注册。
- 全量：`:core:test` **50** + `:bridge-http:test` **6** = **56 绿**。

## 5. 已知缺口

1. 工具结果可能很大（count=20 + summary），会显著抬高输入成本；下一步给工具加"返回条数/摘要长度"上限或按需 need_content。
2. OCR 对 UI 截图（小字/抗锯齿）返回空文本，对大字号清晰图正常——需要一次专门的图片输入规范验证（M5 视觉时一起）。
3. TTS 目前只落 wav，未接播放/会话语音消息；ASR 需 ≤120 秒切片逻辑未做。
4. Android 上未做真机工具调用（本机 key 未进手机）；工具注册逻辑已编译通过。
5. `HttpBridgeContractTest` 在高负载并行构建时出现过一次 SSE 10s 超时（重跑通过）；已把超时提到 20s，后续观察。

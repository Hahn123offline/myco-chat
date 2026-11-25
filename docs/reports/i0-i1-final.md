# I0 / I1 目标收口核对（2026-11-26 02:10）

> 对照 goal objective 的每一条，给证据与入口。全部测试/构建在本文最后重跑一次。

## 0. 结论

**I0 收尾与 I1 首轮全部完成**，真实链路（DeepSeek 两种方言）与真机（Android 15）都已验证；
目标可以标记完成。剩余项见 §5（都不属于本目标范围）。

## 1. I0 收尾逐项

| 目标项 | 状态 | 证据 |
|---|---|---|
| http-sse Bridge | ✅ | `bridge-http/`：`/health`、`/command/*`、`/query/*`、SSE `/events`、Bearer token 鉴权；桌面 `serve` 命令 |
| 两种传输同一套契约测试 | ✅ | `HttpBridgeContractTest` 6 条：建会话/查询、**HTTP 与 in-process 产生相同 5 事件**、SSE delta+done、坏 token、provider 保存+凭据不回显、provider 导出/替换 |
| settings revision 乐观锁 | ✅ | `SettingsStore`（`{schemaVersion,revision,data}`、旧格式兼容、tmp+原子 move、冲突拒绝）；`ProviderStoreTest` 4 条 |
| provider 发现 | ✅ | `ModelDiscovery`（OpenAI `/models`、Anthropic `/v1/models`、`/anthropic` 基址回退根路径）；Bridge `ProviderDiscover`；Android「测试连通性」按钮；真实 DeepSeek 发现成功 |
| provider 导入/导出 | ✅ | CLI `provider-export/import`；Bridge `ProviderExport/ProviderReplace`；Android SAF「导出/导入」按钮；core+http 测试 |
| doctor 扩展 | ✅ | 9 项：store/sessions/providers/credentials/settings/models/tools/bridge（含"启用的 provider 能否解析 chat 角色"） |
| 桌面壳接真实 provider | ✅ | `init-deepseek` + 真实流式（Anthropic Messages）+ `discover`；OpenAI 兼容路径也真实跑通 |
| Android 壳接真实 provider | ✅ | `BridgeHolder` 按 kind 选 `ChatClients.createOrFailed`；真机本地 SSE（adb reverse）流式成功；provider UI 管理可用 |

## 2. I1 首轮逐项

| 目标项 | 状态 | 证据 |
|---|---|---|
| DeepSeek Messages 方言 | ✅ | `AnthropicCompatibleClient`：`x-api-key`/`anthropic-version`、`max_tokens`、SSE 事件解析、**默认显式关思考**；真实 API 两轮对话 |
| OpenAI 兼容实测 | ✅ | `OpenAiCompatibleClient` 对真实 `https://api.deepseek.com`：`/models` + `/chat/completions` 流式成功 |
| 错误/重试 | ✅ | 仅 429/5xx/连接异常重试；指数退避封顶 8s；`Failed{retriable}`；`LlmRetryTest` 4 条（重试成功/耗尽/400 不重试/Anthropic） |
| 取消/停止 | ✅ | `ChatStop` 只取消收集 job、部分文本按 stopped 落盘、`turn/end.steps=0`；取消 <3s 且无 Done（测试）；Android「停止」按钮 |
| 用量 | ✅ | 两方言解析 input/output/cached tokens；写入 `assistant/message` 与 `turn/end` |
| 成本（usage→cost） | ✅ | `ProviderProfile.pricing` + `UsageCost`；真实调用得 `6.5e-5` CNY / `8.2e-5` CNY；单测覆盖；峰谷窗口未做（见 §5） |

## 3. 测试与构建（本轮重跑）

```
:core:test         39 passed
:bridge-http:test   6 passed        → 45/45
:app-desktop:installDist     ok
:app-android:assembleDebug   ok
:app-android:assembleRelease ok（R8，1.13MB，需临时签名才能安装测）
```

## 4. 真机结果（Android 15 / API 35，24129PN74C）

- 冷启动 debug 413–651ms → **release 120ms**；APK 9.5MB → **1.13MB**；PSS 147MB → **79.6MB**；
- provider UI、SSE 流式、历史跨进程持久化、FGS `specialUse` 通知全部通过；
- 真机暴露并修掉 4 个 Android-only 问题（见 [android-s0b-device.md](android-s0b-device.md)）。
- 未测：release 长时存活/耗电（需要过夜窗口，用户后续安排）。

## 5. 明确不属于本目标 / 留待后续

1. DeepSeek 峰谷价格窗口（pricing 字段已留，峰值乘法未做）。
2. Android SAF 文件选择器的完整落盘 e2e（按钮与命令已就绪；系统 UI 流程未脚本化）。
3. 24h FGS 存活/耗电（需要手机过夜）。
4. 中文 IME 输入行为。
5. I2 起的记忆/人格/主动性（下一阶段，需先与用户确认）。

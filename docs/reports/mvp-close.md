# MVP 闭合验收（M1–M6）· 2026-11-26 04:00

> 对照 docs/design/60-mvp-closed-loop-plan.md 的 M1–M6，逐项给证据与入口。

## 0. 结论

**M1–M6 全部完成**，MVP 达到"日常可用闭环"的目标形态：chat 单焦点默认、work 多会话可切、
真实模型（两方言 + vision + 工具 + DeepSeek 能力）、会话管理与搜索、附件（相册/文件/拍照入口）、
明暗主题与稳定组件库。测试 **70 绿**，Android 真机逐项验证。

## 1. 逐项证据

| 里程碑 | 状态 | 证据 |
|---|---|---|
| M1 视觉地基 | ✅ | `ui/Theme.kt`/`Components.kt`/`ThemePrefs.kt`；[m1-report](m1-report.md)；真机浅深截图 |
| M2 工具循环 | ✅ | 两方言 tool calls；Bridge 执行/回灌/事件/预算；[m2-report](m2-report.md)；真实 DeepSeek `time_now` 调用 |
| M3 DeepSeek 能力 | ✅ | search/fetch/ocr/asr/tts 客户端 + 4 个工具（带成本）；[m3-report](m3-report.md)；真实调用（含 TTS→ASR 闭环、模型自主 web_search 引用来源） |
| M4 会话管理/形态分叉 | ✅ | rename/软删/恢复/搜索（sidecar，不改 formatVersion）；chat/work 开关；[m4-report](m4-report.md)；真机长按/改名/搜索/切换 |
| M5 附件与隐私 | ✅ | AttachmentStore/两方言 vision/文本内联；Android 相册/文件/拍照入口；[m5-report](m5-report.md)；真实 vision 描述图片、真机相册附件跨重启 |
| M6 视觉细化 | ✅ | 气泡/输入焦点/历史行/设置卡片/空态；[m6-report](m6-report.md)；浅深截图 |

## 2. 测试与构建

```
:core:test         64 passed
:bridge-http:test   6 passed        → 70/70
:app-desktop:installDist     ok
:app-android:assembleDebug   ok（release R8 此前测得 1.13MB / 冷启 120ms / PSS 79.6MB）
```

## 3. 真实链路证据（DeepSeek 官方；key 仅环境变量注入，未入库/未打印）

- Anthropic Messages + OpenAI 兼容两方言流式对话、usage/cost；
- 工具调用：`time_now`（两方言）；`web_search` + `read_url` 模型自主连续调用并带引用回答；
- 能力：search / fetch / OCR（清晰图）/ TTS / ASR（TTS 产物回灌识别）；
- 视觉：800×400 测试图被正确描述（文字 + 图形）。
- 真机：provider UI、SSE 流式（adb reverse + fake-sse）、历史持久化、FGS 通知、会话管理、附件相册链路。

## 4. 剩余已知缺口（不阻塞 MVP，列入后续）

1. **网络下 push 仍失败**：本轮 M1–M6 提交都在本地（最新 `178334a`），需要网络恢复后 `git push`。
2. DeepSeek 峰谷价格窗口（pricing 字段已在，峰值乘法未做）。
3. 文件选择器与相机的完整真机 e2e（相册已通）；大图未压缩；图片无缩略图。
4. 聊天体验差距：Markdown 渲染、重新生成、语音输入、消息操作条、模型快捷切换、token 展示。
5. 24h 前台存活/耗电（需要手机过夜窗口）。
6. 跨回合工具上下文回放（本回合内正确，历史回放只带文本）。

## 5. 下一步建议（需用户拍板）

- 继续打磨 chat 体验（Markdown/重新生成/语音）→ 更接近 DeepSeek chat 原版；
- 或开始 I2 最小记忆（原文层/块/召回/诚实状态）；
- 或先做 work 模式的任务/工具面板（提前验证 work 形态）。

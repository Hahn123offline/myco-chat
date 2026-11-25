# 参考资料消化报告（第一轮 · 2026-11-25）

六路并行考古的产物。每份都区分了「代码事实 / 有测试 / 真跑过 / 只是文档说」，
并对文档自相矛盾处点名。**读设计简报前先看 §0 一页结论即可**，需要复核再往下翻。

| 报告 | 对象 | 一句话价值 |
|---|---|---|
| [icl-architecture.md](icl-architecture.md) | `E:\11-15\tools\external\dsh-icl` 的插件宿主与产品化 | 「一切皆插件」的真实形态 = 目录 + manifest + `apply(ctx)` + 注销器；8 个注册面；单会话是架构决定 |
| [icl-memory.md](icl-memory.md) | dsh-icl 记忆/召回/整理/评测 | 三表 + 命名空间 + 原文层来源分类；质量杠杆在写入侧；评测 fixture 是最值钱资产 |
| [icl-persona-proactive.md](icl-persona-proactive.md) | dsh-icl 人格/活人感/主动性/感知 | 人格 = 静态卡（无状态机）；68% 心跳空转的真实纠错；一次真唤醒 = hit=0 / prefill 7k–24k tok |
| [dsh-harness.md](dsh-harness.md) | `E:\deepseek-harness` 源码 | provider/profile 配置面、cordis 插件模型、会话日志、agent loop 的扩展点 |
| [deepseek-api.md](deepseek-api.md) | DeepSeek 开放平台（2026-11-25 抓取） | 模型已换代为 `deepseek-flash`/`deepseek-v4-pro`；思考默认开；Search/Fetch/OCR/ASR/TTS 全有官方 API |
| [trace-memory.md](trace-memory.md) | `reference/trace-memory` | `freshness`/`coverage` 语义契约、`block/visit/view`、三值 NULL、唯一出网点 fail-closed |
| [mobile-agent-landscape.md](mobile-agent-landscape.md) | Android 平台与手机 agent 生态（第二轮补） | 常驻只能 FGS(specialUse)+通知；截图 ~333ms 限速；Play 禁止运行时下载可执行文件（proot 受影响）；移动端 10 条约束 |
| [runtime-options.md](runtime-options.md) | 运行时选型（建议 D9 拍板用） | 7 候选五维打分：Kotlin/JVM 单核心 89 分最高；nodejs-mobile/CodePush/RN SSE 的否决证据；1 周 spike 判据 |
| [deepseek-chat-demo-ui.md](deepseek-chat-demo-ui.md) | 官方 `deepseek-ai/chat-demo`（MIT） | chat 壳的结构参考：回复五态、贴底滚动、首 token 三段反馈、移动键盘、会话分组；但它没有 provider 管理 |
| [mobile-chat-ux.md](mobile-chat-ux.md) | 移动端 chat UX 基线 + 开源项目/DSH Go 可抄点（M1–M6 用） | 单条会话=长按菜单、流式四态、40px 贴底规则、过程折叠、imePadding、M4 双形态推荐 |

> 证据分级不统一是这批材料的通病（dsh-icl 自己就有两套互斥的 E0–E4 定义）。
> 本目录各报告已各自声明口径；引用数字前先看它的口径表。

# 运行时选型对比（供 D9 拍板）

> 范围：**手机端内核载体 + 整体架构**——chat/work 两个 app 能否在单台手机上离线独立跑完整内核。
> **访问日期：本文所有 web 链接均于 2026-11-25 抓取**；本地源码 = 工作区 `reference/trace-memory` 同日状态。
> 证据分级：`[官方]` 平台/项目官方文档 · `[源码]` AOSP/本地代码 · `[社区]` issue/博客/商店/第三方 · `[推断]` 基于前两者的推断 · `[需实测]` 无公开数据。
> 合规声明：本文未调用任何模型服务/API，未 install / 未构建 / 未下载安装包。
> 口径：**「离线独立」= 不依赖任何远程电脑**；调用 LLM 云 API 不算违反（模型本来就不在手机上跑）。

---

## 0. 一页结论（≤15 行）

1. **推荐主选 A：Kotlin/JVM 单核心**——`core` 是纯 JVM Kotlin 库（Android 与桌面都是 JVM），Android 用 Compose 原生壳，桌面用 JVM + Ktor HTTP/SSE（+可选 Compose Desktop）做开发/预览；插件 = 编译期 Gradle 模块 + 运行时注册表。结构上按 **B（KMP）** 预留：只改构建形态、不改代码，将来要 iOS/Web 再升格。
2. **三条决定性理由**：
   - ① 离线单机所需的能力（FGS 常驻、通知、无障碍、SQLite、Keystore、文件）**全是 Android 原生 API**；非 Kotlin 栈最终仍要写同一层 Kotlin 胶水，还要额外背一个运行时（包体/内存/耗电/16KB/政策）。
   - ② **前代 trace-memory 已经用 Java 写完了这套内核**：353 个非测试 Java 文件、110 个测试、模块宿主 + `AgentEngine` + 事件日志 + SQLite 记忆 + provider codec；其中 **78/353（22%）** import `android.*`，**Kotlin/JVM 是「移植」不是「重写」**。I0 尚未开工（仓库只有 docs + reference），现在换栈的代码成本 ≈ 0。
   - ③ Play 禁止运行时下载可执行代码 → **「一切皆插件」在手机上只能落地为「编译期模块 + 运行时注册表 + 可下发数据插件」**。Kotlin 的 Gradle 模块与 trace-memory 的 `Module`/`HostContext.effect()` 契约一一对应；JS 栈的动态插件能力在政策下兑现不了，却要先付运行时代价。
3. **否决 D/E 作主选**：nodejs-mobile 官方 npm 停在 Node 18.20.4（2024-10），Node 22 只有 fork（libnode.zip ~88MB/2 ABI）；`node:sqlite` 要 Node ≥22.5 且仍是实验特性；native 模块要交叉编译；动态插件仍受 Play 政策钳制。
4. **真正备选是 C（RN + Hermes）**：唯一能「保住同一份 TS 内核代码」的路线，触发条件只有一个——你判定 TS 复用价值 > 移植前代 Kotlin 内核 + 原生体验。F 分数虽第 2（74.5），但不构成备选：它既不复用 TS，也不复用你的 Kotlin 经验，原生层照样写 Kotlin；G（Rust）开发速度 1.5 分，不可接受。
5. **代价要认**：I0 的 TS daemon 从「内核」降级为「legacy 的 web 预览壳」；Kotlin 生态在 JSON Schema/MCP 服务端侧弱于 TS——但手机端本来就跑不了 Node/Python 的 MCP server，这一条在移动端不是损失。
6. **拍板后的第一个动作**：改 `30-i0-plan.md` 落点（Gradle 多模块），然后做 §6 的 spike-1（Android 单机闭环），不要先做 UI。

---

## 1. 评估维度与权重

| 维度 | 权重 | 为什么这么重 | 怎么判 |
|---|---|---|---|
| 离线独立 | 30 | 硬要求；决定产品是否存在（D9 的全部意义） | 单机能否跑通 chat+work 闭环、无远程 daemon |
| 移动体验 | 20 | 目标是「每天在手机上用」 | 常驻/通知/键盘/单列流、冷启动、流畅度、观感 |
| 插件扩展 | 15 | 「一切皆插件」是身份，但**被 Play 政策封顶** | 加功能要不要发版？数据下发是否合规？ |
| 开发速度 | 20 | 单人维护，「每天能自己用」是验收线 | 改一行→看见效果的时间；测试栈；CI 成本 |
| 长期可维护 | 15 | 两代学费都在这里 | 工具链活跃度、升级成本、是否要双实现 |

> 插件权重压在 15 而不是更高：**移动端所有栈共享同一政策上限**，栈间差异不在「能否动态插件」，而在「编译期模块好不好写、数据插件是否顺手」。这一条直接决定了「用 JS 换动态插件」的收益在手机上基本不存在。

---

## 2. 候选栈逐项

### A. Kotlin/JVM + Compose（**推荐主选**）

- **最小可行架构**：`core/`（纯 JVM Kotlin 库：contracts + kernel 注册表/注销器/预算 + session 事件日志 + llm 三方言 codec + tools/prompt/delivery/memory + api 面；**不依赖任何 UI**，满足「无 GUI 引擎照跑」）＋ `android/core-android/`（SQLite 驱动、FGS、通知、无障碍、文件、Keystore、WorkManager）＋ `android/app-chat/`、`android/app-work/`（Compose UI）＋ `desktop/daemon/`（JVM main + Ktor HTTP+SSE，可无头跑 doctor/CLI；可选 Compose Desktop 壳）＋ `web/`（TS UI，开发/预览，保留 I0 计划里的浏览器壳）＋ `plugins/`（Gradle 模块：`manifest.json` + `apply(ctx)`）。
- **依赖**：Kotlin 2.x + coroutines + kotlinx.serialization；HTTP/SSE 用 OkHttp（`okhttp-sse`）或 Ktor Client（SSE 插件）；SQLite 用 SQLDelight 2.4（android/jdbc 双驱动）或 Room 3.0（KMP，含 JVM 桌面）；AndroidX（Compose、FGS、通知）；MCP 用官方 [kotlin-sdk](https://github.com/modelcontextprotocol/kotlin-sdk)（KMP，含 Stdio/SSE/WS/StreamableHttp 传输；2026-08 仍被文档站点索引，活跃度需在 spike 中确认）。
- **包体**：空 Compose arm64 全优化后约 **0.6MB** `[社区]`（[出处](https://www.volcengine.com/article/155527)）；本内核（Compose+SQLite+OkHttp+序列化）估计 **8–15MB** `[推断/需实测]`。桌面 jpackage 发行约 **60MB**（jlink 裁剪后）`[社区]`（[对照](https://nucleusframework.dev/en/docs/2.4/compare/)），但桌面只是开发载体。
- **离线**：✔ 完整。无 Node/无远程 PC；LLM 只走 HTTPS 出网点。
- **插件**：编译期模块 + 运行时注册表（最稳、0 政策风险、可单测）。加功能要发版；可用 **Play Feature Delivery** 把可选模块拆成 Play 下发的功能模块（仍是发版审核，但不必全量下载）`[官方]`（[Play Feature Delivery](https://developer.android.com/guide/playcore/feature-delivery)）。与 trace-memory 现成契约同构：[`Module.java`](../../reference/trace-memory/app/collector/host-api/src/main/java/com/dsh/host/api/Module.java)（单入口 `install()`）、`HostContext.effect()` 逆序注销、`health()`。
- **桌面预览**：`./gradlew :desktop:daemon:run` 起 JVM daemon + 浏览器 web UI；Compose Desktop 有 **Hot Reload 1.0**（2026-01 发布）`[官方]`（[JetBrains 博客](https://blog.jetbrains.com/kotlin/2026/01/the-journey-to-compose-hot-reload-1-0-0/)）；JVM 单测与 Android 共享同一份 core 代码、不需要模拟器。
- **风险**：放弃 TS I0（现在成本≈0，晚了就是双实现）；Kotlin 侧 JSON Schema 校验器与 MCP 服务端生态弱于 TS；Android JVM ≠ 完整 JVM（`java.net.http`/`javax.*`/部分 `java.nio.file` 不可用，需注意 core 的 API 子集）；单人工程量（但前代可移植）。

### B. Kotlin Multiplatform（KMP）

- **最小可行架构**：与 A 相同，但 `core` 放 `commonMain`，平台差异走 `expect/actual`；Android 与桌面 UI 用 Compose Multiplatform 共享 composable；后续可加 iOS/JS/Wasm target。
- **现状**：KMP 的 Android / iOS / Desktop(JVM) 均为 **Stable** `[官方]`（[平台稳定级](https://github.com/jetbrains/kotlin-multiplatform-dev-docs/blob/f55cabfc9116757d5a443ccd2522476cbef4364b/topics/overview/supported-platforms.md)）；Compose Multiplatform **1.10.0**（2026-01-13）`[官方/社区]`（[release](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.10.0)、[报道](https://soft.zhiding.cn/software_zone/2026/0119/3177171.shtml)）；Room 3.0 聚焦 KMP，覆盖 Android/iOS/JVM 桌面并新增 JS/WASM `[官方]`（[Room 3.0 博客](https://developer.android.com/blog/posts/modernizing-the-room)）；SQLDelight 2.4 提供 `android-driver`/`jdbc-driver` `[官方]`（[升级文档](https://sqldelight.github.io/sqldelight/latest/upgrading-2.0/)）；桌面端能力齐全：窗口、`Tray()` 托盘与系统通知 `[官方]`（[tray 文档](https://github.com/jetbrains/kotlin-multiplatform-dev-docs/blob/f55cabfc9116757d5a443ccd2522476cbef4364b/topics/compose/compose-desktop-tray.md)），HTTP/SSE 服务用 Ktor。
- **与 A 的真实差别**：不是运行时差别，而是**构建形态**差别——B 多了 Gradle `multiplatform` 配置与 `expect/actual` 仪式，换来「UI 也能共享 + 未来 target」。对「桌面只是预览」的本项目，UI 共享收益很小。
- **与 Android 共享 core 的真实成本**（用前代量化）：353 个非测试 Java 文件里 78 个 import `android.*`（22%），其中 Android SQLite（`SQLiteOpenHelper`/`ContentValues`）与 `android.util.Log` 是主要耦合点；core 侧要抽 `Database`/`Log`/`Clock`/`FileSystem` 四个接口。前代已证明可测：[`MemoryStore.java`](../../reference/trace-memory/app/collector/module-memory/src/main/java/com/dsh/memory/MemoryStore.java) 把 schema 与 SQL 放纯逻辑类、`SQLiteOpenHelper` 只做执行。
- **结论**：**B 是 A 的正式化版本，不是竞争方案**。若 12 个月内有 iOS/Web 客户端诉求，直接选 B；否则 A 更省。

### C. React Native + Hermes 承载 TS core

- **最小可行架构**：RN app 内跑 TS core；Node API 用适配层（`op-sqlite`/RNFS/NetInfo 等）补齐；Android 原生模块承载 FGS、通知、无障碍（**这些无论如何都要写 Kotlin**）；SSE 无 `response.body`，需 XHR streaming polyfill 或自写原生模块。
- **关键事实**：RN 内置 `fetch` 会等整个响应、没有 `response.body`，SSE 必须用 polyfill/原生模块 `[社区]`（[fetch-sse 说明](https://socket.dev/npm/package/@the-polyfills/fetch-sse)）；初始包 22–30MB（4 ABI），Hermes 省 2–4MB `[社区]`（[体积分析](https://www.volcengine.com/article/3334639)）；arm64 release 估 ~28.5MB `[社区]`（[rn-size-check](https://www.npmjs.com/package/rn-size-check)）；新架构 bridgeless 仍有 60MB 需优化到 20MB 的案例 `[社区]`（[案例](https://www.volcengine.com/article/1240646)）。
- **SQLite**：`op-sqlite` 自报 Galaxy S22 上 1125ms vs `expo-sqlite` 10626ms（同 benchmark）`[社区/项目自述]`（[npm](https://www.npmjs.com/package/@op-engineering/op-sqlite)）——说明 RN 能做，但要押具体第三方库。
- **离线**：✔ 可行，但适配层是**长期税**（Node ⇄ Hermes 双运行时）；a11y/FGS 仍要 Kotlin，等于「Kotlin 原生 + TS 逻辑 + 适配层」三份。
- **插件**：JS bundle 随包 = 政策安全；CodePush 类热更政策灰（见 §4）。
- **桌面预览**：RN 无桌面；桌面只能继续用 Node daemon + web（即 I0 现状）→ 逻辑要在 Node 与 Hermes 两个运行时都跑通。
- **风险**：RN 升级 churn、内存/耗电、原生观感、Hermes 无 Node API；「一套代码」在最关键的移动能力上不成立。

### D. 原生壳 + 内嵌 Node（nodejs-mobile）

- **关键事实**：官方 npm `nodejs-mobile-react-native@18.20.4` 仍是 latest，**2024-10 发布**，维护者已是社区 `[官方 registry]`（[npm JSON](https://registry.npmjs.org/nodejs-mobile-react-native/latest)，`_npmUser=staltz`、unpackedSize 375MB）；官方 CHANGELOG 最后一条 Node 18.20.4（2024-10-07）`[官方]`（[CHANGELOG](https://github.com/nodejs-mobile/nodejs-mobile/blob/main/doc_mobile/CHANGELOG.md)）；Node 22 的移植只见镜像/分叉（v22.23.2，`android-libnode.zip` **~88MB**，含 arm64-v8a + x86_64）`[社区/分叉，待核对上游]`（[镜像](https://gitcode.com/OpenFlun/nodejs-mobile)）。
- **`node:sqlite` 是死穴**：v22.5.0 才加入、Stability 1.1（实验）、API 全同步 `[官方]`（[Node 22 文档](https://nodejs.org/docs/latest-v22.x/api/sqlite.html)）。官方 Node 18 没有它 → 要么上 fork（88MB），要么交叉编译 `better-sqlite3` 之类原生模块（nodejs-mobile-gyp）。
- **包体/16KB**：`libnode.so` 必须按 16KB page 重编对齐（targetSdk 35+，2027-02-01 起不支持 16KB 的更新不能上架）`[官方]`（[page sizes](https://developer.android.com/guide/practices/page-sizes)）。
- **插件**：JS 插件随包可加载；下载 JS 插件=政策灰区（见 §4）；Node 定时器/长连是移动耗电的经典偷电点 `[推断/需实测]`。
- **结论**：只在你**必须逐字节复用 daemon 代码**时考虑；作主选不划算。

### E. 原生壳 + 轻 JS 引擎（QuickJS/Hermes + 自写绑定）

- **关键事实**：`quickjs-android` armeabi-v7a 整体 APK 增量约 **350KB**、支持 ES2020 `[社区]`（[taoweiji/quickjs-android](https://github.com/taoweiji/quickjs-android)）；但 fs/net/sqlite/keystore/通知全部要**自写 JNI 绑定**，TS→bytecode 要自建 `qjsc` 工具链。
- **工作量大头**：绑定层 ≥ 1–2 周、流式 HTTP + TLS 要自己接（QuickJS 无网络栈）；等于自研一个小 Node。
- **插件**：随包 bytecode 可加载；下载插件仍是政策灰区。
- **结论**：不推荐。除非目标是「插件代码可下载且**只走 webview 式间接 API**」并且愿意先付 3–4 周基建。

### F. Flutter + Dart core

- **现状**：Flutter 稳定通道季更（3.41→3.44→3.47，2026 年 2/5/8 月）`[社区]`（[升级清单](https://github.com/chen08209/FlClash/blob/8d4f9744/.agents/flutter-upgrade.md)）；SQLite 用 drift（可跨 isolate）`[官方/社区]`（[drift isolates](https://drift.simonbinder.eu/)）；包体单 ABI 约 20–26MB、多 ABI 24–88MB 都出现过 `[社区]`（[体积](https://www.volcengine.com/article/2268054)、[突增案例](https://www.volcengine.com/article/3483256)）。
- **硬伤**：FGS/通知/无障碍仍要 Kotlin platform channel（**能力不会因为换 UI 框架而来**）；MCP/JSON Schema 的 Dart 生态弱；对你（Kotlin 熟练者）是负收益。
- **结论**：次优。若你更想长期用 Dart 写 UI 再考虑。

### G. Rust core + Kotlin/JNI 壳

- **现实**：UniFFI 能生成 Kotlin 绑定，但公开资料里**查不到可信的 Android 体积/启动数据**，也「没有已知的 ubrnwasm2 生产使用」`[社区]`（[UniFFI 调研笔记](https://github.com/laudebugs/laminapdf/blob/40bef75661be71bdea7a502073dd70749f9715d3/docs/research/libraries/notes/rust-core-bridge.md)）。
- **代价**：agent loop、三方言 codec、JSON Schema、MCP、HTTP/TLS、SQLite、事件日志全部重写；你不是 Rust 开发者。
- **结论**：不作主选；未来可作为「协议/存储/向量」的共享加速库，但那是优化，不是底盘。

### 2.8 横切约束（与栈无关，但必须在 D9 一并定）

1. **双 app 共享存储在 Android 上不是免费的**：每个 app 独立 UID/独立数据目录，scoped storage 下互不可见 `[官方]`（[app sandbox](https://source.android.com/docs/security/app-sandbox)、[app-specific files](https://developer.android.com/training/data-storage/app-specific)）；`sharedUserId` 自 API 29 废弃，官方建议改用 Service/ContentProvider `[官方]`（[manifest 文档](https://developer.android.com/guide/topics/manifest/manifest-element)）。两条可行路：**(a) 一个 APK、两个 launcher activity（两个图标、一份数据目录）** `[社区]`（[SO 讨论](https://stackoverflow.com/questions/78766885/android-two-launcher-icon-for-two-different-acitvities/78767151)）；**(b) core app + 签名级 ContentProvider/绑定服务** `[官方建议]`。这条在 A/B/C/F 上一样，必须在 I0 的契约里先定（当前 `30-i0-plan.md` 没有覆盖）。
2. **16KB page**：所有 `.so`（SQLite、proot、nodejs、Rust）都要对齐；纯 Kotlin/Java 天然兼容 `[官方]`（[page sizes](https://developer.android.com/guide/practices/page-sizes)）。
3. **常驻/无障碍/截图**：FGS `specialUse`+通知、`takeScreenshot` ~333ms 限速、MediaProjection 短会话——见 [mobile-agent-landscape.md](mobile-agent-landscape.md) §1/§2，本报告不重复；但请注意这些**都是 Android 原生层，选 UI 框架不会替你实现**。

---

## 3. 关键能力证据表

> 符号：✔ 开箱可用 / 低风险；△ 需适配层或第三方；✘ 不可行；? 无公开数据，需实测。A/B 共用结论；D/E 同属 JS 运行时族，只在异构时分开。

| 能力 | 关键事实与来源（访问 2026-11-25） | A/B | C | D | E | F | G |
|---|---|---|---|---|---|---|---|
| SSE 流式解析 | OkHttp `okhttp-sse` 仍在发布（5.5.0）；Ktor Client 有官方 SSE 插件 `[官方]`（[ktor](https://ktor.io/docs/client-server-sent-events.html)、[okhttp-sse](https://central.sonatype.com/artifact/com.squareup.okhttp3/okhttp-sse)）；RN 内置 fetch 无 `response.body`、必须 polyfill/原生模块 `[社区]`（[fetch-sse](https://socket.dev/npm/package/@the-polyfills/fetch-sse)） | ✔ | △ | ✔ | ✔ | ✔ | ✔ |
| SQLite | Room 3.0 覆盖 Android/iOS/JVM 桌面并加 JS/WASM；SQLDelight 2.4 双驱动；drift 可跨 isolate；op-sqlite 自报 S22 1125ms vs expo 10626ms `[官方/社区]`（[Room 3.0](https://developer.android.com/blog/posts/modernizing-the-room)、[SQLDelight](https://sqldelight.github.io/sqldelight/latest/upgrading-2.0/)、[drift](https://drift.simonbinder.eu/)、[op-sqlite](https://www.npmjs.com/package/@op-engineering/op-sqlite)） | ✔ | ✔ | △ 需交叉编译 native 模块；Node18 无 `node:sqlite` | △ 全自写 | ✔ | ✔ |
| FGS/后台 | targetSdk 34+ 必须声明类型；`specialUse` 需 Play 声明；`dataSync` 6h/24h 上限（[FGS 类型](https://developer.android.com/about/versions/14/changes/fgs-types-required)、[超时](https://developer.android.com/develop/background-work/services/fgs/timeout)） | ✔ | △ 插件 | △ 插件 | △ 插件 | △ 插件 | △ 胶水 |
| 通知 | channel 创建后不可改；13+ 要 `POST_NOTIFICATIONS` `[官方]`（[channels](https://developer.android.com/develop/ui/views/notifications/channels)） | ✔ | △ | △ | △ | △ | △ |
| 无障碍 | `AccessibilityService` 原生 API；截图 ~333ms 限速 `[官方/源码]`（[API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)、[AOSP](https://android.googlesource.com/platform/frameworks/base/+/android-17.0.0_r1/services/accessibility/java/com/android/server/accessibility/AbstractAccessibilityServiceConnection.java)） | ✔ | △ 必写原生模块 | △ | △ | △ platform channel | △ |
| 文件系统 | app-specific 目录 + scoped storage；跨 app 不可见 `[官方]`（[app-specific](https://developer.android.com/training/data-storage/app-specific)） | ✔ | △ RNFS | △ | △ | △ | △ |
| JSON Schema | Kotlin：`kotlinx-schema` 0.5.0（JSON Schema 模型/DSL）+ OptimumCode 校验器（基于 kotlinx.serialization）`[官方/社区]`（[kotlinx-schema](https://central.sonatype.com/artifact/org.jetbrains.kotlinx/kotlinx-schema-json)、[validator](https://github.com/OptimumCode/json-schema-validator)）；TS 侧 ajv/zod 最成熟 | △ | ✔ | ✔ | ✔ | △ | △ |
| HTTP/证书 | OkHttp 支持自定义 `SSLSocketFactory`/`CertificatePinner`，Android 有 `network_security_config`；RN 底层即 OkHttp；Flutter 用 `dart:io` SecurityContext `[官方]`（[OkHttp](https://square.github.io/okhttp/)、[network security config](https://developer.android.com/privacy-and-security/security-config)） | ✔ | ✔ | ✔ | △ 自写 | △ | ✔ |
| MCP 客户端 | 官方 `modelcontextprotocol/kotlin-sdk`：KMP，`StdioClientTransport`/`SSE`/`WebSocket`/`StreamableHttp` `[官方]`（[kotlin-sdk](https://github.com/modelcontextprotocol/kotlin-sdk)）；TS 官方 SDK 最大（[typescript-sdk](https://github.com/modelcontextprotocol/typescript-sdk)）。**Android 上 stdio 传输基本不可用**（不能 exec 任意二进制）→ 移动端只能 HTTP 远程或进程内 server | ✔ HTTP / △ stdio | △ | ✔ | ✔ | △ | △ |
| 包体基线 | 空 Compose arm64 ~0.6MB；RN 22–30MB(4 ABI)/arm64 估 ~28.5MB；Flutter 单 ABI 20–26MB；QuickJS ~350KB；nodejs-mobile 官方包 unpacked 375MB；fork libnode.zip ~88MB(2 ABI) `[官方/社区]`（前文各链接） | ✔ | △ | ✘ | ✔ | △ | ✔ |
| 冷启动 | 无同口径公开数据（各家基准机型/口径不同）`[需实测]` | ? | ? | ? | ? | ? | ? |
| 内存 | 无同口径公开数据；RN/Hermes 与 JVM 堆需同机实测 `[需实测]` | ? | ? | ? | ? | ? | ? |
| 耗电 | 平台侧有硬配额（Rare bucket 24h 仅 10 分钟算力、Doze 无网）`[官方]`（[power details](https://developer.android.com/topic/performance/power/power-details)）；内嵌 JS 事件循环/常驻 JVM 的 24h 耗电 `[需实测]` | ? | ? | ? | ? | ? | ? |
| 构建/测试 | core 纯 JVM 单测无需模拟器；Android 专有部分用 Robolectric/真机；RN 用 Jest；Flutter `flutter test`；Rust/NDK 构建更贵 `[推断]` | ✔ | ✔ | △ | △ | ✔ | △ |
| 桌面预览 | Compose Desktop + Hot Reload 1.0；Ktor server；jpackage ~60MB `[官方/社区]`（[Hot Reload](https://blog.jetbrains.com/kotlin/2026/01/the-journey-to-compose-hot-reload-1-0-0/)、[Ktor](https://ktor.io/docs/server-create-and-configure.html)） | ✔ | ✘ | ✔ | ✔ | ✔ | △ |

**证据缺口（老实说）**：冷启动/内存/耗电三项**没有任何可信的同口径公开横向数据**，必须自测；本报告只给平台硬配额与工程经验，不给伪造数字。

---

## 4. Play 政策与插件形态

### 4.1 禁令原文与例外

- **禁令**（Device and Network Abuse）：应用或第三方代码（如 SDK）**不得从 Google Play 以外的来源下载可执行代码**（dex / native code 等）`[官方]`（[政策页](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en)）。
- **例外原文**：[官方政策原文，经两次独立转引核对](https://capawesome.io/cloud/live-updates/faq/)：*"This restriction does not apply to code that runs in a virtual machine or an interpreter where either provides indirect access to Android APIs (such as JavaScript in a webview or browser)."*（Google 页面正文抓取被折叠，且与政策页 `Don't use third-party SDKs ... (except in VMs/interpreters)` 的表述一致）。
- **关键解读** `[推断]`：禁令约束的是**「下载」**，不是「运行」。因此：
  - APK 内自带的 dex / `.so` / assets / QuickJS bytecode → **不涉及下载，合规**（这也是所有 native 库、Termux APKLF 的基础）。
  - Play 下发的 **dynamic feature / asset pack** → 来源是 Play 本身，**合规** `[官方]`（[Play Feature Delivery](https://developer.android.com/guide/playcore/feature-delivery)、[on-demand](https://developer.android.google.cn/guide/playcore/feature-delivery/on-demand)）。
  - 运行时从自家服务器下载 **dex/JAR/.so** → **违规**（哪怕代码是你自己写的）。
  - 运行时下载 **JS 并在解释器/VM 里跑** → 处在例外边缘：webview 里的 JS 明确被豁免；但**自写绑定直接暴露 Android API 的 JS 引擎（QuickJS+JNI）不满足「indirect access」**，属于灰区 `[推断]`。
  - 用 `InMemoryDexClassLoader`/`DexClassLoader` 从 **assets 加载 dex** 本身是 Android 官方支持的（Android 9 兼容 FAQ 就推荐 `InMemoryDexClassLoader`）`[官方]`（[Pie FAQ](https://dl.google.com/dl/developers/android/pie/Android-9-Pie-Compat-FAQs.pdf)）——**前提是 dex 随 APK 分发**。

### 4.2 CodePush 类热更的现状

- Microsoft App Center（含 CodePush）**2025-03-31 停止服务**（分析功能到 2026-06-30）`[官方]`（[Microsoft Learn](https://learn.microsoft.com/sv-se/appcenter/sdk/limitations)、[Pushy 说明](https://pushy.reactnative.cn/docs/intro)）。
- 接棒者：EAS Update（Expo）明确要求遵守各商店规则 `[官方]`（[EAS Update FAQ](https://docs.expo.dev/eas-update/faq/)）；Pushy（react-native-update）以 Hermes 字节码差量包为卖点、自报一行改动可小至 3.4KB `[社区]`（[Pushy FAQ](https://pushy.reactnative.cn/docs/faq)）。
- **对 myco 的判断** `[推断]`：JS 热更在 Play 上长期处于「广泛使用但依赖解释器例外」的灰区，**不能作为插件架构的地基**；可作应急热修（可选、默认关），核心插件能力必须走发版。

### 4.3 Termux APKLF / proot 的可复制思路

- Android 10（targetSdk ≥29）起**不能 exec 应用数据目录里的文件** → 「下载 rootfs 再执行」这条路被系统堵死 `[官方]`（[Termux NLnet 公告](https://termux.dev/en/posts/general/2024/11/11/termux-selected-for-nlnet-ngi-mobifree-grant.html)、[NLnet 项目页](https://nlnet.nl/project/Termux/)）。
- **APKLF 思路**：把可执行文件放进 **APK 的 native library 目录**（`lib/<abi>/`），安装时由系统解到 `/data/app/*/lib/<arch>`，再 symlink 进 rootfs `[官方/社区]`（[Termux 站点源码中的 APKLF 说明](https://docsearch.algolia.com/mcp/docs/repo/termux/termux.github.io)）。代价：`.so` 命名/ABI/16KB 对齐、包体暴涨、Play 审核要解释。
- Play 另有 **on-device container** 条款（容器必须尊重 `REQUIRE_SECURE_ENV`）`[官方政策摘录]`，proot 类方案要按「容器」口径自审 `[推断]`。
- **结论**：work 的按需 proot 要么 **APKLF 随包**，要么 **work 走侧载/F-Droid/GitHub 渠道**；不做「app 内一键下载 Ubuntu 并执行」。

### 4.4 三档插件形态与「一切皆插件」的折中

| 形态 | 政策 | 发版成本 | 能否承载逻辑 | 适合什么 |
|---|---|---|---|---|
| (a) 编译期模块 + 运行时注册表 | ✔ 0 风险 | 要发版（可 Play Feature Delivery 拆模块） | ✔ 全部 | 9 条核心接缝、persona/记忆/工具 |
| (b) 随包资产内可加载（asset dex / JS bytecode） | ✔ 不涉下载 | 要发版 | ✔（JS 需自写绑定；dex 需 `DexClassLoader` + 版本契约） | 实验特性、第三方扩展的「发布形态」 |
| (c) 运行时下载代码 | ✘ 违规（VM/browser JS 例外） | 无需发版 | 灰区 | 不做；只允许**数据插件**（人格卡/提示词/工具 schema/配置） |

**对「一切皆插件」目标的影响**：插件**契约**（目录 + manifest + `apply(ctx)` + 注销器 + 预算 + 自检）100% 保留，而且正是 Kotlin 编译期模块能最干净实现的；改变的只是**分发方式**——能力插件随版本走，数据插件（人格卡、提示词、工具描述、配置）可热下发。**这恰好与「人格卡是版本化模块」的既有设计对齐**。

---

## 5. 打分表与推荐

权重：离线独立 30 / 移动体验 20 / 插件扩展 15 / 开发速度 20 / 长期可维护 15；总分 = Σ 权重 × (得分/5)。

| 候选 | 离线 | 移动 | 插件 | 开发 | 维护 | **总分** | 一句话理由 |
|---|---|---|---|---|---|---|---|
| **A Kotlin/JVM + Compose** | 5 | 5 | 3 | 4.5 | 4 | **89** | 单机闭环最稳；前代可移植；插件=编译期；桌面预览够用 |
| **B KMP** | 5 | 5 | 3 | 3.5 | 5 | **88** | 同 A，多付 Gradle/expect-actual 换未来 target 与 UI 共享 |
| C RN + Hermes | 4 | 3.5 | 3 | 3.5 | 3 | **70** | 一套 TS 代码，但适配层+a11y/FGS 仍写 Kotlin+双运行时 |
| D 内嵌 Node | 4.5 | 2.5 | 4 | 2 | 1.5 | **61.5** | 官方停更、88MB libnode、node:sqlite 缺位、政策钳制 |
| E QuickJS | 4 | 3 | 3.5 | 1.5 | 2 | **58.5** | 350KB 很香，但绑定/工具链自建，开发速度崩 |
| F Flutter | 4.5 | 4 | 3 | 3 | 3.5 | **74.5** | UI 强、包体中等；但换语言且原生能力仍要 Kotlin |
| G Rust+JNI | 5 | 4.5 | 2.5 | 1.5 | 3 | **70.5** | 运行时最强、开发速度最差；适合当加速库不适合当底盘 |

**主选：A（Kotlin/JVM 单核心）。** 理由：
1. 离线独立 30 分是硬要求，A 是唯一「原生层与内核同语言、单机闭环不跨语言/跨运行时边界」的方案（G 的 native 壳与 Rust 核心之间仍有 JNI 边界）。
2. 前代 353 文件/110 测试/78% 平台无关 → 起步期的速度优势是**真实的**（移植 vs 重写），不是估计。
3. 插件模型与 trace-memory `Module` 契约同构；「一切皆插件」的**契约**完整保留，只改分发方式。
4. 日常体验（Compose 原生、FGS、通知、无障碍、键盘安全区）不需要任何第三方桥。
5. 桌面预览虽比 Node 慢，但 `:desktop:daemon:run` + web UI + JVM 单测足够支撑「每天能自己用」的迭代。

**结构升级路径（不是备选）：B。** 若未来要 iOS/Web 客户端，或你希望 UI 也在 Android/桌面共享，就在 A 上开 `multiplatform` 插件——代码不变，只是模块布局与 `expect/actual`。

**真正的备选：C（RN + Hermes）。** 只在一种情形下改选：你先做了 spike 发现「Kotlin 开发速度不可接受」**且**「TS 内核代码复用价值 > 原生体验 + 适配层成本」。若改选 C，必须先接受：a11y/FGS/通知仍写 Kotlin、SSE 用 polyfill/原生模块、包体 +28MB 起、双运行时适配层长期存在。

**不建议**：D/E 作主选（Node 官方停更 + 政策同受钳制 + 包体/耗电最差）；G 作主选（开发速度 1.5 分意味着 3 个月做不出 MVP）。

**敏感性**：按最偏袒 C 的权重（离线 25 / 移动 20 / 插件 5 / 开发 35 / 维护 15）重算：A 91.5、B 87.5、C 70.5；即使把开发速度提到 40、离线压到 20，A 仍领先约 20 分。**C 翻盘需要把「离线独立」这一硬要求本身降级**——那是改需求，不是改选型。

---

## 6. spike 方案（若需要：验证哪 3 件事）

> 总预算约 **1 周（7–9 人日）**，在改 I0 落点之前做完；任一 spike 不过就回到备选论证，不许「先写着看看」。

### spike-1 单机闭环（3–4 人日）
- **验证**：Kotlin Android app 内真实 provider SSE 流 + SQLite + FGS(`specialUse`) 常驻；不接桌面 daemon。
- **判据（可机器判）**：① 首 token p50 < 2s；② 断流 30s 后自动重连、事件 seq 无重复/无空洞；③ 杀进程重启后事件日志重放一致；④ 24h 进程存活率 ≥95%（不白名单时记录实际值，白名单前后各测一轮）；⑤ 通知能唤醒并深链到会话。
- **不过的动作**：若 ②/④ 不达标 → 内核契约改（事件幂等键、重连窗口）而不是换栈；若 ① 不达标 → 先查 TLS/连接复用，再考虑换栈。

### spike-2 包体/冷启动/内存/耗电基线（2 人日）
- **验证**：arm64 release 包、中端机冷启动、常驻 RSS、24h 耗电。
- **判据**：① 相对空 Compose app 的**增量 ≤ 12MB**；② 冷启动到可输入 ≤ 1.5s；③ 空闲常驻 RSS ≤ 120MB；④ 白名单下 24h 耗电 ≤ 3%/天（记录无白名单实测值作为产品文案）；⑤ 桌面 jpackage ≤ 80MB（仅预览，不设硬线）。
- **不过的动作**：超 12MB 先砍依赖（图片库/Compose 组件/多 ABI 分离）；耗电超线则把「常驻」降级为「打开即活」（产品文案与 doctor 同步改）。

### spike-3 插件与预览迭代（2–3 人日）
- **验证**：编译期模块 + 运行时注册表 + 注销器在 Kotlin 下的真实手感；桌面预览速度；单测迁移。
- **判据**：① 2 个真实插件（persona、time）可装/卸、坏 manifest 拒绝加载并报 error（对齐 trace-memory `Module` 契约）；② 改 core 代码 → `:desktop:daemon:run` 到可交互 ≤ 15s；改 web UI 热更新 ≤ 1s；③ 迁移 110+ 个 JVM 单测后 `./gradlew test` ≤ 60s；④ 走查一次「加插件=发版」全流程，确认可接受。
- **不过的动作**：若 ③ 超 60s → 拆模块/并行测试；若 ① 的契约在 Kotlin 下别扭 → 说明 `apply(ctx)` 契约要重设计（这是唯一可能反推 D9 的信号）。

---

## 7. 未解/待实测

1. **冷启动/内存/耗电**：无同口径公开数据，全部 `[需实测]`（spike-2）。
2. **Compose Desktop on Windows**：首次启动/首次绘制、jpackage 体积、托盘与 Ktor 共存；只影响开发体验，不影响产品。
3. **Kotlin JSON Schema 校验器成熟度**：`kotlinx-schema` 仍 0.5.0 早期；可能退回手写校验 + `kotlinx.serialization`（工具 schema 面小，可控）。
4. **MCP kotlin-sdk 的 Streamable HTTP 完整性**（版本/日期/与 2026 版规范的同步度）需要在 spike 中跑一次真实 server。
5. **nodejs-mobile Node 22 fork 的上游可信度**与 16KB 对齐；单 ABI 的 APK 增量未测（88MB 是两 ABI 的 zip）。
6. **QuickJS 自写绑定的实际工期**（估 3–4 周，未验证）。
7. **双 app 共享存储方案**：一包双入口 vs core app + ContentProvider，对 Play 审核/体验/权限的后果（§2.8）。
8. **Play 审核现实**：`specialUse` + 无障碍 + 内嵌 proot（APKLF）组合的过审概率。
9. **Android 17 `ACCESS_LOCAL_NETWORK`** 对局域网/隧道能力的影响 `[官方]`（[Android 17 行为变更](https://developer.android.com/about/versions/17/behavior-changes-17)）。
10. **16KB page 对 proot/APKLF 二进制**的具体对齐要求（需在 work 开工前实测）。
11. **桌面 dev 的「core 无热更新」** 是否可接受（JVM 重启 ~数秒）；若不可接受，考虑 Compose Hot Reload 或把 UI 全放 web。
12. **Android 16K page 与 Room/SQLite native 库**的兼容性（Room 用系统 SQLite 时天然兼容，但 `sqlite-jdbc` 是桌面专用）。

---

### 附：本地证据索引（可直接复核）

- 前代模块/插件契约：[`Module.java`](../../reference/trace-memory/app/collector/host-api/src/main/java/com/dsh/host/api/Module.java)、[`HostContext.java`](../../reference/trace-memory/app/collector/host-api/src/main/java/com/dsh/host/api/HostContext.java)、[`ServiceRegistry.java`](../../reference/trace-memory/app/v2/core/src/main/java/com/dsh/core/ServiceRegistry.java)
- 前代 agent loop / 事件日志 / 工具注册：[`AgentEngine.java`](../../reference/trace-memory/app/v2/agent/src/main/java/com/dsh/agent/AgentEngine.java)、[`FileAgentStore.java`](../../reference/trace-memory/app/v2/agent/src/main/java/com/dsh/agent/FileAgentStore.java)、[`ToolRegistry.java`](../../reference/trace-memory/app/v2/agent/src/main/java/com/dsh/agent/ToolRegistry.java)、[`TranscriptBuilder.java`](../../reference/trace-memory/app/v2/agent/src/main/java/com/dsh/agent/TranscriptBuilder.java)
- 前代 SQLite/记忆：[`MemoryStore.java`](../../reference/trace-memory/app/collector/module-memory/src/main/java/com/dsh/memory/MemoryStore.java)、[`MemorySchema.java`](../../reference/trace-memory/app/collector/module-memory/src/main/java/com/dsh/memory/MemorySchema.java)、[`MemoryFts.java`](../../reference/trace-memory/app/collector/module-memory/src/main/java/com/dsh/memory/MemoryFts.java)
- 前代 provider codec / HTTP 抽象：[`OpenAiChatCodec.java`](../../reference/trace-memory/app/collector/module-models/src/main/java/com/dsh/models/OpenAiChatCodec.java)、[`HttpTransport.java`](../../reference/trace-memory/app/collector/module-models/src/main/java/com/dsh/models/HttpTransport.java)
- 前代 Android 壳（FGS/无障碍/宿主）：[`HostService.java`](../../reference/trace-memory/app/v2/app/src/main/java/com/dsh/v2/HostService.java)、[`TraceAccessibilityService.java`](../../reference/trace-memory/app/v2/app/src/main/java/com/dsh/v2/TraceAccessibilityService.java)、[`AndroidManifest.xml`](../../reference/trace-memory/app/v2/app/src/main/AndroidManifest.xml)
- 平台约束上下文：[mobile-agent-landscape.md](mobile-agent-landscape.md)

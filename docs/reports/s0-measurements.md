# S0-a 测量与验收（2026-11-26 00:20）

> 范围：内核 + CLI 竖切（Android 壳与 GUI 尚未开始，S0-b）。
> 环境：Windows 11 / JDK 21.0.7 / Gradle 8.9（本机 `E:\11-15\tools\gradle-8.9`）/ Kotlin 2.0.21。
> 数据都是**这台开发机**的；不代表手机，也不作普遍性能结论（A8）。

## 1. 功能验收（可复现）

| 项 | 结果 | 证据 |
|---|---|---|
| 内核单测 | **20/20 通过** | `gradle :core:test`（session 6 / provider 4 / credentials 2 / SSE 3 / bridge 4 / openai client 1） |
| SSE 流式端到端 | 通过 | fake-sse（本地 8787）→ core → CLI 增量打印 → 事件落盘 |
| 事件符合契约 | **5/5** 逐条通过 schema 校验 | 一轮对话 = session/start, turn/start, user/message, assistant/message, turn/end |
| 失败必须吵 | 通过 | 无 provider → `PROVIDER_MISSING`，且不写任何事件（单测） |
| 事件重放/版本守卫 | 通过 | 重开进程 seq 接续；未知 formatVersion 拒绝；尾部半行丢弃、中间损坏报错 |
| 凭据不回值 | 通过 | `describe()` 只有 configured/source/writable；单测断言不泄漏明文 |

## 2. 测量数字

| 指标 | 数值 | 备注 |
|---|---|---|
| `:core:test`（warm daemon，`--rerun-tasks`） | **6.7 s** | 20 个测试 |
| 首次全量编译（含依赖下载后） | ≈ 40 s | 不含首次依赖下载（国内镜像后约 1–2 min） |
| `core` jar | 292 KB | 含 kotlinx-coroutines + serialization |
| `app-desktop` dist lib | 4,200 KB / 7 files | JVM 壳，含运行时依赖 |
| `tools/fake-sse` dist lib | 1,728 KB / 3 files | 纯 JDK HttpServer |
| 一轮对话事件文件 | **752 bytes**（5 行） | JSONL，无空白 |
| 工具 schema 预算 | 136 / 24,000 字符 | 1 个演示工具 `time_now` |
| 冷启动（CLI） | 未测 | 手机冷启动属于 S0-b 判据 |
| APK/包体/耗电/24h 存活 | **未测** | 需 Android 壳 + 真机窗口（A8） |

## 3. 过程中真实遇到的问题（已解决，写下来防再犯）

1. **Maven Central 在国内网络下挂起**：单文件下载卡 15min+ 无字节写入。
   → `settings.gradle.kts` 把阿里云镜像放最前，官方仓兜底。
2. **Kotlin 块注释里写 `specs/*.schema.json`**：`/*` 触发嵌套注释，编译报 "Unclosed comment"。
   → 注释里避免出现 `/*` 序列。
3. **`encodeDefaults=true` + 可空字段**：`workspaceId:null` 被写进事件，违反 schema（出现时必须是 string）。
   → `explicitNulls=false`：null 直接省略；schema 保持严格。
4. **Windows 控制台中文乱码**：JVM stdout 默认编码不是 UTF-8。
   → 两个 application 模块加 `-Dfile.encoding/stdout.encoding/stderr.encoding=UTF-8`。

## 4. S0-b Android 壳（代码已落地，真机未验证）

| 项 | 结果 | 备注 |
|---|---|---|
| `:app-android:assembleDebug` | **成功** | AGP 8.7.3 + Kotlin 2.0.21 + Compose BOM 2024.10.01 |
| debug APK | **9,624 KB** | 未开 R8/minify，**不能当最终包体**；release 体积待测 |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 | java.nio.file 需要 26 |
| 前台服务 | `specialUse` + subType 文案已声明 | A7/A8 口径；Play 不适用（侧载） |
| UI | Compose：会话列表 / 聊天页 / 设置页（doctor + FGS 开关） | 模型先接 `FakeChatClient`（离线可复现） |
| 真机/emulator 验证 | **未做** | 本机无 AVD、无 system-image；需 A8 的一次性真机窗口 |
| 冷启动 / RSS / 24h 存活 | **未测** | 同上 |

> 下一步的真机窗口（10–20 分钟）：`adb install -r app-android-debug.apk` → 新建会话 → 发消息 → 杀进程重开历史还在 → 启动前台服务看通知 → 导出 `doctor`。
> 窗口之前不占用用户手机（A8）。

## 5. 下一步（S0-b 剩余）

1. `app-android/`：Compose 壳（会话列表 + 聊天页 + 设置入口）、FGS 骨架、`Bridge` 用 in-process 实现。
2. 真机窗口（提前约，10–20 分钟）：装一次 → 发消息 → 杀进程重开历史还在 → 导出诊断。
3. 补 http-sse Bridge 与两种传输的同一套契约测试（I0 T5）。
4. 指标补：APK 体积、冷启动、RSS；24h 存活/耗电要单独一次真机窗口。

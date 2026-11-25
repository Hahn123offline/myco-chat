# myco

一个**移动端原生**的陪伴 / 工作双 app 项目（chat + work），内核用 Kotlin/JVM 写，桌面端只作开发预览。
当前处于 **S0 walking skeleton** 阶段：内核 + 事件日志 + provider + SSE 流式 + Bridge + CLI 已跑通，
Android 壳与 GUI 还没开始。

> 设计全部在 `docs/design/`；参考资料在 `docs/recon/`；语言无关契约在 `specs/`。
> 决策记录（D1–D10 / A1–A8）见 [`docs/design/20-decision-record.md`](docs/design/20-decision-record.md)。

## 结构

```
specs/                 语言无关契约（JSON Schema + Bridge 契约 + fixtures）
core/                  Kotlin JVM 内核（session / provider / llm / bridge / tools / doctor / settings）
bridge-http/           http-sse 传输（浏览器/远程/调试；JVM-only）
app-desktop/           S0 的 CLI 壳（doctor/sessions/new/chat/demo/discover/serve）
app-android/           Android Compose 壳（会话列表 / 聊天页 / 设置页 + FGS 骨架）
tools/fake-sse/        本地假模型服务（OpenAI 兼容 SSE，不联网、不调真模型）
docs/design/           设计简报、决策记录、I0 方案、状态摘要
docs/recon/            8 份考古/调研
docs/reports/          每阶段测量与验收
```

## 环境（本机已具备）

- JDK 21（`java -version`）
- Gradle 8.9：`E:\11-15\tools\gradle-8.9\bin\gradle.bat`（尚未生成 wrapper）
- Android SDK：`%LOCALAPPDATA%\Android\Sdk`（platform 35/37、build-tools 34/36）

> 国内网络下 `repo.maven.apache.org` / `dl.google.com` 会卡住，`settings.gradle.kts` 已把阿里云镜像放在最前。

## 跑起来

```powershell
$env:MYCO_HOME = 'E:\myco-chat\.myco-dev'   # 隔离本地数据（默认 ~/.myco）

# 1) 测试（20 条）
E:\11-15\tools\gradle-8.9\bin\gradle.bat -p . :core:test

# 2) 构建两个可执行脚本
E:\11-15\tools\gradle-8.9\bin\gradle.bat -p . :tools:fake-sse:installDist :app-desktop:installDist

# 3) 起假模型服务（另一个终端）
.\tools\fake-sse\build\install\fake-sse\bin\fake-sse.bat 8787

# 4) 写本地 provider 模板 + 自检
.\app-desktop\build\install\app-desktop\bin\app-desktop.bat init-local
.\app-desktop\build\install\app-desktop\bin\app-desktop.bat doctor

# 5) 流式对话（事件落盘 + 增量打印）
.\app-desktop\build\install\app-desktop\bin\app-desktop.bat new 测试会话
.\app-desktop\build\install\app-desktop\bin\app-desktop.bat chat <sessionId> 你好

# 6) Android debug APK（无需 AVD；真机安装走一次性窗口）
E:\11-15\tools\gradle-8.9\bin\gradle.bat -p . :app-android:assembleDebug
# 产物：app-android/build/outputs/apk/debug/app-android-debug.apk
```

CLI 命令：`doctor` / `sessions` / `new [title]` / `providers` / `discover <providerId>` / `chat <id> <text...>` /
`demo [text...]` / `serve [port]`（http-sse Bridge）/ `init-local [baseUrl]`。
测试：`:core:test` 26 条 + `:bridge-http:test` 4 条。

## 已验证（S0-a）

- SSE 流式端到端：fake-sse → core SSE 解析 → 增量打印 → 事件落盘（见 [`docs/reports/s0-measurements.md`](docs/reports/s0-measurements.md)）。
- 落盘事件逐条通过 `specs/session-events.schema.json` 校验（5/5）。
- 失败语义：无 provider 时返回 `PROVIDER_MISSING` 且不写脏事件（单测覆盖）。
- 预算：工具面 136 字符 / 上限 24,000。

## 已知边界（S0 刻意不做）

- 只有 `openai-completions` 方言；`deepseek-messages`（Anthropic 协议）/ responses 在 I1。
- 无 GUI、无 Android、无 http-sse Bridge、无 memory、无主动性。
- provider 写入无 revision 乐观锁；会话无 rename/delete。
- GUI 契约已定（`specs/bridge.contract.md`），实现待 T5。

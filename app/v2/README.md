# dsh v2（痕迹记录 · 重写）

独立 Gradle 根，与旧工程 `app/collector` 隔离（旧工程线上对照，不要动）。

## 模块

- `:core`：宿主骨架（Module / HostContext / EventBus / Scope / Host / Store / Config / Log / Health / provide-require）。纯 JVM，周期任务通过 `Scheduler` 注入。
- `:guard`：硬死线（BLOCK：卡号/身份证/支付密码/验证码/CVC；MASK：手机号/邮箱）。纯函数、零依赖。
- `:trace`：痕迹纯逻辑（Trace / Scene / SceneResolver / TraceAccumulator）。不 import Android，假时钟可测。
- `:usage`：UsageStats 降级源纯逻辑（UsageSampler / UsageDeduper）。a11y 断流时记录 app 使用时间段。
- `:app`：Android 壳（无障碍服务 + takeScreenshot + ML Kit OCR + SQLite + 前台通知 + UsageStats 授权）。

## 跑测试

```powershell
$env:JAVA_HOME='C:\Users\<user>\AppData\Roaming\.minecraft\runtime\java-runtime-delta'
cd <workspace>\app\v2
& '<workspace>\tools\gradle-8.9\bin\gradle.bat' test
& '<workspace>\tools\gradle-8.9\bin\gradle.bat' :app:assembleDebug
```

单模块：`gradle :trace:test`。测试报告：`<module>/build/reports/tests/test/index.html`。
APK：`app/build/outputs/apk/debug/app-debug.apk`。

## 数据

`trace.db`（`/data/data/com.dsh.v2/files/trace/trace.db`）表 `traces`：
id/t0/t1/dwell_ms/pkg/activity/page_id/text/shot_ref/guard/guard_hit/cloud_at/label/masked_text/source。
`source=a11y` 是主路（有 text/shot）；`source=usage` 是降级（只有 pkg/t0/t1）。
截图在 `files/trace/shots/`。

## 明确不做

人名、地址、机构名、地名不脱敏；不做语义敏感判断；不做猜测式启发。
pageId 只看结构，不做标题归一化/相似度阈值/错别字表。

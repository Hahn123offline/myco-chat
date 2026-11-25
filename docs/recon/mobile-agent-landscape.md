# 移动端 agent 底盘调研（2026-11-25）

> 第二轮·平台与设备。只读文档 + 本地静态阅读，未调用任何模型服务、未 install/构建。
> **访问日期**：本文所有 web 链接均于 **2026-11-25** 抓取；本地源码 = 工作区 `reference/trace-memory/app/v2`（同日状态）。
> **证据分级**：`[官方]` 平台文档硬限制 · `[源码]` AOSP/本地代码 · `[社区]` 项目自述/issue/博客/商店页 · `[推断]` 我基于前两者的推断 · `[待验证]`。
> 前代已经有 Android 实装（trace-memory v2，Java + AccessibilityService + specialUse FGS），本文多处直接引用它作为"已付过账"的证据。

---

## 0. 一页结论（≤12 行）

1. **常驻 agent 在 Android 只有一条合法活路**：前台服务 + 常驻通知 + 用户白名单；`dataSync` 有 6h/24h 硬上限，`specialUse` 无时长上限但要在 Play Console 声明用途并过审 `[官方]`。
2. **无障碍是"读 + 点"的主通道，截图是限速资源**：`takeScreenshot` 有 ~333ms 平台下限，短间隔返回 `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`，「常驻 1fps 感知」在这个 API 上直接不成立 `[源码/社区]`。
3. **MediaProjection 不能当常驻感知**：每次会话都要用户重新授权、token 单次可用、锁屏即停、Android 15 QPR1+ 还有状态栏常驻提示芯片 `[官方]`。
4. **运行时没有"免费午餐"**：nodejs-mobile 官方 npm 路线停在 Node 18（2024-10）；Node 22 的 Android 移植只见到 2026-09 的社区分支 `[待验证]`；proot Linux 受 Play「禁止下载可执行代码」政策钳制 `[官方]`。
5. **手机 computer use 已有成熟开源形态**：无障碍树 + 动作（droidrun/portal、android-mcp、auto-mobile）；Mobile-Agent-v3 在 AndroidWorld 报 73.3 `[社区]`。
6. **交互范式已被市场验证**：单列流 + 语音输入 + 深度思考开关（DeepSeek）、全双工语音电话（豆包 Seeduplex）、Voice Mode（ChatGPT）；公开 A/B 数据基本没有，只有产品形态证据 `[社区]`。
7. **主动找人只能靠通知**：本地通知 + FCM 是唯一在 Doze/App Standby 下能稳定唤醒的通道，自建 socket 长连在 doze 下会被掐 `[官方]`。
8. **远程形态等于事件日志 + 长连**：手机连设备用 WS/SSE + 幂等事件（ULID/单调 seq）；SakuraFrp 未开自动 HTTPS 时**是明文 HTTP**，必须绑 `*.nyat.app` 子域 `[社区/官方文档]`。
9. ⚠️ **会改架构的发现 A**：myco work 的「按需 proot Linux」如果走「运行时下载 rootfs」，在 Google Play 渠道大概率违规；要么 a) 只在侧载/F-Droid/GitHub 渠道发；要么 b) 按 Termux 的 APKLF 思路把可执行文件塞进 APK native libs。
10. ⚠️ **会改架构的发现 B**：FGS 类型选择直接决定"能不能常驻"和"能不能上架"，必须在 I0 就把它写进移动壳的契约（`specialUse` + subType 文案 + Play 声明），而不是后期补。
11. ⚠️ **会改架构的发现 C**：感知/截图必须设计成「事件驱动 + 按需单帧」，这与前代「连续录屏式感知」的直觉相反；前代实测已在代码里落地为 500ms 交互下限 / 2s 采样下限 / 300ms 去抖。

---

## 1. 运行与存活

### 1.1 前台服务类型（Android 14+ 强制）

- `[官方]` 从 targetSdk 34 起，每个 FGS 必须在 manifest 里声明 `android:foregroundServiceType`，并声明对应 `FOREGROUND_SERVICE_<TYPE>` 普通权限；漏声明 → `MissingForegroundServiceTypeException` / `SecurityException`；运行时须用 `ServiceCompat.startForeground(...)` 传类型位。来源：[Foreground service types are required](https://developer.android.com/about/versions/14/changes/fgs-types-required)；[ServiceInfo 常量](https://developer.android.com/reference/android/content/pm/ServiceInfo)。
- `[官方]` 13 种类型：`camera / connectedDevice / dataSync / health / location / mediaPlayback / mediaProjection / microphone / phoneCall / remoteMessaging / shortService / specialUse / systemExempted`。`shortService` ≈3 分钟且不支持 sticky；`specialUse` 无运行时前置条件，但要在 `<service>` 内写 `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE">` 并在 Play Console「应用内容 → 前台服务类型」声明，**审阅方会看这段自由文本**。
- `[官方]` Play 侧：targetSdk 34+ 的应用必须在上架时声明 FGS 类型（[Play Console 说明](https://support.google.com/googleplay/android-developer/answer/13392821)）。
- `[源码]` 前代 trace-memory v2 的做法就是 `specialUse` + `FOREGROUND_SERVICE_SPECIAL_USE` + subType 文案 `"user-enabled screen trace capture"`（[AndroidManifest.xml:168](../../reference/trace-memory/app/v2/app/src/main/AndroidManifest.xml#L168)）——**这个组合能过编译与运行，是否过 Play 审是另一件事（前代没上架）**。

| myco 候选用途 | 可用的 FGS 类型 | 硬约束 | 结论 |
|---|---|---|---|
| chat 常驻（等消息/心跳/主动投递） | `specialUse`（无更贴切类型） | Play 声明 + 审阅；无时长上限 | **选它**，但必须有"感知暂停"开关 |
| work 跑工具/脚本、拉模型流 | `dataSync` 或 `specialUse` | dataSync 6h/24h，超时 `onTimeout` 必须 stopSelf | 轻任务用 `specialUse`，别用 dataSync 当常驻 |
| 用 a11y 做 computer use | 不额外需要类型（a11y 由系统绑定）；但进程要 FGS 保活 | 见 §2 | 同一 specialUse 服务承载 |
| 语音对话 | `microphone` | 后台不能创建（while-in-use） | 只在前台交互时起 |
| 屏幕录制式感知 | `mediaProjection` | 每次授权、单次 token、锁屏停 | **不采用**（见 §2.4） |

### 1.2 时长上限

- `[官方]` targetSdk 35+ 时，`dataSync` / `mediaProcessing` 合计 **6 小时 / 24 小时**，到点回调 `Service.onTimeout(int,int)`，几秒内不 `stopSelf()` 会抛 `RemoteServiceException`；用户把应用带到前台会重置计时；配额耗尽后再起同类型 FGS 抛 `ForegroundServiceStartNotAllowedException`。来源：[Foreground service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout)。
- `[官方]` `shortService` ≈3 分钟，超时 ANR（即使同时有别的 FGS 也 ANR）。来源同上页 + [类型定义](https://developer.android.com/develop/background-work/services/fgs/service-types)。
- `[官方]` Android 16（API 36）起：**从前台服务里起的后台作业也要遵守 job 运行时配额**（JobScheduler / WorkManager / DownloadManager 都算）。来源：[Changes to foreground services](https://developer.android.com/develop/background-work/services/fgs/changes)。
- `[推断]` 对 myco：「长期常驻」只能挂在 `specialUse` 上；任何"用 dataSync 撑常驻"的设计都会在 6 小时后炸，而且用户看不到原因。

### 1.3 后台启动限制与版本变化

- `[官方]` targetSdk 31+ 禁止后台启动 FGS（例外：从可见状态过渡、通知/widget/bubble 交互、精确闹钟、BOOT_COMPLETED 广播、关掉电池优化、持有 SYSTEM_ALERT_WINDOW 等）。来源：[Restrictions on starting a foreground service from the background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)。
- `[官方]` Android 15（targetSdk 35）：`BOOT_COMPLETED` 接收器**不再允许拉起某些类型的 FGS**；持有 `SYSTEM_ALERT_WINDOW` 也必须当前有可见悬浮窗才算例外。来源：[FGS changes（Android 15 节）](https://developer.android.com/develop/background-work/services/fgs/changes)。
- `[官方]` Android 17（API 37，当前已发布）新增：**本地网络访问需要 `ACCESS_LOCAL_NETWORK` 运行时权限**（target 37 起强制）；后台音频交互需 FGS + while-in-use 能力。来源：[Behavior changes: Android 17](https://developer.android.com/about/versions/17/behavior-changes-17)。
- `[推断]` 对 myco：手机→局域网内设备（未来多设备）在 target 37 上要多要一次权限；开机自启只剩"广播拉起 + 立即转 FGS"这一条窄路，且 Android 15 后部分类型连这条路都堵了。

### 1.4 Doze / App Standby / 电池优化白名单

- `[官方]` Doze 下：网络挂起、**wake lock 被忽略**、标准 Alarm 延后到维护窗口、Wi-Fi 扫描/同步适配器/JobScheduler 不跑（WorkManager 内部就是 JobScheduler，因此也不跑）。`setAndAllowWhileIdle` / `setExactAndAllowWhileIdle` 可在 Doze 触发，但**每应用每 9 分钟最多一次**。来源：[Optimize for Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)。
- `[官方]` 若需实时下行，官方明确推荐 FCM：共享一条连接比每个应用各自维持长连更省电；`RemoteNotification` 可穿透 Doze。App Standby 下长期空闲应用网络访问约「一天一次」。来源同上。
- `[官方]` Play 政策禁止在 Android 6.0+ 上申请 Doze/App Standby 的**直接豁免**，除非「核心功能确实被影响」；`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 只有符合可接受用例才允许。**"任务自动化应用（task automation app）"被官方列为可接受豁免用例**；「即时通讯/聊天类」反而不被接受（应改用 FCM）。来源：[Doze（豁免用例表）](https://developer.android.com/training/monitoring-device-state/doze-standby#exemption-cases)。
- `[官方]` 配额数字（standby bucket，非保证值）：Active 常规 job 60 分钟内最多 20 分钟；Working set 4 小时 10 分钟；Frequent 12 小时 10 分钟；**Rare 24 小时 10 分钟且网络禁用**；Restricted 一天一次 10 分钟。Doze 中 while-idle 闹钟限 7 次/小时。来源：[Power management restrictions](https://developer.android.com/topic/performance/power/power-details)。
- `[推断]` 这是 myco 最关键的一张表：**用户不打开 app 的那个"Rare"世界里，agent 一天只有 10 分钟算力 + 没有网络**。所以"她主动找人"必须走通知，而不是"后台一直跑 loop"。

### 1.5 WorkManager

- `[官方]` 周期任务最小间隔 **15 分钟**（`PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS = 15*60*1000`），且允许不精确（电池优化/Doze 会让它漂移）。来源：[PeriodicWorkRequest](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest)、[AOSP 常量](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/work/work-runtime/src/main/java/androidx/work/PeriodicWorkRequest.kt)。
- `[官方]` WorkManager 在设备不活跃时用 JobScheduler 调度，因此受 job 配额约束（见 §1.4）。来源：[Power management restrictions](https://developer.android.com/topic/performance/power/power-details)。
- `[源码]` 前代没有用 WorkManager，而用 `AlarmManager.setExactAndAllowWhileIdle` + 拿不到精确闹钟权限时降级 `setAndAllowWhileIdle`（[PipelineScheduler.java:68](../../reference/trace-memory/app/v2/app/src/main/java/com/dsh/v2/PipelineScheduler.java#L68)）。这是「每天一次重活」的正确姿势；15 分钟级节拍用 WorkManager 即可。

### 1.6 通知渠道与权限

- `[官方]` Android 8+：通知必须归 channel；**channel 的重要性/声音/震动在创建后不可改**，只能改名与描述；用户拥有最终控制权。来源：[Create and manage notification channels](https://developer.android.com/develop/ui/views/notifications/channels)。
- `[官方]` Android 13+ 需要 `POST_NOTIFICATIONS` 运行时权限；FGS 的常驻通知必须 `setOngoing(true)`。
- `[源码]` 前代分两条 channel：`trace_status`（IMPORTANCE_LOW，不响不徽标）与 `trace_alert`（IMPORTANCE_HIGH + 震动 + 徽标），并在"连续 3 次自愈失败"时用高优先级通知深链到无障碍设置页（[HostService.java:84-93,155-176](../../reference/trace-memory/app/v2/app/src/main/java/com/dsh/v2/HostService.java#L84)）。
- `[推断]` myco 应预置 3 条 channel：`chat_message`（DEFAULT）、`agent_status`（LOW，常驻状态）、`agent_alert`（HIGH，需要人介入）；**channel 建错了没法改，ID 与语义要一次定死**。

### 1.7 开机自启

- `[官方]` `RECEIVE_BOOT_COMPLETED` 广播是允许后台启动 FGS 的例外之一，但 Android 15 起 `BOOT_COMPLETED` 不能拉起某些 FGS 类型（见 §1.3）。来源：[Restrictions on starting a FGS](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)、[FGS changes](https://developer.android.com/develop/background-work/services/fgs/changes)。
- `[源码]` 前代注册 `BOOT_COMPLETED` + `MY_PACKAGE_REPLACED` 接收器（[AndroidManifest.xml:190](../../reference/trace-memory/app/v2/app/src/main/AndroidManifest.xml#L190)）。
- `[社区]` 国内 ROM（MIUI/HyperOS、ColorOS 等）还有系统级自启管理，需要用户二次授权；Shizuku 文档甚至专门写了一整节厂商限制与"允许后台运行"的坑（[Shizuku 用户手册](https://shizuku.rikka.app/guide/setup/)）。
- `[推断]` 不要把"开机就活着"当承诺。产品文案应写成"打开过一次之后尽量保持"，并在 `doctor` 里显示当前保活状态。

### 1.8 耗电根因与量化

| 根因 | 官方/实测数据 | 结论 |
|---|---|---|
| wake lock | Doze 直接忽略 wake lock `[官方]` | 不要指望 wake lock 救命；用一次性的 allow-while-idle 闹钟 |
| socket 轮询 / 自建长连 | 官方：多应用各自维持持久连接会"rapidly deplete the battery"，推荐 FCM 单连接 `[官方]` | chat 下行用通知通道；前台时才开 WS/SSE |
| 后台作业 | Rare bucket：24h 内 10 分钟，网络禁用 `[官方]` | 重活只能在"用户在场/充电/白名单"时跑 |
| 无障碍常驻 | 无官方数字；前代设计是事件驱动（`notificationTimeout=200`、去抖 300ms、采样下限 2s），并靠 FGS 防进程冻结 `[源码]` | 事件驱动 + 去抖是唯一可持续形态；24h 耗电**待实测** |
| WebView 常驻 | 无公开数据 | 内联 HTML 用"按需 WebView + 销毁"，不要后台留一个渲染进程 `[推断]` |
| Node 事件循环 | 无公开数据 | 若用 nodejs-mobile，定时器/心跳是最容易的偷电点；把 tick 收敛到 FGS 生命周期内 `[推断]` |

### 1.9 对 myco 的含义

1. **"她一直在" = FGS(specialUse) + 通知 + 事件驱动感知**，不是"后台 loop 24h 跑"。
2. chat 的下行/唤醒走通知（本地通知 or FCM）；work 的重活在充电/前台/白名单里跑。
3. 电量与配额应作为 `doctor` 的一等输出（当前 bucket、是否白名单、FGS 状态、今日作业分钟数）——参考前代把"如实显示正在记录/未在记录"做成常驻通知（[HostService.java:16-19](../../reference/trace-memory/app/v2/app/src/main/java/com/dsh/v2/HostService.java#L16)）。

---

## 2. 无障碍与感知

### 2.1 AccessibilityService 能做什么

`[官方]` 能力面（[AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)）：
- **读树**：`getRootInActiveWindow()` / `getWindows()` / `findFocus()` / `findAccessibilityNodeInfosByText()`；需在 XML 配置 `canRetrieveWindowContent="true"`（且必须用 XML metadata 声明该能力）。
- **动作**：`performAction(ACTION_CLICK / ACTION_SCROLL_FORWARD / ACTION_SET_TEXT / ACTION_FOCUS ...)`，节点级；`dispatchGesture()` 可发任意手势（坐标级）。
- **截图**：`takeScreenshot(displayId, executor, callback)`（API 30+）与 `takeScreenshotOfWindow(accessibilityWindowId, ...)`（API 34+）——需 `canTakeScreenshot="true"`。
- **覆盖层**：`attachAccessibilityOverlayToWindow/Display`，可用于高亮"我正在操作你"。
- **窗口遍历**：`flagRetrieveInteractiveWindows`。
- `[源码]` 前代配置：事件 `typeWindowStateChanged|typeWindowContentChanged|typeViewClicked|typeViewScrolled`、`flagRetrieveInteractiveWindows`、`canTakeScreenshot=true`、`notificationTimeout=200`（[accessibility_service_config.xml](../../reference/trace-memory/app/v2/app/src/main/res/xml/accessibility_service_config.xml)）。

### 2.2 不能/受限的部分

- `[推断]` 自绘界面（Canvas 游戏、Unity、Flutter 未开语义、部分 React Native 自定义视图）树是空的或只有容器 → 节点点击无从下手，只能坐标点击。
- `[官方]` WebView 需要应用侧开启无障碍支持（`WebView` 的 accessibility 是默认开但内容语义质量取决于网页）；`FLAG_SECURE` 窗口截图为空且树可能被裁剪（`ERROR_TAKE_SCREENSHOT_SECURE_WINDOW` 有专码）。来源：[AccessibilityService 常量表](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)。
- `[官方]` 输入法：无障碍可以填 `ACTION_SET_TEXT`，但**IME 自身的候选窗/输入过程**属于另一层；前代直接把输入法与 systemui 加进 HARD_SKIP 集合（[TraceAccessibilityService.java:90-93](../../reference/trace-memory/app/v2/app/src/main/java/com/dsh/v2/TraceAccessibilityService.java#L90)）。
- `[官方]` 系统级界面（状态栏、音量条、锁屏）不可靠/部分不可读。
- `[官方]` 无障碍的官方定位是"辅助残障用户"，不是自动化通道；Google Play 有专门的无障碍 API 使用政策（用非无障碍 API 能做的别用无障碍做）。

### 2.3 takeScreenshot 配额与间隔

- `[源码]` AOSP `AbstractAccessibilityServiceConnection` 在每次请求前与 `ACCESSIBILITY_TAKE_SCREENSHOT_REQUEST_INTERVAL_TIMES_MS` 比较，太短直接回 `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`（[AOSP android-17.0.0_r1](https://android.googlesource.com/platform/frameworks/base/+/android-17.0.0_r1/services/accessibility/java/com/android/server/accessibility/AbstractAccessibilityServiceConnection.java)）。
- `[社区]` 工程界实测该下限 ≈**333ms**（"~333ms rate limit"）：auto-mobile 项目专门修过"动画期间截图被限速却误报为 capture failed"，并把 `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT` 单独映射成 `rate_limited`（[PR #4995](https://github.com/kaeawc/auto-mobile/pull/4995)、[issue #4927](https://github.com/kaeawc/auto-mobile/issues/4927)）。
- `[源码]` 前代在代码里把下限留得更大：普通采样 **2s**、交互触发 **500ms**、连续交互去抖 300ms，且 `captureOnce` 失败后**先延迟 2s 避开最小间隔**再补一张（[TraceAccessibilityService.java:48-53,638-642](../../reference/trace-memory/app/v2/app/src/main/java/com/dsh/v2/TraceAccessibilityService.java#L48)）。它的经验是"滚动中解析 400ms+，稳定后 40ms"，所以解析放在 250ms settle 之后。
- `[源码]` 错误码要分类计数而不是吞掉：`INTERVAL_TIME_SHORT` / `INVALID_DISPLAY` / `NO_ACCESSIBILITY_ACCESS` / `SECURE_WINDOW`（[TraceAccessibilityService.java:531-535](../../reference/trace-memory/app/v2/app/src/main/java/com/dsh/v2/TraceAccessibilityService.java#L531)）。
- `[推断]` 结论：**感知 API 必须是"事件 → 去抖 → 单帧"**；别做 1fps 环形缓冲。若需视频级，那是 MediaProjection 的活，且它有自己的授权代价（§2.4）。

### 2.4 MediaProjection 授权 UX 成本

- `[官方]` 每次 media projection **会话**都要求用户同意：`createScreenCaptureIntent()` → 用户确认 → `getMediaProjection()`；**token 只能用于一次 `createVirtualDisplay()`**，重复使用抛 `SecurityException`。来源：[Media projection](https://developer.android.com/media/grow/media-projection)。
- `[官方]` targetSdk 34+ 必须有 `mediaProjection` 类型的 FGS 在跑；锁屏、用户点状态栏芯片、别的会话开始、进程被杀都会终止会话，必须注册 `MediaProjection.Callback.onStop()` 释放资源。
- `[官方]` Android 15 QPR1+ 有**大而显眼的状态栏芯片**提示正在投屏/录制，用户可一键停止；Android 14 起默认支持"只共享单个应用窗口"（对"只看某个 app"更友好）。
- `[推断]` 这是"每次都要用户点一下 + 屏幕上一直有提示"的通道。适合 myco work 的"我帮你操作这个 app 30 秒"，不适合 chat 的常驻感知。

### 2.5 camera / 麦克风后台限制

- `[官方]` while-in-use 权限（camera/mic/location）在应用处于后台时**不能创建**对应 FGS，targetSdk 34+ 会直接抛 `SecurityException`（前台检查 `checkSelfPermission` 返回 GRANTED 不代表后台可用）。来源：[Restrictions on starting FGS（while-in-use 节）](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#wiu-restrictions)。
- `[官方]` 豁免：系统组件启动、widget/通知/PendingIntent 交互、device owner、VoiceInteractionService 等。
- `[推断]` 语音陪伴只能在"用户正在看 app"时开麦；后台唤醒麦克风这条产品假设在 Android 上不成立。

### 2.6 通知监听 / UsageStats

- `[官方/社区]` `NotificationListenerService` 需要 `BIND_NOTIFICATION_LISTENER_SERVICE`，且用户必须在"设置 → 通知使用权"里**手动**开启，无法用运行时权限弹窗申请（[Flutter 插件说明](https://pub.dev/packages/notification_listener)、[中文排障文](https://www.volcengine.com/article/864873)）。
- `[官方/源码]` `PACKAGE_USAGE_STATS` 是受保护权限，需 `Settings.ACTION_USAGE_ACCESS_SETTINGS` 手动授权；前代把它当**降级数据源**并标注 `tools:ignore="ProtectedPermissions"`（[AndroidManifest.xml:12-15](../../reference/trace-memory/app/v2/app/src/main/AndroidManifest.xml#L12)）。
- `[推断]` 这两个都是"引导一次、长期有效、用户可能随时关"的权限，必须像前代的 `A11yHealth` 一样做**授权状态自检 + 掉权限告警**（[A11yHealth.java](../../reference/trace-memory/app/v2/app/src/main/java/com/dsh/v2/A11yHealth.java)）。

### 2.7 对 myco 的感知设计含义

```
snapshot(): 结构化、毫秒级、便宜（窗口/活动/焦点/通知流）  ← 默认
capture():  像素、秒级、有配额、带 errorCode              ← 用户显式或 a11y 触发
```
- 事件驱动 + 去抖 + 单帧是唯一可持续形态（与前代一致）。
- 每个失败都要有 `reason` 出口（`rate_limited / secure_window / no_access / interval_short`），不许静默返回空帧——这是第一轮 C4/C8 约束在 Android 侧的翻版。

---

## 3. 运行时选型对比

### 3.1 事实

- `[官方]` **nodejs-mobile**：官方 React Native 插件 npm 最新是 `nodejs-mobile-react-native@18.20.4`（Node 18 LTS，2024-10 发布；`_npmUser` 仍是社区维护者）。来源：[npm registry](https://registry.npmjs.org/nodejs-mobile-react-native/latest)、[官网](https://code.janeasystems.com/nodejs-mobile)。
- `[社区/待验证]` 有镜像仓库的 CHANGELOG 显示 **2026-09-12 从 Node v18.20.4 升到 v22.23.2 LTS，NODE_MODULE_VERSION 108→127，目标平台 arm64-v8a + x86_64（API 30+）**，产物是 `libnode.zip`（[镜像 CHANGELOG](https://gitcode.com/OpenFlun/nodejs-mobile/blob/main/CHANGELOG.md)）。**这是镜像/fork，不是官方 release 页，上游状态需核对**。
- `[官方]` **16 KB page size**：targetSdk 35+ 的 64 位应用必须兼容 16KB 内存页；**2027-02-01 起不支持 16KB 的更新无法上架**。纯 Java/Kotlin 应用天然兼容；任何 `.so`（nodejs-mobile、Rust、proot 里的 native 二进制）都要重编/对齐。来源：[Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes)。
- `[官方]` **Play 禁止下载可执行代码**：应用不得从 Google Play 以外下载 dex/JAR/`.so` 等可执行代码（解释器/VM 内除外）。来源：[Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/9888379)。
- `[官方]` **Termux**：因为 Android 10 起 `targetSdk >= 29` 的应用不能 exec 应用数据目录里的文件，Termux 一直用 `targetSdkVersion=28` 的"向后兼容模式"绕过；正在做 **APKLF（把可执行文件放进 APK native libs 目录，再 symlink 进 rootfs）** 和 termux-core 库（MIT）来合规。来源：[Termux NLnet 公告](https://termux.dev/en/posts/general/2024/11/11/termux-selected-for-nlnet-ngi-mobifree-grant.html)。
- `[官方/社区]` Termux **已回到 Google Play**（2024-06 起，限 Android 11+），但官方说 Play 版仍"under development，有稳定性问题与限制，大多数用户应从 F-Droid 装"（[Termux Wiki](https://wiki.termux.com/wiki/Termux_Google_Play)、[termux-play-store](https://github.com/termux-play-store)）。
- `[社区]` Android 12+ 有 **phantom process killer**：应用 fork 出的子进程超过阈值（默认 ~32）会被系统杀，Termux/proot 批量提进程会被误杀，需要用户手动改 `settings put global settings_enable_monitor_phantom_procs false`（[Termux 社区文档](https://github.com/agnostic-apollo/Android-Docs/blob/master/en/docs/apps/processes/phantom-cached-and-empty-processes.md)、[termux-app#2366](https://github.com/termux/termux-app/issues/2366)）。这是 proot 方案的"用户必须先做一步设置"成本。
- `[社区]` **Shizuku**：用无线调试（Android 11+）或 root 把 ADB 权限借给普通 app，**每次重启都要重新启动**，且国产 ROM 有各种额外开关（[Shizuku 手册](https://shizuku.rikka.app/guide/setup/)）。
- `[社区]` 手机端本地推理有新的实测数据点：ENTITY（Arm 手机自适应 runtime）报告在**同一台手机、同一模型**上 18.1 tok/s vs llama.cpp 默认 10.8 tok/s，且**同工作量电池消耗低 42%**（[项目证据页](https://kkjjkamal123.github.io/ENTITY-WEB/evidence/)）。注意这是特定机型/模型的单点数据，不可直接外推。

### 3.2 选型 × 代价表

| 方案 | 能拿到什么 | 硬代价 | 分发/政策风险 | 适合 myco 哪部分 |
|---|---|---|---|---|
| **Kotlin/JVM 内嵌 agent**（OkHttp+SSE / Room / WorkManager） | 最稳的进程/通知/权限/FGS 集成；16KB 天然兼容；无 exec 问题 | 不能跑 Linux 命令；工具生态要自己写 JVM 版；LLM SDK 少（自己拼 HTTP） | 低 | **移动壳的默认内核**（chat 与 work 的本地编排） |
| **nodejs-mobile（libnode.so）** | 直接复用 Node 生态/TS 代码；和桌面 daemon 同构 | 官方 npm 停在 Node 18；native 模块要交叉编译；体积大；`.so` 要 16KB 对齐；JS 定时器耗电 | 中（`.so` 随 APK 分发是合规的，运行时下载才违规） | 只在"必须复用 daemon 代码"时；不建议做第一版 |
| **Termux + proot-distro** | 几乎完整的 Linux（bash/apt/python/node）；能跑任意命令 | exec 限制（必须 target 28 或 APKLF）；phantom killer；ptrace 性能损失；rootfs 几百 MB；启动 1–3s | **高**：运行时下载 rootfs 里的二进制 = Play 禁止下载可执行代码 | work 的"重任务后端"，**侧载渠道**或 APK 内嵌 |
| **Rust/C++ core（NDK/JNI）** | 低内存、可复用桌面 core 的部分（解析/协议/向量） | JNI 胶水；每 ABI 一份 `.so`；16KB 对齐；调试成本 | 低（`.so` 随包） | 未来把"协议/日志/SQLite 增量"做成共享 core 时 |

### 3.3 结论

- **MVP 阶段**：桌面 daemon 继续做唯一真相；移动壳 = Kotlin 客户端 + 本地轻编排（"手机内部完成的轻任务"），通过 HTTPS/WS 连 daemon。
- **work 的 Linux**：优先 **APK 内嵌 native libs（APKLF 思路）** 或**独立侧载渠道**；不要设计成"app 内一键下载 Ubuntu rootfs 并执行"——那是 Play 政策红线。**这一点建议在 I0 就写进决策记录。**
- **JS 复用**：如果确实要复用 daemon 的 TS 模块，先验证 nodejs-mobile 在 Android 16/17 + 16KB 页上的实测（体积/启动/耗电/ABI），并把版本钉在某个 fork 上。

---

## 4. 手机 computer use 参考实现

### 4.1 无障碍驱动（主流，开源）

| 项目 | 架构 | 关键点 | 证据 |
|---|---|---|---|
| **droidrun / mobilerun-portal** | 手机端 a11y service 暴露本地 API（HTTP/WS），外部 Python agent 连 | Portal 只做"看 + 动"的底座，模型在外部；a11y 树 + 截图双通道 | [GitHub](https://github.com/droidrun/droidrun)、[droidrun-agent(PyPI)](https://pypi.org/project/droidrun-agent/) `[社区]` |
| **android-mcp / android-remote-control-mcp** | MCP server（JSON-RPC），桥接 ADB + Accessibility | 把手机能力包成 MCP 工具；a11y 树解析成可点元素 | [PyPI](https://pypi.org/project/android-mcp/)、[项目文档](https://github.com/danielealbano/android-remote-control-mcp/blob/main/docs/PROJECT.md) `[社区]` |
| **auto-mobile** | a11y 截图 + 层级，工程化处理限速 | 明确把 `~333ms` 限速映射成独立错误码，避免误判 | [PR #4995](https://github.com/kaeawc/auto-mobile/pull/4995) `[社区]` |
| **Mobile-Agent-v3 / GUI-Owl** | 多 agent 框架 + GUI 基座模型 | AndroidWorld 73.3、OSWorld 37.7（论文自报） | [arXiv 2508.15144](https://arxiv.org/html/2508.15144v1) `[社区/论文]` |

### 4.2 Shizuku / ADB-on-device

- `[社区]` 通过无线调试配对把 ADB 级权限借给 app，可用 `adb shell input tap/swipe/text`、`uiautomator dump`、`screencap`；**能力比 a11y 大（能跨 app、能跑 shell）**，但每次重启要重新启动 Shizuku，且国产 ROM 常屏蔽（[Shizuku 手册](https://shizuku.rikka.app/guide/setup/)）。
- `[推断]` 对 myco：Shizuku 适合作为「work 的高级模式/可选增强」，不能作为默认路径——用户安装成本太高。

### 4.3 AutoX / Auto.js 类

- `[待验证]` 这类工具（Auto.js 闭源化、AutoX 停更、AutoJS6 社区 fork）依赖无障碍 + 悬浮窗 + 脚本引擎，历史上大量用于"自动化点击"；其核心能力已被 a11y 原生 API 覆盖，**不建议作为依赖**，只作为"用户预期参考"。
- `[推断]` 它们留下的产品经验是：**用户愿意为"帮我点"付权限**，但要求"随时能停 + 看得见在干什么"（悬浮窗/通知）。myco 的 work 模式应默认显示"正在操作"的覆盖层（a11y overlay 或 FGS 通知）。

### 4.4 坐标点击 vs 节点点击 vs 混合

| 方式 | 优点 | 坑 |
|---|---|---|
| 节点点击（`ACTION_CLICK`） | 稳、与分辨率无关、可解释 | 自绘/游戏/部分 Flutter 无节点；节点可能 stale（树随时变） |
| 坐标点击（`dispatchGesture`） | 万能 | 分辨率/密度/横竖屏/挖孔屏漂移；键盘弹出改变布局；误触 |
| 文本输入 | `ACTION_SET_TEXT` 快 | 受 IME 组合态影响；有些 app 校验真实按键事件 |
| 混合 | 先树后坐标，失败回退 | 需要"动作 → 校验"闭环（截图/树 diff） |

- `[源码]` 前代的经验可直接复用：解析树时限制 ≤300 节点、稳定 250ms 后再解析、把输入法/systemui/自身包名加入跳过集合、页面解析 p50/p95 打点（[TraceAccessibilityService.java:40-93](../../reference/trace-memory/app/v2/app/src/main/java/com/dsh/v2/TraceAccessibilityService.java#L40)）。

### 4.5 对 myco work 的含义

- 第一版 computer use = **无障碍树 + 动作 + 单帧校验**；不引入模型在环的闭环（模型只给"目标/下一步"，执行与校验在本地确定性代码里）。
- 所有动作都要"可回放"：记录 `页面指纹 + 节点选择器 + 坐标 + 结果`，否则用户没法信任（对应第一轮 C4「失败必须吵」）。
- MediaProjection 只作为"用户显式开启的一段录制/演示"，不进常驻管线。

---

## 5. 移动端 chat 交互

> 证据说明：这一类**没有公开 A/B 或用户研究**，只有产品形态（商店页、官方帮助中心、媒体报道）。以下标注为 `[社区]`，不可当因果结论。

### 5.1 单列流

- `[社区]` DeepSeek 手机 App：单列消息流 + 底部输入框 + 输入框旁「DeepThink」思考模式开关 + 会话历史同步（[2026 Android 指南](https://deepseek.wang/guides/deepseek-on-android/)）。
- `[社区]` 豆包：对话框右上角「电话听筒」进入实时语音通话，2026 版全量开放端到端语音（Seeduplex，全双工、可打断）（[媒体报道](https://zhuanlan.zhihu.com/p/2025581998811489316)）。
- `[官方]` ChatGPT：Voice 出现在主聊天页（悬浮语音球）或独立全屏 Voice Mode，可在设置里开启（[Voice Mode FAQ](https://help.openai.com/en/articles/8400625-voice-mode-faq)、[发布公告](https://openai.com/index/chatgpt-can-now-see-hear-and-speak/)）。
- `[推断]` 三家都是**单列流 + 输入框 + 一两个模式开关**；没有一家把"多会话管理"放首屏。这与 myco chat「绝对连续的单焦点角色、不暴露多会话」方向一致。

### 5.2 语音输入

- `[社区]` DeepSeek 语音输入是"点麦克风 → 说话 → 自动转文字进输入框 → 可再开深度思考/联网 → 发送"（[同上指南](https://deepseek.wang/guides/deepseek-on-android/)）；这确认了"语音只做输入法、不做对话"的中间态。
- `[社区]` 豆包走得更远：实时全双工语音通话（可打断、有情绪），是独立入口而不是输入框附件。
- `[推断]` myco chat 的语音应分两级：**语音输入（默认）** 与 **语音通话（可选，显式进入）**；后者是 FGS `microphone` + 前台交互，不能后台常开（§2.5）。

### 5.3 卡片 / HTML 内联

- `[待验证]` 公开证据弱：ChatGPT 有 canvas/富内容，DeepSeek/豆包以 Markdown 文本 + 少量结构化卡片为主。
- `[推断]` myco work「模型直接输出可交互 HTML」在手机上的实现要点：**沙箱 WebView（无 JS 桥默认、必要时受限桥）+ 高度自适应 + 一次性生命周期**；风险是 WebView 常驻耗电/内存、以及"模型写的 HTML 拿到桥权限"的安全面。
- `[推断]` chat 侧建议只允许**受限卡片**（按钮/表单/链接），不允许任意 HTML——单焦点陪伴的产品气质和"可交互网页"是冲突的。

### 5.4 通知与未读

- `[官方]` 通知是 Doze/Standby 下唯一可靠的"找人"通道（§1.4）。
- `[推断]` chat 未读 = 通知（可展开/可直接回复）+ 应用内水位；work 未读 = 会话列表徽标 + 完成/失败通知。
- `[推断]` 「她主动说话」与「系统通知」必须共用一条投递队列（myco 已有 delivery 接缝），否则会出现"通知发了但会话里没有这条消息"的经典不一致。

### 5.5 会话切换成本

- `[推断]` chat 不暴露多会话，切换成本 = 0（这就是产品卖点）；work 有多会话，切换成本 = 列表滚动 + 恢复上下文 + 重新绑模型。
- `[经验]` 手机上"新会话"应该是**一个手势/一个按钮**，不是"左上角抽屉 → 新建"；这是移动端 vs 桌面的关键差异。
- `[源码]` 前代 v2 的 chat 是单 `ChatActivity`，没有会话列表（[ChatActivity.java](../../reference/trace-memory/app/v2/app/src/main/java/com/dsh/v2/ChatActivity.java)）——再次印证"单会话是架构决定"。

### 5.6 证据缺口（老实说）

- 没有找到任何"单列流 vs 多列/抽屉"的公开 A/B 或用户研究；也没有"语音输入提升留存"的公开数据。
- 因此第 5 节的结论全部是**产品形态归纳 + 推断**，不进硬约束清单；若要验证，只能自己做灰度。

---

## 6. 多设备/远程工作区前置

### 6.1 协议形态

- `[源码/本地]` DSH 已验证的边界：客户端只依赖 `api/remotes` + `client-connection`（HTTP RPC + 事件流），React/DOM 不带（[dsh-harness.md](dsh-harness.md) §0 第 12–13 条）。myco 的 `packages/api` + `client-core` 应复用同一形态。
- `[推断]` 手机连设备的推荐组合：
  - 下行：SSE（简单、可断线续传）或 WS（双向、心跳便宜）；**移动网络下 SSE 的自动重连语义更省事**。
  - 上行：HTTP RPC（幂等 key），不做双向 RPC 复用同一 socket。
  - 鉴权：设备配对码 → 长期 refresh token → 短期 access token；token 存 Android Keystore。
  - 断线重连：指数退避 + 抖动，带 `lastEventId`；服务端保留最近 N 分钟事件用于回放。
  - 离线队列：客户端出站队列（同 myco delivery 的落盘队列思路），幂等去重键 = `(deviceId, clientMsgId)`。
- `[推断]` 事件日志要"同步友好"：**ULID/单调 seq** 排序、**不写绝对路径**（用 session 相对路径 + 内容寻址）、时间戳只作展示、排序/去重靠逻辑时钟（Lamport/在线 seq），这样手机与设备时钟漂移也不影响合并。

### 6.2 HTTPS 隧道注意事项（SakuraFrp / frp）

- `[官方文档]` SakuraFrp 支持 HTTP/HTTPS/TCP 等隧道；**自动 HTTPS 需要把 `*.nyat.app` 子域绑定到隧道**，frpc 会从服务端自动加载对应 SSL 证书；域名绑定后台可看 `www.example.nyat.app:10086` 这类地址（[子域绑定功能](https://doc.natfrp.com/bestpractice/domain-bind.html)）。
- `[社区/安全]` 这是最关键的坑：**"若没绑定官方免费 `*.nyat.app` 子域、frpc 也没开自动 HTTPS，SakuraFrp 给的是明文 HTTP（手机↔节点之间无 TLS）"**，工具审批数据会明文经过公网节点（[dsh-mobile-bridge README 安全提示](https://github.com/msz-legend/dsh-mobile-bridge)）。→ myco 必须把"自动 HTTPS + 子域绑定"做成 `doctor` 检查项，检测到明文就拒绝连接。
- `[社区/待验证]` 免费版限额说法：单用户 1 个隧道、带宽 5Mbps、连接数 50、不支持自定义域名/HTTPS 加密/端口映射（[CSDN 问答](https://ask.csdn.net/questions/9344717)）。**这条是社区经验，需以面板实际为准**；但"免费版不适合当生产通道"是明确结论。
- `[官方]` frp 本身支持 HTTP/HTTPS/STCP/XTCP/VirtualNet 等多种形态（[frp Features](https://gofrp.org/en/docs/features/)）；XTCP 适合点对点，但需要两端都在线。
- `[推断]` 移动网络下隧道还会引入：NAT 超时断连（需应用层心跳）、节点延迟抖动、上传带宽瓶颈（手机上行常 <5Mbps）。→ 流式响应要按"低带宽"设计（增量、可中断、不做大图上传）。

### 6.3 与 myco 当前架构的衔接

- `[本地]` 第一轮已定：daemon 持有事件日志（append-only）、客户端只是投影；因此"手机连设备"天然是"连到那个 daemon/事件日志"，而不是"同步两份状态"。
- `[推断]` 需要补的契约：
  1. `event.id` 用 ULID（或 `sessionSeq:monotonic`），保证跨设备排序；
  2. 事件里禁止绝对路径/主机名（`cwd` 用 workspace 相对引用）；
  3. `delivery` 队列按 `(deviceId)` 分片，未 ack 的消息在重连后重投；
  4. 客户端展示"离线期间发生的事"用事件回放，不用本地推断。
- `[待验证]` 手机作为"纯客户端"时，daemon 在 NAT 后（SakuraFrp）的 QoS 是否够跑流式 + 工具审批；以及多设备同时连一个 daemon 的写冲突（应只允许一台设备持写租约）。

---

## 7. myco 从第一天就要遵守的移动端约束清单（≤10 条）

| # | 约束 | 类型 | 依据 |
|---|---|---|---|
| 1 | 常驻必须走 **FGS + 常驻通知**，类型选 `specialUse`（Play 声明 subType），**不要用 dataSync 当常驻** | **硬限制** | §1.1/§1.2 |
| 2 | 「她主动找人」只能走**通知**（本地或 FCM）；后台 socket 长连不能作为唤醒机制 | **硬限制** | §1.4 |
| 3 | 截图/感知按 **"事件 → 去抖 → 单帧"** 设计，尊重 `takeScreenshot` 平台限速并分类错误码 | **硬限制**（限速）+ **经验**（去抖参数） | §2.3 |
| 4 | **MediaProjection 只做用户显式开启的短会话**，不做常驻感知 | **硬限制** | §2.4 |
| 5 | 后台不能开摄像头/麦克风；语音陪伴必须前台交互 | **硬限制** | §2.5 |
| 6 | Linux/proot 后端**不能靠"运行时下载可执行文件"分发**；走 APK 内嵌 native libs 或侧载渠道 | **硬限制**（Play 政策） | §3.1 |
| 7 | 移动壳内核用 **Kotlin/JVM**（HTTP/SSE/SQLite/WorkManager/通知），不要默认背 Node/React | **经验** | §3.2 |
| 8 | 事件日志：**ULID/单调 seq + 无绝对路径 + 逻辑时钟**，客户端离线可重放 | **经验** | §6.1/§6.3 |
| 9 | 电量与配额当一等公民：WorkManager ≥15min、Rare bucket 24h 仅 10min、Doze 无网；设计成"用户在场才跑 loop" | **硬限制**（配额）+ **经验**（设计） | §1.4/§1.5 |
| 10 | 远程连接**默认必须加密**：绑 `*.nyat.app` + 自动 HTTPS，检测到明文 HTTP 就拒绝/告警 | **经验**（安全硬要求） | §6.2 |

---

## 8. 未解/待实测

1. **nodejs-mobile 上游状态**：官方 release 是否还活跃、Node 22 的 2026-09 更新是官方还是 fork；libnode 在 Android 16/17 + 16KB 页上的实测（启动时间、内存、耗电）。
2. **无障碍常驻 24h 的真实代价**：耗电 %、内存、事件风暴下的 CPU；前代有 p50/p95 解析打点但没有整机续航数据。
3. **WebView 常驻 vs 按需**：myco work 的 HTML 内联交互在低端机上的内存/耗电对比。
4. **输入注入成功率**：`ACTION_SET_TEXT` vs ADB IME vs 剪贴板，在 MIUI/HyperOS、ColorOS、HarmonyOS 上的差异。
5. **国产 ROM 后台存活实测**：特别是一加/小米/华为的"自启动 + 电池无限制 + 后台锁定"三件套步骤，需要真机记录并写进引导文案。
6. **proot on targetSdk 36 的可行性**：APKLF 是否已可用；ptrace 开销与 rootfs 启动时间；phantom killer 默认阈值。
7. **Play 审核现实**：`specialUse` + 无障碍 + 通知监听 三者组合的过审概率；是否需要"无障碍仅用于用户显式操作"的产品约束。
8. **本机推理功耗**：ENTITY 的数据是单点，需在目标机型上自测 tok/s 与 mAh/1k tokens。
9. **多设备写租约**：一个 daemon 被多台手机同时连时的冲突策略（"单写多读" vs 事件合并）未验证。
10. **手机端语音全双工**：豆包 Seeduplex 是自研端到端模型；myco 若走"ASR → LLM → TTS"三段式，延迟与打断体验需要实测（前代 ASR 冷启动 9s 是桌面数据，见[设计简报 §7.4](../design/00-design-brief.md)）。

---

### 附：本地证据索引（可直接复核）

- 前台服务与通知：[HostService.java](../../reference/trace-memory/app/v2/app/src/main/java/com/dsh/v2/HostService.java)、[AndroidManifest.xml](../../reference/trace-memory/app/v2/app/src/main/AndroidManifest.xml)
- 无障碍 + 截图限速：[TraceAccessibilityService.java](../../reference/trace-memory/app/v2/app/src/main/java/com/dsh/v2/TraceAccessibilityService.java)、[accessibility_service_config.xml](../../reference/trace-memory/app/v2/app/src/main/res/xml/accessibility_service_config.xml)
- 授权自检：[A11yHealth.java](../../reference/trace-memory/app/v2/app/src/main/java/com/dsh/v2/A11yHealth.java)
- 闹钟/电池降级：[PipelineScheduler.java](../../reference/trace-memory/app/v2/app/src/main/java/com/dsh/v2/PipelineScheduler.java)

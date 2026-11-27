# 手机端事实库（P1 · 只读审计）：mobile-adapt（Web 客户端插件）/ mobile-android（Android 宿主壳）

> 审计对象：
> ① `E:\myco-chat\_ws\refs\dsh-client-ui-mobile-adapt`（15 文件；界面全在 `lib/client.js` 1694 行的单文件 bundle 里，含内联 CSS 模板串）
> ② `E:\myco-chat\_ws\refs\dsh-mobile-android`（46 文件 / 7.5 MB；Java 壳 + Node 运行时 + 热更新器 + Shizuku 工具箱）
>
> 对账分母：
> ① `_ws/out/findings-mobile-adapt.csv`（**41** 条，官方 scan 默认语言集 `ts,tsx,js,jsx,kts,kt,css` 只命中 `lib/client.js` + `lib/index.js` 两个文件）
> ② `_ws/out/findings-mobile-android.csv`（**82** 条 = 官方 scan **0** 条 + Shell 层手工枚举 82 条，见 §1.3 的方法说明）
>
> 事实库：`docs/spec/ref/dsh-mobile-adapt.facts.csv`（41 行：E0 41 / excluded 0）、
> `docs/spec/ref/dsh-mobile-android.facts.csv`（82 行：E0 75 / excluded 7）。
> 对账报告：`_ws/out/coverage-mobile-adapt.md`、`_ws/out/coverage-mobile-android.md` → **uncovered 0 / stale 0**。
>
> **本路唯一目标：桌面 → 手机"具体改了什么"**。不复述 DSH 桌面行为（见 `ref/dsh-base.md` §0/§2.1、`ref/dsh-chat.md` §0），
> 只把桌面事实当对照基准。
>
> **路径缩写**（下文一切 `:行` 均指该缩写文件；跨文件引用写全 `路径:行`）：
>
> | 缩写 | 实际文件 | 所属 |
> |---|---|---|
> | `M/` | `_ws/refs/dsh-client-ui-mobile-adapt/lib/client.js` | mobile-adapt 全部界面逻辑 + 内联 CSS |
> | `R1/` | `_ws/refs/dsh-client-ui-mobile-adapt/README.md` | mobile-adapt 文档（E1 声称） |
> | `A/` | `_ws/refs/dsh-mobile-android/android/java/ai/dshmobile/app/MainActivity.java` | Android 壳主体 |
> | `S/` | `_ws/refs/dsh-mobile-android/android/java/ai/dshmobile/app/DshService.java` | 前台服务 |
> | `X/` | `_ws/refs/dsh-mobile-android/android/AndroidManifest.xml` | Manifest |
> | `C/` | `_ws/refs/dsh-mobile-android/android/assets/console.html` | 诊断日志台 |
> | `R2/` | `_ws/refs/dsh-mobile-android/README.md` | android 仓库 README |
> | `D2/` | `_ws/refs/dsh-mobile-android/docs/DSH-Mobile-说明.md` | android 使用说明 |
> | `dsh` | `E:\deepseek-harness`（桌面原版源码，只作对照） | 桌面基准 |
>
> 依据级别：**E0** 源码行/真机 · **E1** 文档/注释声称 · **E2** 推测（只允许出现在 §6）。
> 行号全部来自本次实读（两个仓库 ~60 文件已全读；`lib/client.js`、`MainActivity.java`、`console.html` 逐行读完）。

---

## 0. 一页结论

> 10 条「桌面 → 手机」改造规律，每条带证据。**推测一律在 §6。**

1. **两个仓库根本不在同一层：一个是"UI 适配层"，一个是"Android 宿主壳"，后者不含任何移动端 UI 适配。**
   mobile-adapt 是纯客户端插件，用一段 `@media (max-width: 767px)` 的内联 CSS + 两个 React 组件覆盖 DSH 的桌面布局（`M/:169-1102` 内联样式表、`M/:1661-1687` 注册 `shell.overlay` 与 `conversation.composer.dock`）。
   mobile-android 只做「把 DSH 内核 + Node 跑进 APK，用 WebView 打开 `dsh --profile web` 的原生 Web UI」——文档自己写明界面就是原生 Web UI（`D2/:17`），代码里**唯一改变 DSH 页面行为的注入只有「外链拦截脚本」**（`A/:406-426`；日志/状态注入都带 `window.__dshLog&&` 之类 no-op 防御，`A/:467-481`），**不注入任何移动端 CSS**（`A/:181-296` 全文）。**所以"手机 UI 改造"证据几乎全部来自 mobile-adapt；mobile-android 提供的是壳层契约（返回键/外链/键盘/安全区/文件选择）。**

2. **命中区的实际策略 = 视觉尺寸直接抬到 44px（主）+ 伪元素透明扩边（次）+ 重排布局（少数），并明确放弃 ripple。**
   菜单行/导航行/输入框/按钮一律 `min-height:44px`（`M/:524-538,770,816-818,854,904-905,907-920,968`）；只有「视觉尺寸不能改」的微小内联控件用伪元素扩边：开关/复选框 `::after { inset:-12px -8px }`（`M/:940-945`）、16px 问号 `::after { inset:-14px }`（`M/:955-959`）；字号 stepper 因为两个 17×12 箭头中心距仅 14px 互相覆盖，改为**静态横排 44×44 + gap 2px**（`M/:802-865`，注释即 issue #2 根因说明）。触摸反馈用 `:active` 底色/`scale(0.94~0.98)`，**全仓库没有任何 ripple 实现**（`M/:333-336,594-597,680-682,841-843`）。→ **这是 Q-03 的正面素材，见 §4。**

3. **"相邻命中区隔离"在本仓库没有形成规则：伪元素扩边无隔离声明，存在可证明的相邻重叠风险。**
   `[role=switch]::after` 上下各扩 12px（`M/:940-945`），而上游开关本体只有 36×20（`dsh` `ui-primitives` 见 `ref/dsh-base.md` §0 第 10 条），扩后命中高 20+24=44px；相邻设置行的命中盒若从 0 间距开始（本插件把设置行 `min-height` 抬到 44px，`M/:851-855`），则两行扩边各侵入 12px、**重叠带 24px**。代码里没有任何 `z-index`/`pointer-events`/间距补偿来防这一点（`M/:929-945` 整块只有 `position:relative` + `::after`）。
   反例教材是字号 stepper：两个箭头中心距 14px 时「想调大却触发调小」（`M/:802-808`、`R1/:54-57`）。**结论：扩边必须配隔离带，否则扩边就是新的误触源。**

4. **手势面极窄：只有"点"，没有任何侧滑/长按/双击/下拉手势；返回手势在两端都未实现。**
   mobile-adapt 的唯一触摸监听在统计面板的到边锁（`M/:1411-1440`：wheel/touchstart/touchmove），抽屉**没有** swipe 手势——只有汉堡（`M/:1345`）、遮罩（`M/:1350`）、Esc（`M/:1302-1306`）、导航后自动收起（`M/:1310-1321`）四种关闭路径。**`R1/:6` 声称 "sidebar becomes a swipe drawer" 与代码矛盾（E1 不成立）**。Android 壳的返回键只判 `WebView.canGoBack()`（`A/:437-447`），**没有「先关抽屉/先退键盘/先退浮层」的仲裁**；DSH 桌面本身也没有返回栈（`ref/dsh-base.md` §0 第 6 条）。→ G-04/Q-03 之外，**G-返回必须由 myco 新建**（§6 Q-MOB-04）。

5. **滚动到边仲裁有两套实现：声明式 `overscroll-behavior: contain`（多数面板）+ 命令式 `stopPropagation`（统计面板），后者是 G-04 最接近的 E0 实现，但只能挡传播不能挡默认滚动。**
   抽屉 `overscroll-behavior:contain` + `touch-action:pan-y`（`M/:285-287`）；设置内容区同（`M/:794-795`）；轨迹详情正文同（`M/:1079-1081`）；设置导航横向 `overscroll-behavior-x:contain` + `scroll-snap-type:x proximity`（`M/:749-751`）。
   统计面板是手写锁：到顶继续上滚/到底继续下滚时 `stopPropagation()`（`M/:1417-1430`），但监听器是 `{passive:true}`（`M/:1432-1434`）→ 只能阻止事件冒泡、不能 `preventDefault`；注释解释了"旧实现只看 `deltaY`，touchmove 恒 undefined 导致锁从未生效"（`M/:1403-1410`）。→ myco 若照此抄，必须补 `preventDefault`（Compose 侧等价于消费手势）才满足 G-04。

6. **键盘与安全区：mobile-adapt 写了完整的 `dvh + env(safe-area-inset-*) + visualViewport` 方案，但桌面 DSH 的 `<meta viewport>` 没有 `viewport-fit=cover`，`env()` 在 DSH 页面里恒为 0 → 这套安全区规则实际空转。**
   mobile-adapt：`html,body{height:100dvh}`（`M/:199-204`）、抽屉 `padding-top/bottom:env(safe-area-inset-top/bottom)`（`M/:277-278`）、汉堡 `top:max(10px,env(safe-area-inset-top)); left:max(12px,env(safe-area-inset-left))`（`M/:316-317`）、输入区 `padding-bottom:env(safe-area-inset-bottom)`（`M/:503-505`）、统计面板按 `visualViewport.height` 重算位置与 max-height（`M/:1443-1465`）、iOS 输入框 `font-size:max(16px,1em)` 防自动放大（`M/:1104-1116`）。
   反证：`dsh apps/web/index.html:5` 是 `<meta name="viewport" content="width=device-width, initial-scale=1" />`，**无 `viewport-fit=cover`**，全仓库 `packages/client` + `apps/web` 无一处 `safe-area-inset`（本次 grep 0 命中）→ 手机浏览器/WebView 不会给 `env()` 非零值。**这属于"写了但没生效"，修正要么改 DSH 入口 meta（改上游），要么由宿主注入（§6 Q-MOB-01）。**
   Android 壳则完全不做安全区/键盘处理：无 `windowSoftInputMode`（`X/:28`）、无 `WindowInsets`（`A/:283-285`）、无 visualViewport 桥；靠"非 edge-to-edge"让系统栏自己留边（`X/:7,26`，targetSdk 28）。

7. **布局压缩是"整块换形态"，不是"缩小间距"：三栏→单栏、侧栏→抽屉、右栏→全屏浮层、设置→全屏、轨迹详情→悬浮卡、上游 hover 控件→强制常显。**
   三栏网格改 `0 minmax(0,1fr) 0`（`M/:225-235`）；侧栏 fixed 抽屉、`width:min(320px,86vw)`（`M/:268-292`）；右栏 fixed 全屏浮层 `100vw/100dvh, z-index:70`（`M/:241-264`）；设置面板 fixed `inset:0`（`M/:726-737`）；轨迹详情 `bottom:calc(composer+8px); height:min(52vh,460px)`（`M/:1035-1056`）；上游 0.2.1 把字号箭头改成 hover 才显现，窄屏强制 `opacity:1`（`M/:819-827`）。断点取 **767px**（`M/:198`），768–1024px 明确"交给上游 `SIDEBAR_AUTO_COLLAPSE=1024`"（`M/:187-197`；桌面值 `dsh packages/client/ui-layout/src/columns.ts:23`）。

8. **触屏专属处理清单（可整块抄）：** 去 tap 高亮 + `touch-action:manipulation` 消 300ms 双击缩放（`M/:212-219`），滚动惯性 `-webkit-overflow-scrolling:touch`（`M/:287,636,791,1079`），`prefers-reduced-motion` 全量关闭过渡/动画（`M/:1091-1101`），`-webkit-text-size-adjust:100%` 防横竖屏自动放大（`M/:206-210`）。**没有 overscroll 弹跳控制以外的"过度滚动"视觉处理**（如拉伸/回弹自绘）。

9. **mobile-android 的壳层契约（myco 真正需要对照的部分）：外链一律交系统浏览器（OAuth 回跳后刷新页面）、`window.open` 捕获、返回键=WebView 历史、更新/错误用原生 3 按钮/1 按钮 AlertDialog；而文件选择、下载、剪贴板、长按系统菜单、深链、全屏/沉浸式、键盘避让全部"未见实现"。**
   外链：`A/:319-354,406-434`；返回：`A/:437-447`；更新对话框：`A/:1211-1237`（3 按钮）、`A/:1420-1438`（不可取消进度）、`A/:1461-1473`（失败单按钮）；**文件选择器**无 `onShowFileChooser`（`A/:241` 后无 override）、**下载**无 `setDownloadListener`（`A/:204` 后无）、**剪贴板**无 JS 桥（`A/:281` 只暴露 `openExternal`）、**长按菜单**未接管（无 `OnLongClickListener`/`setOnCreateContextMenuListener`）、**深链**无 `VIEW` intent-filter（`X/:33-36`）、**全屏/沉浸式**无 `SYSTEM_UI_FLAG`（`A/:145` 只有 `KEEP_SCREEN_ON`）、**键盘避让**无策略（`X/:28`）。

10. **可信度提醒：两仓库都有"文档声称 > 代码实现"的偏差，本路一律以 E0 行为准。**
    mobile-adapt：`R1/:6` "swipe drawer"（代码无 swipe）、`R1/:45` "刘海/底部安全区适配"（DSH 页面无 `viewport-fit` → 空转，见第 6 条）、`R1/:63-82` 触摸清单里多处把 CSS `min-height` 说成"热区"（视觉确实变高，不是透明扩边）。
    mobile-android：`D2/:47,225` 自述"未在真机验证 WebView 表现"（E1 自我披露）；`D2/:17` 界面=原生 Web UI，所以它**不是**给手机做过 UI 适配的实现，不能当"手机版长什么样"的正面样本，只能当**壳层行为样本**。

---

## 1. 两个仓库的定位与可信度

### 1.1 `dsh-client-ui-mobile-adapt`（15 文件）—— **Web 端移动 UI 适配插件**

| 项 | 事实 | 依据 |
|---|---|---|
| 形态 | DSH 客户端插件（`window.__ModuleLoader__.load` 单文件 bundle），另可作 profile bundle（`cordis.patch.yml` insert 一行） | `M/:1-5`、`cordis.patch.yml:1-6`、`package.json:23-35` |
| 生效范围 | **仅 `max-width: 767px`**；≥768px 承诺"分毫不动"（桌面端 CSS 基态 `display:none`） | `M/:174-197`、`R1/:22-26` |
| 界面代码量 | `lib/client.js` 1694 行：内联 CSS 模板串 `:169-1128`、格式化函数 `:1181-1222`、`MobileNav` 组件 `:1269-1353`、`StatsDock` 组件 `:1388-1655`、注册 `:1661-1687` | `M/` 全文 |
| 它改了什么 | ① 布局压缩（§0 第 7 条）；② 触摸命中区抬到 44px（`M/:798-970`）；③ 两个**新增**交互面：汉堡+遮罩+抽屉（`shell.overlay`）、底部统计胶囊+报表（`conversation.composer.dock`）；④ 面板形态（设置全屏、右栏浮层、轨迹详情浮卡）；⑤ iOS 视口/字号/安全区 | `M/:1661-1687` 槽位注册 |
| 它**没**改什么 | 聊天内容区：思考行 24px 命中区、消息操作条 28×28、过程组/工具卡、轮次导航——**全部未触及**（全文无 `ReasoningRow`/`MessageIconActions`/`TurnNavigator`/`ChatGroup` 选择器） | `M/` 全文 grep；对照 `ref/dsh-chat.md` §0 第 6/7 条 |
| 成熟度 | **较高**：1694 行带完整"漂移登记"（`M/:56-168` 记录 4 代 DSH 的类名漂移与双代匹配）、`ensureCssLast()` 用 MutationObserver 持续把插件 `<style>` 钉在 `<head>` 末尾（`M/:1129-1165`）、`StatsBoundary` 错误边界（`M/:1359-1373`）、`aria-expanded/aria-controls`、焦点环、Esc、outside-click、`prefers-reduced-motion`；另有 Playwright 回归脚本 `tools/*.py`（不属 UI 分母） | `M/` 全文、`R1/:334-360` |
| 主要风险 | 选择器大量依赖**上游哈希类名**（`pI_x6G_`/`wSkVaW_`/`bVCLcG_` 等），DSH 一升级就可能整块失配；作者用"语义锚优先 + 双代匹配"降低风险，但 `R1/:317-325` 自认 `0.1.7-alpha` 等版本未完整核对 | `M/:56-168`、`R1/:283-325` |
| 证据级别分布 | CSS/JS 行为 = E0（可逐行核）；"为什么这么改"多为源码注释 E1（example issue #2 根因解释 `M/:158-168,802-808`）；`R1` 的功能宣称 E1 | 见 §0 第 10 条 |

### 1.2 `dsh-mobile-android`（46 文件 / 7.5 MB）—— **Android 宿主壳，不是 UI 适配**

| 项 | 事实 | 依据 |
|---|---|---|
| 形态 | 自写 Android App：解压内置 Node 运行时 + DSH 内核 → 启动 `dsh --profile web --patch android-overlay.yml` → 探测端口 → WebView 打开带 token 的本地 URL | `A/:921-1018,1039-1101`、`D2/:20` |
| 界面 | **DSH 原生 Web UI**（文档明说）+ 一个自写的启动诊断日志台 `console.html`；除此之外没有 UI 适配层 | `D2/:17-18`、`A/:181-296`（不注入 CSS）、`C/:1-125` |
| 代码构成（46 文件里 UI 相关的只有 3 个） | UI：`MainActivity.java`(1630 行，WebView 宿主一半是运行时/更新流程)、`console.html`(125 行)、`AndroidManifest.xml`(54 行)；其余 = AndroidBridge(472)/ShizukuShell(350)/UpdateChecker(354)/UpdateManager(421) 是系统工具箱与热更新；`assemble-runtime.mjs`/`patches.mjs`/`update-runner.mjs`/`elf-*.mjs`/`build-apk.ps1` 是构建与热更新；`android-cli.js`/`android-mcp-server.js` 是给模型用的 MCP/CLI 工具箱 | `README.md:19-30` 目录表、`D2/:13-19` |
| 平台补丁（≠UI） | 8 类 Android 平台补丁：shim/flock/link/fsync/sharp WASM/mcp 图像错误详情/工具箱文件——全部是**文件系统与原生模块**适配，与界面无关 | `mobile-updater/patches.mjs:1-19,91-139`、`D2/:241` |
| 成熟度 | **实验性**：作者自述"未在大量真机上验证"、无真机、WebView 表现未验证；`targetSdk=28` 为绕 SELinux exec 限制、不能上架商店 | `R2/:5,102-104、D2/:225`、`D2/:47,223-227` |
| 对 myco 的价值 | 不是"手机 UI 长什么样"的参考，而是**壳层行为契约**参考：返回键、外链/OAuth、原生对话框、通知、窗口/键盘/安全区缺口（§0 第 9 条） | §3 中 `base.layout.host.*` 节点 |

### 1.3 分母方法（为什么 android 是手工枚举的）

- 官方 `ui_audit.py scan` 的语言集是 `ts,tsx,js,jsx,kts,kt,css`（`tools/ui-audit/ui_audit.py:252`），正则全部面向 JSX/CSS（`:37-66`）。
- mobile-adapt 命中 **41** 条（`files=2`：`lib/client.js`、`lib/index.js`）；其界面全在 `client.js` 内联 CSS 模板串里，所以"CSS 在 .js 里"这件事反而被 `aria.*`/`container.scroll`/`jsx.*` 规则捕获到了。
- mobile-android 默认 scan **`files=6` / findings=0**：`.java`/`.html`/`.xml` 不在语言集内。若只跑官方 scan，分母=0、门禁"uncovered=0"会**假通过**，审计等于没做。
  因此本次对 android 采用：**官方 scan（0 行）保留 + Shell 层交互点人工穷举**，枚举规则固定为「WebView 装配段 / URL 分发 / 返回键 / 原生对话框 / 通知 / Manifest 窗口配置 / console.html 交互元素」，逐点写 `kind,path,line` 进 `_ws/out/findings-mobile-android.csv`，再由 `check` 对账。
  手工枚举脚本 `_ws/out/gen_mobile_audit.py`（只写 `_ws/out` 与本路 3 份产物）；`_ws/out/findings-mobile-android-raw-scan.csv` 保留了官方 scan 的原始 0 行输出。
- 两个仓库都**没有独立 `.css` 文件** → `css.cursor/focus/sticky/contain/overflow` 类目在官方 scan 里分母为 **0**（CSS 在 `client.js` 模板串内，其结构性含义由节点字段 2/3/8 与 §2 表承载，见 §5）。

---

## 2. 桌面 → 手机 差异清单

> 列含义：**桌面做法**（基准，引 `ref/dsh-*.md` 或 `dsh` 行）→ **手机做法**（两仓库 E0 行）→ **改动理由**（源码注释/文档）→ **是否可抄**（myco = Compose 原生 App 时是否需要、以及照抄的风险）。
> 「来源」列：`AD` = mobile-adapt（Web 插件）；`AN` = mobile-android（Android 壳）。

| # | 元素 | 桌面做法（基准） | 手机做法（仓库:行） | 改动理由（源码/注释） | 是否可抄（给 myco） | 来源 |
|---|---|---|---|---|---|---|
| 1 | 整体布局 | 三列 Grid `sidebar 280px \| center \| rightbar`，`<1024px` 侧栏自动收成 56px 图标栏（`dsh packages/client/ui-layout/src/columns.ts:19-23`、`ref/dsh-base.md` §0 第 6 条） | 网格改 `0 minmax(0,1fr) 0`，中栏占满；断点 767px（`M/:225-235,198`）；768–1024 明确交回上游 | 注释：768 起交给官方 56px 窄栏，"只有真正的手机宽度才收敛成单栏 + 抽屉"；实测 767→768 过渡无缝（`M/:187-197`） | **可抄结论**：单栏 + 抽屉；**不可抄实现**（myco 无 CSS Grid 三列） | AD |
| 2 | 侧边栏 | 常驻/自动收窄，占一列布局流（`dsh columns.ts:52-58`） | `position:fixed; left:-110%`，打开时 `left:0`，`width:min(320px,86vw)`，`max-width:calc(100vw-56px)`，右侧 16px 圆角 + 阴影（`M/:268-292`） | 注释：不能用 transform 做动画（会把内部 fixed 面板裁到抽屉里），改 `left` 位移（`M/:266-267`）；transform 还会让 fixed 后代相对抽屉定位 | 可抄结论（抽屉 86vw 上限 + 右侧圆角 + 阴影）；myco 用 Compose `ModalNavigationDrawer`/自绘抽屉时注意"不要用 translate 导致 fixed 子层错位" | AD |
| 3 | 抽屉开合入口 | 无（桌面点侧栏图标） | 汉堡 44×44（fixed，左上 `max(10/12px, safe-area)`，z41）+ 遮罩点击 + Esc + 导航后自动收（`M/:313-343,1347-1351,1302-1306,1310-1321`） | 注释：抽屉是覆盖式导航，点完会话/新建/设置应自动收起，否则"点完还得再点一次遮罩"（`M/:1307-1309`） | **可抄**（四种关闭路径 + 500ms 一次性守卫 `M/:1287-1293`） | AD |
| 4 | 右侧栏（文件/终端/Start） | 桌面是一条可拖拽轨道，`computeColumns` 在可用宽 <`RIGHTBAR_MIN`(300) 时直接把右栏轨道去掉（`dsh columns.ts:52-58`）→ 手机宽度下**功能不可达** | 改为 fixed 全屏浮层（`inset:0; 100vw/100dvh; z-index:70`），平时 `pointer-events:none`，仅 `[data-sidebar-right-open=true]` 时接事件；宽度手柄隐藏（`M/:237-264,262-264`） | 注释：上游 0.2.x 右栏打开时本就渲染 fullscreen，这里只是把它从 0 宽轨道解放出来；半宽面板不能用整屏透明层挡住聊天区（`M/:237-240,256-257`）；`R1/:93` 记录"右侧栏被整体隐藏→功能不可达" | **可抄**（全屏浮层 + 打开才接事件）；"桌面右栏在手机宽度自动消失"这条桌面事实非常适合做 myco 的反证 | AD |
| 5 | 设置面板 | 桌面居中浮层（默认宽 720px 级）+ 纵向导航 | `position:fixed; inset:0; 100dvh`、圆角 0、纵向 flex；`mask` 直接 `display:none`（全屏不透明，省掉 backdrop 模糊）（`M/:719-737`） | 注释：全屏面板本身不透明，遮罩只会白白多一层 backdrop-filter（`M/:722-725`）；`R1/:39` | 可抄（全屏面板 + 去掉叠层面具） | AD |
| 6 | 设置导航 | 桌面纵向列表（标题 + 分组） | 单行横滑：`flex-direction:row; overflow-x:auto; scroll-snap-type:x proximity; scrollbar-width:none`，`navCell min-height:44px`、标题隐藏、图标 16px（`M/:738-786`） | 注释无；效果是"顶部 tab 条"、"内容区获得整屏高度"（`R1/:39`） | 可抄（横滑 tab + snap + 隐藏滚动条）；注意与 Q-03 的横向命中区相邻问题 | AD |
| 7 | 字号调节 stepper | 桌面 `absolute` 列容器（18×26）内两个 17×12 上下箭头，中心距 14px；0.2.1 起默认 `opacity:0`，仅 hover/focus 显形 | `position:static` 横向排列，两个 44×44 并排、`gap:2px`、强制 `opacity:1`；`expander` 24→44；行/控件 `min-height:44` 且允许换行（`M/:802-865`） | **最完整的一段根因注释**：手机上"想调大却触发调小"，反复失败后被用户描述为"无法滑动导致无法保存配置"（issue #2）；箭头 0.2.1 起 hover-only，触屏等于隐藏（`M/:158-168,802-808`；`R1/:47-58`） | **可抄结论**（hit-target 相邻 14px = 必然误触 → 必须重排而不是扩边）；**不可抄视觉方案**（静态横排会改变字号行的视觉形态，需拍板，见 §6 Q-MOB-11） | AD |
| 8 | 开关/复选框 | 开关 36×20、复选框更小（`ref/dsh-base.md` §0 第 10 条） | 视觉尺寸不变，`::after{inset:-12px -8px}` 透明扩边到 ≥44px（`M/:934-945`） | 注释：开关/复选框本体只有 20~36px，用伪元素把可点区域扩到 44px 以上，视觉尺寸保持不变（设置的标签行本身也可以点）（`M/:934-935`） | **可抄手法，但必须补隔离**：`-12px` 上下扩边在行距 <68px 时会侵入邻行（本仓库自己无隔离，见 §0 第 3 条） | AD |
| 9 | 菜单/命令面板/弹层 | 桌面固定宽 240–320px 下拉 | `max-width:calc(100vw-16px)`；菜单 `width:min(240px,100vw-16px); max-height:min(60dvh,100dvh-120px)`；卡片 `max-height:min(50dvh,320px)`；菜单行 `min-height:44`；搜索清除按钮 44×44（`M/:507-538`） | 注释：弹层不超出视口（`M/:507`） | 可抄（视口减 16px + dvh 上限 + 行 44） | AD |
| 10 | 输入工具行（附加/权限/模型/上下文/发送） | 桌面允许多行、触发器不定宽 | 强制单行 `flex-wrap:nowrap`；gap 8/6/4；触发器 `max-width:112px + min-height:44`、标签字号 12px；加号/发送圆钮 44×44；工具行按钮/select 全部 `min-height:44`（`M/:458-505`） | 注释：压缩间距与触发器宽度，保持一行（`M/:458`）；0.2.1 加号/发送仍 28/34px，手机上很难点中（`M/:494`） | **可抄**（单行 + 触发器限宽 + 44 下限）；"发送钮 44"对 myco 输入区直接适用 | AD |
| 11 | 会话头部（标题/操作/tab） | 桌面单行、tab 25px 高、右侧图标 28px、左内边距常规 | 汉堡让位：`padding:10px 12px 0 64px`（=12+44+8）；标题行可换行；操作/工具按钮 44×44；`tabs` 以视口中线居中（负 margin 52px）且 tab 44px 高；面包屑 `max-width:170px; min-height:44px; padding:12px 0` 保住 ellipsis；右栏入口保留并 44（`M/:375-443`） | 注释逐条说明：汉堡 fixed 44 故左内边距 64；tab 上游只有 25px 高，抬手容易点到旁边；面包屑用上下 padding 把 20px 行高撑到 44（`M/:376,434,403-406`） | **可抄**（fixed 悬浮按钮与内容让位公式；tab/面包屑的 padding 撑高手法） | AD |
| 12 | 底部统计行 | 桌面上游统计行 StatsPills（`M/:99-104,545-554` 提到上游 `.bOPqQW_root` / 0.2.1 拆成 `.bOPqQW_anchor` 两个胶囊；本路未独立取证其行高） | 手机端用插件自有统计胶囊接管（`min-height:44`、padding 10/14），并把上游统计行隐藏（双代类名 + `:has()` 兜底，插件异常时官方行自动回退）（`M/:545-619`、`R1/:105-118`） | 注释：手机端本插件胶囊是唯一统计面；0.2.1 上游统计行拆成两个 anchor，双代匹配（`M/:99-104,545-554`） | 可抄"胶囊 + 展开报表"形态与"只在自有组件渲染时隐藏上游"的兜底策略 | AD |
| 13 | 统计报表 | 桌面无此面板（DSH 统计是行内 pill） | fixed 面板贴胶囊上沿（JS 测量 `pill.getBoundingClientRect().top` 写 inline `bottom/maxHeight`），`max-height:min(52dvh,…)`，标题 sticky，`role=dialog aria-modal`，Esc/点外部关闭，Tab 焦点环，关闭后焦点还给胶囊，`visualViewport` 变化重测（`M/:620-664,1443-1465,1467-1517`） | 注释：写死 `bottom:216px` 与 composer 实际高度无关，键盘/附件/队列变化后会悬空或压住输入框（`R1/:96`；`M/:1441-1442`） | **强可抄**（浮层贴锚点 + 键盘重测 + 焦点归还）；⚠️ `role=dialog aria-modal=true` 但无遮罩层，语义与视觉不一致（§6 Q-MOB-13） | AD |
| 14 | 轨迹详情面板 | 桌面右侧栏内嵌/并排 | fixed 浮卡：`left/right:12px; bottom:calc(var(--dsh-composer-height)+8px); height:min(52vh,460px); z-index:1000`；缩放柄隐藏；正文独立滚动（`overscroll-behavior:contain`）（`M/:1000-1088`） | 注释：抬到输入卡上方，用上游实时变量 `--dsh-composer-height` 而不是硬编码 130px（`M/:1039-1040`） | **可抄**（用 composer 实时高度做浮层锚点，而不是魔数） | AD |
| 15 | hover 才出现的控件 | 桌面 hover 显现（字号箭头 `opacity:0`；消息操作条仅 `@media (hover:hover)` 且 hover/focus 才显，触摸设备常驻——DSH 已自带，`ref/dsh-chat.md` §0 第 7 条） | 窄屏**强制常显**字号箭头 `opacity:1`（`M/:819-827`）；插件的汉堡/胶囊/关闭按钮本身就是常驻 | 注释：0.2.1 起箭头默认 `opacity:0` 只在 hover/focus 显形，触屏上等于隐藏（`M/:112-115,806`） | **可抄规则**：myco touch 端一律常驻可见；但**不要**把 DSH 已处理的 media-hover 逻辑再抄一遍 | AD |
| 16 | 消息操作条/思考行/过程组 | 28×28 图标动作、折叠行 24px+delta 命中（`ref/dsh-chat.md` §0 #7、`ref/dsh-base.md` §2.1） | **未见实现**：mobile-adapt 没有任何针对聊天内容区（思考行/动作条/过程组/工具卡/轮次导航）的规则；android 更是原样桌面 UI | —（无改动 = 原样 24/28px 命中区留在手机上） | **不可从这两仓库抄**；Q-03 对"24px 思考行"没有现成答案（§4） | AD/AN |
| 17 | 返回键 / 返回手势 | 桌面无返回栈（`ref/dsh-base.md` §0 第 6 条：`activePanelId` 覆盖式换面板，无历史） | Android 壳：`onBackPressed` → `web.canGoBack()` 则 `goBack()`，否则 `super`（退出 App）；无抽屉/键盘/浮层仲裁（`A/:437-447`）；Web 层无返回手势（`M/:1302-1306` 只有 Esc） | 注释：优先在 WebView 内后退，避免一按就退出 App（`A/:438`） | **不可抄**（顺序不完整）；需 myco 自定优先级（§6 Q-MOB-04） | AN |
| 18 | 外链 / OAuth / window.open | 桌面点链接：应用内或系统浏览器取决于壳 | 本机回环留在 WebView，其它 http(s) 交系统浏览器；`window.open/target=_blank` 用隐藏 WebView 捕获 URL 再交浏览器；OAuth URL 置 `refreshOnResume`，从浏览器回来自动刷新 DSH 页面同步登录态；JS 侧再兜一层（click 捕获 + `window.open` 改写）（`A/:319-354,406-434,449-465`） | 注释：登录是 OAuth，授权页必须在正常浏览器打开，完成后回跳 `http://127.0.0.1:<port>/oauth/callback`（`A/:324-331`） | **强可抄**（myco 同为 Android：外链=系统浏览器 + OAuth 回跳后刷新；"隐藏 WebView 捕获 window.open"可换成 `shouldOverrideUrlLoading`/Custom Tabs） | AN |
| 19 | 文件选择 / 下载 | 桌面直接读本地文件 | **未见实现**：无 `onShowFileChooser`（`A/:241`）、无 `setDownloadListener`（`A/:204`）→ `<input type=file>` 与下载在 WebView 里不可用 | — | **不可抄**；myco 必须自己接 `ActivityResultContracts.OpenDocument`/下载器（§6 Q-MOB-07） | AN |
| 20 | 剪贴板 | 桌面按需 | 无 JS 桥（`A/:281` 只暴露 `openExternal`）；日志台用 `textarea + document.execCommand('copy')` 旧 API（`C/:108-119`） | 注释：`copyLog` 是日志台的复制按钮实现（`C/:55`） | **不可抄**；myco 需用系统 ClipboardManager/Compose 剪贴板（§6 Q-MOB-07） | AN |
| 21 | 长按文本 / 系统上下文菜单 | 桌面右键/hover | 未接管：无 `setOnLongClickListener`、无 `setOnCreateContextMenuListener`（`A/:180-296` 全文）→ WebView 默认长按文本选择/上下文菜单照旧 | — | **不可抄**；需决定"保留系统文本选择 or 自定义"（§6 Q-MOB-08） | AN |
| 22 | 键盘弹出 | 桌面无软键盘 | **未见实现**：无 `windowSoftInputMode`（`X/:28`）、无 `WindowInsets`（`A/:283-285`）、无 `visualViewport` 桥；mobile-adapt 侧只用 `100dvh` + `visualViewport` 重测统计面板（`M/:1443-1465`），输入区本身没做键盘避让 | — | **不可抄**；myco 必须显式 `imePadding()`（§6 Q-MOB-02） | AN/AD |
| 23 | 状态栏 / 挖孔 / 手势条 | 桌面无 | Android 壳：**不做 edge-to-edge**（`X/:7,26`），靠系统窗口默认留边；日志台自己用 `viewport-fit=cover + env(safe-area-inset-bottom)`（`C/:5,33`），DSH 页面没有 → 两页行为不一致；mobile-adapt 的 env() 规则因 DSH 页面无 `viewport-fit` 而空转（§0 第 6 条） | — | **部分可抄**：日志台的 `safe-area` footer 写法；DSH 页面那条不可抄（前提不成立） | AN/AD |
| 24 | 屏幕常亮 / 前台保活 | 桌面无 | `FLAG_KEEP_SCREEN_ON`（`A/:145`）、前台服务常驻通知（`A/:1080`、`S/:11-38`） | 注释：解压/更新耗时长，"请保持屏幕常亮"（`C/:59`、`A/:1446`） | **可抄**（myco 首启/长任务同样需要） | AN |
| 25 | 触摸反馈 / 滚动惯性 / 动画降级 | 桌面 hover 态 + 鼠标 | 去 tap 高亮 + `touch-action:manipulation`（`M/:212-219`）；`:active` 底色/缩放替代 ripple（`M/:333-336,594-597,680-682,841-843`）；`-webkit-overflow-scrolling:touch`；`prefers-reduced-motion` 全关（`M/:1091-1101`） | 注释：去掉移动端点击蓝色高亮闪屏、"按钮自身保留 :active 反馈"；`touch-action:manipulation` 同时消除 300ms 双击缩放延迟（`M/:212-214`） | **可抄**（Compose：`Modifier.indication` 自定义/关闭 ripple 需拍板；300ms 延迟在原生无此问题） | AD |
| 26 | 更新/错误提示 | 桌面用 Web 内 Toast/对话框 | Android 壳用原生 `AlertDialog`（3 按钮更新提示 / 不可取消进度 / 单按钮失败）+ `Toast`（`A/:1211-1237,1420-1473,351,395,1362`） | 注释：更新期间会重启内核，对话数据保留；进度框不可取消（`A/:1224-1225,1420-1429`） | **可抄形态**（阻塞流程用不可取消进度 + 失败可回滚文案） | AN |

---
## 3. 节点事实表（13 字段）

> 字段编号严格对应 `schema-node.md` §1。**本路节点分两类**：
> - `AD`（前缀 `base.layout.*` / `settings.*` / `composer.*` / `chat.*` / `trajectory.*` / `base.primitives.*`）= mobile-adapt 实际改造出来的移动端节点；
> - `AN`（前缀 `base.layout.host.*`）= mobile-android 的 **Android 宿主壳行为节点**（不是界面，是 myco 作为原生 App 必须回答的壳层契约）。
>
> 节点 ↔ `facts.csv` 行数对照（`node_id` 与两份 facts 一一对应；标 `CSS-only` 的节点没有 JSX 交互点，
> 其依据全在 CSS 行，官方 scan 不产 `jsx.*` 行——这是 scan 的语言集限制，不是漏审，见 §1.3/§5）：

| 节点 | facts 行 | 节点 | facts 行 |
|---|---|---|---|
| `base.layout.mobile-nav.hamburger` | 3 | `composer.stats.pill` | 4 |
| `base.layout.mobile-nav.scrim` | 3 | `composer.stats.close` | 1 |
| `base.layout.mobile-nav.drawer` | 3 | `composer.stats.panel` | 1 |
| `base.layout.mobile-nav.auto-close` | 3 | `composer.stats.panel.edge-lock` | 9 |
| `base.layout.right-sidebar.layer` | CSS-only | `composer.tools.row` | CSS-only |
| `base.layout.touch-feedback` | 1 | `chat.header.tabs` | CSS-only |
| `settings.shell.fullscreen-panel` | 1 | `chat.header.crumb` | CSS-only |
| `settings.shell.nav-rail` | 3 | `trajectory.details.panel` | 1 |
| `settings.general.font-stepper` | CSS-only | `base.primitives.touch-floor.dialog-controls` | 4 |
| `settings.general.switch` | 4 | `base.primitives.popover.mobile-constraint` | CSS-only |
| `base.layout.host.webview` | 10 | `base.layout.host.console` | 13 |
| `base.layout.host.back` | 4 | `base.layout.host.update-dialogs` | 8 |
| `base.layout.host.external-link` | 14 | `base.layout.host.toast` | 3 |
| `base.layout.host.navigation` | 3 | `base.layout.host.window` | 7 |
| `base.layout.host.web-error` | 6 | `base.layout.host.ime` | 1 |
| `base.layout.host.missing-native` | 3 | `base.layout.host.notification` | 3 |

### 3.1 mobile-adapt 节点（AD）

#### `base.layout.mobile-nav.hamburger` — 新加的 44×44 浮动汉堡按钮

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.mobile-nav.hamburger`。所属 `base.layout` → 移动壳 overlay。父：React 树 `div.dsh-mobile-nav`，渲染进 `shell.overlay` 槽（`M/:1666-1674`，priority -1）。出现条件：`@media (max-width:767px)` 内 `display:grid`（`M/:313` 处于 `M/:198` 的 media 块内），桌面基态 `display:none`（`M/:175-178`）；`html:has([role=dialog][aria-modal=true])` 时隐藏（`M/:370-373`）。同一时刻 1 个。 |
| 2 | 网格与定位 | `position:fixed`；`top:max(10px, env(safe-area-inset-top))`、`left:max(12px, env(safe-area-inset-left))`；44×44；`padding:0`；圆角 12；1px `--dsw-alias-border-l2`；`z-index:41`；`place-items:center`（`M/:313-332`）。与标题行的契约：会话头部左内边距固定 `64px`＝12(左)+44(按钮)+8(间隙)（`M/:376-379`）。 |
| 3 | 命中区 | **矩形 = 44×44 盒本身**（`width:44; height:44; padding:0`，`M/:320-322`），无扩边、无伪元素；比桌面任何一个图标按钮（16/24/28px）大 3–7 倍面积。与相邻命中区最小间隔：headerActions `gap:4px`、headerUtilities `gap:4px`+`margin-left:8px`（`M/:385-391`）；汉堡与右侧头部动作之间由「左 64 / 右 12」的内容盒保证不重叠（`M/:376-379`）。 |
| 4 | 手势 | 单击 = 切抽屉（`M/:1345` `onClick: onToggle`）。长按/双击/滑动：**未见实现**（组件 `M/:1269-1353` 无指针/触摸监听；全局只有 keydown+click 捕获）。与页面滚动仲裁：fixed 定位不在滚动容器内；全局 `touch-action:manipulation` 消 300ms 双击缩放（`M/:212-219`）。 |
| 5 | 行为 | `onToggle → props.toggle → ctx.layout.toggleSidebar()`（`M/:1271-1273,1663-1665`）；单次点击幂等（再点关闭）。关闭路径还有遮罩点击、Esc、导航后自动收（`M/:1302-1321`）——四条路径由 500ms `closeGuard` 保证同帧只 toggle 一次（`M/:1287-1293`）。无撤销概念。 |
| 6 | 视觉状态 | default：浮动底色 `--dsw-alias-button-floating-fill` + `--dsw-shadow-lv2`（`M/:325-327`）；pressed：`:active` 背景 hover 色 + `transform:scale(.94)`（`M/:333-336`）；focus：`:focus-visible` 2px 业务主色 outline offset 2（`M/:337-340`）；hover：**无**（触屏）；disabled：**未见实现**；模态存在：`display:none`（`M/:370-373`）。转场 `background-color .15s, transform .12s`（`M/:331`）。 |
| 7 | 布局占位与展开形态 | overlay 渲染（fixed），**不参与布局流、不占位、不推挤任何兄弟**（G-01 同向）。不属于 G-03 三形态（不展开）。反证见字段 13。 |
| 8 | 滚动语义 | 不滚、不拦截页面滚动（fixed 悬浮，不在滚动容器内）。点击不穿透（`pointer-events:auto`，`M/:330`）。 |
| 9 | 动效 | 按下缩放 0.12s + 底色 0.15s（`M/:331`）；`prefers-reduced-motion:reduce` 时 `transition:none`（`M/:1091-1099`）。可中断：CSS transition 天然可中断。 |
| 10 | 边界态 | 空数据/无会话：不依赖数据，始终显示。横屏：判据只有宽度 ≤767，横屏 812×375 宽度 >767 → 汉堡消失、回到桌面三栏（`M/:198`）——⚠️ 手机横屏没有汉堡。大字号：图标固定 18px、盒固定 44px（`M/:1224-1235`），不随 `--dsh-content-font-size` 变。多语言：`aria-label = t("nav.open")`（`M/:1344`；zh「打开侧边栏」`M/:11`，en `M/:34`）。 |
| 11 | 无障碍 | `<button type="button">` + `aria-label`（`M/:1341-1345`）；SVG `aria-hidden="true"`（`M/:1229`）；焦点环自定义（`M/:337-340`）。**缺口：没有 `aria-expanded` 表达抽屉开合**（对照同插件统计胶囊 `aria-expanded={open}` `M/:1616`）→ 读屏无法知道抽屉状态。 |
| 12 | 依据 | `E0` `M/:313-343`（CSS）、`M/:1341-1346`（JSX）、`M/:1269-1353`（组件）、`M/:1666-1674`（槽位注册）、`M/:370-373`（模态隐藏）、`M/:198,175-178`（断点/桌面隐藏）、`M/:376-379`（头部让位公式）、`M/:331,337-340,1091-1099`（状态/动效/降级）。 |
| 13 | 反证 + 验收 | **反证**：若没有汉堡，单栏布局下侧栏抽屉的**唯一入口消失**——桌面靠 56px 图标栏换会话（`dsh columns.ts:19-23`），而插件已把网格侧栏列压成 0（`M/:227`），用户将无法切换会话；若把汉堡放进布局流：它会占一行并把标题推下去，违反 G-01（浮层不占位）、且头部高度随它变化。**验收**：`MOB-01` 手机 390/320px 下汉堡可见且点击开抽屉（真机手势）；`MOB-02` 桌面 1440px 下 `display:none`（截图比对）；`MOB-03` 汉堡 44×44 实测（量测截图）。 |

#### `base.layout.mobile-nav.scrim` — 点击外部关闭遮罩

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.mobile-nav.scrim`。同一 `MobileNav` 组件内的 `div.dsh-mobile-scrim`（`M/:1347-1351`）。**始终挂载在 DOM**，靠 opacity/visibility 显隐（`M/:345-357`），只在 ≤767px 生效。 |
| 2 | 网格与定位 | `position:fixed; inset:0; z-index:39`（比抽屉 40 低、比汉堡 41 低）；`display:block`（`M/:346-350`）。 |
| 3 | 命中区 | 命中区 = 整屏（减去被抽屉 z40 盖住的部分）。无扩边问题（满屏）。 |
| 4 | 手势 | 单击 = 关抽屉（`M/:1350` `onClick:onToggle`）。无滑动/长按。`touch-action:none`（`M/:355`）→ 遮罩上不产生任何滚动/手势。 |
| 5 | 行为 | 关闭：`toggleSidebar()`；关闭态 `pointer-events:none` 不拦截触摸（`M/:352-355`）；打开态 `pointer-events:auto`（`M/:360-365`）。 |
| 6 | 视觉状态 | default：`opacity:0; visibility:hidden`；打开：`opacity:1`，`background:rgb(0 0 0 / 35%)`；转场 opacity .22s（打开立刻可见、关闭延迟 .22s 再 hidden，`M/:356,360-366`）。 |
| 7 | 布局占位与展开形态 | fixed 浮层，**不占位**（G-01）。 |
| 8 | 滚动语义 | 不滚；`touch-action:none` 防穿透滚动。 |
| 9 | 动效 | opacity .22s ease；`visibility` 用 `transition: visibility 0s linear .22s` 实现关闭延迟隐藏（`M/:356`）；reduced-motion 关闭（`M/:1093`）。 |
| 10 | 边界态 | 模态（`[role=dialog][aria-modal=true]`）出现时连同汉堡一起隐藏（`M/:370-373`）；横屏 >767 不生效。 |
| 11 | 无障碍 | `aria-hidden="true"`（`M/:1349`）；**不可聚焦**（注释 `M/:1265` "the scrim is not focusable"）→ Esc 关闭由 window keydown 提供（`M/:1302-1306`），不是遮罩自身。 |
| 12 | 依据 | `E0` `M/:345-366`（CSS）、`M/:1347-1351`（JSX）、`M/:1302-1306`（Esc）。 |
| 13 | 反证 + 验收 | **反证**：若没有遮罩，抽屉打开后点抽屉外没有任何反馈（用户以为界面卡死），只能再点汉堡；若遮罩可聚焦/进 Tab 序，读屏会多出一个无意义焦点站。**验收**：`MOB-04` 点遮罩任意处关闭且不触发下层按钮（真机）；`MOB-05` 关闭态下方内容可点（真机，防 pointer-events 残留）。 |

#### `base.layout.mobile-nav.drawer` — 侧栏改固定抽屉

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.mobile-nav.drawer`。**不是新组件**：把上游侧栏容器 `.pI_x6G_sidebarCol` 及其内部 `hHd-Xa_root` 用窄屏 CSS 改成抽屉（`M/:268-299`）。出现条件 ≤767px；768–1024 交给上游 56px 图标栏（`M/:187-197`）。 |
| 2 | 网格与定位 | `position:fixed; top:0; bottom:0; left:-110%`（关闭）→ `left:0`（打开）；`width:min(320px,86vw)`；`max-width:calc(100vw-56px)`；右圆角 `0 16px 16px 0`；`padding-top/bottom:env(safe-area-inset-*)`；`box-sizing:border-box`；`z-index:40`（`M/:268-292`）。内部 `hHd-Xa_root { width:100%; min-height:100% }`（`M/:294-299`）。 |
| 3 | 命中区 | 抽屉内 `button`、`.hHd-Xa_newSession`、`.hHd-Xa_panelRow`、`.VOzbGW_trigger` 统一 `min-height:44px`，图标按钮 `min-width:44px`（`M/:301-310`）——**这是"把桌面 28–36px 导航行抬到 44"的直接证据**。 |
| 4 | 手势 | 抽屉内容：竖向滚动（`touch-action:pan-y`、`overscroll-behavior:contain`）。开合关闭：**只有点击类路径**（汉堡/遮罩/Esc/导航后自动收），**没有侧滑手势**（全文无 touch 监听作用于抽屉；`R1/:6` "swipe drawer" = E1 与代码矛盾）。 |
| 5 | 行为 | 打开条件 = 上游 frame 上无 `data-sidebar-collapsed` 或值不是 `true`（`M/:289-292`）；关闭 = `toggleSidebar()`。插件不持有抽屉状态（状态在上游 layout store）。幂等可撤销。 |
| 6 | 视觉状态 | 关闭：`left:-110%`（完全移出）+ 不能接事件（在屏外）；打开：`left:0` + 阴影 `4px 0 24px rgb(0 0 0/24%)` + 右侧 0.5px 边框 + `--dsw-specific-sidebar-fill` 背景；无 hover/pressed 特殊态（内部行由上游样式）。 |
| 7 | 布局占位与展开形态 | **脱离 Grid 布局流**：侧栏列宽被压成 0（`M/:225-229`，三列改 `0 minmax(0,1fr) 0`）→ 打开抽屉不挤压中栏，是覆盖式导航（不是 G-03 三形态之一，是壳层导航）。 |
| 8 | 滚动语义 | 抽屉自身 `overflow-y:auto` + `-webkit-overflow-scrolling:touch` + `overscroll-behavior:contain`（`M/:284-288`）→ 到边不把滚动链传给页面（G-04 的声明式实现）。 |
| 9 | 动效 | `left .28s cubic-bezier(0.22,1,0.36,1)`（`M/:283`）；`prefers-reduced-motion` 关闭（`M/:1092`）。注释明确"不能用 transform 做显隐动画（会裁 fixed 面板），改用 left"（`M/:266-267`）。 |
| 10 | 边界态 | 320px 窄机：`width:min(320px,86vw)`→275px，`max-width:calc(100vw-56px)`→264px（`M/:273-274`）；超长列表：抽屉内滚动；横屏 >767：不生效（回桌面）；多语言/大字号：行高 44 由 min-height 保证、文字由上游 ellipsis；RTL：`border-radius:0 16px 16px 0` 是物理属性，未见 RTL 处理（`M/:280`）。 |
| 11 | 无障碍 | 抽屉容器**无 `role=dialog`、无 `aria-modal`、无焦点陷阱**（`M/:1269-1353` 未见）→ Tab 可从抽屉跑回背景页面；关闭靠 Esc（`M/:1302-1306`）。这是与"覆盖式导航"语义不匹配的缺口（§6 Q-MOB-14）。 |
| 12 | 依据 | `E0` `M/:266-310`（CSS 抽屉+44 行）、`M/:1274-1339`（关闭逻辑）、`M/:1302-1306`（Esc）、`M/:1347-1351`（遮罩）。`E1` `R1/:33`（"左侧抽屉 left 位移动画"）、`R1/:6`（swipe 声称，**与代码矛盾**）。 |
| 13 | 反证 + 验收 | **反证**：若继续让侧栏占 Grid 一列，390px 屏上中栏只剩 ~334px 且汉堡与 56px 图标栏抢左上角（`M/:136-139` 的漂移记录就是这个问题的历史）；若用 `transform` 做抽屉动画，抽屉内部的 fixed 面板会以抽屉为包含块被裁剪（`M/:266-267` 注释）；若无 `overscroll-behavior:contain`，抽屉滑到底会带动背后会话一起滚（G-04 违反症状）。**验收**：`MOB-06` 抽屉打开后中栏不位移（截图比对）；`MOB-07` 抽屉内滑到底页面不动（真机手势）；`MOB-08` 旋转/横屏不出现半截抽屉（真机）。 |

#### `base.layout.mobile-nav.auto-close` — 导航后自动收抽屉（行为节点）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.mobile-nav.auto-close`。纯行为节点：文档级捕获监听 + `MutationObserver`，定义在 `MobileNav` 的 effect 里（`M/:1274-1339`）。只在 ≤767px 且抽屉打开时生效（`drawerOpen()` 前置判断，`M/:1275-1282`）。 |
| 2 | 网格与定位 | 无视觉盒（逻辑节点）。作用对象选择器：`.YDXeBa_sessionRow, [role=treeitem][aria-selected], .hHd-Xa_newSession, button[aria-label='New session'], .hHd-Xa_panelRow, .VOzbGW_trigger`（`M/:1315`）。 |
| 3 | 命中区 | 继承被点节点的命中区（抽屉内导航行已 44px，见 drawer 字段 3，`M/:301-310`）。不新增可点区域。 |
| 4 | 手势 | 单击（`document.addEventListener("click", onSidebarClick, true)` 捕获阶段，`M/:1333`）。无长按/滑动。 |
| 5 | 行为 | 命中导航类目标 → `scheduleClose()`（60ms 后 `closeDrawer()`，`M/:1295-1301,1320`）；行内"更多操作"按钮（`button/[role=button]` 且非导航目标本身）**不收起**（`M/:1317-1319`）；body 出现 `[role=dialog][aria-modal=true]` → 立即收（`M/:1325-1331`）；`closeGuardUntil=now+500ms` 防同帧多关闭路径竞争（`M/:1287-1293`）。幂等。 |
| 6 | 视觉状态 | 无（不绘任何视觉）。 |
| 7 | 布局占位与展开形态 | 无占位。 |
| 8 | 滚动语义 | 无。 |
| 9 | 动效 | 固定 60ms 延迟（让点击的 pressed 反馈先发生再收，`M/:1297-1300`）。 |
| 10 | 边界态 | ModalObserver 只观察 `document.body` 直接子节点（注释：避免流式消息不断变长时反复触发，`M/:1322-1324`）；导航目标若在 iframe/Shadow DOM 内则捕获不到（未见处理）；多语言不影响选择器（除 `aria-label='New session'` 硬编码英文，`M/:1315`——中文界面下"新建会话"若 aria-label 不是英文则漏判，⚠️ 实际上 `.hHd-Xa_newSession` 类名兜底）。 |
| 11 | 无障碍 | 不改变：导航后焦点仍留在被点元素（元素已随抽屉移出屏幕 → 焦点丢失风险，未见 focus 归还，`M/:1274-1339`）。 |
| 12 | 依据 | `E0` `M/:1274-1339`（effect 全文）、`M/:1315-1321`（选择器/例外/延迟收起）、`M/:1287-1293`（守卫）、`M/:1325-1331`（模态收）。`E1` `R1/:94`（"抽屉点完不收起"的问题与修法）。 |
| 13 | 反证 + 验收 | **反证**：若点完导航不自动收，抽屉会盖住刚打开的会话，用户必须再点一次遮罩（`M/:1307-1309` 注释原话"手机上门面体验很差"）；若把行内 ⋮ 也算导航，用户点开行菜单瞬间抽屉先关，菜单锚点丢失（`M/:1317-1319`）。**验收**：`MOB-09` 点会话行后抽屉 60ms 内收起（真机录屏）；`MOB-10` 点行内 ⋮ 抽屉不收起（真机）。 |

#### `base.layout.right-sidebar.layer` — 右栏改全屏浮层（CSS-only）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.right-sidebar.layer`。上游右栏容器（`[data-rightbar-col]` / `.pI_x6G_rightbarCol` / 旧代 `.pI_x6G_detailsCol`）在窄屏被改成全屏 fixed 浮层（`M/:237-264`）；面板本体来自上游 `ui-sidebar-right`（`P3OORG_*`）。≤767px 生效。 |
| 2 | 网格与定位 | `grid-column:3` + `position:fixed; inset:0; width:100vw; height:100dvh; z-index:70`；常态 `pointer-events:none`，`[data-sidebar-right-open=true]` 时 auto（`M/:241-261`）；`background:transparent`；宽度手柄 `display:none`（`M/:252-264`）。 |
| 3 | 命中区 | 层本身不接事件；面板内按钮 `min-height:44`（`M/:986-998`：`[data-rightbar-col] button`、`.P3OORG_iconButton`）。 |
| 4 | 手势 | 层不绑手势；面板内滚动由上游实现；无侧滑关闭（未见实现）。 |
| 5 | 行为 | 打开/关闭由上游 layout store（插件只改 CSS，不改逻辑）；`R1/:93` 记录此前"整块隐藏"导致功能不可达的 bug。 |
| 6 | 视觉状态 | 透明背景（避免空浮层挡住聊天区，`M/:252-253`）；面板打开时的视觉由上游决定。 |
| 7 | 布局占位与展开形态 | fixed → 不占 Grid 轨道（列宽 0）。属覆盖式导航（非 G-03 三形态）。 |
| 8 | 滚动语义 | 面板高度 100dvh（`M/:995-998`），内部滚动交上游。 |
| 9 | 动效 | 插件未声明 transition（保留上游动效）。 |
| 10 | 边界态 | 半宽面板 + 整屏透明层用 pointer-events 区分（注释 `M/:256-257`）；横屏 >767 回桌面轨道；无数据/空面板：透明层仍需 pointer-events:none（否则挡住下方）。 |
| 11 | 无障碍 | 全部继承上游面板语义（插件不注入 `role`/焦点管理）。 |
| 12 | 依据 | `E0` `M/:237-264`、`M/:986-998`；对照桌面 `dsh packages/client/ui-layout/src/columns.ts:52-58`（手机宽度下右栏轨道被移除）。`E1` `R1/:36,93`。 |
| 13 | 反证 + 验收 | **反证**：桌面 `computeColumns()` 在 390px 视口算出 `available = 390-56-400 = -66 < RIGHTBAR_MIN(300)` → 右栏轨道直接为 0（`dsh columns.ts:54-57`），**若不改成浮层，文件/终端/Start 在手机上完全不可达**（`R1/:93` 记录了这个 bug 与修复）；若透明浮层常态 `pointer-events:auto`，整屏会挡住聊天区所有触摸（注释 `M/:256-257`）。**验收**：`MOB-11` 手机端能打开文件面板并返回（真机）；`MOB-12` 右栏关闭时聊天区可正常滚动（真机）。 |

#### `base.layout.touch-feedback` — 触屏反馈与 300ms 延迟（全程规则）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.touch-feedback`。全局规则：`button, a, [role="button"]`（`M/:214-219`），位于 ≤767px media 内。 |
| 2 | 网格与定位 | 不涉及尺寸（只改反馈与手势行为）。 |
| 3 | 命中区 | 不扩边、不加高（命中区由各组件自己的 min-height 决定，见其它节点）。 |
| 4 | 手势 | `touch-action:manipulation` → 取消双击缩放带来的 300ms 点击延迟；双击不再缩放（`M/:213-218`）。长按/滑动：不拦截。 |
| 5 | 行为 | 无逻辑。 |
| 6 | 视觉状态 | `-webkit-tap-highlight-color:transparent`（去默认蓝色高亮闪屏）；pressed 由组件自绘：汉堡 `scale(.94)`（`M/:333-336`）、统计胶囊 `scale(.98)`+底色（`M/:594-597`）、关闭钮底色（`M/:680-682`）、字号箭头底色（`M/:841-843`）。**没有 ripple**（Material ripple 在 WebView 里未见实现，替代方案就是 :active 变形）。 |
| 7 | 布局占位与展开形态 | 无。 |
| 8 | 滚动语义 | 不改变（`touch-action:manipulation` 允许 pan/双指缩放，只禁双击缩放）。 |
| 9 | 动效 | 各组件自带 transition（0.12–0.15s），reduced-motion 关闭（`M/:1091-1099`）。 |
| 10 | 边界态 | focus-visible 走各组件自定义 outline（`M/:337-340,598-601,683-686`）；键盘用户不受影响；`prefers-reduced-motion` 下按压仍变色（无 transition 即时），反馈不丢。 |
| 11 | 无障碍 | 不改变语义；**去 tap highlight 不损伤可访问性**（焦点环仍在）。 |
| 12 | 依据 | `E0` `M/:212-219`（规则）、`M/:333-336,594-597,680-682,841-843`（按压反馈实现）。`E1` `R1/:43`（声称触摸目标 ≥44×44）、`R1/:44`（动效/reduced-motion）。 |
| 13 | 反证 + 验收 | **反证**：若保留默认 tap highlight，深色 DSH 界面上每个点击先闪一层蓝（注释 `M/:212-213`）；若不设 `touch-action:manipulation`，移动浏览器等待双击 300ms，点击显得"发木"，用户会重复点 → 误触。**验收**：`MOB-13` 连续快速点同一按钮无缩放、无蓝色闪屏（真机录屏）。 |

#### `settings.shell.fullscreen-panel` — 设置面板全屏化

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.shell.fullscreen-panel`。上游设置浮层 `.VOzbGW_overlay/.VOzbGW_panel`（`ui-settings`）在窄屏被改成全屏（`M/:718-737`）。出现条件：打开设置且 ≤767px。 |
| 2 | 网格与定位 | `position:fixed; inset:0; width/height:100dvh; max-width/max-height:100dvh; border-radius:0; flex-direction:column`（`M/:726-737`）；`overlay padding:0`（`M/:719-721`）；`mask` 隐藏（`M/:722-725`）；header `min-height:52+padding`（`M/:781-786`）。 |
| 3 | 命中区 | 关闭钮 `min-width/min-height:44`（`M/:867-876`）；表单输入/下拉 `min-height:44`（`M/:902-920`）；API Key 显隐钮 32→44、输入框右侧留 52（`M/:921-928`）；问号 16px 用伪元素扩到 44（`M/:929-962`）；主/次按钮与插件 tab 44（`M/:964-969`）。 |
| 4 | 手势 | 内容区竖向滚动（pan-y）；工具栏表横向滚动（见 nav-rail）；无下拉关闭手势（未见实现）。 |
| 5 | 行为 | 打开/关闭/内容渲染全由上游设置面板逻辑（插件只改形态）；关闭按钮 44 放大不改行为。 |
| 6 | 视觉状态 | 全屏不透明面板（`--dsw-alias-bg` 系由上游），圆角 0；遮罩层 `display:none`（`M/:722-725`）。 |
| 7 | 布局占位与展开形态 | fixed 全屏，不参与主布局（覆盖式，非 G-03 三形态）。 |
| 8 | 滚动语义 | 内容区 `flex:1 1 0; min-height:0; overflow-y:auto; overscroll-behavior:contain; touch-action:pan-y; -webkit-overflow-scrolling:touch`（`M/:787-796`）→ 独立滚动 + 不传滚动链。 |
| 9 | 动效 | 插件侧未声明（上游自带面板显隐）。 |
| 10 | 边界态 | 横屏 >767 回桌面居中面板；键盘弹出：只把输入框抬到 44 高（注释"避免聚焦时被软键盘顶飞"，`M/:907`），**没有键盘避让/滚动到焦点**（未见实现）；大字号：header 用 min-height 自适应；多语言：标题隐藏（`M/:758-760`）+ 图标导航。 |
| 11 | 无障碍 | 上游 dialog 语义保留；插件不改焦点顺序；关闭钮 44 不改变语义。 |
| 12 | 依据 | `E0` `M/:718-796,867-876,894-920,964-969`。`E1` `R1/:39`（"设置面板全屏、顶部导航单行可横滑"）。 |
| 13 | 反证 + 验收 | **反证**：桌面设置面板是居中浮层，390px 屏上两侧加遮罩只剩窄条、内容区高度不足且不能滚（`M/:718` 整段就是为此存在；`R1/:39`）；若保留 mask 的 `backdrop-filter`，全屏面板后多叠一层模糊，滚动明显掉帧（注释 `M/:722-725`）。**验收**：`MOB-14` 手机设置面板占满全屏且内容区可滚到底（真机）；`MOB-15` 关闭钮 44×44 可点（量测截图）。 |

#### `settings.shell.nav-rail` — 设置导航改单行横滑

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.shell.nav-rail`。上游 `.VOzbGW_nav` 在窄屏改横向 tab 条（`M/:738-786`）。随设置面板出现。 |
| 2 | 网格与定位 | `flex-direction:row; flex-wrap:nowrap; align-items:center; gap:4px; padding:10px 12px 0; overflow-x:auto; overflow-y:hidden; scroll-snap-type:x proximity; scrollbar-width:none; box-shadow:inset 0 -1px 0`（`M/:738-753`）；`navTitle` 隐藏（`M/:758-760`）；`navList` row + gap 4（`M/:761-767`）；`navCell height:auto; min-height:44; padding:6px 12; white-space:nowrap; flex:none; scroll-snap-align:start`（`M/:768-775`）；`navIcon 16×16`（`M/:776-780`）。 |
| 3 | 命中区 | `navCell` 44px 高（+左右 padding 12，实际宽度由文字决定）；相邻 cell `gap:4px`；滚动条隐藏 → **命中区不重叠但可滑出视口**。 |
| 4 | 手势 | 横向滑（pan-x）+ 单击切 tab；与内容区竖向滚动正交；`overscroll-behavior-x:contain` 不让横滑传给页面（`M/:749`）。 |
| 5 | 行为 | 点击 = 切设置子页（上游逻辑）；滚动 snap 到 cell 起点。 |
| 6 | 视觉状态 | 隐藏滚动条（`M/:751-757`）；选中态由上游（插件未改）。 |
| 7 | 布局占位与展开形态 | `flex:none` 固定在面板顶部，内容区在下方独立滚（不随内容滚）。 |
| 8 | 滚动语义 | 自身横滚 + `scroll-snap-type:x proximity`；不内滚内容；到边 `overscroll-behavior-x:contain`（G-04 同向）。 |
| 9 | 动效 | 浏览器原生 snap 动画（插件无自定义曲线）。 |
| 10 | 边界态 | tab 多到溢出：横滑（`R1/:39` "顶部导航单行可横滑"）；手机竖屏 390px 下 8 个 tab 约需 2 屏宽；横屏 >767 恢复桌面纵向导航；大字号：`white-space:nowrap` + 44 高。 |
| 11 | 无障碍 | 上游 tab 语义保留；横滑容器未补 `aria-orientation`（未见实现）；键盘用户可用 Tab 逐项（tab 可聚焦）。 |
| 12 | 依据 | `E0` `M/:738-786`（全部导航 CSS）、`M/:749-751,774`（facts 命中行）。 |
| 13 | 反证 + 验收 | **反证**：桌面设置导航是纵向列表，全屏面板里会占掉首屏约 1/3（每项 40px），内容区只剩半屏；若不隐藏滚动条，横向导航条会多出一条灰线破坏顶栏。**验收**：`MOB-16` 设置页横滑能到达全部 tab（真机手势）；`MOB-17` 竖屏不出现横向页面溢出（截图/量测）。 |

#### `settings.general.font-stepper` — 字号步进器重排（issue #2 的核心，CSS-only）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.general.font-stepper`。上游字号行 `.bVCLcG_stepper/.bVCLcG_arrows/.bVCLcG_arrow/.bVCLcG_expander` 在窄屏被重排（`M/:813-865`）。出现条件：设置 → 通用 → 字号行，≤767px。 |
| 2 | 网格与定位 | 上游结构（注释给出）：`absolute` 列容器 18×26，内含两个 **17×12** 上下箭头，**中心仅相距 14px**（`M/:802-808`）。窄屏改：`.bVCLcG_stepper{width:auto; min-height:44}`、`.bVCLcG_arrows{position:static; flex-direction:row; gap:2px; flex:none; opacity:1}`、`.bVCLcG_arrow{min-width/min-height:44; display:inline-flex; border:1px solid; border-radius:10; background:layer-2}`、`.bVCLcG_expander{44×44}`、`.bVCLcG_row/.control{min-height:44; flex-wrap:wrap; row-gap:8}`（`M/:813-865`）。 |
| 3 | 命中区 | **两个 44×44 命中盒并排，间距 2px**；视觉图标尺寸不变（仍由内部 svg 决定，注释 `M/:829`）；扩边方式 = **改布局（重排）+ 加大盒**，不是伪元素扩边。 |
| 4 | 手势 | 单击加/减；`disabled` 态由上游原生 button 拦截（`M/:844-846` 只改透明度）；无长按连加（未见实现）。 |
| 5 | 行为 | 上游逻辑（改字号并保存）；插件只改布局与可见性。 |
| 6 | 视觉状态 | 箭头 0.2.1 起上游默认 `opacity:0` 仅 hover/focus → 窄屏强制 `opacity:1`（`M/:819-827`）；`:active:not(:disabled)` 底色 hover（`M/:841-843`）；`:disabled` opacity .45（`M/:844-846`）。 |
| 7 | 布局占位与展开形态 | 静态 flex 行参与布局；行允许换行（`M/:856-865`），箭头从"浮在行右侧的 absolute 列"变成"行内并排"→ **行高由 44 决定，视觉变高**（代价）。 |
| 8 | 滚动语义 | 不滚动（在可滚的设置内容区内）。 |
| 9 | 动效 | 无自定义（上游 hover/focus 的 opacity 过渡保留）。 |
| 10 | 边界态 | 字号上限/下限：箭头 disabled 态（`M/:844`）；窄到 320px：行可换行（`M/:856-865`）；多语言：标签与控件同行、控件可折到第二行。 |
| 11 | 无障碍 | 原生 button；`disabled` 用原生属性（可读屏报禁用）；`aria-label` 由上游提供（插件未改）。 |
| 12 | 依据 | `E0` `M/:802-865`（注释+CSS 全段）、`M/:158-168`（文件头 TOUCH-TARGET REGISTER 的同一段说明）。`E1` `R1/:47-58,63,95`（issue #2 根因与修复记录）。 |
| 13 | 反证 + 验收 | **反证**：若照桌面保留 absolute 上下箭头：命中盒 17×12、中心距 14px，手指（接触面 ~30–40px）必然同时压到两个 → "想调大却触发调小"，反复失败后用户以为界面坏了（注释 `M/:802-808`；`R1/:54-57` 原话）。若只把命中盒扩到 44 而不重排：两个 44 盒在 14px 中心距下重叠 30px，重叠区归谁不可判定。**重排是唯一解**。**验收**：`MOB-18` 两箭头命中区不重叠且点击方向正确（真机 14→15→14，`R1/:360` 的回归断言）；`MOB-19` 手感：44×44 量测截图。 |

#### `settings.general.switch` — 开关/复选框伪元素扩边

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.general.switch`。`[role=switch]` / `[role=checkbox]` 的设置行（`M/:934-945`）。出现条件：设置各子页里带开关/复选框的行，≤767px。 |
| 2 | 网格与定位 | 命中宿主 `position:relative`（`M/:936-939`）；扩边用伪元素 `::after{content:""; position:absolute; inset:-12px -8px}`（`M/:940-945`）→ 上下各 +12、左右各 +8。 |
| 3 | 命中区 | 视觉本体：开关按 `ref/dsh-base.md` §0 第 10 条为 36×20 → 扩后 **20+24=44px 高**、36+16=52px 宽；复选框更小，扩后同高 44。**无 `z-index`、无相邻行间距补偿、无隔离带**。 |
| 4 | 手势 | 单击切换（上游 role=switch 逻辑）；伪元素不改变事件目标（仍命中宿主）。 |
| 5 | 行为 | 上游受控开关；插件不改逻辑。 |
| 6 | 视觉状态 | **视觉完全不变**（这正是扩边的意义）；default/checked/disabled 全由上游。 |
| 7 | 布局占位与展开形态 | `position:relative` 不影响布局；伪元素 absolute → 不占位、不推挤。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无（不改变上游开关动画）。 |
| 10 | 边界态 | 与相邻行重叠风险：若相邻行的命中盒紧贴（设置行被抬到 44 后行间距为 0，`M/:851-855`），两行的扩边各侵入 12px → **最多 24px 重叠**（本仓库无防护）；多语言/大字号：不影响伪元素尺寸（固定 -12/-8）→ 大字号时开关变大，44 下限反而可能不再够。 |
| 11 | 无障碍 | 扩边不改变语义；读屏仍报 switch/checkbox 状态（上游 role）。 |
| 12 | 依据 | `E0` `M/:934-945`（facts 命中 4 行：`:936,937,940,941`）；开关桌面尺寸 `ref/dsh-base.md` §0 第 10 条（36×20）；设置行 min-height 44 `M/:851-855`。 |
| 13 | 反证 + 验收 | **反证**：不扩边则 20–36px 的开关在手机上点不中（指尖 30–40px）；若直接把开关视觉放大到 44㎡，比例失真且推高整行。**但扩边必须配隔离**：myco 若抄这一手法，必须同时保证相邻命中盒不相交（否则出现"点这是开、点那是另一个开"）。**验收**：`MOB-20` 开关命中区 ≥44×44（量测）；`MOB-21` 连续两行开关的边缘点击各自只切换自己（真机，隔离验证）。 |
#### `base.primitives.touch-floor.dialog-controls` — 对话框内控件 44px 下限（CSS-only，通配规则）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.touch-floor.dialog-controls`。一条**通配规则**：所有 `[role="dialog"]` 内的 `_sm_` 按钮 / `_iconButton` / `input` / `textarea` / `select`（`M/:894-905`）。出现条件：任何模态对话框（设置、模型、确认框、统计面板……）且 ≤767px。 |
| 2 | 网格与定位 | 不改变布局位置，只 `min-height:44px`（按钮/输入）、`min-width+min-height:44px`（图标按钮）。 |
| 3 | 命中区 | 高 44（输入/按钮）；图标按钮 44×44。用 `min-*` 而非固定值 → 大字号时随内容继续长高，不会裁切。 |
| 4 | 手势 | 继承各控件原生（button 点击、input 聚焦、select 打开系统选择器）。 |
| 5 | 行为 | 不改变任何逻辑（纯尺寸）。 |
| 6 | 视觉状态 | 只变高/宽，状态样式全由上游。 |
| 7 | 布局占位与展开形态 | 参与对话框内布局流；因 `min-height` 抬升会让对话框内容变高 → 依赖对话框自身滚动（若对话框不滚可能溢出，插件未处理此边界：`M/:894-905` 无 max-height 联动）。 |
| 8 | 滚动语义 | 不改（对话框滚动由上游）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 大字号：随内容继续长；对话框高度不够时可能溢出（未见兜底）；复选框/单选框/滑块**被显式排除**（`:not([type=checkbox]):not([type=radio]):not([type=range])`，`M/:902`）→ 这三类保持原尺寸（另由 switch 节点覆盖）。 |
| 11 | 无障碍 | 不改语义；44px 提升触摸可达性（WCAG 2.5.5）。 |
| 12 | 依据 | `E0` `M/:894-905`（facts 命中 4 行 `:895,898,902,903`）。 |
| 13 | 反证 + 验收 | **反证**：桌面对话框按钮 28px、输入框 32–36px（`ref/dsh-base.md` §0 第 10 条：模态关闭 28×28）；手机上点不中，且输入框聚焦时软键盘会顶起布局（注释 `M/:907`）。**代价**：对话框整体变高，小屏上可能出现内容溢出（本插件未联动 `max-height`）。**验收**：`MOB-22` 设置/模型对话框内所有可交互控件 ≥44×44（回归脚本 `tools/regress.py` 已在做，`R1/:356-357`）；`MOB-23` 小屏（320×568）对话框不溢出（真机/截图）。 |

#### `base.primitives.popover.mobile-constraint` — 弹层视口约束（CSS-only）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.primitives.popover.mobile-constraint`。模型选择菜单 `_7KE1Ra_menu`、上下文用量面板 `JObwrW_panel`、命令菜单卡片 `mufS8W_card`（`M/:507-522`）＋菜单行 `_7KE1Ra_option/_cell/mufS8W_row/QsffPG_row`（`M/:524-538`）。≤767px。 |
| 2 | 网格与定位 | 统一 `max-width:calc(100vw - 16px)`；菜单 `width:min(240px, calc(100vw-16px))`、`max-height:min(60dvh, calc(100dvh-120px))`；用量面板 `width:min(264px, 100vw-16px)`；命令卡 `max-height:min(50dvh, 320px)`；菜单行 `min-height:44`；搜索清除钮 44×44 且右对齐 0、搜索框右 padding 48（`M/:507-538`）。 |
| 3 | 命中区 | 菜单行 44 高；搜索清除按钮 44×44；相邻行堆叠无间隙风险（都是满行）。 |
| 4 | 手势 | 菜单项点击；菜单自身滚动（上游）；无长按。 |
| 5 | 行为 | 上游逻辑（选择模型/命令/查看用量），插件不改。 |
| 6 | 视觉状态 | 只约束尺寸，状态由上游。 |
| 7 | 布局占位与展开形态 | 浮层（fixed/portal，上游决定），不占布局；展开形态属 G-03 形态 C（sheet/popover）。 |
| 8 | 滚动语义 | 菜单列表在 `max-height` 内滚动（上游）；`max-height` 用 `dvh` 而非 `vh` → 键盘弹出时不会超出可视区（`M/:515-516`）。 |
| 9 | 动效 | 无（上游动效保留）。 |
| 10 | 边界态 | 320px 屏：菜单 240→304px 上限仍可放下；键盘弹出：`dvh` 上限生效；横屏 >767 回桌面尺寸；多语言：菜单行 `min-height` 不受文案影响（自适应宽）。 |
| 11 | 无障碍 | 不改语义（上游菜单 role）。 |
| 12 | 依据 | `E0` `M/:507-538`。 |
| 13 | 反证 + 验收 | **反证**：桌面弹层固定 240–320px 宽，在 390px 屏 + 键盘弹出后 `max-height` 若用 `vh` 会被键盘遮住下半；不约束 `max-width` 会在窄机横向溢出（`R1/:38`）。**验收**：`MOB-24` 390px/320px 下模型菜单不越界（截图）；`MOB-25` 键盘弹出时菜单底部仍可见（真机）。 |

#### `composer.stats.pill` — 手机端底部统计胶囊（替代桌面统计行）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.stats.pill`。插件自有组件：注册进 `conversation.composer.dock` 槽（id `dsh-mobile-stats`，`M/:1682-1687`）。出现条件：有任意统计量（`stats.steps>0 \|\| turns>0 \|\| billed>0 \|\| output>0`，`M/:1566-1567`）否则返回 null；≤767px 才显示（基态 `display:none`，`M/:183-185`，media 内 `display:flex`，`M/:561-570`）。每会话 1 个。 |
| 2 | 网格与定位 | 在底部 dock 内 `position:relative; display:flex; flex:1 1 auto; min-width:0; max-width:100%; justify-content:center; padding:0 4px`（`M/:561-570`）；胶囊 `inline-flex; gap:6px; min-height:44; padding:10px 14px; border-radius:999px; font-size:12px/18px; max-width:100%`（`M/:571-593`）；dock `gap:8; padding-top:2; flex-wrap:wrap; justify-content:center`（`M/:555-560`）。 |
| 3 | 命中区 | **44px 高胶囊**（`min-height:44` + padding 10/14）；视觉仍是小胶囊（靠 padding 撑高，`M/:577-579` 注释明确"视觉仍是胶囊，靠 padding 撑高"）。与相邻 dock 元素 gap 8。 |
| 4 | 手势 | 单击 = 开/关报表（`M/:1618` `onClick:()=>setOpen(v=>!v)`）；无长按/滑动关闭（面板关闭靠 Esc/点外部，见 panel 节点）。 |
| 5 | 行为 | `setOpen` 切换；受控 `aria-expanded`（`M/:1616`）；打开时把焦点交给面板、关闭时还给胶囊（`M/:1509-1517`）；面板打开时测量胶囊上沿写 inline `bottom`（`M/:1443-1465`）。幂等可撤销。 |
| 6 | 视觉状态 | default：`--dsw-alias-label-tertiary` 文字、layer-2 底、1px l2 边（`M/:580-585`）；pressed：`:active` hover 底色 + `scale(.98)`（`M/:594-597`）；expanded：文字 primary + 业务色边（`M/:602-605`）+ caret 旋转 180°（`M/:617-619`）；focus-visible：2px outline（`M/:598-601`）；超长：单行 nowrap + ellipsis（`M/:587-589`）；无数据：整组件不渲染（`M/:1566-1567`）。 |
| 7 | 布局占位与展开形态 | 胶囊**在布局流内**（占 dock 一行高 44）；展开面板是 `position:fixed` 浮层（形态 C，sheet-overlay），**不占位**（G-01 同向）。 |
| 8 | 滚动语义 | 胶囊不滚、不展开内滚（面板滚）。 |
| 9 | 动效 | 底色/边/文字 .15s、scale .12s（`M/:592`）；caret .18s（`M/:615`）；reduced-motion 关闭（`M/:1096-1099`）。 |
| 10 | 边界态 | 无数据：不渲染且**上游官方统计行保持可见**（`:has(.dsh-stats)` 才隐藏，`M/:549-554`）；插件异常：`StatsBoundary` 只降级不拖垮 dock（`M/:1359-1373`）；超长文本：ellipsis；多语言：zh/en 字典完整（`M/:10-54`）；大字号：不跟 `--dsh-content-font-size`（固定 12px）；横屏 >767 不显示（回桌面官方统计行）。 |
| 11 | 无障碍 | `<button type="button">` + `aria-haspopup="dialog"` + `aria-expanded` + `aria-controls`（仅打开时给 panel id）（`M/:1611-1618`）；可访问名 = 胶囊文本内容（无 aria-label）。 |
| 12 | 依据 | `E0` `M/:561-619`（CSS）、`M/:1388-1400`（数据源）、`M/:1566-1655`（渲染）、`M/:1682-1687`（槽位注册）、`M/:549-554`（隐藏上游）；facts 命中 `:602,617,1616,1618`。`E1` `R1/:42,105-118`。 |
| 13 | 反证 + 验收 | **反证**：桌面统计行是 20px 高的只读 pill 行；在手机上不可点、也没有展开报表入口 → 会话统计在手机上等于看不到（`R1/:42` 提供了"点胶囊看完整会话报告"的动机）。若不隐藏上游统计行会出现两行统计（`R1/:92`）。**验收**：`MOB-26` 胶囊 44px 高可点、展开后统计数字齐全（真机+截图）；`MOB-27` 无数据时不显示胶囊且官方统计行仍在（边界）。 |

#### `composer.stats.close` — 报表关闭按钮

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.stats.close`。统计报表标题栏右侧的关闭钮（`M/:1635-1640`）。出现条件：报表打开。 |
| 2 | 网格与定位 | 在 sticky 标题栏右侧（`justify-content:space-between`，`M/:649-664`）；`44×44`、`border-radius:999px`、`padding:0`、`display:inline-flex`、`margin:-8px -8px -8px 0`（负 margin 抵进标题栏内边距，`M/:665-679`）。 |
| 3 | 命中区 | 44×44（比视觉图标 18px 大得多）；负 margin 让热区伸进标题栏 8px，但**不与盘面其它可点元素相邻**（标题文字不可点）→ 安全。 |
| 4 | 手势 | 单击 = `setOpen(false)`（`M/:1639`）；无长按/滑动。 |
| 5 | 行为 | 关闭面板，焦点归还胶囊（由打开 effect 的 cleanup 处理，`M/:1513-1515`）。幂等。 |
| 6 | 视觉状态 | default 文字 secondary；:active 底色 hover（`M/:680-682`）；:focus-visible 2px outline（`M/:683-686`）；无 hover 专属。 |
| 7 | 布局占位与展开形态 | 在 sticky 标题栏内（`position:sticky; top:0`，`M/:649-652`），随面板滚不动。 |
| 8 | 滚动语义 | 不滚（面板内容独立滚）。 |
| 9 | 动效 | 无自定义（active 即时）。 |
| 10 | 边界态 | 面板超长滚动时按钮始终可见（sticky 标题栏）；多语言：`aria-label=t("stats.close")`（`M/:1638`）；大字号：图标 18 固定、盒 44 固定。 |
| 11 | 无障碍 | `<button type="button">` + `aria-label`（"关闭"/"Close"）（`M/:1635-1639`）；读屏可读。 |
| 12 | 依据 | `E0` `M/:665-686,1635-1640`；facts 命中 `:1639`。 |
| 13 | 反证 + 验收 | **反证**：桌面弹层关闭钮 28×28（`ref/dsh-base.md` §0 第 10 条），手机上面板顶部边缘的 28px 关闭钮命中率低；若没有 × 只能点外部/Esc（`R1/:103` 记录"统计面板只能点 ×"是旧 bug 的反向——旧版只有 × 能关）。**验收**：`MOB-28` 44×44 可点且点外部/Esc 也能关（真机）。 |

#### `composer.stats.panel` — 统计报表浮层

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.stats.panel`。打开胶囊后渲染的 `role=dialog` 浮层（`M/:1623-1653`）。出现条件：胶囊被点开且有数据。 |
| 2 | 网格与定位 | `position:fixed; left:12; right:12; bottom:calc(var(--dsh-composer-height,152px)+8px); z-index:1000; max-width:480px; margin-inline:auto; max-height:min(52dvh, 100dvh-var(--dsh-composer-height)-24px); padding:0 14px 8px; border-radius:16px; border 1px; shadow lv3`（`M/:620-645`）。打开时 JS 用胶囊 `getBoundingClientRect().top` 覆盖 `bottom/maxHeight`（`M/:1443-1465,1606-1609`）。 |
| 3 | 命中区 | 浮层本身；内部关闭钮 44（见 close 节点）；内部行不可点（只读数据）。 |
| 4 | 手势 | 内容滚动（pan-y）；Esc 关闭（`M/:1469-1475`）；点面板外关闭（`pointerdown` 捕获，`M/:1495-1503`）；Tab 焦点环（`M/:1476-1494`）。无滑动关闭/下拉。 |
| 5 | 行为 | 打开：`setOpen(true)` + 测位置 + 焦点进面板；关闭：×/Esc/点外部 → `setOpen(false)` + 焦点还胶囊。幂等。 |
| 6 | 视觉状态 | 打开动画 `dsh-stats-in` .18s（`M/:1118-1127`）；标题 sticky + 底色遮挡（`M/:649-664`）；`:focus{outline:none}`（面板容器）（`M/:646-648`）。 |
| 7 | 布局占位与展开形态 | **形态 C（sheet-overlay / 浮层）**：fixed、不占位、不推挤聊天内容（G-01/G-03 C）。 |
| 8 | 滚动语义 | 自身 `overflow-y:auto` + `overscroll-behavior:contain` + `touch-action:pan-y` + 惯性（`M/:631-636`）→ 到边不传滚动链（G-04，声明式部分）；另加命令式到边锁（见 edge-lock 节点）。 |
| 9 | 动效 | 进入 .18s `cubic-bezier(.22,1,.36,1)` translateY(6px)+scale(.985)（`M/:1118-1127`）；reduced-motion 关闭（`M/:1095`）；无退出动画（直接 unmount）。 |
| 10 | 边界态 | 键盘弹出：`visualViewport` resize 时重测 bottom/maxHeight（`M/:1456-1464`）；横竖屏：`orientationchange` 重测；超长数据：内部滚动；空数据：胶囊不渲染 → 面板不出现；多语言：行标签 zh/en；大字号：面板文字 13/14px 固定（不跟随内容字号轴）；**无遮罩**（点外部靠外部监听，不是 scrim）。 |
| 11 | 无障碍 | `role="dialog" aria-modal="true" aria-label=t("stats.title") tabIndex=-1`（`M/:1626-1631`）；打开 focus 面板、关闭还胶囊（`M/:1509-1517`）；Tab 焦点环（`M/:1476-1494`）；Esc 关闭并 `stopPropagation`（`M/:1470-1474`）。⚠️ `aria-modal=true` 但无 scrim → 语义声称"模态"而视觉/交互不是模态（背景仍可点，点外部即关闭）；这是可访问性语义与交互不一致，进 §6 Q-MOB-13。 |
| 12 | 依据 | `E0` `M/:620-664`（CSS）、`M/:1443-1508`（测量/Esc/外点/Tab）、`M/:1606-1609,1623-1653`（渲染）、`M/:1118-1127`（进入动画）。facts 命中 `:634`。`E1` `R1/:42,96`。 |
| 13 | 反证 + 验收 | **反证**：若把 `bottom` 写死（旧实现 `bottom:216px`），composer 高度随附件/队列/键盘变化后面板会悬空或压住输入框（`R1/:96`）；若不测 `visualViewport`，iOS 键盘弹出时面板底部被遮住一半；若做成形态 A（占位），会推走聊天内容、丢失阅读锚点（G-03 反证）。**验收**：`MOB-29` 键盘弹出后面板不被遮（真机 iOS/Android 各一次）；`MOB-30` 点外部/Esc 关闭且焦点回到胶囊（TalkBack/键盘）。 |

#### `composer.stats.panel.edge-lock` — 报表到边不传滚动（G-04 的手机端实现）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.stats.panel.edge-lock`。面板上的命令式滚动锁（`M/:1411-1440`）。出现条件：面板打开（`open === true` 才挂监听，`M/:1440` 依赖数组）。 |
| 2 | 网格与定位 | 无视觉盒；监听器挂在面板 DOM 上（`el.addEventListener`，`M/:1432-1434`）。 |
| 3 | 命中区 | n/a（作用于整块面板的滚动手势）。 |
| 4 | 手势 | wheel（鼠标/触控板）+ touchstart/touchmove（手指）。判据：`atTop() = scrollTop<=0`、`atBottom() = scrollTop+clientHeight >= scrollHeight-1`；**到顶继续上滚（wheel deltaY<0 或 touch dy>0）→ `stopPropagation()`；到底继续下滚（deltaY>0 或 dy<0）→ `stopPropagation()`**（`M/:1415-1430`）。 |
| 5 | 行为 | 吸收"残余滚动"：在边缘时阻止事件冒泡到外层会话，**但监听器是 `{passive:true}`** → 不能 `preventDefault`，浏览器仍会把滚动交给外层（若外层是全页滚动容器）。注释解释旧 bug：只判 `ev.deltaY`，touchmove 时恒 undefined → 锁从未生效（`M/:1403-1410`）。 |
| 6 | 视觉状态 | 无。 |
| 7 | 布局占位与展开形态 | 无。 |
| 8 | 滚动语义 | 这是**内滚到边仲裁**本身：内滚到边后"停止把速度传给外层"（G-04 的目标）。当前实现只做到"阻止冒泡 + 声明式 `overscroll-behavior:contain` 双保险"（`M/:634`）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 内容不足一屏：atTop && atBottom 同时成立 → 两个方向都拦；`-1px` 容差防亚像素（`M/:1416`）；被动监听无法阻止默认滚动（`M/:1432`）→ 在某些 WebView 上外层仍会跟滚（E2 风险，§6 Q-MOB-15）。 |
| 11 | 无障碍 | 无（纯手势仲裁）。 |
| 12 | 依据 | `E0` `M/:1403-1440`；facts 命中 9 行（`:1417,1420,1423,1432,1433,1434,1436,1437,1438`）。`E1` `R1/:102`（"touchmove 没有 deltaY，方向恒判 false，手机上面板边界穿透"）。 |
| 13 | 反证 + 验收 | **反证**：若不吸收残余速度，用户在面板里滑到底后整个会话开始滚动、丢失阅读位置（G-04 的反证原话）；若只用 `overflow:hidden` 禁滚，则面板自身内容不可读。**myco 必须做到比这里更彻底**：Compose 侧应消费手势（等价 preventDefault），而不是只挡冒泡；否则 G-04 在 Android WebView 上可能仍漏。**验收**：`MOB-31` 面板内滑到底后会话不上移（真机录屏）；`MOB-32` 面板内滑到顶后会话不动（真机）。 |

#### `composer.tools.row` — 输入工具行单行压缩（CSS-only）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.tools.row`。上游输入区工具行 `.uV2eYG_row/.uV2eYG_tools/.uV2eYG_modes/.uV2eYG_trailing` 等（`M/:458-505`）。≤767px。 |
| 2 | 网格与定位 | `flex-wrap:nowrap !important; gap:8; padding:2px 6px 6px`；`tools{gap:6; flex:1 1 auto; min-width:0}`；`modes{gap:4}`；`trailing{gap:6; margin-left:auto; flex:none}`；触发器 `max-width:112px !important; min-height:44`、标签 12px；`add/primary` 44×44（`M/:458-505`）；输入区 `--dsh-composer-side-clearance:8px`（`M/:445-448`）。 |
| 3 | 命中区 | 工具行内所有 `button/select` `min-height:44`（`M/:477-481`）；加号/发送 44×44（圆形按钮原 28/34）；触发器 44 高、112 宽；相邻 gap 4–8。 |
| 4 | 手势 | 点击各工具按钮；触发器点击弹菜单（上游）；无长按。 |
| 5 | 行为 | 全由上游（发送/附件/权限/模型选择），插件只改尺寸与换行。 |
| 6 | 视觉状态 | 只改尺寸/间距；状态由上游。 |
| 7 | 布局占位与展开形态 | 参与输入区布局流，`nowrap` 强制单行（超宽时挤压/裁切而不是换行，`M/:459`）；输入区在底部 dock，随 composer 高度变化。 |
| 8 | 滚动语义 | 工具行不滚（放不下时依赖上游裁切/收缩）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 320px 窄机：单行 + 112px 触发器上限 + 44px 按钮可能总量超宽 → 靠 `min-width:0` + 上游收缩/裁切（未见横向滚动兜底，未见换行兜底 → 风险）；大字号：工具按钮 44 固定、标签 12px 固定；横屏 >767 回桌面。 |
| 11 | 无障碍 | 不改语义；44px 提升可达性。 |
| 12 | 依据 | `E0` `M/:445-505`、`M/:494`（0.2.1 加号/发送仍 28/34 的注释）。`E1` `R1/:37,72-75`。 |
| 13 | 反证 + 验收 | **反证**：桌面工具行允许多行 + 无宽度上限；手机上若不限宽，模型/权限触发器把发送钮挤出屏幕（`R1/:37` "强制单行…触发器限宽 112px"）；若换行则输入区变高、挤压会话可视高度。**代价**：320px 极窄机可能仍需裁切（本插件未验证，`R1/:358` 只测到 iPhone SE 375px）。**验收**：`MOB-33` 375/390px 下工具行单行且发送钮完整可见（截图）；`MOB-34` 各按钮 44px（量测）。 |

#### `chat.header.tabs` — 会话头部「对话/轨迹」tab 抬到 44px 并居中（CSS-only）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.header.tabs`。上游 `.wSkVaW_tabs/.wSkVaW_tab`（`M/:424-443`）。≤767px。 |
| 2 | 网格与定位 | `tabs{justify-content:center; padding-left:0; margin-left:-52px; margin-right:0; gap:32px}`（用 `64-12=52` 负 margin 抵消头部左内边距，让 tab 组落在视口中线）（`M/:424-433`）；`tab{box-sizing:border-box; inline-flex; center; min-width:44px; min-height:44px; padding:0 2px 9px}`（`M/:434-443`）。 |
| 3 | 命中区 | tab 44 高（上游 25px，注释 `M/:434`）+ `min-width:44`；相邻 tab 间距 32px（远大于 8px 隔离带）。 |
| 4 | 手势 | 单击切 tab（上游）；无长按/滑动切 tab（未见实现）。 |
| 5 | 行为 | 上游 tab 切换。 |
| 6 | 视觉状态 | 只加 padding 撑高，选中下划线/颜色由上游（`padding-bottom:9` 让下划线位置不变）。 |
| 7 | 布局占位与展开形态 | 在标题行内、参与布局；`min-height:44` 会把头部抬高约 19px（代价）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 头部换行：`titleRow{flex-wrap:wrap; row-gap:4}`（`M/:380-384`）→ 超长标题时 tab 换到第二行；多语言：tab 文案长度不同但 `min-width` 兜底；大字号：`font` 跟随上游、盒 44 固定。 |
| 11 | 无障碍 | 上游 tab 语义；44px 提升可达性；无 `aria-selected` 变更（上游）。 |
| 12 | 依据 | `E0` `M/:424-443`、`M/:376-379`（52px 的来源）。`E1` `R1/:35`（"「对话/轨迹」标签精确居中"）。 |
| 13 | 反证 + 验收 | **反证**：上游 tab 仅 25px 高，手机上抬手容易点到旁边/漏点（注释 `M/:434`）；若不抵消 64px 左内边距，居中会偏右 26px（`M/:424-433` 的负 margin 就是补偿）。**验收**：`MOB-35` 390px 下 tab 视觉居中（量测左右留白）；`MOB-36` tab 44px 高可点（真机）。 |

#### `chat.header.crumb` — 标题面包屑用 padding 撑到 44px（CSS-only）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.header.crumb`。上游 `.wSkVaW_crumb`（`M/:399-407`）。≤767px。 |
| 2 | 网格与定位 | `max-width:170px; min-height:44px; box-sizing:border-box; padding-top:12px; padding-bottom:12px`；保持 `inline-block` 才能让上游 `text-overflow:ellipsis` 生效（`M/:399-407` 注释）。 |
| 3 | 命中区 | 44px 高（20px 行高 + 12+12 padding）；宽 ≤170px；与右侧动作 gap 4。 |
| 4 | 手势 | 单击（上游面包屑跳转/展开）；无长按。 |
| 5 | 行为 | 上游（会话/工作区分层导航）。 |
| 6 | 视觉状态 | 只加 padding；文字/省略号由上游。 |
| 7 | 布局占位与展开形态 | 参与标题行布局；`min-height:44` 抬高头部（与 tab 共同导致头部更高）。 |
| 8 | 滚动语义 | 不滚；超长用 ellipsis（`max-width:170`）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 超长标题：170px 上限 + ellipsis；多语言：宽固定 170；大字号：行高增长后 padding 仍 12/12，实际高度 >44（随字号长，不裁切）。 |
| 11 | 无障碍 | 上游语义；44px 提升。 |
| 12 | 依据 | `E0` `M/:399-407`。 |
| 13 | 反证 + 验收 | **反证**：面包屑是本插件里唯一**能保持视觉字号不变而把行高抬到 44** 的例子（padding 撑高，不换控件形态）——这正是 Q-03 候选①的 Web 实现方式；但它把头部整体抬高，桌面 24–28px 的行高在手机上变成 44，**头部吃掉更多首屏**（代价）。**验收**：`MOB-37` 面包屑 44px 可点且省略号仍生效（截图/真机）。 |

#### `trajectory.details.panel` — 轨迹详情改悬浮卡（CSS-only）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `trajectory.details.panel`。上游轨迹页的分栏详情面板 `.Y0dWHa_split/.Y0dWHa_tablePane/.Y0dWHa_details`（`M/:1000-1088`）。出现条件：轨迹页选中某行，≤767px。 |
| 2 | 网格与定位 | 分栏改单列：`split{display:block}`、`tablePane{width:100%}`；详情 `position:fixed; left/right:12; top:auto; bottom:calc(var(--dsh-composer-height,152px)+8px); height:min(52vh,460px); max-height:min(52vh, 100dvh-composer-24); z-index:1000; border-radius:14; flex-direction:column; overflow:hidden`（`M/:1029-1056`）；缩放柄隐藏（`M/:1057-1059`）；header `min-height:44`、close 44×44、tabs 44、detailTab 44（`M/:1060-1074`）；body `flex:1 1 0; min-height:0; overflow-y:auto; overscroll-behavior:contain; -webkit-overflow-scrolling:touch; padding-bottom:16`（`M/:1075-1088`）。 |
| 3 | 命中区 | 关闭 44×44（`M/:1065-1068`）；tab 44 高；工具栏按钮 44（`M/:1013-1017`）；搜索框/输入 44（`M/:1018-1025`）；工具栏高度变量 `--dsh-trajectory-toolbar-height:44px`（`M/:1006-1008`）。 |
| 4 | 手势 | 详情正文滚动（pan-y）+ 关闭 + tab 切换；无滑动关闭（未见实现）。 |
| 5 | 行为 | 上游（选中行/切 tab/关闭）；插件只改形态。 |
| 6 | 视觉状态 | 浮卡：圆角 14 + shadow lv3 + border l1；背景 layer-1；`display:flex; flex-direction:column`（`M/:1048-1055`）。 |
| 7 | 布局占位与展开形态 | 形态 C（sheet-overlay）：fixed 浮层不占位；轨迹表格单列全宽。 |
| 8 | 滚动语义 | 正文独立滚动 + `overscroll-behavior:contain`（G-04 同向）；浮卡自身 `overflow:hidden`。 |
| 9 | 动效 | 插件未声明（上游面板动效保留）。 |
| 10 | 边界态 | 键盘弹出：`bottom` 依赖 `--dsh-composer-height` 实时变量（`M/:1039-1040`）→ composer 变高时浮卡跟着抬；高度上限同时受 `52vh` 与 `100dvh-composer-24` 约束；横屏 >767 回桌面分栏；无数据：上游不渲染。 |
| 11 | 无障碍 | 上游面板语义保留；44px 控件提升可达性。 |
| 12 | 依据 | `E0` `M/:1000-1088`；facts 命中 `:1080`（正文 overscroll）。`E1` `R1/:41`（"详情面板悬浮在输入区上方（跟随 composer 高度），内部可滚动"）。 |
| 13 | 反证 + 验收 | **反证**：桌面轨迹是左右分栏；手机上分栏会把表格压成一条缝且详情不可读；若详情仍嵌在流内，选中行后整页跳位、丢失表格锚点（G-03 C 的反证）；若 bottom 写死 130px，composer 变高会盖住详情（注释 `M/:1039-1040`）。**验收**：`MOB-38` 手机轨迹页选中行后详情从底部浮出、正文可滚（真机）；`MOB-39` 关闭按钮 44px（量测）。 |
### 3.2 mobile-android 宿主壳节点（AN）

> 说明：这些节点**不是 myco 要绘制的界面**，而是 Android 宿主（WebView 壳）必须回答的契约。
> myco 是原生 Compose App，没有 WebView 层，但「返回键语义 / 外链 / 文件选择 / 键盘 / 安全区 / 通知」这些壳层问题**同样存在**，
> 本组节点用于对照：mobile-android 做到了什么、漏了什么，myco 不能重复漏。

#### `base.layout.host.webview` — WebView 容器与视口策略

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.host.webview`。`MainActivity` 里唯一的界面容器：`FrameLayout root + WebView(web)`（`A/:180-285`）。出现条件：App 启动即创建，全程唯一（`singleTask`，`X/:31`）。 |
| 2 | 网格与定位 | `FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)` 铺满窗口（`A/:283-284`）；WebView 自身视口策略：`setUseWideViewPort(true)`（遵循页面 `<meta viewport>`，`A/:187`）+ `setLoadWithOverviewMode(true)`（宽度适配视口，`A/:188`）；UA 追加 ` DshMobile/0.1`（`A/:190`）。 |
| 3 | 命中区 | 不适用（整屏 WebView；命中区由 DSH 网页自身决定，见 AD 节点）。**壳层没有任何触摸命中区改写**。 |
| 4 | 手势 | 全部交给 WebView 默认行为：单指滚动、双指缩放、长按文本选择（未接管，`A/:180-296` 无 `OnTouchListener`/`OnLongClickListener`）；返回手势由系统/手势导航处理（Activity 走 `onBackPressed`）。 |
| 5 | 行为 | 加载 `console.html`（`A/:287`）→ 就绪后切到 DSH URL（`A/:1075-1101`）；`onPageFinished` 注入外链 hook（`A/:217-223`）；错误回调转对话框（`A/:226-239`）。 |
| 6 | 视觉状态 | WebView 背景 `#0d1117`（`A/:282`）；无边框/圆角/遮挡。 |
| 7 | 布局占位与展开形态 | 全屏唯一子节点；无展开形态。 |
| 8 | 滚动语义 | 页面滚动由 DSH 网页自身（`overflow`/`scroll`）；壳层不接管滚动、不设 `overScrollMode`（未见实现）→ 过度滚动（overscroll glow/stretch）保持系统默认。 |
| 9 | 动效 | 壳层无动效；页面切换（console→DSH）是硬切换 `loadUrl`（`A/:1087`），无转场。 |
| 10 | 边界态 | 页面加载失败：`onReceivedError`/`onReceivedHttpError` → 失败对话框（`A/:226-239,357-379`）；内核未就绪：停在 console（`A/:287,382-393`）；旋转：`configChanges` 含 `orientation|screenSize` → 不重建、不重载（`X/:32`）；多语言：壳层文案中文硬编码（`A/:366-373` 等）。 |
| 11 | 无障碍 | WebView 内容的读屏由系统 WebView + 网页 a11y 决定（壳层未提供任何 `contentDescription`/自定义动作）；壳层自身无可见控件（除对话框）。 |
| 12 | 依据 | `E0` `A/:180-190,283-287,216-239`、`X/:31-32`；`E1` `D2/:17`（"界面 = dsh --profile web 的原生 Web UI"）。 |
| 13 | 反证 + 验收 | **反证**：若不设 `useWideViewPort/loadWithOverviewMode`，WebView 会按 980px 桌面宽度渲染再缩小 → 手机上看到"整页缩小"的桌面界面、文字不可读（Android WebView 经典问题）；若壳层再注入移动 CSS，就和上游/插件形成双份样式来源（本仓库选择不注入，界面完全是 DSH 的）。**验收**：`MOB-40` 手机打开后页面按 device-width 排版、无需双指缩放（真机）；`MOB-41` 桌面宽度（平板）不出现两套样式叠加（截图）。 |

#### `base.layout.host.back` — 返回键语义

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.host.back`。`MainActivity.onBackPressed()`（`A/:437-447`）。出现条件：任意时刻按返回键/返回手势。 |
| 2 | 网格与定位 | 无视觉。 |
| 3 | 命中区 | 系统返回手势区（系统提供，非本 App 绘制）。 |
| 4 | 手势 | 系统返回键/全面屏返回手势 → `onBackPressed`。 |
| 5 | 行为 | 优先级只有两级：① `web.canGoBack()` → `web.goBack()`（`A/:440-443`）；② 否则 `super.onBackPressed()` → 退出 Activity/App（`A/:446`）。**没有**「先关抽屉 / 先退键盘 / 先关浮层」的仲裁。 |
| 6 | 视觉状态 | 无。 |
| 7 | 布局占位与展开形态 | 无。 |
| 8 | 滚动语义 | 无。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | WebView 无历史（首次进入 DSH 首页）→ 直接退出 App（`A/:446`）；console 页有历史则先后退；模态对话框可见时：Android 系统会先关对话框（`setCancelable` 的才可关，`A/:373` 可取消、`A/:1427` 更新进度不可取消）——这部分由系统处理，NonCancelable 的进度框会吞掉返回键；抽屉/键盘等 Web 内状态对壳层不可见。 |
| 11 | 无障碍 | 系统返回语义；无自定义播报。 |
| 12 | 依据 | `E0` `A/:436-447`；对照桌面无返回栈：`ref/dsh-base.md` §0 第 6 条（`activePanelId` 覆盖式换面板，无历史）。 |
| 13 | 反证 + 验收 | **反证**：若照桌面"没有返回栈"处理，手机上按返回键会直接退出 App，用户丢掉正在看的会话；若只用 WebView 历史（本仓库做法），抽屉打开、键盘弹出时按返回会先触发页面后退（甚至退出 App），而不是"先关抽屉/先收键盘"——**这正是 myco 必须自定的顺序**。**验收**：`MOB-42` 抽屉打开时返回键先关抽屉（myco 目标行为，需拍板 §6 Q-MOB-04）；`MOB-43` 无历史时按返回不瞬间退出（二次确认或回首页）。 |

#### `base.layout.host.external-link` — 外链 / OAuth / window.open

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.host.external-link`。URL 分发逻辑（`A/:298-354,406-434`）+ `window.open` 捕获（`A/:247-278`）+ JS 桥（`A/:281,429-434`）。出现条件：页面发生导航/点链接/开新窗口。 |
| 2 | 网格与定位 | 无视觉（分发逻辑）；系统浏览器 Intent（`A/:346-348`）。 |
| 3 | 命中区 | 继承页面内链接的命中区（DSH 网页决定）；壳层不扩边。 |
| 4 | 手势 | 单击链接（WebView 拦截）→ `shouldOverrideUrlLoading`（`A/:206-214`）或 JS click 捕获（`A/:413-416`）；长按链接：未接管（系统 WebView 默认可能弹上下文菜单）。 |
| 5 | 行为 | ① 本机回环（`127.0.0.1/localhost/0.0.0.0`）→ 留在 WebView（`A/:301-316,319-322`）；② 其它 http(s) → 系统浏览器（`A/:332-354`）；③ `deepseek.com`/`/oauth` → 置 `refreshOnResume`，从浏览器回来自动 `web.loadUrl(dshUrl)` 刷新登录态（`A/:341-344,450-465`）；④ `window.open/target=_blank` → 隐藏 WebView 捕获 URL → 系统浏览器（`A/:247-278`）；⑤ 非 http(s)（`javascript:/intent:/file:`）一律拒绝（`A/:335-339`）。 |
| 6 | 视觉状态 | 无（离开 App）。 |
| 7 | 布局占位与展开形态 | 无。 |
| 8 | 滚动语义 | 无。 |
| 9 | 动效 | 无；从浏览器返回后 1200ms 延迟刷新（`A/:455-463`）。 |
| 10 | 边界态 | 无浏览器可用：Toast 显示完整 URL（`A/:349-352`）；OAuth 回跳（`http://127.0.0.1:<port>/oauth/callback`）由本机服务承接；深链：**没有 VIEW intent-filter**（`X/:33-36`）→ 外部 `dsh://`/https 链接唤不起 App；`javascript:` 链接被拒绝后页面可能无反应（`A/:336-339`）。 |
| 11 | 无障碍 | 无自定义（交系统浏览器）。 |
| 12 | 依据 | `E0` `A/:206-214,247-278,281,298-354,406-434,429-434,450-465`、`X/:33-36`。 |
| 13 | 反证 + 验收 | **反证**：若把所有链接都留在 WebView，OAuth 授权页（第三方域名）会在 WebView 内登录并被不少站点拒绝，登录不可用（注释 `A/:324-331` 说明这是账号登录能用的关键）；若把本机回跳也交给浏览器，token 可能丢在浏览器里、WebView 状态不同步。**验收**：`MOB-44` 账号登录全程可用（浏览器授权→回跳→WebView 自动刷新，真机）；`MOB-45` 点普通外链离开 App 且返回后 DSH 状态不丢（真机）。 |

#### `base.layout.host.navigation` — 壳内页面切换（console ↔ DSH ↔ 兜底）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.host.navigation`。启动/切换/兜底三条路径：`loadDshHome()`（`A/:382-393`）、`switchToDsh()`（`A/:1075-1101`）、回滚失败兜底回 console（`A/:1404-1411`）。 |
| 2 | 网格与定位 | 无视觉（整页替换）。 |
| 3 | 命中区 | 触发点：错误对话框「返回 DSH」按钮（`A/:369`）、探测成功自动切（`A/:1039-1061`）。 |
| 4 | 手势 | 无手势（自动/按钮触发）。 |
| 5 | 行为 | ① 探测到 127.0.0.1 端口就绪 → `switchToDsh(url)`（保留 `?token=`，`A/:1020-1036,1075-1092`）；② 内核未就绪 → console（`A/:382-390`）；③ 更新回滚失败 → 1500ms 后回 console（`A/:1404-1411`）。 |
| 6 | 视觉状态 | 无转场（`loadUrl` 硬切）。 |
| 7 | 布局占位与展开形态 | 无。 |
| 8 | 滚动语义 | 无。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 端口探测 300 轮 ×1s 超时 → 停在 console 并提示（`A/:1039-1061`）；内核崩溃 exit → 状态 STOPPED（`A/:998-1009`）但界面不回 console（只在日志显示）——⚠️ 用户会停在白屏/旧页面（未见自动回 console）。 |
| 11 | 无障碍 | 无。 |
| 12 | 依据 | `E0` `A/:382-393,1039-1101,1404-1411`。 |
| 13 | 反证 + 验收 | **反证**：若不等端口就绪就切 WebView，会先白屏/报错；若不保留 token，DSH 前端鉴权失败（`A/:1020-1022` 注释"token 必须保留"）。**验收**：`MOB-46` 冷启到 DSH 界面全程无白屏（真机录屏）；`MOB-47` 内核崩溃后用户能回到日志台（myco 目标行为）。 |

#### `base.layout.host.web-error` — 页面加载失败对话框

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.host.web-error`。`showWebLoadError()`（`A/:357-379`），由 `onReceivedError`（`A/:226-230`）与 `onReceivedHttpError`（`A/:233-239`）触发；`webErrorShown` 去重（同一次失败只弹一次，`A/:359-361`）。 |
| 2 | 网格与定位 | 原生 `AlertDialog`（系统居中模态）。 |
| 3 | 命中区 | 系统按钮（高度由系统主题决定，通常 ≥48dp）。 |
| 4 | 手势 | 点系统按钮；返回键可关（`setCancelable(true)`，`A/:373`）。 |
| 5 | 行为 | 「返回 DSH」→ `loadDshHome()`（`A/:369`）；「重试」→ `web.loadUrl(url)`（`A/:370-372`）。 |
| 6 | 视觉状态 | 系统 Material 对话框（`Theme.Material.NoActionBar` 下默认深色/浅色跟随系统）。 |
| 7 | 布局占位与展开形态 | 系统 modal（形态 C：浮层，不占位）。 |
| 8 | 滚动语义 | 无。 |
| 9 | 动效 | 系统对话框动画。 |
| 10 | 边界态 | URL 为空：显示"(未知地址)"（`A/:366`）；原因空：显示"未知"（`A/:367`）；`isFinishing()` 时不弹（`A/:359`）；多语言：中文硬编码。 |
| 11 | 无障碍 | 原生 AlertDialog 语义（标题/正文/按钮可读屏）。 |
| 12 | 依据 | `E0` `A/:357-379,226-239`。 |
| 13 | 反证 + 验收 | **反证**：若不提示，WebView 加载失败只表现为黑屏/白屏（`A/:282` 背景色 `#0d1117` → 看起来像黑屏，注释 `A/:193-194` 提到过同类问题），用户以为 App 卡死；若无「重试/返回」用户只能杀进程。**验收**：`MOB-48` 断网/端口不通时出现失败框且两按钮可用（真机）。 |

#### `base.layout.host.console` — 启动诊断日志台（console.html，13 个交互点：日志台 12 + Java 侧首屏加载 1）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.host.console`。自写单页 `console.html`（`C/:1-125`），App 首屏（`A/:287`）与兜底页（`A/:1404-1411`）。出现条件：启动/内核未就绪/回滚失败。 |
| 2 | 网格与定位 | flex 纵向：header（状态灯+状态+详情，`C/:16-24`）+ 进度条 3px（`C/:25-26,51`）+ `#log` 弹性滚动区（`C/:27-28`）+ footer 三按钮（`C/:32-36`）。`body{height:100vh; overflow:hidden}`（`C/:15`）。 |
| 3 | 命中区 | footer 三个 `button{flex:1; padding:9px 6px; font-size:12px}`（`C/:34-36`）→ 高度约 9+9+18=36px 量级（**未达 44**，与 mobile-adapt 的 44 策略不同；日志台是应急界面，未见达标）；`#hint` 全屏遮罩阻塞输入（`C/:37-40,58-59`）。 |
| 4 | 手势 | 单击三按钮（`C/:54-56`）；日志区竖向滚动（`C/:27` 原生滚动 + `-webkit-overflow-scrolling:touch`）；无长按/双击。 |
| 5 | 行为 | 「滚到底部」→ `log.scrollTop=1e9`（`C/:54`）；「复制日志」→ `copyLog()`（`C/:55,108-119`）；「重新加载」→ `location.reload()`（`C/:56`）；日志自动跟随：距底 <120px 才滚到底（`C/:86-88`，用户上翻即停）。 |
| 6 | 视觉状态 | 状态灯三色：wait 黄/warn、run 绿、err 红（`C/:18-21,100-106`）；日志行着色 err/warn/ok/dim/hi（`C/:30-31,71-77`）；按钮 `:active` 变底色（`C/:36`）；启动提示 `#hint` 覆盖层（`C/:58-59`）。 |
| 7 | 布局占位与展开形态 | 单页整屏，无展开；`#hint` 是覆盖层（不占位）。 |
| 8 | 滚动语义 | `#log` 独立滚动；自动跟随条件是「距底 <120px」而不是「永远跟随」（`C/:86`）→ 用户上翻阅读时不抢滚动。 |
| 9 | 动效 | 进度条 `transition:width .25s`（`C/:26`）。 |
| 10 | 边界态 | 页面重载：Java 侧保留 4000 行日志历史并分批重放（`A/:129-135,508-543`），重载不变空白；页面就绪前：Java 缓冲 JS 调用，`onPageFinished` 后放行（`A/:124-133,295`）；大字体：无适配（px 硬编码）；横屏：flex 自动分配。 |
| 11 | 无障碍 | 原生语义（header/button）；日志无 `aria-live`（未见实现）→ 读屏不会播报新日志；状态无 `aria-live`。 |
| 12 | 依据 | `E0` `C/:5,15,27,33-36,54-59,86-91,100-119`、`A/:124-135,287,295,508-543`；facts 命中 14 行。 |
| 13 | 反证 + 验收 | **反证**：手机本地跑内核没有 adb，若无日志台，启动失败/解压失败/Node 执行失败（SELinux exec）用户完全无信息、只能重装（`D2/:52-53,203-217` 整节就是为排查写的）；若日志无限追加且不能复制，崩溃现场无法带出。**验收**：`MOB-49` 首启解压进度可见、日志可复制（真机）；`MOB-50` 点「重新加载」后历史日志恢复（`A/:508-543` 的回归点）。 |

#### `base.layout.host.update-dialogs` — 内核热更新三件套对话框

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.host.update-dialogs`。三个原生 `AlertDialog`：更新提示（`A/:1211-1237`）、不可取消进度（`A/:1420-1438`）、失败提示（`A/:1461-1473`）。出现条件：启动检测到 npm 新版本且未被忽略（`A/:1180-1209`）/更新中/更新失败。 |
| 2 | 网格与定位 | 系统模态对话框（居中；进度框无按钮）。 |
| 3 | 命中区 | 系统按钮：提示框 3 个（`A/:1226-1231`）、失败框 1 个（`A/:1467`）；进度框无按钮、点外部不关（`A/:1429`）。 |
| 4 | 手势 | 点按钮；提示框/失败框返回键可关（`setCancelable(true)`，`A/:1232,1468`）；进度框不可取消（`setCancelable(false)`，`A/:1427`）。 |
| 5 | 行为 | 「立即更新」→ `startUpdateFlow()`（`A/:1226,1241-1273`）；「稍后」→ 下次启动再提示（`A/:1227`）；「此版本不再提示」→ 持久化忽略版本（`A/:1228-1231`）；进度框按阶段刷新「phase（pct%）+ 保持常亮提示」（`A/:1440-1450`）；失败 → 回滚后单按钮提示（`A/:1401-1403,1461-1473`）。 |
| 6 | 视觉状态 | 系统 Material 对话框；进度框文案持续更新（`setMessage`）。 |
| 7 | 布局占位与展开形态 | 系统 modal（形态 C）。 |
| 8 | 滚动语义 | 无。 |
| 9 | 动效 | 系统对话框动画。 |
| 10 | 边界态 | 已忽略版本不再提示（`A/:1191-1194`）；更新器未就绪则不提示（`A/:1196-1200`）；检查失败不弹窗（`A/:1182-1186`）；一次启动只弹一次（`A/:1213`）；`isFinishing()` 不弹（`A/:1215`）；多语言：中文硬编码。 |
| 11 | 无障碍 | 原生 AlertDialog；进度框只更新 message 文本，**无 `aria-live`/无进度语义**（读屏不会自动播报进度）。 |
| 12 | 依据 | `E0` `A/:1180-1273,1420-1473`；`E1` `docs/ANDROID-UPDATE-DESIGN.md:32-43,187-193`（设计文档）。 |
| 13 | 反证 + 验收 | **反证**：更新会重启内核（期间界面不可用），必须让用户**主动确认**并把不可取消的进度框立起来——否则用户以为 App 崩了去杀进程，内核切换中断（本仓库为此写了 `recoverInterruptedUpdate` 自愈，`A/:1483-1507`）；「此版本不再提示」必须持久化，否则每次启动都弹（`A/:1191-1194`）。**验收**：`MOB-51` 三按钮语义正确、忽略后不再弹（真机/单测）；`MOB-52` 进度框不可取消且更新期间界面可见进度（真机录屏）。 |

#### `base.layout.host.toast` — Toast 轻反馈

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.host.toast`。`toast()` 封装（`A/:395-399`）+ 三处调用：无浏览器（`A/:351`）、更新完成（`A/:1362`）。 |
| 2 | 网格与定位 | 系统 Toast（屏幕底部短时浮层）。 |
| 3 | 命中区 | 不可点（系统 Toast）。 |
| 4 | 手势 | 无。 |
| 5 | 行为 | 显示 `LENGTH_LONG` 文案；无确认/撤销。 |
| 6 | 视觉状态 | 系统默认。 |
| 7 | 布局占位与展开形态 | 浮层不占位（G-01 同向）。 |
| 8 | 滚动语义 | 无。 |
| 9 | 动效 | 系统淡入淡出。 |
| 10 | 边界态 | Toast 在 API 30+ 被限制（后台不可弹）——本仓库未处理（未见实现）；多语言：中文硬编码。 |
| 11 | 无障碍 | 系统 Toast 会被 TalkBack 播报（平台行为，E1）。 |
| 12 | 依据 | `E0` `A/:349-352,395-399,1360-1366`。 |
| 13 | 反证 + 验收 | **反证**：外链打不开时若纯静默，用户不知道发生了什么（`A/:350-351` 给出完整 URL 让用户手动访问）；更新完成若无反馈，用户不确定是否已生效。**验收**：`MOB-53` 无浏览器时能看到完整 URL（真机）。 |

#### `base.layout.host.window` — 窗口/系统栏/安全区/旋转/常亮

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.host.window`。窗口层配置：`FLAG_KEEP_SCREEN_ON`（`A/:145`）、`Theme.Material.NoActionBar`（`X/:26`）、`targetSdk 28`（`X/:7`）、`configChanges`（`X/:32`）、无任何 edge-to-edge/insets 调用（`A/:283-285`）。 |
| 2 | 网格与定位 | 内容区 = 窗口减去系统栏（非 edge-to-edge 的默认行为）；未见 `WindowInsets`/`setDecorFitsSystemWindows`/`layoutInDisplayCutoutMode`（全文无）。 |
| 3 | 命中区 | n/a（系统栏区域不归 App）。 |
| 4 | 手势 | 系统手势导航条（系统提供）；App 不接管边缘手势。 |
| 5 | 行为 | 旋转：`configChanges=orientation|screenSize|screenLayout|keyboardHidden|uiMode` → 不重建 Activity、WebView 不重载（`X/:32`）；常亮：启动/更新期间保持（`A/:145`）。 |
| 6 | 视觉状态 | 无 ActionBar；状态栏颜色/图标由系统主题决定（未自定义）；背景 `#0d1117`（`A/:282`）。 |
| 7 | 布局占位与展开形态 | 非 edge-to-edge → 系统栏占位（用户看到状态栏黑边/导航栏黑边），内容不侵入。 |
| 8 | 滚动语义 | 无。 |
| 9 | 动效 | 无（旋转不重建 = 无重建闪烁）。 |
| 10 | 边界态 | 挖孔屏：未设 cutout 模式，走平台默认（E1：非 edge-to-edge 时内容不绘制进挖孔区）；手势条：未设 `navigationBarColor`/contrast，手势条叠在系统导航栏区；横屏：仅靠 `configChanges` 不重载，无横屏专用布局；大字号：系统字体缩放不影响 WebView 内网页（网页字号由 DSH 自己控制）。 |
| 11 | 无障碍 | 系统栏语义由平台管理；App 未自定义。 |
| 12 | 依据 | `E0` `A/:145,282-285`、`X/:7,24-32`；`E1` `D2/:226`（targetSdk 28 的原因）。 |
| 13 | 反证 + 验收 | **反证**：若改成 edge-to-edge（Android 15 默认强制），WebView 内容会伸到状态栏/手势条下，导航按钮与输入框被系统栏压住（本仓库用"不 edge-to-edge"回避）；若旋转重建 Activity，WebView 会重载、丢失会话视图位置。**验收**：`MOB-54` 旋转后 DSH 页面不重载、滚动位置保留（真机）；`MOB-55` 挖孔/手势条不遮挡输入区（真机多机型）。 |

#### `base.layout.host.ime` — 软键盘避让（**未见实现**）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.host.ime`。审计点：`X/:28-37` Activity 声明 + `A/:180-296` WebView 装配。**明确"未见实现"**：Manifest 无 `android:windowSoftInputMode`；代码无 `WindowInsets`、无 `visualViewport` JS 桥、无 `onConfigurationChanged` 键盘处理。 |
| 2 | 网格与定位 | n/a。 |
| 3 | 命中区 | n/a。 |
| 4 | 手势 | n/a。 |
| 5 | 行为 | 键盘弹出后的实际表现由系统默认（`adjustUnspecified`）+ WebView 自身决定，**代码里没有任何显式策略**。 |
| 6 | 视觉状态 | n/a。 |
| 7 | 布局占位与展开形态 | n/a。 |
| 8 | 滚动语义 | 无「键盘弹出时滚到输入框/保持列表底部」逻辑（未见实现）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 全屏 WebView + 无 `adjustResize` 声明：键盘可能覆盖输入区（E2 风险，需真机，§6 Q-MOB-02）；日志台 `body{height:100vh; overflow:hidden}`（`C/:15`）在键盘弹出时也不会让 footer 上移（`100vh` 不含键盘）；横屏键盘更矮。 |
| 11 | 无障碍 | n/a。 |
| 12 | 依据 | `E0` `X/:28-37`（无属性）、`A/:180-296`（无 insets/ime 代码）、`C/:15`；对照 mobile-adapt 侧的替代方案 `M/:1443-1465`（`visualViewport` 重测）、`M/:199-204`（`100dvh`）。 |
| 13 | 反证 + 验收 | **反证**：DSH 的输入区固定在底部，键盘弹出若窗口不 resize，输入框会被键盘盖住 → 用户打字看不见自己打的内容、也点不到发送钮。mobile-adapt 在 Web 侧只能靠 `dvh/visualViewport` 缓解（`M/:199-204,1443-1465`），Android 壳这边连这层都没有。**myco 必须显式处理**：Compose 用 `imePadding()`/`WindowInsets.ime`，且"回到底部"按钮随键盘上移（G-01 浮层也需抬升）。**验收**：`MOB-56` 键盘弹出时输入区完整可见、发送钮不被遮（真机：Gboard 中文输入法）；`MOB-57` 键盘收起后列表不跳位（真机录屏）。 |

#### `base.layout.host.missing-native` — WebView 未接管的原生能力（文件选择/下载/剪贴板/长按菜单）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.host.missing-native`。一组「未见实现」：<br>① **文件选择器**：`setWebChromeClient`（`A/:241`）未 override `onShowFileChooser` → `<input type=file>` 点击无反应；<br>② **下载**：`setWebViewClient`（`A/:204`）后未 `setDownloadListener` → 下载链接无反应；<br>③ **剪贴板**：唯一 JS 桥 `DshMobileBridge` 只暴露 `openExternal`（`A/:281,429-434`）；全文无 `ClipboardManager`；页面内复制依赖网页自身（日志台用 `document.execCommand('copy')`，`C/:108-119`）；<br>④ **长按系统菜单**：无 `setOnLongClickListener`/`setOnCreateContextMenuListener`（`A/:180-296`）→ WebView 默认长按文本选择/上下文菜单照旧，未定制。 |
| 2 | 网格与定位 | n/a（能力缺口，无 UI 形态）。 |
| 3 | 命中区 | 缺口导致「点文件按钮无反应」——命中区存在但无响应。 |
| 4 | 手势 | 长按：系统默认（可能弹文本选择工具条）。 |
| 5 | 行为 | 均无 App 侧实现。 |
| 6 | 视觉状态 | n/a。 |
| 7 | 布局占位与展开形态 | n/a。 |
| 8 | 滚动语义 | n/a。 |
| 9 | 动效 | n/a。 |
| 10 | 边界态 | 附件上传：unknown（E2，需真机确认是"无反应"还是"报错"）；下载：unknown；非 ASCII 文本输入：MCP 工具描述声称剪贴板+粘贴回退（`android/android-mcp-server.js:188`），但那是**模型操作别的 App** 的路径，与 DSH 页面内输入无关；长按文本：系统默认。 |
| 11 | 无障碍 | 缺口本身会被读屏表现为"按钮无响应"。 |
| 12 | 依据 | `E0` `A/:204,241,281,429-434`、`C/:108-119`；对照：mobile-adapt 也不需要文件选择（Web 浏览器原生提供）。 |
| 13 | 反证 + 验收 | **反证**：myco 是原生 App，附件选择/预览是核心功能（`10-panels.md` 里 `attachment` 是 P0 高优），若照 mobile-android 的"不接管"做法，用户点附件按钮将毫无反应；剪贴板若不接，`chat.msg` 的"复制"按钮在 Android 上不可用。**验收**：`MOB-58` 附件选择走系统 picker 且能上传（真机）；`MOB-59` 复制消息后系统剪贴板有内容（真机）；`MOB-60` 长按正文出现"选择/复制"（先拍板保留还是禁用，§6 Q-MOB-08）。 |

#### `base.layout.host.notification` — 前台服务常驻通知

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `base.layout.host.notification`。`DshService`（`S/:11-49`）+ 内核就绪时 `startForegroundService`（`A/:1078-1084`）。出现条件：DSH 内核启动成功（`switchToDsh`）。 |
| 2 | 网格与定位 | 系统通知栏常驻（非 App 内 UI）。 |
| 3 | 命中区 | 通知点击：**未设置 `contentIntent`**（`S/:30-34` 只设 title/text/icon/ongoing）→ 点通知不回到 App（缺口）。 |
| 4 | 手势 | 通知栏操作（系统）。 |
| 5 | 行为 | `START_STICKY`（`S/:41-43`）让系统重启服务；`IMPORTANCE_LOW` 静默（`S/:22-26`）。 |
| 6 | 视觉状态 | 通知「DSH Mobile 运行中 / DSH 内核在后台保持运行」+ 系统图标（`S/:30-34`）。 |
| 7 | 布局占位与展开形态 | 系统通知（不占 App 布局）。 |
| 8 | 滚动语义 | 无。 |
| 9 | 动效 | 系统。 |
| 10 | 边界态 | API<26 走无 channel 构造（`S/:27-29`）；`startForeground` 失败只 log（`S/:35-37`）→ 无通知时后台仍可能被杀；Android 13+ 通知权限（`POST_NOTIFICATIONS`）**未在 Manifest 声明**（`X/:9-15` 只有 INTERNET/NETWORK/FOREGROUND_SERVICE/WAKE_LOCK/Shizuku）→ 在 API 33+ 上通知可能不显示（未见处理）。 |
| 11 | 无障碍 | 系统通知播报。 |
| 12 | 依据 | `E0` `S/:11-49`、`A/:1078-1084`、`X/:9-15`。 |
| 13 | 反证 + 验收 | **反证**：内核跑在 App 主进程里，退到后台若无前台服务/常驻通知，系统会很快回收进程 → 正在生成的回答被中断、会话状态丢失；通知若无 `contentIntent`，用户从通知栏点不回来（只能从桌面图标进）。**验收**：`MOB-61` 切后台 5 分钟回来会话仍在生成/已完成的回答还在（真机）；`MOB-62` Android 13+ 通知可见（若 myco 要前台服务）。 |

---
## 4. Q-03 直接答案：手机端命中区策略

> Q-03（`open-questions.md:22-31`）问的是：DSH 折叠行视觉高只有 24px，手机要 ≥44dp，怎么放大、扩边会不会重叠、代价多少。
> 本节只给**两个仓库的 E0 答案 + 几何推论**；myco 的最终选择仍待拍板（§6 Q-MOB-03/06/11）。

### 4.1 两个仓库实际用的策略（E0）

| 策略 | 用在哪 | 具体像素 / 手法 | 依据 |
|---|---|---|---|
| **S1 视觉抬到 44（主策略）** | 抽屉导航行、设置导航 cell、菜单行、对话框按钮/输入、输入工具行按钮、头部动作/tab/面包屑、统计胶囊/关闭钮、轨迹工具栏 | `min-height:44px`（行/按钮）、`min-width+min-height:44px`（图标按钮）、`height:44px`（关闭钮/tab 容器） | `M/:302-310,768-775,524-530,894-905,907-920,477-481,393-398,434-443,571-593,665-679,1006-1025` |
| **S2 伪元素透明扩边（补充）** | 只有"视觉尺寸不能改"的微小内联控件 | 开关/复选框 `::after{inset:-12px -8px}`；16px 问号 `::after{inset:-14px}` | `M/:934-945,955-959` |
| **S3 padding 撑高（等价 S1，保持文字行高）** | 头部面包屑（20px 文字行 → 44px 盒） | `min-height:44px; padding:12px 0; box-sizing:border-box`，保持 `inline-block` 让上游 ellipsis 生效 | `M/:399-407` |
| **S4 改布局重排（不是扩边）** | 字号 stepper（两个 17×12 箭头中心距 14px） | 从 `absolute` 竖列改为 `static` 横排：`44×44 ×2`、`gap:2px`、强制 `opacity:1`；`expander` 44×44 | `M/:802-865` |
| **S5 什么都不做** | **DSH 聊天内容区全部**：思考行折叠态 24px+delta、消息操作条 28×28、工具卡、轮次导航 | 无任何规则命中这些上游组件 | `M/` 全文 grep 无 `ReasoningRow`/`MessageIconActions`/`TurnNavigator`/`ChatGroup` 选择器 |

**结论 1（可直接抄）**：手机端命中区的**默认动作是 S1（视觉直接 44px）**，透明扩边 S2 只留给"改视觉会破坏控件语义"的开关类。**这条与 Q-03 候选②相反**——open-questions 里的"候选① 视觉不变只扩边"在这个实现里是少数派（只有 3 类控件），因为作者的选择是"宁可视觉变松，也不要看不清命中范围"。

### 4.2 扩边会不会与相邻命中区重叠？——**几何上会，且本仓库没有防**

**S2 的实际矩形（E0 推算）**：上游开关 36×20 → 扩后 52×44（`M/:940-945`；开关桌面尺寸见 `ref/dsh-base.md` §0 第 10 条）。上下各侵入 12px。设置行被本插件抬到 `min-height:44px`（`M/:851-855`）且行间无额外间距声明 → **相邻两行的命中盒各越界 12px，重叠带 24px**，归谁不可判定。
代码里这块只有 `position:relative` + `::after`（`M/:936-945`），**没有 `z-index`、没有 `pointer-events`、没有行距补偿** → 不是"设计成不重叠"，而是"没考虑"。

**作者唯一正面处理相邻冲突的地方是字号 stepper**（S4）：`M/:802-808` 注释与 `R1/:54-57` 明确写"两个箭头中心仅相距 14px，想调大却触发调小"，解法是**重排成并排 44 且 gap 2px**，而不是扩边。→ **结论 2：相邻可点元素中心距 <44px 时，扩边无解，必须重排/换形态。**

**隔离带的实际取值（全仓库）**：`gap:4px`（headerActions `M/:385-387`、设置 navList `M/:761-767`、modes `M/:469-471`）、`gap:6px`（composer tools `M/:464-468`、trailing `M/:472-476`）、`gap:8px`（composer row、dock、`margin-left:8px` `M/:459-463,555-560,388-391`）、`gap:32px`（头部 tab `M/:432`）。**没有任何"最小 8dp 无命中带"的全局规则**（Q-03 建议里的②在本仓库不存在）。

### 4.3 代价（可量化）

| 代价 | 具体数字 | 依据 |
|---|---|---|
| 行高变高、一屏行数变少 | 折叠行 24px（桌面）→ 若改用 S1 变 44px：单行 +20px；按过程组行距 6px（`ref/dsh-chat.md` §0 第 8 条）计，行节距 30px→50px，**同样一屏高度可容纳的过程行数减少约 40%**（算术推算，非事实） | `M/:302-310` vs `ref/dsh-base.md` §2.1 字段 2/3（24px+delta）、`ref/dsh-chat.md` §0 #8（组内 6px） |
| 头部变高 | 汉堡 44 + 头部 tab 44 + 面包屑 padding 12/12 叠加 → 头部至少 2 行（`M/:376-443`）；插件自己也要靠"标题行可换行"容纳 | `M/:380-384` |
| 视觉形态变化 | 字号 stepper 从"浮在行右的上下箭头"变成"行内并排两个方按钮"（`M/:813-865`）；视觉密度与桌面明显不同 | `M/:829` 注释"图标视觉尺寸不变，只放大可点区域" |
| 命中区互相侵占 | S2 的 24px 重叠带（4.2）；用户误触后表现为"点这个却触发了那个"（stepper 的 issue #2 就是同一症状） | `M/:802-808` |
| 遮挡/溢出 | 对话框控件全部 44 后对话框变高，小屏可能溢出（插件未联动 `max-height`，`M/:894-905`）；320px 工具行可能仍需裁切（`M/:458-505`） | `M/:894-905,458-505` |

### 4.4 对 Q-03 的结论（给 myco 的输入）

1. **候选①（视觉不变、上下各扩 10dp）与 DSH 过程组行距不兼容**：
   思考行折叠态视觉高 `24px+delta`（`ref/dsh-base.md` §2.1 字段 2），过程组成员行距 6px（`ref/dsh-chat.md` §0 第 8 条）。
   若相邻两行各扩 e，要求 `2e ≤ 6px` → e=3px，命中高只有 **30px**，达不到 44dp；要达 44dp 需 e=10px，则相邻两行**必然重叠 14px**。
   → **Q-03 候选① 只在"行距 ≥20px"时成立**；桌面 DSH 的 6px 行距下，24px 的折叠行不能靠透明扩边达到 44dp。
2. **候选②（行高提到 44dp）在两个参考实现里是默认解**（S1，`M/:302-310` 等），代价是过程行密度 −40%（4.3）。它同时消掉 G-02 的行高稳定性问题：44dp 固定高 + `contain:size layout` 可同时满足。
3. **候选③（只给图标+标题区 44dp）在参考实现里没有例子**；最近的是 S2（开关/问号），但那是"控件本体小、外扩不改变视觉"，不是"缩窄可点宽度"。
4. **无论选哪个，必须新增一条本仓库没有的硬规则：相邻命中盒不相交**。建议判定式：`中心距 ≥ (a/2 + b/2)` 且留 8dp 无命中带；实现上等价于"扩边量 e = min(10dp, 相邻间距/2)"。否则就是 issue #2 的翻版（`M/:802-808`）。
5. **思考行的特殊反证**：若把思考行视觉高直接做成 44dp 而正文/工具行不变，过程组一屏从 ~22 行降到 ~13 行（算术），"过程可折叠、不抢正文"的桌面意图会被反向放大为"过程占满屏"；若维持 24dp 只扩边，则 44dp 不可达且重叠。**这是 Q-03 唯一没有现成答案的格子，必须拍板**（§6 Q-MOB-03）。

---

## 5. 排除清单

### 5.1 mobile-adapt（`dsh-mobile-adapt.facts.csv`：E0 41 / excluded 0）

**没有 excluded 行**：官方 scan 的 41 条命中全部是可归节点的事实（regex 命中的 `aria.role`/`a11y.expanded` 是插件自己的 ARIA 规则，`container.scroll` 是真实滚动容器，`jsx.*` 是真实事件监听）。逐类说明：

| 类别 | 处理 |
|---|---|
| `aria.role` 14 条（`M/:216,370,371,895,898,902,903,936,937,940,941,1315,1318,1327`） | 全部 E0 → 落到 `base.layout.touch-feedback`(1)、`base.layout.mobile-nav.hamburger`(1)、`base.layout.mobile-nav.scrim`(1)、`base.primitives.touch-floor.dialog-controls`(4)、`settings.general.switch`(4)、`base.layout.mobile-nav.auto-close`(3) |
| `container.scroll` 7 条（`M/:285,634,749,750,774,794,1080`） | 全部 E0 → 抽屉/统计面板/设置导航/设置内容区/轨迹详情 的滚动语义 |
| `jsx.ontouch` 6 / `jsx.onwheel` 3 条（`M/:1417-1438`） | 全部 E0 → `composer.stats.panel.edge-lock`（G-04 手机端实现） |
| `disclosure` 4 条（`M/:1271,1293,1345,1350`） | 全部 E0 → 抽屉开合（`onToggle`） |
| `jsx.onclick` 4 条（`M/:1345,1350,1618,1639`） | 全部 E0 → 汉堡/遮罩/统计胶囊/统计关闭 |
| `a11y.expanded` 3 条（`M/:602,617,1616`） | 全部 E0 → 统计胶囊展开态 |

**不在分母里的文件（非 UI / 非语言集）**：`tools/probe.py`、`tools/regress.py`、`tools/sync_check.py`、`tools/test_mobile.py`、`tools/test_interactions.py`、`tools/unit.test.mjs`（测试/探针脚本，不产生产品 UI）；`install.ps1`、`install.sh`（安装脚本，无交互界面）；`package.json`、`cordis.patch.yml`、`.gitignore`、`LICENSE`（元数据）；`README.md`（E1 文档）。这些文件里 `onClick`/`touch` 等词可能出现在测试代码中，官方 scan 默认语言集只含 `ts/tsx/js/jsx/kts/kt/css`，`.py/.mjs/.ps1/.sh` 未进分母——若将来扩分母，它们应全部 `excluded`（理由：测试/安装/文档非产品 UI）。

**`css.*` 结构性属性分母 = 0**：本仓库没有独立 `.css/.scss/.less` 文件（样式在 `lib/client.js:169-1128` 的模板串里），`ui_audit.py` 的 `CSS_PATTERNS` 只对 css 后缀文件生效（`tools/ui-audit/ui_audit.py:106,60-66`）→ `css.cursor/focus/sticky/contain/overflow` **0 条**。这些结构性含义已由节点字段 2/3/8 与 §2 表覆盖（如 `settings.general.switch` 的伪元素扩边、`base.layout.mobile-nav.drawer` 的 `overscroll-behavior`）。

### 5.2 mobile-android（`dsh-mobile-android.facts.csv`：E0 75 / excluded 7）

**7 条 excluded（逐条理由）**：

| kind | 位置 | 排除理由 |
|---|---|---|
| `android.webview` | `MainActivity.java:242` | `onConsoleMessage` 只把 JS console 转 logcat，不产生任何用户可见 UI（诊断通道） |
| `android.webview` | `MainActivity.java:508` | `onConsolePageReady` 是日志重放通道（把缓冲的日志/状态刷进 console 页），不是用户交互点 |
| `android.webview` | `MainActivity.java:523` | 日志分批 `evaluateJavascript` 重放（性能实现），无交互 |
| `android.webview` | `MainActivity.java:295` | 8s 兜底触发 `fallbackPageReady`（页面就绪兜底），无交互 |
| `android.webview` | `MainActivity.java:638` | `abortLlm` 由 **MCP 工具路径**触发、程序化点击 DSH「停止生成」按钮；不是用户直接操作 UI（是"模型控制界面"） |
| `android.webview` | `MainActivity.java:671` | `llmBusyState` 同上，只查询「停止生成」按钮是否存在 |
| `android.click` | `MainActivity.java:621` | `ShizukuShell.requestPermission` 弹的是**系统授权对话框**（系统 UI），不属本 App 界面 |

**不在分母里的文件（按职责，全部非 UI）**：

| 分组 | 文件 | 排除理由 |
|---|---|---|
| 构建/组装 | `assemble-runtime.mjs`(469)、`rebuild-assets.ps1`、`build-apk.ps1`、`elf-deps.mjs`、`elf-inspect.mjs`、`termux-resolve.mjs`、`asar-extract.mjs`、`extract-deb.py`、`make-icon.py` | 构建期工具：组装 Node/bash 运行时、解 ELF 依赖、打 APK、生成图标；无运行时 UI |
| 热更新器 | `mobile-updater/update-runner.mjs`(1507)、`patches.mjs`(594)、`semver.mjs`、`tar.mjs` | 后台 Node 脚本：下载 npm、打 Android 平台补丁、原子切换内核、JSONL 进度输出；其进度由 Java 侧 AlertDialog 呈现（已归 `base.layout.host.update-dialogs`） |
| 安卓工具箱 | `android/android-cli.js`、`android/android-mcp-server.js`、`AndroidBridge.java`、`ShizukuShell.java`、`UpdateChecker.java`、`UpdateManager.java` | 给模型用的 MCP/CLI 工具与网络/进程实现（截图/tap/swipe/shell/更新检查），不是 App 界面 |
| 平台覆盖层 | `android/android-overlay.yml` | profile patch：禁 office-to-pdf、注册 MCP server；无 UI |
| 文档/许可/图标 | `README.md`、`docs/*.md`、`THIRD_PARTY.md`、`LICENSE`、`res/mipmap-*/*.png`、`.gitignore`、`.gitattributes` | 文档与资源，非交互 |
| 第三方 | `sharpfix/*.tgz`、`mobile-updater/assets/*`（与 `android/assets/*` 同内容副本）、`shim/*.js` | 第三方 WASM 包/随包资源副本/Node 内置模块 shim；**且重复副本不重复计点** |
| 官方 scan 已扫的 6 个 `.js` | `android/android-cli.js`、`android/android-mcp-server.js`、`shim/node-addon-require-builtin-index.js`、`mobile-updater/assets/android-cli.js`、`mobile-updater/assets/android-mcp-server.js`、`mobile-updater/assets/node-addon-require-builtin-index.js` | 默认 language set 含 `.js`，实际扫描 `files=6`、**findings=0**（这些是 CLI/MCP/shim，无 JSX/交互命中）；已在 §1.3 说明 |

**`css.*` 分母 = 0**：`.cshtml?` 没有——本仓库无 `.css/.scss/.less` 文件；`console.html` 的样式在 `<style>` 内联块里（`C/:7-41`），`ui_audit.py` 只对 css 后缀生效 → `css.*` 0 条。日志台内联样式里的结构性事实（`footer` 安全区、`#log` 滚动容器、`body` 高度）已作为 `html.view/html.scroll` 行进事实库。

**官方 scan 的分母缺口（必须记录）**：默认语言集不含 `.java/.xml/.html`，官方 scan 对 android 仓库输出 **0 条**。若直接用这份 0 条分母跑门禁，会得到"uncovered=0"的假通过。本路采用官方 scan + 手工枚举合并（§1.3），`_ws/out/findings-mobile-android-raw-scan.csv` 保留原始 0 行输出以便复核。

---

## 6. 待确认（E2 候选，编号 Q-MOB-01 起）

> 规则同 `README.md` §0：这里只放"源码没写清 / 两种做法都说得通 / 需要真机或用户拍板"的条目，**不得自行选择后写进 spec**。

**Q-MOB-01 手机页面要不要 `viewport-fit=cover`？**
现状：DSH 入口 meta 是 `width=device-width, initial-scale=1`，**无 `viewport-fit=cover`**（`dsh apps/web/index.html:5`）；DSH 客户端全仓库无 `env(safe-area-inset-*)`（本次 grep 0 命中）。而 mobile-adapt 的抽屉/汉堡/输入区安全区规则依赖 `env()`（`M/:277-278,316-317,503-505`）。
候选：① 改 DSH 入口 meta（动上游）；② 宿主/插件在运行时注入 `<meta name="viewport" content="...,viewport-fit=cover">`；③ 手机端不要 `env()` 安全区，改用固定 padding。
影响：刘海屏/手势条避让。myco 是原生 Compose，不存在这个 meta 问题，但"安全区由谁负责"要一致回答。

**Q-MOB-02 键盘弹出时窗口/输入区怎么动？**
现状：Android 壳无 `windowSoftInputMode`（`X/:28`）、无 insets、无 visualViewport 桥（`A/:180-296`）；日志台 `body{height:100vh;overflow:hidden}` 也不会随键盘上移（`C/:15`）。mobile-adapt 只用 `100dvh` + 统计面板重测（`M/:199-204,1443-1465`），**输入区本身没有键盘避让实现**。
候选：① `adjustResize` + 列表自动贴底；② `adjustPan`（不推荐，会顶整页）；③ Compose `imePadding()` + 输入区固定底部、列表底部 padding 随 ime 变化；④ 键盘透明浮层（Android 11+ `setDecorFitsSystemWindows(false)` + `WindowInsets.ime` 动画跟随）。
需真机：`MOB-56/57`。

**Q-MOB-03 思考行（24px 折叠态）的命中区到底怎么给？（Q-03 的真正难点）**
现状：两个仓库都**没做**（`M/` 无 ReasoningRow 选择器；android 是原生 Web UI）。
几何：24px 视觉 + 过程组 6px 行距（§4.4）→ 透明扩边到 44dp 必重叠 14px；视觉抬到 44dp 则过程行密度 −40%。
候选：① 只把"标题+箭头"做成 44dp 宽的可点按钮（摘要区不响应），行高不变；② 行高 24dp 不变、命中盒 30dp（扩到行距边界），接受 <44dp；③ 行高 44dp；④ 改过程组行距到 20dp + 扩边到 44dp（密度代价最大）；⑤ 长按整行出"展开"操作（改变交互语义）。
这是 must-answer，因为 G-02（定高）与 Q-03（44dp）在这里正面冲突。

**Q-MOB-04 返回键/返回手势的优先级顺序？**
现状：android 壳只有两级：WebView 历史 → 退出 App（`A/:440-446`）；Web 端无返回栈（`ref/dsh-base.md` §0 第 6 条）。
候选顺序（需拍板）：① 先关最上层浮层（sheet/dialog/键盘）→ ② 再关抽屉 → ③ 再退页面/面板 → ④ 二次确认退出。每级是否都要"再按一次确认"？

**Q-MOB-05 抽屉要不要侧滑手势？**
现状：`R1/:6` 声称 "swipe drawer"，但代码只有点击类关闭（汉堡/遮罩/Esc/自动收），**无侧滑实现**（`M/:1269-1353` 无 touch 监听）。
候选：① 只点击（与现实现一致）；② 加边缘侧滑（`pointer` 手势 + 与横向滚动面板冲突仲裁）；③ 加"抽屉内容区域右滑关闭"。
若加侧滑，必须回答"与横向滚动的设置导航条（`M/:738-753`）冲突时谁赢"。

**Q-MOB-06 全量 44dp 抬升的密度代价能否接受？**
现状：mobile-adapt 的选择是"菜单/导航/对话框全部 44"（`M/:524-538,770,894-905`），一屏信息量明显低于桌面。
候选：① 全量 44（参考实现做法）；② 列表行 44、次级操作 40/36（折中）；③ 保持 28–36 + 透明扩边（需隔离带，见 §4.4）。
影响所有面板的行级密度；需与 Q-MOB-03 一起拍。

**Q-MOB-07 附件选择 / 下载 / 剪贴板由谁提供？**
现状：android 壳全部未接管（`A/:204,241,281`）；`10-panels.md` 的 `attachment` 是 P0 高优。
候选：① 系统 Photo Picker/Document Picker + 分享/下载到 Downloads；② 应用内自绘文件选择 sheet；③ 复制走系统 ClipboardManager + Snackbar 提示。
需回答"长按消息是否直接复制/选择"。

**Q-MOB-08 长按文本选择 / 系统上下文菜单：保留还是接管？**
现状：android 壳未接管（`A/:180-296` 无长按监听）→ WebView 默认文本选择/上下文菜单；mobile-adapt 也没有自定义长按（`M/` 无 `onContextMenu`）。
候选：① 保留系统选择菜单（与消息操作条可能重复）；② 禁用选择、改用自定义操作条；③ 只对正文允许选择。

**Q-MOB-09 要不要深链？**
现状：Manifest 只有 MAIN/LAUNCHER，无 VIEW filter（`X/:33-36`）→ 外部链接唤不起 App。
候选：① 不做；② `dsh://` scheme；③ App Links（https + assetlinks）。

**Q-MOB-10 hover-only 控件在 myco 的常显清单？**
现状：mobile-adapt 只强制了字号箭头常显（`M/:819-827`）；DSH 桌面自身已对"消息操作条"做了 `@media (hover:hover)` → 触摸设备常驻（`ref/dsh-chat.md` §0 第 7 条）。
待确认：还有哪些"桌面 hover 才出现"的 UI（tooltip、轮次导航 hover 预览、菜单 hover 态）在 myco 触摸端要常显/改点击/删掉？轮次导航在 ≤900px 桌面就不渲染（`ref/dsh-chat.md` §0 #6），手机端无此问题。

**Q-MOB-11 字号 stepper 的视觉形态能否改成并排两个方按钮？**
现状：参考实现 S4（`M/:813-865`）把 absolute 上下箭头改成静态横排 44×44 + 2px gap；视觉变化明显。
候选：① 抄 S4；② 保留上下箭头但把行拉高到 88dp 给两个 44 命中（很丑）；③ 改成"− 数值 +"三段式（Compose 常见）。

**Q-MOB-12 myco 自己要不要 edge-to-edge？**
现状：android 壳**不 edge-to-edge**（`X/:7,26`，targetSdk 28）→ 系统栏自动留边、无挖孔/手势条处理；代价是状态栏+导航栏黑边。
候选：① 跟壳一样不 edge-to-edge（省事、无避让问题）；② edge-to-edge + `WindowInsets` 全量 padding（现代做法，视频/列表可全屏）；③ 仅输入区/列表做 inset，内容区 edge-to-edge。

**Q-MOB-13 `aria-modal=true` 但无遮罩的语义是否可接受？**
现状：统计报表 `role=dialog aria-modal=true` 但**无 scrim**（`M/:1626-1631`，对比抽屉遮罩 `M/:346-366`）→ 读屏认为背景不可交互，实际可以点外部关闭。
候选：① 去掉 `aria-modal`（普通 popover 语义）；② 加真遮罩（点击关闭 + 焦点陷阱已有）；③ 保持现状。

**Q-MOB-14 抽屉的可访问性缺口要不要补？**
现状：抽屉无 `role=dialog`/`aria-modal`/焦点陷阱（`M/:1269-1353`）；汉堡无 `aria-expanded`（`M/:1341-1345`）。
候选：① 补语义与焦点陷阱；② 用 Compose `ModalNavigationDrawer` 现成语义；③ 不补（可接受）。

**Q-MOB-15 G-04 在实现层面怎么才算"吸收残余速度"？**
现状：mobile-adapt 用 `{passive:true}` + `stopPropagation()`（`M/:1417-1440`）+ `overscroll-behavior:contain`（`M/:285,634,794,1080`）。被动监听不能 `preventDefault`，在某些 WebView 上外层仍可能跟滚（E2 风险）。
候选（Compose）：① `nestedScroll` 连接器：内滚到边后把剩余 delta 消费掉（不传给父）；② 只在边缘新增手势才交给父（G-04 字面）；③ 用 `overscrollBehavior` 等价物（Compose 无直接等价，需自绘）。
需真机验证"到边后手指继续滑，外层是否移动"（`MOB-31/32`）。

**Q-MOB-16 断点用宽度还是窗口尺寸类？**
现状：mobile-adapt 用 `@media (max-width:767px)`（`M/:198`），后果是**手机横屏（812×375）宽度 >767 → 汉堡消失、回到桌面三栏**（§3 hamburger 字段 10）。
候选：① 抄宽度断点（横屏变桌面，可能可接受）；② 用"宽度 or 高度任一 ≤ 阈值"；③ Compose `WindowSizeClass`/`currentWindowAdaptiveInfo`（按可用宽高同时判定）。

**Q-MOB-17 目标尺寸取 44 还是 48？**
现状：mobile-adapt 实现一律 44（`M/:320-321,578,831-832`），但注释写 "WCAG 2.5.5 / Material 48dp"（`M/:798`）——**说 48 做 44**（E1 与 E0 不一致）。
候选：① 44dp（WCAG 2.5.5 AA）；② 48dp（Material）；③ 按控件分级（主操作 48、次级 44）。

---

## 附：本路对账口径（快速复核用）

```
python tools/ui-audit/ui_audit.py check --findings _ws\out\findings-mobile-adapt.csv   --facts docs\spec\ref\dsh-mobile-adapt.facts.csv   --out _ws\out\coverage-mobile-adapt.md   --fail-on-uncovered
python tools/ui-audit/ui_audit.py check --findings _ws\out\findings-mobile-android.csv --facts docs\spec\ref\dsh-mobile-android.facts.csv --out _ws\out\coverage-mobile-android.md --fail-on-uncovered
```

- 结果：adapt `covered=41 excluded=0 uncovered=0 stale=0`；android `covered=75 excluded=7 uncovered=0 stale=0`。
- 节点总数 **32**（AD 20 / AN 12），全部 13 字段写满；有 JSX 交互点的节点行数与 facts 一一对应（§3 开头对照表）。
- 手工枚举脚本：`_ws/out/gen_mobile_audit.py`；原始 scan 输出：`_ws/out/findings-mobile-android-raw-scan.csv`。

# myco 后续工作清单与已知问题（2026-11-26）

> 用途：上下文压缩/新会话时从这里恢复。当前最新提交 `6e36128`（本地 `main`，未 push：VPN 开启时 GitHub 不可达）。
> 测试基线：`:core:test` 86 + `:bridge-http:test` 7 + `:app-android:testDebugUnitTest` 7 = **100 绿**；`:app-android:assembleDebug`、`:app-desktop:installDist` 通过。

## 0. 当前已完成（不要再重做）

- 事件契约 B0/B1：seq/sessionId、500 事件重放、HTTP/SSE fromSeq、requestId 幂等。
- 会话 B2：摘要/preview/置顶/归档/软删/永久删除/搜索、抽屉菜单、回收站/归档页、批量选择（全选/批量归档/批量删除+撤销）。
- 消息模型 B3：regenerate 同 nodeId 版本链、editResend（append-only + revises）、会话互斥、stop turnId。
- 会话树：parentId、activeLeaf、activePath；regenerate/editResend 创建新分支；旧分支保留；版本切换切 activeLeaf；**用户消息分支 `‹ N/M ›` 切换器（2026-11-26 新增）**。
- 新会话语义：唯一草稿、首条消息转正、标题模型生成、删除/归档当前会话后进入新草稿。
- 模型：会话级 provider/model 选择、reasoning level 自定义映射、模型目录编辑（全屏 Dialog）、第三方模板、API key 只写、auth.ref 自动生成。
- 聊天 UI：助手全宽无气泡、Markdown（标题/列表/引用/代码块/表格/`---` 分隔线/可点链接）、消息操作条、版本切换、footer。
- 过程时间线：reasoning/tool/text 按事件顺序实时流式；小行折叠（思考单行预览、工具摘要）、组体限高内滚、内层 sticky header、外层不吸顶、组体内外滚动隔离（手势代际）、外层+内层展开动画、展开不移动窗口、**统一进度逐帧跟底（流式思考/工具/正文都跟、结束不跳）**、组头按工具类别汇总。
- 来源：web_search 解析 title/url/site/snippet/date；回答下缩略行（favicon/首字母 + 数量）+ 底部 Sheet 完整列表；工具详情按类型展示。
- 后台保活：生成期间前台服务 + 通知权限请求；实测切 Home 后 1000 词生成完成。
- **长会话"死会话"闪退已修复**（LazyColumn 重复 key；`resolveVisibleMessages` + 唯一分支渲染）。
- 多份调研：`docs/recon/dsh-ui-implementation.md`、`docs/recon/deepseek-citation-ui.md`。
- 本轮报告：`docs/reports/p0-dead-session-report.md`、`p1-autoscroll-report.md`、`user-branch-report.md`、`inner-expand-animation-report.md`。

## 1. 大块（按建议优先级）

### P0 长会话“死会话”闪退（已修复，2026-11-26）
- 现象：会话变长后有概率打开即闪退，无法再进入该会话。
- 根因：工具中间步 assistant（`nodeId=null/finishReason=tool_use`）与正式回答共用 turn；`activeLeaf` 失效/切换会话中间态时 `visible` 退化为全部消息，把同一 turn 渲染出两个 `ph-$turn` → LazyColumn 重复 key 崩溃。
- 修复：`resolveVisibleMessages` 只渲染唯一分支（失效时退回最新叶子，不再退化全量）、剔除工具中间步、key 带 index 且过程组去重、`ChatScreen` 按 `sessionId` 重建、解析/载入容错。
- 报告：`docs/reports/p0-dead-session-report.md`；回归测试 `app-android/src/test/.../VisibleMessagesTest.kt`（5 条）。
- 仍欠：损坏会话的可恢复提示 UI；mid-file 损坏行仍整体不可读（不崩，但空会话 + 提示）。


### P1 自动滚动统一进度（已修复，2026-11-26）
- 现象：自动滚动只跟 act 段，思考/工具段不跟；模型输出结束时窗口乱跳。
- 根因：组体只按 entries 条数滚动（文本变长不跟）、结束时先清 live 再 reload（中间帧内容消失）、落盘后过程组由展开变收起（高度骤减）、外层跟底被程序滚动误判为用户滚动。
- 修复：组体逐帧跟底（落底/小步推进 + 用户拖动暂停）、`reload(clearLive)` 同帧原子提交、`streamingTurn` 使落盘回合保持展开且组体停在底部、外层用 `interactionSource` 判定用户拖动且末项在视口外时直接落底。
- 报告：`docs/reports/p1-autoscroll-report.md`（含真机录屏抽帧验证）。
- 遗留：发送后软键盘不自动收起；思考行仍是单行预览（是否流式自动展开待定）。

### P1 会话树补全（用户消息分支，已实现，2026-11-26）
- DSH 观察（用户确认）：不止重生成有树；用户**修改消息后重发**也产生分支；第二次重发会创建与第一次发送**平行**的节点；每个分支下面再 regenerate 继续分支。
- 已实现：`userVersionGroups`（同 `parentId` 的 user 互为版本）+ 气泡下 `‹ N/M ›` 切换器；切换 = `deepestLeaf(版本)` 写 activeLeaf 并持久化；上下文按 activeLeaf 走（真机验证追问 parentId 正确）。
- 报告：`docs/reports/user-branch-report.md`；单测 2 条（共 7 条）。
- 仍欠：切分支**锚点修正**（DSH demo 有，我们没做，列表会轻微位移）；work 分支独立会话文件（设计如此）。


### P2 展开动画覆盖内层（已实现，2026-11-26）
- 现象：外层过程组体有展开动画，但小思考/工具行的展开/收起仍是瞬间出现。
- 修复：详情 item 固定存在 + `AnimatedVisibility(expandVertically/shrinkVertically + fadeIn/fadeOut, 180/160ms)`；行间距从 arrangement 挪进 item padding，零高度详情不再撑出空隙；展开只向下长、不动窗口。
- 报告：`docs/reports/inner-expand-animation-report.md`（20fps 录屏逐帧验证，4 段动画各约 4 帧）。


### P2 正文引用/来源链路
- 来源缩略行/Sheet 已完成；还差 chat-demo 式正文 `[citation:N]` 角标（需要 citations 数据来源；当前无服务端 citations）。
- 来源 Sheet 的“已截断”提示未做。
- 参考 `docs/recon/deepseek-citation-ui.md`。

### P3 M3 手势 / 返回（已实现，2026-11-26）
- 正文右滑开抽屉：`swipeRightToOpenDrawer`（只观察 Final pass、横向占优 + 96dp 阈值，不影响竖向滚动）；
- 单条归档/删除：非当前会话时**抽屉保持打开**（仅当前会话被操作才关抽屉进新草稿）；
- **预测式导航（2026-11-26 扩展，见 `predictive-nav-report.md`）**：
  - 可复用接口 `PredictiveNavState` + `Modifier.swipeRightToExit`（进度源统一，M5 可直接叠加动效）；
  - 抽屉关闭：抽屉内容 + **遮罩**一起跟手（遮罩 alpha 由 drawer offset 与预测进度共同决定）；
  - 设置→聊天窗口、回收站→设置：系统手势、返回箭头点击、页面右滑拖拽**同一套右滑动画**；
  - 设置页右滑不再被抽屉手势抢走（`gesturesEnabled` 仅聊天页）；
  - 聊天窗口作为常驻基座，设置/回收站覆盖其上（露出真实内容，流式不中断）。
- 报告：`docs/reports/m3-gesture-report.md`、`docs/reports/back-semantics-report.md`、`docs/reports/predictive-nav-report.md`。
- 2026-11-26 追加：设置/回收站改为"左滑进入"（prepareEnter+animateEnter），
  修复打开瞬间闪"没有归档/没有删除"（loading 状态），修复拖拽停一半"抽搐"
  （左缘交系统手势、systemDriven 让位、动画期间忽略拖拽、0.15% 死区）。
- 仍欠：宽屏抽屉无手势（返回仍可用）；抽屉预测预览用位移近似 offset。



### P3 M5 设计系统（进行中：M5.0 技术验证已完成 2026-11-26）
- 方案与拍板见 [90-m5-visual-system.md](../design/90-m5-visual-system.md)；M5.0 结论见 [m5-0-gallery-report.md](m5-0-gallery-report.md)。
- 已完成：超椭圆 Shape、新拟物 Raised/Inset/Float、玻璃 L0（菲涅尔+高光+透射）、统一 LightRig、19 个自绘图标、settle/glide/snap 物理、真机画廊（浅/深）。
- 待做：阴影尺寸缓存（当前 90th 18ms）；L1 真背景模糊是否引 Haze 待评估；M5.1 tokens/primitives 全量替换旧 Theme；M5.2 面板壳（层叠 + 返回栈）；M5.3 聊天/抽屉/设置重做。
- 已知待调：暗色结构面浮雕对比偏弱、浅色粉气泡饱和度、玻璃深色透射偏清、超椭圆 n 值（默认 4.0）。

## 1.5 M5 前结构性细节（2026-11-26 已完成，见 chat-shell-polish-report.md）

- 新会话标题生成后实时同步到聊天顶栏（订阅 SessionUpdated）；
- 标题提示词限 16 字符、不再硬切；抽屉长标题可横向滚动查看；
- 进入会话直接定位底部（不再从顶部滚下来）；只有抽屉换会话才做一次落底；
- 组体内"收起态思考行"不再 sticky（展开行才 sticky，且吸顶范围止于本行内容）；
- 从设置/回收站返回不再自动滚到底：滚动状态提升为会话级 ChatScrollState，
  并且自动跟底只在流式/收尾时生效。

## 2. 已发现但未修的具体点

1. **思考过程展开后长文本滚动性能**：目前外层组体手动接管滚动 + LazyColumn sticky；长 reasoning 已折叠成单行，展开后仍可能掉帧（实测 gfxinfo janky 约 3.6% 现代帧、99th 36ms）。后续可考虑更轻的文本块/分段渲染。
2. **组体内“边界拖动”已改为交给外层原生滚动**，需持续真机确认无竞争。
3. **来源 favicon**：直接抓 host/favicon.ico，部分站点失败退首字母；无 favicon 缓存清理策略。
4. **单条删除/归档**：已改为非当前会话时抽屉保持打开（2026-11-26，见 `m3-gesture-report.md`）；抽屉内的 snackbar 仍可能被抽屉遮住一部分。
5. **标题生成**：首条消息后额外一次模型调用；chat 顶栏标题不实时刷新（抽屉刷新可见）；失败静默。
6. **通知权限**：Android 13+ 首次发送请求；拒绝后 FGS 仍生效但通知不可见。
7. **模型配置**：deepseek-flash 需要手动开启“支持思考强度”并设 high 参数；Anthropic 字符串档位映射 low=2048/medium=8192/high=32768；OpenAI 发 `reasoning_effort`。
8. **editResend UI** 已真机点验（2026-11-26，见 `user-branch-report.md`）；旧消息"已被替代"标记正常。
9. **回收站/归档撤销** 点击时序未完成真机点验。
10. **工具调用结果展示**：web_search 卡片已展示前 3 来源；其它工具 JSON 仅截断文本。
11. **事件重放环在内存**，进程重启后不能回放增量（历史仍从 JSONL）。
12. **组体滚动**：Android 真机上组体区域使用 PointerInput 手动分发；与选择文字/长按等交互的兼容需回归。
13. **无 work 分支/多会话并发生成 UI**；FGS refcount 已处理并发生成。
14. **Markdown 非全集**：链接可点，但表格宽度/嵌套列表/公式/半截 fence 未处理。
15. **分支切换无锚点修正**：切 user/assistant 版本后列表可能轻微位移（DSH demo 有 anchor 修正）。
16. **发送后软键盘不自动收起**：流式期间聊天视口被压缩，观感一般。
17. **分支切换与发送的竞态（待核实/加固）**：UI 切分支是 `scope.launch { SessionSetActiveLeaf }` 的异步命令，
    紧跟发送时 `ChatSend` 依赖 sidecar `activeLeaf`；真实分支数据（parentId）已验证正确，但极端快速连点后
    sidecar 可能停在非最后一次点击的分支（一次实测出现过 `2/3` 与最后可见分支不一致）。彻底解法：给
    `ChatSend` 加显式 `leafId` 参数（请求内携带当前分支），sidecar 只作持久化用途。

## 3. 真机回归清单（Android 15，设备 7e7c5a68）

### 2026-11-26 已回归（本轮）

- [x] 长会话打开（P0 死会话）——4 个会话反复切换无崩溃；
- [x] 流式跟底：思考/工具/正文统一跟随，结束不跳（录屏抽帧核对）；
- [x] 用户编辑重发两次形成 `‹ 3/3 ›`，切分支后上下文正确（事件 parentId 核对）；
- [x] 过程组内层小行展开/收起动画（20fps 录屏逐帧核对）；
- [x] 组体边界拖动交外层原生滚动后无竞争（本轮未复现异常）。

### 仍待回归

- [ ] 深度思考长文本全程 + 组体内 sticky 头；
- [ ] 组体内外滚动边界与惯性（极端长列表）；
- [ ] 来源缩略行/Sheet/点击；
- [ ] 后台/锁屏生成；
- [ ] 单条删除/归档/撤销时序；
- [ ] assistant 版本切换（‹ 1/N ›）与 regenerate 在分支下的组合。

## 4. 开发/验证命令

```powershell
cd E:\myco-chat
& 'E:\11-15\tools\gradle-8.9\bin\gradle.bat' -p E:\myco-chat :core:test :bridge-http:test :app-android:testDebugUnitTest :app-android:assembleDebug :app-desktop:installDist --console=plain
adb -s 7e7c5a68 install -r app-android\build\outputs\apk\debug\app-android-debug.apk
```

- 关键文件：`core/.../bridge/InProcessBridge.kt`（事件/流/树/标题/FGS）、`app-android/.../MainActivity.kt`（Shell/ChatScreen/发请求/FGS/跟底）、`app-android/.../ui/ChatContent.kt`（时间线/折叠/来源/Markdown/滚动控制/可见分支/展开动画）、`app-android/.../ui/Favicons.kt`。
- 事件/会话 schema：`specs/session-events.schema.json`；fixtures 校验 23/23。
- 当前分支 main，远程 `https://github.com/Hahn123offline/myco-chat.git`；最新提交 `6e36128`（未 push）。

## 5. 恢复时的第一件事

1. `git log --oneline -5` 确认最新是 `6e36128`（或在它之后）；
2. 读本文件；P0/P1 自动滚动/用户分支/内层动画均已完成，**不要重做**；
3. 下一步建议按优先级：P2 正文 `[citation:N]` 角标 + 来源 Sheet 截断提示 → P3 M3 手势/预测式返回 → P3 M5 设计系统；分支切换锚点修正（体验 polish）可穿插；
4. 每步跑测试 + 真机验证 + 更新 `docs/reports/`；GitHub 不可达时只本地提交、不要尝试 push。

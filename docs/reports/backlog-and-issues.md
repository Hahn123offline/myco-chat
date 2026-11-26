# myco 后续工作清单与已知问题（2026-11-26）

> 用途：上下文压缩前落盘，后续会话从这里恢复。当前最新提交 `14421a4`（已 push）。
> 测试基线：`:core:test` 86 + `:bridge-http:test` 7 = **94 绿**；`:app-android:assembleDebug`、`:app-desktop:installDist` 通过。

## 0. 当前已完成（不要再重做）

- 事件契约 B0/B1：seq/sessionId、500 事件重放、HTTP/SSE fromSeq、requestId 幂等。
- 会话 B2：摘要/preview/置顶/归档/软删/永久删除/搜索、抽屉菜单、回收站/归档页、批量选择（全选/批量归档/批量删除+撤销）。
- 消息模型 B3：regenerate 同 nodeId 版本链、editResend（append-only + revises）、会话互斥、stop turnId。
- 会话树：parentId、activeLeaf、activePath；regenerate/editResend 创建新分支；旧分支保留；版本切换切 activeLeaf。
- 新会话语义：唯一草稿、首条消息转正、标题模型生成、删除/归档当前会话后进入新草稿。
- 模型：会话级 provider/model 选择、reasoning level 自定义映射、模型目录编辑（全屏 Dialog）、第三方模板、API key 只写、auth.ref 自动生成。
- 聊天 UI：助手全宽无气泡、Markdown（标题/列表/引用/代码块/表格/`---` 分隔线/可点链接）、消息操作条、版本切换、footer。
- 过程时间线：reasoning/tool/text 按事件顺序实时流式；小行折叠（思考单行预览、工具摘要）、组体限高内滚、内层 sticky header、外层不吸顶、组体内外滚动隔离（手势代际）、展开动画（外层组体）、展开不移动窗口、逐帧平滑跟底、组头按工具类别汇总。
- 来源：web_search 解析 title/url/site/snippet/date；回答下缩略行（favicon/首字母 + 数量）+ 底部 Sheet 完整列表；工具详情按类型展示。
- 后台保活：生成期间前台服务 + 通知权限请求；实测切 Home 后 1000 词生成完成。
- 多份调研：`docs/recon/dsh-ui-implementation.md`、`docs/recon/deepseek-citation-ui.md`。

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


### P2 展开动画覆盖内层
- 现象：外层过程组体有展开动画，但小思考/工具行的展开/收起仍是瞬间出现（AnimatedVisibility 只包了外层）。
- 目标：内层行内容也用展开动画（高度 + 淡入淡出），且不影响窗口坐标。

### P2 正文引用/来源链路
- 来源缩略行/Sheet 已完成；还差 chat-demo 式正文 `[citation:N]` 角标（需要 citations 数据来源；当前无服务端 citations）。
- 来源 Sheet 的“已截断”提示未做。
- 参考 `docs/recon/deepseek-citation-ui.md`。

### P3 M3 手势 / 返回
- 正文右滑开抽屉、左缘返回不退出（已有 BackHandler，无预测动画）；
- PredictiveBack 预览动画；
- 抽屉手势与组体内滚动边界不冲突；
- 单条删除/归档是否保持抽屉打开（批量已支持）。

### P3 M5 设计系统
- token/间距/字阶/动效曲线/图标光学对齐/卡片层级/空态/错误态统一；
- 现有多处样式仍是“能用”而非“干净利落”。

## 2. 已发现但未修的具体点

1. **思考过程展开后长文本滚动性能**：目前外层组体手动接管滚动 + LazyColumn sticky；长 reasoning 已折叠成单行，展开后仍可能掉帧（实测 gfxinfo janky 约 3.6% 现代帧、99th 36ms）。后续可考虑更轻的文本块/分段渲染。
2. **组体内“边界拖动”已改为交给外层原生滚动**，需持续真机确认无竞争。
3. **来源 favicon**：直接抓 host/favicon.ico，部分站点失败退首字母；无 favicon 缓存清理策略。
4. **单条删除/归档**仍会关闭抽屉（批量不苦；是否改为行内撤销待定）。
5. **标题生成**：首条消息后额外一次模型调用；chat 顶栏标题不实时刷新（抽屉刷新可见）；失败静默。
6. **通知权限**：Android 13+ 首次发送请求；拒绝后 FGS 仍生效但通知不可见。
7. **模型配置**：deepseek-flash 需要手动开启“支持思考强度”并设 high 参数；Anthropic 字符串档位映射 low=2048/medium=8192/high=32768；OpenAI 发 `reasoning_effort`。
8. **editResend UI** 未完成真机点验；旧消息“已被替代”标记需设备确认。
9. **回收站/归档撤销** 点击时序未完成真机点验。
10. **工具调用结果展示**：web_search 卡片已展示前 3 来源；其它工具 JSON 仅截断文本。
11. **事件重放环在内存**，进程重启后不能回放增量（历史仍从 JSONL）。
12. **组体滚动**：Android 真机上组体区域使用 PointerInput 手动分发；与选择文字/长按等交互的兼容需回归。
13. **无 work 分支/多会话并发生成 UI**；FGS refcount 已处理并发生成。
14. **Markdown 非全集**：链接可点，但表格宽度/嵌套列表/公式/半截 fence 未处理。
15. **消息版本切换持久化**存储 `v_nodeId` 偏好；切分支时 activeLeaf 会持久化，但 user 分支尚无 UI。

## 3. 真机待回归清单（Android 15，设备 7e7c5a68）

- 长会话打开（P0）；
- 深度思考全程：思考行流式/不卡/折叠展开；
- 过程组展开动画 + 小行动画（P2）；
- 流式跟底：思考段、工具段、正文段统一跟随，结束不跳（P1）；
- 用户编辑重发两次的分支 UI（P1）；
- 组体内外滚动边界与惯性（已改，继续观察）；
- 来源缩略行/Sheet/点击；
- 后台/锁屏生成；
- 单条删除/归档/撤销时序。

## 4. 开发/验证命令

```powershell
cd E:\myco-chat
& 'E:\11-15\tools\gradle-8.9\bin\gradle.bat' -p E:\myco-chat :core:test :bridge-http:test :app-android:assembleDebug :app-desktop:installDist --console=plain
adb -s 7e7c5a68 install -r app-android\build\outputs\apk\debug\app-android-debug.apk
```

- 关键文件：`core/src/main/kotlin/dev/myco/core/bridge/InProcessBridge.kt`（事件/流/树/标题/FGS）、`app-android/.../MainActivity.kt`（Shell/ChatScreen/发请求/FGS）、`app-android/.../ui/ChatContent.kt`（时间线/折叠/来源/Markdown/滚动控制）、`app-android/.../ui/Favicons.kt`。
- 事件/会话 schema：`specs/session-events.schema.json`；fixtures 校验 23/23。
- 当前分支 main，远程 `https://github.com/Hahn123offline/myco-chat.git`；最新提交 `14421a4`。

## 5. 恢复时的第一件事

1. `git log --oneline -5` 确认 `14421a4` 之后没有新提交；
2. 读本文件，先修 **P0 死会话闪退**（抓 logcat）；
3. 再按 P1 自动滚动统一进度 / 用户分支；
4. 每步跑测试 + 真机验证 + 更新 `docs/reports/`。

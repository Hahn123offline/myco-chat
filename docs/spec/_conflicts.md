# 冲突登记表（P2 决策索引）

> 每一行 = **参考实现之间 / 参考实现与我方现状之间「做法互斥」** 的一处。
> 这是 P2 拍板的索引：每个 `C-xx` 最终必须落到「采用谁 + 为什么」，或转成 `open-questions.md` 的 Q 条目。
>
> **判定规则**：只有"做到 A 就做不到 B"才算冲突；可以并存的差异进各面板 spec 的「补充项」，不进本表。
> 证据一律 `ref/xxx.md` 的 §/行 + `path:line`；标「旧 myco」的指本仓库现状。

| ID | 冲突点 | 甲方（做法 + 证据） | 乙方（做法 + 证据） | 我的倾向 | 状态 |
|---|---|---|---|---|---|
| C-01 | 内滚到边后残余速度 | **DSH 允许滚链，且是刻意的**：①过程组 `overscroll-behavior-y: auto`（`ChatGroupSeat.module.css:90`），`interrupt` 回调**不接收事件对象**，结构上不可能 preventDefault（`use-process-scroll.ts:53-65`）；②外层会话监听**主动 passive**（`use-chat-viewport.ts:46-47,78-80`）；③README 把它当规格句写："Wheel scrolling can continue into the outer transcript at an edge"（`conversation-nodes/README.md:182`，E1）；④同库分轴惯例：横向 `contain`、纵向 `auto`（`JsonTree.module.css:16-17`、`MarkdownText.module.css:195`）；⑤轮次导航轨道**明写** "no scroll chaining" + `contain`（`TurnNavigator.module.css:48-56`） | **旧 myco / 用户口径**：到边吸收，不传速度（G-04，用户拍板） | **拆成两条规则**：`G-04a 触摸惯性在边吸收`（移动端，用户口径）/ `G-04b 滚轮可链式`（桌面，DSH 口径）。手机上触屏是唯一输入，等于 G-04 全量生效 | 待拍（Q-08） |
| C-02 | 思考行展开形态 | **DSH：形态 A** 不封顶 + 头部 sticky（`ReasoningRow.module.css:6-22`） | **旧 myco：形态 B** 45% 屏高封顶 + 内滚 | 形态 A（已拍板 Q-01） | ✅ 已定 |
| C-03 | 折叠行命中区 | **DSH 无 44dp**：整行 24px / 图标 16×16，无扩边（`dsh-base.md` §0-1、§2.1；`DisclosureRow.module.css:30-63`） | **移动端适配层（mobile-adapt）实际做法**：菜单/导航/输入/按钮一律 `min-height:44`；开关用伪元素 `::after{inset:-12px -8px}` 扩边；**字号 stepper 因两箭头中心距仅 14px 无法扩边，只能重排成 44×44+gap2**（`client.js:524-538,802-865,940-945`） | **几何硬约束（新发现）**：24px 折叠行 + 6px 行距，两侧各扩 e 需 `2e ≤ 6` → 命中高最多 **30px**；要 44dp 需 e=10 → **必然重叠 14px**。三条出路：① 行距改 ≥20px ② 视觉抬到 44dp（过程行密度 −40%）③ 只给"标题+箭头"小按钮，摘要区不响应 | 待拍（Q-MOB-03 / Q-03） |
| C-04 | 消息操作条在触摸端 | **DSH：统一常驻可见**（触摸不进 `@media(hover:hover)`，`MessageIconActions.module.css:5-10,42-61`） | **chat-demo：无移动兜底 → 触摸端历史消息操作不可达**（`chat-demo.md` §0-4，属其真实缺陷） | 常驻（同 DSH） | 待拍（Q-10） |
| C-05 | 过程区展开后能否随时收起 | **DSH：头部 sticky**（G-06） | **chat-demo：无 sticky/contain/max-height → 长内容只能滚回顶部**（`chat-demo.md` §0-2，属其缺陷） | 抄 DSH | 待拍（随 C-02 一起） |
| C-06 | 回到底部 | **DSH：v 钮（仅非跟随时出现）+ 宽屏轮次导航轨道**（`dsh-chat.md` §0-6） | **chat-demo：完全没有回到底部**（全仓 grep 0） | 抄 DSH；手机只保留 v 钮 | 待拍（Q-07 已取证） |
| C-07 | 滚动跟随判据 | **DSH：距底 ≤25px + 500ms 采样 + 位置存 null**（`dsh-chat.md` §0-5；`use-chat-reading.ts:7,134-144`） | **chat-demo：40px + 只有上翻才停**（`chat-demo.md` §0-3；`MessageList.tsx:33,128-136`） | 抄 DSH（更精确） | 待并入 chat spec |
| C-08 | 模型面板维度 | **DSH：两维（模型 × 推理等级）+ 搜索 + 双入口共享会话状态**；preset 在会话开始前、上下文长度在设置页（`dsh-settings.md` §5） | **旧 myco：深度思考/模型/思考强度合在一个 sheet**；且与发送参数双真源（`bugs.md` BUG-003） | 拆开：面板只放「模型 × 强度」，preset 与上下文另归其位 | 待拍（用户点名此处置不合理） |
| C-09 | 附件导入 | **DSH：无内建相册/相机**，只有 `/file`+拖放+粘贴三路；通用文件无客户端限制、选中即上传、**无取消上传按钮**（`dsh-settings.md` §6） | **旧 myco：三个系统选择器**，无预览/进度/最近照片；Bridge 无读/删附件命令（`bugs.md` M-02） | 要做**内建选择器**（myco 新增能力，DSH 无先例） | 待拍（需定义要哪些能力） |
| C-10 | 侧栏形态 | **DSH：grid track 列**（280/56/0），无遮罩、无手势、无返回键；窄屏是**挤压**中心列（`dsh-sidebar.md` §0-1；`AppFrame.tsx:265-266`、`columns.ts:13-23`） | **旧 myco / 目标：覆盖式抽屉**（遮罩 + 手势 + 返回键） | 覆盖式（移动端必须），宽屏可借 DSH 列宽 | 待拍（Q-SB-01） |
| C-11 | 层叠面板栈 | **DSH `ui-layout` 无返回栈/无手势返回**，换面板只是覆盖 `activePanelId`；真层叠在 dockkit（`floats` 数组序即 z 序）（`dsh-base.md` §0-6/8） | **myco 目标：无限抽屉**（层叠覆盖 + 抽象邻接 + 自建返回栈） | **自建**：借 dockkit 的 z 序模型，不借其分栏 | 待拍（Q-BASE-02） |
| C-12 | 会话分组依据 | **DSH：默认按工作区分组**（`stores.ts:84-90` `groupBy:'workspace'`），可切「工作区树 / 单列表」三模式；排序默认更新时间；**全包 grep 无 today/yesterday → 没有时间分组这个选项**（`dsh-workspace.md` §0-1） | **旧 myco：按时间**（今天/昨天/本周/更早） | 需要拍：保留时间分组（陪伴场景更自然）还是改按项目/主题分组；**两者可并存为两种视图** | 待拍（建议做成视图开关） |
| C-17 | 会话行高与密度 | **DSH：32px 整行**（`Rows.module.css`；`dsh-workspace.md` §0-2），行尾悬浮才出 `⋯`+归档/置顶 | 旧 myco：行高更大、操作常显 | 手机端需要 ≥44dp 命中，但**视觉可保持 32–36px 加透明扩边** | 待拍（并入 Q-03） |
| C-18 | 长列表加载策略 | **DSH：非虚拟滚动**，每组先放 5 条空闲会话 +「再展开 N 条」每次 +5/Infinity（`WorkspaceBrowser.tsx:55,581-602`） | 旧 myco：一次性加载 | 学 DSH（手机内存友好、实现简单），但需确认 myco 会话量级 | 待拍 |
| C-13 | 失败态重试 | **DSH：三处失败态都无重试按钮**，自动重试是倒计时展示（`dsh-chat.md` §0 补充） | **旧 myco：有重试入口**（用户此前要求） | 保留手动重试（移动网络刚需），但需定义"重试什么" | 待拍（Q-09） |
| C-14 | 展开动画 | **DSH：全部无高度动画**（连低内容也不做；`dsh-base.md` §0-4） | **旧 myco：spring(0.86,420) 纵向展开** | 已拍板：>240dp 直接切、≤240dp 可动画（G-10） | ✅ 已定 |
| C-15 | 设置保存语义 | **DSH 自身两种并存**：插件类设置页显式保存（离开丢弃草稿）；General 开关/Models 编辑/账户即时写（`dsh-settings.md` §0-3） | 旧 myco：即时写 | 学 DSH：**按页声明**，但必须在 spec 里逐页写明，不许混 | 待并入 settings spec |
| C-16 | 思考行折叠摘要 | **DSH：运行中取最后完成段落首行、结束取首行，删 `**`，用文字 shimmer**（`dsh-chat.md` §0-9 + `schema-node.md` 金标准） | **旧 myco：最后活动行 + 呼吸点** | 按 DSH（已拍板 Q-02） | ✅ 已定 |

| C-19 | 右栏形态 | **DSH 右栏是网格列**：整列平移隐藏、**内容保持挂载**（`SidebarRight.module.css:44-62`；`dsh-workspace.md` §0-5），页签有完整 WAI-ARIA（`TabPanel.tsx:245,263-266,109-124`） | 旧 myco：无右栏；若做抽屉则覆盖式 | ✅ 已澄清：G-01 已补边界「凡参与布局流的列/固定头尾/内联状态条都不属 overlay」 | ✅ 已澄清 |
| C-BP-01 | 菜单行高 | **DSH 菜单行**：`P/Menu.module.css` 的行高按内容撑（见 `ref/dsh-base.md` 菜单节点）；移动端适配层一律 `min-height:44`（`client.js:524-538`） | **旧 myco**：`DropdownMenuItem` 用 Material 默认 48dp | 统一走 G-11 的 `F=48dp`；菜单属于"整行可点"，可用 `padding` 达成不用扩边 | 待并入 G-11 |
| C-BP-02 | 遮罩关闭判据 | **DSH**：遮罩 `mousedown` 即关（`ImageLightbox.tsx:58`）；**chat-demo**：判据更严——必须在遮罩上**按下并松手**才关（`Modal.tsx:54` 附近），避免"框内拖选文字、拖到遮罩上松手"把内容关掉 | 旧 myco：`onDismissRequest` 即关 | 取 chat-demo 的严判据（按下与松手都必须在遮罩上） | 待并入 G 规则 |
| C-BP-03 | 弹层家族分工 | **DSH：只有居中 Modal**，且**零手势关闭**（`useModalLayer.ts:46-96`）；无 BottomSheet 形态 | **旧 myco：有 `ModalBottomSheet`**，支持下滑关（`MainActivity.kt:1291-1315`、`M5ModelSheet.kt:49`） | 保留两种形态但明确分工：**居中 Modal = 需确认/危险操作；BottomSheet = 轻选择/单选**；下滑关只给 BottomSheet | 待拍（Q-BP-03，阻塞 4 个面板） |
| C-BP-04 | dirty 表单关闭 | **DSH `SettingsForm`**：显式保存；离开即丢弃草稿，**无放弃按钮**（`prim/settings-form/SettingsForm.tsx:6-10,50-74`） | chat-demo / 旧 myco：无统一策略 | 有未保存改动时**拦截关闭并提示**（丢弃/继续编辑），不静默丢弃 | 待拍 |

---

## 判定口径（全局，各合并任务必须遵守）

**参考实现的「缺陷」不是「另一种解」。** 进冲突列的两个做法必须是「都能成立的设计选择」；
若某一方的文档/事实库已自评为缺陷（例：chat-demo 触摸端操作条不可达、无焦点陷阱、
无键盘菜单；mobile-adapt 的扩边无隔离导致重叠），**不得与另一种解并列**，
只能进各面板 §4「明确不做」并注明「参考实现自身缺陷」。

同理：**某列缺失时不许写"三家一致"**。当前 `dsh-go.md` / `taixu.md` 尚未产出，
所有"三家一致"结论在补齐前都只能写成"两家一致（另两路未取证）"。

---

## 已澄清

- **C-12 取证完成**：DSH 默认 `groupBy:'workspace'`，**没有时间分组选项**；三模式是 `workspace / workspace-tree / flat`，不是三种时间分组。旧 myco 的时间分组属**我方差异**，是否保留需拍板。
- **R1e（`ui-workspace` + `ui-sidebar-right`）已完成**：会话行 13 字段、分组、搜索、右栏、页签 ARIA 全部取证；侧栏那路留下的"家族级指针"已全部落成节点。

## 待补入（等 R2/R3/R4 回来）

- 移动端命中区的真实像素策略（dsh-Go / 太墟 / mobile-adapt）→ 补 C-03/C-17
- 手机端滚动物理（Flutter Clamping / Compose nested scroll）→ 补 C-01
- 面板架构的第三、第四个样本（dsh-Go 的导航、太墟的 A2UI/render_surface）→ 补 C-11

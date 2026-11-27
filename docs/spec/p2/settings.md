# P2 合并：settings

> **输入**：`ref/dsh-settings.md` §0–§4（含 §5 模型维度表作边界依据）、`ref/dsh-base.md` §2.7（General 外观行，ui-theme/locale 归位）、`ref/dsh-mobile.md` §settings 壳（窄屏全屏化/横滑导航/字号步进器/开关扩边）、`ref/legacy-myco.md` §0/§2.5/§4/§5、`ref/chat-demo.md` §2.23–2.27、`ref/taixu.md` §0/§1/§2.6、`ref/dsh-workspace.md` §2.2 tab-strip（tab 语义借用）。
> **读取时点**：2026-11-27。`ref/taixu.md` 本轮读取时刚补齐 §2.6 设置面板（首页/搜索/外观/智能体/模型 5 节点）；若该文件后续再改，以文件为准并回填本表。
> **口径**：`dsh-Go` 无事实库（`ref/` 下无 `dsh-go.md`），本表该列**全部为「未取证」**，不推测。太墟只在有节点级事实的行写做法，其余写「未取证」。
> **规模**：节点 **71** ／ 互斥冲突 **10**（既有 C-15 + 新登记候选 NS-01…NS-09）／ 明确不做 **14 组**（覆盖 17 个节点）／ 新增节点 **1**（§5）／ 待确认 **12**（Q-settings-01…12）+ 取证缺口 1（F-01）。
> **首批范围提醒**：Q-06 只批了「settings 壳 + models 子页」。本表把其余子页按「⏳ 非首批（Q-settings-03 定范围）」标注，不因此丢弃它们的节点。

---

## 1. 节点清单（先穷举，再填表）

> 一行一原子。出现条件里的「首批」= 已进 Q-06 范围；「⏳」= 范围待拍（Q-settings-03/04）。

| 节点 ID | 出现条件 | 参考里有没有 | 旧 myco 有没有 |
|---|---|---|---|
| `settings.shell.trigger` | 侧栏底部常驻入口（+ 桌面快捷键） | DSH ✓／太墟 ✓／chat-demo ✓ | ✓（抽屉底部「设置」） |
| `settings.shell.panel` | 设置层打开 | DSH ✓（居中模态）／太墟 ✓（覆盖根页）／chat-demo ✓（模态/底部弹层） | ✓（右滑入覆盖层） |
| `settings.shell.home` | 有「首页分类卡」形态时 | DSH ✗／太墟 ✓／chat-demo ✗ | ○（单滚动页，近似） |
| `settings.shell.nav` | 子页之间切换 | DSH ✓（左导航列）／太墟 ✗（push）／chat-demo ✓（页签） | ○（页内分组卡） |
| `settings.shell.close` | 设置层打开时 | DSH ✓／太墟 ✓（顶栏返回）／chat-demo ✓ | ✓（顶栏返回） |
| `settings.shell.back` | 返回上一级/关闭整层 | DSH ✗／太墟 ✓（单栈 popBack）／chat-demo ✗ | ✓（顶栏返回） |
| `settings.shell.deep-link` | 带参数直达某子页 | DSH ✗（只记上次 activeId）／太墟 ✓（openSettingsPath）／chat-demo ✓（openSettings('general')） | ✗ |
| `settings.shell.search` | 设置内检索 | DSH ✗／太墟 ✓／chat-demo ✗ | ✓（过滤式，有缺陷 D10） |
| `settings.shell.search-result` | 搜索有关键词时 | DSH ✗／太墟 ✓（注册表+跳转）／chat-demo ✗ | ○（过滤卡片，无结果页） |
| `settings.shell.first-run-guard` | 无可用 provider 时 | DSH ✓（凭据 onboarding）／太墟 未取证／chat-demo ✓（无 Key 自动开设置） | ✗（未取证） |
| `settings.shell.action-open-document` | 桌面 loopback + 有设置文档 | DSH ✓ | ✗ |
| `settings.shell.update-indicator` | 桌面端 + 有更新 | DSH ✓ | ✗ |
| `settings.general.appearance` | General 子页 | DSH ✓／太墟 ✓／chat-demo ✓ | ✓（主题 chip） |
| `settings.general.language` | General 子页 | DSH ✓／太墟 ✓（外观页内）／chat-demo ✓ | ✗（未取证） |
| `settings.general.font-size` | General 子页 | DSH ✓（字号行）／太墟 ✓（界面缩放滑杆）／chat-demo ✗ | ✗ |
| `settings.general.font-family` | 字号行的「更多字体」展开区 | DSH ✓ | ✗ |
| `settings.general.row-switch` | General 子页开关行 | DSH ✓／太墟 ✓（智能体/外观开关）／chat-demo ✓（思考/搜索开关在输入区非设置） | ✗（未取证） |
| `settings.general.version-row` | General 页版本行 | DSH ○（§1 提及未建节点）／太墟 ✓（关于页） | ✗ |
| `settings.general.zoom` | 有界面缩放项时 | DSH ✗／太墟 ✓ | ✗ |
| `settings.about.page` | 关于页 | 太墟 ✓／DSH ○ | ✗ |
| `settings.about.usage-notice` | 关于/使用须知 | 太墟 ✓ | ✗ |
| `settings.diagnose.page` | 诊断页 | 太墟 ✓／旧 myco ✓（自检 7 项） | ✓ |
| `settings.form.value-field` | 配置表单字段（文本/数字） | DSH ✓／太墟 ✓（模型表单） | ✓（OutlinedTextField） |
| `settings.form.secret-field` | 密钥字段 | DSH ✓／太墟 ✓（含显隐）／chat-demo ✓ | ✓（密钥对话框） |
| `settings.form.help-reset` | 字段帮助 + 恢复默认 | DSH ✓ | ✗ |
| `settings.form.save` | 显式保存/提交 | DSH ✓／太墟 ✓ | ✓（provider 保存） |
| `settings.form.leave-discard` | 离开表单 | DSH ✓（丢弃）／太墟 未取证 | ○（取消编辑器丢弃） |
| `settings.plugins.tab` | Plugins 区有 >1 页签 | DSH ✓／太墟 ○（插件中心，页签未取证） | ✗ |
| `settings.plugins.item-page` | Shell/agent-loop/web-search/subagent 四页 | DSH ✓ | ✗ |
| `settings.subagent.limits` | 有 subagent 命名空间 | DSH ✓／太墟 ○（子代理页，无深度/并发字段） | ✗ |
| `settings.subagent.model-selection` | 同上 | DSH ✓／太墟 ○（子代理编辑对话框） | ✗ |
| `settings.models.page-retry` | models 页加载失败 | DSH ✓／太墟 ○（错误流） | ○（message 文本） |
| `settings.models.row` | provider/model 档案行 | DSH ✓／太墟 ✓（ModelProfileCard） | ✓（provider 卡） |
| `settings.models.add-flow` | 添加入口 | DSH ✓ | ✓（3 个添加按钮） |
| `settings.models.delete-dialog` | 删除确认 | DSH ✓／太墟 未取证 | ✓（AlertDialog） |
| `settings.models.editor.key` | 编辑表单密钥字段 | DSH ✓／太墟 ✓（API Key 列表+显隐） | ✓（密钥对话框，另一节点） |
| `settings.models.editor.customized` | 高级字段折叠区 | DSH ✓／太墟 ✓（可展开分段） | ○（表单平铺） |
| `settings.models.editor.fields` | 显示名/baseURL/协议/route id | DSH ✓／太墟 ✓（provider+baseUrl 等） | ✓ |
| `settings.models.editor-footer` | 编辑表单底部动作 | DSH ✓／太墟 ✓（保存按钮） | ✓ |
| `settings.models.model-list` | 模型目录区 | DSH ✓／太墟 ✓（模型多选） | ✓ |
| `settings.models.model-row` | 单个模型行 | DSH ✓／太墟 ✓ | ✓ |
| `settings.models.model-advanced` | 模型高级字段（容量/输入类型） | DSH ✓／太墟 ✓（maxTokens/context/能力开关） | ✓ |
| `settings.models.fetch-dialog` | 「拉取模型」候选弹窗 | DSH ✓／太墟 ○（候选列表文件存在） | ✓（测试连通性，只显示字符串） |
| `settings.models.onboarding-welcome` | 版本公告未确认 | DSH ✓ | ✗ |
| `settings.models.onboarding-credential` | 无可用 provider（自动/显式） | DSH ✓／太墟 未取证 | ✗ |
| `settings.plugin-inventory.card` | 插件清单页 | DSH ✓ | ✗ |
| `settings.plugin-inventory.group-toggle` | 清单有分组 | DSH ✓ | ✗ |
| `settings.plugin-inventory.preset-switcher` | 有预设 | DSH ✓ | ✗ |
| `settings.plugin-inventory.jump-link` | 卡片归属其它预设 | DSH ✓ | ✗ |
| `settings.plugin-inventory.search` | 清单就绪 | DSH ✓ | ✗ |
| `settings.plugin-inventory.retry` | 清单/客户端同步失败 | DSH ✓ | ✗ |
| `settings.agent-preset.card` | 预设设置页 | DSH ✓／太墟 未取证 | ✗ |
| `settings.agent-preset.creator` | 有 conversation 流程 | DSH ✓ | ✗ |
| `settings.agent-preset.help` | 内置预设卡 | DSH ✓ | ✗ |
| `settings.agent-preset.view` | 查看 YAML | DSH ✓ | ✗ |
| `settings.agent-preset.guide-dialog` | 帮助弹窗 | DSH ✓ | ✗ |
| `settings.account.menu` | 侧栏账户启动器 | DSH ✓ | ✗ |
| `settings.account.signed-out` | 未登录 | DSH ✓ | ✗ |
| `settings.account.card` | 已登录 | DSH ✓ | ✗ |
| `settings.account.sign-in-dialog` | 登录中 | DSH ✓ | ✗ |
| `settings.account.sign-out-dialog` | 退出登录 | DSH ✓ | ✗ |
| `settings.account.notices` | bonus/配额通知 | DSH ✓ | ✗ |
| `settings.account.platform-overlay` | 桌面打开充值/用量页 | DSH ✓ | ✗ |
| `settings.account.onboarding.step` | 桌面首跑引导 | DSH ✓ | ✗ |
| `settings.account.onboarding.nav` | 同上 | DSH ✓ | ✗ |
| `settings.account.onboarding.confirmation` | 同上 | DSH ✓ | ✗ |
| `settings.data.history` | 数据管理/本地备份 | chat-demo ✓／太墟 ✓（本地备份） | ○（提供商导出/导入，无历史备份） |
| `settings.provider.export-import` | provider 配置导出/导入 | 太墟 ✓（JSON 导入导出/分享）／DSH ✗ | ✓ |
| `settings.session.bin-rows` | 会话归档/回收站入口 | DSH ✗／太墟 未取证 | ✓（设置页内两层） |
| `settings.provider.template-pick` | 添加 provider 时选模板 | DSH ○（目录即模板）／太墟 未取证 | ✓（6 个写死模板） |
| `settings.provider.key-dialog` | 给已有 provider 单独设密钥 | DSH ✗（密钥在编辑表单内）／太墟 ✗ | ✓（独立 AlertDialog） |

---

## 2. 对照表（主线）

> **冲突列**只写「做到 A 就做不到 B」。C-15 是既有登记；`NS-xx` 是本轮新发现、**建议 Lead 登记进 `_conflicts.md` 的候选编号**（本文件不擅自改 `_conflicts.md`）。
> **状态**：✅ = 建议明确（有 E0/G 规则支撑，无产品取舍）／⏸ = 需用户拍板或待 M2 结论（进 §6）／❌ = 建议不做（进 §4）。
> **太墟**列凡写「未取证」= `ref/taixu.md` 无该节点事实，不推测。

| 节点 ID | DSH | dsh-Go | 太墟 | chat-demo | 旧 myco | 冲突 C-xx | 建议 + 理由 | 状态 |
|---|---|---|---|---|---|---|---|---|
| `settings.shell.trigger` | 侧栏底部 42px 整行，rail 态 36×36，`aria-haspopup=dialog`+`aria-expanded`，快捷键 `Ctrl/Cmd+,`（`SettingsRoot.tsx:216-245`、`index.ts:179-196`） | 未取证 | 抽屉底部「设置」进栈（`taixu.md:65`+`base.layout.nav-stack`） | 侧栏底部整行按钮 → `openSettings('general')`（`S/Sider.tsx:94-101`、`se/settingsDialogState.ts:13-14`） | 抽屉底部 TextButton「设置」（`DR:115-124`）+ 宽屏常驻 | 无（三家同位置，进 §3.6） | 采用「侧栏底部整行 + 展开态文字/收起态只图标」：三家一致高置信（`_merge-template.md` §4 质量门）；反证 = 若藏进头像菜单，首跑用户找不到（chat-demo 同款反证 `DEMO-43`）。命中区按 Q-settings-10 抬到 ≥44dp | ✅ |
| `settings.shell.panel` | **居中模态** 800×800、`position:fixed inset:0 z:1000`、遮罩 `bg-mask-1`+blur、无进出动画；面板高取 viewport（`SettingsRoot.module.css:61-103,78-84`）；窄屏被 mobile-adapt 改成 `fixed inset:0 / 100dvh / 圆角0 / 遮罩 display:none`（`M/:718-737`） | 未取证 | **覆盖根页 + 单栈**：设置进全局导航栈，与子页共享返回与转场（`taixu.md:19`；`TaiXuNavHost.kt:103,651-655`）；顶栏大标题折叠、宽屏内容 840dp 居中（`settings.shell` 字段 2/10） | 桌面 760×500 居中模态（`max-height:100vh-32`）；移动端底部弹层 100% 宽、高 `100%-64`、圆角 24（`SE/Settings.module.css:1-7,351-360`） | 覆盖层从右滑入、层叠在聊天之上（`legacy-myco` §0 `MA:409-456`） | **NS-01**（浮层形态：居中模态 vs 全屏页面 vs 分类首页+push）；C-19 仅澄清：四家都是 overlay/覆盖，G-01「浮层不占位」适用且**不涉及布局列**，C-19 的边界说明不影响本节点 | 窄屏采用 **全屏覆盖页**（mobile-adapt E0，与无限抽屉「一层覆盖」同构）；宽屏形态待 M2（居中面板 or 列，见 Q-settings-01/02）。无论哪种都要求：① 高度取视口不取内容（反证：随内容变 → 切子页时外框跳动、遮罩边缘位移 `SET-03`）；② 内容区 `overscroll-behavior:contain`（G-04 + `M/:787-796`） | ⏸（M2） |
| `settings.shell.home` | ✗（直接左导航 + 默认选中首行，`SettingsRoot.tsx:57,74-88`） | 未取证 | **✓ 分类卡首页**：搜索入口 → 外观卡 →「环境」组（智能体/Linux）→「功能与数据」组（插件中心/本地备份）→「应用」组（诊断/关于/使用须知）（`SettingsScreen.kt:34-77`）；一屏可扫 30+ 子页（`settings.shell` 反证） | ✗（只有 2 个分组 tab） | ○ 单滚动页（外观卡/会话卡/模型提供方/常驻卡同屏，无子页） | **NS-01**（同层切换 vs 分类首页+push） | 首批**不设首页**，默认落在「通用」：① myco 首批子页 ≤6（Q-06 + 本表 ⏳ 项），一屏横滑可扫完；② 反证 push 方案：`taixu` 用**单一全局栈**消化「设置→子页」两层（`MainPageNavigation.kt:34-38` 深链要保证两层同入），而 myco 的返回栈是全局面板栈（M2），设置内再分 2–3 级会让「一次返回退到哪层」不可预测；③ **触发条件**：若最终子页 >7（plugins/agent 全做），改用太墟式分类首页 + push，并同时把返回语义交回 M2 统一栈 | ✅（附触发条件） |
| `settings.shell.nav` | 188px 左导航、行固定 40px、gap4、`aria-current`、**无 `role=tablist`、无 roving**（`SettingsRoot.module.css:111-177`、`SettingsRoot.tsx:71-89`）；mobile-adapt 窄屏改**横滑 tab 条**：`navCell min-height:44`、`scroll-snap-type:x proximity`、隐藏滚动条、`overscroll-behavior-x:contain`（`M/:738-786`） | 未取证 | 无导航列：首页分类卡 → push 子页（`settings.shell` 字段 7） | 桌面 172px 导航列；移动横排可横滚；`tablist/tab/aria-selected/tabpanel` 齐全但**无方向键**、tabpanel 无 `aria-labelledby`（`SettingsDialog.tsx:87-102`） | 页内分组卡（LazyColumn），无子页导航 | **NS-02**（a11y 语义：`button`+`aria-current` 导航 vs `role=tablist`+roving 页签） | 窄屏采用 **横滑 tab 条 + 完整 WAI-ARIA 页签语义**（`role=tablist` + roving tabindex + ←→/Home/End + manual activation；借用 DSH dockkit 完整实现 `TabPanel.tsx:245,263-266,103-124,293-307`，经由 `dsh-workspace` §2.2 取证）。理由：① DSH 自身两处不一致（settings nav-row 只给 button，plugins.tab 是完整 tablist），取完整者；② 反证：只给 `role=tablist` 不给方向键比不加 role 更糟（`dsh-workspace` RSB-05 反证原文）——chat-demo 缺方向键正是该缺陷；③ 选中项自动滚入视口 + 隐藏滚动条（反证：多一条灰线破坏顶栏 `MOB-17`） | ⏸（形态随 NS-01） |
| `settings.shell.close` | 头部 54px 内 28×28、`flex:none` 不随内容滚、视觉隐藏本地化 label（`SettingsRoot.tsx:90-97`、`css:188-243`）；窄屏被 mobile-adapt 抬到 44×44（`M/:867-876`） | 未取证 | 顶栏返回钮（`base.layout.nav-stack` 字段 4：各页 `onBack`） | 标题栏 X（`SettingsDialog.tsx` 容器；Modal 语义 `B/Modal.tsx:36-82`） | 顶栏返回（`base.primitives.topbar-back`，`MA:1661`） | 无（都在顶栏，进 §3.6） | 关闭按钮**固定在头部不参与内容滚动**（反证：长设置页滚到底找不到关闭 `SET-07`）；a11y 名非空；命中 ≥44dp（Q-settings-10）。措辞用「关闭」而非「返回」（本层就是整层退出，同 NS-01 结论） | ✅ |
| `settings.shell.back` | ✗ **没有「返回上一页」概念**（只有 select/close，`SettingsRoot.tsx:71-89`；Q-SET-05 已登记） | 未取证 | **✓ 单栈返回**：抽屉/浮层永远先于导航栈（5 级返回优先链，`taixu.md:34`；`TaiXuNavHost.kt:653`） | ✗（只有页签切换 + 关闭） | ✓ 顶栏返回退出设置（`MA:452`） | **NS-01** | 采用「返回 = 关闭整个设置层」（设置只有一层）；不引入设置内第二级栈。反证：设置内再分两级后，「一次返回」在全局抽屉栈里对应两层语义，用户无法预测（`taixu.md:106` 反证：多栈各自为政）；深链需求由带参打开满足（下一条） | ⏸（M2） |
| `settings.shell.deep-link` | ✗ 只短路打开「上次 activeId」，无 page 参数（`shell-store.ts:17-25`） | 未取证 | ✓ `openSettingsPath` 保证「设置页→子页」两层同时存在（`MainPageNavigation.kt:34-38`）；深链/通知统一进 `GlobalNavigationBus`（`TaiXuNavHost.kt:126-149`） | ✓ `openSettings('general')` 带 section 参数（`settingsDialogState.ts:13-14`） | ✗ `openSettings()` 无参（`MA:344-349`） | 无（可并存，进 §3.6） | 采纳「带参打开子页 + 记住上次子页」：chat-demo/太墟 E0；反证：没有带参打开，「去设置改字号/配 Key」必须先进设置再找，首跑引导与外部跳转都无法直达。实现上 = `openSettings(sectionId, anchorId?)`，不引入额外栈层 | ✅ |
| `settings.shell.search` | ✗ **未见实现**（`SettingsRoot` 全文无 search；唯一搜索在插件清单页内 `PluginInventorySettingsTab.tsx:474-487`；Q-SET-01 已登记） | 未取证 | **✓ 全局设置搜索（独立子页）**：注册表 + 热门标签 + 结果跳转 + 命中高亮 + 拼音关键词 + 进页自动聚焦；结果走正常入栈（`SettingsSearchScreen.kt:53-455`、`SettingsSearchRegistry.kt:1-910`、`TaiXuNavHost.kt:310-386`） | ✗ **未见实现**（dialog 只有 tablist 与 2 个分组，`SettingsDialog.tsx:87-102`） | **✓ 有**：设置页 `SearchField`（`CP:206`）过滤卡片，命中才显示卡片（`MA:1601,1668,1691`）；**无匹配只剩搜索框、无提示**（D10，`MA:1599-1605,1663-1787`） | **NS-03**（做 vs 不做） | **做**，但用「结果列表 + 定位跳转」而不是旧式「过滤隐藏卡片」。理由：① 旧 myco 有 = 删掉是能力回退（`_merge-brief.md` 硬规则 5）；② 反证：手机全屏 + 横滑 tab 下子页不能一屏看全，找「字号」要先背它在哪个子页（Q-SET-01 同判断；`taixu` 的 30+ 子页反证同理）；③ 旧式过滤有实测缺陷：未命中卡片消失 + 无匹配无提示（D10）；④ 太墟已给 E0 范式（注册表 + 跳转），证明移动端可做。**范围/跳转写法见 §3.6**（搜索态 + 切 tab + 滚到锚点 + 高亮 2s） | ⏸（Q-04） |
| `settings.shell.search-result` | ✗ | 未取证 | ✓ 结果列表带命中高亮、空查询显示热门标签、跳转按 target 表（含跨根页 push，`TaiXuNavHost.kt:352-356`） | ✗ | ○ 无结果页（过滤时长列表直接变短） | **NS-03**（同 `shell.search`） | 结果行 = 「子页名 · 条目名 + 命中高亮」；点击 → 关搜索 → 切到目标 tab → `scrollTo(anchor)` → 高亮 2s；**无匹配必须给「无匹配 + 建议关键词」**（修 D10）；搜索只匹配条目名/别名/子页名，不匹配长说明（避免误命中） | ⏸（Q-04） |
| `settings.shell.first-run-guard` | 用 `settings.onboarding` 槽位弹「官方凭据引导」（`DeepSeekOnboardingDialog.tsx:58-137`；`automatic = credentialOnboarding && !dshDesktop`，`index.ts:82`） | 未取证 | 未取证（`feature/onboarding` 存在但归产品级引导，不在设置面板，`taixu.md:65,73`） | **✓ 无 API Key 时自动打开设置并选中数据管理分组**（`App.tsx:70-74`、`settingsDialogState.ts:19-25`） | 未取证 | 无 | 采纳「无可用 provider 时自动进入 `settings.models` 并把焦点给密钥字段」：反证 = 首跑用户进聊天无法发消息、也不知道去哪配（chat-demo 同款反证）；不采用 DSH 的 onboarding 模态（§4 第 6 条） | ✅ |
| `settings.shell.action-open-document` | 仅 loopback Host 且 `hasDocument` 时渲染；失败 `role=alert` 可重试（`SettingsDocumentAction.tsx:29-50`、`index.ts:102-111`） | 未取证 | 未取证 | ✗ | ✗ | 无 | **不做**（§4-1）：myco 无「本地设置文档」概念；反证：渲染了也无文档可开，点了只能报错（`SET-09` 反证原文） | ❌ |
| `settings.shell.update-indicator` | 桌面 `dshDesktop` 协议 v1 + 宽侧栏才渲染（`DesktopUpdateIndicator.tsx:56-80`） | 未取证 | 未取证 | ✗ | ✗ | 无 | **不做**（§4-2）：Electron 桌面专属；Android 更新走应用商店/系统更新，属系统集成不属设置面板 | ❌ |
| `settings.general.appearance` | 三块 cube：`flex:1 1 180px`、`padding:20 32`、圆角 xl、`button`+`aria-pressed`；**选中态跟持久化偏好、不跟解析后主题**（`T/client/AppearanceRow.tsx:19-63`）；命中区远超 44dp（dsh-base §2.7 中唯一达标触控目标） | 未取证 | **✓ 颜色模式 3 单选（整行）**：`PreferenceSurface` + `ThemeOptionRow`，title+description 双行，单击即生效（`settings.appearance` 字段 2/4/5；`AppearanceSettingsScreen.kt:39-129`） | **✓ 主题三选 radiogroup**：每块 `min-width:128 height:82`、`role=radio`+`aria-checked`，写 localStorage 立即重绘（`SettingsDialog.tsx:214-400,380-390`） | **✓ FilterChip 三选**（`MA:1601,1668-1690`，即时写） | **NS-07**（单选形态/语义：tile+aria-pressed vs 整行单选+radio vs chip） | 采用**整行单选（radio）**（太墟 E0）：① 移动端整行 ≥44dp 且 title+description 可读；② 反证 tile：`flex:1 1 180px` 在 390px 屏上 3 块折成 2 行、吃掉约 1/3 首屏（主题预览的价值在桌面大屏）；③ 反证 chip：只有文字、没有「跟随系统」的说明（旧 myco）；④ 反证 chat-demo 的 radiogroup：它没做方向键，但移动端读屏用 explore-by-touch 命中单选即可，不依赖方向键。**必须保留**「选中态跟偏好不跟解析值」（反证：跟实际主题时「跟随系统」永远不高亮 `BASE-58`） | ⏸（NS-07） |
| `settings.general.language` | 行内按钮 36px → 共享 Menu（`aria-haspopup=menu`+`aria-expanded`），选中即写（`C/LanguageRow.tsx:30-67`） | 未取证 | **✓ 语言 3 单选**（外观页内，`AppCompatDelegate.setApplicationLocales`，`settings.appearance` 字段 2/5） | ✓ 语言 Select（触发器 36 高圆角 18），选中即写（`svc/settings.ts`；`B/Select.tsx:14-93`） | ✗ 未取证 | **NS-08**（选择控件：整行单选 vs 菜单 vs 原生 select） | 采用**整行单选**（太墟 E0）：选项 ≤3、互斥、需显示「当前语言」；反证 menu/select：反而多一步（点开→选），且原生 `select` 在移动端会弹系统选择器、与统一控件断裂（dsh-base `language-row` 反证原文）。规则：选项 ≤4 且需说明 → 整行单选；>4 或领域枚举 → 下拉菜单（见 §3.2） | ⏸（NS-08） |
| `settings.general.font-size` | **stepper**：容器 36 高、两枚 **17×12** 箭头上下叠（中心距 14px）、hover/focus 才显形、按一下 ±1（10–22，`FontSizeRow.tsx:49-99`）；移动端必须**重排**成并排 44×44 + gap 2（`M/:802-865`，注释即 issue #2 根因） | 未取证 | **✓ 界面缩放滑杆**：80%–130%、步进 5%、拖动预览**松手落盘**（`AppearanceSettingsScreen.kt:66-68`）；另有终端字号滑杆 10–24sp | ✗ | ✗ | **NS-09**（stepper vs 滑杆） | 采用**滑杆 + 松手落盘**（太墟 E0）：① 反证 stepper：10→22 要点 12 次，且 17×12 命中区是全审计最小目标（Q-BASE-11 已登记痛点），mobile-adapt 重排后仍要连点 12 次；② 反证每帧落盘：拖动时会卡（太墟反证原文 `TX-APPEAR-02`）。落盘时机 = `onValueChangeFinished`；值标签实时显示 | ⏸（NS-09） |
| `settings.general.font-family` | 字体族列表输入（文本/代码/终端各一行），**blur 提交**、Enter 触发 blur、IME 组合期不提交、`normalizeFontFamily` 归一化、清空=恢复内置栈（`FontFamilyRow.tsx:8,34-64`） | 未取证 | 未取证 | ✗ | ✗ | 无 | 采用 DSH 语义（若做字体族）：blur 提交 + IME 安全 + 归一化；反证：只在 Enter 提交会丢改动，IME 组合期提交会打断中文输入（`BASE-63/64`）。**首版可只做字号、不做字体族**（字体族输入是文本字段，属 §3.2 文本族） | ✅ |
| `settings.general.row-switch` | Switch 36×20，**整行不可点（只有开关可点）**；切换即写；busy disabled；失败行内 `role=alert` / 持久 Toast（`DeveloperToolsRow.tsx:19-37`、`UploadRow.tsx:26-50`）；窄屏伪元素扩边 `inset:-12 -8` 到 44 高（`M/:934-945`，**无隔离带**） | 未取证 | ✓ 开关即时落盘（`toggleSubagent/toggleSkill/togglePlugin`，`AgentSettingsScreen.kt:467,580,642`；外观触感开关 `:125`） | ○ 思考/搜索开关即时写（在输入区，非设置；`CI/ChatInput.tsx:52-58`、`svc/settings.ts:127-135`） | ✗ 未取证 | 无（三家都即时写） | 采用：整行可点**或**仅开关可点？→ 取 DSH「仅开关可点」：反证 = 整行可点时用户想选中文案/滚动手势会误触；命中区靠伪元素扩到 44×44 **必须配 ≥8dp 隔离带**（Q-settings-10；`M/:934-945` 自己没做隔离，反证见 `MOB-21`）。开关写失败必须可见（行内 alert 或 Toast，按 §3.2） | ✅ |
| `settings.general.version-row` | 版本行存在于 General 页（`ui-settings-general` §1 归位表），**节点级细节未取证** | 未取证 | ✓ 设置首页「应用」组 →「关于」（`SettingsScreen.kt:34-77`） | ✗ | ✗ | 无 | 做**关于页**（`settings.about.page`）而非 General 行内版本号：反证 = 版本+许可+致谢会撑长 General 页；太墟已用「关于」独立页。版本号本身只读文本 | ✅ |
| `settings.general.zoom` | ✗（只有内容字号轴 10–22，`theme-settings.ts:24-30`） | 未取证 | ✓ 页面缩放 80%–130%（滑杆松手落盘，`AppearanceSettingsScreen.kt:66-68`） | ✗ | ✗ | 无（与 font-size 同一族，见 NS-09） | 首版只保留**一个**字号/缩放轴，避免「内容字号」+「界面缩放」两套互相叠加：采用内容字号（DSH E0 的 10–22 语义）或界面缩放（太墟 E0 的 80–130%），**由 NS-09 的控件结论一并定**。反证：两套并存时用户调完不知道是哪一套生效，且布局会乘两层缩放 | ⏸（随 NS-09） |
| `settings.about.page` | ○ 版本行 + 无独立关于页（§1 归位表） | 未取证 | ✓ 设置首页「应用」组「关于」页（`SettingsScreen.kt:34-77`） | ✗ | ✗ | 无 | 做**极简关于页**：应用名/版本/构建号/开源许可/致谢；理由：三家参考里太墟有、DSH 有版本行；反证 = 版本号没有触达路径时用户报障说不清版本 | ✅ |
| `settings.about.usage-notice` | ✗ | 未取证 | ✓ `RuntimeUsageNoticeDialog`（从设置首页「使用须知」打开，`SettingsScreen.kt:31-32,72-74`） | ✗ | ✗ | 无 | **不做**（§4-13）：myco 无法务文案与上架流程证据；若后续上架需要，作为独立条款页补，不塞进设置 | ❌ |
| `settings.diagnose.page` | ✗（DSH 无诊断页） | 未取证 | ✓ 设置首页「应用」组「诊断」页（`SettingsScreen.kt:34-77`） | ✗ | ✓ **自检 7 项**（`MA:1771-1786`、`DO:26-88`） | 无 | **保留为极简诊断页**（两家有）：只放「连通性测试 / 日志导出 / 版本与环境摘要」；**删掉**旧 myco 的 `revision=…` 调试文案与「常驻（A7：…）」内部代号（§4-10，`MA:1702,1752`）。反证：诊断能力删掉后弱网问题只能靠猜（旧 myco 用户已在用） | ✅ |
| `settings.form.value-field` | 文本/数字字段：label 行（含「已覆盖」Tag + 恢复默认）+ 32px input + 12px hint；数字仅 `inputMode=numeric`、**不限制输入内容**；非法 `aria-invalid` + 用错误文案替换 hint（行高不变）；help 展开 `role=region` 推下后续字段（`prim/settings-form/fields.tsx:49-115`、`SettingsForm.tsx:50-74`） | 未取证 | ✓ miuix 表单字段：浮动标签 + 内置 error 态（`ModelEditorScreen.kt:208-339`；`settings.models` 字段 6） | ○ API Key 输入与校验是自实现（`SE/ApiKeyField.tsx`） | ✓ OutlinedTextField（无明显校验态，`MA:1893-2018`） | 无（三家都有，细节 DSH 最全） | 采用 DSH 字段族：**只改草稿 + 逐字段就地校验 + 错误替换 hint**；反证：若数字输入自动改写（`1000`→`1K`）会吃掉用户正在敲的字符（`SET-15`）；若非法不就地提示，用户只看到保存灰着却不知道哪错（`SET-16`） | ✅ |
| `settings.form.secret-field` | **write-only**：初值永远空、空=不改 key、`type=password`+`autoComplete=new-password`、占位三态（未配置/已配置留空不改/由环境变量提供）、**无显示/隐藏按钮**、无「显隐」控件（`ProviderEditor.tsx:213-241,330-397`） | 未取证 | **✓ 有显隐**：API Key 列表 + `revealedKeyIndices` 逐条显隐 + 批量导入对话框（`ModelEditorScreen.kt:252,568,595`） | **✓ 掩码**：`'*'.repeat(apiKey.length)` 回显长度，编辑态输入框，校验后保存（`SE/ApiKeyField.tsx:11-100`） | ✓ 独立密钥对话框：明文 `OutlinedTextField`（无 password 遮罩，E0 缺口）+ 只写不读（`MA:1873-1889,2021-2037`） | **NS-05**（密钥是否回显/显隐/多条/写入点） | 采用 **write-only + 单密钥 + 仅对新输入显隐**：① 反证回显：任何回显都让密钥长度可推断（chat-demo 自己记的缺陷：掩码泄露长度），且已存密钥出现在 DOM = 安全退化（`SET-30`）；② 反证 taixu 的「已存值显隐」：它把已存密钥读回客户端才能显隐，与 DSH 的 write-only 契约冲突；③ 「显隐」只对**当前正在输入的新值**生效（防粘贴错），不显示已存值；④ 多条密钥不做（myco provider 单密钥；taixu 的 keyList 属其多环境产品复杂度） | ⏸（NS-05） |
| `settings.form.help-reset` | label 行右侧「恢复默认」+「已覆盖」Tag；help 图标 `aria-expanded`+`aria-controls`，帮助块 `role=region`；恢复默认 = **暂存一次 unset**，保存时才写（`fields.tsx:24-28,71-77`、`config-form-types.ts:17-27`） | 未取证 | ○ 可展开分段（`ModelEditorExpandableSection`），无「恢复默认/已覆盖」概念 | ✗ | ✗ | **NS-04**（配置继承模型：组合层+用户层 vs 平铺快照） | 采用 DSH「组合层/用户层」继承：每字段显示「已覆盖」+ 恢复默认（暂存 unset，保存时写）；模型目录用「继承/恢复继承」而不是复制默认值。反证：复制默认值进用户层后，默认值更新页面不再跟随（`SET-37` 单测就是为此）。**前提**：settings 写模型必须支持「层」——myco 现为 revision 乐观锁的平铺配置 → 进 Q-settings-05 | ⏸（NS-04/Q-05） |
| `settings.form.save` | 每个配置页 1 个「保存」在表单底部（**非粘底**）；门禁 `dirty && !invalid && !saving`；一次 `settings.mutate` 提交全部字段；Host 拒绝显示 `saveFailed` 且**保留草稿**；saving 文案变「保存中…」；**没有放弃按钮**（`prim/SettingsForm.tsx:1-74`） | 未取证 | **✓ 显式保存**：`save()` → `saved==true` 才 `onSaved()` 回退；失败 `profileSaveError` 流提示、停留页面（`ModelEditorScreen.kt:76,98,141-167`） | ✓ 每个动作各自提交（API Key 校验后保存；历史导入二次确认后执行） | ✓ provider 编辑器「保存」（revision 冲突显示错误，`MA:1813-1847`） | **C-15**（保存语义：全局统一即时写 vs 按页声明） | **按页声明**（C-15 采纳 DSH 侧），判据见 §3.1。本节点即「显式保存页」的唯一提交点：门禁照抄 DSH（dirty/invalid/saving 三态）+ 失败保留草稿 + busy 文案；**不做**「放弃」按钮（反证：两个出口会让人纠结「取消算不算丢弃」，DSH 用「离开即丢弃」单一口径）；但**离开出口**要补确认（§5）。**保存按钮位置**：长表单必须粘底或固定在动作栏（反证：DSH footer 不吸附，长表单滚到底才看到保存 `SET-18` 风险；太墟把保存放顶栏/底部动作区，移动端更稳）→ 建议 mobile 固定底部动作栏，见 §3.1 | ✅ |
| `settings.form.leave-discard` | 离开页面（卸载）自动 `onDiscard()` 丢弃草稿（`SettingsForm.tsx:52-54`）；**无放弃按钮**（`SettingsForm.tsx:6-10`） | 未取证 | 未取证（push 离开后的草稿行为无事实） | ○ 关闭弹窗丢弃未确认的导入选择（`dismiss`） | ○ 取消编辑器丢弃（`MA:1818-1851`） | **C-15** | 采用「离开即丢弃 + 不设放弃按钮」，但**必须补一个出口确认**（§5 新增 `settings.shell.dirty-guard`）：反证 = 移动端「系统返回键/侧滑返回」是高频误触手势，静默丢弃整页草稿且**无任何提示**，用户会以为已保存（DSH 只在桌面点关闭按钮，风险量级不同）。判据：dirty 时点关闭/返回 → 二次确认「放弃未保存的修改？」；切子页也走同一确认（或保留草稿，见 Q-settings-09） | ⏸（Q-09） |
| `settings.plugins.tab` | >1 项才显示页签，恰好 1 项直接把该项渲染为页面；`role=tablist/tab/tabpanel` + roving tabindex + ←→/Home/End + manual activation；**页签面板 keep-alive**（`visitedIds` 只增，草稿/展开/搜索态保留）（`PluginsSettingsSection.tsx:33-118`） | 未取证 | ○ 插件中心存在（设置首页「功能与数据」组），页签级事实未取证 | ✗ | ✗ | 无（页签本身；语义归 NS-02） | 若保留插件页：采用 keep-alive + 完整 tab 语义（反证：切换卸载会让配到一半的草稿丢失 `SET-49`；只给 role 不给方向键更糟 RSB-05）。**注意**：页签数量多时无溢出方案（Q-SET-14 已登记）→ 首版页签 ≤4，超出用下拉 | ⏸（Q-03 范围） |
| `settings.plugins.item-page` | 4 个内置配置页（Shell/agent-loop/web-search/subagent）内容 = 共享 `settings.form.*` 表单；每页 = 一个 `plugins.item`（`ui-settings-shell/index.ts:50-52`、`agent-loop:47-49`、`web-search:57-59`、`subagent:85-92`） | 未取证 | ○ 智能体设置页（执行/子代理/技能）形态不同（`settings.agent`） | ✗ | ✗ | 无（内容归 form 节点；页容器不另建控件） | 若这些运行时存在：直接复用 `settings.form.*`（不加新控件）——这正是 C-15 的「显式保存页」。**首版范围**：myco 是否有 agent-loop/web-search/subagent 运行时未定（Q-03）；web 搜索若沿用 provider 能力（旧 myco `web_search` 工具，`TR:59-71`）则不需要单独配置页 | ⏸（Q-03） |
| `settings.subagent.limits` | 两个数字字段（最大深度/最大并发）+ 每字段「已覆盖/恢复默认」+ info 帮助展开表格（0/1 语义）；**页面保存才写**（`SubagentLimitsFields.tsx:18-57`、`SubagentCard.tsx:29-52`） | 未取证 | ○ 子代理页 = 开关列表 + 编辑对话框（`SubagentEditorDialog.kt:1-133`），无深度/并发上限字段 | ✗ | ✗ | 无 | 若保留：采用「数字字段 + 帮助里写清 0/1 语义 + 显式保存」；反证：深度字段允许 0 却不解释语义，用户以为「禁用子代理」实际是别的行为（`SET-61`）。若 myco 无委派上限概念则不做（Q-03） | ⏸（Q-03） |
| `settings.subagent.model-selection` | 一个开关 + 开启后按 provider 分组的 checkbox 组；**开启但一个都不选 → 阻止保存**；目录失败/部分成功/不可用候选各有提示（`SubagentModelSelectionFields.tsx:22-128`） | 未取证 | ○ 子代理编辑对话框（含工具/权限配置，未逐条取证） | ✗ | ✗ | 无 | 若保留：采用「开关 + checkbox 组 + 空选门禁 + 不可用候选灰显不隐藏」；反证：隐藏不可用候选 → 用户以为模型不存在（`SET-64`）；允许空选 → 运行时才报错 | ⏸（Q-03） |
| `settings.models.page-retry` | 整页错误态：`<p role>` + secondary「重试」，失败**保留旧快照**（`ModelsSection.tsx:317-337`） | 未取证 | ○ 错误流提示（`profileSaveError`），页面级 retry 未取证 | ✗ | ○ 失败只写 `message` 文本，无重试按钮（`MA:1703-1705`） | 无（DSH 有重试；§3.6 采纳） | 采用「首载失败显示重试 + 有旧快照时后台刷新失败保留旧内容」；反证：首载失败立刻显示红色错误，用户还不知道这页有什么；后台刷新失败整页替换会打断正在编辑的草稿（`SET-21/22`） | ✅ |
| `settings.models.row` | provider 卡（模型档案）：行头 = 身份+状态点，动作区 = 编辑/移除两个独立按钮；**行本身不可点**（`ModelsSection.tsx:409-535`）；移除 → 确认弹窗；编辑 → 行下**内联展开**编辑器（形态 A，一次只开一张卡；`ModelsSection.tsx:483-491`） | 未取证 | **✓ 档案卡**：`ModelProfileCard` 整卡选预设 + 操作区可换行（`settings.models` 字段 2；`ModelProfileCard.kt`、`ModelProfilesContent.kt`） | ✗ | ✓ provider 卡 + 3 个 TextButton（编辑/设密钥/删除，`MA:1709-1734`） | **NS-05**（密钥写入点：行内按钮 vs 编辑层内字段）+ **NS-06**（编辑容器） | 采用「行 = 身份 + 状态点 + 两个图标/文字动作（编辑/删除），行本身不可点」；反证：整行可点时想选文字/滚动会误触打开编辑器（`SET-23`）。**删除「设置密钥」独立按钮**（NS-05：密钥并入编辑层，避免双写入点） | ✅（NS-05/06 见下两行） |
| `settings.models.add-flow` | 列表末尾虚线添加按钮（44px 高）→ 展开添加卡；两种模式（目录/自定义）用 SegmentedControl 切换，**两个面板 keep-alive**；写/探活中模式开关锁定；无可添加项时 disabled + title（`ModelsSection.tsx:355-395,536-682`） | 未取证 | ○ 新建模型档案入口（`ModelProfilesScreen.kt:102`），模式未取证 | ✗ | ✓ 3 个添加按钮（本地模板/DeepSeek/更多模板，`MA:1735-1741`） | 无（模板=目录内容，可并存，进 §3.6） | 采用「单一添加按钮 → 模式切换（目录/自定义）+ keep-alive」；旧 myco 的「模板对话框」**合并**为目录模式里的模板项（不删能力，见 §3.5-4）。反证：切模式卸载另一面板会丢草稿（`SET-25`）；探活中允许切模式会让响应落到已卸载面板（`SET-26`） | ✅ |
| `settings.models.delete-dialog` | 共享 Modal，`min(480px,100%)`；取消在左（`data-modal-autofocus`）、删除在右（红描边红字）；**默认焦点在取消**；先删凭据再 unset 配置，失败保留弹窗可重试；**不可撤销**（`ModelsSection.tsx:684-717`、`:141-158`） | 未取证 | 未取证 | ✗ | ✓ AlertDialog 单次确认（`MA:1853-1871`），不可撤销 | 无（三家都是单次确认；确认层级见 §3.3/Q-06） | 采用 DSH：单次确认 + **默认焦点在取消**（反证：确认按钮默认聚焦时连按 Enter 会误删 `SET-27`）+ 失败保留可重试 + 文案写明「不可撤销」；撤销能力用 §3.5-5 的配置导出兜底（不做 undo） | ✅ |
| `settings.models.editor.key` | 编辑表单内的 **write-only** 密钥字段（见 `settings.form.secret-field` 行，同一契约）；`credentialOnly` 时是唯一字段；`credentialRequired` 且全空白 → 阻止提交（`ProviderEditor.tsx:213-241,330-397`） | 未取证 | ✓ API Key 列表 + 显隐 + 批量导入（`ModelEditorScreen.kt:252,568,595`） | ✓ 掩码 + GET /models 校验后保存（`useApiKeyForm.ts:30-47`） | ○ 独立密钥对话框（`settings.provider.key-dialog`） | **NS-05** | 同 `settings.form.secret-field`：write-only + 单密钥 + 仅新输入可显隐；**密钥只在本字段写**，删除行内独立对话框（§3.5-13）。探活不在密钥字段做（反证：保存时同步探活会让「保存」卡在网络上，且探活失败≠密钥错，见 §3.3-探活） | ⏸（NS-05） |
| `settings.models.editor.customized` | 原生 `<details>`「自定义设置」：默认收起、**折叠状态不持久化**、展开把卡下方推下、箭头 120ms（reduced-motion 关）（`ProviderEditor.tsx:398-496`） | 未取证 | ✓ **可展开分段** `ModelEditorExpandableSection`，inline 长高（`settings.models` 字段 7） | ✗ | ○ 表单平铺（无折叠） | 无（形态一致：inline 展开） | 采用「高级字段默认收起 + inline 展开」：反证：高级字段直接铺开每张表单会长到两屏，常规编辑（填 key）要滚很久（`SET-31`）；展开用 G-03 形态 A（inline-uncapped）且 **无高度动画**（G-10：>240dp 直接切换）。折叠状态不持久化（反证：持久化后下次打开看到一堆展开的高级字段，主任务被噪声淹没） | ✅ |
| `settings.models.editor.fields` | 显示名（空→回落 route id）/ baseURL（空=删 key 回到继承；非法协议就地报错）/ 协议 select（原生 `select`，max-width 240）/ route id（正则 + 重名检测）；全部只改草稿（`ProviderEditor.tsx:404-470`、`CustomProviderCard.tsx:41-61,119-135`） | 未取证 | ✓ provider 选择 + baseUrl + 模型多选 + rpm/temperature/maxTokens/context/压缩/自定义 headers/能力开关（约 25 项，`ModelEditorScreen.kt:208-339`） | ✗ | ✓ id/kind/baseURL/models/ctx/max 表单（`MA:1893-2018`） | **NS-08**（协议等枚举控件：原生 select vs 菜单 vs 单选） | 采用「文本字段 + 就地校验（http/https；空=继承）+ 协议用共享下拉菜单（不是原生 select）」：反证原生 select：移动端弹系统选择器、与统一控件断裂（dsh-base `language-row` 反证）；反证清空即写：baseURL 清空后马上写 unset 会留半改状态（`SET-18`）→ 必须显式保存。route id 正则与重名检测保留（反证：数字开头会让凭据名在 POSIX shell 下非法 `SET-34`） | ✅ |
| `settings.models.editor-footer` | 编辑表单底部动作行：取消（左）/ 提交（右）；提交门禁 = 只读/unknown 布局/模型行校验/密钥格式/必填 key 缺失；**busy 时连取消也禁用**（防半途卸载）；只读部署取消仍可用（`EditorFooter.tsx:1-67`、`ProviderEditor.tsx:517-537`） | 未取证 | ✓ 顶栏/底部保存按钮，`saved==true` 才回退（`ModelEditorScreen.kt:141-167`） | ✗ | ✓ 取消 + 保存（`MA:1901-1905,2011-2014`） | **NS-06**（编辑容器：内联卡 vs 独立编辑页 vs 全屏 Dialog） | 采用**独立编辑浮层（全屏 Dialog，G-03 形态 C）+ 底部固定动作栏**：① taixu 与旧 myco 两家移动实现都选独立页/全屏 Dialog（2:1）；② 反证 DSH 内联卡：它在 720px 宽模态里有空间，手机全屏页上表单 10+ 字段 + 模型目录增删会把列表推成几屏、用户失去「在编辑哪个 provider」的上下文，且展开瞬间把下面全推走（G-10 不适用动画、直接切）；③ 用**浮层**而不是设置内的第二级导航层，保证 Q-settings-01「设置只有一层」不被破坏（若 Q-01 最终选 push 方案，这里改为推入一层）；④ 采用 busy 时禁用取消（反证：取消可用会让表单卸载后写入响应无处落地，孤儿 `SET-35`）。**门禁**：`dirty && !invalid && !saving` | ⏸（NS-06） |
| `settings.models.model-list` | 模型目录区：继承态显示「继承」标记、任何编辑物化成用户覆盖；「恢复继承」删除用户层（不复制默认值）；「拉取模型」探活端点并弹候选；空态虚框（`DeepSeekModelsEditor.tsx:247-307`、`ModelListEditor.tsx:315-461`） | 未取证 | ✓ 模型多选（候选列表 `ModelEditorCandidateList.kt`），继承语义未取证 | ✗ | ✓ models 数组直接增删（无继承概念） | **NS-04** | 采用 DSH「继承/恢复继承」：反证复制默认值：之后默认值变化页面不再跟随（`SET-37`）；空列表必须给虚线空态（反证：用户以为加载失败 `SET-38`）。**前提**同 Q-settings-05（配置分层） | ⏸（NS-04/Q-05） |
| `settings.models.model-row` | 每模型一行：id/name 两个输入 + 展开 + 删除；**删除无二次确认**（草稿态，Apply 前可取消）；行非法只影响提交门禁，行本身无红框，错误汇总在卡底「第 N 行：原因」（`ModelRow.tsx:44-114`） | 未取证 | ✓ 模型多选行（未逐条取证） | ✗ | ✓ 模型块（标题+4 输入+checkbox+强度配置，删除按钮，`MA:1927-2003`） | 无（确认层级见 §3.3） | 采用「删除无确认（草稿可逆）+ 错误汇总在表单底部」：反证：草稿态删除本来就可在提交前取消，再加确认 = 确认套确认；行内红框会在逐字输入时闪，改由提交门禁统一提示（`SET-40`） | ✅ |
| `settings.models.model-advanced` | 上下文窗口/maxTokens 用 **K/M 文本输入**（`256K`→256000，非法文本保留不等改，空=继承）；输入类型 = 文本/图片 checkbox，**至少保留一项**（最后一项 disabled）（`ModelRow.tsx:86-111`、`ModelInputTypes.tsx:32-61`） | 未取证 | ✓ maxTokens/context/能力开关等（`ModelEditorScreen.kt:208-339`） | ✗ | ✓ contextWindow/max + 思考强度 chip（`MA:1975-1997`） | 无（数值族见 §3.2） | 采用「有界小范围→stepper，无界/大范围→文本缓冲」：上下文窗口用文本 + K/M 缓冲（反证：每次击键从数字重拼会把 `1000` 改写成 `1K` 打断输入 `SET-41`）；输入类型至少留一项（反证：允许取消唯一项 → 模型无输入类型，adapter 拒绝 `SET-42`）。**删掉**旧 myco 写死 4 档的思考强度 chip（§3.5-12） | ✅ |
| `settings.models.fetch-dialog` | 拉取模型候选弹窗：Modal 520px；搜索 + 全选；**默认只勾选尚未在列表中的候选**；采用时按 id 合并（已有行保留用户值）；空候选不打开（`ModelListEditor.tsx:234-313,406-458`） | 未取证 | ○ `ModelEditorCandidateList.kt` 存在（候选列表），交互未取证 | ✗ | ✓ 「测试连通性」只把结果拼成字符串显示、不回填（D16 缺陷，`MA:1822-1831`） | 无（DSH 有「采用」，属升级，进 §3.6） | 采用 DSH：候选弹窗 + 勾选 + **按 id 合并**；反证：默认全选会覆盖用户手工调好的容量；采用时整表替换会删掉未勾选的已有行（`SET-43/44`）。**修旧 myco D16**：发现结果必须可「采用」，不能只显示字符串 | ✅ |
| `settings.models.onboarding-welcome` | 非桌面 + 公告版本未确认 → onboarding Modal（headless，无关闭 X，必须点继续；`#root` inert）（`WelcomeNotice.tsx:34-77`、`OnboardingModal.tsx:8-52`） | 未取证 | 未取证 | ✗ | ✗ | 无 | **不做**（§4-5）：myco 无版本公告机制；反证：公告可关但不记录已确认会每次再弹（`SET-45`），记录又需要一条设置项与版本通道 | ❌ |
| `settings.models.onboarding-credential` | onboarding Modal 内嵌 `ProviderEditor credentialOnly`（只有 key 字段）+「稍后」；已有可用 provider 自动完成；`credentialRequired` 时必填（`DeepSeekOnboardingDialog.tsx:58-137`） | 未取证 | 未取证 | ○ 无 API Key 自动开设置（已在 `shell.first-run-guard` 采纳） | ✗ | 无 | **不做 onboarding 槽位**（§4-6），能力由 `settings.shell.first-run-guard` 承担（直接进 models 页填 key）；反证：另造一个 onboarding 模态会让「配 provider」有两套入口（槽位版 + models 页版） | ❌ |
| `settings.plugin-inventory.card` | 插件清单卡：两列网格、整卡按钮展开、**一次只开一张**、卡内含 jump-link 嵌套按钮（Q-SET-16 缺陷）；失败项置顶+危险描边（`PluginInventorySettingsTab.tsx:123-178,251-602`） | 未取证 | 未取证 | ✗ | ✗ | 无 | 首版**不做**（插件运行时范围见 Q-03）；若做：一次只展开一张（反证：多张展开在网格里形成高低不齐长列，滚动丢上下文 `SET-51`）+ 禁止嵌套按钮（Q-SET-16） | ⏸（Q-03） |
| `settings.plugin-inventory.group-toggle` | 分组折叠头：预设组默认展开、全局面默认折叠；**搜索期间强制都展开**（`PluginInventorySettingsTab.tsx:489-597`） | 未取证 | 未取证 | ✗ | ✗ | 无 | 若做：采用「搜索时强制展开」：反证：保持折叠会把搜索结果藏起来，看起来「搜不到」（`SET-53`） | ⏸（Q-03） |
| `settings.plugin-inventory.preset-switcher` | 预设切换 Menu：**只改「清单页正在看哪个预设」，不写配置、不改默认预设**（`PluginInventorySettingsTab.tsx:489-529`） | 未取证 | 未取证 | ✗ | ✗ | 无 | 若做：保持「查看态不改配置」：反证：只是查清单却把全局默认预设改了（`SET-55`） | ⏸（Q-03） |
| `settings.plugin-inventory.jump-link` | 「在预设中查看」/「其它预设命中」跳转：只切查看上下文（`PluginInventorySettingsTab.tsx:408-436,546-560`） | 未取证 | 未取证 | ✗ | ✗ | 无 | 若做：保留跳转但不写配置；同步修嵌套按钮（改成卡片同级按钮，`SET-52`） | ⏸（Q-03） |
| `settings.plugin-inventory.search` | 清单页顶部搜索：本地过滤 moduleName/entryId/title/description，本地化标题也可搜；无匹配 `role=status`（`PluginInventorySettingsTab.tsx:474-487`） | 未取证 | 未取证 | ✗ | ✗ | 无 | 与 `settings.shell.search`（全局）**不重复**：这是单页内过滤，若保留插件页则保留它；两者共存时以全局搜索为主入口 | ⏸（Q-03） |
| `settings.plugin-inventory.retry` | 两个失败面**各自重试**（Host 清单拉取失败 / 客户端模块同步失败）（`PluginInventorySettingsTab.tsx:321-324,438-472`） | 未取证 | 未取证 | ✗ | ✗ | 无 | 若做：两个失败面各自重试（反证：共用一次重试时，重试清单不会修复客户端图，用户反复失败 `SET-60`） | ⏸（Q-03） |
| `settings.agent-preset.card` | 预设卡：网格 `minmax(268px,1fr)`；cardMain 整卡上半可点=「设为默认」+ `aria-pressed`；broken 卡可看不可设默认；设默认会尝试把当前空白会话同步（`AgentPresetSection.tsx:64-176`） | 未取证 | 未取证（太墟有子代理/技能/插件，无 agent preset） | ✗ | ✗ | 无 | 首版**不做**（myco 无 agent preset 概念，Q-03）；若做：broken 卡禁止设默认（反证：保存一个加载不了的预设，每次新会话都失败 `SET-67`）+ 设默认后同步空白会话（`SET-68`） | ⏸（Q-03） |
| `settings.agent-preset.creator` | 自定义组底部 44px 虚线按钮 → 暂存 cordis 预设并**关闭设置**开新会话（`AgentPresetSection.tsx:84-100`、`index.ts:177-206`） | 未取证 | 未取证 | ✗ | ✗ | 无 | 若做：必须关闭设置层再开会话（反证：不关闭时新会话被设置遮罩挡住、介绍动画看不见 `SET-69`）——这条对 M2 的「跨面板跳转」有用 | ⏸（Q-03） |
| `settings.agent-preset.help` | 仅内置预设卡脚两个 ghost 按钮 → 帮助 Modal（explanation/usage 两页签）；只读、不改选中（`AgentPresetSection.tsx:142-153`、`PresetGuideDialog.tsx:19-34,87-160`） | 未取证 | 未取证 | ✗ | ✗ | 无 | 若做：自定义预设不显示帮助（反证：展示内置文案会误导 `SET-70`） | ⏸（Q-03） |
| `settings.agent-preset.view` | 卡脚图标按钮 → 只读 YAML 查看器 Modal；Esc 捕获阶段只关本层；Tab 陷阱（`AgentPresetSection.tsx:148-174`、`PresetGuideDialog.tsx:36-53`） | 未取证 | 未取证 | ✗ | ✗ | 无 | 若做：Esc 只关最上层（反证：按下 Esc 被踢出整个设置 `SET-72`）+ Tab 陷阱（`SET-73`） | ⏸（Q-03） |
| `settings.agent-preset.guide-dialog` | 帮助弹窗：页签 + 两页独立滚动 + `hidden` 不卸载 + Tab 陷阱 + 焦点回卡（`PresetGuideDialog.tsx:36-160`） | 未取证 | 未取证 | ✗ | ✗ | 无 | 若做：两页各自独立滚动（反证：共享滚动容器切页后停在上一页位置 `SET-74`） | ⏸（Q-03） |
| `settings.account.menu` | 侧栏账户启动器（44px 整行 / rail 36×36）→ 向上弹 Menu（设置/联系我们/登录或退出）；退出前先查运行中任务（`AccountMenu.tsx:22-91`） | 未取证 | **✗ 无账户概念**（设置首页有环境/数据/应用三组，无账户） | ✗ | ✗ | 无（范围，不是互斥） | **不做**（§4-4）：4 家参考里只有 DSH 有账户（DeepSeek 官方账号/余额）；myco 本地优先、凭据在 provider 层。反证：做账户页却没有账号后端，等于一个永远登录不上的页面 | ❌ |
| `settings.account.signed-out` | 未登录引导（居中 + 登录按钮 + 失败文案）（`AccountSection.tsx:84-153`） | 未取证 | ✗ | ✗ | ✗ | 无 | **不做**（§4-4，同上） | ❌ |
| `settings.account.card` | 已登录身份卡 + 余额卡（多币种/bonus/usage/topUp 外链）（`AccountSection.tsx:84-215`） | 未取证 | ✗ | ✗ | ✗ | 无 | **不做**（§4-4） | ❌ |
| `settings.account.sign-in-dialog` | 登录弹窗（授权/复制链接/取消/改用 API Key；关闭会取消 attempt）（`SignInDialog.tsx:10-89`） | 未取证 | ✗ | ✗ | ✗ | 无 | **不做**（§4-4）；「改用 API Key」这条路径由 `settings.models` 的密钥字段覆盖 | ❌ |
| `settings.account.sign-out-dialog` | 退出确认：描述分「有运行任务/未知/无」三种（`SignOutDialog.tsx:7-41`） | 未取证 | ✗ | ✗ | ✗ | 无 | **不做**（§4-4） | ❌ |
| `settings.account.notices` | bonus 到账卡（锚定启动器上方，跟随侧栏/窗口）+ 配额通知 Modal/降级 Toast（`AccountNotice.tsx:50-118`、`AccountQuotaNotice.tsx:29-81`） | 未取证 | ✗ | ✗ | ✗ | 无 | **不做**（§4-4）：依附账户体系；G-01 的浮层不占位要求在这里仍成立（若未来做） | ❌ |
| `settings.account.platform-overlay` | 桌面内嵌 Platform 页（充值/用量）：`fixed inset:0` + 背景 inert + 返回/重试（`PlatformOverlay.tsx:20-87`） | 未取证 | ✗ | ✗ | ✗ | 无 | **不做**（§4-3）：依赖 Electron `bridge.open/setBounds/close`，Android 无对应；替代 = 外部浏览器打开（若未来接官方额度） | ❌ |
| `settings.account.onboarding.step` | 桌面首次启动全屏引导（welcome/credit/purpose/process 四步）（`DesktopOnboarding.tsx:28-125`） | 未取证 | ✗ | ✗ | ✗ | 无 | **不做**（§4-3）：桌面专属；移动首跑引导属产品级流程（太墟把 onboarding 放在 settings 之外，`taixu.md:73`） | ❌ |
| `settings.account.onboarding.nav` | 上述引导的底部导航（返回/跳过/保存失败重试）（`DesktopOnboarding.tsx:95-99,114-118`） | 未取证 | ✗ | ✗ | ✗ | 无 | **不做**（§4-3） | ❌ |
| `settings.account.onboarding.confirmation` | 跳过/无额度确认弹窗（`OnboardingConfirmation.tsx:6-35`） | 未取证 | ✗ | ✗ | ✗ | 无 | **不做**（§4-3） | ❌ |
| `settings.data.history` | 数据管理：导入（选 ZIP → 二次确认 → 进度 → 原子替换）、导出 ZIP、删除全部（二次确认）；三者串行互斥；进度 `role=progressbar`（`SettingsDialog.tsx:107-212,323-349,445-609`、`backup.ts:1-58`） | 未取证 | ✓ **本地备份**（设置首页「功能与数据」组 →「本地备份」；`SettingsScreen.kt:34-77`）；模型档案另有 JSON 导入导出（E1 `README.md`） | **✓ 完整**（导入覆盖 + 导出下载 + 删除全部 + 二次确认 + 进度条） | ○ 只有 provider 配置导出/导入；无历史备份 | 无（三家做法并存，进 §3.6） | **做**（太墟 + chat-demo 两家有）：采用「导入=先暂存再原子替换」「导出=ZIP」「删除全部=二次确认 + 不可撤销」；反证：导入不做原子替换，选错文件会立刻清空全部历史且无法回滚（chat-demo 源码正是为此用 staging store，`db.ts:26-32`，见 `ref/chat-demo.md` §2.27 字段 13；备份编排在 `svc/chat/backup.ts:1-58`）；三者必须串行互斥（反证：并发读写同一库会导出半替换数据）。**危险层级**见 §3.3/Q-06 | ✅（危险层 ⏸） |
| `settings.provider.export-import` | ✗（无 provider 配置导出/导入；靠账户云同步替代） | 未取证 | ✓ **JSON 导入预览 + 复制或更新 + 导出 + 系统分享**（E1 `README.md`；`ModelEditorImportDialogs.kt`、`ModelImportDialog.kt`、`ModelExportDialog.kt`） | ✗ | ✓ 导出/导入 JSON，**整份替换**不是 merge（`MA:1620-1658`、`IB:282-291`），revision 冲突报错 | 无（可并存，进 §3.6） | **保留**（太墟 + 旧 myco 两家有）：导出只导 auth ref 与 credential 状态、不导密钥值（旧 myco 现状正确，`IB:882-893`）；导入必须**先预览再确认**（修旧 myco 的「直接整份替换」风险）。反证：没有导出导入，换机/分享配置只能手抄（旧 myco 反证原文） | ✅ |
| `settings.session.bin-rows` | ✗（DSH 无回收站，归档在会话行菜单） | 未取证 | 未取证 | ✗ | ✓ 设置页「会话」卡两行：归档会话/回收站，叠在设置层之上（`MA:1691-1699,252-256`） | 无（范围 + 与 sidebar 面板归属重叠） | **保留入口但移到「会话数据」子页**（与 `settings.data.history` 同页）：反证：归档/软删的会话没有任何入口找回 = 删除即永久丢失（旧 myco 反证原文 `LEG-27`）。**归属提醒**：回收站列表本体属 sidebar 面板（M3），本任务只留入口 | ✅（归属待 M3 对齐） |
| `settings.provider.template-pick` | ○ 添加卡里的「目录模式」本身就是模板来源（`ModelsSection.tsx:589-604`） | 未取证 | 未取证 | ✗ | ✓ 独立模板对话框（6 个写死模板，`MA:1790-1816,2196-2213`） | 无（模板项可并入 add-flow，见 §3.6） | **合并**（不删能力）：模板项并入 `settings.models.add-flow` 的目录模式列表，删除独立对话框；反证：独立对话框与目录模式是同一件事的两个入口，已选目录模式时再弹模板框会让用户以为「添加」有两条路。写死模板的 `anthropic kind=deepseek-messages` 问题随模板数据迁移一并修（Q-LEG-04） | ✅ |
| `settings.provider.key-dialog` | ✗（密钥在编辑表单内） | 未取证 | ✗（密钥在编辑表单内） | ✗ | ✓ 独立「设置密钥」AlertDialog（`MA:1873-1889,2021-2037`） | **NS-05**（密钥写入点：独立对话框 vs 编辑层内字段） | **合并 → 删除独立对话框**：密钥写入只保留 `settings.models.editor.key` 一处；反证：两个写入点 = 双真源，且独立对话框无法处理「只改 baseURL 保留 key」的语义（空输入=不改的约定没有共存空间）。旧 myco 对话框的「值只写不读、不回显」契约由新字段继承 | ⏸（NS-05） |

---

## 3. 补充项（不互斥的差异，不进对照表的冲突列）

> 本节把**规则与处置**落成可写进 P3 的表格。C-15 的结论（§3.1）、设置项类型族（§3.2）、危险操作（§3.3）、与 M5 的边界（§3.4）、旧 myco 逐条处置（§3.5）、其它可并存差异（§3.6）都在这里。

### 3.1 C-15 结论：保存语义按页声明

**判据（可验收）**：① **含自由文本 / URL / 密钥 / 数值输入的页 = 显式保存**（有明确提交动作，离开不写、不加「放弃」按钮）；② **全为开关 / 单选 / 单值选择 / 滑杆的页 = 即时写**（滑杆等连续控件用「松手落盘」），且写失败必须可见；③ **同一页不得混用两种语义**（否则用户无法预测改动何时生效）。依据：DSH 自身两种并存（`SettingsForm.tsx:6-10,50-74` vs `DeveloperToolsRow.tsx:30-35`、`ProviderEditor.tsx:309-322`）；太墟独立印证「外观即时、模型显式保存」（`settings.appearance` 字段 5、`settings.models` 字段 5 与反证）。

| 页/区域 | 语义 | 触发时机 | 依据 | 反证（不这么做会坏在哪） |
|---|---|---|---|---|
| General：主题 / 语言 / 开关行 | **即时写** | 点选/切换即写；失败行内 `role=alert` 或持久 Toast | DSH `AppearanceRow.tsx:54`、`DeveloperToolsRow.tsx:19-36`；太墟 `setThemeMode` 即时（`AppearanceSettingsScreen.kt:52`） | 要「保存」才生效 → 用户改完返回才知道效果，中途退出全丢（太墟反证原文 `TX-APPEAR-01`） |
| General：字号 / 界面缩放（滑杆） | **松手落盘** | 拖动只改本地态预览，`onValueChangeFinished` 落盘 | 太墟 `setAppFontScale`（`AppearanceSettingsScreen.kt:66-68`）；反证 `TX-APPEAR-02`（每帧落盘拖动会卡） | 每帧写库 → 拖动卡顿、全树重组 |
| models 页：列表级操作（删除 provider、添加入口） | **即时写** | 确认后立即删除；添加走独立编辑层 | DSH `ModelsSection.tsx:295-315`；太墟 `saveModels` 只在保存时写（列表级不写） | 删除要等页面「保存」才生效 → 确认弹窗变成两次确认 |
| models 编辑（provider 字段 / 密钥 / 模型目录） | **显式保存**（独立编辑浮层，底部固定动作栏） | `dirty && !invalid && !saving` 才可提交；成功才回退；失败留页 | DSH `ProviderEditor.tsx:261-307`、`EditorFooter.tsx:1-67`；太墟 `saved==true` 才 `onSaved()`（`ModelEditorScreen.kt:141-167`） | 即时写 → 改一半 baseURL 就写 unset，弱网/半成品全落盘（`SET-18` 反证原文） |
| 插件配置页（若保留 4 页） | **显式保存**（页级，底部动作栏） | 同 form.save；离开 = 丢弃 + dirty 确认 | DSH `SettingsForm.tsx:1-74` | 同上一行 |
| 数据管理 / 本地备份 | **动作 + 二次确认**（无草稿概念） | 选文件 → 确认 → 执行 → 进度 → 结果 | chat-demo `SettingsDialog.tsx:445-609`；太墟「本地备份」 | 对一次性动作谈「草稿」没有意义；不确认就执行 = 选错文件即清库 |
| 关于 / 诊断 | **只读**（诊断页的测试按钮是动作） | — | 太墟设置首页「应用」组（`SettingsScreen.kt:34-77`） | — |

**为什么这样分不会让用户困惑**：判据②/① 的分界是「有没有中间态」——开关/单选按下即最终态（再按一次可逆），文本/密钥/URL 有「改了一半」的中间态。**并且**同一页只出现一种语义，用户只要认出「这一页有输入框」，就知道底部有保存按钮。**收尾要求**：显式保存页离开时必须确认（§5 `settings.shell.dirty-guard`），否则判据①的「离开不写」会变成静默丢数据。

### 3.2 设置项类型族（控件 / 校验 / 失败反馈）

| 类型族 | 控件形态 | 校验与反馈 | 失败/边界 | 依据 |
|---|---|---|---|---|
| 开关（bool） | Switch 36×20；**只有开关可点、整行不响应**；触屏伪元素扩到 44×44 + **≥8dp 隔离带** | 切换即写；busy → disabled；写失败 → 行内 `role=alert`（字段级）或持久 Toast（页面级结果） | 只读/加载中 disabled；相邻行命中盒不得相交 | DSH `DeveloperToolsRow.tsx:19-37`、`UploadRow.tsx:26-50`；mobile `M/:934-945`（无隔离带 → 反证 `MOB-21`）；Q-settings-10 |
| 单选（≤4 项，需说明） | **整行单选**（title + description 双行，`role=radio` 或原生 radio 语义），选中即生效 | 选中态跟**偏好**不跟解析值；选中即写 | 长语言/大字号不换行裁切（行内可折行） | 太墟 `AppearanceSettingsScreen.kt:39-129`；反证 tile/chip 见 NS-07 |
| 单选（>4 项或领域枚举） | 按钮/行 → **共享下拉菜单**（portal、`aria-haspopup=menu`+`aria-expanded`、视口内夹边） | 选中即写；当前值不在选项时显示原始 id | 菜单可滚；Esc 只关菜单（`captureEscape`） | DSH `LanguageRow.tsx:30-67`、`base.primitives.menu`；chat-demo `captureEscape` 反证（`B/Select.tsx:38-39`） |
| 多选（≤10 项） | Checkbox 16×16 + label 包整行；`fieldset`+`legend`；在显式保存页内只改草稿 | 空选门禁（如子代理模型授权）；至少保留一项（最后一项 disabled） | 不可用候选灰显不隐藏 | DSH `ModelInputTypes.tsx:32-61`、`SubagentModelSelectionFields.tsx:22-128` |
| 短文本 | input 32px + `<label for>` + hint 12px（secondary 一档，G-09） | 只改草稿；非法 `aria-invalid` + **错误替换 hint**（行高不变）；help 展开 inline 推下 | 空值 = 继承（占位显示默认）；只读 disabled | DSH `fields.tsx:49-115`；G-09 |
| 长文本 / URL / 路径 | 同短文本 + 协议校验（仅 http/https，本地可放行 `http://127.0.0.1`） | 空 = 删 key 回到继承；非法就地报错 + 提交禁用 | 路径选择器**参考未取证** → Q-settings-08 | DSH `ProviderEditor.tsx:404-470`；太墟「外部模型必须 HTTPS、本地允许 127.0.0.1」（E1 `ARCHITECTURE_RULES.md:76`） |
| 密钥 | `type=password` + `autoComplete=new-password`；**write-only**（初值空、空=不改）+「已配置」占位；显隐只作用于**正在输入的新值** | 格式校验（不调网络）；提交门禁：必填时全空白阻止 | 环境锁定 → disabled；任何时刻 DOM 不含已存明文 | DSH `ProviderEditor.tsx:213-241,330-397`；NS-05 |
| 数值（有界小整数，如并发上限 1–8） | **stepper** 两枚按钮；移动端必须**重排为并排 44×44 + gap≥2**（不能只扩边） | 到 min/max 对应按钮 disabled；读屏须报当前值（补 `aria-valuenow` 或把值写进 aria-label；DSH 缺失 → Q-BASE-12） | 非法/越界 → 夹紧或禁用 | DSH `FontSizeRow.tsx:49-99`；mobile `M/:802-865`（17×12 中心距 14px → 扩边无解，必须重排） |
| 数值（连续范围，如字号 10–22 / 缩放 80–130%） | **滑杆**：拖动实时预览值标签，**松手落盘** | 值标签实时；步长声明（字号 1px、缩放 5%） | 到边界不越界（不循环） | 太墟 `AppearanceSettingsScreen.kt:66-68,117-120`；NS-09 |
| 数值（无界/大范围，如上下文窗口） | **文本输入** `inputMode=numeric`，不限制输入内容；K/M 文本缓冲，blur 结算，**不静默改值** | 非法 → 提交禁用 + 表单底部「第 N 行：原因」 | 空 = 继承（placeholder 为默认） | DSH `fields.tsx:52-57`、`DeepSeekModelsEditor.tsx:29-68` |
| 危险操作 | danger 按钮（红描边红字）+ 确认 Modal；**默认焦点在取消**；文案写明不可撤销 | 提交中禁用；失败保留弹窗可重试 | 只对可删除项出现（removable） | DSH `ModelsSection.tsx:684-717`；§3.3 |
| 只读清单/状态标签 | 卡片整卡按钮展开、**一次只开一张**；状态 Tag/危险描边 | 无写；搜索时强制展开分组 | 失败项置顶；空态文案 | DSH `PluginInventorySettingsTab.tsx:123-178,251-602`（若做） |

### 3.3 危险操作：确认层级与撤销

| 操作 | 参考做法 | 建议层级 | 撤销 | 反证 |
|---|---|---|---|---|
| 删除 provider | DSH 单次 Modal（默认焦点取消、先删凭据再 unset、不可撤销）；旧 myco 单次 AlertDialog；太墟未取证 | **单次确认**（不做二次输入）：默认焦点在取消 + 文案写明「不可撤销」+ 说明会同时删除凭据 | **无 undo**；恢复路径 = 配置导出（§3.6） | 二次输入把成本加在每次删除上，而 provider 是低频、可重配的对象；真正要防的是「连按 Enter 误删」→ 用默认焦点在取消解决（`SET-27`） |
| 删除模型行（编辑层内） | DSH **无确认**（草稿态，提交前可取消） | **无确认 + 草稿可逆**（与 DSH 一致） | 取消编辑层即撤销 | 草稿态再加确认 = 确认套确认；误删在提交前可取消（`SET-40` 同源） |
| 清空全部会话 / 导入覆盖 | chat-demo 单次二次确认 Modal（不可撤销、导入用 staging 原子替换）；太墟「本地备份」 | **二次确认 + 列出将删除的数量 + 不可撤销**；导入必须「先预览再确认」 | **无 undo**；旧 myco 的软删 + Snackbar 撤销只用于**单条会话删除**（旧 E0，`sidebar.delete.confirm`） | 清库不可 undo，所以确认必须显示「影响面」（数量）；不做输入名确认（Q-06 待拍，参考都无先例） |
| 退出登录 | DSH 单次确认 + 先查运行中任务并写明影响 | **不做**（无账户，§4-4） | — | 若未来做：必须提示「有 N 个任务在跑」（`SET-86`） |
| 全局「重置全部设置」 | DSH 未实现（Q-SET-03）；无参考 | **不做**（§4-7）；单字段「恢复默认」已覆盖 | — | 全局重置需要撤销窗口与二次确认设计，收益低 |

**探活（连通性测试）的归属**：key 字段不做保存时探活；探活 = `settings.models.fetch-dialog` 的「拉取模型」按钮（DSH E0）。反证：保存时同步探活会让保存卡在网络等待上，且探活失败≠密钥错（baseURL 也可能错），用户无法判断改哪里。

### 3.4 与 M5 `model` 面板的边界（同一控件不许两处定义）

| 能力 | 归属 | 依据 | 另一侧必须**没有**什么 |
|---|---|---|---|
| provider 增/删/改、密钥、baseURL、协议、route id | `settings.models`（M6） | dsh-settings §2.3 | M5 model 面板不得出现 provider 配置入口 |
| 模型目录：id/name、上下文窗口、maxTokens、输入类型、拉取候选 | `settings.models.model-list / model-row / model-advanced / fetch-dialog`（M6） | 同上 | M5 不得有「编辑模型/容量」入口 |
| 上下文长度 | 逐模型配置在 `settings.models`（M6）；M5 不显示不可改 | dsh-settings §5 维度表（「面板没有此维度」） | M5 不得有上下文长度控件 |
| 会话内「模型 × 推理等级」选择（含搜索、双入口共享 per-session 状态） | **M5 `model` 面板**（`composer` 座位 + `/model` 弹窗） | dsh-settings §2.8、§5 | settings 不得出现「当前会话模型」控件 |
| 默认值（provider 级 defaultContextWindow/maxTokens） | `settings.models` | `ProviderEditor.tsx:14-19` | M5 不得改默认值（只读展示也不做，见 §5 表） |
| agent preset（若做） | `settings.agent-presets`（M6）+ 新会话 hero chip（M5/`model`） | dsh-settings §2.6 | `settings.models` 不放 preset |
| 模型可用性 / 探活 | `settings.models`（拉取按钮）；M5 只在目录加载失败时显示错误条 + Retry | `ModelListEditor.tsx:315-461` vs `ModelSelect.tsx:538-552` | 不要把探活做成 M5 菜单里的按钮 |
| 写作用域 | settings 写**配置层**（provider profile + model catalog）；M5 写**会话投影**（selectModel） | E0 两处写点不同 | 同一字段不得两处可写 |

### 3.5 旧 myco 设置项逐条处置（14 节点 + 设置页壳）

| # | 旧节点 | 旧做法（E0） | 处置 | 去向 / 理由 |
|---|---|---|---|---|
| 1 | 设置页壳（页 + 搜索 + 顶栏返回） | 覆盖层从右滑入；页内 LazyColumn 分组卡；搜索过滤；顶栏返回（`MA:409-456,1661`；`CP:206`；D10） | **保留能力、改形态** | 形态按 NS-01（设置层 + 同层子页）；搜索保留并升级（NS-03）；返回 = 关层（见 §2） |
| 2 | `settings.appearance.theme-chip` | 3 个 FilterChip 即时写（`MA:1668-1690`） | **保留 → 合并** | 进 `settings.general.appearance`，形态改整行单选（NS-07） |
| 3 | `settings.session.bin-rows` | 设置页两行 → 归档/回收站（叠第二层，`MA:1691-1699,252-256`） | **保留 → 移位** | 进「会话数据」子页；入口不能删（否则归档/软删无找回） |
| 4 | `settings.provider.row-actions` | 3 个 TextButton：编辑/设密钥/删除（`MA:1709-1734`） | **合并** | 编辑/删除 → `settings.models.row`；「设密钥」**删除**（NS-05，密钥进编辑层） |
| 5 | `settings.provider.add-actions` | 3 个添加按钮（本地模板/DeepSeek/更多模板，`MA:1735-1741`） | **合并** | 收敛为 `settings.models.add-flow` 一个入口 + 目录模式 |
| 6 | `settings.provider.export` | 导出 provider JSON（`MA:1620-1634,1744`） | **保留** | 进 `settings.provider.export-import`（§3.6） |
| 7 | `settings.provider.import` | 导入 → **整份替换**（`MA:1635-1658`、`IB:282-291`） | **保留 + 改语义** | 保留能力，改为「先预览再确认」；整份替换保留但必须显示将覆盖的条数 |
| 8 | `settings.foreground.toggle` | 前台服务启动/停止（`MA:1750-1770`） | **删除** | §4-10：工程/系统服务控件 + 状态不查询真实服务（`running` 只是本地态）会误导 |
| 9 | `settings.templates.pick` | 独立模板对话框（6 个写死模板，`MA:1790-1816`） | **合并** | 模板 = add-flow 目录模式的内容（§2 `provider.template-pick`） |
| 10 | `settings.provider-editor.save` | 全屏编辑器底部保存，revision 冲突报错（`MA:1818-1851,2011-2014`） | **保留** | = `settings.models.editor-footer`（显式保存，C-15） |
| 11 | `settings.provider-editor.discover` | 测试连通性，结果只拼字符串、不回填（D16，`MA:1822-1831`） | **保留 + 升级** | = `settings.models.fetch-dialog`：候选可勾选、按 id 合并（修 D16） |
| 12 | `settings.provider-editor.add-remove-model` | 模型块增删（最后 1 个不可删，`MA:1927-2003`） | **保留** | = `model-list` + `model-row`；保留「最后一项不可删」语义 |
| 13 | `settings.provider-editor.model-level-chip` | 写死 4 档 off/low/medium/high 的默认思考档（`MA:1975-1997`） | **删除** | §4-11：与 Host efforts 任意 Map 冲突（导入自定义档无法编辑）；effort 交 M5 面板 / catalog |
| 14 | `settings.key.save` | 独立「设置密钥」AlertDialog，明文输入、只写不读（`MA:1873-1889,2021-2037`） | **合并 → 删除** | 密钥并入 `settings.models.editor.key`（NS-05）；保留「只写不读」契约 |
| 15 | `settings.provider.delete-confirm` | 单次 AlertDialog（不可撤销，`MA:1853-1871`） | **保留** | = `settings.models.delete-dialog`；补「默认焦点在取消」 |

> 另有两项**非节点**但属于旧设置页的工程控件：`revision=$revision · 保存冲突会提示重试`（`MA:1702`）、「常驻（A7：不承诺 24/7，尽力而为）」（`MA:1752`）→ **删除**（§4-10）。旧 `设置页搜索无匹配无提示`（D10）→ **按新搜索结果页修掉**（§2 `shell.search-result`）。

### 3.6 其它可并存差异（采纳 / 不采纳）

| 差异 | 谁有 | 采纳 | 理由 |
|---|---|---|---|
| 设置入口在侧栏底部 | DSH / chat-demo / 旧 myco / 太墟（抽屉底部） | ✅ 采纳（四家一致） | 反证：藏进头像菜单首跑找不到 |
| 带参打开子页 + 记上次子页 | chat-demo / 太墟 | ✅ 采纳 | 首跑引导与外部跳转需直达 |
| 关闭后焦点归还触发按钮 | DSH（`SET-04`） | ✅ 采纳 | 反证：焦点落 body，Tab 从头开始 |
| 桌面快捷键（`Ctrl/Cmd+,`） | DSH | ✅ 桌面端采纳；移动无键盘不做 | 反证：无快捷键不构成缺陷，移动端无按键 |
| 加载失败可重试（页面级 / 两个失败面各自重试） | DSH | ✅ 采纳 | 反证：只显示错误无出口（旧 myco 的 `message` 文本） |
| 页面级结果用 Toast、字段级错误用行内 | DSH（日志上传 vs 开发者工具） | ✅ 采纳 | 反证：字段错误用全局 Toast 会丢掉「哪个字段错」 |
| busy/saving 的 a11y 文案 | DSH（文案变化 + `role=status`） | ✅ 采纳（G-08） | 反证：读屏用户不知道在保存 |
| 设置层高度取视口、不取内容 | DSH（`css:78-84` 注释） | ✅ 采纳 | 反证：切子页外框跳动（`SET-03`） |
| 内容区 `overscroll-behavior:contain` | mobile-adapt（`M/:787-796`） | ✅ 采纳（G-04） | 反证：内滚到边把速度传给外层，读者丢位置 |
| 窄屏设置全屏化、去遮罩 | mobile-adapt（`M/:718-737`） | ✅ 采纳 | 反证：390px 屏上两侧遮罩只剩窄条、内容区不能滚（`MOB-14`）；全屏面板不透明，遮罩白费一层 blur |
| 窄屏顶部导航横滑 + 隐藏滚动条 | mobile-adapt | ✅ 采纳（若用 tab 形态） | 反证：8 个 tab 竖排吃掉首屏 1/3（`MOB-16`） |
| 大标题随滚动折叠 | 太墟（`exitUntilCollapsedScrollBehavior`） | ❌ 不采纳 | 反证：折叠后关闭/返回被挤进小顶栏；且需要 nestedScroll 与 overscroll 的**固定顺序**（`taixu.md:49`、`RuntimeSettingsScaffold.kt:98-109`），实现成本高于 2–3 屏设置页的收益 |
| 设置内独立「分类卡首页」 | 太墟 | ❌ 首批不做（附触发条件） | 见 §2 `shell.home`：子页 >7 时才启用 |
| 添加 provider 的两种模式 keep-alive | DSH | ✅ 采纳（若自定义 provider 保留） | 反证：切模式丢草稿 / 探活响应落空（`SET-25/26`） |
| provider 配置 JSON 导入/导出/分享 | 太墟（E1）/ 旧 myco（E0） | ✅ 采纳（§3.5-6/7、§2 `provider.export-import`） | 反证：换机/分享配置只能手抄 |
| 「拉取模型」结果可勾选采用 | DSH | ✅ 采纳 | 修旧 myco D16（只显示字符串=半成品） |
| 数据导入「先暂存再原子替换」 | chat-demo / 太墟 | ✅ 采纳 | 反证：选错文件立刻清空历史且不可回滚 |
| 会话删除 = 软删 + Snackbar 撤销 + 回收站 | 旧 myco | ✅ 采纳（单条） | 比 undo 更强；「删除全部」不适用（见 §3.3） |
| 设置搜索**形态**：结果列表 + 定位跳转 | 太墟 E0；旧 myco 是过滤式（缺陷 D10） | ✅ 采纳（§2 `shell.search`） | 反证：过滤式隐藏卡片 + 无匹配无提示（D10） |

**搜索的具体写法（若 Q-settings-04 拍「做」）**：
1. **入口**：设置层顶栏放搜索图标（常驻，与关闭按钮同区）；点击展开为搜索态（不是子页、不入栈）。
2. **范围**：所有已注册子页的全部设置条目（条目名 + 别名 + 子页名），**不**匹配长说明/帮助正文；本地过滤、不分词、大小写不敏感（照 DSH 清单搜索的 `includes` 语义，并补拼音/别名——太墟注册表含拼音关键词）。
3. **结果行**：`子页名 · 条目名` + 命中高亮；无匹配 → 「无匹配，试试『字号 / 密钥 / 主题』」+ 热门关键词 chips（太墟有热门标签，E0）。
4. **跳转定位**：点击 → 关搜索态 → 切到目标子页 tab → `scrollTo(anchorId)` → 目标行高亮 2s（不弹 Toast）。每行在 P3 必须有稳定 `anchorId`（= 节点 ID）。
5. **退出**：Esc / 关闭搜索 = 回原 tab 原位置（不关设置）；再按返回才关设置层（与 §2 `shell.back` 的单层语义一致）。
6. **不做**：过滤隐藏其它卡片（D10 缺陷）、跨面板搜索（会话/模型面板各自有搜索）。

---

## 4. 明确不做（参考里有的）

| # | 能力 | 出处 | 不做的理由 |
|---|---|---|---|
| 1 | `settings.shell.action-open-document`（打开本地设置文档） | DSH `SettingsDocumentAction.tsx:29-50` | myco 无「本地设置文档」概念；渲染了也只能报错（`SET-09` 反证） |
| 2 | `settings.shell.update-indicator`（桌面更新指示器） | DSH `DesktopUpdateIndicator.tsx:56-80` | Electron 桌面专属；Android 更新走商店/系统，不属设置面板 |
| 3 | `settings.account.platform-overlay` + `onboarding.step/nav/confirmation`（桌面内嵌平台页 + 桌面首跑引导） | DSH `PlatformOverlay.tsx`、`DesktopOnboarding.tsx` | 依赖 Electron bridge 与桌面窗口；移动端无对应；移动首跑引导属产品级流程（太墟也放 settings 之外，`taixu.md:73`） |
| 4 | `settings.account.menu/signed-out/card/sign-in-dialog/sign-out-dialog/notices`（整个账户体系，7 节点） | DSH `ui-settings-account` | 4 家里只有 DSH 有账户（官方账号/余额/充值）；太墟/chat-demo/旧 myco 均无账户；myco 本地优先、凭据在 provider 层。反证：做账户页却没有账号后端 = 永远登录不上的页面。**若未来接官方额度**，另立面板并保留「外部浏览器打开」的降级路径 |
| 5 | `settings.models.onboarding-welcome`（版本欢迎公告） | DSH `WelcomeNotice.tsx` | 无版本公告机制；公告需要「已确认版本」设置项 + 发布通道 |
| 6 | `settings.models.onboarding-credential`（凭据 onboarding 槽位） | DSH `DeepSeekOnboardingDialog.tsx` | 槽位机制不做；能力由 `settings.shell.first-run-guard` 承担（直接进 models 页），避免两套配 Key 入口 |
| 7 | 全局「重置全部设置」/ 设置级全局危险确认 | DSH 未实现（Q-SET-03）；无参考 | 需定撤销窗口与二次确认强度，收益低于单字段「恢复默认」；避免误触把全部配置清空 |
| 8 | 设置页签的拖拽 / 拆分 / 浮动 / 关闭（dockkit 能力） | dsh-workspace §2.2 `TabPanel.tsx` | 设置子页页签**不可关闭**（反证：关掉后该子页再无入口，同 `RSB-05` 反证）；设置不拆分不浮动 |
| 9 | 只读 / 不可写部署横幅（readOnly / unavailable） | DSH `SettingsForm.tsx:55-59` | myco 无「只读部署」形态；引入会多一个全页状态与一整套禁用逻辑 |
| 10 | 旧 myco 工程/调试控件：`revision=…` 文案、常驻前台服务开关、「A7」内部代号 | `MA:1702,1750-1770,1752` | 内部代号进 UI；前台服务状态不查真实服务会误导；Q-LEG-12 已建议移出主路径 |
| 11 | 旧 myco「模型默认思考档」写死 4 档 chip | `MA:1975-1997` | 与 Host efforts 任意 Map 冲突（导入自定义档不可编辑）；effort 归 M5 面板 |
| 12 | 太墟外观页的聊天背景 / 身份卡 / 工具状态样式 / 终端外观 | `settings.appearance` 字段 2 | myco 首版无终端面板、无聊天背景/身份卡定制；这些是太墟产品特性，不随设置壳迁入 |
| 13 | 太墟「使用须知」对话框 | `SettingsScreen.kt:31-32,72-74` | 无法务文案与上架流程证据；若后续上架，作为独立条款页补 |
| 14 | 旧 myco 搜索的「过滤隐藏卡片」形态 | 旧 `MA:1599-1605,1663-1787`（D10） | 未命中卡片消失 + 无匹配无提示 = 实测缺陷；改为结果列表 + 定位跳转（§3.6） |

> 说明：**不删能力、只换形态**的项（模板对话框、独立密钥对话框、provider 3 按钮、bin 入口）不算「不做」，已在 §3.5 标为「合并」或「保留 → 移位」。

---

## 5. 新增节点（参考里都没有、myco 要新设计）

| 节点 ID | 为什么需要 | 依据（用户要求 / 平台规范 / 我方约束） |
|---|---|---|
| `settings.shell.dirty-guard` | 显式保存页存在后，「切子页 / 关闭设置层 / 系统返回键」都会静默丢弃整页草稿。四家参考都没处理此出口（DSH 只有桌面点关闭按钮时丢，且无提示；太墟 push 离开行为未取证）。移动端系统返回/侧滑返回是高频误触手势，静默丢数据 = 用户以为已保存 | 反证（`_merge-brief.md` 规则 2 要求每个新增写反证）：不做的坏处是「改了一半的密钥/URL 直接消失且无提示」；行为：dirty 时离开 → 二次确认「放弃未保存的修改？」（取消 = 留在原页）；dirty 判定 = 与打开时快照比较。**不引入「放弃」按钮**（C-15 结论），只拦出口。需用户拍板（Q-settings-09） |

---

## 6. 待确认（阻塞项）

> **F-01 取证缺口（不算阻塞，但必须记录）**：`ref/taixu.md` 的设置章节在本轮读取时才补齐（§2.6：首页/搜索/外观/智能体/模型 5 节点）；太墟的「危险确认、密钥字段形态、插件清单、agent preset、provider 删除」等节点级事实仍**未取证**——§2 对应行已标「未取证」，未推测。`dsh-Go` 无事实库，该列全列未取证。这两点不改变本文件已给结论；P3 前若太墟章节再补，交叉核对即可。

| ID | 问题 | 两解及各自后果 | 建议 |
|---|---|---|---|
| Q-settings-01 | **设置层数与返回语义**（与 M2 强耦合）：设置 = 一层（子页同层切换，返回关层）还是「分类首页 + 逐级 push」（返回退一级）？ | A 单层同层：实现简单、返回语义唯一（一次返回 = 退设置）；代价 = 子页不能用「返回上一级」逐层退，子页全部平铺在导航里。B 分类首页 + push：更符合「设置是树」的直觉、子页数可无限扩；代价 = 设置内出现第二级栈，与 myco 全局抽屉栈叠加后「一次返回退到哪层」不可预测；深链要保证两层同入（太墟为此专门写 `openSettingsPath`） | **A（单层）**，触发条件：子页 >7 时改 B。等 M2 结论（C-11/Q-BASE-02） |
| Q-settings-02 | **宽屏壳形态**：居中小模态（DSH 800×800）还是抽屉层/列？ | A 居中模态：桌面手感成熟、与 DSH 一致；代价 = 窄屏要另做全屏形态（两套）。B 抽屉层/列：与 myco 无限抽屉统一、一套形态；代价 = 宽屏大空间利用率低。注意 C-19 澄清：**G-01 只约束 overlay，不约束布局列**——若 M2 选列，本文件「高度取视口」等 overlay 规则不适用 | 窄屏**确定全屏覆盖页**（mobile-adapt E0）；宽屏**等 M2**，若 M2 选列则同步改本文件 §2 `shell.panel` 的 overlay 规则 |
| Q-settings-03 | **子页范围 / 运行时前提**：myco 首版是否有 plugins / subagent / agent-loop / web-search / agent-preset 运行时？决定 `settings.plugins.*`（8 节点）、`settings.subagent.*`（2）、`settings.agent-preset.*`（5）是否存在 | A 无这些运行时：删除 15 个节点，设置只剩「通用/模型/会话数据/关于/诊断」5 页；保存语义的「显式保存页」只剩 models 编辑页。B 有：需按 DSH/太墟补齐 15 个节点，settings 子页涨到 6–8 个（触发 Q-01 的 B 方案） | 需你按 myco 运行时现状一句话定；**本文件不建议自行删除**（`_merge-brief.md` 硬规则 5） |
| Q-settings-04 | **设置搜索做不做 + 形态** | A 不做：DSH/chat-demo 同款，实现省；代价 = 旧 myco 已有能力回退，且手机全屏 + 横滑 tab 下找「字号」要背子页。B 做（结果列表 + 定位跳转，§3.6）：找回能力 + 可容纳子页增长；代价 = 新增搜索态、注册表（每条目要有 anchorId）、无匹配/热门词等空态 | **B 做**（理由见 §2 `shell.search`；太墟 E0 证明移动端可做，旧 myco 已有该能力） |
| Q-settings-05 | **配置继承（组合层/用户层）是否作为 settings 写模型要求**？DSH 每字段有「已覆盖 / 恢复默认」+ 模型目录「恢复继承」；myco 现为 revision 乐观锁的**平铺**配置 | A 引入继承层：默认值更新用户自动跟随、恢复默认 = unset；代价 = 后端 settings 要支持分层（组合层 + 用户层），是较大改动。B 平铺快照：实现简单；代价 = 默认值更新不跟随（DSH `SET-37` 反证），「恢复默认」只能写死内置值 | **A**（语义正确），但需确认后端能否支持；若不能，本文件所有「恢复默认/恢复继承」行降级为「按内置默认重置」并在 P3 写明差异 |
| Q-settings-06 | **危险操作确认层级**：一次确认 vs 二次输入确认？ | A 一次确认（默认焦点在取消）：DSH/旧 myco/chat-demo 都是这个；代价 = 误删靠「焦点在取消」兜。B 二次输入（输入 provider id/会话名）：误删率最低；代价 = 每次删除都多一步，参考**无任何先例**（Q-LEG-02 是提案，E2） | **A**；「删除全部会话」用「二次确认 + 显示影响数量」而不是输入名 |
| Q-settings-07 | **「清空数据 / 删除全部会话」是否进首版**，与旧 myco 的回收站/软删如何并存？ | A 进：chat-demo（E0）+ 太墟本地备份都有；与回收站并存时「删除全部」要绕过硬删语义（回收站也清空？）。B 不进：首版只有软删 + 回收站，无「一键清空」；代价 = 用户想彻底清空只能逐条 | **A 进，但「删除全部」= 清空回收站 + 所有会话，二次确认 + 不可撤销**；逐条删除仍走软删 + 撤销 |
| Q-settings-08 | **路径选择控件**（若未来出现「选择目录」类设置）：参考**未取证**——DSH 没有路径选择器（baseURL 用文本），太墟未取证 | A 纯文本输入 + 校验：零实现成本；代价 = 手机上手打路径几乎不可用。B 系统目录选择器（Android SAF）：可用；代价 = 需要新权限/通道、错误处理，且参考无先例 | 首版**A（文本）**，且把「怎么选目录」标为未取证（G-00）；只有当实际出现路径类设置时再回 Q |
| Q-settings-09 | **未保存草稿的丢弃出口**：严格照 DSH（离开静默丢弃）还是加确认（§5 新增 `dirty-guard`）？ | A 严格照 DSH：与参考一致、实现少；代价 = 移动端返回手势误触丢整页草稿且无提示。B 加 dirty 确认：防丢数据；代价 = 多一个确认弹窗（每次带着改动离开都会弹） | **B**（移动端返回手势是高频误触，静默丢数据 = 用户以为已保存） |
| Q-settings-10 | **命中区**：设置内目标尺寸（导航 tab 44 / 行 40 / Select 36 / 关闭 28 / 开关 36×20 / 字号箭头 17×12）全部低于 44dp；全局 Q-03 未拍 | A 视觉不变 + 透明扩边：需隔离带（相邻各行扩边各侵入 12px → 最多重叠 24px，mobile-adapt 未做防护）。B 重排（行高抬到 44 / 字号改滑杆）：可达标；代价 = 视觉密度变化。C 双端不同 | **随 Q-03 全局结论**；本面板的增量要求：① 设置行间距 ≥8dp 隔离带；② 字号控件按 NS-09 改滑杆（从根上消除 17×12）；③ 关闭/导航 tab/开关必达 44 |
| Q-settings-11 | **设置界面自身是否跟随内容字号轴**（Q-BASE-05 已登记） | A 照 DSH：设置界面字号固定（14/12），只有内容缩放；代价 = 用户把字号调大后设置界面不变。B 跟随：可读性一致；代价 = 布局全变、G-02 的定高规则失效 | **A（照 DSH）**；等 Q-BASE-05 拍板后回填 |
| Q-settings-12 | **provider/model 配置导入导出是否保留**（太墟 E1 + 旧 myco E0；DSH 无，靠账户云同步） | A 保留：换机/分享刚需，且可当「删除 provider」的恢复路径；代价 = 需定格式、冲突处理（旧 myco 是整份替换 + revision 冲突）、密钥不导出。B 不保留：DSH 无、靠账户同步；代价 = myco 无账户，换机只能手抄 | **A 保留**（先预览再确认；不导密钥值） |

---

## 7. 自检

- [x] 每个节点在对照表里有一行（§1 的 71 个节点全部在 §2 有行；§4 明确不做的节点也在 §2 有行、状态 ❌）
- [x] 每条「建议」都引了 G 规则或给了反证（G-01/G-03/G-04/G-08/G-09/G-10 直接引用；其余均写「反证：…」）
- [x] 没有 E2 推测出现在 §2/§3（凡 ref 未覆盖：`dsh-Go` 全列、太墟缺失节点、路径选择控件、字体族、onboarding 细节等，均标「未取证」并进 §6 或 §3.2 的「未取证」格）
- [x] 明确不做项都写了理由（§4 共 14 组，覆盖 17 个节点；且「换形态不删能力」的项已单独在 §3.5 标合并/移位）
- [x] 阻塞项已编号（Q-settings-01…12；另有取证缺口 F-01 单列）
- [x] C-15 已处理（§3.1 给出逐页语义表 + 三条可验收判据）
- [x] 容器/层叠/返回栈相关冲突已写进 §6 并注明「待 M2 base.layout 结论」（`shell.panel`/`shell.nav`/`shell.back`/`shell.home` 的 ⏸ 行 + Q-settings-01/02）
- [x] 冲突只收互斥项（10 条：C-15、NS-01…NS-09；可并存的全部进 §3.6，模板/密钥对话框/3 按钮/bin 入口等「同能力不同形态」进 §3.5 合并而非冲突）
- [x] 未修改 `ref/**`、`00-global-rules.md`、`_conflicts.md`、`_merge-*.md`、`p2/_plan.md`、其它 `p2/*.md`；未改 app；未 git commit
- [ ] **未拍板行不得进入 P3**：本文件 10 条冲突行与 12 条待确认项在用户拍板前不得被 P3 采用

**写作用域声明**：本任务只写 `docs/spec/p2/settings.md`。建议 Lead 把 NS-01…NS-09 登记进 `_conflicts.md`（本文件无权改），并复核 Q-settings-03（子页范围）——它决定本文件 15 个节点的去留。

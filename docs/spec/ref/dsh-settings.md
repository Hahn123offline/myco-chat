# DSH 设置 / 模型 / 附件 / 触发命令 —— P1 事实库（只读审计）

> 审计对象：`E:\deepseek-harness\packages\client` 下 17 个包（见 §1），**只看产品 UI（`src/`）**。
> 依据级别：`E0` 源码 `文件:行` / `E1` 文档声称 / `E2` 推测（只进 §4）。
> 代码里没有的行为一律写「未见实现」。
> 机器可读事实库：[dsh-settings.facts.csv](dsh-settings.facts.csv)（472 条：E0 243、excluded 229）。
> 对账结果：`python tools/ui-audit/ui_audit.py check ... --fail-on-uncovered` → **uncovered=0**（`_ws/out/coverage-dsh-settings.md`）。
> 盘点分母：`_ws/out/findings-dsh-settings.csv`（本路由前缀 472 条）。
>
> 约定：`sw`=`E:\deepseek-harness\packages\client`；`dsh`=`E:\deepseek-harness`；`prim`=`packages/client/ui-primitives`。
> 同一节点覆盖同型控件时，第 1 字段写明覆盖了哪几行（facts.csv 的每条 E0 行都指向该节点）。

---

## 0. 一页结论

1. **设置是「壳 + 槽位」结构，壳本身零文案**。`ui-settings-general` 只渲染壳（触发行、800×800 居中模态、188px 左导航、关闭），全部标题/页签/页面内容都由注册方通过 `settings.*` 槽位注入（`ui-settings/src/client/contract/slots.ts:1-10`、`ui-settings-general/src/client/index.ts:198-238`）。**加一个设置页 = 注册一个 `settings.section`，不改壳**（`ui-settings/src/client/contract/slots.ts:46-57`）。
2. **设置面板没有「搜索设置」**：壳里没有搜索框、没有跨页过滤（`SettingsRoot.tsx` 全文无 search；`ui-settings-general` 也无）。唯一的搜索在「插件清单」页内部（`ui-settings-plugin-inventory/src/client/PluginInventorySettingsTab.tsx:475-485`）。**未见实现**。
3. **保存方式有两种，且同屏并存**：插件配置页（Shell/agent-loop/web-search/subagent）用共享 `SettingsForm`，**显式「保存」才写、离开页面即丢弃草稿、没有「放弃」按钮**（`prim/settings-form/SettingsForm.tsx:6-10,50-74`）；General 的开关行、Models 的编辑卡、账户页则是**即时写**（`DeveloperToolsRow.tsx:30-35`、`ProviderEditor.tsx:309-322`、`AccountSection.tsx:149-150`）。**没有统一的「应用/撤销」**。
4. **没有全局「重置设置」、没有设置级危险操作确认**；唯一的危险确认是模型 provider 删除（`ModelsSection.tsx:684-717`）与账户退出（`SignOutDialog.tsx:24-39`）。**未见实现**。
5. **模型切换有两个入口、共享一份 per-session 状态**：输入区的命名座位（`conversation.input.model`，胶囊「模型名 · 推理等级」）与 `/model` 命令弹窗；两者都读写同一个 `ModelDirectory`（`ui-model-selection/src/client/index.ts:1-12,168-190`）。**这正是 myco 简易 sheet 最可能缺的「同一事实源 + 双入口」结构**。
6. **模型面板只有两个维度：模型 + 推理等级（reasoning effort）**。根菜单是两行（模型 / 推理等级），各自钻入列表；**没有 preset、没有上下文长度、没有温度/采样参数入口**；preset 在 hero chip 与设置页 `agent-presets`，上下文长度只在 `settings.models` 的逐模型编辑里（`ModelSelect.tsx:490-505`、`ProviderEditor.tsx:14-19`、`ModelRow.tsx:86-111`）。推理等级的词表来自 Host catalog（`ModelSelect.tsx:19-21,129-140`）。
7. **模型面板的键盘/反馈完整度高**：↑↓ 移焦、Enter/Tab 选中、Esc/Shift+Tab 先退钻取层再关闭；搜索只在模型数 >4 时出现（↑↓ 在搜索框内循环高亮、Enter/Tab 接受）；选择提交期间 trigger 与目标行显示 spinner，失败走 Toast；目录加载失败是菜单内错误条 + Retry（`ModelSelect.tsx:106,297-360,370-384,472-474,541-552,593-595`）。
8. **附件「导入媒体」没有内建相册/相机选择器**：来源只有三路——`/file` 命令（图标回形针）打开原生文件对话框（隐藏 `<input type=file multiple>`）、**文档级拖放**（全屏 DropOverlay）、**粘贴**（剪贴板文件）。三者汇入同一个 `intakeFiles`（`ui-conversation/src/client/apply.ts:271-280`、`InputBar.tsx:212-249,455-462`、`ui-attachment/src/client/drop-events.ts`、`ui-conversation/src/client/input/editor/keymap.ts:159-185`）。
9. **附件的限制只对图片生效且来自 Host 投影**：图片数量/单文件大小/单消息总量/媒体类型由 `imageLimits` 投影决定，超限**整批拒绝并 toast**；通用文件**没有客户端数量/大小限制，选中即上传**（`InputBar.tsx:206-239`）。DropOverlay 会显示张数与大小上限提示（`InputBar.tsx:414-417`、`DropOverlay.tsx:24-40`）。
10. **附件的上传/失败/移除有完整状态机，但没有「取消上传」按钮**：文件卡三态 `uploading/ready/error` + 2px 进度条 + 失败重试 + 18px 移除按钮（`FileCard.tsx:19-81`、`FileCard.module.css:132-159`）；后台上传是 Web Worker + XHR/fetch，服务层支持 `AbortSignal`（`file-upload/src/client/runtime.ts:79-152,240-298`），**但 ComposerAttachments 未暴露取消动作**（`ComposerAttachments.tsx:56-71` 只给移除/重试）。
11. **`/` 与 `@` 是同一个「触发源注册表」的两个 source**：菜单是 combobox（焦点始终留在输入框，`aria-activedescendant` 暴露高亮），行用 mousedown 选择、支持钻取（Tab/箭头）、面包屑、分组骨架（`ui-input-trigger/src/client/MenuView.tsx:47-222`、`controller.ts:265-313`、`core/detect.ts:20-75`）。`@` 走 file-reference 共享语法（`core/detect.ts:50-60`）。
12. **`+` 按钮打开的就是命令菜单**（与 `/` 同一候选源）：按钮 `aria-haspopup=listbox`，点击走 `toggleCommandMenu`（`ui-conversation/src/client/skeleton/InputBar.tsx:441-453,294-302`）；命令菜单是另一个 popupSelect 壳：**持有焦点**、本地搜索、Enter/Tab 接受、↑↓ 循环、Esc/Shift+Tab 关闭、外点关闭（`ui-commands/src/client/PopupSelectView.tsx:1-11,97-132`）。
13. **设置各子页的条目类型齐全**：开关（`Switch`）、文本/数字输入（`SettingsValueField`，数字只给 keypad 提示、是否合法由字段 spec 决定）、密钥（`SettingsSecretField`，write-only，只显示 configured 状态）、选择（原生 `select`）、路径/端点（文本输入 + 占位继承）、每字段「已覆盖」Tag + 恢复默认、可展开帮助（`aria-expanded`）、原生 `<details>` 折叠「自定义设置」（`prim/settings-form/fields.tsx:49-151`、`ProviderEditor.tsx:380-497`、`AgentLoopCard.tsx:26-38`、`WebSearchCard.tsx:31-69`）。
14. **设置条目校验反馈逐字段就地显示**：非法时 `aria-invalid` + 用「非法提示」替换 hint（`fields.tsx:62-63,95-109`）；保存被 Host 拒绝时表单底部显示 `saveFailed`（`SettingsForm.tsx:62`）；模型卡片的行级校验会把「第 N 行 + 原因键」显示在卡底（`ProviderEditor.tsx:517-523`）。
15. **用户在 P1 点名的两处「比 DSH 差」在源码层面可量化的差距**：模型切换缺的是「两级菜单 + 双入口共享状态 + Host 驱动的 effort 维度 + 键盘全链路 + pending/失败反馈」；附件导入缺的是「三路入口（对话框/拖放/粘贴）+ 图片限制提示 + 文件卡三态/进度/重试 + 文档级拖放遮罩」。二者在 DSH 都不是一个 sheet 能覆盖的（见 §5、§6）。

---

## 1. 包 → 面板归位表

| 包（`sw/`） | 是什么 | 注册到哪（槽位/服务） | 归入面板 | 本库节点前缀 |
|---|---|---|---|---|
| `ui-settings` | 设置**领域基座**：`ctx.configForms`（表单模型）、`ctx.settingsSchema`、settings describe 镜像；**无 UI 组件** | 服务 `configForms`/`settingsSchema`；声明全部 `settings.*` 槽位类型（`client/contract/slots.ts:14-93`） | `settings`（基座） | `settings.form.*` 的证据源 |
| `ui-settings-shell` | Shell 执行器的设置页（命令超时 / 单流输出上限） | `plugins.item`（`id:'shell', order:40`，`client/index.ts:50-52`） | `settings.plugins` | `settings.plugins.tab` 的下挂页 |
| `ui-settings-general` | **设置壳本体**（触发按钮/模态/导航/关闭）+ General 页 + 版本行 + 开发者工具行 + 连接/更新指示；**壳零文案** | 占据 `sidebar.settings`；声明并渲染 `settings.trigger/header/action/close/section/onboarding`；注册 `settings.general.item`（`client/index.ts:175-238`） | `settings`、`settings.general` | `settings.shell.*`、`settings.general.*` |
| `ui-settings-models` | 模型与 provider 管理页 + 产品 onboarding（welcome 公告、DeepSeek 官方凭据引导） | `settings.section`（`id:'models', order:10`）+ `settings.onboarding`（`welcome-notice` order -100、`deepseek-official` order 0）（`client/index.ts:138-161`） | `settings.models` | `settings.models.*` |
| `ui-settings-agent-loop` | agent loop 页（每步并行工具调用上限） | `plugins.item`（`id:'agent-loop', order:30`，`client/index.ts:47-49`） | `settings.agent-loop` | `settings.form.*` |
| `ui-settings-web-search` | 搜索 provider 页（key/baseURL/maxUses） | `plugins.item`（`id:'web-search', order:20`，`client/index.ts:57-59`） | `settings.web-search` | `settings.form.*` |
| `ui-settings-plugins` | **Plugins 设置区**：页签壳（tablist roving tabindex），页面由各 feature 贡献 | `settings.section`（`id:'plugins', order:15`）+ 子槽 `settings.plugins.tab`（`client/index.ts:76-84`） | `settings.plugins` | `settings.plugins.tab` |
| `ui-settings-plugin-inventory` | 只读插件清单（按 agent preset 分组 + 全局面；搜索/展开/状态标签） | `settings.plugins.tab`（`id:'all', order:10`，`client/index.ts:56-63`） | `settings.plugins` | `settings.plugin-inventory.*` |
| `ui-settings-subagent` | 子代理设置（委派上限 + 可被选择的模型授权） | `plugins.item`（`id:'subagent', order:10`，`client/index.ts:85-92`） | `settings.subagent` | `settings.subagent.*` |
| `ui-settings-session-log` | API 日志上传开关（General 行）+ 保存结果 Toast | `settings.general.item`（`id:'session-log-deepseek', order:90`）+ `shell.overlay`（`client/index.ts:36-40`） | `settings.general` | `settings.general.*` |
| `ui-settings-account` | 账户区（登录/余额/充值/退出）+ 侧栏账户启动器 + 桌面 onboarding + 配额通知 + 内嵌 Platform 页 | `settings.section`（`account`）、`settings.launcher`、`sidebar.toggle.badge`、`settings.onboarding`、`shell.quota-notice`、`shell.overlay`（`client/index.ts`） | `settings.account` | `settings.account.*` |
| `ui-model-selection` | 模型选择：输入区命名座位 + `/model` 弹窗贡献，共享 per-session 目录 | `conversation.input.model` + `commandUi.register({name:'model'})`（`client/index.ts:124-190`） | `model` | `model.*` |
| `ui-agent-preset` | 预设：新会话 hero chip、会话头只读标签、设置页 roster、Creator 入口、Add plugin 菜单项 | `conversation.hero.agentPreset`、`conversation.session.header.actions`、`settings.section`（`agent-presets`, order 20）、`plugins.add.actions`（`client/index.ts:208-274`） | `model`（合并面板）/`settings.agent-presets` | `agent-preset.*`、`settings.agent-preset.*` |
| `ui-input-trigger` | `/`、`@` 触发检测、候选菜单、pick/claim 管线（combobox） | 服务 `ctx.inputTriggers`；`conversation.input.overlay`（`id:'slash-menu', order:0`，`client/index.ts:59-86`） | `composer` | `composer.slash.*` |
| `ui-commands` | 命令 UI 运行时：`/` 命令源、客户端贡献注册、popupSelect 壳 | 服务 `ctx.commandUi`；`conversation.input.overlay`（`id:'command-popup', order:1`，`client/index.ts:58-75`） | `composer` | `composer.command.*` |
| `ui-attachment` | 附件呈现：草稿轨、文件卡、图片缩略/灯箱、拖放遮罩、历史消息图片 | `conversation.input.attachments`、`conversation.message.images`、`conversation.trajectory.images`、`tool.call.images`（`client/index.ts:15-33`） | `composer`/`chat` | `composer.attach.*`、`chat.msg.image` |
| `file-upload` | **无 UI**：浏览器后台上传服务 `ctx.fileUpload`（Worker+XHR/fetch、进度、AbortSignal、Remote 兜底） | 服务 `fileUpload`（`client/index.ts:24-26`） | `base`（能力层） | 供 §6 状态机取证 |

> 归位之外的包（`ui-settings-account` 的桌面专属 Chrome、`file-upload` HTTP 路由）不属于首版 myco 面板范围。

---

## 2. 节点事实表（13 字段）

> 说明：字段编号严格对应 `docs/spec/schema-node.md §1`。
> 覆盖约定：一个节点写「覆盖」清单时，表示该节点是这些同型控件的唯一事实源，facts.csv 中对应行都指向它。

### 2.1 设置壳（`settings.shell.*`）

#### `settings.shell.trigger` —— 侧栏底部设置触发按钮（含快捷键）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.shell.trigger`。所属：设置壳 → 侧栏底部触发行。父节点：侧栏 `sidebar.settings` 占位。出现条件：始终出现（壳的常驻入口）；侧栏宽/窄两种形态各渲染一次。覆盖：`SettingsRoot.tsx:222,228,229`（按钮/aria-expanded/click）；快捷键注册见 `index.ts:179-196`。 |
| 2 | 网格与定位 | 触发行 `flex: none; display:flex; align-items:center; gap:8px; width:calc(100%+4px); margin:4px -2px`，窄栏（rail）时 `width:36px; margin:8px 0 10px`（`SettingsRoot.module.css:1-14`）。按钮本体：宽列 `height:42px; padding:0 10px 0 8px; border-radius: var(--dsw-radius-md); gap:8px`；rail 态 `36×36、padding:0、justify-content:center`（`:16-52`）。行内右侧依次：连接指示器、桌面更新指示器（`SettingsRoot.tsx:234-244`）。 |
| 3 | 命中区 | 宽列整行按钮 100%×42px；rail 态 36×36px（小于 44dp，见 Q-SET-15）。与右侧两个指示器之间由触发行 8px gap 分开，各自是独立命中区。 |
| 4 | 手势 | 单击 = 打开设置面板（`actions.open()`）；再次触发时按钮仍在侧栏（面板遮罩盖住侧栏，实际由遮罩/关闭键退出）。键盘：全局快捷键 `settings.open`，桌面 `Ctrl/Cmd+,`、Web `Ctrl/Cmd+Alt+,`，`regions:['page','editable','terminal']`、`modals:['settings']`；面板已开时同一快捷键调用 `closeTopModal` 关闭（`index.ts:179-196`）。长按/双击无自定义（未见实现）。 |
| 5 | 行为 | 打开：`shell-store.open=true`，面板以 body portal 模态出现，导航选中上次 `activeId`（或首行）。关闭：`activeId` 清空（`shell-store.ts:17-25`）。幂等；可撤销（再开/再关）。 |
| 6 | 视觉状态 | default 透明底、`color: label-primary`；hover `background: interactive-bg-hover`（`SettingsRoot.module.css:38-40`）；focus-visible 使用浏览器/共享焦点环（未见自定义 ring；`aria-keyshortcuts` 暴露快捷键）；disabled：未见（始终可点）。展开态无特殊标记（面板本身是模态）。 |
| 7 | 布局占位与展开形态 | 参与侧栏布局流，固定 42px（rail 36px），**不随面板开合改变高度**。面板本身是 overlay（`position:fixed; inset:0; z-index:1000`，`SettingsRoot.module.css:61-68`），不占布局 —— 形态 C（sheet-overlay），符合 G-01。 |
| 8 | 滚动语义 | 不滚动。侧栏自身滚动时触发行随之滚走（它位于侧栏尾部内容流内）。 |
| 9 | 动效 | 无开合动画；hover 仅背景色瞬时变化。面板出现/消失为直接挂载/卸载（`{open && <SettingsPanel/>}`，`SettingsRoot.tsx:246-254`），无过渡。 |
| 10 | 边界态 | 图标+文案由 `settings.trigger` 槽位提供；缺贡献时退化为「无无障碍名的纯图标按钮」（broken-composition，`contract/slots.ts:19-27`）。rail 态只显示图标（`chrome.tsx:22-28`）。多语言/大字号：标签单行 ellipsis（`chrome.module.css:1-7`、`SettingsRoot.module.css:54-57`）。 |
| 11 | 无障碍 | `<button aria-label=t('trigger')>`、`aria-haspopup="dialog"`、`aria-expanded={open}`、`aria-keyshortcuts`；焦点顺序：触发行 → 连接指示器 → 更新指示器（`SettingsRoot.tsx:222-232`）。**无可见文字时仍有 aria-label**。 |
| 12 | 依据 | `E0` `sw/ui-settings-general/src/client/SettingsRoot.tsx:216-245`；`E0` `SettingsRoot.module.css:1-57`；`E0` `sw/ui-settings-general/src/client/index.ts:179-196,231-238`；`E0` `slots.ts:19-27`；`E0` `chrome.tsx:22-28`。 |
| 13 | 反证 + 验收 | **反证**：若触发按钮放进面板内（而不是侧栏常驻），面板关闭后用户没有任何再次入口；若 rail 态不保留 36×36 命中区，折叠侧栏后设置入口会消失。**验收**：`SET-01` 宽/窄侧栏两种形态下按钮可点且 aria-label 非空（截图+DOM 断言）；`SET-02` 快捷键在页面/输入框/终端三区域均能开关设置（真机+单测）。 |

#### `settings.shell.panel` —— 设置模态面板（遮罩 / 关闭）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.shell.panel`。所属：设置壳 → body portal 模态。出现条件：`open=true`；全局同一时刻最多 1 个。覆盖：遮罩点击 `SettingsRoot.tsx:69`；关闭按钮 `:93`；模态层 `:61,70`。 |
| 2 | 网格与定位 | 全屏层 `position:fixed; inset:0; z-index:1000; display:flex; align-items:center; justify-content:center`（`SettingsRoot.module.css:61-68`）。遮罩 `position:absolute; inset:var(--dsh-frame-chrome-top,0) 0 0`（`:71-76`）。面板：`width:800px; height:min(800px, 100vh - 2*max(24px, var(--dsh-frame-overlay-top)))`、`max-width:calc(100vw - 48px)`、圆角 `--dsw-radius-panel`、`overflow:hidden`（`:85-103`）。左导航 188px（`:111-119`），右内容列：header 54px + options 滚动区（`:188-197,227-233`）。 |
| 3 | 命中区 | 遮罩全覆盖（含顶部 frame 之下）；面板本身吞掉点击（不在遮罩上）。关闭按钮 28×28px（`SettingsRoot.tsx:93-96`、`.close` 209-225）。面板与遮罩间无缝隙（面板在遮罩上层 `z-index:1`）。 |
| 4 | 手势 | 单击遮罩 = 关闭（`onClick={onClose}`）；单击关闭按钮 = 关闭；`useModalLayer(panel,true,onClose)` 提供文档级 Escape 与焦点陷阱（`SettingsRoot.tsx:60-61`）；无长按/双击/滑动关闭（未见实现）。 |
| 5 | 行为 | 关闭时 `close()`：`open=false; activeId=undefined`（`shell-store.ts:21`）。面板内容随 `open` 卸载，下一次打开从头渲染。幂等。 |
| 6 | 视觉状态 | 遮罩：`--dsw-alias-bg-mask-1` + `backdrop-filter: var(--dsw-mask-blur)`（`:71-76`）。面板：`--dsw-alias-bg-layer-2`、`--dsw-elevation-prominent`、滚动条 l2 token（`:93-102`）。关闭按钮 hover 背景 hover 色；focus-visible 走共享环。 |
| 7 | 布局占位与展开形态 | 形态 C：overlay 完全不占布局（G-01）。面板内两列：nav 独立滚动（`navList overflow-y:auto`，`:131-136`）与 options 独立滚动（`:227-233`）；**面板高度取 viewport 而非内容，防止每次换页面板尺寸跳动**（注释 `:78-84`）。 |
| 8 | 滚动语义 | 两个内滚容器：左导航 `.navList`、右内容 `.options`；各自到边即停（浏览器 `overflow-y:auto` 行为，未见自定义串联/边缘手势传递）。外层页面不滚（body 锁定由模态层负责，具体实现不在本包）。 |
| 9 | 动效 | 无进入/退出动画（直接挂载/卸载）。 |
| 10 | 边界态 | `rows.length===0` 时 active 为 undefined，内容列为空（`SettingsRoot.tsx:57,99`）；访问中的 section 注销时回落到首行（`:57`）；onboarding 步骤出现时若面板开着会被强制关闭（`:150-158`）。小屏：`max-width:calc(100vw-48px)` + 面板高度随 vh。 |
| 11 | 无障碍 | `role="dialog" aria-modal="true" aria-labelledby={titleId}`（标题由 `settings.header` 槽位内容提供）；面板 `tabIndex={-1}` + `data-shortcut-modal="settings"`；关闭按钮内是视觉隐藏的本地化文案（`.hiddenLabel`）；遮罩 `aria-hidden="true"`。 |
| 12 | 依据 | `E0` `SettingsRoot.tsx:54-105,246-254`；`E0` `SettingsRoot.module.css:61-103,111-136,188-243`；`E0` `shell-store.ts:16-26`；`E0` `sw/ui-settings/src/client/contract/slots.ts:14-93`。 |
| 13 | 反证 + 验收 | **反证**：若面板高度随内容变化，每次切 section 面板会跳动、遮罩边缘位移，用户失去空间感；若只允许关闭按钮关闭，Esc/点击遮罩两个系统级习惯失效，用户会觉得「弹窗关不掉」。**验收**：`SET-03` 切换不同长度 section 时面板外框尺寸不变（截图比对）；`SET-04` Esc、遮罩、关闭按钮三条路径都能关闭且焦点回到触发按钮（真机）。 |

#### `settings.shell.nav-row` —— 左导航页签行

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.shell.nav-row`。所属：设置壳 → 左导航列表。出现条件：`settings.section` 注册表非空；每个注册项 1 行，按 `order` 升序（`SettingsRoot.tsx:74-88`；排序来源 `index.ts:129-154`）。 |
| 2 | 网格与定位 | `.navList` 纵向 `gap:4px; overflow-y:auto`（`SettingsRoot.module.css:131-136`）；行 `.navCell` `display:flex; align-items:center; gap:8px; height:40px; padding:9px 16px 9px 12px; border-radius: md`（`:140-157`）；图标 16px（`navIcon`，`SettingsRoot.tsx:32-39`），标签 `14/22` 单行 ellipsis（`:171-177`）。 |
| 3 | 命中区 | 整行 164×40px（导航列 188 - 左右 padding）；图标与标签同属一个按钮命中区；行间 4px。 |
| 4 | 手势 | 单击 = `actions.select(id)` 切换内容列；无长按/双击；键盘：Tab 进入导航行（原生 button），无 roving tabindex（每行都可 Tab，`SettingsRoot.tsx:76-87`）。 |
| 5 | 行为 | 只改 `activeId`，面板不关、不导航离开（当前页内切换）。幂等。无历史栈（**没有「返回上一页」概念**，与移动端抽屉不同，见 Q-SET-05）。 |
| 6 | 视觉状态 | default 透明；hover `--dsw-specific-sidebar-nav-item-hover`；active/selected `--dsw-specific-sidebar-nav-item-active` + `aria-current="true"`（`SettingsRoot.module.css:159-165`、`SettingsRoot.tsx:80`）；focus-visible 走共享环（未见自定义）。 |
| 7 | 布局占位与展开形态 | 参与导航列布局流，固定 40px；不展开。导航列超出时自身滚动，页签行不被压缩。 |
| 8 | 滚动语义 | 归 `.navList`（内滚，`overflow-y:auto`）；到边即停，不串联到面板或页面（未见自定义）。 |
| 9 | 动效 | 仅背景色切换，无位移/高度动画。 |
| 10 | 边界态 | 图标按 id 白名单：`account/models/agent-presets/plugins/archived-sessions`，未知 id 回落齿轮图标（`SettingsRoot.tsx:32-39`）；标签超长 ellipsis；注册项被移除时 active 回落首行（`:57`）；多语言由注册方重注册触发重渲染（`index.ts:126-129` 注释）。 |
| 11 | 无障碍 | `<button type="button">` + `aria-current`（选中）+ `data-modal-autofocus`（选中行可被模态自动聚焦）；无 `role=tablist`（它是导航列不是页签，语义上可接受但见 Q-SET-05）。 |
| 12 | 依据 | `E0` `SettingsRoot.tsx:57,71-89`；`E0` `SettingsRoot.module.css:131-177`；`E0` `index.ts:126-154`；`E0` `ui-settings/src/client/contract/slots.ts:46-57`。 |
| 13 | 反证 + 验收 | **反证**：若导航行不是固定 40px 而随文案撑高，多语言下（德语/俄语）导航会抖动且内容列起始位置漂移；若选中态没有 `aria-current`，读屏用户无法知道自己在哪一页。**验收**：`SET-05` 40px 行高在 zh/en 下不变（截图）；`SET-06` 读屏报出「当前页」（TalkBack/VoiceOver 真机）。 |

#### `settings.shell.close` —— 关闭按钮

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.shell.close`。所属：设置壳 → 内容列 header 右侧。始终出现（面板打开时）。覆盖：`SettingsRoot.tsx:93-96`。 |
| 2 | 网格与定位 | `.header` `height:54px; padding:20px 14px 8px 10px; justify-content:space-between`（`SettingsRoot.module.css:188-197`）；`.close` 28×28px、圆角 sm、`padding:0`（`:209-221`）；位于 header 右端，左侧是 `settings.action` 动作区（`:199-206`）。 |
| 3 | 命中区 | 28×28px（<44dp，移动端需扩边，同 Q-SET-15）；与左侧动作区间 8px gap。 |
| 4 | 手势 | 单击 = 关闭面板；键盘 Enter/Space（原生 button）；Escape 由模态层处理（等效）。 |
| 5 | 行为 | 调 `onClose` → `shell-store.close()`；不保存任何设置草稿（插件配置页的未保存草稿随卸载丢弃，`SettingsForm.tsx:52-54`）。幂等。 |
| 6 | 视觉状态 | 透明底；hover `interactive-bg-hover`（`:223-225`）；focus-visible 走共享焦点环；无 disabled 态。 |
| 7 | 布局占位与展开形态 | header 固定 54px，按钮不参与 options 滚动区，**滚动时始终可见**（header 是 `flex:none`）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 关闭按钮的无障碍名来自 `settings.close` 槽位；缺贡献时按钮无名字（broken-composition，`contract/slots.ts:40-45`）。图标 14px。 |
| 11 | 无障碍 | `<button type="button">` + 视觉隐藏的本地化 label（`.hiddenLabel`，`SettingsRoot.module.css:235-243`）；`aria-hidden` 修饰图标。 |
| 12 | 依据 | `E0` `SettingsRoot.tsx:90-97`；`E0` `SettingsRoot.module.css:188-243`；`E0` `ui-settings/src/client/contract/slots.ts:40-45`。 |
| 13 | 反证 + 验收 | **反证**：若关闭按钮放进滚动区（options 内），长设置页滚到底后找不到关闭；若按钮只有图标没有名字，读屏只会报「按钮」。**验收**：`SET-07` 任意 section 滚到底部关闭按钮仍可见（录屏）；`SET-08` 关闭按钮 aria-label 非空（DOM 断言）。 |

#### `settings.shell.action-open-document` —— 标题栏动作：打开本地设置文档

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.shell.action-open-document`。所属：设置壳 → header 动作区（`settings.action` 槽位列表）。出现条件：**仅 loopback Host 且 `hasDocument=true` 时**出现（`SettingsDocumentAction.tsx:29-36`；`index.ts:102-111,220-228`）。1 个。 |
| 2 | 网格与定位 | 动作区 `.actions` 右对齐（`margin-left:auto`）、`gap:8px`（`SettingsRoot.module.css:199-206`）；动作容器 `.action` 横向 `gap:8px`、`min-width:0`（`SettingsDocumentAction.module.css:1-6`）；Button 为共享 outline sm 尺寸。错误文案 max-width 180 + ellipsis（`:8-16`）。 |
| 3 | 命中区 | 共享 Button（sm 尺寸，约 28px 高）；与关闭按钮相邻。 |
| 4 | 手势 | 单击 = `controller.open()`（Host `settings.openSettingsDocument()`）；无键盘自定义（原生 button）。 |
| 5 | 行为 | 打开原生设置文档；`opening` 期间按钮 disabled，并发手势折叠（`settings-document-store.ts:57-73`）。失败显示 `role=alert` 错误文案，按钮保持可用可重试。幂等（Host 侧打开同一文档）。 |
| 6 | 视觉状态 | outline 变体 default/hover/focus-visible/disabled 来自共享 Button；`opening=true` → disabled；错误态旁边出现红色 `role=alert` 文本。 |
| 7 | 布局占位与展开形态 | header 固定高 54px 内，不参与滚动；错误文本内联占位（可能挤压按钮）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 非 loopback（远端浏览器）→ 不渲染（`index.ts:102-104`）；`status!=='ready'` → null（加载中/不可用不占位）；错误只显示本地化文案，诊断文本进 store。 |
| 11 | 无障碍 | Button 原生语义；错误 `role="alert"`；无额外 aria-label（用可见文案）。 |
| 12 | 依据 | `E0` `SettingsDocumentAction.tsx:29-50`；`E0` `SettingsDocumentAction.module.css:1-16`；`E0` `settings-document-store.ts:43-99`；`E0` `SettingsRoot.tsx:90-97`；`E0` `ui-settings-general/src/client/index.ts:102-111,220-228`。 |
| 13 | 反证 + 验收 | **反证**：若文档不可用时仍渲染按钮，点击后只能报错，用户以为功能坏了；若不显示打开失败错误，用户不知道是「没有文档」还是「打不开」。**验收**：`SET-09` 远端浏览器不出现该动作（截图）；`SET-10` 打开失败时错误文案可见且可重试（Mock + 真机）。 |

#### `settings.shell.update-indicator` —— 桌面更新状态指示器

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.shell.update-indicator`。所属：设置壳 → 侧栏触发行右侧。出现条件：桌面端（`dshDesktop` 协议 v1）+ 宽侧栏 + 有更新状态/失败；其他情况不渲染（`DesktopUpdateIndicator.tsx:56-70`）。覆盖：`:71,72`。 |
| 2 | 网格与定位 | `.indicator` `inline-grid; grid-template-columns:14px max-content; column-gap:4px; height:28px; padding:0 8px; border-radius:sm; font-size:12/18`（`DesktopUpdateIndicator.module.css:1-21`）；位于触发按钮右侧，与连接指示器并列（`SettingsRoot.tsx:243-244`）。连接断开/连接中时被隐藏（`:243`）。 |
| 3 | 命中区 | 28px 高 × 内容宽（图标 14 + 文案）；`aria-disabled` 时仍占位。 |
| 4 | 手势 | 单击 = `onOpen()`（打开当前更新动作）；`busy`（opening/checking/downloading/verifying/installing）时 `aria-disabled=true` 且点击不动作（`:71-72`）。 |
| 5 | 行为 | 打开 shell 拥有的更新流程（Electron 侧）；状态文案随 phase 变化：checking/available/downloading(percent)/verifying/installing/ready/error(retry)（`:14-49`）。失败分 9 类错误文案（`:29-42`）。幂等（busy 时忽略）。 |
| 6 | 视觉状态 | 品牌色描边+8% 品牌底；error → 红点；busy → 14px spinner；`aria-disabled` 时仍显示「重试」但有 disabled 语义（css `:46`）。 |
| 7 | 布局占位与展开形态 | 参与触发行布局，固定 28px；Tooltip 浮层（不占位）。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | spinner 旋转；无其它。 |
| 10 | 边界态 | 浏览器（无 `dshDesktop`）→ 组件不渲染；窄侧栏（rail）→ 不渲染；连接异常时让位给连接指示器（`SettingsRoot.tsx:243`）。 |
| 11 | 无障碍 | `<button aria-label={label} aria-disabled={busy}>` + Tooltip 详情；图标 `aria-hidden`。 |
| 12 | 依据 | `E0` `DesktopUpdateIndicator.tsx:14-80`；`E0` `DesktopUpdateIndicator.module.css:1-30`；`E0` `SettingsRoot.tsx:234-244`；`E0` `desktop-update-source.ts`（状态源）。 |
| 13 | 反证 + 验收 | **反证**：若 busy 时按钮仍可点，会重复触发下载/安装并互相打断；若与连接指示器同时显示，两个状态互相矛盾、用户不知道哪个优先。**验收**：`SET-11` 更新中重复点击只触发一次（真机）；`SET-12` 连接断开时只显示连接指示器（截图）。 |

### 2.2 General 行与共享表单控件（`settings.general.*` / `settings.form.*`）

#### `settings.general.row-switch` —— General 的开关行（开发者工具 / 日志上传）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.general.row-switch`。所属：设置 → General 页（`settings.general.item` 槽位列表）。出现条件：每个贡献行 1 个；本库覆盖 2 行：`developer-tools`（order 15）与 `session-log-deepseek`（order 90）。覆盖：`DeveloperToolsRow.tsx:30-35`、`UploadRow.tsx:34-36`（均无扫描命中行，靠组件源码取证）。 |
| 2 | 网格与定位 | General 页是 item 纵向栈（`GeneralSection.tsx:14-19`）；行 `.row` 横向 `space-between`（`DeveloperToolsRow.module.css`、`UploadRow.module.css` 各 344B，标题+描述在左，Switch 在右）。 |
| 3 | 命中区 | Switch 本体（共享 Switch 尺寸，通常 36×20）；整行不可点（只有右侧开关可点）。 |
| 4 | 手势 | 单击 Switch = 切换并**立即写**；无长按/双击。键盘：Switch 原生 role=switch + Space。 |
| 5 | 行为 | 开发者工具：`onChange(next)` → `setEnabled(next)`，`busy` 期间禁用；失败 `role=alert` 就地报错（`DeveloperToolsRow.tsx:19-36`）。日志上传：`setEnabled(enabled)` → 写 bool；`busy`/`status!==ready`/`!writable` 时禁用；写结果用持久 Toast 显示（`UploadRow.tsx:26-49`、`upload-preference.ts`）。幂等（重复写同值由 settings 层吸收）。 |
| 6 | 视觉状态 | default 开/关；busy → disabled（灰）；失败 → 行内红字（开发者工具），或持久 Toast（日志上传）。focus-visible 走共享 Switch 环。 |
| 7 | 布局占位与展开形态 | 行固定参与布局流；不展开（开关行没有折叠体）。 |
| 8 | 滚动语义 | 归 General 页所在 options 列（`SettingsRoot.module.css:227-233`）滚动。 |
| 9 | 动效 | Switch 自身滑动动画（primitive）；无其它。 |
| 10 | 边界态 | 只读部署：Switch disabled；加载中：`status!=='ready'` → disabled；失败：错误文案/Tost 各 1 行，不破坏行高（描述单行）。 |
| 11 | 无障碍 | Switch 带可见 label（`aria-label`/label 关联）；失败文案 `role="alert"`；Toast 由共享组件播报。 |
| 12 | 依据 | `E0` `sw/ui-settings-general/src/client/DeveloperToolsRow.tsx:19-37`；`E0` `sw/ui-settings-session-log/src/client/UploadRow.tsx:26-50`；`E0` `upload-preference.ts:1-60`；`E0` `GeneralSection.tsx:14-19`。 |
| 13 | 反证 + 验收 | **反证**：若开关是「暂存」而非即时写，用户切完离开页面以为生效、实际没保存（与同页插件配置页的显式保存语义混淆）；若失败只沉默，用户会反复切换。**验收**：`SET-13` 切换后离开再进状态保持（真机）；`SET-14` 写失败时出现可见错误（Mock 500 + 真机）。 |

#### `settings.form.value-field` —— 共享「值字段」（文本 / 数字 / 密钥）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.form.value-field`。所属：插件配置页表单（Shell/agent-loop/web-search/subagent 各页）。出现条件：每字段 1 个；类型由字段 spec 决定（`settingsNumberField`/`settingsTextField`，`agent-loop-card-controller.ts:45`、`web-search-card-controller.ts:91-95`）。覆盖实现：`prim/settings-form/fields.tsx:49-115`（数值/文本）、`:125-151`（密钥）。 |
| 2 | 网格与定位 | 字段 `.field` 纵向栈（label 行 + input + hint）；label 与「已覆盖」Tag/「恢复默认」按钮同行、右侧对齐；input `height:32px`（models 页同类）或共享字段样式；hint 12px 在下方（`fields.tsx:64-113`、`ModelsSection.module.css:575-608`）。 |
| 3 | 命中区 | input 整宽 × 32px；「恢复默认」小按钮在 label 行右端；help 图标按钮 12px 图标（`fields.tsx:71-77`）；与相邻字段间距由表单纵向 gap。 |
| 4 | 手势 | 单击/聚焦输入 = 编辑草稿；单击「恢复默认」= 暂存一次 unset（字段回到组合层默认）；单击 help 图标 = 展开/收起帮助（`aria-expanded`）；无长按/双击。数字字段仅 `inputMode=numeric`（给键盘提示，**不限制输入内容**，`fields.tsx:52-57`）。 |
| 5 | 行为 | 输入只改草稿（`onEdit(text)`），不写；`invalid=true` 时字段不合法、表单保存按钮被禁用；「恢复默认」只暂存清空，保存时才写 unset。保存后草稿重置、`overridden` 状态由用户层是否含该 key 决定（`fields.tsx:24-28`、`ui-settings/src/client/config-form-types.ts:17-27`）。 |
| 6 | 视觉状态 | default；focus `border-color: business-primary`（models 页 `.input:focus`）；invalid：`aria-invalid` + 用错误文案替换 hint（红）；overridden：label 行出现「已覆盖」Tag + 恢复默认按钮；disabled：只读/不可用/保存中；help 展开：下方 `role=region` 帮助块。 |
| 7 | 布局占位与展开形态 | 参与表单纵向流；help 帮助块展开时**推开下方字段**（inline，非浮层）；错误文案替换 hint 行，行高不变（hint 与错误同尺寸 12/18）。 |
| 8 | 滚动语义 | 归设置面板 options 列滚动；无内滚。 |
| 9 | 动效 | 无（除 help 块显隐直接切换）。 |
| 10 | 边界态 | 空值：显示占位（继承默认）；非法数字：保存禁用 + 错误提示；只读部署：`readOnly` 提示条 + 字段 disabled；namespace 不再 served：整表单替换为 `unavailable` 文案（`SettingsForm.tsx:55`）；多语言：label/hint 由所属插件字典提供。 |
| 11 | 无障碍 | `<label htmlFor>` 关联；`aria-describedby` 指向 hint/错误/帮助；invalid 用 `aria-invalid`；help 按钮 `aria-expanded + aria-controls`；帮助块 `role="region" aria-label`。 |
| 12 | 依据 | `E0` `prim/settings-form/fields.tsx:49-115,125-151`；`E0` `prim/settings-form/SettingsForm.tsx:50-74`；`E0` `ui-settings/src/client/config-form-types.ts:8-34`；`E0` `sw/ui-settings-agent-loop/src/client/AgentLoopCard.tsx:20-41`；`E0` `sw/ui-settings-web-search/src/client/WebSearchCard.tsx:24-72`；`E0` `sw/ui-settings-subagent/src/client/SubagentLimitsFields.tsx:18-57`。 |
| 13 | 反证 + 验收 | **反证**：若数字字段在输入时自动改写（如把 1000 换成 1K），用户正在输入的字符会被吃掉；若「恢复默认」立即写而不是暂存，用户点错后无法通过「保存」以外的路径恢复原值；若非法输入不就地提示，用户只能看到保存按钮灰着却不知道哪个字段错。**验收**：`SET-15` 数字字段输入 `1K`/`256000` 均原样保留（单测）；`SET-16` 非法值使保存禁用且错误文案指向该字段（真机）；`SET-17` 恢复默认后保存才生效、取消离开不写（Mock 断言）。 |

#### `settings.form.save` —— 插件配置页的「保存」（唯一写入点）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.form.save`。所属：插件配置页（`plugins.item` 各页）表单底部。出现条件：每个配置页 1 个；`available=false` 时整表单被替换、无保存按钮。覆盖：`prim/SettingsForm.tsx:61-72`。 |
| 2 | 网格与定位 | `.footer` 底部一行；保存按钮右对齐；错误文案在按钮左侧（`SettingsForm.module.css`，随 primitive）。 |
| 3 | 命中区 | 共享按钮尺寸（sm）；footer 高度随内容，不悬浮。 |
| 4 | 手势 | 单击 = `onSave()`：把所有暂存字段一次性写成一个 `settings.mutate`（每字段一次写操作，同 revision fence）；键盘 Enter/Space。**没有「放弃」按钮**（`SettingsForm.tsx:6-10`）。 |
| 5 | 行为 | 保存条件：`dirty && !invalid && !saving`，否则 disabled（`:56`）；成功写后 `dirty` 清空；Host 拒绝 → `failed` 显示 `saveFailed` 文案但**保留草稿供修正**（`ui-settings-shell/locales.ts:26-27`）；离开页面（卸载）自动 `onDiscard()` 丢弃草稿（`SettingsForm.tsx:52-54`）。幂等（重复保存同一草稿第二次是 no-op，因 dirty 已清）。 |
| 6 | 视觉状态 | default；disabled（未改动/有非法字段/保存中）；saving 时文案变「保存中…」；failed 时按钮旁 `role=status` 文案；focus-visible 共享环。 |
| 7 | 布局占位与展开形态 | 表单末尾固定一行；错误文案出现/消失会轻微推高 footer（inline），不覆盖内容。 |
| 8 | 滚动语义 | 归设置面板 options 列滚动；footer 不吸附（滚出视口即不可见）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | namespace 不再 served → `unavailable` 行代替整表单（`:55`）；只读部署 → 顶部 `readOnly` 提示 + 控件 disabled（`:59`）；保存失败；并发保存（saving 时禁用）。 |
| 11 | 无障碍 | 原生 button；保存中/失败用 `role=status` 文案；不可用/只读用 `role=status`。 |
| 12 | 依据 | `E0` `prim/settings-form/SettingsForm.tsx:1-74`；`E0` `sw/ui-settings-shell/src/client/ShellCard.tsx:26-53`；`E0` `sw/ui-settings-agent-loop/src/client/AgentLoopCard.tsx:25-39`；`E0` `sw/ui-settings-web-search/src/client/WebSearchCard.tsx:30-70`；`E0` `sw/ui-settings-subagent/src/client/SubagentCard.tsx:29-50`；`E0` `sw/ui-settings-shell/src/client/locales.ts:13-46`。 |
| 13 | 反证 + 验收 | **反证**：若每次输入即时写，用户改一半离开会留下半改状态（例如 baseURL 清空后马上写 unset）；若离开页面不丢弃草稿，下次打开会看到上次未保存的脏值却不知道。**验收**：`SET-18` 编辑后不保存直接关闭，再开为原值（真机）；`SET-19` 保存失败草稿保留且可改后重试（Mock 409/500）；`SET-20` 只读部署控件 disabled 且提示可见（截图）。 |

### 2.3 模型与 provider 管理页（`settings.models.*`）

> 页面容器：`ModelsSection` 渲染 `settings.section` 内容列，`<h2>` 标题 + intro + 已保存提示 + provider 卡列表 + 添加区 + `settings.models.footer` 槽位（`ModelsSection.tsx:397-718`）。section 最大宽 720px、纵向 gap 12px（`ModelsSection.module.css:6-12`）。
> 页面是**即时写**模式：卡片内的 Apply 提交后立刻 `settings.mutate` + reload，没有全局保存按钮（`ProviderEditor.tsx:294-307`、`ModelsSection.tsx:301-315`）。

#### `settings.models.page-retry` —— 页面加载失败重试

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.models.page-retry`。所属：Models 页 → 错误态。出现条件：`state.status==='error'`；整页只 1 个。覆盖：`ModelsSection.tsx:324`。 |
| 2 | 网格与定位 | 内容列顶部：`<p class=error>` + 次级按钮（`ModelsSection.tsx:318-329`）；按钮 `.secondaryButton` 高度按共享按钮（约 32px）。 |
| 3 | 命中区 | 共享按钮尺寸；错误文案在按钮上方一行，不重叠。 |
| 4 | 手势 | 单击 = `controller.load()` 重新拉取目录；无长按/双击。 |
| 5 | 行为 | 重新拉取 provider 目录 + 设置命名空间 + 凭据状态；成功后页面恢复 ready。幂等。 |
| 6 | 视觉状态 | 错误文案 `--dsw-alias-state-error-primary`、12/18（`.error` `ModelsSection.module.css:624-629`）；按钮 secondary hover 背景。 |
| 7 | 布局占位与展开形态 | 错误态整页替换内容（不叠加），按钮在文字下方；无展开。 |
| 8 | 滚动语义 | 归设置面板 options 列滚动。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 首次加载（`idle`）→ 只显示标题+intro 骨架，不显示重试（`:330-337`）；有旧快照时背景刷新失败**保留旧内容**（`hasReadySnapshot`，`:237-240`）。 |
| 11 | 无障碍 | 原生 button；错误文案普通文本（不是 `role=alert`，见 Q-SET-16）。 |
| 12 | 依据 | `E0` `sw/ui-settings-models/src/client/ModelsSection.tsx:317-337`；`E0` `ModelsSection.module.css:624-629`；`E0` `store.ts`（`status:'error'`）。 |
| 13 | 反证 + 验收 | **反证**：若首次加载失败也显示「重试」，用户会在还不知道这页有什么时先看到红色错误；若背景刷新失败就整页替换成错误，正在编辑的草稿会被打断。**验收**：`SET-21` 有快照时刷新失败保留旧列表（单测）；`SET-22` 首载失败显示重试并恢复（Mock 500）。 |

#### `settings.models.row` —— provider 行（编辑 / 移除）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.models.row`。所属：Models 页 → provider 卡列表。出现条件：`configured` 行每行 1 个；另有 `needsSetup` 行改渲染 setup 卡（`:418-440`）。覆盖：`ModelsSection.tsx:478,483（编辑）`、`:497,502（移除）`；行容器 `:447-534`。 |
| 2 | 网格与定位 | 列表 `.rows` 纵向 `gap:8px; margin:12px 0 0`（css `:43-51`）；行卡 `.rowCard` 圆角 xl、`padding:12px 14px`、纵向 gap 12、卡片描边/填充 token（`:54-62`）；行头 `.rowHead` 横向 gap 10：身份（名称+Tag+凭据点）在左、动作区在右（`:64-68`、`.rowActions :116-125`）。动作是两个次级/危险按钮，共享按钮高约 32px（`:126-207`）。 |
| 3 | 命中区 | 编辑按钮与移除按钮各自是独立命中区（约 32px 高），行间 8px；行卡本身**不是**点击区（只有两个按钮可点，行头其余部分不响应）。 |
| 4 | 手势 | 单击「编辑」= 展开/收起该行编辑器（一次只开一个卡；开 add 卡时先关 add，`:483-491`）；单击「移除」= 打开删除确认弹窗（`:502-507`）。无长按/双击/滑动。 |
| 5 | 行为 | 编辑：`editing=target`，行下方渲染 `ProviderEditor`；再次点击同一行收起。移除：`deleteTarget=target` → 弹窗确认后先删凭据再 unset 配置，两步都可重试（`removeProviderProfile` `:141-158`）。幂等；可撤销（删除有确认；编辑是草稿）。 |
| 6 | 视觉状态 | 行卡 default：卡片描边/填充；名字 14/22 500；`declared=true` 加「自定义」Tag；凭据点：configured 绿点 / missing 红点（`role=img` + aria-label），未配置且无引用时不显示（`:454-475`、css `:88-115`）；编辑中行下方出现内嵌编辑器；错误 `role=alert` 在行头下方（`:415-417`）。 |
| 7 | 布局占位与展开形态 | 形态 A（inline-uncapped）：编辑器插在行卡内部、把后续 provider 行整体下移；行卡自身高度随内容增长。删除是形态 C（Modal 浮层）。 |
| 8 | 滚动语义 | 归设置面板 options 列；无内滚。 |
| 9 | 动效 | 无展开动画（直接挂载编辑器）。卡片 hover 无位移（仅按钮 hover）。 |
| 10 | 边界态 | 不可写部署 → 移除按钮 disabled（`:501`）；`state.status!=='ready'` → 编辑/移除 disabled（`:482,501`）；行错误文本；`needsSetup` 首跑姿态显示 setup 卡（`:418-440`）；被 `dismissedSetup` 关闭后回落为普通行（`:288-293`）。 |
| 11 | 无障碍 | 两个按钮都有 `aria-label`：`providerCopy(t('editProvider'), target)` / `removeProvider`（含 provider 名）；凭据点 `role="img"` + aria-label；行不是按钮。 |
| 12 | 依据 | `E0` `ModelsSection.tsx:409-535`；`E0` `ModelsSection.module.css:43-125,126-207`；`E0` `removeProviderProfile` `:141-158`。 |
| 13 | 反证 + 验收 | **反证**：若整行可点，用户想选中文字/滚动时容易误触打开编辑器；若移除无二次确认，一次误点就删掉 provider 配置与凭据（不可撤销）。**验收**：`SET-23` 行空白区点击不打开编辑器（单测）；`SET-24` 移除必须先确认，取消后配置不变（真机）。 |

#### `settings.models.add-flow` —— 添加 provider（入口 + 模式切换 + 目录选择）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.models.add-flow`。所属：Models 页 → 列表末尾添加区。出现条件：`catalogOffered || customOffered`（有可配置命名空间或 pi-ai 协议）；`catalogEnabled && customEnabled` 都有时显示模式开关，否则只显示唯一模式标题。覆盖：按钮 `ModelsSection.tsx:661,665`；模式开关 `:543-566`；provider 选择 `:589-604`；add 卡 `:537-682`。 |
| 2 | 网格与定位 | 添加按钮 `.addButton` `flex:1 1 0; min-width:180px; height:44px; border:1px dashed`、圆角 lg（css `:313-324`），整行宽；add 卡 `.addCard` `padding:14px 16px`、纵向 gap 14、模块底色（`:348-357`）；模式开关 `.addModes` 左对齐纵向 gap 8（`:328-333`）；两个面板 `.addPanel` 纵向 gap 14（`:338-346`）；provider select 最大宽 240px（`.selectInput` `:591-622`）。 |
| 3 | 命中区 | 添加按钮整行 44px 高；模式开关是共享 SegmentedControl（每段按钮）；provider `<select>` 整宽但最大 240px。 |
| 4 | 手势 | 单击添加 → 打开 add 卡（自动选中第一个可添加 provider，模式=catalog 或 custom）；单击模式段 → 切换面板（**两个面板都保持挂载**，`visited` 只增不减，`:245,376,579-653`）；选择 provider → 切换编辑对象（`setEditing(targetOf(picked.row))`）；`switchLocked`（任一面板写/探活中）时模式开关禁用（`:377,547`）。 |
| 5 | 行为 | 目录模式：选一个已注册但未配置的 provider，出现其 `ProviderEditor`，Apply 后 `announceSaved` 并刷新；自定义模式：出现 `CustomProviderCard`，Create 后关闭并 refresh。两模式草稿互不丢弃（面板 keep-alive）；关闭 add 卡会丢弃 catalog 目标（`closeAdd` `:271-276`）。 |
| 6 | 视觉状态 | 添加按钮 dashes 边、hover 背景；add 卡模块底；模式段选中态由 SegmentedControl 提供；`title` 提示「没有可添加项/不可用」（`:553,559`）；已选中 provider 的编辑器标题隐藏（`hideTitle`，`:610`）。 |
| 7 | 布局占位与展开形态 | 形态 A：add 卡内联在列表末尾，把下方 footer 推下；模式面板切换用 `hidden` 属性（不卸载，`:584,635`）。 |
| 8 | 滚动语义 | 归设置面板 options 列；add 卡自身不滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 无可添加目录项 → 目录模式段 disabled + title；无 pi-ai 命名空间 → 自定义模式不提供（只显示目录模式标题，`:571-574`）；不可写部署 → 添加按钮 disabled；写/探活进行中 → 模式开关锁定；refresh 移除了当前 draft 行 → 卡保留、由「第一个可添加行」兜底（`:383-389`）。 |
| 11 | 无障碍 | 添加按钮有可见文案 + 加号图标；SegmentedControl `label=t('addMode')`；select `aria-label=t('provider')`；隐藏面板用 `hidden`，tabpanel 模式带 `aria-labelledby`（`:582-583`）。 |
| 12 | 依据 | `E0` `ModelsSection.tsx:355-395,536-682`；`E0` `ModelsSection.module.css:302-346,591-622`。 |
| 13 | 反证 + 验收 | **反证**：若切模式卸载另一面板，用户切回去草稿就没了；若切换时另一面板还有写/探活在飞，答案会落到已卸载的面板上（孤儿响应）。**验收**：`SET-25` 两模式来回切换草稿保留（单测）；`SET-26` 探活中模式开关禁用（真机）。 |

#### `settings.models.delete-dialog` —— 删除 provider 确认

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.models.delete-dialog`。所属：Models 页 → 删除确认 Modal。出现条件：`deleteTarget!==undefined`；同一时刻最多 1 个。覆盖：取消 `ModelsSection.tsx:700`；确认 `:707`；Modal 定义 `:684-717`。 |
| 2 | 网格与定位 | 共享 Modal，`class=deleteDialog` 宽度 `min(480px,100%)`（`ModelsSection.module.css:631-633`）；footer：取消（outline）在左、删除（危险描边+红字）在右，`data-modal-autofocus` 在取消上（`:698-714`）。 |
| 3 | 命中区 | 共享 Button 尺寸；两个按钮间由 Modal footer gap 分隔；默认焦点在取消按钮。 |
| 4 | 手势 | 单击取消/确认；Escape/遮罩关闭由 Modal 提供；确认会先删凭据再 unset 配置，两步串行（`removeProviderProfile`）。 |
| 5 | 行为 | 取消：清空 target/failure，无写。确认：`deleting=true` → 删除；失败保留弹窗并显示失败文案（`:716`）、按钮恢复可重试；成功关闭。**不可撤销**（无 undo）。 |
| 6 | 视觉状态 | 删除按钮常态红描边红字，hover 红底（`:635-642`）；deleting 时两按钮 disabled、确认文案变「删除中…」；失败文案红色在 body。 |
| 7 | 布局占位与展开形态 | 形态 C：Modal 浮层，不占位。 |
| 8 | 滚动语义 | 弹窗内容短，无内滚（Modal 自带最大高约束）。 |
| 9 | 动效 | 共享 Modal 的进入/退出。 |
| 10 | 边界态 | 只删除用户添加的 provider（`row.removable` 才出现移除按钮）；带托管凭据时文案不同（`deleteDescriptionWithCredential`，`:691-696`）；并发删除禁用；失败保留。 |
| 11 | 无障碍 | Modal `title`/`description` 由插件文案给出；取消按钮 `data-modal-autofocus` 接收初始焦点；危险按钮靠文案而非颜色传达。 |
| 12 | 依据 | `E0` `ModelsSection.tsx:141-158,295-315,684-717`；`E0` `ModelsSection.module.css:631-642`。 |
| 13 | 反证 + 验收 | **反证**：若确认按钮默认聚焦，用户连按 Enter 会误删；若先 unset 配置再删凭据，凭据删失败时 provider 已不可见但密钥还留着。**验收**：`SET-27` 默认焦点在取消（DOM 断言）；`SET-28` 凭据失败时 provider 行仍可见且可重试（Mock）。 |

##### provider 编辑卡的字段族（`settings.models.editor.*`）

> 五个字段节点共享同一张卡：`ProviderEditor`（`ProviderEditor.tsx:501-538`）。卡片容器 `.editor`、标题行 `.editorHeader`、字段 `.field`（label+input）、hint/error 12px。**卡片是「草稿 + Apply」**：输入只改本地草稿，Apply 组装 path ops 一次写完（`applyOnce` `:261-307`）。「推理等级」故意不在卡上（每模型能力，`ProviderEditor.tsx:14-19`）。

#### `settings.models.editor.key` —— API Key 输入（write-only）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.models.editor.key`。所属：provider 编辑卡 / onboarding 凭据卡 / 自定义 provider 卡。出现条件：每个编辑卡 1 个（`credentialOnly` 时是唯一字段）。覆盖：`ProviderEditor.tsx:383`；`CustomProviderCard.tsx:271`。 |
| 2 | 网格与定位 | 字段块在卡内纵向流顶部（自定义卡里在 baseURL/协议之后，`CustomProviderCard.tsx:269-287`）；input 整宽 32px、圆角 md、password 类型；下方是错误/hint（`ModelsSection.module.css:575-608`）。 |
| 3 | 命中区 | 整宽 input（高度 32px）；无附加按钮（无「显示/隐藏密钥」、无「粘贴」按钮，未见实现）。 |
| 4 | 手势 | 聚焦输入 = 编辑草稿；无长按/双击；浏览器密码管理器被 `autoComplete="new-password"` 明确回避（`ProviderEditor.tsx:386`）。 |
| 5 | 行为 | **只写不读**：字段初值永远空；输入非空时 Apply 会经 `credentials/set` 写该引用（`applyOnce:301-305`）；空输入表示「不改 key」（保留已有）；`keyState.configured` 只用于占位文案「已配置」（`:360-364`）。失败在字段下方报错。 |
| 6 | 视觉状态 | default（占位：未配置→「输入 API Key」/已配置→「已配置，留空则不修改」/锁定→「由环境变量提供」）；invalid（`aria-invalid` + 错误文案）；disabled（只读/凭据不可写/保存中）；无 configured 时的 Tag 形态（语义在占位+`role` 无）。 |
| 7 | 布局占位与展开形态 | 固定字段高度，错误文案出现时把下方字段推下（inline）。 |
| 8 | 滚动语义 | 归设置面板 options 列滚动。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 空输入不覆盖已有 key（关键边界）；粘贴带空白 → `trim()` 后判断空（`:236`）；`credentialRequired` 且输入全空白 → 阻止提交（`:237-241`）；锁定（env 提供）→ disabled + 占位说明；只读部署 → disabled。 |
| 11 | 无障碍 | `<label htmlFor>` + `aria-label`；错误 `aria-invalid`；password 类型不会被读屏朗读内容；无「显示密码」控件。 |
| 12 | 依据 | `E0` `ProviderEditor.tsx:213-241,330-397,261-307`；`E0` `apiKey.ts`（key 格式校验）；`E0` `CustomProviderCard.tsx:269-287`；`E0` `ModelsSection.module.css:575-608`。 |
| 13 | 反证 + 验收 | **反证**：若空输入被当成「清空 key」，用户只想改 baseURL 却把密钥删了；若字段回显已存密钥，密钥就随设置响应在浏览器里可见（安全退化）。**验收**：`SET-29` 改 baseURL 且 key 留空后已有 key 不变（Mock 断言）；`SET-30` 任何时刻页面 DOM 都不含已存密钥明文（真机抓 DOM）。 |

#### `settings.models.editor-customized` —— 「自定义设置」原生折叠区

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.models.editor.customized`。所属：编辑卡 → 高级字段折叠区。出现条件：`credentialOnly!==true` 且布局非 unknown（`:398`）。覆盖：`:398,399`。 |
| 2 | 网格与定位 | 原生 `<details>`，顶部 0.5px 分隔线 + `padding-top:10px`（css `:366-369`）；summary `inline-flex; gap:6px; padding:2px 4px; margin-left:-4px; font 12/18 500`，箭头用 `::before` 5px 方块旋转（`:373-405`）；展开体 `.customizedBody` 纵向 gap 12、padding-top 12（`:411-416`）。 |
| 3 | 命中区 | summary 整行文字+箭头（fit-content 宽，不是整卡宽）；`cursor:pointer`。 |
| 4 | 手势 | 单击 summary = 展开/收起（原生 details）；键盘 Enter/Space（原生）。 |
| 5 | 行为 | 展开显示：显示名（仅 hand-declared）、baseURL、协议（仅 hand-declared）、模型目录（DeepSeek 容量表 / pi-ai 列表+探活）。折叠不丢草稿（React state 在卡内）。 |
| 6 | 视觉状态 | default：secondary 字色；hover：primary；展开：`[open]` 时箭头转 45°（`:403-405`）；focus-visible 共享环。 |
| 7 | 布局占位与展开形态 | 形态 A：展开把卡下方字段/按钮整体推下；不是内滚。 |
| 8 | 滚动语义 | 归设置面板 options 列。 |
| 9 | 动效 | 箭头 `transform 120ms ease`（`:399-401`）；reduced-motion 下关闭（`:655-660`）。 |
| 10 | 边界态 | 无「自定义设置」的 namespace（unknown layout）→ 整卡只显示 hint 不显示此折叠区（`:513-515`）；折叠状态不持久化（每次挂载默认收起）。 |
| 11 | 无障碍 | 原生 details/summary 语义（展开态由浏览器暴露）；箭头是 CSS 伪元素（`aria-hidden` 天然）；summary 文本即标签。 |
| 12 | 依据 | `E0` `ProviderEditor.tsx:398-496`；`E0` `ModelsSection.module.css:366-416,655-660`。 |
| 13 | 反证 + 验收 | **反证**：若把高级字段直接铺开，每张卡会长到两屏，常规编辑（填 key）要滚很久；若用自定义 div 折叠而不给展开语义，读屏无法知道折叠状态。**验收**：`SET-31` 折叠时卡片高度只含 key 字段（截图）；`SET-32` 读屏报「可折叠/已展开」（真机）。 |

#### `settings.models.editor.fields` —— 身份与端点字段

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | 四个字段节点：`settings.models.editor.display-name`（显示名，仅 hand-declared 路由，`ProviderEditor.tsx:408`）、`settings.models.editor.base-url`（端点，两族都有，`:430`）、`settings.models.editor.protocol`（协议 select，仅 hand-declared，`:452`）、`settings.models.editor.route-id`（route id，仅「声明新 provider」的创建卡，`CustomProviderCard.tsx:214`）。每卡各 1 个。 |
| 2 | 网格与定位 | 都在 `.customizedBody`/创建卡纵向流内：`.field`（label 行 + 32px input）+ hint 12px；`<select>` 加 `.selectInput`（`max-width:240px`、右侧 12px 内联 chevron 背景、padding-right 32，css `:575-622`）。 |
| 3 | 命中区 | input 整宽 32px；select 最大 240px 宽；label 不可点（不是 `<label for>`，是 `span`，见 Q-SET-16）。 |
| 4 | 手势 | 聚焦/输入 = 改草稿；select 单击打开原生下拉；无长按/双击。 |
| 5 | 行为 | 显示名：空 → 回落 route id（占位显示继承值，`:408-424`）；baseURL：空 → 删除该 key（回到继承），值非法（非 http/https，创建卡）→ `aria-invalid` + 错误；协议：仅 hand-declared 可选，选择写 `api`；route id：创建卡校验 `^[a-z][a-z0-9]*(-[a-z0-9]+)*$` + 重名检测，失败就地报错并阻止提交（`CustomProviderCard.tsx:52,119-120,132-135,226-228`）。全部只改草稿，Apply 才写。 |
| 6 | 视觉状态 | default/hint；invalid：`aria-invalid` + 红错误文案；disabled：只读/提交中（创建卡在 profile 已创建后 `profileDisabled`，`:115-117`）；占位显示继承值（不写值）。 |
| 7 | 布局占位与展开形态 | 固定字段高度；错误文案出现推下后续字段（inline）。 |
| 8 | 滚动语义 | 归设置面板 options 列。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 显示名/协议的「未覆盖」显示继承值占位；baseURL 空 = 用默认；创建卡的三种失败（正则/重名/URL）分别报错；只读部署 disabled。 |
| 11 | 无障碍 | input/select 都带 `aria-label`（本地化）；invalid `aria-invalid`；label 是 `span`（视觉关联但非语义关联，见 Q-SET-16）；baseURL 有 `aria-describedby` 指向 deepseek 端点 hint（`:437`）。 |
| 12 | 依据 | `E0` `ProviderEditor.tsx:404-470`；`E0` `CustomProviderCard.tsx:41-61,119-135,210-268`；`E0` `ModelsSection.module.css:575-622`；`E0` `protocol-label.ts`。 |
| 13 | 反证 + 验收 | **反证**：若显示名清空就写空串，provider 标题会变空白；若 baseURL 不做协议校验就提交，Host 侧错误会以路径名出现（用户看不懂）；若 route id 允许数字开头，`deriveKeyRef` 生成的凭据名在 POSIX shell 下非法（`CustomProviderCard.tsx:44-52` 注释）。**验收**：`SET-33` 清空显示名后标题回退 route id（真机）；`SET-34` 非法 baseURL/route id 就地报错且提交禁用（单测）。 |

#### `settings.models.editor-footer` —— 编辑卡动作行（取消 / 应用·创建）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.models.editor-footer`。所属：每张 provider 编辑/创建卡底部。出现条件：卡挂载即有；1 个。覆盖：`EditorFooter.tsx:49,53,57,61`。 |
| 2 | 网格与定位 | `.editorActions` 横向、右对齐（css `:296-300`）；取消（secondary）在左、提交（primary 或危险）在右；与字段间距 `gap:12px`。 |
| 3 | 命中区 | 共享按钮尺寸（约 32px 高）；两按钮间 gap。 |
| 4 | 手势 | 单击取消 = `onClose(false)` 丢弃草稿；单击提交 = `onSubmit`（Apply/Create）；键盘 Enter/Space。 |
| 5 | 行为 | 提交门禁由所属卡计算（`submitDisabled`：只读/unknown 布局/模型行校验失败/密钥格式非法/必填 key 缺失，`ProviderEditor.tsx:527-530`；创建卡再叠加 route/baseURL/models 门禁，`CustomProviderCard.tsx:132-135,312`）；提交中 `busy` → 取消也禁用（防半途卸载）、提交文案变 busy；成功 `onClose(true)` 触发刷新。取消在只读部署仍可用（可关闭卡片，`EditorFooter.tsx:11-13`）。 |
| 6 | 视觉状态 | 取消 secondary；提交 primary，创建/删除用对应变体；disabled 灰；busy 时两按钮 disabled 且提交文案「应用中…」。 |
| 7 | 布局占位与展开形态 | 卡底部固定一行，不吸底（随卡滚动）。 |
| 8 | 滚动语义 | 归设置面板 options 列。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 只读部署：提交 disabled、取消可用；提交失败：卡内错误文案，footer 恢复；busy 并发点击被 disabled 拦住；卡关闭即卸载（草稿丢）。 |
| 11 | 无障碍 | 原生 button；busy 文案变化会被读屏感知（按钮文本变化）；无 aria-busy（见 Q-SET-16）。 |
| 12 | 依据 | `E0` `EditorFooter.tsx:1-67`；`E0` `ModelsSection.module.css:296-300`；`E0` `ProviderEditor.tsx:517-537`；`E0` `CustomProviderCard.tsx:309-317`。 |
| 13 | 反证 + 验收 | **反证**：若 busy 时取消可用，卡片卸载后写入响应回来无处落地（孤儿）；若禁写部署连取消都禁用，用户会被困在一张改不了的卡里。**验收**：`SET-35` 提交中取消不可点（单测）；`SET-36` 只读部署能关闭卡片（真机）。 |

#### `settings.models.model-list` —— 模型目录区（继承提示 / 恢复继承 / 拉取 / 添加）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.models.model-list`。所属：编辑卡内模型目录区。出现条件：每卡 1 个（credentialOnly 不渲染）；pi-ai 卡额外有「拉取模型」与自定义区。覆盖：`DeepSeekModelsEditor.tsx:258,262,296,300`；`ModelListEditor.tsx:333,337,343,350,396,400`。 |
| 2 | 网格与定位 | `.modelCatalog` 纵向；`.modelListHead` 横向 `space-between`（标题+继承状态在左、动作在右，css `:418-458`）；列表 `.modelList` 纵向 gap 8（`:447-451`）；「添加模型」`.addModelButton` 28px 高、左对齐（`:553-573`）；空态 `.modelEmpty` 虚线框居中（`:546-551`）。 |
| 3 | 命中区 | 「恢复继承」「拉取模型」「添加模型」都是文字按钮（约 28px 高）；模型行输入是独立命中区（见 `model-row`）。 |
| 4 | 手势 | 单击恢复继承 = 删除用户层 models（一行一次）、单击拉取 = 探活端点并弹候选、单击添加 = 追加 `{id:''}` 空行；无长按/双击。 |
| 5 | 行为 | 继承态（`overridden=false`）显示继承来的行与「继承」标记，任何编辑把整数组物化成用户覆盖；恢复继承 = 删除该 key、回到组合层（不复制默认值）；拉取失败显示错误文案且不弹窗、空结果提示；拉取结果弹窗内勾选后采用。幂等（恢复继承重复点只在 overridden 时出现）。 |
| 6 | 视觉状态 | 继承/自定义的状态文案（`modelsInherited/modelsCustomized`）；行卡描边（`.modelEntry` `:460-464`）；拉取中按钮文案变「拉取中…」且 disabled；错误红字；`overridden=false` 时不显示恢复继承按钮。 |
| 7 | 布局占位与展开形态 | 列表内联增长（添加行推下）；拉取弹窗形态 C。 |
| 8 | 滚动语义 | 归设置面板 options 列；候选弹窗内列表独立滚动（`.candidateList max-height:320; overflow-y:auto`，`:685-694`）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 空模型列表 → 「暂无模型」虚线框；非自定义时隐藏恢复继承；拉取需要 provider 或 baseURL（`askable`，`ModelListEditor.tsx:315-317`）；key 格式非法时拉取禁用并给 title 原因（`:346-349`）；只读部署 disabled。 |
| 11 | 无障碍 | 区域 `aria-label=t('models')`；空态提示为文本；拉取按钮 title 说明不可用原因；候选弹窗为共享 Modal。 |
| 12 | 依据 | `E0` `DeepSeekModelsEditor.tsx:247-307`；`E0` `ModelListEditor.tsx:315-461`；`E0` `ModelsSection.module.css:418-573,662-724`。 |
| 13 | 反证 + 验收 | **反证**：若恢复继承把默认值复制进用户层，之后默认值变化页面不会再跟随（继承语义被破坏）；若空列表不显示虚框提示，用户会以为加载失败。**验收**：`SET-37` 恢复继承后刷新仍显示新默认（单测）；`SET-38` 拉取失败/空结果有明确文案（Mock）。 |

#### `settings.models.model-row` —— 单个模型行（id/name 输入、展开、删除）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.models.model-row`。所属：模型目录列表。出现条件：每个模型 1 行；行前有序号（1-based）用于 aria-label。覆盖：`ModelRow.tsx:50,62,65,71,75,81`。 |
| 2 | 网格与定位 | `.modelEntry` 描边圆角、`padding:6px`；`.modelRow` 网格 `minmax(0,1.4fr) minmax(0,1fr) auto auto`、gap 6（css `:460-471`：id 输入、name 输入、展开按钮、删除按钮）；`.iconButton` 28×28（`:475-497`）；展开体 `.modelAdvanced` 见下。 |
| 3 | 命中区 | id/name 输入各整格（约 32px 高）、展开 28×28、删除 28×28；两图标按钮间由 grid gap 6px 分隔（<24dp，见 Q-SET-15）。 |
| 4 | 手势 | 输入 = 改草稿；单击展开/收起；单击删除 = 删除该行（无二次确认，见 Q-SET-10 对比 provider 删除）；无长按/双击。 |
| 5 | 行为 | 每击键即时改草稿（父级 `onChange` 换整数组）；id 失焦 trim；删除会重排后续行的编辑缓冲与展开状态（`DeepSeekModelsEditor.tsx:176-196`、`ModelListEditor.tsx:194-207,381-392`）；行不合法只影响 Apply 门禁，不阻止输入。 |
| 6 | 视觉状态 | default；展开（chevron 下/右切换）；disabled（只读/提交中）；focus-visible 共享环；非法行由卡底「第 N 行：原因」提示、行本身无红框（见 Q-SET-16）。 |
| 7 | 布局占位与展开形态 | 形态 A：展开体插在行内、把后续行与「添加模型」推下。 |
| 8 | 滚动语义 | 归设置面板 options 列。 |
| 9 | 动效 | 无（chevron 直接切换）。 |
| 10 | 边界态 | 空 name → 删除该字段（`:60`）；id 为空 → 行不合法、Apply 禁用并提示「第 N 行：需要 id」（`validateDeepSeekModels`）；重复 id → 「重复」提示；删除唯一行后列表空态；行号随删除左移（缓冲区重排）。 |
| 11 | 无障碍 | 输入 `aria-label="模型 ID 第 N 行"`/「模型名称 第 N 行」；展开按钮 `aria-expanded` + `aria-label="高级 第 N 行"`；删除按钮 `aria-label="删除模型 第 N 行"`；行本身不是按钮。 |
| 12 | 依据 | `E0` `ModelRow.tsx:44-114`；`E0` `ModelsSection.module.css:460-503`；`E0` `DeepSeekModelsEditor.tsx:165-210,285-292`；`E0` `ModelListEditor.tsx:357-394`。 |
| 13 | 反证 + 验收 | **反证**：若删除模型无确认（对比 provider 删除有确认），用户误点就丢一行已调好的容量；若展开态按行号而不是按模型身份记忆，删除中间行后展开状态会错位到别的模型上（代码按索引重排正是为此）。**验收**：`SET-39` 删除中间行后其余行展开态跟随正确模型（单测）；`SET-40` 空 id/重复 id 的 Apply 门禁与行号提示（真机）。 |

#### `settings.models.model-advanced` —— 模型高级字段（容量 / 输入类型）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.models.model-advanced`。所属：模型行展开体。出现条件：该行展开；每行 1 份。覆盖：`ModelRow.tsx:92,101`（容量输入）、`ModelInputTypes.tsx:40-55`（文本/图片复选）；缓冲区结算 `DeepSeekModelsEditor.tsx:244`。 |
| 2 | 网格与定位 | `.modelAdvanced` 网格 2 列、gap 8、`padding:8px 4px 2px`（css `:505-510`）；容量字段纵向 label+input（`.modelField`，`:512-517`）；输入类型 `fieldset` 跨两列（`:519-538`），复选框横向 gap 16、min-height 32。 |
| 3 | 命中区 | 两个容量输入整格 32px；两个 Checkbox 约 16×16 + 文字标签（label 包裹整行，可点）。 |
| 4 | 手势 | 输入容量 = 改草稿（文本缓冲，blur 时结算）；单击 checkbox = 切换输入类型；无长按/双击。 |
| 5 | 行为 | 容量以「K/M 后缀文本」输入（`256K`→256000，`1M`→1000000；非法文本保留在屏幕上等改，不静默改号，`DeepSeekModelsEditor.tsx:29-68,220-245`）；空 = 继承（placeholder 显示默认）；输入类型：文本/图片至少保留一项（当前唯一选中项 disabled）；DeepSeek 关闭 image 时顺带删除 `imagePixelBudget/imageMaxBytes`（`ModelInputTypes.tsx:45-55`）。 |
| 6 | 视觉状态 | default；输入非法时值保留（无红框，靠保存门禁文案）；checkbox checked/unchecked/disabled；`inputMode=numeric` 仅键位提示。 |
| 7 | 布局占位与展开形态 | 展开体固定两行网格；输入类型换行时随内容增高（inline）。 |
| 8 | 滚动语义 | 归设置面板 options 列。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 容量留空 = 继承（placeholder 为路由级默认，页面计数 K=1000 与适配器 262144 不同源，代码注释明确不镜像，`ModelListEditor.tsx:105-119`）；非法容量 → Apply 禁用；输入类型只剩一项时该项不可取消；只读 disabled。 |
| 11 | 无障碍 | 容量 `<label>` 包裹 + `aria-label="上下文窗口 第 N 行"`；输入类型 `<fieldset aria-label="输入类型 第 N 行">` + legend；Checkbox 有可见 label。 |
| 12 | 依据 | `E0` `ModelRow.tsx:86-111`；`E0` `ModelInputTypes.tsx:32-61`；`E0` `DeepSeekModelsEditor.tsx:29-68,213-245`；`E0` `ModelListEditor.tsx:102-130`；`E0` `ModelsSection.module.css:505-538`。 |
| 13 | 反证 + 验收 | **反证**：若容量输入每次击键都从数字重新拼字，输入 `1000` 会被改写成 `1K` 打断输入；若允许取消唯一的输入类型，模型会变成「无输入类型」而 adapter 拒绝。**验收**：`SET-41` 输入 `1000`/`256K`/`1M` 往返不丢值（单测）；`SET-42` 最后一个输入类型 checkbox 禁用（单测）。 |

#### `settings.models.fetch-dialog` —— 「拉取模型」候选弹窗

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.models.fetch-dialog`。所属：pi-ai/自定义 provider 的模型列表。出现条件：`candidates!==undefined`（拉取成功且非空）；同一时刻最多 1 个。覆盖：`ModelListEditor.tsx:415,416,421,433,445`；Modal `:406-458`。 |
| 2 | 网格与定位 | 共享 Modal，`class=fetchDialog` 宽 `max-width:520px`（css `:662-671`）；工具栏 `.candidateToolbar` 横向 gap 8（搜索框 flex 1 1 240 + 全选按钮，`:673-683`）；候选列表 `.candidateList` 纵向 gap 2、`max-height:320; overflow-y:auto`（`:685-694`）；每行 label 内 checkbox + id（`:696-712`）。 |
| 3 | 命中区 | 复选框行整行 label 可点（约 24-28px 高）；搜索框整宽；「全选/取消全选」文字按钮；footer 取消/采用按钮。 |
| 4 | 手势 | 单击 checkbox 切换单个候选；单击全选 = 切换所有可见候选；输入搜索 = 本地过滤；单击取消/采用；Escape/遮罩关闭（Modal）。 |
| 5 | 行为 | 打开时默认勾选「尚未在列表中的候选」（已配置的行默认不勾，避免覆盖用户调好的容量，`:254-259`）；采用时按 id 合并：已有行保留用户值、新候选插入（`:271-285`）；采用后关闭弹窗；空候选不打开（提示「没有发现模型」，`:250-253`）。 |
| 6 | 视觉状态 | 行 default/hover；checkbox 选中；搜索无匹配 → 「无匹配」`role=status`（`:438-440`）；全选按钮文案随「是否全部可见已选」切换（`:301-313,429-436`）。 |
| 7 | 布局占位与展开形态 | 形态 C：Modal 浮层；候选列表内滚（到边即停）。 |
| 8 | 滚动语义 | 候选列表内滚（`overflow-y:auto`）；弹窗外层不滚；到边不串联（未见自定义）。 |
| 9 | 动效 | 共享 Modal 动效。 |
| 10 | 边界态 | 无候选不打开；搜索无匹配；全选在无可见项时禁用；已配置项默认不勾；id 展示用 title 显示 name（`:450`）。 |
| 11 | 无障碍 | Modal title/description；搜索框 `aria-label`；checkbox 原生语义 + 行文本；空结果 `role=status`。 |
| 12 | 依据 | `E0` `ModelListEditor.tsx:234-313,406-458`；`E0` `ModelsSection.module.css:662-724`。 |
| 13 | 反证 + 验收 | **反证**：若默认全选（含已配置行），采用会覆盖用户手工修正的容量；若采用时整表替换而不是合并，未勾选的已有行会被删掉。**验收**：`SET-43` 已配置行默认不勾（单测）；`SET-44` 采用后未勾选的已有行保留（单测）。 |

#### `settings.models.onboarding-welcome` —— 版本欢迎公告（settings.onboarding 步骤）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.models.onboarding-welcome`。所属：设置壳 → onboarding 槽位（`id:'welcome-notice', order:-100`）。出现条件：非桌面端 + 公告版本未被确认（`index.ts:149-154`；`WelcomeNotice.tsx:48-52`）。全局最多 1 个。覆盖：`WelcomeNotice.tsx:70`。 |
| 2 | 网格与定位 | 共享 onboarding Modal（`OnboardingModal`）：`width:min(600px,100%); max-height:100%; padding:0`，内容区 `padding:28px` 可滚（`OnboardingModal.module.css:1-33`）；正文段落 + 底部主按钮右对齐（`WelcomeNotice.module.css`）。 |
| 3 | 命中区 | 主按钮（共享 Button primary）；弹窗无关闭 X（`headless` + `onClose` 空实现，必须点继续）。 |
| 4 | 手势 | 单击「继续」= 写确认版本并 `complete()`；无 Esc/遮罩关闭（`OnboardingModal` 的 `onClose=ignoreImplicitDismiss`，`:8,42`）。 |
| 5 | 行为 | 打开时 `controller.load()` 读已确认版本；确认写 `welcomeNoticeVersion` 设置；成功后步骤完成、shell 换下一个 onboarding 步骤；已确认则不出现。幂等（重复确认同版本）。 |
| 6 | 视觉状态 | 主按钮 default/hover/disabled（`saving`）；失败 `role=alert` 文案（`WelcomeNotice.tsx:64`）；弹窗把 `#root` 置 inert（`OnboardingModal.tsx:26-32`）。 |
| 7 | 布局占位与展开形态 | 形态 C：模态浮层；正文超长时内容区自身滚。 |
| 8 | 滚动语义 | `.content` 内滚（`overflow-y:auto`）；到边即停。 |
| 9 | 动效 | 共享 Modal 进入/退出。 |
| 10 | 边界态 | 桌面端不注册该步骤（`if(!('dshDesktop' in globalThis))`，`index.ts:149`）；加载中/已确认 → 渲染 null；失败保留弹窗可重试。 |
| 11 | 无障碍 | Modal 标题 + `focusTitle` 时标题可聚焦（`tabIndex=-1`）；正文段落；`#root` inert 保证焦点不逃逸。 |
| 12 | 依据 | `E0` `sw/ui-settings-models/src/client/WelcomeNotice.tsx:34-77`；`E0` `OnboardingModal.tsx:8-52`；`E0` `OnboardingModal.module.css:1-33`；`E0` `welcome-store.ts`；`E0` `index.ts:149-154`。 |
| 13 | 反证 + 验收 | **反证**：若公告可 Esc/点遮罩关闭但不记录已确认，用户每次进设置都会再看到同一公告；若确认失败仍关闭，用户以为看过了。**验收**：`SET-45` 点继续后重启不再出现同版本公告（真机）；`SET-46` 写失败时公告不关闭（Mock）。 |

#### `settings.models.onboarding-credential` —— 官方 DeepSeek 凭据引导（settings.onboarding 步骤 + sign-in 槽位）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.models.onboarding-credential`。所属：设置壳 → onboarding（`id:'deepseek-official', order:0`）。出现条件：无任何可用 provider 且官方凭据缺失（自动）或被显式要求（`DeepSeekOnboardingDialog.tsx:58-99`）；`automatic = credentialOnboarding && !dshDesktop`（`index.ts:82`）。覆盖：`:110-136`（复用 ProviderEditor + Modal）。 |
| 2 | 网格与定位 | onboarding Modal（600px 宽）+ 内嵌 `ProviderEditor credentialOnly`（只有 key 字段 + 动作行）；description 一段文案。 |
| 3 | 命中区 | key 输入整宽；底部「稍后」/「保存」两个按钮（`cancelLabelKey='onboardingLater'`、`submitLabelKey='onboardingSave'`）；`onboardingLater` 只关闭步骤。 |
| 4 | 手势 | 输入 key → 保存 = 写凭据并完成步骤；单击「稍后」= 跳过本步（`complete()`）；`credentialRequired` + `autoFocusCredential`：必填且自动聚焦。 |
| 5 | 行为 | 保存成功 → `controller.load()` 重新评估 readiness；仍缺凭据则步骤保持；已有可用 provider 时自动完成（`:68-75`）；显式打开时即使 provider-ready 也显示（`explicit`）。 |
| 6 | 视觉状态 | 同编辑卡（key 字段/按钮）；保存中 busy；失败卡内错误；`settings.models.sign-in` 槽位可用时先渲染登录替代（`renderSlot(... fallback: editor)`，`:136`）。 |
| 7 | 布局占位与展开形态 | 形态 C：onboarding 模态；key 字段是唯一内容。 |
| 8 | 滚动语义 | Modal 内容区滚动。 |
| 9 | 动效 | 共享 Modal。 |
| 10 | 边界态 | 桌面端不自动弹（`credentialOnboarding && !dshDesktop`）；adapter 缺失/不可用 → 直接完成（`:70-75`）；key 全空白 + required → 阻止提交并报错；只读凭据 → 控件禁用。 |
| 11 | 无障碍 | Modal 标题 + 描述；key 输入 label/aria-label；`autoFocus` 把焦点放输入框；`#root` inert。 |
| 12 | 依据 | `E0` `DeepSeekOnboardingDialog.tsx:58-137`；`E0` `ProviderEditor.tsx:324-397`；`E0` `sw/ui-settings-models/src/client/index.ts:82,100-108,155-161`；`E0` `OnboardingModal.tsx:17-52`。 |
| 13 | 反证 + 验收 | **反证**：若「稍后」也写一个空凭据，之后 readiness 会误判为已配置（应只跳过不写）；若已能对话的用户仍被强制弹窗，会被打断。**验收**：`SET-47` 已有可用 provider 时不再弹（真机）；`SET-48` 稍后不落任何配置（Mock 断言）。 |

### 2.4 Plugins 设置区与插件清单（`settings.plugins.*` / `settings.plugin-inventory.*`）

> Plugins 区结构：`settings.section(id:plugins, order:15)` 渲染标题 + intro + 页签 + 页签面板；页签来自 `settings.plugins.tab`（`PluginsSettingsSection.tsx:53-118`）。页签面板 keep-alive：首次选中后一直挂载（隐藏），以保留本地草稿/展开/搜索状态（`:42-51,98-115`）。内置页（Shell/agent-loop/web-search/subagent）由各自包注册为 `plugins.item`，其内容 = `settings.form.*` 表单，本文不重复建节点。

#### `settings.plugins.tab` —— Plugins 区页签（tablist / roving tabindex）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.plugins.tab`。所属：Plugins 设置区。出现条件：`settings.plugins.tab` 注册项 >1 时显示制表栏；恰好 1 项时直接把该项渲染为页面、不显示页签（`PluginsSettingsSection.tsx:40,57-59`）。覆盖：`:61,65,70,76,77,107`。 |
| 2 | 网格与定位 | `.tabs` 横向 `align-items:flex-end; gap:22px; border-bottom:0.5px`（css `:23-29`）；页签 `.tab` `padding:7px 1px 9px; font 13/20; 下划线 ::after 2px`（`:31-58`）；面板 `.panel` `padding-top:2px`（`:67-70`）。 |
| 3 | 命中区 | 页签按钮宽度 = 文本宽 + 1px 水平 padding（**命中区窄**，低于 44dp，见 Q-SET-15）；页签间 22px gap。 |
| 4 | 手势 | 单击切换；键盘：←→ 循环、Home/End 跳首尾，切换后**手动 focus 下一个页签**（`:77-91`）；Tab 只进入当前选中页签（roving tabindex）。 |
| 5 | 行为 | 只切 `activeId`；切换不卸载已访问面板（`visitedIds` 只增）；未访问面板首次选中才挂载。无返回栈。 |
| 6 | 视觉状态 | default tertiary 字；hover/active primary + 2px 下划线；focus-visible 焦点环 + 下划线（`:43-65`）；`aria-selected`/`data-active` 双标记。 |
| 7 | 布局占位与展开形态 | 页签行固定高度（内容高）；面板切换只切 `hidden`，**不改变外层高度结构**；面板内滚动归设置面板。 |
| 8 | 滚动语义 | 页签行不滚（数量多时会溢出，未见横向滚动/换行实现，见 Q-SET-14）；面板内容归设置 options 列滚动。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 0 项 → 空态文案（`:57`）；1 项 → 无页签直接渲染（`:57-58`）；多语言标签宽度变化；隐藏面板保留 state（草稿不丢）。 |
| 11 | 无障碍 | `role=tablist` + `aria-label`；`role=tab` + `aria-selected` + `aria-controls`；`role=tabpanel` + `aria-labelledby`；roving tabindex（`:61-75,103-110`）。 |
| 12 | 依据 | `E0` `sw/ui-settings-plugins/src/client/PluginsSettingsSection.tsx:33-118`；`E0` `PluginsSettingsSection.module.css:23-70`；`E0` `sw/ui-settings-plugins/src/client/index.ts:74-84`。 |
| 13 | 反证 + 验收 | **反证**：若切换页签卸载面板，用户配置到一半去另一个页签看文档再回来，草稿已丢；若页签不实现方向键（只 Tab 逐个走），多页签时键盘用户要按多次才能到相邻页。**验收**：`SET-49` 页签切换后草稿保留（真机）；`SET-50` ←→/Home/End 切换并聚焦（单测）。 |

#### `settings.plugin-inventory.card` —— 插件清单卡（展开详情）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.plugin-inventory.card`。所属：Plugins → 插件清单页 → 预设组/全局面组。出现条件：每个插件行 1 张；失败项排在全局面组最前（`PluginInventorySettingsTab.tsx:295-301`）。覆盖：`:151,154,158,349,398`。 |
| 2 | 网格与定位 | `.cards` 两列网格 `repeat(2,minmax(0,1fr)); gap:10px`（css `:89-96`）；`.card` 纵向、`overflow:hidden`、圆角 xl、卡片描边/填充（`:101-109`）；`.cardContent` 铺满卡（`:124-130`）；标题行 + 描述 + entry id 三行；`.chevron` 在右端。 |
| 3 | 命中区 | 整张卡（cardContent 按钮）可点，面积大；卡内若含跳转按钮（`jump-link`）则是卡按钮内部的嵌套按钮（`<button>` 内嵌 `<button>` 是非法 HTML，见 Q-SET-16）。 |
| 4 | 手势 | 单击 = 展开/收起（一次只开一张，`:259,325-327`）；键盘 Enter/Space（原生 button）。 |
| 5 | 行为 | 展开显示 loader entry、模块名、状态、来源预设、运行时 phase、条件（`:181-204,359-371`）；搜索时强制展开两个分组（`:316-317`）；展开状态是单值（同时只开一张）。 |
| 6 | 视觉状态 | default；hover 背景；展开 `data-open=true`（描边变 l3、描述行数放开、chevron 旋转，css `:111-123,187,434`）；失败 `data-failed=true` 危险描边（`:425-432`）；`aria-expanded`。 |
| 7 | 布局占位与展开形态 | 形态 A：展开的卡在网格中变高，同行另一卡保持自身高度（`align-self:start`，css `:115-122`）；展开把下方行推下。 |
| 8 | 滚动语义 | 归设置 options 列；详情区文本自身不滚。 |
| 9 | 动效 | chevron 旋转（transition）；无高度动画。 |
| 10 | 边界态 | `metadataError` → 卡下 `role=status` 提示；broken 预设组提示 `role=alert`；空清单/空搜索各自文案；client sync 中显示状态行；只读页面（清单只读）。 |
| 11 | 无障碍 | 卡按钮 `aria-expanded + aria-controls` + `aria-label`（标题, entryId, 状态）；`aria-describedby` 指向描述；`<li>` 语义。 |
| 12 | 依据 | `E0` `sw/ui-settings-plugin-inventory/src/client/PluginInventorySettingsTab.tsx:123-178,251-602`；`E0` `PluginInventorySettingsTab.module.css:89-130,425-500`。 |
| 13 | 反证 + 验收 | **反证**：若展开多张卡，两列网格中会形成高低不齐的长列，用户滚动时丢失上下文；若整卡可点而卡内又嵌按钮，点击嵌套按钮会先触发卡片展开（事件冒泡）。**验收**：`SET-51` 同时只展开一张卡（单测）；`SET-52` 卡内跳转按钮点击不展开卡片（真机 + DOM 合法）。 |

#### `settings.plugin-inventory.group-toggle` —— 分组折叠（预设组 / 全局面组）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.plugin-inventory.group-toggle`。所属：清单页两个分组头。出现条件：预设组（有预设时）与全局面组（有 entries 时）各 1 个。覆盖：`:492,495,497,569,572,574`。 |
| 2 | 网格与定位 | `.groupTitleRow` 横向 `min-height:36px; gap:8px`（css `:242-247`）；按钮 `.groupToggle` 含 chevron + 标题（`:258-290`）；`.group + .group` 上边框 + padding-top 14（`:249-252`）。 |
| 3 | 命中区 | 按钮 = 标题文字 + 12px chevron（fit-content，不是整行）；与右侧切换器/计数分开。 |
| 4 | 手势 | 单击 = 折叠/展开（`setPresetOpen(!effectiveOpen)` / `setGlobalOpen(...)`）；键盘 Enter/Space。 |
| 5 | 行为 | 默认：预设组展开、全局面折叠（`presetOpen ?? true`、`globalOpen ?? false`）；**搜索期间强制都展开**（`:316-317`）；折叠不影响卡片 state。 |
| 6 | 视觉状态 | 展开/折叠由 `aria-expanded` + chevron 旋转（css `:277-283`）；hover/focus-visible 共享样式。 |
| 7 | 布局占位与展开形态 | 折叠时该组卡片列表整体收起、后续组上移（形态 A 的折叠）。 |
| 8 | 滚动语义 | 归设置 options 列。 |
| 9 | 动效 | chevron transition；无高度动画。 |
| 10 | 边界态 | 搜索中不可折叠（点击仍会改 state，但 effective 恒展开）；空组不渲染；失败计数徽标。 |
| 11 | 无障碍 | `aria-expanded` + `aria-controls` 指向组面板 id；按钮文本即组名。 |
| 12 | 依据 | `E0` `PluginInventorySettingsTab.tsx:489-597`；`E0` `PluginInventorySettingsTab.module.css:234-296`。 |
| 13 | 反证 + 验收 | **反证**：若搜索时仍保持折叠，用户搜到的结果被藏在折叠组里，看起来「搜不到」。**验收**：`SET-53` 搜索时两组自动展开（单测）；`SET-54` aria-expanded 与视觉一致（DOM 断言）。 |

#### `settings.plugin-inventory.preset-switcher` —— 预设切换器（Menu）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.plugin-inventory.preset-switcher`。所属：预设组标题行右端。出现条件：`presets` 非空（至少 1 个）。覆盖：`:515,519,521`。 |
| 2 | 网格与定位 | `.switcher` `inline-flex; gap:12px; height:36px; padding:0 14px; border-radius:md; 模块底色`（css `:315-331`）；标签 `max-width:240px` ellipsis（`:346-350`）；`.headerEnd{margin-left:auto}` 推到右端（`:254-256`）。 |
| 3 | 命中区 | 36px 高、宽度=标签+chevron+padding；右侧与组标题行同高。 |
| 4 | 手势 | 单击 = 开/关共享 Menu（`aria-haspopup=menu`）；菜单内单击项 = `setChosenPreset(id)`；外点/Esc 关闭（共享 Menu）。 |
| 5 | 行为 | 切换只改「清单页正在看哪个预设」，**不改默认预设、不写配置**（`:508-512`）；选择后组内容与计数切到该预设。 |
| 6 | 视觉状态 | default 模块底；hover 背景；菜单项 selectedId 显示选中；broken 预设的标签带「（不可用）」文案（`presetOptionBroken`，`:116-121`）。 |
| 7 | 布局占位与展开形态 | 按钮在组标题行内固定 36px；菜单浮层（Menu portal）不占位。 |
| 8 | 滚动语义 | 菜单自身可滚（共享 Menu）；页面归 options 列。 |
| 9 | 动效 | 共享 Menu 动效。 |
| 10 | 边界态 | 无预设 → 整组不渲染；无显式选择时用「默认预设或第一个」（`fallbackPreset`，`:110-113`）；broken 预设仍可选（看它为什么坏）。 |
| 11 | 无障碍 | `aria-haspopup=menu` + `aria-expanded` + `aria-label`（`switcherLabel`）；菜单项文字含预设名/状态。 |
| 12 | 依据 | `E0` `PluginInventorySettingsTab.tsx:489-529,279`；`E0` `PluginInventorySettingsTab.module.css:254-256,315-350`。 |
| 13 | 反证 + 验收 | **反证**：若切换器改的是「默认预设」，用户在清单页随便看看就把全局默认改了；若无显式选择时不回落，首屏会空一组。**验收**：`SET-55` 切换预设不写任何配置（Mock 断言）；`SET-56` 默认选中默认预设（真机）。 |

#### `settings.plugin-inventory.jump-link` —— 「在预设中查看」/「其它预设命中」跳转

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.plugin-inventory.jump-link`。所属：全局面卡详情 / 预设组搜索提示。出现条件：① 卡片由某预设启用（`providers!==undefined`）时显示「在预设中查看」；② 搜索命中其它预设时每个预设一个跳转按钮（`:546-560`）。覆盖：`:419,422,550,554`。 |
| 2 | 网格与定位 | `.jumpLink` 文字按钮（次级样式，随 css）；在 `dd`/`p` 内联，`enabledIn` 行内排列（`:416-427`）。 |
| 3 | 命中区 | 文字按钮（命中区=文字宽度，偏小）；与相邻文本间由空格/换行分隔。 |
| 4 | 手势 | 单击 = `setChosenPreset(...)` 切到目标预设（并保持清单页）；无键盘自定义（原生 button）。 |
| 5 | 行为 | 只改查看上下文，不写配置、不导航离开设置。 |
| 6 | 视觉状态 | 链接色/下划线（随 css）；hover/focus-visible 共享样式。 |
| 7 | 布局占位与展开形态 | 内联，不展开。 |
| 8 | 滚动语义 | 归设置 options 列。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 目标预设已选中/消失时仍可点（`setChosenPreset` 幂等）；无匹配时不渲染。 |
| 11 | 无障碍 | 原生 button 文本即标签；在卡片内是嵌套 button（Q-SET-16）。 |
| 12 | 依据 | `E0` `PluginInventorySettingsTab.tsx:408-436,546-560`；`E0` `PluginInventorySettingsTab.module.css`（`.jumpLink`）。 |
| 13 | 反证 + 验收 | **反证**：若跳转顺带把该预设设为默认，用户只是在查清单却改了全局行为。**验收**：`SET-57` 跳转后默认预设置不变（Mock）。 |

#### `settings.plugin-inventory.search` —— 清单搜索

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.plugin-inventory.search`。所属：清单页顶部。出现条件：清单就绪（`snapshot!==undefined`）；1 个。覆盖：`:478`。 |
| 2 | 网格与定位 | `.search` label 内：搜索图标 + 视觉隐藏文案 + input（css `:53-84`）；input 宽度随容器，样式与共享输入接近。 |
| 3 | 命中区 | input 整宽 × 共享输入高（约 32px）；前置图标不可点（装饰）。 |
| 4 | 手势 | 输入 = 本地过滤（不分词，`toLocaleLowerCase().includes`）；无清除按钮（未见实现）；键盘 Tab 进入。 |
| 5 | 行为 | 匹配 moduleName/entryId/title/description（`:103-108`）；匹配时两组强制展开、其它预设命中数提示、无命中显示「无匹配」；清空恢复。 |
| 6 | 视觉状态 | default；focus-visible 共享输入/焦点样式；无匹配文案 `role=status`（`:438-440,487`）。 |
| 7 | 布局占位与展开形态 | 固定在清单顶部（随内容滚动，不吸顶）；无展开。 |
| 8 | 滚动语义 | 归设置 options 列。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 空清单 → 空态文案；无匹配 → 「无匹配」；本地化标题也可搜（例如中文标题命中）。 |
| 11 | 无障碍 | `<label>` 包裹 + 视觉隐藏文本 + `aria-label`；`type=search`。 |
| 12 | 依据 | `E0` `PluginInventorySettingsTab.tsx:474-487`；`E0` `PluginInventorySettingsTab.module.css:53-84`。 |
| 13 | 反证 + 验收 | **反证**：若搜索只匹配技术名（moduleName）不匹配本地化标题，中文用户永远搜不到插件。**验收**：`SET-58` 用中文标题能命中（单测）；`SET-59` 搜索时两组自动展开（真机）。 |

#### `settings.plugin-inventory.retry` —— 清单加载失败 / client sync 失败重试

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.plugin-inventory.retry`。所属：清单页顶部状态区。出现条件：① `state.status==='error'`（Host 清单拉取失败）；② `clientSync.failures.length>0`（客户端模块同步失败）。覆盖：`:451,470`。 |
| 2 | 网格与定位 | 失败条 `.failure` 内：状态行 + 失败列表 + 按钮；`retry` 在错误 `role=alert` 行下方（`:445-472`）。 |
| 3 | 命中区 | 原生 button（文字按钮，命中区偏小）。 |
| 4 | 手势 | 单击 = 重新拉取（`setRequest(v=>v+1)`）/ 重试客户端模块图（`retryClient`）；键盘 Enter/Space。 |
| 5 | 行为 | Host 清单失败：整页进入 loading 再拉一次；client sync 失败：重试客户端图，不改变 Host 组合（`retryClient`，`:321-324,445-452`）。两个失败面独立。 |
| 6 | 视觉状态 | 失败条红字 + StateDot error 图标；重试中按钮 disabled（client sync 用 `clientSync.syncing`）。 |
| 7 | 布局占位与展开形态 | 顶部状态条内联，占位（不是浮层）。 |
| 8 | 滚动语义 | 归设置 options 列。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 加载中显示骨架卡（4 张）而不是错误；同步失败列出每条 failure；两个失败可同时出现。 |
| 11 | 无障碍 | `role="alert"` 状态行；按钮原生语义；骨架 `aria-hidden` + 视觉隐藏 loading 文案（`:454-464`）。 |
| 12 | 依据 | `E0` `PluginInventorySettingsTab.tsx:321-324,438-472`；`E0` `PluginInventorySettingsTab.module.css:89-96,503`。 |
| 13 | 反证 + 验收 | **反证**：若两个失败共用一次重试，重试 Host 清单不会修复客户端模块图（反之亦然），用户会反复失败。**验收**：`SET-60` 两类失败各自可重试且只影响自己（Mock 组合）。 |

### 2.5 子代理页（`settings.subagent.*`）

#### `settings.subagent.limits` —— 委派上限（最大深度 / 最大并发）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.subagent.limits`。所属：Plugins → Subagent 页。出现条件：`subagent` 命名空间被 Host 提供（`SubagentCard.tsx:32-40`），两个数字字段。覆盖实现：`SubagentLimitsFields.tsx:18-57`（扫描无命中行，靠组件源码）。 |
| 2 | 网格与定位 | `.limits` 两列（`.limit` 各一个字段），响应式；字段 label + 32px input + hint；页面共享表单纵向流。 |
| 3 | 命中区 | 两个 input 整格；每个字段右侧有「已覆盖」Tag +「恢复默认」按钮（共享 `SettingsValueField`）；字段旁有 info 帮助按钮（展开表格帮助）。 |
| 4 | 手势 | 输入草稿；单击恢复默认暂存 unset；单击 info 展开帮助；键盘原生。 |
| 5 | 行为 | 编辑只改草稿，点页面「保存」才写；非法数字禁用保存；帮助里给出 0/1 两档深度语义与覆盖规则（`SubagentLimitsFields.tsx:24-46`）。 |
| 6 | 视觉状态 | 同 `settings.form.value-field`（invalid/overridden/disabled/help 展开）。 |
| 7 | 布局占位与展开形态 | 两字段并排；帮助块展开时 inline 推下（`role=region`）。 |
| 8 | 滚动语义 | 归设置 options 列。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 只读/保存中 disabled；非法值提示；namespace 未提供时整个 section 不渲染。 |
| 11 | 无障碍 | label 关联 + `aria-describedby`；帮助按钮 `aria-expanded`；帮助表格有 `aria-label`。 |
| 12 | 依据 | `E0` `sw/ui-settings-subagent/src/client/SubagentCard.tsx:29-52`；`E0` `SubagentLimitsFields.tsx:18-57`；`E0` `subagent-limits-card-controller.ts`；`E0` `prim/settings-form/fields.tsx:49-115`。 |
| 13 | 反证 + 验收 | **反证**：若深度字段允许 0 但不解释语义，用户以为「禁用子代理」实际是别的行为（帮助里必须写清 0/1）。**验收**：`SET-61` 帮助展开显示 0/1 语义（截图）；`SET-62` 非法值禁用保存（单测）。 |

#### `settings.subagent.model-selection` —— 子代理可选模型授权（开关 + 复选框组）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.subagent.model-selection`。所属：Plugins → Subagent 页（模型授权 section）。出现条件：`subagent-model-selection` 命名空间被提供；1 个开关 + 开启后一组 checkbox。覆盖：`:46`（checkbox）、`:87`（retry）；组件 `:22-127`。 |
| 2 | 网格与定位 | `.permission` 纵向 gap 6、padding 12 0；`.toggleRow` 横向 space-between（label 左、Switch 右）；开启后 `.selection` 纵向 gap 10；`.models fieldset` 内按 provider 分组（`.modelGroup` + `.providerName`），每项 `.model` label 行（checkbox + 模型名 + `provider · provider/model` 路由行）；css `SubagentModelSelectionFields.module.css:1-127`。 |
| 3 | 命中区 | Switch 本体；每个 checkbox 行整行 label 可点（约 28px 高）；retry 文字按钮；行间距 10px。 |
| 4 | 手势 | 单击 Switch = 开启/关闭授权（立即写？见 5）；单击 checkbox = 勾选/取消该路由；retry 重拉目录；键盘原生。 |
| 5 | 行为 | 开关 → `toggleEnabled`；勾选 → `toggleModel(key)`；开启后拉模型目录（catalog），加载中/失败/部分成功各有状态；`invalid`（开启但一个都没选）→ 阻止保存并提示；`conflicted`（外部改动）→ `role=status` 提示（`:77-126`）。是否即时写由 `SubagentModelSelectionCardController` 决定（`subagent-model-selection-card-controller.ts`，含 saving 状态与 §4 保存语义）。 |
| 6 | 视觉状态 | Switch 开/关/disabled；行 hover 背景（css `:110`）；不可用候选组单独列出并禁用（`.unavailable` 文案）；加载/失败/部分/空各自提示；冲突提示。 |
| 7 | 布局占位与展开形态 | 开关行固定；开启后选项区 inline 出现，把下方内容推下（形态 A）。 |
| 8 | 滚动语义 | 归设置 options 列；组多时无内滚（未见实现）。 |
| 9 | 动效 | Switch 动画；无展开动画。 |
| 10 | 边界态 | 目录加载失败 → 错误条 + retry；部分 provider 失败 → 部分提示；空目录 → 空提示；只读/saving → 全禁用；未开启时选项区不渲染。 |
| 11 | 无障碍 | Switch 有 label；fieldset + legend「允许的模型」；每项 checkbox label 含模型名与路由；状态提示 `role=status/alert`。 |
| 12 | 依据 | `E0` `sw/ui-settings-subagent/src/client/SubagentModelSelectionFields.tsx:22-128`；`E0` `SubagentModelSelectionFields.module.css:1-127`；`E0` `sw/ui-settings-subagent/src/client/index.ts:59-93`；`E0` `subagent-model-selection-card-controller.ts`。 |
| 13 | 反证 + 验收 | **反证**：若开启开关但允许空选择，子代理将没有任何可调用模型（运行时才报错）；若不可用候选直接隐藏，用户以为模型不存在。**验收**：`SET-63` 开启后不选模型无法保存（单测）；`SET-64` 不可用候选分组可见且禁用（真机）。 |

### 2.6 Agent preset（`agent-preset.*` / `settings.agent-preset.*`）

#### `agent-preset.hero-chip` —— 新会话页预设 chip

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `agent-preset.hero-chip`。所属：聊天 → 新会话 hero 行（`conversation.hero.agentPreset`），紧邻 workspace picker。出现条件：主视图（retainedBy.mainView>0）+ roster 就绪；`options.length===0 || current===''` 时不渲染（`AgentPresetSeat.tsx:116-118,139-141`）。每屏 1 个。覆盖：`:190,194,197`。 |
| 2 | 网格与定位 | `.menuAnchor{min-width:54px; max-width:100%}`；`.seat` `inline-flex; gap:4px; min-height:28px; padding:0 8px; max-width:min(100%,240px); font 13/20 500`（`AgentPresetSeat.module.css:13-35`）；图标 16px + 标签 + chevron 14px；与 workspace chip 同行（hero 行）。 |
| 3 | 命中区 | 整 chip 按钮（≥28px 高，宽随内容，上限 240px）；`min-width:54px` 保证只剩图标时仍可点。 |
| 4 | 手势 | 单击 = 开/关共享 Menu（`aria-haspopup=menu`）；菜单项单击 = `select(id)` 暂存选择；无长按/双击。 |
| 5 | 行为 | 选择**只暂存**（`AgentPresetStage`），等空白会话成为当前会话时应用（`seat-store.ts:86-131,193-247`）；应用失败 → Toast 列出被拒预设与原因（8s）+ 回退显示当前；`busy` 时按钮 disabled；应用成功后 stage 清空，下次新会话回到 Host 默认。 |
| 6 | 视觉状态 | default 透明；hover/展开背景 hover 色；disabled 灰（quaternary 字）；一次性「介绍」动画：图标 ease-in + 名称逐字 fade-up（150ms 延迟/40ms 步进/400ms fade，reduced-motion 直接跳过）（`AgentPresetSeat.tsx:46-68,123-137`）；Toast 失败态。 |
| 7 | 布局占位与展开形态 | chip 参与 hero 行布局、固定 28px 高；菜单是浮层（Menu portal）不占位；Toast 浮层锚定 composer 卡（`:205-218`）。 |
| 8 | 滚动语义 | 不滚（hero 行）。菜单自身可滚。 |
| 9 | 动效 | 打开/关闭菜单瞬时；一次性 introduce 动画见 6；reduced-motion 全关。 |
| 10 | 边界态 | roster 加载失败 → chip 不出现但失败仍会 Toast（`:139` 注释+`error` 状态）；options 为空 → 不渲染；非主视图 → 不渲染；Coding Tools 关闭时过滤 PTC/Minimal 选项并自动关菜单（`index.ts:112-119`、`AgentPresetSeat.tsx:105-112`）；同名预设重复选失败 → 重放 Toast（seq key）。 |
| 11 | 无障碍 | `aria-haspopup=menu` + `aria-expanded`；`title` 显示错误/提示；菜单项文字含名称+描述（`noDescription` 兜底）；disabled 时 title 说明原因。 |
| 12 | 依据 | `E0` `sw/ui-agent-preset/src/client/AgentPresetSeat.tsx:81-221`；`E0` `AgentPresetSeat.module.css:13-57,114-140`；`E0` `seat-store.ts:39-247`；`E0` `index.ts:72-128,183-239`。 |
| 13 | 反证 + 验收 | **反证**：若 chip 在会话开始后仍可切预设，Host 会拒绝（历史已按旧预设产生）——这正是它只放在新会话页的原因；若选择即时应用而没有 stage，空白会话尚未创建时选择会丢失。**验收**：`SET-65` 运行中会话不出现可切换的 preset 控件（真机）；`SET-66` 选预设→发首条消息后会话头标签为该预设（真机）。 |

#### `settings.agent-preset.card` —— 设置页预设卡（设为默认）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.agent-preset.card`。所属：设置 → Agent presets section（`id:'agent-presets', order:20`）→ 内置组/自定义组卡片。出现条件：roster 每项 1 张；`broken` 也渲染（可查看 YAML）。覆盖：`:122,125`。 |
| 2 | 网格与定位 | `.cards` 网格 `repeat(auto-fill,minmax(268px,1fr)); gap:12px`（css `:44-52`）；`.card` 圆角 xl、卡片描边/填充、纵向；`.cardMain` `padding:14px 16px 12px; gap:12px`（`:159-173`）；卡头：名称 + broken 徽标 + Tag（使用中/内置/自定义）；卡脚：帮助按钮 + 查看按钮（`:245-255`）。 |
| 3 | 命中区 | `.cardMain` 大面积可点（整卡上半部）；卡脚按钮独立；`.cardMain` 与卡脚是兄弟（不是嵌套），避免误触。 |
| 4 | 手势 | 单击 cardMain = `makeDefault(id)`（已默认/broken/saving 时 disabled）；键盘 Enter/Space。 |
| 5 | 行为 | 设为默认：写 `agent-preset` 设置命名空间（`writeDefaultPreset`），并尝试把**当前空白会话**同步到该预设（`syncBlankSession`：仅当会话仍空白且 id 未变才 stage+apply，`seat-store.ts:168-184`、`index.ts:242-254`）；Host 拒绝时 Toast（卡内也有 `role=alert` broken 原因）；保存中卡 disabled。 |
| 6 | 视觉状态 | default/hover/`cardActive`（当前默认，背景强调，css `:64-79`）；broken 卡 `cardBroken` + 徽标 + 原因 `role=alert`（`:80-84,129-139`）；Tag tone solid（使用中）/outline（分组）；disabled（已默认保存中/只读）。 |
| 7 | 布局占位与展开形态 | 卡片固定参与网格；不展开（帮助和查看是浮层/弹窗）。 |
| 8 | 滚动语义 | 归设置 options 列。 |
| 9 | 动效 | 卡片 hover border/background transition `.16s`；无高度动画。 |
| 10 | 边界态 | broken 预设：可查看不可设默认；Coding Tools 关闭时内置 PTC/Minimal 卡隐藏（`:106-108`）；自定义组为空时仍保留组标题与 Creator 入口（`:109`）；saving 中禁用全部卡。 |
| 11 | 无障碍 | cardMain `aria-pressed={isDefault}` + `aria-disabled`（broken）+ `aria-label="{设为默认|使用中|不可用}: 名称"`；Tag 文本；broken 原因 `role=alert`。 |
| 12 | 依据 | `E0` `sw/ui-agent-preset/src/client/AgentPresetSection.tsx:64-176`；`E0` `AgentPresetSection.module.css:1-60,64-84,159-200`；`E0` `section-store.ts`；`E0` `settings-store.ts`；`E0` `index.ts:242-274`。 |
| 13 | 反证 + 验收 | **反证**：若 broken 卡也可点「设为默认」，用户会保存一个加载不了的预设，之后每次新会话都失败；若设默认只写配置不同步空白会话，用户看到卡上「使用中」但当前会话仍是旧预设。**验收**：`SET-67` broken 卡不可设默认（单测）；`SET-68` 设默认后当前空白会话头标签同步（真机）。 |

#### `settings.agent-preset.creator` —— Creator 入口（新建自定义预设）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.agent-preset.creator`。所属：Agent presets 设置页 → 自定义组底部。出现条件：有 conversation 流程 + roster 含 `cordis` 预设（`AgentPresetSection.tsx:84-88`），否则整个按钮不渲染。覆盖：`:91,95`。 |
| 2 | 网格与定位 | `.creatorButton` 整行宽、`height:44px`、虚线边框、圆角 lg、居中图标+文案（css `:355-371`）。 |
| 3 | 命中区 | 整行 44px 高的按钮。 |
| 4 | 手势 | 单击 = `startCreatorDraft()` + `closeSettings()`（离开设置、暂存 cordis 预设并开始新会话）；键盘 Enter/Space。 |
| 5 | 行为 | 暂存 `cordis` 预设在「下一个空白会话」上，启动新会话（`index.ts:201-206,230-239`）；不发送任何消息；成功后 hero chip 播一次介绍动画（`introduce`）。saving 时禁用。 |
| 6 | 视觉状态 | default 虚线边；hover 背景；disabled（saving）opacity .4。 |
| 7 | 布局占位与展开形态 | 自定义组末尾固定 44px；点击后面板关闭（导航离开）。 |
| 8 | 滚动语义 | 归设置 options 列。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 无 conversation 流程（如某些嵌入式部署）→ 不渲染；roster 无 cordis → 不渲染；saving disabled。 |
| 11 | 无障碍 | 原生 button + 可见文案「新建插件/预设」；加号图标。 |
| 12 | 依据 | `E0` `AgentPresetSection.tsx:84-100`；`E0` `AgentPresetSection.module.css:355-380`；`E0` `sw/ui-agent-preset/src/client/index.ts:177-206,230-239`；`E0` `seat-store.ts:139-148`。 |
| 13 | 反证 + 验收 | **反证**：若入口不关闭设置面板就开始会话，新会话被设置遮罩挡住、hero chip 的介绍动画也看不见。**验收**：`SET-69` 点击后设置关闭且新会话 hero 显示 cordis 预设（真机）。 |

#### `settings.agent-preset.help` —— 预设帮助（模式说明 / 如何使用）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.agent-preset.help`。所属：预设卡卡脚。出现条件：仅**内置预设**（trust='system'）有 `presetGuide`；自定义预设不渲染（`AgentPresetSection.tsx:142-147`、`PresetGuideDialog.tsx:32-34`）。每卡 2 个按钮。覆盖：`:144,146`。 |
| 2 | 网格与定位 | `.cardHelp` 两个 ghost 按钮（12px 文案）在卡脚左侧，与右侧查看图标按钮分开（css `:245-271`）。 |
| 3 | 命中区 | 文字按钮（ghost，命中区=文字+内边距，偏小）；两按钮间距由 gap。 |
| 4 | 手势 | 单击 = 打开 `PresetGuideDialog`（initialPage='explanation' / 'usage'）；键盘原生。 |
| 5 | 行为 | 只读帮助：打开前先聚焦当前页签（`PresetGuideDialog.tsx:97-99`），Esc/关闭按钮关闭并回到卡（焦点回触发按钮由父级？关闭时未显式回焦，见 Q-SET-16）；**不改变选中的预设**。 |
| 6 | 视觉状态 | ghost 按钮 hover/焦点；打开后是模态（见 guide-dialog）。 |
| 7 | 布局占位与展开形态 | 卡脚内联；帮助内容在模态里（形态 C）。 |
| 8 | 滚动语义 | 帮助弹窗各页独立滚动（`PresetGuideDialog.module.css:62` `overflow-y:auto`）。 |
| 9 | 动效 | 共享 Modal 动效。 |
| 10 | 边界态 | 自定义预设无帮助按钮；未知 id 无帮助（`guides` 白名单 4 个）；帮助文案是本地化 Markdown（用法页按 `###` 分段渲染示例）。 |
| 11 | 无障碍 | 按钮 `aria-label="模式说明: 名称"` / "如何使用: 名称"；弹窗页签 `role=tab`/面板 `role=tabpanel`。 |
| 12 | 依据 | `E0` `AgentPresetSection.tsx:142-153`；`E0` `AgentPresetSection.module.css:245-271`；`E0` `PresetGuideDialog.tsx:19-34,87-160`；`E0` `index.ts:130`（字典）。 |
| 13 | 反证 + 验收 | **反证**：若帮助按钮出现在自定义预设上，会展示内置预设的文案（误导）；若帮助会改变当前选中预设，用户看完说明发现选择被改了。**验收**：`SET-70` 自定义预设无帮助按钮（单测）；`SET-71` 打开/关闭帮助后选中预设不变（真机）。 |

#### `settings.agent-preset.view` —— 只读 YAML composition 查看器

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.agent-preset.view`。所属：预设卡卡脚 → Modal。出现条件：每卡 1 个查看按钮；点开后 `viewed!==null` 出现 Modal。覆盖：查看按钮 `AgentPresetSection.tsx:151,152`；Modal 关闭 `:172`。 |
| 2 | 网格与定位 | 卡脚右侧 `.iconButton`（方形图标按钮，28×28 类，css `:271-330`）；Modal `class=dialog` + `<pre class=viewerCode>` 可滚（`:163-174`、css `:245-350`）。 |
| 3 | 命中区 | 图标按钮（28×28，偏小）；Modal 内 footer 关闭按钮。 |
| 4 | 手势 | 单击查看按钮 = `view(id)` 拉全文并打开 Modal（记录触发按钮用于关闭回焦）；Modal 内：关闭按钮 / Esc / 遮罩；**Tab 被陷阱限制在弹窗内**（`trapPresetReaderTab`，`PresetGuideDialog.tsx:39-53`；`AgentPresetSection.tsx:164-170`）。 |
| 5 | 行为 | 只读展示 composition（YAML 文本），broken 预设也能看（诊断指向它）；关闭回焦触发按钮（`closeViewer` `:79`）；打开期间切换 section 会在卸载时关闭 viewer（`:75-78`）。 |
| 6 | 视觉状态 | 图标按钮 secondary；Modal 标题「查看 · 名称」；代码块 `<pre>` 等宽。 |
| 7 | 布局占位与展开形态 | 形态 C：Modal 浮层；代码块内滚（`viewerCode`）。 |
| 8 | 滚动语义 | `.viewerCode` 内滚（`overflow:auto`，css `:332`）；到边即停。 |
| 9 | 动效 | 共享 Modal。 |
| 10 | 边界态 | 内容加载中/失败（`section-store.view` 状态）；broken 内容仍是原文；超长 YAML 内滚；Esc 优先关查看器（捕获阶段 stopPropagation，防止关设置）。 |
| 11 | 无障碍 | 按钮 `aria-label="查看: 名称"`；Modal title + `autoFocus` 关闭按钮；Tab 陷阱。 |
| 12 | 依据 | `E0` `AgentPresetSection.tsx:148-174`；`E0` `PresetGuideDialog.tsx:36-53`；`E0` `AgentPresetSection.module.css:245-350`；`E0` `section-store.ts`（view）。 |
| 13 | 反证 + 验收 | **反证**：若 Esc 同时关查看器和设置，用户读 YAML 时按 Esc 会被踢出设置；若 Tab 不被陷阱，焦点跑到设置页面后面（视觉上被遮挡）。**验收**：`SET-72` 查看器内 Esc 只关查看器（真机）；`SET-73` Tab 循环留在弹窗内（键盘遍历）。 |

#### `settings.agent-preset.guide-dialog` —— 帮助弹窗（页签 / 焦点陷阱 / 关闭）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.agent-preset.guide-dialog`。所属：预设帮助弹窗（由 `settings.agent-preset.help` 打开）。出现条件：`guide!==null`；1 个。覆盖：`:98,103,113,117,137`。 |
| 2 | 网格与定位 | `Modal headless` + `.guideDialog`；`.guideLayout` 纵向；标题行（标题 + 关闭 18px 图标）；intro；SegmentedTabs（两个页签）；两个 `role=tabpanel` 面板，隐藏的用 `hidden`（`PresetGuideDialog.tsx:111-158`、css `:1-120`）。 |
| 3 | 命中区 | 页签按钮、关闭按钮（18px 图标 + ghost 按钮）、页面内容里的复制按钮等；Tab 陷阱遍历 `button/[tabindex=0]`。 |
| 4 | 手势 | 单击页签切换（SegmentedTabs 自带键盘）；关闭按钮；Esc 关闭；Tab/Shift+Tab 循环（`trapPresetReaderTab`）；遮罩点击？`Modal onClose` 是传入的 `onClose`（是，可关）。 |
| 5 | 行为 | 只读；切换页签不改任何状态；打开后聚焦当前页签（`useLayoutEffect`）；关闭回到预设卡。 |
| 6 | 视觉状态 | 当前页签 selected；面板 hidden 切换；focus-visible 焦点环（`PresetGuideDialog.module.css:71`）。 |
| 7 | 布局占位与展开形态 | 形态 C 浮层；页签面板切换用 `hidden`（不卸载，独立滚动位置保留）。 |
| 8 | 滚动语义 | 每个 `.guidePanel` 独立滚动（`overflow-y:auto`，css `:62`）；到边即停。 |
| 9 | 动效 | 共享 Modal。 |
| 10 | 边界态 | 无帮助的预设不打开；Escape 在捕获阶段 stopPropagation（只关本层）；`hidden` 面板内容不参与焦点；Markdown 内代码块复制按钮可用。 |
| 11 | 无障碍 | Modal title；页签 `role=tab`/`aria-selected`；面板 `role=tabpanel`+`aria-labelledby`；Tab 陷阱；Escape 关闭。 |
| 12 | 依据 | `E0` `PresetGuideDialog.tsx:36-160`；`E0` `PresetGuideDialog.module.css:1-120`。 |
| 13 | 反证 + 验收 | **反证**：若两个页签共享一个滚动容器，切到「如何使用」时滚动位置会停在上一页的位置；若不用 `hidden` 而卸载，滚动位置丢失。**验收**：`SET-74` 两页独立滚动位置（真机）；`SET-75` Tab 不出弹窗（键盘）。 |

#### `agent-preset.plugins-add-item` —— 「添加插件」菜单里的 Creator 项

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `agent-preset.plugins-add-item`。所属：插件管理器 → 「添加插件」菜单（`plugins.add.actions` 槽位）。出现条件：有 conversation 流程时注册（`index.ts:230-239`）；roster 就绪且含 cordis 时可点（`CreatePluginMenuItem.tsx:31`）。 |
| 2 | 网格与定位 | 共享 `MenuItemButton`（图标 14px + 两行文案：标题 + 描述）；菜单项高度随共享 Menu。 |
| 3 | 命中区 | 整菜单项（共享 MenuItemButton）。 |
| 4 | 手势 | 单击 = `onDismiss()` 关闭菜单 + `startCreatorDraft()`；disabled 时不可点。 |
| 5 | 行为 | 启动 Creator 流程（暂存 cordis 预设并开新会话）；描述文案随 roster 状态变化：检查中 / 不可用 / 缺少 cordis 预设（`CreatePluginMenuItem.tsx:34-37`）。 |
| 6 | 视觉状态 | default/hover/disabled（共享 MenuItemButton）；描述行 ellipsis（css `CreatePluginMenuItem.module.css:12`）。 |
| 7 | 布局占位与展开形态 | 菜单项固定行高；不展开。 |
| 8 | 滚动语义 | 归共享 Menu。 |
| 9 | 动效 | 共享 Menu。 |
| 10 | 边界态 | roster idle/loading → 描述「检查中…」且 disabled；error/unavailable → 「不可用」；缺 cordis → 「缺少」；都 disabled 但仍显示（让用户知道为什么没有）。 |
| 11 | 无障碍 | 共享 MenuItemButton 语义；描述文本是可见的说明（不是仅 title）。 |
| 12 | 依据 | `E0` `sw/ui-agent-preset/src/client/CreatePluginMenuItem.tsx:27-50`；`E0` `CreatePluginMenuItem.module.css`；`E0` `sw/ui-agent-preset/src/client/index.ts:230-239`。 |
| 13 | 反证 + 验收 | **反证**：若 roster 未就绪时该项隐藏，用户会以为「没有创建插件功能」；若不可用时点击，进入流程后才失败。**验收**：`SET-76` 三种未就绪态都显示且禁用（单测）；`SET-77` 就绪时点击进入 Creator 新会话（真机）。 |

### 2.7 账户（`settings.account.*`）

> 账户区结构：`settings.section(id:account)` 渲染「未登录引导」或「身份卡 + 余额卡」；侧栏另有账户启动器与菜单；桌面端还有 onboarding 全屏流程、配额通知与内嵌 Platform 页（`sw/ui-settings-account/src/client/index.ts`）。

#### `settings.account.menu` —— 侧栏账户启动器 + 菜单

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.account.menu`。所属：侧栏底部（`settings.launcher` 槽位 owner = 账户插件）。出现条件：始终（未登录显示省略号图标 + More，登录后显示头像 + 名字）。覆盖：`AccountMenu.tsx:64,65`。 |
| 2 | 网格与定位 | `.trigger` `height:44px; width:100%; padding:6px; gap:8px; border-radius:md`；窄栏 `data-collapsed=true` → `36×36`；未登录 `data-signed-out=true` → `height:32px`（`AccountMenu.module.css:1-10`）；头像 24px（`:6`）；标签单行 ellipsis（`:8`）。菜单 `Menu` 向上弹（`side="top"`，`AccountMenu.tsx:63`）。 |
| 3 | 命中区 | 整行按钮 44px 高（窄栏 36×36，<44dp 见 Q-SET-15）；未登录态 32px 高。 |
| 4 | 手势 | 单击 = 开/关菜单；菜单项单击：设置（关菜单→回焦触发按钮→`openSettings()`）、联系我们、登录（`beginSignIn`）、退出（先查运行中任务再开确认）。键盘：共享 Menu 的 ↑↓/Enter/Esc。 |
| 5 | 行为 | 菜单项随登录态：未登录 = 设置/联系我们/登录；已登录 = 设置/联系我们/退出登录（busy 时禁用）。打开设置面板的 false→true 边沿会 `refreshAccount()` 一次（`:28-34`）。退出先 `hasRunningAccountTasks()`，返回 boolean 或 'unknown' 再弹确认（`:49-54`）。 |
| 6 | 视觉状态 | default；hover 背景；菜单展开 `aria-expanded`；`data-collapsed`/`data-signed-out` 两套尺寸；busy 时退出项 disabled；未读/会话过期 Toast 在启动器上方（`:58-59`）；bonus 通知卡（见 notices）。 |
| 7 | 布局占位与展开形态 | 启动器参与侧栏布局流，固定 44px（或 36/32）；菜单/Toast/通知卡都是浮层不占位（G-01）。 |
| 8 | 滚动语义 | 不滚；菜单自身可滚（共享 Menu）。 |
| 9 | 动效 | 菜单共享动效；Toast 自动消失。 |
| 10 | 边界态 | 未登录/已登录两形态；profile 未加载时标签为空（`label=null`，只显示头像/省略号）；会话过期 Toast；模型要求登录 Toast；`loginVisible && !onboarding` 才显示登录弹窗（`:85`）；桌面 Platform 页打开中仍可操作。 |
| 11 | 无障碍 | `aria-haspopup=menu` + `aria-expanded` + `aria-label=menu`；菜单项图标 + 文本；Toast 由共享组件播报。 |
| 12 | 依据 | `E0` `sw/ui-settings-account/src/client/AccountMenu.tsx:22-91`；`E0` `AccountMenu.module.css:1-10`；`E0` `sw/ui-settings-account/src/client/index.ts`（launcher 注册）。 |
| 13 | 反证 + 验收 | **反证**：若退出登录不先检查运行中任务，用户会在有任务跑时把凭据删掉导致任务失败且无提示；若未登录态不显示入口，用户找不到登录/联系我们。**验收**：`SET-78` 有运行任务时退出弹窗提示影响（真机/Mock）；`SET-79` 未登录能看到「登录/联系我们」（截图）。 |

#### `settings.account.signed-out` —— 未登录引导（登录按钮）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.account.signed-out`。所属：账户设置 section。出现条件：`!signedIn && !active`（没有任何登录 attempt 进行中）。覆盖：`AccountSection.tsx:150`（按钮）；容器 `:140-153`。 |
| 2 | 网格与定位 | `.signedOut` 居中、`min-height:calc(100%+16px)`（撑满选项列）；内容 `.signedOutContent` 纵向居中、gap 20；标题 14/22、描述 12/18 居中；按钮 `min-width:72px`（`AccountSection.module.css:150-195`）。 |
| 3 | 命中区 | 主按钮（共享 Button primary，命中区≈按钮尺寸，宽≥72px）。 |
| 4 | 手势 | 单击 = `start()` 创建登录 attempt（桌面打开浏览器授权，客户端显示等待态）；键盘原生。 |
| 5 | 行为 | 点击后按钮 `busy` disabled；成功进入「登录进行中/已登录」；失败保留未登录态并显示「失败」文案（`:136-147`）。幂等（busy 拦截重复点击）。 |
| 6 | 视觉状态 | default；busy disabled；文案失败态变红系？失败文案用 `t('failed')`，描述位（`:145-147`）。focus-visible 共享环。 |
| 7 | 布局占位与展开形态 | 占满内容列高度并居中（无展开）。 |
| 8 | 滚动语义 | 不滚（内容短）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 未登录流失败；账户状态流失败（`streamFailed`）；加载中按钮 disabled；`state===undefined`（尚无快照）按钮 disabled（`:149`）。 |
| 11 | 无障碍 | section `aria-label=nav`；描述 `role=status`；按钮原生语义。 |
| 12 | 依据 | `E0` `AccountSection.tsx:84-153`；`E0` `AccountSection.module.css:150-195`。 |
| 13 | 反证 + 验收 | **反证**：若快照未到时按钮可点，`start()` 会在没有账户状态的情况下发起 attempt（可能重复弹浏览器授权）。**验收**：`SET-80` 快照未到时登录按钮 disabled（单测）；`SET-81` 登录失败有可见提示（Mock）。 |

#### `settings.account.card` —— 已登录身份卡 + 余额卡（含外链动作）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.account.card`。所属：账户 section。出现条件：已登录或登录进行中（`signedIn || active`）。覆盖：`AccountSection.tsx:133,134,164,169,174,207,209`；容器 `:154-214`。 |
| 2 | 网格与定位 | `.section` 纵向 gap 16；`.card`/`.balanceCard` `padding:12px 16px; border-radius:xl; 卡片描边/填充`（css `:11-17`）；身份卡横向 space-between（头像 32px + 名字/状态）；余额卡纵向 gap 8、每行 `.row min-height:40px`（`:65-75`）；`accountInfo` 外链在身份卡底部；余额行右侧金额/链接；底部「更多」行放 usage/topUp 链接（`:202-211`）。 |
| 3 | 命中区 | 每个外链是独立命中区（文字链接，高度≈22px，偏小）；「登录中」状态下「打开授权页」与「取消」是链接+按钮；`accountInfo` 链接带 12px 外链图标。 |
| 4 | 手势 | 单击 usage/topUp 链接：桌面端拦截默认导航改为内嵌 Platform 页（`showPage`），其它端新标签打开；单击「打开授权页」新标签打开带主题参数的授权 URL；单击「取消」取消 attempt；无长按/双击。 |
| 5 | 行为 | 余额：钱包就绪→显示多币种金额；未就绪/失败→显示「余额不可用」并给 Platform 链接（仍可去充值页查）；bonus 钱包>0 才显示 bonus 行；登录中显示 attempt 状态文案（初始化/等待浏览器/完成中/失败/过期）。重复点击被 busy/phase 拦截。 |
| 6 | 视觉状态 | 身份卡 default；登录中按钮区出现；状态行 `role=status`；失败/过期文案；disabled 链接用 `aria-disabled`（无 href 时）；hover 链接下划线（随 css）；`linkButton.primary` 为充值主链接。 |
| 7 | 布局占位与展开形态 | 两张卡内联；bonus 行随数据出现/消失会改变余额卡高度（inline，非动画）。 |
| 8 | 滚动语义 | 归设置 options 列。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 未登录不渲染此卡；余额读取失败仍给 Platform 链接；`details` 未加载显示 loading；bonus 行在读取前显示 loading；登录中 attempt 可能 expired/failed；Platform 页打开时本页仍在（返回后刷新）。 |
| 11 | 无障碍 | 状态 `role=status`；外链 `target=_blank rel=noreferrer`；图标链接带文本；`aria-disabled` 标注未就绪链接。 |
| 12 | 依据 | `E0` `AccountSection.tsx:84-215`；`E0` `AccountSection.module.css:1-200`；`E0` `platform-pages.ts`；`E0` `authorize-url.ts`。 |
| 13 | 反证 + 验收 | **反证**：若余额加载失败只显示「不可用」没有去充值的链接，用户无法自查余额；若桌面端 topUp 仍开新标签而不用内嵌页，用户会在浏览器和应用之间来回切。**验收**：`SET-82` 桌面端 topUp 打开内嵌 Platform 页（真机）；`SET-83` 余额失败时链接可达（截图）。 |

#### `settings.account.sign-in-dialog` —— 登录弹窗（授权 / 复制链接 / 取消 / 改用 API Key）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.account.sign-in-dialog`。所属：账户启动器 → Modal。出现条件：`account.loginVisible && !account.onboarding`。覆盖：`SignInDialog.tsx:67,72,73,82,84`；容器 `:63-88`。 |
| 2 | 网格与定位 | `Modal headless` + `SignInDialog.module.css`：标题行（标题 + 14px 关闭图标）、描述段（内含「复制链接」行内按钮）、底部两个按钮（outline 次 / primary 主）；`dialog` 宽度随共享 Modal。 |
| 3 | 命中区 | 关闭按钮（约 28px）；「复制链接」行内文字按钮；底部两按钮。 |
| 4 | 手势 | 单击关闭 = `dismiss()`（进行中先 `cancel(attempt.id)` 再关）；单击复制 = 写剪贴板（2s 反馈）；单击次按钮 = 进行中取消 / 非进行中「改用 API Key」（→ `showLogin(false)` + 打开 deepseek-official onboarding）；单击主按钮 = `retry()`（若进行中先 cancel 再 start）。 |
| 5 | 行为 | `account.view.status==='credential-stored'` 时自动关闭（`:41`）；phase 决定标题：失败/浏览器等待/超时/初始；错误优先本地失败；`waiting` 时主按钮 spinner + aria-label「等待中」。 |
| 6 | 视觉状态 | 等待旋钮（spinner）；复制反馈文案（copiedLink/copyFailed，2s 后清除）；失败/过期用不同标题与描述；busy/committing 时按钮 disabled；focus-visible 共享环。 |
| 7 | 布局占位与展开形态 | 形态 C：Modal；内容短不滚。 |
| 8 | 滚动语义 | 无内滚（内容短，Modal 自带约束）。 |
| 9 | 动效 | spinner 旋转；共享 Modal。 |
| 10 | 边界态 | attempt 过期（timeoutTitle）；`no-response` 错误码单独文案；剪贴板不可用→copyFailed；无 authorizeUrl 时复制禁用；登录中关闭会取消 attempt；committing 不可关闭/取消。 |
| 11 | 无障碍 | Modal 标题；关闭按钮 `aria-label=close`；等待中主按钮 `aria-label=waiting`；复制按钮内文本即反馈。 |
| 12 | 依据 | `E0` `SignInDialog.tsx:10-89`；`E0` `SignInDialog.module.css`；`E0` `sw/ui-settings-account/src/client/AccountMenu.tsx:85-87`。 |
| 13 | 反证 + 验收 | **反证**：若关闭登录弹窗不取消 attempt，Host 侧会留着一个等待授权的事务，下次打开显示旧 attempt；若复制链接不给反馈，用户不知道复制成功没有。**验收**：`SET-84` 关闭后 attempt 被取消（Mock 断言）；`SET-85` 复制后 2s 内显示「已复制」（真机）。 |

#### `settings.account.sign-out-dialog` —— 退出登录确认

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.account.sign-out-dialog`。所属：账户启动器 → Modal。出现条件：`signedIn && signOutImpact!==undefined`（已查完运行中任务）。覆盖：`:28,36,38`。 |
| 2 | 网格与定位 | `Modal headless`；`.content`（描述）；`.actions` 右下两个按钮（取消 outline / 退出 primary）；沿用 `SignInDialog.module.css`。 |
| 3 | 命中区 | 关闭按钮、取消、退出三个独立命中区（共享按钮尺寸）。 |
| 4 | 手势 | 单击关闭/取消 = `dismiss()`（busy 时忽略）；单击退出 = `confirm()` 调 `signOut()`，成功后关闭；失败显示 `role=alert` 文案。 |
| 5 | 行为 | 描述按影响分三种：有运行中任务 / 未知 / 无（`signOutRunningDescription`/`signOutUnknownDescription`/`signOutDescription`）；退出不可撤销（但确认前可取消）。 |
| 6 | 视觉状态 | busy 时全按钮 disabled；失败红字；focus-visible 共享环。 |
| 7 | 布局占位与展开形态 | 形态 C：Modal。 |
| 8 | 滚动语义 | 无内滚。 |
| 9 | 动效 | 共享 Modal。 |
| 10 | 边界态 | 影响未知（查询失败）；退出失败保留弹窗；busy 防重复。 |
| 11 | 无障碍 | Modal 标题「退出登录」；关闭 `aria-label`；失败 `role=alert`。 |
| 12 | 依据 | `E0` `SignOutDialog.tsx:7-41`；`E0` `AccountMenu.tsx:49-54,88-89`；`E0` `SignInDialog.module.css`。 |
| 13 | 反证 + 验收 | **反证**：若退出不提示「有任务在跑」，用户以为退出只是登出，实际任务会中断且无恢复手段。**验收**：`SET-86` 三种影响文案各自出现（Mock）；`SET-87` 退出失败弹窗不关（Mock）。 |

#### `settings.account.notices` —— bonus 通知卡 / 配额通知（Modal 或 Toast）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.account.notices`。覆盖两类：① `AccountNoticeCard`（bonus 到账通知，侧栏启动器上方，`AccountNotice.tsx`）② `AccountQuotaNotice`（额度用尽时 frame-wide 通知，`shell.quota-notice` 槽位，桌面端可去充值，`AccountQuotaNotice.tsx`）。覆盖：`AccountNotice.tsx:116`；`AccountQuotaNotice.tsx:72,73`。 |
| 2 | 网格与定位 | bonus 卡：body portal 的 `aside.card`，固定定位在启动器上方（`left=max(8,min(rect.left, innerWidth-268))`、`bottom=innerHeight-rect.top+4`），`ResizeObserver/scroll/resize` 跟随（`AccountNotice.tsx:62-82`）；关闭 18px？图标（`AccountNotice.module.css`）。配额：`Modal`（title/description + 取消/去充值）或降级 `Toast`。 |
| 3 | 命中区 | bonus 卡关闭按钮；配额 Modal 两个按钮（或 Toast 自动消失）。 |
| 4 | 手势 | bonus 卡单击关闭 = `onDismiss(orderId)`（视为已读）；配额：取消=dismiss；「去充值」= 先保留通知（keepOpen）再打开 Platform top-up 页，返回后 dismiss（`:73-79`）。 |
| 5 | 行为 | bonus 卡：显示 ≥1 帧后 `onShown` 上报（已读）；过期通知不渲染；关闭=已读。配额通知：签名/未登录/无桌面宿主时降级为普通 Toast；已登录+桌面时 Modal；Platform 页打开期间不挂 Modal（`:61-68`）。两者都不写设置。 |
| 6 | 视觉状态 | bonus 卡 default/关闭按钮 hover；配额 Modal 共享样式；降级 Toast 警告图标。 |
| 7 | 布局占位与展开形态 | 两类都是浮层（G-01 不占位）：bonus 卡 fixed；配额 Modal/Toast 浮层。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | bonus 卡无；Toast/Modal 共享动效；bonus 卡在窗口/侧栏尺寸变化时重新定位（无动画）。 |
| 10 | 边界态 | 通知过期（不渲染/不报告）；文档隐藏（visibilitychange 后再报告）；侧栏收起（重新定位）；账户未就绪（配额通知等待首快照，`AccountQuotaNotice.tsx:58-61`）；登出（释放 claim + dismiss）。 |
| 11 | 无障碍 | bonus 卡 `role=status` + `aria-label` 关闭；配额 Modal 标题/描述 + 两个按钮；降级 Toast 由共享组件播报。 |
| 12 | 依据 | `E0` `AccountNotice.tsx:50-118`；`E0` `AccountNotice.module.css`；`E0` `AccountQuotaNotice.tsx:29-81`；`E0` `sw/ui-settings-account/src/client/index.ts`（quota-notice 注册）。 |
| 13 | 反证 + 验收 | **反证**：若 bonus 卡不跟随侧栏/窗口变化，侧栏收起后卡片会飘在半空或遮住 composer；若配额通知在无桌面宿主时仍显示「去充值」按钮，点了没有页面可打开。**验收**：`SET-88` 侧栏收起后通知卡仍贴启动器（真机）；`SET-89` 无宿主时降级 Toast 无充值按钮（截图）。 |

#### `settings.account.platform-overlay` —— 桌面内嵌 Platform 页（返回 / 重试）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.account.platform-overlay`。所属：桌面端账户 → 全窗口原生页面容器。出现条件：`openPlatformPage` 被调用（top-up/usage）；同时只显示一页。覆盖：`:73（返回）,82（重试）`。 |
| 2 | 网格与定位 | body portal `.overlay` `position:fixed; inset:0;` 全窗口；header（window-drag 区）左侧 red/green 灯占位 + 返回按钮；`.viewport` 余下区域承载原生页（`PlatformOverlay.module.css`）。 |
| 3 | 命中区 | 返回按钮（icon+文字，含 20px gap）；重试按钮（失败态）；其余区域是原生页。 |
| 4 | 手势 | 单击返回 = `onClose()`（销毁原生页并恢复焦点）；单击重试 = `attempt+1` 重新 `bridge.open`；键盘：打开后焦点给返回按钮。 |
| 5 | 行为 | 打开时把 `document.body` 其它子节点置 inert 并记录原焦点，关闭时恢复（`:36-45`）；`ResizeObserver` 同步原生 bounds；失败显示失败文案 + 重试；关闭时 `bridge.close()`。 |
| 6 | 视觉状态 | 加载中 spinner；失败文案 + 重试；`role=status` 状态区。 |
| 7 | 布局占位与展开形态 | 形态 C（全窗口浮层），完全盖住应用、不占布局。 |
| 8 | 滚动语义 | 原生页自己滚动；本容器不滚。 |
| 9 | 动效 | 无（原生页加载）。 |
| 10 | 边界态 | 加载失败可重试；页面切换（page 变化）重新打开并回焦；窗口 teardown 时关闭错误被吞掉（`:64-67`）。 |
| 11 | 无障碍 | `role=dialog aria-modal=true aria-label=返回文案`；返回按钮获得焦点；`role=status` 加载/失败文案；背景 inert。 |
| 12 | 依据 | `E0` `PlatformOverlay.tsx:20-87`；`E0` `PlatformOverlay.module.css`；`E0` `overlay-inert.ts`；`E0` `platform-pages.ts`。 |
| 13 | 反证 + 验收 | **反证**：若不恢复原焦点，关闭内嵌页后键盘用户会丢失位置；若不把背景 inert，Tab 会跑到被全屏盖住的设置页里。**验收**：`SET-90` 关闭后焦点回原控件（键盘遍历）；`SET-91` 内嵌页打开时 Tab 不进入背景（真机）。 |

#### `settings.account.onboarding.step` —— 桌面首次启动引导的步骤内容与主操作

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.account.onboarding.step`。所属：桌面 onboarding 全屏舞台（`OnboardingSurface`）。出现条件：`state.visible && step in {welcome,credit,purpose,process}`；每步只 1 个页面。覆盖：`OnboardingWelcomeStep.tsx:22`；`OnboardingCreditStep.tsx:29,30`；`OnboardingPurposeStep.tsx:34,58`；`OnboardingProcessStep.tsx:25,42,46,47,53`。 |
| 2 | 网格与定位 | 全屏 `page`（`height:100%; min-height:480px; overflow:hidden`，css `:1-10`）；内容 `.content` 绝对居中（top/left 50%，宽 `min(100%-2*page-padding,1200px)`）；purpose/process 卡片：`min-height:187~222px`、`padding:24.5px 32.5px`、圆角 32px（`:143-165,257`）；底部 `.navigation` 固定底部（`bottom:clamp(24px,7.61vh,70px)`，`:226-235`）；步进时标题自动聚焦（`DesktopOnboarding.tsx:55`）。 |
| 3 | 命中区 | 主/次按钮（共享 Button，`page .action` 尺寸随 css）；purpose 卡内 checkbox 覆盖整卡（`.cardCheckbox::after inset:0`，css `:206`）；process 卡整卡是 `button role=radio`。 |
| 4 | 手势 | welcome：单击「开始」→ 下一步；credit：主按钮（已充值→继续 / 未充值→充值）、次按钮（已充值→充值 / 未充值→稍后）；purpose：单击卡片勾选（office/development 可组合），出现「继续」→ 下一屏（office 直接完成）；process：单击卡片选中（单选），出现「进入」→ 完成。键盘：process 卡片 ←→/↑↓ 循环选中并聚焦（`OnboardingProcessStep.tsx:25-35`）；purpose 卡内 checkbox 原生 Space；卡片 focus-visible 描边。 |
| 5 | 行为 | purpose 选择即时持久化（`onSelect → update({purpose})`）；process 同理；步进通过 `update({step})` 写设置并驱动动画（`DesktopOnboarding.tsx:87`）；complete('completed'/'skipped') 写完成状态；每步 40ms 淡出后切步（reduced-motion 直接切）。 |
| 6 | 视觉状态 | 选中卡 `selected`（图标阵列 + 渐变边，css `:53-60,317-328`）；hover 卡片描边/阴影（`:183-184`）；busy 时 `aria-disabled` + cursor wait（`:202`）；步骤切换 100ms pageEnter 淡入（`:9-18`）。 |
| 7 | 布局占位与展开形态 | 全屏固定；选择后出现的「继续/进入」按钮属于 `.primaryAction`，会改变布局（出现时内容块位置略变，未见占位预留）。 |
| 8 | 滚动语义 | `.page { overflow:auto }`（css `:280`）；`OnboardingSurface` 舞台也 `overflow:auto`（`OnboardingSurface.module.css:38-46`），小屏时两层滚动，到边不串联（未见自定义）。 |
| 9 | 动效 | 步间 40ms 淡出→切步→100ms 淡入；卡片图标进入动画；reduced-motion 全关（css `:284-285`）。 |
| 10 | 边界态 | 窗口窄/矮：内容宽随 `clamp` 缩放、页可滚；`busy`（loading/saving/切步中）全部按钮禁用；credit 步：已充值/可充值/不可充值三种按钮组合（`OnboardingCreditStep.tsx:29-30`）；purpose 未选时无「继续」；process 未选时无「进入」。 |
| 11 | 无障碍 | 每步 `<h1 id="desktop-onboarding-title">` 且 `tabIndex=-1` 由代码聚焦；purpose checkbox 有 `aria-label`；process `role=radiogroup` + `role=radio` + `aria-checked`；`aria-disabled` 用于 busy；页面 `aria-busy`。 |
| 12 | 依据 | `E0` `sw/ui-settings-account/src/client/DesktopOnboarding.tsx:28-125`；`E0` `OnboardingWelcomeStep.tsx`；`E0` `OnboardingCreditStep.tsx`；`E0` `OnboardingPurposeStep.tsx`；`E0` `OnboardingProcessStep.tsx`；`E0` `DesktopOnboarding.module.css:1-340`；`E0` `OnboardingSurface.tsx:16-42`。 |
| 13 | 反证 + 验收 | **反证**：若 process 卡片用 Tab 逐个走而不是方向键循环（radiogroup 惯例），键盘用户要按很多次且无法按预期操作；若步骤切换不做淡出直接换内容，用户会看到两帧内容同时存在（重排）。**验收**：`SET-92` process 方向键循环选中并聚焦（单测）；`SET-93` reduced-motion 下无过渡且内容完整（真机）。 |

#### `settings.account.onboarding.nav` —— 首次启动引导的底部导航（返回 / 跳过 / 保存失败重试）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.account.onboarding.nav`。所属：onboarding 舞台底部。出现条件：`step!=='welcome'`（返回/跳过只在非首屏出现）；保存失败条在有 `state.error` 时出现。覆盖：`DesktopOnboarding.tsx:114（重试）,116（返回）,117（跳过）`。 |
| 2 | 网格与定位 | `.navigation` 绝对定位于底部、左右两端对齐、宽 `min(100%-2*page-padding,1200px)`，`pointer-events:none` 容器 + 按钮 `pointer-events:auto`（css `:226-241`）；返回按钮含 17×14 返回图标 + 20px gap；跳过按钮纯文本。 |
| 3 | 命中区 | 两个文字/图标按钮（高度≈22px，命中区偏小）；底部左右两端各一。 |
| 4 | 手势 | 单击返回 = `go(上一步)`（credit→welcome、purpose→credit、process→purpose）；单击跳过 = 打开对应确认弹窗（credit→'credit-skip'，其它→'skip'）；保存失败条「重试」= `retry()`。键盘原生。 |
| 5 | 行为 | 返回只改 step（写设置）；跳过不直接完成，而是先弹确认（`OnboardingConfirmation`）；重试保存。busy 时全禁用。 |
| 6 | 视觉状态 | default tertiary 字色；hover primary；focus-visible 焦点环 + offset 5px（css `:238-240`）；busy disabled。 |
| 7 | 布局占位与展开形态 | 绝对定位不占内容流（不推挤步骤内容），但被视为浮在页面底部。 |
| 8 | 滚动语义 | 不滚（不随内容滚动，固定底部）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | welcome 步无导航；busy 禁用；保存失败出现重试条（`role=alert`）。 |
| 11 | 无障碍 | 原生 button；返回图标 `aria-hidden`；失败条 `role=alert`；焦点顺序：内容控件 → 返回 → 跳过。 |
| 12 | 依据 | `E0` `DesktopOnboarding.tsx:95-99,114-118`；`E0` `DesktopOnboarding.module.css:226-242`。 |
| 13 | 反证 + 验收 | **反证**：若跳过直接完成不确认，用户会永久错过首次配置（且不知道后果）；若导航参与内容流，不同步骤内容高度不同会让返回/跳过按钮上下跳。**验收**：`SET-94` 各步按钮位置固定（截图对比）；`SET-95` 跳过必弹确认、取消后仍在当前步（真机）。 |

#### `settings.account.onboarding.confirmation` —— 跳过/无额度确认弹窗

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `settings.account.onboarding.confirmation`。所属：onboarding 舞台 → Modal。出现条件：`dialog!==null`（`skip` / `credit` / `credit-skip`）。覆盖：`:26,30,31`。 |
| 2 | 网格与定位 | 共享 Modal headless（无模糊背板 `backdropBlur={false}`）+ `.confirmation{padding:22px 24px 0}`；标题行（标题 + 28px 关闭）、描述段、`.confirmationActions` 右对齐 gap 8（`OnboardingConfirmation.tsx:23-34`、css `:243-250`）。 |
| 3 | 命中区 | 关闭按钮 28×28；底部两按钮（共享 Button，圆角 12px）。 |
| 4 | 手势 | 关闭 = onClose；左按钮按 kind 分支（credit-skip→跳过 / credit→继续 / skip→保留设置）；右按钮（去充值 / 直接进入）；键盘原生 + Modal Esc。 |
| 5 | 行为 | 只做确认，不改设置；确认后由 `DesktopOnboarding` 执行 complete/go/recharge（`DesktopOnboarding.tsx:121-123`）。 |
| 6 | 视觉状态 | 共享 Modal/按钮；busy 禁用；`credit && !canRecharge` 时右按钮 disabled（`:31`）。 |
| 7 | 布局占位与展开形态 | 形态 C：Modal。 |
| 8 | 滚动语义 | 无内滚。 |
| 9 | 动效 | 共享 Modal。 |
| 10 | 边界态 | 三种 kind 文案与按钮组合不同；无桌面充值通道时右按钮禁用；busy（保存中）禁用。 |
| 11 | 无障碍 | Modal 标题 + 关闭 aria-label；按钮文本表达后果。 |
| 12 | 依据 | `E0` `OnboardingConfirmation.tsx:6-35`；`E0` `DesktopOnboarding.tsx:121-123`；`E0` `DesktopOnboarding.module.css:243-250`。 |
| 13 | 反证 + 验收 | **反证**：若「跳过」按钮直接用低对比度小字而不确认，用户容易误触永久跳过配置；若无充值通道仍显示「去充值」，点击后无反应。**验收**：`SET-96` 三种 kind 的按钮组合正确（单测）；`SET-97` 无充值通道时右按钮 disabled（截图）。 |

### 2.8 模型面板（`model.*`）

> 结构：输入区工具行的命名座位（`conversation.input.model`）渲染 `ModelSelect`：一个触发胶囊 + 打开后在 body portal 的两级菜单。根菜单两行「模型 / 推理等级」，各钻入自己的列表。数据与提交都走 `ctx.modelDirectories` 的 per-session `ModelDirectory`（与 `/model` 命令弹窗共享同一实例）。菜单没有「确认」按钮：选中即提交并关闭。

#### `model.trigger` —— 输入区模型胶囊（模型名 · 推理等级）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.trigger`。所属：composer 工具行 → 模型座位。出现条件：`available`（非 addressed subagent 会话，`ui-model-selection/src/client/index.ts:175-188`）；会话未就绪时 `available=false` → 不渲染（`ModelSelect.tsx:242`）。每会话 1 个。覆盖：`:448,454,461,459,362`。 |
| 2 | 网格与定位 | `.trigger` `display:flex; align-items:center; gap:4px; height:28px; padding:0 4px 0 8px; border-radius:sm`；`max-width:min(360px,45cqw)`（旧回退 220px）；字号 13/20 400 secondary（`ModelSelect.module.css:9-30`）。图标 16px（窄容器下用图标替代文字，`--dsh-composer-model-icon-display`，`:66-69`）；effort 文案 `flex-shrink:1000`（先丢 effort 再丢模型名，`:56-63`）；chevron 12px（`:78-86`）。 |
| 3 | 命中区 | 胶囊整体（28px 高，宽随内容，上限 360/45cqw）+ 4px 内边距；与相邻的 permission/plan 触发器由工具行 gap 分隔。28px < 44dp（移动端需扩边，见 Q-SET-15）。 |
| 4 | 手势 | 单击 = 开/关菜单（打开时 trigger 先聚焦，`show()` `:244-253`；`open` 时 close(true) 回焦）。键盘：焦点在 trigger 时按 ↑↓/Tab/Enter 由根容器 `onKeyDown` 仲裁（`:297-360`）；外部点击关闭（`:149-159`）；焦点移出 trigger+menu 关闭（`onBlur` `:362-368`）。无长按/双击。 |
| 5 | 行为 | 打开时若当前模型不在目录（`currentChoice===undefined`）直接进模型面板并把焦点交给搜索/首行；已选时显示根两行。选择提交：`select()` → `ModelDirectory.select`（session 级持久投影）；成功关闭并回焦 trigger（无焦点环 `data-selection-focus`）；失败保留 trigger 状态并弹 Toast。busy（pending selection）时 trigger 显示 `StateDot(ongoing)` 替 chevron（`:472-474`）。 |
| 6 | 视觉状态 | default secondary；hover 背景 hover 色；focus-visible 焦点环（选择后通过 `data-selection-focus` 抑制一次）；disabled（`locked`）；busy = spinner 点；chevron 展开时旋转 180°（`:32-86`）。 |
| 7 | 布局占位与展开形态 | 参与工具行布局，固定 28px；菜单是 body portal 浮层不占位（G-01）。窄容器下文本→图标降级不改变高度。 |
| 8 | 滚动语义 | 不滚。菜单自身见 `model.menu.model-row`。 |
| 9 | 动效 | chevron `transform 120ms ease`；`StateDot` 脉冲；无其它。 |
| 10 | 边界态 | 目录加载中显示「正在加载模型…」；当前选择不在目录时保留 `retainedEffort` 文案（`:121-128`）；菜单失败后 trigger 仍显示当前选择；`locked`（如重命名/发送中）disabled；窄行 → 只显示图标（tooltip/title 保留全文）。 |
| 11 | 无障碍 | `aria-label` 三种：加载中/「选择模型」/「选择模型，当前 X，推理等级 Y」；`aria-haspopup=menu`、`aria-expanded`、`aria-controls`、`aria-busy`；`title` 为可见全文。 |
| 12 | 依据 | `E0` `sw/ui-model-selection/src/client/ModelSelect.tsx:64-75,117-142,244-269,297-360,416-475`；`E0` `ModelSelect.module.css:1-86`；`E0` `locales.ts:12-36`；`E0` `sw/ui-model-selection/src/client/index.ts:168-190`。 |
| 13 | 反证 + 验收 | **反证**：若不显示推理等级，用户切了 effort 后在输入区看不到任何当前值，只能再点开确认；若选择后强制显示焦点环，鼠标用户会看到一个「刚点过」的框。**验收**：`SET-98` 切换模型/effort 后胶囊文案同步（真机）；`SET-99` 鼠标选择后无焦点环、键盘选择后有（截图对比）。 |

#### `model.menu.root` —— 根菜单（「模型」行 + 「推理等级」行）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.menu.root`。所属：模型菜单 pane=`root`。出现条件：打开菜单且当前模型在目录；两行：模型行恒有、effort 行仅当模型 `reasoning!==undefined`（`ModelSelect.tsx:490-505`）。覆盖：`:492,498`（两行）、`:441,352`（键盘/回焦）。 |
| 2 | 网格与定位 | 菜单卡（`MenuSurface`）`position:fixed; z-index:1100; width:max-content; min-width:min(240px,100vw-32); max-width:min(420px,100vw-32); max-height:min(360px,100vh-96); padding:4px`（`ModelSelect.module.css:91-117`）；单元格 `.cell` `height:34px; padding:0 8px; gap:6px; border-radius:md`（`:286-304`）；label 左、当前值右对齐 tertiary、12px chevron（`:311-331`）。定位：以 trigger 右缘对齐（`x=rect.right-width`）、位于 trigger 上方 8px，并在视口内 clamp（`ModelSelect.tsx:215-239`）。 |
| 3 | 命中区 | 每行整行 34px 高（宽度=卡宽）；两行上下相邻（padding 4 + 0 gap，直接相连）；chevron 仅为视觉提示（不是独立命中区）。 |
| 4 | 手势 | 单击行 = `drill('model'|'effort')` 切换 pane；根 pane 中 ↑↓ 在行间移动焦点（`moveFocus`），Esc 关闭、Tab 进入/离开（`:297-360`）；菜单外点击关闭（`:149-159`）。 |
| 5 | 行为 | 钻取清空搜索与高亮、记录回焦意图（`paneFocus='drill'`，`:271-276`）；从模型面板返回时焦点交回打开它的单元格（`:192-193`）；从 effort 面板返回同理（`:192`）；再次打开时总是从 root 开始（`close` 重置 pane，`:260-264`）。 |
| 6 | 视觉状态 | 行 default；hover/focus-visible 背景 hover 色；无 selected 态（选中值显示在 `.cellValue`）；禁用态：busy 时行 disabled（`:586` 等），焦点落到 trigger。 |
| 7 | 布局占位与展开形态 | 形态 C：portal 浮层；pane 切换是**同一张卡换内容**，卡高随内容变（root 两行→模型列表），
位置按 `pane/state/query` 重新测量（`:215-239` 依赖）。 |
| 8 | 滚动语义 | 根 pane 不滚（2 行）；卡整体 `overflow:hidden`。 |
| 9 | 动效 | 无切 pane 动画；chevron 旋转见 trigger。 |
| 10 | 边界态 | 模型无 reasoning → 只有 1 行；busy 时行禁用；目录加载失败时不进 root（保持错误条，见 retry）；视口太小时卡 clamp 到视口内。 |
| 11 | 无障碍 | `MenuSurface role='menu'`（root）+ `aria-label=模型与推理等级`；行 `role=menuitem`；`aria-busy` 反映加载/提交中。 |
| 12 | 依据 | `E0` `ModelSelect.tsx:437-505`；`E0` `ModelSelect.module.css:91-117,286-331`；`E0` `locales.ts:22-24`。 |
| 13 | 反证 + 验收 | **反证**：若 effort 行对无 reasoning 的模型也显示，点进去是空列表（用户以为功能坏了）；若 pane 切换不重新测量位置，模型列表变高后会超出视口顶部被裁掉。**验收**：`SET-100` 无 reasoning 模型只有一行（单测）；`SET-101` 打开不同 pane 卡片完整可见（截图）。 |

#### `model.menu.model-row` —— 模型列表行（provider 分组 / 搜索高亮 / 选中 / pending）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.menu.model-row`。所属：模型菜单 pane=`model`。出现条件：目录 groups 非空（按 `orderModelProviders` 排序：DeepSeek 账号 → 官方 → 其它，`provider-order.ts:8-12`）；按 provider 分组显示，组头为 `MenuGroup`。覆盖：`:568,571,575,587`；搜索/分组相关 `:509-537`、`:604-606`；高亮滚入视口 `:204`。 |
| 2 | 网格与定位 | `.groups` 为唯一滚动容器（`min-height:0; overflow-y:auto`，`:216-219`）；行 `.option` `min-height:34px; padding:5px 7px; gap:6px; border-radius:md`（`:221-237`）；模型名 13/18 ellipsis（`:263-271`）；尾部 `.check` 14px（`:273-283`）；组头（`MenuGroup` label）滚动时吸顶（`observeStickyMenuGroups`，`:196-200`）。 |
| 3 | 命中区 | 整行 34px 高（宽度=卡宽）；行内左侧文本区、右侧 check 区；行间无 gap（连续）。 |
| 4 | 手势 | 单击 = `choose({provider, model})`（选中当前值仅关闭、否则提交）；指针移动改共享高亮（`onMouseMove`，`:580-583`）；键盘：搜索模式下 ↑↓ 在搜索框内循环高亮、非搜索模式 ↑↓ 移动焦点；Enter/Tab 接受（`:307-355`）；行自身 `tabIndex` 搜索模式 -1，否则 0（`:574`）。 |
| 5 | 行为 | 提交走 `ModelDirectory.select`：乐观 pending（该行 check 位置显示 `StateDot(ongoing)`，trigger 也显示），Host 接受后关闭；拒绝 → Toast（`session/writer-held` 专门文案）；同值选择只关闭不写。失败保留菜单？——提交时菜单已关闭（`submit` 先 `setSelectionFocus` + trigger focus，`:386-392`），Toast 挂在 composer 卡上。 |
| 6 | 视觉状态 | default；hover/focus-visible/`data-highlighted` 背景 hover 色；selected 行 = 尾部对勾（不是底色，`:245-249`）；pending 行 = 状态点；disabled（busy）；组头吸顶时带背景（`:196-200`）。 |
| 7 | 布局占位与展开形态 | 列表在固定 max-height 卡内**内滚**（形态 B 的变体：capped-inner-scroll）；卡高受 `max-height:min(360px,100vh-96)` 限制。 |
| 8 | 滚动语义 | `.groups` 内滚；组头吸顶；到边即停（未见 overscroll 串联）；高亮变化时 `scrollIntoView(nearest)`（`:202-206`）。 |
| 9 | 动效 | 无行动画；`StateDot` 脉冲。 |
| 10 | 边界态 | 搜索无匹配 → `role=status`「没有匹配的模型」（`:604-606`）；目录为空 →「暂无可用模型」；目录加载中 →「正在刷新模型列表…」（`:538-540`）；加载失败 → 错误条 + Retry（`:541-552`）；部分 provider 失败 → 警告行（`:547-552`）；busy 时行 disabled；超长模型名 ellipsis（`title` 全文）。 |
| 11 | 无障碍 | `role=menu`（列表）+ `role=menuitemradio` + `aria-checked`；`id` 供 `aria-activedescendant`；组头由 `MenuGroup` 提供；搜索框 `role=searchbox` + `aria-controls`。 |
| 12 | 依据 | `E0` `ModelSelect.tsx:93-118,149-239,297-360,480-608`；`E0` `ModelSelect.module.css:216-331`；`E0` `provider-order.ts:1-12`；`E0` `directory.ts:84-128`；`E0` `locales.ts:15-35`。 |
| 13 | 反证 + 验收 | **反证**：若选中值用底色标记，用户会把「当前高亮（即将被 Enter 接受）」误认成「已选中」；若列表不内滚而把卡撑高，模型多时菜单会盖满屏幕且顶部被视口裁掉。**验收**：`SET-102` 选中/高亮/待定三态可区分（截图）；`SET-103` 20 个模型时列表内滚且卡不超出视口（真机）。 |

#### `model.menu.effort-row` —— 推理等级行（含 provider 默认项）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.menu.effort-row`。所属：模型菜单 pane=`effort`。出现条件：当前模型有 `reasoning`；选项 =（若 `defaultEffort===undefined`）首项「Default」+ `reasoning.efforts[]` 逐项（`ModelSelect.tsx:129-140`）。覆盖：`:621,624,629`；错误条 `:615`。 |
| 2 | 网格与定位 | 与模型行同 `.option` 度量（34px 行、13/18 文案、尾部 check）；无分组头；列表同样在卡内（不会很长）。 |
| 3 | 命中区 | 整行 34px。 |
| 4 | 手势 | 单击 = `chooseEffort(effort)`（同值仅关闭）；键盘同模型列表（↑↓/Enter/Tab/Esc/Shift+Tab）。 |
| 5 | 行为 | 提交 `{provider, model, reasoningEffort}`；`effort===undefined` 表示「保留 provider 默认」（提交时省略字段）；同值只关闭；pending 时该行显示状态点（`:635-638`）。 |
| 6 | 视觉状态 | default/hover/focus；selected = 对勾（`effectiveEffort===level.effort`）；pending 行 = 状态点；busy disabled。 |
| 7 | 布局占位与展开形态 | 内滚列表（行数少时不需要滚）；卡高随项数。 |
| 8 | 滚动语义 | 同模型列表的 `.groups` 容器（pane 切换复用）。 |
| 9 | 动效 | 无（状态点脉冲）。 |
| 10 | 边界态 | 模型无 effort → 该 pane 不可达（root 无此行）；`efforts` 为空但有 `defaultEffort` → 只有「Default」；空列表 → 「当前模型未提供推理等级」文案（`:618-619`）；目录失败 → retry；busy disabled。 |
| 11 | 无障碍 | `role=menuitemradio` + `aria-checked`；标签为 effort 本地化名（由 Host catalog 提供）。 |
| 12 | 依据 | `E0` `ModelSelect.tsx:119-140,402-414,610-643`；`E0` `ModelSelect.module.css:221-283`；`E0` `directory.ts:91-128`；`E0` `locales.ts:25,35`。 |
| 13 | 反证 + 验收 | **反证**：若「Default」在模型有默认 effort 时也显示，用户会以为存在一个额外的「不指定」档而其实与当前值重复；若不显示「Default」而模型没有默认，用户无法表达「交给 provider 决定」。**验收**：`SET-104` Default 选项出现条件正确（单测）；`SET-105` 切换 effort 后胶囊与下次打开菜单都反映新值（真机）。 |

#### `model.menu.search-clear` —— 模型搜索（输入 + 清除）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.menu.search-clear`。所属：模型菜单 pane=`model` 顶部。出现条件：`choices.length>4` 才显示搜索（`ModelSelect.tsx:106`）；清除按钮仅 `query!==''`（`:523-536`）。覆盖：`:524,529`。 |
| 2 | 网格与定位 | `.searchRow` `position:relative; margin:2px 0 3px`；输入 `height:auto; padding:5px 7px; border-radius:md`（`:158-175`）；清除按钮 24×24 圆钮在输入右侧 4px（`:177-200`）；查询非空时输入 `padding-right:34px`（`:173-175`）。 |
| 3 | 命中区 | 输入整行；清除按钮 24×24px（<44dp）。 |
| 4 | 手势 | 输入 = 本地过滤（每次击键）；单击清除 = 清空 query + 焦点回搜索框；键盘：↑↓ 移高亮（焦点留在输入框）、Enter/Tab 接受、Esc/Shift+Tab 退出 pane、←→ 原生光标。 |
| 5 | 行为 | 过滤算法：按 provider 分组内做大小写不敏感的「有序子序列」匹配，排序 = 前缀 > 对齐分 > 目录序（`rankByName`，`:108`）；空 query 恢复全量；搜索模式切换时 `useLayoutEffect` 清 query/高亮（`:161-166`）。 |
| 6 | 视觉状态 | default 透明底；focus-within 边框透明（`:202-204`）；placeholder caption 色；清除按钮 hover/focus-visible 背景（`:196-200`）。 |
| 7 | 布局占位与展开形态 | 搜索行在列表上方固定（不随列表滚动）；无匹配时列表隐藏、显示空态（`:559,604`）。 |
| 8 | 滚动语义 | 不滚；列表在其下方内滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 模型 ≤4 个 → 无搜索（`showSearch=false`）；无匹配 → 空态文案；busy 时 `readOnly`（`:520`）；清除时焦点回输入框。 |
| 11 | 无障碍 | `role=searchbox` + `aria-label=搜索模型…` + `aria-controls` 指向列表 + `aria-activedescendant` 指向高亮行；清除按钮 `aria-label=清除搜索`。 |
| 12 | 依据 | `E0` `ModelSelect.tsx:106-115,255-258,307-355,509-537`；`E0` `ModelSelect.module.css:158-214`；`E0` `locales.ts:31-33`。 |
| 13 | 反证 + 验收 | **反证**：若搜索框接管焦点后 ↑↓ 移动的是真实焦点（而不是虚拟高亮），Enter 会先触发行的 click 再提交，行为不可控；若模型少时也显示搜索框，2-3 个模型的菜单里凭空多一行输入。**验收**：`SET-106` ≤4 模型时无搜索框（单测）；`SET-107` 输入时焦点不离开搜索框、Enter 选中高亮（键盘）。 |

#### `model.menu.retry` —— 目录/分组加载失败重试

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `model.menu.retry`。所属：模型菜单 pane=`model` / `effort` 顶部错误条。出现条件：`state.error!==null && lastActionRef==='load'`（整体错误）或 `state.failures` 非空（逐组警告）。覆盖：`:544`（整体）、`:550`（分组）、`:615`（effort pane）。 |
| 2 | 网格与定位 | `.error`/`.warning` 条：`display:flex; justify-content:space-between; gap:6px; padding:6px 7px; border-radius:md; font 11/16`（`:127-145`）；retry 是条内右端文字按钮（`:147-156`）。 |
| 3 | 命中区 | 文字按钮（命中区=文字宽 + 内部 padding 0），较小。 |
| 4 | 手势 | 单击 = `reload()`（`lastActionRef='load'` + `load()`）；键盘原生。 |
| 5 | 行为 | 重跑目录加载；成功后同一菜单内显示列表；失败保留错误条可再点。可分 provider 失败：警告条列出 provider 名与消息，Retry 重跑全部。 |
| 6 | 视觉状态 | 错误条红底红字（hover-danger），警告条模块底 + warn 字色；loading 时条被「正在刷新…」替代。 |
| 7 | 布局占位与展开形态 | 条在列表上方 inline，占位（挤压列表高度）；列表仍在下方滚动。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 选择失败不走这里（走 Toast，`lastActionRef='select'`，`:380-383`）；错误条只在最近一次动作是 load 时显示；分组警告与整体错误可同时存在。 |
| 11 | 无障碍 | 错误文本是普通 span（未见 `role=alert`，见 Q-SET-16）；retry 原生 button。 |
| 12 | 依据 | `E0` `ModelSelect.tsx:77-84,144-147,370-384,538-552,610-617`；`E0` `ModelSelect.module.css:127-156`；`E0` `catalog.ts:47-106`；`E0` `locales.ts:27-31`。 |
| 13 | 反证 + 验收 | **反证**：若选择失败也显示菜单内错误条（而不是 Toast 并关菜单），用户会以为要点菜单里的 Retry 才能撤销一次已提交的选择。**验收**：`SET-108` 选择失败显示 Toast 且菜单已关（单测）；`SET-109` 加载失败条可 Retry 恢复列表（Mock）。 |

### 2.9 触发菜单 / 命令弹窗（`composer.slash.*` / `composer.command.*`）

> 两条链路共用 `conversation.input.overlay` 槽位：`slash-menu`（order 0，combobox，焦点留输入框）与 `command-popup`（order 1，popupSelect，持有焦点）。`/` 与 `@` 由 `inputTriggers` 源注册表分发；`+` 按钮打开的命令菜单也走同一 `command` 源。

#### `composer.slash.menu` —— `/`、`@` 候选菜单（combobox）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.slash.menu`。所属：输入区上方浮层（`conversation.input.overlay`）。出现条件：光标前有活的 `/` 或 `@` token（含引用语法），且该 trigger 至少注册了一个 source（`controller.ts:126-177`、`core/detect.ts:48-75`）；关闭条件：token 消失/全部空组/外点/Esc。覆盖：`:77,83,90,91,135`。 |
| 2 | 网格与定位 | `.menu` `position:absolute; bottom:calc(100%+4px); left:0; right:0; z-index:100; padding:4px; max-height:400px`，运行时用 `useAnchoredMaxHeight` clamp 到 composer 上方空间（TOP_MARGIN 84 = 会话头 76 + 8，`MenuView.tsx:28-35,62`；css `:1-29`）；菜单宽=composer 卡宽。候选行 `.item min-height:34px; padding:6px 8px; gap:6px; font 13/20`（css `:51-66`）；组标题 11px；骨架行 34px。 |
| 3 | 命中区 | 每行整行 34px；行内「钻取箭头」是独立 18×18 命中区（`MenuView.tsx:197-210`）；面包屑按钮在列表上方。 |
| 4 | 手势 | 指针：行 mousedown 选中（`preventDefault` 保持输入框焦点，`:170-173`）、钻取箭头 mousedown 钻取并阻止冒泡、面包屑 mousedown 回退；外点（菜单与 composer 卡之外）关闭（`:81-92`）。键盘：↑↓ 移共享高亮（焦点仍在输入框，`:265-277`）、Esc/Shift+Tab 关闭（`:278-286`）、Enter 选中高亮（pending 时消费不选，`:287-296`）、Tab 钻取或选中（`:297-311`）。 |
| 5 | 行为 | 打开时按 trigger 的 source 名册 seed 待加载组（`core/menu.ts:30-44`）；每 source 独立拉候选，`source-settled` 就位、`source-failed` 移除该组（`:111-129`）；查询细化时**保留旧行与高亮**（stale-while-revalidate），settle 后整体替换（`:96-110`）；全部空组自动关闭（`:77-79,118,126`）；选中通过 source 的 `onPick` 派发（claim/execute/drill/popup），见 `ui-commands/service.ts:252-275`。 |
| 6 | 视觉状态 | 行 default；共享高亮 `.item.active` 背景 hover 色（css `:68-72`）；行内图标 14px menu-icon 色；描述右对齐 tertiary；钻取键帽提示只在高亮行显示（css `:151-173`）；菜单底部渐隐条在「下方还有内容」时出现（`data-overflow-below::after`，css `:33-42`）；pending 组显示 2 行呼吸骨架（css `:204-224`）；无 hover 单独态（与键盘共享高亮）。 |
| 7 | 布局占位与展开形态 | 形态 C：浮层不占位（G-01）；菜单在 composer 上方，卡高被空间 clamp 且列表内滚。 |
| 8 | 滚动语义 | `.viewport` 内滚（`overflow-y:auto`，css `:44-49`）；高亮变化 `scrollIntoView(nearest)`（`MenuView.tsx:74-78`）；到边即停，不把滚轮传给会话（浮层在 composer 上方，未见串联实现）。 |
| 9 | 动效 | 骨架 2s 呼吸；底部 16px 渐隐条；无开合动画。 |
| 10 | 边界态 | 组加载中：骨架（有旧行则不显示骨架）；组失败：静默移除组（console.error）；全部空：自动关闭；无 source：不打开；`frozen` tier（提交中）：不再开菜单、原关闭不重开（`controller.ts:139-143`）；被 Esc 关掉的同一 token 不重开（`dismissedHit`，`:151-157`）；`@` 引用 token 可跨空格（`detect.ts:50-60`）。 |
| 11 | 无障碍 | 滚动容器 `role=listbox` + `aria-label=触发候选建议` + `aria-activedescendant` 指向高亮行 id；行 `role=option` + `aria-selected`；组标题 `role=presentation`；骨架 `role=status aria-label=正在加载…`；面包屑 `aria-current=location`；行 mousedown 保持 combobox 焦点。 |
| 12 | 依据 | `E0` `sw/ui-input-trigger/src/client/MenuView.tsx:24-222`；`E0` `MenuView.module.css:1-274`；`E0` `sw/ui-input-trigger/src/client/controller.ts:126-313`；`E0` `core/detect.ts:20-75`；`E0` `core/menu.ts:30-169`；`E0` `sw/ui-input-trigger/src/client/index.ts:59-86`。 |
| 13 | 反证 + 验收 | **反证**：若菜单抢走输入框焦点（像普通下拉），用户继续打字会中断、Esc 后光标位置丢失；若查询细化时清空列表显示骨架，用户每敲一个字列表就闪一次，无法连续选择。**验收**：`SET-110` 打开菜单后继续打字仍进草稿、↑↓ 只移高亮（真机）；`SET-111` 逐字输入时旧候选保持到新结果到达（录屏）。 |

#### `composer.slash.row` —— 候选行

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.slash.row`。所属：slash 菜单各组。出现条件：组内每个候选 1 行；行内容 = 图标（可选）+ 标题/名字 + 本地化名别名 + 右对齐描述 + 钻取尾部。覆盖：`:161,164`。 |
| 2 | 网格与定位 | `.item` `min-height:34px; padding:6px 8px`；`.itemName max-width:40%` ellipsis；`.itemAlias max-width:20%` 12/18 tertiary；`.itemDescription flex:1` 右对齐 12/18（css `:51-136`）；钻取尾部 `.trailing{margin-left:auto}`。 |
| 3 | 命中区 | 整行 34px；行内钻取箭头 18×18 独立（`MenuView.tsx:197-210`）。 |
| 4 | 手势 | mousedown 选中（阻止默认以保持焦点）；钻取箭头 mousedown 钻取；hover 改共享高亮（`onMouseMove`，`:177`）。键盘见 `composer.slash.menu`。 |
| 5 | 行为 | 选中 → `controller.pick(source,index)` → source 的 settle：command 源按决策表执行（贡献/装饰/claim/裸执行，`ui-commands/service.ts:252-275`）；`@` 引用源插入引用 chip/继续钻取；`drill` 动作下钻目录。选中后菜单关闭（`menuReduce` 由 shell 处理），输入框保留焦点。 |
| 6 | 视觉状态 | 行 default；共享高亮背景；钻取行在高亮时显示 Tab 键帽 + 「进入目录」文案（css `:151-173,191-210`）；分组标题 11px tertiary。 |
| 7 | 布局占位与展开形态 | 行固定 34px；列表内滚。 |
| 8 | 滚动语义 | 归列表内滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 标题与名字仅大小写不同则不显示别名（`MenuView.tsx:187-189`）；描述缺省不占位；图标可为字符串（引用图标）或组件；不可用候选由 source 过滤（不渲染）。 |
| 11 | 无障碍 | `role=option` + `aria-selected` + `id`（供 activedescendant）；钻取箭头 `role=button` + `aria-label`。 |
| 12 | 依据 | `E0` `MenuView.tsx:104-221`；`E0` `MenuView.module.css:51-274`；`E0` `sw/ui-commands/src/client/presentation.ts:66-86`（行面）；`E0` `core/detect.ts`。 |
| 13 | 反证 + 验收 | **反证**：若悬停与键盘高亮是两套（CSS :hover 与 active 同时存在），鼠标停在某行时键盘移动会出现两个高亮。**验收**：`SET-112` 指针与键盘共用一个高亮（单测）；`SET-113` 钻取行按 Tab 下钻而非执行（键盘）。 |

#### `composer.slash.drill` —— 钻取箭头（进入目录）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.slash.drill`。所属：slash 菜单可钻取行尾部。出现条件：候选 `drill===true`（例如 `@` 的目录/文件行）。覆盖：`MenuView.tsx:198`。 |
| 2 | 网格与定位 | `.drill` `18×18; border-radius:xs`，在 `.trailing` 内（键帽提示左侧）；css `:175-190`。 |
| 3 | 命中区 | 18×18px（<44dp）；紧邻键帽提示文字，不重叠。 |
| 4 | 手势 | mousedown（`preventDefault + stopPropagation`）→ `onPick(source,index,'drill')`；Tab（高亮在该行时）也可钻取（`controller.ts:305-307`）。 |
| 5 | 行为 | 只钻取不选中：source 在 `drill` 动作下返回面包屑/子级候选；再按 Tab/Enter 才选中。 |
| 6 | 视觉状态 | default menu-icon；hover 背景 + primary。 |
| 7 | 布局占位与展开形态 | 行内固定 18px。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 非可钻取行不渲染；钻取后行列表更新、面包屑出现。 |
| 11 | 无障碍 | `role=button` + `aria-label=进入目录`（`locales.ts:13`）。 |
| 12 | 依据 | `E0` `MenuView.tsx:191-212`；`E0` `MenuView.module.css:138-190`；`E0` `controller.ts:297-311`。 |
| 13 | 反证 + 验收 | **反证**：若钻取箭头与整行同一个点击动作，用户永远进不了目录（只会选中）；若箭头命中区太小且无键盘路径，触屏用户无法进入目录。**验收**：`SET-114` 点箭头进入目录、点行选中（真机）；`SET-115` Tab 在钻取行下钻（键盘）。 |

#### `composer.slash.crumb` —— 目录面包屑

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.slash.crumb`。所属：slash 菜单组头（钻取源）。出现条件：source 发布了 headers（面包屑 trail），每个 crumb 1 个按钮；当前层 crumb disabled。覆盖：`MenuView.tsx:111`。 |
| 2 | 网格与定位 | `.crumbs` `flex; flex-wrap; gap:2px; padding:3px 3px 5px; border-bottom`（css `:229-240`）；`.crumb` 11/16、`padding:2px 5px`、`max-width:40%` ellipsis（`:243-258`）；分隔符 12px chevron。 |
| 3 | 命中区 | 文字按钮（高度≈16-20px，偏小）；与相邻 crumb 间 2px + 分隔符。 |
| 4 | 手势 | mousedown（保焦点）→ `onCrumb(source,index)`；当前层 disabled；无键盘高亮（crumb 不参与 activedescendant）。 |
| 5 | 行为 | 点击历史层 → source 回到该层并重算候选（`controller.pickCrumb:233-241`）；不影响草稿。 |
| 6 | 视觉状态 | default tertiary；hover 背景 + primary；`aria-current=location` 的当前层 primary、`cursor:default`、disabled。 |
| 7 | 布局占位与展开形态 | 固定在列表上方（不随列表滚动）；换行时占多行（`flex-wrap`）。 |
| 8 | 滚动语义 | 不滚；列表在其下方内滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 无 headers 的 source 不显示；当前层禁用；面包屑很长时每项 40% 宽 ellipsis + 换行。 |
| 11 | 无障碍 | `<nav aria-label=目录导航>`；按钮 `aria-current=location`（当前）。 |
| 12 | 依据 | `E0` `MenuView.tsx:104-128`；`E0` `MenuView.module.css:226-274`；`E0` `controller.ts:233-241`；`E0` `locales.ts:16`。 |
| 13 | 反证 + 验收 | **反证**：若面包屑参与键盘高亮循环，↑↓ 会先穿过一串「路径」按钮才能到候选，键盘操作变慢；若当前层仍可点，点击只是重刷列表，用户误以为能选中。**验收**：`SET-116` 面包屑不进入 ↑↓ 循环（键盘）；`SET-117` 点上一级回到父目录列表（真机）。 |

#### `composer.command.popup` —— popupSelect 命令弹窗（`/命令`、`/model`、`+` 菜单）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.command.popup`。所属：输入区上方浮层（`conversation.input.overlay`，id `command-popup`）。出现条件：命令的 client face 是 `popupSelect` 且由菜单/裸 Enter/`+` 按钮触发（`ui-commands/src/client/popup.ts:171-178`）；同一时刻一个会话一个弹窗。覆盖：`:77,81,82,97,161,163`。 |
| 2 | 网格与定位 | `.card` `position:absolute; bottom:calc(100%+4px); left:0; right:0; z-index:100; padding:3px; max-height:320px`（运行时 `useAnchoredMaxHeight` clamp 到 composer 上方空间，`MAX_HEIGHT=320`，css `:1-31`）；搜索框在卡顶；列表 `.viewport` 内滚；行 `.row` 12px 字、`padding:5px 7px`、圆角 md（`:40-53`）。 |
| 3 | 命中区 | 每行整行（约 26-28px 高）；搜索框整行；错误条 retry 文字按钮；确认弹窗按钮见 `composer.command.confirmation`。 |
| 4 | 手势 | **持有焦点**：打开后搜索输入框获得焦点（`PopupSelectView.tsx:85-88`）；键入本地过滤；单击行 `popup.select(index)`；外点（capture pointerdown）立即 dismiss（`:70-83`）；无长按/双击。 |
| 5 | 行为 | 打开时一次性拉选项（`options()`，abortable）；过滤纯本地；提交单飞（`submitting`）：成功消费 token（CAS，菜单路径按 span、Enter 路径按 bare-token）、关闭、回焦 composer；失败保留弹窗 + 搜索 + token + 错误文案，再点即重试（`popup.ts:243-309`）。高风险选项先经确认弹窗（`:259-262,283-289`）。 |
| 6 | 视觉状态 | 行 default/hover(`rowActive`)/selected（`aria-selected` 高亮）；`active` 行有 check（`active:true` 的业务值）；pending 显示「正在加载选项…」；submitting 显示「正在应用…」；失败红色错误条 + retry；空态按「无选项/无匹配」区分（`PopupSelectView.tsx:173-192`）。 |
| 7 | 布局占位与展开形态 | 形态 C：浮层；列表内滚；确认弹窗是更高一层 Modal。 |
| 8 | 滚动语义 | `.viewport` 内滚；高亮变化 `scrollIntoView(nearest)`（`:65-68`）；到边即停。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 无选项 → 空态；加载失败 → 错误条 + retry；搜索无匹配；`/model` 对 addressed subagent 会话抛错（`ui-model-selection/index.ts:133-156`）；提交失败保留状态；`submitting` 时搜索只读、选择 no-op；`acknowledged` 未勾选时确认按钮无效。 |
| 11 | 无障碍 | `MenuSurface aria-label='/命令 选项'`；搜索框 `aria-label=筛选选项`；`role=listbox` + `/命令 匹配项`；行 `role=option` + `aria-selected`；错误 `role=alert`；确认弹窗独立 Modal 语义。 |
| 12 | 依据 | `E0` `sw/ui-commands/src/client/PopupSelectView.tsx:42-212`；`E0` `PopupSelectView.module.css:1-143`；`E0` `popup.ts:58-331`；`E0` `service.ts:223-249`（候选合成）；`E0` `locales.ts:30-37`；`E0` `sw/ui-model-selection/src/client/index.ts:124-166`（/model 贡献）。 |
| 13 | 反证 + 验收 | **反证**：若弹窗不持焦点（像 slash 菜单），用户输入搜索文字会进草稿；若提交失败就关弹窗，用户需要重打 `/命令` 才能重试，且草稿里的 token 已被消费丢失。**验收**：`SET-118` 打开后键入只进搜索框不进草稿（真机）；`SET-119` 提交失败弹窗保留、token 未被消费、可再试（Mock）。 |

#### `composer.command.row` —— 命令弹窗选项行

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.command.row`。所属：popupSelect 列表。出现条件：每个 `SelectOption` 1 行（可带分组、badge、detail、active、confirmation）。覆盖：`:137,141`；滚动 `:67`。 |
| 2 | 网格与定位 | `.row` `padding:5px 7px; gap:6px; border-radius:md; font 12`；`.label` baseline gap 4；badge 上标 8px；`.detail` 11px 右对齐 ellipsis；`.check` 右侧 14px（css `:40-101`）；分组头 `MenuGroup`（`option-groups.ts`）。 |
| 3 | 命中区 | 整行（约 26px 高）；行间距 0。 |
| 4 | 手势 | 单击 = `popup.select(index)`；hover = `popup.highlight(index)`；键盘由弹窗统一处理。 |
| 5 | 行为 | 选中即向 open 时的 spec `onSelect(option, context)` 提交；若 option 带 `confirmation` 则先进入风险确认；失败保留弹窗。 |
| 6 | 视觉状态 | default/hover（rowActive）/selected（aria-selected 0-based 索引 = 高亮）；badge/detail；`active` 行尾部对勾；`confirmation` 行无特殊视觉（点击后才弹确认）。 |
| 7 | 布局占位与展开形态 | 固定行高，列表内滚。 |
| 8 | 滚动语义 | 归列表内滚；激活行自动滚入视口。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | badge 上标不撑行高；detail 超长 ellipsis；失败行（如 `/model` 的 provider 加载失败行）不可选——由业务在 onSelect 里抛错（`ui-model-selection/index.ts:68-74`）。 |
| 11 | 无障碍 | `role=option` + `aria-selected`；badge 拼进 aria-label；`/model` 的 active 行是「当前值」（打开时高亮停在这里，`popup.ts:117-130`）。 |
| 12 | 依据 | `E0` `PopupSelectView.tsx:134-152`；`E0` `PopupSelectView.module.css:40-101`；`E0` `contract.ts:29-47`；`E0` `popup.ts:102-115,217-241`。 |
| 13 | 反证 + 验收 | **反证**：若不把打开时高亮停在「当前值」上，用户盲按 Enter 会从第一项开始执行（例如把模型改成列表第一个而不是保持当前）；若失败行可选，用户会选中一个没有数据的 provider 行。**验收**：`SET-120` 打开时高亮=当前值（单测）；`SET-121` 失败行选择报错且弹窗保留（Mock）。 |

#### `composer.command.retry` —— 命令弹窗加载失败重试

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.command.retry`。所属：popupSelect 错误条。出现条件：`state.status==='failed'`；1 个。覆盖：`PopupSelectView.tsx:177`。 |
| 2 | 网格与定位 | 错误条 `.error` 横向 `gap:6px`，retry 在右（css `:120-143`）；按钮 `padding:2px 7px; border:0.5px; font 11`。 |
| 3 | 命中区 | 文字按钮（偏小）。 |
| 4 | 手势 | 单击 = `popup.retry()`（保留 search，重跑 options fetch，复用同一 AbortSignal 绑定）；键盘原生。 |
| 5 | 行为 | 失败原因写进 `error` 文案（`errorText`）；重试期间 status=pending（显示 loading），成功后 ready。幂等（非 failed 时 no-op，`popup.ts:196-203`）。 |
| 6 | 视觉状态 | 错误条红字；retry 有边框。 |
| 7 | 布局占位与展开形态 | 条在搜索框下、列表上方，inline 占位。 |
| 8 | 滚动语义 | 不滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 重试保留搜索词；关闭再开是新的 fetch；失败可反复重试；提交失败（onSelect 抛错）不显示 retry 而是同一错误条（status 仍 ready），再点行即重试。 |
| 11 | 无障碍 | 错误条 `role=alert`；retry 原生 button。 |
| 12 | 依据 | `E0` `PopupSelectView.tsx:173-183`；`E0` `PopupSelectView.module.css:120-143`；`E0` `popup.ts:180-203`。 |
| 13 | 反证 + 验收 | **反证**：若重试清空搜索词，用户要重新输入一次过滤条件；若失败不能重试只能关闭重开，草稿 token 会被消费。**验收**：`SET-122` 失败重试保留搜索词（单测）；`SET-123` 重试成功后可选（Mock）。 |

#### `composer.command.launcher` —— `+` 按钮（打开命令菜单）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.command.launcher`。所属：composer 工具行最左（**实现不在本审计包**：`ui-conversation`，此处登记归位与证据）。出现条件：`sessionId!==undefined` 且命令菜单可用；`locked`/`toggleCommandMenu===undefined` 时 disabled。覆盖：`sw/ui-conversation/src/client/skeleton/InputBar.tsx:441-453,294-302`。 |
| 2 | 网格与定位 | 工具行 `.tools` 内按钮 `.add`（约 28×28，图标 `IconPlusOutlineMedium` 14px）；与后续 `.modes` 区同排（`InputBar.tsx:439-470`）。 |
| 3 | 命中区 | 约 28×28px（<44dp，见 Q-SET-15）。 |
| 4 | 手势 | 单击 = 先把焦点还给编辑器（`focusDraftEditor`）再 `toggleCommandMenu(keyboard.caretSpan())`（`:294-302`）；mousedown `keepFocus` 防止焦点被按钮拿走（`:449`）。 |
| 5 | 行为 | 通过 `InputTriggerController.toggleSource('command', hit)` 打开「只有 command 一个 source」的 slash 菜单（`ui-input-trigger/src/client/controller.ts:187-205`）；再次点击关闭；菜单与 `/` 输入共用候选与 pick 管线。 |
| 6 | 视觉状态 | default/hover/disabled（`locked` 或菜单不可用）；`aria-expanded` 反映菜单开合。 |
| 7 | 布局占位与展开形态 | 工具行固定；菜单为浮层。 |
| 8 | 滚动语义 | 菜单内滚（见 slash.menu）。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | locked 禁用；无 command source 时点击无菜单（`toggleCommandMenu` 空）；菜单打开后再次点击关闭；没有命令权限时菜单空 → 自动关闭。 |
| 11 | 无障碍 | `aria-label=指令`（`input.commands`）+ `aria-haspopup=listbox` + `aria-expanded`；Tooltip 文案同 aria-label。 |
| 12 | 依据 | `E0` `sw/ui-conversation/src/client/skeleton/InputBar.tsx:294-302,439-454`；`E0` `sw/ui-input-trigger/src/client/controller.ts:187-205`；`E0` `sw/ui-commands/src/client/locales.ts:10-21`（两个 section）。 |
| 13 | 反证 + 验收 | **反证**：若点击后焦点留在按钮上，菜单的键盘仲裁拿不到按键（必须先把焦点交给编辑器）；若 `+` 与 `/` 是两套菜单，用户会看到两套候选与两种键位。**验收**：`SET-124` 点 `+` 后按键能操作菜单（真机）；`SET-125` `+` 菜单与 `/` 菜单候选一致（对比截图）。 |

#### `composer.command.confirmation` —— 高风险命令确认弹窗（RiskConfirmation）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.command.confirmation`。所属：popupSelect 之上的一层确认。出现条件：被选中的 `SelectOption.confirmation!==undefined` 且尚未确认（`popup.ts:259-262`）。覆盖实现：`PopupSelectView.tsx:195-209`（共享 `RiskConfirmation` primitive）。 |
| 2 | 网格与定位 | 共享 `RiskConfirmation`（Modal 语义）：title/description/acknowledge 复选框/取消/确认/关闭；由 primitive 决定尺寸。 |
| 3 | 命中区 | 确认按钮（未勾选 acknowledgement 时禁用）、取消、关闭；复选框整行。 |
| 4 | 手势 | 勾选 acknowledgement → 确认；取消只退出确认、回到**仍打开**的选项弹窗（搜索/高亮/草稿 token 都保留，`popup.ts:276-281`）；关闭同取消。 |
| 5 | 行为 | 确认后 `settle`（与普通选中同一提交路径）；失败保留选项弹窗并显示错误；确认期间弹窗 `confirming` 隐藏命令卡（`PopupSelectView.tsx:155,195-209`）。 |
| 6 | 视觉状态 | 复选框未勾选 → 确认 disabled；busy/submitting；错误条在命令弹窗上。 |
| 7 | 布局占位与展开形态 | 形态 C：Modal 浮层，盖在命令弹窗上。 |
| 8 | 滚动语义 | 弹窗内容滚动由 primitive 处理。 |
| 9 | 动效 | 共享 Modal。 |
| 10 | 边界态 | 取消不消费 token、不关命令弹窗；确认失败保留；确认中不可关闭命令弹窗（`confirming!==null` 时外点监听被移除，`:76`）。 |
| 11 | 无障碍 | 共享 RiskConfirmation 语义（title/description/acknowledge 复选框/按钮）；确认按钮的 disabled 语义由原生 button 表达。 |
| 12 | 依据 | `E0` `sw/ui-commands/src/client/PopupSelectView.tsx:195-209`；`E0` `popup.ts:243-309`；`E0` `contract.ts:11-18`；`E1` `RiskConfirmation` 实现（`prim`）。 |
| 13 | 反证 + 验收 | **反证**：若取消确认会连带关闭命令弹窗，用户误点了高风险项后要重新输入 `/命令`；若未勾选 acknowledgement 也能确认，风险确认形同虚设。**验收**：`SET-147` 取消后命令弹窗仍在且可换个选项（真机）；`SET-148` 未勾选时确认不可点（真机）。 |

### 2.10 附件与历史消息图片（`composer.attach.*` / `chat.msg.image`）

> 结构：composer 卡内的 `conversation.input.attachments` 槽位渲染 `ComposerAttachments`：如有草稿附件 → 横向 `AttachmentRail`（图片缩略图 / 文件卡）；拖拽文件悬停时叠加全屏 `DropOverlay`；点击图片缩略图 → 共享 `ImageLightbox`。文档级拖放监听由 `installDocumentDropEvents` 安装。
> 来源三路（对话框 / 拖放 / 粘贴）都汇入 `intakeFiles`，intake 约束与上传状态机见 §6。

#### `composer.attach.rail` —— 草稿附件横向轨

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.attach.rail`。所属：composer 卡内附件区。出现条件：`attachments.length>0`（图片或文件任意）；1 条轨。覆盖：`AttachmentRail.tsx:109,115,122,125,153`；容器 `ComposerAttachments.tsx:49-97`。 |
| 2 | 网格与定位 | `.rail`（外层）`padding:2px 10px 0; margin-bottom:-6px`（`ComposerAttachments.module.css:1-7`）；内部 `.rail` `display:flex; align-items:stretch; gap:10px; overflow-x:auto; overflow-y:hidden; scrollbar-width:none`（`AttachmentRail.module.css:6-23`）；每项固定 `height:64px`（图片 64×64，文件卡 240×64）。 |
| 3 | 命中区 | 轨本身是滚动容器；每项内部控件各自可点（缩略图/移除/重试）；项间 10px。 |
| 4 | 手势 | **横向滚动**：滚轮竖直分量被消费并转成横向 `scrollBy`（`deltaMode` LINE 归一到 16px，单步 clamp 60px；`passive:false` 手动监听，`:100-127`）；斜向触控板保留横向意图；纯横向手势走原生。到边后**不把滚轮传给外层**（preventDefault 已消费）。触屏：原生横向 pan（未见 pointer capture 自定义）。 |
| 5 | 行为 | 新增附件时（数量增长）自动把轨滚到末端露出新项；本身挂载在已有草稿上时保持原起点（`countRef` null 语义，`:61-85`）；左右可翻页箭头只在对应方向有溢出时出现（`:66-75,138-168`）。 |
| 6 | 视觉状态 | 滚动条隐藏（`scrollbar-width:none` + `::-webkit-scrollbar{display:none}`，css `:12-23`）；溢出边缘用 24px 圆形箭头指示（`:85-115`）；无 hover 态（轨本身）。 |
| 7 | 布局占位与展开形态 | 参与 composer 卡布局流，固定 64px 高；不展开；内容超宽时横向滚动（不是换行）。 |
| 8 | 滚动语义 | 内滚容器 = 轨（横向）；到边即停、不串联外层（preventDefault）；锚定：新增项滚到末端；未做吸附。 |
| 9 | 动效 | 翻页 `behavior:smooth`（reduced-motion 时 `auto`，`:32-37,128-135`）；滚轮步进 `auto`（无惯性动画）。 |
| 10 | 边界态 | 0 项 → 整个轨不渲染；ResizeObserver 不可用（jsdom）→ 仅首帧算一次；窗口/侧栏宽度变化 → 重新算边缘箭头（`:86-99`）；reduced-motion；粗指针（无 hover）。 |
| 11 | 无障碍 | 轨 `role=group` + `aria-label=待发送附件`；箭头 `aria-label`；项由 owner 渲染（见各节点）。 |
| 12 | 依据 | `E0` `sw/ui-attachment/src/AttachmentRail.tsx:27-171`；`E0` `AttachmentRail.module.css:1-115`；`E0` `ComposerAttachments.module.css:1-7`；`E0` `ComposerAttachments.tsx:36-97`。 |
| 13 | 反证 + 验收 | **反证**：若竖直滚轮不被消费，鼠标在附件轨上滚动会滚动身后的会话，用户想横向找附件却把对话翻走；若新增附件不自动滚到末端，用户看不到刚加的那张（以为没加上）。**验收**：`SET-126` 轨上滚轮只横向滚、外层会话不动（真机）；`SET-127` 新增附件后轨滚到末端（真机）。 |

#### `composer.attach.rail-arrow` —— 轨翻页箭头（左/右）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.attach.rail-arrow`。所属：轨左右两端。出现条件：`scrollLeft>1` 显示左箭头；未到右端显示右箭头（1px 容差，`:66-75`）。覆盖：`:139,143,160,164`；翻页实现 `:128-135`。 |
| 2 | 网格与定位 | `.arrow` `position:absolute; top:50%; 24×24; border-radius:999px; z-index:2`，左 `left:4px` / 右 `right:4px`，`transform:translateY(-50%)`（css `:85-115`）。 |
| 3 | 命中区 | 24×24px（<44dp）；浮在轨两边、覆盖部分第一/最后一项。 |
| 4 | 手势 | 单击 = `page(±1)`：`scrollBy({left: ±max(clientWidth-64,200), behavior:smooth})`；无长按连翻（未见实现）。 |
| 5 | 行为 | 一次翻一屏减 64px（保留一张作为上下文），窄轨至少 200px；仅视觉滚动，不改附件数据。幂等。 |
| 6 | 视觉状态 | default 输入面色 + 面板阴影；hover hover-solid 背景；focus-visible 共享环（未见自定义）。 |
| 7 | 布局占位与展开形态 | 绝对定位不占位、覆盖在轨上；随边缘状态出现/消失（瞬时）。 |
| 8 | 滚动语义 | 触发轨滚动（smooth）；到边后箭头消失。 |
| 9 | 动效 | 翻页平滑滚动（reduced-motion → auto）。 |
| 10 | 边界态 | 内容未溢出 → 两箭头都不出现；reduced-motion；粗指针（hover 不可见，但箭头仍可点）。 |
| 11 | 无障碍 | `aria-label=向左/右翻页`；图标箭头。 |
| 12 | 依据 | `E0` `AttachmentRail.tsx:66-75,128-168`；`E0` `AttachmentRail.module.css:85-115`；`E0` `locales.ts:73-79`。 |
| 13 | 反证 + 验收 | **反证**：若无边缘箭头且滚动条隐藏，用户不知道轨还能横向滚（尤其没有触控板时）。**验收**：`SET-128` 溢出时箭头出现、到端消失（截图）；`SET-129` 点击翻页保留一张上下文（录屏）。 |

#### `composer.attach.image-thumb` —— 草稿图片缩略图（点开原图）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.attach.image-thumb`。所属：轨内 `attachment.kind==='image'` 项。出现条件：草稿里有图片附件。覆盖：`ComposerAttachments.tsx:76,80`；容器 `:74-93`。 |
| 2 | 网格与定位 | `.imageItem` `64×64; position:relative`；`.thumbnail` `64×64; padding:0; overflow:hidden; border:0.5px; border-radius:xl; background:interactive-bg-hover; cursor:zoom-in`；`img` 100% `object-fit:cover`（`ComposerAttachments.module.css:9-31`）。 |
| 3 | 命中区 | 64×64 缩略图按钮；右上角移除按钮 18×18 与其重叠（z-index 1）。 |
| 4 | 手势 | 单击缩略图 = `setPreview(attachment)` 打开 `ImageLightbox` 原图预览；无长按/双击。 |
| 5 | 行为 | 预览在附件被移除后自动关闭（`useEffect` 检查 preview 是否还在 attachments 里，`:28-30`）；预览是本地上传前 URL（`previewUrl`），不请求 Host。 |
| 6 | 视觉状态 | default；hover 由移除按钮逐步显示体现（缩略图本身无 hover 样式）；粗指针移除按钮常显；focus-visible 由共享/浏览器环。 |
| 7 | 布局占位与展开形态 | 固定 64×64 参与轨布局；灯光箱形态 C 浮层。 |
| 8 | 滚动语义 | 随轨横向滚。 |
| 9 | 动效 | 移除按钮 opacity 0.2s（reduced-motion 关闭，css `:49-67`）。 |
| 10 | 边界态 | 未命名文件 alt 用「待发送图片」；图片加载失败无单独失败态（浏览器默认裂图，未见实现）；预览中附件被移除 → 灯光箱自动关。 |
| 11 | 无障碍 | `<button title=打开原图>` + `img alt=文件名或待发送图片`；灯光箱由共享组件提供语义。 |
| 12 | 依据 | `E0` `ComposerAttachments.tsx:24-30,74-93,98-105`；`E0` `ComposerAttachments.module.css:9-31`；`E0` `labels.ts:13-31`。 |
| 13 | 反证 + 验收 | **反证**：若缩略图不能点开原图，用户无法确认自己选的是哪张（64×64 裁切后近似图很难分辨）。**验收**：`SET-130` 点缩略图打开原图、Esc/关闭后回轨（真机）。 |

#### `composer.attach.remove` —— 草稿附件移除按钮（图片 / 文件各一）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.attach.remove`。所属：图片项右上角（`ComposerAttachments.tsx:84,88`）与文件卡右上角（`FileCard.tsx:63,67`，归 `composer.attach.file-remove`）。出现条件：每个附件 1 个。 |
| 2 | 网格与定位 | 图片：`.remove` `18×18; top:4; right:4; border-radius:50%; 对比色底; opacity:0→hover/focus/coarse 1`（`ComposerAttachments.module.css:33-67`）；文件卡：`18×18; top:6; right:6`（`FileCard.module.css:84-130`，失败态常显且红底）。 |
| 3 | 命中区 | 18×18px（<44dp），覆盖在缩略图右上角；与缩略图按钮重叠但 z-index 更高。 |
| 4 | 手势 | 单击 = `onRemoveAttachment(id)`：从草稿移除（图片还释放本地预览 URL，由上层负责）；无长按/双击；无二次确认。 |
| 5 | 行为 | 移除后附件立即从轨消失；若该附件正在上传，上传结果被丢弃（composer 状态里不再有该 id）；不可撤销（需重新选文件）。幂等（id 已不存在则无操作）。 |
| 6 | 视觉状态 | 图片：默认透明、hover/focus/粗指针显示；文件：默认透明、hover 显示，失败态**常显**且红底白叉（css `:126-130`）；focus-visible 显示。 |
| 7 | 布局占位与展开形态 | 绝对定位覆盖在项上，不占布局。 |
| 8 | 滚动语义 | 随轨滚。 |
| 9 | 动效 | opacity 0.2s（reduced-motion 关闭）。 |
| 10 | 边界态 | 触屏（pointer:coarse）始终显示；键盘 Tab 到它时显示（focus-visible）；上传中/失败均可移除；预览打开时移除会关闭预览。 |
| 11 | 无障碍 | `aria-label=移除 X`（图片用文件名）；文件卡 `aria-label=移除 name`。 |
| 12 | 依据 | `E0` `ComposerAttachments.tsx:84-91`；`E0` `ComposerAttachments.module.css:33-67`；`E0` `FileCard.tsx:63-70`；`E0` `FileCard.module.css:84-130`；`E0` `labels.ts:52-66`。 |
| 13 | 反证 + 验收 | **反证**：若移除按钮常显（桌面），每一张缩略图右上角都挂一个叉，用户很难看清缩略图；若触屏不常显，手机上没有 hover 就永远删不掉附件。**验收**：`SET-131` 桌面 hover/键盘才显示、触屏常显（真机两种）；`SET-132` 移除后该附件不再出现也不影响其它附件（真机）。 |

#### `composer.attach.file-card` —— 待发送文件卡（名称/大小/进度/状态）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.attach.file-card`。所属：轨内 `attachment.kind==='file'` 项。出现条件：草稿里有通用文件附件；每文件 1 张。覆盖：`FileCard.tsx:22-81`（组件全量；扫描命中在 retry/remove 行）。 |
| 2 | 网格与定位 | `.card` `inline-flex; width:240px; height:64px; padding:0 12px; gap:10px; border:0.5px; border-radius:lg`（`FileCard.module.css:1-14`）；`.icon` 28×28；正文两行：名称 14/22 500 + meta 12/15 tertiary；进度条 `position:absolute; left/right:12; bottom:5; height:2; border-radius:1`（`:132-159`）。 |
| 3 | 命中区 | 文件卡正文（失败态时正文是重试按钮，见 file-retry）；卡右上角移除 18×18；卡本身不是点击区。 |
| 4 | 手势 | 上传中/成功态：卡不可点（只有移除可点）；失败态：正文变重试按钮（见 file-retry）。无长按/双击。 |
| 5 | 行为 | 三态：`uploading`（spinner + 进度条，进度未知时 indeterminate 动画）/`ready`（显示扩展名 + 大小）/`error`（红描边 + 失败文案 + 重试）。上传由上层 `uploads[id]` 状态驱动（`ComposerAttachments.tsx:56-72`）。 |
| 6 | 视觉状态 | uploading：spinner + progress track/bar（有真实 progress 时宽度 %，无则 1.2s 往返动画）；ready：类型图标 + 扩展名+大小；error：红描边、红 meta、移除常显红底；hover 时名称让出 18px 给移除（css `:121-124`）。 |
| 7 | 布局占位与展开形态 | 固定 240×64 参与轨横向布局；不展开。 |
| 8 | 滚动语义 | 随轨滚。 |
| 9 | 动效 | spinner 0.8s 旋转；进度条 indeterminate 1.2s alternate；reduced-motion 关（`:115-119`）。 |
| 10 | 边界态 | 上传中；上传完成；失败；进度未知（无 total）；超长文件名 ellipsis（`title` 全文）；空文件名 fallback「文件」；粗指针移除常显。 |
| 11 | 无障碍 | 正文 `aria-label=待发送文件`（成功态）/重试按钮（失败态）；进度条 `aria-hidden`（**没有** progressbar 角色，见 Q-SET-16）；移除按钮有名字。 |
| 12 | 依据 | `E0` `FileCard.tsx:19-81`；`E0` `FileCard.module.css:1-165`；`E0` `ComposerAttachments.tsx:56-73`；`E0` `labels.ts:52-66`。 |
| 13 | 反证 + 验收 | **反证**：若进度条用真实 role=progressbar 但不给它 total/值，读屏会播报错误百分比；若不显示失败态而只是转圈，用户永远等不到。**验收**：`SET-133` 三态视觉可区分（截图）；`SET-134` 上传完成后卡片显示大小与扩展名（真机）。 |

#### `composer.attach.file-retry` —— 文件上传失败重试（正文即按钮）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.attach.file-retry`。所属：失败态文件卡正文。出现条件：`uploads[id].status==='error'`。覆盖：`FileCard.tsx:52`（及 `:37-40` 的 `retryable`）。 |
| 2 | 网格与定位 | 失败时正文是 `<button class="body retry">`（占卡宽减 icon 与移除），内含文件名 + 失败 meta；`padding:8px 0`（css `:43-59`）。 |
| 3 | 命中区 | 卡内正文区域（约 190×64），比普通文字按钮大。 |
| 4 | 手势 | 单击 = `onRetryFile(id)`（重跑该文件上传）；无长按/双击。 |
| 5 | 行为 | 重试期间卡片回到 uploading 态（spinner+进度）；再次失败回到 error；成功 ready。幂等（重复点击会重复发起？——重试按钮在 uploading 态消失，因此不会重复）。 |
| 6 | 视觉状态 | error 描边/红 meta；按钮 hover 无特殊（继承卡样式）；focus-visible 显示。 |
| 7 | 布局占位与展开形态 | 与成功态同尺寸，不改变轨高度。 |
| 8 | 滚动语义 | 随轨滚。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 连续失败可反复重试；不可重试的失败（非 error 状态）没有此按钮；上传中被移除则无重试。 |
| 11 | 无障碍 | 按钮 `aria-label=重试 name`；文字包含失败原因位（meta）。 |
| 12 | 依据 | `E0` `FileCard.tsx:37-62`；`E0` `FileCard.module.css:51-82`；`E0` `ComposerAttachments.tsx:63-71`；`E0` `labels.ts:58-65`。 |
| 13 | 反证 + 验收 | **反证**：若失败只显示红描边而没有重试入口，用户只能删掉重选（大文件要重新上传）；若重试可并发点两次，会重复上传同一文件。**验收**：`SET-135` 失败后点正文重试成功（Mock 首次 500 二次 200）；`SET-136` 重试中无重复上传请求（网络面板）。 |

#### `composer.attach.drop-target` —— 文档级拖放目标 + 全屏 DropOverlay

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.attach.drop-target`。所属：文档级（任何可接受拖放的页面，依附于挂载中的 `ComposerAttachments`）。出现条件：该视图 `canAcceptDrop`（未锁定/非 busy/可加附件）时接受；文件拖入文档即激活。覆盖：`drop-events.ts:51,65,76,78,82,84`；遮罩 `DropOverlay.tsx:24-40`。 |
| 2 | 网格与定位 | `DropOverlay` 全视口 `position:fixed; inset:0; z-index:1000; display:flex; center`，`pointer-events:none`（装饰，不抢拖放目标）（`DropOverlay.module.css:1-19`）；内容居中：插画 115×84、标题 `--dsw-font-l-20`、限制行 14 + `white-space:pre-wrap`（`:27-52`）；`margin-top:-3%` 视觉居中。 |
| 3 | 命中区 | **没有命中区**（`pointer-events:none`）；目标是整页：拖放事件在 document 上监听，页面上任何一个 drop 都交给该视图。 |
| 4 | 手势 | 拖入：`dragenter`（计数嵌套深度）→ 显示遮罩；`dragover`：`dropEffect = canAcceptDrop ? 'copy' : 'none'`；离开/`dragend` 重置；`drop`：取 `dataTransfer.files` + `webkitGetAsEntry()` 识别目录 → 一次性 `onAddFiles(files, dirs)`。文本拖放/非文件拖放忽略。**粘贴不在此节点**（见 `composer.attach.paste`）。 |
| 5 | 行为 | `canAcceptDrop=false` 时仍显示遮罩但文案为「无法添加文件」且不接收（`DropOverlay disabled`，`ComposerAttachments.tsx:43-48`）；接收后文件交给 `intakeFiles` 做限制校验，整批拒绝时 toast；遮罩在 drop/离开视口/dragend 时消失。 |
| 6 | 视觉状态 | 遮罩：页面压暗遮罩色 + `backdrop-filter: blur(10px)` + 160ms fade-in；可接收=彩色插画 + 标题 + 限制行；不可接收=灰色禁用插画 + 无限制行（`DropOverlay.tsx:31-36`）；reduced-motion 无 fade。 |
| 7 | 布局占位与展开形态 | 形态 C：全屏浮层，完全不占布局（G-01）；`pointer-events:none` 保证拖放命中仍落在页面元素上。 |
| 8 | 滚动语义 | 无滚动。 |
| 9 | 动效 | 160ms fade-in；reduced-motion 关。 |
| 10 | 边界态 | 拖入目录（webkitGetAsEntry 可用时识别）；多文件一次 drop；混合文本+文件（只取文件）；不可接收态；嵌套 dragenter/leave 用深度计数避免闪烁；从窗口外离开时对齐 viewport 边界重置（`:57-64`）。 |
| 11 | 无障碍 | 遮罩 `role=status`（拖入时读屏播报）；插画 `aria-hidden`；限制文案是文本（可被读屏读到）。 |
| 12 | 依据 | `E0` `sw/ui-attachment/src/client/drop-events.ts:30-87`；`E0` `DropOverlay.tsx:24-40`；`E0` `DropOverlay.module.css:1-52`；`E0` `ComposerAttachments.tsx:32-48`；`E0` `labels.ts:40-50`。 |
| 13 | 反证 + 验收 | **反证**：若遮罩捕获指针事件（pointer-events:auto），拖放一进入页面就会丢失 drop 目标，永远放不下；若不区分能否接收，用户拖进来看到邀请、松手却毫无反应。**验收**：`SET-137` 拖入即显示遮罩、松手成功加入附件（真机）；`SET-138` 不可接收态松手不加入且文案为拒绝（真机）。 |

#### `composer.attach.paste` —— 粘贴文件/图片（剪贴板）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.attach.paste`。所属：草稿编辑器（**实现不在本审计包**：`ui-conversation`；此处登记归位与证据）。出现条件：编辑器挂载且未锁定/非 busy。覆盖：`sw/ui-conversation/src/client/input/editor/keymap.ts:159-185`（PASTE_COMMAND）；入口 `:139` 转发给 `intakeFiles`。 |
| 2 | 网格与定位 | 无独立视觉：粘贴发生在草稿输入区内，结果进入附件轨（见 `composer.attach.rail`）。 |
| 3 | 命中区 | 无独立命中区；输入区即目标。 |
| 4 | 手势 | 系统粘贴（Ctrl/Cmd+V 或长按粘贴）：读 `clipboardData.items`，`kind==='file'` 的项取出 File（含 `webkitGetAsEntry` 目录标记）→ `intakeFiles(files, dirs)`；若同时有 `text/plain` 非空 → 继续走文本粘贴（`handlers.pasteText`），否则 preventDefault 且只吃文件。 |
| 5 | 行为 | 纯文件剪贴板 → 只加附件；文件+文本 → 两者都处理（文本插入草稿、文件入轨）；无文件 → 浏览器原生文本粘贴。受限时由 intake 统一拒绝并 toast（与拖放同路径）。 |
| 6 | 视觉状态 | 无独立视觉；成功入轨后的缩略图/文件卡见对应节点。 |
| 7 | 布局占位与展开形态 | 无。 |
| 8 | 滚动语义 | 无。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | 剪贴板只有文本 → 原生粘贴；只有文件 → 加附件且阻止默认；目录/文件混合；锁定时忽略；`输入法组合中`不影响（PASTE_COMMAND 与 IME 无冲突）。 |
| 11 | 无障碍 | 粘贴是系统手势；附件加入后由轨的 `role=group` 与项的无障碍名播报。 |
| 12 | 依据 | `E0` `sw/ui-conversation/src/client/input/editor/keymap.ts:38-42,159-185`；`E0` `sw/ui-conversation/src/client/input/editor/view-binding.ts:139`；`E0` `InputBar.tsx:212-239`（intake）。 |
| 13 | 反证 + 验收 | **反证**：若粘贴文件时同时 `preventDefault` 文本，用户从网页复制「图片+说明文字」会丢说明；若粘贴文件不阻止默认，浏览器可能把图片当文件下载/导航。**验收**：`SET-139` 复制图片后 Ctrl+V 入轨（真机）；`SET-140` 同时含文本时文本进草稿、图片进轨（真机）。 |

#### `composer.attach.picker` —— 文件对话框入口（`/file` 命令 + 隐藏 input）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.attach.picker`。所属：composer（**实现不在本审计包**：`ui-conversation`；此处登记归位与证据）。出现条件：composer 挂载且 `canAcceptDrop`（`available`）；命令 `/file` 出现在命令菜单的「添加」段。覆盖：`sw/ui-conversation/src/client/apply.ts:271-280`（注册 `/file` action）；`InputBar.tsx:455-462`（隐藏 input）；`:243-249`（change 处理）；`view-binding.ts:88-97`（bindFilePicker）。 |
| 2 | 网格与定位 | 无可见控件：`<input type="file" multiple hidden>` 挂在工具行 DOM 内（不在布局中显示）。命令菜单里的「文件/附件」行由 popupSelect 渲染（见 `composer.command.popup`）。 |
| 3 | 命中区 | 菜单行整行（命令弹窗）；隐藏 input 无命中区。 |
| 4 | 手势 | 打开命令菜单（`+` 或 `/`）→ 选「文件」→ `inputHub.pickFiles()` → `inputRef.click()` 原生文件对话框；选中后 `change` 事件把 `files` 交给 `intakeFiles`（`e.target.value=''` 以便再选同一文件）。 |
| 5 | 行为 | 原生对话框**多选**（`multiple`）；不设 `accept`（类型由 intake 校验）；子代理会话下 input disabled（`subagent!==null`）；选择后立即入轨并自动上传（通用文件）。 |
| 6 | 视觉状态 | 菜单行 default/hover/disabled（`available()` 为 false 时命令不可用）。 |
| 7 | 布局占位与展开形态 | 无（原生对话框由系统绘制）。 |
| 8 | 滚动语义 | 无。 |
| 9 | 动效 | 无。 |
| 10 | 边界态 | `available=false`（锁定/busy/无可加附件视图）时命令不出现或 disabled；取消对话框无副作用；重复选同一文件可再次触发（value 重置）；子代理会话禁用。 |
| 11 | 无障碍 | 命令行有文本标签（本地化）；原生文件对话框由系统提供无障碍。 |
| 12 | 依据 | `E0` `sw/ui-conversation/src/client/apply.ts:271-280`；`E0` `InputBar.tsx:241-249,455-462`；`E0` `view-binding.ts:88-97`；`E0` `facade.ts:587-615`；`E0` `hub.ts:200-214`。 |
| 13 | 反证 + 验收 | **反证**：若 input 不重置 value，用户删掉附件后想重选同一张图会打不开对话框（change 不触发）；若不支持 multiple，用户选 10 张图要开 10 次对话框。**验收**：`SET-141` 一次多选入轨（真机）；`SET-142` 删除后再选同一文件仍入轨（真机）。 |

#### `composer.attach.lightbox` —— 原图预览（ImageLightbox）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `composer.attach.lightbox`。所属：草稿图片 / 历史消息图片 → 共享 `ImageLightbox`（primitive）。出现条件：缩略图点击后 `preview!==null`（草稿）或 `open=true`（历史图片）。实现不在本包（`ui-primitives/ImageLightbox`），此处只登记行为契约。 |
| 2 | 网格与定位 | 全屏遮罩 + 居中原图（共享组件尺寸/圆角）；关闭按钮在固定角。 |
| 3 | 命中区 | 关闭按钮；遮罩点击；图本身不缩放（未见 pinch/zoom 实现）。 |
| 4 | 手势 | 单击关闭/遮罩关闭；Esc 关闭（共享 Modal 语义）；无缩放/旋转/滑动切换（未见实现，多图是逐张打开）。 |
| 5 | 行为 | 只读预览；关闭回触发缩略图；附件被移除时草稿预览自动关闭。 |
| 6 | 视觉状态 | 共享 lightbox 样式（深色遮罩/原图按比例）。 |
| 7 | 布局占位与展开形态 | 形态 C 浮层。 |
| 8 | 滚动语义 | 原图超屏时共享组件处理（未见内滚/缩放实现）。 |
| 9 | 动效 | 共享。 |
| 10 | 边界态 | 图片加载失败（历史图有独立失败态，草稿预览为本地 URL 一般不失败）；无图时不渲染。 |
| 11 | 无障碍 | `role=dialog` + 本地化 `dialog/close` labels（`labels.ts:13-15`）；`alt` 为文件名。 |
| 12 | 依据 | `E0` `ComposerAttachments.tsx:98-105`；`E0` `MessageImage.tsx:162`；`E0` `labels.ts:13-15`；`E1` ImageLightbox 实现（`prim`，未逐行取证）。 |
| 13 | 反证 + 验收 | **反证**：若预览不是浮层而是把图展开在 composer 里，输入区会被顶走，用户失去正在编辑的草稿位置。**验收**：`SET-143` 预览期间 composer 布局不动（截图）；`SET-144` Esc/关闭回原缩略图（真机）。 |

#### `chat.msg.image` —— 历史消息图片（缩略/单图/失败重试/灯箱）

| # | 字段 | 内容 |
|---|---|---|
| 1 | 身份与出现条件 | `chat.msg.image`。所属：消息图片 gallery（`conversation.message.images`）、轨迹图片、工具卡图片（同一渲染器）。出现条件：消息/工具有图片；lone=单图、过图=64 tile、列表=48 thumbnail（`MessageImage.tsx:169-191`）。覆盖：`:129,135,144,152`。 |
| 2 | 网格与定位 | `.gallery` `flex wrap; gap:10`（`MessageImage.module.css:1-16`）；单图：长边 240、宽高比 clamp [0.25,4]、不上采样（`MessageImage.tsx:50-62`）；tile 64×64；thumbnail 48×48；`.frame` `min-width/height:44px`、圆角 xl、`cursor:zoom-in`（css `:18-44,71-87`）。 |
| 3 | 命中区 | 单图按钮（图尺寸，≥44px）；tile/thumbnail 64/48（48<44? 48>44 可）；失败态整块是重试按钮。 |
| 4 | 手势 | 单击图 = 打开 `ImageLightbox`（src 就绪后）；失败态单击 = 重试（`attempt+1` 重跑加载 effect）。 |
| 5 | 行为 | 挂载时 `load(attachment)`（session 授权 URL；`peek` 可同步命中缓存）；加载中显示 loading 文案/spinner；失败显示重试（thumbnail 变刷新图标）；成功可开原图；gallery 多图时统一 variant。 |
| 6 | 视觉状态 | loading（spinner/文案 + `aria-busy`）；loaded；error（红底重试块）；thumbnail 用状态图标 + tooltip；focus-visible 焦点环（css `:103-107`）。 |
| 7 | 布局占位与展开形态 | 参与消息流布局；单图尺寸按比例预算，tile 固定；灯箱形态 C。 |
| 8 | 滚动语义 | 随消息滚动。 |
| 9 | 动效 | spinner 0.9s；reduced-motion 关。 |
| 10 | 边界态 | URL 加载失败（可重试）；无尺寸信息（preview 未探针）→ 240×240 方块；超长/超宽图裁切对齐（top/left）；列表缩略图只显示 48 方块；reduced-motion。 |
| 11 | 无障碍 | 按钮 `aria-busy`（加载中）、`aria-label` 打开原图/加载中/加载失败；`alt` 为文件名；失败态在 thumbnail 用刷新图标 + aria-label。 |
| 12 | 依据 | `E0` `sw/ui-attachment/src/MessageImage.tsx:45-192`；`E0` `MessageImage.module.css:1-107`；`E0` `sw/ui-attachment/src/client/MessageImages.tsx:6-17`；`E0` `labels.ts:22-31`。 |
| 13 | 反证 + 验收 | **反证**：若加载失败不给重试，弱网用户刷新整个会话才能看到图；若单图不按比例预算尺寸，图片加载完成会把消息高度突然撑开、正文被推走。**验收**：`SET-145` 弱网失败可点重试恢复（真机/Mock）；`SET-146` 单图加载前后消息高度不跳（录屏）。 |

---

## 3. 排除清单

> 分母 472 条 = **E0 243 条（66 个节点）** + **excluded 229 条**。
> 逐条理由在 [dsh-settings.facts.csv](dsh-settings.facts.csv) 的 `note` 列；下表按类别汇总，确保**没有任何未解释项**。
> 复核命令（未覆盖必须为 0）：
> `python tools/ui-audit/ui_audit.py check --findings _ws\out\findings-dsh-settings.csv --facts docs\spec\ref\dsh-settings.facts.csv --only-prefix "ui-settings,ui-model-selection/,ui-agent-preset/,ui-input-trigger/,ui-commands/,ui-attachment/,file-upload/" --out _ws\out\coverage-dsh-settings.md --fail-on-uncovered` → `covered=243 excluded=229 uncovered=0 stale=0`。

### 3.1 测试文件（72 条，全部 excluded）

理由统一：**测试文件（`*/tests/`）不是产品 UI**，命中来自测试夹具、桩对象、断言与测试用 button/anchor，不构成真实交互点。

| 文件 | 条数 | 文件 | 条数 |
|---|---|---|---|
| `ui-agent-preset/tests/components.client.spec.tsx` | 7 | `ui-settings-models/tests/onboarding-dialog.client.spec.tsx` | 2 |
| `ui-agent-preset/tests/create-plugin-menu-item.client.spec.tsx` | 1 | `ui-settings-models/tests/styles.client.spec.ts` | 2 |
| `ui-attachment/tests/attachment-rail.client.spec.tsx` | 16 | `ui-settings-plugin-inventory/tests/components.client.spec.tsx` | 14 |
| `ui-commands/tests/popup-view.client.spec.tsx` | 7 | `ui-settings-plugins/tests/section.client.spec.tsx` | 2 |
| `ui-input-trigger/tests/menu-view.client.spec.tsx` | 8 | `ui-settings-subagent/tests/card.client.spec.tsx` | 2 |
| `ui-model-selection/tests/model-select.client.spec.tsx` | 9 | `ui-settings-general/tests/settings-root.client.spec.tsx` | 2 |

### 3.2 CSS 结构属性（148 条，全部 excluded）

理由：扫描器把 CSS 声明当作交互点；这些是**纯结构/视觉声明**，没有独立可交互语义。它们的内容已归入对应节点的第 6/7/8 字段：

| 类别 | 条数 | 归入 | 说明 |
|---|---|---|---|
| `css.overflow` | 59 | 各节点第 7/8 字段 | 溢出/滚动容器声明（内滚、ellipsis、clip） |
| `css.cursor` | 48 | 各节点第 6 字段 | 光标语义（pointer/zoom-in） |
| `css.focus` | 41 | 各节点第 6/11 字段 | focus-visible 焦点样式 |

按文件（仅含有命中的文件）：`ModelsSection.module.css` 20、`ModelSelect.module.css` 16、`AgentPresetSection.module.css` 14、`PluginInventorySettingsTab.module.css` 14、`SettingsRoot.module.css` 11、`DesktopOnboarding.module.css` 10、`MenuView.module.css` 8、`FileCard.module.css` 7、`PopupSelectView.module.css` 7、`MessageImage.module.css` 6、`AttachmentRail.module.css` 5、`SubagentModelSelectionFields.module.css` 4、`ComposerAttachments.module.css` 3、`PluginsSettingsSection.module.css` 3、`AccountMenu/AccountNotice/AccountSection/SignInDialog.module.css` 各 2、`PresetGuideDialog.module.css` 2、其余（`AgentPresetLabel`/`AgentPresetSeat`/`CreatePluginMenuItem`/`OnboardingSurface`/`PlatformOverlay`/`DesktopUpdateIndicator`/`SettingsDocumentAction`/`chrome`/`OnboardingModal`）各 1。
（`css.sticky`/`css.contain` 在本路由 0 条。）

### 3.3 非交互代码（9 条，逐条理由）

| kind | 位置 | 代码片段 | 排除理由 |
|---|---|---|---|
| `disclosure` | `ui-settings-plugin-inventory/.../PluginInventorySettingsTab.tsx:125` | `rowKey, ..., onToggle` | props 解构参数声明，不是交互绑定 |
| `disclosure` | 同上 `:137` | `readonly onToggle: (key: string) => void` | props 类型声明 |
| `jsx.onfocus` | `ui-settings-models/.../ModelRow.tsx:17` | `onBlur?: () => void` | 类型声明 |
| `disclosure` | `ui-settings-models/.../ModelRow.tsx:35` | `onToggle: () => void` | 类型声明 |
| `disclosure` | `ui-settings-account/.../OnboardingPurposeStep.tsx:32` | `(['office','development'] as const).map(...)` | JSX 渲染 map，无手势 |
| `disclosure` | 同上 `:38` | `function PurposeCard(...)` | 组件函数声明 |
| `disclosure` | 同上 `:42` | `onToggle: () => void` | 类型声明 |
| `jsx.onkeydown` | `ui-model-selection/.../ModelSelect.tsx:479` | 注释 `bubble through this React subtree, keeping onKeyDown/onBlur live` | 注释行，非代码 |
| `jsx.onfocus` | 同上 `:479` | 与上同一注释行（正则同时命中两个关键字） | 同上 |

> 可点击/手势类（`jsx.onclick` 92、`native.button` 66、`native.input` 20、`jsx.onkeydown` 9、`jsx.onfocus` 46-9、`jsx.onpointer` 6、`jsx.ondrag` 6、`jsx.onwheel` 3、`jsx.onscroll` 2、`aria.role` 16、`a11y.expanded` 33、`disclosure` 13、`native.details` 2、`native.anchor` 4、`container.scroll` 43）**全部落在 §2 的节点上或在上表给出真实理由**，无一条「无法解释」。

### 3.4 其它说明

- `ui-settings`、`ui-settings-shell`、`ui-settings-agent-loop`、`ui-settings-web-search`、`ui-settings-session-log`、`file-upload` **扫描命中 0 条**：前者是纯数据/契约层，后四者只渲染共享 `SettingsForm`/`Switch`/服务（无原生交互标签）。它们的事实以组件源码写入 §2 与 §1（E0 行不在 facts.csv 计数内，但可核对 `path:line`）。
- 桌面专属能力（`settings.account.platform-overlay`、`settings.shell.update-indicator`、onboarding 全屏流程）在移动端不适用，是否移植见 Q-SET-17。

---

## 4. 待确认（E2 候选）

> 以下全部是**推测/取舍**，源码里没有答案，**禁止直接进实现**；等用户拍板后才能升级为 E0/E1。

| 编号 | 问题 | 为什么需要拍板 | 参考事实（E0） |
|---|---|---|---|
| Q-SET-01 | myco 的无限抽屉里要不要「搜索设置」？ | DSH 设置壳没有搜索（只有插件清单页内部搜索）。如果 myco 设置项多、且是抽屉形态，搜索的收益可能更高；但加上就要定搜索范围/键盘入口/空态。 | `SettingsRoot.tsx` 无 search；`PluginInventorySettingsTab.tsx:475-485` 只有清单内搜索 |
| Q-SET-02 | 统一「保存」语义选哪种？ | DSH 同屏并存两种：插件配置页**显式保存**（离开丢弃草稿、无放弃按钮），General/Models/账户**即时写**。myco 若混用，用户无法预测改动何时生效。 | `prim/SettingsForm.tsx:6-10,50-74`；`DeveloperToolsRow.tsx:30-35`；`ProviderEditor.tsx:309-322` |
| Q-SET-03 | 要不要「重置全部设置」与全局危险操作确认？ | DSH **未实现**（只有 provider 删除与退出登录两个局部确认）。myco 是否要做属于产品取舍；若做，需要定义撤销窗口与二次确认强度。 | `ModelsSection.tsx:684-717`；`SignOutDialog.tsx:24-39`；设置壳无 reset |
| Q-SET-04 | 设置壳形态：居中模态（DSH 800×800 + 188 左导航）还是 myco 的无限抽屉？ | DSH 是桌面模态、无返回栈；myco 已定「无限抽屉」，抽屉里是否保留左导航/页签或改为逐级推入，会直接决定节点层级与返回语义。 | `SettingsRoot.module.css:85-103,111-119`；§1 槽位结构 |
| Q-SET-05 | 抽屉内子页导航与返回：DSH 无「返回上一页」（左导航常驻 + 关闭），myco 抽屉是否需要逐级返回/手势返回？ | 若沿用 DSH 左导航，抽屉内横向空间更紧；若逐级推入则要定义返回键/手势与状态保留。 | `SettingsRoot.tsx:71-89`（只有 select/close，无 back） |
| Q-SET-06 | 模型面板要几个维度？DSH 只有「模型 + reasoning effort」；preset 在 hero chip/设置页，上下文长度只在 `settings.models` 逐模型配置。myco 合并面板是否加 preset / 上下文长度 / 温度等第三维？ | 用户点名「模型切换比不上 DSH」；若把 preset 塞进同一面板，会与「会话开始后不可换 preset」的 Host 约束冲突（只能在空白会话换）。 | `ModelSelect.tsx:490-505`；`ProviderEditor.tsx:14-19`；`seat-store.ts:212-217` |
| Q-SET-07 | 模型切换对「已发消息」的影响要不要在 UI 明示？ | DSH 选择是 session 级持久投影（`selectModel`），代码中**未见**对历史消息的改写/重渲染；但 UI 没有任何「仅影响后续回合」的提示。myco 是否需要一句说明/标记？ | `directory.ts:17-35,84-128`；`index.ts:1-12`（共享目录注释） |
| Q-SET-08 | 「当前生效」汇总要显示到什么程度？ | DSH 只在输入区胶囊显示「模型 · 推理等级」，无上下文窗口/价格/可用性汇总；菜单里也无汇总区。myco 若做汇总卡，需要新的信息源（Host catalog 是否给这些字段待查）。 | `ModelSelect.tsx:416-428`；`locales.ts:20-21` |
| Q-SET-09 | 附件导入要不要内建「相册/相机」选择器？ | 用户点名「导入媒体缺内建 UI」；DSH **未见实现**（只有原生文件对话框/拖放/粘贴），桌面端靠系统文件选择器。移动端要内建相册/相机属于新增能力，需要权限与压缩策略。 | `apply.ts:271-280`（/file）；`InputBar.tsx:455-462`（input type=file）；`drop-events.ts`；`keymap.ts:159-185` |
| Q-SET-10 | 子页危险操作确认的粒度：provider 删除要确认，但**模型行删除无确认**；myco 是否统一？ | DSH 不一致：删 provider = 弹窗确认 + 先删凭据；删模型行 = 立即删（草稿态，Apply 前可取消）。若 myco 设置也是即时写，删模型行没有确认的风险更大。 | `ModelsSection.tsx:684-717`；`ModelRow.tsx:75-81`（无确认） |
| Q-SET-11 | 通用文件的客户端限制策略：DSH 只对图片限制数量/大小/总量（Host `imageLimits` 投影），通用文件无客户端限制、选中即上传。myco 是否统一限制（大小/数量/类型）？ | 无限制时用户可能上传超大文件后才在 Host 侧失败；限制太严又会挡住正常用法。 | `InputBar.tsx:206-239`（注释明确「generic files carry no client-side size or count limit」）；`InputBar.tsx:414-417` |
| Q-SET-12 | 上传取消要不要做成显式按钮？ | DSH 客户端上传服务支持 `AbortSignal`（Worker terminate），但 ComposerAttachments **只给移除/重试**，没有「取消上传」。移除是否等效于取消（丢弃结果）？移动端弱网时用户可能想要显式取消。 | `file-upload/.../runtime.ts:79-152,240-298`；`ComposerAttachments.tsx:56-71`；`FileCard.tsx` 无取消按钮 |
| Q-SET-13 | 草稿附件排序与图文混排规则：DSH 轨按草稿顺序、不按类型分组，图片 64×64、文件卡 240×64。myco 是否分组（图片区/文件区）或保持混排？ | 影响轨的横向浏览与移除操作密度。 | `AttachmentRail.tsx:1-171`；`ComposerAttachments.tsx:49-97` |
| Q-SET-14 | Plugins 页签溢出策略：DSH 页签行 `flex` 不换行不滚动（页签多时会挤/溢出）。myco 页签多了怎么办（横向滚/下拉菜单/换行）？ | DSH 只有 2 个页签（可配置/全部），未处理多页签溢出；myco 若页签更多需要补设计。 | `PluginsSettingsSection.module.css:23-29`（无 overflow/wrap） |
| Q-SET-15 | 移动端命中区扩边规则：DSH 多处 18px（移除/钻取箭头）、24px（翻页箭头/搜索清除）、28px（关闭/展开/编辑）、34px（菜单行）小于 44dp。myco 需要统一「视觉小、命中区 ≥44dp」的扩边方案。 | 直接照搬 DSH 视觉尺寸在手机上会点不中；扩边方式（伪元素/负 margin/热区层）需要定一种。 | `ComposerAttachments.module.css:33-67`（18px）；`MenuView.tsx:197-210`（18px）；`SettingsRoot.module.css:209-225`（28px）；`ModelSelect.tsx:574`（34px 行） |
| Q-SET-16 | 无障碍细节补齐清单：DSH 有些标签是 `span` 而非 `<label for>`（模型卡字段）、卡片内嵌 `<button>`（插件清单 jump-link）、上传进度无 `role=progressbar`、菜单错误条无 `role=alert`、顶部加载失败文案无 `role=alert`。myco 是照抄还是修正？ | 照抄会继承这些语义缺陷；修正需要定统一规则（表单控件必须 label 关联、禁止嵌套按钮、进度必须 progressbar）。 | `ProviderEditor.tsx:382,429`（span label）；`PluginInventorySettingsTab.tsx:419-422`（卡内 button）；`FileCard.tsx:71-78`（进度无 role）；`ModelSelect.tsx:541-552`（错误无 role）；`ModelsSection.tsx:318-329`（错误无 role） |
| Q-SET-17 | 桌面专属能力是否移植：内嵌 Platform 页（充值/用量）、桌面更新指示器、首次启动全屏 onboarding。移动端（Android）没有 Electron/原生窗口，这些节点是否全部不做？ | 不做 = 移动端缺充值/用量入口（需要替代：外部浏览器）；做 = 需要 WebView/浏览器标签方案。 | `PlatformOverlay.tsx`（`bridge.open/setBounds/close` 来自桌面 preload）；`DesktopUpdateIndicator.tsx`（`dshDesktop` 全局）；`OnboardingSurface.tsx` |
| Q-SET-18 | agent preset 的「介绍动画」与 hero chip 的取舍：DSH 会在从设置页进入 Creator 时让 chip 播一次逐字动画（reduced-motion 跳过）。myco 是否保留这类一次性动画？ | 属于品牌手感取舍；也是「新会话 hero 行」要不要有非必要动效的问题（G-10 只管展开动画）。 | `AgentPresetSeat.tsx:46-68,123-137` |

---

## 5. 模型面板的完整维度清单

>「模型面板」= 输入区 `conversation.input.model` 座位打开的菜单（`ModelSelect`）。下表覆盖所有维度（含「面板没有」的维度，明确写未见实现），并给出事实行。

| 维度 | 取值 | 默认 | 是否联动 / 互斥 | 证据行 |
|---|---|---|---|---|
| **模型**（model.id） | Host catalog `groups[].models[].id/name`；按 provider 分组，顺序：DeepSeek 账号 → 官方 → 其它（组内保持目录序） | 会话持久投影 `modelSelection.next`；无投影时用 catalog `default`（跨会话共享目录） | **是**：切换模型会把 effort 重置为新模型的 `defaultEffort`（`ModelDirectory.select` 用 `selection.reasoningEffort ?? catalog.reasoningFor(selection)?.defaultEffort`） | `ModelSelect.tsx:93-118`；`provider-order.ts:8-12`；`directory.ts:157-194,91-105`；`index.ts:46-101` |
| **provider** | `groups[].id`；分组头 = provider 名（`deepseek-account` 显示为「DeepSeek 账号」） | 无独立默认，由当前模型所属 provider 决定 | 不作为独立可选项（模型行绑定 provider） | `ModelSelect.tsx:561-566`；`provider-order.ts` |
| **推理等级**（reasoning effort） | `reasoning.efforts[].id/name`（Host 下发，客户端无词表）；若模型无 `defaultEffort`，首项为「Default」= 不指定→provider 默认 | `current.reasoningEffort ?? reasoning.defaultEffort`；无 effort 时显示 `retainedEffort`（上次保留的等级名） | **是**（单向）：模型 → effort 重置；effort 反向不换模型；模型无 reasoning → 根菜单 effort 行**消失**（互斥/条件出现） | `ModelSelect.tsx:119-140,497-503`；`locales.ts:25,35`；`directory.ts:157-165`；`index.ts:1-12`（元数据来自 Host） |
| **搜索**（模型列表） | 本地过滤文本；大小写不敏感「有序子序列」匹配，组内按 前缀 > 对齐分 > 目录序 排名 | 每次打开清空；仅 `choices.length>4` 时出现 | 搜索清空/切换 pane 会重置高亮；搜索模式下行 `tabIndex=-1`、↑↓ 走虚拟高亮 | `ModelSelect.tsx:106-115,255-258,509-537`；`prim` `rankByName` |
| **选中反馈** | 菜单内：当前值尾部对勾；提交中：trigger 显示状态点、目标行显示状态点；成功后菜单关闭 + trigger 回焦（无焦点环）；失败：composer 卡锚定 Toast（`session/writer-held` 专门文案） | — | pending 状态同时锁 trigger 与相关行（disabled） | `ModelSelect.tsx:370-400,472-474,592-596,634-640`；`directory.ts:44-45,91-128` |
| **当前生效汇总** | 仅输入区胶囊：`模型名 · 推理等级`（窄容器优先丢 effort、再丢模型名，只留图标+tooltip）；`aria-label` 含模型+等级；无上下文窗口/价格/剩余额度汇总 | — | 胶囊文案与菜单选中同源（同一 directory snapshot） | `ModelSelect.tsx:416-428`；`ModelSelect.module.css:45-63`；`locales.ts:20-21` |
| **切换是否影响已发消息** | 选择写入 session 级持久投影（`session.selectModel`）；代码中**未见**对历史消息内容/渲染的改写；`resetConnected` 会丢弃进行中的选择响应；`pending` 只影响 UI 反馈 | — | 进行中的选择与老响应按 generation 丢弃（最新一次赢） | `directory.ts:17-35,84-128,130-142`；`index.ts:1-12`；`catalog.ts:95-106` |
| **agent preset** | **面板没有此维度**（未见实现）。preset 在新会话 hero chip 选择（只暂存、空白会话才应用）或设置页 `agent-presets` 设默认；会话一旦开始，Host 拒绝切换 | 新会话 = Host 默认预设（roster `isDefault`） | 与模型面板互斥（两个不同入口/不同时机）；Coding Tools 关闭时过滤 PTC/Minimal | `AgentPresetSeat.tsx:1-13,105-112`；`seat-store.ts:193-247`；`ui-agent-preset/src/client/index.ts:1-18` |
| **上下文长度** | **面板没有此维度**（未见实现）。只在 `settings.models` 的逐模型编辑（`contextWindow`/`maxTokens`，K/M 文本）与 provider 默认（`defaultContextWindow`/`maxTokens`）里出现；切换模型不会改它 | 继承 provider/模型目录默认；空 = 继承 | 与模型面板无联动（不同页面，写不同 settings 层） | `ModelRow.tsx:86-111`；`DeepSeekModelsEditor.tsx:213-245`；`ModelListEditor.tsx:102-130` |
| **`/model` 命令弹窗** | 与输入区座位**同一 state、同一目录**的第二个入口：一列模型（分组、可搜索 `fuzzy-label`、失败行不可选、当前值 `active`）；无 effort 维度（选中后 effort 取同路由当前值或模型默认） | 打开时高亮停在当前值 | 与输入区座位联动：任一处切换，另一处下次打开显示新值 | `ui-model-selection/index.ts:51-101,124-166`；`popup.ts:117-130`；`ui-commands/PopupSelectView.tsx` |
| **禁用/不可用条件** | addressed subagent 会话两个入口都不可用（`available=false`；弹窗抛错、座位不渲染）；`locked` 时 trigger disabled；目录加载失败时菜单仍是错误条可重试 | — | 与 subagent 归属相关（Agent-bound RPC 不可跨子代理历史） | `index.ts:134-156,175-188`；`ModelSelect.tsx:242,460` |

> 结论：DSH 的模型面板是「**两维（模型 × effort）+ 搜索 + 双入口共享会话状态**」；**不是**「模型/思考强度/preset/上下文长度」四维面板。myco 若按四维合并面板设计，需要明确预设与上下文的入口/时机差异（见 Q-SET-06）。

---

## 6. 附件导入的完整来源与状态机

### 6.1 来源表

| 来源 | 选择器 / 触发 | 状态（入口 → 结果） | 取消 | 失败 / 限制 |
|---|---|---|---|---|
| **① `/file` 命令（原生文件对话框）** | composer 命令菜单里的「文件」项（图标回形针）；`/file` 命令 → `inputHub.pickFiles()` → 隐藏 `<input type=file multiple>` 的 `click()` | 命令可用（`canAcceptDrop`）→ 系统对话框 → 选中 → `intakeFiles` → 附件入轨 → 通用文件立即上传；图片先本地预览 | 系统对话框取消 = 无副作用；入轨后只能**移除**（无「取消上传」按钮，Q-SET-12） | 命令不可用时 `available=false`（不出现/禁用）；子代理会话 input disabled；intake 校验整批拒绝 → toast（图片超限/类型不支持/目录） |
| **② 拖放（文档级）** | 文件从系统拖入窗口任意位置；`dragenter/dragover` 显示全屏 `DropOverlay`；`drop` 提交 | 拖入（遮罩出现）→ 可接收：copy 光标 + 邀请文案 + 限制行；不可接收：禁用插画 + 不 drop；松手 → `onAddFiles(files, dirs)` → 轨 | 拖出/`dragend`/drop 后遮罩消失；已入轨同上只能移除 | 目录项（`webkitGetAsEntry` 识别）随 files 一起交，由 intake 决定拒/收；混合批次可整批拒绝；不可接收时 `dropEffect=none` 且不投递 |
| **③ 粘贴（剪贴板）** | 草稿编辑器内 Ctrl/Cmd+V（PASTE_COMMAND） | 读 `clipboardData.items` 的 file 项 → `intakeFiles`；若同时有文本 → 文本照常插入草稿 | 无（粘贴即入轨）；已入轨只能移除 | 无文件 → 原生文本粘贴；受限批次 → intake toast；锁定/忙时忽略 |
| **④ 历史消息图片**（发送后展示，不是导入入口） | 消息/工具卡图片 gallery | 挂载即 `load(attachment)`（session 授权 URL）→ loading → loaded / error | 无 | error → 点击整块重试（thumbnail 刷新图标）；尺寸未知按 240×240 裁切；灯箱预览 |
| **⑤ 草稿图片预览** | 轨内 64×64 缩略图单击 | 本地 `previewUrl` → `ImageLightbox` 全屏原图 | 关闭灯箱；移除附件会同时关闭预览 | 本地 URL 一般不失败（图片压缩/EXIF 失败未见处理，未实现） |

### 6.2 统一 intake（三路汇合）与限制

- 三路入口都进 `InputBar.intakeFiles(files, directories)`（`sw/ui-conversation/src/client/skeleton/InputBar.tsx:206-239`）：
  - 子代理「continuable」会话中：**只接受图片**，混合批次（含目录或非图片）整批拒绝 `image.unsupportedType`；
  - 图片限制来自 `imageLimits` 投影：`maxImagesPerMessage` / `maxImageBytes` / `maxMessageImageBytes` / `mediaTypes`；超限**整批拒绝**并 toast；
  - **通用文件没有客户端数量/大小/类型限制**，选中即上传（Host 侧还有一层，本审计未取）；
  - intake 通过后 `addFiles(files, directories)` 把附件放进 composer 草稿状态（实现在 `ui-conversation`，不在本包）。
- 限制提示：DropOverlay 的 desc 行显示「最多 N 张 / 每张 ≤ X」（`InputBar.tsx:414-417`、`labels.ts:40-50`）；对话框/粘贴路径没有前置提示，只在超限时 toast。

### 6.3 附件项状态机（草稿轨内）

| 类型 | 状态 | 进入条件 | 可见反馈 | 退出路径 |
|---|---|---|---|---|
| 图片 | `draft-image` | 入轨即本地预览 | 64×64 缩略图（`object-fit:cover`）+ 右上 18px 移除；粗指针常显移除 | 移除；发送后转为消息图片 |
| 文件 | `uploading` | 入轨即上传（`uploads[id].status='uploading'`） | spinner + 2px 进度条（有真实 total 时按 %，否则 indeterminate 往返） | 完成→ready；失败→error；移除 |
| 文件 | `ready` | 上传 HTTP 200 + 合法回执 | 文件类型图标 + 扩展名/大小 | 移除；发送 |
| 文件 | `error` | 传输失败 / 非 200 / 回执非法 | 红描边 + 失败文案 + 红底移除常显；正文变重试按钮 | 重试→uploading；移除 |
| 发送门禁 | `uploadsPending` | 任一文件仍 uploading | composer 主按钮语义改变；按 Enter 时 toast「文件仍在上传」 | 等上传完成或移除 |

### 6.4 上传通道（`file-upload`，能力层）

| 项 | 事实 | 证据 |
|---|---|---|
| 通道选择 | 浏览器 Blob/ReadableStream 走**后台 Web Worker**（XHR 带进度 / fetch duplex half 流式）；`Uint8Array` 走 Remote `fileUploads.upload`（base64） | `runtime.ts:191-221,240-298` |
| 进度 | Worker `postMessage({kind:'progress',loaded,total?})`；Blob 用 `xhr.upload.onprogress`；流用累计字节 | `runtime.ts:91-97,126` |
| 取消 | 服务层支持 `AbortSignal`（Worker `terminate()` + DOMException AbortError）；**UI 未暴露取消按钮** | `runtime.ts:252-258,282-286`；`ComposerAttachments.tsx` 无 cancel |
| 失败 | 传输失败 → worker error → reject；HTTP 非 200 → `file upload transport failed with HTTP n`；回执字段非法 → TypeError | `runtime.ts:208-211,300-334` |
| 兜底 | 自定义 hook `__DSH_FILE_UPLOAD__.fetch`（宿主注入）替代 Worker；无 Worker 环境 → reject「background upload requires Web Worker support」 | `runtime.ts:154-171,224-238,243-245` |
| 地址 | `POST {FILE_UPLOAD_ROUTE}?sessionId=…&name=…`，`content-type: application/octet-stream` | `runtime.ts:199-207`；`protocol.ts` |

### 6.5 与「用户点名：导入媒体缺内建 UI」的差距清单（可执行）

1. **入口**：DSH 无内建相册/相机，只有「命令菜单→文件」+拖放+粘贴三路；myco 要内建媒体选择器需**新增**（Q-SET-09）。
2. **选择器形态**：原生文件对话框（系统 UI，`multiple`，无 `accept`），类型校验在 intake 之后 —— myco 若要做应用内网格选择器，需要自己实现类型过滤/缩略图/多选。
3. **多选**：对话框 `multiple`；拖放/粘贴天然多文件。
4. **上传进度/取消**：文件卡有进度条，**没有取消**（Q-SET-12）。
5. **失败重试**：有（正文即重试）。
6. **预览缩略图**：图片 64×64 + 灯箱原图；文件卡是类型图标（无内容预览）。
7. **大小/类型限制提示**：只在拖放遮罩里显示图片限制；对话框/粘贴无前置提示。
8. **发送前移除**：图片/文件都有 18px 移除按钮；发送中仍有上传则阻止发送并 toast。

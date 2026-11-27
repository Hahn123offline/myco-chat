# P2 合并：`attachment`（导入媒体 / 附件选择与预览）

> **归属切分（与 M4 `composer.md` 已定的分割线一致，回答其 `Q-CMP-09`）**
> - **composer 拥有**：`+` 键接线、草稿附件轨（缩略图／文件卡／移除／重试／横滚／滚到末端）、粘贴与长文转附件的**接入点**、草稿灯箱触发。→ 本表**不定义**这些节点的行为，只在 §3 给出"给 composer 的硬约束"与证据。
> - **attachment（本表）拥有**：来源面板与三源选择（含取消选择）、intake 限制与拒绝文案、附件项**状态机**（处理中/就绪/失败/发送门禁）、处理/保存**通道**（进度/取消/重试的能力口径）、内建选择器本体（`Q-ATT-01`）、历史消息中的图片/文件、孤儿清理、后端缺口清单。
> - 灯箱/查看器**原语**归 `base.primitives`（M1）；草稿图的**触发**归 composer，历史图的**展示与查看**归本表。
> 参考列只许写事实库 `ref/*.md` 已有证据（带 `文件:行`）；缺失处写「未取证」，不推测（G-00）。
> 状态图例：`✅` 已有 G 规则或已拍板项可直接定；`📌` 多参考一致/属缺陷修复，建议一键确认；`⏸` 需用户拍板（编号见 §6）；`🆕` 我方新增设计（无参考实现可抄，必须写代价）。
> 冲突编号：`C-09` 已在 `_conflicts.md` 登记；`C-M5-09…C-M5-14` 为本次新发现、**尚未登记**（建议 Lead 顺延登记为 C-20 起）。

---

## 1. 节点清单（先穷举，再填表）

| 节点 ID | 出现条件 | 参考里有没有 | 旧 myco 有没有 |
|---|---|---|---|
| `attachment.panel.sources` | 点 `+` 后打开（面板本体） | DSH **无此面板**；dsh-Go ✅（底部弹层 tile 网格：相册/拍照/文件＋插件工具格＋「本对话」「手机操控」卡）；太墟 ✅（底部抽屉 `ChatAttachSheet`：网格＋扩展入口＋紧凑行）；chat-demo 无面板 | ✅ 三按钮 sheet（标题「添加附件」＋拍照/相册/文件，`MA:1291-1315`） |
| `attachment.source.file` | 面板内选「文件」 | DSH ✅（`/file`→`<input type=file multiple>`，无 `accept`）；dsh-Go ✅（`file_picker`）；太墟 ✅（attach sheet 的「文件」）；chat-demo ✅（原生 input＋扩展名白名单） | ✅ `OpenDocument("*/*")` **单张**（`MA:1309-1312,819-824`） |
| `attachment.source.camera` | 面板内选「拍照」 | DSH 无；dsh-Go ✅（`image_picker` camera）；太墟 未取证；chat-demo 无 | ✅ `launchCamera()` FileProvider 写 `cache/camera`（`MA:852-861`） |
| `attachment.source.grid`（**条件节点**：仅当 `Q-ATT-01` 选 B） | 面板内「最近照片」页签/首屏 | **无任何参考实现**（DSH/dsh-Go/太墟 的"选图"全部交给系统 picker）→ 🆕 我方设计（§5） | ❌ 无（系统相册 `PickVisualMedia(ImageOnly)`，`MA:1305-1308,819-821`） |
| `attachment.intake.limits` | 任一来源入轨时、超限时 | DSH ✅（`imageLimits` 投影，整批拒绝＋toast；通用文件无客户端限制）；dsh-Go 部分（「数量上限由状态层」）；太墟 部分（处理失败→notice→Toast；权限拒绝→Snackbar）；chat-demo ✅（客户端常量 64MB/32MB/50＋白名单，整批不添加） | ⚠️ 只有后端上限（图片 32MB／其它 8MB，`AS:24-25`），保存失败才报错误条；无数量/类型校验、无前置提示 |
| `attachment.state.item`（状态机语义，呈现由 composer 渲染） | 草稿附件存在时 | DSH ✅（`uploading/ready/error`＋`uploadsPending` 门禁）；dsh-Go ✅（处理中→tray，处理中不可发送）；太墟 ✅（`attachmentsProcessing` 期间不可发送＋顶部转圈）；chat-demo ✅（全部成功前不能发） | ⚠️ 同步落盘、无"处理中"状态；发送即带附件（`MA:962-968`） |
| `attachment.channel.process`（处理/保存通道：进度/取消/重试口径） | 附件被选中之后 | DSH ✅（Worker＋XHR/fetch 进度＋`AbortSignal`，但 UI 无取消按钮；失败可重试）；dsh-Go 未取证（无进度条/上传通道证据）；太墟 部分（VM 复制/压缩/编码，处理中无进度）；chat-demo ✅（3 并发、删除即 `cancelUpload`、`retryable` 分级） | ⚠️ 单条 `AttachmentSave`（base64→本地落盘，`MA:797-804`、`IB:136-149`）：无进度、无取消、失败只报错误条 |
| `attachment.msg.image` | 历史消息/工具卡含图片 | DSH ✅（`chat.msg.image`：尺寸预算、loading、失败重试、灯箱、工具卡同渲染器）；dsh-Go ✅（缩略＋全屏查看器：缩放、同组横滑图库、长按菜单、失败重载）；太墟 部分（用户气泡内 `ImageThumbnail` 缩略图；全屏查看未取证）；chat-demo ✅（点图开外链/重试） | **未取证**：`legacy-myco.md` 无历史消息图片渲染节点 → §6.2 |
| `attachment.msg.file` | 历史消息含通用文件 | DSH 未取证（历史侧只登记图片；`kind=file` 只送元数据 `IB:533`）；dsh-Go 未取证；太墟 未取证；chat-demo ✅（文档卡＋失败态） | **未取证**（同上） |
| `attachment.orphan-cleanup` | 移除/替换后；会话永久删除时 | 参考里都没有（DSH 只丢弃 composer 状态 id；dsh-Go/太墟 落盘与清理策略未取证） | ⚠️ 只在永久删会话时清附件目录（`SS:262-268`、`IB:241-248`）→ M-03/D12 孤儿文件 |

> **交接给 composer 的节点（本表不定义，只给约束）**：`+` 键接线、`composer.attach.rail`（轨壳/高度/横滚/滚到末端/空轨不渲染）、`rail.thumb`（缩略图呈现）、`rail.file-card`（卡片呈现）、`rail.remove`（移除按钮）、`rail.retry`（重试按钮）、草稿灯箱触发。证据与硬约束见 §3（标 `→composer` 的行）。
> 合计本表 **10 个节点**（含 1 个条件节点）。

---

## 2. 对照表（主线）

| 节点 ID | DSH | dsh-Go | 太墟 | chat-demo | 旧 myco | 冲突 C-xx | 建议 + 理由 | 状态 |
|---|---|---|---|---|---|---|---|---|
| `attachment.panel.sources` | **无面板**：三路来源（系统对话框/拖放/粘贴）各自独立，没有"来源选择"层（`dsh-settings.md §6.1`） | 底部弹层：固定 3 源（相册/拍照/文件）＋插件工具格＋「本对话」「手机操控」卡；tile＝**60dp 彩色方块＋图标 26＋名称**，4 个/行；内容多时内滚；进出 380/260ms（`dsh-go.md §2.3 attachment.panel`） | 底部抽屉 `ChatAttachSheet`：网格＋扩展入口＋紧凑行（`taixu.md §2.4 composer.attach-sheet` 字段 2） | 无面板（上传按钮＋拖放＋粘贴平铺） | **旧形态**：三按钮 ModalBottomSheet（拍照/相册/文件，`MA:1291-1315`），Column 无内滚（`MA:1297`） | `C-09`（内建 UI 有无） | **做面板**：三家移动端参考都有内建面板（dsh-Go／太墟／旧 myco），`C-09` 从"myco 新增"更正为"有 3 家先例、必须做"。**形态＝底部 sheet**（旧 myco 已有，dsh-Go 同；G-03 形态 C 不占位）——**旧的三选一（拍照/相册/文件）作为"来源层"保留**，条目升级为 dsh-Go 式 tile 网格；**内建选择器（若做网格，§5）嵌在这个面板里**，与三源是"首屏默认/并列来源"关系（`Q-ATT-01`）。**必须补内滚**（反证：旧 sheet 是无内滚 Column，来源变多会溢出，`MA:1297`）。**不做** dsh-Go 的插件工具格／「本对话」卡／「手机操控」卡／生成模式（→ §4） | 📌 |
| `attachment.source.file` | 系统文件对话框；`multiple`、**不设 `accept`**；`change` 后 `e.target.value=''` 以便再选同一文件（`InputBar.tsx:455-462,243-249`） | `file_picker`（面板字段 5） | attach sheet「文件」（字段 4/5） | 原生 input＋扩展名白名单 986 行＋64MB/32MB/50（`svc/attachments.ts:12-22`）；多选未取证 | `OpenDocument("*/*")` **单张**（`MA:1309-1312,819-824`） | `C-09`（多选缺失） | **多选＋无 `accept`＋重置 value**：反证＝单张选择要开 N 次对话框（DSH 字段 13 反证）；不重置 value 则删掉后重选同一文件不触发 change（同字段反证）。**不做扩展名白名单**（`C-M5-11`）：白名单与后端"任意 file＋kind 判定"（`AS:23-38`）不同源，会拒掉后端能收的文件 | 📌 |
| `attachment.source.camera` | 无（三路入口没有相机） | `image_picker` 拍照（面板字段 5） | 未取证 | 无 | FileProvider 写 `cache/camera` → 系统相机 → 回调入轨；失败文案「相机不可用：…」（`MA:852-861`） | `C-09` | **保留**（dsh-Go＋旧 myco 两家有；用户点名的"导入媒体"含拍照）。实现照旧（FileProvider 临时文件＋系统相机 Intent）；失败文案保留（反证：不给文案用户分不清权限还是硬件） | 📌 |
| `attachment.source.grid`（条件） | 未见实现 | **无网格**：「相册」→ 系统 `image_picker` | **无网格**：「照片」→ 系统 Photo Picker（E2 未直查） | 无（原生 input） | 无（系统相册） | —（无可比参考 → 属新增设计） | 🆕 **我方设计（无参考实现可抄）**：若做，形态＝面板内"最近照片"网格（多选＋缩略图＋分页）。**代价必须一起拍**（`Q-ATT-01` B 解）：① 权限（Android 13+ `READ_MEDIA_IMAGES` / `READ_MEDIA_VISUAL_USER_SELECTED` 或 Photo Picker 受限访问，**均未取证**）；② 缩略图生成/缓存/分页（无参考先例）；③ 开发量显著高于"面板＋系统 picker"；④ 与系统 picker 功能重叠。**默认建议不做**（→ §4），保留为候选（§5） | ⏸（Q-ATT-01） |
| `attachment.intake.limits` | 三路汇入 `intakeFiles`：子代理 continuable 只收图片；图片限制来自 `imageLimits`（数量/单文件/总量/媒体类型），超限**整批拒绝**＋toast；通用文件无客户端限制；提示只在拖放遮罩（`InputBar.tsx:206-239,414-417`） | 「数量上限由状态层」（`composer.attach` 字段 10）——客户端不写死常量 | 处理失败→notice→Toast；权限拒绝→Snackbar；二进制 `BinaryFileSniffer`（`composer.attach-sheet` 字段 10） | 校验顺序 格式→大小→数量，任一不满足**整批不添加**；配额 toast（`svc/attachments.ts:50-62,93-103`） | 后端上限 图片 32MB／其它 8MB（`AS:24-25`）；保存失败才报错误条；无数量/类型/总量校验 | `C-M5-14`（限制来源）／`C-M5-11`（通用文件限制/白名单） | ① **单一真源＝后端/状态层**，客户端不写常量（`C-M5-14`）：DSH 来自 Host 投影、dsh-Go 明确"上限由状态层"、旧 myco 只有后端单文件上限——三家都不在客户端写死表，只有 chat-demo 是常量；② **整批拒绝**（DSH/chat-demo 一致；反证：部分接收会让用户以为全加上了）；③ **通用文件也前置校验**（用后端已知 8MB；反证：不校验则传完才失败 `MA:805`）；④ **不做扩展名白名单**（`C-M5-11`）；⑤ 提示：面板内显示限制＋超限 toast（手机无拖放路径） | 📌／⏸（上限值口径 Q-ATT-03） |
| `attachment.state.item` | 文件项三态 `uploading`（spinner＋进度条）/`ready`（图标＋扩展名＋大小）/`error`（红描边＋失败文案）；`uploadsPending` 时改主按钮语义＋Enter toast（`dsh-settings.md §6.3`；`FileCard.tsx:19-81`） | 选中即进 tray；**处理中不可发送且显示进度**（验收 `TX-ATTACH-02`）；移除后 tray 收起、草稿保留 | `attachmentsProcessing` 期间输入区顶部 14dp 转圈＋"处理中"，不可发送（`composer.input` 字段 10③）；失败→Toast | 全部上传成功前不能发送（`drafts.ts:96-118`）；失败按 `retryable` 分级 | 同步落盘：无中间态，发送即带 `attachments`（`MA:962-968`、`MA:944`） | `C-M5-12`（落盘/处理模型） | **采用"处理中/就绪/失败"三态＋发送门禁**，但**按本地处理语义命名**（`C-M5-12`）：myco 的 `AttachmentSave` 是本地落盘（`MA:797-804`、`IB:136-149`），不存在 DSH 的"上传到 Host"长传输；移动端参考（dsh-Go/太墟）显示的也是"处理中"而非进度。**状态机归本表**（states/转移/门禁），**呈现（卡片/图标/转圈/按钮）归 composer 渲染**（§6.3 #1）。反证＝若允许在"处理中"发送，用户会得到一条缺附件的消息且无从补救（4/4 参考一致禁止） | ⏸（随 Q-ATT-02） |
| `attachment.channel.process` | 浏览器 Blob 走后台 Web Worker（XHR 进度 / fetch duplex 流式）；`Uint8Array` 走 Remote `fileUploads.upload`（base64）；服务层支持 `AbortSignal`，**UI 未暴露取消**；失败可重试（`runtime.ts:79-152,191-221,240-298`） | 未取证（无进度条/上传通道证据） | VM 做"复制/压缩/编码"，处理中只转圈、无进度（字段 5/6） | 3 并发；删除即 `cancelUpload`；`retryable` 分级（网络/限流/5xx 可重试，格式/过大不可重试） | 单条 `AttachmentSave`（base64→本地落盘）+ 失败错误条；无重试入口（`MA:805`） | `C-M5-10`（取消上传） | **按"本地处理/落盘"定义通道**：① 进度只在能拿到真实 total 时显示（否则转圈，反证：假进度比没有更糟）；② **不做显式"取消上传"按钮**（→ §4 `C-M5-10`）：本地写盘没有可取消的长传输，移除（×）即丢弃（dsh-Go/太墟 同）；③ **失败可重试**，并采 chat-demo 的 `retryable` 分级（反证：把"格式/过大"也做重试，用户反复点必然失败的动作）；④ 并发：首版串行（→ §4）。若 P4 改远端/分块上传，再借 DSH 的 `AbortSignal` | 📌（通道口径）／✅（不做取消） |
| `attachment.msg.image` | 历史/轨迹/工具卡**同一渲染器**：单图长边 240＋宽高比 clamp；多图 64 tile；列表 48；挂载即 `load(attachment)`；loading `aria-busy`；**失败＝整块重试按钮**；成功点击开 `ImageLightbox`；无尺寸按 240×240（`MessageImage.tsx:45-192`） | 缩略按列宽等比＋`maxHeight`；**单击＝全屏查看器**（黑底＋顶部返回/动作＋底部计数）；**双击/双指缩放＋平移**；**同组多图横滑图库**（`PageScrollPhysics`，缩放时切 `NeverScrollable` 禁翻页）；**失败＝占位＋重载**；长按＝图片菜单（`chat.msg.image` 字段 1-10） | 用户气泡内 `ImageThumbnail` 缩略图行（`ChatMessageBubbles.kt:335-369`）；全屏查看未取证 | 点击可**开外链**；补传失败点整卡重试；导入历史无原件只显示文件信息（`M/UserMessage.tsx:64-137`） | **未取证**（`legacy-myco.md` 无此节点）→ §6.2 | `C-M5-09`（点击后的查看形态） | 采用**应用内全屏查看器**（dsh-Go 是手机端最完整答案：缩放＋图库＋失败重载）；**不用外链**（chat-demo）——反证＝myco 附件落本地（`AS`），没有可跳 URL，跳浏览器还丢会话上下文。**尺寸预算**照 DSH（反证：不给预算则图片加载完撑开消息、正文被推走；myco 需后端给宽高，§6.1 #4）。**缩放态禁翻页**＝G-04 正向实现（手势归属唯一） | ⏸（旧现状未取证 + Q-ATT-04）／其余 📌 |
| `attachment.msg.file` | **未取证**（历史侧只登记图片；`kind=file` 只送元数据 `IB:533`） | 未取证 | 未取证 | 文档卡（类型图标＋文件名/大小＋失败态＋重试图标）（`M/MessageFile.tsx:28-135`） | **未取证**（同上） | — | **最小形态**：类型图标＋文件名＋大小（照 chat-demo 度量），**点击不设行为**（DSH/太墟 无先例；chat-demo 是外链）。反证/依据：`kind=file` 只送元数据（`IB:533`），历史里没有可预览内容；点开"下载/预览"需要后端读附件能力（§6.1 #1）。待拍：是否显示状态、点击行为 → `Q-ATT-04` | ⏸（Q-ATT-04） |
| `attachment.orphan-cleanup` | **未取证**（移除只丢弃 composer 状态 id；Host 是否清理未取证） | 未取证 | 未取证 | 本地 IndexedDB（未见清理策略） | ⚠️ 只改内存；只有永久删会话清附件目录（`SS:262-268`）→ M-03/D12 | `C-M5-12`／`C-M5-13` | **新增节点（§5）**：需要"删除附件"命令＋两个清理触发点（移除/替换时立即删；启动或会话结束时扫未引用）。反证＝M-03 已证实：存储只增不减、用户无入口清理；P4 前置后端契约（§6.1 #2/#10） | ⏸（Q-ATT-02） |

> **新冲突索引（`C-M5-09…14`，均未登记；建议 Lead 顺延登记为 C-20 起）**：
> `09` 图片点击后的查看形态：外链（chat-demo）vs 应用内灯箱（DSH `ImageLightbox`）vs 全屏查看器含缩放/图库（dsh-Go）｜
> `10` 取消上传：无按钮（DSH）vs 删除即取消（chat-demo）vs 只转圈（dsh-Go/太墟）｜
> `11` 通用文件客户端限制：无（DSH）vs 有（chat-demo 64MB/32MB/50＋扩展名白名单）｜
> `12` 落盘/处理模型：同步落盘无状态（旧 `MA:790-817`）vs 上传三态＋进度（DSH）vs 处理中转圈（dsh-Go/太墟）｜
> `13` 移除是否删已落盘文件：不删（旧 M-03/D12）vs 删（本表建议，需删除命令）｜
> `14` 限制来源：Host 投影（DSH）vs 客户端常量（chat-demo）vs 状态层（dsh-Go）＋后端单文件上限（旧 `AS:24-25`）。

---

## 3. 补充项（不互斥的差异；标 `→composer` 的是交接给 M4 的硬约束）

| 节点 ID | 谁有、谁没有 | 是否采纳 |
|---|---|---|
| `attachment.panel.sources`（tile 网格 vs 纵向按钮） | dsh-Go tile 60dp 网格 4/行；太墟 网格＋扩展＋紧凑行；旧 myco 纵向 TextButton | ✅ 采纳 tile 网格（2/2 有面板的都这么做；反证：纵向三按钮不像媒体入口，来源变多要滚） |
| `attachment.panel.sources`（面板内额外入口层） | dsh-Go：插件工具格／「本对话」卡／「手机操控」卡／生成模式；太墟：扩展入口／草图抽屉 | ❌ 不做（→ §4）：借附件面板当"更多入口"会变成大杂烩（dsh-Go 自己的反证也承认"一级菜单 8+ 项误触率上升"） |
| `attachment.source.file`（多选） | DSH `multiple`；dsh-Go/太墟 系统 picker 多选；旧单张 | ✅ 采纳多选（反证见该行） |
| `attachment.source.file`（相机/相册系统 picker 的具体实现） | dsh-Go `image_picker`／`file_picker`；太墟 Photo Picker；旧 myco `PickVisualMedia`/`OpenDocument`/FileProvider | ✅ 采纳旧 myco 已有实现（等价能力，无需新依赖） |
| `attachment.intake.limits`（整批拒绝＋toast） | DSH、chat-demo 一致 | ✅ 采纳（反证：部分接收会让用户以为全加上了；旧 myco 逐个保存会部分成功） |
| `attachment.intake.limits`（超限提示位置） | DSH 只在拖放遮罩；chat-demo 只有超限 toast；旧 myco 事后错误条 | ✅ 采纳"面板内显示限制＋超限 toast"（手机无拖放路径） |
| `attachment.state.item`（进度未知时 indeterminate） | DSH 有（无 total 时 1.2s 往返）；chat-demo 只有 spinner 蒙层 | ✅ 采纳（但本地落盘通常没有 total → 见 `attachment.channel.process`） |
| `attachment.channel.process`（重试分级） | chat-demo 有（`svc/attachments.ts:189-206`）；DSH 单态；太墟 只给 Toast | ✅ 采纳 chat-demo 分级（不互斥的增强） |
| `attachment.channel.process`（上传并发 3） | 只有 chat-demo（`svc/attachments.ts:21-22,105-145`） | ❌ 首版串行（反证：单条 Bridge Command 下并发会让处理/失败/顺序语义复杂一倍；"一次选 3 张大图"非主路径） |
| `attachment.msg.image`（同组多图横滑图库） | dsh-Go 有；DSH 逐张打开；chat-demo 无 | ✅ 采纳 dsh-Go（多图逐张开关灯箱成本高；缩放时禁翻页＝G-04 手势归属一致） |
| `attachment.msg.image`（长按图片动作菜单：保存/分享/复制、保存到相册） | dsh-Go 有（`media_card.dart:622-759`）；DSH/太墟/chat-demo 无 | ❌ 首版不做（→ §4）：系统媒体能力（写相册权限/分享 Intent）超出本面板；且长按需与列表滚动/查看器手势仲裁 |
| `→composer`：草稿轨高度 | DSH 64（图片 64）；dsh-Go 56；太墟 48dp 级缩略图 | 建议 **64dp**（缩略清晰度优先；56/48 可作大字号/横屏降级）。**呈现归 composer**，本表只给量值 |
| `→composer`：轨出现/消失与空态 | dsh-Go `ExpandReveal` 280ms 且"玻璃长高、文字行不跳"；太墟 预览行在输入胶囊上方、占位但"只增高不移动按钮"；chat-demo 出现/消失让输入卡高度突变（缺陷） | 建议：展开动画＋按钮位置不变＋空轨不渲染（反证＝chat-demo 的真实缺陷：正在打的字被推走） |
| `→composer`：轨新增项定位 | DSH 新增自动滚到末端、挂载时保持原起点；dsh-Go/太墟 无证据 | 建议采纳 DSH（反证：不滚则看不到刚加的那张，以为没加上） |
| `→composer`：缩略图点击＝预览、长按＝动作菜单 | dsh-Go 单击预览/长按菜单；DSH 单击开灯箱；太墟 长按预览（E2 未核实） | 建议：**单击预览**（触发归 composer，查看器归本表）；长按菜单不做（§4） |
| `→composer`：粘贴与长文转附件的接入点 | DSH/chat-demo 粘贴文件入轨；太墟 长文本粘贴→转附件（`PasteLongTextReceiver`） | 接入点归 composer；"长文本转附件"属输入策略（太墟有、DSH/chat-demo 无），建议 M4 单列，不在本面板 |
| 旧 myco 特色：相机拍照、`AttachmentSave` 即得 `kind=image/text/file` 判定 | 旧有（`MA:852-861`、`AS:23-38`） | ✅ 保留相机；✅ 采纳后端 kind 判定（客户端不复制判定逻辑，与 `C-M5-11` 同源） |

---

## 4. 明确不做（参考里有的）

| 能力 | 出处 | 不做的理由 |
|---|---|---|
| **应用内相册网格 / 最近照片浏览器（默认不做）** | 无参考实现（dsh-Go→`image_picker`；太墟→Photo Picker；DSH→文件对话框）；旧 myco→系统相册 | 三家都把选图交给系统 picker，无一家自建网格；自建需媒体权限＋分页＋缩略图缓存＋可观开发量，且与系统 picker 功能重叠。**"内建 UI"需求由 `attachment.panel.sources` 满足**（3 家先例）。→ 若用户坚持，按 §5 我方设计＋`Q-ATT-01` 的代价项拍板（**不是默默丢**） |
| 桌面级全屏拖放遮罩（DropOverlay／DragMask：插画＋blur＋fade＋限制行＋dropEffect） | DSH `composer.attach.drop-target`；chat-demo 2.4 | 桌面窗口级拖放的视觉语言；**dsh-Go／太墟 移动端均未见拖放**（0/2）。手机单窗口无"从文件管理器拖进窗口"主路径（G-00：不用桌面结论推移动端） |
| 显式"取消上传"按钮 | DSH 服务层有 `AbortSignal` 但 UI 无按钮；chat-demo 删除即取消；dsh-Go/太墟 处理中只转圈 | myco 是**本地处理/落盘**，没有可取消的长传输；移除（×）即丢弃（dsh-Go/太墟 同）。只有 P4 改远端/分块上传时才需要（届时借 DSH 的 AbortSignal） |
| 轨上"竖直滚轮转横向滚动" | `AttachmentRail.tsx:100-127` | 手机无滚轮/触控板；触摸横向 pan 是原生能力（DSH 自己注明触摸走原生 pan）。**呈现归 composer**，本表只登记"不做" |
| 扩展名白名单（986 行清单，含 `user_data` 分类） | chat-demo 2.4 字段 10①（`svc/uploadExts.ts`） | 与 myco 后端"任意 file＋kind 判定"（`AS:23-38`）不同源：会拒掉后端能收的文件，且清单维护是长期负担（`C-M5-11`） |
| 上传并发 3 队列 | chat-demo `svc/attachments.ts:21-22,105-145` | 见 §3：首版串行（单 Bridge Command，并发语义无处安放） |
| dsh-Go 面板里的非附件能力：插件工具格、四档权限页、生成模式/生成 sheet、「本对话」卡、「手机操控」卡、语音通话入口 | `dsh-go.md §2.3 attachment.panel` 字段 1/4/5 | 它们借附件面板当"更多入口"层，归属其它面板（权限/审批、生成、手机操控）；塞进来会让面板变成大杂烩、每个能力各写一套入口 |
| 太墟附件抽屉的"扩展入口 / 草图抽屉（`ChatSketchSheet`）" | `taixu.md §2.4 composer.attach-sheet` 字段 2/4 | 草图/绘图不在"导入媒体"需求内；扩展入口属插件系统（myco 首版无插件面板） |
| 视频附件的「首帧/尾帧」标签 | dsh-Go `composer.attach` 字段 2 | 视频附件未在 myco 首版需求内；若做，需视频抽帧能力（未取证）。**呈现归 composer**，本表登记"不做" |
| 长按图片动作菜单（保存/分享/复制）、保存到相册 | dsh-Go `chat.msg.image` 字段 3/5 | 系统媒体能力（写相册权限/分享 Intent）超出本面板；需与滚动/查看器手势仲裁。首版不做（§3 已登记） |
| DSH 的「子代理 continuable 会话只收图片」限制 | `InputBar.tsx:216-219` | myco core 没有子代理/continuable 概念（legacy-myco §3 缺口 9），限制无对应主体 |
| 引用角标 / 右侧来源面板 | chat-demo 2.14/2.15 | 不属本面板（归 `chat`/M3）；§6.3 已登记交接 |

---

## 5. 新增节点（参考里都没有、myco 要新设计）

| 节点 ID | 为什么需要 | 依据（用户要求 / 平台规范 / 我方约束）＋**代价** |
|---|---|---|
| `attachment.source.grid`（条件节点） | 用户点名"缺内建选择器/最近照片"（`bugs.md` M-02、`C-09`）。**但三家参考（DSH/dsh-Go/太墟）都不做应用内相册网格**——这是真正没有参考可抄的我方设计 | **我方设计（🆕 无参考实现）**：面板内"最近照片"网格（多选/缩略图/分页）。**代价**：① 权限（Android 13+ 媒体权限或 Photo Picker 受限访问，均未取证）；② 缩略图生成＋缓存＋分页（无先例、需自定尺寸/策略）；③ 开发量显著大于"面板＋系统 picker"；④ 与系统 picker 功能重叠，需回答"为何不让系统做"。**仅在 `Q-ATT-01` 选 B 时实现** |
| `attachment.orphan-cleanup` | 移除/替换后清理已落盘文件；启动或会话结束时扫未引用文件 | **我方约束**：M-03/D12（`AttachmentStore` 无删除 API `AS:23-71`；移除只改内存；只有永久删会话清目录 `SS:262-268`）。P4 前置后端契约（§6.1 #2/#10） |

---

## 6. 待确认（阻塞项）

> 编号 `Q-ATT-nn`；Lead 汇总后并入 `open-questions.md`。与已有编号交叉的已注明（`Q-CMP-02`/`Q-CMP-09`、`Q-MOB-07`、`Q-SET-09/11/12/13`）。

| ID | 问题 | 两解及各自后果 | 建议 |
|---|---|---|---|
| `Q-ATT-01` | **内建选择器交付到哪一层？**（与 `dsh-mobile §6 Q-MOB-07` 同题，建议合并） | **A. 内建面板＋系统 picker**（相册/拍照/文件三源 → 系统 `image_picker`/Photo Picker/文件选择器）＝dsh-Go、太墟 的形态。后果：有 3 家先例；无媒体权限、无相册性能问题；"最近照片"体验由系统决定。代价低。<br>**B. 面板内嵌"最近照片"网格（🆕 我方设计，无参考可抄）**。后果：真正内建到像素；代价＝媒体权限（未取证）＋缩略图/分页/缓存自研＋开发量＋与系统 picker 重叠。 | **A（首版）**，B 作为后续增强（B 的全部代价已写进 §5）。理由：A 就是"用户点名缺的那层内建 UI"，且有 3 家移动端参考；B 没有任何先例，四项代价全部未验证。**若用户要 B，则本项升级为 P4 前置**（先给权限与缩略图方案） |
| `Q-ATT-02` | **落盘时机与处理通道：选中即处理/落盘，还是发送时才落盘？**（`C-M5-12`、`C-M5-13`、`orphan-cleanup` 共同依赖） | **A. 选中即处理/落盘**（旧 `MA:790-817`；太墟"选完→VM 处理（复制/压缩/编码）"；dsh-Go 选中即进 tray），移除/替换时**删已落盘文件**（需新增删除命令）。后果：id 稳定、可预览、可重试、有"处理中"状态；代价＝后端要补删除命令＋处理状态回执（§6.1 #2/#3）。<br>**B. 发送时才落盘**（内存/临时文件；`Q-LEG-06` 备选）。后果：无需删除命令、无孤儿；代价＝发送瞬间处理大文件（大图卡顿）、此前无处理结果可见、失败要重来。 | **A**。理由：① 旧 myco 已是 A（只差删除命令）；② dsh-Go/太墟 都是"选中即可见、可移除"；③ 用户能尽早发现格式/大小错误（chat-demo 字段 13 反证："发送时统一传"会让用户长时间无反馈）。A 的前置＝后端 #1/#2/#3（读/删/处理状态） |
| `Q-ATT-03` | **限制的来源与范围**：图片数量/单文件/总量/类型从哪来？通用文件是否前置限制？myco 后端有没有"张数/总量"上限？ | 事实：DSH 来自 Host `imageLimits`；dsh-Go「数量上限由状态层」；chat-demo 全客户端常量；旧 myco 只有后端单文件 32MB/8MB。<br>**A. 全部来自后端/状态层投影**（客户端只渲染）：单一真源、可调；代价＝要一条配置 Query（§6.1 #6）＋先定义"张数/总量"（myco 后端未见，§6.1 #7）。<br>**B. 客户端常量**（chat-demo）：无需后端；代价＝改限制要发版、与后端不同源时"客户端过了后端拒"。 | **A**，并明确：① 图片四类限制走后端投影；② **通用文件也前置校验**（后端已知 8MB；反证：不校验则传完才失败 `MA:805`）；③ **不做扩展名白名单**（`C-M5-11`）。**需同时拍**：后端要不要补"张数/单消息总量"两项（否则 A 的"数量"无数据源） |
| `Q-ATT-04` | **历史消息里的附件怎么展示、点什么？**（`C-M5-09`；含旧 myco 现状未取证） | **A. 图片＝应用内全屏查看器**（dsh-Go：缩放＋同组横滑＋失败重载；DSH `ImageLightbox` 提供焦点陷阱/回焦规范）；文件＝信息卡不可点。后果：离线可看、不丢会话；代价＝需要本地可读路径（§6.1 #1）＋触摸关闭手势（点遮罩/返回键；下滑未取证）。<br>**B. 图片＝开外链**（chat-demo）。后果：无需本地读附件；代价＝myco 附件落本地、**没有可跳 URL**；跳浏览器丢会话上下文；离线不可用。 | **A**（B 在 myco 的附件模型下不可实现）。**需同时拍**：① 文件卡是否显示状态；② 查看器是否做缩放（建议做，dsh-Go 先例）；③ 工具卡图片是否同渲染器（DSH 同渲染器，建议同；反证＝两套渲染器会在尺寸预算/失败重试上分叉）；④ 旧 myco 历史消息附件是 P1 缺口（§6.2 #1），需补取证才能写 P3 验收 |

> **本面板阻塞项：4 条**（`Q-ATT-01…04`）。
> 已与 M4 对齐（不阻塞）：`+`＝附件入口（`Q-CMP-02` 已定，本表不另提常驻"相机/相册"按钮、不提双入口）；草稿轨/缩略图/移除/重试呈现归 composer（`Q-CMP-09` 分割线见 §6.3 #1）。

### 6.1 后端缺口清单（P4 前置；任务书第 8 点）

> 结论先说：**预览与状态机不是"画界面"就能做**——`bugs.md` M-02 已定「Bridge 只有 `AttachmentSave`，无读/删附件命令」（`BR:119-144`；`AS:23-71` 无删除）。`缺`＝必须新增契约；`未取证`＝存在性不明；`已有`＝不用补。

| # | 需要的能力 | 现状（E0） | 服务哪些节点 | 状态 |
|---|---|---|---|---|
| 1 | **读附件**（按 attachmentId 拿到本地可读路径/授权 URI，供 UI 预览） | `BR:119-144` 只有 `AttachmentSave`；`AS:23-71` 只有 save/read/textContent/exists，ref 明确"无面向 UI 的读命令/Query" | `attachment.msg.image`、草稿预览（composer 渲染） | **缺**（Query） |
| 2 | **删附件**（移除/替换时删已落盘文件） | `AS` 无删除 API；移除只改内存（`MA:1230,1235`）；只有永久删会话清附件目录（`SS:262-268`） | `attachment.orphan-cleanup`、composer 的移除按钮（语义在本表） | **缺**（Command） |
| 3 | **处理/保存状态回执 +（可选）可中断** | `AttachmentSave` 是一次性 Command 携带 base64（`MA:797-804`、`IB:136-149`），无进度/阶段事件；DSH 的进度/abort 属"上传到 Host"模型（`runtime.ts:79-152,240-298`），本地落盘无对应 | `attachment.state.item`、`attachment.channel.process` | **缺**（至少要有"处理中→完成/失败"阶段回执；进度/取消可后置） |
| 4 | **附件元数据回执**（id/size/mime/**宽高**） | `AttachmentSave` 回执字段未取证；历史图片按比例预算必须有宽高（DSH `MessageImage.tsx:45-62` 用 dimensions） | `attachment.msg.image`、`attachment.state.item` | **未取证 → 需定义** |
| 5 | **历史消息里的附件引用** | `ChatSend(attachments)` 有（`MA:962-968`）；`kind=file` 发送时只送元数据（`IB:533`）；事件侧附件引用字段未取证 | `attachment.msg.image`、`attachment.msg.file` | **未取证 → 需定义** |
| 6 | **限制值的配置投影**（图片数量/单文件/总量/媒体类型；通用文件上限） | 后端只有固定 32MB/8MB（`AS:24-25`），无 Query 下发；DSH 由 Host `imageLimits`、dsh-Go「上限由状态层」 | `attachment.intake.limits`、`attachment.panel.sources` | **缺**（配置 Query） |
| 7 | **图片数量与单消息总大小限制的定义** | myco 后端未见这两项（未取证）；DSH 有 Host 投影 | `attachment.intake.limits` | **未取证 → 需定义** |
| 8 | **系统选择器接入**（文件/相册/相机） | myco 现有 `OpenDocument`/`PickVisualMedia`/FileProvider 相机（`MA:819-861`）；等价于 dsh-Go 的 `file_picker`/`image_picker` | `attachment.source.file`、`attachment.source.camera`、`attachment.panel.sources` | **已有** |
| 9 | **相机临时文件** | FileProvider 仅暴露 `cache/camera/`（`xml/file_paths.xml`，legacy-myco §1.2） | `attachment.source.camera` | **已有** |
| 10 | **孤儿清理的触发点与策略** | 无（只有永久删会话时清目录，`SS:262-268`） | `attachment.orphan-cleanup` | **缺**（Command＋策略） |

> 计数：**10 条 P4 前置依赖**——`缺` 5 条（#1/#2/#3/#6/#10）、`未取证/需定义` 3 条（#4/#5/#7）、`已有` 2 条（#8/#9）。
> **硬阻塞**：#1（没读附件就不能预览/历史图）、#2（没删附件就修不掉 M-03）、#3（没处理状态就无"处理中/失败"，发送门禁无依据）。

### 6.2 未取证清单（G-00：ref 没有 = 不许猜）

| # | 未取证项 | 影响 | 处理 |
|---|---|---|---|
| 1 | 旧 myco 历史消息里的图片/附件渲染 | `attachment.msg.image`/`msg.file` 的旧现状无法写；P3 验收缺现状基线 | `legacy-myco.md` 未登记该节点 → P1 补一条，或 P4 直接按参考新建 |
| 2 | DSH/dsh-Go/太墟 历史消息通用文件（`kind=file`）的渲染 | `attachment.msg.file` 无参考先例 | 三份 ref 历史侧都只登记图片；`IB:533` 只证"只送元数据" |
| 3 | dsh-Go／太墟 附件是否上传到远端（附件数据模型） | `C-M5-12` 只能按"本地处理"定 | 两 ref 只登记 tray/处理中，无上传通道节点 |
| 4 | 太墟全屏图片查看能力（长按预览 E2 未核实） | `C-M5-09` 里太墟只能算"缩略图"一票 | `taixu.md` 明确标 E2；需真机或源码补 |
| 5 | Android 媒体权限/剪贴板在目标系统版本上的行为 | `Q-ATT-01` B 解与粘贴的可行性 | 需 P4 真机验证（不得用"一般 Android 都这样"代替，E2） |
| 6 | dsh-Go「数量上限由状态层」的具体值 | `Q-ATT-03` 的上限口径 | 未给数值 |
| 7 | DSH 移除附件后 Host 是否清理已上传文件 | `attachment.orphan-cleanup` 无法与参考对比 | `ComposerAttachments.tsx:56-71` 只证 composer 状态丢弃 id |
| 8 | dsh-Go 面板内「生成模式/插件工具格」是否算附件能力 | §4「不做」的边界是否成立 | 需 dsh-Go 侧再确认归属（本表按"非附件"处理并写明理由） |

### 6.3 跨面板与文档对齐（我不改这些文件）

| # | 归属 | 冲突/重叠 | 处理建议 |
|---|---|---|---|
| 1 | `composer`（M4，已产出 `composer.md`） | **归属重叠**：M4 把「草稿附件轨（rail/thumb/file-card/remove/file-retry）、轨道横滚与新增滚末端、灯箱触发」列为 composer 拥有，并登记 `Q-CMP-09` 要求 M5 确认分割线 | **按 Lead 已定切分执行，本表已重构**：本表 §1 **不再定义**轨/缩略图/文件卡/移除/重试节点（只在 §3 给 `→composer` 的硬约束与证据）；attachment 只保留"来源面板/三源、intake、状态机、处理通道、内建选择器、历史消息、孤儿清理"。回答 `Q-CMP-09`：① composer 拥有轨壳（位置/高度 64dp/出现消失动画/横滚/滚到末端/空轨不渲染/高度发布给 chat）；② attachment 拥有项级**语义**（状态机、通道、删除、重试分级）；③ `+` 接线归 composer，面板归 attachment。两边互相引用 ID、不重复描述行为（G-00b） |
| 2 | `composer`（M4） | M4 采用 DSH 三态 `uploading/ready/error`（"在传"）；本表按本地落盘建议"处理中/就绪/失败"（`C-M5-12`） | 前提不同（远端上传 vs 本地落盘）。`Q-ATT-02` 选 A 后统一措辞：composer 的"上传中/在传"改为"处理中"，进度条只在有真实 total 时出现（本表 `channel.process`）；"取消上传"本表结论＝不做（§4 `C-M5-10`），可关闭 `Q-CMP-09` 的该半边 |
| 3 | `composer`（M4） | `+` 语义：M4 `Q-CMP-02` 已定「`+`＝附件入口」（4/5 多数派，与本表一致） | **无冲突**：本表不另提常驻"相机/相册"按钮、不提双入口（Lead 明确要求）；若 M5 将来要常驻双入口，须新开阻塞项并注明"与 Q-CMP-02 一起拍" |
| 4 | `chat`（M3） | 引用角标 → 来源面板（chat-demo 2.14/2.15，DSH 未取证）被任务书列为本面板必读 | 归 `chat`；§4 已登记 |
| 5 | `p2/_plan.md` | 原定输出 `p2/model-attachment.md`，本次拆为 `p2/model.md` + `p2/attachment.md` | Lead 更新 `_plan.md` 输出列 |
| 6 | `dsh-settings.md §4` / `dsh-mobile.md §6` | `Q-SET-09/11/12/13`、`Q-MOB-07` 与本表 `Q-ATT-01/03` 重叠 | 合并编号，保留一处权威 |
| 7 | `docs/spec/ref/` | `dsh-go.md`、`taixu.md` 在本次合并过程中才落库（时间戳晚于任务启动），此前 `_plan.md` 的"P1 十份事实库全部完成"与实际不同步 | Lead 确认 ref 已齐；本表已按 E0 填列。**并请 Lead 更新一处前提**："内建选择器无参考实现"需拆开说——**面板层**有 dsh-Go/太墟 先例；**应用内相册网格**才是无先例的我方设计（§5 已标 🆕 并写代价） |

---

## 7. 自检

- [x] 每个节点在对照表里有一行（10 个节点 → 10 行；条件节点 `attachment.source.grid` 同时在 §5 以"我方设计＋代价"登记）
- [x] **与 composer 边界不重复**：轨/缩略图/文件卡/移除/重试/`+` 接线/灯箱触发归 composer，本表只给 `→composer` 硬约束（§3）与切分说明（§6.3 #1）
- [x] 每条「建议」都引了 G 规则或给了反证（G-03 形态 C、G-04 手势归属、G-08 同族、G-00b 归属；其余为 `文件:行` 反证；无主观化表述）
- [x] 没有 E2 推测出现在 §2/§3（缺失处写「未取证」，太墟 E2 处按原标注引用，集中在 §6.2）
- [x] 明确不做项都写了理由（§4 共 12 条，含应用内相册网格、拖放遮罩、显式取消上传）
- [x] 内建选择器/网格标明"我方设计（🆕 无参考实现）"并写清四项代价（§5）；未伪装成参考做法
- [x] 阻塞项已编号（`Q-ATT-01…04`，共 4 条）
- [x] 一行一节点；仅互斥项进冲突列（`C-09`＋`C-M5-09…14`）
- [x] 后端缺口已逐条列成表（§6.1，10 条，标注 缺/未取证/已有）
- [x] 未修改 `docs/spec/ref/**`、`00-global-rules.md`、`_conflicts.md`、`_merge-*.md`、`p2/_plan.md`；未改 app；未 git commit

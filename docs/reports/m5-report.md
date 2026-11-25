# M5 媒体附件与权限隐私报告（2026-11-26 03:35）

## 1. 结论

附件链路打通：**图片 → vision**、**文本文件 → 内联上下文**、**其他文件 → 仅元数据（诚实标注未解析）**。
Android 侧接系统 Photo Picker / 文件选择器 / 相机（FileProvider），附件只落应用私有目录，
发送时才进入模型上下文；真机验证了"相册选图 → chip → 发送 → 重启后历史仍显示附件"。

## 2. 数据与存储

- 新 `AttachmentRef(id,name,mime,size,kind)`，其中 kind ∈ image/text/file；
  **只进 user/message 事件，不写绝对路径**（同步友好）。
- `AttachmentStore`：`<home>/attachments/<sessionId>/<id>__<safeName>`；
  - 校验名/大小：图片 ≤32MB，其他 ≤8MB；
  - 读取时 normalize + 必须在会话目录下（防 `../` 与跨会话读取）；
  - 文本内容截断 12k 字符；不自动上传任何东西。
- schema 更新：`user/message.data.attachments`（fixtures 校验 23/23 通过）。

## 3. 模型上下文

- `ChatMessage.images: List<ChatImage>`（base64 + mediaType，仅内存，不落事件日志）。
- OpenAI：`content` 变 parts 数组，图片 `image_url = data:<mime>;base64,...`。
- Anthropic：`content` blocks，图片 `{type:image, source:{type:base64, media_type, data}}`。
- 文本附件：以 `[附件：name]\n<内容>` 内联进 user 文本；其他类型：`[附件：name（mime，size）未解析，仅元数据]`。
- 附件读失败 → 整轮 `ATTACHMENT_NOT_FOUND`，**不写半截 turn**。

## 4. Bridge / 传输 / CLI

- `Command.AttachmentSave(sessionId, name, mime, base64)` → 返回 ref；
- `Command.ChatSend` 增 `attachments: List<AttachmentRef>`；
- http-sse 同步支持（base64 仅传输层，事件里仍是 ref）；
- 桌面 CLI 新增 `chat-file <sessionId> <path> [text]`。

## 5. 真机（Android 15 / API 35）

- 输入区新增「＋」→ 底部面板（拍照 / 相册 / 文件）；
- 相册走系统 Photo Picker（无需存储权限）；文件走 OpenDocument；相机走 `TakePicture` + FileProvider
  （不申请 CAMERA 权限，交给系统相机；失败有明确错误）。
- 实测：相册选一张截图 → chip 显示文件名 → 发送 → 重启 App 后历史气泡显示「[附件] 文件名」。
- 文件选择器能打开，但推送到 `/sdcard/Download` 的 txt 没出现在 MIUI 文件选择器"最近"里；**未完成一次手工文件选择 e2e**。
- 相机：未自动化（会拉起外部相机 App），代码路径编译通过，未真机点过。

## 6. 真实模型验证（桌面，DeepSeek vision）

生成一张 800×400 图（文字 `MYCO VISION 42` + 右下深绿圆圈），用 `chat-file` 发给 `deepseek-flash`：

```
attached vision.png (image, 8752 bytes, image/png)
看图后回答：
  图上文字为：**"MYCO VISION 42"**……
  图形方面：背景是浅米白/奶油色……右下角有一个单独的细线圆圈，描边为深绿色……
turn/end steps=1 costUsd=0.001107
```

事件里 `user/message.attachments` 保留 id/name/mime/size/kind，没有绝对路径。

## 7. 测试

`:core:test` **64** + `:bridge-http:test` **6** = **70 绿**。
新增：附件存储 5 条（读写/分类/超限/跨会话/穿越）、Bridge 附件 3 条（文本内联+落盘、图片 vision、缺失拒绝），
两方言图片序列化各 1 条（data URL / base64 block）。

## 8. 已知缺口

1. 相机与文件选择未完成真机 e2e（相册已通）；需要再约一次手机窗口。
2. 图片不压缩直接 base64，大图会显著增加请求体（DeepSeek 单图 ≤32MB；聊天里应该先缩到 ~1300px，未做）。
3. 历史回放只显示附件文件名，不显示缩略图；点开/预览未做。
4. 附件不进 tool 上下文，也不做 OCR 自动识别（用户可显式调 ocr_image 工具）。
5. 音频/视频附件只按 file 处理（未接 ASR 自动转写）。

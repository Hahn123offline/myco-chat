# M5.3 聊天页样板 v1（2026-11-27）

> 入口：`adb shell am start -n dev.myco.android/.MainActivity --ez m5_chat true`（不影响正常 app）
> 设计契约：[90-m5-visual-system.md](../design/90-m5-visual-system.md)；材质原语见 [m5-0-gallery-report.md](m5-0-gallery-report.md)

## 1. 范围

静态数据样张，用来确认**视觉与间距**，不接真实事件流。四块：

| 区块 | 实现 |
|---|---|
| 顶栏 | 玻璃浮条：左菜单（Raised 36dp）、标题 17sp + 状态 caption、右主题切换/更多；无描边 |
| 用户消息 | blush 色 Float 气泡、右对齐、最大 300dp、彩色投影 |
| 助手消息 | 全宽正文 16/26、无气泡、meta 一行 caption（18.4s · 5 步） |
| 过程折叠 | Raised 胶囊（图标 + “思考过程 · 5 步 + 已阅读 3 个网页” + chevron），收起态只占一行 |
| 分支切换 | `‹ 1/2 ›` 低对比图标行，跟随消息右侧 |
| 流式 | 文本 + 2dp 圆头光标；光标按 `TextLayoutResult` 行尾定位（不再固定右侧） |
| 输入区 | 复用 `M5Composer`：Glass 浮岛、文本 ≥44dp、工具行 32dp、发送键 moss 32dp |

新增可复用组件：`M5Composer`（`ui/m5/M5Composer.kt`），后续真实聊天直接替换数据源即可。

## 2. 真机截图

浅色 / 深色各一张（见提交时的 `.myco-dev-e2e/chat-light2.png` / `chat-dark2.png`，不入库）。

## 3. 已知待确认

1. 消息节奏（turn 间距 18dp）是否合适；助手正文是否需要行内引用/代码块区域留下位置；
2. 过程胶囊的位置（现在在 meta 之后、分支切换之前）；
3. 顶部浮条是否要收窄/半透明程度；
4. 用户气泡粉色的饱和度在整屏里偏重还是刚好。

## 4. 下一步

- 按反馈微调本屏 → 接真实数据（事件流/会话树/流式）替换静态样张；
- 抽屉与设置重做（复用 primitives + `NeoSiblings`）；
- M5.2 面板壳（层叠 + 返回栈）在页面稳定后接入。

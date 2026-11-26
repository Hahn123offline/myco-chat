# 预测式导航：遮罩跟手 + 回收站退出 + 点击/拖拽同一套运动（2026-11-26）

本轮把"预测式返回"从两处手势扩展成一套可复用的导航退出机制，并把设置/回收站/抽屉都接进同一套运动。

## 1. 新增可复用接口（`ui/PredictiveNav.kt`）

- `PredictiveNavState`：一个 0..1 的 `progress`（Compose State），三个入口写同一个进度：
  1. 系统返回手势（`PredictiveBackHandler`）；
  2. 页面向右拖拽（`Modifier.swipeRightToExit`）；
  3. 点击返回按钮/系统返回键（`state.exit { commit }`：从当前进度补到 1 再切页）。
- `Modifier.swipeRightToExit(state, enabled, onCommit)`：横向占优才接管（消费事件，不影响竖向滚动），
  跟手写 progress；松手 >22% 或速度 >600px/s 完成退出，否则滑回。
- 这是给 M5 留的钩子：同一个 progress 可以继续叠加缩放/模糊/阴影/共享元素等动效。

## 2. 抽屉：遮罩跟手

- 抽屉遮罩不再用 Material3 的固定 scrim，改为 **自己画**：
  - `ModalNavigationDrawer(scrimColor = Transparent)` 保留其点击关闭行为；
  - 内容层上按 `alpha = 0.32 * openFraction * (1 - drawerNav.progress)` 画黑色遮罩；
  - `openFraction` 由 `drawerState.offset` 归一化（`1 - |offset|/drawerWidth`），所以**原生左右拖拽开/关抽屉时遮罩也跟着走**；
  - 预测式关闭时 `(1 - drawerNav.progress)` 让遮罩随抽屉滑出一起变亮。
- 真机验证：录屏 20fps，抽屉右侧遮罩区域亮度 169.7 → 250.1 随手势连续上升，无跳变。

## 3. 回收站/归档页：向右退出（预测式 + 点击 + 拖拽）

- `Screen.SessionBin` 现在叠在设置层上（设置作为露出层保持 compose）；
- 系统返回手势：`PredictiveBackHandler` → `binNav.progress` 驱动页面右移，完成回设置；
- 点击左上返回箭头：`binNav.exit { screen = Settings }`，同一套右滑动画；
- 页面右滑拖拽 >22%/快速甩动：同样退出回设置；
- 设置 → 回收站的进入仍是即时切换（进入动画不在本轮，M5 可统一做"推入"）。

## 4. 设置页：右滑不再打开会话列表

- 原因：`ModalNavigationDrawer(gesturesEnabled = true)` 的抽屉手势会拦截设置页的右滑；
- 现在 `gesturesEnabled = screen is Screen.Chat`：只有聊天窗口允许抽屉手势；
- 设置页右滑由 `swipeRightToExit` 接管 → 回聊天窗口（与动画暗示一致）。

## 5. 聊天窗口作为常驻基座

- 结构改为：聊天窗口始终 compose 在底层（当前会话或 `lastChatScreen`），设置/回收站作为覆盖层；
- 好处：设置页右滑露出的是**真实会话内容**；进入设置时正在流式生成也不销毁 ChatScreen，返回后 live 状态还在。

## 6. 常规退出与按键返回也走动画

- 设置/回收站左上返回箭头：`nav.exit` 动画（180ms）后再切页；
- 系统返回键：`PredictiveBackHandler` 的第一个事件 progress 直接到 1（无中间进度）时识别为"按键"，
  回落到 `nav.exit` 从 0 播放同一套退出动画（不会再"瞬移"）。

## 真机验证（7e7c5a68 / Android 15）

- 抽屉打开 → 预测式返回：抽屉左滑 + 遮罩连续变亮（抽帧亮度曲线 169.7→250.1）；
- 设置页右滑 400ms 快甩 → 回聊天窗口（不再打开会话列表）；慢拖跟手、松手滑回/完成均正常；
- 设置返回箭头 → 聊天窗口（右滑动画）；系统返回键在设置页 → 同一套右滑动画（录屏 `z0124–z0127`）；
- 回收站：从设置进入后，右滑/返回箭头/系统返回键都右滑回设置；
- 抽屉原生左滑关闭仍可用（遮罩跟随）；
- 无 FATAL；`:core:test` 86 + `:bridge-http:test` 7 + `:app-android:testDebugUnitTest` 7 全绿，Android/desktop 构建通过。

## 已知/后续

- 设置/回收站的"进入"仍是即时切换（没有推入动画），M5 可用同一套 progress 做反向入场；
- 抽屉预测式预览自身是 `graphicsLayer` 位移（抽屉真实 offset 未变），所以遮罩用相乘近似；
  若以后换自定义 drawer，可直接用真实 offset 驱动；
- 宽屏（≥600dp）下没有抽屉手势，设置/回收站右滑退出同样可用。

# M3 收尾：手势 / 返回 / 抽屉行为（2026-11-26）

> 注：本报告第 2 节"聊天根页返回=打开抽屉"的语义已被后续确认替换，
> 最新返回语义见 `docs/reports/back-semantics-report.md`（聊天窗口返回=回桌面；
> 预测动画只出现在"抽屉→聊天窗口"和"设置→聊天窗口"两处）。第 1、3 节仍有效。

M3 遗留的三项交互收尾，已全部实现并真机验证（7e7c5a68 / Android 15，暗色主题）。

## 1. 正文右滑开抽屉

- 新增 `Modifier.swipeRightToOpenDrawer(enabled, onOpen)`（`ChatContent.kt`），挂在聊天消息区
  （`ChatMessages` 的 modifier）上，不影响输入框、顶栏。
- 只观察不消费：在 `PointerEventPass.Final` 读取手指位置，竖向滚动（含过程组内部滚动）完全不受影响；
- 判定：横向位移 > 18dp 且明显压过纵向（1.5 倍），结束时累计右移 > 96dp 才触发一次；
- 用 `composed` + `rememberUpdatedState` 包裹，流式高频重组不会打断进行中的手势；
- 抽屉自身的左缘拖动（Material3 `ModalNavigationDrawer`）继续保留，两条路径都能开抽屉。

**真机验证**：草稿页从屏幕中部（x=300）向右滑 → 抽屉打开；随后竖向滑动聊天列表行为不变。

## 2. 预测式返回（PredictiveBack）

- 聊天根页原 `BackHandler` 换成 `PredictiveBackHandler`（`activity-compose 1.9.2`，manifest 已有
  `android:enableOnBackInvokedCallback="true"`）：
  - 手势进行中：`backPreview = progress`，内容整体轻微右移（最多 56dp）+ 缩小 2%，作为抽屉拉开的预览；
  - 手势取消：位移回 0；手势完成：真正 `drawerState.open()`，再清零预览；
- 抽屉打开时仍由 `BackHandler` 直接关闭；空态/设置/回收站各自原有返回逻辑不变。

**真机验证**：返回键（以及系统返回手势）在聊天根页会打开抽屉而不是退出 App。

已知：抽屉自带的左缘拖动与系统返回手势在左缘区域会互相竞争（当前先到先得，两条路径的结果都是"抽屉打开"，
行为一致但"预览位移"不一定每次可见）。彻底分离需要把抽屉手势关掉、只走预测式返回，留作后续体验项。

## 3. 单条归档 / 删除不再关抽屉

- 原逻辑：单条归档/删除无论是否是当前会话都会 `drawerState.close()`，整理列表时每次都要重新打开抽屉；
- 现在：
  - **非当前会话**：抽屉保持打开，列表原地刷新，底部 snackbar 提供"撤销"；
  - **当前会话**：才关闭抽屉并进入新草稿（内容已不存在，合理）。
- 批量归档/删除逻辑不变（本来就应关闭抽屉）。

**真机验证**：
- 长按「多语言问候」→ 归档：条目消失、抽屉保持打开、撤销提示出现；
- 长按「DeepSeek 最新动态与质数求和」→ 删除 → 确认：条目消失、抽屉保持打开、撤销提示出现。

已知：抽屉打开时 snackbar 在右侧内容区，会被抽屉盖住一部分（只露出"撤销"按钮），属观感问题，
后续可以把 snackbar 提到抽屉层之上或改成抽屉内提示。

## 回归

- `:core:test` 86 + `:bridge-http:test` 7 + `:app-android:testDebugUnitTest` 7 = 100 绿；
- `:app-android:assembleDebug`、`:app-desktop:installDist` 通过；
- 本轮无 FATAL（`adb logcat -b crash` 为空）。

# 返回语义整改：预测式返回只出现在两处（2026-11-26）

用户确认的新语义（替换 M3 里"聊天根页返回打开抽屉"的旧逻辑）：

```
聊天窗口 → 多会话(抽屉) → 设置 → 聊天窗口
```

- 聊天窗口是根：在聊天窗口返回 = 退出 App 回桌面；
- 多会话（抽屉）打开时返回 = 预测式关闭抽屉，回到聊天窗口；
- 设置页返回 = 直接回到"进入设置前那个活跃会话窗口"，设置页向右滑出；
- 预测式动画只出现在 **两处**：会话管理→聊天窗口、设置→聊天窗口；
- 设置返回时若原活跃会话已不存在（被删/归档），落到新草稿会话。

## 实现（`MainActivity.kt`）

### 1. 聊天窗口返回 = 回桌面

- 删除了聊天根页的 `PredictiveBackHandler`（原来会打开抽屉）和 `screen == null` 的返回拦截；
- 聊天根页不再注册任何返回回调 → 系统执行默认返回，**系统自带预测式退场动画**（App 缩小/滑回桌面）。

### 2. 会话管理（抽屉）→ 聊天窗口（第 1 次预测动画）

- `PredictiveBackHandler(enabled = drawerState.isOpen)`：手势进度写 `backPreview`；
- 抽屉组件用 `graphicsLayer { translationX = -backPreview * drawerWidthPx }` 跟随手指向左滑出，
  露出下面的聊天窗口；
- 手势完成：`drawerState.snapTo(Closed)`（预览已经在位，snap 不会有二次位移）；取消：进度归零，抽屉滑回。

### 3. 设置 → 聊天窗口（第 2 次预测动画）

- 新增 `lastChatScreen`：`LaunchedEffect(screen)` 记录最近一次聊天窗口；
- **聊天窗口作为基座层**在设置页下方继续 compose（`screen !is Screen.Chat` 时渲染 base ChatScreen），
  所以设置右滑时露出的是真实的会话内容，而不是空白；
- `PredictiveBackHandler(enabled = drawerState.isClosed && screen is Screen.Settings)`：
  进度驱动设置页 `translationX = progress * screenWidthPx` 向右滑出；完成时切回 `lastChatScreen`；
- 设置页左上返回箭头同样走 `leaveSettings()`（同一语义）；
- 兜底：`lastChatScreen` 不在会话列表里 → `openDraft()` 新草稿（按用户确认）。

### 4. 其他

- 回收站/归档子页仍在设置内：返回设置（保留原 `BackHandler`，非预测式）；
- 平板宽屏：设置页覆盖基座会话，返回同样回会话窗口。

## 真机验证（7e7c5a68 / Android 15，暗色）

1. 聊天窗口按返回 → 直接回到桌面（`topResumedActivity` 变为 launcher），进程仍在后台；
2. 打开抽屉 → 返回 → 抽屉关闭、回到聊天窗口（不退出 App）；
3. 抽屉 → 设置 → 返回 → **直接回到进入设置前那个"新会话"聊天窗口**，不是抽屉、不是会话列表；
4. 环"聊天窗口→多会话→设置→聊天窗口"闭合；
5. 预测动画抽帧：
   - 设置返回手势（20fps 录屏 `s0030→s0044`）：设置页整体向右移出，露出下面聊天窗口，返回箭头随页滑出；
   - 抽屉返回手势（`k0074→k0100`）：抽屉整体向左滑出，露出聊天窗口，手势完成后 snap 关闭；
6. `adb logcat -b crash` 无 FATAL；`:app-android:assembleDebug`、全量测试通过。

## 已知/后续

- 设置页下基座聊天是重新 compose 的实例（从事件日志重建），若进入设置时正在流式生成，返回后
  live 过程可能重建（最终内容仍正确）；要做到无缝需把 ChatScreen 提升为全局常驻层，留作后续。
- 抽屉预览期间遮罩层仍是全暗（抽屉真实偏移未变，只是内容层位移），观感可再优化。
- 回收站/归档页返回仍是即时切换，未做预测动画。

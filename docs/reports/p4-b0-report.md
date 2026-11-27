# P4-B0 验收报告：面板壳 + 基座原子

> 日期 2026-11-27 · 自验方式：模拟器 `emulator-5554`（android-35, 1080×2400, 411×914dp, density 2.625）
> 截图目录：`docs/reports/assets/emulator/20261127-p4b0-*.png`
> 说明：**触控手感、玻璃观感、帧率**只能真机判断，本报告不把它们写成通过。

## 1. 交付

| 范围 | 文件 | 说明 |
|---|---|---|
| 壳 | `ui/m5/shell/` 7 个：PanelStack / PanelHost / BackDispatcher / InsetsController / PanelZLayout / **PanelGraph** | 覆盖栈、返回 dispatcher、insets、z 阶、邻接图 |
| 基座 | `ui/m5/primitives/` 10 个：DisclosureRow / M5Button / IconActionBar / M5Menu / M5Sheet / M5Modal / M5Lightbox / M5Toast / HitTargets / M5Animation | 由后续面板复用 |
| 调试入口 | `ui/m5/ShellDemo.kt` + `m5_shell` extra | 仅开发用，不入产品流程 |

**测试**：`ui.m5.shell.*` 68 条 + `ui.m5.chat.model.*` 58 条，合计 126 条纯逻辑单测通过；全量 184 条中 1 条为 B1 未完成文件（已修）。

## 2. 实机自验结果

| # | 用例 | 结果 | 证据 |
|---|---|---|---|
| 1 | 内容区右滑 >96dp 开抽屉 | ✅ PARTIAL 320dp + 0.32 遮罩，**下层不位移、不变白** | `p4b0-02` |
| 2 | 返回键关抽屉 | ✅ 回根，`stack.depth=1`，抽屉节点消失 | `p4b0-03` |
| 3 | 点遮罩关抽屉 | ✅ | `p4b0-05` |
| 4 | 短滑 140px（<96dp） | ✅ **不**开抽屉（观察期生效） | `p4b0-04` |
| 5 | 打开设置（FULL） | ✅ 全屏覆盖、无遮罩、无侧栏列 | `p4b0-06` |
| 6 | 设置返回 | ✅ pop 回 chat | `p4b0-07` |
| 7 | insets | ✅ 状态栏 48.76dp / 导航栏 24dp 解析正确；内容根按边垫一次 | `p4b0-01` 顶部不压状态栏 |
| 8 | 列表竖向滚动 | ✅ 抽屉开合后滚动位置不变 | 复核实拍 |
| 9 | 左滑 >22% 拖出关抽屉 | ✅ | B0 自验 `05-closed-by-drag-dismiss` |

## 3. 本轮修掉的两个真 bug（都是实拍发现，不是自审发现）

### BUG-A 开抽屉时整屏变白
- **现象**：右滑开抽屉 → 白屏。
- **根因**：`progress` 一个标量被三通道（进入/退出/开手势）共用；开手势期间 `progress` 从 0 起，
  栈顶层的 `translationX = (1-progress)*sign*width` 把**下层聊天一起移出屏幕**。
- **修法（转正版）**：显式建模 **progress 归属**——
  `PanelProgressPhase { IDLE, ENTERING, EXITING, DISMISS, PULL, PREDICTIVE }` + `progressOwnerRouteId`；
  渲染层只对 owner 应用位移；遮罩可点时机改为 `progress>=1 && owner==null && idle`。
  Lead 的临时 hack（`!canDismiss` 判断）已删除。
- **为什么不能只打补丁**：临时修把"渲染位移"绑在"手势可用性"上，会丢 settings（swipe=null）的进出转场、
  会在"pop FULL 面板 + 新顶层也是 FULL"时同帧双位移、会让预测返回/拖出关隐性依赖 swipe 配置。

### BUG-B 右滑不开抽屉（`beginPull` 永不返回）
- **现象**：点按钮能开，右滑不开；`beginPull` 后无 `onPullRelease`，pulled 永久卡在 progress=0。
- **根因**：手势 modifier 的**挂载条件**里用了瞬态量 `isIdle(state)`；`beginPull` 置 `pulledRoute` → `animating=true`
  → 重组时 modifier 变成空 → **pointerInput 从链上摘除 → 手势协程被取消**。
  另叠加一层：冷启动首帧未完成（Davey >2.5s）时输入根本不进 Compose。
- **修法**：挂载条件只留**结构性**条件（swipe 声明 / depth==1 / 窗口档），瞬态条件移入 `pointerInput` 内部运行时守卫；
  两处手势加 `try/finally` 兜底，协程取消时 `cancelPull()` / 回弹，绝不留半开状态。

## 4. 验收覆盖

- **已实现并自验**：BL-01…BL-09（栈/邻接图/深链/回根单转场/replace/快照）、BL-10…BL-15（开合手势/观察期/边缘让系统/提交阈值/reveal=0）、BL-16…BL-21（遮罩 0.32/全屏无遮罩/多层只一层）、BL-22…BL-27（返回优先级/登记注销/push 前清浮层）、BL-28/29/31（预测返回）、BL-32/33/34（保活冻结）、BL-35…BL-39（断点）、BL-40/41（insets/大字号几何）、BL-45（返回钮命中盒）、BL-47…BL-52。
- **基座原子**：BP 清单由 10 个文件头注释逐条关联（披露行/按钮/动作条/菜单/sheet/modal/lightbox/toast/命中区/动画）。

## 5. 已知缺口（不隐藏）

| 项 | 原因 | 处置 |
|---|---|---|
| Lightbox 双指缩放 | 与 pager 多指仲裁未做 | 首版只双击 1×↔2× + 缩放态禁翻页；`Q-BP-06` 原型后补 |
| 抽屉 `role=dialog` | Compose 无此 Role | 用 `paneTitle` + 覆盖层清语义；验收改为"能报面板名与可关闭动作"（D-31） |
| 浮层退出动画 | Dialog 移除即消失 | 只保留入场 spring（D-33） |
| 颜色/字号 token | `base.theme` 未落地 | 暂读 `MaterialTheme`，B3 后切 |
| reduced-motion 动态跟随 | 只在进入组合时读系统倍率 | 接受"重进页面生效" |
| 正文内链接命中区 | Compose 内联文本命中=字形盒 | G-16 豁免项，P4 原型后再定（D-22） |
| 触控手感/玻璃观感/帧率 | 模拟器不可信 | **待真机** |

## 6. 后续

1. B1 聊天 UI（进行中）→ 我接线 `ChatScreen.kt` → 第二轮自验（滚动跟随/回底钮迟滞/过程组到边吸收）。
2. B2 composer + attachment；B3 model/settings/sidebar；B4 旧功能对账 + bug 修复。

# 进度状态（P3 收尾 / P4 开工）

> 时间：2026-11-27 14:30 左右。用户不在电脑前，本文件供远程查阅。
> **推送状态**：本地已提交至 `f8c5bdc`，远程停在 `478d895`（落后 2 个提交），
> git push 多次长时间无输出，按用户指示**暂停推送**，网络恢复后一次性推。

## 一句话

规格层（P1 事实库 → P2 合并 → P3 面板规格）基本完成；**P4 第一批判（B0 壳与基座）正在实现**。
20 条阻塞决策已全部拍板并固化为 G-12…G-18。

## 已完成

| 层 | 产物 | 规模 |
|---|---|---|
| P1 事实库 | `docs/spec/ref/` 10 份 | 7 个实现、约 1.4 万交互点、全部 uncovered=0 |
| P2 面板合并 | `docs/spec/p2/` 8 份 | 40+ 冲突、约 70 条"明确不做" |
| P2 决策 | `docs/spec/decision-pack.md` | 20 阻塞（全拍）+ 23 非阻塞 + 22 已结案 |
| 全局规则 | `docs/spec/00-global-rules.md` | G-00…G-18 |
| P3 面板规格 | `docs/spec/panels/` 8 份 | 节点 131 / 验收项 345+（见下表） |

### P3 规格明细（`tools/check_panels.py` 结构校验通过）

| 面板 | 节点 | 验收项 | 备注 |
|---|---|---|---|
| base.layout | 17 | 52 | 覆盖栈/返回 dispatcher/insets/z 阶 |
| base.primitives | 27 | 79 | 1 条留 P4 原型（正文链接自绘命中层） |
| chat | 24 | 68 | 63 首版 + 5 沿用；2 条已裁定（角标候选、内联链接豁免） |
| composer | 19 | 76+ | 3 条已裁定（客户端队列/静态命令/无 steer） |
| model | 11 | 45 | 关思考 = 显式 off 档（MODEL-32 可断言） |
| attachment | 12 | 28 | 首版 20 + 候选 8 |
| settings | — | — | **补完中**（原 agent 失败，缺 §4/§5） |
| sidebar | 20 | 61 | |

## 进行中

1. **P4-B0 实现**（子 agent）：`ui/m5/shell/` 已出 5 个文件（PanelStack / BackDispatcher / InsetsController / PanelHost / PanelZLayout），
   `ui/m5/primitives/` 待出；要求带 JVM 单测。
2. **settings 规格补完**（子 agent）。

## 已解决的关键问题

| 问题 | 处置 |
|---|---|
| 无人真机验收 | **模拟器自验链已打通**：AVD `myco35`（android-35 x86_64）、无窗口启动、`tools/android/emu.ps1` 一键 build/install/launch/screenshot/rotate；截图归档 `docs/reports/assets/emulator/` |
| 附件"缺内建 UI" | 用户实拍入库 `ref/deepseek-app-attachment.md`；裁定首版 = BottomSheet 三入口，最近照片内联形态为 `candidate` |
| 功能边界模糊 | `00-product-boundary.md`：myco = 基座平台，"work" 能力（代码/终端/git/工作流）不做，只留扩展点 |

## 已知缺口（不隐藏）

| 类别 | 项 |
|---|---|
| 后端硬前置 | 读附件 Query、删附件 Command、处理状态回执、附件限制投影、消息 seq 定位（搜索跳转）、队列/steer 的 core 能力 |
| 已知设计缺口 | 正文内链接命中区 <44dp（G-16 豁免，P4 原型验证后再定） |
| 只能真机判断 | 玻璃/模糊真实观感、触控手感与阈值、帧率、系统返回手势 |
| 候选功能（可能砍） | 最近照片条 + 多选 + 内联附件面板；行内引用角标 |

## 下一步

1. B0 完成 → Lead 在 `ChatScreen.kt` 留接线点 → B0 模拟器截图自验（返回链/insets/两层栈）。
2. B1 聊天面板实现（依赖 B0 的 primitives）。
3. B2 composer + attachment；B3 model/settings/sidebar；B4 旧功能对账 + bug 修复。
4. 每批：构建 → 模拟器截图入库 → 验收项打状态 → 提交。

---

## 更新 2026-11-27 15:30（P4 进行中 + 玻璃并行线）

**已落地**：B0（面板壳+基座，16 文件/126 测试）、B1（聊天列表 UI，9 文件/186 测试全绿）、
`ChatListM5` 已接入真实数据路径（旧列表保留 `useNewChatList` 开关可回滚）。

**玻璃并行线**：
- 能力调研完成：`docs/spec/ref/android-blur-glass.md`（13 项矩阵，官方证据）
- 原型实验室进行中：`docs/reports/glass-lab-report.md` + `docs/reports/assets/glass/`
- 模拟器已从 SwiftShader 切到 **host GPU（AMD Radeon 890M / GLES 3.1）**，模糊截图才可信

**玻璃关键结论（来自调研，待实验室验证）**：
1. 唯一可行的真背景模糊 = 自绘 `GraphicsLayer.record` + `drawLayer` + `RenderEffect.createBlurEffect`（API 31+，需硬件加速）= Haze 的机制
2. AGSL 着色器**采样不到背景**，只能作用于自己重绘的层
3. `Modifier.blur()` 只糊自己内容；API < 31 没有任何平台真模糊 → 只能纯绘制假玻璃
4. 多层玻璃 = 每帧多次全屏重绘，实测同屏 9 块超帧预算 → 同屏 ≤3、滚动列表上方 ≤1
5. 窗口级 blurBehind 会静默失效（OEM/省电/视频），必须监听 `crossWindowBlurEnabled` 并有降级外观

**测试基线**：186 条单测全绿。**推送仍暂停**（本地提交至 `553ed16` 之后）。

# P4 实施计划（面板落地）

> 前置：P3 全部面板规格完成（`docs/spec/panels/*.md`）+ `acceptance.md` 汇总。
> 实现时**只看 `panels/*.md` 与 `00-global-rules.md`**，不再回查 `ref/`（那是审计层，不是实现依据）。

## 0. 批次与理由

| 批次 | 内容 | 为什么这个顺序 |
|---|---|---|
| **B0 壳与基座** | `base.layout`（覆盖栈/返回 dispatcher/insets/z 阶） + `base.primitives`（披露行/按钮/菜单/弹层家族/命中区） | 所有面板的容器与原子。**必须先做**，否则后面每个面板都要返工 |
| **B1 聊天** | `chat`（轮/消息/思考/过程组/工具卡/操作条/滚动/来源面板） | 最重、bug 最多、验收点最多（63 条） |
| **B2 输入与附件** | `composer` + `attachment` | 用户点名两处之一（导入媒体）；依赖 B0 的弹层与 B1 的列表高度契约 |
| **B3 其余** | `model` + `settings` + `sidebar` | 依赖 B0 的栈/断点，与 B2 的附件/模型边界 |
| **B4 对账收口** | 旧功能逐项对账（`ref/legacy-myco.md` §3/§4）+ 已确认 bug 修复 + 候选功能决策 | 保证"一行功能不漏"，也处理 BUG-001…004、M-01…M-03 |

**每批结束必须**：构建通过 + 模拟器自验截图入库 + 该批验收项逐条标状态 + 提交。**不做"做完一批再一起验"。**

## 1. 每批的验证协议（用户不在也能验）

```powershell
# 1) 构建 + 安装 + 启动
pwsh -File tools\android\emu.ps1 -Action build
pwsh -File tools\android\emu.ps1 -Action install
pwsh -File tools\android\emu.ps1 -Action launch -Entry chat

# 2) 标准截图（明/暗、竖/横、大字号）
pwsh -File tools\android\emu.ps1 -Action suite
pwsh -File tools\android\emu.ps1 -Action shot -Name dark      # 切深色后
pwsh -File tools\android\emu.ps1 -Action rotate               # 横屏
pwsh -File tools\android\emu.ps1 -Action shot -Name landscape
pwsh -File tools\android\emu.ps1 -Action input -Keys KEYCODE_BACK   # 返回链
```

- 截图**提交入库**：`docs/reports/assets/emulator/<日期>-<批次>-<状态>.png`（用户远程查看）。
- 每批写一份 `docs/reports/p4-<批次>-report.md`：验收 ID × 结果（✅/❌/⚠️ 已知缺口）× 证据（截图名/命令）。
- **不能用模拟器验的**（需真机）：玻璃/模糊的真实观感、触控手感与阈值、性能（帧率）、系统返回手势。→ 在报告里单列「待真机确认」，**不许写成已通过**。

## 2. 写作用域（并行时防冲突）

| 范围 | 归属 |
|---|---|
| `ui/m5/shell/*`（新建：面板栈、dispatcher、insets、z 阶） | B0-1 |
| `ui/m5/primitives/*`（新建：披露行、按钮、菜单、sheet、modal、lightbox、命中区工具） | B0-2 |
| `ui/m5/chat/*` | B1 |
| `ui/m5/composer/*`、`ui/m5/attachment/*` | B2 |
| `ui/m5/model/*`、`ui/m5/settings/*`、`ui/m5/sidebar/*` | B3 |
| `ChatScreen.kt` / `MainActivity.kt` 接线 | **只 Lead 改**（避免多人抢同一文件） |

> B0 之后若并行，必须先由 Lead 在 `ChatScreen.kt` 里留好接线点（一个入口 + 一个 state 容器），再分头写面板。

## 3. 每批的完成定义（DoD）

- [ ] 该批所有 `panels/<id>.md` 的验收项有状态（✅ / ❌ / ⚠️ 已知缺口 / ⏸ 待真机）
- [ ] 构建无警告新增（或已在报告里说明）
- [ ] 模拟器截图入库，且**截图能看出该批的关键状态**（不是只拍首页）
- [ ] 旧实现路径仍在（`else` 分支）直到该面板验收完成，**不做一次性删除**
- [ ] `docs/reports/backlog-and-issues.md` 与本批相关项已更新

## 4. 风险

| 风险 | 处置 |
|---|---|
| B0 的 insets/返回 dispatcher 做错 → 后面全部返工 | B0 先只做"壳 + 一条能通的最小链路"，截图证明返回链与 insets 正确，再进 B1 |
| 形态 D（按状态切）在 Compose 里做不稳 | 先做"两套容器 + 切换"，再补锚点补偿；锚点补偿用 `LazyListState` 的 item 偏移，禁止帧后 jumpTo |
| 命中区自绘热点层（`Q-BP-06`）不确定 | B0 先做 3 个最小原型（正文链接/图标动作/过程行），**实测再决定**是否用自绘层 |
| 玻璃/模糊观感只能真机判断 | 保留 M5 现有 L0 玻璃实现；B4 出一份"待真机观感确认"清单 |

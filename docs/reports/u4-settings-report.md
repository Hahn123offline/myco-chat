# U4 补充报告：设置搜索 / 版本选择持久化（2026-11-26）

## 1. 设置搜索
- 设置页顶部新增「搜索设置」输入框。
- 支持中英文关键词过滤分组：外观（appearance/theme/light/dark）、会话（session/archive/trash/delete）、
  模型提供方（provider/model/key/import/export）、常驻（foreground/notification/background）、自检（doctor/status）。
- 真机验证：输入 `archive` → 只剩「会话」组（归档会话/回收站）✅
- 未做：分组"卡片化"（外观/会话仍是平铺行；provider 已是卡片）；设置项没有独立的可点击跳转子页。

## 2. 版本选择持久化（activeVersion）
- 版本切换 `‹ n/m ›` 时把 `v_<nodeId>=version` 写入 `myco.chat` 偏好；进会话时读回作为初始选中版本。
- 重新生成后新版本仍会自动成为最新（默认 max version），除非用户显式切回旧版。
- 说明：未做真机点验（设备上当前会话没滚到版本区；逻辑为纯本地偏好读写）。

## 3. 真机状态
- 设置搜索 ✅（截图）。
- 平板常驻侧栏 ✅（上一轮）。
- editResend / 归档页 / 撤销点击时序仍未完成真机点验（脚本在键盘与坐标上反复失败）；后端有单测。

## 4. 测试/构建
`:core:test` 80 + `:bridge-http:test` 7 = **87 绿**；Android/桌面构建通过。

## 5. 剩余（下一轮收口）
1. 设置分组卡片化（外观/会话/常驻/自检包裹为卡片）。
2. editResend / 归档 / 撤销真机补点。
3. 对照 80 组件规格的基座总验收矩阵。
4. push（GitHub 502）。

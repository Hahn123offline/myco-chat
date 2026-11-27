# M5 功能审计计划 —— 已被 `docs/spec/` 取代

> ⚠️ **本文废弃，不要在此继续维护。** 唯一权威改为 [docs/spec/](../../spec/README.md)。
> 保留本文件只为留下决策轨迹：用户决定「先放缓全量逻辑迁移，先把各状态 UI 布局调对，
> 再做逐按钮功能审计」。

## 当时的判断（仍然成立）

1. 逐轮"发现丢了什么再补"的效率太低：旧交互散落在 `MainActivity.kt` + `ChatContent.kt`
   约 3,800 行里，靠增量迁移必然漏。
2. 必须做静态盘点 + 参考仓库对照 + 逐项需求表 + 用户逐项核对。

## 当时没想到、后来补上的关键一条

**"盘点出来"不等于"理解"**。只记录"有这个按钮"，会得到一份看着完整、
实际每项仍需现场决策的清单，等于把补丁往后推。所以新方案强制：

- 每个节点 13 字段，缺一不合格（[schema-node.md](../../spec/schema-node.md)）；
- 每字段标证据级别，`E2`（推测）**禁止进入 spec 与实现**（G-00）；
- 每条必须写反证"反过来做会坏在哪"，写不出 = 没理解。

## 新方案入口

- 铁律与流程：[docs/spec/README.md](../../spec/README.md)
- 全局规则：[docs/spec/00-global-rules.md](../../spec/00-global-rules.md)
- 面板穷举：[docs/spec/10-panels.md](../../spec/10-panels.md)
- 待拍板：[docs/spec/open-questions.md](../../spec/open-questions.md)

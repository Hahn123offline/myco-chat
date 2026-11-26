# DSH/chat-demo 对齐批次（2026-11-26）

## 1. 调研（两份新报告）
- `docs/recon/dsh-ui-implementation.md`：从安装版 app.asar 抽源码，查清 DSH 0.2.2 的会话 UI：
  Turn→ProcessGroup→行；reasoning/response 分开；live chunk 合并（3 rAF）；完成后整体替换并折叠；
  `ScrollFollow`（25px 阈值、用户上滑才停跟随、ResizeObserver 驱动、回到底部）；工具行 24dp 折叠摘要 + 展开卡；
  组头运行中/完成文案；间距契约。
- `docs/recon/deepseek-citation-ui.md`：从官方 chat-demo（jsDelivr 镜像）读引用链路：
  SourceRegistry 按 URL 首次出现编号；`[citation:N]` 注入文本块尾部；CitationLink 点击新窗口；
  来源卡 = 站点/日期/标题/摘要；搜索块先到、编号先定，完成后不重排；可复制正文不带编号。

## 2. 代码对齐
- 来源卡升级：`SourceRef` 增加 snippet/date；按 turn 聚合并去重；回答下方「来源 · N 个网页」卡片（站点/日期/标题/两行摘要，点击浏览器打开）；日期归一为 `YYYY/MM/DD`。
- 工具行：改为 24dp 级折叠摘要（`▸ name · 完成 · 已阅读 N 个网页`），点击展开参数/结果/来源；不再默认把长 JSON 全铺开。
- 组头状态机 v1：运行中显示「正在思考… / 正在使用工具… / 正在处理…」；完成后「过程 · N 个工具」。
- 流式节流：reasoning/正文增量先进 buffer，50ms 合并发布（对齐 DSH 的 rAF 合并），工具 started/finished 仍立即 flush。
- 滚动跟随 v1（按 DSH ScrollFollow 语义）：
  - 跟底状态 `followBottom`；用户上滑离开底部才取消；回到底部恢复；
  - 每 60ms 在跟随时贴底；用户拖动时跳过；
  - 回到底部按钮只在 `!followBottom` 显示，点击立即落底并恢复跟随；
  - 去掉了按 delta 长度的自动 scroll，避免抢窗口。
- Markdown `---` 分隔线已支持（上一批）。

## 3. 已真机/未真机
- 真机：来源卡可显示/点击（iPhone 新闻会话）、工具行折叠、思考/工具实时顺序。
- 未完整验证：滚动跟随的"用户上滑不被抓回 / 生成完不瞬移"；需要连续手感测试。
- 已知偏差：DSH 完成后默认折叠过程、折叠时有 keepAboveClosing 防跳，我们尚未实现；DSH 主列表不虚拟化，我们仍是 LazyColumn。

## 4. 下一步
1. 按 DSH 报告 §10 继续：稳定 key（nodeKey/groupPart）、完成后折叠过程 + 避免跳动的锚点补偿、工具行详情卡、组头文案/间距对齐。
2. 按 citation 报告 P0：正文 `[n]` 角标（若模型/工具提供 citations）、来源 Sheet、搜索行缩略图。
3. 真机验证滚动状态机与重生成分支；再补 work 分支/单条删除不关抽屉。

# 最新决策与当前状态（2026-11-25 22:40 · 关机前整理）

> 给用户关机后快速回来用的一页纸。权威细节见 [决策记录](20-decision-record.md)、[设计简报](00-design-brief.md)、[I0 方案](30-i0-plan.md)。
> 当前**只差你开机后说"开始"**，其余不用再答。

## 0. 一句话

框架选型（Kotlin/JVM 单核心）、产品形态（两 APK 共享存储 + 显式同步）、MVP 范围（多会话 chat）、
离线独立（单手机可完整运行）都已定；语言无关契约（T0）已交付并校验通过；下一步是 S0 walking skeleton。

## 1. 已定决策（速查）

| # | 结论 |
|---|---|
| D1 | 桌面 daemon 只是开发/预览载体；**设计按移动端原生** |
| D2 | 自研薄内核（ICL 为参考，不寄生 DSH）；ICL 实际运行过更久，但验证薄 |
| D3 | chat / work = **两个独立 app**，共享存储、各自读取策略 |
| D4+A2 | MVP = 常规 **多会话 chat**（会话列表 + 聊天框 + 设置页），能平替限额官方 chat |
| D5 | 三种协议方言；Anthropic Messages 做但不过度投入 |
| D6 | 记忆七表现定，三条不可逆决策先定死 |
| D7 | 出网：先只做唯一出网点壳 + 审计 |
| D8 | 感知只留 `snapshot` / `capture` 形状 |
| D9 | **Kotlin/JVM 单核心**（Android Compose + 桌面 JVM 预览 ≥ KMP）；分发侧载优先；**动态插件推迟**，留 `PluginLoader` 接缝 |
| D10 | **两个 APK、各自完整内核 + 显式增量同步**（不是共享同一份文件） |
| A1 | 人格卡版本化模块：多卡 + 历史 + 两 app 各自副本 + 单向复制同步（不互锁） |
| A2 | MVP 必须多会话（"单焦点"是人格/主动性层的属性，不是 UI 只有一会话） |
| A3 | proot 默认侧载，I5 前研究 DSH-GO / 太墟 |
| A4 | **离线独立是硬要求**：单手机完整运行，无远程依赖；远程只是可选增强 |
| A5 | GUI 模块化：Bridge 唯一接口（in-process / HTTP-SSE），**无 GUI 后端照跑**，GUI 可整体换 |
| A6 | 同步 = 版本链 + 本地 journal：拉取预览后新建版本；推送两边各确认；撤回只改本机；冲突保留双方；密钥只同步 `auth.ref` |
| A7 | 主动说话可以有（后台通知），但不承诺 24/7；受阻积压、打开追平 |
| A8 | **手机不能持续接 ADB**：真机窗口提前约、一次性、可回退；app 自带自测导出；参考机数据不作普遍结论 |

## 2. 本轮纠正记录（防止旧结论回流）

1. **A7 误读已纠正**：你说的"没法持续 ADB"是**测试约束（A8）**，不是"手机不能常驻"。常驻设计（不承诺 24/7、积压合并）保留。
2. **DSH-GO / 太墟纠偏**：DSH-GO = 后端跑完整 Linux + DSH；**太墟原理未确认**，但两者都强依赖 Linux 后端——正是 work 想躲掉的形态。不据此设计 chat 内核。
3. **参考机**：12GB RAM / Snapdragon 8 Elite Gen 1 / 4800mAh。性能不弱、**电池是短板**；该机数据只代表该机。

## 3. 已交付

- `specs/`（T0）：4 份 JSON Schema（provider / capability / plugin / session-events）+ Bridge 契约 + 23 条 fixture；
  `python specs/validate_fixtures.py` → **23/23 通过**。语言无关，Kotlin 实现直接照它写。
- `docs/design/`：简报、决策记录、I0 方案、待拍板清单（已标注结论）。
- `docs/recon/`：8 份调研（dsh-icl ×3、DSH 源码、DeepSeek API、trace-memory、移动端底盘、运行时选型、DeepSeek demo UI）。
- 远端已推送：`e31466b`（docs）/ `d976fb4`（specs）。

## 4. 下一步：S0 walking skeleton（开工第一件事）

不写一次性 spike，直接做真实产品代码的最小竖切（3–5 天）：

```
Gradle 多模块
  core/         Kotlin JVM：session（JSONL 事件日志）+ provider（profile/凭据 ref）+ llm（SSE 客户端）+ bridge + tools
  app-android/  Compose：会话列表 + 聊天页 + 设置入口 + FGS 骨架
  app-desktop/  JVM：开发/预览
  tools/fake-sse/  本地假模型服务（不调真模型）
```

只测三件真不确定的事：① 国产 ROM 后台存活/耗电（真机窗口）② 流式体验与包体（emulator + fake SSE）③ trace-memory 移植摩擦力。
其余（SQLite / JSON Schema / SSE / 通知）直接用成熟实现，不花时间验证。

验收：`gradlew :core:test` 通过；桌面壳连 fake-sse 看到流式；真机上"建会话→发消息→流出→杀进程重开历史还在"；
产出 `docs/reports/s0-measurements.md`（包体/冷启动/RSS/24h 存活/耗电/移植行数）。

## 5. 需要你配合的（很少）

- 开机后说"**开始**"。
- S0 真机环节：约一个 10–20 分钟窗口，按我给的命令清单装一次、跑完、导出、回滚。
- 其余时间我用 emulator，不占用你手机。

## 6. 仍未定（不阻塞开工）

动态插件（已定推迟）；太墟原理（I6 前研究）；同步协议细节（I3 前定）；provider 配置是否两 app 共享（默认共享）。

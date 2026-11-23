# 记忆层契约（v1）

> 状态：冻结草案　日期：2026-11-23
> 适用范围：**轨迹采集与查询器**（本仓） ↔ **聊天 app + agent 记忆**（另一仓）

## 0. 两边的定位

这是**两个独立的 app**，不是两个模块：

| | 本仓 | 另一仓 |
|---|---|---|
| 形态 | 安卓轨迹采集与查询器 | 聊天 chat app + agent 记忆 |
| 职责 | 采得到、拼得干净、查得回 | 对话、人格、交互、模型调度 |
| 能否独立运行 | 是（装上即采，查询器可自测） | 是 |
| 是否依赖对方 | 否 | 否（未接入时降级为"没有记忆工具"） |

**唯一耦合点：`memory.db` 这一份文件格式。**

不是共享进程、不是共享代码、不是共享 APK。对方可以把本仓产出的
`memory.db` 读走，本仓也可以完全不关心对方是否存在。

## 1. 交付边界

**本仓负责**

```
采集     A11Y / OCR / 像素 / 截图，只记事实，不做"这是什么"的判断
对齐拼接 文本锚点 + 像素双判据，静态层分离
去重     block 内容寻址：相同内容只存一份
访问     visit 时间寻址：一条一次，永不合并
重建     view：按问题生成，不落盘成事实
检索     recall / read_block / read_frame
自证     answerability harness（无 agent、无设备也能跑）
```

**本仓不负责**（另一仓或独立 app）

```
agent loop、人格卡、仿真契约
实时看屏、手动"记一下"的交互入口
API 调用、模型管理、向量化调度
视频记忆的写入策略
对话记忆（谁跟 agent 说了什么）
```

## 2. 唯一共享物：memory.db

### 2.1 公开读接口（稳定，跨版本保证）

另一方**只应读这些表**：

```sql
-- 文本行（带位置）：检索与证据回指的最小单元
lines(id, object_key, pkg, time, text, block_id, frame_id,
      y_top, x_left, x_right, kind, shot_ref)

-- 对象消息（当前实现的聚合视图；v2 由 block/visit 取代）
object_messages(id, object_key, pkg, t_first, t_last, seen_count,
                sender, text, text_key, block_id, frame_id)

-- 通用记忆项（事实、人物、偏好；与轨迹正交）
mem_items(id, namespace, key, value, t0, t1, source, updated_at)

-- 客观日叙事（**不含**用户与 agent 的对话）
day_narratives(day, narrative, segments, mainlines, open_threads, gaps)
```

### 2.2 实现细节（**不保证稳定，不要依赖**）

```
chains / l0_chunks / merge_judgments / pipeline_runs
agent_sessions / agent_events
trace_gaps / sqlite_sequence
```

这些是会随实现改动、重命名、删除的中间表。依赖它们等于把两边的发布绑死。

### 2.3 证据层（可选，不随设备迁移）

```
evidence.db  （设备本地）
shots/*.jpg  （设备本地，会被 GC）
```

读 `lines.shot_ref` 可以回指到原图。**换设备后这些引用必然失效**，
调用方必须能处理 `shot_ref` 指向的文件不存在。

## 3. 工具面（语义契约）

读接口只有三个。语义比实现重要。

### 3.1 `recall`

```
recall(query?, object?, from?, to?, pkg?, limit?) → {
  answer,      // 人可读的结论；空串 = 没找到（**不等于**没有）
  ids,         // 命中的行/消息 id，供下钻
  coverage,    // { covered_ms, gaps[], 本次显示范围, 未显示条数 }
  freshness    // 见 §4，必须返回
}
```

约束：

- `answer` 为空时，调用方**不得**把它表述为"没有发生"。
- `coverage` 必须声明本次结果覆盖的时间范围与空档。
- 时间参数缺省时，默认范围由实现决定，但必须在 `coverage` 里说明用了什么范围。

### 3.2 `read_block`

```
read_block(block_id) → { id, time, pkg, object_key, lines[], shot_ref }
```

读一整块（一次停留的画面）。`shot_ref` 可能失效（被 GC 或换设备）。

### 3.3 `read_frame`

```
read_frame(frame_id | shot_ref) → { ok, reason, image | 文字描述 }
```

- `ok=false` 时必须给 `reason`（例如 `"evidence_evicted"`、`"mask_denied"`）。
- **核不到原图时必须明说"我没能核对原图"**，不得用推断冒充核对。

## 4. 新鲜度（本次新增，v1 必选）

这是分仓后最容易出事的地方：**记忆有滞后，但工具返回的是一个自信的答案。**

```json
freshness: {
  "projected_until": 1763...,   // 数据已处理到哪个时间点
  "lag_ms": 64800000,           // now - projected_until
  "state": "ok" | "catching_up" | "stalled"
}
```

约束：

- 任何 `recall` 必须返回 `freshness`。
- `lag_ms > 阈值` 时，调用方**必须**在回答里带上"我这边还没处理到 X 点"这类限定语，
  而不是把"没查到"说成"没有"。
- `state=stalled` 表示采集或投影已经停了（无障碍被关、进程被杀），
  调用方应把它当作"这段时间没有记忆"而非"这段时间没发生事"。

**这条是为了修一个已经发生过的真实故障**：`object_messages` 落后 18 小时，
系统对一条确实存在的消息回答了"没看到"。分仓后同样的错误会被写进对话记忆、被二次固化。

## 5. 确定性

同一次查询，在数据不变的前提下**必须返回同样的结果**。具体要求：

- 排序必须完全确定（不能有依赖 db 内部顺序的并列）。
- 不得使用随机数、当前时间做隐式影响（时间只用于 `freshness`）。
- 同一 `recall` 的 `ids` 集合在两次调用间必须一致。

这条让另一仓能对它做缓存和回归测试。

## 6. 可携带性

目标：**换设备后记忆还在，但原始数据不可溯源。**

```
memory.db      可导出 / 可导入 / 跨设备
evidence.db    不导出
shots/*.jpg    不导出
```

导出时：

- `lines.shot_ref` 与 `object_messages.frame_id` 允许指向已不存在的证据，**不清理**。
- 导入方必须容忍悬空引用（读不到证据就返回 `ok=false`，不报错、不崩溃）。

版本字段：`memory.db` 里保留 `schema_version`，导入方**必须**检查，
不认识的版本应拒绝导入而不是尝试自动迁移。

## 7. 版本化与能力协商

```
schema_version      表结构版本；不匹配时拒绝
capabilities[]      可选能力：["freshness", "visit", "vector", "video"]
```

调用方应**按能力降级**，而不是按版本号猜：

- 没有 `freshness` → 视为总是新鲜（旧产物），调用方自己承担滞后风险
- 没有 `visit` → 用 `object_messages` 的聚合视图
- 没有 `vector` → 只用文本检索

## 8. 明确不做

```
✗ 本仓不提供 chat UI
✗ 本仓不管理模型、不调外部 API
✗ 本仓不决定"这段记忆该怎么叙述给用户"
✗ 本仓不写 agent 自己的对话记忆
✗ 本仓不保证"证据一定还在"（GC 是设计的一部分）
```

## 9. 验收：两边都能独立跑

本仓自证（不需要对方、不需要设备）：

```
pcharness recall --q "..." --from ... --to ...
  → 命中 / 覆盖率 / 新鲜度 三个数字
pcharness answerability --set 10
  → 答案可查性 x/10
```

另一仓接入前应能：

```
读一份导出的 memory.db → 调 recall → 得到 answer + coverage + freshness
在没有本仓的机器上，用同一份 db 跑出同样结果
```

## 10. 未决

1. `mem_items` 的 `namespace` 约定（哪些命名空间属于本仓、哪些属于对方）
2. 视频记忆的写入方：本仓采到视频帧，还是对方直接写 `mem_items`？
3. `visit` 表上线后，`object_messages` 保留多久（兼容期）

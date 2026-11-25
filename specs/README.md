# specs/ —— 语言无关契约（T0）

> D9 已定 Kotlin/JVM 单核心，但这批规格**不绑定语言**：任何栈都可以照它实现，测试样例是验收依据。
> 改规格 = 改接口，必须同时更新 fixture 与（若有）实现；**规格是唯一真相，代码/文档不得另写一份**。

## 文件

| 文件 | 约束对象 |
|---|---|
| `provider-profile.schema.json` | chat 家族的 provider 档案（id/kind/auth/models/roles/compat…） |
| `capability-provider.schema.json` | search / fetch / ocr / asr / tts 的 provider 条目（端点 + 计费单位） |
| `plugin-manifest.schema.json` | 插件清单（id/version/entry/tools/slots/requires/budget） |
| `session-events.schema.json` | 会话事件（JSONL 单行；8 种事件 + SessionMeta） |
| `bridge.contract.md` | GUI ↔ 核心的唯一接口（命令/查询/事件/失败语义） |

## 样例与验收

`fixtures/*.fixtures.json`：每份 schema 至少 1 个合法样例 + **≥3 个非法样例**。
运行：

```bash
python specs/validate_fixtures.py
```

退出码 0 = 全部符合预期；非 0 = 有样例与 schema 不一致（打印 diff 位置）。
校验器是**自包含的最小实现**（不依赖第三方库）：支持 `type/required/properties/additionalProperties/
enum/const/pattern/minLength/minItems/items/minProperties/oneOf/$ref/minimum/maxLength`；
`format` 只做 URI 前缀检查。遇到未实现的 keyword 会**报错而不是忽略**（防止静默放行）。

## 约定

1. **未知字段一律拒绝**（`additionalProperties:false`）：宁可起不来，也不要半残运行。
2. 所有 id 都是稳定的：`provider.id` / `plugin.id` / `session.id` 一旦发布不得复用。
3. 时间戳一律 Unix epoch **毫秒**（`at` / `createdAt`）；序号一律整数单调（`seq`）。
4. 路径禁止出现在持久数据里（同步友好）；需要时存相对 `$MYCO_HOME` 的 POSIX 路径。
5. 密钥永不出现在这些文件里：`auth.ref` 只是引用名。

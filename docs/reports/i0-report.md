# I0 收尾报告（2026-11-26 01:40）

> 范围：T5 http-sse Bridge、settings revision、provider 方言/发现、桌面/Android 接线。
> 全量验证：`core` 26 测试 + `bridge-http` 4 测试 = **30/30 绿**；`:app-android:assembleDebug` 成功。
> 环境：Windows 11 / JDK 21 / Gradle 8.9 / Kotlin 2.0.21 / AGP 8.7.3 / Compose BOM 2024.10.01。

## 1. http-sse Bridge（`bridge-http/`，JVM-only）

| 端点 | 说明 |
|---|---|
| `GET /health` | `{"ok":true,"bridgeVersion":1}` |
| `POST /command/session.create`、`/command/chat.send` | body = payload JSON |
| `POST /query/session.list`、`/session.events`、`/providers`、`/doctor` | 只读 |
| `GET /events?sessionId=` | SSE 事件流（`assistant.delta/done`、`failed`、`session.updated`） |

- 鉴权：`Authorization: Bearer <token>`（`/health` 除外）；token 随机生成，写入 `$MYCO_HOME/runtime.json`。
- 桌面命令：`app-desktop serve [port]`（默认 8788），打印 URL/token/curl 示例。
- 契约测试（4 条）：HTTP 建会话/查询；**HTTP 与 in-process 产生完全相同的 5 个事件类型**；SSE 转发 delta+done；坏 token 被拒。
- 实测冒烟：`/query/session.list`、`/command/session.create` 正常；无 token 返回 401。

## 2. settings revision 乐观锁

- 文件格式升级为 `{"schemaVersion":1,"revision":N,"data":{...}}`；**兼容旧格式**（当作 revision 0），首次写入自动升级。
- `save(expectedRevision)` 不匹配抛 `SettingsConflictException`，不写盘；tmp + 原子 move；同进程加锁。
- 测试：roundtrip、stale revision 拒绝、旧格式兼容、非法 id/models 拒绝。

## 3. Provider 协议与发现

- 新增 **Anthropic Messages 方言**（DeepSeek `/anthropic` 端点）：`x-api-key` + `anthropic-version`、`max_tokens` 必填、
  `message_start/content_block_delta/message_delta/message_stop` 解析；**默认显式关闭思考**（`thinking:{type:disabled}`），避免默认开思考的成本与 temperature 失效。
- `ChatClients` 工厂按 kind 选方言；**不支持的方言不偷偷换假模型**，而是流式返回明确错误（`createOrFailed`）。
- `ModelDiscovery`：显式动作，OpenAI `/models` 与 Anthropic `/v1/models` 两条路径，404 时换路径、其它错误立刻报；
  CLI 命令 `discover <providerId>`。
- 壳接线：桌面 `clientFor` 走真方言（`MYCO_FAKE=1` 仍可强制假模型）；Android 有 provider 就用真方言，没有才用假模型。

## 4. 过程中踩到并修掉的坑（写下来防再犯）

1. **HttpURLConnection 的流式响应在另一线程 `disconnect()` 会死锁**（chunked 输入流的锁）。→ http-sse 客户端改用裸 `Socket`；
   取消时 `socket.close()` 是线程安全的。
2. **chunked 的 size 是字节数，不是字符数**。用 `InputStreamReader` 按字符读会串位（中文 3 字节），
   表现是 SSE 帧丢失/半个 JSON。→ 按字节 `readExactly(n)` 再用 UTF-8 解码，并跨 chunk 累积半行。
3. **`runCatching { job.join() }` 编译不过**（`awaitClose` 的 block 不是 suspend）。→ 用 `job.cancel()` + socket 关闭驱动退出。
4. **junit 测试 worker 被非守护线程挂住**：`HttpServer` 的 cached pool → 改 daemon 线程工厂 + `close()` 里 `shutdownNow()`。
5. **`encodeDefaults=true` + 可空字段**（上一轮）：`explicitNulls=false` 省略 null。

## 5. 当前测试与构建

```
:core:test        26 passed
:bridge-http:test  4 passed
:app-desktop:installDist      ok
:app-android:assembleDebug    ok（debug APK ≈9.6MB，未开 R8）
```

## 6. 下一个需要用户决策的边界

I1 的"真链路"必须打一次真实模型，但：

- `DEEPSEEK_API_KEY` **不在环境里**；
- 用户的自建 LLM 服务「算力被占用，不要直接调用」。

所以在拿到**一次授权**之前，只能继续做不联网的部分（provider 管理 UI、Android 设置页、doctor 扩展、文档）。
可选：① 允许打一次自建服务（只发 1 条 ping 请求，测 `/v1/chat/completions` 路径与 SSE）；
② 提供 DeepSeek key；③ 继续只做 fake/单元测试，等用户方便再说。

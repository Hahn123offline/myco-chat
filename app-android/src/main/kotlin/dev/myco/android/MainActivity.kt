package dev.myco.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.myco.core.bridge.BridgeEvent
import dev.myco.core.bridge.Command
import dev.myco.core.bridge.CommandResult
import dev.myco.core.bridge.InProcessBridge
import dev.myco.core.bridge.Query
import dev.myco.core.bridge.QueryResult
import dev.myco.core.spec.AuthProfile
import dev.myco.core.spec.ModelProfile
import dev.myco.core.spec.ProviderProfile
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BridgeHolder.init(applicationContext)
        setContent { MaterialTheme { MycoApp() } }
    }
}

private sealed interface Screen {
    data object SessionList : Screen
    data class Chat(val id: String, val title: String) : Screen
    data object Settings : Screen
}

private data class SessionRow(val id: String, val title: String, val createdAt: Long)
private data class ChatLine(val role: String, val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MycoApp() {
    val bridge = BridgeHolder.bridge
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<Screen>(Screen.SessionList) }
    var sessions by remember { mutableStateOf<List<SessionRow>>(emptyList()) }

    suspend fun refreshSessions() {
        val res = bridge.query(Query.SessionList(app = null))
        sessions = when (res) {
            is QueryResult.Ok -> parseSessions(res.data as? JsonArray)
            else -> emptyList()
        }
    }

    LaunchedEffect(Unit) { refreshSessions() }

    when (val s = screen) {
        Screen.SessionList -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("myco") },
                    actions = { TextButton(onClick = { screen = Screen.Settings }) { Text("设置") } },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Button(onClick = {
                    scope.launch {
                        val r = bridge.send(Command.SessionCreate(title = "新会话"))
                        refreshSessions()
                        if (r is CommandResult.Ok) {
                            val id = (r.data as? JsonObject)?.get("sessionId")?.jsonPrimitive?.content
                            if (id != null) screen = Screen.Chat(id, "新会话")
                        }
                    }
                }) { Text("新建会话") }
                Spacer(Modifier.height(12.dp))
                if (sessions.isEmpty()) {
                    Text("还没有会话。S0-b 使用假模型（离线）。", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(sessions) { row ->
                            Column(
                                Modifier.fillMaxWidth().clickable { screen = Screen.Chat(row.id, row.title) }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                Text(formatTime(row.createdAt), style = MaterialTheme.typography.bodySmall)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        is Screen.Chat -> ChatScreen(
            bridge = bridge,
            sessionId = s.id,
            title = s.title,
            onBack = {
                scope.launch {
                    refreshSessions()
                    screen = Screen.SessionList
                }
            },
        )

        Screen.Settings -> SettingsScreen(bridge = bridge, onBack = { screen = Screen.SessionList })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(bridge: InProcessBridge, sessionId: String, title: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<ChatLine>>(emptyList()) }
    var streaming by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sessionId) {
        val res = bridge.query(Query.SessionEvents(sessionId, 0))
        lines = if (res is QueryResult.Ok) parseLines(res.data as? JsonArray) else emptyList()
        bridge.events.collect { e ->
            when (e) {
                is BridgeEvent.AssistantDelta -> if (e.sessionId == sessionId) {
                    streaming = (streaming ?: "") + e.text
                }
                is BridgeEvent.AssistantDone -> if (e.sessionId == sessionId) {
                    lines = lines + ChatLine("assistant", e.text); streaming = null
                }
                is BridgeEvent.Failed -> if (streaming != null || error == null) {
                    error = "${e.code}: ${e.message}"; streaming = null
                }
                is BridgeEvent.SessionUpdated -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(lines) { line -> ChatBubble(line) }
                if (streaming != null) item { ChatBubble(ChatLine("assistant", streaming!!), streaming = true) }
                if (error != null) item {
                    Text("错误：$error", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f), placeholder = { Text("说点什么…") }, maxLines = 4,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = input.isNotBlank() && streaming == null,
                    onClick = {
                        val text = input.trim()
                        input = ""; error = null
                        lines = lines + ChatLine("user", text)
                        scope.launch {
                            val r = bridge.send(Command.ChatSend(sessionId, text))
                            if (r is CommandResult.Err) error = "${r.code}: ${r.message}"
                        }
                    },
                ) { Text("发送") }
            }
        }
    }
}

@Composable
private fun ChatBubble(line: ChatLine, streaming: Boolean = false) {
    val isUser = line.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.fillMaxWidth(0.86f).padding(vertical = 2.dp),
        ) {
            Text(
                line.text + if (streaming) " ▍" else "",
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(bridge: InProcessBridge, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checks by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var providers by remember { mutableStateOf<List<ProviderUi>>(emptyList()) }
    var revision by remember { mutableStateOf(0L) }
    var message by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<ProviderEditor?>(null) }
    var discoverStatus by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ProviderUi?>(null) }
    var keyTarget by remember { mutableStateOf<ProviderUi?>(null) }
    var running by remember { mutableStateOf(false) }

    suspend fun reload() {
        val doctorRes = bridge.query(Query.DoctorRun)
        checks = if (doctorRes is QueryResult.Ok) parseChecks(doctorRes.data as? JsonArray) else emptyList()
        val prov = bridge.query(Query.Providers)
        if (prov is QueryResult.Ok) {
            val obj = prov.data as? JsonObject
            revision = obj?.get("revision")?.jsonPrimitive?.longOrNull ?: 0L
            providers = parseProviders(obj)
        }
    }
    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text("模型提供方", style = MaterialTheme.typography.titleMedium) }
            item { Text("revision=$revision · 保存冲突会提示重试", style = MaterialTheme.typography.bodySmall) }
            if (message != null) item {
                Text(message!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (providers.isEmpty()) item {
                Text("还没有 provider（当前用假模型，可离线跑）", style = MaterialTheme.typography.bodySmall)
            }
            items(providers, key = { it.id }) { p ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        "${p.displayName.ifBlank { p.id }}  (${p.kind})",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(p.baseUrl, style = MaterialTheme.typography.bodySmall)
                    Text("models: " + p.models.joinToString { it.id }, style = MaterialTheme.typography.bodySmall)
                    if (p.authRef != null) {
                        Text(
                            if (p.configured) "密钥：已配置（不回显）" else "密钥：缺失 ${p.authRef}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (p.configured) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { discoverStatus = null; editor = p.toEditor() }) { Text("编辑") }
                        if (p.authRef != null) TextButton(onClick = { keyTarget = p }) { Text("设置密钥") }
                        TextButton(onClick = { deleteTarget = p }) { Text("删除") }
                    }
                    HorizontalDivider()
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { discoverStatus = null; editor = ProviderEditor.localTemplate() }) { Text("加本地模板") }
                    OutlinedButton(onClick = { discoverStatus = null; editor = ProviderEditor.deepseekTemplate() }) { Text("加 DeepSeek") }
                }
            }
            item { HorizontalDivider() }
            item { Text("常驻（A7：不承诺 24/7，尽力而为）", style = MaterialTheme.typography.titleMedium) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        context.startForegroundService(Intent(context, MycoForegroundService::class.java))
                        running = true
                    }) { Text("启动前台服务") }
                    OutlinedButton(onClick = {
                        context.stopService(Intent(context, MycoForegroundService::class.java))
                        running = false
                    }) { Text("停止") }
                }
            }
            item {
                Text(if (running) "已请求启动（通知栏应出现常驻通知）" else "未启动", style = MaterialTheme.typography.bodySmall)
            }
            item { HorizontalDivider() }
            item { Text("自检", style = MaterialTheme.typography.titleMedium) }
            if (checks.isEmpty()) item { CircularProgressIndicator(Modifier.height(24.dp)) }
            else items(checks) { pair ->
                Row { Text(pair.first, Modifier.width(96.dp)); Text(pair.second, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    editor?.let { ed ->
        ProviderEditDialog(
            initial = ed,
            discoverStatus = discoverStatus,
            onDiscover = { form ->
                discoverStatus = "获取中…"
                scope.launch {
                    val res = bridge.send(Command.ProviderDiscover(form.toProfile()))
                    discoverStatus = when (res) {
                        is CommandResult.Ok -> "可用模型：${parseDiscoveredIds(res.data)}"
                        is CommandResult.Err -> "${res.code}: ${res.message}"
                    }
                }
            },
            onDismiss = { editor = null; discoverStatus = null },
            onSave = { updated ->
                scope.launch {
                    val res = bridge.send(Command.ProviderSave(updated.toProfile(), revision))
                    message = if (res is CommandResult.Err) "${res.code}: ${res.message}" else null
                    if (res is CommandResult.Ok) {
                        editor = null
                        BridgeHolder.rebuild(context)
                    }
                    reload()
                }
            },
        )
    }

    deleteTarget?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除 ${p.id}？") },
            text = { Text("只从 providers.json 移除；已派生的密钥引用不动。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        val r = bridge.send(Command.ProviderDelete(p.id, revision))
                        if (r is CommandResult.Err) message = "${r.code}: ${r.message}"
                        BridgeHolder.rebuild(context)
                        reload()
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }

    keyTarget?.let { p ->
        val ref = p.authRef
        if (ref != null) {
            KeyDialog(
                ref = ref,
                onDismiss = { keyTarget = null },
                onSave = { value ->
                    keyTarget = null
                    scope.launch {
                        val r = bridge.send(Command.CredentialSet(ref, value))
                        message = if (r is CommandResult.Err) "${r.code}: ${r.message}" else "密钥已保存（不回显）"
                        reload()
                    }
                },
            )
        }
    }
}

@Composable
private fun ProviderEditDialog(
    initial: ProviderEditor,
    discoverStatus: String?,
    onDiscover: (ProviderEditor) -> Unit,
    onDismiss: () -> Unit,
    onSave: (ProviderEditor) -> Unit,
) {
    var form by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.originalId == null) "添加提供方" else "编辑 ${initial.originalId}") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("kind: ${form.kind}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(form.id, { form = form.copy(id = it) }, label = { Text("Provider ID") }, singleLine = true)
                OutlinedTextField(form.baseUrl, { form = form.copy(baseUrl = it) }, label = { Text("Base URL") }, singleLine = true)
                OutlinedTextField(form.modelId, { form = form.copy(modelId = it) }, label = { Text("模型 id") }, singleLine = true)
                OutlinedTextField(form.contextWindow, { form = form.copy(contextWindow = it) }, label = { Text("上下文窗口") }, singleLine = true)
                OutlinedTextField(form.maxTokens, { form = form.copy(maxTokens = it) }, label = { Text("最大输出") }, singleLine = true)
                OutlinedTextField(form.ref, { form = form.copy(ref = it) }, label = { Text("密钥引用（留空=无鉴权）") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { onDiscover(form) }) { Text("测试连通性") }
                    if (discoverStatus != null) {
                        Text(discoverStatus, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(form) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun KeyDialog(ref: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置密钥 $ref") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("只写不读；保存后只显示“已配置”。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value, { value = it }, label = { Text("API key") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onSave(value.trim()) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ---------- provider 编辑模型 ----------

private data class ModelUi(val id: String, val contextWindow: Long, val maxTokens: Long)

private data class ProviderUi(
    val id: String,
    val kind: String,
    val displayName: String,
    val baseUrl: String,
    val authRef: String?,
    val configured: Boolean,
    val models: List<ModelUi>,
) {
    fun toEditor(): ProviderEditor = ProviderEditor(
        originalId = id,
        id = id,
        kind = kind,
        displayName = displayName,
        baseUrl = baseUrl,
        modelId = models.firstOrNull()?.id ?: "",
        contextWindow = (models.firstOrNull()?.contextWindow ?: 32768).toString(),
        maxTokens = (models.firstOrNull()?.maxTokens ?: 4096).toString(),
        ref = authRef ?: "",
    )
}

private data class ProviderEditor(
    val originalId: String?,
    val id: String,
    val kind: String,
    val displayName: String,
    val baseUrl: String,
    val modelId: String,
    val contextWindow: String,
    val maxTokens: String,
    val ref: String,
) {
    fun toProfile(): ProviderProfile = ProviderProfile(
        id = id.trim(),
        kind = kind,
        displayName = displayName.ifBlank { null },
        baseUrl = baseUrl.trim().ifBlank { null },
        auth = if (ref.isBlank()) AuthProfile.None else AuthProfile.ApiKey(ref.trim()),
        models = listOf(
            ModelProfile(
                id = modelId.trim(),
                contextWindow = contextWindow.toLongOrNull() ?: 32768L,
                maxTokens = maxTokens.toLongOrNull() ?: 4096L,
            )
        ),
        roles = mapOf("chat" to modelId.trim(), "fast" to modelId.trim()),
    )

    companion object {
        fun localTemplate() = ProviderEditor(
            originalId = null, id = "local", kind = "openai-completions",
            displayName = "本地 OpenAI 兼容", baseUrl = "http://10.0.2.2:8787/v1",
            modelId = "fake-chat", contextWindow = "32768", maxTokens = "4096", ref = "",
        )

        fun deepseekTemplate() = ProviderEditor(
            originalId = null, id = "deepseek", kind = "deepseek-messages",
            displayName = "DeepSeek 官方", baseUrl = "https://api.deepseek.com/anthropic",
            modelId = "deepseek-flash", contextWindow = "1000000", maxTokens = "384000",
            ref = "DEEPSEEK_API_KEY",
        )
    }
}

private fun parseDiscoveredIds(data: kotlinx.serialization.json.JsonElement?): String {
    val arr = (data as? JsonObject)?.get("models") as? JsonArray ?: return "（空）"
    return arr.mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.content }.joinToString(", ").ifBlank { "（空）" }
}

private fun parseProviders(doc: JsonObject?): List<ProviderUi> {
    val arr = doc?.get("providers") as? JsonArray ?: return emptyList()
    return arr.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val auth = o["auth"] as? JsonObject
        val credential = o["credential"] as? JsonObject
        val models = (o["models"] as? JsonArray)?.mapNotNull { mEl ->
            val m = mEl as? JsonObject ?: return@mapNotNull null
            ModelUi(
                id = m["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                contextWindow = m["contextWindow"]?.jsonPrimitive?.longOrNull ?: 0L,
                maxTokens = m["maxTokens"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        } ?: emptyList()
        ProviderUi(
            id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
            kind = o["kind"]?.jsonPrimitive?.content ?: "",
            displayName = o["displayName"]?.jsonPrimitive?.content ?: "",
            baseUrl = o["baseUrl"]?.jsonPrimitive?.content ?: "",
            authRef = if (auth?.get("type")?.jsonPrimitive?.content == "api-key") auth["ref"]?.jsonPrimitive?.content else null,
            configured = credential?.get("configured")?.jsonPrimitive?.content == "true",
            models = models,
        )
    }
}

// ---------- JSON 解析 ----------

// ---------- JSON 解析 ----------

private fun parseSessions(arr: JsonArray?): List<SessionRow> = arr.orEmpty().mapNotNull { el ->
    val o = el as? JsonObject ?: return@mapNotNull null
    SessionRow(
        id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
        title = o["title"]?.jsonPrimitive?.content ?: "",
        createdAt = o["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
    )
}.sortedByDescending { it.createdAt }

private fun parseLines(arr: JsonArray?): List<ChatLine> = arr.orEmpty().mapNotNull { el ->
    val o = el as? JsonObject ?: return@mapNotNull null
    when (o["type"]?.jsonPrimitive?.content) {
        "user/message" -> ChatLine("user", o["data"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "")
        "assistant/message" -> ChatLine("assistant", o["data"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "")
        else -> null
    }
}

private fun parseChecks(arr: JsonArray?): List<Pair<String, String>> = arr.orEmpty().mapNotNull { el ->
    val o = el as? JsonObject ?: return@mapNotNull null
    (o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null) to
        "${o["status"]?.jsonPrimitive?.content ?: "?"}  ${o["detail"]?.jsonPrimitive?.content ?: ""}"
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()

private fun formatTime(epochMs: Long): String =
    DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMs))

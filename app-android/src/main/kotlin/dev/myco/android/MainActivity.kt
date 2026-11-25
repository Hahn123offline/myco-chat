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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val res = bridge.query(Query.DoctorRun)
        checks = if (res is QueryResult.Ok) parseChecks(res.data as? JsonArray) else emptyList()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("设置") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("模型", style = MaterialTheme.typography.titleMedium)
            Text("S0-b 使用假模型（离线、可复现）。provider 管理与真实 SSE 在 I1。", style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            Text("常驻（A7：不承诺 24/7，尽力而为）", style = MaterialTheme.typography.titleMedium)
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
            Text(if (running) "已请求启动（通知栏应出现常驻通知）" else "未启动", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("自检", style = MaterialTheme.typography.titleMedium)
            if (checks.isEmpty()) {
                CircularProgressIndicator(Modifier.height(24.dp))
            } else {
                checks.forEach { (name, status) ->
                    Row { Text(name, Modifier.width(96.dp)); Text(status, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

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

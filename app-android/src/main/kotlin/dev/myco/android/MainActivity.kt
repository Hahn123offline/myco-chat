package dev.myco.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import dev.myco.android.ui.AppMode
import dev.myco.android.ui.AppPrefs
import dev.myco.android.ui.ChatBubble
import dev.myco.android.ui.ChatComposer
import dev.myco.android.ui.BubbleRole
import dev.myco.android.ui.Divider
import dev.myco.android.ui.EmptyState
import dev.myco.android.ui.InlineError
import dev.myco.android.ui.MycoRadius
import dev.myco.android.ui.MycoSpacing
import dev.myco.android.ui.MycoTheme
import dev.myco.android.ui.MycoTopBar
import dev.myco.android.ui.SectionHeader
import dev.myco.android.ui.ThemeMode
import dev.myco.android.ui.ThemePrefs
import dev.myco.core.bridge.Command
import dev.myco.core.bridge.BridgeEvent
import dev.myco.core.bridge.CommandResult
import dev.myco.core.bridge.InProcessBridge
import dev.myco.core.bridge.Query
import dev.myco.core.bridge.QueryResult
import dev.myco.core.spec.AuthProfile
import dev.myco.core.spec.ModelProfile
import dev.myco.core.spec.MycoJson
import dev.myco.core.spec.ProviderProfile
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonObject
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
        val initial = ThemePrefs.load(this)
        val initialAppMode = AppPrefs.loadMode(this)
        setContent {
            var mode by remember { mutableStateOf(initial) }
            var appMode by remember { mutableStateOf(initialAppMode) }
            MycoTheme(mode) {
                MycoApp(
                    mode,
                    onThemeChange = {
                        mode = it
                        ThemePrefs.save(applicationContext, it)
                    },
                    appMode = appMode,
                    onAppModeChange = {
                        appMode = it
                        AppPrefs.saveMode(applicationContext, it)
                    },
                )
            }
        }
    }
}

private sealed interface Screen {
    data object History : Screen
    data class Chat(val id: String, val title: String) : Screen
    data object Settings : Screen
}

private data class SessionRow(val id: String, val title: String, val createdAt: Long, val snippet: String? = null)
private data class ChatLine(val role: String, val text: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MycoApp(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    appMode: AppMode,
    onAppModeChange: (AppMode) -> Unit,
) {
    val bridge = BridgeHolder.bridge
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<Screen>(Screen.History) }
    var sessions by remember { mutableStateOf<List<SessionRow>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var rowMenu by remember { mutableStateOf<SessionRow?>(null) }
    var renameTarget by remember { mutableStateOf<SessionRow?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SessionRow?>(null) }

    suspend fun refreshSessions() {
        val q = searchQuery.trim()
        val res = if (q.isEmpty()) bridge.query(Query.SessionList(app = null)) else bridge.query(Query.SessionSearch(q))
        sessions = when (res) {
            is QueryResult.Ok -> parseSessions(res.data as? JsonArray)
            else -> emptyList()
        }
    }

    fun createSession() {
        scope.launch {
            val r = bridge.send(Command.SessionCreate(title = "新会话"))
            refreshSessions()
            if (r is CommandResult.Ok) {
                val id = (r.data as? JsonObject)?.get("sessionId")?.jsonPrimitive?.content
                if (id != null) screen = Screen.Chat(id, "新会话")
            }
        }
    }

    fun openMostRecent() {
        val first = sessions.firstOrNull() ?: return
        screen = Screen.Chat(first.id, first.title)
    }

    LaunchedEffect(Unit) {
        refreshSessions()
        if (appMode == AppMode.CHAT) openMostRecent()
    }
    LaunchedEffect(searchQuery) { refreshSessions() }

    when (val s = screen) {
        Screen.History -> HistoryScreen(
            title = if (appMode == AppMode.CHAT) "历史" else "myco",
            showBack = appMode == AppMode.CHAT && sessions.isNotEmpty(),
            onBack = { openMostRecent() },
            sessions = sessions,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onOpen = { screen = Screen.Chat(it.id, it.title) },
            onLongPress = { rowMenu = it },
            onCreate = if (appMode == AppMode.WORK) ({ createSession() }) else null,
            onSettings = { screen = Screen.Settings },
            appMode = appMode,
        )

        is Screen.Chat -> ChatScreen(
            bridge = bridge,
            sessionId = s.id,
            title = s.title,
            onBack = if (appMode == AppMode.WORK) ({
                scope.launch {
                    refreshSessions()
                    screen = Screen.History
                }
            }) else null,
            onHistory = if (appMode == AppMode.CHAT) ({ screen = Screen.History }) else null,
        )

        Screen.Settings -> SettingsScreen(
            bridge = bridge,
            themeMode = themeMode,
            onThemeChange = onThemeChange,
            appMode = appMode,
            onAppModeChange = {
                searchQuery = ""
                onAppModeChange(it)
            },
            onBack = { screen = Screen.History },
        )
    }

    rowMenu?.let { row ->
        AlertDialog(
            onDismissRequest = { rowMenu = null },
            title = { Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { Text("对会话做什么？") },
            confirmButton = {
                TextButton(onClick = { rowMenu = null; renameTarget = row; renameText = row.title }) { Text("改名") }
            },
            dismissButton = {
                TextButton(onClick = { rowMenu = null; deleteTarget = row }) { Text("删除") }
            },
        )
    }
    renameTarget?.let { row ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = row
                    renameTarget = null
                    scope.launch {
                        bridge.send(Command.SessionRename(target.id, renameText))
                        refreshSessions()
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }
    deleteTarget?.let { row ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除会话？") },
            text = { Text("「${row.title}」会从列表隐藏（软删，日志保留）。") },
            confirmButton = {
                TextButton(onClick = {
                    val target = row
                    deleteTarget = null
                    scope.launch {
                        bridge.send(Command.SessionDelete(target.id))
                        refreshSessions()
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun HistoryScreen(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    sessions: List<SessionRow>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (SessionRow) -> Unit,
    onLongPress: (SessionRow) -> Unit,
    onCreate: (() -> Unit)?,
    onSettings: () -> Unit,
    appMode: AppMode,
) {
    Scaffold(
        topBar = {
            MycoTopBar(
                title = title,
                onBack = if (showBack) onBack else null,
                actions = { TextButton(onClick = onSettings) { Text("设置") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = MycoSpacing.l, vertical = MycoSpacing.s),
                placeholder = { Text("搜索会话或消息") },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(MycoRadius.control),
            )
            when {
                sessions.isEmpty() && query.isBlank() -> EmptyState(
                    title = if (appMode == AppMode.CHAT) "开始聊天" else "还没有会话",
                    message = if (appMode == AppMode.CHAT) "这里是你的历史；新对话会出现在这里。" else "新建一个会话开始。",
                    actionLabel = onCreate?.let { "新建会话" },
                    onAction = onCreate,
                )
                sessions.isEmpty() -> EmptyState(
                    title = "没有匹配",
                    message = "换个词试试，或清空搜索。",
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = MycoSpacing.l, vertical = MycoSpacing.s),
                ) {
                    items(sessions, key = { it.id }) { row ->
                        Column(
                            Modifier.fillMaxWidth()
                                .combinedClickable(onClick = { onOpen(row) }, onLongClick = { onLongPress(row) })
                                .padding(vertical = MycoSpacing.m)
                        ) {
                            Text(
                                row.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(formatTime(row.createdAt), style = MaterialTheme.typography.bodySmall)
                            row.snippet?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = MycoSpacing.xs),
                                )
                            }
                        }
                        Divider()
                    }
                    if (onCreate != null) {
                        item {
                            Box(Modifier.padding(vertical = MycoSpacing.m)) {
                                Button(onClick = onCreate) { Text("新建会话") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    bridge: InProcessBridge,
    sessionId: String,
    title: String,
    onBack: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
) {
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
                    if (e.text.isNotEmpty()) lines = lines + ChatLine("assistant", e.text)
                    streaming = null
                }
                is BridgeEvent.Failed -> if (streaming != null || error == null) {
                    error = "${e.code}: ${e.message}"; streaming = null
                }
                is BridgeEvent.SessionUpdated -> Unit
                is BridgeEvent.ToolStarted -> if (e.sessionId == sessionId) {
                    lines = lines + ChatLine("system", "正在使用工具：${e.name}")
                }
                is BridgeEvent.ToolFinished -> if (e.sessionId == sessionId) {
                    lines = lines + ChatLine("system", "工具 ${e.name} ${if (e.ok) "完成" else "失败"}")
                }
            }
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || streaming != null) return
        input = ""; error = null
        lines = lines + ChatLine("user", text)
        scope.launch {
            val r = bridge.send(Command.ChatSend(sessionId, text))
            if (r is CommandResult.Err) error = "${r.code}: ${r.message}"
        }
    }

    Scaffold(
        topBar = {
            MycoTopBar(
                title = title,
                onBack = onBack,
                actions = {
                    if (onHistory != null) TextButton(onClick = onHistory) { Text("历史") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            val listState = rememberLazyListState()
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            val context = LocalContext.current
            val atBottom by remember {
                androidx.compose.runtime.derivedStateOf {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()
                    last == null || last.index >= info.totalItemsCount - 2
                }
            }
            LaunchedEffect(lines.size, streaming?.length) {
                val target = listState.layoutInfo.totalItemsCount - 1
                if (target >= 0 && atBottom) listState.scrollToItem(target)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = MycoSpacing.l, vertical = MycoSpacing.m),
                verticalArrangement = Arrangement.spacedBy(MycoSpacing.s),
            ) {
                items(lines) { line ->
                    ChatBubble(
                        role = when (line.role) {
                            "user" -> BubbleRole.USER
                            "assistant" -> BubbleRole.ASSISTANT
                            "error" -> BubbleRole.ERROR
                            else -> BubbleRole.SYSTEM
                        },
                        text = line.text,
                        onLongPress = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(line.text))
                            android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                if (streaming != null) item {
                    ChatBubble(BubbleRole.ASSISTANT, streaming!!, streaming = true)
                }
                if (error != null) item {
                    InlineError(error!!)
                }
            }
            Box(Modifier.padding(MycoSpacing.m)) {
                ChatComposer(
                    value = input,
                    onValueChange = { input = it },
                    onSend = { send() },
                    onStop = { scope.launch { bridge.send(Command.ChatStop(sessionId)) } },
                    streaming = streaming != null,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    bridge: InProcessBridge,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    appMode: AppMode,
    onAppModeChange: (AppMode) -> Unit,
    onBack: () -> Unit,
) {
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

    // 导入/导出走系统文件选择器：不依赖 adb，也不把密钥写进文件（auth 只有 ref）。
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val res = bridge.query(Query.ProviderExport)
                if (res is QueryResult.Ok) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(res.data.toString().toByteArray(Charsets.UTF_8))
                        }
                    }.onSuccess { message = "已导出 provider 文件（不含密钥值）" }
                        .onFailure { message = "导出失败：${it.message}" }
                } else message = "导出失败：读取 provider 失败"
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("空文件")
                    val root = MycoJson.parseToJsonElement(text).jsonObject
                    val payload = root["providers"] ?: root["data"] ?: root
                    val list = when (payload) {
                        is JsonArray -> MycoJson.decodeFromJsonElement(ListSerializer(ProviderProfile.serializer()), payload)
                        is JsonObject -> MycoJson.decodeFromJsonElement(
                            dev.myco.core.spec.ProviderFile.serializer(), payload
                        ).providers
                        else -> error("不认识的 JSON 形状")
                    }
                    val r = bridge.send(Command.ProviderReplace(list, revision))
                    if (r is CommandResult.Err) error("${r.code}: ${r.message}")
                    BridgeHolder.rebuild(context)
                    reload()
                    message = "已导入 ${list.size} 个 provider"
                }.onFailure { message = "导入失败：${it.message}" }
            }
        }
    }

    Scaffold(
        topBar = { MycoTopBar(title = "设置", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = MycoSpacing.l),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { SectionHeader("外观") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(MycoSpacing.s)) {
                    ThemeMode.values().forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onThemeChange(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "跟随系统"
                                        ThemeMode.LIGHT -> "浅色"
                                        ThemeMode.DARK -> "深色"
                                    }
                                )
                            },
                        )
                    }
                }
            }
            item { Divider() }
            item { SectionHeader("应用形态") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(MycoSpacing.s)) {
                    FilterChip(
                        selected = appMode == AppMode.CHAT,
                        onClick = { onAppModeChange(AppMode.CHAT) },
                        label = { Text("聊天（单焦点）") },
                    )
                    FilterChip(
                        selected = appMode == AppMode.WORK,
                        onClick = { onAppModeChange(AppMode.WORK) },
                        label = { Text("工作（多会话）") },
                    )
                }
            }
            item {
                Text(
                    if (appMode == AppMode.CHAT) "打开就是最近一次对话；历史在顶栏「历史」里。"
                    else "以会话列表为首页，适合多任务。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Divider() }
            item { SectionHeader("模型提供方") }
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
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("myco-providers.json") }) { Text("导出") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("导入") }
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
            displayName = "本地 OpenAI 兼容", baseUrl = "http://127.0.0.1:8787/v1",
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

private fun parseSessions(arr: JsonArray?): List<SessionRow> = arr.orEmpty().mapNotNull { el ->
    val o = el as? JsonObject ?: return@mapNotNull null
    SessionRow(
        id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
        title = o["title"]?.jsonPrimitive?.content ?: "",
        createdAt = o["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
        snippet = o["snippet"]?.jsonPrimitive?.content,
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




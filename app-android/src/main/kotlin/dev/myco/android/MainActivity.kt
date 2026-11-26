package dev.myco.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
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
import dev.myco.android.ui.ChatBubble
import dev.myco.android.ui.ChatComposer
import dev.myco.android.ui.BubbleRole
import dev.myco.android.ui.Divider
import dev.myco.android.ui.EmptyState
import dev.myco.android.ui.GlassSurface
import dev.myco.android.ui.InlineError
import dev.myco.android.ui.MycoDrawerContent
import dev.myco.android.ui.MycoRadius
import dev.myco.android.ui.MycoSpacing
import dev.myco.android.ui.SearchField
import dev.myco.android.ui.SessionListItem
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
        setContent {
            var mode by remember { mutableStateOf(initial) }
            MycoTheme(mode) {
                MycoApp(
                    mode,
                    onThemeChange = {
                        mode = it
                        ThemePrefs.save(applicationContext, it)
                    },
                )
            }
        }
    }
}

private sealed interface Screen {
    data class Chat(val id: String, val title: String) : Screen
    data object Settings : Screen
}

private data class SessionRow(val id: String, val title: String, val createdAt: Long, val snippet: String? = null)
private data class ChatLine(val role: String, val text: String)

private fun SessionRow.toUi(): dev.myco.android.ui.model.SessionItemUi {
    val now = java.time.LocalDate.now()
    val date = java.time.Instant.ofEpochMilli(createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val group = when {
        date == now -> dev.myco.android.ui.model.SessionGroup.TODAY
        date == now.minusDays(1) -> dev.myco.android.ui.model.SessionGroup.YESTERDAY
        date.isAfter(now.minusDays(7)) -> dev.myco.android.ui.model.SessionGroup.WEEK
        else -> dev.myco.android.ui.model.SessionGroup.EARLIER
    }
    return dev.myco.android.ui.model.SessionItemUi(
        id = id,
        title = title,
        timeLabel = formatTime(createdAt),
        snippet = snippet,
        group = group,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MycoApp(themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    val bridge = BridgeHolder.bridge
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var screen by remember { mutableStateOf<Screen?>(null) }
    var sessions by remember { mutableStateOf<List<SessionRow>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var rowMenu by remember { mutableStateOf<SessionRow?>(null) }
    var renameTarget by remember { mutableStateOf<SessionRow?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SessionRow?>(null) }
    val snackbar = remember { SnackbarHostState() }

    suspend fun refreshSessions() {
        val q = searchQuery.trim()
        val res = if (q.isEmpty()) bridge.query(Query.SessionList(app = null)) else bridge.query(Query.SessionSearch(q))
        sessions = when (res) {
            is QueryResult.Ok -> parseSessions(res.data as? JsonArray)
            else -> emptyList()
        }
        val current = screen
        if (current == null && sessions.isNotEmpty()) {
            screen = Screen.Chat(sessions.first().id, sessions.first().title)
        } else if (searchQuery.isBlank() && current is Screen.Chat && sessions.none { it.id == current.id }) {
            // 只在实际删除后切走；抽屉里搜索过滤不应改变当前打开的会话
            screen = sessions.firstOrNull()?.let { Screen.Chat(it.id, it.title) }
        }
    }

    fun openMostRecent() {
        screen = sessions.firstOrNull()?.let { Screen.Chat(it.id, it.title) }
    }

    fun createSession() {
        scope.launch {
            val r = bridge.send(Command.SessionCreate(title = "新会话"))
            searchQuery = ""
            refreshSessions()
            if (r is CommandResult.Ok) {
                val id = (r.data as? JsonObject)?.get("sessionId")?.jsonPrimitive?.content
                if (id != null) screen = Screen.Chat(id, "新会话")
            }
            drawerState.close()
        }
    }

    LaunchedEffect(Unit) { refreshSessions() }
    LaunchedEffect(searchQuery) { refreshSessions() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false, // MVP 只做汉堡入口；边缘滑交给系统返回，避免冲突
        drawerContent = {
            ModalDrawerSheet {
                MycoDrawerContent(
                    sessions = sessions.map { it.toUi() },
                    selectedId = (screen as? Screen.Chat)?.id,
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSelect = {
                        screen = Screen.Chat(it.id, it.title)
                        scope.launch { drawerState.close() }
                    },
                    onSessionLongPress = { rowMenu = sessions.firstOrNull { s -> s.id == it.id } },
                    onNewChat = { createSession() },
                    onSettings = {
                        screen = Screen.Settings
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            when (val s = screen) {
                null -> Scaffold(
                    topBar = { MycoTopBar(title = "myco", onMenu = { scope.launch { drawerState.open() } }) },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        EmptyState(
                            title = "开始聊天",
                            message = "这是你的本地对话基座。历史都在左侧抽屉里。",
                            actionLabel = "开始新对话",
                            onAction = { createSession() },
                        )
                    }
                }
                is Screen.Chat -> ChatScreen(
                    bridge = bridge,
                    sessionId = s.id,
                    title = s.title,
                    onMenu = { scope.launch { drawerState.open() } },
                )
                Screen.Settings -> SettingsScreen(
                    bridge = bridge,
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    onBack = {
                        screen = sessions.firstOrNull()?.let { Screen.Chat(it.id, it.title) }
                    },
                )
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
            )
            // 抽屉打开时返回只关抽屉；聊天/空态根页返回打开抽屉（不是退出 App）；设置页返回上一个会话
            BackHandler(enabled = drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }
            BackHandler(enabled = drawerState.isClosed && screen is Screen.Chat) {
                scope.launch { drawerState.open() }
            }
            BackHandler(enabled = drawerState.isClosed && screen == null) {
                scope.launch { drawerState.open() }
            }
            BackHandler(enabled = drawerState.isClosed && screen is Screen.Settings) {
                screen = sessions.firstOrNull()?.let { Screen.Chat(it.id, it.title) }
            }
        }
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
            text = { Text("「${row.title}」会进入回收站，可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    val target = row
                    deleteTarget = null
                    scope.launch {
                        bridge.send(Command.SessionDelete(target.id))
                        refreshSessions()
                        val result = snackbar.showSnackbar(
                            message = "已删除「${target.title}」",
                            actionLabel = "撤销",
                            duration = androidx.compose.material3.SnackbarDuration.Short,
                        )
                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                            bridge.send(Command.SessionRestore(target.id))
                            refreshSessions()
                        }
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    bridge: InProcessBridge,
    sessionId: String,
    title: String,
    onMenu: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<ChatLine>>(emptyList()) }
    var streaming by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<List<dev.myco.core.spec.AttachmentRef>>(emptyList()) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val context = LocalContext.current

    fun attachUri(uri: android.net.Uri) {
        scope.launch {
            runCatching {
                val resolver = context.contentResolver
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("读取附件失败")
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                val name = displayName(context, uri)
                val r = bridge.send(
                    Command.AttachmentSave(
                        sessionId = sessionId,
                        name = name,
                        mime = mime,
                        base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP),
                    )
                )
                if (r is CommandResult.Err) error("${r.code}: ${r.message}")
                val o = (r as CommandResult.Ok).data as JsonObject
                dev.myco.core.spec.AttachmentRef(
                    id = o["id"]!!.jsonPrimitive.content,
                    name = o["name"]!!.jsonPrimitive.content,
                    mime = o["mime"]!!.jsonPrimitive.content,
                    size = o["size"]!!.jsonPrimitive.content.toLong(),
                    kind = o["kind"]!!.jsonPrimitive.content,
                )
            }.onSuccess { pending = pending + it }
                .onFailure { error = "附件失败：${it.message}" }
        }
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { attachUri(it) }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { attachUri(it) }
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { attachUri(it) }
    }
    fun launchCamera() {
        runCatching {
            val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
            val file = java.io.File(dir, "camera_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            cameraUri = uri
            takePicture.launch(uri)
        }.onFailure { error = "相机不可用：${it.message}" }
    }

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
        if ((text.isEmpty() && pending.isEmpty()) || streaming != null) return
        val outgoing = pending
        input = ""; pending = emptyList(); error = null
        lines = lines + ChatLine("user", text + if (outgoing.isEmpty()) "" else "\n" + outgoing.joinToString("\n") { "[附件] ${it.name}" })
        scope.launch {
            val r = bridge.send(Command.ChatSend(sessionId, text, outgoing))
            if (r is CommandResult.Err) error = "${r.code}: ${r.message}"
        }
    }

    Scaffold(
        topBar = { MycoTopBar(title = title, onMenu = onMenu) },
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
            if (!atBottom && lines.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().padding(horizontal = MycoSpacing.m)) {
                    TextButton(onClick = {
                        scope.launch {
                            val target = listState.layoutInfo.totalItemsCount - 1
                            if (target >= 0) listState.scrollToItem(target)
                        }
                    }) { Text("↓ 回到最新") }
                }
            }
            if (pending.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = MycoSpacing.m),
                    horizontalArrangement = Arrangement.spacedBy(MycoSpacing.s),
                ) {
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(MycoSpacing.s),
                    ) {
                        pending.forEach { ref ->
                            androidx.compose.material3.AssistChip(
                                onClick = { pending = pending - ref },
                                label = { Text(ref.name, maxLines = 1) },
                            )
                        }
                    }
                    TextButton(onClick = { pending = emptyList() }) { Text("清空") }
                }
            }
            Box(Modifier.padding(MycoSpacing.m)) {
                ChatComposer(
                    value = input,
                    onValueChange = { input = it },
                    onSend = { send() },
                    onStop = { scope.launch { bridge.send(Command.ChatStop(sessionId)) } },
                    streaming = streaming != null,
                    enabled = input.isNotBlank() || pending.isNotEmpty(),
                    onAttach = { showAttachSheet = true },
                )
            }
        }
    }

    if (showAttachSheet) {
        val sheetState = androidx.compose.material3.rememberModalBottomSheetState()
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            sheetState = sheetState,
        ) {
            Column(Modifier.fillMaxWidth().padding(MycoSpacing.l)) {
                Text("添加附件", style = MaterialTheme.typography.titleMedium)
                Text(
                    "附件只存在本机应用私有目录；发送时才会进入模型上下文。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = MycoSpacing.s),
                )
                TextButton(onClick = { showAttachSheet = false; launchCamera() }) { Text("拍照") }
                TextButton(onClick = {
                    showAttachSheet = false
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("相册") }
                TextButton(onClick = {
                    showAttachSheet = false
                    pickFile.launch(arrayOf("*/*"))
                }) { Text("文件") }
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
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(MycoSpacing.m)) {
                            Text(
                                "${p.displayName.ifBlank { p.id }}  (${p.kind})",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(p.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("models: " + p.models.joinToString { it.id }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        }
                    }
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

private fun displayName(context: android.content.Context, uri: android.net.Uri): String {
    val fallback = uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
    return runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx)
                else fallback
            } else fallback
        } ?: fallback
    }.getOrDefault(fallback)
}

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
        "user/message" -> {
            val data = o["data"] as? JsonObject
            val text = data?.get("text")?.jsonPrimitive?.content ?: ""
            val names = (data?.get("attachments") as? JsonArray)?.mapNotNull {
                (it as? JsonObject)?.get("name")?.jsonPrimitive?.content
            }.orEmpty()
            ChatLine(
                "user",
                text + if (names.isEmpty()) "" else "\n" + names.joinToString("\n") { "[附件] $it" },
            )
        }
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







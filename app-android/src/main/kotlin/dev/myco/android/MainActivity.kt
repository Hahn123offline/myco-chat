package dev.myco.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.PermanentDrawerSheet
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
import androidx.compose.ui.graphics.graphicsLayer
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
import dev.myco.android.ui.SettingsCard
import dev.myco.android.ui.SettingsRow
import dev.myco.android.ui.MycoTheme
import dev.myco.android.ui.MycoTopBar
import dev.myco.android.ui.SectionHeader
import dev.myco.android.ui.rememberPredictiveNavState
import dev.myco.android.ui.swipeRightToExit
import dev.myco.android.ui.ThemeMode
import dev.myco.android.ui.ThemePrefs
import dev.myco.android.ui.swipeRightToOpenDrawer
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
import dev.myco.core.spec.ReasoningProfile
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 会话窗口的滚动状态：放在设置/回收站覆盖层之外，页面重建时也能保住阅读位置。 */
private class ChatScrollState {
    val list = androidx.compose.foundation.lazy.LazyListState()
    var initialBottomDone by mutableStateOf(false)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BridgeHolder.init(applicationContext)
        val initial = ThemePrefs.load(this)
        setContent {
            var mode by remember { mutableStateOf(initial) }
            val galleryMode = remember { intent?.getBooleanExtra("m5_gallery", false) == true }
            val chatSampleMode = remember { intent?.getBooleanExtra("m5_chat", false) == true }
            MycoTheme(mode) {
                if (galleryMode) {
                    val dark = when (mode) {
                        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                        ThemeMode.LIGHT -> false
                        ThemeMode.DARK -> true
                    }
                    dev.myco.android.ui.m5.M5Gallery(
                        dark = dark,
                        onToggleDark = { mode = if (dark) ThemeMode.LIGHT else ThemeMode.DARK },
                        onClose = { finish() },
                    )
                } else if (chatSampleMode) {
                    val dark = when (mode) {
                        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                        ThemeMode.LIGHT -> false
                        ThemeMode.DARK -> true
                    }
                    dev.myco.android.ui.m5.M5ChatSample(
                        dark = dark,
                        onToggleDark = { mode = if (dark) ThemeMode.LIGHT else ThemeMode.DARK },
                        onClose = { finish() },
                    )
                } else {
                    MycoApp(
                        mode,
                        onThemeChange = {
                            mode = it
                            ThemePrefs.save(applicationContext, it)
                        },
                        m5Ui = remember { intent?.getBooleanExtra("m5_chat_ui", false) == true },
                    )
                }
            }
        }
    }
}

private sealed interface Screen {
    data class Chat(val id: String, val title: String) : Screen
    data object Settings : Screen
    data class SessionBin(val kind: BinKind) : Screen
}

private enum class BinKind { ARCHIVED, DELETED }

private data class SessionRow(
    val id: String,
    val title: String,
    val createdAt: Long,
    val snippet: String? = null,
    val lastMessageAt: Long = createdAt,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val deleted: Boolean = false,
    val messageCount: Int = 0,
    val draft: Boolean = false,
)
private data class ChatLine(val role: String, val text: String)

private fun SessionRow.toUi(): dev.myco.android.ui.model.SessionItemUi {
    val now = java.time.LocalDate.now()
    val date = java.time.Instant.ofEpochMilli(lastMessageAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val group = when {
        pinned -> dev.myco.android.ui.model.SessionGroup.PINNED
        date == now -> dev.myco.android.ui.model.SessionGroup.TODAY
        date == now.minusDays(1) -> dev.myco.android.ui.model.SessionGroup.YESTERDAY
        date.isAfter(now.minusDays(7)) -> dev.myco.android.ui.model.SessionGroup.WEEK
        else -> dev.myco.android.ui.model.SessionGroup.EARLIER
    }
    return dev.myco.android.ui.model.SessionItemUi(
        id = id,
        title = title,
        timeLabel = formatTime(lastMessageAt),
        snippet = snippet,
        group = group,
        pinned = pinned,
        unread = false,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MycoApp(themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit, m5Ui: Boolean = false) {
    val bridge = BridgeHolder.bridge
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var screen by remember { mutableStateOf<Screen?>(null) }
    var sessions by remember { mutableStateOf<List<SessionRow>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var rowMenu by remember { mutableStateOf<SessionRow?>(null) }
    var batchMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var renameTarget by remember { mutableStateOf<SessionRow?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SessionRow?>(null) }
    // 当前活跃的会话窗口：聊天页常驻基座用它作为唯一真源（打开会话时同步更新，避免异步 effect 造成重建）
    var lastChatScreen by remember { mutableStateOf<Screen.Chat?>(null) }
    fun showChat(chat: Screen.Chat) {
        lastChatScreen = chat
        screen = chat
    }
    // 每个会话一份滚动状态，挂在页面生命周期之外：进出设置/回收站时阅读位置不丢
    val chatScroll = remember(lastChatScreen?.id) { ChatScrollState() }
    // 可复用的预测式退出进度：抽屉收起 / 设置页 / 回收站各一份（M5 在这里叠加更多动效）
    val drawerNav = rememberPredictiveNavState()
    val settingsNav = rememberPredictiveNavState()
    val binNav = rememberPredictiveNavState()

    // 设置/回收站都是"从右侧滑入"（退出时向右滑出），与预测式返回共用同一个 progress
    fun openSettings() {
        settingsNav.prepareEnter()
        screen = Screen.Settings
        scope.launch { settingsNav.animateEnter() }
    }

    fun openBin(kind: BinKind) {
        binNav.prepareEnter()
        screen = Screen.SessionBin(kind)
        scope.launch { binNav.animateEnter() }
    }
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
            // 启动时不自动选"最近会话"：交给 ensureDraft 打开唯一的新会话草稿
        } else if (searchQuery.isBlank() && current is Screen.Chat && sessions.none { it.id == current.id }) {
            // 会话被删除/归档：待 openDraft 接管，不跳到"下一个会话"
            screen = null
        }
    }

    suspend fun openDraft(open: Boolean = true): String? {
        val r = bridge.send(Command.SessionEnsureDraft())
        if (r !is CommandResult.Ok) return null
        val id = (r.data as? JsonObject)?.get("sessionId")?.jsonPrimitive?.content ?: return null
        if (open) {
            searchQuery = ""
            showChat(Screen.Chat(id, "新会话"))
        }
        refreshSessions()
        return id
    }

    fun openMostRecent() {
        screen = sessions.firstOrNull()?.let { Screen.Chat(it.id, it.title) }
    }

    fun createSession() {
        scope.launch {
            openDraft()
            drawerState.close()
        }
    }

    LaunchedEffect(Unit) {
        refreshSessions()
        openDraft()
    }
    LaunchedEffect(searchQuery) { refreshSessions() }
    // 打开抽屉时刷新标题/预览（标题是首轮后异步生成的）
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) refreshSessions()
    }
    // 标题生成/重命名后立刻同步到聊天顶栏（不用等打开抽屉刷新）
    LaunchedEffect(Unit) {
        bridge.events.collect { e ->
            if (e is BridgeEvent.SessionUpdated) {
                val cur = screen as? Screen.Chat
                if (cur != null && cur.id == e.sessionId) {
                    val res = bridge.query(Query.SessionList(app = null))
                    if (res is QueryResult.Ok) {
                        val row = parseSessions(res.data as? JsonArray).firstOrNull { it.id == cur.id }
                        if (row != null && row.title.isNotBlank() && row.title != cur.title) {
                            showChat(Screen.Chat(cur.id, row.title))
                        }
                    }
                }
                if (drawerState.isOpen) refreshSessions()
            }
        }
    }

    val drawerBody: @Composable () -> Unit = {
        MycoDrawerContent(
            sessions = sessions
                .filter { !it.draft || it.id == (screen as? Screen.Chat)?.id }
                .map { it.toUi() },
            selectedId = (screen as? Screen.Chat)?.id,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSelect = {
                if (batchMode) {
                    selectedIds = if (it.id in selectedIds) selectedIds - it.id else selectedIds + it.id
                } else {
                    showChat(Screen.Chat(it.id, it.title))
                    scope.launch { drawerState.close() }
                }
            },
            onSessionLongPress = { if (!batchMode) rowMenu = sessions.firstOrNull { s -> s.id == it.id } },
            onNewChat = { createSession() },
            onSettings = {
                scope.launch {
                    drawerState.close()
                    openSettings()
                }
            },
            batchMode = batchMode,
            selectedIds = selectedIds,
            onToggleBatch = {
                batchMode = !batchMode
                selectedIds = emptySet()
            },
            onToggleSelected = { id ->
                selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
            },
            onSelectAll = { selectedIds = sessions.map { it.id }.toSet() },
            onBatchArchive = {
                val ids = selectedIds
                scope.launch {
                    val activeId = (screen as? Screen.Chat)?.id
                    ids.forEach { bridge.send(Command.SessionArchive(it, true)) }
                    batchMode = false; selectedIds = emptySet(); drawerState.close()
                    if (activeId != null && activeId in ids) openDraft() else refreshSessions()
                    val r = snackbar.showSnackbar(
                        message = "已归档 ${ids.size} 个会话", actionLabel = "撤销",
                        duration = androidx.compose.material3.SnackbarDuration.Short,
                    )
                    if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        ids.forEach { bridge.send(Command.SessionArchive(it, false)) }
                        refreshSessions()
                    }
                }
            },
            onBatchDelete = {
                val ids = selectedIds
                scope.launch {
                    val activeId = (screen as? Screen.Chat)?.id
                    ids.forEach { bridge.send(Command.SessionDelete(it)) }
                    batchMode = false; selectedIds = emptySet(); drawerState.close()
                    if (activeId != null && activeId in ids) openDraft() else refreshSessions()
                    val r = snackbar.showSnackbar(
                        message = "已删除 ${ids.size} 个会话", actionLabel = "撤销",
                        duration = androidx.compose.material3.SnackbarDuration.Short,
                    )
                    if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        ids.forEach { bridge.send(Command.SessionRestore(it)) }
                        refreshSessions()
                    }
                }
            },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val screenWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        // 设置页的返回目标：进入设置前那个活跃会话窗口（这就是"聊天窗口→多会话→设置→聊天窗口"的环）
        fun leaveSettings() {
            val base = lastChatScreen
            if (base != null && sessions.any { it.id == base.id }) {
                showChat(base)
            } else {
                scope.launch { openDraft() }
            }
        }
        val main: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize()) {
                // 聊天窗口常驻基座：设置/回收站覆盖其上，退出动画露出的是真实会话内容。
                // 用 lastChatScreen 而不是 screen 分支推导，保证进入/离开设置时这个调用点稳定、状态不重建
                val chat = lastChatScreen
                if (chat != null) {
                    androidx.compose.runtime.key(chat.id) {
                        ChatScreen(
                            bridge = bridge,
                            sessionId = chat.id,
                            title = chat.title,
                            onMenu = if (wide) null else ({ scope.launch { drawerState.open() } }),
                            onSwipeOpenDrawer = if (wide || screen !is Screen.Chat) null
                                else ({ scope.launch { drawerState.open() } }),
                            scroll = chatScroll,
                            m5Ui = m5Ui,
                        )
                    }
                }
                // 设置层：自己是当前页时跟随 settingsNav 右滑；作为回收站下面的露出层时保持静止
                if (screen is Screen.Settings || screen is Screen.SessionBin) {
                    val settingsCurrent = screen is Screen.Settings
                    // 手势接在"未平移"的外层：平移只能加在内层，否则图形层变换会改变指针局部坐标，
                    // 让 dx 和 translation 互相喂出振荡（按住不放时页面抽搐的根因）
                    Box(
                        Modifier.fillMaxSize().then(
                            if (settingsCurrent) {
                                Modifier.swipeRightToExit(settingsNav, enabled = true) { leaveSettings() }
                            } else {
                                Modifier
                            },
                        ),
                    ) {
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                translationX = if (settingsCurrent) settingsNav.progress * screenWidthPx else 0f
                            },
                        ) {
                            SettingsScreen(
                                bridge = bridge,
                                themeMode = themeMode,
                                onThemeChange = onThemeChange,
                                onOpenBin = { kind -> openBin(kind) },
                                onBack = { scope.launch { settingsNav.exit { leaveSettings() } } },
                            )
                        }
                    }
                }
                // 回收站/归档：叠在设置层上，右滑退出回设置
                val bin = screen as? Screen.SessionBin
                if (bin != null) {
                    Box(
                        Modifier.fillMaxSize()
                            .swipeRightToExit(binNav, enabled = true) { screen = Screen.Settings },
                    ) {
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                translationX = binNav.progress * screenWidthPx
                            },
                        ) {
                            SessionBinScreen(
                                bridge = bridge,
                                kind = bin.kind,
                                onBack = { scope.launch { binNav.exit { screen = Screen.Settings } } },
                            )
                        }
                    }
                }
                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
                )
                if (!wide) {
                    // 会话管理（抽屉）打开时返回 = 预测式关闭抽屉（抽屉内容 + 遮罩一起跟手）
                    PredictiveBackHandler(enabled = drawerState.isOpen) { progress ->
                        var first = true
                        var jumped = false
                        drawerNav.beginSystemGesture()
                        try {
                            progress.collect { event ->
                                // 按键/无中间进度直接到 1：当作"常规退出"，从 0 播同一套退出动画
                                if (first && event.progress >= 0.95f) jumped = true
                                first = false
                                if (!jumped) drawerNav.onGestureProgress(event.progress)
                            }
                            if (jumped) drawerNav.reset()
                            drawerNav.exit { drawerState.snapTo(DrawerValue.Closed) }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } finally {
                            drawerNav.endSystemGesture()
                            drawerNav.reset()
                        }
                    }
                }
                // 设置返回 = 回当前活跃会话窗口（预测式：设置页向右滑出）
                PredictiveBackHandler(enabled = drawerState.isClosed && screen is Screen.Settings) { progress ->
                    var first = true
                    var jumped = false
                    settingsNav.beginSystemGesture()
                    try {
                        progress.collect { event ->
                            if (first && event.progress >= 0.95f) jumped = true
                            first = false
                            if (!jumped) settingsNav.onGestureProgress(event.progress)
                        }
                        if (jumped) settingsNav.reset()
                        settingsNav.exit { leaveSettings() }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } finally {
                        settingsNav.endSystemGesture()
                        settingsNav.reset()
                    }
                }
                // 回收站/归档返回 = 右滑回设置
                PredictiveBackHandler(enabled = drawerState.isClosed && screen is Screen.SessionBin) { progress ->
                    var first = true
                    var jumped = false
                    binNav.beginSystemGesture()
                    try {
                        progress.collect { event ->
                            if (first && event.progress >= 0.95f) jumped = true
                            first = false
                            if (!jumped) binNav.onGestureProgress(event.progress)
                        }
                        if (jumped) binNav.reset()
                        binNav.exit { screen = Screen.Settings }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } finally {
                        binNav.endSystemGesture()
                        binNav.reset()
                    }
                }
                // 聊天窗口是根：不拦截系统返回 → 直接退出到桌面（系统自带预测式退场动画）
            }
        }
        if (wide) {
            // 平板/横屏：同一抽屉组件转为常驻侧栏（不是"没有抽屉"）
            Row(Modifier.fillMaxSize()) {
                PermanentDrawerSheet(Modifier.width(320.dp)) { drawerBody() }
                Box(Modifier.weight(1f)) { main() }
            }
        } else {
            // 抽屉约占 5/6，右侧留出一点上下文（比全屏更好按"关闭/返回"）
            val drawerWidth = maxWidth * 0.84f
            val drawerWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { drawerWidth.toPx() }
            ModalNavigationDrawer(
                drawerState = drawerState,
                // 只有聊天窗口允许用抽屉原生手势（右滑开 / 左滑关）；设置/回收站右滑是"退出"，不能被抽屉抢走
                gesturesEnabled = screen is Screen.Chat,
                // 遮罩自己画：要跟预测式进度（以及原生拖拽的 drawer offset）一起变化
                scrimColor = androidx.compose.ui.graphics.Color.Transparent,
                drawerContent = {
                    // 预测式关闭预览：手势进度让抽屉整体向左滑出，露出下面的聊天窗口
                    ModalDrawerSheet(
                        Modifier.width(drawerWidth).graphicsLayer {
                            translationX = -drawerNav.progress * drawerWidthPx
                        },
                    ) { drawerBody() }
                },
            ) {
                val drawerOffset = drawerState.offset
                Box(Modifier.fillMaxSize()) {
                    main()
                    val openFraction =
                        (1f - kotlin.math.abs(drawerOffset.value) / drawerWidthPx).coerceIn(0f, 1f)
                    val scrimAlpha = 0.32f * openFraction * (1f - drawerNav.progress)
                    if (scrimAlpha > 0.005f) {
                        Box(
                            Modifier.fillMaxSize().background(
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = scrimAlpha),
                            ),
                        )
                    }
                }
            }
        }
    }

    rowMenu?.let { row ->
        AlertDialog(
            onDismissRequest = { rowMenu = null },
            title = { Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    TextButton(onClick = {
                        rowMenu = null
                        scope.launch {
                            bridge.send(Command.SessionPin(row.id, !row.pinned))
                            refreshSessions()
                        }                    }) { Text(if (row.pinned) "取消置顶" else "置顶") }
                    TextButton(onClick = {
                        rowMenu = null
                        scope.launch {
                            val wasActive = (screen as? Screen.Chat)?.id == row.id
                            bridge.send(Command.SessionArchive(row.id, true))
                            // 非当前会话：抽屉保持打开，便于连续整理；当前会话被归档才关抽屉进新草稿
                            if (wasActive) { drawerState.close(); openDraft() } else refreshSessions()
                            val r = snackbar.showSnackbar(
                                message = "已归档「${row.title}」",
                                actionLabel = "撤销",
                                duration = androidx.compose.material3.SnackbarDuration.Short,
                            )
                            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                bridge.send(Command.SessionArchive(row.id, false)); refreshSessions()
                            }
                        }
                    }) { Text("归档") }
                    TextButton(onClick = { rowMenu = null; renameTarget = row; renameText = row.title }) { Text("重命名") }
                    TextButton(onClick = { rowMenu = null; deleteTarget = row }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { rowMenu = null }) { Text("关闭") } },
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
                        val wasActive = (screen as? Screen.Chat)?.id == target.id
                        bridge.send(Command.SessionDelete(target.id))
                        // 非当前会话：抽屉保持打开，便于连续整理；当前会话被删除才关抽屉进新草稿
                        if (wasActive) { drawerState.close(); openDraft() } else refreshSessions()
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
    onMenu: (() -> Unit)?,
    onSwipeOpenDrawer: (() -> Unit)? = null,
    scroll: ChatScrollState,
    m5Ui: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf(dev.myco.android.ui.ChatContentState()) }
    var streaming by remember { mutableStateOf<String?>(null) }
    var streamingReasoning by remember { mutableStateOf<String?>(null) }
    var liveEntries by remember { mutableStateOf<List<dev.myco.android.ui.TimelineEntry>>(emptyList()) }
    // 当前正在流式输出（或刚结束）的 turn：用于让该回合的过程组在落盘后仍保持展开，结束时不因收起而跳窗
    var streamingTurn by remember { mutableStateOf<Long?>(null) }
    var loadingFork by remember { mutableStateOf(false) }
    var followBottom by remember { mutableStateOf(true) }
    // 流式结束后再多跟几帧，用于内容落盘/底部 footer 出现时收尾；之后不再自动滚动
    var settleFrames by remember { mutableStateOf(0) }
    val listState = scroll.list

    suspend fun scrollToLatest() {
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) listState.scrollToItem(total - 1, 100000)
    }
    val streamTextBuffer = remember { StringBuilder() }
    val streamReasoningBuffer = remember { StringBuilder() }

    fun flushReasoningBuffer() {
        if (streamReasoningBuffer.isEmpty()) return
        val add = streamReasoningBuffer.toString()
        streamReasoningBuffer.setLength(0)
        val cur = liveEntries.toMutableList()
        val idx = cur.indexOfLast { it.kind == "reasoning" && it.running }
        if (idx >= 0) cur[idx] = cur[idx].copy(text = cur[idx].text + add)
        else cur += dev.myco.android.ui.TimelineEntry(kind = "reasoning", text = add, running = true)
        liveEntries = cur
    }

    fun flushTextBuffer() {
        if (streamTextBuffer.isEmpty()) return
        streaming = (streaming ?: "") + streamTextBuffer.toString()
        streamTextBuffer.setLength(0)
    }

    fun flushStreamBuffers() {
        flushReasoningBuffer()
        flushTextBuffer()
    }
    var optimisticUser by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<List<dev.myco.core.spec.AttachmentRef>>(emptyList()) }
    val context = LocalContext.current
    val chatPrefs = remember { context.getSharedPreferences("myco.chat", android.content.Context.MODE_PRIVATE) }
    var thinking by remember { mutableStateOf(chatPrefs.getBoolean("thinking", false)) }
    var webSearch by remember { mutableStateOf(chatPrefs.getBoolean("webSearch", true)) }
    var providersUi by remember { mutableStateOf<List<ProviderUi>>(emptyList()) }
    var selProviderId by remember { mutableStateOf<String?>(null) }
    var selModelId by remember { mutableStateOf<String?>(null) }
    var selReasoningLevel by remember { mutableStateOf<String?>(null) }
    var activeLeaf by remember { mutableStateOf<String?>(null) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showM5Model by remember { mutableStateOf(false) }
    var modelPickerError by remember { mutableStateOf<String?>(null) }
    val initialVersions = remember {
        chatPrefs.all.mapNotNull { (k, v) ->
            if (k.startsWith("v_") && v is Int) k.removePrefix("v_") to v else null
        }.toMap()
    }
    var userMenuTarget by remember { mutableStateOf<dev.myco.android.ui.model.ChatMessageUi?>(null) }
    var editTarget by remember { mutableStateOf<dev.myco.android.ui.model.ChatMessageUi?>(null) }
    var editText by remember { mutableStateOf("") }
    var showAttachSheet by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    suspend fun reload(clearLive: Boolean = false) {
        val res = bridge.query(Query.SessionEvents(sessionId, 0))
        val sel = bridge.query(Query.SessionModel(sessionId))
        val newContent = runCatching {
            if (res is QueryResult.Ok) dev.myco.android.ui.parseChatContent(res.data as? JsonArray) else dev.myco.android.ui.ChatContentState()
        }.getOrElse {
            // 数据异常时宁可显示空会话 + 提示，也不能把整个 App 带崩
            error = "会话数据异常：${it.message ?: it::class.simpleName}"
            dev.myco.android.ui.ChatContentState()
        }
        // 一次性提交：内容、分支、live 缓冲同一帧生效，避免"内容已换 / live 还在"或反之的中间帧造成跳窗
        content = newContent
        if (sel is QueryResult.Ok) {
            activeLeaf = (sel.data as? JsonObject)?.get("activeLeaf")
                ?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content
        }
        if (res is QueryResult.Err) error = "载入失败：${res.code}: ${res.message}"
        if (clearLive) {
            streamTextBuffer.setLength(0)
            streamReasoningBuffer.setLength(0)
            streaming = null
            streamingReasoning = null
            liveEntries = emptyList()
            optimisticUser = null
        }
    }

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
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val resolver = context.contentResolver
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("读取音频失败")
                    val name = displayName(context, uri)
                    val mime = resolver.getType(uri) ?: "audio/wav"
                    val r = bridge.send(
                        Command.TranscribeAudio(
                            name = name,
                            mime = mime,
                            base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP),
                        )
                    )
                    if (r is CommandResult.Err) error("${r.code}: ${r.message}")
                    val text = ((r as CommandResult.Ok).data as JsonObject)["text"]?.jsonPrimitive?.content ?: ""
                    if (text.isBlank()) error("没有识别到文字")
                    input = (input + " " + text).trim()
                }.onFailure { error = "语音转写失败：${it.message}" }
            }
        }
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

    suspend fun loadModelInfo() {
        val provRes = bridge.query(Query.Providers)
        if (provRes is QueryResult.Ok) providersUi = parseProviders(provRes.data as? JsonObject)
        val selRes = bridge.query(Query.SessionModel(sessionId))
        if (selRes is QueryResult.Ok) {
            val o = selRes.data as? JsonObject
            fun JsonObject?.strOrNull(key: String): String? =
                this?.get(key)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content
            selProviderId = o.strOrNull("providerId")
            selModelId = o.strOrNull("modelId")
            selReasoningLevel = o.strOrNull("reasoningLevel")
            activeLeaf = o.strOrNull("activeLeaf")
        }
    }

    LaunchedEffect(sessionId) {
        loadModelInfo()
        reload()
        bridge.events.collect { e ->
            when (e) {
                is BridgeEvent.ReasoningDelta -> if (e.sessionId == sessionId) {
                    streamingTurn = e.turnId
                    streamReasoningBuffer.append(e.text)
                }
                is BridgeEvent.AssistantDelta -> if (e.sessionId == sessionId) {
                    streamingTurn = e.turnId
                    if (streaming == null && streamTextBuffer.isEmpty()) {
                        flushReasoningBuffer()
                        liveEntries = liveEntries.map { if (it.kind == "reasoning") it.copy(running = false) else it }
                    }
                    streamTextBuffer.append(e.text)
                }
                is BridgeEvent.AssistantDone -> if (e.sessionId == sessionId) {
                    // 先 reload 出落盘内容、再在同一个延续里清 live 缓冲：
                    // 中间帧里正文不会凭空消失，窗口也就不会先跳一下再跳回来
                    streamingTurn = e.turnId
                    settleFrames = 12
                    reload(clearLive = true)
                }
                is BridgeEvent.Failed -> if (streaming != null || error == null) {
                    streamTextBuffer.setLength(0)
                    streamReasoningBuffer.setLength(0)
                    error = "${e.code}: ${e.message}"; streaming = null; streamingReasoning = null; liveEntries = emptyList()
                }
                is BridgeEvent.SessionUpdated -> Unit
                is BridgeEvent.ToolStarted -> if (e.sessionId == sessionId) {
                    streamingTurn = e.turnId
                    flushStreamBuffers()
                    liveEntries = liveEntries.map { if (it.kind == "reasoning") it.copy(running = false) else it }
                    val cur = liveEntries.toMutableList()
                    val pending = streaming
                    if (!pending.isNullOrBlank()) {
                        cur += dev.myco.android.ui.TimelineEntry(kind = "text", text = pending)
                        streaming = null
                    }
                    cur += dev.myco.android.ui.TimelineEntry(kind = "tool", name = e.name, args = e.args, running = true)
                    liveEntries = cur
                }
                is BridgeEvent.ToolFinished -> if (e.sessionId == sessionId) {
                    val cur = liveEntries.toMutableList()
                    val idx = cur.indexOfLast { it.kind == "tool" && it.name == e.name && it.running }
                    val srcs = if (e.name == "web_search") dev.myco.android.ui.parseSources(e.text) else emptyList()
                    if (idx >= 0) cur[idx] = cur[idx].copy(ok = e.ok, text = e.text, running = false, sources = srcs)
                    else cur += dev.myco.android.ui.TimelineEntry(kind = "tool", name = e.name, ok = e.ok, text = e.text, sources = srcs)
                    liveEntries = cur
                }
            }
        }
    }

    LaunchedEffect(sessionId) {
        while (true) {
            kotlinx.coroutines.delay(50)
            flushReasoningBuffer()
            flushTextBuffer()
        }
    }

    fun send() {
        val text = input.trim()
        if ((text.isEmpty() && pending.isEmpty()) || streaming != null) return
        val outgoing = pending
        input = ""; pending = emptyList(); error = null
        optimisticUser = text.ifBlank { outgoing.joinToString(" ") { it.name } }
        followBottom = true
        settleFrames = 12
        liveEntries = emptyList()
        streaming = null
        streamTextBuffer.setLength(0)
        streamReasoningBuffer.setLength(0)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        MycoForegroundService.begin(context)
        scope.launch {
            try {
                val r = bridge.send(
                    Command.ChatSend(
                        sessionId, text, outgoing,
                        thinking = thinking, webSearch = webSearch,
                        providerId = selProviderId, modelId = selModelId, reasoningLevel = selReasoningLevel,
                    )
                )
                if (r is CommandResult.Ok) {
                    // 首轮结束后用模型生成短标题（额外小请求；失败不影响回答）
                    bridge.send(Command.SessionGenerateTitle(sessionId))
                }
                if (r is CommandResult.Err) {
                    error = "${r.code}: ${r.message}"
                    optimisticUser = null
                }
                reload()
            } finally {
                MycoForegroundService.end(context)
            }
        }
    }

    fun regenerate(message: dev.myco.android.ui.model.ChatMessageUi) {
        val id = message.messageId ?: return
        error = null
        // 重生成立刻把视图切到分叉点（父节点），不把旧分支内容留在新回答下面
        loadingFork = true
        followBottom = true
        settleFrames = 12
        activeLeaf = message.parentId
        liveEntries = emptyList()
        streaming = null
        streamTextBuffer.setLength(0)
        streamReasoningBuffer.setLength(0)
        scope.launch {
            val r = bridge.send(Command.ChatRegenerate(sessionId, id))
            if (r is CommandResult.Err) error = "${r.code}: ${r.message}"
            reload()
        }
    }

    fun editResend(message: dev.myco.android.ui.model.ChatMessageUi, newText: String) {
        val id = message.messageId ?: return
        error = null
        optimisticUser = newText
        settleFrames = 12
        activeLeaf = message.parentId
        liveEntries = emptyList()
        streaming = null
        streamTextBuffer.setLength(0)
        streamReasoningBuffer.setLength(0)
        scope.launch {
            val r = bridge.send(Command.ChatEditResend(sessionId, id, newText))
            if (r is CommandResult.Err) {
                error = "${r.code}: ${r.message}"
                optimisticUser = null
            }
            reload()
        }
    }

    Scaffold(
        topBar = {
            if (m5Ui) {
                dev.myco.android.ui.m5.M5ChatTopBarReal(
                    title = title,
                    subtitle = providersUi.firstOrNull { it.id == selProviderId }?.displayName ?: "DeepSeek",
                    onMenu = onMenu,
                )
            } else {
                MycoTopBar(title = title, onMenu = onMenu)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            val currentProvider = providersUi.firstOrNull { it.id == selProviderId }
            val currentModel = currentProvider?.models?.firstOrNull { it.id == selModelId }
            val modelLabel = currentModel?.name?.takeIf { it.isNotBlank() } ?: currentModel?.id ?: "默认"
            if (!m5Ui) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = MycoSpacing.l, vertical = MycoSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showModelPicker = true }) { Text("模型 · $modelLabel") }
                    if (selReasoningLevel != null) {
                        Text(
                            "思考 · $selReasoningLevel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            val context = LocalContext.current
            val atBottom by remember {
                androidx.compose.runtime.derivedStateOf {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
                    last.index >= info.totalItemsCount - 1 &&
                        last.offset + last.size <= info.viewportEndOffset + 4
                }
            }
            val display = if (optimisticUser == null) content else content.copy(
                messages = content.messages + dev.myco.android.ui.model.ChatMessageUi("user", optimisticUser!!)
            )
            // 跟随底部：用户在底部时才跟随流式；用户往上拖就停止跟随，不被抢窗口
            var userScrolling by remember { mutableStateOf(false) }
            LaunchedEffect(listState) {
                // 只认"用户自己的拖动"来关掉跟随；程序滚动/回填不算，避免跟随被误关
                listState.interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is androidx.compose.foundation.interaction.DragInteraction.Start -> userScrolling = true
                        is androidx.compose.foundation.interaction.DragInteraction.Stop,
                        is androidx.compose.foundation.interaction.DragInteraction.Cancel -> {
                            userScrolling = false
                            followBottom = atBottom
                        }
                        else -> Unit
                    }
                }
            }
            LaunchedEffect(sessionId) {
                // 进入会话时直接定位到最底部（不做"从顶部滚到底部"的动画）。
                // 每个会话只做一次；进出设置/回收站（页面重建）时保持原阅读位置。
                if (scroll.initialBottomDone) return@LaunchedEffect
                androidx.compose.runtime.snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
                listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1, 100000)
                scroll.initialBottomDone = true
            }
            LaunchedEffect(sessionId) {
                while (true) {
                    androidx.compose.runtime.withFrameNanos { }
                    if (!followBottom || userScrolling) continue
                    // 只有"内容正在生长"（流式输出/live 过程）或刚落盘收尾的几帧才自动跟底；
                    // 从设置/回收站返回、单纯重组时不再主动把窗口拉到底部
                    if (streaming == null && liveEntries.isEmpty() && settleFrames <= 0) continue
                    if (settleFrames > 0) settleFrames--
                    val info = listState.layoutInfo
                    val total = info.totalItemsCount
                    if (total == 0) continue
                    val last = info.visibleItemsInfo.lastOrNull()
                    if (last == null) {
                        listState.scrollToItem(total - 1, 100000)
                        continue
                    }
                    if (last.index < total - 1) {
                        // 新内容已经长到视口外了：直接落到底部（跟随不是动画，不抢用户手势）
                        listState.scrollToItem(total - 1, 100000)
                        continue
                    }
                    val overflow = last.offset + last.size - info.viewportEndOffset
                    if (overflow > 0) {
                        // 逐帧小步推进：内容每帧长一点，窗口平滑跟着走，不瞬移
                        listState.dispatchRawDelta((overflow * 0.35f).coerceIn(1f, 64f))
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            val listModifier = Modifier.fillMaxSize()
                .swipeRightToOpenDrawer(
                    enabled = onSwipeOpenDrawer != null,
                    onOpen = { onSwipeOpenDrawer?.invoke() },
                )
            if (m5Ui) {
                dev.myco.android.ui.m5.ChatMessagesM5(
                    state = display,
                    listState = listState,
                    streamingText = streaming,
                    streamingReasoning = streamingReasoning,
                    onCopy = { text ->
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                        android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onRegenerate = { regenerate(it) },
                    onUserLongPress = { userMenuTarget = it },
                    initialVersions = initialVersions,
                    onVersionSelected = { nodeId, version ->
                        chatPrefs.edit().putInt("v_$nodeId", version).apply()
                    },
                    initialActiveLeaf = activeLeaf,
                    onActiveLeafChanged = { leaf ->
                        activeLeaf = leaf
                        scope.launch { bridge.send(Command.SessionSetActiveLeaf(sessionId, leaf)) }
                    },
                    liveEntries = liveEntries,
                    autoExpandTurn = streamingTurn,
                    modifier = listModifier,
                )
            } else {
                dev.myco.android.ui.ChatMessages(
                    state = display,
                    listState = listState,
                    streamingText = streaming,
                    liveEntries = liveEntries,
                    onCopy = { text ->
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                        android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onRegenerate = { regenerate(it) },
                    onUserLongPress = { userMenuTarget = it },
                    initialVersions = initialVersions,
                    onVersionSelected = { nodeId, version ->
                        chatPrefs.edit().putInt("v_$nodeId", version).apply()
                    },
                    initialActiveLeaf = activeLeaf,
                    onActiveLeafChanged = { leaf ->
                        activeLeaf = leaf
                        scope.launch { bridge.send(Command.SessionSetActiveLeaf(sessionId, leaf)) }
                    },
                    autoExpandTurn = streamingTurn,
                    modifier = listModifier,
                )
            }
            if (error != null) {
                Box(Modifier.padding(horizontal = MycoSpacing.m, vertical = MycoSpacing.xs)) {
                    InlineError(error!!)
                }
            }
                if (!followBottom && (display.messages.isNotEmpty() || streaming != null)) {
                    if (m5Ui) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 12.dp),
                        ) {
                            dev.myco.android.ui.m5.M5BackToLatest {
                                followBottom = true
                                scope.launch { scrollToLatest() }
                            }
                        }
                    } else {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 8.dp, bottom = 8.dp),
                        ) {
                            TextButton(onClick = {
                                followBottom = true
                                scope.launch { scrollToLatest() }
                            }) { Text("↓ 回到最新") }
                        }
                    }
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
            if (m5Ui) {
                dev.myco.android.ui.m5.M5Composer(
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                    value = input,
                    onValueChange = { input = it },
                    onSend = { send() },
                    streaming = streaming != null,
                    onStop = { scope.launch { bridge.send(Command.ChatStop(sessionId)) } },
                    thinking = thinking,
                    webSearch = webSearch,
                    reasoningLabel = selReasoningLevel,
                    onOpenModel = { showM5Model = true },
                    onAttach = { showAttachSheet = true },
                    onToggleThinking = {
                        thinking = !thinking
                        chatPrefs.edit().putBoolean("thinking", thinking).apply()
                    },
                    onToggleWebSearch = {
                        webSearch = !webSearch
                        chatPrefs.edit().putBoolean("webSearch", webSearch).apply()
                    },
                )
            } else {
                Box(Modifier.padding(MycoSpacing.m)) {
                    ChatComposer(
                        value = input,
                        onValueChange = { input = it },
                        onSend = { send() },
                        onStop = { scope.launch { bridge.send(Command.ChatStop(sessionId)) } },
                        streaming = streaming != null,
                        enabled = input.isNotBlank() || pending.isNotEmpty(),
                        onAttach = { showAttachSheet = true },
                        onVoice = { pickAudio.launch(arrayOf("audio/*")) },
                        thinking = thinking,
                        onToggleThinking = {
                            thinking = !thinking
                            chatPrefs.edit().putBoolean("thinking", thinking).apply()
                        },
                        webSearch = webSearch,
                        onToggleWebSearch = {
                            webSearch = !webSearch
                            chatPrefs.edit().putBoolean("webSearch", webSearch).apply()
                        },
                    )
                }
            }
        }
    }

    if (showAttachSheet) {        val sheetState = androidx.compose.material3.rememberModalBottomSheetState()
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

    userMenuTarget?.let { msg ->
        AlertDialog(
            onDismissRequest = { userMenuTarget = null },
            title = { Text("用户消息") },
            text = {
                Column {
                    TextButton(onClick = {
                        android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                        userMenuTarget = null
                    }) { Text("复制") }
                    TextButton(onClick = {
                        editTarget = msg
                        editText = msg.text
                        userMenuTarget = null
                    }) { Text("编辑并重发") }
                }
            },
            confirmButton = { TextButton(onClick = { userMenuTarget = null }) { Text("关闭") } },
        )
    }
    editTarget?.let { msg ->
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("编辑消息") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 6,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = msg
                    val text = editText.trim()
                    editTarget = null
                    if (text.isNotBlank()) editResend(target, text)
                }) { Text("发送") }
            },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text("取消") } },
        )
    }
    if (showModelPicker) {
        val selectedProvider = providersUi.firstOrNull { it.id == selProviderId }
        val selectedModel = selectedProvider?.models?.firstOrNull { it.id == selModelId }
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("选择模型") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp)) {
                    modelPickerError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    providersUi.filter { it.enabled }.forEach { p ->
                        Text(
                            p.displayName.ifBlank { p.id },
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = MycoSpacing.s),
                        )
                        if (p.models.isEmpty()) {
                            Text("（没有模型）", style = MaterialTheme.typography.bodySmall)
                        }
                        p.models.forEach { m ->
                            val checked = p.id == selProviderId && m.id == selModelId
                            val chooseModel: () -> Unit = {
                                scope.launch {
                                    val level = m.reasoningDefault
                                    val r = bridge.send(Command.SessionSetModel(sessionId, p.id, m.id, level))
                                    if (r is CommandResult.Err) {
                                        modelPickerError = "${r.code}: ${r.message}"
                                    } else {
                                        selProviderId = p.id; selModelId = m.id; selReasoningLevel = level
                                    }
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().clickable { chooseModel() },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = checked, onClick = chooseModel)
                                Column(Modifier.weight(1f)) {
                                    Text(m.name ?: m.id, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${m.id} · ${m.contextWindow} ctx · ${m.maxTokens} out",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    if (selectedModel != null && selectedModel.reasoningEfforts.isNotEmpty()) {
                        Text("思考强度", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = MycoSpacing.m))
                        Row(horizontalArrangement = Arrangement.spacedBy(MycoSpacing.s)) {
                            FilterChip(
                                selected = selReasoningLevel == null,
                                onClick = {
                                    scope.launch {
                                        bridge.send(Command.SessionSetModel(sessionId, selProviderId, selModelId, null))
                                        selReasoningLevel = null
                                    }
                                },
                                label = { Text("默认") },
                            )
                            selectedModel.reasoningEfforts.keys.forEach { level ->
                                FilterChip(
                                    selected = selReasoningLevel == level,
                                    onClick = {
                                        scope.launch {
                                            val r = bridge.send(Command.SessionSetModel(sessionId, selProviderId, selModelId, level))
                                            if (r is CommandResult.Err) modelPickerError = "${r.code}: ${r.message}"
                                            else selReasoningLevel = level
                                        }
                                    },
                                    label = { Text(level) },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModelPicker = false }) { Text("关闭") } },
        )
    }
    if (showM5Model) {
        val options = providersUi.filter { it.enabled }.flatMap { prov ->
            prov.models.map { m ->
                dev.myco.android.ui.m5.M5ModelOption(
                    providerId = prov.id,
                    providerName = prov.displayName.ifBlank { prov.id },
                    modelId = m.id,
                    modelName = m.name ?: m.id,
                    reasoningDefault = m.reasoningDefault,
                    levels = m.reasoningEfforts.keys.toList(),
                    selected = prov.id == selProviderId && m.id == selModelId,
                )
            }
        }
        dev.myco.android.ui.m5.M5ModelSheet(
            models = options,
            currentReasoning = selReasoningLevel,
            thinking = thinking,
            error = modelPickerError,
            onToggleThinking = {
                thinking = !thinking
                chatPrefs.edit().putBoolean("thinking", thinking).apply()
            },
            onPickModel = { opt ->
                scope.launch {
                    val r = bridge.send(Command.SessionSetModel(sessionId, opt.providerId, opt.modelId, opt.reasoningDefault))
                    if (r is CommandResult.Err) {
                        modelPickerError = "${r.code}: ${r.message}"
                    } else {
                        selProviderId = opt.providerId
                        selModelId = opt.modelId
                        selReasoningLevel = opt.reasoningDefault
                        showM5Model = false
                    }
                }
            },
            onPickReasoning = { level ->
                scope.launch {
                    val r = bridge.send(Command.SessionSetModel(sessionId, selProviderId, selModelId, level))
                    if (r is CommandResult.Err) {
                        modelPickerError = "${r.code}: ${r.message}"
                    } else {
                        selReasoningLevel = level
                        showM5Model = false
                    }
                }
            },
            onDismiss = { showM5Model = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionBinScreen(
    bridge: InProcessBridge,
    kind: BinKind,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<SessionRow>>(emptyList()) }
    // 首次加载完成前不显示"没有归档/没有删除"空态：滑入动画期间只留白，避免闪一下空文案
    var loading by remember(kind) { mutableStateOf(true) }
    var permanentTarget by remember { mutableStateOf<SessionRow?>(null) }
    val archivedMode = kind == BinKind.ARCHIVED

    suspend fun load() {
        val res = bridge.query(
            Query.SessionList(includeArchived = archivedMode, includeDeleted = !archivedMode)
        )
        val all = if (res is QueryResult.Ok) parseSessions(res.data as? JsonArray) else emptyList()
        rows = all.filter { if (archivedMode) it.archived else it.deleted }
        loading = false
    }
    LaunchedEffect(kind) { load() }

    Scaffold(
        topBar = {
            MycoTopBar(title = if (archivedMode) "归档会话" else "回收站", onBack = onBack)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                Box(Modifier.fillMaxSize())
            } else if (rows.isEmpty()) {
                EmptyState(
                    title = if (archivedMode) "没有归档会话" else "回收站是空的",
                    message = if (archivedMode) "长按会话菜单里可以归档。" else "删除的会话会先进入这里。",
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = MycoSpacing.l)) {
                    items(rows, key = { it.id }) { row ->
                        Column(Modifier.fillMaxWidth().padding(vertical = MycoSpacing.m)) {
                            Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text(formatTime(row.lastMessageAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(MycoSpacing.s)) {
                                TextButton(onClick = {
                                    scope.launch {
                                        if (archivedMode) bridge.send(Command.SessionArchive(row.id, false))
                                        else bridge.send(Command.SessionRestore(row.id))
                                        load()
                                    }
                                }) { Text(if (archivedMode) "取消归档" else "恢复") }
                                TextButton(onClick = { permanentTarget = row }) {
                                    Text("永久删除", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        Divider()
                    }
                }
            }
        }
    }

    permanentTarget?.let { row ->
        AlertDialog(
            onDismissRequest = { permanentTarget = null },
            title = { Text("永久删除？") },
            text = { Text("「${row.title}」的日志与附件会被删除，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val target = row
                    permanentTarget = null
                    scope.launch {
                        bridge.send(Command.SessionDeletePermanent(target.id))
                        load()
                    }
                }) { Text("永久删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { permanentTarget = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    bridge: InProcessBridge,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onOpenBin: (BinKind) -> Unit,
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
    var showTemplates by remember { mutableStateOf(false) }
    var settingsQuery by remember { mutableStateOf("") }
    val q = settingsQuery.trim()
    fun match(vararg keys: String) = q.isBlank() || keys.any { it.contains(q, ignoreCase = true) || q.contains(it, ignoreCase = true) }
    val showAppearance = match("外观", "主题", "浅色", "深色", "appearance", "theme", "light", "dark")
    val showSession = match("会话", "归档", "回收站", "删除", "session", "archive", "trash", "delete")
    val showProvider = match("模型", "provider", "密钥", "导入", "导出", "model", "key", "import", "export")
    val showFgs = match("常驻", "前台", "通知", "后台", "foreground", "notification", "background")
    val showDoctor = match("自检", "诊断", "doctor", "diagnostic", "status", "状态")

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
            item { SearchField(value = settingsQuery, onValueChange = { settingsQuery = it }, placeholder = "搜索设置") }
            if (showAppearance) {
            item {
                SettingsCard("外观") {
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
            }
            }
            if (showSession) {
            item {
                SettingsCard("会话") {
                    SettingsRow("归档会话", "已从抽屉隐藏，可取消归档", onClick = { onOpenBin(BinKind.ARCHIVED) })
                    Divider()
                    SettingsRow("回收站", "软删的会话，可恢复或永久删除", onClick = { onOpenBin(BinKind.DELETED) })
                }
            }
            }
            if (showProvider) {
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
                    OutlinedButton(onClick = { showTemplates = true }) { Text("更多模板") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("myco-providers.json") }) { Text("导出") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("导入") }
                }
            }
            item { HorizontalDivider() }
            }
            if (showFgs) {
            item {
                SettingsCard("常驻（A7：不承诺 24/7，尽力而为）") {
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
                    Text(
                        if (running) "已请求启动（通知栏应出现常驻通知）" else "未启动",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = MycoSpacing.xs),
                    )
                }
            }
            }
            if (showDoctor) {
            item {
                SettingsCard("自检") {
                    if (checks.isEmpty()) {
                        CircularProgressIndicator(Modifier.height(24.dp))
                    } else {
                        checks.forEach { pair ->
                            Row(Modifier.padding(vertical = 2.dp)) {
                                Text(pair.first, Modifier.width(96.dp))
                                Text(pair.second, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            }
        }
    }

    if (showTemplates) {
        AlertDialog(
            onDismissRequest = { showTemplates = false },
            title = { Text("第三方模型提供商") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp)) {
                    providerTemplates().forEach { t ->
                        TextButton(onClick = {
                            discoverStatus = null
                            editor = t.toEditor()
                            showTemplates = false
                        }) {
                            Column {
                                Text(t.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${t.id} · ${t.kind} · ${t.models.size} 个模型",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTemplates = false }) { Text("取消") } },
        )
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
                    val ref = updated.effectiveRef()
                    val res = bridge.send(Command.ProviderSave(updated.toProfile(), revision))
                    var err = (res as? CommandResult.Err)?.let { "${it.code}: ${it.message}" }
                    if (err == null && updated.apiKey.isNotBlank()) {
                        val cred = bridge.send(Command.CredentialSet(ref, updated.apiKey.trim()))
                        if (cred is CommandResult.Err) err = "密钥保存失败：${cred.code}: ${cred.message}"
                    }
                    message = err
                    if (err == null) {
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
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f),
            shape = RoundedCornerShape(MycoRadius.sheet),
        ) {
            Column(Modifier.fillMaxSize().padding(MycoSpacing.l)) {
                Text(
                    if (initial.originalId == null) "添加提供方" else "编辑 ${initial.originalId}",
                    style = MaterialTheme.typography.titleLarge,
                )
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = MycoSpacing.s),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                Text("kind: ${form.kind}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(form.id, { form = form.copy(id = it) }, label = { Text("Provider ID") }, singleLine = true)
                OutlinedTextField(form.displayName, { form = form.copy(displayName = it) }, label = { Text("显示名称") }, singleLine = true)
                OutlinedTextField(form.baseUrl, { form = form.copy(baseUrl = it) }, label = { Text("Base URL") }, singleLine = true)
                OutlinedTextField(
                    form.apiKey,
                    { form = form.copy(apiKey = it) },
                    label = { Text(if (form.originalId == null) "API Key（只写）" else "API Key（留空=保持不变）") },
                    singleLine = true,
                )
                Text("凭据引用：${form.effectiveRef()}（自动生成，不进日志）", style = MaterialTheme.typography.bodySmall)
                Text("模型目录", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = MycoSpacing.s))
                form.models.forEachIndexed { index, model ->
                    Column(Modifier.fillMaxWidth().padding(vertical = MycoSpacing.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("模型 ${index + 1}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            if (form.models.size > 1) {
                                TextButton(onClick = {
                                    form = form.copy(models = form.models.filterIndexed { i, _ -> i != index })
                                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                        OutlinedTextField(
                            model.id,
                            { v -> form = form.copy(models = form.models.toMutableList().also { it[index] = model.copy(id = v) }) },
                            label = { Text("模型 ID") }, singleLine = true,
                        )
                        OutlinedTextField(
                            model.name,
                            { v -> form = form.copy(models = form.models.toMutableList().also { it[index] = model.copy(name = v) }) },
                            label = { Text("显示名称（可选）") }, singleLine = true,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                model.contextWindow,
                                { v -> form = form.copy(models = form.models.toMutableList().also { it[index] = model.copy(contextWindow = v) }) },
                                label = { Text("上下文窗口") }, singleLine = true, modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                model.maxTokens,
                                { v -> form = form.copy(models = form.models.toMutableList().also { it[index] = model.copy(maxTokens = v) }) },
                                label = { Text("最大输出") }, singleLine = true, modifier = Modifier.weight(1f),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = model.inputText, onCheckedChange = { v ->
                                form = form.copy(models = form.models.toMutableList().also { it[index] = model.copy(inputText = v) })
                            })
                            Text("文本")
                            Checkbox(checked = model.inputImage, onCheckedChange = { v ->
                                form = form.copy(models = form.models.toMutableList().also { it[index] = model.copy(inputImage = v) })
                            })
                            Text("图片")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = model.reasoningEnabled, onCheckedChange = { v ->
                                form = form.copy(models = form.models.toMutableList().also { it[index] = model.copy(reasoningEnabled = v) })
                            })
                            Text("支持思考强度")
                        }
                        if (model.reasoningEnabled) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("off", "low", "medium", "high").forEach { level ->
                                    FilterChip(
                                        selected = model.defaultLevel == level,
                                        onClick = {
                                            form = form.copy(models = form.models.toMutableList().also { it[index] = model.copy(defaultLevel = level) })
                                        },
                                        label = { Text("默认 $level") },
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(model.effortLow, { v ->
                                    form = form.copy(models = form.models.toMutableList().also { it[index] = model.copy(effortLow = v) })
                                }, label = { Text("low 参数") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(model.effortMedium, { v ->
                                    form = form.copy(models = form.models.toMutableList().also { it[index] = model.copy(effortMedium = v) })
                                }, label = { Text("medium") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(model.effortHigh, { v ->
                                    form = form.copy(models = form.models.toMutableList().also { it[index] = model.copy(effortHigh = v) })
                                }, label = { Text("high") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                TextButton(onClick = {
                    form = form.copy(models = form.models + ModelEditor.blank(form.models.size))
                }) { Text("＋ 添加模型") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { onDiscover(form) }) { Text("测试连通性") }
                    if (discoverStatus != null) {
                        Text(discoverStatus, style = MaterialTheme.typography.bodySmall)
                    }
                }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(onClick = { onSave(form) }) { Text("保存") }
                }
            }
        }
    }
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

private data class ModelUi(
    val id: String,
    val contextWindow: Long,
    val maxTokens: Long,
    val name: String? = null,
    val reasoningEfforts: Map<String, String?> = emptyMap(),
    val reasoningDefault: String? = null,
)

private data class ModelEditor(
    val id: String,
    val name: String = "",
    val contextWindow: String,
    val maxTokens: String,
    val inputText: Boolean = true,
    val inputImage: Boolean = false,
    val reasoningEnabled: Boolean = false,
    val effortLow: String = "low",
    val effortMedium: String = "medium",
    val effortHigh: String = "high",
    val defaultLevel: String = "low",
) {
    fun toProfile(): ModelProfile = ModelProfile(
        id = id.trim(),
        name = name.trim().ifBlank { null },
        contextWindow = contextWindow.toLongOrNull() ?: 32768L,
        maxTokens = maxTokens.toLongOrNull() ?: 4096L,
        input = buildList {
            if (inputText) add("text")
            if (inputImage) add("image")
        },
        reasoning = if (reasoningEnabled) ReasoningProfile(
            efforts = linkedMapOf(
                "off" to "",
                "low" to effortLow.ifBlank { "low" },
                "medium" to effortMedium.ifBlank { "medium" },
                "high" to effortHigh.ifBlank { "high" },
            ),
            default = defaultLevel,
        ) else null,
    )

    companion object {
        fun blank(index: Int) = ModelEditor(
            id = if (index == 0) "model" else "model-$index",
            contextWindow = "128000", maxTokens = "8192",
        )

        fun from(m: ModelUi) = ModelEditor(
            id = m.id,
            name = m.name ?: "",
            contextWindow = m.contextWindow.toString(),
            maxTokens = m.maxTokens.toString(),
            reasoningEnabled = m.reasoningEfforts.isNotEmpty(),
            effortLow = m.reasoningEfforts["low"] ?: "low",
            effortMedium = m.reasoningEfforts["medium"] ?: "medium",
            effortHigh = m.reasoningEfforts["high"] ?: "high",
            defaultLevel = m.reasoningDefault ?: "low",
        )
    }
}

private data class ProviderUi(
    val id: String,
    val kind: String,
    val displayName: String,
    val baseUrl: String,
    val authRef: String?,
    val configured: Boolean,
    val enabled: Boolean,
    val models: List<ModelUi>,
) {
    fun toEditor(): ProviderEditor = ProviderEditor(
        originalId = id,
        id = id,
        kind = kind,
        displayName = displayName,
        baseUrl = baseUrl,
        models = models.map { ModelEditor.from(it) },
        ref = authRef ?: "",
    )
}

private data class ProviderEditor(
    val originalId: String?,
    val id: String,
    val kind: String,
    val displayName: String,
    val baseUrl: String,
    val models: List<ModelEditor>,
    val ref: String,
    /** 只写不读：非空时在保存 provider 成功后写入 credentials；不进入任何序列化。 */
    val apiKey: String = "",
) {
    fun effectiveRef(): String {
        val current = ref.trim()
        if (current.isNotBlank() && current.first().isUpperCase() &&
            current.all { it == '_' || it.isDigit() || it in 'A'..'Z' }
        ) return current
        val base = id.trim().uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_')
        return (base.ifBlank { "MYCO" } + "_API_KEY")
    }

    fun toProfile(): ProviderProfile {
        val profiles = models.mapNotNull { model -> model.id.trim().ifBlank { null }?.let { model.toProfile() } }
        val first = profiles.firstOrNull()?.id ?: ""
        return ProviderProfile(
            id = id.trim(),
            kind = kind,
            displayName = displayName.ifBlank { null },
            baseUrl = baseUrl.trim().ifBlank { null },
            auth = if (effectiveRef().isBlank() && apiKey.isBlank()) AuthProfile.None
            else AuthProfile.ApiKey(effectiveRef()),
            models = profiles,
            roles = mapOf("chat" to first, "fast" to first),
        )
    }

    companion object {
        fun localTemplate() = ProviderEditor(
            originalId = null, id = "local", kind = "openai-completions",
            displayName = "本地 OpenAI 兼容", baseUrl = "http://127.0.0.1:8787/v1",
            models = listOf(ModelEditor("fake-chat", "fake-chat", "32768", "4096")), ref = "",
        )

        fun deepseekTemplate() = ProviderEditor(
            originalId = null, id = "deepseek", kind = "deepseek-messages",
            displayName = "DeepSeek 官方", baseUrl = "https://api.deepseek.com/anthropic",
            models = listOf(
                ModelEditor("deepseek-chat", "DeepSeek Chat", "128000", "8192"),
                ModelEditor(
                    "deepseek-reasoner", "DeepSeek Reasoner", "128000", "65536",
                    reasoningEnabled = true, effortLow = "low", effortMedium = "medium", effortHigh = "high",
                    defaultLevel = "medium",
                ),
            ),
            ref = "",
        )
    }
}

/** 内置第三方 provider 模板（只做地址/协议/默认模型，key 由用户填）。 */
private data class ProviderTemplate(
    val id: String,
    val kind: String,
    val displayName: String,
    val baseUrl: String,
    val models: List<ModelEditor>,
) {
    fun toEditor() = ProviderEditor(
        originalId = null, id = id, kind = kind, displayName = displayName,
        baseUrl = baseUrl, models = models, ref = "",
    )
}

private fun providerTemplates(): List<ProviderTemplate> = listOf(
    ProviderTemplate("deepseek", "deepseek-messages", "DeepSeek 官方", "https://api.deepseek.com/anthropic",
        ProviderEditor.deepseekTemplate().models),
    ProviderTemplate("openai", "openai-completions", "OpenAI", "https://api.openai.com/v1",
        listOf(
            ModelEditor("gpt-4.1", "GPT-4.1", "1047576", "32768"),
            ModelEditor("gpt-4.1-mini", "GPT-4.1 mini", "1047576", "32768"),
            ModelEditor("o4-mini", "o4-mini", "200000", "100000", reasoningEnabled = true, defaultLevel = "medium"),
        )),
    ProviderTemplate("anthropic", "deepseek-messages", "Anthropic", "https://api.anthropic.com",
        listOf(ModelEditor("claude-sonnet-4-5", "Claude Sonnet 4.5", "200000", "64000", reasoningEnabled = true, defaultLevel = "medium"))),
    ProviderTemplate("kimi", "openai-completions", "Kimi (Moonshot)", "https://api.moonshot.cn/v1",
        listOf(ModelEditor("kimi-k2-0905-preview", "Kimi K2", "262144", "16384"))),
    ProviderTemplate("openrouter", "openai-completions", "OpenRouter", "https://openrouter.ai/api/v1",
        listOf(ModelEditor("openai/gpt-4.1", "GPT-4.1 (OpenRouter)", "1047576", "32768"))),
    ProviderTemplate("gemini", "openai-completions", "Google Gemini (OpenAI 兼容)", "https://generativelanguage.googleapis.com/v1beta/openai/",
        listOf(ModelEditor("gemini-2.5-pro", "Gemini 2.5 Pro", "1048576", "65536", inputImage = true, reasoningEnabled = true, defaultLevel = "medium"))),
)

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
                name = m["name"]?.jsonPrimitive?.content,
                reasoningEfforts = ((m["reasoning"] as? JsonObject)?.get("efforts") as? JsonObject)
                    ?.mapValues { (_, v) -> if (v is kotlinx.serialization.json.JsonNull) null else v.jsonPrimitive.content }
                    ?: emptyMap(),
                reasoningDefault = (m["reasoning"] as? JsonObject)?.get("default")?.jsonPrimitive?.content,
            )
        } ?: emptyList()
        ProviderUi(
            id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
            kind = o["kind"]?.jsonPrimitive?.content ?: "",
            displayName = o["displayName"]?.jsonPrimitive?.content ?: "",
            baseUrl = o["baseUrl"]?.jsonPrimitive?.content ?: "",
            authRef = if (auth?.get("type")?.jsonPrimitive?.content == "api-key") auth["ref"]?.jsonPrimitive?.content else null,
            configured = credential?.get("configured")?.jsonPrimitive?.content == "true",
            enabled = o["enabled"]?.jsonPrimitive?.content != "false",
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
        snippet = o["snippet"]?.jsonPrimitive?.content ?: o["preview"]?.jsonPrimitive?.content,
        lastMessageAt = o["lastMessageAt"]?.jsonPrimitive?.longOrNull
            ?: o["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
        pinned = o["pinned"]?.jsonPrimitive?.content == "true",
        archived = o["archived"]?.jsonPrimitive?.content == "true",
        deleted = o["deleted"]?.jsonPrimitive?.content == "true",
        messageCount = o["messageCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        draft = o["draft"]?.jsonPrimitive?.content == "true",
    )
}.sortedWith(compareByDescending<SessionRow> { it.pinned }.thenByDescending { it.lastMessageAt })

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




















































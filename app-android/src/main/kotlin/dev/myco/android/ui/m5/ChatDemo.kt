package dev.myco.android.ui.m5

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.myco.android.ui.model.ChatMessageUi
import dev.myco.android.ui.SourceRef
import dev.myco.android.ui.TimelineEntry
import dev.myco.android.ui.model.TurnMetaUi
import dev.myco.android.ui.m5.chat.model.ChatTimelineInput
import dev.myco.android.ui.m5.chat.model.LiveTurnInput
import dev.myco.android.ui.m5.chat.model.TurnErrorUi
import dev.myco.android.ui.m5.chat.ui.ChatListCallbacks
import dev.myco.android.ui.m5.chat.ui.ChatListM5
import dev.myco.android.ui.m5.chat.ui.MessageVersion
import dev.myco.android.ui.m5.shell.LocalPanelInsets

/**
 * B1 聊天列表渲染验证页（开发用，adb extra `m5_chat_demo` 进入）。
 *
 * 目的：在没有后端/会话数据的情况下，把 [ChatListM5] 的 13 类 block 全部铺出来看一遍，
 * 覆盖验收：CHAT-06/11/17/19/20/25/28/36/38/43/44/46/54 的**视觉**部分
 * （数值/状态机部分已由 chat.model 与 chat.ui 的单测锁定）。
 */
@Composable
fun M5ChatDemo() {
    val insets = LocalPanelInsets.current
    val sources = listOf(
        SourceRef("K2 编译器", "https://kotlinlang.org/docs/k2.html", "kotlinlang.org"),
        SourceRef("K2 迁移指南", "https://kotlinlang.org/docs/k2-migration.html", "kotlinlang.org"),
    )
    val demo = ChatTimelineInput(
        messages = listOf(
            ChatMessageUi(role = "user", text = "帮我查一下 K2 编译器现在到哪一步了", turn = 1L, messageId = "u1"),
            ChatMessageUi(
                role = "assistant", nodeId = "n1", turn = 1L, messageId = "a1",
                text = "## K2 现状\n\nK2 是 Kotlin 的新前端，**默认启用**后编译速度明显提升。\n\n- 统一了前端，减少两套实现的行为差异\n- 编译更快（官方称约 2 倍）\n- 部分旧插件需要适配\n\n```kotlin\nfun main() { println(\"k2\") }\n```",
            ),
            ChatMessageUi(role = "user", text = "那迁移成本呢？", turn = 2L, messageId = "u2"),
            ChatMessageUi(
                role = "assistant", nodeId = "n2", turn = 2L, messageId = "a2", finishReason = "max_tokens",
                text = "迁移成本主要在编译器插件与 KAPT。建议先跑一遍 `-language-version 2.0` 做试点。",
            ),
            ChatMessageUi(role = "user", text = "再帮我试一次联网搜索", turn = 3L, messageId = "u3"),
            ChatMessageUi(role = "assistant", nodeId = "n3", turn = 3L, messageId = "a3", text = "这次没搜到，网络似乎不稳。"),
        ),
        timelines = mapOf(
            1L to listOf(
                TimelineEntry(kind = "reasoning", text = "先确认用户问的是 Kotlin 的 K2 编译器，不是语音合成。\n然后需要给出当前状态、收益、迁移成本三块信息。"),
                TimelineEntry(kind = "tool", name = "web_search", args = "{\"q\":\"Kotlin K2 compiler status\"}", ok = true, sources = sources),
                TimelineEntry(kind = "text", text = "整理搜索结果，按「现状 / 收益 / 迁移」组织回答。"),
            ),
            2L to listOf(
                TimelineEntry(kind = "reasoning", text = "迁移成本问题需要分「编译插件」「KAPT」「IDE」三部分回答。"),
            ),
            3L to listOf(
                TimelineEntry(kind = "tool", name = "web_search", args = "{\"q\":\"Kotlin 2.0 release\"}", ok = false, running = false),
            ),
        ),
        metas = mapOf(
            1L to TurnMetaUi("ok", 3, 12_400, null),
            2L to TurnMetaUi("truncated", 1, 8_100, null),
            3L to TurnMetaUi("error", 1, 2_000, null),
        ),
        turnSources = mapOf(1L to sources),
        turnErrors = mapOf(3L to TurnErrorUi(code = "network", text = "网络连接不稳定，搜索没有返回结果。", retriable = true)),
        // 直播轮：思考 + 工具进行中 + 已到正文
        live = LiveTurnInput(
            turnId = 4L,
            userText = "顺便说下 K2 对 Compose 的影响",
            entries = listOf(
                TimelineEntry(kind = "reasoning", text = "需要区分 Compose 编译器插件与 Kotlin 前端的关系。当前正在查证。", running = true),
                TimelineEntry(kind = "tool", name = "web_search", args = "{\"q\":\"K2 Compose compiler\"}", running = true),
            ),
            streamingText = "Compose 编译器插件是独立的，K2 主要影响前端解析与类型检查，",
        ),
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        ChatListM5(
            state = demo,
            listState = rememberLazyListState(),
            aStableViewportHeightDp = 720f,
            bottomContentPadding = 96.dp + insets.bottom,
            callbacks = ChatListCallbacks(
                onRegenerate = {},
                onRetryTurn = { _, _ -> },
                onUserLongPress = {},
                onVersionSwitch = { _, _ -> },
                onOpenToolDetail = {},
            ),
            versions = { key -> if (key.contains("/reply")) MessageVersion(index = 2, total = 3) else null },
            expansionResetKey = "demo",
        )
    }
}

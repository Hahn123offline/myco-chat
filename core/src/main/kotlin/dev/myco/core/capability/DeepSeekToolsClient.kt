package dev.myco.core.capability

import dev.myco.core.provider.CredentialResolver
import dev.myco.core.spec.AuthProfile
import dev.myco.core.spec.ProviderProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

/** 能力调用失败：code 尽量对齐 HTTP/业务错误，retriable 只标网络/限流/5xx。 */
class CapabilityException(
    val code: String,
    message: String,
    val retriable: Boolean = false,
) : Exception(message)

data class SearchHit(
    val title: String,
    val url: String,
    val snippet: String,
    val summary: String? = null,
    val siteName: String? = null,
    val date: String? = null,
    val rerankScore: Double? = null,
)

data class FetchPage(val title: String, val content: String)

data class OcrPage(val index: Int, val text: String)

data class OcrResult(val pages: List<OcrPage>, val pageCount: Int) {
    fun fullText(limit: Int = 20_000): String {
        val sb = StringBuilder()
        for (p in pages) {
            if (sb.length >= limit) break
            sb.append("--- page ${p.index + 1} ---\n").append(p.text.take(limit - sb.length)).append('\n')
        }
        return sb.toString().trim()
    }
}

data class AsrResult(val text: String, val durationMs: Long?)

/** 刊例价（人民币，随官方调整，只用于"给用户看的大概成本"，不是账单）。 */
object CapabilityCosts {
    const val SEARCH_PER_CALL_CNY = 0.005          // $0.75/千次 ≈ 5 元/千次
    const val FETCH_PER_CALL_CNY = 0.005           // 同上
    const val OCR_PER_PAGE_CNY = 0.0015            // 1.5 元/千页
    const val ASR_PER_SECOND_CNY = 0.39 / 3600.0   // 0.39 元/小时（限时价）
    const val TTS_PER_CHAR_CNY = 1.5 / 10_000.0    // 1.5 元/万字符（限时价）

    fun asrCost(durationMs: Long?): Double =
        durationMs?.let { maxOf(1.0, it / 1000.0) * ASR_PER_SECOND_CNY } ?: 0.0

    fun ttsCost(chars: Int): Double = chars * TTS_PER_CHAR_CNY

    fun ocrCost(pages: Int): Double = pages * OCR_PER_PAGE_CNY
}

/**
 * DeepSeek 独立工具 API 客户端：`/v1/tools/search|fetch|ocr|asr|tts`。
 * 与对话端点分开发：这里按"能力"计费（成功才收费），不占模型轮次。
 */
class DeepSeekToolsClient(
    private val origin: String,
    private val apiKey: String?,
    private val timeoutMs: Int = 60_000,
) {
    fun search(query: String, count: Int = 10, needContent: Boolean = false): List<SearchHit> {
        require(query.isNotBlank()) { "query 不能为空" }
        require(query.length <= 128) { "query 最长 128 字符" }
        val body = buildJsonObject {
            put("query", query)
            put("count", count.coerceIn(1, 50))
            if (needContent) put("need_content", true)
        }
        val obj = postJson("/v1/tools/search", body, timeoutMs)
        val results = obj["results"]?.jsonArray
            ?: (obj["data"]?.jsonObject?.get("results")?.jsonArray)
            ?: return emptyList()
        return results.map { el ->
            val o = el.jsonObject
            SearchHit(
                title = o["title"]?.jsonPrimitive?.content ?: "",
                url = o["url"]?.jsonPrimitive?.content ?: "",
                snippet = o["snippet"]?.jsonPrimitive?.content ?: "",
                summary = o["summary"]?.jsonPrimitive?.content,
                siteName = o["site_name"]?.jsonPrimitive?.content,
                date = o["date"]?.jsonPrimitive?.content,
                rerankScore = o["rerank_score"]?.jsonPrimitive?.content?.toDoubleOrNull(),
            )
        }.filter { it.url.isNotBlank() }
    }

    fun fetch(url: String): FetchPage {
        require(url.length <= 8192) { "url 过长" }
        val obj = postJson("/v1/tools/fetch", buildJsonObject { put("url", url) }, timeoutMs)
        val inner = (obj["data"] as? JsonObject) ?: obj
        return FetchPage(
            title = inner["title"]?.jsonPrimitive?.content ?: "",
            content = inner["content"]?.jsonPrimitive?.content ?: "",
        )
    }

    fun ocr(bytes: ByteArray, filename: String, purpose: String = "text_recognition"): OcrResult {
        val obj = postMultipart(
            path = "/v1/tools/ocr",
            fields = mapOf("purpose" to purpose),
            fileField = "file",
            filename = filename,
            bytes = bytes,
            timeoutMs = 240_000,
        )
        val inner = (obj["data"] as? JsonObject) ?: obj
        val pagesJson = inner["pages"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
        val pages = pagesJson.mapIndexed { i, el ->
            OcrPage(i, el.jsonObject["text"]?.jsonPrimitive?.content ?: "")
        }
        val count = inner["page_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: pages.size
        return OcrResult(pages, count)
    }

    fun asr(bytes: ByteArray, filename: String): AsrResult {
        val obj = postMultipart(
            path = "/v1/tools/asr",
            fields = emptyMap(),
            fileField = "file",
            filename = filename,
            bytes = bytes,
            timeoutMs = 300_000,
        )
        val inner = (obj["data"] as? JsonObject) ?: obj
        return AsrResult(
            text = inner["text"]?.jsonPrimitive?.content ?: "",
            durationMs = inner["duration_ms"]?.jsonPrimitive?.content?.toLongOrNull(),
        )
    }

    fun tts(text: String, voiceId: String = "stella", format: String = "wav", filterMarkdown: Boolean = true): ByteArray {
        require(text.isNotBlank()) { "text 不能为空" }
        val body = buildJsonObject {
            put("text", text)
            put("voice_id", voiceId)
            put("format", format)
            put("filter_markdown", filterMarkdown)
        }
        return postForBytes("/v1/tools/tts", body, timeoutMs = 120_000)
    }

    // ---- HTTP ----

    private fun postJson(path: String, body: JsonObject, timeoutMs: Int): JsonObject {
        val bytes = postForBytes(path, body, timeoutMs)
        return runCatching { Json.parseToJsonElement(String(bytes, StandardCharsets.UTF_8)).jsonObject }
            .getOrElse { throw CapabilityException("BAD_RESPONSE", "响应不是 JSON：${String(bytes, StandardCharsets.UTF_8).take(200)}") }
    }

    private fun postForBytes(path: String, body: JsonObject, timeoutMs: Int): ByteArray {
        return request(path, body.toString().toByteArray(StandardCharsets.UTF_8), "application/json", timeoutMs)
    }

    private fun postMultipart(
        path: String,
        fields: Map<String, String>,
        fileField: String,
        filename: String,
        bytes: ByteArray,
        timeoutMs: Int,
    ): JsonObject {
        val boundary = "myco-" + UUID.randomUUID().toString().replace("-", "")
        val out = ByteArrayOutputStream()
        fun write(s: String) = out.write(s.toByteArray(StandardCharsets.UTF_8))
        for ((k, v) in fields) {
            write("--$boundary\r\n")
            write("Content-Disposition: form-data; name=\"$k\"\r\n\r\n")
            write(v)
            write("\r\n")
        }
        write("--$boundary\r\n")
        write("Content-Disposition: form-data; name=\"$fileField\"; filename=\"${filename.replace("\"", "")}\"\r\n")
        write("Content-Type: application/octet-stream\r\n\r\n")
        out.write(bytes)
        write("\r\n--$boundary--\r\n")
        val payload = out.toByteArray()
        val response = request(path, payload, "multipart/form-data; boundary=$boundary", timeoutMs)
        return runCatching { Json.parseToJsonElement(String(response, StandardCharsets.UTF_8)).jsonObject }
            .getOrElse { throw CapabilityException("BAD_RESPONSE", "响应不是 JSON") }
    }

    private fun request(path: String, payload: ByteArray, contentType: String, timeoutMs: Int): ByteArray {
        var lastError: Exception? = null
        for (attempt in 1..3) {
            try {
                val conn = (URL(origin + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = minOf(timeoutMs, 15_000)
                    readTimeout = timeoutMs
                    setRequestProperty("Content-Type", contentType)
                    setRequestProperty("Accept", "application/json")
                    if (!apiKey.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
                }
                conn.outputStream.use { it.write(payload) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
                val response = stream?.use { it.readBytes() } ?: ByteArray(0)
                if (code in 200..299) return response
                val message = runCatching {
                    Json.parseToJsonElement(String(response, StandardCharsets.UTF_8)).jsonObject
                        .let { o -> (o["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.content ?: String(response, StandardCharsets.UTF_8) }
                }.getOrDefault("HTTP $code")
                val retriable = code == 429 || code >= 500
                if (retriable && attempt < 3) {
                    Thread.sleep(500L * attempt)
                    continue
                }
                throw CapabilityException("HTTP_$code", message, retriable)
            } catch (e: CapabilityException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < 3) Thread.sleep(500L * attempt) else throw CapabilityException(
                    "NETWORK", e.message ?: "网络错误", retriable = true
                )
            }
        }
        throw CapabilityException("NETWORK", lastError?.message ?: "网络错误", retriable = true)
    }

    companion object {
        /** provider 基址 → 工具 API 的站点根（去掉 /anthropic 与 /v1 后缀）。 */
        fun originOf(baseUrl: String?): String {
            val base = (baseUrl ?: "https://api.deepseek.com").trimEnd('/')
            return base.removeSuffix("/anthropic").removeSuffix("/v1").trimEnd('/')
        }

        fun fromProvider(provider: ProviderProfile, credentials: CredentialResolver): DeepSeekToolsClient? {
            val key = credentials.resolveFor(provider.auth)
            return DeepSeekToolsClient(originOf(provider.baseUrl), key)
        }
    }
}

/** 只给测试/调试用：把 count 拼成数组的辅助，避免测试里手写 JSON 太痛苦。 */
internal fun hitsJson(vararg urls: String): kotlinx.serialization.json.JsonElement = buildJsonArray {
    urls.forEach { add(buildJsonObject { put("title", "t"); put("url", it); put("snippet", "s") }) }
}

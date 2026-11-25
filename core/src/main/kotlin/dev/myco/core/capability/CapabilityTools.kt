package dev.myco.core.capability

import dev.myco.core.provider.CredentialResolver
import dev.myco.core.spec.ProviderProfile
import dev.myco.core.tools.ToolDef
import dev.myco.core.tools.ToolRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path

/**
 * 把 DeepSeek 的独立能力 API 注册成模型可调用的工具。
 * 只对 api.deepseek.com 生效：自定义 OpenAI 兼容服务没有这些端点，注册了只会误导模型。
 * 成本只写 costNote（给 UI/账本用），不进 schema，避免白占 token。
 */
fun registerDeepSeekCapabilityTools(
    registry: ToolRegistry,
    provider: ProviderProfile,
    credentials: CredentialResolver,
    /** 测试注入：指向本地 mock 服务器；生产传 null 走 api.deepseek.com 校验。 */
    clientOverride: DeepSeekToolsClient? = null,
): Boolean {
    val origin = DeepSeekToolsClient.originOf(provider.baseUrl)
    val client = clientOverride ?: run {
        if (!origin.contains("api.deepseek.com")) return false
        DeepSeekToolsClient.fromProvider(provider, credentials) ?: return false
    }
    if (registry.names().contains("web_search")) return false

    registry.register(
        ToolDef(
            name = "web_search",
            summary = "联网搜索（返回标题/URL/摘要，按成功次数计费）",
            description = "搜索公开网页。query 1–128 字符，count 1–50。结果包含 title/url/snippet，回答时请引用 URL。",
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("query", buildJsonObject {
                        put("type", "string"); put("description", "搜索词，支持 site:/lang:/after: 等运算符")
                    })
                    put("count", buildJsonObject {
                        put("type", "integer"); put("description", "结果条数 1–50，默认 10")
                    })
                })
                put("required", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("query")) })
            },
            costNote = "¥${CapabilityCosts.SEARCH_PER_CALL_CNY}/次成功（$0.75/千次）",
        ) { args ->
            val query = args["query"]?.jsonPrimitive?.content?.trim().orEmpty()
            require(query.isNotEmpty()) { "缺少 query" }
            val count = args["count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 10
            val hits = client.search(query, count)
            buildJsonObject {
                put("query", query)
                put("count", hits.size)
                put("estimatedCostCny", CapabilityCosts.SEARCH_PER_CALL_CNY * (if (hits.isEmpty()) 0 else 1))
                put("results", buildJsonArray {
                    hits.forEach { h ->
                        add(buildJsonObject {
                            put("title", h.title); put("url", h.url); put("snippet", h.snippet)
                            h.summary?.let { put("summary", it.take(800)) }
                            h.siteName?.let { put("site", it) }
                            h.date?.let { put("date", it) }
                        })
                    }
                })
            }.toString()
        },
    )

    registry.register(
        ToolDef(
            name = "read_url",
            summary = "读取一个网页正文（Markdown，按成功次数计费）",
            description = "抓取 http(s) 网页正文。url ≤8192 字符，不能含账号密码。返回 title 与 content（超长会截断）。",
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("url", buildJsonObject { put("type", "string"); put("description", "http(s) 链接") })
                })
                put("required", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("url")) })
            },
            costNote = "¥${CapabilityCosts.FETCH_PER_CALL_CNY}/次成功（$0.75/千次）",
        ) { args ->
            val url = args["url"]?.jsonPrimitive?.content?.trim().orEmpty()
            require(url.isNotEmpty()) { "缺少 url" }
            val page = client.fetch(url)
            buildJsonObject {
                put("url", url)
                put("title", page.title)
                put("truncated", page.content.length > 12_000)
                put("content", page.content.take(12_000))
                put("estimatedCostCny", if (page.content.isBlank()) 0.0 else CapabilityCosts.FETCH_PER_CALL_CNY)
            }.toString()
        },
    )

    registry.register(
        ToolDef(
            name = "ocr_image",
            summary = "OCR 识别图片/PDF 的文字（按成功页数计费）",
            description = "识别本地文件（PNG/JPEG/WebP/GIF/BMP/TIFF/PDF/DOC/DOCX/PPT/PPTX）中的文字，返回逐页文本。path 为设备/主机上的文件路径。",
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "本地文件路径") })
                    put("purpose", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(kotlinx.serialization.json.JsonPrimitive("text_recognition"))
                            add(kotlinx.serialization.json.JsonPrimitive("rich_layout_recognition"))
                        })
                    })
                })
                put("required", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("path")) })
            },
            costNote = "¥${CapabilityCosts.OCR_PER_PAGE_CNY}/页（1.5 元/千页）",
        ) { args ->
            val path = Path.of(args["path"]?.jsonPrimitive?.content ?: error("缺少 path"))
            require(Files.isRegularFile(path)) { "文件不存在：$path" }
            val purpose = args["purpose"]?.jsonPrimitive?.content ?: "text_recognition"
            val result = client.ocr(Files.readAllBytes(path), path.fileName.toString(), purpose)
            buildJsonObject {
                put("file", path.fileName.toString())
                put("pageCount", result.pageCount)
                put("estimatedCostCny", CapabilityCosts.ocrCost(result.pageCount))
                put("text", result.fullText())
            }.toString()
        },
    )

    registry.register(
        ToolDef(
            name = "transcribe_audio",
            summary = "语音转文字（单段 ≤120 秒，按秒计费）",
            description = "识别本地 16-bit PCM WAV 或 Ogg Opus 音频（≤8 MiB，≤120 秒）。path 为文件路径，返回 text 与时长。",
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "本地音频路径") })
                })
                put("required", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("path")) })
            },
            costNote = "≈¥${"%.5f".format(CapabilityCosts.ASR_PER_SECOND_CNY)}/秒（0.39 元/小时，限时价）",
        ) { args ->
            val path = Path.of(args["path"]?.jsonPrimitive?.content ?: error("缺少 path"))
            require(Files.isRegularFile(path)) { "文件不存在：$path" }
            val result = client.asr(Files.readAllBytes(path), path.fileName.toString())
            buildJsonObject {
                put("text", result.text)
                result.durationMs?.let { put("durationMs", it) }
                put("estimatedCostCny", CapabilityCosts.asrCost(result.durationMs))
            }.toString()
        },
    )

    return true
}

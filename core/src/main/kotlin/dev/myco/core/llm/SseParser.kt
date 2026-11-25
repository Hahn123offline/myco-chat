package dev.myco.core.llm

/** 一条 SSE frame：`event` 可选，`data` 是拼接后的多行 data。 */
data class SseFrame(val event: String?, val data: String)

/**
 * 最小 SSE 解析器：按行喂入，空行 flush 一帧。
 * 只处理 data/event 字段与注释行（": keep-alive"）；未知字段忽略但不吞帧。
 */
class SseParser {
    private val dataLines = StringBuilder()
    private var event: String? = null

    fun feed(line: String): SseFrame? {
        if (line.isEmpty()) return flush()
        if (line.startsWith(":")) return null // 注释 / keep-alive
        when {
            line.startsWith("data:") -> {
                val v = line.removePrefix("data:").let { if (it.startsWith(" ")) it.substring(1) else it }
                if (dataLines.isNotEmpty()) dataLines.append('\n')
                dataLines.append(v)
            }
            line.startsWith("event:") -> event = line.removePrefix("event:").trim()
            else -> Unit // id/retry 等暂不关心
        }
        return null
    }

    fun finish(): SseFrame? = flush()

    private fun flush(): SseFrame? {
        if (dataLines.isEmpty()) {
            event = null
            return null
        }
        val frame = SseFrame(event, dataLines.toString())
        dataLines.setLength(0)
        event = null
        return frame
    }
}

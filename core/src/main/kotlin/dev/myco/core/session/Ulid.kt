package dev.myco.core.session

import java.security.SecureRandom

/**
 * ULID（Crockford Base32，26 字符，字典序按时间排序）。
 * 事件日志要跨设备同步，UUID 无序、时间戳撞车；ULID 是本项目所有 id 的统一格式。
 */
object Ulid {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val random = SecureRandom()

    fun newId(now: Long = System.currentTimeMillis()): String {
        val timePart = buildString {
            var t = now
            for (i in 0 until 10) {
                append(ALPHABET[(t and 31).toInt()])
                t = t ushr 5
            }
        }.reversed()
        val randPart = buildString {
            repeat(16) { append(ALPHABET[random.nextInt(32)]) }
        }
        return timePart + randPart
    }
}

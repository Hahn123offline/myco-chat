package dev.myco.core.io

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Android 的 java.nio.file.Files **没有** readString/writeString（API 35 仍缺），
 * 但 readAllBytes / write 是有的。所有持久化文本读写都走这里，桌面与手机同一实现。
 */
object TextFiles {
    fun read(path: Path): String = String(Files.readAllBytes(path), StandardCharsets.UTF_8)

    fun write(path: Path, text: String) {
        Files.write(path, text.toByteArray(StandardCharsets.UTF_8))
    }

    fun append(path: Path, text: String) {
        Files.write(
            path,
            text.toByteArray(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }
}

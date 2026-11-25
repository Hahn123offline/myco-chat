package dev.myco.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet

/**
 * $MYCO_HOME 的目录布局。所有持久数据都相对它，禁止在数据里写绝对路径。
 */
class MycoHome(val root: Path) {
    val store: Path = root.resolve("store")
    val sessions: Path = store.resolve("sessions")
    val attachments: Path = store.resolve("attachments")
    val config: Path = root.resolve("config")
    val logs: Path = root.resolve("logs")
    val providersFile: Path = config.resolve("providers.json")
    val credentialsFile: Path = root.resolve("credentials.json")

    fun ensureLayout() {
        Files.createDirectories(sessions)
        Files.createDirectories(attachments)
        Files.createDirectories(config)
        Files.createDirectories(logs)
    }

    companion object {
        fun resolveDefault(): MycoHome {
            val fromEnv = System.getenv("MYCO_HOME")
            val root = if (!fromEnv.isNullOrBlank()) Path.of(fromEnv)
            else Path.of(System.getProperty("user.home"), ".myco")
            return MycoHome(root).also { it.ensureLayout() }
        }

        /** 若文件权限过于宽松则返回告警（不阻断，只吵）。 */
        fun permissionWarning(path: Path): String? {
            if (!Files.exists(path)) return null
            return try {
                val perms = Files.getPosixFilePermissions(path)
                val tooOpen = EnumSet.of(
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
                    PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE
                )
                if (perms.any { it in tooOpen }) "文件权限过宽（建议 0600）：$path" else null
            } catch (_: UnsupportedOperationException) {
                null // Windows: 无 POSIX 权限，交给 ACL 检查（后续）
            }
        }
    }
}

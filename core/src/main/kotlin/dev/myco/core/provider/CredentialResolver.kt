package dev.myco.core.provider

import dev.myco.core.MycoHome
import dev.myco.core.spec.AuthProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files

data class CredentialStatus(
    val configured: Boolean,
    val source: String?,
    val writable: Boolean,
)

/**
 * 凭据只按引用解析：环境变量 > 本地 credentials 文件。
 * describe() 永远不回值；warn 只在权限过宽时输出，不阻断。
 */
class CredentialResolver(
    private val home: MycoHome,
    private val env: Map<String, String> = System.getenv(),
) {
    companion object {
        private val REF_RE = Regex("^[A-Z][A-Z0-9_]*$")
    }
    fun resolve(ref: String): String? {
        env[ref]?.takeIf { it.isNotBlank() }?.let { return it }
        return fileRecords()[ref]?.takeIf { it.isNotBlank() }
    }

    fun describe(ref: String): CredentialStatus {
        if (env[ref]?.isNotBlank() == true) return CredentialStatus(true, "env", false)
        val fromFile = fileRecords()[ref]?.isNotBlank() == true
        return CredentialStatus(fromFile, if (fromFile) "file" else null, writable = true)
    }

    fun resolveFor(auth: AuthProfile): String? = when (auth) {
        is AuthProfile.ApiKey -> resolve(auth.ref)
        AuthProfile.None -> null
    }

    /** 只写不读：值写入本地 credentials.json（0600 尽力而为），永不回显。 */
    fun set(ref: String, value: String) {
        require(REF_RE.matches(ref)) { "非法 ref: $ref" }
        require(value.isNotBlank()) { "密钥不能为空" }
        require(value.all { it.code in 0x21..0x7E }) { "密钥只能包含可打印 ASCII（HTTP 头字符集）" }
        val records = fileRecords().toMutableMap()
        records[ref] = value
        write(records)
    }

    fun delete(ref: String): Boolean {
        val records = fileRecords().toMutableMap()
        val removed = records.remove(ref) != null
        if (removed) write(records)
        return removed
    }

    private fun write(records: Map<String, String>) {
        if (records.isEmpty()) {
            Files.deleteIfExists(home.credentialsFile)
            return
        }
        Files.createDirectories(home.credentialsFile.toAbsolutePath().parent)
        val tmp = home.credentialsFile.resolveSibling("credentials.json.tmp")
        val json = JsonObject(records.mapValues { (_, v) -> JsonPrimitive(v) })
        Files.writeString(tmp, json.toString())
        try {
            val perms = java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")
            Files.setPosixFilePermissions(tmp, perms)
        } catch (_: UnsupportedOperationException) {
            // Windows：交给 ACL；不伪造权限
        }
        Files.move(tmp, home.credentialsFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    private fun fileRecords(): Map<String, String> {
        if (!Files.exists(home.credentialsFile)) return emptyMap()
        MycoHome.permissionWarning(home.credentialsFile)?.let { System.err.println("[warn] $it") }
        return try {
            val root = Json.parseToJsonElement(Files.readString(home.credentialsFile)).jsonObject
            root.mapValues { (_, v) -> v.jsonPrimitive.content }
        } catch (e: Exception) {
            System.err.println("[warn] credentials.json 解析失败，按未配置处理：${e.message}")
            emptyMap()
        }
    }
}

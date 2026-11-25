package dev.myco.core.provider

import dev.myco.core.MycoHome
import dev.myco.core.spec.AuthProfile
import kotlinx.serialization.json.Json
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

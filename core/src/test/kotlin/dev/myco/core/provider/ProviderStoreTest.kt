package dev.myco.core.provider

import dev.myco.core.MycoHome
import dev.myco.core.settings.SettingsConflictException
import dev.myco.core.spec.AuthProfile
import dev.myco.core.spec.ModelProfile
import dev.myco.core.spec.ProviderProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ProviderStoreTest {
    private fun home(): MycoHome = MycoHome(Files.createTempDirectory("myco-prov")).also { it.ensureLayout() }

    private fun provider(id: String = "local", auth: AuthProfile = AuthProfile.None) = ProviderProfile(
        id = id,
        kind = "openai-completions",
        baseUrl = "http://127.0.0.1:8787/v1",
        auth = auth,
        models = listOf(ModelProfile(id = "fake-chat", contextWindow = 32768, maxTokens = 4096)),
    )

    @Test
    fun `save and load roundtrip with revision`() {
        val h = home()
        val store = ProviderStore(h)
        assertEquals(0L, store.load().revision)
        assertEquals(1L, store.save(listOf(provider()), expectedRevision = 0))
        val snap = store.load()
        assertEquals(1L, snap.revision)
        assertEquals(1, snap.providers.size)
        assertEquals("local", snap.providers.first().id)
    }

    @Test
    fun `stale revision is rejected`() {
        val h = home()
        val store = ProviderStore(h)
        store.save(listOf(provider()), expectedRevision = 0)
        assertThrows(SettingsConflictException::class.java) {
            store.save(listOf(provider("other")), expectedRevision = 0)
        }
        // 拿到新 revision 后可以写
        assertEquals(2L, store.save(listOf(provider("other")), expectedRevision = 1))
    }

    @Test
    fun `legacy plain file is read as revision 0`() {
        val h = home()
        Files.writeString(
            h.providersFile,
            """{"providers":[{"id":"local","kind":"openai-completions","auth":{"type":"none"},"models":[{"id":"m","contextWindow":1,"maxTokens":1}]}]}"""
        )
        val snap = ProviderStore(h).load()
        assertEquals(0L, snap.revision)
        assertEquals("local", snap.providers.single().id)
    }

    @Test
    fun `empty models rejected`() {
        val h = home()
        val bad = provider().copy(models = emptyList())
        assertThrows(ProviderConfigException::class.java) { ProviderStore(h).save(listOf(bad), 0) }
    }

    @Test
    fun `illegal id rejected`() {
        val h = home()
        val bad = provider(id = "Bad_ID")
        assertThrows(ProviderConfigException::class.java) { ProviderStore(h).save(listOf(bad), 0) }
    }

    @Test
    fun `empty file means no providers`() {
        assertEquals(0, ProviderStore(home()).load().providers.size)
    }
}

class CredentialResolverTest {
    private fun home() = MycoHome(Files.createTempDirectory("myco-cred")).also { it.ensureLayout() }

    @Test
    fun `env wins and describe never reveals value`() {
        val h = home()
        val resolver = CredentialResolver(h, mapOf("DEEPSEEK_API_KEY" to "sk-secret"))
        assertEquals("sk-secret", resolver.resolve("DEEPSEEK_API_KEY"))
        val status = resolver.describe("DEEPSEEK_API_KEY")
        assertTrue(status.configured)
        assertEquals("env", status.source)
        assertFalse(status.writable)
        assertFalse(status.toString().contains("sk-secret"))
    }

    @Test
    fun `file fallback and missing ref`() {
        val h = home()
        Files.writeString(h.credentialsFile, "{\"OTHER_KEY\":\"v\"}")
        val resolver = CredentialResolver(h, emptyMap())
        assertEquals("v", resolver.resolve("OTHER_KEY"))
        assertNull(resolver.resolve("MISSING"))
        assertFalse(resolver.describe("MISSING").configured)
    }
}

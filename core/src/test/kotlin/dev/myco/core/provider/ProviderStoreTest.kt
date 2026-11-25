package dev.myco.core.provider

import dev.myco.core.MycoHome
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
    fun `save and load roundtrip`() {
        val h = home()
        val store = ProviderStore(h)
        store.save(listOf(provider()))
        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals("local", loaded.first().id)
    }

    @Test
    fun `empty models rejected`() {
        val h = home()
        val bad = provider().copy(models = emptyList())
        assertThrows(ProviderConfigException::class.java) { ProviderStore(h).save(listOf(bad)) }
    }

    @Test
    fun `illegal id rejected`() {
        val h = home()
        val bad = provider(id = "Bad_ID")
        assertThrows(ProviderConfigException::class.java) { ProviderStore(h).save(listOf(bad)) }
    }

    @Test
    fun `empty file means no providers`() {
        assertEquals(0, ProviderStore(home()).load().size)
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

package io.github.aequicor.heartbeat.ai.engines.deepseekharness

import io.github.aequicor.heartbeat.ai.engine.api.EngineConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeepSeekHarnessProviderTest {

    private class FakeSettings : DeepSeekLaunchSettings {
        var reads: Int = 0
            private set

        var executable: String = "dsh-acp-server"
        var arguments: List<String> = listOf()
        var workingDirectory: String? = null

        override fun current(): DeepSeekLaunchConfig {
            reads += 1
            return DeepSeekLaunchConfig(executable, arguments, workingDirectory)
        }
    }

    @Test
    fun keyIdentifiesTheAgentWithoutSecrets() {
        assertEquals("acp.deepseek-harness", DeepSeekHarness.key.id)
        assertEquals(EngineConnection.ACP, DeepSeekHarness.key.connection)
        assertFalse(DeepSeekHarness.key.toString().contains("sk-"))
        assertEquals("DeepSeek Harness", DeepSeekHarness.key.name)
    }

    @Test
    fun creatingAnEngineDoesNotReadSettings() {
        val settings = FakeSettings()
        val provider = DeepSeekHarnessProvider(settings)

        provider.create()

        assertEquals(0, settings.reads)
    }

    @Test
    fun launchConfigurationIsReadOnEveryConnect() {
        val settings = FakeSettings()
        val provider = DeepSeekHarnessProvider(settings)

        val first = provider.launchSpec()
        settings.executable = "dsh"
        settings.arguments = listOf("--profile", "acp")
        val second = provider.launchSpec()

        assertEquals(2, settings.reads)
        assertEquals("dsh-acp-server", first.executable)
        assertTrue(first.arguments.isEmpty())
        assertEquals("dsh", second.executable)
        assertEquals(listOf("--profile", "acp"), second.arguments)
    }

    @Test
    fun defaultSettingsPreferTheProfileLauncher() {
        val config = DefaultDeepSeekLaunchSettings().current()

        assertEquals("dsh-acp-server", config.executable)
        assertTrue(config.arguments.isEmpty())
    }
}

package io.github.aequicor.heartbeat.features.aiengine.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AiEngineSessionKeyTest {

    @Test
    fun sessionIdentityIgnoresMetadataAndIncludesEngineId() {
        val original = AiEngineSessionKey(TestEngineKey("first"), "chat", title = "Before")
        val renamed = AiEngineSessionKey(TestEngineKey("first", "Renamed"), "chat", title = "After")
        val differentEngine = AiEngineSessionKey(TestEngineKey("second"), "chat", title = "Before")

        assertEquals(original, renamed)
        assertEquals(original.hashCode(), renamed.hashCode())
        assertNotEquals(original, differentEngine)
    }

    private data class TestEngineKey(override val id: String, override val name: String = id) : AiEngineKey {
        override val description: String = name
        override val connection: AiEngineConnection = AiEngineConnection.EMBEDDED
    }
}

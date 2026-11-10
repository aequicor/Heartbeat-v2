package io.github.aequicor.heartbeat.features.aiengine.api.session

import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineConnection
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SessionKeyTest {

    @Test
    fun sessionIdentityIgnoresMetadataAndIncludesEngineId() {
        val original = SessionKey(TestEngineKey("first"), "chat", title = "Before")
        val renamed = SessionKey(TestEngineKey("first", "Renamed"), "chat", title = "After")
        val differentEngine = SessionKey(TestEngineKey("second"), "chat", title = "Before")

        assertEquals(original, renamed)
        assertEquals(original.hashCode(), renamed.hashCode())
        assertNotEquals(original, differentEngine)
    }

    private data class TestEngineKey(override val id: String, override val name: String = id) : AiEngineKey {
        override val description: String = name
        override val connection: AiEngineConnection = AiEngineConnection.EMBEDDED
    }
}

package io.github.aequicor.heartbeat.ai.engine.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class EngineKeyTest {

    private fun key(
        id: String = "acp.test",
        name: String = id,
    ): EngineKey = EngineKey(
        id = id,
        name = name,
        description = "test engine",
        connection = EngineConnection.ACP,
    )

    @Test
    fun engineIdentityIgnoresDisplayMetadata() {
        val original = key(name = "Original")
        val renamed = key(name = "Renamed")

        assertEquals(original, renamed)
        assertEquals(original.hashCode(), renamed.hashCode())
        assertEquals(1, setOf(original, renamed).size)
    }

    @Test
    fun enginesWithDifferentIdsAreDifferent() {
        assertNotEquals(key(id = "acp.one"), key(id = "acp.two"))
    }

    @Test
    fun sessionIdentityIgnoresMetadata() {
        val engine = key()
        val original = SessionKey(engine, "session-1", title = "First")
        val updated = original.withMetadata(title = "Renamed", updatedAt = "2026-11-09T10:00:00Z")

        assertEquals(original, updated)
        assertEquals(original.hashCode(), updated.hashCode())
        assertEquals(1, mapOf(original to "value").keys.count { it == updated })
    }

    @Test
    fun sessionIdentityIncludesTheEngine() {
        val first = SessionKey(key(id = "acp.one"), "session-1")
        val second = SessionKey(key(id = "acp.two"), "session-1")

        assertNotEquals(first, second)
    }

    @Test
    fun blankIdsAreRejected() {
        val failure = runCatching { key(id = " ") }.exceptionOrNull()

        assertEquals(IllegalArgumentException::class, failure?.let { it::class })
    }
}

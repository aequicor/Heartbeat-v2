package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.ui.graphics.ImageBitmap
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraDiagramPreviews
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class AuroraDiagramPreviewsTest {
    @Test
    fun inlineAndFullscreenShareIdenticalImagesAndRetryFailedLayouts() = runTest {
        val requests = mutableListOf<String>()
        var fail = true
        val previews = AuroraDiagramPreviews(StandardTestDispatcher(testScheduler)) { kind, source ->
            requests += "$kind/$source"
            yield()
            if (fail) error("Invalid diagram")
            ImageBitmap(64, 32)
        }
        assertFailsWith<IllegalStateException> { previews.preview("mermaid", "A") }
        fail = false
        val first = async { previews.preview("mermaid", "A") }
        val second = async { previews.preview("mermaid", "A") }
        val image = first.await()
        assertSame(image, second.await())
        assertNotSame(image, previews.preview("plantuml", "A"))
        assertEquals(listOf("mermaid/A", "mermaid/A", "plantuml/A"), requests)
    }
}

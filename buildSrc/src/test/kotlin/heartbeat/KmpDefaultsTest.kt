package heartbeat

import kotlin.test.Test
import kotlin.test.assertEquals

class KmpDefaultsTest {

    @Test
    fun namespaceNormalizesHierarchySeparatorsAndHyphens() {
        assertEquals(
            "io.github.aequicor.heartbeat.features.aistudio.ui.compose",
            KmpDefaults.namespace(":features:ai-studio:ui:compose"),
        )
    }
}

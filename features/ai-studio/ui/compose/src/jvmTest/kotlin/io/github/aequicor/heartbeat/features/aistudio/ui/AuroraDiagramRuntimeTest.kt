package io.github.aequicor.heartbeat.features.aistudio.ui

import io.github.aequicor.heartbeat.features.aistudio.ui.components.renderAuroraDiagram
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.math.abs

class AuroraDiagramRuntimeTest {
    @Test
    fun higherPixelScaleRerasterizesBothLanguages(): Unit = runBlocking {
        Assume.assumeTrue(System.getenv("HB_DIAGRAM_RUNTIME_SMOKE") == "1")
        val examples = listOf(
            "mermaid" to "flowchart LR\n A[Запущен] --> B[Остановлен]",
            "plantuml" to "@startuml\nКлиент -> Агент: Запрос\n@enduml",
        )
        for ((language, source) in examples) {
            val normal = renderAuroraDiagram(language, source, 2f)
            val detailed = renderAuroraDiagram(language, source, 4f)
            assertTrue(abs(detailed.width - normal.width * 2) <= 2)
            assertTrue(abs(detailed.height - normal.height * 2) <= 2)
        }
    }

    @Test
    fun rendersBothLanguagesAndRejectsInvalidSyntax(): Unit = runBlocking {
        Assume.assumeTrue(System.getenv("HB_DIAGRAM_RUNTIME_SMOKE") == "1")
        val mermaid = renderAuroraDiagram("mermaid", "flowchart LR\n A[Запущен] --> B[Остановлен]")
        assertTrue(mermaid.width > 100 && mermaid.height > 40)
        val plantUml = renderAuroraDiagram("plantuml", "@startuml\nКлиент -> Агент: Syntax Error\n@enduml")
        assertTrue(plantUml.width > 100 && plantUml.height > 40)
        assertFailsWith<IllegalStateException> { renderAuroraDiagram("mermaid", "flowchart LR\n [A") }
        assertFailsWith<IllegalStateException> { renderAuroraDiagram("plantuml", "this syntax is invalid") }
    }
}

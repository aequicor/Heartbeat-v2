package io.github.aequicor.heartbeat.features.aistudio.ui

import io.github.aequicor.heartbeat.features.aistudio.ui.components.renderAuroraDiagram
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuroraDiagramRuntimeTest {
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

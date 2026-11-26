package io.github.aequicor.heartbeat.features.aistudio.ui.components

import io.github.aequicor.heartbeat.designsystem.components.HbMenuAction
import io.github.aequicor.heartbeat.designsystem.components.HbMenuEntry
import io.github.aequicor.heartbeat.designsystem.components.HbMenuSeparator
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Scenario

internal object AuroraDemoParameters {
    const val Engine = "Claude Code"
    const val Model = "Sonnet 4.5"

    private val efforts = listOf("Низкое", "Среднее", "Высокое")

    fun effortLabel(value: String): String = when (value) {
        "Низкое" -> "Низкий"
        "Среднее" -> "Средний"
        "Высокое" -> "Высокий"
        else -> value
    }

    fun effortOptions(selected: String, onAction: (AiStudioAction) -> Unit): List<HbMenuEntry> =
        efforts.map { effort ->
            HbMenuAction(
                effortLabel(effort), { onAction(AiStudioAction.SetEffort(effort)) }, selected = selected == effort,
            )
        }

    fun engineOptions(selected: Scenario, onAction: (AiStudioAction) -> Unit): List<HbMenuEntry> =
        listOf(
            HbMenuAction("$Engine · локальная демонстрация", {}, selected = true),
            HbMenuSeparator,
            HbMenuAction("Сценарий демонстрации", {}, enabled = false),
        ) + Scenario.entries.map { scenario ->
            HbMenuAction(
                scenario.label, { onAction(AiStudioAction.SetScenario(scenario)) }, selected = selected == scenario,
            )
        }
}

package io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines

import io.github.aequicor.heartbeat.features.aienginefacade.domain.model.AcpConnectionSettings
import io.github.aequicor.heartbeat.features.aienginefacade.domain.model.ChatEngine
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenState.Editor

internal fun AcpConnectionSettings.form(ignoreEnvironmentNameCase: Boolean): AcpConnectionForm = AcpConnectionForm(
    preset = AcpConnectionForm.Preset.entries.firstOrNull { it.name == preset } ?: AcpConnectionForm.Preset.Manual,
    name = name,
    command = command,
    argumentText = argumentText,
    separator = AcpConnectionForm.Separator.entries.first { it.character == separator },
    environment = environment.entries.mapIndexed { index, (key, value) ->
        AcpConnectionForm.Variable(index.toLong(), key, value)
    }.ifEmpty { listOf(AcpConnectionForm.Variable(0)) },
    ignoreEnvironmentNameCase = ignoreEnvironmentNameCase,
)

internal fun AcpConnectionForm.settings(id: String): AcpConnectionSettings = AcpConnectionSettings(
    id, name.trim(), preset.name, command.trim(), argumentText, separator.character,
    environment.filter { it.name.isNotBlank() }.associate { it.name to it.value },
)

internal fun AcpConnectionForm.comparableEnvironment(): List<AcpConnectionForm.Variable> = environment
    .filterNot { it.name.isBlank() && it.value.isEmpty() }
    .map { it.copy(rowId = 0) }

internal fun ChatEngine.editor(connection: AcpConnectionSettings): Editor.Engine = Editor.Engine(
    engineId = id, changed = false, name = name, connectionName = connection.name, command = connection.command,
    responseFormat = localSettings["responseFormat"].orEmpty(), rules = rules,
)

internal fun ChatEngine.withEdits(editor: Editor.Engine): ChatEngine = copy(
    localSettings = if (editor.responseFormat.isEmpty()) localSettings - "responseFormat"
    else localSettings + ("responseFormat" to editor.responseFormat),
    rules = editor.rules,
)

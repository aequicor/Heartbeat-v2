package io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines

public sealed interface AcpConnectionFormChange {

    public data class PresetSelected(val preset: AcpConnectionForm.Preset) : AcpConnectionFormChange

    public data class NameChanged(val name: String) : AcpConnectionFormChange

    public data class CommandChanged(val command: String) : AcpConnectionFormChange

    public data class ArgumentsChanged(val text: String) : AcpConnectionFormChange

    public data class SeparatorSelected(val separator: AcpConnectionForm.Separator) : AcpConnectionFormChange

    public data class VariableAdded(val rowId: Long) : AcpConnectionFormChange

    public data class VariableNameChanged(val rowId: Long, val name: String) : AcpConnectionFormChange

    public data class VariableValueChanged(val rowId: Long, val value: String) : AcpConnectionFormChange

    public data class VariableRemoved(val rowId: Long) : AcpConnectionFormChange
}

internal fun AcpConnectionForm.edit(change: AcpConnectionFormChange, newConnection: Boolean): AcpConnectionForm =
    when (change) {
        is AcpConnectionFormChange.PresetSelected -> selectPreset(change.preset, newConnection)
        is AcpConnectionFormChange.NameChanged -> copy(name = change.name)
        is AcpConnectionFormChange.CommandChanged -> copy(command = change.command)
        is AcpConnectionFormChange.ArgumentsChanged -> copy(argumentText = change.text)
        is AcpConnectionFormChange.SeparatorSelected -> copy(separator = change.separator)
        is AcpConnectionFormChange.VariableAdded -> {
            if (environment.any { it.rowId == change.rowId }) this
            else copy(environment = environment + AcpConnectionForm.Variable(change.rowId))
        }
        is AcpConnectionFormChange.VariableNameChanged -> copy(environment = environment.map {
            if (it.rowId == change.rowId) it.copy(name = change.name) else it
        })
        is AcpConnectionFormChange.VariableValueChanged -> copy(environment = environment.map {
            if (it.rowId == change.rowId) it.copy(value = change.value) else it
        })
        is AcpConnectionFormChange.VariableRemoved -> copy(
            environment = environment.filterNot { it.rowId == change.rowId }.ifEmpty {
                listOf(AcpConnectionForm.Variable(change.rowId + 1))
            },
        )
    }

package io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines

public sealed interface EngineSettingsScreenAction {

    public data object Load : EngineSettingsScreenAction

    public data class Select(val id: String) : EngineSettingsScreenAction

    public data object AddConnection : EngineSettingsScreenAction

    public data class EditConnection(val change: AcpConnectionFormChange) : EngineSettingsScreenAction

    public data object OpenEngineSettings : EngineSettingsScreenAction

    public data object OpenConnection : EngineSettingsScreenAction

    public data class SetResponseFormat(val format: String) : EngineSettingsScreenAction

    public data class SetRules(val rules: String) : EngineSettingsScreenAction

    public data class StartRename(val id: String) : EngineSettingsScreenAction

    public data class ConfirmRename(val id: String, val name: String) : EngineSettingsScreenAction

    public data object CancelRename : EngineSettingsScreenAction

    public data class Copy(val id: String) : EngineSettingsScreenAction

    public data class Delete(val id: String) : EngineSettingsScreenAction

    public data class ConfirmDelete(val id: String) : EngineSettingsScreenAction

    public data object Check : EngineSettingsScreenAction

    public data object Save : EngineSettingsScreenAction

    public data object Back : EngineSettingsScreenAction

    public data object Close : EngineSettingsScreenAction

    public data object BackToList : EngineSettingsScreenAction

    public data object Discard : EngineSettingsScreenAction

    public data object DismissDialog : EngineSettingsScreenAction

    public data class FindSetting(val field: EngineSettingField) : EngineSettingsScreenAction

    public data object CancelFieldSearch : EngineSettingsScreenAction
}

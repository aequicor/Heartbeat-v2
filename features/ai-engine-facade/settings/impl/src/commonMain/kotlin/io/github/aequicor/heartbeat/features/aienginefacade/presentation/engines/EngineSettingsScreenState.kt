package io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines

public sealed interface EngineSettingsScreenState {

    public data object Loading : EngineSettingsScreenState

    public data class Failed(val failure: EngineOperationFailure) : EngineSettingsScreenState

    public data class Loaded(
        val rows: List<Row>,
        val editor: Editor? = null,
        val renameId: String? = null,
        val dialog: Dialog? = null,
        val notice: String? = null,
        val saving: Boolean = false,
        val requestedField: EngineSettingField? = null,
        val highlighted: EngineSettingField? = null,
        val highlightRequest: Long = 0,
    ) : EngineSettingsScreenState

    public data class Row(
        val id: String,
        val name: String,
        val command: String,
        val subtitle: String = command,
    )

    public sealed interface Editor {

        public val engineId: String?
        public val changed: Boolean

        public data class Connection(
            override val engineId: String?,
            override val changed: Boolean,
            val form: AcpConnectionForm,
            val check: AcpCheckState = AcpCheckState.Idle,
        ) : Editor

        public data class Engine(
            override val engineId: String,
            override val changed: Boolean,
            val name: String,
            val connectionName: String,
            val command: String,
            val responseFormat: String,
            val rules: String,
        ) : Editor
    }

    public sealed interface Dialog {

        public data class Delete(val id: String, val engineName: String, val connectionName: String) : Dialog

        public data object Unsaved : Dialog
    }
}

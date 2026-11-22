package io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines

public sealed interface AcpCheckState {

    public data object Idle : AcpCheckState

    public data object Retry : AcpCheckState

    public data object Checking : AcpCheckState

    public data class Connected(val version: Int) : AcpCheckState

    public data class Failed(val failure: EngineOperationFailure) : AcpCheckState
}

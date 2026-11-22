package io.github.aequicor.heartbeat.features.aienginefacade.component.engines

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenAction
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsStoreFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal class EngineSettingsComponentImpl(
    factory: EngineSettingsStoreFactory,
    mainDispatcher: CoroutineDispatcher,
    onClose: () -> Unit,
) : EngineSettingsComponent, InstanceKeeper.Instance {

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val store = factory.create(
        scope,
        ignoreEnvironmentNameCase = System.getProperty("os.name").startsWith("Windows", ignoreCase = true),
        close = onClose,
    )

    override val state = store.state

    @Throwable(CancellationException::class)
    override suspend fun dispatch(action: EngineSettingsScreenAction) = store.dispatch(action)

    override fun onDestroy() = scope.cancel()
}

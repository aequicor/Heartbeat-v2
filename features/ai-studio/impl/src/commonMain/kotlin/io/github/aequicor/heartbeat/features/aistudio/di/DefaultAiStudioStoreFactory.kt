package io.github.aequicor.heartbeat.features.aistudio.di

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStoreFactory
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacade
import io.github.aequicor.heartbeat.features.aistudio.data.StudioCatalog
import io.github.aequicor.heartbeat.features.aistudio.presentation.DefaultAiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.StudioEngineSessions
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioStoreFactory
import kotlinx.coroutines.CoroutineScope

@ContributesBinding(AppScope::class)
public class DefaultAiStudioStoreFactory private constructor(
    private val preferences: PreferencesStoreFactory,
    private val catalog: StudioCatalog?,
    private val engines: AiEngineFacade?,
) : AiStudioStoreFactory {
    @Inject
    public constructor(preferences: PreferencesStoreFactory, database: DatabaseConnection, engines: AiEngineFacade) :
        this(preferences, StudioCatalog(database), engines)

    /** Standalone fixture for the existing preview and screenshot clients. */
    public constructor(preferences: PreferencesStoreFactory) : this(preferences, null, null)

    override fun create(scope: CoroutineScope): AiStudioStore =
        DefaultAiStudioStore(
            preferences.forNamespace("aurora"), scope, catalog,
            catalog?.let { StudioEngineSessions(requireNotNull(engines), it) },
        )
}

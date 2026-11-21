package io.github.aequicor.heartbeat.features.aistudio.di

import dev.zacsweers.metro.ContributesBinding
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStoreFactory
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.data.repository.SqlDelightChatWorkspaceRepository
import io.github.aequicor.heartbeat.features.aistudio.presentation.DefaultAiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioStoreFactory
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraChat
import kotlinx.coroutines.CoroutineScope

@ContributesBinding(AppScope::class)
public class DefaultAiStudioStoreFactory(
    preferences: PreferencesStoreFactory,
    connection: DatabaseConnection,
) : AiStudioStoreFactory {
    private val repository = SqlDelightChatWorkspaceRepository(
        connection, preferences.forNamespace("aurora"), AuroraChat.serializer(),
    )

    override fun create(scope: CoroutineScope): AiStudioStore = DefaultAiStudioStore(repository, scope)
}

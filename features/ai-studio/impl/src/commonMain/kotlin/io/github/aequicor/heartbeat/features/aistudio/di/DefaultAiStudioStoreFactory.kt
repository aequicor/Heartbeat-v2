package io.github.aequicor.heartbeat.features.aistudio.di

import dev.zacsweers.metro.ContributesBinding
import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStoreFactory
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.presentation.DefaultAiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioStoreFactory
import kotlinx.coroutines.CoroutineScope

@ContributesBinding(AppScope::class)
public class DefaultAiStudioStoreFactory(private val preferences: PreferencesStoreFactory) : AiStudioStoreFactory {
    override fun create(scope: CoroutineScope): AiStudioStore =
        DefaultAiStudioStore(preferences.forNamespace("aurora"), scope)
}

package io.github.aequicor.heartbeat.features.aistudio.di

import dev.zacsweers.metro.ContributesBinding
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacade
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioStoreFactory
import io.github.aequicor.heartbeat.features.aistudio.presentation.DefaultAiStudioStore
import kotlinx.coroutines.CoroutineScope

@ContributesBinding(AppScope::class)
public class DefaultAiStudioStoreFactory(private val engines: AiEngineFacade) : AiStudioStoreFactory {
    override fun create(scope: CoroutineScope): AiStudioStore = DefaultAiStudioStore(engines, scope)
}

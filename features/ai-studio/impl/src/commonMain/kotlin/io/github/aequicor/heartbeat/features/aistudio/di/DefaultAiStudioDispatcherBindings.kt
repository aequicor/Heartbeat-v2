package io.github.aequicor.heartbeat.features.aistudio.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import io.github.aequicor.heartbeat.di.AppScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@BindingContainer
@ContributesTo(AppScope::class)
interface DefaultAiStudioDispatcherBindings {
    companion object {
        @Provides
        @Named("ai-studio-worker")
        @Suppress("InjectDispatcher") // This binding selects the dispatcher for the Aurora store.
        fun provideAiStudioWorker(): CoroutineDispatcher = Dispatchers.Default
    }
}

package io.github.aequicor.heartbeat.features.aistudio.component

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import io.github.aequicor.heartbeat.di.AppScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class AiStudioWorker

@BindingContainer
@ContributesTo(AppScope::class)
interface AiStudioDispatcherBindings {
    @Provides
    @AiStudioWorker
    @Suppress("InjectDispatcher") // This binding selects the dispatcher for the Aurora store.
    fun provideAiStudioWorker(): CoroutineDispatcher = Dispatchers.Default
}

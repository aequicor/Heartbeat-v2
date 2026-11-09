package io.github.aequicor.heartbeat.features.aienginefacade.di

import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineProvider

@ContributesIntoSet(AppScope::class)
@SingleIn(AppScope::class)
internal class MetroTestEngineProvider : AiEngineProvider {
    private val delegate = TestEngineProvider(TestEngineKey("metro-test"))
    override val key: AiEngineKey = delegate.key
    val creations: Int get() = delegate.creations

    override fun create(): AiEngine = delegate.create()
}

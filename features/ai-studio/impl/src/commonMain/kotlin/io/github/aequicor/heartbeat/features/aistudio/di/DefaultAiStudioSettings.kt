package io.github.aequicor.heartbeat.features.aistudio.di

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory implementation of the AI studio preferences. The avatar starts visible; persistence is
 * out of scope until a storage layer is introduced.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultAiStudioSettings : AiStudioSettings {

    private val mutableAvatarVisible = MutableStateFlow(true)

    override val avatarVisible: StateFlow<Boolean> = mutableAvatarVisible.asStateFlow()

    override fun setAvatarVisible(visible: Boolean) {
        mutableAvatarVisible.value = visible
    }
}

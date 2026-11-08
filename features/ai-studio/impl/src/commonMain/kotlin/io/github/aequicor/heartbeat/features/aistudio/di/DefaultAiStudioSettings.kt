package io.github.aequicor.heartbeat.features.aistudio.di

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStoreFactory
import io.github.aequicor.heartbeat.di.AppCoroutineScope
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Persistent AI studio preferences.
 *
 * The feature owns the keys and the namespace; the physical DataStore file belongs to the
 * platform. `avatarVisible` starts from the stored value and follows every later write, so the
 * workspace and the settings page observe the same source of truth.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class DefaultAiStudioSettings @Inject constructor(
    preferencesStoreFactory: PreferencesStoreFactory,
    appCoroutineScope: AppCoroutineScope,
) : AiStudioSettings {

    private val store = preferencesStoreFactory.forNamespace(NAMESPACE)

    override val avatarVisible: StateFlow<Boolean> = store
        .observeBoolean(AVATAR_VISIBLE, default = true)
        .stateIn(
            scope = appCoroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    override suspend fun setAvatarVisible(visible: Boolean) {
        store.putBoolean(AVATAR_VISIBLE, visible)
    }

    private companion object {
        private const val NAMESPACE = "ai-studio"
        private const val AVATAR_VISIBLE = "avatar_visible"
    }
}

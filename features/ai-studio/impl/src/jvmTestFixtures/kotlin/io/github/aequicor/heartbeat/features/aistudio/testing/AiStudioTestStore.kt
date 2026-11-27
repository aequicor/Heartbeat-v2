package io.github.aequicor.heartbeat.features.aistudio.testing

import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.DefaultAiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioStore
import kotlinx.coroutines.CoroutineScope

public fun createAiStudioTestStore(preferences: PreferencesStore, scope: CoroutineScope): AiStudioStore =
    DefaultAiStudioStore(preferences, scope)

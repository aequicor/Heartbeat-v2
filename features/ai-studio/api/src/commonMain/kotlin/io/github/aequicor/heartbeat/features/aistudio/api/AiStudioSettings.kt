package io.github.aequicor.heartbeat.features.aistudio.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Preferences of the AI studio, shared by the workspace and its settings page.
 *
 * The values are persisted by the storage infrastructure and survive application restarts.
 */
public interface AiStudioSettings {

    /** Whether the user avatar is visible in the studio navigation. */
    public val avatarVisible: StateFlow<Boolean>

    public suspend fun setAvatarVisible(visible: Boolean)
}

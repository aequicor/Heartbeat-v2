package io.github.aequicor.heartbeat.features.aistudio.api

import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory preferences of the AI studio, shared by the workspace and its settings page. Persistence
 * is not part of this contract yet: the values live only for the lifetime of the application.
 */
public interface AiStudioSettings {

    /** Whether the user avatar is visible in the studio navigation. */
    public val avatarVisible: StateFlow<Boolean>

    public fun setAvatarVisible(visible: Boolean)
}

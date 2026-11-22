package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

public interface AiStudioStore {
    public val projectsSessionsStore: ProjectsSessionsStore?
    public val screenState: StateFlow<AiStudioScreenState>
    public val draft: StateFlow<AiStudioScreenState.Draft>
    public val liveText: StateFlow<AiStudioScreenState.LiveText>

    public fun dispatch(action: AiStudioAction)
}

/** The owning component supplies a background scope that ends with its lifecycle. */
public interface AiStudioStoreFactory {
    public fun create(scope: CoroutineScope): AiStudioStore
}

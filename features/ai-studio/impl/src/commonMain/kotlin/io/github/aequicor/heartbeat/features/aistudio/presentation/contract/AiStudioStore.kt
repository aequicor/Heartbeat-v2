package io.github.aequicor.heartbeat.features.aistudio.presentation.contract

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

public interface AiStudioStore {
    public val screenState: StateFlow<AiStudioScreenState>

    public fun dispatch(action: AiStudioAction)
}

/** The owning component supplies a scope that ends with its lifecycle. */
public interface AiStudioStoreFactory {
    public fun create(scope: CoroutineScope): AiStudioStore
}

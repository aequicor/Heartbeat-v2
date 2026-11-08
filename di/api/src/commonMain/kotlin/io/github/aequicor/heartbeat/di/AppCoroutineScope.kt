package io.github.aequicor.heartbeat.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

/**
 * Coroutine scope owned by the application composition root.
 *
 * Infrastructure services that must observe a flow for the whole process lifetime (for example a
 * persistent settings store) receive this scope instead of creating an unmanaged one. The platform
 * cancels it when the application shuts down.
 */
public class AppCoroutineScope(
    private val delegate: CoroutineScope,
) : CoroutineScope by delegate {

    public fun cancel() {
        delegate.cancel()
    }
}

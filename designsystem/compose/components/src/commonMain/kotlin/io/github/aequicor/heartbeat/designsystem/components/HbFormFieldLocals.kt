package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.runtime.staticCompositionLocalOf

/** Label and validation context provided by [HbFormField] to its control (HB-DS-016). */
internal object HbFormFieldLocals {

    internal val LocalLabel = staticCompositionLocalOf<String?> { null }

    internal val LocalError = staticCompositionLocalOf<String?> { null }
}

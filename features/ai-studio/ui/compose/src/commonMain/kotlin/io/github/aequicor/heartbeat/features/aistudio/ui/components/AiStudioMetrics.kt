package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing

/**
 * Product-level measurements that are not part of the shared token contract. A value that another
 * feature could need belongs in the Design System; a hairline of the AI studio chrome does not.
 */
internal object AiStudioMetrics {

    /** Hairline border of a card or a field, half of the smallest spacing step. */
    val hairline: Dp = HbSpacing.Xxs / 2
}

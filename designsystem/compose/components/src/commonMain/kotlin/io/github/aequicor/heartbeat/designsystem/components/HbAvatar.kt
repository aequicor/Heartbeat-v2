package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Compact identity of a person or a workspace (HB-DS-018). The avatar is display-only; the visible
 * [initials] stay readable and [contentDescription] names the identity, so the circle is not the
 * only information. Without a description the avatar is decorative and carries no text semantics
 * beyond its initials.
 */
@Composable
fun HbAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val colors = HbThemeDefaults.LocalColors.current

    Box(
        modifier = modifier
            .size(AVATAR_SIZE)
            .clip(CircleShape)
            .background(colors.accent.container)
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = initials,
            style = HbTypography.Metadata,
            color = { colors.accent.foreground },
        )
    }
}

private val AVATAR_SIZE = 24.dp

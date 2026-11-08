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
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Compact identity of a person or a workspace (HB-DS-018). The avatar is display-only; the visible
 * [initials] stay readable and [contentDescription] names the identity, so the circle is not the
 * only information. Without a description the avatar is decorative and carries no text semantics
 * beyond its initials.
 *
 * The circle scales with the resolved text preference: initials use a scalable type role, so a
 * fixed 24-unit circle would clip them at a large text scale. The geometry and the type grow
 * together, so the initials stay inside the circle at every supported scale.
 */
@Composable
fun HbAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val colors = HbTheme.colors
    val avatarSize = AVATAR_SIZE * HbTheme.textScale

    Box(
        modifier = modifier
            .size(avatarSize)
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
            maxLines = 1,
        )
    }
}

private val AVATAR_SIZE = 24.dp

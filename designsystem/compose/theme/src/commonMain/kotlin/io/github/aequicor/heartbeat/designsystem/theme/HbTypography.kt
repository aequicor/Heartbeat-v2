package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.aequicor.heartbeat.designsystem.foundation.HbTypeScales
import io.github.aequicor.heartbeat.designsystem.foundation.HbTypeStyle
import io.github.aequicor.heartbeat.designsystem.foundation.HbTypeWeights

/**
 * Text styles of the application, resolved from the portable type scale. The desktop scale is the
 * default; a touch host resolves the touch scale through the same roles.
 */
object HbTypography {
    private val scale = HbTypeScales.Desktop
    val PageTitle = scale.pageTitle.toTextStyle()
    val Title = scale.sectionTitle.toTextStyle()
    val PaneTitle = scale.paneTitle.toTextStyle()
    val Body = scale.readingBody.toTextStyle()
    val Label = scale.uiLabel.toTextStyle()
    val Caption = scale.caption.toTextStyle()
    val Code = scale.code.toTextStyle()
    val Metadata = scale.metadata.toTextStyle()
}

internal fun HbTypeStyle.toTextStyle(): TextStyle = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight.toFontWeight(),
)

internal fun Int.toFontWeight(): FontWeight = when (this) {
    HbTypeWeights.Medium -> FontWeight.Medium
    HbTypeWeights.SemiBold -> FontWeight.SemiBold
    else -> FontWeight.Normal
}

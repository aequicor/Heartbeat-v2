package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.aequicor.heartbeat.designsystem.resources.Res
import io.github.aequicor.heartbeat.designsystem.resources.golos_text
import org.jetbrains.compose.resources.Font

@Composable
public fun HbAuroraFont(): FontFamily = FontFamily(
    Font(Res.font.golos_text, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(Res.font.golos_text, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(Res.font.golos_text, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(Res.font.golos_text, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbTooltip
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors

internal enum class AuroraSourceAction(val label: String, val icon: HbIconGlyph? = null) {
    Wrap("Перенос строк"), Source("Показать исходник"), Preview("Показать диаграмму"), Expand("Открыть на всё окно"),
    Copy("Копировать исходник", HbIconGlyph.Copy), Copied("Скопировано", HbIconGlyph.Check),
    Retry("Повторить отображение", HbIconGlyph.Retry), Close("Закрыть просмотр", HbIconGlyph.Close),
    Larger("Увеличить масштаб", HbIconGlyph.Plus), Smaller("Уменьшить масштаб", HbIconGlyph.Minus),
}

@Composable
internal fun SourceAction(
    action: AuroraSourceAction,
    touch: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val tint = if (enabled) Colors.MessageInk else Colors.MessageMuted.copy(alpha = .45f)
    val target = if (touch) AuroraLayout.TouchTarget else AuroraLayout.DesktopTarget
    HbTooltip(action.label, enabled = !touch && enabled) {
        AuroraControl(
            onClick, modifier.size(target).semantics {
                if (action == AuroraSourceAction.Wrap) stateDescription = if (selected) "Включено" else "Выключено"
                if (action == AuroraSourceAction.Copied) liveRegion = LiveRegionMode.Polite
            },
            label = action.label, enabled = enabled, touch = touch, round = false,
            horizontalPadding = Dp.Hairline,
            background = if (selected) Colors.MessageActionHover else Color.Transparent,
        ) {
            Box(Modifier.size(target), contentAlignment = Alignment.Center) {
                val glyph = action.icon
                if (glyph != null) HbIcon(glyph, tint = tint, size = AuroraLayout.Glyph)
                else Canvas(Modifier.size(AuroraLayout.Glyph)) { drawSourceAction(action, tint) }
            }
        }
    }
}

private fun DrawScope.drawSourceAction(action: AuroraSourceAction, color: Color) {
    fun point(x: Int, y: Int) = Offset(x * size.width / 24, y * size.height / 24)
    fun line(x: Int, y: Int, endX: Int, endY: Int) {
        drawLine(color, point(x, y), point(endX, endY), size.width / 12, StrokeCap.Round)
    }
    when (action) {
        AuroraSourceAction.Expand -> {
            line(14, 4, 20, 4)
            line(20, 4, 20, 10)
            line(20, 4, 13, 11)
            line(4, 14, 4, 20)
            line(4, 20, 10, 20)
            line(4, 20, 11, 13)
        }
        AuroraSourceAction.Wrap -> {
            line(3, 5, 20, 5)
            line(3, 11, 19, 11)
            line(19, 11, 21, 13)
            line(21, 13, 21, 15)
            line(21, 15, 19, 17)
            line(19, 17, 11, 17)
            line(11, 17, 14, 14)
            line(11, 17, 14, 20)
            line(3, 17, 7, 17)
        }
        AuroraSourceAction.Source -> {
            line(7, 7, 2, 12)
            line(2, 12, 7, 17)
            line(17, 7, 22, 12)
            line(22, 12, 17, 17)
            line(14, 4, 10, 20)
        }
        AuroraSourceAction.Preview -> {
            fun box(x: Int, y: Int) {
                line(x, y, x + 6, y)
                line(x + 6, y, x + 6, y + 6)
                line(x + 6, y + 6, x, y + 6)
                line(x, y + 6, x, y)
            }
            box(3, 3)
            box(15, 15)
            line(6, 9, 6, 18)
            line(6, 18, 15, 18)
        }
        else -> Unit
    }
}

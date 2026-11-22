package io.github.aequicor.heartbeat.features.settings.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbText
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.resources.Res
import io.github.aequicor.heartbeat.designsystem.resources.ic_settings_back
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Aurora
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.settings.api.SettingsPageDescriptor
import io.github.aequicor.heartbeat.features.settings.api.SettingsPageId
import io.github.aequicor.heartbeat.features.settings.ui.ComposeSettingsPageRegistry
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun SettingsSidebar(
    pages: List<SettingsPageDescriptor>,
    selectedPageId: SettingsPageId?,
    registry: ComposeSettingsPageRegistry,
    onSearch: () -> Unit,
    onSelect: (SettingsPageId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxHeight().background(HbTheme.colors.surface.subtle)
            .padding(horizontal = Aurora.ContentInset).padding(top = Aurora.ChromeInset),
    ) {
        SettingsSidebarAction("Назад в чат", onBack, outlined = false) { color ->
            Image(painterResource(Res.drawable.ic_settings_back), null, Modifier.size(Aurora.LargeGlyph),
                colorFilter = ColorFilter.tint(color))
        }
        Spacer(Modifier.height(HbSpacing.L))
        HbText("Настройки", Modifier.padding(horizontal = Aurora.ContentInset), style = HbTypography.PageTitle)
        Spacer(Modifier.height(Aurora.DocumentHeadingGap))
        SettingsSidebarAction("Найти настройку", onSearch, outlined = true) { color ->
            HbIcon(HbIconGlyph.Search, tint = color, size = Aurora.LargeGlyph)
        }
        Spacer(Modifier.height(Aurora.ContentInset))
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
        ) {
            pages.forEach { page ->
                val icon = registry.find(page.id.value)?.navigationIcon
                val selected = page.id == selectedPageId
                SettingsSidebarAction(
                    label = page.title, onClick = { onSelect(page.id) }, outlined = selected, selected = selected,
                ) { color ->
                    if (icon == null) HbIcon(HbIconGlyph.Settings, tint = color, size = Aurora.LargeGlyph)
                    else icon(page.id.value, color)
                }
            }
        }
    }
}

@Composable
private fun SettingsSidebarAction(
    label: String,
    onClick: () -> Unit,
    outlined: Boolean,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    icon: @Composable (Color) -> Unit,
) {
    val colors = HbTheme.colors
    val color = if (selected == true) colors.accent.foreground else colors.text.primary
    var focused by remember { mutableStateOf(false) }
    val shape = HbShapes.Small
    val fill = if (outlined) colors.surface.raised else Color.Transparent
    val outline = if (focused) colors.focus else colors.border.control
    val action = if (selected == null) Modifier.clickable(role = Role.Button, onClick = onClick)
        else Modifier.selectable(selected, role = Role.Tab, onClick = onClick)
    val height = if (selected == null) HbSizes.controlHeight else Aurora.SettingsRowHeight
    Row(
        modifier.fillMaxWidth().heightIn(min = height)
            .clip(shape).background(fill)
            .then(if (focused || outlined) Modifier.border(Aurora.Stroke, outline, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }.then(action)
            .padding(horizontal = Aurora.ContentInset, vertical = HbSpacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(color)
        BasicText(label, style = HbTypography.Label.copy(color = color))
    }
}

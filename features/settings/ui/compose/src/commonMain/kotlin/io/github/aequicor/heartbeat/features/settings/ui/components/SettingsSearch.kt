package io.github.aequicor.heartbeat.features.settings.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbButtonVariant
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbIconButton
import io.github.aequicor.heartbeat.designsystem.components.HbSearchField
import io.github.aequicor.heartbeat.designsystem.components.HbText
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Aurora
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.settings.api.SettingsPageDescriptor
import io.github.aequicor.heartbeat.features.settings.api.SettingsPageId
import io.github.aequicor.heartbeat.features.settings.ui.ComposeSettingsPageRegistry
import io.github.aequicor.heartbeat.navigation.compose.ComposeSettingsPageProvider.SearchResult

@Composable
internal fun SettingsSearch(
    pages: List<SettingsPageDescriptor>,
    registry: ComposeSettingsPageRegistry,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (SettingsPageId, SearchResult?) -> Unit,
    onDismiss: () -> Unit,
) {
    val results = remember(pages, registry, query) { searchResults(pages, registry, query) }
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(HbSpacing.M).width(Aurora.SettingsSearchWidth).clip(HbShapes.Medium)
                .background(HbTheme.colors.surface.raised)
                .border(Aurora.Stroke, HbTheme.colors.border.subtle, HbShapes.Medium)
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                        onDismiss()
                        true
                    } else false
                }.padding(Aurora.ContentInset),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HbSearchField(query, onQueryChange, "Очистить поиск",
                    modifier = Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent {
                        val first = results.firstOrNull()
                        val submit = it.type == KeyEventType.KeyDown && it.key in listOf(Key.Enter, Key.NumPadEnter)
                        if (submit && first != null) {
                            onSelect(first.pageId, first.field)
                            true
                        } else false
                    }, placeholder = "Найти настройку")
                HbIconButton(contentDescription = "Закрыть поиск", onClick = onDismiss) { HbIcon(HbIconGlyph.Close) }
            }
            SideEffect(focus) { focus.requestFocus() }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (results.isEmpty()) {
                    HbText("Настройки не найдены", Modifier.padding(HbSpacing.S), HbTypography.Label)
                    HbButton("Сбросить поиск", {
                        onQueryChange("")
                        focus.requestFocus()
                    }, variant = HbButtonVariant.Ghost)
                }
                results.groupBy { it.group }.forEach { (group, entries) ->
                    HbText(group, Modifier.padding(HbSpacing.S), HbTypography.Caption)
                    entries.forEach { match ->
                        Row(Modifier.fillMaxWidth().heightIn(min = Aurora.DesktopTarget).clip(HbShapes.Small)
                            .clickable(role = Role.Button) { onSelect(match.pageId, match.field) }
                            .padding(horizontal = HbSpacing.S), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            HbText(match.label, style = HbTypography.Label)
                            HbIcon(HbIconGlyph.ChevronDown, Modifier.rotate(ForwardRotation), size = Aurora.SmallGlyph)
                        }
                    }
                }
            }
        }
    }
}

private class Match(val pageId: SettingsPageId, val group: String, val label: String, val field: SearchResult? = null)

@Suppress("DoNotSplitByRegex")
private fun searchResults(
    pages: List<SettingsPageDescriptor>,
    registry: ComposeSettingsPageRegistry,
    query: String,
): List<Match> =
    buildList {
        val terms = query.trim().split(Whitespace)
        for (page in pages) {
            val words = page.keywords + page.title
            if (terms.all { term -> words.any { it.contains(term, ignoreCase = true) } }) {
                add(Match(page.id, "Разделы", page.title))
            }
            for (field in registry.find(page.id.value)?.search(page.id.value, query).orEmpty()) {
                add(Match(page.id, "${page.title} / ${field.group}", field.label, field))
            }
        }
    }

private val Whitespace = Regex("""\s+""")
private const val ForwardRotation = -90f

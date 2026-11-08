package io.github.aequicor.heartbeat.features.settings.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbNavigationItem
import io.github.aequicor.heartbeat.designsystem.components.HbPaneHeader
import io.github.aequicor.heartbeat.designsystem.components.HbSearchField
import io.github.aequicor.heartbeat.designsystem.foundation.HbPaneGeometry
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.features.settings.api.SettingsPageDescriptor
import io.github.aequicor.heartbeat.features.settings.api.SettingsPageId

/** Flat, searchable list of settings pages. The search field stays pinned above the list. */
@Suppress("DoNotSplitByRegex")
@Composable
internal fun SettingsSidebar(
    pages: List<SettingsPageDescriptor>,
    selectedPageId: SettingsPageId?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (SettingsPageId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors
    val filtered = remember(pages, query) {
        val terms = query.trim().split(Whitespace).filter { it.isNotEmpty() }
        if (terms.isEmpty()) {
            pages
        } else {
            pages.filter { page ->
                terms.all { term ->
                    page.title.contains(term, ignoreCase = true) ||
                        page.keywords.any { it.contains(term, ignoreCase = true) }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(min = HbPaneGeometry.SidebarMin.dp, max = HbPaneGeometry.SidebarMax.dp)
            .background(colors.surface.subtle),
    ) {
        HbPaneHeader(title = "Настройки")
        HbSearchField(
            value = query,
            onValueChange = onQueryChange,
            clearDescription = "Очистить поиск",
            placeholder = "Поиск настроек…",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HbSpacing.S),
        )
        Spacer(Modifier.height(HbSpacing.Xs))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(HbSpacing.S),
            verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
        ) {
            filtered.forEach { page ->
                HbNavigationItem(
                    label = page.title,
                    selected = page.id == selectedPageId,
                    onClick = { onSelect(page.id) },
                )
            }
        }
    }
}

private val Whitespace = Regex("""\s+""")

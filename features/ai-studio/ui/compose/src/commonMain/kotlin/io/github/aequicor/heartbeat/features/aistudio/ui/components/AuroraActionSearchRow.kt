package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.close_navigation_search
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.submit_navigation_search
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AuroraActionSearchRow(
    actionLabel: String,
    searchLabel: String,
    expanded: Boolean,
    appliedQuery: String,
    onAction: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
    searchEnabled: Boolean = true,
    target: Dp = AuroraLayout.NavigationRow,
    actionBackground: Color = AuroraColors.NavigationPrimaryAction,
    actionInk: Color = AuroraColors.NavigationOnPrimaryAction,
    searchPlaceholder: String = searchLabel,
    actionHoverBackground: Color? = null,
    actionInteractionSource: MutableInteractionSource? = null,
    searchInteractionSource: MutableInteractionSource? = null,
    actionIcon: @Composable () -> Unit,
) {
    var query by remember { mutableStateOf(appliedQuery) }
    var restoreSearchFocus by remember { mutableStateOf(false) }
    val inputFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val close = {
        restoreSearchFocus = true
        onCollapse()
    }
    val submit = { onSearch(query) }
    val progress by animateSearchExpansion(expanded)
    LaunchedEffect(expanded) {
        withFrameNanos { }
        if (expanded) {
            query = appliedQuery
            inputFocus.requestFocus()
        } else if (restoreSearchFocus) {
            restoreSearchFocus = false
            searchFocus.requestFocus()
        }
    }
    BoxWithConstraints(
        modifier.fillMaxWidth().onPreviewKeyEvent { event ->
            if (!expanded || event.key != Key.Escape || event.type != KeyEventType.KeyDown) false
            else {
                close()
                true
            }
        },
    ) {
        val gap = AuroraLayout.Space
        val actionWidth = target + (maxWidth - gap - target * 2).coerceAtLeast(Dp.Hairline) * (1f - progress)
        val compactPadding = (target - AuroraLayout.Glyph) / 2
        val actionPadding = AuroraLayout.ChromeInset + (compactPadding - AuroraLayout.ChromeInset) * progress
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            AuroraSearchAction(
                actionLabel, expanded, progress, if (expanded) close else onAction,
                enabled = expanded || actionEnabled, target, actionWidth, actionPadding,
                SearchActionColors(actionBackground, actionInk, actionHoverBackground),
                actionInteractionSource, actionIcon,
            )
            AuroraSearchControls(
                searchLabel, expanded, progress, target,
                enabled = searchEnabled || expanded, searchFocus, onExpand, submit,
                searchInteractionSource, modifier = Modifier.weight(1f),
            ) {
                AuroraSearchInput(
                    query, { text ->
                        query = text
                        if (text.isEmpty() && appliedQuery.isNotEmpty()) onSearch("")
                    }, searchLabel, searchPlaceholder, expanded, submit,
                    Modifier.weight(1f).padding(start = AuroraLayout.Space * progress)
                        .alpha(progress).focusRequester(inputFocus),
                )
            }
        }
    }
}

@Composable
private fun AuroraSearchAction(
    label: String,
    expanded: Boolean,
    progress: Float,
    onAction: () -> Unit,
    enabled: Boolean,
    target: Dp,
    width: Dp,
    padding: Dp,
    colors: SearchActionColors,
    interactionSource: MutableInteractionSource?,
    icon: @Composable () -> Unit,
) {
    AuroraControl(
        onAction,
        Modifier.width(width).height(target),
        label = if (expanded) stringResource(Res.string.close_navigation_search) else null,
        enabled = enabled,
        background = colors.background,
        focus = colors.ink,
        round = false,
        horizontalPadding = padding,
        minimumTarget = target,
        hoverBackground = colors.hover,
        interactionSource = interactionSource,
    ) {
        icon()
        BasicText(
            label,
            Modifier.padding(start = AuroraLayout.ControlGap).alpha(1f - progress),
            style = HbTypography.Label.copy(color = colors.ink),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun AuroraSearchControls(
    label: String,
    expanded: Boolean,
    progress: Float,
    target: Dp,
    enabled: Boolean,
    searchFocus: FocusRequester,
    onExpand: () -> Unit,
    onSubmit: () -> Unit,
    interactionSource: MutableInteractionSource?,
    modifier: Modifier = Modifier,
    input: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(AuroraLayout.SmallRadius)
    Row(
        modifier.height(target)
            .background(AuroraColors.NavigationSearchSurface, shape)
            .border(
                AuroraLayout.Stroke,
                if (expanded) AuroraColors.Primary else AuroraColors.NavigationSearchOutline,
                shape,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (expanded || progress > 0f) input()
        AuroraControl(
            if (expanded) onSubmit else onExpand,
            Modifier.width(target).focusRequester(searchFocus),
            label = if (expanded) stringResource(Res.string.submit_navigation_search) else label,
            enabled = enabled,
            background = Color.Transparent,
            round = false,
            horizontalPadding = (target - AuroraLayout.Glyph) / 2,
            minimumTarget = target,
            hoverBackground = AuroraColors.NavigationHover,
            interactionSource = interactionSource,
        ) {
            HbIcon(
                HbIconGlyph.Search,
                modifier = Modifier.size(AuroraLayout.Glyph),
                tint = if (enabled) AuroraColors.NavigationIcon else AuroraColors.Muted,
                size = AuroraLayout.Glyph,
            )
        }
    }
}

@Composable
private fun AuroraSearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    label: String,
    placeholder: String,
    expanded: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        query,
        onQueryChange,
        modifier.then(
            if (expanded) Modifier.semantics { contentDescription = label }
            else Modifier.clearAndSetSemantics { hideFromAccessibility() },
        ).onPreviewKeyEvent { event ->
            when (event.key) {
                Key.Enter, Key.NumPadEnter -> {
                    if (event.type == KeyEventType.KeyDown) onSubmit()
                    true
                }
                else -> false
            }
        },
        enabled = expanded,
        singleLine = true,
        textStyle = HbTypography.Label.copy(color = AuroraColors.DesktopInk),
        cursorBrush = SolidColor(AuroraColors.Primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        decorationBox = { field ->
            Box {
                if (query.isEmpty()) BasicText(
                    placeholder,
                    style = HbTypography.Label.copy(color = AuroraColors.Muted),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                field()
            }
        },
    )
}

@Composable
private fun animateSearchExpansion(expanded: Boolean) = animateFloatAsState(
    targetValue = if (expanded) 1f else 0f,
    animationSpec = if (HbTheme.motion == HbMotionPreference.Reduced) snap()
    else tween(SearchTransitionMillis, easing = SearchEasing),
    label = "Navigation search",
)

private data class SearchActionColors(val background: Color, val ink: Color, val hover: Color?)

private const val SearchTransitionMillis = 220
private val SearchEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

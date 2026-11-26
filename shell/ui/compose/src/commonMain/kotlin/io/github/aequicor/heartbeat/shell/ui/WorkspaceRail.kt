package io.github.aequicor.heartbeat.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.designsystem.theme.HbWindowCaption
import io.github.aequicor.heartbeat.designsystem.theme.HbWindowClientArea
import io.github.aequicor.heartbeat.navigation.api.AppRoute
import io.github.aequicor.heartbeat.navigation.decompose.WorkspaceRegistration

@Composable
internal fun WorkspaceRail(
    workspaces: List<WorkspaceRegistration>,
    activeFeature: String,
    open: (AppRoute) -> Unit,
) {
    val caption = HbWindowCaption.current
    val top = if (caption.leftInset > Dp.Hairline) Layout.NavigationRailCaptionOffset else Dp.Hairline
    val railShape = RoundedCornerShape(topStart = Layout.ContentInset, bottomStart = Layout.ContentInset)
    Column(
        Modifier.width(Layout.WorkspaceRailWidth).fillMaxHeight().background(HbAuroraColors.WorkspaceRail, railShape)
            .padding(horizontal = Layout.SmallGap)
            .padding(top = top + Layout.ChromeInset, bottom = Layout.ChromeInset)
            .selectableGroup(),
    ) {
        workspaces.filterNot { it.inFooter }.forEach { item ->
            WorkspaceButton(item, item.route.featureId == activeFeature, open)
            Spacer(Modifier.height(Layout.WorkspaceItemGap))
        }
        if (workspaces.none { it.route.featureId == "skill-builder" }) {
            Column(
                Modifier.fillMaxWidth().semantics { disabled() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Layout.SmallGap),
            ) {
                HbIcon(HbIconGlyph.SkillsWorkspace, tint = HbAuroraColors.WorkspaceDisabled, size = Layout.WorkspaceIcon)
                BasicText(
                    "Мастер скилов",
                    style = HbTypography.WorkspaceLabel.copy(
                        color = HbAuroraColors.WorkspaceDisabled, textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (workspaces.any { it.inFooter }) {
            Spacer(Modifier.fillMaxWidth().height(Layout.Stroke).background(HbAuroraColors.WorkspaceDivider))
            Spacer(Modifier.height(Layout.WorkspaceFooterGap))
            workspaces.filter { it.inFooter }.forEach { item ->
                WorkspaceButton(item, item.route.featureId == activeFeature, open)
            }
        }
    }
}

@Composable
private fun WorkspaceButton(item: WorkspaceRegistration, selected: Boolean, open: (AppRoute) -> Unit) {
    val interaction = remember(item.route) { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val ink = when {
        selected -> HbAuroraColors.WorkspaceActive
        item.inFooter -> HbAuroraColors.WorkspaceFooterInk
        else -> HbAuroraColors.WorkspaceInactive
    }
    val shape = RoundedCornerShape(Layout.SmallRadius)
    val focus = if (focused && HbFocusIndication.keyboardNavigation) {
        Modifier.border(Layout.FrameStroke, HbAuroraColors.Primary, shape)
    } else Modifier
    HbWindowClientArea { client ->
        Column(
            client.fillMaxWidth().height(Layout.WorkspaceItemHeight).clip(shape)
                .background(if (hovered) HbAuroraColors.NavigationHover else Color.Transparent)
                .then(focus)
                .selectable(selected, interactionSource = interaction, indication = null, role = Role.Tab) { open(item.route) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HbIcon(item.icon, tint = ink, size = Layout.WorkspaceIcon)
            Spacer(Modifier.height(Layout.SmallGap))
            BasicText(
                item.label,
                style = (if (item.inFooter) HbTypography.WorkspaceFooterLabel else HbTypography.WorkspaceLabel)
                    .copy(color = ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

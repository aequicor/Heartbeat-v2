package io.github.aequicor.heartbeat.designsystem.sandbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbAvatar
import io.github.aequicor.heartbeat.designsystem.components.HbBreadcrumbs
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbCheckbox
import io.github.aequicor.heartbeat.designsystem.components.HbLink
import io.github.aequicor.heartbeat.designsystem.components.HbRadioGroup
import io.github.aequicor.heartbeat.designsystem.components.HbSelect
import io.github.aequicor.heartbeat.designsystem.components.HbSettingsRow
import io.github.aequicor.heartbeat.designsystem.components.HbSwitch
import io.github.aequicor.heartbeat.designsystem.components.HbTable
import io.github.aequicor.heartbeat.designsystem.components.HbTabs
import io.github.aequicor.heartbeat.designsystem.components.HbTextField
import io.github.aequicor.heartbeat.designsystem.components.HbTreeItem
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.math.abs

/**
 * Contract checks of the kit that a static review can only describe (HB-DS-012, HB-DS-015,
 * HB-DS-018, HB-DS-019): geometry, semantics and keyboard behavior are asserted through the Compose
 * test API, so a regression fails the build.
 */
@OptIn(ExperimentalTestApi::class)
class KitContractTest {

    @Test
    fun busyButtonKeepsItsWidth() = runComposeUiTest {
        setContent {
            HbTheme {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HbButton(
                        text = "Сохранить",
                        onClick = {},
                        busy = true,
                        modifier = Modifier.testTag(BusyTag),
                    )
                    HbButton(
                        text = "Сохранить",
                        onClick = {},
                        modifier = Modifier.testTag(IdleTag),
                    )
                }
            }
        }

        waitForIdle()
        val busyBounds = onNodeWithTag(BusyTag).getUnclippedBoundsInRoot()
        val idleBounds = onNodeWithTag(IdleTag).getUnclippedBoundsInRoot()

        assertTrue(
            idleBounds.right - idleBounds.left == busyBounds.right - busyBounds.left,
            "The reserved busy slot must not change the button width",
        )
    }

    @Test
    fun busyButtonAnnouncesBusyStateWithoutALabel() = runComposeUiTest {
        setContent {
            HbTheme {
                HbButton(
                    text = "Сохранить",
                    onClick = {},
                    busy = true,
                    modifier = Modifier.testTag(BusyTag),
                )
            }
        }

        onNodeWithTag(BusyTag).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
        val description = onNodeWithTag(BusyTag)
            .fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription]
        assertTrue(description.isNotBlank(), "The busy state must have a non-empty description")
    }

    @Test
    fun busyButtonAnnouncesTheProvidedLabel() = runComposeUiTest {
        setContent {
            HbTheme {
                HbButton(
                    text = "Удаление…",
                    onClick = {},
                    busy = true,
                    busyLabel = "Идёт удаление",
                    modifier = Modifier.testTag(BusyTag),
                )
            }
        }

        assertEquals(
            "Идёт удаление",
            onNodeWithTag(BusyTag).fetchSemanticsNode().config[SemanticsProperties.StateDescription],
        )
    }

    @Test
    fun radioOptionKeepsTheMinimumTarget() = runComposeUiTest {
        setContent {
            HbTheme {
                HbRadioGroup(
                    options = listOf("Точно", "Быстро"),
                    selected = "Точно",
                    onSelect = {},
                    optionLabel = { it },
                )
            }
        }

        val bounds = onNodeWithText("Точно").getUnclippedBoundsInRoot()
        val height = bounds.bottom - bounds.top
        assertTrue(height >= 32.dp, "A radio row is $height high, below the dense minimum target")
    }

    @Test
    fun indeterminateCheckboxTurnsOn() = runComposeUiTest {
        var reported: Boolean? = null
        setContent {
            HbTheme {
                HbCheckbox(
                    label = "Смешанное",
                    checked = false,
                    indeterminate = true,
                    onCheckedChange = { reported = it },
                )
            }
        }

        onNodeWithText("Смешанное").performClick()

        assertEquals(true, reported, "The first click of an indeterminate checkbox must turn it on")
    }

    @Test
    fun tabsMoveFromTheFocusedTabInsteadOfTheSelectedOne() = runComposeUiTest {
        var selected by mutableStateOf("История")
        setContent {
            HbTheme {
                HbTabs(
                    tabs = listOf("История", "Настройки", "Модели"),
                    selected = selected,
                    onSelect = { selected = it },
                    tabLabel = { it },
                )
            }
        }

        onNodeWithText("Настройки").requestFocus()
        onNodeWithText("Настройки").performKeyInput { pressKey(Key.DirectionRight) }

        onNodeWithText("Модели").assertIsSelected()
    }

    @Test
    fun tabsMirrorTheArrowDirectionInRtl() = runComposeUiTest {
        var selected by mutableStateOf("Модели")
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HbTheme {
                    HbTabs(
                        tabs = listOf("История", "Настройки", "Модели"),
                        selected = selected,
                        onSelect = { selected = it },
                        tabLabel = { it },
                    )
                }
            }
        }

        onNodeWithText("Настройки").requestFocus()
        onNodeWithText("Настройки").performKeyInput { pressKey(Key.DirectionRight) }

        onNodeWithText("История").assertIsSelected()
    }

    @Test
    fun readOnlyFieldKeepsTheValueAndRejectsInput() = runComposeUiTest {
        var changes = 0
        setContent {
            HbTheme {
                HbTextField(
                    value = "project-2026-11",
                    onValueChange = { changes++ },
                    readOnly = true,
                    modifier = Modifier.testTag(FieldTag),
                )
            }
        }

        val config = onNodeWithText("project-2026-11").fetchSemanticsNode().config

        assertFalse(config[SemanticsProperties.IsEditable], "A read-only field must not be editable")
        assertFalse(
            config.contains(SemanticsProperties.Disabled),
            "A read-only field is not a disabled field",
        )
        assertEquals(0, changes)
    }

    @Test
    fun treeRowSelectsAndExpandsIndependently() = runComposeUiTest {
        var clicks = 0
        var expanded = false
        setContent {
            HbTheme {
                HbTreeItem(
                    label = "components",
                    depth = 1,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onClick = { clicks++ },
                    expandLabel = "Expand",
                    collapseLabel = "Collapse",
                )
            }
        }

        onNodeWithText("components").performClick()
        assertEquals(1, clicks)
        assertFalse(expanded, "Selecting the row must not expand the branch")

        onNodeWithContentDescription("Expand").performClick()
        assertTrue(expanded, "The chevron must expand the branch")
        assertEquals(1, clicks, "Expanding the branch must not activate the row")
    }

    @Test
    fun treeItemAnnouncesItsDepth() = runComposeUiTest {
        setContent {
            HbTheme {
                HbTreeItem(
                    label = "foundation",
                    depth = 2,
                    modifier = Modifier.testTag(TreeNodeTag),
                )
            }
        }

        val description = onNodeWithTag(TreeNodeTag)
            .fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription]
        assertTrue(description.isNotBlank(), "The tree level must reach the accessible state")
    }

    @Test
    fun tableKeepsCellCoordinatesWithARowAction() = runComposeUiTest {
        var clicked = -1
        setContent {
            HbTheme {
                HbTable(
                    columns = listOf("Файл", "Статус"),
                    rows = listOf(listOf("HbButton.kt", "Изменён")),
                    onRowClick = { clicked = it },
                )
            }
        }

        val cells = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionItemInfo))
            .fetchSemanticsNodes()
        assertEquals(4, cells.size, "Header and row cells must keep their collection coordinates")
        onNodeWithText("HbButton.kt").performClick()
        assertEquals(0, clicked)
    }

    @Test
    fun avatarGrowsWithTheTextScale() = runComposeUiTest {
        setContent {
            HbTheme(textScale = 2f) {
                HbAvatar(initials = "HB", modifier = Modifier.testTag(AvatarTag))
            }
        }

        val bounds = onNodeWithTag(AvatarTag).getUnclippedBoundsInRoot()
        assertTrue(
            bounds.bottom - bounds.top == 48.dp,
            "The avatar geometry must follow the text scale",
        )
    }

    @Test
    fun kitKeepsTheMinimumTargetInEveryDensity() = runComposeUiTest {
        var density by mutableStateOf(HbDensity.Dense)
        setContent {
            HbTheme(density = density) {
                Column {
                    HbCheckbox(label = "Согласен", checked = true, onCheckedChange = {})
                    HbSwitch(label = "Автозапуск", checked = true, onCheckedChange = {})
                    HbRadioGroup(
                        options = listOf("Точно", "Быстро"),
                        selected = "Точно",
                        onSelect = {},
                        optionLabel = { it },
                    )
                    HbTabs(
                        tabs = listOf("История", "Настройки"),
                        selected = "История",
                        onSelect = {},
                        tabLabel = { it },
                    )
                }
            }
        }

        mapOf(
            HbDensity.Dense to 32.dp,
            HbDensity.Comfortable to 40.dp,
            HbDensity.Touch to 48.dp,
        ).forEach { (configured, expected) ->
            density = configured
            waitForIdle()
            listOf("Согласен", "Автозапуск", "Точно", "История").forEach { label ->
                val bounds = onNodeWithText(label).getUnclippedBoundsInRoot()
                val height = bounds.bottom - bounds.top
                assertTrue(
                    height >= expected,
                    "$label is $height high in $configured, below the $expected minimum target",
                )
            }
        }
    }

    @Test
    fun focusRingIsDrawnOutsideTheInvalidField() = runComposeUiTest {
        var focusColor = Color.Unspecified
        setContent {
            HbTheme {
                focusColor = HbTheme.colors.focus
                HbTextField(
                    value = "",
                    onValueChange = {},
                    errorMessage = "Проверьте адрес",
                    modifier = Modifier.padding(24.dp),
                )
            }
        }

        val field = onNode(hasSetTextAction())
        field.requestFocus()
        waitForIdle()

        val node = field.fetchSemanticsNode()
        val bounds = node.boundsInRoot
        val pixels = onRoot().captureToImage().toPixelMap()
        val centerY = ((bounds.top + bounds.bottom) / 2).toInt()
        val left = bounds.left.toInt()
        // The semantics node describes the text area; the outline sits outside the control edge,
        // so the band starts at the text area and covers the control padding and the ring.
        val ringVisible = (1..RingSearch).any { offset ->
            colorDistance(pixels[left - offset, centerY], focusColor) < RingColorTolerance
        }

        assertTrue(
            ringVisible,
            "The focus ring must be painted outside the field bounds, over the error border",
        )
    }

    @Test
    fun idleButtonKeepsItsLabelCenteredWithoutABusySlot() = runComposeUiTest {
        setContent {
            HbTheme {
                HbButton(
                    text = "Сохранить",
                    onClick = {},
                    modifier = Modifier.testTag(IdleTag),
                )
            }
        }

        val button = onNodeWithTag(IdleTag).getUnclippedBoundsInRoot()
        val label = onNodeWithText("Сохранить", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val buttonCenter = (button.left.value + button.right.value) / 2f
        val labelCenter = (label.left.value + label.right.value) / 2f

        assertTrue(
            abs(buttonCenter - labelCenter) <= 1f,
            "The idle label must stay centered; button center $buttonCenter, label center $labelCenter",
        )
    }

    @Test
    fun settingsRowGivesItsTitleToTheNestedSwitch() = runComposeUiTest {
        var checked by mutableStateOf(true)
        setContent {
            HbTheme {
                HbSettingsRow(
                    title = "Автозапуск",
                    control = { controlModifier ->
                        HbSwitch(
                            label = "",
                            checked = checked,
                            onCheckedChange = { checked = it },
                            modifier = controlModifier,
                        )
                    },
                )
            }
        }

        onNodeWithContentDescription("Автозапуск").assertIsOn().performClick()

        assertFalse(checked, "The switch named by the settings row must toggle")
    }

    @Test
    fun clickableTableRowKeepsTheMinimumTargetAndFocus() = runComposeUiTest {
        setContent {
            HbTheme(density = HbDensity.Touch) {
                HbTable(
                    columns = listOf("Файл", "Статус"),
                    rows = listOf(listOf("HbButton.kt", "Изменён")),
                    onRowClick = {},
                )
            }
        }

        val row = onNode(hasClickAction()).getUnclippedBoundsInRoot()
        assertTrue(
            row.bottom - row.top >= 48.dp,
            "A clickable table row must keep the touch minimum target",
        )

        onNode(hasClickAction()).requestFocus()
        onNode(hasClickAction()).assertIsFocused()
    }

    @Test
    fun shortLinkKeepsTheMinimumTargetOnBothAxes() = runComposeUiTest {
        setContent {
            HbTheme(density = HbDensity.Touch) {
                HbLink(
                    text = "OK",
                    onClick = {},
                    modifier = Modifier.testTag(LinkTag),
                )
            }
        }

        val bounds = onNodeWithTag(LinkTag).getUnclippedBoundsInRoot()
        assertTrue(bounds.right - bounds.left >= 48.dp, "A short link is too narrow for touch")
        assertTrue(bounds.bottom - bounds.top >= 48.dp, "A short link is too short for touch")
    }

    @Test
    fun selectedTreeItemIsAnnouncedAndMarked() = runComposeUiTest {
        var marker = Color.Unspecified
        setContent {
            HbTheme {
                marker = HbTheme.colors.selection.indicator
                HbTreeItem(
                    label = "components",
                    depth = 0,
                    selected = true,
                    onClick = {},
                    modifier = Modifier.testTag(TreeNodeTag),
                )
            }
        }

        onNodeWithTag(TreeNodeTag).assertIsSelected()

        val pixels = onNodeWithTag(TreeNodeTag).captureToImage().toPixelMap()
        val marked = (0 until 3).any { x ->
            (0 until pixels.height).any { y ->
                colorDistance(pixels[x, y], marker) < RingColorTolerance
            }
        }
        assertTrue(marked, "A selected tree item must draw a permanent non-color-only marker")
    }

    @Test
    fun unselectedTreeItemAnnouncesTheExplicitFalseState() = runComposeUiTest {
        setContent {
            HbTheme {
                HbTreeItem(
                    label = "foundation",
                    depth = 0,
                    selected = false,
                    onClick = {},
                    modifier = Modifier.testTag(TreeNodeTag),
                )
            }
        }

        onNodeWithTag(TreeNodeTag).assertIsNotSelected()
    }

    @Test
    fun shortBreadcrumbKeepsTheMinimumTargetOnBothAxes() = runComposeUiTest {
        setContent {
            HbTheme(density = HbDensity.Touch) {
                HbBreadcrumbs(
                    items = listOf("A", "B"),
                    onNavigate = {},
                    modifier = Modifier.testTag(BreadcrumbTag),
                )
            }
        }

        val link = onNodeWithText("A").getUnclippedBoundsInRoot()
        assertTrue(link.right - link.left >= 48.dp, "A short breadcrumb is too narrow for touch")
        assertTrue(link.bottom - link.top >= 48.dp, "A short breadcrumb is too short for touch")
    }

    @Test
    fun disabledFieldPassesTheDisabledStateToTrailing() = runComposeUiTest {
        var trailingEnabled: Boolean? = null
        setContent {
            HbTheme {
                HbTextField(
                    value = "",
                    onValueChange = {},
                    label = "Поле",
                    enabled = false,
                    trailing = { fieldEnabled -> trailingEnabled = fieldEnabled },
                )
            }
        }

        assertEquals(false, trailingEnabled)
    }

    @Test
    fun disabledSelectKeepsItsDisabledBorderAndDoesNotOpen() = runComposeUiTest {
        var border = Color.Unspecified
        setContent {
            HbTheme {
                border = HbTheme.colors.border.subtle
                HbSelect(
                    options = listOf("Альфа"),
                    selected = null,
                    onSelect = {},
                    label = "Модель",
                    enabled = false,
                    optionLabel = { it },
                    modifier = Modifier.testTag(SelectTag),
                )
            }
        }

        onNodeWithContentDescription("Модель").assertIsNotEnabled().performClick()
        onNodeWithText("Альфа").assertDoesNotExist()

        val pixels = onNodeWithTag(SelectTag).captureToImage().toPixelMap()
        val middleY = pixels.height / 2
        val marked = (0 until 3).any { x ->
            colorDistance(pixels[x, middleY], border) < RingColorTolerance
        }
        assertTrue(marked, "The disabled select must use the subtle border role")
    }

    private fun colorDistance(first: Color, second: Color): Float =
        maxOf(
            abs(first.red - second.red),
            abs(first.green - second.green),
            abs(first.blue - second.blue),
        )

    private companion object {
        private const val BusyTag = "busy"
        private const val IdleTag = "idle"
        private const val FieldTag = "field"
        private const val TreeNodeTag = "tree-node"
        private const val AvatarTag = "avatar"
        private const val LinkTag = "link"
        private const val SelectTag = "select"
        private const val BreadcrumbTag = "breadcrumb"
        private const val RingSearch = 24
        private const val RingColorTolerance = 0.05f
    }
}

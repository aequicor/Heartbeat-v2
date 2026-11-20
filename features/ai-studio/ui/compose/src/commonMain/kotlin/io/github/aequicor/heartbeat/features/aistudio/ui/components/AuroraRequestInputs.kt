@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Request

@Composable
internal fun AuroraRequestNavigation(panel: Request.Questions, touch: Boolean, onAction: (AiStudioAction) -> Unit) {
    val skip = { onAction(AiStudioAction.SkipQuestion(panel.id)) }
    if (panel.questions.size == 1) {
        AuroraRequestNavButton("Пропустить", touch, onClick = skip)
        return
    }
    Row(Modifier.width(AuroraLayout.RequestPagerWidth), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            AuroraRequestNavButton("Назад", touch, enabled = panel.index > 0) {
                onAction(AiStudioAction.MoveQuestion(panel.id, -1))
            }
        }
        Box(Modifier.width(AuroraLayout.TouchTarget), contentAlignment = Alignment.Center) {
            AuroraRequestLabel("${panel.index + 1} из ${panel.questions.size}")
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (!panel.question.required) AuroraRequestNavButton("Пропустить", touch, onClick = skip)
        }
    }
}

@Composable
private fun AuroraRequestNavButton(label: String, touch: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    AuroraControl(
        onClick, touch = touch, enabled = enabled, background = Color.Transparent, focus = AuroraColors.Amber,
    ) {
        val color = if (enabled) AuroraColors.RequestText else AuroraColors.RequestSecondary.copy(alpha = 0.72f)
        AuroraRequestLabel(label, color = color)
    }
}

@Composable
internal fun AuroraQuestionOptions(
    panel: Request.Questions,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val question = panel.question
    val choose = { option: String -> onAction(AiStudioAction.Choose(panel.id, question.id, option)) }
    val radio = !question.multiple
    val requesters = remember(question.id, question.options) { List(question.options.size) { FocusRequester() } }
    val group = if (radio) Modifier.selectableGroup() else Modifier
    Column(
        modifier.then(group).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap),
    ) {
        question.options.forEachIndexed { index, option ->
            val arrows = if (radio) Modifier.focusRequester(requesters[index]).onPreviewKeyEvent { event ->
                val step = when (event.key) {
                    Key.DirectionUp, Key.DirectionLeft -> -1
                    Key.DirectionDown, Key.DirectionRight -> 1
                    else -> 0
                }
                if (step == 0 || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val next = (index + step + requesters.size) % requesters.size
                requesters[next].requestFocus()
                choose(question.options[next])
                true
            } else Modifier
            AuroraQuestionOption(option, option in panel.answer.options, radio, touch, { choose(option) }, arrows)
        }
    }
}

@Composable
private fun AuroraQuestionOption(
    label: String,
    selected: Boolean,
    radio: Boolean,
    touch: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(AuroraLayout.SmallRadius)
    val background = when {
        selected -> AuroraColors.RequestSelected
        hovered -> AuroraColors.RequestField
        else -> Color.Transparent
    }
    val indication = if (focused && HbFocusIndication.keyboardNavigation) {
        Modifier.border(AuroraLayout.FrameStroke, AuroraColors.Amber, shape)
    } else Modifier
    val control = if (radio) {
        Modifier.selectable(selected, interaction, indication = null, role = Role.RadioButton, onClick = onClick)
    } else {
        Modifier.toggleable(
            selected, interaction, indication = null, role = Role.Checkbox, onValueChange = { onClick() },
        )
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = auroraRequestTarget(touch, compact = !touch)).clip(shape)
            .background(background).then(indication).then(control)
            .padding(
                horizontal = if (touch) AuroraLayout.ContentInset else AuroraLayout.Space,
                vertical = if (touch) AuroraLayout.Space else AuroraLayout.SmallGap,
            ),
        horizontalArrangement = Arrangement.spacedBy(if (touch) AuroraLayout.ChromeInset else AuroraLayout.Space),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuroraChoiceMark(selected, radio)
        AuroraRequestLabel(label)
    }
}

@Composable
private fun AuroraChoiceMark(selected: Boolean, radio: Boolean) {
    val outline = if (selected) AuroraColors.Amber else AuroraColors.RequestSecondary
    val shape = if (radio) CircleShape else RoundedCornerShape(AuroraLayout.TimerThickness)
    Box(
        Modifier.size(AuroraLayout.Glyph).border(AuroraLayout.RequestChoiceStroke, outline, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            if (radio) Box(Modifier.size(AuroraLayout.SmallGap).background(outline, CircleShape))
            else HbIcon(HbIconGlyph.Check, size = AuroraLayout.SmallGlyph, tint = outline)
        }
    }
}

@Composable
internal fun AuroraQuestionActions(panel: Request.Questions, touch: Boolean, onAction: (AiStudioAction) -> Unit) {
    val next = if (panel.last) AiStudioAction.SubmitAnswers(panel.id) else AiStudioAction.MoveQuestion(panel.id, 1)
    val label = if (panel.last) "Отправить" else "Далее"
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < AuroraLayout.RequestHorizontalActions || HbTheme.textScale > 1.35f
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AuroraLayout.Space, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(AuroraLayout.Space),
        ) {
            if (panel.question.freeText) {
                AuroraAnswerText(panel, touch, onAction, if (stacked) Modifier.fillMaxWidth() else Modifier.weight(1f))
            }
            AuroraRequestButton(
                label, { onAction(next) }, touch, primary = true, centered = true, compact = !touch,
                modifier = if (stacked) Modifier.fillMaxWidth() else Modifier.width(AuroraLayout.RequestNextWidth),
            )
        }
    }
}

@Composable
internal fun AuroraPermissionActions(
    request: Request.Permission,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < AuroraLayout.RequestHorizontalActions || HbTheme.textScale > 1.35f
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AuroraLayout.Space),
            verticalArrangement = Arrangement.spacedBy(AuroraLayout.Space),
        ) {
            val button = if (stacked) Modifier.fillMaxWidth() else Modifier
            AuroraRequestButton("Разрешить один раз", { onAction(AiStudioAction.AnswerPermission(request.id, true)) },
                touch, button, primary = true, centered = stacked)
            AuroraRequestButton("Отклонить", { onAction(AiStudioAction.AnswerPermission(request.id, false)) },
                touch, button, centered = stacked)
        }
    }
}

@Composable
private fun AuroraAnswerText(
    panel: Request.Questions,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(panel.id, panel.question.id) { mutableStateOf(panel.answer.text) }
    SideEffect(panel.id, panel.question.id, panel.answer.skipped) {
        if (panel.answer.skipped) draft = ""
    }
    var focused by remember { mutableStateOf(false) }
    val hint = if (panel.answer.options.isEmpty()) "Свой ответ…" else "Комментарий…"
    val shape = RoundedCornerShape(AuroraLayout.SmallRadius)
    val focusVisible = focused && HbFocusIndication.keyboardNavigation
    val style = auroraMessageStyle()
    BasicTextField(
        draft,
        { text ->
            draft = text
            onAction(AiStudioAction.EditAnswer(panel.id, panel.question.id, text))
        },
        modifier.heightIn(min = auroraRequestTarget(touch, compact = !touch))
            .background(AuroraColors.RequestField, shape)
            .border(
                if (focusVisible) AuroraLayout.FrameStroke else AuroraLayout.Stroke,
                if (focusVisible) AuroraColors.Amber else AuroraColors.RequestBorder, shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .padding(
                horizontal = if (touch) AuroraLayout.ContentInset else AuroraLayout.Space,
                vertical = if (touch) AuroraLayout.Space else AuroraLayout.SmallGap,
            )
            .semantics { contentDescription = hint },
        textStyle = style.copy(color = AuroraColors.RequestText),
        cursorBrush = SolidColor(AuroraColors.Amber),
        maxLines = 4,
        decorationBox = { field ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (draft.isEmpty()) {
                    BasicText(hint, style = style.copy(color = AuroraColors.RequestSecondary))
                }
                field()
            }
        },
    )
}

@Composable
private fun AuroraRequestButton(
    label: String,
    onClick: () -> Unit,
    touch: Boolean,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    centered: Boolean = false,
    compact: Boolean = false,
) {
    AuroraControl(
        onClick, modifier.heightIn(min = auroraRequestTarget(touch, compact)), touch = touch, round = false,
        background = if (primary) AuroraColors.Amber else AuroraColors.RequestField,
        focus = if (primary) AuroraColors.RequestOnAccent else AuroraColors.Amber,
        horizontalPadding = AuroraLayout.ContentInset,
    ) {
        val verticalPadding = if (compact) AuroraLayout.SmallGap else AuroraLayout.Space
        Box(
            Modifier.weight(1f, fill = centered).padding(vertical = verticalPadding),
            contentAlignment = Alignment.Center,
        ) {
            val color = if (primary) AuroraColors.RequestOnAccent else AuroraColors.RequestText
            AuroraRequestLabel(label, color = color)
        }
    }
}

@Composable
internal fun AuroraRequestLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AuroraColors.RequestText,
) {
    AuroraText(text, modifier, color = color)
}

private fun auroraRequestTarget(touch: Boolean, compact: Boolean) = when {
    touch -> AuroraLayout.TouchTarget
    compact -> AuroraLayout.DesktopTarget
    else -> AuroraLayout.RequestActionHeight
}

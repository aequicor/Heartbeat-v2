package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbProgress
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction.EnginePicker
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ChatEnginePickerState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ChatEnginePickerState.Choice
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ChatEnginePickerState.Selection
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ChatEnginePickerState.Step
import kotlinx.coroutines.delay

@Composable
internal fun AuroraChatEngineParameters(
    picker: ChatEnginePickerState?,
    configuration: Selection?,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = picker?.confirmed ?: configuration
    var opener by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(picker?.step) {
        if (picker?.step == null) {
            opener?.requestFocus()
            opener = null
        }
    }
    Row(
        modifier.then(if (touch) Modifier else Modifier.horizontalScroll(rememberScrollState()))
            .auroraFloatingHeaderSurface(Colors.Pearl, Colors.HeaderOutline)
            .padding(horizontal = Layout.Space),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (step in Step.entries) key(step) {
            val value = when (step) {
                Step.Engine -> current?.engine?.name
                Step.Model -> current?.model?.name
                Step.Reasoning -> current?.level?.name
            }
            val label = value ?: step.label
            if (picker == null) {
                if (value != null) AuroraHeaderText(value, Modifier.padding(Layout.Space))
            } else {
                val focus = remember { FocusRequester() }
                val anchor = remember { BringIntoViewRequester() }
                val active = picker.step == step
                Box(Modifier.bringIntoViewRequester(anchor)) {
                    AuroraHeaderButton(
                        "${step.label}, $label", {
                            opener = focus
                            onAction(EnginePicker.Open(step))
                        }, Modifier.focusRequester(focus),
                        height = if (touch) Layout.TouchTarget else Layout.DesktopTarget,
                        contentPadding = PaddingValues(horizontal = Layout.Space),
                    ) {
                        AuroraHeaderText(label, Modifier.widthIn(max = Layout.ParameterMaxWidth),
                            color = if (active) Colors.Primary else Colors.Ink)
                        HbIcon(HbIconGlyph.ChevronDown, tint = Colors.Muted, size = Layout.SmallGlyph)
                    }
                    if (active) {
                        LaunchedEffect(Unit) { anchor.bringIntoView() }
                        EngineMenu(picker, step, touch, onAction)
                    }
                }
            }
        }
    }
}

private val Step.label: String get() = when (this) {
    Step.Engine -> "Движок"
    Step.Model -> "Модель"
    Step.Reasoning -> "Уровень рассуждения"
}

private object EngineMenuPosition : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
        anchorBounds.bottom.coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0)),
    )
}

@Composable
private fun EngineMenu(
    state: ChatEnginePickerState,
    step: Step,
    touch: Boolean,
    onAction: (EnginePicker) -> Unit,
) {
    Popup(
        popupPositionProvider = EngineMenuPosition,
        onDismissRequest = { onAction(EnginePicker.Dismiss) },
        properties = PopupProperties(focusable = true, dismissOnBackPress = false),
    ) {
        EnginePickerBackHandler { onAction(EnginePicker.Back) }
        val focus = LocalFocusManager.current
        val back = remember { FocusRequester() }
        LaunchedEffect(step) { back.requestFocus() }
        val target = if (touch) Layout.TouchTarget else Layout.EngineMenuRow
        Column(
            Modifier.width(Layout.EngineMenuWidth).heightIn(max = Layout.EngineMenuMaxHeight)
                .auroraFloatingHeaderSurface(Colors.Pearl, Colors.HeaderOutline)
                .onPreviewKeyEvent {
                    if (it.type != KeyEventType.KeyDown) false
                    else when (it.key) {
                        Key.DirectionDown -> focus.moveFocus(FocusDirection.Next)
                        Key.DirectionUp -> focus.moveFocus(FocusDirection.Previous)
                        Key.Escape -> { onAction(EnginePicker.Dismiss); true }
                        Key.Back -> { onAction(EnginePicker.Back); true }
                        else -> false
                    }
                }.padding(horizontal = Layout.ControlGap),
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = Layout.EngineMenuHeader)) {
                AuroraHeaderButton("Назад", { onAction(EnginePicker.Back) },
                    Modifier.align(Alignment.CenterStart).focusRequester(back), height = target) {
                    AuroraHeaderText("←", Modifier.padding(horizontal = Layout.Space))
                }
                Column(Modifier.align(Alignment.Center).padding(horizontal = Layout.TouchTarget), horizontalAlignment = Alignment.CenterHorizontally) {
                    AuroraHeaderText(step.label)
                    val subtitle = when (step) {
                        Step.Engine -> state.confirmed?.engine?.name
                        Step.Model -> state.engine?.name
                        Step.Reasoning -> listOfNotNull(state.engine?.name, state.model?.name).joinToString(" · ")
                    }
                    if (!subtitle.isNullOrEmpty()) AuroraHeaderText(subtitle, style = HbTypography.Caption)
                }
                AuroraHeaderText("${step.ordinal + 1} из 3", Modifier.align(Alignment.CenterEnd), style = HbTypography.Caption)
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                when {
                    step == Step.Engine -> {
                        if (state.engines.isEmpty()) MenuHint("Нет настроенных движков")
                        MenuChoices(state.engines, state.engine ?: state.confirmed?.engine, target) { onAction(EnginePicker.Engine(it)) }
                    }
                    state.loading -> LoadingHint(step)
                    state.failed -> {
                        MenuHint(if (step == Step.Model) "Не удалось загрузить модели" else "Не удалось применить настройки")
                        MenuAction("Повторить", target) { onAction(EnginePicker.Retry) }
                    }
                    step == Step.Model -> {
                        if (state.models.isNullOrEmpty()) {
                            MenuHint("У этого движка нет доступных моделей")
                            MenuAction("Выбрать другой движок", target) { onAction(EnginePicker.Back) }
                        } else MenuChoices(state.models.orEmpty(), state.model, target) { onAction(EnginePicker.Model(it)) }
                    }
                    else -> {
                        when {
                            state.levels == null -> MenuHint("Модель не поддерживает выбор уровня рассуждения")
                            state.levels.isNullOrEmpty() -> MenuHint("Нет доступных уровней рассуждения")
                            else -> MenuChoices(state.levels.orEmpty(), state.level, target) { onAction(EnginePicker.Reasoning(it)) }
                        }
                        MenuAction("Применить", target, state.canApply) { onAction(EnginePicker.Apply) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuChoices(choices: List<Choice>, selected: Choice?, height: Dp, onSelect: (String) -> Unit) {
    for (choice in choices) key(choice.id) {
        AuroraHeaderButton(choice.name, { onSelect(choice.id) }, Modifier.fillMaxWidth(), height = height,
            contentPadding = PaddingValues(horizontal = Layout.ContentInset)) {
            AuroraHeaderText(choice.name, Modifier.weight(1f))
            if (selected?.id == choice.id) HbIcon(HbIconGlyph.Check, tint = Colors.Primary, size = Layout.SmallGlyph)
        }
    }
}

@Composable
private fun MenuAction(label: String, height: Dp, enabled: Boolean = true, onClick: () -> Unit) {
    AuroraHeaderButton(label, onClick, Modifier.fillMaxWidth(), height = height, enabled = enabled,
        contentPadding = PaddingValues(horizontal = Layout.ContentInset)) {
        AuroraHeaderText(label, color = if (enabled) Colors.Primary else Colors.Muted)
    }
}

@Composable
private fun MenuHint(text: String) {
    BasicText(text, Modifier.padding(Layout.ContentInset), style = HbTypography.Label.copy(color = Colors.Muted))
}

@Composable
private fun LoadingHint(step: Step) {
    var delayed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(400); delayed = true }
    MenuHint(if (step == Step.Model) "Загружаем модели…" else "Обновляем настройки…")
    if (delayed) HbProgress(Modifier.padding(horizontal = Layout.ContentInset, vertical = Layout.Space))
}

@Composable
internal expect fun EnginePickerBackHandler(onBack: () -> Unit)

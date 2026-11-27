package io.github.aequicor.heartbeat.features.aistudio.ui.components

import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraImageEditor as Editor
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing as Spacing
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.EditedImageCopyDraft

@Composable
internal fun AuroraEditedImageCopyDraft(
    draft: EditedImageCopyDraft,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var explain by remember(draft.file.id) { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(Layout.SmallRadius)
    val explanation = if (draft.databaseBusy) {
        "База данных занята. Через несколько секунд нажмите «Повторить»."
    } else {
        "Не удалось записать копию в базу данных. Повторите попытку или сохраните файл локально."
    }
    val footerLabel = HbTypography.Label.copy(
        color = Colors.FileIssueInk, fontSize = Editor.FormMetadata, fontWeight = FontWeight.Medium,
    )
    Column(modifier.fillMaxWidth().padding(bottom = Layout.Space)) {
        AuroraFileLabel(
            draft.file,
            Modifier.fillMaxWidth().border(Layout.Stroke, Colors.FileIssueInk, cardShape).padding(Layout.Space),
            attachmentTile = true,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Layout.ContentInset)
                .background(Editor.FailureFooterSurface, RoundedCornerShape(bottomStart = Layout.SmallRadius, bottomEnd = Layout.SmallRadius)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Xxs),
        ) {
            Box {
                AuroraControl(
                    { explain = !explain }, label = "Ошибка сохранения · подробнее",
                    background = Editor.FailureFooterSurface, horizontalPadding = Layout.ControlGap, round = false,
                ) {
                    BasicText("Ошибка  ⓘ", style = footerLabel)
                }
                if (explain) {
                    val offset = with(LocalDensity.current) { IntOffset(0, Editor.FailureHintOffset.roundToPx()) }
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = offset,
                        onDismissRequest = { explain = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        val surface = Editor.FailureHintSurface
                        Column(
                            Modifier.widthIn(max = Editor.FailureHintWidth).background(surface, RoundedCornerShape(Layout.SmallRadius)).padding(Layout.ContentInset),
                            verticalArrangement = Arrangement.spacedBy(Layout.SmallGap),
                        ) {
                            AuroraText(explanation, color = Colors.DesktopOnPrimaryAction, small = true)
                            AuroraControl(
                                {
                                    explain = false
                                    onDownload()
                                },
                                label = "Сохранить файл на устройство", background = surface,
                                horizontalPadding = Spacing.None, round = false,
                            ) {
                                AuroraText("Сохранить файл на устройство", color = Colors.DesktopOnPrimaryAction, small = true)
                            }
                        }
                    }
                }
            }
            AuroraControl(onDownload, label = "Сохранить файл на устройство",
                background = Editor.FailureFooterSurface, horizontalPadding = Layout.ControlGap, round = false) {
                BasicText("Сохранить", style = footerLabel.copy(textDecoration = TextDecoration.Underline))
            }
            Spacer(Modifier.weight(1f))
            AuroraControl(onRetry, label = "Повторить",
                background = Editor.FailureFooterSurface, horizontalPadding = Layout.ControlGap, round = false) {
                BasicText("Повторить", style = footerLabel.copy(textDecoration = TextDecoration.Underline))
            }
        }
    }
}

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.components.HbAppName
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraImageEditor
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.designsystem.theme.HbWindowCaption
import io.github.aequicor.heartbeat.navigation.compose.ComposeWindowOverlay

@Composable
internal fun AuroraAttachmentLayer(onClose: () -> Unit, content: @Composable () -> Unit) {
    ComposeWindowOverlay {
        AttachmentOverlayBackHandler(onClose)
        val caption = HbWindowCaption.current
        Column(Modifier.fillMaxSize()) {
            if (caption.height > Dp.Hairline) {
                Box(
                    Modifier.fillMaxWidth().height(caption.height).background(HbAuroraColors.ViewerToolbar),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(HbAppName, style = HbTypography.Metadata.copy(color = HbAuroraColors.DesktopWorkMuted))
                }
            }
            Box(
                Modifier.fillMaxWidth().weight(1f).background(HbAuroraColors.ViewerSurface)
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
            ) { content() }
        }
    }
}

@Composable
internal fun AuroraAttachmentModal(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val dismiss = rememberUpdatedState(onDismiss)
    ComposeWindowOverlay {
        AttachmentOverlayBackHandler(onDismiss)
        val focus = remember { FocusRequester() }
        LaunchedEffect(focus) { focus.requestFocus() }
        Box(
            Modifier.fillMaxSize().padding(top = HbWindowCaption.current.height)
                .background(HbAuroraImageEditor.Scrim)
                .focusRequester(focus).onPreviewKeyEvent {
                    if (it.key != Key.Escape || it.type != KeyEventType.KeyDown) false
                    else {
                        dismiss.value()
                        true
                    }
                }.focusable()
                .pointerInput(Unit) { detectTapGestures { dismiss.value() } },
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
internal expect fun AttachmentOverlayBackHandler(onClose: () -> Unit)

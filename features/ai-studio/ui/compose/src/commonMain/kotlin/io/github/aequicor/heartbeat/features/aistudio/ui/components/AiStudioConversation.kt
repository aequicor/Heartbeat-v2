package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbIconButton
import io.github.aequicor.heartbeat.designsystem.components.HbScrollArea
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.foundation.HbPaneGeometry
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/** Conversation of the studio: model introduction, one question and one answer. */
@Composable
internal fun AiStudioTranscript(modifier: Modifier = Modifier) {
    HbScrollArea(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = HbPaneGeometry.ReadingMax.dp)
                    .fillMaxWidth()
                    .padding(horizontal = HbSpacing.Xl, vertical = HbSpacing.L),
                verticalArrangement = Arrangement.spacedBy(HbSpacing.L),
            ) {
                AiStudioHero()
                AiStudioUserMessage()
                AiStudioAssistantMessage()
            }
        }
    }
}

@Composable
private fun AiStudioHero(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        AiStudioBrandMark(
            modifier = Modifier
                .width(heroMarkWidth)
                .height(HbSpacing.Xl),
        )
        BasicText(
            text = "Sonnet 3.7 Craft",
            style = HbTypography.PageTitle,
            color = { colors.text.primary },
        )
        BasicText(
            text = "Мощная модель для продуманной работы: анализ, дизайн, " +
                "написание и сложное продуктовое мышление.",
            style = HbTypography.Body.copy(textAlign = TextAlign.Center),
            color = { colors.text.secondary },
            modifier = Modifier.widthIn(max = heroTextMaxWidth),
        )
        AiStudioPulseDivider()
    }
}

@Composable
private fun AiStudioUserMessage(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        AiStudioUserBadge()
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(HbShapes.Large)
                .background(colors.action.primaryBackground)
                .padding(HbSpacing.M),
        ) {
            BasicText(
                text = UserMessage,
                style = HbTypography.Body,
                color = { colors.action.primaryForeground },
            )
        }
    }
}

@Composable
private fun AiStudioAssistantMessage(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        AiStudioAssistantBadge()
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(HbShapes.Large)
                .background(colors.surface.base)
                .border(AiStudioMetrics.hairline, colors.border.subtle, HbShapes.Large)
                .padding(HbSpacing.M),
            verticalArrangement = Arrangement.spacedBy(HbSpacing.S),
        ) {
            BasicText(
                text = "Концепция: AI-студия для полноценной работы с ИИ",
                style = HbTypography.Title,
                color = { colors.text.primary },
            )
            BasicText(
                text = "Единое, продуманное рабочее пространство для работы с несколькими моделями, " +
                    "агентами и инструментами — с фокусом на продуктивность, гибкость и реальные " +
                    "задачи, а не на эффектность.",
                style = HbTypography.Body,
                color = { colors.text.secondary },
            )
            BasicText(
                text = "Ключевые акценты",
                style = HbTypography.Label.copy(fontWeight = FontWeight.SemiBold),
                color = { colors.text.primary },
            )
            Column(verticalArrangement = Arrangement.spacedBy(HbSpacing.S)) {
                assistantAccents.forEach { accent ->
                    AiStudioAccent(accent = accent)
                }
            }
            AiStudioMessageActions()
        }
    }
}

@Composable
private fun AiStudioAccent(
    accent: AiStudioAccent,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        Box(
            modifier = Modifier
                .padding(top = HbSpacing.S)
                .size(HbSpacing.Xs)
                .clip(CircleShape)
                .background(colors.accent.brand),
        )
        BasicText(
            text = accentText(accent),
            style = HbTypography.Body,
            color = { colors.text.primary },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AiStudioMessageActions(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
    ) {
        HbIconButton(
            contentDescription = "Копировать ответ",
            onClick = {},
        ) {
            AiStudioIcon(
                glyph = AiStudioGlyph.Copy,
                modifier = Modifier.size(HbSizes.iconSize),
                tint = colors.text.secondary,
            )
        }
        HbIconButton(
            contentDescription = "Ответ полезен",
            onClick = {},
        ) {
            AiStudioIcon(
                glyph = AiStudioGlyph.ThumbUp,
                modifier = Modifier.size(HbSizes.iconSize),
                tint = colors.text.secondary,
            )
        }
        HbIconButton(
            contentDescription = "Ответ не полезен",
            onClick = {},
        ) {
            AiStudioIcon(
                glyph = AiStudioGlyph.ThumbUp,
                modifier = Modifier
                    .size(HbSizes.iconSize)
                    .rotate(FlipDegrees),
                tint = colors.text.secondary,
            )
        }
        HbIconButton(
            contentDescription = "Действия с ответом",
            onClick = {},
        ) {
            HbIcon(
                glyph = HbIconGlyph.More,
                size = HbSizes.iconSize,
                tint = colors.text.secondary,
            )
        }
    }
}

@Composable
private fun AiStudioUserBadge(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors

    Box(
        modifier = modifier
            .size(HbSizes.minimumTarget)
            .clip(CircleShape)
            .background(colors.surface.subtle),
        contentAlignment = Alignment.Center,
    ) {
        AiStudioIcon(
            glyph = AiStudioGlyph.User,
            modifier = Modifier.size(HbSizes.iconSize),
            tint = colors.text.secondary,
        )
    }
}

@Composable
private fun AiStudioAssistantBadge(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors

    Box(
        modifier = modifier
            .size(HbSizes.minimumTarget)
            .clip(CircleShape)
            .background(colors.accent.container),
        contentAlignment = Alignment.Center,
    ) {
        AiStudioBrandMark(
            modifier = Modifier
                .width(HbSpacing.M)
                .height(HbSpacing.S),
        )
    }
}

private fun accentText(accent: AiStudioAccent): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
        append(accent.lead)
    }
    append(" ")
    append(accent.body)
}

private data class AiStudioAccent(
    val lead: String,
    val body: String,
)

private const val FlipDegrees = 180f

private val heroMarkWidth = HbSpacing.Xl * 2 + HbSpacing.S

private val heroTextMaxWidth = HbPaneGeometry.ReadingMax.dp - HbSpacing.Xl * 6

private const val UserMessage =
    "Помоги придумать концепцию для AI-студии — современного рабочего пространства с доступом " +
        "к нескольким моделям, агентам и инструментам. На чём стоит сделать акцент, чтобы это " +
        "было по-настоящему полезным и удобным?"

private val assistantAccents = listOf(
    AiStudioAccent(
        lead = "Мульти-модельная работа.",
        body = "Быстрый доступ к разным моделям и удобное сравнение результатов в одном " +
            "пространстве.",
    ),
    AiStudioAccent(
        lead = "Агенты под задачи.",
        body = "Настраиваемые агенты для повторяющихся процессов: исследования, анализ, " +
            "написание, работа с данными и продуктовые задачи.",
    ),
    AiStudioAccent(
        lead = "Расширяемость.",
        body = "Плагины и интеграции с внешними сервисами (файлы, редакторы, данные, API), " +
            "чтобы ИИ органично входил в существующий рабочий процесс.",
    ),
    AiStudioAccent(
        lead = "Чистый и спокойный опыт.",
        body = "Минималистичный, быстрый и понятный интерфейс, который помогает " +
            "сосредоточиться на результате.",
    ),
)

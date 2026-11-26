package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aiengine.api.session.QuestionnaireAnswer.Value
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionQuestionnaire.Field
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionQuestionnaire.Option
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionQuestionnaire.Type
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatQuestionnaire
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Answer
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Question
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Request

internal fun ChatQuestionnaire.toPanel(): Request.Questions = Request.Questions(
    id,
    questions = fields.map(Field::toQuestion).ifEmpty { listOf(Question("", message, required = true)) },
    answers = fields.mapNotNull { field -> field.defaultAnswer()?.let { field.id to it } }.toMap(),
    message = message.takeIf { fields.isNotEmpty() },
    canDecline = true,
)

internal fun Request.Questions.withAnswer(answer: Answer): Request.Questions =
    copy(answers = answers + (question.id to answer), error = null)

private fun Field.toQuestion(): Question {
    val choices = if (type == Type.Boolean) listOf(Option("true", "Да"), Option("false", "Нет")) else options
    return Question(
        id, title, choices.map(Option::value),
        multiple = type == Type.MultiChoice,
        freeText = type == Type.Text || type == Type.Integer || type == Type.Number,
        required = required,
        optionLabels = choices.associate { it.value to it.label },
        description = description,
    )
}

private fun Field.defaultAnswer(): Answer? {
    val answer = when (val value = default) {
        null -> return null
        is Value.Text -> if (type == Type.Choice) Answer(options = setOf(value.value)) else Answer(text = value.value)
        is Value.Choices -> Answer(options = value.values.toSet())
        is Value.Flag -> Answer(options = setOf(value.value.toString()))
        is Value.Integer -> Answer(text = value.value.toString())
        is Value.Number -> Answer(text = value.value.toString())
    }
    return answer.takeIf { readAnswer(it) != null }
}

internal fun Field.readAnswer(answer: Answer): Value? = when (type) {
    Type.Text -> answer.text.takeIf { text ->
        val length = text.codePointCount()
        length >= (minLength ?: 0) &&
            length <= (maxLength ?: Int.MAX_VALUE) &&
            (pattern == null || runCatching { Regex(pattern.orEmpty()).containsMatchIn(text) }.getOrDefault(false))
    }?.let(Value::Text)
    Type.Choice -> answer.options.singleOrNull()?.takeIf { value ->
        options.any { it.value == value }
    }?.let(Value::Text)
    Type.MultiChoice -> answer.options.takeIf { selected ->
        val count = selected.size.toLong()
        count >= (minItems ?: 0L) && count <= (maxItems ?: Long.MAX_VALUE) &&
            selected.all { value -> options.any { it.value == value } }
    }?.toList()?.let(Value::Choices)
    Type.Boolean -> answer.options.singleOrNull()?.toBooleanStrictOrNull()?.let(Value::Flag)
    Type.Integer -> answer.text.trim().toLongOrNull()?.takeIf {
        it >= (minInteger ?: Long.MIN_VALUE) && it <= (maxInteger ?: Long.MAX_VALUE)
    }?.let(Value::Integer)
    Type.Number -> answer.text.trim().toDoubleOrNull()?.takeIf {
        it.isFinite() && it >= (minNumber ?: -Double.MAX_VALUE) && it <= (maxNumber ?: Double.MAX_VALUE)
    }?.let(Value::Number)
}

private fun String.codePointCount(): Int = indices.count { index ->
    index == 0 || !this[index].isLowSurrogate() || !this[index - 1].isHighSurrogate()
}

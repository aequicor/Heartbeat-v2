package io.github.aequicor.heartbeat.features.aistudio.presentation.mock

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Step
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEventKind

/** Deterministic reading material for the large-message demonstration chat. */
internal object LargeMessageDemo {
    const val ChatId = "large-messages"
    private const val Conclusion = "Разбор готов. В ходе работы можно раскрыть рассуждение и прочитать каждый случай."
    private const val Replay = "Отправьте «текст», «рассуждение», «отчёт» или «код», чтобы посмотреть пример в потоке."

    private val situations = listOf(
        "Читатель вернулся к началу ответа" to "первый абзац остаётся у верхнего края окна",
        "Читатель выделил несколько строк" to "в буфер попадает ровно выделенная цитата",
        "Читатель раскрыл этап работы" to "заголовок этапа остаётся в поле зрения",
        "Читатель открыл исходник диаграммы" to "переводы строк в исходнике сохраняются",
        "Читатель сузил окно" to "текст переносится и выбранная фраза остаётся доступной",
        "Читатель вернулся в диалог" to "черновик и место чтения восстанавливаются",
        "Читатель дошёл до конца ответа" to "продолжение появляется рядом с последней строкой",
        "Читатель прокрутил таблицу" to "можно прочитать последнюю колонку и вернуться к первой",
    )
    private val arrivals = listOf(
        "приходит новый абзац", "закрывается блок кода", "готово превью схемы", "ответ завершается",
    )
    private val windows = listOf(
        "основное окно", "компактное окно", "окно с открытой панелью файлов", "восстановленное окно",
    )

    private val cases: List<String> by lazy {
        buildList {
            for (window in windows) for ((action, expected) in situations) for (arrival in arrivals) {
                add("Наблюдение ${size + 1}, $window. $action. Пока он читает, $arrival. " +
                    "Ожидаемый результат — $expected. Для сверки сохраняем фразу над курсором, " +
                    "её соседние строки и положение прокрутки. Затем повторяем действие с клавиатуры.")
            }
        }
    }

    val plain by lazy { cases.joinToString("\n\n") }
    val reasoning by lazy {
        buildList {
            for ((index, pair) in situations.withIndex()) {
                val (action, expected) = pair
                windows.forEachIndexed { position, window ->
                    add(buildString {
                        if (position == 0) append("Случай ${index + 1}. $action. Проверяем, что $expected.\n\n")
                        append("Разберём $window. ")
                        for (arrival in arrivals) append("Когда $arrival, проверяем, что $expected. ")
                        append("\n\n")
                    })
                }
            }
        }
    }
    val report by lazy {
        buildString {
            appendLine("# Протокол чтения диалога")
            appendLine("\n```kotlin")
            appendLine("data class ReadingCase(val action: String, val expected: String)")
            appendLine("```")
            for ((index, entry) in situations.withIndex()) {
                appendLine("\n## ${index + 1}. ${entry.first}\n")
                appendLine("**Критерий** — ${entry.second}.")
                appendLine("\n| Событие | Ожидаемое поведение |")
                appendLine("| --- | --- |")
                for (arrival in arrivals) appendLine("| $arrival | ${entry.second} |")
                appendLine("\n> Контрольная фраза раздела ${index + 1} — ${entry.second}.\n")
                appendLine("```kotlin")
                appendLine("val case${index + 1} = ReadingCase(")
                appendLine("    action = \"${entry.first}\",")
                appendLine("    expected = \"${entry.second}\",")
                appendLine(")")
                appendLine("```\n")
                cases.filterIndexed { n, _ -> n / arrivals.size % situations.size == index }
                    .forEach {
                        appendLine(it)
                        appendLine()
                    }
            }
            append(AuroraMock.richSample)
        }
    }

    private val longCode by lazy {
        buildString {
            appendLine("## Набор событий для воспроизведения прокрутки\n")
            appendLine("```kotlin")
            appendLine("val events = listOf(")
            for (index in 1..2_048) {
                val (action, expected) = situations[(index - 1) % situations.size]
                appendLine("    \"Событие $index. $action. Ожидается, что $expected.\",")
            }
            appendLine(")")
            append("```")
        }
    }

    fun answer(prompt: String): String = when (prompt.trim().lowercase()) {
        "текст" -> plain
        "код" -> longCode
        "рассуждение" -> Conclusion
        else -> report
    }

    fun history(): List<Message> {
        val details = reasoning.joinToString("").trimEnd()
        val event = WorkEvent("reasoning", "Размышления", details, details, WorkEventKind.Thinking, StepStatus.Done)
        val step = Step("analysis", "Разбор поведения чата", "", StepStatus.Done, events = listOf(event))
        return listOf(
            Reply("large-analysis", "14:12", steps = listOf(step), text = "$Conclusion\n\n$Replay",
                status = ReplyStatus.Completed),
            User("ask-analysis", "14:11", "Разбери сценарии чтения по шагам."),
            Reply("large-text", "14:08", text = plain, status = ReplyStatus.Completed),
            User("ask-text", "14:07", "Собери наблюдения в один текст."),
            Reply("large-report", "14:04", text = report, status = ReplyStatus.Completed),
            User("ask-report", "14:03", "Подготовь отчёт с таблицами, кодом и схемами."),
        )
    }
}

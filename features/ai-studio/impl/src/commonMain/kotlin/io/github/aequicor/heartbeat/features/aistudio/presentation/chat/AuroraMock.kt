@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.FileDate
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Question
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.User

internal object AuroraMock {
    const val LongChat = "long"
    const val InitialChat = "project"
    const val HistoryTurns = 1_250
    const val StreamFrames = 240

    // The long-history scenario exercises the same fenced sources that arrive from an engine.
    val richSample = """
        ## Остановка запроса

        Сначала запросите **остановку**. Повтор доступен после подтверждения.

        ### Порядок действий

        1. Сохраните `requestId` и исходную реплику.
        2. Дождитесь *подтверждения* остановки.
           - История остаётся доступной.
           - ~~Повтор во время остановки~~ исключён.

        > **Проверка:** поздняя порция не должна возвращать старое поколение ответа.

        | Состояние | Повтор | История |
        | :--- | :---: | ---: |
        | Останавливается | Нет | Доступна |
        | Остановлен | Да | Сохранена |

        - [x] Исходная реплика сохранена
        - [ ] Новый ход ещё не запущен

        Подробнее: [карточка сессии](https://example.com/session).

        ---

        ```kotlin
        val canRetry = status == Status.Stopped
        ```

        ```mermaid
        flowchart LR
          A[Запущен] --> B[Останавливается] --> C[Остановлен]
        ```

        ```plantuml
        @startuml
        Клиент -> Агент: Остановить
        Агент --> Клиент: Остановлен
        @enduml
        ```
    """.trimIndent()

    val interimSample = """
        ### Промежуточный результат

        Проверка **исходных условий** завершена. Якорь чтения — `messageId + offset`.

        - [x] Реплика сохранена
        - [x] Повтор привязан к исходному ходу
        - [ ] Осталось проверить *поздние события*

        > Следующий этап проверит остановку без потери частичного ответа.

        | Проверка | Результат |
        | --- | --- |
        | История | Сохранена |
        | Прокрутка | Якорь удерживается |

        [Контекст проверки](https://example.com/session/checks).
    """.trimIndent()

    val inputs = listOf(
        File("brief", "architecture.md", "Markdown", "# Архитектура\n\nГраница UI и логики.\n\n" +
            "История хранится по диалогам. Остановка подтверждается отдельным событием.",
            FileDate(2026, 11, 14, "14:02")),
        File("cases", "scenarios.csv", "Таблица", "Сценарий,Ожидаемый результат\n" +
            "Повтор,Исходная реплика\nСтоп,Частичный результат\nВозврат,Якорь чтения",
            FileDate(2026, 10, 29, "11:20")),
        File("example", "Example.kt", "Код", "internal data class ReadingAnchor(val id: String, val offset: Int)\n",
            FileDate(2026, 10, 18, "16:30")),
    )

    val questions = listOf(
        Question("scope", "Что проверить в первую очередь?", listOf("Контракты сессий", "Экран чата", "Хранилище"),
            required = true, freeText = true),
        Question("checks", "Какие проверки включить?", listOf("Повтор", "Остановка", "Восстановление"),
            multiple = true, freeText = true),
        Question("report", "Добавить краткий отчёт?", listOf("Да", "Нет"), freeText = true, required = true),
    )

    val optionalQuestion = Question("comment", "Добавить комментарий к отчёту?", freeText = true)

    fun workspace() = AuroraWorkspace(
        selectedId = InitialChat,
        chats = mockNavigation(),
    )

    fun history(id: String): List<Message> = when (id) {
        InitialChat -> pencilConversation()
        LargeMessageDemo.ChatId -> LargeMessageDemo.history()
        LongChat -> buildList {
            repeat(HistoryTurns) { index ->
                val number = HistoryTurns - index
                val text = if (index == 0) richSample else "Проверка $number завершена. " +
                    "Повтор сохраняет исходную реплику, а Stop подтверждает остановку отдельным событием."
                add(Reply("mock-reply-$number", "14:03", text = text, status = ReplyStatus.Completed))
                add(User("mock-user-$number", "14:02", "Проверь контракты сессий и сценарий отмены ($number)"))
            }
        }
        else -> listOf()
    }

    fun output(id: String, text: String, time: String) = File(
        id = "$id-result",
        name = "aurora-report.md",
        kind = "Markdown",
        content = "# Проверка рабочей области\n\nЗапрос\n$text\n\n" +
            "## Результаты\n\n" +
            "• Отправка и повтор используют идентификатор исходной реплики.\n" +
            "• Остановка сохраняет полученный ответ.\n" +
            "• Якорь чтения хранится отдельно для каждой беседы.\n\n" +
            "## Проверка\n\nПример подготовлен по локальным демонстрационным данным.",
        date = FileDate(2026, 11, 14, time),
    )
}

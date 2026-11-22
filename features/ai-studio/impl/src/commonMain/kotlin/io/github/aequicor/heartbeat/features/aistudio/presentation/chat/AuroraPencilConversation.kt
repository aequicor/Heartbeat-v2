package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Step
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.WorkEventKind

/** The sample conversation in Pencil's Desktop long-history frame d6ZB8. */
internal fun pencilConversation(): List<Message> = listOf(
    User("sample-task", "14:02", "Проверь контракты сессий и сценарий отмены"),
    finalReply(
        "sample-reply", "14:03",
        "Повторный запрос можно отправить после подтверждения остановки. " +
            "Иначе оба запроса могут выполняться одновременно.\n\n" +
            "Дождитесь статуса «Остановлено», затем отправьте запрос снова.",
        "Проверка отмены запроса",
    ),
    User("sample-stopping", "14:04", "Что увидит пользователь, пока запрос останавливается?"),
    finalReply("sample-draft", "14:04", "Да. Черновик сохранён.", "Проверка черновика"),
    User("sample-disconnect", "14:05", "А если связь пропала во время остановки?"),
    finalReply(
        "sample-recovery", "14:06",
        "Сначала запрашиваем состояние текущего хода. Пока сервер не ответил, " +
            "показываем «Проверяем состояние» и сохраняем черновик. Если ход ещё выполняется, " +
            "повторяем остановку с тем же идентификатором. Новый запрос отправляем только после подтверждения.",
        "Восстановление после обрыва связи",
        recoveryEvents(),
    ),
    User("sample-repeat", "14:07", "Как избежать повторной отправки сообщения?"),
    finalReply(
        "sample-dedup", "14:08",
        "У каждой отправки должен быть постоянный идентификатор. После обрыва связи клиент сверяет его с сервером: " +
            "найденное сообщение возвращается в историю, отсутствующее можно отправить повторно. " +
            "Нажатие на кнопку во время проверки не создаёт второй запрос.",
        "Защита от повторной отправки",
    ),
    User("sample-queue", "14:09", "Что делать с сообщениями в очереди?"),
    finalReply(
        "sample-queued", "14:10",
        "Очередь сохраняется отдельно от выполняющегося хода. Остановка текущего ответа не удаляет следующие " +
            "сообщения. Перед продолжением пользователь может изменить текст или убрать отдельную реплику " +
            "из очереди. Порядок отправки остаётся видимым.",
        "Сохранение очереди сообщений",
    ),
    User("sample-checks", "14:11", "Добавь эти случаи в список проверок"),
    finalReply(
        "sample-more-checks", "14:12",
        "Добавлю проверки обрыва связи до и после подтверждения остановки, повторной отправки с тем же " +
            "идентификатором и восстановления очереди. Для каждого случая зафиксирую ожидаемый статус, " +
            "доступность отправки и сохранность черновика.",
        "Сценарии проверки отмены",
    ),
).asReversed()

private fun finalReply(
    id: String, time: String, text: String, stage: String, events: List<WorkEvent> = listOf(),
) = Reply(
    id, time, listOf(Step("check", stage, "", StepStatus.Done, events)), text = text, status = ReplyStatus.Completed,
)

private fun recoveryEvents() = listOf(
    WorkEvent(
        "contract", "Читаю контракт сессии", "Готово · session-contract.md",
        "running → stopping → stopped. Повторная отправка после подтверждения stopped.",
        WorkEventKind.File, StepStatus.Done, "Файл: session-contract.md",
    ),
    WorkEvent(
        "search", "Ищу обработчик восстановления связи", "Готово",
        "Проверка завершена. Результат использован в ответе.", WorkEventKind.Search, StepStatus.Done,
    ),
    WorkEvent(
        "thinking", "Размышления", "Сверяю состояние текущего хода после обрыва связи...",
        "Сверяю состояние текущего хода после обрыва связи. Сначала нужно получить состояние сервера, " +
            "затем подтвердить остановку. Черновик сохраняется до завершения проверки.",
        WorkEventKind.Thinking, StepStatus.Done,
    ),
)

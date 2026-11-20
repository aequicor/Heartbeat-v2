package io.github.aequicor.heartbeat.features.aistudio.ui

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Step
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEventKind

internal fun pencilTimelineFixture() = Reply(
    "pencil-work", "",
    steps = listOf(
        Step(
            "repeat", "Проверка повторной отправки", "", StepStatus.Done,
            result = "Повторная отправка доступна до подтверждения остановки.",
            events = listOf(
                WorkEvent(
                    "contract", "Читаю контракт сессии", "Готово · session-contract.md",
                    "Отмена: running → stopping → stopped\nПовторная отправка: доступна в stopping",
                    WorkEventKind.File, StepStatus.Done, "Файл: session-contract.md",
                ),
                WorkEvent(
                    "search", "Ищу обработчик остановки", "Готово", "Обработчик остановки найден",
                    WorkEventKind.Search, StepStatus.Done, "Поиск завершён",
                ),
                WorkEvent(
                    "thinking", "Размышления", "Сопоставляю состояния отмены с повторной отправкой...",
                    "Сопоставляю состояния отмены с повторной отправкой.", WorkEventKind.Thinking, StepStatus.Done,
                ),
            ),
        ),
        Step(
            "check", "Проверка переходов состояния", "", StepStatus.Current,
            activeTitle = "Проверяю повторную отправку",
            events = listOf(WorkEvent(
                "check", "Проверяю переходы состояния", "Выполняется",
                "Результат пока не получен", WorkEventKind.Check, StepStatus.Current, "Выполнение продолжается",
            )),
        ),
    ),
)

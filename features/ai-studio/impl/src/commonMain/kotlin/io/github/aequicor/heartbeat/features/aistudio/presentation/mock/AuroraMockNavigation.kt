package io.github.aequicor.heartbeat.features.aistudio.presentation.mock

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Quota
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Scenario
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.ProjectsSessionsBloc.Companion.Ungrouped
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraChat
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraWorkspace

internal fun mockNavigation(): List<AuroraChat> = buildList {
    val heartbeat = "Heartbeat"
    add(Chat(AuroraMock.InitialChat, "Единая модель сессий", heartbeat,
        contextPercent = 61, quota = Quota(hours = 5, usedPercent = 78)))
    add(Chat("contract-check", "Проверка контрактов", heartbeat, parentId = AuroraMock.InitialChat,
        mockSubagent = true, quota = Quota(hours = 5, usedPercent = 24)))
    add(Chat("ui-regression", "Регрессия UI", heartbeat, parentId = AuroraMock.InitialChat,
        navigationStatus = NavigationStatus.Unread))
    addProject(heartbeat, listOf(
        "Архитектура движка", "Проверка восстановления сессий после разрыва соединения", "Автотесты для очереди",
        "Диагностика отмены", "Журнал событий", "Проверка API",
    ))
    addProject("Мобильное приложение", listOf(
        "Навигация Android", "Подготовка релиза", "Навигация между проектами и восстановление черновиков на Android",
    ), NavigationStatus.Attention)
    addProject("Сайт проекта", listOf("Страница загрузки"), NavigationStatus.Unread)
    addProject(
        "Дизайн-система", listOf("Компоненты чата", "Типографика", "Состояния прокрутки"), NavigationStatus.Attention,
    )
    addProject("Документы", listOf("Схема сессии", "Протокол обмена", "Контракты событий"), NavigationStatus.Attention)
    addProject(
        "Инструменты", listOf("Работа с файлами", "Поиск по проекту", "Журнал выполнения"), NavigationStatus.Attention,
    )
    addProject("Интеграции", listOf("Подключение агента", "Поток ответов", "Проверка соединения"))
    add(Chat(LargeMessageDemo.ChatId, "Большие сообщения", "Проверки", Scenario.Stream))
    addProject("Проверки", listOf("Отмена запроса", "Восстановление связи", "Очередь сообщений"))
    add(Chat(AuroraMock.LongChat, "Длинная история", "Проверки", Scenario.Stream, contextPercent = 71))
    add(Chat("research", "Отчёт по контрактам", "Проверки", Scenario.RunFailure))
    addProject(Ungrouped, listOf("Как устроен планировщик", "Идеи для Aurora"))
}.map { AuroraChat(it) }

internal fun AuroraWorkspace.withMockNavigation(): AuroraWorkspace {
    val missing = mockNavigation().associateBy { it.summary.id }.toMutableMap()
    val restored = chats.map { saved ->
        val fixture = missing.remove(saved.summary.id) ?: return@map saved
        saved.copy(summary = saved.summary.copy(
            project = fixture.summary.project,
            parentId = fixture.summary.parentId,
            quota = fixture.summary.quota,
            mockSubagent = fixture.summary.mockSubagent,
        ))
    }
    return copy(chats = restored + missing.values)
}

private fun MutableList<Chat>.addProject(
    project: String,
    titles: List<String>,
    firstStatus: NavigationStatus? = null,
) {
    titles.forEachIndexed { index, title ->
        add(Chat("fixture-$project-$index", title, project, navigationStatus = firstStatus.takeIf { index == 0 }))
    }
}

package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStore
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.ReadSubagentHistoryUseCase
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Scenario
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Step
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.WorkEventKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.ReadSubagentHistoryUseCase.Subagent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class SubagentIndicatorTest {

    @Test
    fun showsLaunchFromEngineWorkAndKeepsItAfterTheReplyIsArchived() {
        val event = WorkEvent("spawn", "spawn_agent", "", "", WorkEventKind.Check, StepStatus.Current, detailLabel = "math-qa")
        val reply = Reply("reply", "12:00", steps = listOf(Step("step", "", "", StepStatus.Current, listOf(event))))
        val running = AiStudioScreenState(live = reply)
        assertEquals(listOf("math-qa"), running.visibleSubagents.map { it.title })
        val action = assertIs<AiStudioAction.OpenSubagentWork>(running.visibleSubagents.single().open)
        assertEquals("spawn", action.target.eventId)
        val bootstrap = AiStudioScreenState.SessionBootstrapState(
            AiStudioScreenState.SessionBootstrapState.Project.Ungrouped,
        )
        assertTrue(running.copy(sessionBootstrap = bootstrap).visibleSubagents.isEmpty())
        assertEquals(running.visibleSubagents, running.copy(live = null, messages = listOf(reply)).visibleSubagents)
    }

    @Test
    fun showsIndicatorWhenTheDemoStartsASubagentInANewChat() = runTest {
        val store = DefaultAiStudioStore(EmptyPreferences, backgroundScope)
        runCurrent()
        store.dispatch(AiStudioAction.NewChat)
        runCurrent()
        val chat = checkNotNull(store.screenState.value.chat)
        store.dispatch(AiStudioAction.SetScenario(Scenario.Subagents))
        runCurrent()
        assertTrue(store.screenState.value.visibleSubagents.isEmpty())

        store.dispatch(AiStudioAction.EditDraft(chat.id, "Проверь переходы состояния"))
        store.dispatch(AiStudioAction.Send)
        advanceTimeBy(2_500)
        runCurrent()

        val launch = store.screenState.value.visibleSubagents.single()
        assertEquals("Проверка переходов", launch.title)
        store.dispatch(launch.open)
        runCurrent()
        assertEquals(launch.work, store.screenState.value.referenceJump?.target)
        val conversation = checkNotNull(store.screenState.value.subagentConversation)
        assertEquals("Проверка переходов", conversation.title)
        store.dispatch(AiStudioAction.EditDraft(chat.id, "Это не должно попасть в родительский чат"))
        assertTrue(store.draft.value.text.isEmpty())
        store.dispatch(AiStudioAction.CloseSubagentConversation)
        runCurrent()
        assertEquals(null, store.screenState.value.subagentConversation)
    }

    @Test
    fun refreshesTheOpenProtocolSubagentAndRetainsHistoryBetweenViews() = runTest {
        val child = Subagent("child-session", "Проверка ACP", null, ChatHistoryEntry.StepStatus.Current, emptyList())
        val history = MutableStateFlow(Result.success(listOf(child)))
        val reader = ReadSubagentHistoryUseCase { history }
        val store = DefaultAiStudioStore(
            EmptyPreferences, backgroundScope,
            defaultDispatcher = StandardTestDispatcher(testScheduler), readSubagentHistory = reader,
        )
        runCurrent()
        store.dispatch(AiStudioAction.NewChat)
        runCurrent()
        val chat = checkNotNull(store.screenState.value.chat)
        val launch = store.screenState.value.visibleSubagents.single()
        assertEquals("Проверка ACP", launch.title)
        store.dispatch(launch.open)
        runCurrent()
        assertEquals(emptyList(), store.screenState.value.subagentConversation?.messages)

        val answer = ChatHistoryEntry.Reply(
            "child", "", "Первый фрагмент", null, null, ChatHistoryEntry.ReplyStatus.Working, emptyList(), null,
        )
        history.value = Result.success(listOf(child.copy(messages = listOf(answer))))
        runCurrent()
        assertEquals("Первый фрагмент", assertIs<Reply>(store.screenState.value.subagentConversation?.messages?.single()).text)
        store.dispatch(AiStudioAction.EditDraft(chat.id, "Не отправляется"))
        assertTrue(store.draft.value.text.isEmpty())

        store.dispatch(AiStudioAction.CloseSubagentConversation)
        runCurrent()
        val completed = answer.copy(text = "Готово", status = ChatHistoryEntry.ReplyStatus.Completed)
        history.value = Result.success(listOf(child.copy(messages = listOf(completed))))
        runCurrent()
        assertEquals(null, store.screenState.value.subagentConversation)
        store.dispatch(launch.open)
        runCurrent()
        assertEquals("Готово", assertIs<Reply>(store.screenState.value.subagentConversation?.messages?.single()).text)
    }

    private object EmptyPreferences : PreferencesStore {

        override fun observeString(key: String, default: String) = flowOf(default)

        override fun observeBoolean(key: String, default: Boolean) = flowOf(default)

        override fun observeLong(key: String, default: Long) = flowOf(default)

        override suspend fun putString(key: String, value: String) = Unit

        override suspend fun putBoolean(key: String, value: Boolean) = Unit

        override suspend fun putLong(key: String, value: Long) = Unit

        override suspend fun remove(key: String) = Unit
    }
}

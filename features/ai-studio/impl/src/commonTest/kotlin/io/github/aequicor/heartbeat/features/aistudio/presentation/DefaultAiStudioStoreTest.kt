@file:Suppress("MagicNumber")
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.aequicor.heartbeat.features.aistudio.presentation

import io.github.aequicor.heartbeat.features.aistudio.presentation.rich.ReplyMarkdownParser

import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.mock.AuroraMock
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Access
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Activity
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Anchor
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Delivery
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.DetailFormat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.FileDate
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyBlock
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Request
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Scenario
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEventKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraWorkspace
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.prepareFileGroups
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DefaultAiStudioStoreTest {

    @Test
    fun projectsAndSubsessionsExpandInPlaceAndSearchShowsAncestors() = runTest {
        val store = studio()
        val initial = store.screenState.value.navigation
        assertEquals("Heartbeat", initial.first().title)
        assertEquals(1, initial.single { it.id == "contract-check" }.depth)
        store.dispatch(AiStudioAction.ToggleNavigation(AuroraMock.InitialChat))
        runCurrent()
        assertFalse(store.screenState.value.navigation.any { it.id == "contract-check" })
        store.dispatch(AiStudioAction.SearchChats("Проверка контрактов"))
        runCurrent()
        assertEquals(listOf("Heartbeat", "Единая модель сессий", "Проверка контрактов"),
            store.screenState.value.navigation.map { it.title })
        store.dispatch(AiStudioAction.ShowChatSearch(false))
        store.dispatch(AiStudioAction.ToggleNavigation("more:Heartbeat"))
        runCurrent()
        val expanded = store.screenState.value
        val firstAdded = expanded.navigation.single { it.title == "Журнал событий" }
        assertEquals(firstAdded.id, expanded.navigationFocusId)
        assertEquals(AuroraMock.InitialChat, expanded.chat?.id)

        store.dispatch(AiStudioAction.ToggleNavigation("more:Heartbeat"))
        store.dispatch(AiStudioAction.NavigationFocusHandled("stale-target"))
        runCurrent()
        assertEquals(firstAdded.id, store.screenState.value.navigationFocusId)
        store.dispatch(AiStudioAction.NavigationFocusHandled(firstAdded.id))
        runCurrent()
        assertNull(store.screenState.value.navigationFocusId)
    }

    @Test
    fun failedRestorePreservesStoredDataThroughLaterEdits() = runTest {
        val data = TestPreferences()
        val saved = "{unreadable workspace}"
        data.snapshot.value = saved
        val store = studio(data)
        assertNotNull(store.screenState.value.storageError)
        assertEquals(saved, data.snapshot.value)
        store.dispatch(AiStudioAction.NewChat)
        runCurrent()
        store.dispatch(AiStudioAction.EditDraft(assertNotNull(store.draft.value.chatId), "Черновик"))
        advance(200)
        assertEquals("Черновик", store.draft.value.text)
        assertEquals(saved, data.snapshot.value)
    }

    @Test
    fun restoringAProjectChatKeepsSidebarOrder() = runTest {
        val data = TestPreferences()
        val store = studio(data)
        store.dispatch(AiStudioAction.NewProjectChat("Документы"))
        runCurrent()
        val id = assertNotNull(store.draft.value.chatId)
        val navigation = store.screenState.value.navigation.map { it.id }
        val restored = studio(data)
        assertEquals(id, restored.draft.value.chatId)
        assertEquals(navigation, restored.screenState.value.navigation.map { it.id })
    }

    @Test
    fun missingSelectedChatKeepsTheOtherChats() = runTest {
        val data = TestPreferences()
        val store = studio(data)
        store.dispatch(AiStudioAction.NewChat)
        runCurrent()
        val id = assertNotNull(store.draft.value.chatId)
        val saved = Json.decodeFromString<AuroraWorkspace>(data.snapshot.value)
        data.snapshot.value = Json.encodeToString(saved.copy(selectedId = "missing"))
        val recovered = studio(data)
        assertTrue(recovered.screenState.value.chats.any { it.id == id })
        assertNotNull(recovered.screenState.value.chat)
    }

    @Test
    fun archivesOnlyTheChosenSessionAndPreservesTheActiveDraftAfterRestart() = runTest {
        val data = TestPreferences()
        val store = studio(data)
        val archived = AuroraMock.InitialChat
        val initialMessages = store.screenState.value.messages
        store.dispatch(AiStudioAction.EditDraft(archived, "Черновик перед архивированием"))
        store.dispatch(AiStudioAction.ArchiveChat(archived))
        store.dispatch(AiStudioAction.ArchiveChat(archived))
        runCurrent()

        val state = store.screenState.value
        assertEquals(archived, state.chat?.id)
        assertEquals(initialMessages, state.messages)
        assertFalse(state.chats.any { it.id == archived })
        assertFalse(state.navigation.any { it.id == archived })
        assertEquals(0, state.navigation.single { it.id == "contract-check" }.depth)
        assertEquals(0, state.navigation.single { it.id == "ui-regression" }.depth)

        val restored = studio(data)
        assertEquals(archived, restored.screenState.value.chat?.id)
        assertEquals("Черновик перед архивированием", restored.draft.value.text)
        assertFalse(restored.screenState.value.chats.any { it.id == archived })
        restored.dispatch(AiStudioAction.SearchChats("Проверка контрактов"))
        runCurrent()
        assertEquals(listOf("contract-check"), restored.screenState.value.chats.map { it.id })
    }

    @Test
    fun headerUsesKnownMetricsAndTheCurrentChatsSubagents() = runTest {
        val store = studio()
        val initial = store.screenState.value
        assertEquals(listOf("contract-check"), initial.subagents.map { it.chatId })
        assertEquals(61, initial.chat?.contextPercent)
        assertEquals(78, initial.chat?.quota?.usedPercent)
        store.dispatch(AiStudioAction.SearchChats("Только другая группа"))
        runCurrent()
        assertEquals(initial.subagents, store.screenState.value.subagents)

        store.dispatch(AiStudioAction.OpenChat("contract-check"))
        runCurrent()
        assertTrue(store.screenState.value.subagents.isEmpty())
        assertNull(store.screenState.value.chat?.contextPercent)
        assertEquals(24, store.screenState.value.chat?.quota?.usedPercent)

        store.dispatch(AiStudioAction.NewChat)
        runCurrent()
        assertTrue(store.screenState.value.subagents.isEmpty())
        assertNull(store.screenState.value.chat?.contextPercent)
        assertNull(store.screenState.value.chat?.quota)
    }

    @Test
    fun sendsAnswersAsOneMessageAndOpensResult() = runTest {
        val store = studio()
        store.dispatch(AiStudioAction.Attach("brief"))
        send(store)
        awaitQuestions(store)
        val first = questions(store)
        store.dispatch(AiStudioAction.EditDraft(AuroraMock.InitialChat, "Следующий вопрос"))
        store.dispatch(AiStudioAction.MoveQuestion(first.id, 1))
        store.dispatch(AiStudioAction.SkipQuestion(first.id))
        store.dispatch(AiStudioAction.SubmitAnswers(first.id))
        runCurrent()
        assertEquals(0, questions(store).index)
        assertNotNull(questions(store).error)
        fillAnswers(store)

        assertNull(store.screenState.value.request)
        assertEquals("Следующий вопрос", store.draft.value.text)
        assertEquals(1, store.screenState.value.messages.filterIsInstance<User>().count { it.answers })
        advance(400)
        val permission = assertIs<Request.Permission>(store.screenState.value.request)
        val live = assertNotNull(store.screenState.value.live)
        store.dispatch(AiStudioAction.ToggleStep(live.id, "read"))
        store.dispatch(AiStudioAction.ToggleWorkEvent(live.id, "read", "thinking"))
        store.dispatch(AiStudioAction.ToggleStep("sample-recovery", "check"))
        runCurrent()
        assertTrue("${live.id}/read" in store.screenState.value.expandedSteps)
        assertTrue("${live.id}/read/thinking" in store.screenState.value.expandedEvents)
        store.dispatch(AiStudioAction.AnswerPermission(permission.id, allow = true))
        advance(950)

        val result = assertIs<Reply>(store.screenState.value.messages.first())
        assertEquals(ReplyStatus.Completed, result.status)
        val report = assertNotNull(result.result)
        assertEquals(setOf("sample-recovery/check"), store.screenState.value.expandedSteps)
        assertTrue(store.screenState.value.expandedEvents.isEmpty())
        store.dispatch(AiStudioAction.OpenResult(result.id))
        runCurrent()
        assertEquals(report, store.screenState.value.viewer)
        assertTrue(store.screenState.value.fileGroups.flatMap { it.files }.any { it.id == "brief" })

        store.dispatch(AiStudioAction.CloseViewer)
        store.dispatch(AiStudioAction.ToggleFiles(true))
        store.dispatch(AiStudioAction.ShowFileSearch(true))
        store.dispatch(AiStudioAction.SearchFiles("AURORA"))
        runCurrent()
        assertTrue(store.screenState.value.fileSearchVisible)
        val filtered = store.screenState.value.fileGroups.single()
        assertEquals("Ноябрь", filtered.title)
        assertEquals(report.id, filtered.files.single().id)
        store.dispatch(AiStudioAction.ShowFileSearch(false))
        store.dispatch(AiStudioAction.SearchFiles("запоздавший ввод"))
        runCurrent()
        assertFalse(store.screenState.value.fileSearchVisible)
        assertEquals("", store.screenState.value.fileSearch)
        assertTrue(store.screenState.value.fileGroups.flatMap { it.files }.any { it.id == "brief" })
    }

    @Test
    fun fileGroupsDistinguishYearsAndKeepFilesFromOlderSnapshots() {
        val old = Json.decodeFromString<File>(
            """{"id":"old","name":"README","kind":"Текст","content":"да"}""",
        )
        val january = old.copy(id = "january", name = "run.MD", date = FileDate(2026, 1, 1, "00:10"))
        val december = old.copy(id = "december", name = "trace.log", date = FileDate(2025, 12, 31, "23:59"))
        val groups = prepareFileGroups(listOf(old, december, january))
        assertEquals(listOf("Январь 2026", "Декабрь 2025", "Без даты"), groups.map { it.title })
        assertEquals(listOf("january", "december", "old"), groups.map { it.files.single().id })
        val current = groups.first().files.single()
        assertEquals("md", current.extension)
        assertEquals("1 янв, 00:10", current.date)
        assertEquals("4 Б", current.size)
        assertEquals("run.MD, 4 Б, 1 янв, 00:10", current.accessibilityLabel)
        assertNull(groups.last().files.single().date)
    }

    @Test
    fun sendAndDecisionRetriesKeepTheOriginalRows() = runTest {
        val store = studio()
        store.dispatch(AiStudioAction.SetScenario(Scenario.SendFailure))
        send(store)
        advance(400)
        val failed = assertIs<User>(store.screenState.value.messages.first())
        assertEquals(Delivery.Failed, failed.delivery)
        store.dispatch(AiStudioAction.EditDraft(AuroraMock.InitialChat, "Другой черновик"))
        store.dispatch(AiStudioAction.Retry(failed.id))
        advance(1_400)
        assertEquals(listOf(Delivery.Sent), store.screenState.value.messages.filterIsInstance<User>()
            .filter { it.id == failed.id }.map { it.delivery })
        assertEquals("Другой черновик", store.draft.value.text)

        store.dispatch(AiStudioAction.CancelTurn)
        advance(400)
        store.dispatch(AiStudioAction.SetScenario(Scenario.DecisionFailure))
        send(store)
        awaitQuestions(store)
        fillAnswers(store)
        advance(400)
        val answer = assertIs<User>(store.screenState.value.messages.first())
        assertTrue(answer.answers)
        assertEquals(Delivery.Failed, answer.delivery)
        assertNull(store.screenState.value.request)
        store.dispatch(AiStudioAction.Retry(answer.id))
        advance(400)
        assertIs<Request.Permission>(store.screenState.value.request)
        assertEquals(1, store.screenState.value.messages.count { it.id == answer.id })
    }

    @Test
    fun retryingAnswersAfterRestartCompletesTheInterruptedReviewStep() = runTest {
        val data = TestPreferences()
        val firstSession = sessionScope()
        val store = DefaultAiStudioStore(data, firstSession)
        runCurrent()
        store.dispatch(AiStudioAction.SetAccess(Access.ReadOnly))
        send(store)
        awaitQuestions(store)
        fillAnswers(store)
        val live = assertNotNull(store.screenState.value.live)
        val answer = assertIs<User>(store.screenState.value.messages.first())
        assertEquals(Delivery.Sending, answer.delivery)

        firstSession.cancel()
        val restarted = studio(data)
        restarted.dispatch(AiStudioAction.Retry(answer.id))
        advance(400)
        val history = restarted.screenState.value.messages
        val completed = assertIs<Reply>(history.single { it.id == live.id })
        assertEquals(ReplyStatus.Completed, completed.status)
        assertEquals(live.steps.map { it.id }, completed.steps.map { it.id })
        assertTrue(completed.steps.all { it.status == StepStatus.Done })
        assertEquals(Delivery.Sent, assertIs<User>(history.single { it.id == answer.id }).delivery)
    }

    @Test
    fun executionRetryKeepsReplyAndStopInvalidatesQueuedEvents() = runTest {
        val store = studio()
        store.dispatch(AiStudioAction.SetScenario(Scenario.RunFailure))
        send(store)
        awaitQuestions(store)
        fillAnswers(store)
        advance(400)
        val permission = assertIs<Request.Permission>(store.screenState.value.request)
        store.dispatch(AiStudioAction.AnswerPermission(permission.id, true))
        advance(950)

        val failed = assertIs<Reply>(store.screenState.value.messages.first())
        assertEquals(ReplyStatus.Failed, failed.status)
        assertNotNull(failed.interim)
        store.dispatch(AiStudioAction.Retry(failed.id))
        runCurrent()
        assertEquals(failed.id, store.screenState.value.live?.id)
        store.dispatch(AiStudioAction.EditDraft(AuroraMock.InitialChat, "После остановки"))
        store.dispatch(AiStudioAction.CancelTurn)
        store.dispatch(AiStudioAction.Send)
        runCurrent()
        assertEquals(Activity.Stopping, store.screenState.value.activity)
        advance(2_000)

        val stopped = assertIs<Reply>(store.screenState.value.messages.first())
        assertEquals(failed.id, stopped.id)
        assertEquals(ReplyStatus.Stopped, stopped.status)
        assertEquals("После остановки", store.draft.value.text)
        assertEquals(Activity.Idle, store.screenState.value.activity)
    }

    @Test
    fun finishingInAnUnselectedChatCollapsesOnlyThatTurnsWork() = runTest {
        val store = studio()
        send(store)
        awaitQuestions(store)
        fillAnswers(store)
        advance(400)
        val permission = assertIs<Request.Permission>(store.screenState.value.request)
        val replyId = assertNotNull(store.screenState.value.live).id
        store.dispatch(AiStudioAction.ToggleStep(replyId, "read"))
        store.dispatch(AiStudioAction.ToggleWorkEvent(replyId, "read", "thinking"))
        store.dispatch(AiStudioAction.ToggleStep("sample-recovery", "check"))
        store.dispatch(AiStudioAction.AnswerPermission(permission.id, allow = true))
        store.dispatch(AiStudioAction.OpenChat(AuroraMock.LongChat))
        runCurrent()
        assertTrue("${replyId}/read/thinking" in store.screenState.value.expandedEvents)
        advance(950)

        assertEquals(AuroraMock.LongChat, store.screenState.value.chat?.id)
        assertEquals(setOf("sample-recovery/check"), store.screenState.value.expandedSteps)
        assertTrue(store.screenState.value.expandedEvents.isEmpty())
        store.dispatch(AiStudioAction.OpenChat(AuroraMock.InitialChat))
        runCurrent()
        val reply = assertIs<Reply>(store.screenState.value.messages.first())
        assertEquals(replyId, reply.id)
        assertEquals(ReplyStatus.Completed, reply.status)
    }

    @Test
    fun freezesOptionalTimerAtItsCurrentValueAndDeclineFinishesWithoutAFile() = runTest {
        val store = studio()
        store.dispatch(AiStudioAction.SetScenario(Scenario.Optional))
        send(store)
        advance(2_200)
        val panel = questions(store)
        assertTrue(panel.remaining < 1f)
        store.dispatch(AiStudioAction.InteractWithRequest("old-request"))
        advance(250)
        assertTrue(questions(store).remaining < panel.remaining)
        val remaining = questions(store).remaining
        store.dispatch(AiStudioAction.InteractWithRequest(panel.id))
        advance(130_000)
        assertEquals(remaining, questions(store).remaining)
        assertTrue(questions(store).frozen)
        assertTrue(questions(store).answer.text.isEmpty())
        store.dispatch(AiStudioAction.EditAnswer(panel.id, panel.question.id, "Добавить"))
        store.dispatch(AiStudioAction.SubmitAnswers(panel.id))
        advance(400)
        val permission = assertIs<Request.Permission>(store.screenState.value.request)
        store.dispatch(AiStudioAction.AnswerPermission(permission.id, false))
        runCurrent()
        val reply = assertIs<Reply>(store.screenState.value.messages.first())
        assertEquals(ReplyStatus.Completed, reply.status)
        assertNull(reply.result)
    }

    @Test
    fun untouchedOptionalTextRequestExpiresWithoutGrantingPermission() = runTest {
        val store = studio()
        store.dispatch(AiStudioAction.SetScenario(Scenario.Optional))
        send(store)
        awaitQuestions(store)
        val panel = questions(store)
        val duration = 120_000L + panel.question.title.length * 50
        val rounded = (duration + 249) / 250 * 250
        advance(rounded - 1)
        assertEquals(panel.id, questions(store).id)
        advance(1)
        assertNull(store.screenState.value.request)
        advance(400)
        assertIs<Request.Permission>(store.screenState.value.request)
        assertTrue(store.screenState.value.messages.filterIsInstance<User>().any { it.text == "Время ответа истекло" })
    }

    @Test
    fun restoresTheDraftAnchorAndAttachmentsAcrossSelectionAndRestart() = runTest {
        val data = TestPreferences()
        val firstScope = sessionScope()
        val store = DefaultAiStudioStore(data, firstScope)
        runCurrent()
        store.dispatch(AiStudioAction.OpenChat(AuroraMock.LongChat))
        runCurrent()
        val messages = store.screenState.value.messages
        assertEquals(AuroraMock.HistoryTurns * 2, messages.size)
        val anchor = Anchor(messages[1_450].id, 12)
        store.dispatch(AiStudioAction.ReadAt(AuroraMock.LongChat, anchor))
        store.dispatch(AiStudioAction.EditDraft(AuroraMock.LongChat, "Черновик длинного диалога"))
        store.dispatch(AiStudioAction.Attach("cases"))
        store.dispatch(AiStudioAction.OpenChat(AuroraMock.InitialChat))
        runCurrent()
        assertEquals("", store.draft.value.text)
        store.dispatch(AiStudioAction.OpenChat(AuroraMock.LongChat))
        advance(175)
        assertEquals(1_450, store.screenState.value.restoreIndex)
        assertEquals(anchor, store.screenState.value.restoreAnchor)
        assertTrue(data.snapshot.value.isNotBlank())

        firstScope.cancel()
        val restarted = studio(data)
        assertEquals("Черновик длинного диалога", restarted.draft.value.text)
        assertEquals(anchor, restarted.screenState.value.restoreAnchor)
        assertEquals(listOf("cases"), restarted.screenState.value.attachments.map { it.id })
    }

    @Test
    fun typingAfterSendingKeepsTheSubmittedTextAndTheNextDraftSeparate() = runTest {
        val store = studio()
        send(store)
        store.dispatch(AiStudioAction.EditDraft(AuroraMock.InitialChat, "Новый черновик до обработки отправки"))
        runCurrent()
        val user = assertIs<User>(store.screenState.value.messages.first())
        assertEquals("Проверь контракты сессий", user.text)
        assertEquals("Новый черновик до обработки отправки", store.draft.value.text)
    }

    @Test
    fun legacyReadNotesRestoresMarkdownAndOtherFileToolOutputRemainsPlain() = runTest {
        val record = """
            {
              "id": "read-notes", "title": "Читаю рабочие заметки", "summary": "Готово",
              "details": "# Архитектура", "kind": "File", "status": "Done",
              "detailLabel": "Файл: architecture.md"
            }
        """.trimIndent()
        val notes = Json.decodeFromString<WorkEvent>(record)
        assertEquals(DetailFormat.Markdown, notes.detailsFormat)
        val content = assertNotNull(notes.markdownDetails)
        val firstRead = content.document()
        assertIs<ReplyBlock.Heading>(firstRead.blocks.single())
        assertSame(firstRead, content.document())
        val log = Json.decodeFromString<WorkEvent>(record.replace("read-notes", "read-log"))
        assertEquals(DetailFormat.Plain, log.detailsFormat)
        assertNull(log.markdownDetails)
    }

    @Test
    fun thinkingAccumulatesFragmentsWithoutChangingDisclosureAndRetainsTextOnStop() = runTest {
        val store = studio()
        send(store)
        advance(400)
        val live = assertNotNull(store.screenState.value.live)
        val step = live.steps.single()
        val event = step.events.single { it.kind == WorkEventKind.Thinking }
        val key = "${live.id}/${step.id}/${event.id}"
        val toggle = AiStudioAction.ToggleWorkEvent(live.id, step.id, event.id)
        assertEquals("Ожидаю текст…", event.preview)
        assertEquals("Ожидаю текст…", event.detailText)

        store.dispatch(toggle)
        advance(300)
        val current = assertNotNull(store.screenState.value.live)
        val partial = current.steps.single().events.single { it.id == event.id }
        assertTrue(key in store.screenState.value.expandedEvents)
        assertTrue(partial.details.isNotBlank())
        val expected = "Выделяю условия **повтора**, остановки и восстановления."
        assertTrue(expected.startsWith(partial.details) && partial.details.length < expected.length)
        assertEquals(partial.details.replace("**", "").trimEnd('*'), partial.preview)
        assertNotNull(partial.markdownDetails)

        store.dispatch(toggle)
        advance(300)
        val complete = assertNotNull(store.screenState.value.live)
        val received = complete.steps.single().events.single { it.id == event.id }
        assertFalse(key in store.screenState.value.expandedEvents)
        assertTrue(received.details.startsWith(partial.details))
        assertTrue(received.details.length > partial.details.length)

        store.dispatch(AiStudioAction.CancelTurn)
        advance(600)
        assertNull(store.screenState.value.request)
        val stopped = assertIs<Reply>(store.screenState.value.messages.first())
        val saved = stopped.steps.single().events.single { it.id == event.id }
        assertEquals(StepStatus.Stopped, saved.status)
        assertEquals("Размышления прерваны", saved.title)
        assertEquals(received.details, saved.details)
        val expectedFinal = assertIs<ReplyBlock.Paragraph>(
            ReplyMarkdownParser(literalFastPath = false)
                .parse(received.details, final = true).blocks.single(),
        )
        val archivedText = assertIs<ReplyBlock.Paragraph>(
            assertNotNull(saved.markdownDetails).document().blocks.single(),
        )
        assertEquals(expectedFinal.lines, archivedText.lines)
    }

    @Test
    fun restartingDuringThinkingKeepsTheSameEventHistoryAsStopping() = runTest {
        val data = TestPreferences()
        val store = studio(data)
        send(store)
        advance(850)
        val live = assertNotNull(store.screenState.value.live)
        val thinking = live.steps.single().events.single { it.kind == WorkEventKind.Thinking }
        assertTrue(thinking.details.isNotBlank())

        // Capture a completed periodic save, rather than assuming one save per thinking fragment.
        var waited = 0
        do {
            val saved = Json.decodeFromString<AuroraWorkspace>(data.snapshot.value)
                .chats.first { it.summary.id == AuroraMock.InitialChat }.interrupted
            if (saved?.steps == store.screenState.value.live?.steps) break
            advance(1)
            waited++
        } while (waited < 1_000)
        assertTrue(waited < 1_000)
        val checkpoint = TestPreferences(data.snapshot.value)
        store.dispatch(AiStudioAction.CancelTurn)
        advance(400)
        val stopped = assertIs<Reply>(store.screenState.value.messages.first())
        val recovered = assertIs<Reply>(studio(checkpoint).screenState.value.messages.first())
        assertEquals(ReplyStatus.Stopped, recovered.status)
        assertEquals(stopped.steps, recovered.steps)
    }

    @Test
    fun restoresReceivedTextWhenAStreamingTurnIsInterrupted() = runTest {
        val data = TestPreferences()
        val store = studio(data)
        store.dispatch(AiStudioAction.SetScenario(Scenario.Stream))
        send(store)
        advance(2_000)
        val received = store.liveText.value
        assertTrue(received.text.isNotBlank())
        val raw = received.text + " [ещё не законченная ссылка"
        val live = assertNotNull(store.screenState.value.live)
        store.event { store.streamText(AuroraMock.InitialChat, live.copy(text = raw)) }
        runCurrent()
        store.dispatch(AiStudioAction.OpenChat(AuroraMock.LongChat))
        runCurrent()

        val restored = studio(data)
        restored.dispatch(AiStudioAction.OpenChat(AuroraMock.InitialChat))
        runCurrent()
        val interrupted = assertIs<Reply>(restored.screenState.value.messages.first())
        assertEquals(received.replyId, interrupted.id)
        assertEquals(ReplyStatus.Stopped, interrupted.status)
        assertEquals(raw, interrupted.text)
    }

    @Test
    fun streamingReusesHistoryAndFinishesPredictably() = runTest {
        val store = studio()
        store.dispatch(AiStudioAction.OpenChat(AuroraMock.LongChat))
        runCurrent()
        store.dispatch(AiStudioAction.EditDraft(AuroraMock.LongChat, "Проверка потока"))
        store.dispatch(AiStudioAction.Send)
        advance(1_800)
        val beforeStream = store.screenState.value
        advance(400)
        assertTrue(beforeStream === store.screenState.value)
        assertFalse(store.liveText.value.text.isBlank())
        assertNotNull(store.liveText.value.document)
        advance(10_000)
        val reply = assertIs<Reply>(store.screenState.value.messages.first())
        assertEquals("Проверено 240 этапов. Отчёт готов.", reply.text)
    }

    @Test
    fun completionPublishesTheFullFinalDocumentBeforeThePendingWindowExpires() = runTest {
        val store = studio()
        store.dispatch(AiStudioAction.SetScenario(Scenario.Stream))
        send(store)
        advance(1_800)
        val live = assertNotNull(store.screenState.value.live)
        val source = "См. [пример](https://example.com) и [незакрытый"
        store.event { store.streamText(AuroraMock.InitialChat, live.copy(text = source)) }
        runCurrent()
        advance(5)
        store.event { store.finish(AuroraMock.InitialChat, live.copy(text = source, status = ReplyStatus.Completed)) }
        runCurrent()
        val final = assertIs<Reply>(store.screenState.value.messages.first())
        assertEquals(source, final.text)
        val document = assertNotNull(final.historyContent).available
        assertNotNull(document)
        assertEquals(document, final.historyContent.document())
        val completed = store.screenState.value
        advance(32)
        assertSame(completed, store.screenState.value)
    }

    @Test
    fun largeMessageDemonstrationStreamsTheSelectedExampleAndKeepsItsFullText() = runTest {
        val store = studio()
        val chat = store.screenState.value.chats.single { it.title == "Большие сообщения" }
        store.dispatch(AiStudioAction.OpenChat(chat.id))
        runCurrent()
        val examples = store.screenState.value.messages.filterIsInstance<Reply>()
        val reasoning = examples.first().steps.single().events.single().details
        assertTrue(reasoning.length > 8_000)
        val plain = examples.single { it.id == "large-text" }.text
        val report = examples.single { it.id == "large-report" }.text
        assertTrue(plain.length > 30_000)
        assertEquals(10, report.lineSequence().count { it == "```kotlin" })
        assertTrue(report.contains("```mermaid") && report.contains("```plantuml"))
        for ((command, expected) in listOf("текст" to plain, "отчёт" to report)) {
            store.dispatch(AiStudioAction.EditDraft(chat.id, command))
            store.dispatch(AiStudioAction.Send)
            advance(3_000)
            val partial = store.liveText.value.text
            assertTrue(partial.isNotEmpty() && partial.length < expected.length && expected.startsWith(partial))
            advance(12_000)
            assertEquals(expected, assertIs<Reply>(store.screenState.value.messages.first()).text)
        }
        store.dispatch(AiStudioAction.EditDraft(chat.id, "рассуждение"))
        store.dispatch(AiStudioAction.Send)
        advance(3_000)
        val active = assertNotNull(store.screenState.value.live).steps.single().events.last().details
        assertTrue(active.isNotBlank() && active.length < reasoning.length && reasoning.startsWith(active))
        advance(180_000)
        val completed = assertIs<Reply>(store.screenState.value.messages.first())
        assertEquals(reasoning, completed.steps.first().events.last().details.trimEnd())
    }

    @Test
    fun codeDemoSendsOneLongCopyableSourceBlock() = runTest {
        val store = studio()
        val chat = store.screenState.value.chats.single { it.title == "Большие сообщения" }
        store.dispatch(AiStudioAction.OpenChat(chat.id))
        runCurrent()
        store.dispatch(AiStudioAction.EditDraft(chat.id, "код"))
        store.dispatch(AiStudioAction.Send)
        advance(20_000)
        val reply = assertIs<Reply>(store.screenState.value.messages.first())
        val source = reply.text.substringAfter("```kotlin\n").substringBefore("\n```")
        assertEquals(2_050, source.lines().size)
        assertTrue(source.startsWith("val events = listOf(\n    \"Событие 1."))
        assertTrue(source.lineSequence().last { it.startsWith("    ") }.startsWith("    \"Событие 2048."))
        assertTrue(source.endsWith("\n)"))
    }
}

private fun TestScope.awaitQuestions(store: DefaultAiStudioStore) {
    var elapsed = 0
    while (store.screenState.value.request !is Request.Questions && elapsed < 5_000) {
        advance(1)
        elapsed++
    }
    assertIs<Request.Questions>(store.screenState.value.request)
}
private fun TestScope.studio(data: TestPreferences = TestPreferences()): DefaultAiStudioStore =
    DefaultAiStudioStore(data, backgroundScope).also { runCurrent() }

private fun TestScope.sessionScope(): CoroutineScope {
    val context = backgroundScope.coroutineContext
    return CoroutineScope(context + SupervisorJob(context[Job]))
}

private fun TestScope.advance(ms: Long) {
    advanceTimeBy(ms)
    runCurrent()
}

private fun send(store: DefaultAiStudioStore) {
    store.dispatch(AiStudioAction.EditDraft(AuroraMock.InitialChat, "Проверь контракты сессий"))
    store.dispatch(AiStudioAction.Send)
}

private fun questions(store: DefaultAiStudioStore): Request.Questions =
    assertIs<Request.Questions>(store.screenState.value.request)

private fun TestScope.fillAnswers(store: DefaultAiStudioStore) {
    val panel = questions(store)
    store.dispatch(AiStudioAction.Choose(panel.id, "scope", "Контракты сессий"))
    store.dispatch(AiStudioAction.MoveQuestion(panel.id, 1))
    store.dispatch(AiStudioAction.SkipQuestion(panel.id))
    store.dispatch(AiStudioAction.Choose(panel.id, "report", "Да"))
    store.dispatch(AiStudioAction.SubmitAnswers(panel.id))
    runCurrent()
}

private class TestPreferences(initial: String = "") : PreferencesStore {
    val snapshot = MutableStateFlow(initial)

    override fun observeString(key: String, default: String) = snapshot
    override fun observeBoolean(key: String, default: Boolean) = flowOf(default)
    override fun observeLong(key: String, default: Long) = flowOf(default)
    override suspend fun putString(key: String, value: String) { snapshot.value = value }
    override suspend fun putBoolean(key: String, value: Boolean) = Unit
    override suspend fun putLong(key: String, value: Long) = Unit
    override suspend fun remove(key: String) { snapshot.value = "" }
}

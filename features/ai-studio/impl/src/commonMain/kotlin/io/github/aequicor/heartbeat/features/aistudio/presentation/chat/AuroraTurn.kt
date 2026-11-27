@file:Suppress("TooManyFunctions", "MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionWorkState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Access
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Activity
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Answer
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Delivery
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.DetailFormat
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Question
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Request
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Scenario
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Step
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.WorkEventKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One in-flight mock turn, advanced only by commands on its owning workspace. */
internal class AuroraTurn(
    private val prompt: User,
    private val store: DefaultAiStudioStore,
    private val scope: CoroutineScope,
    private val chatId: String,
    private val replyId: String,
) {
    var activity = Activity.Sending
        private set
    var request: Request? = null
        private set
    var reply: Reply? = null
        private set

    val workState: SessionWorkState
        get() = when (activity) {
            Activity.Idle -> SessionWorkState.Idle
            Activity.Waiting -> SessionWorkState.Demand
            Activity.Sending, Activity.Running, Activity.Stopping -> SessionWorkState.Working
        }

    private var scheduled: Job? = null
    private var generation = 0L
    private var timerTicks = 0
    private var remainingTicks = 0
    private var failedAnswerId: String? = null
    private var sendingAnswer: User? = null
    private var frame = 0
    private val thinkingMarkdown by lazy { WorkMarkdownStream() }
    private val demonstration =
        store.mockSessionId(chatId) == LargeMessageDemo.ChatId && prompt.scenario == Scenario.Stream
    private val answerSample by lazy {
        if (demonstration) LargeMessageDemo.answer(prompt.text) else AuroraMock.richSample
    }
    private val thinkingStream by lazy {
        val fragments = if (demonstration && prompt.text.trim().equals("рассуждение", ignoreCase = true)) {
            LargeMessageDemo.reasoning
        } else ThinkingFragments
        val text = fragments.joinToString("")
        MockTokenStream(if (demonstration) text else text.replaceFirst("повтора", "**повтора**"))
    }

    fun sendPrompt() {
        store.putMessage(chatId, prompt.copy(delivery = Delivery.Sending, error = null))
        publish(structural = true)
        after(SendDelay) {
            if (prompt.delivery != Delivery.Failed && prompt.scenario == Scenario.SendFailure) {
                val failed = prompt.copy(delivery = Delivery.Failed, error = "Локальная имитация потери соединения")
                store.putMessage(chatId, failed)
                store.finish(chatId)
            } else {
                store.putMessage(chatId, prompt.copy(delivery = Delivery.Sent, error = null))
                beginReply()
                receiveThinkingFragment()
            }
        }
    }

    fun resumeExecution(previous: Reply) {
        activity = Activity.Running
        reply = previous.copy(status = ReplyStatus.Working)
        startExecution(fail = false)
    }

    private fun beginReply() {
        activity = Activity.Running
        reply = Reply(
            replyId, ReplyTime, promptId = prompt.id,
            steps = listOf(readingStep()),
        )
        publish(structural = true)
    }

    private fun readingStep(): Step {
        val source = AuroraMock.inputs.first()
        return Step(
            id = "read", title = "Проверка исходных условий", details = "", status = StepStatus.Current,
            activeTitle = "Проверяю исходные условия",
            events = listOf(
                WorkEvent("read-notes", "Читаю рабочие заметки", "Готово · ${source.name}", source.content,
                    WorkEventKind.File, StepStatus.Done, "Файл: ${source.name}",
                    if (source.isMarkdown) DetailFormat.Markdown else DetailFormat.Plain),
                WorkEvent("scan", "Ищу обработчик остановки", "Готово", "Обработчик остановки найден",
                    WorkEventKind.Search, StepStatus.Done),
                WorkEvent(
                    "thinking", "Думает...", "", "", WorkEventKind.Thinking, StepStatus.Current,
                    detailsFormat = if (demonstration && prompt.text.trim().equals("текст", ignoreCase = true)) {
                        DetailFormat.Plain
                    } else DetailFormat.Markdown,
                ),
            ),
        )
    }

    private fun receiveThinkingFragment() {
        val batch = thinkingStream.next() ?: return afterReading()
        after(batch.delayMillis) {
            val current = checkNotNull(reply)
            val step = current.steps.last()
            val events = step.events.map { event ->
                if (event.kind != WorkEventKind.Thinking) event else {
                    val stream = if (event.detailsFormat == DetailFormat.Markdown) thinkingMarkdown else null
                    val summary = if (stream == null) batch.text else {
                        (event.details + batch.text).replace("**", "").trimEnd('*').removePrefix(event.summary)
                    }
                    event.appendDetails(batch.text, summary, stream)
                }
            }
            reply = current.copy(steps = current.steps.dropLast(1) + step.copy(events = events))
            if (batch.complete) afterReading() else {
                publish()
                receiveThinkingFragment()
            }
        }
    }
    private fun afterReading() {
        val result = AuroraMock.interimSample
        val current = checkNotNull(reply).finishCurrentSteps(StepStatus.Done)
        val finished = current.steps.last().copy(result = result)
        val pending = Step(
            id = "review", title = "Проверка сценариев диалога", details = "", status = StepStatus.Current,
            activeTitle = "Проверяю сценарии диалога",
        )
        reply = current.copy(interim = result, steps = current.steps.dropLast(1) + listOf(finished, pending))
        when (prompt.scenario) {
            Scenario.Stream, Scenario.Subagents -> startExecution(fail = false)
            Scenario.Optional -> askQuestions(timed = true)
            else -> askQuestions(timed = false)
        }
    }

    private fun askQuestions(timed: Boolean) {
        activity = Activity.Waiting
        val questions = if (timed) listOf(AuroraMock.optionalQuestion) else AuroraMock.questions
        request = Request.Questions("$replyId-questions", questions, timed = timed)
        publish(structural = true)
        if (timed) {
            timerTicks = requestTimerTicks(questions)
            remainingTicks = timerTicks
            tick()
        }
    }

    private fun tick() {
        after(TimerTick) {
            val questions = request as? Request.Questions ?: return@after
            remainingTicks--
            if (remainingTicks <= 0) {
                recordAnswers(questions, "Время ответа истекло", fail = false)
            } else {
                request = questions.copy(remaining = remainingTicks.toFloat() / timerTicks)
                publish()
                tick()
            }
        }
    }

    private fun requestTimerTicks(questions: List<Question>): Int {
        val milliseconds = questions.sumOf { question ->
            val response = if (question.freeText) TextResponseTime else ChoiceResponseTime
            val characters = question.title.length.toLong() + question.options.sumOf { it.length.toLong() }
            response + characters * ReadingTimePerCharacter
        }.coerceAtMost(MaxResponseTime)
        return ((milliseconds + TimerTick - 1) / TimerTick).toInt()
    }

    fun answer(action: AiStudioAction) {
        when (action) {
            is AiStudioAction.InteractWithRequest -> questionPanel(action.requestId) {}
            is AiStudioAction.Choose -> updateAnswer(action.requestId, action.questionId) { panel, answer ->
                if (action.option !in panel.question.options) return@updateAnswer answer
                val options = if (panel.question.multiple) {
                    val previous = answer.options
                    if (action.option in previous) previous - action.option else previous + action.option
                } else {
                    setOf(action.option)
                }
                answer.copy(options = options, skipped = false)
            }
            is AiStudioAction.EditAnswer -> updateAnswer(action.requestId, action.questionId) { panel, answer ->
                if (panel.question.freeText) answer.withText(action.text) else answer
            }
            is AiStudioAction.MoveQuestion -> questionPanel(action.requestId) { panel ->
                val next = (panel.index + action.offset).coerceIn(panel.questions.indices)
                display(panel.copy(index = next, error = null))
            }
            is AiStudioAction.SkipQuestion -> questionPanel(action.requestId) { panel ->
                if (!panel.question.required) display(
                    panel.copy(
                        index = (panel.index + 1).coerceAtMost(panel.questions.lastIndex),
                        answers = panel.answers + (panel.question.id to Answer(skipped = true)),
                        error = null,
                    ),
                )
            }
            is AiStudioAction.SubmitAnswers -> questionPanel(action.requestId, ::submit)
            is AiStudioAction.AnswerPermission -> permission(action)
            else -> Unit
        }
    }

    private fun questionPanel(id: String, block: (Request.Questions) -> Unit) {
        val panel = (request as? Request.Questions)?.takeIf { it.id == id } ?: return
        if (!panel.timed || panel.frozen) {
            block(panel)
            return
        }
        cancelScheduled()
        val frozen = panel.copy(frozen = true)
        request = frozen
        block(frozen)
        if (request === frozen) publish()
    }

    private fun Answer.withText(text: String) = copy(text = text, skipped = skipped && text.isBlank())

    private fun updateAnswer(
        id: String,
        questionId: String,
        transform: (Request.Questions, Answer) -> Answer,
    ) = questionPanel(id) { panel ->
        if (panel.question.id == questionId) {
            display(panel.copy(answers = panel.answers + (questionId to transform(panel, panel.answer)), error = null))
        }
    }

    private fun display(panel: Request.Questions) {
        request = panel
        publish()
    }

    private fun submit(panel: Request.Questions) {
        val missing = panel.questions.indexOfFirst { it.required && panel.answers[it.id]?.answered != true }
        if (missing >= 0) {
            display(panel.copy(index = missing, error = "Ответьте на обязательный вопрос"))
            return
        }
        val text = panel.questions.joinToString("\n\n") { question ->
            val answer = panel.answers[question.id] ?: Answer()
            val values = (answer.options + listOf(answer.text.trim())).filter(String::isNotEmpty)
            "${question.title}\n${if (values.isEmpty()) "Пропущено" else values.joinToString(" · ")}"
        }
        recordAnswers(panel, text, prompt.scenario == Scenario.DecisionFailure)
    }

    private fun recordAnswers(panel: Request.Questions, text: String, fail: Boolean) {
        request = null
        activity = Activity.Running
        val message = User(
            id = store.nextId(), time = ReplyTime, text = text, delivery = Delivery.Sending,
            answers = true, scenario = prompt.scenario, promptId = prompt.id,
        )
        if (panel.timed) cancelScheduled()
        sendAnswer(message, fail)
    }

    fun retryAnswer(message: User) {
        if (message.id != failedAnswerId || activity != Activity.Waiting) return
        failedAnswerId = null
        activity = Activity.Running
        sendAnswer(message.copy(delivery = Delivery.Sending, error = null), fail = false)
    }

    fun restoreAnswer(previous: Reply?, message: User) {
        activity = Activity.Running
        reply = previous?.copy(
            status = ReplyStatus.Working,
            steps = previous.steps.map { step ->
                if (step.status == StepStatus.Stopped) step.copy(status = StepStatus.Current) else step
            },
        )
        if (reply == null) beginReply()
        sendAnswer(message.copy(delivery = Delivery.Sending, error = null), fail = false)
    }

    private fun sendAnswer(message: User, fail: Boolean) {
        sendingAnswer = message
        store.putMessage(chatId, message)
        publish(structural = true)
        after(SendDelay) {
            sendingAnswer = null
            if (fail) {
                failedAnswerId = message.id
                activity = Activity.Waiting
                store.putMessage(chatId, message.copy(delivery = Delivery.Failed, error = "Ответы не доставлены"))
                publish(structural = true)
            } else {
                store.putMessage(chatId, message.copy(delivery = Delivery.Sent, error = null))
                askPermission()
            }
        }
    }

    private fun askPermission() {
        activity = Activity.Waiting
        if (prompt.access == Access.ReadOnly) {
            complete("Проверка завершена. Режим «Только чтение» оставляет итоги в сообщении.", writeResult = false)
        } else {
            request = Request.Permission(
                "$replyId-permission", "Разрешить запись отчёта?", "aurora-report.md",
                "Подготовить отчёт по проверенным сценариям", "Только этот демонстрационный шаг",
            )
            publish(structural = true)
        }
    }

    private fun permission(action: AiStudioAction.AnswerPermission) {
        if ((request as? Request.Permission)?.id != action.requestId) return
        request = null
        activity = Activity.Running
        if (action.allow) {
            startExecution(fail = prompt.scenario == Scenario.RunFailure)
        } else {
            complete("Запись отчёта отклонена. Проверка завершена, итоги доступны в сообщении.", writeResult = false)
        }
    }

    private fun startExecution(fail: Boolean) {
        val previous = checkNotNull(reply)
        val step = previous.steps.lastOrNull() ?: Step("report", "Подготовка отчёта", "", StepStatus.Current)
        val report = if (prompt.scenario == Scenario.Subagents) {
            WorkEvent(
                "review-subagent", "Subagent", "Выполняется", "Проверены переходы состояния диалога.",
                WorkEventKind.Check, StepStatus.Current, detailLabel = "Проверка переходов",
            )
        } else {
            WorkEvent("report", "Проверяю переходы состояния", "Выполняется", "Собираю результаты проверки.",
                WorkEventKind.Check, StepStatus.Current)
        }
        val running = step.copy(
            activeTitle = "Подготавливаю отчёт", status = StepStatus.Current,
            events = step.events.filterNot { it.id == report.id } + report,
        )
        reply = previous.copy(steps = previous.steps.dropLast(1) + running)
        publish(structural = true)
        if (prompt.scenario == Scenario.Stream) {
            stream()
        } else {
            after(StageDelay) {
                if (fail) {
                    val partial = checkNotNull(reply).copy(
                        text = "Сохранилась проверка условий. Подготовку отчёта можно повторить.",
                        status = ReplyStatus.Failed,
                    )
                    store.finish(chatId, partial.finishCurrentSteps(StepStatus.Failed))
                } else {
                    complete("Проверены повтор, остановка и восстановление диалога. Результаты доступны в отчёте.")
                }
            }
        }
    }

    private fun stream() {
        after(StreamDelay) {
            frame++
            val current = checkNotNull(reply)
            if (frame == AuroraMock.StreamFrames) {
                if (demonstration) complete(answerSample, writeResult = false)
                else complete("Проверено $frame этапов. Отчёт готов.")
                return@after
            }
            if (frame % StreamBatch == 0) {
                val batch = frame / StreamBatch
                val text = if (demonstration) {
                    val end = answerSample.length * batch / (AuroraMock.StreamFrames / StreamBatch)
                    answerSample.substring(0, end)
                } else {
                    val start = (batch - 1) * StreamFragment
                    val fragment = if (start < answerSample.length) {
                        answerSample.substring(start, minOf(start + StreamFragment, answerSample.length))
                    } else "\n\nПроверки ${frame - StreamBatch + 1}–$frame завершены."
                    current.text + fragment
                }
                val next = current.copy(text = text)
                reply = next
                store.streamText(chatId, next)
            }
            stream()
        }
    }

    fun stop() {
        if (activity == Activity.Stopping) return
        request = null
        activity = Activity.Stopping
        publish(structural = true)
        after(SendDelay) {
            val current = reply
            if (current == null) {
                store.putMessage(chatId, prompt.copy(delivery = Delivery.Failed, error = "Отправка остановлена"))
            }
            val answer = sendingAnswer
            if (answer != null) {
                val stopped = answer.copy(delivery = Delivery.Failed, error = "Отправка ответов остановлена")
                store.putMessage(chatId, stopped)
            }
            store.finish(
                chatId,
                current?.finishCurrentSteps(StepStatus.Stopped)?.copy(
                    status = ReplyStatus.Stopped,
                    text = current.text.ifBlank { "Работа остановлена. Полученные данные сохранены." },
                ),
            )
        }
    }

    private fun complete(text: String, writeResult: Boolean = true) {
        val current = checkNotNull(reply)
        val completed = current.finishCurrentSteps(StepStatus.Done).copy(
            status = ReplyStatus.Completed,
            text = text,
            result = if (writeResult && prompt.access != Access.ReadOnly) {
                AuroraMock.output(replyId, prompt.text, current.time)
            } else {
                null
            },
        )
        store.finish(chatId, completed)
    }

    private fun publish(structural: Boolean = false) = store.changed(chatId, structural)

    private fun after(milliseconds: Long, block: () -> Unit) {
        cancelScheduled()
        val expected = generation
        scheduled = scope.launch {
            delay(milliseconds)
            store.event { if (generation == expected) block() }
        }
    }

    private fun cancelScheduled() {
        generation++
        scheduled?.cancel()
        scheduled = null
    }

    private companion object {
        const val SendDelay = 360L
        const val StageDelay = 900L
        val ThinkingFragments = listOf("Выделяю условия повтора, ", "остановки и восстановления.")
        const val StreamDelay = 40L
        const val StreamBatch = 3
        const val StreamFragment = 24
        const val TimerTick = 250L
        const val ChoiceResponseTime = 60_000L
        const val TextResponseTime = 120_000L
        const val ReadingTimePerCharacter = 50L
        const val MaxResponseTime = 600_000L
        const val ReplyTime = "14:05"
    }
}

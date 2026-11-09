# AI Engine

Спецификация задаёт границу между приложением и конкретным ИИ-агентом: приложение работает с
фасадом, движком и сессией, а ACP, Koog и другие runtime подключаются отдельными модулями.

## Назначение

`AiEngineFacade` — единственная точка входа для фич. Он публикует каталог движков и их
агрегированные состояния, создаёт движки по требованию и освобождает их ресурсы.

`AiEngine` владеет соединением с агентом и всеми открытыми runtime-сессиями. `AiSession` владеет
одним разговором: отправляет промпт, принимает обновления, отменяет турн и отвечает на запросы
разрешений.

Приложение не знает, как агент запускается и какой протокол использует. ACP-адаптер и
модуль-провайдер конкретного агента — единственные места, где эта конкретика допустима.

## Модули

| Модуль | Содержание | Target |
| --- | --- | --- |
| `:ai:engine:api` | Публичные контракты и модели | KMP, все обязательные targets |
| `:ai:engine:impl` | `DefaultAiEngineFacade`, агрегация состояний | KMP, все обязательные targets |
| `:ai:engine:acp` | `AcpAdapter`, ACP Kotlin SDK, запуск процесса | JVM |
| `:ai:engines:deepseek-harness` | Ключ, провайдер и launch config DeepSeek Harness | JVM |
| `:ai:engines:koog` | Собственный легковесный агент | Не подключается до проверки ACP-сценария |

Зависимости направлены к контрактам:

```text
features/api          -> ai/engine/api
features/impl         -> ai/engine/api
ai/engine/impl        -> ai/engine/api, di/api
ai/engine/acp         -> ai/engine/api
ai/engines/<agent>    -> ai/engine/api, ai/engine/acp, di/api
platform/*            -> ai/engine/impl, ai/engines/<agent>
```

`features:ai-studio:api` объявляет зависимость `api(project(":ai:engine:api"))`, поэтому модели
движка видны её потребителям без повторных объявлений.

## Регистрация движков

```kotlin
class EngineKey(
    val id: String,
    val name: String,
    val description: String,
    val connection: EngineConnection,
) {
    // Identity = id. name, description и connection — отображаемые метаданные.
    override fun equals(other: Any?): Boolean =
        other is EngineKey && id == other.id

    override fun hashCode(): Int = id.hashCode()
}

enum class EngineConnection {
    ACP,
    EMBEDDED,
}

interface AiEngineProvider {
    val key: EngineKey

    // Не запускает процессы и не занимает ресурсы: фасад может создать кандидата и отбросить его.
    fun create(): AiEngine
}
```

Провайдер регистрируется в Metro через `@ContributesIntoSet(AppScope::class)`. Фасад получает
`Set<AiEngineProvider>` и отклоняет повторяющийся `EngineKey.id` при создании.

`EngineKey` не содержит секретов, путей к исполняемым файлам и учётных данных.

## Фасад

```kotlin
interface AiEngineFacade {
    val availableEngines: StateFlow<List<EngineKey>>

    // Снимок для UI, throttle-latest не дольше 500 мс.
    val engines: StateFlow<Map<EngineKey, EngineState>>

    fun getEngine(key: EngineKey): AiEngine

    suspend fun dispose(key: EngineKey)
}
```

`availableEngines` — каталог всех зарегистрированных движков независимо от подключения. `engines`
содержит запись для каждого движка каталога, включая `NotInitialized` для ещё не созданного.

`getEngine` возвращает движок по ключу из каталога: ключ, собранный вне провайдера, сопоставляется
по `id`, а возвращённый экземпляр содержит каталожный ключ. Создание идемпотентно и
потокобезопасно; повторный вызов возвращает тот же экземпляр. После `dispose(key)` движок
освобождает ресурсы, а следующий `getEngine` создаёт новый экземпляр; агрегированное состояние
возвращается в `NotInitialized`.

## Движок

```kotlin
interface AiEngine {
    val key: EngineKey

    val state: StateFlow<EngineState>

    // Набор известен после initialize; до подключения — пустой.
    val capabilities: StateFlow<EngineCapabilities>

    // Только открытые runtime-сессии.
    val sessions: StateFlow<List<SessionKey>>

    fun dispatch(event: EngineEvent)

    suspend fun listSessions(cursor: String? = null): SessionPage

    suspend fun createSession(
        config: SessionConfig,
        existing: SessionKey? = null,
    ): AiSession

    fun getSession(key: SessionKey): AiSession?

    suspend fun closeSession(key: SessionKey)

    suspend fun dispose()
}
```

`state` — источник истины о подключении. `sessions` содержит ключи открытых сессий; состояние
конкретной сессии читается у `AiSession.state`, копии в списке нет.

`getSession` возвращает `null`, если сессия закрыта, недоступна или неизвестна. `listSessions`
возвращает сессии, известные агенту, и требует запущенного соединения. `createSession(config)`
создаёт разговор, `createSession(config, existing)` открывает существующий. Если сессия уже
открыта, возвращается её runtime-экземпляр. Если агент не поддерживает повторное открытие,
операция завершается `AiEngineException.Unsupported`, а не создаёт новый разговор.

### Состояния движка

```kotlin
sealed interface EngineState {
    data object NotInitialized : EngineState
    data object Connecting : EngineState

    data class AuthorizationRequired(
        val authMethodId: String,
        val label: String,
    ) : EngineState

    data object Ready : EngineState

    data class Failed(val reason: String) : EngineState
}

sealed interface EngineEvent {
    data object Connect : EngineEvent
    data object Disconnect : EngineEvent

    data class AuthorizationCompleted(val result: AuthResult) : EngineEvent
}

sealed interface AuthResult {
    data object Success : AuthResult
    data object Cancelled : AuthResult
    data class Failure(val reason: String) : AuthResult
}
```

| Текущее состояние | `Connect` | `Disconnect` | `AuthorizationCompleted` |
| --- | --- | --- | --- |
| `NotInitialized` | `Connecting` | без изменений | без изменений |
| `Connecting` | без изменений | отмена `initialize`, остановка процесса, `NotInitialized` | без изменений |
| `AuthorizationRequired` | повторное подключение, `Connecting` | `NotInitialized` | `Success` — повторное подключение; `Cancelled` — `NotInitialized`; `Failure` — `Failed` |
| `Ready` | без изменений | закрытие сессий, остановка процесса, `NotInitialized` | без изменений |
| `Failed` | `Connecting` | `NotInitialized` | без изменений |

Успешная авторизация не переводит движок сразу в `Ready`: он завершает подключение и публикует
`Ready` только после `initialize`. `Disconnect` во время `Connecting` должен дождаться завершения
отмены: после него не остаётся работающего процесса.

### Возможности и ошибки

```kotlin
enum class EngineCapability {
    SESSION_LIST,
    SESSION_RESUME,
    SESSION_CLOSE,
}

data class EngineCapabilities(
    val supported: Set<EngineCapability> = emptySet(),
) {
    fun supports(capability: EngineCapability): Boolean = capability in supported
}

sealed class AiEngineException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unsupported(val capability: EngineCapability) : AiEngineException("...")
    class NotReady(val state: EngineState) : AiEngineException("...")
    class Protocol(val code: Int, message: String, cause: Throwable? = null) : AiEngineException(message, cause)
    class SessionBusy(val session: SessionKey) : AiEngineException("...")
    class SessionNotFound(val session: SessionKey) : AiEngineException("...")
}
```

Поддержка необязательных операций определяется результатом `initialize`, а не предположениями об
агенте. Вызов операции без capability завершается `Unsupported` до отправки запроса. Вызов при
неподключённом движке завершается `NotReady`.

## Сессия

```kotlin
interface AiSession {
    val key: SessionKey

    val state: StateFlow<SessionState>

    // Горячий поток: подписка до SendPrompt получает все обновления турна.
    val updates: Flow<SessionUpdate>

    fun dispatch(event: SessionEvent)
}

class SessionKey(
    val engine: EngineKey,
    val id: String,
    val title: String? = null,
    val workingDirectory: String? = null,
    val updatedAt: String? = null,
) {
    // Identity = engine.id + id. Изменение метаданных не меняет идентичность.
    override fun equals(other: Any?): Boolean =
        other is SessionKey && engine.id == other.engine.id && id == other.id

    override fun hashCode(): Int = 31 * engine.id.hashCode() + id.hashCode()
}

data class SessionConfig(val workingDirectory: String? = null)

data class SessionPage(
    val sessions: List<SessionKey>,
    val nextCursor: String? = null,
)
```

`SessionKey` из `listSessions` содержит метаданные, известные агенту. Сопоставление с сохранённой
историей выполняется по `engine.id + id`, а не по заголовку.

### Состояния и события сессии

```kotlin
sealed interface SessionState {
    data object Idle : SessionState
    data object Running : SessionState

    data class AwaitingPermission(val prompt: PermissionPrompt) : SessionState

    data object Unavailable : SessionState
    data object Closed : SessionState

    data class Failed(val reason: String) : SessionState
}

data class PermissionPrompt(
    val requestId: String,
    val title: String,
    val options: List<PermissionOption>,
)

data class PermissionOption(
    val id: String,
    val name: String,
    val kind: PermissionOptionKind,
)

enum class PermissionOptionKind {
    ALLOW_ONCE,
    ALLOW_ALWAYS,
    REJECT_ONCE,
    REJECT_ALWAYS,
    UNKNOWN,
}

sealed interface SessionEvent {
    data class SendPrompt(val text: String) : SessionEvent
    data object Cancel : SessionEvent
    data class AnswerPermission(val requestId: String, val optionId: String) : SessionEvent
}
```

| Текущее состояние | `SendPrompt` | `Cancel` | `AnswerPermission` |
| --- | --- | --- | --- |
| `Idle` | `Running` | без изменений | без изменений |
| `Running` | `SessionBusy` | запрос отмены; `Idle` после подтверждения агентом | без изменений |
| `AwaitingPermission` | `SessionBusy` | ответ `cancelled`, `Idle` | `Running` для совпавшего `requestId` |
| `Unavailable` | `SessionBusy` | без изменений | без изменений |
| `Closed` | `SessionBusy` | без изменений | без изменений |
| `Failed` | `Running` (повторная попытка) | `Idle` | без изменений |

`AwaitingPermission` — подсостояние выполняющегося турна: после ответа сессия возвращается в
`Running`, а не в `Idle`. `requestId` формирует адаптер; протокольный идентификатор запроса
наружу не выходит. Адаптер обязан завершить ожидание разрешения при `Cancel`, `Disconnect`,
`closeSession` и `dispose`, иначе агент остаётся заблокированным на собственном запросе.

`Failed` фиксирует причину последнего сбоя турна. Повторная отправка промпта переводит сессию в
`Running` и очищает причину.

### Обновления

```kotlin
sealed interface SessionUpdate {
    data class AgentText(val text: String) : SessionUpdate
    data class AgentThought(val text: String) : SessionUpdate
    data class ToolCall(val id: String, val title: String, val status: ToolCallStatus) : SessionUpdate
    data class Completed(val stopReason: String?) : SessionUpdate
    data class Failed(val message: String) : SessionUpdate
}

enum class ToolCallStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
}
```

Обновления не проходят через агрегацию фасада: троттлинг применяется только к состояниям движков.
Поток горячий, поэтому подписка UI до `SendPrompt` получает все чанки турна; потерянные обновления
не восстанавливаются из состояния.

## ACP-адаптер

`:ai:engine:acp` — единственный модуль, который зависит от `com.agentclientprotocol:acp`
(версия в `gradle/libs.versions.toml`). Адаптер реализует `AiEngine` и не содержит логики
конкретного агента.

```kotlin
data class AcpLaunchSpec(
    val executable: String,
    val arguments: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
    val workingDirectory: String? = null,
)
```

Конфигурация подключения вычисляется при `Connect`, поэтому настройки и авторизация, изменённые
после создания движка, применяются к следующему подключению.

| Heartbeat | ACP |
| --- | --- |
| `EngineEvent.Connect` | запуск процесса, `initialize` |
| `createSession(config)` | `session/new` |
| `createSession(config, existing)` | `session/resume` при capability, иначе `Unsupported` |
| `listSessions()` | `session/list` при capability, иначе `Unsupported` |
| `SessionEvent.SendPrompt` | `session/prompt` |
| `SessionEvent.Cancel` | `session/cancel` |
| `SessionEvent.AnswerPermission` | ответ на `session/request_permission` |
| `closeSession()` | `session/close` при capability, иначе локальное закрытие runtime-сессии |
| `EngineEvent.Disconnect` | закрытие соединения и завершение процесса |

Правила адаптера:

- capability читаются из ответа `initialize`; отсутствующая capability даёт `Unsupported`;
- `session/resume` не воспроизводит историю, поэтому отображаемую историю хранит Heartbeat;
- `session/load` не используется в первом срезе;
- разрешение отвечается из callback `ClientSessionOperations.requestPermissions` через
  `CompletableDeferred`, а `requestId` генерирует адаптер;
- stderr дочернего процесса непрерывно вычитывается, иначе буфер пайпа блокирует агента;
- на Windows исполняемый файл `.cmd` запускается через `cmd.exe /c`;
- `Disconnect`, `closeSession` и `dispose` завершают процесс и его дерево.

## DeepSeek Harness

Сервер ACP поставляет профиль-бандл `dsh-acp-server`: он устанавливается в профиль `acp` и
запускает `dsh --profile acp`. Поэтому провайдер поддерживает три формы запуска:

```text
dsh-acp-server                 # рекомендованная: сам поднимает профиль и бутстрапит его
dsh --profile acp              # профиль уже установлен
pnpm dsh --profile acp         # запуск из собранного репозитория Harness
```

Если профиль отсутствует или процесс не стартует, движок публикует `Failed` с диагностикой и
командой установки. Провайдер не устанавливает профиль автоматически.

`authenticate` у этого сервера возвращает успех и не проверяет наличие ключей модели. Поэтому
авторизация выполняется отдельной auth-фичей или уже настроенным окружением Harness: при
отсутствии конфигурации движок публикует `AuthorizationRequired`, а после настройки выполняется
повторный `Connect`.

Секреты передаются только через `AcpLaunchSpec.environment` и защищённое хранилище; они не
попадают в `EngineKey`, `EngineState`, `SessionKey` и журналы.

Сервер поддерживает `session/resume`, `session/list`, `session/close` и `session/load`, но
Heartbeat хранит отображаемую историю сам: после перезапуска приложения диалог должен
открываться без запуска агента.

## Хранение истории

Историю хранит `features:ai-studio:impl`: модули `:ai:*` не зависят от `storage` и не владеют
схемой. Текущая схема `chat` содержит только `id` и `title`; для сопоставления с сессиями агента
нужны `engine_id`, `acp_session_id`, `working_directory`, `updated_at` и таблица сообщений. Это
изменение схемы входит в следующий срез и не блокирует подключение и отправку промпта.

## Границы первого среза

Входит: подключение DeepSeek Harness, создание и повторное открытие сессии, текстовый промпт,
получение ответа, отмена, запрос разрешения, корректная утилизация ресурсов, интерфейс
`AiEngineFacade → AiEngine → AiSession`.

Не входит: Koog, собственный JSON-RPC транспорт, `AcpConnectionManager`, `SessionRegistry`,
`EngineCoordinator`, `session/load`, вложения и изображения, режимы и конфигурация сессии,
хранение истории, изменения UI AI Studio.

## Требования и проверка

| ID | Требование | Источник | Критерий проверки |
| --- | --- | --- | --- |
| HB-AIE-001 | Приложение должно работать с агентом только через `AiEngineFacade → AiEngine → AiSession`; модули `:ai:*` не содержат типов фич, Compose и Decompose. | Запрос владельца от 9 ноября 2026 | `architectureCheck` пропускает только рёбра из таблицы модулей; Konsist `modulePackagesMatchTheGradleModule` проверяет пакеты `:ai..`. |
| HB-AIE-002 | Каждый провайдер должен регистрироваться в Metro и попадать в каталог фасада; повторяющийся `EngineKey.id` должен отклоняться при создании фасада. | Запрос владельца от 9 ноября 2026 | Unit-тест фасада с двумя провайдерами одного `id` завершается ошибкой; тест с двумя разными `id` публикует оба движка. |
| HB-AIE-003 | Идентичность `EngineKey` и `SessionKey` должна определяться только `id` (для сессии — `engine.id + id`); метаданные не влияют на равенство и хеш-код. | Запрос владельца от 9 ноября 2026 | Unit-тесты: ключи с разными `name`/`title` равны и дают одинаковый хеш; ключи с разными `id` не равны. |
| HB-AIE-004 | `AiEngineFacade.engines` должен публиковать последнее состояние каждого движка не реже чем раз в 500 мс, без потери финального состояния. | Запрос владельца от 9 ноября 2026 | Тест с виртуальным временем: серия быстрых переходов публикует последнее состояние; интервал между публикациями не меньше 500 мс. |
| HB-AIE-005 | `getEngine` должен быть идемпотентным и потокобезопасным, а `dispose(key)` — освобождать ресурсы и возвращать движок в `NotInitialized`. | Запрос владельца от 9 ноября 2026 | Тест: два последовательных вызова возвращают один экземпляр; после `dispose` создаётся новый, старый получил `dispose`. |
| HB-AIE-006 | Движок должен следовать таблице состояний: `Connect` идемпотентен, `Disconnect` во время `Connecting` отменяет подключение и не оставляет процесса, успешная авторизация не публикует `Ready` без `initialize`. | Запрос владельца от 9 ноября 2026 | Unit-тесты с fake-движком: двойной `Connect` не создаёт второе соединение; `Disconnect` в `Connecting` даёт `NotInitialized`; `AuthorizationCompleted(Success)` даёт `Connecting`. |
| HB-AIE-007 | Сессия должна следовать таблице состояний: `SendPrompt` только из `Idle` и `Failed`, иначе `SessionBusy`; ответ на разрешение возвращает в `Running`; `Cancel` завершает турн и ожидание разрешения. | Запрос владельца от 9 ноября 2026 | Unit-тесты сессии: параллельный промпт даёт `SessionBusy`; после `AnswerPermission` состояние `Running`; `Cancel` в `AwaitingPermission` завершает ожидание. |
| HB-AIE-008 | Вызов операции без capability должен завершаться `Unsupported` до отправки запроса; вызов при неподключённом движке — `NotReady`. | Запрос владельца от 9 ноября 2026 | Unit-тесты: движок без `SESSION_LIST` бросает `Unsupported`; `listSessions` в `NotInitialized` бросает `NotReady`. |
| HB-AIE-009 | ACP-адаптер должен сопоставлять операции по таблице и определять поддержку по `initialize`, а не по типу агента. | Запрос владельца от 9 ноября 2026 | Интеграционный тест со stub-агентом по ACP: `initialize` без `session.resume` даёт `Unsupported` для повторного открытия; `initialize` с capability открывает сессию. |
| HB-AIE-010 | ACP-адаптер должен запускать процесс по `AcpLaunchSpec`, вычитывать stderr и завершать процесс с деревом при `Disconnect` и `dispose`. | Запрос владельца от 9 ноября 2026 | Интеграционный тест завершает stub-агент после `Disconnect`; `AcpProcess` вычитывает stderr в отдельной корутине и ограничивает диагностику 4000 символами. |
| HB-AIE-011 | Провайдер DeepSeek Harness должен публиковать ключ `acp.deepseek-harness`, вычислять launch config при `Connect` и не помещать секреты в ключ и состояние. | Запрос владельца от 9 ноября 2026 | Тест: ключ не содержит значений окружения; изменение настроек после создания движка применяется при следующем `Connect`. |
| HB-AIE-012 | Документация должна описывать архитектуру AI Engine, а навигация MkDocs — включать страницу. | Запрос владельца от 9 ноября 2026 | `mkdocs build --strict` без предупреждений; страница присутствует в `nav`. |

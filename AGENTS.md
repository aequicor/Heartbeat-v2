# Heartbeat Lab

Heartbeat Lab — студия на Kotlin Multiplatform (KMP) для разработки и исследований в проектах с помощью ИИ.

# Навигация

- Gradle-модули перечислены в [settings.gradle.kts](settings.gradle.kts).
- Общий код KMP лежит в `src/commonMain`, платформенный — в наборах вроде `src/jvmMain`.
- `platform/` — точки входа Android, iOS и desktop. На Windows, macOS и Linux desktop использует JVM и Compose Multiplatform.
- Общие модули поддерживают Kotlin/Native для iOS, macOS, Linux и Windows. Для Chromium используется Kotlin/Wasm. [Таргеты](docs/specs/platforms.md)
- `features/<имя>/` — код одной фичи. `shell/` связывает фичи, `navigation/` задаёт контракты и адаптеры навигации.
- `core/` — общие технические сервисы. `storage/database/` — SQL-схемы фич и миграции общей БД.
- `designsystem/` — токены, тема и UI-компоненты. `di/` — общие определения для внедрения зависимостей.
- `buildSrc/` — плагины Gradle. `architecture-tests/` — проверки границ модулей и слоёв.
- `docs/specs/` — спецификации проекта.

Перед изменением документации, точек входа или сборки прочитай инструкции соответствующей области.
[Документация](docs/AGENTS.md) · [Платформы](platform/AGENTS.md) · [Gradle](buildSrc/AGENTS.md)

# Архитектура фича-модуля

Доменную область проектирует оператор. Он определяет модели, бизнес-правила, сценарии, интеракторы, контракты репозиториев и источников данных, ошибки и зависимости между компонентами. Сохраняй согласованные имена, смысл и контракты как в описаниях, так и в коде. Если для задачи нужно добавить, переименовать, изменить или удалить что-либо в доменной области, объясни причину и получи явное согласие оператора на конкретное изменение.

В пакете `domain` модуля `impl/` размещай компоненты по назначению.

| Пакет | Содержимое |
| --- | --- |
| `model/` | Доменные модели |
| `interactor/` | Интеракторы |
| `usecase/` | Сценарии использования. Имя интерфейса заканчивается на `UseCase`, имя реализации — на `UseCaseImpl`. |
| `repository/` | Интерфейсы репозиториев |
| `datasource/` | Интерфейсы источников данных |
| `exception/` | Доменные ошибки |

Каждому классу верхнего уровня отводи отдельный файл `.kt`. Вложенные классы и методы могут находиться внутри класса. В том же файле допустимы функции, в том числе функции расширения, свойства и константы верхнего уровня.

В Kotlin-коде и сэмплах оставляй одну пустую строку после открывающей скобки тела класса или интерфейса. Отделяй методы друг от друга и от соседних свойств одной пустой строкой. Аннотацию ставь непосредственно над объявлением. [Настройки форматирования](.editorconfig)

Направление зависимостей в коде — `ui (Compose) → presentation (Decompose/Store) → domain (interactor/usecase) ← data (datasource)`.

В классической схеме с репозиторием контракт доступа к данным на границе `domain` и `data` задаёт `Repository`. В Heartbeat эту роль выполняет `DataSource`. Объявляй его интерфейс в `domain/datasource/`, а реализацию размещай в `data/datasource/`. Доменный компонент получает источник данных через DI и вызывает его методы через доменный интерфейс. Добавляй репозиторий только тогда, когда его роль предусмотрена согласованной доменной моделью.

- Через `api/` фича предоставляет другим модулям модели данных, навигацию, проверки доступности и исключения публичных контрактов. Юзкейсы размещай в `impl/domain/usecase/`. Публичные контракты фичи не должны ссылаться на Store или его типы.
- Для проверки доступности фичи обычно достаточно метода `isAvailable()` без параметров. Реализация сама читает фича-флаги и возвращает `true`, если фича доступна.
- На границе `presentation → domain` каждый вызов UseCase или Interactor обязательно выполняй внутри явного `withContext(defaultDispatcher)` на стороне `presentation`. Default-диспетчер передавай через DI. Внутри самого `presentation` на него также можно выносить ресурсоёмкие вычисления, например сортировку большого списка или построение дерева для экрана.
- В слое `data` размещай работу с сетью, базой данных и другими платформенными средствами вне UI.
- Каждый запрос к бэкенду и каждую операцию БД выполняй внутри явного `withContext(ioDispatcher)` в реализации API или DAO. IO-диспетчер предоставляет платформенный граф DI. Принимай его в конструкторе как `CoroutineDispatcher` без значения по умолчанию.
- В `data/api/` размещай интерфейсы взаимодействия с бэкендом, в `data/dto/` — модели запросов и ответов.
- В `data/dao/` размещай интерфейсы доступа к базе данных, в `data/entity/` — модели записей БД.
- DTO и Entity обычно помечай аннотацией `@Serializable` из `kotlinx.serialization`.
- Для каждого DTO, включая DTO запросов и ответов, объявляй в его файле оба маппера. `toDomain` переводит DTO в соответствующую доменную модель или значение, `toDto` выполняет обратное преобразование.
- Если для Entity нужен маппинг между записью БД и доменной моделью, объявляй в файле Entity оба маппера — `toDomain` и `toEntity`.

Каждый метод, из которого исключение по контракту может выйти к вызывающему коду, помечай `@Throwable(...)` и перечисляй типы этих исключений через `::class`. Если метод объявлен в интерфейсе, ставь аннотацию на саму декларацию. Для `suspend`-метода, который передаёт отмену вызывающей корутине, включай `CancellationException::class`.

Для записи `@Throwable(...)` импортируй стандартную аннотацию Kotlin как `import kotlin.Throws as Throwable`. Если в том же файле нужен базовый тип исключений, пиши `kotlin.Throwable`.

Все операции UseCase и Interactor возвращают `kotlin.Result<T>`, в том числе `Result<Unit>` для операций без значения. Ошибки передавай из `domain` через `Result.failure`. При отмене корутины пробрасывай исходную `CancellationException` — это единственное исключение, которое может выйти из домена. Сразу после `runCatching` вызывай `.rethrowCancellation()`, до обработки или возврата результата. Для таких `suspend`-операций в `@Throwable` указывай только `CancellationException::class`.

Тип ошибки, который вызывающий модуль распознаёт в `Result.failure`, объявляй в `api/exception/`. Внутренние доменные ошибки размещай в `impl/domain/exception/`. В реализации DataSource преобразуй известные ошибки транспорта и БД в ошибки контракта, сохраняя исходную причину в `cause`. UseCase или Interactor, вызывающий такой DataSource, отвечает за возврат ошибки в `Result`.

| Ситуация при обновлении аккаунта | Как обработать |
| --- | --- |
| Источник временно недоступен | При первой загрузке покажи `Error` с кнопкой «Повторить». Если профиль уже виден, оставь его в `Loaded` и покажи внутри него `RefreshBanner` с сообщением и той же кнопкой. |
| Аккаунт не найден | Покажи полноэкранный `Error` с текстом «Аккаунт не найден» без кнопки повтора. |
| Операция отменена | Передай `CancellationException` вызывающей корутине. Отмена завершает работу без сообщения об ошибке и повторного запроса. |

Организуй `presentation` по областям UI (BLoC). Каждой области выделяй свой плоский пакет. Размещай в нём рядом Store, его реализацию, фабрику, Action и State, каждый в своём файле. Например, `AccountStore`, `AccountScreenAction` и `AccountScreenState` лежат в `impl/presentation/account/`. Адаптер той же области лежит в `presentation/decompose/account/`.

Каждая область `impl/presentation/` может предоставлять готовую часть экрана со своим состоянием и действиями. Эти части доступны независимо, без корневого композитора в `impl/presentation/`.

Способ сборки экрана выбирает Decompose. Он может передать UI одну часть напрямую или объединить несколько частей в компонент, если этого требует экран.

Store — легковесный аналог компонента Decompose. Состояние доступно снаружи для чтения через `val state: StateFlow<State>`. Единственный внешний метод Store — `suspend fun dispatch(action: Action)`. Все остальные методы и изменяемое состояние объявляй `private`. Первую загрузку также запускай действием через `dispatch`. Действия каждого экрана объявляй в отдельном файле как `sealed interface`. Каждый вариант обозначает событие и содержит данные, необходимые Store для его обработки.

В `ui/compose/` отображай `State` и передавай действия в `dispatch`. Адаптер Decompose создаёт `CoroutineScope` на время жизни компонента, передаёт его через фабрику в Store и отменяет при уничтожении компонента. Store выполняет обработчики действий в этом scope. Метод `dispatch` ждёт завершения обработчика и передаёт ему отмену вызывающей корутины.

Иерархия классов состояния должна отражать визуальную структуру экрана. На верхнем уровне описывай взаимоисключающие варианты всего экрана. Модель отдельного блока можно объявить вложенным классом того состояния, в котором этот блок виден.

В модели кнопки храни подпись и типизированное действие. При нажатии Compose передаёт в `dispatch` компонента событие `AccountScreenAction.ButtonClicked` с действием кнопки. Компонент вызывает `dispatch` Store в той же корутине.

Соблюдай все принципы SOLID. Разделяй большие интерфейсы на узкоспециализированные, чтобы каждый потребитель зависел только от нужных ему операций. Каждый класс должен реализовывать интерфейс. К имени интерфейса добавляй суффикс `Impl` для имени класса реализации, например `AccountStoreImpl`. Предоставляй реализацию потребителям через DI по этому интерфейсу. Граф зависимостей собирает платформенная точка входа. [Спецификация архитектуры](docs/specs/architecture.md)

## Примеры Kotlin-кода

**¬** `feature/api/model/Account.kt`

```kotlin
public data class Account(
    public val id: String,
    public val displayName: String,
)
```

**¬** `feature/api/navigation/Router.kt`

```kotlin
public interface Router {

    public fun openAccountChat(account: Account)
}
```

**¬** `feature/api/checker/AccountChatAvailabilityChecker.kt`

```kotlin
public fun interface AccountChatAvailabilityChecker {

    public fun isAvailable(): Boolean
}
```

**¬** `feature/api/exception/AccountRefreshException.kt`

```kotlin
public sealed class AccountRefreshException(cause: Throwable?) : Exception(cause) {

    public class Unavailable(cause: Throwable) : AccountRefreshException(cause)

    public class NotFound(cause: Throwable? = null) : AccountRefreshException(cause)
}
```

**¬** `feature/impl/data/api/AccountServiceApi.kt`

```kotlin
import kotlin.Throws as Throwable

internal interface AccountServiceApi {

    @Throwable(AccountServiceException::class, CancellationException::class)
    suspend fun getAccount(request: RequestAccountDto): AccountDto

    @Throwable(AccountServiceException::class, CancellationException::class)
    suspend fun createAccount(request: CreateAccountRequestDto): AccountDto
}
```

**¬** `feature/impl/data/api/AccountServiceApiImpl.kt`

```kotlin
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class AccountServiceApiImpl(
    private val client: HttpClient,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
) : AccountServiceApi {

    @Throwable(AccountServiceException::class, CancellationException::class)
    override suspend fun getAccount(request: RequestAccountDto): AccountDto = withContext(ioDispatcher) {
        accountRequest {
            client.get {
                url { appendPathSegments("accounts", request.accountId, encodeSlash = true) }
            }.body<AccountDto>()
        }
    }

    @Throwable(AccountServiceException::class, CancellationException::class)
    override suspend fun createAccount(request: CreateAccountRequestDto): AccountDto = withContext(ioDispatcher) {
        accountRequest {
            client.post("accounts") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<AccountDto>()
        }
    }

    @Throwable(AccountServiceException::class, CancellationException::class)
    private suspend fun <T> accountRequest(call: suspend () -> T): T =
        TODO("Выполнить запрос, преобразовать сбой в AccountServiceException и передать отмену вызывающей корутине")
}
```

Для этого примера платформа предоставляет графу Metro клиент Ktor с базовым URL бэкенда, JSON-сериализацией и `expectSuccess = true`. Через параметр конструктора `AccountServiceApiImpl` получает готовый клиент. [GET и POST в Ktor](https://ktor.io/docs/client-requests.html)

**¬** `feature/impl/data/dto/RequestAccountDto.kt`

```kotlin
import kotlinx.serialization.Serializable

@Serializable
internal data class RequestAccountDto(val accountId: String) {

    companion object {

        fun toDto(accountId: String): RequestAccountDto =
            TODO("Сформировать запрос на получение аккаунта по идентификатору")
    }
}

internal fun RequestAccountDto.toDomain(): String =
    TODO("Извлечь идентификатор аккаунта из запроса на получение")
```

**¬** `feature/impl/data/dto/CreateAccountRequestDto.kt`

```kotlin
import kotlinx.serialization.Serializable

@Serializable
internal data class CreateAccountRequestDto(val displayName: String) {

    companion object {

        fun toDto(displayName: String): CreateAccountRequestDto =
            TODO("Сформировать запрос на создание аккаунта по отображаемому имени")
    }
}

internal fun CreateAccountRequestDto.toDomain(): String =
    TODO("Извлечь отображаемое имя из запроса на создание аккаунта")
```

**¬** `feature/impl/data/dto/AccountDto.kt`

```kotlin
import kotlinx.serialization.Serializable

@Serializable
internal data class AccountDto(val id: String, val displayName: String)

internal fun AccountDto.toDomain(): Account =
    TODO("Преобразовать ответ бэкенда в доменную модель аккаунта")

internal fun Account.toDto(): AccountDto =
    TODO("Преобразовать доменную модель аккаунта в DTO бэкенда")
```

**¬** `feature/impl/data/dao/AccountDao.kt`

```kotlin
import kotlin.Throws as Throwable

internal interface AccountDao {

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun insert(account: AccountEntity)

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun findById(id: String): AccountEntity?

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun update(account: AccountEntity)

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun delete(id: String)

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun upsert(account: AccountEntity)
}
```

**¬** `feature/impl/data/dao/AccountDaoImpl.kt`

```kotlin
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class AccountDaoImpl(
    private val connection: DatabaseConnection,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
) : AccountDao {

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun insert(account: AccountEntity): Unit = withContext(ioDispatcher) {
        withQueries { insertAccount(account.id, account.displayName) }
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun findById(id: String): AccountEntity? = withContext(ioDispatcher) {
        withQueries { selectAccountById(id, ::AccountEntity).executeAsOneOrNull() }
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun update(account: AccountEntity): Unit = withContext(ioDispatcher) {
        withQueries { updateAccount(displayName = account.displayName, id = account.id) }
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun delete(id: String): Unit = withContext(ioDispatcher) {
        withQueries { deleteAccount(id) }
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun upsert(account: AccountEntity): Unit = withContext(ioDispatcher) {
        withQueries { upsertAccount(account.id, account.displayName) }
    }

    @Throwable(DaoException::class, CancellationException::class)
    private suspend fun <T> withQueries(operation: AccountQueries.() -> T): T =
        TODO("Выполнить операцию с AccountQueries(connection.driver), преобразовать ошибку БД и передать отмену")
}
```

DAO получает из графа `DatabaseConnection`. В сэмпле `AccountQueries` обозначает класс, который SQLDelight генерирует из файла `Account.sq`.

**¬** `feature/impl/data/entity/AccountEntity.kt`

```kotlin
import kotlinx.serialization.Serializable

@Serializable
internal data class AccountEntity(val id: String, val displayName: String)

internal fun AccountEntity.toDomain(): Account =
    TODO("Преобразовать запись БД в доменную модель аккаунта")

internal fun Account.toEntity(): AccountEntity =
    TODO("Преобразовать доменную модель аккаунта в запись БД")
```

**¬** `feature/impl/data/datasource/AccountDataSourceImpl.kt`

```kotlin
import kotlin.Throws as Throwable

@Inject
internal class AccountDataSourceImpl(
    private val api: AccountServiceApi,
    private val dao: AccountDao,
) : AccountDataSource {

    @Throwable(AccountRefreshException::class, CancellationException::class)
    override suspend fun refreshName(accountId: String): String =
        TODO("Получить актуальное имя аккаунта у бэкенда, сохранить в БД и вернуть в домен")
}
```

**¬** `feature/impl/data/datasource/ChatDataSourceImpl.kt`

```kotlin
@Inject
internal class ChatDataSourceImpl(private val session: Session) : ChatDataSource {

    override fun send(text: String): Unit = TODO("Передать сообщение в сессию агента")

    override fun cancel(): Unit = TODO("Отменить текущий запрос в сессии агента")
}
```

**¬** `feature/impl/domain/datasource/AccountDataSource.kt`

```kotlin
import kotlin.Throws as Throwable

internal interface AccountDataSource {

    @Throwable(AccountRefreshException::class, CancellationException::class)
    suspend fun refreshName(accountId: String): String
}
```

**¬** `feature/impl/domain/datasource/ChatDataSource.kt`

```kotlin
internal interface ChatDataSource {

    fun send(text: String)

    fun cancel()
}
```

**¬** `feature/impl/domain/util/Result.kt`

```kotlin
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Throwable(CancellationException::class)
internal fun <T> Result<T>.rethrowCancellation(): Result<T> {
    val failure = exceptionOrNull()
    if (failure is CancellationException) throw failure
    return this
}
```

**¬** `feature/impl/domain/interactor/ProjectsSessionsInteractor.kt`

```kotlin
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface ProjectsSessionsInteractor {

    @Throwable(CancellationException::class)
    suspend fun search(query: String): Result<List<ProjectTree>>
}
```

**¬** `feature/impl/domain/usecase/RefreshAccountNameUseCase.kt`

```kotlin
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal fun interface RefreshAccountNameUseCase {

    @Throwable(CancellationException::class)
    suspend operator fun invoke(accountId: String): Result<String>
}
```

**¬** `feature/impl/domain/usecase/RefreshAccountNameUseCaseImpl.kt`

```kotlin
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class RefreshAccountNameUseCaseImpl(private val accounts: AccountDataSource) : RefreshAccountNameUseCase {

    @Throwable(CancellationException::class)
    override suspend fun invoke(accountId: String): Result<String> =
        runCatching { accounts.refreshName(accountId) }.rethrowCancellation()
}
```

**¬** `feature/impl/presentation/account/AccountScreenState.kt`

```kotlin
public sealed interface AccountScreenState {

    public data object Loading : AccountScreenState

    public data class Loaded(
        val displayName: String,
        val refreshButton: Button = Button(label = "Обновить", action = Button.AccountAction.Refresh),
        val refreshBanner: RefreshBanner? = null,
    ) : AccountScreenState {

        public data class Button(val label: String, val action: AccountAction) {

            public enum class AccountAction {

                Refresh,
            }
        }

        public data class RefreshBanner(val message: String)
    }

    public data class Error(val message: String, val showRetry: Boolean = false) : AccountScreenState
}
```

**¬** `feature/impl/presentation/account/AccountScreenAction.kt`

```kotlin
public sealed interface AccountScreenAction {

    public data object Load : AccountScreenAction

    public data class ButtonClicked(
        val buttonAction: AccountScreenState.Loaded.Button.AccountAction,
    ) : AccountScreenAction

    public data class ThemeChanged(val isDark: Boolean) : AccountScreenAction

    public data class OpenChat(val account: Account) : AccountScreenAction
}
```

**¬** `feature/impl/presentation/account/AccountStore.kt`

```kotlin
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

public interface AccountStore {

    public val state: StateFlow<AccountScreenState>

    @Throwable(CancellationException::class)
    public suspend fun dispatch(action: AccountScreenAction)
}
```

**¬** `feature/impl/presentation/account/AccountStoreFactory.kt`

```kotlin
import kotlinx.coroutines.CoroutineScope

public fun interface AccountStoreFactory {

    public fun create(accountId: String, scope: CoroutineScope): AccountStore
}
```

**¬** `feature/impl/presentation/account/AccountStoreImpl.kt`

```kotlin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal class AccountStoreImpl(
    private val accountId: String,
    private val refreshAccountName: RefreshAccountNameUseCase,
    private val scope: CoroutineScope,
    private val defaultDispatcher: CoroutineDispatcher,
) : AccountStore {

    private val refreshMutex = Mutex()
    private val mutableState = MutableStateFlow<AccountScreenState>(AccountScreenState.Loading)

    override val state: StateFlow<AccountScreenState> = mutableState.asStateFlow()

    @Throwable(CancellationException::class)
    override suspend fun dispatch(action: AccountScreenAction) {
        currentCoroutineContext().ensureActive()
        val operation = scope.async { handle(action) }
        try {
            operation.await()
        } catch (cancelled: CancellationException) {
            operation.cancel(cancelled)
            throw cancelled
        }
    }

    @Throwable(CancellationException::class)
    private suspend fun handle(action: AccountScreenAction) {
        when (action) {
            AccountScreenAction.Load -> refresh()
            is AccountScreenAction.ButtonClicked -> when (action.buttonAction) {
                AccountScreenState.Loaded.Button.AccountAction.Refresh -> refresh()
            }
            is AccountScreenAction.ThemeChanged -> TODO("Применить выбранную тему")
            is AccountScreenAction.OpenChat -> TODO("Проверить доступность и запросить переход в чат аккаунта")
        }
    }

    @Throwable(CancellationException::class)
    private suspend fun refresh(): Unit = refreshMutex.withLock {
        val result = withContext(defaultDispatcher) { refreshAccountName(accountId) }
        mutableState.value = result.fold(
            onSuccess = { name -> AccountScreenState.Loaded(displayName = name) },
            onFailure = { error -> stateAfterFailure(mutableState.value, error) },
        )
    }

    private fun stateAfterFailure(current: AccountScreenState, error: kotlin.Throwable): AccountScreenState =
        TODO("Выбрать Error или баннер в Loaded по причине сбоя и текущему состоянию")
}
```

**¬** `feature/impl/di/AccountBindings.kt`

```kotlin
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import io.github.aequicor.heartbeat.di.AppScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch

@BindingContainer
@ContributesTo(AppScope::class)
public interface AccountBindings {

    public companion object {

        @Provides
        internal fun provideAccountServiceApi(impl: AccountServiceApiImpl): AccountServiceApi = impl

        @Provides
        internal fun provideAccountDao(impl: AccountDaoImpl): AccountDao = impl

        @Provides
        internal fun provideAccountDataSource(impl: AccountDataSourceImpl): AccountDataSource = impl

        @Provides
        internal fun provideRefreshAccountNameUseCase(impl: RefreshAccountNameUseCaseImpl): RefreshAccountNameUseCase =
            impl

        @Provides
        internal fun provideAccountStoreFactory(
            refreshAccountName: RefreshAccountNameUseCase,
            @Named("default") defaultDispatcher: CoroutineDispatcher,
        ): AccountStoreFactory =
            AccountStoreFactory { accountId, scope ->
                val store = AccountStoreImpl(accountId, refreshAccountName, scope, defaultDispatcher)
                scope.launch { store.dispatch(AccountScreenAction.Load) }
                store
            }
    }
}
```

В этом примере Decompose использует `FeatureComponent`, чтобы передать готовый `AccountComponent` в UI. Экран аккаунта принимает сам `AccountComponent` и может использоваться отдельно.

**¬** `feature/presentation/decompose/FeatureComponent.kt`

```kotlin
public interface FeatureComponent {

    public val account: AccountComponent
}
```

**¬** `feature/presentation/decompose/FeatureComponentImpl.kt`

```kotlin
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

internal class FeatureComponentImpl(
    componentContext: ComponentContext,
    accountId: String,
    dispatcher: CoroutineDispatcher,
    accountStoreFactory: AccountStoreFactory,
) : FeatureComponent {

    private val componentScope = CoroutineScope(SupervisorJob() + dispatcher)

    init {
        componentContext.lifecycle.doOnDestroy(componentScope::cancel)
    }

    override val account: AccountComponent =
        AccountComponentImpl(store = accountStoreFactory.create(accountId, componentScope))
}
```

**¬** `feature/presentation/decompose/account/AccountComponent.kt`

```kotlin
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

public interface AccountComponent {

    public val state: StateFlow<AccountScreenState>

    @Throwable(CancellationException::class)
    public suspend fun dispatch(action: AccountScreenAction)
}
```

**¬** `feature/presentation/decompose/account/AccountComponentImpl.kt`

```kotlin
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal class AccountComponentImpl(private val store: AccountStore) : AccountComponent {

    override val state: StateFlow<AccountScreenState> = store.state

    @Throwable(CancellationException::class)
    override suspend fun dispatch(action: AccountScreenAction) = store.dispatch(action)
}
```

**¬** `feature/ui/compose/FeatureScreen.kt`

```kotlin
@Composable
internal fun FeatureScreen(component: FeatureComponent) {
    AccountScreen(component.account)
}
```

**¬** `feature/ui/compose/AccountScreen.kt`

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun AccountScreen(component: AccountComponent) {
    val scope = rememberCoroutineScope()
    when (val state = component.state.collectAsState().value) {
        AccountScreenState.Loading -> Text("Загрузка…")
        is AccountScreenState.Loaded -> Column {
            Text(state.displayName)
            val button = state.refreshButton
            Button(onClick = {
                scope.launch { component.dispatch(AccountScreenAction.ButtonClicked(button.action)) }
            }) {
                Text(button.label)
            }
            state.refreshBanner?.let { TODO("Показать баннер обновления") }
        }
        is AccountScreenState.Error -> TODO("Показать ошибку с доступными действиями")
    }
}
```

# Скилы

До начала этапа открой `SKILL.md` каждого подходящего навыка. Используй [локальную копию](.agents/skills), а если её нет — исходный набор.

| Когда подключать | Какой навык применять |
| --- | --- |
| Пишешь код или проводишь код-ревью | [Kent Beck Style](.agents/skills/kent-beck-style/SKILL.md) для ясных имён и простой логики. |
| Проектируешь или проверяешь контракты, границы слоёв и зависимости | [Clean Architecture](.agents/skills/clean-architecture/SKILL.md) для распределения ответственности между компонентами. |
| Проектируешь, меняешь или проверяешь Kotlin, KMP или Compose | Начни с [using-chrisbanes-skills](.agents/skills/using-chrisbanes-skills/SKILL.md). |
| Реализуешь согласованный Kotlin API, фабрику или платформенный сервис с `expect/actual` | [kotlin-api-design](.agents/skills/kotlin-api-design/SKILL.md) |
| Меняешь `StateFlow` в Store, время жизни корутин или обработку отмены | [kotlin-concurrency-and-flow](.agents/skills/kotlin-concurrency-and-flow/SKILL.md) |
| Определяешь, где экран Compose хранит состояние, собирает Flow или запускает эффект | [compose-state-and-effects](.agents/skills/compose-state-and-effects/SKILL.md) |
| Меняешь переход фокуса или обработку клавиш в desktop-интерфейсе | [compose-focus-navigation](.agents/skills/compose-focus-navigation/SKILL.md) |
| Пишешь UI-тест Compose или проверяешь скриншотный эталон | [compose-ui-testing-patterns](.agents/skills/compose-ui-testing-patterns/SKILL.md) |
| Разбираешь лишние рекомпозиции или нестабильные параметры Compose | [compose-performance](.agents/skills/compose-performance/SKILL.md) |
| Вызываешь нативный API из Kotlin или экспортируешь Kotlin API для Swift, Objective-C или C | [kotlin-native-interop](.agents/skills/kotlin-native-interop/SKILL.md) |
| Диагностируешь медленную компиляцию Kotlin/Native или линковку фреймворка для iOS | [kotlin-tooling-native-build-performance](.agents/skills/kotlin-tooling-native-build-performance/SKILL.md) |
| Собираешься запустить Gradle или разбираешь сбой сборки Gradle | Открой [gradle-run](.agents/skills/gradle-run/SKILL.md). |
| Сборка Android-таргета KMP падает из-за несовместимости плагинов с AGP 9 | [kotlin-tooling-agp9-migration](.agents/skills/kotlin-tooling-agp9-migration/SKILL.md) · [Исходник в каталоге JetBrains](https://github.com/JetBrains/skills/blob/main/kotlin-tooling-agp9-migration/SKILL.md) |
| Разбираешь конфликт транзитивных зависимостей Gradle или `NoSuchMethodError` при запуске JVM-приложения | [dependency-conflict-resolver](https://github.com/JetBrains/skills/blob/main/dependency-conflict-resolver/SKILL.md) |
| По запросу оператора выясняешь, почему в PR упала проверка GitHub Actions | [gh-fix-ci](https://github.com/JetBrains/skills/blob/main/gh-fix-ci/SKILL.md) |

Исходные наборы для Kotlin и архитектуры — [chrisbanes/skills](https://github.com/chrisbanes/skills) и [Clean Architecture + Kent Beck Style](https://github.com/nathankim0/clean-architecture-skills).
Правила для своей ОС и инструментов выбирай по [индексу окружения](.rules/index.md).

# Планирование

1. Определи цель, границы задачи и критерии готовности.
2. Назови затронутые модули и платформы. Покажи ожидаемый результат на конкретном сценарии.
3. При изменении контракта опиши интерфейс, входы, выходы, ошибки и поток данных. Для сети и БД укажи нужные DTO и методы доступа.
4. При изменении UI подготовь прототип в Figma через MCP Figwright. До реализации пройди сценарии с оператором. [Работа с макетами](docs/setup.md#design)
5. Обсуди с оператором каждый шаг и его проверку. Начинай реализацию после явного согласования плана.
6. Для расширения задачи объясни причину и заранее получи разрешение оператора.
7. Проверь результат по согласованным критериям. [Порядок работы](docs/specs/agent-workflow.md)

# Код-ревью

- **Логика.** Сверь обычный, граничный и ошибочный сценарии с контрактом. Где применимо, проверь повторный вызов, отмену и параллельное выполнение.
- **Читаемость.** Ищи неясные имена, скрытые побочные эффекты, противоречивые условия, лишнюю вложенность и дублирование правил.
- **Архитектура.** Проверь, кто отвечает за изменённое правило и в какую сторону направлены зависимости.
- **Безопасность.** Проследи путь внешнего ввода до файла, БД или команды. Проверь права, валидацию и отсутствие секретов в логах и ошибках.
- **Совместимость.** Проверь существующие вызовы и сохранённые данные.
- **Временные тесты.** Для проверки результата можно написать тест. После проверки удали созданный тобой тест и его временные файлы. Если ты добавил проверку в существующий файл, убери только своё временное добавление. Удаление ранее существовавших тестов требует отдельного запроса оператора.

**Цикл ревью — проверка → исправление → проверка**

1. Прочитай diff и затронутые места вызова. Для каждого замечания укажи место, сценарий и последствие.
2. Воспроизведи дефект и устрани его причину в границах задачи.
3. Повтори исходный сценарий. Если дефект остался или появился новый, повтори цикл. После проверки убери временные тесты и перечитай итоговый diff.
4. Заверши ревью, когда замечания по задаче закрыты и проверки прошли. Сообщи результат. Для недоступной проверки укажи сценарий и причину.

# Система контроля версий GitHub

- Работай в текущей ветке и рабочем каталоге. Создавай ветку или worktree только по явному запросу оператора.
- Перед каждым коммитом получи явное согласие оператора.
- Агенту запрещено включать тесты в коммиты. Перед коммитом проверь состав подготовленных изменений через `git diff --cached`.
- Открывай новый PR как draft с согласованным планом и пустым diff.
- После каждого проверенного коммита в PR отправь ветку и обнови описание. Для коммита с изменениями добавь пункт ревью со списком проверок.
- После итоговых проверок переведи PR в Ready for review. Передай владельцу ссылку и SHA текущего HEAD.
- Владелец отмечает проверенные коммиты и публикует `/reviewed <HEAD SHA>`. Новый HEAD требует нового подтверждения.
- После успешных проверок и разрешения обсуждений GitHub выполняет squash-слияние. [Правила worktree, PR и CI](.github/AGENTS.md)

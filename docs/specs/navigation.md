# Навигация

Нормативный контракт навигации Heartbeat 2.0. Навигация построена по модели **feature-first**:
Shell управляет расположением фич, а каждая фича — собственными экранами.

Модули `navigation/*` реализованы. AI Studio подключён как корневой экран: `AiStudioFeatureEntry`
создаёт корень фичи, `AiStudioComposeRenderer` отображает его, а Shell начинает стек с
`AiStudioRoutes.main()`.

Источник — согласованная архитектура навигации Heartbeat v2 от 8 ноября 2026. Границы модулей,
Composition Root и общие правила зависимостей заданы в [спецификации архитектуры](architecture.md).

## Принципы

1. `Shell` знает точки входа фич, но не их внутренние экраны.
2. Каждая фича владеет собственным навигационным графом.
3. Фичи подключаются через `FeatureEntry`, зарегистрированный в Metro.
4. `ui/compose` может напрямую зависеть от собственного `presentation/decompose`.
5. Нативный UI использует совместимый presentation-адаптер и не зависит от Decompose.
6. `api` и `impl` компилируются для всех заявленных KMP targets и не зависят от UI и навигационных
   фреймворков.
7. Межфичевая навигация проходит через Shell, внутрифичевая остаётся локальной.

Shell не растёт вместе с числом экранов. Его сложность определяется глобальными сценариями:
рабочими областями, окнами, переходами между фичами и системной навигацией.

## Модули

| Модуль | Содержимое | Targets |
| --- | --- | --- |
| `navigation/api` | Универсальные маршруты `AppRoute` | Все обязательные |
| `navigation/decompose` | SPI Decompose-фич: `FeatureRoot`, `FeatureHost`, `DecomposeFeatureEntry` | Совместимые с Decompose |
| `navigation/decompose-compose` | SPI Compose-рендереров `ComposeFeatureRenderer` | Compose targets |

Разделение исключает циклы зависимостей: `navigation/api` не знает о presentation,
`navigation/decompose` не знает о Compose, `navigation/decompose-compose` зависит от обоих.

## Маршрут

`AppRoute` — транспортный контракт на границе фич.

```kotlin
@Serializable
data class AppRoute(
    val featureId: String,
    val destination: String = "root",
    val parameters: Map<String, String> = emptyMap(),
)
```

Правила:

- `navigation/api` компилируется для всех обязательных targets и не содержит `ComponentContext`,
  `StackNavigation`, `@Composable` и других framework-specific типов.
- Каждая фича объявляет типизированные фабрики маршрутов в собственном `api`.

```kotlin
object EditorRoutes {
    const val FEATURE_ID = "editor"

    fun edit(noteId: String) = AppRoute(
        featureId = FEATURE_ID,
        destination = "edit",
        parameters = mapOf("noteId" to noteId),
    )

    fun history(noteId: String) = AppRoute(
        featureId = FEATURE_ID,
        destination = "history",
        parameters = mapOf("noteId" to noteId),
    )
}
```

- Централизованного `sealed class` со всеми экранами приложения нет.
- Сырые параметры `parameters` валидирует `FeatureEntry`; UI и Store работают с типизированными
  аргументами.
- `AppRoute` сериализуется, поэтому маршрут используется в конфигурациях Decompose и в deep links.

## SPI Decompose

```kotlin
interface FeatureRoot

interface FeatureHost {
    fun open(route: AppRoute)
    fun back()
}

interface DecomposeFeatureEntry {
    val featureId: String

    fun create(
        route: AppRoute,
        context: ComponentContext,
        host: FeatureHost,
        dispatcher: CoroutineDispatcher,
    ): FeatureRoot
}
```

| Интерфейс | Ответственность |
| --- | --- |
| `FeatureRoot` | Корень конкретной фичи |
| `DecomposeFeatureEntry` | Фабрика её корневого компонента |
| `FeatureHost` | Минимальный порт межфичевой навигации |

`FeatureHost` не повторяет `StackNavigation`: он содержит только операции уровня Shell. Внутри фичи
используются собственные Decompose navigation API.

`FeatureHost` привязан к дочернему узлу, из которого запрошена навигация: `open` меняет маршрут
этого узла, а не корневого стека.

## Регистрация

```kotlin
@ContributesIntoSet(AppScope::class)
class EditorFeatureEntry(
    private val factory: EditorFlowComponentFactory,
) : DecomposeFeatureEntry {

    override val featureId = EditorRoutes.FEATURE_ID

    override fun create(
        route: AppRoute,
        context: ComponentContext,
        host: FeatureHost,
        dispatcher: CoroutineDispatcher,
    ): FeatureRoot {
        val noteId = requireNotNull(route.parameters["noteId"])

        return factory.create(
            context = context,
            noteId = noteId,
            initialDestination = route.destination,
            host = host,
            dispatcher = dispatcher,
        )
    }
}
```

- Регистрация автоматическая на уровне DI: Metro агрегирует `Set<DecomposeFeatureEntry>` при
  компиляции конечного графа.
- `EditorFlowComponentFactory` — проектируемая фабрика корня Editor; она может использовать
  существующую `EditorComponentFactory`.
- Это не динамическая загрузка модулей: платформа по-прежнему подключает Gradle-модуль фичи.
- Повторяющийся `featureId` — ошибка конфигурации, а не порядок регистрации.

## Внутри фичи

```kotlin
interface EditorFlowComponent : FeatureRoot {
    val childStack: Value<ChildStack<*, Child>>

    sealed interface Child {
        data class Editor(val component: EditorComponent) : Child
        data class History(val component: HistoryComponent) : Child
        data class Compare(val component: CompareComponent) : Child
    }
}
```

- `sealed interface Child` локален для фичи; Shell его не видит.
- `DefaultEditorFlowComponent` содержит собственный `StackNavigation<EditorConfig>`.
- Конфигурации навигации `@Serializable`, чтобы Decompose восстанавливал состояние; вложенные
  навигационные модели сохраняют свои конфигурации самостоятельно.
- Новый экран меняет только фичу: Shell и другие фичи остаются неизменными.

## Shell

```kotlin
class FeatureRegistry(entries: Set<DecomposeFeatureEntry>) {
    private val byId = entries.associateBy { it.featureId }

    init {
        require(byId.size == entries.size) { "Duplicate feature registrations" }
    }

    fun resolve(featureId: String): DecomposeFeatureEntry =
        requireNotNull(byId[featureId]) { "Feature not registered: $featureId" }
}
```

- Shell создаёт корень фичи только через реестр и общие контракты.
- В Shell нет `when (featureId)`, зависимости от `EditorComponent` и ручного создания фич.
- Набор entries предоставляет composition root; `shell:presentation:decompose` не зависит от
  Gradle-модулей фич.
- При восстановлении состояния неизвестный или устаревший маршрут не должен приводить к падению
  production-приложения: Shell обрабатывает отсутствующую регистрацию, сообщает диагностику и
  возвращается к корневому маршруту.

## Compose

```kotlin
interface ComposeFeatureRenderer {
    val featureId: String

    @Composable
    fun Render(component: FeatureRoot)
}
```

- `features/editor/ui/compose` реализует renderer и отображает `EditorFlowContent`, который управляет
  всеми внутренними экранами редактора через Decompose `Children`.
- `shell/ui/compose` строит реестр по `featureId` и отображает активный компонент, не перечисляя
  фичи.
- Набор renderers передаёт composition root; shell UI не зависит от composition-root графа.
- Интеграционный тест проверяет соответствие `DecomposeFeatureEntry` и `ComposeFeatureRenderer`:
  каждому доступному UI-маршруту соответствует renderer, повторяющиеся идентификаторы отклоняются.
- Поэтому добавление Compose-фичи не требует изменения Shell UI.

## Back

- Back сначала обрабатывает навигационный стек активной фичи, затем корневую навигацию Shell.
- Порядок не зависит от порядка регистрации обработчиков; поведение покрыто тестом с вложенными
  стеками.
- Обработчик Back включается только для активного дочернего стека.

## Межфичевая навигация

Фича вправе запросить открытие другой фичи, но не должна создавать её presentation-компоненты.

```kotlin
// Home presentation adapter
fun onNoteSelected(noteId: String) {
    host.open(EditorRoutes.edit(noteId))
}
```

```text
Home UI
   ↓
HomeComponent
   ↓
FeatureHost.open(EditorRoutes.edit(noteId))
   ↓
ShellComponent
   ↓
FeatureRegistry
   ↓
EditorFeatureEntry
   ↓
EditorFlowComponent
   ↓
Editor UI
```

- `HomeStore` не зависит от `FeatureHost`, `AppRoute` и Decompose. Выбор заметки преобразуется в
  навигационное намерение на границе presentation-адаптера.
- Межфичевые переходы выполняет Shell; фича не создаёт компоненты другой фичи.

## Платформы

| Платформа | Presentation | UI |
| --- | --- | --- |
| Android | Decompose | Compose |
| Desktop JVM | Decompose | Compose |
| iOS | Decompose или BLoC | SwiftUI либо Compose при соответствующей сборке |
| macOS Native | Decompose или BLoC | Native UI |
| Windows Native (`mingwX64`) | BLoC | Win32 |
| Linux Native | Decompose или BLoC | Native UI |

Таблица задаёт допустимые комбинации, а не готовность реализаций. Decompose 3.6.0-beta02 включён
для Android, JVM, iOS, macOS и Linux Native, но не для `mingwX64`.

Нативные UI используют тот же `navigation/api`, но не зависят от Decompose. Когда появится BLoC, у
него будут собственные entry points и renderer adapters.

## Навигация для Desktop AI Studio

Корневая навигация не обязана всегда быть `ChildStack`.

| Часть интерфейса | Ответственность |
| --- | --- |
| Sidebar | Shell UI, постоянный layout |
| Workspace | Shell navigation, выбранная фича |
| Chat screens | Внутренняя навигация Chat |
| Editor screens | Внутренняя навигация Editor |
| Dialogs | `ChildSlot` на соответствующем уровне |
| Tabs | Локальная модель активных вкладок |

Для переключения рабочего пространства подходят `ChildSlot` или модель вкладок; для мобильных
переходов между страницами — `ChildStack`. Shell владеет компоновкой и навигацией верхнего уровня,
но не структурой экранов фич.

## Архитектурные проверки

| Источник | Разрешённые зависимости |
| --- | --- |
| `navigation/api` | `navigation/api` |
| `navigation/decompose` | `navigation/api`, `navigation/decompose`, Decompose |
| `navigation/decompose-compose` | `navigation/api`, `navigation/decompose`, Compose |
| `features/*/api` | `navigation/api`, `features/*/api`, `core/*` |
| `features/*/impl` | `features/*/api`, `di:api`, `core/*` |
| `features/*/presentation/decompose` | `navigation/decompose`, `features/*/api`, Decompose, Metro |
| `features/*/ui/compose` | собственный `features/<feature>/presentation/decompose`, `navigation/decompose-compose`, Compose, Design System, Metro |
| `shell/presentation/decompose` | `navigation/decompose`, `shell:api`, Decompose, Metro |
| `shell/ui/compose` | `shell:presentation:decompose`, `navigation/decompose-compose`, Compose, Design System |
| `features/*/ui/win32` | совместимый native presentation, без Decompose и Compose |

Дополнительные ограничения:

- `api` и `impl` не содержат UI-зависимостей; `navigation/api` не содержит типов Decompose, Compose
  и Metro.
- Presentation-адаптеры одной фичи не зависят друг от друга.
- UI фичи не зависит от presentation-адаптера другой фичи.
- Shell UI не перечисляет фичи и не импортирует их компоненты.

## Требования и проверка

| ID | Требование | Источник | Критерий проверки |
| --- | --- | --- | --- |
| HB-NAV-001 | `navigation/api` должен компилироваться для всех обязательных targets, содержать сериализуемый `AppRoute` и не содержать `ComponentContext`, `StackNavigation`, `@Composable` и других framework-specific типов. | Согласованная архитектура навигации от 8 ноября 2026 | Сборка модуля для обязательных targets; `architectureCheck` запрещает Decompose, Compose и Metro; тест сериализации `AppRoute`. |
| HB-NAV-002 | Каждая фича объявляет типизированные фабрики маршрутов в собственном `api`; централизованной иерархии всех экранов нет. | Согласованная архитектура навигации от 8 ноября 2026 | Наличие `EditorRoutes.edit`; отсутствие общего sealed-типа маршрутов; Shell не импортирует маршруты фич. |
| HB-NAV-003 | `navigation/decompose` должен определять `FeatureRoot`, `FeatureHost` и `DecomposeFeatureEntry` без типов Compose. | Согласованная архитектура навигации от 8 ноября 2026 | Сборка модуля без Compose; проверка импортов по роли. |
| HB-NAV-004 | Каждая Decompose-фича регистрирует `DecomposeFeatureEntry` через Metro `@ContributesIntoSet(AppScope::class)`; `featureId` уникален. | Согласованная архитектура навигации от 8 ноября 2026 | `Set<DecomposeFeatureEntry>` в тестовом графе содержит entry фичи; дубликат `featureId` отклоняется. |
| HB-NAV-005 | `FeatureRegistry` разрешает entry по `featureId` и отклоняет дубликаты при создании. | Согласованная архитектура навигации от 8 ноября 2026 | Unit-тест: дубликат — исключение; неизвестный id — исключение с диагностикой. |
| HB-NAV-006 | Shell создаёт корни фич только через реестр и не содержит `when (featureId)` и зависимостей от компонентов конкретных фич. | Согласованная архитектура навигации от 8 ноября 2026 | `shell:presentation:decompose` не зависит от Gradle-модулей фич; тест с fake-entries. |
| HB-NAV-007 | `FeatureHost` — единственный порт межфичевой навигации; Store не зависит от `FeatureHost`, `AppRoute` и Decompose. | Согласованная архитектура навигации от 8 ноября 2026 | Проверить сигнатуру `DefaultEditorStore` и импорты модуля `impl`; архитектурная проверка. |
| HB-NAV-008 | Каждая фича владеет собственным `ChildStack`; `sealed interface Child` локален; добавление экрана не меняет Shell. | Согласованная архитектура навигации от 8 ноября 2026 | Тест навигации фичи; diff нового экрана не затрагивает `shell/*`. |
| HB-NAV-009 | `navigation/decompose-compose` определяет `ComposeFeatureRenderer`; UI фичи реализует renderer, Shell UI отображает активный компонент без перечисления фич. | Согласованная архитектура навигации от 8 ноября 2026 | Сборка renderer-модуля; тест Shell UI с двумя fake-renderers. |
| HB-NAV-010 | Интеграционный тест должен проверять соответствие entry и renderer и отклонять повторяющиеся идентификаторы. | Согласованная архитектура навигации от 8 ноября 2026 | Тест падает при отсутствующем renderer и при дубликате `featureId`. |
| HB-NAV-011 | Back сначала обрабатывает стек активной фичи, затем корневую навигацию Shell; поведение покрыто тестом с вложенными стеками. | Согласованная архитектура навигации от 8 ноября 2026 | Тест: Back в Editor закрывает внутренний экран, затем возвращает управление Shell. |
| HB-NAV-012 | Нативные UI используют `navigation/api` без Decompose; для BLoC предусмотрены отдельные entry points и renderer adapters. | Согласованная архитектура навигации от 8 ноября 2026 | Сборка `ui/win32` без Decompose и Compose; архитектурная проверка. |
| HB-NAV-013 | Неизвестный или устаревший маршрут при восстановлении состояния не должен приводить к падению production-приложения. | Согласованная архитектура навигации от 8 ноября 2026 | Тест восстановления с неизвестным `featureId` возвращает fallback вместо исключения. |
| HB-NAV-014 | Архитектурные проверки должны содержать роли `navigation/*`, разрешение `ui/compose` на собственный Decompose-адаптер и запрет зависимостей между адаптерами разных фич. | Согласованная архитектура навигации от 8 ноября 2026 | Негативные сценарии `ui → чужая presentation` и `presentation/decompose → Compose` падают; unit-тесты правил в `buildSrc`. |
| HB-NAV-015 | Добавление фичи не должно изменять `ShellComponent` и `ShellScreen`; подключение фичи выполняется Gradle-зависимостью платформы и Metro-регистрацией. | Согласованная архитектура навигации от 8 ноября 2026 | Diff подключения фичи не затрагивает `shell/*`; тестовый граф видит новую entry. |

# Архитектура

Heartbeat использует Kotlin Multiplatform. Общий код обслуживает сценарии кодинга и исследований в проектах. Целевые платформы — Android, iOS, Linux, Windows и macOS; desktop работает на JVM с Compose Multiplatform, расширение Chromium использует Kotlin/Wasm.

Проект построен по **Feature-first Modular Architecture** с разделением публичных контрактов, универсальной реализации и платформозависимых адаптеров.

Поведение точек входа и доставка артефактов описаны на странице
[Поддерживаемые таргеты](platforms.md).

## Архитектурная концепция

| Область | Технология |
| --- | --- |
| Язык | Kotlin 2.4 |
| Общая логика | Kotlin Multiplatform |
| Dependency Injection | Metro 1.5 (compile-time) |
| Асинхронность | Coroutines 1.11, Flow |
| Presentation | Decompose 3.6 / собственный BLoC (запланирован) |
| UI | Compose Multiplatform 1.12 / нативные UI |
| Сборка | Gradle Kotlin DSL, AGP 9.5 |
| Конфигурация сборки | Precompiled-плагины в `buildSrc` |

### Основные принципы

1. **Feature-first** — бизнес-функциональность изолируется внутри соответствующей фичи.
2. **Universal Core** — `api` и `impl` каждой фичи компилируются для всех заявленных KMP targets.
3. **Framework-independent Logic** — бизнес-логика и состояние UI не зависят от Decompose, Compose или BLoC.
4. **Pluggable Presentation** — Decompose и BLoC выступают альтернативными адаптерами.
5. **Pluggable UI** — Compose, SwiftUI и Win32 могут использовать одинаковые бизнес-контракты.
6. **Dependency Inversion** — взаимодействие между фичами происходит через публичные API.
7. **Compile-time DI** — Metro собирает и проверяет граф зависимостей при компиляции.
8. **Platform-owned Composition Root** — платформенное приложение выбирает конкретные реализации.
9. **Explicit Module Boundaries** — зависимости между модулями контролируются Gradle и архитектурными проверками.

## Границы компонентов

| Компонент | Ответственность |
| --- | --- |
| `platform/` | Точки входа: запуск, создание Metro-графа, корневая Presentation-иерархия, интеграция с ОС |
| `shell/` | Общая оболочка приложения: корневая навигация, состояние сессии, координация фич |
| `features/` | Бизнес-функциональность: контракты, реализация, адаптеры Presentation и UI |
| `frameworks/` | Собственные переиспользуемые фреймворки, например BLoC |
| `di/` | Общие определения областей видимости Metro и общие registrations |
| `designsystem/` | UI Kit по UI-технологиям |
| `core/` | Техническая инфраструктура: сеть, БД, файловая система, платформенные сервисы |
| `buildSrc/` | Precompiled-плагины соглашений сборки |

## Структура репозитория

```text
project/
│
├── platform/
│   ├── android/
│   ├── desktop/
│   ├── ios/
│   ├── macos/
│   ├── windows/
│   └── linux/
│
├── shell/
│   ├── api/
│   ├── impl/
│   ├── presentation/
│   │   ├── decompose/
│   │   └── block/
│   └── ui/
│       └── compose/
│
├── features/
│   ├── home/
│   ├── editor/
│   └── settings/
│
├── frameworks/
│   └── block/
│
├── di/
│   ├── api/
│   └── impl/
│
├── designsystem/
│   ├── foundation/
│   ├── compose/
│   │   ├── theme/
│   │   ├── resources/
│   │   ├── components/
│   │   └── desktop/
│   └── native/
│       ├── swiftui/
│       └── win32/
│
├── core/
│   ├── network/
│   ├── database/
│   ├── filesystem/
│   └── platform-api/
│
├── buildSrc/
│   └── src/main/kotlin/
│
├── docs/
│   └── specs/
│
├── gradle/
│   └── libs.versions.toml
│
├── build.gradle.kts
└── settings.gradle.kts
```

Обозначенные фичи являются примерами. Структура применяется к реальным функциональным областям приложения.

`presentation/block`, `frameworks/block` и нативные UI-каталоги пока являются запланированными компонентами. До реализации BLoC они не подключаются к Gradle.

Нативные UI-каталоги могут быть Swift Package, Xcode-проектами или C/C++-библиотеками. Не каждый каталог обязательно является Gradle-модулем.

## Структура фичи

Каждая фича имеет стандартную структуру:

```text
features/<feature>/
├── api/
├── impl/
├── presentation/
│   ├── decompose/
│   └── block/
└── ui/
    ├── compose/
    └── <native-ui>/
```

### `api` — публичные контракты

Gradle path: `:features:<feature>:api`

Содержит:

- Публичные интерфейсы Store.
- Контракты фабрик.
- UI State.
- Actions и Effects.
- Бизнес-модели, которые должны использовать другие модули.
- Публичные бизнес-сервисы, если фича предоставляет их другим фичам.

Публичный контракт называется `<Feature>Store`, реализация — `Default<Feature>Store`, фабрика — `<Feature>StoreFactory`. Термины `Controller` и `ViewModel` не используются для универсального State/Action-хранилища фичи; названия платформенных API (например, AndroidX ViewModel) этим правилом не затрагиваются.

Пример:

```kotlin
interface EditorStore {
    val state: StateFlow<EditorState>
    val effects: Flow<EditorEffect>

    fun dispatch(action: EditorAction)
}

interface EditorStoreFactory {
    fun create(
        noteId: String,
        scope: CoroutineScope,
    ): EditorStore
}

data class EditorState(
    val title: String = "",
    val content: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val loadError: String? = null,
    val saveError: String? = null,
)

sealed interface EditorAction {
    data class TitleChanged(
        val value: String
    ) : EditorAction

    data class ContentChanged(
        val value: String
    ) : EditorAction

    data object Save : EditorAction

    data object Reload : EditorAction
}

sealed interface EditorEffect {
    data object Saved : EditorEffect
}
```

API не должен содержать типов Decompose, Compose, Metro или BLoC.

`Coroutines` и `Flow` допустимы, поскольку являются платформонезависимыми Kotlin-библиотеками.

**Targets: все поддерживаемые платформы проекта.**

### `impl` — универсальная реализация

Gradle path: `:features:<feature>:impl`

Этот модуль объединяет Domain, Data и UI-независимую Presentation-логику.

```text
impl/
└── src/
    ├── commonMain/
    │   └── kotlin/
    │       ├── domain/
    │       │   ├── model/
    │       │   ├── repository/
    │       │   └── usecase/
    │       │
    │       ├── data/
    │       │   ├── datasource/
    │       │   ├── repository/
    │       │   └── mapper/
    │       │
    │       ├── presentation/
    │       │   ├── DefaultEditorStore.kt
    │       │   └── EditorStateMapper.kt
    │       │
    │       └── di/
    │           └── EditorBindings.kt
    │
    ├── androidMain/
    ├── jvmMain/
    ├── iosMain/
    ├── macosMain/
    ├── mingwX64Main/
    └── linuxX64Main/
```

Domain, Data и Presentation — внутренние пакеты **одного Gradle-модуля**, а не независимые Gradle-проекты.

Ответственность:

**Domain**

- Бизнес-модели.
- UseCase.
- Бизнес-правила.
- Бизнес-валидация.
- Контракты репозиториев.

**Data**

- Реализации репозиториев.
- DataSource.
- DTO и мапперы.
- Кэширование.
- Работа с сетью и локальным хранилищем.

**Presentation**

- Обработка пользовательских действий.
- Управление `StateFlow`.
- Формирование UI State.
- Запуск бизнес-операций.
- Формирование одноразовых эффектов.

Presentation внутри `impl` не управляет платформенной навигацией, окнами или жизненным циклом UI-фреймворка.

Пока данные загружаются, Store не принимает правки, которые может перезаписать загрузка. Ошибка загрузки отражается в `loadError` и не блокирует Store навсегда: действие `Reload` повторяет загрузку. Ошибка операции отражается в UI State, а признак выполнения сбрасывается при успехе, ошибке и отмене; отмена не трактуется как ошибка операции.

`Save` фиксирует снимок состояния синхронно в момент действия и блокирует правки до завершения сохранения, поэтому сохранённая заметка всегда соответствует тому, что видел пользователь, и опубликованный `Saved` не приводит к потере последних изменений.

Допускаются Metro-аннотации и registrations, если они совместимы со всеми обязательными targets. Это означает независимость от UI-фреймворков, а не полный запрет DI-инструментов.

**Targets: все поддерживаемые платформы проекта, включая `mingwX64` и Linux Native.**

### `presentation/decompose`

Gradle path: `:features:<feature>:presentation:decompose`

Содержит адаптер Store к Decompose.

```text
presentation/decompose/
└── src/commonMain/kotlin/
    ├── component/
    │   ├── EditorComponent.kt
    │   ├── EditorComponentFactory.kt
    │   └── DefaultEditorComponent.kt
    └── di/
        └── DefaultEditorComponentFactory.kt
```

Ответственность:

- `ComponentContext`.
- Навигация внутри фичи.
- Lifecycle.
- State restoration.
- Back handling.
- Управление временем жизни Store.
- Обработка выходных Effects.
- Создание компонентов через Metro.

Пример контракта:

```kotlin
interface EditorComponent {
    val store: EditorStore
}
```

Реализация получает `EditorStoreFactory` и создаёт Store с нужными параметрами и lifecycle-aware `CoroutineScope`. UI-диспетчер передаёт платформенный host: Kotlin/Native targets без Darwin main loop не имеют `Dispatchers.Main`, поэтому универсальный адаптер не создаёт его сам.

Decompose не должен дублировать состояние и бизнес-логику `DefaultEditorStore`.

**Targets: только совместимые с Decompose платформы.**

### `presentation/block`

Gradle path: `:features:<feature>:presentation:block`

Это будущая альтернативная реализация Presentation-адаптера.

Предполагаемая структура:

```text
presentation/block/
└── src/commonMain/kotlin/
    ├── bloc/
    │   └── EditorBloc.kt
    ├── lifecycle/
    └── di/
```

Ответственность:

- Интеграция с собственным BLoC framework.
- Жизненный цикл Store.
- Навигационные события.
- Связь с платформенным event loop.
- Управление подписками и освобождением ресурсов.
- Адаптация состояния для Native UI.

`presentation/block` использует ту же бизнес-реализацию из `impl`, что и Decompose-адаптер.

Он не должен содержать копию UseCase, репозиториев и бизнес-правил.

**Цель: поддержка всех KMP targets, включая Windows/Linux Native.**

### `ui`

Платформенно-зависимые реализации интерфейса:

```text
ui/
├── compose/
├── swiftui/
└── win32/
```

`ui/compose` должен быть независим от конкретного Presentation Framework.

Например:

```kotlin
@Composable
fun EditorScreen(
    store: EditorStore,
) {
    val state by store.state.collectAsState()

    HbTextField(
        value = state.title,
        onValueChange = {
            store.dispatch(
                EditorAction.TitleChanged(it)
            )
        },
    )
}
```

Пример иллюстрирует контракт. В реальном Compose-приложении нужно использовать соответствующую lifecycle-aware подписку.

Связывание `EditorComponent` с `EditorScreen` выполняется в интеграционном слое UI/оболочки, а не внутри универсального экрана.

Таким образом, один экран может использоваться с разными Presentation-адаптерами.

## Архитектура Shell

`shell` — общая оболочка приложения.

```text
shell/
├── api/
├── impl/
├── presentation/
│   ├── decompose/
│   └── block/
└── ui/
    └── compose/
```

### `shell:api`

Общие контракты приложения:

- Состояние приложения.
- Команды глобальной навигации.
- Контракты координации фич.
- События изменения сессии.
- Контракты фабрик, не привязанные к Decompose.

### `shell:impl`

Универсальная логика оболочки:

- Координация запуска приложения.
- Управление состоянием сессии.
- Обработка глобальных бизнес-событий.
- Координация фич, если она не принадлежит конкретной фиче.

Не должен зависеть от Decompose или BLoC.

### `shell:presentation:decompose`

Содержит:

- RootComponent.
- Root navigation.
- `ChildStack`, `ChildSlot` и другие необходимые модели.
- Создание дочерних компонентов фич.
- Управление деревом компонентов.
- Обработку глобальных навигационных событий.

Зависит от контрактов и фабрик Decompose-адаптеров подключаемых фич, но не от их бизнес-реализаций.

### `shell:presentation:block`

Будущая реализация корневого Presentation через собственный BLoC framework.

Она должна обеспечивать аналогичную функциональность без зависимости от Decompose.

### `shell:ui:compose`

Отвечает за отображение приложения:

- Root UI.
- Отрисовку навигационного дерева.
- Подключение Compose UI фич.
- Общий layout приложения.
- Передачу Store в экраны фич.

Это интеграционный UI-модуль, поэтому ему разрешена зависимость от `shell:presentation:decompose` и API Decompose-адаптеров фич. При этом сами `features/*/ui/compose` от Decompose не зависят.

Для нативных платформ Root UI располагается в соответствующем `platform` или нативном UI-проекте.

## Платформенные приложения

Все платформенные точки входа располагаются исключительно в `platform/`.

```text
platform/
├── android/
├── desktop/
├── ios/
├── macos/
├── windows/
└── linux/
```

### Назначение

| Модуль | Runtime | UI | Presentation |
| --- | --- | --- | --- |
| `android` | Android / ART | Compose | Decompose |
| `desktop` | JVM | Compose Desktop | Decompose |
| `ios` | Kotlin/Native + Swift | SwiftUI | Decompose |
| `macos` | Kotlin/Native | Native UI | Decompose или BLoC |
| `windows` | Kotlin/Native | Win32 | BLoC |
| `linux` | Kotlin/Native | Native UI | BLoC или совместимый Decompose |

`desktop` и `windows` — разные приложения по типу runtime.

`desktop` использует JVM, а `windows` — Kotlin/Native и нативные API Windows.

Каждая платформа отвечает за:

- Точку входа.
- Создание Metro Graph.
- Создание корневой Presentation-иерархии.
- Инициализацию UI.
- Интеграцию с ОС.
- Window lifecycle.
- Управление потоками UI.
- Завершение приложения.

Платформы самостоятельно выбирают зависимости на `presentation/decompose` или `presentation/block`.

## Dependency Injection — Metro

Metro является единственным основным DI-фреймворком.

### Общие DI-модули

```text
di/
├── api/
│   ├── AppScope.kt
│   └── SessionScope.kt
│
└── impl/
    └── CommonBindings.kt
```

**`di:api`** содержит общие определения DI-областей видимости.

**`di:impl`** предназначен исключительно для общих DI-регистраций, не принадлежащих конкретной фиче или платформе.

Если таких registrations нет, `di:impl` можно не создавать до появления реальной необходимости.

### Локальные registrations

Каждая реализация самостоятельно регистрирует предоставляемые зависимости.

| Расположение | Что регистрируется |
| --- | --- |
| `features/editor/impl` | StoreFactory, Repository, UseCase |
| `features/editor/presentation/decompose` | Decompose ComponentFactory |
| `features/editor/presentation/block` | BLoC factory |
| `core/network` | HTTP services |
| `core/database` | Общая инфраструктура БД |

Предпочтительно constructor injection и Metro `@ContributesBinding`.

Параметры, возникающие во время исполнения, такие как `ComponentContext`, `noteId` и callbacks, передаются через фабрики и Assisted Injection.

### Composition Root

Финальный Metro-граф принадлежит платформенному приложению.

```text
platform/android/
└── src/main/kotlin/
    └── di/
        └── AndroidGraph.kt

platform/windows/
└── src/mingwX64Main/kotlin/
    └── di/
        └── WindowsGraph.kt
```

Концептуально:

```kotlin
@DependencyGraph(AppScope::class)
interface AndroidGraph {
    val rootFactory: DecomposeRootFactory
}
```

А Windows Native Graph предоставляет фабрику корневого BLoC вместо Decompose.

Общий контракт графа можно выделить отдельно, если это действительно требуется нескольким платформам.

**Важно:** при смешивании common и platform-specific bindings финальный `@DependencyGraph` располагается в платформенной компиляции, чтобы видеть весь набор registrations.

### Scopes

| Scope | Время жизни |
| --- | --- |
| `AppScope` | Время жизни экземпляра приложения |
| `SessionScope` | Время жизни пользовательской сессии |
| Store / Component | Время жизни конкретного экземпляра Presentation |

Жизненным циклом экранных Store управляет Presentation-адаптер.

Metro не заменяет Decompose lifecycle.

Для Desktop с несколькими окнами создаются независимые корневые Presentation-иерархии. Глобальные сервисы могут находиться в одном application graph.

## Design System / UI Kit

UI Kit разделяется по UI-технологиям, а не по абстрактным кроссплатформенным слоям. Общие значения
токенов вынесены в нейтральный к рендереру модуль `foundation`, который не импортирует Compose.

```text
designsystem/
├── foundation/
│   └── src/commonMain/kotlin/
│       ├── HbColor.kt
│       ├── HbColorScheme.kt
│       ├── HbIconGlyph.kt
│       ├── HbContrastPreference.kt
│       ├── HbContrastSchemes.kt
│       ├── HbTypography.kt
│       ├── HbGeometry.kt
│       ├── HbMotion.kt
│       ├── HbMotionPreference.kt
│       ├── HbLayout.kt
│       └── HbWorkspaceLayout.kt
│
├── patterns/
│   └── src/commonMain/kotlin/
│       ├── HbSessionRow.kt
│       ├── HbMessage.kt
│       ├── HbToolActivity.kt
│       ├── HbApprovalRequest.kt
│       ├── HbWorkspaceScaffold.kt
│       └── HbUsageDisplay.kt
│
├── catalog/
│   └── src/jvmMain/kotlin/
│       ├── HbCatalogEntry.kt
│       ├── CatalogContent.kt
│       ├── CatalogFieldSamples.kt
│       └── CatalogPatternSamples.kt
│
├── compose/
│   ├── theme/
│   │   ├── HbTheme.kt
│   │   ├── HbColors.kt
│   │   ├── HbTypography.kt
│   │   ├── HbShapes.kt
│   │   └── HbSpacing.kt
│   │
│   ├── resources/
│   │   └── src/commonMain/composeResources/
│   │       ├── drawable/
│   │       ├── font/
│   │       └── values/
│   │
│   ├── components/
│   │   ├── HbButton.kt
│   │   ├── HbTextField.kt
│   │   ├── HbText.kt
│   │   └── HbDialog.kt
│   │
│   └── desktop/
│       ├── HbTitleBar.kt
│       └── WindowControls.kt
│
└── native/
    ├── swiftui/
    └── win32/
```

### Правила

- Публичный API Design System имеет префикс `Hb`: компоненты (`HbButton`, `HbTextField`, `HbDialog`), токены и тема (`HbColors`, `HbTheme`).
- `foundation` хранит портативную схему и значения токенов: цвета sRGB, логические размеры, масштабируемую типографику, длительности, кривые и идентификаторы иконок. Модуль не импортирует Compose и не содержит типов `Color`, `Dp`, `TextStyle`, объектов навигации, DI и клиентов среды исполнения.
- `compose` разрешает портативные токены в типы Compose; `theme` и `resources` внутри него Compose-ориентированы.
- `patterns` содержит продуктовые паттерны Heartbeat: строку сессии, сообщение, активность инструмента, запрос подтверждения и отображение расхода. Паттерны получают модели представления и callbacks, а бизнес-правила остаются в feature store.
- `catalog` содержит исполняемые примеры кита и паттернов: приложение каталога и снимки, которые рендерятся тестом. Запуск — `gradlew :designsystem:catalog:runCatalog`.
- Design System не зависит от фич.
- Design System не зависит от Metro или Decompose.
- Фичи могут использовать Design System через свои UI-модули.
- Компоненты с бизнес-семантикой остаются в UI конкретной фичи.
- Платформенные UI Kit могут иметь разные реализации и следовать нативным паттернам ОС. Нативный адаптер разрешает те же портативные токены в собственные типы.

Общие контракты токенов, компонентов, поверхностей и адаптивного поведения заданы в
[спецификации дизайн-системы](design-system.md).

## Core — техническая инфраструктура

```text
core/
├── network/
├── database/
├── filesystem/
└── platform-api/
```

`core` содержит только техническую инфраструктуру.

Допустимое содержимое:

- Настройка сетевого клиента.
- Общие database drivers.
- Абстракции файловой системы.
- Платформенные сервисы.
- Общие средства сериализации и логирования при необходимости.

Недопустимое содержимое:

- Feature-specific Repository.
- Бизнес-модели.
- UseCase.
- Экранное состояние.
- Навигация фич.

Каждый модуль `core` должен объявлять только те KMP targets, которые он действительно поддерживает. Универсальные инфраструктурные зависимости, используемые в `feature:impl`, обязаны поддерживать все targets этого `impl`.

## Межфичевое взаимодействие

Фичи не должны зависеть от чужих `impl`.

Пример:

```text
features/
├── notes/
│   ├── api/
│   └── impl/
│
├── editor/
│   ├── api/
│   └── impl/
│
└── home/
    ├── api/
    └── impl/
```

Если `Editor` и `Home` работают с одной бизнес-сущностью Notes, то общий контракт размещается в `notes:api`.

```text
editor:impl ──> notes:api
home:impl   ──> notes:api

notes:impl  ──> notes:api
```

Реализация Notes подключается в платформенном Metro Graph.

Такая фича может не иметь UI и Presentation — это допустимая **headless feature**.

Для навигации между фичами используются callbacks, outputs и Shell, а не обращения к чужим Decompose-компонентам из бизнес-логики.

## Направление зависимостей

### Универсальная часть

```text
feature:api
    ▲
    │
feature:impl
    ├── domain/
    ├── data/
    └── presentation/
```

`impl` реализует контракты `api`.

### Presentation

```text
feature:presentation:decompose
    ├── feature:api
    ├── Decompose
    └── Metro

feature:presentation:block
    ├── feature:api
    ├── frameworks:block
    └── Metro
```

Обе Presentation-реализации получают Store через API-фабрики.

### UI

```text
feature:ui:compose
    ├── feature:api
    ├── Compose
    └── designsystem:compose:components
```

### Composition Root

```text
platform:android
    ├── shell:presentation:decompose
    ├── shell:ui:compose
    ├── features:*:impl
    ├── features:*:presentation:decompose
    └── Metro Graph

platform:windows
    ├── shell:presentation:block
    ├── features:*:impl
    ├── features:*:presentation:block
    ├── native UI
    └── Metro Graph
```

Приведены логические зависимости; точный список Gradle-зависимостей определяется используемыми фичами.

### Запрещённые зависимости

Зависимости задаются allowlist по ролям модулей: ребро, которого нет в таблице, — нарушение.

| Роль модуля | Может зависеть от |
| --- | --- |
| `platform` | всех ролей: в платформенной компиляции находится итоговый граф Metro |
| `shell:api` | `shell:api`, `features/*/api`, `di:api`, `core/*` |
| `shell:impl` | `shell:api`, `shell:impl`, `features/*/api`, `di:api`, `core/*` |
| `shell:presentation/*` | `shell:api`, `shell:impl`, `shell:presentation/*`, `features/*/api`, `features/*/presentation/*`, `di:api`, `core/*` |
| `shell:ui/*` | `shell:*`, `features/*/api`, `features/*/presentation/*`, `features/*/ui/*`, `designsystem/*`, `di:api`, `core/*` |
| `features/*/api` | `features/*/api`, `core/*` |
| `features/*/impl` | `features/*/api`, `di:api`, `core/*` |
| `features/*/presentation/*` | `features/*/api`, `di:api`, `core/*` |
| `features/*/ui/*` | `features/*/api`, `designsystem/*`, `core/*` |
| `di:api` | `di:api`, `core/*` |
| `core/*` | `core/*`, `di:api` |
| `designsystem/*` | `designsystem/*`, `core/*` |

Следствия, которые проверяются отдельными сообщениями:

- `api` не зависит от `impl`, `presentation`, `ui`, `designsystem` и `di`: Metro в контрактах запрещён.
- `impl` не зависит от `presentation`, `ui` и `designsystem`; фичи взаимодействуют только через `api`.
- Presentation-адаптер получает Store через `api` и не зависит от `impl`; адаптеры не зависят друг от друга.
- `ui` не зависит от `impl`; связывать компоненты может только `shell:ui:compose`.
- `core` и `designsystem` не зависят от фич; `designsystem` не зависит от `di`.
- `features/*/api` и `core` не зависят от UI-фреймворков и Metro; `designsystem` и `ui` — от Decompose и Metro; Presentation-адаптеры — от Compose.

Группы артефактов проверяются и в объявленных зависимостях, и в разрешённых compile/runtime/klib-графах универсальных модулей; импорты проверяются по той же роли модуля.

Исключение: композиционный UI-модуль Shell может зависеть от Decompose Presentation API для отрисовки навигационного дерева.

## KMP Targets

### Обязательные для универсальных модулей

```text
android
jvm
iosArm64
iosSimulatorArm64
iosX64
macosArm64
macosX64
mingwX64
linuxX64
linuxArm64
```

При необходимости список расширяется другими KMP targets.

`api`, `impl` и универсальные модули `shell` должны компилироваться для всего утверждённого набора.

Apple-цели компилируются только на macOS-хостах; CI должен предусматривать необходимые хосты и toolchains. Поддержка цели библиотекой и возможность собрать её на конкретной машине — разные вещи.

### Матрица

| Модуль | Android | JVM | Apple Native | Linux Native | Windows Native |
| --- | --- | --- | --- | --- | --- |
| `api` | Да | Да | Да | Да | Да |
| `impl` | Да | Да | Да | Да | Да |
| `presentation/decompose` | Да | Да | Да | Да, с Decompose 3.6.0 | Нет |
| `presentation/block` | План | План | План | План | План |
| `ui/compose` | Да | Да | Опционально | Нет | Нет |

Decompose публикует артефакты `linuxX64` и `linuxArm64` начиная с версии 3.6.0; артефактов `mingwX64` нет, поэтому Windows Native использует BLoC.

## Особенности Native Windows / Linux

Планируемая связка:

```text
Win32 / Linux Native UI
          |
          v
Presentation BLoC
          |
          v
Universal EditorStore
          |
          v
Domain + Data
          |
          v
Platform Services
```

Kotlin/Native используется для исполнения общей логики без JVM.

Native UI взаимодействует с Kotlin через соответствующий FFI или платформенный адаптер.

Требования:

- Нативный UI event loop не должен блокироваться бизнес-операциями.
- Обновления интерфейса должны выполняться на соответствующем UI-потоке.
- Подписки на Flow должны отменяться при уничтожении экрана.
- Граница Kotlin/Native и C ABI должна управлять временем жизни объектов.
- На границе с C не следует передавать произвольные Kotlin-объекты как будто это C-совместимые структуры.
- UI не должен управлять репозиториями напрямую.

BLoC framework должен предоставлять абстракции, достаточные для интеграции с lifecycle и event loop каждой целевой ОС.

## Convention Plugins

Precompiled-плагины хранятся в `buildSrc/src/main/kotlin/`. Идентификатор плагина совпадает с именем файла.

```text
buildSrc/src/main/kotlin/
├── heartbeat-kmp-universal.gradle.kts
├── heartbeat-kmp-decompose.gradle.kts
├── heartbeat-kmp-compose.gradle.kts
├── heartbeat-metro.gradle.kts
├── heartbeat-compose-designsystem.gradle.kts
└── heartbeat-architecture-check.gradle.kts
```

| Plugin | Ответственность |
| --- | --- |
| `heartbeat-kmp-universal` | Полный обязательный набор KMP targets |
| `heartbeat-kmp-decompose` | Совместимые Decompose targets и зависимости |
| `heartbeat-kmp-compose` | Compose Multiplatform |
| `heartbeat-metro` | Metro compiler plugin и runtime |
| `heartbeat-compose-designsystem` | Конфигурация Compose UI Kit |
| `heartbeat-architecture-check` | Проверка правил архитектуры |

Плагин `heartbeat-kmp-block` появится вместе с реализацией BLoC framework.

### Примеры использования

`features/editor/impl/build.gradle.kts`

```kotlin
plugins {
    id("heartbeat-kmp-universal")
    id("heartbeat-metro")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":features:editor:api"))
        implementation(libs.kotlinx.coroutines.core)
    }
}
```

`features/editor/presentation/decompose/build.gradle.kts`

```kotlin
plugins {
    id("heartbeat-kmp-decompose")
    id("heartbeat-metro")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":features:editor:api"))
        implementation(libs.decompose)
    }
}
```

`features/editor/ui/compose/build.gradle.kts`

```kotlin
plugins {
    id("heartbeat-kmp-compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":features:editor:api"))
        implementation(
            project(":designsystem:compose:components")
        )
    }
}
```

Convention Plugins должны обеспечивать единые версии инструментов, согласованные compiler options и тестовые настройки.

## Тестирование

### `api` и `impl`

- `commonTest` для бизнес-логики.
- Тесты UseCase.
- Тесты Repository с fake DataSource.
- Проверка переходов UI State.
- Проверка Actions и Effects.
- Тесты ошибок и отмены операций.
- Компиляция для всех обязательных KMP targets.

### Presentation-адаптеры

- Тестирование навигации.
- Тестирование жизненного цикла.
- Корректность создания и уничтожения Store.
- Проверка State restoration.
- Тесты интеграции с Metro.
- Контрактные тесты одинаковых сценариев для Decompose и BLoC.

### UI

- Compose UI Tests.
- Preview/Screenshot Tests.
- Нативные UI-тесты.
- Smoke Tests запуска приложения.

### Архитектурные проверки

CI должен отклонять изменения, нарушающие зависимости между модулями.

Особое внимание:

- Decompose не должен появляться в транзитивных зависимостях универсального `impl`.
- UI не должен зависеть от бизнес-реализаций.
- Фичи не должны импортировать чужие реализации.
- Все заявленные targets должны компилироваться.
- Metro contributions должны быть доступны конечным графам.
- Внутри `impl` должны соблюдаться границы Domain/Data/Presentation.
- Запрещённые импорты Decompose, Compose, Metro и платформенных UI API контролируются правилом detekt `ForbiddenImport` и собственной проверкой исходников по роли модуля.
- Имена архитектурных ролей и пакеты их размещения контролируются Konsist-тестами `:architecture-tests`; правила покрыты unit-тестами `NamingRulesTest`.
- Соответствие имени файла основному объявлению контролирует правило detekt `MatchingDeclarationName`, имена классов — `ClassNaming`.

Проверка разделена на пять уровней:

- **Gradle (`heartbeat-architecture-check`).** Allowlist зависимостей по ролям модулей, запрещённые фреймворки в объявленных зависимостях, разрешённые compile/runtime/klib-графы универсальных модулей JVM, Android и Kotlin/Native и импорты по роли модуля. Правила покрыты unit-тестами в `buildSrc`.
- **Konsist (`:architecture-tests`).** Source-level проверки, которые Gradle не выражает: стандарт именования ролей и размещение по пакетам, публичность контрактов `api` и видимость деталей `impl`, соответствие `Default<Contract>` одноимённому интерфейсу `Contract`, направление импортов пакетов-ролей фичи (`api`, `domain`, `data`, `presentation`, `di`, `component`, `ui`). Задача `architectureCheck` запускает `:architecture-tests:test`.
- **detekt.** Синтаксические соглашения: `MatchingDeclarationName` (имя файла), `ClassNaming` (имена классов), `ForbiddenImport` (запрещённые фреймворки). Запускается задачей `detektAll` и пре-пуш хуком.
- **explicit API (`heartbeat-kmp-api`).** Модули `features/*/api`, `shell:api` и `di:api` компилируются в explicit API mode: публичные объявления указывают видимость, публичные свойства и функции — тип.
- **CI.** `static-analysis.yml` по команде `/reviewed` выполняет `architectureCheck` и `detektAll` и публикует статусы `static-analysis` и `architecture-check` для проверенного коммита. `cross-platform.yml` по команде `/reviewed` выполняет проверки, недоступные на хосте владельца: компиляцию Apple-целей и тесты macOS на `macos-15`, нативные тесты Linux на ubuntu. Метка `verify-platform` дополнительно собирает и запускает точки входа.

## Соглашения по именованию

| Имя | Значение |
| --- | --- |
| `platform` | Платформенные приложения |
| `shell` | Общая оболочка приложения |
| `features` | Бизнес-функциональность |
| `api` | Публичные универсальные контракты |
| `impl` | Универсальная бизнес-реализация |
| `presentation/decompose` | Адаптер Decompose |
| `presentation/block` | Адаптер собственного BLoC |
| `ui/compose` | Compose UI |
| `ui/swiftui` | SwiftUI |
| `ui/win32` | Win32 Native UI |
| `frameworks` | Собственные переиспользуемые фреймворки |
| `di` | Общие контракты и регистрации Metro |
| `designsystem` | UI Kit по технологиям |
| `core` | Универсальная техническая инфраструктура |
| `buildSrc` | Precompiled-плагины соглашений сборки |

### Имена архитектурных ролей

Роль объявления определяется его пакетом внутри фичи, имя — ролью.

| Архитектурная роль | Пакет фичи | Правило | Пример |
| --- | --- | --- | --- |
| UseCase | `domain.usecase` | `*UseCase` | `LoadNoteUseCase` |
| Контракт Repository | `domain.repository` | `*Repository`, без префикса `Default` | `EditorRepository` |
| Реализация Repository | `data.repository` | `*Repository`; реализация по умолчанию — `Default*Repository` | `DefaultEditorRepository`, `SqlDelightNoteRepository` |
| DataSource | `data.datasource` | `*DataSource` | `EditorDataSource` |
| DTO | `data.datasource`, `data.dto` | `*Dto` | `NoteDto` |
| Mapper-класс | `data.mapper` | `*Mapper` | `NoteMapper` |
| Mapper-функция | `data.mapper`, `presentation.mapper` | `to*()` | `toNote()`, `toDto()` |
| Store | `api` | `<Feature>Store` | `EditorStore` |
| Реализация Store | `presentation` | `Default<Feature>Store` | `DefaultEditorStore` |
| Фабрика Store | `api` | `<Feature>StoreFactory` | `EditorStoreFactory` |
| Component | `component` | `<Feature>Component` | `EditorComponent` |
| Реализация Component | `component` | `Default<Feature>Component` | `DefaultEditorComponent` |
| Фабрика Component | `component` | `<Feature>ComponentFactory` | `EditorComponentFactory` |
| State | `api` | `<Feature>State` | `EditorState` |
| Action | `api` | `<Feature>Action` | `EditorAction` |
| Effect | `api` | `<Feature>Effect` | `EditorEffect` |
| Привязка Metro | `di` | `Default*` | `DefaultEditorStoreFactory` |
| Compose-экран | `ui` | `*Screen` | `EditorScreen` |
| Design System | `designsystem` | `Hb*` для публичного API | `HbButton`, `HbTypography` |

Правила не ограничивают пакет `domain.model`, а также `core` и `platform`.

Пакет `api` и его подпакеты (`api.model`, `api.service`) содержат все публичные контракты фичи. Обязательно только имя с префиксом `<Feature>`; суффикс роли действует, когда объявление исполняет роль: рядом с `EditorStore` допустимы `EditorNote` и `EditorService`.

Размещение объявлений проверяется по Gradle-модулю, а не только по пакету: исходник в `features/<feature>/impl` не может объявлять пакет `api` или произвольный пакет вне фичи, иначе объявление обходит правила имён и видимости (HB-ARCH-043).

Исключения:

- Доменные модели в `domain.model` называются по предметной области: `Note`.
- Вложенные объявления `Action` и `Effect` называются по интенту, а не по роли: `EditorAction.TitleChanged`, `EditorEffect.Saved`.
- `Default` означает реализацию по умолчанию, а не обязательный префикс: специализированная реализация называется по технологии — `SqlDelightNoteRepository`.
- Маппер может быть набором top-level extension-функций в файле `NoteMappers.kt`; отдельный класс `*Mapper` нужен, только когда у преобразования есть состояние.

Видимость: `public` для внешних контрактов, `internal` для деталей реализации, `private` для локальных вспомогательных объявлений. Контракты `api`-модулей компилируются в explicit API mode, и их публичность, включая подпакеты `api.*`, проверяется Konsist-тестом `apiContractsArePublic`. Детали `impl` — DTO, mapper-функции, реализация Store и реализация Component — обязаны быть `internal`. `public` остаётся у типов, которые видит финальный граф Metro (`@Inject`, `@ContributesBinding`, `@ContributesTo`, `@Provides`, `@SingleIn`), и у публичных контрактов, в сигнатурах которых они участвуют; проверку выполняет Konsist-тест `implementationDetailsAreInternal`.

### Результирующий стандарт фичи

```text
features/<feature>/
├── api/
├── impl/
│   ├── domain/
│   ├── data/
│   └── presentation/
├── presentation/
│   ├── decompose/
│   └── block/
└── ui/
    └── compose/
```

Фактические каталоги `domain/`, `data/` и `presentation/` внутри `impl` находятся в `src/commonMain/kotlin/...`, а не непосредственно в корне Gradle-проекта.

## Требования и проверка

| ID | Требование | Источник | Критерий проверки |
| --- | --- | --- | --- |
| HB-ARCH-007 | Репозиторий должен содержать каталоги `platform`, `shell`, `features`, `frameworks`, `di`, `designsystem`, `core` и `buildSrc` с назначением по таблице границ компонентов. | Согласованная архитектура от 7 ноября 2026 | Сверить дерево репозитория с разделом «Структура репозитория». |
| HB-ARCH-008 | Каждая фича должна следовать стандартной структуре `api`, `impl`, `presentation/*`, `ui/*`; внутри `impl` пакеты `domain`, `data`, `presentation` находятся в одном Gradle-модуле. | Согласованная архитектура от 7 ноября 2026 | Проверить дерево модулей фичи и пакеты `src/commonMain/kotlin` её `impl`. |
| HB-ARCH-009 | Модуль `api` фичи не должен содержать типов Decompose, Compose, Metro или BLoC; Coroutines и Flow допустимы. | Согласованная архитектура от 7 ноября 2026 | Собрать `api` для всех targets и проверить его транзитивные зависимости архитектурной проверкой. |
| HB-ARCH-010 | Модуль `impl` фичи должен компилироваться для всех обязательных KMP targets, включая `mingwX64` и Linux Native. | Согласованная архитектура от 7 ноября 2026 | Скомпилировать `impl` для каждого обязательного target на поддерживающем хосте. |
| HB-ARCH-011 | Presentation-адаптеры должны получать Store через фабрики `api` и не дублировать состояние и бизнес-логику `impl`. | Согласованная архитектура от 7 ноября 2026 | Сверить граф зависимостей адаптера и отсутствие копий UseCase/репозиториев в его коде. |
| HB-ARCH-012 | Модуль `ui/compose` фичи не должен зависеть от `feature:impl` и от Presentation-фреймворков; экраны принимают контракты `api`. | Согласованная архитектура от 7 ноября 2026 | Архитектурная проверка и сборка `ui/compose` без зависимости от Decompose. |
| HB-ARCH-013 | Модули `shell:api` и `shell:impl` не должны зависеть от Decompose или BLoC; `shell:presentation:decompose` должен зависеть от адаптеров фич только через их API. | Согласованная архитектура от 7 ноября 2026 | Сверить зависимости shell-модулей архитектурной проверкой. |
| HB-ARCH-014 | Финальный Metro `@DependencyGraph` должен располагаться в платформенной компиляции, чтобы видеть весь набор registrations. | Согласованная архитектура от 7 ноября 2026, документация Metro и восьмое код-ревью PR #20 | Тестовый граф `DesktopCompositionRootGraph` в `platform/desktop` запрашивает `EditorStoreFactory` и `EditorComponentFactory`; при отсутствии binding компиляция падает с `[Metro/MissingBinding]`. Тест подтверждает только сборку графа: production-граф в точке входа и запуск приложения с ним — отдельный этап. |
| HB-ARCH-015 | DI должен использовать Metro с constructor injection и `@ContributesBinding`; runtime-параметры должны передаваться через фабрики или Assisted Injection. | Согласованная архитектура от 7 ноября 2026 | Собрать модули с Metro; тест графа в `commonTest` возвращает связанные зависимости. |
| HB-ARCH-016 | Области видимости `AppScope`, `SessionScope` и время жизни Store должны задаваться Presentation-адаптером; Metro не заменяет Decompose lifecycle. | Согласованная архитектура от 7 ноября 2026 | Проверить определения scope в `di:api` и уничтожение Store при завершении компонента. |
| HB-ARCH-017 | Design System не должна зависеть от фич, Metro или Decompose; компоненты с бизнес-семантикой остаются в UI фичи. | Согласованная архитектура от 7 ноября 2026 | Архитектурная проверка графа зависимостей `designsystem`. |
| HB-ARCH-018 | Модули `core` должны содержать только техническую инфраструктуру: без Feature-specific Repository, бизнес-моделей, UseCase, экранного состояния и навигации. | Согласованная архитектура от 7 ноября 2026 | Архитектурная проверка запрещает зависимости `core` от `features`; ревью содержимого. |
| HB-ARCH-019 | Фичи должны взаимодействовать только через публичные `api`; допустимы headless-фичи без UI и Presentation. | Согласованная архитектура от 7 ноября 2026 | Архитектурная проверка запрещает `impl → features:*:impl`; наличие headless-фичи подтверждается отсутствием UI-модулей. |
| HB-ARCH-020 | Система сборки должна отклонять зависимости, которых нет в allowlist раздела «Запрещённые зависимости», включая транзитивное появление UI-фреймворков и Metro. | Согласованная архитектура от 7 ноября 2026 и третье код-ревью PR #20 | Задача `heartbeat-architecture-check`; негативные сценарии `presentation:decompose → impl` и `core:network → Compose` падают. |
| HB-ARCH-021 | Универсальные модули должны объявлять обязательный набор targets: android, jvm, iosArm64, iosSimulatorArm64, iosX64, macosArm64, macosX64, mingwX64, linuxX64, linuxArm64. | Согласованная архитектура от 7 ноября 2026 | Сверить `kotlin.targets` модулей с набором; сборка каждого target на поддерживающем хосте. |
| HB-ARCH-022 | Модули должны следовать матрице targets по типу: Decompose-адаптеры без Windows Native, Compose UI без Linux и Windows Native. | Согласованная архитектура от 7 ноября 2026 | Сверить targets модулей с матрицей раздела «KMP Targets». |
| HB-ARCH-023 | Пока BLoC framework не реализован, `presentation/block`, `frameworks/block` и нативные UI-каталоги не должны подключаться к Gradle. | Согласованная архитектура от 7 ноября 2026 | Проверить, что `settings.gradle.kts` не содержит block-модулей. |
| HB-ARCH-024 | Соглашения сборки должны задаваться precompiled-плагинами `buildSrc`: `heartbeat-kmp-universal`, `heartbeat-kmp-decompose`, `heartbeat-kmp-compose`, `heartbeat-kmp-api`, `heartbeat-metro`, `heartbeat-compose-designsystem`, `heartbeat-architecture-check`; версии — только в `gradle/libs.versions.toml`. | Согласованная архитектура от 7 ноября 2026 и buildSrc/AGENTS.md | Сверить файлы плагинов и версии каталога; сборка модулей применяет плагины по идентификатору без версии. |
| HB-ARCH-025 | Бизнес-логика должна покрываться `commonTest`; Presentation-адаптеры — тестами навигации, жизненного цикла и контрактными тестами; CI должен выполнять архитектурные проверки и статический анализ для проверенного коммита, а проверки, недоступные на хосте владельца, — на подходящих runner-ах. | Согласованная архитектура от 7 ноября 2026 и третье код-ревью PR #20 | `static-analysis.yml` после `/reviewed` публикует статусы `static-analysis` и `architecture-check`; `cross-platform.yml` после `/reviewed` выполняет Apple-цели и нативные тесты Linux; `jvmTest` выполняется локально. |
| HB-ARCH-026 | В Native Windows/Linux UI event loop не должен блокироваться бизнес-операциями, обновления UI должны выполняться на UI-потоке, подписки на Flow должны отменяться при уничтожении экрана, а граница с C ABI должна управлять временем жизни объектов. | Согласованная архитектура от 7 ноября 2026 | Прогнать сценарии с длительной операцией и уничтожением экрана на нативном приложении. |
| HB-ARCH-027 | Имена модулей и пакетов должны соответствовать таблице соглашений по именованию. | Согласованная архитектура от 7 ноября 2026 | Сверить пути модулей с таблицей раздела «Соглашения по именованию». |
| HB-ARCH-028 | Публичный API Design System должен иметь префикс `Hb`: кастомные UI-компоненты, токены и тема, например `HbButton`, `HbTextField`, `HbTypography`, `HbShapes`, `HbSpacing`, `HbColors`, `HbTheme`. | Запрос владельца от 7 ноября 2026 | Konsist-тест `designSystemApiUsesHbPrefix` проверяет публичные объявления `designsystem`; композаблы, токены и тема используются в фичах. |
| HB-ARCH-029 | Пока данные загружаются, Store не должен принимать правки, которые может перезаписать завершающаяся загрузка. | Код-ревью PR #20 от 7 ноября 2026 | Тест: правка до завершения загрузки игнорируется, после загрузки состояние соответствует загруженным данным. |
| HB-ARCH-030 | Store должен отражать ошибку операции в UI State и сбрасывать признак выполнения при успехе, ошибке и отмене; отмена не должна трактоваться как ошибка. | Код-ревью PR #20 от 7 ноября 2026, пятое код-ревью | Тесты: ошибка сохранения выставляет поле ошибки и снимает признак сохранения; ошибка остаётся до следующей попытки `Save`, включая правку после ошибки; повторный `Save` до запуска coroutine выполняется один раз. |
| HB-ARCH-031 | Presentation-адаптер не должен создавать платформенный UI-диспетчер; диспетчер передаёт платформенный host или фабрика lifecycle scope. | Код-ревью PR #20 от 7 ноября 2026 | Проверить сигнатуру фабрики компонента и отсутствие `Dispatchers.Main` в универсальном адаптере; тест уничтожения компонента отменяет scope. |
| HB-ARCH-032 | Система сборки должна запускать архитектурную проверку при `gradlew build` и `check` и проверять транзитивные зависимости `impl`, а не только объявленные. | Код-ревью PR #20 от 7 ноября 2026 | `gradlew build` выполняет `architectureCheck`; негативный сценарий с Compose через `core` падает с сообщением о транзитивном нарушении. |
| HB-ARCH-033 | Публичный контракт состояния фичи должен называться `<Feature>Store`, реализация — `Default<Feature>Store`, фабрика — `<Feature>StoreFactory`; Store, State, Action и Effect размещаются в `features/<feature>/api`, реализация Store — в `features/<feature>/impl`; Store не зависит от Compose, Decompose, BLoC, AndroidX ViewModel и платформенных UI API. | Запрос владельца от 7 ноября 2026 | Сверить имена типов и модули размещения; архитектурная проверка подтверждает отсутствие UI-фреймворков в зависимостях `impl`, `api` проверяется ревью. |
| HB-ARCH-034 | Ошибка загрузки должна отражаться в UI State и не блокировать Store навсегда; должна быть доступна повторная загрузка, а отмена не должна считаться ошибкой. | Второе код-ревью PR #20 от 7 ноября 2026 | Тесты: ошибка загрузки выставляет `loadError` и снимает `isLoading`; `Reload` загружает данные; правка после ошибки игнорируется. |
| HB-ARCH-035 | `Save` должен фиксировать снимок состояния в момент действия, а правки во время сохранения не должны теряться. | Второе код-ревью PR #20 от 7 ноября 2026 | Тест: правка во время сохранения игнорируется, сохраняется и остаётся в состоянии именно снимок на момент `Save`. |
| HB-ARCH-036 | Архитектурная проверка должна анализировать объявленные зависимости, разрешённые compile/runtime графы JVM, Android и Kotlin/Native, явно сообщать о неразрешимой конфигурации и проверять импорты по роли модуля. | Второе, третье, седьмое и восьмое код-ревью PR #20 от 7 ноября 2026 | Негативный сценарий для импорта Compose падает; unit-тесты правил в `buildSrc` покрывают отдельные политики объявленных и транзитивных зависимостей; `gradlew build` выполняет `architectureCheck`. Проверка импортов — эвристика по строкам `import`: полностью квалифицированная ссылка требует запрещённую зависимость и ловится проверкой classpath, а точный source-level анализ (Kotlin Analysis API) остаётся отдельной задачей. |
| HB-ARCH-041 | Проверка зависимостей должна строиться на allowlist ролей модулей (`platform`, `shell:*`, `features/*/api`, `features/*/impl`, `features/*/presentation/*`, `features/*/ui/*`, `di:api`, `core/*`, `designsystem/*`): ребро вне таблицы и запрещённая группа артефактов по роли — нарушение. | Третье код-ревью PR #20 от 7 ноября 2026 | Unit-тест `everyRolePairFollowsTheAllowlist` покрывает каждое ребро матрицы; объявленные и транзитивные зависимости проверяются задачей `architectureCheck`. |
| HB-ARCH-042 | Архитектурные и статические проверки должны выполняться в CI для проверенного владельцем коммита и публиковать обязательные статусы; проверки, недоступные на хосте владельца (Apple-цели, нативные тесты Linux), должны выполняться на соответствующих runner-ах. | Третье, четвёртое, шестое, седьмое и восьмое код-ревью PR #20 от 7 ноября 2026 и политика CI владельца | `static-analysis.yml` и `cross-platform.yml` разрешают SHA из команды один раз, сверяют его с текущим HEAD и передают зафиксированный SHA всем job; несовпадение отклоняет запуск. `static-analysis.yml` публикует `static-analysis` и `architecture-check`; `cross-platform.yml` публикует `linux-native` и `apple-native` для этого SHA, компилирует `linuxArm64` и выполняет `linuxX64Test`; повторная команда не отменяет запущенную проверку (`cancel-in-progress: false`), чтобы не оставить `pending`; команда разбирается по грамматике `/reviewed <SHA или ссылка>`, префиксный вариант и многострочный комментарий не принимаются; статусы добавлены владельцем в ruleset. |
| HB-ARCH-037 | Konsist-тесты в модуле `:architecture-tests` должны проверять, что каждая реализация `Default<Contract>` реализует одноимённый интерфейс `Contract`, и что пакеты-роли фичи (`api`, `domain`, `data`, `presentation`, `di`, `component`, `ui`) соблюдают направление импортов; тесты должны выполняться в задаче `architectureCheck`. | Запрос владельца от 7 ноября 2026 | `gradlew architectureCheck` выполняет `:architecture-tests:test`; негативный сценарий с реализацией без контракта или запрещённым импортом между ролями падает. |
| HB-ARCH-038 | Модули `features/*/api`, `shell:api` и `di:api` должны компилироваться в explicit API mode (`explicitApi()`): публичные объявления указывают видимость, публичные свойства и функции — тип. | Запрос владельца от 7 ноября 2026 | Сборка api-модулей в strict explicit API mode без ошибок; публичное объявление без модификатора не компилируется. |
| HB-ARCH-039 | Имена и размещение архитектурных ролей фичи должны соответствовать таблице «Имена архитектурных ролей»: в пакетах ролей имя получает обязательный суффикс (`UseCase`, `Repository`, `DataSource`, `Dto`, `Mapper`, `Store`, `StoreFactory`, `Component`, `ComponentFactory`, `State`, `Action`, `Effect`, `Screen`), в `api` и его подпакетах — префикс `<Feature>`, поэтому публичные модели и сервисы фичи сохраняют свои имена; `Default` означает реализацию по умолчанию; доменные модели, вложенные Action/Effect и top-level mapper-функции не подчиняются роли. | Запрос владельца от 7 ноября 2026 и третье код-ревью PR #20 | Konsist-тест `declarationsFollowNamingStandard` падает на `LoadNote` в `domain.usecase`; unit-тесты `NamingRulesTest` покрывают позитивный и негативный случай каждого правила. |
| HB-ARCH-040 | Публичные контракты `features/*/api` и его подпакетов (`api.model`, `api.service`) должны быть `public`; детали реализации — `internal` или `private`. Публичными остаются только контрактные роли: интерфейс `*Repository`, доменная модель с модификатором `data`/`sealed`/`enum`/`value`, интерфейсы `<Feature>Component` и `<Feature>ComponentFactory`, `@Composable fun *Screen`; типы с Metro-аннотацией — единственное исключение, и только в ролях, которые видит финальный граф. | Запрос владельца от 7 ноября 2026, третье, четвёртое, девятое и десятое код-ревью PR #20 | Konsist-тесты `apiContractsArePublic` (включая подпакеты) и `implementationDetailsAreInternal`; unit-тесты `VisibilityRulesTest` покрывают роль, вид и модификаторы объявления: публичные вспомогательная модель в `domain.model`, helper в `di` и не-Screen композиция в `ui` дают нарушение; explicit API mode api-модулей. Правило видимости минимизирует внутренний API, а межмодульную границу защищает dependency graph: публичная доменная модель в `impl` допустима, потому что `impl -> impl` и `presentation -> impl` запрещены `architectureCheck`. |
| HB-ARCH-043 | Размещение исходников должно соответствовать Gradle-модулю: `features/<feature>/api` объявляет только пакет `features.<feature>.api` и его подпакеты, `features/<feature>/impl` — роли `domain`, `data`, `presentation`, `di`, модуль `presentation/<adapter>` — `component`, модуль `ui/<platform>` — `ui`, `core/<module>` — `core.<module>`, `di/api` — `di`, модули `shell/...` — область `shell`, `platform/...` — корневой пакет `io.github.aequicor.heartbeat`; произвольный пакет вне этой таблицы — нарушение. | Четвёртое и восьмое код-ревью PR #20 от 7 ноября 2026 | Konsist-тест `modulePackagesMatchTheGradleModule` сканирует `features`, `designsystem`, `core`, `di`, `shell` и `platform`; unit-тесты `ModulePackageRulesTest` покрывают негативный случай для каждого типа модуля; таблица соответствий задана явно в `ModulePackageRules`. |
| HB-ARCH-044 | Эффекты `<Feature>Store` должны быть необязательными одноразовыми уведомлениями: Store не ждёт подписчика, не воспроизводит пропущенный эффект, а критичный результат операции отражается в UI State. | Пятое код-ревью PR #20 от 7 ноября 2026 | Тесты `savedEffectIsNotReplayedToALateCollector` и `savesWithoutEffectCollectorDoNotBlock`; контракт доставки описан в KDoc `EditorStore`. |
| HB-ARCH-045 | Модуль `designsystem/foundation` должен компилироваться для всех обязательных targets, не зависеть от Compose, Decompose, Metro и фич и не содержать типов рендерера; Compose-тема разрешает его токены в собственные значения. | Спецификация дизайн-системы 2.0 от 7 ноября 2026 | Konsist-тест `foundationStaysRendererNeutral` отклоняет импорт Compose в `designsystem/foundation`; `architectureCheck` проверяет зависимости модуля; `theme` собирается с зависимостью от `foundation`. |

**Итог:** единственная реализация бизнес-логики и состояния фичи компилируется для всех платформ. Decompose и BLoC остаются заменяемыми Presentation-адаптерами. Compose и нативные интерфейсы используют одни и те же контракты. Metro собирает необходимые реализации в платформенном Composition Root.

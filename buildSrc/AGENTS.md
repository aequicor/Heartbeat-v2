# Инструкции для сборочных плагинов

- `buildSrc` хранит соглашения сборки. Плагин — precompiled Kotlin DSL скрипт
  `src/main/kotlin/<имя>.gradle.kts`; идентификатор плагина совпадает с именем файла.
- `heartbeat-dev-run` регистрирует `runDev` и `runDev<Os>` для Windows, Linux и macOS. `runDev` выбирает
  точку входа текущего хоста, `runDev<Os>` на другом хосте завершается с подсказкой.
- `heartbeat-detekt` применяет detekt ко всем модулям, подключает наборы правил Compose и Faire и
  регистрирует `detektAll`. Значения правил задавай в `detekt.yml`, а не в скрипте плагина.
- `heartbeat-git-hooks` регистрирует `installGitHooks`, который переключает `core.hooksPath` на
  `.githooks`. Хук `pre-push` запускает `detektAll` и блокирует push при нарушениях.
- Новую точку входа добавляй в список `entryPoints` класса `DevRuns` и синхронизируй с модулем в
  `platform/`, матрицей `verify-platform` и `docs/setup.md`.
- Не дублируй версии плагинов и репозитории: версии задавай в `gradle/libs.versions.toml`. Артефакты
  плагинов подключай в `buildSrc`, а в модулях указывай идентификатор плагина без версии.
- Проверяй изменения командой `gradlew tasks --group dev`, а запуск — `runDev` на поддерживаемом хосте.

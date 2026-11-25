# Инструкции для сборочных плагинов

- `buildSrc` хранит соглашения сборки. Плагин — precompiled Kotlin DSL скрипт
  `src/main/kotlin/<имя>.gradle.kts`; идентификатор плагина совпадает с именем файла.
- `heartbeat-dev-run` регистрирует `runDev` и `runDev<Os>` для Windows, Linux и macOS. `runDev` выбирает
  точку входа текущего хоста, `runDev<Os>` на другом хосте завершается с подсказкой.
- `heartbeat-jcef-jdk` выбирает установленный JBR 25 с JCEF или скачивает закреплённый JBRSDK с проверкой
  SHA-512 для `jlink` и runtime image. `jpackage` использует Gradle JDK; `runDev` продолжает использовать
  обычный JBR 25.
- `heartbeat-inno-setup` находит установленный `ISCC.exe` или скачивает закреплённый Inno Setup с проверкой
  SHA-256 и распаковывает portable-версию в `build`.
- `heartbeat-desktop-windows` добавляет JNA в ресурсы Windows, настраивает каталоги локального запуска
  и регистрирует задачу `packageInnoSetup`. Версию установщика берёт из настроек дистрибутива Compose.
- `heartbeat-app-cds` включает динамический AppCDS для `run` и создаёт базовый CDS-архив в `createRuntimeImage`.
- `heartbeat-desktop-distribution` подготавливает JCEF в собранном образе и запускает обучение AOT
  штатным лаунчером. Плагин проверяет запуск с архивом `classes.jsa` и без него, затем подписывает
  образ на macOS. На Linux задача упаковки DEB использует обученный образ.
  Для сборки Linux без дисплея нужны `xvfb-run` и `xauth`.
- `heartbeat-detekt` применяет detekt ко всем модулям, подключает наборы правил Compose и Faire и
  регистрирует `detektAll`. Значения правил задавай в `detekt.yml`, а не в скрипте плагина.
- `heartbeat-git-hooks` регистрирует `installGitHooks`, который переключает `core.hooksPath` на
  `.githooks`. Хук `pre-push` запускает локальные проверки из `static-analysis.yml` и
  `cross-platform.yml`, доступные на текущем хосте, и блокирует push при сбое.
- Новую точку входа добавляй в список `entryPoints` класса `DevRuns` и синхронизируй с модулем в
  `platform/`, матрицей `verify-platform` и `docs/setup.md`.
- Не дублируй версии плагинов и репозитории: версии задавай в `gradle/libs.versions.toml`. Артефакты
  плагинов подключай в `buildSrc`, а в модулях указывай идентификатор плагина без версии.
- Проверяй изменения командой `gradlew tasks --group dev`, а запуск — `runDev` на поддерживаемом хосте.

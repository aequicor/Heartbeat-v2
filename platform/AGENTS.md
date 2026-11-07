# Инструкции для платформ

- Платформенный модуль — только точка входа: запуск приложения, заставка и подключение бандлов. Общую
  функциональность выноси в общий код. [Границы компонентов](../docs/specs/architecture.md)
- Каждый модуль при запуске сообщает маркер `Heartbeat started` и завершается с кодом 0. Desktop-модуль
  печатает маркер в stdout, Android пишет его в logcat с тегом `Heartbeat`, iOS — через OSLog с
  подсистемой `io.github.aequicor.heartbeat`.
- Цели сборки: Android с `compileSdk 36` и `minSdk 23`, JVM с Compose Multiplatform для Windows, Linux
  и macOS. На Windows установщик собирает Inno Setup. iOS собирается из
  `platform/ios/Heartbeat.xcodeproj` в Xcode, а не через Gradle.
- Добавление платформы затрагивает модуль в `settings.gradle.kts`, точку входа в
  `buildSrc/src/main/kotlin/heartbeat-dev-run.gradle.kts`, матрицу `.github/workflows/verify-platform.yml`,
  раздел в `docs/setup.md` и требование в спецификации архитектуры.
- Проверяй сборку и запуск на затронутой платформе. Полную проверку всех точек входа запускает метка
  `verify-platform` на PR.

# Окружение и первый запуск

Пошаговая настройка машины: от чистой системы до запущенного приложения на каждой платформе.

## Шаг 1. Установи инструменты

Установи актуальную стабильную версию [Android Studio](https://developer.android.com/studio). IDE
включает JetBrains Runtime 25, эмулятор и инструменты Android SDK. Jewel требует именно JetBrains
Runtime, поэтому сборка desktop-модуля выбирает JDK с vendor `JETBRAINS`.

На macOS установи также Xcode: он нужен для сборки iOS из `platform/ios`. Запусти Xcode один раз,
чтобы принять лицензию.

Поддерживаемые хосты desktop: Windows 10 или новее на x64, Linux x64, macOS на Apple Silicon.
Android требует устройства или эмулятора с API 23 или новее. iOS собирается только на macOS.

## Шаг 2. Открой проект

Открой в Android Studio корень репозитория — каталог с `settings.gradle.kts`. Выбери **Trust
Project** и дождись окончания Gradle sync. IDE создаст `local.properties` с путём к Android SDK.

## Шаг 3. Проверь SDK и Gradle JDK

- **Tools → SDK Manager**: установлены SDK Platform 37 и Platform-Tools.
- **Settings → Build, Execution, Deployment → Build Tools → Gradle**: Gradle JDK — `Embedded JDK
  (JetBrains Runtime 25)`, дистрибутив — из `gradle-wrapper.properties`. Обёртка скачает
  Gradle 9.8.2.

## Шаг 4. Запусти desktop-приложение

1. В окне **Gradle** открой **Heartbeat → Tasks → dev**.
2. Дважды щёлкни `runDev`.
3. Откроется окно `Heartbeat`. В окне **Run** появится строка `Heartbeat started`. Закрой окно,
   чтобы процесс завершился с кодом 0.

`runDev` выбирает точку входа текущего хоста. Именованная задача собирает конкретную платформу,
а на другом хосте завершается с подсказкой.

| Задача | Хост |
| --- | --- |
| `runDevWindows` | Windows x64 |
| `runDevLinux` | Linux x64 |
| `runDevMacos` | macOS ARM64 |

Desktop-приложение работает на JVM и использует Compose Multiplatform. Первый запуск скачивает
зависимости Compose и Gradle; нужна сеть.

Установщик текущей ОС собирает `gradlew :platform:desktop:packageDistributionForCurrentOS`.
На Windows это установщик Inno Setup: `Heartbeat-<версия>-setup.exe` в
`platform/desktop/build/compose/binaries/main/innosetup`. Нужен Inno Setup 6.3 или новее; компилятор
`ISCC.exe` ищется в `ISCC`, `INNO_SETUP_HOME`, стандартном каталоге установки или в `PATH`. Образ приложения с
`Heartbeat.exe` собирает `gradlew :platform:desktop:createDistributable`.

## Шаг 5. Запусти приложение на Android

1. Создай устройство в **Device Manager** или подключи телефон с Android 6.0 и новее.
2. В списке конфигураций запуска выбери приложение и нажми **Run**. Для сборки APK без запуска
   подойдёт задача `:platform:android:assembleDebug`.
3. В **Logcat** по тегу `Heartbeat` появится запись `Heartbeat started`.

## Шаг 6. Запусти приложение на iOS

1. На Mac открой `platform/ios/Heartbeat.xcodeproj` в Xcode.
2. Выбери схему `Heartbeat` и симулятор iPhone, нажми **Run**.
3. Приложение сообщит `Heartbeat started` в системный журнал через OSLog с подсистемой
   `io.github.aequicor.heartbeat`.

iOS собирается из проекта Xcode, а не через Gradle.

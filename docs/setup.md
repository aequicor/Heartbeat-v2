# Окружение и первый запуск

Пошаговая настройка машины: от чистой системы до запущенного приложения на каждой платформе.

## Шаг 1. Установи инструменты

Установи актуальную стабильную версию [Android Studio](https://developer.android.com/studio). IDE
включает JetBrains Runtime 21, эмулятор и инструменты Android SDK.

На macOS установи также Xcode: Kotlin/Native берёт из него SDK и фреймворки для таргетов
`macosArm64` и `platform/ios`. Запусти Xcode один раз, чтобы принять лицензию.

Поддерживаемые хосты desktop: Windows 10 или новее на x64, Linux x64, macOS на Apple Silicon.
Android требует устройства или эмулятора с API 23 или новее. iOS собирается только на macOS.

## Шаг 2. Открой проект

Открой в Android Studio корень репозитория — каталог с `settings.gradle.kts`. Выбери **Trust
Project** и дождись окончания Gradle sync. IDE создаст `local.properties` с путём к Android SDK.

## Шаг 3. Проверь SDK и Gradle JDK

- **Tools → SDK Manager**: установлены SDK Platform 36 и Platform-Tools.
- **Settings → Build, Execution, Deployment → Build Tools → Gradle**: Gradle JDK — `Embedded JDK
  (JetBrains Runtime 21)`, дистрибутив — из `gradle-wrapper.properties`. Обёртка скачает
  Gradle 9.5.1.

## Шаг 4. Запусти desktop-приложение

1. В окне **Gradle** открой **Heartbeat → Tasks → dev**.
2. Дважды щёлкни `runDev`.
3. В окне **Run** появится строка `Heartbeat started`, и приложение завершится с кодом 0.

`runDev` выбирает точку входа текущего хоста. Именованная задача собирает конкретную платформу,
а на другом хосте завершается с подсказкой.

| Задача | Хост |
| --- | --- |
| `runDevWindows` | Windows x64 |
| `runDevLinux` | Linux x64 |
| `runDevMacos` | macOS ARM64 |

Первый запуск скачивает Kotlin/Native 2.4.20 в `~/.konan`; на Windows — также LLVM и
MinGW-тулчейн. Нужна сеть, загрузка занимает несколько минут; MSYS2 и MinGW вручную не
устанавливаются.

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

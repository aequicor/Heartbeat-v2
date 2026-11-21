# Упаковка desktop: модули runtime и каталог данных

## Применимость

`platform/desktop`, `createDistributable`/jpackage, JCEF, SQLDelight (sqlite-jdbc), DataStore;
запуск `runDev`/`runDistributableWritableTemp` в песочнице DSH на Windows.

## Симптом

- `Heartbeat storage initialization failed: [SQLITE_CANTOPEN] Unable to open the database file`:
  каталог `%LOCALAPPDATA%\Heartbeat` не создаётся из песочницы, хотя `mkdirs()` вызывается.
- `ClassNotFoundException: sun.misc.Unsafe` из `androidx.datastore...protobuf.UnsafeUtil` в
  упакованном приложении, хотя в JVM-тестах запись DataStore работает.
- На Windows `Desktop runtime is missing JCEF files` при установленном JBR 25 с JCEF.
  Compose 1.12.2 передаёт `jlink --strip-native-commands`. Этот флаг удаляет секцию `bin`
  модуля `jcef.jmod`, включая helper-процессы, ресурсы Chromium и локализации.

## Требуемое поведение

1. В `compose.desktop.application.nativeDistributions` перечислять модули, которые jpackage не
   выводит сам: `java.sql` (sqlite-jdbc) и `jdk.unsupported` (DataStore/protobuf,
   `sun.misc.Unsafe`). Проверять `runtime/release` по строке `MODULES`.
2. Для песочницы dev-запуска задавать `HEARTBEAT_DATA_DIR` на каталог внутри `build` в задаче
   `runDistributableWritableTemp`; обычная установка использует `%LOCALAPPDATA%\Heartbeat`.
3. Проверка: `createDistributable`, затем запуск `Heartbeat.exe` с `HEARTBEAT_DATA_DIR`; файл
   `heartbeat.db` создаётся, в stderr нет `SQLITE_CANTOPEN` и `ClassNotFoundException`.
4. На Windows задай `jcef.jmod` выбранного SDK как вход `createDistributable`. После упаковки
   восстанови всю секцию `bin` в `Heartbeat/runtime/bin`, сохрани вложенные пути и проверь
   обязательные файлы. Для проверки запуска выполни `runDev` и дождись окна Heartbeat.

## Исключения

На машине без песочницы `%LOCALAPPDATA%` доступен, `HEARTBEAT_DATA_DIR` не нужен. Apple/Linux
упаковка не использует Windows-задачу.

# Индекс правил окружения

| Правило | Когда применять |
| --- | --- |
| [mkdocs-venv](environment/mkdocs-venv.md) | Сборка документации MkDocs в локальном `.venv` без pip |
| [git-sandbox](environment/git-sandbox.md) | Сбой `git push` и подписанного `git commit` из-за блокировки вспомогательных процессов msys2 в песочнице DSH |
| [pwsh-gradle-output](environment/pwsh-gradle-output.md) | Проверка результата команды `gradlew` в PowerShell: обрезанный пайплайн скрывает итог и прерывает сборку |
| [gradle-android-jar-lock](environment/gradle-android-jar-lock.md) | Сбой `bundle*ClassesToCompileJar` на Windows из-за занятого `classes.jar` |
| [windows-sandbox-temp](environment/windows-sandbox-temp.md) | Сбой JVM-процесса с `AccessDeniedException` при записи в реальный `%TEMP%` в песочнице DSH на Windows |
| [sqldelight-schema-composition](storage/sqldelight-schema-composition.md) | Добавление или изменение SQLDelight-схемы: расположение `.sq`, композиция агрегата и зависимости `storage/database` |
| [jpackage-runtime](desktop/jpackage-runtime.md) | Сбой упакованного desktop-приложения: `SQLITE_CANTOPEN` или `ClassNotFoundException: sun.misc.Unsafe` при `createDistributable`/`runDev` |

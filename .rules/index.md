# Индекс правил окружения

| Правило | Когда применять |
| --- | --- |
| [mkdocs-venv](environment/mkdocs-venv.md) | Сборка документации MkDocs в локальном `.venv` без pip |
| [git-sandbox](environment/git-sandbox.md) | Сбой `git push` и подписанного `git commit` из-за блокировки вспомогательных процессов msys2 в песочнице DSH |
| [pwsh-gradle-output](environment/pwsh-gradle-output.md) | Проверка результата команды `gradlew` в PowerShell: обрезанный пайплайн скрывает итог и прерывает сборку |
| [windows-gradle-jbr](environment/windows-gradle-jbr.md) | Запуск Gradle на Windows из оболочки с системной Java 17 вместо JBR Android Studio |
| [cua-target-window](environment/cua-target-window.md) | Кадр Windows не соответствует выбранному окну или его актуальному размеру в `@oai/sky` |
| [cua-screenshot-encoding](environment/cua-screenshot-encoding.md) | Пиксельный анализ сглаживания или резкости по снимкам CUA |
| [skia-empty-text-shadow](environment/skia-empty-text-shadow.md) | Проверка дополнительного покрытия текста через совпадающую тень Skia на Windows |
| [compose-intrinsic-overflow](testing/compose-intrinsic-overflow.md) | Проверка переполнения TextLayoutResult при intrinsic-ширине в Compose JVM |
| [compose-desktop-wheel-anchor](testing/compose-desktop-wheel-anchor.md) | Проверка места чтения в Compose Desktop при обновлении текста и инерции колеса |
| [macos-window-presentation](testing/macos-window-presentation.md) | Проверка видимого пропуска кадров в окне Compose Desktop на macOS |
| [desktop-jcef-test-runtime](testing/desktop-jcef-test-runtime.md) | JVM-тест диаграмм при нескольких установленных JBR 25 |
| [gradle-android-jar-lock](environment/gradle-android-jar-lock.md) | Сбой `bundle*ClassesToCompileJar` на Windows из-за занятого `classes.jar` |
| [android-emulator-frame-timing](environment/android-emulator-frame-timing.md) | Измерение времени кадра на Android Emulator с аппаратным или программным GPU на macOS |
| [android-studio-emulator-window](environment/android-studio-emulator-window.md) | ADB видит тестовый AVD на macOS, а в Running Devices нет его экрана |
| [kotlin-incremental-classpath](environment/kotlin-incremental-classpath.md) | Сбой инкрементальной компиляции Kotlin JVM с `DirtyData.dirtyLookupSymbols` после изменения вложенного типа |
| [windows-sandbox-temp](environment/windows-sandbox-temp.md) | Сбой JVM-процесса с `AccessDeniedException` при записи в реальный `%TEMP%` в песочнице DSH на Windows |
| [sqldelight-schema-composition](storage/sqldelight-schema-composition.md) | Добавление или изменение SQLDelight-схемы: расположение `.sq`, композиция агрегата и зависимости `storage/database` |
| [jpackage-runtime](desktop/jpackage-runtime.md) | Сбой упаковки JCEF на Windows, `SQLITE_CANTOPEN` или `ClassNotFoundException: sun.misc.Unsafe` при `createDistributable`/`runDev` |
| [apply-patch-order](tooling/apply-patch-order.md) | Несколько блоков `@@` для одного файла в `apply_patch` |
| [rg-powershell-globs](tooling/rg-powershell-globs.md) | Шаблон имени файла в поиске `rg` из PowerShell на Windows |
| [selection-container-layout](testing/selection-container-layout.md) | Несколько текстовых чанков внутри SelectionContainer в Compose UI-тесте |

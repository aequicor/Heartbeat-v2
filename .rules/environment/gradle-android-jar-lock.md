# Блокировка jar Android-промежуточных классов

## Условия применимости

Windows, Gradle KMP-модуль с плагином `com.android.kotlin.multiplatform.library`
(`designsystem/*`, `features/*`, `core/*`), задачи `bundleAndroidMainClassesToCompileJar` или
`bundle*ClassesToCompileJar`.

## Симптом

Задача падает с `java.nio.file.FileSystemException` на
`build/intermediates/compile_library_classes_jar/<sourceSet>/bundle.../classes.jar`:
«Процесс не может получить доступ к файлу, так как этот файл занят другим процессом».
Повторный запуск той же команды воспроизводит ошибку, пока жив процесс, удерживающий файл.

## Требуемое поведение

Останови демон и удали каталог промежуточных классов, затем повтори команду:

```powershell
.\gradlew.bat --stop
Start-Sleep -Seconds 2
Remove-Item -Recurse -Force <module>\build\intermediates\compile_library_classes_jar
.\gradlew.bat <task>
```

В удалённом примере это `designsystem/compose/components/build/intermediates/compile_library_classes_jar`.
Не меняй исходники и настройки сборки ради обхода: ошибка относится к занятому файлу, а не к коду.

## Исключения

Если после остановки демона файл всё ещё занят (например, его удерживает IDE), закрой процесс,
который держит каталог, либо перезапусти IDE. Не удаляй весь каталог `build` модуля без
необходимости: это удлиняет следующую сборку.

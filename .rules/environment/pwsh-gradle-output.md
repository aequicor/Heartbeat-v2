# Вывод Gradle в PowerShell

## Условия применимости

Windows, PowerShell (`pwsh`), команды `gradlew`/`gradlew.bat`, вывод которых фильтруется в пайплайне.

## Симптом

Команда `.\gradlew.bat <task> ... | Select-String -Pattern '...' | Select-Object -First N` печатает
только первые строки и не показывает `BUILD SUCCESSFUL`/`BUILD FAILED`. Сборка может остаться
незавершённой: `Select-Object -First` закрывает пайплайн, из-за чего процесс Gradle теряет stdout и
прерывается.

## Поведение

Не ограничивай потоковый вывод Gradle через `Select-Object -First` и не проверяй результат по
усечённому пайплайну. Направь вывод в файл и фильтруй файл:

```powershell
.\gradlew.bat <task> *> "$env:TEMP\gradle.txt"; "exit: $LASTEXITCODE"
Select-String -Path "$env:TEMP\gradle.txt" -Pattern '^e:|BUILD SUCCESSFUL|BUILD FAILED'
```

`$LASTEXITCODE` — надёжный признак результата; строки `BUILD SUCCESSFUL`/`BUILD FAILED` в файле
подтверждают, что задача дошла до конца.

## Исключения

`Select-Object -Last N` по уже завершённой команде безопасен, как и короткие команды без фильтра.

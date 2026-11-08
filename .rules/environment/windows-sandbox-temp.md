# Только для чтения %TEMP% в песочнице DSH на Windows

## Применимость

Windows, файловая песочница DSH (`workspace-write`), запуск процесса JVM, который создаёт
временные файлы (`java.nio.file.Files.createTempFile`, `java.awt.Font.createFont` и т. п.).

## Условие

Процесс падает с `java.nio.file.AccessDeniedException` при создании файла в реальном системном
temp `C:\Users\<user>\AppData\Local\Temp`. Директория доступна, но запись запрещена: песочница
разрешает запись только в workspace (каталог проекта/`user.dir`) и в перенаправленный temp
`...\Temp\dsh-*` (переменные `TMP`/`TEMP` для оболочки DSH).

## Требуемое поведение

Перенаправляй temp на этапе запуска процесса, а не из кода: задай переменные окружения
`TMP`/`TEMP` (или JVM-аргумент `-Djava.io.tmpdir`) на записываемую директорию до старта JVM.
`System.setProperty("java.io.tmpdir", ...)` внутри кода не работает — JVM кэширует расположение
temp при запуске, и `Files.createTempFile` продолжает использовать старое значение.

## Исключения

При политике `danger-full-access` реальный `%TEMP%` снова доступен на запись, и проблема не
воспроизводится.

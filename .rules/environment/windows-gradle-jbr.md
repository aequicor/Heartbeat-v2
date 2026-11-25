# JVM для Gradle на Windows

## Применимость

Запуск Gradle Heartbeat-v2 из PowerShell на Windows вне Android Studio.

## Условие

Оболочка может выбирать системную Java 17, хотя Android Studio использует JetBrains Runtime 25.
В этом случае `:buildSrc:generateExternalPluginSpecBuilders` не разрешает Metro 1.5.2:
зависимость требует JVM 21 или новее. Это ошибка JVM сборки, а не Kotlin toolchain модуля.

## Требуемое поведение

Перед первым запуском Gradle сверяй JVM оболочки с [настройкой проекта](../../docs/setup.md).
Если выбрана Java 17, задай `JAVA_HOME` только для запускаемой оболочки на установленный полный
JDK 26. Для упаковки проверь наличие `bin/jpackage.exe` у Gradle JDK: на этой машине используй
`C:/Users/undfr/.jdks/openjdk-26.0.2`. JBR 25 с JCEF для `jlink` и runtime image выбирается отдельно.
Не меняй системные переменные, зависимости и настройки toolchain ради этого сбоя.

## Исключения

Если оболочка уже использует требуемый JDK, переопределять `JAVA_HOME` не нужно. Для другой
установки Android Studio сначала проверь её фактический путь и версию runtime.

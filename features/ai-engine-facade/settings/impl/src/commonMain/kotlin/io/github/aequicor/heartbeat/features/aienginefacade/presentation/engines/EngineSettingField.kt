package io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines

public enum class EngineSettingField(public val label: String, private vararg val synonyms: String) {

    Preset("Пресет", "шаблон"),
    ConnectionName("Название подключения"),
    Command("Команда запуска", "path", "путь", "cli"),
    Arguments("Аргументы", "argv"),
    Separator("Разделитель", "пробел", "запятая"),
    Environment("Переменные среды", "среда", "окружение", "env"),
    EngineName("Имя движка"),
    ResponseFormat("Формат ответа"),
    Rules("Правила движка", "инструкции");

    public val group: String
        get() = if (connectionField) "Подключение ACP" else "Настройки движка"

    internal val connectionField: Boolean
        get() = this != EngineName && this != ResponseFormat && this != Rules

    public companion object {

        private val wordDelimiter = Regex("""\s+""")

        @Suppress("DoNotSplitByRegex")
        public fun search(query: String): List<EngineSettingField> {
            val terms = query.trim().lowercase().split(wordDelimiter)
            return entries.filter { field ->
                val words = field.label.lowercase().split(' ') + field.synonyms
                terms.all { term -> words.any { it.startsWith(term) } }
            }
        }
    }
}

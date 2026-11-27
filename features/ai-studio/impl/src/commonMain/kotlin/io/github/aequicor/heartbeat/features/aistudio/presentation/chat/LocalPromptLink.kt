package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

public data class LocalPromptLink(val label: String, val uri: String, val range: IntRange) {

    public companion object {

        public fun findIn(text: String): Sequence<LocalPromptLink> = Pattern.findAll(text).map { match ->
            val (label, wrapped, bare) = match.destructured
            LocalPromptLink(label.replace(Escape) { it.groupValues[1] }, wrapped.ifEmpty { bare }, match.range)
        }

        private val Pattern = Regex(
            """(?<!\\)\[((?:\\.|[^\]\\\r\n])+)]\((?:<((?:file|content):[^>\r\n]+)>|((?:file|content):[^)\s]+))\)""",
            RegexOption.IGNORE_CASE,
        )
        private val Escape = Regex("""\\(.)""")
    }
}

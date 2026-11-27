package io.github.aequicor.heartbeat.features.aistudio.domain.model

import kotlinx.serialization.json.Json

internal data class ImageMarkDescription(val text: String, val marks: List<ImageMark>) {

    fun footer(): String = if (marks.isEmpty()) "" else buildString {
        append("marks:")
        for (mark in marks) {
            append("\n  - name: ").append(quote(mark.name))
            append("\n    position: [").append(mark.x).append(", ").append(mark.y).append(']')
            append("\n    description: ").append(quote(mark.description))
        }
    }

    fun encode(): String? = listOf(text.trim(), footer()).filter(String::isNotEmpty)
        .joinToString("\n\n").takeIf(String::isNotEmpty)

    companion object {

        private val entry = Regex(
            """  - name: ("(?:[^"\\]|\\.)*")\n    position: \[(\d+), (\d+)]\n    description: ("(?:[^"\\]|\\.)*")""",
        )

        fun decode(description: String?): ImageMarkDescription {
            val text = description.orEmpty()
            val separator = text.lastIndexOf("\n\nmarks:\n")
            val footer = when {
                separator >= 0 -> text.substring(separator + 2)
                text.startsWith("marks:\n") -> text
                else -> return ImageMarkDescription(text, emptyList())
            }
            val marks = parseFooter(footer) ?: return ImageMarkDescription(text, emptyList())
            return ImageMarkDescription(if (separator >= 0) text.substring(0, separator) else "", marks)
        }

        private fun parseFooter(footer: String): List<ImageMark>? = buildList {
            var offset = "marks:".length
            while (offset < footer.length) {
                if (footer[offset] != '\n') return null
                val match = entry.matchAt(footer, offset + 1) ?: return null
                val (name, x, y, description) = match.destructured
                val unquoted = runCatching {
                    Json.decodeFromString<String>(name) to Json.decodeFromString<String>(description)
                }.getOrNull() ?: return null
                if (unquoted.first.isBlank()) return null
                add(ImageMark(x.toIntOrNull() ?: return null, y.toIntOrNull() ?: return null, unquoted.first, unquoted.second))
                offset = match.range.last + 1
            }
            if (isEmpty()) return null
        }

        private fun quote(value: String): String = Json.encodeToString(value)
            .replace("\u0085", "\\u0085").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    }
}

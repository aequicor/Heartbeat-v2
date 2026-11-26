package io.github.aequicor.heartbeat.features.aiengine.api.session

public data class SessionQuestionnaire(
    val id: String,
    val message: String,
    val fields: List<Field>,
) {

    public data class Field(
        val id: String,
        val title: String,
        val description: String?,
        val required: Boolean,
        val type: Type,
        val options: List<Option> = emptyList(),
        val minLength: Int? = null,
        val maxLength: Int? = null,
        val pattern: String? = null,
        val minItems: Long? = null,
        val maxItems: Long? = null,
        val minInteger: Long? = null,
        val maxInteger: Long? = null,
        val minNumber: Double? = null,
        val maxNumber: Double? = null,
        val default: QuestionnaireAnswer.Value? = null,
    )

    public enum class Type { Text, Choice, MultiChoice, Boolean, Integer, Number }

    public data class Option(val value: String, val label: String)
}

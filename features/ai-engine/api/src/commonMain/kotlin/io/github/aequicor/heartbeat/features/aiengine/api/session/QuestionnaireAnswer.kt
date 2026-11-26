package io.github.aequicor.heartbeat.features.aiengine.api.session

public sealed interface QuestionnaireAnswer {

    public data class Accept(val values: Map<String, Value>) : QuestionnaireAnswer

    public data object Decline : QuestionnaireAnswer

    public sealed interface Value {

        public data class Text(val value: String) : Value

        public data class Choices(val values: List<String>) : Value

        public data class Flag(val value: Boolean) : Value

        public data class Integer(val value: Long) : Value

        public data class Number(val value: Double) : Value
    }
}

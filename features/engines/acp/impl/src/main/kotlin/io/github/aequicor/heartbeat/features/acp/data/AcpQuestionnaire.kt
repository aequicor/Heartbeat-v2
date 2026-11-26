package io.github.aequicor.heartbeat.features.acp.data

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.ElicitationAction
import com.agentclientprotocol.model.ElicitationContentValue
import com.agentclientprotocol.model.ElicitationPropertySchema
import com.agentclientprotocol.model.ElicitationSchema
import com.agentclientprotocol.model.MultiSelectItems
import io.github.aequicor.heartbeat.features.aiengine.api.session.QuestionnaireAnswer
import io.github.aequicor.heartbeat.features.aiengine.api.session.QuestionnaireAnswer.Value
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionQuestionnaire
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionQuestionnaire.Field
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionQuestionnaire.Option
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionQuestionnaire.Type

@OptIn(UnstableApi::class)
internal fun ElicitationSchema.toQuestionnaire(id: String, message: String): SessionQuestionnaire =
    SessionQuestionnaire(id, message, properties.map { (name, property) ->
        property.toField(name, name in required.orEmpty())
    })

@OptIn(UnstableApi::class)
private fun ElicitationPropertySchema.toField(name: String, required: Boolean): Field = when (this) {
    is ElicitationPropertySchema.StringProperty -> {
        val options = oneOf?.map { Option(it.value, it.title) }
            ?: enumValues.orEmpty().map { Option(it, it) }
        Field(
            name, title ?: name, description, required, if (options.isEmpty()) Type.Text else Type.Choice,
            options, minLength = minLength, maxLength = maxLength, pattern = pattern,
            default = default?.let(Value::Text),
        )
    }
    is ElicitationPropertySchema.BooleanProperty -> Field(
        name, title ?: name, description, required, Type.Boolean, default = default?.let(Value::Flag),
    )
    is ElicitationPropertySchema.IntegerProperty -> Field(
        name, title ?: name, description, required, Type.Integer, minInteger = minimum, maxInteger = maximum,
        default = default?.let(Value::Integer),
    )
    is ElicitationPropertySchema.NumberProperty -> Field(
        name, title ?: name, description, required, Type.Number, minNumber = minimum, maxNumber = maximum,
        default = default?.let(Value::Number),
    )
    is ElicitationPropertySchema.ArrayProperty -> {
        val options = when (val choices = items) {
            is MultiSelectItems.Titled -> choices.items.options.map { Option(it.value, it.title) }
            is MultiSelectItems.Untitled -> choices.items.values.map { Option(it, it) }
        }
        Field(
            name, title ?: name, description, required, Type.MultiChoice, options,
            minItems = minItems, maxItems = maxItems, default = default?.let(Value::Choices),
        )
    }
}

@OptIn(UnstableApi::class)
internal fun QuestionnaireAnswer.toElicitationAction(): ElicitationAction = when (this) {
    QuestionnaireAnswer.Decline -> ElicitationAction.Decline
    is QuestionnaireAnswer.Accept -> ElicitationAction.Accept(values.mapValues { (_, value) ->
        when (value) {
            is Value.Text -> ElicitationContentValue.StringValue(value.value)
            is Value.Choices -> ElicitationContentValue.StringArrayValue(value.values)
            is Value.Flag -> ElicitationContentValue.BooleanValue(value.value)
            is Value.Integer -> ElicitationContentValue.IntegerValue(value.value)
            is Value.Number -> ElicitationContentValue.NumberValue(value.value)
        }
    })
}

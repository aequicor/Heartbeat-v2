package io.github.aequicor.heartbeat.navigation.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Transport route at the feature boundary.
 *
 * Feature modules declare typed factories for their own routes, so the shell never enumerates
 * screens. The raw [parameters] are validated by the owning `FeatureEntry`; UI and stores work
 * with typed arguments.
 */
data class AppRoute(
    val featureId: String,
    val destination: String = "root",
    val parameters: Map<String, String> = mapOf(),
) {

    companion object {

        /** Serializer used by the shell navigation stack to restore the route history. */
        val serializer: KSerializer<AppRoute> = AppRouteSerializer
    }
}

/**
 * Hand-written serializer: the navigation contract module does not apply the serialization
 * compiler plugin, so the transport type carries its own serializer instead of `@Serializable`.
 */
private object AppRouteSerializer : KSerializer<AppRoute> {

    private val parametersSerializer: KSerializer<Map<String, String>> =
        MapSerializer(String.serializer(), String.serializer())

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("io.github.aequicor.heartbeat.navigation.api.AppRoute") {
            element<String>("featureId")
            element<String>("destination")
            element("parameters", parametersSerializer.descriptor)
        }

    override fun serialize(encoder: Encoder, value: AppRoute) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeStringElement(descriptor, 0, value.featureId)
        composite.encodeStringElement(descriptor, 1, value.destination)
        composite.encodeSerializableElement(descriptor, 2, parametersSerializer, value.parameters)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): AppRoute {
        val composite = decoder.beginStructure(descriptor)
        var featureId = ""
        var destination = "root"
        var parameters = mapOf<String, String>()

        while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break
                0 -> featureId = composite.decodeStringElement(descriptor, 0)
                1 -> destination = composite.decodeStringElement(descriptor, 1)
                2 -> parameters =
                    composite.decodeSerializableElement(descriptor, 2, parametersSerializer)

                else -> error("Unexpected AppRoute field index: $index")
            }
        }

        composite.endStructure(descriptor)

        return AppRoute(
            featureId = featureId,
            destination = destination,
            parameters = parameters,
        )
    }
}

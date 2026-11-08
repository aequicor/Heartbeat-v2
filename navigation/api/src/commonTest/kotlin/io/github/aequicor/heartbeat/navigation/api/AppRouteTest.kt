package io.github.aequicor.heartbeat.navigation.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalSerializationApi::class)
class AppRouteTest {

    @Test
    fun serializerDescribesTheTransportContract() {
        val descriptor = AppRoute.serializer.descriptor

        assertEquals(3, descriptor.elementsCount)
        assertEquals("featureId", descriptor.getElementName(0))
        assertEquals("destination", descriptor.getElementName(1))
        assertEquals("parameters", descriptor.getElementName(2))
    }

    @Test
    fun routeDefaultsToTheFeatureRoot() {
        val route = AppRoute(featureId = "editor")

        assertEquals("root", route.destination)
        assertEquals(mapOf(), route.parameters)
    }
}

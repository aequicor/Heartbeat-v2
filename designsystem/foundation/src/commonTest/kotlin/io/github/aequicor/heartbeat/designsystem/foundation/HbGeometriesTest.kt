package io.github.aequicor.heartbeat.designsystem.foundation

import kotlin.test.Test
import kotlin.test.assertEquals

/** Density defaults and their resolution (HB-DS-009). */
class HbGeometriesTest {

    @Test
    fun denseKeepsTheCompactDefaults() {
        val geometry = HbGeometries.forDensity(HbDensity.Dense)

        assertEquals(32, geometry.controlHeight)
        assertEquals(32, geometry.navigationRowHeight)
        assertEquals(6, geometry.controlRadius)
    }

    @Test
    fun comfortableWidensControlsWithoutChangingRadii() {
        val geometry = HbGeometries.forDensity(HbDensity.Comfortable)

        assertEquals(40, geometry.controlHeight)
        assertEquals(40, geometry.minimumTarget)
        assertEquals(6, geometry.controlRadius)
        assertEquals(8, geometry.fieldRadius)
    }

    @Test
    fun touchUsesTouchTargetsAndRadii() {
        val geometry = HbGeometries.forDensity(HbDensity.Touch)

        assertEquals(48, geometry.controlHeight)
        assertEquals(48, geometry.minimumTarget)
        assertEquals(56, geometry.settingsRowHeight)
        assertEquals(8, geometry.controlRadius)
        assertEquals(12, geometry.fieldRadius)
        assertEquals(16, geometry.floatingSurfaceRadius)
    }
}

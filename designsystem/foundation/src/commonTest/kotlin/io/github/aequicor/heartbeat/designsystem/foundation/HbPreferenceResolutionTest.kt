package io.github.aequicor.heartbeat.designsystem.foundation

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Resolution of the system preference markers (HB-DS-005, HB-DS-011): `System` follows the value the
 * host reports, a concrete value passes through, and a host that reports nothing falls back to the
 * documented default.
 */
class HbPreferenceResolutionTest {

    @Test
    fun systemMotionFollowsTheReportedHostValue() {
        assertEquals(HbMotionPreference.Reduced, HbMotionPreference.System.resolve(HbMotionPreference.Reduced))
        assertEquals(HbMotionPreference.Full, HbMotionPreference.System.resolve(HbMotionPreference.Full))
    }

    @Test
    fun systemMotionDefaultsToFullWhenTheHostReportsNothing() {
        assertEquals(HbMotionPreference.Full, HbMotionPreference.System.resolve(HbMotionPreference.System))
    }

    @Test
    fun concreteMotionPassesThroughTheReportedValue() {
        assertEquals(HbMotionPreference.Reduced, HbMotionPreference.Reduced.resolve(HbMotionPreference.Full))
        assertEquals(HbMotionPreference.Full, HbMotionPreference.Full.resolve(HbMotionPreference.Reduced))
    }

    @Test
    fun systemContrastFollowsTheReportedHostValue() {
        assertEquals(HbContrastPreference.High, HbContrastPreference.System.resolve(HbContrastPreference.High))
        assertEquals(HbContrastPreference.Standard, HbContrastPreference.System.resolve(HbContrastPreference.Standard))
    }

    @Test
    fun systemContrastDefaultsToStandardWhenTheHostReportsNothing() {
        assertEquals(HbContrastPreference.Standard, HbContrastPreference.System.resolve(HbContrastPreference.System))
    }

    @Test
    fun concreteContrastPassesThroughTheReportedValue() {
        assertEquals(HbContrastPreference.High, HbContrastPreference.High.resolve(HbContrastPreference.Standard))
        assertEquals(HbContrastPreference.Standard, HbContrastPreference.Standard.resolve(HbContrastPreference.High))
    }
}

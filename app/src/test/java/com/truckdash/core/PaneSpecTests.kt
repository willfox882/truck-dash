package com.truckdash.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaneSpecTest {
    @Test fun heightFollowsAspectRatioAtDesignWidth() {
        assertEquals(782.2f, PaneRules.heightDp(1440f, 2560f), 0.1f)   // full 9:16
        assertEquals(391.1f, PaneRules.heightDp(1440f, 1280f), 0.1f)   // top/bottom half
        assertEquals(293.3f, PaneRules.heightDp(810f, 540f), 0.1f)     // 3:2 strip
        assertEquals(440f, PaneRules.heightDp(500f, 500f), 0.01f)      // square
    }

    @Test fun fullAndQuadrantStayNormal() {
        val full = PaneRules.classify(PaneRules.heightDp(1440f, 2560f))
        assertFalse(full.compact); assertFalse(full.tiny)
        val quadrant = PaneRules.classify(PaneRules.heightDp(720f, 1280f)) // 9:16 again
        assertFalse(quadrant.compact); assertFalse(quadrant.tiny)
    }

    @Test fun halfIsCompactNotTiny() {
        val half = PaneRules.classify(PaneRules.heightDp(1440f, 1280f))
        assertTrue(half.compact); assertFalse(half.tiny)
    }

    @Test fun stripIsTinyAndTinyImpliesCompact() {
        val strip = PaneRules.classify(PaneRules.heightDp(810f, 540f))
        assertTrue(strip.tiny); assertTrue(strip.compact)
    }

    @Test fun thresholdsWithoutHistory() {
        assertFalse(PaneRules.classify(620f).compact)
        assertTrue(PaneRules.classify(619.9f).compact)
        assertFalse(PaneRules.classify(330f).tiny)
        assertTrue(PaneRules.classify(329.9f).tiny)
    }

    @Test fun hysteresisPreventsFlicker() {
        val t = PaneTracker()
        // start tall, shrink slowly: stays normal until 24 dp below the threshold
        assertFalse(t.update(440f, 640f).compact)
        assertFalse(t.update(440f, 610f).compact)
        assertFalse(t.update(440f, 597f).compact)
        assertTrue(t.update(440f, 595f).compact)
        // grow back: stays compact until 24 dp above the threshold
        assertTrue(t.update(440f, 620f).compact)
        assertTrue(t.update(440f, 643f).compact)
        assertFalse(t.update(440f, 645f).compact)
        // jitter around the threshold never flips
        val results = (0 until 20).map { t.update(440f, if (it % 2 == 0) 612f else 628f).compact }.toSet()
        assertEquals(1, results.size)
    }

    @Test fun tinyHysteresis() {
        val t = PaneTracker()
        assertTrue(t.update(440f, 300f).tiny)
        assertTrue(t.update(440f, 340f).tiny)       // still tiny inside the band
        assertTrue(t.update(440f, 340f).compact)
        assertFalse(t.update(440f, 360f).tiny)
        assertTrue(t.update(440f, 360f).compact)
        assertFalse(t.update(440f, 340f).tiny)      // and does not come back inside the band
        assertTrue(t.update(440f, 300f).tiny)
    }

    @Test fun updateIsIdempotent() {
        val t = PaneTracker()
        val a = t.update(440f, 600f)
        assertEquals(a, t.update(440f, 600f))
        assertEquals(a, t.update(440f, 600f))
    }

    @Test fun extremeAspectRatios() {
        assertTrue(PaneRules.classify(PaneRules.heightDp(4000f, 100f)).tiny)      // ultra wide
        val tall = PaneRules.classify(PaneRules.heightDp(100f, 10_000f))          // ultra tall
        assertFalse(tall.compact); assertFalse(tall.tiny)
        assertTrue(PaneRules.heightDp(1e-30f, 1e30f).isFinite())
    }

    @Test fun badSizesFallBackToFull() {
        for ((w, h) in listOf(0f to 0f, 0f to 100f, 100f to 0f, -5f to 100f, Float.NaN to 100f, 100f to Float.NaN,
            Float.POSITIVE_INFINITY to 100f, 100f to Float.POSITIVE_INFINITY)) {
            val s = PaneTracker().update(w, h)
            assertEquals("$w x $h", PaneRules.FALLBACK_HEIGHT_DP, s.heightDp, 0.01f)
            assertFalse(s.compact); assertFalse(s.tiny)
        }
        // a transient bad measurement while compact must not leave the pane stuck in a wrong state
        val t = PaneTracker()
        assertTrue(t.update(440f, 391f).compact)
        assertFalse(t.update(0f, 0f).compact)
        assertTrue(t.update(440f, 391f).compact)
    }
}

package com.truckdash.core

/**
 * Shape of the pane the app is drawn in, in design dp (the UI is laid out as if 440 dp wide).
 * Full screen and quadrant panes are 9:16 (about 782 dp tall) and stay "normal"; a top/bottom split is about
 * 391 dp tall (compact); a short strip is tiny. Identical name and thresholds in the sister app Markets Board.
 */
data class PaneSpec(val heightDp: Float, val compact: Boolean, val tiny: Boolean) {
    companion object {
        val Full = PaneSpec(PaneRules.FALLBACK_HEIGHT_DP, compact = false, tiny = false)
    }
}

object PaneRules {
    const val DESIGN_WIDTH_DP = 440f
    const val COMPACT_BELOW_DP = 620f
    const val TINY_BELOW_DP = 330f
    const val HYSTERESIS_DP = 24f

    /** Height of a 9:16 pane (440 x 782), used for zero, NaN or infinite sizes so a bad measurement never shrinks the UI. */
    const val FALLBACK_HEIGHT_DP = 782f

    /** Pane height in design dp from the real pixel size. Non-finite or non-positive sizes give [FALLBACK_HEIGHT_DP]. */
    fun heightDp(widthPx: Float, heightPx: Float): Float {
        if (!widthPx.isFinite() || !heightPx.isFinite() || widthPx <= 0f || heightPx <= 0f) return FALLBACK_HEIGHT_DP
        val h = DESIGN_WIDTH_DP * heightPx / widthPx
        return if (h.isFinite()) h else FALLBACK_HEIGHT_DP
    }

    /**
     * One threshold with hysteresis. With no previous state the plain threshold applies (below = true).
     * Once false it needs to drop below threshold - 24 to turn true; once true it needs to exceed threshold + 24 to turn false.
     */
    fun flag(heightDp: Float, threshold: Float, was: Boolean?): Boolean = when (was) {
        null -> heightDp < threshold
        false -> heightDp < threshold - HYSTERESIS_DP
        true -> heightDp <= threshold + HYSTERESIS_DP
    }

    /** Stateless classification (no hysteresis), for tests and one-off use. */
    fun classify(heightDp: Float): PaneSpec = classify(heightDp, null, null)

    /** Classification with the previous result, `tiny` implies `compact`. */
    fun classify(heightDp: Float, wasCompact: Boolean?, wasTiny: Boolean?): PaneSpec {
        val tiny = flag(heightDp, TINY_BELOW_DP, wasTiny)
        val compact = tiny || flag(heightDp, COMPACT_BELOW_DP, wasCompact)
        return PaneSpec(heightDp, compact, tiny)
    }
}

/** Keeps the hysteresis state between measurements (hold it in `remember`). Same input always gives the same output. */
class PaneTracker {
    private var compact: Boolean? = null
    private var tiny: Boolean? = null

    fun update(widthPx: Float, heightPx: Float): PaneSpec {
        val s = PaneRules.classify(PaneRules.heightDp(widthPx, heightPx), compact, tiny)
        compact = s.compact
        tiny = s.tiny
        return s
    }
}

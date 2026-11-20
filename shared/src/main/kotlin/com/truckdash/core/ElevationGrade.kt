package com.truckdash.core

/** Sums climbing with a hysteresis band so GPS altitude jitter does not inflate the total. */
class ElevationAccumulator(private val hysteresisM: Double = 3.0) {
    private var ref: Double? = null
    var gainM = 0.0
        private set

    fun add(altM: Double) {
        val r = ref
        if (r == null) {
            ref = altM
            return
        }
        val d = altM - r
        if (d >= hysteresisM) {
            gainM += d
            ref = altM
        } else if (d <= -hysteresisM) {
            ref = altM
        }
    }
}

/** Road grade % from altitude change over a trailing distance window. */
class GradeEstimator(private val windowM: Double = 250.0) {
    private data class S(val dist: Double, val alt: Double)

    private val buf = ArrayDeque<S>()
    private var dist = 0.0
    private var smooth: Double? = null

    /** @return grade percent (+ uphill), or null until enough distance has accumulated. */
    fun add(deltaM: Double, altM: Double): Float? {
        dist += deltaM
        buf.addLast(S(dist, altM))
        while (buf.size > 2 && dist - buf.first().dist > windowM * 1.5) buf.removeFirst()
        val first = buf.first()
        val span = dist - first.dist
        if (span < windowM * 0.6) return null
        val raw = ((altM - first.alt) / span * 100.0).coerceIn(-20.0, 20.0)
        // Output smoothing over about 100 m of driving: GPS altitude is only good to several metres.
        val prev = smooth
        val out = if (prev == null) raw else prev + (raw - prev) * (deltaM / 100.0).coerceIn(0.0, 1.0)
        smooth = out
        return out.toFloat()
    }

    fun reset() {
        buf.clear()
        dist = 0.0
        smooth = null
    }
}

package com.truckdash.core

/** Speed (km/h) above which Trips/Health are locked when the "Driving lock" setting is on. */
const val LOCK_KMH = 5f

/** Pure rule for the optional driving lock. Off by default: when [lockEnabled] is false nothing is ever locked. */
object SpeedLock {
    fun isLocked(speedKmh: Float, lockEnabled: Boolean, passengerMode: Boolean): Boolean =
        lockEnabled && !passengerMode && speedKmh > LOCK_KMH
}

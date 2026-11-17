package com.truckdash.core

import com.truckdash.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedLockTest {
    @Test fun settingDefaultsToOff() {
        assertFalse(Settings().drivingLock)
    }

    @Test fun lockOnBlocksAboveFiveKmh() {
        assertEquals(5f, LOCK_KMH, 0f)
        assertFalse(SpeedLock.isLocked(0f, lockEnabled = true, passengerMode = false))
        assertFalse(SpeedLock.isLocked(5f, lockEnabled = true, passengerMode = false)) // not strictly above
        assertTrue(SpeedLock.isLocked(5.1f, lockEnabled = true, passengerMode = false))
        assertTrue(SpeedLock.isLocked(110f, lockEnabled = true, passengerMode = false))
    }

    @Test fun passengerModeUnlocksWhenLockIsOn() {
        assertFalse(SpeedLock.isLocked(110f, lockEnabled = true, passengerMode = true))
    }

    @Test fun lockOffNeverLocksAtAnySpeed() {
        for (kmh in listOf(0f, 4.9f, 5.1f, 30f, 110f, 250f)) {
            assertFalse(SpeedLock.isLocked(kmh, lockEnabled = false, passengerMode = false))
            assertFalse(SpeedLock.isLocked(kmh, lockEnabled = false, passengerMode = true))
        }
    }
}

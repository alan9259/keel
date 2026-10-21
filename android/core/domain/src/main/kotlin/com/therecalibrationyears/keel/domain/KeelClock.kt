package com.therecalibrationyears.keel.domain

import java.time.Instant
import java.time.ZoneId

/** The current time and zone, injected so no rule depends on the wall clock (tests use UTC). */
interface KeelClock {
    fun now(): Instant
    fun zone(): ZoneId

    object System : KeelClock {
        override fun now(): Instant = Instant.now()
        override fun zone(): ZoneId = ZoneId.systemDefault()
    }
}

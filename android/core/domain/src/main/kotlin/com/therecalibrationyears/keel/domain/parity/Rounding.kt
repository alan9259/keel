package com.therecalibrationyears.keel.domain.parity

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

/**
 * Swift's `.rounded()`: to nearest, halves away from zero (2.5 → 3, -2.5 → -3). Kotlin's
 * `round` and `Math.round` differ on halves, so iOS rules port through this (plan §1.6).
 */
fun roundHalfAwayFromZero(x: Double): Double = sign(x) * floor(abs(x) + 0.5)

/**
 * Swift `String(format: "%.1f", x)`: the exact binary value rounded half-to-even, as C
 * printf does (7.25 → "7.2"). `String.format` rounds half-up ("7.3"), so it would drift
 * from iOS on exact ties.
 */
fun formatOneDecimal(x: Double): String = BigDecimal(x).setScale(1, RoundingMode.HALF_EVEN).toPlainString()

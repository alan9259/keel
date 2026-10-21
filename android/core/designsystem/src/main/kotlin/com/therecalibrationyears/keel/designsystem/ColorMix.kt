package com.therecalibrationyears.keel.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.math.cbrt
import kotlin.math.pow

/**
 * Mixes [this] toward [other] by [fraction] (0 = this, 1 = other) in OKLab, matching
 * SwiftUI's default `Color.mix(with:by:)` (perceptual). Used for the derived theme colours
 * (copper, soft sage) so they match iOS; `ThemeColourTests` pins the iOS-resolved values.
 */
fun Color.mixOklab(other: Color, fraction: Float): Color {
    val a = toOklab(this)
    val b = toOklab(other)
    fun lerp(x: Double, y: Double) = x + (y - x) * fraction
    return fromOklab(
        lerp(a[0], b[0]), lerp(a[1], b[1]), lerp(a[2], b[2]),
        alpha = this.alpha + (other.alpha - this.alpha) * fraction,
    )
}

/**
 * Mixes in plain sRGB component space (SwiftUI `in: .device`), used for the logo's mist
 * water (off-white with 22% mist blue = #CFD4D4).
 */
fun Color.mixDevice(other: Color, fraction: Float): Color = Color(
    red = red + (other.red - red) * fraction,
    green = green + (other.green - green) * fraction,
    blue = blue + (other.blue - blue) * fraction,
    alpha = alpha + (other.alpha - alpha) * fraction,
)

private fun toLinear(c: Float): Double {
    val v = c.toDouble()
    return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

private fun toGamma(v: Double): Float {
    val c = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055
    return c.coerceIn(0.0, 1.0).toFloat()
}

private fun toOklab(color: Color): DoubleArray {
    val r = toLinear(color.red); val g = toLinear(color.green); val b = toLinear(color.blue)
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    return doubleArrayOf(
        0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
        1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
        0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
    )
}

private fun fromOklab(lightness: Double, a: Double, b: Double, alpha: Float): Color {
    val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).pow(3)
    return Color(
        red = toGamma(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
        green = toGamma(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
        blue = toGamma(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s),
        alpha = alpha,
    )
}

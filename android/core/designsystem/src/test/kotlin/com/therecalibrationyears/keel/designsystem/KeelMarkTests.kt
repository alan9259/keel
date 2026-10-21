package com.therecalibrationyears.keel.designsystem

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Port of KeelTests/KeelMarkTests.swift. The mark is drawn in code, so prove it matches the
 * owner's final v2 art (`brand/keel-icon-1024.png`): render the square form at 1024 with
 * real Skia graphics and compare pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34]) // Robolectric needs Java 21 for 35+; the drawing doesn't depend on the API level
class KeelMarkTests {
    private val repo = File(checkNotNull(System.getProperty("keel.repoRoot")))
    private val side = 1024

    private fun renderedMark(): IntArray {
        val bitmap = ImageBitmap(side, side)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(side.toFloat(), side.toFloat())) {
            drawKeelMark(cornerRadiusPx = 0f)
        }
        return IntArray(side * side).also { bitmap.readPixels(it) }
    }

    private fun brandIcon(): IntArray {
        val image = ImageIO.read(File(repo, "brand/keel-icon-1024.png"))
        assertEquals(side, image.width)
        return image.getRGB(0, 0, side, side, null, 0, side)
    }

    private fun IntArray.hex(x: Int, y: Int) = "#%06X".format(this[y * side + x] and 0xFFFFFF)

    /**
     * Every pixel within a small tolerance of the PNG, allowing only anti-aliased edges to
     * differ. Catches a wrong colour, size, position, stroke weight or waterline.
     */
    @Test fun markMatchesTheV2BrandArt() {
        val a = renderedMark()
        val b = brandIcon()
        var differing = 0
        var worst = 0
        for (i in a.indices) {
            val d = listOf(16, 8, 0).maxOf { shift -> abs((a[i] shr shift and 0xFF) - (b[i] shr shift and 0xFF)) }
            worst = maxOf(worst, d)
            if (d > 24) differing++
        }
        val fraction = differing.toDouble() / (side * side)
        assertTrue("$differing pixels differ by >24 (worst $worst)", fraction < 0.005)
    }

    /** The four colours land where the art has them. */
    @Test fun markColoursAtKnownPoints() {
        val a = renderedMark()
        assertEquals("#8C4A45", a.hex(20, 20)) // rosewood tile
        assertEquals("#F7F5F0", a.hex(512, 250)) // solid off-white disc (v1 was translucent)
        assertEquals("#CFD4D4", a.hex(512, 780)) // mist water (v1 was off-white)
        assertEquals("#8C4A45", a.hex(408, 500)) // K spine
    }

    /**
     * The launcher icon is the v2 art too (replaces the iOS LaunchMark asset check): solid
     * off-white disc, mist water, rosewood K and tile.
     */
    @Test fun launcherIconIsV2() {
        val foreground = File(repo, "android/app/src/main/res/drawable/ic_launcher_foreground.xml").readText()
        val fills = Regex("""android:(fillColor|strokeColor)="#([0-9A-Fa-f]{6,8})"""").findAll(foreground)
            .map { it.groupValues[2].takeLast(6).uppercase() }.toList()
        assertEquals(listOf("F7F5F0", "CFD4D4", "8C4A45"), fills)
        val background = File(repo, "android/app/src/main/res/values/colors.xml").readText()
        assertTrue(background.contains("#FF8C4A45"))
    }
}

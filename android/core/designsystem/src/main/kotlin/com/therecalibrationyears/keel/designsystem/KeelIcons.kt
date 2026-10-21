package com.therecalibrationyears.keel.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Keel's system icons: Material Symbols (Apache 2.0) path data standing in for the SF
 * Symbols the iOS app uses. Tinted from the theme at the call site. The SF name each one
 * replaces is noted so screens can be ported one-to-one; add more as screens need them.
 */
object KeelIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .build()

    /** SF "arrow.left". */
    val back = icon("back", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")

    /** SF "house". */
    val home = icon(
        "home",
        "M12,5.69l5,4.5V18h-2v-6H9v6H7v-7.81l5,-4.5M12,3L2,12h3v8h6v-6h2v6h6v-8h3L12,3z",
    )

    /** SF "checkmark". */
    val check = icon("check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")

    /** SF "plus". */
    val plus = icon("plus", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")

    /** SF "leaf.fill". */
    val leaf = icon(
        "leaf",
        "M6.05,8.05c-2.73,2.73 -2.73,7.15 -0.02,9.88c1.47,-3.4 4.09,-6.24 7.36,-7.93" +
            "c-2.77,2.34 -4.71,5.61 -5.39,9.32c2.6,1.23 5.8,0.78 7.95,-1.37C19.43,14.47 20,4 20,4" +
            "S9.53,4.57 6.05,8.05z",
    )

    /** SF "heart.fill". */
    val heart = icon(
        "heart",
        "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09" +
            "C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z",
    )

    /** SF "chevron.right". */
    val chevronRight = icon("chevronRight", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z")
}

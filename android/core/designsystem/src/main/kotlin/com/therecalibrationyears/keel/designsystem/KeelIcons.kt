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

    /** Material Symbols path data: a 960-unit canvas whose y runs from -960 to 0. */
    private fun symbol(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 960f, 960f)
            .addGroup(translationY = 960f)
            .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .clearGroup()
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

    /** SF "chevron.left". */
    val chevronLeft = icon("chevronLeft", "M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z")

    /** SF "calendar" (Material "calendar_today"). */
    val calendar = icon(
        "calendar",
        "M20,3h-1V1h-2v2H7V1H5v2H4c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z" +
            "m0,18H4V8h16v13z",
    )

    /** SF "pills.fill" (Material Symbols "pill", filled). */
    val pill = symbol(
        "pill",
        "m668-349 106-107q32-32 49-73t17-86q0-94-65.5-159.5T615-840q-45 0-86 17t-73 49L349-668l319 319Z " +
            "M345-120q45 0 86-17t73-49l107-106-319-319-106 107q-32 32-49 73t-17 86q0 94 65.5 159.5T345-120Z",
    )

    /** SF "chart.line.uptrend.xyaxis" (Material "show_chart"). */
    val chart = icon("chart", "M3.5,18.49l6,-6.01 4,4L22,6.92l-1.41,-1.41 -7.09,7.97 -4,-4L2,16.99z")

    /** SF "ellipsis" (Material "more_horiz"). */
    val more = icon(
        "more",
        "M6,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM18,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2" +
            " -0.9,-2 -2,-2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
    )

    /** SF "doc.text" (Material "description"). */
    val document = icon(
        "document",
        "M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6z" +
            "m2,16H8v-2h8v2zm0,-4H8v-2h8v2zm-3,-5V3.5L18.5,9H13z",
    )

    /** SF "chevron.right". */
    val chevronRight = icon("chevronRight", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z")
}

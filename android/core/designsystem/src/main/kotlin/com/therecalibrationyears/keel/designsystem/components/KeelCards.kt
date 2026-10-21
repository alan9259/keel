package com.therecalibrationyears.keel.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.Spacing
import com.therecalibrationyears.keel.designsystem.lineSpacing

/** Card surface with a hairline border (port of iOS `StandardCard`). */
@Composable
fun StandardCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    cornerRadius: Dp = Radius.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.card)
            .border(1.dp, theme.border, shape)
            .padding(padding),
        content = content,
    )
}

/** Sage-tinted callout with a left accent bar and a leading icon (port of iOS `CalloutCard`). */
@Composable
fun CalloutCard(
    modifier: Modifier = Modifier,
    icon: ImageVector = KeelIcons.leaf,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.md)
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(theme.sageTint),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(theme.sage))
        Row(
            Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(icon, contentDescription = null, tint = theme.sage, modifier = Modifier.size(20.dp))
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** Warm gradient hero card, accent to sage at 10% (port of iOS `HeroCard`). */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(theme.accent.copy(alpha = 0.10f), theme.sage.copy(alpha = 0.10f)),
                    start = Offset.Zero, end = Offset.Infinite,
                ),
            )
            .border(1.dp, theme.accentBorder, shape)
            .padding(22.dp),
        content = content,
    )
}

/** Small sage-tinted note with a bold lead-in (port of iOS `InfoNoteCard`). */
@Composable
fun InfoNoteCard(lead: String, message: String, modifier: Modifier = Modifier) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.md)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(lead) }
            append(" $message")
        },
        style = KeelFont.caption.lineSpacing(2f),
        color = theme.text.copy(alpha = 0.7f),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.sageTint)
            .border(1.dp, theme.sageBorder, shape)
            .padding(Spacing.md),
    )
}

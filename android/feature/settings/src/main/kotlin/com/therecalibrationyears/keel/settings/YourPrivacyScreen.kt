package com.therecalibrationyears.keel.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import com.therecalibrationyears.keel.designsystem.lineSpacing
import com.therecalibrationyears.keel.domain.FeedbackMail
import com.therecalibrationyears.keel.domain.KeelLinks
import com.therecalibrationyears.keel.domain.PrivacySummary

/** The table's column widths, as on iOS: four columns are wider than a phone, so it scrolls sideways. */
private val columnWidths = listOf(170.dp, 150.dp, 190.dp, 200.dp)

/** Port of iOS `YourPrivacyView`, showing [summary] (the owner's wording). */
@Composable
fun YourPrivacyScreen(summary: PrivacySummary, info: AppInfo, onBack: () -> Unit) {
    val theme = LocalKeelTheme.current
    val context = LocalContext.current
    var showNoMailApp by remember { mutableStateOf(false) }
    KeelScreen(title = PrivacySummary.TITLE, subtitle = summary.lastUpdated, onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("The short version")
            summary.shortVersion.forEach {
                Text(it, style = KeelFont.body.lineSpacing(3f), color = theme.text.copy(alpha = 0.85f))
            }
        }
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Your information at a glance")
            GlanceTable(summary)
        }
        val shape = RoundedCornerShape(Radius.card)
        Column(Modifier.fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape)) {
            LinkRow(KeelIcons.document, "Read the full privacy policy", null) { context.openLink(KeelLinks.PRIVACY_POLICY) }
            Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
            LinkRow(KeelIcons.mailOutline, "Support", FeedbackMail.ADDRESS) {
                if (!context.openMailDraft(FeedbackMail.Kind.SUPPORT, info)) showNoMailApp = true
            }
        }
    }
    if (showNoMailApp) NoMailAppDialog { showNoMailApp = false }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, Modifier.semantics { heading() }, style = KeelFont.serif(20.sp), color = LocalKeelTheme.current.heading)

/**
 * The policy draft's table: a header row, then one row per kind of information. TalkBack reads
 * each row as one sentence with its headings, and skips the header row.
 */
@Composable
private fun GlanceTable(summary: PrivacySummary) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
        Column(Modifier.width(IntrinsicSize.Max).clip(shape).background(theme.card).border(1.dp, theme.border, shape)) {
            TableRow(Modifier.background(theme.accentTint).clearAndSetSemantics {}) {
                summary.columns.forEachIndexed { i, heading -> Cell(heading, i, header = true) }
            }
            summary.atAGlance.forEach { row ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
                TableRow(Modifier.clearAndSetSemantics { contentDescription = summary.spoken(row) }) {
                    Cell(row.what, 0, emphasised = true)
                    Cell(row.kept, 1)
                    Cell(row.access, 2)
                    Cell(row.retention, 3)
                }
            }
        }
    }
}

@Composable
private fun TableRow(modifier: Modifier, cells: @Composable () -> Unit) {
    Row(modifier.height(IntrinsicSize.Min)) { cells() }
}

@Composable
private fun Cell(text: String, column: Int, header: Boolean = false, emphasised: Boolean = false) {
    val theme = LocalKeelTheme.current
    val style = when {
        header -> KeelFont.sans(13.sp, FontWeight.SemiBold)
        emphasised -> KeelFont.sans(14.sp, FontWeight.Medium)
        else -> KeelFont.sans(14.sp)
    }
    val colour = when {
        header -> theme.heading
        emphasised -> theme.text
        else -> theme.text.copy(alpha = 0.85f)
    }
    Row(Modifier.width(columnWidths[column]).fillMaxHeight()) {
        Text(text, Modifier.weight(1f).padding(12.dp), style = style, color = colour)
        if (column < columnWidths.lastIndex) Box(Modifier.width(1.dp).fillMaxHeight().background(theme.border))
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, detail: String?, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = theme.accent, modifier = Modifier.size(20.dp).width(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = KeelFont.body, color = theme.text)
            if (detail != null) Text(detail, style = KeelFont.caption, color = theme.muted)
        }
        Icon(KeelIcons.arrowUpRight, contentDescription = null, tint = theme.muted, modifier = Modifier.size(18.dp))
    }
}

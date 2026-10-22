package com.therecalibrationyears.keel.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import com.therecalibrationyears.keel.designsystem.lineSpacing
import com.therecalibrationyears.keel.domain.AboutCopy
import com.therecalibrationyears.keel.domain.CrisisResources
import com.therecalibrationyears.keel.domain.FeedbackMail
import com.therecalibrationyears.keel.domain.KeelLinks
import com.therecalibrationyears.keel.domain.SupportRegion
import java.time.ZoneId
import java.util.Locale

/**
 * Port of iOS `SupportView`: always-available crisis and support lines for her region (AU, NZ,
 * or both when she is elsewhere). Each line opens the phone's dialler with the number filled in.
 */
@Composable
fun SupportScreen(
    onBack: () -> Unit,
    localeCountry: String? = Locale.getDefault().country.ifEmpty { null },
    zoneId: String = ZoneId.systemDefault().id,
) {
    val theme = LocalKeelTheme.current
    val regions = CrisisResources.matching(localeCountry, zoneId)
    KeelScreen(
        title = "Get support",
        subtitle = "You don't have to handle this alone. Free, confidential support is available.",
        onBack = onBack,
    ) {
        regions.forEach { region ->
            // A country label only when more than one region shows, so a single region reads cleanly.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (regions.size > 1) SectionEyebrow(region.name, Modifier.padding(top = 4.dp), tracking = 1.5.sp)
                RegionCard(region)
            }
        }
        Text(
            CrisisResources.footer(regions),
            Modifier.fillMaxWidth().padding(top = 4.dp),
            style = KeelFont.caption.copy(textAlign = TextAlign.Center),
            color = theme.muted,
        )
    }
}

@Composable
private fun RegionCard(region: SupportRegion) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Column(Modifier.fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape)) {
        CallRow("Emergency", region.emergency, "If you or someone else is in immediate danger", theme.accent, emphasised = true)
        region.contacts.forEach { contact ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
            CallRow(contact.name, contact.contact, contact.note, theme.sage, emphasised = false)
        }
    }
}

@Composable
private fun CallRow(name: String, contact: String, note: String?, tint: Color, emphasised: Boolean) {
    val theme = LocalKeelTheme.current
    val context = LocalContext.current
    val haptic = KeelHaptics.rememberPerformer()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { if (context.dial(contact)) haptic(KeelHapticKind.LIGHT) }
            .clearAndSetSemantics {
                contentDescription = "Call $name, $contact"
                role = Role.Button
                onClick { context.dial(contact) }
            }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(if (emphasised) KeelIcons.phone else KeelIcons.phoneOutline, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = KeelFont.bodyLarge, color = theme.text)
            if (note != null) Text(note, style = KeelFont.caption, color = theme.muted)
        }
        Text(contact, style = KeelFont.sans(15.sp, FontWeight.SemiBold), color = tint)
    }
}

/** The dialler with the number filled in (she presses call herself; no permission needed). */
private fun Context.dial(contact: String): Boolean {
    val digits = CrisisResources.dialDigits(contact)
    if (digits.isEmpty()) return false
    return try {
        startActivity(Intent(Intent.ACTION_DIAL, "tel:$digits".toUri())) // not-copy: tel URI
        true
    } catch (_: ActivityNotFoundException) {
        false // a phone with no dialler (some tablets): the number is on screen to use elsewhere
    }
}

/** Port of iOS `AboutView`: the owner's words, then the version, the emoji credit and two links. */
@Composable
fun AboutScreen(info: AppInfo, onOpenPrivacy: () -> Unit, onBack: () -> Unit) {
    val theme = LocalKeelTheme.current
    val context = LocalContext.current
    var showNoMailApp by remember { mutableStateOf(false) }
    val body = KeelFont.bodyLarge.lineSpacing(4f)
    KeelScreen(title = "About Keel", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AboutCopy.opening.forEachIndexed { index, paragraph ->
                Text(paragraph, style = body, color = theme.text.copy(alpha = if (index == 0) 0.95f else 0.75f))
            }
        }
        AboutCopy.sections.forEach { (heading, paragraphs) ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(heading, Modifier.semantics { heading() }, style = KeelFont.serif(20.sp), color = theme.heading)
                paragraphs.forEach { Text(it, style = body, color = theme.text.copy(alpha = 0.75f)) }
            }
        }
        Text(
            AboutCopy.CLOSING, Modifier.padding(top = 4.dp),
            style = KeelFont.serif(17.sp, FontWeight.Normal).lineSpacing(4f), color = theme.text.copy(alpha = 0.9f),
        )
        Column(Modifier.padding(top = 12.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(AboutCopy.versionLine(info.shortVersion), style = KeelFont.caption, color = theme.muted)
                Text(AboutCopy.EMOJI_CREDIT, style = KeelFont.caption, color = theme.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FooterLink("Your privacy", onOpenPrivacy)
                    Text("·", style = KeelFont.caption, color = theme.muted)
                    FooterLink("Support") { if (!context.openMailDraft(FeedbackMail.Kind.SUPPORT, info)) showNoMailApp = true }
                }
            }
        }
    }
    if (showNoMailApp) NoMailAppDialog { showNoMailApp = false }
}

@Composable
private fun FooterLink(text: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.CenterStart) {
        Text(text, style = KeelFont.caption, color = LocalKeelTheme.current.accent)
    }
}

/** Port of iOS `ConnectView`: Keel's social accounts and a direct email. */
@Composable
fun ConnectScreen(info: AppInfo, onBack: () -> Unit) {
    val theme = LocalKeelTheme.current
    val context = LocalContext.current
    var showNoMailApp by remember { mutableStateOf(false) }
    KeelScreen(title = "Connect with Us", subtitle = "We'd love to hear from you", onBack = onBack) {
        Text(
            "Keel is built by a very small team who care deeply about this community. Follow along for updates, or just say hello.",
            style = KeelFont.bodyLarge.lineSpacing(4f), color = theme.text.copy(alpha = 0.7f),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SocialCard(KeelIcons.instagram, "Instagram", "@keelperiapp", "Gentle reminders, tips and a glimpse behind the product.", KeelLinks.INSTAGRAM)
            SocialCard(KeelIcons.facebook, "Facebook", "@KeelPeriApp", "Updates, stories and a look at what we're building.", KeelLinks.FACEBOOK)
        }
        EmailCard { if (!context.openMailDraft(FeedbackMail.Kind.SUPPORT, info)) showNoMailApp = true }
    }
    if (showNoMailApp) NoMailAppDialog { showNoMailApp = false }
}

/** One social account; the whole card opens it (its app if installed, else the browser). */
@Composable
private fun SocialCard(logo: ImageVector, name: String, handle: String, blurb: String, url: String) {
    val theme = LocalKeelTheme.current
    val context = LocalContext.current
    val haptic = KeelHaptics.rememberPerformer()
    val shape = RoundedCornerShape(Radius.card)
    val open = { haptic(KeelHapticKind.LIGHT); context.openLink(url) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.card)
            .border(1.dp, theme.border, shape)
            .clickable(role = Role.Button) { open() }
            .clearAndSetSemantics {
                contentDescription = "$name, $handle"
                role = Role.Button
                onClick { open(); true }
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The brand's own single-colour glyph, black or white, never recoloured to the theme.
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(theme.background), contentAlignment = Alignment.Center) {
            Icon(logo, contentDescription = null, tint = KeelIcons.socialGlyphTint(darkPage = theme.background.luminance() < 0.5f), modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = KeelFont.bodyLarge, color = theme.text)
            Text(handle, style = KeelFont.body, color = theme.accent)
            Text(blurb, style = KeelFont.caption, color = theme.muted)
        }
        Icon(KeelIcons.arrowUpRight, contentDescription = null, tint = theme.muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun EmailCard(onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.card)
            .border(1.dp, theme.border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "Email us at ${FeedbackMail.ADDRESS}"
                role = Role.Button
                onClick { onClick(); true }
            }
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Get in touch directly", style = KeelFont.bodyLarge, color = theme.text)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = theme.muted)) { append("For support, feedback or anything else, reach us at ") }
                    withStyle(SpanStyle(color = theme.accent)) { append(FeedbackMail.ADDRESS) }
                },
                style = KeelFont.body.lineSpacing(3f),
            )
            Text("We read every message and reply as soon as we can.", style = KeelFont.caption, color = theme.muted)
        }
        Icon(KeelIcons.mailOutline, contentDescription = null, tint = theme.accent, modifier = Modifier.size(20.dp))
    }
}

/** A web link, in its app if one claims it, else the browser. */
internal fun Context.openLink(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No browser at all: nothing can open it.
    }
}

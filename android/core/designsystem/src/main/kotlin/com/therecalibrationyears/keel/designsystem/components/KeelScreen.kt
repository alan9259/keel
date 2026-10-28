package com.therecalibrationyears.keel.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalGoHome
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Spacing

/**
 * A pushed feature screen (port of iOS `ScreenHeader` + `keelFeatureScreen()`): the page
 * background, a scrolling column that starts with the back / title / Home header, and the
 * floating header (back, centred title, Home) once the header has scrolled out of view.
 * Home appears only where the navigation host provides [LocalGoHome], unless [onHome] is
 * given (for example to confirm before discarding a draft).
 */
@Composable
fun KeelScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleSize: TextUnit = 28.sp,
    showsHome: Boolean = true,
    onHome: (() -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalKeelTheme.current
    val goHome = LocalGoHome.current
    val homeAction = if (showsHome) onHome ?: goHome else null

    // Where the header sits inside the scrolling content, to know when it has scrolled away
    // (the iOS app uses a 5% visibility threshold).
    var headerTop by remember { mutableIntStateOf(0) }
    var headerHeight by remember { mutableFloatStateOf(0f) }
    val scrolledAway by remember {
        derivedStateOf {
            headerHeight > 0f && scrollState.value > headerTop + headerHeight * 0.95f
        }
    }

    Box(modifier.fillMaxSize().background(theme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Spacing.screenH, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ScreenHeader(
                title = title, subtitle = subtitle, titleSize = titleSize,
                onBack = onBack, onHome = homeAction,
                modifier = Modifier.onGloballyPositioned {
                    headerTop = it.positionInParent().y.toInt()
                    headerHeight = it.size.height.toFloat()
                },
            )
            content()
        }
        AnimatedVisibility(
            visible = scrolledAway,
            enter = slideInVertically(tween(200)) { -it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            FloatingHeaderBar(title = title, onBack = onBack, onHome = homeAction)
        }
    }
}

/** Back arrow, title (+ optional subtitle) and Home, used at the top of a feature screen. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleSize: TextUnit = 28.sp,
    onHome: (() -> Unit)? = null,
) {
    val theme = LocalKeelTheme.current
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = if (subtitle == null) Alignment.CenterVertically else Alignment.Top,
    ) {
        HeaderIconButton(KeelIcons.back, "Back", onBack)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = KeelFont.serif(titleSize), color = theme.heading, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = KeelFont.caption, color = theme.muted)
        }
        if (onHome != null) HeaderIconButton(KeelIcons.home, "Home", onHome)
    }
}

@Composable
private fun FloatingHeaderBar(title: String, onBack: () -> Unit, onHome: (() -> Unit)?) {
    val theme = LocalKeelTheme.current
    Column(
        Modifier
            .fillMaxWidth()
            .blocksTouchesBelow() // a tap on the title mustn't reach the row scrolled under it
            .shadow(6.dp, ambientColor = theme.scrim, spotColor = theme.scrim)
            .background(theme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIconButton(KeelIcons.back, "Back", onBack)
            Text(
                title,
                style = KeelFont.serif(18.sp),
                color = theme.heading,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (onHome != null) HeaderIconButton(KeelIcons.home, "Home", onHome) else Spacer(Modifier.size(HeaderButtonSlot))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
    }
}

/** The room a header button takes in its row (iOS lays them out at 32pt). */
private val HeaderButtonSlot = 32.dp

/** The back / Home icon button: muted glyph, 48dp touch target. */
@Composable
private fun HeaderIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    IconButton(onClick = onClick, modifier = Modifier.headerHitTarget().semantics { contentDescription = label }) {
        Icon(icon, contentDescription = null, tint = theme.muted, modifier = Modifier.size(24.dp))
    }
}

/**
 * As iOS `headerHitTarget()`: the button keeps its full touch target but takes only
 * [HeaderButtonSlot] of the row, spilling evenly past it. Laid out at 48dp, the two buttons
 * left the title 32dp less room than on iPhone, so "Medications and supplements" took three
 * lines on a 393dp phone where the iPhone shows two.
 */
private fun Modifier.headerHitTarget() = layout { measurable, constraints ->
    val button = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val slot = HeaderButtonSlot.roundToPx()
    layout(slot, slot) { button.place((slot - button.width) / 2, (slot - button.height) / 2) }
}

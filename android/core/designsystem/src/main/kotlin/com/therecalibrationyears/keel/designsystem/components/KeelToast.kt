package com.therecalibrationyears.keel.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme

@Immutable
data class ToastData(val title: String, val subtitle: String? = null)

/** Rounded toast shown above the navigation bar for 2.4 s (port of iOS `ToastView`). */
@Composable
fun KeelToast(data: ToastData, modifier: Modifier = Modifier) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .shadow(15.dp, shape, ambientColor = theme.scrim, spotColor = theme.scrim)
            .clip(shape)
            .background(theme.toastBackground)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(data.title, style = KeelFont.sans(14.sp, FontWeight.SemiBold), color = theme.toastText)
        data.subtitle?.let {
            Text(it, style = KeelFont.sans(12.sp), color = theme.toastText, modifier = Modifier.alpha(0.75f))
        }
    }
}

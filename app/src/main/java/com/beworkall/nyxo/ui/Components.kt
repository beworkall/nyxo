@file:OptIn(ExperimentalFoundationApi::class)

package com.beworkall.nyxo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beworkall.nyxo.LauncherViewModel
import com.beworkall.nyxo.data.AppEntry
import com.beworkall.nyxo.data.IconCache
import com.beworkall.nyxo.radar.Risk

// ---------------------------------------------------------------- palette

val ACCENTS = listOf(
    Color(0xFF9D8CFF), // moonlight violet
    Color(0xFF5B8CFF), // blue
    Color(0xFF3DDC97), // green
    Color(0xFFFF3B5C), // red
    Color(0xFFFFB020), // amber
    Color(0xFFE9ECF2), // white
)
val Ink = Color(0xFFE9ECF2)
val Muted = Color(0xFF8A93A6)
val Faint = Color(0xFF5D6576)
val Panel = Color(0xFF10141C)
val PanelHi = Color(0xFF161B25)
val Line = Color(0xFF232A38)
val RiskHigh = Color(0xFFFF4D6D)
val RiskMid = Color(0xFFFFB020)
val RiskOk = Color(0xFF2ECC71)

private val LabelShadow = Shadow(color = Color.Black, offset = Offset(0f, 1f), blurRadius = 6f)

@Composable
fun NyxoTheme(accent: Color, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF07090D),
            secondary = accent,
            background = Color.Black,
            onBackground = Ink,
            surface = Panel,
            onSurface = Ink,
            surfaceVariant = PanelHi,
            onSurfaceVariant = Muted,
            outline = Line,
        ),
        content = content,
    )
}

@Composable
fun accent(): Color = MaterialTheme.colorScheme.primary

fun riskColor(risk: Risk?): Color? = when (risk) {
    Risk.HIGH -> RiskHigh
    Risk.MEDIUM -> RiskMid
    else -> null
}

// ---------------------------------------------------------------- icons

@Composable
fun AppIconImage(app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    // Keyed on the app: when a row is reused for a different app (thumb strip, search), the old icon is dropped.
    val icon by produceState(IconCache.peek(app, px), app.key, px) {
        value = IconCache.peek(app, px)
        if (value == null) value = IconCache.load(context, app, px)
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(modifier.size(size).clip(CircleShape).background(PanelHi))
    }
}

@Composable
fun LetterIcon(label: String, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(PanelHi),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.take(1).uppercase(), color = Muted, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun RadarDot(risk: Risk?, modifier: Modifier = Modifier, size: Dp = 11.dp, isNew: Boolean = false) {
    val color = riskColor(risk) ?: return
    Box(
        modifier
            .size(if (isNew) size + 4.dp else size)
            .clip(CircleShape)
            .background(if (isNew) Ink else Color.Black)
            .padding(2.dp)
            .clip(CircleShape)
            .background(Color.Black)
            .padding(if (isNew) 1.dp else 0.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/** Small "NEW" tag for apps that gained a permission since you last looked. */
@Composable
fun NewTag() {
    Text(
        "NEW",
        color = Color(0xFF07090D),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(RiskMid)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** Small radar mark used as the Privacy Radar button. */
@Composable
fun RadarGlyph(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val stroke = 1.6.dp.toPx()
        val r = this.size.minDimension / 2f
        drawCircle(color, radius = r - stroke, style = Stroke(stroke))
        drawCircle(color.copy(alpha = 0.6f), radius = r * 0.5f, style = Stroke(stroke))
        drawLine(color, center, Offset(center.x + r * 0.62f, center.y - r * 0.62f), strokeWidth = stroke)
        drawCircle(color, radius = 2.2.dp.toPx(), center = Offset(center.x + r * 0.32f, center.y + r * 0.38f))
    }
}

// ---------------------------------------------------------------- app cells

@Composable
fun AppCell(
    vm: LauncherViewModel,
    app: AppEntry,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    iconSize: Dp = 54.dp,
) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val squish = rememberSquish(source)
    val target = rememberLaunchTarget()
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = { vm.launchFrom(app, target) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.menuApp = app
                },
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.graphicsLayer {
                scaleX = squish
                scaleY = squish
            }
        ) {
            AppIconImage(app, iconSize, Modifier.trackBounds(target))
            if (vm.prefs.radarDots.value) {
                RadarDot(vm.radar[app.pkg]?.risk, Modifier.align(Alignment.BottomEnd), isNew = app.pkg in vm.changes)
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                app.label,
                style = TextStyle(fontSize = 12.sp, color = Ink, shadow = LabelShadow),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

/** Row with icon and label, used in search results and lists. */
@Composable
fun AppRow(vm: LauncherViewModel, app: AppEntry, showIcon: Boolean = true) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val squish = rememberSquish(source)
    val target = rememberLaunchTarget()
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = squish
                scaleY = squish
            }
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = { vm.launchFrom(app, target) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.menuApp = app
                },
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon) {
            AppIconImage(app, 40.dp, Modifier.trackBounds(target))
            Spacer(Modifier.width(14.dp))
        }
        Text(
            app.label,
            fontSize = if (showIcon) 17.sp else 22.sp,
            fontWeight = if (showIcon) FontWeight.Medium else FontWeight.Light,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (vm.prefs.radarDots.value) RadarDot(vm.radar[app.pkg]?.risk, isNew = app.pkg in vm.changes)
    }
}

/** Big minimal text entry for the text-list home style. */
@Composable
fun TextAppRow(vm: LauncherViewModel, app: AppEntry) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { vm.launch(app) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.menuApp = app
                },
            )
            .padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            app.label,
            style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Light, color = Ink, shadow = LabelShadow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (vm.prefs.radarDots.value) {
            Spacer(Modifier.width(10.dp))
            RadarDot(vm.radar[app.pkg]?.risk)
        }
    }
}

// ---------------------------------------------------------------- inputs and chrome

@Composable
fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
    placeholder: String = "Search apps, contacts, settings…",
    onGo: () -> Unit = {},
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Ink, fontSize = 17.sp),
        cursorBrush = SolidColor(accent()),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = modifier.focusRequester(focus),
        decorationBox = { inner ->
            Row(
                Modifier
                    .nyxoSurface(RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = Faint, fontSize = 16.sp, maxLines = 1)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear",
                        tint = Muted,
                        modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onChange("") },
                    )
                }
            }
        },
    )
}

@Composable
fun IconBtn(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun Header(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
        Spacer(Modifier.width(6.dp))
        Column {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = Muted)
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = accent(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Faint)
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF07090D),
                checkedTrackColor = accent(),
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = PanelHi,
                uncheckedBorderColor = Line,
            ),
        )
    }
}

@Composable
fun ChoiceRow(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
        Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, label) in options) {
                val on = value == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (on) accent().copy(alpha = 0.16f) else Panel)
                        .border(1.dp, if (on) accent() else Line, RoundedCornerShape(999.dp))
                        .clickable { onSelect(value) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text(label, color = if (on) accent() else Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun InfoCard(title: String, body: String, color: Color = RiskOk) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .nyxoSurface(RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Box(Modifier.padding(top = 6.dp).size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
fun ActionButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) accent() else PanelHi)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (primary) Color(0xFF07090D) else Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun RowScope.StatTile(label: String, count: Int, color: Color) {
    Column(
        Modifier
            .weight(1f)
            .nyxoSurface(RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Text(count.toString(), color = color, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun DefaultBanner(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accent().copy(alpha = 0.12f))
            .border(1.dp, accent().copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Make Nyxo your home screen", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Tap to set it as the default launcher", color = Muted, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = accent())
    }
}

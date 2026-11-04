package com.beworkall.nyxo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.beworkall.nyxo.LauncherViewModel
import com.beworkall.nyxo.data.CalendarLine
import com.beworkall.nyxo.system.Actions
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val ClockShadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 2f), blurRadius = 12f)

val CLOCK_STYLES = listOf(
    "thin" to "Thin",
    "bold" to "Bold",
    "stacked" to "Stacked",
    "analog" to "Analog",
    "words" to "Words",
)

/** The home screen clock. Wakes once a minute and only while the home screen is visible. */
@Composable
fun NyxoClock(vm: LauncherViewModel) {
    val context = LocalContext.current
    val p = vm.prefs
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var event by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val calendarOn = p.calendarLine.value
    LaunchedEffect(lifecycle, calendarOn) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var minutes = 0
            while (true) {
                now = System.currentTimeMillis()
                if (calendarOn && minutes % 5 == 0) event = CalendarLine.next(context)
                if (!calendarOn) event = null
                minutes++
                delay(60_000L - now % 60_000L + 50L)
            }
        }
    }
    val font = LocalNyxoFont.current
    val h24 = p.clock24.value
    val minute = now / 60_000L
    val date = remember(minute) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)) }
    val quiet = remember { MutableInteractionSource() }
    val openAlarms: () -> Unit = { Actions.alarms(context) }
    val accent = accent()

    Column {
        when (p.clockStyle.value) {
            "analog" -> AnalogClock(now, accent, Modifier.clickable(quiet, null, onClick = openAlarms))
            "words" -> Text(
                remember(minute) { timeInWords(now) },
                style = TextStyle(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Light, color = Ink, fontFamily = font, shadow = ClockShadow),
                modifier = Modifier.clickable(quiet, null, onClick = openAlarms),
            )
            "stacked" -> {
                val hh = remember(minute, h24) { SimpleDateFormat(if (h24) "HH" else "hh", Locale.getDefault()).format(Date(now)) }
                val mm = remember(minute) { SimpleDateFormat("mm", Locale.getDefault()).format(Date(now)) }
                Column(Modifier.clickable(quiet, null, onClick = openAlarms)) {
                    Text(hh, style = TextStyle(fontSize = 92.sp, lineHeight = 84.sp, fontWeight = FontWeight.Bold, color = Ink, fontFamily = font, shadow = ClockShadow))
                    Text(mm, style = TextStyle(fontSize = 92.sp, lineHeight = 84.sp, fontWeight = FontWeight.Bold, color = accent, fontFamily = font, shadow = ClockShadow))
                }
            }
            else -> {
                val bold = p.clockStyle.value == "bold"
                val time = remember(minute, h24) { SimpleDateFormat(if (h24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now)) }
                Text(
                    time,
                    style = TextStyle(
                        fontSize = if (bold) 78.sp else 86.sp,
                        fontWeight = if (bold) FontWeight.Black else FontWeight.Thin,
                        color = Ink,
                        fontFamily = font,
                        letterSpacing = if (bold) (-2).sp else (-3).sp,
                        shadow = ClockShadow,
                    ),
                    modifier = Modifier.clickable(quiet, null, onClick = openAlarms),
                )
            }
        }
        Text(
            date.uppercase(),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent, letterSpacing = 3.sp, shadow = ClockShadow),
            modifier = Modifier
                .padding(start = 4.dp, top = 2.dp)
                .clickable(quiet, null) { Actions.calendar(context) },
        )
        val e = event
        if (e != null) {
            Text(
                e,
                style = TextStyle(fontSize = 15.sp, color = Ink, fontFamily = font, shadow = ClockShadow),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 4.dp, top = 8.dp)
                    .clickable(quiet, null) { Actions.calendar(context) },
            )
        }
        if (p.focusOn.value) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .padding(start = 2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent.copy(alpha = 0.16f))
                    .clickable { vm.toggleFocus() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.layout.Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Text("Focus on · tap to end", color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AnalogClock(now: Long, accent: Color, modifier: Modifier) {
    val cal = remember(now / 60_000L) { Calendar.getInstance().apply { timeInMillis = now } }
    val h = cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60f
    val m = cal.get(Calendar.MINUTE).toFloat()
    Canvas(modifier.padding(vertical = 6.dp).size(150.dp)) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(Color.Black.copy(alpha = 0.35f), r)
        drawCircle(Color.White.copy(alpha = 0.16f), r, style = Stroke(width = 2.dp.toPx()))
        for (i in 0 until 12) {
            val a = i / 12f * 2f * PI.toFloat() - PI.toFloat() / 2f
            val inner = if (i % 3 == 0) r * 0.80f else r * 0.86f
            drawLine(
                Color.White.copy(alpha = if (i % 3 == 0) 0.8f else 0.35f),
                Offset(c.x + cos(a) * inner, c.y + sin(a) * inner),
                Offset(c.x + cos(a) * r * 0.92f, c.y + sin(a) * r * 0.92f),
                strokeWidth = (if (i % 3 == 0) 3.dp else 1.5.dp).toPx(),
                cap = StrokeCap.Round,
            )
        }
        fun hand(turns: Float, len: Float, width: Float, color: Color) {
            val a = turns * 2f * PI.toFloat() - PI.toFloat() / 2f
            drawLine(color, c, Offset(c.x + cos(a) * r * len, c.y + sin(a) * r * len), strokeWidth = width, cap = StrokeCap.Round)
        }
        hand(h / 12f, 0.5f, 6.dp.toPx(), Color.White)
        hand(m / 60f, 0.74f, 3.5.dp.toPx(), accent)
        drawCircle(accent, 5.dp.toPx(), c)
    }
}

private val NUMBERS = listOf(
    "twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven",
)
private val MINUTES = mapOf(
    5 to "five", 10 to "ten", 15 to "quarter", 20 to "twenty", 25 to "twenty-five", 30 to "half",
)

/** "quarter past ten", rounded to the nearest five minutes. */
fun timeInWords(now: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    var h = cal.get(Calendar.HOUR)
    var m = ((cal.get(Calendar.MINUTE) + 2) / 5) * 5
    if (m == 60) { m = 0; h = (h + 1) % 12 }
    return when {
        m == 0 -> "${NUMBERS[h]}\no'clock"
        m <= 30 -> "${MINUTES[m]} past\n${NUMBERS[h]}"
        else -> "${MINUTES[60 - m]} to\n${NUMBERS[(h + 1) % 12]}"
    }
}

/** Fonts that ship on every Android phone, so nothing is downloaded. */
val FONTS = listOf(
    "default" to "Default",
    "light" to "Light",
    "condensed" to "Condensed",
    "serif" to "Serif",
    "mono" to "Mono",
)

fun fontFamilyOf(id: String): FontFamily = when (id) {
    "light" -> FontFamily(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL))
    "condensed" -> FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    else -> FontFamily.Default
}

val LocalNyxoFont = androidx.compose.runtime.staticCompositionLocalOf<FontFamily> { FontFamily.Default }

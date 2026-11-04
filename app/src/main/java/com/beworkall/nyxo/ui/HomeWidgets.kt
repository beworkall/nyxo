package com.beworkall.nyxo.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.beworkall.nyxo.LauncherViewModel
import com.beworkall.nyxo.Screen
import com.beworkall.nyxo.data.WidgetHosting
import com.beworkall.nyxo.data.WidgetSpec
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Widgets the user added in Nyxo Settings, shown above the app list. */
@Composable
fun WidgetArea(vm: LauncherViewModel) {
    val specs = vm.prefs.widgets.value.mapNotNull { WidgetSpec.decode(it) }
    if (specs.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (spec in specs) {
            key(spec.key) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(spec.heightDp.dp)
                        .nyxoSurface(RoundedCornerShape(22.dp)),
                ) {
                    when (spec.kind) {
                        "app" -> AppWidget(spec)
                        "battery" -> BatteryWidget()
                        "volume" -> VolumeWidget()
                        "privacy" -> PrivacyWidget(vm)
                        "notes" -> NotesWidget(vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppWidget(spec: WidgetSpec) {
    val context = LocalContext.current
    val info = remember(spec.id) {
        runCatching { WidgetHosting.manager(context).getAppWidgetInfo(spec.id) }.getOrNull()
    }
    if (info == null) {
        Text(
            "This widget isn't available any more. Remove it in Nyxo Settings → Widgets.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wDp = maxWidth.value.roundToInt()
        val hDp = maxHeight.value.roundToInt()
        AndroidView(
            factory = { ctx -> WidgetHosting.host(ctx).createView(ctx, spec.id, info) },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                val opts = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, hDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, hDp)
                }
                runCatching { view.updateAppWidgetOptions(opts) }
            },
        )
    }
}

/** Runs [block] now and every [everyMs] while the screen is visible, and never in the background. */
@Composable
private fun WhileVisible(everyMs: Long, block: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                block()
                delay(everyMs)
            }
        }
    }
}

@Composable
private fun BatteryWidget() {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    WhileVisible(60_000L) {
        val i: Intent? = ContextCompat.registerReceiver(
            context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (i != null) {
            val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            level = if (l >= 0 && s > 0) l * 100 / s else -1
            val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        }
    }
    val color = when {
        charging -> RiskOk
        level in 0..15 -> RiskHigh
        else -> accent()
    }
    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(Line, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                val sweep = if (level < 0) 0f else 360f * level / 100f
                drawArc(color, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text(if (level < 0) "–" else "$level", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text("Battery", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    level < 0 -> "Reading…"
                    charging -> "Charging · $level%"
                    else -> "$level% left"
                },
                color = Muted,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun VolumeWidget() {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    if (audio == null) return
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    WhileVisible(5_000L) { vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Media volume", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${(vol / max * 100).roundToInt()}%", color = Muted, fontSize = 13.sp)
        }
        Slider(
            value = vol,
            onValueChange = { v ->
                vol = v
                runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, v.roundToInt(), 0) }
            },
            valueRange = 0f..max.toFloat(),
            colors = SliderDefaults.colors(thumbColor = accent(), activeTrackColor = accent()),
        )
    }
}

@Composable
private fun PrivacyWidget(vm: LauncherViewModel) {
    val score = privacyScore(vm.radar.values)
    Row(
        Modifier
            .fillMaxSize()
            .clickable { vm.screen = Screen.Radar }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(score.score.toString(), color = score.color, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Privacy · ${score.grade}", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(score.summary, color = Muted, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2)
        }
        RadarGlyph(accent())
    }
}

@Composable
private fun NotesWidget(vm: LauncherViewModel) {
    var text by remember { mutableStateOf(vm.prefs.notes.value) }
    LaunchedEffect(text) {
        delay(600)
        vm.prefs.notes.value = text
    }
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        if (text.isEmpty()) Text("Quick note…", color = Faint, fontSize = 15.sp)
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(color = Ink, fontSize = 15.sp, lineHeight = 21.sp),
            cursorBrush = SolidColor(accent()),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

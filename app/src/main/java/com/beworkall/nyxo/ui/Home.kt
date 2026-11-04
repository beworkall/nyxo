package com.beworkall.nyxo.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beworkall.nyxo.LauncherViewModel
import com.beworkall.nyxo.Screen
import com.beworkall.nyxo.data.AppEntry
import com.beworkall.nyxo.system.Actions
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val ClockShadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 2f), blurRadius = 12f)

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = vm.prefs
    val byKey = remember(vm.apps) { vm.apps.associateBy { it.key } }
    val home = prefs.home.value.mapNotNull { byKey[it] }
    val dock = prefs.dock.value.mapNotNull { byKey[it] }
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val gestureHandler: (String) -> Unit = { gesture -> handleGesture(vm, context, gesture) }
    val onGesture = rememberUpdatedState(gestureHandler)

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onGesture.value("double") },
                    onLongPress = { onGesture.value("long") },
                )
            }
            .pointerInput(Unit) {
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { total = Offset.Zero },
                    onDragEnd = {
                        val x = total.x
                        val y = total.y
                        if (abs(y) > abs(x)) {
                            if (y < -threshold) onGesture.value("up")
                            else if (y > threshold) onGesture.value("down")
                        } else {
                            if (x < -threshold) onGesture.value("left")
                            else if (x > threshold) onGesture.value("right")
                        }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        total += amount
                    },
                )
            }
            .systemBarsPadding()
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Clock(
            h24 = prefs.clock24.value,
            onTime = { Actions.alarms(context) },
            onDate = { Actions.calendar(context) },
        )
        if (!vm.isDefaultHome && activity != null) {
            Spacer(Modifier.height(20.dp))
            DefaultBanner { Actions.requestDefaultHome(activity) }
        }

        Spacer(Modifier.weight(1f))

        if (prefs.gridLayout.value) {
            if (home.isEmpty()) EmptyHint()
            HomeGrid(vm, home, prefs.columns.value)
            Spacer(Modifier.height(16.dp))
            if (dock.isNotEmpty()) Dock(vm, dock)
        } else {
            if (home.isEmpty()) EmptyHint()
            for (app in home) TextAppRow(vm, app)
            Spacer(Modifier.height(24.dp))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { vm.openDrawer() }
                .padding(top = 6.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "All apps", tint = Muted.copy(alpha = 0.7f))
        }
    }
}

private fun handleGesture(vm: LauncherViewModel, context: Context, gesture: String) {
    val p = vm.prefs
    when (gesture) {
        "up" -> vm.openDrawer()
        "down" -> if (p.swipeDown.value == "search") vm.openDrawer(focusSearch = true)
        "left" -> vm.appByKey(p.swipeLeft.value)?.let { vm.launch(it) }
        "right" -> vm.appByKey(p.swipeRight.value)?.let { vm.launch(it) }
        "double" -> when (p.doubleTap.value) {
            "search" -> vm.openDrawer(focusSearch = true)
            "settings" -> vm.screen = Screen.Settings
            else -> Unit
        }
        "long" -> {
            vm.screen = Screen.Settings
        }
    }
}

@Composable
private fun EmptyHint() {
    Text(
        "Swipe up for all apps.\nLong-press any app to pin it here.",
        color = Muted,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
    )
}

@Composable
private fun HomeGrid(vm: LauncherViewModel, apps: List<AppEntry>, columns: Int) {
    val cols = columns.coerceIn(3, 6)
    Column {
        for (row in apps.chunked(cols)) {
            Row(Modifier.fillMaxWidth()) {
                for (app in row) AppCell(vm, app, Modifier.weight(1f))
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Dock(vm: LauncherViewModel, apps: List<AppEntry>) {
    val shape = RoundedCornerShape(30.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        for (app in apps) AppCell(vm, app, Modifier.weight(1f), showLabel = false, iconSize = 52.dp)
    }
}

@Composable
private fun Clock(h24: Boolean, onTime: () -> Unit, onDate: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L)
        }
    }
    val time = remember(now / 60_000L, h24) {
        SimpleDateFormat(if (h24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    }
    val date = remember(now / 60_000L) {
        SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now))
    }
    val noRipple = remember { MutableInteractionSource() }
    Column {
        Text(
            time,
            style = TextStyle(
                fontSize = 86.sp,
                fontWeight = FontWeight.Thin,
                color = Ink,
                letterSpacing = (-3).sp,
                shadow = ClockShadow,
            ),
            modifier = Modifier.clickable(interactionSource = noRipple, indication = null, onClick = onTime),
        )
        Text(
            date.uppercase(),
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accent(),
                letterSpacing = 3.sp,
                shadow = ClockShadow,
            ),
            modifier = Modifier
                .padding(start = 4.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDate),
        )
    }
}

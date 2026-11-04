@file:OptIn(ExperimentalFoundationApi::class)

package com.beworkall.nyxo.ui

import android.app.Activity
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.beworkall.nyxo.LauncherViewModel
import com.beworkall.nyxo.Screen
import com.beworkall.nyxo.data.AppEntry
import com.beworkall.nyxo.data.AppStack
import com.beworkall.nyxo.data.STACK_PREFIX
import com.beworkall.nyxo.system.Actions
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val ClockShadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 2f), blurRadius = 12f)
private val RowShadow = Shadow(color = Color.Black, offset = Offset(0f, 1f), blurRadius = 6f)

private sealed interface HomeEntry {
    val key: String
}

private data class AppItem(val app: AppEntry) : HomeEntry {
    override val key: String get() = app.key
}

private data class StackItem(val stack: AppStack, val apps: List<AppEntry>) : HomeEntry {
    override val key: String get() = STACK_PREFIX + stack.id
}

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = vm.prefs
    val hidden = prefs.hidden.value
    val byKey = remember(vm.apps) { vm.apps.associateBy { it.key } }
    val visibleApps = remember(vm.apps, hidden) { vm.apps.filter { it.key !in hidden } }
    val stacks = vm.stacks
    val entries: List<HomeEntry> = prefs.home.value.mapNotNull { k ->
        if (k.startsWith(STACK_PREFIX)) {
            val s = stacks.firstOrNull { it.id == k.removePrefix(STACK_PREFIX) } ?: return@mapNotNull null
            val apps = s.keys.mapNotNull { byKey[it] }.filter { it.key !in hidden }
            if (apps.isEmpty()) null else StackItem(s, apps)
        } else {
            byKey[k]?.takeIf { it.key !in hidden }?.let { AppItem(it) }
        }
    }
    val dock = prefs.dock.value.mapNotNull { byKey[it] }
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val gestureHandler: (String) -> Unit = { gesture -> handleGesture(vm, context, gesture) }
    val onGesture = rememberUpdatedState(gestureHandler)
    val left = prefs.leftHanded.value

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
            .systemBarsPadding(),
    ) {
        Spacer(Modifier.height(40.dp))
        Box(Modifier.padding(horizontal = 22.dp)) {
            Clock(
                h24 = prefs.clock24.value,
                onTime = { Actions.alarms(context) },
                onDate = { Actions.calendar(context) },
            )
        }
        if (!vm.isDefaultHome && activity != null) {
            Box(Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp)) {
                DefaultBanner { Actions.requestDefaultHome(activity) }
            }
        }

        if (prefs.gridLayout.value) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp)) {
                Spacer(Modifier.weight(1f))
                if (entries.isEmpty()) EmptyHint("Swipe up for all apps.\nLong-press any app to pin it here.")
                HomeGrid(vm, entries, prefs.columns.value)
                Spacer(Modifier.height(16.dp))
                if (dock.isNotEmpty()) Dock(vm, dock)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.openDrawer() }
                    .padding(top = 6.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "All apps", tint = Muted.copy(alpha = 0.7f))
            }
        } else {
            Spacer(Modifier.height(12.dp))
            ThumbAlphabet(vm, visibleApps, left, Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .padding(start = if (left) 48.dp else 22.dp, end = if (left) 22.dp else 48.dp, bottom = 12.dp),
                ) {
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .wrapContentHeight(align = Alignment.Bottom, unbounded = true),
                    ) {
                        if (entries.isEmpty()) {
                            EmptyHint("Slide your thumb along the letters on the edge.\nLong-press any app to pin it here.")
                        }
                        for (e in entries) {
                            when (e) {
                                is AppItem -> ListAppRow(vm, e.app)
                                is StackItem -> ListStackRow(vm, e)
                            }
                        }
                    }
                }
            }
        }
    }

    // Grid mode opens stacks in a small pop-up.
    val openId = vm.openStackId
    if (prefs.gridLayout.value && openId != null) {
        val item = entries.firstOrNull { it is StackItem && it.stack.id == openId } as? StackItem
        if (item != null) {
            StackPopup(vm, item)
        } else {
            LaunchedEffect(openId) { vm.openStackId = null }
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
            "settings" -> {
                vm.screen = Screen.Settings
            }
            else -> Unit
        }
        "long" -> {
            vm.screen = Screen.Settings
        }
    }
    if (gesture == "left" || gesture == "right") {
        val key = if (gesture == "left") p.swipeLeft.value else p.swipeRight.value
        if (key.isEmpty()) Actions.toast(context, "Pick an app for this swipe in Settings → Gestures")
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        color = Muted,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
    )
}

// ---------------------------------------------------------------- one-handed list

@Composable
private fun ListAppRow(vm: LauncherViewModel, app: AppEntry, indent: Dp = 0.dp, big: Boolean = true) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .padding(start = indent)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = { vm.launch(app) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.menuApp = app
                },
            )
            .padding(horizontal = 4.dp, vertical = if (big) 9.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconImage(app, if (big) 28.dp else 24.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            app.label,
            style = TextStyle(
                fontSize = if (big) 24.sp else 19.sp,
                fontWeight = FontWeight.Light,
                color = Ink,
                shadow = RowShadow,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (vm.prefs.radarDots.value) {
            Spacer(Modifier.width(10.dp))
            RadarDot(vm.radar[app.pkg]?.risk)
        }
    }
}

@Composable
private fun ListStackRow(vm: LauncherViewModel, item: StackItem) {
    val haptic = LocalHapticFeedback.current
    val open = vm.openStackId == item.stack.id
    Column {
        Row(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClick = { vm.openStackId = if (open) null else item.stack.id },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.editStackId = item.stack.id
                    },
                )
                .padding(horizontal = 4.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StackFan(item.apps, 28.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                item.stack.name,
                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Light, color = Ink, shadow = RowShadow),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            Text(item.apps.size.toString(), color = accent(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                for (app in item.apps) ListAppRow(vm, app, indent = 30.dp, big = false)
            }
        }
    }
}

/** Up to three icons fanned out, used for stacks. */
@Composable
private fun StackFan(apps: List<AppEntry>, size: Dp) {
    val shown = apps.take(3)
    Box(Modifier.width(size + 12.dp).height(size)) {
        shown.forEachIndexed { i, app ->
            AppIconImage(app, size, Modifier.offset(x = (i * 6).dp))
        }
    }
}

// ---------------------------------------------------------------- grid

@Composable
private fun HomeGrid(vm: LauncherViewModel, entries: List<HomeEntry>, columns: Int) {
    val cols = columns.coerceIn(3, 6)
    Column {
        for (row in entries.chunked(cols)) {
            Row(Modifier.fillMaxWidth()) {
                for (e in row) {
                    when (e) {
                        is AppItem -> AppCell(vm, e.app, Modifier.weight(1f))
                        is StackItem -> StackCell(vm, e, Modifier.weight(1f))
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StackCell(vm: LauncherViewModel, item: StackItem, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { vm.openStackId = item.stack.id },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.editStackId = item.stack.id
                },
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                .padding(6.dp),
        ) {
            val shown = item.apps.take(4)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (pair in shown.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (app in pair) AppIconImage(app, 19.dp)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.stack.name,
            style = TextStyle(fontSize = 12.sp, color = Ink, shadow = RowShadow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun StackPopup(vm: LauncherViewModel, item: StackItem) {
    Dialog(onDismissRequest = { vm.openStackId = null }) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Panel)
                .border(1.dp, Line, shape)
                .padding(16.dp),
        ) {
            Text(item.stack.name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
            for (row in item.apps.chunked(4)) {
                Row(Modifier.fillMaxWidth()) {
                    for (app in row) AppCell(vm, app, Modifier.weight(1f))
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
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

// ---------------------------------------------------------------- clock

@Composable
private fun Clock(h24: Boolean, onTime: () -> Unit, onDate: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L)
        }
    }
    val minute = now / 60_000L
    val time = remember(minute, h24) {
        SimpleDateFormat(if (h24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    }
    val date = remember(minute) {
        SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now))
    }
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
            modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTime),
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

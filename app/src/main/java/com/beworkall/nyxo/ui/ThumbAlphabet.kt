package com.beworkall.nyxo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beworkall.nyxo.LauncherViewModel
import com.beworkall.nyxo.data.AppEntry
import com.beworkall.nyxo.data.letterOf
import com.beworkall.nyxo.system.Sfx
import com.beworkall.nyxo.system.Sound
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_ROWS = 9

/**
 * One-handed app access: a letter strip on the screen edge.
 * Slide along it and the letters swell around your thumb while that letter's apps pop out beside it.
 * Slide sideways onto an app and let go to open it.
 */
@Composable
fun ThumbAlphabet(
    vm: LauncherViewModel,
    apps: List<AppEntry>,
    leftHanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val accentColor = accent()
    val letters = remember(apps) { apps.map { letterOf(it.label) }.distinct() }
    val byLetter = remember(apps) { apps.groupBy { letterOf(it.label) } }

    val stripW = with(density) { 36.dp.toPx() }
    val rowH = with(density) { 52.dp.toPx() }
    val popupW = with(density) { 236.dp.toPx() }
    val gap = with(density) { 10.dp.toPx() }
    val selectPad = with(density) { 14.dp.toPx() }
    val sigma = with(density) { 46.dp.toPx() }
    val swell = with(density) { 54.dp.toPx() }

    var touching by remember { mutableStateOf(false) }
    var waveY by remember { mutableFloatStateOf(0f) }
    var letterIndex by remember { mutableIntStateOf(-1) }
    var selected by remember { mutableIntStateOf(-1) }
    var selecting by remember { mutableStateOf(false) }

    val latestLetters = rememberUpdatedState(letters)
    val latestByLetter = rememberUpdatedState(byLetter)
    val launchApp: (AppEntry) -> Unit = { vm.launch(it) }
    val launch = rememberUpdatedState(launchApp)

    BoxWithConstraints(modifier) {
        val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val boxW = constraints.maxWidth.toFloat()

        fun currentApps(): List<AppEntry> {
            val ls = latestLetters.value
            if (letterIndex !in ls.indices) return emptyList()
            return latestByLetter.value[ls[letterIndex]].orEmpty().take(MAX_ROWS)
        }

        fun popupTop(rows: Int): Float {
            val h = rows * rowH
            return (waveY - h / 2f).coerceIn(0f, max(0f, boxH - h))
        }

        fun update(x: Float, y: Float) {
            val ls = latestLetters.value
            if (ls.isEmpty()) return
            val inSelectZone = if (leftHanded) x > stripW + selectPad else x < -selectPad
            if (!inSelectZone) {
                selecting = false
                if (selected != -1) selected = -1
                waveY = y.coerceIn(0f, boxH)
                val idx = (waveY / boxH * ls.size).toInt().coerceIn(0, ls.lastIndex)
                if (idx != letterIndex) {
                    letterIndex = idx
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    Sound.play(Sfx.TICK)
                }
            } else {
                selecting = true
                val rows = currentApps().size
                val i = floor((y - popupTop(rows)) / rowH).toInt()
                val next = if (i in 0 until rows) i else -1
                if (next != selected) {
                    selected = next
                    if (next >= 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }

        fun finish() {
            val list = currentApps()
            val pick = when {
                selecting && selected in list.indices -> list[selected]
                !selecting && list.size == 1 -> list[0]
                else -> null
            }
            touching = false
            selecting = false
            selected = -1
            letterIndex = -1
            if (pick != null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                launch.value(pick)
            }
        }

        content()

        // The swell follows your thumb on a spring, so it feels physical.
        val look = LocalLook.current
        val springWave by animateFloatAsState(
            targetValue = waveY,
            animationSpec = if (look.motion) spring(dampingRatio = 0.6f, stiffness = 500f) else spring(stiffness = 100_000f),
            label = "wave",
        )
        val swellAmount by animateFloatAsState(
            targetValue = if (touching) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
            label = "swell",
        )

        // ---- the letter strip
        Column(
            Modifier
                .align(if (leftHanded) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight()
                .width(36.dp)
                .pointerInput(leftHanded, boxH) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        touching = true
                        update(down.position.x, down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            update(change.position.x, change.position.y)
                        }
                        finish()
                    }
                },
        ) {
            val n = letters.size.coerceAtLeast(1)
            letters.forEachIndexed { i, c ->
                val centerY = (i + 0.5f) * boxH / n
                val d = (centerY - springWave) / sigma
                val f = exp(-(d * d)) * swellAmount
                val isCurrent = touching && i == letterIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationX = (if (leftHanded) 1f else -1f) * f * swell
                            scaleX = 1f + f * 1.1f
                            scaleY = 1f + f * 1.1f
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        c.toString(),
                        color = if (isCurrent) accentColor else lerp(Faint, Ink, f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // ---- the pop-out list of apps for the current letter
        val list = if (touching) currentApps() else emptyList()
        if (list.isNotEmpty()) {
            val top = popupTop(list.size)
            val x = if (leftHanded) stripW + gap else boxW - stripW - gap - popupW
            val letter = letters.getOrNull(letterIndex)
            Column(
                Modifier
                    .offset { IntOffset(x.roundToInt(), top.roundToInt()) }
                    .width(with(density) { popupW.toDp() })
                    .nyxoSurface(RoundedCornerShape(20.dp))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
            ) {
                list.forEachIndexed { i, app ->
                    val on = i == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(if (on) accentColor.copy(alpha = 0.18f) else Color.Transparent)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIconImage(app, 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            app.label,
                            color = if (on) accentColor else Ink,
                            fontSize = 15.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (letter != null) {
                // Big letter badge next to the list so you always know where you are.
                val badge = with(density) { 56.dp.toPx() }
                val bx = if (leftHanded) x + popupW + gap else x - gap - badge
                val by = (waveY - badge / 2f).coerceIn(0f, max(0f, boxH - badge))
                Box(
                    Modifier
                        .offset { IntOffset(bx.roundToInt(), by.roundToInt()) }
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(letter.toString(), color = Color(0xFF07090D), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

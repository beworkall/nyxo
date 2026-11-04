package com.beworkall.nyxo.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beworkall.nyxo.LauncherViewModel
import com.beworkall.nyxo.R
import com.beworkall.nyxo.radar.Risk
import com.beworkall.nyxo.system.Actions

private const val STEPS = 5

/** First-run setup. Every choice here can be changed later in Settings. */
@Composable
fun Onboarding(vm: LauncherViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val p = vm.prefs

    fun next() {
        // The hand choice only matters for the one-handed layout.
        step = if (step == 2 && p.gridLayout.value) 4 else (step + 1).coerceAtMost(STEPS - 1)
    }

    fun previous() {
        step = if (step == 4 && p.gridLayout.value) 2 else (step - 1).coerceAtLeast(0)
    }

    BackHandler(enabled = step > 0) { previous() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        ProgressDots(step)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 28.dp, bottom = 16.dp),
        ) {
            when (step) {
                0 -> WelcomeStep()
                1 -> PrivacyDotsStep(vm)
                2 -> HomeStyleStep(vm)
                3 -> HandStep(vm)
                else -> FinishStep(vm)
            }
        }
        Column(Modifier.padding(bottom = 18.dp)) {
            when (step) {
                0 -> ActionButton("Get started", primary = true) { next() }
                1 -> {
                    ActionButton("Turn on privacy dots", primary = true) {
                        p.radarDots.value = true
                        next()
                    }
                    ActionButton("Not now") {
                        p.radarDots.value = false
                        next()
                    }
                }
                2, 3 -> ActionButton("Continue", primary = true) { next() }
                else -> ActionButton("Start using Nyxo", primary = true) { p.onboarded.value = true }
            }
        }
    }
}

@Composable
private fun ProgressDots(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(STEPS) { i ->
            Box(
                Modifier
                    .height(4.dp)
                    .width(if (i == step) 26.dp else 10.dp)
                    .clip(CircleShape)
                    .background(if (i <= step) accent() else Line),
            )
        }
    }
}

@Composable
private fun StepTitle(kicker: String, title: String, body: String) {
    Text(kicker.uppercase(), color = accent(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
    Spacer(Modifier.height(10.dp))
    Text(title, color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
    Spacer(Modifier.height(12.dp))
    Text(body, color = Muted, fontSize = 16.sp, lineHeight = 23.sp)
}

// ---------------------------------------------------------------- 1. welcome

@Composable
private fun WelcomeStep() {
    Box(
        Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Panel)
            .border(1.dp, Line, RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(140.dp))
    }
    Spacer(Modifier.height(28.dp))
    StepTitle(
        "Welcome",
        "A home screen that's\non your side.",
        "Nyxo is fast, black and built for one hand. It also tells you which apps can see more than they should.",
    )
    Spacer(Modifier.height(24.dp))
    Bullet("🌑", "Pure black screen that saves battery on AMOLED")
    Bullet("👍", "Reach every app with your thumb")
    Bullet("📵", "No internet permission. Nothing you do here can leave your phone")
}

@Composable
private fun Bullet(icon: String, text: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Panel),
            contentAlignment = Alignment.Center,
        ) { Text(icon, fontSize = 18.sp) }
        Spacer(Modifier.width(14.dp))
        Text(text, color = Ink, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

// ---------------------------------------------------------------- 2. privacy dots

@Composable
private fun PrivacyDotsStep(vm: LauncherViewModel) {
    StepTitle(
        "Privacy dots",
        "See which apps\ncan see you.",
        "Some apps can do far more than they look like they do. Nyxo can put a small dot on their icon so you notice.",
    )
    Spacer(Modifier.height(24.dp))

    val pulse by rememberInfiniteTransition(label = "dots").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        DemoTile("A", Color(0xFF3A4BD8), RiskHigh, pulse)
        DemoTile("B", Color(0xFF1F8F6A), RiskMid, pulse)
        DemoTile("C", Color(0xFF8A4BD8), null, pulse)
    }
    Spacer(Modifier.height(22.dp))
    Legend(RiskHigh, "Full access", "It can read your screen or every notification, or control your device.")
    Legend(RiskMid, "Sensitive access", "Camera, microphone, location, SMS, call history or drawing over other apps.")
    Legend(null, "No dot", "None of the above.")

    Spacer(Modifier.height(18.dp))
    InfoCard(
        "Why it matters",
        "Scam and stalkerware apps hide in plain sight. To read your messages and one-time codes they need exactly " +
            "these permissions, so a red dot on an app you don't recognise is worth a look.",
        RiskHigh,
    )

    val results = vm.radar.values
    val full = results.count { it.risk == Risk.HIGH }
    val sensitive = results.count { it.hasIcon && it.risk == Risk.MEDIUM }
    val summary = when {
        results.isEmpty() -> "Checking the apps on this phone…"
        full == 0 && sensitive == 0 -> "On this phone right now: no apps with full or sensitive access."
        else -> "On this phone right now: $full with full access and $sensitive with sensitive access."
    }
    InfoCard(
        summary,
        "Checked on this phone only. Tap the radar icon in the app drawer any time to see the full list, " +
            "even if you keep the dots off.",
        accent(),
    )
}

@Composable
private fun DemoTile(letter: String, tint: Color, dot: Color?, pulse: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.55f)))),
                contentAlignment = Alignment.Center,
            ) { Text(letter, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            if (dot != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                        }
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(dot),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("App $letter", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun Legend(color: Color?, title: String, body: String) {
    Row(Modifier.padding(vertical = 7.dp)) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(color ?: Color.Transparent)
                .border(1.dp, color ?: Faint, CircleShape),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

// ---------------------------------------------------------------- 3. home style

@Composable
private fun HomeStyleStep(vm: LauncherViewModel) {
    val p = vm.prefs
    StepTitle("Home screen", "Pick your style.", "You can switch any time in Settings.")
    Spacer(Modifier.height(22.dp))
    OptionCard(
        "One-handed",
        "Your favourite apps as a clean list, and a letter strip on the edge. Slide your thumb along it to open any app.",
        recommended = true,
        selected = !p.gridLayout.value,
        onClick = { p.gridLayout.value = false },
    ) { OneHandedPreview(p.leftHanded.value) }
    Spacer(Modifier.height(12.dp))
    OptionCard(
        "Icon grid",
        "Classic icons with a dock at the bottom.",
        recommended = false,
        selected = p.gridLayout.value,
        onClick = { p.gridLayout.value = true },
    ) { GridPreview() }
}

// ---------------------------------------------------------------- 4. hand

@Composable
private fun HandStep(vm: LauncherViewModel) {
    val p = vm.prefs
    StepTitle("Your thumb", "Which hand do you use?", "The letter strip goes on that side of the screen.")
    Spacer(Modifier.height(22.dp))
    OptionCard(
        "Right hand",
        "Letters on the right edge.",
        recommended = false,
        selected = !p.leftHanded.value,
        onClick = { p.leftHanded.value = false },
    ) { OneHandedPreview(left = false) }
    Spacer(Modifier.height(12.dp))
    OptionCard(
        "Left hand",
        "Letters on the left edge.",
        recommended = false,
        selected = p.leftHanded.value,
        onClick = { p.leftHanded.value = true },
    ) { OneHandedPreview(left = true) }
}

// ---------------------------------------------------------------- 5. finish

@Composable
private fun FinishStep(vm: LauncherViewModel) {
    val activity = LocalContext.current as? Activity
    StepTitle("All set", "Three things to try.", "That's all there is to learn.")
    Spacer(Modifier.height(22.dp))
    if (!vm.prefs.gridLayout.value) {
        Bullet("👍", "Slide your thumb along the letters on the edge, then slide onto an app and let go")
    } else {
        Bullet("☝️", "Swipe up for all your apps")
    }
    Bullet("🔎", "Swipe down to search apps, contacts, settings, or type a sum like 15% of 2400")
    Bullet("✋", "Long-press any app to pin it, stack it, hide it or check its privacy")
    Spacer(Modifier.height(18.dp))
    if (!vm.isDefaultHome && activity != null) {
        InfoCard(
            "Make Nyxo your home screen",
            "So the Home button brings you here. Android will ask you to confirm.",
            accent(),
        )
        ActionButton("Set as home screen") { Actions.requestDefaultHome(activity) }
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun OptionCard(
    title: String,
    body: String,
    recommended: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) accent().copy(alpha = 0.10f) else Panel)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent() else Line, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(64.dp)
                .height(104.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .border(1.dp, Line, RoundedCornerShape(12.dp))
                .padding(6.dp),
        ) { preview() }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (recommended) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "RECOMMENDED",
                        color = accent(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
            }
            Text(body, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun OneHandedPreview(left: Boolean) {
    Row(Modifier.fillMaxSize()) {
        if (left) LetterStripPreview()
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            repeat(5) {
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(Muted))
                    Spacer(Modifier.width(3.dp))
                    Box(Modifier.height(3.dp).width(if (it % 2 == 0) 22.dp else 16.dp).clip(CircleShape).background(Ink.copy(alpha = 0.7f)))
                }
            }
        }
        if (!left) LetterStripPreview()
    }
}

@Composable
private fun LetterStripPreview() {
    Column(
        Modifier.width(5.dp).fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(12) { i ->
            Box(Modifier.size(if (i == 7) 4.dp else 2.dp).clip(CircleShape).background(if (i == 7) accent() else Faint))
        }
    }
}

@Composable
private fun GridPreview() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        repeat(3) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(4) { Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(Muted)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Ink.copy(alpha = 0.12f)),
        )
    }
}

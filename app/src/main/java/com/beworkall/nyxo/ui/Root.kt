package com.beworkall.nyxo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.beworkall.nyxo.LauncherViewModel
import com.beworkall.nyxo.Screen
import com.beworkall.nyxo.data.LoadedWallpaper
import com.beworkall.nyxo.data.Prefs
import com.beworkall.nyxo.data.Wallpapers
import com.beworkall.nyxo.data.packById

/** Picks the accent: the theme pack's colour unless the user chose their own. */
fun themeAccent(prefs: Prefs): Color =
    if (prefs.accentFromTheme.value) packById(prefs.themePack.value).accent
    else ACCENTS[prefs.accent.value.coerceIn(0, ACCENTS.lastIndex)]

/** Loads the current theme's wallpaper at screen size. Shared by the home screen and Nyxo Settings. */
@Composable
fun rememberWallpaper(prefs: Prefs, widthPx: Int, heightPx: Int): LoadedWallpaper? {
    val context = LocalContext.current
    val packId = prefs.themePack.value
    val pack = packById(packId)
    val version = if (packId == "custom") Wallpapers.customFile(context).lastModified() else 0L
    val wallpaper by produceState<LoadedWallpaper?>(null, packId, widthPx, heightPx, version) {
        value = Wallpapers.load(context, pack, widthPx, heightPx, version)
    }
    return wallpaper
}

@Composable
fun NyxoRoot(vm: LauncherViewModel) {
    val prefs = vm.prefs
    val accent = themeAccent(prefs)

    NyxoTheme(accent) {
        // A launcher never closes on Back; Back steps out of the current panel.
        BackHandler(enabled = true) { vm.back() }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wallpaper = rememberWallpaper(prefs, constraints.maxWidth, constraints.maxHeight)
            val style = prefs.panelStyle.value
            val look = remember(style, wallpaper, prefs.motion.value, prefs.tiltLight.value) {
                Look(
                    style = style,
                    wallpaper = wallpaper,
                    motion = prefs.motion.value,
                    tiltEnabled = prefs.tiltLight.value && wallpaper != null && style != "solid",
                    tiltX = { vm.tiltX },
                    tiltY = { vm.tiltY },
                )
            }
            val panelOpen = vm.screen != Screen.Home
            val depth by animateFloatAsState(
                targetValue = if (panelOpen && prefs.motion.value) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 300f),
                label = "depth",
            )
            val ripples = remember { Ripples() }
            val waterOn = style == "water" && prefs.motion.value

            CompositionLocalProvider(LocalLook provides look) {
                WallpaperLayer(wallpaper, prefs.dim.value, depth)
                if (waterOn) RippleOverlay(ripples, Color.White)

                HomeScreen(vm, if (waterOn) ripples else null)

                SlidePanel(vm.screen == Screen.Drawer, prefs.motion.value) { DrawerScreen(vm) }
                SlidePanel(vm.screen == Screen.Radar, prefs.motion.value) { RadarScreen(vm) }
                SlidePanel(vm.screen == Screen.Hidden, prefs.motion.value) { HiddenScreen(vm) }

                val menuApp = vm.menuApp
                if (menuApp != null) AppMenu(vm, menuApp)
                val detail = vm.radarDetail
                if (detail != null) RadarDetailDialog(vm, detail)
                val pickFor = vm.stackPickerFor
                if (pickFor != null) StackPicker(vm, pickFor)
                val editId = vm.editStackId
                if (editId != null) StackEditor(vm, editId)

                // First-run setup sits above everything until it's finished.
                SlidePanel(!prefs.onboarded.value, prefs.motion.value) { Onboarding(vm) }
            }
        }
    }
}

@Composable
private fun SlidePanel(visible: Boolean, motion: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = if (motion) {
            fadeIn(tween(140)) + slideInVertically(spring(dampingRatio = 0.82f, stiffness = 420f)) { it / 5 }
        } else {
            fadeIn(tween(100))
        },
        exit = if (motion) {
            fadeOut(tween(130)) + slideOutVertically(tween(170)) { it / 6 }
        } else {
            fadeOut(tween(90))
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            PanelBackdrop()
            content()
        }
    }
}

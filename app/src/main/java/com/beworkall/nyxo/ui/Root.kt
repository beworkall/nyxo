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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.beworkall.nyxo.LauncherViewModel
import com.beworkall.nyxo.Screen
import com.beworkall.nyxo.data.IconCache
import com.beworkall.nyxo.data.LoadedWallpaper
import com.beworkall.nyxo.data.Prefs
import com.beworkall.nyxo.data.Wallpapers
import com.beworkall.nyxo.data.packById

/** Picks the accent from the chosen source: theme pack, wallpaper, Material You, or your own pick. */
fun themeAccent(prefs: Prefs, wallpaper: LoadedWallpaper?, context: android.content.Context): Color =
    when (prefs.accentMode.value) {
        "custom" -> ACCENTS[prefs.accent.value.coerceIn(0, ACCENTS.lastIndex)]
        "wallpaper" -> wallpaper?.vibrant ?: packById(prefs.themePack.value).accent
        "system" -> if (android.os.Build.VERSION.SDK_INT >= 31) {
            Color(context.getColor(android.R.color.system_accent1_200))
        } else {
            packById(prefs.themePack.value).accent
        }
        else -> packById(prefs.themePack.value).accent
    }

/** Keeps icon packs and themed icons in sync with the settings. */
@Composable
fun SyncIconSettings(prefs: Prefs, accent: Color) {
    val argb = accent.toArgb()
    val pack = prefs.iconPack.value
    val themed = prefs.iconThemed.value
    LaunchedEffect(pack, themed, argb) { IconCache.configure(pack, themed, argb) }
}

/** Loads the current theme's wallpaper at screen size. Shared by the home screen and Nyxo Settings. */
@Composable
fun rememberWallpaper(prefs: Prefs): LoadedWallpaper? {
    val context = LocalContext.current
    // Always the full physical screen. The window shrinks when the keyboard opens; the wallpaper must not.
    val (widthPx, heightPx) = remember { screenSize(context) }
    val packId = prefs.themePack.value
    val pack = packById(packId)
    val version = if (packId == "custom") Wallpapers.customFile(context).lastModified() else 0L
    // Keeps showing the current wallpaper until the next one is ready, so switching themes never flashes black.
    var wallpaper by remember { mutableStateOf<LoadedWallpaper?>(null) }
    LaunchedEffect(packId, widthPx, heightPx, version) {
        if (widthPx <= 0 || heightPx <= 0) return@LaunchedEffect
        val next = Wallpapers.load(context, pack, widthPx, heightPx, version)
        if (next != null || pack.res == null && packId != "custom") wallpaper = next
    }
    return wallpaper
}

@Composable
fun NyxoRoot(vm: LauncherViewModel) {
    val prefs = vm.prefs
    val context = LocalContext.current
    val wallpaper = rememberWallpaper(prefs)
    val accent = themeAccent(prefs, wallpaper, context)
    SyncIconSettings(prefs, accent)

    NyxoTheme(accent) {
        // A launcher never closes on Back; Back steps out of the current panel.
        BackHandler(enabled = true) { vm.back() }

        BoxWithConstraints(Modifier.fillMaxSize()) {
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

            val font = remember(prefs.font.value) { fontFamilyOf(prefs.font.value) }
            CompositionLocalProvider(LocalLook provides look, LocalNyxoFont provides font) {
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
                val limited = vm.breakerApp
                if (limited != null) BreakerDialog(vm, limited)

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

private fun screenSize(context: android.content.Context): Pair<Int, Int> {
    val wm = context.getSystemService(android.view.WindowManager::class.java)
    if (wm != null && android.os.Build.VERSION.SDK_INT >= 30) {
        val b = wm.maximumWindowMetrics.bounds
        return minOf(b.width(), b.height()) to maxOf(b.width(), b.height())
    }
    val dm = android.util.DisplayMetrics()
    @Suppress("DEPRECATION")
    wm?.defaultDisplay?.getRealMetrics(dm)
    val w = if (dm.widthPixels > 0) dm.widthPixels else context.resources.displayMetrics.widthPixels
    val h = if (dm.heightPixels > 0) dm.heightPixels else context.resources.displayMetrics.heightPixels
    return minOf(w, h) to maxOf(w, h)
}

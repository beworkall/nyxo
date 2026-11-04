package com.beworkall.nyxo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.beworkall.nyxo.LauncherViewModel
import com.beworkall.nyxo.Screen

@Composable
fun NyxoRoot(vm: LauncherViewModel) {
    val prefs = vm.prefs
    val accent = ACCENTS[prefs.accent.value.coerceIn(0, ACCENTS.lastIndex)]

    NyxoTheme(accent) {
        // A launcher never closes on Back; Back just steps out of the current panel.
        BackHandler(enabled = true) { vm.back() }

        Box(Modifier.fillMaxSize()) {
            val shade = if (prefs.showWallpaper.value) prefs.dim.value else 1f
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = shade)))

            HomeScreen(vm)

            SlidePanel(vm.screen == Screen.Drawer) { DrawerScreen(vm) }
            SlidePanel(vm.screen == Screen.Radar) { RadarScreen(vm) }
            SlidePanel(vm.screen == Screen.Hidden) { HiddenScreen(vm) }
            SlidePanel(vm.screen == Screen.Settings) { SettingsScreen(vm) }

            val menuApp = vm.menuApp
            if (menuApp != null) AppMenu(vm, menuApp)
            val detail = vm.radarDetail
            if (detail != null) RadarDetailDialog(vm, detail)
        }
    }
}

@Composable
private fun SlidePanel(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 6 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 6 },
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xF505070A))) { content() }
    }
}

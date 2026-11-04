package com.beworkall.nyxo.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beworkall.nyxo.BuildConfig
import com.beworkall.nyxo.LauncherViewModel
import com.beworkall.nyxo.Screen
import com.beworkall.nyxo.system.Actions

@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val p = vm.prefs
    var pickFor by remember { mutableStateOf<String?>(null) }

    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        p.searchContacts.value = granted
        if (!granted) Actions.toast(context, "Contact search stays off without permission")
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Header("Settings", "Nyxo Launcher ${BuildConfig.VERSION_NAME}") { vm.screen = Screen.Home }
        LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 40.dp)) {
            if (!vm.isDefaultHome && activity != null) {
                item { DefaultBanner { Actions.requestDefaultHome(activity) } }
            }

            item { SectionLabel("Look") }
            item {
                ChoiceRow(
                    "Home style",
                    listOf("grid" to "Icons + dock", "list" to "Text list"),
                    if (p.gridLayout.value) "grid" else "list",
                ) { p.gridLayout.value = it == "grid" }
            }
            if (p.gridLayout.value) {
                item {
                    ChoiceRow("Columns", listOf("4" to "4", "5" to "5"), p.columns.value.toString()) {
                        p.columns.value = it.toInt()
                    }
                }
            }
            item { AccentRow(p.accent.value) { p.accent.value = it } }
            item {
                SwitchRow("Show wallpaper", "Off keeps the screen pure AMOLED black", p.showWallpaper.value) {
                    p.showWallpaper.value = it
                }
            }
            if (p.showWallpaper.value) {
                item {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text("Wallpaper dim", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Slider(
                            value = p.dim.value,
                            onValueChange = { p.dim.value = it },
                            valueRange = 0f..0.9f,
                            colors = SliderDefaults.colors(thumbColor = accent(), activeTrackColor = accent()),
                        )
                    }
                }
            }
            item {
                SwitchRow("Privacy dots on icons", "Red: full access. Amber: sensitive access.", p.radarDots.value) {
                    p.radarDots.value = it
                }
            }
            item { SwitchRow("24-hour clock", null, p.clock24.value) { p.clock24.value = it } }

            item { SectionLabel("Drawer & search") }
            item {
                SwitchRow("Open keyboard with the drawer", "Start typing as soon as you swipe up", p.autoKeyboard.value) {
                    p.autoKeyboard.value = it
                }
            }
            item {
                SwitchRow("Search contacts", "Needs contacts permission. Results stay on this phone.", p.searchContacts.value) { on ->
                    if (!on) {
                        p.searchContacts.value = false
                    } else if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        p.searchContacts.value = true
                    } else {
                        contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    }
                }
            }

            item { SectionLabel("Gestures") }
            item {
                ChoiceRow(
                    "Double-tap",
                    listOf("search" to "Search", "settings" to "Settings", "none" to "Off"),
                    p.doubleTap.value,
                ) { p.doubleTap.value = it }
            }
            item {
                ChoiceRow(
                    "Swipe down",
                    listOf("search" to "Search", "none" to "Off"),
                    p.swipeDown.value,
                ) { p.swipeDown.value = it }
            }
            item {
                SettingRow("Swipe left opens", vm.appByKey(p.swipeLeft.value)?.label ?: "Nothing") { pickFor = "left" }
            }
            item {
                SettingRow("Swipe right opens", vm.appByKey(p.swipeRight.value)?.label ?: "Nothing") { pickFor = "right" }
            }
            item { SettingRow("Long-press empty space", "Opens these settings") }

            item { SectionLabel("Privacy") }
            item {
                SettingRow("Privacy Radar", "See which apps can watch your screen, read notifications or use your camera") {
                    vm.screen = Screen.Radar
                }
            }
            item {
                SettingRow("Hidden apps", "${p.hidden.value.size} hidden. Long-press any app and choose Hide app.") {
                    if (activity != null) Actions.authenticate(activity, "Hidden apps") { vm.screen = Screen.Hidden }
                }
            }
            item {
                InfoCard(
                    "No internet permission",
                    "Nyxo can't connect to the internet at all. Your apps, searches and settings never leave this phone.",
                )
            }

            item { SectionLabel("About") }
            item {
                SettingRow("Source code", "github.com/beworkall/nyxo") {
                    Actions.url(context, "https://github.com/beworkall/nyxo")
                }
            }
            item { SettingRow("Version", "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})") }
        }
    }

    val side = pickFor
    if (side != null) {
        AppPicker(
            vm,
            title = if (side == "left") "Swipe left opens…" else "Swipe right opens…",
            onPick = { app ->
                val key = app?.key ?: ""
                if (side == "left") p.swipeLeft.value = key else p.swipeRight.value = key
                pickFor = null
            },
            onDismiss = { pickFor = null },
        )
    }
}

@Composable
private fun AccentRow(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
        Text("Accent", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ACCENTS.forEachIndexed { i, c ->
                val on = i == selected
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(if (on) 3.dp else 0.dp, if (on) Ink else Color.Transparent, CircleShape)
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color(0xFF07090D), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

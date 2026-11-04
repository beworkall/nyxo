package com.beworkall.nyxo.ui.settings

import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.beworkall.nyxo.BuildConfig
import com.beworkall.nyxo.LauncherViewModel
import com.beworkall.nyxo.MainActivity
import com.beworkall.nyxo.Screen
import com.beworkall.nyxo.SettingsActivity
import com.beworkall.nyxo.data.THEME_PACKS
import com.beworkall.nyxo.data.ThemePack
import com.beworkall.nyxo.data.WidgetHosting
import com.beworkall.nyxo.data.WidgetSpec
import com.beworkall.nyxo.data.Wallpapers
import com.beworkall.nyxo.data.packById
import com.beworkall.nyxo.system.Actions
import com.beworkall.nyxo.system.Sfx
import com.beworkall.nyxo.system.Sound
import com.beworkall.nyxo.ui.ACCENTS
import com.beworkall.nyxo.ui.ActionButton
import com.beworkall.nyxo.ui.AppPicker
import com.beworkall.nyxo.ui.ChoiceRow
import com.beworkall.nyxo.ui.DefaultBanner
import com.beworkall.nyxo.ui.Faint
import com.beworkall.nyxo.ui.Header
import com.beworkall.nyxo.ui.HiddenScreen
import com.beworkall.nyxo.ui.InfoCard
import com.beworkall.nyxo.ui.Ink
import com.beworkall.nyxo.ui.LocalLook
import com.beworkall.nyxo.ui.Look
import com.beworkall.nyxo.ui.Muted
import com.beworkall.nyxo.ui.NyxoTheme
import com.beworkall.nyxo.ui.Panel
import com.beworkall.nyxo.ui.PanelBackdrop
import com.beworkall.nyxo.ui.PanelHi
import com.beworkall.nyxo.ui.RadarDetailDialog
import com.beworkall.nyxo.ui.RadarScreen
import com.beworkall.nyxo.ui.SectionLabel
import com.beworkall.nyxo.ui.SettingRow
import com.beworkall.nyxo.ui.SwitchRow
import com.beworkall.nyxo.ui.WallpaperLayer
import com.beworkall.nyxo.ui.accent
import com.beworkall.nyxo.ui.nyxoSurface
import com.beworkall.nyxo.ui.privacyScore
import com.beworkall.nyxo.ui.rememberWallpaper
import com.beworkall.nyxo.ui.themeAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Page(val title: String) {
    Main("Nyxo Settings"),
    Theme("Theme"),
    HomeLayout("Home screen"),
    Widgets("Widgets"),
    SoundMotion("Sound & motion"),
    Gestures("Gestures"),
    DrawerSearch("Drawer & search"),
    Privacy("Privacy"),
    About("About"),
}

@Composable
fun SettingsRoot(vm: LauncherViewModel, activity: SettingsActivity) {
    val prefs = vm.prefs
    NyxoTheme(themeAccent(prefs)) {
        val stack = remember { mutableStateListOf(Page.Main) }
        val page = stack.last()
        fun open(p: Page) { Sound.play(Sfx.TAP); stack.add(p) }
        fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else activity.finish() }

        // Radar and Hidden apps are shared screens; they navigate with vm.screen and come back with vm.back().
        val overlay = vm.screen
        BackHandler(enabled = true) {
            when {
                vm.radarDetail != null -> vm.radarDetail = null
                overlay != Screen.Home -> vm.back()
                else -> pop()
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wallpaper = rememberWallpaper(prefs, constraints.maxWidth, constraints.maxHeight)
            val look = remember(prefs.panelStyle.value, wallpaper, prefs.motion.value) {
                Look(
                    style = prefs.panelStyle.value,
                    wallpaper = wallpaper,
                    motion = prefs.motion.value,
                    tiltEnabled = false,
                    tiltX = { 0f },
                    tiltY = { 0f },
                )
            }
            CompositionLocalProvider(LocalLook provides look) {
                WallpaperLayer(wallpaper, prefs.dim.value, 1f)
                PanelBackdrop()
                when (overlay) {
                    Screen.Radar -> RadarScreen(vm)
                    Screen.Hidden -> HiddenScreen(vm)
                    else -> AnimatedContent(
                        targetState = page,
                        transitionSpec = {
                            val forward = targetState.ordinal >= initialState.ordinal && targetState != Page.Main
                            if (prefs.motion.value) {
                                (slideInHorizontally(tween(220)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(180))) togetherWith
                                    (slideOutHorizontally(tween(200)) { if (forward) -it / 6 else it / 6 } + fadeOut(tween(140)))
                            } else {
                                fadeIn(tween(90)) togetherWith fadeOut(tween(90))
                            }
                        },
                        label = "settingsPage",
                    ) { p ->
                        Column(Modifier.fillMaxSize().systemBarsPadding()) {
                            Header(p.title, if (p == Page.Main) "Home screen, themes, widgets, privacy" else null) { pop() }
                            when (p) {
                                Page.Main -> MainPage(vm, activity, ::open)
                                Page.Theme -> ThemePage(vm)
                                Page.HomeLayout -> HomeLayoutPage(vm)
                                Page.Widgets -> WidgetsPage(vm, activity)
                                Page.SoundMotion -> SoundMotionPage(vm)
                                Page.Gestures -> GesturesPage(vm)
                                Page.DrawerSearch -> DrawerSearchPage(vm)
                                Page.Privacy -> PrivacyPage(vm, activity)
                                Page.About -> AboutPage(vm, activity)
                            }
                        }
                    }
                }
                val detail = vm.radarDetail
                if (detail != null) RadarDetailDialog(vm, detail)
            }
        }
    }
}

@Composable
private fun SettingsList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        content = content,
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .nyxoSurface(RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) { content() }
}

// ------------------------------------------------------------------- main

@Composable
private fun MainPage(vm: LauncherViewModel, activity: SettingsActivity, open: (Page) -> Unit) {
    val context = LocalContext.current
    val pack = packById(vm.prefs.themePack.value)
    SettingsList {
        item {
            if (!vm.isDefaultHome) {
                DefaultBanner { Actions.requestDefaultHome(activity) }
                Spacer(Modifier.height(10.dp))
            }
            Group {
                SettingRow(
                    "Switch home app",
                    if (vm.isDefaultHome) "Go back to One UI Home or pick another launcher. You can come back any time."
                    else "Choose which home screen your phone uses",
                ) { Actions.switchHomeApp(context) }
            }
            SectionLabel("Look")
            Group {
                SettingRow("Theme", "${pack.name} · ${styleName(vm.prefs.panelStyle.value)}") { open(Page.Theme) }
                SettingRow("Home screen", if (vm.prefs.gridLayout.value) "Icon grid" else "One-handed list") { open(Page.HomeLayout) }
                SettingRow("Widgets", widgetSummary(vm)) { open(Page.Widgets) }
            }
            SectionLabel("Feel")
            Group {
                SettingRow("Sound & motion", if (vm.prefs.sounds.value) "Sounds on" else "Sounds off") { open(Page.SoundMotion) }
                SettingRow("Gestures", "Double-tap, swipes, long-press") { open(Page.Gestures) }
                SettingRow("Drawer & search", null) { open(Page.DrawerSearch) }
            }
            SectionLabel("Safety")
            Group {
                SettingRow("Privacy", if (vm.prefs.radarDots.value) "Privacy dots on" else "Privacy dots off") { open(Page.Privacy) }
            }
            SectionLabel("Nyxo")
            Group {
                SettingRow("About", "Version ${BuildConfig.VERSION_NAME}") { open(Page.About) }
            }
        }
    }
}

private fun styleName(style: String) = when (style) {
    "glass" -> "Glass"
    "hardglass" -> "Hard glass"
    "water" -> "Water"
    else -> "Solid"
}

private fun widgetSummary(vm: LauncherViewModel): String {
    val n = vm.prefs.widgets.value.size
    return if (n == 0) "None yet" else "$n on your home screen"
}

// ------------------------------------------------------------------- theme

@Composable
private fun ThemePage(vm: LauncherViewModel) {
    val p = vm.prefs
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            if (Wallpapers.importCustom(context, uri)) {
                p.themePack.value = "custom"
                if (p.panelStyle.value == "solid") p.panelStyle.value = "glass"
                Sound.play(Sfx.GLASS)
            } else {
                Actions.toast(context, "Couldn't open that photo")
            }
        }
    }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Column(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(THEME_PACKS, key = { it.id }) { pack ->
                PackCard(pack, selected = p.themePack.value == pack.id) {
                    if (pack.id == "custom" && !Wallpapers.customFile(context).exists()) {
                        pickPhoto()
                    } else {
                        p.themePack.value = pack.id
                        p.panelStyle.value = pack.style
                        Sound.play(if (pack.style == "water") Sfx.DROP else Sfx.GLASS)
                    }
                }
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column {
                    SectionLabel("Panel style")
                    ChoiceRow(
                        "How panels look over the wallpaper",
                        listOf("solid" to "Solid", "glass" to "Glass", "hardglass" to "Hard", "water" to "Water"),
                        p.panelStyle.value,
                    ) { p.panelStyle.value = it; Sound.play(if (it == "water") Sfx.DROP else Sfx.GLASS) }
                    Text(
                        "Glass: soft frosted blur. Hard glass: sharper, bright edges. Water: ripples where you touch.",
                        color = Muted, fontSize = 13.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    SectionLabel("Wallpaper")
                    Text("Darken wallpaper", color = Ink, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp, top = 6.dp))
                    Slider(
                        value = p.dim.value,
                        onValueChange = { p.dim.value = it },
                        valueRange = 0f..0.8f,
                        colors = sliderColors(),
                    )
                    if (p.themePack.value == "custom") {
                        ActionButton("Pick a different photo") { pickPhoto() }
                    }
                    if (p.themePack.value != "amoled") {
                        ActionButton("Use as phone wallpaper too") {
                            scope.launch {
                                val file = wallpaperFile(context, packById(p.themePack.value))
                                if (file != null) Actions.setAsPhoneWallpaper(context, file)
                                else Actions.toast(context, "Couldn't prepare the wallpaper")
                            }
                        }
                    }
                    SectionLabel("Accent colour")
                    SwitchRow("Match the theme", "Use the colour picked for this wallpaper", p.accentFromTheme.value) {
                        p.accentFromTheme.value = it
                    }
                    if (!p.accentFromTheme.value) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ACCENTS.forEachIndexed { i, c ->
                                val on = p.accent.value == i
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(if (on) 3.dp else 0.dp, if (on) Ink else Color.Transparent, CircleShape)
                                        .clickable { p.accent.value = i; Sound.play(Sfx.TAP) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Wallpapers created by BEwork with Google Gemini.",
                        color = Faint, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PackCard(pack: ThemePack, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val customVersion = if (pack.id == "custom") Wallpapers.customFile(context).lastModified() else 0L
    val thumb by produceState<ImageBitmap?>(null, pack.id, customVersion) {
        value = Wallpapers.thumbnail(context, pack, 240, 480)
    }
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) pack.accent else Color.White.copy(alpha = 0.08f), shape)
            .background(Panel)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.72f).background(Color.Black)) {
            val t = thumb
            if (t != null) {
                Image(t, contentDescription = pack.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (pack.id == "custom") {
                Text("+ Photo", color = Muted, fontSize = 15.sp, modifier = Modifier.align(Alignment.Center))
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(pack.accent),
            )
            if (selected) {
                Text(
                    "IN USE",
                    color = Color(0xFF07090D),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(pack.accent)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(pack.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(pack.tagline, color = Muted, fontSize = 12.sp, maxLines = 2, lineHeight = 16.sp, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A file the system wallpaper screen can read: the custom photo, or the pack image written out once. */
private suspend fun wallpaperFile(context: android.content.Context, pack: ThemePack): File? = withContext(Dispatchers.IO) {
    if (pack.id == "custom") return@withContext Wallpapers.customFile(context).takeIf { it.exists() }
    val res = pack.res ?: return@withContext null
    runCatching {
        val out = File(context.filesDir, "share_${pack.id}.jpg")
        if (!out.exists()) {
            val bmp = BitmapFactory.decodeResource(context.resources, res) ?: return@runCatching null
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            bmp.recycle()
        }
        out
    }.getOrNull()
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = accent(),
    activeTrackColor = accent(),
    inactiveTrackColor = PanelHi,
)

// ------------------------------------------------------------------- home layout

@Composable
private fun HomeLayoutPage(vm: LauncherViewModel) {
    val p = vm.prefs
    SettingsList {
        item {
            Group {
                ChoiceRow(
                    "Layout",
                    listOf("list" to "One-handed list", "grid" to "Icon grid"),
                    if (p.gridLayout.value) "grid" else "list",
                ) { p.gridLayout.value = it == "grid" }
                ChoiceRow(
                    "Letter strip side",
                    listOf("right" to "Right hand", "left" to "Left hand"),
                    if (p.leftHanded.value) "left" else "right",
                ) { p.leftHanded.value = it == "left" }
                if (p.gridLayout.value) {
                    ChoiceRow(
                        "Columns",
                        listOf("3" to "3", "4" to "4", "5" to "5"),
                        p.columns.value.toString(),
                    ) { p.columns.value = it.toInt() }
                }
            }
            SectionLabel("Clock")
            Group {
                ChoiceRow(
                    "Style",
                    listOf("thin" to "Thin", "bold" to "Bold", "stacked" to "Stacked"),
                    p.clockStyle.value,
                ) { p.clockStyle.value = it }
                SwitchRow("24-hour clock", null, p.clock24.value) { p.clock24.value = it }
            }
        }
    }
}

// ------------------------------------------------------------------- widgets

@Composable
private fun WidgetsPage(vm: LauncherViewModel, activity: SettingsActivity) {
    val context = LocalContext.current
    val p = vm.prefs
    val specs = p.widgets.value.mapNotNull { WidgetSpec.decode(it) }
    var adding by remember { mutableStateOf(false) }
    val manager = remember { WidgetHosting.manager(context) }

    SettingsList {
        item {
            Text(
                "Widgets sit above your apps on the home screen. Nyxo's own widgets need no permissions.",
                color = Muted, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            SectionLabel("On your home screen")
        }
        if (specs.isEmpty()) {
            item { Text("Nothing yet. Add one below.", color = Faint, fontSize = 14.sp, modifier = Modifier.padding(8.dp)) }
        }
        items(specs, key = { it.key }) { spec ->
            val name = remember(spec.key) {
                if (spec.kind == "app") {
                    runCatching { manager.getAppWidgetInfo(spec.id)?.loadLabel(context.packageManager) }.getOrNull() ?: "Widget"
                } else {
                    WidgetSpec.BUILT_IN.firstOrNull { it.first == spec.kind }?.second ?: spec.kind
                }
            }
            val index = specs.indexOf(spec)
            Group {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Height ${spec.heightDp} dp", color = Muted, fontSize = 12.sp)
                    }
                    MiniBtn("−") { resize(vm, spec, -30) }
                    MiniBtn("+") { resize(vm, spec, 30) }
                    MiniBtn("↑", enabled = index > 0) { move(vm, index, -1) }
                    MiniBtn("↓", enabled = index < specs.lastIndex) { move(vm, index, 1) }
                    MiniBtn("✕") { activity.removeWidget(spec) }
                }
            }
        }
        item {
            SectionLabel("Nyxo widgets")
            Group {
                for ((kind, title, sub) in WidgetSpec.BUILT_IN) {
                    val already = specs.any { it.kind == kind }
                    SettingRow(title, if (already) "$sub · on home" else sub) { activity.addBuiltInWidget(kind) }
                }
            }
            SectionLabel("App widgets")
            ActionButton(if (adding) "Hide app widgets" else "Browse app widgets", primary = !adding) { adding = !adding }
        }
        if (adding) {
            item { AppWidgetList(onPick = { activity.addAppWidget(it); adding = false }) }
        }
    }
}

@Composable
private fun AppWidgetList(onPick: (AppWidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    val providers by produceState<List<Pair<AppWidgetProviderInfo, String>>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            runCatching {
                WidgetHosting.manager(context).installedProviders.map { info ->
                    val app = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
                    }.getOrDefault(info.provider.packageName)
                    info to app
                }.sortedWith(compareBy<Pair<AppWidgetProviderInfo, String>>({ it.second.lowercase() }, { it.first.loadLabel(pm).lowercase() }))
            }.getOrDefault(emptyList())
        }
    }
    val density = context.resources.displayMetrics.densityDpi
    Column(Modifier.padding(top = 8.dp)) {
        if (providers.isEmpty()) Text("Looking for widgets…", color = Faint, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
        var lastApp = ""
        for ((info, app) in providers) {
            if (app != lastApp) {
                Text(app, color = accent(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 4.dp))
                lastApp = app
            }
            val icon = remember(info.provider) {
                runCatching { info.loadIcon(context, density)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onPick(info) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(34.dp))
                else Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(PanelHi))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(info.loadLabel(context.packageManager), color = Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val cells = "${((info.minWidth + 30) / 70).coerceAtLeast(1)} × ${((info.minHeight + 30) / 70).coerceAtLeast(1)}"
                    Text(cells, color = Faint, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MiniBtn(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(34.dp)
            .clip(CircleShape)
            .background(if (enabled) PanelHi else Color.Transparent)
            .clickable(enabled = enabled) { Sound.play(Sfx.TAP); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Ink else Faint, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

private fun resize(vm: LauncherViewModel, spec: WidgetSpec, delta: Int) {
    val h = (spec.heightDp + delta).coerceIn(60, 520)
    vm.prefs.widgets.value = vm.prefs.widgets.value.map {
        if (WidgetSpec.decode(it)?.key == spec.key) spec.copy(heightDp = h).encode() else it
    }
}

private fun move(vm: LauncherViewModel, index: Int, by: Int) {
    val list = vm.prefs.widgets.value.toMutableList()
    val to = index + by
    if (index !in list.indices || to !in list.indices) return
    val item = list.removeAt(index)
    list.add(to, item)
    vm.prefs.widgets.value = list
}

// ------------------------------------------------------------------- sound & motion

@Composable
private fun SoundMotionPage(vm: LauncherViewModel) {
    val p = vm.prefs
    SettingsList {
        item {
            Group {
                SwitchRow("Sounds", "Soft taps, glass chimes and water drops. Silent when your phone is on vibrate or mute.", p.sounds.value) {
                    p.sounds.value = it
                    if (it) Sound.play(Sfx.GLASS)
                }
                if (p.sounds.value) {
                    Text("Sound volume", color = Ink, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
                    Slider(
                        value = p.soundVolume.value,
                        onValueChange = { p.soundVolume.value = it },
                        onValueChangeFinished = { Sound.play(Sfx.TAP) },
                        valueRange = 0.1f..1f,
                        colors = sliderColors(),
                    )
                }
            }
            SectionLabel("Motion")
            Group {
                SwitchRow("Motion effects", "Springy panels, depth and ripples. Turn off to save a little battery.", p.motion.value) {
                    p.motion.value = it
                }
                SwitchRow("Tilt light", "Glass catches the light as you tilt your phone. Only runs while the home screen is open.", p.tiltLight.value) {
                    p.tiltLight.value = it
                }
            }
        }
    }
}

// ------------------------------------------------------------------- gestures

@Composable
private fun GesturesPage(vm: LauncherViewModel) {
    val p = vm.prefs
    var pickFor by remember { mutableStateOf<String?>(null) }
    SettingsList {
        item {
            Group {
                ChoiceRow(
                    "Double-tap the home screen",
                    listOf("search" to "Search", "drawer" to "Apps", "radar" to "Radar", "none" to "Nothing"),
                    p.doubleTap.value,
                ) { p.doubleTap.value = it }
                ChoiceRow(
                    "Swipe down",
                    listOf("search" to "Search", "none" to "Nothing"),
                    p.swipeDown.value,
                ) { p.swipeDown.value = it }
                SettingRow("Swipe left opens", vm.appByKey(p.swipeLeft.value)?.label ?: "Nothing") { pickFor = "left" }
                SettingRow("Swipe right opens", vm.appByKey(p.swipeRight.value)?.label ?: "Nothing") { pickFor = "right" }
                SwitchRow("Long-press opens Nyxo Settings", "Hold on an empty spot of the home screen", p.longPressSettings.value) {
                    p.longPressSettings.value = it
                }
            }
            Text(
                "Swipe up always opens your apps. Nyxo Settings also has its own icon in the app list.",
                color = Muted, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
    }
    val side = pickFor
    if (side != null) {
        AppPicker(
            vm,
            if (side == "left") "Swipe left opens…" else "Swipe right opens…",
            onPick = { app ->
                val key = app?.key ?: ""
                if (side == "left") p.swipeLeft.value = key else p.swipeRight.value = key
                pickFor = null
            },
            onDismiss = { pickFor = null },
        )
    }
}

// ------------------------------------------------------------------- drawer

@Composable
private fun DrawerSearchPage(vm: LauncherViewModel) {
    val p = vm.prefs
    SettingsList {
        item {
            Group {
                SwitchRow("Open keyboard with search", "Start typing as soon as search opens", p.autoKeyboard.value) {
                    p.autoKeyboard.value = it
                }
                SwitchRow("Search contacts", "Show matching contacts in search. Asks for contacts access.", p.searchContacts.value) {
                    p.searchContacts.value = it
                }
            }
        }
    }
}

// ------------------------------------------------------------------- privacy

@Composable
private fun PrivacyPage(vm: LauncherViewModel, activity: SettingsActivity) {
    val p = vm.prefs
    val score = remember(vm.radar) { privacyScore(vm.radar.values) }
    SettingsList {
        item {
            Group {
                SwitchRow(
                    "Privacy dots",
                    "A small coloured dot next to apps that can see or do a lot. Red = check it, amber = worth a look.",
                    p.radarDots.value,
                ) { p.radarDots.value = it }
            }
            Spacer(Modifier.height(6.dp))
            Group {
                SettingRow("Privacy Radar", "Score ${score.score}/100 · ${score.grade}") { vm.screen = Screen.Radar }
                val hiddenCount = p.hidden.value.size
                SettingRow("Hidden apps", if (hiddenCount == 0) "None" else "$hiddenCount hidden · unlock to view") {
                    Actions.authenticate(activity, "Hidden apps") {
                        Sound.play(Sfx.UNLOCK)
                        vm.screen = Screen.Hidden
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            InfoCard(
                "Nyxo has no internet access",
                "No accessibility service, no overlays, no tracking. Everything Nyxo checks stays on your phone.",
            )
        }
    }
}

// ------------------------------------------------------------------- about

@Composable
private fun AboutPage(vm: LauncherViewModel, activity: SettingsActivity) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    SettingsList {
        item {
            Group {
                SettingRow("Nyxo Launcher", "Version ${BuildConfig.VERSION_NAME}")
                SettingRow("Source code", "github.com/beworkall/nyxo") { Actions.url(context, "https://github.com/beworkall/nyxo") }
                SettingRow("Wallpapers", "Created by BEwork with Google Gemini")
            }
            SectionLabel("Setup")
            Group {
                SettingRow("Restart first-time setup", "Walk through the welcome steps again") { confirm = true }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Panel,
            title = { Text("Restart setup?", color = Ink) },
            text = { Text("Your apps, widgets and themes stay as they are. You'll just see the welcome steps again.", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    vm.prefs.onboarded.value = false
                    activity.startActivity(
                        Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                    activity.finish()
                }) { Text("Restart", color = accent()) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel", color = Muted) } },
        )
    }
}

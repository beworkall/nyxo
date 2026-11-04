package com.beworkall.nyxo

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beworkall.nyxo.data.AppEntry
import com.beworkall.nyxo.data.AppRepository
import com.beworkall.nyxo.data.Prefs
import com.beworkall.nyxo.radar.PrivacyRadar
import com.beworkall.nyxo.radar.RadarResult
import com.beworkall.nyxo.system.Actions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Home, Drawer, Radar, Settings, Hidden }

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()

    val prefs = Prefs(app)

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var radar by mutableStateOf<Map<String, RadarResult>>(emptyMap())
        private set
    var scanning by mutableStateOf(false)
        private set
    var isDefaultHome by mutableStateOf(true)
        private set
    var resumeTick by mutableIntStateOf(0)
        private set

    var screen by mutableStateOf(Screen.Home)
    var menuApp by mutableStateOf<AppEntry?>(null)
    var radarDetail by mutableStateOf<RadarResult?>(null)
    var drawerFocusSearch by mutableStateOf(false)

    private var refreshJob: Job? = null

    init {
        refresh(immediate = true)
    }

    fun onResume() {
        isDefaultHome = Actions.isDefaultHome(ctx)
        resumeTick++
        refresh()
    }

    fun refresh(immediate: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (!immediate && apps.isNotEmpty()) delay(250)
            val list = withContext(Dispatchers.IO) { AppRepository.load(ctx) }
            apps = list
            seedIfNeeded(list)
            scanning = true
            try {
                radar = withContext(Dispatchers.Default) { PrivacyRadar.scan(ctx, list) }
            } finally {
                scanning = false
            }
        }
    }

    // ---------------------------------------------------------------- navigation

    fun onHomePressed() {
        menuApp = null
        radarDetail = null
        screen = Screen.Home
    }

    fun back() {
        when {
            radarDetail != null -> radarDetail = null
            menuApp != null -> menuApp = null
            screen == Screen.Hidden -> screen = Screen.Drawer
            screen != Screen.Home -> screen = Screen.Home
        }
    }

    fun openDrawer(focusSearch: Boolean = false) {
        drawerFocusSearch = focusSearch
        screen = Screen.Drawer
    }

    /** After leaving for another app, come back to a clean home screen. */
    fun returnHomeSoon() {
        viewModelScope.launch {
            delay(350)
            menuApp = null
            radarDetail = null
            if (screen != Screen.Settings) screen = Screen.Home
        }
    }

    fun launch(app: AppEntry) {
        if (Actions.launch(ctx, app)) returnHomeSoon()
    }

    fun appByKey(key: String): AppEntry? = if (key.isEmpty()) null else apps.firstOrNull { it.key == key }

    // ---------------------------------------------------------------- placement

    fun isOnHome(app: AppEntry) = app.key in prefs.home.value

    fun toggleHome(app: AppEntry) {
        prefs.home.value = if (isOnHome(app)) prefs.home.value - app.key else prefs.home.value + app.key
    }

    fun isInDock(app: AppEntry) = app.key in prefs.dock.value

    /** Returns false when the dock is already full. */
    fun toggleDock(app: AppEntry): Boolean {
        if (isInDock(app)) {
            prefs.dock.value = prefs.dock.value - app.key
            return true
        }
        if (prefs.dock.value.size >= MAX_DOCK) return false
        prefs.dock.value = prefs.dock.value + app.key
        return true
    }

    fun isHidden(app: AppEntry) = app.key in prefs.hidden.value

    fun toggleHidden(app: AppEntry) {
        if (isHidden(app)) {
            prefs.hidden.value = prefs.hidden.value - app.key
        } else {
            prefs.hidden.value = prefs.hidden.value + app.key
            prefs.home.value = prefs.home.value - app.key
            prefs.dock.value = prefs.dock.value - app.key
        }
    }

    // ---------------------------------------------------------------- first run

    private fun seedIfNeeded(list: List<AppEntry>) {
        if (prefs.seeded.value || list.isEmpty()) return
        val pm = ctx.packageManager

        @Suppress("DEPRECATION")
        fun pkgFor(intent: Intent): String? =
            runCatching { pm.queryIntentActivities(intent, 0).firstOrNull()?.activityInfo?.packageName }.getOrNull()

        fun appFor(pkg: String?): AppEntry? = if (pkg == null) null else list.firstOrNull { it.pkg == pkg }

        val dock = listOfNotNull(
            appFor(pkgFor(Intent(Intent.ACTION_DIAL))),
            appFor(pkgFor(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING))),
            appFor(pkgFor(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))),
            appFor(pkgFor(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))),
        ).distinctBy { it.key }

        val home = listOfNotNull(
            appFor(pkgFor(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY))),
            appFor(pkgFor(Intent(AlarmClock.ACTION_SHOW_ALARMS))),
            appFor(pkgFor(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))),
            appFor("com.android.vending"),
            appFor(pkgFor(Intent(Settings.ACTION_SETTINGS))),
        ).distinctBy { it.key }.filter { h -> dock.none { it.key == h.key } }

        prefs.dock.value = dock.map { it.key }
        prefs.home.value = home.map { it.key }
        prefs.seeded.value = true
    }

    companion object {
        const val MAX_DOCK = 5
    }
}

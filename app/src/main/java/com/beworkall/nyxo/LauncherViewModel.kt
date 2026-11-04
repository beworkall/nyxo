package com.beworkall.nyxo

import android.app.Application
import android.graphics.Rect
import android.view.View
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beworkall.nyxo.data.AppEntry
import com.beworkall.nyxo.data.AppStack
import com.beworkall.nyxo.data.AppRepository
import com.beworkall.nyxo.data.Prefs
import com.beworkall.nyxo.data.STACK_PREFIX
import com.beworkall.nyxo.radar.PrivacyRadar
import com.beworkall.nyxo.radar.RadarResult
import com.beworkall.nyxo.system.Sfx
import com.beworkall.nyxo.system.Sound
import com.beworkall.nyxo.system.Actions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screens inside the home app. Settings live in the separate Nyxo Settings app. */
enum class Screen { Home, Drawer, Radar, Hidden }

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()

    val prefs = Prefs.get(app)

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

    // Real back stack: Back returns to where you came from, Home clears it.
    private val backStack = mutableStateListOf<Screen>()

    var screen: Screen
        get() = backStack.lastOrNull() ?: Screen.Home
        set(value) {
            if (value == Screen.Home) {
                backStack.clear()
                return
            }
            val existing = backStack.indexOf(value)
            if (existing >= 0) {
                while (backStack.size > existing + 1) backStack.removeAt(backStack.lastIndex)
            } else {
                if (value == Screen.Drawer) Sound.play(Sfx.OPEN)
                backStack.add(value)
            }
        }

    // Phone tilt for glass highlights, -1..1
    var tiltX by mutableFloatStateOf(0f)
    var tiltY by mutableFloatStateOf(0f)

    // Privacy Radar memory
    var changes by mutableStateOf<Map<String, Set<String>>>(emptyMap())
        private set
    var newApps by mutableStateOf<List<RadarResult>>(emptyList())
        private set
    var menuApp by mutableStateOf<AppEntry?>(null)
    var radarDetail by mutableStateOf<RadarResult?>(null)
    var drawerFocusSearch by mutableStateOf(false)
    var stackPickerFor by mutableStateOf<AppEntry?>(null)
    var editStackId by mutableStateOf<String?>(null)
    var openStackId by mutableStateOf<String?>(null)

    private var refreshJob: Job? = null
    private var lastScan = 0L
    private var dirty = true

    init {
        Sound.init(app)
        refresh(immediate = true)
    }

    /** Cheap on every resume; the full rescan only runs when something changed or 15 minutes passed. */
    fun onResume() {
        isDefaultHome = Actions.isDefaultHome(ctx)
        resumeTick++
        if (dirty || System.currentTimeMillis() - lastScan > RESCAN_MS) refresh()
    }

    /** Called when an app is installed, updated or removed. */
    fun markDirty() {
        dirty = true
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
                val scanned = withContext(Dispatchers.Default) { PrivacyRadar.scan(ctx, list) }
                radar = scanned
                compareWithMemory(scanned)
                lastScan = System.currentTimeMillis()
                dirty = false
            } finally {
                scanning = false
            }
        }
    }

    // ---------------------------------------------------------------- navigation

    fun goBackOrHome(): Boolean {
        if (backStack.isEmpty()) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun onHomePressed() {
        stackPickerFor = null
        editStackId = null
        openStackId = null
        menuApp = null
        radarDetail = null
        screen = Screen.Home
    }

    fun back() {
        when {
            stackPickerFor != null -> stackPickerFor = null
            editStackId != null -> editStackId = null
            openStackId != null -> openStackId = null
            radarDetail != null -> radarDetail = null
            menuApp != null -> menuApp = null
            else -> goBackOrHome()
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
            openStackId = null
            screen = Screen.Home
        }
    }

    /** Opens [app]. With [source] set, it zooms out of the icon like the stock launcher. */
    fun launch(app: AppEntry, sourceView: View? = null, source: Rect? = null) {
        val context = sourceView?.context ?: ctx
        if (Actions.launch(context, app, if (prefs.motion.value) sourceView else null, source)) {
            Sound.launch()
            returnHomeSoon()
        }
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
            saveStacks(stacks.map { it.copy(keys = it.keys - app.key) })
            cleanEmptyStacks()
        }
    }

    // ---------------------------------------------------------------- radar memory

    private fun decodeSeen(): Map<String, Set<String>> {
        val out = HashMap<String, Set<String>>()
        for (line in prefs.radarSeen.value) {
            val parts = line.split('\u0001')
            if (parts.isEmpty() || parts[0].isBlank()) continue
            out[parts[0]] = parts.drop(1).filter { it.isNotBlank() }.toSet()
        }
        return out
    }

    private fun saveSeen(map: Map<String, Set<String>>) {
        prefs.radarSeen.value = map.entries.sortedBy { it.key }.map { (pkg, titles) ->
            (listOf(pkg) + titles.sorted()).joinToString("\u0001")
        }
    }

    private fun titlesOf(r: RadarResult): Set<String> = r.findings.map { it.title }.toSet()

    /**
     * Compares this scan with what the user last saw.
     * New permissions on known apps become "changes"; brand-new risky apps become home cards.
     */
    private fun compareWithMemory(scanned: Map<String, RadarResult>) {
        val seen = decodeSeen().toMutableMap()
        if (!prefs.radarSeenReady.value) {
            // First scan ever: remember everything as the baseline.
            for ((pkg, r) in scanned) seen[pkg] = titlesOf(r)
            saveSeen(seen)
            prefs.radarSeenReady.value = true
            changes = emptyMap()
            newApps = emptyList()
            return
        }
        val changed = HashMap<String, Set<String>>()
        val fresh = ArrayList<RadarResult>()
        var memoryChanged = false
        for ((pkg, r) in scanned) {
            val known = seen[pkg]
            val titles = titlesOf(r)
            if (known == null) {
                if (r.hasIcon && r.findings.isNotEmpty()) {
                    fresh.add(r)
                } else {
                    seen[pkg] = titles
                    memoryChanged = true
                }
            } else {
                val added = titles - known
                if (added.isNotEmpty()) changed[pkg] = added
                // Permissions that were taken away are simply forgotten.
                val kept = known intersect titles
                if (kept.size != known.size) {
                    seen[pkg] = kept
                    memoryChanged = true
                }
            }
        }
        // Forget uninstalled apps.
        val gone = seen.keys - scanned.keys
        if (gone.isNotEmpty()) {
            gone.forEach { seen.remove(it) }
            memoryChanged = true
        }
        if (memoryChanged) saveSeen(seen)
        changes = changed
        newApps = fresh.sortedBy { it.label.lowercase() }
    }

    /** The user has looked at this app; stop flagging what it can currently do. */
    fun markSeen(pkg: String) {
        val r = radar[pkg] ?: return
        val seen = decodeSeen().toMutableMap()
        seen[pkg] = titlesOf(r)
        saveSeen(seen)
        changes = changes - pkg
        newApps = newApps.filter { it.pkg != pkg }
    }

    fun markAllSeen() {
        val seen = decodeSeen().toMutableMap()
        for ((pkg, r) in radar) seen[pkg] = titlesOf(r)
        saveSeen(seen)
        changes = emptyMap()
        newApps = emptyList()
    }

    // ---------------------------------------------------------------- stacks

    val stacks: List<AppStack> get() = prefs.stacks.value.mapNotNull { AppStack.decode(it) }

    fun stackById(id: String): AppStack? = stacks.firstOrNull { it.id == id }

    fun stackOf(app: AppEntry): AppStack? = stacks.firstOrNull { app.key in it.keys }

    private fun saveStacks(list: List<AppStack>) {
        prefs.stacks.value = list.map { it.encode() }
    }

    fun createStack(name: String, app: AppEntry) {
        val id = java.lang.Long.toString(System.currentTimeMillis(), 36)
        val stack = AppStack(id, name.trim().ifEmpty { "Stack" }, listOf(app.key))
        saveStacks(stacks.map { it.copy(keys = it.keys - app.key) }.filter { it.keys.isNotEmpty() } + stack)
        val home = prefs.home.value
        val index = home.indexOf(app.key)
        prefs.home.value = if (index >= 0) {
            home.toMutableList().also { it[index] = STACK_PREFIX + id }
        } else {
            home + (STACK_PREFIX + id)
        }
        prefs.dock.value = prefs.dock.value - app.key
        cleanEmptyStacks()
    }

    fun addToStack(stackId: String, app: AppEntry) {
        saveStacks(stacks.map { s ->
            when {
                s.id == stackId && app.key !in s.keys -> s.copy(keys = s.keys + app.key)
                s.id != stackId -> s.copy(keys = s.keys - app.key)
                else -> s
            }
        })
        prefs.home.value = prefs.home.value - app.key
        prefs.dock.value = prefs.dock.value - app.key
        cleanEmptyStacks()
    }

    fun removeFromStack(stackId: String, key: String) {
        saveStacks(stacks.map { if (it.id == stackId) it.copy(keys = it.keys - key) else it })
        cleanEmptyStacks()
    }

    fun renameStack(stackId: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        saveStacks(stacks.map { if (it.id == stackId) it.copy(name = clean) else it })
    }

    fun deleteStack(stackId: String) {
        saveStacks(stacks.filter { it.id != stackId })
        prefs.home.value = prefs.home.value - (STACK_PREFIX + stackId)
        if (editStackId == stackId) editStackId = null
        if (openStackId == stackId) openStackId = null
    }

    /** Stacks with no apps left disappear, and so do their home entries. */
    private fun cleanEmptyStacks() {
        val empty = stacks.filter { it.keys.isEmpty() }.map { it.id }.toSet()
        if (empty.isEmpty()) return
        saveStacks(stacks.filter { it.id !in empty })
        prefs.home.value = prefs.home.value.filter { k -> !(k.startsWith(STACK_PREFIX) && k.removePrefix(STACK_PREFIX) in empty) }
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
        private const val RESCAN_MS = 15 * 60 * 1000L
    }
}

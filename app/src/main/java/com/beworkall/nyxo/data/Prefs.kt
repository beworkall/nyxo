package com.beworkall.nyxo.data

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/** A single saved setting that is also Compose state, so the UI updates the moment it changes. */
class Pref<T>(
    private val sp: SharedPreferences,
    private val key: String,
    default: T,
    read: (SharedPreferences, String, T) -> T,
    private val write: (SharedPreferences.Editor, String, T) -> Unit,
    private val durable: Boolean = false,
) {
    private val state: MutableState<T> = mutableStateOf(read(sp, key, default))

    var value: T
        get() = state.value
        set(v) {
            if (v == state.value) return
            state.value = v
            val editor = sp.edit()
            write(editor, key, v)
            // Setup progress is written straight to disk: Android may restart the home app right after.
            if (durable) editor.commit() else editor.apply()
        }
}

/**
 * One shared instance per process, so the home screen and the separate Nyxo Settings
 * app see every change instantly.
 */
class Prefs private constructor(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("nyxo", Context.MODE_PRIVATE)

    private fun bool(k: String, d: Boolean, durable: Boolean = false) = Pref<Boolean>(sp, k, d,
        { s, key, def -> s.getBoolean(key, def) }, { e, key, v -> e.putBoolean(key, v) }, durable)

    private fun int(k: String, d: Int, durable: Boolean = false) = Pref<Int>(sp, k, d,
        { s, key, def -> s.getInt(key, def) }, { e, key, v -> e.putInt(key, v) }, durable)

    private fun float(k: String, d: Float) = Pref<Float>(sp, k, d,
        { s, key, def -> s.getFloat(key, def) }, { e, key, v -> e.putFloat(key, v) })

    private fun string(k: String, d: String) = Pref<String>(sp, k, d,
        { s, key, def -> s.getString(key, def) ?: def }, { e, key, v -> e.putString(key, v) })

    private fun list(k: String) = Pref<List<String>>(sp, k, emptyList(),
        { s, key, _ -> (s.getString(key, "") ?: "").split('\n').filter { it.isNotBlank() } },
        { e, key, v -> e.putString(key, v.joinToString("\n")) })

    // Look
    val gridLayout = bool("grid_layout_v2", false, durable = true) // false = one-handed list
    val leftHanded = bool("left_handed", false, durable = true)
    val columns = int("columns", 4)
    val radarDots = bool("radar_dots", true, durable = true)
    val clock24 = bool("clock_24", DateFormat.is24HourFormat(context))
    val clockStyle = string("clock_style", "thin") // thin, bold, stacked
    val accent = int("accent", 0)
    val accentFromTheme = bool("accent_from_theme", true)

    // Theme: wallpaper pack + panel style
    val themePack = string("theme_pack", "amoled")
    val panelStyle = string("panel_style", "solid") // solid, glass, hardglass, water
    val dim = float("dim_v3", 0.30f)
    val tiltLight = bool("tilt_light", true)

    // Sound and motion
    val sounds = bool("sounds", true)
    val soundVolume = float("sound_volume", 0.6f)
    val motion = bool("motion", true)

    // Drawer and search
    val autoKeyboard = bool("auto_keyboard_v2", false) // keyboard on swipe up; search gestures always open it
    val searchContacts = bool("search_contacts", false)

    // Gestures: "search"/"drawer"/"none", or an app key for left/right
    val doubleTap = string("double_tap_v2", "none")
    val swipeDown = string("swipe_down_v2", "search")
    val swipeLeft = string("swipe_left", "")
    val swipeRight = string("swipe_right", "")
    val longPressSettings = bool("long_press_settings", true)

    // App placement, stored as "package/activity" keys
    val home = list("home")
    val dock = list("dock")
    val hidden = list("hidden")
    val seeded = bool("seeded", false)
    val stacks = list("stacks")

    // Widgets, encoded by WidgetSpec
    val widgets = list("widgets")
    val notes = string("notes", "")

    // Privacy Radar memory: what each app could do the last time you looked
    val radarSeen = list("radar_seen")
    val radarSeenReady = bool("radar_seen_ready", false)

    // First-run setup
    val onboarded = bool("onboarded_v2", false, durable = true)
    val onboardStep = int("onboard_step", 0, durable = true)

    companion object {
        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
    }
}

package com.beworkall.nyxo.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Saves your whole Nyxo setup to one file you pick (Downloads, Drive, anywhere) and loads it back.
 * Uses Android's file picker, so Nyxo needs no storage permission.
 */
object Backup {
    private const val FORMAT = 1

    // Device-specific or one-time values that shouldn't move to another phone.
    private val SKIP = setOf("onboard_step", "radar_seen", "radar_seen_ready", "seeded", "breaker_counts")

    suspend fun export(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = Prefs.get(context)
            val values = JSONObject()
            for ((k, v) in prefs.sp.all) {
                if (k in SKIP || v == null) continue
                val entry = JSONObject()
                when (v) {
                    is Boolean -> entry.put("t", "b").put("v", v)
                    is Int -> entry.put("t", "i").put("v", v)
                    is Long -> entry.put("t", "l").put("v", v)
                    is Float -> entry.put("t", "f").put("v", v.toDouble())
                    is String -> entry.put("t", "s").put("v", if (k == "widgets") portableWidgets(v) else v)
                    else -> continue
                }
                values.put(k, entry)
            }
            val root = JSONObject().put("nyxo_backup", FORMAT).put("prefs", values)
            val photo = Wallpapers.customFile(context)
            if (photo.exists() && photo.length() < 12L * 1024 * 1024) {
                root.put("custom_wallpaper", Base64.encodeToString(photo.readBytes(), Base64.NO_WRAP))
            }
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString().toByteArray()) } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    /** Returns null on success, or a short reason it failed. */
    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull() ?: return@withContext "Couldn't open that file"
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return@withContext "That isn't a Nyxo backup"
        if (!root.has("nyxo_backup")) return@withContext "That isn't a Nyxo backup"
        val values = root.optJSONObject("prefs") ?: return@withContext "The backup is empty"

        val prefs = Prefs.get(context)
        val editor = prefs.sp.edit()
        for (k in values.keys()) {
            if (k in SKIP) continue
            val e = values.optJSONObject(k) ?: continue
            when (e.optString("t")) {
                "b" -> editor.putBoolean(k, e.optBoolean("v"))
                "i" -> editor.putInt(k, e.optInt("v"))
                "l" -> editor.putLong(k, e.optLong("v"))
                "f" -> editor.putFloat(k, e.optDouble("v").toFloat())
                "s" -> editor.putString(k, e.optString("v"))
            }
        }
        // Setup was done on the old phone; don't show it again.
        editor.putBoolean("onboarded_v2", true)
        editor.commit()

        val photo = root.optString("custom_wallpaper", "")
        if (photo.isNotEmpty()) {
            runCatching { Wallpapers.customFile(context).writeBytes(Base64.decode(photo, Base64.NO_WRAP)) }
            Wallpapers.forget()
        }
        withContext(Dispatchers.Main) { prefs.reloadAll() }
        null
    }

    /** App widgets are tied to this phone, so only Nyxo's own widgets go into the backup. */
    private fun portableWidgets(raw: String): String =
        raw.split('\n').filter { line -> WidgetSpec.decode(line)?.kind?.let { it != "app" } == true }.joinToString("\n")
}

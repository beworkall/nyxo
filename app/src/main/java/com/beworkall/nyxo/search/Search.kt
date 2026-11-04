package com.beworkall.nyxo.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import com.beworkall.nyxo.data.AppEntry

data class Contact(val id: Long, val lookup: String, val name: String)

data class SettingShortcut(val label: String, val action: String, val keywords: String)

private val SETTINGS = listOf(
    SettingShortcut("Wi-Fi", Settings.ACTION_WIFI_SETTINGS, "wifi wi-fi internet network wireless"),
    SettingShortcut("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, "bluetooth bt pair headphones"),
    SettingShortcut("Mobile data usage", Settings.ACTION_DATA_USAGE_SETTINGS, "data usage mobile sim"),
    SettingShortcut("Airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS, "airplane flight mode"),
    SettingShortcut("Display", Settings.ACTION_DISPLAY_SETTINGS, "display brightness screen dark mode font"),
    SettingShortcut("Sound", Settings.ACTION_SOUND_SETTINGS, "sound volume ringtone vibrate"),
    SettingShortcut("Battery saver", Settings.ACTION_BATTERY_SAVER_SETTINGS, "battery power saver"),
    SettingShortcut("Apps", Settings.ACTION_APPLICATION_SETTINGS, "apps applications manage"),
    SettingShortcut("Default apps", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, "default apps launcher home browser"),
    SettingShortcut("Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS, "location gps"),
    SettingShortcut("Privacy", Settings.ACTION_PRIVACY_SETTINGS, "privacy permissions"),
    SettingShortcut("Security", Settings.ACTION_SECURITY_SETTINGS, "security lock screen fingerprint password"),
    SettingShortcut("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS, "accessibility"),
    SettingShortcut("Notification access", Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, "notification access listener"),
    SettingShortcut("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "storage space memory"),
    SettingShortcut("Date and time", Settings.ACTION_DATE_SETTINGS, "date time clock timezone"),
    SettingShortcut("NFC", Settings.ACTION_NFC_SETTINGS, "nfc tap pay"),
    SettingShortcut("Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "developer options usb debugging adb"),
    SettingShortcut("All settings", Settings.ACTION_SETTINGS, "settings system"),
)

object Search {

    private fun score(label: String, q: String): Int? {
        val l = label.lowercase()
        if (l == q) return 0
        if (l.startsWith(q)) return 1
        val words = l.split(' ', '-', '_', '.', ':').filter { it.isNotEmpty() }
        if (words.any { it.startsWith(q) }) return 2
        val initials = words.joinToString("") { it.take(1) }
        if (q.length > 1 && initials.startsWith(q)) return 3
        if (l.contains(q)) return 4
        if (l.replace(" ", "").contains(q.replace(" ", ""))) return 5
        return null
    }

    fun apps(all: List<AppEntry>, query: String, limit: Int = 8): List<AppEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<AppEntry, Int>>()
        for (a in all) {
            val s = score(a.label, q) ?: continue
            scored.add(a to s)
        }
        scored.sortWith(compareBy<Pair<AppEntry, Int>>({ it.second }, { it.first.label.lowercase() }))
        return scored.take(limit).map { it.first }
    }

    fun settings(query: String, limit: Int = 4): List<SettingShortcut> {
        val words = query.trim().lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return SETTINGS.filter { s ->
            val hay = s.label.lowercase() + " " + s.keywords
            words.all { w -> hay.contains(w) }
        }.take(limit)
    }

    fun contacts(context: Context, query: String, limit: Int = 5): List<Contact> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val out = ArrayList<Contact>()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q))
        val projection = arrayOf("_id", "lookup", "display_name")
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    out.add(Contact(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: ""))
                }
            }
        }
        return out
    }
}

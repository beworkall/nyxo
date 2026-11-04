package com.beworkall.nyxo.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context

/**
 * A widget on the home screen.
 * kind = "app" for a real Android widget (id = its AppWidget id),
 * or one of Nyxo's own: "battery", "volume", "privacy", "notes".
 */
data class WidgetSpec(val kind: String, val id: Int, val heightDp: Int, val group: Int = 0) {
    val key: String get() = "$kind:$id"

    /** group > 0: shown in a swipeable stack with the other widgets of the same group. */
    fun encode(): String = if (group > 0) "$kind:$id:$heightDp:$group" else "$kind:$id:$heightDp"

    companion object {
        fun decode(raw: String): WidgetSpec? {
            val p = raw.split(':')
            if (p.size != 3 && p.size != 4) return null
            val id = p[1].toIntOrNull() ?: return null
            val h = p[2].toIntOrNull() ?: return null
            val g = if (p.size == 4) p[3].toIntOrNull() ?: 0 else 0
            return WidgetSpec(p[0], id, h, g)
        }

        val BUILT_IN = listOf(
            Triple("battery", "Battery", "Charge level with a ring"),
            Triple("volume", "Volume", "Media volume slider. No permissions needed"),
            Triple("privacy", "Privacy score", "Your phone's privacy score at a glance"),
            Triple("notes", "Quick note", "A note that stays on your home screen"),
        )

        const val MAX_IN_STACK = 4

        fun defaultHeight(kind: String): Int = when (kind) {
            "battery" -> 96
            "volume" -> 88
            "privacy" -> 96
            "notes" -> 130
            else -> 160
        }
    }
}

object WidgetHosting {
    private const val HOST_ID = 4207
    @Volatile
    private var host: AppWidgetHost? = null

    fun host(context: Context): AppWidgetHost =
        host ?: synchronized(this) {
            host ?: AppWidgetHost(context.applicationContext, HOST_ID).also { host = it }
        }

    fun manager(context: Context): AppWidgetManager = AppWidgetManager.getInstance(context.applicationContext)
}

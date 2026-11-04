package com.beworkall.nyxo.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(
    val label: String,
    val pkg: String,
    val cls: String,
    val isSystem: Boolean,
) {
    val key: String get() = "$pkg/$cls"
    val component: ComponentName get() = ComponentName(pkg, cls)
}

/** Section letter used by the drawer scroller and the thumb alphabet. */
fun letterOf(label: String): Char {
    val c = label.firstOrNull { it.isLetterOrDigit() } ?: return '#'
    return if (c.isLetter()) c.uppercaseChar() else '#'
}

const val STACK_PREFIX = "stack:"

/** A named group of apps shown as one entry on the home screen. */
data class AppStack(val id: String, val name: String, val keys: List<String>) {
    fun encode(): String = (listOf(id, name.replace('\t', ' ').replace('\n', ' ')) + keys).joinToString("\t")

    companion object {
        fun decode(raw: String): AppStack? {
            val parts = raw.split('\t')
            if (parts.size < 2 || parts[0].isBlank()) return null
            return AppStack(parts[0], parts[1], parts.drop(2).filter { it.isNotBlank() })
        }
    }
}

object AppRepository {
    /** Every app with a launcher icon, sorted A to Z. */
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val infos = pm.queryIntentActivities(intent, 0)
        return infos.mapNotNull { ri ->
            val ai = ri.activityInfo ?: return@mapNotNull null
            // Hide the home screen itself, but keep the separate Nyxo Settings app in the list.
            if (ai.packageName == context.packageName && ai.name.endsWith(".MainActivity")) return@mapNotNull null
            val flags = ai.applicationInfo?.flags ?: 0
            val label = ri.loadLabel(pm).toString().trim().ifEmpty { ai.packageName }
            AppEntry(label, ai.packageName, ai.name, (flags and ApplicationInfo.FLAG_SYSTEM) != 0)
        }
            .distinctBy { it.key }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}

object IconCache {
    private val cache = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private fun keyOf(app: AppEntry, px: Int) = "${app.key}@$px"

    fun peek(app: AppEntry, px: Int): ImageBitmap? = cache.get(keyOf(app, px))

    suspend fun load(context: Context, app: AppEntry, px: Int): ImageBitmap? {
        peek(app, px)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getActivityIcon(app.component).toBitmap(px, px).asImageBitmap()
            }.getOrNull()
        }
        if (bitmap != null) cache.put(keyOf(app, px), bitmap)
        return bitmap
    }

    fun clear() = cache.evictAll()
}

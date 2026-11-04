package com.beworkall.nyxo.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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

    // Current icon settings. Changing them clears the cache and bumps [version] so icons redraw.
    @Volatile private var pack = ""
    @Volatile private var themed = false
    @Volatile private var tint = 0
    var version by mutableIntStateOf(0)
        private set

    fun configure(packPkg: String, themedIcons: Boolean, accentArgb: Int) {
        val accentMatters = themedIcons
        if (packPkg == pack && themedIcons == themed && (!accentMatters || accentArgb == tint)) return
        if (packPkg != pack) IconPacks.forget()
        pack = packPkg
        themed = themedIcons
        tint = accentArgb
        cache.evictAll()
        version++
    }

    private fun keyOf(app: AppEntry, px: Int) = "${app.key}@$px"

    fun peek(app: AppEntry, px: Int): ImageBitmap? = cache.get(keyOf(app, px))

    suspend fun load(context: Context, app: AppEntry, px: Int): ImageBitmap? {
        peek(app, px)?.let { return it }
        val packPkg = pack
        val themedNow = themed
        val tintNow = tint
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val fromPack = if (packPkg.isNotEmpty()) IconPacks.icon(context, packPkg, app) else null
                val drawable = fromPack ?: systemIcon(context, app)
                val bmp = if (themedNow && fromPack == null) ThemedIcons.render(drawable, px, tintNow) else drawable.toBitmap(px, px)
                bmp.asImageBitmap()
            }.getOrNull()
        }
        if (bitmap != null) cache.put(keyOf(app, px), bitmap)
        return bitmap
    }

    /** Same source One UI Home uses, so every app shows its real launcher icon. */
    private fun systemIcon(context: Context, app: AppEntry): Drawable {
        val la = context.getSystemService(LauncherApps::class.java)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component)
        return runCatching { la?.resolveActivity(intent, Process.myUserHandle())?.getIcon(context.resources.displayMetrics.densityDpi) }.getOrNull()
            ?: context.packageManager.getActivityIcon(app.component)
    }

    fun clear() = cache.evictAll()
}

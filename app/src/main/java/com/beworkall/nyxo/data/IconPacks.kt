package com.beworkall.nyxo.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class IconPackInfo(val pkg: String, val label: String)

/**
 * Standard icon packs from the Play Store (the ADW / Nova format almost every pack ships).
 * A pack is an app with an appfilter.xml that maps each app's activity to one of its drawables.
 */
object IconPacks {
    private val ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme",
        "com.anddoes.launcher.THEME",
    )

    private class Loaded(val res: Resources, val map: Map<String, String>)

    @Volatile private var loadedPkg: String? = null
    @Volatile private var loaded: Loaded? = null

    fun installed(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val seen = LinkedHashMap<String, IconPackInfo>()
        for (action in ACTIONS) {
            @Suppress("DEPRECATION")
            val found = runCatching { pm.queryIntentActivities(Intent(action), 0) }.getOrDefault(emptyList())
            for (ri in found) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg !in seen) seen[pkg] = IconPackInfo(pkg, ri.loadLabel(pm).toString())
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    /** The pack's icon for [app], or null if the pack doesn't cover it. */
    @SuppressLint("DiscouragedApi")
    fun icon(context: Context, packPkg: String, app: AppEntry): Drawable? {
        val pack = load(context, packPkg) ?: return null
        val name = pack.map["ComponentInfo{${app.pkg}/${app.cls}}"]
            ?: pack.map.entries.firstOrNull { it.key.startsWith("ComponentInfo{${app.pkg}/") }?.value
            ?: return null
        val id = pack.res.getIdentifier(name, "drawable", packPkg)
        if (id == 0) return null
        return runCatching {
            pack.res.getDrawableForDensity(id, context.resources.displayMetrics.densityDpi, null)
        }.getOrNull()
    }

    fun forget() {
        loadedPkg = null
        loaded = null
    }

    @SuppressLint("DiscouragedApi")
    @Synchronized
    private fun load(context: Context, pkg: String): Loaded? {
        if (loadedPkg == pkg) return loaded
        val result = runCatching {
            val res = context.packageManager.getResourcesForApplication(pkg)
            val map = HashMap<String, String>()
            val xmlId = res.getIdentifier("appfilter", "xml", pkg)
            val parser: XmlPullParser = if (xmlId != 0) {
                res.getXml(xmlId)
            } else {
                XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(res.assets.open("appfilter.xml"), "UTF-8")
                }
            }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (component != null && drawable != null) map[component] = drawable
                }
                event = parser.next()
            }
            Loaded(res, map)
        }.getOrNull()
        loadedPkg = pkg
        loaded = result
        return result
    }
}

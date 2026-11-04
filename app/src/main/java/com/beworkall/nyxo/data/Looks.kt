package com.beworkall.nyxo.data

import org.json.JSONObject

/** A saved look from Theme Studio: everything that changes how Nyxo looks, nothing about your apps. */
data class SavedLook(
    val id: String,
    val name: String,
    val pack: String,
    val style: String,
    val accentMode: String,
    val accent: Int,
    val dim: Float,
    val clock: String,
    val font: String,
    val themedIcons: Boolean,
    val iconPack: String,
) {
    fun encode(): String = JSONObject()
        .put("id", id).put("name", name).put("pack", pack).put("style", style)
        .put("accentMode", accentMode).put("accent", accent).put("dim", dim.toDouble())
        .put("clock", clock).put("font", font).put("themed", themedIcons).put("iconPack", iconPack)
        .toString()

    fun applyTo(p: Prefs) {
        p.themePack.value = pack
        p.panelStyle.value = style
        p.accentMode.value = accentMode
        p.accent.value = accent
        p.dim.value = dim
        p.clockStyle.value = clock
        p.font.value = font
        p.iconThemed.value = themedIcons
        p.iconPack.value = iconPack
    }

    companion object {
        fun decode(raw: String): SavedLook? = runCatching {
            val o = JSONObject(raw)
            SavedLook(
                o.getString("id"), o.getString("name"), o.optString("pack", "amoled"), o.optString("style", "solid"),
                o.optString("accentMode", "theme"), o.optInt("accent", 0), o.optDouble("dim", 0.3).toFloat(),
                o.optString("clock", "thin"), o.optString("font", "default"), o.optBoolean("themed", false),
                o.optString("iconPack", ""),
            )
        }.getOrNull()

        fun fromCurrent(p: Prefs, name: String) = SavedLook(
            id = System.currentTimeMillis().toString(36),
            name = name,
            pack = p.themePack.value,
            style = p.panelStyle.value,
            accentMode = p.accentMode.value,
            accent = p.accent.value,
            dim = p.dim.value,
            clock = p.clockStyle.value,
            font = p.font.value,
            themedIcons = p.iconThemed.value,
            iconPack = p.iconPack.value,
        )
    }
}

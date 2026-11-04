package com.beworkall.nyxo.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Usage breaker and focus mode. Nyxo only counts the times you open an app from Nyxo itself,
 * so this needs no usage-access permission.
 */
object Wellbeing {
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    /** Daily open limit for [key], or null if the app isn't limited. */
    fun limitOf(p: Prefs, key: String): Int? =
        p.breakerApps.value.firstNotNullOfOrNull { line ->
            val parts = line.split('\t')
            if (parts.size == 2 && parts[0] == key) parts[1].toIntOrNull() else null
        }

    fun setLimit(p: Prefs, key: String, limit: Int?) {
        val rest = p.breakerApps.value.filterNot { it.substringBefore('\t') == key }
        p.breakerApps.value = if (limit == null) rest else rest + "$key\t$limit"
    }

    private fun counts(p: Prefs): MutableMap<String, Int> {
        val parts = p.breakerCounts.value.split('|')
        val map = LinkedHashMap<String, Int>()
        if (parts.firstOrNull() != today()) return map
        for (item in parts.drop(1)) {
            val k = item.substringBeforeLast('=', "")
            val n = item.substringAfterLast('=', "").toIntOrNull() ?: continue
            if (k.isNotEmpty()) map[k] = n
        }
        return map
    }

    fun opensToday(p: Prefs, key: String): Int = counts(p)[key] ?: 0

    /** Only limited apps are counted. */
    fun recordOpen(p: Prefs, key: String) {
        if (limitOf(p, key) == null) return
        val map = counts(p)
        map[key] = (map[key] ?: 0) + 1
        p.breakerCounts.value = (listOf(today()) + map.map { "${it.key}=${it.value}" }).joinToString("|")
    }

    fun isFocusHidden(p: Prefs, key: String): Boolean = p.focusOn.value && key in p.focusApps.value
}

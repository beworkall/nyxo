package com.beworkall.nyxo.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/** Your next calendar event in the coming 24 hours, read on the phone. */
object CalendarLine {
    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun next(context: Context): String? = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext null
        runCatching {
            val now = System.currentTimeMillis()
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, now - 10 * 60_000L)
                ContentUris.appendId(it, now + 24 * 3_600_000L)
            }.build()
            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY,
            )
            context.contentResolver.query(uri, projection, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
                var allDay: String? = null
                while (c.moveToNext()) {
                    val title = c.getString(0)?.trim().orEmpty().ifEmpty { "Event" }
                    val begin = c.getLong(1)
                    if (c.getInt(2) == 1) {
                        if (allDay == null) allDay = "Today · $title"
                        continue
                    }
                    val time = DateFormat.getTimeFormat(context).format(Date(begin))
                    return@runCatching if (begin <= now) "Now · $title" else "$time · $title"
                }
                allDay
            }
        }.getOrNull()
    }
}

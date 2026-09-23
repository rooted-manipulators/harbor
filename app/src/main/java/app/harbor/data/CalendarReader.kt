package app.harbor.data

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import app.harbor.domain.CalendarPull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The one read of the phone's calendar. ADR-015.
 *
 * Seven days of instances -- recurring events already expanded by the
 * provider -- from the start of today, handed to [CalendarPull] and forgotten.
 * Titles become block labels, which never leave the device (see WeekBlock).
 *
 * Returns null when the provider refuses, which on a phone with the
 * permission granted means there is no calendar provider at all.
 */
object CalendarReader {

    suspend fun nextWeek(context: Context, today: LocalDate = LocalDate.now()): List<CalendarPull.Event>? =
        withContext(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = today.plusDays(CalendarPull.DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, from)
                ContentUris.appendId(it, to)
            }.build()
            val columns = arrayOf(
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.AVAILABILITY,
            )
            runCatching {
                context.contentResolver.query(uri, columns, null, null, null)?.use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            add(
                                CalendarPull.Event(
                                    start = local(c.getLong(0), zone),
                                    end = local(c.getLong(1), zone),
                                    title = c.getString(2),
                                    allDay = c.getInt(3) != 0,
                                    showsFree = c.getInt(4) == CalendarContract.Instances.AVAILABILITY_FREE,
                                ),
                            )
                        }
                    }
                }
            }.getOrNull()
        }

    private fun local(millis: Long, zone: ZoneId): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
}

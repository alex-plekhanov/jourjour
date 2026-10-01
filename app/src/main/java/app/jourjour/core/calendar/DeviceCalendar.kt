package app.jourjour.core.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * One event as the device's calendar holds it.
 *
 * The two ends are moments, as everything in this app is; [startZone] and [endZone] are
 * what the calendar says they are to be read on, which an airline's booking fills in and
 * most other things leave at wherever the phone was.
 *
 * @param isAllDay a day marked off rather than an hour booked. The provider keeps those
 *   on UTC midnight with no hour worth reading, so [startsAt] says which day and nothing
 *   more.
 */
data class CalendarEvent(
    val title: String,
    val notes: String,
    val place: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val isAllDay: Boolean,
    val startZone: ZoneId?,
    val endZone: ZoneId?,
    /** Which calendar it came off, which is how two events of the same name are told apart. */
    val calendar: String,
)

/**
 * The calendar on the phone, read and never written.
 *
 * Nothing here runs until somebody presses the one button that copies an event, which is
 * also where permission is asked for: a person who never presses it is never asked, and
 * everything else in the app works without it.
 */
object DeviceCalendar {

    const val PERMISSION = Manifest.permission.READ_CALENDAR

    fun Context.canReadCalendar(): Boolean =
        ContextCompat.checkSelfPermission(this, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * What stands in the calendar on [date], read on [zone] — the clock the schedule is
     * being written against, which is what decides where that day begins and ends.
     *
     * Asked of `Instances` rather than of `Events`, since that is the table that has
     * already worked out what a repeating event comes to on a given day.
     *
     * @return the events in the order they happen, or nothing at all where the calendar
     *   cannot be read — a phone with no calendar app, or permission taken back between
     *   the press and the query.
     */
    fun Context.eventsOn(date: LocalDate, zone: ZoneId): List<CalendarEvent> {
        val from = date.atStartOfDay(zone).toInstant()
        val until = date.plusDays(1).atStartOfDay(zone).toInstant()
        // Asked a day wider at either end, because a day marked off is kept on UTC
        // midnight however far from UTC the person is; which of them belong to this date
        // is settled below, where an all-day row is read on its own clock.
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().let {
            ContentUris.appendId(it, from.toEpochMilli() - DAY_MILLIS)
            ContentUris.appendId(it, until.toEpochMilli() + DAY_MILLIS)
            it.build()
        }
        val cursor = runCatching {
            contentResolver.query(uri, PROJECTION, null, null, "${CalendarContract.Instances.BEGIN} ASC")
        }.getOrNull() ?: return emptyList()
        return cursor.use { rows ->
            buildList {
                while (rows.moveToNext()) {
                    val event = rows.read() ?: continue
                    if (event.belongsTo(date, from, until)) add(event)
                }
            }
        }
    }

    /** Whether an event is one of this day's, which an all-day row answers differently. */
    private fun CalendarEvent.belongsTo(date: LocalDate, from: Instant, until: Instant): Boolean =
        if (isAllDay) {
            startsAt.atZone(ZoneOffset.UTC).toLocalDate() == date
        } else {
            endsAt.isAfter(from) && startsAt.isBefore(until)
        }

    private fun Cursor.read(): CalendarEvent? {
        val begin = getLong(BEGIN)
        val end = getLong(END)
        if (end < begin) return null
        // An invitation turned down is not something anybody is about to write into a
        // plan, and leaving it out is the difference between a short list and a long one.
        if (getInt(STATUS) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) return null
        return CalendarEvent(
            title = getString(TITLE).orEmpty().trim(),
            notes = getString(DESCRIPTION).orEmpty().trim(),
            place = getString(LOCATION).orEmpty().trim(),
            startsAt = Instant.ofEpochMilli(begin),
            endsAt = Instant.ofEpochMilli(end),
            isAllDay = getInt(ALL_DAY) != 0,
            startZone = zoneAt(ZONE),
            endZone = zoneAt(END_ZONE),
            calendar = getString(CALENDAR).orEmpty().trim(),
        )
    }

    /** A zone the calendar names, or nothing where it names one nobody has heard of. */
    private fun Cursor.zoneAt(column: Int): ZoneId? =
        getString(column)?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    private val PROJECTION = arrayOf(
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.DESCRIPTION,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.EVENT_TIMEZONE,
        CalendarContract.Instances.EVENT_END_TIMEZONE,
        CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        CalendarContract.Instances.SELF_ATTENDEE_STATUS,
    )

    private const val BEGIN = 0
    private const val END = 1
    private const val TITLE = 2
    private const val DESCRIPTION = 3
    private const val LOCATION = 4
    private const val ALL_DAY = 5
    private const val ZONE = 6
    private const val END_ZONE = 7
    private const val CALENDAR = 8
    private const val STATUS = 9
}

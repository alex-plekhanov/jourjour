package app.jourjour.ui.schedule

import androidx.annotation.StringRes
import app.jourjour.R
import app.jourjour.core.calendar.CalendarEvent
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.schedule.PlacedEvent
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One event as it is being typed.
 *
 * The two ends are held as local date and time apiece, with the zone each is read in
 * beside them, because that is what a person types: the hour on the clock in front of
 * them. What is saved is the moment those add up to. The zones are not typed into here —
 * they are wherever the schedule says the event is — and the form only shows them where
 * the two differ, which is the one case a clock alone would mislead.
 */
data class ScheduleSheet(
    /** Zero while the event has never been saved. */
    val id: Long = 0,
    /**
     * Whether this one started life as a copy of another event rather than as an empty
     * form. Both are unsaved, and only one of them is empty enough to be worth offering
     * the calendar to: a copy already says what it is.
     */
    val isCopy: Boolean = false,
    val title: String = "",
    val notes: String = "",
    /** Notes are folded away until there are some, as they are on the entry form. */
    val isNotesVisible: Boolean = false,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val startZone: ZoneId,
    val endDate: LocalDate,
    val endTime: LocalTime,
    val endZone: ZoneId,
    /**
     * The length as it is being typed, which is two figures either of which may be
     * half-written. The two ends are what the event is; these are a way of saying them.
     */
    val hours: String = "",
    val minutes: String = "",
    /**
     * The event this one begins at the end of, and what that one is called.
     *
     * While it is set the start is not typed into at all: it is that event's end, and a
     * field offering to disagree with it would be offering to break the link.
     */
    val followsId: Long? = null,
    val followsTitle: String = "",
    /** Whether the event itself says where it begins, rather than taking what it finds. */
    val declaresStart: Boolean = false,
    /** Whether it says where it lands, which is what a flight does. */
    val declaresEnd: Boolean = false,
    /**
     * What the event is expected to cost, which is the budget's finer plan for whatever
     * category it names. All three or none: a sum with nothing to file it under says
     * nothing the budget can read.
     */
    val categoryId: Long? = null,
    val amount: String = "",
    val currencyCode: String? = null,
    val isCategorySheetVisible: Boolean = false,
    @param:StringRes val titleError: Int? = null,
    @param:StringRes val endError: Int? = null,
    @param:StringRes val rangeError: Int? = null,
    @param:StringRes val categoryError: Int? = null,
    @param:StringRes val amountError: Int? = null,
    @param:StringRes val currencyError: Int? = null,
) {
    val isNew: Boolean get() = id == 0L

    val startsAt: Instant get() = ZonedDateTime.of(startDate, startTime, startZone).toInstant()
    val endsAt: Instant get() = ZonedDateTime.of(endDate, endTime, endZone).toInstant()

    /** The length as it stands, in minutes, which is what the one field of it shows. */
    val lengthMinutes: Long
        get() = hours.toLongOrNull().orZero() * 60 + minutes.toLongOrNull().orZero()

    /** What the two ends come to, or nothing where the second is not after the first. */
    val duration: Duration?
        get() = Duration.between(startsAt, endsAt).takeIf { !it.isNegative && !it.isZero }

    /** Whether the clocks move under the event, which is when they are worth printing. */
    val crossesZones: Boolean get() = startZone != endZone

    /** Whether the start belongs to the event before it rather than to anybody's typing. */
    val isLinked: Boolean get() = followsId != null

    /**
     * Moving the start moves the end with it and leaves the length alone — a flight an
     * hour late is an hour later, not an hour shorter. Typing an end that is before the
     * start is refused rather than silently swapped, so the length is kept from here.
     */
    fun withStart(date: LocalDate = startDate, time: LocalTime = startTime): ScheduleSheet {
        val carry = duration ?: Duration.ofHours(1)
        val moved = ZonedDateTime.of(date, time, startZone).plus(carry).withZoneSameInstant(endZone)
        return copy(
            startDate = date,
            startTime = time,
            endDate = moved.toLocalDate(),
            endTime = moved.toLocalTime(),
            endError = null,
            rangeError = null,
        )
    }

    /** Moving the end leaves the start where it is; what gives is the length. */
    fun withEnd(date: LocalDate = endDate, time: LocalTime = endTime): ScheduleSheet =
        copy(endDate = date, endTime = time, endError = null, rangeError = null).writtenLength()

    /**
     * Typing the length moves the end, which is the third way round the same three
     * figures. What is typed is kept exactly as typed — a field that rewrote "9" as "09"
     * under the cursor would be unusable — so only the end is worked out from it.
     */
    fun withLength(hours: String = this.hours, minutes: String = this.minutes): ScheduleSheet {
        val wanted = Duration.ofMinutes(hours.toLongOrNull().orZero() * 60 + minutes.toLongOrNull().orZero())
        val moved = ZonedDateTime.of(startDate, startTime, startZone)
            .plus(wanted)
            .withZoneSameInstant(endZone)
        return copy(
            hours = hours,
            minutes = minutes,
            endDate = moved.toLocalDate(),
            endTime = moved.toLocalTime(),
            endError = null,
            rangeError = null,
        )
    }

    /** The length as the two ends now stand, for the fields that show it. */
    fun writtenLength(): ScheduleSheet {
        val length = Duration.between(startsAt, endsAt).coerceAtLeast(Duration.ZERO)
        return copy(
            hours = (length.toMinutes() / 60).toString(),
            minutes = (length.toMinutes() % 60).toString(),
        )
    }

    /** Hands the start over to the event before, which is what a link means. */
    fun following(event: PlacedEvent): ScheduleSheet = copy(
        followsId = event.id,
        followsTitle = event.event.title,
    ).withStart(
        date = event.endsAt.atZone(startZone).toLocalDate(),
        time = event.endsAt.atZone(startZone).toLocalTime(),
    )

    /** Takes the start back. The time stands: it was right a moment ago and still is. */
    fun unfollowed(): ScheduleSheet = copy(followsId = null, followsTitle = "")

    /**
     * The fields filled in from an event on the device's calendar.
     *
     * The moments are taken as they stand, a calendar event being a moment as much as
     * ours is. What is not taken wholesale is the zone: a calendar names one on every
     * event it holds, and copying that over would have an ordinary dinner declaring a
     * move on a trip where nothing moved. It is declared only where it reads differently
     * from the clock the schedule already hands down — which is how a flight booked by an
     * airline brings its two ends over and nothing else brings anything.
     *
     * A day marked off rather than an hour booked has no hour to take, so it is offered
     * at the hour a day starts for as long as an event lasts by default.
     *
     * Any link goes: the times are about to be somebody else's, and a start handed down
     * by the event before it cannot also be the one on the calendar.
     *
     * @param inheritedAt what the schedule reads at a moment, declarations aside.
     */
    fun copiedFrom(
        event: CalendarEvent,
        dayBegins: LocalTime,
        defaultMinutes: Int,
        inheritedAt: (Instant) -> ZoneId,
    ): ScheduleSheet {
        val described = event.described()
        val written = unfollowed().copy(
            title = event.title,
            notes = described,
            isNotesVisible = described.isNotBlank(),
            titleError = null,
            endError = null,
            rangeError = null,
        )
        if (event.isAllDay) {
            return written
                .withStart(time = dayBegins)
                .withLength(
                    hours = (defaultMinutes / MINUTES_IN_HOUR).toString(),
                    minutes = (defaultMinutes % MINUTES_IN_HOUR).toString(),
                )
        }
        val inherited = inheritedAt(event.startsAt)
        val start = event.startZone
            ?.takeIf { it.reads(event.startsAt) != inherited.reads(event.startsAt) }
            ?: inherited
        val end = event.endZone
            ?.takeIf { it.reads(event.endsAt) != start.reads(event.startsAt) }
            ?: start
        val from = event.startsAt.atZone(start)
        val to = event.endsAt.atZone(end)
        return written.copy(
            startDate = from.toLocalDate(),
            startTime = from.toLocalTime(),
            startZone = start,
            declaresStart = start != inherited,
            endDate = to.toLocalDate(),
            endTime = to.toLocalTime(),
            endZone = end,
            declaresEnd = end != start,
        ).writtenLength()
    }

    /**
     * Where the event begins, which moves the moment and not the clock: the hour typed is
     * the hour meant, and saying it is read somewhere else does not make it a different
     * hour. The far end follows unless it has been given a place of its own.
     */
    fun withStartZone(zone: ZoneId): ScheduleSheet = copy(
        startZone = zone,
        declaresStart = true,
        endZone = if (declaresEnd) endZone else zone,
        endError = null,
        rangeError = null,
    ).writtenLength()

    /**
     * Where it lands. The arrival on a boarding pass is local to where it lands, so the
     * clock stands and the length is what gives — unless that would land the flight before
     * it took off, in which case the length is kept and the clock moves instead.
     */
    fun withEndZone(zone: ZoneId): ScheduleSheet {
        val kept = copy(endZone = zone, declaresEnd = true, endError = null, rangeError = null)
        if (kept.duration != null) return kept.writtenLength()
        val carry = duration ?: Duration.ofHours(1)
        val moved = ZonedDateTime.of(startDate, startTime, startZone).plus(carry).withZoneSameInstant(zone)
        return kept.copy(endDate = moved.toLocalDate(), endTime = moved.toLocalTime()).writtenLength()
    }

    /** Back to whatever the schedule hands down at that point. */
    fun withInheritedZones(start: ZoneId, end: ZoneId): ScheduleSheet =
        copy(
            startZone = start,
            endZone = end,
            declaresStart = false,
            declaresEnd = false,
            endError = null,
            rangeError = null,
        ).writtenLength()

    /**
     * Back to whatever the schedule hands down at this event's start, which is what "the
     * clocks do not move here" means.
     *
     * The end goes with it where the end was only echoing the start — an event that said
     * nothing about where it lands was landing wherever it began.
     */
    fun withInheritedStart(inherited: ZoneId): ScheduleSheet = copy(
        startZone = inherited,
        declaresStart = false,
        endZone = if (declaresEnd) endZone else inherited,
        endError = null,
        rangeError = null,
    ).writtenLength()

    /** The same for where it lands: it lands wherever it started, which is the usual case. */
    fun withInheritedEnd(): ScheduleSheet = copy(
        endZone = startZone,
        declaresEnd = false,
        endError = null,
        rangeError = null,
    ).writtenLength()

    /**
     * Where the event is, then what the calendar says about it: the place is short and
     * says which event this is, and a description copied out of a booking rarely is.
     */
    private fun CalendarEvent.described(): String =
        listOf(place, notes).filter { it.isNotBlank() }.joinToString("\n")

    /** What a zone reads at a moment, which is the only thing two of them differ by. */
    private fun ZoneId.reads(at: Instant): Int = rules.getOffset(at).totalSeconds

    companion object {
        private const val MINUTES_IN_HOUR = 60

        /**
         * @param followsTitle what the event this one is tied to is called, if any.
         * @param decimals of the currency the cost is written in, which decides how the
         *   figure is put into the field.
         */
        fun of(placed: PlacedEvent, followsTitle: String = "", decimals: Int = 2) = ScheduleSheet(
            id = placed.id,
            title = placed.event.title,
            notes = placed.event.notes,
            isNotesVisible = placed.event.notes.isNotBlank(),
            startDate = placed.start.toLocalDate(),
            startTime = placed.start.toLocalTime(),
            startZone = placed.startZone,
            endDate = placed.end.toLocalDate(),
            endTime = placed.end.toLocalTime(),
            endZone = placed.endZone,
            followsId = placed.event.followsId,
            followsTitle = followsTitle,
            declaresStart = placed.event.startsInZone != null,
            declaresEnd = placed.event.endsInZone != null,
            categoryId = placed.event.categoryId,
            amount = placed.event.amountMilli
                ?.let { MoneyFormat.formatAmountForInput(Money(it), decimals) }
                .orEmpty(),
            currencyCode = placed.event.currencyCode,
        ).writtenLength()

        /** A new event over the minutes somebody tapped. */
        fun over(startsAt: Instant, durationMinutes: Int, zone: ZoneId): ScheduleSheet {
            val start = startsAt.atZone(zone)
            val end = start.plusMinutes(durationMinutes.toLong())
            return ScheduleSheet(
                title = "",
                startDate = start.toLocalDate(),
                startTime = start.toLocalTime(),
                startZone = zone,
                endDate = end.toLocalDate(),
                endTime = end.toLocalTime(),
                endZone = zone,
            ).writtenLength()
        }
    }
}

/** A checked sheet, or the same sheet with the reasons it was refused written on it. */
sealed interface ScheduleSheetCheck {
    data class Accepted(
        val title: String,
        val notes: String,
        val startsAt: Long,
        val durationMinutes: Int,
        val startsInZone: String?,
        val endsInZone: String?,
        val followsId: Long?,
        val categoryId: Long?,
        val amountMilli: Long?,
        val currencyCode: String?,
    ) : ScheduleSheetCheck

    data class Refused(val sheet: ScheduleSheet) : ScheduleSheetCheck
}

/**
 * @param within the trip's own days, which an event may not be written outside of: a
 * schedule is what happens on the trip, and a hotel booked for the week before is a plan
 * of a different kind.
 */
fun ScheduleSheet.check(within: ClosedRange<Instant>?, decimals: Int = 2): ScheduleSheetCheck {
    val length = duration
    val titleError = R.string.error_title_required.takeIf { title.isBlank() }
    val endError = R.string.error_event_end_before_start.takeIf { length == null }
    val rangeError = R.string.error_outside_trip.takeIf {
        length != null && within != null && (startsAt < within.start || endsAt > within.endInclusive)
    }

    // The cost is optional whole: an event with nothing said about money is most events.
    // But a figure with nothing to file it under says nothing the budget can read, and a
    // category with no figure beside it is a thought somebody did not finish.
    val amountValue = MoneyFormat.parseAmount(amount, decimals)
    val costAsked = amount.isNotBlank() || categoryId != null
    val amountError = when {
        !costAsked -> null
        amount.isBlank() -> R.string.error_amount_required
        amountValue == null -> R.string.error_amount_invalid
        else -> null
    }
    val categoryError = R.string.error_category_required.takeIf { costAsked && categoryId == null }
    val currencyError = R.string.error_currency_required.takeIf { costAsked && currencyCode == null }

    if (titleError != null || endError != null || rangeError != null ||
        amountError != null || categoryError != null || currencyError != null
    ) {
        return ScheduleSheetCheck.Refused(
            copy(
                titleError = titleError,
                endError = endError,
                rangeError = rangeError,
                amountError = amountError,
                categoryError = categoryError,
                currencyError = currencyError,
            )
        )
    }

    return ScheduleSheetCheck.Accepted(
        title = title.trim(),
        notes = notes.trim(),
        followsId = followsId,
        startsAt = startsAt.epochSecond,
        durationMinutes = requireNotNull(length).toMinutes().toInt(),
        // Only what the event itself states is written down. An inherited zone is not a
        // fact about this event, and storing it would nail the event to a place the trip
        // might later be rerouted away from.
        startsInZone = startZone.id.takeIf { declaresStart },
        endsInZone = endZone.id.takeIf { declaresEnd },
        categoryId = categoryId.takeIf { costAsked },
        amountMilli = amountValue?.milli?.takeIf { costAsked },
        currencyCode = currencyCode.takeIf { costAsked },
    )
}

/** An unwritten figure is nought rather than nothing: a blank hours field means no hours. */
private fun Long?.orZero(): Long = this ?: 0L

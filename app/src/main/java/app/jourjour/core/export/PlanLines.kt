package app.jourjour.core.export

import app.jourjour.core.schedule.Band
import app.jourjour.core.schedule.EventBand
import app.jourjour.core.schedule.GapBand
import app.jourjour.core.schedule.GapEdge
import app.jourjour.core.schedule.OverlapBand
import app.jourjour.core.schedule.PlacedEvent
import app.jourjour.core.schedule.ScheduleDay
import app.jourjour.core.schedule.endShift
import app.jourjour.core.schedule.shiftLabel
import app.jourjour.core.schedule.startShift
import java.time.LocalDate
import java.time.ZonedDateTime

/** Which of a line's corners are rounded; a square one says it carries on into the next. */
data class PlanCorners(val top: Boolean = true, val bottom: Boolean = true)

/**
 * One line of a printed schedule.
 *
 * The same lines the tab draws while a schedule is being *read*: the days, what is booked
 * in them, the free stretches between two things, and the minutes claimed twice. Not the
 * chains, the ends of days or the empty days — those are the writing half of the screen,
 * and a sheet of paper is nobody's writing half.
 */
sealed interface PlanLine {

    /** A date across the page, with what the day is about where anybody has said. */
    data class Day(val date: String, val summary: String) : PlanLine

    /**
     * One event, whole, under the day it begins in.
     *
     * Not cut at midnight as the screen cuts it. A list on a screen is scrolled through a
     * day at a time and has to show what is going on under each heading; a page is read
     * down, and a flight printed under two dates reads as two flights — with its notes
     * and its cost on whichever half they happened to land.
     */
    data class Event(
        val start: String,
        /** How far the clocks moved at that hour, or empty where they did not. */
        val startShift: String,
        /**
         * The date the event begins on, where that is not the heading's date — which is
         * only where it began before the trip did. Empty everywhere else.
         */
        val startDate: String,
        val end: String,
        val endShift: String,
        /**
         * The date it ends on, where that is not the heading's: an hour under the second
         * of October that is really the third has to say so, or it reads as a flight that
         * landed before it took off.
         */
        val endDate: String,
        val title: String,
        val notes: String,
        val length: String,
        val cost: String,
        val corners: PlanCorners,
    ) : PlanLine

    /** Free time between two things, which is the one gap worth printing: how long it is. */
    data class Gap(val length: String) : PlanLine

    /** Minutes claimed twice, square against the two events that claim them. */
    data class Overlap(val text: String, val length: String) : PlanLine
}

/**
 * The days of a trip as lines to print.
 *
 * A day with nothing booked in it is left out, as it is on the screen being read: a page
 * of empty dates is a page nobody wanted.
 *
 * An event is printed once, under the first heading it appears under, with both its ends.
 * The day after is printed as though the night had nothing in it — and so the free time
 * that would have run from the end of it is the morning, which is not printed either: a
 * length at the top of a day with nothing above it to be measured from is a figure about
 * nothing anybody can see.
 *
 * @param clock renders an hour the way the reader's locale writes one.
 * @param shortDate renders a date small enough to stand under an hour in its own column.
 * @param length renders a stretch of time as the padded clock a schedule writes.
 * @param costOf what the event is expected to cost, already in words, or empty.
 */
fun planLines(
    days: List<ScheduleDay>,
    bandsOf: (ScheduleDay) -> List<Band>,
    summaryOf: (ScheduleDay) -> String,
    dateOf: (ScheduleDay) -> String,
    clock: (ZonedDateTime) -> String,
    shortDate: (LocalDate) -> String,
    length: (minutes: Long) -> String,
    costOf: (PlacedEvent) -> String,
    overlapText: String,
): List<PlanLine> = buildList {
    // By the event rather than by which piece of it this is: one that began before the
    // trip did has no first piece to find, and is printed under the first day it shows on.
    val printed = mutableSetOf<Long>()
    days.forEach { day ->
        val bands = bandsOf(day)
            .filter { it.isWorthPrinting }
            .filter { it !is EventBand || printed.add(it.placed.id) }
            .dropWhile { it is GapBand }
        if (bands.none { it is EventBand }) return@forEach
        add(PlanLine.Day(date = dateOf(day), summary = summaryOf(day)))
        bands.forEachIndexed { at, band ->
            when (band) {
                is EventBand -> add(
                    band.toLine(
                        on = day.date,
                        above = bands.getOrNull(at - 1),
                        below = bands.getOrNull(at + 1),
                        clock = clock,
                        shortDate = shortDate,
                        length = length,
                        costOf = costOf,
                    )
                )
                is GapBand -> add(PlanLine.Gap(length(band.length.toMinutes())))
                is OverlapBand -> add(
                    PlanLine.Overlap(overlapText, length(band.length.toMinutes()))
                )
                else -> Unit
            }
        }
    }
}

@Suppress("LongParameterList")
private fun EventBand.toLine(
    on: LocalDate,
    above: Band?,
    below: Band?,
    clock: (ZonedDateTime) -> String,
    shortDate: (LocalDate) -> String,
    length: (minutes: Long) -> String,
    costOf: (PlacedEvent) -> String,
) = PlanLine.Event(
    start = clock(placed.start),
    startShift = placed.startShift?.let(::shiftLabel).orEmpty(),
    startDate = placed.start.toLocalDate().takeIf { it != on }?.let(shortDate).orEmpty(),
    end = clock(placed.end),
    endShift = placed.endShift?.let(::shiftLabel).orEmpty(),
    endDate = placed.end.toLocalDate().takeIf { it != on }?.let(shortDate).orEmpty(),
    title = placed.event.title,
    notes = placed.event.notes,
    length = length(placed.duration.toMinutes()),
    cost = costOf(placed),
    // Square only against minutes it has been found to share with something else, so
    // that the run reads as one thing. Midnight squares nothing here: the event is whole.
    corners = PlanCorners(
        top = above !is OverlapBand,
        bottom = below !is OverlapBand,
    ),
)

/**
 * What belongs on a printed schedule.
 *
 * The chains are a control and print as nothing; the stretches at either end of a day are
 * the night, and how long the night is helps nobody.
 */
private val Band.isWorthPrinting: Boolean
    get() = when (this) {
        is GapBand -> edge == GapEdge.BETWEEN
        is EventBand, is OverlapBand -> true
        else -> false
    }


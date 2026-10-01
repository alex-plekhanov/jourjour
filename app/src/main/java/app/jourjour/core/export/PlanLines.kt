package app.jourjour.core.export

import app.jourjour.core.schedule.Band
import app.jourjour.core.schedule.EventBand
import app.jourjour.core.schedule.EventPart
import app.jourjour.core.schedule.GapBand
import app.jourjour.core.schedule.GapEdge
import app.jourjour.core.schedule.OverlapBand
import app.jourjour.core.schedule.PlacedEvent
import app.jourjour.core.schedule.ScheduleDay
import app.jourjour.core.schedule.endShift
import app.jourjour.core.schedule.shiftLabel
import app.jourjour.core.schedule.startShift
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
     * One event, or the part of one that falls in this day.
     *
     * An end the event does not have in this day is left out altogether — and so is what
     * it costs, which belongs to the day it finishes in and would otherwise be counted by
     * eye twice.
     */
    data class Event(
        val start: String,
        /** How far the clocks moved at that hour, or empty where they did not. */
        val startShift: String,
        val end: String,
        val endShift: String,
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
 * @param clock renders an hour the way the reader's locale writes one.
 * @param length renders a stretch of time as the padded clock a schedule writes.
 * @param costOf what the event is expected to cost, already in words, or empty.
 */
fun planLines(
    days: List<ScheduleDay>,
    bandsOf: (ScheduleDay) -> List<Band>,
    summaryOf: (ScheduleDay) -> String,
    dateOf: (ScheduleDay) -> String,
    clock: (ZonedDateTime) -> String,
    length: (minutes: Long) -> String,
    costOf: (PlacedEvent) -> String,
    overlapText: String,
): List<PlanLine> = buildList {
    days.forEach { day ->
        val bands = bandsOf(day).filter { it.isWorthPrinting }
        if (bands.none { it is EventBand }) return@forEach
        add(PlanLine.Day(date = dateOf(day), summary = summaryOf(day)))
        bands.forEachIndexed { at, band ->
            when (band) {
                is EventBand -> add(
                    band.toLine(
                        above = bands.getOrNull(at - 1),
                        below = bands.getOrNull(at + 1),
                        clock = clock,
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

private fun EventBand.toLine(
    above: Band?,
    below: Band?,
    clock: (ZonedDateTime) -> String,
    length: (minutes: Long) -> String,
    costOf: (PlacedEvent) -> String,
) = PlanLine.Event(
    start = if (part.opens) clock(placed.start) else "",
    startShift = if (part.opens) placed.startShift?.let(::shiftLabel).orEmpty() else "",
    end = if (part.closes) clock(placed.end) else "",
    endShift = if (part.closes) placed.endShift?.let(::shiftLabel).orEmpty() else "",
    title = placed.event.title,
    notes = placed.event.notes,
    // The whole of it either way: half a flight is not a figure anybody wants.
    length = length(placed.duration.toMinutes()),
    cost = if (part.closes) costOf(placed) else "",
    // Square where this carries on: into tomorrow, or into the minutes it has been found
    // to share with something else, so that the run reads as one thing.
    corners = PlanCorners(
        top = part.opens && above !is OverlapBand,
        bottom = part.closes && below !is OverlapBand,
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

private val EventPart.opens: Boolean
    get() = this == EventPart.WHOLE || this == EventPart.STARTS

private val EventPart.closes: Boolean
    get() = this == EventPart.WHOLE || this == EventPart.ENDS

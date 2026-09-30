package app.jourjour.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import app.jourjour.core.money.Money

/**
 * One thing that happens at a known time on a trip: a flight, a hotel night, a table
 * booked for eight.
 *
 * Unlike an entry, whose time is a wall clock ("the money went out on the third at noon",
 * whatever zone the phone is in later), an event is a **moment**: [startsAt] is seconds
 * since the epoch and [durationMinutes] carries it to its end. Everything a person reads
 * — the time in the corner of the card, which day the event falls under, how long a day
 * lasts — is that moment rendered in whatever zone is in force there.
 *
 * That is the whole reason for the shape. A flight leaving Moscow at 23:00 and landing in
 * New York at 20:00 the same evening ends later than it began, and no pair of wall clocks
 * can say so. It also buys the rest for nothing: a day of 31 hours when the trip goes
 * west, a day of 23 when the clocks change, and no special case for either.
 *
 * **Zones are inherited, not repeated.** Both are null on almost every event, which means
 * "wherever we already were". A zone at the end is what a flight says — this is what took
 * us there; a zone at the start is "from here on we are in X", for a move no event
 * accounts for. Either holds until the next event that says otherwise, and the home zone
 * in the settings answers before any of them has spoken.
 *
 * What a person typed is the **wall clock**, and that is the thing that must not move.
 * Putting a flight in front of dinner leaves dinner at eight and changes its moment. So
 * writing a zone re-anchors everything after it, in one transaction; see `reanchor`.
 *
 * [followsId] says this event begins where another ends. The start is still stored — a
 * derived one would vanish with its predecessor and could not be sorted or grouped by —
 * and the link is a promise the repository keeps: move the predecessor and this follows,
 * delete it and the link goes while the time stays.
 *
 * The three expense columns are filled together or not at all: what this is expected to
 * cost, which is what the budget reads as the finer plan for its category.
 */
@Entity(
    tableName = "schedule_events",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ScheduleEvent::class,
            parentColumns = ["id"],
            childColumns = ["follows_id"],
            // The link is worth less than the event: losing a predecessor unchains this
            // one, and the time it had stays the time it has.
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = Currency::class,
            parentColumns = ["code"],
            childColumns = ["currency_code"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["trip_id", "starts_at"]),
        Index("follows_id"),
        Index("category_id"),
        Index("currency_code"),
    ],
)
data class ScheduleEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "trip_id")
    val tripId: Long,
    val title: String = "",
    val notes: String = "",
    /** When it begins, in seconds since the epoch. */
    @ColumnInfo(name = "starts_at")
    val startsAt: Long,
    /** How long it lasts. At least a minute: an event of no length is not an event. */
    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int,
    /** "From here on we are in X", for a move no event of the schedule accounts for. */
    @ColumnInfo(name = "starts_in_zone")
    val startsInZone: String? = null,
    /** Where this event lands, which is what a flight says. */
    @ColumnInfo(name = "ends_in_zone")
    val endsInZone: String? = null,
    /** The event this one begins at the end of. */
    @ColumnInfo(name = "follows_id")
    val followsId: Long? = null,
    /** What the planned expense is for; null when nothing is planned. */
    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,
    /** What it is expected to cost, in thousandths. See [Money]. */
    @ColumnInfo(name = "amount_milli")
    val amountMilli: Long? = null,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String? = null,
)

/** An event with the dictionary rows its planned expense names, which is how a list shows it. */
data class ScheduleEventDetails(
    @Embedded
    val event: ScheduleEvent,
    @Relation(parentColumn = "category_id", entityColumn = "id")
    val category: Category?,
    @Relation(parentColumn = "currency_code", entityColumn = "code")
    val currency: Currency?,
)

package app.jourjour.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One thing to do before a trip: take the passport, book the car, stop the post.
 *
 * Flat, like an entry. A section is a piece of text on the item rather than a row of its
 * own, because a section has nothing of its own to hold — no order, no dates, nothing but
 * its name. Renaming one is an update of every item that carries the name, and deleting
 * one is deleting them; a section with no items in it has stopped existing, which is what
 * a person deleting the last item of a section means to happen.
 *
 * [isSkipped] is "not this trip": the item stays on the list, keeping the list the same
 * from trip to trip, but nobody is expected to do anything about it. It is a different
 * thing from [isDone], which is why it is a flag of its own rather than a third state of
 * the tick — an item ruled out this time is still waiting on the next.
 */
@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trip_id")],
)
data class ChecklistItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "trip_id")
    val tripId: Long,
    val section: String,
    val title: String,
    @ColumnInfo(name = "is_done")
    val isDone: Boolean = false,
    /** Ruled out for this trip: still on the list, no longer anybody's business. */
    @ColumnInfo(name = "is_skipped")
    val isSkipped: Boolean = false,
) {
    /** Whether the item is still waiting on somebody. */
    val isOutstanding: Boolean get() = !isDone && !isSkipped
}

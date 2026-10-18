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
 * own, because a section has nothing of its own to hold: its name is on its items and so
 * is its place, a section standing where the run of them stands — see [sortOrder].
 * Renaming one is an update of every item that carries the name, and deleting
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
    /**
     * What there is to say about the item beyond its name — which charger, where the
     * insurance papers are kept. Empty for most items, which is what an item with nothing
     * more to say looks like, rather than null: an empty note and no note are the same.
     */
    val notes: String = "",
    @ColumnInfo(name = "is_done")
    val isDone: Boolean = false,
    /** Ruled out for this trip: still on the list, no longer anybody's business. */
    @ColumnInfo(name = "is_skipped")
    val isSkipped: Boolean = false,
    /**
     * How many of the thing to take, in thousandths — three shirts, two litres of water.
     * Null where nobody said, which is most items: "passport" is not a count.
     */
    @ColumnInfo(name = "quantity_milli")
    val quantityMilli: Long? = null,
    /**
     * Where the item stands among the trip's items — all of them, not its section's.
     *
     * The sections have no order of their own to store, having no row to store it on, so
     * they take the one this gives them: a section stands where its items stand, and
     * moving a section is moving the run of them. A list nobody has dragged carries nought
     * all the way down and falls back on the order the items were made in.
     */
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
) {
    /** Whether the item is still waiting on somebody. */
    val isOutstanding: Boolean get() = !isDone && !isSkipped
}

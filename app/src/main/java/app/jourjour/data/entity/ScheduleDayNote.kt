package app.jourjour.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * What a day of a trip is about, in a line: "flight to Morocco", "Istanbul", "the drive
 * north".
 *
 * It belongs to the **date** and not to any event, which is the whole of why it is a row
 * of its own. A day is about a place far more often than it is about any one thing that
 * happens in it, and the days with nothing booked in them are exactly the ones most worth
 * naming while a trip is still being planned.
 *
 * The date is local and stays local, unlike an event's moment: it is the heading a person
 * reads, not something to be ordered against anything. A day with nothing to say has no
 * row at all rather than an empty one.
 */
@Entity(
    tableName = "schedule_days",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // One line per day, enforced here rather than remembered everywhere: a second row for
    // the same date would be a description nobody could see or delete.
    indices = [Index(value = ["trip_id", "date"], unique = true)],
)
data class ScheduleDayNote(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "trip_id")
    val tripId: Long,
    val date: LocalDate,
    val summary: String,
)

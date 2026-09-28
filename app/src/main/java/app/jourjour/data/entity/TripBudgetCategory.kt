package app.jourjour.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * What a person has settled about one category of one trip's budget.
 *
 * The flags belong to the category rather than to the lines under it. Spending is matched
 * to a budget by category — nothing ties a line to the entries that paid for it — so
 * "there will be no more of this" is a statement about the category, and stamping it on
 * every line would leave the app deciding what a half-stamped category means.
 *
 * [isSpendingClosed] says the money side is finished, whatever was actually spent against
 * what was planned: the remainder is nil and the app should stop expecting anything.
 *
 * [isPlanningClosed] says the detailed plan for this category is complete, so it may be
 * read as the truth about what is still to come rather than as a partial booking. It is
 * written down now and comes into use with the schedule, where a category still being
 * planned would otherwise make the remainder look smaller than it is.
 *
 * A row exists only where somebody has said something: there is no row for a category
 * whose flags are both off, and the last line of a category taking its row with it is the
 * same rule a checklist section lives by.
 */
@Entity(
    tableName = "trip_budget_categories",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        // One row to a category of a trip: a second would leave the flags depending on
        // which of them was read.
        Index(value = ["trip_id", "category_id"], unique = true),
        Index("category_id"),
    ],
)
data class TripBudgetCategory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "trip_id")
    val tripId: Long,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    /** No more will be spent here, so nothing is left to spend. */
    @ColumnInfo(name = "is_spending_closed")
    val isSpendingClosed: Boolean = false,
    /** Nothing more will be planned here, so the plan can be read as the whole of it. */
    @ColumnInfo(name = "is_planning_closed")
    val isPlanningClosed: Boolean = false,
)

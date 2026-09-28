package app.jourjour.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDateTime

/**
 * A recount of a wallet: how much money it actually held at a moment in time.
 *
 * Only the counted amount is stored. What the wallet was supposed to hold is derived from
 * the opening balance and the entries instead, so that correcting an entry afterwards
 * recomputes the discrepancy rather than leaving it frozen at stale data.
 */
@Entity(
    tableName = "balance_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = TripWallet::class,
            parentColumns = ["id"],
            childColumns = ["wallet_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["wallet_id", "date_time"])],
)
data class BalanceSnapshot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "wallet_id")
    val walletId: Long,
    @ColumnInfo(name = "date_time")
    val dateTime: LocalDateTime,
    /** The amount actually counted. */
    @ColumnInfo(name = "amount_milli")
    val amountMilli: Long,
    val notes: String = "",
)

data class BalanceSnapshotDetails(
    @Embedded
    val snapshot: BalanceSnapshot,
    @Relation(entity = TripWallet::class, parentColumn = "wallet_id", entityColumn = "id")
    val wallet: TripWalletDetails,
)

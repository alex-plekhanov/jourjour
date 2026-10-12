package app.jourjour.repository

import app.jourjour.data.dao.EntryDao
import app.jourjour.data.entity.Entry
import app.jourjour.data.entity.EntryDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(private val dao: EntryDao) {

    /**
     * Lives as long as the repository, which is as long as the process. All it runs are
     * the shared queries below, and those stop on their own once nobody is listening.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val byTrip = ConcurrentHashMap<Long, Flow<List<EntryDetails>>>()

    /**
     * The entries of a trip, as one query however many screens read it.
     *
     * The three tabs of a trip's screen all do, and Room would otherwise run the query
     * once per tab on every change to the table, and build the rows three times over. It
     * is shared while anybody listens and for a moment after — a rotation — and then
     * dropped along with what it held, so a trip left behind costs nothing.
     */
    fun observeByTrip(tripId: Long): Flow<List<EntryDetails>> = byTrip.getOrPut(tripId) {
        dao.observeByTrip(tripId).shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = STOP_TIMEOUT_MS,
                replayExpirationMillis = 0,
            ),
            replay = 1,
        )
    }

    suspend fun getDetailsById(id: Long): EntryDetails? = dao.getDetailsById(id)

    suspend fun insert(entry: Entry): Long = dao.insert(entry)

    suspend fun insertAll(entries: List<Entry>) = dao.insertAll(entries)

    suspend fun update(entry: Entry) = dao.update(entry)

    suspend fun delete(entry: Entry) = dao.delete(entry)

    /**
     * The moment of the trip's nearest entry before this one, and null where there is
     * none. What the two buttons beside the date on the entry form walk along.
     */
    suspend fun momentBefore(tripId: Long, moment: LocalDateTime): LocalDateTime? =
        dao.momentBefore(tripId, moment)

    /** The same, forwards. */
    suspend fun momentAfter(tripId: Long, moment: LocalDateTime): LocalDateTime? =
        dao.momentAfter(tripId, moment)

    suspend fun countForTrip(tripId: Long): Int = dao.countForTrip(tripId)

    /** What a wallet was last paid in, which is what a new entry on it opens on. */
    suspend fun lastCurrencyForWallet(walletId: Long): String? =
        dao.lastCurrencyForWallet(walletId)

    suspend fun setExpenseSettlement(entryId: Long, amountMilli: Long?) =
        dao.setExpenseSettlement(entryId, amountMilli)

    suspend fun setIncomeSettlement(entryId: Long, amountMilli: Long?) =
        dao.setIncomeSettlement(entryId, amountMilli)

    suspend fun photoFiles(tripId: Long): List<String> = dao.photoFiles(tripId)

    suspend fun allPhotoFiles(): List<String> = dao.allPhotoFiles()

    fun observePhotoCount(tripId: Long): Flow<Int> = dao.observePhotoCount(tripId)

    suspend fun clearPhotos(tripId: Long) = dao.clearPhotos(tripId)

    private companion object {
        /** The moment a screen may be away for and still find the query running. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

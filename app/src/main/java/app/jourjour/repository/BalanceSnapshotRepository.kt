package app.jourjour.repository

import kotlinx.coroutines.flow.Flow
import app.jourjour.data.dao.BalanceSnapshotDao
import app.jourjour.data.entity.BalanceSnapshot
import app.jourjour.data.entity.BalanceSnapshotDetails
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BalanceSnapshotRepository @Inject constructor(private val dao: BalanceSnapshotDao) {

    fun observeByTrip(tripId: Long): Flow<List<BalanceSnapshotDetails>> = dao.observeByTrip(tripId)

    suspend fun insert(snapshot: BalanceSnapshot): Long = dao.insert(snapshot)
}

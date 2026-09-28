package app.jourjour.repository

import kotlinx.coroutines.flow.Flow
import app.jourjour.data.dao.CurrencyDao
import app.jourjour.data.entity.Currency
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyRepository @Inject constructor(private val dao: CurrencyDao) {

    fun observeAll(): Flow<List<Currency>> = dao.observeAll()

    suspend fun getAll(): List<Currency> = dao.getAll()

    suspend fun getByCode(code: String): Currency? = dao.getByCode(code)

    suspend fun insert(currency: Currency) = dao.insert(currency)

    suspend fun update(currency: Currency) = dao.update(currency)

    suspend fun delete(currency: Currency) = dao.delete(currency)

    suspend fun isUsed(code: String): Boolean = dao.isUsed(code)

    /** Records that an entry has just been written in this currency. */
    suspend fun touch(code: String, usedAt: LocalDateTime) = dao.touch(code, usedAt)
}

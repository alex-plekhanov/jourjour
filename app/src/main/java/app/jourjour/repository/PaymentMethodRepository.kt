package app.jourjour.repository

import kotlinx.coroutines.flow.Flow
import app.jourjour.data.dao.PaymentMethodDao
import app.jourjour.data.dao.TripWalletDao
import app.jourjour.data.entity.PaymentMethod
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentMethodRepository @Inject constructor(
    private val dao: PaymentMethodDao,
    private val walletDao: TripWalletDao,
) {

    fun observeAll(): Flow<List<PaymentMethod>> = dao.observeAll()

    suspend fun getAll(): List<PaymentMethod> = dao.getAll()

    suspend fun getById(id: Long): PaymentMethod? = dao.getById(id)

    suspend fun insert(paymentMethod: PaymentMethod): Long = dao.insert(paymentMethod)

    suspend fun update(paymentMethod: PaymentMethod) = dao.update(paymentMethod)

    suspend fun delete(paymentMethod: PaymentMethod) = dao.delete(paymentMethod)

    /** A payment method is held by the wallets set up with it, not by entries directly. */
    suspend fun isUsed(paymentMethodId: Long): Boolean =
        walletDao.existsForPaymentMethod(paymentMethodId)
}

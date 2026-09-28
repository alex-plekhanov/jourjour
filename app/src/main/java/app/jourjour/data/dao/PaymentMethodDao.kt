package app.jourjour.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import app.jourjour.data.entity.PaymentMethod

@Dao
interface PaymentMethodDao {

    @Query("SELECT * FROM payment_methods ORDER BY sort_order, name")
    fun observeAll(): Flow<List<PaymentMethod>>

    @Query("SELECT * FROM payment_methods ORDER BY sort_order, name")
    suspend fun getAll(): List<PaymentMethod>

    @Query("SELECT * FROM payment_methods WHERE id = :id")
    suspend fun getById(id: Long): PaymentMethod?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(paymentMethod: PaymentMethod): Long

    @Update
    suspend fun update(paymentMethod: PaymentMethod)

    @Delete
    suspend fun delete(paymentMethod: PaymentMethod)
}

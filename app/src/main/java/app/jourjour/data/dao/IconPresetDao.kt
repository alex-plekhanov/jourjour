package app.jourjour.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import app.jourjour.data.entity.IconPreset
import app.jourjour.data.entity.IconTarget

@Dao
interface IconPresetDao {

    @Query("SELECT emoji FROM icon_presets WHERE target = :target ORDER BY sort_order, emoji")
    fun observeEmoji(target: IconTarget): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(preset: IconPreset)

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM icon_presets WHERE target = :target")
    suspend fun maxSortOrder(target: IconTarget): Int

    /**
     * Adds an icon a person typed by hand. It goes to the end of the row, where the eye
     * looks for something new; a repeat of an icon already offered is ignored.
     */
    @Transaction
    suspend fun add(target: IconTarget, emoji: String) {
        insert(IconPreset(target, emoji, sortOrder = maxSortOrder(target) + 1))
    }
}

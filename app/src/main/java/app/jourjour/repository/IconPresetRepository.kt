package app.jourjour.repository

import kotlinx.coroutines.flow.Flow
import app.jourjour.data.dao.IconPresetDao
import app.jourjour.data.entity.IconTarget
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IconPresetRepository @Inject constructor(private val dao: IconPresetDao) {

    fun observeEmoji(target: IconTarget): Flow<List<String>> = dao.observeEmoji(target)

    suspend fun add(target: IconTarget, emoji: String) = dao.add(target, emoji)
}

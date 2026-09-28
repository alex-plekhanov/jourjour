package app.jourjour.ui.settings.category

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import app.jourjour.data.entity.CategoryWithFavorites
import app.jourjour.repository.CategoryRepository
import app.jourjour.ui.common.STOP_TIMEOUT_MS
import javax.inject.Inject

@Immutable
data class CategoryListState(
    val categories: List<CategoryWithFavorites> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class CategoryListViewModel @Inject constructor(
    categoryRepository: CategoryRepository,
) : ViewModel() {

    val state: StateFlow<CategoryListState> = categoryRepository.observeAllWithFavorites()
        .map { CategoryListState(categories = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = CategoryListState(),
        )
}

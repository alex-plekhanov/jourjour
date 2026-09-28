package app.jourjour.ui.settings.category

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.jourjour.R
import app.jourjour.core.text.resolveName
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.CategoryFavorite
import app.jourjour.data.entity.IconTarget
import app.jourjour.repository.CategoryRepository
import app.jourjour.repository.IconPresetRepository
import app.jourjour.ui.common.DictionaryEditViewModel
import app.jourjour.ui.common.DictionaryForm
import app.jourjour.ui.common.STOP_TIMEOUT_MS
import app.jourjour.ui.navigation.Route
import javax.inject.Inject

data class CategoryEditState(
    val name: String = "",
    val icon: String = DEFAULT_ICON,
    val isExpense: Boolean = true,
    val isIncome: Boolean = false,
    val trackQuantity: Boolean = false,
    val iconPresets: List<String> = emptyList(),
    /** Favourite titles are edited in the form and saved together with the category. */
    val favorites: List<String> = emptyList(),
    val favoriteDraft: String = "",
    @param:StringRes val nameError: Int? = null,

    @param:StringRes val directionError: Int? = null,
    val isEditMode: Boolean = false,
    override val isDeleteDialogVisible: Boolean = false,
) : DictionaryForm<CategoryEditState> {
    override val hasErrors: Boolean
        get() = nameError != null || directionError != null

    val canAddFavorite: Boolean
        get() = favoriteDraft.isNotBlank() && favoriteDraft.trim() !in favorites

    override fun withDeleteDialog(visible: Boolean) = copy(isDeleteDialogVisible = visible)
}

private const val DEFAULT_ICON = "🧾"

@HiltViewModel
class CategoryEditViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val categoryRepository: CategoryRepository,
    private val iconPresetRepository: IconPresetRepository,
    savedStateHandle: SavedStateHandle,
) : DictionaryEditViewModel<Category, CategoryEditState>(
    context,
    CategoryEditState(
        isEditMode = savedStateHandle.toRoute<Route.CategoryEdit>().categoryId != Route.NEW,
    ),
) {

    private val categoryId: Long = savedStateHandle.toRoute<Route.CategoryEdit>().categoryId

    val state: StateFlow<CategoryEditState> =
        combine(form, iconPresetRepository.observeEmoji(IconTarget.CATEGORY)) { form, presets ->
            form.copy(iconPresets = presets)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = form.value,
        )

    override val saveErrorRes = R.string.error_save_category
    override val deleteErrorRes = R.string.error_delete_category
    override val inUseRes = R.string.error_category_in_use

    /**
     * The favourites as they were loaded, keyed by the text the form shows. A preloaded
     * favourite is shown translated, so it has to be recognised by that translation on the
     * way back — otherwise saving the form would turn it into plain text and pin it to
     * whatever language it happened to be edited in.
     */
    private var loadedFavorites: Map<String, CategoryFavorite> = emptyMap()

    init {
        if (categoryId != Route.NEW) load()
    }

    override suspend fun read(): Category? = categoryRepository.getById(categoryId)

    override fun formOf(row: Category): CategoryEditState {
        initialName = resolveName(context, row.name, row.nameKey)
        return CategoryEditState(
            name = initialName,
            icon = row.icon,
            isExpense = row.isExpense,
            isIncome = row.isIncome,
            trackQuantity = row.trackQuantity,
            favorites = loadedFavorites.keys.toList(),
            isEditMode = true,
        )
    }

    /** The favourites come with the row: the form cannot open without them. */
    override fun load() {
        viewModelScope.launch {
            loadedFavorites = categoryRepository.getFavorites(categoryId)
                .associateBy { resolveName(context, it.name, it.nameKey) }
            super.load()
        }
    }

    fun updateName(value: String) = update { it.copy(name = value) }

    fun updateIcon(value: String) = update { it.copy(icon = value) }

    /** Keeps a hand-typed icon so it is a tap away next time. */
    fun addIconPreset(emoji: String) {
        viewModelScope.launch { iconPresetRepository.add(IconTarget.CATEGORY, emoji) }
    }

    fun updateIsExpense(value: Boolean) = update { it.copy(isExpense = value) }

    fun updateIsIncome(value: Boolean) = update { it.copy(isIncome = value) }

    fun updateTrackQuantity(value: Boolean) = update { it.copy(trackQuantity = value) }

    fun updateFavoriteDraft(value: String) = update { it.copy(favoriteDraft = value) }

    fun addFavorite() = update { state ->
        if (!state.canAddFavorite) {
            state
        } else {
            state.copy(favorites = state.favorites + state.favoriteDraft.trim(), favoriteDraft = "")
        }
    }

    fun removeFavorite(name: String) = update { it.copy(favorites = it.favorites - name) }

    override fun validate(state: CategoryEditState) = state.copy(
        nameError = R.string.error_name_empty.takeIf { state.name.isBlank() },

        // A category that is neither would leave an entry with nothing to fill in.
        directionError = R.string.error_direction_required
            .takeIf { !state.isExpense && !state.isIncome },
    )

    override fun build(state: CategoryEditState, previous: Category?) = Category(
        id = previous?.id ?: Route.NEW,
        name = state.name.trim(),
        icon = state.icon.trim(),
        isExpense = state.isExpense,
        isIncome = state.isIncome,
        trackQuantity = state.trackQuantity,
        nameKey = keptNameKey(previous?.nameKey, state.name),
        sortOrder = previous?.sortOrder ?: DEFAULT_SORT_ORDER,
    )

    override suspend fun write(row: Category, previous: Category?) {
        val favorites = form.value.favorites
        categoryRepository.save(row) { id ->
            favorites.map { label ->
                loadedFavorites[label] ?: CategoryFavorite(categoryId = id, name = label)
            }
        }
    }

    override suspend fun isUsed(row: Category): Boolean = categoryRepository.isUsed(row.id)

    override suspend fun remove(row: Category) = categoryRepository.delete(row)

    private companion object {
        const val DEFAULT_SORT_ORDER = 1_000
    }
}

package app.jourjour.ui.settings.currency

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import app.jourjour.R
import app.jourjour.core.text.resolveName
import app.jourjour.data.entity.Currency
import app.jourjour.repository.CurrencyRepository
import app.jourjour.ui.common.DictionaryEditViewModel
import app.jourjour.ui.common.DictionaryForm
import app.jourjour.ui.navigation.Route
import javax.inject.Inject

data class CurrencyEditState(
    val code: String = "",
    val name: String = "",
    val symbol: String = "",
    /** A string while it is typed: an empty field means unfilled, not zero. */
    val decimals: String = "2",
    @param:StringRes val codeError: Int? = null,
    @param:StringRes val nameError: Int? = null,
    @param:StringRes val symbolError: Int? = null,
    @param:StringRes val decimalsError: Int? = null,
    val isEditMode: Boolean = false,
    override val isDeleteDialogVisible: Boolean = false,
) : DictionaryForm<CurrencyEditState> {
    override val hasErrors: Boolean
        get() = codeError != null || nameError != null || symbolError != null || decimalsError != null

    override fun withDeleteDialog(visible: Boolean) = copy(isDeleteDialogVisible = visible)
}

@HiltViewModel
class CurrencyEditViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val currencyRepository: CurrencyRepository,
    savedStateHandle: SavedStateHandle,
) : DictionaryEditViewModel<Currency, CurrencyEditState>(
    context,
    CurrencyEditState(isEditMode = savedStateHandle.toRoute<Route.CurrencyEdit>().code != null),
) {

    private val code: String? = savedStateHandle.toRoute<Route.CurrencyEdit>().code

    val state: StateFlow<CurrencyEditState> = form.asStateFlow()

    override val saveErrorRes = R.string.error_save_currency
    override val deleteErrorRes = R.string.error_delete_currency
    override val inUseRes = R.string.error_currency_in_use

    init {
        if (code != null) load()
    }

    override suspend fun read(): Currency? = code?.let { currencyRepository.getByCode(it) }

    override fun formOf(row: Currency): CurrencyEditState {
        initialName = resolveName(context, row.name, row.nameKey)
        return CurrencyEditState(
            code = row.code,
            name = initialName,
            symbol = row.symbol,
            decimals = row.decimals.toString(),
            isEditMode = true,
        )
    }

    fun updateCode(value: String) = update {
        it.copy(code = value.uppercase().filter(Char::isLetter).take(CODE_LENGTH))
    }

    fun updateName(value: String) = update { it.copy(name = value) }

    fun updateSymbol(value: String) = update { it.copy(symbol = value.take(SYMBOL_MAX_LENGTH)) }

    fun updateDecimals(value: String) = update {
        it.copy(decimals = value.filter(Char::isDigit).take(1))
    }

    override fun validate(state: CurrencyEditState): CurrencyEditState {
        val decimals = state.decimals.toIntOrNull()
        return state.copy(
            codeError = R.string.error_code_empty.takeIf { state.code.isBlank() },
            nameError = R.string.error_name_empty.takeIf { state.name.isBlank() },
            symbolError = R.string.error_symbol_empty.takeIf { state.symbol.isBlank() },
            decimalsError = R.string.error_decimals_invalid
                .takeIf { decimals == null || decimals !in VALID_DECIMALS },
        )
    }

    override fun build(state: CurrencyEditState, previous: Currency?) = Currency(
        code = state.code,
        name = state.name.trim(),
        symbol = state.symbol.trim(),
        decimals = state.decimals.toInt(),
        nameKey = keptNameKey(previous?.nameKey, state.name),
        sortOrder = previous?.sortOrder ?: DEFAULT_SORT_ORDER,
        // Editing a currency says nothing about when it was last spent.
        usedAt = previous?.usedAt,
    )

    override suspend fun write(row: Currency, previous: Currency?) {
        if (previous == null) currencyRepository.insert(row) else currencyRepository.update(row)
    }

    override suspend fun isUsed(row: Currency): Boolean = currencyRepository.isUsed(row.code)

    override suspend fun remove(row: Currency) = currencyRepository.delete(row)

    private companion object {
        const val CODE_LENGTH = 3
        const val SYMBOL_MAX_LENGTH = 4
        const val DEFAULT_SORT_ORDER = 1_000
        val VALID_DECIMALS = 0..3
    }
}

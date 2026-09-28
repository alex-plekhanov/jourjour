package app.jourjour.ui.settings.paymentmethod

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
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.data.entity.IconTarget
import app.jourjour.repository.IconPresetRepository
import app.jourjour.repository.PaymentMethodRepository
import app.jourjour.ui.common.DictionaryEditViewModel
import app.jourjour.ui.common.DictionaryForm
import app.jourjour.ui.common.STOP_TIMEOUT_MS
import app.jourjour.ui.navigation.Route
import javax.inject.Inject

data class PaymentMethodEditState(
    val name: String = "",
    val icon: String = DEFAULT_ICON,
    val fixedCurrency: Boolean = false,
    val iconPresets: List<String> = emptyList(),
    @param:StringRes val nameError: Int? = null,
    val isEditMode: Boolean = false,
    override val isDeleteDialogVisible: Boolean = false,
) : DictionaryForm<PaymentMethodEditState> {
    override val hasErrors: Boolean get() = nameError != null

    override fun withDeleteDialog(visible: Boolean) = copy(isDeleteDialogVisible = visible)
}

private const val DEFAULT_ICON = "💳"

@HiltViewModel
class PaymentMethodEditViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val iconPresetRepository: IconPresetRepository,
    savedStateHandle: SavedStateHandle,
) : DictionaryEditViewModel<PaymentMethod, PaymentMethodEditState>(
    context,
    PaymentMethodEditState(
        isEditMode = savedStateHandle.toRoute<Route.PaymentMethodEdit>().paymentMethodId != Route.NEW,
    ),
) {

    private val paymentMethodId: Long =
        savedStateHandle.toRoute<Route.PaymentMethodEdit>().paymentMethodId

    val state: StateFlow<PaymentMethodEditState> =
        combine(
            form,
            iconPresetRepository.observeEmoji(IconTarget.PAYMENT_METHOD),
        ) { form, iconPresets ->
            form.copy(iconPresets = iconPresets)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = form.value,
        )

    override val saveErrorRes = R.string.error_save_payment_method
    override val deleteErrorRes = R.string.error_delete_payment_method
    override val inUseRes = R.string.error_payment_method_in_use

    init {
        if (paymentMethodId != Route.NEW) load()
    }

    override suspend fun read(): PaymentMethod? = paymentMethodRepository.getById(paymentMethodId)

    override fun formOf(row: PaymentMethod): PaymentMethodEditState {
        initialName = resolveName(context, row.name, row.nameKey)
        return PaymentMethodEditState(
            name = initialName,
            icon = row.icon,
            fixedCurrency = row.fixedCurrency,
            isEditMode = true,
        )
    }

    fun updateName(value: String) = update { it.copy(name = value) }

    fun updateIcon(value: String) = update { it.copy(icon = value) }

    /** Keeps a hand-typed icon so it is a tap away next time. */
    fun addIconPreset(emoji: String) {
        viewModelScope.launch { iconPresetRepository.add(IconTarget.PAYMENT_METHOD, emoji) }
    }

    fun updateFixedCurrency(value: Boolean) = update { it.copy(fixedCurrency = value) }

    override fun validate(state: PaymentMethodEditState) = state.copy(
        nameError = R.string.error_name_empty.takeIf { state.name.isBlank() },
    )

    override fun build(state: PaymentMethodEditState, previous: PaymentMethod?) = PaymentMethod(
        id = previous?.id ?: Route.NEW,
        name = state.name.trim(),
        icon = state.icon.trim(),
        fixedCurrency = state.fixedCurrency,
        nameKey = keptNameKey(previous?.nameKey, state.name),
        sortOrder = previous?.sortOrder ?: DEFAULT_SORT_ORDER,
    )

    override suspend fun write(row: PaymentMethod, previous: PaymentMethod?) {
        if (previous == null) {
            paymentMethodRepository.insert(row)
        } else {
            paymentMethodRepository.update(row)
        }
    }

    override suspend fun isUsed(row: PaymentMethod): Boolean = paymentMethodRepository.isUsed(row.id)

    override suspend fun remove(row: PaymentMethod) = paymentMethodRepository.delete(row)

    private companion object {
        const val DEFAULT_SORT_ORDER = 1_000
    }
}

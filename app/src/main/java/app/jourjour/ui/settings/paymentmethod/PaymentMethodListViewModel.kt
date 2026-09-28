package app.jourjour.ui.settings.paymentmethod

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.repository.PaymentMethodRepository
import app.jourjour.ui.common.STOP_TIMEOUT_MS
import javax.inject.Inject

@Immutable
data class PaymentMethodListState(
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class PaymentMethodListViewModel @Inject constructor(
    paymentMethodRepository: PaymentMethodRepository,
) : ViewModel() {

    val state: StateFlow<PaymentMethodListState> = paymentMethodRepository.observeAll()
        .map { PaymentMethodListState(paymentMethods = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = PaymentMethodListState(),
        )
}

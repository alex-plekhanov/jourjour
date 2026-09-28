package app.jourjour.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Navigation destinations. Type-safe: arguments travel as fields rather than strings, so
 * a navigation call with the wrong set of parameters fails to compile.
 */
sealed interface Route {

    @Serializable
    data object TripList : Route

    @Serializable
    data class TripEdit(val tripId: Long = NEW) : Route

    /** Working on a trip: its wallets, its entries and its checklist, as three tabs. */
    @Serializable
    data class Trip(val tripId: Long) : Route

    /** What a trip came to. Off the menu of its entries, not a tab of its own. */
    @Serializable
    data class Statistics(val tripId: Long) : Route

    /** Reconciling what the account behind wallet [walletId] was charged. */
    @Serializable
    data class Reconcile(val walletId: Long) : Route

    /** [entryId] = [NEW] creates a new entry on trip [tripId]. */
    @Serializable
    data class EntryEdit(val tripId: Long, val entryId: Long = NEW) : Route

    @Serializable
    data object About : Route

    @Serializable
    data object CurrencyList : Route

    /** [code] = null creates a new currency. */
    @Serializable
    data class CurrencyEdit(val code: String? = null) : Route

    @Serializable
    data object PaymentMethodList : Route

    @Serializable
    data class PaymentMethodEdit(val paymentMethodId: Long = NEW) : Route

    @Serializable
    data object CategoryList : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data class CategoryEdit(val categoryId: Long = NEW) : Route

    companion object {
        /** The id of a record that has not been saved yet. */
        const val NEW = 0L
    }
}

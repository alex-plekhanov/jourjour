package app.jourjour.core.dump

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything the database holds, as one JSON document.
 *
 * Meant for moving what was entered on one install to another while the app is being
 * developed — a debug build's data cannot be reached any other way once a release build
 * replaces it — and kept as plain as that: rows are written out as they are, dates as ISO
 * text, and references between them by name rather than by id, since the ids of the
 * install that reads the file are not the ids of the one that wrote it.
 *
 * The photos are not in the document but beside it, in the archive [DumpArchive] makes;
 * the document names them, and a name whose file did not come along is dropped on the
 * way in.
 */
@Serializable
data class Dump(
    val app: String = APP,
    /** The version of the schema the rows come from; a file from another is refused. */
    val databaseVersion: Int,
    val exportedAt: String,
    val currencies: List<CurrencyDump> = emptyList(),
    val paymentMethods: List<PaymentMethodDump> = emptyList(),
    val categories: List<CategoryDump> = emptyList(),
    val trips: List<TripDump> = emptyList(),
) {
    companion object {
        const val APP = "JourJour"

        const val MIME_TYPE = "application/json"

        private val json = Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        fun encode(dump: Dump): String = json.encodeToString(serializer(), dump)

        fun decode(text: String): Dump = json.decodeFromString(serializer(), text)
    }
}

/**
 * A dictionary row named the way the app tells them apart: by the resource key a
 * preloaded row carries, or by its text where a person made the row. One of the two.
 */
@Serializable
data class NameRef(
    val nameKey: String? = null,
    val name: String? = null,
) {
    companion object {
        fun of(name: String, nameKey: String?): NameRef =
            if (nameKey != null) NameRef(nameKey = nameKey) else NameRef(name = name)
    }
}

@Serializable
data class CurrencyDump(
    val code: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val nameKey: String? = null,
    val sortOrder: Int = 0,
    val usedAt: String? = null,
)

@Serializable
data class PaymentMethodDump(
    val name: String,
    val icon: String,
    val fixedCurrency: Boolean,
    val nameKey: String? = null,
    val sortOrder: Int = 0,
)

@Serializable
data class CategoryDump(
    val name: String,
    val icon: String,
    val isExpense: Boolean,
    val isIncome: Boolean,
    val trackQuantity: Boolean,
    val nameKey: String? = null,
    val sortOrder: Int = 0,
    val favorites: List<FavoriteDump> = emptyList(),
)

@Serializable
data class FavoriteDump(
    val name: String,
    val nameKey: String? = null,
    val sortOrder: Int = 0,
)

@Serializable
data class TripDump(
    val name: String,
    val startDate: String,
    val endDate: String,
    val createdAt: String,
    val photoFile: String? = null,
    val wallets: List<WalletDump> = emptyList(),
    val rates: List<RateDump> = emptyList(),
    val checklist: List<ChecklistDump> = emptyList(),
    val budget: List<BudgetDump> = emptyList(),
    val budgetCategories: List<BudgetCategoryDump> = emptyList(),
    val schedule: List<ScheduleDump> = emptyList(),
    val scheduleDays: List<ScheduleDayDump> = emptyList(),
    val entries: List<EntryDump> = emptyList(),
)

/**
 * What one day of the trip is about, in a line.
 *
 * The date is local and travels as ISO text, unlike an event's start: it is the heading a
 * person reads and not a moment anything is ordered against.
 */
@Serializable
data class ScheduleDayDump(
    val date: String,
    val summary: String,
)

/**
 * One thing that happens at a known time, with whatever it is expected to cost.
 *
 * [startsAt] is a moment — seconds since the epoch — and not a local date-time like an
 * entry's, which is what lets a flight land at an earlier hour than it left.
 *
 * A link to the event before travels as a **position in this list** rather than as an id,
 * ids being the one thing a dump cannot promise: the rows land in a database that has its
 * own. The list is in time order and an event always follows an earlier one, so the index
 * always points backwards.
 */
@Serializable
data class ScheduleDump(
    val title: String = "",
    val notes: String = "",
    val startsAt: Long,
    val durationMinutes: Int,
    val startsInZone: String? = null,
    val endsInZone: String? = null,
    val followsIndex: Int? = null,
    val category: NameRef? = null,
    val amountMilli: Long? = null,
    val currencyCode: String? = null,
    /** How it is to be paid for, when that has been settled. */
    val paymentMethod: NameRef? = null,
)

/** One line of what the trip was expected to cost. */
@Serializable
data class BudgetDump(
    val category: NameRef,
    val title: String = "",
    val notes: String = "",
    val quantityMilli: Long? = null,
    val amountMilli: Long,
    val currencyCode: String,
    /** How the line is to be paid for, when that has been settled. */
    val paymentMethod: NameRef? = null,
)

/** What was settled about one category of the trip's budget. */
@Serializable
data class BudgetCategoryDump(
    val category: NameRef,
    val isSpendingClosed: Boolean = false,
    val isPlanningClosed: Boolean = false,
)

/** One thing to do before the trip, with its section as the text it is. */
@Serializable
data class ChecklistDump(
    val section: String,
    val title: String,
    val quantityMilli: Long? = null,
    val sortOrder: Int = 0,
    val isDone: Boolean = false,
    val isSkipped: Boolean = false,
)

/** A rate of the trip. Both ends by code, which is what the rows hold anyway. */
@Serializable
data class RateDump(
    val fromCurrencyCode: String,
    val toCurrencyCode: String,
    val rateMilli: Long,
    val multiplier: Long = 1,
    val note: String = "",
    /** Whether the note is a lookup's stamp; see `TripRate`. */
    val noteFromLookup: Boolean = false,
)

@Serializable
data class WalletDump(
    /** As stored: null for a wallet that goes by the name its pair gives it. */
    val name: String? = null,
    val paymentMethod: NameRef,
    val currencyCode: String,
    val openingBalanceMilli: Long = 0,
    val lastUsedAt: String? = null,
    val recounts: List<RecountDump> = emptyList(),
)

@Serializable
data class RecountDump(
    val dateTime: String,
    val amountMilli: Long,
    val notes: String = "",
)

/**
 * How an entry points at a wallet of its trip. A wallet with a name of its own is found
 * by that name; one without is found by its payment method and currency, among the
 * wallets that have no name either.
 */
@Serializable
data class WalletRef(
    val name: String? = null,
    val paymentMethod: NameRef,
    val currencyCode: String,
)

@Serializable
data class EntryDump(
    val dateTime: String,
    val category: NameRef,
    val title: String = "",
    val place: String = "",
    val notes: String = "",
    val quantityMilli: Long? = null,
    val expenseWallet: WalletRef? = null,
    val expenseAmountMilli: Long? = null,
    val expenseCurrencyCode: String? = null,
    val incomeWallet: WalletRef? = null,
    val incomeAmountMilli: Long? = null,
    val incomeCurrencyCode: String? = null,
    val expenseSettlementAmountMilli: Long? = null,
    val incomeSettlementAmountMilli: Long? = null,
    val photoFile: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

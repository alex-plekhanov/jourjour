package app.jourjour.core.export

import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.Entry
import app.jourjour.data.entity.EntryDetails
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.data.entity.TripWallet
import app.jourjour.data.entity.TripWalletDetails
import java.time.LocalDateTime

/** What the export tests build their tables out of. */
typealias TestEntry = EntryDetails

val usd = Currency("USD", "US Dollar", "$", decimals = 2)
val jpy = Currency("JPY", "Japanese Yen", "¥", decimals = 0)
val kwd = Currency("KWD", "Kuwaiti Dinar", "د.ك", decimals = 3)
val cash = PaymentMethod(id = 1, name = "Cash", icon = "💵", fixedCurrency = true)
val card = PaymentMethod(id = 2, name = "Card", icon = "💳")
val dining = Category(id = 1, name = "Restaurants", icon = "🍽")
val exchange = Category(id = 2, name = "Exchange", icon = "💱", isExpense = true, isIncome = true)
val refunds = Category(id = 3, name = "Refunds", icon = "↩", isExpense = false, isIncome = true)
val fuel = Category(id = 4, name = "Fuel", icon = "⛽", trackQuantity = true)

/** A cash wallet in dollars and a card account in dollars, as a trip would have them. */
val cashWallet = wallet(id = 1, paymentMethod = cash, currency = usd, name = "Cash")
val cardWallet = wallet(id = 2, paymentMethod = card, currency = usd, name = "Card")

fun wallet(
    id: Long,
    paymentMethod: PaymentMethod,
    currency: Currency,
    name: String? = null,
) = TripWalletDetails(
    wallet = TripWallet(
        id = id,
        tripId = 1,
        paymentMethodId = paymentMethod.id,
        currencyCode = currency.code,
        name = name,
    ),
    paymentMethod = paymentMethod,
    currency = currency,
)

/** The name a wallet goes by in a file, as the app resolves it outside of a test. */
fun walletLabel(details: TripWalletDetails): String =
    details.wallet.name ?: "${details.paymentMethod.name} (${details.currency.name})"

/**
 * An entry with only the halves the test cares about filled in. The relations follow the
 * columns, as Room would fill them: no amount means no wallet and no currency.
 */
@Suppress("LongParameterList")
fun entry(
    id: Long = 1,
    category: Category = dining,
    dateTime: LocalDateTime = LocalDateTime.of(2026, 4, 3, 12, 30),
    title: String = "",
    place: String = "",
    notes: String = "",
    quantityMilli: Long? = null,
    expenseAmountMilli: Long? = null,
    expenseCurrency: Currency? = usd,
    expenseWallet: TripWalletDetails = cashWallet,
    incomeAmountMilli: Long? = null,
    settlementMilli: Long? = null,
    latitude: Double? = null,
    longitude: Double? = null,
    photoFile: String? = null,
): TestEntry = EntryDetails(
    entry = Entry(
        id = id,
        tripId = 1,
        categoryId = category.id,
        dateTime = dateTime,
        title = title,
        place = place,
        notes = notes,
        quantityMilli = quantityMilli,
        expenseWalletId = expenseWallet.wallet.id.takeIf { expenseAmountMilli != null },
        expenseAmountMilli = expenseAmountMilli,
        expenseCurrencyCode = expenseCurrency?.code?.takeIf { expenseAmountMilli != null },
        incomeWalletId = cardWallet.wallet.id.takeIf { incomeAmountMilli != null },
        incomeAmountMilli = incomeAmountMilli,
        incomeCurrencyCode = usd.code.takeIf { incomeAmountMilli != null },
        expenseSettlementAmountMilli = settlementMilli,
        latitude = latitude,
        longitude = longitude,
        photoFile = photoFile,
    ),
    category = category,
    expenseWallet = expenseWallet.takeIf { expenseAmountMilli != null },
    incomeWallet = cardWallet.takeIf { incomeAmountMilli != null },
    expenseCurrency = expenseCurrency?.takeIf { expenseAmountMilli != null },
    incomeCurrency = usd.takeIf { incomeAmountMilli != null },
)

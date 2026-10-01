package app.jourjour.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.StringRes
import androidx.sqlite.db.SupportSQLiteDatabase
import app.jourjour.R
import app.jourjour.data.entity.IconTarget
import java.util.Currency as JavaCurrency

/**
 * The contents the dictionaries start out with.
 *
 * Written straight into [SupportSQLiteDatabase] from the creation callback, which means
 * synchronously and before any query can return. Going through the DAOs is not an option:
 * touching the database from inside that callback has to be deferred to a background
 * coroutine, and the first screen would meanwhile see empty dictionaries.
 *
 * Names come from string resources. Both go into the database: the text in the current
 * language as a fallback, and the resource name in `name_key`, through which the name is
 * looked up again when the device language changes. See
 * [app.jourjour.core.text.resolveName].
 */
object DefaultData {

    /**
     * True while the dictionaries have never been filled in. Currencies stand for the lot:
     * they cannot all be deleted from the app, since a trip or an entry holds on to them.
     */
    fun isEmpty(db: SupportSQLiteDatabase): Boolean =
        db.query("SELECT EXISTS(SELECT 1 FROM currencies)").use { cursor ->
            !cursor.moveToFirst() || cursor.getInt(0) == 0
        }

    fun populate(db: SupportSQLiteDatabase, context: Context) {
        insertCurrencies(db, context)
        insertPaymentMethods(db, context)
        insertCategories(db, context)
        insertIconPresets(db)
    }

    // region Icons offered when picking one

    /**
     * The icons a picker starts out with. Anything typed by hand is added to the same
     * table, so the list a person sees grows with use.
     */
    private val paymentMethodIcons = listOf("💵", "💳", "🔳", "💸")

    private val categoryIcons = listOf(
        "🍽", "🛒", "🛏️", "🚌", "⛽", "🚗", "📱", "🎭", "🛍", "🎁", "💊", "💱",
        "🧾", "✈", "🚕", "🚆", "🏛", "🎟", "☕", "🍺", "🏥", "🧺", "🎣", "⛱", "🏨",
    )

    private fun insertIconPresets(db: SupportSQLiteDatabase) {
        insertIconPresets(db, IconTarget.PAYMENT_METHOD, paymentMethodIcons)
        insertIconPresets(db, IconTarget.CATEGORY, categoryIcons)
    }

    private fun insertIconPresets(
        db: SupportSQLiteDatabase,
        target: IconTarget,
        icons: List<String>,
    ) {
        icons.forEachIndexed { index, emoji ->
            val values = ContentValues().apply {
                put("target", target.name)
                put("emoji", emoji)
                put("sort_order", index)
            }
            db.insert("icon_presets", SQLiteDatabase.CONFLICT_IGNORE, values)
        }
    }

    // endregion

    // region Currencies

    private class CurrencySeed(val code: String, val symbol: String, @param:StringRes val nameRes: Int)

    /**
     * The currencies a trip is likely to need, grouped by where they are spent so that the
     * list stays scannable without searching.
     *
     * Every code here is a currency in circulation. It is deliberately not the whole of
     * ISO 4217 and not what `java.util.Currency` knows either: that list carries some fifty
     * withdrawn codes — marks, pesetas, francs, the pre-redenomination rubles — which have
     * no business being offered for a trip taken now. Anything missing is a currency a
     * person can add by hand.
     *
     * A symbol is written out only where it is a glyph people recognise; where it is not,
     * the code stands in, as it does for the Swiss franc.
     */
    private val currencies = listOf(
        // Reserve and major currencies
        CurrencySeed("USD", "$", R.string.currency_usd),
        CurrencySeed("EUR", "€", R.string.currency_eur),
        CurrencySeed("GBP", "£", R.string.currency_gbp),
        CurrencySeed("CHF", "CHF", R.string.currency_chf),
        CurrencySeed("JPY", "¥", R.string.currency_jpy),
        CurrencySeed("CNY", "¥", R.string.currency_cny),
        CurrencySeed("CAD", "C$", R.string.currency_cad),
        CurrencySeed("AUD", "A$", R.string.currency_aud),
        CurrencySeed("NZD", "NZ$", R.string.currency_nzd),

        // Russia and the countries around it
        CurrencySeed("RUB", "₽", R.string.currency_rub),
        CurrencySeed("UAH", "₴", R.string.currency_uah),
        CurrencySeed("BYN", "Br", R.string.currency_byn),
        CurrencySeed("MDL", "L", R.string.currency_mdl),
        CurrencySeed("GEL", "₾", R.string.currency_gel),
        CurrencySeed("AMD", "֏", R.string.currency_amd),
        CurrencySeed("AZN", "₼", R.string.currency_azn),
        CurrencySeed("KZT", "₸", R.string.currency_kzt),
        CurrencySeed("UZS", "soʻm", R.string.currency_uzs),
        CurrencySeed("KGS", "сом", R.string.currency_kgs),
        CurrencySeed("TJS", "SM", R.string.currency_tjs),
        CurrencySeed("TMT", "TMT", R.string.currency_tmt),

        // The rest of Europe
        CurrencySeed("SEK", "kr", R.string.currency_sek),
        CurrencySeed("NOK", "kr", R.string.currency_nok),
        CurrencySeed("DKK", "kr", R.string.currency_dkk),
        CurrencySeed("ISK", "kr", R.string.currency_isk),
        CurrencySeed("PLN", "zł", R.string.currency_pln),
        CurrencySeed("CZK", "Kč", R.string.currency_czk),
        CurrencySeed("HUF", "Ft", R.string.currency_huf),
        CurrencySeed("RON", "lei", R.string.currency_ron),
        CurrencySeed("BGN", "лв", R.string.currency_bgn),
        CurrencySeed("RSD", "дин.", R.string.currency_rsd),
        CurrencySeed("BAM", "KM", R.string.currency_bam),
        CurrencySeed("MKD", "ден", R.string.currency_mkd),
        CurrencySeed("ALL", "L", R.string.currency_all),
        CurrencySeed("TRY", "₺", R.string.currency_try),

        // The Middle East
        CurrencySeed("AED", "د.إ", R.string.currency_aed),
        CurrencySeed("SAR", "﷼", R.string.currency_sar),
        CurrencySeed("QAR", "﷼", R.string.currency_qar),
        CurrencySeed("OMR", "ر.ع.", R.string.currency_omr),
        CurrencySeed("KWD", "د.ك", R.string.currency_kwd),
        CurrencySeed("BHD", "د.ب", R.string.currency_bhd),
        CurrencySeed("JOD", "د.ا", R.string.currency_jod),
        CurrencySeed("ILS", "₪", R.string.currency_ils),

        // South and South-East Asia
        CurrencySeed("INR", "₹", R.string.currency_inr),
        CurrencySeed("PKR", "₨", R.string.currency_pkr),
        CurrencySeed("BDT", "৳", R.string.currency_bdt),
        CurrencySeed("LKR", "Rs", R.string.currency_lkr),
        CurrencySeed("NPR", "Rs", R.string.currency_npr),
        CurrencySeed("THB", "฿", R.string.currency_thb),
        CurrencySeed("VND", "₫", R.string.currency_vnd),
        CurrencySeed("KHR", "៛", R.string.currency_khr),
        CurrencySeed("LAK", "₭", R.string.currency_lak),
        CurrencySeed("IDR", "Rp", R.string.currency_idr),
        CurrencySeed("MYR", "RM", R.string.currency_myr),
        CurrencySeed("SGD", "S$", R.string.currency_sgd),
        CurrencySeed("PHP", "₱", R.string.currency_php),

        // East Asia
        CurrencySeed("HKD", "HK$", R.string.currency_hkd),
        CurrencySeed("TWD", "NT$", R.string.currency_twd),
        CurrencySeed("KRW", "₩", R.string.currency_krw),
        CurrencySeed("MNT", "₮", R.string.currency_mnt),

        // Africa
        CurrencySeed("EGP", "E£", R.string.currency_egp),
        CurrencySeed("MAD", "DH", R.string.currency_mad),
        CurrencySeed("TND", "د.ت", R.string.currency_tnd),
        CurrencySeed("ZAR", "R", R.string.currency_zar),

        // The Americas
        CurrencySeed("BRL", "R$", R.string.currency_brl),
        CurrencySeed("MXN", "MX$", R.string.currency_mxn),
        CurrencySeed("ARS", "AR$", R.string.currency_ars),
        CurrencySeed("CLP", "CL$", R.string.currency_clp),
    )

    private fun insertCurrencies(db: SupportSQLiteDatabase, context: Context) {
        currencies.forEachIndexed { index, seed ->
            val values = ContentValues().apply {
                put("code", seed.code)
                put("name", context.getString(seed.nameRes))
                put("symbol", seed.symbol)
                put("decimals", fractionDigits(seed.code))
                put("name_key", context.resources.getResourceEntryName(seed.nameRes))
                put("sort_order", index)
            }
            db.insert("currencies", SQLiteDatabase.CONFLICT_IGNORE, values)
        }
    }

    /** Decimal places come from ICU: JPY has none, KWD has three. */
    private fun fractionDigits(code: String): Int =
        runCatching { JavaCurrency.getInstance(code).defaultFractionDigits }
            .getOrDefault(2)
            .coerceIn(0, 3)

    // endregion

    // region Payment methods

    private class PaymentMethodSeed(
        @param:StringRes val nameRes: Int,
        val icon: String,
        val fixedCurrency: Boolean = false,
    )

    /**
     * Cash cannot be handed over in a currency it is not; a card lets the bank convert.
     * That difference is the whole of what a payment method decides.
     */
    private val paymentMethods = listOf(
        PaymentMethodSeed(R.string.payment_method_cash, "💵", fixedCurrency = true),
        PaymentMethodSeed(R.string.payment_method_card, "💳"),
    )

    private fun insertPaymentMethods(db: SupportSQLiteDatabase, context: Context) {
        paymentMethods.forEachIndexed { index, seed ->
            val values = ContentValues().apply {
                put("name", context.getString(seed.nameRes))
                put("icon", seed.icon)
                put("fixed_currency", seed.fixedCurrency)
                put("name_key", context.resources.getResourceEntryName(seed.nameRes))
                put("sort_order", index)
            }
            db.insert("payment_methods", SQLiteDatabase.CONFLICT_IGNORE, values)
        }
    }

    // endregion

    // region Categories and favourites

    private class CategorySeed(
        @param:StringRes val nameRes: Int,
        val icon: String,
        val isExpense: Boolean = true,
        val isIncome: Boolean = false,
        val trackQuantity: Boolean = false,
        val favorites: List<Int> = emptyList(),
    )

    private val categories = listOf(
        CategorySeed(
            R.string.category_groceries, "🛒",
            favorites = listOf(R.string.favorite_water),
        ),
        CategorySeed(
            R.string.category_dining, "🍽",
            favorites = listOf(
                R.string.favorite_breakfast,
                R.string.favorite_lunch,
                R.string.favorite_dinner,
                R.string.favorite_coffee,
                R.string.favorite_tips,
            ),
        ),
        CategorySeed(
            // A bed rather than a hotel: at the size an icon is read at, the hotel's
            // windows and its letter are a smudge, and the bed is three flat shapes.
            R.string.category_accommodation, "🛏️",
            // Nights, which is how a room is priced and how one stay is compared with
            // another: two hundred a night says something a thousand for the stay does not.
            trackQuantity = true,
            favorites = listOf(
                R.string.favorite_hotel,
                R.string.favorite_apartment,
                R.string.favorite_hostel,
                R.string.favorite_camping,
            ),
        ),
        CategorySeed(
            R.string.category_transport, "🚌",
            favorites = listOf(
                R.string.favorite_metro,
                R.string.favorite_bus,
                R.string.favorite_taxi,
                R.string.favorite_train,
                R.string.favorite_plane,
                R.string.favorite_ferry,
            ),
        ),
        CategorySeed(
            R.string.category_fuel, "⛽",
            trackQuantity = true,
            favorites = listOf(
                R.string.favorite_petrol_92,
                R.string.favorite_petrol_95,
                R.string.favorite_petrol_98,
                R.string.favorite_diesel,
            ),
        ),
        CategorySeed(
            R.string.category_car, "🚗",
            favorites = listOf(
                R.string.favorite_car_rental,
                R.string.favorite_parking,
                R.string.favorite_toll_road,
                R.string.favorite_car_wash,
            ),
        ),
        CategorySeed(
            R.string.category_connectivity, "📱",
            favorites = listOf(R.string.favorite_sim_card, R.string.favorite_top_up),
        ),
        CategorySeed(
            R.string.category_entertainment, "🎭",
            favorites = listOf(R.string.favorite_ticket),
        ),
        CategorySeed(R.string.category_shopping, "🛍"),
        CategorySeed(
            R.string.category_souvenirs, "💝",
            favorites = listOf(R.string.favorite_magnet),
        ),
        // An expense: a gift is bought far more often than it is received, and the one
        // that is received rarely has a price to write down. A donation belongs here for
        // the same reason a gift does — money handed over with nothing coming back.
        CategorySeed(
            R.string.category_gifts, "🎁",
            favorites = listOf(R.string.favorite_donation),
        ),
        CategorySeed(R.string.category_health, "💊"),
        CategorySeed(
            R.string.category_fees, "📋",
            favorites = listOf(
                R.string.favorite_visa,
                R.string.favorite_permit,
                R.string.favorite_insurance,
                R.string.favorite_fine,
            ),
        ),
        // Money coming back rather than money earned: cashback, tax free, a purchase taken
        // back to the shop. A gift used to stand here, which read as an expense — buying
        // one — as often as it read as receiving one.
        CategorySeed(
            R.string.category_refunds, "💰",
            isExpense = false, isIncome = true,
            favorites = listOf(
                R.string.favorite_cashback,
                R.string.favorite_tax_free,
                R.string.favorite_returned_purchase,
            ),
        ),
        CategorySeed(
            R.string.category_currency_exchange, "💱",
            isExpense = true, isIncome = true,
            favorites = listOf(R.string.favorite_currency_exchange),
        ),
        // Money that never left: it went out of one wallet and into another. Both halves,
        // like an exchange, because that is exactly what a withdrawal is — and named for
        // the movement rather than for the cash machine, since a transfer between cards or
        // a top-up from a bank account is the same entry.
        CategorySeed(
            R.string.category_transfer, "🔁",
            isExpense = true, isIncome = true,
            favorites = listOf(
                R.string.favorite_cash_withdrawal,
                R.string.favorite_card_top_up,
            ),
        ),
        // Getting to the place and home again, kept apart from the transport that moves a
        // person about once they are there. It is usually the largest single thing a trip
        // costs, and counted together with the buses it swallows them whole: a fortnight of
        // metro fares next to two air tickets reads as nothing at all.
        CategorySeed(
            R.string.category_getting_there, "✈️",
            favorites = listOf(R.string.favorite_plane),
        ),
        CategorySeed(R.string.category_other, "🧾", isExpense = true, isIncome = false),
    )

    private fun insertCategories(db: SupportSQLiteDatabase, context: Context) {
        categories.forEachIndexed { index, seed ->
            val values = ContentValues().apply {
                put("name", context.getString(seed.nameRes))
                put("icon", seed.icon)
                put("is_expense", seed.isExpense)
                put("is_income", seed.isIncome)
                put("track_quantity", seed.trackQuantity)
                put("name_key", context.resources.getResourceEntryName(seed.nameRes))
                put("sort_order", index)
            }
            val categoryId = db.insert("categories", SQLiteDatabase.CONFLICT_IGNORE, values)
            if (categoryId == -1L) return@forEachIndexed

            seed.favorites.forEachIndexed { favoriteIndex, favoriteRes ->
                val favorite = ContentValues().apply {
                    put("category_id", categoryId)
                    put("name", context.getString(favoriteRes))
                    put("name_key", context.resources.getResourceEntryName(favoriteRes))
                    put("sort_order", favoriteIndex)
                }
                db.insert("category_favorites", SQLiteDatabase.CONFLICT_IGNORE, favorite)
            }
        }
    }

    // endregion
}
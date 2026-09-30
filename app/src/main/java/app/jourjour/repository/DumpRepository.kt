package app.jourjour.repository

import androidx.room.withTransaction
import app.jourjour.core.dump.CategoryDump
import app.jourjour.core.dump.BudgetCategoryDump
import app.jourjour.core.dump.BudgetDump
import app.jourjour.core.dump.ScheduleDayDump
import app.jourjour.core.dump.ScheduleDump
import app.jourjour.core.dump.ChecklistDump
import app.jourjour.core.dump.CurrencyDump
import app.jourjour.core.dump.Dump
import app.jourjour.core.dump.EntryDump
import app.jourjour.core.dump.FavoriteDump
import app.jourjour.core.dump.NameRef
import app.jourjour.core.dump.PaymentMethodDump
import app.jourjour.core.dump.RateDump
import app.jourjour.core.dump.RecountDump
import app.jourjour.core.dump.TripDump
import app.jourjour.core.dump.WalletDump
import app.jourjour.core.dump.WalletRef
import app.jourjour.data.dao.BalanceSnapshotDao
import app.jourjour.data.dao.CategoryDao
import app.jourjour.data.dao.CategoryFavoriteDao
import app.jourjour.data.dao.BudgetDao
import app.jourjour.data.dao.ScheduleDao
import app.jourjour.data.dao.ChecklistDao
import app.jourjour.data.dao.CurrencyDao
import app.jourjour.data.dao.EntryDao
import app.jourjour.data.dao.PaymentMethodDao
import app.jourjour.data.dao.TripDao
import app.jourjour.data.dao.TripRateDao
import app.jourjour.data.dao.TripWalletDao
import app.jourjour.data.db.AppDatabase
import app.jourjour.data.entity.BalanceSnapshot
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.CategoryFavorite
import app.jourjour.data.entity.BudgetItem
import app.jourjour.data.entity.ScheduleDayNote
import app.jourjour.data.entity.ScheduleEvent
import app.jourjour.data.entity.ChecklistItem
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.Entry
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.data.entity.Trip
import app.jourjour.data.entity.TripBudgetCategory
import app.jourjour.data.entity.TripRate
import app.jourjour.data.entity.TripWallet
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The whole database out as a [Dump], and a [Dump] back in.
 *
 * Reading one in adds and never changes: a dictionary row that is already there is left
 * as it is, a trip that is already there — the same name and dates — is passed over with
 * everything in it, and the rest is written as the file has it. Nothing is checked beyond
 * what the rows need to be written at all: this is a tool for moving data between
 * installs while the app is being developed, not a second import.
 */
@Singleton
class DumpRepository @Inject constructor(
    private val db: AppDatabase,
    private val currencyDao: CurrencyDao,
    private val paymentMethodDao: PaymentMethodDao,
    private val categoryDao: CategoryDao,
    private val favoriteDao: CategoryFavoriteDao,
    private val tripDao: TripDao,
    private val walletDao: TripWalletDao,
    private val rateDao: TripRateDao,
    private val checklistDao: ChecklistDao,
    private val budgetDao: BudgetDao,
    private val scheduleDao: ScheduleDao,
    private val entryDao: EntryDao,
    private val snapshotDao: BalanceSnapshotDao,
) {

    /** How a dump was taken in. */
    sealed interface Outcome {
        /**
         * [added] trips were written; [skipped] were already there and left alone. The
         * photos the written rows point at are named, for the caller to put in place.
         */
        data class Done(
            val added: Int,
            val skipped: Int,
            val receipts: Set<String> = emptySet(),
            val covers: Set<String> = emptySet(),
        ) : Outcome

        /** The rows came from a newer schema than this build knows; nothing was written. */
        data class NewerVersion(val found: Int, val expected: Int) : Outcome
    }

    suspend fun export(): Dump {
        val paymentMethods = paymentMethodDao.getAll().associateBy { it.id }
        return Dump(
            databaseVersion = AppDatabase.VERSION,
            exportedAt = LocalDateTime.now().toString(),
            currencies = currencyDao.getAll().map { it.dump() },
            paymentMethods = paymentMethods.values.map { it.dump() },
            categories = categoryDao.getAll().map { category ->
                category.dump(favoriteDao.getByCategory(category.id))
            },
            trips = tripDao.getAll().map { trip -> trip.dump(paymentMethods) },
        )
    }

    private suspend fun Trip.dump(paymentMethods: Map<Long, PaymentMethod>): TripDump {
        val wallets = walletDao.getByTrip(id)
        val walletRefs = wallets.associate { details ->
            details.wallet.id to WalletRef(
                name = details.wallet.name,
                paymentMethod = details.paymentMethod.ref(),
                currencyCode = details.wallet.currencyCode,
            )
        }
        val categories = categoryDao.getAll().associateBy { it.id }
        return TripDump(
            name = name,
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            createdAt = createdAt.toString(),
            photoFile = photoFile,
            wallets = wallets.map { details ->
                WalletDump(
                    name = details.wallet.name,
                    paymentMethod = details.paymentMethod.ref(),
                    currencyCode = details.wallet.currencyCode,
                    openingBalanceMilli = details.wallet.openingBalanceMilli,
                    lastUsedAt = details.wallet.lastUsedAt?.toString(),
                    recounts = snapshotDao.getByWallet(details.wallet.id).map { it.dump() },
                )
            },
            rates = rateDao.getByTrip(id).map { rate ->
                RateDump(
                    fromCurrencyCode = rate.fromCurrencyCode,
                    toCurrencyCode = rate.toCurrencyCode,
                    rateMilli = rate.rateMilli,
                    multiplier = rate.multiplier,
                )
            },
            checklist = checklistDao.getByTrip(id).map { item ->
                ChecklistDump(
                    section = item.section,
                    title = item.title,
                    isDone = item.isDone,
                    isSkipped = item.isSkipped,
                )
            },
            budget = budgetDao.getByTrip(id).map { details ->
                BudgetDump(
                    category = details.category.ref(),
                    title = details.item.title,
                    notes = details.item.notes,
                    quantityMilli = details.item.quantityMilli,
                    amountMilli = details.item.amountMilli,
                    currencyCode = details.item.currencyCode,
                )
            },
            budgetCategories = budgetDao.categoriesOf(id).map { settled ->
                BudgetCategoryDump(
                    category = categories.getValue(settled.categoryId).ref(),
                    isSpendingClosed = settled.isSpendingClosed,
                    isPlanningClosed = settled.isPlanningClosed,
                )
            },
            schedule = scheduleDao.getByTrip(id).let { events ->
                // The link travels as a position in this very list, so the positions have
                // to be known before the first row is written out.
                val positions = events.withIndex().associate { (at, it) -> it.event.id to at }
                events.map { details ->
                    ScheduleDump(
                        title = details.event.title,
                        notes = details.event.notes,
                        startsAt = details.event.startsAt,
                        durationMinutes = details.event.durationMinutes,
                        startsInZone = details.event.startsInZone,
                        endsInZone = details.event.endsInZone,
                        followsIndex = details.event.followsId?.let(positions::get),
                        category = details.category?.ref(),
                        amountMilli = details.event.amountMilli,
                        currencyCode = details.event.currencyCode,
                    )
                }
            },
            scheduleDays = scheduleDao.daysOf(id).map { day ->
                ScheduleDayDump(date = day.date.toString(), summary = day.summary)
            },
            entries = entryDao.getByTrip(id).map { entry ->
                entry.dump(
                    category = categories.getValue(entry.categoryId).ref(),
                    expenseWallet = entry.expenseWalletId?.let(walletRefs::getValue),
                    incomeWallet = entry.incomeWalletId?.let(walletRefs::getValue),
                )
            },
        )
    }

    /**
     * Writes the dump in, all of it or none. A trip is known by its name and its dates;
     * one the database already has is skipped whole.
     *
     * @param receipts the receipt photos that came with the dump, by name; a row keeps its
     *   photo only where the file is at hand, since a name pointing at nothing is worse
     *   than none.
     * @param covers the same for the covers of trips.
     */
    suspend fun import(
        dump: Dump,
        receipts: Set<String> = emptySet(),
        covers: Set<String> = emptySet(),
    ): Outcome {
        // An older dump is read as it is. Every schema change so far has only added —
        // a table, a column with a default — so the fields an old file carries still mean
        // what they meant, and the ones it does not carry take their defaults. What it
        // cannot do is read forward: a file from a newer build may name a column this one
        // has never heard of, and there is nothing sensible to do with it.
        //
        // This holds only while the changes keep adding. The first one that renames or
        // drops something needs either a migration for dumps or a floor below which they
        // are refused; until then the version is a ceiling and nothing more.
        if (dump.databaseVersion > AppDatabase.VERSION) {
            return Outcome.NewerVersion(
                found = dump.databaseVersion,
                expected = AppDatabase.VERSION,
            )
        }
        return db.withTransaction {
            importDictionaries(dump)
            val paymentMethods = paymentMethodDao.getAll()
            val categories = categoryDao.getAll()
            val existing = tripDao.getAll().map { Triple(it.name, it.startDate, it.endDate) }.toSet()

            var added = 0
            var skipped = 0
            val photos = Photos(receipts, covers)
            dump.trips.forEach { trip ->
                val key = Triple(trip.name, LocalDate.parse(trip.startDate), LocalDate.parse(trip.endDate))
                if (key in existing) {
                    skipped++
                } else {
                    importTrip(trip, paymentMethods, categories, photos)
                    added++
                }
            }
            Outcome.Done(added, skipped, photos.receiptsUsed, photos.coversUsed)
        }
    }

    /** What is not there yet is added; what is there is left as it is. */
    private suspend fun importDictionaries(dump: Dump) {
        val currencies = currencyDao.getAll().map { it.code }.toSet()
        dump.currencies.filter { it.code !in currencies }.forEach { currencyDao.insert(it.row()) }

        val paymentMethods = paymentMethodDao.getAll()
        dump.paymentMethods
            .filter { candidate -> paymentMethods.none { it.matches(candidate.ref()) } }
            .forEach { paymentMethodDao.insert(it.row()) }

        val categories = categoryDao.getAll()
        dump.categories
            .filter { candidate -> categories.none { it.matches(candidate.ref()) } }
            .forEach { candidate ->
                val id = categoryDao.insert(candidate.row())
                favoriteDao.insertAll(candidate.favorites.map { it.row(id) })
            }
    }

    /** Which photos came with the dump, and which of them the rows written so far use. */
    private class Photos(private val receipts: Set<String>, private val covers: Set<String>) {
        val receiptsUsed = mutableSetOf<String>()
        val coversUsed = mutableSetOf<String>()

        fun receipt(name: String?): String? = name?.takeIf { it in receipts }?.also { receiptsUsed += it }

        fun cover(name: String?): String? = name?.takeIf { it in covers }?.also { coversUsed += it }
    }

    private suspend fun importTrip(
        trip: TripDump,
        paymentMethods: List<PaymentMethod>,
        categories: List<Category>,
        photos: Photos,
    ) {
        val tripId = tripDao.insert(
            Trip(
                name = trip.name,
                startDate = LocalDate.parse(trip.startDate),
                endDate = LocalDate.parse(trip.endDate),
                photoFile = photos.cover(trip.photoFile),
                createdAt = LocalDateTime.parse(trip.createdAt),
            )
        )

        val wallets = trip.wallets.map { wallet ->
            val paymentMethod = paymentMethods.first { it.matches(wallet.paymentMethod) }
            val id = walletDao.insert(
                TripWallet(
                    tripId = tripId,
                    paymentMethodId = paymentMethod.id,
                    currencyCode = wallet.currencyCode,
                    name = wallet.name,
                    openingBalanceMilli = wallet.openingBalanceMilli,
                    lastUsedAt = wallet.lastUsedAt?.let(LocalDateTime::parse),
                )
            )
            wallet.recounts.forEach { snapshotDao.insert(it.row(id)) }
            ImportedWallet(id, wallet.name, paymentMethod, wallet.currencyCode)
        }

        trip.rates.forEach { rate ->
            rateDao.insert(
                TripRate(
                    tripId = tripId,
                    fromCurrencyCode = rate.fromCurrencyCode,
                    toCurrencyCode = rate.toCurrencyCode,
                    rateMilli = rate.rateMilli,
                    multiplier = rate.multiplier,
                )
            )
        }

        trip.checklist.forEach { item ->
            checklistDao.insert(
                ChecklistItem(
                    tripId = tripId,
                    section = item.section,
                    title = item.title,
                    isDone = item.isDone,
                    isSkipped = item.isSkipped,
                )
            )
        }

        trip.budget.forEach { line ->
            budgetDao.insert(
                BudgetItem(
                    tripId = tripId,
                    categoryId = categories.first { it.matches(line.category) }.id,
                    title = line.title,
                    notes = line.notes,
                    quantityMilli = line.quantityMilli,
                    amountMilli = line.amountMilli,
                    currencyCode = line.currencyCode,
                )
            )
        }

        trip.budgetCategories.forEach { settled ->
            budgetDao.insert(
                TripBudgetCategory(
                    tripId = tripId,
                    categoryId = categories.first { it.matches(settled.category) }.id,
                    isSpendingClosed = settled.isSpendingClosed,
                    isPlanningClosed = settled.isPlanningClosed,
                )
            )
        }

        // Two passes: every event has to have its new id before any of them can point at
        // another, and the dump's indices are what say which.
        val eventIds = trip.schedule.map { event ->
            scheduleDao.insert(
                ScheduleEvent(
                    tripId = tripId,
                    title = event.title,
                    notes = event.notes,
                    startsAt = event.startsAt,
                    durationMinutes = event.durationMinutes,
                    startsInZone = event.startsInZone,
                    endsInZone = event.endsInZone,
                    categoryId = event.category?.let { ref -> categories.first { it.matches(ref) }.id },
                    amountMilli = event.amountMilli,
                    currencyCode = event.currencyCode,
                )
            )
        }
        trip.schedule.forEachIndexed { at, event ->
            val leader = event.followsIndex?.let(eventIds::getOrNull) ?: return@forEachIndexed
            scheduleDao.setFollows(eventIds[at], leader)
        }

        trip.scheduleDays.forEach { day ->
            scheduleDao.describeDay(
                ScheduleDayNote(
                    tripId = tripId,
                    date = LocalDate.parse(day.date),
                    summary = day.summary,
                )
            )
        }

        trip.entries.forEach { entry ->
            entryDao.insert(
                Entry(
                    tripId = tripId,
                    categoryId = categories.first { it.matches(entry.category) }.id,
                    dateTime = LocalDateTime.parse(entry.dateTime),
                    title = entry.title,
                    place = entry.place,
                    notes = entry.notes,
                    quantityMilli = entry.quantityMilli,
                    expenseWalletId = entry.expenseWallet?.let { wallets.find(it).id },
                    expenseAmountMilli = entry.expenseAmountMilli,
                    expenseCurrencyCode = entry.expenseCurrencyCode,
                    incomeWalletId = entry.incomeWallet?.let { wallets.find(it).id },
                    incomeAmountMilli = entry.incomeAmountMilli,
                    incomeCurrencyCode = entry.incomeCurrencyCode,
                    expenseSettlementAmountMilli = entry.expenseSettlementAmountMilli,
                    incomeSettlementAmountMilli = entry.incomeSettlementAmountMilli,
                    photoFile = photos.receipt(entry.photoFile),
                    latitude = entry.latitude,
                    longitude = entry.longitude,
                )
            )
        }
    }

    /** A wallet just written, with what an entry of the same file points at it by. */
    private class ImportedWallet(
        val id: Long,
        val name: String?,
        val paymentMethod: PaymentMethod,
        val currencyCode: String,
    )

    /**
     * By name where the wallet has one; otherwise by the pair, among the wallets that go
     * by their pair too — exactly the two ways a wallet is told apart on a trip.
     */
    private fun List<ImportedWallet>.find(ref: WalletRef): ImportedWallet =
        if (ref.name != null) {
            first { it.name == ref.name }
        } else {
            first {
                it.name == null && it.paymentMethod.matches(ref.paymentMethod) && it.currencyCode == ref.currencyCode
            }
        }

    // region Rows to dumps and back

    private fun Currency.dump() = CurrencyDump(
        code = code,
        name = name,
        symbol = symbol,
        decimals = decimals,
        nameKey = nameKey,
        sortOrder = sortOrder,
        usedAt = usedAt?.toString(),
    )

    private fun CurrencyDump.row() = Currency(
        code = code,
        name = name,
        symbol = symbol,
        decimals = decimals,
        nameKey = nameKey,
        sortOrder = sortOrder,
        usedAt = usedAt?.let(LocalDateTime::parse),
    )

    private fun PaymentMethod.dump() = PaymentMethodDump(
        name = name,
        icon = icon,
        fixedCurrency = fixedCurrency,
        nameKey = nameKey,
        sortOrder = sortOrder,
    )

    private fun PaymentMethodDump.row() = PaymentMethod(
        name = name,
        icon = icon,
        fixedCurrency = fixedCurrency,
        nameKey = nameKey,
        sortOrder = sortOrder,
    )

    private fun Category.dump(favorites: List<CategoryFavorite>) = CategoryDump(
        name = name,
        icon = icon,
        isExpense = isExpense,
        isIncome = isIncome,
        trackQuantity = trackQuantity,
        nameKey = nameKey,
        sortOrder = sortOrder,
        favorites = favorites.map { FavoriteDump(it.name, it.nameKey, it.sortOrder) },
    )

    private fun CategoryDump.row() = Category(
        name = name,
        icon = icon,
        isExpense = isExpense,
        isIncome = isIncome,
        trackQuantity = trackQuantity,
        nameKey = nameKey,
        sortOrder = sortOrder,
    )

    private fun FavoriteDump.row(categoryId: Long) =
        CategoryFavorite(categoryId = categoryId, name = name, nameKey = nameKey, sortOrder = sortOrder)

    private fun BalanceSnapshot.dump() =
        RecountDump(dateTime = dateTime.toString(), amountMilli = amountMilli, notes = notes)

    private fun RecountDump.row(walletId: Long) = BalanceSnapshot(
        walletId = walletId,
        dateTime = LocalDateTime.parse(dateTime),
        amountMilli = amountMilli,
        notes = notes,
    )

    private fun Entry.dump(category: NameRef, expenseWallet: WalletRef?, incomeWallet: WalletRef?) = EntryDump(
        dateTime = dateTime.toString(),
        category = category,
        title = title,
        place = place,
        notes = notes,
        quantityMilli = quantityMilli,
        expenseWallet = expenseWallet,
        expenseAmountMilli = expenseAmountMilli,
        expenseCurrencyCode = expenseCurrencyCode,
        incomeWallet = incomeWallet,
        incomeAmountMilli = incomeAmountMilli,
        incomeCurrencyCode = incomeCurrencyCode,
        expenseSettlementAmountMilli = expenseSettlementAmountMilli,
        incomeSettlementAmountMilli = incomeSettlementAmountMilli,
        photoFile = photoFile,
        latitude = latitude,
        longitude = longitude,
    )

    private fun PaymentMethod.ref() = NameRef.of(name, nameKey)

    private fun PaymentMethodDump.ref() = NameRef.of(name, nameKey)

    private fun PaymentMethod.matches(ref: NameRef) =
        if (ref.nameKey != null) nameKey == ref.nameKey else name == ref.name

    private fun Category.ref() = NameRef.of(name, nameKey)

    private fun CategoryDump.ref() = NameRef.of(name, nameKey)

    private fun Category.matches(ref: NameRef) =
        if (ref.nameKey != null) nameKey == ref.nameKey else name == ref.name

    // endregion
}

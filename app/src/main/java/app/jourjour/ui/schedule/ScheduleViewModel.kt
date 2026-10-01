package app.jourjour.ui.schedule

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.jourjour.core.schedule.Band
import app.jourjour.core.schedule.ChainBand
import app.jourjour.core.schedule.GapBand
import app.jourjour.core.schedule.Move
import app.jourjour.core.schedule.PlacedEvent
import app.jourjour.core.schedule.ScheduleDay
import app.jourjour.core.schedule.Zoning
import app.jourjour.core.schedule.EventBand
import app.jourjour.core.schedule.DropEdge
import app.jourjour.core.schedule.GapEdge
import app.jourjour.core.schedule.Landing
import app.jourjour.core.schedule.applying
import app.jourjour.core.schedule.landingFor
import app.jourjour.core.schedule.bands
import app.jourjour.core.schedule.clashes
import app.jourjour.core.schedule.days
import app.jourjour.core.schedule.endsAt
import app.jourjour.core.schedule.nearestBefore
import app.jourjour.core.schedule.newEventIn
import app.jourjour.core.schedule.place
import app.jourjour.core.schedule.settle
import app.jourjour.core.export.ScheduleColumn
import app.jourjour.core.export.SchedulePdf
import app.jourjour.core.export.planLines
import app.jourjour.core.export.ScheduleCsv
import app.jourjour.core.export.ScheduleCsvImport
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.text.resolveName
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.ScheduleEvent
import app.jourjour.data.entity.ScheduleEventDetails
import app.jourjour.data.prefs.AppPreferences
import app.jourjour.data.prefs.DEFAULT_DAY_BEGINS
import app.jourjour.data.prefs.DEFAULT_EVENT_LENGTH
import app.jourjour.repository.CategoryRepository
import app.jourjour.repository.CurrencyRepository
import app.jourjour.repository.ScheduleRepository
import app.jourjour.repository.TripRepository
import app.jourjour.ui.common.STOP_TIMEOUT_MS
import app.jourjour.ui.common.formatDuration
import app.jourjour.ui.navigation.Route
import app.jourjour.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.io.File
import androidx.core.content.FileProvider
import javax.inject.Inject

/** What a new event is offered as when nothing on the schedule says otherwise. */
private data class Defaults(
    val dayBegins: LocalTime,
    val length: Duration,
    /** Whether a schedule opens read-only once its trip has begun. */
    val viewWhenStarted: Boolean,
)

/** One line of the tab: a date across the list, or one of the bands under it. */
sealed interface ScheduleRow {
    val day: ScheduleDay

    /**
     * @param summary what the day is about, in a line, or empty where nobody has said.
     *   It is what the heading carries beside its date — a day is about a place far more
     *   often than it is about any one thing booked in it.
     */
    data class Heading(
        override val day: ScheduleDay,
        val summary: String = "",
    ) : ScheduleRow
    data class Content(override val day: ScheduleDay, val band: Band) : ScheduleRow
}

/**
 * What the times on the open form would run into, and what could be done about it.
 *
 * Shown rather than refused: two things booked over the same minutes is a fact about a
 * plan and the person writing it knows which of them is wrong. [canShift] is whether
 * moving what comes after would clear it — it is false where the collision is with
 * something *earlier*, since clearing that way would mean shoving the morning into
 * yesterday, and where the push would carry an event off the end of the trip.
 */
@Immutable
data class ScheduleClash(
    val minutes: Long,
    /** What it runs into, by name. */
    val other: String,
    val canShift: Boolean,
    /** How many events the shift would move, which is what makes it worth asking first. */
    val moves: Int,
)

/**
 * What the open form could begin at the end of: the last thing that happened before it.
 *
 * Only what is near enough to mean anything. An event on the same day, or one that ended
 * on the day before — the flight that landed at half past eleven last night and the bags
 * collected at ten past midnight are one thing, and a lunch three days ago is not the
 * predecessor of anything. Where nothing is near enough the question is not asked at all,
 * since "tie this to nothing" is not a question.
 */
@Immutable
data class NearestEvent(
    val id: Long,
    val title: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
)

@Immutable
data class ScheduleState(
    /** The whole trip, day by day, flattened: it is read as one column and scrolled as one. */
    val rows: List<ScheduleRow> = emptyList(),
    /** Where to open: the day the trip is on now, or its first. */
    val focusRow: Int = 0,
    /**
     * The dictionary rows a planned expense names, by event. Kept beside the bands rather
     * than inside them: the timeline is arithmetic and has no business carrying an icon.
     */
    val details: Map<Long, ScheduleEventDetails> = emptyMap(),
    val hasEvents: Boolean = false,
    val sheet: ScheduleSheet? = null,
    /** What the open form's times run into, where they run into anything. */
    val clash: ScheduleClash? = null,
    /** Whether the shift has been asked for and is waiting on Save to be written. */
    val isShiftAsked: Boolean = false,
    /** Whether the schedule is being read rather than written. */
    val isViewing: Boolean = false,
    /** The event the open form could begin at the end of, where there is one worth offering. */
    val nearest: NearestEvent? = null,
    /** What a planned expense may be filed under: what a person spends on, never what comes back. */
    val categories: List<Category> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val isLoading: Boolean = true,
) {
    fun currency(code: String?): Currency? = currencies.firstOrNull { it.code == code }

    /** What that day is about, as its heading has it, or empty where nobody has said. */
    fun summaryOf(date: LocalDate): String = rows
        .filterIsInstance<ScheduleRow.Heading>()
        .firstOrNull { it.day.date == date }
        ?.summary
        .orEmpty()
}

/**
 * What happens and when.
 *
 * The rows are worked out here rather than in the tab because none of it is drawing: which
 * clock is in force, where one day ends and the next begins, what is booked and what is
 * free. See `core/schedule`, which does all of it and knows nothing about a screen.
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: ScheduleRepository,
    private val currencyRepository: CurrencyRepository,
    categoryRepository: CategoryRepository,
    private val appPreferences: AppPreferences,
    tripRepository: TripRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val tripId: Long = savedStateHandle.toRoute<Route.Trip>().tripId

    private val sheet = MutableStateFlow<ScheduleSheet?>(null)

    /**
     * Whether the shift has been asked for. It is a question, not a write: nothing moves
     * until Save, so cancelling the form cancels the shift with it — and any change to
     * the times takes the answer back, the question having become a different one.
     */
    private val shiftAsked = MutableStateFlow(false)

    /**
     * Whether reading or writing was asked for on this screen, and nothing until it was:
     * the settings and the trip's dates answer for it until somebody says otherwise, and
     * what they say has to be allowed to change under an answer that was never given.
     */
    private val asked = MutableStateFlow<Boolean?>(null)

    /** The rows themselves, which every action works from and the tab never sees. */
    private var events: List<ScheduleEvent> = emptyList()

    /** The days of the trip as instants, which is what bounds an event. */
    private var span: ClosedRange<Instant>? = null

    /** What a sheet of the schedule is made of besides the events: whose trip, and which days. */
    private data class Printable(
        val trip: app.jourjour.data.entity.Trip,
        val days: List<ScheduleDay>,
        val summaries: Map<LocalDate, String>,
    )

    private var printable: Printable? = null

    /** The zone in force at the moment being looked at, for a sheet that has to guess. */
    private var zoning: Zoning = Zoning.of(emptyList(), ZoneId.systemDefault())

    /** Where the clocks stand before the trip has moved them. A setting, not the phone. */
    private var home: ZoneId = ZoneId.systemDefault()

    /** The currency a plan was last written in, which is what the next one starts in. */
    private var lastCurrencyCode: String? = null

    /** The whole category dictionary, which the table names rather than the form's narrowed one. */
    private var allCategories: List<Category> = emptyList()

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** What the tab has to say after a table went out or came in: a line for the snackbar. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val _sheets = Channel<Uri>(Channel.BUFFERED)

    /**
     * A sheet of the schedule, written and waiting to be handed to another app.
     *
     * The chooser is opened by the screen rather than here: picking an app is something a
     * person does in front of an activity, and an application context would have to ask
     * for a task of its own to show it.
     */
    val sheets: Flow<Uri> = _sheets.receiveAsFlow()

    /** What a new event is offered as, which only the button and a tapped gap need. */
    private var defaults = Defaults(DEFAULT_DAY_BEGINS, Duration.ofMinutes(DEFAULT_EVENT_LENGTH.toLong()), true)


    /** The currency a planned expense is offered in, and what its figures are shaped by. */
    private var currencies: List<Currency> = emptyList()

    /** The currency a person reckons in, which is what a plan is offered in first. */
    private var baseCode: String? = null

    private val settings = combine(
        appPreferences.homeZone,
        appPreferences.dayBegins,
        appPreferences.defaultEventMinutes,
        appPreferences.viewWhenStarted,
    ) { zone, begins, minutes, viewWhenStarted ->
        zone to Defaults(begins, Duration.ofMinutes(minutes.toLong()), viewWhenStarted)
    }

    /** What a planned expense may name, which is the same pair the budget form offers. */
    private val dictionaries = combine(
        categoryRepository.observeAll(),
        currencyRepository.observeAll(),
        appPreferences.baseCurrency,
    ) { categories, currencies, baseCode -> Triple(categories, currencies, baseCode) }

    private val written = combine(
        tripRepository.observeById(tripId),
        repository.observeByTrip(tripId),
        repository.observeDays(tripId),
        settings,
        dictionaries,
    ) { trip, details, days, settings, dictionaries ->
        Sources(trip, details, days.associate { it.date to it.summary }, settings, dictionaries)
    }

    /** The flows the rows are built from, carried together for want of a wider combine. */
    private data class Sources(
        val trip: app.jourjour.data.entity.Trip?,
        val details: List<ScheduleEventDetails>,
        /** What each day is about, by date; a day nobody has described is not in it. */
        val days: Map<LocalDate, String>,
        val settings: Pair<ZoneId, Defaults>,
        val dictionaries: Triple<List<Category>, List<Currency>, String?>,
    )

    val state: StateFlow<ScheduleState> = combine(
        written,
        sheet,
        shiftAsked,
        asked,
    ) { sources, sheet, shiftAsked, asked ->
        val (trip, details, described, settings, books) = sources
        val (homeZone, preferred) = settings
        // Read-only unless somebody has said otherwise on this screen: writing a schedule
        // down and reading what is next are different jobs, done at different times, and
        // the trip having started is what says which of them this is.
        //
        // Never over an empty schedule, whatever the dates say. There is nothing to read,
        // and reading hides the empty days that are the only way to write anything — which
        // would leave a trip that began this morning with no way into its own plan.
        val viewing = asked ?: (
            preferred.viewWhenStarted &&
                details.isNotEmpty() &&
                trip != null &&
                !LocalDate.now().isBefore(trip.startDate)
            )
        // The whole dictionary, which a table and a sheet of paper name a category from:
        // an event may be filed under one that has since stopped being offered, and a
        // column that went blank rather than saying so would be the wrong kind of quiet.
        allCategories = books.first
        currencies = books.second
        baseCode = books.third
        val rows = details.map { it.event }
        events = rows
        home = homeZone
        defaults = preferred
        zoning = Zoning.of(rows, homeZone)
        val placed = place(rows, zoning)
        val days = trip?.let { days(it.startDate, it.endDate, zoning) }.orEmpty()
        span = days.firstOrNull()?.from?.rangeTo(days.last().until)
        printable = trip?.let { Printable(it, days, described) }

        val lines = buildList {
            days.forEach { day ->
                val bands = bands(day, placed).filter { it.isWorthShowing(viewing) }
                // A day with nothing booked in it is a day a reader has no business
                // scrolling past. It stays for whoever is still writing the schedule,
                // where the empty days are the room left to fill.
                if (viewing && bands.none { it is EventBand }) return@forEach
                add(ScheduleRow.Heading(day, summary = described[day.date].orEmpty()))
                bands.forEach { add(ScheduleRow.Content(day, it)) }
            }
        }
        ScheduleState(
            rows = lines,
            focusRow = lines.focusOn(Instant.now()),
            details = details.associateBy { it.event.id },
            hasEvents = rows.isNotEmpty(),
            sheet = sheet,
            clash = sheet?.let { clashFor(it, rows) },
            isShiftAsked = shiftAsked,
            isViewing = viewing,
            nearest = nearestFor(sheet, rows),
            // Money planned is money going out. A category that only ever brings money
            // in has nothing to plan, and an exchange moves a person's own money about.
            categories = books.first.filter { it.isExpense && !it.isIncome },
            currencies = books.second,
            isLoading = trip == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ScheduleState())

    /**
     * The schedule as it would stand if the open form were saved, which is what every
     * question about the form is asked of: what it runs into, what would have to move,
     * and how far.
     *
     * Nothing here is written. A person is entitled to see what an edit does before it
     * does it, and to shut the form and have none of it happen.
     */
    private fun proposed(sheet: ScheduleSheet, stored: List<ScheduleEvent>): List<ScheduleEvent>? {
        // The times and nothing else. What the form runs into is a question about hours,
        // so it is asked of the hours alone: an event still waiting for its name, or with
        // a figure half typed into the money, runs into exactly what it will run into
        // once those are finished, and saying nothing until then is saying it too late.
        val length = sheet.duration ?: return null
        val edited = (stored.firstOrNull { it.id == sheet.id } ?: blank())
            .copy(
                id = sheet.id,
                startsAt = sheet.startsAt.epochSecond,
                durationMinutes = length.toMinutes().toInt(),
                startsInZone = sheet.startZone.id.takeIf { sheet.declaresStart },
                endsInZone = sheet.endZone.id.takeIf { sheet.declaresEnd },
                followsId = sheet.followsId,
            )
        return stored.filterNot { it.id == sheet.id } + edited
    }

    /**
     * What the form's times run into.
     *
     * Only what this edit is party to: the event itself, and whatever its links dragged
     * along with it. A clash somewhere else in the trip was there before the form was
     * opened and will be there after it is shut, and shouting about it here would be
     * blaming somebody for what they are not doing.
     */
    private fun clashFor(sheet: ScheduleSheet, stored: List<ScheduleEvent>): ScheduleClash? {
        val proposal = proposed(sheet, stored) ?: return null
        val pinned = setOf(sheet.id)
        val followed = settle(proposal, pinned, push = false)
        val tied = proposal.applying(followed)
        val touched = followed.map { it.id }.toSet() + sheet.id
        val clash = clashes(tied)
            .firstOrNull { it.before.id in touched || it.after.id in touched }
            ?: return null

        val shifted = settle(proposal, pinned, push = true)
        val room = proposal.applying(shifted)
        return ScheduleClash(
            minutes = clash.minutes,
            other = (if (clash.before.id == sheet.id) clash.after else clash.before).title,
            // Offered only where it would actually clear this clash and leave everything
            // it moved inside the trip. A clash with something *earlier* clears no other
            // way than by shoving the morning into yesterday, and a push that ran off the
            // end of the trip would be a worse plan than the clash it fixed.
            canShift = shifted.size > followed.size &&
                clashes(room).none { it.before.id in touched || it.after.id in touched } &&
                span?.let { days -> room.all { Instant.ofEpochSecond(it.endsAt) <= days.endInclusive } } != false,
            moves = shifted.size,
        )
    }

    private fun blank() = ScheduleEvent(tripId = tripId, startsAt = 0, durationMinutes = 1)

    /** Writes an event over the empty time somebody tapped. */
    fun openSheet(band: GapBand) {
        val times = newEventIn(band, zoning, defaults.dayBegins, defaults.length)
        val at = Instant.ofEpochSecond(times.startsAt)
        sheet.value = ScheduleSheet.over(at, times.durationMinutes, zoning.at(at))
    }

    fun openSheet(placed: PlacedEvent) {
        val leader = placed.event.followsId?.let { id -> events.firstOrNull { it.id == id } }
        sheet.value = ScheduleSheet.of(
            placed = placed,
            followsTitle = leader?.title.orEmpty(),
            decimals = decimalsOf(placed.event.currencyCode),
        )
    }

    /**
     * Ties an event to the one it touches, or lets it go. Nothing moves either way: they
     * already begin and end at the same moment, which is why there is a chain to tap.
     */
    fun toggleChain(band: ChainBand) {
        viewModelScope.launch {
            repository.setFollows(band.after.id, band.before.id.takeIf { !band.linked })
        }
    }

    /**
     * Says what a day is about — "flight to Morocco", "Istanbul" — or rubs it out.
     *
     * It belongs to the date rather than to anything booked on it, which is why it is
     * written from the heading and not from any form: the days worth naming while a trip
     * is being planned are very often the ones with nothing in them yet.
     */
    fun describe(date: LocalDate, summary: String) {
        viewModelScope.launch { repository.describe(tripId, date, summary) }
    }

    fun closeSheet() {
        sheet.value = null
        shiftAsked.value = false
    }

    /**
     * Turns the event on the form into a new one beside it: the same name, notes, hours,
     * zones and planned cost, saved as a second event rather than over the first.
     *
     * The link is the one thing not carried over. It is what holds a start where it is,
     * and a copy is made in order to be put somewhere else; two events beginning at the
     * end of the same one is also the clash below spelt out twice.
     *
     * The hours stay as they were, which means the copy lands on top of the event it came
     * from and the form says so at once. That is the point: what a copy is for is a
     * second thing at another time, and the one thing left to do with it is say when —
     * with what it runs into, and the offer to move the rest along, already on the screen.
     */
    fun repeat() {
        val open = sheet.value ?: return
        if (open.isNew) return
        shiftAsked.value = false
        sheet.value = open.unfollowed().copy(id = 0)
    }

    fun updateTitle(value: String) = sheet.update { it?.copy(title = value, titleError = null) }

    fun updateNotes(value: String) = sheet.update { it?.copy(notes = value) }

    fun toggleNotes() = sheet.update { it?.copy(isNotesVisible = it.isNotesVisible.not()) }

    fun updateStartDate(value: LocalDate) = retimed { it.withStart(date = value) }

    fun updateStartTime(value: LocalTime) = retimed { it.withStart(time = value) }

    fun updateEndDate(value: LocalDate) = retimed { it.withEnd(date = value) }

    fun updateEndTime(value: LocalTime) = retimed { it.withEnd(time = value) }

    /** How long it lasts, as the one figure it is read as; the end moves to match. */
    fun updateLength(total: Long) = retimed {
        it.withLength(hours = (total / 60).toString(), minutes = (total % 60).toString())
    }

    /**
     * What the open form could begin at the end of, near enough to be worth offering.
     *
     * The event it is already tied to always is, whatever the dates say: an offer to untie
     * has to stand wherever the link does, or a link made yesterday could not be undone
     * today. Otherwise it is the nearest thing before it, kept only where it happens on
     * the same day or ended on the day before — anything further back is not what
     * "begins at the end of" is about.
     */
    private fun nearestFor(open: ScheduleSheet?, stored: List<ScheduleEvent>): NearestEvent? {
        open ?: return null
        val tied = open.followsId?.let { id -> stored.firstOrNull { it.id == id } }
        val leader = tied ?: nearestBefore(stored, open.startsAt.epochSecond, open.id) ?: return null
        val placed = place(listOf(leader), zoning).first()
        if (tied == null) {
            val startsOn = open.startsAt.atZone(zoning.at(open.startsAt)).toLocalDate()
            val endsOn = placed.end.toLocalDate()
            if (endsOn != startsOn && endsOn != startsOn.minusDays(1)) return null
        }
        return NearestEvent(leader.id, leader.title, placed.start, placed.end)
    }

    /** Ties the event to whatever ended nearest the time typed in. */
    fun linkToNearest() = sheet.update { current ->
        current ?: return@update null
        val leader = nearestBefore(events, current.startsAt.epochSecond, current.id) ?: return@update current
        current.following(place(listOf(leader), zoning).first())
    }

    fun unlink() = sheet.update { it?.unfollowed() }

    /** Says yes to making room. Nothing moves until Save; see [shiftAsked]. */
    fun askShift() {
        shiftAsked.value = true
    }

    /**
     * Any change to the times is a new question, so the answer to the old one goes: a
     * shift agreed to for one set of hours has nothing to say about another.
     */
    private fun retimed(change: (ScheduleSheet) -> ScheduleSheet) {
        shiftAsked.value = false
        sheet.update { it?.let(change) }
    }

    /** Between reading the schedule and writing it, which is what the menu offers. */
    fun setViewing(value: Boolean) {
        asked.value = value
    }

    // region Dragging an event somewhere else

    /**
     * Whether a chain let go here would fit, or nothing where this is no place to let go.
     *
     * Asked as the finger moves, which is why it answers and does not write: what a person
     * is being shown is where the thing would land, in one colour if there is room for it
     * and another if there is not.
     */
    fun aim(draggedId: Long, at: Int, edge: DropEdge): Boolean? =
        landingAt(draggedId, at, edge)?.fits

    /**
     * Lets the chain go.
     *
     * Where it fits, it simply moves — that is the whole of what the drag was saying, and
     * a form asking to confirm it would be asking about something already decided. Where
     * it does not, the form opens on the new times instead, with what they run into and
     * the offer to push the rest along: dropping something on top of something else is a
     * question, and this is where the app has one to ask.
     */
    fun drop(draggedId: Long, at: Int, edge: DropEdge) {
        val landing = landingAt(draggedId, at, edge) ?: return
        if (!landing.fits) return openOnLanding(draggedId, landing)
        viewModelScope.launch { repository.moveAll(tripId, landing.moves, home) }
    }

    private fun landingAt(draggedId: Long, at: Int, edge: DropEdge): Landing? {
        val row = state.value.rows.getOrNull(at) as? ScheduleRow.Content ?: return null
        return landingFor(
            events = events,
            band = row.band,
            day = row.day,
            draggedId = draggedId,
            edge = edge,
            zoning = zoning,
            within = span,
        )
    }

    /** Opens the form on where the drag was aimed, so that the clash can be settled there. */
    private fun openOnLanding(draggedId: Long, landing: Landing) {
        val placed = place(events, zoning).firstOrNull { it.id == draggedId } ?: return
        val leader = placed.event.followsId?.let { id -> events.firstOrNull { it.id == id } }
        val starts = landing.moves.first { it.id == draggedId }.startsAt
        val at = Instant.ofEpochSecond(starts).atZone(placed.startZone)
        sheet.value = ScheduleSheet
            .of(placed, followsTitle = leader?.title.orEmpty(), decimals = decimalsOf(placed.event.currencyCode))
            .withStart(date = at.toLocalDate(), time = at.toLocalTime())
    }

    // endregion

    // region The table

    /**
     * Writes the schedule out as a sheet of paper and offers it to whatever will take it.
     *
     * The same thing the tab shows while a schedule is being *read* — the days that have
     * anything in them, what is booked, the free stretches between two things — because
     * that is what anybody asked to look at a plan is being asked to read. The file goes
     * to the cache: it exists for the length of a share and nothing else.
     */
    fun sharePlan() {
        val printing = printable ?: return
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) { writeSheet(printing) }
                _sheets.send(uri)
            } catch (e: Exception) {
                _messages.send(
                    context.getString(R.string.error_share, e.localizedMessage.orEmpty())
                )
            }
        }
    }

    private fun writeSheet(printing: Printable): Uri {
        val placed = place(events, zoning)
        val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        val heading = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
        val lines = planLines(
            days = printing.days,
            bandsOf = { day -> bands(day, placed) },
            summaryOf = { day -> printing.summaries[day.date].orEmpty() },
            dateOf = { day -> day.date.format(heading) },
            clock = { at -> at.format(clock) },
            length = { minutes -> formatDuration(minutes) },
            costOf = { event -> costLine(event) },
            overlapText = context.getString(R.string.schedule_overlap),
        )

        // Swept before each write rather than after each share: the share is handled by
        // somebody else and we are not told when they have finished reading.
        val folder = File(context.cacheDir, SHARE_DIRECTORY)
        folder.deleteRecursively()
        folder.mkdirs()
        val file = File(folder, "${printing.trip.name.trim().ifBlank { "plan" }}.pdf")
        file.outputStream().use { out ->
            SchedulePdf.write(
                out = out,
                title = context.getString(
                    R.string.pdf_title,
                    printing.trip.name,
                    printing.trip.startDate.format(date),
                    printing.trip.endDate.format(date),
                ),
                headers = listOf(
                    context.getString(R.string.field_starts),
                    context.getString(R.string.field_ends),
                    context.getString(R.string.pdf_column_event),
                    context.getString(R.string.csv_length),
                    context.getString(R.string.pdf_column_cost),
                ),
                zoneMark = context.getString(R.string.pdf_zone_mark),
                lines = lines,
            )
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    /** What the event is expected to cost, as one line: what it is for, and how much. */
    private fun costLine(placed: PlacedEvent): String {
        val amount = placed.event.amountMilli ?: return ""
        val currency = currencies.firstOrNull { it.code == placed.event.currencyCode } ?: return ""
        val category = allCategories.firstOrNull { it.id == placed.event.categoryId }
            ?.let { resolveName(context, it.name, it.nameKey) }
            ?: return ""
        return "$category: ${MoneyFormat.formatAmountWithCurrency(Money(amount), currency)}"
    }

    fun exportTo(target: Uri) {
        viewModelScope.launch {
            val rows = repository.eventsOf(tripId)
            val placed = place(rows, Zoning.of(rows, home))
            try {
                withContext(Dispatchers.IO) {
                    val csv = ScheduleCsv.build(
                        headers = headers(),
                        events = placed,
                        separator = context.getString(R.string.csv_separator),
                        follows = context.getString(R.string.action_yes),
                        nameOf = { name, key -> resolveName(context, name, key) },
                        categoryOf = { id ->
                            allCategories.firstOrNull { it.id == id }?.let { it.name to it.nameKey }
                        },
                        decimalsOf = { code -> decimalsOf(code) },
                    )
                    context.contentResolver.openOutputStream(target)?.use {
                        it.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: error(target.toString())
                }
                _messages.send(context.getString(R.string.plan_exported, rows.size))
            } catch (e: Exception) {
                _messages.send(
                    context.getString(R.string.error_export, e.localizedMessage.orEmpty())
                )
            }
        }
    }

    /**
     * Reads a table in and merges it the way a checklist does: an event the schedule
     * already carries is passed over, matched on the moment it begins and what it is
     * called. Two things at the same minute with the same name are the same thing.
     */
    fun importFrom(source: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(source)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error(source.toString())
                }

                val result = ScheduleCsvImport.read(
                    text = text,
                    headers = headers(),
                    tripId = tripId,
                    home = home,
                    categories = state.value.categories,
                    currencies = state.value.currencies,
                    nameOf = { name, key -> resolveName(context, name, key) },
                )
                when (result) {
                    is ScheduleCsvImport.Result.Ready -> {
                        val already = repository.eventsOf(tripId)
                            .mapTo(mutableSetOf()) { it.startsAt to it.title.trim().lowercase() }
                        // A row the schedule already carries is passed over, and a link is
                        // only tied where both ends of it arrived: a row whose predecessor
                        // was already there has nothing new to be tied to.
                        val follows = ScheduleCsvImport.followers(
                            text = text,
                            headers = headers(),
                            yes = context.getString(R.string.action_yes),
                        )
                        val taken = result.events.withIndex().filter { (_, event) ->
                            (event.startsAt to event.title.trim().lowercase()) !in already
                        }
                        val added = repository.addAll(taken.map { it.value }, home)
                        val ids = taken.map { it.index }.zip(added).toMap()
                        taken.forEachIndexed { at, (index, _) ->
                            val leader = ids[index - 1]?.takeIf { index in follows } ?: return@forEachIndexed
                            repository.setFollows(added[at], leader)
                        }
                        _messages.send(
                            context.getString(
                                R.string.plan_imported,
                                taken.size,
                                result.events.size - taken.size,
                            )
                        )
                    }
                    else -> _messages.send(describe(result))
                }
            } catch (e: Exception) {
                _messages.send(
                    context.getString(R.string.error_import, e.localizedMessage.orEmpty())
                )
            }
        }
    }

    /** The column titles in the language the device is set to, which is what a file carries. */
    private fun headers(): List<String> =
        ScheduleColumn.entries.map { context.getString(it.titleRes) }

    /** Says what stopped the import, in terms of the file the person is looking at. */
    private fun describe(result: ScheduleCsvImport.Result): String = when (result) {
        ScheduleCsvImport.Result.HeaderMismatch ->
            context.getString(R.string.error_import_header)

        is ScheduleCsvImport.Result.MissingColumns -> context.getString(
            R.string.error_import_missing_columns,
            result.columns.joinToString { context.getString(it.titleRes) },
        )

        is ScheduleCsvImport.Result.Empty -> context.getString(
            R.string.error_import_empty_column,
            result.line,
            context.getString(result.column.titleRes),
        )

        is ScheduleCsvImport.Result.Unknown -> context.getString(
            R.string.error_import_unknown,
            result.line,
            context.getString(result.column.titleRes),
            result.value,
        )

        is ScheduleCsvImport.Result.Malformed -> context.getString(
            R.string.error_import_malformed,
            result.line,
            context.getString(result.column.titleRes),
            result.value,
        )

        is ScheduleCsvImport.Result.HalfACost ->
            context.getString(R.string.error_import_half_a_cost, result.line)

        is ScheduleCsvImport.Result.Ready -> ""
    }

    // endregion

    // region The planned expense

    fun showCategorySheet() = sheet.update { it?.copy(isCategorySheetVisible = true) }

    fun hideCategorySheet() = sheet.update { it?.copy(isCategorySheetVisible = false) }

    /**
     * Naming what the money is for is what brings the money on. It arrives in the currency
     * a person reckons in — most plans are written in it, and the ones that are not are
     * one tap from being changed.
     */
    fun selectCategory(category: Category) = sheet.update {
        it?.copy(
            categoryId = category.id,
            currencyCode = it.currencyCode ?: lastCurrencyCode ?: baseCode,
            isCategorySheetVisible = false,
            categoryError = null,
        )
    }

    fun updateAmount(value: String) = sheet.update { it?.copy(amount = value, amountError = null) }

    fun selectCurrency(code: String?) {
        lastCurrencyCode = code
        sheet.update { it?.copy(currencyCode = code, currencyError = null) }
        // The picker offers what was used most recently, and this is what it reads.
        code?.let { viewModelScope.launch { currencyRepository.touch(it, LocalDateTime.now()) } }
    }

    /** Takes the money off the event; the event itself is not about money. */
    fun clearCost() = sheet.update {
        it?.copy(
            categoryId = null,
            amount = "",
            categoryError = null,
            amountError = null,
            currencyError = null,
        )
    }

    /** What the figures on the open form are shaped by: two decimals here, none in yen. */
    private fun decimalsOf(code: String?): Int =
        currencies.firstOrNull { it.code == code }?.decimals ?: Money.SCALE - 1

    // endregion

    /**
     * Where the event begins, or nothing where the clocks do not move under it — which is
     * almost every event, and which hands it back to whatever the schedule says about that
     * stretch of the trip, read from where the event stands now.
     */
    fun selectStartZone(zone: ZoneId?) = sheet.update { current ->
        current ?: return@update null
        if (zone == null) current.withInheritedStart(zoning.at(current.startsAt))
        else current.withStartZone(zone)
    }

    /** And where it lands, which is what a flight says and nothing else does. */
    fun selectEndZone(zone: ZoneId?) = sheet.update { current ->
        current ?: return@update null
        if (zone == null) current.withInheritedEnd() else current.withEndZone(zone)
    }

    /**
     * Writes the event and, in the same transaction, everything it displaced.
     *
     * The links are kept whatever else happens — an event tied to another has no time of
     * its own — and the rest is pushed out of the way only where that was asked for.
     */
    fun confirmSheet() {
        val current = sheet.value ?: return
        when (val checked = current.check(span, decimalsOf(current.currencyCode))) {
            is ScheduleSheetCheck.Refused -> sheet.value = checked.sheet
            is ScheduleSheetCheck.Accepted -> viewModelScope.launch {
                val stored = events
                val proposal = proposed(current, stored).orEmpty()
                val moves = settle(proposal, setOf(current.id), push = shiftAsked.value)
                repository.save(
                    event = (stored.firstOrNull { it.id == current.id } ?: blank())
                        .copy(
                            title = checked.title,
                            notes = checked.notes,
                            startsAt = checked.startsAt,
                            durationMinutes = checked.durationMinutes,
                            startsInZone = checked.startsInZone,
                            endsInZone = checked.endsInZone,
                            followsId = checked.followsId,
                            categoryId = checked.categoryId,
                            amountMilli = checked.amountMilli,
                            currencyCode = checked.currencyCode,
                        ),
                    home = home,
                    moves = moves,
                )
                sheet.value = null
                shiftAsked.value = false
                // Somebody is writing, so the screen stays open for writing. Without this
                // the first event of a trip that began this morning would flip the tab to
                // reading the moment it was saved — there being something to read at last
                // — and take the empty days away from under the person filling them.
                asked.value = false
            }
        }
    }

    fun delete() {
        val current = sheet.value ?: return
        val existing = events.firstOrNull { it.id == current.id } ?: return
        viewModelScope.launch {
            repository.delete(existing, home)
            sheet.value = null
        }
    }

    private companion object {
        /** Named in the file provider's paths, which is what makes it reachable at all. */
        const val SHARE_DIRECTORY = "share"
    }
}

/**
 * Which row to open on: the heading of the day the trip is on, and its first day where it
 * has not started or is already over.
 *
 * The heading rather than what is happening now, so that the day is read from the top —
 * what is left of it is the question, and the morning is the context for the answer.
 */
private fun List<ScheduleRow>.focusOn(now: Instant): Int {
    val today = indexOfFirst { it is ScheduleRow.Heading && now < it.day.until }
    return today.takeIf { it >= 0 } ?: 0
}

/** A length is a count, so the field takes digits and nothing else. */
private fun String.digits(): String = filter(Char::isDigit).take(4)

/**
 * Whether a band is worth a reader's attention.
 *
 * Writing a schedule down and reading what is next are different jobs. The chains, the
 * empty ends of a day and the invitation to fill them are all about the writing; the hours
 * between two things are about the reading, since what is free is half of what a person
 * wants to know on the road.
 */
private fun Band.isWorthShowing(viewing: Boolean): Boolean = when {
    !viewing -> true
    this is ChainBand -> false
    this is GapBand -> edge == GapEdge.BETWEEN
    else -> true
}

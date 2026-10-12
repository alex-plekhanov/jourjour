package app.jourjour.ui.checklist

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.jourjour.R
import app.jourjour.core.checklist.ChecklistSection
import app.jourjour.core.checklist.DropSide
import app.jourjour.core.checklist.itemLanding
import app.jourjour.core.checklist.itemsToCopy
import app.jourjour.core.checklist.onlyNew
import app.jourjour.core.checklist.sectionLanding
import app.jourjour.core.checklist.sectionsOf
import app.jourjour.core.export.ChecklistColumn
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.money.Quantity
import app.jourjour.core.export.ChecklistCsv
import app.jourjour.core.export.ChecklistCsvImport
import app.jourjour.data.entity.ChecklistItem
import app.jourjour.data.entity.Trip
import app.jourjour.repository.ChecklistRepository
import app.jourjour.repository.TripRepository
import app.jourjour.ui.navigation.Route
import javax.inject.Inject

/**
 * The item sheet, as it is being filled in.
 *
 * A section is either one that exists or one being invented on the spot. The invented one
 * lives here and nowhere else until the item is saved: a section with no items is not a
 * thing the database can hold, so abandoning the sheet takes the new section with it —
 * which is the right outcome, since nothing was put in it.
 */
data class ChecklistItemSheet(
    /** Zero while the item has never been saved. */
    val id: Long = 0,
    val section: String? = null,
    val isNewSection: Boolean = false,
    val newSection: String = "",
    val title: String = "",
    /** How many of the thing, as it is typed. Empty for most items: a passport is not a count. */
    val quantity: String = "",
    val isDone: Boolean = false,
    val isSkipped: Boolean = false,
    @param:StringRes val sectionError: Int? = null,
    @param:StringRes val titleError: Int? = null,
) {
    val isNew: Boolean get() = id == 0L

    /** What the section will be called, whichever way it was chosen. */
    val sectionName: String? get() = if (isNewSection) newSection.trim() else section
}

/** Renaming a section, which is the only thing a section has to change. */
data class SectionRename(
    val original: String,
    val name: String,
    @param:StringRes val error: Int? = null,
)

/**
 * A section about to go, and how many items go with it.
 *
 * Only a section is worth asking about. An item is one line that was one line to type,
 * and a prompt over every one of them would weigh more than the mistake it guards against;
 * a section takes a handful of them along without saying which, and that is worth a
 * sentence.
 */
data class SectionDeletion(val name: String, val count: Int)

/**
 * Taking a checklist from a trip already made.
 *
 * Three fields, each narrowing the one before it: a trip, then one of its sections, then
 * one item of that section. Only the trip is required — leaving a field empty takes
 * everything under it, which is what copying a list usually means.
 */
data class ChecklistCopy(
    /** Every other trip, newest first. The one being worked on is not among them. */
    val trips: List<Trip> = emptyList(),
    val tripId: Long? = null,
    /** The chosen trip's whole checklist, which the two lower fields are drawn from. */
    val sourceItems: List<ChecklistItem> = emptyList(),
    val section: String? = null,
    val title: String? = null,
    @param:StringRes val tripError: Int? = null,
    val isCopying: Boolean = false,
) {
    val trip: Trip? get() = trips.firstOrNull { it.id == tripId }

    /** In the order the source trip holds them, which is the order it is read in. */
    val sections: List<String> get() = sourceItems.map { it.section }.distinct()

    val titles: List<String>
        get() = sourceItems.filter { it.section == section }.map { it.title }

    /** A trip has been named and has something to give. */
    val canCopy: Boolean get() = tripId != null && sourceItems.isNotEmpty() && !isCopying

    /** The chosen trip turned out to have no checklist at all, which the form says. */
    val sourceIsEmpty: Boolean get() = tripId != null && sourceItems.isEmpty()
}

/**
 * One line of the screen: a section's heading, or one item under it.
 *
 * The list is kept as a flat run of these as well as in its sections, because that is what
 * a finger is matched against — a drag asks which line it is over, and "the fourth line
 * down" is a question only a flat list can answer.
 */
sealed interface ChecklistRow {
    data class Heading(val section: ChecklistSection) : ChecklistRow
    data class Item(val item: ChecklistItem) : ChecklistRow
}

/** What a drag has hold of: one item, or a whole section by its heading. */
sealed interface Held {
    data class Item(val id: Long) : Held
    data class Section(val name: String) : Held
}

@Immutable
data class ChecklistState(
    val sections: List<ChecklistSection> = emptyList(),
    /** Sections folded away. Held here rather than stored: it is how the screen looks. */
    val collapsed: Set<String> = emptySet(),
    val sheet: ChecklistItemSheet? = null,
    val rename: SectionRename? = null,
    val deletion: SectionDeletion? = null,
    val copy: ChecklistCopy? = null,
    val isLoading: Boolean = true,
) {
    /** Every section there is, for the sheet to offer. */
    val sectionNames: List<String> get() = sections.map { it.name }

    /**
     * The screen as one column of lines, a folded section being its heading alone.
     *
     * Worked out once per state rather than on every read: the tab walks it by index and
     * so does every answer about where a drag is pointing.
     */
    val rows: List<ChecklistRow> = buildList {
        sections.forEach { section ->
            add(ChecklistRow.Heading(section))
            if (section.name !in collapsed) section.items.forEach { add(ChecklistRow.Item(it)) }
        }
    }

    fun isCollapsed(section: ChecklistSection): Boolean = section.name in collapsed
}

/**
 * The trip's checklist.
 *
 * Everything on this screen is written as it is done — a tick, a rename, a deletion —
 * rather than gathered up and saved: there is nothing here to get half-finished, and a
 * checklist is used with one hand while packing.
 */
@HiltViewModel
class ChecklistViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: ChecklistRepository,
    private val tripRepository: TripRepository,
    private val lastSection: LastSection,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val tripId: Long = savedStateHandle.toRoute<Route.Trip>().tripId

    private val _state = MutableStateFlow(ChecklistState())
    val state: StateFlow<ChecklistState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Whether the folded-away sections have been worked out for this screen yet. */
    private var opened = false

    /** The trip's items in the order the list reads them, which is what a drop is told in. */
    private val items: List<ChecklistItem>
        get() = _state.value.sections.flatMap { it.items }

    init {
        viewModelScope.launch {
            repository.observeByTrip(tripId).collect { items ->
                val sections = sectionsOf(items)
                _state.update { state ->
                    state.copy(
                        sections = sections,
                        collapsed = collapsedFor(sections, state.collapsed),
                        isLoading = false,
                    )
                }
                opened = true
            }
        }
    }

    /**
     * Which sections are folded away.
     *
     * The first time the screen has anything to show, the settled ones are: they are done
     * with, and what somebody opening a checklist wants to see is what is left. After that
     * it is whatever they have since folded and unfolded — a section that settles while
     * being looked at must not shut itself, which would look like the screen snatching the
     * list away at the moment of finishing it.
     *
     * Names of sections that no longer exist are dropped, or a section made later under a
     * name once used would come back folded for no reason anybody could see.
     */
    private fun collapsedFor(sections: List<ChecklistSection>, current: Set<String>): Set<String> {
        val names = sections.map { it.name }.toSet()
        if (!opened) return sections.filter { it.isSettled }.map { it.name }.toSet()
        return current intersect names
    }

    fun toggleSection(name: String) = _state.update { state ->
        val collapsed = if (name in state.collapsed) state.collapsed - name else state.collapsed + name
        state.copy(collapsed = collapsed)
    }

    /** The tick. Ruled-out items have none to give, so nothing happens on one. */
    fun toggleDone(item: ChecklistItem) {
        if (item.isSkipped) return
        viewModelScope.launch { repository.setDone(item.id, !item.isDone) }
    }

    /**
     * The same tick for a whole section, which is how a packed bag is signed off: the
     * things were done one after another and the list is being caught up with afterwards.
     * A section folded away because it had settled stays folded — see [collapsedFor].
     */
    fun markSection(section: ChecklistSection, done: Boolean) {
        viewModelScope.launch { repository.setSectionDone(tripId, section.name, done) }
    }

    // region Dragging a line somewhere else

    /**
     * Lets go of what was being carried.
     *
     * What was picked up says what the drop means — an item goes between two items, and
     * takes the section of the one it lands against; a section goes between two sections,
     * taking its items with it. The tab only ever aims at a line of the right kind, so
     * anything else arriving here is a gesture that ended over nothing.
     *
     * Letting go where the thing already is writes nothing, which is the ordinary end of a
     * drag that changed its mind.
     */
    fun drop(held: Held, onto: ChecklistRow?, side: DropSide) {
        val placements = when {
            held is Held.Item && onto is ChecklistRow.Item ->
                itemLanding(items, held.id, onto.item.id, side)

            held is Held.Section && onto is ChecklistRow.Heading ->
                sectionLanding(items, held.name, onto.section.name, side)

            else -> null
        }
        if (placements.isNullOrEmpty()) return
        viewModelScope.launch { repository.place(placements) }
    }

    // endregion

    // region The item sheet

    /** @param item null to add one. A new item opens on no section, so one must be chosen. */
    fun openSheet(item: ChecklistItem? = null) = _state.update { state ->
        state.copy(
            sheet = when (item) {
                // A new item opens on the section the last one was saved in, since a
                // checklist is written in runs and picking the same section four times
                // over is the one bit of work this form can take off somebody. Only where
                // this list has such a section: the name is remembered across trips, and
                // in a trip that has no section by that name there is nothing to offer and
                // the form opens on none, which is where it always used to open.
                null -> ChecklistItemSheet(
                    section = lastSection.name?.takeIf { it in state.sectionNames },
                )
                else -> ChecklistItemSheet(
                    id = item.id,
                    section = item.section,
                    title = item.title,
                    quantity = item.quantityMilli
                        ?.let { MoneyFormat.formatQuantityForInput(Quantity(it)) }
                        .orEmpty(),
                    isDone = item.isDone,
                    isSkipped = item.isSkipped,
                )
            }
        )
    }

    fun closeSheet() = _state.update { it.copy(sheet = null) }

    fun sheetSelectSection(name: String) = updateSheet {
        it.copy(section = name, isNewSection = false, sectionError = null)
    }

    fun sheetNewSection() = updateSheet {
        it.copy(isNewSection = true, section = null, sectionError = null)
    }

    fun sheetUpdateNewSection(value: String) = updateSheet {
        it.copy(newSection = value, sectionError = null)
    }

    fun sheetUpdateTitle(value: String) = updateSheet { it.copy(title = value, titleError = null) }

    fun sheetUpdateQuantity(value: String) = updateSheet { it.copy(quantity = value) }

    fun sheetUpdateDone(value: Boolean) = updateSheet { it.copy(isDone = value) }

    /** Ruling an item out settles it, so the tick beside it stops meaning anything. */
    fun sheetUpdateSkipped(value: Boolean) = updateSheet { it.copy(isSkipped = value) }

    private fun updateSheet(transform: (ChecklistItemSheet) -> ChecklistItemSheet) =
        _state.update { state ->
            state.copy(sheet = state.sheet?.let(transform))
        }

    fun confirmSheet() {
        val sheet = _state.value.sheet ?: return
        val section = sheet.sectionName
        val title = sheet.title.trim()

        // Two ways to have no section, and they are not the same mistake: nothing picked
        // from the list, or a new one whose name was never typed.
        val sectionError = when {
            !section.isNullOrBlank() -> null
            sheet.isNewSection -> R.string.error_name_empty
            else -> R.string.error_section_required
        }
        val titleError = R.string.error_name_empty.takeIf { title.isBlank() }
        if (sectionError != null || titleError != null) {
            updateSheet { it.copy(sectionError = sectionError, titleError = titleError) }
            return
        }

        val item = ChecklistItem(
            id = sheet.id,
            tripId = tripId,
            section = checkNotNull(section),
            title = title,
            // A count that will not parse is left out rather than refused: it is a detail
            // of the item, and the item is what a checklist is for.
            quantityMilli = MoneyFormat.parseQuantity(sheet.quantity)?.milli,
            isDone = sheet.isDone,
            isSkipped = sheet.isSkipped,
        )
        // The place in the list belongs to the list and not to the form, which never asks
        // about it: an item keeps the one it had, and one the form has moved to another
        // section is given the end of that section the way a new item is.
        val place = items.firstOrNull { it.id == sheet.id && it.section == section }?.sortOrder
        // And this is where the next item will open. An item written counts as much as one
        // added: somebody who has just been in a section is somebody working in it.
        lastSection.remember(item.section)
        viewModelScope.launch {
            repository.save(item, place)
            closeSheet()
        }
    }

    // endregion

    // region Sections

    fun openRename(name: String) = _state.update {
        it.copy(rename = SectionRename(original = name, name = name))
    }

    fun closeRename() = _state.update { it.copy(rename = null) }

    fun updateRename(value: String) = _state.update { state ->
        state.copy(rename = state.rename?.copy(name = value, error = null))
    }

    fun confirmRename() {
        val rename = _state.value.rename ?: return
        val name = rename.name.trim()
        val error = when {
            name.isBlank() -> R.string.error_name_empty
            // Merging two sections by renaming one onto the other would be a surprising
            // way to do it, and an unsurprising way to lose track of where an item went.
            name != rename.original && name in _state.value.sectionNames ->
                R.string.error_section_taken
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(rename = rename.copy(error = error)) }
            return
        }
        if (name == rename.original) {
            closeRename()
            return
        }
        // The fold travels with the name; otherwise a folded section would spring open the
        // moment it was renamed.
        _state.update { state ->
            val collapsed = when (rename.original) {
                in state.collapsed -> state.collapsed - rename.original + name
                else -> state.collapsed
            }
            state.copy(collapsed = collapsed, rename = null)
        }
        viewModelScope.launch { repository.renameSection(tripId, rename.original, name) }
    }

    // endregion

    // region Deleting

    /**
     * Takes away the item the form is open on, which is the only place it is offered
     * from. Straight away: one line, typed in one breath, and the form puts it back.
     */
    fun deleteOpen() {
        val sheet = _state.value.sheet ?: return
        if (sheet.isNew) return
        closeSheet()
        viewModelScope.launch { repository.delete(sheet.id) }
    }

    fun askDeleteSection(section: ChecklistSection) = _state.update {
        it.copy(deletion = SectionDeletion(section.name, section.items.size))
    }

    fun cancelDelete() = _state.update { it.copy(deletion = null) }

    fun confirmDelete() {
        val deletion = _state.value.deletion ?: return
        _state.update { it.copy(deletion = null) }
        viewModelScope.launch { repository.deleteSection(tripId, deletion.name) }
    }

    // endregion

    // region Copying from another trip

    fun openCopy() {
        _state.update { it.copy(copy = ChecklistCopy()) }
        viewModelScope.launch {
            val others = tripRepository.observeAll().first().filterNot { it.id == tripId }
            updateCopy { it.copy(trips = others) }
        }
    }

    fun closeCopy() = _state.update { it.copy(copy = null) }

    /** A different trip makes the two fields under it meaningless, so they go. */
    fun copySelectTrip(id: Long) {
        updateCopy { it.copy(tripId = id, section = null, title = null, tripError = null) }
        viewModelScope.launch {
            val items = repository.getByTrip(id)
            // The sheet may have moved on to another trip while this was being read.
            updateCopy { copy -> copy.takeIf { it.tripId == id }?.copy(sourceItems = items) ?: copy }
        }
    }

    /** @param name null to take every section of the trip. */
    fun copySelectSection(name: String?) = updateCopy { it.copy(section = name, title = null) }

    /** @param title null to take every item of the chosen section. */
    fun copySelectItem(title: String?) = updateCopy { it.copy(title = title) }

    private fun updateCopy(transform: (ChecklistCopy) -> ChecklistCopy) =
        _state.update { state -> state.copy(copy = state.copy?.let(transform)) }

    fun confirmCopy() {
        val copy = _state.value.copy ?: return
        if (copy.tripId == null) {
            updateCopy { it.copy(tripError = R.string.error_trip_required) }
            return
        }
        if (copy.isCopying) return
        updateCopy { it.copy(isCopying = true) }

        viewModelScope.launch {
            val wanted = itemsToCopy(
                source = copy.sourceItems,
                target = repository.getByTrip(tripId),
                section = copy.section,
                title = copy.title,
                tripId = tripId,
            )
            repository.addAll(wanted)
            _state.update { it.copy(copy = null) }
            // Both numbers, because a copy that added nothing is not the same thing as a
            // copy that did nothing wrong, and the difference is what was already there.
            val offered = copy.sourceItems
                .count { item ->
                    (copy.section == null || item.section == copy.section) &&
                        (copy.title == null || item.title == copy.title)
                }
            _messages.send(
                context.getString(
                    R.string.checklist_copied,
                    wanted.size,
                    offered - wanted.size,
                )
            )
        }
    }

    // endregion

    // region The table, out and back

    /** The titles the file is written with, and the ones an import matches against. */
    private fun headers(): List<String> =
        ChecklistColumn.entries.map { context.getString(it.titleRes) }

    fun exportTo(target: Uri) {
        viewModelScope.launch {
            val items = repository.getByTrip(tripId)
            try {
                withContext(Dispatchers.IO) {
                    val csv = ChecklistCsv.build(
                        headers = headers(),
                        items = items,
                        separator = context.getString(R.string.csv_separator),
                        yes = context.getString(R.string.action_yes),
                        no = context.getString(R.string.action_no),
                    )
                    context.contentResolver.openOutputStream(target)?.use {
                        it.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: error(target.toString())
                }
                _messages.send(context.getString(R.string.checklist_exported, items.size))
            } catch (e: Exception) {
                _messages.send(
                    context.getString(R.string.error_export, e.localizedMessage.orEmpty())
                )
            }
        }
    }

    /**
     * Reads a table in and merges it, the way a copy from another trip merges: an item the
     * list already carries is passed over. Unlike a copy, the ticks in the file are taken
     * at their word — somebody wrote them there.
     */
    fun importFrom(source: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(source)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error(source.toString())
                }

                val result = ChecklistCsvImport.read(
                    text = text,
                    headers = headers(),
                    tripId = tripId,
                    // The same two words the export writes, so that a file this app wrote
                    // is a file this app can read.
                    yes = context.getString(R.string.action_yes),
                    no = context.getString(R.string.action_no),
                )
                when (result) {
                    is ChecklistCsvImport.Result.Ready -> {
                        val fresh = onlyNew(result.items, repository.getByTrip(tripId))
                        repository.addAll(fresh)
                        _messages.send(
                            context.getString(
                                R.string.checklist_imported,
                                fresh.size,
                                result.items.size - fresh.size,
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

    /** Says what stopped the import, in terms of the file the person is looking at. */
    private fun describe(result: ChecklistCsvImport.Result): String = when (result) {
        ChecklistCsvImport.Result.HeaderMismatch ->
            context.getString(R.string.error_import_header)

        is ChecklistCsvImport.Result.MissingColumns -> context.getString(
            R.string.error_import_missing_columns,
            result.columns.joinToString { context.getString(it.titleRes) },
        )

        is ChecklistCsvImport.Result.Empty -> context.getString(
            R.string.error_import_empty_column,
            result.line,
            context.getString(result.column.titleRes),
        )

        is ChecklistCsvImport.Result.Malformed -> context.getString(
            R.string.error_import_malformed,
            result.line,
            context.getString(result.column.titleRes),
            result.value,
        )

        is ChecklistCsvImport.Result.Ready -> ""
    }

    // endregion
}

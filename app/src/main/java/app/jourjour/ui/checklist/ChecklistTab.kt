package app.jourjour.ui.checklist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer.PinnedHandle
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.core.checklist.ChecklistSection
import app.jourjour.core.checklist.DropSide
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.money.Quantity
import app.jourjour.data.entity.ChecklistItem
import app.jourjour.ui.common.AIM_FILL_ALPHA
import app.jourjour.ui.common.AIM_STROKE_ALPHA
import app.jourjour.ui.common.AUTO_SCROLL_EDGE
import app.jourjour.ui.common.AUTO_SCROLL_STEP
import app.jourjour.ui.common.BLOCK_GAP
import app.jourjour.ui.common.BlockNotes
import app.jourjour.ui.common.BlockNotesToggle
import app.jourjour.ui.common.CARRIED_ALPHA
import app.jourjour.ui.common.ConfirmDeleteDialog
import app.jourjour.ui.common.Dragging
import app.jourjour.ui.common.EmptyList
import app.jourjour.ui.common.NOTES_TOGGLE_SIZE
import app.jourjour.ui.common.OverflowMenu
import app.jourjour.ui.common.aimedAlong
import app.jourjour.ui.common.aimedWhole
import app.jourjour.ui.common.draggedAfterLongPress
import app.jourjour.ui.common.edgeStep
import app.jourjour.ui.common.recordBlock
import kotlin.math.roundToInt

/**
 * What still has to be done before the trip.
 *
 * Read from a doorway with one hand full, so everything it can be asked is one tap: the
 * tick settles the item, the rest of the row opens it, and the heading folds the section.
 * What a whole section can be asked — renaming, deleting, settling the lot — is behind the
 * three dots beside its name, out of the way of the tapping.
 *
 * The one thing it is held for rather than tapped is the order: a line put down against
 * another line goes there, and a heading takes its section along with it.
 */
@Composable
fun ChecklistTab(
    state: ChecklistState,
    viewModel: ChecklistViewModel,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Which items have their notes unfolded, which belongs to the screen rather than to the
    // list: it says what is being read at this moment and is nobody's business tomorrow.
    var opened by rememberSaveable { mutableStateOf(emptySet<Long>()) }

    // The drag, which lives entirely on this screen: what is held, where the finger is on
    // the screen, how far below the top of what it picked up it took hold, and where
    // letting go would put it. None of it survives the gesture.
    var held by remember { mutableStateOf<Held?>(null) }
    var pointer by remember { mutableFloatStateOf(0f) }
    var grip by remember { mutableFloatStateOf(0f) }
    var aim by remember { mutableStateOf<Aim?>(null) }
    // Where the tab begins on the screen, which is what the thing being carried is drawn
    // against: it is drawn over the list, not in it.
    var page by remember { mutableFloatStateOf(0f) }
    // Where every line of the list stands, so that a finger anywhere on the screen can be
    // matched to the line under it. Measured rather than guessed: a line is as tall as the
    // wording on it and the font it is read at make it.
    val where = remember { mutableStateMapOf<Int, ClosedFloatingPointRange<Float>>() }
    // And where the list itself is, which is what tells a finger held near one of its ends
    // from one held in the middle.
    var viewport by remember { mutableStateOf(0f..0f) }

    // The list comes to a finger held near either end of it, so that a line can be carried
    // to a section that was off the screen when it was picked up. What moves is the list:
    // what is carried stays under the finger, being drawn from where the finger is, and
    // the aim is asked again because the lines have gone past underneath.
    val edge = with(LocalDensity.current) { AUTO_SCROLL_EDGE.toPx() }
    val fastest = with(LocalDensity.current) { AUTO_SCROLL_STEP.toPx() }
    val current by rememberUpdatedState(state)
    LaunchedEffect(held) {
        val carrying = held ?: return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val step = edgeStep(pointer, viewport, edge, fastest)
            if (step == 0f) continue
            if (listState.scrollBy(step) == 0f) continue
            aim = current.aimedAt(carrying, pointer, where)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { page = it.positionInWindow().y },
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            state.sections.isEmpty() ->
                EmptyList(
                    R.string.empty_checklist,
                    R.string.empty_checklist_hint,
                    R.string.about_checklist,
                )

            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 88.dp),
                modifier = Modifier.onGloballyPositioned {
                    val top = it.positionInWindow().y
                    viewport = top..(top + it.size.height)
                },
            ) {
                // Keyed by what the line is rather than where it stands, so that the line a
                // gesture lives on stays that line when the list changes under it — a tick
                // from the menu, a write from elsewhere. Keyed by place, the gesture would be
                // handed to whatever line had moved into the slot.
                itemsIndexed(state.rows, key = { _, row -> row.key }) { at, row ->
                    // A line scrolled out of the list is a line nothing can be dropped on,
                    // and a range left behind would go on answering for a stretch of
                    // screen that something else has since moved into.
                    DisposableEffect(at) { onDispose { where.remove(at) } }
                    // What keeps a line composed while the list runs out from under it. The
                    // line being carried is the one that must not be let go of: the gesture
                    // lives on it, so a list that dropped it at the top of the screen would
                    // end the drag halfway to where it was going.
                    val pinnable = LocalPinnableContainer.current
                    var pinned by remember { mutableStateOf<PinnedHandle?>(null) }
                    DisposableEffect(at) { onDispose { pinned?.release() } }

                    // Whether this line is part of what is being carried. A section is
                    // carried as its items: the heading and everything under it go together,
                    // that being what is being moved.
                    val carried = held?.carries(row) == true

                    // The one handler both kinds of line are dragged by: what is picked up
                    // differs, what happens to it on the way does not.
                    // One drag at a time, and each one answering only for itself: a second
                    // finger picking something else up while the first is carrying is not
                    // a second drag, and whatever a gesture that is not the one under way
                    // has to say — a line long gone from the screen being let go of — is
                    // not about what is being carried now.
                    val drag = { grabbing: Held, step: Dragging ->
                        val ours = held == grabbing
                        when (step) {
                            is Dragging.Grabbed -> if (held == null) {
                                pinned = pinnable?.pin()
                                held = grabbing
                                pointer = step.pointerY
                                // How far below the top of this line the finger took hold,
                                // which is how far below the finger it is drawn for as long
                                // as it is carried.
                                grip = step.pointerY - (where[at]?.start ?: step.pointerY)
                                aim = null
                            }
                            is Dragging.Moved -> if (ours) {
                                pointer = step.pointerY
                                aim = state.aimedAt(grabbing, step.pointerY, where)
                            }
                            Dragging.Dropped, Dragging.Cancelled -> {
                                // Only a finger lifted moves anything; a gesture taken away
                                // puts the thing back where it was.
                                if (ours && step == Dragging.Dropped) {
                                    aim?.let {
                                        viewModel.drop(grabbing, state.rows.getOrNull(it.at), it.side)
                                    }
                                }
                                pinned?.release()
                                pinned = null
                                if (ours) {
                                    held = null
                                    aim = null
                                }
                            }
                        }
                        Unit
                    }

                    Box(
                        modifier = Modifier
                            // Where the line rests, which is what a finger is matched
                            // against.
                            .onGloballyPositioned { placed ->
                                val top = placed.positionInWindow().y
                                where[at] = top..(top + placed.size.height)
                            }
                            // Gone from its place while it is carried, being drawn under
                            // the finger instead; a copy left behind would be a second one
                            // of it. The gap it leaves says where it came from.
                            .graphicsLayer { alpha = if (carried) 0f else 1f }
                            .aimed(aim?.takeIf { it.at == at && it.side != DropSide.INTO }),
                    ) {
                        when (row) {
                            is ChecklistRow.Heading -> SectionHeader(
                                section = row.section,
                                collapsed = state.isCollapsed(row.section),
                                onToggle = { viewModel.toggleSection(row.section.name) },
                                onRename = { viewModel.openRename(row.section.name) },
                                onDelete = { viewModel.askDeleteSection(row.section) },
                                onMarkAll = { viewModel.markSection(row.section, done = true) },
                                onUnmarkAll = { viewModel.markSection(row.section, done = false) },
                                onDrag = { drag(Held.Section(row.section.name), it) },
                                parted = at > 0,
                                isTarget = aim?.let { it.at == at && it.side == DropSide.INTO } == true,
                            )
                            is ChecklistRow.Item -> ItemRow(
                                item = row.item,
                                onToggle = { viewModel.toggleDone(row.item) },
                                onEdit = { viewModel.openSheet(row.item) },
                                onDrag = { drag(Held.Item(row.item.id), it) },
                                isNotesOpen = row.item.id in opened,
                                onToggleNotes = { opened = opened.toggling(row.item.id) },
                            )
                        }
                    }
                }
            }
        }

        // What is being carried, over the list and as far below the finger as the finger
        // was below its top when it took hold. Placed from the finger alone, so that
        // nothing the list does underneath — scrolling, or keeping the line the gesture
        // lives on somewhere off the screen — can move it out from under it.
        held?.let { carrying ->
            Carried(
                held = carrying,
                state = state,
                opened = opened,
                modifier = Modifier
                    .offset { IntOffset(0, (pointer - grip - page).roundToInt()) }
                    .graphicsLayer { alpha = CARRIED_ALPHA },
            )
        }
    }

    state.copy?.let { copy ->
        ChecklistCopySheet(
            copy = copy,
            onSelectTrip = viewModel::copySelectTrip,
            onSelectSection = viewModel::copySelectSection,
            onSelectItem = viewModel::copySelectItem,
            onConfirm = viewModel::confirmCopy,
            onDismiss = viewModel::closeCopy,
        )
    }

    state.rename?.let { rename ->
        SectionRenameDialog(
            rename = rename,
            onChange = viewModel::updateRename,
            onConfirm = viewModel::confirmRename,
            onDismiss = viewModel::closeRename,
        )
    }

    state.deletion?.let { deletion ->
        ConfirmDeleteDialog(
            message = stringResource(
                R.string.confirm_delete_checklist_section,
                deletion.name,
                deletion.count,
            ),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

/**
 * A section, as a band across the list.
 *
 * The deeper of the two shades, as a day of the plan and a category of the budget are: it
 * heads the blocks under it and is the page they stand on, not one more thing in the list.
 *
 * Struck through once nothing in it is waiting on anybody, the same mark its items carry,
 * because a section is only ever the sum of them. Held, it is the whole section that is
 * being picked up, for the same reason.
 */
@Composable
private fun SectionHeader(
    section: ChecklistSection,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMarkAll: () -> Unit,
    onUnmarkAll: () -> Unit,
    /** Null for the copy drawn under the finger, which is carried rather than picked up. */
    onDrag: ((Dragging) -> Unit)?,
    /** Whether anything stands above it — the first heading has nothing to be parted from. */
    parted: Boolean,
    /** Whether an item being carried would go into this section if it were let go here. */
    isTarget: Boolean = false,
) {
    val mark = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            // A hair of the page above every band but the first. A folded section is its
            // band alone, and a list folded away — which is what a packed list opens as —
            // would otherwise be one slab of colour with names written down it. Above an
            // open section it makes the gap below the last block the gap between two
            // blocks, so the column keeps one rhythm whatever is folded.
            .padding(top = if (parted) BLOCK_GAP else 0.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            // The whole band boxed rather than one edge of it struck: an item let go here
            // goes into the section, to the end of it, and not in front of or behind the
            // heading — which is what a strip would be saying.
            .then(
                if (isTarget) {
                    Modifier.aimedWhole(
                        fill = mark.copy(alpha = AIM_FILL_ALPHA),
                        stroke = mark.copy(alpha = AIM_STROKE_ALPHA),
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onToggle)
            .then(
                if (onDrag == null) Modifier else Modifier.draggedAfterLongPress(section.name, onDrag)
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                // The way a tree is drawn: pointing along the row while it is shut,
                // pointing at what it holds once it is open. An up arrow reads as a
                // button that scrolls, not as a branch.
                imageVector = if (collapsed) {
                    Icons.Default.KeyboardArrowRight
                } else {
                    Icons.Default.KeyboardArrowDown
                },
                contentDescription = stringResource(
                    if (collapsed) R.string.action_expand_section else R.string.action_collapse_section
                ),
            )
        }
        Text(
            text = section.name,
            style = MaterialTheme.typography.titleSmall,
            textDecoration = if (section.isSettled) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        SectionMenu(
            onRename = onRename,
            onDelete = onDelete,
            onMarkAll = onMarkAll,
            onUnmarkAll = onUnmarkAll,
        )
    }
}

/**
 * One thing to do, as a block standing on the band of its section — the block an event of
 * the plan and a line of the budget are, since it is the same kind of thing to read: one
 * record of a list, which opens on a tap.
 *
 * Three states, told apart the way each of them is: done is struck through, ruled out for
 * this trip is greyed and unticks nothing, and outstanding is plain. Ruled out is not a
 * third position of the tick — it says nothing about whether the thing was done, only
 * that nobody is asking.
 *
 * The tick stands against the top of the block and the wording is set down to sit level
 * with it, rather than the two being centred on each other: a block grows downwards, and
 * a tick that slid down a block grown by a long name would be beside nothing in
 * particular.
 */
@Composable
private fun ItemRow(
    item: ChecklistItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    /** Null for the copy drawn under the finger, which is carried rather than picked up. */
    onDrag: ((Dragging) -> Unit)?,
    isNotesOpen: Boolean,
    onToggleNotes: () -> Unit,
) {
    // Whether the notes have more in them than two lines show, which only the layout can
    // say. Kept here rather than with the text because the chevron it decides about
    // belongs to the block and stands at the foot of it.
    var notesOverflow by remember(item.notes) { mutableStateOf(false) }
    val showsToggle = item.notes.isNotBlank() && (notesOverflow || isNotesOpen)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .recordBlock()
            .clickable(onClick = onEdit)
            .then(if (onDrag == null) Modifier else Modifier.draggedAfterLongPress(item.id, onDrag)),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            ItemTick(item, onToggle)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = ITEM_TEXT_DROP, bottom = ITEM_TEXT_DROP, end = ITEM_INSET),
            ) {
                ItemTitle(item)
                // Under the wording and quieter than it, as an event's notes stand under
                // its name: the name is what is looked for, this is what is read once it
                // has been found.
                if (item.notes.isNotBlank()) {
                    BlockNotes(
                        text = item.notes,
                        isOpen = isNotesOpen,
                        overflows = notesOverflow,
                        onOverflow = { notesOverflow = it },
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // A line for the chevron once the notes are open, so that it never lies
                // over the last of what it has just opened.
                if (showsToggle && isNotesOpen) Spacer(Modifier.height(NOTES_TOGGLE_SIZE))
            }
        }
        if (showsToggle) {
            BlockNotesToggle(
                isOpen = isNotesOpen,
                onToggle = onToggleNotes,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** The tick, which settles the item and is the one thing on the block that does not open it. */
@Composable
private fun ItemTick(item: ChecklistItem, onToggle: () -> Unit) {
    Checkbox(
        checked = item.isDone,
        // The two things a row is asked, told apart by where the finger lands: the tick
        // settles the item, everything else opens it. The box keeps a target of its own
        // around it, since a tick is smaller than a fingertip — which is also what sets it
        // in from the edge of the block, by as much as the text of a budget line is.
        onCheckedChange = { onToggle() },
        enabled = !item.isSkipped,
    )
}

/** What the item is, and how many of it where somebody said. */
@Composable
private fun ItemTitle(item: ChecklistItem) {
    Text(
        // The count rides in brackets after the wording, the way a shopping list writes
        // it. Most items have none, and those rows read exactly as before.
        text = item.quantityMilli
            ?.let { "${item.title} (${MoneyFormat.formatQuantity(Quantity(it))})" }
            ?: item.title,
        style = MaterialTheme.typography.bodyLarge,
        textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
        color = when {
            item.isSkipped -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        },
    )
}

/**
 * How far the wording is set down from the top of its block: half of what the tick's
 * target is taller than a line of it, which puts the first line level with the tick.
 */
private val ITEM_TEXT_DROP = 12.dp

/** How far the wording keeps off the far edge of its block, as a budget line's figure does. */
private val ITEM_INSET = 12.dp

/**
 * What a section can be asked: renamed, deleted, and settled or unsettled in one go.
 *
 * Ticking a whole section off is what somebody does on the way out of the door with the
 * bag already packed, and doing it row by row is the part of a checklist nobody wants.
 * Items ruled out for this trip are left where they are, since their tick means nothing.
 */
@Composable
private fun SectionMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMarkAll: () -> Unit,
    onUnmarkAll: () -> Unit,
) {
    OverflowMenu { dismiss ->
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_mark_all)) },
            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
            onClick = { dismiss(); onMarkAll() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_unmark_all)) },
            leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) },
            onClick = { dismiss(); onUnmarkAll() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_edit)) },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
            onClick = { dismiss(); onRename() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
            onClick = { dismiss(); onDelete() },
        )
    }
}

/**
 * What is being carried, as it is drawn under the finger: one item's block, or a section's
 * band with its items under it as the list shows them.
 *
 * Built from the list's own rows, so that it is the same thing the finger picked up, and
 * answering to nothing: the gesture belongs to the line it began on, which stays in the
 * list for as long as it lasts.
 */
@Composable
private fun Carried(
    held: Held,
    state: ChecklistState,
    opened: Set<Long>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        state.rows.forEachIndexed { at, row ->
            if (!held.carries(row)) return@forEachIndexed
            when (row) {
                is ChecklistRow.Heading -> SectionHeader(
                    section = row.section,
                    collapsed = state.isCollapsed(row.section),
                    onToggle = {},
                    onRename = {},
                    onDelete = {},
                    onMarkAll = {},
                    onUnmarkAll = {},
                    onDrag = null,
                    // With the hair above it the list gives it, so that it hangs under the
                    // finger exactly as far down as it was taken hold of.
                    parted = at > 0,
                )
                is ChecklistRow.Item -> ItemRow(
                    item = row.item,
                    onToggle = {},
                    onEdit = {},
                    onDrag = null,
                    isNotesOpen = row.item.id in opened,
                    onToggleNotes = {},
                )
            }
        }
    }
}

/** Which line a drag is pointing at, and which part of it letting go would land on. */
data class Aim(val at: Int, val side: DropSide)

/**
 * Where the finger is pointing, and what letting go there would mean.
 *
 * Between two of its own kind, for either: an item between two items, a section between
 * two sections, which half of the line the finger is in deciding the side, there being no
 * space between two lines to aim at. And an item over a heading is aimed at the whole of
 * it — into that section, at the end.
 *
 * What is being carried is no target — the one place it cannot be put is where it is —
 * and is passed over before anything is matched at all: the line the gesture lives on is
 * kept by the list wherever it has room for it, which is not where it belongs, and the
 * place it reports could be standing over a line that is a target.
 */
private fun ChecklistState.aimedAt(
    held: Held,
    pointerY: Float,
    where: Map<Int, ClosedFloatingPointRange<Float>>,
): Aim? {
    val (at, range) = where.entries.firstOrNull { (at, range) ->
        pointerY in range && rows.getOrNull(at)?.let { !held.carries(it) } == true
    } ?: return null
    val row = rows[at]
    val half = if (pointerY < (range.start + range.endInclusive) / 2) {
        DropSide.BEFORE
    } else {
        DropSide.AFTER
    }
    val side = when (held) {
        is Held.Item -> when (row) {
            is ChecklistRow.Item -> half
            is ChecklistRow.Heading -> DropSide.INTO
        }
        is Held.Section -> half.takeIf { row is ChecklistRow.Heading } ?: return null
    }
    return Aim(at, side)
}

/** Whether this line is one of those being carried, and so travels with the finger. */
private fun Held.carries(row: ChecklistRow): Boolean = when (this) {
    is Held.Item -> row is ChecklistRow.Item && row.item.id == id
    is Held.Section -> when (row) {
        is ChecklistRow.Heading -> row.section.name == name
        is ChecklistRow.Item -> row.item.section == name
    }
}

/**
 * Marks a line as where the drag would land in front of it or behind it: a strip along the
 * edge it would go against. A heading an item would go into marks itself, see [SectionHeader].
 *
 * Blue and no other colour, and nothing is ever refused — a checklist always has room for
 * one more line between two of its lines, which is the whole difference from a schedule,
 * where a place is also a question of whether the hours are free.
 */
@Composable
private fun Modifier.aimed(aim: Aim?): Modifier {
    if (aim == null) return this
    val mark = MaterialTheme.colorScheme.primary.copy(alpha = AIM_STROKE_ALPHA)
    return aimedAlong(atTop = aim.side == DropSide.BEFORE, colour = mark)
}

/** The same set with one id in it or out of it, which is what a tap on a chevron does. */
private fun Set<Long>.toggling(id: Long): Set<Long> = if (id in this) this - id else this + id

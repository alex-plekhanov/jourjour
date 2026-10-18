package app.jourjour.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer.PinnedHandle
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jourjour.R
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.schedule.ChainBand
import app.jourjour.core.schedule.DropEdge
import app.jourjour.core.schedule.EventBand
import app.jourjour.core.schedule.EventPart
import app.jourjour.core.schedule.GapBand
import app.jourjour.core.schedule.GapEdge
import app.jourjour.core.schedule.OverlapBand
import app.jourjour.core.schedule.PlacedEvent
import app.jourjour.core.schedule.ScheduleDay
import app.jourjour.core.schedule.endShift
import app.jourjour.core.schedule.shiftLabel
import app.jourjour.core.schedule.startShift
import app.jourjour.data.entity.ScheduleEventDetails
import app.jourjour.ui.common.AIM_STROKE_ALPHA
import app.jourjour.ui.common.AIM_THICKNESS
import app.jourjour.ui.common.AUTO_SCROLL_EDGE
import app.jourjour.ui.common.AUTO_SCROLL_STEP
import app.jourjour.ui.common.BLOCK_CORNER
import app.jourjour.ui.common.BLOCK_ELEVATION
import app.jourjour.ui.common.BLOCK_GAP
import app.jourjour.ui.common.BLOCK_MARGIN
import app.jourjour.ui.common.CARRIED_ALPHA
import app.jourjour.ui.common.Dragging
import app.jourjour.ui.common.FieldLabel
import app.jourjour.ui.common.aimedAlong
import app.jourjour.ui.common.edgeStep
import app.jourjour.ui.common.recordBlock
import app.jourjour.ui.common.PaymentMethodMark
import app.jourjour.ui.common.PrimaryButton
import app.jourjour.ui.common.SecondaryButton
import app.jourjour.ui.common.formFieldColors
import app.jourjour.ui.common.formatDuration
import app.jourjour.ui.common.rememberDayFormatter
import app.jourjour.ui.common.rememberTimeFormatter
import app.jourjour.ui.theme.FieldShape
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/** The mark that lies across a join. */
private val CHAIN_SIZE = 24.dp

/** Wider than the mark, so that the tap for it is not a bullseye. */
private val CHAIN_TOUCH_WIDTH = 44.dp

/**
 * As wide as a padded clock with room for the small figure a zone change raises off it.
 *
 * The text and nothing else: the mark beside it is a drawing and stays the size it is,
 * while these are figures and grow with the system font, so the column they stand in is
 * worked out rather than fixed. A box fixed in dp lost the raised figure off its right
 * edge the first time anybody turned the font up.
 */
private val CLOCK_TEXT_WIDTH = 50.dp

/** What stands between a mark and the figure it belongs to. */
private val MARK_GAP = 4.dp

/** How far a block's own content is set in from its edge. */
private val BLOCK_INSET = 12.dp

/**
 * And how far the clocks are, which is less. They are a column of figures with room to
 * find for a raised one, and the block's edge is rounded away from them in any case.
 */
private val CLOCK_INSET = 6.dp

/** The chevron that unfolds the rest of an event's notes. */
private val NOTES_TOGGLE_SIZE = 24.dp

/** How much of an event's notes a block shows before it takes asking. */
private const val NOTES_LINES = 2

/** What sets the name in from the clocks beside it, and the notes in to the name. */
private val NAME_INSET = 8.dp

/** The small marks beside a time: read at a glance, never looked at. */
private val MARK_SIZE = 14.dp

/** How much of the colour a whole band takes when it is the target, rather than an edge. */
private const val AIM_FILL_ALPHA = 0.18f

/**
 * What happens and when, day by day.
 *
 * Every day of the trip is a heading, empty ones included, and under each one an unbroken
 * column: what is booked, what is free, where two things touch and where they claim the
 * same minutes. Nothing is drawn to scale — an hour and a fortnight take the same room —
 * because this is read as a list of what happens next rather than as a chart of how long
 * it takes. What the blocks say instead is what touches what.
 */
@Composable
fun ScheduleTab(
    state: ScheduleState,
    viewModel: ScheduleViewModel,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    // The day the trip is on, brought into view once. Only once: the tab is somewhere a
    // person works, and scrolling out from under them on the next write would be worse
    // than opening at the top would have been.
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading, state.focusRow) {
        if (!focused && !state.isLoading) {
            focused = true
            listState.scrollToItem(state.focusRow)
        }
    }

    // Which notes are unfolded, which belongs to the screen rather than to the schedule:
    // it says what is being read at this moment and is nobody's business tomorrow.
    var opened by rememberSaveable { mutableStateOf(emptySet<Long>()) }

    // Which day is being described, by epoch day. The form for it is one field and no
    // arithmetic, so it is opened and closed here rather than kept anywhere.
    var describing by rememberSaveable { mutableStateOf<Long?>(null) }

    // The drag, which lives entirely on this screen: what is held, how far the finger has
    // carried it, where the finger is, and where letting go would put it. None of it
    // survives the gesture.
    var dragged by remember { mutableStateOf<Long?>(null) }
    var carried by remember { mutableFloatStateOf(0f) }
    var pointer by remember { mutableFloatStateOf(0f) }
    var aim by remember { mutableStateOf<Aim?>(null) }
    // Where every line of the list stands, so that a finger anywhere on the screen can be
    // matched to the band under it. Measured rather than guessed: the rows are of every
    // height a name can make them.
    val where = remember { mutableStateMapOf<Int, ClosedFloatingPointRange<Float>>() }
    // And where the list itself is, which is what tells a finger held near one of its ends
    // from one held in the middle.
    var viewport by remember { mutableStateOf(0f..0f) }

    // The list comes to a finger held near either end of it, so that a chain can be
    // carried to a day that was off the screen when it was picked up. What moves is the
    // list: the block stays under the finger, which is why the scroll is added to what the
    // finger has carried, and the aim is asked again because the bands have gone past.
    val edge = with(LocalDensity.current) { AUTO_SCROLL_EDGE.toPx() }
    val fastest = with(LocalDensity.current) { AUTO_SCROLL_STEP.toPx() }
    val current by rememberUpdatedState(state)
    LaunchedEffect(dragged) {
        val held = dragged ?: return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val step = edgeStep(pointer, viewport, edge, fastest)
            if (step == 0f) continue
            val moved = listState.scrollBy(step)
            if (moved == 0f) continue
            carried += moved
            aim = current.aimedAt(viewModel, held, pointer, where)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 88.dp),
                modifier = Modifier.onGloballyPositioned {
                    val top = it.positionInWindow().y
                    viewport = top..(top + it.size.height)
                },
            ) {
                // The tab cannot be empty — the days are there whether anything is in them
                // or not — so what it is for is said above them, and goes for good with
                // the first event.
                if (!state.hasEvents) item(key = "about") { AboutTheTab() }

                itemsIndexed(state.rows, key = { at, _ -> at }) { at, row ->
                    // A line scrolled out of the list is a line nothing can be dropped on,
                    // and a range left behind would go on answering for a stretch of
                    // screen that something else has since moved into.
                    DisposableEffect(at) { onDispose { where.remove(at) } }
                    // What keeps a line composed while the list runs out from under it.
                    // A block being carried is the one line that must not be let go of:
                    // the gesture lives on it, so a list that dropped it at the top of the
                    // screen would end the drag halfway to where it was going.
                    val pinnable = LocalPinnableContainer.current
                    var pinned by remember { mutableStateOf<PinnedHandle?>(null) }
                    DisposableEffect(at) { onDispose { pinned?.release() } }
                    // A chain is drawn by whatever stands under it rather than as a line
                    // of its own, so that it lies across the join and the two blocks are
                    // left the same hair of space every other pair of blocks has.
                    OverChain(state.chainAbove(at), viewModel) {
                        Box(modifier = Modifier.onGloballyPositioned { placed ->
                            val top = placed.positionInWindow().y
                            where[at] = top..(top + placed.size.height)
                        }) {
                            when (row) {
                                // Reading is not writing here either: the line is read
                                // off the heading while a schedule is being read, and
                                // written where everything else about the day is.
                                is ScheduleRow.Heading -> DayHeading(
                                    day = row.day,
                                    summary = row.summary,
                                    onClick = { describing = row.day.date.toEpochDay() }
                                        .takeIf { !state.isViewing },
                                )
                                is ScheduleRow.Content -> when (val band = row.band) {
                                    is EventBand -> EventBlock(
                                        band = band,
                                        details = state.details[band.placed.id],
                                        corners = state.cornersAt(at),
                                        // Reading is not writing: the form is where an
                                        // event is changed, and while the schedule is
                                        // being read a tap on a block does nothing.
                                        onClick = { viewModel.openSheet(band.placed) }
                                            .takeIf { !state.isViewing },
                                        // The notes are on the block either way. They are
                                        // what the event is about as much as its name is,
                                        // and a line of them read while a plan is being
                                        // written is a line nobody has to open a form for.
                                        isNotesOpen = band.placed.id in opened,
                                        onToggleNotes = {
                                            opened = opened.toggling(band.placed.id)
                                        },
                                        carried = carried.takeIf { dragged == band.placed.id },
                                        onDrag = if (state.isViewing) null else { step ->
                                            when (step) {
                                                Dragging.Grabbed -> {
                                                    pinned = pinnable?.pin()
                                                    dragged = band.placed.id
                                                    carried = 0f
                                                    aim = null
                                                }
                                                is Dragging.Moved -> {
                                                    carried += step.dy
                                                    pointer = step.pointerY
                                                    aim = state.aimedAt(
                                                        viewModel = viewModel,
                                                        dragged = band.placed.id,
                                                        pointerY = step.pointerY,
                                                        where = where,
                                                    )
                                                }
                                                Dragging.Dropped -> {
                                                    aim?.let {
                                                        viewModel.drop(band.placed.id, it.at, it.edge)
                                                    }
                                                    pinned?.release()
                                                    pinned = null
                                                    dragged = null
                                                    carried = 0f
                                                    aim = null
                                                }
                                            }
                                        },
                                    )
                                    is GapBand -> GapBlock(
                                        band = band,
                                        onClick = { viewModel.openSheet(band) }
                                            .takeIf { !state.isViewing && dragged == null },
                                        aim = aim?.takeIf { it.at == at },
                                        invites = !state.isViewing,
                                    )
                                    is OverlapBand -> OverlapBlock(band, aim?.takeIf { it.at == at })
                                    // Drawn by the block below it, see above.
                                    is ChainBand -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    describing?.let { epochDay ->
        val date = LocalDate.ofEpochDay(epochDay)
        DayDescriptionDialog(
            date = date,
            summary = state.summaryOf(date),
            onConfirm = {
                viewModel.describe(date, it)
                describing = null
            },
            onDismiss = { describing = null },
        )
    }

    // The form of an event is not drawn here: it takes the whole screen, top bar and all,
    // and a tab cannot put anything over the bars it lives under. See TripScreen.
}

/**
 * What a day is about, in one field.
 *
 * One line is what it is for — "flight to Morocco", "Istanbul" — so the field is one line
 * high and grows with what is typed rather than fixing a size for text that is usually two
 * words. Emptying it is how a description is taken back, which is why the form saves a
 * blank as readily as anything else.
 */
@Composable
private fun DayDescriptionDialog(
    date: LocalDate,
    summary: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(date, summary) { mutableStateOf(summary) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(date) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(rememberDayFormatter())) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { FieldLabel(stringResource(R.string.field_day_summary)) },
                singleLine = false,
                shape = FieldShape,
                colors = formFieldColors(),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            PrimaryButton(onClick = { onConfirm(text) }, text = stringResource(R.string.action_save))
        },
        dismissButton = {
            SecondaryButton(onClick = onDismiss, text = stringResource(R.string.action_cancel))
        },
    )
}

@Composable
private fun AboutTheTab() {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            text = stringResource(R.string.about_plan),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.empty_plan_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

/**
 * A date across the list, and beside it what the day is about.
 *
 * The description is the day's own and belongs to no event on it — "flight to Morocco",
 * "Istanbul", the name of wherever this day happens. It is set more quietly than the date,
 * which is what the heading is found by; and where it runs to more than one line the band
 * grows downwards with the date staying where it was, since a date that drifted down the
 * band would be a date nobody could scan a column of.
 *
 * Tapping the heading is how it is written, there being nowhere else to write it: a day
 * with nothing booked in it has no form of its own, and those are exactly the days most
 * worth naming while a trip is still being planned.
 */
@Composable
private fun DayHeading(day: ScheduleDay, summary: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = day.date.format(rememberDayFormatter()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
        )
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.End,
                // Takes what the date leaves and wraps inside it, rather than pushing the
                // date off the other end of the row.
                modifier = Modifier.weight(1f).padding(start = 16.dp),
            )
        }
    }
}

/** Which of a block's corners are rounded; a square one says it carries on into the next. */
data class Corners(val top: Boolean = true, val bottom: Boolean = true)

/**
 * One event, or the part of one that falls in this day. Two lines tall at the least.
 *
 * The four corners are the four facts: when it begins, when it ends, how long that is, and
 * what it is expected to cost. The name goes between them, at the top, where the eye
 * lands. Nothing is said twice — the date is in the heading above and the notes are on the
 * form; and the figures are set quietly, the marks beside them quieter still, because they
 * are read at a glance and the name is what is actually looked at.
 *
 * A name too long for one line grows the block from the middle: the two clocks stay
 * against the edges they belong to, which is the whole of what the shape is saying.
 *
 * An event carried over from yesterday or into tomorrow is cut square along that join and
 * prints only the end it actually has in this day — the other is left out altogether, mark
 * and all, an empty line being worse than none. The length is the whole of it either way,
 * since half a flight is not a figure anybody wants.
 */
@Composable
private fun EventBlock(
    band: EventBand,
    details: ScheduleEventDetails?,
    corners: Corners,
    onClick: (() -> Unit)?,
    isNotesOpen: Boolean = false,
    onToggleNotes: () -> Unit = {},
    /** How far the finger has carried it, or nothing while it is where it belongs. */
    carried: Float? = null,
    onDrag: ((Dragging) -> Unit)? = null,
) {
    val placed = band.placed
    // Where this block stands, so that the finger's place on the screen can be worked out
    // from its place on the block. Measured, since a block is as tall as its name makes it.
    var top by remember { mutableFloatStateOf(0f) }
    // Whether the notes have more in them than two lines show, which only the layout can
    // say. Kept here rather than with the text because the control it decides about
    // belongs to the block and stands at the foot of it.
    var notesOverflow by remember(placed.event.notes) { mutableStateOf(false) }
    // Money belongs to the piece that carries the end. An event spread over three days is
    // one event and costs what it costs once; printing the figure under every heading it
    // appears under would read as three nights at that price rather than one stay. The
    // last piece is where it goes, being where the event is finished with.
    val hasCost = band.part.closesHere &&
        details?.event?.amountMilli != null &&
        details.currency != null
    val showsToggle = band.showsNotes && (notesOverflow || isNotesOpen)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Lifted and carried: it follows the finger, and is drawn over everything else
            // on the way so that it is never half behind the list it is being moved about.
            .zIndex(if (carried == null) 0f else 1f)
            .offset { IntOffset(0, carried?.roundToInt() ?: 0) }
            .graphicsLayer { alpha = if (carried == null) 1f else CARRIED_ALPHA }
            // Raised off the empty time around it, which is what tells the two apart at a
            // glance: what is booked stands on the page, what is free is the page. It
            // clips to the corners the block has as it lifts them, so the press ripple
            // keeps to those rather than washing over the square ones it does not.
            .recordBlock(cornerShape(corners))
            // Measured here and nowhere earlier: a finger's place on this block is given
            // in the space the gesture below sees, which is this one — carried offset and
            // all. Taken from above the offset it would be the place the block started at,
            // and the aim would never leave where the drag began.
            .onGloballyPositioned { top = it.positionInWindow().y }
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .then(
                if (onDrag == null) {
                    Modifier
                } else {
                    // Held before it is moved: a list is scrolled with the same finger,
                    // and an event that slid out of place on a flick would be unusable.
                    Modifier.pointerInput(placed.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDrag(Dragging.Grabbed) },
                            onDragEnd = { onDrag(Dragging.Dropped) },
                            onDragCancel = { onDrag(Dragging.Dropped) },
                            onDrag = { change, amount ->
                                change.consume()
                                onDrag(Dragging.Moved(amount.y, top + change.position.y))
                            },
                        )
                    }
                }
            )
            .padding(start = CLOCK_INSET, end = BLOCK_INSET, top = 8.dp, bottom = 8.dp),
    ) {
        Row(
            // What lets the clocks reach the ends of a block whose height is everything
            // else's: a row of wrapped height cannot be filled, only one that has been
            // asked how short it could be.
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            // Against the whole block and not against the name: the end of an event is
            // the end of *it*, notes, money and all, and a clock that stopped where the
            // name did would be pointing at the name.
            Column(
                modifier = Modifier.width(clockColumnWidth()).fillMaxHeight(),
                verticalArrangement = pinned(band.part.opensHere, band.part.closesHere),
            ) {
                if (band.part.opensHere) {
                    Marked(R.drawable.ic_time_start, clock(placed.start), placed.startShift)
                }
                if (band.part.closesHere) {
                    Marked(R.drawable.ic_time_end, clock(placed.end), placed.endShift)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row {
                    Text(
                        text = placed.event.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(horizontal = NAME_INSET),
                    )
                    // The length, and nothing else on this side. What the event is
                    // expected to cost has a line of its own at the foot of the block, so
                    // the name runs the full width up to the one figure always there.
                    Marked(R.drawable.ic_time_span, formatDuration(placed.duration.toMinutes()))
                }

                // Under the name and set in to it, which is where a second line of the
                // same thing belongs — but running the whole width, since nothing stands
                // to the right of it.
                if (band.showsNotes) {
                    Notes(
                        text = placed.event.notes,
                        isOpen = isNotesOpen,
                        overflows = notesOverflow,
                        onOverflow = { notesOverflow = it },
                        modifier = Modifier.padding(start = NAME_INSET, top = 4.dp),
                    )
                }

                // Last, in the corner: money is the one thing here that most events do
                // not have, so it takes a line only where there is one to take — and only
                // on the piece that ends the event, see [hasCost].
                if (hasCost) PlannedCost(details, modifier = Modifier.align(Alignment.End))

                // A line for the chevron where the foot of the block has nothing else on
                // it, so that it never lies over the last of the notes it has just
                // opened. Where there is money down there it shares that line, the
                // figures being against the right and the chevron in the middle.
                if (showsToggle && isNotesOpen && !hasCost) {
                    Spacer(Modifier.height(NOTES_TOGGLE_SIZE))
                }
            }
        }

        // Against the foot of the block and in the middle of it, which is where a control
        // for the whole block belongs — not in the middle of the notes, which are only
        // what it happens to act on.
        if (showsToggle) {
            NotesToggle(
                isOpen = isNotesOpen,
                onToggle = onToggleNotes,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * An event's notes, under its name and above what it is expected to cost.
 *
 * Two lines is the whole of the question. Notes that fit in two are simply there, with no
 * control at all — a chevron that can only ever say "that was all of it" is worse than no
 * chevron. Longer notes run out instead of stopping: the text fades away down those two
 * lines, which says there is more without spending a line on saying it.
 *
 * @param onOverflow told whether there is more than these lines show, which is what the
 *   block needs in order to decide about its chevron. Reported from the shut state alone:
 *   open, the text has no overflow and the answer would be "nothing more" every time.
 */
@Composable
private fun Notes(
    text: String,
    isOpen: Boolean,
    overflows: Boolean,
    onOverflow: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (isOpen) Int.MAX_VALUE else NOTES_LINES,
        onTextLayout = { if (!isOpen) onOverflow(it.hasVisualOverflow) },
        modifier = modifier
            .fillMaxWidth()
            .then(if (overflows && !isOpen) Modifier.fadingOut() else Modifier),
    )
}

/** The chevron that opens the rest of the notes, or shuts them again. */
@Composable
private fun NotesToggle(isOpen: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_double_down),
        contentDescription = stringResource(
            if (isOpen) R.string.action_hide_notes else R.string.action_show_notes
        ),
        tint = MaterialTheme.colorScheme.outline,
        modifier = modifier
            .size(NOTES_TOGGLE_SIZE)
            .clip(RoundedCornerShape(NOTES_TOGGLE_SIZE / 2))
            .clickable(onClick = onToggle)
            .rotate(if (isOpen) 180f else 0f),
    )
}

/**
 * Text that runs out rather than stopping: whole at the top, gone at the bottom.
 *
 * Drawn off-screen first and then multiplied by the gradient, which is the only way to
 * fade what has already been painted — an alpha on the text would fade all of it evenly
 * and say nothing about where it was cut off.
 */
private fun Modifier.fadingOut(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent)),
            blendMode = BlendMode.DstIn,
        )
    }

/** Against the top, against the bottom, or one of each with the block stretching between. */
private fun pinned(top: Boolean, bottom: Boolean): Arrangement.Vertical = when {
    top && bottom -> Arrangement.SpaceBetween
    bottom -> Arrangement.Bottom
    else -> Arrangement.Top
}

/**
 * How wide the column of clocks stands: the mark, the gap, and the figures as the system
 * font has them. Only the last of the three is text, so only the last of the three grows.
 */
@Composable
private fun clockColumnWidth(): Dp =
    MARK_SIZE + MARK_GAP + CLOCK_TEXT_WIDTH * LocalDensity.current.fontScale

/**
 * A small mark and the figure beside it, with — where the clocks moved — how far by.
 *
 * The shift is set as a ticket sets the day it lands on: a small figure raised off the
 * hour rather than a second thing beside it, since it belongs to that hour and has no
 * business widening the column and shunting the name along.
 */
@Composable
private fun Marked(icon: Int, text: String, shift: Long? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(MARK_SIZE),
        )
        Text(
            text = buildAnnotatedString {
                append(text)
                // Only where they moved: a nought would be on every line of every trip.
                if (shift != null) {
                    withStyle(
                        SpanStyle(
                            fontSize = 8.sp,
                            baselineShift = BaselineShift.Superscript,
                        )
                    ) {
                        append(shiftLabel(shift))
                    }
                }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = MARK_GAP),
        )
    }
}

/** What the event is expected to cost, where anybody has said — and what it is for. */
@Composable
private fun PlannedCost(details: ScheduleEventDetails?, modifier: Modifier = Modifier) {
    val amount = details?.event?.amountMilli ?: return
    val currency = details.currency ?: return
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = MoneyFormat.formatAmountWithCurrency(Money(amount), currency),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        details.category?.let {
            Text(text = it.icon, modifier = Modifier.padding(start = 6.dp))
        }
        // How it is to be paid for, last and always in the same place: a block whose
        // method is unsaid keeps the room so that the costs of a day end at one edge.
        PaymentMethodMark(
            method = details.paymentMethod,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * Empty time: one line, what to do with it on the left, how much of it there is on the
 * right where that is worth knowing.
 *
 * Between two events the length is the useful number — three hours free is the question
 * being asked. The stretches at either end of a day are the night, and how long the night
 * is helps nobody.
 */
@Composable
private fun GapBlock(
    band: GapBand,
    onClick: (() -> Unit)?,
    aim: Aim? = null,
    /** Whether the way in is worth saying. It is not while the schedule is being read. */
    invites: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BLOCK_MARGIN, vertical = BLOCK_GAP)
            .clip(RoundedCornerShape(BLOCK_CORNER))
            .aimed(aim)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = BLOCK_INSET, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Said quietly, and on every gap rather than once: this is the way in, and a line
        // loud enough to be noticed twice would be shouted a hundred times down a
        // fortnight. It goes altogether where there is no way in — a schedule being read
        // is not a schedule anybody is adding to.
        Text(
            text = if (invites) stringResource(R.string.schedule_tap_to_add) else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        if (band.edge == GapEdge.BETWEEN) {
            Marked(icon = R.drawable.ic_time_span, text = formatDuration(band.length.toMinutes()))
        }
    }
}

/**
 * Minutes claimed twice: one line in the colours a destructive action is given, square
 * against the two events that claim them so the column stays unbroken.
 *
 * It opens nothing. Which of the two is wrong is not a question it can answer, and either
 * of them is one tap away above and below.
 */
@Composable
private fun OverlapBlock(band: OverlapBand, aim: Aim? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BLOCK_MARGIN, vertical = BLOCK_GAP)
            // While a drag is aimed at it the band says where that drag would land instead
            // of what is wrong with the schedule: one thing at a time, and the thing being
            // answered right now is the one the finger is asking. It comes off the page
            // while it is saying that — a shadow under a mark that is only a hint would
            // be claiming the minutes are already somebody's.
            .then(if (aim == null) Modifier.shadow(BLOCK_ELEVATION) else Modifier)
            .background(
                if (aim == null) MaterialTheme.colorScheme.errorContainer else Color.Transparent
            )
            .aimed(aim)
            .padding(horizontal = BLOCK_INSET, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.schedule_overlap),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Marked(icon = R.drawable.ic_time_span, text = formatDuration(band.length.toMinutes()))
    }
}

/**
 * Two events with nothing between them, and whether one follows the other.
 *
 * A pale chain is a coincidence — they happen to touch. A solid one is a consequence: the
 * bags come off *this* flight, and if the flight moves they move with it. The tap is what
 * says which, there being nowhere else to say it.
 *
 * It is drawn by the block **below** the join, lying half over that block and half over
 * the one above — which is the only order that works: a list draws its lines in turn, so
 * anything hanging downwards would be painted over by whatever comes next.
 */
@Composable
private fun OverChain(
    chain: ChainBand?,
    viewModel: ScheduleViewModel,
    content: @Composable () -> Unit,
) {
    if (chain == null) return content()
    Box(modifier = Modifier.fillMaxWidth()) {
        content()
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = -CHAIN_SIZE / 2)
                .size(width = CHAIN_TOUCH_WIDTH, height = CHAIN_SIZE)
                .clip(RoundedCornerShape(CHAIN_SIZE / 2))
                .clickable { viewModel.toggleChain(chain) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chain),
                contentDescription = stringResource(
                    if (chain.linked) R.string.action_unchain else R.string.action_chain
                ),
                // Pale where the two only happen to touch, because that is a fact about
                // the clock and not something anybody has said: it is there to be tapped,
                // not to be read.
                tint = if (chain.linked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                modifier = Modifier.size(CHAIN_SIZE),
            )
        }
    }
}

/** Rounded where the block ends, cut square where whatever is next carries on from it. */
private fun cornerShape(corners: Corners) = RoundedCornerShape(
    topStart = if (corners.top) BLOCK_CORNER else 0.dp,
    topEnd = if (corners.top) BLOCK_CORNER else 0.dp,
    bottomStart = if (corners.bottom) BLOCK_CORNER else 0.dp,
    bottomEnd = if (corners.bottom) BLOCK_CORNER else 0.dp,
)

internal val EventPart.opensHere: Boolean
    get() = this == EventPart.WHOLE || this == EventPart.STARTS

internal val EventPart.closesHere: Boolean
    get() = this == EventPart.WHOLE || this == EventPart.ENDS

/** Hours and minutes as the locale writes them. */
@Composable
private fun clock(at: ZonedDateTime): String = at.format(rememberTimeFormatter())




/**
 * Which corners a block has, read from what stands above and below it.
 *
 * Square means "this carries on": into tomorrow, or into the minutes it has been found to
 * share with something else. A clash is drawn square against both events that claim it,
 * so the three of them read as one run rather than as three separate things.
 */
private fun ScheduleState.cornersAt(at: Int): Corners {
    val band = (rows.getOrNull(at) as? ScheduleRow.Content)?.band as? EventBand ?: return Corners()
    val above = (rows.getOrNull(at - 1) as? ScheduleRow.Content)?.band
    val below = (rows.getOrNull(at + 1) as? ScheduleRow.Content)?.band
    return Corners(
        top = band.part.opensHere && above !is OverlapBand,
        bottom = band.part.closesHere && below !is OverlapBand,
    )
}

/** The chain that belongs to this line, which is the one standing immediately above it. */
private fun ScheduleState.chainAbove(at: Int): ChainBand? =
    (rows.getOrNull(at - 1) as? ScheduleRow.Content)?.band as? ChainBand

/** Whether this part of an event has notes worth unfolding under it. */
private val EventBand.showsNotes: Boolean
    get() = placed.event.notes.isNotBlank() && part.closesHere

/** The same set with one id in it or out of it, which is what a tap on a chevron does. */
private fun Set<Long>.toggling(id: Long): Set<Long> = if (id in this) this - id else this + id

/** Where letting go would put the thing being dragged, and whether there is room for it. */
data class Aim(val at: Int, val edge: DropEdge, val fits: Boolean)

/**
 * Where the finger is pointing, and what letting go there would mean.
 *
 * A gap has two ends and they say opposite things, so which half the finger is in decides
 * which is offered — and the other is tried where that one is no target at all, since the
 * stretches at the ends of a day have only one side to line up against.
 */
private fun ScheduleState.aimedAt(
    viewModel: ScheduleViewModel,
    dragged: Long,
    pointerY: Float,
    where: Map<Int, ClosedFloatingPointRange<Float>>,
): Aim? {
    val at = where.entries.firstOrNull { pointerY in it.value }?.key ?: return null
    val band = (rows.getOrNull(at) as? ScheduleRow.Content)?.band ?: return null
    val range = where.getValue(at)
    val edges = when {
        band is OverlapBand -> listOf(DropEdge.WHOLE)
        band !is GapBand -> return null
        band.edge == GapEdge.WHOLE_DAY -> listOf(DropEdge.WHOLE)
        pointerY < (range.start + range.endInclusive) / 2 -> listOf(DropEdge.TOP, DropEdge.BOTTOM)
        else -> listOf(DropEdge.BOTTOM, DropEdge.TOP)
    }
    edges.forEach { edge ->
        viewModel.aim(dragged, at, edge)?.let { return Aim(at, edge, it) }
    }
    return null
}

/**
 * Marks a band as where the drag would land: a strip against the end it would line up
 * with, or the whole band filled where the whole band is the target.
 *
 * Blue for a place there is room in and red for one there is not — red being a question
 * rather than a refusal, since letting go there opens the form on those times with what
 * they run into.
 */
@Composable
private fun Modifier.aimed(aim: Aim?): Modifier {
    if (aim == null) return this
    val base = if (aim.fits) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    // Lighter than the colour it is taken from: this is a hint about what would happen,
    // not a line anybody has drawn yet, and it sits under a block being carried over it.
    val mark = base.copy(alpha = AIM_STROKE_ALPHA)
    return when (aim.edge) {
        DropEdge.WHOLE -> this
            .background(base.copy(alpha = AIM_FILL_ALPHA))
            .border(AIM_THICKNESS, mark, RoundedCornerShape(BLOCK_CORNER))
        DropEdge.TOP -> aimedAlong(atTop = true, colour = mark)
        DropEdge.BOTTOM -> aimedAlong(atTop = false, colour = mark)
    }
}

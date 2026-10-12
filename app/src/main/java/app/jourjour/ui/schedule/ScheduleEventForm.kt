package app.jourjour.ui.schedule

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.jourjour.R
import app.jourjour.core.calendar.DeviceCalendar
import app.jourjour.ui.common.CategoryPickerField
import app.jourjour.ui.common.CategorySheet
import app.jourjour.ui.common.CurrencyPickerField
import app.jourjour.ui.common.DateField
import app.jourjour.ui.common.DurationField
import app.jourjour.ui.common.EditorScaffold
import app.jourjour.ui.common.FieldHeading
import app.jourjour.ui.common.InfoTooltipButton
import app.jourjour.ui.common.MoneyField
import app.jourjour.ui.common.PaymentMethodPickerField
import app.jourjour.ui.common.SecondaryButton
import app.jourjour.ui.common.TimeField
import app.jourjour.ui.common.TitleWithNotesField
import app.jourjour.ui.common.ZoneChangeField
import app.jourjour.ui.common.rememberTimeFormatter
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** As wide as a short date needs and no wider: "18.09.26" is all it will ever hold. */
private val DATE_FIELD_WIDTH = 108.dp

/** As wide as a clock needs and no wider. */
private val TIME_FIELD_WIDTH = 84.dp

/** What a currency takes: three letters and the arrow that says they can be changed. */
private val SIDE_FIELD_WIDTH = 132.dp

/** How tall a field stands, which is what anything beside one is lined up against. */
private val FIELD_HEIGHT = 56.dp

/**
 * Where the button beside a field begins.
 *
 * A field is set down by the gap it keeps from the row above, and its label sits in the
 * border rather than over it, so the box a person actually sees starts lower than the
 * arithmetic says. This is that gap as the eye reads it rather than as the layout gives
 * it: two dp of difference is nothing to measure and everything to notice.
 */
private val CLEAR_TOP = 12.dp

/** The corner a block of text has; the same one a field and an event block are given. */
private val CLASH_CORNER = 12.dp

/** As wide as a padded clock with its mark, which is all this column ever holds. */
private val NEAREST_CLOCK_WIDTH = 72.dp

/** The small mark beside a clock, as the list draws it: read at a glance, never looked at. */
private val MARK_SIZE = 14.dp

/**
 * The form of one event, over the whole screen.
 *
 * Read downwards in the order the questions come: what it is, what it begins at the end of
 * where anything does, when it starts and when it ends with the clock each is read on, how
 * long that makes it, and what it is expected to cost. Delete is in the top bar, away from
 * the button people came to press, as it is on every other form.
 */
@Composable
fun ScheduleEventForm(
    sheet: ScheduleSheet,
    state: ScheduleState,
    viewModel: ScheduleViewModel,
    snackbarHostState: SnackbarHostState,
) {
    // Opening the notes is a request to write them, so the cursor follows. Only when the
    // control was pressed: an event that already carries notes opens with them on show,
    // and taking the cursor there would be taking it away from whatever was being edited.
    val notesFocus = remember { FocusRequester() }
    var notesAsked by remember { mutableStateOf(false) }
    LaunchedEffect(sheet.isNotesVisible, notesAsked) {
        if (sheet.isNotesVisible && notesAsked) {
            runCatching { notesFocus.requestFocus() }
            notesAsked = false
        }
    }

    // The form is a screen over the tab rather than a destination of its own, so Back is
    // wired here: shutting it is what Back means while it is open.
    BackHandler(onBack = viewModel::closeSheet)

    // Permission for the calendar is asked for on the button that needs it, as the
    // entry form asks for location: that way it is clear what it is for, and somebody
    // who never copies an event is never asked at all.
    val context = LocalContext.current
    val calendarDay by viewModel.calendarDay.collectAsStateWithLifecycle()
    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.openCalendar() else viewModel.reportCalendarDenied()
    }
    val askCalendar = {
        with(DeviceCalendar) {
            if (context.canReadCalendar()) {
                viewModel.openCalendar()
            } else {
                calendarPermission.launch(DeviceCalendar.PERMISSION)
            }
        }
    }

    calendarDay?.let { events ->
        CalendarPickerDialog(
            events = events,
            zone = sheet.startZone,
            onPick = viewModel::copyFromCalendar,
            onDismiss = viewModel::closeCalendar,
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        EditorScaffold(
            title = stringResource(
                if (sheet.isNew) R.string.title_event_new else R.string.title_event_edit
            ),
            onBack = viewModel::closeSheet,
            onSave = viewModel::confirmSheet,
            snackbarHostState = snackbarHostState,
            onDelete = viewModel::delete.takeIf { !sheet.isNew },
            actions = {
                // Only on a form nobody has written anything into yet. A copy of another
                // event already says what it is, and an event that exists is not looking
                // for somebody else's name and hours.
                if (sheet.isNew && !sheet.isCopy) {
                    IconButton(onClick = askCalendar) {
                        Icon(
                            painter = painterResource(R.drawable.ic_calendar_copy),
                            contentDescription = stringResource(R.string.action_copy_from_calendar),
                        )
                    }
                }
                // The same button the entry form carries, in the same corner: a second
                // event like this one, to be given a time of its own.
                if (!sheet.isNew) {
                    IconButton(onClick = viewModel::repeat) {
                        Icon(
                            painter = painterResource(R.drawable.ic_copy),
                            contentDescription = stringResource(R.string.action_repeat_event),
                        )
                    }
                }
            },
        ) {
            TitleWithNotesField(
                title = sheet.title,
                notes = sheet.notes,
                isFavorite = false,
                isNotesVisible = sheet.isNotesVisible,
                onTitleChange = viewModel::updateTitle,
                onNotesChange = viewModel::updateNotes,
                onToggleFavorite = {},
                onToggleNotes = {
                    notesAsked = !sheet.isNotesVisible
                    viewModel.toggleNotes()
                },
                notesFocus = notesFocus,
                labelRes = R.string.field_name,
                showFavorite = false,
                errorRes = sheet.titleError,
            )

            state.nearest?.let { Nearest(nearest = it, sheet = sheet, viewModel = viewModel) }

            // Greyed while the event is tied to another: the start is that one's end, and
            // a field offering to disagree with it would be offering to break the link.
            WhenRow(
                labelRes = R.string.field_starts,
                date = sheet.startDate,
                time = sheet.startTime,
                zone = sheet.startZone.takeIf { sheet.declaresStart },
                onDateChange = viewModel::updateStartDate,
                onTimeChange = viewModel::updateStartTime,
                onZoneChange = viewModel::selectStartZone,
                errorRes = sheet.rangeError,
                enabled = !sheet.isLinked,
            )

            WhenRow(
                labelRes = R.string.field_ends,
                date = sheet.endDate,
                time = sheet.endTime,
                zone = sheet.endZone.takeIf { sheet.declaresEnd },
                onDateChange = viewModel::updateEndDate,
                onTimeChange = viewModel::updateEndTime,
                onZoneChange = viewModel::selectEndZone,
                errorRes = sheet.endError,
            )

            // Between the two ends, where it belongs: the third way round the same three
            // figures, and the one most people reach for — an hour and a half, not until
            // half past two.
            LengthRow(sheet = sheet, viewModel = viewModel)

            // Under the hours it is about and not at the foot of the form: a warning
            // below the money is a warning below the fold on a form this long, and what
            // it warns about is what was just typed three fields above it.
            state.clash?.let { Clash(clash = it, asked = state.isShiftAsked, viewModel = viewModel) }

            Cost(sheet = sheet, state = state, viewModel = viewModel)
        }
    }
}

/**
 * What this event begins at the end of.
 *
 * There is no list to pick from, because there is nothing to pick: somebody writing down
 * "collect the bags" at twenty past five means the flight that landed at ten past. So what
 * is offered is the one thing near enough to have been meant — named, with the hours it
 * takes up — and the question is only asked where there is such a thing. See
 * [ScheduleViewModel.nearest].
 */
@Composable
private fun Nearest(nearest: NearestEvent, sheet: ScheduleSheet, viewModel: ScheduleViewModel) {
    val clock = rememberTimeFormatter()
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        SectionHeading(
            titleRes = R.string.schedule_nearest_title,
            hintRes = R.string.hint_nearest,
            descriptionRes = R.string.action_about_nearest,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The hours first, because that is what says whether this is the thing meant:
            // a name three days away and a name ten minutes away read alike. Written the
            // way the list writes them, one over the other against the same two marks,
            // so that what is named here is recognised as what stands up there — but
            // without the zone, which is the list's business and not this line's.
            Column(modifier = Modifier.width(NEAREST_CLOCK_WIDTH)) {
                Marked(R.drawable.ic_time_start, nearest.start.format(clock))
                Marked(R.drawable.ic_time_end, nearest.end.format(clock))
            }
            Text(
                text = nearest.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // The chain the list draws over a join, as a button: tied it is filled and
            // untied it is not, which says which of the two states this is without a word
            // — and a word here would be one of two opposite ones in the same place, read
            // as a label half the time and as an offer the other half.
            val chain: @Composable () -> Unit = {
                Icon(
                    painter = painterResource(R.drawable.ic_chain),
                    contentDescription = stringResource(
                        if (sheet.isLinked) R.string.schedule_unlink else R.string.schedule_link
                    ),
                )
            }
            if (sheet.isLinked) {
                FilledIconButton(onClick = viewModel::unlink, content = chain)
            } else {
                FilledTonalIconButton(onClick = viewModel::linkToNearest, content = chain)
            }
        }
    }
}

/** A small mark and the clock beside it, as the list sets them. */
@Composable
private fun Marked(@DrawableRes icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(MARK_SIZE),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * One end of the event on one line: which day, which hour, and the clock that hour is on.
 *
 * The zone reads "unchanged" for almost every event, which is the truth — it happens
 * wherever the trip has got to — and is what keeps the line from asking a question nobody
 * has an answer to.
 */
@Composable
private fun WhenRow(
    @StringRes labelRes: Int,
    date: LocalDate,
    time: LocalTime,
    zone: ZoneId?,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onZoneChange: (ZoneId?) -> Unit,
    @StringRes errorRes: Int?,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The two that hold a figure of a known size are given that size, and the zone —
        // which holds a place name and cannot know how long one is — takes what is left.
        DateField(
            date = date,
            onDateChange = onDateChange,
            labelRes = labelRes,
            modifier = Modifier.width(DATE_FIELD_WIDTH),
            errorRes = errorRes,
            compact = true,
            enabled = enabled,
        )
        // Labelled for what it is rather than for which end it settles: the date beside
        // it has already said that, and "ends" over a field this narrow wraps onto a
        // second line and drags the field down with it.
        TimeField(
            time = time,
            onTimeChange = onTimeChange,
            labelRes = R.string.field_time_short,
            modifier = Modifier.width(TIME_FIELD_WIDTH),
            enabled = enabled,
        )
        ZoneChangeField(
            zone = zone,
            onSelect = onZoneChange,
            labelRes = R.string.field_zone_short,
            modifier = Modifier.weight(1f),
            enabled = enabled,
        )
    }
}

/**
 * How long it lasts, written as a clock is and standing under the two clocks it is
 * measured between.
 *
 * Under the hours and not under the dates, because that is what it is about: which day a
 * thing happens on has nothing to do with how long it goes on for, and a figure of the
 * same shape in the same column is read as belonging to the two above it.
 */
@Composable
private fun LengthRow(sheet: ScheduleSheet, viewModel: ScheduleViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.width(DATE_FIELD_WIDTH))
        DurationField(
            minutes = sheet.lengthMinutes,
            onMinutesChange = viewModel::updateLength,
            labelRes = R.string.field_lasts,
            modifier = Modifier.width(TIME_FIELD_WIDTH),
        )
    }
}

/**
 * What the event is expected to cost, which is the budget's finer plan for whatever it is
 * filed under — this hotel for these nights, against ten nights at fifty.
 *
 * What it is for is the question that comes first and the only one always asked: a sum
 * with nothing to file it under says nothing the budget can read, so the money arrives
 * with the category and goes with it. Clearing is beside the category for the same
 * reason — it is the one answer the others hang off.
 */
@Composable
private fun Cost(sheet: ScheduleSheet, state: ScheduleState, viewModel: ScheduleViewModel) {
    val category = state.categories.firstOrNull { it.id == sheet.categoryId }
    val currency = state.currency(sheet.currencyCode)

    SectionHeading(
        titleRes = R.string.schedule_cost_title,
        hintRes = R.string.hint_cost,
        descriptionRes = R.string.action_about_cost,
    )

    // The whole width, and the same width whatever has been answered: a field that
    // narrowed itself the moment it was filled in would be answering by moving.
    CategoryPickerField(
        category = category,
        onClick = viewModel::showCategorySheet,
        modifier = Modifier.fillMaxWidth(),
        errorRes = sheet.categoryError,
    )

    if (sheet.categoryId != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoneyField(
                value = sheet.amount,
                onValueChange = viewModel::updateAmount,
                currency = currency,
                labelRes = R.string.field_amount,
                modifier = Modifier.weight(1f),
                errorRes = sheet.amountError,
                showSymbol = false,
                // A cost can be money coming back: a deposit returned is planned the
                // same way the paying of it is, see the budget's own lines.
                allowNegative = true,
            )
            CurrencyPickerField(
                selectedCode = sheet.currencyCode,
                currencies = state.currencies,
                onSelect = viewModel::selectCurrency,
                modifier = Modifier.width(SIDE_FIELD_WIDTH),
                errorRes = sheet.currencyError,
                compact = true,
            )
            // At the end of what it undoes rather than beside the question that started
            // it: round, because it is a button and not a field, and a button the height
            // of a field standing in a row of them only looks like a field that failed.
            //
            // Centred against the field rather than against the row: a row is as tall as
            // its tallest line, an error under a figure makes one taller, and a button
            // that drifted down the moment something was mistyped would be answering the
            // mistake.
            Box(
                modifier = Modifier.padding(top = CLEAR_TOP).height(FIELD_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                FilledTonalIconButton(onClick = viewModel::clearCost) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_clear),
                    )
                }
            }
        }

        // Under the money, and only where there is money: what this will be paid with is
        // a question about the expense, not about the event. Headed rather than labelled,
        // as on the budget line it is the same question as.
        FieldHeading(
            titleRes = R.string.field_payment_method,
            hintRes = R.string.hint_payment_method,
            descriptionRes = R.string.action_about_payment_method,
        )
        PaymentMethodPickerField(
            selectedId = sheet.paymentMethodId,
            paymentMethods = state.paymentMethods,
            onSelect = viewModel::selectPaymentMethod,
            onClear = { viewModel.selectPaymentMethod(null) },
            labelRes = null,
        )
    }

    if (sheet.isCategorySheetVisible) {
        CategorySheet(
            categories = state.categories,
            onSelect = viewModel::selectCategory,
            onDismiss = viewModel::hideCategorySheet,
        )
    }
}

/**
 * What these times run into, and the offer to make room.
 *
 * Said rather than refused: two things booked over the same minutes is a fact about a plan
 * and the person writing it knows which of them is wrong. The offer only appears where
 * moving what comes after would actually clear the way — a clash with something earlier
 * cannot be cleared by pushing, since that would shove the morning into yesterday.
 *
 * Nothing moves until Save, so shutting the form takes the shift with it.
 */
@Composable
private fun Clash(clash: ScheduleClash, asked: Boolean, viewModel: ScheduleViewModel) {
    // In the colours the schedule already gives minutes claimed twice, and in a block of
    // them rather than as a line of red text at the foot of a long form: what it says is
    // the one thing on this screen a person may not have noticed they were doing.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(CLASH_CORNER))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
    ) {
        Text(
            text = stringResource(R.string.schedule_clash, clash.other, clash.minutes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            // A warning naming something with a paragraph for a name is still a warning,
            // and the whole of the name is not what makes it one.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!clash.canShift) return
        if (asked) {
            Text(
                text = stringResource(R.string.schedule_shift_asked, clash.moves),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            SecondaryButton(
                onClick = viewModel::askShift,
                text = stringResource(R.string.action_shift_others),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

/**
 * A line across the form saying what the fields under it are about, with the paragraph
 * that says why behind an "i" beside it.
 *
 * The same shape the budget and the trip form head their sections with. The explanation
 * lives behind the mark rather than under the heading: it is read once and then never
 * again, and a paragraph standing there for good would push the form off the screen.
 */
@Composable
private fun SectionHeading(
    @StringRes titleRes: Int,
    @StringRes hintRes: Int,
    @StringRes descriptionRes: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
        )
        InfoTooltipButton(
            titleRes = titleRes,
            textRes = hintRes,
            descriptionRes = descriptionRes,
        )
    }
}


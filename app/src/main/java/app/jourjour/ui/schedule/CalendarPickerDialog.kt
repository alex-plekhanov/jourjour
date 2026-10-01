package app.jourjour.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.core.calendar.CalendarEvent
import app.jourjour.ui.common.SecondaryButton
import app.jourjour.ui.common.rememberTimeFormatter
import java.time.ZoneId

/**
 * What the device's calendar has on one day, to be copied into the form.
 *
 * A list and nothing else: there is no searching, no range and no second page, because
 * the question being answered is "what was I already doing that day", and a day is as
 * much of an answer as that question has.
 *
 * @param zone the clock the day is being read on, for the events the calendar does not
 *   say a zone for. Everything else is shown on its own, which is the hour it will carry
 *   once it is copied.
 */
@Composable
fun CalendarPickerDialog(
    events: List<CalendarEvent>,
    zone: ZoneId,
    onPick: (CalendarEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val times = rememberTimeFormatter()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_copy_from_calendar)) },
        text = {
            if (events.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_calendar_day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    events.forEach { event ->
                        CalendarRow(
                            event = event,
                            // An event with no name of its own still has an hour and a
                            // calendar, which is enough to know it by.
                            title = event.title.ifBlank { stringResource(R.string.value_not_set) },
                            at = if (event.isAllDay) {
                                stringResource(R.string.calendar_all_day)
                            } else {
                                // Each end on the clock it is written against, which is
                                // also the hour it will carry once it is copied: a flight
                                // that lands in another zone lands at the hour there.
                                val from = event.startsAt.atZone(event.startZone ?: zone)
                                val until = event.endsAt.atZone(event.endZone ?: event.startZone ?: zone)
                                "${times.format(from)} – ${times.format(until)}"
                            },
                            onPick = { onPick(event) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            SecondaryButton(onClick = onDismiss, text = stringResource(R.string.action_cancel))
        },
    )
}

@Composable
private fun CalendarRow(
    event: CalendarEvent,
    title: String,
    at: String,
    onPick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            // Which calendar it stands on, since two of them may hold the same hour and
            // only the name says which is the one being copied.
            text = listOf(at, event.calendar).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

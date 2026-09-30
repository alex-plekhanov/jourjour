package app.jourjour.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.ui.theme.FieldShape
import java.time.Instant
import java.time.ZoneId

/**
 * Picking a time zone. There are several hundred, so this opens a sheet with a search
 * rather than a list to scroll — and what is searched is the place, because a person
 * knows where they are going and not what the zone is called.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZonePickerField(
    zone: ZoneId,
    onSelect: (ZoneId) -> Unit,
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    /**
     * Whether this is the zone the schedule handed down rather than one the event states.
     * Shown all the same — the hour beside it means nothing without it — but as the fact
     * it is rather than as an answer somebody gave.
     */
    inherited: Boolean = false,
    enabled: Boolean = true,
) {
    var sheetVisible by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = dismissingFocus { sheetVisible = true }),
    ) {
        OutlinedTextField(
            value = zoneLabel(zone),
            onValueChange = {},
            label = { FieldLabel(stringResource(labelRes)) },
            readOnly = true,
            // Disabled on purpose so the wrapper gets the tap, see CurrencyPickerField.
            enabled = false,
            supportingText = if (inherited) {
                { Text(stringResource(R.string.hint_zone_inherited)) }
            } else {
                null
            },
            singleLine = true,
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            shape = FieldShape,
            colors = pickerFieldColors(enabled),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }

    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ZonePickerSheet(
                current = zone,
                onSelect = {
                    onSelect(it)
                    sheetVisible = false
                },
            )
        }
    }
}

/**
 * The same picker for a zone an event may or may not declare, which most of them do not.
 *
 * "Unchanged" is the answer almost every time — the event happens wherever the trip has
 * got to — so it is both what the field reads while nothing has been said and the first
 * thing the picker offers, rather than something to be found by clearing a field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneChangeField(
    zone: ZoneId?,
    onSelect: (ZoneId?) -> Unit,
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var sheetVisible by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.clickable(enabled = enabled, onClick = dismissingFocus { sheetVisible = true }),
    ) {
        OutlinedTextField(
            // The city alone, not the city and its region: this field stands in a row
            // beside a date and a clock and has room for a word, and the word that says
            // where is the one anybody reads.
            value = zone?.let(::zoneCity) ?: stringResource(R.string.value_zone_unchanged),
            onValueChange = {},
            label = { FieldLabel(stringResource(labelRes)) },
            readOnly = true,
            // Disabled on purpose so the wrapper gets the tap, see CurrencyPickerField.
            enabled = false,
            singleLine = true,
            // The same arrow every picker in the app carries: a field that opens a list
            // and one that is typed into look alike without it.
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            shape = FieldShape,
            colors = pickerFieldColors(enabled),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }

    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ZonePickerSheet(
                current = zone,
                onInherit = {
                    onSelect(null)
                    sheetVisible = false
                },
                onSelect = {
                    onSelect(it)
                    sheetVisible = false
                },
            )
        }
    }
}

@Composable
private fun ZonePickerSheet(
    current: ZoneId?,
    onSelect: (ZoneId) -> Unit,
    /** Offered where saying nothing is an answer; null where a zone has to be named. */
    onInherit: (() -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }

    // Built once: six hundred zones, each asking the rules what the offset is today.
    val zones = remember {
        ZoneId.getAvailableZoneIds()
            .asSequence()
            .filter { '/' in it }
            .map(ZoneId::of)
            .sortedWith(compareBy({ it.offsetNow() }, { it.id }))
            .toList()
    }
    val matching = remember(zones, query) {
        if (query.isBlank()) zones else zones.filter { zoneLabel(it).contains(query, true) }
    }

    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        Text(
            text = stringResource(R.string.select_zone),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_zones)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = FieldShape,
            colors = formFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
            // At the head of the list and not behind the search, because it is the answer
            // for almost every event and nobody searches for the thing they already have.
            if (onInherit != null && query.isBlank()) {
                item(key = "inherit") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onInherit)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        // In brackets and in the accent colour, the way the wallets
                        // offer a new wallet: it is not one of the places in the list
                        // below but the answer to a different question.
                        Text(
                            text = stringResource(R.string.select_zone_unchanged),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    HorizontalDivider()
                }
            }
            items(matching, key = { it.id }) { zone ->
                ZoneRow(zone = zone, selected = zone == current, onSelect = onSelect)
            }
            if (matching.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.empty_zones),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoneRow(zone: ZoneId, selected: Boolean, onSelect: (ZoneId) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(zone) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = zonePlace(zone),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = zone.offsetLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

/** "Bangkok, Asia · UTC+07:00" — the place first, since that is what is being looked for. */
fun zoneLabel(zone: ZoneId): String = "${zonePlace(zone)} · ${zone.offsetLabel()}"

/**
 * The place out of the zone's own name: "Asia/Ho_Chi_Minh" is read as "Ho Chi Minh, Asia".
 *
 * Not the localised display name, which for half the zones is the name of a standard —
 * "Индокитай", "Восточноевропейское время" — and for the other half a city nobody would
 * think to search for. The identifier is the one thing that always names somewhere.
 */
fun zonePlace(zone: ZoneId): String {
    val parts = zone.id.split('/')
    val city = parts.last().replace('_', ' ')
    val region = parts.first().replace('_', ' ')
    return if (parts.size > 1) "$city, $region" else city
}

/** Just the place out of that: "Ho Chi Minh", for a field with room for one word. */
fun zoneCity(zone: ZoneId): String = zone.id.substringAfterLast('/').replace('_', ' ')

/** "UTC+07:00", as the zone stands today rather than as it stood when the rules changed. */
fun ZoneId.offsetLabel(): String {
    val offset = rules.getOffset(Instant.now())
    return if (offset.totalSeconds == 0) "UTC" else "UTC${offset.id}"
}

private fun ZoneId.offsetNow(): Int = rules.getOffset(Instant.now()).totalSeconds

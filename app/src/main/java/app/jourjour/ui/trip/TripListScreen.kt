package app.jourjour.ui.trip

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.jourjour.R
import app.jourjour.core.dump.Dump
import app.jourjour.core.dump.DumpArchive
import app.jourjour.core.photo.TripCoverStorage
import app.jourjour.data.entity.Trip
import app.jourjour.ui.common.AddFab
import app.jourjour.ui.common.CommonMenuItems
import app.jourjour.ui.common.EmptyList
import app.jourjour.ui.common.ObserveMessages
import app.jourjour.ui.common.OverflowMenu
import app.jourjour.ui.theme.JourJourTheme
import java.time.LocalDate

@Composable
fun TripListScreen(
    onOpenTrip: (Long) -> Unit,
    onEditTrip: (Long) -> Unit,
    onAddTrip: () -> Unit,
    onCurrencies: () -> Unit,
    onPaymentMethods: () -> Unit,
    onCategories: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    viewModel: TripListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    ObserveMessages(viewModel.messages, snackbarHostState)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DumpArchive.MIME_TYPE)
    ) { target -> target?.let(viewModel::exportDump) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { source -> source?.let(viewModel::importDump) }

    TripListContent(
        state = state,
        coverStorage = viewModel.coverStorage,
        snackbarHostState = snackbarHostState,
        onExportDump = { exportLauncher.launch(dumpFileName()) },
        onImportDump = { importLauncher.launch(DUMP_MIME_TYPES) },
        onOpenTrip = onOpenTrip,
        onEditTrip = onEditTrip,
        onAddTrip = onAddTrip,
        onCurrencies = onCurrencies,
        onPaymentMethods = onPaymentMethods,
        onCategories = onCategories,
        onSettings = onSettings,
        onAbout = onAbout,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripListContent(
    state: TripListState,
    coverStorage: TripCoverStorage,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onExportDump: () -> Unit = {},
    onImportDump: () -> Unit = {},
    onOpenTrip: (Long) -> Unit,
    onEditTrip: (Long) -> Unit,
    onAddTrip: () -> Unit,
    onCurrencies: () -> Unit,
    onPaymentMethods: () -> Unit,
    onCategories: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_trips)) },
                actions = {
                    OverflowMenu { dismiss ->
                        // The dumps are for moving data between installs while the app is
                        // being developed, and stay off the menu until the setting says so.
                        if (state.dumpsEnabled) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_export_dump)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_export), contentDescription = null) },
                                onClick = { dismiss(); onExportDump() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_import_dump)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_import), contentDescription = null) },
                                onClick = { dismiss(); onImportDump() },
                            )
                            HorizontalDivider()
                        }
                        CommonMenuItems(
                            dismiss = dismiss,
                            onCurrencies = onCurrencies,
                            onPaymentMethods = onPaymentMethods,
                            onCategories = onCategories,
                            onSettings = onSettings,
                            onAbout = onAbout,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            AddFab(onClick = onAddTrip, contentDescription = stringResource(R.string.action_add))
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.trips.isEmpty() ->
                    EmptyList(R.string.empty_trips, R.string.empty_trips_hint)

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        // Clear of the floating button, which would otherwise cover the last card.
                        bottom = 88.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.trips, key = { it.id }) { trip ->
                        TripItem(
                            trip = trip,
                            coverStorage = coverStorage,
                            onTripClick = { onOpenTrip(trip.id) },
                            onEditClick = { onEditTrip(trip.id) },
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TripListScreenPreview() {
    JourJourTheme {
        TripListContent(
            state = TripListState(
                trips = listOf(
                    Trip(
                        id = 1,
                        name = "Japan",
                        startDate = LocalDate.of(2026, 4, 3),
                        endDate = LocalDate.of(2026, 4, 17),
                    ),
                ),
                isLoading = false,
            ),
            coverStorage = TripCoverStorage(LocalContext.current),
            onOpenTrip = {},
            onEditTrip = {},
            onAddTrip = {},
            onCurrencies = {},
            onPaymentMethods = {},
            onCategories = {},
            onSettings = {},
            onAbout = {},
        )
    }
}

/** What a picker offers to open as a dump: the archive, or a bare document, however it is labelled. */
private val DUMP_MIME_TYPES = arrayOf(DumpArchive.MIME_TYPE, Dump.MIME_TYPE, "application/octet-stream")

/** "jourjour-dump-2026-09-16.zip": the app, the kind of file, the day it was taken. */
private fun dumpFileName(): String = "jourjour-dump-${LocalDate.now()}.zip"

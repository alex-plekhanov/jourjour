package app.jourjour.ui.trip

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.jourjour.R
import app.jourjour.core.export.BudgetCsv
import app.jourjour.core.export.ChecklistCsv
import app.jourjour.core.export.ReceiptsZip
import app.jourjour.core.export.PlanColumn
import app.jourjour.core.export.ScheduleCsv
import app.jourjour.core.export.SchedulePdf
import app.jourjour.core.export.TripColumn
import app.jourjour.core.export.TripCsv
import app.jourjour.core.export.TripXlsx
import app.jourjour.core.export.Xlsx
import androidx.compose.ui.platform.LocalContext
import app.jourjour.core.share.shareFile
import app.jourjour.ui.common.AddFab
import app.jourjour.ui.common.CommonMenuItems
import app.jourjour.ui.common.ObserveMessages
import app.jourjour.ui.common.OverflowMenu
import app.jourjour.ui.common.UnscaledText
import app.jourjour.ui.common.WalletFormScreen
import app.jourjour.ui.common.ColumnPickerDialog
import app.jourjour.ui.entry.EntriesTab
import app.jourjour.ui.entry.EntriesViewModel
import app.jourjour.ui.common.keyboardInsets
import app.jourjour.ui.common.withHalo
import app.jourjour.ui.entry.SearchControl
import app.jourjour.ui.checklist.ChecklistItemForm
import app.jourjour.ui.checklist.ChecklistTab
import app.jourjour.ui.budget.BudgetItemForm
import app.jourjour.ui.budget.BudgetReportScreen
import app.jourjour.ui.budget.BudgetTab
import app.jourjour.ui.budget.BudgetViewModel
import app.jourjour.ui.schedule.ScheduleEventForm
import app.jourjour.ui.schedule.ScheduleTab
import app.jourjour.ui.schedule.ScheduleViewModel
import app.jourjour.ui.checklist.ChecklistViewModel
import app.jourjour.ui.wallet.WalletsTab
import app.jourjour.ui.wallet.WalletsViewModel
import kotlinx.coroutines.launch

/** How far down the screen the cover reaches before it has faded away entirely. */
private val BANNER_HEIGHT = 220.dp

/** What Material sets a floating action button off from the edge of the screen by. */
private val FAB_MARGIN = 16.dp

/** How far the row of tabs stands over whatever is being read under it. */
private val TAB_ELEVATION = 8.dp

/** A cover is stored no wider than this, so the banner gets it whole. */
private const val BANNER_COVER_MAX_SIDE = 1080

/**
 * The five sides of working on a trip, in the order a trip is lived through: the budget,
 * written before anything is booked; the plan, which is what is booked and when; the
 * checklist, worked through on the way out of the door; the wallets, without which nothing
 * can be written down; and the entries, which is what the app is for.
 *
 * The entries are labelled "Expenses", which is not the whole truth — an income and an
 * exchange are entries too — but is what the tab is for as anybody using it sees it. What
 * a trip costs outweighs what comes back by an order of magnitude, and a word everyone
 * reads at a glance beats a more accurate one nobody scans for.
 */
internal enum class TripTab(@get:StringRes val titleRes: Int) {
    BUDGET(R.string.title_budget),
    PLAN(R.string.title_plan),
    CHECKLIST(R.string.title_checklist),
    WALLETS(R.string.title_wallets),
    ENTRIES(R.string.title_entries),
}

/**
 * A trip, as five tabs under one top bar.
 *
 * Which tab opens is the tab last worked on, whichever trip that was — see [openingTab].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(
    onBack: () -> Unit,
    onAddEntry: (Long) -> Unit,
    onOpenEntry: (Long, Long) -> Unit,
    onReconcile: (Long) -> Unit,
    onEditTrip: (Long) -> Unit,
    onStatistics: (Long) -> Unit,
    onCurrencies: () -> Unit,
    onPaymentMethods: () -> Unit,
    onCategories: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    entriesViewModel: EntriesViewModel = hiltViewModel(),
    walletsViewModel: WalletsViewModel = hiltViewModel(),
    checklistViewModel: ChecklistViewModel = hiltViewModel(),
    budgetViewModel: BudgetViewModel = hiltViewModel(),
    scheduleViewModel: ScheduleViewModel = hiltViewModel(),
    tabsViewModel: TripTabsViewModel = hiltViewModel(),
) {
    val entries by entriesViewModel.state.collectAsStateWithLifecycle()
    val wallets by walletsViewModel.state.collectAsStateWithLifecycle()
    val checklist by checklistViewModel.state.collectAsStateWithLifecycle()
    val budget by budgetViewModel.state.collectAsStateWithLifecycle()
    val schedule by scheduleViewModel.state.collectAsStateWithLifecycle()
    val tabs by tabsViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveMessages(entriesViewModel.messages, snackbarHostState)
    ObserveMessages(walletsViewModel.messages, snackbarHostState)
    ObserveMessages(checklistViewModel.messages, snackbarHostState)
    ObserveMessages(budgetViewModel.messages, snackbarHostState)
    ObserveMessages(scheduleViewModel.messages, snackbarHostState)

    // The chooser is opened from here and not from the view model: picking an app is
    // something a person does in front of an activity.
    val activity = LocalContext.current
    val noApp = stringResource(R.string.error_no_share_target)
    val chooser = stringResource(R.string.menu_share_plan)
    LaunchedEffect(Unit) {
        scheduleViewModel.sheets.collect { uri ->
            if (!activity.shareFile(uri, SchedulePdf.MIME_TYPE, chooser)) {
                snackbarHostState.showSnackbar(noApp)
            }
        }
    }

    // The tabs are pages of one pager, so a tab is a swipe away as well as a tap.
    val pagerState = rememberPagerState(pageCount = { TripTab.entries.size })

    // Held here rather than inside the tab because the add button is outside it and has
    // to know which day is on the screen.
    val scheduleListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val current = TripTab.entries[pagerState.currentPage]

    // Which tab to open is held back until the wallets and what was remembered are both
    // in: opening one tab and jumping to another a moment later would read as a fault.
    // Without animation, since this is where the screen started as far as anyone looking
    // at it is concerned.
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(wallets.isLoading, tabs) {
        if (opened || wallets.isLoading || !tabs.isLoaded) return@LaunchedEffect
        opened = true
        pagerState.scrollToPage(openingTab(tabs, wallets.balances.isEmpty()).ordinal)
    }

    // What is left is what a person comes back to, so it is written down as they move
    // rather than when they go: there is no moment the app is told it is closing.
    LaunchedEffect(current, opened) {
        if (opened) tabsViewModel.remember(current.name)
    }

    val tripId = entriesViewModel.tripId
    val coverFile = entries.trip?.photoFile

    // The cover belongs behind the whole top of the screen, app bar included, so it is laid
    // down first and the screen is drawn over it on a see-through background.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        coverFile?.let { photoFile ->
            TripCover(
                photoFile = photoFile,
                coverStorage = entriesViewModel.coverStorage,
                fade = Brush.verticalGradient(fadeColors()),
                maxSide = BANNER_COVER_MAX_SIDE,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BANNER_HEIGHT)
                    .align(Alignment.TopCenter),
            )
        }

        Scaffold(
            modifier = Modifier.windowInsetsPadding(keyboardInsets()),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = entries.trip?.name.orEmpty(),
                            // Over the cover the title is set off by a halo of the background,
                            // which is what the cover fades into.
                            style = LocalTextStyle.current.withHalo(MaterialTheme.colorScheme.background),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { onEditTrip(tripId) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit),
                                contentDescription = stringResource(R.string.menu_edit_trip),
                            )
                        }
                        TripMenu(
                            viewModel = entriesViewModel,
                            // What a trip came to, and the ways its table goes in and
                            // out, are only offered where the entries are: on the other
                            // tabs they answer a question nobody standing there is asking.
                            onEntries = current == TripTab.ENTRIES,
                            onStatistics = { onStatistics(tripId) },
                            // The same rule the other way round: copying a checklist is
                            // offered where the checklist is.
                            onChecklist = current == TripTab.CHECKLIST,
                            onCopyChecklist = checklistViewModel::openCopy,
                            onExportChecklist = checklistViewModel::exportTo,
                            onImportChecklist = checklistViewModel::importFrom,
                            onPlan = current == TripTab.PLAN,
                            isViewingPlan = schedule.isViewing,
                            onSetViewingPlan = scheduleViewModel::setViewing,
                            onSharePlan = scheduleViewModel::sharePlan,
                            onChoosePlanColumns = scheduleViewModel::chooseColumns,
                            onExportPlanXlsx = scheduleViewModel::exportXlsxTo,
                            planColumnSelection = schedule.columnSelection,
                            onTogglePlanColumn = scheduleViewModel::toggleColumn,
                            onConfirmPlanColumns = scheduleViewModel::confirmColumns,
                            onClosePlanColumns = scheduleViewModel::closeColumns,
                            onExportPlan = scheduleViewModel::exportTo,
                            onImportPlan = scheduleViewModel::importFrom,
                            onBudget = current == TripTab.BUDGET,
                            onBudgetReport = budgetViewModel::openReport,
                            canReport = budget.standing != null,
                            onCloseSpending = budgetViewModel::askCloseAll,
                            onConvert = budgetViewModel::openConverter,
                            canCloseSpending = budget.canCloseAll,
                            onExportBudget = budgetViewModel::exportTo,
                            onImportBudget = budgetViewModel::importFrom,
                            hasPhotos = entries.photoCount > 0,
                            tripName = entries.trip?.name.orEmpty(),
                            columnSelection = entries.columnSelection,
                            onCurrencies = onCurrencies,
                            onPaymentMethods = onPaymentMethods,
                            onCategories = onCategories,
                            onSettings = onSettings,
                            onAbout = onAbout,
                        )
                    },
                    colors = if (coverFile == null) {
                        TopAppBarDefaults.topAppBarColors()
                    } else {
                        TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    },
                )
            },
            bottomBar = {
                // Raised over whatever is being read rather than ruled off from it. The
                // bar and the blocks on every tab are the same shade of surface, so a
                // list ran straight into the tabs; a line loud enough to stop it was
                // louder than anything it was separating, and this says the same thing
                // without drawing anything — the tabs are not part of the list, they are
                // over it.
                NavigationBar(modifier = Modifier.shadow(TAB_ELEVATION)) {
                    TripTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = item == current,
                            onClick = { scope.launch { pagerState.animateScrollToPage(item.ordinal) } },
                            icon = { Icon(tabIcon(item), contentDescription = null) },
                            // The caption keeps its size whatever the device's font is
                            // set to: it names what the icon above it already shows, and
                            // grown it wraps onto a second line and pushes the bar up
                            // into the list. See UnscaledText.
                            label = { UnscaledText { Text(stringResource(item.titleRes)) } },
                        )
                    }
                }
            },
            floatingActionButton = {
                when (current) {
                    TripTab.BUDGET -> AddFab(
                        onClick = { budgetViewModel.openSheet() },
                        contentDescription = stringResource(R.string.action_add),
                    )
                    // No button at all: the schedule is added to by tapping the empty time
                    // an event goes into, and that is as true of the first event as of
                    // any other. An empty plan is not a blank screen — it is every day of
                    // the trip, each of them tappable and saying so — so a button beside
                    // them would only be a second way in that answers a different
                    // question from the one being asked.
                    TripTab.PLAN -> Unit
                    TripTab.CHECKLIST -> AddFab(
                        onClick = { checklistViewModel.openSheet() },
                        contentDescription = stringResource(R.string.action_add),
                    )
                    TripTab.WALLETS -> AddFab(
                        onClick = { walletsViewModel.openWalletSheet() },
                        contentDescription = stringResource(R.string.action_add),
                    )
                    // The search grows into whatever room is left beside the add button,
                    // which is why the two share a row that spans the screen.
                    //
                    // The slot sets a button off from the end of the screen by a margin of
                    // its own, and a row that filled the width would leave it no room to
                    // do so. The row therefore stops short by that margin twice over and
                    // lets the slot give one of them back, which leaves the search as far
                    // from its edge as the add button is from the other.
                    TripTab.ENTRIES -> BoxWithConstraints {
                        Row(
                            modifier = Modifier.width(maxWidth - FAB_MARGIN * 2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SearchControl(
                                query = entries.search,
                                onOpen = entriesViewModel::openSearch,
                                onQueryChange = entriesViewModel::updateSearch,
                                onClose = entriesViewModel::closeSearch,
                                modifier = Modifier.weight(1f),
                            )
                            AddFab(
                                onClick = { onAddEntry(tripId) },
                                contentDescription = stringResource(R.string.action_add),
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) { page ->
                val content = Modifier.fillMaxSize()
                when (TripTab.entries[page]) {
                    TripTab.BUDGET -> BudgetTab(
                        state = budget,
                        viewModel = budgetViewModel,
                        onEditTrip = { onEditTrip(tripId) },
                        onSettings = onSettings,
                        modifier = content,
                    )
                    TripTab.PLAN -> ScheduleTab(
                        state = schedule,
                        viewModel = scheduleViewModel,
                        listState = scheduleListState,
                        modifier = content,
                    )
                    TripTab.CHECKLIST -> ChecklistTab(
                        state = checklist,
                        viewModel = checklistViewModel,
                        modifier = content,
                    )
                    TripTab.WALLETS -> WalletsTab(
                        state = wallets,
                        viewModel = walletsViewModel,
                        onReconcile = onReconcile,
                        modifier = content,
                    )
                    TripTab.ENTRIES -> EntriesTab(
                        state = entries,
                        onOpenEntry = { entryId -> onOpenEntry(tripId, entryId) },
                        modifier = content,
                    )
                }
            }
        }

        // Every form of a record on these tabs takes the whole screen, top bar and all, so
        // they are drawn here rather than inside the tab each belongs to: a tab lives
        // under those bars and cannot put anything over them. One of them at most is ever
        // open, since a tab is what opens it.
        if (budget.isReportOpen) {
            BudgetReportScreen(
                standing = budget.standing,
                onEditTrip = { onEditTrip(tripId) },
                onSettings = onSettings,
                onClose = budgetViewModel::closeReport,
            )
        }

        budget.sheet?.let { sheet ->
            BudgetItemForm(
                sheet = sheet,
                state = budget,
                viewModel = budgetViewModel,
                snackbarHostState = snackbarHostState,
            )
        }

        schedule.sheet?.let { sheet ->
            ScheduleEventForm(
                sheet = sheet,
                state = schedule,
                viewModel = scheduleViewModel,
                snackbarHostState = snackbarHostState,
            )
        }

        checklist.sheet?.let { sheet ->
            ChecklistItemForm(
                sheet = sheet,
                sections = checklist.sectionNames,
                snackbarHostState = snackbarHostState,
                onSelectSection = checklistViewModel::sheetSelectSection,
                onNewSection = checklistViewModel::sheetNewSection,
                onNewSectionChange = checklistViewModel::sheetUpdateNewSection,
                onTitleChange = checklistViewModel::sheetUpdateTitle,
                onQuantityChange = checklistViewModel::sheetUpdateQuantity,
                onDoneChange = checklistViewModel::sheetUpdateDone,
                onSkippedChange = checklistViewModel::sheetUpdateSkipped,
                onConfirm = checklistViewModel::confirmSheet,
                onDelete = checklistViewModel::deleteOpen,
                onDismiss = checklistViewModel::closeSheet,
            )
        }

        wallets.walletSheet?.let { sheet ->
            WalletFormScreen(
                titleRes = if (sheet.isNew) {
                    R.string.title_wallet_new
                } else {
                    R.string.title_wallet_edit
                },
                form = sheet.form,
                paymentMethods = wallets.paymentMethods,
                currencies = wallets.currencies,
                snackbarHostState = snackbarHostState,
                onSelectPaymentMethod = walletsViewModel::walletSheetSelectPaymentMethod,
                onSelectCurrency = walletsViewModel::walletSheetSelectCurrency,
                onNameChange = walletsViewModel::walletSheetUpdateName,
                onBalanceChange = walletsViewModel::walletSheetUpdateBalance,
                onSave = walletsViewModel::confirmWallet,
                onClose = walletsViewModel::closeWalletSheet,
                onDelete = walletsViewModel::deleteWallet.takeIf { !sheet.isNew },
                // A wallet that exists keeps the currency it was made with: entries and
                // recounts are amounts in it.
                currencyLocked = !sheet.isNew,
            )
        }
    }
}

/**
 * Which tab a trip opens on: the one last worked on, whichever trip that was.
 *
 * Carrying on where a person was is what they expect and the one thing the app knows for
 * certain; anything cleverer would be guessing at what they came for. Until there is a tab
 * to carry on from — a first run — a trip opens on its entries, which is what the app is
 * for, or on the wallets while there is none to write an entry against.
 *
 * A remembered name no tab answers to any more — a tab renamed or dropped between
 * releases — is read as nothing remembered rather than as an error.
 */
internal fun openingTab(tabs: TripTabsState, noWallets: Boolean): TripTab {
    TripTab.entries.firstOrNull { it.name == tabs.remembered }?.let { return it }
    return if (noWallets) TripTab.WALLETS else TripTab.ENTRIES
}

@Composable
private fun tabIcon(tab: TripTab) = when (tab) {
    TripTab.BUDGET -> painterResource(R.drawable.ic_budget)
    TripTab.PLAN -> painterResource(R.drawable.ic_schedule)
    TripTab.CHECKLIST -> painterResource(R.drawable.ic_checklist)
    TripTab.WALLETS -> painterResource(R.drawable.ic_wallet)
    TripTab.ENTRIES -> rememberVectorPainter(Icons.Default.List)
}

/**
 * What a trip adds to the menu every screen shares: taking its table out, and reading one
 * back in. Everywhere there is to go is a tab or a button instead.
 */
@Composable
private fun TripMenu(
    viewModel: EntriesViewModel,
    onEntries: Boolean,
    onStatistics: () -> Unit,
    onChecklist: Boolean,
    onCopyChecklist: () -> Unit,
    onExportChecklist: (Uri) -> Unit,
    onImportChecklist: (Uri) -> Unit,
    onPlan: Boolean,
    isViewingPlan: Boolean,
    onSetViewingPlan: (Boolean) -> Unit,
    onSharePlan: () -> Unit,
    onChoosePlanColumns: () -> Unit,
    onExportPlanXlsx: (Uri) -> Unit,
    planColumnSelection: Set<PlanColumn>?,
    onTogglePlanColumn: (PlanColumn) -> Unit,
    onConfirmPlanColumns: () -> Unit,
    onClosePlanColumns: () -> Unit,
    onExportPlan: (Uri) -> Unit,
    onImportPlan: (Uri) -> Unit,
    onBudget: Boolean,
    onBudgetReport: () -> Unit,
    canReport: Boolean,
    onCloseSpending: () -> Unit,
    onConvert: () -> Unit,
    canCloseSpending: Boolean,
    onExportBudget: (Uri) -> Unit,
    onImportBudget: (Uri) -> Unit,
    hasPhotos: Boolean,
    tripName: String,
    columnSelection: Set<TripColumn>?,
    onCurrencies: () -> Unit,
    onPaymentMethods: () -> Unit,
    onCategories: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    // The person picks where the table goes, which asks for no permission of ours and lets
    // it land anywhere a document can — downloads, a cloud folder, wherever.
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(TripCsv.MIME_TYPE)
    ) { target -> target?.let(viewModel::exportTo) }
    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(TripXlsx.MIME_TYPE)
    ) { target -> target?.let(viewModel::exportXlsxTo) }
    val receiptsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ReceiptsZip.MIME_TYPE)
    ) { target -> target?.let(viewModel::exportReceiptsTo) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { source -> source?.let(viewModel::importFrom) }
    val checklistOutLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ChecklistCsv.MIME_TYPE)
    ) { target -> target?.let(onExportChecklist) }
    val checklistInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { source -> source?.let(onImportChecklist) }
    val budgetOutLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BudgetCsv.MIME_TYPE)
    ) { target -> target?.let(onExportBudget) }
    val planOutLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ScheduleCsv.MIME_TYPE)
    ) { target -> target?.let(onExportPlan) }
    val planXlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(Xlsx.MIME_TYPE)
    ) { target -> target?.let(onExportPlanXlsx) }
    val planInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { source -> source?.let(onImportPlan) }
    val budgetInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { source -> source?.let(onImportBudget) }

    OverflowMenu { dismiss ->
        if (onPlan) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (isViewingPlan) R.string.action_edit_plan else R.string.action_view_plan
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(
                            if (isViewingPlan) R.drawable.ic_edit else R.drawable.ic_eye
                        ),
                        contentDescription = null,
                    )
                },
                onClick = { dismiss(); onSetViewingPlan(!isViewingPlan) },
            )
            HorizontalDivider()
            // Above the table exports: a sheet of the plan is for somebody else to read,
            // and a table is for a spreadsheet to. The first is what people reach for.
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_share_plan)) },
                leadingIcon = {
                    Icon(Icons.Default.Share, contentDescription = null)
                },
                onClick = { dismiss(); onSharePlan() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_export_plan)) },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_export), contentDescription = null)
                },
                onClick = {
                    dismiss()
                    planOutLauncher.launch(exportFileName(tripName, PLAN_SUFFIX))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_export_plan_xlsx)) },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_export), contentDescription = null)
                },
                // The columns first, the file afterwards: a sheet read by a person is
                // asked what it should carry, and there is no point naming a file for a
                // table nobody has agreed to yet.
                onClick = { dismiss(); onChoosePlanColumns() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_import_plan)) },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_import), contentDescription = null)
                },
                onClick = { dismiss(); planInLauncher.launch(CSV_MIME_TYPES) },
            )
            HorizontalDivider()
        }
        if (onBudget) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_close_all_spending)) },
                // A lock rather than a tick: what this does is shut a thing for good, and
                // a tick would say "done" in the same breath as the ticks that mark a
                // category closed one at a time.
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                // Out of reach once every category is closed: there is nothing the tap
                // would do, and a prompt that changes nothing is worse than a grey line.
                enabled = canCloseSpending,
                onClick = { dismiss(); onCloseSpending() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.title_converter)) },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_convert), contentDescription = null)
                },
                onClick = { dismiss(); onConvert() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.title_budget_report)) },
                // The chart the entries put their statistics behind: this is the same
                // kind of thing to open — what the tab under it comes to, read rather
                // than worked on.
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_chart), contentDescription = null)
                },
                // Nothing to read where nothing has been planned, booked or spent.
                enabled = canReport,
                onClick = { dismiss(); onBudgetReport() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_export_budget)) },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_export), contentDescription = null)
                },
                onClick = {
                    dismiss()
                    budgetOutLauncher.launch(exportFileName(tripName, BUDGET_SUFFIX))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_import_budget)) },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_import), contentDescription = null)
                },
                onClick = { dismiss(); budgetInLauncher.launch(CSV_MIME_TYPES) },
            )
            HorizontalDivider()
        }
        if (onChecklist) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_copy_checklist)) },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_copy), contentDescription = null)
                },
                onClick = { dismiss(); onCopyChecklist() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_export_checklist)) },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_export), contentDescription = null)
                },
                onClick = {
                    dismiss()
                    checklistOutLauncher.launch(exportFileName(tripName, CHECKLIST_SUFFIX))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_import_checklist)) },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_import), contentDescription = null)
                },
                onClick = { dismiss(); checklistInLauncher.launch(CSV_MIME_TYPES) },
            )
            HorizontalDivider()
        }
        if (onEntries) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.title_statistics)) },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_chart), contentDescription = null)
                },
                onClick = { dismiss(); onStatistics() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_export_csv)) },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_export), contentDescription = null)
                },
                onClick = { dismiss(); csvLauncher.launch(exportFileName(tripName, CSV_SUFFIX)) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_export_xlsx)) },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_export), contentDescription = null)
                },
                // Which columns first, then where the file goes.
                onClick = { dismiss(); viewModel.chooseColumns() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_export_receipts)) },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_archive), contentDescription = null)
                },
                // Kept in place but out of reach when there is nothing to gather: an empty
                // archive is worse than no archive.
                enabled = hasPhotos,
                onClick = {
                    dismiss()
                    receiptsLauncher.launch(exportFileName(tripName, RECEIPTS_SUFFIX))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_import_csv)) },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_import), contentDescription = null)
                },
                onClick = { dismiss(); importLauncher.launch(CSV_MIME_TYPES) },
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

    columnSelection?.let { selected ->
        ColumnPickerDialog(
            columns = TripColumn.entries,
            selected = selected,
            titleOf = viewModel::columnTitle,
            onToggle = viewModel::toggleColumn,
            onConfirm = {
                viewModel.confirmColumns()
                xlsxLauncher.launch(exportFileName(tripName, XLSX_SUFFIX))
            },
            onDismiss = viewModel::closeColumns,
        )
    }

    planColumnSelection?.let { selected ->
        // Read once, here: the dialog asks for a title with a plain call, and a resource
        // is fetched where resources can be fetched.
        val titles = PlanColumn.entries.associateWith { stringResource(it.pickRes) }
        ColumnPickerDialog(
            columns = PlanColumn.entries,
            selected = selected,
            titleOf = { column -> titles.getValue(column) },
            onToggle = onTogglePlanColumn,
            onConfirm = {
                onConfirmPlanColumns()
                planXlsxLauncher.launch(exportFileName(tripName, PLAN_XLSX_SUFFIX))
            },
            onDismiss = onClosePlanColumns,
        )
    }
}

/**
 * What the picker will offer for an import. More than one, because a provider names a CSV
 * from its extension and not every one of them settles on the same name for it.
 */
private val CSV_MIME_TYPES = arrayOf(
    TripCsv.MIME_TYPE,
    "text/comma-separated-values",
    "application/csv",
    "text/plain",
)

private const val CSV_SUFFIX = ".csv"
private const val CHECKLIST_SUFFIX = "_checklist.csv"
private const val BUDGET_SUFFIX = "_budget.csv"
private const val PLAN_SUFFIX = "_plan.csv"
private const val PLAN_XLSX_SUFFIX = "_plan.xlsx"
private const val XLSX_SUFFIX = ".xlsx"
private const val RECEIPTS_SUFFIX = "_receipts.zip"

/**
 * A name the file picker can offer. Whatever a file system might object to becomes an
 * underscore, and a trip whose name leaves nothing behind falls back on a plain one.
 */
private fun exportFileName(tripName: String, suffix: String): String {
    val cleaned = tripName
        .map { if (it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_') it else '_' }
        .joinToString("")
        .trim()
    return cleaned.ifEmpty { "trip" } + suffix
}

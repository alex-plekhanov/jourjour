package app.jourjour

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.navigation.NavDestination.Companion.hasRoute
import dagger.hilt.android.AndroidEntryPoint
import app.jourjour.ui.StartupViewModel
import app.jourjour.ui.entry.EntryEditScreen
import app.jourjour.ui.navigation.Route
import app.jourjour.ui.reconcile.ReconcileScreen
import app.jourjour.ui.statistics.StatisticsScreen
import app.jourjour.ui.settings.AboutScreen
import app.jourjour.ui.settings.SettingsScreen
import app.jourjour.ui.settings.category.CategoryEditScreen
import app.jourjour.ui.settings.category.CategoryListScreen
import app.jourjour.ui.settings.currency.CurrencyEditScreen
import app.jourjour.ui.settings.currency.CurrencyListScreen
import app.jourjour.ui.settings.paymentmethod.PaymentMethodEditScreen
import app.jourjour.ui.settings.paymentmethod.PaymentMethodListScreen
import app.jourjour.ui.theme.JourJourTheme
import app.jourjour.ui.trip.TripEditScreen
import app.jourjour.ui.trip.TripScreen
import app.jourjour.ui.trip.TripListScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val startupViewModel: StartupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JourJourTheme {
                val startup by startupViewModel.startTripId.collectAsStateWithLifecycle()

                // Only the background is drawn while the last position is read from disk:
                // showing the trip list and swapping it out a moment later would be worse.
                Surface(modifier = Modifier.fillMaxSize()) {
                    startup?.let { result ->
                        JourJourNavHost(
                            navController = rememberNavController(),
                            startTripId = result.tripId,
                            onLocationChanged = startupViewModel::recordLocation,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JourJourNavHost(
    navController: NavHostController,
    startTripId: Long?,
    onLocationChanged: (Long?) -> Unit,
) {
    val back: () -> Unit = { navController.popBackStack() }

    // The dictionaries, the settings and the about page hang off every screen's menu, so
    // the same handful of navigations is handed to each screen that shows one.
    val onCurrencies: () -> Unit = { navController.navigate(Route.CurrencyList) }
    val onPaymentMethods: () -> Unit = { navController.navigate(Route.PaymentMethodList) }
    val onCategories: () -> Unit = { navController.navigate(Route.CategoryList) }
    val onSettings: () -> Unit = { navController.navigate(Route.Settings) }
    val onAbout: () -> Unit = { navController.navigate(Route.About) }

    // The graph always starts at the trip list and only then moves on to the expenses
    // that were last open. That way back from the expenses returns to the list instead of
    // closing the app.
    //
    // Once, not once per Activity: the back stack outlives a rotation through saved state,
    // and pushing the trip onto it again would leave a copy of that screen under every
    // one opened since — three presses of Back to reach the list after two turns.
    var openedLastTrip by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startTripId) {
        if (openedLastTrip) return@LaunchedEffect
        openedLastTrip = true
        if (startTripId != null) navController.navigate(Route.Trip(startTripId))
    }

    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            val destination = entry.destination
            val tripId = when {
                destination.hasRoute<Route.Trip>() -> entry.toRoute<Route.Trip>().tripId
                // The entry form is part of working on a trip's expenses, not some
                // other screen: adding an entry before closing must not reset the return.
                destination.hasRoute<Route.EntryEdit>() -> entry.toRoute<Route.EntryEdit>().tripId
                else -> null
            }
            onLocationChanged(tripId)
        }
    }

    NavHost(navController = navController, startDestination = Route.TripList) {
        composable<Route.TripList> {
            TripListScreen(
                onOpenTrip = { tripId -> navController.navigate(Route.Trip(tripId)) },
                onEditTrip = { tripId -> navController.navigate(Route.TripEdit(tripId)) },
                onAddTrip = { navController.navigate(Route.TripEdit()) },
                onCurrencies = onCurrencies,
                onPaymentMethods = onPaymentMethods,
                onCategories = onCategories,
                onSettings = onSettings,
                onAbout = onAbout,
            )
        }

        composable<Route.TripEdit> {
            TripEditScreen(
                onClose = back,
                // A trip is made in order to be used, so the form hands over to it
                // rather than back to the list. The form itself leaves the stack, so
                // back from the trip still returns to the list.
                onCreated = { tripId ->
                    navController.navigate(Route.Trip(tripId)) { popUpTo(Route.TripList) }
                },
                // A deleted trip has no screen to return to: back to the list, past the
                // trip screen the form was opened from.
                onDeleted = { navController.popBackStack(Route.TripList, inclusive = false) },
            )
        }

        composable<Route.Trip> {
            TripScreen(
                onBack = back,
                onAddEntry = { tripId -> navController.navigate(Route.EntryEdit(tripId)) },
                onReconcile = { walletId -> navController.navigate(Route.Reconcile(walletId)) },
                onOpenEntry = { tripId, entryId ->
                    navController.navigate(Route.EntryEdit(tripId, entryId))
                },
                onEditTrip = { tripId -> navController.navigate(Route.TripEdit(tripId)) },
                onStatistics = { tripId -> navController.navigate(Route.Statistics(tripId)) },
                onCurrencies = onCurrencies,
                onPaymentMethods = onPaymentMethods,
                onCategories = onCategories,
                onSettings = onSettings,
                onAbout = onAbout,
            )
        }

        composable<Route.Statistics> { entry ->
            StatisticsScreen(
                onBack = back,
                onEditTrip = {
                    navController.navigate(Route.TripEdit(entry.toRoute<Route.Statistics>().tripId))
                },
                onSettings = onSettings,
            )
        }

        composable<Route.Reconcile> {
            ReconcileScreen(onBack = back)
        }

        composable<Route.EntryEdit> {
            EntryEditScreen(onClose = back)
        }

        composable<Route.Settings> {
            SettingsScreen(onBack = back)
        }

        composable<Route.About> {
            AboutScreen(onBack = back)
        }

        composable<Route.CurrencyList> {
            CurrencyListScreen(
                onBack = back,
                onOpenCurrency = { code -> navController.navigate(Route.CurrencyEdit(code)) },
            )
        }
        composable<Route.CurrencyEdit> {
            CurrencyEditScreen(onClose = back)
        }

        composable<Route.PaymentMethodList> {
            PaymentMethodListScreen(
                onBack = back,
                onOpenPaymentMethod = { id -> navController.navigate(Route.PaymentMethodEdit(id)) },
            )
        }
        composable<Route.PaymentMethodEdit> {
            PaymentMethodEditScreen(onClose = back)
        }

        composable<Route.CategoryList> {
            CategoryListScreen(
                onBack = back,
                onOpenCategory = { id -> navController.navigate(Route.CategoryEdit(id)) },
            )
        }
        composable<Route.CategoryEdit> {
            CategoryEditScreen(onClose = back)
        }
    }
}

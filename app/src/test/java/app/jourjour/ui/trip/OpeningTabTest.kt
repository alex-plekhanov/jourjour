package app.jourjour.ui.trip

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where a trip opens: on the tab last worked on, whichever trip that was, and — until
 * there is one — on whichever of the entries and the wallets there is something to do on.
 */
class OpeningTabTest {

    private fun tabs(remembered: String? = null) =
        TripTabsState(isLoaded = true, remembered = remembered)

    @Test
    fun `a trip opens on the tab last worked on`() {
        assertEquals(TripTab.PLAN, openingTab(tabs(remembered = "PLAN"), noWallets = true))
    }

    @Test
    fun `the tab is the same whichever trip is opened`() {
        assertEquals(
            TripTab.CHECKLIST,
            openingTab(tabs(remembered = "CHECKLIST"), noWallets = false),
        )
    }

    @Test
    fun `with nothing remembered a trip opens on its entries`() {
        assertEquals(TripTab.ENTRIES, openingTab(tabs(), noWallets = false))
    }

    @Test
    fun `with nothing remembered and nothing to pay from, the wallets open`() {
        assertEquals(TripTab.WALLETS, openingTab(tabs(), noWallets = true))
    }

    @Test
    fun `a name no tab answers to is read as nothing remembered`() {
        assertEquals(TripTab.ENTRIES, openingTab(tabs(remembered = "RECEIPTS"), noWallets = false))
    }
}

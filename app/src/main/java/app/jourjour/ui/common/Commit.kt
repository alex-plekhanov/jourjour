package app.jourjour.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The write a form ends in, made to run one at a time.
 *
 * A save takes a few milliseconds, and a second tap inside that gap would run the whole
 * thing again: a second row for a new entry, and a second Closed event, whose pop takes
 * the screen underneath along with the form. Disabling the button for those milliseconds
 * would only flicker, so the second tap is dropped here instead.
 */
class Commit {
    private var job: Job? = null

    /** Runs [block] unless the previous one is still on its way. */
    fun launch(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch(block = block)
    }
}

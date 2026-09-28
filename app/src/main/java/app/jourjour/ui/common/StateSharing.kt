package app.jourjour.ui.common

/**
 * How long a screen's state goes on being computed after its last reader has gone.
 *
 * A rotation takes the readers away and brings them back within the moment, and that
 * moment should not restart every query the screen is made of. Five seconds is long
 * enough for that and short enough that a screen left behind stops costing anything.
 */
const val STOP_TIMEOUT_MS = 5_000L

package app.jourjour.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.Flow

/**
 * One-off things a form has to say. Events rather than a state field: closing the screen
 * and showing an error must happen once, not again on every recomposition and once more
 * after a rotation.
 */
sealed interface EditorEvent {
    /** Saved, or the record gone — the form has done its job and can close. */
    data object Closed : EditorEvent

    /**
     * Deleted, where that is more than closing: a form opened from the screen of the very
     * record it just deleted has nowhere to go back to, and says so.
     */
    data object Deleted : EditorEvent

    /**
     * Saved for the very first time. Apart from closing the form it says what was made,
     * so that a caller who wants to go straight there has the id to go with.
     */
    data class Created(val id: Long) : EditorEvent

    data class Error(val message: String) : EditorEvent {
        /** What a write that failed has to say: the message for the action, with what went wrong. */
        constructor(context: Context, @StringRes messageRes: Int, e: Exception) :
            this(context.getString(messageRes, e.localizedMessage.orEmpty()))
    }
}

/**
 * The same for a screen that has nothing to close and only ever reports how an action went.
 */
@Composable
fun ObserveMessages(messages: Flow<String>, snackbarHostState: SnackbarHostState) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(messages, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            messages.collect { snackbarHostState.showSnackbar(it) }
        }
    }
}

@Composable
fun ObserveEditorEvents(
    events: Flow<EditorEvent>,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    /** Where a form that has just made something goes; closing it is the usual answer. */
    onCreated: (Long) -> Unit = { onClose() },
    /** Where a form goes once its record is gone; closing it is the usual answer. */
    onDeleted: () -> Unit = onClose,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(events, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            events.collect { event ->
                when (event) {
                    EditorEvent.Closed -> onClose()
                    EditorEvent.Deleted -> onDeleted()
                    is EditorEvent.Created -> onCreated(event.id)
                    is EditorEvent.Error -> snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }
}

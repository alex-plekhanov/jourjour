package app.jourjour.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the form of a dictionary row has to offer the frame around it. */
interface DictionaryForm<S : DictionaryForm<S>> {
    val hasErrors: Boolean

    val isDeleteDialogVisible: Boolean

    fun withDeleteDialog(visible: Boolean): S
}

/**
 * The frame of a dictionary form: a currency, a payment method, a category.
 *
 * All three open on a row or on nothing, validate as they are typed into, save when asked
 * and delete only what nothing refers to. What differs is the row and its fields, so that
 * is what a subclass supplies: how to read the row, what form it opens as, what makes the
 * form wrong, what row the form comes to, and how it is written and removed.
 *
 * @param T the row.
 * @param S the form.
 */
abstract class DictionaryEditViewModel<T : Any, S : DictionaryForm<S>>(
    protected val context: Context,
    initial: S,
) : ViewModel() {

    protected val form = MutableStateFlow(initial)

    private val commit = Commit()
    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    /** The row the form opened on, or null while a new one is being made. */
    protected var original: T? = null
        private set

    /**
     * The name the form opened with. Editing it makes the row a person's own: from then
     * on the typed text is shown rather than the string resource it used to follow.
     */
    protected var initialName: String = ""

    /** The message when saving fails, with room for what went wrong. */
    @get:StringRes protected abstract val saveErrorRes: Int

    /** The message when deleting fails, with room for what went wrong. */
    @get:StringRes protected abstract val deleteErrorRes: Int

    /** The refusal to delete a row something still refers to. */
    @get:StringRes protected abstract val inUseRes: Int

    /** The row the form was opened on, or null when it is gone. */
    protected abstract suspend fun read(): T?

    /** The form as it opens on [row]; the place to set [initialName]. */
    protected abstract fun formOf(row: T): S

    protected abstract fun validate(state: S): S

    /** The row the form comes to, [previous] being what it opened on. */
    protected abstract fun build(state: S, previous: T?): T

    /** Writes [row], inserting or updating as [previous] says. */
    protected abstract suspend fun write(row: T, previous: T?)

    /** Whether anything refers to [row], which holds a deletion back. */
    protected abstract suspend fun isUsed(row: T): Boolean

    protected abstract suspend fun remove(row: T)

    /** Opens the form on the row, or closes it when the row has gone. */
    protected open fun load() {
        viewModelScope.launch {
            val row = read()
            if (row == null) {
                _events.send(EditorEvent.Closed)
                return@launch
            }
            original = row
            form.value = formOf(row)
        }
    }

    protected fun update(transform: (S) -> S) {
        form.update { validate(transform(it)) }
    }

    /** The resource key a row keeps only while its name is still the one it opened with. */
    protected fun keptNameKey(previousKey: String?, name: String): String? =
        previousKey?.takeIf { name.trim() == initialName }

    fun save() {
        val state = validate(form.value).also { form.value = it }
        if (state.hasErrors) return

        val previous = original
        val row = build(state, previous)
        commit.launch(viewModelScope) {
            try {
                write(row, previous)
                _events.send(EditorEvent.Closed)
            } catch (e: Exception) {
                _events.send(EditorEvent.Error(context, saveErrorRes, e))
            }
        }
    }

    fun showDeleteDialog() = form.update { it.withDeleteDialog(true) }

    fun hideDeleteDialog() = form.update { it.withDeleteDialog(false) }

    /**
     * Foreign keys would refuse a row that is in use anyway, but the message they produce
     * tells a person nothing, so the check comes first and has words of its own.
     */
    fun delete() {
        val row = original ?: return
        hideDeleteDialog()
        commit.launch(viewModelScope) {
            try {
                if (isUsed(row)) {
                    _events.send(EditorEvent.Error(context.getString(inUseRes)))
                    return@launch
                }
                remove(row)
                _events.send(EditorEvent.Closed)
            } catch (e: Exception) {
                _events.send(EditorEvent.Error(context, deleteErrorRes, e))
            }
        }
    }
}

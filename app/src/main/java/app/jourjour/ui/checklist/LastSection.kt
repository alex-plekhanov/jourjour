package app.jourjour.ui.checklist

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The section last written into, offered to the next item added.
 *
 * A checklist is filled in in runs — four things for the bag, then three papers — and
 * picking the same section out of a list four times running is the one bit of friction in
 * a form that is otherwise two fields. So the form opens on the section the last item was
 * saved in.
 *
 * It lives for as long as the app does and nowhere else: not in the database, which has no
 * business holding what somebody was in the middle of, and not in the settings either,
 * since coming back to the app tomorrow is coming back to a fresh question. It is the name
 * and nothing more — no trip, no id — so that it carries across trips, which is the right
 * answer where the name means the same thing in both and no answer at all where it does
 * not: a name no section of this list carries is simply not offered, and the form opens on
 * nothing, exactly as it used to. A section renamed or deleted falls out the same way.
 */
@Singleton
class LastSection @Inject constructor() {

    /** The name last saved, or null until something has been. */
    var name: String? = null
        private set

    fun remember(section: String) {
        name = section
    }
}

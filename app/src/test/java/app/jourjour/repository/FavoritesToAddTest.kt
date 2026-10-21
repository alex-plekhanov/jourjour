package app.jourjour.repository

import app.jourjour.core.dump.FavoriteDump
import app.jourjour.data.entity.CategoryFavorite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A category already on the phone keeps everything it has, and gains the favourites the dump
 * brings that it lacks. The edges are where it can go wrong: a preloaded title stored in
 * another language, a name that would overwrite a row, and the order the two lists meet in.
 */
class FavoritesToAddTest {

    @Test
    fun a_title_from_the_other_phone_is_added() {
        val own = listOf(CategoryFavorite(CATEGORY, "Lunch", sortOrder = 0))

        val added = favoritesToAdd(CATEGORY, own, listOf(FavoriteDump("Pizza")))

        assertEquals(listOf("Pizza"), added.map { it.name })
    }

    @Test
    fun a_title_already_here_is_not_written_again() {
        val own = listOf(CategoryFavorite(CATEGORY, "Pizza", sortOrder = 3))

        val added = favoritesToAdd(CATEGORY, own, listOf(FavoriteDump("Pizza", sortOrder = 0)))

        // Written again it would replace the row, and with it the place it was dragged to.
        assertTrue(added.isEmpty())
    }

    @Test
    fun a_preloaded_title_is_known_by_its_key_in_any_language() {
        val own = listOf(CategoryFavorite(CATEGORY, "Завтрак", nameKey = "favorite_breakfast"))

        val added = favoritesToAdd(
            CATEGORY,
            own,
            listOf(FavoriteDump("Breakfast", nameKey = "favorite_breakfast")),
        )

        assertTrue(added.isEmpty())
    }

    @Test
    fun what_is_added_stands_after_what_was_there_in_the_order_the_dump_had_it() {
        val own = listOf(
            CategoryFavorite(CATEGORY, "Lunch", sortOrder = 0),
            CategoryFavorite(CATEGORY, "Dinner", sortOrder = 4),
        )

        val added = favoritesToAdd(
            CATEGORY,
            own,
            listOf(FavoriteDump("Tea", sortOrder = 2), FavoriteDump("Pizza", sortOrder = 1)),
        )

        assertEquals(listOf("Pizza" to 5, "Tea" to 6), added.map { it.name to it.sortOrder })
        assertTrue(added.all { it.categoryId == CATEGORY })
    }

    @Test
    fun a_new_category_takes_the_whole_list_from_the_top() {
        val added = favoritesToAdd(
            CATEGORY,
            own = emptyList(),
            brought = listOf(FavoriteDump("Lunch", sortOrder = 0), FavoriteDump("Dinner", sortOrder = 1)),
        )

        assertEquals(listOf("Lunch" to 0, "Dinner" to 1), added.map { it.name to it.sortOrder })
    }

    @Test
    fun a_name_the_dump_carries_twice_is_added_once() {
        val added = favoritesToAdd(
            CATEGORY,
            own = emptyList(),
            brought = listOf(FavoriteDump("Lunch"), FavoriteDump("Lunch")),
        )

        assertEquals(1, added.size)
    }

    private companion object {
        const val CATEGORY = 7L
    }
}

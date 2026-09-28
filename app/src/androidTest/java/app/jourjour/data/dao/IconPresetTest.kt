package app.jourjour.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import app.jourjour.data.db.AppDatabase
import app.jourjour.data.db.DefaultDataCallback
import app.jourjour.data.entity.IconTarget

/**
 * Icons a person types by hand join the ones on offer, so the picker gets more useful with
 * use. Payment methods and categories keep separate sets: a card icon has no business
 * among the categories.
 */
@RunWith(AndroidJUnit4::class)
class IconPresetTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DefaultDataCallback(context))
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun each_target_has_a_set_of_its_own() = runBlocking {
        val paymentMethods = db.iconPresetDao().observeEmoji(IconTarget.PAYMENT_METHOD).first()
        val categories = db.iconPresetDao().observeEmoji(IconTarget.CATEGORY).first()

        assertEquals(listOf("💵", "💳", "🔳", "💸"), paymentMethods)
        assertEquals(true, categories.contains("⛽"))
        assertEquals(false, categories.contains("💳"))
    }

    @Test
    fun a_custom_icon_lands_last_and_only_under_its_own_target() = runBlocking {
        db.iconPresetDao().add(IconTarget.PAYMENT_METHOD, "🐙")

        val paymentMethods = db.iconPresetDao().observeEmoji(IconTarget.PAYMENT_METHOD).first()
        // The new icon goes last, where the eye looks for something new.
        assertEquals("🐙", paymentMethods.last())
        assertEquals(false, db.iconPresetDao().observeEmoji(IconTarget.CATEGORY).first().contains("🐙"))
    }

    @Test
    fun adding_the_same_icon_twice_changes_nothing() = runBlocking {
        val before = db.iconPresetDao().observeEmoji(IconTarget.PAYMENT_METHOD).first()
        db.iconPresetDao().add(IconTarget.PAYMENT_METHOD, "💳")

        assertEquals(before, db.iconPresetDao().observeEmoji(IconTarget.PAYMENT_METHOD).first())
    }
}

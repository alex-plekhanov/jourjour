package app.jourjour.ui.common

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class CommitTest {

    @Test
    fun `a second write while the first is still on its way is dropped`() = runBlocking {
        val commit = Commit()
        val gate = CompletableDeferred<Unit>()
        var runs = 0

        commit.launch(this) { runs++; gate.await() }
        yield()
        commit.launch(this) { runs++ }
        yield()
        assertEquals(1, runs)

        gate.complete(Unit)
        yield()
        commit.launch(this) { runs++ }
        yield()
        assertEquals(2, runs)
    }
}

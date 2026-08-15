// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.deox9.musicplayer.player.SavedQueue
import com.deox9.musicplayer.player.SavedQueueTrack
import com.deox9.musicplayer.player.SavedQueues
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Whether saving two queues actually leaves two queues.
 *
 * Written because the device disagreed with itself: the switcher listed three
 * queues, storage held one, and the one it held was named "Queue 3" — a name the
 * suggester only produces when "Queue" and "Queue 2" already exist. So they were
 * created and then lost. The rules in SavedQueues are unit-tested and correct, which
 * puts the fault in the layer that reads and writes them, or in what the UI does
 * with it. This tests the storage layer directly, against real files.
 */
@RunWith(RobolectricTestRunner::class)
class SavedQueuesRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: SavedQueuesRepository

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        repository = SavedQueuesRepository(context)
        clearStore()
    }

    @After
    fun tearDown() = runTest { clearStore() }

    /**
     * Emptied through the repository rather than by deleting the file.
     *
     * DataStore caches one instance per file name for the life of the process, so
     * deleting the file leaves the cached copy holding the previous test's data —
     * which is exactly what happened: a test expecting two queues saw ten, left over
     * from the test before it.
     */
    private suspend fun clearStore() {
        repository.update { SavedQueues() }
    }

    private fun queue(id: String, name: String, index: Int = 0, positionMs: Long = 0L) =
        SavedQueue(
            id = id,
            name = name,
            tracks = (1..3).map { SavedQueueTrack("uri-$id-$it", "Track $it", "Artist") },
            currentIndex = index,
            positionMs = positionMs,
            updatedAtMs = System.currentTimeMillis(),
        )

    @Test
    fun `saving two queues leaves two queues`() = runTest {
        repository.update { it.save(queue("a", "Queue")) }
        repository.update { it.save(queue("b", "Queue 2")) }

        val stored = repository.current()

        assertEquals(2, stored.queues.size)
        assertEquals(setOf("a", "b"), stored.queues.map { it.id }.toSet())
    }

    /**
     * The switcher's own flow: save, then activate, then save another. This is what
     * the Keep button does twice, and it is where the queues went missing.
     */
    @Test
    fun `the keep-then-keep-again flow leaves both queues`() = runTest {
        repository.update { it.save(queue("a", "Queue")).activate("a") }
        repository.update { it.save(queue("b", "Queue 2")).activate("b") }

        val stored = repository.current()

        assertEquals(2, stored.queues.size)
        assertEquals("b", stored.activeId)
    }

    /**
     * The per-second write that mirrors the live queue into the active one. It runs
     * between Keep presses, so if it clobbers the rest of the list the earlier
     * queues vanish exactly as observed.
     */
    @Test
    fun `mirroring the active queue does not remove the others`() = runTest {
        repository.update { it.save(queue("a", "Queue")).activate("a") }
        repository.update { it.save(queue("b", "Queue 2")).activate("b") }

        // What PlaybackService does on every position tick.
        repeat(3) {
            repository.update { queues ->
                val active = queues.queues.first { q -> q.id == queues.activeId }
                queues.save(active.copy(positionMs = 5_000L, updatedAtMs = System.currentTimeMillis()))
            }
        }

        val stored = repository.current()
        assertEquals(2, stored.queues.size)
        assertEquals(5_000L, stored.queues.first { it.id == "b" }.positionMs)
    }

    @Test
    fun `a queue survives being written and read back whole`() = runTest {
        repository.update { it.save(queue("a", "Late night", index = 2, positionMs = 134_000L)) }

        val restored = repository.current().queues.single()

        assertEquals("Late night", restored.name)
        assertEquals(2, restored.currentIndex)
        assertEquals(134_000L, restored.positionMs)
        assertEquals(3, restored.tracks.size)
        assertEquals("uri-a-1", restored.tracks.first().uri)
        assertEquals("Track 1", restored.tracks.first().title)
    }

    @Test
    fun `ten queues all persist`() = runTest {
        (1..10).forEach { n -> repository.update { it.save(queue("q$n", "Queue $n")) } }

        assertEquals(10, repository.current().queues.size)
    }

    @Test
    fun `an empty store reads as no queues rather than failing`() = runTest {
        assertTrue(repository.current().queues.isEmpty())
    }
}

package app.moodiary.core

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.moodiary.core.backup.RestoreState
import app.moodiary.core.database.MoodiaryDatabase
import app.moodiary.core.datastore.DraftStore
import app.moodiary.core.media.PhotoStore
import app.moodiary.data.repository.JournalRepository
import app.moodiary.feature.entryeditor.EntryDraft
import app.moodiary.feature.entryeditor.EditorViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class EditorSafetyTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Before fun setMain() { Dispatchers.setMain(Dispatchers.Unconfined) }
    @After fun resetMain() { Dispatchers.resetMain() }

    @Test fun untouchedDraftKeepsInstantAfterTimezoneChangeIncludingDstOverlap() {
        val timestamp = Instant.parse("2024-11-03T06:30:45Z").toEpochMilli()
        val original = "2024-11-03T01:30"
        val draft = EntryDraft(localDateTime = original, originalLocalDateTime = original, originalTimestamp = timestamp)
        assertEquals(timestamp, draft.timestamp(ZoneId.of("Asia/Shanghai")))
        assertEquals(Instant.parse("2024-11-03T02:30:00Z").toEpochMilli(), draft.copy(localDateTime = "2024-11-03T10:30").timestamp(ZoneId.of("Asia/Shanghai")))
    }

    @Test fun oldDraftPhotosSurviveCleanupAndAbandonedImportsAreRemoved() = runBlocking {
        val drafts = DraftStore(context)
        val photos = PhotoStore(context)
        val referenced = photos.file("draft.img").apply { writeBytes(byteArrayOf(1)); setLastModified(1) }
        val abandoned = photos.file("abandoned.img").apply { writeBytes(byteArrayOf(1)); setLastModified(1) }
        drafts.write(0, """{"photoPaths":["draft.img"]}""")
        photos.cleanUnreferenced(drafts.photoReferences())
        assertTrue(referenced.exists()); assertFalse(abandoned.exists())
        drafts.delete(0)
        photos.cleanUnreferenced(drafts.photoReferences())
        assertFalse(referenced.exists())
    }

    @Test fun deletedEntryMediaIsRemovedImmediatelyUnlessADraftStillOwnsIt() = runBlocking {
        val photos = PhotoStore(context)
        val linked = photos.file("recent-linked.img").apply { writeBytes(byteArrayOf(1)) }
        val removed = photos.file("recent-removed.img").apply { writeBytes(byteArrayOf(1)) }
        photos.cleanUnreferenced(setOf(linked.name), setOf(linked.name, removed.name))
        assertTrue(linked.exists()); assertFalse(removed.exists())
        linked.delete()
        Unit
    }

    @Test fun corruptDraftStopsCleanupRatherThanGuessingOwnership() = runBlocking {
        val drafts = DraftStore(context)
        drafts.write(0, "invalid json")
        try { drafts.photoReferences(); fail("Corrupt draft must stop cleanup") } catch (_: IllegalArgumentException) { }
        finally { drafts.delete(0) }
    }

    @Test fun savingPreventsConcurrentTypingAndDiscardUntilCommitFinishes() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, MoodiaryDatabase::class.java).build()
        val drafts = DraftStore(context); drafts.delete(0)
        try {
            val repository = JournalRepository(database, RestoreState(context)); repository.seedDefaults()
            val vm = EditorViewModel(SavedStateHandle(), repository, PhotoStore(context), drafts, context)
            vm.load(0, null)
            withTimeout(5000) { vm.busy.first { !it } }
            val moodId = repository.snapshot().moods.first().id
            vm.change { it.copy(moodId = moodId) }
            vm.change { it.copy(note = "Committed note") }
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val lock = async(Dispatchers.IO) { repository.withExclusive { entered.complete(Unit); release.await() } }
            entered.await(); vm.save()
            assertTrue(vm.busy.value)
            vm.change { it.copy(note = "Input while disabled") }
            var discarded = false; vm.discard { discarded = true }
            assertEquals("Committed note", vm.draft.value.note); assertFalse(discarded)
            release.complete(Unit); lock.await()
            withTimeout(5000) { vm.savedEntry.first { it } }
            assertEquals("Committed note", repository.snapshot().entries.single().note)
            assertNull(drafts.read(0))
        } finally { database.close(); drafts.delete(0) }
    }
}

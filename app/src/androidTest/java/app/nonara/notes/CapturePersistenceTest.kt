package app.nonara.notes

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import android.media.MediaPlayer

@RunWith(AndroidJUnit4::class)
class CapturePersistenceTest {
    private fun isolated(block:(Context)->Unit) {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(base.cacheDir,"test-${newId()}").apply { mkdirs() }
        val context=object:ContextWrapper(base) { override fun getFilesDir()=dir }
        try { block(context) } finally { dir.deleteRecursively() }
    }
    @Test fun newCapturesAreAlwaysSherpaRecords()=isolated { context ->
        val record=Captures(context).create(null,null,"de-DE","base")
        assertEquals("sherpa",record.getString("mode"))
        assertEquals("de-DE",record.getString("language"))
        assertEquals("base",record.getString("model"))
        assertEquals("recording",record.getString("state"))
    }
    @Test fun repeatedCaptureCommitDoesNotDuplicateNote()=isolated { context ->
        val captures=Captures(context)
        val r=captures.create(null,null,"de-DE","base")
        val id=r.getString("id")
        captures.commit(id,"Ein neuer Gedanke")
        captures.commit(id,"Ein neuer Gedanke")
        assertEquals("completed",captures.read(id).getString("state"))
        assertEquals("Ein neuer Gedanke",Stores.notes(context).list().single().body)
    }
    @Test fun completedRecordingKeepsPlayableAudioAfterRetryAndTrashRestore()=isolated { context ->
        val captures=Captures(context)
        val note=Note.create("Original")
        val store=Stores.notes(context)
        store.save(note)
        val id=captures.create(note,null,"de-DE","base").getString("id")
        val first=ByteArray(16_000) { 11 }
        val second=ByteArray(16_000) { 22 }
        captures.dir(id).resolve("2.pcm").writeBytes(second)
        captures.dir(id).resolve("1.pcm").writeBytes(first)
        captures.commit(id,"Diktat")
        captures.commit(id,"Diktat")
        store.trash(note.id)
        store.restore(note.id)
        val wav=context.filesDir.resolve("recordings/${note.id}/$id.wav")
        assertTrue(wav.isFile)
        assertArrayEquals(first+second,wav.readBytes().drop(44).toByteArray())
        assertFalse(captures.dir(id).resolve("1.pcm").exists())
        assertEquals("Original\n\nDiktat",store.get(note.id)?.body)
        val player=MediaPlayer()
        try {
            wav.inputStream().use { player.setDataSource(it.fd) }
            player.prepare()
            assertEquals(1000,player.duration)
        } finally { player.release() }
        store.trash(note.id)
        context.filesDir.resolve("trash/${note.id}.deleted").writeText("2000-01-01T00:00:00Z")
        store.purge()
        assertFalse(wav.exists())
    }
    @Test fun deletedTargetPreservesRecognizedTextForRecovery()=isolated { context ->
        val store=Stores.notes(context); val note=Note.create("Existing")
        store.save(note)
        val captures=Captures(context); val r=captures.create(note,null,"en-US","base")
        store.trash(note.id)
        try { captures.commit(r.getString("id"),"Recovered text"); fail("Must not resurrect a deleted note") } catch(_:IllegalStateException) {}
        assertTrue(store.list().isEmpty())
        assertEquals("Recovered text",captures.read(r.getString("id")).getString("text"))
    }
    @Test fun discardedCaptureCannotBeResurrected()=isolated { context ->
        val captures=Captures(context); val r=captures.create(null,null,"de-DE","base")
        val id=r.getString("id"); captures.state(id,"discarded")
        captures.state(id,"transcribing")
        captures.commit(id,"Must not appear")
        assertEquals("discarded",captures.read(id).getString("state"))
        assertTrue(Stores.notes(context).list().isEmpty())
    }
}

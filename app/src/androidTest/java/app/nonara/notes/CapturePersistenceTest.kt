package app.nonara.notes

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CapturePersistenceTest {
    private fun isolated(block:(Context)->Unit) {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(base.cacheDir,"test-${newId()}").apply { mkdirs() }
        val context=object:ContextWrapper(base) { override fun getFilesDir()=dir }
        try { block(context) } finally { dir.deleteRecursively() }
    }
    @Test fun repeatedCaptureCommitDoesNotDuplicateNote()=isolated { context ->
        val captures=Captures(context)
        val r=captures.create(null,null,"android","de-DE","base")
        val id=r.getString("id")
        captures.commit(id,"Ein neuer Gedanke")
        captures.commit(id,"Ein neuer Gedanke")
        assertEquals("completed",captures.read(id).getString("state"))
        assertEquals("Ein neuer Gedanke",Stores.notes(context).list().single().body)
    }
    @Test fun deletedTargetPreservesRecognizedTextForRecovery()=isolated { context ->
        val store=Stores.notes(context); val note=Note.create("Existing")
        store.save(note)
        val captures=Captures(context); val r=captures.create(note,null,"android","en-US","base")
        store.trash(note.id)
        try { captures.commit(r.getString("id"),"Recovered text"); fail("Must not resurrect a deleted note") } catch(_:IllegalStateException) {}
        assertTrue(store.list().isEmpty())
        assertEquals("Recovered text",captures.read(r.getString("id")).getString("text"))
    }
    @Test fun discardedCaptureCannotBeResurrected()=isolated { context ->
        val captures=Captures(context); val r=captures.create(null,null,"sherpa","de-DE","base")
        val id=r.getString("id"); captures.state(id,"discarded")
        captures.state(id,"transcribing")
        captures.commit(id,"Must not appear")
        assertEquals("discarded",captures.read(id).getString("state"))
        assertTrue(Stores.notes(context).list().isEmpty())
    }
}

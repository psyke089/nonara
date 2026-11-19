package app.nonara.notes

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.concurrent.thread

class EditorSaveTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private class QueuedIo:CoroutineDispatcher() {
        val tasks=LinkedBlockingQueue<Runnable>()
        override fun dispatch(context:CoroutineContext,block:Runnable) { tasks.add(block) }
        fun next():Runnable = checkNotNull(tasks.poll(5,TimeUnit.SECONDS)) { "Editor did not request storage" }
    }
    private fun eventually(check:()->Boolean) {
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
        while(!check()) {
            check(System.nanoTime()<deadline) { "Editor did not reach the expected state" }
            Thread.sleep(10)
        }
    }
    private fun finishIoBeforeMainResumes(task:Runnable,action:()->Unit) {
        instrumentation.runOnMainSync {
            val committed=CountDownLatch(1)
            thread { try { task.run() } finally { committed.countDown() } }
            assertTrue(committed.await(5,TimeUnit.SECONDS))
            action()
        }
    }

    @Test fun typingAfterDiskCommitBeforeMainResumesDoesNotCauseAConflict() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val io=QueuedIo()
        lateinit var model:AppModel
        val owner=ViewModelStore()
        val note=Note.create("original")
        val store=Stores.notes(app)
        store.save(note)
        try {
            instrumentation.runOnMainSync {
                model=AppModel(app,io); owner.put("editor",model)
                model.open(note); model.edit("first edit")
            }
            io.next().run() // Durable journal, followed by debounce.
            val commit=io.next()
            // Disk has changed, but the main-thread continuation is still queued.
            finishIoBeforeMainResumes(commit) { model.edit("latest edit") }
            io.next().run()
            eventually { model.error!=null || io.tasks.isNotEmpty() }
            if(model.error==null) io.next().run()
            eventually { model.status!="Saving…" }
            assertNull("Own completed save must not conflict: ${model.error}",model.error)
            assertEquals("Saved",model.status)
            assertEquals("latest edit",store.get(note.id)?.body)
        } finally {
            instrumentation.runOnMainSync { owner.clear() }
            app.filesDir.resolve("notes/${note.id}.md").delete()
            app.filesDir.resolve("recovery/${note.id}.md").delete()
        }
    }

    @Test fun lifecycleFlushAfterDiskCommitKeepsTheEditorUsable() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val io=QueuedIo()
        lateinit var model:AppModel
        val owner=ViewModelStore()
        val note=Note.create("original")
        val store=Stores.notes(app)
        store.save(note)
        try {
            instrumentation.runOnMainSync {
                model=AppModel(app,io); owner.put("editor",model)
                model.open(note); model.edit("saved before backgrounding")
            }
            io.next().run()
            finishIoBeforeMainResumes(io.next()) { model.lifecycleStop() }
            io.next().run() // Lifecycle flush must use the acknowledged baseline.
            instrumentation.waitForIdleSync()
            assertNull(model.error)
            assertEquals("Saved",model.status)
            assertEquals("saved before backgrounding",store.get(note.id)?.body)
        } finally {
            instrumentation.runOnMainSync { owner.clear() }
            app.filesDir.resolve("notes/${note.id}.md").delete()
            app.filesDir.resolve("recovery/${note.id}.md").delete()
        }
    }

    @Test fun completedSaveFromPreviousEditorCannotChangeTheNewEditorsBaseline() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val io=QueuedIo()
        lateinit var model:AppModel
        val owner=ViewModelStore()
        val first=Note.create("first note")
        val second=Note.create("second note")
        val store=Stores.notes(app)
        store.save(first); store.save(second)
        try {
            instrumentation.runOnMainSync {
                model=AppModel(app,io); owner.put("editor",model)
                model.open(first); model.edit("saved first note")
            }
            io.next().run()
            finishIoBeforeMainResumes(io.next()) {
                model.open(second); model.edit("saved second note")
            }
            io.next().run()
            io.next().run()
            instrumentation.waitForIdleSync()
            assertNull(model.error)
            assertEquals("Saved",model.status)
            assertEquals(second.id,model.editor?.id)
            assertEquals("saved first note",store.get(first.id)?.body)
            assertEquals("saved second note",store.get(second.id)?.body)
        } finally {
            instrumentation.runOnMainSync { owner.clear() }
            for(note in listOf(first,second)) {
                app.filesDir.resolve("notes/${note.id}.md").delete()
                app.filesDir.resolve("recovery/${note.id}.md").delete()
            }
        }
    }
}

package app.nonara.notes

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EditorConflictTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun conflictCanBeDismissedEditedAndSavedAsCopyWithoutRepeatingThePopup() {
        val model=ViewModelProvider(compose.activity)[AppModel::class.java]
        val store=model.store
        val note=Note.create("Conflict regression ${newId()}")
        val draft="My draft ${newId()}"
        val nextNote="Still usable ${newId()}"
        store.save(note)
        try {
            compose.runOnIdle { model.open(note) }
            // A finished dictation changes the note while its old version is still open.
            val transcribed=store.appendCapture(note.id,newId(),"spoken text",null,null)
            compose.onNodeWithTag("body").performTextReplacement(draft)
            compose.waitUntil(5000) { model.error!=null }
            compose.onNodeWithContentDescription("Keep editing").performClick()
            compose.onNodeWithTag("body").assertTextContains(draft)
            compose.onNodeWithTag("body").performTextInput(" more")
            val expected=model.editor!!.body
            compose.runOnIdle { model.lifecycleStop() }
            runBlocking { model.flush() }
            compose.runOnIdle {
                assertNull("A dismissed conflict must not repeatedly reopen",model.error)
                assertEquals(transcribed.body,store.get(note.id)?.body)
            }
            compose.onNodeWithContentDescription("Save as copy").performClick()
            compose.waitUntil(5000) { model.page=="home" }
            assertEquals(transcribed.body,store.get(note.id)?.body)
            assertEquals(1,store.list().count { it.body.contains(draft) })
            assertEquals(expected,store.list().single { it.body.contains(draft) }.body)
            compose.onNodeWithContentDescription("Write").performClick()
            compose.onNodeWithTag("body").performTextInput(nextNote)
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitUntil(5000) { model.page=="home" }
            assertEquals(1,store.list().count { it.body==nextNote })
        } finally {
            compose.runOnIdle { model.error=null }
            (store.list().filter { it.id==note.id || it.body.contains(draft) || it.body==nextNote }).forEach {
                compose.activity.filesDir.resolve("notes/${it.id}.md").delete()
                compose.activity.filesDir.resolve("recovery/${it.id}.md").delete()
            }
        }
    }
}

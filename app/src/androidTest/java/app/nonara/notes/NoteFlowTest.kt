package app.nonara.notes

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class NoteFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun typedThoughtPersistsAndCanBeFoundAgain() {
        val text="Nonara UI regression ${newId()}"
        val store=Stores.notes(compose.activity)
        try {
            compose.onNodeWithText("Write").performClick()
            compose.onNodeWithTag("body").performTextInput(text)
            compose.waitUntil(10000) { store.list().any { it.body==text } }
            compose.onNodeWithText("Back").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("search").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("search").performTextInput(text)
            compose.onNodeWithTag("note-${store.list().single { it.body==text }.id}").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("body").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("body").assertTextContains(text)
            assertEquals(text,store.list().single { it.body==text }.body)
            compose.onNodeWithText("Back").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("search").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            store.list().filter { it.body==text }.forEach { note -> compose.activity.filesDir.resolve("notes/${note.id}.md").delete() }
        }
    }
}

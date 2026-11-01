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
    @Test fun landscapeKeepsWritingAndModelSelectionReachable() {
        val previous=compose.activity.requestedOrientation
        try {
            compose.runOnIdle { compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(5000) { compose.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            compose.onNodeWithText("Write",substring=false).assertIsDisplayed()
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithTag("model-small").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Back",substring=false).performClick()
            compose.onNodeWithTag("search").assertExists()
        } finally {
            compose.runOnIdle { compose.activity.requestedOrientation=previous }
        }
    }
    @Test fun selectingParakeetPreservesGerman() {
        val model=androidx.lifecycle.ViewModelProvider(compose.activity)[AppModel::class.java]
        val original=Triple(model.modelSize,model.mode,model.language)
        try {
            compose.runOnIdle { model.preference("language","de-DE") }
            compose.waitUntil(5000) { model.language=="de-DE" }
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithTag("model-parakeet-v3").performScrollTo().performClick()
            compose.waitUntil(5000) { model.modelSize=="parakeet-v3" && model.language=="de-DE" && model.mode=="sherpa" }
            compose.onNodeWithTag("model-parakeet-v3").assertIsSelected()
            compose.onNodeWithText("Deutsch",substring=false).performScrollTo().assertIsEnabled().assertIsSelected()
        } finally {
            compose.runOnIdle {
                model.preference("model",original.first)
                model.preference("mode",original.second)
                model.preference("language",original.third)
            }
            compose.waitUntil(5000) { model.modelSize==original.first && model.mode==original.second && model.language==original.third }
        }
    }
    @Test fun speechModelCanBeSelectedForTheNextRecording() {
        val model=androidx.lifecycle.ViewModelProvider(compose.activity)[AppModel::class.java]
        val original=model.modelSize
        try {
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithTag("model-small").performScrollTo().performClick()
            compose.waitUntil(5000) { model.modelSize=="small" }
            compose.onNodeWithTag("model-small").assertIsSelected()
            compose.onNodeWithTag("model-base").performScrollTo().performClick()
            compose.waitUntil(5000) { model.modelSize=="base" }
            compose.onNodeWithTag("model-base").assertIsSelected()
        } finally {
            compose.runOnIdle { model.preference("model",original) }
            compose.waitUntil(5000) { model.modelSize==original }
        }
    }
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

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

    @Test fun homeShowsSeparatePenAndMicrophoneButtonsAndRestoresCardNotes() {
        val model=androidx.lifecycle.ViewModelProvider(compose.activity)[AppModel::class.java]
        val note=Note.create("Card appearance ${newId()}")
        model.store.save(note)
        try {
            compose.waitUntil(5000) { model.notes.any { it.id==note.id } }
            val card=compose.onNodeWithTag("note-${note.id}").assertIsDisplayed()
            card.assertHasClickAction()
            compose.onNodeWithContentDescription("Write").assertIsDisplayed().assertHasClickAction()
            compose.onNodeWithContentDescription("Record").assertIsDisplayed().assertHasClickAction()
            val pen=compose.onNodeWithContentDescription("Write").fetchSemanticsNode().boundsInRoot
            val microphone=compose.onNodeWithContentDescription("Record").fetchSemanticsNode().boundsInRoot
            assertTrue(pen.right < microphone.left || microphone.right < pen.left)
            compose.onNodeWithContentDescription("Write").performClick()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitUntil(5000) { model.page=="home" }
            compose.onNodeWithTag("note-${note.id}").performClick()
            compose.waitUntil(5000) { model.page=="editor" }
        } finally { compose.activity.filesDir.resolve("notes/${note.id}.md").delete() }
    }

    @Test fun untitledPreviewAppearsOnceAndStatusStaysInTheSamePlace() {
        val model=androidx.lifecycle.ViewModelProvider(compose.activity)[AppModel::class.java]
        val note=Note.create("Untitled preview ${newId()}")
        model.store.save(note)
        try {
            compose.runOnIdle { model.query=note.body }
            compose.waitUntil(5000) { model.notes.any { it.id==note.id } }
            compose.onNodeWithTag("note-${note.id}").assertIsDisplayed()
            compose.onNodeWithText("Today").assertDoesNotExist()
            compose.onAllNodesWithText(note.body,useUnmergedTree=true).filter(!hasSetTextAction()).assertCountEquals(1)
            compose.onNodeWithTag("note-${note.id}").performClick()
            val editorStatus=compose.onNodeWithTag("app-status").assertTextEquals("Saved").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitUntil(5000) { model.page=="home" }
            assertEquals(editorStatus,compose.onNodeWithTag("app-status").assertTextEquals("Saved").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithContentDescription("Settings").performClick()
            assertEquals(editorStatus,compose.onNodeWithTag("app-status").assertTextEquals("Saved").fetchSemanticsNode().boundsInRoot)
        } finally { compose.activity.filesDir.resolve("notes/${note.id}.md").delete() }
    }

    @Test fun editorListsAndPlaysACompletedRecording() {
        val model=androidx.lifecycle.ViewModelProvider(compose.activity)[AppModel::class.java]
        val note=Note.create("Recording list ${newId()}")
        model.store.save(note)
        val id=model.captures.create(note,null,"de-DE","base").getString("id")
        try {
            model.captures.dir(id).resolve("1.pcm").writeBytes(ByteArray(320_000))
            model.captures.commit(id,"Recorded text")
            compose.runOnIdle { model.open(model.store.get(note.id)) }
            compose.onNodeWithTag("body").assertTextContains("${note.body}\n\nRecorded text")
            compose.waitUntil(5000) { compose.onAllNodesWithTag("recording-$id").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("recording-$id").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Play recording").performClick()
            compose.onNodeWithContentDescription("Pause recording").assertExists()
            compose.onNodeWithContentDescription("Pause recording").performClick()
            compose.onNodeWithContentDescription("Play recording").assertExists()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitUntil(5000) { model.page=="home" }
        } finally {
            compose.activity.filesDir.resolve("notes/${note.id}.md").delete()
            compose.activity.filesDir.resolve("recordings/${note.id}").deleteRecursively()
            model.captures.dir(id).deleteRecursively()
        }
    }
    @Test fun landscapeKeepsWritingAndModelSelectionReachable() {
        val previous=compose.activity.requestedOrientation
        try {
            compose.runOnIdle { compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(5000) { compose.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            compose.onNodeWithContentDescription("Write").assertIsDisplayed()
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithTag("model-small").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithTag("search").assertExists()
        } finally {
            compose.runOnIdle { compose.activity.requestedOrientation=previous }
        }
    }
    @Test fun selectingParakeetPreservesGerman() {
        val model=androidx.lifecycle.ViewModelProvider(compose.activity)[AppModel::class.java]
        val original=Pair(model.modelSize,model.language)
        try {
            compose.runOnIdle { model.preference("language","de-DE") }
            compose.waitUntil(5000) { model.language=="de-DE" }
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithTag("model-parakeet-v3").performScrollTo().performClick()
            compose.waitUntil(5000) { model.modelSize=="parakeet-v3" && model.language=="de-DE" }
            compose.onNodeWithTag("model-parakeet-v3").assertIsSelected()
            compose.onNodeWithText("Deutsch",substring=false).performScrollTo().assertIsEnabled().assertIsSelected()
        } finally {
            compose.runOnIdle {
                model.preference("model",original.first)
                model.preference("language",original.second)
            }
            compose.waitUntil(5000) { model.modelSize==original.first && model.language==original.second }
        }
    }
    @Test fun speechModelCanBeSelectedForTheNextRecording() {
        val model=androidx.lifecycle.ViewModelProvider(compose.activity)[AppModel::class.java]
        val original=model.modelSize
        try {
            compose.onNodeWithContentDescription("Settings").performClick()
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
    @Test fun everyNewChoiceIsReachableAndPreservesGerman() {
        val model=androidx.lifecycle.ViewModelProvider(compose.activity)[AppModel::class.java]
        val original=Pair(model.modelSize,model.language)
        try {
            compose.runOnIdle { model.preference("language","de-DE") }
            compose.waitUntil(5000) { model.language=="de-DE" }
            compose.onNodeWithContentDescription("Settings").performClick()
            for(id in listOf("canary-180m","omnilingual-300m","nemotron-3.5","whisper-turbo","whisper-large-v3")) {
                compose.onNodeWithTag("model-$id").performScrollTo().performClick()
                compose.waitUntil(5000) { model.modelSize==id && model.language=="de-DE" }
                compose.onNodeWithTag("model-$id").assertIsSelected()
            }
            compose.onNodeWithText("Deutsch",substring=false).performScrollTo().assertIsSelected()
        } finally {
            compose.runOnIdle {
                model.preference("model",original.first)
                model.preference("language",original.second)
            }
            compose.waitUntil(5000) { model.modelSize==original.first && model.language==original.second }
        }
    }
    @Test fun settingsExposeNoAndroidBackendControls() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Android live",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Request Android language pack",substring=false).assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
    }
    @Test fun recordingWithoutAnInstalledModelOpensSettings() {
        val model=androidx.lifecycle.ViewModelProvider(compose.activity)[AppModel::class.java]
        val original=Pair(model.modelSize,model.language)
        val missing=model.models.ids().firstOrNull { !model.models.ready(it) }
        org.junit.Assume.assumeTrue("Every model is installed; cannot exercise the missing-model gate",missing!=null)
        val target=missing!!
        try {
            compose.runOnIdle { model.preference("model",target) }
            compose.waitUntil(5000) { model.modelSize==target && target !in model.installed }
            compose.onNodeWithContentDescription("Record").performClick()
            compose.waitUntil(5000) { model.page=="settings" && model.error!=null }
            assertNull(model.active)
            assertTrue(model.error.orEmpty().contains("Settings"))
        } finally {
            compose.runOnIdle {
                model.error=null; model.page="home"
                model.preference("model",original.first)
                if(model.language!=original.second) model.preference("language",original.second)
            }
            compose.waitUntil(5000) { model.modelSize==original.first && model.page=="home" }
        }
    }
    @Test fun languageAndModelSelectionSurviveRestart() {
        val model=androidx.lifecycle.ViewModelProvider(compose.activity)[AppModel::class.java]
        val original=Pair(model.modelSize,model.language)
        try {
            compose.runOnIdle {
                model.preference("model","small")
                model.preference("language","en-US")
            }
            compose.waitUntil(5000) { model.modelSize=="small" && model.language=="en-US" }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10000) {
                val restarted=androidx.lifecycle.ViewModelProvider(compose.activity)[AppModel::class.java]
                restarted.modelSize=="small" && restarted.language=="en-US"
            }
        } finally {
            val current=androidx.lifecycle.ViewModelProvider(compose.activity)[AppModel::class.java]
            compose.runOnIdle {
                current.preference("model",original.first)
                current.preference("language",original.second)
            }
            compose.waitUntil(5000) { current.modelSize==original.first && current.language==original.second }
        }
    }
    @Test fun typedThoughtPersistsAndCanBeFoundAgain() {
        val text="Nonara UI regression ${newId()}"
        val store=Stores.notes(compose.activity)
        try {
            compose.onNodeWithContentDescription("Write").performClick()
            compose.onNodeWithTag("body").performTextInput(text)
            compose.waitUntil(10000) { store.list().any { it.body==text } }
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("search").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("search").performTextInput(text)
            compose.onNodeWithTag("note-${store.list().single { it.body==text }.id}").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("body").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("body").assertTextContains(text)
            assertEquals(text,store.list().single { it.body==text }.body)
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("search").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            store.list().filter { it.body==text }.forEach { note -> compose.activity.filesDir.resolve("notes/${note.id}.md").delete() }
        }
    }
}

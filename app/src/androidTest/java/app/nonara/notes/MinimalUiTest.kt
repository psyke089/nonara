package app.nonara.notes

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Rule
import org.junit.Test

class MinimalUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun homeUsesLogoAndIconActionsWithoutModelSwitcherOrDecorativeCopy() {
        compose.onNodeWithText("A little room",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Write it down",substring=true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Nonara").assertIsDisplayed()
        compose.onNodeWithContentDescription("Record").assertIsDisplayed()
        compose.onNodeWithContentDescription("Write").assertIsDisplayed()
        compose.onNodeWithTag("model-switcher").assertDoesNotExist()
        compose.onNodeWithContentDescription("Undo last deletion").assertDoesNotExist()
        val model=ViewModelProvider(compose.activity)[AppModel::class.java]
        compose.onNodeWithText(model.models.info(model.modelSize).name,substring=true).assertDoesNotExist()
    }

    @Test fun idleModelFitsACompactRowAtStandardFontSize() {
        val model=ViewModelProvider(compose.activity)[AppModel::class.java]
        val id=model.models.ids().first { model.modelTransfers[it]==null && (!model.streaming || model.models.info(it).streaming) }
        compose.onNodeWithContentDescription("Settings").performClick()
        val row=compose.onNodeWithTag("model-card-$id").performScrollTo()
        val bounds=row.getUnclippedBoundsInRoot()
        val height=(bounds.bottom-bounds.top).value
        val scale=compose.activity.resources.configuration.fontScale
        org.junit.Assert.assertTrue("Idle model height: ${height}dp",height<=96f*maxOf(scale,1f))
        compose.onNodeWithTag("model-$id").assertHasClickAction()
    }

    @Test fun streamingPreferenceSurvivesActivityRecreation() {
        val model=ViewModelProvider(compose.activity)[AppModel::class.java]
        val previous=model.streaming
        try {
            compose.runOnIdle { model.preference("streaming","true") }
            compose.waitUntil(5000) { model.streaming }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(5000) { ViewModelProvider(compose.activity)[AppModel::class.java].streaming }
        } finally {
            val current=ViewModelProvider(compose.activity)[AppModel::class.java]
            compose.runOnIdle { current.preference("streaming",previous.toString()) }
            compose.waitUntil(5000) { current.streaming==previous }
        }
    }

    @Test fun settingsShowStreamingAndLanguageChipsAndRejectIncompatibleLanguage() {
        val model=ViewModelProvider(compose.activity)[AppModel::class.java]
        val original=Pair(model.modelSize,model.language)
        try {
            compose.runOnIdle { model.preference("language","de-DE") }
            compose.waitUntil(5000) { model.language=="de-DE" }
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("Make it yours",substring=true).assertDoesNotExist()
            compose.onNodeWithText("Your voice, your choice",substring=true).assertDoesNotExist()
            compose.onNodeWithTag("dictation-streaming").performScrollTo().performClick()
            compose.onNodeWithTag("model-kroko-de").performScrollTo().performClick()
            compose.onNodeWithTag("model-kroko-de-language-de-DE",useUnmergedTree=true).assertExists()
            compose.onNodeWithTag("model-kroko-de-streaming",useUnmergedTree=true).assertExists()
            compose.onNodeWithTag("language-en-US").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("model-base").assertDoesNotExist()
        } finally {
            compose.runOnIdle {
                model.preference("streaming","false")
                model.preference("model",original.first)
                model.preference("language",original.second)
            }
            compose.waitUntil(5000) { !model.streaming && model.modelSize==original.first && model.language==original.second }
        }
    }
}

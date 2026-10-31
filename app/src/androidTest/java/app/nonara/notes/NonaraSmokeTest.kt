package app.nonara.notes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Proves the real activity launches and renders the notes surface on a device. */
@RunWith(AndroidJUnit4::class)
class NonaraSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun emptyStateIsShown() {
        val activity = compose.activity
        compose.onNodeWithText(activity.getString(R.string.notes_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.notes_empty_body)).assertIsDisplayed()
    }
}

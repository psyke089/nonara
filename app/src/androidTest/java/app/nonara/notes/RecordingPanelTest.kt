package app.nonara.notes

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.material3.Text
import app.nonara.notes.ui.RecordingPanel
import app.nonara.notes.ui.theme.NonaraTheme
import org.junit.Rule
import org.junit.Test

class RecordingPanelTest {
    @get:Rule val compose=createComposeRule()

    @Test fun recordingShowsDurationInputActivityAndPauseResumeControls() {
        compose.setContent {
            var paused by remember { mutableStateOf(false) }
            var finished by remember { mutableStateOf(false) }
            NonaraTheme {
                if(finished) Text("Recording finished") else RecordingPanel(
                    paused=paused,busy=false,elapsed=65000,level=0.5f,
                    label="Whisper base · Deutsch",preview="",
                    onPause={paused=!paused},onFinish={finished=true},onDiscard={})
            }
        }
        compose.onNodeWithText("Recording",substring=false).assertIsDisplayed()
        compose.onNodeWithText("01:05").assertIsDisplayed()
        compose.onNodeWithContentDescription("Microphone activity").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Sound detected"))
        compose.onNodeWithText("Pause",substring=false).performClick()
        compose.onNodeWithText("Recording paused").assertIsDisplayed()
        compose.onNodeWithContentDescription("Microphone activity").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Microphone paused"))
        compose.onNodeWithText("Resume").performClick()
        compose.onNodeWithText("Recording",substring=false).assertIsDisplayed()
        compose.onNodeWithText("Finish",substring=false).performClick()
        compose.onNodeWithText("Recording finished").assertIsDisplayed()
    }
}

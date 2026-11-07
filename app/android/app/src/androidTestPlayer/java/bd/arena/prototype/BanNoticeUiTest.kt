package bd.arena.prototype

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BanNoticeUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()

    private val colors=darkColorScheme(primary=Color(0xFFC4DF9B),onPrimary=Color(0xFF101311),background=Color(0xFF101311),surface=Color(0xFF1B211D),onSurface=Color(0xFFF1F3ED),onBackground=Color(0xFFF1F3ED),onSurfaceVariant=Color(0xFFABB5AD),surfaceVariant=Color(0xFF273029),outline=Color(0xFF6B786D))
    private val shapes=Shapes(small=RoundedCornerShape(8.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(20.dp),extraLarge=RoundedCornerShape(24.dp))
    private fun content(body:@Composable ()->Unit) {
        check(BuildConfig.FIREBASE_USE_EMULATOR)
        ui.runOnUiThread{ui.activity.setContent(content=body)}
    }
    private fun capture(name:String) {
        ui.waitForIdle()
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap=automation.takeScreenshot()
        val file=java.io.File(ui.activity.getExternalFilesDir(null),"moderation-$name.png")
        file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/${file.name}")).use{it.readBytes()}
    }

    @Test fun playerSeesAccountReasonAndCanRetryOrSignOutAtLargeBanglaText() {
        var retry=0;var signOut=0
        val reason="Sample · confirmed use of game hacks during the match."
        var bangla by mutableStateOf(false)
        var status by mutableStateOf(PlayerBanStatus(ready=true,active=true,reason=reason))
        content {
            MaterialTheme(colorScheme=colors,shapes=shapes) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {
                    LocalizedTypography(bangla) { Surface(Modifier.fillMaxSize(),color=colors.background) {
                        AccountBlockedScreen(status,bangla,{retry++},{signOut++})
                    } }
                }
            }
        }
        ui.onNodeWithText("This account is banned.").assertExists()
        ui.onNodeWithText(reason).performScrollTo().assertIsDisplayed()
        capture("account-en-large")
        ui.runOnIdle{status=status.copy(ready=false,failed=true)}
        ui.onNodeWithText("Showing the last saved reason. Connect to verify the current status.").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Retry").performScrollTo().performClick();assertEquals(1,retry)
        ui.onNodeWithText("Sign out").performScrollTo().performClick();assertEquals(1,signOut)
        ui.runOnIdle{bangla=true}
        ui.onNodeWithText("নিষেধাজ্ঞার কারণ").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText(reason).performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("সাইন আউট").performScrollTo().assertIsDisplayed()
        capture("account-bn-large")
        assertEquals(1,signOut)
    }

    @Test fun matchBanShowsReasonAndClearsWhenLiftedWhileMissingReasonHasNoInventedCause() {
        var status by mutableStateOf(PlayerBanStatus(ready=true,active=true,reason="Sample · match rule violation"))
        var retry=0
        content {MaterialTheme(colorScheme=colors,shapes=shapes) {Surface(Modifier.fillMaxSize(),color=colors.background) {
            Column(Modifier.widthIn(max=375.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {MatchBanNotice(status,false){retry++}}
        }}}
        ui.onNodeWithText("You are banned from this match.").assertIsDisplayed()
        ui.onNodeWithText(status.reason).assertIsDisplayed()
        capture("match-reason")
        ui.onNodeWithText("Retry").performScrollTo().performClick();assertEquals(1,retry)
        ui.runOnIdle{status=status.copy(reason="")}
        ui.onNodeWithText("The reason is unavailable. Reconnect and retry.").assertIsDisplayed()
        ui.runOnIdle{status=status.copy(active=false)}
        ui.onNodeWithText("You are banned from this match.").assertDoesNotExist()
        ui.onNodeWithText("The reason is unavailable. Reconnect and retry.").assertDoesNotExist()
    }
}

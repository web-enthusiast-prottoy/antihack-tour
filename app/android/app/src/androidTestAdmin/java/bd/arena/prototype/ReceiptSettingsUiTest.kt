package bd.arena.prototype

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Tasks
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReceiptSettingsUiTest {
    @get:Rule val ui=createAndroidComposeRule<AdminActivity>()
    @Test fun diagnosticActionsAreReachableInAShortLargeTextLayout() {
        check(BuildConfig.FIREBASE_USE_EMULATOR)
        val session=AdminSession(ui.activity)
        try {
            Tasks.await(session.auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),20,TimeUnit.SECONDS)
            ui.waitUntil(20_000){session.owner}
            ui.runOnUiThread{ui.activity.setContent {
                MaterialTheme(colorScheme=darkColorScheme()) {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {
                        Box(Modifier.width(375.dp).height(320.dp)) {
                            val settings=rememberSmsDepositSettings(session,false){}
                            PaymentSettings(session,false,{},settings)
                        }
                    }
                }
            }}
            ui.waitUntil(20_000){ui.onAllNodesWithText("Copy verification report").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("Open bKash notification access").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("View verification details").performScrollTo().performClick()
            ui.onNodeWithText("Copy verification report").performScrollTo().assertIsDisplayed().performClick()
            ui.runOnIdle {
                val clipboard=ui.activity.getSystemService(android.content.ClipboardManager::class.java)
                val text=clipboard.primaryClip!!.getItemAt(0).coerceToText(ui.activity).toString()
                assertTrue(text.contains("Antihack Admin"))
                assertTrue(text.contains("SMS access:"))
                assertTrue(text.contains("Notification access:"))
                assertFalse(text.contains("01600000000"))
            }
        }finally{session.auth.signOut();session.close()}
    }
}

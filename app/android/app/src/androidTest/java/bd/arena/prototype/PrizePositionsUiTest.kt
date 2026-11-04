package bd.arena.prototype

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

class PrizePositionsUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun waitText(text:String){ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun node(text:String):SemanticsNodeInteraction {
        val lazy=ui.onAllNodesWithTag("admin-records").fetchSemanticsNodes()
        if(lazy.isNotEmpty())ui.onNodeWithTag("admin-records").performScrollToNode(hasText(text))
        return ui.onNodeWithText(text).performScrollTo()
    }
    private fun shell(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use{it.readBytes().toString(Charsets.UTF_8).trim()}
    private fun capture(name:String){
        ui.waitForIdle()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap=automation.takeScreenshot()
        val file=java.io.File(ui.activity.getExternalFilesDir(null),"prizes-$name.png")
        file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/prizes-$name.png")).use{it.readBytes()}
    }
    @Test fun editorAndMatchManagementSharePrizePositionsWithPlayersAndLockAfterJoin() {
        check(BuildConfig.FIREBASE_USE_EMULATOR)
        ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit()
        ui.activityRule.scenario.recreate()
        val session=AdminSession(ui.activity)
        val id="prize-device-${System.currentTimeMillis()}"
        val title="Sample prize $id"
        fun login(){Tasks.await(session.auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),20,TimeUnit.SECONDS);ui.waitUntil(20_000){session.allowed}}
        fun openAdminMatches() {
            ui.onNodeWithText("Profile").performClick();node("Administration").performClick()
            waitText("Operations overview");ui.onNodeWithContentDescription("Admin navigation").performClick();ui.onNodeWithText("Matches").performClick()
            waitText(title+" updated")
        }
        fun total()=Tasks.await(session.db.document("matches/$id").get(Source.SERVER),20,TimeUnit.SECONDS).getLong("prizePaisa")
        fun save(rank:String,amount:String,first:Boolean=false) {
            node("Position").performTextReplacement(rank)
            node("Prize (taka)").performTextReplacement(amount)
            node("Save prize").performClick()
            if(first){waitText("Start a prize breakdown?");ui.onNode(hasText("Save prize") and hasAnyAncestor(isDialog())).performClick()}
            ui.waitUntil(20_000){ui.onNodeWithText("Position").fetchSemanticsNode().config[SemanticsProperties.EditableText].text.isEmpty()}
        }
        try {
            login()
            saveLifecycleFixture(session,id,mapOf("title" to title,"description" to "Local prize acceptance only.","image" to "solo","mode" to "solo","map" to "Bermuda","startsAt" to Date(System.currentTimeMillis()+86400000),"capacity" to 48L,"joined" to 0L,"entryPaisa" to 0L,"prizePaisa" to 90000L,"perKillPaisa" to 500L,"rules" to "Local sample. No real rewards.","cancellationTerms" to "Local sample only.","status" to "open","updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to session.uid!!))
            ui.onNodeWithText("Profile").performClick();node("Administration").performClick()
            waitText("Operations overview");ui.onNodeWithContentDescription("Admin navigation").performClick();ui.onNodeWithText("Matches").performClick();waitText(title)
            ui.onNode(hasText("Edit") and hasAnySibling(hasText(title))).performScrollTo().performClick()
            save("1","500",true);assertEquals(50000L,total())
            node("Prize pool (taka)").assertIsNotEnabled()
            node("Title").performTextReplacement(title+" updated")
            node("Save").performClick();waitText(title+" updated")
            ui.onNode(hasText("Room and results") and hasAnySibling(hasText(title+" updated"))).performScrollTo().performClick()
            ui.onNodeWithText("Prizes").performClick()
            save("2","300");save("3","200");save("4","100");assertEquals(110000L,total())
            node("Position").performTextReplacement("4")
            node("Remove prize").performClick()
            ui.waitUntil(20_000){ui.onNodeWithText("Position").fetchSemanticsNode().config[SemanticsProperties.EditableText].text.isEmpty()}
            assertEquals(100000L,total());capture("admin")
            session.auth.signOut();ui.waitUntil(20_000){ui.onAllNodesWithText("Explore").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("Explore").performClick();ui.onNodeWithContentDescription("Search match ID or title").performTextInput(id)
            waitText(title+" updated");ui.onNodeWithText(title+" updated").performClick()
            ui.onNodeWithTag("player-content").performScrollToNode(hasText("Prize distribution"))
            waitText("#3");ui.waitUntil(20_000){ui.onAllNodesWithText("Saved prizes · reconnect to confirm").fetchSemanticsNodes().isEmpty()};ui.onNodeWithText("#1").assertExists();ui.onNodeWithText("#2").assertExists();ui.onNodeWithText("#4").assertDoesNotExist();capture("player")
            val font=shell("settings get system font_scale")
            try {
                ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",true).commit()
                ui.activityRule.scenario.recreate();waitText(title+" updated")
                ui.onNodeWithTag("player-content").performScrollToNode(hasText("পুরস্কার বণ্টন"))
                shell("settings put system font_scale 2.0")
                ui.waitUntil(20_000){ui.activity.resources.configuration.fontScale>1.9f}
                waitText(title+" updated")
                ui.onNodeWithTag("player-content").performScrollToNode(hasText("পুরস্কার বণ্টন"));waitText("#3")
                ui.onNodeWithTag("player-content").performScrollToNode(hasText("#3"));ui.onNodeWithText("#3").assertIsDisplayed()
                capture("player-bangla-large")
            } finally {
                shell(if(font=="null")"settings delete system font_scale" else "settings put system font_scale $font")
                ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit()
                ui.activityRule.scenario.recreate()
            }
            login()
            val owner=session.uid!!
            val profile=Tasks.await(session.db.document("users/$owner").get(Source.SERVER),20,TimeUnit.SECONDS)
            val username=profile.getString("ffUsername").orEmpty().ifBlank{"Local prize player"}
            if(profile.getString("ffUsername").isNullOrBlank())Tasks.await(session.db.document("users/$owner").set(mapOf("ffUsername" to username),SetOptions.merge()),20,TimeUnit.SECONDS)
            val player=PlayerData(session);var joined=false
            player.join(Tournament(id,title,"solo","",0,48),listOf(username)){joined=it=="Registration confirmed."}
            ui.waitUntil(20_000){joined};player.close()
            ui.onNodeWithContentDescription("Back").performClick();openAdminMatches()
            ui.onNode(hasText("Room and results") and hasAnySibling(hasText(title+" updated"))).performScrollTo().performClick()
            ui.onNodeWithText("Prizes").performClick();waitText("Prize terms are locked.")
            node("Position").assertIsNotEnabled();node("Save prize").assertIsNotEnabled()
            capture("locked");assertEquals(100000L,total())
        } finally {session.auth.signOut();session.close()}
    }
}

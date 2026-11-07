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
    @get:Rule val ui=createAndroidComposeRule<AdminActivity>()
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
    @Test fun editorAndMatchManagementShareEditablePrizePositionsAfterJoin() {
        check(BuildConfig.FIREBASE_USE_EMULATOR)
        ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit()
        ui.activityRule.scenario.recreate()
        val session=AdminSession(ui.activity)
        val id="prize-device-${System.currentTimeMillis()}"
        val title="Sample prize $id"
        fun login(){Tasks.await(session.auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),20,TimeUnit.SECONDS);ui.waitUntil(20_000){session.allowed}}
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
            waitText("Needs review")
            ui.onNodeWithTag("admin-nav-Matches").performClick()
            waitText(title)
            ui.onNodeWithText(title).performScrollTo().performClick()
            node("Edit match").performClick()
            ui.onNodeWithText("Prizes").performClick()
            save("1","500",true);assertEquals(50000L,total())
            node("Prize pool (taka)").assertIsNotEnabled()
            ui.onNodeWithText("Details").performClick()
            node("Title").performTextReplacement(title+" updated")
            ui.onNodeWithText("Save").performClick();waitText(title+" updated")
            ui.onNodeWithText(title+" updated").performScrollTo().performClick()
            ui.onNodeWithText("Prizes").performClick()
            save("2","300");save("3","200");save("4","100");assertEquals(110000L,total())
            node("Position").performTextReplacement("4")
            node("Remove prize").performClick()
            ui.waitUntil(20_000){ui.onNodeWithText("Position").fetchSemanticsNode().config[SemanticsProperties.EditableText].text.isEmpty()}
            assertEquals(100000L,total());capture("admin")
            val owner=session.uid!!
            val profile=Tasks.await(session.db.document("users/$owner").get(Source.SERVER),20,TimeUnit.SECONDS)
            val username=profile.getString("ffUsername").orEmpty().ifBlank{"Local prize player"}
            if(profile.getString("ffUsername").isNullOrBlank())Tasks.await(session.db.document("users/$owner").set(mapOf("ffUsername" to username),SetOptions.merge()),20,TimeUnit.SECONDS)
            val player=PlayerData(session);var joined=false
            player.join(Tournament(id,title,"solo","",0,48),listOf(username)){joined=it=="Registration confirmed."}
            ui.waitUntil(20_000){joined};player.close()
            ui.onNodeWithContentDescription("Back").performClick();waitText(title+" updated")
            ui.onNodeWithText(title+" updated").performScrollTo().performClick()
            ui.onNodeWithText("Prizes").performClick();waitText("Position")
            node("Position").assertIsEnabled();node("Save prize").assertIsEnabled()
            save("2","350");capture("editable-after-join");assertEquals(105000L,total())
        } finally {session.auth.signOut();session.close()}
    }
}

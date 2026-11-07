package bd.arena.prototype

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Source
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class MatchSchedulesUiTest {
    @get:Rule val ui=createAndroidComposeRule<AdminActivity>()
    private fun waitText(text:String){ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun field(text:String):SemanticsNodeInteraction {
        ui.onNodeWithTag("admin-records").performScrollToNode(hasText(text))
        return ui.onNodeWithText(text).performScrollTo()
    }
    @Test fun matchFormCreatesTodaysBatchBeforeReportingSuccess() {
        check(BuildConfig.FIREBASE_USE_EMULATOR)
        ui.activity.getSharedPreferences("settings",0).edit().putBoolean("bangla",false).commit()
        ui.activityRule.scenario.recreate()
        val session=AdminSession(ui.activity)
        val title="Sample schedule ${System.currentTimeMillis()}"
        try {
            Tasks.await(session.auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),20,TimeUnit.SECONDS)
            ui.waitUntil(20_000){session.allowed}
            ui.onNodeWithTag("admin-nav-Matches").performClick()
            waitText("Create match")
            ui.waitUntil(20_000){runCatching{ui.onNodeWithText("Create match").assertIsEnabled()}.isSuccess}
            field("Create match").performClick();waitText("Title")
            field("Title").performTextReplacement(title)
            ui.onNodeWithText("Prizes").performClick()
            field("Total prize (taka)").performTextReplacement("100")
            field("Add prize position").performClick()
            field("Prize (taka) · #1").performTextReplacement("50")
            ui.onNodeWithText("Scheduling").performClick()
            ui.onNodeWithContentDescription("Repeat this match automatically").performScrollTo().performClick()
            field("Minutes between match starts").performTextReplacement("25")
            // Creation survives an Android activity recreation just like other match drafts.
            ui.activityRule.scenario.recreate()
            waitText("Create schedule")
            field("Minutes between match starts").assertTextContains("25")
            ui.onNodeWithText("Create schedule").performClick()
            waitText("Schedules");waitText(title)
            val query=session.db.collection("matchSchedules").whereEqualTo("template.title",title).limit(10)
            val saved=Tasks.await(query.get(Source.SERVER),20,TimeUnit.SECONDS).documents.single()
            assertEquals(25L,saved.getLong("delayMinutes"));assertEquals(listOf(5000L),saved.get("prizes"))
            assertEquals("solo",(saved.get("template") as Map<*,*>)["image"])
            assertEquals("completed",saved.getString("state"))
            ui.onNodeWithText(title).performScrollTo().performClick()
            ui.onNodeWithText("All matches created").assertExists()
            val runs=Tasks.await(saved.reference.collection("runs").orderBy("sequence").limit(100).get(Source.SERVER),20,TimeUnit.SECONDS).documents
            assertTrue(runs.isNotEmpty())
            assertTrue(runs.all{it.getTimestamp("startsAt")!=null})
        }finally{session.auth.signOut();session.close()}
    }
    @Test fun schedulingFieldsRemainReachableAtLargeBanglaTextInAShortLayout() {
        val today=LocalDate.now(ZoneId.of("Asia/Dhaka")).toString()
        ui.runOnUiThread{ui.activity.setContent {
            LocaleStrings.configure(ui.activity,true)
            MaterialTheme(colorScheme=darkColorScheme()) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {
                    LocalizedTypography(true) {
                        var delay by rememberSaveable{mutableStateOf("20")}
                        Column(Modifier.width(375.dp).height(320.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                            MatchSchedulingForm(true,true,{},today,{},today,{},540,{},1380,{},delay,{delay=it})
                        }
                    }
                }
            }
        }}
        ui.onNodeWithText("শুরুর সময়: 9:00 AM").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("ম্যাচ শুরুর ব্যবধান (মিনিট)").performScrollTo().performTextReplacement("25")
        ui.onNodeWithText("তারিখসীমা · দুই তারিখসহ").performScrollTo().assertIsDisplayed()
    }
}

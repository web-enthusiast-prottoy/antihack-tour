package bd.arena.prototype

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.google.android.gms.tasks.Tasks
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class TimedRoomUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun roomAppearsAfterTimeWithoutAdminEditAndReopensOnResume() {
        check(BuildConfig.FIREBASE_USE_EMULATOR)
        val session=AdminSession(ui.activity)
        try {
            Tasks.await(session.auth.signInWithEmailAndPassword("room-release@arena.test","RoomLocal123!"),20,TimeUnit.SECONDS)
            ui.waitUntil(20_000){session.uid!=null}
            // Emulator-only trusted fixture: arm a future release, then make no further room writes.
            val database="projects/demo-arena/databases/(default)"
            val base="http://10.0.2.2:8080/v1/$database/documents"
            val clock=java.net.URL("$base:batchGet").openConnection() as java.net.HttpURLConnection
            val serverTime=try {
                clock.requestMethod="POST"
                clock.doOutput=true
                clock.setRequestProperty("Authorization","Bearer owner")
                clock.setRequestProperty("Content-Type","application/json")
                clock.outputStream.use{it.write("""{"documents":["$database/documents/rooms/room-release-auto"],"mask":{"fieldPaths":["releaseAt"]}}""".toByteArray())}
                val response=clock.inputStream.bufferedReader().use{it.readText()}
                java.time.Instant.parse(org.json.JSONArray(response).getJSONObject(0).getString("readTime"))
            } finally {clock.disconnect()}
            val url=java.net.URL("$base/rooms/room-release-auto?updateMask.fieldPaths=releaseAt")
            val connection=url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod="PATCH";connection.doOutput=true
            connection.setRequestProperty("Authorization","Bearer owner")
            connection.setRequestProperty("Content-Type","application/json")
            val release=serverTime.plusSeconds(15).toString()
            connection.outputStream.use{it.write("{\"fields\":{\"releaseAt\":{\"timestampValue\":\"$release\"}}}".toByteArray())}
            check(connection.responseCode==200);connection.disconnect()
            ui.runOnUiThread {ui.activity.setContent {MaterialTheme {MatchRoomCard(session,"room-release-auto",true)}}}
            ui.waitUntil(10_000){ui.onAllNodesWithText("Room details have not been published or released for your entry yet.").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("TIMED-SAMPLE-ROOM").assertDoesNotExist()
            ui.waitUntil(35_000){ui.onAllNodesWithText("TIMED-SAMPLE-ROOM").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("TIMED-SAMPLE-PASS").assertIsDisplayed()
            ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            ui.waitUntil(20_000){ui.onAllNodesWithText("TIMED-SAMPLE-ROOM").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("Hide").performClick()
            ui.onNodeWithText("TIMED-SAMPLE-ROOM").assertDoesNotExist()
            ui.onNodeWithText("Show room").performClick()
            ui.waitUntil(20_000){ui.onAllNodesWithText("TIMED-SAMPLE-ROOM").fetchSemanticsNodes().isNotEmpty()}
        } finally {session.auth.signOut();session.close()}
    }
}

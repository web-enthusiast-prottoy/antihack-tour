package bd.arena.prototype

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Requires local Auth/Firestore and firebase/tests/device-fixture.mjs; never runs on live. */
class FreeRegistrationUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun freeJoinConfirmsRetriesAndRecoversAfterOfflineFailure() {
        assertEquals("emulator",BuildConfig.NOTICE_BACKEND)
        val auth=FirebaseAuth.getInstance(FirebaseApp.getInstance("arena-native"))
        auth.signOut()
        Tasks.await(auth.createUserWithEmailAndPassword("free-device-${System.currentTimeMillis()}@arena.test","PlayerLocal123!"),30,TimeUnit.SECONDS)
        val uid=auth.currentUser!!.uid
        val session=AdminSession(ui.activity)
        ui.waitUntil(10_000) {session.uid==uid}
        Tasks.await(session.db.document("users/$uid").set(mapOf("displayName" to "Device Player","ffUsername" to "DevicePlayer","ffUid" to "","avatar" to "solo")),30,TimeUnit.SECONDS)
        ui.waitUntil(20_000) {ui.onAllNodesWithText("Explore").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Explore").performClick()
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Device free registration"))
        ui.onNodeWithText("Device free registration").performClick()
        ui.onNodeWithText("Join match").performClick()
        ui.onNodeWithTag("join-confirm").performScrollTo().performClick()
        ui.waitUntil(20_000) {ui.onAllNodesWithText("Registration confirmed.").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("OK").performClick()
        val ref=session.db.document("matches/device-free-registration")
        val count=Tasks.await(ref.get(),10,TimeUnit.SECONDS).getLong("joined")
        assertEquals(1L,count)
        assertEquals("confirmed",Tasks.await(session.db.document("entries/${uid}_device-free-registration").get(),10,TimeUnit.SECONDS).getString("status"))
        ui.onNodeWithText("confirmed").assertIsNotEnabled()
        val screenshot=ui.onRoot().captureToImage().asAndroidBitmap()
        val capture=java.io.File(ui.activity.getExternalFilesDir(null),"free-registration.png")
        capture.outputStream().use {screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        screenshot.recycle()
        val copied=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cp ${capture.absolutePath} /data/local/tmp/arena-v09-free-registration.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(copied).use {it.readBytes()}
        val data=PlayerData(session)
        val match=Tournament("device-free-registration","Device free registration","Solo","",1,3)
        try {
            Tasks.await(session.db.disableNetwork(),10,TimeUnit.SECONDS)
            var offlineConfirmed=false
            ui.runOnUiThread {data.join(match) {offlineConfirmed=true}}
            ui.waitUntil(20_000) {!data.busy}
            assertFalse("Offline transaction must not report confirmation",offlineConfirmed)
            assertTrue(data.error.isNotEmpty())
        } finally {Tasks.await(session.db.enableNetwork(),10,TimeUnit.SECONDS)}
        var confirmed=false
        ui.runOnUiThread {data.join(match) {confirmed=true}}
        ui.waitUntil(20_000) {!data.busy}
        assertTrue(data.error,confirmed)
        assertEquals(count,Tasks.await(ref.get(),10,TimeUnit.SECONDS).getLong("joined"))
        session.close()
        auth.signOut()
    }
}

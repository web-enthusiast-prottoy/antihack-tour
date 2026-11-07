package bd.arena.prototype

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class AdminContentUiTest {
    @get:Rule val ui=createAndroidComposeRule<AdminActivity>()
    private fun auth()=FirebaseAuth.getInstance(FirebaseApp.getInstance("arena-native"))
    private fun waitText(text:String){try{ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}catch(error:Throwable){capture("failure-"+text.replace(Regex("[^A-Za-z0-9]+"),"-"));throw error}}
    private fun waitGone(text:String){try{ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()}}catch(error:Throwable){capture("failure-gone-"+text.replace(Regex("[^A-Za-z0-9]+"),"-"));throw error}}
    private fun scroll(text:String){ui.onNode(hasTestTag("admin-records") and hasScrollAction()).performScrollToNode(hasText(text))}
    private fun capture(name:String){
        ui.waitForIdle();android.os.SystemClock.sleep(250)
        val bmp=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file=java.io.File(ui.activity.getExternalFilesDir(null),"antihack-$name.png")
        file.outputStream().use{bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()
        shell("cp ${file.absolutePath} /data/local/tmp/antihack-$name.png")
    }
    private fun shell(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use{it.readBytes().toString(Charsets.UTF_8).trim()}
    private fun chooseSampleArtwork() {
        val context=ui.activity
        val uri=context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"antihack-test-upload.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png")})!!
        val bitmap=android.graphics.BitmapFactory.decodeResource(context.resources,R.drawable.brand_logo)
        context.contentResolver.openOutputStream(uri)!!.use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val monitor=instrumentation.addMonitor(android.content.IntentFilter(android.content.Intent.ACTION_GET_CONTENT).apply{addDataType("image/*");addCategory(android.content.Intent.CATEGORY_OPENABLE)},android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,android.content.Intent().setData(uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)),true)
        try {ui.onNodeWithText("Choose image").performClick();waitText("Use default image")}finally{instrumentation.removeMonitor(monitor);context.contentResolver.delete(uri,null,null)}
    }
    @Test fun categoryArtworkAndMatchEditorUseDatabase() {
        Tasks.await(auth().signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),30,TimeUnit.SECONDS)
        waitText("Needs review")
        ui.onNodeWithTag("admin-nav-Settings").performClick()
        ui.onNodeWithText("Categories").performScrollTo().performClick();waitText("Create category")
        val categoryName="Sample Device ${System.currentTimeMillis()}"
        ui.onNodeWithText("Create category").performClick();ui.onNodeWithText("Category name").performTextInput(categoryName)
        ui.onNodeWithText("Category name").assertTextContains(categoryName)
        chooseSampleArtwork()
        ui.onNodeWithText("Category name").assertTextContains(categoryName)
        ui.onNodeWithText("Save category").performScrollTo().performClick();waitGone("Category name");ui.onNodeWithTag("category-list").performScrollToNode(hasText(categoryName));waitText(categoryName)
        ui.onNodeWithText(categoryName).performClick();waitText("Category name");ui.onNodeWithText("Category name").performTextReplacement(categoryName+" Updated")
        ui.onNodeWithText("Save category").performScrollTo().performClick();waitGone("Category name");ui.onNodeWithTag("category-list").performScrollToNode(hasText(categoryName+" Updated"));waitText(categoryName+" Updated");capture("admin-categories")
        ui.onNodeWithContentDescription("Back").performClick();waitText("Manage announcements")
        ui.onNodeWithTag("admin-nav-Matches").performClick()
        ui.waitUntil(20_000){ui.onAllNodes(hasText("Create match") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        scroll("Sample · Island Solo");capture("admin-matches")
        scroll("Create match")
        ui.onNodeWithText("Create match").performClick()
        val matchTitle="Sample editor ${System.currentTimeMillis()}"
        ui.onNodeWithText("Title").performTextInput(matchTitle)
        scroll("Category: Uncategorized");ui.onNodeWithText("Category: Uncategorized").performClick()
        ui.onNode(hasText(categoryName+" Updated") and hasAnyAncestor(isPopup())).performScrollTo().performClick()
        scroll("Description");ui.onNodeWithText("Description").performTextInput("Local sample editor verification.")
        scroll("Choose image");chooseSampleArtwork()
        scroll("Entry fee (taka)");ui.onNodeWithText("Entry fee (taka)").performTextReplacement("1.50")
        scroll("Prize pool (taka)");ui.onNodeWithText("Prize pool (taka)").performTextReplacement("100.255")
        scroll("Rules");ui.onNodeWithText("Rules").performTextInput("Local sample only.")
        scroll("Cancellation terms");ui.onNodeWithText("Cancellation terms").performTextInput("Local sample only.")
        scroll("Save");ui.onNodeWithText("Save").performClick();waitText("Enter valid taka amounts with up to two decimals.")
        scroll("Prize pool (taka)");ui.onNodeWithText("Prize pool (taka)").performTextReplacement("100.25");capture("admin-editor")
        scroll("Save");ui.onNodeWithText("Save").performClick();waitText("Saved.")
        val session=AdminSession(ui.activity)
        try {
            val saved=Tasks.await(session.db.collection("matches").whereEqualTo("title",matchTitle).limit(1).get(com.google.firebase.firestore.Source.SERVER),20,TimeUnit.SECONDS).documents.single()
            assertEquals(150L,saved.getLong("entryPaisa"));assertEquals(10025L,saved.getLong("prizePaisa"));assertFalse(saved.getString("categoryId").isNullOrBlank());assertFalse(saved.getString("artworkId").isNullOrBlank())
            val image=Tasks.await(session.db.document("artwork/${saved.getString("artworkId")}").get(com.google.firebase.firestore.Source.SERVER),20,TimeUnit.SECONDS)
            assertTrue(image.getBlob("data")!!.toBytes().size<=maxArtworkBytes)
        } finally {session.close()}
        auth().signOut()
    }
}

package bd.arena.prototype

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Requires paging-fixture.mjs. Uses its own disposable project and the real client rules. */
class BoundedFeedTest {
    private lateinit var db:FirebaseFirestore
    private lateinit var auth:FirebaseAuth
    private var feed:BoundedFeed?=null
    @Volatile private var state=BoundedFeedState()
    @Before fun setup() {
        check(BuildConfig.FIREBASE_USE_EMULATOR)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val existing=FirebaseApp.getApps(context).find{it.name=="paging-test"}
        val app=existing?:FirebaseApp.initializeApp(context,FirebaseOptions.Builder().setProjectId("demo-arena-paging").setApplicationId("1:1234567890:android:paging-local").setApiKey("local-emulator-key").build(),"paging-test")
        auth=FirebaseAuth.getInstance(app);db=FirebaseFirestore.getInstance(app)
        if(existing==null){auth.useEmulator("10.0.2.2",9099);db.useEmulator("10.0.2.2",8080)}
        auth.signOut();state=BoundedFeedState()
    }
    @After fun cleanup(){feed?.close();Tasks.await(db.enableNetwork(),10,TimeUnit.SECONDS);auth.signOut()}
    private fun await(condition:()->Boolean) {
        val end=System.currentTimeMillis()+20_000
        while(!condition()&&System.currentTimeMillis()<end)Thread.sleep(25)
        assertTrue("rows=${state.rows.size}, loading=${state.loading}, cached=${state.cached}, more=${state.more}, error=${state.error}",condition())
    }
    private fun listen(query:Query,automatic:Boolean=false) {
        feed=BoundedFeed(query,automatic){state=it}.also{it.start()}
    }
    @Test fun manualPagesReachEveryPublicMatchWithoutDuplicatesAndSurviveOffline() {
        listen(db.collection("matches").whereIn("status",listOf("open","closed","live","review","settled","cancelled")))
        await{!state.loading&&!state.cached&&state.rows.size==100};assertTrue(state.more)
        feed!!.loadMore();await{!state.loading&&!state.cached&&state.rows.size==200};assertTrue(state.more)
        feed!!.loadMore();await{!state.loading&&!state.cached&&state.rows.size==205};assertFalse(state.more)
        assertEquals((0..204).map{"page"+it.toString().padStart(3,'0')},state.rows.map{it.id})
        Tasks.await(db.disableNetwork(),10,TimeUnit.SECONDS);await{state.cached};assertEquals(205,state.rows.size)
        Tasks.await(db.enableNetwork(),10,TimeUnit.SECONDS);await{!state.cached&&!state.loading};assertEquals(205,state.rows.size)
    }
    @Test fun automaticCategoriesRebaseWhenTheFirstPageBoundaryMoves() {
        listen(db.collection("categories").whereEqualTo("active",true),true)
        await{!state.loading&&!state.cached&&state.rows.size==205};assertFalse(state.more)
        Tasks.await(auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),20,TimeUnit.SECONDS)
        val first=db.document("categories/page000")
        Tasks.await(first.update(mapOf("active" to false,"updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to auth.currentUser!!.uid)),20,TimeUnit.SECONDS)
        await{!state.loading&&!state.cached&&state.rows.size==204&&state.rows.none{it.id=="page000"}}
        val early=db.document("categories/page-early")
        Tasks.await(early.set(mapOf("name" to "Sample early category","order" to 0L,"active" to true,"updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to auth.currentUser!!.uid)),20,TimeUnit.SECONDS)
        await{!state.loading&&!state.cached&&state.rows.size==205&&state.rows.first().id=="page-early"}
        assertEquals(205,state.rows.map{it.id}.distinct().size)
        Tasks.await(db.document("categories/page204").update(mapOf("name" to "Sample updated tail","updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to auth.currentUser!!.uid)),20,TimeUnit.SECONDS)
        await{state.rows.last().getString("name")=="Sample updated tail"}
    }
    @Test fun deniedUnfilteredGuestQueryCanRetryAfterStaffAuthentication() {
        listen(db.collection("categories"))
        await{state.error};assertFalse(state.loading)
        Tasks.await(auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"),20,TimeUnit.SECONDS)
        feed!!.retry();await{!state.error&&!state.loading&&!state.cached&&state.rows.size==100}
        assertTrue(state.more)
    }
}

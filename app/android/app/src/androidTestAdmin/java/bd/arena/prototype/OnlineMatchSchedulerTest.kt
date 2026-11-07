package bd.arena.prototype

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class OnlineMatchSchedulerTest {
    @get:Rule val ui=createAndroidComposeRule<AdminActivity>()
    private val zone=ZoneId.of("Asia/Dhaka")
    private val firstDay=LocalDate.now(zone).plusDays(1)
    private fun midnight(day:LocalDate)=day.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun <T> await(task:com.google.android.gms.tasks.Task<T>)=Tasks.await(task,30,TimeUnit.SECONDS)
    private fun session():AdminSession {
        check(BuildConfig.FIREBASE_USE_EMULATOR&&BuildConfig.LOCAL_OPERATIONS)
        ui.runOnUiThread{ui.activity.setContent{};ui.activity.stopService(Intent(ui.activity,MatchSchedulerService::class.java))}
        val session=AdminSession(ui.activity)
        await(session.auth.signInWithEmailAndPassword("admin@arena.test","ArenaLocal123!"))
        ui.waitUntil(20_000){session.allowed}
        return session
    }
    private fun create(session:AdminSession):String {
        val id=session.db.collection("matchSchedules").document().id
        val details=mapOf<String,Any>("title" to "Sample daily batch $id","description" to "Local sample only","image" to "solo","mode" to "solo","map" to "Bermuda","capacity" to 48L,"entryPaisa" to 0L,"prizePaisa" to 10000L,"perKillPaisa" to 0L,"rules" to "Sample","cancellationTerms" to "Sample","categoryId" to "","artworkId" to "")
        val done=CompletableFuture<Exception?>()
        ui.runOnUiThread{MatchSchedules(session).create(id,details,listOf(5000L,2500L),MatchScheduleTiming(firstDay.toString(),firstDay.plusDays(1).toString(),540,1140,20),ArtworkSelection()){done.complete(it)}}
        assertNull(done.get(20,TimeUnit.SECONDS));return id
    }
    private fun progress(session:AdminSession,id:String)=await(session.db.document("matchScheduleProgress/$id").get(Source.SERVER))
    private fun advance(session:AdminSession,id:String,now:Long)=runBlocking{withTimeout(90_000){AdminMatchScheduler(session).advance(id,now)}}
    private fun state(session:AdminSession,id:String,next:String) {
        val record=await(session.db.document("matchSchedules/$id").get(Source.SERVER))
        val done=CompletableFuture<Exception?>()
        ui.runOnUiThread{MatchSchedules(session).changeState(record,next){done.complete(it)}}
        assertNull(done.get(20,TimeUnit.SECONDS))
    }
    private fun cleanup(session:AdminSession,id:String) {
        await(session.db.enableNetwork())
        if(await(session.db.document("matchSchedules/$id").get(Source.SERVER)).getString("state") in listOf("active","paused"))state(session,id,"stopped")
        session.auth.signOut();session.close()
        ui.activity.stopService(Intent(ui.activity,MatchSchedulerService::class.java))
    }
    @Test fun creates31MatchesTogetherThenWaitsForNextBangladeshDay() {
        val session=session();val id=create(session)
        try {
            advance(session,id,System.currentTimeMillis())
            assertEquals(0L,progress(session,id).getLong("sequence"))
            assertEquals(midnight(firstDay),progress(session,id).getTimestamp("nextAt")!!.toDate().time)
            advance(session,id,midnight(firstDay))
            assertEquals(31L,progress(session,id).getLong("sequence"))
            assertEquals("queued",progress(session,id).getString("phase"))
            val runs=await(session.db.collection("matchSchedules/$id/runs").orderBy("sequence").limit(100).get(Source.SERVER)).documents
            assertEquals(31,runs.size)
            runs.forEachIndexed{index,run->
                val match=await(session.db.document("matches/${run.getString("match")}").get(Source.SERVER))
                val expected=midnight(firstDay)+(540+index*20)*60_000L
                assertEquals(expected,match.getTimestamp("startsAt")!!.toDate().time)
                assertEquals(expected,run.getTimestamp("startsAt")!!.toDate().time)
                assertEquals("open",match.getString("status"));assertEquals(0L,match.getLong("joined"))
                assertEquals(2,await(match.reference.collection("prizes").limit(100).get(Source.SERVER)).size())
            }
            // No winners or match completion are needed; repeating the same day is a no-op.
            advance(session,id,midnight(firstDay)+12*60*60_000L)
            assertEquals(31L,progress(session,id).getLong("sequence"))
            assertEquals(midnight(firstDay.plusDays(1)),progress(session,id).getTimestamp("nextAt")!!.toDate().time)
            advance(session,id,midnight(firstDay.plusDays(1)))
            assertEquals(62L,progress(session,id).getLong("sequence"))
            assertEquals("completed",await(session.db.document("matchSchedules/$id").get(Source.SERVER)).getString("state"))
        }finally{cleanup(session,id)}
    }
    @Test fun competingRunnersAndPauseResumeDoNotDuplicateDailyMatches() {
        val session=session();val id=create(session)
        try {
            state(session,id,"paused");advance(session,id,midnight(firstDay))
            assertFalse(progress(session,id).exists())
            state(session,id,"active")
            runBlocking{withTimeout(90_000){listOf(async{AdminMatchScheduler(session).advance(id,midnight(firstDay))},async{AdminMatchScheduler(session).advance(id,midnight(firstDay))}).awaitAll()}}
            assertEquals(31L,progress(session,id).getLong("sequence"))
            assertEquals(31,await(session.db.collection("matchSchedules/$id/runs").limit(100).get(Source.SERVER)).size())
            state(session,id,"stopped");advance(session,id,midnight(firstDay.plusDays(1)))
            assertEquals(31L,progress(session,id).getLong("sequence"))
        }finally{cleanup(session,id)}
    }
}

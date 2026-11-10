package bd.arena.prototype

import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun announcementAfterEnrollment(updated:Timestamp,enrolled:Timestamp)=updated>enrolled

/** No hosted sender: query only published revisions since the last completed check. */
internal object AnnouncementPolling {
    private const val jobId=906
    private val checking=Mutex()
    private fun preferences(context:Context)=context.getSharedPreferences("announcement-polling",0)
    private fun timestamp(prefs:SharedPreferences,key:String)=Timestamp(prefs.getLong(key,0),prefs.getInt("${key}Nanos",0))
    private fun save(prefs:SharedPreferences,key:String,value:Timestamp) {
        prefs.edit().putLong(key,value.seconds).putInt("${key}Nanos",value.nanoseconds).apply()
    }
    fun schedule(context:Context):Boolean {
        if(BuildConfig.NOTICE_BACKEND!="live")return false
        val manager=context.getSystemService(NotificationManager::class.java)
        val scheduler=context.getSystemService(JobScheduler::class.java)
        if(!manager.areNotificationsEnabled()||manager.getNotificationChannel(announcementChannel)?.importance==NotificationManager.IMPORTANCE_NONE) {
            scheduler.cancel(jobId);return false
        }
        if(scheduler.getPendingJob(jobId)==null) scheduler.schedule(JobInfo.Builder(jobId,ComponentName(context,AnnouncementCheckService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60_000L).setPersisted(true).build())
        return true
    }
    suspend fun check(context:Context) {
        if(BuildConfig.NOTICE_BACKEND!="live"||!checking.tryLock())return
        try {withTimeout(45_000) {
            val prefs=preferences(context)
            val query=Notices.database(context).collection("notices").whereEqualTo("published",true).orderBy("updatedAt",Query.Direction.DESCENDING)
            if(!prefs.getBoolean("enrolled",false)) {
                // Use a server record, not the phone clock. Do not alert for historical notices.
                val newest=query.limit(1).get(Source.SERVER).awaitAnnouncement().documents.firstOrNull()?.getTimestamp("updatedAt")?:Timestamp(0,0)
                save(prefs,"baseline",newest);save(prefs,"cursor",newest)
                prefs.edit().putBoolean("enrolled",true).apply()
                return@withTimeout
            }
            val baseline=timestamp(prefs,"baseline")
            val since=timestamp(prefs,"cursor")
            val changes=query.whereGreaterThanOrEqualTo("updatedAt",since)
            var cursor:DocumentSnapshot?=null
            var newest=since
            do {
                var pageQuery:Query=changes.limit(50)
                cursor?.let{pageQuery=pageQuery.startAfter(it)}
                val page=pageQuery.get(Source.SERVER).awaitAnnouncement().documents
                for(doc in page) {
                    val updated=doc.getTimestamp("updatedAt")?:continue
                    newest=maxOf(newest,updated)
                    if(announcementAfterEnrollment(updated,baseline))AnnouncementAlerts.show(context,mapOf(
                        "type" to "announcement","noticeId" to doc.id,"revision" to (doc.getLong("revision")?:0).toString(),
                        "title" to doc.getString("title").orEmpty(),"body" to doc.getString("body").orEmpty()))
                    yield()
                }
                cursor=page.lastOrNull()
            }while(page.size==50)
            // Failed or interrupted pages retain the old cursor. Displayed revisions deduplicate retries.
            save(prefs,"cursor",newest)
        }} finally {checking.unlock()}
    }
    fun observe(context:Context,feed:NoticeFeed) {
        if(BuildConfig.NOTICE_BACKEND!="live"||feed.loading||feed.cached||feed.error)return
        val prefs=preferences(context)
        if(!prefs.getBoolean("enrolled",false))return
        val baseline=timestamp(prefs,"baseline").toDate().time
        feed.notices.filter{it.updatedAt>baseline}.forEach{notice->AnnouncementAlerts.show(context,mapOf(
            "type" to "announcement","noticeId" to notice.id,"revision" to notice.revision.toString(),"title" to notice.title,"body" to notice.body))}
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitAnnouncement():T=suspendCancellableCoroutine { continuation->
    addOnSuccessListener{if(continuation.isActive)continuation.resume(it)}
    addOnFailureListener{if(continuation.isActive)continuation.resumeWithException(it)}
}

class AnnouncementCheckService:JobService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private var check:Job?=null
    override fun onStartJob(params:JobParameters):Boolean {
        check=scope.launch {
            try {AnnouncementPolling.check(this@AnnouncementCheckService)}
            catch(_:TimeoutCancellationException){Log.w("Announcements","Background check timed out; will retry on the next check.")}
            catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){Log.w("Announcements","Background check unavailable; will retry on the next check.")}
            jobFinished(params,false)
        }
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean {check?.cancel();return true}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}

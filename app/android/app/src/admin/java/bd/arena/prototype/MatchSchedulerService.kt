package bd.arena.prototype

import android.app.*
import android.content.Intent
import android.net.*
import android.os.IBinder
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

internal object MatchSchedulerStatus {
    var message by mutableStateOf("Scheduling will resume when this admin app is online.")
    var running by mutableStateOf(false)
}

/** Started from the visible admin app, then kept visible by its ongoing notification. */
class MatchSchedulerService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var session:AdminSession
    private lateinit var network:ConnectivityManager
    private var connected by mutableStateOf(false)
    private val callback=object:ConnectivityManager.NetworkCallback() {
        override fun onAvailable(value:Network){scope.launch{connected=true}}
        override fun onLost(value:Network){scope.launch{connected=network.activeNetwork!=null}}
    }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("match-scheduling","Match scheduling",NotificationManager.IMPORTANCE_LOW))
        status("Connecting to run match schedules.")
        session=AdminSession(this)
        network=getSystemService(ConnectivityManager::class.java)
        connected=network.activeNetwork!=null
        network.registerDefaultNetworkCallback(callback)
        scope.launch {
            snapshotFlow{Triple(session.restoring,session.uid,session.allowed&&connected)}.collectLatest{(restoring,uid,online)->
                if(restoring)return@collectLatest
                if(uid==null){stopSelf();return@collectLatest}
                if(!online){status("Scheduling paused · reconnect with staff access to resume.");return@collectLatest}
                var schedules=BoundedFeedState()
                val feed=BoundedFeed(session.db.collection("matchSchedules").whereEqualTo("state","active"),automatic=true){schedules=it}
                feed.start()
                val runner=AdminMatchScheduler(session)
                try {
                    while(isActive) {
                        if(schedules.cached||schedules.loading||schedules.error)status("Waiting to confirm schedules online.")
                        else {
                            status(if(schedules.rows.isEmpty())"No active match schedules." else "Scheduling is running on this admin phone.",true)
                            for(record in schedules.rows) {
                                ensureActive()
                                try {
                                    runner.advance(record.id)
                                }catch(cancelled:CancellationException){throw cancelled}
                                catch(failure:Exception){status("Scheduling needs attention. Reconnect or review the schedule.");android.util.Log.w("MatchScheduler","Schedule ${record.id} could not advance",failure)}
                            }
                        }
                        delay(15_000)
                    }
                }finally{feed.close()}
            }
        }
    }
    private fun status(message:String,running:Boolean=false) {
        MatchSchedulerStatus.message=message;MatchSchedulerStatus.running=running
        val open=PendingIntent.getActivity(this,0,Intent(this,AdminActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        startForeground(905,Notification.Builder(this,"match-scheduling").setSmallIcon(R.drawable.ic_admin_notification)
            .setContentTitle("Match scheduling").setContentText(adminText(message)).setContentIntent(open).setOngoing(true).build())
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int)=START_STICKY
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onDestroy() {
        scope.cancel();network.unregisterNetworkCallback(callback);session.close()
        MatchSchedulerStatus.message="Scheduling will resume when this admin app is online.";MatchSchedulerStatus.running=false
        super.onDestroy()
    }
}

@Composable
internal fun StartAdminMatchScheduler(session:AdminSession) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var resumed by remember{mutableIntStateOf(0)}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)resumed++}
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer)}
    }
    DisposableEffect(session.uid,resumed) {
        // Also restart automatically when a new schedule is saved after the service stopped.
        val listener=session.db.collection("matchSchedules").whereEqualTo("state","active").limit(1).addSnapshotListener(MetadataChanges.INCLUDE){snapshot,failure->
            if(failure==null&&snapshot!=null&&!snapshot.metadata.isFromCache&&!snapshot.isEmpty&&session.allowed) {
                try{context.startForegroundService(Intent(context,MatchSchedulerService::class.java))}
                catch(_:IllegalStateException){MatchSchedulerStatus.message="Open the admin app to resume scheduling."}
            }
        }
        onDispose{listener.remove()}
    }
}

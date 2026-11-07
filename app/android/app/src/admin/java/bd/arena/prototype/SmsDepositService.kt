package bd.arena.prototype

import android.app.*
import android.content.*
import android.os.IBinder
import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.runtime.*
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

private data class ReceiptAccess(val uid:String?,val restoring:Boolean,val owner:Boolean,val checking:Boolean,val payment:String,val binding:String,val sources:Pair<Boolean,Boolean>,val restart:Int,val connected:Boolean)

/** Visible owner-enabled watcher; every credit still requires the existing server transaction. */
class SmsDepositService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var session:AdminSession
    private lateinit var preferences:SharedPreferences
    private var paymentListener:com.google.firebase.firestore.ListenerRegistration?=null
    private var paymentReceiver by mutableStateOf("")
    private var sources by mutableStateOf(false to false)
    private var restart by mutableIntStateOf(0)
    private lateinit var network:ConnectivityManager
    private var connected by mutableStateOf(false)
    private val connection=object:ConnectivityManager.NetworkCallback() {
        override fun onAvailable(value:Network){scope.launch{connected=true;restart++}}
        override fun onLost(value:Network){scope.launch{connected=network.activeNetwork!=null;restart++}}
    }
    private val changed=SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key?.startsWith("sms-deposit-receiver-")==true)restart++}
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("deposit-verification","Deposit verification",NotificationManager.IMPORTANCE_LOW))
        showStatus("Connecting to verify owner access")
        session=AdminSession(this)
        preferences=getSharedPreferences("settings",0)
        preferences.registerOnSharedPreferenceChangeListener(changed)
        network=getSystemService(ConnectivityManager::class.java)
        connected=network.activeNetwork!=null
        network.registerDefaultNetworkCallback(connection)
        paymentListener=session.db.document("paymentInstructions/bkash").addSnapshotListener(MetadataChanges.INCLUDE){record,failure->
            paymentReceiver=if(failure==null&&record!=null&&!record.metadata.isFromCache&&!record.metadata.hasPendingWrites()&&record.getBoolean("enabled")==true)record.getString("number").orEmpty()else ""
        }
        scope.launch {
            while(isActive){sources=hasSmsAccess(this@SmsDepositService) to hasBkashNotificationAccess(this@SmsDepositService);delay(5_000)}
        }
        scope.launch {
            snapshotFlow{ReceiptAccess(session.uid,session.restoring,session.owner,session.checking,paymentReceiver,
                preferences.getString("sms-deposit-receiver-${session.uid}","").orEmpty(),sources,restart,connected)}.collectLatest{access->
                if(access.restoring){showStatus("Restoring the owner session");return@collectLatest}
                if(access.uid==null||access.binding.isEmpty()){stopSelf();return@collectLatest}
                if(!access.connected){showStatus("Verification paused · reconnect to check pending deposits automatically");return@collectLatest}
                if(!access.owner){showStatus(if(access.checking)"Waiting for server-confirmed owner access" else "Verification paused · owner access could not be verified");return@collectLatest}
                if(access.payment!=access.binding){showStatus("Verification paused · reconnect and confirm the enabled receiving number");return@collectLatest}
                if(!access.sources.first&&!access.sources.second){showStatus("Verification paused · allow SMS or bKash notification access");return@collectLatest}
                while(isActive) {
                    showStatus("Checking receipts and pending deposits")
                    try {
                        watchDeposits(applicationContext,session,access.binding,onStatus={status,report->showStatus(status,report)}) {
                            preferences.edit().putLong("sms-last-credit",System.currentTimeMillis()).apply()
                        }
                    }catch(cancelled:CancellationException){throw cancelled}
                    catch(failure:Exception){showStatus("Verification will retry automatically · checking connection",workflowFailure(failure))}
                    delay(5_000)
                }
            }
        }
    }
    private fun showStatus(text:String,report:String?=null) {
        if(this::session.isInitialized)receiptStatus(this,session.uid?:session.auth.currentUser?.uid,text,report)
        val open=PendingIntent.getActivity(this,0,Intent(this,AdminActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        startForeground(904,Notification.Builder(this,"deposit-verification").setSmallIcon(R.drawable.ic_admin_notification)
            .setContentTitle("Deposit verification").setContentText(text).setContentIntent(open).setOngoing(true).build())
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {sources=hasSmsAccess(this) to hasBkashNotificationAccess(this);restart++;return START_STICKY}
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onDestroy() {
        scope.cancel();paymentListener?.remove()
        network.unregisterNetworkCallback(connection)
        preferences.unregisterOnSharedPreferenceChangeListener(changed)
        receiptStatus(this,session.uid,"Background verification stopped · reopen Payment settings to restart")
        session.close();super.onDestroy()
    }
}

class SmsDepositBootReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!=Intent.ACTION_BOOT_COMPLETED)return
        if(context.getSharedPreferences("settings",0).all.keys.none{it.startsWith("sms-deposit-receiver-")})return
        try{context.startForegroundService(Intent(context,SmsDepositService::class.java))}
        catch(_:IllegalStateException){/* Android may require opening Payment settings after a restricted startup. */}
    }
}

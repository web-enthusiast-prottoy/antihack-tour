package bd.arena.prototype

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONArray
import org.json.JSONObject

internal const val bkashAppPackage="com.bKash.customerapp"
internal fun hasBkashNotificationAccess(context:Context):Boolean =
    Settings.Secure.getString(context.contentResolver,"enabled_notification_listeners").orEmpty().split(':')
        .any{ComponentName.unflattenFromString(it)==ComponentName(context,BkashNotificationService::class.java)}

internal fun parseBkashNotification(packageName:String,texts:List<String>):BkashSmsReceipt? {
    if(packageName!=bkashAppPackage)return null
    return texts.mapNotNull{parseBkashSms("bKash",it)}.distinct().singleOrNull()
}

internal class NotificationReceiptStore(context:Context,uid:String,receiver:String) {
    private val preferences=context.getSharedPreferences("bkash-receipts",Context.MODE_PRIVATE)
    private val key="$uid:$receiver"
    fun read(now:Long):List<BkashSmsReceipt> {
        val saved=runCatching{JSONArray(preferences.getString(key,"[]"))}.getOrElse{JSONArray()}
        return (0 until saved.length()).mapNotNull{index->runCatching {
            val row=saved.getJSONObject(index)
            BkashSmsReceipt(row.getLong("amount"),row.getString("sender"),row.getString("reference"),row.getLong("paidAt"))
        }.getOrNull()}.filter{it.paidAtMillis in (now-receiptWindowMillis)..(now+60_000)}
    }
    fun add(receipt:BkashSmsReceipt,now:Long) {
        val rows=(read(now)+receipt).distinct().sortedByDescending{it.paidAtMillis}.take(500)
        val saved=JSONArray()
        rows.forEach{saved.put(JSONObject().put("amount",it.amountPaisa).put("sender",it.sender).put("reference",it.reference).put("paidAt",it.paidAtMillis))}
        preferences.edit().putString(key,saved.toString()).apply()
    }
}

/** Android supplies the posting package; other apps' notifications are discarded immediately. */
class BkashNotificationService:NotificationListenerService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var session:AdminSession
    private var connected=false
    private val changed=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,key->
        if(connected&&session.owner&&key?.startsWith("sms-deposit-receiver-")==true)runCatching{activeNotifications?.forEach(::capture)}
    }
    override fun onCreate() {super.onCreate();session=AdminSession(this);getSharedPreferences("settings",0).registerOnSharedPreferenceChangeListener(changed)}
    override fun onListenerConnected() {
        connected=true
        scope.launch {
            snapshotFlow{session.owner to session.uid}.collectLatest{(owner,uid)->
                if(owner&&uid!=null)runCatching{activeNotifications?.forEach(::capture)}
            }
        }
    }
    override fun onNotificationPosted(notification:StatusBarNotification) {capture(notification)}
    private fun capture(posted:StatusBarNotification) {
        if(posted.packageName!=bkashAppPackage||!connected||!session.owner)return
        val uid=session.uid?:return
        val preferences=getSharedPreferences("settings",0)
        val receiver=preferences.getString("sms-deposit-receiver-$uid","").orEmpty()
        if(!validDepositSender(receiver))return
        val extras=posted.notification.extras
        val texts=listOfNotNull(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())+
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().map{it.toString()}
        val now=System.currentTimeMillis()
        val receipt=parseBkashNotification(posted.packageName,texts)
        preferences.edit().putLong("receipt-notification-seen-$uid",now)
            .putString("receipt-notification-result-$uid",if(receipt?.sender==null)"bKash notification received, but it has no supported complete receipt. Keep SMS access enabled or review this payment manually." else "Complete bKash notification receipt saved.").apply()
        if(receipt?.sender!=null&&receipt.paidAtMillis in (now-receiptWindowMillis)..(now+60_000)) {
            NotificationReceiptStore(this,uid,receiver).add(receipt,now)
            preferences.edit().putLong("receipt-notification-changed",now).apply()
        }
    }
    override fun onListenerDisconnected() {connected=false;scope.coroutineContext.cancelChildren();if(hasBkashNotificationAccess(this))requestRebind(ComponentName(this,BkashNotificationService::class.java))}
    override fun onDestroy() {scope.cancel();getSharedPreferences("settings",0).unregisterOnSharedPreferenceChangeListener(changed);session.close();super.onDestroy()}
}

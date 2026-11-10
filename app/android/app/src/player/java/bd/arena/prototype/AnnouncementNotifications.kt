package bd.arena.prototype

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

internal const val announcementChannel="announcements"
internal const val announcementIntent="openAnnouncements"

class PlayerApplication:Application() {
    override fun onCreate() {
        super.onCreate()
        if(BuildConfig.NOTICE_BACKEND!="live")return
        if(FirebaseApp.getApps(this).none{it.name==FirebaseApp.DEFAULT_APP_NAME}) {
            FirebaseApp.initializeApp(this,FirebaseOptions.Builder()
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID).setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY).setGcmSenderId(BuildConfig.FIREBASE_MESSAGING_SENDER_ID).build())
        }
        val bangla=getSharedPreferences("settings",0).getBoolean("bangla",false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(
            announcementChannel,if(bangla)"ঘোষণা" else "Announcements",NotificationManager.IMPORTANCE_DEFAULT))
        FirebaseMessaging.getInstance().isAutoInitEnabled=true
        subscribeToAnnouncements()
    }
}

internal fun subscribeToAnnouncements() {
    if(BuildConfig.NOTICE_BACKEND!="live")return
    FirebaseMessaging.getInstance().subscribeToTopic("announcements").addOnFailureListener {
        Log.w("Announcements","Could not subscribe to announcements; Firebase will retry.")
    }
}

internal fun announcementRevision(data:Map<String,String>,previous:Long):Long? {
    if(data["type"]!="announcement"||data["noticeId"].isNullOrBlank()||
        data["title"].isNullOrBlank()||data["body"].isNullOrBlank())return null
    return data["revision"]?.toLongOrNull()?.takeIf{it>0&&it>previous}
}

class AnnouncementMessagingService:FirebaseMessagingService() {
    override fun onNewToken(token:String) {subscribeToAnnouncements()}
    override fun onMessageReceived(message:RemoteMessage) {
        AnnouncementAlerts.show(this,message.data)
    }
}

internal object AnnouncementAlerts {
    private val deliveryLock=Any()
    fun show(context:Context,data:Map<String,String>) {
        if(BuildConfig.NOTICE_BACKEND!="live")return
        val manager=context.getSystemService(NotificationManager::class.java)
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        if(!manager.areNotificationsEnabled()||manager.getNotificationChannel(announcementChannel)?.importance==NotificationManager.IMPORTANCE_NONE)return
        val id=data["noticeId"]?:return
        val preferences=context.getSharedPreferences("announcement-delivery",0)
        synchronized(deliveryLock) {
            val revision=announcementRevision(data,preferences.getLong(id,0))?:return
            val intent=Intent(context,MainActivity::class.java).putExtra(announcementIntent,true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(id,0,Notification.Builder(context,announcementChannel)
                .setSmallIcon(R.drawable.ic_bell).setContentTitle(data["title"]).setContentText(data["body"])
                .setStyle(Notification.BigTextStyle().bigText(data["body"]))
                .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(false).build())
            preferences.edit().putLong(id,revision).apply()
        }
    }
}

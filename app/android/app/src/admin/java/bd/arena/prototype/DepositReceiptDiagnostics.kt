package bd.arena.prototype

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

/** Diagnostics contain counts/reasons only: never SMS bodies, account numbers or transaction IDs. */
internal fun receiptStatus(context:Context,uid:String?,status:String,report:String?=null) {
    if(uid==null)return
    context.getSharedPreferences("settings",0).edit().putString("receipt-status-$uid",status)
        .putLong("receipt-heartbeat-$uid",System.currentTimeMillis()).apply {
            if(report!=null)putString("receipt-report-$uid",report)
        }.apply()
}

@Composable
internal fun ReceiptDiagnostics(session:AdminSession,bangla:Boolean) {
    val context=LocalContext.current
    val preferences=remember{context.getSharedPreferences("settings",0)}
    var revision by remember{mutableIntStateOf(0)}
    DisposableEffect(preferences) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key?.startsWith("receipt-")==true)revision++}
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose{preferences.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val uid=session.uid
    var details by rememberSaveable(uid){mutableStateOf(false)}
    val status=remember(uid,revision){preferences.getString("receipt-status-$uid",null)}
    val report=remember(uid,revision){preferences.getString("receipt-report-$uid","").orEmpty()}
    val heartbeat=remember(uid,revision){preferences.getLong("receipt-heartbeat-$uid",0)}
    val notification=remember(uid,revision){preferences.getString("receipt-notification-result-$uid","").orEmpty()}
    val clipboard=LocalClipboardManager.current
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(if(bangla)"যাচাইয়ের অবস্থা" else "Verification status",style=MaterialTheme.typography.titleMedium)
        Text(status?:if(bangla)"ব্যাকগ্রাউন্ড যাচাই এখনো শুরু হয়নি।" else "Background verification has not started.",Modifier.semantics{liveRegion=LiveRegionMode.Polite})
        if(heartbeat>0)Text((if(bangla)"সর্বশেষ আপডেট: " else "Last update: ")+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.MEDIUM).format(Date(heartbeat)),style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={details=!details}){Text(if(bangla)if(details)"বিস্তারিত লুকান" else "যাচাইয়ের বিস্তারিত দেখুন" else if(details)"Hide verification details" else "View verification details")}
        if(details&&report.isNotBlank())Text(report,style=MaterialTheme.typography.bodySmall)
        if(details&&notification.isNotBlank())Text(notification,style=MaterialTheme.typography.bodySmall)
        Text(if(bangla)"শেষ আপডেট পুরোনো হলে যাচাই পুনরায় চালু করুন। টাকা জমা না হলে নিচের রিপোর্টটি কপি করে দিন।" else "If the last update is old, restart verification. If a deposit stays pending, copy the report below.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={clipboard.setText(AnnotatedString("Antihack Admin ${BuildConfig.VERSION_NAME}\n${status.orEmpty()}\nLast update: $heartbeat\nSMS access: ${hasSmsAccess(context)}\nNotification access: ${hasBkashNotificationAccess(context)}\n$notification\n$report"))}) {
            Text(if(bangla)"যাচাই রিপোর্ট কপি করুন" else "Copy verification report")
        }
    }
}

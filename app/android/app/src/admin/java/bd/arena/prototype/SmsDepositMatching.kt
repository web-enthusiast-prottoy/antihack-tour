package bd.arena.prototype

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun rememberSmsDepositSettings(
    session: AdminSession,
    bangla: Boolean,
    onCredit: () -> Unit
): @Composable () -> Unit {
    if (!session.owner) return {}
    val context = LocalContext.current
    val uid = session.uid
    val preferences = remember { context.getSharedPreferences("settings", 0) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)permissionRevision++}
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer)}
    }
    val credit by rememberUpdatedState(onCredit)
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "sms-last-credit") credit()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val preference = "sms-deposit-receiver-$uid"
    var boundNumber by remember(uid) { mutableStateOf(preferences.getString(preference, "").orEmpty()) }
    var confirmLive by remember { mutableStateOf<String?>(null) }
    var requestedNumber by remember { mutableStateOf<String?>(null) }
    var denied by remember { mutableStateOf(false) }
    val payment = paymentNumber(session)
    val availableNumber = payment.number.takeIf {
        payment.enabled && !payment.loading && !payment.cached && !payment.error && validDepositSender(it)
    }
    val currentNumber by rememberUpdatedState(availableNumber)
    val permission = Manifest.permission.READ_SMS
    val smsGranted=remember(permissionRevision){hasSmsAccess(context)}
    val notificationGranted=remember(permissionRevision){hasBkashNotificationAccess(context)}
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        permissionRevision++
        val number = requestedNumber
        requestedNumber = null
        if (granted && number != null && number == currentNumber) {
            boundNumber = number
            preferences.edit().putString(preference, number).apply()
        }
    }

    if (availableNumber != null && boundNumber == availableNumber && (smsGranted||notificationGranted)) {
        LaunchedEffect(uid, boundNumber, availableNumber, permissionRevision) {
            try { context.startForegroundService(android.content.Intent(context, SmsDepositService::class.java)) }
            catch (_: IllegalStateException) { denied = true }
        }
    }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRevision++ }
    return {
        val enabled = boundNumber.isNotEmpty() && boundNumber == availableNumber &&
            (smsGranted||notificationGranted)
        OutlinedButton(enabled = enabled || availableNumber != null, onClick = {
            denied = false
            if (enabled) {
                boundNumber = ""
                preferences.edit().remove(preference).apply()
                context.stopService(android.content.Intent(context, SmsDepositService::class.java))
            } else if (BuildConfig.LOCAL_OPERATIONS) {
                val number=currentNumber
                if(number!=null&&(hasSmsAccess(context)||hasBkashNotificationAccess(context))){boundNumber=number;preferences.edit().putString(preference,number).apply()}
                else {requestedNumber = number;launcher.launch(permission)}
            } else {
                confirmLive = currentNumber
            }
        }) {
            Text(when {
                enabled && bangla -> "স্বয়ংক্রিয় যাচাই বন্ধ করুন"
                enabled -> "Turn off auto-approval"
                !BuildConfig.LOCAL_OPERATIONS && bangla -> "স্বয়ংক্রিয় অনুমোদন চালু করুন"
                !BuildConfig.LOCAL_OPERATIONS -> "Enable auto-approval"
                bangla -> "নমুনা জমার রসিদ যাচাই চালু করুন"
                else -> "Enable sample receipt matching"
            })
        }
        if (denied) Text(if (bangla) "এসএমএস অনুমতি পাওয়া যায়নি। জমা নিজে যাচাই করুন।"
            else "SMS access is unavailable. Deposits can be reviewed manually.")
        else if (enabled && !BuildConfig.LOCAL_OPERATIONS) Text(if (bangla) "সম্পূর্ণ বিকাশ রসিদের তথ্য মিললে লাইভ ওয়ালেটে টাকা যোগ হবে।"
            else "Exact complete bKash receipt matches credit live wallets.")
        else if (enabled) Text(if (bangla) "সম্পূর্ণ বিকাশ রসিদের তথ্য মিললে নমুনা জমা অনুমোদিত হবে।"
            else "Exact complete bKash receipt matches approve sample deposits.")
        else Text(if (bangla) "সাম্প্রতিক রসিদ পড়তে এসএমএস বা বিকাশ বিজ্ঞপ্তির অ্যাক্সেস দিন।"
            else "Reads receipts from 25274 or bKash SMS, with optional bKash app notifications.")
        Text(if(bangla)"এসএমএস অ্যাক্সেস: "+if(smsGranted)"চালু" else "বন্ধ" else "SMS access: "+if(smsGranted)"Allowed" else "Not allowed")
        Text(if(bangla)"বিকাশ বিজ্ঞপ্তির অ্যাক্সেস: "+if(notificationGranted)"চালু" else "বন্ধ" else "bKash notification access: "+if(notificationGranted)"Allowed" else "Not allowed")
        TextButton(onClick={context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))}) {
            Text(if(bangla)"বিকাশ বিজ্ঞপ্তির অ্যাক্সেস খুলুন" else "Open bKash notification access")
        }
        Text(if(bangla)"এই ফোনের বিকাশ অ্যাপে গ্রহণের নম্বরটি ব্যবহার করুন। শুধু সম্পূর্ণ বিকাশ রসিদ প্রক্রিয়া করা হয়। ছোট বা আড়াল করা বিজ্ঞপ্তি নিজে যাচাই করতে হবে।" else "Use the receiving account in the bKash app on this phone. Only complete bKash receipts are processed; shortened or hidden notifications need manual review.")
        ReceiptDiagnostics(session,bangla)
        if (enabled) {
            TextButton(onClick = {
                try {
                    context.stopService(android.content.Intent(context, SmsDepositService::class.java))
                    context.startForegroundService(android.content.Intent(context, SmsDepositService::class.java))
                } catch (_: IllegalStateException) { denied = true }
            }) { Text(if (bangla) "ব্যাকগ্রাউন্ড যাচাই পুনরায় চালু করুন" else "Restart background verification") }
            if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                TextButton(onClick = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text(if (bangla) "ব্যাকগ্রাউন্ড যাচাইয়ের বিজ্ঞপ্তি চালু করুন" else "Allow background verification notifications")
                }
            }
            TextButton(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) {
                Text(if (bangla) "ব্যাকগ্রাউন্ড যাচাইয়ের ব্যাটারি সেটিংস" else "Battery settings for background verification")
            }
            Text(if (bangla) "এই ফোন অনলাইনে ও চালু রাখুন। অ্যাপ বন্ধ করে দিলে বা ফোন বন্ধ থাকলে অনুরোধ অপেক্ষায় থাকবে।"
                else "Keep this receiving phone online and powered on. Force-stopping the app or turning off the phone pauses verification; requests remain pending.")
        }
        confirmLive?.let { confirmedNumber -> AlertDialog(
            onDismissRequest = { confirmLive = null },
            title = { Text(if (bangla) "স্বয়ংক্রিয় অনুমোদন চালু করবেন?" else "Enable live receipt auto-approval?") },
            text = {
                Text(if (bangla) "এই ফোনের এসএমএস বা বিকাশ অ্যাপের সম্পূর্ণ রসিদ মিললে লাইভ ওয়ালেটে টাকা যোগ হবে। গ্রহণের নম্বর: $confirmedNumber।"
                    else "Matching SMS or complete bKash app notification receipts on this phone will credit live wallets for $confirmedNumber. Use the phone that receives payments for this bKash number.")
            },
            confirmButton = {
                TextButton(enabled = currentNumber == confirmedNumber, onClick = {
                    confirmLive = null
                    if(hasSmsAccess(context)||hasBkashNotificationAccess(context)) {boundNumber=confirmedNumber;preferences.edit().putString(preference,confirmedNumber).apply()}
                    else {requestedNumber = confirmedNumber;launcher.launch(permission)}
                }) { Text(if (bangla) "চালু করুন" else "Enable") }
            },
            dismissButton = { TextButton(onClick = { confirmLive = null }) { Text(adminText("Cancel")) } }
        ) }
    }
}

internal fun hasSmsAccess(context:Context)=context.checkSelfPermission(Manifest.permission.READ_SMS)==PackageManager.PERMISSION_GRANTED

internal data class ReceiptScan(val receipts:List<BkashSmsReceipt>,val smsMessages:Int=0,val smsReceipts:Int=0,val notifications:Int=0,val error:String="")

internal fun recentReceipts(context:Context,session:AdminSession,receiver:String,now:Long):ReceiptScan {
    val recent=now-receiptWindowMillis
    val sms=mutableListOf<BkashSmsReceipt>()
    var messages=0
    var error=""
    if(hasSmsAccess(context))try {
        context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI,arrayOf(Telephony.Sms.ADDRESS,Telephony.Sms.BODY),
            "(${Telephony.Sms.ADDRESS} = ? OR ${Telephony.Sms.ADDRESS} = ? COLLATE NOCASE) AND ${Telephony.Sms.DATE} >= ?",
            arrayOf(bkashSmsAddress,"bKash",recent.toString()),null)?.use {rows->
            while(rows.moveToNext()) {
                messages++
                parseBkashSms(rows.getString(0).orEmpty(),rows.getString(1).orEmpty())?.takeIf{it.paidAtMillis in recent..(now+60_000)}?.let(sms::add)
            }
        }?:run{error="SMS inbox is unavailable."}
    }catch(_:SecurityException){error="SMS permission was removed. Allow SMS access and restart verification."}
    val notifications=if(hasBkashNotificationAccess(context)&&session.uid!=null)NotificationReceiptStore(context,session.uid!!,receiver).read(now)else emptyList()
    return ReceiptScan((sms+notifications).distinct(),messages,sms.size,notifications.size,error)
}

internal fun receiptMismatch(receiver:String,claimReceiver:String?,reference:String?,sender:String?,amount:Long?,createdAt:Long,receipts:Map<String,List<BkashSmsReceipt>>,now:Long):String? {
    if(receiver!=claimReceiver)return "Receiving number changed"
    val found=receipts[reference]?.distinct().orEmpty()
    if(found.isEmpty())return "No complete receipt for transaction ID"
    if(found.size!=1)return "Conflicting receipts for transaction ID"
    val receipt=found.single()
    if(receipt.sender==null||receipt.sender!=sender)return "Sending number does not match"
    if(receipt.amountPaisa!=amount)return "Amount does not match"
    if(!validSmsReviewTimes(createdAt,receipt.paidAtMillis,now))return "Receipt or request is outside the 7-day review window"
    return null
}

internal fun matchingSmsReceipt(receiver:String,claimReceiver:String?,reference:String?,sender:String?,amount:Long?,createdAt:Long,receipts:Map<String,List<BkashSmsReceipt>>,now:Long):BkashSmsReceipt? =
    if(receiptMismatch(receiver,claimReceiver,reference,sender,amount,createdAt,receipts,now)==null)receipts[reference]?.distinct()?.singleOrNull()else null

internal data class ReceiptCheckReport(val pending:Int,val scan:ReceiptScan,val credited:Int,val held:Map<String,Int>,val errors:List<String>) {
    fun summary()=buildString {
        append("Pending requests: $pending\nSMS messages checked: ${scan.smsMessages}\nComplete SMS receipts: ${scan.smsReceipts}\nComplete bKash notification receipts: ${scan.notifications}\nCredited: $credited")
        held.forEach{(reason,count)->append("\n$reason: $count")}
        if(scan.error.isNotBlank())append("\n${scan.error}")
        errors.distinct().forEach{append("\nApproval failed: $it")}
    }
}

internal suspend fun checkSmsClaims(session:AdminSession,receiver:String,pending:List<DocumentSnapshot>,scan:ReceiptScan,now:Long,operations:PlayerData,onCredit:()->Unit):ReceiptCheckReport {
    var processed=0
    val held=mutableMapOf<String,Int>()
    val errors=mutableListOf<String>()
    val receipts=scan.receipts.groupBy{it.reference}
    for(claim in pending) {
        if(!session.allowed||!session.owner)break
        val issue=receiptMismatch(receiver,claim.getString("receiver"),claim.getString("reference"),claim.getString("sender"),claim.getLong("amount"),claim.getTimestamp("createdAt")?.toDate()?.time?:0,receipts,now)
        if(issue!=null){held[issue]=(held[issue]?:0)+1;continue}
        val receipt=receipts.getValue(claim.getString("reference")!!).distinct().single()
        // Never choose an account when two pending claims are competing for the same receipt.
        if(pending.count{it.getString("receiver")==receiver&&it.getString("reference")==receipt.reference}>1){
            val reason="Duplicate pending claims need manual review";held[reason]=(held[reason]?:0)+1;continue
        }
        operations.operation("autoApproveDeposit",mapOf("id" to claim.id,"receiptConfirmed" to true,
            "verifiedAmount" to receipt.amountPaisa,"verifiedReference" to receipt.reference,"verifiedSender" to checkNotNull(receipt.sender),
            "verifiedReceiver" to receiver,"paidAtMillis" to receipt.paidAtMillis)) {processed++;onCredit()}
        val complete=withTimeoutOrNull(45_000){snapshotFlow{operations.busy}.first{!it}}
        if(complete==null){errors+="Approval is still waiting for a server response. Check the request status before retrying.";break}
        if(operations.error.isNotBlank())errors+=operations.error
    }
    return ReceiptCheckReport(pending.size,scan,processed,held,errors)
}

internal suspend fun watchDeposits(context:Context,session:AdminSession,receiver:String,onStatus:(String,String?)->Unit,onCredit:()->Unit) {
    val changed=Channel<Unit>(Channel.CONFLATED)
    val resolver=context.contentResolver
    val observer=object:ContentObserver(Handler(Looper.getMainLooper())){override fun onChange(selfChange:Boolean){changed.trySend(Unit)}}
    var observing=false
    if(hasSmsAccess(context))try{resolver.registerContentObserver(Telephony.Sms.CONTENT_URI,true,observer);observing=true}catch(_:SecurityException){onStatus("SMS access unavailable · allow SMS permission and restart",null)}
    val preferences=context.getSharedPreferences("settings",0)
    val notificationChanged=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key=="receipt-notification-changed")changed.trySend(Unit)}
    preferences.registerOnSharedPreferenceChangeListener(notificationChanged)
    var queue=BoundedFeedState()
    val feed=BoundedFeed(session.db.collection(depositCollection).whereEqualTo("status","pending"),automatic=true){queue=it;changed.trySend(Unit)}
    val operations=PlayerData(session)
    feed.start()
    try {
        while(session.allowed&&session.owner) {
            withTimeoutOrNull(30_000){changed.receive()}
            if(queue.error){onStatus("Cannot load pending deposits · retrying automatically",null);kotlinx.coroutines.delay(5_000);feed.retry();continue}
            if(queue.loading||queue.cached){onStatus("Waiting for current pending deposits from the server",null);continue}
            val now=System.currentTimeMillis()
            val scan=withContext(Dispatchers.IO){recentReceipts(context,session,receiver,now)}
            val report=checkSmsClaims(session,receiver,queue.rows,scan,now,operations,onCredit)
            check(!operations.busy){"Approval is waiting for a response; reconnecting to confirm pending requests."}
            onStatus(when {
                report.errors.isNotEmpty()->"Automatic approval failed · open Payment settings for the reason"
                scan.error.isNotBlank()->scan.error
                report.pending==0->"Watching payment receipts · no pending deposits"
                report.credited>0->"Matched deposits credited · watching for more"
                else->"Deposits are pending · open Payment settings for unmatched receipt details"
            },report.summary())
        }
    }finally{feed.close();if(observing)resolver.unregisterContentObserver(observer);preferences.unregisterOnSharedPreferenceChangeListener(notificationChanged);changed.close()}
}

@Composable
internal fun SmsDepositCheck(session:AdminSession,bangla:Boolean,onCredit:()->Unit) {
    if(!session.owner)return
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val payment=paymentNumber(session)
    val operations=remember(session.uid){PlayerData(session)}
    var checking by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf("")}
    val ready=(hasSmsAccess(context)||hasBkashNotificationAccess(context))&&payment.enabled&&!payment.loading&&!payment.cached&&!payment.error&&validDepositSender(payment.number)
    OutlinedButton(enabled=ready&&!checking,onClick={
        checking=true;status=""
        scope.launch {
            try {
                val pending=mutableListOf<DocumentSnapshot>()
                var cursor:String?=null
                do {
                    var query=session.db.collection(depositCollection).whereEqualTo("status","pending").orderBy(com.google.firebase.firestore.FieldPath.documentId()).limit(100)
                    cursor?.let{query=query.startAfter(it)}
                    val page=withContext(Dispatchers.IO){com.google.android.gms.tasks.Tasks.await(query.get(Source.SERVER),20,java.util.concurrent.TimeUnit.SECONDS)}
                    pending+=page.documents
                    cursor=page.documents.lastOrNull()?.id.takeIf{page.size()==100}
                }while(cursor!=null)
                val now=System.currentTimeMillis()
                val scan=withContext(Dispatchers.IO){recentReceipts(context,session,payment.number,now)}
                val report=checkSmsClaims(session,payment.number,pending,scan,now,operations,onCredit)
                status="${report.credited} matching requests processed.\n"+report.summary()
                receiptStatus(context,session.uid,"Manual receipt check completed",report.summary())
            }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
            catch(_:Exception){status="Could not check deposits. Check connection, owner access and receipt permissions, then retry.";receiptStatus(context,session.uid,status)}
            finally{checking=false}
        }
    }) {Text(if(checking){if(bangla)"যাচাই হচ্ছে…" else "Checking deposits…"}else if(bangla)"এসএমএস থেকে জমা যাচাই করুন" else "Check deposits now")}
    Text(if(bangla)"সম্পূর্ণ এসএমএস বা বিকাশ বিজ্ঞপ্তির সাথে মেলা গত ৭ দিনের অনুরোধ যাচাই করে।" else "Checks pending requests against complete SMS or bKash notification receipts from the last 7 days.")
    if(!ready)Text(if(bangla)"পেমেন্ট সেটিংসে গ্রহণের নম্বর ও এসএমএস বা বিজ্ঞপ্তির অ্যাক্সেস চালু করুন।" else "Enable the receiving number and SMS or notification access in Payment settings.")
    if(status.isNotBlank())Text(status)
}

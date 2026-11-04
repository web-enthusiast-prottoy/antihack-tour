package bd.arena.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
private fun WithdrawalError(message:String) {
    if(message.isNotBlank())Text(LocaleStrings.get(message),Modifier.semantics{liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)
}

@Composable
internal fun WithdrawalSettings(session:AdminSession,back:()->Unit) {
    BackHandler{back()}
    val operations=remember(session.uid){Withdrawals(session)}
    var reload by remember{mutableStateOf(0)}
    var policy by remember{mutableStateOf<DocumentSnapshot?>(null)}
    var minimum by rememberSaveable{mutableStateOf("")}
    var fee by rememberSaveable{mutableStateOf("0.00")}
    var enabled by rememberSaveable{mutableStateOf(false)}
    var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf("")}
    var saved by remember{mutableStateOf(false)}
    DisposableEffect(reload,session.uid) {
        var active=true;loading=true;policy=null;error=""
        session.db.document("withdrawalPolicy/bkash").get(Source.SERVER).addOnCompleteListener{task->if(active&&session.allowed){
            loading=false
            if(task.isSuccessful){policy=task.result;minimum=task.result.getLong("minimumPaisa")?.let{java.math.BigDecimal.valueOf(it,2).toPlainString()}.orEmpty();fee=java.math.BigDecimal.valueOf(task.result.getLong("feePaisa")?:0,2).toPlainString();enabled=task.result.getBoolean("enabled")==true}
            else error="Could not load withdrawal settings. Reconnect and reload."
        }}
        onDispose{active=false}
    }
    val minValue=runCatching{awardPaisa(minimum)}.getOrNull()
    val feeValue=runCatching{awardPaisa(fee)}.getOrNull()
    val valid=minValue!=null&&feeValue!=null&&minValue>0&&minValue<=maxPaisa-feeValue
    Column(Modifier.widthIn(max=640.dp).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        ScreenHeader(LocaleStrings.get("Withdrawal settings"),back=back)
        Text(LocaleStrings.get("Choose the minimum and fee before enabling bKash withdrawals."))
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedTextField(minimum,{minimum=it;saved=false},label={Text(LocaleStrings.get("Minimum withdrawal (taka)"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),isError=minimum.isNotBlank()&&(minValue==null||minValue==0L),enabled=!loading&&!operations.busy,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(fee,{fee=it;saved=false},label={Text(LocaleStrings.get("Withdrawal fee (taka)"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),isError=fee.isNotBlank()&&feeValue==null,enabled=!loading&&!operations.busy,modifier=Modifier.fillMaxWidth())
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text(LocaleStrings.get("Enable withdrawals"),Modifier.weight(1f));Switch(enabled,{enabled=it;saved=false},enabled=!loading&&!operations.busy,modifier=Modifier.semantics{contentDescription=LocaleStrings.get("Enable withdrawals")})}
        Text(LocaleStrings.get("Disabling stops new requests. Existing requests keep their reviewed fee and must still be resolved."),style=MaterialTheme.typography.bodySmall)
        WithdrawalError(error);WithdrawalError(operations.error)
        if(saved)Text(LocaleStrings.get("Withdrawal settings saved."),Modifier.semantics{liveRegion=LiveRegionMode.Polite})
        Button(onClick={operations.policy(minValue!!,feeValue!!,enabled,policy?.getLong("revision")?:0){saved=true;reload++}},enabled=policy!=null&&valid&&!loading&&!operations.busy,modifier=Modifier.fillMaxWidth()){Text(LocaleStrings.get(if(operations.busy)"Saving…" else "Save withdrawal settings"))}
        TextButton(onClick={reload++},enabled=!operations.busy){Text(LocaleStrings.get("Reload"))}
    }
}

@Composable
internal fun WithdrawalForm(session:AdminSession,busyChanged:(Boolean)->Unit,done:()->Unit) {
    val uid=session.uid?:return
    val operations=remember(uid){Withdrawals(session)}
    val context=androidx.compose.ui.platform.LocalContext.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val intent=remember(uid){context.getSharedPreferences("withdrawal-intent-$uid",0)}
    val id=rememberSaveable(uid){intent.getString("id",null)?:session.db.collection("withdrawals").document().id}
    var amount by rememberSaveable(uid){mutableStateOf(intent.getString("amount","").orEmpty())}
    var number by rememberSaveable(uid){mutableStateOf(intent.getString("destination","").orEmpty())}
    var reload by remember{mutableStateOf(0)}
    var policy by remember{mutableStateOf<DocumentSnapshot?>(null)}
    var available by remember{mutableStateOf(0L)}
    var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf("")}
    var review by remember{mutableStateOf(false)}
    var submitted by remember{mutableStateOf<DocumentSnapshot?>(null)}
    LaunchedEffect(operations.busy){busyChanged(operations.busy)}
    DisposableEffect(Unit){onDispose{busyChanged(false)}}
    DisposableEffect(uid,reload) {
        var active=true;loading=true;policy=null;error=""
        Tasks.whenAllSuccess<DocumentSnapshot>(session.db.document("withdrawalPolicy/bkash").get(Source.SERVER),session.db.document("wallets/$uid").get(Source.SERVER),session.db.document("withdrawals/$id").get(Source.SERVER)).addOnCompleteListener{task->if(active&&session.uid==uid){
            loading=false
            if(task.isSuccessful){policy=task.result[0];available=task.result[1].getLong("winnings")?:0;if(task.result[2].exists())submitted=task.result[2]}else error="Could not load withdrawal settings. Reconnect and reload."
        }}
        onDispose{active=false}
    }
    val value=runCatching{awardPaisa(amount)}.getOrNull()
    val fee=policy?.getLong("feePaisa")?:0
    val minimum=policy?.getLong("minimumPaisa")?:0
    val total=value?.let{runCatching{withdrawalTotal(it,fee)}.getOrNull()}
    val amountValid=total!=null&&minimum>0&&value>=minimum&&total<=available
    val numberValid=validDepositSender(number)
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(LocaleStrings.get("Withdraw winnings"),style=MaterialTheme.typography.headlineSmall)
        if(BuildConfig.LOCAL_OPERATIONS)Text(LocaleStrings.get("Local sample only. No real money is transferred."),style=MaterialTheme.typography.bodySmall)
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(submitted!=null){
            Text(LocaleStrings.get("Withdrawal request received."),Modifier.semantics{liveRegion=LiveRegionMode.Polite})
            Text(submitted!!.id,style=MaterialTheme.typography.bodySmall)
            Text(LocaleStrings.get(submitted!!.getString("status").orEmpty()))
            Text(LocaleStrings.get("Track its status in Withdrawal requests."))
            Button(onClick={intent.edit().clear().apply();done()},modifier=Modifier.fillMaxWidth()){Text(LocaleStrings.get("Done"))}
        }else {
            LabeledValue(LocaleStrings.get("Available winnings"),money(available))
            if(policy?.getBoolean("enabled")!=true&&!loading)Text(LocaleStrings.get("New withdrawals are not enabled. Existing requests are still tracked below."))
            if(minimum>0)LabeledValue(LocaleStrings.get("Minimum withdrawal"),money(minimum))
            OutlinedTextField(amount,{amount=it},label={Text(LocaleStrings.get("Withdrawal amount (taka)"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,isError=amount.isNotBlank()&&!amountValid,supportingText=if(amount.isNotBlank()&&!amountValid)({Text(LocaleStrings.get("Use up to two decimals, meet the minimum, and include the fee within your winnings."))})else null,enabled=!operations.busy,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(number,{number=it.trim()},label={Text(LocaleStrings.get("Payout bKash number"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={keyboard?.hide()}),singleLine=true,isError=number.isNotBlank()&&!numberValid,supportingText=if(number.isNotBlank()&&!numberValid)({Text(LocaleStrings.get("Enter an 11-digit bKash number."))})else null,enabled=!operations.busy,modifier=Modifier.fillMaxWidth())
            LabeledValue(LocaleStrings.get("Withdrawal fee"),money(fee))
            if(total!=null)LabeledValue(LocaleStrings.get("Total reserved"),money(total))
            WithdrawalError(error);WithdrawalError(operations.error)
            Button(onClick={review=true},enabled=!loading&&policy?.getBoolean("enabled")==true&&amountValid&&numberValid&&!operations.busy,modifier=Modifier.fillMaxWidth()){Text(LocaleStrings.get("Review withdrawal"))}
            TextButton(onClick={reload++},enabled=!operations.busy){Text(LocaleStrings.get("Reload"))}
        }
    }
    if(review)AlertDialog(onDismissRequest={if(!operations.busy)review=false},title={Text(LocaleStrings.get("Confirm withdrawal"))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text(number,style=MaterialTheme.typography.titleLarge)
        LabeledValue(LocaleStrings.get("Payout amount"),money(value?:0));LabeledValue(LocaleStrings.get("Withdrawal fee"),money(fee));LabeledValue(LocaleStrings.get("Total reserved"),money(total?:0))
        Text(LocaleStrings.get("Check the payout number. This total is held from your winnings while an admin reviews and sends the payout."))
        WithdrawalError(operations.error)
    }},confirmButton={Button(onClick={
        if(intent.edit().putString("id",id).putString("amount",amount).putString("destination",number).commit())operations.submit(id,value!!,number,policy!!){submitted=it;review=false}
    },enabled=!operations.busy){Text(LocaleStrings.get(if(operations.busy)"Please wait…" else "Confirm request"))}},dismissButton={TextButton(onClick={review=false},enabled=!operations.busy){Text(LocaleStrings.get("Back"))}})
}

@Composable
internal fun WithdrawalDetails(record:DocumentSnapshot,privateAccount:Boolean=false) {
    LabeledValue(LocaleStrings.get("Payout amount"),money(record.getLong("amountPaisa")?:0))
    val fee=record.getLong("feePaisa")?:0
    Text(LocaleStrings.get("Withdrawal fee")+": "+money(fee)+" · "+LocaleStrings.get("Total reserved")+": "+money(record.getLong("totalPaisa")?:0),style=MaterialTheme.typography.bodySmall)
    androidx.compose.foundation.text.selection.SelectionContainer{Text(LocaleStrings.get("Payout bKash number")+": "+record.getString("destination").orEmpty())}
    Text(LocaleStrings.get("Request ID")+": "+record.id,style=MaterialTheme.typography.bodySmall)
    if(privateAccount)Text(LocaleStrings.get("Account reference")+": "+record.getString("uid").orEmpty(),style=MaterialTheme.typography.bodySmall)
    val time=record.getTimestamp("createdAt")?.toDate()?.toInstant()?.atZone(ZoneId.of("Asia/Dhaka"))?.format(DateTimeFormatter.ofPattern("dd MMM yyyy · h:mm a"))
    if(time!=null)Text(time,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    when(record.getString("status")){
        "pending","approved"->Text(LocaleStrings.get(if(record.getString("status")=="approved")"Approved · funds held until payout is confirmed." else "Pending review · funds held."),style=MaterialTheme.typography.bodySmall)
        "rejected"->{Text(LocaleStrings.get("Rejected · funds returned to available winnings."));Text(record.getString("reason").orEmpty())}
        "sent"->Text(LocaleStrings.get("Payout reference")+": "+record.getString("payoutReference").orEmpty())
    }
}

@Composable
internal fun WithdrawalActivity(session:AdminSession,refresh:Int) {
    val uid=session.uid?:return
    var records by remember(uid){mutableStateOf<List<DocumentSnapshot>>(emptyList())}
    var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    var more by remember{mutableStateOf(false)}
    var generation by remember{mutableStateOf(0)}
    fun load(reset:Boolean) {
        if(loading)return
        loading=true;error="";val version=++generation
        var query:Query=session.db.collection("withdrawals").whereEqualTo("uid",uid).orderBy("createdAt",Query.Direction.DESCENDING)
        if(!reset)records.lastOrNull()?.let{query=query.startAfter(it)}
        query.limit(6).get(Source.SERVER).addOnCompleteListener{task->if(version==generation&&session.uid==uid){loading=false
            if(task.isSuccessful){records=((if(reset)emptyList()else records)+task.result.documents.take(5)).distinctBy{it.id};more=task.result.size()>5}
            else error="Could not load withdrawal requests. Reconnect and retry."
        }}
    }
    DisposableEffect(uid,refresh){load(true);onDispose{generation++;loading=false}}
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row{Text(LocaleStrings.get("Withdrawal requests"),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick={load(true)},enabled=!loading){Text(LocaleStrings.get("Refresh"))}}
        if(!loading&&records.isEmpty()&&error.isBlank())Text(LocaleStrings.get("No withdrawal requests yet."),style=MaterialTheme.typography.bodyMedium)
        records.forEach{record->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(LocaleStrings.get(record.getString("status").orEmpty()),style=MaterialTheme.typography.titleMedium);WithdrawalDetails(record)}}}
        WithdrawalError(error)
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotBlank())TextButton(onClick={load(records.isEmpty())}){Text(LocaleStrings.get("Retry"))}
        if(more&&!loading)OutlinedButton(onClick={load(false)},modifier=Modifier.fillMaxWidth()){Text(LocaleStrings.get("Load more"))}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WithdrawalReview(session:AdminSession,initial:DocumentSnapshot,close:()->Unit) {
    if(!session.allowed)return
    val operations=remember(initial.id){Withdrawals(session)}
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var record by remember{mutableStateOf<DocumentSnapshot?>(null)}
    var error by remember{mutableStateOf("")}
    var reload by remember{mutableStateOf(0)}
    var reference by rememberSaveable(initial.id){mutableStateOf("")}
    var reason by rememberSaveable(initial.id){mutableStateOf("")}
    var noTransfer by rememberSaveable(initial.id){mutableStateOf(false)}
    var sentConfirmed by rememberSaveable(initial.id){mutableStateOf(false)}
    DisposableEffect(reload,initial.id){var active=true;record=null;error=""
        initial.reference.get(Source.SERVER).addOnCompleteListener{task->if(active&&session.allowed){if(task.isSuccessful&&task.result.exists())record=task.result else error="Could not load withdrawal requests. Reconnect and retry."}}
        onDispose{active=false}
    }
    ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest={if(!operations.busy)close()}) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(LocaleStrings.get("Review withdrawal request"),style=MaterialTheme.typography.headlineSmall)
            record?.let{r->
                Text(r.getString("username").orEmpty(),style=MaterialTheme.typography.titleMedium)
                Text(LocaleStrings.get(r.getString("status").orEmpty()));WithdrawalDetails(r,true)
                val status=r.getString("status")
                if(status=="pending")Button(onClick={operations.decide(r,"approved"){reload++}},enabled=!operations.busy,modifier=Modifier.fillMaxWidth()){Text(LocaleStrings.get("Approve withdrawal"))}
                if(status=="approved"){
                    Text(LocaleStrings.get("Send the payout through bKash outside this app. If the transfer outcome is uncertain, leave this request Approved until reconciled."))
                    OutlinedTextField(reference,{reference=it},label={Text(LocaleStrings.get("Actual payout reference"))},singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={keyboard?.hide()}),enabled=!operations.busy,modifier=Modifier.fillMaxWidth())
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(sentConfirmed,{sentConfirmed=it},enabled=!operations.busy,modifier=Modifier.semantics{contentDescription=LocaleStrings.get("Confirm successful payout")});Text(LocaleStrings.get("I confirmed this payout amount and number were transferred."),Modifier.weight(1f))}
                    Button(onClick={operations.decide(r,"sent",reference=reference,sentConfirmed=sentConfirmed){close()}},enabled=!operations.busy&&sentConfirmed&&Regex("[A-Za-z0-9-]{6,80}").matches(reference.trim()),modifier=Modifier.fillMaxWidth()){Text(LocaleStrings.get("Mark as Sent"))}
                }
                if(status in listOf("pending","approved")){
                    HorizontalDivider()
                    OutlinedTextField(reason,{reason=it},label={Text(LocaleStrings.get("Rejection reason"))},minLines=2,isError=reason.trim().length>1000,enabled=!operations.busy,modifier=Modifier.fillMaxWidth())
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(noTransfer,{noTransfer=it},enabled=!operations.busy,modifier=Modifier.semantics{contentDescription=LocaleStrings.get("Confirm no payout occurred")});Text(LocaleStrings.get("I confirmed no payout occurred. Release the held funds."),Modifier.weight(1f))}
                    OutlinedButton(onClick={operations.decide(r,"rejected",reason=reason,noTransfer=noTransfer){close()}},enabled=!operations.busy&&noTransfer&&reason.isNotBlank()&&reason.trim().length<=1000,modifier=Modifier.fillMaxWidth()){Text(LocaleStrings.get("Reject and release funds"))}
                }
            }
            if(record==null&&error.isBlank())LinearProgressIndicator(Modifier.fillMaxWidth())
            WithdrawalError(error);WithdrawalError(operations.error)
            if(operations.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick={reload++},enabled=!operations.busy){Text(LocaleStrings.get("Reload"))}
            TextButton(onClick=close,enabled=!operations.busy){Text(LocaleStrings.get("Close"))}
        }
    }
}

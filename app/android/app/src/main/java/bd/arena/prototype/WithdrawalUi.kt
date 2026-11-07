package bd.arena.prototype

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
internal fun WithdrawalError(message:String) {
    if(message.isNotBlank())Text(LocaleStrings.get(message),Modifier.semantics{liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)
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
            Text(LocaleStrings.get("Track its status in Transaction history."))
            Button(onClick={intent.edit().clear().apply();done()},modifier=Modifier.fillMaxWidth()){Text(LocaleStrings.get("Done"))}
        }else {
            LabeledValue(LocaleStrings.get("Available winnings"),money(available))
            if(policy?.getBoolean("enabled")!=true&&!loading)Text(LocaleStrings.get("New withdrawals are not enabled. Existing requests appear in Transaction history."))
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

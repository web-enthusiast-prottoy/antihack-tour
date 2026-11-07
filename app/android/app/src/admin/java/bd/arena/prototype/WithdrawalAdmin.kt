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
    var confirming by remember { mutableStateOf(false) }
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
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedTextField(minimum,{minimum=it;saved=false},label={Text(LocaleStrings.get("Minimum withdrawal (taka)"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),isError=minimum.isNotBlank()&&(minValue==null||minValue==0L),enabled=!loading&&!operations.busy,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(fee,{fee=it;saved=false},label={Text(LocaleStrings.get("Withdrawal fee (taka)"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),isError=fee.isNotBlank()&&feeValue==null,enabled=!loading&&!operations.busy,modifier=Modifier.fillMaxWidth())
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text(LocaleStrings.get("Enable withdrawals"),Modifier.weight(1f));Switch(enabled,{enabled=it;saved=false},enabled=!loading&&!operations.busy,modifier=Modifier.semantics{contentDescription=LocaleStrings.get("Enable withdrawals")})}
        WithdrawalError(error);WithdrawalError(operations.error)
        if(saved)Text(LocaleStrings.get("Withdrawal settings saved."),Modifier.semantics{liveRegion=LiveRegionMode.Polite})
        Button(onClick={confirming=true},enabled=policy!=null&&valid&&!loading&&!operations.busy,modifier=Modifier.fillMaxWidth()){Text(LocaleStrings.get(if(operations.busy)"Saving…" else "Save withdrawal settings"))}
        if (confirming && minValue != null && feeValue != null) AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(adminText("Confirm withdrawal settings")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(adminText(if (enabled) "Enabled" else "Disabled"))
                    LabeledValue(adminText("Minimum withdrawal"), money(minValue))
                    LabeledValue(adminText("Withdrawal fee"), money(feeValue))
                    Text(adminText("This change affects new requests."))
                }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(adminText("Cancel")) } },
            confirmButton = { TextButton(onClick = {
                confirming = false
                operations.policy(minValue, feeValue, enabled, policy?.getLong("revision") ?: 0) { saved = true; reload++ }
            }) { Text(adminText("Save")) } }
        )
        RegisterRefresh(!operations.busy){reload++}
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
        FixedRefreshHost {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(LocaleStrings.get("Review withdrawal request"),style=MaterialTheme.typography.headlineSmall)
            record?.let{r->
                PlayerIdentity(session,r.getString("uid").orEmpty(),r.getString("username").orEmpty())
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
            RegisterRefresh(!operations.busy){reload++}
            TextButton(onClick=close,enabled=!operations.busy){Text(LocaleStrings.get("Close"))}
        }
        }
    }
}

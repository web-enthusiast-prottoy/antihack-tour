package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.*

internal const val defaultBkashNumber="01889898989"
internal data class PaymentNumberState(val number:String=if(BuildConfig.LOCAL_OPERATIONS)defaultBkashNumber else "",val loading:Boolean=true,val cached:Boolean=true,val error:Boolean=false,val revision:Long=0,val enabled:Boolean=false)

@Composable
internal fun paymentNumber(session:AdminSession):PaymentNumberState {
    var state by remember(session.uid){mutableStateOf(PaymentNumberState())}
    DisposableEffect(session.uid) {
        val stop=session.db.document("paymentInstructions/bkash").addSnapshotListener(MetadataChanges.INCLUDE){snapshot,error->
            state=if(error!=null)PaymentNumberState(loading=false,error=true)else PaymentNumberState(snapshot?.getString("number")?:(if(BuildConfig.LOCAL_OPERATIONS)defaultBkashNumber else ""),false,snapshot?.metadata?.isFromCache?:true,false,snapshot?.getLong("revision")?:0,snapshot?.getBoolean("enabled")?:BuildConfig.LOCAL_OPERATIONS)
        }
        onDispose{stop.remove()}
    }
    return state
}

@Composable
internal fun PaymentSettings(session:AdminSession,bangla:Boolean,back:()->Unit) {
    fun t(en:String,bn:String)=if(bangla)bn else en
    androidx.activity.compose.BackHandler{back()}
    val current=paymentNumber(session)
    var number by remember(current.number,current.revision){mutableStateOf(if(current.enabled)current.number else "")}
    var enabled by remember(current.enabled,current.revision){mutableStateOf(current.enabled)}
    var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    var error by remember{mutableStateOf("")}
    Column(Modifier.widthIn(max=640.dp).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        ScreenHeader(t("Payment settings","পেমেন্ট সেটিংস"),back=back)
        Text(t("Players see this bKash number in Add Money instructions.","টাকা যোগ করার নির্দেশনায় খেলোয়াড়েরা এই বিকাশ নম্বর দেখবেন।"))
        if(current.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedTextField(number,{number=it},label={Text(t("Receiving bKash number","টাকা গ্রহণের বিকাশ নম্বর"))},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Phone),isError=number.isNotBlank()&&!validDepositSender(number))
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text(t("Enable deposit instructions","টাকা জমার নির্দেশনা চালু করুন"),Modifier.weight(1f));Switch(enabled,{enabled=it},enabled=!busy,modifier=Modifier.semantics{contentDescription=t("Enable deposit instructions","টাকা জমার নির্দেশনা চালু করুন")})}
        if(current.cached||current.error)Text(t("Connect to load and update the current number.","বর্তমান নম্বর দেখতে ও বদলাতে ইন্টারনেট সংযোগ দিন।"))
        if(message.isNotEmpty())Text(message)
        if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
        Button(onClick={
            val actor=session.uid?:return@Button
            val expected=current.revision
            val ref=session.db.document("paymentInstructions/bkash")
            busy=true;error="";message=""
            session.db.runTransaction {tx->
                val old=tx.get(ref)
                require((old.getLong("revision")?:0)==expected){"The number changed. Reload before saving."}
                val revision=expected+1
                tx.set(ref,mapOf("number" to number.trim(),"enabled" to enabled,"revision" to revision,"updatedAt" to FieldValue.serverTimestamp()))
                tx.set(session.db.document("auditRecords/payment_bkash_$revision"),mapOf("action" to "savePaymentInstructions","path" to ref.path,"actor" to actor,"at" to FieldValue.serverTimestamp()))
            }.addOnCompleteListener {if(session.uid==actor&&session.allowed){busy=false;if(it.isSuccessful)message=t("Receiving number saved.","টাকা গ্রহণের নম্বর সংরক্ষিত হয়েছে।")else error=t("Could not save. Reconnect and reload before retrying.","সংরক্ষণ হয়নি। সংযোগ দিয়ে আবার লোড করে চেষ্টা করুন।")}}
        },enabled=session.allowed&&!busy&&!current.loading&&!current.cached&&!current.error&&((!enabled&&number.isBlank())||validDepositSender(number))){Text(t(if(busy)"Saving…" else "Save number",if(busy)"সংরক্ষণ হচ্ছে…" else "নম্বর সংরক্ষণ করুন"))}
    }
}
